package com.aikeyboard.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aikeyboard.app.BuildConfig

/**
 * Armazena a Groq API key criptografada no dispositivo via EncryptedSharedPreferences.
 * Se nenhuma key foi configurada pelo usuário, cai para a key padrão embutida no build
 * pessoal (definida em local.properties).
 * Nenhum dado sai do aparelho exceto quando o usuário invoca "Corrigir com IA".
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

    /** Retorna a key Groq salva pelo usuário; se não houver, usa a padrão do build. */
    fun getApiKey(): String? {
        val saved = prefs.getString(KEY_GROQ, null)?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        return BuildConfig.GROQ_DEFAULT_API_KEY.takeIf { it.isNotBlank() }
    }

    /** Indica se a key Groq ativa veio do usuário (e não da padrão embutida). */
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

    /** Retorna a key Gemini salva pelo usuário; se não houver, usa a padrão do build. */
    fun getGeminiApiKey(): String? {
        val saved = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        return BuildConfig.GEMINI_DEFAULT_API_KEY.takeIf { it.isNotBlank() }
    }

    fun hasUserGeminiKey(): Boolean =
        prefs.getString(KEY_GEMINI, null)?.isNotBlank() == true

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key.trim()).apply()
    }

    fun clearGemini() {
        prefs.edit().remove(KEY_GEMINI).apply()
    }

    fun maskedGeminiPreview(): String? = getGeminiApiKey()?.let(::mask)

    fun getGeminiSecondaryApiKey(): String? {
        val saved = prefs.getString(KEY_GEMINI_SECONDARY, null)?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        return BuildConfig.GEMINI_SECONDARY_DEFAULT_API_KEY.takeIf { it.isNotBlank() }
    }

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
