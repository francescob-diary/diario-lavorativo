package it.diario.lavorativo.core.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import it.diario.lavorativo.domain.model.ExportDocument
import it.diario.lavorativo.domain.model.ExportSheet
import java.io.OutputStream

/**
 * Disegna il PDF.
 *
 * Usa android.graphics.pdf.PdfDocument, che c'e' gia' dentro Android: niente
 * iText, niente PdfBox, nessuna libreria da trascinare.
 *
 * ATTENZIONE - IMPAGINAZIONE PROVVISORIA.
 * Questa e' una tabella pulita e leggibile, ma non e' ancora il modulo
 * cartaceo che si usa in sede. Quando arrivera' quel modello, si rifa'
 * SOLO questo file: il contenuto arriva gia' pronto da ExportBuilder e non
 * va toccato. Percio' la parte grafica sta qui e da nessun'altra parte.
 */
object PdfWriter {

    // A4 a 72 punti per pollice, come vuole PdfDocument.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 32f
    private const val LINE_HEIGHT = 14f
    private const val HEADER_GAP = 8f

    fun write(document: ExportDocument, output: OutputStream) {
        val pdf = PdfDocument()
        val state = PageState(pdf)

        state.newPage()
        drawDocumentHeader(state, document)

        document.sheets.filter { !it.isEmpty }.forEach { sheet ->
            drawSheet(state, sheet)
        }

        state.finishPage()
        pdf.writeTo(output)
        pdf.close()
    }

    // ------------------------------------------------------------ disegno

    private fun drawDocumentHeader(state: PageState, document: ExportDocument) {
        state.canvas.drawText(document.title, MARGIN, state.y, TITLE)
        state.y += 22f

        state.canvas.drawText(document.periodLabel, MARGIN, state.y, SUBTITLE)
        state.y += 16f

        if (document.subtitle.isNotBlank()) {
            state.canvas.drawText(document.subtitle, MARGIN, state.y, BODY)
            state.y += 14f
        }

        state.y += 6f
        state.canvas.drawLine(MARGIN, state.y, PAGE_WIDTH - MARGIN, state.y, RULE)
        state.y += 18f
    }

    private fun drawSheet(state: PageState, sheet: ExportSheet) {
        state.ensureRoom(LINE_HEIGHT * 4)

        state.y += 6f
        state.canvas.drawText(sheet.name, MARGIN, state.y, SECTION)
        state.y += HEADER_GAP + 8f

        val widths = columnWidths(sheet)

        drawRow(state, sheet.headers, widths, HEADER_CELL)
        state.y += 2f
        state.canvas.drawLine(MARGIN, state.y, PAGE_WIDTH - MARGIN, state.y, RULE)
        state.y += LINE_HEIGHT

        sheet.rows.forEach { row ->
            // Se la riga non ci sta piu', si cambia pagina e si ristampa
            // l'intestazione: una tabella che continua senza titoli di
            // colonna e' illeggibile.
            if (state.needsNewPage(LINE_HEIGHT)) {
                state.newPage()
                state.canvas.drawText(
                    sheet.name + " (continua)", MARGIN, state.y, SECTION
                )
                state.y += HEADER_GAP + 8f
                drawRow(state, sheet.headers, widths, HEADER_CELL)
                state.y += 2f
                state.canvas.drawLine(MARGIN, state.y, PAGE_WIDTH - MARGIN, state.y, RULE)
                state.y += LINE_HEIGHT
            }
            drawRow(state, row, widths, BODY)
            state.y += LINE_HEIGHT
        }

        state.y += 10f
    }

    private fun drawRow(
        state: PageState,
        cells: List<String>,
        widths: FloatArray,
        paint: Paint
    ) {
        var x = MARGIN
        cells.forEachIndexed { index, value ->
            val width = widths.getOrElse(index) { 60f }
            state.canvas.drawText(fit(value, width, paint), x, state.y, paint)
            x += width
        }
    }

    /**
     * Larghezze proporzionali al contenuto, entro un minimo e un massimo:
     * la colonna "Data" non deve prendersi lo spazio di "Lavoro svolto", ma
     * nemmeno sparire.
     */
    private fun columnWidths(sheet: ExportSheet): FloatArray {
        val usable = PAGE_WIDTH - MARGIN * 2
        val count = sheet.columnCount
        if (count == 0) return FloatArray(0)

        val pesi = FloatArray(count) { index ->
            val piuLungo = (sheet.rows.map { it.getOrElse(index) { "" } } + sheet.headers[index])
                .maxOf { it.length }
            piuLungo.toFloat().coerceIn(6f, 30f)
        }

        val somma = pesi.sum()
        return FloatArray(count) { usable * pesi[it] / somma }
    }

    /** Taglia il testo che non ci sta, con i puntini: meglio corto che sovrapposto. */
    private fun fit(value: String, width: Float, paint: Paint): String {
        val disponibile = width - 4f
        if (paint.measureText(value) <= disponibile) return value

        var testo = value
        while (testo.isNotEmpty() && paint.measureText(testo + "...") > disponibile) {
            testo = testo.dropLast(1)
        }
        return if (testo.isEmpty()) "" else testo + "..."
    }

    // ------------------------------------------------------------ pagina

    private class PageState(val pdf: PdfDocument) {
        var page: PdfDocument.Page? = null
        var number = 0
        var y = 0f

        val canvas: Canvas get() = page!!.canvas

        fun newPage() {
            finishPage()
            number++
            page = pdf.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
            )
            y = MARGIN + 14f
            drawFooter()
        }

        fun finishPage() {
            page?.let { pdf.finishPage(it) }
            page = null
        }

        fun needsNewPage(needed: Float): Boolean = y + needed > PAGE_HEIGHT - MARGIN - 20f

        fun ensureRoom(needed: Float) {
            if (page == null || needsNewPage(needed)) newPage()
        }

        /** Numero di pagina in fondo: se il foglio si sparpaglia sul tavolo si rimette in ordine. */
        private fun drawFooter() {
            canvas.drawText(
                "Pagina " + number.toString(),
                PAGE_WIDTH - MARGIN - 50f,
                PAGE_HEIGHT - MARGIN + 6f,
                FOOTER
            )
        }
    }

    // ------------------------------------------------------------ pennelli

    private val TITLE = Paint().apply {
        color = Color.BLACK
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val SUBTITLE = Paint().apply {
        color = Color.BLACK
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val SECTION = Paint().apply {
        color = Color.BLACK
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val HEADER_CELL = Paint().apply {
        color = Color.BLACK
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val BODY = Paint().apply {
        color = Color.BLACK
        textSize = 8f
        isAntiAlias = true
    }

    private val FOOTER = Paint().apply {
        color = Color.GRAY
        textSize = 8f
        isAntiAlias = true
    }

    private val RULE = Paint().apply {
        color = Color.GRAY
        strokeWidth = 0.6f
    }
}
