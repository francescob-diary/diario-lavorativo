package it.diario.lavorativo.ui.entries

import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.CommunicationDirection
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.EventType
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import java.time.LocalDate
import java.time.LocalTime

/** Le tre sezioni della giornata. */
enum class EntriesTab { ATTIVITA, EVENTI, COMUNICAZIONI }

data class EntriesUiState(
    val loading: Boolean = true,
    val workDayId: Long = 0L,
    val date: LocalDate = LocalDate.now(),
    val dayExists: Boolean = false,
    val tab: EntriesTab = EntriesTab.ATTIVITA,
    val activities: List<WorkActivity> = emptyList(),
    val events: List<WorkEvent> = emptyList(),
    val communications: List<Communication> = emptyList(),
    /** Scheda aperta in questo momento, null se si sta solo consultando. */
    val editor: EditorState? = null,
    val message: String? = null
) {
    val activityCount: Int get() = activities.size
    val eventCount: Int get() = events.size
    val communicationCount: Int get() = communications.size
    val unresolvedCount: Int get() = events.count { it.unresolved }
}

/** Stato della scheda di inserimento o modifica, uno per tipo di voce. */
sealed interface EditorState {

    data class Activity(
        val id: Long = 0L,
        val category: ActivityCategory = ActivityCategory.ALTRO,
        val description: String = "",
        val startTime: LocalTime? = null,
        val endTime: LocalTime? = null,
        val quantity: String = "",
        val notes: String = "",
        val suggestions: List<String> = emptyList()
    ) : EditorState {
        val canSave: Boolean get() = description.isNotBlank()
        val isNew: Boolean get() = id == 0L
    }

    data class Event(
        val id: Long = 0L,
        val type: EventType = EventType.PROBLEMA,
        val time: LocalTime = LocalTime.of(12, 0),
        val title: String = "",
        val description: String = "",
        val durationMinutes: String = "",
        val severity: EventSeverity = EventSeverity.NORMALE,
        val unresolved: Boolean = false
    ) : EditorState {
        val canSave: Boolean get() = title.isNotBlank()
        val isNew: Boolean get() = id == 0L
    }

    data class Comm(
        val id: Long = 0L,
        val channel: CommunicationChannel = CommunicationChannel.TELEFONATA,
        val direction: CommunicationDirection = CommunicationDirection.RICEVUTA,
        val time: LocalTime = LocalTime.of(12, 0),
        val contactName: String = "",
        val contactRef: String = "",
        val subject: String = "",
        val content: String = "",
        val durationMinutes: String = "",
        val requiresFollowUp: Boolean = false,
        val contactSuggestions: List<String> = emptyList(),
        /** True quando la scheda e' stata aperta da una condivisione. */
        val fromShare: Boolean = false
    ) : EditorState {
        val canSave: Boolean get() = contactName.isNotBlank()
        val isNew: Boolean get() = id == 0L
    }
}
