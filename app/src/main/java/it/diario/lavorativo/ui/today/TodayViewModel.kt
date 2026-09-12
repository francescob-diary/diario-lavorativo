package it.diario.lavorativo.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.location.LocationProvider
import it.diario.lavorativo.core.location.LocationResult
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.BreakType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.UserSettings
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkTimeSummary
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.SiteRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.SiteLocationMatcher
import it.diario.lavorativo.domain.service.SiteMatch
import it.diario.lavorativo.domain.service.WorkTimeCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * ViewModel della schermata Oggi.
 *
 * Tiene insieme quattro sorgenti: la giornata corrente, la giornata eventualmente
 * rimasta aperta, i cantieri attivi e le impostazioni. Il tempo che scorre e'
 * modellato come un flusso ([ticker]) cosi' il contatore delle ore si aggiorna
 * da solo mentre la schermata e' visibile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val workDayRepository: WorkDayRepository,
    private val siteRepository: SiteRepository,
    private val settingsRepository: SettingsRepository,
    private val calculator: WorkTimeCalculator,
    private val clock: AppClock,
    private val locationProvider: LocationProvider,
    private val siteLocationMatcher: SiteLocationMatcher
) : ViewModel() {

    private val dialogState = MutableStateFlow(TodayDialog.NESSUNA)
    private val messageState = MutableStateFlow<String?>(null)
    private val suggestionState = MutableStateFlow<SiteSuggestion>(SiteSuggestion.Idle)

    private val ticker = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_INTERVAL_MS)
        }
    }

    private val currentDate: Flow<LocalDate> = ticker
        .map { it.atZone(clock.zone()).toLocalDate() }
        .distinctUntilChanged()

    private val currentDay = currentDate.flatMapLatest { workDayRepository.observeDay(it) }

    private data class CoreState(
        val now: Instant,
        val day: WorkDay?,
        val openDay: WorkDay?,
        val settings: UserSettings,
        val sites: List<Site>
    )

    private val coreState = combine(
        ticker,
        currentDay,
        workDayRepository.observeOpenDay(),
        settingsRepository.settings,
        siteRepository.observeActiveSites()
    ) { now, day, openDay, settings, sites ->
        CoreState(now, day, openDay, settings, sites)
    }

    val uiState: StateFlow<TodayUiState> =
        combine(
            coreState,
            dialogState,
            messageState,
            suggestionState
        ) { core, dialog, message, suggestion ->
            val today = core.now.atZone(clock.zone()).toLocalDate()
            val summary = core.day?.let {
                calculator.summarize(it, core.now, core.settings.standardWorkMinutes)
            } ?: WorkTimeSummary()

            TodayUiState(
                date = today,
                now = core.now,
                zone = clock.zone(),
                day = core.day,
                summary = summary,
                activeSites = core.sites,
                settings = core.settings,
                unclosedPreviousDay = core.openDay?.takeIf { it.date != today },
                dialog = dialog,
                suggestion = suggestion,
                isLoading = false,
                message = message
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TodayUiState(
                date = clock.today(),
                now = clock.now(),
                zone = clock.zone()
            )
        )

    // --- Azioni -----------------------------------------------------------

    fun startDay(siteId: Long?) = viewModelScope.launch {
        val now = clock.now()
        workDayRepository.startDay(clock.today(), now, siteId)
        dialogState.value = TodayDialog.NESSUNA
    }

    fun endDay() = viewModelScope.launch {
        val day = uiState.value.day ?: return@launch
        workDayRepository.endDay(day.id, clock.now())
    }

    fun closePreviousDay() = viewModelScope.launch {
        val day = uiState.value.unclosedPreviousDay ?: return@launch
        workDayRepository.endDay(day.id, clock.now())
        messageState.value = "Giornata precedente chiusa. Controlla gli orari nello storico."
    }

    /** Un solo pulsante per le pause: avvia se non e' in corso, altrimenti chiude. */
    fun toggleBreak(type: BreakType = BreakType.PAUSA) = viewModelScope.launch {
        val day = uiState.value.day ?: return@launch
        if (!day.isRunning) return@launch
        val open = day.openBreak
        if (open == null) {
            workDayRepository.startBreak(day.id, clock.now(), type)
        } else {
            workDayRepository.endBreak(open.id, clock.now())
        }
    }

    fun selectSite(siteId: Long?) = viewModelScope.launch {
        val day = uiState.value.day
        if (day == null) {
            // Nessuna giornata ancora: la scelta del cantiere avvia direttamente la giornata.
            startDay(siteId)
        } else {
            workDayRepository.updateSite(day.id, siteId)
            dialogState.value = TodayDialog.NESSUNA
        }
    }

    fun createSite(name: String, city: String?) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val id = siteRepository.upsert(Site(name = trimmed, city = city?.trim()?.ifBlank { null }))
        selectSite(id)
    }

    /** Correzione manuale degli orari (requisito 17). */
    fun updateStartTime(time: LocalTime) = viewModelScope.launch {
        val day = uiState.value.day ?: return@launch
        val newStart = time.toInstantOn(day.date)
        val end = day.endTime
        workDayRepository.updateDayTimes(
            workDayId = day.id,
            start = newStart,
            end = end
        )
        dialogState.value = TodayDialog.NESSUNA
    }

    fun updateEndTime(time: LocalTime) = viewModelScope.launch {
        val day = uiState.value.day ?: return@launch
        var newEnd = time.toInstantOn(day.date)
        val start = day.startTime
        // Turno che passa la mezzanotte: se l'uscita risulta prima dell'ingresso
        // si intende il giorno successivo.
        if (start != null && newEnd.isBefore(start)) {
            newEnd = time.toInstantOn(day.date.plusDays(1))
        }
        workDayRepository.updateDayTimes(workDayId = day.id, start = start, end = newEnd)
        dialogState.value = TodayDialog.NESSUNA
    }

    /**
     * Cerca il cantiere in base alla posizione attuale.
     *
     * Il permesso viene chiesto dalla schermata: qui si assume concesso, e se
     * non lo e' il provider risponde PermissionMissing e mostriamo il motivo.
     * Il risultato e' solo un suggerimento, non seleziona nulla da solo.
     */
    fun detectSite() = viewModelScope.launch {
        suggestionState.value = SiteSuggestion.Searching

        val sites = siteRepository.getGeolocatedSites()
        if (sites.isEmpty()) {
            suggestionState.value = SiteSuggestion.NoGeolocatedSites
            return@launch
        }

        when (val fix = locationProvider.currentLocation()) {
            is LocationResult.Success -> {
                suggestionState.value = when (val match = siteLocationMatcher.match(fix.point, sites)) {
                    is SiteMatch.Confident -> SiteSuggestion.Found(
                        site = match.site,
                        distanceMeters = match.distanceMeters.toInt()
                    )
                    is SiteMatch.Ambiguous -> SiteSuggestion.Several(match.candidates)
                    is SiteMatch.None -> SiteSuggestion.OutOfRange(
                        nearestName = match.nearest?.site?.name,
                        distanceMeters = match.nearest?.distanceMeters?.toInt()
                    )
                }
            }
            LocationResult.PermissionMissing -> suggestionState.value =
                SiteSuggestion.Unavailable("Serve il permesso di posizione.")
            LocationResult.LocationDisabled -> suggestionState.value =
                SiteSuggestion.Unavailable("Il GPS e' spento.")
            LocationResult.Timeout -> suggestionState.value =
                SiteSuggestion.Unavailable("Nessun segnale GPS. Prova all'aperto.")
            is LocationResult.Error -> suggestionState.value =
                SiteSuggestion.Unavailable(fix.message)
        }
    }

    fun clearSuggestion() {
        suggestionState.value = SiteSuggestion.Idle
    }

    /** Accetta il cantiere suggerito dal GPS. */
    fun acceptSuggestion(siteId: Long) {
        suggestionState.value = SiteSuggestion.Idle
        selectSite(siteId)
    }

    fun showDialog(dialog: TodayDialog) {
        dialogState.value = dialog
    }

    fun dismissDialog() {
        dialogState.value = TodayDialog.NESSUNA
    }

    fun consumeMessage() {
        messageState.value = null
    }

    private fun LocalTime.toInstantOn(date: LocalDate): Instant =
        LocalDateTime.of(date, this).atZone(clock.zone()).toInstant()

    companion object {
        private const val TICK_INTERVAL_MS = 15_000L
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = this.diarioContainer
                TodayViewModel(
                    workDayRepository = container.workDayRepository,
                    siteRepository = container.siteRepository,
                    settingsRepository = container.settingsRepository,
                    calculator = container.workTimeCalculator,
                    clock = container.clock,
                    locationProvider = container.locationProvider,
                    siteLocationMatcher = container.siteLocationMatcher
                )
            }
        }
    }
}
