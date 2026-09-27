package it.diario.lavorativo.domain.dictation

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import java.time.LocalTime

/**
 * Regole semplici per capire un racconto senza il modello.
 *
 * Capisce le frasi piu' comuni ("dalle 8 alle 17", "pausa dalle 12 alle 13",
 * "ho iniziato alle otto e mezza", i nomi dei cantieri gia' registrati, i
 * chilometri). Su un discorso disordinato sbaglia o lascia vuoto: per quello
 * c'e' il modello. Serve anche a riempire i buchi della risposta del modello.
 */
object RuleBasedDayParser {

    private val NUMERI = linkedMapOf(
        "diciassette" to 17, "diciannove" to 19, "diciotto" to 18, "quattordici" to 14,
        "quindici" to 15, "sedici" to 16, "tredici" to 13, "dodici" to 12, "undici" to 11,
        "dieci" to 10, "nove" to 9, "otto" to 8, "sette" to 7, "sei" to 6, "cinque" to 5,
        "quattro" to 4, "tre" to 3, "due" to 2, "una" to 1, "venti" to 20
    )

    /** Testo minuscolo con i numeri detti a parole trasformati in cifre. */
    internal fun normalizeNumbers(text: String): String {
        var t = " " + text.lowercase()
            .replace("mezzogiorno", "12")
            .replace("mezzanotte", "0") + " "
        NUMERI.forEach { (parola, n) ->
            t = t.replace(Regex("(?<=[\\s'])" + parola + "(?=[\\s,.;:!?])"), n.toString())
        }
        t = t.replace(Regex("(\\d{1,2}) e mezz[ao]"), "$1:30")
            .replace(Regex("(\\d{1,2}) e un quarto"), "$1:15")
            .replace(Regex("(\\d{1,2}) e tre quarti"), "$1:45")
            .replace(Regex("(\\d{1,2})[.,](\\d{2})(?!\\d)"), "$1:$2")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private const val ORA = "(\\d{1,2}(?::\\d{2})?)"

    private fun clock(text: String): LocalTime? {
        val parti = text.split(':')
        val h = parti[0].toIntOrNull() ?: return null
        val m = parti.getOrNull(1)?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    /** Uscita piu' piccola dell'ingresso di mattina: si intende il pomeriggio. */
    private fun afternoon(start: LocalTime?, end: LocalTime): LocalTime =
        if (start != null && !end.isAfter(start) && end.hour < 12) end.plusHours(12) else end

    fun parse(transcript: String, sites: List<Site>): DictationDraft {
        val t = normalizeNumbers(transcript)

        val tipo = when {
            Regex("\\bferie\\b").containsMatchIn(t) -> DayType.FERIE
            Regex("\\bmalat(o|a|tia)\\b").containsMatchIn(t) -> DayType.MALATTIA
            Regex("\\bpermesso\\b").containsMatchIn(t) -> DayType.PERMESSO
            Regex("\\b(festivo|festa)\\b").containsMatchIn(t) -> DayType.FESTIVO
            else -> DayType.LAVORO
        }

        // Pausa con orari: si toglie dal testo, cosi' "dalle 12 alle 13"
        // della pausa non viene preso per l'orario della giornata.
        val pausaRegex = Regex("(pausa|pranzo)[^.]{0,20}?dall[ae] $ORA (?:alle|all') $ORA")
        val pause = mutableListOf<DraftBreak>()
        var resto = t
        pausaRegex.findAll(t).forEach { m ->
            val da = clock(m.groupValues[2])
            val a = clock(m.groupValues[3])
            if (da != null && a != null) {
                val fine = afternoon(da, a)
                if (fine.isAfter(da)) pause += DraftBreak(da, fine)
            }
            resto = resto.replace(m.value, " ")
        }
        if (pause.isEmpty() &&
            Regex("(pausa pranzo|un'ora di pausa|un ora di pausa|pausa di un'ora|pausa di un ora|1 ora di pausa|pausa di 1 ora)")
                .containsMatchIn(t)
        ) {
            pause += DraftBreak(LocalTime.of(12, 0), LocalTime.of(13, 0))
        }

        var inizio: LocalTime? = null
        var fine: LocalTime? = null
        Regex("dall[ae] $ORA (?:alle|all'|fino alle) $ORA").find(resto)?.let { m ->
            inizio = clock(m.groupValues[1])
            fine = clock(m.groupValues[2])?.let { afternoon(inizio, it) }
        }
        if (inizio == null) {
            Regex("(iniziato|cominciato|attaccato|partito|entrato|inizio)[^.]{0,12}?alle $ORA").find(resto)
                ?.let { inizio = clock(it.groupValues[2]) }
        }
        if (fine == null) {
            Regex("(finito|staccato|smontato|uscito|fine)[^.]{0,12}?alle $ORA").find(resto)
                ?.let { m -> fine = clock(m.groupValues[2])?.let { afternoon(inizio, it) } }
        }

        val km = Regex("(\\d{1,4})\\s*(km|chilometri)").find(t)?.groupValues?.get(1)?.toIntOrNull()

        val cantieri = SiteNameMatcher.findInText(transcript, sites).map { s ->
            DraftSite(name = s.name, siteId = s.id)
        }

        val domande = buildList {
            if (tipo == DayType.LAVORO && (inizio == null || fine == null)) {
                add("A che ora hai iniziato e a che ora hai finito?")
            }
            if (tipo == DayType.LAVORO && cantieri.isEmpty() && sites.isNotEmpty()) {
                add("In che cantiere hai lavorato?")
            }
        }

        return DictationDraft(
            dayType = tipo,
            start = inizio,
            end = fine,
            breaks = pause,
            sites = cantieri,
            description = transcript.trim().takeIf { it.isNotEmpty() && tipo == DayType.LAVORO },
            km = km,
            questions = domande
        )
    }

    /**
     * Unisce la risposta del modello con quella delle regole: vince il
     * modello, le regole riempiono solo i buchi.
     */
    fun merge(model: DictationDraft, rules: DictationDraft): DictationDraft = model.copy(
        start = model.start ?: rules.start,
        end = model.end ?: rules.end,
        breaks = model.breaks.ifEmpty { rules.breaks },
        sites = model.sites.ifEmpty { rules.sites },
        km = model.km ?: rules.km
    )
}
