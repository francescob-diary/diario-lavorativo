package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.PeriodTotals
import it.diario.lavorativo.domain.model.WorkDay
import java.time.Duration
import java.time.Instant

/**
 * Somma i totali di un insieme di giornate.
 *
 * Riusa [WorkTimeCalculator] per ogni singola giornata: le regole di calcolo
 * restano scritte in un solo posto. Se un giorno cambia il modo di contare
 * le pause, cambia anche qui automaticamente.
 *
 * Le giornate non ancora chiuse vengono incluse con il tempo maturato fino
 * a [now], ma NON vengono contate fra i giorni lavorati: altrimenti il totale
 * del mese cambierebbe da solo mentre l'operaio e' ancora in cantiere.
 */
class PeriodSummarizer(
    private val calculator: WorkTimeCalculator = WorkTimeCalculator()
) {

    fun totals(
        days: List<WorkDay>,
        now: Instant,
        standardWorkMinutes: Int
    ): PeriodTotals {
        var workedDays = 0
        var net = Duration.ZERO
        var overtime = Duration.ZERO
        var breaks = Duration.ZERO
        val absences = mutableMapOf<DayType, Int>()

        for (day in days) {
            if (day.dayType != DayType.LAVORO) {
                absences[day.dayType] = (absences[day.dayType] ?: 0) + 1
                continue
            }
            if (!day.isStarted) continue

            val summary = calculator.summarize(day, now, standardWorkMinutes)
            net += summary.net
            overtime += summary.overtime
            breaks += summary.breaks
            if (day.isClosed) workedDays++
        }

        return PeriodTotals(
            workedDays = workedDays,
            net = net,
            overtime = overtime,
            breaks = breaks,
            absenceDays = absences.toMap()
        )
    }
}
