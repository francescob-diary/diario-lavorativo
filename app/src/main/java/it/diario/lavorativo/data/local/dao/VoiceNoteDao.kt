package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {

    @Query("SELECT * FROM voice_notes WHERE workDayId = :workDayId ORDER BY recordedAt ASC")
    fun observeForDay(workDayId: Long): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun byId(id: Long): VoiceNoteEntity?

    @Query("SELECT * FROM voice_notes ORDER BY recordedAt ASC")
    suspend fun all(): List<VoiceNoteEntity>

    @Query("SELECT COUNT(*) FROM voice_notes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: VoiceNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<VoiceNoteEntity>)

    @Update
    suspend fun update(note: VoiceNoteEntity)

    @Delete
    suspend fun delete(note: VoiceNoteEntity)

    @Query("DELETE FROM voice_notes")
    suspend fun deleteAll()
}
