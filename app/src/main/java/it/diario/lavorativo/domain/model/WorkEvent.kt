package it.diario.lavorativo.domain.model

import java.time.Instant

/**
 * Un fatto particolare accaduto nella giornata: un fermo, un ritardo,
 * una consegna, un problema.
 *
 * A differenza dell'attivita', l'evento ha sempre un orario: serve proprio
 * a dire QUANDO e' successo qualcosa, ed e' la voce che finisce nelle
 * contestazioni.
 */
data class WorkEvent(
    val id: Long = 0L,
    val workDayId: Long,
    val type: EventType,
    val time: Instant,
    val title: String,
    val description: String? = null,
    /** Durata del disagio in minuti, per fermi e ritardi. */
    val durationMinutes: Int? = null,
    val severity: EventSeverity = EventSeverity.NORMALE,
    /** Se true la questione e' ancora aperta e va ripresa. */
    val unresolved: Boolean = false,
    val createdAt: Instant = Instant.EPOCH
)

enum class EventType {
    RITARDO,
    FERMO_LAVORI,
    CONSEGNA_MATERIALE,
    MATERIALE_MANCANTE,
    VISITA,
    SOPRALLUOGO_TECNICO,
    INFORTUNIO,
    GUASTO_ATTREZZATURA,
    MALTEMPO,
    VARIANTE,
    PROBLEMA,
    ALTRO;

    companion object {
        fun fromStorage(value: String?): EventType =
            entries.firstOrNull { it.name == value } ?: ALTRO
    }
}

/** Serve a far risaltare nell'elenco cio' che conta davvero. */
enum class EventSeverity {
    NORMALE,
    IMPORTANTE,
    GRAVE;

    companion object {
        fun fromStorage(value: String?): EventSeverity =
            entries.firstOrNull { it.name == value } ?: NORMALE
    }
}
