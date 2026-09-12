package it.diario.lavorativo.domain.model

import java.time.LocalDate

/** Cantiere. Modello di dominio, indipendente da Room. */
data class Site(
    val id: Long = 0L,
    val name: String,
    val address: String? = null,
    val city: String? = null,
    val client: String? = null,
    val company: String? = null,
    val contact: String? = null,
    val phone: String? = null,
    val startDate: LocalDate? = null,
    val expectedEndDate: LocalDate? = null,
    val notes: String? = null,
    val status: SiteStatus = SiteStatus.ATTIVO,
    /** Posizione del cantiere. Null se non ancora registrata. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Raggio in metri entro cui si considera di "essere sul cantiere". */
    val radiusMeters: Int = DEFAULT_RADIUS_METERS
) {
    /** Etichetta breve usata nelle liste: "Nome - Citta". */
    val displayLabel: String
        get() = if (city.isNullOrBlank()) name else name + " - " + city

    /** Vero se il cantiere ha coordinate utilizzabili per il riconoscimento automatico. */
    val hasPosition: Boolean
        get() = latitude != null && longitude != null

    val position: GeoPoint?
        get() {
            val la = latitude
            val lo = longitude
            return if (la != null && lo != null) GeoPoint(la, lo) else null
        }

    /** Riga di indirizzo leggibile, per la scheda cantiere. */
    val fullAddress: String?
        get() {
            val parts = listOfNotNull(
                address?.takeIf { it.isNotBlank() },
                city?.takeIf { it.isNotBlank() }
            )
            return if (parts.isEmpty()) null else parts.joinToString(", ")
        }

    companion object {
        const val DEFAULT_RADIUS_METERS = 150
        val RADIUS_CHOICES = listOf(50, 100, 150, 300, 500, 1000)
    }
}
