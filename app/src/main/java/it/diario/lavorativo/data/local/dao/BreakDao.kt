package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.BreakEntity

@Dao
interface BreakDao {

    @Insert
    suspend fun insert(entity: BreakEntity): Long

    @Update
    suspend fun update(entity: BreakEntity)

    @Query("SELECT * FROM breaks WHERE id = :id")
    suspend fun getById(id: Long): BreakEntity?

    /** Pausa ancora aperta di una giornata, se esiste. */
    @Query("SELECT * FROM breaks WHERE workDayId = :workDayId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getOpenBreak(workDayId: Long): BreakEntity?

    @Query("DELETE FROM breaks WHERE id = :id")
    suspend fun delete(id: Long)

    // ---- backup: lettura e riscrittura totali ----

    @Query("SELECT * FROM breaks ORDER BY startTime ASC")
    suspend fun allForBackup(): List<BreakEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<BreakEntity>)

    @Query("DELETE FROM breaks")
    suspend fun deleteAllForRestore()
}
