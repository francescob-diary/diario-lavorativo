package it.diario.lavorativo.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un punto sulla mappa. Deliberatamente senza dipendenze da Android
 * cosi' il calcolo delle distanze e' testabile con i test unitari.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    /** Precisione dichiarata dal sensore, in metri. Null se sconosciuta. */
    val accuracyMeters: Float? = null
) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    /**
     * Distanza in metri con la formula dell'emisenoverso (haversine).
     * Errore trascurabile alle distanze di un cantiere.
     */
    fun distanceMetersTo(other: GeoPoint): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * asin(sqrt(a).coerceAtMost(1.0))
    }
}
