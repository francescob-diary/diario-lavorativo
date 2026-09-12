package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un rifornimento.
 *
 * L'importo sta in centesimi come numero intero: i soldi con la virgola
 * mobile a fine anno non tornano con gli scontrini, e in sede il totale
 * deve tornare.
 *
 * date e' un epochDay come nelle giornate, cosi' i confronti fra tabelle
 * sono confronti fra numeri e non fra formati diversi.
 *
 * Se il mezzo viene cancellato spariscono anche i suoi rifornimenti: da
 * soli non vogliono dire niente, non si saprebbe di quale auto sono.
 */
@Entity(
    tableName = "fuel_stops",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["date"])
    ]
)
data class FuelStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vehicleId: Long,
    val date: Long,
    val liters: Double,
    val amountCents: Long,
    val odometerKm: Int? = null,
    val fullTank: Boolean = true,
    val station: String? = null,
    val notes: String? = null,
    val createdAt: Long = 0L
)
