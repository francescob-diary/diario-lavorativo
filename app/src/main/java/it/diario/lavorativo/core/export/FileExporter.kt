package it.diario.lavorativo.core.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import it.diario.lavorativo.R
import it.diario.lavorativo.domain.model.ExportDocument
import it.diario.lavorativo.domain.model.ExportFormat
import it.diario.lavorativo.domain.model.WeeklyReport
import java.io.File

/**
 * Scrive il file su disco e prepara la condivisione.
 *
 * I file finiscono nello spazio privato dell'app, sotto "export", e si
 * mandano fuori solo tramite FileProvider: non serve nessun permesso di
 * archiviazione e nessun'altra app puo' curiosare nella cartella.
 */
class FileExporter(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun write(document: ExportDocument, format: ExportFormat): File {
        val file = File(directory, document.fileName + "." + format.extension)

        when (format) {
            ExportFormat.CSV ->
                file.writeText(CsvWriter.write(document), Charsets.UTF_8)

            ExportFormat.XLSX ->
                file.writeBytes(XlsxWriter.write(document))

            ExportFormat.PDF ->
                file.outputStream().use { PdfWriter.write(document, it) }
        }

        return file
    }

    /**
     * Il rapportino settimanale impaginato sul modulo cartaceo dell'azienda.
     * Sta a parte perche' non e' una tabella qualunque: e' il foglio che in
     * sede si aspettano di vedere.
     */
    fun writeWeeklySheet(
        report: WeeklyReport,
        userName: String,
        fileName: String
    ): File {
        val file = File(directory, fileName + ".pdf")
        // Il marchio si carica qui, dove il Context c'e'. Se la risorsa
        // mancasse il foglio esce lo stesso, senza logo, invece di non
        // uscire affatto: il rapportino serve il lunedi' mattina.
        val logo = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo_mbr)
        }.getOrNull()
        file.outputStream().use { WeeklySheetPdf.write(report, userName, it, logo) }
        return file
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )

    /**
     * Intent di condivisione. Con createChooser l'utente sceglie ogni volta
     * dove mandarlo: mail, WhatsApp o salvataggio su Drive.
     */
    fun shareIntent(file: File, format: ExportFormat, subject: String): Intent {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(share, "Invia " + format.extension.uppercase())
    }

    /** Apre il file con l'app predefinita, per controllarlo prima di mandarlo. */
    fun openIntent(file: File, format: ExportFormat): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(file), format.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * Ripulisce le esportazioni vecchie. Sono file rigenerabili in qualunque
     * momento: tenerli per sempre riempirebbe la memoria senza motivo.
     */
    fun cleanOlderThan(days: Int = 30) {
        val soglia = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < soglia) file.delete()
        }
    }

    private companion object {
        const val DIRECTORY = "export"
    }
}
