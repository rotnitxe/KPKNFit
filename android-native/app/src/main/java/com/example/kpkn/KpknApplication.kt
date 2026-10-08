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
import com.example.kpkn.data.account.ProfileStorage
import android.content.SharedPreferences
import java.io.File

class KpknApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        ProfileStorage.initialize(base)
        super.attachBaseContext(LocaleManager.wrapContext(base))
    }

    // Platform SDK identity and WorkManager remain global; personal paths follow the selected profile.
    override fun getFilesDir(): File = if (ProfileStorage.initialized()) ProfileStorage.context(baseContext).filesDir else super.getFilesDir()
    override fun getCacheDir(): File = if (ProfileStorage.initialized()) ProfileStorage.context(baseContext).cacheDir else super.getCacheDir()
    override fun getNoBackupFilesDir(): File = if (ProfileStorage.initialized()) ProfileStorage.context(baseContext).noBackupFilesDir else super.getNoBackupFilesDir()
    override fun getDatabasePath(name: String): File = if (ProfileStorage.initialized()) ProfileStorage.context(baseContext).getDatabasePath(name) else super.getDatabasePath(name)
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        if (ProfileStorage.initialized()) ProfileStorage.context(baseContext).getSharedPreferences(name, mode) else super.getSharedPreferences(name, mode)

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
