package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Il mezzo aziendale.
 *
 * Tabella a parte e non un campo sulla giornata: l'auto la cambiano ogni
 * tanto, e i consumi di un furgone non c'entrano niente con quelli del
 * successivo. Tenendoli separati i conti restano leggibili anche fra due
 * anni.
 */
@Entity(
    tableName = "vehicles",
    indices = [Index(value = ["active"])]
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val plate: String? = null,
    val active: Boolean = true,
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
