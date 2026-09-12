package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Tabella dei cantieri. Le date sono salvate come epochDay (giorni dal 1970-01-01). */
@Entity(
    tableName = "sites",
    indices = [Index(value = ["name"]), Index(value = ["status"])]
)
data class SiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val address: String? = null,
    val city: String? = null,
    val client: String? = null,
    val company: String? = null,
    val contact: String? = null,
    val phone: String? = null,
    val startDate: Long? = null,
    val expectedEndDate: Long? = null,
    val notes: String? = null,
    val status: String = "ATTIVO",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = 150,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
