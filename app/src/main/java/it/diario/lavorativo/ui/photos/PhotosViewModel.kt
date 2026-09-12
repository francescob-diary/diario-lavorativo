package it.diario.lavorativo.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.photo.GalleryWriter
import it.diario.lavorativo.core.photo.PhotoStorage
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Photo
import it.diario.lavorativo.domain.model.PhotoSource
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.repository.DiaryEntryRepository
import it.diario.lavorativo.domain.repository.WorkDayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PhotosUiState(
    val loading: Boolean = true,
    val workDayId: Long = 0L,
    val date: LocalDate = LocalDate.now(),
    val photos: List<Photo> = emptyList(),
    val activities: List<WorkActivity> = emptyList(),
    val events: List<WorkEvent> = emptyList(),
    val importing: Boolean = false,
    /** Foto aperta a schermo intero, null se si sta guardando la griglia. */
    val openPhoto: Photo? = null,
    val message: String? = null
) {
    val isEmpty: Boolean get() = !loading && photos.isEmpty()
}

/** Foto di una giornata: scatto, importazione dalla galleria, didascalie. */
class PhotosViewModel(
    private val entryRepository: DiaryEntryRepository,
    private val workDayRepository: WorkDayRepository,
    private val storage: PhotoStorage,
    private val gallery: GalleryWriter,
    private val clock: AppClock,
    private val epochDay: Long
) : ViewModel() {

    private val date: LocalDate = LocalDate.ofEpochDay(epochDay)

    private val _uiState = MutableStateFlow(PhotosUiState(date = date))
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val day = workDayRepository.observeDay(date).first()
            if (day == null) {
                _uiState.update { it.copy(loading = false) }
                return@launch
            }
            _uiState.update { it.copy(workDayId = day.id, loading = false) }
            observe(day.id)
        }
    }

    private fun observe(workDayId: Long) {
        viewModelScope.launch {
            entryRepository.observePhotos(workDayId).collect { list ->
                _uiState.update { it.copy(photos = list) }
            }
        }
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
    }

    /** Se non c'e' ancora una giornata la si crea: la foto non deve andare persa. */
    private suspend fun ensureDay(): Long {
        val current = _uiState.value.workDayId
        if (current != 0L) return current
        val id = workDayRepository.createEmptyDay(date, DayType.LAVORO)
        _uiState.update { it.copy(workDayId = id) }
        observe(id)
        return id
    }

    /**
     * Prepara la voce in galleria su cui la fotocamera scrivera'.
     * La foto va prima in galleria e poi nell'app, come richiesto.
     */
    suspend fun prepareCameraTarget(): Uri? = gallery.createPendingImage(clock.now(), clock.zone())

    /** Lo scatto e' andato a buon fine: si rende visibile e si importa la copia. */
    fun onCameraResult(uri: Uri, success: Boolean) = viewModelScope.launch {
        if (!success) {
            // Scatto annullato: si toglie la voce vuota, altrimenti resta
            // in galleria un file da zero byte.
            gallery.discard(uri)
            return@launch
        }
        gallery.publish(uri)
        importInto(uri, PhotoSource.FOTOCAMERA, galleryUri = uri.toString())
    }

    /** Foto scelta dalla galleria: e' gia' li', si importa solo la copia. */
    fun onGalleryPicked(uri: Uri) = viewModelScope.launch {
        importInto(uri, PhotoSource.GALLERIA, galleryUri = uri.toString())
    }

    /**
     * Importa nella cartella privata dell'app una copia ridotta.
     * L'originale a piena risoluzione resta in galleria: la copia serve alle
     * miniature e al PDF, e tenere due volte otto megapixel non avrebbe senso.
     */
    private suspend fun importInto(uri: Uri, source: PhotoSource, galleryUri: String?) {
        _uiState.update { it.copy(importing = true) }
        val dayId = ensureDay()
        val now = clock.now()

        storage.importFrom(uri, now, clock.zone())
            .onSuccess { stored ->
                entryRepository.savePhoto(
                    Photo(
                        workDayId = dayId,
                        fileName = stored.fileName,
                        galleryUri = galleryUri,
                        takenAt = now,
                        source = source,
                        sizeBytes = stored.sizeBytes
                    )
                )
                _uiState.update { it.copy(importing = false, message = "Foto salvata") }
            }
            .onFailure {
                _uiState.update {
                    it.copy(importing = false, message = "Non sono riuscito a salvare la foto")
                }
            }
    }

    fun openPhoto(photo: Photo) = _uiState.update { it.copy(openPhoto = photo) }

    fun closePhoto() = _uiState.update { it.copy(openPhoto = null) }

    fun setCaption(id: Long, caption: String) = viewModelScope.launch {
        entryRepository.setPhotoCaption(id, caption.trim().ifBlank { null })
        _uiState.update { state ->
            val updated = state.openPhoto?.takeIf { it.id == id }
                ?.copy(caption = caption.trim().ifBlank { null })
            state.copy(openPhoto = updated ?: state.openPhoto)
        }
    }

    fun linkToActivity(photoId: Long, activityId: Long?) = viewModelScope.launch {
        entryRepository.setPhotoLinks(photoId, activityId, null)
        refreshOpenPhoto(photoId)
    }

    fun linkToEvent(photoId: Long, eventId: Long?) = viewModelScope.launch {
        entryRepository.setPhotoLinks(photoId, null, eventId)
        refreshOpenPhoto(photoId)
    }

    private suspend fun refreshOpenPhoto(photoId: Long) {
        val fresh = entryRepository.getPhoto(photoId) ?: return
        _uiState.update { state ->
            if (state.openPhoto?.id == photoId) state.copy(openPhoto = fresh) else state
        }
    }

    /**
     * Cancella prima il record e poi il file: se l'app venisse chiusa in mezzo
     * resterebbe un file orfano, che la pulizia periodica rimuove. L'ordine
     * inverso lascerebbe invece un record che punta al vuoto, molto peggio.
     */
    fun deletePhoto(photo: Photo) = viewModelScope.launch {
        entryRepository.deletePhoto(photo.id)
        storage.delete(photo.fileName)
        _uiState.update {
            it.copy(
                openPhoto = null,
                // L'originale in galleria NON viene toccato: e' del telefono,
                // non dell'app. Si cancella dalla galleria come qualsiasi
                // altra foto.
                message = if (photo.isInGallery) {
                    "Rimossa dal diario, resta in galleria"
                } else {
                    "Foto eliminata"
                }
            )
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(epochDay: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PhotosViewModel(
                    entryRepository = diarioContainer.diaryEntryRepository,
                    workDayRepository = diarioContainer.workDayRepository,
                    storage = diarioContainer.photoStorage,
                    gallery = diarioContainer.galleryWriter,
                    clock = diarioContainer.clock,
                    epochDay = epochDay
                )
            }
        }
    }
}
