package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    /** Prima le attivita' con orario, poi quelle senza, in ordine di inserimento. */
    @Query(
        """
        SELECT * FROM activities WHERE workDayId = :workDayId
        ORDER BY (startTime IS NULL), startTime ASC, id ASC
        """
    )
    fun observeForDay(workDayId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE workDayId = :workDayId ORDER BY (startTime IS NULL), startTime ASC, id ASC")
    suspend fun getForDay(workDayId: Long): List<ActivityEntity>

    /** Descrizioni gia' usate, per proporle come suggerimento e non riscriverle ogni volta. */
    @Query(
        """
        SELECT description FROM activities
        WHERE category = :category
        GROUP BY description ORDER BY COUNT(*) DESC LIMIT :limit
        """
    )
    suspend fun frequentDescriptions(category: String, limit: Int = 8): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityEntity): Long

    @Update
    suspend fun update(entity: ActivityEntity)

    @Delete
    suspend fun delete(entity: ActivityEntity)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- backup: lettura e riscrittura totali ----

    @Query("SELECT * FROM activities ORDER BY createdAt ASC")
    suspend fun allForBackup(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<ActivityEntity>)

    @Query("DELETE FROM activities")
    suspend fun deleteAllForRestore()
}
