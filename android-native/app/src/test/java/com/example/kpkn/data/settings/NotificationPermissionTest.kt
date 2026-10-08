package com.example.kpkn.data.settings

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.services.notifications.PermissionAwareNotifications
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 33])
class NotificationPermissionTest {
    @Test fun revocationNeverClaimsNotificationWasPosted() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val context: Context = application
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("permission-test", "Test", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, "permission-test").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PermissionAwareNotifications.post(context, 71001, notification))
        assertNotNull(shadowOf(manager).getNotification(71001))
        if (Build.VERSION.SDK_INT >= 33) {
            shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            assertFalse(PermissionAwareNotifications.post(context, 71002, notification))
            assertNull(shadowOf(manager).getNotification(71002))
        }
    }
}
