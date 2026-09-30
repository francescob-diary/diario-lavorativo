package it.diario.lavorativo.domain.dictation

/**
 * Pulizia di base del testo dettato, che funziona sempre, anche senza
 * Gemma: il microfono scrive tutto attaccato, senza punti ne' maiuscole.
 *
 * - toglie le esitazioni ("eh", "cioe'", "diciamo"...);
 * - toglie le parole ripetute di fila ("poi poi", "abbiamo abbiamo");
 * - mette il punto prima di "poi", "dopo", "nel pomeriggio"... quando la
 *   frase e' gia' lunga, cosi' il racconto si divide in frasi;
 * - maiuscola all'inizio di ogni frase e punto in fondo.
 *
 * Non riscrive le parole: quello lo fa Gemma quando c'e'.
 */
object TextCleaner {

    private val RIEMPITIVI = listOf(
        "eh", "ehm", "uhm", "mm", "cioè", "cioe", "diciamo", "insomma",
        "vabbè", "vabbe", "praticamente"
    )

    private val CONNETTORI = listOf(
        "nel pomeriggio", "di pomeriggio", "la mattina", "in mattinata", "alla fine",
        "più tardi", "piu tardi", "verso sera", "dopodiché", "dopodiche",
        "successivamente", "poi", "dopo", "infine", "quindi"
    ).map { it.split(' ') }.sortedByDescending { it.size }

    private val FINE_FRASE = charArrayOf('.', '!', '?')
    private val PUNTEGGIATURA = charArrayOf('.', ',', ';', ':', '!', '?')

    /** Parole prima di un taglio: dopo 6 parole una nuova frase ha senso. */
    private const val MIN_PAROLE = 6

    private val RIEMPITIVI_REGEX = Regex(
        "(?i)(?<![\\p{L}'])(?:" + RIEMPITIVI.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}'])[,]?\\s*"
    )

    fun clean(input: String): String {
        var t = input.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return t
        t = RIEMPITIVI_REGEX.replace(t, " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return t
        t = removeRepeats(t)
        t = splitSentences(t)
        t = capitalizeSentences(t)
        t = t.replace(Regex("\\s+([,.;:!?])"), "$1")
        if (t.last() !in FINE_FRASE) t += "."
        return t
    }

    /** "poi poi" -> "poi", "abbiamo fatto abbiamo fatto" -> "abbiamo fatto". */
    internal fun removeRepeats(text: String): String {
        var t = text
        for (n in 3 downTo 1) {
            val gruppo = (1..n).joinToString("\\s+") { "\\p{L}+" }
            t = Regex("(?i)(?<!\\p{L})($gruppo)(?:\\s+\\1)+(?!\\p{L})").replace(t, "$1")
        }
        return t
    }

    private fun splitSentences(text: String): String {
        val parole = text.split(' ')
        val basse = parole.map { it.lowercase().trim(',', '.') }
        val out = mutableListOf<String>()
        var dallUltimo = 0
        for (i in parole.indices) {
            val connettore = CONNETTORI.firstOrNull { c ->
                i + c.size <= basse.size && basse.subList(i, i + c.size) == c
            }
            if (connettore != null && dallUltimo >= MIN_PAROLE && out.isNotEmpty() &&
                out.last().last() !in PUNTEGGIATURA
            ) {
                // "... cartongesso e dopo" -> "... cartongesso. Dopo"
                if (out.size > 1 && out.last().lowercase() in setOf("e", "ed")) out.removeAt(out.size - 1)
                out[out.size - 1] = out.last() + "."
                dallUltimo = 0
            }
            out += parole[i]
            dallUltimo++
            if (parole[i].isNotEmpty() && parole[i].last() in FINE_FRASE) dallUltimo = 0
        }
        return out.joinToString(" ")
    }

    private fun capitalizeSentences(text: String): String {
        val sb = StringBuilder(text)
        var inizio = true
        for (i in sb.indices) {
            val c = sb[i]
            if (inizio && c.isLetter()) {
                sb.setCharAt(i, c.uppercaseChar())
                inizio = false
            } else if (c in FINE_FRASE) {
                inizio = true
            }
        }
        return sb.toString()
    }
}
