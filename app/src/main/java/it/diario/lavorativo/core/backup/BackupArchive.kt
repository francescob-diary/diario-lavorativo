package it.diario.lavorativo.core.backup

import it.diario.lavorativo.domain.backup.BackupContent
import it.diario.lavorativo.domain.backup.BackupManifest
import it.diario.lavorativo.domain.backup.BackupReadResult
import it.diario.lavorativo.domain.backup.BackupSerializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Il file di backup: uno zip con dentro
 *   manifest.json   la carta d'identita', leggibile da sola
 *   dati.json       tutte le righe del database
 *   foto/           le copie ridotte delle foto, se richieste
 *   note/           le note vocali, se richieste
 *
 * Zip e non un file unico perche' cosi' il manifest si legge senza tirare
 * fuori centinaia di megabyte di foto: prima si dice all'utente cosa sta
 * per ripristinare, poi si ripristina.
 *
 * E' un formato aperto: si apre con qualsiasi computer, e i dati si
 * leggono anche senza l'app. Un backup che si apre solo col programma che
 * l'ha fatto non e' un backup, e' un ostaggio.
 */
object BackupArchive {

    const val MANIFEST = "manifest.json"
    const val DATA = "dati.json"
    const val PHOTO_DIR = "foto/"
    const val VOICE_DIR = "note/"

    /** Nome del file, con la data davanti cosi' si ordinano da soli. */
    fun fileName(dateIso: String): String = "diario-backup-" + dateIso + ".zip"

    // ---------------------------------------------------------- scrittura

    fun write(
        output: OutputStream,
        manifest: BackupManifest,
        content: BackupContent,
        photos: List<File> = emptyList(),
        voiceNotes: List<File> = emptyList(),
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val total = 2 + photos.size + voiceNotes.size
        var done = 0

        ZipOutputStream(output).use { zip ->
            putText(zip, MANIFEST, BackupSerializer.writeManifest(manifest))
            onProgress(++done, total)

            putText(zip, DATA, BackupSerializer.writeContent(content))
            onProgress(++done, total)

            photos.forEach { file ->
                if (file.exists()) putFile(zip, PHOTO_DIR + file.name, file)
                onProgress(++done, total)
            }
            voiceNotes.forEach { file ->
                if (file.exists()) putFile(zip, VOICE_DIR + file.name, file)
                onProgress(++done, total)
            }
        }
    }

    private fun putText(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putFile(zip: ZipOutputStream, path: String, file: File) {
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    // ---------------------------------------------------------- lettura

    /**
     * Legge solo il manifest, senza toccare il resto: serve per mostrare
     * all'utente cosa contiene il file prima che decida.
     */
    fun readManifest(input: InputStream): BackupManifest? {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST) {
                    return BackupSerializer.readManifest(
                        zip.readBytes().toString(Charsets.UTF_8)
                    )
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Legge i dati e rimette al loro posto foto e note vocali.
     *
     * I media si scrivono su disco prima di toccare il database: se il
     * file e' rovinato a meta', meglio accorgersene con il diario ancora
     * intatto.
     */
    fun read(
        input: InputStream,
        photoDirectory: File,
        voiceDirectory: File,
        restoreMedia: Boolean
    ): BackupReadResult {
        var dataText: String? = null
        var manifest: BackupManifest? = null
        var mediaRestored = 0

        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == MANIFEST ->
                            manifest = BackupSerializer.readManifest(
                                zip.readBytes().toString(Charsets.UTF_8)
                            )

                        name == DATA ->
                            dataText = zip.readBytes().toString(Charsets.UTF_8)

                        restoreMedia && name.startsWith(PHOTO_DIR) && !entry.isDirectory -> {
                            if (extract(zip, photoDirectory, name.removePrefix(PHOTO_DIR))) {
                                mediaRestored++
                            }
                        }

                        restoreMedia && name.startsWith(VOICE_DIR) && !entry.isDirectory -> {
                            if (extract(zip, voiceDirectory, name.removePrefix(VOICE_DIR))) {
                                mediaRestored++
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return BackupReadResult.Damaged(
                "Il file non si apre: potrebbe essere incompleto o non essere un backup"
            )
        }

        val text = dataText
            ?: return BackupReadResult.Damaged(
                "Dentro il file non c'e' " + DATA + ": non e' un backup del diario"
            )

        val esito = BackupSerializer.readContent(text)
        if (esito is BackupReadResult.Ok && manifest != null) {
            return BackupReadResult.Ok(manifest!!, esito.content)
        }
        return esito
    }

    /**
     * Tira fuori un file dentro la cartella indicata.
     *
     * Il nome viene ripulito: uno zip puo' contenere percorsi tipo
     * "../../altro" e scrivere fuori dalla cartella dell'app. E' un modo
     * noto di fare danni, e non costa niente chiuderlo.
     */
    private fun extract(zip: ZipInputStream, directory: File, rawName: String): Boolean {
        val safeName = File(rawName).name
        if (safeName.isBlank() || safeName == "." || safeName == "..") return false

        directory.mkdirs()
        val target = File(directory, safeName)
        if (target.canonicalPath != File(directory, safeName).canonicalPath) return false

        target.outputStream().use { out -> zip.copyTo(out) }
        return true
    }

    /** Conta foto e note vocali dentro l'archivio, senza estrarle. */
    fun countMedia(input: InputStream): Int {
        var count = 0
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory &&
                        (entry.name.startsWith(PHOTO_DIR) || entry.name.startsWith(VOICE_DIR))
                    ) {
                        count++
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return 0
        }
        return count
    }
}
