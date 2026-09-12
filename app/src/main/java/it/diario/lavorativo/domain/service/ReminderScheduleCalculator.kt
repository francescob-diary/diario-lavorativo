package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.ReminderSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Calcola quando deve suonare il promemoria e quale settimana riguarda.
 *
 * Separato dal codice Android apposta: la data giusta e' la parte che si
 * sbaglia facilmente, e cosi' si puo' verificare davvero con i test.
 */
class ReminderScheduleCalculator {

    /**
     * Prossimo momento in cui far scattare il promemoria, a partire da [from].
     * Se l'orario di oggi e' gia' passato si va alla settimana successiva.
     */
    fun nextTrigger(settings: ReminderSettings, from: LocalDateTime): LocalDateTime {
        val candidate = from.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(settings.dayOfWeek))
            .atTime(settings.time)

        return if (candidate.isAfter(from)) {
            candidate
        } else {
            candidate.plusWeeks(1)
        }
    }

    /**
     * Lunedi' della settimana che il promemoria deve riepilogare.
     *
     * Regola: si riepiloga sempre la settimana GIA' CONCLUSA. Avvisare il
     * lunedi' mattina della settimana che comincia in quel momento non
     * avrebbe senso, non c'e' ancora niente da scrivere.
     */
    fun weekToReport(triggerDate: LocalDate): LocalDate =
        triggerDate
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(1)
}
