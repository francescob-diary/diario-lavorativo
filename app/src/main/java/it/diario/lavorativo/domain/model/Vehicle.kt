package it.diario.lavorativo.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Il mezzo aziendale.
 *
 * Serve una tabella a parte perche' l'auto ogni tanto la cambiano: se i
 * rifornimenti fossero attaccati alla giornata e basta, cambiando furgone
 * i consumi si mescolerebbero e il calcolo verrebbe fuori senza senso.
 *
 * [active] segna il mezzo in uso adesso. I vecchi restano, con i loro
 * dati: servono se in sede chiedono i conti dell'anno scorso.
 */
data class Vehicle(
    val id: Long = 0L,
    val name: String,
    val plate: String? = null,
    val active: Boolean = true,
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    /** Etichetta breve: "Doblo' (AB123CD)" oppure solo il nome. */
    val label: String
        get() = if (plate.isNullOrBlank()) name else name + " (" + plate + ")"
}

/**
 * Un rifornimento.
 *
 * Gli importi si tengono in centesimi come numeri interi, non con la
 * virgola: 0.1 + 0.2 in virgola mobile non fa 0.3, e su cento rifornimenti
 * il totale a fine anno non tornerebbe con lo scontrino.
 *
 * [odometerKm] e' facoltativo perche' non sempre si guarda il contachilometri
 * alla pompa, ma senza quello il consumo non si puo' calcolare: e' l'unico
 * numero che lega i litri alla strada fatta davvero.
 *
 * [fullTank] distingue il pieno dal rabbocco. Il consumo si misura solo da
 * pieno a pieno: se il serbatoio resta a meta' non si sa quanto c'era prima.
 */
data class FuelStop(
    val id: Long = 0L,
    val vehicleId: Long,
    val date: LocalDate,
    val liters: Double,
    val amountCents: Long,
    val odometerKm: Int? = null,
    val fullTank: Boolean = true,
    val station: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.now()
) {
    /** Prezzo al litro ricavato dallo scontrino, non digitato a mano. */
    val pricePerLiter: Double?
        get() = if (liters > 0.0) amountCents / 100.0 / liters else null
}

/** Le spese dell'auto che non sono carburante. */
enum class ExpenseType(val label: String) {
    PEDAGGIO("Pedaggio"),
    PARCHEGGIO("Parcheggio"),
    LAVAGGIO("Lavaggio"),
    MULTA("Multa"),
    ALTRO("Altro");

    companion object {
        fun fromName(value: String?): ExpenseType =
            entries.firstOrNull { it.name == value } ?: ALTRO
    }
}

/**
 * Pedaggio, parcheggio o simili.
 *
 * [workDayId] e' facoltativo: se la spesa cade in una giornata registrata
 * si aggancia li' e finisce nel conto della trasferta, senno' resta da
 * sola. Meglio una spesa orfana che una spesa persa.
 */
data class VehicleExpense(
    val id: Long = 0L,
    val vehicleId: Long,
    val workDayId: Long? = null,
    val date: LocalDate,
    val type: ExpenseType = ExpenseType.PEDAGGIO,
    val amountCents: Long,
    val place: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.now()
)

/** Tipo di intervento sul mezzo. */
enum class MaintenanceType(val label: String, val scadeAKm: Boolean, val scadeADate: Boolean) {
    TAGLIANDO("Tagliando", true, true),
    CAMBIO_OLIO("Cambio olio", true, false),
    FILTRI("Filtri", true, false),
    GOMME("Gomme", true, false),
    REVISIONE("Revisione", false, true),
    FRENI("Freni", true, false),
    BATTERIA("Batteria", false, true),
    BOLLO("Bollo", false, true),
    ASSICURAZIONE("Assicurazione", false, true),
    RIPARAZIONE("Riparazione", false, false),
    ALTRO("Altro", false, false);

    companion object {
        fun fromName(value: String?): MaintenanceType =
            entries.firstOrNull { it.name == value } ?: ALTRO
    }
}

/**
 * Un intervento fatto sul mezzo.
 *
 * Si segnano insieme la data e i chilometri, perche' le due scadenze
 * viaggiano diverse: la revisione scade a tempo, il tagliando a
 * chilometri. Tenerne uno solo vorrebbe dire perdere l'altro.
 */
data class Maintenance(
    val id: Long = 0L,
    val vehicleId: Long,
    val date: LocalDate,
    val type: MaintenanceType = MaintenanceType.TAGLIANDO,
    val description: String? = null,
    val odometerKm: Int? = null,
    val amountCents: Long? = null,
    val workshop: String? = null,
    /** Prossima scadenza a calendario, se l'officina l'ha detta. */
    val nextDueDate: LocalDate? = null,
    /** Prossima scadenza a contachilometri. */
    val nextDueKm: Int? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.now()
) {
    val hasDeadline: Boolean get() = nextDueDate != null || nextDueKm != null
}
