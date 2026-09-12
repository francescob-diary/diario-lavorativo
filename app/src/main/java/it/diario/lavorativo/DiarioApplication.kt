package it.diario.lavorativo

import android.app.Application
import it.diario.lavorativo.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Punto di ingresso dell'app: costruisce il contenitore delle dipendenze. */
class DiarioApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleWeeklyReminder()
    }

    /**
     * Riarma il promemoria a ogni avvio.
     *
     * Costa poco e mette al riparo dai casi in cui il sistema perde la
     * sveglia: aggiornamento dell'app, pulizia della memoria, ottimizzazioni
     * aggressive del risparmio energetico di certi telefoni.
     */
    private fun scheduleWeeklyReminder() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                val settings = container.settingsRepository.reminder.first()
                container.reminderScheduler.schedule(settings, LocalDateTime.now())
            }
        }
    }
}
