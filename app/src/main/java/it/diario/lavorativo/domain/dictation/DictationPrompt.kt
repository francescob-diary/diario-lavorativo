package it.diario.lavorativo.domain.dictation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Le istruzioni per il modello: cosa deve tirare fuori dal racconto e in
 * che forma. Corte apposta: ogni parola in piu' e' tempo di attesa sul
 * telefono.
 */
object DictationPrompt {

    private val DATA: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)

    fun build(
        transcript: String,
        date: LocalDate,
        siteNames: List<String>,
        previousAnswers: List<Pair<String, String>> = emptyList()
    ): String {
        val cantieri = if (siteNames.isEmpty()) "nessuno registrato" else siteNames.joinToString("; ")
        val risposte = if (previousAnswers.isEmpty()) "" else {
            "\nRisposte a domande fatte prima:\n" + previousAnswers.joinToString("\n") { (d, r) ->
                "- " + d + " -> " + r
            } + "\n"
        }
        return """
Compila il diario di lavoro di un muratore dal suo racconto della giornata.
Rispondi SOLO con un oggetto JSON, senza nessun altro testo.

Formato:
{"tipo":"LAVORO","ingresso":"08:00","uscita":"17:00","pause":[{"da":"12:00","a":"13:00"}],"cantieri":[{"nome":"","ore":null,"lavoro":""}],"luogo":null,"lavoro":"","materiali":null,"spostamenti":[{"da":"","a":"","partenza":null,"arrivo":null}],"km":null,"note":null,"domande":[]}

Regole:
- tipo: LAVORO, FERIE, PERMESSO, MALATTIA oppure FESTIVO.
- Orari in formato HH:MM sulle 24 ore. Di pomeriggio "le cinque" sono "17:00".
- Se un dato non e' stato detto metti null o una lista vuota. Non inventare niente.
- cantieri: il primo e' il principale. "ore" solo se ha detto quante ore ha fatto in quel cantiere.
- Una zona o una stanza (lounge, bagno, cucina, piano terra, facciata) NON e' un cantiere: va nel lavoro, es. "cubo nella zona lounge".
- Se nomina un cantiere solo per sbaglio o per dire dove si trova una cosa, non aggiungerlo.
- Se un cantiere corrisponde a uno di questi, scrivi il nome esatto: $cantieri
- lavoro: cosa ha fatto, in poche parole. materiali: i materiali nominati.
- spostamenti: viaggi da un posto a un altro, con gli orari se detti.
- Se gli orari non sono detti lasciali null: valgono quelli standard 08:00-17:00 con pausa 12:00-13:00.
- domande: al massimo 2 domande brevi, solo se manca il cantiere o il lavoro fatto. Mai sugli orari.

Oggi e' ${date.format(DATA)}.
$risposte
Racconto:
"$transcript"
""".trimIndent()
    }
}
