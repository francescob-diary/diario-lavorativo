package it.diario.lavorativo.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.ui.photos.PhotoThumbnail
import java.time.Duration
import java.time.format.DateTimeFormatter

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * La giornata come e' stata compilata, da leggere e basta. Si arriva qui dal
 * calendario; per cambiare qualcosa c'e' MODIFICA, che apre la scheda
 * completa. I pulsanti per registrare stanno nella schermata Oggi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySummaryScreen(
    epochDay: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenPhotos: () -> Unit,
    viewModel: DayDetailViewModel = viewModel(
        key = "riepilogo-" + epochDay.toString(),
        factory = DayDetailViewModel.factory(epochDay)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.date.format(LONG_DATE).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            !state.exists -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nessuna giornata registrata.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("REGISTRALA")
                }
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (state.dayType != DayType.LAVORO) {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            dayTypeLabel(state.dayType, 1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // Orari e ore in evidenza.
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                (state.startTime?.format(HHMM) ?: "--:--") + " - " +
                                    (state.endTime?.format(HHMM) ?: if (state.isRunning) "in corso" else "--:--"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Nette " + DurationFormat.short(state.summary.net) +
                                    (if (!state.summary.overtime.isZero)
                                        "  ·  straord. " + DurationFormat.short(state.summary.overtime) else ""),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (state.breaks.isNotEmpty()) {
                                Text(
                                    "Pause: " + state.breaks.joinToString(", ") { b ->
                                        b.startTime.atZone(state.zone).toLocalTime().format(HHMM) + "-" +
                                            (b.endTime?.atZone(state.zone)?.toLocalTime()?.format(HHMM) ?: "...")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Block("Cantieri") {
                        if (state.extraSites.isEmpty()) {
                            Line(state.site?.displayLabel ?: "Nessun cantiere")
                            state.site?.workInProgress?.takeIf { it.isNotBlank() }?.let {
                                Small(it)
                            }
                        } else {
                            HoursLine(state.site?.name ?: "Principale", state.mainSiteHours)
                            state.extraSites.forEach { HoursLine(it.site.name, it.duration) }
                        }
                    }
                }

                if (state.description.isNotBlank()) {
                    Block("Lavoro svolto") { Line(state.description) }
                }

                if (state.activities.isNotEmpty()) {
                    Block("Lavorazioni") {
                        state.activities.forEach { a ->
                            Line("- " + a.description +
                                (a.quantity?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
                        }
                    }
                }

                if (state.trips.isNotEmpty()) {
                    Block("Spostamenti") {
                        state.trips.forEach { t ->
                            val orari = listOfNotNull(
                                t.departTime?.atZone(state.zone)?.toLocalTime()?.format(HHMM),
                                t.arriveTime?.atZone(state.zone)?.toLocalTime()?.format(HHMM)
                            ).joinToString("-")
                            Line(t.route.ifBlank { "Spostamento" } + if (orari.isNotEmpty()) "  ($orari)" else "")
                        }
                    }
                }

                if (state.travelKm.isNotBlank()) {
                    Block("Chilometri") { Line(state.travelKm + " km") }
                }

                if (state.notes.isNotBlank()) {
                    Block("Note") { Line(state.notes) }
                }

                if (state.photos.isNotEmpty()) {
                    Block("Foto (" + state.photos.size + ")") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.photos.forEach { foto ->
                                PhotoThumbnail(
                                    photo = foto,
                                    onClick = onOpenPhotos,
                                    modifier = Modifier.width(96.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("MODIFICA LA GIORNATA")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(2.dp))
    content()
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HoursLine(name: String, hours: Duration) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(DurationFormat.short(hours), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
