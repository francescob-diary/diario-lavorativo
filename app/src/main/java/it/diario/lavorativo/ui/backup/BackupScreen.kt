package it.diario.lavorativo.ui.backup

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Backup e ripristino.
 *
 * Il file lo si salva dove si vuole tramite il selettore del sistema:
 * cartella Download, chiavetta, Drive. Nessun permesso da chiedere e
 * nessun posto deciso dall'app: il backup e' roba dell'utente, deve poterlo
 * mettere dove gli pare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val salvaFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.writeBackup(it) }
    }

    val apriFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.inspectBackup(it) }
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
        topBar = {
            TopAppBar(
                title = { Text("Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
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
            Text(
                text = "Salva una copia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Un solo file con dentro tutto il diario. Scegli tu dove " +
                    "metterlo: Download, chiavetta o Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Includi foto e note vocali")
                    Text(
                        text = "Il file diventa molto piu' grande, ma il backup e' completo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.includeMedia,
                    onCheckedChange = viewModel::setIncludeMedia
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { salvaFile.launch(viewModel.suggestedFileName()) },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("SALVA IL BACKUP")
            }

            state.lastBackupLabel?.let { riga ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ultimo salvataggio: " + riga,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.working) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "  " + state.progressLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Ripristina",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Il ripristino sostituisce tutto quello che c'e' adesso " +
                        "nel diario. Quello che non e' nel backup si perde. Se hai " +
                        "dubbi, salva prima una copia di quello che hai ora.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { apriFile.launch(arrayOf("application/zip", "*/*")) },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("SCEGLI UN FILE DI BACKUP")
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Conferma: prima si dice cosa c'e' dentro, poi si chiede.
    state.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            title = { Text("Ripristinare questo backup?") },
            text = {
                Column {
                    Text("Dentro il file ci sono:")
                    Spacer(Modifier.height(8.dp))
                    Text("- " + pending.manifest.workDays.toString() + " giornate")
                    Text("- " + pending.manifest.sites.toString() + " cantieri")
                    Text("- " + pending.manifest.photos.toString() + " foto")
                    Text("- " + pending.manifest.voiceNotes.toString() + " note vocali")
                    Spacer(Modifier.height(8.dp))
                    Text("Periodo: " + pending.periodLabel)
                    if (!pending.manifest.includesMedia) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Questo backup non contiene i file di foto e note " +
                                "vocali: le righe torneranno, ma senza i file.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Il diario di adesso viene sostituito.",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }) {
                    Text("RIPRISTINA")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) { Text("ANNULLA") }
            }
        )
    }
}
