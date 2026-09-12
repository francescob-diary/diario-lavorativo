package it.diario.lavorativo.domain.model

import java.time.Duration

/**
 * Risultato del calcolo delle ore di una giornata.
 * Prodotto esclusivamente da WorkTimeCalculator: nessuna schermata deve
 * ricalcolare questi valori per conto proprio.
 */
data class WorkTimeSummary(
    /** Tempo trascorso fra ingresso e uscita (o adesso, se la giornata e' aperta). */
    val gross: Duration = Duration.ZERO,
    /** Totale delle pause, sovrapposizioni escluse. */
    val breaks: Duration = Duration.ZERO,
    /** Tempo effettivamente lavorato = lordo - pause. Mai negativo. */
    val net: Duration = Duration.ZERO,
    /** Quanto il netto supera l'orario standard. */
    val overtime: Duration = Duration.ZERO,
    /** Quanto manca al raggiungimento dell'orario standard. */
    val deficit: Duration = Duration.ZERO,
    /** Orario standard usato per il calcolo (globale o override della giornata). */
    val standard: Duration = Duration.ZERO,
    val isStarted: Boolean = false,
    val isRunning: Boolean = false,
    val isOnBreak: Boolean = false,
    /** Durata della pausa attualmente in corso, se presente. */
    val currentBreak: Duration? = null
)
