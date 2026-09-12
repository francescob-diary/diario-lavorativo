package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabella delle giornate.
 *
 * Formato dei campi temporali:
 *  - date            -> epochDay (Long), un record per giorno di calendario;
 *  - startTime/endTime -> epoch millis UTC (Long), convertiti in ora locale solo in UI.
 *
 * L'indice unico su date impedisce di creare due giornate per la stessa data.
 * Se il cantiere viene cancellato la giornata resta, con siteId a null.
 */
@Entity(
    tableName = "work_days",
    foreignKeys = [
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["date"], unique = true),
        Index(value = ["siteId"]),
        Index(value = ["endTime"])
    ]
)
data class WorkDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val date: Long,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val siteId: Long? = null,
    val dayType: String = "LAVORO",
    val place: String? = null,
    val role: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val standardMinutesOverride: Int? = null,
    /** Chilometri percorsi con il mezzo aziendale in questa giornata. */
    val travelKm: Int? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
