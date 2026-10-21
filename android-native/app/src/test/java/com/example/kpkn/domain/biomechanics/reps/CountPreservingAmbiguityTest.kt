package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3: la duda «por ruido» (un giro junto a un hueco corto, un rizo del reposo) no cambia el número de completas y cuesta un
 * nivel; la duda «por amplitud», un hueco largo en el giro o un movimiento descartado siguen sin ofrecer número.
 */
class CountPreservingAmbiguityTest {
    private val side = ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true)
    private val pushUp = RepTestSupport.eccentricProfile()

    /** Sin la evidencia de ALTA (solo el lado cercano): así decide la regla nueva y no la de M2 (sería ALTA salvo la duda). */
    private val nearOnly = setOf(AnalysisFlag.SIDE_UNKNOWN)

    private val withoutRule = AnalysisTuning.DEFAULT.let {
        it.copy(metrics = it.metrics.copy(countReliability = it.metrics.countReliability.copy(preservingAmbiguityHeight = null)))
    }

    private fun builder(): SignalBuilder {
        val b = SignalBuilder().hold(1.0)
        repeat(5) { b.rep() }
        return b.hold(1.0)
    }

    /** Cinco repeticiones iguales con [gapS] segundos rellenados en el giro de la tercera y, si se pide, caminando en la quinta. */
    private fun fiveReps(gapS: Double, walkLast: Boolean = false): CountSeries {
        val b = builder()
        val clean = b.series()
        val turn = RepSegmenter.segment(clean, pushUp, emptySet()).cycles[2].peak
        val n = (gapS * b.fs).toInt()
        val filled = BooleanArray(b.size) { it in (turn - n / 2) until (turn - n / 2 + n) }
        val lastRep = RepSegmenter.segment(clean, pushUp, emptySet()).cycles[4]
        val walk = BooleanArray(b.size) { walkLast && it in lastRep.leftValley..(lastRep.rightValley ?: lastRep.peak) }
        return b.series(filled = filled, walk = walk)
    }

    @Test fun aShortGapAtTheTurnCostsOneLevelButNeverHigh() {
        val series = fiveReps(gapS = 0.07)
        val seg = RepSegmenter.segment(series, pushUp, emptySet())
        assertEquals(RepTestSupport.summary(seg), Triple(5, 0, 0), RepTestSupport.counts(seg))
        val item = seg.ambiguity.single()
        assertEquals(AmbiguityKind.TURN_IN_GAP, item.kind)
        assertTrue("hueco ${item.gapS}", item.gapS in 0.03..0.10)
        val medium = RepConfidence.score(series, seg, side, pushUp, nearOnly)
        assertEquals(medium.toString(), CountConfidence.MEDIA, medium.level)
        assertNull(medium.forcedLowReason)
        // Sin la regla, la misma duda deja la serie sin número.
        val before = RepConfidence.score(series, seg, side, pushUp, nearOnly, withoutRule)
        assertEquals(CountConfidence.BAJA, before.level)
        assertEquals("ambiguous_cycles", before.forcedLowReason)
        // Con toda la evidencia de ALTA, la duda sigue impidiendo ALTA.
        assertEquals(CountConfidence.MEDIA, RepConfidence.score(series, seg, side, pushUp, emptySet()).level)
    }

    @Test fun aLongGapAtTheTurnStillOffersNoNumber() {
        val series = fiveReps(gapS = 0.40)
        val seg = RepSegmenter.segment(series, pushUp, emptySet())
        assertEquals(RepTestSupport.summary(seg), 5, seg.fullCount)
        val item = seg.ambiguity.single()
        assertEquals(AmbiguityKind.TURN_IN_GAP, item.kind)
        assertTrue("hueco ${item.gapS}", item.gapS > 0.25)
        val b = RepConfidence.score(series, seg, side, pushUp, nearOnly)
        assertEquals(CountConfidence.BAJA, b.level)
        assertEquals("ambiguous_cycles", b.forcedLowReason)
    }

    @Test fun aDiscardedMovementMakesTheDoubtASecondProblem() {
        val series = fiveReps(gapS = 0.07, walkLast = true)
        val seg = RepSegmenter.segment(series, pushUp, emptySet())
        assertTrue(RepTestSupport.summary(seg), seg.discarded.any { it.reason == "nonrep_walk" })
        assertEquals(4, seg.fullCount)
        assertTrue(seg.ambiguity.all { it.kind == AmbiguityKind.TURN_IN_GAP })
        val b = RepConfidence.score(series, seg, side, pushUp, nearOnly)
        assertEquals(CountConfidence.BAJA, b.level)
        assertEquals("ambiguous_cycles", b.forcedLowReason)
    }

    @Test fun onlyRestWigglesAndShortGapsPreserveTheCount() {
        val settings = AnalysisTuning.DEFAULT.metrics.countReliability
        val h = requireNotNull(settings.preservingAmbiguityHeight)
        val g = settings.preservingAmbiguityGapS
        assertTrue(AmbiguityItem(AmbiguityKind.PROMINENCE, 0.2).isCountPreserving(h, g))
        assertTrue(AmbiguityItem(AmbiguityKind.NEAR_PARTIAL, 0.25).isCountPreserving(h, g))
        assertFalse(AmbiguityItem(AmbiguityKind.PROMINENCE, 0.9).isCountPreserving(h, g)) // un giro partido arriba
        assertFalse(AmbiguityItem(AmbiguityKind.NEAR_PARTIAL, 0.5).isCountPreserving(h, g))
        assertFalse(AmbiguityItem(AmbiguityKind.NEAR_PARTIAL, Double.NaN).isCountPreserving(h, g))
        assertTrue(AmbiguityItem(AmbiguityKind.TURN_IN_GAP, 1.0, gapS = 0.1).isCountPreserving(h, g))
        assertTrue(AmbiguityItem(AmbiguityKind.TURN_IN_GAP, 1.0, gapS = 0.25).isCountPreserving(h, g))
        assertFalse(AmbiguityItem(AmbiguityKind.TURN_IN_GAP, 1.0, gapS = 0.4).isCountPreserving(h, g))
        assertFalse(AmbiguityItem(AmbiguityKind.NEAR_FULL, 1.0).isCountPreserving(h, g)) // duda por amplitud
        assertFalse(AmbiguityItem(AmbiguityKind.SIDES, Double.NaN).isCountPreserving(h, g))
    }

    @Test fun anAmplitudeDoubtIsNotNoise() {
        // La tercera al 74 %: completa por poco (duda por amplitud). Sin evidencia de ALTA sigue sin número, como en M2.
        val b = SignalBuilder().hold(1.0)
        for (k in 0 until 5) b.rep(amplitude = if (k == 2) 0.74 else 1.0)
        val series = b.hold(1.0).series()
        val seg = RepSegmenter.segment(series, pushUp, emptySet())
        assertTrue(seg.ambiguity.any { it.kind == AmbiguityKind.NEAR_FULL })
        val low = RepConfidence.score(series, seg, side, pushUp, nearOnly)
        assertEquals(CountConfidence.BAJA, low.level)
        assertEquals("ambiguous_cycles", low.forcedLowReason)
    }
}
