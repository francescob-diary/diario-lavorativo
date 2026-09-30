package it.diario.lavorativo.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Sveglia delle 16:30 per le cose prestate. Suona tutti i giorni; la
 * notifica compare solo se c'e' ancora qualcosa fuori.
 */
class LoanReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(from: LocalDateTime = LocalDateTime.now()) {
        val manager = alarmManager ?: return
        val next = nextTrigger(from)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_LOAN_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        val TIME: LocalTime = LocalTime.of(16, 30)
        private const val REQUEST_CODE = 4401
        private const val WINDOW_MILLIS = 5L * 60L * 1000L

        fun nextTrigger(from: LocalDateTime): LocalDateTime {
            val today = from.toLocalDate().atTime(TIME)
            return if (from.isBefore(today)) today else today.plusDays(1)
        }
    }
}
