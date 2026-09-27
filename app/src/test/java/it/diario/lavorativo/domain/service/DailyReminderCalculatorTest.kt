package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DailyReminderSettings
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WorkDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class DailyReminderCalculatorTest {

    private val base = DailyReminderSettings()

    @Test
    fun luneidPrimaDelleCinqueSuonaOggi() {
        // 28 settembre 2026 e' un lunedi'.
        val da = LocalDateTime.of(2026, 9, 28, 10, 0)
        assertEquals(LocalDateTime.of(2026, 9, 28, 17, 15), DailyReminderCalculator.nextTrigger(base, da))
    }

    @Test
    fun dopoLOrarioSiPassaAlGiornoDopo() {
        val da = LocalDateTime.of(2026, 9, 28, 18, 0)
        assertEquals(LocalDateTime.of(2026, 9, 29, 17, 15), DailyReminderCalculator.nextTrigger(base, da))
    }

    @Test
    fun venerdiSeraSiSaltaAlLunedi() {
        val da = LocalDateTime.of(2026, 10, 2, 18, 0)
        assertEquals(LocalDateTime.of(2026, 10, 5, 17, 15), DailyReminderCalculator.nextTrigger(base, da))
    }

    @Test
    fun sabatoSeScelto() {
        val conSabato = base.copy(days = base.days + DayOfWeek.SATURDAY)
        val da = LocalDateTime.of(2026, 10, 2, 18, 0)
        assertEquals(LocalDateTime.of(2026, 10, 3, 17, 15), DailyReminderCalculator.nextTrigger(conSabato, da))
    }

    @Test
    fun spentoONessunGiorno() {
        val da = LocalDateTime.of(2026, 9, 28, 10, 0)
        assertNull(DailyReminderCalculator.nextTrigger(base.copy(enabled = false), da))
        assertNull(DailyReminderCalculator.nextTrigger(base.copy(days = emptySet()), da))
    }

    @Test
    fun giornataCompilata() {
        val data = LocalDate.of(2026, 9, 28)
        assertFalse(DailyReminderCalculator.isDayCompiled(null))
        assertFalse(DailyReminderCalculator.isDayCompiled(WorkDay(date = data, startTime = Instant.EPOCH)))
        assertTrue(
            DailyReminderCalculator.isDayCompiled(
                WorkDay(date = data, startTime = Instant.EPOCH, endTime = Instant.EPOCH.plusSeconds(3600))
            )
        )
        assertTrue(DailyReminderCalculator.isDayCompiled(WorkDay(date = data, dayType = DayType.FERIE)))
    }
}
