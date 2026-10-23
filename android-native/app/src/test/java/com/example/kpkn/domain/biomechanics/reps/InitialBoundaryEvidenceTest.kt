package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5: el retorno visible desde el giro inicial no prueba que la ida también esté dentro del vídeo. */
class InitialBoundaryEvidenceTest {
    private val profile = RepTestSupport.profile(AnalysisProfileId.TRICEPS_EXTENDED_REST)
    private val boundaryTuning = AnalysisTuning.DEFAULT.copy(metrics = AnalysisTuning.DEFAULT.metrics.copy(
        countReliability = AnalysisTuning.DEFAULT.metrics.countReliability.copy(initialBoundaryMaxPeriods = 1.0)))

    private fun interior(builder: SignalBuilder, count: Int = 4, turn: Double = 140.0): SignalBuilder = builder.apply {
        repeat(count) { go(0.7, turn).hold(0.2).go(0.7, 40.0).hold(0.2) }
        hold(0.8)
    }

    private fun result(builder: SignalBuilder): Pair<CountSeries, SegmentationResult> {
        val series = builder.series(unit = SignalUnit.DEGREES)
        return series to RepSegmenter.segment(series, profile, emptySet())
    }

    @Test fun completeReturnFromInitialTurnMakesTheCountUncertainWithoutAddingAFull() {
        val (series, seg) = result(interior(SignalBuilder(start = 140.0).go(0.7, 40.0).hold(0.2)))
        val withoutPriorWarnings = seg.copy(discarded = emptyList())

        assertEquals(4, seg.fullCount)
        assertTrue(countEvidence(series, withoutPriorWarnings, boundaryTuning).censoredStart)
        val confidence = RepConfidence.score(series, withoutPriorWarnings,
            ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true), profile, emptySet(), boundaryTuning)
        assertEquals(CountConfidence.BAJA, confidence.level)
        assertEquals("censored_start", confidence.forcedLowReason)
    }

    @Test fun partialInitialReturnDoesNotImplyAnOmittedFull() {
        val (series, seg) = result(interior(SignalBuilder(start = 80.0).go(0.7, 40.0).hold(0.2)))

        assertEquals(4, seg.fullCount)
        assertFalse(countEvidence(series, seg, boundaryTuning).censoredStart)
    }

    @Test fun zeroBoundaryPeriodLimitDisablesOnlyTheNewGuard() {
        val (series, seg) = result(interior(SignalBuilder(start = 140.0).go(0.7, 40.0).hold(0.2)))
        val withoutPriorWarnings = seg.copy(discarded = emptyList())
        val base = boundaryTuning
        val disabled = base.copy(metrics = base.metrics.copy(
            countReliability = base.metrics.countReliability.copy(initialBoundaryMaxPeriods = 0.0)))

        assertTrue(countEvidence(series, withoutPriorWarnings, base).censoredStart)
        assertFalse(countEvidence(series, withoutPriorWarnings, disabled).censoredStart)
        assertEquals(seg.fullCount, withoutPriorWarnings.fullCount)
    }

    @Test fun longPreparationBetweenInitialReturnAndCyclesIsNotAnAdjacentRep() {
        val (series, seg) = result(interior(SignalBuilder(start = 140.0).go(0.7, 40.0).hold(2.0)))

        assertEquals(4, seg.fullCount)
        assertFalse(countEvidence(series, seg, boundaryTuning).censoredStart)
    }

    @Test fun initialTurnWithoutInteriorReferenceDoesNotCreateARep() {
        val (series, seg) = result(SignalBuilder(start = 140.0).go(0.7, 40.0).hold(2.5))

        assertEquals(0, seg.fullCount)
        assertFalse(countEvidence(series, seg, boundaryTuning).censoredStart)
    }

    @Test fun oneInteriorFullDoesNotProvideACadenceReference() {
        val (series, seg) = result(interior(SignalBuilder(start = 140.0).go(0.7, 40.0).hold(0.2), count = 1))

        assertEquals(1, seg.fullCount)
        assertFalse(countEvidence(series, seg.copy(discarded = emptyList()), boundaryTuning).censoredStart)
    }

    @Test fun twoMiniSetsSeparatedByRestDoNotTurnPreparationIntoBoundaryCensoring() {
        val builder = SignalBuilder(start = 140.0).go(0.7, 40.0).hold(2.0)
        interior(builder, count = 3).hold(12.0)
        interior(builder, count = 2)
        val (series, seg) = result(builder)
        val fulls = seg.cycles.filter { it.kind == RepKind.FULL }

        assertEquals(5, seg.fullCount)
        assertEquals(2, seg.miniSets)
        // Aislamos un FULL de cada bloque: el único hueco entre ellos cruza la pausa de 12 s.
        val acrossRest = seg.copy(cycles = listOf(fulls.first(), fulls.last()), discarded = emptyList())
        assertFalse(countEvidence(series, acrossRest, boundaryTuning).censoredStart)
    }

    @Test fun boundaryUsesTheSelectedSideRatherThanTheAveragedSignal() {
        val left = interior(SignalBuilder(start = 160.0).go(0.7, 40.0).hold(0.2), turn = 160.0).values()
        val right = interior(SignalBuilder(start = 40.0).hold(0.9), turn = 120.0).values()
        assertEquals(left.size, right.size)
        val noisyRight = DoubleArray(right.size) { right[it] + if (it % 2 == 0) 2.0 else -2.0 }
        val mean = DoubleArray(left.size) { (left[it] + noisyRight[it]) / 2.0 }
        val series = SignalBuilder.seriesOf(mean, unit = SignalUnit.DEGREES,
            perSide = mapOf(Side.LEFT to left, Side.RIGHT to noisyRight))
        val seg = RepSegmenter.segment(series, profile, emptySet()).copy(discarded = emptyList())

        assertEquals(SideMode.BOTH_SYNC, seg.sideMode)
        assertEquals(Side.LEFT, seg.selectedSignalSide)
        assertEquals(4, seg.fullCount)
        assertTrue(countEvidence(series, seg, boundaryTuning).censoredStart)
        val averaged = seg.copy(sideMode = SideMode.NOT_APPLICABLE, perSideCounts = emptyMap(), selectedSignalSide = null)
        assertFalse(countEvidence(series, averaged, boundaryTuning).censoredStart)
        assertFalse(countEvidence(series, seg.copy(sideMode = SideMode.ALTERNATING), boundaryTuning).censoredStart)
    }

    @Test fun cutDuringTheInitialOutboundMovementStillNeedsAWarning() {
        val (series, seg) = result(interior(SignalBuilder(start = 95.0).go(0.25, 140.0).go(0.7, 40.0).hold(0.2)))

        assertEquals(4, seg.fullCount)
        assertTrue(countEvidence(series, seg, boundaryTuning).censoredStart)
    }

    @Test fun terminalSetdownDoesNotLookLikeAnInitialBoundaryReturn() {
        val seriesBuilder = interior(SignalBuilder(start = 40.0).hold(1.0))
            .go(0.7, 140.0).hold(0.4)
        val (series, seg) = result(seriesBuilder)

        val evidence = countEvidence(series, seg, boundaryTuning)
        assertFalse(evidence.censoredStart)
    }
}
