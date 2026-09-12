package it.diario.lavorativo.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import it.diario.lavorativo.domain.model.ReminderSettings
import it.diario.lavorativo.domain.service.ReminderScheduleCalculator
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Programma il promemoria settimanale con l'AlarmManager di sistema.
 *
 * Perche' AlarmManager e non WorkManager: qui l'orario conta davvero. Il
 * foglio va consegnato appena si arriva, e un promemoria che scatta "entro
 * qualche ora" arriverebbe a cose fatte. WorkManager e' pensato per lavori
 * differibili, questo non lo e'.
 *
 * Non si usa una sveglia esatta con richiesta di permesso speciale: da
 * Android 12 servirebbe SCHEDULE_EXACT_ALARM, che il Play Store concede solo
 * ad allarmi e calendari. setWindow con una finestra di dieci minuti e'
 * abbondantemente sufficiente per un promemoria di lavoro, e non richiede
 * nessun permesso particolare.
 */
class ReminderScheduler(
    private val context: Context,
    private val calculator: ReminderScheduleCalculator = ReminderScheduleCalculator()
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(settings: ReminderSettings, from: LocalDateTime = LocalDateTime.now()) {
        cancel()
        if (!settings.enabled) return

        val manager = alarmManager ?: return
        val next = calculator.nextTrigger(settings, from)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        manager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            WINDOW_MILLIS,
            pendingIntent(mutable = false)
        )
    }

    fun cancel() {
        alarmManager?.cancel(pendingIntent(mutable = false))
    }

    private fun pendingIntent(mutable: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_WEEKLY_REMINDER
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or if (mutable) {
                PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_IMMUTABLE
            }
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private companion object {
        const val REQUEST_CODE = 4201
        /** Dieci minuti di tolleranza: piu' che accettabile per un promemoria. */
        const val WINDOW_MILLIS = 10L * 60L * 1000L
    }
}
