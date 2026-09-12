package it.diario.lavorativo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import it.diario.lavorativo.domain.model.SharedText
import it.diario.lavorativo.core.reminder.ReminderReceiver.Companion.EXTRA_OPEN_WEEK
import it.diario.lavorativo.ui.navigation.DiarioNavHost
import it.diario.lavorativo.ui.theme.DiarioLavorativoTheme

/**
 * Unica Activity dell'app: tutto il resto e' Compose.
 *
 * Oltre all'avvio normale gestisce la condivisione: quando l'utente seleziona
 * un messaggio in WhatsApp o una mail e sceglie Diario Lavorativo, il testo
 * arriva qui dentro come Intent ACTION_SEND e viene passato alla navigazione,
 * che apre la scheda della comunicazione gia' compilata.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            var shared by remember {
                mutableStateOf(readSharedText(intent))
            }

            // L'app potrebbe essere gia' aperta quando arriva una condivisione:
            // in quel caso il sistema chiama onNewIntent e l'Intent va riletto.
            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { newIntent ->
                    readSharedText(newIntent)?.let { shared = it }
                    if (newIntent.hasExtra(EXTRA_OPEN_WEEK)) openWeekly = true
                }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }

            var openWeekly by remember {
                mutableStateOf(intent?.hasExtra(EXTRA_OPEN_WEEK) == true)
            }

            DiarioLavorativoTheme {
                DiarioNavHost(
                    sharedText = shared,
                    onSharedTextHandled = { shared = null },
                    openWeeklyReport = openWeekly,
                    onWeeklyReportOpened = { openWeekly = false }
                )
            }
        }
    }

    /**
     * Estrae il testo condiviso. Restituisce null se l'Intent non e' una
     * condivisione di testo, cioe' nel normale avvio dall'icona.
     */
    private fun readSharedText(source: Intent?): SharedText? {
        if (source == null) return null
        if (source.action != Intent.ACTION_SEND && source.action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }
        if (source.type?.startsWith("text/") != true) return null

        val text = source.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: return null

        return SharedText(
            text = text,
            subject = source.getStringExtra(Intent.EXTRA_SUBJECT),
            // Il pacchetto di origine non e' sempre esposto: quando manca
            // il canale lo sceglie l'utente, senza conseguenze.
            sourcePackage = runCatching { referrer?.host }.getOrNull()
        )
    }
}
