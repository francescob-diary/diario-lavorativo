package it.diario.lavorativo.domain.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItalianNumbersTest {

    @Test
    fun numeriAParole() {
        assertEquals(80, ItalianNumbers.parse("ottanta"))
        assertEquals(31, ItalianNumbers.parse("trentuno"))
        assertEquals(28, ItalianNumbers.parse("ventotto"))
        assertEquals(120, ItalianNumbers.parse("centoventi"))
        assertEquals(180, ItalianNumbers.parse("centottanta"))
        assertEquals(108, ItalianNumbers.parse("centotto"))
        assertEquals(250, ItalianNumbers.parse("duecentocinquanta"))
        assertEquals(1200, ItalianNumbers.parse("milleduecento"))
        assertEquals(3000, ItalianNumbers.parse("tremila"))
        assertEquals(45, ItalianNumbers.parse("45"))
        assertNull(ItalianNumbers.parse("cantiere"))
    }

    @Test
    fun chilometriNelRacconto() {
        assertEquals(80, ItalianNumbers.findKm("stamattina ho fatto gasolio e ottanta chilometri col furgone"))
        assertEquals(42, ItalianNumbers.findKm("fatto 42 km"))
        assertEquals(120, ItalianNumbers.findKm("in tutto centoventi km"))
        assertNull(ItalianNumbers.findKm("ho posato le piastrelle"))
    }
}
