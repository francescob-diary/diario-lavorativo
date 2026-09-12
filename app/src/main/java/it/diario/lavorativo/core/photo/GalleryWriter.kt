package it.diario.lavorativo.core.photo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Scrive le foto nella galleria del telefono, nell'album "Diario Lavorativo".
 *
 * Le foto vanno prima in galleria e poi nell'app: cosi' restano anche se
 * l'app viene disinstallata o si cambia telefono, e si possono mandare al
 * cliente da WhatsApp senza passare da qui.
 *
 * Da Android 10 si usa MediaStore e non serve nessun permesso per le immagini
 * create dall'app stessa. Sotto quella versione serve il permesso di scrittura
 * sull'archivio, dichiarato nel manifest con maxSdkVersion 28.
 */
class GalleryWriter(private val context: Context) {

    /**
     * Prepara la voce in galleria su cui la fotocamera scrivera' lo scatto.
     * Restituisce l'Uri da passare all'app fotocamera, oppure null se la
     * creazione non riesce.
     */
    suspend fun createPendingImage(
        takenAt: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = it.diario.lavorativo.domain.service.PhotoNaming
            .fileName(takenAt, zone)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt.toEpochMilli())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                // IS_PENDING nasconde la foto alla galleria finche' la
                // fotocamera non ha finito di scriverla: senza, comparirebbe
                // per un istante un'immagine vuota o corrotta.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                put(MediaStore.Images.Media.DATA, legacyFile(fileName).absolutePath)
            }
        }

        runCatching {
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
        }.getOrNull()
    }

    /** Rende visibile in galleria la foto appena scritta. */
    suspend fun publish(uri: Uri) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    /**
     * Elimina la voce rimasta a meta' quando l'utente annulla lo scatto.
     * Senza questa pulizia la galleria si riempirebbe di file vuoti.
     */
    suspend fun discard(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** Su Android 9 e precedenti la cartella va creata a mano. */
    private fun legacyFile(fileName: String): File {
        val pictures = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val album = File(pictures, ALBUM_NAME).apply { mkdirs() }
        return File(album, fileName)
    }

    /** Su Android 9 e precedenti serve il permesso di scrittura. */
    fun needsLegacyPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private companion object {
        const val ALBUM_NAME = "Diario Lavorativo"
        const val RELATIVE_PATH = Environment.DIRECTORY_PICTURES + "/Diario Lavorativo"
    }
}
