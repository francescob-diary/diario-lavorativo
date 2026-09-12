package it.diario.lavorativo.domain.repository

import it.diario.lavorativo.domain.model.BreakType
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkDay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Contratto di accesso ai dati delle giornate.
 * La UI e i ViewModel dipendono solo da questa interfaccia, mai da Room.
 */
interface WorkDayRepository {

    /** Giornata di una data specifica (null se non ancora registrata). */
    fun observeDay(date: LocalDate): Flow<WorkDay?>

    /** Giornata iniziata e non ancora chiusa, se esiste. Serve per il caso "turno oltre mezzanotte". */
    fun observeOpenDay(): Flow<WorkDay?>

    /** Tutte le giornate, dalla piu' recente. */
    fun observeAllDays(): Flow<List<WorkDay>>

    /** Giornate comprese fra due date, estremi inclusi. Usata da storico e calendario. */
    fun observeDaysBetween(from: LocalDate, to: LocalDate): Flow<List<WorkDay>>

    /** Data della prima giornata registrata, se esiste. */
    fun observeFirstDate(): Flow<LocalDate?>

    /** Elimina una giornata e, per cascata, le sue pause. */
    suspend fun deleteDay(workDayId: Long)

    /** Crea la giornata e registra l'ingresso. Restituisce l'id della giornata. */
    suspend fun startDay(date: LocalDate, at: Instant, siteId: Long?): Long

    /** Registra l'uscita e chiude automaticamente un'eventuale pausa ancora aperta. */
    suspend fun endDay(workDayId: Long, at: Instant)

    /** Correzione manuale degli orari (requisito 17). */
    suspend fun updateDayTimes(workDayId: Long, start: Instant?, end: Instant?)

    suspend fun updateSite(workDayId: Long, siteId: Long?)

    suspend fun updateNotes(workDayId: Long, description: String?, notes: String?)

    /**
     * Chilometri percorsi con il mezzo aziendale in questa giornata.
     *
     * Null cancella il dato: non e' la stessa cosa di zero. Zero vuol dire
     * "oggi non mi sono mosso", null vuol dire "non l'ho segnato", e nei
     * conti del mese le due cose pesano in modo diverso.
     */
    suspend fun updateTravelKm(workDayId: Long, km: Int?)

    /** Cambia il tipo di giornata: lavoro, ferie, permesso, malattia, festivo. */
    suspend fun updateDayType(workDayId: Long, type: DayType)

    /** Crea una giornata vuota per una data, senza orari. Restituisce l'id. */
    suspend fun createEmptyDay(date: LocalDate, type: DayType): Long

    suspend fun startBreak(workDayId: Long, at: Instant, type: BreakType): Long

    suspend fun endBreak(breakId: Long, at: Instant)

    suspend fun deleteBreak(breakId: Long)
}
