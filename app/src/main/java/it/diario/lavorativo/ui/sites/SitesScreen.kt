package it.diario.lavorativo.ui.sites

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.service.SiteDay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus

/**
 * Anagrafica cantieri (requisito 6). Sostituisce il segnaposto della fase 1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    onOpenSite: (Long) -> Unit,
    onOpenDay: (java.time.LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SitesViewModel = viewModel(factory = SitesViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenSite(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuovo cantiere") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("Cantieri", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = state.activeCount.toString() + " attivi su " + state.sites.size.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Cerca per nome, citta o committente") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = state.showTerminated,
                onClick = viewModel::toggleShowTerminated,
                label = { Text("Mostra anche archiviati") }
            )
            Spacer(Modifier.height(8.dp))

            if (state.isEmpty) {
                EmptySites()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.visibleSites, key = { it.id }) { site ->
                        SiteCard(
                            site = site,
                            days = state.daysBySite[site.id].orEmpty(),
                            onOpenDay = onOpenDay,
                            onClick = { onOpenSite(site.id) },
                            onArchive = { viewModel.archive(site) },
                            onReactivate = { viewModel.reactivate(site) }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteCard(
    site: Site,
    days: List<SiteDay>,
    onOpenDay: (java.time.LocalDate) -> Unit,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onReactivate: () -> Unit
) {
    val archived = site.status == SiteStatus.TERMINATO
    var aperto by rememberSaveable(site.id) { mutableStateOf(false) }
    Card(
        onClick = { aperto = !aperto },
        modifier = Modifier.fillMaxWidth(),
        colors = if (archived) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (site.hasPosition) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Posizione registrata",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Modifica cantiere")
                }
            }
            site.fullAddress?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            site.client?.let {
                Text(
                    "Committente: " + it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Riepilogo dei giorni lavorati; toccando la scheda si aprono.
            Spacer(Modifier.height(6.dp))
            val totale = days.fold(java.time.Duration.ZERO) { a, d -> a.plus(d.hours) }
            Text(
                text = if (days.isEmpty()) "Nessun giorno lavorato" else
                    days.size.toString() + (if (days.size == 1) " giorno" else " giorni") +
                        "  ·  " + DurationFormat.short(totale) +
                        "  ·  ultimo " + days.first().date.format(GIORNO_BREVE),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (aperto && days.isNotEmpty()) {
                SiteDaysTable(days = days, onOpenDay = onOpenDay)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (archived) "Archiviato" else "Attivo",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (archived) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.weight(1f))
                if (archived) {
                    TextButton(onClick = onReactivate) { Text("Riattiva") }
                } else {
                    TextButton(onClick = onArchive) { Text("Archivia") }
                }
            }
        }
    }
}

@Composable
private fun EmptySites() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Nessun cantiere", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Aggiungi il primo cantiere col pulsante in basso. Se lo registri " +
                    "stando sul posto, l'app potra' riconoscerlo da sola quando ci torni.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private val GIORNO_BREVE: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d/MM")

private val MESE: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ITALIAN)

/**
 * Tabella dei giorni lavorati su un cantiere, mese per mese: giorno,
 * iniziale del giorno della settimana e ore. Un tocco apre la giornata.
 */
@Composable
private fun SiteDaysTable(days: List<SiteDay>, onOpenDay: (java.time.LocalDate) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        days.groupBy { java.time.YearMonth.from(it.date) }.forEach { (mese, giorni) ->
            val oreMese = giorni.fold(java.time.Duration.ZERO) { a, d -> a.plus(d.hours) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    mese.format(MESE).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    giorni.size.toString() + " gg  ·  " + DurationFormat.short(oreMese),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            giorni.sortedBy { it.date }.chunked(4).forEach { riga ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    riga.forEach { g ->
                        OutlinedButton(
                            onClick = { onOpenDay(g.date) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    g.date.dayOfMonth.toString() + " " + LETTERE[g.date.dayOfWeek.value - 1],
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                                Text(
                                    DurationFormat.short(g.hours),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    repeat(4 - riga.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val LETTERE = listOf("L", "M", "M", "G", "V", "S", "D")
