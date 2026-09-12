package it.diario.lavorativo.domain.model

/**
 * Tipo di giornata. In fase 1 si usa solo [LAVORO]; gli altri valori sono già
 * previsti perché il calendario (fase 7) deve poterli mostrare senza migrazioni.
 */
enum class DayType {
    LAVORO,
    FERIE,
    PERMESSO,
    MALATTIA,
    FESTIVO;

    companion object {
        fun fromStorage(value: String?): DayType =
            entries.firstOrNull { it.name == value } ?: LAVORO
    }
}
