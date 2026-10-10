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
    @Volatile private var suspendedForDataSession = false
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
    @Synchronized fun setEnabled(value: Boolean) {
        enabled = value && !BuildConfig.DEBUG && !suspendedForDataSession
        reporter?.let { sdk ->
            sdk.setUserId(""); sdk.setCrashlyticsCollectionEnabled(false)
            if (!enabled) sdk.deleteUnsentReports()
        }
    }
    fun record(error: Throwable) { if (enabled) reporter?.let { it.recordException(sanitized(error)); it.sendUnsentReports() } }
    fun consent(context: android.content.Context): Boolean = context.getSharedPreferences("diagnostic_consent", 0).getBoolean("crash_reporting", false)
    internal fun consentEditor(context: android.content.Context) = CrashConsentEditor(context)
    suspend fun changeConsent(context: android.content.Context, value: Boolean) = consentEditor(context).change(value)
    @Synchronized internal fun persistAcceptedConsent(context: android.content.Context, value: Boolean) {
        reporter?.deleteUnsentReports()
        check(context.getSharedPreferences("diagnostic_consent", 0).edit().putBoolean("crash_reporting", value).commit())
        if (!suspendedForDataSession) {
            profileContext = context
            setEnabled(value)
        }
    }
    @Synchronized internal fun pauseForDataSession() {
        suspendedForDataSession = true
        setEnabled(false)
    }
    suspend fun attachConsent(context: android.content.Context) {
        profileContext = context
        suspendedForDataSession = false
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

/** One UI/editor lifetime; retired callers never resolve a fresh writer after maintenance. */
internal class CrashConsentEditor(context: android.content.Context) {
    private val fixed = com.example.kpkn.data.backup.capturedPersonalContext(context)
    private val writer = com.example.kpkn.data.backup.DataSessionWriter("crash-consent-editor")
    suspend fun change(value: Boolean) = writer.write {
        CrashReportingController.persistAcceptedConsent(fixed, value)
    }
}
