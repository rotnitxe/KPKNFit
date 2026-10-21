package com.example.kpkn.data.work

import androidx.work.WorkInfo
import com.example.kpkn.data.models.VideoAnalysisStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TechniqueAnalysisStopPolicyTest(
    private val reasonName: String,
    private val reason: Int,
    private val expected: VideoAnalysisStatus?,
) {
    @Test
    fun theRowStatusFollowsTheStopReason() {
        assertEquals(reasonName, expected, TechniqueAnalysisWorker.rowStatusOnStop(reason))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun reasons(): List<Array<Any?>> = listOf(
            arrayOf("cancelled_by_app", WorkInfo.STOP_REASON_CANCELLED_BY_APP, null),
            arrayOf("unknown", WorkInfo.STOP_REASON_UNKNOWN, null),
            arrayOf("foreground_service_timeout", WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT, VideoAnalysisStatus.QUEUED),
            arrayOf("not_stopped", WorkInfo.STOP_REASON_NOT_STOPPED, VideoAnalysisStatus.QUEUED),
            arrayOf("preempt", WorkInfo.STOP_REASON_PREEMPT, VideoAnalysisStatus.QUEUED),
            arrayOf("timeout", WorkInfo.STOP_REASON_TIMEOUT, VideoAnalysisStatus.QUEUED),
            arrayOf("device_state", WorkInfo.STOP_REASON_DEVICE_STATE, VideoAnalysisStatus.QUEUED),
            arrayOf("battery_not_low", WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW, VideoAnalysisStatus.QUEUED),
            arrayOf("charging", WorkInfo.STOP_REASON_CONSTRAINT_CHARGING, VideoAnalysisStatus.QUEUED),
            arrayOf("connectivity", WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY, VideoAnalysisStatus.QUEUED),
            arrayOf("device_idle", WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE, VideoAnalysisStatus.QUEUED),
            arrayOf("storage_not_low", WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW, VideoAnalysisStatus.QUEUED),
            arrayOf("quota", WorkInfo.STOP_REASON_QUOTA, VideoAnalysisStatus.QUEUED),
            arrayOf("background_restriction", WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION, VideoAnalysisStatus.QUEUED),
            arrayOf("app_standby", WorkInfo.STOP_REASON_APP_STANDBY, VideoAnalysisStatus.QUEUED),
            arrayOf("user", WorkInfo.STOP_REASON_USER, VideoAnalysisStatus.QUEUED),
            arrayOf("system_processing", WorkInfo.STOP_REASON_SYSTEM_PROCESSING, VideoAnalysisStatus.QUEUED),
            arrayOf("estimated_app_launch_time_changed", WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED, VideoAnalysisStatus.QUEUED),
            arrayOf("future_system_reason", 9876, VideoAnalysisStatus.QUEUED),
        )
    }
}
