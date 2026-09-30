package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.LoanEntity
import it.diario.lavorativo.data.local.entity.ToolEntity
import kotlinx.coroutines.flow.Flow

/** Attrezzi del deposito e cose prestate. */
@Dao
interface StuffDao {

    @Query("SELECT * FROM tools ORDER BY name COLLATE NOCASE ASC")
    fun observeTools(): Flow<List<ToolEntity>>

    @Insert
    suspend fun insertTool(entity: ToolEntity): Long

    @Update
    suspend fun updateTool(entity: ToolEntity)

    @Query("DELETE FROM tools WHERE id = :id")
    suspend fun deleteTool(id: Long)

    @Query("SELECT * FROM loans ORDER BY returnedDate IS NOT NULL, loanDate DESC, id DESC")
    fun observeLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE returnedDate IS NULL ORDER BY loanDate ASC")
    suspend fun openLoans(): List<LoanEntity>

    @Insert
    suspend fun insertLoan(entity: LoanEntity): Long

    @Update
    suspend fun updateLoan(entity: LoanEntity)

    @Query("UPDATE loans SET returnedDate = :epochDay WHERE id = :id")
    suspend fun setReturned(id: Long, epochDay: Long?)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)

    // ---- backup ----

    @Query("SELECT * FROM tools ORDER BY id")
    suspend fun allToolsForBackup(): List<ToolEntity>

    @Query("SELECT * FROM loans ORDER BY id")
    suspend fun allLoansForBackup(): List<LoanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolsForRestore(rows: List<ToolEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoansForRestore(rows: List<LoanEntity>)

    @Query("DELETE FROM tools")
    suspend fun deleteAllTools()

    @Query("DELETE FROM loans")
    suspend fun deleteAllLoans()
}
