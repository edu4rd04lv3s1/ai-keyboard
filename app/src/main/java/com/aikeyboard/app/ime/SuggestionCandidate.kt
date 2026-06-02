package com.aikeyboard.app.ime

/**
 * Item exibido na barra de sugestões do teclado.
 *
 * O tipo evita que a IME trate uma palavra, uma correção, um emoji e uma frase
 * pronta como se fossem sempre a mesma operação de substituição.
 */
data class SuggestionCandidate(
    val value: String,
    val type: SuggestionType = SuggestionType.WORD
)

enum class SuggestionType {
    WORD,
    CORRECTION,
    EMOJI,
    QUICK_PHRASE,
    CLIPBOARD
}
