package it.diario.lavorativo.core.reminder

import it.diario.lavorativo.domain.service.DailyReminderCalculator
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
        // goAsync si puo' chiamare una volta sola per ogni ricezione: tutto
        // il lavoro in sottofondo passa da qui, in fila.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_WEEKLY_REMINDER -> {
                        showNotification(context)
                        runCatching {
                            VehicleDueNotifier.notifyIfNeeded(
                                context.applicationContext,
                                appContainer(context)
                            )
                        }
                        runCatching { rescheduleWeekly(context) }
                    }
                    ACTION_DAILY_REMINDER -> {
                        runCatching { dailyReminder(context) }
                        runCatching { rescheduleDaily(context, LocalDateTime.now().plusMinutes(1)) }
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        runCatching { rescheduleWeekly(context) }
                        runCatching { rescheduleDaily(context, LocalDateTime.now()) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Dopo un riavvio le sveglie di sistema si perdono: vanno riarmate. */
    private suspend fun rescheduleWeekly(context: Context) {
        val settings = appContainer(context).settingsRepository.reminder.first()
        ReminderScheduler(context.applicationContext).schedule(settings, LocalDateTime.now())
    }

    private suspend fun rescheduleDaily(context: Context, from: LocalDateTime) {
        val settings = appContainer(context).settingsRepository.dailyReminder.first()
        DailyReminderScheduler(context.applicationContext).schedule(settings, from)
    }

    /** Fine giornata: se il diario di oggi non e' ancora compilato, avvisa. */
    private suspend fun dailyReminder(context: Context) {
        val oggi = appContainer(context).workDayRepository.getDay(LocalDate.now())
        if (!DailyReminderCalculator.isDayCompiled(oggi)) {
            showDailyNotification(context)
        }
    }

    private fun showDailyNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    DAILY_CHANNEL_ID,
                    "Fine giornata",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Promemoria per compilare il diario della giornata" }
            )
        }

        // Toccando la notifica si apre direttamente la dettatura.
        val openDictation = PendingIntent.getActivity(
            context,
            DAILY_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_DICTATION, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DAILY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Com'e' andata oggi?")
            .setContentText("Tocca e racconta la giornata: il diario si compila da solo.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openDictation)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(DAILY_NOTIFICATION_ID, notification)
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
        const val ACTION_DAILY_REMINDER = "it.diario.lavorativo.PROMEMORIA_GIORNALIERO"
        const val EXTRA_OPEN_DICTATION = "apri_dettatura"
        private const val DAILY_CHANNEL_ID = "fine_giornata"
        private const val DAILY_NOTIFICATION_ID = 4301
        private const val DAILY_REQUEST_CODE = 4302
        const val EXTRA_OPEN_WEEK = "apri_settimana"
        private const val CHANNEL_ID = "foglio_settimanale"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_CODE = 4202
        private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
    }
}
