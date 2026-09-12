package it.diario.lavorativo.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.diario.lavorativo.domain.model.ReminderSettings
import it.diario.lavorativo.domain.model.UserSettings
import it.diario.lavorativo.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "impostazioni")

/** Impostazioni su DataStore: piccole, non relazionali, non hanno bisogno del database. */
class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val STANDARD_MINUTES = intPreferencesKey("standard_work_minutes")
        val OVERTIME_ENABLED = booleanPreferencesKey("overtime_enabled")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_DAY = intPreferencesKey("reminder_day")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    override val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            userName = prefs[Keys.USER_NAME].orEmpty(),
            standardWorkMinutes = prefs[Keys.STANDARD_MINUTES]
                ?: UserSettings.DEFAULT_STANDARD_MINUTES,
            overtimeEnabled = prefs[Keys.OVERTIME_ENABLED] ?: true
        )
    }

    override val reminder: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[Keys.REMINDER_ENABLED] ?: true,
            dayOfWeek = java.time.DayOfWeek.of(
                (prefs[Keys.REMINDER_DAY] ?: java.time.DayOfWeek.MONDAY.value).coerceIn(1, 7)
            ),
            hour = (prefs[Keys.REMINDER_HOUR] ?: ReminderSettings.DEFAULT_HOUR).coerceIn(0, 23),
            minute = (prefs[Keys.REMINDER_MINUTE] ?: ReminderSettings.DEFAULT_MINUTE).coerceIn(0, 59)
        )
    }

    override suspend fun setReminder(settings: ReminderSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDER_ENABLED] = settings.enabled
            prefs[Keys.REMINDER_DAY] = settings.dayOfWeek.value
            prefs[Keys.REMINDER_HOUR] = settings.hour
            prefs[Keys.REMINDER_MINUTE] = settings.minute
        }
    }

    override suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    override suspend fun setStandardWorkMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.STANDARD_MINUTES] = minutes.coerceIn(0, 24 * 60) }
    }

    override suspend fun setOvertimeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OVERTIME_ENABLED] = enabled }
    }
}
