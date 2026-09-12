package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pedaggi, parcheggi e le altre spese del mezzo.
 *
 * Il collegamento alla giornata e' SET_NULL: se la giornata viene
 * cancellata la spesa resta, slegata. Un pedaggio pagato e' pagato lo
 * stesso, e a fine mese deve comparire nel totale anche se la giornata
 * a cui apparteneva non c'e' piu'.
 */
@Entity(
    tableName = "vehicle_expenses",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["workDayId"]),
        Index(value = ["date"])
    ]
)
data class VehicleExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vehicleId: Long,
    val workDayId: Long? = null,
    val date: Long,
    val type: String = "PEDAGGIO",
    val amountCents: Long,
    val place: String? = null,
    val notes: String? = null,
    val createdAt: Long = 0L
)
