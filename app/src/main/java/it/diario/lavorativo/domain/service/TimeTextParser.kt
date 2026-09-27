package it.diario.lavorativo.domain.service

import java.time.LocalTime

/**
 * Orari e durate scritti a mano, nel modo in cui li scrive la gente.
 *
 * Orari: "8", "8:30", "8.30", "830", "08:30", "17".
 * Durate in ore: "4", "3,5", "3.5", "3:30".
 *
 * Tutto quello che non si capisce restituisce null: meglio un campo
 * segnato in rosso che un orario inventato.
 */
object TimeTextParser {

    fun parseClock(text: String): LocalTime? {
        val t = text.trim().replace('.', ':').replace(',', ':').replace(" ", "")
        if (t.isEmpty()) return null
        val (h, m) = when {
            t.contains(':') -> {
                val parts = t.split(':')
                if (parts.size != 2) return null
                (parts[0].toIntOrNull() ?: return null) to
                    (parts[1].ifEmpty { "0" }.toIntOrNull() ?: return null)
            }
            t.all { it.isDigit() } && t.length <= 2 -> (t.toIntOrNull() ?: return null) to 0
            t.all { it.isDigit() } && t.length in 3..4 ->
                t.dropLast(2).toInt() to t.takeLast(2).toInt()
            else -> return null
        }
        if (h !in 0..24 || m !in 0..59) return null
        if (h == 24) return if (m == 0) LocalTime.MIDNIGHT else null
        return LocalTime.of(h, m)
    }

    /** Ore scritte a mano in minuti. */
    fun parseHours(text: String): Int? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        if (t.contains(':')) {
            val parts = t.split(':')
            if (parts.size != 2) return null
            val h = parts[0].ifEmpty { "0" }.toIntOrNull() ?: return null
            val m = parts[1].ifEmpty { "0" }.toIntOrNull() ?: return null
            if (h < 0 || m !in 0..59) return null
            return (h * 60 + m).takeIf { it <= 24 * 60 }
        }
        val valore = t.replace(',', '.').toDoubleOrNull() ?: return null
        if (valore < 0 || valore > 24) return null
        return Math.round(valore * 60).toInt()
    }

    /** 210 -> "3:30", 240 -> "4". */
    fun formatHours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) h.toString() else h.toString() + ":" + m.toString().padStart(2, '0')
    }
}
