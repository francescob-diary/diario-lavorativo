package it.diario.lavorativo.domain.repository

import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.Photo
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import kotlinx.coroutines.flow.Flow

/**
 * Voci del diario agganciate a una giornata: lavorazioni, eventi e comunicazioni.
 *
 * Stanno in un unico repository perche' sono tre elenchi con lo stesso ciclo di
 * vita e le stesse schermate. Separarli avrebbe triplicato il codice senza
 * dare nulla in cambio.
 */
interface DiaryEntryRepository {

    // --- attivita' ---
    fun observeActivities(workDayId: Long): Flow<List<WorkActivity>>
    suspend fun saveActivity(activity: WorkActivity): Long
    suspend fun deleteActivity(id: Long)
    /** Descrizioni gia' usate per quella categoria, da riproporre. */
    suspend fun frequentDescriptions(category: ActivityCategory): List<String>

    // --- eventi ---
    fun observeEvents(workDayId: Long): Flow<List<WorkEvent>>
    fun observeUnresolvedEvents(): Flow<List<WorkEvent>>
    suspend fun saveEvent(event: WorkEvent): Long
    suspend fun deleteEvent(id: Long)
    suspend fun setEventUnresolved(id: Long, unresolved: Boolean)

    // --- comunicazioni ---
    fun observeCommunications(workDayId: Long): Flow<List<Communication>>
    fun observePendingFollowUp(): Flow<List<Communication>>
    suspend fun saveCommunication(communication: Communication): Long
    suspend fun deleteCommunication(id: Long)
    suspend fun setFollowUp(id: Long, pending: Boolean)
    suspend fun searchCommunications(text: String): List<Communication>
    suspend fun frequentContacts(): List<String>

    // --- foto ---
    fun observePhotos(workDayId: Long): Flow<List<Photo>>
    suspend fun savePhoto(photo: Photo): Long
    suspend fun deletePhoto(id: Long)
    suspend fun getPhoto(id: Long): Photo?
    suspend fun setPhotoCaption(id: Long, caption: String?)
    suspend fun setPhotoLinks(id: Long, activityId: Long?, eventId: Long?)
    /** Nomi dei file ancora referenziati, per ripulire quelli orfani. */
    suspend fun knownPhotoFiles(): Set<String>
    suspend fun totalPhotoBytes(): Long
}
