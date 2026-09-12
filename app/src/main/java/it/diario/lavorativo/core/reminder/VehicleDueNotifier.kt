package it.diario.lavorativo.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.diario.lavorativo.MainActivity
import it.diario.lavorativo.R
import it.diario.lavorativo.core.di.AppContainer
import java.time.LocalDate

/**
 * L'avviso delle scadenze del mezzo.
 *
 * Non ha una sveglia sua: si attacca a quella del foglio settimanale, che
 * suona gia' una volta la settimana. Un tagliando non scade da un giorno
 * all'altro, e una sveglia in piu' vorrebbe dire un'altra cosa che il
 * risparmio energetico dei telefoni puo' spegnere di nascosto.
 *
 * Avvisa solo per le scadenze che l'officina ha davvero scritto. Se non ha
 * detto niente, qui non arriva niente: un avviso inventato dopo due volte
 * si impara a ignorare, e allora non serve piu' nemmeno quando e' vero.
 */
object VehicleDueNotifier {

    suspend fun notifyIfNeeded(
        context: Context,
        container: AppContainer,
        today: LocalDate = LocalDate.now()
    ) {
        if (!canNotify(context)) return

        val mezzo = container.vehicleRepository.activeVehicle() ?: return
        val interventi = container.vehicleRepository.allMaintenances(mezzo.id)
        if (interventi.isEmpty()) return

        val rifornimenti = container.vehicleRepository.fuelStops(
            mezzo.id,
            LocalDate.ofEpochDay(0),
            today
        )
        val contatore = container.vehicleCalculator.lastKnownOdometer(rifornimenti, interventi)

        val scadenze = container.maintenanceScheduler.toWarn(interventi, today, contatore)
        val testo = container.maintenanceScheduler.warningText(scadenze) ?: return

        createChannel(context)

        val apri = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifica = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(mezzo.label)
            .setContentText(testo)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(apri)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notifica)
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Canale a parte da quello del foglio: chi vuole il promemoria del
        // lunedi' ma non quello del furgone puo' spegnerne uno solo dalle
        // impostazioni di Android.
        val canale = NotificationChannel(
            CHANNEL_ID,
            "Scadenze del mezzo",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tagliando, revisione e altre scadenze dell'auto aziendale"
        }
        manager.createNotificationChannel(canale)
    }

    private const val CHANNEL_ID = "scadenze_mezzo"
    private const val NOTIFICATION_ID = 4203
    private const val REQUEST_CODE = 4204
}
