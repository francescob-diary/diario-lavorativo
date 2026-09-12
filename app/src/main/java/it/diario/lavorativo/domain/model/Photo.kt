package it.diario.lavorativo.domain.model

import java.time.Instant

/**
 * Foto di cantiere agganciata a una giornata.
 *
 * Puo' essere collegata anche a una singola lavorazione o a un evento: e' il
 * caso tipico della crepa fotografata, del materiale arrivato rotto o del
 * lavoro finito da mostrare al cliente.
 *
 * Il file NON sta nella galleria del telefono ma nello spazio privato
 * dell'app: le foto di cantiere spesso riguardano case di clienti e non
 * devono finire mescolate alle foto personali ne' nei backup automatici
 * delle app di fotografie.
 */
data class Photo(
    val id: Long = 0L,
    val workDayId: Long,
    /** Lavorazione a cui la foto si riferisce, se ce n'e' una. */
    val activityId: Long? = null,
    /** Evento a cui la foto si riferisce, se ce n'e' uno. */
    val eventId: Long? = null,
    /** Nome della copia ridotta dentro la cartella privata dell'app. */
    val fileName: String,
    /**
     * Uri della foto originale in galleria, quando c'e'.
     * L'originale a piena risoluzione resta li'; l'app ne tiene solo una
     * copia ridotta per le miniature e per il PDF.
     */
    val galleryUri: String? = null,
    val caption: String? = null,
    /** Quando e' stata scattata o scelta. */
    val takenAt: Instant,
    val source: PhotoSource = PhotoSource.FOTOCAMERA,
    /** Dimensione in byte, per sapere quanto spazio occupano. */
    val sizeBytes: Long = 0L,
    val createdAt: Instant = Instant.EPOCH
) {
    val isLinked: Boolean get() = activityId != null || eventId != null
    val isInGallery: Boolean get() = !galleryUri.isNullOrBlank()
    val hasCaption: Boolean get() = !caption.isNullOrBlank()
}

enum class PhotoSource {
    FOTOCAMERA,
    GALLERIA;

    companion object {
        fun fromStorage(value: String?): PhotoSource =
            entries.firstOrNull { it.name == value } ?: FOTOCAMERA
    }
}
