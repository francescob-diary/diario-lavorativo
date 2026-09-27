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
import it.diario.lavorativo.domain.model.DailyReminderSettings
import it.diario.lavorativo.domain.model.UserSettings
import it.diario.lavorativo.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "impostazioni")

/** Impostazioni su DataStore: piccole, non relazionali, non hanno bisogno del database. */
class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private companion object {
        const val EXPORT_PREFIX = "export_"
    }

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val STANDARD_MINUTES = intPreferencesKey("standard_work_minutes")
        val OVERTIME_ENABLED = booleanPreferencesKey("overtime_enabled")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_DAY = intPreferencesKey("reminder_day")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val DAILY_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val DAILY_HOUR = intPreferencesKey("daily_reminder_hour")
        val DAILY_MINUTE = intPreferencesKey("daily_reminder_minute")
        val DAILY_DAYS = stringPreferencesKey("daily_reminder_days")
        val MODEL_URI = stringPreferencesKey("model_uri")
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

    override val exportChoices: Flow<Map<String, Boolean>> = context.dataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith(EXPORT_PREFIX) && value is Boolean) {
                key.name.removePrefix(EXPORT_PREFIX) to value
            } else {
                null
            }
        }.toMap()
    }

    override val dailyReminder: Flow<DailyReminderSettings> = context.dataStore.data.map { prefs ->
        DailyReminderSettings(
            enabled = prefs[Keys.DAILY_ENABLED] ?: true,
            hour = (prefs[Keys.DAILY_HOUR] ?: DailyReminderSettings.DEFAULT_HOUR).coerceIn(0, 23),
            minute = (prefs[Keys.DAILY_MINUTE] ?: DailyReminderSettings.DEFAULT_MINUTE).coerceIn(0, 59),
            days = prefs[Keys.DAILY_DAYS]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in 1..7 }
                ?.map { java.time.DayOfWeek.of(it) }
                ?.toSet()
                ?: DailyReminderSettings.DEFAULT_DAYS
        )
    }

    override val modelUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.MODEL_URI]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setModelUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.MODEL_URI) else prefs[Keys.MODEL_URI] = uri
        }
    }

    override suspend fun setDailyReminder(settings: DailyReminderSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_ENABLED] = settings.enabled
            prefs[Keys.DAILY_HOUR] = settings.hour
            prefs[Keys.DAILY_MINUTE] = settings.minute
            prefs[Keys.DAILY_DAYS] = settings.days.map { it.value }.sorted().joinToString(",")
        }
    }

    override suspend fun setExportChoice(key: String, value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(EXPORT_PREFIX + key)] = value }
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
