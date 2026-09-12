package it.diario.lavorativo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.PeriodSummarizer
import it.diario.lavorativo.domain.service.WorkTimeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Storico e calendario in un unico ViewModel: le due viste guardano gli
 * stessi dati del mese selezionato, cambia solo il modo di disegnarli.
 *
 * Il mese e' la finestra di caricamento: si interrogano solo le giornate
 * comprese nel mese visualizzato, non tutto lo storico. Cosi' la schermata
 * resta veloce anche dopo anni di registrazioni.
 */
class HistoryViewModel(
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculator: WorkTimeCalculator,
    private val summarizer: PeriodSummarizer,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HistoryUiState(month = YearMonth.from(clock.today()), today = clock.today())
    )
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val selectedMonth = MutableStateFlow(YearMonth.from(clock.today()))

    init {
        viewModelScope.launch {
            workDayRepository.observeFirstDate().collect { first ->
                _uiState.update { it.copy(firstRecordedDate = first) }
            }
        }

        viewModelScope.launch {
            selectedMonth
                .flatMapLatest { month ->
                    val from = month.atDay(1)
                    val to = month.atEndOfMonth()
                    combine(
                        workDayRepository.observeDaysBetween(from, to),
                        settingsRepository.settings
                    ) { days, settings -> Triple(month, days, settings.standardWorkMinutes) }
                }
                .collect { (month, days, standard) ->
                    val now = clock.now()
                    val today = clock.today()

                    val rows = days
                        .sortedByDescending { it.date }
                        .map { day ->
                            HistoryRow(
                                day = day,
                                summary = calculator.summarize(day, now, standard)
                            )
                        }

                    val byDate = rows.associateBy { it.day.date }

                    val cells = monthGrid(month).map { date ->
                        val row = byDate[date]
                        CalendarCell(
                            date = date,
                            day = row?.day,
                            summary = row?.summary,
                            isToday = date == today,
                            isSelected = date == _uiState.value.selectedDate
                        )
                    }

                    _uiState.update {
                        it.copy(
                            loading = false,
                            month = month,
                            today = today,
                            rows = rows,
                            cells = cells,
                            totals = summarizer.totals(days, now, standard)
                        )
                    }
                }
        }
    }

    /**
     * Griglia del mese allineata alla settimana: parte dal lunedi' precedente
     * il primo del mese e arriva alla domenica successiva all'ultimo, cosi'
     * le colonne restano sempre sotto la stessa iniziale del giorno.
     */
    private fun monthGrid(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = month.atEndOfMonth()
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val dates = mutableListOf<LocalDate>()
        var cursor = first
        while (!cursor.isAfter(last)) {
            dates += cursor
            cursor = cursor.plusDays(1)
        }
        return dates
    }

    fun showList() = _uiState.update { it.copy(view = HistoryView.ELENCO) }

    fun showCalendar() = _uiState.update { it.copy(view = HistoryView.CALENDARIO) }

    fun previousMonth() {
        if (!_uiState.value.canGoBack) return
        changeMonth(_uiState.value.month.minusMonths(1))
    }

    fun nextMonth() {
        if (!_uiState.value.canGoForward) return
        changeMonth(_uiState.value.month.plusMonths(1))
    }

    fun goToCurrentMonth() = changeMonth(YearMonth.from(clock.today()))

    private fun changeMonth(month: YearMonth) {
        // Cambiando mese la selezione precedente non ha piu' senso.
        _uiState.update { it.copy(month = month, selectedDate = null, loading = true) }
        selectedMonth.value = month
    }

    fun selectDate(date: LocalDate?) = _uiState.update { state ->
        val newSelection = if (state.selectedDate == date) null else date
        state.copy(
            selectedDate = newSelection,
            cells = state.cells.map { it.copy(isSelected = it.date == newSelection) }
        )
    }

    fun deleteDay(workDayId: Long) = viewModelScope.launch {
        workDayRepository.deleteDay(workDayId)
        _uiState.update { it.copy(selectedDate = null, message = "Giornata eliminata") }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    workDayRepository = diarioContainer.workDayRepository,
                    settingsRepository = diarioContainer.settingsRepository,
                    calculator = diarioContainer.workTimeCalculator,
                    summarizer = diarioContainer.periodSummarizer,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
