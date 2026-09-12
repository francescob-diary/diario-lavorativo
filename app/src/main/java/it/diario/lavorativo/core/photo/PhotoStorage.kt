package it.diario.lavorativo.core.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import it.diario.lavorativo.domain.service.PhotoNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId

/**
 * Salvataggio e lettura dei file delle foto.
 *
 * Qui sta solo la COPIA RIDOTTA che serve all'app per le miniature e per il
 * PDF. L'originale a piena risoluzione resta in galleria, dove lo scrive
 * GalleryWriter: le foto vanno prima in galleria e poi nell'app.
 *
 * La copia viene ridotta a 1600 pixel di lato lungo. Tenere due volte gli
 * otto megapixel dell'originale riempirebbe il telefono senza motivo, e per
 * una miniatura o per una pagina stampata 1600 pixel bastano.
 */
class PhotoStorage(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, PhotoNaming.DIRECTORY).apply { mkdirs() }

    fun fileFor(fileName: String): File = File(directory, fileName)

    /**
     * Importa un'immagine da un Uri (galleria o scatto appena fatto),
     * ridimensiona, raddrizza e salva. Restituisce il file finale.
     */
    suspend fun importFrom(
        uri: Uri,
        takenAt: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): Result<StoredPhoto> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(uri) ?: error("Immagine non leggibile")
            val rotated = applyExifRotation(uri, bitmap)
            val target = uniqueFile(takenAt, zone)

            FileOutputStream(target).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, PhotoNaming.JPEG_QUALITY, out)
            }
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()

            StoredPhoto(fileName = target.name, sizeBytes = target.length())
        }
    }

    /**
     * Un nome libero. Due scatti nello stesso secondo sono possibili
     * (tocco doppio, raffica): in quel caso si aggiunge un suffisso invece di
     * sovrascrivere la foto precedente.
     */
    private fun uniqueFile(takenAt: Instant, zone: ZoneId): File {
        var suffix = 0
        while (suffix < 100) {
            val candidate = File(directory, PhotoNaming.fileName(takenAt, zone, suffix))
            if (!candidate.exists()) return candidate
            suffix++
        }
        return File(directory, PhotoNaming.fileName(takenAt, zone, System.nanoTime().toInt()))
    }

    /** Decodifica riducendo in memoria, per non far saltare l'app sui file grandi. */
    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoNaming.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val (w, h) = PhotoNaming.scaledSize(decoded.width, decoded.height)
        if (w == decoded.width && h == decoded.height) return decoded

        val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /**
     * Raddrizza secondo l'orientamento EXIF. Senza questo passaggio le foto
     * scattate in verticale compaiono coricate su un lato.
     */
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = PhotoNaming.rotationDegrees(orientation)
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Carica una foto gia' salvata, ridotta per la miniatura. */
    suspend fun load(fileName: String, targetPx: Int = PhotoNaming.MAX_SIDE_PX): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = fileFor(fileName)
            if (!file.exists()) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = PhotoNaming.sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }

    suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(fileName)
        file.exists() && file.delete()
    }

    /**
     * Cancella i file che non risultano piu' in nessun record. Puo' succedere
     * se l'app viene chiusa fra lo scatto e il salvataggio: senza questa
     * pulizia resterebbero a occupare spazio per sempre.
     */
    suspend fun deleteOrphans(known: Set<String>): Int = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.filter { it.isFile && it.name !in known }
            ?.count { it.delete() }
            ?: 0
    }
}

/** Esito del salvataggio di una foto. */
data class StoredPhoto(
    val fileName: String,
    val sizeBytes: Long
)
