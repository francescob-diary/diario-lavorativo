package it.diario.lavorativo.core.export

import it.diario.lavorativo.domain.model.ExportDocument
import it.diario.lavorativo.domain.model.ExportSheet

/**
 * Scrive il CSV.
 *
 * Due scelte che sembrano dettagli ma decidono se il file si apre o no in
 * sede:
 *  - separatore punto e virgola, perche' Excel in italiano si aspetta quello
 *    e con la virgola butterebbe tutta la riga in una cella sola;
 *  - BOM in testa, senno' le lettere accentate escono a scacchi.
 *
 * Puro Kotlin: produce una stringa, non tocca il disco.
 */
object CsvWriter {

    const val SEPARATOR = ";"
    const val BOM = "\uFEFF"

    /** Un solo foglio. Il CSV non ha schede, quindi i fogli si accodano. */
    fun writeSheet(sheet: ExportSheet): String {
        val sb = StringBuilder()
        sb.append(escapeRow(sheet.headers))
        sheet.rows.forEach { row ->
            sb.append("\r\n")
            sb.append(escapeRow(row))
        }
        return sb.toString()
    }

    /**
     * Documento intero. I fogli vengono uno sotto l'altro, separati dal
     * proprio titolo: un CSV non ha schede e spezzare in piu' file
     * costringerebbe a mandarne quattro invece di uno.
     */
    fun write(document: ExportDocument): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(escapeRow(listOf(document.title)))
        sb.append("\r\n")
        sb.append(escapeRow(listOf(document.periodLabel)))
        if (document.subtitle.isNotBlank()) {
            sb.append("\r\n")
            sb.append(escapeRow(listOf(document.subtitle)))
        }

        document.sheets.filter { !it.isEmpty }.forEach { sheet ->
            sb.append("\r\n\r\n")
            sb.append(escapeRow(listOf(sheet.name)))
            sb.append("\r\n")
            sb.append(writeSheet(sheet))
        }

        return sb.toString()
    }

    private fun escapeRow(cells: List<String>): String =
        cells.joinToString(SEPARATOR) { escape(it) }

    /**
     * Una cella va fra virgolette se contiene il separatore, virgolette o un
     * a capo. Le virgolette interne si raddoppiano: e' la regola del CSV, e
     * un indirizzo tipo 'Via Roma 12; scala B' senza questo spaccherebbe la
     * riga in due colonne.
     */
    fun escape(value: String): String {
        val needsQuotes = value.contains(SEPARATOR) ||
            value.contains("\"") ||
            value.contains("\n") ||
            value.contains("\r")

        return if (needsQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
