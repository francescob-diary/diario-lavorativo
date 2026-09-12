package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Lavorazioni di una giornata. Cancellata la giornata, spariscono anche queste. */
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workDayId"]), Index(value = ["category"])]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDayId: Long,
    val category: String = "ALTRO",
    val description: String,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val quantity: String? = null,
    val notes: String? = null,
    val createdAt: Long = 0L
)
