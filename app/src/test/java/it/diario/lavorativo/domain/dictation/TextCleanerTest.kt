package it.diario.lavorativo.domain.dictation

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCleanerTest {

    @Test
    fun raccontoAttaccatoDiventaFrasi() {
        assertEquals(
            "Stamattina ho iniziato a rasare le pareti della camera. Poi nel pomeriggio ho montato il cubo " +
                "nella zona lounge con il cartongesso. Dopo ho pulito tutto.",
            TextCleaner.clean(
                "stamattina eh ho iniziato a rasare le pareti della camera poi poi nel pomeriggio ho montato " +
                    "il cubo nella zona lounge con il cartongesso e dopo ho pulito tutto"
            )
        )
    }

    @Test
    fun ripetizioniEdEsitazioniVia() {
        assertEquals(
            "Abbiamo fatto il massetto del bagno tutto il bagno. Quindi domani si posa.",
            TextCleaner.clean("abbiamo abbiamo fatto il massetto del bagno diciamo tutto il bagno quindi domani si posa")
        )
    }

    @Test
    fun frasiCorteNonSiSpezzano() {
        assertEquals("Ho fatto gasolio la mattina poi sono andato in viale sarca.",
            TextCleaner.clean("ho fatto gasolio la mattina poi sono andato in viale sarca"))
    }

    @Test
    fun casiLimite() {
        assertEquals("", TextCleaner.clean("   "))
        assertEquals("Posato piastrelle.", TextCleaner.clean("posato piastrelle"))
        assertEquals("Fatto.", TextCleaner.clean("Fatto."))
    }
}
