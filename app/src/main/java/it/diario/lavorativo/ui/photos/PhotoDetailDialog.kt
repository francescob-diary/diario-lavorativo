package it.diario.lavorativo.ui.photos

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.domain.model.Photo
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.ui.entries.label
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Foto a schermo intero con didascalia e collegamento a una lavorazione o a
 * un evento. Il collegamento serve a dare senso alla foto quando la si
 * ritrova mesi dopo: "crepa" da sola non dice niente, "crepa - problema
 * segnalato il 12 marzo" si', ed e' quella che vale in una contestazione.
 */
@Composable
fun PhotoDetailDialog(
    photo: Photo,
    activities: List<WorkActivity>,
    events: List<WorkEvent>,
    onCaption: (String) -> Unit,
    onLinkActivity: (Long?) -> Unit,
    onLinkEvent: (Long?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var caption by remember(photo.id) { mutableStateOf(photo.caption.orEmpty()) }

    val storage = remember { diarioContainer.photoStorage }
    val bitmap by produceState<Bitmap?>(initialValue = null, photo.fileName) {
        value = storage.load(photo.fileName)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val current = bitmap
                    if (current == null) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            bitmap = current.asImageBitmap(),
                            contentDescription = photo.caption ?: "Foto di cantiere",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Scattata alle " +
                        ORA.format(photo.takenAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Didascalia") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (activities.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Collega a una lavorazione",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activities.forEach { activity ->
                            FilterChip(
                                selected = photo.activityId == activity.id,
                                onClick = {
                                    onLinkActivity(
                                        if (photo.activityId == activity.id) null else activity.id
                                    )
                                },
                                label = { Text(activity.description) }
                            )
                        }
                    }
                }

                if (events.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Oppure a un evento",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        events.forEach { event ->
                            FilterChip(
                                selected = photo.eventId == event.id,
                                onClick = {
                                    onLinkEvent(if (photo.eventId == event.id) null else event.id)
                                },
                                label = { Text(event.type.label() + ": " + event.title) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            text = "Elimina",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Chiudi") }
                        TextButton(onClick = {
                            onCaption(caption)
                            onDismiss()
                        }) { Text("Salva") }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la foto?") },
            text = { Text("Il file verra' cancellato dal telefono e non si potra' recuperare.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            }
        )
    }
}
