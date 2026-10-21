package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2, iteración 2: un problema que no invalida los ciclos observados cuesta UN nivel en vez de forzar BAJA (o dejar sin
 * número). La vista oblicua no admitida, los ciclos dudosos con todas las repeticiones completas y el encuadre recortado
 * sin desplazamiento.
 */
class OneLevelPublicationTest {
    private val side = ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true)

    /** Flexiones: cuentan de lado o tumbado, no en oblicua. */
    private val pushUp = RepTestSupport.eccentricProfile()

    /** Cinco repeticiones iguales salvo la tercera, con [third] del recorrido. */
    private fun fiveReps(third: Double = 1.0): CountSeries {
        val b = SignalBuilder().hold(1.0)
        for (k in 0 until 5) b.rep(amplitude = if (k == 2) third else 1.0)
        return b.hold(1.0).series()
    }

    private fun analyze(track: PoseTrackV2, id: AnalysisProfileId) = TechniqueAnalysisEngine.analyze(
        TechniqueAnalysisInput(track, null, RepTestSupport.profile(id), null, null, emptySet()))

    private fun cropFeet(track: PoseTrackV2) = track.mapFrames { _, image, _ ->
        for (lm in listOf(PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX))
            image[lm]?.let { image[lm] = it.copy(x = -0.2f) }
    }

    @Test fun anObliqueViewTheProfileDoesNotAdmitCanBeMediumButNeverHigh() {
        val series = fiveReps()
        val seg = RepSegmenter.segment(series, pushUp, emptySet())
        val oblique = RepConfidence.score(series, seg, side.copy(kind = ViewKind.OBLIQUE), pushUp, emptySet())
        // c_view = 0,7 (§6) deja la puntuación en 0,7: MEDIA, nunca ALTA.
        assertEquals(oblique.toString(), CountConfidence.MEDIA, oblique.level)
        assertNull(oblique.forcedLowReason)
        assertEquals(0.7, oblique.cView, 1e-9)
        // Las demás vistas no admitidas siguen sin ofrecer número.
        val front = RepConfidence.score(series, seg, side.copy(kind = ViewKind.FRONT), pushUp, emptySet())
        assertEquals(CountConfidence.BAJA, front.level)
        assertEquals("unsupported_view", front.forcedLowReason)
    }

    @Test fun anAmbiguousSetOfCompleteRepsCostsOneLevel() {
        // La tercera llega al 74 % del recorrido: completa, pero dentro de la franja dudosa de 0,70 ± 0,08.
        val series = fiveReps(third = 0.74)
        val seg = RepSegmenter.segment(series, pushUp, emptySet())
        assertEquals(RepTestSupport.summary(seg), Triple(5, 0, 0), RepTestSupport.counts(seg))
        assertTrue(RepTestSupport.summary(seg), seg.ambiguousCycles > 0)
        val doubtful = RepConfidence.score(series, seg, side, pushUp, emptySet())
        assertEquals(doubtful.toString(), CountConfidence.MEDIA, doubtful.level)
        assertNull(doubtful.forcedLowReason)
        // Sin la duda sería MEDIA (solo el lado cercano): con la duda baja otro nivel y no se ofrece.
        val nearOnly = RepConfidence.score(series, seg, side, pushUp, setOf(AnalysisFlag.SIDE_UNKNOWN))
        assertEquals(CountConfidence.BAJA, nearOnly.level)
        assertEquals("ambiguous_cycles", nearOnly.forcedLowReason)
    }

    @Test fun anAmbiguousPartialNextToTheFullThresholdStillOffersNoNumber() {
        // Al 66 %: parcial dentro de la franja dudosa, podría ser una completa más.
        val series = fiveReps(third = 0.66)
        val seg = RepSegmenter.segment(series, pushUp, emptySet())
        assertEquals(RepTestSupport.summary(seg), Triple(4, 1, 0), RepTestSupport.counts(seg))
        val b = RepConfidence.score(series, seg, side, pushUp, emptySet())
        assertEquals(CountConfidence.BAJA, b.level)
        assertEquals("ambiguous_cycles", b.forcedLowReason)
    }

    @Test fun aCroppedRegionKeepsOnlyACountThatWouldBeHighAndCapsItAtMedium() {
        val clean = SyntheticLifter.generate(SynthConfig(SynthMovement.SQUAT,
            script = repScript(4, 1.0, 0.3, 1.0, 0.4, lead = 1.0, tail = 1.0))).track
        val plain = analyze(clean, AnalysisProfileId.SQUAT)
        assertEquals(plain.toString(), CountConfidence.ALTA, plain.confidence)
        val cropped = analyze(cropFeet(clean), AnalysisProfileId.SQUAT)
        assertNull(cropped.noRepsReason)
        assertEquals(plain.repCount, cropped.repCount)
        assertEquals(CountConfidence.MEDIA, cropped.confidence)
        assertTrue(MetricKey.REPS in cropped.available)
        // Sin evidencia de ALTA (aquí, una segunda persona), el recorte sigue dejando la serie sin número.
        val crowded = SyntheticLifter.generate(SynthConfig(SynthMovement.SQUAT, noise = SynthNoise().copy(secondPersonFraction = 0.3),
            script = repScript(4, 1.0, 0.3, 1.0, 0.4, lead = 1.0, tail = 1.0))).track
        assertEquals("poor_capture", analyze(cropFeet(crowded), AnalysisProfileId.SQUAT).noRepsReason)
    }
}
