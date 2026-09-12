package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DueStatus
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.MaintenanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Le scadenze del mezzo.
 *
 * Il punto delicato non e' calcolare i giorni, e' non inventare scadenze
 * che nessuno ha dichiarato.
 */
class MaintenanceSchedulerTest {

    private val sched = MaintenanceScheduler()
    private val oggi = LocalDate.of(2026, 6, 1)

    private fun revisione(scadenza: LocalDate?) = Maintenance(
        id = 1L, vehicleId = 1L, date = LocalDate.of(2024, 5, 1),
        type = MaintenanceType.REVISIONE, nextDueDate = scadenza
    )

    private fun tagliando(km: Int?) = Maintenance(
        id = 2L, vehicleId = 1L, date = LocalDate.of(2025, 9, 1),
        type = MaintenanceType.TAGLIANDO, odometerKm = 80000, nextDueKm = km
    )

    @Test
    fun `una revisione passata risulta scaduta`() {
        val due = sched.due(revisione(oggi.minusDays(3)), oggi)
        assertEquals(DueStatus.SCADUTA, due.status)
        assertTrue((due.daysLeft ?: 0L) < 0L)
    }

    @Test
    fun `una revisione fra dieci giorni risulta vicina`() {
        assertEquals(DueStatus.VICINA, sched.due(revisione(oggi.plusDays(10)), oggi).status)
    }

    @Test
    fun `una revisione fra otto mesi sta buona`() {
        assertEquals(DueStatus.OK, sched.due(revisione(oggi.plusMonths(8)), oggi).status)
    }

    @Test
    fun `il tagliando a chilometri usa il contatore attuale`() {
        assertEquals(DueStatus.OK, sched.due(tagliando(100000), oggi, 90000).status)
        assertEquals(DueStatus.VICINA, sched.due(tagliando(100000), oggi, 99300).status)
        assertEquals(DueStatus.SCADUTA, sched.due(tagliando(100000), oggi, 101000).status)
    }

    @Test
    fun `senza contatore attuale la scadenza a chilometri non allarma`() {
        val due = sched.due(tagliando(100000), oggi, null)
        assertNull(due.kmLeft)
        assertEquals(DueStatus.OK, due.status)
    }

    @Test
    fun `fra due scadenze comanda la piu' urgente`() {
        val doppia = tagliando(100000).copy(nextDueDate = oggi.plusDays(5))
        assertEquals(DueStatus.VICINA, sched.due(doppia, oggi, 85000).status)
    }

    @Test
    fun `un intervento senza scadenza dichiarata non compare`() {
        val riparazione = Maintenance(
            id = 3L, vehicleId = 1L, date = oggi.minusDays(3),
            type = MaintenanceType.RIPARAZIONE
        )
        assertTrue(sched.dueList(listOf(riparazione), oggi).isEmpty())
    }

    @Test
    fun `di ogni tipo conta solo l ultimo intervento`() {
        val vecchio = Maintenance(
            id = 4L, vehicleId = 1L, date = LocalDate.of(2024, 1, 1),
            type = MaintenanceType.TAGLIANDO, nextDueDate = LocalDate.of(2025, 1, 1)
        )
        val nuovo = Maintenance(
            id = 5L, vehicleId = 1L, date = LocalDate.of(2026, 1, 1),
            type = MaintenanceType.TAGLIANDO, nextDueDate = LocalDate.of(2027, 1, 1)
        )
        val lista = sched.dueList(listOf(vecchio, nuovo), oggi)
        assertEquals(1, lista.size)
        assertEquals(DueStatus.OK, lista[0].status)
    }

    @Test
    fun `le scadute vengono prima nell elenco`() {
        val lista = sched.dueList(
            listOf(revisione(oggi.minusDays(10)), tagliando(100000)), oggi, 85000
        )
        assertEquals(DueStatus.SCADUTA, lista.first().status)
    }

    @Test
    fun `solo le scadenze aperte finiscono negli avvisi`() {
        val aperte = sched.toWarn(
            listOf(revisione(oggi.minusDays(2)), tagliando(100000)), oggi, 85000
        )
        assertEquals(1, aperte.size)
    }

    @Test
    fun `senza scadenze non c e' nessun testo da mostrare`() {
        assertNull(sched.warningText(emptyList()))
    }

    @Test
    fun `il testo dell avviso nomina l intervento`() {
        val testo = sched.warningText(sched.toWarn(listOf(revisione(oggi.minusDays(1))), oggi))
        assertEquals("Revisione: scaduto", testo)
    }

    @Test
    fun `la proposta di chilometri parte dal contatore`() {
        assertEquals(115000, sched.suggestedNextKm(MaintenanceType.CAMBIO_OLIO, 100000))
        assertEquals(120000, sched.suggestedNextKm(MaintenanceType.TAGLIANDO, 100000))
    }

    @Test
    fun `per la revisione non si propongono chilometri`() {
        assertNull(sched.suggestedNextKm(MaintenanceType.REVISIONE, 100000))
    }

    @Test
    fun `senza contatore non si propone niente`() {
        assertNull(sched.suggestedNextKm(MaintenanceType.TAGLIANDO, null))
    }
}
