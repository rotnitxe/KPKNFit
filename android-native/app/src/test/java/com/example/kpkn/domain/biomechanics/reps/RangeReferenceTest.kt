package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RangeReference
import com.example.kpkn.domain.biomechanics.metrics.RangeReferenceSource
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4: la referencia de recorrido (una serie hecha solo de parciales), la zona de cada parcial y la confianza propia de las
 * parciales, con señales hechas a mano (reposo 0, completa 1,0, 30 muestras por segundo, alturas corporales).
 */
class RangeReferenceTest {
    private val eccentric = RepTestSupport.eccentricProfile() // flexiones: DROP_SHOULDER_NEAR
    private val concentric = RepTestSupport.concentricProfile() // peso muerto: RISE_SHOULDER_NEAR
    private val side = ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true)

    private fun personal(amplitude: Double = 1.0, signal: CountSignal = CountSignal.DROP_SHOULDER_NEAR, view: ViewKind? = null) =
        RangeReference(amplitude, signal, RangeReferenceSource.PERSONAL, 3, view)

    private fun reps(vararg amplitudes: Double): CountSeries {
        val b = SignalBuilder().hold(2.0)
        for (a in amplitudes) b.rep(a)
        return b.hold(1.5).series()
    }

    @Test
    fun withAPersonalReferenceASeriesOfOnlyPartialsIsSeen() {
        val series = reps(0.4, 0.4, 0.4, 0.4, 0.4)
        // Sin referencia, la serie se mide contra sí misma: cinco completas.
        assertEquals(Triple(5, 0, 0), RepTestSupport.counts(RepSegmenter.segment(series, eccentric, emptySet())))
        // Con la referencia personal: cinco parciales del 40 %, en la parte acortada (sin llegar al fondo de la flexión).
        val seg = RepSegmenter.segment(series, eccentric, emptySet(), reference = personal())
        assertEquals(RepTestSupport.summary(seg), Triple(0, 5, 0), RepTestSupport.counts(seg))
        assertTrue(seg.referenceApplied)
        assertNull(seg.noRepsReason)
        assertTrue(seg.cycles.all { it.zone == PartialZone.SHORTENED && it.partialKind == PartialKind.REST_END })
        assertTrue(seg.cycles.all { it.amplitudeFraction in 0.35..0.45 })
        assertEquals(0.4, seg.cycles.first().amplitude, 0.03)
        // Se puede ofrecer, pero como mucho en MEDIA.
        val count = RepConfidence.score(series, seg, side, eccentric, emptySet())
        assertEquals(count.toString(), CountConfidence.MEDIA, count.level)
        assertEquals(CountConfidence.MEDIA, RepConfidence.partialLevel(seg, count))
    }

    @Test
    fun aReferenceFromAnotherSignalOrViewIsNotUsed() {
        val series = reps(0.4, 0.4, 0.4, 0.4, 0.4)
        val otherSignal = RepSegmenter.segment(series, eccentric, emptySet(), reference = personal(signal = CountSignal.ELBOW_FLEXION))
        assertEquals(Triple(5, 0, 0), RepTestSupport.counts(otherSignal))
        assertFalse(otherSignal.referenceApplied)
        assertEquals("signal", otherSignal.reference?.reason)
        val invalid = RepSegmenter.segment(series, eccentric, emptySet(), reference = personal(amplitude = Double.NaN))
        assertEquals("invalid", invalid.reference?.reason)
        assertEquals(Triple(5, 0, 0), RepTestSupport.counts(invalid))
    }

    @Test
    fun fullRepsSomewhatShorterThanTheReferenceStayFull() {
        // Al 90 % de la referencia (por debajo del 80 % se aplicaría): la serie se mide contra sí misma.
        val seg = RepSegmenter.segment(reps(0.9, 0.9, 0.9, 0.9, 0.9), eccentric, emptySet(), reference = personal())
        assertEquals(RepTestSupport.summary(seg), Triple(5, 0, 0), RepTestSupport.counts(seg))
        assertFalse(seg.referenceApplied)
        // Y la pérdida gradual por fatiga (del 100 % al 75 %) sigue siendo completa, con o sin referencia.
        val fatigue = reps(1.0, 0.95, 0.9, 0.85, 0.8, 0.75)
        assertEquals(Triple(6, 0, 0), RepTestSupport.counts(RepSegmenter.segment(fatigue, eccentric, emptySet())))
        assertEquals(Triple(6, 0, 0), RepTestSupport.counts(RepSegmenter.segment(fatigue, eccentric, emptySet(), reference = personal())))
    }

    @Test
    fun aProfileReferenceCountsLessAndIsNeverOfferedAlone() {
        val series = reps(0.4, 0.4, 0.4, 0.4, 0.4)
        val profileRef = RangeReference(1.0, CountSignal.DROP_SHOULDER_NEAR, RangeReferenceSource.PROFILE, 0)
        val seg = RepSegmenter.segment(series, eccentric, emptySet(), reference = profileRef)
        assertEquals(RepTestSupport.summary(seg), Triple(0, 5, 0), RepTestSupport.counts(seg))
        // Con la confianza del perfil (0,75), cada parcial recorre el 0,4/0,75 de la completa.
        assertTrue(seg.cycles.all { it.amplitudeFraction in 0.48..0.58 })
        val count = RepConfidence.score(series, seg, side, eccentric, emptySet())
        assertEquals(CountConfidence.BAJA, count.level)
        assertEquals("no_full_reps", count.forcedLowReason)
        assertEquals(CountConfidence.BAJA, RepConfidence.partialLevel(seg, count))
    }

    @Test
    fun theZoneFollowsTheUnreachedEndAndTheStartPhase() {
        // Medias desde el reposo: con arranque E (flexiones) se quedan arriba, acortadas; con arranque C (peso muerto), abajo,
        // en estiramiento.
        val short = reps(1.0, 1.0, 1.0, 1.0, 0.45, 0.45)
        val e = RepSegmenter.segment(short, eccentric, emptySet())
        assertEquals(RepTestSupport.summary(e), Triple(4, 2, 0), RepTestSupport.counts(e))
        assertTrue(e.cycles.filter { it.kind == RepKind.PARTIAL }.all { it.zone == PartialZone.SHORTENED })
        val c = RepSegmenter.segment(short, concentric, emptySet())
        assertEquals(RepTestSupport.summary(c), Triple(4, 2, 0), RepTestSupport.counts(c))
        assertTrue(c.cycles.filter { it.kind == RepKind.PARTIAL }.all { it.zone == PartialZone.STRETCHED })
        // Rebotes junto al giro (45 % ⇄ 100 %): con arranque E, en estiramiento; con arranque C, acortadas.
        val b = SignalBuilder().hold(2.0)
        repeat(5) { b.rep() }
        b.go(1.5, 1.0).go(1.5, 0.45).go(1.5, 1.0).go(1.5, 0.45).go(1.5, 1.0).go(1.5, 0.0).hold(2.0)
        val bounce = b.series()
        val eb = RepSegmenter.segment(bounce, eccentric, emptySet())
        assertTrue(RepTestSupport.summary(eb), eb.cycles.filter { it.kind == RepKind.PARTIAL }.let { p -> p.size == 3 && p.all { it.zone == PartialZone.STRETCHED } })
        val cb = RepSegmenter.segment(bounce, concentric, emptySet())
        assertTrue(RepTestSupport.summary(cb), cb.cycles.filter { it.kind == RepKind.PARTIAL }.let { p -> p.size == 3 && p.all { it.zone == PartialZone.SHORTENED } })
    }

    @Test
    fun aSeriesOfOnlyStretchPulsesNeedsTheReferenceAndKeepsItsZone() {
        // Baja del reposo al fondo y rebota entre el fondo y el 55 % hasta el final: solo con la referencia se ve que son parciales
        // en estiramiento (el reposo de la zona es el nivel más bajo, el del principio).
        val b = SignalBuilder().hold(2.0).go(1.5, 1.0)
        repeat(3) { b.go(1.0, 0.55).go(1.0, 1.0) }
        val series = b.go(1.5, 0.55).hold(2.0).series()
        val seg = RepSegmenter.segment(series, eccentric, emptySet(), reference = personal())
        assertEquals(RepTestSupport.summary(seg), 0, seg.fullCount)
        assertTrue(RepTestSupport.summary(seg), seg.partialCount >= 3)
        assertTrue(RepTestSupport.summary(seg), seg.cycles.all { it.kind == RepKind.PARTIAL && it.zone == PartialZone.STRETCHED })
    }

    @Test
    fun partialsHaveTheirOwnConfidence() {
        // Cinco completas y dos parciales claras al final: la de las parciales es la del conteo (ALTA).
        val series = reps(1.0, 1.0, 1.0, 1.0, 1.0, 0.45, 0.45)
        val seg = RepSegmenter.segment(series, eccentric, emptySet())
        assertEquals(RepTestSupport.summary(seg), Triple(5, 2, 0), RepTestSupport.counts(seg))
        val count = RepConfidence.score(series, seg, side, eccentric, emptySet())
        assertEquals(count.toString(), CountConfidence.ALTA, count.level)
        assertEquals(CountConfidence.ALTA, RepConfidence.partialLevel(seg, count))
        // Nunca por encima de la del conteo.
        val nearOnly = RepConfidence.score(series, seg, side, eccentric, setOf(AnalysisFlag.SIDE_UNKNOWN))
        assertEquals(CountConfidence.MEDIA, nearOnly.level)
        assertEquals(CountConfidence.MEDIA, RepConfidence.partialLevel(seg, nearOnly))
        // Una segmentación sin desglose (hecha a mano) no sostiene parciales.
        assertEquals(CountConfidence.BAJA, RepConfidence.partialLevel(seg.copy(partialEvidence = PartialEvidence.NONE), count))
    }

    @Test
    fun aCycleSetAsideAsSetupLeavesThePartialsApproximate() {
        // Una parcial antes de la primera completa se aparta como preparación: si era una parcial, el número no la cuenta. La
        // confianza de las parciales queda como mucho en MEDIA («≈»), aunque la del conteo sea ALTA.
        val series = reps(0.45, 1.0, 1.0, 1.0, 1.0, 1.0, 0.45)
        val seg = RepSegmenter.segment(series, eccentric, emptySet())
        assertTrue(RepTestSupport.summary(seg), seg.discarded.any { it.reason == "setup" })
        val count = RepConfidence.score(series, seg, side, eccentric, emptySet())
        val expected = if (count.level == CountConfidence.ALTA) CountConfidence.MEDIA else count.level
        assertEquals(count.toString(), expected, RepConfidence.partialLevel(seg, count))
    }
}
