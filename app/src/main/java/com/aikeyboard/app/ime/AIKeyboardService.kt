package com.aikeyboard.app.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.aikeyboard.app.KeyboardApp
import com.aikeyboard.app.ai.CorrectionStyle
import com.aikeyboard.app.ai.GroqException
import com.aikeyboard.app.ai.TextCorrector
import com.aikeyboard.app.data.PersonalizationStore
import com.aikeyboard.app.data.Settings
import com.aikeyboard.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IME com caminho quente enxuto.
 *
 * Princípios de performance que governam essa classe:
 *  - A correção em fronteira (espaço) é PRÉ-COMPUTADA em background pelo
 *    pipeline e guardada em [correctionMemo]. No caso comum o `onSpace` só
 *    LÊ o memo — zero Damerau-Levenshtein na UI thread. Em cache miss
 *    (digitação muito rápida), cai para um cálculo síncrono bounded.
 *  - O pipeline em `Dispatchers.Default` é disparado por um StateFlow do
 *    `currentWord`, com debounce de [SUGGESTION_DEBOUNCE_MS] e `collectLatest`
 *    — cada tecla cancela o cálculo anterior. Ele atualiza o memo SEMPRE
 *    (mesmo com a barra desligada) e a strip de sugestões só quando ligada.
 *  - O caminho quente NÃO faz NENHUMA chamada IPC ao InputConnection por
 *    tecla. Em vez de perguntar ao app de destino "tem seleção?" / "qual a
 *    palavra antes do cursor?" (binder round-trips que travavam o keystroke),
 *    mantemos um ESPELHO local do cursor/seleção ([selStart]/[selEnd]) e da
 *    palavra em composição ([currentWord]). O espelho é semeado em
 *    `onStartInputView` (seleção inicial do EditorInfo), atualizado pelas
 *    nossas próprias edições ([icCommitText]/[icDeleteBefore]) e
 *    ressincronizado só quando o usuário move o cursor ([onUpdateSelection]).
 *  - O tipo do campo ([fieldPolicy]) desliga autocorreção/sugestão/maiúscula
 *    em senha, e-mail, URL e campos numéricos.
 */
@OptIn(FlowPreview::class)
class AIKeyboardService : InputMethodService(), KeyboardActionListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private var inFlightJob: Job? = null

    private lateinit var settingsStore: SettingsStore
    private lateinit var personalizationStore: PersonalizationStore
    private var keyboardView: KeyboardView? = null
    private var settings: Settings = Settings()
    private var inputConnection: InputConnection? = null
    private var userDictionary: Set<String> = emptySet()

    private var shiftOn = false
    private var capsLock = false

    /**
     * Política do campo atual (senha/e-mail/URL/numérico desligam autocorreção,
     * sugestão e maiúscula). Resolvida em [onStartInputView] a partir do
     * `EditorInfo.inputType`. Ver [FieldPolicy].
     */
    private var fieldPolicy: FieldPolicy = FieldPolicy.DEFAULT

    /**
     * Espelho local do cursor/seleção do campo, mantido em sincronia por
     * [onUpdateSelection] e pelas mutações que NÓS aplicamos (via [icCommitText]/
     * [icDeleteBefore], que atualizam `expected*`). É o que permite ao caminho
     * quente decidir "tem seleção?" e "qual é a palavra?" SEM nenhuma chamada
     * IPC (`getSelectedText`/`getTextBeforeCursor`) por tecla — a origem real do
     * delay anterior. `expected*` guarda a posição que esperamos depois das
     * nossas próprias edições; quando o `onUpdateSelection` traz algo diferente,
     * foi o usuário que moveu o cursor e ressincronizamos.
     */
    private var selStart = 0
    private var selEnd = 0
    private var expectedSelStart = 0
    private var expectedSelEnd = 0

    /**
     * `true` logo após NÓS comitarmos um espaço (caminho normal do [onSpace]).
     * Usado pelo atalho "dois espaços = ponto final". Qualquer outra entrada o
     * zera.
     */
    private var justCommittedSpace = false

    /**
     * Verdadeiro depois que o léxico PT-BR terminou de carregar no
     * background. Enquanto for falso, o caminho de digitação pula a
     * autocorreção em fronteira para nunca bloquear a UI thread.
     */
    @Volatile
    private var lexiconReady = false

    /**
     * Memo da correção de fronteira pré-computada pelo pipeline de sugestões
     * em background (Dispatchers.Default). O [onSpace] lê este memo: em cache
     * hit, aplica a correção SEM rodar Damerau-Levenshtein na UI thread.
     * Publicação atômica via referência única `@Volatile` (sem leitura
     * rasgada entre `word` e `result`).
     */
    @Volatile
    private var correctionMemo: CorrectionMemo? = null

    /**
     * Palavra em composição rastreada localmente, evitando IPC ao
     * InputConnection a cada keystroke pra descobrir o que o usuário
     * está digitando. Inicializada uma única vez em [onStartInputView]
     * e mantida em sincronia por [onChar]/[onBackspace]/[onSpace]/[onEnter].
     */
    private val currentWord = StringBuilder()

    /**
     * Reflexo do [currentWord] num StateFlow. O pipeline de sugestões
     * faz `collectLatest` aqui, então cada keystroke cancela
     * automaticamente o cálculo anterior em background.
     */
    private val currentWordFlow = MutableStateFlow("")

    /**
     * Estado de undo de auto-correção. Se preenchido, indica que a
     * última fronteira aplicou uma correção e o próximo BACKSPACE deve
     * desfazer (restaurar a palavra original) em vez de apagar caractere.
     */
    private var pendingAutoCorrect: PendingAutoCorrect? = null

    /**
     * Última palavra inserida por gesto (swipe), sem o espaço. Enquanto não for
     * nula, tocar numa sugestão substitui esta palavra (troca a escolha do
     * gesto). Qualquer outra tecla a invalida.
     */
    private var lastGestureCommit: String? = null

    /** Última palavra fechada — usada para aprender bigramas (predição da próxima). */
    private var previousCommittedWord: String? = null

    private val corrector: TextCorrector by lazy {
        val app = applicationContext as KeyboardApp
        TextCorrector(
            clients = listOf(
                app.groqClient,            // 1ª opção: Groq (rápido ~1s, qualidade alta).
                app.geminiClient,          // 2ª opção: Gemini (chave primária) — fallback.
                app.geminiSecondaryClient  // 3ª opção: Gemini (chave secundária) — fallback.
            )
        )
    }

    private val typingEngine: TypingEngine by lazy {
        TypingEngine(PortugueseLexicon(this))
    }

    /**
     * Ditado por voz. Criado sob demanda (só quando o usuário toca no 🎤) para
     * não instanciar um SpeechRecognizer em quem nunca usa voz. Enquanto
     * [dictating] é true, o pipeline de sugestões fica pausado e o texto
     * reconhecido entra como "composing" (provisório/sublinhado) até a frase
     * ser finalizada.
     */
    private var voiceController: VoiceInputController? = null
    private var dictating = false

    private val voiceCallbacks = object : VoiceInputController.Callbacks {
        override fun onPartial(text: String) {
            ic()?.setComposingText(text, 1)
        }

        override fun onFinal(text: String) {
            val ic = ic() ?: return
            ic.beginBatchEdit()
            try {
                ic.setComposingText(text, 1)
                ic.finishComposingText()
                ic.commitText(" ", 1)
            } finally {
                ic.endBatchEdit()
            }
        }

        override fun onListeningChanged(listening: Boolean) {
            keyboardView?.setListening(listening)
            if (!listening) {
                dictating = false
                justCommittedSpace = false
                // O ditado comita fora dos helpers de espelho; força a próxima
                // atualização de seleção a ressincronizar o cursor (sentinela
                // impossível que nunca casa com uma posição real).
                expectedSelStart = -1
                expectedSelEnd = -1
                ic()?.let { ic ->
                    ic.finishComposingText()
                    updateAutoShiftState(ic)
                }
                currentWord.setLength(0)
                currentWordFlow.value = ""
            }
        }

        override fun onError(message: String) {
            keyboardView?.showError(message)
        }
    }

    private fun voice(): VoiceInputController =
        voiceController ?: VoiceInputController(this, voiceCallbacks).also { voiceController = it }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as KeyboardApp
        settingsStore = app.settingsStore
        personalizationStore = app.personalizationStore
        settings = settingsStore.current
        userDictionary = app.userDictionaryStore.current

        serviceScope.launch {
            settingsStore.flow.collectLatest { newSettings ->
                val scaleChanged = newSettings.keyboardSize != settings.keyboardSize
                val themeChanged = newSettings.keyboardTheme != settings.keyboardTheme
                settings = newSettings
                keyboardView?.let { view ->
                    if (scaleChanged) view.setScale(newSettings.keyboardSize.scale)
                    if (themeChanged) view.setThemeMode(newSettings.keyboardTheme)
                    view.setHapticEnabled(newSettings.hapticEnabled)
                    view.setSoundEnabled(newSettings.soundEnabled)
                    view.setGestureEnabled(newSettings.gestureTypingEnabled)
                    view.setSuggestionsEnabled(newSettings.suggestionsEnabled && fieldPolicy.suggestions)
                }
            }
        }

        serviceScope.launch {
            app.userDictionaryStore.flow.collectLatest { words ->
                userDictionary = words
            }
        }

        // Pré-aquece o léxico fora do caminho de digitação. Tocamos em
        // `words.size` para garantir que o construtor do PortugueseLexicon
        // termine (lazy `by lazy` é SYNCHRONIZED, então se o usuário digitar
        // antes daqui, o keystroke bloquearia esperando o lock). Só marcamos
        // `lexiconReady` depois que TUDO está pronto.
        serviceScope.launch(Dispatchers.Default) {
            @Suppress("UNUSED_VARIABLE")
            val warmup = typingEngine.warmup()
            lexiconReady = true
        }

        // Pre-aquecimento de rede: dispara uma resolução DNS + TLS handshake
        // contra api.groq.com em background. Quando o usuário acionar uma
        // ação de IA, a primeira chamada já encontra a rota quente — o que
        // economiza ~200–300ms da latência percebida. Best-effort; qualquer
        // falha aqui é silenciosa porque é puramente otimização.
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                java.net.InetAddress.getByName("api.groq.com")
            }
        }

        // Pipeline de sugestões em tempo real:
        //  1. currentWordFlow emite a cada keystroke (zero IPC por tecla);
        //  2. debounce coalesce rajadas de digitação rápida;
        //  3. distinctUntilChanged evita recomputar palavra idêntica;
        //  4. collectLatest CANCELA o cálculo anterior automaticamente
        //     quando uma nova palavra chega — então digitar rápido nunca
        //     acumula trabalho em fila;
        //  5. compute roda em Dispatchers.Default (CPU), nunca na UI;
        //  6. setSuggestions roda em Main.immediate, só atualizando text
        //     dos 3 slots já existentes (sem layout pass pesado).
        serviceScope.launch {
            currentWordFlow
                .debounce(SUGGESTION_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { word ->
                    if (!lexiconReady) return@collectLatest
                    // Durante o ditado por voz o texto entra como "composing";
                    // não calculamos nem mostramos sugestões de digitação.
                    if (dictating) return@collectLatest
                    // Lidos na UI thread antes de saltar pro Default (sem corrida).
                    val suggestionsAllowed = settings.suggestionsEnabled && fieldPolicy.suggestions
                    if (!suggestionsAllowed) {
                        // Barra desligada: ZERO trabalho de fundo por tecla. O
                        // onSpace corrige sincronicamente (rápido no nível padrão).
                        // Memo invalidado para não servir resultado obsoleto.
                        if (correctionMemo != null) correctionMemo = null
                        return@collectLatest
                    }
                    val autocorrectAllowed = fieldPolicy.autocorrect
                    val candidates = withContext(Dispatchers.Default) {
                        // Correção de fronteira pré-computada — alimenta o memo que
                        // o onSpace lê sem rodar fuzzy na UI thread. Pulada em campo
                        // que não permite autocorreção (senha/e-mail).
                        val correction = if (autocorrectAllowed && word.isNotEmpty()) {
                            typingEngine.boundaryCorrection(
                                word,
                                userDictionary,
                                personalizationStore.snapshot,
                                settings.autocorrectLevel
                            )
                        } else {
                            null
                        }
                        correctionMemo = CorrectionMemo(word, correction)

                        if (word.isEmpty()) {
                            typingEngine.nextWordCandidates(
                                previousWord = null,
                                userDictionary = userDictionary,
                                personalization = personalizationStore.snapshot
                            )
                        } else {
                            typingEngine.suggestionCandidates(
                                prefix = word,
                                userDictionary = userDictionary,
                                personalization = personalizationStore.snapshot,
                                correction = correction
                            )
                        }
                    }
                    withContext(Dispatchers.Main.immediate) {
                        keyboardView?.setSuggestions(candidates)
                    }
                }
        }
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this, scale = settings.keyboardSize.scale).apply {
            listener = this@AIKeyboardService
        }
        keyboardView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputConnection = currentInputConnection
        capsLock = false
        shiftOn = false
        pendingAutoCorrect = null
        lastGestureCommit = null
        previousCommittedWord = null
        correctionMemo = null
        justCommittedSpace = false

        // Política do campo: senha/e-mail/URL/numérico desligam autocorreção,
        // sugestão e maiúscula — onde elas só causariam "erros".
        fieldPolicy = FieldPolicy.forInputType(info.inputType)

        // Espelho do cursor a partir da seleção inicial informada pelo app.
        // Quando ausente (-1), começa em 0 e se auto-corrige no primeiro
        // onUpdateSelection (ver [onUpdateSelection]).
        val initStart = info.initialSelStart.coerceAtLeast(0)
        val initEnd = info.initialSelEnd.coerceAtLeast(0)
        selStart = initStart
        selEnd = initEnd
        expectedSelStart = initStart
        expectedSelEnd = initEnd

        // Reinicializa `currentWord` lendo o texto antes do cursor UMA
        // ÚNICA VEZ por sessão de input. Depois disso, todas as
        // atualizações vêm de eventos locais (onChar/onBackspace/etc) e do
        // onUpdateSelection — nunca de IPC por tecla.
        currentWord.setLength(0)
        if (selStart == selEnd) {
            ic()?.let { ic ->
                val before = ic.getTextBeforeCursor(MAX_CORRECTION_CONTEXT, 0)?.toString().orEmpty()
                val bounds = findLastWordBounds(before)
                if (bounds != null) currentWord.append(before.substring(bounds))
            }
        }
        currentWordFlow.value = currentWord.toString()
        ic()?.let { updateAutoShiftState(it) }
        keyboardView?.setShiftState(shiftOn, capsLock)
        keyboardView?.setBusy(false)
        keyboardView?.setListening(false)
        keyboardView?.setThemeMode(settings.keyboardTheme)
        keyboardView?.setHapticEnabled(settings.hapticEnabled)
        keyboardView?.setSoundEnabled(settings.soundEnabled)
        keyboardView?.setGestureEnabled(settings.gestureTypingEnabled)
        keyboardView?.setSuggestionsEnabled(settings.suggestionsEnabled && fieldPolicy.suggestions)

        // Acabou de conceder a permissão de microfone na Activity-trampolim:
        // inicia o ditado assim que o campo recupera o foco (best-effort).
        if (VoiceInputController.autoStartPending) {
            VoiceInputController.autoStartPending = false
            if (VoicePermissionActivity.hasMicPermission(this)) {
                keyboardView?.post {
                    val controller = voice()
                    if (controller.isAvailable() && !controller.isListening) {
                        startDictation(controller)
                    }
                }
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        inputConnection = null
        inFlightJob?.cancel()
        voiceController?.cancelNow()
        dictating = false
        keyboardView?.setBusy(false)
        keyboardView?.setListening(false)
        keyboardView?.setSuggestions(emptyList())
        currentWord.setLength(0)
        currentWordFlow.value = ""
        pendingAutoCorrect = null
        lastGestureCommit = null
        correctionMemo = null
        justCommittedSpace = false
        selStart = 0
        selEnd = 0
        expectedSelStart = 0
        expectedSelEnd = 0
        personalizationStore.flush()
    }

    override fun onDestroy() {
        personalizationStore.flush()
        voiceController?.destroy()
        voiceController = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onChar(c: Char) {
        finalizeVoiceIfListening()
        lastGestureCommit = null
        justCommittedSpace = false
        val ic = ic() ?: return
        val value = if (shiftOn) c.uppercaseChar() else c
        icCommitText(ic, value.toString())
        resetShiftAfterChar()
        // Mantém o composing word local em sincronia com o que foi
        // efetivamente comitado. Janela de undo da auto-correção é
        // descartada porque o usuário já avançou pra próxima palavra.
        pendingAutoCorrect = null
        if (isWordChar(value)) {
            currentWord.append(value)
            currentWordFlow.value = currentWord.toString()
        } else {
            // Char não-palavra (vírgula, ponto, símbolo) fecha a palavra
            // sem espaço — limpa o composing.
            if (currentWord.isNotEmpty()) resetComposingWord()
        }
    }

    override fun onText(text: String) {
        finalizeVoiceIfListening()
        lastGestureCommit = null
        justCommittedSpace = false
        val ic = ic() ?: return
        icCommitText(ic, text)
        // Texto multi-char (emoji, frase pronta) sempre fecha a palavra.
        pendingAutoCorrect = null
        if (currentWord.isNotEmpty()) resetComposingWord()
    }

    override fun onSpace() {
        finalizeVoiceIfListening()
        lastGestureCommit = null
        val ic = ic() ?: return

        // 1) Seleção ativa: substitui pelo espaço.
        if (hasSelection()) {
            icCommitText(ic, " ")
            resetComposingWord()
            pendingAutoCorrect = null
            justCommittedSpace = false
            updateAutoShiftState(ic)
            return
        }

        // FONTE DA VERDADE: lê o texto REAL antes do cursor — UMA vez por espaço
        // (≈uma vez por palavra; latência irrelevante). Toda decisão e toda
        // edição destrutiva abaixo usa ESTE texto, nunca a sombra local
        // [currentWord] (que apps como o WhatsApp dessincronizam, fazendo apagar
        // o número errado de caracteres e destruir o texto).
        val before = ic.getTextBeforeCursor(MAX_CORRECTION_CONTEXT, 0)?.toString().orEmpty()

        // 2) Atalho "dois espaços = ponto final".
        if (fieldPolicy.autocorrect && justCommittedSpace &&
            shouldConvertSecondSpaceToPeriod(before.takeLast(2))
        ) {
            ic.beginBatchEdit()
            try {
                icDeleteBefore(ic, 1)
                icCommitText(ic, ". ")
            } finally {
                ic.endBatchEdit()
            }
            pendingAutoCorrect = null
            previousCommittedWord = null
            justCommittedSpace = false
            resetComposingWord()
            if (!capsLock && fieldPolicy.autoCap) setShift(true)
            return
        }

        // 3) Espaço normal: fecha a palavra REAL e, se permitido, autocorrige.
        val bounds = findLastWordBounds(before)
        val originalWord = bounds?.let(before::substring)
        val isProperNoun = bounds != null && originalWord != null &&
            looksLikeProperNoun(before, bounds.first, originalWord)
        val correctedWord: String? = when {
            originalWord == null -> null
            !fieldPolicy.autocorrect -> null
            !originalWord.any(Char::isLetter) -> null
            !lexiconReady -> null
            // Nome próprio no meio da frase (ex.: "vou ver o Marcelo"): não corrige.
            isProperNoun -> null
            else -> {
                val memo = correctionMemo
                if (memo != null && memo.word == originalWord) {
                    memo.result
                } else {
                    typingEngine
                        .boundaryCorrection(
                            originalWord,
                            userDictionary,
                            personalizationStore.snapshot,
                            settings.autocorrectLevel
                        )
                        .also { correctionMemo = CorrectionMemo(originalWord, it) }
                }
            }
        }

        val appliedCorrection = correctedWord != null &&
            correctedWord.isNotEmpty() &&
            correctedWord != originalWord

        ic.beginBatchEdit()
        try {
            if (appliedCorrection) {
                // Apaga exatamente a palavra REAL lida do campo.
                icDeleteBefore(ic, originalWord!!.length)
                icCommitText(ic, correctedWord!!)
            }
            icCommitText(ic, " ")
        } finally {
            ic.endBatchEdit()
        }

        val committedWord = correctedWord ?: originalWord
        if (committedWord != null) {
            personalizationStore.recordCommittedWord(committedWord)
            previousCommittedWord?.let { personalizationStore.recordBigram(it, committedWord) }
            previousCommittedWord = committedWord
        }

        // Arma a janela de undo se houve correção (backspace logo após desfaz).
        pendingAutoCorrect = if (appliedCorrection) {
            PendingAutoCorrect(original = originalWord!!, corrected = correctedWord!!)
        } else {
            null
        }
        resetComposingWord()
        justCommittedSpace = true

        // Auto-shift derivado do `before` já lido (sem IPC extra): fechar uma
        // palavra nunca capitaliza; após pontuação de fim de frase, sim.
        if (!capsLock && fieldPolicy.autoCap) {
            setShift(if (originalWord != null) false else shouldAutoCapitalize(before))
        }
    }

    override fun onEnter() {
        finalizeVoiceIfListening()
        lastGestureCommit = null
        justCommittedSpace = false
        previousCommittedWord = null
        val ic = ic() ?: return
        pendingAutoCorrect = null
        resetComposingWord()
        val action = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            icCommitText(ic, "\n")
            updateAutoShiftState(ic)
        }
    }

    override fun onBackspace() {
        finalizeVoiceIfListening()
        lastGestureCommit = null
        justCommittedSpace = false
        val ic = ic() ?: return
        // Janela de undo da auto-correção: se o backspace acontece
        // IMEDIATAMENTE depois de uma correção aplicada (sem nenhuma
        // outra tecla no meio), o usuário está dizendo "não era isso".
        // Restauramos a palavra original e marcamos o par como rejeitado.
        val pending = pendingAutoCorrect
        if (pending != null) {
            pendingAutoCorrect = null
            // Confirma no texto REAL que o campo está mesmo em "corrigida " antes
            // de apagar — se o app mexeu no campo nesse meio-tempo, NÃO arrisca
            // garrambar; cai para um backspace normal.
            val expected = pending.corrected + " "
            val actual = ic.getTextBeforeCursor(expected.length, 0)?.toString()
            if (actual == expected) {
                ic.beginBatchEdit()
                try {
                    icDeleteBefore(ic, expected.length)
                    icCommitText(ic, pending.original + " ")
                } finally {
                    ic.endBatchEdit()
                }
                personalizationStore.recordRejectedCorrection(pending.original, pending.corrected)
                resetComposingWord()
                return
            }
        }

        if (hasSelection()) {
            icCommitText(ic, "")
            resetComposingWord()
            return
        }
        icDeleteBefore(ic, 1)
        // Mantém composing local em sincronia: tira o último char se
        // houver, caso contrário sinaliza palavra vazia.
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
        }
        currentWordFlow.value = currentWord.toString()
    }

    override fun onBackspaceWord() {
        finalizeVoiceIfListening()
        lastGestureCommit = null
        justCommittedSpace = false
        val ic = ic() ?: return
        pendingAutoCorrect = null
        if (hasSelection()) {
            icCommitText(ic, "")
            resetComposingWord()
            return
        }

        val before = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        var index = before.length
        while (index > 0 && before[index - 1].isWhitespace()) index--
        while (index > 0 && !before[index - 1].isWhitespace()) index--
        val deleteCount = before.length - index
        if (deleteCount > 0) icDeleteBefore(ic, deleteCount)
        resetComposingWord()
    }

    override fun onShift() {
        if (shiftOn && !capsLock) {
            capsLock = true
            shiftOn = true
        } else if (capsLock) {
            capsLock = false
            shiftOn = false
        } else {
            shiftOn = true
        }
        keyboardView?.setShiftState(shiftOn, capsLock)
    }

    override fun onSwitchIme() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.showInputMethodPicker()
    }

    override fun onVoiceInput() {
        // Toque enquanto ouve = parar (finaliza a frase atual).
        voiceController?.takeIf { it.isListening }?.let {
            it.stop()
            return
        }
        // Sem permissão de microfone: abre a Activity-trampolim que a pede.
        if (!VoicePermissionActivity.hasMicPermission(this)) {
            keyboardView?.showError(getString(com.aikeyboard.app.R.string.voice_need_permission))
            runCatching {
                startActivity(
                    Intent(this, VoicePermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        val controller = voice()
        if (!controller.isAvailable()) {
            keyboardView?.showError(getString(com.aikeyboard.app.R.string.voice_unavailable))
            return
        }
        startDictation(controller)
    }

    private fun startDictation(controller: VoiceInputController) {
        // Zera o estado de digitação antes de ditar.
        pendingAutoCorrect = null
        correctionMemo = null
        currentWord.setLength(0)
        currentWordFlow.value = ""
        keyboardView?.setSuggestions(emptyList())
        dictating = true
        keyboardView?.showError(getString(com.aikeyboard.app.R.string.voice_listening))
        controller.start()
    }

    /**
     * Se o ditado estiver ativo e o usuário tocar numa tecla, finaliza o que já
     * foi reconhecido (mantém o texto) e encerra a escuta antes de processar a
     * tecla — assim voz e digitação nunca brigam pela mesma região de texto.
     */
    private fun finalizeVoiceIfListening() {
        voiceController?.takeIf { it.isListening }?.cancelNow()
    }

    override fun onSuggestionTap(candidate: SuggestionCandidate) {
        finalizeVoiceIfListening()
        val ic = ic() ?: return
        pendingAutoCorrect = null
        val gestureWord = lastGestureCommit
        ic.beginBatchEdit()
        try {
            when (candidate.type) {
                SuggestionType.ORIGINAL -> {
                    realWordBeforeCursor(ic)?.let { icDeleteBefore(ic, it.length) }
                    icCommitText(ic, candidate.value + " ")
                    val memo = correctionMemo
                    if (memo != null && memo.word == candidate.value && memo.result != null) {
                        personalizationStore.recordRejectedCorrection(candidate.value, memo.result)
                    }
                    personalizationStore.recordCommittedWord(candidate.value)
                    lastGestureCommit = null
                }
                SuggestionType.WORD, SuggestionType.CORRECTION -> {
                    // Substitui a palavra-alvo pela escolha do usuário + espaço.
                    // Alvo: a palavra do gesto (palavra+espaço) ou, ao digitar, a
                    // palavra REAL antes do cursor (lida do campo, não da sombra —
                    // evita apagar o número errado de caracteres).
                    if (gestureWord != null) {
                        icDeleteBefore(ic, gestureWord.length + 1)
                    } else {
                        realWordBeforeCursor(ic)?.let { icDeleteBefore(ic, it.length) }
                    }
                    icCommitText(ic, candidate.value + " ")
                    personalizationStore.recordCommittedWord(candidate.value)
                    // Permite trocar de novo entre as alternativas do gesto.
                    lastGestureCommit = if (gestureWord != null) candidate.value else null
                }
                SuggestionType.EMOJI -> {
                    // Emoji vai grudado no que veio antes — não come a
                    // palavra digitada, só insere o emoji + espaço.
                    icCommitText(ic, candidate.value + " ")
                    lastGestureCommit = null
                }
                SuggestionType.QUICK_PHRASE, SuggestionType.CLIPBOARD -> {
                    icCommitText(ic, candidate.value)
                    lastGestureCommit = null
                }
            }
        } finally {
            ic.endBatchEdit()
        }
        resetComposingWord()
        // Palavra/correção terminam em espaço → habilita o atalho de ponto final.
        justCommittedSpace = candidate.type == SuggestionType.ORIGINAL ||
            candidate.type == SuggestionType.WORD ||
            candidate.type == SuggestionType.CORRECTION
        updateAutoShiftState(ic)
    }

    override fun onGesture(keys: List<Char>) {
        finalizeVoiceIfListening()
        if (!settings.gestureTypingEnabled || !lexiconReady) return
        val ic = ic() ?: return
        val candidates = typingEngine.decodeGesture(
            path = keys,
            userDictionary = userDictionary,
            personalization = personalizationStore.snapshot
        )
        val best = candidates.firstOrNull() ?: return
        pendingAutoCorrect = null

        // Respeita shift/caps na primeira letra da palavra do gesto.
        val word = when {
            capsLock -> best.uppercase()
            shiftOn -> best.replaceFirstChar { it.uppercase() }
            else -> best
        }

        // A 1ª letra do gesto foi comitada no toque; remove a palavra REAL antes
        // do cursor (lida do campo, não da sombra) e a substitui pela decodificada.
        val realWord = realWordBeforeCursor(ic)
        ic.beginBatchEdit()
        try {
            if (!realWord.isNullOrEmpty()) icDeleteBefore(ic, realWord.length)
            icCommitText(ic, "$word ")
        } finally {
            ic.endBatchEdit()
        }
        personalizationStore.recordCommittedWord(best)
        previousCommittedWord?.let { personalizationStore.recordBigram(it, best) }
        previousCommittedWord = best
        lastGestureCommit = word
        resetComposingWord()
        justCommittedSpace = true

        if (shiftOn && !capsLock) setShift(false)

        // As demais hipóteses do gesto viram sugestões tocáveis (1 toque troca).
        if (settings.suggestionsEnabled && fieldPolicy.suggestions && candidates.size > 1) {
            keyboardView?.setSuggestions(
                candidates.drop(1).map { SuggestionCandidate(it, SuggestionType.WORD) }
            )
        }
    }

    /**
     * Toque-longo (acento ou número): a tecla-base já foi comitada no ACTION_DOWN
     * (digitação instantânea), então aqui REMOVEMOS essa base e a substituímos
     * pela variante escolhida — tudo num único batch para o app de destino ver
     * uma troca atômica.
     */
    override fun onAccentReplace(accent: Char) {
        finalizeVoiceIfListening()
        val ic = ic() ?: return
        pendingAutoCorrect = null
        justCommittedSpace = false
        lastGestureCommit = null
        ic.beginBatchEdit()
        try {
            if (currentWord.isNotEmpty()) {
                icDeleteBefore(ic, 1)
                currentWord.deleteCharAt(currentWord.length - 1)
            }
            icCommitText(ic, accent.toString())
        } finally {
            ic.endBatchEdit()
        }
        if (isWordChar(accent)) {
            currentWord.append(accent)
            currentWordFlow.value = currentWord.toString()
        } else {
            resetComposingWord()
        }
        resetShiftAfterChar()
    }

    override fun onAiAction(style: CorrectionStyle) {
        finalizeVoiceIfListening()
        if (inFlightJob?.isActive == true) return
        val ic = ic() ?: return
        val target = readTargetText(ic)
        if (target.original.isBlank()) {
            keyboardView?.showError(getString(com.aikeyboard.app.R.string.error_no_text))
            return
        }

        keyboardView?.setBusy(true)
        inFlightJob = serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    corrector.transform(target.original, style)
                }
                val applied = applyResult(target, result)
                when {
                    // Campo perdeu o foco/IC durante a chamada: avisa em vez de
                    // sumir em silêncio (o usuário percebia como "não fez nada").
                    !applied -> keyboardView?.showError(
                        getString(com.aikeyboard.app.R.string.error_apply_failed)
                    )
                    // A IA devolveu o mesmo texto: também é feedback (não é bug).
                    result.trim() == target.original.trim() -> keyboardView?.showInfo(
                        getString(com.aikeyboard.app.R.string.ai_no_changes)
                    )
                }
            } catch (e: GroqException) {
                val message = when (e.code) {
                    "missing_api_key" -> getString(com.aikeyboard.app.R.string.error_no_api_key)
                    "network_error" -> getString(com.aikeyboard.app.R.string.error_network)
                    else -> getString(com.aikeyboard.app.R.string.error_api, e.message)
                }
                keyboardView?.showError(message)
            } catch (e: Throwable) {
                keyboardView?.showError(
                    getString(com.aikeyboard.app.R.string.error_api, e.message ?: "?")
                )
            } finally {
                keyboardView?.setBusy(false)
            }
        }
    }

    private fun ic(): InputConnection? =
        inputConnection ?: currentInputConnection?.also { inputConnection = it }

    /** Há texto selecionado no campo (segundo o espelho local do cursor). */
    private fun hasSelection(): Boolean = selStart != selEnd

    /**
     * Comita [text] e atualiza o espelho do cursor (posição esperada após a
     * edição). Substitui qualquer seleção e colapsa o cursor logo após o texto.
     */
    private fun icCommitText(ic: InputConnection, text: String) {
        ic.commitText(text, 1)
        setExpectedCursor(CursorMath.afterCommit(selStart, selEnd, text.length))
    }

    /**
     * Apaga [count] caracteres antes do cursor e atualiza o espelho. Só deve ser
     * chamado com o cursor colapsado (sem seleção) — os caminhos com seleção são
     * tratados antes via [hasSelection].
     */
    private fun icDeleteBefore(ic: InputConnection, count: Int) {
        if (count <= 0) return
        ic.deleteSurroundingText(count, 0)
        setExpectedCursor(CursorMath.afterDeleteBefore(selStart, selEnd, count))
    }

    private fun setExpectedCursor(pos: Int) {
        selStart = pos
        selEnd = pos
        expectedSelStart = pos
        expectedSelEnd = pos
    }

    /**
     * Lê (via IPC) a palavra REAL imediatamente antes do cursor — fonte da
     * verdade para edições destrutivas, imune à dessincronia da sombra
     * [currentWord]. Só é chamada em ações pontuais (sugestão/gesto), nunca por
     * tecla, então o IPC é irrelevante para a latência.
     */
    private fun realWordBeforeCursor(ic: InputConnection): String? {
        val before = ic.getTextBeforeCursor(MAX_CORRECTION_CONTEXT, 0)?.toString().orEmpty()
        return findLastWordBounds(before)?.let(before::substring)
    }

    /**
     * Sincroniza o espelho do cursor e a palavra em composição com a realidade
     * do campo. Chamado quando o usuário move o cursor / seleciona (detectado em
     * [onUpdateSelection]). Faz UMA leitura de contexto — e só aqui, nunca por
     * tecla.
     */
    private fun resyncCurrentWordFromContext() {
        pendingAutoCorrect = null
        lastGestureCommit = null
        justCommittedSpace = false
        correctionMemo = null
        currentWord.setLength(0)
        if (!hasSelection()) {
            ic()?.let { ic ->
                val before = ic.getTextBeforeCursor(MAX_CORRECTION_CONTEXT, 0)?.toString().orEmpty()
                val bounds = findLastWordBounds(before)
                if (bounds != null) currentWord.append(before.substring(bounds))
            }
        }
        currentWordFlow.value = currentWord.toString()
    }

    /**
     * Callback do sistema a cada mudança de cursor/seleção — inclusive o eco das
     * nossas próprias edições. Se a posição relatada bate com a que esperávamos
     * (`expected*`), foi a nossa edição e não há nada a fazer além de registrar.
     * Caso contrário, o usuário moveu o cursor ou um agente externo editou o
     * campo: ressincronizamos. Também adota a posição relatada como verdade, o
     * que torna o rastreamento auto-corretivo mesmo quando o app não informa a
     * seleção inicial.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // Durante o ditado por voz o texto entra como "composing"; não mexemos.
        if (dictating) {
            selStart = newSelStart
            selEnd = newSelEnd
            return
        }
        val echoOfOurEdit = newSelStart == expectedSelStart && newSelEnd == expectedSelEnd
        selStart = newSelStart
        selEnd = newSelEnd
        if (echoOfOurEdit) return
        expectedSelStart = newSelStart
        expectedSelEnd = newSelEnd
        resyncCurrentWordFromContext()
    }

    private fun resetComposingWord() {
        currentWord.setLength(0)
        currentWordFlow.value = ""
    }

    /** Reseta o shift "one-shot" após um caractere (caps lock permanece). */
    private fun resetShiftAfterChar() {
        if (shiftOn && !capsLock) {
            shiftOn = false
            keyboardView?.setShiftState(shift = false, caps = false)
        }
    }

    private fun setShift(value: Boolean) {
        if (shiftOn != value) {
            shiftOn = value
            keyboardView?.setShiftState(shift = shiftOn, caps = capsLock)
        }
    }

    private fun updateAutoShiftState(ic: InputConnection) {
        if (capsLock) return
        if (!fieldPolicy.autoCap) {
            setShift(false)
            return
        }
        val before = ic.getTextBeforeCursor(MAX_SHIFT_CONTEXT, 0)?.toString().orEmpty()
        setShift(shouldAutoCapitalize(before))
    }

    private fun readTargetText(ic: InputConnection): TargetText {
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrBlank()) {
            return TargetText(
                original = selected,
                replaceBefore = 0,
                replaceAfter = 0,
                hadSelection = true
            )
        }

        val before = ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(4000, 0)?.toString().orEmpty()
        return TargetText(
            original = before + after,
            replaceBefore = before.length,
            replaceAfter = after.length,
            hadSelection = false
        )
    }

    private fun applyResult(target: TargetText, result: String): Boolean {
        val ic = ic() ?: return false
        ic.beginBatchEdit()
        try {
            if (target.hadSelection) {
                ic.commitText(result, 1)
            } else {
                ic.deleteSurroundingText(target.replaceBefore, target.replaceAfter)
                ic.commitText(result, 1)
            }
        } finally {
            ic.endBatchEdit()
        }
        // Edição grande fora dos helpers de espelho: zera o estado de composição
        // e força a próxima atualização de seleção a ressincronizar o cursor.
        pendingAutoCorrect = null
        lastGestureCommit = null
        justCommittedSpace = false
        correctionMemo = null
        currentWord.setLength(0)
        currentWordFlow.value = ""
        expectedSelStart = -1
        expectedSelEnd = -1
        return true
    }

    @Suppress("unused")
    private fun sendKey(keyCode: Int) {
        val ic = ic() ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    companion object {
        private const val MAX_CORRECTION_CONTEXT = 96
        private const val MAX_SHIFT_CONTEXT = 48
        /**
         * Janela de debounce do pipeline de sugestões/correção. 40ms coalesce a
         * digitação rápida em bem menos cálculos de fundo (menos CPU/calor, que
         * em aparelhos com throttling térmico vira lentidão percebida) sem atraso
         * visível na barra. A autocorreção no espaço NÃO depende disto: tem
         * fallback síncrono que lê o texto real e corrige na hora.
         */
        private const val SUGGESTION_DEBOUNCE_MS = 40L
    }
}

/**
 * Snapshot de uma auto-correção aplicada que ainda pode ser desfeita
 * pelo próximo backspace. Resetado ao primeiro keystroke que não seja
 * backspace.
 */
private data class PendingAutoCorrect(
    val original: String,
    val corrected: String
)

/**
 * Par (palavra digitada -> correção) pré-computado em background. `result`
 * nulo significa "não há correção para esta palavra". Imutável: trocado por
 * inteiro numa referência `@Volatile`, garantindo leitura consistente.
 */
private data class CorrectionMemo(
    val word: String,
    val result: String?
)

internal fun findLastWordBounds(textBeforeCursor: String): IntRange? {
    if (textBeforeCursor.isBlank()) return null

    var end = textBeforeCursor.length
    while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
    if (end == 0) return null

    var start = end
    while (start > 0 && isWordChar(textBeforeCursor[start - 1])) start--
    if (start == end) return null

    return start until end
}

/**
 * Como [findLastWordBounds], mas captura o token inteiro entre espaços —
 * incluindo pontuação acidental no meio (ex.: "r.esolver"). Exige terminar em
 * letra, para não capturar pontuação final legítima ("fim.", "ok!").
 */
internal fun findLastTokenBounds(textBeforeCursor: String): IntRange? {
    if (textBeforeCursor.isBlank()) return null

    var end = textBeforeCursor.length
    while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
    if (end == 0 || !textBeforeCursor[end - 1].isLetter()) return null

    var start = end
    while (start > 0 && !textBeforeCursor[start - 1].isWhitespace()) start--
    if (start == end) return null

    return start until end
}

/**
 * `true` quando a palavra que começa em [wordStart] dentro de [before] parece um
 * NOME PRÓPRIO — capitalizada e NO MEIO da frase (não logo após pontuação de fim
 * de frase nem no início do campo). Esses não devem ser autocorrigidos (nomes
 * como "Marcelo", "Itaú", "WhatsApp" não viram outra palavra). No início de frase
 * a maiúscula é só auto-capitalização, então ali a correção continua valendo.
 */
internal fun looksLikeProperNoun(before: String, wordStart: Int, word: String): Boolean {
    if (word.isEmpty() || !word[0].isUpperCase()) return false
    var i = wordStart - 1
    while (i >= 0 && before[i].isWhitespace()) i--
    if (i < 0) return false // início do campo = início de frase
    return when (before[i]) {
        '.', '!', '?', '\n', ':', ';' -> false // início de frase
        else -> true // maiúscula no meio da frase = provável nome próprio
    }
}

internal fun shouldAutoCapitalize(textBeforeCursor: String): Boolean {
    if (textBeforeCursor.isBlank()) return true

    var index = textBeforeCursor.length - 1
    while (index >= 0 && textBeforeCursor[index].isWhitespace()) index--
    if (index < 0) return true

    return when (textBeforeCursor[index]) {
        '.', '!', '?', '\n' -> true
        else -> false
    }
}

private fun isWordChar(value: Char): Boolean = value.isLetter() || value == '\'' || value == '-'

/**
 * Aritmética pura da posição do cursor após nossas edições, para manter o
 * espelho local em sincronia sem precisar consultar o campo. Testável sem Android.
 */
internal object CursorMath {
    /** Posição do cursor após comitar [textLen] chars sobre a seleção [selStart,selEnd]. */
    fun afterCommit(selStart: Int, selEnd: Int, textLen: Int): Int =
        minOf(selStart, selEnd) + textLen

    /** Posição do cursor após apagar [count] chars antes de um cursor colapsado. */
    fun afterDeleteBefore(selStart: Int, selEnd: Int, count: Int): Int =
        (minOf(selStart, selEnd) - count).coerceAtLeast(0)
}

/**
 * Decide se o segundo espaço deve virar ". " (atalho estilo Gboard). [before] são
 * os dois caracteres imediatamente antes do cursor. Só converte quando há um
 * espaço logo antes do cursor precedido de letra/dígito — nunca após pontuação
 * ("?. ") nem em sequências de espaços ("   ").
 */
internal fun shouldConvertSecondSpaceToPeriod(before: String): Boolean {
    if (before.length != 2) return false
    if (before[1] != ' ') return false
    return before[0].isLetterOrDigit()
}

private data class TargetText(
    val original: String,
    val replaceBefore: Int,
    val replaceAfter: Int,
    val hadSelection: Boolean
)

interface KeyboardActionListener {
    fun onChar(c: Char)
    fun onText(text: String)
    fun onBackspace()
    fun onBackspaceWord()
    fun onSpace()
    fun onEnter()
    fun onShift()
    fun onSwitchIme()
    fun onVoiceInput()
    fun onAiAction(style: CorrectionStyle)
    fun onSuggestionTap(candidate: SuggestionCandidate)
    fun onGesture(keys: List<Char>)
    fun onAccentReplace(accent: Char)
}
