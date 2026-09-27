package it.diario.lavorativo.domain.dictation

import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DictationParsingTest {

    private val cantieri = listOf(
        Site(id = 1, name = "Rossi", address = "Viale Sarca 85", city = "Milano"),
        Site(id = 2, name = "Condominio Rivoli", address = "Corso Francia 12", city = "Rivoli")
    )

    @Test
    fun orariEPausaInCifre() {
        val d = RuleBasedDayParser.parse(
            "Oggi ho lavorato dalle 8 alle 17 in viale Sarca, pausa pranzo dalle 12 alle 13",
            cantieri
        )
        assertEquals(LocalTime.of(8, 0), d.start)
        assertEquals(LocalTime.of(17, 0), d.end)
        assertEquals(listOf(DraftBreak(LocalTime.of(12, 0), LocalTime.of(13, 0))), d.breaks)
        assertEquals(1L, d.sites.first().siteId)
    }

    @Test
    fun orariAParoleEPomeriggio() {
        val d = RuleBasedDayParser.parse("dalle otto e mezza alle cinque al condominio rivoli", cantieri)
        assertEquals(LocalTime.of(8, 30), d.start)
        assertEquals(LocalTime.of(17, 0), d.end)
        assertEquals(2L, d.sites.first().siteId)
    }

    @Test
    fun pausaSenzaOrariDiventaMezzogiorno() {
        val d = RuleBasedDayParser.parse("dalle 7 alle 16 con un'ora di pausa", cantieri)
        assertEquals(listOf(DraftBreak(LocalTime.of(12, 0), LocalTime.of(13, 0))), d.breaks)
    }

    @Test
    fun ferieEChilometri() {
        assertEquals(DayType.FERIE, RuleBasedDayParser.parse("oggi ero in ferie", cantieri).dayType)
        assertEquals(42, RuleBasedDayParser.parse("fatto 42 km col furgone", cantieri).km)
    }

    @Test
    fun seMancanoGliOrariChiede() {
        val d = RuleBasedDayParser.parse("ho rasato le pareti", cantieri)
        assertNull(d.start)
        assertTrue(d.questions.isNotEmpty())
    }

    @Test
    fun rispostaDelModelloConFenceEVirgolaInPiu() {
        val risposta = """
            ```json
            {"tipo":"LAVORO","ingresso":"8:00","uscita":"17.00",
             "pause":[{"da":"12:00","a":"13:00"}],
             "cantieri":[{"nome":"viale sarca","ore":"4","lavoro":"rasatura"},{"nome":"Nuovo Box","ore":4,"lavoro":null},],
             "lavoro":"rasatura pareti","materiali":"stucco","spostamenti":[{"da":"Milano","a":"Rivoli","partenza":"13:00","arrivo":"13:40"}],
             "km":"35","note":null,"domande":["Quanti sacchi di stucco?"]}
            ```
        """.trimIndent()
        val d = DictationResponseParser.parse(risposta, cantieri)
        assertNotNull(d)
        d!!
        assertEquals(LocalTime.of(8, 0), d.start)
        assertEquals(LocalTime.of(17, 0), d.end)
        assertEquals(2, d.sites.size)
        assertEquals(1L, d.sites[0].siteId)
        assertEquals(240, d.sites[0].minutes)
        assertNull(d.sites[1].siteId)
        assertEquals("Nuovo Box", d.sites[1].name)
        assertEquals(35, d.km)
        assertEquals(1, d.trips.size)
        assertEquals(LocalTime.of(13, 40), d.trips[0].arrive)
        assertEquals(1, d.questions.size)
    }

    @Test
    fun rispostaSenzaJsonNonSiRompe() {
        assertNull(DictationResponseParser.parse("Mi dispiace, non ho capito.", cantieri))
    }

    @Test
    fun unioneModelloERegole() {
        val modello = DictationDraft(description = "rasatura")
        val regole = DictationDraft(start = LocalTime.of(8, 0), end = LocalTime.of(17, 0))
        val unito = RuleBasedDayParser.merge(modello, regole)
        assertEquals(LocalTime.of(8, 0), unito.start)
        assertEquals("rasatura", unito.description)
    }

    @Test
    fun cantiereRiconosciutoDaPezzoDiNome() {
        assertEquals(2L, SiteNameMatcher.match("rivoli", cantieri)?.id)
        assertNull(SiteNameMatcher.match("capannone di Orbassano", cantieri))
    }
}
