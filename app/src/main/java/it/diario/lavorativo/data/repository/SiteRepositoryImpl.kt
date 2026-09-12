package it.diario.lavorativo.data.repository

import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.data.local.dao.SiteDao
import it.diario.lavorativo.data.mapper.toDomain
import it.diario.lavorativo.data.mapper.toEntity
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus
import it.diario.lavorativo.domain.repository.SiteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SiteRepositoryImpl(
    private val siteDao: SiteDao,
    private val clock: AppClock
) : SiteRepository {

    override fun observeActiveSites(): Flow<List<Site>> =
        siteDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAllSites(): Flow<List<Site>> =
        siteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSite(id: Long): Flow<Site?> =
        siteDao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: Long): Site? = siteDao.getById(id)?.toDomain()

    override suspend fun getGeolocatedSites(): List<Site> =
        siteDao.getGeolocated().map { it.toDomain() }

    override suspend fun countWorkDays(id: Long): Int = siteDao.countWorkDays(id)

    override suspend fun upsert(site: Site): Long {
        val now = clock.now().toEpochMilli()
        val cleaned = site.copy(
            name = site.name.trim(),
            address = site.address?.trim()?.takeIf { it.isNotEmpty() },
            city = site.city?.trim()?.takeIf { it.isNotEmpty() },
            client = site.client?.trim()?.takeIf { it.isNotEmpty() },
            company = site.company?.trim()?.takeIf { it.isNotEmpty() },
            contact = site.contact?.trim()?.takeIf { it.isNotEmpty() },
            phone = site.phone?.trim()?.takeIf { it.isNotEmpty() },
            notes = site.notes?.trim()?.takeIf { it.isNotEmpty() }
        )
        return if (cleaned.id == 0L) {
            siteDao.insert(cleaned.toEntity(createdAt = now, updatedAt = now))
        } else {
            val existing = siteDao.getById(cleaned.id)
            siteDao.update(
                cleaned.toEntity(createdAt = existing?.createdAt ?: now, updatedAt = now)
            )
            cleaned.id
        }
    }

    override suspend fun setStatus(id: Long, status: SiteStatus) {
        val existing = siteDao.getById(id) ?: return
        siteDao.update(existing.copy(status = status.name, updatedAt = clock.now().toEpochMilli()))
    }

    override suspend fun delete(id: Long) = siteDao.delete(id)
}
