package it.diario.lavorativo.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.WorkBreak
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Quale orario si sta scegliendo nel selettore. */
private enum class TimeTarget { INGRESSO, USCITA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    epochDay: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenEntries: () -> Unit = {},
    onOpenPhotos: () -> Unit = {},
    onOpenVoiceNotes: () -> Unit = {},
    viewModel: DayDetailViewModel = viewModel(
        key = "day-" + epochDay.toString(),
        factory = DayDetailViewModel.factory(epochDay)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(monthLabel(java.time.YearMonth.from(state.date))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (state.exists) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina giornata")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            !state.exists -> MissingDay(
                dateLabel = state.date.format(LONG_DATE),
                onCreateWork = viewModel::createWorkDay,
                onPickType = viewModel::onDayType,
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = state.date.format(LONG_DATE),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                SummaryCard(state)

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onOpenEntries,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("LAVORI, EVENTI E CONTATTI")
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onOpenPhotos,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("FOTO")
                    }
                    OutlinedButton(
                        onClick = onOpenVoiceNotes,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("NOTE VOCALI")
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Orari", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { timeTarget = TimeTarget.INGRESSO },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ingresso " + (state.startTime?.format(TIME_FORMAT) ?: "--:--"))
                    }
                    OutlinedButton(
                        onClick = { timeTarget = TimeTarget.USCITA },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning
                    ) {
                        Text("Uscita " + (state.endTime?.format(TIME_FORMAT) ?: "--:--"))
                    }
                }
                if (state.crossesMidnight) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Turno oltre la mezzanotte: l'uscita e' del giorno dopo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.isRunning) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Giornata ancora aperta: chiudila dalla schermata Oggi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text("Tipo di giornata", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DayTypeRow(selected = state.dayType, onSelect = viewModel::onDayType)

                Spacer(Modifier.height(16.dp))

                SitePicker(
                    site = state.site,
                    sites = state.availableSites,
                    onSelect = viewModel::onSite
                )

                Spacer(Modifier.height(16.dp))

                if (state.breaks.isNotEmpty()) {
                    Text("Pause", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    state.breaks.forEach { pause ->
                        BreakRow(pause = pause, onDelete = { viewModel.deleteBreak(pause.id) })
                    }
                    Spacer(Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescription,
                    label = { Text("Lavoro svolto") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text("Note") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Chilometri del mezzo aziendale. Lasciato vuoto non conta
                // come zero: nei totali del mese la giornata senza il dato
                // non abbassa la media.
                OutlinedTextField(
                    value = state.travelKm,
                    onValueChange = viewModel::onTravelKm,
                    label = { Text("Chilometri percorsi") },
                    placeholder = { Text("vuoto se non li hai segnati") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (state.dirty) "SALVA MODIFICHE" else "NESSUNA MODIFICA")
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    timeTarget?.let { target ->
        val initial = when (target) {
            TimeTarget.INGRESSO -> state.startTime ?: LocalTime.of(7, 30)
            TimeTarget.USCITA -> state.endTime ?: LocalTime.of(17, 0)
        }
        TimePickerDialog(
            title = if (target == TimeTarget.INGRESSO) "Ora di ingresso" else "Ora di uscita",
            initial = initial,
            onDismiss = { timeTarget = null },
            onConfirm = { picked ->
                if (target == TimeTarget.INGRESSO) {
                    viewModel.onStartTime(picked)
                } else {
                    viewModel.onEndTime(picked)
                }
                timeTarget = null
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la giornata?") },
            text = { Text("Verranno cancellati orari, pause e note di questa giornata.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteDay(onBack)
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun SummaryCard(state: DayDetailUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SummaryItem("Lordo", DurationFormat.short(state.summary.gross), Modifier.weight(1f))
            SummaryItem("Pause", DurationFormat.short(state.summary.breaks), Modifier.weight(1f))
            SummaryItem("Netto", DurationFormat.short(state.summary.net), Modifier.weight(1f))
            SummaryItem(
                label = if (state.summary.overtime.isZero) "Mancano" else "Straord.",
                value = if (state.summary.overtime.isZero) {
                    DurationFormat.short(state.summary.deficit)
                } else {
                    DurationFormat.short(state.summary.overtime)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DayTypeRow(selected: DayType, onSelect: (DayType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = {
                    Text(
                        when (type) {
                            DayType.LAVORO -> "Lavoro"
                            DayType.FERIE -> "Ferie"
                            DayType.PERMESSO -> "Permesso"
                            DayType.MALATTIA -> "Malattia"
                            DayType.FESTIVO -> "Festivo"
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SitePicker(site: Site?, sites: List<Site>, onSelect: (Site?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = site?.name ?: "Nessun cantiere",
            onValueChange = {},
            readOnly = true,
            label = { Text("Cantiere") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Nessun cantiere") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            sites.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.name) },
                    onClick = {
                        onSelect(candidate)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BreakRow(pause: WorkBreak, onDelete: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val from = pause.startTime.atZone(zone).toLocalTime().format(TIME_FORMAT)
    val to = pause.endTime?.atZone(zone)?.toLocalTime()?.format(TIME_FORMAT)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (to != null) from + " - " + to else from + " - in corso",
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Elimina pausa",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun MissingDay(
    dateLabel: String,
    onCreateWork: () -> Unit,
    onPickType: (DayType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nessuna giornata registrata per questa data.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCreateWork,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("REGISTRA GIORNATA DI LAVORO")
        }
        Spacer(Modifier.height(16.dp))
        Text("oppure segna", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DayType.FERIE, DayType.PERMESSO, DayType.MALATTIA).forEach { type ->
                AssistChip(
                    onClick = { onPickType(type) },
                    label = {
                        Text(
                            when (type) {
                                DayType.FERIE -> "Ferie"
                                DayType.PERMESSO -> "Permesso"
                                else -> "Malattia"
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(pickerState.hour, pickerState.minute))
            }) { Text("Conferma") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
