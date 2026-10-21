package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * M4: una repetición no pasa a PARCIAL solo por quedar algo más corta. Cada regla con una señal hecha a mano (reposo 0,
 * completa 1,0, 30 muestras por segundo): el borde censurado, la referencia estable, la parcial aislada entre completas, la
 * fallida que necesita su dejada y el último ciclo que vuelve a medias.
 */
class PartialsGateTest {
    private val eccentric = RepTestSupport.eccentricProfile()
    private val concentric = RepTestSupport.concentricProfile()
    private val side = ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true)

    private var lastTrace = ""

    private fun segment(series: CountSeries, profile: AnalysisProfile): SegmentationResult {
        val (seg, trace) = RepTestSupport.segmentTraced(series, profile)
        lastTrace = trace
        return seg
    }

    private fun describe(seg: SegmentationResult) = RepTestSupport.summary(seg) + "\n   " + lastTrace

    private fun reps(vararg amplitudes: Double): SignalBuilder {
        val b = SignalBuilder().hold(2.0)
        for (a in amplitudes) b.rep(a)
        return b.hold(1.5)
    }

    /** Cuatro completas y una quinta cuya vuelta corta el vídeo a la mitad, con la señal aún bajando deprisa. */
    private fun cutDuringTheLastReturn(): CountSeries {
        val b = SignalBuilder().hold(2.0)
        repeat(4) { b.rep() }
        b.go(1.5, 1.0).hold(0.5).go(1.5, 0.0)
        val values = b.values()
        return SignalBuilder.seriesOf(values.copyOf(values.size - (0.75 * b.fs).roundToInt()))
    }

    @Test
    fun aReturnCutByTheVideoIsNotAPartial() {
        val series = cutDuringTheLastReturn()
        // Arranque E: le falta la concéntrica (la vuelta). No cuenta y la confianza lo trata como borde censurado.
        val e = segment(series, eccentric)
        assertEquals(describe(e), Triple(4, 0, 0), RepTestSupport.counts(e))
        assertTrue(describe(e), e.discarded.any { it.reason == "censored" })
        val confidence = RepConfidence.score(series, e, side, eccentric, emptySet())
        assertEquals(confidence.toString(), CountConfidence.BAJA, confidence.level)
        assertEquals("censored_return", confidence.forcedLowReason)
        // Arranque C: la concéntrica se vio entera; cuenta como completa (y el borde censurado sigue sin dar número).
        val c = segment(series, concentric)
        assertEquals(describe(c), Triple(5, 0, 0), RepTestSupport.counts(c))
        assertEquals(CountConfidence.BAJA, RepConfidence.score(series, c, side, concentric, emptySet()).level)
    }

    @Test
    fun aLastRepThatComesBackHalfwayIsAPartial() {
        fun lastHalfway(holdS: Double): CountSeries {
            val b = SignalBuilder().hold(2.0)
            repeat(4) { b.rep() }
            return b.go(1.5, 1.0).hold(0.5).go(1.5, 0.5).hold(holdS).series()
        }
        // Arranque C: vuelve a la mitad y se queda ahí; no acabó en el bloqueo: es una parcial (acortada, junto al giro).
        val c = segment(lastHalfway(1.5), concentric)
        assertEquals(describe(c), Triple(4, 1, 0), RepTestSupport.counts(c))
        assertEquals(PartialZone.SHORTENED, c.cycles.last().zone)
        // Arranque E: vuelve a la mitad y el vídeo acaba enseguida; no es una dejada: es una parcial en estiramiento.
        val e = segment(lastHalfway(0.3), eccentric)
        assertEquals(describe(e), Triple(4, 1, 0), RepTestSupport.counts(e))
        assertEquals(PartialKind.FAR_END, e.cycles.last().partialKind)
        assertEquals(PartialZone.STRETCHED, e.cycles.last().zone)
        // Con arranque E y la serie acabando en la parada (1,5 s), sigue siendo la fallida del §6.
        val stalled = segment(lastHalfway(1.5), eccentric)
        assertEquals(describe(stalled), Triple(4, 0, 1), RepTestSupport.counts(stalled))
    }

    @Test
    fun aSingleFullCycleIsNoStableReference() {
        // Una completa y una más corta: la amplitud completa sale de un solo ciclo. No hay parcial: cuenta como completa dudosa.
        val single = reps(1.0, 0.45).series()
        val seg = segment(single, eccentric)
        assertEquals(describe(seg), Triple(2, 0, 0), RepTestSupport.counts(seg))
        assertTrue(describe(seg), seg.ambiguity.any { it.kind == AmbiguityKind.SHORT_FULL })
        assertEquals(CountConfidence.BAJA, RepConfidence.score(single, seg, side, eccentric, emptySet()).level)
        // Con dos completas la referencia es estable y la parcial se sostiene.
        val two = segment(reps(1.0, 1.0, 0.45).series(), eccentric)
        assertEquals(describe(two), Triple(2, 1, 0), RepTestSupport.counts(two))
    }

    @Test
    fun anIsolatedShortRepBetweenFullsNeedsAClearDrop() {
        // Al 62 % entre completas: una medida algo corta, no una parcial (completa dudosa).
        val near = segment(reps(1.0, 1.0, 0.62, 1.0, 1.0).series(), eccentric)
        assertEquals(describe(near), Triple(5, 0, 0), RepTestSupport.counts(near))
        assertTrue(describe(near), near.ambiguity.any { it.kind == AmbiguityKind.SHORT_FULL })
        // Al 45 % sí es una caída clara.
        val clear = segment(reps(1.0, 1.0, 0.45, 1.0, 1.0).series(), eccentric)
        assertEquals(describe(clear), Triple(4, 1, 0), RepTestSupport.counts(clear))
        // Al final de la serie (sin completa detrás) el 62 % sigue siendo parcial.
        val last = segment(reps(1.0, 1.0, 1.0, 1.0, 0.62).series(), eccentric)
        assertEquals(describe(last), Triple(4, 1, 0), RepTestSupport.counts(last))
    }

    @Test
    fun aNarrowBumpIsNoiseNotAPartial() {
        // Cinco completas (2 s de anchura a media altura) y, en un descanso, un bache rápido de 0,6 s hasta el 70 %: sin la
        // regla de anchura sería un ciclo más (una parcial aislada entre completas cerca del 0,70, que pasa a completa dudosa);
        // con ella es ruido.
        val b = SignalBuilder().hold(2.0)
        repeat(3) { b.rep() }
        b.hold(0.6).go(0.3, 0.7).go(0.3, 0.0).hold(0.6)
        repeat(2) { b.rep() }
        val series = b.hold(1.5).series()
        val base = AnalysisTuning.DEFAULT
        val off = base.copy(metrics = base.metrics.copy(partials = base.metrics.partials.copy(minPartialWidth = 0.0)))
        val without = RepSegmenter.segment(series, eccentric, emptySet(), off)
        assertEquals(RepTestSupport.summary(without), Triple(6, 0, 0), RepTestSupport.counts(without))
        assertTrue(RepTestSupport.summary(without), without.ambiguity.any { it.kind == AmbiguityKind.SHORT_FULL })
        val seg = segment(series, eccentric)
        assertEquals(describe(seg), Triple(5, 0, 0), RepTestSupport.counts(seg))
        assertTrue(describe(seg), seg.discarded.any { it.reason == "noise" })
    }

    @Test
    fun aNarrowCycleWithItsTurnInAFilledGapKeepsItsDoubt() {
        // El mismo bache, con el giro dentro de un hueco rellenado: sale del número como ruido, pero conserva su duda (puede ser
        // una repetición real mal vista): la serie no llega a ALTA, como cualquier serie con un giro en un hueco largo.
        val b = SignalBuilder().hold(2.0)
        repeat(3) { b.rep() }
        b.hold(0.6)
        val peak = b.size + (0.3 * b.fs).roundToInt() - 1
        b.go(0.3, 0.7).go(0.3, 0.0).hold(0.6)
        repeat(2) { b.rep() }
        b.hold(1.5)
        val filled = BooleanArray(b.size) { it in peak - 6..peak + 6 }
        val series = b.series(filled = filled)
        val seg = segment(series, eccentric)
        assertEquals(describe(seg), Triple(5, 0, 0), RepTestSupport.counts(seg))
        assertTrue(describe(seg), seg.ambiguity.any { it.kind == AmbiguityKind.TURN_IN_GAP })
        val level = RepConfidence.score(series, seg, side, eccentric, emptySet()).level
        assertTrue(level.toString(), level != CountConfidence.ALTA)
    }

    @Test
    fun aTrainOfStretchPulsesIsNotAFailure() {
        // Tras cinco completas, tres rebotes abajo (45 % ⇄ 100 %) con una pausa de 0,7 s en cada valle y vuelta al reposo. La
        // pausa del primero ya no lo vuelve fallido: tras él hay más rebotes, no una dejada que acabe la serie.
        val b = SignalBuilder().hold(2.0)
        repeat(5) { b.rep() }
        b.go(1.5, 1.0).hold(0.3).go(1.0, 0.45).hold(0.7)
        b.go(1.0, 1.0).hold(0.3).go(1.0, 0.45).hold(0.7)
        b.go(1.0, 1.0).hold(0.3).go(1.5, 0.0).hold(2.0)
        val seg = segment(b.series(), eccentric)
        assertEquals(describe(seg), Triple(5, 3, 0), RepTestSupport.counts(seg))
        assertTrue(describe(seg), seg.cycles.filter { it.kind == RepKind.PARTIAL }.all { it.zone == PartialZone.STRETCHED })
    }
}
