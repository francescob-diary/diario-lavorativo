package it.diario.lavorativo.core.di

import android.content.Context
import it.diario.lavorativo.core.location.AndroidLocationProvider
import it.diario.lavorativo.core.location.LocationProvider
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.core.time.SystemAppClock
import it.diario.lavorativo.data.local.DiarioDatabase
import it.diario.lavorativo.data.prefs.SettingsRepositoryImpl
import it.diario.lavorativo.data.repository.DiaryEntryRepositoryImpl
import it.diario.lavorativo.data.repository.SiteRepositoryImpl
import it.diario.lavorativo.data.repository.WorkDayRepositoryImpl
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import it.diario.lavorativo.domain.repository.SiteRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.PeriodSummarizer
import it.diario.lavorativo.core.photo.GalleryWriter
import it.diario.lavorativo.core.audio.AudioPlayer
import it.diario.lavorativo.core.audio.AudioRecorder
import it.diario.lavorativo.core.audio.VoiceStorage
import it.diario.lavorativo.data.backup.BackupRepository
import it.diario.lavorativo.data.repository.VoiceNoteRepositoryImpl
import it.diario.lavorativo.domain.repository.VoiceNoteRepository
import it.diario.lavorativo.core.export.FileExporter
import it.diario.lavorativo.core.photo.PhotoStorage
import it.diario.lavorativo.core.reminder.ReminderScheduler
import it.diario.lavorativo.domain.service.ReminderScheduleCalculator
import it.diario.lavorativo.domain.service.SharedTextParser
import it.diario.lavorativo.domain.service.StatisticsCalculator
import it.diario.lavorativo.domain.service.ExportBuilder
import it.diario.lavorativo.domain.service.WeeklyReportBuilder
import it.diario.lavorativo.domain.service.SiteLocationMatcher
import it.diario.lavorativo.domain.service.WorkTimeCalculator
import it.diario.lavorativo.domain.service.MaintenanceScheduler
import it.diario.lavorativo.domain.service.VehicleCalculator
import it.diario.lavorativo.data.repository.VehicleRepositoryImpl
import it.diario.lavorativo.domain.repository.VehicleRepository

/**
 * Contenitore delle dipendenze, creato una sola volta nell'Application.
 *
 * Scelta: iniezione manuale invece di Hilt. Il progetto ha un solo modulo e
 * poche dipendenze; Hilt aggiungerebbe un altro processore di annotazioni
 * (oltre a Room) senza vantaggi concreti adesso. Se in futuro servira', il
 * passaggio a Hilt tocchera' solo questa classe e i factory dei ViewModel.
 */
class AppContainer(val context: Context) {

    private val database: DiarioDatabase = DiarioDatabase.getInstance(context)

    val clock: AppClock = SystemAppClock()

    /**
     * Sta in cima di proposito: il backup lo usa, e in Kotlin le proprieta'
     * si inizializzano nell'ordine in cui sono scritte. Dichiararlo in fondo
     * vorrebbe dire passarlo ancora vuoto a chi lo prende nel costruttore.
     */
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
        context.applicationContext
    )

    val workTimeCalculator: WorkTimeCalculator = WorkTimeCalculator()

    val siteLocationMatcher: SiteLocationMatcher = SiteLocationMatcher()

    val sharedTextParser: SharedTextParser = SharedTextParser()

    val photoStorage: PhotoStorage = PhotoStorage(context)

    val galleryWriter: GalleryWriter = GalleryWriter(context)

    val periodSummarizer: PeriodSummarizer = PeriodSummarizer(workTimeCalculator)

    val statisticsCalculator: StatisticsCalculator =
        StatisticsCalculator(workTimeCalculator, periodSummarizer)

    // ---- fase 12: backup e note vocali ----

    val voiceStorage: VoiceStorage = VoiceStorage(context)

    val audioRecorder: AudioRecorder = AudioRecorder(context)

    /**
     * Uno solo per tutta l'app: due riproduttori vivi vorrebbe dire due
     * note vocali che suonano insieme.
     */
    val audioPlayer: AudioPlayer = AudioPlayer()

    val voiceNoteRepository: VoiceNoteRepository =
        VoiceNoteRepositoryImpl(database.voiceNoteDao(), voiceStorage)

    val backupRepository: BackupRepository =
        BackupRepository(context, database, settingsRepository)

    val vehicleCalculator: VehicleCalculator = VehicleCalculator()

    val maintenanceScheduler: MaintenanceScheduler = MaintenanceScheduler()

    val exportBuilder: ExportBuilder =
        ExportBuilder(workTimeCalculator, periodSummarizer, vehicleCalculator)

    val fileExporter: FileExporter = FileExporter(context)

    val weeklyReportBuilder: WeeklyReportBuilder =
        WeeklyReportBuilder(workTimeCalculator, periodSummarizer)

    val reminderCalculator: ReminderScheduleCalculator = ReminderScheduleCalculator()

    val reminderScheduler: ReminderScheduler = ReminderScheduler(context, reminderCalculator)

    val locationProvider: LocationProvider = AndroidLocationProvider(context)

    val workDayRepository: WorkDayRepository = WorkDayRepositoryImpl(
        workDayDao = database.workDayDao(),
        breakDao = database.breakDao(),
        clock = clock
    )

    val siteRepository: SiteRepository = SiteRepositoryImpl(
        siteDao = database.siteDao(),
        clock = clock
    )

    // ---- fase 13: mezzo aziendale ----

    val vehicleRepository: VehicleRepository = VehicleRepositoryImpl(
        vehicleDao = database.vehicleDao(),
        fuelStopDao = database.fuelStopDao(),
        expenseDao = database.vehicleExpenseDao(),
        maintenanceDao = database.maintenanceDao(),
        clock = clock
    )

    val diaryEntryRepository: DiaryEntryRepository = DiaryEntryRepositoryImpl(
        activityDao = database.activityDao(),
        eventDao = database.eventDao(),
        communicationDao = database.communicationDao(),
        photoDao = database.photoDao(),
        clock = clock
    )

}
