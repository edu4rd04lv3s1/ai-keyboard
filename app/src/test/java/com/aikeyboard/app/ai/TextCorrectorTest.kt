package com.aikeyboard.app.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCorrectorTest {

    @Test
    fun whatsappStyleUsesReliableLowTemperatureOptions() {
        val options = CorrectionStyle.WHATSAPP_CASUAL.requestOptionsFor("voce pode mandar depois")

        assertEquals(null, options.model)
        assertEquals(0.0, options.temperature, 0.0)
        assertTrue(options.maxOutputTokens in 384..640)
    }

    @Test
    fun whatsappPromptUsesPopularAbbreviationsAndKeepsCorrectSpelling() {
        val prompt = CorrectionStyle.WHATSAPP_CASUAL.systemPrompt

        // Estilo de conversa de WhatsApp, porém com ortografia correta.
        assertTrue(prompt.contains("ORTOGRAFIA correta"))
        // Aplica as siglas populares que o usuário pediu (ex.: voce->vc, também->tbm).
        assertTrue(prompt.contains("você→vc"))
        assertTrue(prompt.contains("também→tbm"))
        // Regra central: as siglas são corretas neste modo e nunca são expandidas.
        assertTrue(prompt.contains("NUNCA as expanda"))
        // Casual não pode degradar gramática essencial.
        assertTrue(prompt.contains("NUNCA troque \"é\" por"))
        assertTrue(prompt.contains("Vice e minha"))
        assertTrue(prompt.contains("Você e minha"))
    }

    @Test
    fun transformCleansCommonModelWrapperText() = runBlocking {
        val client = FakeAiClient("""Texto reescrito: "vc pode me mandar o endereço dps?"""")
        val corrector = TextCorrector(listOf(client))

        val result = corrector.transform(
            text = "voce pode me mandar o endereço depois",
            style = CorrectionStyle.WHATSAPP_CASUAL
        )

        assertEquals("vc pode me mandar o endereço dps?", result)
    }

    @Test
    fun transformSendsTextAsDelimitedContentNotChat() = runBlocking {
        // Garante o conserto do bug "a IA respondeu em vez de reescrever":
        // o texto vai envelopado com <texto>…</texto> e instrução de não conversar.
        val client = FakeAiClient("ok")
        TextCorrector(listOf(client)).transform("vamos testar", CorrectionStyle.WHATSAPP_CASUAL)

        val sent = requireNotNull(client.lastUserPrompt)
        assertTrue(sent.contains("vamos testar"))
        assertTrue(sent.contains("<texto>"))
        assertTrue(sent.contains("</texto>"))
        assertTrue(sent.contains("não responda"))
    }

    @Test
    fun transformStripsEchoedDelimiterTags() = runBlocking {
        // Se o modelo devolver as marcas do prompt, elas são removidas.
        val client = FakeAiClient("<texto>\nvc pode vir hj?\n</texto>")
        val result = TextCorrector(listOf(client)).transform(
            text = "voce pode vir hoje",
            style = CorrectionStyle.WHATSAPP_CASUAL
        )

        assertEquals("vc pode vir hj?", result)
    }

    @Test
    fun transformFallsBackToOriginalWhenProviderReturnsBlankText() = runBlocking {
        val client = FakeAiClient("   ")
        val corrector = TextCorrector(listOf(client))

        val result = corrector.transform(
            text = "já está bom",
            style = CorrectionStyle.CORRECT
        )

        assertEquals("já está bom", result)
    }

    @Test
    fun whatsappStyleRejectsThreatInterpretationWhenOriginalHasNoThreat() = runBlocking {
        val client = FakeAiClient("Você está me ameaçando")
        val corrector = TextCorrector(listOf(client))

        val original = "Meu amor sabe o que eu vou fazer com você"
        val result = corrector.transform(
            text = original,
            style = CorrectionStyle.WHATSAPP_CASUAL
        )

        assertEquals(original, result)
    }

    // ---------- Fallback robusto (provedor quebrado NÃO bloqueia os demais) ----------

    @Test
    fun deadKeyProviderFallsThroughToWorkingProvider() = runBlocking {
        // Cenário real: 1º provedor com quota esgotada / chave morta, 2º saudável.
        // Deve retornar a resposta do 2º — nunca mais abortar na 1ª falha.
        val dead = FailingAiClient(AiException.rateLimit("Gemini", "quota 0"))
        val healthy = FakeAiClient("Texto corrigido")
        val result = TextCorrector(listOf(dead, healthy)).transform("texto", CorrectionStyle.CORRECT)
        assertEquals("Texto corrigido", result)
    }

    @Test
    fun invalidKeyProviderFallsThrough() = runBlocking {
        val dead = FailingAiClient(AiException.invalidKey("Gemini", "API key expired"))
        val healthy = FakeAiClient("ok corrigido")
        val result = TextCorrector(listOf(dead, healthy)).transform("x", CorrectionStyle.CORRECT)
        assertEquals("ok corrigido", result)
    }

    @Test
    fun unconfiguredProvidersAreSkipped() = runBlocking {
        val notConfigured = FakeAiClient("nunca", configured = false)
        val healthy = FakeAiClient("ok corrigido")
        val result = TextCorrector(listOf(notConfigured, healthy)).transform("x", CorrectionStyle.CORRECT)
        assertEquals("ok corrigido", result)
    }

    @Test
    fun networkErrorOnFirstFallsThroughToSecond() = runBlocking {
        val flaky = FailingAiClient(AiException.network("timeout"))
        val healthy = FakeAiClient("recuperado")
        val result = TextCorrector(listOf(flaky, healthy)).transform("x", CorrectionStyle.CORRECT)
        assertEquals("recuperado", result)
    }

    @Test(expected = AiException::class)
    fun throwsWhenEveryProviderFails() = runBlocking {
        val a = FailingAiClient(AiException.invalidKey("Gemini"))
        val b = FailingAiClient(AiException.rateLimit("Groq"))
        TextCorrector(listOf(a, b)).transform("x", CorrectionStyle.CORRECT)
        Unit
    }

    private class FakeAiClient(
        private val response: String,
        private val configured: Boolean = true
    ) : AiClient {
        override val name = "Fake"
        var lastUserPrompt: String? = null
            private set
        var lastSystemPrompt: String? = null
            private set

        override fun isConfigured(): Boolean = configured

        override suspend fun chat(
            systemPrompt: String,
            userPrompt: String,
            options: AiRequestOptions
        ): String {
            lastSystemPrompt = systemPrompt
            lastUserPrompt = userPrompt
            return response
        }
    }

    /** Cliente que sempre lança — simula provedor com quota esgotada / fora do ar. */
    private class FailingAiClient(private val error: AiException) : AiClient {
        override val name = "Failing"
        override fun isConfigured(): Boolean = true
        override suspend fun chat(
            systemPrompt: String,
            userPrompt: String,
            options: AiRequestOptions
        ): String = throw error
    }
}
