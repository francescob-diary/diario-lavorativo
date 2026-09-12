package it.diario.lavorativo.domain.model

/**
 * Impostazioni utente.
 * L'orario standard NON e' fissato nel codice: 8 ore e' solo il valore iniziale,
 * modificabile dalle Impostazioni (requisito 14).
 */
data class UserSettings(
    val userName: String = "",
    val standardWorkMinutes: Int = DEFAULT_STANDARD_MINUTES,
    val overtimeEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_STANDARD_MINUTES = 8 * 60
    }
}
