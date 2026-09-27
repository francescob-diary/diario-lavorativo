package it.diario.lavorativo.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.diario.lavorativo.domain.model.DailyReminderSettings
import it.diario.lavorativo.domain.service.DailyReminderCalculator
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sveglia del promemoria di fine giornata. Stessa scelta di quello
 * settimanale: setWindow, niente permessi speciali, riarmata a ogni scatto.
 */
class DailyReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(settings: DailyReminderSettings, from: LocalDateTime = LocalDateTime.now()) {
        cancel()
        val manager = alarmManager ?: return
        val next = DailyReminderCalculator.nextTrigger(settings, from) ?: return
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, pendingIntent())
    }

    fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DAILY_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val REQUEST_CODE = 4301
        const val WINDOW_MILLIS = 5L * 60L * 1000L
    }
}
