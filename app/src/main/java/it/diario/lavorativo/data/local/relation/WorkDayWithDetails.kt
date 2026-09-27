package it.diario.lavorativo.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import it.diario.lavorativo.data.local.entity.BreakEntity
import it.diario.lavorativo.data.local.entity.DaySiteEntity
import it.diario.lavorativo.data.local.entity.SiteEntity
import it.diario.lavorativo.data.local.entity.TripEntity
import it.diario.lavorativo.data.local.entity.WorkDayEntity

/**
 * Giornata con pause, cantiere principale, cantieri in piu' e
 * spostamenti, letta in una sola query.
 */
data class WorkDayWithDetails(
    @Embedded val workDay: WorkDayEntity,
    @Relation(parentColumn = "id", entityColumn = "workDayId")
    val breaks: List<BreakEntity> = emptyList(),
    @Relation(parentColumn = "siteId", entityColumn = "id")
    val site: SiteEntity? = null,
    @Relation(entity = DaySiteEntity::class, parentColumn = "id", entityColumn = "workDayId")
    val extraSites: List<DaySiteWithSite> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "workDayId")
    val trips: List<TripEntity> = emptyList()
)
