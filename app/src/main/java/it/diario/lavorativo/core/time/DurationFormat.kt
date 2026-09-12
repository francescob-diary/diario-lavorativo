package it.diario.lavorativo.core.time

import java.time.Duration

/**
 * Formattazione delle durate in forma leggibile a colpo d'occhio in cantiere.
 * Tenuto fuori dalla UI per essere testabile e coerente in tutte le schermate.
 */
object DurationFormat {

    /** Forma compatta: "8h 15m", "45m", "0m". */
    fun short(duration: Duration): String {
        val safe = if (duration.isNegative) Duration.ZERO else duration
        val hours = safe.toHours()
        val minutes = safe.toMinutes() % 60
        return when {
            hours > 0 && minutes > 0 -> hours.toString() + "h " + minutes + "m"
            hours > 0 -> hours.toString() + "h"
            else -> minutes.toString() + "m"
        }
    }

    /**
     * Ore in decimale con la virgola, come si scrivono sul modulo cartaceo
     * nella colonna H: 9h -> "9", 8h30 -> "8,5", 7h45 -> "7,75".
     * Si arrotonda al quarto d'ora, che e' la precisione con cui si compila
     * a mano il foglio.
     */
    fun decimalHours(duration: Duration): String {
        val safe = if (duration.isNegative) Duration.ZERO else duration
        if (safe.isZero) return ""

        val quarti = Math.round(safe.toMinutes() / 15.0)
        val ore = quarti / 4
        val resto = (quarti % 4) * 25

        return when (resto) {
            0L -> ore.toString()
            50L -> ore.toString() + ",5"
            else -> ore.toString() + "," + resto.toString()
        }
    }

    /** Forma con segno, per gli scostamenti: "+1h 30m", "-45m". */
    fun signed(duration: Duration): String {
        val prefix = if (duration.isNegative) "-" else "+"
        return prefix + short(if (duration.isNegative) duration.negated() else duration)
    }
}
