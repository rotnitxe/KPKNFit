package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import org.junit.Assert.assertEquals
import org.junit.Test

class PoseStrideForTest {
    private fun stride(fps: Double, degraded: Boolean, max: Double = 35.0, min: Double = AnalysisTuning.DEFAULT.lowFpsBelow) =
        poseStrideFor(fps, max, min, degraded)

    @Test
    fun everyBriefRowMatchesTheNormalRuleAndTheDegradedSafetyMargin() {
        val rows = listOf(
            Triple(24.0, 1, 1),
            Triple(25.0, 1, 1),
            Triple(30.0, 1, 1),
            Triple(40.0, 2, 2),
            Triple(50.0, 2, 2),
            Triple(59.94, 2, 2),
            Triple(60.0, 2, 2),
            Triple(120.0, 2, 3),
        )
        for ((fps, normal, degraded) in rows) {
            assertEquals("normal a $fps fps", normal, stride(fps, degraded = false))
            assertEquals("degradado a $fps fps", degraded, stride(fps, degraded = true))
        }
    }

    @Test
    fun theNormalRuleKeepsItsStrictMaximumBoundaryRegardlessOfTheMinimum() {
        assertEquals(1, stride(35.0, degraded = false))
        assertEquals(2, stride(Math.nextUp(35.0), degraded = false))
        assertEquals(1, stride(40.0, degraded = false, max = 45.0))
        assertEquals(2, stride(120.0, degraded = false, min = 100.0))
    }

    @Test
    fun degradationRequiresTheWholeTwentyPercentMarginWithAnInclusiveBoundary() {
        assertEquals(1, stride(Math.nextDown(48.0), degraded = true, max = 60.0))
        assertEquals(2, stride(48.0, degraded = true, max = 60.0))
        assertEquals(2, stride(Math.nextDown(72.0), degraded = true))
        assertEquals(3, stride(72.0, degraded = true))
        // 59,94 / 3 = 19,98 Hz y 60 / 3 = 20 Hz: ninguno conserva el margen.
        assertEquals(2, stride(59.94, degraded = true))
        assertEquals(2, stride(60.0, degraded = true))
    }

    @Test
    fun theMinimumCadenceComesFromTheArgument() {
        assertEquals(2, stride(30.0, degraded = true, min = 10.0))
        assertEquals(3, stride(60.0, degraded = true, min = 15.0))
        assertEquals(2, stride(120.0, degraded = true, min = 40.0))
    }

    @Test
    fun invalidCadencesAlwaysKeepEveryFrame() {
        for (fps in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -30.0)) {
            for (degraded in listOf(false, true)) {
                assertEquals("cadencia $fps, degradado $degraded", 1, stride(fps, degraded))
            }
        }
    }
}
