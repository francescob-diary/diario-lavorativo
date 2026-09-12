package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.DueStatus
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.MaintenanceDue
import it.diario.lavorativo.domain.model.MaintenanceType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Le scadenze del mezzo.
 *
 * Il tagliando scade a chilometri, la revisione scade a data: sono due
 * conti diversi e vanno tenuti diversi. Un intervento puo' avere tutte e
 * due le scadenze, e allora vale quella che arriva prima.
 *
 * Niente scadenze inventate: se all'ultimo tagliando nessuno ha scritto
 * quando tornare, l'app non lo indovina. Le regole standard cambiano da
 * un mezzo all'altro e sbagliare vorrebbe dire allarmi finti, che dopo
 * due volte si imparano a ignorare, e allora non servono piu' a niente.
 */
class MaintenanceScheduler(
    /** Sotto questa soglia la scadenza a calendario si segna come vicina. */
    private val warnDays: Long = 30L,
    /** Sotto questa soglia la scadenza a chilometri si segna come vicina. */
    private val warnKm: Int = 1000
) {

    /**
     * Le scadenze aperte, una per tipo di intervento.
     *
     * Di ogni tipo conta solo l'ultimo fatto: se il tagliando e' stato
     * rifatto a marzo, quello di ottobre dell'anno prima non ha piu' niente
     * da dire.
     */
    fun dueList(
        maintenances: List<Maintenance>,
        today: LocalDate,
        currentOdometerKm: Int? = null
    ): List<MaintenanceDue> {
        if (maintenances.isEmpty()) return emptyList()

        val ultimoPerTipo = maintenances
            .groupBy { it.type }
            .mapValues { (_, lista) ->
                lista.sortedWith(compareBy({ it.date }, { it.id })).last()
            }

        return ultimoPerTipo.values
            .filter { it.hasDeadline }
            .map { due(it, today, currentOdometerKm) }
            .sortedWith(compareBy({ ordineStato(it.status) }, { it.daysLeft ?: Long.MAX_VALUE }))
    }

    /** La scadenza di un singolo intervento. */
    fun due(
        maintenance: Maintenance,
        today: LocalDate,
        currentOdometerKm: Int? = null
    ): MaintenanceDue {
        val giorniMancanti = maintenance.nextDueDate?.let {
            ChronoUnit.DAYS.between(today, it)
        }

        val kmMancanti = if (maintenance.nextDueKm != null && currentOdometerKm != null) {
            maintenance.nextDueKm - currentOdometerKm
        } else {
            null
        }

        val stato = stato(giorniMancanti, kmMancanti)

        return MaintenanceDue(
            type = maintenance.type,
            lastDate = maintenance.date,
            lastOdometerKm = maintenance.odometerKm,
            dueDate = maintenance.nextDueDate,
            dueKm = maintenance.nextDueKm,
            daysLeft = giorniMancanti,
            kmLeft = kmMancanti,
            status = stato
        )
    }

    /**
     * Vale la scadenza piu' vicina delle due: se il tagliando e' fra dieci
     * giorni ma anche fra tremila chilometri, quello che conta sono i dieci
     * giorni.
     */
    private fun stato(giorni: Long?, km: Int?): DueStatus {
        val statoGiorni = when {
            giorni == null -> null
            giorni < 0L -> DueStatus.SCADUTA
            giorni <= warnDays -> DueStatus.VICINA
            else -> DueStatus.OK
        }
        val statoKm = when {
            km == null -> null
            km < 0 -> DueStatus.SCADUTA
            km <= warnKm -> DueStatus.VICINA
            else -> DueStatus.OK
        }

        return when {
            statoGiorni == null && statoKm == null -> DueStatus.OK
            statoGiorni == null -> statoKm ?: DueStatus.OK
            statoKm == null -> statoGiorni
            // Due scadenze insieme: comanda la piu' urgente.
            ordineStato(statoGiorni) <= ordineStato(statoKm) -> statoGiorni
            else -> statoKm
        }
    }

    private fun ordineStato(stato: DueStatus): Int = when (stato) {
        DueStatus.SCADUTA -> 0
        DueStatus.VICINA -> 1
        DueStatus.OK -> 2
    }

    /**
     * Le scadenze che meritano una notifica: quelle passate e quelle
     * vicine. Il resto sta buono.
     */
    fun toWarn(
        maintenances: List<Maintenance>,
        today: LocalDate,
        currentOdometerKm: Int? = null
    ): List<MaintenanceDue> =
        dueList(maintenances, today, currentOdometerKm)
            .filter { it.status != DueStatus.OK }

    /**
     * Il testo della notifica, gia' pronto.
     *
     * Una riga sola: in cantiere una notifica lunga non si legge.
     */
    fun warningText(dues: List<MaintenanceDue>): String? {
        if (dues.isEmpty()) return null
        val scadute = dues.filter { it.isOverdue }
        if (scadute.isNotEmpty()) {
            val primo = scadute.first()
            return if (scadute.size == 1) {
                primo.type.label + ": scaduto"
            } else {
                primo.type.label + " e altre " + (scadute.size - 1) + " scadute"
            }
        }
        val primo = dues.first()
        return if (dues.size == 1) {
            primo.type.label + " " + primo.shortLabel
        } else {
            primo.type.label + " " + primo.shortLabel + ", e altre " + (dues.size - 1)
        }
    }

    /** Proposta di scadenza per un tipo, se l'utente non ne mette una sua. */
    fun suggestedNextKm(type: MaintenanceType, odometerKm: Int?): Int? {
        if (odometerKm == null || !type.scadeAKm) return null
        // Non e' una regola del costruttore, e' solo un valore di partenza
        // che l'utente vede scritto nel campo e puo' cambiare prima di
        // salvare. Resta una sua scelta, non una decisione dell'app.
        val passo = when (type) {
            MaintenanceType.CAMBIO_OLIO -> 15000
            MaintenanceType.TAGLIANDO -> 20000
            MaintenanceType.FILTRI -> 20000
            MaintenanceType.GOMME -> 40000
            MaintenanceType.FRENI -> 40000
            else -> return null
        }
        return odometerKm + passo
    }
}
