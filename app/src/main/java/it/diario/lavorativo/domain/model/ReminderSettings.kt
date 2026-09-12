package it.diario.lavorativo.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Promemoria per la consegna del foglio settimanale.
 *
 * Valori iniziali: lunedi' alle 8:20, il riepilogo della settimana appena
 * conclusa. Sono modificabili dalle Impostazioni, perche' turni e abitudini
 * cambiano da impresa a impresa.
 */
data class ReminderSettings(
    val enabled: Boolean = true,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val hour: Int = 8,
    val minute: Int = 20
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)

    companion object {
        const val DEFAULT_HOUR = 8
        const val DEFAULT_MINUTE = 20
    }
}
