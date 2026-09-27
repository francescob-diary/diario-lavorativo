package it.diario.lavorativo.ui.history

import it.diario.lavorativo.domain.model.PeriodTotals
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkTimeSummary
import java.time.LocalDate
import java.time.YearMonth

/**
 * Una casella del calendario mensile.
 * [day] e' null per le giornate senza registrazione.
 */
data class CalendarCell(
    val date: LocalDate,
    val day: WorkDay? = null,
    val summary: WorkTimeSummary? = null,
    val isToday: Boolean = false,
    val isSelected: Boolean = false
) {
    val hasData: Boolean get() = day != null
}

/**
 * Riga dell'elenco scorrevole dello storico.
 * Il riepilogo e' precalcolato dal ViewModel: la UI non fa conti.
 */
data class HistoryRow(
    val day: WorkDay,
    val summary: WorkTimeSummary
)

/** Modalita' di visualizzazione: storico e calendario sono la stessa schermata. */
enum class HistoryView(val label: String) {
    ELENCO("Elenco"),
    MESE("Mese"),
    SETTIMANA("Settimana"),
    GIORNO("Giorno")
}

data class HistoryUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val view: HistoryView = HistoryView.ELENCO,
    val cells: List<CalendarCell> = emptyList(),
    val rows: List<HistoryRow> = emptyList(),
    val totals: PeriodTotals = PeriodTotals(),
    val selectedDate: LocalDate? = null,
    val firstRecordedDate: LocalDate? = null,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
    /** Giorno attorno a cui ruotano le viste settimana e giorno. */
    val focusDate: LocalDate = LocalDate.now(),
    /** Tutte le giornate caricate (anche a cavallo del mese), per data. */
    val byDate: Map<LocalDate, HistoryRow> = emptyMap()
) {
    /** Lunedi'-domenica della settimana del giorno in primo piano. */
    val focusWeek: List<LocalDate>
        get() {
            val lunedi = focusDate.minusDays((focusDate.dayOfWeek.value - 1).toLong())
            return (0L..6L).map { lunedi.plusDays(it) }
        }

    val isEmpty: Boolean get() = !loading && rows.isEmpty()

    /** Giornata selezionata nel calendario, se ha dati. */
    val selectedRow: HistoryRow?
        get() = selectedDate?.let { d -> rows.firstOrNull { it.day.date == d } }

    /** Si puo' tornare indietro solo fino al mese della prima registrazione. */
    val canGoBack: Boolean
        get() = firstRecordedDate?.let { YearMonth.from(it) < month } ?: false

    /** Non si naviga nel futuro oltre il mese corrente. */
    val canGoForward: Boolean get() = month < YearMonth.from(today)

    val isCurrentMonth: Boolean get() = month == YearMonth.from(today)
}
