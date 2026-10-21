package com.example.kpkn.screens.technique.player

import androidx.compose.runtime.saveable.SaverScope
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El estado del reproductor de técnica (VT7a) con un reproductor de mentira: lo que se le pide al ExoPlayer —paso a paso al
 * milisegundo por debajo, bucle por repetición, arrastre rápido y vuelta al fotograma exacto— y el reloj de la capa. La reproducción
 * real se prueba en VT7b y VT10.
 */
class TechniquePlayerStateTest {
    private class FakePort : TechniquePlayerPort {
        val calls = ArrayList<String>()
        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun seekToMs(positionMs: Long) {
            calls += "seek:$positionMs"
        }

        override fun setSpeed(factor: Float) {
            calls += "speed:$factor"
        }

        override fun setExactSeek(exact: Boolean) {
            calls += "exact:$exact"
        }

        override fun scheduleLoop(startMs: Long, endMs: Long) {
            calls += "loop:$startMs-$endMs"
        }

        override fun cancelLoop() {
            calls += "loop:off"
        }
    }

    private var now = 0L
    private val state = TechniquePlayerState(nanoTime = { now })
    private val port = FakePort()

    /** Fotogramas a 30 fps: 66 667 µs es el 3.º y se busca en 66 ms, nunca en 67. */
    private val frames = longArrayOf(0L, 33_333L, 66_667L, 100_000L, 133_333L)

    private val repA = TechniqueRepMark(0, 1_000_000L, 2_000_000L, 3_000_000L, RepKind.FULL)
    private val repB = TechniqueRepMark(1, 4_000_100L, 5_000_000L, 6_000_900L, RepKind.FULL)
    private val repTooShort = TechniqueRepMark(2, 7_000_000L, 7_100_000L, 7_200_000L, RepKind.FULL)

    private fun overlayWith(reps: List<TechniqueRepMark> = emptyList()) = TechniqueOverlayData(
        videoAspect = 0.5625f,
        frameTimesUs = frames,
        joints = FloatArray(frames.size * OverlayJoint.COUNT * 2) { Float.NaN },
        nearSide = null,
        stepPtsUs = frames,
        trail = null,
        guideX = Float.NaN,
        reps = reps,
        maxGapUs = 150_000L,
    )

    /** Engancha el reproductor de mentira y olvida lo que se le pidió al engancharse. */
    private fun attached(overlay: TechniqueOverlayData? = overlayWith()) {
        state.updateOverlay(overlay)
        state.attach(port)
        port.calls.clear()
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Paso a paso
    // ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun stepForwardPausesAndSeeksToTheFlooredNextPts() {
        attached()
        state.play()
        port.calls.clear()
        state.reportFrame(33_333L)
        state.stepFrame(1)
        // 66 667 µs → 66 ms (el 67 se saltaría este fotograma).
        assertEquals(listOf("pause", "seek:66"), port.calls)
        assertFalse(state.isPlaying)
    }

    @Test
    fun stepBackwardGoesToThePreviousFrame() {
        attached()
        state.reportFrame(100_000L)
        state.stepFrame(-1)
        assertEquals(listOf("pause", "seek:66"), port.calls)
    }

    @Test
    fun quickTapsChainTheStepsEvenIfTheFrameHasNotArrivedYet() {
        attached()
        state.reportFrame(0L)
        now = 0L
        state.stepFrame(1)
        now = 100_000_000L
        state.stepFrame(1)
        assertEquals(listOf("pause", "seek:33", "pause", "seek:66"), port.calls)
        // Pasado el medio segundo el cursor caduca y manda lo que se ve (todavía el fotograma 0).
        port.calls.clear()
        now = 1_000_000_000L
        state.stepFrame(1)
        assertEquals(listOf("pause", "seek:33"), port.calls)
    }

    @Test
    fun stepsStopAtBothEnds() {
        attached()
        state.reportFrame(0L)
        state.stepFrame(-1)
        assertEquals(listOf("pause", "seek:0"), port.calls)
        // Llega el fotograma pedido: el cursor ya no hace falta y manda lo que se ve.
        state.reportFrame(0L)
        port.calls.clear()
        state.reportFrame(133_333L)
        state.stepFrame(1)
        assertEquals(listOf("pause", "seek:133"), port.calls)
    }

    @Test
    fun withoutTheListOfPtsTheStepIsNominal() {
        attached(overlay = null)
        state.reportFrame(100_000L)
        state.stepFrame(1)
        state.stepFrame(-1)
        // 100 000 + 33 333 = 133 333 µs → 133 ms; 100 000 − 33 333 = 66 667 µs → 66 ms.
        assertEquals(listOf("pause", "seek:133", "pause", "seek:66"), port.calls)
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Repetición elegida y bucle
    // ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun selectingARepSeeksToItsStartAndDoesNotPlayWithoutLoop() {
        attached(overlayWith(listOf(repA, repB)))
        state.selectRep(1)
        assertEquals(1, state.selectedRep)
        // 4 000 100 µs → 4000 ms.
        assertEquals(listOf("loop:off", "seek:4000"), port.calls)
        assertFalse(state.isPlaying)
        // Un índice que no existe no hace nada.
        port.calls.clear()
        state.selectRep(7)
        assertEquals(1, state.selectedRep)
        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun theLoopRepeatsTheChosenRepAndFollowsTheSelection() {
        attached(overlayWith(listOf(repA, repB)))
        state.selectRep(1)
        port.calls.clear()
        // Activar el bucle programa el mensaje al final de la repetición (6 000 900 µs → 6000 ms) y coloca la posición en su inicio.
        state.setLoopEnabled(true)
        assertEquals(listOf("loop:4000-6000", "seek:4000"), port.calls)
        port.calls.clear()
        // Cambiar de repetición sustituye el bucle, busca su inicio y la reproduce.
        state.selectRep(0)
        assertEquals(listOf("loop:1000-3000", "seek:1000", "play"), port.calls)
        assertTrue(state.isPlaying)
        // Al desactivarlo se cancela y no se toca la posición.
        port.calls.clear()
        state.setLoopEnabled(false)
        assertEquals(listOf("loop:off"), port.calls)
    }

    @Test
    fun aRepTooShortIsNeverLooped() {
        attached(overlayWith(listOf(repA, repTooShort)))
        state.setLoopEnabled(true)
        port.calls.clear()
        state.selectRep(1)
        assertEquals(listOf("loop:off", "seek:7000"), port.calls)
        assertFalse(state.isPlaying)
    }

    @Test
    fun enablingTheLoopWithoutSelectionTakesTheRepOfTheMoment() {
        attached(overlayWith(listOf(repA, repB)))
        state.reportFrame(4_500_000L)
        state.setLoopEnabled(true)
        assertEquals(1, state.selectedRep)
        // La posición ya cae dentro de la repetición: no se mueve.
        assertEquals(listOf("loop:4000-6000"), port.calls)
    }

    @Test
    fun enablingTheLoopAfterTheLastRepTakesTheLastOneAndGoesToItsStart() {
        attached(overlayWith(listOf(repA, repB)))
        state.reportFrame(9_000_000L)
        state.setLoopEnabled(true)
        assertEquals(1, state.selectedRep)
        assertEquals(listOf("loop:4000-6000", "seek:4000"), port.calls)
    }

    @Test
    fun playingWithTheLoopOnEntersTheRepAtItsStart() {
        attached(overlayWith(listOf(repA, repB)))
        state.selectRep(0)
        state.setLoopEnabled(true)
        state.reportFrame(5_000_000L)
        port.calls.clear()
        state.play()
        assertEquals(listOf("seek:1000", "play"), port.calls)
    }

    @Test
    fun anOverlayWithFewerRepsDropsTheSelection() {
        state.updateOverlay(overlayWith(listOf(repA, repB)))
        state.selectRep(1)
        state.updateOverlay(overlayWith(listOf(repA)))
        assertEquals(-1, state.selectedRep)
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Reproducir, arrastrar y velocidad
    // ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun playPauseAndToggleReachThePlayer() {
        attached()
        state.togglePlay()
        assertTrue(state.isPlaying)
        state.togglePlay()
        assertFalse(state.isPlaying)
        assertEquals(listOf("play", "pause"), port.calls)
    }

    @Test
    fun playingAfterTheEndRestartsFromTheBeginning() {
        attached()
        state.reportPlayWhenReady(true)
        state.reportEnded(true)
        assertFalse(state.isPlaying)
        state.play()
        assertEquals(listOf("seek:0", "play"), port.calls)
        assertTrue(state.isPlaying)
    }

    @Test
    fun scrubbingUsesTheFastSeekAndEndsOnTheExactFrameResumingIfItWasPlaying() {
        attached()
        state.play()
        port.calls.clear()
        state.scrubStart()
        assertFalse(state.isPlaying)
        state.scrubTo(2_000_000L)
        // Dentro del mismo milisegundo no se repite la búsqueda.
        state.scrubTo(2_000_400L)
        state.scrubTo(3_000_000L)
        state.scrubEnd()
        assertEquals(
            listOf("pause", "exact:false", "seek:2000", "seek:3000", "exact:true", "seek:3000", "play"),
            port.calls,
        )
        assertTrue(state.isPlaying)
    }

    @Test
    fun scrubbingWhilePausedStaysPaused() {
        attached()
        state.scrubStart()
        state.scrubTo(1_000_000L)
        state.scrubEnd()
        assertEquals(listOf("pause", "exact:false", "seek:1000", "exact:true", "seek:1000"), port.calls)
        assertFalse(state.isPlaying)
    }

    @Test
    fun seekingBeyondTheDurationIsClamped() {
        attached()
        state.reportDurationMs(31_000L)
        state.seekToUs(99_000_000L)
        state.seekToUs(-5L)
        assertEquals(listOf("seek:31000", "seek:0"), port.calls)
        assertEquals(31_000_000L, state.durationUs)
    }

    @Test
    fun theSpeedReachesThePlayer() {
        attached()
        state.setSpeed(TechniqueSpeed.QUARTER)
        assertEquals(TechniqueSpeed.QUARTER, state.speed)
        assertEquals(listOf("speed:0.25"), port.calls)
    }

    @Test
    fun anErrorStopsPlaybackAndIsReported() {
        attached()
        state.play()
        state.reportError(true)
        assertTrue(state.hasError)
        assertFalse(state.isPlaying)
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Antes de que exista el reproductor y tras un giro de pantalla
    // ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun whatIsAskedBeforeThePlayerExistsIsAppliedWhenItAttaches() {
        state.setSpeed(TechniqueSpeed.HALF)
        state.updateOverlay(overlayWith(listOf(repA, repB)))
        state.setLoopEnabled(true)
        state.play()
        assertTrue(state.isPlaying)
        state.attach(port)
        assertEquals(listOf("exact:true", "speed:0.5", "loop:1000-3000", "seek:1000", "play"), port.calls)
        assertEquals(1_000_000L, state.positionUs)
    }

    @Test
    fun theSaverKeepsPositionRepSpeedAndLoopAcrossARotation() {
        val original = TechniquePlayerState(
            initialPositionUs = 12_345_000L,
            initialRep = 3,
            initialSpeed = TechniqueSpeed.QUARTER,
            initialLoop = true,
        )
        val saver = TechniquePlayerState.Saver
        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(original) }
        val restored = saver.restore(saved!!)!!
        assertEquals(12_345_000L, restored.positionUs)
        assertEquals(3, restored.selectedRep)
        assertEquals(TechniqueSpeed.QUARTER, restored.speed)
        assertTrue(restored.loopEnabled)
        // Al engancharse el reproductor nuevo, vuelve a donde estaba.
        val fresh = FakePort()
        restored.attach(fresh)
        assertTrue("seek:12345" in fresh.calls)
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // El reloj de la capa
    // ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theClockShowsTheFrameWhoseReleaseTimeHasArrived() {
        val clock = FramePresentationClock()
        val t0 = 1_000_000_000L
        clock.onFrame(0L, t0, arrivalNs = t0)
        clock.onFrame(33_333L, t0 + 33_000_000L, arrivalNs = t0 + 10_000_000L)
        clock.onFrame(66_667L, t0 + 66_000_000L, arrivalNs = t0 + 40_000_000L)
        // Nada ha salido antes del primero.
        assertEquals(FramePresentationClock.NO_FRAME, clock.displayedAt(t0 - 1L))
        assertTrue(clock.hasPending)
        // La capa no se adelanta: el segundo fotograma se avisó a los 10 ms pero sale a los 33.
        assertEquals(0L, clock.displayedAt(t0 + 20_000_000L))
        assertTrue(clock.hasPending)
        assertEquals(33_333L, clock.displayedAt(t0 + 40_000_000L))
        assertTrue(clock.hasPending)
        assertEquals(66_667L, clock.displayedAt(t0 + 70_000_000L))
        assertFalse(clock.hasPending)
    }

    @Test
    fun theClockTreatsAMissingOrAbsurdReleaseTimeAsNow() {
        val clock = FramePresentationClock()
        val now = 5_000_000_000L
        // Sin hora de salida: es ahora.
        clock.onFrame(100L, 0L, arrivalNs = now)
        assertEquals(100L, clock.displayedAt(now))
        // Una hora a segundos de distancia se recorta a 200 ms vista.
        clock.onFrame(200L, now + 10_000_000_000L, arrivalNs = now)
        assertEquals(100L, clock.displayedAt(now + 150_000_000L))
        assertEquals(200L, clock.displayedAt(now + 250_000_000L))
    }

    @Test
    fun theClockKeepsWorkingAfterTheRingWrapsAround() {
        val clock = FramePresentationClock()
        val t0 = 1_000_000_000L
        for (i in 0 until 40) clock.onFrame(i * 33_333L, t0 + i * 33_000_000L, arrivalNs = t0 + i * 33_000_000L)
        assertEquals(39 * 33_333L, clock.displayedAt(t0 + 39 * 33_000_000L + 1L))
        assertEquals(38 * 33_333L, clock.displayedAt(t0 + 38 * 33_000_000L + 1L))
    }
}
