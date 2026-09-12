package it.diario.lavorativo.ui.sites

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
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onReactivate: () -> Unit
) {
    val archived = site.status == SiteStatus.TERMINATO
    Card(
        onClick = onClick,
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
