package it.diario.lavorativo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.StatsRange
import it.diario.lavorativo.domain.model.StatsSummary
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.StatisticsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val range: StatsRange = StatsRange.MESE,
    val reference: LocalDate = LocalDate.now(),
    val summary: StatsSummary = StatsSummary()
) {
    /** Non si guarda avanti nel futuro: non c'e' niente da vedere. */
    val canGoForward: Boolean get() = summary.to.isBefore(LocalDate.now())
}

/** Dashboard: quadro del periodo, andamento, cantieri, lavorazioni. */
class StatsViewModel(
    private val workDayRepository: WorkDayRepository,
    private val entryRepository: DiaryEntryRepository,
    private val settingsRepository: SettingsRepository,
    private val statistics: StatisticsCalculator,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState(reference = LocalDate.now(clock.zone())))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun selectRange(range: StatsRange) {
        _uiState.update { it.copy(range = range) }
        reload()
    }

    fun previous() {
        val state = _uiState.value
        val shifted = when (state.range) {
            StatsRange.SETTIMANA -> state.reference.minusWeeks(1)
            StatsRange.MESE -> state.reference.minusMonths(1)
            StatsRange.ANNO -> state.reference.minusYears(1)
        }
        _uiState.update { it.copy(reference = shifted) }
        reload()
    }

    fun next() {
        val state = _uiState.value
        if (!state.canGoForward) return
        val shifted = when (state.range) {
            StatsRange.SETTIMANA -> state.reference.plusWeeks(1)
            StatsRange.MESE -> state.reference.plusMonths(1)
            StatsRange.ANNO -> state.reference.plusYears(1)
        }
        _uiState.update { it.copy(reference = shifted) }
        reload()
    }

    private fun reload() = viewModelScope.launch {
        _uiState.update { it.copy(loading = true) }
        val state = _uiState.value

        val from = statistics.rangeStart(state.range, state.reference)
        val to = statistics.rangeEnd(state.range, state.reference)
        val previousFrom = statistics.previousStart(state.range, from)
        val previousTo = from.minusDays(1)

        val days = workDayRepository.observeDaysBetween(from, to).first()
        val previousDays = workDayRepository.observeDaysBetween(previousFrom, previousTo).first()
        val standard = settingsRepository.settings.first().standardWorkMinutes

        // Lavorazioni ed eventi si leggono solo per le giornate del periodo:
        // su un anno intero caricarli tutti sarebbe inutilmente pesante.
        val activities = mutableListOf<WorkActivity>()
        val events = mutableListOf<WorkEvent>()
        days.forEach { day ->
            activities += entryRepository.observeActivities(day.id).first()
            events += entryRepository.observeEvents(day.id).first()
        }

        val summary = statistics.build(
            range = state.range,
            reference = state.reference,
            days = days,
            previousDays = previousDays,
            activities = activities,
            events = events,
            now = clock.now(),
            standardMinutes = standard
        )

        _uiState.update { it.copy(loading = false, summary = summary) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatsViewModel(
                    workDayRepository = diarioContainer.workDayRepository,
                    entryRepository = diarioContainer.diaryEntryRepository,
                    settingsRepository = diarioContainer.settingsRepository,
                    statistics = diarioContainer.statisticsCalculator,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
