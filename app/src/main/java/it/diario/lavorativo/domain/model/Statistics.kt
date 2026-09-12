package it.diario.lavorativo.domain.model

import java.time.Duration
import java.time.LocalDate

/** Arco di tempo su cui si guardano le statistiche. */
enum class StatsRange {
    SETTIMANA,
    MESE,
    ANNO
}

/**
 * Una barra del grafico: un periodo con le sue ore.
 * [label] e' gia' pronto per essere scritto sotto la barra.
 */
data class TrendPoint(
    val start: LocalDate,
    val label: String,
    val net: Duration = Duration.ZERO,
    val overtime: Duration = Duration.ZERO,
    val workedDays: Int = 0
) {
    val hasWork: Boolean get() = !net.isZero
}

/** Ore per cantiere, per sapere dove e' andato il tempo. */
data class SiteHours(
    val siteName: String,
    val net: Duration,
    val days: Int
)

/** Quante volte e' stata registrata una certa lavorazione. */
data class CategoryCount(
    val category: ActivityCategory,
    val count: Int
)

/**
 * Quadro completo di un periodo.
 *
 * [previousNet] serve al confronto col periodo precedente: sapere di avere
 * fatto 160 ore dice poco, sapere di averne fatte 12 in piu' del mese scorso
 * dice molto.
 */
data class StatsSummary(
    val range: StatsRange = StatsRange.MESE,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val totals: PeriodTotals = PeriodTotals(),
    val previousNet: Duration = Duration.ZERO,
    val trend: List<TrendPoint> = emptyList(),
    val sites: List<SiteHours> = emptyList(),
    val categories: List<CategoryCount> = emptyList(),
    val eventDays: Int = 0,
    val unresolvedEvents: Int = 0
) {
    val hasData: Boolean get() = totals.hasData

    /** Differenza rispetto al periodo precedente. Negativa se si e' lavorato meno. */
    val difference: Duration get() = totals.net.minus(previousNet)

    val hasComparison: Boolean get() = !previousNet.isZero

    /** Il cantiere su cui si e' lavorato di piu'. */
    val topSite: SiteHours? get() = sites.firstOrNull()

    /** Massimo del grafico, per dimensionare le barre. Mai zero. */
    val trendPeak: Duration
        get() = trend.maxOfOrNull { it.net } ?: Duration.ZERO
}
