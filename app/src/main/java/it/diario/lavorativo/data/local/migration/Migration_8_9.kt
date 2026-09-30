package it.diario.lavorativo.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Versione 9: deposito virtuale degli attrezzi e cose prestate. Solo tabelle nuove. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tools (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                placeType TEXT NOT NULL,
                siteId INTEGER,
                placeDetail TEXT,
                placeName TEXT,
                notes TEXT,
                movedAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(siteId) REFERENCES sites(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tools_siteId ON tools(siteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tools_name ON tools(name)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS loans (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                what TEXT NOT NULL,
                toWhom TEXT NOT NULL,
                loanDate INTEGER NOT NULL,
                returnedDate INTEGER,
                notes TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_returnedDate ON loans(returnedDate)")
    }
}
