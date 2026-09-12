package it.diario.lavorativo.domain.model

import java.time.Instant

/**
 * Una lavorazione svolta durante la giornata.
 *
 * L'orario e' facoltativo: in cantiere spesso si annota a fine giornata
 * "ho fatto questo e questo" senza ricordare l'ora esatta. Obbligare a
 * inserirla farebbe solo saltare la registrazione.
 */
data class WorkActivity(
    val id: Long = 0L,
    val workDayId: Long,
    val category: ActivityCategory = ActivityCategory.ALTRO,
    val description: String,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    /** Quantita' libera: "12 mq", "3 bancali", "8 ml". */
    val quantity: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.EPOCH
) {
    val hasTime: Boolean get() = startTime != null
}

/**
 * Categorie pensate sul lavoro edile vero, non generiche.
 * ALTRO esiste apposta: non deve mai capitare di non poter registrare
 * qualcosa perche' manca la voce giusta.
 */
enum class ActivityCategory {
    MURATURA,
    MARMO,
    INTONACO,
    PAVIMENTI,
    RIVESTIMENTI,
    DEMOLIZIONE,
    SCAVO,
    IMPIANTI,
    CARTONGESSO,
    PULIZIA,
    CARICO_SCARICO,
    SOPRALLUOGO,
    PREPARAZIONE,
    ALTRO;

    companion object {
        fun fromStorage(value: String?): ActivityCategory =
            entries.firstOrNull { it.name == value } ?: ALTRO
    }
}
