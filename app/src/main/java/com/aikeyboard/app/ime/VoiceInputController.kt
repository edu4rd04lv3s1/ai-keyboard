package com.aikeyboard.app.ime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aikeyboard.app.R

/**
 * Ditado por voz ao vivo usando o [SpeechRecognizer] nativo do Android (o mesmo
 * motor do "voice typing" do Gboard).
 *
 *  - PT-BR, com resultados PARCIAIS (texto aparece enquanto você fala);
 *  - pontuação/capitalização automáticas no Android 13+ (EXTRA_ENABLE_FORMATTING);
 *  - ditado CONTÍNUO: ao terminar uma frase, reinicia sozinho até o usuário
 *    parar, então dá pra falar vários trechos seguidos.
 *
 * Deve ser criado e usado na main thread (o SpeechRecognizer exige um Looper).
 * Os callbacks chegam na main thread.
 */
class VoiceInputController(
    private val context: Context,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        /** Texto provisório enquanto o usuário fala (substitui o anterior). */
        fun onPartial(text: String)
        /** Frase finalizada e pronta pra confirmar. */
        fun onFinal(text: String)
        /** Mudou o estado de escuta (liga/desliga o indicador no teclado). */
        fun onListeningChanged(listening: Boolean)
        /** Erro relevante para mostrar ao usuário. */
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /** Mantém reiniciando a escuta após cada frase até o usuário parar. */
    private var continuous = false

    @Volatile
    var isListening = false
        private set

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (isListening) return
        if (!isAvailable()) {
            callbacks.onError(context.getString(R.string.voice_unavailable))
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
        }
        continuous = true
        isListening = true
        callbacks.onListeningChanged(true)
        startOneUtterance()
    }

    /** Para após finalizar a frase atual (confirma o que foi dito). */
    fun stop() {
        if (!isListening) return
        continuous = false
        runCatching { recognizer?.stopListening() }
    }

    /** Interrompe imediatamente (usado quando o usuário toca numa tecla). */
    fun cancelNow() {
        continuous = false
        runCatching { recognizer?.cancel() }
        if (isListening) {
            isListening = false
            callbacks.onListeningChanged(false)
        }
    }

    fun destroy() {
        continuous = false
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun startOneUtterance() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Pontuação e capitalização automáticas (Android 13+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
                )
            }
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure {
                continuous = false
                isListening = false
                callbacks.onListeningChanged(false)
                callbacks.onError(context.getString(R.string.voice_error_generic))
            }
    }

    private fun scheduleRestart() {
        // Pequeno atraso antes de reiniciar evita ERROR_RECOGNIZER_BUSY em
        // alguns aparelhos ao reutilizar o recognizer logo após um resultado.
        mainHandler.postDelayed({ if (continuous) startOneUtterance() }, RESTART_DELAY_MS)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(callbacks::onPartial)
        }

        override fun onResults(results: Bundle?) {
            firstResult(results)?.let(callbacks::onFinal)
            if (continuous) {
                scheduleRestart()
            } else {
                isListening = false
                callbacks.onListeningChanged(false)
            }
        }

        override fun onError(error: Int) {
            // Em ditado contínuo, silêncio/sem-fala são transitórios: reinicia.
            if (continuous &&
                (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                scheduleRestart()
                return
            }
            val wasListening = isListening
            continuous = false
            isListening = false
            if (wasListening) callbacks.onListeningChanged(false)
            // ERROR_CLIENT normalmente vem de cancel()/stop() proposital.
            if (error != SpeechRecognizer.ERROR_CLIENT) {
                callbacks.onError(messageFor(error))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun messageFor(error: Int): String {
        val res = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_permission
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_error_network
            else -> R.string.voice_error_generic
        }
        return context.getString(res)
    }

    companion object {
        private const val RESTART_DELAY_MS = 120L

        /**
         * Sinalizado pela [VoicePermissionActivity] quando a permissão é
         * concedida, para o IME iniciar o ditado assim que o foco voltar.
         */
        @Volatile
        var autoStartPending = false
    }
}
