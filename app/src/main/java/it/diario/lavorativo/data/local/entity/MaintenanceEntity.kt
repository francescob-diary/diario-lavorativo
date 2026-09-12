package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un intervento sul mezzo: tagliando, gomme, revisione, riparazione.
 *
 * Si tengono insieme data e chilometri perche' le scadenze successive
 * viaggiano su due binari diversi: la revisione a tempo, il tagliando a
 * chilometri. Registrarne uno solo vorrebbe dire perdere l'altro.
 *
 * nextDueDate e nextDueKm sono quello che ha detto l'officina, non una
 * regola dell'app: se non l'ha detto restano vuoti e nessuno inventa.
 */
@Entity(
    tableName = "maintenances",
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
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class MaintenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vehicleId: Long,
    val date: Long,
    val type: String = "TAGLIANDO",
    val description: String? = null,
    val odometerKm: Int? = null,
    val amountCents: Long? = null,
    val workshop: String? = null,
    val nextDueDate: Long? = null,
    val nextDueKm: Int? = null,
    val notes: String? = null,
    val createdAt: Long = 0L
)
