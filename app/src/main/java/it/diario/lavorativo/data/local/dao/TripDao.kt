package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.TripEntity

@Dao
interface TripDao {

    @Insert
    suspend fun insert(entity: TripEntity): Long

    @Update
    suspend fun update(entity: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long)

    // ---- backup ----

    @Query("SELECT * FROM trips ORDER BY id ASC")
    suspend fun allForBackup(): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<TripEntity>)

    @Query("DELETE FROM trips")
    suspend fun deleteAllForRestore()
}
