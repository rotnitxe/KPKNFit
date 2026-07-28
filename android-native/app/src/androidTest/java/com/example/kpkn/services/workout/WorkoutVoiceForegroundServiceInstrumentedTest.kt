package com.example.kpkn.services.workout

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutVoiceForegroundServiceInstrumentedTest {

    @Test
    fun serviceDeclaresMicrophoneForegroundType() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, WorkoutVoiceForegroundService::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        assertTrue(
            info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0,
        )
    }
}
