package com.aikeyboard.app.ime

import com.aikeyboard.app.data.AutocorrectLevel
import com.aikeyboard.app.data.PersonalizationSnapshot
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocorrectMetricsTest {

    @Test
    fun autocorrectEvaluationCorpusReportsMetricsAndGuardsAssertRows() {
        val cases = loadCases()
        assertTrue("Corpus precisa ter pelo menos 100 casos", cases.size >= 100)
        assertTrue("Corpus precisa ter casos ASSERT", cases.any { it.mode == EvalMode.ASSERT })
        assertTrue("Corpus precisa ter casos OBSERVE", cases.any { it.mode == EvalMode.OBSERVE })
        assertTrue("IDs do corpus precisam ser únicos", cases.map { it.id }.toSet().size == cases.size)

        val engine = TypingEngine(PortugueseLexicon(loadLexiconWords()))
        val results = cases.map { row ->
            var actual: String?
            val start = System.nanoTime()
            actual = engine.boundaryCorrection(
                word = row.typed,
                userDictionary = emptySet(),
                personalization = PersonalizationSnapshot(),
                level = row.level
            )
            val elapsed = System.nanoTime() - start
            EvalResult(row, actual, elapsed)
        }

        println(metricsReport("ASSERT", results.filter { it.case.mode == EvalMode.ASSERT }))
        println(metricsReport("OBSERVE", results.filter { it.case.mode == EvalMode.OBSERVE }))
        println(metricsReport("ALL", results))

        val assertFailures = results
            .filter { it.case.mode == EvalMode.ASSERT && it.actual != it.case.expected }
            .joinToString("\n") { result ->
                "${result.case.id}: ${result.case.typed} expected=${result.case.expected.display()} " +
                    "actual=${result.actual.display()}"
            }

        assertTrue(
            "Divergências em casos ASSERT:\n$assertFailures",
            assertFailures.isEmpty()
        )
    }

    private fun loadCases(): List<EvalCase> {
        val stream = javaClass.classLoader?.getResourceAsStream("ime/autocorrect_eval_cases.tsv")
            ?: error("Recurso ime/autocorrect_eval_cases.tsv não encontrado")
        return stream.bufferedReader().useLines { lines ->
            lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split('\t')
                    require(parts.size == 6) { "Linha inválida no corpus: $line" }
                    EvalCase(
                        id = parts[0],
                        level = AutocorrectLevel.valueOf(parts[1]),
                        category = parts[2],
                        typed = parts[3],
                        expected = parts[4].takeUnless { it == NONE },
                        mode = EvalMode.valueOf(parts[5])
                    )
                }
                .toList()
        }
    }

    private fun loadLexiconWords(): List<String> {
        val candidates = listOf(
            File("src/main/res/raw/pt_br_words.txt"),
            File("app/src/main/res/raw/pt_br_words.txt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Não encontrei pt_br_words.txt em ${candidates.joinToString { it.path }}")
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun metricsReport(label: String, results: List<EvalResult>): String {
        val total = results.size
        val tp = results.count { it.expectedCorrection && it.actual == it.case.expected }
        val fn = results.count { it.expectedCorrection && it.actual != it.case.expected }
        val fp = results.count { !it.expectedCorrection && it.actual != null }
        val tn = results.count { !it.expectedCorrection && it.actual == null }
        val precision = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)
        val recall = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)
        val falseCorrectionRate = if (fp + tn == 0) 0.0 else fp.toDouble() / (fp + tn)
        val latenciesMs = results.map { it.elapsedNanos / 1_000_000.0 }.sorted()
        return "AutocorrectMetrics[$label] " +
            "total=$total TP=$tp FP=$fp FN=$fn TN=$tn " +
            "precision=${precision.format()} recall=${recall.format()} " +
            "falseCorrectionRate=${falseCorrectionRate.format()} " +
            "P50=${percentile(latenciesMs, 0.50).format()}ms " +
            "P95=${percentile(latenciesMs, 0.95).format()}ms " +
            "P99=${percentile(latenciesMs, 0.99).format()}ms"
    }

    private fun percentile(values: List<Double>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        val index = (ceil(values.size * percentile).toInt() - 1).coerceIn(0, values.lastIndex)
        return values[index]
    }

    private fun Double.format(): String = String.format(Locale.US, "%.4f", this)

    private fun String?.display(): String = this ?: NONE

    private val EvalResult.expectedCorrection: Boolean
        get() = case.expected != null

    private data class EvalCase(
        val id: String,
        val level: AutocorrectLevel,
        @Suppress("unused")
        val category: String,
        val typed: String,
        val expected: String?,
        val mode: EvalMode
    )

    private data class EvalResult(
        val case: EvalCase,
        val actual: String?,
        val elapsedNanos: Long
    )

    private enum class EvalMode {
        ASSERT,
        OBSERVE
    }

    private companion object {
        const val NONE = "<NONE>"
    }
}
