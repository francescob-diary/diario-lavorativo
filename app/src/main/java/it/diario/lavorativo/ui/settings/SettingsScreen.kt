package it.diario.lavorativo.ui.settings

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.util.Formatters
import java.time.Duration

/**
 * Impostazioni (requisito 18). In fase 1 sono attive le voci che servono
 * davvero ai calcoli: nome utente, orario standard e gestione straordinario.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenExport: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenVehicle: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var nameField by remember { mutableStateOf(settings.userName) }

    // Allinea il campo quando arriva il valore salvato.
    LaunchedEffect(settings.userName) {
        if (nameField != settings.userName) nameField = settings.userName
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = {
                        nameField = it
                        viewModel.setUserName(it)
                    },
                    label = { Text("Nome utente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text("Orario lavorativo standard", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Usato per calcolare lo straordinario.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { viewModel.changeStandardMinutes(-15) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Riduci di 15 minuti")
                    }
                    Text(
                        text = Formatters.duration(
                            Duration.ofMinutes(settings.standardWorkMinutes.toLong())
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    )
                    FilledTonalIconButton(onClick = { viewModel.changeStandardMinutes(15) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Aumenta di 15 minuti")
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Calcola straordinario", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Mostra le ore oltre l'orario standard.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.overtimeEnabled,
                        onCheckedChange = viewModel::setOvertimeEnabled
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onOpenExport,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("ESPORTA E INVIA")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Rapportino settimanale sul modulo dell'azienda, oppure PDF, CSV " +
                "e foglio di calcolo per qualsiasi periodo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenVehicle,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("MEZZO AZIENDALE")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Rifornimenti, pedaggi, parcheggi e officina. Tiene il conto " +
                "dei consumi e avvisa quando si avvicina il tagliando o la revisione.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenBackup,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("BACKUP E RIPRISTINO")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Una copia di tutto il diario in un solo file, da mettere dove " +
                "vuoi. Falla ogni tanto: un telefono si rompe o si perde.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Formato data e ora e tema arrivano nelle fasi successive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
    }
}
