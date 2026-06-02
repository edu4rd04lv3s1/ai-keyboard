package com.aikeyboard.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Dicionário pessoal do usuário — palavras que o app NÃO deve corrigir
 * automaticamente (nomes próprios, gírias pessoais, jargão, etc.).
 *
 * Persiste como `Set<String>` em SharedPreferences. Todas as palavras são
 * armazenadas em lowercase para lookup case-insensitive.
 */
class UserDictionaryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Set<String>> = _flow

    val current: Set<String> get() = _flow.value

    private fun load(): Set<String> =
        prefs.getStringSet(KEY_WORDS, emptySet())?.toSet() ?: emptySet()

    /** Adiciona uma palavra ao dicionário (lowercase). Retorna true se foi nova. */
    fun add(word: String): Boolean {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return false
        val updated = current + w
        if (updated.size == current.size) return false
        save(updated)
        return true
    }

    /** Remove uma palavra. Retorna true se existia. */
    fun remove(word: String): Boolean {
        val w = word.trim().lowercase()
        val updated = current - w
        if (updated.size == current.size) return false
        save(updated)
        return true
    }

    /** Verifica se [word] está no dicionário (case-insensitive). */
    fun contains(word: String): Boolean = current.contains(word.trim().lowercase())

    fun clear() {
        save(emptySet())
    }

    private fun save(set: Set<String>) {
        prefs.edit().putStringSet(KEY_WORDS, set).apply()
        _flow.value = set
    }

    companion object {
        private const val PREFS_NAME = "ai_keyboard_user_dict"
        private const val KEY_WORDS = "words"
    }
}
