package com.aikeyboard.app.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Aprendizado local do teclado.
 *
 * Guarda:
 *  - frequência das palavras realmente usadas pelo usuário;
 *  - pares de autocorreção que o usuário rejeitou explicitamente.
 *
 * Tudo fica apenas no dispositivo e usa uma persistência compacta em SharedPreferences.
 *
 * Concorrência: a escrita acontece na main thread (eventos do IME) enquanto a
 * leitura via [snapshot] acontece no pipeline de sugestões em
 * `Dispatchers.Default`. Por isso as estruturas são concorrentes
 * ([ConcurrentHashMap]) — antes eram `HashMap` simples lidos/escritos de duas
 * threads, o que disparava `ConcurrentModificationException` e congelava o
 * teclado.
 */
class PersonalizationStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val frequencies: ConcurrentHashMap<String, Int> =
        ConcurrentHashMap<String, Int>().apply { putAll(loadFrequencies()) }
    private val rejectedCorrections: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
            .apply { addAll(loadRejectedCorrections()) }
    /** Bigramas aprendidos: palavra anterior -> (próxima palavra -> contagem). */
    private val bigrams: ConcurrentHashMap<String, ConcurrentHashMap<String, Int>> =
        ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>().apply { putAll(loadBigrams()) }
    private var dirtyWordCount = 0
    private var dirtyBigramCount = 0

    /**
     * Snapshot *read-through*: aponta para as MESMAS coleções concorrentes,
     * sem copiar. Antes o getter fazia `frequencies.toMap()` +
     * `rejectedCorrections.toSet()` a CADA keystroke (até 1500 entradas),
     * gerando lixo e pausas de GC. Como as coleções são concorrentes, a
     * iteração fraca do `ConcurrentHashMap` é segura mesmo durante escrita.
     */
    val snapshot: PersonalizationSnapshot =
        PersonalizationSnapshot(frequencies, rejectedCorrections, bigrams)

    fun recordCommittedWord(word: String) {
        val normalized = normalizeWord(word) ?: return
        frequencies.merge(normalized, 1) { old, _ -> (old + 1).coerceAtMost(MAX_FREQUENCY) }
        dirtyWordCount++
        // Persistência fica apenas em pontos do ciclo de vida do IME
        // (`onFinishInputView` / `onDestroy`). Antes esse caminho fazia
        // `saveFrequencies()` a cada 12 palavras — o que envolve sort +
        // joinToString de até 1500 entradas e causava micro-stalls
        // imprevisíveis durante a digitação contínua.
    }

    fun recordRejectedCorrection(original: String, corrected: String) {
        val key = correctionKey(original, corrected) ?: return
        if (rejectedCorrections.add(key)) {
            saveRejectedCorrections()
        }
    }

    /**
     * Registra que [word] foi escrita logo após [previous] — alimenta a predição
     * de próxima palavra. Barato (só merges em memória); persistido no `flush`.
     */
    fun recordBigram(previous: String, word: String) {
        val prev = normalizeWord(previous) ?: return
        val next = normalizeWord(word) ?: return
        if (prev == next) return
        val nexts = bigrams.getOrPut(prev) { ConcurrentHashMap() }
        nexts.merge(next, 1) { old, _ -> (old + 1).coerceAtMost(MAX_FREQUENCY) }
        dirtyBigramCount++
    }

    fun flush() {
        if (dirtyWordCount > 0) {
            saveFrequencies()
            dirtyWordCount = 0
        }
        if (dirtyBigramCount > 0) {
            saveBigrams()
            dirtyBigramCount = 0
        }
    }

    private fun loadFrequencies(): Map<String, Int> =
        prefs.getString(KEY_FREQUENCIES, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val sep = line.lastIndexOf('=')
                if (sep <= 0) return@mapNotNull null
                val word = normalizeWord(line.substring(0, sep)) ?: return@mapNotNull null
                val count = line.substring(sep + 1).toIntOrNull() ?: return@mapNotNull null
                word to count.coerceIn(1, MAX_FREQUENCY)
            }
            ?.toMap()
            .orEmpty()

    private fun saveFrequencies() {
        val serialized = frequencies.entries
            .sortedByDescending { it.value }
            .take(MAX_STORED_WORDS)
            .joinToString("\n") { (word, count) -> "$word=$count" }
        prefs.edit().putString(KEY_FREQUENCIES, serialized).apply()
    }

    private fun loadRejectedCorrections(): Set<String> =
        prefs.getStringSet(KEY_REJECTED_CORRECTIONS, emptySet())?.toSet().orEmpty()

    private fun saveRejectedCorrections() {
        prefs.edit().putStringSet(KEY_REJECTED_CORRECTIONS, rejectedCorrections).apply()
    }

    private fun loadBigrams(): Map<String, ConcurrentHashMap<String, Int>> {
        val raw = prefs.getString(KEY_BIGRAMS, null) ?: return emptyMap()
        val map = HashMap<String, ConcurrentHashMap<String, Int>>()
        raw.lineSequence().forEach { line ->
            val arrow = line.indexOf('>')
            val eq = line.lastIndexOf('=')
            if (arrow <= 0 || eq <= arrow + 1) return@forEach
            val prev = line.substring(0, arrow)
            val next = line.substring(arrow + 1, eq)
            val count = line.substring(eq + 1).toIntOrNull() ?: return@forEach
            if (prev.isBlank() || next.isBlank()) return@forEach
            map.getOrPut(prev) { ConcurrentHashMap() }[next] = count.coerceIn(1, MAX_FREQUENCY)
        }
        return map
    }

    private fun saveBigrams() {
        val sb = StringBuilder()
        var prevCount = 0
        for ((prev, nexts) in bigrams) {
            if (prevCount >= MAX_BIGRAM_PREVS) break
            nexts.entries.sortedByDescending { it.value }.take(MAX_NEXTS_PER_PREV)
                .forEach { (next, count) ->
                    sb.append(prev).append('>').append(next).append('=').append(count).append('\n')
                }
            prevCount++
        }
        prefs.edit().putString(KEY_BIGRAMS, sb.toString()).apply()
    }

    private fun normalizeWord(word: String): String? =
        word.trim()
            .lowercase()
            .takeIf { value -> value.isNotBlank() && value.any(Char::isLetter) }

    private fun correctionKey(original: String, corrected: String): String? {
        val left = normalizeWord(original) ?: return null
        val right = normalizeWord(corrected) ?: return null
        if (left == right) return null
        return "$left>$right"
    }

    companion object {
        private const val PREFS_NAME = "ai_keyboard_personalization"
        private const val KEY_FREQUENCIES = "word_frequencies"
        private const val KEY_REJECTED_CORRECTIONS = "rejected_corrections"
        private const val KEY_BIGRAMS = "word_bigrams"
        private const val MAX_STORED_WORDS = 1500
        private const val MAX_FREQUENCY = 999
        private const val MAX_BIGRAM_PREVS = 500
        private const val MAX_NEXTS_PER_PREV = 5
    }
}

data class PersonalizationSnapshot(
    val frequencies: Map<String, Int> = emptyMap(),
    val rejectedCorrections: Set<String> = emptySet(),
    val bigrams: Map<String, Map<String, Int>> = emptyMap()
) {
    fun frequencyOf(word: String): Int = frequencies[word.lowercase()] ?: 0

    fun isRejected(original: String, corrected: String): Boolean =
        "${original.lowercase()}>${corrected.lowercase()}" in rejectedCorrections

    /** Próximas palavras mais prováveis após [previous], por contagem aprendida. */
    fun topNextWords(previous: String, limit: Int): List<String> {
        val nexts = bigrams[previous.lowercase()] ?: return emptyList()
        if (nexts.isEmpty()) return emptyList()
        return nexts.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }
}
