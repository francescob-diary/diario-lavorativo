package it.diario.lavorativo.data.repository

import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.data.local.dao.ActivityDao
import it.diario.lavorativo.data.local.dao.CommunicationDao
import it.diario.lavorativo.data.local.dao.EventDao
import it.diario.lavorativo.data.local.dao.PhotoDao
import it.diario.lavorativo.data.mapper.toDomain
import it.diario.lavorativo.data.mapper.toEntity
import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.Photo
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class DiaryEntryRepositoryImpl(
    private val activityDao: ActivityDao,
    private val eventDao: EventDao,
    private val communicationDao: CommunicationDao,
    private val photoDao: PhotoDao,
    private val clock: AppClock
) : DiaryEntryRepository {

    // --- attivita' ---

    override fun observeActivities(workDayId: Long): Flow<List<WorkActivity>> =
        activityDao.observeForDay(workDayId).map { list -> list.map { it.toDomain() } }

    override suspend fun saveActivity(activity: WorkActivity): Long {
        val stamped = if (activity.id == 0L) activity.copy(createdAt = clock.now()) else activity
        return activityDao.insert(stamped.toEntity())
    }

    override suspend fun deleteActivity(id: Long) = activityDao.deleteById(id)

    override suspend fun frequentDescriptions(category: ActivityCategory): List<String> =
        activityDao.frequentDescriptions(category.name)

    // --- eventi ---

    override fun observeEvents(workDayId: Long): Flow<List<WorkEvent>> =
        eventDao.observeForDay(workDayId).map { list -> list.map { it.toDomain() } }

    override fun observeUnresolvedEvents(): Flow<List<WorkEvent>> =
        eventDao.observeUnresolved().map { list -> list.map { it.toDomain() } }

    override suspend fun saveEvent(event: WorkEvent): Long {
        val stamped = if (event.id == 0L) event.copy(createdAt = clock.now()) else event
        return eventDao.insert(stamped.toEntity())
    }

    override suspend fun deleteEvent(id: Long) = eventDao.deleteById(id)

    override suspend fun setEventUnresolved(id: Long, unresolved: Boolean) =
        eventDao.setUnresolved(id, unresolved)

    // --- comunicazioni ---

    override fun observeCommunications(workDayId: Long): Flow<List<Communication>> =
        communicationDao.observeForDay(workDayId).map { list -> list.map { it.toDomain() } }

    override fun observePendingFollowUp(): Flow<List<Communication>> =
        communicationDao.observePendingFollowUp().map { list -> list.map { it.toDomain() } }

    override suspend fun saveCommunication(communication: Communication): Long {
        val stamped = if (communication.id == 0L) {
            communication.copy(createdAt = clock.now())
        } else {
            communication
        }
        return communicationDao.insert(stamped.toEntity())
    }

    override suspend fun deleteCommunication(id: Long) = communicationDao.deleteById(id)

    override suspend fun setFollowUp(id: Long, pending: Boolean) =
        communicationDao.setFollowUp(id, pending)

    override suspend fun searchCommunications(text: String): List<Communication> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()
        return communicationDao.search(cleaned).map { it.toDomain() }
    }

    override suspend fun frequentContacts(): List<String> = communicationDao.frequentContacts()

    // --- foto ---

    override fun observePhotos(workDayId: Long): Flow<List<Photo>> =
        photoDao.observeForDay(workDayId).map { list -> list.map { it.toDomain() } }

    override suspend fun savePhoto(photo: Photo): Long {
        val stamped = if (photo.id == 0L) photo.copy(createdAt = clock.now()) else photo
        return photoDao.insert(stamped.toEntity())
    }

    override suspend fun deletePhoto(id: Long) = photoDao.deleteById(id)

    override suspend fun getPhoto(id: Long): Photo? = photoDao.getById(id)?.toDomain()

    override suspend fun setPhotoCaption(id: Long, caption: String?) =
        photoDao.setCaption(id, caption)

    override suspend fun setPhotoLinks(id: Long, activityId: Long?, eventId: Long?) =
        photoDao.setLinks(id, activityId, eventId)

    override suspend fun knownPhotoFiles(): Set<String> = photoDao.allFileNames().toSet()

    override suspend fun totalPhotoBytes(): Long = photoDao.totalBytes()
}
