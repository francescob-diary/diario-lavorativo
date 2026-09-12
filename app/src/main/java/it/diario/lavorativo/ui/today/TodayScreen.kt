package it.diario.lavorativo.ui.today

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.util.Formatters
import it.diario.lavorativo.domain.model.Site
import androidx.compose.foundation.layout.size
import it.diario.lavorativo.ui.components.BigActionButton
import it.diario.lavorativo.ui.theme.VerdeAttivo
import java.time.LocalTime

/**
 * Schermata OGGI.
 *
 * Deve rispondere a colpo d'occhio a due domande:
 *  1) sto lavorando oppure no?
 *  2) quante ore ho fatto finora?
 */
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    onOpenEntries: (java.time.LocalDate) -> Unit = {},
    onOpenPhotos: (java.time.LocalDate) -> Unit = {},
    onOpenVoiceNotes: (java.time.LocalDate) -> Unit = {},
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Il permesso di posizione si chiede solo quando l'utente tocca "Dove sono".
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) viewModel.detectSite()
    }
    val requestDetect: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Header(state)

            state.unclosedPreviousDay?.let { previous ->
                Spacer(Modifier.height(12.dp))
                UnclosedDayBanner(
                    text = "Giornata del ${Formatters.shortDate(previous.date)} ancora aperta.",
                    onClose = viewModel::closePreviousDay
                )
            }

            Spacer(Modifier.height(16.dp))
            SummaryCard(
                state = state,
                onEditStart = { viewModel.showDialog(TodayDialog.MODIFICA_INGRESSO) },
                onEditEnd = { viewModel.showDialog(TodayDialog.MODIFICA_USCITA) }
            )

            Spacer(Modifier.height(12.dp))
            SiteRow(
                siteLabel = state.day?.site?.displayLabel,
                onClick = { viewModel.showDialog(TodayDialog.SCELTA_CANTIERE) }
            )

            Spacer(Modifier.height(12.dp))
            SiteDetectionCard(
                suggestion = state.suggestion,
                onDetect = requestDetect,
                onAccept = viewModel::acceptSuggestion,
                onDismiss = viewModel::clearSuggestion
            )

            Spacer(Modifier.height(20.dp))
            MainActions(
                state = state,
                onStart = {
                    if (state.activeSites.isEmpty()) {
                        viewModel.startDay(null)
                    } else {
                        viewModel.showDialog(TodayDialog.SCELTA_CANTIERE)
                    }
                },
                onEnd = viewModel::endDay,
                onToggleBreak = { viewModel.toggleBreak() }
            )

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { onOpenEntries(state.date) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("LAVORI, EVENTI E CONTATTI")
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onOpenPhotos(state.date) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("FOTO")
                }
                OutlinedButton(
                    onClick = { onOpenVoiceNotes(state.date) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("NOTE VOCALI")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    when (state.dialog) {
        TodayDialog.SCELTA_CANTIERE -> SitePickerDialog(
            sites = state.activeSites,
            onSelect = viewModel::selectSite,
            onNewSite = { viewModel.showDialog(TodayDialog.NUOVO_CANTIERE) },
            onDismiss = viewModel::dismissDialog
        )

        TodayDialog.NUOVO_CANTIERE -> NewSiteDialog(
            onConfirm = viewModel::createSite,
            onDismiss = viewModel::dismissDialog
        )

        TodayDialog.MODIFICA_INGRESSO -> TimeCorrectionDialog(
            title = "Correggi ingresso",
            initial = state.day?.startTime?.atZone(state.zone)?.toLocalTime() ?: LocalTime.of(7, 30),
            onConfirm = viewModel::updateStartTime,
            onDismiss = viewModel::dismissDialog
        )

        TodayDialog.MODIFICA_USCITA -> TimeCorrectionDialog(
            title = "Correggi uscita",
            initial = state.day?.endTime?.atZone(state.zone)?.toLocalTime() ?: LocalTime.of(17, 0),
            onConfirm = viewModel::updateEndTime,
            onDismiss = viewModel::dismissDialog
        )

        TodayDialog.NESSUNA -> Unit
    }
}

@Composable
private fun Header(state: TodayUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "DIARIO LAVORATIVO",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = Formatters.fullDate(state.date),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun UnclosedDayBanner(text: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            TextButton(onClick = onClose) { Text("Chiudi") }
        }
    }
}

@Composable
private fun SummaryCard(
    state: TodayUiState,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit
) {
    val summary = state.summary
    val statusText = when {
        state.isOnBreak -> "IN PAUSA"
        state.isDayRunning -> "AL LAVORO"
        state.isDayClosed -> "GIORNATA CONCLUSA"
        else -> "NON INIZIATA"
    }
    val statusColor = when {
        state.isOnBreak -> MaterialTheme.colorScheme.secondary
        state.isDayRunning -> VerdeAttivo
        state.isDayClosed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Formatters.durationClock(summary.net),
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "ore lavorate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            TimeRow(
                label = "Ingresso",
                value = Formatters.time(state.day?.startTime, state.zone),
                onEdit = onEditStart.takeIf { state.isDayStarted }
            )
            TimeRow(
                label = "Uscita",
                value = Formatters.time(state.day?.endTime, state.zone),
                onEdit = onEditEnd.takeIf { state.isDayStarted }
            )
            TimeRow(
                label = "Pause",
                value = Formatters.duration(summary.breaks),
                onEdit = null
            )
            TimeRow(
                label = "Totale presenza",
                value = Formatters.duration(summary.gross),
                onEdit = null
            )
            if (state.settings.overtimeEnabled && summary.overtime.toMinutes() > 0) {
                TimeRow(
                    label = "Straordinario",
                    value = "+" + Formatters.duration(summary.overtime),
                    onEdit = null,
                    highlight = true
                )
            }
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    value: String,
    onEdit: (() -> Unit)?,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Modifica $label")
            }
        } else {
            // Spazio equivalente all'icona, per tenere allineate le righe.
            Spacer(Modifier.width(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteRow(siteLabel: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Domain, contentDescription = null)
            Column(modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)) {
                Text(
                    text = "Cantiere",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = siteLabel ?: "Non selezionato",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Icon(Icons.Filled.Edit, contentDescription = "Cambia cantiere")
        }
    }
}

@Composable
private fun MainActions(
    state: TodayUiState,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onToggleBreak: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !state.isDayStarted -> BigActionButton(
                text = "INIZIA GIORNATA",
                icon = Icons.Filled.PlayArrow,
                containerColor = VerdeAttivo,
                contentColor = Color.White,
                onClick = onStart
            )

            state.isDayRunning -> {
                BigActionButton(
                    text = "TERMINA GIORNATA",
                    icon = Icons.Filled.Stop,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = onEnd
                )
                BigActionButton(
                    text = if (state.isOnBreak) "FINE PAUSA" else "INIZIA PAUSA",
                    icon = Icons.Outlined.Coffee,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onToggleBreak,
                    height = 64
                )
            }

            else -> Text(
                text = "Giornata conclusa. Puoi ancora correggere gli orari qui sopra.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SitePickerDialog(
    sites: List<Site>,
    onSelect: (Long?) -> Unit,
    onNewSite: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli il cantiere") },
        text = {
            Column {
                sites.forEach { site ->
                    TextButton(
                        onClick = { onSelect(site.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = site.displayLabel,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Senza cantiere", modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider()
                TextButton(onClick = onNewSite, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Nuovo cantiere", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun NewSiteDialog(
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo cantiere") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Citta'") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, city) },
                enabled = name.isNotBlank()
            ) { Text("Salva e usa") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeCorrectionDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

/**
 * Riconoscimento del cantiere tramite GPS.
 *
 * Volutamente un suggerimento e non un'automazione: il cantiere non viene mai
 * cambiato senza un tocco dell'utente, cosi' un fix impreciso non falsa il diario.
 */
@Composable
private fun SiteDetectionCard(
    suggestion: SiteSuggestion,
    onDetect: () -> Unit,
    onAccept: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (suggestion) {
                SiteSuggestion.Idle -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Riconoscimento cantiere",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Usa il GPS per capire su quale cantiere sei.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = onDetect, modifier = Modifier.height(52.dp)) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Dove sono")
                        }
                    }
                }

                SiteSuggestion.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Cerco il segnale GPS...", style = MaterialTheme.typography.bodyLarge)
                }

                is SiteSuggestion.Found -> {
                    Text("Sembri essere qui:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(suggestion.site.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "a circa " + suggestion.distanceMeters.toString() + " metri dal punto salvato",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { onAccept(suggestion.site.id) },
                            modifier = Modifier.height(52.dp)
                        ) { Text("Conferma cantiere") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onDismiss) { Text("No") }
                    }
                }

                is SiteSuggestion.Several -> {
                    Text(
                        "Piu' cantieri vicini. Quale?",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    suggestion.candidates.forEach { candidate ->
                        TextButton(
                            onClick = { onAccept(candidate.site.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                candidate.site.name + " - " +
                                    candidate.distanceMeters.toInt().toString() + " m",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDismiss) { Text("Nessuno di questi") }
                }

                is SiteSuggestion.OutOfRange -> {
                    Text(
                        "Nessun cantiere nel raggio impostato.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (suggestion.nearestName != null && suggestion.distanceMeters != null) {
                        Text(
                            "Il piu' vicino e' " + suggestion.nearestName + ", a circa " +
                                suggestion.distanceMeters.toString() + " metri.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDetect) { Text("Riprova") }
                        TextButton(onClick = onDismiss) { Text("Chiudi") }
                    }
                }

                SiteSuggestion.NoGeolocatedSites -> {
                    Text(
                        "Nessun cantiere ha una posizione salvata.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Apri la scheda di un cantiere, stando sul posto, e premi " +
                            "Registra qui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) { Text("Ho capito") }
                }

                is SiteSuggestion.Unavailable -> {
                    Text(suggestion.reason, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDetect) { Text("Riprova") }
                        TextButton(onClick = onDismiss) { Text("Chiudi") }
                    }
                }
            }
        }
    }
}
