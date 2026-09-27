package it.diario.lavorativo.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Promemoria di fine giornata: "compila il diario".
 *
 * Di partenza alle 17:15, un quarto d'ora dopo la fine standard, dal lunedi'
 * al venerdi'. Se la giornata e' gia' compilata la notifica non parte.
 */
data class DailyReminderSettings(
    val enabled: Boolean = true,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
    val days: Set<DayOfWeek> = DEFAULT_DAYS
) {
    val time: LocalTime get() = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    companion object {
        const val DEFAULT_HOUR = 17
        const val DEFAULT_MINUTE = 15
        val DEFAULT_DAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
    }
}
