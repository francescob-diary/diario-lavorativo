package it.diario.lavorativo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una cosa prestata: cosa, a chi, da quando, e quando e' tornata. */
@Entity(tableName = "loans", indices = [Index(value = ["returnedDate"])])
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val what: String,
    val toWhom: String,
    /** Giorno del prestito (epochDay). */
    val loanDate: Long,
    /** Giorno della restituzione (epochDay); null = ancora fuori. */
    val returnedDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long = 0L
)
