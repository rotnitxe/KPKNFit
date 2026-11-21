package com.example.kpkn.services.competition

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
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toCompetitionRecord
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.navigation.KpknDeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CompetitionReminderManager(context: Context) {

    private val appCtx = context.applicationContext
    private val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_COMPETITION,
            "Competiciones",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Recordatorios de eventos competitivos"
            enableVibration(true)
        }
        val manager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun schedule(record: CompetitionRecord, zoneId: ZoneId = ZoneId.systemDefault()) {
        cancel(record.id)
        if (record.status != CompetitionRecordStatus.PLANNED) return
        val eventAt = record.eventDate?.toEventDateTime(record.startTime) ?: return
        scheduleIfEnabled(record, TYPE_WEEK, eventAt.minusDays(7), record.reminderOneWeekEnabled, zoneId)
        scheduleIfEnabled(record, TYPE_48H, eventAt.minusHours(48), record.reminder48hEnabled, zoneId)
        scheduleIfEnabled(record, TYPE_START, eventAt, record.reminderStartEnabled, zoneId)
        // Follow-up reminder only when an explicit competition start time exists.
        if (!record.startTime.isNullOrBlank()) {
            scheduleIfEnabled(record, TYPE_POST_RESULT, eventAt.plusHours(5), true, zoneId)
        }
    }

    fun cancel(recordId: String) {
        listOf(TYPE_WEEK, TYPE_48H, TYPE_START, TYPE_POST_RESULT).forEach { type ->
            alarmManager.cancel(pendingIntent(recordId, type))
        }
    }

    private fun scheduleIfEnabled(
        record: CompetitionRecord,
        type: String,
        triggerAt: LocalDateTime,
        enabled: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        if (!enabled) return
        val triggerMs = triggerAt.atZone(zoneId).toInstant().toEpochMilli()
        if (triggerMs <= System.currentTimeMillis()) return
        val pendingIntent = pendingIntent(record.id, type, record.title, record.eventDate.orEmpty())
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            }
        }
    }

    private fun pendingIntent(
        recordId: String,
        type: String,
        title: String = "",
        date: String = "",
    ): PendingIntent {
        val requestCode = (recordId + type).hashCode()
        val intent = Intent(appCtx, CompetitionReminderReceiver::class.java).apply {
            putExtra(EXTRA_RECORD_ID, recordId)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DATE, date)
        }
        return PendingIntent.getBroadcast(
            appCtx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_COMPETITION = "competition_reminders"
        const val EXTRA_RECORD_ID = "competition_record_id"
        const val EXTRA_TYPE = "competition_reminder_type"
        const val EXTRA_TITLE = "competition_title"
        const val EXTRA_DATE = "competition_date"
        const val TYPE_WEEK = "week"
        const val TYPE_48H = "48h"
        const val TYPE_START = "start"
        const val TYPE_POST_RESULT = "post_result"
    }
}

class CompetitionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasPermission(context)) return

        val title = intent.getStringExtra(CompetitionReminderManager.EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Competición"
        val recordId = intent.getStringExtra(CompetitionReminderManager.EXTRA_RECORD_ID).orEmpty()
        val date = intent.getStringExtra(CompetitionReminderManager.EXTRA_DATE).orEmpty()
        val type = intent.getStringExtra(CompetitionReminderManager.EXTRA_TYPE).orEmpty()
        if (type == CompetitionReminderManager.TYPE_POST_RESULT && recordId.isNotBlank()) {
            val appContext = context.applicationContext
            val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entity = KpknDatabase.getInstance(appContext).competitionRecordDao().getById(recordId)
                    if (entity?.status == CompetitionRecordStatus.PLANNED.name) {
                        postNotification(appContext, title, recordId, date, type)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to verify competition status before posting notification", t)
                    KpknDiagnosticLogger.exception("app", "competition_reminder_check_failed", t)
                } finally {
                    pendingResult?.finish()
                }
            }
            return
        }
        postNotification(context, title, recordId, date, type)
    }

    private fun postNotification(
        context: Context,
        title: String,
        recordId: String,
        date: String,
        type: String,
    ) {
        CompetitionReminderManager(context).createChannels()
        val body = when (type) {
            CompetitionReminderManager.TYPE_WEEK -> "Queda una semana para $title."
            CompetitionReminderManager.TYPE_48H -> "Quedan 48 horas para $title."
            CompetitionReminderManager.TYPE_START -> "$title comienza ahora."
            CompetitionReminderManager.TYPE_POST_RESULT -> "¿Cómo te fue en la competición?"
            else -> "Revisa tu registro de competición."
        } + if (date.isNotBlank()) " Fecha: $date." else ""

        val notification = NotificationCompat.Builder(context, CompetitionReminderManager.CHANNEL_COMPETITION)
            .setSmallIcon(com.example.kpkn.R.drawable.ic_stat_kpkn)
            .setContentTitle("KPKN · Competición")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                KpknDeepLinks.pendingActivityIntent(
                    context = context,
                    requestCode = 0,
                    path = if (recordId.isNotBlank()) {
                        "competition/$recordId"
                    } else {
                        "profile"
                    },
                )
            )
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                NotificationManagerCompat.from(context).notify((title + date + type).hashCode(), notification)
            }
        }
    }

    private companion object {
        const val TAG = "CompetitionReminderRcv"
    }
}

/** Rebuilds all future competition alarms after a reboot, package replacement, or timezone change. */
class CompetitionReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        val zoneId = zoneFor(intent)
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reschedulePersistedRecords(appContext, zoneId = zoneId)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to reschedule competition reminders after $action", t)
                KpknDiagnosticLogger.exception("app", "competition_boot_reschedule_failed", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CompetitionReminderBoot"

        internal val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        suspend fun reschedulePersistedRecords(
            context: Context,
            database: KpknDatabase = KpknDatabase.getInstance(context),
            zoneId: ZoneId = ZoneId.systemDefault(),
        ) {
            val manager = CompetitionReminderManager(context)
            manager.createChannels()
            database.competitionRecordDao()
                .getAll()
                .forEach { entity ->
                    runCatching {
                        val record = entity.toCompetitionRecord()
                        if (record.status == CompetitionRecordStatus.PLANNED) {
                            manager.schedule(record, zoneId)
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Skipping unreadable competition record ${entity.id}", error)
                    }
                }
        }

        internal fun zoneFor(intent: Intent): ZoneId {
            val id = intent.getStringExtra(Intent.EXTRA_TIMEZONE) ?: return ZoneId.systemDefault()
            return try {
                ZoneId.of(id)
            } catch (_: DateTimeException) {
                ZoneId.systemDefault()
            }
        }
    }
}

private fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

private fun String.toEventDateTime(startTime: String?): LocalDateTime? {
    val date = runCatching { LocalDate.parse(this) }.getOrNull() ?: return null
    val time = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(9, 0)
    return LocalDateTime.of(date, time)
}
