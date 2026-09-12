package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Telefonate, WhatsApp, mail e messaggi legati alla giornata.
 *
 * Il contenuto puo' essere lungo: SQLite tiene il testo senza limite pratico,
 * quindi si salva la conversazione per intero invece di troncarla.
 */
@Entity(
    tableName = "communications",
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
        Index(value = ["channel"]),
        Index(value = ["contactName"]),
        Index(value = ["requiresFollowUp"])
    ]
)
data class CommunicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDayId: Long,
    val channel: String,
    val direction: String,
    val time: Long,
    val contactName: String,
    val contactRef: String? = null,
    val subject: String? = null,
    val content: String? = null,
    val durationMinutes: Int? = null,
    val requiresFollowUp: Boolean = false,
    val createdAt: Long = 0L
)
