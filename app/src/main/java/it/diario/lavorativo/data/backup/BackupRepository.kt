package it.diario.lavorativo.data.backup

import android.content.Context
import androidx.room.withTransaction
import it.diario.lavorativo.data.local.DiarioDatabase
import it.diario.lavorativo.domain.backup.BackupContent
import it.diario.lavorativo.domain.backup.BackupSettings
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.model.ReminderSettings
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.DayOfWeek

/**
 * Legge tutto il database per il backup e lo riscrive per il ripristino.
 *
 * Il ripristino avviene dentro una sola transazione: o entra tutto, o non
 * cambia niente. A meta' strada il diario sarebbe peggio che vuoto,
 * perche' sembrerebbe a posto pur avendo le giornate senza le pause o le
 * foto senza le giornate.
 */
class BackupRepository(
    private val context: Context,
    private val database: DiarioDatabase,
    private val settingsRepository: SettingsRepository
) {

    /** Tutte le righe, pronte da scrivere nel file. */
    suspend fun readEverything(): BackupContent {
        val user = settingsRepository.settings.first()
        val reminder = settingsRepository.reminder.first()

        return BackupContent(
            workDays = database.workDayDao().allForBackup().map { it.toBackup() },
            breaks = database.breakDao().allForBackup().map { it.toBackup() },
            sites = database.siteDao().allForBackup().map { it.toBackup() },
            activities = database.activityDao().allForBackup().map { it.toBackup() },
            events = database.eventDao().allForBackup().map { it.toBackup() },
            communications = database.communicationDao().allForBackup().map { it.toBackup() },
            photos = database.photoDao().allForBackup().map { it.toBackup() },
            voiceNotes = database.voiceNoteDao().all().map { it.toBackup() },
            vehicles = database.vehicleDao().allForBackup().map { it.toBackup() },
            fuelStops = database.fuelStopDao().allForBackup().map { it.toBackup() },
            vehicleExpenses = database.vehicleExpenseDao().allForBackup()
                .map { it.toBackup() },
            maintenances = database.maintenanceDao().allForBackup().map { it.toBackup() },
            settings = BackupSettings(
                userName = user.userName,
                standardWorkMinutes = user.standardWorkMinutes,
                overtimeEnabled = user.overtimeEnabled,
                reminderEnabled = reminder.enabled,
                reminderDayOfWeek = reminder.dayOfWeek.value,
                reminderHour = reminder.hour,
                reminderMinute = reminder.minute
            )
        )
    }

    /**
     * Sostituisce tutto il contenuto.
     *
     * L'ordine di cancellazione e inserimento non e' casuale: i cantieri
     * vanno prima delle giornate e le giornate prima di tutto il resto,
     * senno' i collegamenti fra tabelle rifiutano le righe.
     */
    suspend fun replaceEverything(content: BackupContent) {
        database.withTransaction {
            // Le foglie prima, la radice per ultima.
            // Le spese del mezzo puntano sia al mezzo sia alla giornata:
            // vanno tolte prima di tutte e due.
            database.vehicleExpenseDao().deleteAllForRestore()
            database.fuelStopDao().deleteAllForRestore()
            database.maintenanceDao().deleteAllForRestore()
            database.vehicleDao().deleteAllForRestore()
            database.voiceNoteDao().deleteAll()
            database.photoDao().deleteAllForRestore()
            database.communicationDao().deleteAllForRestore()
            database.eventDao().deleteAllForRestore()
            database.activityDao().deleteAllForRestore()
            database.breakDao().deleteAllForRestore()
            database.workDayDao().deleteAllForRestore()
            database.siteDao().deleteAllForRestore()

            // Ora al contrario: prima quello che gli altri richiamano.
            database.siteDao().insertAllForRestore(content.sites.map { it.toEntity() })
            database.workDayDao().insertAllForRestore(content.workDays.map { it.toEntity() })
            database.breakDao().insertAllForRestore(content.breaks.map { it.toEntity() })
            database.activityDao().insertAllForRestore(content.activities.map { it.toEntity() })
            database.eventDao().insertAllForRestore(content.events.map { it.toEntity() })
            database.communicationDao()
                .insertAllForRestore(content.communications.map { it.toEntity() })
            database.photoDao().insertAllForRestore(content.photos.map { it.toEntity() })
            database.voiceNoteDao().insertAll(content.voiceNotes.map { it.toEntity() })

            // Il mezzo prima di quello che lo richiama.
            database.vehicleDao().insertAllForRestore(content.vehicles.map { it.toEntity() })
            database.fuelStopDao()
                .insertAllForRestore(content.fuelStops.map { it.toEntity() })
            database.maintenanceDao()
                .insertAllForRestore(content.maintenances.map { it.toEntity() })
            // Per ultime le spese: hanno bisogno del mezzo e della giornata.
            database.vehicleExpenseDao()
                .insertAllForRestore(content.vehicleExpenses.map { it.toEntity() })
        }

        content.settings?.let { s ->
            settingsRepository.setUserName(s.userName)
            settingsRepository.setStandardWorkMinutes(s.standardWorkMinutes)
            settingsRepository.setOvertimeEnabled(s.overtimeEnabled)
            settingsRepository.setReminder(
                ReminderSettings(
                    enabled = s.reminderEnabled,
                    dayOfWeek = dayOfWeek(s.reminderDayOfWeek),
                    hour = s.reminderHour.coerceIn(0, 23),
                    minute = s.reminderMinute.coerceIn(0, 59)
                )
            )
        }
    }

    /**
     * Un numero fuori posto non deve far cadere il ripristino: si ripiega
     * sul lunedi', che e' il valore iniziale.
     */
    private fun dayOfWeek(value: Int): DayOfWeek =
        DayOfWeek.entries.firstOrNull { it.value == value } ?: DayOfWeek.MONDAY

    /** Cartella delle foto ridotte dentro l'app. */
    fun photoDirectory(): File = File(context.filesDir, "foto")

    /** Cartella delle note vocali. */
    fun voiceDirectory(): File = File(context.filesDir, "note")
}
