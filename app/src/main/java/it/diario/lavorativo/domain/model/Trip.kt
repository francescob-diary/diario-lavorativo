package it.diario.lavorativo.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Spostamento: partenza e arrivo, con orari.
 * Non va nel rapportino che si consegna in sede.
 */
data class Trip(
    val id: Long = 0L,
    val workDayId: Long = 0L,
    val departTime: Instant? = null,
    val arriveTime: Instant? = null,
    val fromPlace: String? = null,
    val toPlace: String? = null,
    val notes: String? = null
) {
    /** Tempo di viaggio, se ci sono tutti e due gli orari e hanno senso. */
    val duration: Duration?
        get() {
            val a = departTime ?: return null
            val b = arriveTime ?: return null
            return if (b.isBefore(a)) null else Duration.between(a, b)
        }

    /** "Rivoli -> Trana", con i pezzi che ci sono. */
    val route: String
        get() = listOfNotNull(
            fromPlace?.takeIf { it.isNotBlank() },
            toPlace?.takeIf { it.isNotBlank() }
        ).joinToString(" -> ")
}
