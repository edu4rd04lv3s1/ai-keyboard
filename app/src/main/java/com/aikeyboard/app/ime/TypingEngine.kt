package com.aikeyboard.app.ime

import com.aikeyboard.app.data.AutocorrectLevel
import com.aikeyboard.app.data.PersonalizationSnapshot

/**
 * Motor local de digitação PT-BR.
 *
 * Objetivos:
 *  - corrigir imediatamente apenas quando a intenção é praticamente inequívoca;
 *  - aplicar autocorreção mais forte ao fechar a palavra (espaço/pontuação);
 *  - manter sugestões rápidas e priorizadas por frequência.
 */
class TypingEngine(
    private val lexicon: PortugueseLexicon
) {

    /**
     * Toca em todas as estruturas pesadas do léxico para forçar a sua
     * construção. Chamado em background no `onCreate` do IME para evitar
     * que o primeiro keystroke pague o custo de inicialização.
     */
    fun warmup(): Int = lexicon.words.size

    /**
     * Correção ao fechar a palavra com espaço/pontuação.
     *
     * A agressividade é controlada pelo [level] escolhido nos ajustes:
     *  - OFF: nunca corrige automaticamente (nem acentos óbvios).
     *  - LIGHT: só correções inequívocas — mapa local de typos, restauração
     *    de acento sem ambiguidade e distance=1 quando o candidato é ISOLADO.
     *    Não desempata por frequência e não tenta distance=2. Quase nunca erra.
     *  - MEDIUM (padrão): equilíbrio — distance=1 com margem de frequência
     *    folgada e distance=2 só em palavras longas (6+).
     *  - AGGRESSIVE: margem baixa e distance=2 a partir de 4 letras.
     *
     * Em todos os níveis, o safety net é o auto-undo no IME: backspace logo
     * após uma correção a desfaz e registra o par como rejeitado.
     */
    fun boundaryCorrection(
        word: String,
        userDictionary: Set<String>,
        personalization: PersonalizationSnapshot = PersonalizationSnapshot(),
        level: AutocorrectLevel = AutocorrectLevel.MEDIUM
    ): String? {
        if (level == AutocorrectLevel.OFF) return null
        // Nunca mexe no que foi digitado de propósito (gírias, risadas, marcas,
        // alongamentos, tokens com dígito). É o que mantém a correção agressiva
        // sem "inventar" erros onde não há. Ver [TypingHeuristics].
        if (TypingHeuristics.isProtectedFromAutocorrect(word)) return null
        if (!isEligible(word, userDictionary)) return null
        LocalCorrections.lookup(word, userDictionary)?.let { return it }
        if (lexicon.contains(word.lowercase())) return null

        lexicon.unambiguousAccentMatch(word)?.let { return applyCase(word, it) }

        val normalized = PortugueseLexicon.normalize(word)
        if (normalized.length < 3) return null

        // ULTIMATE: corretor com modelo de teclado — custo ponderado pela
        // proximidade física das teclas, tolerando múltiplos erros por palavra.
        if (level == AutocorrectLevel.ULTIMATE) {
            return ultimateCorrection(word, normalized, personalization)
        }

        // Thresholds por nível. `frequencyMargin = MAX` desativa o desempate
        // (só corrige candidato isolado); `minLengthForDistance2 = MAX`
        // desativa a 2ª passada.
        val frequencyMargin: Int
        val minLengthForDistance2: Int
        when (level) {
            // OFF e ULTIMATE já retornaram acima; ficam aqui só para o when ser exaustivo.
            AutocorrectLevel.OFF, AutocorrectLevel.ULTIMATE -> return null
            AutocorrectLevel.LIGHT -> {
                frequencyMargin = Int.MAX_VALUE
                minLengthForDistance2 = Int.MAX_VALUE
            }
            AutocorrectLevel.MEDIUM -> {
                frequencyMargin = MEDIUM_FREQUENCY_MARGIN
                minLengthForDistance2 = MEDIUM_MIN_LENGTH_FOR_DISTANCE_2
            }
            AutocorrectLevel.AGGRESSIVE -> {
                frequencyMargin = AGGRESSIVE_FREQUENCY_MARGIN
                minLengthForDistance2 = AGGRESSIVE_MIN_LENGTH_FOR_DISTANCE_2
            }
        }

        // 1ª passada — distance=1 (rápido e cobre ~85% dos typos reais).
        val near = lexicon.fuzzyCandidates(
            word = word,
            maxDistance = 1,
            limit = 3,
            sameInitialOnly = true
        )
        near.firstOrNull()?.let { best ->
            val second = near.getOrNull(1)
            if (isHighConfidenceAutoCorrection(best, second, frequencyMargin) &&
                !personalization.isRejected(word, best.word)
            ) {
                return applyCase(word, best.word)
            }
        }

        // 2ª passada — distance=2 (só a partir de minLengthForDistance2). O
        // guard aceita o melhor candidato quando ele está isolado OU quando é
        // claramente mais frequente que o concorrente de mesma distância.
        if (normalized.length >= minLengthForDistance2) {
            val wider = lexicon.fuzzyCandidates(
                word = word,
                maxDistance = 2,
                limit = 2,
                sameInitialOnly = true
            )
            val best = wider.firstOrNull() ?: return null
            val second = wider.getOrNull(1)
            val bestIsClear = second == null ||
                second.distance > best.distance ||
                (second.rank - best.rank) >= frequencyMargin
            if (best.distance == 2 &&
                bestIsClear &&
                !personalization.isRejected(word, best.word)
            ) {
                return applyCase(word, best.word)
            }
        }

        return null
    }

    /**
     * Correção ULTIMATE: escolhe o melhor candidato pela distância ponderada por
     * teclado e só aplica se for claramente o vencedor. As salvaguardas do
     * chamador continuam valendo (palavra já no léxico/dicionário não chega aqui;
     * backspace logo após desfaz e registra rejeição).
     */
    private fun ultimateCorrection(
        word: String,
        normalized: String,
        personalization: PersonalizationSnapshot
    ): String? {
        // Teto de custo por comprimento: palavras curtas ficam restritas (evita
        // trocar por outra palavra parecida); longas toleram mais (casar uma
        // palavra longa inteira por acaso é improvável), cobrindo erro na 1ª
        // letra + erro interno (ex.: wuslidade->qualidade, grdbalhar->trabalhar).
        val maxCost = when {
            normalized.length <= 4 -> ULTIMATE_MAX_COST_SHORT
            normalized.length >= 7 -> ULTIMATE_MAX_COST_LONG
            else -> ULTIMATE_MAX_COST
        }
        val candidates = lexicon.weightedCandidates(word, maxCost, limit = 4)
        val best = candidates.firstOrNull() ?: return null
        if (best.distance <= 0) return null
        if (personalization.isRejected(word, best.word)) return null

        val second = candidates.getOrNull(1)
        val clearWinner = when {
            second == null -> true                                   // candidato único
            best.distance <= PortugueseLexicon.W_SUB_ADJ -> true     // custo mínimo (1 tecla vizinha)
            (second.distance - best.distance) >= ULTIMATE_MARGIN -> true  // claramente mais barato
            // Empate de custo: aceita o MUITO mais frequente (csmos -> vamos, não vemos).
            second.distance == best.distance &&
                (second.rank - best.rank) >= ULTIMATE_FREQ_MARGIN -> true
            else -> false
        }
        return if (clearWinner) applyCase(word, best.word) else null
    }

    /**
     * Correção de um "token" de fronteira que pode conter pontuação ACIDENTAL no
     * meio (ex.: "r.esolver", "tra,balho"). Remove só a pontuação espúria
     * (preserva hífen e apóstrofo), e então valida/corrige a forma limpa. Para
     * tokens sem ruído, é igual a [boundaryCorrection].
     */
    fun correctBoundaryToken(
        token: String,
        userDictionary: Set<String>,
        personalization: PersonalizationSnapshot = PersonalizationSnapshot(),
        level: AutocorrectLevel = AutocorrectLevel.MEDIUM
    ): String? {
        val hasStray = token.any { it in STRAY_PUNCTUATION }
        if (!hasStray) return boundaryCorrection(token, userDictionary, personalization, level)
        // Só trata pontuação acidental cercada por letras; não mexe em tokens com
        // dígitos/símbolos ("10kg", "v2.0", "1ano").
        val onlyLettersAndStray = token.all {
            it.isLetter() || it == '-' || it == '\'' || it in STRAY_PUNCTUATION
        }
        if (!onlyLettersAndStray) return null

        val cleaned = buildString {
            for (c in token) if (c !in STRAY_PUNCTUATION) append(c)
        }
        if (cleaned.length < 2 || cleaned == token) return null
        // Tenta corrigir a forma limpa; se ela já for válida, basta devolvê-la
        // (remove só o ruído, sem mexer nas letras).
        boundaryCorrection(cleaned, userDictionary, personalization, level)?.let { return it }
        if (lexicon.contains(cleaned.lowercase())) return cleaned
        return null
    }

    fun suggestions(
        prefix: String,
        userDictionary: Set<String>,
        personalization: PersonalizationSnapshot = PersonalizationSnapshot()
    ): List<String> {
        if (prefix.length < 2) return emptyList()
        val userDictionaryNormalized = userDictionary.mapTo(mutableSetOf(), PortugueseLexicon::normalize)
        val normalizedPrefix = PortugueseLexicon.normalize(prefix)

        val out = linkedSetOf<String>()

        userDictionary.asSequence()
            .filter { PortugueseLexicon.normalize(it).startsWith(normalizedPrefix) }
            .filterNot { PortugueseLexicon.normalize(it) == normalizedPrefix }
            .sortedBy { it.length }
            .take(2)
            .forEach(out::add)

        personalization.frequencies.entries.asSequence()
            .filter { (word, count) ->
                count >= MIN_PERSONAL_FREQUENCY &&
                    PortugueseLexicon.normalize(word).startsWith(normalizedPrefix) &&
                    PortugueseLexicon.normalize(word) != normalizedPrefix
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.length }
            )
            .map { it.key }
            .take(3)
            .forEach(out::add)

        lexicon.completions(normalizedPrefix, limit = 12)
            .asSequence()
            .filterNot { PortugueseLexicon.normalize(it) in userDictionaryNormalized }
            .filterNot { PortugueseLexicon.normalize(it) == normalizedPrefix }
            .sortedWith(
                compareByDescending<String> { personalization.frequencyOf(it) }
                    .thenBy { lexicon.rankOf(it) }
            )
            .forEach {
                if (out.size < MAX_SUGGESTIONS) out += it
            }

        return out.take(MAX_SUGGESTIONS)
    }

    fun suggestionCandidates(
        prefix: String,
        userDictionary: Set<String>,
        personalization: PersonalizationSnapshot = PersonalizationSnapshot(),
        // Correção já calculada pelo chamador (o IME pré-computa em background
        // e reaproveita aqui), evitando rodar `boundaryCorrection` duas vezes
        // por keystroke. Se não for passada, calcula sob demanda.
        correction: String? = boundaryCorrection(prefix, userDictionary, personalization)
    ): List<SuggestionCandidate> {
        if (prefix.length < 2) return emptyList()

        val out = mutableListOf<SuggestionCandidate>()
        val seenWords = mutableSetOf<String>()
        val hasCorrection = correction != null && !correction.equals(prefix, ignoreCase = false)
        val normalizedOriginal = PortugueseLexicon.normalize(prefix)
        val normalizedCorrection = correction?.let(PortugueseLexicon::normalize)

        fun add(candidate: SuggestionCandidate) {
            val key = when (candidate.type) {
                SuggestionType.ORIGINAL -> "original:${candidate.value}"
                SuggestionType.CORRECTION -> "correction:${PortugueseLexicon.normalize(candidate.value)}"
                SuggestionType.EMOJI -> "emoji:${candidate.value}"
                SuggestionType.QUICK_PHRASE -> "quick:${candidate.value.lowercase()}"
                SuggestionType.CLIPBOARD -> "clipboard:${candidate.value.lowercase()}"
                SuggestionType.WORD -> "word:${PortugueseLexicon.normalize(candidate.value)}"
            }
            if (seenWords.add(key)) out += candidate
        }

        if (hasCorrection) {
            add(SuggestionCandidate(prefix, SuggestionType.ORIGINAL))
            add(SuggestionCandidate(correction!!, SuggestionType.CORRECTION))
        }

        emojiSuggestions(prefix)
            .forEach { add(SuggestionCandidate(it, SuggestionType.EMOJI)) }

        suggestions(prefix, userDictionary, personalization)
            .forEach { suggestion ->
                val normalizedSuggestion = PortugueseLexicon.normalize(suggestion)
                val duplicatesOriginalOrCorrection = hasCorrection &&
                    (normalizedSuggestion == normalizedOriginal ||
                        normalizedSuggestion == normalizedCorrection)
                if (!duplicatesOriginalOrCorrection) {
                    add(SuggestionCandidate(suggestion, SuggestionType.WORD))
                }
            }

        return out.take(MAX_SUGGESTIONS)
    }

    fun nextWordCandidates(
        previousWord: String?,
        userDictionary: Set<String>,
        personalization: PersonalizationSnapshot = PersonalizationSnapshot()
    ): List<SuggestionCandidate> {
        val out = linkedSetOf<String>()
        val normalizedPrevious = previousWord
            ?.takeIf { it.isNotBlank() }
            ?.let(PortugueseLexicon::normalize)

        // 1) Bigramas APRENDIDOS do usuário têm prioridade (o que ELE costuma
        //    escrever depois desta palavra).
        if (!previousWord.isNullOrBlank()) {
            personalization.topNextWords(previousWord, MAX_SUGGESTIONS).forEach(out::add)
        }
        // 2) Bigramas comuns do PT-BR.
        COMMON_NEXT_WORDS[normalizedPrevious].orEmpty().forEach(out::add)

        personalization.frequencies.entries.asSequence()
            .filter { (_, count) -> count >= MIN_PERSONAL_FREQUENCY }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.length }
            )
            .map { it.key }
            .filter { PortugueseLexicon.normalize(it) != normalizedPrevious }
            .take(4)
            .forEach(out::add)

        userDictionary.asSequence()
            .filter { it.length >= 3 }
            .sortedBy { it.length }
            .take(3)
            .forEach(out::add)

        COMMON_STARTERS.forEach(out::add)

        return out.take(MAX_SUGGESTIONS).map { SuggestionCandidate(it, SuggestionType.WORD) }
    }

    /**
     * Decodifica um gesto de digitação (swipe) na(s) palavra(s) mais provável(is).
     *
     * [path] é a sequência de letras das teclas que o dedo percorreu, na ordem.
     * Heurística rápida e 100% on-device:
     *  - âncoras fortes: a 1ª e a última letra do traço quase nunca erram;
     *  - a palavra precisa ser **subsequência** do caminho (o dedo passou por
     *    todas as letras dela, em ordem);
     *  - ranqueia por uso pessoal, depois frequência no léxico e comprimento.
     *
     * Retorna candidatos ordenados (melhor primeiro). O chamador comita o
     * primeiro e oferece os demais como sugestões tocáveis.
     */
    fun decodeGesture(
        path: List<Char>,
        userDictionary: Set<String> = emptySet(),
        personalization: PersonalizationSnapshot = PersonalizationSnapshot()
    ): List<String> {
        if (path.size < 2) return emptyList()

        // Normaliza cada tecla (sem acento, minúscula) e colapsa repetições
        // consecutivas — o dedo costuma "tremer" sobre a mesma tecla.
        val collapsed = StringBuilder(path.size)
        for (raw in path) {
            val c = PortugueseLexicon.normalize(raw.toString()).firstOrNull() ?: continue
            if (collapsed.isEmpty() || collapsed[collapsed.length - 1] != c) collapsed.append(c)
        }
        if (collapsed.length < 2) return emptyList()
        val pathStr = collapsed.toString()
        val first = pathStr.first()
        val last = pathStr.last()

        val matches = ArrayList<ScoredWord>()
        for (word in lexicon.wordsStartingWith(first)) {
            if (word in userDictionary) continue
            val norm = lexicon.normalizedForm(word)
            if (norm.length < 2 || norm[norm.length - 1] != last) continue
            if (!isSubsequence(norm, pathStr)) continue
            matches += ScoredWord(word, gestureScore(word, norm, personalization))
        }

        // Palavras pessoais que também casam o traço entram com bônus.
        userDictionary.asSequence()
            .filter { it.length >= 2 }
            .forEach { word ->
                val norm = PortugueseLexicon.normalize(word)
                if (norm.isNotEmpty() && norm.first() == first && norm.last() == last &&
                    isSubsequence(norm, pathStr)
                ) {
                    matches += ScoredWord(word, gestureScore(word, norm, personalization) + USER_WORD_BONUS)
                }
            }

        if (matches.isEmpty()) return emptyList()
        return matches
            .sortedByDescending { it.score }
            .distinctBy { it.word.lowercase() }
            .take(MAX_GESTURE_CANDIDATES)
            .map { it.word }
    }

    private fun gestureScore(
        word: String,
        normalized: String,
        personalization: PersonalizationSnapshot
    ): Double {
        val personal = personalization.frequencyOf(word)
        val rank = lexicon.rankOf(word) // menor = mais comum
        // Comprimento: casar uma palavra longa inteira por acaso é improvável,
        // então recompensa. Uso pessoal pesa forte. Frequência desempata.
        return normalized.length * 2.0 +
            personal * 5.0 +
            (LEXICON_RANK_CEILING - rank).coerceAtLeast(0) * 0.0015
    }

    private fun isSubsequence(word: String, path: String): Boolean {
        if (word.isEmpty()) return true
        var i = 0
        for (c in path) {
            if (word[i] == c) {
                i++
                if (i == word.length) return true
            }
        }
        return false
    }

    private data class ScoredWord(val word: String, val score: Double)

    private fun emojiSuggestions(prefix: String): List<String> {
        val normalized = PortugueseLexicon.normalize(prefix)
        if (normalized.length < MIN_EMOJI_EXACT_LENGTH) return emptyList()
        return EMOJI_KEYWORDS
            .asSequence()
            .filter { (keyword, _) ->
                keyword == normalized ||
                    (
                        keyword.startsWith(normalized) &&
                            normalized.length >= MIN_EMOJI_COMPLETION_PREFIX_LENGTH
                        )
            }
            .flatMap { (_, emojis) -> emojis.asSequence() }
            .distinct()
            .take(MAX_EMOJI_SUGGESTIONS)
            .toList()
    }

    private fun isEligible(word: String, userDictionary: Set<String>): Boolean {
        if (word.isBlank()) return false
        val lower = word.lowercase()
        if (lower in userDictionary) return false
        // Comparação normalizada sem alocar um Set novo a cada chamada — este
        // método roda no caminho de cada espaço/keystroke.
        if (userDictionary.isNotEmpty()) {
            val normalizedLower = PortugueseLexicon.normalize(lower)
            if (userDictionary.any { PortugueseLexicon.normalize(it) == normalizedLower }) {
                return false
            }
        }
        return true
    }

    private fun isHighConfidenceAutoCorrection(
        best: PortugueseLexicon.Candidate,
        second: PortugueseLexicon.Candidate?,
        frequencyMargin: Int
    ): Boolean {
        if (best.distance == 0) return false
        if (best.distance == 1) {
            if (second == null) return true
            if (second.distance > 1) return true
            // Empate de distância: só corrige se o melhor for claramente mais
            // frequente que o concorrente. A margem vem do nível escolhido
            // (MAX = nunca desempata, usado no nível Leve).
            return second.rank - best.rank >= frequencyMargin
        }
        return false
    }

    private fun applyCase(original: String, replacement: String): String {
        val allUpper = original.any { it.isLetter() } &&
            original.all { !it.isLetter() || it.isUpperCase() }
        if (allUpper) return replacement.uppercase()
        if (original.firstOrNull()?.isUpperCase() == true) {
            return replacement.replaceFirstChar { it.uppercase() }
        }
        return replacement
    }

    companion object {
        private const val MAX_SUGGESTIONS = 3
        private const val MIN_PERSONAL_FREQUENCY = 2
        /** Pontuação de sentença tratada como "ruído" quando cai no meio da palavra. */
        private const val STRAY_PUNCTUATION = ".,;:!?"

        // Decodificação de gesto (swipe).
        private const val MAX_GESTURE_CANDIDATES = 4
        private const val USER_WORD_BONUS = 50.0
        /** Aproximação do tamanho do léxico, para converter rank em pontuação. */
        private const val LEXICON_RANK_CEILING = 12000

        // Thresholds por nível de autocorreção (ver boundaryCorrection).
        // Margem = diferença mínima de rank (frequência) pra desempatar dois
        // candidatos de mesma distância. Menor = mais agressivo.
        private const val MEDIUM_FREQUENCY_MARGIN = 60
        private const val MEDIUM_MIN_LENGTH_FOR_DISTANCE_2 = 6
        private const val AGGRESSIVE_FREQUENCY_MARGIN = 25
        private const val AGGRESSIVE_MIN_LENGTH_FOR_DISTANCE_2 = 4

        // ULTIMATE — limites do custo ponderado (×10; ver PortugueseLexicon.W_*).
        /** Teto de custo para palavras de 5-6 letras. */
        private const val ULTIMATE_MAX_COST = 34
        /** Teto para palavras longas (7+): folga p/ erro na 1ª letra + erro interno. */
        private const val ULTIMATE_MAX_COST_LONG = 40
        /** Teto bem menor para palavras curtas (≤4): evita trocar por outra palavra. */
        private const val ULTIMATE_MAX_COST_SHORT = 18
        /** Vantagem mínima do melhor candidato sobre o 2º para aplicar a correção. */
        private const val ULTIMATE_MARGIN = 7
        /** Em empate de custo, diferença de rank (frequência) p/ aceitar o mais comum. */
        private const val ULTIMATE_FREQ_MARGIN = 300
        private const val MIN_EMOJI_EXACT_LENGTH = 2
        private const val MIN_EMOJI_COMPLETION_PREFIX_LENGTH = 4
        private const val MAX_EMOJI_SUGGESTIONS = 2

        private val COMMON_STARTERS = listOf(
            "eu",
            "você",
            "não",
            "obrigado",
            "por favor"
        )

        private val COMMON_NEXT_WORDS = linkedMapOf(
            "bom" to listOf("dia", "trabalho", "fim"),
            "boa" to listOf("tarde", "noite", "semana"),
            "tudo" to listOf("bem", "certo", "ok"),
            "por" to listOf("favor", "isso", "enquanto"),
            "muito" to listOf("obrigado", "bom", "bem"),
            "obrigado" to listOf("pela", "por", "mesmo"),
            "obrigada" to listOf("pela", "por", "mesmo"),
            "vou" to listOf("ver", "chegar", "mandar"),
            "posso" to listOf("te", "ver", "ajudar"),
            "preciso" to listOf("de", "ver", "falar"),
            "nao" to listOf("sei", "consigo", "quero"),
            "não" to listOf("sei", "consigo", "quero"),
            "voce" to listOf("pode", "tem", "consegue"),
            "você" to listOf("pode", "tem", "consegue"),
            "eu" to listOf("vou", "acho", "preciso"),
            "a" to listOf("gente", "partir", "respeito"),
            "o" to listOf("que", "dia", "projeto"),
            "que" to listOf("você", "eu", "a"),
            "de" to listOf("você", "novo", "hoje"),
            "para" to listOf("você", "mim", "hoje"),
            "com" to listOf("você", "isso", "a"),
            "em" to listOf("breve", "casa", "contato")
        )

        private val EMOJI_KEYWORDS = linkedMapOf(
            "parabens" to listOf("🎉"),
            "aniversario" to listOf("🎂", "🎉"),
            "obrigado" to listOf("🙏"),
            "obrigada" to listOf("🙏"),
            "valeu" to listOf("🙏"),
            "feliz" to listOf("😊"),
            "triste" to listOf("😢"),
            "amor" to listOf("❤️"),
            "beijo" to listOf("😘"),
            "risada" to listOf("😂"),
            "kkk" to listOf("😂"),
            "ok" to listOf("👍"),
            "beleza" to listOf("👍"),
            "perfeito" to listOf("👌"),
            "urgente" to listOf("⚠️")
        )
    }
}
