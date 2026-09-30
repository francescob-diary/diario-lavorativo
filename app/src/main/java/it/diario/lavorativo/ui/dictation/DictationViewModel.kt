package it.diario.lavorativo.ui.dictation

import java.time.ZoneId
import it.diario.lavorativo.domain.model.WorkDay
import it.diario.lavorativo.domain.dictation.TextCleaner
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.llm.LlmProgress
import it.diario.lavorativo.core.llm.LlmResult
import it.diario.lavorativo.core.llm.LocalLlm
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.dictation.DictationDefaults
import it.diario.lavorativo.domain.dictation.DictationDraft
import it.diario.lavorativo.domain.dictation.DictationPrompt
import it.diario.lavorativo.domain.dictation.DictationResponseParser
import it.diario.lavorativo.domain.dictation.DraftSite
import it.diario.lavorativo.domain.dictation.DraftTrip
import it.diario.lavorativo.domain.dictation.RuleBasedDayParser
import it.diario.lavorativo.domain.model.BreakType
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.Trip
import it.diario.lavorativo.domain.repository.SettingsRepository
import it.diario.lavorativo.domain.repository.SiteRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.TimeTextParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A che punto si e'. */
enum class DictationStage { RACCONTO, ELABORAZIONE, DOMANDE, CONFERMA, SALVATO }

// ------------------------------------------------ modulo della conferma

/** Righe del modulo: tutto testo, cosi' si corregge a mano senza sorprese. */
data class FormBreak(val start: String = "", val end: String = "")

data class FormSite(
    val siteId: Long? = null,
    val name: String = "",
    val hours: String = "",
    val work: String = ""
)

data class FormTrip(
    val from: String = "",
    val to: String = "",
    val depart: String = "",
    val arrive: String = ""
)

data class DictationForm(
    val dayType: DayType = DayType.LAVORO,
    val start: String = "",
    val end: String = "",
    val breaks: List<FormBreak> = emptyList(),
    val sites: List<FormSite> = emptyList(),
    val description: String = "",
    val materials: String = "",
    val place: String = "",
    val trips: List<FormTrip> = emptyList(),
    val km: String = "",
    val notes: String = ""
) {
    private fun badTime(t: String) = t.isNotBlank() && TimeTextParser.parseClock(t) == null

    /** Campi scritti in un modo che non si capisce: vanno sistemati prima di salvare. */
    val errors: List<String>
        get() = buildList {
            if (badTime(start)) add("Ingresso")
            if (badTime(end)) add("Uscita")
            breaks.forEachIndexed { i, b ->
                if (badTime(b.start) || badTime(b.end)) add("Pausa " + (i + 1).toString())
            }
            sites.drop(1).forEachIndexed { i, s ->
                if (s.hours.isNotBlank() && TimeTextParser.parseHours(s.hours) == null) {
                    add("Ore cantiere " + (i + 2).toString())
                }
            }
            trips.forEachIndexed { i, t ->
                if (badTime(t.depart) || badTime(t.arrive)) add("Spostamento " + (i + 1).toString())
            }
            if (km.isNotBlank() && km.trim().toIntOrNull() == null) add("Chilometri")
        }
}

data class DictationUiState(
    val date: LocalDate = LocalDate.now(),
    val stage: DictationStage = DictationStage.RACCONTO,
    val transcript: String = "",
    val progress: LlmProgress? = null,
    val usedModel: Boolean = false,
    /** Tempi dell'ultima elaborazione col modello, da mostrare sotto. */
    val timings: String? = null,
    val hasModel: Boolean = false,
    val form: DictationForm = DictationForm(),
    val questions: List<String> = emptyList(),
    val answers: List<Pair<String, String>> = emptyList(),
    val sites: List<Site> = emptyList(),
    val message: String? = null,
    /** La giornata e' gia' nel diario: quello che si racconta si aggiunge. */
    val appending: Boolean = false,
    /** Si e' ripresa una dettatura lasciata a meta'. */
    val restored: Boolean = false,
    /** Perche' Gemma non ha lavorato, se non ha lavorato. Si mostra ben visibile. */
    val modelIssue: String? = null
) {
    val currentQuestion: String? get() = questions.firstOrNull()
}

/**
 * La dettatura della giornata.
 *
 * 1. Si racconta a voce (il testo si puo' anche correggere a mano).
 * 2. Il modello sul telefono lo legge e compila i campi; se il modello
 *    non c'e', lo fanno delle regole semplici.
 * 3. Se mancano dati importanti, l'app li chiede (al massimo tre domande).
 * 4. Schermata di conferma: si controlla tutto, si corregge, si salva.
 *    Niente va nel diario senza SALVA.
 */
class DictationViewModel(
    private val workDayRepository: WorkDayRepository,
    private val siteRepository: SiteRepository,
    private val settingsRepository: SettingsRepository,
    private val llm: LocalLlm,
    private val clock: AppClock,
    private val drafts: DictationDraftStore,
    private val epochDay: Long
) : ViewModel() {

    /** La giornata come e' nel diario, se c'e' gia': base per aggiungere. */
    private var existingForm: DictationForm? = null

    private val date: LocalDate = LocalDate.ofEpochDay(epochDay)

    private val _uiState = MutableStateFlow(DictationUiState(date = date))
    val uiState: StateFlow<DictationUiState> = _uiState.asStateFlow()

    /** Domande gia' fatte: non piu' di tre in tutto, per non diventare un interrogatorio. */
    private var askedCount = 0

    init {
        viewModelScope.launch {
            siteRepository.observeActiveSites().collect { list ->
                _uiState.update { it.copy(sites = list) }
            }
        }
        viewModelScope.launch {
            settingsRepository.modelUri.collect { uri ->
                _uiState.update { it.copy(hasModel = uri != null) }
            }
        }
        viewModelScope.launch {
            // La giornata gia' salvata, se c'e': si parte da li'.
            val giorno = workDayRepository.getDay(date)
            if (giorno != null && (giorno.isStarted || giorno.site != null || !giorno.description.isNullOrBlank() ||
                    giorno.dayType != DayType.LAVORO)
            ) {
                existingForm = giorno.toForm(clock.zone())
                _uiState.update { it.copy(appending = true) }
            }
            // Dettatura lasciata a meta': si riprende esattamente da dove era.
            drafts.load(epochDay)?.let { b ->
                _uiState.update {
                    it.copy(
                        stage = b.stage,
                        transcript = b.transcript,
                        answers = b.answers,
                        form = b.form,
                        usedModel = b.usedModel,
                        appending = b.appending || it.appending,
                        restored = true
                    )
                }
            }
            // Da qui in poi ogni cambiamento resta scritto sul telefono.
            _uiState.collect { drafts.save(epochDay, it) }
        }
    }

    /** Butta la dettatura in corso e riparte da capo. */
    fun restart() {
        drafts.clear(epochDay)
        askedCount = 0
        _uiState.update {
            it.copy(
                stage = DictationStage.RACCONTO,
                transcript = "",
                answers = emptyList(),
                questions = emptyList(),
                form = DictationForm(),
                usedModel = false,
                restored = false,
                modelIssue = null
            )
        }
    }

    /** Giornata gia' salvata: si va direttamente a correggerla, a mano o col microfono. */
    fun editExisting() {
        val base = existingForm ?: return
        _uiState.update { it.copy(form = base, stage = DictationStage.CONFERMA) }
    }

    fun setTranscript(text: String) = _uiState.update { it.copy(transcript = text) }

    /** Il racconto e' finito: si passa a capire cosa c'e' dentro. */
    fun analyze() = viewModelScope.launch {
        val transcript = _uiState.value.transcript.trim()
        if (transcript.isEmpty()) {
            _uiState.update { it.copy(message = "Racconta prima la giornata") }
            return@launch
        }
        _uiState.update { it.copy(stage = DictationStage.ELABORAZIONE, progress = null) }

        val sites = siteRepository.observeActiveSites().first()
        val regole = RuleBasedDayParser.parse(fullText(), sites)
        val uri = settingsRepository.modelUri.first()

        var usato = false
        var problema: String? = when {
            uri == null -> "Gemma non e' scelto (Impostazioni > Dettatura): ho usato le regole semplici."
            !llm.isAvailable() -> (llm.unavailableReason() ?: "Gemma non parte") +
                ": ho usato le regole semplici."
            else -> null
        }
        val draft: DictationDraft = if (uri != null && llm.isAvailable()) {
            val prompt = DictationPrompt.build(
                transcript = transcript,
                date = date,
                siteNames = sites.map { it.name },
                previousAnswers = _uiState.value.answers
            )
            when (val r = llm.generate(Uri.parse(uri), prompt) { p ->
                _uiState.update { it.copy(progress = p) }
            }) {
                is LlmResult.Ok -> {
                    _uiState.update {
                        it.copy(timings = "Tempo " + r.timings.total.toString() + "s (" + r.timings.label + ")")
                    }
                    val letto = DictationResponseParser.parse(r.text, sites)
                    if (letto != null) {
                        usato = true
                        RuleBasedDayParser.merge(letto, regole)
                    } else {
                        problema = "Gemma ha risposto in un modo che non capisco: ho usato le regole semplici."
                        regole
                    }
                }
                is LlmResult.Failure -> {
                    problema = r.reason + ": ho usato le regole semplici."
                    regole
                }
            }
        } else {
            regole
        }

        // Domande: solo se non se ne sono gia' fatte troppe. Sugli orari
        // non si chiede mai: se non sono stati detti valgono quelli standard.
        val spazio = (MAX_QUESTIONS - askedCount).coerceAtLeast(0)
        val domande = draft.questions
            .filter { q -> _uiState.value.answers.none { it.first.equals(q, ignoreCase = true) } }
            .filterNot { DictationDefaults.isAboutTimes(it) }
            .take(minOf(2, spazio))

        // Il racconto ripulito: punti, maiuscole, niente ripetizioni. Senza
        // Gemma il lavoro svolto e' il racconto stesso, ripulito.
        val pulito = draft.copy(
            description = (draft.description ?: if (!usato) transcript else null)
                ?.let { TextCleaner.clean(it) }?.ifBlank { null },
            notes = draft.notes?.let { TextCleaner.clean(it) }?.ifBlank { null }
        )
        val base = existingForm
        val nuovoModulo = if (_uiState.value.appending && base != null) {
            mergeForms(base, pulito.toForm())
        } else {
            DictationDefaults.apply(pulito).toForm()
        }

        _uiState.update {
            it.copy(
                usedModel = usato,
                modelIssue = problema,
                progress = null,
                form = nuovoModulo,
                questions = domande,
                stage = if (domande.isEmpty()) DictationStage.CONFERMA else DictationStage.DOMANDE
            )
        }
    }

    /** Racconto piu' le risposte alle domande, per le regole semplici. */
    private fun fullText(): String {
        val s = _uiState.value
        return (listOf(s.transcript) + s.answers.map { it.second }).joinToString(". ")
    }

    /** Risposta a voce alla domanda corrente. */
    fun answer(text: String) {
        val q = _uiState.value.currentQuestion ?: return
        askedCount++
        val risposta = text.trim()
        _uiState.update {
            it.copy(
                answers = if (risposta.isEmpty()) it.answers else it.answers + (q to risposta),
                questions = it.questions.drop(1)
            )
        }
        if (_uiState.value.questions.isEmpty()) {
            if (_uiState.value.answers.isEmpty()) {
                _uiState.update { it.copy(stage = DictationStage.CONFERMA) }
            } else {
                analyze()
            }
        }
    }

    fun skipQuestions() {
        askedCount = MAX_QUESTIONS
        if (_uiState.value.answers.isNotEmpty()) {
            _uiState.update { it.copy(questions = emptyList()) }
            analyze()
        } else {
            _uiState.update { it.copy(questions = emptyList(), stage = DictationStage.CONFERMA) }
        }
    }

    fun cancelProcessing() {
        llm.cancel()
    }

    fun backToStory() = _uiState.update { it.copy(stage = DictationStage.RACCONTO) }

    fun updateForm(block: (DictationForm) -> DictationForm) =
        _uiState.update { it.copy(form = block(it.form)) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ------------------------------------------------------ salvataggio

    fun save() = viewModelScope.launch {
        val f = _uiState.value.form
        if (f.errors.isNotEmpty()) {
            _uiState.update { it.copy(message = "Controlla: " + f.errors.joinToString(", ")) }
            return@launch
        }
        val zone = clock.zone()
        fun at(d: LocalDate, t: LocalTime) = d.atTime(t).atZone(zone).toInstant()

        val esistente = workDayRepository.getDay(date)
        val id = esistente?.id ?: workDayRepository.createEmptyDay(date, f.dayType)
        workDayRepository.updateDayType(id, f.dayType)

        if (f.dayType == DayType.LAVORO) {
            val inizio = TimeTextParser.parseClock(f.start)
            val fine = TimeTextParser.parseClock(f.end)
            val start = inizio?.let { at(date, it) } ?: esistente?.startTime
            val end = fine?.let { t ->
                // Uscita prima dell'ingresso: turno oltre la mezzanotte.
                if (inizio != null && t.isBefore(inizio)) at(date.plusDays(1), t) else at(date, t)
            } ?: esistente?.endTime
            workDayRepository.updateDayTimes(id, start, end)

            // Le pause confermate sostituiscono quelle che c'erano.
            esistente?.breaks?.forEach { workDayRepository.deleteBreak(it.id) }
            f.breaks.forEach { b ->
                val da = TimeTextParser.parseClock(b.start)
                val a = TimeTextParser.parseClock(b.end)
                if (da != null && a != null && a.isAfter(da)) {
                    val tipo = if (da.hour in 11..14) BreakType.PRANZO else BreakType.PAUSA
                    workDayRepository.addBreak(id, at(date, da), at(date, a), tipo)
                }
            }

            // Cantieri: il primo e' il principale, gli altri con le loro ore.
            val ids = f.sites.filter { it.name.isNotBlank() || it.siteId != null }
                .map { s -> s to (s.siteId ?: createSite(s.name)) }
            workDayRepository.updateSite(id, ids.firstOrNull()?.second ?: esistente?.site?.id)
            esistente?.extraSites?.forEach { workDayRepository.deleteExtraSite(it.id) }
            ids.drop(1).forEach { (s, siteId) ->
                workDayRepository.addExtraSite(
                    id, siteId, TimeTextParser.parseHours(s.hours) ?: 0, s.work.ifBlank { null }
                )
            }
        }

        val descrizione = listOf(
            f.description.trim(),
            f.materials.trim().takeIf { it.isNotEmpty() }?.let { "Materiali: $it" }.orEmpty()
        ).filter { it.isNotEmpty() }.joinToString(". ").ifBlank { null }
        workDayRepository.updateNotes(
            id,
            descrizione ?: esistente?.description,
            f.notes.trim().ifBlank { null } ?: esistente?.notes
        )
        workDayRepository.updatePlace(id, f.place.ifBlank { null } ?: esistente?.place)
        f.km.trim().toIntOrNull()?.let { workDayRepository.updateTravelKm(id, it) }

        // Spostamenti confermati: sostituiscono quelli di prima.
        esistente?.trips?.forEach { workDayRepository.deleteTrip(it.id) }
        f.trips.filter { it.from.isNotBlank() || it.to.isNotBlank() }.forEach { t ->
            val dep = TimeTextParser.parseClock(t.depart)
            val arr = TimeTextParser.parseClock(t.arrive)
            workDayRepository.saveTrip(
                Trip(
                    workDayId = id,
                    departTime = dep?.let { at(date, it) },
                    arriveTime = arr?.let { a ->
                        if (dep != null && a.isBefore(dep)) at(date.plusDays(1), a) else at(date, a)
                    },
                    fromPlace = t.from.trim().ifBlank { null },
                    toPlace = t.to.trim().ifBlank { null }
                )
            )
        }

        _uiState.update { it.copy(stage = DictationStage.SALVATO, message = "Giornata salvata") }
    }

    private suspend fun createSite(name: String): Long =
        siteRepository.upsert(Site(name = name.trim()))

    companion object {
        private const val MAX_QUESTIONS = 3

        fun factory(epochDay: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DictationViewModel(
                    workDayRepository = diarioContainer.workDayRepository,
                    siteRepository = diarioContainer.siteRepository,
                    settingsRepository = diarioContainer.settingsRepository,
                    llm = diarioContainer.localLlm,
                    clock = diarioContainer.clock,
                    drafts = diarioContainer.dictationDrafts,
                    epochDay = epochDay
                )
            }
        }
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun LocalTime?.label(): String = this?.format(HHMM).orEmpty()

/** Dalla bozza capita al modulo da confermare. */
fun DictationDraft.toForm(): DictationForm = DictationForm(
    dayType = dayType,
    start = start.label(),
    end = end.label(),
    breaks = breaks.map { FormBreak(it.start.label(), it.end.label()) },
    sites = sites.map { s: DraftSite ->
        FormSite(
            siteId = s.siteId,
            name = s.name,
            hours = s.minutes?.let { TimeTextParser.formatHours(it) }.orEmpty(),
            work = s.work.orEmpty()
        )
    },
    description = description.orEmpty(),
    materials = materials.orEmpty(),
    place = place.orEmpty(),
    trips = trips.map { t: DraftTrip ->
        FormTrip(t.from.orEmpty(), t.to.orEmpty(), t.depart.label(), t.arrive.label())
    },
    km = km?.toString().orEmpty(),
    notes = notes.orEmpty()
)

/**
 * Unisce quello che si e' appena raccontato alla giornata gia' salvata:
 * gli orari e le pause detti ora sostituiscono quelli di prima, il resto
 * si aggiunge (lavoro, note, spostamenti, cantieri nuovi).
 */
internal fun mergeForms(base: DictationForm, nuovo: DictationForm): DictationForm {
    fun unisci(a: String, b: String): String = when {
        b.isBlank() -> a
        a.isBlank() -> b
        a.contains(b, ignoreCase = true) -> a
        else -> a.trimEnd() + " " + b.trim()
    }
    val cantieri = base.sites + nuovo.sites.filter { n ->
        base.sites.none { b ->
            (n.siteId != null && n.siteId == b.siteId) || b.name.equals(n.name, ignoreCase = true)
        }
    }
    return base.copy(
        dayType = if (nuovo.dayType != DayType.LAVORO) nuovo.dayType else base.dayType,
        start = nuovo.start.ifBlank { base.start },
        end = nuovo.end.ifBlank { base.end },
        breaks = nuovo.breaks.ifEmpty { base.breaks },
        sites = cantieri,
        description = unisci(base.description, nuovo.description),
        materials = unisci(base.materials, nuovo.materials),
        place = nuovo.place.ifBlank { base.place },
        trips = base.trips + nuovo.trips,
        km = nuovo.km.ifBlank { base.km },
        notes = unisci(base.notes, nuovo.notes)
    )
}

/** La giornata del diario nel modulo della dettatura, per correggerla o aggiungere. */
internal fun WorkDay.toForm(zone: ZoneId): DictationForm {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    fun label(i: java.time.Instant?) = i?.atZone(zone)?.toLocalTime()?.format(fmt).orEmpty()
    // Il lavoro svolto e' salvato come "testo. Materiali: ...": si separa.
    val desc = description.orEmpty()
    val idx = desc.indexOf("Materiali: ")
    val lavoro = if (idx >= 0) desc.substring(0, idx).trim().trimEnd('.').let { if (it.isEmpty()) it else "$it." } else desc
    val materiali = if (idx >= 0) desc.substring(idx + "Materiali: ".length).trim() else ""
    return DictationForm(
        dayType = dayType,
        start = label(startTime),
        end = label(endTime),
        breaks = breaks.filter { it.endTime != null }.map { FormBreak(label(it.startTime), label(it.endTime)) },
        sites = listOfNotNull(site?.let { FormSite(it.id, it.name) }) +
            extraSites.map { FormSite(it.site.id, it.site.name, TimeTextParser.formatHours(it.minutes), it.description.orEmpty()) },
        description = lavoro,
        materials = materiali,
        place = place.orEmpty(),
        trips = trips.map { FormTrip(it.fromPlace.orEmpty(), it.toPlace.orEmpty(), label(it.departTime), label(it.arriveTime)) },
        km = travelKm?.toString().orEmpty(),
        notes = notes.orEmpty()
    )
}
