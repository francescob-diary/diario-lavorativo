package it.diario.lavorativo.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import it.diario.lavorativo.data.local.entity.DaySiteEntity
import it.diario.lavorativo.data.local.entity.SiteEntity

/** Cantiere in piu' della giornata, insieme ai dati del cantiere. */
data class DaySiteWithSite(
    @Embedded val daySite: DaySiteEntity,
    @Relation(parentColumn = "siteId", entityColumn = "id")
    val site: SiteEntity? = null
)
