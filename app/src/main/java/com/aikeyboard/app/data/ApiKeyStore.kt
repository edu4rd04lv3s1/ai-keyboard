package com.aikeyboard.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Armazena as API keys (Groq/Gemini) criptografadas no dispositivo via
 * EncryptedSharedPreferences. As chaves só existem se o usuário as configurar
 * dentro do app — nenhuma chave é embutida no APK.
 * Nenhum dado sai do aparelho exceto quando o usuário invoca uma ação de IA.
 */
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // -------- Groq --------

    /** Retorna a key Groq salva pelo usuário, ou null se nenhuma foi configurada. */
    fun getApiKey(): String? =
        prefs.getString(KEY_GROQ, null)?.takeIf { it.isNotBlank() }

    /** Indica se o usuário configurou uma key Groq. */
    fun hasUserKey(): Boolean =
        prefs.getString(KEY_GROQ, null)?.isNotBlank() == true

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GROQ, key.trim()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_GROQ).apply()
    }

    fun maskedPreview(): String? = getApiKey()?.let(::mask)

    // -------- Gemini --------

    /** Retorna a key Gemini salva pelo usuário, ou null se nenhuma foi configurada. */
    fun getGeminiApiKey(): String? =
        prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    fun hasUserGeminiKey(): Boolean =
        prefs.getString(KEY_GEMINI, null)?.isNotBlank() == true

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key.trim()).apply()
    }

    fun clearGemini() {
        prefs.edit().remove(KEY_GEMINI).apply()
    }

    fun maskedGeminiPreview(): String? = getGeminiApiKey()?.let(::mask)

    fun getGeminiSecondaryApiKey(): String? =
        prefs.getString(KEY_GEMINI_SECONDARY, null)?.takeIf { it.isNotBlank() }

    fun setGeminiSecondaryApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_SECONDARY, key.trim()).apply()
    }

    // -------- Helpers --------

    private fun mask(k: String): String =
        if (k.length <= 8) "••••${k.takeLast(2)}"
        else "${k.take(4)}…${k.takeLast(4)}"

    companion object {
        private const val PREFS_NAME = "ai_keyboard_secure_prefs"
        private const val KEY_GROQ = "groq_api_key"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_GEMINI_SECONDARY = "gemini_api_key_secondary"
    }
}
