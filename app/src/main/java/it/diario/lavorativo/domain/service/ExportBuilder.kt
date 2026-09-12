package it.diario.lavorativo.domain.service

import it.diario.lavorativo.core.time.DurationFormat
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.ExportDocument
import it.diario.lavorativo.domain.model.ExportOptions
import it.diario.lavorativo.domain.model.ExportScope
import it.diario.lavorativo.domain.model.ExportSheet
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.Vehicle
import it.diario.lavorativo.domain.model.VehicleExpense
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Costruisce il documento da esportare.
 *
 * Produce una struttura neutra di tabelle: CSV, XLSX e PDF partono tutti da
 * qui, quindi il foglio di calcolo e il PDF non possono dire numeri diversi.
 *
 * Puro Kotlin, verificabile con test veri.
 */
class ExportBuilder(
    private val calculator: WorkTimeCalculator,
    private val summarizer: PeriodSummarizer,
    /**
     * Lo stesso oggetto che fa i conti nella schermata dell'auto: il
     * documento che va in sede e la schermata devono dire lo stesso
     * numero, e l'unico modo sicuro e' che lo calcoli la stessa riga di
     * codice.
     */
    private val vehicleCalculator: VehicleCalculator = VehicleCalculator()
) {

    fun rangeStart(scope: ExportScope, reference: LocalDate, firstEver: LocalDate?): LocalDate =
        when (scope) {
            ExportScope.SETTIMANA ->
                reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ExportScope.MESE -> reference.withDayOfMonth(1)
            ExportScope.ANNO -> reference.withDayOfYear(1)
            // Senza giornate registrate non c'e' un "tutto": si ripiega
            // sull'anno corrente invece di partire dall'anno zero.
            ExportScope.TUTTO -> firstEver ?: reference.withDayOfYear(1)
        }

    fun rangeEnd(scope: ExportScope, reference: LocalDate, lastEver: LocalDate?): LocalDate =
        when (scope) {
            ExportScope.SETTIMANA ->
                rangeStart(scope, reference, null).plusDays(6)
            ExportScope.MESE -> reference.withDayOfMonth(reference.lengthOfMonth())
            ExportScope.ANNO -> reference.withDayOfYear(reference.lengthOfYear())
            ExportScope.TUTTO -> lastEver ?: reference
        }

    fun build(
        options: ExportOptions,
        from: LocalDate,
        to: LocalDate,
        days: List<WorkDay>,
        activities: Map<Long, List<WorkActivity>> = emptyMap(),
        events: Map<Long, List<WorkEvent>> = emptyMap(),
        communications: Map<Long, List<Communication>> = emptyMap(),
        vehicles: List<Vehicle> = emptyList(),
        fuelStops: List<FuelStop> = emptyList(),
        vehicleExpenses: List<VehicleExpense> = emptyList(),
        maintenances: List<Maintenance> = emptyList(),
        userName: String = "",
        now: Instant,
        standardMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): ExportDocument {
        val ordered = days.sortedBy { it.date }
        val sheets = mutableListOf<ExportSheet>()

        if (options.includeSummary) {
            sheets += summarySheet(ordered, activities, events, now, standardMinutes)
        }
        // Nota: i totali si calcolano solo sulle giornate chiuse, vedi
        // summarySheet. Una giornata mai terminata continuerebbe ad
        // accumulare ore fino a oggi e falserebbe tutto il documento.
        if (options.includeDays) {
            sheets += daysSheet(ordered, activities, now, standardMinutes, zone)
        }
        if (options.includeActivities) {
            sheets += activitiesSheet(ordered, activities, zone)
        }
        if (options.includeEvents) {
            sheets += eventsSheet(ordered, events, zone)
        }
        if (options.includeCommunications) {
            sheets += communicationsSheet(ordered, communications, zone)
        }
        if (options.includeVehicle) {
            // Fogli creati solo se c'e' qualcosa dentro: chi non ha il mezzo
            // aziendale non si ritrova quattro pagine vuote in fondo.
            val riepilogo = vehicleSummarySheet(
                from, to, ordered, vehicles, fuelStops, vehicleExpenses, maintenances
            )
            if (!riepilogo.isEmpty) sheets += riepilogo

            val rifornimenti = fuelSheet(fuelStops, vehicles)
            if (!rifornimenti.isEmpty) sheets += rifornimenti

            val spese = vehicleExpensesSheet(vehicleExpenses, vehicles)
            if (!spese.isEmpty) sheets += spese

            val interventi = maintenanceSheet(maintenances, vehicles)
            if (!interventi.isEmpty) sheets += interventi
        }

        val period = periodLabel(from, to)

        return ExportDocument(
            title = "Diario giornata lavorativa",
            subtitle = if (userName.isBlank()) "" else userName,
            periodLabel = period,
            from = from,
            to = to,
            sheets = sheets,
            fileName = fileName(options.scope, from, to)
        )
    }

    // ------------------------------------------------------------ tabelle

    private fun summarySheet(
        days: List<WorkDay>,
        activities: Map<Long, List<WorkActivity>>,
        events: Map<Long, List<WorkEvent>>,
        now: Instant,
        standardMinutes: Int
    ): ExportSheet {
        // Le giornate mai chiuse restano fuori dai totali: esportando marzo
        // ad aprile, una giornata aperta il 5 marzo avrebbe accumulato
        // ventisette giorni di ore e il foglio avrebbe detto 667 ore.
        // Vengono comunque segnalate in fondo, cosi' chi riceve il
        // documento sa che manca un dato e lo puo' chiedere.
        val closed = days.filter { !it.isRunning }
        val totals = summarizer.totals(closed, now, standardMinutes)
        val rows = mutableListOf<List<String>>()

        rows += listOf("Giorni lavorati", totals.workedDays.toString())
        rows += listOf("Ore nette", DurationFormat.short(totals.net))
        rows += listOf("Ore lorde", DurationFormat.short(totals.net.plus(totals.breaks)))
        rows += listOf("Pause", DurationFormat.short(totals.breaks))
        rows += listOf("Straordinario", DurationFormat.short(totals.overtime))
        rows += listOf("Media al giorno", DurationFormat.short(totals.averageNet))

        totals.absenceDays.forEach { (type, count) ->
            rows += listOf(absenceLabel(type), count.toString())
        }

        val cantieri = days
            .filter { it.dayType == DayType.LAVORO && it.isStarted }
            .mapNotNull { it.site?.name }
            .distinct()
        if (cantieri.isNotEmpty()) {
            rows += listOf("Cantieri", cantieri.joinToString(", "))
        }

        val numeroLavorazioni = days.sumOf { activities[it.id].orEmpty().size }
        if (numeroLavorazioni > 0) {
            rows += listOf("Lavorazioni registrate", numeroLavorazioni.toString())
        }

        val aperti = days.sumOf { events[it.id].orEmpty().count { e -> e.unresolved } }
        if (aperti > 0) {
            rows += listOf("Questioni ancora aperte", aperti.toString())
        }

        // Le giornate mai chiuse vanno segnalate: chi riceve il foglio deve
        // sapere che quel numero non e' definitivo.
        val incomplete = days.count { it.isRunning }
        if (incomplete > 0) {
            rows += listOf("Giornate senza orario di uscita", incomplete.toString())
        }

        return ExportSheet(
            name = "Riepilogo",
            headers = listOf("Voce", "Valore"),
            rows = rows
        )
    }

    private fun daysSheet(
        days: List<WorkDay>,
        activities: Map<Long, List<WorkActivity>>,
        now: Instant,
        standardMinutes: Int,
        zone: ZoneId
    ): ExportSheet {
        val rows = days.map { day ->
            val s = calculator.summarize(day, now, standardMinutes)
            val lavoro = day.description?.takeIf { it.isNotBlank() }
                ?: activities[day.id].orEmpty()
                    .joinToString("; ") { it.description }

            // Ferie, permessi, malattia e festivi non portano ore: le
            // caselle restano vuote invece di mostrare numeri che nelle
            // Statistiche valgono zero. Due schermate della stessa app non
            // possono dire cose diverse sullo stesso giorno.
            //
            // Anche la giornata aperta lascia le caselle vuote: non
            // sappiamo a che ora e' finita, e inventarlo sarebbe peggio
            // che ammettere che manca.
            val mostraOre = day.dayType == DayType.LAVORO && !day.isRunning

            listOf(
                day.date.format(DATE),
                day.date.dayOfWeek.getDisplayName(
                    java.time.format.TextStyle.SHORT, Locale.ITALIAN
                ).replaceFirstChar { it.uppercase() },
                dayTypeLabel(day.dayType),
                day.site?.name.orEmpty(),
                if (day.dayType == DayType.LAVORO) {
                    day.startTime?.let { time(it, zone) }.orEmpty()
                } else {
                    ""
                },
                when {
                    day.dayType != DayType.LAVORO -> ""
                    day.isRunning -> "in corso"
                    else -> day.endTime?.let { time(it, zone) }.orEmpty()
                },
                if (mostraOre) DurationFormat.short(s.breaks) else "",
                if (mostraOre) DurationFormat.short(s.gross) else "",
                if (mostraOre) DurationFormat.short(s.net) else "",
                if (mostraOre) DurationFormat.short(s.overtime) else "",
                // Chilometri: la casella resta vuota quando non sono stati
                // segnati. Uno zero direbbe "non mi sono mosso", che e'
                // un'altra cosa.
                day.travelKm?.toString().orEmpty(),
                lavoro,
                day.notes.orEmpty()
            )
        }

        return ExportSheet(
            name = "Giornate",
            headers = listOf(
                "Data", "Giorno", "Tipo", "Cantiere", "Ingresso", "Uscita",
                "Pause", "Lordo", "Netto", "Straordinario", "Km", "Lavoro svolto", "Note"
            ),
            rows = rows
        )
    }

    private fun activitiesSheet(
        days: List<WorkDay>,
        activities: Map<Long, List<WorkActivity>>,
        zone: ZoneId
    ): ExportSheet {
        val rows = mutableListOf<List<String>>()
        days.forEach { day ->
            activities[day.id].orEmpty().forEach { a ->
                rows += listOf(
                    day.date.format(DATE),
                    day.site?.name.orEmpty(),
                    categoryLabel(a.category),
                    a.description,
                    a.quantity.orEmpty(),
                    a.startTime?.let { time(it, zone) }.orEmpty(),
                    a.endTime?.let { time(it, zone) }.orEmpty(),
                    a.notes.orEmpty()
                )
            }
        }

        return ExportSheet(
            name = "Lavorazioni",
            headers = listOf(
                "Data", "Cantiere", "Tipo", "Descrizione", "Quantita",
                "Dalle", "Alle", "Note"
            ),
            rows = rows
        )
    }

    private fun eventsSheet(
        days: List<WorkDay>,
        events: Map<Long, List<WorkEvent>>,
        zone: ZoneId
    ): ExportSheet {
        val rows = mutableListOf<List<String>>()
        days.forEach { day ->
            events[day.id].orEmpty().forEach { e ->
                rows += listOf(
                    day.date.format(DATE),
                    time(e.time, zone),
                    day.site?.name.orEmpty(),
                    eventTypeLabel(e.type),
                    severityLabel(e.severity),
                    e.title,
                    e.description.orEmpty(),
                    e.durationMinutes?.toString().orEmpty(),
                    if (e.unresolved) "DA RISOLVERE" else "risolto"
                )
            }
        }

        return ExportSheet(
            name = "Eventi",
            headers = listOf(
                "Data", "Ora", "Cantiere", "Tipo", "Gravita", "Cosa e' successo",
                "Dettagli", "Minuti persi", "Stato"
            ),
            rows = rows
        )
    }

    private fun communicationsSheet(
        days: List<WorkDay>,
        communications: Map<Long, List<Communication>>,
        zone: ZoneId
    ): ExportSheet {
        val rows = mutableListOf<List<String>>()
        days.forEach { day ->
            communications[day.id].orEmpty().forEach { c ->
                rows += listOf(
                    day.date.format(DATE),
                    time(c.time, zone),
                    channelLabel(c.channel),
                    directionLabel(c.direction),
                    c.contactName,
                    c.contactRef.orEmpty(),
                    c.subject.orEmpty(),
                    c.content.orEmpty(),
                    c.durationMinutes?.toString().orEmpty(),
                    if (c.requiresFollowUp) "DA RICHIAMARE" else ""
                )
            }
        }

        return ExportSheet(
            name = "Comunicazioni",
            headers = listOf(
                "Data", "Ora", "Come", "Verso", "Chi", "Riferimento",
                "Oggetto", "Contenuto", "Minuti", "Seguito"
            ),
            rows = rows
        )
    }

    // ------------------------------------------------------------ etichette

    fun periodLabel(from: LocalDate, to: LocalDate): String = when {
        from == to -> from.format(LONG_DATE)
        from.year == to.year && from.month == to.month &&
            from.dayOfMonth == 1 && to.dayOfMonth == to.lengthOfMonth() ->
            from.format(MONTH_YEAR).replaceFirstChar { it.uppercase() }
        from.dayOfYear == 1 && to.dayOfYear == to.lengthOfYear() && from.year == to.year ->
            "Anno " + from.year.toString()
        else -> from.format(DATE) + " - " + to.format(DATE)
    }

    // ---------------------------------------------------- mezzo aziendale

    private fun vehicleSummarySheet(
        from: LocalDate,
        to: LocalDate,
        days: List<WorkDay>,
        vehicles: List<Vehicle>,
        stops: List<FuelStop>,
        expenses: List<VehicleExpense>,
        maintenances: List<Maintenance>
    ): ExportSheet {
        // I chilometri delle giornate si sommano solo dove sono stati
        // segnati davvero: le giornate senza il dato non contano come zero.
        val kmGiornate = days.mapNotNull { it.travelKm }.sum()

        val totali = vehicleCalculator.totals(
            from = from,
            to = to,
            stops = stops,
            expenses = expenses,
            maintenances = maintenances,
            travelKm = kmGiornate
        )

        if (totali.isEmpty) {
            return ExportSheet("Mezzo", listOf("Voce", "Valore"), emptyList())
        }

        val rows = mutableListOf<List<String>>()

        val nomeMezzo = vehicles.firstOrNull { it.active }?.label
            ?: vehicles.firstOrNull()?.label
        if (nomeMezzo != null) rows += listOf("Mezzo", nomeMezzo)

        if (kmGiornate > 0) rows += listOf("Km segnati sulle giornate", kmGiornate.toString())
        if (totali.odometerKm > 0) {
            rows += listOf("Km dal contachilometri", totali.odometerKm.toString())
        }
        if (totali.fuelStops > 0) {
            rows += listOf("Rifornimenti", totali.fuelStops.toString())
            rows += listOf("Litri", decimal(totali.liters, 2))
            rows += listOf("Spesa carburante", euro(totali.fuelCents))
            rows += listOf("Prezzo medio al litro", decimal(totali.averagePricePerLiter, 3))
        }
        if (totali.consumptions.isNotEmpty()) {
            rows += listOf("Consumo medio (l/100 km)", decimal(totali.averageLitersPer100Km, 2))
        }
        if (totali.tollCents > 0) rows += listOf("Pedaggi", euro(totali.tollCents))
        if (totali.parkingCents > 0) rows += listOf("Parcheggi", euro(totali.parkingCents))
        if (totali.otherExpenseCents > 0) {
            rows += listOf("Altre spese", euro(totali.otherExpenseCents))
        }
        if (totali.maintenanceCount > 0) {
            rows += listOf("Interventi in officina", totali.maintenanceCount.toString())
            rows += listOf("Spesa manutenzione", euro(totali.maintenanceCents))
        }
        rows += listOf("Totale spese mezzo", euro(totali.totalCents))
        if (totali.effectiveKm > 0) {
            rows += listOf("Costo al km", euro(Math.round(totali.costPerKmCents)))
        }

        return ExportSheet(
            name = "Mezzo",
            headers = listOf("Voce", "Valore"),
            rows = rows
        )
    }

    private fun fuelSheet(stops: List<FuelStop>, vehicles: List<Vehicle>): ExportSheet {
        val nomi = vehicles.associate { it.id to it.label }
        val rows = stops.sortedBy { it.date }.map { r ->
            listOf(
                r.date.format(DATE),
                nomi[r.vehicleId].orEmpty(),
                decimal(r.liters, 2),
                euro(r.amountCents),
                r.pricePerLiter?.let { decimal(it, 3) }.orEmpty(),
                r.odometerKm?.toString().orEmpty(),
                if (r.fullTank) "Pieno" else "Parziale",
                r.station.orEmpty(),
                r.notes.orEmpty()
            )
        }
        return ExportSheet(
            name = "Rifornimenti",
            headers = listOf(
                "Data", "Mezzo", "Litri", "Importo", "Prezzo al litro",
                "Km", "Tipo", "Distributore", "Note"
            ),
            rows = rows
        )
    }

    private fun vehicleExpensesSheet(
        expenses: List<VehicleExpense>,
        vehicles: List<Vehicle>
    ): ExportSheet {
        val nomi = vehicles.associate { it.id to it.label }
        val rows = expenses.sortedBy { it.date }.map { e ->
            listOf(
                e.date.format(DATE),
                nomi[e.vehicleId].orEmpty(),
                e.type.label,
                euro(e.amountCents),
                e.place.orEmpty(),
                e.notes.orEmpty()
            )
        }
        return ExportSheet(
            name = "Pedaggi e parcheggi",
            headers = listOf("Data", "Mezzo", "Tipo", "Importo", "Luogo", "Note"),
            rows = rows
        )
    }

    private fun maintenanceSheet(
        maintenances: List<Maintenance>,
        vehicles: List<Vehicle>
    ): ExportSheet {
        val nomi = vehicles.associate { it.id to it.label }
        val rows = maintenances.sortedBy { it.date }.map { m ->
            listOf(
                m.date.format(DATE),
                nomi[m.vehicleId].orEmpty(),
                m.type.label,
                m.description.orEmpty(),
                m.odometerKm?.toString().orEmpty(),
                m.amountCents?.let { euro(it) }.orEmpty(),
                m.workshop.orEmpty(),
                m.nextDueDate?.format(DATE).orEmpty(),
                m.nextDueKm?.toString().orEmpty(),
                m.notes.orEmpty()
            )
        }
        return ExportSheet(
            name = "Manutenzioni",
            headers = listOf(
                "Data", "Mezzo", "Intervento", "Descrizione", "Km", "Costo",
                "Officina", "Prossima scadenza", "Prossimi km", "Note"
            ),
            rows = rows
        )
    }

    /**
     * Importo in euro con la virgola, come si scrive in Italia. Parte dai
     * centesimi interi, quindi il totale della colonna torna sempre con la
     * somma degli scontrini.
     */
    private fun euro(cents: Long): String {
        val segno = if (cents < 0) "-" else ""
        val assoluto = Math.abs(cents)
        return segno + (assoluto / 100).toString() + "," +
            (assoluto % 100).toString().padStart(2, '0')
    }

    /** Numero con la virgola al posto del punto: Excel italiano vuole cosi'. */
    private fun decimal(value: Double, decimals: Int): String =
        String.format(Locale.ITALIAN, "%." + decimals + "f", value)

    /**
     * Nome del file. Va bene per un allegato e resta ordinato in cartella:
     * "diario-2026-03" si mette in fila da solo per data.
     */
    fun fileName(scope: ExportScope, from: LocalDate, to: LocalDate): String = when (scope) {
        ExportScope.SETTIMANA -> "diario-settimana-" + from.format(COMPACT)
        ExportScope.MESE -> "diario-" + from.format(MONTH_COMPACT)
        ExportScope.ANNO -> "diario-" + from.year.toString()
        ExportScope.TUTTO -> "diario-" + from.format(COMPACT) + "-" + to.format(COMPACT)
    }

    private fun time(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).toLocalTime().format(TIME)

    private fun dayTypeLabel(type: DayType): String = when (type) {
        DayType.LAVORO -> "Lavoro"
        DayType.FERIE -> "Ferie"
        DayType.PERMESSO -> "Permesso"
        DayType.MALATTIA -> "Malattia"
        DayType.FESTIVO -> "Festivo"
    }

    private fun absenceLabel(type: DayType): String = when (type) {
        DayType.FERIE -> "Giorni di ferie"
        DayType.PERMESSO -> "Permessi"
        DayType.MALATTIA -> "Giorni di malattia"
        DayType.FESTIVO -> "Festivi"
        DayType.LAVORO -> "Giornate"
    }

    private fun categoryLabel(c: it.diario.lavorativo.domain.model.ActivityCategory): String =
        c.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun eventTypeLabel(t: it.diario.lavorativo.domain.model.EventType): String =
        t.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun severityLabel(s: it.diario.lavorativo.domain.model.EventSeverity): String =
        s.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun channelLabel(c: it.diario.lavorativo.domain.model.CommunicationChannel): String =
        c.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun directionLabel(d: it.diario.lavorativo.domain.model.CommunicationDirection): String =
        d.name.lowercase().replaceFirstChar { it.uppercase() }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)
        val LONG_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
        val MONTH_YEAR: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
        val MONTH_COMPACT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM", Locale.ITALIAN)
        val COMPACT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ITALIAN)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)
    }
}
