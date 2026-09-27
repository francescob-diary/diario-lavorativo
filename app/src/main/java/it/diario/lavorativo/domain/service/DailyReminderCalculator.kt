package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DailyReminderSettings
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkDay
import java.time.LocalDateTime

/**
 * Quando deve suonare il promemoria di fine giornata, e se serve davvero.
 * Puro Kotlin: si prova con i test.
 */
object DailyReminderCalculator {

    /** Prossimo scatto dopo [from], nei giorni scelti. Null se nessun giorno e' scelto. */
    fun nextTrigger(settings: DailyReminderSettings, from: LocalDateTime): LocalDateTime? {
        if (!settings.enabled || settings.days.isEmpty()) return null
        for (offset in 0L..7L) {
            val giorno = from.toLocalDate().plusDays(offset)
            if (giorno.dayOfWeek !in settings.days) continue
            val candidato = giorno.atTime(settings.time)
            if (candidato.isAfter(from)) return candidato
        }
        return null
    }

    /**
     * La giornata e' gia' a posto? Allora niente notifica. E' a posto se e'
     * un'assenza (ferie, malattia...) oppure se ha ingresso e uscita.
     */
    fun isDayCompiled(day: WorkDay?): Boolean {
        if (day == null) return false
        if (day.dayType != DayType.LAVORO) return true
        return day.startTime != null && day.endTime != null
    }
}
