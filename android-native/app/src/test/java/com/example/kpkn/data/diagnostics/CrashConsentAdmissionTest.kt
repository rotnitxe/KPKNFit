package com.example.kpkn.data.diagnostics

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.backup.DataSessionWriters
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class CrashConsentAdmissionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After fun reopenAdmission(): Unit = runBlocking {
        CrashReportingController.setEnabled(false)
        DataSessionWriters.closeAdmission()
        DataSessionWriters.awaitStopped()
        DataSessionWriters.reopenAdmission()
    }

    @Test fun maintenanceCannotBeFollowedByAnUnadmittedConsentWrite(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("diagnostic_consent", 0)
        assertTrue(preferences.edit().clear().commit())
        DataSessionWriters.closeAdmission()
        DataSessionWriters.awaitStopped()

        val result = runCatching { CrashReportingController.changeConsent(context, true) }

        assertFalse("A new personal preference must not be recreated while maintenance owns storage", preferences.contains("crash_reporting"))
        assertTrue("A caller must receive a non-success result when admission is closed", result.isFailure)
    }

    @Test fun anOldEditorCannotOverwriteRestoredConsentAfterReopen(): Unit = runBlocking {
        val old = CrashReportingController.consentEditor(context)
        old.change(false)
        DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped(); DataSessionWriters.reopenAdmission()
        val restored = CrashReportingController.consentEditor(context)
        restored.change(true)
        assertTrue(runCatching { old.change(false) }.isFailure)
        assertTrue(context.getSharedPreferences("diagnostic_consent",0).getBoolean("crash_reporting",false))
    }

    @Test fun anAcceptedConsentChangeDrainsBeforeMaintenanceReturns(): Unit = runBlocking {
        val editor = CrashReportingController.consentEditor(context)
        val outer = com.example.kpkn.data.backup.DataSessionWriter("qa-consent-parent")
        val admitted = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val change = launch { outer.write { admitted.complete(Unit); release.await(); editor.change(true) } }
        admitted.await()
        DataSessionWriters.closeAdmission()
        val stopped = async { DataSessionWriters.awaitStopped() }
        delay(30)
        assertFalse("Maintenance must wait for the accepted dependency", stopped.isCompleted)
        release.complete(Unit)
        withTimeout(5000) { change.join(); stopped.await() }
        assertTrue(context.getSharedPreferences("diagnostic_consent",0).getBoolean("crash_reporting",false))
    }

}
