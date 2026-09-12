package it.diario.lavorativo.domain.model

import java.time.LocalDate

/**
 * Un consumo misurato fra due pieni.
 *
 * Non e' una media inventata: sono i chilometri fatti davvero fra due
 * pieni, divisi per i litri messi dentro nel frattempo. Se manca un
 * contachilometri il tratto si salta, senza indovinare.
 */
data class FuelConsumption(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val km: Int,
    val liters: Double,
    val costCents: Long
) {
    /** Litri per cento chilometri: il numero che si usa in Europa. */
    val litersPer100Km: Double
        get() = if (km > 0) liters * 100.0 / km else 0.0

    /** Chilometri con un litro, per chi ragiona cosi'. */
    val kmPerLiter: Double
        get() = if (liters > 0.0) km / liters else 0.0

    /** Quanto costa fare un chilometro, in centesimi. */
    val costPerKmCents: Double
        get() = if (km > 0) costCents.toDouble() / km else 0.0
}

/**
 * Il riepilogo del mezzo su un periodo.
 *
 * I chilometri arrivano da due strade diverse: quelli segnati sulle
 * giornate ([travelKm]) e quelli ricavati dal contachilometri
 * ([odometerKm]). Si tengono separati di proposito, perche' quasi mai
 * coincidono e mescolarli darebbe un numero che non e' ne' uno ne' l'altro.
 */
data class VehicleTotals(
    val from: LocalDate,
    val to: LocalDate,
    val travelKm: Int = 0,
    val odometerKm: Int = 0,
    val liters: Double = 0.0,
    val fuelCents: Long = 0L,
    val tollCents: Long = 0L,
    val parkingCents: Long = 0L,
    val otherExpenseCents: Long = 0L,
    val maintenanceCents: Long = 0L,
    val fuelStops: Int = 0,
    val maintenanceCount: Int = 0,
    val consumptions: List<FuelConsumption> = emptyList()
) {
    val expensesCents: Long get() = tollCents + parkingCents + otherExpenseCents

    val totalCents: Long get() = fuelCents + expensesCents + maintenanceCents

    /**
     * Chilometri da usare nei conti: si preferisce il contachilometri,
     * che e' un dato misurato, e si ripiega su quelli delle giornate.
     */
    val effectiveKm: Int get() = if (odometerKm > 0) odometerKm else travelKm

    /** Media dei consumi misurati, pesata sui chilometri. */
    val averageLitersPer100Km: Double
        get() {
            val kmTotali = consumptions.sumOf { it.km }
            if (kmTotali <= 0) return 0.0
            val litriTotali = consumptions.sumOf { it.liters }
            return litriTotali * 100.0 / kmTotali
        }

    val averagePricePerLiter: Double
        get() = if (liters > 0.0) fuelCents / 100.0 / liters else 0.0

    val costPerKmCents: Double
        get() {
            val km = effectiveKm
            return if (km > 0) totalCents.toDouble() / km else 0.0
        }

    val isEmpty: Boolean
        get() = fuelStops == 0 && maintenanceCount == 0 &&
            expensesCents == 0L && travelKm == 0
}

/** Quanto manca a una scadenza. */
enum class DueStatus {
    SCADUTA,
    VICINA,
    OK
}

/**
 * Una scadenza del mezzo, calcolata dall'ultimo intervento fatto.
 *
 * Il promemoria non si inventa niente: se l'officina non ha detto quando
 * tornare, la scadenza non esiste e l'app tace. Meglio nessun avviso che
 * un avviso sbagliato che poi si impara a ignorare.
 */
data class MaintenanceDue(
    val type: MaintenanceType,
    val lastDate: LocalDate?,
    val lastOdometerKm: Int?,
    val dueDate: LocalDate? = null,
    val dueKm: Int? = null,
    /** Giorni che mancano; negativo se e' gia' passata. */
    val daysLeft: Long? = null,
    /** Chilometri che mancano; negativo se sono gia' stati superati. */
    val kmLeft: Int? = null,
    val status: DueStatus = DueStatus.OK
) {
    val isOverdue: Boolean get() = status == DueStatus.SCADUTA

    /** Riga breve da mostrare: "fra 12 giorni", "fra 800 km", "scaduta". */
    val shortLabel: String
        get() {
            if (status == DueStatus.SCADUTA) return "scaduta"
            val perGiorni = daysLeft?.let { "fra " + it + " giorni" }
            val perKm = kmLeft?.let { "fra " + it + " km" }
            return when {
                perGiorni != null && perKm != null -> perGiorni + " o " + perKm
                perGiorni != null -> perGiorni
                perKm != null -> perKm
                else -> ""
            }
        }
}
