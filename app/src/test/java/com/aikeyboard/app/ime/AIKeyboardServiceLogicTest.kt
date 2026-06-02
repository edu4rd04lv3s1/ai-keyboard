package com.aikeyboard.app.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AIKeyboardServiceLogicTest {

    @Test
    fun findLastWordBounds_ignoresTrailingSpaces() {
        val input = "oi voce   "
        val bounds = findLastWordBounds(input)

        requireNotNull(bounds)
        assertEquals("voce", input.substring(bounds))
    }

    @Test
    fun findLastWordBounds_returnsNullWhenCursorAfterPunctuation() {
        assertNull(findLastWordBounds("oi,"))
        assertNull(findLastWordBounds("..."))
    }

    @Test
    fun findLastWordBounds_keepsApostropheAndHyphen() {
        val input = "d'agua bem-vindo"
        val bounds = findLastWordBounds(input)

        requireNotNull(bounds)
        assertEquals("bem-vindo", input.substring(bounds))
    }

    @Test
    fun shouldAutoCapitalize_trueAtSentenceStart() {
        assertTrue(shouldAutoCapitalize(""))
        assertTrue(shouldAutoCapitalize("   "))
        assertTrue(shouldAutoCapitalize("Oi. "))
        assertTrue(shouldAutoCapitalize("Tudo bem?   "))
    }

    @Test
    fun shouldAutoCapitalize_falseAfterRegularWord() {
        assertFalse(shouldAutoCapitalize("tudo bem "))
    }

    // ---------- Espelho do cursor (sem IPC no caminho quente) ----------

    @Test
    fun cursorMath_afterCommitAppendsLength() {
        // Cursor colapsado em 5, comita 3 chars -> 8.
        assertEquals(8, CursorMath.afterCommit(5, 5, 3))
    }

    @Test
    fun cursorMath_afterCommitReplacesSelection() {
        // Seleção [2,6], comita 1 char -> colapsa em 2 + 1 = 3.
        assertEquals(3, CursorMath.afterCommit(2, 6, 1))
        // Ordem invertida da seleção dá o mesmo resultado (usa o menor índice).
        assertEquals(3, CursorMath.afterCommit(6, 2, 1))
    }

    @Test
    fun cursorMath_afterDeleteMovesBackAndClampsAtZero() {
        assertEquals(4, CursorMath.afterDeleteBefore(5, 5, 1))
        assertEquals(0, CursorMath.afterDeleteBefore(2, 2, 5))
    }

    // ---------- Atalho "dois espaços = ponto final" ----------

    @Test
    fun doubleSpace_convertsAfterWord() {
        assertTrue(shouldConvertSecondSpaceToPeriod("o "))
        assertTrue(shouldConvertSecondSpaceToPeriod("7 "))
    }

    @Test
    fun doubleSpace_doesNotConvertAfterPunctuationOrSpaces() {
        assertFalse(shouldConvertSecondSpaceToPeriod(". "))  // já tem ponto
        assertFalse(shouldConvertSecondSpaceToPeriod("? "))  // outra pontuação
        assertFalse(shouldConvertSecondSpaceToPeriod("  "))  // dois espaços
        assertFalse(shouldConvertSecondSpaceToPeriod("oi"))  // sem espaço antes do cursor
        assertFalse(shouldConvertSecondSpaceToPeriod(" "))   // contexto incompleto
    }

    // ---------- Proteção de nome próprio (não autocorrigir nomes no chat) ----------

    private fun isProperNoun(before: String): Boolean {
        val bounds = findLastWordBounds(before) ?: return false
        return looksLikeProperNoun(before, bounds.first, before.substring(bounds))
    }

    @Test
    fun properNoun_protectsCapitalizedWordMidSentence() {
        assertTrue(isProperNoun("vou ver o Marcelo"))
        assertTrue(isProperNoun("oi Joao"))
        assertTrue(isProperNoun("paguei no Itau"))
    }

    @Test
    fun properNoun_doesNotProtectSentenceStart() {
        // Início do campo: a maiúscula é só auto-capitalização → corrigível.
        assertFalse(isProperNoun("Qaundo"))
        // Logo após pontuação de fim de frase → início de frase → corrigível.
        assertFalse(isProperNoun("Oi. Qaundo"))
        assertFalse(isProperNoun("Pronto! Voce"))
    }

    @Test
    fun properNoun_doesNotProtectLowercaseWords() {
        assertFalse(isProperNoun("vou ver o marcelo"))
        assertFalse(isProperNoun("qaundo"))
    }
}
