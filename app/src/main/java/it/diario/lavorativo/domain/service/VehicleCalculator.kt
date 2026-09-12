package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.ExpenseType
import it.diario.lavorativo.domain.model.FuelConsumption
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.VehicleExpense
import it.diario.lavorativo.domain.model.VehicleTotals
import java.time.LocalDate

/**
 * I conti del mezzo aziendale.
 *
 * Tutto quello che c'e' qui dentro e' Kotlin puro: nessun riferimento ad
 * Android, nessun database. Cosi' si puo' provare davvero, e i numeri che
 * finiscono nel documento che va in sede sono numeri controllati.
 *
 * Regola di fondo: quando manca un dato non si indovina. Un consumo
 * inventato e' peggio di un consumo mancante, perche' sembra vero.
 */
class VehicleCalculator {

    /**
     * I consumi misurati fra un pieno e l'altro.
     *
     * Come si conta, per chi vuole controllare: si parte da un pieno con
     * il contachilometri segnato. Da li' in avanti si sommano tutti i
     * litri messi dentro, compresi i rabbocchi. Quando arriva il pieno
     * successivo con il contachilometri segnato, i chilometri fatti sono
     * la differenza fra i due contatori, e i litri sono quelli sommati.
     *
     * Il primo pieno non produce nessun consumo: si sa quanto e' stato
     * messo, ma non da che punto si e' partiti.
     */
    fun consumptions(stops: List<FuelStop>): List<FuelConsumption> {
        val ordinati = stops
            .filter { it.liters > 0.0 }
            .sortedWith(compareBy({ it.date }, { it.odometerKm ?: Int.MAX_VALUE }, { it.id }))

        val risultato = mutableListOf<FuelConsumption>()

        var partenzaKm: Int? = null
        var partenzaData: LocalDate? = null
        var litriAccumulati = 0.0
        var centesimiAccumulati = 0L

        for (stop in ordinati) {
            val km = stop.odometerKm

            if (partenzaKm == null) {
                // Non si e' ancora partiti: serve un pieno con il contatore.
                if (stop.fullTank && km != null) {
                    partenzaKm = km
                    partenzaData = stop.date
                    litriAccumulati = 0.0
                    centesimiAccumulati = 0L
                }
                continue
            }

            litriAccumulati += stop.liters
            centesimiAccumulati += stop.amountCents

            if (!stop.fullTank || km == null) {
                // Rabbocco, oppure pieno senza contatore: i litri restano
                // in conto e si aspetta il prossimo pieno buono.
                continue
            }

            val percorsi = km - partenzaKm
            if (percorsi > 0 && litriAccumulati > 0.0) {
                risultato += FuelConsumption(
                    fromDate = partenzaData ?: stop.date,
                    toDate = stop.date,
                    km = percorsi,
                    liters = litriAccumulati,
                    costCents = centesimiAccumulati
                )
            }

            // Il pieno appena trovato diventa la nuova partenza.
            partenzaKm = km
            partenzaData = stop.date
            litriAccumulati = 0.0
            centesimiAccumulati = 0L
        }

        return risultato
    }

    /**
     * Chilometri ricavati dal contachilometri nel periodo: differenza fra
     * la lettura piu' alta e la piu' bassa. Serve almeno due letture.
     */
    fun odometerSpan(stops: List<FuelStop>, maintenances: List<Maintenance>): Int {
        val letture = buildList {
            stops.forEach { s -> s.odometerKm?.let { add(it) } }
            maintenances.forEach { m -> m.odometerKm?.let { add(it) } }
        }
        if (letture.size < 2) return 0
        val minimo = letture.min()
        val massimo = letture.max()
        val differenza = massimo - minimo
        return if (differenza > 0) differenza else 0
    }

    /**
     * Il riepilogo del periodo.
     *
     * [travelKm] sono i chilometri segnati sulle giornate: li passa chi
     * chiama, perche' stanno nelle giornate e non qui.
     */
    fun totals(
        from: LocalDate,
        to: LocalDate,
        stops: List<FuelStop>,
        expenses: List<VehicleExpense>,
        maintenances: List<Maintenance>,
        travelKm: Int = 0
    ): VehicleTotals {
        val rifornimenti = stops.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val spese = expenses.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val interventi = maintenances.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }

        var pedaggi = 0L
        var parcheggi = 0L
        var altre = 0L
        for (spesa in spese) {
            when (spesa.type) {
                ExpenseType.PEDAGGIO -> pedaggi += spesa.amountCents
                ExpenseType.PARCHEGGIO -> parcheggi += spesa.amountCents
                else -> altre += spesa.amountCents
            }
        }

        return VehicleTotals(
            from = from,
            to = to,
            travelKm = travelKm,
            odometerKm = odometerSpan(rifornimenti, interventi),
            liters = rifornimenti.sumOf { it.liters },
            fuelCents = rifornimenti.sumOf { it.amountCents },
            tollCents = pedaggi,
            parkingCents = parcheggi,
            otherExpenseCents = altre,
            maintenanceCents = interventi.sumOf { it.amountCents ?: 0L },
            fuelStops = rifornimenti.size,
            maintenanceCount = interventi.size,
            // I consumi si calcolano sui rifornimenti del periodo: il primo
            // fa da riferimento e non produce un consumo suo, come sempre.
            consumptions = consumptions(rifornimenti)
        )
    }

    /**
     * L'ultima lettura del contachilometri che si conosce, guardando sia i
     * rifornimenti sia gli interventi in officina. Serve per capire quanto
     * manca alle scadenze a chilometri.
     *
     * Si prende la lettura piu' alta, non la piu' recente per data: il
     * contachilometri non torna indietro, quindi il numero piu' grande e'
     * per forza quello arrivato dopo. Se una data e' stata scritta storta
     * - capita, si registra il rifornimento la sera dopo - il conto regge
     * lo stesso.
     */
    fun lastKnownOdometer(stops: List<FuelStop>, maintenances: List<Maintenance>): Int? {
        val letture = buildList {
            stops.forEach { s -> s.odometerKm?.let { add(it) } }
            maintenances.forEach { m -> m.odometerKm?.let { add(it) } }
        }
        return letture.maxOrNull()
    }
}
