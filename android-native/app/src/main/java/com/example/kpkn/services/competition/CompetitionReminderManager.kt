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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.navigation.KpknDeepLinks
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

    fun schedule(record: CompetitionRecord) {
        cancel(record.id)
        val eventAt = record.eventDate?.toEventDateTime(record.startTime) ?: return
        scheduleIfEnabled(record, TYPE_WEEK, eventAt.minusDays(7), record.reminderOneWeekEnabled)
        scheduleIfEnabled(record, TYPE_48H, eventAt.minusHours(48), record.reminder48hEnabled)
        scheduleIfEnabled(record, TYPE_START, eventAt, record.reminderStartEnabled)
    }

    fun cancel(recordId: String) {
        listOf(TYPE_WEEK, TYPE_48H, TYPE_START).forEach { type ->
            alarmManager.cancel(pendingIntent(recordId, type))
        }
    }

    private fun scheduleIfEnabled(record: CompetitionRecord, type: String, triggerAt: LocalDateTime, enabled: Boolean) {
        if (!enabled) return
        val triggerMs = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
    }
}

class CompetitionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasPermission(context)) return
        CompetitionReminderManager(context).createChannels()

        val title = intent.getStringExtra(CompetitionReminderManager.EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Competición"
        val date = intent.getStringExtra(CompetitionReminderManager.EXTRA_DATE).orEmpty()
        val type = intent.getStringExtra(CompetitionReminderManager.EXTRA_TYPE).orEmpty()
        val body = when (type) {
            CompetitionReminderManager.TYPE_WEEK -> "Queda una semana para $title."
            CompetitionReminderManager.TYPE_48H -> "Quedan 48 horas para $title."
            CompetitionReminderManager.TYPE_START -> "$title comienza ahora."
            else -> "Revisa tu registro de competición."
        } + if (date.isNotBlank()) " Fecha: $date." else ""

        val notification = NotificationCompat.Builder(context, CompetitionReminderManager.CHANNEL_COMPETITION)
            .setSmallIcon(com.example.kpkn.R.mipmap.ic_launcher)
            .setContentTitle("KPKN · Competición")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                KpknDeepLinks.pendingActivityIntent(
                    context = context,
                    requestCode = 0,
                    path = "competitions",
                )
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify((title + date + type).hashCode(), notification)
        }
    }

    private fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}

private fun String.toEventDateTime(startTime: String?): LocalDateTime? {
    val date = runCatching { LocalDate.parse(this) }.getOrNull() ?: return null
    val time = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(9, 0)
    return LocalDateTime.of(date, time)
}
