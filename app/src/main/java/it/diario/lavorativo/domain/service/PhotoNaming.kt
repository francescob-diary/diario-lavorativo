package it.diario.lavorativo.domain.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Regole sui file delle foto, tenute fuori dal codice Android per poterle
 * verificare davvero con i test.
 *
 * Il nome del file contiene la data leggibile: se un domani si apre la
 * cartella da un computer si capisce subito a quando si riferisce una foto,
 * senza dover interrogare il database.
 */
object PhotoNaming {

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    const val EXTENSION = ".jpg"
    const val DIRECTORY = "foto"

    /** Massimo lato lungo dell'immagine salvata. */
    const val MAX_SIDE_PX = 1600

    /** Qualita' JPEG: sopra 85 il file cresce molto senza guadagno visibile. */
    const val JPEG_QUALITY = 85

    fun fileName(takenAt: Instant, zone: ZoneId, suffix: Int = 0): String {
        val stamp = STAMP.format(takenAt.atZone(zone))
        val extra = if (suffix > 0) "_" + suffix.toString() else ""
        return "IMG_" + stamp + extra + EXTENSION
    }

    /**
     * Fattore di riduzione in potenze di due, come richiesto dal decodificatore
     * di Android. Serve a caricare in memoria un'immagine grande senza far
     * saltare l'app: un telefono da 50 megapixel produce file che, aperti
     * per intero, occuperebbero centinaia di megabyte.
     */
    fun sampleSize(width: Int, height: Int, targetPx: Int = MAX_SIDE_PX): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= targetPx) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Dimensioni finali mantenendo le proporzioni, con il lato lungo entro
     * [maxSide]. Le immagini gia' piccole non vengono ingrandite.
     */
    fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val ratio = maxSide.toDouble() / longest.toDouble()
        val w = (width * ratio).toInt().coerceAtLeast(1)
        val h = (height * ratio).toInt().coerceAtLeast(1)
        return w to h
    }

    /** Gradi di rotazione corrispondenti al valore di orientamento EXIF. */
    fun rotationDegrees(exifOrientation: Int): Float = when (exifOrientation) {
        6 -> 90f
        3 -> 180f
        8 -> 270f
        else -> 0f
    }

    /** Etichetta leggibile per raggruppare le foto per giorno. */
    fun dayLabel(date: LocalDate): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy").format(date)
}
