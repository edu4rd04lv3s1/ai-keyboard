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
 * Cliente HTTP minimalista para a API Groq (OpenAI-compatible).
 * Documentação: https://console.groq.com/docs/api-reference
 */
class GroqClient(
    private val apiKeyStore: ApiKeyStore,
    private val baseUrl: String = DEFAULT_BASE_URL
) : AiClient {

    override val name = "Groq"

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

    override fun isConfigured(): Boolean = apiKeyStore.getApiKey() != null

    override suspend fun chat(
        systemPrompt: String,
        userPrompt: String,
        options: AiRequestOptions
    ): String =
        chat(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            model = options.model ?: DEFAULT_MODEL,
            temperature = options.temperature,
            maxTokens = options.maxOutputTokens
        )

    /**
     * Faz uma chamada de chat completion à Groq e retorna o conteúdo da resposta.
     * Lança [AiException] em qualquer erro recuperável da API.
     */
    suspend fun chat(
        systemPrompt: String,
        userPrompt: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.2,
        maxTokens: Int = 1024
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getApiKey()
            ?: throw AiException.missingKey(name)

        val payload = ChatRequest(
            model = model,
            temperature = temperature,
            max_tokens = maxTokens,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userPrompt)
            )
        )
        val body = json.encodeToString(ChatRequest.serializer(), payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val parsed = runCatching {
                        json.decodeFromString(GroqErrorEnvelope.serializer(), text)
                    }.getOrNull()
                    val errMsg: String = parsed?.error?.message ?: "HTTP ${response.code}"
                    val errCode: String = parsed?.error?.code ?: "http_${response.code}"
                    // Rate-limit / quota → fallback faz sentido
                    if (response.code == 429 ||
                        errCode.contains("rate_limit", true) ||
                        errCode.contains("quota", true)
                    ) throw AiException.rateLimit(name, errMsg)
                    // Chave inválida/expirada → recuperável (tenta o próximo provedor).
                    if (response.code == 401 || response.code == 403 ||
                        errCode.contains("invalid_api_key", true)
                    ) throw AiException.invalidKey(name, "$name: $errMsg")
                    throw AiException.api(name, errCode, errMsg)
                }
                val parsed = json.decodeFromString(ChatResponse.serializer(), text)
                parsed.choices.firstOrNull()?.message?.content?.trim()
                    ?: throw AiException.api(name, "empty_response", "Resposta vazia")
            }
        } catch (e: IOException) {
            throw AiException.network(e.message)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        const val FAST_MODEL = "llama-3.1-8b-instant"
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    val max_tokens: Int
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList()) {
    @Serializable data class Choice(val message: ChatMessage)
}

@Serializable
private data class GroqErrorBody(
    val message: String? = null,
    val code: String? = null
)

@Serializable
private data class GroqErrorEnvelope(val error: GroqErrorBody? = null)

/**
 * Compatibilidade histórica: alias de [AiException] para chamadas que ainda usam o nome antigo.
 */
typealias GroqException = AiException
