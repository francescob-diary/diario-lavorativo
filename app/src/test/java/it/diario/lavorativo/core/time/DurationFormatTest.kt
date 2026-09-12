package it.diario.lavorativo.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class DurationFormatTest {

    @Test
    fun `ore e minuti`() {
        assertEquals("8h 15m", DurationFormat.short(Duration.ofMinutes(495)))
    }

    @Test
    fun `ore esatte senza minuti`() {
        assertEquals("8h", DurationFormat.short(Duration.ofHours(8)))
    }

    @Test
    fun `solo minuti`() {
        assertEquals("45m", DurationFormat.short(Duration.ofMinutes(45)))
    }

    @Test
    fun `durata nulla`() {
        assertEquals("0m", DurationFormat.short(Duration.ZERO))
    }

    @Test
    fun `durata negativa trattata come zero`() {
        assertEquals("0m", DurationFormat.short(Duration.ofMinutes(-30)))
    }

    @Test
    fun `oltre le ventiquattro ore non si azzera`() {
        assertEquals("30h", DurationFormat.short(Duration.ofHours(30)))
    }

    @Test
    fun `segno positivo`() {
        assertEquals("+1h 30m", DurationFormat.signed(Duration.ofMinutes(90)))
    }

    @Test
    fun `segno negativo`() {
        assertEquals("-45m", DurationFormat.signed(Duration.ofMinutes(-45)))
    }
}
