package it.diario.lavorativo.domain.model

import java.time.Duration

/**
 * Un cantiere in piu' nella giornata, con le ore fatte li'.
 *
 * Il cantiere principale e' [WorkDay.site]; le sue ore sono quello che
 * resta della giornata togliendo questi.
 */
data class DaySite(
    val id: Long = 0L,
    val workDayId: Long = 0L,
    val site: Site,
    val minutes: Int = 0,
    val description: String? = null
) {
    val duration: Duration get() = Duration.ofMinutes(minutes.toLong())
}
