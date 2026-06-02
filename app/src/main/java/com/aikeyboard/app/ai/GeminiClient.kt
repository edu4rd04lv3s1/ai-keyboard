package com.aikeyboard.app.ai

import com.aikeyboard.app.data.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente para Google Gemini (Generative Language API v1beta).
 *
 * Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key=API_KEY
 * Free tier (gemini-2.0-flash, em jul/2025): 15 RPM, 1.000.000 TPM, 1.500 RPD.
 *
 * Documentação: https://ai.google.dev/api/rest/v1beta/models/generateContent
 */
class GeminiClient(
    private val apiKeyStore: ApiKeyStore,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val model: String = DEFAULT_MODEL,
    private val apiKeyProvider: () -> String? = { apiKeyStore.getGeminiApiKey() }
) : AiClient {

    override val name = "Gemini"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun isConfigured(): Boolean = apiKeyProvider() != null

    override suspend fun chat(
        systemPrompt: String,
        userPrompt: String,
        options: AiRequestOptions
    ): String =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyProvider()
                ?: throw AiException.missingKey(name)

            val payload = GenerateRequest(
                system_instruction = TextBlock(parts = listOf(Part(systemPrompt))),
                contents = listOf(
                    Content(role = "user", parts = listOf(Part(userPrompt)))
                ),
                generationConfig = GenerationConfig(
                    temperature = options.temperature,
                    maxOutputTokens = options.maxOutputTokens
                )
            )
            val body = json.encodeToString(GenerateRequest.serializer(), payload)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val url = "$baseUrl/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val parsed = runCatching {
                            json.decodeFromString(GeminiErrorEnvelope.serializer(), text)
                        }.getOrNull()
                        val errMsg = parsed?.error?.message ?: "HTTP ${response.code}"
                        val errStatus = parsed?.error?.status ?: "http_${response.code}"
                        if (response.code == 429 ||
                            errStatus.equals("RESOURCE_EXHAUSTED", true) ||
                            errMsg.contains("quota", true)
                        ) throw AiException.rateLimit(name, errMsg)
                        // Chave inválida/expirada → recuperável (tenta o próximo provedor).
                        if (response.code == 401 || response.code == 403 ||
                            errStatus.equals("API_KEY_INVALID", true) ||
                            errMsg.contains("API key", true)
                        ) throw AiException.invalidKey(name, "$name: $errMsg")
                        throw AiException.api(name, errStatus, errMsg)
                    }
                    val parsed = json.decodeFromString(GenerateResponse.serializer(), text)
                    val out = parsed.candidates.firstOrNull()
                        ?.content?.parts?.firstOrNull()?.text?.trim()
                    out ?: throw AiException.api(name, "empty_response", "Resposta vazia")
                }
            } catch (e: IOException) {
                throw AiException.network(e.message)
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
    }
}

// -------- Wire types --------

@Serializable
private data class GenerateRequest(
    val system_instruction: TextBlock? = null,
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
private data class TextBlock(val parts: List<Part>)

@Serializable
private data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
private data class Part(val text: String)

@Serializable
private data class GenerationConfig(
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 1024
)

@Serializable
private data class GenerateResponse(
    val candidates: List<Candidate> = emptyList()
) {
    @Serializable
    data class Candidate(
        val content: ContentReply? = null,
        val finishReason: String? = null
    )

    @Serializable
    data class ContentReply(
        val parts: List<Part> = emptyList(),
        val role: String? = null
    )
}

@Serializable
private data class GeminiErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
private data class GeminiErrorEnvelope(val error: GeminiErrorBody? = null)
