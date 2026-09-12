package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Nota vocale di una giornata.
 *
 * In cantiere le mani sono sporche, i guanti non vanno sul vetro e spesso
 * c'e' rumore: dettare venti secondi e' l'unico modo realistico di
 * annotare qualcosa mentre si lavora. Si trascrive con calma la sera.
 *
 * L'audio sta nello spazio privato dell'app, non in galleria: al contrario
 * delle foto non serve a nessun'altra app e non deve finire nei backup
 * automatici del telefono.
 */
@Entity(
    tableName = "voice_notes",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workDayId"]),
        Index(value = ["fileName"], unique = true)
    ]
)
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val workDayId: Long,
    /** Nome del file audio dentro la cartella "note" dell'app. */
    val fileName: String,
    val durationSeconds: Int,
    /** Trascrizione scritta a mano dopo, quando c'e' tempo. */
    val note: String? = null,
    val recordedAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)
