package com.aikeyboard.app.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.Handler
import android.util.AttributeSet
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.aikeyboard.app.R
import com.aikeyboard.app.ai.CorrectionStyle
import com.aikeyboard.app.data.KeyboardTheme
import kotlin.math.abs

/**
 * Teclado em modo turbo: uma única View desenha e processa todas as teclas.
 * A área de toque é contínua por linha; os vãos visuais não deixam taps escaparem.
 */
@SuppressLint("ViewConstructor")
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private var scale: Float = 1.0f
) : LinearLayout(context, attrs) {

    var listener: KeyboardActionListener? = null

    private val topBar: FrameLayout
    private val aiBar: LinearLayout
    private val progress: ProgressBar
    private val suggestionStrip: LinearLayout
    private val suggestionSlots: Array<TextView>
    private val keysView: FastKeysView
    private var shift = false
    private var caps = false
    /** Estado do ditado por voz: muda o ícone do 🎤 para ⏹ e liga o spinner. */
    private var listening = false
    private var keyboardMode = KeyboardMode.LETTERS
    /** Lista corrente de sugestões. Mantida pra refletir em re-layout sem nova alocação. */
    private var currentSuggestions: List<SuggestionCandidate> = emptyList()
    /**
     * Feature ligada nos ajustes. Quando true, a faixa de sugestões reserva
     * sua altura PERMANENTEMENTE (barra fixa) — só esta flag liga/desliga o
     * container, nunca o fluxo de digitação. Assim as teclas nunca se mexem.
     */
    private var suggestionsEnabled = false
    /**
     * Estado de destaque atual de cada slot. Evita realocar o RippleDrawable
     * de fundo quando o destaque não mudou (a alocação só acontece na
     * transição cinza<->accent, não a cada palavra).
     */
    private val slotHighlighted = BooleanArray(SUGGESTION_SLOT_COUNT)

    /** Feedback tátil/sonoro a cada tecla. Controlado pelos ajustes. */
    private var hapticEnabled = true
    private var soundEnabled = false
    /** Digitação por gesto (swipe). Controlada pelos ajustes. */
    private var gestureEnabled = true
    /** Tema atual do teclado (resolvido em [color]/[effectiveDark]). */
    private var themeMode = KeyboardTheme.SYSTEM
    /** Cache resId→cor já resolvida para o tema atual (evita lookup por frame). */
    private val colorCache = SparseIntArray()

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Tecla atualmente em "key preview" (a bolha ampliada que sobe acima do
     * dedo). Desenhada pelo [dispatchDraw] do container raiz para poder
     * ultrapassar o topo da grade de teclas. Apenas teclas de caractere.
     */
    private var previewKey: FastKey? = null
    private val previewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val previewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val previewRect = RectF()

    init {
        orientation = VERTICAL
        setBackgroundColor(color(R.color.kb_background))
        setPadding(dp(6), dp(8), dp(6), dp(10))

        topBar = FrameLayout(context).apply {
            background = surfaceBackground(radiusDp = 18)
            layoutParams = LayoutParams(MATCH_PARENT, dp(50)).apply {
                setMargins(dp(2), dp(2), dp(2), dp(8))
            }
        }

        aiBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(38), 0)
        }
        // A barra de ações é rolável horizontalmente: cabem muitas ações de IA
        // sem aumentar a altura nem espremer os botões.
        val aiScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(aiBar, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        }
        topBar.addView(aiScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        progress = ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.GONE
            indeterminateTintList = ColorStateList.valueOf(color(R.color.kb_accent))
        }
        topBar.addView(progress, FrameLayout.LayoutParams(dp(24), dp(24)).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = dp(8)
        })

        addView(topBar)
        buildAiChips()

        // Strip de sugestões: 3 slots fixos, reaproveitados a cada update.
        // Nunca chamamos removeAllViews/addView no hot path — só atualizamos
        // text + visibility, que é praticamente grátis.
        suggestionStrip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(MATCH_PARENT, dp(40)).apply {
                setMargins(dp(2), 0, dp(2), dp(6))
            }
            visibility = View.GONE
        }
        suggestionSlots = Array(SUGGESTION_SLOT_COUNT) { slotIndex ->
            TextView(context).apply {
                setTextColor(color(R.color.kb_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                background = suggestionSlotBackground(highlighted = false)
                isClickable = true
                isFocusable = true
                visibility = View.INVISIBLE
                setOnClickListener {
                    val candidate = currentSuggestions.getOrNull(slotIndex) ?: return@setOnClickListener
                    listener?.onSuggestionTap(candidate)
                }
                layoutParams = LayoutParams(0, MATCH_PARENT, 1f).apply {
                    val edgeMargin = dp(3)
                    setMargins(edgeMargin, dp(2), edgeMargin, dp(2))
                }
            }
        }
        suggestionSlots.forEach(suggestionStrip::addView)
        addView(suggestionStrip)

        keysView = FastKeysView(context)
        addView(keysView, LayoutParams(MATCH_PARENT, keysView.desiredHeight()))
    }

    fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        for (i in 0 until aiBar.childCount) aiBar.getChildAt(i).isEnabled = !busy
    }

    fun setShiftState(shift: Boolean, caps: Boolean) {
        this.shift = shift
        this.caps = caps
        keysView.invalidate()
    }

    /** Liga/desliga o indicador de ditado por voz (ícone do mic + spinner). */
    fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        keysView.invalidate()
    }

    fun setHapticEnabled(enabled: Boolean) { hapticEnabled = enabled }

    fun setSoundEnabled(enabled: Boolean) { soundEnabled = enabled }

    fun setGestureEnabled(enabled: Boolean) {
        gestureEnabled = enabled
    }

    /**
     * Modo de tema do teclado. As cores efetivas são resolvidas em
     * [resolveColor]; aqui só guardamos a escolha e repintamos.
     */
    fun setThemeMode(theme: KeyboardTheme) {
        themeMode = theme
        rebuildPalette()
        refreshThemedChrome()
        keysView.invalidate()
        invalidate()
    }

    /**
     * Reaplica as cores do "chrome" (fundo, barra superior, faixa de sugestões
     * e chips) a partir da paleta do tema atual. A grade de teclas e o preview
     * leem as cores direto no `onDraw`/`dispatchDraw`, então basta invalidar.
     */
    private fun refreshThemedChrome() {
        setBackgroundColor(color(R.color.kb_background))
        topBar.background = surfaceBackground(radiusDp = 18)
        for (i in 0 until suggestionSlots.size) {
            val highlighted = slotHighlighted[i]
            suggestionSlots[i].background = suggestionSlotBackground(highlighted)
            suggestionSlots[i].setTextColor(
                color(if (highlighted) R.color.white else R.color.kb_text)
            )
        }
        buildAiChips()
    }

    /**
     * Feedback de uma tecla: vibração curta (KEYBOARD_TAP, processada fora da
     * UI thread pelo sistema) + clique opcional. Chamado no ACTION_DOWN, então
     * o retorno é imediato ao toque, sem esperar o caractere ser comitado.
     */
    private fun performKeyFeedback() {
        if (hapticEnabled) {
            performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        }
        if (soundEnabled) {
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    /**
     * Atualiza a tecla em key-preview e repinta o container. O teclado é
     * hardware-accelerated, então a grade de teclas é recomposta a partir da
     * sua display list (sem re-executar onDraw) — barato o bastante para o
     * caminho quente.
     */
    private fun updatePreview(key: FastKey?) {
        if (previewKey === key) return
        previewKey = key
        invalidate()
    }

    /**
     * Desenha o key-preview por cima de TODOS os filhos (inclusive a barra),
     * para que a bolha das teclas da primeira fileira possa subir acima da
     * grade. Custa um round-rect + um glyph por frame, só enquanto há um dedo
     * numa tecla de caractere — zero alocação no caminho quente.
     */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val key = previewKey ?: return
        val char = key.spec.char ?: return

        val offsetX = keysView.left.toFloat()
        val offsetY = keysView.top.toFloat()
        val keyLeft = offsetX + key.drawRect.left
        val keyRight = offsetX + key.drawRect.right
        val keyTop = offsetY + key.drawRect.top
        val centerX = (keyLeft + keyRight) / 2f

        val pad = dp(PREVIEW_SIDE_PAD).toFloat()
        val rise = dp(PREVIEW_RISE).toFloat()
        var bubbleLeft = keyLeft - pad
        var bubbleRight = keyRight + pad
        // Mantém a bolha dentro da largura do teclado.
        val minLeft = paddingLeft.toFloat()
        val maxRight = (width - paddingRight).toFloat()
        if (bubbleLeft < minLeft) { bubbleRight += minLeft - bubbleLeft; bubbleLeft = minLeft }
        if (bubbleRight > maxRight) { bubbleLeft -= bubbleRight - maxRight; bubbleRight = maxRight }
        val bubbleTop = keyTop - rise
        val bubbleBottom = offsetY + key.drawRect.bottom
        previewRect.set(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom)

        previewFillPaint.color = color(R.color.kb_key_special_bg)
        previewStrokePaint.color = color(R.color.kb_accent)
        previewStrokePaint.strokeWidth = dp(2).toFloat()
        previewTextPaint.color = color(R.color.kb_text)
        previewTextPaint.textSize = dp(30).toFloat()

        val radius = dp(14).toFloat()
        canvas.drawRoundRect(previewRect, radius, radius, previewFillPaint)
        canvas.drawRoundRect(previewRect, radius, radius, previewStrokePaint)

        val label = if (shift || caps) char.uppercaseChar().toString() else char.toString()
        // Centraliza o glyph na metade SUPERIOR da bolha (acima da tecla).
        val textCenterY = bubbleTop + rise / 2f
        val baseline = textCenterY - (previewTextPaint.descent() + previewTextPaint.ascent()) / 2f
        canvas.drawText(label, centerX, baseline, previewTextPaint)
    }

    fun setScale(newScale: Float) {
        if (newScale == scale) return
        scale = newScale
        setPadding(dp(6), dp(8), dp(6), dp(10))
        (keysView.layoutParams as? LayoutParams)?.let { lp ->
            lp.height = keysView.desiredHeight()
            keysView.layoutParams = lp
        }
        keysView.invalidateKeyLayout()
    }

    fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun showInfo(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun setUndoEnabled(enabled: Boolean) = Unit

    fun setSuggestionsEnabled(enabled: Boolean) {
        suggestionsEnabled = enabled
        // ÚNICO ponto que liga/desliga o container. A faixa fica VISIBLE o
        // tempo todo enquanto a feature está ligada — mesmo vazia — então sua
        // altura é constante e as teclas nunca pulam. O fluxo de digitação
        // (setSuggestions) jamais mexe na visibilidade do container.
        suggestionStrip.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            currentSuggestions = emptyList()
            blankAllSlots()
        }
    }

    /**
     * Atualiza os 3 slots da faixa FIXA. Não cria Views e NÃO altera a
     * visibilidade do container — só o texto/visibilidade dos slots já
     * existentes. Custa praticamente nada e nunca dispara layout pass do
     * teclado (as teclas não se mexem), então pode rodar em digitação veloz.
     *
     * Ordem semântica dos slots:
     *   slot 0 = palavra original digitada (cinza),
     *   slot 1 = melhor correção/sugestão (destacada com accent),
     *   slot 2 = próxima sugestão (cinza).
     *
     * Slots sem candidato ficam INVISIBLE (não GONE) pra manter o espaço.
     */
    fun setSuggestions(suggestions: List<SuggestionCandidate>) {
        currentSuggestions = suggestions
        if (!suggestionsEnabled) return
        for (i in suggestionSlots.indices) {
            val slot = suggestionSlots[i]
            val candidate = suggestions.getOrNull(i)
            if (candidate == null) {
                if (slot.visibility != View.INVISIBLE) slot.visibility = View.INVISIBLE
                if (slot.text.isNotEmpty()) slot.text = ""
            } else {
                slot.visibility = View.VISIBLE
                slot.text = candidate.value
                val highlighted = candidate.type == SuggestionType.CORRECTION
                // Só realoca o fundo quando o destaque realmente mudou.
                if (slotHighlighted[i] != highlighted) {
                    slotHighlighted[i] = highlighted
                    slot.background = suggestionSlotBackground(highlighted = highlighted)
                    slot.setTextColor(color(if (highlighted) R.color.white else R.color.kb_text))
                }
            }
        }
    }

    private fun blankAllSlots() {
        for (slot in suggestionSlots) {
            slot.visibility = View.INVISIBLE
            slot.text = ""
        }
    }

    private fun suggestionSlotBackground(highlighted: Boolean): RippleDrawable {
        val base = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(
                color(if (highlighted) R.color.kb_accent else R.color.kb_surface_raised)
            )
            if (!highlighted) setStroke(dp(1), color(R.color.kb_stroke))
        }
        val ripple = color(
            if (highlighted) R.color.kb_accent_pressed else R.color.kb_key_bg_pressed
        )
        return RippleDrawable(ColorStateList.valueOf(ripple), base, null)
    }

    private fun buildAiChips() {
        aiBar.removeAllViews()
        AI_ACTIONS.forEach { chip ->
            val tv = TextView(context).apply {
                text = context.getString(chip.labelRes)
                setTextColor(
                    color(
                        if (chip.tone == ChipTone.SECONDARY) {
                            R.color.kb_text
                        } else {
                            R.color.white
                        }
                    )
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                background = chipBackground(chip.tone)
                setPadding(dp(14), 0, dp(14), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { listener?.onAiAction(chip.style) }
            }
            aiBar.addView(tv, LayoutParams(WRAP_CONTENT, dp(34)).apply {
                marginEnd = dp(6)
            })
        }
    }

    private fun switchMode(mode: KeyboardMode) {
        if (mode == keyboardMode) return
        keyboardMode = mode
        keysView.cancelTouchWork()
        keysView.invalidateKeyLayout()
    }

    /** True se [char] abre algum popup de toque-longo (número e/ou acentos). */
    private fun hasLongPress(char: Char): Boolean =
        TOP_ROW_NUMERALS.containsKey(char.lowercaseChar()) || AccentVariants.hasVariants(char)

    /**
     * Variantes do popup de toque-longo: o número da fileira superior (quando
     * houver) seguido dos acentos. O caso comum (vogais da 1ª fileira) oferece
     * número + acentos numa só bolha.
     */
    private fun longPressVariants(char: Char): List<Char> {
        val numeral = TOP_ROW_NUMERALS[char.lowercaseChar()]
        val accents = AccentVariants.variantsFor(char)
        return when {
            numeral == null -> accents
            accents.isEmpty() -> listOf(numeral)
            else -> ArrayList<Char>(accents.size + 1).apply {
                add(numeral)
                addAll(accents)
            }
        }
    }

    /**
     * Cor temática para [resId] (uma das cores `kb_*` do tema escuro). No tema
     * claro, mapeia para a variante `kb_light_*`. O resultado é cacheado por
     * tema, então o `onDraw`/`dispatchDraw` nunca paga resource lookup por frame.
     */
    private fun color(resId: Int): Int {
        val idx = colorCache.indexOfKey(resId)
        if (idx >= 0) return colorCache.valueAt(idx)
        val effective = if (effectiveDark()) resId else lightVariantOrSelf(resId)
        val value = ContextCompat.getColor(context, effective)
        colorCache.put(resId, value)
        return value
    }

    /** Tema efetivo: SYSTEM resolve pelo modo noturno do aparelho. */
    private fun effectiveDark(): Boolean = when (themeMode) {
        KeyboardTheme.DARK -> true
        KeyboardTheme.LIGHT -> false
        KeyboardTheme.SYSTEM ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun lightVariantOrSelf(resId: Int): Int = when (resId) {
        R.color.kb_background -> R.color.kb_light_background
        R.color.kb_surface -> R.color.kb_light_surface
        R.color.kb_surface_raised -> R.color.kb_light_surface_raised
        R.color.kb_key_bg -> R.color.kb_light_key_bg
        R.color.kb_key_bg_pressed -> R.color.kb_light_key_bg_pressed
        R.color.kb_key_special_bg -> R.color.kb_light_key_special_bg
        R.color.kb_key_special_pressed -> R.color.kb_light_key_special_pressed
        R.color.kb_stroke -> R.color.kb_light_stroke
        R.color.kb_text -> R.color.kb_light_text
        R.color.kb_text_muted -> R.color.kb_light_text_muted
        R.color.kb_accent -> R.color.kb_light_accent
        R.color.kb_accent_pressed -> R.color.kb_light_accent_pressed
        R.color.kb_whatsapp -> R.color.kb_light_whatsapp
        R.color.kb_whatsapp_pressed -> R.color.kb_light_whatsapp_pressed
        else -> resId
    }

    private fun rebuildPalette() {
        colorCache.clear()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // Mudança de modo noturno do sistema (relevante quando o tema é SYSTEM).
        rebuildPalette()
        refreshThemedChrome()
        keysView.invalidateKeyLayout()
        invalidate()
    }

    private fun dp(value: Int): Int = (value * scale * resources.displayMetrics.density).toInt()

    private fun surfaceBackground(radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color(R.color.kb_surface))
            setStroke(dp(1), color(R.color.kb_stroke))
        }

    private fun chipBackground(tone: ChipTone): RippleDrawable {
        val base = GradientDrawable().apply {
            cornerRadius = dp(17).toFloat()
            setColor(
                color(
                    when (tone) {
                        ChipTone.PRIMARY -> R.color.kb_accent
                        ChipTone.WHATSAPP -> R.color.kb_whatsapp
                        ChipTone.SECONDARY -> R.color.kb_surface_raised
                    }
                )
            )
            if (tone == ChipTone.SECONDARY) setStroke(dp(1), color(R.color.kb_stroke))
        }
        val ripple = color(
            when (tone) {
                ChipTone.PRIMARY -> R.color.kb_accent_pressed
                ChipTone.WHATSAPP -> R.color.kb_whatsapp_pressed
                ChipTone.SECONDARY -> R.color.kb_key_bg_pressed
            }
        )
        return RippleDrawable(ColorStateList.valueOf(ripple), base, null)
    }

    private inner class FastKeysView(context: Context) : View(context) {
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val specialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val specialPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Paints/rects do popup de acentos — pré-alocados (zero garbage por frame).
        private val accentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val accentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val accentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        private val accentHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val accentBubbleRect = RectF()
        private val accentCharRect = RectF()
        // Número pequeno no canto das teclas da fileira superior.
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }

        private val keys = mutableListOf<FastKey>()
        private val handler = Handler(android.os.Looper.getMainLooper())
        private var layoutWidth = -1
        private var downAt = 0L

        private var pressedKey: FastKey? = null
        private var showAccentsFor: FastKey? = null
        private var accentVariants: List<Char> = emptyList()
        private var selectedAccentIndex: Int = -1
        private var downX = 0f
        private var downY = 0f
        private var inAccentMode = false

        /**
         * Ponteiro (dedo) que controla gesto/toque-longo/preview. É o PRIMEIRO a
         * tocar; os demais dedos (multitoque) só comitam o caractere no seu
         * próprio DOWN — é o que permite digitar veloz com dois polegares sem
         * perder a tecla do segundo dedo. [MotionEvent.INVALID_POINTER_ID] = nenhum.
         */
        private var activePointerId = MotionEvent.INVALID_POINTER_ID

        /**
         * Estado de shift/caps capturado no instante do DOWN. Como o caractere é
         * comitado já no toque (e o shift "one-shot" zera nesse momento), o popup
         * de acento precisa lembrar a intenção do toque para acentuar em MAIÚSCULA.
         */
        private var pressShift = false

        /**
         * Verdadeiro entre o DOWN e o UP enquanto a tecla base ainda não virou
         * acento. O caractere já foi comitado no DOWN; o UP não deve recomitar.
         */
        private var committedOnDown = false

        // --- Estado da digitação por gesto (swipe) ---
        private var gesturing = false
        private var gestureStartKey: FastKey? = null
        private val gesturePath = ArrayList<Char>(16)
        private val gestureTrace = Path()
        private val gestureTracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val longPressRunnable = Runnable {
            val key = pressedKey ?: return@Runnable
            val char = key.spec.char ?: return@Runnable
            val variants = longPressVariants(char)
            if (variants.isNotEmpty()) {
                inAccentMode = true
                showAccentsFor = key
                accentVariants = if (pressShift) variants.map { it.uppercaseChar() } else variants
                selectedAccentIndex = 0
                updatePreview(null)
                invalidate()
            }
        }

        private val backspaceRepeat = object : Runnable {
            override fun run() {
                val held = android.os.SystemClock.uptimeMillis() - downAt
                if (held > 1100) {
                    listener?.onBackspaceWord()
                    handler.postDelayed(this, 95)
                } else {
                    listener?.onBackspace()
                    handler.postDelayed(this, 55)
                }
            }
        }

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = false
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }

        fun desiredHeight(): Int = rowHeightPx() * ROW_COUNT

        fun invalidateKeyLayout() {
            keys.clear()
            layoutWidth = -1
            if (width > 0) ensureKeyLayout(width)
            invalidate()
        }

        fun cancelTouchWork() {
            handler.removeCallbacks(backspaceRepeat)
        }

        override fun onDetachedFromWindow() {
            cancelTouchWork()
            handler.removeCallbacks(longPressRunnable)
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            keys.clear()
            layoutWidth = -1
            if (w > 0) ensureKeyLayout(w)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            ensureKeyLayout(width)

            keyPaint.color = color(R.color.kb_key_bg)
            keyPressedPaint.color = color(R.color.kb_key_bg_pressed)
            specialPaint.color = color(R.color.kb_key_special_bg)
            specialPressedPaint.color = color(R.color.kb_key_special_pressed)
            strokePaint.color = color(R.color.kb_stroke)
            strokePaint.strokeWidth = dp(1).toFloat()
            textPaint.color = color(R.color.kb_text)
            labelPaint.color = color(R.color.kb_text)
            hintPaint.color = color(R.color.kb_text_muted)
            hintPaint.textSize = dp(10).toFloat()

            val radius = dp(12).toFloat()
            val specialTextSize = dp(14).toFloat()
            val charTextSize = dp(18).toFloat()
            // Loop indexado (sem Iterator) — este método roda por frame.
            for (i in keys.indices) {
                val key = keys[i]
                val isPressed = key == pressedKey && !inAccentMode
                val paintToUse = if (key.spec.special) {
                    if (isPressed) specialPressedPaint else specialPaint
                } else {
                    if (isPressed) keyPressedPaint else keyPaint
                }

                canvas.drawRoundRect(key.drawRect, radius, radius, paintToUse)
                canvas.drawRoundRect(key.drawRect, radius, radius, strokePaint)

                val paint = if (key.spec.special) labelPaint else textPaint
                paint.textSize = if (key.spec.special) specialTextSize else charTextSize
                val centerY = key.drawRect.centerY() - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(key.label(), key.drawRect.centerX(), centerY, paint)

                val hint = key.cornerHint
                if (hint != null) {
                    canvas.drawText(
                        hint,
                        key.drawRect.right - dp(9),
                        key.drawRect.top + dp(14),
                        hintPaint
                    )
                }
            }

            // Trilha do gesto (swipe), desenhada por cima das teclas.
            if (gesturing) {
                gestureTracePaint.color = color(R.color.kb_accent)
                gestureTracePaint.alpha = 190
                gestureTracePaint.strokeWidth = dp(6).toFloat()
                canvas.drawPath(gestureTrace, gestureTracePaint)
            }

            // Popup de acentos (long-press) — desenhado com paints/rects
            // pré-alocados, sem criar objetos por frame.
            if (inAccentMode && showAccentsFor != null) {
                val variantWidth = dp(42)
                val totalWidth = accentVariants.size * variantWidth
                val viewWidth = width.toFloat()
                val keyCenterX = showAccentsFor!!.drawRect.centerX()
                var bubbleLeft = keyCenterX - totalWidth / 2f
                if (bubbleLeft < dp(4)) bubbleLeft = dp(4).toFloat()
                if (bubbleLeft + totalWidth > viewWidth - dp(4)) {
                    bubbleLeft = viewWidth - dp(4) - totalWidth
                }

                val bubbleTop = showAccentsFor!!.drawRect.top - dp(52)
                val bubbleHeight = dp(46).toFloat()
                accentBubbleRect.set(bubbleLeft, bubbleTop, bubbleLeft + totalWidth, bubbleTop + bubbleHeight)

                accentFillPaint.color = color(R.color.kb_surface_raised)
                accentStrokePaint.color = color(R.color.kb_stroke)
                accentStrokePaint.strokeWidth = dp(1).toFloat()
                accentTextPaint.textSize = dp(20).toFloat()

                val bubbleRadius = dp(12).toFloat()
                canvas.drawRoundRect(accentBubbleRect, bubbleRadius, bubbleRadius, accentFillPaint)
                canvas.drawRoundRect(accentBubbleRect, bubbleRadius, bubbleRadius, accentStrokePaint)

                for (i in accentVariants.indices) {
                    val char = accentVariants[i]
                    val charLeft = bubbleLeft + i * variantWidth
                    accentCharRect.set(
                        charLeft + dp(2),
                        bubbleTop + dp(3),
                        charLeft + dp(40),
                        bubbleTop + bubbleHeight - dp(3)
                    )

                    if (i == selectedAccentIndex) {
                        accentHighlightPaint.color = color(R.color.kb_accent)
                        canvas.drawRoundRect(accentCharRect, dp(8).toFloat(), dp(8).toFloat(), accentHighlightPaint)
                        accentTextPaint.color = color(R.color.white)
                        accentTextPaint.typeface = Typeface.DEFAULT_BOLD
                    } else {
                        accentTextPaint.color = color(R.color.kb_text)
                        accentTextPaint.typeface = Typeface.DEFAULT
                    }

                    val centerY = accentCharRect.centerY() -
                        (accentTextPaint.descent() + accentTextPaint.ascent()) / 2f
                    canvas.drawText(char.toString(), accentCharRect.centerX(), centerY, accentTextPaint)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    ensureKeyLayout(width)
                    activePointerId = event.getPointerId(0)
                    val key = keyAt(event.x, event.y)
                    pressedKey = key
                    downX = event.x
                    downY = event.y
                    inAccentMode = false
                    showAccentsFor = null
                    gesturing = false
                    committedOnDown = false
                    gesturePath.clear()
                    pressShift = shift || caps
                    gestureStartKey = key?.takeIf { it.spec.action == FastAction.Char }
                    if (key != null) {
                        performKeyFeedback()
                        if (key.spec.action == FastAction.Char) updatePreview(key)
                        // DIGITAÇÃO INSTANTÂNEA: caractere/texto/espaço/backspace
                        // atuam JÁ no toque, não ao soltar. Teclas especiais
                        // (shift/enter/modo/voz/idioma) ficam no UP (permite
                        // deslizar pra fora e cancelar).
                        if (actsOnDown(key.spec.action)) {
                            committedOnDown = true
                            commitOnDown(key)
                            if (key.spec.action == FastAction.Char && key.spec.char != null &&
                                hasLongPress(key.spec.char)
                            ) {
                                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                            }
                        }
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Dedo adicional (digitar veloz com dois polegares). Comita só
                    // caractere/texto/espaço no próprio toque — é o que impede a
                    // tecla do segundo dedo de ser DESCARTADA. Gesto/long-press
                    // continuam exclusivos do dedo primário; enquanto há popup de
                    // acento ou gesto em andamento, o segundo dedo é ignorado (não
                    // intercala um caractere no meio do estado modal do primário).
                    if (!inAccentMode && !gesturing) {
                        val idx = event.actionIndex
                        val key = keyAt(event.getX(idx), event.getY(idx))
                        if (key != null && actsOnDown(key.spec.action) &&
                            key.spec.action != FastAction.Backspace
                        ) {
                            performKeyFeedback()
                            commitOnDown(key)
                            // Um segundo dedo digitando = não é toque-longo.
                            handler.removeCallbacks(longPressRunnable)
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val pIdx = event.findPointerIndex(activePointerId)
                    if (pIdx < 0) return true
                    val mx = event.getX(pIdx)
                    val my = event.getY(pIdx)
                    if (inAccentMode && showAccentsFor != null) {
                        val totalWidth = accentVariants.size * dp(42)
                        val viewWidth = width.toFloat()
                        val keyCenterX = showAccentsFor!!.drawRect.centerX()
                        var bubbleLeft = keyCenterX - totalWidth / 2f
                        if (bubbleLeft < dp(4)) bubbleLeft = dp(4).toFloat()
                        if (bubbleLeft + totalWidth > viewWidth - dp(4)) {
                            bubbleLeft = viewWidth - dp(4) - totalWidth
                        }
                        val relativeX = mx - bubbleLeft
                        val index = (relativeX / dp(42)).toInt().coerceIn(0, accentVariants.size - 1)
                        if (selectedAccentIndex != index) {
                            selectedAccentIndex = index
                            invalidate()
                        }
                    } else if (gesturing) {
                        handleGestureMove(mx, my)
                    } else {
                        if (abs(mx - downX) > dp(16) || abs(my - downY) > dp(16)) {
                            handler.removeCallbacks(longPressRunnable)
                        }
                        val currentKey = keyAt(mx, my)
                        val movedEnough = abs(mx - downX) > dp(GESTURE_START_THRESHOLD) ||
                            abs(my - downY) > dp(GESTURE_START_THRESHOLD)
                        if (gestureEnabled && gestureStartKey != null && movedEnough &&
                            currentKey != null && currentKey.spec.action == FastAction.Char &&
                            currentKey !== gestureStartKey
                        ) {
                            beginGesture(currentKey, mx, my)
                        } else if (!committedOnDown && pressedKey != currentKey) {
                            // Só teclas especiais (não comitadas no DOWN) seguem o
                            // dedo; um caractere já comitado não muda ao deslizar.
                            pressedKey = currentKey
                            invalidate()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // Se o dedo que levantou é o primário, finaliza gesto/acento.
                    if (event.getPointerId(event.actionIndex) == activePointerId) {
                        val idx = event.actionIndex
                        finishActivePointer(realUp = true, upX = event.getX(idx), upY = event.getY(idx))
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val pIdx = event.findPointerIndex(activePointerId)
                    if (pIdx >= 0) {
                        finishActivePointer(realUp = true, upX = event.getX(pIdx), upY = event.getY(pIdx))
                    } else {
                        finishActivePointer(realUp = true, upX = event.x, upY = event.y)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishActivePointer(realUp = false, upX = event.x, upY = event.y)
                    return true
                }
            }
            return true
        }

        /**
         * Finaliza o dedo primário: confirma gesto (só com [MIN_GESTURE_KEYS]+
         * teclas — um simples "rolar" do dedo entre teclas vizinhas NÃO vira
         * swipe e o caractere comitado no toque permanece), aplica o acento do
         * toque-longo, ou dispara a tecla especial (que age no UP). Char/texto/
         * espaço/backspace já agiram no DOWN e nunca são redisparados aqui.
         */
        private fun finishActivePointer(realUp: Boolean, upX: Float, upY: Float) {
            cancelTouchWork()
            handler.removeCallbacks(longPressRunnable)
            when {
                gesturing -> {
                    if (realUp && gesturePath.size >= MIN_GESTURE_KEYS) {
                        listener?.onGesture(ArrayList(gesturePath))
                    }
                    gestureTrace.reset()
                }
                inAccentMode && showAccentsFor != null -> {
                    if (realUp) {
                        accentVariants.getOrNull(selectedAccentIndex)?.let {
                            listener?.onAccentReplace(it)
                        }
                    }
                }
                else -> {
                    val key = pressedKey
                    if (realUp && key != null && !committedOnDown && key == keyAt(upX, upY)) {
                        dispatchSpecial(key)
                    }
                }
            }
            pressedKey = null
            showAccentsFor = null
            inAccentMode = false
            gesturing = false
            gestureStartKey = null
            committedOnDown = false
            activePointerId = MotionEvent.INVALID_POINTER_ID
            updatePreview(null)
            parent?.requestDisallowInterceptTouchEvent(false)
            invalidate()
        }

        /** Inicia o rastreamento do swipe a partir da tecla inicial. */
        private fun beginGesture(currentKey: FastKey, x: Float, y: Float) {
            gesturing = true
            handler.removeCallbacks(longPressRunnable)
            updatePreview(null)
            pressedKey = null
            gesturePath.clear()
            gestureStartKey?.spec?.char?.let { gesturePath.add(it) }
            currentKey.spec.char?.let {
                if (gesturePath.isEmpty() || gesturePath[gesturePath.size - 1] != it) gesturePath.add(it)
            }
            gestureTrace.reset()
            val start = gestureStartKey
            if (start != null) {
                gestureTrace.moveTo(start.drawRect.centerX(), start.drawRect.centerY())
            } else {
                gestureTrace.moveTo(x, y)
            }
            gestureTrace.lineTo(x, y)
            invalidate()
        }

        /** Acumula o traço e as letras visitadas durante o swipe. */
        private fun handleGestureMove(x: Float, y: Float) {
            gestureTrace.lineTo(x, y)
            val c = keyAt(x, y)?.spec?.char
            if (c != null && (gesturePath.isEmpty() || gesturePath[gesturePath.size - 1] != c)) {
                gesturePath.add(c)
            }
            invalidate()
        }

        /** Ações que atuam no ACTION_DOWN (digitação instantânea). */
        private fun actsOnDown(action: FastAction): Boolean = when (action) {
            FastAction.Char, FastAction.Text, FastAction.Space, FastAction.Backspace -> true
            else -> false
        }

        /** Dispara, já no toque, as teclas que atuam no DOWN. */
        private fun commitOnDown(key: FastKey) {
            when (key.spec.action) {
                FastAction.Char -> key.spec.char?.let { listener?.onChar(it) }
                FastAction.Text -> key.spec.text?.let { listener?.onText(it) }
                FastAction.Space -> listener?.onSpace()
                FastAction.Backspace -> {
                    downAt = android.os.SystemClock.uptimeMillis()
                    listener?.onBackspace()
                    handler.postDelayed(backspaceRepeat, BACKSPACE_REPEAT_DELAY_MS)
                }
                else -> Unit
            }
        }

        /** Teclas especiais (toggles/ações) que atuam ao soltar (UP). */
        private fun dispatchSpecial(key: FastKey) {
            when (key.spec.action) {
                FastAction.Enter -> listener?.onEnter()
                FastAction.Shift -> listener?.onShift()
                FastAction.Voice -> listener?.onVoiceInput()
                FastAction.SwitchIme -> listener?.onSwitchIme()
                FastAction.ModeSymbols -> switchMode(KeyboardMode.SYMBOLS)
                FastAction.ModeLetters -> switchMode(KeyboardMode.LETTERS)
                FastAction.ModeEmojis -> switchMode(KeyboardMode.EMOJIS)
                else -> Unit
            }
        }

        private fun ensureKeyLayout(viewWidth: Int) {
            if (viewWidth <= 0 || (layoutWidth == viewWidth && keys.isNotEmpty())) return
            layoutWidth = viewWidth
            keys.clear()

            val rows = when (keyboardMode) {
                KeyboardMode.LETTERS -> letterRows()
                KeyboardMode.SYMBOLS -> symbolRows()
                KeyboardMode.EMOJIS -> emojiRows()
            }
            val rowHeight = rowHeightPx().toFloat()
            val visualGap = dp(4).toFloat()
            rows.forEachIndexed { rowIndex, row ->
                val y = rowIndex * rowHeight
                val leftPad = if (row.indentSecondRow) dp(16).toFloat() else 0f
                val availableWidth = viewWidth - leftPad * 2f
                val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
                var x = leftPad
                row.keys.forEach { spec ->
                    val keyWidth = availableWidth * spec.weight / totalWeight
                    keys += FastKey(
                        spec = spec,
                        rowIndex = rowIndex,
                        drawRect = RectF(
                            x + visualGap / 2f,
                            y + dp(3),
                            x + keyWidth - visualGap / 2f,
                            y + rowHeight - dp(3)
                        ),
                        hitRect = RectF(
                            x,
                            y,
                            x + keyWidth,
                            y + rowHeight
                        ),
                        cornerHint = spec.char?.let { TOP_ROW_NUMERALS[it.lowercaseChar()]?.toString() }
                    )
                    x += keyWidth
                }
            }
        }

        private fun keyAt(x: Float, y: Float): FastKey? {
            if (x < -dp(18) || x > width + dp(18) || y < 0f || y > desiredHeight()) return null
            // Loops indexados (sem Iterator) — chamado a cada ponto do swipe.
            for (i in keys.indices) {
                val key = keys[i]
                if (key.hitRect.contains(x, y)) return key
            }

            val row = (y / rowHeightPx()).toInt().coerceIn(0, ROW_COUNT - 1)
            var best: FastKey? = null
            var bestDistance = Float.MAX_VALUE
            for (i in keys.indices) {
                val key = keys[i]
                if (key.rowIndex != row) continue
                val distance = abs(x - key.hitRect.centerX())
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = key
                }
            }
            return best
        }

        private fun rowHeightPx(): Int = dp(52)

        private fun FastKey.label(): String = when {
            spec.action == FastAction.Voice -> if (listening) "⏹" else "🎤"
            spec.char != null -> if (shift) spec.upperLabel else spec.label
            else -> spec.label
        }

        private fun letterRows(): List<FastRow> = listOf(
            FastRow("qwertyuiop".map { FastKeySpec.char(it) }),
            FastRow("asdfghjkl".map { FastKeySpec.char(it) }, indentSecondRow = true),
            FastRow(
                listOf(FastKeySpec.special("⇧", FastAction.Shift, 1.5f)) +
                    "zxcvbnm".map { FastKeySpec.char(it) } +
                    FastKeySpec.special("⌫", FastAction.Backspace, 1.5f)
            ),
            FastRow(
                listOf(
                    FastKeySpec.special("?123", FastAction.ModeSymbols, 1.5f),
                    FastKeySpec.special("😊", FastAction.ModeEmojis, 1.2f),
                    FastKeySpec.special("🌐", FastAction.SwitchIme, 1.2f),
                    FastKeySpec.special("🎤", FastAction.Voice, 1.2f),
                    FastKeySpec.char(',', 1f),
                    FastKeySpec.special(context.getString(R.string.key_space), FastAction.Space, 3.2f),
                    FastKeySpec.char('.', 1f),
                    FastKeySpec.special("↵", FastAction.Enter, 1.5f)
                )
            )
        )

        private fun symbolRows(): List<FastRow> = listOf(
            FastRow("1234567890".map { FastKeySpec.char(it) }),
            FastRow("@#\$%&*-+=".map { FastKeySpec.char(it) }),
            FastRow(
                listOf('(', ')', '/', ':', ';', '"', '\'', '!', '?').map { FastKeySpec.char(it) } +
                    FastKeySpec.special("⌫", FastAction.Backspace, 1.5f)
            ),
            FastRow(
                listOf(
                    FastKeySpec.special("ABC", FastAction.ModeLetters, 1.5f),
                    FastKeySpec.special("😊", FastAction.ModeEmojis, 1.2f),
                    FastKeySpec.special("🌐", FastAction.SwitchIme, 1.2f),
                    FastKeySpec.special("🎤", FastAction.Voice, 1.2f),
                    FastKeySpec.char(',', 1f),
                    FastKeySpec.special(context.getString(R.string.key_space), FastAction.Space, 3.2f),
                    FastKeySpec.char('.', 1f),
                    FastKeySpec.special("↵", FastAction.Enter, 1.5f)
                )
            )
        )

        private fun emojiRows(): List<FastRow> = listOf(
            FastRow(listOf("😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎", "🥳", "🤩").map {
                FastKeySpec.text(it)
            }),
            FastRow(listOf("👍", "🙏", "👏", "🙌", "💪", "🔥", "🎉", "❤️", "✨", "✅").map {
                FastKeySpec.text(it)
            }),
            FastRow(
                listOf(
                    FastKeySpec.special("ABC", FastAction.ModeLetters, 1.5f)
                ) +
                    listOf("😉", "🤔", "😅", "😭", "😴", "😢", "😡").map { FastKeySpec.text(it) } +
                    FastKeySpec.special("⌫", FastAction.Backspace, 1.5f)
            ),
            FastRow(
                listOf(
                    FastKeySpec.special("?123", FastAction.ModeSymbols, 1.5f),
                    FastKeySpec.special("🌐", FastAction.SwitchIme, 1.2f),
                    FastKeySpec.text("😂", 1f),
                    FastKeySpec.special(context.getString(R.string.key_space), FastAction.Space, 4f),
                    FastKeySpec.text("❤️", 1f),
                    FastKeySpec.special("↵", FastAction.Enter, 1.5f)
                )
            )
        )
    }

    private data class AiChip(
        val labelRes: Int,
        val style: CorrectionStyle,
        val tone: ChipTone
    )

    private data class FastRow(
        val keys: List<FastKeySpec>,
        val indentSecondRow: Boolean = false
    )

    private data class FastKey(
        val spec: FastKeySpec,
        val rowIndex: Int,
        val drawRect: RectF,
        val hitRect: RectF,
        /** Número exibido no canto (fileira superior); null se não houver. */
        val cornerHint: String? = null
    )

    private data class FastKeySpec(
        val label: String,
        val action: FastAction,
        val weight: Float,
        val char: Char?,
        val text: String?,
        val special: Boolean,
        /** Rótulo em maiúscula, pré-computado para não alocar String por frame. */
        val upperLabel: String = label
    ) {
        companion object {
            fun char(value: Char, weight: Float = 1f): FastKeySpec =
                FastKeySpec(
                    label = value.toString(),
                    action = FastAction.Char,
                    weight = weight,
                    char = value,
                    text = null,
                    special = false,
                    upperLabel = value.uppercaseChar().toString()
                )

            fun text(value: String, weight: Float = 1f): FastKeySpec =
                FastKeySpec(value, FastAction.Text, weight, null, value, false)

            fun special(label: String, action: FastAction, weight: Float): FastKeySpec =
                FastKeySpec(label, action, weight, null, null, true)
        }
    }

    private enum class FastAction {
        Char,
        Text,
        Shift,
        Backspace,
        Space,
        Enter,
        Voice,
        SwitchIme,
        ModeSymbols,
        ModeLetters,
        ModeEmojis
    }

    private enum class KeyboardMode {
        LETTERS,
        SYMBOLS,
        EMOJIS
    }

    private enum class ChipTone {
        PRIMARY,
        WHATSAPP,
        SECONDARY
    }

    companion object {
        private const val ROW_COUNT = 4
        private const val SUGGESTION_SLOT_COUNT = 3
        /** Quanto a bolha de preview sobe acima da tecla (dp). */
        private const val PREVIEW_RISE = 46
        /** Folga lateral da bolha de preview além da largura da tecla (dp). */
        private const val PREVIEW_SIDE_PAD = 6
        /**
         * Distância (dp) que o dedo precisa percorrer para iniciar um swipe.
         * Maior que o roçar típico entre teclas vizinhas (~uma largura de tecla),
         * pra um toque com leve deslize NÃO ser confundido com gesto.
         */
        private const val GESTURE_START_THRESHOLD = 34
        /**
         * Mínimo de teclas distintas para um traço ser aceito como palavra de
         * gesto. Abaixo disso é "rolar o dedo" e o caractere comitado no toque
         * permanece — mata o falso-positivo que comia teclas.
         */
        private const val MIN_GESTURE_KEYS = 3
        /** Atraso (ms) até o toque-longo abrir o popup de acentos/números. */
        private const val LONG_PRESS_MS = 400L
        /** Atraso (ms) até o backspace começar a repetir quando segurado. */
        private const val BACKSPACE_REPEAT_DELAY_MS = 300L

        /**
         * Número acessível por toque-longo em cada tecla da fileira superior
         * (estilo Gboard). Combinado com os acentos no popup de long-press.
         */
        private val TOP_ROW_NUMERALS: Map<Char, Char> = mapOf(
            'q' to '1', 'w' to '2', 'e' to '3', 'r' to '4', 't' to '5',
            'y' to '6', 'u' to '7', 'i' to '8', 'o' to '9', 'p' to '0'
        )

        /**
         * Ações de IA da barra superior (rolável). As 3 primeiras têm destaque
         * de cor; as demais ficam em tom neutro. Todas chamam o mesmo motor
         * ([CorrectionStyle]) que já existia — só estavam subutilizadas.
         */
        private val AI_ACTIONS: List<AiChip> = listOf(
            AiChip(R.string.action_whatsapp, CorrectionStyle.WHATSAPP_CASUAL, ChipTone.WHATSAPP),
            AiChip(R.string.action_correct, CorrectionStyle.CORRECT, ChipTone.PRIMARY),
            AiChip(R.string.action_rewrite, CorrectionStyle.REWRITE, ChipTone.SECONDARY),
            AiChip(R.string.action_professional, CorrectionStyle.PROFESSIONAL, ChipTone.SECONDARY),
            AiChip(R.string.action_formal, CorrectionStyle.FORMAL, ChipTone.SECONDARY),
            AiChip(R.string.action_friendly, CorrectionStyle.FRIENDLY, ChipTone.SECONDARY),
            AiChip(R.string.action_shorten, CorrectionStyle.SHORTEN, ChipTone.SECONDARY),
            AiChip(R.string.action_elaborate, CorrectionStyle.ELABORATE, ChipTone.SECONDARY),
            AiChip(R.string.action_summarize, CorrectionStyle.SUMMARIZE, ChipTone.SECONDARY),
            AiChip(R.string.action_emojify, CorrectionStyle.EMOJIFY, ChipTone.SECONDARY),
            AiChip(R.string.action_translate, CorrectionStyle.TRANSLATE_EN, ChipTone.SECONDARY),
            AiChip(R.string.action_translate_pt, CorrectionStyle.TRANSLATE_PT_BR, ChipTone.SECONDARY)
        )
    }
}
