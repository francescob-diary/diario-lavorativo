package it.diario.lavorativo.core.util

/**
 * Soldi e numeri digitati a mano.
 *
 * Sembra una sciocchezza e invece e' il punto dove si perdono i dati: alla
 * pompa si scrive "76,15" con la virgola, la tastiera del telefono a volte
 * mette il punto, e chi ha fretta scrive "76.1" o "76,1 euro". Tutte queste
 * forme devono diventare lo stesso numero, senza far arrabbiare nessuno e
 * senza inventare cifre.
 *
 * Kotlin puro, quindi provabile: e' l'unico modo di essere sicuri che un
 * importo digitato di corsa al distributore non diventi dieci volte tanto.
 */
object MoneyFormat {

    /**
     * Da testo a centesimi interi.
     *
     * Restituisce null se non si capisce: meglio dire "non ho capito" che
     * salvare uno zero che poi in fondo alla colonna nessuno ritrova.
     */
    fun parseCents(text: String?): Long? {
        val pulito = clean(text) ?: return null
        val negativo = pulito.startsWith("-")
        val corpo = pulito.removePrefix("-")

        val pezzi = corpo.split('.')
        if (pezzi.size > 2) return null

        val interi = pezzi[0].ifEmpty { "0" }
        if (!interi.all { it.isDigit() }) return null

        // Piu' di due decimali: si tengono i primi due e basta. Non si
        // arrotonda, perche' un importo non e' una misura: sullo scontrino
        // i centesimi sono quelli e non ce ne sono altri.
        val decimali = (pezzi.getOrNull(1) ?: "").take(2).padEnd(2, '0')
        if (!decimali.all { it.isDigit() }) return null

        val valore = try {
            interi.toLong() * 100L + decimali.toLong()
        } catch (e: NumberFormatException) {
            return null
        }
        return if (negativo) -valore else valore
    }

    /** Da centesimi a testo con la virgola: 7615 diventa "76,15". */
    fun formatCents(cents: Long): String {
        val segno = if (cents < 0) "-" else ""
        val assoluto = if (cents < 0) -cents else cents
        return segno + (assoluto / 100).toString() + "," +
            (assoluto % 100).toString().padStart(2, '0')
    }

    /** Come sopra, con il simbolo: "76,15 EUR" scritto all'italiana. */
    fun formatEuro(cents: Long): String = formatCents(cents) + " euro"

    /**
     * Un numero con la virgola, tipo i litri.
     *
     * Non passa dai centesimi perche' i litri hanno tre decimali sulle
     * colonnine e due sullo scontrino: qui serve il numero, non il denaro.
     */
    fun parseDecimal(text: String?): Double? {
        val pulito = clean(text) ?: return null
        return pulito.toDoubleOrNull()
    }

    /** Un numero intero come i chilometri: niente virgola, niente punti. */
    fun parseInt(text: String?): Int? {
        val pulito = text?.trim()?.replace(".", "")?.replace(" ", "") ?: return null
        if (pulito.isEmpty()) return null
        return pulito.toIntOrNull()?.takeIf { it >= 0 }
    }

    /** Numero con la virgola per mostrarlo: 8.0 con 2 decimali fa "8,00". */
    fun formatDecimal(value: Double, decimals: Int = 2): String {
        var scala = 1L
        repeat(decimals) { scala *= 10L }
        val arrotondato = Math.round(value * scala)
        val negativo = arrotondato < 0
        val assoluto = if (negativo) -arrotondato else arrotondato
        val interi = assoluto / scala
        val resto = assoluto % scala
        val segno = if (negativo) "-" else ""
        if (decimals == 0) return segno + interi.toString()
        return segno + interi.toString() + "," + resto.toString().padStart(decimals, '0')
    }

    /**
     * Toglie il rumore e riporta tutto al punto decimale, che e' quello che
     * capisce Kotlin. Toglie anche gli spazi e la parola "euro", che chi
     * detta il testo si porta dietro senza accorgersene.
     */
    private fun clean(text: String?): String? {
        var s = text?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        s = s.replace("euro", "")
            .replace("eur", "")
            .replace("litri", "")
            .replace("lt", "")
            .replace("km", "")
            .replace(" ", "")
            .replace("\u20ac", "")
        if (s.isEmpty()) return null

        // Una virgola sola: e' il separatore decimale italiano.
        s = s.replace(',', '.')

        // Restano solo cifre, punto e meno; qualsiasi altra cosa vuol dire
        // che non si e' capito, e allora si dice.
        if (!s.all { it.isDigit() || it == '.' || it == '-' }) return null
        return s
    }
}
