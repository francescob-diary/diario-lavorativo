package it.diario.lavorativo.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Voce dell'app: legge ad alta voce le domande sui dati mancanti, cosi'
 * si puo' rispondere senza guardare lo schermo.
 */
class Speaker(context: Context) {

    private var ready = false
    private var pending: Pair<String, () -> Unit>? = null
    private var onDone: (() -> Unit)? = null

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.ITALIAN
                pending?.let { (text, done) -> speak(text, done) }
            } else {
                pending?.second?.invoke()
            }
            pending = null
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                onDone?.let { callback ->
                    onDone = null
                    android.os.Handler(android.os.Looper.getMainLooper()).post { callback() }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }
        })
    }

    /** Legge [text] e chiama [done] quando ha finito (o se non puo' parlare). */
    fun speak(text: String, done: () -> Unit = {}) {
        if (!ready) {
            pending = text to done
            return
        }
        onDone = done
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "domanda")
    }

    fun stop() = runCatching { tts.stop() }

    fun release() = runCatching { tts.shutdown() }
}
