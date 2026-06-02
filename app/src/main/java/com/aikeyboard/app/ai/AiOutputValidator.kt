package com.aikeyboard.app.ai

import java.text.Normalizer

internal object AiOutputValidator {
    private val protectedTokenRegex = Regex(
        """https?://\S+|www\.\S+|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}|@\w+|#\w+|\b\d+(?:[./:-]\d+)*\b"""
    )
    private val sensitiveDriftTerms = listOf(
        "ameac",
        "ameaca",
        "ameacando",
        "amedront",
        "assust",
        "medo",
        "perigo",
        "perigoso",
        "violencia",
        "violento",
        "machucar",
        "agredir",
        "agressao",
        "crime",
        "denunciar",
        "denuncia"
    )

    fun shouldAccept(original: String, output: String, style: CorrectionStyle): Boolean {
        if (output.isBlank()) return false
        if (!preservesProtectedTokens(original, output)) return false
        if (hasUnsafeSemanticDrift(original, output, style)) return false
        return true
    }

    private fun preservesProtectedTokens(original: String, output: String): Boolean {
        val originalTokens = protectedTokenRegex.findAll(original)
            .map { normalizeProtectedToken(it.value) }
            .groupingBy { it }
            .eachCount()
        if (originalTokens.isEmpty()) return true

        val outputTokens = protectedTokenRegex.findAll(output)
            .map { normalizeProtectedToken(it.value) }
            .groupingBy { it }
            .eachCount()
        return originalTokens.all { (token, count) ->
            outputTokens.getOrDefault(token, 0) >= count
        }
    }

    private fun hasUnsafeSemanticDrift(
        original: String,
        output: String,
        style: CorrectionStyle
    ): Boolean {
        if (style !in semanticPreservingStyles) return false
        val originalNormalized = normalize(original)
        val outputNormalized = normalize(output)
        return sensitiveDriftTerms.any { term ->
            term in outputNormalized && term !in originalNormalized
        }
    }

    private fun normalize(text: String): String {
        val withoutMarks = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutMarks.replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeProtectedToken(token: String): String =
        token.trimEnd('.', ',', ';', ':', '!', '?')

    private val semanticPreservingStyles = setOf(
        CorrectionStyle.REWRITE,
        CorrectionStyle.ENHANCE,
        CorrectionStyle.FRIENDLY,
        CorrectionStyle.HUMANIZE,
        CorrectionStyle.POLITE,
        CorrectionStyle.CASUAL,
        CorrectionStyle.FORMAL,
        CorrectionStyle.PROFESSIONAL,
        CorrectionStyle.WHATSAPP_CASUAL
    )
}
