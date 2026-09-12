package it.diario.lavorativo.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.audio.AudioPlayer
import it.diario.lavorativo.core.audio.AudioRecorder
import it.diario.lavorativo.core.audio.VoiceStorage
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.VoiceNote
import it.diario.lavorativo.domain.repository.VoiceNoteRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class VoiceUiState(
    val date: LocalDate = LocalDate.now(),
    val notes: List<VoiceNote> = emptyList(),
    val recording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val playingFileName: String? = null,
    val message: String? = null,
    val permissionMissing: Boolean = false,
    val editingId: Long? = null
)

class VoiceNotesViewModel(
    private val epochDay: Long,
    private val repository: VoiceNoteRepository,
    private val workDayRepository: WorkDayRepository,
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
    private val storage: VoiceStorage,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VoiceUiState(date = LocalDate.ofEpochDay(epochDay))
    )
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var workDayId: Long? = null

    init {
        viewModelScope.launch {
            val day = workDayRepository.observeDay(LocalDate.ofEpochDay(epochDay)).first()
            workDayId = day?.id
            day?.let { d ->
                repository.observeForDay(d.id).collect { notes ->
                    _uiState.update { it.copy(notes = notes) }
                }
            }
        }
    }

    fun startRecording() {
        val dayId = workDayId
        if (dayId == null) {
            _uiState.update {
                it.copy(message = "Apri prima la giornata: la nota va agganciata a quella")
            }
            return
        }
        if (_uiState.value.recording) return

        // Riascoltare e registrare insieme non ha senso e sporca l'audio.
        player.stop()

        val target = storage.file(storage.newFileName(clock.now().toEpochMilli()))
        if (recorder.start(target) == null) {
            _uiState.update {
                it.copy(message = "Non riesco ad accendere il microfono", permissionMissing = true)
            }
            return
        }

        _uiState.update {
            it.copy(recording = true, elapsedSeconds = 0, playingFileName = null)
        }

        // Contatore che gira: senza, non si sa se sta registrando davvero.
        viewModelScope.launch {
            while (_uiState.value.recording) {
                delay(500)
                _uiState.update { it.copy(elapsedSeconds = recorder.elapsedSeconds()) }
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.recording) return
        val dayId = workDayId

        val result = recorder.stop()
        _uiState.update { it.copy(recording = false, elapsedSeconds = 0) }

        if (result == null || dayId == null) {
            _uiState.update { it.copy(message = "Registrazione troppo breve, non l'ho salvata") }
            return
        }

        viewModelScope.launch {
            repository.add(
                VoiceNote(
                    workDayId = dayId,
                    fileName = result.file.name,
                    durationSeconds = result.durationSeconds,
                    recordedAt = clock.now()
                )
            )
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        _uiState.update { it.copy(recording = false, elapsedSeconds = 0) }
    }

    fun togglePlay(note: VoiceNote) {
        if (_uiState.value.playingFileName == note.fileName) {
            player.stop()
            _uiState.update { it.copy(playingFileName = null) }
            return
        }

        val ok = player.play(storage.file(note.fileName), note.fileName) {
            _uiState.update { it.copy(playingFileName = null) }
        }

        _uiState.update {
            if (ok) it.copy(playingFileName = note.fileName)
            else it.copy(message = "Questa nota non si riesce a riprodurre")
        }
    }

    fun startEditing(id: Long?) = _uiState.update { it.copy(editingId = id) }

    fun saveTranscript(id: Long, text: String) {
        viewModelScope.launch {
            repository.updateTranscript(id, text)
            _uiState.update { it.copy(editingId = null) }
        }
    }

    fun delete(note: VoiceNote) {
        if (_uiState.value.playingFileName == note.fileName) player.stop()
        viewModelScope.launch {
            repository.delete(note)
            _uiState.update { it.copy(playingFileName = null, message = "Nota eliminata") }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        // Uscendo dalla schermata non deve restare niente acceso: il
        // microfono aperto scarica la batteria e non si vede.
        recorder.cancel()
        player.stop()
        super.onCleared()
    }

    companion object {
        fun factory(epochDay: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VoiceNotesViewModel(
                    epochDay = epochDay,
                    repository = diarioContainer.voiceNoteRepository,
                    workDayRepository = diarioContainer.workDayRepository,
                    recorder = diarioContainer.audioRecorder,
                    player = diarioContainer.audioPlayer,
                    storage = diarioContainer.voiceStorage,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
