package it.diario.lavorativo.domain.dictation

import it.diario.lavorativo.domain.model.Site
import java.text.Normalizer

/**
 * Riconosce un cantiere detto a voce fra quelli gia' registrati.
 *
 * Si confrontano nome, indirizzo e citta' senza maiuscole ne' accenti:
 * "viale sarca" deve trovare il cantiere "Rossi - Viale Sarca 85".
 */
object SiteNameMatcher {

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val PAROLE_VUOTE = setOf(
        "via", "viale", "piazza", "corso", "largo", "strada", "vicolo", "cantiere",
        "casa", "del", "della", "dei", "delle", "di", "da", "in", "a", "al", "alla", "lo", "la", "il"
    )

    private fun tokens(text: String): Set<String> =
        normalize(text).split(' ').filter { it.length >= 3 && it !in PAROLE_VUOTE }.toSet()

    private val ZONE = setOf(
        "zona", "area", "lounge", "longe", "lonsg", "bagno", "bagni", "cucina", "soggiorno",
        "salone", "sala", "camera", "camere", "stanza", "stanze", "corridoio", "ingresso",
        "piano", "terra", "primo", "secondo", "terrazzo", "terrazza", "balcone", "cortile",
        "scala", "scale", "facciata", "tetto", "sottotetto", "cantina", "reception", "hall",
        "ufficio", "uffici", "spogliatoio", "spogliatoi", "parete", "pareti", "muro", "soffitto",
        "pavimento", "cubo"
    )

    /**
     * "Zona lounge", "bagno al primo piano": parti di un cantiere, non
     * cantieri. Vero se tutte le parole del nome sono di questo tipo.
     */
    fun looksLikeZone(name: String): Boolean {
        val parole = normalize(name).split(' ')
            .filter { it.isNotEmpty() && it !in PAROLE_VUOTE && it.none(Char::isDigit) }
        return parole.isNotEmpty() && parole.all { it in ZONE }
    }

    /** Il cantiere che corrisponde meglio a [spoken], se la somiglianza basta. */
    fun match(spoken: String, sites: List<Site>): Site? {
        val detto = normalize(spoken)
        if (detto.isEmpty()) return null
        sites.firstOrNull { normalize(it.name) == detto }?.let { return it }

        var migliore: Site? = null
        var punteggio = 0.0
        val parole = tokens(spoken)
        sites.forEach { site ->
            val nome = normalize(site.name)
            val s = when {
                nome.isNotEmpty() && (detto.contains(nome) || nome.contains(detto)) -> 1.0
                else -> {
                    val suoi = tokens(site.name) + tokens(site.address.orEmpty()) +
                        tokens(site.city.orEmpty())
                    if (suoi.isEmpty() || parole.isEmpty()) 0.0
                    else parole.count { it in suoi }.toDouble() / parole.size
                }
            }
            if (s > punteggio) {
                punteggio = s
                migliore = site
            }
        }
        return if (punteggio >= 0.6) migliore else null
    }

    /**
     * Cantieri nominati dentro un racconto libero, nell'ordine in cui
     * compaiono. Serve alle regole semplici, quando il modello non c'e'.
     */
    fun findInText(text: String, sites: List<Site>): List<Site> {
        val t = " " + normalize(text) + " "
        return sites.mapNotNull { site ->
            val candidati = listOfNotNull(
                normalize(site.name).takeIf { it.length >= 3 },
                // L'indirizzo senza il civico: a voce il numero spesso non si dice.
                site.address?.let { normalize(it).replace(Regex(" \\d+\\w*$"), "").trim() }
                    ?.takeIf { it.length >= 5 }
            )
            val pos = candidati.map { t.indexOf(" " + it) }.filter { it >= 0 }.minOrNull()
            pos?.let { it to site }
        }.sortedBy { it.first }.map { it.second }
    }
}
