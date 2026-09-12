package it.diario.lavorativo.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.diario.lavorativo.MainActivity
import it.diario.lavorativo.R
import it.diario.lavorativo.core.di.appContainer
import it.diario.lavorativo.domain.service.ReminderScheduleCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Mostra il promemoria del foglio settimanale e riprogramma quello successivo.
 *
 * La riprogrammazione avviene qui perche' si usa una sveglia singola e non
 * ripetuta: le sveglie ripetute di sistema vengono spostate liberamente dal
 * risparmio energetico, mentre riarmarla ogni volta tiene il giorno e l'ora
 * sotto controllo.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WEEKLY_REMINDER -> {
                showNotification(context)
                notifyVehicleDues(context)
                reschedule(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> reschedule(context)
        }
    }

    /** Dopo un riavvio le sveglie di sistema si perdono: vanno riarmate. */
    private fun reschedule(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = appContainer(context).settingsRepository.reminder.first()
                ReminderScheduler(context.applicationContext)
                    .schedule(settings, LocalDateTime.now())
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Le scadenze del mezzo viaggiano insieme al promemoria settimanale:
     * una sveglia sola per due avvisi, cosi' c'e' una cosa in meno che il
     * telefono puo' spegnere di nascosto.
     */
    private fun notifyVehicleDues(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                VehicleDueNotifier.notifyIfNeeded(
                    context.applicationContext,
                    appContainer(context)
                )
            } catch (e: Exception) {
                // Un avviso che non parte non deve far cadere il promemoria
                // del foglio, che e' quello che conta di piu'.
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        createChannel(context)

        val week = ReminderScheduleCalculator().weekToReport(LocalDate.now())
        val label = DAY_MONTH.format(week) + " - " + DAY_MONTH.format(week.plusDays(6))

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_WEEK, week.toEpochDay())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Foglio settimanale da consegnare")
            .setContentText("Settimana " + label + ". Controlla e stampa.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foglio settimanale",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Promemoria per la consegna del riepilogo in sede"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_WEEKLY_REMINDER = "it.diario.lavorativo.PROMEMORIA_SETTIMANALE"
        const val EXTRA_OPEN_WEEK = "apri_settimana"
        private const val CHANNEL_ID = "foglio_settimanale"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_CODE = 4202
        private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
    }
}
