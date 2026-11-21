package com.example.kpkn

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import com.example.kpkn.telemetry.KpknTelemetry
import com.example.kpkn.telemetry.nutrition.NutritionCrashHook
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.secure.LegacyAiCredentialCleanup
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger
import com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector
import com.example.kpkn.ui.locale.LocaleManager
import java.io.File

class KpknApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val processScope: CoroutineScope
        get() = applicationScope

    internal fun flushPendingWritesOnBackground() {
        applicationScope.launch(NonCancellable) {
            withTimeoutOrNull(3_000L) {
                runCatching { ActiveWorkoutHolder.get()?.flushOngoingForBackgroundAndAwait() }
                    .onFailure { KpknDiagnosticLogger.exception("app", "stop_flush_workout_failed", it) }
                runCatching { ProgramRepository.getInstance().flushPendingWrites() }
                    .onFailure { KpknDiagnosticLogger.exception("app", "stop_flush_program_failed", it) }
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        KpknDiagnosticLogger.initialize(this)
        WorkoutVoiceDiagnosticLogger.initialize(this)
        val isMainProcess = resolveProcessName() == packageName
        if (isMainProcess) {
            KpknDiagnosticLogger.beginSession()
            applicationScope.launch {
                LegacyAiCredentialCleanup.clear(this@KpknApplication)
                KpknDiagnosticLogger.recordHealthChecks(this@KpknApplication)
            }
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

        if (isMainProcess) {
            val telemetry = KpknTelemetry.getInstance(this)
            telemetry.setUserProperty("app_version", BuildConfig.VERSION_NAME)
            telemetry.setUserProperty("app_version_code", BuildConfig.VERSION_CODE.toString())
        }
    }

    private fun resolveProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return runCatching {
            File("/proc/self/cmdline").readText(Charsets.US_ASCII)
                .substringBefore('\u0000')
                .trim()
                .takeIf { it.isNotEmpty() }
        }.getOrNull() ?: packageName
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            KpknDiagnosticLogger.flushAsync()
        }
    }
}
