package it.diario.lavorativo.domain.model

/** Tipo di pausa: distinguerla serve per le statistiche (fase 10). */
enum class BreakType {
    PAUSA,
    PRANZO;

    companion object {
        fun fromStorage(value: String?): BreakType =
            entries.firstOrNull { it.name == value } ?: PAUSA
    }
}
