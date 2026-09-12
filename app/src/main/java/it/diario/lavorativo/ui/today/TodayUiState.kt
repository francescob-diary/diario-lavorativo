package it.diario.lavorativo.ui.today

import it.diario.lavorativo.domain.service.SiteDistance
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.UserSettings
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkTimeSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Quale finestra di dialogo e' aperta nella schermata Oggi. */
enum class TodayDialog {
    NESSUNA,
    SCELTA_CANTIERE,
    NUOVO_CANTIERE,
    MODIFICA_INGRESSO,
    MODIFICA_USCITA
}

/**
 * Esito del riconoscimento del cantiere tramite GPS, in forma pronta per la UI.
 * Resta un suggerimento: l'ultima parola e' sempre dell'utente.
 */
sealed interface SiteSuggestion {
    data object Idle : SiteSuggestion
    data object Searching : SiteSuggestion
    /** Un cantiere riconosciuto, da confermare con un tocco. */
    data class Found(val site: Site, val distanceMeters: Int) : SiteSuggestion
    /** Piu' cantieri vicini: si scelgono dalla lista. */
    data class Several(val candidates: List<SiteDistance>) : SiteSuggestion
    /** Fuori da tutti i raggi: mostra il piu' vicino come riferimento. */
    data class OutOfRange(val nearestName: String?, val distanceMeters: Int?) : SiteSuggestion
    /** Nessun cantiere ha coordinate salvate. */
    data object NoGeolocatedSites : SiteSuggestion
    data class Unavailable(val reason: String) : SiteSuggestion
}

/**
 * Stato completo della schermata Oggi.
 * La schermata si limita a disegnare questo oggetto: nessun calcolo nella UI.
 */
data class TodayUiState(
    val date: LocalDate,
    val now: Instant,
    val zone: ZoneId,
    val day: WorkDay? = null,
    val summary: WorkTimeSummary = WorkTimeSummary(),
    val activeSites: List<Site> = emptyList(),
    val settings: UserSettings = UserSettings(),
    /** Giornata di un giorno precedente rimasta aperta (avviso in cima alla schermata). */
    val unclosedPreviousDay: WorkDay? = null,
    val dialog: TodayDialog = TodayDialog.NESSUNA,
    val suggestion: SiteSuggestion = SiteSuggestion.Idle,
    val isLoading: Boolean = true,
    val message: String? = null
) {
    val isDayStarted: Boolean get() = day?.isStarted == true
    val isDayRunning: Boolean get() = day?.isRunning == true
    val isDayClosed: Boolean get() = day?.isClosed == true
    val isOnBreak: Boolean get() = day?.isOnBreak == true
}
