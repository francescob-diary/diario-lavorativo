package it.diario.lavorativo.ui.history

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.YearMonth
import java.util.Locale

private val LOCALE_IT = Locale("it", "IT")

internal val LONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", LOCALE_IT)

private val HOUR_MINUTE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", LOCALE_IT)

/** "Settembre 2026", con l'iniziale maiuscola. */
internal fun monthLabel(month: YearMonth): String {
    val name = month.month.getDisplayName(TextStyle.FULL, LOCALE_IT)
    val capitalized = name.substring(0, 1).uppercase(LOCALE_IT) + name.substring(1)
    return capitalized + " " + month.year
}

/** "07:30 - 17:00", oppure "dalle 07:30" se la giornata e' ancora aperta. */
internal fun timesLabel(day: WorkDay): String {
    val zone = ZoneId.systemDefault()
    val start = day.startTime?.atZone(zone)?.format(HOUR_MINUTE)
    val end = day.endTime?.atZone(zone)?.format(HOUR_MINUTE)
    return when {
        start != null && end != null -> start + " - " + end
        start != null -> "dalle " + start
        else -> "orari non registrati"
    }
}

/** Etichetta del tipo di giornata, al singolare o al plurale. */
internal fun dayTypeLabel(type: DayType, count: Int): String = when (type) {
    DayType.LAVORO -> if (count == 1) "giornata" else "giornate"
    DayType.FERIE -> if (count == 1) "giorno di ferie" else "giorni di ferie"
    DayType.PERMESSO -> if (count == 1) "permesso" else "permessi"
    DayType.MALATTIA -> if (count == 1) "giorno di malattia" else "giorni di malattia"
    DayType.FESTIVO -> if (count == 1) "festivo" else "festivi"
}
