package it.diario.lavorativo.ui.stuff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.domain.model.Loan
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus
import it.diario.lavorativo.domain.model.Tool
import it.diario.lavorativo.domain.model.ToolPlaceType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("d/MM/yyyy")

/**
 * Attrezzi: il deposito virtuale (dove sta ogni cosa) e le cose prestate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StuffScreen(
    startTab: StuffTab = StuffTab.DEPOSITO,
    viewModel: StuffViewModel = viewModel(
        key = "attrezzi-" + startTab.name,
        factory = StuffViewModel.factory(startTab)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var toolEdit by remember { mutableStateOf<Tool?>(null) }
    var loanEdit by remember { mutableStateOf<Loan?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (state.tab == StuffTab.DEPOSITO) toolEdit = Tool(name = "")
                    else loanEdit = Loan(what = "", toWhom = "", loanDate = state.today)
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (state.tab == StuffTab.DEPOSITO) "Attrezzo" else "Prestito") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == StuffTab.DEPOSITO,
                    onClick = { viewModel.selectTab(StuffTab.DEPOSITO) },
                    text = { Text("Deposito") }
                )
                Tab(
                    selected = state.tab == StuffTab.PRESTITI,
                    onClick = { viewModel.selectTab(StuffTab.PRESTITI) },
                    text = {
                        Text(
                            "Prestiti" + if (state.openLoans.isNotEmpty()) " (" + state.openLoans.size + ")" else ""
                        )
                    }
                )
            }
            when (state.tab) {
                StuffTab.DEPOSITO -> DepotTab(state, viewModel, onEdit = { toolEdit = it })
                StuffTab.PRESTITI -> LoansTab(state, viewModel, onEdit = { loanEdit = it })
            }
        }
    }

    toolEdit?.let { t ->
        ToolDialog(
            tool = t,
            sites = state.sites.filter { it.status == SiteStatus.ATTIVO || it.id == t.site?.id },
            onDismiss = { toolEdit = null },
            onSave = {
                viewModel.saveTool(it)
                toolEdit = null
            },
            onDelete = if (t.id != 0L) ({
                viewModel.deleteTool(t)
                toolEdit = null
            }) else null
        )
    }
    loanEdit?.let { l ->
        LoanDialog(
            loan = l,
            onDismiss = { loanEdit = null },
            onSave = {
                viewModel.saveLoan(it)
                loanEdit = null
            },
            onDelete = if (l.id != 0L) ({
                viewModel.deleteLoan(l)
                loanEdit = null
            }) else null
        )
    }
}

// ------------------------------------------------------------ deposito

@Composable
private fun DepotTab(state: StuffUiState, vm: StuffViewModel, onEdit: (Tool) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQuery,
            label = { Text("Cerca un attrezzo o un posto") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            FilterChip(
                selected = state.view == DepotView.PER_POSTO,
                onClick = { vm.selectView(DepotView.PER_POSTO) },
                label = { Text("Cosa c'e' dove") }
            )
            FilterChip(
                selected = state.view == DepotView.PER_ATTREZZO,
                onClick = { vm.selectView(DepotView.PER_ATTREZZO) },
                label = { Text("Dov'e' ogni attrezzo") }
            )
        }
        if (state.tools.isEmpty()) {
            Text(
                "Nessun attrezzo. Aggiungi quello che hai e dove lo lasci: deposito, furgone, " +
                    "un cantiere (anche la stanza). Quando lo sposti, lo aggiorni con un tocco.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            if (state.view == DepotView.PER_POSTO) {
                state.byPlace.forEach { (posto, attrezzi) ->
                    item(key = "p-$posto") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    posto + "  (" + attrezzi.size + ")",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                attrezzi.forEach { t ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { onEdit(t) }.padding(vertical = 6.dp)
                                    ) {
                                        Text(t.name, modifier = Modifier.weight(1f))
                                        t.placeDetail?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(state.visibleTools, key = { "t-" + it.id }) { t ->
                    Card(Modifier.fillMaxWidth().clickable { onEdit(t) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(t.fullPlace, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun ToolDialog(
    tool: Tool,
    sites: List<Site>,
    onDismiss: () -> Unit,
    onSave: (Tool) -> Unit,
    onDelete: (() -> Unit)?
) {
    var nome by remember { mutableStateOf(tool.name) }
    var tipo by remember { mutableStateOf(tool.placeType) }
    var cantiere by remember { mutableStateOf(tool.site) }
    var stanza by remember { mutableStateOf(tool.placeDetail.orEmpty()) }
    var altro by remember { mutableStateOf(tool.placeName.orEmpty()) }
    var note by remember { mutableStateOf(tool.notes.orEmpty()) }
    val valido = nome.isNotBlank() && (tipo != ToolPlaceType.CANTIERE || cantiere != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tool.id == 0L) "Nuovo attrezzo" else "Dov'e' " + tool.name + "?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Attrezzo") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Dove si trova", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToolPlaceType.entries.forEach { t ->
                        FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.label) })
                    }
                }
                if (tipo == ToolPlaceType.CANTIERE) {
                    if (sites.isEmpty()) {
                        Text("Nessun cantiere attivo.", color = MaterialTheme.colorScheme.error)
                    }
                    sites.forEach { s ->
                        FilterChip(
                            selected = cantiere?.id == s.id,
                            onClick = { cantiere = s },
                            label = { Text(s.displayLabel) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (tipo == ToolPlaceType.ALTRO) {
                    OutlinedTextField(
                        value = altro, onValueChange = { altro = it },
                        label = { Text("Quale posto") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = stanza, onValueChange = { stanza = it },
                    label = { Text("Stanza o zona (facoltativa)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("ELIMINA ATTREZZO", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = {
                    onSave(
                        tool.copy(
                            name = nome,
                            placeType = tipo,
                            site = if (tipo == ToolPlaceType.CANTIERE) cantiere else null,
                            placeDetail = stanza,
                            placeName = altro,
                            notes = note
                        )
                    )
                }
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}

// ------------------------------------------------------------ prestiti

@Composable
private fun LoansTab(state: StuffUiState, vm: StuffViewModel, onEdit: (Loan) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item {
            Text(
                "Alle 16:30 arriva un promemoria se c'e' ancora qualcosa fuori.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        if (state.openLoans.isEmpty()) {
            item { Text("Niente in prestito.", modifier = Modifier.padding(vertical = 12.dp)) }
        }
        items(state.openLoans, key = { "o-" + it.id }) { l ->
            val giorni = ChronoUnit.DAYS.between(l.loanDate, state.today)
            Card(Modifier.fillMaxWidth().clickable { onEdit(l) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.what, style = MaterialTheme.typography.titleMedium)
                        Text("a " + l.toWhom)
                        Text(
                            "dal " + l.loanDate.format(DATA) +
                                if (giorni > 0) " (" + giorni + (if (giorni == 1L) " giorno)" else " giorni)") else " (oggi)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { vm.markReturned(l) }) { Text("RESTITUITO") }
                }
            }
        }
        if (state.returnedLoans.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Restituiti", style = MaterialTheme.typography.titleSmall)
            }
            items(state.returnedLoans.take(30), key = { "r-" + it.id }) { l ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(l) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.what + " - " + l.toWhom)
                            Text(
                                "reso il " + (l.returnedDate?.format(DATA) ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { vm.undoReturned(l) }) { Text("ANNULLA") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun LoanDialog(
    loan: Loan,
    onDismiss: () -> Unit,
    onSave: (Loan) -> Unit,
    onDelete: (() -> Unit)?
) {
    var cosa by remember { mutableStateOf(loan.what) }
    var chi by remember { mutableStateOf(loan.toWhom) }
    var data by remember { mutableStateOf(loan.loanDate.format(DATA)) }
    var note by remember { mutableStateOf(loan.notes.orEmpty()) }
    val giorno = runCatching { LocalDate.parse(data.trim(), DATA) }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (loan.id == 0L) "Nuovo prestito" else "Prestito") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = cosa, onValueChange = { cosa = it },
                    label = { Text("Cosa") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chi, onValueChange = { chi = it },
                    label = { Text("A chi") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = data, onValueChange = { data = it },
                    label = { Text("Quando (gg/mm/aaaa)") }, singleLine = true,
                    isError = giorno == null, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note") }, modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("ELIMINA", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cosa.isNotBlank() && chi.isNotBlank() && giorno != null,
                onClick = {
                    onSave(loan.copy(what = cosa, toWhom = chi, loanDate = giorno ?: loan.loanDate, notes = note))
                }
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}
