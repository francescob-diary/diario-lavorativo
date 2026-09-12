package it.diario.lavorativo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import it.diario.lavorativo.data.local.entity.CommunicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunicationDao {

    @Query("SELECT * FROM communications WHERE workDayId = :workDayId ORDER BY time ASC, id ASC")
    fun observeForDay(workDayId: Long): Flow<List<CommunicationEntity>>

    @Query("SELECT * FROM communications WHERE workDayId = :workDayId ORDER BY time ASC, id ASC")
    suspend fun getForDay(workDayId: Long): List<CommunicationEntity>

    /**
     * Ricerca nel contenuto: serve proprio a ritrovare la frase esatta a
     * distanza di mesi. LIKE senza indice full text, ma su qualche migliaio
     * di righe e' istantaneo e non aggiunge dipendenze.
     */
    @Query(
        """
        SELECT * FROM communications
        WHERE content LIKE '%' || :text || '%'
           OR subject LIKE '%' || :text || '%'
           OR contactName LIKE '%' || :text || '%'
        ORDER BY time DESC LIMIT :limit
        """
    )
    suspend fun search(text: String, limit: Int = 100): List<CommunicationEntity>

    /** Nomi gia' usati, per il completamento del contatto. */
    @Query(
        """
        SELECT contactName FROM communications
        GROUP BY contactName ORDER BY COUNT(*) DESC LIMIT :limit
        """
    )
    suspend fun frequentContacts(limit: Int = 10): List<String>

    /** Comunicazioni da mettere per iscritto o da richiamare. */
    @Query("SELECT * FROM communications WHERE requiresFollowUp = 1 ORDER BY time DESC")
    fun observePendingFollowUp(): Flow<List<CommunicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommunicationEntity): Long

    @Update
    suspend fun update(entity: CommunicationEntity)

    @Delete
    suspend fun delete(entity: CommunicationEntity)

    @Query("DELETE FROM communications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE communications SET requiresFollowUp = :pending WHERE id = :id")
    suspend fun setFollowUp(id: Long, pending: Boolean)

    // ---- backup: lettura e riscrittura totali ----

    @Query("SELECT * FROM communications ORDER BY time ASC")
    suspend fun allForBackup(): List<CommunicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(rows: List<CommunicationEntity>)

    @Query("DELETE FROM communications")
    suspend fun deleteAllForRestore()
}
