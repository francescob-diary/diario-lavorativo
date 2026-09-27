package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.Site

/**
 * L'indirizzo del cantiere come va scritto nella colonna CLIENTE O
 * CANTIERE del rapportino: "Via Nome, civico, citta'", per esempio
 * "Viale Sarca, 85, Milano". Il nome del cliente non ci va.
 *
 * Regole:
 * - il civico in fondo all'indirizzo si stacca con una virgola, anche se
 *   e' stato scritto attaccato ("Viale Sarca 85" diventa "Viale Sarca, 85");
 * - la citta' si aggiunge solo se c'e';
 * - i pezzi vuoti si saltano: niente virgole doppie o in fondo;
 * - se l'indirizzo manca del tutto si scrive il nome del cantiere, perche'
 *   una casella vuota su un foglio che va in sede genera domande.
 *
 * Puro Kotlin, niente Android: si prova con i test.
 */
object SiteAddressFormatter {

    /** Civico in fondo: 85, 85/A, 85B, 12 bis. */
    private val CIVICO_IN_FONDO = Regex("""^(.*?)[\s,]+(\d+\s*(?:/\s*\w+|[a-zA-Z]|bis|ter)?)$""")

    fun forReport(site: Site?): String? {
        if (site == null) return null
        val strada = site.address?.let { normalizeStreet(it) }.orEmpty()
        val citta = site.city?.trim().orEmpty().trim(',', ' ')

        val pezzi = listOf(strada, citta).filter { it.isNotBlank() }
        if (strada.isBlank()) return site.name.trim().ifBlank { null }
        return pezzi.joinToString(", ")
    }

    internal fun normalizeStreet(raw: String): String {
        // Spazi doppi e virgole ripetute fuori, poi il civico staccato.
        val pulito = raw.trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*,\s*"""), ", ")
            .replace(Regex("""(, )+"""), ", ")
            .trim(',', ' ')
        val match = CIVICO_IN_FONDO.matchEntire(pulito) ?: return pulito
        val via = match.groupValues[1].trim(',', ' ')
        val civico = match.groupValues[2].replace(" ", "")
        if (via.isBlank()) return pulito
        return "$via, $civico"
    }
}
