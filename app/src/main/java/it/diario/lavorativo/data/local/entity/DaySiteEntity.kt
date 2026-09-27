package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cantiere in piu' di una giornata.
 *
 * Il cantiere principale resta in work_days.siteId, come prima: qui ci
 * vanno solo gli altri, ognuno con le ore fatte li'. Le ore del principale
 * sono il resto della giornata. Cosi' tutto quello che gia' leggeva
 * siteId continua a funzionare senza toccarlo.
 */
@Entity(
    tableName = "day_sites",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workDayId"]), Index(value = ["siteId"])]
)
data class DaySiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDayId: Long,
    val siteId: Long,
    /** Minuti lavorati su questo cantiere. */
    val minutes: Int = 0,
    /** Cosa si e' fatto li', se diverso dal lavoro in corso del cantiere. */
    val description: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L
)
