package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.MaintenanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {

    @Query("SELECT * FROM maintenances WHERE vehicleId = :vehicleId ORDER BY date DESC, id DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<MaintenanceEntity>>

    /** Tutti gli interventi del mezzo: servono interi per calcolare le scadenze. */
    @Query("SELECT * FROM maintenances WHERE vehicleId = :vehicleId ORDER BY date ASC, id ASC")
    suspend fun allForVehicle(vehicleId: Long): List<MaintenanceEntity>

    @Query(
        "SELECT * FROM maintenances WHERE vehicleId = :vehicleId " +
            "AND date BETWEEN :from AND :to ORDER BY date ASC, id ASC"
    )
    suspend fun betweenForVehicle(vehicleId: Long, from: Long, to: Long): List<MaintenanceEntity>

    @Query("SELECT * FROM maintenances WHERE date BETWEEN :from AND :to ORDER BY date ASC, id ASC")
    suspend fun between(from: Long, to: Long): List<MaintenanceEntity>

    @Query("SELECT * FROM maintenances WHERE id = :id")
    suspend fun byId(id: Long): MaintenanceEntity?

    @Query("SELECT COUNT(*) FROM maintenances")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: MaintenanceEntity): Long

    @Update
    suspend fun update(row: MaintenanceEntity)

    @Delete
    suspend fun delete(row: MaintenanceEntity)

    @Query("SELECT * FROM maintenances ORDER BY id ASC")
    suspend fun allForBackup(): List<MaintenanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<MaintenanceEntity>)

    @Query("DELETE FROM maintenances")
    suspend fun deleteAllForRestore()
}
