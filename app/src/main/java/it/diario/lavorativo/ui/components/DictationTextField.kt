package it.diario.lavorativo.ui.components

import it.diario.lavorativo.domain.dictation.TextCleaner
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Campo di testo con il microfono: si parla e il testo compare scritto,
 * aggiunto in fondo a quello che c'e' gia'. Poi si corregge a mano.
 *
 * Usa il riconoscimento vocale di Android, chiedendo di lavorare senza
 * rete quando il pacchetto della lingua italiana e' sul telefono.
 */
@Composable
fun DictationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 2,
    supportingText: String? = null
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val detto = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (detto.isNotEmpty()) {
            // La frase dettata arriva gia' ripulita: maiuscola, punto,
            // niente ripetizioni. Il testo di prima si chiude col punto.
            val frase = TextCleaner.clean(detto)
            val prima = value.trimEnd().let {
                if (it.isEmpty() || it.last() in ".!?") it else "$it."
            }
            onValueChange(if (prima.isEmpty()) frase else "$prima $frase")
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = {
                try {
                    launcher.launch(dictationIntent())
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        "Su questo telefono manca il riconoscimento vocale",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }) {
                Icon(Icons.Filled.Mic, contentDescription = "Detta")
            }
        },
        modifier = modifier
    )
}

/**
 * Richiesta di dettatura in italiano con la finestrella di Google.
 *
 * Non si chiede di lavorare senza rete: senza il pacchetto dell'italiano
 * offline Google risponde "ricerca vocale non disponibile". Se il pacchetto
 * c'e', Google lo usa comunque da solo.
 */
fun dictationIntent(prompt: String = "Parla pure"): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "it-IT")
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
