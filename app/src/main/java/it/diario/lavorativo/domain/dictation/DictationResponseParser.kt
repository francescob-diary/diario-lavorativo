package it.diario.lavorativo.domain.dictation

import it.diario.lavorativo.core.json.JsonException
import it.diario.lavorativo.core.json.JsonParser
import it.diario.lavorativo.core.json.JsonValue
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.service.TimeTextParser
import java.time.LocalTime

/**
 * Legge la risposta del modello e la trasforma in una bozza.
 *
 * Tollerante apposta: un modello piccolo a volte mette il JSON dentro
 * delle virgolette di codice, lascia una virgola di troppo o scrive le ore
 * come testo. Quello che non si capisce si scarta, non fa cadere tutto.
 */
object DictationResponseParser {

    fun parse(response: String, sites: List<Site>): DictationDraft? {
        val obj = extractObject(response) ?: return null

        val tipo = obj.text("tipo")?.uppercase()?.let { t ->
            DayType.entries.firstOrNull { it.name == t }
        } ?: DayType.LAVORO

        val cantieri = obj.objects("cantieri").mapNotNull { c ->
            val nome = c.text("nome")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val trovato = SiteNameMatcher.match(nome, sites)
            DraftSite(
                name = trovato?.name ?: nome,
                siteId = trovato?.id,
                minutes = c.hours("ore"),
                work = c.text("lavoro")
            )
        }.distinctBy { it.siteId ?: it.name.lowercase() }
            .let { tidySites(it) }

        val pause = obj.objects("pause").mapNotNull { p ->
            val da = p.time("da") ?: return@mapNotNull null
            val a = p.time("a") ?: return@mapNotNull null
            if (a.isAfter(da)) DraftBreak(da, a) else null
        }

        val viaggi = obj.objects("spostamenti").mapNotNull { v ->
            val trip = DraftTrip(
                from = v.text("da"),
                to = v.text("a"),
                depart = v.time("partenza"),
                arrive = v.time("arrivo")
            )
            if (trip.from == null && trip.to == null) null else trip
        }

        val domande = obj.array("domande")
            .mapNotNull { (it as? JsonValue.Str)?.value?.trim()?.takeIf { q -> q.length > 3 } }
            .take(2)

        return DictationDraft(
            dayType = tipo,
            start = obj.time("ingresso"),
            end = obj.time("uscita"),
            breaks = pause,
            sites = cantieri,
            description = obj.text("lavoro"),
            place = obj.text("luogo"),
            materials = obj.text("materiali"),
            trips = viaggi,
            km = obj.number("km")?.toInt()?.takeIf { it in 0..5000 },
            notes = obj.text("note"),
            questions = domande
        )
    }

    /**
     * Pulizia dei cantieri proposti dal modello:
     * - le zone di un cantiere (lounge, bagno, cucina, piano terra...) non
     *   sono cantieri: si tolgono;
     * - i cantieri gia' registrati vanno davanti a quelli sconosciuti;
     * - se ce n'e' almeno uno registrato, gli sconosciuti senza ore si
     *   scartano: quasi sempre sono pezzi del racconto presi per nomi.
     */
    internal fun tidySites(list: List<DraftSite>): List<DraftSite> {
        val senzaZone = list.filter { it.siteId != null || !SiteNameMatcher.looksLikeZone(it.name) }
        val noti = senzaZone.filter { it.siteId != null }
        val ignoti = senzaZone.filter { it.siteId == null }
        return if (noti.isEmpty()) ignoti else noti + ignoti.filter { it.minutes != null }
    }

    /** Il pezzo fra la prima { e l'ultima }, con le correzioni piu' comuni. */
    internal fun extractObject(response: String): JsonValue.Obj? {
        val inizio = response.indexOf('{')
        val fine = response.lastIndexOf('}')
        if (inizio < 0 || fine <= inizio) return null
        val grezzo = response.substring(inizio, fine + 1)
        val tentativi = listOf(
            grezzo,
            grezzo.replace(Regex(",\\s*([}\\]])"), "$1")
        )
        for (t in tentativi) {
            try {
                return JsonParser.parseObject(t)
            } catch (e: JsonException) {
                // si prova la versione successiva
            } catch (e: RuntimeException) {
                // idem
            }
        }
        return null
    }

    // ------------------------------------------------ letture tolleranti

    private fun JsonValue.Obj.text(key: String): String? = when (val v = this[key]) {
        is JsonValue.Str -> v.value.trim().takeIf { it.isNotEmpty() && it.lowercase() != "null" }
        is JsonValue.Num -> v.raw
        else -> null
    }

    private fun JsonValue.Obj.number(key: String): Double? = when (val v = this[key]) {
        is JsonValue.Num -> v.raw.toDoubleOrNull()
        is JsonValue.Str -> v.value.replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull()
        else -> null
    }

    private fun JsonValue.Obj.time(key: String): LocalTime? =
        text(key)?.let { TimeTextParser.parseClock(it) }

    /** Ore di un cantiere: 4, "4", "3,5", "3:30" -> minuti. */
    private fun JsonValue.Obj.hours(key: String): Int? = when (val v = this[key]) {
        is JsonValue.Num -> v.raw.toDoubleOrNull()?.let { Math.round(it * 60).toInt() }
        is JsonValue.Str -> TimeTextParser.parseHours(v.value)
        else -> null
    }?.takeIf { it in 1..(24 * 60) }
}
