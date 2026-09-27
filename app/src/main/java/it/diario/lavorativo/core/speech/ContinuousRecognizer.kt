package it.diario.lavorativo.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Cosa sta succedendo all'ascolto, per lo schermo. */
data class ListeningState(
    val listening: Boolean = false,
    /** Frasi gia' riconosciute e definitive. */
    val text: String = "",
    /** La frase che si sta dicendo adesso, ancora provvisoria. */
    val partial: String = "",
    val error: String? = null
)

/**
 * Ascolto continuo con il riconoscimento vocale di Android.
 *
 * Il riconoscitore di sistema si ferma a ogni pausa del discorso: qui lo si
 * fa ripartire da solo finche' l'utente non preme "ho finito", cosi' si
 * puo' raccontare la giornata con calma, come a una persona.
 *
 * Si chiede di lavorare senza rete: funziona se sul telefono c'e' il
 * pacchetto della lingua italiana per il riconoscimento offline.
 * Va usato dal thread principale.
 */
class ContinuousRecognizer(private val context: Context) {

    private val _state = MutableStateFlow(ListeningState())
    val state: StateFlow<ListeningState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wanted = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Riparte da [initialText]: serve quando si aggiunge a un racconto gia' fatto. */
    fun start(initialText: String = _state.value.text) {
        if (!isAvailable()) {
            _state.update {
                it.copy(error = "Su questo telefono il riconoscimento vocale non e' disponibile.")
            }
            return
        }
        wanted = true
        _state.value = ListeningState(listening = true, text = initialText)
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
        }
        listen()
    }

    fun stop() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.stopListening() }
        // La frase a meta' non va persa: si tiene quello che si e' sentito.
        _state.update { s ->
            s.copy(listening = false, text = join(s.text, s.partial), partial = "")
        }
    }

    fun setText(text: String) = _state.update { it.copy(text = text) }

    fun release() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun listen() {
        if (!wanted) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Lasciar respirare chi parla: molti telefoni ignorano questi
            // valori, ma dove funzionano evitano tagli a meta' frase.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { restartLater(500) }
    }

    private fun restartLater(delayMs: Long) {
        if (!wanted) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMs)
    }

    private fun join(a: String, b: String): String {
        val x = a.trim()
        val y = b.trim()
        return when {
            y.isEmpty() -> x
            x.isEmpty() -> y
            else -> "$x $y"
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.update { it.copy(listening = true, error = null) }
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.update { it.copy(partial = p) }
        }

        override fun onResults(results: Bundle?) {
            val frase = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.update { it.copy(text = join(it.text, frase), partial = "") }
            restartLater(150)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartLater(150)

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    runCatching { recognizer?.cancel() }
                    restartLater(600)
                }

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(
                    "Serve il permesso del microfono."
                )

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> fail(
                    "Il riconoscimento vocale chiede internet: scarica la lingua italiana " +
                        "per l'uso offline dalle impostazioni della tastiera o di Google."
                )

                else -> restartLater(400)
            }
        }
    }

    private fun fail(message: String) {
        wanted = false
        _state.update { s ->
            s.copy(listening = false, error = message, text = join(s.text, s.partial), partial = "")
        }
    }
}
