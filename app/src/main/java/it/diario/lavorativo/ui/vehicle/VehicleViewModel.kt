package it.diario.lavorativo.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.DueStatus
import it.diario.lavorativo.domain.model.ExpenseType
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.MaintenanceDue
import it.diario.lavorativo.domain.model.MaintenanceType
import it.diario.lavorativo.domain.model.Vehicle
import it.diario.lavorativo.domain.model.VehicleExpense
import it.diario.lavorativo.domain.model.VehicleTotals
import it.diario.lavorativo.domain.repository.VehicleRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.MaintenanceScheduler
import it.diario.lavorativo.domain.service.VehicleCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Quale periodo si sta guardando. */
enum class VehiclePeriod(val label: String) {
    MESE("Mese"),
    ANNO("Anno"),
    TUTTO("Tutto")
}

data class VehicleUiState(
    val loading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicleId: Long? = null,
    val period: VehiclePeriod = VehiclePeriod.MESE,
    val reference: LocalDate = LocalDate.now(),
    val totals: VehicleTotals? = null,
    val dues: List<MaintenanceDue> = emptyList(),
    val fuelStops: List<FuelStop> = emptyList(),
    val expenses: List<VehicleExpense> = emptyList(),
    val maintenances: List<Maintenance> = emptyList(),
    val lastOdometerKm: Int? = null,
    val message: String? = null
) {
    val selectedVehicle: Vehicle?
        get() = vehicles.firstOrNull { it.id == selectedVehicleId }

    val hasVehicle: Boolean get() = vehicles.isNotEmpty()

    /** Le scadenze che meritano di essere viste subito, in cima. */
    val urgentDues: List<MaintenanceDue>
        get() = dues.filter { it.status != DueStatus.OK }
}

/**
 * La schermata del mezzo aziendale.
 *
 * Tiene insieme rifornimenti, spese e officina, perche' sono la stessa
 * domanda vista da tre lati: quanto costa questo furgone.
 *
 * I conti non li fa questa classe: li fanno VehicleCalculator e
 * MaintenanceScheduler, che sono Kotlin puro e si possono provare. Qui c'e'
 * solo il montaggio.
 */
class VehicleViewModel(
    private val repository: VehicleRepository,
    private val workDayRepository: WorkDayRepository,
    private val calculator: VehicleCalculator,
    private val scheduler: MaintenanceScheduler,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleUiState(reference = LocalDate.now(clock.zone())))
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeVehicles().collect { mezzi ->
                _uiState.update { stato ->
                    // Se non e' stato scelto niente si parte dal mezzo in uso.
                    val scelto = stato.selectedVehicleId?.takeIf { id -> mezzi.any { it.id == id } }
                        ?: mezzi.firstOrNull { it.active }?.id
                        ?: mezzi.firstOrNull()?.id
                    stato.copy(vehicles = mezzi, selectedVehicleId = scelto, loading = false)
                }
                refresh()
            }
        }
    }

    fun selectVehicle(id: Long) {
        _uiState.update { it.copy(selectedVehicleId = id) }
        refresh()
    }

    fun setPeriod(period: VehiclePeriod) {
        _uiState.update { it.copy(period = period) }
        refresh()
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ------------------------------------------------------------ salvataggi

    fun saveVehicle(name: String, plate: String?, id: Long = 0L, active: Boolean = true) {
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "Il mezzo ha bisogno di un nome") }
            return
        }
        viewModelScope.launch {
            val esistente = if (id != 0L) repository.vehicleById(id) else null
            val nuovo = repository.saveVehicle(
                (esistente ?: Vehicle(name = name)).copy(
                    id = id,
                    name = name.trim(),
                    plate = plate?.trim()?.takeIf { it.isNotBlank() },
                    active = active
                )
            )
            if (id == 0L) _uiState.update { it.copy(selectedVehicleId = nuovo) }
            refresh()
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
            _uiState.update { it.copy(selectedVehicleId = null) }
            refresh()
        }
    }

    fun saveFuelStop(
        date: LocalDate,
        liters: Double,
        amountCents: Long,
        odometerKm: Int?,
        fullTank: Boolean,
        station: String?,
        id: Long = 0L
    ) {
        val vehicleId = _uiState.value.selectedVehicleId
        if (vehicleId == null) {
            _uiState.update { it.copy(message = "Aggiungi prima il mezzo") }
            return
        }
        if (liters <= 0.0 || amountCents <= 0L) {
            _uiState.update { it.copy(message = "Servono i litri e l'importo") }
            return
        }
        viewModelScope.launch {
            repository.saveFuelStop(
                FuelStop(
                    id = id,
                    vehicleId = vehicleId,
                    date = date,
                    liters = liters,
                    amountCents = amountCents,
                    odometerKm = odometerKm,
                    fullTank = fullTank,
                    station = station?.trim()?.takeIf { it.isNotBlank() },
                    createdAt = clock.now()
                )
            )
            refresh()
        }
    }

    fun deleteFuelStop(stop: FuelStop) {
        viewModelScope.launch {
            repository.deleteFuelStop(stop)
            refresh()
        }
    }

    fun saveExpense(
        date: LocalDate,
        type: ExpenseType,
        amountCents: Long,
        place: String?,
        id: Long = 0L
    ) {
        val vehicleId = _uiState.value.selectedVehicleId
        if (vehicleId == null) {
            _uiState.update { it.copy(message = "Aggiungi prima il mezzo") }
            return
        }
        if (amountCents <= 0L) {
            _uiState.update { it.copy(message = "Serve l'importo") }
            return
        }
        viewModelScope.launch {
            // Se quel giorno c'e' una giornata registrata la spesa ci si
            // aggancia da sola: cosi' finisce nel conto della trasferta
            // senza doverla collegare a mano.
            val giornata = workDayRepository.observeDay(date).first()
            repository.saveExpense(
                VehicleExpense(
                    id = id,
                    vehicleId = vehicleId,
                    workDayId = giornata?.id,
                    date = date,
                    type = type,
                    amountCents = amountCents,
                    place = place?.trim()?.takeIf { it.isNotBlank() },
                    createdAt = clock.now()
                )
            )
            refresh()
        }
    }

    fun deleteExpense(expense: VehicleExpense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            refresh()
        }
    }

    fun saveMaintenance(
        date: LocalDate,
        type: MaintenanceType,
        description: String?,
        odometerKm: Int?,
        amountCents: Long?,
        workshop: String?,
        nextDueDate: LocalDate?,
        nextDueKm: Int?,
        id: Long = 0L
    ) {
        val vehicleId = _uiState.value.selectedVehicleId
        if (vehicleId == null) {
            _uiState.update { it.copy(message = "Aggiungi prima il mezzo") }
            return
        }
        viewModelScope.launch {
            repository.saveMaintenance(
                Maintenance(
                    id = id,
                    vehicleId = vehicleId,
                    date = date,
                    type = type,
                    description = description?.trim()?.takeIf { it.isNotBlank() },
                    odometerKm = odometerKm,
                    amountCents = amountCents,
                    workshop = workshop?.trim()?.takeIf { it.isNotBlank() },
                    nextDueDate = nextDueDate,
                    nextDueKm = nextDueKm,
                    createdAt = clock.now()
                )
            )
            refresh()
        }
    }

    fun deleteMaintenance(maintenance: Maintenance) {
        viewModelScope.launch {
            repository.deleteMaintenance(maintenance)
            refresh()
        }
    }

    /** Proposta di chilometri per la prossima scadenza, da poter cambiare. */
    fun suggestNextKm(type: MaintenanceType, odometerKm: Int?): Int? =
        scheduler.suggestedNextKm(type, odometerKm ?: _uiState.value.lastOdometerKm)

    // ------------------------------------------------------------ ricarica

    private fun refresh() {
        val stato = _uiState.value
        val vehicleId = stato.selectedVehicleId ?: return

        viewModelScope.launch {
            val oggi = LocalDate.now(clock.zone())
            val da = periodStart(stato.period, oggi)
            val a = periodEnd(stato.period, oggi)

            val rifornimenti = repository.fuelStops(vehicleId, da, a)
            val spese = repository.expenses(vehicleId, da, a)
            val interventiPeriodo = repository.maintenances(vehicleId, da, a)

            // Le scadenze guardano tutta la storia del mezzo, non solo il
            // periodo mostrato: un tagliando fatto due anni fa scade lo
            // stesso, anche se in questa schermata non si vede.
            val tuttiInterventi = repository.allMaintenances(vehicleId)
            val tuttiRifornimenti =
                repository.fuelStops(vehicleId, LocalDate.ofEpochDay(0), oggi)
            val contatore = calculator.lastKnownOdometer(tuttiRifornimenti, tuttiInterventi)

            // I chilometri segnati sulle giornate del periodo.
            val kmGiornate = workDayRepository.observeDaysBetween(da, a).first()
                .mapNotNull { it.travelKm }
                .sum()

            val totali = calculator.totals(
                from = da,
                to = a,
                stops = rifornimenti,
                expenses = spese,
                maintenances = interventiPeriodo,
                travelKm = kmGiornate
            )

            _uiState.update {
                it.copy(
                    totals = totali,
                    dues = scheduler.dueList(tuttiInterventi, oggi, contatore),
                    fuelStops = rifornimenti.sortedByDescending { r -> r.date },
                    expenses = spese.sortedByDescending { e -> e.date },
                    maintenances = interventiPeriodo.sortedByDescending { m -> m.date },
                    lastOdometerKm = contatore,
                    loading = false
                )
            }
        }
    }

    private fun periodStart(period: VehiclePeriod, today: LocalDate): LocalDate = when (period) {
        VehiclePeriod.MESE -> today.withDayOfMonth(1)
        VehiclePeriod.ANNO -> today.withDayOfYear(1)
        // Piu' indietro del 1970 non serve andare.
        VehiclePeriod.TUTTO -> LocalDate.ofEpochDay(0)
    }

    private fun periodEnd(period: VehiclePeriod, today: LocalDate): LocalDate = when (period) {
        VehiclePeriod.MESE -> today.withDayOfMonth(today.lengthOfMonth())
        VehiclePeriod.ANNO -> today.withDayOfYear(today.lengthOfYear())
        VehiclePeriod.TUTTO -> today
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VehicleViewModel(
                    repository = diarioContainer.vehicleRepository,
                    workDayRepository = diarioContainer.workDayRepository,
                    calculator = diarioContainer.vehicleCalculator,
                    scheduler = diarioContainer.maintenanceScheduler,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
