package it.diario.lavorativo.ui.export

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
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.di.appContainer
import it.diario.lavorativo.domain.model.ExportFormat
import it.diario.lavorativo.domain.model.ExportScope

/**
 * Esportazione. Si sceglie il periodo, il formato e cosa metterci dentro,
 * poi il file viene preparato e passato alla condivisione di sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("Esporta") },
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
            SectionTitle("Periodo")
            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ExportScope.entries.forEachIndexed { index, scope ->
                    SegmentedButton(
                        selected = state.options.scope == scope,
                        onClick = { viewModel.selectScope(scope) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ExportScope.entries.size
                        )
                    ) {
                        Text(scopeLabel(scope))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.previous() },
                    enabled = state.canScroll
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Periodo precedente")
                }
                Text(
                    text = state.periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.next() },
                    enabled = state.canGoForward
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Periodo successivo")
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionTitle("Formato")
            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ExportFormat.entries.forEachIndexed { index, format ->
                    SegmentedButton(
                        selected = state.options.format == format,
                        onClick = { viewModel.selectFormat(format) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ExportFormat.entries.size
                        )
                    ) {
                        Text(format.extension.uppercase())
                    }
                }
            }

            // Il caso piu' importante merita di essere detto: settimana piu'
            // PDF non e' una tabella qualunque, e' il foglio che va in sede.
            if (state.options.scope == ExportScope.SETTIMANA &&
                state.options.format == ExportFormat.PDF
            ) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = "Esce sul modulo \"ORE DELLA SETTIMANA\", pronto da stampare " +
                            "e consegnare in sede.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionTitle("Cosa mettere dentro")
            Spacer(Modifier.height(4.dp))

            SwitchRow(
                label = "Riepilogo",
                checked = state.options.includeSummary,
                onChange = viewModel::toggleSummary
            )
            SwitchRow(
                label = "Giornate e orari",
                checked = state.options.includeDays,
                onChange = viewModel::toggleDays
            )
            SwitchRow(
                label = "Lavorazioni",
                checked = state.options.includeActivities,
                onChange = viewModel::toggleActivities
            )
            SwitchRow(
                label = "Eventi",
                checked = state.options.includeEvents,
                onChange = viewModel::toggleEvents
            )
            SwitchRow(
                label = "Comunicazioni",
                checked = state.options.includeCommunications,
                onChange = viewModel::toggleCommunications
            )

            Text(
                text = "Le comunicazioni sono escluse di proposito: nel documento che " +
                    "va in sede finirebbero anche i messaggi di altre persone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.export { file, format ->
                        // Si apre subito la condivisione: il file serve per
                        // mandarlo, non per restare nel telefono.
                        viewModel.shareLast { intent -> context.startActivity(intent) }
                    }
                },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                if (state.working) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("PREPARA E INVIA")
                }
            }

            state.lastFile?.let { file ->
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            appContainer(context).fileExporter
                                .openIntent(file, state.lastFormat)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("APRI " + file.name)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun scopeLabel(scope: ExportScope): String = when (scope) {
    ExportScope.SETTIMANA -> "Settimana"
    ExportScope.MESE -> "Mese"
    ExportScope.ANNO -> "Anno"
    ExportScope.TUTTO -> "Tutto"
}
