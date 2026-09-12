package it.diario.lavorativo.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Registratore delle note vocali.
 *
 * Formato AAC dentro un contenitore m4a: lo legge qualunque telefono e
 * qualunque computer, e occupa poco. Un minuto sta sotto i 200 kB, quindi
 * anche cento note non pesano quanto una foto.
 *
 * Un solo canale a 22 kHz: e' una voce a mezzo metro in mezzo al rumore
 * del cantiere, la qualita' da studio non serve e raddoppierebbe lo spazio.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean get() = recorder != null

    /** Avvia. Restituisce il file su cui sta scrivendo, o null se non parte. */
    fun start(target: File): File? {
        if (isRecording) return currentFile

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioChannels(1)
            mr.setAudioSamplingRate(22050)
            mr.setAudioEncodingBitRate(32000)
            mr.setOutputFile(target.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            currentFile = target
            startedAt = System.currentTimeMillis()
            target
        } catch (e: Exception) {
            // Microfono occupato da una chiamata, permesso negato, disco
            // pieno: in tutti i casi si lascia pulito e si dice di no.
            runCatching { mr.release() }
            target.delete()
            recorder = null
            currentFile = null
            null
        }
    }

    /**
     * Ferma e restituisce durata in secondi e file.
     * Null se la registrazione e' stata cosi' breve da non contenere nulla.
     */
    fun stop(): Recording? {
        val mr = recorder ?: return null
        val file = currentFile

        val fermato = try {
            mr.stop()
            true
        } catch (e: Exception) {
            // stop() lancia se non e' stato registrato niente: succede se si
            // preme e si rilascia subito. Il file resta vuoto e va buttato.
            false
        }

        runCatching { mr.release() }
        recorder = null
        currentFile = null

        if (!fermato || file == null || !file.exists() || file.length() < MIN_BYTES) {
            file?.delete()
            return null
        }

        val secondi = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        return Recording(file, secondi)
    }

    /** Annulla e butta via il file: serve al tasto di rinuncia. */
    fun cancel() {
        val file = currentFile
        recorder?.let { mr ->
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        recorder = null
        currentFile = null
        file?.delete()
    }

    /** Secondi trascorsi, per il contatore che gira mentre si registra. */
    fun elapsedSeconds(): Int =
        if (!isRecording) 0
        else ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    data class Recording(val file: File, val durationSeconds: Int)

    private companion object {
        /** Sotto questa soglia il file e' solo l'intestazione del contenitore. */
        const val MIN_BYTES = 1024L
    }
}
