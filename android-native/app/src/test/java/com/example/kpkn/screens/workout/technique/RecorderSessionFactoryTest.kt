package com.example.kpkn.screens.workout.technique

import android.util.Range
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
}
