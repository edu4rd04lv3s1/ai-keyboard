package com.aikeyboard.app.ai

data class AiRequestOptions(
    val model: String? = null,
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 512
)

/**
 * Abstração de um provider de LLM (Groq, Gemini, …).
 * Permite trocar/encadear providers sem mudar o resto do app.
 */
interface AiClient {
    /** Identificador legível (Groq, Gemini, …) usado em logs/UI. */
    val name: String

    /** True se este provider tem credenciais configuradas e pode ser usado agora. */
    fun isConfigured(): Boolean

    /**
     * Faz uma chamada de chat completion e retorna o conteúdo.
     * Lança [AiException] em qualquer erro recuperável.
     */
    suspend fun chat(
        systemPrompt: String,
        userPrompt: String,
        options: AiRequestOptions = AiRequestOptions()
    ): String
}

/**
 * Erro unificado vindo de qualquer provider.
 * O campo [recoverable] indica se faz sentido tentar com outro provider.
 */
class AiException(
    val code: String,
    override val message: String,
    val recoverable: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause) {

    companion object {
        /** Erro veio de rate-limit / quota — vale a pena tentar fallback. */
        fun rateLimit(provider: String, msg: String? = null) = AiException(
            code = "rate_limit",
            message = msg ?: "$provider: limite atingido",
            recoverable = true
        )

        /** Erro de rede — vale a pena tentar fallback. */
        fun network(msg: String?) = AiException(
            code = "network_error",
            message = msg ?: "Falha de rede",
            recoverable = true
        )

        /** Configuração ausente (sem API key) — não fazer fallback (provider só "não existe"). */
        fun missingKey(provider: String) = AiException(
            code = "missing_api_key",
            message = "$provider: API key não configurada",
            recoverable = false
        )

        /**
         * Chave inválida/expirada/sem permissão (HTTP 401/403, API_KEY_INVALID).
         * RECUPERÁVEL: outro provedor pode ter credencial válida, então a cadeia
         * de fallback deve continuar em vez de abortar.
         */
        fun invalidKey(provider: String, msg: String? = null) = AiException(
            code = "invalid_api_key",
            message = msg ?: "$provider: API key inválida ou expirada",
            recoverable = true
        )

        /** Erro genérico — não retentar (auth, body inválido, etc.). */
        fun api(provider: String, code: String, msg: String) = AiException(
            code = code,
            message = "$provider: $msg",
            recoverable = false
        )
    }
}
