package com.example.kpkn.data.diagnostics

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.BuildConfig
import com.example.kpkn.data.backup.DataSessionWriters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Release control paths only: both Firebase SDK classes are shadowed; no live project or network. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [26, 34],
    shadows = [ProbeCrashlyticsShadow::class, ProbeFirebaseAppShadow::class])
class CrashReportLifecycleAdversarialTest {
    private lateinit var context: Context
    private var priorHandler: Thread.UncaughtExceptionHandler? = null

    @Before fun prepare(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        assertFalse("This probe requires testReleaseUnitTest; BuildConfig is never mutated", BuildConfig.DEBUG)
        ProbeCrashState.reset()
        priorHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> ProbeCrashState.lastFatal = error; ProbeCrashState.delegated.incrementAndGet() }
        for ((name, value) in listOf("reporter" to null, "profileContext" to null, "enabled" to false,
            "installed" to false, "suspendedForDataSession" to false, "sessionWriter" to null)) {
            CrashReportingController::class.java.getDeclaredField(name).apply { isAccessible = true }.set(CrashReportingController, value)
        }
        val constructor = FirebaseApp::class.java.getDeclaredConstructor(Context::class.java, String::class.java, FirebaseOptions::class.java)
            .apply { isAccessible = true }
        val app = constructor.newInstance(context, FirebaseApp.DEFAULT_APP_NAME, null)
        CrashReportingController.initialize(app)
        assertEquals(1, ProbeCrashState.fakeFactoryCalls.get())
        check(context.getSharedPreferences("diagnostic_consent", 0).edit().clear().putBoolean("crash_reporting", true).commit())
        CrashReportingController.attachConsent(context)
    }

    @After fun restore(): Unit = runBlocking {
        ProbeCrashState.recordRelease?.countDown()
        CrashReportingController.pauseForDataSession()
        DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped(); DataSessionWriters.reopenAdmission()
        Thread.setDefaultUncaughtExceptionHandler(priorHandler)
    }

    @Test fun shadowedReleasePathRecordsOnlySanitizedError() {
        CrashReportingController.record(IllegalArgumentException("private dictation", IllegalStateException("private health")))
        assertEquals(1, ProbeCrashState.records.get())
        assertEquals(1, ProbeCrashState.sends.get())
        assertFalse(ProbeCrashState.lastError.toString().contains("private"))
        assertNull(ProbeCrashState.lastError?.cause)
    }

    @Test fun pauseCannotReturnWhileAcceptedRecordCanStillIssueASend() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        ProbeCrashState.recordEntered = entered; ProbeCrashState.recordRelease = release
        val record = Thread { CrashReportingController.record(IllegalStateException("private")) }
        val pauseStarted = CountDownLatch(1); val pauseFinished = CountDownLatch(1)
        val pause = Thread {
            pauseStarted.countDown()
            CrashReportingController.pauseForDataSession()
            ProbeCrashState.pauseReturned.set(true)
            pauseFinished.countDown()
        }
        record.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            pause.start(); assertTrue(pauseStarted.await(3, TimeUnit.SECONDS))
            assertFalse("Pause must drain an accepted record before returning", pauseFinished.await(150, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); record.join(5000); pause.join(5000) }
        assertFalse(record.isAlive); assertFalse(pause.isAlive)
        assertEquals("No SDK send may be issued after pause returned", 0, ProbeCrashState.sendsAfterPause.get())
    }

    @Test fun fatalMarkerCannotBeRecreatedWhenGlobalAdmissionIsClosed(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("diagnostic_consent", 0)
        DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped()
        requireNotNull(Thread.getDefaultUncaughtExceptionHandler()).uncaughtException(Thread.currentThread(), IllegalStateException("private"))
        assertFalse("Fatal marker must respect storage admission even before account pause", preferences.contains("approved_fatal_pending"))
        assertEquals(1, ProbeCrashState.delegated.get())
    }

    @Test fun admittedFatalMarkerDrainsBeforeStorageMaintenance(): Unit = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val gated = object : ContextWrapper(context) {
            @Volatile var armed = false
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                if (armed && name == "diagnostic_consent") {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return super.getSharedPreferences(name, mode)
            }
        }
        CrashReportingController.attachConsent(gated); gated.armed = true
        val handler = requireNotNull(Thread.getDefaultUncaughtExceptionHandler())
        val fatal = Thread { handler.uncaughtException(Thread.currentThread(), IllegalStateException("private")) }
        val drainStarted = CountDownLatch(1); val drained = CountDownLatch(1)
        val maintenance = Thread { runBlocking { drainStarted.countDown(); DataSessionWriters.awaitStopped(); drained.countDown() } }
        fatal.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            DataSessionWriters.closeAdmission()
            maintenance.start(); assertTrue(drainStarted.await(3, TimeUnit.SECONDS))
            assertFalse("Maintenance must drain an accepted fatal marker", drained.await(150, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); fatal.join(5000); maintenance.join(5000) }
        assertFalse(fatal.isAlive); assertFalse(maintenance.isAlive)
        assertTrue(context.getSharedPreferences("diagnostic_consent", 0).getBoolean("approved_fatal_pending", false))
    }

    @Test fun globalClosureBlocksSdkSubmissionsWithoutThrowing(): Unit = runBlocking {
        DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped()
        CrashReportingController.record(IllegalStateException("private"))
        assertEquals(0, ProbeCrashState.records.get())
        assertEquals(0, ProbeCrashState.sends.get())
    }

    @Test fun retiredSessionCannotUploadOrWritePendingUntilAttachedAgain(): Unit = runBlocking {
        DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped(); DataSessionWriters.reopenAdmission()
        CrashReportingController.record(IllegalStateException("private"))
        requireNotNull(Thread.getDefaultUncaughtExceptionHandler())
            .uncaughtException(Thread.currentThread(), IllegalArgumentException("private"))
        assertEquals(0, ProbeCrashState.records.get())
        assertEquals(0, ProbeCrashState.sends.get())
        assertFalse(context.getSharedPreferences("diagnostic_consent", 0).contains("approved_fatal_pending"))
        assertEquals(1, ProbeCrashState.delegated.get())
        CrashReportingController.attachConsent(context)
        CrashReportingController.record(IllegalStateException("private"))
        assertEquals(1, ProbeCrashState.records.get())
        assertEquals(1, ProbeCrashState.sends.get())
    }

    @Test fun pausedFatalStillDelegatesSanitizedError() {
        CrashReportingController.pauseForDataSession()
        val error = IllegalArgumentException("private dictation", IllegalStateException("private health"))
        error.stackTrace = Array(80) { StackTraceElement("TestClass", "method", "Test.kt", it) }
        requireNotNull(Thread.getDefaultUncaughtExceptionHandler()).uncaughtException(Thread.currentThread(), error)
        assertEquals(1, ProbeCrashState.delegated.get())
        assertFalse(context.getSharedPreferences("diagnostic_consent", 0).contains("approved_fatal_pending"))
        assertFalse(ProbeCrashState.lastFatal.toString().contains("private"))
        assertNull(ProbeCrashState.lastFatal?.cause)
        assertEquals(64, requireNotNull(ProbeCrashState.lastFatal).stackTrace.size)
        assertEquals(0, ProbeCrashState.sends.get())
    }

    @Test fun acceptedAttachmentDrainsMarkerBeforeMaintenance(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("diagnostic_consent", 0)
        assertTrue(preferences.edit().putBoolean("approved_fatal_pending", true).commit())
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val gated = gatedContext(entered, release)
        val attachError = AtomicReference<Throwable?>()
        val attach = Thread { try { runBlocking { CrashReportingController.attachConsent(gated) } } catch (error: Throwable) { attachError.set(error) } }
        val drainStarted = CountDownLatch(1); val drained = CountDownLatch(1)
        val maintenance = Thread { runBlocking { drainStarted.countDown(); DataSessionWriters.awaitStopped(); drained.countDown() } }
        attach.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            DataSessionWriters.closeAdmission()
            maintenance.start(); assertTrue(drainStarted.await(3, TimeUnit.SECONDS))
            assertFalse("Attachment must keep an admitted marker removal inside the drain", drained.await(150, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); attach.join(5000); maintenance.join(5000) }
        assertFalse(attach.isAlive); assertFalse(maintenance.isAlive)
        assertNull(attachError.get())
        assertFalse(preferences.contains("approved_fatal_pending"))
        assertEquals(1, ProbeCrashState.sends.get())
    }

    @Test fun pendingMarkerIsPreservedWhenNewAttachmentAdmissionIsClosed(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("diagnostic_consent", 0)
        assertTrue(preferences.edit().putBoolean("approved_fatal_pending", true).commit())
        DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped()
        assertTrue(runCatching { CrashReportingController.attachConsent(context) }.isFailure)
        assertTrue(preferences.getBoolean("approved_fatal_pending", false))
        assertEquals(0, ProbeCrashState.sends.get())
    }

    @Test fun acceptedAttachmentCannotReenableAfterPauseReturns() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val gated = gatedContext(entered, release)
        val attachError = AtomicReference<Throwable?>()
        val attach = Thread { try { runBlocking { CrashReportingController.attachConsent(gated) } } catch (error: Throwable) { attachError.set(error) } }
        val pauseStarted = CountDownLatch(1); val paused = CountDownLatch(1)
        val pause = Thread { pauseStarted.countDown(); CrashReportingController.pauseForDataSession(); paused.countDown() }
        attach.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            pause.start(); assertTrue(pauseStarted.await(3, TimeUnit.SECONDS))
            assertFalse("Pause must serialize with an accepted attachment", paused.await(150, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); attach.join(5000); pause.join(5000) }
        assertFalse(attach.isAlive); assertFalse(pause.isAlive)
        assertNull(attachError.get())
        CrashReportingController.record(IllegalStateException("private"))
        assertEquals(0, ProbeCrashState.records.get())
        assertEquals(0, ProbeCrashState.sends.get())
    }

    @Test fun rejectedPendingRemovalDisablesNewAttachment(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("diagnostic_consent", 0)
        assertTrue(preferences.edit().putBoolean("approved_fatal_pending", true).commit())
        val rejected = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val delegate = super.getSharedPreferences(name, mode)
                if (name != "diagnostic_consent") return delegate
                return object : SharedPreferences by delegate {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = delegate.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                            override fun commit(): Boolean = false
                        }
                    }
                }
            }
        }
        assertTrue(runCatching { CrashReportingController.attachConsent(rejected) }.isFailure)
        assertTrue(preferences.getBoolean("approved_fatal_pending", false))
        // One already-issued SDK request cannot be retracted by the controller.
        assertEquals(1, ProbeCrashState.sends.get())
        CrashReportingController.record(IllegalStateException("private"))
        assertEquals(0, ProbeCrashState.records.get())
    }

    @Test fun throwingSdkSubmissionReleasesItsPermit(): Unit = runBlocking {
        ProbeCrashState.recordFailure = IllegalStateException("simulated SDK failure")
        assertTrue(runCatching { CrashReportingController.record(IllegalStateException("private")) }.isFailure)
        assertEquals(1, ProbeCrashState.records.get())
        assertEquals(0, ProbeCrashState.sends.get())
        DataSessionWriters.closeAdmission()
        withTimeout(1000) { DataSessionWriters.awaitStopped() }
    }

    private fun gatedContext(entered: CountDownLatch, release: CountDownLatch): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (name == "diagnostic_consent") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            return super.getSharedPreferences(name, mode)
        }
    }
}

internal object ProbeCrashState {
    val records = AtomicInteger(); val sends = AtomicInteger(); val sendsAfterPause = AtomicInteger()
    val delegated = AtomicInteger(); val fakeFactoryCalls = AtomicInteger(); val pauseReturned = AtomicBoolean()
    @Volatile var recordEntered: CountDownLatch? = null
    @Volatile var recordRelease: CountDownLatch? = null
    @Volatile var lastError: Throwable? = null
    @Volatile var lastFatal: Throwable? = null
    @Volatile var recordFailure: Throwable? = null
    fun reset() {
        records.set(0); sends.set(0); sendsAfterPause.set(0); delegated.set(0); fakeFactoryCalls.set(0); pauseReturned.set(false)
        recordEntered = null; recordRelease = null; lastError = null; lastFatal = null; recordFailure = null
    }
}

@Implements(FirebaseCrashlytics::class, callThroughByDefault = false)
class ProbeCrashlyticsShadow {
    @Implementation fun recordException(error: Throwable) {
        ProbeCrashState.records.incrementAndGet(); ProbeCrashState.lastError = error
        ProbeCrashState.recordFailure?.let { throw it }
        ProbeCrashState.recordEntered?.countDown()
        check(ProbeCrashState.recordRelease?.await(5, TimeUnit.SECONDS) != false)
    }
    @Implementation fun sendUnsentReports() {
        ProbeCrashState.sends.incrementAndGet()
        if (ProbeCrashState.pauseReturned.get()) ProbeCrashState.sendsAfterPause.incrementAndGet()
    }
    @Implementation fun deleteUnsentReports() { }
    @Implementation fun setUserId(value: String) { }
    @Implementation fun setCrashlyticsCollectionEnabled(value: Boolean) { }
    companion object {
        @JvmStatic @Implementation fun getInstance(): FirebaseCrashlytics {
            ProbeCrashState.fakeFactoryCalls.incrementAndGet()
            return FirebaseCrashlytics::class.java.getDeclaredConstructor(CrashlyticsCore::class.java)
                .apply { isAccessible = true }.newInstance(null)
        }
    }
}

@Implements(FirebaseApp::class, callThroughByDefault = false)
class ProbeFirebaseAppShadow {
    @Implementation fun __constructor__(context: Context?, name: String?, options: FirebaseOptions?) { }
    @Implementation fun getName(): String = FirebaseApp.DEFAULT_APP_NAME
}
