package it.diario.lavorativo.domain.model

import java.time.LocalDate

/** Formato del file da produrre. */
enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

/** Arco di tempo da esportare. */
enum class ExportScope {
    SETTIMANA,
    MESE,
    ANNO,
    TUTTO
}

/**
 * Cosa finisce nel file.
 *
 * [includeCommunications] e' spento di proposito: nel documento che va in
 * sede non devono finire i messaggi di terzi. Chi vuole portarseli lo
 * decide di volta in volta.
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val scope: ExportScope = ExportScope.MESE,
    val reference: LocalDate = LocalDate.now(),
    val includeDays: Boolean = true,
    val includeActivities: Boolean = true,
    val includeEvents: Boolean = true,
    val includeCommunications: Boolean = false,
    /**
     * Il mezzo aziendale e' acceso di partenza, al contrario delle
     * comunicazioni: rifornimenti e pedaggi sono spese di lavoro, ed e'
     * proprio la sede a chiederli. Se non c'e' nessun dato il foglio non
     * viene creato lo stesso, quindi non fa danni a chi il mezzo non ce
     * l'ha.
     */
    val includeVehicle: Boolean = true,
    val includeSummary: Boolean = true
) {
    val hasSomethingToExport: Boolean
        get() = includeDays || includeActivities || includeEvents ||
            includeCommunications || includeVehicle || includeSummary
}

/**
 * Una tabella del documento. Neutra di proposito: le celle sono gia'
 * stringhe pronte da scrivere, cosi' CSV, XLSX e PDF partono tutti dagli
 * stessi identici dati e non possono divergere fra loro.
 */
data class ExportSheet(
    val name: String,
    val headers: List<String>,
    val rows: List<List<String>>
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** Larghezza in colonne, presa dall'intestazione. */
    val columnCount: Int get() = headers.size
}

/** Il documento completo, pronto per essere scritto in qualunque formato. */
data class ExportDocument(
    val title: String,
    val subtitle: String,
    val periodLabel: String,
    val from: LocalDate,
    val to: LocalDate,
    val sheets: List<ExportSheet> = emptyList(),
    /** Nome del file senza estensione. */
    val fileName: String = "diario"
) {
    val hasData: Boolean get() = sheets.any { !it.isEmpty }
}

/** Esito di un'esportazione, per dire all'utente com'e' andata. */
sealed interface ExportResult {
    data class Success(
        val fileName: String,
        val format: ExportFormat,
        val bytes: Long
    ) : ExportResult

    data object Empty : ExportResult

    data class Failure(val reason: String) : ExportResult
}
