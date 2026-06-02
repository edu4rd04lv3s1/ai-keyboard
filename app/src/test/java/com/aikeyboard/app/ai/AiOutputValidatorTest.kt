package com.aikeyboard.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOutputValidatorTest {

    @Test
    fun rewriteEvaluationDatasetMatchesExpectedAcceptance() {
        val rows = javaClass.classLoader
            ?.getResourceAsStream("ai/rewrite_eval_cases.tsv")
            ?.bufferedReader()
            ?.useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { line ->
                        val parts = line.split('\t')
                        RewriteEvalCase(
                            style = CorrectionStyle.valueOf(parts[0]),
                            expectedAccept = parts[1] == "ACCEPT",
                            original = parts[2],
                            output = parts[3]
                        )
                    }
                    .toList()
            }
            .orEmpty()

        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            assertEquals(
                row.expectedAccept,
                AiOutputValidator.shouldAccept(row.original, row.output, row.style)
            )
        }
    }

    @Test
    fun acceptsOutputsThatPreserveProtectedTokens() {
        assertTrue(
            AiOutputValidator.shouldAccept(
                original = "Me chama às 18:30 no @joao ou em https://site.com #evento",
                output = "Me chama às 18:30 no @joao ou em https://site.com #evento.",
                style = CorrectionStyle.HUMANIZE
            )
        )
    }

    @Test
    fun rejectsOutputsThatDropProtectedTokens() {
        assertFalse(
            AiOutputValidator.shouldAccept(
                original = "Me chama às 18:30 no @joao ou em https://site.com #evento",
                output = "Me chama mais tarde no site.",
                style = CorrectionStyle.HUMANIZE
            )
        )
    }

    private data class RewriteEvalCase(
        val style: CorrectionStyle,
        val expectedAccept: Boolean,
        val original: String,
        val output: String
    )
}
