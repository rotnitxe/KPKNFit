package com.example.kpkn.screens.technique.compare

import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.screens.technique.player.TechniquePlayerPort
import com.example.kpkn.screens.technique.player.TechniquePlayerState
import com.example.kpkn.screens.technique.player.TechniqueRepMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueComparisonPlaybackTest {
    private class Port : TechniquePlayerPort {
        val seeks = mutableListOf<Long>()
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekToMs(positionMs: Long) { seeks += positionMs }
        override fun setSpeed(factor: Float) = Unit
        override fun setExactSeek(exact: Boolean) = Unit
        override fun scheduleLoop(startMs: Long, endMs: Long) = Unit
        override fun cancelLoop() = Unit
    }

    private fun rep(start: Long) = TechniqueRepMark(0, start, start + 1_000_000L, start + 2_000_000L, RepKind.FULL)

    @Test
    fun starts_at_first_reps_resyncs_boundaries_and_restarts_both_after_one_ends() {
        val a = TechniquePlayerState()
        val b = TechniquePlayerState()
        val portA = Port()
        val portB = Port()
        a.attach(portA); b.attach(portB)
        a.reportDurationMs(10_000L); b.reportDurationMs(14_000L)
        val sync = TechniqueComparisonPlayback(a, b, listOf(rep(1_500_000L), rep(5_000_000L)), listOf(rep(4_000_000L), rep(8_000_000L)))
        sync.alignInitially()
        sync.togglePlayback()
        assertEquals(1500L, portA.seeks.last())
        assertEquals(4000L, portB.seeks.last())
        assertTrue(a.isPlaying && b.isPlaying)
        a.reportFrame(5_000_000L); b.reportFrame(6_000_000L)
        assertEquals(0, sync.repIndexAt(4_999_999L))
        sync.onRepBoundary(sync.repIndexAt(a.positionUs))
        assertEquals(8000L, portB.seeks.last())

        a.reportFrame(9_999_999L)
        a.reportEnded(true)
        sync.onPlaybackChanged()
        assertFalse(sync.isPlaying)
        sync.togglePlayback()
        assertTrue(a.isPlaying && b.isPlaying)
        assertEquals("El reinicio interno del player a 0 no debe ganar a la alineación", 1500L, portA.seeks.last())
        assertEquals(4000L, portB.seeks.last())
    }

    @Test
    fun shared_scrub_pauses_and_resumes_both_and_manual_pause_preserves_position() {
        val a = TechniquePlayerState()
        val b = TechniquePlayerState()
        val portA = Port()
        val portB = Port()
        a.attach(portA); b.attach(portB)
        a.reportDurationMs(10_000L); b.reportDurationMs(14_000L)
        val sync = TechniqueComparisonPlayback(a, b, listOf(rep(1_500_000L)), listOf(rep(4_000_000L)))
        sync.togglePlayback()
        sync.scrubTo(0.5f)
        sync.onPlaybackChanged()
        assertFalse(sync.isPlaying)
        assertEquals(5750L, portA.seeks.last())
        assertEquals(8250L, portB.seeks.last())
        sync.finishScrub()
        assertTrue(a.isPlaying && b.isPlaying)
        sync.togglePlayback()
        val seeksBeforeResume = portA.seeks.size
        sync.togglePlayback()
        assertEquals(seeksBeforeResume, portA.seeks.size)
    }
}
