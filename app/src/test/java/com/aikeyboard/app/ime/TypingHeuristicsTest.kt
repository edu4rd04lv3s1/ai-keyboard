package com.aikeyboard.app.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingHeuristicsTest {

    private fun protected(word: String) = TypingHeuristics.isProtectedFromAutocorrect(word)

    @Test
    fun protectsInternetSlang() {
        listOf("vc", "vcs", "pra", "pro", "pq", "blz", "vlw", "flw", "mt", "mto", "tb", "tbm", "obg")
            .forEach { assertTrue("deveria proteger '$it'", protected(it)) }
    }

    @Test
    fun protectsLaughter() {
        listOf("kkk", "kk", "kkkk", "rs", "rsrs", "haha", "hahaha", "hehe", "huehue", "ksksks")
            .forEach { assertTrue("deveria proteger risada '$it'", protected(it)) }
    }

    @Test
    fun protectsEmphaticElongation() {
        listOf("siiim", "amooo", "naooo", "muitooo", "nossaaa")
            .forEach { assertTrue("deveria proteger alongamento '$it'", protected(it)) }
    }

    @Test
    fun protectsTokensWithDigits() {
        listOf("10kg", "b2b", "x1", "covid19", "mp3").forEach {
            assertTrue("deveria proteger token com dígito '$it'", protected(it))
        }
    }

    @Test
    fun protectsCamelCaseBrandsAndShortAcronyms() {
        assertTrue(protected("iPhone"))
        assertTrue(protected("WhatsApp"))
        assertTrue(protected("OK"))
        assertTrue(protected("API"))
        assertTrue(protected("CEP"))
    }

    @Test
    fun doesNotProtectOrdinaryTyposOrWords() {
        // Estes DEVEM continuar corrigíveis — o guarda não pode bloqueá-los.
        listOf("qaundo", "voce", "nao", "entao", "trabhalo", "casa", "obrigado", "Brasil")
            .forEach { assertFalse("não deveria proteger '$it'", protected(it)) }
    }

    @Test
    fun longAllCapsWordStaysCorrectable() {
        // Caps lock numa palavra longa não é sigla: continua corrigível.
        assertFalse(protected("QAUNDO"))
    }
}
