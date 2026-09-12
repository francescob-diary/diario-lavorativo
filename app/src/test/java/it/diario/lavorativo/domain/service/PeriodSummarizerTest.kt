package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkBreak
import it.diario.lavorativo.domain.model.WorkDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class PeriodSummarizerTest {

    private val summarizer = PeriodSummarizer(WorkTimeCalculator())
    private val standard = 8 * 60
    private val now: Instant = at(LocalDate.of(2026, 9, 7), 18, 0)

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant =
        date.atTime(LocalTime.of(hour, minute)).toInstant(ZoneOffset.UTC)

    private fun workDay(
        date: LocalDate,
        startHour: Int = 8,
        startMinute: Int = 0,
        endHour: Int? = 17,
        endMinute: Int = 0,
        breakMinutes: Long = 0,
        type: DayType = DayType.LAVORO,
        id: Long = date.toEpochDay()
    ): WorkDay {
        val start = at(date, startHour, startMinute)
        val end = endHour?.let { at(date, it, endMinute) }
        val breaks = if (breakMinutes > 0) {
            listOf(
                WorkBreak(
                    id = id,
                    workDayId = id,
                    startTime = start.plus(Duration.ofHours(4)),
                    endTime = start.plus(Duration.ofHours(4)).plus(Duration.ofMinutes(breakMinutes))
                )
            )
        } else {
            emptyList()
        }
        return WorkDay(
            id = id,
            date = date,
            startTime = start,
            endTime = end,
            breaks = breaks,
            dayType = type
        )
    }

    @Test
    fun `periodo vuoto non ha dati`() {
        val totals = summarizer.totals(emptyList(), now, standard)
        assertEquals(0, totals.workedDays)
        assertFalse(totals.hasData)
        assertEquals(Duration.ZERO, totals.net)
    }

    @Test
    fun `somma il netto di piu giornate`() {
        val days = listOf(
            workDay(LocalDate.of(2026, 9, 1)),
            workDay(LocalDate.of(2026, 9, 2)),
            workDay(LocalDate.of(2026, 9, 3))
        )
        val totals = summarizer.totals(days, now, standard)
        assertEquals(3, totals.workedDays)
        assertEquals(Duration.ofHours(27), totals.net)
        assertTrue(totals.hasData)
    }

    @Test
    fun `le pause vengono sottratte dal netto e sommate a parte`() {
        val days = listOf(
            workDay(LocalDate.of(2026, 9, 1), breakMinutes = 60),
            workDay(LocalDate.of(2026, 9, 2), breakMinutes = 30)
        )
        val totals = summarizer.totals(days, now, standard)
        assertEquals(Duration.ofMinutes(90), totals.breaks)
        assertEquals(Duration.ofHours(18).minus(Duration.ofMinutes(90)), totals.net)
    }

    @Test
    fun `lo straordinario somma solo le eccedenze`() {
        val days = listOf(
            // 9 ore lorde, 1 ora di pausa: netto 8 ore, nessuno straordinario
            workDay(LocalDate.of(2026, 9, 1), breakMinutes = 60),
            // 9 ore nette: 1 ora di straordinario
            workDay(LocalDate.of(2026, 9, 2), endHour = 17, breakMinutes = 0)
        )
        val totals = summarizer.totals(days, now, standard)
        assertEquals(Duration.ofHours(1), totals.overtime)
    }

    @Test
    fun `le giornate di ferie non contano come lavorate`() {
        val days = listOf(
            workDay(LocalDate.of(2026, 9, 1)),
            workDay(LocalDate.of(2026, 9, 2), type = DayType.FERIE),
            workDay(LocalDate.of(2026, 9, 3), type = DayType.FERIE),
            workDay(LocalDate.of(2026, 9, 4), type = DayType.MALATTIA)
        )
        val totals = summarizer.totals(days, now, standard)
        assertEquals(1, totals.workedDays)
        assertEquals(Duration.ofHours(9), totals.net)
        assertEquals(2, totals.absenceDays[DayType.FERIE])
        assertEquals(1, totals.absenceDays[DayType.MALATTIA])
    }

    @Test
    fun `una giornata aperta contribuisce al tempo ma non al conteggio`() {
        val today = LocalDate.of(2026, 9, 7)
        val days = listOf(
            workDay(LocalDate.of(2026, 9, 4)),
            // aperta alle 8, adesso sono le 18: 10 ore maturate
            workDay(today, endHour = null)
        )
        val totals = summarizer.totals(days, now, standard)
        assertEquals(1, totals.workedDays)
        assertEquals(Duration.ofHours(19), totals.net)
    }

    @Test
    fun `una giornata mai iniziata viene ignorata`() {
        val days = listOf(
            WorkDay(id = 1L, date = LocalDate.of(2026, 9, 1)),
            workDay(LocalDate.of(2026, 9, 2))
        )
        val totals = summarizer.totals(days, now, standard)
        assertEquals(1, totals.workedDays)
        assertEquals(Duration.ofHours(9), totals.net)
    }

    @Test
    fun `la media si calcola solo sui giorni lavorati`() {
        val days = listOf(
            workDay(LocalDate.of(2026, 9, 1), endHour = 16),
            workDay(LocalDate.of(2026, 9, 2), endHour = 18),
            workDay(LocalDate.of(2026, 9, 3), type = DayType.FERIE)
        )
        val totals = summarizer.totals(days, now, standard)
        assertEquals(2, totals.workedDays)
        assertEquals(Duration.ofHours(18), totals.net)
        assertEquals(Duration.ofHours(9), totals.averageNet)
    }

    @Test
    fun `la media di un periodo senza lavoro e zero`() {
        val days = listOf(workDay(LocalDate.of(2026, 9, 1), type = DayType.FESTIVO))
        val totals = summarizer.totals(days, now, standard)
        assertEquals(Duration.ZERO, totals.averageNet)
        assertTrue(totals.hasData)
    }
}
