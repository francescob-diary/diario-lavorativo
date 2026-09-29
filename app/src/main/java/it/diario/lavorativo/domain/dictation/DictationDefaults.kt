package it.diario.lavorativo.domain.dictation

import it.diario.lavorativo.domain.model.DayType
import java.time.LocalTime

/**
 * Orari standard della giornata di lavoro: 08:00-17:00 con pausa pranzo
 * 12:00-13:00. Valgono sempre, a meno che nel racconto si dica altro.
 */
object DictationDefaults {

    val START: LocalTime = LocalTime.of(8, 0)
    val END: LocalTime = LocalTime.of(17, 0)
    val LUNCH_START: LocalTime = LocalTime.of(12, 0)
    val LUNCH_END: LocalTime = LocalTime.of(13, 0)

    fun apply(draft: DictationDraft): DictationDraft {
        if (draft.dayType != DayType.LAVORO) return draft
        val inizio = draft.start ?: START
        val fine = draft.end ?: END
        // La pausa pranzo standard solo se la giornata la contiene tutta:
        // chi ha detto "dalle 13 alle 18" non ha fatto la pausa a mezzogiorno.
        val pause = draft.breaks.ifEmpty {
            if (!inizio.isAfter(LUNCH_START) && !fine.isBefore(LUNCH_END)) {
                listOf(DraftBreak(LUNCH_START, LUNCH_END))
            } else {
                emptyList()
            }
        }
        return draft.copy(start = inizio, end = fine, breaks = pause)
    }

    /** Domanda sugli orari: con gli orari standard non serve piu' farla. */
    fun isAboutTimes(question: String): Boolean {
        val q = question.lowercase()
        return listOf("che ora", "orari", "orario", "iniziato", "finito", "a che ora", "pausa")
            .any { q.contains(it) }
    }
}
