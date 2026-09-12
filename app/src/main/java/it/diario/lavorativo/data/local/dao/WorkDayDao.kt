package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.WorkDayEntity
import it.diario.lavorativo.data.local.relation.WorkDayWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDayDao {

    @Transaction
    @Query("SELECT * FROM work_days WHERE date = :epochDay LIMIT 1")
    fun observeByDate(epochDay: Long): Flow<WorkDayWithDetails?>

    @Transaction
    @Query("SELECT * FROM work_days WHERE startTime IS NOT NULL AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeOpenDay(): Flow<WorkDayWithDetails?>

    @Transaction
    @Query("SELECT * FROM work_days ORDER BY date DESC")
    fun observeAll(): Flow<List<WorkDayWithDetails>>

    /** Giornate di un intervallo di date, estremi inclusi. Usata da storico e calendario. */
    @Transaction
    @Query("SELECT * FROM work_days WHERE date BETWEEN :fromEpochDay AND :toEpochDay ORDER BY date DESC")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<WorkDayWithDetails>>

    /** Data della giornata piu' vecchia registrata: serve a limitare la navigazione del calendario. */
    @Query("SELECT MIN(date) FROM work_days")
    fun observeFirstDate(): Flow<Long?>

    @Transaction
    @Query("SELECT * FROM work_days WHERE id = :id")
    suspend fun getById(id: Long): WorkDayWithDetails?

    @Query("SELECT * FROM work_days WHERE date = :epochDay LIMIT 1")
    suspend fun getByDate(epochDay: Long): WorkDayEntity?

    @Insert
    suspend fun insert(entity: WorkDayEntity): Long

    @Update
    suspend fun update(entity: WorkDayEntity)

    @Query("DELETE FROM work_days WHERE id = :id")
    suspend fun delete(id: Long)

    // ---- backup: lettura e riscrittura totali ----

    @Query("SELECT * FROM work_days ORDER BY date ASC")
    suspend fun allForBackup(): List<WorkDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<WorkDayEntity>)

    @Query("DELETE FROM work_days")
    suspend fun deleteAllForRestore()
}
