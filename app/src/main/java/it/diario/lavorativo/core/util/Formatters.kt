package it.diario.lavorativo.core.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formattazioni condivise: una sola definizione per tutta l'app. */
object Formatters {

    // Locale.ITALY: Locale.of() esiste solo da Java 19, non su Android API 26.
    private val italian: Locale = Locale.ITALY

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", italian)

    private val fullDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", italian)

    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", italian)

    fun time(instant: Instant?, zone: ZoneId): String =
        instant?.atZone(zone)?.format(timeFormatter) ?: "--:--"

    fun fullDate(date: LocalDate): String =
        date.format(fullDateFormatter).replaceFirstChar { it.uppercase(italian) }

    fun shortDate(date: LocalDate): String = date.format(shortDateFormatter)

    /** Durata leggibile: "8h 15m", "45m", "0m". */
    fun duration(duration: Duration?): String {
        if (duration == null) return "--"
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** Durata compatta per i totali grandi: "8:15". */
    fun durationClock(duration: Duration?): String {
        if (duration == null) return "--:--"
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        return String.format(italian, "%d:%02d", totalMinutes / 60, totalMinutes % 60)
    }
}
