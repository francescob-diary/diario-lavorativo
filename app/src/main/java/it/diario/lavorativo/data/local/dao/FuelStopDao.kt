package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.FuelStopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelStopDao {

    @Query("SELECT * FROM fuel_stops WHERE vehicleId = :vehicleId ORDER BY date DESC, id DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<FuelStopEntity>>

    @Query(
        "SELECT * FROM fuel_stops WHERE vehicleId = :vehicleId " +
            "AND date BETWEEN :from AND :to ORDER BY date ASC, id ASC"
    )
    suspend fun betweenForVehicle(vehicleId: Long, from: Long, to: Long): List<FuelStopEntity>

    @Query("SELECT * FROM fuel_stops WHERE date BETWEEN :from AND :to ORDER BY date ASC, id ASC")
    suspend fun between(from: Long, to: Long): List<FuelStopEntity>

    @Query("SELECT * FROM fuel_stops WHERE id = :id")
    suspend fun byId(id: Long): FuelStopEntity?

    @Query("SELECT COUNT(*) FROM fuel_stops")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: FuelStopEntity): Long

    @Update
    suspend fun update(row: FuelStopEntity)

    @Delete
    suspend fun delete(row: FuelStopEntity)

    @Query("SELECT * FROM fuel_stops ORDER BY id ASC")
    suspend fun allForBackup(): List<FuelStopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<FuelStopEntity>)

    @Query("DELETE FROM fuel_stops")
    suspend fun deleteAllForRestore()
}
