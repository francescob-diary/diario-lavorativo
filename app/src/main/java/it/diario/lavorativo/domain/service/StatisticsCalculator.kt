package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.CategoryCount
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.SiteHours
import it.diario.lavorativo.domain.model.StatsRange
import it.diario.lavorativo.domain.model.StatsSummary
import it.diario.lavorativo.domain.model.TrendPoint
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Calcola le statistiche di un periodo.
 *
 * Riusa [WorkTimeCalculator] e [PeriodSummarizer]: i numeri della dashboard
 * devono coincidere con quelli dello storico e del rapportino, altrimenti
 * l'utente smette di fidarsi dell'app.
 *
 * Puro Kotlin, verificabile con test veri.
 */
class StatisticsCalculator(
    private val calculator: WorkTimeCalculator,
    private val summarizer: PeriodSummarizer
) {

    /** Primo giorno del periodo che contiene [reference]. */
    fun rangeStart(range: StatsRange, reference: LocalDate): LocalDate = when (range) {
        StatsRange.SETTIMANA -> reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatsRange.MESE -> reference.withDayOfMonth(1)
        StatsRange.ANNO -> reference.withDayOfYear(1)
    }

    /** Ultimo giorno del periodo che contiene [reference]. */
    fun rangeEnd(range: StatsRange, reference: LocalDate): LocalDate = when (range) {
        StatsRange.SETTIMANA -> rangeStart(range, reference).plusDays(6)
        StatsRange.MESE -> reference.withDayOfMonth(reference.lengthOfMonth())
        StatsRange.ANNO -> reference.withDayOfYear(reference.lengthOfYear())
    }

    fun previousStart(range: StatsRange, start: LocalDate): LocalDate = when (range) {
        StatsRange.SETTIMANA -> start.minusWeeks(1)
        StatsRange.MESE -> start.minusMonths(1)
        StatsRange.ANNO -> start.minusYears(1)
    }

    fun build(
        range: StatsRange,
        reference: LocalDate,
        days: List<WorkDay>,
        previousDays: List<WorkDay> = emptyList(),
        activities: List<WorkActivity> = emptyList(),
        events: List<WorkEvent> = emptyList(),
        now: Instant,
        standardMinutes: Int
    ): StatsSummary {
        val from = rangeStart(range, reference)
        val to = rangeEnd(range, reference)

        val previous = summarizer.totals(previousDays, now, standardMinutes)

        return StatsSummary(
            range = range,
            from = from,
            to = to,
            totals = summarizer.totals(days, now, standardMinutes),
            previousNet = previous.net,
            trend = trend(range, from, to, days, now, standardMinutes),
            sites = siteBreakdown(days, now, standardMinutes),
            categories = categoryBreakdown(activities),
            eventDays = events.map { it.workDayId }.distinct().size,
            unresolvedEvents = events.count { it.unresolved }
        )
    }

    /**
     * Barre del grafico. La granularita' segue il periodo: giorni dentro una
     * settimana, settimane dentro un mese, mesi dentro un anno. Mostrare
     * trecentosessantacinque barre su un telefono non servirebbe a nulla.
     */
    fun trend(
        range: StatsRange,
        from: LocalDate,
        to: LocalDate,
        days: List<WorkDay>,
        now: Instant,
        standardMinutes: Int
    ): List<TrendPoint> = when (range) {
        StatsRange.SETTIMANA -> dailyTrend(from, days, now, standardMinutes)
        StatsRange.MESE -> weeklyTrend(from, to, days, now, standardMinutes)
        StatsRange.ANNO -> monthlyTrend(from, days, now, standardMinutes)
    }

    private fun dailyTrend(
        from: LocalDate,
        days: List<WorkDay>,
        now: Instant,
        standardMinutes: Int
    ): List<TrendPoint> {
        val byDate = days.associateBy { it.date }
        return (0L..6L).map { offset ->
            val date = from.plusDays(offset)
            val day = byDate[date]
            val summary = day?.let { calculator.summarize(it, now, standardMinutes) }
            val isWork = day?.dayType == DayType.LAVORO
            TrendPoint(
                start = date,
                label = DAY_LABELS[date.dayOfWeek.value - 1],
                net = if (isWork) summary?.net ?: Duration.ZERO else Duration.ZERO,
                overtime = if (isWork) summary?.overtime ?: Duration.ZERO else Duration.ZERO,
                workedDays = if (isWork && day?.isClosed == true) 1 else 0
            )
        }
    }

    private fun weeklyTrend(
        from: LocalDate,
        to: LocalDate,
        days: List<WorkDay>,
        now: Instant,
        standardMinutes: Int
    ): List<TrendPoint> {
        val points = mutableListOf<TrendPoint>()
        var weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var index = 1
        while (!weekStart.isAfter(to)) {
            val weekEnd = weekStart.plusDays(6)
            // Si contano solo i giorni che cadono dentro il mese richiesto:
            // la prima e l'ultima settimana sono quasi sempre a cavallo.
            val slice = days.filter {
                !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) &&
                    !it.date.isBefore(from) && !it.date.isAfter(to)
            }
            val totals = summarizer.totals(slice, now, standardMinutes)
            points += TrendPoint(
                start = weekStart,
                label = "S" + index.toString(),
                net = totals.net,
                overtime = totals.overtime,
                workedDays = totals.workedDays
            )
            weekStart = weekStart.plusWeeks(1)
            index++
        }
        return points
    }

    private fun monthlyTrend(
        from: LocalDate,
        days: List<WorkDay>,
        now: Instant,
        standardMinutes: Int
    ): List<TrendPoint> = (0..11).map { offset ->
        val month = YearMonth.from(from).plusMonths(offset.toLong())
        val slice = days.filter { YearMonth.from(it.date) == month }
        val totals = summarizer.totals(slice, now, standardMinutes)
        TrendPoint(
            start = month.atDay(1),
            label = MONTH_LABELS[month.monthValue - 1],
            net = totals.net,
            overtime = totals.overtime,
            workedDays = totals.workedDays
        )
    }

    /**
     * Ore per cantiere, dal piu' impegnativo al meno. Le giornate senza
     * cantiere finiscono sotto "Senza cantiere" invece di sparire: sono ore
     * lavorate comunque e devono tornare nel totale.
     */
    fun siteBreakdown(
        days: List<WorkDay>,
        now: Instant,
        standardMinutes: Int
    ): List<SiteHours> = days
        .filter { it.dayType == DayType.LAVORO && it.isStarted }
        .groupBy { it.site?.name ?: NO_SITE }
        .map { (name, group) ->
            SiteHours(
                siteName = name,
                net = group.fold(Duration.ZERO) { acc, day ->
                    acc.plus(calculator.summarize(day, now, standardMinutes).net)
                },
                days = group.count { it.isClosed }
            )
        }
        .sortedByDescending { it.net }

    /** Lavorazioni piu' frequenti, dalla piu' registrata alla meno. */
    fun categoryBreakdown(activities: List<WorkActivity>): List<CategoryCount> = activities
        .groupingBy { it.category }
        .eachCount()
        .map { (category, count) -> CategoryCount(category, count) }
        .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { it.category.name })

    private companion object {
        const val NO_SITE = "Senza cantiere"
        val DAY_LABELS = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
        val MONTH_LABELS = listOf(
            "Gen", "Feb", "Mar", "Apr", "Mag", "Giu",
            "Lug", "Ago", "Set", "Ott", "Nov", "Dic"
        )
    }
}
