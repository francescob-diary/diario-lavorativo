package it.diario.lavorativo.domain.repository

import it.diario.lavorativo.domain.model.ReminderSettings
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
}
