package com.example.kpkn.screens.workout.technique

import android.util.Range
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecorderSessionFactoryTest {

    @Test
    fun tier1_featureGroupSelectedWhenSupportedAndWanted() {
        val caps = CameraFpsCapabilities(
            isFeatureGroup60Supported = true,
            supportedRanges = setOf(Range(15, 30), Range(30, 30), Range(60, 60)),
        )
        val tier = RecorderSessionFactory.chooseTier(caps, want60 = true)
        assertEquals(RecorderFpsTier.FEATURE_GROUP_60, tier)
    }

    @Test
    fun tier2_range60SelectedWhenGroupUnsupportedButRange60Supported() {
        val caps = CameraFpsCapabilities(
            isFeatureGroup60Supported = false,
            supportedRanges = setOf(Range(15, 30), Range(30, 30), Range(60, 60)),
        )
        val tier = RecorderSessionFactory.chooseTier(caps, want60 = true)
        assertEquals(RecorderFpsTier.RANGE_60, tier)
    }

    @Test
    fun tier3_fallback30SelectedWhenNeither60OptionSupported() {
        val caps = CameraFpsCapabilities(
            isFeatureGroup60Supported = false,
            supportedRanges = setOf(Range(15, 30), Range(30, 30)),
        )
        val tier = RecorderSessionFactory.chooseTier(caps, want60 = true)
        assertEquals(RecorderFpsTier.FALLBACK_30, tier)
    }

    @Test
    fun configured_bind_failure_uses_unconfigured_fallback_and_downgrades_effective_grant() {
        val requestedSession = createFeatureGroupSession()
        val desiredWant60 = RecorderSessionFactory.wants60Fps(
            prefer60Setting = true,
            profileId = AnalysisProfileId.DEADLIFT,
        )
        var configuredBindAttempts = 0
        var fallbackBindAttempts = 0
        var diagnosticCount = 0

        val result = RecorderSessionFactory.bindWithFallback(
            session = requestedSession,
            bindConfigured = {
                configuredBindAttempts++
                error("synthetic configured bind failure")
            },
            bindUnconfigured = {
                fallbackBindAttempts++
                "bare-bind"
            },
            onConfiguredBindFailure = { diagnosticCount++ },
        )

        assertEquals("bare-bind", result.camera)
        assertEquals(1, configuredBindAttempts)
        assertEquals(1, fallbackBindAttempts)
        assertEquals(1, diagnosticCount)
        assertTrue(desiredWant60)
        assertEquals(60f, if (desiredWant60) 60f else 30f, 0f)
        assertEquals(RecorderFpsTier.FALLBACK_30, result.effectiveSession.tier)
        assertEquals(30f, result.effectiveSession.grantedFps, 0f)
        assertFalse(result.effectiveSession.is60FpsGranted)
        assertTrue(result.usedUnconfiguredFallback)
        assertTrue(result.effectiveSession.sharesUseCasesWith(requestedSession))
    }

    @Test
    fun fallback30SelectedWhenNotWantedEvenIfSupported() {
        val caps = CameraFpsCapabilities(
            isFeatureGroup60Supported = true,
            supportedRanges = setOf(Range(60, 60)),
        )
        val tier = RecorderSessionFactory.chooseTier(caps, want60 = false)
        assertEquals(RecorderFpsTier.FALLBACK_30, tier)
    }

    @Test
    fun wants60FpsRespectsSettingAndEligibleProfiles() {
        assertFalse(RecorderSessionFactory.wants60Fps(prefer60Setting = false, AnalysisProfileId.SQUAT))
        assertTrue(RecorderSessionFactory.wants60Fps(prefer60Setting = true, AnalysisProfileId.SQUAT))
        assertTrue(RecorderSessionFactory.wants60Fps(prefer60Setting = true, AnalysisProfileId.DEADLIFT))
        assertTrue(RecorderSessionFactory.wants60Fps(prefer60Setting = true, AnalysisProfileId.BENCH))
        assertTrue(RecorderSessionFactory.wants60Fps(prefer60Setting = true, AnalysisProfileId.OHP))
        assertTrue(RecorderSessionFactory.wants60Fps(prefer60Setting = true, AnalysisProfileId.HIP_THRUST))
        assertTrue(RecorderSessionFactory.wants60Fps(prefer60Setting = true, AnalysisProfileId.ROW))
        assertFalse(RecorderSessionFactory.wants60Fps(prefer60Setting = true, AnalysisProfileId.CURL))
        assertFalse(RecorderSessionFactory.wants60Fps(prefer60Setting = true, null))
    }

    @OptIn(ExperimentalSessionConfig::class)
    private fun createFeatureGroupSession(): RecorderSession {
        val preview = Preview.Builder().build()
        val videoCapture = VideoCapture.withOutput(Recorder.Builder().build())
        val sessionConfig = SessionConfig.Builder(preview, videoCapture).build()
        return RecorderSession(
            preview = preview,
            videoCapture = videoCapture,
            sessionConfig = sessionConfig,
            tier = RecorderFpsTier.FEATURE_GROUP_60,
            grantedFps = 60f,
        )
    }
}
