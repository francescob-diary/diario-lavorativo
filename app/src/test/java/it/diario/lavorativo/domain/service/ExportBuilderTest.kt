package it.diario.lavorativo.domain.service

import it.diario.lavorativo.core.export.CsvWriter
import it.diario.lavorativo.core.export.XlsxWriter
import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.CommunicationDirection
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.EventType
import it.diario.lavorativo.domain.model.ExportOptions
import it.diario.lavorativo.domain.model.ExportScope
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class ExportBuilderTest {

    private val calculator = WorkTimeCalculator()
    private val builder = ExportBuilder(calculator, PeriodSummarizer(calculator))
    private val standard = 8 * 60
    private val zone = ZoneOffset.UTC

    /** Primo aprile: cosi' marzo e' tutto passato e le giornate sono chiuse. */
    private val now: Instant =
        LocalDate.of(2026, 4, 1).atTime(9, 0).toInstant(ZoneOffset.UTC)

    private val riferimento = LocalDate.of(2026, 3, 4)

    private fun at(d: LocalDate, h: Int): Instant =
        d.atTime(LocalTime.of(h, 0)).toInstant(ZoneOffset.UTC)

    private fun giorno(
        d: LocalDate,
        dalle: Int = 8,
        alle: Int? = 17,
        tipo: DayType = DayType.LAVORO,
        cantiere: String? = "Rossi",
        note: String? = null
    ): WorkDay = WorkDay(
        id = d.toEpochDay(),
        date = d,
        startTime = at(d, dalle),
        endTime = alle?.let { at(d, it) },
        site = cantiere?.let { Site(id = 1L, name = it) },
        dayType = tipo,
        notes = note
    )

    /** Lun 2 e mar 3 chiusi, mer 4 ferie, gio 5 mai terminato. */
    private fun settimana(): List<WorkDay> = listOf(
        giorno(LocalDate.of(2026, 3, 2)),
        giorno(LocalDate.of(2026, 3, 3), cantiere = "Bianchi & figli", note = "Nota con ; dentro"),
        giorno(LocalDate.of(2026, 3, 4), tipo = DayType.FERIE, cantiere = null),
        giorno(LocalDate.of(2026, 3, 5), alle = null)
    )

    private fun documento(options: ExportOptions) = builder.build(
        options = options,
        from = LocalDate.of(2026, 3, 1),
        to = LocalDate.of(2026, 3, 31),
        days = settimana(),
        activities = mapOf(
            LocalDate.of(2026, 3, 2).toEpochDay() to listOf(
                WorkActivity(
                    id = 1, workDayId = 1, category = ActivityCategory.MARMO,
                    description = "Posa <soglie> in marmo", quantity = "12 mq"
                )
            )
        ),
        events = mapOf(
            LocalDate.of(2026, 3, 3).toEpochDay() to listOf(
                WorkEvent(
                    id = 1, workDayId = 1, type = EventType.PROBLEMA,
                    time = at(LocalDate.of(2026, 3, 3), 10),
                    title = "Crepa sul muro", unresolved = true,
                    severity = EventSeverity.IMPORTANTE
                )
            )
        ),
        communications = mapOf(
            LocalDate.of(2026, 3, 2).toEpochDay() to listOf(
                Communication(
                    id = 1, workDayId = 1, channel = CommunicationChannel.TELEFONATA,
                    direction = CommunicationDirection.RICEVUTA,
                    time = at(LocalDate.of(2026, 3, 2), 9),
                    contactName = "Geom. Rossi", subject = "Consegna materiale"
                )
            )
        ),
        userName = "Dennis",
        now = now,
        standardMinutes = standard,
        zone = zone
    )

    // ---------------------------------------------------------------- periodo

    @Test
    fun `la settimana parte dal lunedi`() {
        assertEquals(
            LocalDate.of(2026, 3, 2),
            builder.rangeStart(ExportScope.SETTIMANA, riferimento, null)
        )
    }

    @Test
    fun `il mese copre dal primo all'ultimo giorno`() {
        assertEquals(
            LocalDate.of(2026, 3, 1),
            builder.rangeStart(ExportScope.MESE, riferimento, null)
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            builder.rangeEnd(ExportScope.MESE, riferimento, null)
        )
    }

    @Test
    fun `tutto parte dalla prima giornata registrata`() {
        assertEquals(
            LocalDate.of(2024, 5, 6),
            builder.rangeStart(ExportScope.TUTTO, riferimento, LocalDate.of(2024, 5, 6))
        )
    }

    @Test
    fun `senza nessuna giornata tutto ripiega sull'anno corrente`() {
        assertEquals(
            LocalDate.of(2026, 1, 1),
            builder.rangeStart(ExportScope.TUTTO, riferimento, null)
        )
    }

    @Test
    fun `i nomi dei file si mettono in ordine da soli`() {
        assertEquals(
            "diario-2026-03",
            builder.fileName(ExportScope.MESE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        )
        assertEquals(
            "diario-2026",
            builder.fileName(ExportScope.ANNO, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        )
    }

    @Test
    fun `l'etichetta del periodo riconosce mese e anno interi`() {
        assertEquals(
            "Marzo 2026",
            builder.periodLabel(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        )
        assertEquals(
            "Anno 2026",
            builder.periodLabel(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        )
        assertEquals(
            "02/03/2026 - 08/03/2026",
            builder.periodLabel(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 8))
        )
    }

    // ---------------------------------------------------------------- contenuto

    @Test
    fun `le comunicazioni restano fuori se non si chiedono`() {
        val options = ExportOptions(scope = ExportScope.MESE)
        assertFalse(options.includeCommunications)

        val doc = documento(options)
        assertEquals(4, doc.sheets.size)
        assertTrue(doc.sheets.none { it.name == "Comunicazioni" })
        assertFalse(CsvWriter.write(doc).contains("Geom. Rossi"))
    }

    @Test
    fun `accendendole le comunicazioni compaiono`() {
        val doc = documento(
            ExportOptions(scope = ExportScope.MESE, includeCommunications = true)
        )
        assertEquals(5, doc.sheets.size)
        assertEquals(
            "Geom. Rossi",
            doc.sheets.first { it.name == "Comunicazioni" }.rows[0][4]
        )
    }

    @Test
    fun `una giornata mai chiusa non gonfia i totali`() {
        // Esportando marzo ad aprile, la giornata aperta il 5 avrebbe
        // accumulato ventisette giorni di ore: il foglio avrebbe detto 667h.
        val doc = documento(ExportOptions(scope = ExportScope.MESE))
        val voci = doc.sheets.first { it.name == "Riepilogo" }
            .rows.associate { it[0] to it[1] }

        assertEquals("18h", voci["Ore nette"])
        assertEquals("2", voci["Giorni lavorati"])
        assertEquals("1", voci["Giornate senza orario di uscita"])
    }

    @Test
    fun `la giornata aperta non inventa un orario di uscita`() {
        val giornate = documento(ExportOptions(scope = ExportScope.MESE))
            .sheets.first { it.name == "Giornate" }

        assertEquals("in corso", giornate.rows[3][5])
        assertEquals("", giornate.rows[3][8])
    }

    @Test
    fun `le ferie non mostrano ore, come nelle statistiche`() {
        val giornate = documento(ExportOptions(scope = ExportScope.MESE))
            .sheets.first { it.name == "Giornate" }

        assertEquals("Ferie", giornate.rows[2][2])
        assertEquals("", giornate.rows[2][4])
        assertEquals("", giornate.rows[2][5])
        assertEquals("", giornate.rows[2][8])
        assertEquals("", giornate.rows[2][9])
    }

    @Test
    fun `le ferie si contano comunque nel riepilogo`() {
        val voci = documento(ExportOptions(scope = ExportScope.MESE))
            .sheets.first { it.name == "Riepilogo" }
            .rows.associate { it[0] to it[1] }

        assertEquals("1", voci["Giorni di ferie"])
        assertEquals("1", voci["Questioni ancora aperte"])
    }

    // ---------------------------------------------------------------- CSV

    @Test
    fun `il csv si apre in Excel italiano`() {
        val csv = CsvWriter.write(documento(ExportOptions(scope = ExportScope.MESE)))

        // Senza BOM le lettere accentate escono a scacchi, con la virgola
        // al posto del punto e virgola finisce tutto in una cella sola.
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Data;Giorno;Tipo"))
    }

    @Test
    fun `una cella col punto e virgola non spacca la riga`() {
        val csv = CsvWriter.write(documento(ExportOptions(scope = ExportScope.MESE)))
        assertTrue(csv.contains("\"Nota con ; dentro\""))
    }

    @Test
    fun `le virgolette dentro una cella si raddoppiano`() {
        assertEquals("\"dice \"\"si\"\"\"", CsvWriter.escape("dice \"si\""))
        assertEquals("Rossi", CsvWriter.escape("Rossi"))
    }

    // ---------------------------------------------------------------- XLSX

    @Test
    fun `le lettere di colonna reggono oltre la Z`() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AZ", XlsxWriter.columnName(51))
        assertEquals("BA", XlsxWriter.columnName(52))
    }

    @Test
    fun `i caratteri riservati non rompono il foglio`() {
        // "Rossi & figli" senza questo renderebbe il file illeggibile.
        assertEquals("Rossi &amp; figli", XlsxWriter.escapeXml("Rossi & figli"))
        assertEquals("&lt;soglie&gt;", XlsxWriter.escapeXml("<soglie>"))
    }

    @Test
    fun `i nomi delle schede rispettano i limiti di Excel`() {
        assertEquals("Foglio uno", XlsxWriter.escapeSheetName("Foglio/uno"))
        assertEquals(31, XlsxWriter.escapeSheetName("x".repeat(50)).length)
    }

    @Test
    fun `l'xlsx prodotto e' un vero archivio zip`() {
        val bytes = XlsxWriter.write(documento(ExportOptions(scope = ExportScope.MESE)))

        assertTrue(bytes.size > 500)
        assertEquals(0x50.toByte(), bytes[0])
        assertEquals(0x4B.toByte(), bytes[1])
    }
}
