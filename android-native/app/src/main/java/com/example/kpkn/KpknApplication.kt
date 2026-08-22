package com.example.kpkn

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import com.example.kpkn.telemetry.KpknTelemetry
import com.example.kpkn.telemetry.nutrition.NutritionCrashHook
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.secure.LegacyAiCredentialCleanup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger
import com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector
import com.example.kpkn.ui.locale.LocaleManager

class KpknApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        KpknDiagnosticLogger.initialize(this)
        KpknDiagnosticLogger.beginSession()
        WorkoutVoiceDiagnosticLogger.initialize(this)
        val isMainProcess = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            Application.getProcessName() == packageName
        if (isMainProcess) {
            applicationScope.launch {
                LegacyAiCredentialCleanup.clear(this@KpknApplication)
                KpknDiagnosticLogger.recordHealthChecks(this@KpknApplication)
            }
        }
        if (isMainProcess) {
            WorkoutVoiceExitInfoCollector.initialize(this)
            // NutriTelemetry: store JSONL local + hook de crashes del proceso.
            // El hook va tras initialize para que el próximo arranque emita
            // previous_session_crash/exit con el contexto persistido.
            NutritionTelemetry.initialize(this)
            NutritionCrashHook.install(this)
        }

        // Enable StrictMode in debug builds to catch disk/network on main thread and leaked closeables.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build()
            )
        }

        val telemetry = KpknTelemetry.getInstance(this)
        telemetry.setUserProperty("app_version", BuildConfig.VERSION_NAME)
        telemetry.setUserProperty("app_version_code", BuildConfig.VERSION_CODE.toString())
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            KpknDiagnosticLogger.flushAsync()
        }
    }
}
