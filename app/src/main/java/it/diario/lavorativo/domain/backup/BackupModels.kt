package it.diario.lavorativo.domain.backup

/**
 * Le righe del backup.
 *
 * Ricalcano una per una le tabelle del database, ma sono classi Kotlin
 * pure: nessuna annotazione di Room, nessun riferimento ad Android. Cosi'
 * la parte che scrive e rilegge il backup si puo' provare davvero, e il
 * ripristino non e' un salto nel buio.
 *
 * Le tabelle vere si convertono in queste e viceversa nel livello dati:
 * sono copie campo per campo, senza logica.
 */

/**
 * Versione 1: giornate, cantieri, voci, foto, note vocali.
 * Versione 2: mezzo aziendale e chilometri delle giornate.
 *
 * Un backup vecchio si legge ancora: le tabelle che non c'erano tornano
 * vuote, ed e' la verita'. Al contrario un backup nuovo su un'app vecchia
 * viene rifiutato, perche' quella non saprebbe dove mettere i dati
 * dell'auto e li perderebbe per strada senza dirlo.
 */
const val BACKUP_FORMAT_VERSION = 2

data class BackupWorkDay(
    val id: Long,
    val date: Long,
    val startTime: Long?,
    val endTime: Long?,
    val siteId: Long?,
    val dayType: String,
    val place: String?,
    val role: String?,
    val description: String?,
    val notes: String?,
    val standardMinutesOverride: Int?,
    val travelKm: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class BackupBreak(
    val id: Long,
    val workDayId: Long,
    val startTime: Long,
    val endTime: Long?,
    val type: String,
    val note: String?
)

data class BackupSite(
    val id: Long,
    val name: String,
    val address: String?,
    val city: String?,
    val client: String?,
    val company: String?,
    val contact: String?,
    val phone: String?,
    val startDate: Long?,
    val expectedEndDate: Long?,
    val notes: String?,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class BackupActivity(
    val id: Long,
    val workDayId: Long,
    val category: String,
    val description: String,
    val startTime: Long?,
    val endTime: Long?,
    val quantity: String?,
    val notes: String?,
    val createdAt: Long
)

data class BackupEvent(
    val id: Long,
    val workDayId: Long,
    val type: String,
    val time: Long,
    val title: String,
    val description: String?,
    val durationMinutes: Int?,
    val severity: String,
    val unresolved: Boolean,
    val createdAt: Long
)

data class BackupCommunication(
    val id: Long,
    val workDayId: Long,
    val channel: String,
    val direction: String,
    val time: Long,
    val contactName: String,
    val contactRef: String?,
    val subject: String?,
    val content: String?,
    val durationMinutes: Int?,
    val requiresFollowUp: Boolean,
    val createdAt: Long
)

data class BackupPhoto(
    val id: Long,
    val workDayId: Long,
    val activityId: Long?,
    val eventId: Long?,
    val fileName: String,
    val galleryUri: String?,
    val caption: String?,
    val takenAt: Long,
    val source: String,
    val sizeBytes: Long,
    val createdAt: Long
)

data class BackupVoiceNote(
    val id: Long,
    val workDayId: Long,
    val fileName: String,
    val durationSeconds: Int,
    val note: String?,
    val recordedAt: Long,
    val createdAt: Long
)

data class BackupVehicle(
    val id: Long,
    val name: String,
    val plate: String?,
    val active: Boolean,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class BackupFuelStop(
    val id: Long,
    val vehicleId: Long,
    val date: Long,
    val liters: Double,
    val amountCents: Long,
    val odometerKm: Int?,
    val fullTank: Boolean,
    val station: String?,
    val notes: String?,
    val createdAt: Long
)

data class BackupVehicleExpense(
    val id: Long,
    val vehicleId: Long,
    val workDayId: Long?,
    val date: Long,
    val type: String,
    val amountCents: Long,
    val place: String?,
    val notes: String?,
    val createdAt: Long
)

data class BackupMaintenance(
    val id: Long,
    val vehicleId: Long,
    val date: Long,
    val type: String,
    val description: String?,
    val odometerKm: Int?,
    val amountCents: Long?,
    val workshop: String?,
    val nextDueDate: Long?,
    val nextDueKm: Int?,
    val notes: String?,
    val createdAt: Long
)

/** Le impostazioni: poche righe, ma rifarle a mano e' una scocciatura. */
data class BackupSettings(
    val userName: String,
    val standardWorkMinutes: Int,
    val overtimeEnabled: Boolean,
    val reminderEnabled: Boolean,
    /** Giorno della settimana del promemoria, 1 = lunedi'. */
    val reminderDayOfWeek: Int,
    val reminderHour: Int,
    val reminderMinute: Int
)

/** Quello che c'e' dentro il file, senza i file veri di foto e audio. */
data class BackupContent(
    val workDays: List<BackupWorkDay> = emptyList(),
    val breaks: List<BackupBreak> = emptyList(),
    val sites: List<BackupSite> = emptyList(),
    val activities: List<BackupActivity> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
    val communications: List<BackupCommunication> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
    val voiceNotes: List<BackupVoiceNote> = emptyList(),
    val vehicles: List<BackupVehicle> = emptyList(),
    val fuelStops: List<BackupFuelStop> = emptyList(),
    val vehicleExpenses: List<BackupVehicleExpense> = emptyList(),
    val maintenances: List<BackupMaintenance> = emptyList(),
    val settings: BackupSettings? = null
) {
    val totalRows: Int
        get() = workDays.size + breaks.size + sites.size + activities.size +
            events.size + communications.size + photos.size + voiceNotes.size +
            vehicles.size + fuelStops.size + vehicleExpenses.size + maintenances.size

    val isEmpty: Boolean get() = totalRows == 0
}

/**
 * La carta d'identita' del backup, in un file a parte dentro l'archivio.
 * Si legge da sola e senza caricare tutto il resto: serve per dire
 * all'utente cosa sta per ripristinare prima che lo faccia.
 */
data class BackupManifest(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val createdAt: Long,
    val appVersion: String,
    val databaseVersion: Int,
    val workDays: Int,
    val sites: Int,
    val photos: Int,
    val voiceNotes: Int,
    val vehicles: Int = 0,
    val fuelStops: Int = 0,
    val includesMedia: Boolean,
    val firstDate: Long?,
    val lastDate: Long?
)

/** Esito di una lettura: o si e' capito il file, o si dice perche' no. */
sealed interface BackupReadResult {
    data class Ok(
        val manifest: BackupManifest,
        val content: BackupContent
    ) : BackupReadResult

    /** Backup fatto con una versione piu' nuova dell'app. */
    data class TooNew(val formatVersion: Int) : BackupReadResult

    data class Damaged(val reason: String) : BackupReadResult
}
