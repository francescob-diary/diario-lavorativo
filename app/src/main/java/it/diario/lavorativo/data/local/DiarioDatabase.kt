package it.diario.lavorativo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import it.diario.lavorativo.data.local.dao.ActivityDao
import it.diario.lavorativo.data.local.dao.BreakDao
import it.diario.lavorativo.data.local.dao.CommunicationDao
import it.diario.lavorativo.data.local.dao.EventDao
import it.diario.lavorativo.data.local.dao.PhotoDao
import it.diario.lavorativo.data.local.dao.VoiceNoteDao
import it.diario.lavorativo.data.local.dao.VehicleDao
import it.diario.lavorativo.data.local.dao.FuelStopDao
import it.diario.lavorativo.data.local.dao.VehicleExpenseDao
import it.diario.lavorativo.data.local.dao.MaintenanceDao
import it.diario.lavorativo.data.local.dao.SiteDao
import it.diario.lavorativo.data.local.dao.WorkDayDao
import it.diario.lavorativo.data.local.entity.ActivityEntity
import it.diario.lavorativo.data.local.entity.BreakEntity
import it.diario.lavorativo.data.local.entity.CommunicationEntity
import it.diario.lavorativo.data.local.entity.EventEntity
import it.diario.lavorativo.data.local.entity.PhotoEntity
import it.diario.lavorativo.data.local.entity.VoiceNoteEntity
import it.diario.lavorativo.data.local.entity.VehicleEntity
import it.diario.lavorativo.data.local.entity.FuelStopEntity
import it.diario.lavorativo.data.local.entity.VehicleExpenseEntity
import it.diario.lavorativo.data.local.entity.MaintenanceEntity
import it.diario.lavorativo.data.local.entity.SiteEntity
import it.diario.lavorativo.data.local.entity.WorkDayEntity
import it.diario.lavorativo.data.local.migration.MIGRATION_1_2
import it.diario.lavorativo.data.local.migration.MIGRATION_2_3
import it.diario.lavorativo.data.local.migration.MIGRATION_3_4
import it.diario.lavorativo.data.local.migration.MIGRATION_4_5
import it.diario.lavorativo.data.local.migration.MIGRATION_5_6

/**
 * Database locale dell'app.
 *
 * Le entita' delle fasi successive (Event, Photo, Activity, Trip, Problem) verranno
 * aggiunte qui con un incremento di version e una Migration dedicata: lo schema
 * viene esportato in app/schemas per poterle scrivere in modo sicuro.
 *
 * version 2: colonne phone, latitude, longitude, radiusMeters sui cantieri.
 * version 3: tabelle activities, events, communications.
 * version 4: tabella photos.
 * version 5: tabella voice_notes.
 * version 6: mezzo aziendale (vehicles, fuel_stops, vehicle_expenses,
 *            maintenances) e colonna travelKm sulle giornate.
 */
@Database(
    entities = [
        WorkDayEntity::class,
        BreakEntity::class,
        SiteEntity::class,
        ActivityEntity::class,
        EventEntity::class,
        CommunicationEntity::class,
        PhotoEntity::class,
        VoiceNoteEntity::class,
        VehicleEntity::class,
        FuelStopEntity::class,
        VehicleExpenseEntity::class,
        MaintenanceEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class DiarioDatabase : RoomDatabase() {

    abstract fun workDayDao(): WorkDayDao
    abstract fun breakDao(): BreakDao
    abstract fun siteDao(): SiteDao
    abstract fun activityDao(): ActivityDao
    abstract fun eventDao(): EventDao
    abstract fun communicationDao(): CommunicationDao
    abstract fun photoDao(): PhotoDao
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelStopDao(): FuelStopDao
    abstract fun vehicleExpenseDao(): VehicleExpenseDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        private const val DB_NAME = "diario_lavorativo.db"

        @Volatile
        private var instance: DiarioDatabase? = null

        fun getInstance(context: Context): DiarioDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): DiarioDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DiarioDatabase::class.java,
                DB_NAME
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                // WAL: scritture piu' veloci e letture non bloccate (utile in cantiere).
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
