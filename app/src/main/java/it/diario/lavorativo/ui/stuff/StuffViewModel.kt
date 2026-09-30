package it.diario.lavorativo.ui.stuff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.data.repository.StuffRepository
import it.diario.lavorativo.domain.model.Loan
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.Tool
import it.diario.lavorativo.domain.repository.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class StuffTab { DEPOSITO, PRESTITI }

/** Come si guarda il deposito: per posto (cosa c'e' qui) o per attrezzo (dov'e'). */
enum class DepotView { PER_POSTO, PER_ATTREZZO }

data class StuffUiState(
    val tab: StuffTab = StuffTab.DEPOSITO,
    val view: DepotView = DepotView.PER_POSTO,
    val query: String = "",
    val tools: List<Tool> = emptyList(),
    val sites: List<Site> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val message: String? = null
) {
    val visibleTools: List<Tool>
        get() {
            val q = query.trim().lowercase()
            return if (q.isEmpty()) tools else tools.filter {
                it.name.lowercase().contains(q) || it.fullPlace.lowercase().contains(q)
            }
        }

    /** Posto -> attrezzi che ci sono, i posti con piu' roba prima. */
    val byPlace: List<Pair<String, List<Tool>>>
        get() = visibleTools.groupBy { it.placeLabel }
            .toList()
            .sortedWith(compareBy({ ordine(it.second.first()) }, { it.first.lowercase() }))

    private fun ordine(t: Tool): Int = t.placeType.ordinal

    val openLoans: List<Loan> get() = loans.filter { it.isOut }
    val returnedLoans: List<Loan> get() = loans.filterNot { it.isOut }
}

class StuffViewModel(
    private val repository: StuffRepository,
    siteRepository: SiteRepository,
    private val clock: AppClock,
    startTab: StuffTab
) : ViewModel() {

    private val _uiState = MutableStateFlow(StuffUiState(tab = startTab, today = clock.today()))
    val uiState: StateFlow<StuffUiState> = _uiState.asStateFlow()

    init {
        val sites = siteRepository.observeAllSites()
        viewModelScope.launch {
            sites.collect { list -> _uiState.update { it.copy(sites = list) } }
        }
        viewModelScope.launch {
            repository.observeTools(sites).collect { list -> _uiState.update { it.copy(tools = list) } }
        }
        viewModelScope.launch {
            repository.observeLoans().collect { list -> _uiState.update { it.copy(loans = list) } }
        }
    }

    fun selectTab(tab: StuffTab) = _uiState.update { it.copy(tab = tab) }
    fun selectView(view: DepotView) = _uiState.update { it.copy(view = view) }
    fun onQuery(q: String) = _uiState.update { it.copy(query = q) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun saveTool(tool: Tool) = viewModelScope.launch {
        if (tool.name.isBlank()) return@launch
        repository.saveTool(tool)
        _uiState.update { it.copy(message = tool.name.trim() + ": " + tool.fullPlace) }
    }

    fun deleteTool(tool: Tool) = viewModelScope.launch {
        repository.deleteTool(tool.id)
        _uiState.update { it.copy(message = tool.name + " tolto dal deposito") }
    }

    fun saveLoan(loan: Loan) = viewModelScope.launch {
        if (loan.what.isBlank() || loan.toWhom.isBlank()) return@launch
        repository.saveLoan(loan)
    }

    fun markReturned(loan: Loan) = viewModelScope.launch {
        repository.setReturned(loan.id, clock.today())
        _uiState.update { it.copy(message = loan.what + " restituito") }
    }

    fun undoReturned(loan: Loan) = viewModelScope.launch {
        repository.setReturned(loan.id, null)
    }

    fun deleteLoan(loan: Loan) = viewModelScope.launch { repository.deleteLoan(loan.id) }

    companion object {
        fun factory(startTab: StuffTab): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StuffViewModel(
                    repository = diarioContainer.stuffRepository,
                    siteRepository = diarioContainer.siteRepository,
                    clock = diarioContainer.clock,
                    startTab = startTab
                )
            }
        }
    }
}
