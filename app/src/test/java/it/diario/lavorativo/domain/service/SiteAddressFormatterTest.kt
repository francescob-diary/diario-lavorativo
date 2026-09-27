package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteAddressFormatterTest {

    private fun site(address: String? = null, city: String? = null, name: String = "Cantiere Rossi") =
        Site(name = name, address = address, city = city, client = "Rossi Srl")

    @Test
    fun civicoAttaccatoVieneStaccatoConLaVirgola() {
        assertEquals("Viale Sarca, 85, Milano", SiteAddressFormatter.forReport(site("Viale Sarca 85", "Milano")))
    }

    @Test
    fun indirizzoGiaConVirgolaResta() {
        assertEquals("Viale Sarca, 85, Milano", SiteAddressFormatter.forReport(site("Viale Sarca, 85", "Milano")))
    }

    @Test
    fun cittaFacoltativa() {
        assertEquals("Via Roma, 12/A", SiteAddressFormatter.forReport(site("Via Roma 12/A")))
    }

    @Test
    fun senzaCivicoNessunaVirgolaInPiu() {
        assertEquals("Corso Francia, Torino", SiteAddressFormatter.forReport(site("Corso Francia", "Torino")))
    }

    @Test
    fun numeroNelNomeDellaViaNonSiConfondeColCivico() {
        assertEquals("Via 20 Settembre, 5", SiteAddressFormatter.forReport(site("Via 20 Settembre 5")))
    }

    @Test
    fun senzaIndirizzoSiUsaIlNomeDelCantiere() {
        assertEquals("Cantiere Rossi", SiteAddressFormatter.forReport(site(null, "Milano")))
    }

    @Test
    fun nienteVirgoleDoppieOFinali() {
        assertEquals("Viale Sarca, 85, Milano", SiteAddressFormatter.forReport(site(" Viale Sarca ,, 85, ", " Milano ")))
    }

    @Test
    fun nessunCantiereNessunaEtichetta() {
        assertNull(SiteAddressFormatter.forReport(null))
    }
}
