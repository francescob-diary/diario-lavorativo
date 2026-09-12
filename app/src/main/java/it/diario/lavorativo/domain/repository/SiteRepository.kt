package it.diario.lavorativo.domain.repository

import it.diario.lavorativo.domain.model.Site
import kotlinx.coroutines.flow.Flow

/** Contratto di accesso ai cantieri. */
interface SiteRepository {
    fun observeActiveSites(): Flow<List<Site>>
    fun observeAllSites(): Flow<List<Site>>
    fun observeSite(id: Long): Flow<Site?>
    suspend fun getById(id: Long): Site?
    /** Solo i cantieri con coordinate, usati dal riconoscimento automatico. */
    suspend fun getGeolocatedSites(): List<Site>
    /** Quante giornate sono collegate al cantiere: da controllare prima di cancellare. */
    suspend fun countWorkDays(id: Long): Int
    suspend fun upsert(site: Site): Long
    suspend fun setStatus(id: Long, status: it.diario.lavorativo.domain.model.SiteStatus)
    suspend fun delete(id: Long)
}
