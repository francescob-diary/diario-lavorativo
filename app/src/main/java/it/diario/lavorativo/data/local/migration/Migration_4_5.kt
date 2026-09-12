package it.diario.lavorativo.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versione 5: arrivano le note vocali.
 *
 * Solo una tabella nuova, nessuna colonna toccata: i dati gia' registrati
 * non vengono sfiorati. E' la migrazione piu' sicura possibile, ma va
 * scritta lo stesso, senno' Room cancella e ricrea tutto il database.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS voice_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workDayId INTEGER NOT NULL,
                fileName TEXT NOT NULL,
                durationSeconds INTEGER NOT NULL,
                note TEXT,
                recordedAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_voice_notes_workDayId ON voice_notes(workDayId)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_voice_notes_fileName " +
                "ON voice_notes(fileName)"
        )
    }
}
