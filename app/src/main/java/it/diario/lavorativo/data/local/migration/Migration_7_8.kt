package it.diario.lavorativo.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versione 8: piu' cantieri nella stessa giornata, spostamenti, foto
 * legate al cantiere.
 *
 * Due tabelle nuove e una colonna in piu' sulle foto. Niente viene
 * riscritto: le giornate esistenti restano con il loro cantiere unico e
 * le foto esistenti con il cantiere vuoto.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS day_sites (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                workDayId INTEGER NOT NULL,
                siteId INTEGER NOT NULL,
                minutes INTEGER NOT NULL,
                description TEXT,
                sortOrder INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(siteId) REFERENCES sites(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_day_sites_workDayId ON day_sites(workDayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_day_sites_siteId ON day_sites(siteId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS trips (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                workDayId INTEGER NOT NULL,
                departTime INTEGER,
                arriveTime INTEGER,
                fromPlace TEXT,
                toPlace TEXT,
                notes TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_trips_workDayId ON trips(workDayId)")

        db.execSQL("ALTER TABLE photos ADD COLUMN siteId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_siteId ON photos(siteId)")
    }
}
