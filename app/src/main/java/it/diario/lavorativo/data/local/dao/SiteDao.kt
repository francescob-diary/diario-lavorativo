package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.SiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {

    @Query("SELECT * FROM sites WHERE status = 'ATTIVO' ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites ORDER BY status ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE id = :id")
    fun observeById(id: Long): Flow<SiteEntity?>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getById(id: Long): SiteEntity?

    /** Solo i cantieri con coordinate: sono gli unici utili al riconoscimento GPS. */
    @Query("SELECT * FROM sites WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    suspend fun getGeolocated(): List<SiteEntity>

    /** Quante giornate fanno riferimento a questo cantiere: serve prima di cancellare. */
    @Query("SELECT COUNT(*) FROM work_days WHERE siteId = :id")
    suspend fun countWorkDays(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SiteEntity): Long

    @Update
    suspend fun update(entity: SiteEntity)

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun delete(id: Long)

    // ---- backup: lettura e riscrittura totali ----

    @Query("SELECT * FROM sites ORDER BY name ASC")
    suspend fun allForBackup(): List<SiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<SiteEntity>)

    @Query("DELETE FROM sites")
    suspend fun deleteAllForRestore()
}
