package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.BreakType
import it.diario.lavorativo.domain.model.WorkBreak
import it.diario.lavorativo.domain.model.WorkDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Test del calcolo delle ore. Sono unit test puri: nessun emulatore necessario.
 * Eseguirli con: ./gradlew testDebugUnitTest
 */
class WorkTimeCalculatorTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val date = LocalDate.of(2026, 9, 7)
    private val calculator = WorkTimeCalculator()
    private val standardMinutes = 480

    private fun at(hour: Int, minute: Int): Instant =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()

    @Test
    fun `esempio della specifica: 0730-1700 con due pause`() {
        val day = WorkDay(
            id = 1, date = date,
            startTime = at(7, 30), endTime = at(17, 0),
            breaks = listOf(
                WorkBreak(id = 1, workDayId = 1, startTime = at(10, 0), endTime = at(10, 15)),
                WorkBreak(id = 2, workDayId = 1, startTime = at(12, 30), endTime = at(13, 30), type = BreakType.PRANZO)
            )
        )

        val summary = calculator.summarize(day, at(18, 0), standardMinutes)

        assertEquals(570L, summary.gross.toMinutes())     // 9h 30m di presenza
        assertEquals(75L, summary.breaks.toMinutes())     // 1h 15m di pause
        assertEquals(495L, summary.net.toMinutes())       // 8h 15m lavorate
        assertEquals(15L, summary.overtime.toMinutes())   // 15m di straordinario
        assertEquals(0L, summary.deficit.toMinutes())
    }

    @Test
    fun `giornata non iniziata non produce ore`() {
        val summary = calculator.summarize(WorkDay(id = 2, date = date), at(9, 0), standardMinutes)

        assertFalse(summary.isStarted)
        assertEquals(0L, summary.net.toMinutes())
        assertEquals(Duration.ofMinutes(480), summary.standard)
    }

    @Test
    fun `giornata in corso conta fino ad adesso`() {
        val day = WorkDay(id = 3, date = date, startTime = at(7, 30))

        val summary = calculator.summarize(day, at(9, 0), standardMinutes)

        assertTrue(summary.isRunning)
        assertEquals(90L, summary.gross.toMinutes())
        assertEquals(390L, summary.deficit.toMinutes())
    }

    @Test
    fun `pausa aperta viene contata fino ad adesso`() {
        val day = WorkDay(
            id = 4, date = date, startTime = at(7, 30),
            breaks = listOf(WorkBreak(id = 9, workDayId = 4, startTime = at(10, 0)))
        )

        val summary = calculator.summarize(day, at(10, 20), standardMinutes)

        assertTrue(summary.isOnBreak)
        assertEquals(20L, summary.breaks.toMinutes())
        assertEquals(150L, summary.net.toMinutes())
        assertEquals(20L, summary.currentBreak?.toMinutes())
    }

    @Test
    fun `pause sovrapposte non vengono contate due volte`() {
        val day = WorkDay(
            id = 5, date = date, startTime = at(8, 0), endTime = at(12, 0),
            breaks = listOf(
                WorkBreak(id = 1, workDayId = 5, startTime = at(9, 0), endTime = at(10, 0)),
                WorkBreak(id = 2, workDayId = 5, startTime = at(9, 30), endTime = at(10, 30))
            )
        )

        assertEquals(90L, calculator.summarize(day, at(13, 0), standardMinutes).breaks.toMinutes())
    }

    @Test
    fun `pausa fuori orario viene tagliata`() {
        val day = WorkDay(
            id = 6, date = date, startTime = at(8, 0), endTime = at(12, 0),
            breaks = listOf(WorkBreak(id = 1, workDayId = 6, startTime = at(7, 0), endTime = at(8, 30)))
        )

        assertEquals(30L, calculator.summarize(day, at(13, 0), standardMinutes).breaks.toMinutes())
    }

    @Test
    fun `turno che passa la mezzanotte`() {
        val day = WorkDay(
            id = 7, date = date,
            startTime = at(22, 0),
            endTime = at(22, 0).plus(Duration.ofHours(6))
        )

        assertEquals(360L, calculator.summarize(day, at(23, 0), standardMinutes).gross.toMinutes())
    }

    @Test
    fun `override dell orario standard della singola giornata`() {
        val day = WorkDay(
            id = 8, date = date, startTime = at(8, 0), endTime = at(12, 30),
            standardMinutesOverride = 240
        )

        assertEquals(30L, calculator.summarize(day, at(13, 0), standardMinutes).overtime.toMinutes())
    }

    @Test
    fun `il netto non diventa mai negativo`() {
        val day = WorkDay(
            id = 9, date = date, startTime = at(8, 0), endTime = at(9, 0),
            breaks = listOf(WorkBreak(id = 1, workDayId = 9, startTime = at(8, 0), endTime = at(9, 0)))
        )

        assertEquals(0L, calculator.summarize(day, at(10, 0), standardMinutes).net.toMinutes())
    }
}
