package it.diario.lavorativo.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Astrazione dell'orologio di sistema.
 * Serve per poter simulare il tempo nei test invece di dipendere da Instant.now().
 */
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
    // LocalDate.ofInstant() e' Java 9+: su Android API 26 non esiste, si passa da atZone().
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

/** Implementazione reale, usata dall'app. */
class SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
