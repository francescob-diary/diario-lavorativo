package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.GeoPoint
import it.diario.lavorativo.domain.model.Site

/** Esito del riconoscimento del cantiere in base alla posizione. */
sealed interface SiteMatch {
    /** Un solo cantiere nel raggio: si puo' selezionare da soli. */
    data class Confident(val site: Site, val distanceMeters: Double) : SiteMatch
    /** Piu' cantieri compatibili: decide l'utente, il primo e' il piu' vicino. */
    data class Ambiguous(val candidates: List<SiteDistance>) : SiteMatch
    /** Nessun cantiere nel raggio; mostra comunque il piu' vicino se esiste. */
    data class None(val nearest: SiteDistance?) : SiteMatch
}

data class SiteDistance(val site: Site, val distanceMeters: Double)

/**
 * Associa una posizione GPS ai cantieri salvati.
 *
 * Nessuna dipendenza da Android: e' logica pura, quindi verificabile con i test.
 * La precisione del sensore viene sommata al raggio del cantiere, altrimenti con
 * un fix impreciso non si riconoscerebbe mai il cantiere giusto.
 */
class SiteLocationMatcher {

    fun match(position: GeoPoint, sites: List<Site>): SiteMatch {
        if (!position.isValid) return SiteMatch.None(null)

        val measured = sites
            .mapNotNull { site ->
                val sitePosition = site.position ?: return@mapNotNull null
                SiteDistance(site, position.distanceMetersTo(sitePosition))
            }
            .sortedBy { it.distanceMeters }

        if (measured.isEmpty()) return SiteMatch.None(null)

        val slack = (position.accuracyMeters ?: 0f).toDouble().coerceAtMost(MAX_ACCURACY_SLACK)
        val inRange = measured.filter { it.distanceMeters <= it.site.radiusMeters + slack }

        return when {
            inRange.isEmpty() -> SiteMatch.None(measured.first())
            inRange.size == 1 -> SiteMatch.Confident(inRange.first().site, inRange.first().distanceMeters)
            else -> SiteMatch.Ambiguous(inRange)
        }
    }

    /**
     * Distanza percorsa lungo una sequenza di punti, in chilometri.
     * Gli spostamenti sotto la soglia vengono ignorati: sono deriva del GPS
     * quando si sta fermi, non chilometri reali.
     */
    fun travelledKm(points: List<GeoPoint>, minStepMeters: Double = 25.0): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            val step = points[i - 1].distanceMetersTo(points[i])
            if (step >= minStepMeters) total += step
        }
        return total / 1000.0
    }

    companion object {
        /** Oltre questo valore la posizione e' troppo vaga per allargare il raggio. */
        const val MAX_ACCURACY_SLACK = 200.0
    }
}
