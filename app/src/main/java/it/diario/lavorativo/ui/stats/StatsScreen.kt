package it.diario.lavorativo.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.CategoryCount
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.SiteHours
import it.diario.lavorativo.domain.model.StatsRange
import it.diario.lavorativo.domain.model.StatsSummary
import it.diario.lavorativo.domain.model.TrendPoint
import it.diario.lavorativo.ui.entries.label
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PERIODO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
private val MESE_ANNO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val ANNO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy", Locale.ITALIAN)

/** Altezza massima delle barre del grafico. */
private const val ALTEZZA_BARRA = 110f

/**
 * Statistiche: quadro del periodo, andamento, ore per cantiere, lavorazioni.
 *
 * Le barre sono disegnate a mano con dei Box invece che con una libreria di
 * grafici: qui servono barre piene e ben contrastate, leggibili al sole e con
 * il telefono in mano, e tirarsi dentro una dipendenza per questo sarebbe
 * sproporzionato.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StatsRange.entries.forEachIndexed { index, range ->
                SegmentedButton(
                    selected = state.range == range,
                    onClick = { viewModel.selectRange(range) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = StatsRange.entries.size
                    )
                ) {
                    Text(rangeLabel(range))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.previous() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Periodo precedente")
            }
            Text(
                text = periodLabel(state.range, summary),
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

        Spacer(Modifier.height(12.dp))

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            !summary.hasData -> Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nessuna giornata registrata in questo periodo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            else -> StatsContent(summary)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatsContent(summary: StatsSummary) {
    HeadlineCard(summary)

    Spacer(Modifier.height(20.dp))

    SectionTitle("Andamento")
    Spacer(Modifier.height(8.dp))
    TrendChart(points = summary.trend, peak = summary.trendPeak)

    if (summary.sites.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionTitle("Ore per cantiere")
        Spacer(Modifier.height(8.dp))
        val peak = summary.sites.first().net
        summary.sites.forEach { site ->
            SiteBar(site = site, peak = peak)
            Spacer(Modifier.height(10.dp))
        }
    }

    if (summary.categories.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionTitle("Lavorazioni piu' frequenti")
        Spacer(Modifier.height(4.dp))
        // Sei righe bastano: sotto ci sono sempre code lunghe di roba fatta una volta.
        summary.categories.take(6).forEach { entry ->
            CategoryRow(entry)
        }
    }

    val assenze = summary.totals.absenceDays
    if (assenze.isNotEmpty() || summary.eventDays > 0 || summary.unresolvedEvents > 0) {
        Spacer(Modifier.height(20.dp))
        SectionTitle("Altro")
        Spacer(Modifier.height(8.dp))

        assenze.forEach { (type, count) ->
            Text(
                text = absenceLabel(type, count),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (summary.eventDays > 0) {
            Text(
                text = "Giornate con eventi registrati: " + summary.eventDays.toString(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (summary.unresolvedEvents > 0) {
            Text(
                text = "Questioni ancora aperte: " + summary.unresolvedEvents.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
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

private fun rangeLabel(range: StatsRange): String = when (range) {
    StatsRange.SETTIMANA -> "Settimana"
    StatsRange.MESE -> "Mese"
    StatsRange.ANNO -> "Anno"
}

private fun periodLabel(range: StatsRange, summary: StatsSummary): String = when (range) {
    StatsRange.SETTIMANA ->
        summary.from.format(PERIODO) + " - " + summary.to.format(PERIODO)
    StatsRange.MESE ->
        summary.from.format(MESE_ANNO).replaceFirstChar { it.uppercase() }
    StatsRange.ANNO ->
        summary.from.format(ANNO)
}

private fun absenceLabel(type: DayType, count: Int): String {
    val nome = when (type) {
        DayType.FERIE -> if (count == 1) "giorno di ferie" else "giorni di ferie"
        DayType.PERMESSO -> if (count == 1) "permesso" else "permessi"
        DayType.MALATTIA -> if (count == 1) "giorno di malattia" else "giorni di malattia"
        DayType.FESTIVO -> if (count == 1) "festivo" else "festivi"
        DayType.LAVORO -> if (count == 1) "giornata" else "giornate"
    }
    return count.toString() + " " + nome
}

@Composable
private fun HeadlineCard(summary: StatsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Metric(
                    label = "Ore nette",
                    value = DurationFormat.short(summary.totals.net),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    label = "Giorni",
                    value = summary.totals.workedDays.toString(),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    label = "Straordinario",
                    value = DurationFormat.short(summary.totals.overtime),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Metric(
                    label = "Media al giorno",
                    value = DurationFormat.short(summary.totals.averageNet),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    label = "Pause",
                    value = DurationFormat.short(summary.totals.breaks),
                    modifier = Modifier.weight(1f)
                )
                if (summary.hasComparison) {
                    Metric(
                        label = "Sul precedente",
                        value = DurationFormat.signed(summary.difference),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Senza periodo precedente la casella resta vuota, ma lo
                    // spazio si tiene lo stesso per non far ballare le colonne.
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Barre verticali. L'altezza e' in proporzione al periodo con piu' ore, cosi'
 * il confronto si legge a colpo d'occhio senza numeri sull'asse. I periodi
 * senza lavoro restano grigi e piatti invece di sparire: un buco nel grafico
 * dice qualcosa quanto una barra alta.
 */
@Composable
private fun TrendChart(points: List<TrendPoint>, peak: Duration) {
    if (points.isEmpty()) return

    val peakMinutes = peak.toMinutes().coerceAtLeast(1L)

    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEach { point ->
            val fraction = (point.net.toMinutes().toFloat() / peakMinutes.toFloat())
                .coerceIn(0f, 1f)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = if (point.hasWork) DurationFormat.short(point.net) else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((ALTEZZA_BARRA * fraction).dp.coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (point.hasWork) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SiteBar(site: SiteHours, peak: Duration) {
    val peakMinutes = peak.toMinutes().coerceAtLeast(1L)
    val fraction = (site.net.toMinutes().toFloat() / peakMinutes.toFloat())
        .coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = site.siteName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = DurationFormat.short(site.net) + " - " + site.days.toString() +
                    (if (site.days == 1) " giorno" else " giorni"),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun CategoryRow(entry: CategoryCount) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.category.label(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = entry.count.toString() + (if (entry.count == 1) " volta" else " volte"),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
