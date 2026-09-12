package it.diario.lavorativo.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.export.FileExporter
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.VehicleExpense
import it.diario.lavorativo.domain.repository.VehicleRepository
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.ExportFormat
import it.diario.lavorativo.domain.model.ExportOptions
import it.diario.lavorativo.domain.model.ExportScope
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.ExportBuilder
import it.diario.lavorativo.domain.service.WeeklyReportBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class ExportUiState(
    val options: ExportOptions = ExportOptions(),
    val periodLabel: String = "",
    val working: Boolean = false,
    val message: String? = null,
    /** Ultimo file prodotto, per poterlo aprire o rimandare senza rifarlo. */
    val lastFile: File? = null,
    val lastFormat: ExportFormat = ExportFormat.PDF
) {
    val canGoForward: Boolean
        get() = options.scope != ExportScope.TUTTO &&
            options.reference.isBefore(LocalDate.now())

    val canScroll: Boolean get() = options.scope != ExportScope.TUTTO
}

/** Prepara PDF, CSV e XLSX e li passa alla condivisione di sistema. */
class ExportViewModel(
    private val workDayRepository: WorkDayRepository,
    private val entryRepository: DiaryEntryRepository,
    private val settingsRepository: SettingsRepository,
    private val builder: ExportBuilder,
    private val weeklyBuilder: WeeklyReportBuilder,
    private val exporter: FileExporter,
    private val vehicleRepository: VehicleRepository,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExportUiState(options = ExportOptions(reference = LocalDate.now(clock.zone())))
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        refreshLabel()
        // I file vecchi si rigenerano in un attimo: non ha senso tenerli.
        viewModelScope.launch { exporter.cleanOlderThan() }
    }

    fun selectScope(scope: ExportScope) {
        update { it.copy(scope = scope) }
    }

    fun selectFormat(format: ExportFormat) {
        update { it.copy(format = format) }
    }

    fun toggleDays(value: Boolean) = update { it.copy(includeDays = value) }
    fun toggleActivities(value: Boolean) = update { it.copy(includeActivities = value) }
    fun toggleEvents(value: Boolean) = update { it.copy(includeEvents = value) }
    fun toggleSummary(value: Boolean) = update { it.copy(includeSummary = value) }

    /**
     * Le comunicazioni sono spente di default: nel documento che va in sede
     * non devono finire i messaggi di terzi. Si accende a mano quando serve.
     */
    fun toggleCommunications(value: Boolean) =
        update { it.copy(includeCommunications = value) }

    fun previous() {
        val o = _uiState.value.options
        val shifted = when (o.scope) {
            ExportScope.SETTIMANA -> o.reference.minusWeeks(1)
            ExportScope.MESE -> o.reference.minusMonths(1)
            ExportScope.ANNO -> o.reference.minusYears(1)
            ExportScope.TUTTO -> o.reference
        }
        update { it.copy(reference = shifted) }
    }

    fun next() {
        if (!_uiState.value.canGoForward) return
        val o = _uiState.value.options
        val shifted = when (o.scope) {
            ExportScope.SETTIMANA -> o.reference.plusWeeks(1)
            ExportScope.MESE -> o.reference.plusMonths(1)
            ExportScope.ANNO -> o.reference.plusYears(1)
            ExportScope.TUTTO -> o.reference
        }
        update { it.copy(reference = shifted) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Prepara il file. Restituisce null se non c'e' niente da esportare. */
    fun export(onReady: (File, ExportFormat) -> Unit) = viewModelScope.launch {
        val options = _uiState.value.options

        if (!options.hasSomethingToExport) {
            _uiState.update { it.copy(message = "Scegli almeno una cosa da esportare") }
            return@launch
        }

        _uiState.update { it.copy(working = true) }

        try {
            val firstEver = workDayRepository.observeFirstDate().first()
            val today = LocalDate.now(clock.zone())
            val from = builder.rangeStart(options.scope, options.reference, firstEver)
            val to = builder.rangeEnd(options.scope, options.reference, today)

            val days = workDayRepository.observeDaysBetween(from, to).first()

            if (days.isEmpty()) {
                _uiState.update {
                    it.copy(working = false, message = "Nessuna giornata in questo periodo")
                }
                return@launch
            }

            // Le voci si leggono solo per le giornate del periodo: su un anno
            // intero caricare tutto sarebbe inutilmente pesante.
            val activities = mutableMapOf<Long, List<WorkActivity>>()
            val events = mutableMapOf<Long, List<WorkEvent>>()
            val communications = mutableMapOf<Long, List<Communication>>()

            days.forEach { day ->
                if (options.includeActivities || options.includeSummary) {
                    activities[day.id] = entryRepository.observeActivities(day.id).first()
                }
                if (options.includeEvents || options.includeSummary) {
                    events[day.id] = entryRepository.observeEvents(day.id).first()
                }
                if (options.includeCommunications) {
                    communications[day.id] =
                        entryRepository.observeCommunications(day.id).first()
                }
            }

            val settings = settingsRepository.settings.first()

            // Dati del mezzo. Si leggono tutti i mezzi, anche quelli non
            // piu' in uso: se nel periodo esportato si guidava il furgone
            // vecchio, i suoi rifornimenti devono comparire lo stesso.
            val vehicles = if (options.includeVehicle) {
                vehicleRepository.observeVehicles().first()
            } else {
                emptyList()
            }
            val fuelStops = mutableListOf<FuelStop>()
            val vehicleExpenses = mutableListOf<VehicleExpense>()
            val maintenances = mutableListOf<Maintenance>()
            vehicles.forEach { v ->
                fuelStops += vehicleRepository.fuelStops(v.id, from, to)
                vehicleExpenses += vehicleRepository.expenses(v.id, from, to)
                maintenances += vehicleRepository.maintenances(v.id, from, to)
            }

            // Una settimana in PDF esce sul modulo cartaceo dell'azienda,
            // non come tabella generica: e' il foglio che in sede si
            // aspettano di ricevere il lunedi'.
            if (options.scope == ExportScope.SETTIMANA &&
                options.format == ExportFormat.PDF
            ) {
                val report = weeklyBuilder.build(
                    weekStart = from,
                    days = days,
                    activities = activities,
                    events = events,
                    now = clock.now(),
                    standardMinutes = settings.standardWorkMinutes,
                    zone = clock.zone()
                )
                val file = exporter.writeWeeklySheet(
                    report = report,
                    userName = settings.userName,
                    fileName = builder.fileName(options.scope, from, to)
                )
                _uiState.update {
                    it.copy(
                        working = false,
                        lastFile = file,
                        lastFormat = ExportFormat.PDF,
                        message = "Rapportino pronto: " + file.name
                    )
                }
                onReady(file, ExportFormat.PDF)
                return@launch
            }

            val document = builder.build(
                options = options,
                from = from,
                to = to,
                days = days,
                activities = activities,
                events = events,
                communications = communications,
                vehicles = vehicles,
                fuelStops = fuelStops,
                vehicleExpenses = vehicleExpenses,
                maintenances = maintenances,
                userName = settings.userName,
                now = clock.now(),
                standardMinutes = settings.standardWorkMinutes,
                zone = clock.zone()
            )

            val file = exporter.write(document, options.format)

            _uiState.update {
                it.copy(
                    working = false,
                    lastFile = file,
                    lastFormat = options.format,
                    message = "Pronto: " + file.name
                )
            }
            onReady(file, options.format)
        } catch (e: Exception) {
            // Se qualcosa va storto meglio dirlo che lasciare la rotella
            // che gira per sempre.
            _uiState.update {
                it.copy(
                    working = false,
                    message = "Non sono riuscito a creare il file"
                )
            }
        }
    }

    fun shareLast(onIntent: (android.content.Intent) -> Unit) {
        val state = _uiState.value
        val file = state.lastFile ?: return
        onIntent(
            exporter.shareIntent(
                file = file,
                format = state.lastFormat,
                subject = "Diario lavorativo - " + state.periodLabel
            )
        )
    }

    private fun update(block: (ExportOptions) -> ExportOptions) {
        _uiState.update { it.copy(options = block(it.options)) }
        refreshLabel()
    }

    private fun refreshLabel() = viewModelScope.launch {
        val options = _uiState.value.options
        val firstEver = workDayRepository.observeFirstDate().first()
        val today = LocalDate.now(clock.zone())
        val from = builder.rangeStart(options.scope, options.reference, firstEver)
        val to = builder.rangeEnd(options.scope, options.reference, today)
        _uiState.update { it.copy(periodLabel = builder.periodLabel(from, to)) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExportViewModel(
                    workDayRepository = diarioContainer.workDayRepository,
                    entryRepository = diarioContainer.diaryEntryRepository,
                    settingsRepository = diarioContainer.settingsRepository,
                    builder = diarioContainer.exportBuilder,
                    weeklyBuilder = diarioContainer.weeklyReportBuilder,
                    exporter = diarioContainer.fileExporter,
                    vehicleRepository = diarioContainer.vehicleRepository,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
