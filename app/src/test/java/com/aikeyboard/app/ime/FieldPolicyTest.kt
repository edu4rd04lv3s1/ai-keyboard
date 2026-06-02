package com.aikeyboard.app.ime

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldPolicyTest {

    @Test
    fun plainTextEnablesEverything() {
        val p = FieldPolicy.forInputType(InputType.TYPE_CLASS_TEXT)
        assertTrue(p.autocorrect)
        assertTrue(p.suggestions)
        assertTrue(p.autoCap)
    }

    @Test
    fun passwordFieldsAreLocked() {
        val variations = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        variations.forEach { v ->
            val p = FieldPolicy.forInputType(InputType.TYPE_CLASS_TEXT or v)
            assertEquals(FieldPolicy.LOCKED, p)
        }
    }

    @Test
    fun emailAndUriAndFilterAreLocked() {
        listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER
        ).forEach { v ->
            assertEquals(FieldPolicy.LOCKED, FieldPolicy.forInputType(InputType.TYPE_CLASS_TEXT or v))
        }
    }

    @Test
    fun numericAndPhoneAndDatetimeAndNullAreLocked() {
        listOf(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            0 /* TYPE_NULL */
        ).forEach { cls ->
            assertEquals(FieldPolicy.LOCKED, FieldPolicy.forInputType(cls))
        }
    }

    @Test
    fun noSuggestionsFlagDisablesCorrectionButKeepsAutoCap() {
        val p = FieldPolicy.forInputType(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        assertFalse(p.autocorrect)
        assertFalse(p.suggestions)
        assertTrue(p.autoCap)
    }

    @Test
    fun capSentencesFlagStaysFullyEnabled() {
        val p = FieldPolicy.forInputType(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        assertTrue(p.autocorrect)
        assertTrue(p.suggestions)
        assertTrue(p.autoCap)
    }
}
