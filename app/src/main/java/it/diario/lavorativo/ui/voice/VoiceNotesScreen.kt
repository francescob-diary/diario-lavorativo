package it.diario.lavorativo.ui.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import it.diario.lavorativo.domain.model.VoiceNote
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

/**
 * Note vocali della giornata.
 *
 * Il tasto di registrazione e' grande e sta in fondo, dove arriva il
 * pollice: si preme con una mano sola, che in cantiere e' spesso l'unica
 * libera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNotesScreen(
    epochDay: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: VoiceNotesViewModel = viewModel(
        factory = VoiceNotesViewModel.factory(epochDay)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var daEliminare by remember { mutableStateOf<VoiceNote?>(null) }

    val permessoMicrofono = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concesso ->
        if (concesso) viewModel.startRecording()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Note vocali")
                        Text(
                            text = state.date.format(TITOLO_DATA),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.notes.isEmpty() && !state.recording) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna nota vocale per questa giornata.\n" +
                            "Tieni premuto il pulsante e parla.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.notes, key = { it.id }) { nota ->
                        VoiceNoteCard(
                            note = nota,
                            playing = state.playingFileName == nota.fileName,
                            editing = state.editingId == nota.id,
                            onPlay = { viewModel.togglePlay(nota) },
                            onEdit = { viewModel.startEditing(nota.id) },
                            onCancelEdit = { viewModel.startEditing(null) },
                            onSave = { viewModel.saveTranscript(nota.id, it) },
                            onDelete = { daEliminare = nota }
                        )
                    }
                }
            }

            RecordingBar(
                recording = state.recording,
                elapsedSeconds = state.elapsedSeconds,
                onStart = {
                    permessoMicrofono.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStop = { viewModel.stopRecording() },
                onCancel = { viewModel.cancelRecording() }
            )
        }
    }

    daEliminare?.let { nota ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            title = { Text("Eliminare la nota?") },
            text = {
                Text(
                    "La registrazione del " +
                        nota.recordedAt.atZone(ZoneId.systemDefault())
                            .toLocalTime().format(ORA) +
                        " viene cancellata e non si recupera."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(nota)
                    daEliminare = null
                }) {
                    Text("ELIMINA")
                }
            },
            dismissButton = {
                TextButton(onClick = { daEliminare = null }) { Text("ANNULLA") }
            }
        )
    }
}

@Composable
private fun VoiceNoteCard(
    note: VoiceNote,
    playing: Boolean,
    editing: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var testo by remember(note.id, editing) { mutableStateOf(note.note.orEmpty()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Ferma" else "Ascolta"
                    )
                }

                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.recordedAt.atZone(ZoneId.systemDefault())
                            .toLocalTime().format(ORA),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = note.durationLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Trascrivi")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                }
            }

            if (editing) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Cosa dice la nota") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancelEdit) { Text("ANNULLA") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { onSave(testo) }) { Text("SALVA") }
                }
            } else if (note.hasTranscript) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note.note.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * La barra in fondo. Mentre registra diventa rossa e mostra i secondi che
 * passano: senza un segnale visibile non si sa se sta prendendo o no.
 */
@Composable
private fun RecordingBar(
    recording: Boolean,
    elapsedSeconds: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (recording) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (recording) {
                Text(
                    text = "Sto registrando   " +
                        (elapsedSeconds / 60).toString() + ":" +
                        (elapsedSeconds % 60).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) {
                        Text("BUTTA VIA")
                    }
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("SALVA")
                    }
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.size(12.dp))
                    Text("REGISTRA UNA NOTA", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)
private val TITOLO_DATA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
