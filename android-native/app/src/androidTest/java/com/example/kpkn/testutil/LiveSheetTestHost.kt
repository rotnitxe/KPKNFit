package com.example.kpkn.testutil

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.example.kpkn.data.models.VoiceCaptureMode

/** Match MainActivity's window, including the area behind three-button navigation. */
fun ComponentActivity.configureLiveSheetTestWindow() {
    window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
}

/** Exercise returning-user activation without opening hardware or changing first-use coverage. */
fun verifiedVoiceModesForUiFixture(context: Context): AutoCloseable {
    check(context.packageName == "com.example.kpkn.dbg") { "Voice fixtures only run in .dbg" }
    val preferences = context.getSharedPreferences("workout_voice_first_use_v2", Context.MODE_PRIVATE)
    val previous = VoiceCaptureMode.entries.associateWith { mode ->
        if (preferences.contains(mode.name)) preferences.getBoolean(mode.name, false) else null
    }
    check(preferences.edit().apply {
        VoiceCaptureMode.entries.forEach { putBoolean(it.name, true) }
    }.commit())
    return AutoCloseable {
        check(preferences.edit().apply {
            previous.forEach { (mode, value) ->
                if (value == null) remove(mode.name) else putBoolean(mode.name, value)
            }
        }.commit())
    }
}
