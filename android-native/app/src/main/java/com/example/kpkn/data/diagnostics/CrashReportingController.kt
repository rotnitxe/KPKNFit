package com.example.kpkn.data.diagnostics

import android.content.Context
import com.example.kpkn.BuildConfig
import com.example.kpkn.data.backup.DataSessionWriter
import com.example.kpkn.data.backup.capturedPersonalContext
import com.example.kpkn.data.media.DataSessionClosedException
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Never send dictation, payloads, user identity, exception messages or nested causes to Crashlytics. */
object CrashReportingController {
    @Volatile private var reporter: FirebaseCrashlytics? = null
    @Volatile private var enabled = false
    @Volatile private var profileContext: Context? = null
    private var sessionWriter: DataSessionWriter? = null
    private var installed = false
    @Volatile private var suspendedForDataSession = false

    @Synchronized fun initialize(app: FirebaseApp) {
        if (BuildConfig.DEBUG || app.name != FirebaseApp.DEFAULT_APP_NAME) return
        reporter = FirebaseCrashlytics.getInstance().also { it.setCrashlyticsCollectionEnabled(false); it.setUserId("") }
        if (!installed) {
            val delegate = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                persistApprovedFatalMarker()
                delegate?.uncaughtException(thread, sanitized(error))
            }
            installed = true
        }
    }

    @Synchronized private fun persistApprovedFatalMarker() {
        if (!enabled) return
        val writer = sessionWriter ?: return
        val fixed = profileContext ?: return
        // Reporting must never prevent delegation of the original fatal exception.
        runCatching {
            writer.immediate {
                check(fixed.getSharedPreferences("diagnostic_consent", 0).edit()
                    .putBoolean("approved_fatal_pending", true).commit())
            }
        }
    }

    @Synchronized fun setEnabled(value: Boolean) {
        enabled = value && !BuildConfig.DEBUG && !suspendedForDataSession
        reporter?.let { sdk ->
            sdk.setUserId(""); sdk.setCrashlyticsCollectionEnabled(false)
            if (!enabled) sdk.deleteUnsentReports()
        }
    }

    @Synchronized fun record(error: Throwable) {
        if (!enabled) return
        val writer = sessionWriter ?: return
        try {
            writer.immediate {
                reporter?.let { sdk ->
                    sdk.recordException(sanitized(error))
                    sdk.sendUnsentReports()
                }
            }
        } catch (_: DataSessionClosedException) {
            // Optional reporting cannot reopen storage or fail an operation during maintenance.
        }
    }

    fun consent(context: Context): Boolean = context.getSharedPreferences("diagnostic_consent", 0).getBoolean("crash_reporting", false)
    internal fun consentEditor(context: Context) = CrashConsentEditor(context)
    suspend fun changeConsent(context: Context, value: Boolean) = consentEditor(context).change(value)

    @Synchronized internal fun persistAcceptedConsent(context: Context, value: Boolean) {
        reporter?.deleteUnsentReports()
        check(context.getSharedPreferences("diagnostic_consent", 0).edit().putBoolean("crash_reporting", value).commit())
        if (!suspendedForDataSession) {
            profileContext = context
            setEnabled(value)
        }
    }

    @Synchronized internal fun pauseForDataSession() {
        suspendedForDataSession = true
        sessionWriter?.closeAdmission()
        sessionWriter = null
        profileContext = null
        setEnabled(false)
    }

    suspend fun attachConsent(context: Context): Unit = withContext(Dispatchers.IO) {
        synchronized(this@CrashReportingController) {
            val fixed = capturedPersonalContext(context)
            val writer = DataSessionWriter("crash-reporting-session")
            try {
                writer.immediate {
                    val preferences = fixed.getSharedPreferences("diagnostic_consent", 0)
                    val accepted = preferences.getBoolean("crash_reporting", false)
                    sessionWriter?.closeAdmission()
                    sessionWriter = writer
                    profileContext = fixed
                    suspendedForDataSession = false
                    setEnabled(accepted)
                    if (enabled && preferences.getBoolean("approved_fatal_pending", false)) {
                        reporter?.sendUnsentReports()
                        check(preferences.edit().remove("approved_fatal_pending").commit())
                    } else reporter?.deleteUnsentReports()
                }
            } catch (error: Throwable) {
                writer.closeAdmission()
                if (sessionWriter === writer) pauseForDataSession()
                throw error
            }
        }
    }

    fun sanitized(error: Throwable): Throwable = RuntimeException(error.javaClass.name).also {
        it.stackTrace = error.stackTrace.take(64).toTypedArray()
    }
}

/** One UI/editor lifetime; retired callers never resolve a fresh writer after maintenance. */
internal class CrashConsentEditor(context: Context) {
    private val fixed = capturedPersonalContext(context)
    private val writer = DataSessionWriter("crash-consent-editor")
    suspend fun change(value: Boolean) = writer.write {
        CrashReportingController.persistAcceptedConsent(fixed, value)
    }
}
