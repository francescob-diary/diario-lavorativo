package it.diario.lavorativo.ui.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.WeeklyReport
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.WeeklyReportBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeeklyUiState(
    val loading: Boolean = true,
    val report: WeeklyReport = WeeklyReport(weekStart = LocalDate.now()),
    val notes: String = "",
    val message: String? = null
) {
    val canGoForward: Boolean
        get() = report.weekStart.plusWeeks(1).isBefore(
            LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusDays(1)
        )
}

/**
 * Foglio settimanale da consegnare in sede.
 *
 * Il foglio non si compila da zero: viene ricostruito dalle giornate gia'
 * registrate, e l'utente controlla, corregge quello che manca e aggiunge le
 * note. E' il motivo per cui esiste l'app.
 */
class WeeklyViewModel(
    private val workDayRepository: WorkDayRepository,
    private val entryRepository: DiaryEntryRepository,
    private val settingsRepository: SettingsRepository,
    private val builder: WeeklyReportBuilder,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyUiState())
    val uiState: StateFlow<WeeklyUiState> = _uiState.asStateFlow()

    init {
        // All'apertura si mostra la settimana appena conclusa: e' quella che
        // si deve consegnare, ed e' il motivo per cui si apre la schermata.
        load(previousWeekStart())
    }

    private fun previousWeekStart(): LocalDate =
        LocalDate.now(clock.zone())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(1)

    fun load(weekStart: LocalDate) = viewModelScope.launch {
        _uiState.update { it.copy(loading = true) }

        val monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = workDayRepository.observeDaysBetween(monday, monday.plusDays(6)).first()
        val standard = settingsRepository.settings.first().standardWorkMinutes

        val activities = mutableMapOf<Long, List<WorkActivity>>()
        val events = mutableMapOf<Long, List<WorkEvent>>()
        days.forEach { day ->
            activities[day.id] = entryRepository.observeActivities(day.id).first()
            events[day.id] = entryRepository.observeEvents(day.id).first()
        }

        val report = builder.build(
            weekStart = monday,
            days = days,
            activities = activities,
            events = events,
            now = clock.now(),
            standardMinutes = standard,
            zone = clock.zone()
        )

        _uiState.update { it.copy(loading = false, report = report) }
    }

    fun previousWeek() = load(_uiState.value.report.weekStart.minusWeeks(1))

    fun nextWeek() {
        if (!_uiState.value.canGoForward) return
        load(_uiState.value.report.weekStart.plusWeeks(1))
    }

    fun onNotes(text: String) = _uiState.update { it.copy(notes = text) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WeeklyViewModel(
                    workDayRepository = diarioContainer.workDayRepository,
                    entryRepository = diarioContainer.diaryEntryRepository,
                    settingsRepository = diarioContainer.settingsRepository,
                    builder = diarioContainer.weeklyReportBuilder,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
