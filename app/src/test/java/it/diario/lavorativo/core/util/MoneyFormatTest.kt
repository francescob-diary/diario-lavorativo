package it.diario.lavorativo.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gli importi digitati alla pompa.
 *
 * Un errore qui non si vede subito: si vede a fine anno, quando il totale
 * non torna con gli scontrini e non si sa piu' dove guardare.
 */
class MoneyFormatTest {

    @Test
    fun `la virgola italiana diventa centesimi`() {
        assertEquals(7615L, MoneyFormat.parseCents("76,15"))
    }

    @Test
    fun `anche il punto della tastiera funziona`() {
        assertEquals(7615L, MoneyFormat.parseCents("76.15"))
    }

    @Test
    fun `un decimale solo vale dieci centesimi`() {
        assertEquals(7610L, MoneyFormat.parseCents("76,1"))
    }

    @Test
    fun `la parola euro non da' fastidio`() {
        assertEquals(5000L, MoneyFormat.parseCents("50 euro"))
    }

    @Test
    fun `i decimali di troppo si tagliano invece di arrotondare`() {
        assertEquals(179L, MoneyFormat.parseCents("1,799"))
    }

    @Test
    fun `un testo che non si capisce restituisce niente`() {
        assertNull(MoneyFormat.parseCents("pieno"))
        assertNull(MoneyFormat.parseCents(""))
        assertNull(MoneyFormat.parseCents(null))
        assertNull(MoneyFormat.parseCents("1,2,3"))
    }

    @Test
    fun `i centesimi tornano a testo con la virgola`() {
        assertEquals("76,15", MoneyFormat.formatCents(7615L))
        assertEquals("7,05", MoneyFormat.formatCents(705L))
        assertEquals("50,00", MoneyFormat.formatCents(5000L))
    }

    @Test
    fun `scritto letto e riscritto resta uguale`() {
        listOf("0,01", "9,99", "100,00", "1234,56").forEach { p ->
            assertEquals(p, MoneyFormat.formatCents(MoneyFormat.parseCents(p)!!))
        }
    }

    @Test
    fun `i litri accettano virgola e punto`() {
        assertEquals(42.37, MoneyFormat.parseDecimal("42,37")!!, 0.001)
        assertEquals(42.37, MoneyFormat.parseDecimal("42.37")!!, 0.001)
    }

    @Test
    fun `i chilometri accettano il punto delle migliaia`() {
        assertEquals(128450, MoneyFormat.parseInt("128.450"))
    }

    @Test
    fun `i chilometri negativi vengono rifiutati`() {
        assertNull(MoneyFormat.parseInt("-5"))
    }

    @Test
    fun `i numeri da mostrare escono con la virgola`() {
        assertEquals("8,00", MoneyFormat.formatDecimal(8.0, 2))
        assertEquals("1,775", MoneyFormat.formatDecimal(1.7752, 3))
        assertEquals("-3,5", MoneyFormat.formatDecimal(-3.5, 1))
    }
}
