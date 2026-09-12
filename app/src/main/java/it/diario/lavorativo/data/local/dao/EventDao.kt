package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events WHERE workDayId = :workDayId ORDER BY time ASC, id ASC")
    fun observeForDay(workDayId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE workDayId = :workDayId ORDER BY time ASC, id ASC")
    suspend fun getForDay(workDayId: Long): List<EventEntity>

    /** Questioni rimaste in sospeso, da riprendere. */
    @Query(
        """
        SELECT * FROM events WHERE unresolved = 1
        ORDER BY time DESC LIMIT :limit
        """
    )
    fun observeUnresolved(limit: Int = 50): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventEntity): Long

    @Update
    suspend fun update(entity: EventEntity)

    @Delete
    suspend fun delete(entity: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE events SET unresolved = :unresolved WHERE id = :id")
    suspend fun setUnresolved(id: Long, unresolved: Boolean)

    // ---- backup: lettura e riscrittura totali ----

    @Query("SELECT * FROM events ORDER BY time ASC")
    suspend fun allForBackup(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<EventEntity>)

    @Query("DELETE FROM events")
    suspend fun deleteAllForRestore()
}
