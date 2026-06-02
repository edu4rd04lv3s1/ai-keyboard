package com.aikeyboard.app.ime

import android.text.InputType

/**
 * Comportamento do teclado derivado do tipo do campo de entrada
 * (`EditorInfo.inputType`). É o que impede correções e maiúsculas indevidas em
 * campos onde elas só atrapalham: senha, e-mail, URL, filtros e campos numéricos.
 *
 * Função pura — as constantes de [InputType] são `static final int` (inlinadas em
 * tempo de compilação), então isto é totalmente coberto por unit test sem Android.
 */
data class FieldPolicy(
    /** Aplicar autocorreção automática ao fechar a palavra. */
    val autocorrect: Boolean,
    /** Mostrar a barra de sugestões/correções. */
    val suggestions: Boolean,
    /** Auto-capitalizar o início de frase. */
    val autoCap: Boolean
) {
    companion object {
        val DEFAULT = FieldPolicy(autocorrect = true, suggestions = true, autoCap = true)
        val LOCKED = FieldPolicy(autocorrect = false, suggestions = false, autoCap = false)

        fun forInputType(inputType: Int): FieldPolicy {
            val klass = inputType and InputType.TYPE_MASK_CLASS

            // Campos sem texto natural (número, telefone, data/hora) e TYPE_NULL
            // (o campo gerencia o próprio input, ex.: emuladores de terminal):
            // nada de autocorreção, sugestão ou maiúscula.
            when (klass) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME,
                0 /* TYPE_NULL */ -> return LOCKED
            }

            if (klass == InputType.TYPE_CLASS_TEXT) {
                val variation = inputType and InputType.TYPE_MASK_VARIATION
                val sensitive = when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_URI,
                    InputType.TYPE_TEXT_VARIATION_FILTER -> true
                    else -> false
                }
                if (sensitive) return LOCKED

                // Campo pediu explicitamente para não sugerir (ex.: alguns campos
                // de busca/código): respeita, mas mantém a auto-maiúscula.
                val noSuggestions =
                    (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
                return FieldPolicy(
                    autocorrect = !noSuggestions,
                    suggestions = !noSuggestions,
                    autoCap = true
                )
            }

            return DEFAULT
        }
    }
}
