package com.aikeyboard.app.ime

/**
 * Geometria do teclado QWERTY para a autocorreção sensível à posição física das
 * teclas.
 *
 * A maior parte dos erros reais de digitação é "dedo na tecla vizinha": trocar
 * `p` por `o`, `r` por `e`, `s` por `d`. Saber quais teclas são adjacentes
 * permite cobrar barato por esse tipo de troca (provavelmente um typo) e caro
 * por trocas entre teclas distantes (provavelmente outra palavra) — o que torna
 * a correção de múltiplos erros confiável sem inventar palavras.
 *
 * Cada tecla recebe uma coordenada (x, y); as fileiras têm um deslocamento
 * (stagger) que aproxima o teclado físico. Duas teclas são adjacentes quando
 * estão a no máximo ~1 unidade em x e em y.
 */
object KeyboardAdjacency {

    private val rows = arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val rowOffset = floatArrayOf(0.0f, 0.5f, 1.0f)

    // Coordenadas indexadas por código do caractere (ASCII minúsculo). -1 = sem tecla.
    private val xs = FloatArray(128) { -1f }
    private val ys = FloatArray(128) { -1f }

    init {
        for (r in rows.indices) {
            val row = rows[r]
            for (c in row.indices) {
                val code = row[c].code
                xs[code] = c + rowOffset[r]
                ys[code] = r.toFloat()
            }
        }
    }

    /** True se [a] e [b] são teclas QWERTY fisicamente vizinhas (inclui diagonais). */
    fun areAdjacent(a: Char, b: Char): Boolean {
        if (a == b) return false
        val ca = a.code
        val cb = b.code
        if (ca !in 0..127 || cb !in 0..127) return false
        val ax = xs[ca]
        val bx = xs[cb]
        if (ax < 0f || bx < 0f) return false
        val dx = ax - bx
        val dy = ys[ca] - ys[cb]
        return dx in -1.05f..1.05f && dy in -1.05f..1.05f
    }
}
