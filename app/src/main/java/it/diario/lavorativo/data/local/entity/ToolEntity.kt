package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un attrezzo del deposito virtuale e il posto dove si trova adesso:
 * il deposito, il furgone, un cantiere (anche la stanza precisa) o altro.
 */
@Entity(
    tableName = "tools",
    foreignKeys = [
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["siteId"]), Index(value = ["name"])]
)
data class ToolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** DEPOSITO, FURGONE, CANTIERE, ALTRO. */
    val placeType: String = "DEPOSITO",
    val siteId: Long? = null,
    /** Stanza o zona dentro il posto, es. "bagno al primo piano". */
    val placeDetail: String? = null,
    /** Nome del posto quando e' ALTRO. */
    val placeName: String? = null,
    val notes: String? = null,
    val movedAt: Long = 0L,
    val createdAt: Long = 0L
)
