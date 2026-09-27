package it.diario.lavorativo.domain.repository

import it.diario.lavorativo.domain.model.ReminderSettings
import it.diario.lavorativo.domain.model.DailyReminderSettings
import it.diario.lavorativo.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** Contratto per le impostazioni utente (requisito 18). */
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setUserName(name: String)
    suspend fun setStandardWorkMinutes(minutes: Int)
    suspend fun setOvertimeEnabled(enabled: Boolean)

    /** Promemoria settimanale per la consegna del foglio in sede. */
    val reminder: Flow<ReminderSettings>

    suspend fun setReminder(settings: ReminderSettings)

    /**
     * Interruttori dell'export ("cosa mettere dentro"), salvati come li si
     * lascia. Chiave assente = mai toccato: vale il valore di partenza.
     */
    val exportChoices: Flow<Map<String, Boolean>>

    /** Promemoria di fine giornata per compilare il diario. */
    val dailyReminder: Flow<DailyReminderSettings>

    suspend fun setDailyReminder(settings: DailyReminderSettings)

    /** File del modello per la dettatura (indirizzo del documento), null se non scelto. */
    val modelUri: Flow<String?>

    suspend fun setModelUri(uri: String?)


    suspend fun setExportChoice(key: String, value: Boolean)
}
