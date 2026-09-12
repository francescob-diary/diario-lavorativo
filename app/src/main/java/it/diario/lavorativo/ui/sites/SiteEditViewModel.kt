package it.diario.lavorativo.ui.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.location.LocationProvider
import it.diario.lavorativo.core.location.LocationResult
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus
import it.diario.lavorativo.domain.repository.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Scheda di un cantiere: creazione, modifica, posizione, cancellazione. */
class SiteEditViewModel(
    private val siteRepository: SiteRepository,
    private val locationProvider: LocationProvider,
    private val siteId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteEditUiState(id = siteId))
    val uiState: StateFlow<SiteEditUiState> = _uiState.asStateFlow()

    init {
        if (siteId != 0L) load(siteId)
    }

    private fun load(id: Long) = viewModelScope.launch {
        val site = siteRepository.getById(id) ?: return@launch
        val linked = siteRepository.countWorkDays(id)
        _uiState.update {
            it.copy(
                id = site.id,
                name = site.name,
                address = site.address.orEmpty(),
                city = site.city.orEmpty(),
                client = site.client.orEmpty(),
                company = site.company.orEmpty(),
                contact = site.contact.orEmpty(),
                phone = site.phone.orEmpty(),
                notes = site.notes.orEmpty(),
                status = site.status,
                latitude = site.latitude,
                longitude = site.longitude,
                radiusMeters = site.radiusMeters,
                linkedWorkDays = linked
            )
        }
    }

    fun onName(v: String) = _uiState.update { it.copy(name = v, error = null) }
    fun onAddress(v: String) = _uiState.update { it.copy(address = v) }
    fun onCity(v: String) = _uiState.update { it.copy(city = v) }
    fun onClient(v: String) = _uiState.update { it.copy(client = v) }
    fun onCompany(v: String) = _uiState.update { it.copy(company = v) }
    fun onContact(v: String) = _uiState.update { it.copy(contact = v) }
    fun onPhone(v: String) = _uiState.update { it.copy(phone = v) }
    fun onNotes(v: String) = _uiState.update { it.copy(notes = v) }
    fun onRadius(v: Int) = _uiState.update { it.copy(radiusMeters = v) }
    fun onStatus(v: SiteStatus) = _uiState.update { it.copy(status = v) }

    fun clearPosition() = _uiState.update {
        it.copy(latitude = null, longitude = null, positionAccuracy = null, message = "Posizione rimossa")
    }

    /**
     * Registra la posizione attuale come posizione del cantiere.
     * Da premere stando sul posto: e' il modo piu' semplice di ottenere le
     * coordinate senza geocodifica online ne' mappa.
     */
    fun capturePosition() = viewModelScope.launch {
        _uiState.update { it.copy(capturingPosition = true, error = null, message = null) }
        when (val result = locationProvider.currentLocation()) {
            is LocationResult.Success -> _uiState.update {
                it.copy(
                    capturingPosition = false,
                    latitude = result.point.latitude,
                    longitude = result.point.longitude,
                    positionAccuracy = result.point.accuracyMeters,
                    message = "Posizione registrata"
                )
            }
            LocationResult.PermissionMissing -> _uiState.update {
                it.copy(
                    capturingPosition = false,
                    error = "Serve il permesso di posizione per registrare il cantiere."
                )
            }
            LocationResult.LocationDisabled -> _uiState.update {
                it.copy(
                    capturingPosition = false,
                    error = "Il GPS e' spento. Attivalo e riprova."
                )
            }
            LocationResult.Timeout -> _uiState.update {
                it.copy(
                    capturingPosition = false,
                    error = "Nessun segnale GPS. Prova all'aperto e riprova."
                )
            }
            is LocationResult.Error -> _uiState.update {
                it.copy(capturingPosition = false, error = result.message)
            }
        }
    }

    fun save() = viewModelScope.launch {
        val s = _uiState.value
        if (s.name.isBlank()) {
            _uiState.update { it.copy(error = "Il nome del cantiere e' obbligatorio.") }
            return@launch
        }
        _uiState.update { it.copy(saving = true, error = null) }
        try {
            siteRepository.upsert(
                Site(
                    id = s.id,
                    name = s.name,
                    address = s.address,
                    city = s.city,
                    client = s.client,
                    company = s.company,
                    contact = s.contact,
                    phone = s.phone,
                    notes = s.notes,
                    status = s.status,
                    latitude = s.latitude,
                    longitude = s.longitude,
                    radiusMeters = s.radiusMeters
                )
            )
            _uiState.update { it.copy(saving = false, saved = true) }
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(
                    saving = false,
                    error = "Esiste gia' un cantiere con questo nome, oppure il salvataggio non e' riuscito."
                )
            }
        }
    }

    fun delete() = viewModelScope.launch {
        val s = _uiState.value
        if (s.id == 0L) return@launch
        siteRepository.delete(s.id)
        _uiState.update { it.copy(saved = true) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, error = null) }

    companion object {
        /** siteId a 0 significa "nuovo cantiere". */
        fun factory(siteId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SiteEditViewModel(
                    siteRepository = diarioContainer.siteRepository,
                    locationProvider = diarioContainer.locationProvider,
                    siteId = siteId
                )
            }
        }
    }
}
