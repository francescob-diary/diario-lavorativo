package it.diario.lavorativo.core.audio

import android.content.Context
import java.io.File

/** Dove stanno i file audio: cartella privata dell'app, non la galleria. */
class VoiceStorage(private val context: Context) {

    val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun file(fileName: String): File = File(directory, fileName)

    fun exists(fileName: String): Boolean = file(fileName).exists()

    fun delete(fileName: String): Boolean {
        val f = file(fileName)
        return if (f.exists()) f.delete() else false
    }

    fun all(): List<File> = directory.listFiles()?.toList().orEmpty()

    /** Nome nuovo basato sull'istante: non si scontra mai con quelli esistenti. */
    fun newFileName(millis: Long): String = "nota_" + millis.toString() + ".m4a"

    private companion object {
        const val DIRECTORY = "note"
    }
}
