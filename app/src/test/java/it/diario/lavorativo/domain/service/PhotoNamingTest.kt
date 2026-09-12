package it.diario.lavorativo.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class PhotoNamingTest {

    private val zone = ZoneOffset.UTC

    private fun instant(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Instant =
        LocalDate.of(y, mo, d).atTime(LocalTime.of(h, mi, s)).toInstant(ZoneOffset.UTC)

    @Test
    fun `il nome del file contiene data e ora leggibili`() {
        val name = PhotoNaming.fileName(instant(2026, 3, 9, 14, 32, 5), zone)
        assertEquals("IMG_20260309_143205.jpg", name)
    }

    @Test
    fun `due scatti nello stesso secondo non si sovrascrivono`() {
        val moment = instant(2026, 3, 9, 14, 32, 5)
        val primo = PhotoNaming.fileName(moment, zone)
        val secondo = PhotoNaming.fileName(moment, zone, suffix = 1)
        assertEquals("IMG_20260309_143205.jpg", primo)
        assertEquals("IMG_20260309_143205_1.jpg", secondo)
    }

    @Test
    fun `la riduzione e sempre una potenza di due`() {
        assertEquals(2, PhotoNaming.sampleSize(4000, 3000))
        assertEquals(4, PhotoNaming.sampleSize(8000, 6000))
        assertEquals(2, PhotoNaming.sampleSize(3200, 2400))
    }

    @Test
    fun `le immagini gia piccole non vengono ridotte`() {
        assertEquals(1, PhotoNaming.sampleSize(1600, 1200))
        assertEquals(1, PhotoNaming.sampleSize(800, 600))
    }

    @Test
    fun `dimensioni non valide non fanno esplodere il calcolo`() {
        assertEquals(1, PhotoNaming.sampleSize(0, 0))
        assertEquals(1, PhotoNaming.sampleSize(-10, 500))
    }

    @Test
    fun `il ridimensionamento mantiene le proporzioni`() {
        assertEquals(1600 to 1200, PhotoNaming.scaledSize(4000, 3000))
        assertEquals(1600 to 1200, PhotoNaming.scaledSize(3200, 2400))
    }

    @Test
    fun `le immagini piccole non vengono ingrandite`() {
        assertEquals(800 to 600, PhotoNaming.scaledSize(800, 600))
        assertEquals(1600 to 1200, PhotoNaming.scaledSize(1600, 1200))
    }

    @Test
    fun `il verticale viene gestito come l orizzontale`() {
        assertEquals(1200 to 1600, PhotoNaming.scaledSize(3000, 4000))
    }

    @Test
    fun `il lato lungo non supera mai il massimo`() {
        val (w, h) = PhotoNaming.scaledSize(6000, 1000)
        assertEquals(1600, w)
        assertTrue(h in 1..1600)
    }

    @Test
    fun `l orientamento exif diventa gradi di rotazione`() {
        assertEquals(0f, PhotoNaming.rotationDegrees(1), 0.01f)
        assertEquals(90f, PhotoNaming.rotationDegrees(6), 0.01f)
        assertEquals(180f, PhotoNaming.rotationDegrees(3), 0.01f)
        assertEquals(270f, PhotoNaming.rotationDegrees(8), 0.01f)
    }

    @Test
    fun `un orientamento sconosciuto non ruota l immagine`() {
        assertEquals(0f, PhotoNaming.rotationDegrees(0), 0.01f)
        assertEquals(0f, PhotoNaming.rotationDegrees(99), 0.01f)
    }
}
