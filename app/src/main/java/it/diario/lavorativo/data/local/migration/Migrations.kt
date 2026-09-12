package it.diario.lavorativo.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Da versione 1 a 2: aggiunge al cantiere telefono, coordinate e raggio.
 *
 * Migrazione e non fallback distruttivo: chi ha gia' registrato giornate non
 * deve perderle solo perche' aggiungiamo delle colonne.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sites ADD COLUMN phone TEXT")
        db.execSQL("ALTER TABLE sites ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE sites ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE sites ADD COLUMN radiusMeters INTEGER NOT NULL DEFAULT 150")
    }
}

/**
 * Da versione 2 a 3: attivita', eventi e comunicazioni.
 *
 * Le tre tabelle sono nuove, quindi la migrazione e' puramente additiva:
 * nessun dato esistente viene toccato. Gli indici replicano esattamente
 * quelli dichiarati nelle @Entity, altrimenti Room segnala lo schema
 * divergente al primo avvio dopo l'aggiornamento.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS activities (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                workDayId INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT 'ALTRO',
                description TEXT NOT NULL,
                startTime INTEGER,
                endTime INTEGER,
                quantity TEXT,
                notes TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_workDayId ON activities(workDayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_category ON activities(category)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                workDayId INTEGER NOT NULL,
                type TEXT NOT NULL,
                time INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                durationMinutes INTEGER,
                severity TEXT NOT NULL DEFAULT 'NORMALE',
                unresolved INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_workDayId ON events(workDayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_type ON events(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_unresolved ON events(unresolved)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS communications (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                workDayId INTEGER NOT NULL,
                channel TEXT NOT NULL,
                direction TEXT NOT NULL,
                time INTEGER NOT NULL,
                contactName TEXT NOT NULL,
                contactRef TEXT,
                subject TEXT,
                content TEXT,
                durationMinutes INTEGER,
                requiresFollowUp INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_communications_workDayId ON communications(workDayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_communications_channel ON communications(channel)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_communications_contactName ON communications(contactName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_communications_requiresFollowUp ON communications(requiresFollowUp)")
    }
}

/**
 * Da versione 3 a 4: tabella delle foto.
 *
 * I collegamenti a lavorazione ed evento sono SET_NULL: cancellando una di
 * quelle voci la foto resta, slegata. Gli indici replicano quelli dichiarati
 * nella @Entity, compreso l'unico su fileName, che impedisce di registrare
 * due volte lo stesso file.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS photos (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                workDayId INTEGER NOT NULL,
                activityId INTEGER,
                eventId INTEGER,
                fileName TEXT NOT NULL,
                galleryUri TEXT,
                caption TEXT,
                takenAt INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT 'FOTOCAMERA',
                sizeBytes INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(workDayId) REFERENCES work_days(id) ON DELETE CASCADE,
                FOREIGN KEY(activityId) REFERENCES activities(id) ON DELETE SET NULL,
                FOREIGN KEY(eventId) REFERENCES events(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_workDayId ON photos(workDayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_activityId ON photos(activityId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_eventId ON photos(eventId)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_photos_fileName ON photos(fileName)"
        )
    }
}
