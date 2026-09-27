package it.diario.lavorativo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.SiteRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import it.diario.lavorativo.domain.model.Trip
import it.diario.lavorativo.ui.day.ExtraSiteInput
import it.diario.lavorativo.domain.service.WorkTimeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Dettaglio e modifica di una giornata passata.
 *
 * Le modifiche non vengono scritte a ogni tocco: si accumulano nello stato e
 * si salvano con il pulsante SALVA. In cantiere e' facile toccare per sbaglio,
 * e una scrittura immediata renderebbe difficile accorgersi dell'errore.
 *
 * Eccezione: il tipo giornata di una data ancora senza registrazione crea
 * subito la giornata, perche' senza un record non c'e' nulla da modificare.
 */
class DayDetailViewModel(
    private val workDayRepository: WorkDayRepository,
    private val entryRepository: DiaryEntryRepository,
    private val siteRepository: SiteRepository,
    private val settingsRepository: SettingsRepository,
    private val calculator: WorkTimeCalculator,
    private val clock: AppClock,
    private val epochDay: Long
) : ViewModel() {

    private val date: LocalDate = LocalDate.ofEpochDay(epochDay)

    private val _uiState = MutableStateFlow(DayDetailUiState(date = date))
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                workDayRepository.observeDay(date),
                siteRepository.observeActiveSites(),
                settingsRepository.settings
            ) { day, sites, settings ->
                Triple(day, sites, settings.standardWorkMinutes)
            }.collect { (day, sites, standard) ->
                // Se l'utente ha modifiche non salvate non si sovrascrive il form
                // con i dati del database: si aggiorna solo la lista dei cantieri.
                if (_uiState.value.dirty) {
                    // Cantieri in piu' e spostamenti si salvano subito:
                    // si aggiornano anche col modulo modificato.
                    _uiState.update {
                        it.copy(
                            availableSites = sites,
                            extraSites = day?.extraSites.orEmpty(),
                            trips = day?.trips.orEmpty()
                        )
                    }
                    return@collect
                }
                applyDay(day, sites, standard)
            }
        }
    }

    private fun applyDay(day: WorkDay?, sites: List<Site>, standard: Int) {
        if (day == null) {
            _uiState.update {
                it.copy(
                    loading = false,
                    exists = false,
                    availableSites = sites,
                    date = date
                )
            }
            return
        }
        val zone = clock.zone()
        _uiState.update {
            it.copy(
                loading = false,
                exists = true,
                workDayId = day.id,
                date = day.date,
                startTime = day.startTime?.atZone(zone)?.toLocalTime(),
                endTime = day.endTime?.atZone(zone)?.toLocalTime(),
                dayType = day.dayType,
                site = day.site,
                availableSites = sites,
                breaks = day.breaks,
                description = day.description.orEmpty(),
                notes = day.notes.orEmpty(),
                travelKm = day.travelKm?.toString().orEmpty(),
                summary = calculator.summarize(day, clock.now(), standard),
                isRunning = day.isRunning,
                extraSites = day.extraSites,
                trips = day.trips,
                zone = zone,
                dirty = false
            )
        }
        observeEntries(day.id)
    }

    private var entriesFor: Long = 0L

    /** Lavorazioni e foto della giornata, per il riepilogo in fondo. */
    private fun observeEntries(workDayId: Long) {
        if (entriesFor == workDayId) return
        entriesFor = workDayId
        viewModelScope.launch {
            entryRepository.observeActivities(workDayId).collect { list ->
                _uiState.update { it.copy(activities = list) }
            }
        }
        viewModelScope.launch {
            entryRepository.observePhotos(workDayId).collect { list ->
                _uiState.update { it.copy(photos = list) }
            }
        }
    }

    fun saveExtraSite(input: ExtraSiteInput) = viewModelScope.launch {
        val dayId = _uiState.value.workDayId
        if (dayId == 0L) return@launch
        if (input.id == 0L) {
            workDayRepository.addExtraSite(dayId, input.siteId, input.minutes, input.description)
        } else {
            workDayRepository.updateExtraSite(input.id, input.siteId, input.minutes, input.description)
        }
    }

    fun deleteExtraSite(id: Long) = viewModelScope.launch {
        workDayRepository.deleteExtraSite(id)
    }

    fun saveTrip(trip: Trip) = viewModelScope.launch {
        val dayId = _uiState.value.workDayId
        if (dayId == 0L) return@launch
        workDayRepository.saveTrip(trip.copy(workDayId = dayId))
    }

    fun deleteTrip(id: Long) = viewModelScope.launch {
        workDayRepository.deleteTrip(id)
    }

    fun onStartTime(time: LocalTime) =
        _uiState.update { it.copy(startTime = time, dirty = true) }

    fun onEndTime(time: LocalTime) =
        _uiState.update { it.copy(endTime = time, dirty = true) }

    fun onSite(site: Site?) = _uiState.update { it.copy(site = site, dirty = true) }

    fun onDescription(v: String) = _uiState.update { it.copy(description = v, dirty = true) }

    fun onNotes(v: String) = _uiState.update { it.copy(notes = v, dirty = true) }

    fun onTravelKm(v: String) =
        _uiState.update { it.copy(travelKm = v.filter { c -> c.isDigit() }, dirty = true) }

    fun onDayType(type: DayType) {
        val state = _uiState.value
        if (!state.exists) {
            // Nessuna registrazione per questa data: la si crea con il tipo scelto.
            viewModelScope.launch {
                workDayRepository.createEmptyDay(date, type)
                _uiState.update { it.copy(message = "Giornata creata") }
            }
            return
        }
        _uiState.update { it.copy(dayType = type, dirty = true) }
    }

    /** Crea una giornata vuota di lavoro, per registrare a mano un giorno dimenticato. */
    fun createWorkDay() = viewModelScope.launch {
        workDayRepository.createEmptyDay(date, DayType.LAVORO)
        _uiState.update { it.copy(message = "Giornata creata, ora inserisci gli orari") }
    }

    fun save() = viewModelScope.launch {
        val state = _uiState.value
        if (!state.exists) return@launch

        val start = state.startTime?.let { toInstant(state.date, it) }
        // Uscita prima dell'ingresso: e' il turno che passa la mezzanotte,
        // quindi l'uscita appartiene al giorno successivo.
        val end = state.endTime?.let { time ->
            val day = if (state.crossesMidnight) state.date.plusDays(1) else state.date
            toInstant(day, time)
        }

        workDayRepository.updateDayTimes(state.workDayId, start, end)
        workDayRepository.updateSite(state.workDayId, state.site?.id)
        workDayRepository.updateDayType(state.workDayId, state.dayType)
        workDayRepository.updateNotes(
            state.workDayId,
            state.description.trim().ifBlank { null },
            state.notes.trim().ifBlank { null }
        )
        // Campo vuoto vuol dire "non l'ho segnato", e si salva come tale:
        // uno zero direbbe che quel giorno non ci si e' mossi.
        workDayRepository.updateTravelKm(
            state.workDayId,
            state.travelKm.trim().toIntOrNull()
        )

        _uiState.update { it.copy(dirty = false, message = "Modifiche salvate") }
    }

    fun deleteBreak(breakId: Long) = viewModelScope.launch {
        workDayRepository.deleteBreak(breakId)
        _uiState.update { it.copy(message = "Pausa eliminata") }
    }

    fun deleteDay(onDeleted: () -> Unit) = viewModelScope.launch {
        val id = _uiState.value.workDayId
        if (id != 0L) workDayRepository.deleteDay(id)
        onDeleted()
    }

    private fun toInstant(day: LocalDate, time: LocalTime): Instant =
        day.atTime(time).atZone(clock.zone()).toInstant()

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(epochDay: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DayDetailViewModel(
                    workDayRepository = diarioContainer.workDayRepository,
                    entryRepository = diarioContainer.diaryEntryRepository,
                    siteRepository = diarioContainer.siteRepository,
                    settingsRepository = diarioContainer.settingsRepository,
                    calculator = diarioContainer.workTimeCalculator,
                    clock = diarioContainer.clock,
                    epochDay = epochDay
                )
            }
        }
    }
}
