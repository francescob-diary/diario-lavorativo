package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY active DESC, name ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun byId(id: Long): VehicleEntity?

    /** Il mezzo in uso adesso: il primo attivo, se ce n'e' uno. */
    @Query("SELECT * FROM vehicles WHERE active = 1 ORDER BY id ASC LIMIT 1")
    suspend fun firstActive(): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: VehicleEntity): Long

    @Update
    suspend fun update(row: VehicleEntity)

    @Delete
    suspend fun delete(row: VehicleEntity)

    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    suspend fun allForBackup(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<VehicleEntity>)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllForRestore()
}
