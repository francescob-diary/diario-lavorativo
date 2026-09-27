package it.diario.lavorativo.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.DaySite
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.Trip
import it.diario.lavorativo.domain.service.TimeTextParser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Pezzi della giornata che si modificano subito, senza passare dal
 * pulsante "salva": i cantieri in piu' e gli spostamenti. Stanno qui
 * perche' li usano sia la schermata Oggi sia il dettaglio del giorno.
 */

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Richiesta di salvataggio di un cantiere in piu'. id 0 = nuovo. */
data class ExtraSiteInput(
    val id: Long,
    val siteId: Long,
    val minutes: Int,
    val description: String?
)

// ------------------------------------------------------------ cantieri

@Composable
fun ExtraSitesSection(
    mainSite: Site?,
    mainHours: Duration?,
    extraSites: List<DaySite>,
    availableSites: List<Site>,
    onSave: (ExtraSiteInput) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<DaySite?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(modifier) {
        Text("Cantieri della giornata", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))

        if (extraSites.isEmpty()) {
            Text(
                text = "Un solo cantiere" + (mainSite?.let { ": " + it.name } ?: "") +
                    ". Se hai lavorato anche altrove, aggiungilo con le ore fatte li'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SiteHoursRow(
                name = (mainSite?.name ?: "Cantiere principale") + " (principale)",
                hours = mainHours,
                onClick = null,
                onDelete = null
            )
            extraSites.forEach { ds ->
                SiteHoursRow(
                    name = ds.site.name,
                    hours = ds.duration,
                    onClick = { editing = ds },
                    onDelete = { onDelete(ds.id) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { adding = true },
            enabled = availableSites.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("AGGIUNGI UN ALTRO CANTIERE")
        }
    }

    if (adding || editing != null) {
        val current = editing
        ExtraSiteDialog(
            existing = current,
            sites = availableSites.filter { it.id != mainSite?.id },
            onDismiss = {
                adding = false
                editing = null
            },
            onConfirm = { input ->
                onSave(input.copy(id = current?.id ?: 0L))
                adding = false
                editing = null
            }
        )
    }
}

@Composable
private fun SiteHoursRow(
    name: String,
    hours: Duration?,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onClick != null) {
            TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                Text(name, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text(name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp))
        }
        Text(
            text = hours?.let { DurationFormat.short(it) } ?: "--",
            fontWeight = FontWeight.Bold
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Togli cantiere")
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun ExtraSiteDialog(
    existing: DaySite?,
    sites: List<Site>,
    onDismiss: () -> Unit,
    onConfirm: (ExtraSiteInput) -> Unit
) {
    var siteId by remember { mutableStateOf(existing?.site?.id ?: sites.firstOrNull()?.id) }
    var ore by remember {
        mutableStateOf(existing?.minutes?.let { TimeTextParser.formatHours(it) } ?: "")
    }
    var cosa by remember { mutableStateOf(existing?.description.orEmpty()) }
    val minuti = TimeTextParser.parseHours(ore)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Altro cantiere" else "Modifica cantiere") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sites.forEach { s ->
                    FilterChip(
                        selected = siteId == s.id,
                        onClick = { siteId = s.id },
                        label = { Text(s.displayLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (sites.isEmpty()) {
                    Text("Crea prima il cantiere nella sezione Cantieri.")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ore,
                    onValueChange = { ore = it },
                    label = { Text("Ore fatte li'") },
                    supportingText = { Text("Es. 4 oppure 3,5 oppure 3:30") },
                    isError = ore.isNotBlank() && minuti == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cosa,
                    onValueChange = { cosa = it },
                    label = { Text("Lavorazione (facoltativa)") },
                    supportingText = { Text("Vuota = il lavoro in corso del cantiere") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = siteId ?: return@TextButton
                    onConfirm(ExtraSiteInput(0L, id, minuti ?: 0, cosa.ifBlank { null }))
                },
                enabled = siteId != null && minuti != null && minuti > 0
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}

// ---------------------------------------------------------- spostamenti

@Composable
fun TripsSection(
    trips: List<Trip>,
    date: LocalDate,
    zone: ZoneId,
    onSave: (Trip) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<Trip?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(modifier) {
        Text("Spostamenti", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Restano nel diario: non vanno nel rapportino.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))

        trips.forEach { trip ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { editing = trip }, modifier = Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = trip.route.ifBlank { "Spostamento" },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = timeLabel(trip.departTime, zone) + " - " +
                                    timeLabel(trip.arriveTime, zone) +
                                    (trip.duration?.let { "  (" + DurationFormat.short(it) + ")" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(trip.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina spostamento")
                    }
                }
            }
        }

        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("AGGIUNGI SPOSTAMENTO")
        }
    }

    if (adding || editing != null) {
        TripDialog(
            existing = editing,
            date = date,
            zone = zone,
            onDismiss = {
                adding = false
                editing = null
            },
            onConfirm = { trip ->
                onSave(trip)
                adding = false
                editing = null
            }
        )
    }
}

private fun timeLabel(instant: Instant?, zone: ZoneId): String =
    instant?.atZone(zone)?.toLocalTime()?.format(HHMM) ?: "--:--"

@Composable
private fun TripDialog(
    existing: Trip?,
    date: LocalDate,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Trip) -> Unit
) {
    var da by remember { mutableStateOf(existing?.fromPlace.orEmpty()) }
    var a by remember { mutableStateOf(existing?.toPlace.orEmpty()) }
    var partenza by remember {
        mutableStateOf(existing?.departTime?.let { timeLabel(it, zone) } ?: "")
    }
    var arrivo by remember {
        mutableStateOf(existing?.arriveTime?.let { timeLabel(it, zone) } ?: "")
    }
    var note by remember { mutableStateOf(existing?.notes.orEmpty()) }

    val oraPartenza = TimeTextParser.parseClock(partenza)
    val oraArrivo = TimeTextParser.parseClock(arrivo)
    val partenzaOk = partenza.isBlank() || oraPartenza != null
    val arrivoOk = arrivo.isBlank() || oraArrivo != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuovo spostamento" else "Modifica spostamento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = da, onValueChange = { da = it },
                    label = { Text("Da (partenza)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = a, onValueChange = { a = it },
                    label = { Text("A (destinazione)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = partenza, onValueChange = { partenza = it },
                        label = { Text("Ora partenza") }, singleLine = true,
                        isError = !partenzaOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = arrivo, onValueChange = { arrivo = it },
                        label = { Text("Ora arrivo") }, singleLine = true,
                        isError = !arrivoOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        Trip(
                            id = existing?.id ?: 0L,
                            workDayId = existing?.workDayId ?: 0L,
                            departTime = oraPartenza?.let { toInstant(date, it, zone) },
                            arriveTime = oraArrivo?.let { t ->
                                // Arrivo prima della partenza: e' il giorno dopo.
                                val d = if (oraPartenza != null && t < oraPartenza) date.plusDays(1) else date
                                toInstant(d, t, zone)
                            },
                            fromPlace = da.trim().ifBlank { null },
                            toPlace = a.trim().ifBlank { null },
                            notes = note.trim().ifBlank { null }
                        )
                    )
                },
                enabled = partenzaOk && arrivoOk && (da.isNotBlank() || a.isNotBlank())
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}

private fun toInstant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
    date.atTime(time).atZone(zone).toInstant()
