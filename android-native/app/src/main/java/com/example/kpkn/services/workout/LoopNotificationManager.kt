package com.example.kpkn.services.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.kpkn.MainActivity
import com.example.kpkn.R

class LoopNotificationManager(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOOP,
                "Loops del programa",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Avisos cuando una semana especial de loop está activa"
            }
        )
    }

    fun notifyLoopActive(programName: String, loopTitle: String) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_LOOP)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Loop activo: $loopTitle")
            .setContentText("$programName tiene una semana especial lista para programar.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$programName tiene una semana de loop activa. Revisa el roadmap y programa sus sesiones."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(loopTitle.hashCode(), notification)
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(appContext, 31020, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_LOOP = "program_loop_events"
    }
}
