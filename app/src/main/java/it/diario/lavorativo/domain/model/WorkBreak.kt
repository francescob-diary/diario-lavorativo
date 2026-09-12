package it.diario.lavorativo.domain.model

import java.time.Instant

/**
 * Pausa registrata dentro una giornata.
 * Se [endTime] e' nullo la pausa e' ancora in corso.
 */
data class WorkBreak(
    val id: Long = 0L,
    val workDayId: Long,
    val startTime: Instant,
    val endTime: Instant? = null,
    val type: BreakType = BreakType.PAUSA,
    val note: String? = null
) {
    val isOpen: Boolean get() = endTime == null
}
