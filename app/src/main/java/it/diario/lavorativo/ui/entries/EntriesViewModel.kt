package it.diario.lavorativo.ui.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.CommunicationDirection
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.EventType
import it.diario.lavorativo.domain.model.SharedText
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import it.diario.lavorativo.domain.service.SharedTextParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Attivita', eventi e comunicazioni di una giornata.
 *
 * Le tre sezioni condividono il ViewModel perche' condividono la giornata di
 * riferimento e la stessa schermata a schede: tenerle separate avrebbe
 * significato tre volte lo stesso codice di caricamento.
 */
class EntriesViewModel(
    private val entryRepository: DiaryEntryRepository,
    private val workDayRepository: WorkDayRepository,
    private val parser: SharedTextParser,
    private val clock: AppClock,
    private val epochDay: Long
) : ViewModel() {

    private val date: LocalDate = LocalDate.ofEpochDay(epochDay)

    private val _uiState = MutableStateFlow(EntriesUiState(date = date))
    val uiState: StateFlow<EntriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val day = workDayRepository.observeDay(date).first()
            if (day == null) {
                _uiState.update { it.copy(loading = false, dayExists = false) }
                return@launch
            }
            _uiState.update { it.copy(workDayId = day.id, dayExists = true, loading = false) }
            observeAll(day.id)
        }
    }

    private fun observeAll(workDayId: Long) {
        viewModelScope.launch {
            entryRepository.observeActivities(workDayId).collect { list ->
                _uiState.update { it.copy(activities = list) }
            }
        }
        viewModelScope.launch {
            entryRepository.observeEvents(workDayId).collect { list ->
                _uiState.update { it.copy(events = list) }
            }
        }
        viewModelScope.launch {
            entryRepository.observeCommunications(workDayId).collect { list ->
                _uiState.update { it.copy(communications = list) }
            }
        }
    }

    fun selectTab(tab: EntriesTab) = _uiState.update { it.copy(tab = tab) }

    /**
     * Se la giornata non esiste ancora la si crea al primo inserimento.
     * Capita di voler annotare una telefonata ricevuta in un giorno in cui
     * non si e' timbrato: la voce non deve andare persa.
     */
    private suspend fun ensureDay(): Long {
        val current = _uiState.value.workDayId
        if (current != 0L) return current
        val id = workDayRepository.createEmptyDay(date, DayType.LAVORO)
        _uiState.update { it.copy(workDayId = id, dayExists = true) }
        observeAll(id)
        return id
    }

    // ------------------------------------------------------------------
    // Apertura delle schede
    // ------------------------------------------------------------------

    fun newActivity() = viewModelScope.launch {
        val editor = EditorState.Activity(
            suggestions = entryRepository.frequentDescriptions(ActivityCategory.ALTRO)
        )
        _uiState.update { it.copy(editor = editor) }
    }

    fun editActivity(activity: WorkActivity) = viewModelScope.launch {
        val zone = clock.zone()
        _uiState.update {
            it.copy(
                editor = EditorState.Activity(
                    id = activity.id,
                    category = activity.category,
                    description = activity.description,
                    startTime = activity.startTime?.atZone(zone)?.toLocalTime(),
                    endTime = activity.endTime?.atZone(zone)?.toLocalTime(),
                    quantity = activity.quantity.orEmpty(),
                    notes = activity.notes.orEmpty(),
                    suggestions = entryRepository.frequentDescriptions(activity.category)
                )
            )
        }
    }

    fun newEvent() = _uiState.update {
        it.copy(editor = EditorState.Event(time = nowLocalTime()))
    }

    fun editEvent(event: WorkEvent) = _uiState.update {
        it.copy(
            editor = EditorState.Event(
                id = event.id,
                type = event.type,
                time = event.time.atZone(clock.zone()).toLocalTime(),
                title = event.title,
                description = event.description.orEmpty(),
                durationMinutes = event.durationMinutes?.toString().orEmpty(),
                severity = event.severity,
                unresolved = event.unresolved
            )
        )
    }

    fun newCommunication(channel: CommunicationChannel = CommunicationChannel.TELEFONATA) =
        viewModelScope.launch {
            val editor = EditorState.Comm(
                channel = channel,
                time = nowLocalTime(),
                contactSuggestions = entryRepository.frequentContacts()
            )
            _uiState.update { it.copy(editor = editor) }
        }

    fun editCommunication(communication: Communication) = viewModelScope.launch {
        val editor = EditorState.Comm(
            id = communication.id,
            channel = communication.channel,
            direction = communication.direction,
            time = communication.time.atZone(clock.zone()).toLocalTime(),
            contactName = communication.contactName,
            contactRef = communication.contactRef.orEmpty(),
            subject = communication.subject.orEmpty(),
            content = communication.content.orEmpty(),
            durationMinutes = communication.durationMinutes?.toString().orEmpty(),
            requiresFollowUp = communication.requiresFollowUp,
            contactSuggestions = entryRepository.frequentContacts()
        )
        _uiState.update { it.copy(editor = editor) }
    }

    /**
     * Scheda precompilata con un testo arrivato dalla condivisione di sistema.
     * E' il percorso con cui entrano i messaggi WhatsApp e le mail.
     */
    fun openFromShare(shared: SharedText) = viewModelScope.launch {
        val parsed = parser.parse(shared)
        val editor = EditorState.Comm(
            channel = parsed.channel,
            direction = CommunicationDirection.RICEVUTA,
            time = nowLocalTime(),
            contactName = parsed.contactName.orEmpty(),
            subject = parsed.subject.orEmpty(),
            content = parsed.content,
            contactSuggestions = entryRepository.frequentContacts(),
            fromShare = true
        )
        _uiState.update { it.copy(editor = editor, tab = EntriesTab.COMUNICAZIONI) }
    }

    fun closeEditor() = _uiState.update { it.copy(editor = null) }

    // ------------------------------------------------------------------
    // Modifica dei campi
    // ------------------------------------------------------------------

    fun updateActivityEditor(block: (EditorState.Activity) -> EditorState.Activity) =
        _uiState.update { state ->
            val editor = state.editor
            if (editor is EditorState.Activity) state.copy(editor = block(editor)) else state
        }

    fun updateEventEditor(block: (EditorState.Event) -> EditorState.Event) =
        _uiState.update { state ->
            val editor = state.editor
            if (editor is EditorState.Event) state.copy(editor = block(editor)) else state
        }

    fun updateCommEditor(block: (EditorState.Comm) -> EditorState.Comm) =
        _uiState.update { state ->
            val editor = state.editor
            if (editor is EditorState.Comm) state.copy(editor = block(editor)) else state
        }

    /** Cambiando categoria cambiano anche le descrizioni suggerite. */
    fun onActivityCategory(category: ActivityCategory) = viewModelScope.launch {
        val suggestions = entryRepository.frequentDescriptions(category)
        updateActivityEditor { it.copy(category = category, suggestions = suggestions) }
    }

    // ------------------------------------------------------------------
    // Salvataggio
    // ------------------------------------------------------------------

    fun save() = viewModelScope.launch {
        when (val editor = _uiState.value.editor) {
            is EditorState.Activity -> saveActivity(editor)
            is EditorState.Event -> saveEvent(editor)
            is EditorState.Comm -> saveComm(editor)
            null -> Unit
        }
    }

    private suspend fun saveActivity(editor: EditorState.Activity) {
        if (!editor.canSave) return
        val dayId = ensureDay()
        entryRepository.saveActivity(
            WorkActivity(
                id = editor.id,
                workDayId = dayId,
                category = editor.category,
                description = editor.description.trim(),
                startTime = editor.startTime?.let { toInstant(it) },
                endTime = editor.endTime?.let { toInstant(it) },
                quantity = editor.quantity.trim().ifBlank { null },
                notes = editor.notes.trim().ifBlank { null }
            )
        )
        _uiState.update { it.copy(editor = null, message = "Attivita' salvata") }
    }

    private suspend fun saveEvent(editor: EditorState.Event) {
        if (!editor.canSave) return
        val dayId = ensureDay()
        entryRepository.saveEvent(
            WorkEvent(
                id = editor.id,
                workDayId = dayId,
                type = editor.type,
                time = toInstant(editor.time),
                title = editor.title.trim(),
                description = editor.description.trim().ifBlank { null },
                durationMinutes = editor.durationMinutes.trim().toIntOrNull(),
                severity = editor.severity,
                unresolved = editor.unresolved
            )
        )
        _uiState.update { it.copy(editor = null, message = "Evento salvato") }
    }

    private suspend fun saveComm(editor: EditorState.Comm) {
        if (!editor.canSave) return
        val dayId = ensureDay()
        entryRepository.saveCommunication(
            Communication(
                id = editor.id,
                workDayId = dayId,
                channel = editor.channel,
                direction = editor.direction,
                time = toInstant(editor.time),
                contactName = editor.contactName.trim(),
                contactRef = editor.contactRef.trim().ifBlank { null },
                subject = editor.subject.trim().ifBlank { null },
                content = editor.content.ifBlank { null },
                durationMinutes = editor.durationMinutes.trim().toIntOrNull(),
                requiresFollowUp = editor.requiresFollowUp
            )
        )
        _uiState.update { it.copy(editor = null, message = "Comunicazione salvata") }
    }

    // ------------------------------------------------------------------
    // Cancellazioni
    // ------------------------------------------------------------------

    fun deleteActivity(id: Long) = viewModelScope.launch {
        entryRepository.deleteActivity(id)
        _uiState.update { it.copy(message = "Attivita' eliminata") }
    }

    fun deleteEvent(id: Long) = viewModelScope.launch {
        entryRepository.deleteEvent(id)
        _uiState.update { it.copy(message = "Evento eliminato") }
    }

    fun deleteCommunication(id: Long) = viewModelScope.launch {
        entryRepository.deleteCommunication(id)
        _uiState.update { it.copy(message = "Comunicazione eliminata") }
    }

    fun toggleEventUnresolved(event: WorkEvent) = viewModelScope.launch {
        entryRepository.setEventUnresolved(event.id, !event.unresolved)
    }

    fun toggleFollowUp(communication: Communication) = viewModelScope.launch {
        entryRepository.setFollowUp(communication.id, !communication.requiresFollowUp)
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ------------------------------------------------------------------

    private fun nowLocalTime(): LocalTime {
        val today = LocalDate.now(clock.zone())
        // Per una giornata passata l'ora "adesso" non ha senso: si parte da meta' mattina.
        return if (today == date) {
            clock.now().atZone(clock.zone()).toLocalTime().withSecond(0).withNano(0)
        } else {
            LocalTime.of(10, 0)
        }
    }

    private fun toInstant(time: LocalTime): Instant =
        date.atTime(time).atZone(clock.zone()).toInstant()

    companion object {
        fun factory(epochDay: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EntriesViewModel(
                    entryRepository = diarioContainer.diaryEntryRepository,
                    workDayRepository = diarioContainer.workDayRepository,
                    parser = diarioContainer.sharedTextParser,
                    clock = diarioContainer.clock,
                    epochDay = epochDay
                )
            }
        }
    }
}
