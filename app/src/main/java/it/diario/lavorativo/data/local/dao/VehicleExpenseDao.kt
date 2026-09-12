package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.VehicleExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleExpenseDao {

    @Query(
        "SELECT * FROM vehicle_expenses WHERE vehicleId = :vehicleId " +
            "ORDER BY date DESC, id DESC"
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<VehicleExpenseEntity>>

    @Query(
        "SELECT * FROM vehicle_expenses WHERE vehicleId = :vehicleId " +
            "AND date BETWEEN :from AND :to ORDER BY date ASC, id ASC"
    )
    suspend fun betweenForVehicle(
        vehicleId: Long,
        from: Long,
        to: Long
    ): List<VehicleExpenseEntity>

    @Query(
        "SELECT * FROM vehicle_expenses WHERE date BETWEEN :from AND :to " +
            "ORDER BY date ASC, id ASC"
    )
    suspend fun between(from: Long, to: Long): List<VehicleExpenseEntity>

    @Query("SELECT * FROM vehicle_expenses WHERE workDayId = :workDayId ORDER BY id ASC")
    suspend fun forWorkDay(workDayId: Long): List<VehicleExpenseEntity>

    @Query("SELECT * FROM vehicle_expenses WHERE id = :id")
    suspend fun byId(id: Long): VehicleExpenseEntity?

    @Query("SELECT COUNT(*) FROM vehicle_expenses")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: VehicleExpenseEntity): Long

    @Update
    suspend fun update(row: VehicleExpenseEntity)

    @Delete
    suspend fun delete(row: VehicleExpenseEntity)

    @Query("SELECT * FROM vehicle_expenses ORDER BY id ASC")
    suspend fun allForBackup(): List<VehicleExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<VehicleExpenseEntity>)

    @Query("DELETE FROM vehicle_expenses")
    suspend fun deleteAllForRestore()
}
