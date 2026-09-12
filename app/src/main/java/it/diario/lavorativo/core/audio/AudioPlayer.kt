package it.diario.lavorativo.core.audio

import android.media.MediaPlayer
import java.io.File

/**
 * Riproduttore delle note vocali.
 *
 * Uno solo per volta: se se ne avvia un'altra, la precedente si ferma.
 * Due voci sovrapposte non si capiscono, e in cantiere la nota si
 * riascolta proprio perche' non si era capito.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var playingFile: String? = null

    val currentFileName: String? get() = playingFile

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun play(file: File, fileName: String, onFinished: () -> Unit): Boolean {
        stop()
        if (!file.exists()) return false

        return try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                stop()
                onFinished()
            }
            mp.prepare()
            mp.start()

            player = mp
            playingFile = fileName
            true
        } catch (e: Exception) {
            // File rovinato o formato non riconosciuto: meglio non partire
            // che restare con il tasto premuto e nessun suono.
            stop()
            false
        }
    }

    fun stop() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        playingFile = null
    }

    /** Posizione e durata in millisecondi, per la barra di avanzamento. */
    fun progress(): Pair<Int, Int>? = runCatching {
        val mp = player ?: return null
        mp.currentPosition to mp.duration
    }.getOrNull()
}
