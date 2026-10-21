package com.example.kpkn.data.media.pose

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class AndroidGpuExitHistoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager: ActivityManager get() = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private fun exit(pid: Int, reason: Int, processName: String = Application.getProcessName()) {
        shadowOf(manager).addApplicationExitInfo(
            ApplicationExitInfoBuilder.newBuilder()
                .setPid(pid)
                .setProcessName(processName)
                .setReason(reason)
                .build(),
        )
    }

    @Test
    fun anAbsentRecordHasNoKnownCause() {
        assertNull(AndroidGpuExitHistory(context).causeOf(123))
    }

    @Test
    fun theAndroidReasonsDistinguishNativeJavaAnrAndOtherExits() {
        val reasons = listOf(
            ApplicationExitInfo.REASON_CRASH_NATIVE to GpuExitCause.NATIVE_CRASH,
            ApplicationExitInfo.REASON_CRASH to GpuExitCause.JAVA_CRASH,
            ApplicationExitInfo.REASON_ANR to GpuExitCause.ANR,
            ApplicationExitInfo.REASON_LOW_MEMORY to GpuExitCause.OTHER,
            ApplicationExitInfo.REASON_USER_REQUESTED to GpuExitCause.OTHER,
            ApplicationExitInfo.REASON_SIGNALED to GpuExitCause.OTHER,
            ApplicationExitInfo.REASON_UNKNOWN to null,
        )
        val history = AndroidGpuExitHistory(context)
        reasons.forEachIndexed { index, (reason, expected) ->
            val pid = 100 + index
            exit(pid, reason)
            assertEquals("motivo Android $reason", expected, history.causeOf(pid))
        }
    }

    @Test
    fun bothThePidAndTheCurrentProcessNameMustMatch() {
        exit(123, ApplicationExitInfo.REASON_CRASH)
        exit(456, ApplicationExitInfo.REASON_CRASH_NATIVE)
        exit(123, ApplicationExitInfo.REASON_CRASH_NATIVE, Application.getProcessName() + ":other")

        assertEquals(GpuExitCause.JAVA_CRASH, AndroidGpuExitHistory(context).causeOf(123))
    }

    @Test
    fun aPidThatOnlyBelongsToAnotherProcessHasNoKnownCause() {
        exit(123, ApplicationExitInfo.REASON_CRASH_NATIVE, Application.getProcessName() + ":other")

        assertNull(AndroidGpuExitHistory(context).causeOf(123))
    }

    @Test
    fun onlyTheSixteenMostRecentRecordsAreConsulted() {
        exit(123, ApplicationExitInfo.REASON_CRASH_NATIVE)
        repeat(16) { exit(200 + it, ApplicationExitInfo.REASON_LOW_MEMORY) }

        assertNull(AndroidGpuExitHistory(context).causeOf(123))
    }

    @Test
    fun anExceptionWhileReadingHistoryHasNoKnownCause() {
        val denied = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? = throw SecurityException("exit history unavailable")
        }

        assertNull(AndroidGpuExitHistory(denied).causeOf(123))
    }

    @Test
    @Config(sdk = [28])
    fun beforeApiThirtyTheHistoryIsUnavailableWithoutCallingActivityManager() {
        val unavailable = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? = error("history must not be queried on API 28")
        }

        assertNull(AndroidGpuExitHistory(unavailable).causeOf(123))
    }
}
