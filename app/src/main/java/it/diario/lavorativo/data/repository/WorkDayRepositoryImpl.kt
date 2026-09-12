package it.diario.lavorativo.data.repository

import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.data.local.dao.BreakDao
import it.diario.lavorativo.data.local.dao.WorkDayDao
import it.diario.lavorativo.data.local.entity.BreakEntity
import it.diario.lavorativo.data.local.entity.WorkDayEntity
import it.diario.lavorativo.data.mapper.toDomain
import it.diario.lavorativo.data.mapper.toEpochDayLong
import it.diario.lavorativo.domain.model.BreakType
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Implementazione Room del repository delle giornate.
 * Tutta la logica di scrittura (apertura/chiusura giornata e pause) passa di qui,
 * cosi' i ViewModel restano sottili.
 */
class WorkDayRepositoryImpl(
    private val workDayDao: WorkDayDao,
    private val breakDao: BreakDao,
    private val clock: AppClock
) : WorkDayRepository {

    override fun observeDay(date: LocalDate): Flow<WorkDay?> =
        workDayDao.observeByDate(date.toEpochDayLong()).map { it?.toDomain() }

    override fun observeOpenDay(): Flow<WorkDay?> =
        workDayDao.observeOpenDay().map { it?.toDomain() }

    override fun observeAllDays(): Flow<List<WorkDay>> =
        workDayDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDaysBetween(from: LocalDate, to: LocalDate): Flow<List<WorkDay>> =
        workDayDao.observeRange(from.toEpochDayLong(), to.toEpochDayLong())
            .map { list -> list.map { it.toDomain() } }

    override fun observeFirstDate(): Flow<LocalDate?> =
        workDayDao.observeFirstDate().map { epochDay -> epochDay?.let { LocalDate.ofEpochDay(it) } }

    override suspend fun deleteDay(workDayId: Long) = workDayDao.delete(workDayId)

    override suspend fun startDay(date: LocalDate, at: Instant, siteId: Long?): Long {
        val epochDay = date.toEpochDayLong()
        val now = clock.now().toEpochMilli()
        val existing = workDayDao.getByDate(epochDay)

        return if (existing == null) {
            workDayDao.insert(
                WorkDayEntity(
                    date = epochDay,
                    startTime = at.toEpochMilli(),
                    siteId = siteId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            // La giornata esiste gia' (per esempio creata dal calendario): la si apre.
            workDayDao.update(
                existing.copy(
                    startTime = existing.startTime ?: at.toEpochMilli(),
                    siteId = siteId ?: existing.siteId,
                    updatedAt = now
                )
            )
            existing.id
        }
    }

    override suspend fun endDay(workDayId: Long, at: Instant) {
        val details = workDayDao.getById(workDayId) ?: return
        val now = clock.now().toEpochMilli()

        // Se e' rimasta una pausa aperta la si chiude allo stesso istante dell'uscita:
        // altrimenti il calcolo conterebbe la pausa fino a "adesso" per sempre.
        breakDao.getOpenBreak(workDayId)?.let { open ->
            val end = maxOf(open.startTime, at.toEpochMilli())
            breakDao.update(open.copy(endTime = end))
        }

        workDayDao.update(
            details.workDay.copy(
                endTime = at.toEpochMilli(),
                updatedAt = now
            )
        )
    }

    override suspend fun updateDayTimes(workDayId: Long, start: Instant?, end: Instant?) {
        val details = workDayDao.getById(workDayId) ?: return
        workDayDao.update(
            details.workDay.copy(
                startTime = start?.toEpochMilli(),
                endTime = end?.toEpochMilli(),
                updatedAt = clock.now().toEpochMilli()
            )
        )
    }

    override suspend fun updateSite(workDayId: Long, siteId: Long?) {
        val details = workDayDao.getById(workDayId) ?: return
        workDayDao.update(
            details.workDay.copy(siteId = siteId, updatedAt = clock.now().toEpochMilli())
        )
    }

    override suspend fun updateNotes(workDayId: Long, description: String?, notes: String?) {
        val details = workDayDao.getById(workDayId) ?: return
        workDayDao.update(
            details.workDay.copy(
                description = description,
                notes = notes,
                updatedAt = clock.now().toEpochMilli()
            )
        )
    }

    override suspend fun updateTravelKm(workDayId: Long, km: Int?) {
        val details = workDayDao.getById(workDayId) ?: return
        // I chilometri negativi non esistono: se arriva un numero storto si
        // preferisce cancellare il dato piuttosto che salvarlo sbagliato.
        val valore = km?.takeIf { it >= 0 }
        workDayDao.update(
            details.workDay.copy(
                travelKm = valore,
                updatedAt = clock.now().toEpochMilli()
            )
        )
    }

    override suspend fun updateDayType(workDayId: Long, type: DayType) {
        val details = workDayDao.getById(workDayId) ?: return
        workDayDao.update(
            details.workDay.copy(
                dayType = type.name,
                updatedAt = clock.now().toEpochMilli()
            )
        )
    }

    override suspend fun createEmptyDay(date: LocalDate, type: DayType): Long {
        val epochDay = date.toEpochDayLong()
        workDayDao.getByDate(epochDay)?.let { return it.id }
        val now = clock.now().toEpochMilli()
        return workDayDao.insert(
            WorkDayEntity(
                date = epochDay,
                dayType = type.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun startBreak(workDayId: Long, at: Instant, type: BreakType): Long {
        // Una sola pausa aperta per volta: se ce n'e' gia' una si restituisce quella.
        breakDao.getOpenBreak(workDayId)?.let { return it.id }
        return breakDao.insert(
            BreakEntity(
                workDayId = workDayId,
                startTime = at.toEpochMilli(),
                type = type.name
            )
        )
    }

    override suspend fun endBreak(breakId: Long, at: Instant) {
        val entity = breakDao.getById(breakId) ?: return
        val end = maxOf(entity.startTime, at.toEpochMilli())
        breakDao.update(entity.copy(endTime = end))
    }

    override suspend fun deleteBreak(breakId: Long) = breakDao.delete(breakId)
}
