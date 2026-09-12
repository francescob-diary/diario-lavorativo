package it.diario.lavorativo.core.export

import it.diario.lavorativo.domain.model.ExportDocument
import it.diario.lavorativo.domain.model.ExportSheet
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Scrive un file XLSX vero, senza librerie.
 *
 * Un xlsx e' uno zip con dentro qualche file XML: si puo' produrre con la
 * sola java.util.zip. L'alternativa era Apache POI, che su Android pesa
 * decine di megabyte e trascina dipendenze che con minSdk 26 danno
 * problemi. Per scrivere quattro tabelle non vale la pena.
 *
 * Puro JVM: restituisce i byte, non tocca il disco. Quindi e' verificabile.
 */
object XlsxWriter {

    fun write(document: ExportDocument): ByteArray {
        val sheets = document.sheets.filter { !it.isEmpty }.ifEmpty { document.sheets }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(sheets.size))
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook(sheets))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put(zip, "xl/styles.xml", styles())
            sheets.forEachIndexed { index, sheet ->
                put(zip, "xl/worksheets/sheet" + (index + 1) + ".xml", sheetXml(sheet))
            }
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // ------------------------------------------------------------ pezzi xml

    private fun contentTypes(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append(HEADER)
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..sheetCount) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet")
            sb.append(i)
            sb.append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun rootRels(): String =
        HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbook(sheets: List<ExportSheet>): String {
        val sb = StringBuilder()
        sb.append(HEADER)
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        sb.append("<sheets>")
        sheets.forEachIndexed { index, sheet ->
            sb.append("<sheet name=\"")
            sb.append(escapeSheetName(sheet.name))
            sb.append("\" sheetId=\"")
            sb.append(index + 1)
            sb.append("\" r:id=\"rId")
            sb.append(index + 1)
            sb.append("\"/>")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRels(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append(HEADER)
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheetCount) {
            sb.append("<Relationship Id=\"rId")
            sb.append(i)
            sb.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
            sb.append(i)
            sb.append(".xml\"/>")
        }
        sb.append("<Relationship Id=\"rId")
        sb.append(sheetCount + 1)
        sb.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        sb.append("</Relationships>")
        return sb.toString()
    }

    /**
     * Due stili soli: normale e grassetto. Il grassetto serve per la riga di
     * intestazione, cosi' chi apre il foglio distingue subito i titoli
     * dalle righe.
     */
    private fun styles(): String =
        HEADER +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>" +
            "<borders count=\"1\"><border/></borders>" +
            "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>" +
            "<cellXfs count=\"2\">" +
            "<xf xfId=\"0\"/>" +
            "<xf xfId=\"0\" fontId=\"1\" applyFont=\"1\"/>" +
            "</cellXfs>" +
            "</styleSheet>"

    private fun sheetXml(sheet: ExportSheet): String {
        val sb = StringBuilder()
        sb.append(HEADER)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetData>")

        row(sb, 1, sheet.headers, bold = true)
        sheet.rows.forEachIndexed { index, cells ->
            row(sb, index + 2, cells, bold = false)
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun row(sb: StringBuilder, number: Int, cells: List<String>, bold: Boolean) {
        sb.append("<row r=\"")
        sb.append(number)
        sb.append("\">")
        cells.forEachIndexed { index, value ->
            sb.append("<c r=\"")
            sb.append(columnName(index))
            sb.append(number)
            sb.append("\" t=\"inlineStr\"")
            if (bold) sb.append(" s=\"1\"")
            sb.append("><is><t xml:space=\"preserve\">")
            sb.append(escapeXml(value))
            sb.append("</t></is></c>")
        }
        sb.append("</row>")
    }

    /**
     * Lettera della colonna: 0 -> A, 25 -> Z, 26 -> AA. Le tabelle qui
     * arrivano a dodici colonne, ma il conto regge oltre la Z lo stesso.
     */
    fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        }
        return sb.toString()
    }

    /**
     * I caratteri riservati dell'XML vanno sostituiti, senno' un semplice
     * "Rossi & figli" o un "<" in una nota renderebbero il file illeggibile.
     */
    fun escapeXml(value: String): String {
        val sb = StringBuilder(value.length)
        value.forEach { c ->
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else ->
                    // I caratteri di controllo non sono ammessi in XML:
                    // meglio uno spazio che un file che non si apre.
                    if (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        sb.append(' ')
                    } else {
                        sb.append(c)
                    }
            }
        }
        return sb.toString()
    }

    /**
     * Excel rifiuta i nomi scheda con certi caratteri e oltre i 31 caratteri.
     */
    fun escapeSheetName(name: String): String {
        val cleaned = name.map { c ->
            if (c in charArrayOf(':', '\\', '/', '?', '*', '[', ']')) ' ' else c
        }.joinToString("")
        val trimmed = if (cleaned.length > 31) cleaned.substring(0, 31) else cleaned
        return escapeXml(trimmed.ifBlank { "Foglio" })
    }

    private const val HEADER =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
}
