package it.diario.lavorativo.ui.history

import it.diario.lavorativo.domain.model.DaySite
import it.diario.lavorativo.domain.model.Trip
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.Photo

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.WorkBreak
import it.diario.lavorativo.domain.model.WorkTimeSummary
import java.time.LocalDate
import java.time.LocalTime

/**
 * Stato della schermata di dettaglio di una giornata.
 *
 * Gli orari sono tenuti come [LocalTime] perche' la schermata modifica
 * l'ora nel fuso locale; la conversione a istante UTC avviene solo al
 * momento del salvataggio.
 */
data class DayDetailUiState(
    val loading: Boolean = true,
    val exists: Boolean = false,
    val workDayId: Long = 0L,
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val dayType: DayType = DayType.LAVORO,
    val site: Site? = null,
    val availableSites: List<Site> = emptyList(),
    val breaks: List<WorkBreak> = emptyList(),
    val description: String = "",
    val notes: String = "",
    /** Chilometri percorsi, come li ha scritti l'utente. Vuoto = non segnati. */
    val travelKm: String = "",
    val summary: WorkTimeSummary = WorkTimeSummary(),
    val isRunning: Boolean = false,
    val extraSites: List<DaySite> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val activities: List<WorkActivity> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    val dirty: Boolean = false,
    val message: String? = null
) {
    /** L'uscita prima dell'ingresso e' ammessa: significa turno oltre mezzanotte. */
    val crossesMidnight: Boolean
        get() {
            val s = startTime
            val e = endTime
            return s != null && e != null && e < s
        }

    val canSave: Boolean get() = exists && dirty

    /** Ore del cantiere principale: il resto della giornata. */
    val mainSiteHours: java.time.Duration
        get() {
            val resto = summary.net.minusMinutes(extraSites.sumOf { it.minutes }.toLong())
            return if (resto.isNegative) java.time.Duration.ZERO else resto
        }
}
