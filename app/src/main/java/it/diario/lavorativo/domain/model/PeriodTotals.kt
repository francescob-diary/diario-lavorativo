package it.diario.lavorativo.domain.model

import java.time.Duration

/**
 * Totali di un periodo (tipicamente un mese) mostrati in cima allo storico.
 * Modello puro: niente Android, niente formattazione. La UI decide come scriverlo.
 */
data class PeriodTotals(
    val workedDays: Int = 0,
    val net: Duration = Duration.ZERO,
    val overtime: Duration = Duration.ZERO,
    val breaks: Duration = Duration.ZERO,
    val absenceDays: Map<DayType, Int> = emptyMap()
) {
    val hasData: Boolean get() = workedDays > 0 || absenceDays.isNotEmpty()

    /** Media del netto sui soli giorni effettivamente lavorati. */
    val averageNet: Duration
        get() = if (workedDays == 0) Duration.ZERO else net.dividedBy(workedDays.toLong())
}
