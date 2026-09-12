package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Eventi e problemi di una giornata. */
@Entity(
    tableName = "events",
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
        Index(value = ["type"]),
        Index(value = ["unresolved"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDayId: Long,
    val type: String,
    val time: Long,
    val title: String,
    val description: String? = null,
    val durationMinutes: Int? = null,
    val severity: String = "NORMALE",
    val unresolved: Boolean = false,
    val createdAt: Long = 0L
)
