package it.diario.lavorativo.ui.sites

import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus

/** Stato della lista cantieri. */
data class SitesUiState(
    val loading: Boolean = true,
    val sites: List<Site> = emptyList(),
    val showTerminated: Boolean = false,
    val query: String = "",
    val message: String? = null
) {
    val visibleSites: List<Site>
        get() = sites
            .filter { showTerminated || it.status == SiteStatus.ATTIVO }
            .filter { site ->
                if (query.isBlank()) true else {
                    val q = query.trim().lowercase()
                    listOfNotNull(site.name, site.city, site.client, site.company)
                        .any { it.lowercase().contains(q) }
                }
            }

    val activeCount: Int get() = sites.count { it.status == SiteStatus.ATTIVO }
    val isEmpty: Boolean get() = !loading && sites.isEmpty()
}

/** Stato della scheda di un singolo cantiere (nuovo o esistente). */
data class SiteEditUiState(
    val id: Long = 0L,
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val client: String = "",
    val company: String = "",
    val contact: String = "",
    val phone: String = "",
    val notes: String = "",
    val status: SiteStatus = SiteStatus.ATTIVO,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = Site.DEFAULT_RADIUS_METERS,
    val capturingPosition: Boolean = false,
    val positionAccuracy: Float? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val linkedWorkDays: Int = 0,
    val error: String? = null,
    val message: String? = null
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = name.isNotBlank() && !saving
    val hasPosition: Boolean get() = latitude != null && longitude != null

    val positionLabel: String
        get() {
            val la = latitude
            val lo = longitude
            return if (la == null || lo == null) "Nessuna posizione registrata"
            else String.format(java.util.Locale.ITALY, "%.5f, %.5f", la, lo)
        }
}
