package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.ExpenseType
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.MaintenanceType
import it.diario.lavorativo.domain.model.VehicleExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * I conti del mezzo.
 *
 * Il caso che conta davvero e' il consumo: e' l'unico numero che l'app
 * calcola e che l'utente non puo' controllare a occhio.
 */
class VehicleCalculatorTest {

    private val calc = VehicleCalculator()
    private val giorno = LocalDate.of(2026, 3, 1)

    private fun stop(
        id: Long,
        piu: Long,
        litri: Double,
        euro: Double,
        km: Int?,
        pieno: Boolean = true
    ) = FuelStop(
        id = id,
        vehicleId = 1L,
        date = giorno.plusDays(piu),
        liters = litri,
        amountCents = Math.round(euro * 100.0),
        odometerKm = km,
        fullTank = pieno
    )

    @Test
    fun `il primo pieno non produce un consumo`() {
        val consumi = calc.consumptions(listOf(stop(1, 0, 40.0, 70.0, 100000)))
        assertTrue(consumi.isEmpty())
    }

    @Test
    fun `fra due pieni si misurano km e litri`() {
        val consumi = calc.consumptions(
            listOf(stop(1, 0, 40.0, 70.0, 100000), stop(2, 10, 40.0, 72.0, 100500))
        )
        assertEquals(1, consumi.size)
        assertEquals(500, consumi[0].km)
        assertEquals(40.0, consumi[0].liters, 0.001)
        assertEquals(8.0, consumi[0].litersPer100Km, 0.001)
    }

    @Test
    fun `il rabbocco in mezzo si somma ai litri`() {
        val consumi = calc.consumptions(
            listOf(
                stop(1, 0, 40.0, 70.0, 100000),
                stop(2, 5, 10.0, 18.0, 100200, pieno = false),
                stop(3, 10, 30.0, 54.0, 100500)
            )
        )
        assertEquals(1, consumi.size)
        assertEquals(40.0, consumi[0].liters, 0.001)
        assertEquals(500, consumi[0].km)
    }

    @Test
    fun `un pieno senza contachilometri non spezza il tratto`() {
        val consumi = calc.consumptions(
            listOf(
                stop(1, 0, 40.0, 70.0, 100000),
                stop(2, 5, 20.0, 36.0, null),
                stop(3, 10, 20.0, 36.0, 100500)
            )
        )
        assertEquals(1, consumi.size)
        assertEquals(40.0, consumi[0].liters, 0.001)
    }

    @Test
    fun `senza contachilometri non si inventa nessun consumo`() {
        val consumi = calc.consumptions(
            listOf(stop(1, 0, 40.0, 70.0, null), stop(2, 10, 40.0, 70.0, null))
        )
        assertTrue(consumi.isEmpty())
    }

    @Test
    fun `un contachilometri fermo non produce un consumo`() {
        val consumi = calc.consumptions(
            listOf(stop(1, 0, 40.0, 70.0, 100000), stop(2, 10, 40.0, 70.0, 100000))
        )
        assertTrue(consumi.isEmpty())
    }

    @Test
    fun `i rifornimenti in ordine sparso vengono rimessi in fila`() {
        val consumi = calc.consumptions(
            listOf(stop(2, 10, 40.0, 72.0, 100500), stop(1, 0, 40.0, 70.0, 100000))
        )
        assertEquals(1, consumi.size)
        assertEquals(500, consumi[0].km)
    }

    @Test
    fun `il prezzo al litro esce dallo scontrino`() {
        assertEquals(1.80, stop(1, 0, 40.0, 72.0, null).pricePerLiter!!, 0.001)
    }

    @Test
    fun `le spese si dividono per tipo`() {
        val spese = listOf(
            VehicleExpense(1L, 1L, null, giorno, ExpenseType.PEDAGGIO, 1250L),
            VehicleExpense(2L, 1L, null, giorno, ExpenseType.PARCHEGGIO, 400L),
            VehicleExpense(3L, 1L, null, giorno, ExpenseType.LAVAGGIO, 1500L)
        )
        val t = calc.totals(giorno, giorno.plusDays(1), emptyList(), spese, emptyList())
        assertEquals(1250L, t.tollCents)
        assertEquals(400L, t.parkingCents)
        assertEquals(1500L, t.otherExpenseCents)
    }

    @Test
    fun `quello che sta fuori dal periodo non entra nei totali`() {
        val spese = listOf(
            VehicleExpense(1L, 1L, null, giorno, ExpenseType.PEDAGGIO, 1250L),
            VehicleExpense(2L, 1L, null, giorno.plusMonths(6), ExpenseType.PEDAGGIO, 9999L)
        )
        val t = calc.totals(giorno, giorno.plusDays(10), emptyList(), spese, emptyList())
        assertEquals(1250L, t.expensesCents)
    }

    @Test
    fun `i chilometri del contatore battono quelli delle giornate`() {
        val t = calc.totals(
            giorno, giorno.plusDays(30),
            listOf(stop(1, 0, 40.0, 70.0, 100000), stop(2, 10, 40.0, 70.0, 100500)),
            emptyList(), emptyList(),
            travelKm = 480
        )
        assertEquals(500, t.odometerKm)
        assertEquals(480, t.travelKm)
        assertEquals(500, t.effectiveKm)
    }

    @Test
    fun `senza contatore valgono i chilometri delle giornate`() {
        val t = calc.totals(
            giorno, giorno.plusDays(30), emptyList(), emptyList(), emptyList(), travelKm = 300
        )
        assertEquals(300, t.effectiveKm)
    }

    @Test
    fun `l ultimo contatore noto e' la lettura piu' alta`() {
        val letture = listOf(stop(1, 30, 40.0, 70.0, 99000), stop(2, 1, 40.0, 70.0, 102000))
        assertEquals(102000, calc.lastKnownOdometer(letture, emptyList()))
    }

    @Test
    fun `il contatore si legge anche dagli interventi in officina`() {
        val tagliando = Maintenance(
            id = 1L, vehicleId = 1L, date = giorno,
            type = MaintenanceType.TAGLIANDO, odometerKm = 105000
        )
        assertEquals(
            105000,
            calc.lastKnownOdometer(listOf(stop(1, 0, 40.0, 70.0, 100000)), listOf(tagliando))
        )
    }

    @Test
    fun `senza nessuna lettura il contatore resta sconosciuto`() {
        assertNull(calc.lastKnownOdometer(listOf(stop(1, 0, 40.0, 70.0, null)), emptyList()))
    }

    @Test
    fun `un periodo senza niente si dichiara vuoto`() {
        val t = calc.totals(giorno, giorno.plusDays(30), emptyList(), emptyList(), emptyList())
        assertTrue(t.isEmpty)
        assertEquals(0L, t.totalCents)
    }
}
