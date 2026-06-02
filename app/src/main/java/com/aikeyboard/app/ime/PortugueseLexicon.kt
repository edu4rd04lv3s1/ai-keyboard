package com.aikeyboard.app.ime

import android.content.Context
import com.aikeyboard.app.R
import java.text.Normalizer

/**
 * Léxico compacto de PT-BR carregado de `res/raw/pt_br_words.txt`.
 *
 * O arquivo é ordenado por frequência aproximada: quanto menor o índice,
 * mais comum a palavra. A estrutura pré-computa índices para manter sugestões
 * rápidas mesmo com um vocabulário bem maior que a lista original do app.
 */
class PortugueseLexicon private constructor(
    sourceWords: List<String>
) {

    constructor(context: Context) : this(
        context.resources
            .openRawResource(R.raw.pt_br_words)
            .bufferedReader()
            .useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toList()
            }
    )

    internal constructor(words: Collection<String>) : this(
        words.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    )

    val words: List<String> = sourceWords

    private val exactWords: Set<String> = words.toSet()
    private val rank: Map<String, Int> = words.withIndex().associate { (i, word) -> word to i }

    private val normalizedForms: Map<String, String> =
        words.associateWith(::normalize)

    private val wordsByNormalized: Map<String, List<String>> =
        words.groupBy { normalizedForms.getValue(it) }

    private val prefixIndex: Map<String, List<String>> = buildPrefixIndex()

    private val wordsByLengthAndInitial: Map<LengthInitial, List<String>> =
        words.groupBy { word ->
            val normalized = normalizedForms.getValue(word)
            LengthInitial(normalized.length, normalized.firstOrNull() ?: '\u0000')
        }

    fun contains(word: String): Boolean = exactWords.contains(word.lowercase())

    fun rankOf(word: String): Int = rank[word.lowercase()] ?: Int.MAX_VALUE

    /**
     * Retorna uma restauração inequívoca de acentos para [word], se existir.
     * Ex.: `voce` -> `você`.
     *
     * Se a forma digitada já é uma palavra válida (`esta`) ou se houver mais de
     * uma possibilidade com a mesma forma normalizada, não corrigimos.
     */
    fun unambiguousAccentMatch(word: String): String? {
        val lower = word.lowercase()
        if (contains(lower)) return null
        val candidates = wordsByNormalized[normalize(lower)].orEmpty()
        return candidates.singleOrNull()
            ?.takeIf { it != lower }
    }

    /**
     * Completa palavras por prefixo normalizado, então `vo` encontra `você`.
     */
    fun completions(prefix: String, limit: Int): List<String> {
        if (prefix.isBlank() || limit <= 0) return emptyList()
        val normalizedPrefix = normalize(prefix)
        val bucketKey = normalizedPrefix.take(MAX_PREFIX_LENGTH)
        return prefixIndex[bucketKey].orEmpty()
            .asSequence()
            .filter { normalizedForms.getValue(it).startsWith(normalizedPrefix) }
            .take(limit)
            .toList()
    }

    /**
     * Palavras cuja forma normalizada começa com [initial] (sem acento, minúscula).
     * Usado pelo decodificador de gesto (swipe) para ancorar na 1ª letra.
     */
    fun wordsStartingWith(initial: Char): List<String> =
        prefixIndex[PortugueseLexicon.normalize(initial.toString())].orEmpty()

    /** Forma normalizada (minúscula, sem acentos) de uma palavra do léxico. */
    fun normalizedForm(word: String): String =
        normalizedForms[word] ?: normalize(word)

    /**
     * Busca candidatos próximos para sugestão/autocorreção.
     * O pool já vem reduzido por inicial + comprimento para manter latência baixa.
     */
    fun fuzzyCandidates(
        word: String,
        maxDistance: Int,
        limit: Int,
        sameInitialOnly: Boolean = true
    ): List<Candidate> {
        if (word.isBlank() || limit <= 0) return emptyList()
        val normalized = normalize(word)
        val initial = normalized.firstOrNull() ?: return emptyList()
        // O pool é capado em `MAX_POOL_PER_BUCKET` por (comprimento,
        // inicial). Como `wordsByLengthAndInitial` preserva a ordem original
        // do arquivo (que é por frequência decrescente), pegamos sempre as
        // palavras mais comuns — o que é exatamente o que queremos para
        // autocorreção. Limita o pior caso do Damerau-Levenshtein.
        val pool = buildList {
            for (len in (normalized.length - maxDistance)..(normalized.length + maxDistance)) {
                if (len <= 0) continue
                if (sameInitialOnly) {
                    val bucket = wordsByLengthAndInitial[LengthInitial(len, initial)].orEmpty()
                    addAll(if (bucket.size > MAX_POOL_PER_BUCKET) bucket.subList(0, MAX_POOL_PER_BUCKET) else bucket)
                } else {
                    wordsByLengthAndInitial
                        .asSequence()
                        .filter { it.key.length == len }
                        .forEach { entry ->
                            val bucket = entry.value
                            addAll(if (bucket.size > MAX_POOL_PER_BUCKET) bucket.subList(0, MAX_POOL_PER_BUCKET) else bucket)
                        }
                }
            }
        }

        return pool.asSequence()
            .mapNotNull { candidate ->
                val candidateNormalized = normalizedForms.getValue(candidate)
                val distance = damerauLevenshteinWithin(normalized, candidateNormalized, maxDistance)
                    ?: return@mapNotNull null
                Candidate(candidate, distance, rankOf(candidate))
            }
            .sortedWith(compareBy<Candidate> { it.distance }.thenBy { it.rank })
            .take(limit)
            .toList()
    }

    /**
     * Candidatos para a autocorreção ULTIMATE, usando [weightedKeyboardDistance]
     * (custo sensível à posição física das teclas). O pool inclui palavras cuja
     * inicial é a mesma OU uma tecla vizinha da digitada (cobre erro na 1ª letra),
     * com comprimento dentro de ±2, capado por frequência para manter a latência.
     * `Candidate.distance` aqui é o custo ponderado (×10).
     */
    fun weightedCandidates(word: String, maxCost: Int, limit: Int): List<Candidate> {
        if (word.isBlank() || limit <= 0) return emptyList()
        val normalized = normalize(word)
        if (normalized.length < 2) return emptyList()
        val initial = normalized[0]

        val initials = HashSet<Char>(8)
        initials.add(initial)
        var k = 'a'
        while (k <= 'z') {
            if (KeyboardAdjacency.areAdjacent(initial, k)) initials.add(k)
            k++
        }

        val pool = ArrayList<String>(256)
        for (len in (normalized.length - WEIGHTED_LEN_DELTA)..(normalized.length + WEIGHTED_LEN_DELTA)) {
            if (len <= 1) continue
            for (ini in initials) {
                val bucket = wordsByLengthAndInitial[LengthInitial(len, ini)] ?: continue
                if (bucket.size > MAX_WEIGHTED_POOL_PER_BUCKET) {
                    pool.addAll(bucket.subList(0, MAX_WEIGHTED_POOL_PER_BUCKET))
                } else {
                    pool.addAll(bucket)
                }
            }
        }

        return pool.asSequence()
            .mapNotNull { candidate ->
                val candNorm = normalizedForms.getValue(candidate)
                val cost = weightedKeyboardDistance(normalized, candNorm, maxCost)
                    ?: return@mapNotNull null
                Candidate(candidate, cost, rankOf(candidate))
            }
            .sortedWith(compareBy<Candidate> { it.distance }.thenBy { it.rank })
            .take(limit)
            .toList()
    }

    data class Candidate(
        val word: String,
        val distance: Int,
        val rank: Int
    )

    private data class LengthInitial(val length: Int, val initial: Char)

    private fun buildPrefixIndex(): Map<String, List<String>> {
        val mutable = mutableMapOf<String, MutableList<String>>()
        words.forEach { word ->
            val normalized = normalizedForms.getValue(word)
            val maxPrefix = minOf(MAX_PREFIX_LENGTH, normalized.length)
            for (len in 1..maxPrefix) {
                val prefix = normalized.substring(0, len)
                mutable.getOrPut(prefix) { mutableListOf() }.add(word)
            }
        }
        return mutable.mapValues { (_, value) -> value.toList() }
    }

    companion object {
        private const val MAX_PREFIX_LENGTH = 5

        /**
         * Teto do pool de candidatos por (comprimento, inicial) no
         * fuzzy matching. Mantém o pior caso do Damerau-Levenshtein
         * bounded — sem esse cap, palavras com inicial muito comum
         * (ex.: 'a', 'e', 'p') podiam gerar centenas de comparações
         * por keystroke. Top-96 por frequência cobre praticamente
         * todos os casos reais de autocorreção.
         */
        private const val MAX_POOL_PER_BUCKET = 96

        // --- Autocorreção ULTIMATE: custos (×10) da distância ponderada ---
        /** Substituição por tecla VIZINHA no QWERTY (typo provável). */
        const val W_SUB_ADJ = 10
        /** Substituição por tecla distante (provavelmente outra palavra). */
        const val W_SUB_FAR = 22
        /** Inserção ou remoção de letra. */
        const val W_INDEL = 14
        /** Transposição de letras adjacentes (ex.: "qeu" -> "que"). */
        const val W_TRANSPOSE = 9
        private const val WEIGHTED_LEN_DELTA = 2
        private const val MAX_WEIGHTED_POOL_PER_BUCKET = 60

        fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")

        /**
         * Distância de Damerau-Levenshtein com poda por limite.
         * Trata substituição, inserção, remoção e transposição adjacente.
         */
        fun damerauLevenshteinWithin(a: String, b: String, limit: Int): Int? {
            if (a == b) return 0
            if (kotlin.math.abs(a.length - b.length) > limit) return null

            val prevPrev = IntArray(b.length + 1)
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)

            for (i in 1..a.length) {
                curr[0] = i
                var rowMin = curr[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var value = minOf(
                        prev[j] + 1,
                        curr[j - 1] + 1,
                        prev[j - 1] + cost
                    )
                    if (
                        i > 1 &&
                        j > 1 &&
                        a[i - 1] == b[j - 2] &&
                        a[i - 2] == b[j - 1]
                    ) {
                        value = minOf(value, prevPrev[j - 2] + 1)
                    }
                    curr[j] = value
                    rowMin = minOf(rowMin, value)
                }
                if (rowMin > limit) return null
                for (k in prev.indices) prevPrev[k] = prev[k]
                val tmp = prev
                prev = curr
                curr = tmp
            }

            return prev[b.length].takeIf { it <= limit }
        }

        /**
         * Distância de edição ponderada pela geometria do teclado, escalada ×10
         * para usar aritmética inteira (sem alocar Double). Substituição entre
         * teclas vizinhas é barata ([W_SUB_ADJ]); entre teclas distantes é cara
         * ([W_SUB_FAR]); inserção/remoção [W_INDEL]; transposição [W_TRANSPOSE].
         * Retorna o custo (×10) ou null se ultrapassar [limit] (poda por linha).
         */
        fun weightedKeyboardDistance(a: String, b: String, limit: Int): Int? {
            if (a == b) return 0
            val la = a.length
            val lb = b.length
            if (kotlin.math.abs(la - lb) * W_INDEL > limit) return null

            val prevPrev = IntArray(lb + 1)
            var prev = IntArray(lb + 1) { it * W_INDEL }
            var curr = IntArray(lb + 1)

            for (i in 1..la) {
                curr[0] = i * W_INDEL
                var rowMin = curr[0]
                val ai = a[i - 1]
                for (j in 1..lb) {
                    val bj = b[j - 1]
                    val subCost = when {
                        ai == bj -> 0
                        KeyboardAdjacency.areAdjacent(ai, bj) -> W_SUB_ADJ
                        else -> W_SUB_FAR
                    }
                    var value = minOf(
                        prev[j] + W_INDEL,
                        curr[j - 1] + W_INDEL,
                        prev[j - 1] + subCost
                    )
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        value = minOf(value, prevPrev[j - 2] + W_TRANSPOSE)
                    }
                    curr[j] = value
                    if (value < rowMin) rowMin = value
                }
                if (rowMin > limit) return null
                for (m in prev.indices) prevPrev[m] = prev[m]
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[lb].takeIf { it <= limit }
        }
    }
}
