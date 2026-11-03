package com.example.kpkn.services.nutrition

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toNutritionLog
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.SupplementPilaItem
import com.example.kpkn.domain.nutrition.NutritionReminderPlanner
import com.example.kpkn.domain.nutrition.supplements.SupplementCatalog
import com.example.kpkn.domain.nutrition.supplements.SupplementEngine
import com.example.kpkn.navigation.KpknDeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Recordatorios diarios de suplementos: una alarma de un solo uso por suplemento de la pila, reprogramada al
 * dispararse y restaurada tras reinicio/actualización/cambio de zona por [NutritionReminderBootReceiver].
 *
 * Solo se programa lo que la pila pide (`reminder != null`). Si la persona ya completó sus dosis del día, la
 * notificación no se envía; la cadena diaria se mantiene igualmente.
 */
class SupplementReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL = "supplement_reminders"
        const val EXTRA_ID = "supplement_id"
        const val EXTRA_HOUR = "supplement_hour"
        const val EXTRA_MINUTE = "supplement_minute"
        internal const val ACTION_PREFIX = "com.example.kpkn.SUPPLEMENT_REMINDER."

        // Ventana de respaldo sin permiso de alarmas exactas.
        private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L
    }

    private val appCtx = context.applicationContext
    private val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL,
            appCtx.getString(R.string.notif_channel_supplements_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appCtx.getString(R.string.notif_channel_supplements_desc)
            enableVibration(true)
        }
        (appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    /** Programa la próxima toma de [item] o cancela su alarma si ya no tiene recordatorio. */
    fun sync(item: SupplementPilaItem, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val time = parseTime(item.reminder) ?: run {
            cancel(item.id)
            return false
        }
        return scheduleNext(item.id, time.first, time.second, now)
    }

    /** Realinea todas las alarmas de la pila: programa las activas y cancela las que ya no tienen hora. */
    fun syncAll(items: List<SupplementPilaItem>, now: ZonedDateTime = ZonedDateTime.now()) {
        items.forEach { sync(it, now) }
    }

    fun cancel(id: String) {
        alarmManager.cancel(pendingIntent(id, null, null))
    }

    internal fun scheduleNext(id: String, hour: Int, minute: Int, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val triggerMs = NutritionReminderPlanner.nextTrigger(now, safeHour, safeMinute).toInstant().toEpochMilli()
        val pi = pendingIntent(id, safeHour, safeMinute)
        return try {
            if (canScheduleExactAlarms()) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                    return true
                } catch (_: SecurityException) {
                    // El permiso se revocó entre la comprobación y la llamada: respaldo inexacto.
                }
            }
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, INEXACT_WINDOW_MS, pi)
            true
        } catch (error: Exception) {
            Log.e(TAG, "No se pudo programar el recordatorio de $id", error)
            false
        }
    }

    fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appCtx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun sendNotification(item: SupplementPilaItem) {
        if (!notificationsAllowed()) return
        createChannels()
        val def = SupplementCatalog.resolve(item) ?: return
        val notification = NotificationCompat.Builder(appCtx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kpkn)
            .setContentTitle(appCtx.getString(R.string.notif_supplement_title))
            .setContentText(appCtx.getString(R.string.notif_supplement_text, def.shortName, SupplementEngine.doseLabel(def, item.dose)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(
                KpknDeepLinks.pendingActivityIntent(
                    context = appCtx,
                    requestCode = requestCode(item.id),
                    path = "nutrition",
                ),
            )
            .build()
        try {
            NotificationManagerCompat.from(appCtx).notify(notificationId(item.id), notification)
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo enviar el recordatorio de ${item.id}", error)
        }
    }

    private fun pendingIntent(id: String, hour: Int?, minute: Int?): PendingIntent {
        val intent = Intent(appCtx, SupplementReminderReceiver::class.java).apply {
            // El action forma parte de la identidad del PendingIntent: dos ids con el mismo hash bajo no se pisan.
            action = "$ACTION_PREFIX$id"
            putExtra(EXTRA_ID, id)
            if (hour != null) putExtra(EXTRA_HOUR, hour)
            if (minute != null) putExtra(EXTRA_MINUTE, minute)
        }
        return PendingIntent.getBroadcast(
            appCtx,
            requestCode(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun parseTime(raw: String?): Pair<Int, Int>? {
        if (raw == null) return null
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun requestCode(id: String): Int = stableId(id, 7_000_000)

    private fun notificationId(id: String): Int = stableId(id, 8_000_000)

    // 20 bits de hash estable: el action ya distingue los PendingIntent; esto evita notificaciones cruzadas.
    private fun stableId(id: String, offset: Int): Int = offset + ((id.hashCode() and 0x7FFFFFFF) % 1_000_000)

    private val TAG = "SupplementReminder"
}

/**
 * Recibe la alarma de un suplemento. Si sigue en la pila y con la misma hora, reprograma la siguiente ocurrencia y
 * avisa solo cuando aún faltan dosis hoy. Si se quitó o se desactivó, no hace nada: la cadena muere con ella.
 */
class SupplementReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(SupplementReminderManager.EXTRA_ID) ?: return
        val firedHour = intent.getIntExtra(SupplementReminderManager.EXTRA_HOUR, -1)
        val firedMinute = intent.getIntExtra(SupplementReminderManager.EXTRA_MINUTE, -1)
        val appContext = context.applicationContext
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(appContext, id, firedHour = firedHour, firedMinute = firedMinute)
            } catch (error: Throwable) {
                Log.e(TAG, "Falló el recordatorio del suplemento $id", error)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SupplementReminder"

        /** Lectura y decisión fuera de `onReceive` para poder probarla con una base y un reloj inyectados. */
        internal suspend fun handle(
            context: Context,
            id: String,
            firedHour: Int = -1,
            firedMinute: Int = -1,
            database: KpknDatabase = KpknDatabase.getInstance(context),
            now: ZonedDateTime = ZonedDateTime.now(),
        ) {
            val settings = database.settingsDao().get()?.toSettings() ?: return
            val item = settings.supplementPila.firstOrNull { it.id == id } ?: return
            val reminder = item.reminder ?: return
            val parts = reminder.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return

            val manager = SupplementReminderManager(context)
            manager.createChannels()
            // La próxima ocurrencia se programa siempre que el recordatorio siga activo.
            manager.scheduleNext(id, hour, minute, now)
            // Una alarma vieja (la hora cambió después de programarla) solo se reprograma: no avisa a destiempo.
            if (firedHour in 0..23 && firedMinute in 0..59 && (firedHour != hour || firedMinute != minute)) return

            val today = now.toLocalDate()
            val logs = database.nutritionDao().getLogsForDayPrefix(today.toString())
                .mapNotNull { entity -> runCatching { entity.toNutritionLog() }.getOrNull() }
                .filter { it.status != NutritionStatus.PLANNED }
            val takes = SupplementEngine.takesFromLogs(logs, today.toString())[id].orEmpty()
            if (!SupplementEngine.isDone(item, takes)) manager.sendNotification(item)
        }
    }
}
