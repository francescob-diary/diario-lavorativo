package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.EventType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class WeeklyReportBuilderTest {

    private val calculator = WorkTimeCalculator()
    private val builder = WeeklyReportBuilder(calculator, PeriodSummarizer(calculator))
    private val zone = ZoneOffset.UTC
    private val standard = 8 * 60
    private val monday = LocalDate.of(2026, 3, 2)
    private val now: Instant = LocalDate.of(2026, 3, 9).atTime(9, 0).toInstant(ZoneOffset.UTC)

    private fun at(d: LocalDate, h: Int): Instant =
        d.atTime(LocalTime.of(h, 0)).toInstant(ZoneOffset.UTC)

    private fun day(
        d: LocalDate,
        from: Int = 8,
        to: Int? = 17,
        type: DayType = DayType.LAVORO
    ) = WorkDay(
        id = d.toEpochDay(),
        date = d,
        startTime = at(d, from),
        endTime = to?.let { at(d, it) },
        site = Site(id = 1L, name = "Cantiere Rossi"),
        dayType = type
    )

    @Test
    fun `il foglio ha sempre sette righe`() {
        val report = builder.build(monday, emptyList(), now = now, standardMinutes = standard, zone = zone)
        assertEquals(7, report.days.size)
        assertFalse(report.hasData)
    }

    @Test
    fun `una settimana di cinque giorni somma correttamente`() {
        val days = (0L..4L).map { day(monday.plusDays(it)) }
        val report = builder.build(monday, days, now = now, standardMinutes = standard, zone = zone)
        assertEquals(5, report.totals.workedDays)
        assertEquals(Duration.ofHours(45), report.totals.net)
        assertEquals(Duration.ofHours(5), report.totals.overtime)
        assertTrue(report.isReadyToDeliver)
    }

    @Test
    fun `una giornata aperta impedisce la consegna`() {
        val days = listOf(day(monday), day(monday.plusDays(1), to = null))
        val report = builder.build(monday, days, now = now, standardMinutes = standard, zone = zone)
        assertEquals(1, report.incompleteDays.size)
        assertFalse(report.isReadyToDeliver)
    }

    @Test
    fun `le ferie non producono ore ma restano sul foglio`() {
        val days = listOf(day(monday), day(monday.plusDays(1), type = DayType.FERIE))
        val report = builder.build(monday, days, now = now, standardMinutes = standard, zone = zone)
        assertEquals(Duration.ZERO, report.days[1].net)
        assertTrue(report.days[1].hasContent)
        assertEquals(1, report.totals.absenceDays[DayType.FERIE])
    }

    @Test
    fun `le lavorazioni finiscono nella riga del giorno`() {
        val d = day(monday)
        val activities = mapOf(
            d.id to listOf(
                WorkActivity(
                    id = 1, workDayId = d.id, category = ActivityCategory.MARMO,
                    description = "Posa soglie", quantity = "12 mq"
                ),
                WorkActivity(
                    id = 2, workDayId = d.id, category = ActivityCategory.PULIZIA,
                    description = "Pulizia cantiere"
                )
            )
        )
        val report = builder.build(
            monday, listOf(d), activities,
            now = now, standardMinutes = standard, zone = zone
        )
        assertEquals("Posa soglie (12 mq); Pulizia cantiere", report.days[0].work)
    }

    @Test
    fun `senza lavorazioni si usa la descrizione della giornata`() {
        val d = day(monday).copy(description = "Ripresa intonaci")
        val report = builder.build(monday, listOf(d), now = now, standardMinutes = standard, zone = zone)
        assertEquals("Ripresa intonaci", report.days[0].work)
    }

    @Test
    fun `solo gli eventi gravi o irrisolti vanno segnalati`() {
        val d = day(monday)
        val events = mapOf(
            d.id to listOf(
                WorkEvent(
                    id = 1, workDayId = d.id, type = EventType.FERMO_LAVORI,
                    time = at(monday, 10), title = "Manca il materiale",
                    severity = EventSeverity.GRAVE
                ),
                WorkEvent(
                    id = 2, workDayId = d.id, type = EventType.CONSEGNA_MATERIALE,
                    time = at(monday, 11), title = "Arrivati bancali"
                ),
                WorkEvent(
                    id = 3, workDayId = d.id, type = EventType.PROBLEMA,
                    time = at(monday, 15), title = "Da chiarire", unresolved = true
                )
            )
        )
        val report = builder.build(
            monday, listOf(d), events = events,
            now = now, standardMinutes = standard, zone = zone
        )
        assertEquals(2, report.notableEvents.size)
        assertEquals("Manca il materiale", report.notableEvents[0].title)
    }

    @Test
    fun `una data infrasettimanale viene riportata al lunedi`() {
        val days = (0L..4L).map { day(monday.plusDays(it)) }
        val report = builder.build(
            LocalDate.of(2026, 3, 4), days,
            now = now, standardMinutes = standard, zone = zone
        )
        assertEquals(monday, report.weekStart)
        assertEquals(LocalDate.of(2026, 3, 8), report.weekEnd)
    }
}
