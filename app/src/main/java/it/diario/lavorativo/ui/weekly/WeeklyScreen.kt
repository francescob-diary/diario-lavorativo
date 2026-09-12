package it.diario.lavorativo.ui.weekly

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WeeklyDayLine
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GIORNO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)
private val INTERVALLO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

/**
 * Foglio settimanale: la stessa griglia del cartellino cartaceo, cosi' chi
 * lo riceve in sede ritrova quello che si aspetta.
 */
@Composable
fun WeeklyScreen(
    modifier: Modifier = Modifier,
    onOpenDay: (java.time.LocalDate) -> Unit = {},
    viewModel: WeeklyViewModel = viewModel(factory = WeeklyViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report

    // Il permesso notifiche si chiede qui, dove il promemoria ha senso,
    // e non allo start dell'app dove sarebbe solo una richiesta a freddo.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousWeek) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Settimana precedente")
            }
            Text(
                text = "Settimana " + report.weekStart.format(INTERVALLO) +
                    " - " + report.weekEnd.format(INTERVALLO),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = viewModel::nextWeek, enabled = state.canGoForward) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Settimana successiva")
            }
        }

        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Column
        }

        Spacer(Modifier.height(12.dp))

        TotalsCard(
            worked = report.totals.workedDays,
            net = DurationFormat.short(report.totals.net),
            overtime = DurationFormat.short(report.totals.overtime)
        )

        if (report.incompleteDays.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Ci sono " + report.incompleteDays.size.toString() +
                        " giornate senza orario di uscita. Sistemale prima di consegnare.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        report.days.forEach { line ->
            DayRow(line = line, onClick = { onOpenDay(line.date) })
            Divider()
        }

        if (report.sites.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Cantieri", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(report.sites.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }

        if (report.notableEvents.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Da segnalare",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            report.notableEvents.forEach { event ->
                Text(
                    text = "- " + event.title,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotes,
            label = { Text("Note per la sede") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "La stampa in PDF arriva con la fase 11, sul modulo che " +
                    "usi in sede.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TotalsCard(worked: Int, net: String, overtime: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            TotalItem("Giorni", worked.toString(), Modifier.weight(1f))
            TotalItem("Ore nette", net, Modifier.weight(1f))
            TotalItem("Straordinario", overtime, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TotalItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DayRow(line: WeeklyDayLine, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = line.date.format(GIORNO),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            if (line.dayType != DayType.LAVORO) {
                Text(
                    text = when (line.dayType) {
                        DayType.FERIE -> "Ferie"
                        DayType.PERMESSO -> "Permesso"
                        DayType.MALATTIA -> "Malattia"
                        DayType.FESTIVO -> "Festivo"
                        DayType.LAVORO -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (line.startLabel != null) {
                Text(
                    text = line.startLabel + " - " + (line.endLabel ?: "aperta"),
                    style = MaterialTheme.typography.bodyMedium
                )
                line.siteName?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                if (line.work.isNotBlank()) {
                    Text(text = line.work, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!line.net.isZero) {
                Text(
                    text = DurationFormat.short(line.net),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!line.overtime.isZero) {
                Text(
                    text = "+" + DurationFormat.short(line.overtime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
