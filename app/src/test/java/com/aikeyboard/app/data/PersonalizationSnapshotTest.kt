package com.aikeyboard.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Garante o contrato que o pipeline de sugestões depende: o snapshot lê
 * "através" das coleções vivas (sem cópia por keystroke) e a iteração é
 * segura mesmo com escrita concorrente — exatamente o cenário que antes
 * lançava ConcurrentModificationException e congelava o teclado.
 */
class PersonalizationSnapshotTest {

    @Test
    fun snapshotReadsThroughLiveFrequencies() {
        val freq = ConcurrentHashMap<String, Int>()
        val snapshot = PersonalizationSnapshot(frequencies = freq)

        freq["projeto"] = 5
        assertEquals(5, snapshot.frequencyOf("projeto"))
        assertEquals(0, snapshot.frequencyOf("ausente"))
    }

    @Test
    fun snapshotIterationIsSafeDuringConcurrentWrites() {
        val freq = ConcurrentHashMap<String, Int>()
        repeat(200) { freq["w$it"] = it }
        val snapshot = PersonalizationSnapshot(frequencies = freq)

        val writer = Thread {
            repeat(5000) { freq["x$it"] = it }
        }
        writer.start()
        // Iterar enquanto a outra thread escreve não pode lançar exceção.
        var seen = 0
        repeat(100) {
            for (entry in snapshot.frequencies) seen += entry.value.coerceAtMost(1)
        }
        writer.join()

        assertTrue(seen >= 0)
    }
}
