package it.diario.lavorativo.domain.backup

import it.diario.lavorativo.core.backup.BackupArchive
import it.diario.lavorativo.core.json.JsonException
import it.diario.lavorativo.core.json.JsonParser
import it.diario.lavorativo.core.json.JsonWriter
import it.diario.lavorativo.core.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Prove del backup.
 *
 * E' l'unica parte dell'app dove uno sbaglio non da' fastidio ma cancella
 * il lavoro di mesi, quindi si prova tutto: andata e ritorno campo per
 * campo, file rovinati, file di altre versioni e archivi con percorsi
 * malevoli dentro.
 */
class BackupSerializerTest {

    private fun contenutoDiProva() = BackupContent(
        workDays = listOf(
            BackupWorkDay(
                id = 1L, date = 20150L,
                startTime = 1774000000123L, endTime = 1774032000456L,
                siteId = 5L, dayType = "LAVORO",
                place = "Cantiere via Roma", role = "Muratore",
                description = "Posa \"soglie\" in marmo",
                notes = "Nota su\ndue righe",
                standardMinutesOverride = null,
                travelKm = 137,
                createdAt = 1774000000000L, updatedAt = 1774032000000L
            ),
            BackupWorkDay(
                id = 2L, date = 20151L,
                startTime = null, endTime = null, siteId = null, dayType = "FERIE",
                place = null, role = null, description = null, notes = null,
                standardMinutesOverride = 480,
                travelKm = null,
                createdAt = 1774100000000L, updatedAt = 1774100000000L
            )
        ),
        breaks = listOf(
            BackupBreak(1L, 1L, 1774012000000L, 1774015600000L, "PRANZO", "mensa"),
            BackupBreak(2L, 1L, 1774020000000L, null, "PAUSA", null)
        ),
        sites = listOf(
            BackupSite(
                5L, "Rossi & figli", "Via Roma 12, scala \"B\"", "Citta' di Castello",
                "Geom. Bianchi", "MBR", "Luca", "333 1234567", 20100L, null,
                "Attenzione all'accesso", "ATTIVO", 45.123456, 11.987654, 150,
                1770000000000L, 1774000000000L
            )
        ),
        activities = listOf(
            BackupActivity(
                1L, 1L, "MARMO", "Posa <soglie>", 1774005000000L, null,
                "12 mq", null, 1774005000000L
            )
        ),
        events = listOf(
            BackupEvent(
                1L, 1L, "PROBLEMA", 1774010000000L, "Crepa sul muro",
                "Va segnalata al geometra", 45, "IMPORTANTE", true, 1774010000000L
            )
        ),
        communications = listOf(
            BackupCommunication(
                1L, 1L, "TELEFONATA", "RICEVUTA", 1774008000000L, "Geom. Rossi",
                "333 999", "Consegna", "Arrivano lunedi'", 10, true, 1774008000000L
            )
        ),
        photos = listOf(
            BackupPhoto(
                1L, 1L, null, null, "foto_1.jpg", "content://media/1",
                "Prima della posa", 1774006000000L, "FOTOCAMERA", 245678L, 1774006000000L
            )
        ),
        voiceNotes = listOf(
            BackupVoiceNote(1L, 1L, "nota_1.m4a", 42, "Da riascoltare", 1L, 1L)
        ),
        settings = BackupSettings("Dennis", 480, true, true, 1, 8, 20)
    )

    // ------------------------------------------------------------ JSON

    @Test
    fun `un istante in millisecondi non perde precisione`() {
        // Riscritto come 1.774E12 sarebbe un orario sbagliato al ritorno.
        val millis = 1774000000123L
        val testo = JsonWriter.write(jsonObject { put("t", millis) })

        assertFalse(testo.contains("E"))
        assertEquals(millis, JsonParser.parseObject(testo).long("t"))
    }

    @Test
    fun `virgolette, a capo e barre tornano identici`() {
        listOf(
            "Via Roma 12, scala \"B\"",
            "riga uno\nriga due",
            "percorso\\con\\barre",
            "citta' perche' cosi'",
            ""
        ).forEach { originale ->
            val riletto = JsonParser
                .parseObject(JsonWriter.write(jsonObject { put("v", originale) }))
                .string("v")
            assertEquals(originale, riletto)
        }
    }

    @Test
    fun `un valore nullo resta nullo e una chiave mancante non fa cadere niente`() {
        val o = JsonParser.parseObject(
            JsonWriter.write(jsonObject { put("assente", null as String?) })
        )
        assertNull(o.stringOrNull("assente"))
        assertNull(o.stringOrNull("maiVista"))
        assertEquals(7L, o.long("maiVista", 7L))
    }

    @Test
    fun `la forma compatta non va a capo`() {
        val doc = jsonObject {
            put("a", 1L)
            putArray("lista", listOf(jsonObject { put("b", 2L) }))
        }
        assertFalse(JsonWriter.write(doc, indent = false).contains("\n"))
    }

    @Test
    fun `un file rovinato viene segnalato con la posizione`() {
        listOf("{\"a\":1", "{\"a\" 1}", "{\"a\":1,}", "", "{}{}").forEach { rotto ->
            val errore = try {
                JsonParser.parse(rotto)
                null
            } catch (e: JsonException) {
                e
            }
            assertNotNull("doveva lamentarsi di: " + rotto, errore)
            assertTrue(errore!!.message.orEmpty().contains("posizione"))
        }
    }

    // ------------------------------------------------------------ backup

    @Test
    fun `il backup torna indietro identico campo per campo`() {
        val originale = contenutoDiProva()
        val esito = BackupSerializer.readContent(BackupSerializer.writeContent(originale))

        assertTrue(esito is BackupReadResult.Ok)
        assertEquals(originale, (esito as BackupReadResult.Ok).content)
    }

    @Test
    fun `i dettagli che si perdono facilmente sopravvivono`() {
        val esito = BackupSerializer
            .readContent(BackupSerializer.writeContent(contenutoDiProva()))
        val c = (esito as BackupReadResult.Ok).content

        assertEquals(1774000000123L, c.workDays[0].startTime)
        assertEquals(45.123456, c.sites[0].latitude!!, 0.0000001)
        assertEquals("Via Roma 12, scala \"B\"", c.sites[0].address)
        assertEquals("Nota su\ndue righe", c.workDays[0].notes)
        assertNull(c.workDays[1].startTime)
        assertNull(c.breaks[1].endTime)
    }

    @Test
    fun `il manifest si scrive e si rilegge uguale`() {
        val m = BackupManifest(
            formatVersion = 2, createdAt = 1774000000000L, appVersion = "1.0",
            databaseVersion = 6, workDays = 120, sites = 8, photos = 45, voiceNotes = 3,
            vehicles = 1, fuelStops = 44, includesMedia = true,
            firstDate = 20000L, lastDate = 20150L
        )
        assertEquals(m, BackupSerializer.readManifest(BackupSerializer.writeManifest(m)))
    }

    @Test
    fun `un manifest rovinato torna nullo invece di far cadere l'app`() {
        assertNull(BackupSerializer.readManifest("{rotto"))
    }

    @Test
    fun `un backup di una versione futura viene respinto`() {
        // Meglio dire di aggiornare l'app che leggere a meta' e perdere roba.
        val esito = BackupSerializer.readContent("{\"formatVersion\": 99, \"workDays\": []}")
        assertTrue(esito is BackupReadResult.TooNew)
    }

    @Test
    fun `un file che non e' un backup viene respinto`() {
        assertTrue(BackupSerializer.readContent("questo non e' un backup")
            is BackupReadResult.Damaged)
        assertTrue(BackupSerializer.readContent("{\"workDays\": []}")
            is BackupReadResult.Damaged)
    }

    @Test
    fun `un file troncato viene respinto`() {
        val testo = BackupSerializer.writeContent(contenutoDiProva())
        assertTrue(BackupSerializer.readContent(testo.substring(0, testo.length / 2))
            is BackupReadResult.Damaged)
    }

    @Test
    fun `una riga rovinata non fa cadere tutto il ripristino`() {
        // Su mille giornate, meglio recuperarne 999 che nessuna.
        val testo = """
            {
              "formatVersion": 1,
              "workDays": [
                {"id": 1, "date": 20150, "dayType": "LAVORO"},
                {"date": 20151, "dayType": "LAVORO"},
                {"id": 3, "dayType": "LAVORO"},
                {"id": 4, "date": 20153, "dayType": "FERIE"}
              ],
              "sites": [{"id": 9, "name": "Buono"}, {"id": 10}]
            }
        """.trimIndent()

        val esito = BackupSerializer.readContent(testo)
        assertTrue(esito is BackupReadResult.Ok)

        val c = (esito as BackupReadResult.Ok).content
        assertEquals(listOf(1L, 4L), c.workDays.map { it.id })
        assertEquals(1, c.sites.size)
        assertEquals(3, BackupSerializer.countSkipped(testo, c))
    }

    // ------------------------------------------------------------ archivio

    private fun tempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "diario_test_" + name)
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    @Test
    fun `l'archivio riporta indietro dati e file identici`() {
        val sorgente = tempDir("sorgente")
        File(sorgente, "foto_1.jpg").writeBytes(ByteArray(5000) { (it % 251).toByte() })
        File(sorgente, "nota_1.m4a").writeBytes(ByteArray(2000) { 3 })

        val contenuto = contenutoDiProva()
        val manifest = BackupManifest(
            formatVersion = 2, createdAt = 1L, appVersion = "1.0", databaseVersion = 6,
            workDays = 2, sites = 1, photos = 1, voiceNotes = 1,
            includesMedia = true, firstDate = 20150L, lastDate = 20151L
        )

        val out = ByteArrayOutputStream()
        BackupArchive.write(
            output = out,
            manifest = manifest,
            content = contenuto,
            photos = listOf(File(sorgente, "foto_1.jpg")),
            voiceNotes = listOf(File(sorgente, "nota_1.m4a"))
        )
        val zip = out.toByteArray()

        val destFoto = tempDir("foto")
        val destNote = tempDir("note")
        val esito = BackupArchive.read(ByteArrayInputStream(zip), destFoto, destNote, true)

        assertTrue(esito is BackupReadResult.Ok)
        assertEquals(contenuto, (esito as BackupReadResult.Ok).content)
        assertEquals(manifest, esito.manifest)

        assertTrue(
            File(sorgente, "foto_1.jpg").readBytes()
                .contentEquals(File(destFoto, "foto_1.jpg").readBytes())
        )
        assertTrue(
            File(sorgente, "nota_1.m4a").readBytes()
                .contentEquals(File(destNote, "nota_1.m4a").readBytes())
        )
    }

    @Test
    fun `il manifest si legge senza tirare fuori le foto`() {
        val manifest = BackupManifest(
            formatVersion = 2, createdAt = 1L, appVersion = "1.0", databaseVersion = 6,
            workDays = 2, sites = 1, photos = 1, voiceNotes = 1,
            includesMedia = true, firstDate = 20150L, lastDate = 20151L
        )
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, contenutoDiProva())

        assertEquals(manifest, BackupArchive.readManifest(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun `uno zip che non e' un backup viene respinto spiegando perche`() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("qualcosa.txt"))
            z.write("ciao".toByteArray())
            z.closeEntry()
        }

        val esito = BackupArchive.read(
            ByteArrayInputStream(out.toByteArray()),
            tempDir("a"), tempDir("b"), false
        )
        assertTrue(esito is BackupReadResult.Damaged)
        assertTrue((esito as BackupReadResult.Damaged).reason.contains("dati.json"))
    }

    @Test
    fun `un archivio con percorsi malevoli non scrive fuori dalla cartella`() {
        // Uno zip puo' contenere nomi tipo "foto/../../scappato": estratti
        // alla lettera finirebbero fuori dallo spazio dell'app.
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            z.write("{\"formatVersion\":1}".toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("dati.json"))
            z.write("{\"formatVersion\":1}".toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("foto/../../../scappato.txt"))
            z.write("non dovrei stare qui".toByteArray())
            z.closeEntry()
        }

        val dentro = tempDir("sicura")
        BackupArchive.read(
            ByteArrayInputStream(out.toByteArray()), dentro, tempDir("c"), true
        )

        assertFalse(File(dentro.parentFile, "scappato.txt").exists())
        assertTrue(File(dentro, "scappato.txt").exists())
    }
}
