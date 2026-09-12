package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Foto di una giornata.
 *
 * fileName e' la copia ridotta nello spazio privato dell'app; galleryUri
 * punta all'originale in galleria, che resta al suo posto.
 *
 * I collegamenti a lavorazione ed evento usano SET_NULL e non CASCADE:
 * cancellando una lavorazione la foto resta, semplicemente slegata. Una foto
 * di cantiere e' una prova, non deve sparire per un ripensamento su un'altra
 * voce.
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["workDayId"]),
        Index(value = ["activityId"]),
        Index(value = ["eventId"]),
        Index(value = ["fileName"], unique = true)
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDayId: Long,
    val activityId: Long? = null,
    val eventId: Long? = null,
    val fileName: String,
    val galleryUri: String? = null,
    val caption: String? = null,
    val takenAt: Long,
    val source: String = "FOTOCAMERA",
    val sizeBytes: Long = 0L,
    val createdAt: Long = 0L
)
