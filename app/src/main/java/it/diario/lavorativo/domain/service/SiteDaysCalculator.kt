package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Un giorno lavorato su un cantiere, con le ore fatte li'. */
data class SiteDay(val date: LocalDate, val hours: Duration)

/**
 * Il calendario al contrario: per ogni cantiere, i giorni in cui ci si e'
 * lavorato. Le giornate divise su piu' cantieri contano per ognuno, con le
 * ore fatte su ciascuno; il principale prende il resto della giornata.
 */
class SiteDaysCalculator(private val calculator: WorkTimeCalculator) {

    fun build(days: List<WorkDay>, now: Instant, standardMinutes: Int): Map<Long, List<SiteDay>> {
        val out = mutableMapOf<Long, MutableList<SiteDay>>()
        days.filter { it.dayType == DayType.LAVORO }.forEach { day ->
            val net = if (day.isStarted) calculator.summarize(day, now, standardMinutes).net else Duration.ZERO
            day.extraSites.forEach { ds ->
                out.getOrPut(ds.site.id) { mutableListOf() } += SiteDay(day.date, ds.duration)
            }
            day.site?.let { s ->
                val resto = net.minusMinutes(day.extraSitesMinutes.toLong())
                out.getOrPut(s.id) { mutableListOf() } +=
                    SiteDay(day.date, if (resto.isNegative) Duration.ZERO else resto)
            }
        }
        return out.mapValues { (_, list) ->
            list.groupBy { it.date }
                .map { (d, parts) -> SiteDay(d, parts.fold(Duration.ZERO) { a, p -> a.plus(p.hours) }) }
                .sortedByDescending { it.date }
        }
    }
}
