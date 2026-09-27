package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Spostamento della giornata: da dove a dove, a che ora.
 *
 * Resta nel diario, nello storico e nelle statistiche, ma non finisce mai
 * nel rapportino che va in sede.
 */
@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workDayId"])]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDayId: Long,
    val departTime: Long? = null,
    val arriveTime: Long? = null,
    val fromPlace: String? = null,
    val toPlace: String? = null,
    val notes: String? = null,
    val createdAt: Long = 0L
)
