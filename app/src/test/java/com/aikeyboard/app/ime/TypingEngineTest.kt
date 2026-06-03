package com.aikeyboard.app.ime

import com.aikeyboard.app.data.AutocorrectLevel
import com.aikeyboard.app.data.PersonalizationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypingEngineTest {

    private val lexicon = PortugueseLexicon(
        listOf(
            "de",
            "e",
            "é",
            "não",
            "você",
            "vc",
            "vice",
            "minha",
            "meu",
            "eu",
            "ela",
            "quando",
            "casa",
            "caso",
            "caminhão",
            "profissional",
            "projeto",
            "produto"
        )
    )
    private val engine = TypingEngine(lexicon)

    @Test
    fun boundaryCorrectionFixesClearTypo() {
        assertEquals("quando", engine.boundaryCorrection("qaundo", emptySet()))
    }

    @Test
    fun boundaryCorrectionRestoresUnambiguousAccent() {
        assertEquals("você", engine.boundaryCorrection("voce", emptySet()))
    }

    @Test
    fun userDictionaryBlocksCorrection() {
        assertNull(engine.boundaryCorrection("qaundo", setOf("qaundo")))
    }

    @Test
    fun rejectedCorrectionIsNotRepeated() {
        val personalization = PersonalizationSnapshot(
            rejectedCorrections = setOf("caza>casa")
        )
        assertNull(engine.boundaryCorrection("caza", emptySet(), personalization))
    }

    @Test
    fun personalizedWordWinsSuggestionRanking() {
        val personalization = PersonalizationSnapshot(
            frequencies = mapOf("produto" to 1, "projeto" to 8)
        )
        assertEquals(
            listOf("projeto", "produto", "profissional"),
            engine.suggestions("pro", emptySet(), personalization)
        )
    }

    @Test
    fun richSuggestionsPutCorrectionFirst() {
        val suggestions = engine.suggestionCandidates("voce", emptySet())

        assertEquals(SuggestionCandidate("voce", SuggestionType.ORIGINAL), suggestions[0])
        assertEquals(SuggestionCandidate("você", SuggestionType.CORRECTION), suggestions[1])
    }

    @Test
    fun richSuggestionsIncludeContextualEmoji() {
        val suggestions = engine.suggestionCandidates("feliz", emptySet())

        assertEquals(SuggestionCandidate("😊", SuggestionType.EMOJI), suggestions.first())
    }

    @Test
    fun correctionSuggestionsKeepOriginalAndFilterDuplicateThirdCandidate() {
        val suggestions = engine.suggestionCandidates(
            prefix = "pro",
            userDictionary = emptySet(),
            correction = "profissional"
        )

        assertEquals(
            listOf(
                SuggestionCandidate("pro", SuggestionType.ORIGINAL),
                SuggestionCandidate("profissional", SuggestionType.CORRECTION),
                SuggestionCandidate("projeto", SuggestionType.WORD)
            ),
            suggestions
        )
    }

    @Test
    fun nextWordCandidatesUseCommonContext() {
        val suggestions = engine.nextWordCandidates("bom", emptySet())

        assertEquals(SuggestionCandidate("dia", SuggestionType.WORD), suggestions.first())
    }

    @Test
    fun nextWordCandidatesIncludePersonalizedWords() {
        val personalization = PersonalizationSnapshot(
            frequencies = mapOf("projeto" to 5)
        )
        val suggestions = engine.nextWordCandidates("sobre", emptySet(), personalization)

        assertEquals(
            listOf(
                SuggestionCandidate("projeto", SuggestionType.WORD),
                SuggestionCandidate("eu", SuggestionType.WORD),
                SuggestionCandidate("você", SuggestionType.WORD)
            ),
            suggestions
        )
    }

    @Test
    fun aggressiveTieBreakCorrectsByFrequencyMargin() {
        // "mato" (rank 0) e "mito" (rank 31) ficam ambos a distância 1 de
        // "moto". A margem de frequência é 31: no nível AGGRESSIVE (margem 25)
        // corrige para o mais frequente.
        val lex = PortugueseLexicon(
            listOf("mato") + List(30) { "fil$it" } + listOf("mito")
        )
        val eng = TypingEngine(lex)
        assertEquals("mato", eng.boundaryCorrection("moto", emptySet(), level = AutocorrectLevel.AGGRESSIVE))
    }

    @Test
    fun aggressiveDistanceTwoCorrectsShortWordWhenIsolated() {
        // "moao" está a distância 2 de "mato" e é o único candidato próximo.
        // No nível AGGRESSIVE (mínimo 4 letras p/ distance=2) é corrigido.
        val lex = PortugueseLexicon(listOf("mato", "casa", "nome"))
        val eng = TypingEngine(lex)
        assertEquals("mato", eng.boundaryCorrection("moao", emptySet(), level = AutocorrectLevel.AGGRESSIVE))
    }

    @Test
    fun protectedSlangIsNeverAutocorrected() {
        // Léxico que TENTARIA corrigir: "pra"->"para" (distância 1), "mto"->"muito".
        val lex = PortugueseLexicon(listOf("para", "muito", "você", "quando", "casa"))
        val eng = TypingEngine(lex)
        // Gírias/risadas digitadas de propósito ficam intocadas em qualquer nível.
        assertNull(eng.boundaryCorrection("pra", emptySet(), level = AutocorrectLevel.ULTIMATE))
        assertNull(eng.boundaryCorrection("mto", emptySet(), level = AutocorrectLevel.AGGRESSIVE))
        assertNull(eng.boundaryCorrection("kkk", emptySet(), level = AutocorrectLevel.ULTIMATE))
        assertNull(eng.boundaryCorrection("vc", emptySet(), level = AutocorrectLevel.ULTIMATE))
        // ...mas um typo de verdade continua sendo corrigido normalmente.
        assertEquals("quando", eng.boundaryCorrection("qaundo", emptySet(), level = AutocorrectLevel.ULTIMATE))
    }

    @Test
    fun offLevelNeverAutocorrects() {
        // OFF não troca nada — nem typo óbvio do mapa local, nem acento.
        assertNull(engine.boundaryCorrection("qaundo", emptySet(), level = AutocorrectLevel.OFF))
        assertNull(engine.boundaryCorrection("voce", emptySet(), level = AutocorrectLevel.OFF))
    }

    @Test
    fun contextualCorrectionFixesViceAtSentenceStart() {
        assertEquals("Você", applyContextual("Vice"))
        assertEquals("você", applyContextual("vice"))
        assertEquals("Oi. Você", applyContextual("Oi. Vice"))
    }

    @Test
    fun contextualCorrectionDoesNotChangeNominalVice() {
        assertNull(engine.contextualBoundaryCorrection("o vice", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("a vice", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("do vice", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("da vice", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("ao vice", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("à vice", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("vice-presidente", emptySet()).candidate)
    }

    @Test
    fun contextualCorrectionRetrofitsSerAttribution() {
        val decision = engine.contextualBoundaryCorrection("Você e minha", emptySet())
        val edit = requireNotNull(decision.candidate)

        assertEquals("Você é minha", applyContextualEditToText("Você e minha", edit))
        assertEquals("e", edit.originalRejected)
        assertEquals("é", edit.correctedRejected)
    }

    @Test
    fun contextualCorrectionSupportsVcAndMeuOnlyInHighConfidencePatterns() {
        assertEquals("vc é minha", applyContextual("vc e minha"))
        assertEquals("Você é meu", applyContextual("Você e meu"))
        assertEquals("vc é meu", applyContextual("vc e meu"))
    }

    @Test
    fun contextualCorrectionDoesNotChangeRealConjunctions() {
        assertNull(engine.contextualBoundaryCorrection("você e eu", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("você e ela", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("você e ele", emptySet()).candidate)
        assertNull(engine.contextualBoundaryCorrection("você e você", emptySet()).candidate)
    }

    @Test
    fun contextualCorrectionConvergesProgressivelyForWhatsappPhrase() {
        val typed = "Vice e minha neguinha linda e maravilhosa"

        assertEquals(
            "Você é minha neguinha linda e maravilhosa",
            simulateProgressiveAutocorrect(typed)
        )
    }

    @Test
    fun lightLevelFixesUnambiguousButNotFrequencyTies() {
        // LIGHT corrige o inequívoco (acento via mapa local)...
        assertEquals("você", engine.boundaryCorrection("voce", emptySet(), level = AutocorrectLevel.LIGHT))
        // ...mas não desempata por frequência: "moto" tem dois candidatos a
        // distância 1 (mato/mito), então fica sem corrigir.
        val lex = PortugueseLexicon(
            listOf("mato") + List(30) { "fil$it" } + listOf("mito")
        )
        val eng = TypingEngine(lex)
        assertNull(eng.boundaryCorrection("moto", emptySet(), level = AutocorrectLevel.LIGHT))
    }

    @Test
    fun mediumLevelSkipsDistanceTwoOnShortWords() {
        val lex = PortugueseLexicon(listOf("mato", "casa", "nome"))
        val eng = TypingEngine(lex)
        // MEDIUM não tenta distance=2 em palavra de 4 letras (mínimo 6).
        assertNull(eng.boundaryCorrection("moao", emptySet(), level = AutocorrectLevel.MEDIUM))
        // AGGRESSIVE (mínimo 4) corrige o mesmo caso.
        assertEquals("mato", eng.boundaryCorrection("moao", emptySet(), level = AutocorrectLevel.AGGRESSIVE))
    }

    @Test
    fun gestureDecodesWordFromPath() {
        // Traço c → a → s → a decodifica "casa".
        assertEquals("casa", engine.decodeGesture(listOf('c', 'a', 's', 'a')).firstOrNull())
    }

    @Test
    fun gestureLastLetterAnchorDistinguishesWords() {
        // Mesmo começo, final diferente: o último ponto do traço decide.
        assertEquals("casa", engine.decodeGesture(listOf('c', 'a', 's', 'a')).firstOrNull())
        assertEquals("caso", engine.decodeGesture(listOf('c', 'a', 's', 'o')).firstOrNull())
    }

    @Test
    fun gestureToleratesPassThroughKeys() {
        // Teclas de passagem ('w') entre as letras reais não impedem o casamento.
        assertEquals("quando", engine.decodeGesture(listOf('q', 'w', 'u', 'a', 'n', 'd', 'o')).firstOrNull())
    }

    @Test
    fun gestureCollapsesRepeatedKeys() {
        // O dedo "tremendo" sobre as mesmas teclas (repetições consecutivas)
        // não atrapalha: c,c,a,a,s,s,a → "casa".
        assertEquals("casa", engine.decodeGesture(listOf('c', 'c', 'a', 'a', 's', 's', 'a')).firstOrNull())
    }

    @Test
    fun gestureIgnoresTooShortPaths() {
        assertEquals(emptyList<String>(), engine.decodeGesture(listOf('a')))
    }

    @Test
    fun gestureReturnsEmptyWhenNothingMatches() {
        // 'z' não inicia nenhuma palavra do léxico de teste.
        assertEquals(emptyList<String>(), engine.decodeGesture(listOf('z', 'x', 'k')))
    }

    // ---------- Autocorreção ULTIMATE (modelo de teclado) ----------

    private val ultimateLex = PortugueseLexicon(
        listOf(
            "amor", "teclado", "celular", "computador", "obrigado",
            "mensagem", "maravilhosa", "casa", "caso", "trabalho", "amora"
        )
    )
    private val ultimateEngine = TypingEngine(ultimateLex)

    private fun ultimate(typo: String, dict: Set<String> = emptySet()) =
        ultimateEngine.boundaryCorrection(typo, dict, level = AutocorrectLevel.ULTIMATE)

    private fun applyContextual(text: String): String? =
        engine.contextualBoundaryCorrection(text, emptySet())
            .candidate
            ?.let { applyContextualEditToText(text, it) }

    private fun simulateProgressiveAutocorrect(text: String): String {
        var current = ""
        text.trim().split(Regex("\\s+")).forEach { word ->
            current = if (current.isEmpty()) word else "$current $word"
            current = closeCurrentWord(current)
        }
        return current
    }

    private fun closeCurrentWord(text: String): String {
        engine.contextualBoundaryCorrection(text, emptySet())
            .candidate
            ?.let { return applyContextualEditToText(text, it) }

        val bounds = findLastWordBounds(text) ?: return text
        val original = text.substring(bounds)
        val corrected = engine.boundaryCorrection(original, emptySet()) ?: return text
        return text.substring(0, bounds.first) + corrected + text.substring(bounds.last + 1)
    }

    @Test
    fun ultimateFixesSingleAdjacentKeyTypo() {
        // 'p' e 'o' são teclas vizinhas: ampr -> amor.
        assertEquals("amor", ultimate("ampr"))
    }

    @Test
    fun ultimateFixesMultipleAdjacentKeyTypos() {
        // r->e e p->o, ambas trocas de teclas vizinhas: trcladp -> teclado.
        assertEquals("teclado", ultimate("trcladp"))
    }

    @Test
    fun ultimateFixesOmissionAndAdjacentTypos() {
        assertEquals("celular", ultimate("celulr"))      // letra faltando
        assertEquals("computador", ultimate("comptador")) // letra faltando
        assertEquals("obrigado", ultimate("obrgado"))     // letra faltando
        assertEquals("mensagem", ultimate("mensgem"))     // letra faltando
    }

    @Test
    fun ultimateNeverChangesValidWord() {
        // Palavra que já existe no léxico nunca é "corrigida".
        assertNull(ultimate("casa"))
        assertNull(ultimate("amor"))
    }

    @Test
    fun ultimateRespectsUserDictionary() {
        assertNull(ultimate("ampr", dict = setOf("ampr")))
    }

    @Test
    fun ultimateRespectsRejectedUndo() {
        val pers = PersonalizationSnapshot(rejectedCorrections = setOf("ampr>amor"))
        assertNull(ultimateEngine.boundaryCorrection("ampr", emptySet(), pers, AutocorrectLevel.ULTIMATE))
    }

    @Test
    fun ultimateDoesNotInventForFarGibberish() {
        // Sequência sem palavra próxima: não corrige (não inventa).
        assertNull(ultimate("xqwz"))
    }

    // ---------- MAX: erro na 1ª letra, desempate, pontuação, bigramas ----------

    @Test
    fun ultimateFixesFirstLetterPlusInternalError() {
        // w->q e g->t são teclas vizinhas: erro na 1ª letra + erro interno.
        val lex = PortugueseLexicon(listOf("qualidade", "qualquer", "trabalhar", "trabalho"))
        val eng = TypingEngine(lex)
        assertEquals("qualidade", eng.boundaryCorrection("wuslidade", emptySet(), level = AutocorrectLevel.ULTIMATE))
        assertEquals("trabalhar", eng.boundaryCorrection("grdbalhar", emptySet(), level = AutocorrectLevel.ULTIMATE))
    }

    @Test
    fun ultimateBreaksCostTieByFrequency() {
        // "csmos": c->v vizinha; "vamos" e "vemos" empatam em custo. O mais
        // frequente (vamos, rank menor) vence o desempate.
        val lex = PortugueseLexicon(listOf("vamos") + List(400) { "zz$it" } + listOf("vemos"))
        val eng = TypingEngine(lex)
        assertEquals("vamos", eng.boundaryCorrection("csmos", emptySet(), level = AutocorrectLevel.ULTIMATE))
    }

    @Test
    fun strayPunctuationInsideWordIsCleaned() {
        val lex = PortugueseLexicon(listOf("resolver", "trabalho", "casa"))
        val eng = TypingEngine(lex)
        assertEquals("resolver", eng.correctBoundaryToken("r.esolver", emptySet(), level = AutocorrectLevel.ULTIMATE))
    }

    @Test
    fun tokenWithDigitsOrSymbolsIsNotTouched() {
        val lex = PortugueseLexicon(listOf("resolver", "casa"))
        val eng = TypingEngine(lex)
        assertNull(eng.correctBoundaryToken("10kg", emptySet(), level = AutocorrectLevel.ULTIMATE))
        assertNull(eng.correctBoundaryToken("v2.0", emptySet(), level = AutocorrectLevel.ULTIMATE))
    }

    @Test
    fun learnedBigramPredictsNextWord() {
        val snap = PersonalizationSnapshot(bigrams = mapOf("bom" to mapOf("dia" to 5, "festa" to 2)))
        val result = engine.nextWordCandidates("bom", emptySet(), snap)
        assertEquals("dia", result.first().value)
    }

    @Test
    fun snapshotTopNextWordsRanksByCount() {
        val snap = PersonalizationSnapshot(bigrams = mapOf("vou" to mapOf("chegar" to 1, "mandar" to 9)))
        assertEquals(listOf("mandar", "chegar"), snap.topNextWords("vou", 5))
    }
}
