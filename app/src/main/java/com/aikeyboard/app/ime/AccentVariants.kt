package com.aikeyboard.app.ime

/**
 * Variantes acentuadas mostradas em popup ao segurar (long-press) cada letra.
 * Cobertura: PT-BR + alguns acentos ES/FR comuns.
 *
 * A primeira posição da lista é a variante mais comum (será o "destino padrão"
 * caso o usuário deslize para baixo sem escolher).
 */
object AccentVariants {

    private val MAP: Map<Char, List<Char>> = mapOf(
        'a' to listOf('á', 'à', 'â', 'ã', 'ä', 'å', 'æ'),
        'e' to listOf('é', 'ê', 'è', 'ë', '€'),
        'i' to listOf('í', 'î', 'ï', 'ì'),
        'o' to listOf('ó', 'ô', 'õ', 'ò', 'ö', 'ø', 'œ'),
        'u' to listOf('ú', 'û', 'ü', 'ù'),
        'c' to listOf('ç', '©'),
        'n' to listOf('ñ'),
        'y' to listOf('ý', 'ÿ'),
        's' to listOf('ß', '$'),
        // Símbolos úteis acessados via long-press em chars não-letra (não temos acesso, mas deixar aqui)
    )

    /** Retorna a lista de variantes para [c] (case-insensitive), ou lista vazia. */
    fun variantsFor(c: Char): List<Char> {
        val lower = c.lowercaseChar()
        val variants = MAP[lower] ?: return emptyList()
        // Preserva o case original
        return if (c.isUpperCase()) variants.map { it.uppercaseChar() } else variants
    }

    /** True se [c] tem variantes (e portanto deve ter long-press handler). */
    fun hasVariants(c: Char): Boolean = MAP.containsKey(c.lowercaseChar())
}
