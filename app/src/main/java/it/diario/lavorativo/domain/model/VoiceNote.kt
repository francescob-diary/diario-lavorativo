package it.diario.lavorativo.domain.model

import java.time.Instant

/**
 * Nota vocale registrata in cantiere.
 *
 * Con le mani sporche o i guanti addosso non si scrive: si detta. La
 * trascrizione si aggiunge dopo, con calma, e resta a fianco dell'audio
 * invece di sostituirlo, cosi' si puo' sempre riascoltare com'era stato
 * detto.
 */
data class VoiceNote(
    val id: Long = 0L,
    val workDayId: Long,
    val fileName: String,
    val durationSeconds: Int,
    val note: String? = null,
    val recordedAt: Instant,
    val createdAt: Instant = Instant.now()
) {
    val hasTranscript: Boolean get() = !note.isNullOrBlank()

    /** Durata leggibile: "0:42", "1:05". */
    val durationLabel: String
        get() {
            val minuti = durationSeconds / 60
            val secondi = durationSeconds % 60
            return minuti.toString() + ":" + secondi.toString().padStart(2, '0')
        }
}
