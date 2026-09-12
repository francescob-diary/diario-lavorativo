package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.WeeklyDayLine
import it.diario.lavorativo.domain.model.WeeklyReport
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
            sites = days.mapNotNull { it.site?.name }.distinct(),
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
        return WeeklyDayLine(
            date = day.date,
            dayType = day.dayType,
            siteName = day.site?.name,
            startLabel = day.startTime?.let { HHMM.format(it.atZone(zone)) },
            endLabel = day.endTime?.let { HHMM.format(it.atZone(zone)) },
            net = if (day.dayType == DayType.LAVORO) summary.net else Duration.ZERO,
            overtime = if (day.dayType == DayType.LAVORO) summary.overtime else Duration.ZERO,
            breaks = summary.breaks,
            work = describeWork(day, activities),
            incomplete = day.isRunning
        )
    }

    /**
     * Riga "lavoro svolto". Si preferiscono le lavorazioni registrate una per
     * una; se non ce ne sono si ripiega sulla descrizione libera della giornata,
     * cosi' la casella non resta vuota su un foglio che finisce in sede.
     */
    private fun describeWork(day: WorkDay, activities: List<WorkActivity>): String {
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
