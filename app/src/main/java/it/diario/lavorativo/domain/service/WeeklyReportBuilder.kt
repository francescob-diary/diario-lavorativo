package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.WeeklyDayLine
import it.diario.lavorativo.domain.model.WeeklyReport
import it.diario.lavorativo.domain.model.WeeklySegment
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Costruisce il foglio settimanale a partire dalle giornate registrate.
 *
 * Riusa [WorkTimeCalculator] e [PeriodSummarizer] invece di rifare i conti:
 * i totali del foglio consegnato in sede devono coincidere con quelli che
 * l'utente vede nello storico, altrimenti si perde fiducia nell'app.
 *
 * Puro Kotlin, nessuna dipendenza da Android: verificabile con test veri.
 */
class WeeklyReportBuilder(
    private val calculator: WorkTimeCalculator,
    private val summarizer: PeriodSummarizer
) {

    /**
     * @param weekStart il lunedi' della settimana da riepilogare
     * @param days le giornate registrate che cadono in quella settimana
     * @param activities lavorazioni, raggruppate per id di giornata
     * @param events eventi, raggruppati per id di giornata
     */
    fun build(
        weekStart: LocalDate,
        days: List<WorkDay>,
        activities: Map<Long, List<WorkActivity>> = emptyMap(),
        events: Map<Long, List<WorkEvent>> = emptyMap(),
        now: Instant,
        standardMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): WeeklyReport {
        val monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val byDate = days.associateBy { it.date }

        val lines = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            val day = byDate[date]
            if (day == null) {
                WeeklyDayLine(date = date)
            } else {
                lineFor(day, activities[day.id].orEmpty(), now, standardMinutes, zone)
            }
        }

        val notable = days
            .flatMap { events[it.id].orEmpty() }
            .filter { it.severity != EventSeverity.NORMALE || it.unresolved }
            .sortedBy { it.time }

        return WeeklyReport(
            weekStart = monday,
            days = lines,
            totals = summarizer.totals(days, now, standardMinutes),
            sites = days.flatMap { d -> d.allSites.map { it.name } }.distinct(),
            notableEvents = notable
        )
    }

    private fun lineFor(
        day: WorkDay,
        activities: List<WorkActivity>,
        now: Instant,
        standardMinutes: Int,
        zone: ZoneId
    ): WeeklyDayLine {
        val summary = calculator.summarize(day, now, standardMinutes)
        val lavoro = describeWork(day, activities)

        // Giornata divisa su piu' cantieri: le ore del principale sono il
        // resto della giornata togliendo quelle segnate sugli altri.
        val segments = if (day.extraSites.isEmpty() || day.dayType != DayType.LAVORO) {
            emptyList()
        } else {
            val extra = Duration.ofMinutes(day.extraSitesMinutes.toLong())
            val principale = summary.net.minus(extra).let { if (it.isNegative) Duration.ZERO else it }
            listOf(
                WeeklySegment(
                    siteLabel = SiteAddressFormatter.forReport(day.site).orEmpty(),
                    hours = principale,
                    work = lavoro
                )
            ) + day.extraSites.map { ds ->
                WeeklySegment(
                    siteLabel = SiteAddressFormatter.forReport(ds.site).orEmpty(),
                    hours = ds.duration,
                    work = ds.description?.takeIf { it.isNotBlank() }
                        ?: ds.site.workInProgress?.takeIf { it.isNotBlank() }
                        ?: ""
                )
            }
        }

        return WeeklyDayLine(
            date = day.date,
            dayType = day.dayType,
            siteName = day.site?.name,
            siteLabel = SiteAddressFormatter.forReport(day.site),
            startLabel = day.startTime?.let { HHMM.format(it.atZone(zone)) },
            endLabel = day.endTime?.let { HHMM.format(it.atZone(zone)) },
            net = if (day.dayType == DayType.LAVORO) summary.net else Duration.ZERO,
            overtime = if (day.dayType == DayType.LAVORO) summary.overtime else Duration.ZERO,
            breaks = summary.breaks,
            work = lavoro,
            incomplete = day.isRunning,
            segments = segments
        )
    }

    /**
     * Riga "lavoro svolto". Si preferiscono le lavorazioni registrate una per
     * una; se non ce ne sono si ripiega sulla descrizione libera della giornata,
     * cosi' la casella non resta vuota su un foglio che finisce in sede.
     */
    private fun describeWork(day: WorkDay, activities: List<WorkActivity>): String {
        // Il "Lavoro in corso" del cantiere, se c'e', e' quello che va sul
        // foglio: e' la descrizione generale che in sede si aspettano.
        day.site?.workInProgress?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        if (activities.isNotEmpty()) {
            return activities.joinToString("; ") { activity ->
                val quantity = activity.quantity?.takeIf { it.isNotBlank() }
                if (quantity == null) {
                    activity.description
                } else {
                    activity.description + " (" + quantity + ")"
                }
            }
        }
        return day.description.orEmpty().trim()
    }

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
