package it.diario.lavorativo.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TimeTextParserTest {

    @Test
    fun orariScrittiInTantiModi() {
        assertEquals(LocalTime.of(8, 0), TimeTextParser.parseClock("8"))
        assertEquals(LocalTime.of(8, 30), TimeTextParser.parseClock("8:30"))
        assertEquals(LocalTime.of(8, 30), TimeTextParser.parseClock("8.30"))
        assertEquals(LocalTime.of(8, 30), TimeTextParser.parseClock("830"))
        assertEquals(LocalTime.of(17, 0), TimeTextParser.parseClock("17"))
        assertEquals(LocalTime.of(17, 5), TimeTextParser.parseClock("1705"))
    }

    @Test
    fun orariSbagliatiNonPassano() {
        assertNull(TimeTextParser.parseClock("25"))
        assertNull(TimeTextParser.parseClock("8:75"))
        assertNull(TimeTextParser.parseClock("otto"))
        assertNull(TimeTextParser.parseClock(""))
    }

    @Test
    fun oreInMinuti() {
        assertEquals(240, TimeTextParser.parseHours("4"))
        assertEquals(210, TimeTextParser.parseHours("3,5"))
        assertEquals(210, TimeTextParser.parseHours("3.5"))
        assertEquals(210, TimeTextParser.parseHours("3:30"))
        assertNull(TimeTextParser.parseHours("30"))
        assertNull(TimeTextParser.parseHours("tre"))
    }

    @Test
    fun oreScritteBene() {
        assertEquals("4", TimeTextParser.formatHours(240))
        assertEquals("3:30", TimeTextParser.formatHours(210))
    }
}
