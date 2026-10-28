package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingCapturePolicyTest {

    private val ready = FramingVerdict(ok = true, issues = emptyList(), view = ViewKind.SIDE)
    private val blocked = FramingVerdict(
        ok = false,
        issues = listOf(FramingIssue.FEET_OUT),
        view = ViewKind.SIDE,
    )

    @Test
    fun checkingWaitsForAStableVerdict() {
        assertFalse(FramingCheckStatus.CHECKING.allowsCapture(ready))
        assertFalse(FramingCheckStatus.CHECKING.allowsCapture(blocked))
    }

    @Test
    fun evaluatedRequiresAFavorableVerdict() {
        assertTrue(FramingCheckStatus.EVALUATED.allowsCapture(ready))
        assertFalse(FramingCheckStatus.EVALUATED.allowsCapture(blocked))
    }

    @Test
    fun unavailableIsAnExplicitErrorAndStillAllowsCapture() {
        assertTrue(FramingCheckStatus.UNAVAILABLE.allowsCapture(ready))
        assertTrue(FramingCheckStatus.UNAVAILABLE.allowsCapture(blocked))
    }
}
