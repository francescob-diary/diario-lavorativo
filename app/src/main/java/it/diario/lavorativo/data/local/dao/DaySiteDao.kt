package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.DaySiteEntity

@Dao
interface DaySiteDao {

    @Insert
    suspend fun insert(entity: DaySiteEntity): Long

    @Update
    suspend fun update(entity: DaySiteEntity)

    @Query("SELECT * FROM day_sites WHERE id = :id")
    suspend fun getById(id: Long): DaySiteEntity?

    @Query("SELECT * FROM day_sites WHERE workDayId = :workDayId ORDER BY sortOrder ASC, id ASC")
    suspend fun forDay(workDayId: Long): List<DaySiteEntity>

    @Query("DELETE FROM day_sites WHERE id = :id")
    suspend fun delete(id: Long)

    // ---- backup ----

    @Query("SELECT * FROM day_sites ORDER BY id ASC")
    suspend fun allForBackup(): List<DaySiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<DaySiteEntity>)

    @Query("DELETE FROM day_sites")
    suspend fun deleteAllForRestore()
}
