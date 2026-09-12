package it.diario.lavorativo.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.audio.VoiceStorage
import it.diario.lavorativo.core.backup.BackupArchive
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.data.backup.BackupRepository
import it.diario.lavorativo.domain.backup.BACKUP_FORMAT_VERSION
import it.diario.lavorativo.domain.backup.BackupManifest
import it.diario.lavorativo.domain.backup.BackupReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

data class BackupUiState(
    val includeMedia: Boolean = true,
    val working: Boolean = false,
    val progressLabel: String = "",
    val message: String? = null,
    val lastBackupLabel: String? = null,
    /** Manifest del file scelto per il ripristino, in attesa di conferma. */
    val pendingRestore: PendingRestore? = null
)

/**
 * Un ripristino non parte mai da solo: prima si legge il file, si dice
 * all'utente cosa contiene, e solo dopo si sostituisce il diario.
 */
data class PendingRestore(
    val uri: Uri,
    val manifest: BackupManifest,
    val mediaCount: Int
) {
    val periodLabel: String
        get() {
            val da = manifest.firstDate?.let { LocalDate.ofEpochDay(it) }
            val a = manifest.lastDate?.let { LocalDate.ofEpochDay(it) }
            return if (da == null || a == null) "nessuna giornata"
            else da.toString() + "  ->  " + a.toString()
        }
}

class BackupViewModel(
    private val context: Context,
    private val repository: BackupRepository,
    private val voiceStorage: VoiceStorage,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun setIncludeMedia(value: Boolean) = _uiState.update { it.copy(includeMedia = value) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun cancelRestore() = _uiState.update { it.copy(pendingRestore = null) }

    /** Nome proposto al sistema quando si sceglie dove salvare. */
    fun suggestedFileName(): String =
        BackupArchive.fileName(LocalDate.now(clock.zone()).toString())

    // ------------------------------------------------------------- backup

    fun writeBackup(uri: Uri) = viewModelScope.launch {
        _uiState.update { it.copy(working = true, progressLabel = "Leggo il diario...") }

        try {
            val content = repository.readEverything()
            val includeMedia = _uiState.value.includeMedia

            val photos = if (includeMedia) {
                content.photos.map { File(repository.photoDirectory(), it.fileName) }
            } else {
                emptyList()
            }
            val voices = if (includeMedia) {
                content.voiceNotes.map { voiceStorage.file(it.fileName) }
            } else {
                emptyList()
            }

            val manifest = BackupManifest(
                formatVersion = BACKUP_FORMAT_VERSION,
                createdAt = clock.now().toEpochMilli(),
                appVersion = appVersion(),
                databaseVersion = DATABASE_VERSION,
                workDays = content.workDays.size,
                sites = content.sites.size,
                photos = content.photos.size,
                voiceNotes = content.voiceNotes.size,
                vehicles = content.vehicles.size,
                fuelStops = content.fuelStops.size,
                includesMedia = includeMedia,
                firstDate = content.workDays.minOfOrNull { it.date },
                lastDate = content.workDays.maxOfOrNull { it.date }
            )

            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    BackupArchive.write(
                        output = out,
                        manifest = manifest,
                        content = content,
                        photos = photos,
                        voiceNotes = voices,
                        onProgress = { done, total ->
                            _uiState.update {
                                it.copy(
                                    progressLabel = "Scrivo " + done.toString() +
                                        " di " + total.toString()
                                )
                            }
                        }
                    )
                } ?: throw IllegalStateException("File non scrivibile")
            }

            _uiState.update {
                it.copy(
                    working = false,
                    progressLabel = "",
                    lastBackupLabel = riepilogo(manifest),
                    message = "Backup salvato: " + content.totalRows.toString() + " righe"
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    working = false,
                    progressLabel = "",
                    message = "Non sono riuscito a salvare il backup"
                )
            }
        }
    }

    // ---------------------------------------------------------- ripristino

    /** Primo passo: si guarda dentro il file, senza toccare niente. */
    fun inspectBackup(uri: Uri) = viewModelScope.launch {
        _uiState.update { it.copy(working = true, progressLabel = "Controllo il file...") }

        try {
            val manifest = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use {
                    BackupArchive.readManifest(it)
                }
            }

            if (manifest == null) {
                _uiState.update {
                    it.copy(
                        working = false,
                        progressLabel = "",
                        message = "Questo file non e' un backup del diario"
                    )
                }
                return@launch
            }

            if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
                _uiState.update {
                    it.copy(
                        working = false,
                        progressLabel = "",
                        message = "Backup fatto con una versione piu' nuova dell'app: " +
                            "aggiorna prima di ripristinarlo"
                    )
                }
                return@launch
            }

            val media = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use {
                    BackupArchive.countMedia(it)
                } ?: 0
            }

            _uiState.update {
                it.copy(
                    working = false,
                    progressLabel = "",
                    pendingRestore = PendingRestore(uri, manifest, media)
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(working = false, progressLabel = "", message = "File non leggibile")
            }
        }
    }

    /** Secondo passo: confermato, si sostituisce tutto. */
    fun confirmRestore() = viewModelScope.launch {
        val pending = _uiState.value.pendingRestore ?: return@launch
        _uiState.update {
            it.copy(working = true, progressLabel = "Ripristino in corso...", pendingRestore = null)
        }

        try {
            val esito = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(pending.uri)?.use { input ->
                    BackupArchive.read(
                        input = input,
                        photoDirectory = repository.photoDirectory(),
                        voiceDirectory = voiceStorage.directory,
                        restoreMedia = pending.manifest.includesMedia
                    )
                }
            }

            when (esito) {
                is BackupReadResult.Ok -> {
                    repository.replaceEverything(esito.content)
                    _uiState.update {
                        it.copy(
                            working = false,
                            progressLabel = "",
                            message = "Ripristinate " +
                                esito.content.totalRows.toString() + " righe"
                        )
                    }
                }

                is BackupReadResult.TooNew -> _uiState.update {
                    it.copy(
                        working = false,
                        progressLabel = "",
                        message = "Backup troppo recente per questa versione dell'app"
                    )
                }

                is BackupReadResult.Damaged -> _uiState.update {
                    it.copy(
                        working = false,
                        progressLabel = "",
                        message = "Backup rovinato: " + esito.reason
                    )
                }

                null -> _uiState.update {
                    it.copy(working = false, progressLabel = "", message = "File non leggibile")
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    working = false,
                    progressLabel = "",
                    message = "Ripristino non riuscito: il diario e' rimasto com'era"
                )
            }
        }
    }

    private fun riepilogo(m: BackupManifest): String =
        m.workDays.toString() + " giornate, " + m.sites.toString() + " cantieri, " +
            m.photos.toString() + " foto, " + m.voiceNotes.toString() + " note vocali"

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    companion object {
        const val DATABASE_VERSION = 6

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BackupViewModel(
                    context = diarioContainer.context,
                    repository = diarioContainer.backupRepository,
                    voiceStorage = diarioContainer.voiceStorage,
                    clock = diarioContainer.clock
                )
            }
        }
    }
}
