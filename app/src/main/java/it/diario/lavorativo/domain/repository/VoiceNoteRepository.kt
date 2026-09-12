package it.diario.lavorativo.domain.repository

import it.diario.lavorativo.domain.model.VoiceNote
import kotlinx.coroutines.flow.Flow

interface VoiceNoteRepository {

    fun observeForDay(workDayId: Long): Flow<List<VoiceNote>>

    suspend fun byId(id: Long): VoiceNote?

    suspend fun add(note: VoiceNote): Long

    suspend fun updateTranscript(id: Long, text: String?)

    /** Cancella la riga e anche il file audio: senza uno l'altro non serve. */
    suspend fun delete(note: VoiceNote)

    suspend fun count(): Int
}
