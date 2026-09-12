package it.diario.lavorativo.ui.sites

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus

/**
 * Scheda cantiere: dati anagrafici e posizione per il riconoscimento automatico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteEditScreen(
    siteId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SiteEditViewModel = viewModel(
        key = "site-" + siteId.toString(),
        factory = SiteEditViewModel.factory(siteId)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) viewModel.capturePosition()
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Nuovo cantiere" else "Modifica cantiere") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Nome cantiere") },
                singleLine = true,
                isError = state.error != null && state.name.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddress,
                label = { Text("Indirizzo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.city,
                onValueChange = viewModel::onCity,
                label = { Text("Citta") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.client,
                onValueChange = viewModel::onClient,
                label = { Text("Committente") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.company,
                onValueChange = viewModel::onCompany,
                label = { Text("Impresa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.contact,
                onValueChange = viewModel::onContact,
                label = { Text("Referente in cantiere") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = { Text("Telefono") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = { Text("Note") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            PositionCard(
                state = state,
                onCapture = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onClear = viewModel::clearPosition,
                onRadius = viewModel::onRadius
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stato:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(0.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    SiteStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { viewModel.onStatus(status) },
                            label = {
                                Text(if (status == SiteStatus.ATTIVO) "Attivo" else "Archiviato")
                            }
                        )
                    }
                }
            }

            if (!state.isNew && state.linkedWorkDays > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Giornate registrate su questo cantiere: " + state.linkedWorkDays.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text(
                    if (state.saving) "Salvataggio..." else "Salva cantiere",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il cantiere?") },
            text = {
                Text(
                    if (state.linkedWorkDays > 0) {
                        "Ci sono " + state.linkedWorkDays.toString() +
                            " giornate collegate. Le giornate restano, ma perderanno " +
                            "il riferimento al cantiere. Se vuoi conservarlo, archivialo invece " +
                            "di eliminarlo."
                    } else {
                        "L'operazione non si puo' annullare."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun PositionCard(
    state: SiteEditUiState,
    onCapture: () -> Unit,
    onClear: () -> Unit,
    onRadius: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Posizione del cantiere", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Premi il pulsante stando sul cantiere. L'app potra' poi riconoscerlo " +
                    "da sola quando inizi la giornata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(state.positionLabel, style = MaterialTheme.typography.bodyLarge)
            state.positionAccuracy?.let {
                Text(
                    "Precisione: circa " + it.toInt().toString() + " metri",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onCapture,
                    enabled = !state.capturingPosition,
                    modifier = Modifier.height(52.dp)
                ) {
                    if (state.capturingPosition) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text("Cerco il segnale...")
                    } else {
                        Icon(Icons.Filled.MyLocation, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text(if (state.hasPosition) "Aggiorna" else "Registra qui")
                    }
                }
                if (state.hasPosition) {
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = onClear) { Text("Rimuovi") }
                }
            }

            if (state.hasPosition) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Raggio di riconoscimento: " + state.radiusMeters.toString() + " metri",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Site.RADIUS_CHOICES.forEach { radius ->
                        FilterChip(
                            selected = state.radiusMeters == radius,
                            onClick = { onRadius(radius) },
                            label = { Text(radius.toString()) }
                        )
                    }
                }
            }
        }
    }
}
