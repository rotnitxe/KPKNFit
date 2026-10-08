package com.example.kpkn.data.diagnostics

import com.example.kpkn.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Never send dictation, payloads, user identity, exception messages or nested causes to Crashlytics. */
object CrashReportingController {
    @Volatile private var reporter: FirebaseCrashlytics? = null
    @Volatile private var enabled = false
    @Volatile private var profileContext: android.content.Context? = null
    private var installed = false
    @Synchronized fun initialize(app: FirebaseApp) {
        if (BuildConfig.DEBUG || app.name != FirebaseApp.DEFAULT_APP_NAME) return
        reporter = FirebaseCrashlytics.getInstance().also { it.setCrashlyticsCollectionEnabled(false); it.setUserId("") }
        if (!installed) {
            val delegate = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                if (enabled) runCatching { profileContext?.getSharedPreferences("diagnostic_consent", 0)?.edit()?.putBoolean("approved_fatal_pending", true)?.commit() }
                delegate?.uncaughtException(thread, sanitized(error))
            }
            installed = true
        }
    }
    fun setEnabled(value: Boolean) {
        enabled = value && !BuildConfig.DEBUG
        reporter?.let { sdk ->
            sdk.setUserId(""); sdk.setCrashlyticsCollectionEnabled(false)
            if (!enabled) sdk.deleteUnsentReports()
        }
    }
    fun record(error: Throwable) { if (enabled) reporter?.let { it.recordException(sanitized(error)); it.sendUnsentReports() } }
    fun consent(context: android.content.Context): Boolean = context.getSharedPreferences("diagnostic_consent", 0).getBoolean("crash_reporting", false)
    suspend fun changeConsent(context: android.content.Context, value: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        reporter?.deleteUnsentReports()
        profileContext = context
        check(context.getSharedPreferences("diagnostic_consent", 0).edit().putBoolean("crash_reporting", value).commit())
        setEnabled(value)
    }
    suspend fun attachConsent(context: android.content.Context) {
        profileContext = context
        setEnabled(consent(context))
        val preferences = context.getSharedPreferences("diagnostic_consent", 0)
        if (enabled && preferences.getBoolean("approved_fatal_pending", false)) {
            reporter?.sendUnsentReports()
            preferences.edit().remove("approved_fatal_pending").commit()
        } else reporter?.deleteUnsentReports()
    }
    fun sanitized(error: Throwable): Throwable = RuntimeException(error.javaClass.name).also {
        it.stackTrace = error.stackTrace.take(64).toTypedArray()
    }
}
