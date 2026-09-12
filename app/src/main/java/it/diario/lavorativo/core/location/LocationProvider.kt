package it.diario.lavorativo.core.location

import it.diario.lavorativo.domain.model.GeoPoint

/** Esito di una richiesta di posizione, senza eccezioni da gestire a mano. */
sealed interface LocationResult {
    data class Success(val point: GeoPoint) : LocationResult
    /** Permesso non concesso: la UI deve chiederlo. */
    data object PermissionMissing : LocationResult
    /** GPS spento a livello di sistema. */
    data object LocationDisabled : LocationResult
    /** Nessun fix entro il tempo massimo. */
    data object Timeout : LocationResult
    data class Error(val message: String) : LocationResult
}

/**
 * Astrazione sulla posizione. L'interfaccia sta qui e non nel dominio perche'
 * e' un dettaglio di piattaforma, ma resta sostituibile con una finta nei test.
 */
interface LocationProvider {
    fun hasPermission(): Boolean
    fun isLocationEnabled(): Boolean
    /** Richiede una posizione singola, aggiornata. Sospende fino al fix o al timeout. */
    suspend fun currentLocation(timeoutMillis: Long = 15_000L): LocationResult
}
