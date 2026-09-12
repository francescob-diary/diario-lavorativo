package it.diario.lavorativo.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.WeeklyDayLine
import it.diario.lavorativo.domain.model.WeeklyReport
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Il rapportino settimanale, impaginato sul modulo cartaceo che si usa in
 * sede: "ORE DELLA SETTIMANA", cinque colonne, sette righe da lunedi' a
 * domenica con la domenica in rosso.
 *
 * Riprodurre il foglio a cui sono abituati in ufficio non e' un vezzo: un
 * documento che arriva con le colonne al posto giusto si controlla in dieci
 * secondi, uno diverso va letto da capo e genera domande.
 *
 * La colonna COD / DOCUMENTI resta vuota: nell'app non c'e' un dato che le
 * corrisponda e si compila a mano, come sul cartaceo.
 */
object WeeklySheetPdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 30f

    private const val HEADER_ROW_HEIGHT = 30f
    private const val DATA_ROW_HEIGHT = 52f

    /** Larghezze relative delle cinque colonne, prese dal foglio cartaceo. */
    private val COLUMN_WEIGHTS = floatArrayOf(88f, 175f, 45f, 375f, 172f)

    private val COLUMN_TITLES = listOf(
        "GIORNI", "CLIENTE O CANTIERE", "H", "LAVORAZIONE E MATERIALE", "COD / DOCUMENTI"
    )

    /** Iniziali dei giorni come sul modulo: lunedi' L, martedi' M, e via cosi'. */
    private val DAY_LETTERS = listOf("L", "M", "M", "G", "V", "S", "D")

    private val BLU = Color.rgb(31, 78, 156)
    private val ROSSO = Color.rgb(220, 30, 30)

    /** Riquadro del marchio, misurato sul foglio cartaceo. */
    private const val LOGO_LEFT = MARGIN + 45f
    private const val LOGO_TOP = MARGIN + 32f
    private const val LOGO_WIDTH = 105f
    private const val LOGO_MAX_HEIGHT = 48f

    /**
     * Filtro acceso in ridimensionamento: il marchio arriva a blocchi netti
     * apposta, ma rimpicciolito senza filtro diventerebbe seghettato.
     */
    private val LOGO_PAINT = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }

    /**
     * [logo] e' il marchio da stampare in alto a sinistra. Arriva gia'
     * caricato da chi chiama, invece di leggerselo da solo: cosi' questo
     * file non ha bisogno del Context ne' delle risorse, e la geometria
     * del foglio resta controllabile a parte.
     *
     * Se e' null il foglio esce lo stesso, con lo spazio vuoto.
     */
    fun write(
        report: WeeklyReport,
        userName: String,
        output: OutputStream,
        logo: Bitmap? = null
    ) {
        val pdf = PdfDocument()
        val page = pdf.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        )
        val canvas = page.canvas

        var y = drawHeader(canvas, report, userName, logo)
        y = drawTableHeader(canvas, y)

        report.days.forEachIndexed { index, line ->
            y = drawDayRow(canvas, y, index, line)
        }

        drawTotals(canvas, y, report)

        pdf.finishPage(page)
        pdf.writeTo(output)
        pdf.close()
    }

    // ------------------------------------------------------------ testata

    private fun drawHeader(
        canvas: Canvas,
        report: WeeklyReport,
        userName: String,
        logo: Bitmap?
    ): Float {
        canvas.drawText(
            "ORE DELLA SETTIMANA",
            PAGE_WIDTH / 2f,
            MARGIN + 20f,
            TITLE
        )

        // Il marchio in alto a sinistra, nella stessa posizione del modulo
        // cartaceo. Si adatta allo spazio mantenendo le proporzioni: un logo
        // schiacciato si nota subito e fa sembrare sciatto tutto il foglio.
        if (logo != null && logo.width > 0 && logo.height > 0) {
            val proporzione = logo.height.toFloat() / logo.width.toFloat()
            var larghezza = LOGO_WIDTH
            var altezza = larghezza * proporzione
            if (altezza > LOGO_MAX_HEIGHT) {
                altezza = LOGO_MAX_HEIGHT
                larghezza = altezza / proporzione
            }
            val destinazione = RectF(
                LOGO_LEFT,
                LOGO_TOP,
                LOGO_LEFT + larghezza,
                LOGO_TOP + altezza
            )
            canvas.drawBitmap(
                logo,
                Rect(0, 0, logo.width, logo.height),
                destinazione,
                LOGO_PAINT
            )
        }

        if (userName.isNotBlank()) {
            canvas.drawText(
                userName.uppercase(Locale.ITALIAN),
                PAGE_WIDTH / 2f + 40f,
                MARGIN + 105f,
                NAME
            )
        }

        // "DAL 2 AL 8 2026", come sul modulo.
        val dal = report.weekStart.format(DAY_MONTH)
        val al = report.weekEnd.format(DAY_MONTH)
        var x = MARGIN + 50f
        canvas.drawText("DAL ", x, MARGIN + 140f, RANGE_LABEL)
        x += RANGE_LABEL.measureText("DAL ")
        canvas.drawText(dal, x, MARGIN + 140f, RANGE_VALUE)
        x += RANGE_VALUE.measureText(dal)
        canvas.drawText(" AL ", x, MARGIN + 140f, RANGE_LABEL)
        x += RANGE_LABEL.measureText(" AL ")
        canvas.drawText(al, x, MARGIN + 140f, RANGE_VALUE)
        x += RANGE_VALUE.measureText(al)
        canvas.drawText(" " + report.weekStart.year.toString(), x, MARGIN + 140f, RANGE_VALUE)

        return MARGIN + 155f
    }

    // ------------------------------------------------------------ tabella

    private fun drawTableHeader(canvas: Canvas, top: Float): Float {
        val bottom = top + HEADER_ROW_HEIGHT
        val widths = columnWidths()

        drawCellBorders(canvas, top, bottom, widths)

        var x = MARGIN
        COLUMN_TITLES.forEachIndexed { index, title ->
            val width = widths[index]
            // "CLIENTE O CANTIERE" sul modulo sta su due righe: si spezza
            // anche qui, senno' non entra nella colonna.
            val righe = wrap(title, width - 6f, COLUMN_TITLE)
            val partenza = bottom - (HEADER_ROW_HEIGHT - righe.size * 10f) / 2f - 4f -
                (righe.size - 1) * 10f
            righe.forEachIndexed { riga, testo ->
                canvas.drawText(
                    testo,
                    x + width / 2f,
                    partenza + riga * 10f,
                    COLUMN_TITLE
                )
            }
            x += width
        }

        return bottom
    }

    private fun drawDayRow(
        canvas: Canvas,
        top: Float,
        index: Int,
        line: WeeklyDayLine
    ): Float {
        val bottom = top + DATA_ROW_HEIGHT
        val widths = columnWidths()

        drawCellBorders(canvas, top, bottom, widths)

        // La domenica e' rossa sul modulo, e rossa resta.
        val etichetta = line.date.dayOfMonth.toString() + "-" + DAY_LETTERS[index]
        val pennello = if (index == 6) DAY_LABEL_SUNDAY else DAY_LABEL
        canvas.drawText(
            etichetta,
            MARGIN + widths[0] - 6f,
            top + DATA_ROW_HEIGHT / 2f + 7f,
            pennello
        )

        // Ferie, malattia e permessi si scrivono al posto della lavorazione:
        // in sede devono vedere perche' quel giorno non ci sono ore.
        val lavorazione = if (line.dayType != DayType.LAVORO) {
            absenceLabel(line.dayType)
        } else {
            line.work
        }

        cell(canvas, MARGIN + widths[0], widths[1], top, line.siteName.orEmpty())
        cell(canvas, MARGIN + widths[0] + widths[1], widths[2], top,
            if (line.isWorkDay) DurationFormat.decimalHours(line.net) else "",
            centered = true)
        cell(canvas, MARGIN + widths[0] + widths[1] + widths[2], widths[3], top, lavorazione)

        return bottom
    }

    /**
     * Riga dei totali sotto la tabella. Sul cartaceo si fa la somma a mano
     * in fondo: qui e' gia' fatta, ed e' il primo numero che guardano.
     */
    private fun drawTotals(canvas: Canvas, top: Float, report: WeeklyReport) {
        val y = top + 22f
        canvas.drawText(
            "TOTALE ORE: " + DurationFormat.decimalHours(report.totals.net).ifBlank { "0" },
            MARGIN,
            y,
            TOTALS
        )

        if (report.incompleteDays.isNotEmpty()) {
            canvas.drawText(
                "Attenzione: " + report.incompleteDays.size.toString() +
                    " giornata senza orario di uscita",
                MARGIN,
                y + 16f,
                WARNING
            )
        }

        if (report.notes.isNotBlank()) {
            var riga = y + (if (report.incompleteDays.isEmpty()) 20f else 36f)
            wrap(report.notes, PAGE_WIDTH - MARGIN * 2, BODY).forEach { testo ->
                canvas.drawText(testo, MARGIN, riga, BODY)
                riga += 12f
            }
        }
    }

    // ------------------------------------------------------------ utilita'

    private fun columnWidths(): FloatArray {
        val usable = PAGE_WIDTH - MARGIN * 2
        val somma = COLUMN_WEIGHTS.sum()
        return FloatArray(COLUMN_WEIGHTS.size) { usable * COLUMN_WEIGHTS[it] / somma }
    }

    private fun drawCellBorders(canvas: Canvas, top: Float, bottom: Float, widths: FloatArray) {
        canvas.drawLine(MARGIN, top, PAGE_WIDTH - MARGIN, top, BORDER)
        canvas.drawLine(MARGIN, bottom, PAGE_WIDTH - MARGIN, bottom, BORDER)

        var x = MARGIN
        canvas.drawLine(x, top, x, bottom, BORDER)
        widths.forEach { width ->
            x += width
            canvas.drawLine(x, top, x, bottom, BORDER)
        }
    }

    private fun cell(
        canvas: Canvas,
        x: Float,
        width: Float,
        top: Float,
        text: String,
        centered: Boolean = false
    ) {
        if (text.isBlank()) return

        val paint = if (centered) BODY_CENTERED else BODY
        val righe = wrap(text, width - 8f, paint).take(3)
        var y = top + 16f
        righe.forEach { riga ->
            canvas.drawText(riga, if (centered) x + width / 2f else x + 4f, y, paint)
            y += 11f
        }
    }

    /** Manda a capo alle parole, cosi' una descrizione lunga non esce dalla cella. */
    private fun wrap(text: String, width: Float, paint: Paint): List<String> {
        if (paint.measureText(text) <= width) return listOf(text)

        val righe = mutableListOf<String>()
        var corrente = StringBuilder()

        text.split(" ").forEach { parola ->
            val prova = if (corrente.isEmpty()) parola else corrente.toString() + " " + parola
            if (paint.measureText(prova) <= width) {
                corrente = StringBuilder(prova)
            } else {
                if (corrente.isNotEmpty()) righe += corrente.toString()
                corrente = StringBuilder(parola)
            }
        }
        if (corrente.isNotEmpty()) righe += corrente.toString()

        return righe
    }

    private fun absenceLabel(type: DayType): String = when (type) {
        DayType.FERIE -> "FERIE"
        DayType.PERMESSO -> "PERMESSO"
        DayType.MALATTIA -> "MALATTIA"
        DayType.FESTIVO -> "FESTIVO"
        DayType.LAVORO -> ""
    }

    private val DAY_MONTH: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d/MM", Locale.ITALIAN)

    // ------------------------------------------------------------ pennelli

    private val TITLE = Paint().apply {
        color = BLU
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val NAME = Paint().apply {
        color = Color.BLACK
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val RANGE_LABEL = Paint().apply {
        color = BLU
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val RANGE_VALUE = Paint().apply {
        color = Color.BLACK
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val COLUMN_TITLE = Paint().apply {
        color = BLU
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val DAY_LABEL = Paint().apply {
        color = BLU
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    private val DAY_LABEL_SUNDAY = Paint().apply {
        color = ROSSO
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    private val BODY = Paint().apply {
        color = Color.BLACK
        textSize = 9f
        isAntiAlias = true
    }

    // La colonna H e' stretta come sul modulo cartaceo: a corpo 11 un
    // valore tipo "9,25" sbordava sui bordi della cella.
    private val BODY_CENTERED = Paint().apply {
        color = Color.BLACK
        textSize = 9.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val TOTALS = Paint().apply {
        color = Color.BLACK
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val WARNING = Paint().apply {
        color = ROSSO
        textSize = 9f
        isAntiAlias = true
    }

    private val BORDER = Paint().apply {
        color = Color.rgb(20, 20, 20)
        strokeWidth = 1.1f
        style = Paint.Style.STROKE
    }
}
