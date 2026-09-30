package it.diario.lavorativo.domain.model

import java.time.Instant
import java.time.LocalDate

/** Dove puo' stare un attrezzo. */
enum class ToolPlaceType(val label: String) {
    DEPOSITO("Deposito"),
    FURGONE("Furgone"),
    CANTIERE("Cantiere"),
    ALTRO("Altro");

    companion object {
        fun fromStorage(v: String?): ToolPlaceType = entries.firstOrNull { it.name == v } ?: DEPOSITO
    }
}

/** Un attrezzo e il posto dove si trova adesso. */
data class Tool(
    val id: Long = 0L,
    val name: String,
    val placeType: ToolPlaceType = ToolPlaceType.DEPOSITO,
    val site: Site? = null,
    /** Stanza o zona, es. "bagno primo piano". */
    val placeDetail: String? = null,
    /** Nome del posto quando e' "Altro". */
    val placeName: String? = null,
    val notes: String? = null,
    val movedAt: Instant = Instant.EPOCH
) {
    /** Il posto senza la stanza: e' la chiave per raggruppare. */
    val placeLabel: String
        get() = when (placeType) {
            ToolPlaceType.CANTIERE -> site?.name ?: "Cantiere"
            ToolPlaceType.ALTRO -> placeName?.takeIf { it.isNotBlank() } ?: "Altro"
            else -> placeType.label
        }

    /** "Montenero - bagno primo piano". */
    val fullPlace: String
        get() = placeLabel + (placeDetail?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: "")
}

/** Una cosa prestata. */
data class Loan(
    val id: Long = 0L,
    val what: String,
    val toWhom: String,
    val loanDate: LocalDate,
    val returnedDate: LocalDate? = null,
    val notes: String? = null
) {
    val isOut: Boolean get() = returnedDate == null
}
