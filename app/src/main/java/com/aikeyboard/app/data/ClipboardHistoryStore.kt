package com.aikeyboard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/**
 * Histórico compacto de clipboard para o teclado.
 *
 * Guarda somente textos que o usuário pediu para ver ou colar pelo IME. Não observa
 * o clipboard em segundo plano.
 */
class ClipboardHistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(): List<String> = load()

    fun add(text: String): List<String> {
        val normalized = normalize(text) ?: return load()
        val updated = (listOf(normalized) + load().filterNot { it == normalized })
            .take(MAX_ITEMS)
        save(updated)
        return updated
    }

    private fun load(): List<String> =
        prefs.getString(KEY_ITEMS, null)
            ?.lineSequence()
            ?.mapNotNull(::decode)
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(MAX_ITEMS)
            ?.toList()
            .orEmpty()

    private fun save(items: List<String>) {
        val serialized = items
            .mapNotNull { normalize(it) }
            .distinct()
            .take(MAX_ITEMS)
            .joinToString("\n", transform = ::encode)
        prefs.edit().putString(KEY_ITEMS, serialized).apply()
    }

    private fun normalize(text: String): String? =
        text.trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_CHARS)
            .takeIf { it.isNotBlank() }

    private fun encode(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(encoded: String): String? =
        runCatching {
            String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrNull()

    companion object {
        private const val PREFS_NAME = "ai_keyboard_clipboard"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 8
        private const val MAX_CHARS = 500
    }
}
