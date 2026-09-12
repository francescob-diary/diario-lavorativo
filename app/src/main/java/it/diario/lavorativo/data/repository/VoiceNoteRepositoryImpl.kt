package it.diario.lavorativo.data.repository

import it.diario.lavorativo.core.audio.VoiceStorage
import it.diario.lavorativo.data.local.dao.VoiceNoteDao
import it.diario.lavorativo.data.local.entity.VoiceNoteEntity
import it.diario.lavorativo.domain.model.VoiceNote
import it.diario.lavorativo.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class VoiceNoteRepositoryImpl(
    private val dao: VoiceNoteDao,
    private val storage: VoiceStorage
) : VoiceNoteRepository {

    override fun observeForDay(workDayId: Long): Flow<List<VoiceNote>> =
        dao.observeForDay(workDayId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: Long): VoiceNote? = dao.byId(id)?.toDomain()

    override suspend fun add(note: VoiceNote): Long = dao.insert(note.toEntity())

    override suspend fun updateTranscript(id: Long, text: String?) {
        val row = dao.byId(id) ?: return
        dao.update(row.copy(note = text?.takeIf { it.isNotBlank() }))
    }

    override suspend fun delete(note: VoiceNote) {
        dao.byId(note.id)?.let { dao.delete(it) }
        // Il file audio va via con la riga: tenerlo occuperebbe spazio per
        // niente, visto che non e' raggiungibile da nessun'altra parte.
        storage.delete(note.fileName)
    }

    override suspend fun count(): Int = dao.count()

    private fun VoiceNoteEntity.toDomain() = VoiceNote(
        id = id,
        workDayId = workDayId,
        fileName = fileName,
        durationSeconds = durationSeconds,
        note = note,
        recordedAt = Instant.ofEpochMilli(recordedAt),
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun VoiceNote.toEntity() = VoiceNoteEntity(
        id = id,
        workDayId = workDayId,
        fileName = fileName,
        durationSeconds = durationSeconds,
        note = note,
        recordedAt = recordedAt.toEpochMilli(),
        createdAt = createdAt.toEpochMilli()
    )
}
