package it.diario.lavorativo.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkTimeSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ITALIAN = Locale("it", "IT")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onOpenWeekly: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<WorkDay?>(null) }

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
            TopAppBar(title = { Text("Storico") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            MonthHeader(
                month = state.month,
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isCurrentMonth = state.isCurrentMonth,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onToday = viewModel::goToCurrentMonth
            )

            Spacer(Modifier.height(8.dp))

            TotalsCard(state)

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenWeekly,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("RAPPORTINO SETTIMANALE")
            }

            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.view == HistoryView.ELENCO,
                    onClick = viewModel::showList,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Elenco") }
                SegmentedButton(
                    selected = state.view == HistoryView.CALENDARIO,
                    onClick = viewModel::showCalendar,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Calendario") }
            }

            Spacer(Modifier.height(12.dp))

            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.isEmpty -> EmptyMonth(state.isCurrentMonth)

                state.view == HistoryView.ELENCO -> DayList(
                    rows = state.rows,
                    today = state.today,
                    onOpenDay = onOpenDay,
                    onDelete = { pendingDelete = it }
                )

                else -> CalendarMonth(
                    state = state,
                    onSelect = viewModel::selectDate,
                    onOpenDay = onOpenDay
                )
            }
        }
    }

    pendingDelete?.let { day ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminare la giornata?") },
            text = {
                Text(
                    "Stai per eliminare " + day.date.format(LONG_DATE) +
                        ". L'operazione non si puo' annullare."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDay(day.id)
                    pendingDelete = null
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = canGoBack) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Mese precedente")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = monthLabel(month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (!isCurrentMonth) {
                TextButton(onClick = onToday) { Text("Vai a questo mese") }
            }
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Mese successivo")
        }
    }
}

@Composable
private fun TotalsCard(state: HistoryUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TotalItem("Giornate", state.totals.workedDays.toString(), Modifier.weight(1f))
                TotalItem("Ore nette", DurationFormat.short(state.totals.net), Modifier.weight(1f))
                TotalItem(
                    "Straordinario",
                    DurationFormat.short(state.totals.overtime),
                    Modifier.weight(1f)
                )
            }
            if (state.totals.workedDays > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Media giornaliera " +
                        DurationFormat.short(state.totals.averageNet) +
                        ", pause totali " + DurationFormat.short(state.totals.breaks),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val absences = state.totals.absenceDays
            if (absences.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = absences.entries.joinToString(", ") { (type, count) ->
                        count.toString() + " " + dayTypeLabel(type, count)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TotalItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DayList(
    rows: List<HistoryRow>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    onDelete: (WorkDay) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.day.id }) { row ->
            DayCard(
                row = row,
                isToday = row.day.date == today,
                onClick = { onOpenDay(row.day.date) },
                onDelete = { onDelete(row.day) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun DayCard(
    row: HistoryRow,
    isToday: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val day = row.day
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.size(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = day.date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, ITALIAN)
                        .uppercase(ITALIAN),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.site?.name ?: "Senza cantiere",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = timesLabel(day),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (day.dayType != DayType.LAVORO) {
                    Text(
                        text = dayTypeLabel(day.dayType, 1).uppercase(ITALIAN),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DurationFormat.short(row.summary.net),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!row.summary.overtime.isZero) {
                    Text(
                        text = DurationFormat.signed(row.summary.overtime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (day.isRunning) {
                    Text(
                        text = "in corso",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Elimina giornata",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun CalendarMonth(
    state: HistoryUiState,
    onSelect: (LocalDate?) -> Unit,
    onOpenDay: (LocalDate) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "G", "V", "S", "D").forEach { initial ->
                Text(
                    text = initial,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        state.cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayBox(
                        cell = cell,
                        inMonth = YearMonth.from(cell.date) == state.month,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(cell.date) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val selected = state.selectedRow
        when {
            selected != null -> Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDay(selected.day.date) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selected.day.date.format(LONG_DATE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(selected.day.site?.name ?: "Senza cantiere")
                    Text(timesLabel(selected.day))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Netto " + DurationFormat.short(selected.summary.net) +
                            ", pause " + DurationFormat.short(selected.summary.breaks),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tocca per aprire la giornata",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            state.selectedDate != null -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = state.selectedDate.format(LONG_DATE),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Nessuna giornata registrata.")
                }
            }

            else -> Text(
                text = "Tocca un giorno per vedere il dettaglio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun DayBox(
    cell: CalendarCell,
    inMonth: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = when {
        cell.isSelected -> MaterialTheme.colorScheme.primary
        cell.hasData -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        cell.isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.outlineVariant
        cell.hasData -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(background)
                .then(
                    if (cell.isToday && !cell.isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = inMonth, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = cell.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = if (cell.hasData) FontWeight.Bold else FontWeight.Normal
                )
                cell.summary?.let { summary ->
                    Text(
                        text = summary.net.toHours().toString() + "h",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMonth(isCurrentMonth: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Nessuna giornata in questo mese",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isCurrentMonth) {
                    "Appena inizi una giornata dalla schermata Oggi la trovi qui."
                } else {
                    "Prova a cambiare mese con le frecce in alto."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
