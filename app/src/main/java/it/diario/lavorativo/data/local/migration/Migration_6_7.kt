package it.diario.lavorativo.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versione 7: il cantiere ha il suo "Lavoro in corso".
 *
 * Una colonna sola, aggiunta con ALTER TABLE: i cantieri esistenti la
 * trovano vuota e il rapportino, finche' non la si compila, continua a
 * usare le lavorazioni del giorno come prima.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sites ADD COLUMN workInProgress TEXT")
    }
}
