package it.diario.lavorativo.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Giornata lavorativa.
 *
 * Regola: la giornata appartiene alla data dell'ORARIO DI INGRESSO, anche se
 * l'uscita avviene dopo la mezzanotte. Vedi README, sezione "Decisioni tecniche".
 */
data class WorkDay(
    val id: Long = 0L,
    val date: LocalDate,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val site: Site? = null,
    val breaks: List<WorkBreak> = emptyList(),
    val dayType: DayType = DayType.LAVORO,
    val place: String? = null,
    val role: String? = null,
    val description: String? = null,
    val notes: String? = null,
    /** Se valorizzato, sovrascrive l'orario standard globale solo per questa giornata. */
    val standardMinutesOverride: Int? = null,
    /**
     * Chilometri percorsi con il mezzo aziendale.
     *
     * Null non vuol dire zero: vuol dire che non e' stato segnato. La
     * differenza conta, perche' una giornata senza il dato non deve
     * abbassare la media dei chilometri del mese.
     */
    val travelKm: Int? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH
) {
    val isStarted: Boolean get() = startTime != null
    val isClosed: Boolean get() = endTime != null
    val isRunning: Boolean get() = isStarted && !isClosed
    val openBreak: WorkBreak? get() = breaks.firstOrNull { it.isOpen }
    val isOnBreak: Boolean get() = isRunning && openBreak != null
}
