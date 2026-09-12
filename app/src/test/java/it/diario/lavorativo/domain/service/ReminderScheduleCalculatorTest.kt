package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderScheduleCalculatorTest {

    private val calculator = ReminderScheduleCalculator()
    private val settings = ReminderSettings()

    @Test
    fun `da meta settimana si va al lunedi successivo`() {
        val giovedi = LocalDateTime.of(2026, 3, 5, 14, 0)
        assertEquals(LocalDateTime.of(2026, 3, 9, 8, 20), calculator.nextTrigger(settings, giovedi))
    }

    @Test
    fun `il lunedi prima dell orario scatta lo stesso giorno`() {
        val presto = LocalDateTime.of(2026, 3, 9, 6, 30)
        assertEquals(LocalDateTime.of(2026, 3, 9, 8, 20), calculator.nextTrigger(settings, presto))
    }

    @Test
    fun `il lunedi dopo l orario si passa alla settimana successiva`() {
        val tardi = LocalDateTime.of(2026, 3, 9, 9, 0)
        assertEquals(LocalDateTime.of(2026, 3, 16, 8, 20), calculator.nextTrigger(settings, tardi))
    }

    @Test
    fun `all orario esatto il promemoria non si ripete`() {
        val esatto = LocalDateTime.of(2026, 3, 9, 8, 20)
        assertEquals(LocalDateTime.of(2026, 3, 16, 8, 20), calculator.nextTrigger(settings, esatto))
    }

    @Test
    fun `si riepiloga sempre la settimana gia conclusa`() {
        assertEquals(LocalDate.of(2026, 3, 2), calculator.weekToReport(LocalDate.of(2026, 3, 9)))
        assertEquals(LocalDate.of(2026, 3, 9), calculator.weekToReport(LocalDate.of(2026, 3, 16)))
    }

    @Test
    fun `il giorno e l orario sono modificabili`() {
        val venerdi = ReminderSettings(dayOfWeek = DayOfWeek.FRIDAY, hour = 17, minute = 0)
        val giovedi = LocalDateTime.of(2026, 3, 5, 14, 0)
        assertEquals(LocalDateTime.of(2026, 3, 6, 17, 0), calculator.nextTrigger(venerdi, giovedi))
    }

    @Test
    fun `il calcolo scavalca il cambio d anno`() {
        val fineAnno = LocalDateTime.of(2026, 12, 30, 12, 0)
        assertEquals(LocalDateTime.of(2027, 1, 4, 8, 20), calculator.nextTrigger(settings, fineAnno))
        assertEquals(LocalDate.of(2026, 12, 28), calculator.weekToReport(LocalDate.of(2027, 1, 4)))
    }
}
