package com.example.kpkn.screens.technique.player

import com.example.kpkn.domain.biomechanics.metrics.RepPhaseTimes
import com.example.kpkn.screens.technique.TechniqueSamples
import org.junit.Assert.*
import org.junit.Test

/** The overlay must treat native phase timestamps as PTS, never as pose-array indices. */
class NativePlateTimingOverlayTest {
    @Test fun nativeSubPoseFrameMarksDoNotChangeSkeletonOrFrameStepping() {
        val track = TechniqueSamples.squatResult.track
        val baseline = TechniqueSamples.squatReport
        val first = baseline.reps.first()
        val old = first.phases
        // Independent off-grid events: deliberately not rounded to any pose frame.
        val anchorIndex = track.frames.indexOfFirst { it.tUs >= old.turnStartUs }
        val anchorUs = track.frames[anchorIndex].tUs
        // Place the turn strictly inside this pose interval, beyond the existing 500 us
        // player-rounding allowance; that allowance is not a change of the phase clock.
        val phases = RepPhaseTimes(old.departUs + 5137L, anchorUs + 5137L,
            anchorUs + 15_137L, old.arriveUs - 731L)
        val report = baseline.copy(reps = listOf(first.copy(phases = phases)))
        val overlay = TechniqueOverlayData.from(track, null, report, track.frameWidth, track.frameHeight)
        val mark = overlay.reps.single()
        assertEquals(first.index, mark.index)
        assertEquals(phases.departUs, mark.startUs)
        assertEquals((phases.turnStartUs + phases.turnEndUs) / 2, mark.turnUs)
        assertEquals(phases.arriveUs, mark.endUs)
        assertArrayEquals(track.frames.map { it.tUs }.toLongArray(), overlay.stepPtsUs)
        assertArrayEquals(track.frames.map { it.tUs }.toLongArray(), overlay.frameTimesUs)
        assertEquals(anchorIndex, overlay.frameIndexAt(mark.turnUs))
        assertEquals(0, overlay.repIndexAt(mark.turnUs))
    }
}
