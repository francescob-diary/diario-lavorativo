package it.diario.lavorativo.domain.model

import java.time.Duration
import java.time.LocalDate

/**
 * Riepilogo della settimana da consegnare in sede.
 *
 * Viene ricostruito dai dati gia' registrati: l'utente non ricompila nulla
 * da zero, controlla e corregge. Ogni riga corrisponde a una giornata, cosi'
 * il foglio si legge come il cartellino cartaceo a cui sono abituati in sede.
 *
 * La settimana va da lunedi' a domenica: e' la convenzione italiana e coincide
 * con la griglia del calendario gia' usata nello storico.
 */
data class WeeklyReport(
    val weekStart: LocalDate,
    val days: List<WeeklyDayLine> = emptyList(),
    val totals: PeriodTotals = PeriodTotals(),
    /** Cantieri toccati nella settimana, in ordine di prima comparsa. */
    val sites: List<String> = emptyList(),
    /** Eventi che meritano di finire sul foglio: gravi, importanti o irrisolti. */
    val notableEvents: List<WorkEvent> = emptyList(),
    /** Note aggiunte a mano dall'utente prima di consegnare. */
    val notes: String = "",
    /** True quando il foglio e' stato marcato come consegnato. */
    val delivered: Boolean = false
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    val hasData: Boolean get() = days.any { it.hasContent }

    /** Giornate registrate ma senza orario di uscita: vanno sistemate prima di consegnare. */
    val incompleteDays: List<WeeklyDayLine> get() = days.filter { it.incomplete }

    val isReadyToDeliver: Boolean get() = hasData && incompleteDays.isEmpty()
}

/**
 * Una riga del foglio settimanale, una per giorno di calendario.
 * I giorni senza nulla registrato ci sono lo stesso, vuoti: sul cartellino
 * cartaceo le righe ci sono comunque e servono a far vedere che non e' un buco.
 */
data class WeeklyDayLine(
    val date: LocalDate,
    val dayType: DayType = DayType.LAVORO,
    val siteName: String? = null,
    val startLabel: String? = null,
    val endLabel: String? = null,
    val net: Duration = Duration.ZERO,
    val overtime: Duration = Duration.ZERO,
    val breaks: Duration = Duration.ZERO,
    /** Lavorazioni del giorno, gia' unite in una riga leggibile. */
    val work: String = "",
    /** True se registrata ma mai chiusa. */
    val incomplete: Boolean = false
) {
    val isWorkDay: Boolean get() = dayType == DayType.LAVORO

    val hasContent: Boolean
        get() = startLabel != null || !net.isZero || dayType != DayType.LAVORO || work.isNotBlank()
}
