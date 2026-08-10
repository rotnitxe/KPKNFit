package com.example.kpkn.services.cardio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.domain.cardio.CardioGpsMilestoneEngine
import com.example.kpkn.navigation.KpknDeepLinks

/** Posts deduplicated kilometre notifications and safely no-ops without notification permission. */
class CardioGpsMilestoneNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun notifyReached(sessionKey: String, distanceMeters: Double, targetDistanceKm: Double?) {
        if (sessionKey.isBlank()) return
        val distanceKm = distanceMeters / 1_000.0
        val emitted = readEmitted(sessionKey)
        val reached = CardioGpsMilestoneEngine.reachedKilometres(
            distanceKm = distanceKm,
            alreadyEmitted = emitted,
            targetDistanceKm = targetDistanceKm,
        )
        if (reached.isEmpty() || !canPost()) return
        ensureChannel()
        val editor = preferences.edit()
        reached.forEach { kilometre ->
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Hito de cardio")
                .setContentText("Alcanzaste $kilometre km")
                .setContentIntent(
                    KpknDeepLinks.pendingActivityIntent(
                        context = appContext,
                        requestCode = notificationId(sessionKey, kilometre),
                        path = "training",
                    ),
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { NotificationManagerCompat.from(appContext).notify(notificationId(sessionKey, kilometre), notification) }
            editor.putBoolean(key(sessionKey, kilometre), true)
        }
        editor.apply()
    }

    private fun readEmitted(sessionKey: String): Set<Int> =
        (1..MAX_MILESTONE_LOOKBACK).filter { preferences.getBoolean(key(sessionKey, it), false) }.toSet()

    private fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hitos de cardio", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Kilómetros alcanzados durante cardio GPS"
                setShowBadge(false)
            },
        )
    }

    private fun key(sessionKey: String, kilometre: Int): String = "$PREFERENCE_PREFIX$sessionKey#$kilometre"
    private fun notificationId(sessionKey: String, kilometre: Int): Int =
        ("$sessionKey#$kilometre").hashCode().coerceAtLeast(1)

    companion object {
        private const val PREFERENCES = "cardio_gps_milestones"
        private const val PREFERENCE_PREFIX = "emitted_"
        private const val CHANNEL_ID = "cardio_milestones"
        private const val MAX_MILESTONE_LOOKBACK = 1000
    }
}
