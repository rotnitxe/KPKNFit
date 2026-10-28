package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenFramingGuidePolicyTest {

    private fun verdict(vararg issues: FramingIssue, ok: Boolean = issues.isEmpty()) =
        FramingVerdict(ok = ok, issues = issues.toList(), view = ViewKind.SIDE)

    @Test
    fun checkingIsSilent() {
        val policy = SpokenFramingGuidePolicy()
        assertNull(policy.onEvaluation(FramingCheckStatus.CHECKING, verdict(), nowMs = 0L))
    }

    @Test
    fun readyIsAnnouncedOncePerStableStreak() {
        val policy = SpokenFramingGuidePolicy()
        assertEquals(
            FramingInstruction.READY_SPEECH,
            policy.onEvaluation(FramingCheckStatus.EVALUATED, verdict(), nowMs = 0L),
        )
        assertNull(policy.onEvaluation(FramingCheckStatus.EVALUATED, verdict(), nowMs = 5_000L))
        // Un problema reinicia la racha; al volver a estar listo se anuncia de nuevo.
        policy.onEvaluation(
            FramingCheckStatus.EVALUATED,
            verdict(FramingIssue.FEET_OUT),
            nowMs = 20_000L,
        )
        assertEquals(
            FramingInstruction.READY_SPEECH,
            policy.onEvaluation(FramingCheckStatus.EVALUATED, verdict(), nowMs = 40_000L),
        )
    }

    @Test
    fun sameIssueIsDeduplicatedWithinWindow() {
        val policy = SpokenFramingGuidePolicy()
        assertEquals(
            FramingInstruction.spoken(FramingIssue.FEET_OUT),
            policy.onEvaluation(
                FramingCheckStatus.EVALUATED,
                verdict(FramingIssue.FEET_OUT),
                nowMs = 0L,
            ),
        )
        assertNull(
            policy.onEvaluation(
                FramingCheckStatus.EVALUATED,
                verdict(FramingIssue.FEET_OUT),
                nowMs = 5_000L,
            ),
        )
        assertEquals(
            FramingInstruction.spoken(FramingIssue.FEET_OUT),
            policy.onEvaluation(
                FramingCheckStatus.EVALUATED,
                verdict(FramingIssue.FEET_OUT),
                nowMs = 13_000L,
            ),
        )
    }

    @Test
    fun aDifferentIssueWaitsForMinimumInterval() {
        val policy = SpokenFramingGuidePolicy()
        policy.onEvaluation(
            FramingCheckStatus.EVALUATED,
            verdict(FramingIssue.FEET_OUT),
            nowMs = 0L,
        )
        assertNull(
            policy.onEvaluation(
                FramingCheckStatus.EVALUATED,
                verdict(FramingIssue.TILTED),
                nowMs = 1_000L,
            ),
        )
        assertEquals(
            FramingInstruction.spoken(FramingIssue.TILTED),
            policy.onEvaluation(
                FramingCheckStatus.EVALUATED,
                verdict(FramingIssue.TILTED),
                nowMs = 3_000L,
            ),
        )
    }

    @Test
    fun speaksOnlyThePriorityIssue() {
        val policy = SpokenFramingGuidePolicy()
        val text = policy.onEvaluation(
            FramingCheckStatus.EVALUATED,
            verdict(FramingIssue.TWO_PEOPLE, FramingIssue.FEET_OUT),
            nowMs = 0L,
        )
        assertEquals(FramingInstruction.spoken(FramingIssue.TWO_PEOPLE), text)
    }

    @Test
    fun unavailableIsAnnouncedOnceUntilItRecovers() {
        val policy = SpokenFramingGuidePolicy()
        assertEquals(
            FramingInstruction.UNAVAILABLE_SPEECH,
            policy.onEvaluation(FramingCheckStatus.UNAVAILABLE, verdict(), nowMs = 0L),
        )
        assertNull(policy.onEvaluation(FramingCheckStatus.UNAVAILABLE, verdict(), nowMs = 5_000L))
        policy.onEvaluation(
            FramingCheckStatus.EVALUATED,
            verdict(FramingIssue.FEET_OUT),
            nowMs = 20_000L,
        )
        assertEquals(
            FramingInstruction.UNAVAILABLE_SPEECH,
            policy.onEvaluation(FramingCheckStatus.UNAVAILABLE, verdict(), nowMs = 40_000L),
        )
    }

    @Test
    fun manualSpeechSuppressesTheImmediateDuplicate() {
        val policy = SpokenFramingGuidePolicy()
        policy.noteSpoken(FramingInstruction.spoken(FramingIssue.FEET_OUT), nowMs = 1_000L)
        assertNull(
            policy.onEvaluation(
                FramingCheckStatus.EVALUATED,
                verdict(FramingIssue.FEET_OUT),
                nowMs = 2_000L,
            ),
        )
    }

    @Test
    fun frontViewChangesTheSpokenInstruction() {
        val policy = SpokenFramingGuidePolicy()
        val text = policy.onEvaluation(
            FramingCheckStatus.EVALUATED,
            verdict(FramingIssue.WRONG_VIEW),
            preferredView = ViewKind.FRONT,
            nowMs = 0L,
        )
        assertTrue(text!!.contains("de frente"))
        assertFalse(text.contains("de lado"))
    }
}
