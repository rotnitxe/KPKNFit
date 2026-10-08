package com.example.kpkn.services.notifications

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notification permission can be revoked after a caller checks it. */
object PermissionAwareNotifications {
    fun post(context: Context, id: Int, notification: Notification): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
