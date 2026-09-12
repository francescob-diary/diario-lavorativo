package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE workDayId = :workDayId ORDER BY takenAt ASC, id ASC")
    fun observeForDay(workDayId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE activityId = :activityId ORDER BY takenAt ASC")
    fun observeForActivity(activityId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE eventId = :eventId ORDER BY takenAt ASC")
    fun observeForEvent(eventId: Long): Flow<List<PhotoEntity>>

    /** Nomi di tutti i file registrati: serve a ripulire quelli orfani. */
    @Query("SELECT fileName FROM photos")
    suspend fun allFileNames(): List<String>

    @Query("SELECT COUNT(*) FROM photos WHERE workDayId = :workDayId")
    suspend fun countForDay(workDayId: Long): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM photos")
    suspend fun totalBytes(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PhotoEntity): Long

    @Update
    suspend fun update(entity: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE photos SET caption = :caption WHERE id = :id")
    suspend fun setCaption(id: Long, caption: String?)

    @Query("UPDATE photos SET activityId = :activityId, eventId = :eventId WHERE id = :id")
    suspend fun setLinks(id: Long, activityId: Long?, eventId: Long?)

    // ---- backup: lettura e riscrittura totali ----

    @Query("SELECT * FROM photos ORDER BY takenAt ASC")
    suspend fun allForBackup(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<PhotoEntity>)

    @Query("DELETE FROM photos")
    suspend fun deleteAllForRestore()
}
