package it.diario.lavorativo.ui.vehicle

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.util.MoneyFormat
import it.diario.lavorativo.domain.model.DueStatus
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.MaintenanceDue
import it.diario.lavorativo.domain.model.VehicleExpense
import it.diario.lavorativo.domain.model.VehicleTotals
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cosa si sta guardando nell'elenco in basso. */
private enum class VehicleTab(val label: String) {
    RIFORNIMENTI("Rifornimenti"),
    SPESE("Pedaggi"),
    OFFICINA("Officina")
}

private val SHORT_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM", Locale.ITALIAN)

/**
 * Il mezzo aziendale: quanto beve, quanto costa, quando va in officina.
 *
 * L'ordine della schermata segue l'urgenza: prima le scadenze, che sono
 * l'unica cosa che puo' costare una multa, poi i numeri del periodo, poi
 * i tre pulsanti per registrare, e in fondo l'elenco di quello che c'e'
 * gia'. Chi apre di corsa al distributore trova il pulsante senza
 * scorrere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    onBack: () -> Unit = {},
    viewModel: VehicleViewModel = viewModel(factory = VehicleViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val oggi = remember { LocalDate.now() }

    var tab by remember { mutableStateOf(VehicleTab.RIFORNIMENTI) }
    var mezzoInModifica by remember { mutableStateOf(false) }
    var rifornimentoAperto by remember { mutableStateOf(false) }
    var rifornimentoInModifica by remember { mutableStateOf<FuelStop?>(null) }
    var speseAperto by remember { mutableStateOf(false) }
    var spesaInModifica by remember { mutableStateOf<VehicleExpense?>(null) }
    var officinaAperta by remember { mutableStateOf(false) }
    var interventoInModifica by remember { mutableStateOf<Maintenance?>(null) }
    var daCancellare by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.selectedVehicle?.label ?: "Mezzo aziendale") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (state.hasVehicle) {
                        IconButton(onClick = { mezzoInModifica = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifica mezzo")
                        }
                    }
                }
            )
        }
    ) { padding ->

        if (!state.hasVehicle) {
            EmptyVehicle(
                modifier = Modifier.padding(padding),
                onAdd = { mezzoInModifica = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                // Piu' mezzi: si sceglie quale guardare.
                if (state.vehicles.size > 1) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            state.vehicles.forEach { v ->
                                FilterChip(
                                    selected = v.id == state.selectedVehicleId,
                                    onClick = { viewModel.selectVehicle(v.id) },
                                    label = { Text(v.name) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Le scadenze in cima: sono l'unica cosa che ha una data
                // di consegna vera.
                if (state.urgentDues.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        DueCard(state.urgentDues)
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        VehiclePeriod.entries.forEach { p ->
                            FilterChip(
                                selected = state.period == p,
                                onClick = { viewModel.setPeriod(p) },
                                label = { Text(p.label) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    state.totals?.let { TotalsCard(it) }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                rifornimentoInModifica = null
                                rifornimentoAperto = true
                            },
                            modifier = Modifier.weight(1f).height(72.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.LocalGasStation, contentDescription = null)
                                Text("PIENO", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                spesaInModifica = null
                                speseAperto = true
                            },
                            modifier = Modifier.weight(1f).height(72.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Receipt, contentDescription = null)
                                Text("SPESA", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                interventoInModifica = null
                                officinaAperta = true
                            },
                            modifier = Modifier.weight(1f).height(72.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Build, contentDescription = null)
                                Text("OFFICINA", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        VehicleTab.entries.forEach { t ->
                            FilterChip(
                                selected = tab == t,
                                onClick = { tab = t },
                                label = { Text(t.label) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                when (tab) {
                    VehicleTab.RIFORNIMENTI -> {
                        if (state.fuelStops.isEmpty()) {
                            item { EmptyRow("Nessun rifornimento in questo periodo") }
                        }
                        items(state.fuelStops, key = { "r" + it.id }) { r ->
                            FuelRow(
                                stop = r,
                                onEdit = {
                                    rifornimentoInModifica = r
                                    rifornimentoAperto = true
                                },
                                onDelete = {
                                    daCancellare = "questo rifornimento" to {
                                        viewModel.deleteFuelStop(r)
                                    }
                                }
                            )
                        }
                    }

                    VehicleTab.SPESE -> {
                        if (state.expenses.isEmpty()) {
                            item { EmptyRow("Nessun pedaggio o parcheggio in questo periodo") }
                        }
                        items(state.expenses, key = { "s" + it.id }) { e ->
                            ExpenseRow(
                                expense = e,
                                onEdit = {
                                    spesaInModifica = e
                                    speseAperto = true
                                },
                                onDelete = {
                                    daCancellare = "questa spesa" to { viewModel.deleteExpense(e) }
                                }
                            )
                        }
                    }

                    VehicleTab.OFFICINA -> {
                        if (state.maintenances.isEmpty()) {
                            item { EmptyRow("Nessun intervento in questo periodo") }
                        }
                        items(state.maintenances, key = { "m" + it.id }) { m ->
                            MaintenanceRow(
                                maintenance = m,
                                onEdit = {
                                    interventoInModifica = m
                                    officinaAperta = true
                                },
                                onDelete = {
                                    daCancellare = "questo intervento" to {
                                        viewModel.deleteMaintenance(m)
                                    }
                                }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // ---------------------------------------------------------- dialoghi

    if (mezzoInModifica) {
        VehicleDialog(
            existing = state.selectedVehicle,
            onDismiss = { mezzoInModifica = false },
            onSave = { nome, targa, attivo ->
                viewModel.saveVehicle(
                    name = nome,
                    plate = targa,
                    id = state.selectedVehicleId ?: 0L,
                    active = attivo
                )
                mezzoInModifica = false
            }
        )
    }

    if (rifornimentoAperto) {
        FuelStopDialog(
            existing = rifornimentoInModifica,
            today = oggi,
            lastOdometerKm = state.lastOdometerKm,
            onDismiss = { rifornimentoAperto = false },
            onSave = { data, litri, importo, km, pieno, distributore ->
                viewModel.saveFuelStop(
                    date = data,
                    liters = litri,
                    amountCents = importo,
                    odometerKm = km,
                    fullTank = pieno,
                    station = distributore,
                    id = rifornimentoInModifica?.id ?: 0L
                )
                rifornimentoAperto = false
            }
        )
    }

    if (speseAperto) {
        ExpenseDialog(
            existing = spesaInModifica,
            today = oggi,
            onDismiss = { speseAperto = false },
            onSave = { data, tipo, importo, luogo ->
                viewModel.saveExpense(
                    date = data,
                    type = tipo,
                    amountCents = importo,
                    place = luogo,
                    id = spesaInModifica?.id ?: 0L
                )
                speseAperto = false
            }
        )
    }

    if (officinaAperta) {
        MaintenanceDialog(
            existing = interventoInModifica,
            today = oggi,
            lastOdometerKm = state.lastOdometerKm,
            suggestNextKm = { tipo, km -> viewModel.suggestNextKm(tipo, km) },
            onDismiss = { officinaAperta = false },
            onSave = { data, tipo, descrizione, km, costo, officina, prossimaData, prossimiKm ->
                viewModel.saveMaintenance(
                    date = data,
                    type = tipo,
                    description = descrizione,
                    odometerKm = km,
                    amountCents = costo,
                    workshop = officina,
                    nextDueDate = prossimaData,
                    nextDueKm = prossimiKm,
                    id = interventoInModifica?.id ?: 0L
                )
                officinaAperta = false
            }
        )
    }

    daCancellare?.let { (cosa, azione) ->
        AlertDialog(
            onDismissRequest = { daCancellare = null },
            title = { Text("Cancellare?") },
            text = { Text("Stai per togliere " + cosa + ". Non si torna indietro.") },
            confirmButton = {
                TextButton(onClick = {
                    azione()
                    daCancellare = null
                }) { Text("CANCELLA") }
            },
            dismissButton = {
                TextButton(onClick = { daCancellare = null }) { Text("LASCIA STARE") }
            }
        )
    }
}

// ------------------------------------------------------------- pezzi

@Composable
private fun EmptyVehicle(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Nessun mezzo registrato",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Aggiungi il furgone o l'auto aziendale: da li' in poi si segnano " +
                "rifornimenti, pedaggi e interventi in officina.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(72.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text("  AGGIUNGI IL MEZZO")
        }
    }
}

@Composable
private fun DueCard(dues: List<MaintenanceDue>) {
    val scadute = dues.any { it.isOverdue }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (scadute) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (scadute) "Scadenze passate" else "Scadenze vicine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            dues.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(d.type.label)
                    Text(
                        d.shortLabel,
                        fontWeight = if (d.isOverdue) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(totals: VehicleTotals) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (totals.isEmpty) {
                Text(
                    "Ancora niente in questo periodo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            TotalRow("Spesa totale", MoneyFormat.formatEuro(totals.totalCents), grande = true)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (totals.fuelStops > 0) {
                TotalRow("Carburante", MoneyFormat.formatEuro(totals.fuelCents))
                TotalRow("Litri", MoneyFormat.formatDecimal(totals.liters, 2))
            }
            if (totals.tollCents > 0) {
                TotalRow("Pedaggi", MoneyFormat.formatEuro(totals.tollCents))
            }
            if (totals.parkingCents > 0) {
                TotalRow("Parcheggi", MoneyFormat.formatEuro(totals.parkingCents))
            }
            if (totals.otherExpenseCents > 0) {
                TotalRow("Altre spese", MoneyFormat.formatEuro(totals.otherExpenseCents))
            }
            if (totals.maintenanceCents > 0) {
                TotalRow("Officina", MoneyFormat.formatEuro(totals.maintenanceCents))
            }

            if (totals.travelKm > 0 || totals.odometerKm > 0) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (totals.travelKm > 0) {
                    TotalRow("Km dalle giornate", totals.travelKm.toString() + " km")
                }
                if (totals.odometerKm > 0) {
                    TotalRow("Km dal contatore", totals.odometerKm.toString() + " km")
                }
            }

            // Il consumo compare solo se e' stato misurato davvero, cioe' se
            // c'e' almeno un tratto fra due pieni con il contachilometri.
            if (totals.consumptions.isNotEmpty()) {
                TotalRow(
                    "Consumo",
                    MoneyFormat.formatDecimal(totals.averageLitersPer100Km, 2) + " l/100 km"
                )
            }
            if (totals.effectiveKm > 0) {
                TotalRow(
                    "Costo al km",
                    MoneyFormat.formatCents(Math.round(totals.costPerKmCents)) + " euro"
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, grande: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (grande) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyLarge,
            color = if (grande) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (grande) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyLarge,
            fontWeight = if (grande) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    amount: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Modifica")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Cancella")
        }
    }
    HorizontalDivider()
}

@Composable
private fun FuelRow(stop: FuelStop, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dettagli = buildList {
        add(MoneyFormat.formatDecimal(stop.liters, 2) + " litri")
        stop.pricePerLiter?.let { add(MoneyFormat.formatDecimal(it, 3) + "/l") }
        if (!stop.fullTank) add("rabbocco")
        stop.odometerKm?.let { add(it.toString() + " km") }
        stop.station?.let { add(it) }
    }.joinToString("  -  ")

    ListRow(
        title = stop.date.format(SHORT_DATE),
        subtitle = dettagli,
        amount = MoneyFormat.formatCents(stop.amountCents),
        onEdit = onEdit,
        onDelete = onDelete
    )
}

@Composable
private fun ExpenseRow(expense: VehicleExpense, onEdit: () -> Unit, onDelete: () -> Unit) {
    ListRow(
        title = expense.date.format(SHORT_DATE) + "  " + expense.type.label,
        subtitle = expense.place.orEmpty(),
        amount = MoneyFormat.formatCents(expense.amountCents),
        onEdit = onEdit,
        onDelete = onDelete
    )
}

@Composable
private fun MaintenanceRow(
    maintenance: Maintenance,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dettagli = buildList {
        maintenance.description?.let { add(it) }
        maintenance.odometerKm?.let { add(it.toString() + " km") }
        maintenance.workshop?.let { add(it) }
    }.joinToString("  -  ")

    ListRow(
        title = maintenance.date.format(SHORT_DATE) + "  " + maintenance.type.label,
        subtitle = dettagli,
        // Un intervento in garanzia non ha costo: si scrive un trattino,
        // non uno zero che sembrerebbe un prezzo.
        amount = maintenance.amountCents?.let { MoneyFormat.formatCents(it) } ?: "-",
        onEdit = onEdit,
        onDelete = onDelete
    )
}
