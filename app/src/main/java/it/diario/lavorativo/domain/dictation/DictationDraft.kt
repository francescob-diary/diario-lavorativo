package it.diario.lavorativo.domain.dictation

import it.diario.lavorativo.domain.model.DayType
import java.time.LocalTime

/**
 * Quello che si e' capito dal racconto della giornata, prima della
 * conferma. Tutto e' modificabile nella schermata di conferma: niente
 * finisce nel diario finche' non si preme SALVA.
 */
data class DictationDraft(
    val dayType: DayType = DayType.LAVORO,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val breaks: List<DraftBreak> = emptyList(),
    /** Il primo e' il cantiere principale. */
    val sites: List<DraftSite> = emptyList(),
    val description: String? = null,
    val place: String? = null,
    val materials: String? = null,
    val trips: List<DraftTrip> = emptyList(),
    val km: Int? = null,
    val notes: String? = null,
    /** Domande sui dati importanti che mancano (al massimo tre). */
    val questions: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = start == null && end == null && breaks.isEmpty() && sites.isEmpty() &&
            description.isNullOrBlank() && trips.isEmpty() && km == null &&
            notes.isNullOrBlank() && dayType == DayType.LAVORO
}

data class DraftBreak(val start: LocalTime, val end: LocalTime)

data class DraftSite(
    /** Nome come e' stato detto (o come l'ha riconosciuto il modello). */
    val name: String,
    /** Cantiere gia' esistente a cui corrisponde. Null = nuovo o sconosciuto. */
    val siteId: Long? = null,
    /** Minuti fatti li', se detti. */
    val minutes: Int? = null,
    val work: String? = null
)

data class DraftTrip(
    val from: String? = null,
    val to: String? = null,
    val depart: LocalTime? = null,
    val arrive: LocalTime? = null
)
