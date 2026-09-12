package it.diario.lavorativo.ui.entries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.SharedText
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.ui.history.LONG_DATE
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntriesScreen(
    epochDay: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sharedText: SharedText? = null,
    onSharedTextHandled: () -> Unit = {},
    viewModel: EntriesViewModel = viewModel(
        key = "entries-" + epochDay.toString(),
        factory = EntriesViewModel.factory(epochDay)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Testo arrivato da WhatsApp o dalla posta: apre la scheda gia' compilata.
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            viewModel.openFromShare(sharedText)
            onSharedTextHandled()
        }
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
                title = { Text(state.date.format(LONG_DATE)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (state.tab) {
                        EntriesTab.ATTIVITA -> viewModel.newActivity()
                        EntriesTab.EVENTI -> viewModel.newEvent()
                        EntriesTab.COMUNICAZIONI -> viewModel.newCommunication()
                    }
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = {
                    Text(
                        when (state.tab) {
                            EntriesTab.ATTIVITA -> "Attivita'"
                            EntriesTab.EVENTI -> "Evento"
                            EntriesTab.COMUNICAZIONI -> "Comunicazione"
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == EntriesTab.ATTIVITA,
                    onClick = { viewModel.selectTab(EntriesTab.ATTIVITA) },
                    text = { Text(tabLabel("Lavori", state.activityCount)) }
                )
                Tab(
                    selected = state.tab == EntriesTab.EVENTI,
                    onClick = { viewModel.selectTab(EntriesTab.EVENTI) },
                    text = { Text(tabLabel("Eventi", state.eventCount)) }
                )
                Tab(
                    selected = state.tab == EntriesTab.COMUNICAZIONI,
                    onClick = { viewModel.selectTab(EntriesTab.COMUNICAZIONI) },
                    text = { Text(tabLabel("Contatti", state.communicationCount)) }
                )
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.tab) {
                    EntriesTab.ATTIVITA -> ActivityList(
                        items = state.activities,
                        onEdit = viewModel::editActivity,
                        onDelete = viewModel::deleteActivity
                    )
                    EntriesTab.EVENTI -> EventList(
                        items = state.events,
                        onEdit = viewModel::editEvent,
                        onDelete = viewModel::deleteEvent,
                        onToggleUnresolved = viewModel::toggleEventUnresolved
                    )
                    EntriesTab.COMUNICAZIONI -> CommunicationList(
                        items = state.communications,
                        onEdit = viewModel::editCommunication,
                        onDelete = viewModel::deleteCommunication,
                        onToggleFollowUp = viewModel::toggleFollowUp
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        EntryEditorSheet(
            editor = editor,
            viewModel = viewModel,
            onDismiss = viewModel::closeEditor
        )
    }
}

private fun tabLabel(name: String, count: Int): String =
    if (count > 0) name + " " + count.toString() else name

@Composable
private fun ActivityList(
    items: List<WorkActivity>,
    onEdit: (WorkActivity) -> Unit,
    onDelete: (Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("Nessuna lavorazione registrata.\nTocca il pulsante per aggiungerne una.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { activity ->
            EntryCard(onClick = { onEdit(activity) }, onDelete = { onDelete(activity.id) }) {
                Text(
                    text = activity.category.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val zone = ZoneId.systemDefault()
                val orario = activity.startTime?.atZone(zone)?.toLocalTime()?.format(TIME)
                val fine = activity.endTime?.atZone(zone)?.toLocalTime()?.format(TIME)
                val riga = listOfNotNull(
                    when {
                        orario != null && fine != null -> orario + " - " + fine
                        orario != null -> "dalle " + orario
                        else -> null
                    },
                    activity.quantity
                ).joinToString("  -  ")
                if (riga.isNotEmpty()) {
                    Text(text = riga, style = MaterialTheme.typography.bodySmall)
                }
                activity.notes?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun EventList(
    items: List<WorkEvent>,
    onEdit: (WorkEvent) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleUnresolved: (WorkEvent) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("Nessun evento registrato.\nQui vanno fermi, ritardi, consegne e problemi.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { event ->
            val evidenzia = event.severity != EventSeverity.NORMALE || event.unresolved
            EntryCard(
                onClick = { onEdit(event) },
                onDelete = { onDelete(event.id) },
                highlighted = evidenzia
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.time.atZone(ZoneId.systemDefault()).toLocalTime().format(TIME),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "   " + event.type.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                event.description?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                val dettagli = buildList {
                    event.durationMinutes?.let { add(it.toString() + " minuti") }
                    if (event.severity != EventSeverity.NORMALE) add(event.severity.label())
                }
                if (dettagli.isNotEmpty()) {
                    Text(
                        text = dettagli.joinToString("  -  "),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = { onToggleUnresolved(event) }) {
                    Text(if (event.unresolved) "Segna come risolto" else "Segna da risolvere")
                }
            }
        }
    }
}

@Composable
private fun CommunicationList(
    items: List<Communication>,
    onEdit: (Communication) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleFollowUp: (Communication) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage(
            "Nessuna comunicazione registrata.\n\n" +
                "Da WhatsApp o dalla posta puoi selezionare un messaggio, " +
                "premere Condividi e scegliere Diario Lavorativo: " +
                "il testo arriva qui gia' pronto."
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { comm ->
            EntryCard(
                onClick = { onEdit(comm) },
                onDelete = { onDelete(comm.id) },
                highlighted = comm.requiresFollowUp
            ) {
                Text(
                    text = comm.time.atZone(ZoneId.systemDefault()).toLocalTime().format(TIME) +
                        "   " + comm.channel.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = comm.direction.labelFor(comm.channel) + " " + comm.contactName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                comm.subject?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (comm.hasContent) {
                    Text(text = comm.preview(), style = MaterialTheme.typography.bodySmall)
                }
                comm.durationMinutes?.let {
                    Text(
                        text = it.toString() + " minuti",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { onToggleFollowUp(comm) }) {
                    Text(
                        if (comm.requiresFollowUp) "Fatto, non serve piu'"
                        else "Da mettere per iscritto"
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    onClick: () -> Unit,
    onDelete: () -> Unit,
    highlighted: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Column(modifier = Modifier.weight(1f), content = content)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Elimina",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
