package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DaySite
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.WorkDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class SiteDaysCalculatorTest {

    private val zona = ZoneId.of("Europe/Rome")
    private val a = Site(id = 1, name = "Montenero")
    private val b = Site(id = 2, name = "Rossi")

    private fun giorno(data: LocalDate, principale: Site?, extra: List<DaySite> = emptyList()) = WorkDay(
        id = data.toEpochDay(),
        date = data,
        startTime = data.atTime(8, 0).atZone(zona).toInstant(),
        endTime = data.atTime(16, 0).atZone(zona).toInstant(),
        site = principale,
        extraSites = extra
    )

    @Test
    fun giorniPerCantiereConOreDivise() {
        val d1 = LocalDate.of(2026, 9, 28)
        val d2 = LocalDate.of(2026, 9, 29)
        val giorni = listOf(
            giorno(d1, a),
            giorno(d2, a, listOf(DaySite(site = b, minutes = 180)))
        )
        val r = SiteDaysCalculator(WorkTimeCalculator())
            .build(giorni, d2.atTime(20, 0).atZone(zona).toInstant(), 480)

        assertEquals(listOf(d2, d1), r[1L]!!.map { it.date })
        assertEquals(Duration.ofHours(8), r[1L]!!.last().hours)
        assertEquals(Duration.ofHours(5), r[1L]!!.first().hours)
        assertEquals(listOf(d2), r[2L]!!.map { it.date })
        assertEquals(Duration.ofHours(3), r[2L]!!.single().hours)
    }
}
