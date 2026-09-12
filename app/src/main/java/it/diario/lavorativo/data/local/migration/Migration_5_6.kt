package it.diario.lavorativo.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versione 6: arriva il mezzo aziendale.
 *
 * Quattro tabelle nuove piu' una colonna sola aggiunta alle giornate, i
 * chilometri percorsi. Le tabelle nuove non toccano niente di quello che
 * c'e' gia'; la colonna si aggiunge con un ALTER TABLE, che in SQLite e'
 * l'operazione piu' innocua che ci sia: le righe esistenti si trovano il
 * campo a null, cioe' "non lo so", che e' esattamente la verita' per le
 * giornate registrate prima di oggi.
 *
 * Nessun dato viene riscritto e nessuna tabella viene ricreata: e' la
 * differenza fra una migrazione che si puo' lanciare a occhi chiusi e una
 * da guardare col fiato sospeso.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vehicles (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                plate TEXT,
                active INTEGER NOT NULL DEFAULT 1,
                notes TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_active ON vehicles(active)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fuel_stops (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                vehicleId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                liters REAL NOT NULL,
                amountCents INTEGER NOT NULL,
                odometerKm INTEGER,
                fullTank INTEGER NOT NULL DEFAULT 1,
                station TEXT,
                notes TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fuel_stops_vehicleId ON fuel_stops(vehicleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fuel_stops_date ON fuel_stops(date)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vehicle_expenses (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                vehicleId INTEGER NOT NULL,
                workDayId INTEGER,
                date INTEGER NOT NULL,
                type TEXT NOT NULL DEFAULT 'PEDAGGIO',
                amountCents INTEGER NOT NULL,
                place TEXT,
                notes TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON DELETE CASCADE,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vehicle_expenses_vehicleId " +
                "ON vehicle_expenses(vehicleId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vehicle_expenses_workDayId " +
                "ON vehicle_expenses(workDayId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vehicle_expenses_date ON vehicle_expenses(date)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS maintenances (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                vehicleId INTEGER NOT NULL,
                date INTEGER NOT NULL,
                type TEXT NOT NULL DEFAULT 'TAGLIANDO',
                description TEXT,
                odometerKm INTEGER,
                amountCents INTEGER,
                workshop TEXT,
                nextDueDate INTEGER,
                nextDueKm INTEGER,
                notes TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_maintenances_vehicleId ON maintenances(vehicleId)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenances_date ON maintenances(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenances_type ON maintenances(type)")

        // I chilometri percorsi nella giornata. Restano sulla giornata e non
        // su una tabella a parte perche' sono un dato della giornata: si
        // segnano insieme agli orari e servono nel rapportino delle trasferte.
        db.execSQL("ALTER TABLE work_days ADD COLUMN travelKm INTEGER")
    }
}
