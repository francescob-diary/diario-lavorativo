package it.diario.lavorativo.domain.dictation

/**
 * Numeri scritti a parole in italiano, come li scrive il riconoscimento
 * vocale: "ottanta", "centoventi", "trentuno", "duecentocinquanta",
 * "milleduecento". Null se la parola non e' un numero.
 */
object ItalianNumbers {

    private val VALORI: List<Pair<String, Int>> = listOf(
        "diciassette" to 17, "diciannove" to 19, "diciotto" to 18, "quattordici" to 14,
        "quindici" to 15, "sedici" to 16, "tredici" to 13, "dodici" to 12, "undici" to 11,
        "dieci" to 10, "cinquanta" to 50, "cinquant" to 50, "quaranta" to 40, "quarant" to 40,
        "sessanta" to 60, "sessant" to 60, "settanta" to 70, "settant" to 70,
        "ottanta" to 80, "ottant" to 80, "novanta" to 90, "novant" to 90,
        "trenta" to 30, "trent" to 30, "venti" to 20, "vent" to 20,
        "quattro" to 4, "cinque" to 5, "sette" to 7, "otto" to 8, "nove" to 9,
        "zero" to 0, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "tré" to 3, "sei" to 6, "un" to 1
    ).sortedByDescending { it.first.length }

    fun parse(word: String): Int? {
        var resto = word.lowercase().trim()
        if (resto.isEmpty()) return null
        resto.toIntOrNull()?.let { return it }
        var totale = 0
        var corrente = 0
        while (resto.isNotEmpty()) {
            when {
                resto.startsWith("mille") -> {
                    totale += (if (corrente == 0) 1 else corrente) * 1000
                    corrente = 0
                    resto = resto.removePrefix("mille")
                }
                resto.startsWith("mila") -> {
                    if (corrente == 0) return null
                    totale += corrente * 1000
                    corrente = 0
                    resto = resto.removePrefix("mila")
                }
                resto.startsWith("cento") -> {
                    corrente = (if (corrente == 0) 1 else corrente) * 100
                    resto = resto.removePrefix("cento")
                    // "centottanta", "centotto": la "o" e' stata mangiata.
                    if (resto.startsWith("tt")) resto = "o$resto"
                }
                else -> {
                    val trovato = VALORI.firstOrNull { resto.startsWith(it.first) } ?: return null
                    corrente += trovato.second
                    resto = resto.removePrefix(trovato.first)
                }
            }
        }
        return totale + corrente
    }

    private val KM = Regex(
        "(?i)(\\d{1,4}|[a-zàèéìòù]{2,})\\s*(?:km|chilometri|kilometri|chilometro|kilometro)(?![a-z])"
    )

    /** Chilometri detti nel racconto: "80 km", "ottanta chilometri". */
    fun findKm(text: String): Int? = KM.findAll(text)
        .mapNotNull { parse(it.groupValues[1]) }
        .firstOrNull { it in 1..2000 }
}
