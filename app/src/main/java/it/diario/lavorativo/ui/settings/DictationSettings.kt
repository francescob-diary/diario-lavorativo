package it.diario.lavorativo.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.diario.lavorativo.domain.model.DailyReminderSettings
import it.diario.lavorativo.domain.service.TimeTextParser
import java.time.DayOfWeek

/** Scelta del file del modello per la dettatura (Gemma, formato .gguf). */
@Composable
fun DictationSettingsCard(
    modelUri: String?,
    onModelPicked: (String?) -> Unit
) {
    val context = LocalContext.current
    val nome = remember(modelUri) { modelUri?.let { displayName(context, Uri.parse(it)) } }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Il permesso di leggere il file deve durare anche dopo il riavvio.
        val ok = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        val nomeFile = displayName(context, uri).orEmpty()
        if (!nomeFile.lowercase().endsWith(".gguf")) {
            Toast.makeText(
                context,
                "Attenzione: il modello deve essere un file .gguf",
                Toast.LENGTH_LONG
            ).show()
        }
        if (!ok) {
            Toast.makeText(context, "Non riesco a tenere il permesso sul file", Toast.LENGTH_LONG).show()
        }
        onModelPicked(uri.toString())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Dettatura", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Per capire il racconto libero serve il file del modello " +
                    "(Gemma 2 2B, formato .gguf, circa 1,6 GB) salvato sul telefono. " +
                    "Senza il modello l'app usa regole semplici: capisce orari, " +
                    "pause e cantieri detti in modo ordinato.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (nome == null) "Nessun modello scelto" else "Modello: " + nome,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text(if (nome == null) "SCEGLI IL MODELLO" else "CAMBIA") }
                if (nome != null) {
                    TextButton(onClick = { onModelPicked(null) }) { Text("TOGLI") }
                }
            }
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment

/** Promemoria di fine giornata: acceso/spento, ora, giorni. */
@Composable
fun DailyReminderCard(
    settings: DailyReminderSettings,
    onChange: ((DailyReminderSettings) -> DailyReminderSettings) -> Unit
) {
    var modificaOra by remember { mutableStateOf(false) }
    val giorni = listOf(
        DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "M",
        DayOfWeek.THURSDAY to "G", DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S",
        DayOfWeek.SUNDAY to "D"
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Promemoria di fine giornata", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Se il diario di oggi non e' compilato, arriva una notifica. " +
                            "Toccandola si apre la dettatura.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { acceso -> onChange { it.copy(enabled = acceso) } }
                )
            }
            if (settings.enabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { modificaOra = true }) {
                    Text(
                        "Alle " + settings.hour.toString().padStart(2, '0') + ":" +
                            settings.minute.toString().padStart(2, '0')
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    giorni.forEach { (giorno, lettera) ->
                        val scelto = giorno in settings.days
                        FilterChip(
                            selected = scelto,
                            onClick = {
                                onChange {
                                    it.copy(days = if (scelto) it.days - giorno else it.days + giorno)
                                }
                            },
                            label = { Text(lettera) }
                        )
                    }
                }
            }
        }
    }

    if (modificaOra) {
        var testo by remember {
            mutableStateOf(
                settings.hour.toString().padStart(2, '0') + ":" +
                    settings.minute.toString().padStart(2, '0')
            )
        }
        val ora = TimeTextParser.parseClock(testo)
        AlertDialog(
            onDismissRequest = { modificaOra = false },
            title = { Text("Ora del promemoria") },
            text = {
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Es. 17:15") },
                    isError = ora == null,
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = ora != null,
                    onClick = {
                        val o = ora ?: return@TextButton
                        onChange { it.copy(hour = o.hour, minute = o.minute) }
                        modificaOra = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { modificaOra = false }) { Text("ANNULLA") } }
        )
    }
}
