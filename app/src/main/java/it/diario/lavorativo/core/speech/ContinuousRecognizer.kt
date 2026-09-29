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
    private val sounds = ListeningSounds(context)
    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var errorsInARow = 0
    private var emptyInARow = 0
    private var heardSomething = false
    private var preferOffline = true

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Riparte da [initialText]: serve quando si aggiunge a un racconto gia' fatto. */
    fun start(initialText: String = _state.value.text) {
        emptyInARow = 0
        errorsInARow = 0
        heardSomething = false
        preferOffline = true
        if (!isAvailable()) {
            _state.update {
                it.copy(error = "Su questo telefono il riconoscimento vocale non e' disponibile.")
            }
            return
        }
        wanted = true
        sounds.begin()
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
        sounds.end()
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.stopListening() }
        // La frase a meta' non va persa: si tiene quello che si e' sentito.
        _state.update { s ->
            s.copy(listening = false, text = join(s.text, s.partial), partial = "")
        }
    }

    fun setText(text: String) = _state.update { it.copy(text = text) }

    /** Aggiunge in fondo del testo arrivato da un'altra strada (il microfono di Google). */
    fun append(text: String) = _state.update { it.copy(text = join(it.text, text), error = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun release() {
        wanted = false
        sounds.end()
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
            // Prima si prova senza internet; se il telefono non ha
            // l'italiano offline, si passa da solo a quello con internet.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { restartLater(600, fresh = true) }
    }

    private fun restartLater(delayMs: Long, fresh: Boolean = false) {
        if (!wanted) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            // Su tanti telefoni il riconoscitore riusato si incastra:
            // meglio buttarlo e crearne uno nuovo.
            if (fresh) {
                runCatching { recognizer?.destroy() }
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(listener)
                }
            }
            listen()
        }, delayMs)
    }

    /**
     * Un giro finito senza aver capito niente. Dopo due giri a vuoto senza
     * internet si prova con internet; dopo altri giri a vuoto ci si ferma
     * e si propone il microfono di Google, invece di continuare a suonare.
     */
    private fun emptyRound() {
        emptyInARow++
        when {
            preferOffline && !heardSomething && emptyInARow >= 2 -> {
                preferOffline = false
                emptyInARow = 0
                restartLater(400, fresh = true)
            }
            emptyInARow >= MAX_EMPTY -> fail(
                if (heardSomething) {
                    "Mi sono fermato perche' non sentivo piu' niente. Ripremi il microfono per continuare."
                } else {
                    "Non riesco a sentirti con l'ascolto continuo. Usa il microfono di Google qui sotto."
                }
            )
            else -> restartLater(300, fresh = true)
        }
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
            errorsInARow = 0
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
            if (p.isNotBlank()) {
                heardSomething = true
                emptyInARow = 0
            }
            _state.update { it.copy(partial = p) }
        }

        override fun onResults(results: Bundle?) {
            val frase = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.update { it.copy(text = join(it.text, frase), partial = "") }
            if (frase.isBlank()) {
                emptyRound()
            } else {
                heardSomething = true
                emptyInARow = 0
                restartLater(250)
            }
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> emptyRound()

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    runCatching { recognizer?.cancel() }
                    errorsInARow++
                    if (errorsInARow >= 5) {
                        fail("L'ascolto continuo non funziona su questo telefono: usa il microfono di Google qui sotto.")
                    } else {
                        restartLater(700, fresh = true)
                    }
                }

                // Lingua offline non installata: si passa a quella con internet.
                12, 13 -> if (preferOffline) {
                    preferOffline = false
                    restartLater(400, fresh = true)
                } else {
                    fail("Il riconoscimento vocale in italiano non e' disponibile. Usa il microfono di Google qui sotto.")
                }

                // Di solito non manca al Diario ma al servizio vocale di
                // Google, che e' quello che ascolta davvero.
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(
                    "Il riconoscimento vocale non ha il permesso del microfono. " +
                        "Dai il permesso Microfono all'app Google (Impostazioni > App > " +
                        "Google > Autorizzazioni), oppure usa il microfono di Google qui sotto."
                )

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> fail(
                    "Il riconoscimento vocale chiede internet: scarica la lingua italiana " +
                        "per l'uso offline dalle impostazioni della tastiera o di Google."
                )

                else -> {
                    // Errori strani ripetuti: meglio fermarsi e proporre
                    // la riserva che girare a vuoto.
                    errorsInARow++
                    if (errorsInARow >= 5) {
                        fail("L'ascolto continuo non funziona su questo telefono: usa il microfono di Google qui sotto.")
                    } else {
                        restartLater(500, fresh = true)
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_EMPTY = 4
    }

    private fun fail(message: String) {
        wanted = false
        sounds.end()
        _state.update { s ->
            s.copy(listening = false, error = message, text = join(s.text, s.partial), partial = "")
        }
    }
}
