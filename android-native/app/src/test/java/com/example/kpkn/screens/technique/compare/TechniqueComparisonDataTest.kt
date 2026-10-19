package com.example.kpkn.screens.technique.compare

import com.example.kpkn.domain.biomechanics.bar.PlateSeed

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueComparisonDataTest {
    private fun measuredTrack() = BarTrack(
        mediaId = "test", frameWidth = 320, frameHeight = 240, plateDiameterMm = 450, seed = PlateSeed(0L, 100f, 100f, 20f, true),
        trackingContinuity = PlateTrackingContinuity.UNINTERRUPTED,
        samples = listOf(
            BarSample(0L, 100f, 100f, 20f, 1f, 1f, BarSampleSource.HOUGH),
            BarSample(30_000L, 900f, 900f, 100f, 1f, 1f, BarSampleSource.PREDICTED),
            BarSample(60_000L, 110f, 150f, 200f, 1f, 1f, BarSampleSource.NCC),
            BarSample(90_000L, 800f, 800f, 100f, 1f, 1f, BarSampleSource.POSE_PRIOR),
            BarSample(500_000L, 100f, 100f, 20f, 1f, 1f, BarSampleSource.HOUGH),
        ),
    )

    @Test
    fun calibrated_path_uses_report_scales_per_axis_and_only_measured_samples() {
        val report = TechniqueReportSamples.fullReport().copy(reps = emptyList())
        val points = TechniqueComparisonData.extractPath(measuredTrack(), report, true)
        assertEquals(3, points.size)
        assertEquals(2.5f, points[1].dx, 0.0001f)
        assertEquals(12.75f, points[1].dy, 0.0001f)
        assertTrue("Un hueco no debe conectarse como una medición continua", points.last().startsSegment)
    }

    @Test
    fun relative_path_normalizes_pixel_excursion_independently_of_angular_rom() {
        val report = TechniqueReportSamples.fullReport().copy(reps = emptyList(), romMeanDeg = 20.0)
        val first = TechniqueComparisonData.extractPath(measuredTrack(), report, false)
        val changedAngle = TechniqueComparisonData.extractPath(measuredTrack(), report.copy(romMeanDeg = 160.0), false)
        assertEquals(first, changedAngle)
        assertEquals(100f, first[1].dy, 0.0001f)
        assertEquals(20f, first[1].dx, 0.0001f)
        assertTrue(TechniqueComparisonData.extractPath(measuredTrack().copy(samples = measuredTrack().samples.take(1)), report, false).isEmpty())
    }

    @Test
    fun mixed_velocity_units_never_produce_an_absolute_value_or_delta_for_uncalibrated_video() {
        val a = TechniqueReportSamples.fullReport().let { it.copy(available = it.available + MetricKey.VELOCITY_RELATIVE) }
        val b = a.copy(velocityUnit = VelocityUnit.BODY_HEIGHTS_PER_S, mcvBest = 0.7, velocityLossPct = 24.5)
        val rows = TechniqueComparisonData.differenceTable(a, b, measuredTrack(), measuredTrack())
        val mcv = rows.single { it.label == "MCV (mejor)" }
        assertTrue(mcv.valueA.endsWith("m/s"))
        assertEquals("—", mcv.valueB)
        assertEquals("—", mcv.diff)
        assertTrue(rows.single { it.label == "Pérdida de velocidad" }.diff.endsWith("pp"))
        assertFalse(TechniqueComparisonData.differenceTable(b, b, measuredTrack(), measuredTrack()).any { it.label == "MCV (mejor)" })
    }

    @Test
    fun anOldCalibratedReportCannotAuthorizeAnInterruptedOrUnknownCurrentTrack() {
        val oldReport = TechniqueReportSamples.fullReport()
        for (state in listOf(PlateTrackingContinuity.UNKNOWN, PlateTrackingContinuity.INTERRUPTED)) {
            val bar = measuredTrack().copy(trackingContinuity = state)
            assertEquals(null, TechniqueComparisonData.calibratedScale(oldReport, bar))
            assertTrue(TechniqueComparisonData.extractPath(bar, oldReport, true).isEmpty())
            assertTrue(TechniqueComparisonData.extractPath(bar, oldReport, false).isEmpty())
            assertFalse(TechniqueComparisonData.differenceTable(oldReport, oldReport, bar, bar).any { it.label == "MCV (mejor)" })
        }
        val stale = oldReport.copy(engineVersion = oldReport.engineVersion - 1)
        assertEquals(null, TechniqueComparisonData.calibratedScale(stale, measuredTrack()))
        assertTrue(TechniqueComparisonData.extractPath(measuredTrack(), stale, true).isEmpty())
        assertTrue(TechniqueComparisonData.differenceTable(stale, stale, measuredTrack(), measuredTrack()).isEmpty())
    }

    @Test
    fun no_signal_and_missing_tut_do_not_turn_into_measured_zeroes() {
        assertTrue(TechniqueComparisonData.differenceTable(TechniqueReportSamples.minimalReport(), null).isEmpty())
        val report = TechniqueReportSamples.fullReport().copy(tutMs = null)
        assertFalse(TechniqueComparisonData.differenceTable(report, report).any { it.label == "TUT" })
    }
}
