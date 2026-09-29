package it.diario.lavorativo.ui.sites

import kotlinx.coroutines.flow.combine
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.service.SiteDaysCalculator
import it.diario.lavorativo.domain.service.WorkTimeCalculator
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus
import it.diario.lavorativo.domain.repository.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lista dei cantieri: ricerca, filtro archiviati, archiviazione rapida. */
class SitesViewModel(
    private val siteRepository: SiteRepository,
    private val workDayRepository: WorkDayRepository,
    private val settingsRepository: SettingsRepository,
    private val calculator: WorkTimeCalculator,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(SitesUiState())
    val uiState: StateFlow<SitesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            siteRepository.observeAllSites().collect { sites ->
                _uiState.update { it.copy(loading = false, sites = sites) }
            }
        }
        // Il calendario al contrario: giorni lavorati per ogni cantiere.
        viewModelScope.launch {
            combine(workDayRepository.observeAllDays(), settingsRepository.settings) { days, s ->
                SiteDaysCalculator(calculator).build(days, clock.now(), s.standardWorkMinutes)
            }.collect { mappa -> _uiState.update { it.copy(daysBySite = mappa) } }
        }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun toggleShowTerminated() =
        _uiState.update { it.copy(showTerminated = !it.showTerminated) }

    fun archive(site: Site) = viewModelScope.launch {
        siteRepository.setStatus(site.id, SiteStatus.TERMINATO)
        _uiState.update { it.copy(message = "Cantiere " + site.name + " archiviato") }
    }

    fun reactivate(site: Site) = viewModelScope.launch {
        siteRepository.setStatus(site.id, SiteStatus.ATTIVO)
        _uiState.update { it.copy(message = "Cantiere " + site.name + " riattivato") }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SitesViewModel(
                    siteRepository = diarioContainer.siteRepository,
                    workDayRepository = diarioContainer.workDayRepository,
                    settingsRepository = diarioContainer.settingsRepository,
                    calculator = diarioContainer.workTimeCalculator,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
