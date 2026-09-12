package it.diario.lavorativo.ui.entries

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.CommunicationDirection
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.EventType
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Scheda di inserimento, aperta dal basso.
 *
 * Una sola scheda per i tre tipi di voce: cambia il contenuto, non la
 * struttura. Cosi' il comportamento e' identico ovunque e c'e' un solo
 * punto in cui sistemare le cose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorSheet(
    editor: EditorState,
    viewModel: EntriesViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            when (editor) {
                is EditorState.Activity -> ActivityForm(editor, viewModel)
                is EditorState.Event -> EventForm(editor, viewModel)
                is EditorState.Comm -> CommForm(editor, viewModel)
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Annulla") }

                Button(
                    onClick = viewModel::save,
                    enabled = when (editor) {
                        is EditorState.Activity -> editor.canSave
                        is EditorState.Event -> editor.canSave
                        is EditorState.Comm -> editor.canSave
                    },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("SALVA") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Attivita'
// ---------------------------------------------------------------------------

@Composable
private fun ActivityForm(editor: EditorState.Activity, viewModel: EntriesViewModel) {
    SheetTitle(if (editor.isNew) "Nuova lavorazione" else "Modifica lavorazione")

    Text("Tipo di lavoro", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActivityCategory.entries.forEach { category ->
            FilterChip(
                selected = category == editor.category,
                onClick = { viewModel.onActivityCategory(category) },
                label = { Text(category.label()) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = editor.description,
        onValueChange = { v -> viewModel.updateActivityEditor { it.copy(description = v) } },
        label = { Text("Cosa hai fatto") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )

    if (editor.suggestions.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Gia' usate", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            editor.suggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { viewModel.updateActivityEditor { it.copy(description = suggestion) } },
                    label = { Text(suggestion) }
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = editor.quantity,
        onValueChange = { v -> viewModel.updateActivityEditor { it.copy(quantity = v) } },
        label = { Text("Quantita', se ha senso (12 mq, 3 bancali)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(Modifier.height(16.dp))

    Text("Orario, se te lo ricordi", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionalTimeButton(
            label = "Inizio",
            value = editor.startTime,
            modifier = Modifier.weight(1f),
            onPick = { t -> viewModel.updateActivityEditor { it.copy(startTime = t) } }
        )
        OptionalTimeButton(
            label = "Fine",
            value = editor.endTime,
            modifier = Modifier.weight(1f),
            onPick = { t -> viewModel.updateActivityEditor { it.copy(endTime = t) } }
        )
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = editor.notes,
        onValueChange = { v -> viewModel.updateActivityEditor { it.copy(notes = v) } },
        label = { Text("Note") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
}

// ---------------------------------------------------------------------------
// Eventi
// ---------------------------------------------------------------------------

@Composable
private fun EventForm(editor: EditorState.Event, viewModel: EntriesViewModel) {
    SheetTitle(if (editor.isNew) "Nuovo evento" else "Modifica evento")

    Text("Cos'e' successo", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EventType.entries.forEach { type ->
            FilterChip(
                selected = type == editor.type,
                onClick = { viewModel.updateEventEditor { it.copy(type = type) } },
                label = { Text(type.label()) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = editor.title,
        onValueChange = { v -> viewModel.updateEventEditor { it.copy(title = v) } },
        label = { Text("In breve") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = editor.description,
        onValueChange = { v -> viewModel.updateEventEditor { it.copy(description = v) } },
        label = { Text("Come e' andata") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3
    )

    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RequiredTimeButton(
            label = "Ora",
            value = editor.time,
            modifier = Modifier.weight(1f),
            onPick = { t -> viewModel.updateEventEditor { it.copy(time = t) } }
        )
        OutlinedTextField(
            value = editor.durationMinutes,
            onValueChange = { v ->
                viewModel.updateEventEditor { it.copy(durationMinutes = v.filter { c -> c.isDigit() }) }
            },
            label = { Text("Minuti persi") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    Spacer(Modifier.height(16.dp))

    Text("Quanto e' grave", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EventSeverity.entries.forEach { severity ->
            FilterChip(
                selected = severity == editor.severity,
                onClick = { viewModel.updateEventEditor { it.copy(severity = severity) } },
                label = { Text(severity.label()) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    SwitchRow(
        label = "Ancora da risolvere",
        checked = editor.unresolved,
        onChange = { v -> viewModel.updateEventEditor { it.copy(unresolved = v) } }
    )
}

// ---------------------------------------------------------------------------
// Comunicazioni
// ---------------------------------------------------------------------------

@Composable
private fun CommForm(editor: EditorState.Comm, viewModel: EntriesViewModel) {
    SheetTitle(
        when {
            editor.fromShare -> "Messaggio ricevuto"
            editor.isNew -> "Nuova comunicazione"
            else -> "Modifica comunicazione"
        }
    )

    if (editor.fromShare) {
        Text(
            text = "Testo arrivato dalla condivisione. Controlla il nome e l'ora, " +
                "poi salva.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
    }

    Text("Come", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommunicationChannel.entries.forEach { channel ->
            FilterChip(
                selected = channel == editor.channel,
                onClick = { viewModel.updateCommEditor { it.copy(channel = channel) } },
                label = { Text(channel.label()) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommunicationDirection.entries.forEach { direction ->
            FilterChip(
                selected = direction == editor.direction,
                onClick = { viewModel.updateCommEditor { it.copy(direction = direction) } },
                label = { Text(direction.label()) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = editor.contactName,
        onValueChange = { v -> viewModel.updateCommEditor { it.copy(contactName = v) } },
        label = { Text("Chi") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    if (editor.contactSuggestions.isNotEmpty() && editor.contactName.isBlank()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            editor.contactSuggestions.forEach { name ->
                AssistChip(
                    onClick = { viewModel.updateCommEditor { it.copy(contactName = name) } },
                    label = { Text(name) }
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = editor.contactRef,
        onValueChange = { v -> viewModel.updateCommEditor { it.copy(contactRef = v) } },
        label = { Text("Numero o indirizzo") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RequiredTimeButton(
            label = "Ora",
            value = editor.time,
            modifier = Modifier.weight(1f),
            onPick = { t -> viewModel.updateCommEditor { it.copy(time = t) } }
        )
        if (editor.channel == CommunicationChannel.TELEFONATA) {
            OutlinedTextField(
                value = editor.durationMinutes,
                onValueChange = { v ->
                    viewModel.updateCommEditor {
                        it.copy(durationMinutes = v.filter { c -> c.isDigit() })
                    }
                },
                label = { Text("Durata min.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(12.dp))

    if (editor.channel == CommunicationChannel.EMAIL || editor.subject.isNotBlank()) {
        OutlinedTextField(
            value = editor.subject,
            onValueChange = { v -> viewModel.updateCommEditor { it.copy(subject = v) } },
            label = { Text("Oggetto") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = editor.content,
        onValueChange = { v -> viewModel.updateCommEditor { it.copy(content = v) } },
        label = {
            Text(
                if (editor.channel == CommunicationChannel.TELEFONATA) {
                    "Cosa vi siete detti"
                } else {
                    "Contenuto"
                }
            )
        },
        modifier = Modifier.fillMaxWidth(),
        minLines = 5
    )

    Spacer(Modifier.height(16.dp))

    SwitchRow(
        label = "Da mettere per iscritto",
        checked = editor.requiresFollowUp,
        onChange = { v -> viewModel.updateCommEditor { it.copy(requiresFollowUp = v) } }
    )
}

// ---------------------------------------------------------------------------
// Pezzi comuni
// ---------------------------------------------------------------------------

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RequiredTimeButton(
    label: String,
    value: LocalTime,
    modifier: Modifier = Modifier,
    onPick: (LocalTime) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.height(56.dp)) {
        Text(label + " " + value.format(TIME))
    }
    if (open) {
        TimePickerDialog(
            initial = value,
            onDismiss = { open = false },
            onConfirm = {
                onPick(it)
                open = false
            }
        )
    }
}

@Composable
private fun OptionalTimeButton(
    label: String,
    value: LocalTime?,
    modifier: Modifier = Modifier,
    onPick: (LocalTime?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { if (value == null) open = true else onPick(null) },
        modifier = modifier.height(56.dp)
    ) {
        Text(if (value == null) label else value.format(TIME) + "  x")
    }
    if (open) {
        TimePickerDialog(
            initial = LocalTime.of(8, 0),
            onDismiss = { open = false },
            onConfirm = {
                onPick(it)
                open = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(pickerState.hour, pickerState.minute))
            }) { Text("Conferma") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
