package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkBreak
import it.diario.lavorativo.domain.model.WorkTimeSummary
import java.time.Duration
import java.time.Instant

/**
 * Unico punto in cui vengono calcolate le ore di una giornata.
 *
 * Non ha dipendenze da Android: e' Kotlin puro, quindi testabile con unit test
 * normali (vedi app/src/test/.../WorkTimeCalculatorTest.kt).
 *
 * Regole applicate:
 *  - lordo   = ingresso -> uscita (se la giornata e' aperta si usa "adesso");
 *  - pause   = somma degli intervalli di pausa, tagliati dentro l'orario di
 *              lavoro e uniti fra loro se si sovrappongono (niente doppi conteggi);
 *  - netto   = lordo - pause, mai negativo;
 *  - straordinario = netto - orario standard, se positivo;
 *  - mancante      = orario standard - netto, se positivo.
 */
class WorkTimeCalculator {

    fun summarize(
        day: WorkDay,
        now: Instant,
        standardWorkMinutes: Int
    ): WorkTimeSummary {
        val standard = Duration.ofMinutes(
            (day.standardMinutesOverride ?: standardWorkMinutes).coerceAtLeast(0).toLong()
        )

        val start = day.startTime
            ?: return WorkTimeSummary(standard = standard, isStarted = false)

        // Riferimento finale: l'uscita registrata oppure l'istante attuale.
        val rawEnd = day.endTime ?: now
        val end = if (rawEnd.isBefore(start)) start else rawEnd

        val gross = Duration.between(start, end)
        val breaks = totalBreaks(day.breaks, start, end)
        val net = (gross - breaks).coerceAtLeastZero()

        val diff = net - standard
        val overtime = if (diff.isNegative) Duration.ZERO else diff
        val deficit = if (diff.isNegative) diff.negated() else Duration.ZERO

        val openBreak = day.openBreak
        val currentBreak = if (day.isRunning && openBreak != null) {
            Duration.between(openBreak.startTime.coerceAtMost(end), end).coerceAtLeastZero()
        } else {
            null
        }

        return WorkTimeSummary(
            gross = gross,
            breaks = breaks,
            net = net,
            overtime = overtime,
            deficit = deficit,
            standard = standard,
            isStarted = true,
            isRunning = day.isRunning,
            isOnBreak = day.isOnBreak,
            currentBreak = currentBreak
        )
    }

    /**
     * Somma le pause dopo averle tagliate nell'intervallo [start, end] e unite
     * quando si sovrappongono. Una pausa ancora aperta viene chiusa a [end].
     */
    private fun totalBreaks(breaks: List<WorkBreak>, start: Instant, end: Instant): Duration {
        if (breaks.isEmpty()) return Duration.ZERO

        val intervals = breaks
            .map { b ->
                val from = b.startTime.coerceIn(start, end)
                val to = (b.endTime ?: end).coerceIn(start, end)
                from to to
            }
            .filter { (from, to) -> to.isAfter(from) }
            .sortedBy { it.first }

        if (intervals.isEmpty()) return Duration.ZERO

        var total = Duration.ZERO
        var currentFrom = intervals.first().first
        var currentTo = intervals.first().second

        for (i in 1 until intervals.size) {
            val (from, to) = intervals[i]
            if (from.isAfter(currentTo)) {
                total += Duration.between(currentFrom, currentTo)
                currentFrom = from
                currentTo = to
            } else if (to.isAfter(currentTo)) {
                currentTo = to
            }
        }
        total += Duration.between(currentFrom, currentTo)
        return total
    }

    // Instant implementa Comparable: coerceIn / coerceAtMost sono quelli della stdlib Kotlin.
    private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this
}
