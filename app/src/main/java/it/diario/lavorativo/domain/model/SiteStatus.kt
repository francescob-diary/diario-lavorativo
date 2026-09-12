package it.diario.lavorativo.domain.model

/** Stato del cantiere. Solo i cantieri ATTIVO compaiono nella scelta rapida. */
enum class SiteStatus {
    ATTIVO,
    TERMINATO;

    companion object {
        fun fromStorage(value: String?): SiteStatus =
            entries.firstOrNull { it.name == value } ?: ATTIVO
    }
}
