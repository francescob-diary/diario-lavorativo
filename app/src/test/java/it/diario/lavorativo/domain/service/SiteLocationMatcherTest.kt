package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.GeoPoint
import it.diario.lavorativo.domain.model.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test del riconoscimento del cantiere tramite posizione.
 * Coordinate reali di riferimento: Duomo di Milano.
 */
class SiteLocationMatcherTest {

    private val matcher = SiteLocationMatcher()

    private val duomo = GeoPoint(45.4642, 9.1900)

    private fun site(
        id: Long,
        name: String,
        lat: Double?,
        lon: Double?,
        radius: Int = 150
    ) = Site(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        radiusMeters = radius
    )

    @Test
    fun `distanza fra due punti noti e plausibile`() {
        // Duomo -> Castello Sforzesco: circa 1,1 km in linea d'aria.
        val castello = GeoPoint(45.4707, 9.1795)
        val d = duomo.distanceMetersTo(castello)
        assertTrue("distanza inattesa: " + d, d in 950.0..1250.0)
    }

    @Test
    fun `distanza da un punto a se stesso e zero`() {
        assertEquals(0.0, duomo.distanceMetersTo(duomo), 0.001)
    }

    @Test
    fun `un solo cantiere nel raggio da esito certo`() {
        val sites = listOf(
            site(1, "Cantiere Duomo", 45.4642, 9.1900),
            site(2, "Cantiere Lontano", 45.6000, 9.5000)
        )
        val match = matcher.match(duomo, sites)
        assertTrue(match is SiteMatch.Confident)
        assertEquals(1L, (match as SiteMatch.Confident).site.id)
    }

    @Test
    fun `due cantieri sovrapposti danno esito ambiguo ordinato per distanza`() {
        val sites = listOf(
            site(1, "Lotto A", 45.4645, 9.1905, radius = 300),
            site(2, "Lotto B", 45.4642, 9.1900, radius = 300)
        )
        val match = matcher.match(duomo, sites)
        assertTrue(match is SiteMatch.Ambiguous)
        val candidates = (match as SiteMatch.Ambiguous).candidates
        assertEquals(2, candidates.size)
        assertEquals(2L, candidates.first().site.id)
        assertTrue(candidates[0].distanceMeters <= candidates[1].distanceMeters)
    }

    @Test
    fun `fuori raggio restituisce il cantiere piu vicino`() {
        val sites = listOf(
            site(1, "Vicino", 45.4700, 9.1900, radius = 100),
            site(2, "Lontanissimo", 45.9000, 9.9000, radius = 100)
        )
        val match = matcher.match(duomo, sites)
        assertTrue(match is SiteMatch.None)
        assertEquals("Vicino", (match as SiteMatch.None).nearest?.site?.name)
    }

    @Test
    fun `la precisione del sensore allarga il raggio utile`() {
        // 250 m dal punto salvato, raggio 150: fuori. Con precisione 200 m rientra.
        val sites = listOf(site(1, "Cantiere", 45.4664, 9.1900, radius = 150))
        val precise = matcher.match(duomo.copy(accuracyMeters = 5f), sites)
        assertTrue(precise is SiteMatch.None)

        val vague = matcher.match(duomo.copy(accuracyMeters = 200f), sites)
        assertTrue(vague is SiteMatch.Confident)
    }

    @Test
    fun `cantieri senza coordinate vengono ignorati`() {
        val sites = listOf(
            site(1, "Senza posizione", null, null),
            site(2, "Solo latitudine", 45.4642, null)
        )
        val match = matcher.match(duomo, sites)
        assertTrue(match is SiteMatch.None)
        assertEquals(null, (match as SiteMatch.None).nearest)
    }

    @Test
    fun `posizione non valida non produce abbinamenti`() {
        val sites = listOf(site(1, "Cantiere", 45.4642, 9.1900))
        val match = matcher.match(GeoPoint(0.0, 0.0), sites)
        assertTrue(match is SiteMatch.None)
    }

    @Test
    fun `chilometri percorsi ignorano la deriva del gps da fermo`() {
        // Cinque letture entro pochi metri: fermo, quindi zero chilometri.
        val drift = listOf(
            GeoPoint(45.4642, 9.1900),
            GeoPoint(45.46421, 9.19001),
            GeoPoint(45.46420, 9.19002),
            GeoPoint(45.46422, 9.19000)
        )
        assertEquals(0.0, matcher.travelledKm(drift), 0.001)
    }

    @Test
    fun `chilometri percorsi sommano gli spostamenti reali`() {
        val path = listOf(
            GeoPoint(45.4642, 9.1900),
            GeoPoint(45.4707, 9.1795),
            GeoPoint(45.4642, 9.1900)
        )
        val km = matcher.travelledKm(path)
        assertTrue("km inattesi: " + km, km in 2.0..2.4)
    }

    @Test
    fun `un solo punto non produce chilometri`() {
        assertEquals(0.0, matcher.travelledKm(listOf(duomo)), 0.001)
    }
}
