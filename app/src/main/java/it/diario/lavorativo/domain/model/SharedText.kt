package it.diario.lavorativo.domain.model

/**
 * Testo arrivato dalla condivisione di sistema.
 *
 * [subject] e' l'oggetto quando la sorgente e' la posta; le app di messaggistica
 * di solito non lo valorizzano.
 */
data class SharedText(
    val text: String,
    val subject: String? = null,
    /** Nome del pacchetto che ha condiviso, quando il sistema lo espone. */
    val sourcePackage: String? = null
)
