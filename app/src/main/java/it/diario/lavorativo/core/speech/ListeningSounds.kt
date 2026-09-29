package it.diario.lavorativo.core.speech

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * I suoni mentre si detta.
 *
 * Il "tu-tu" che si sente a ogni ripartenza lo suona il servizio vocale di
 * Google e non si puo' cambiare. Si puo' pero' zittire: mentre il Diario
 * ascolta si abbassano a zero i canali dove lo suona, e al loro posto l'app
 * fa un "tic" leggero ogni tanto, per ricordare che il microfono e' acceso.
 *
 * I canali si rimettono come erano appena si smette di ascoltare. Se l'app
 * venisse chiusa di colpo a meta', si rimettono alla riapertura: quali
 * canali erano stati zittiti resta scritto nelle preferenze.
 */
class ListeningSounds(context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val prefs = context.getSharedPreferences("suoni_dettatura", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var active = false

    init {
        // Chiusura brusca l'ultima volta: si rimette a posto l'audio.
        restoreSaved()
    }

    fun begin() {
        if (active) return
        active = true
        val zittiti = STREAMS.filter { s ->
            val m = audio ?: return@filter false
            !m.isStreamMute(s) && runCatching {
                m.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
            }.isSuccess
        }
        prefs.edit().putString(KEY, zittiti.joinToString(",")).apply()
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME) }.getOrNull()
        handler.postDelayed(tick, TICK_MS)
    }

    fun end() {
        if (!active) return
        active = false
        handler.removeCallbacks(tick)
        runCatching { tone?.release() }
        tone = null
        restoreSaved()
    }

    private fun restoreSaved() {
        val salvati = prefs.getString(KEY, "").orEmpty()
            .split(',').mapNotNull { it.trim().toIntOrNull() }
        val m = audio
        if (m != null) {
            salvati.forEach { s -> runCatching { m.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } }
        }
        prefs.edit().remove(KEY).apply()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private companion object {
        const val KEY = "canali_zittiti"
        /** Un tic ogni venti secondi: presente ma non invadente. */
        const val TICK_MS = 20_000L
        /** Volume del tic, in percentuale del volume della sveglia. */
        const val TONE_VOLUME = 12
        val STREAMS = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        )
    }
}
