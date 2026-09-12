package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.EventType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.StatsRange
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class StatisticsCalculatorTest {

    private val calculator = WorkTimeCalculator()
    private val stats = StatisticsCalculator(calculator, PeriodSummarizer(calculator))
    private val standard = 8 * 60

    /** Ci si mette dopo la fine dei periodi provati, cosi' le giornate sono tutte chiuse. */
    private val now: Instant = LocalDate.of(2026, 4, 1)
        .atTime(9, 0).toInstant(ZoneOffset.UTC)

    /** Mercoledi 4 marzo 2026: marzo e' un mese comodo, tocca sei settimane. */
    private val mercoledi = LocalDate.of(2026, 3, 4)

    private fun at(date: LocalDate, hour: Int): Instant =
        date.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC)

    private fun giorno(
        date: LocalDate,
        dalle: Int = 8,
        alle: Int? = 17,
        tipo: DayType = DayType.LAVORO,
        cantiere: String? = "Rossi"
    ): WorkDay = WorkDay(
        id = date.toEpochDay(),
        date = date,
        startTime = at(date, dalle),
        endTime = alle?.let { at(date, it) },
        site = cantiere?.let { Site(id = 1L, name = it) },
        dayType = tipo
    )

    /** Lunedi 2 - venerdi 6 marzo, 8-17: nove ore lorde al giorno. */
    private fun settimanaPiena(): List<WorkDay> =
        (0L..4L).map { giorno(LocalDate.of(2026, 3, 2).plusDays(it)) }

    // ---------------------------------------------------------------- confini

    @Test
    fun `la settimana va da lunedi a domenica`() {
        assertEquals(
            LocalDate.of(2026, 3, 2),
            stats.rangeStart(StatsRange.SETTIMANA, mercoledi)
        )
        assertEquals(
            LocalDate.of(2026, 3, 8),
            stats.rangeEnd(StatsRange.SETTIMANA, mercoledi)
        )
    }

    @Test
    fun `il mese va dal primo all'ultimo giorno`() {
        assertEquals(LocalDate.of(2026, 3, 1), stats.rangeStart(StatsRange.MESE, mercoledi))
        assertEquals(LocalDate.of(2026, 3, 31), stats.rangeEnd(StatsRange.MESE, mercoledi))
    }

    @Test
    fun `l'anno va da gennaio a dicembre`() {
        assertEquals(LocalDate.of(2026, 1, 1), stats.rangeStart(StatsRange.ANNO, mercoledi))
        assertEquals(LocalDate.of(2026, 12, 31), stats.rangeEnd(StatsRange.ANNO, mercoledi))
    }

    @Test
    fun `febbraio bisestile finisce il ventinove`() {
        assertEquals(
            LocalDate.of(2028, 2, 29),
            stats.rangeEnd(StatsRange.MESE, LocalDate.of(2028, 2, 10))
        )
    }

    @Test
    fun `il periodo precedente arretra della giusta misura`() {
        assertEquals(
            LocalDate.of(2026, 2, 1),
            stats.previousStart(StatsRange.MESE, LocalDate.of(2026, 3, 1))
        )
        assertEquals(
            LocalDate.of(2025, 1, 1),
            stats.previousStart(StatsRange.ANNO, LocalDate.of(2026, 1, 1))
        )
        assertEquals(
            LocalDate.of(2026, 2, 23),
            stats.previousStart(StatsRange.SETTIMANA, LocalDate.of(2026, 3, 2))
        )
    }

    // ---------------------------------------------------------------- totali

    @Test
    fun `i totali del mese contano giorni ore e straordinario`() {
        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = settimanaPiena(),
            now = now,
            standardMinutes = standard
        )

        assertEquals(5, summary.totals.workedDays)
        assertEquals(Duration.ofHours(45), summary.totals.net)
        assertEquals(Duration.ofHours(5), summary.totals.overtime)
        assertTrue(summary.hasData)
    }

    @Test
    fun `un periodo senza giornate non ha dati e non divide per zero`() {
        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = emptyList(),
            now = now,
            standardMinutes = standard
        )

        assertFalse(summary.hasData)
        assertEquals(Duration.ZERO, summary.trendPeak)
        assertEquals(Duration.ZERO, summary.totals.averageNet)
        assertTrue(summary.sites.isEmpty())
    }

    // ---------------------------------------------------------------- grafico

    @Test
    fun `la settimana ha sette barre con i nomi dei giorni`() {
        val summary = stats.build(
            range = StatsRange.SETTIMANA,
            reference = mercoledi,
            days = settimanaPiena(),
            now = now,
            standardMinutes = standard
        )

        assertEquals(7, summary.trend.size)
        assertEquals("Lun", summary.trend[0].label)
        assertEquals("Dom", summary.trend[6].label)
        assertEquals(Duration.ofHours(9), summary.trend[0].net)
        assertFalse(summary.trend[5].hasWork)
    }

    @Test
    fun `marzo tocca sei settimane e le ore cadono in quella giusta`() {
        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = settimanaPiena(),
            now = now,
            standardMinutes = standard
        )

        // La prima settimana parte lunedi 23 febbraio, l'ultima finisce domenica 5 aprile.
        assertEquals(6, summary.trend.size)
        assertEquals("S1", summary.trend[0].label)
        assertEquals(Duration.ZERO, summary.trend[0].net)
        assertEquals(Duration.ofHours(45), summary.trend[1].net)
        assertEquals(Duration.ZERO, summary.trend[2].net)
        assertEquals(Duration.ofHours(45), summary.trendPeak)
    }

    @Test
    fun `l'anno ha dodici barre coi mesi`() {
        val summary = stats.build(
            range = StatsRange.ANNO,
            reference = mercoledi,
            days = settimanaPiena(),
            now = now,
            standardMinutes = standard
        )

        assertEquals(12, summary.trend.size)
        assertEquals("Gen", summary.trend[0].label)
        assertEquals("Dic", summary.trend[11].label)
        assertEquals(Duration.ofHours(45), summary.trend[2].net)
        assertEquals(Duration.ZERO, summary.trend[0].net)
    }

    // ---------------------------------------------------------------- cantieri

    @Test
    fun `i cantieri sono ordinati dal piu' impegnativo`() {
        val misto = listOf(
            giorno(LocalDate.of(2026, 3, 2), cantiere = "Rossi"),
            giorno(LocalDate.of(2026, 3, 3), cantiere = "Bianchi"),
            giorno(LocalDate.of(2026, 3, 4), cantiere = "Bianchi")
        )

        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = misto,
            now = now,
            standardMinutes = standard
        )

        assertEquals(2, summary.sites.size)
        assertEquals("Bianchi", summary.sites[0].siteName)
        assertEquals(Duration.ofHours(18), summary.sites[0].net)
        assertEquals(2, summary.sites[0].days)
        assertEquals(Duration.ofHours(9), summary.sites[1].net)
        assertEquals("Bianchi", summary.topSite?.siteName)
    }

    @Test
    fun `una giornata senza cantiere non sparisce dal conto`() {
        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = listOf(giorno(LocalDate.of(2026, 3, 2), cantiere = null)),
            now = now,
            standardMinutes = standard
        )

        assertEquals("Senza cantiere", summary.sites[0].siteName)
        assertEquals(Duration.ofHours(9), summary.sites[0].net)
    }

    // ---------------------------------------------------------------- confronto

    @Test
    fun `il confronto col periodo precedente dice quanto si e' lavorato in piu'`() {
        val precedente = (0L..3L).map { giorno(LocalDate.of(2026, 2, 2).plusDays(it)) }

        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = settimanaPiena(),
            previousDays = precedente,
            now = now,
            standardMinutes = standard
        )

        assertEquals(Duration.ofHours(36), summary.previousNet)
        assertEquals(Duration.ofHours(9), summary.difference)
        assertTrue(summary.hasComparison)
    }

    @Test
    fun `se si e' lavorato meno la differenza e' negativa`() {
        val precedente = (0L..3L).map { giorno(LocalDate.of(2026, 2, 2).plusDays(it)) }

        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = precedente,
            previousDays = settimanaPiena(),
            now = now,
            standardMinutes = standard
        )

        assertEquals(Duration.ofHours(-9), summary.difference)
    }

    @Test
    fun `senza periodo precedente non si mostra il confronto`() {
        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = settimanaPiena(),
            now = now,
            standardMinutes = standard
        )

        assertFalse(summary.hasComparison)
    }

    // ------------------------------------------------------ lavorazioni ed eventi

    @Test
    fun `le lavorazioni sono contate e ordinate per frequenza`() {
        val attivita = listOf(
            WorkActivity(id = 1, workDayId = 1, category = ActivityCategory.MARMO, description = "a"),
            WorkActivity(id = 2, workDayId = 1, category = ActivityCategory.MARMO, description = "b"),
            WorkActivity(id = 3, workDayId = 2, category = ActivityCategory.MARMO, description = "c"),
            WorkActivity(id = 4, workDayId = 2, category = ActivityCategory.PULIZIA, description = "d"),
            WorkActivity(id = 5, workDayId = 3, category = ActivityCategory.INTONACO, description = "e")
        )

        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = settimanaPiena(),
            activities = attivita,
            now = now,
            standardMinutes = standard
        )

        assertEquals(3, summary.categories.size)
        assertEquals(ActivityCategory.MARMO, summary.categories[0].category)
        assertEquals(3, summary.categories[0].count)
    }

    @Test
    fun `gli eventi contano le giornate toccate e le questioni aperte`() {
        val eventi = listOf(
            WorkEvent(id = 1, workDayId = 10, type = EventType.PROBLEMA, time = now, title = "x", unresolved = true),
            WorkEvent(id = 2, workDayId = 10, type = EventType.RITARDO, time = now, title = "y"),
            WorkEvent(id = 3, workDayId = 11, type = EventType.MALTEMPO, time = now, title = "z", unresolved = true)
        )

        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = settimanaPiena(),
            events = eventi,
            now = now,
            standardMinutes = standard
        )

        assertEquals(2, summary.eventDays)
        assertEquals(2, summary.unresolvedEvents)
    }

    @Test
    fun `le ferie si contano ma non fanno ore ne' finiscono nei cantieri`() {
        val summary = stats.build(
            range = StatsRange.MESE,
            reference = mercoledi,
            days = listOf(giorno(LocalDate.of(2026, 3, 2), tipo = DayType.FERIE)),
            now = now,
            standardMinutes = standard
        )

        assertEquals(Duration.ZERO, summary.totals.net)
        assertTrue(summary.sites.isEmpty())
        assertEquals(1, summary.totals.absenceDays[DayType.FERIE])
    }
}
