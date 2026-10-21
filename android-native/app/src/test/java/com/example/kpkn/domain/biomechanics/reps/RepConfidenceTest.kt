package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.pose.LegacyPoseTrackAdapter
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/** La confianza del conteo (§6): sus casos con el generador y la fórmula factor a factor con datos a mano. */
class RepConfidenceTest {
    private fun gen(
        movement: SynthMovement,
        script: List<SynthSegment> = repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0),
        camera: SynthCamera = SynthCamera(),
        noise: SynthNoise = SynthNoise(),
        fps: Double = 30.0,
    ): SynthResult = SyntheticLifter.generate(SynthConfig(movement, camera = camera, fps = fps, script = script, noise = noise))

    private fun score(run: RepTestSupport.Run, flags: Set<AnalysisFlag> = run.prepared.flags): ConfidenceBreakdown =
        RepConfidence.score(run.series, run.seg, run.prepared.view, run.profile, flags)

    private fun describe(b: ConfidenceBreakdown) =
        "cTrack=${b.cTrack} cSnr=${"%.2f".format(b.cSnr)} cAmp=${"%.2f".format(b.cAmp)} cRhy=${"%.2f".format(b.cRhy)} cView=${b.cView} " +
            "nAmb=${b.nAmbiguous} score=${"%.3f".format(b.score)} nivel=${b.level} forzada=${b.forcedLowReason}"

    // ---- casos con el generador ----

    @Test
    fun aCleanSideViewIsHigh() {
        val r = gen(SynthMovement.SQUAT)
        val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))!!
        val b = score(run)
        println("[confianza] limpio y de lado: ${describe(b)}")
        assertEquals(Triple(5, 0, 0), RepTestSupport.counts(run.seg))
        assertEquals(describe(b), CountConfidence.ALTA, b.level)
        assertNull(b.forcedLowReason)
        assertEquals(0, b.nAmbiguous)
    }

    @Test
    fun anObliqueViewOutsideTheCountViewsIsNotHigh() {
        // Los gemelos solo se cuentan de lado: a 45° la vista es oblicua (c_view = 0,7).
        val r = gen(SynthMovement.CALF_RAISE, camera = SynthCamera(yawDeg = 45.0))
        val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.CALF))!!
        val b = score(run)
        println("[confianza] oblicuo a 45°: vista=${run.prepared.view.kind} ${describe(b)}")
        assertEquals(ViewKind.OBLIQUE, run.prepared.view.kind)
        assertEquals(0.7, b.cView, 1e-9)
        assertNotEquals(describe(b), CountConfidence.ALTA, b.level)
        assertEquals(Triple(5, 0, 0), RepTestSupport.counts(run.seg))
    }

    @Test
    fun aSecondPersonLegacyTracksAndLowFrameRatesAreNotHigh() {
        val profile = RepTestSupport.profile(AnalysisProfileId.SQUAT)
        // MULTI_PERSON: la 2.ª pose aparece en el 30 % de los fotogramas.
        val multi = RepTestSupport.run(gen(SynthMovement.SQUAT, noise = SynthNoise().copy(secondPersonFraction = 0.3)), profile)!!
        val bm = score(multi)
        println("[confianza] 2.ª persona: flags=${multi.prepared.flags} ${describe(bm)}")
        assertTrue(AnalysisFlag.MULTI_PERSON in multi.prepared.flags)
        assertNotEquals(describe(bm), CountConfidence.ALTA, bm.level)
        // Traza v1 a 10 Hz: cuenta, pero nunca ALTA (y con fs < 20 queda en BAJA).
        val r = gen(SynthMovement.SQUAT)
        val legacy = RepTestSupport.run(TrackPreparation.prepare(LegacyPoseTrackAdapter.toV2(RepTestSupport.legacyV1(r))), profile)!!
        val bl = score(legacy)
        println("[confianza] traza v1: flags=${legacy.prepared.flags} ${describe(bl)}")
        assertTrue(AnalysisFlag.LEGACY_TRACK in legacy.prepared.flags)
        assertNotEquals(describe(bl), CountConfidence.ALTA, bl.level)
        // 15 fps: fs < 20 fuerza BAJA aunque el conteo sea bueno.
        val slow = RepTestSupport.run(gen(SynthMovement.SQUAT, fps = 15.0), profile)!!
        val bs = score(slow)
        println("[confianza] 15 fps: flags=${slow.prepared.flags} ${describe(bs)}")
        assertTrue(AnalysisFlag.LOW_FPS in slow.prepared.flags)
        assertEquals(CountConfidence.BAJA, bs.level)
        assertEquals("low_fps", bs.forcedLowReason)
    }

    @Test
    fun aSeriesOfIdenticalShortRepsIsFullAgainstItselfAndThenTheForcedLowIsOnlyForNoFullReps() {
        // Cinco repeticiones al 55 % del recorrido, todas iguales: la amplitud completa (Afull) sale de la propia serie, así
        // que el conteo las da por completas. El recorrido real lo juzgan la amplitud mínima del perfil y las métricas de ROM.
        val script = listOf<SynthSegment>(SynthSegment.Idle(2.0)) +
            List(5) { SynthSegment.Rep(1.5, 0.5, 1.5, 0.5, 1.0, null, SynthRepKind.PARTIAL_FAR) } + SynthSegment.Idle(2.0)
        val r = gen(SynthMovement.PUSH_UP, script)
        val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.PUSH_UP))!!
        val b = score(run)
        println("[confianza] cinco al 55 %: ${RepTestSupport.summary(run.seg)} ${describe(b)}")
        assertEquals(Triple(5, 0, 0), RepTestSupport.counts(run.seg))
        assertNull(b.forcedLowReason)
    }

    // ---- la fórmula, factor a factor ----

    private fun view(kind: ViewKind) = ViewEstimate(kind, null, null, null, true)

    private fun full(amplitude: Double, peak: Int, miniSet: Int = 0) = RepCycle(0, RepKind.FULL, null, null, peak - 20, peak, peak + 20, amplitude, false, miniSet)

    private fun seg(
        cycles: List<RepCycle>,
        aFull: Double = 1.0,
        sigma: Double = 0.01,
        mode: SideMode = SideMode.NOT_APPLICABLE,
        ambiguous: Int = 0,
        flipped: Boolean = false,
    ) = SegmentationResult(
        cycles = cycles.mapIndexed { i, c -> c.copy(index = i) },
        discarded = emptyList(),
        activeWindow = null,
        miniSets = 1,
        periodS = 4.0,
        noiseSigma = sigma,
        aFull = aFull,
        lRest = 0.0,
        sideMode = mode,
        perSideCounts = emptyMap(),
        polarityFlipped = flipped,
        noRepsReason = null,
        ambiguousCycles = ambiguous,
    )

    /** Cinco completas de amplitud 1 con el mismo intervalo. */
    private fun perfectCycles() = List(5) { full(1.0, 100 + 120 * it) }

    private val profile = RepTestSupport.profile(AnalysisProfileId.SQUAT)
    private val handSeries = SignalBuilder().hold(1.0).rep().hold(1.0)

    private fun score(
        cycles: List<RepCycle> = perfectCycles(),
        validFraction: Double = 1.0,
        aFull: Double = 1.0,
        sigma: Double = 0.01,
        kind: ViewKind = ViewKind.SIDE,
        flags: Set<AnalysisFlag> = emptySet(),
        mode: SideMode = SideMode.NOT_APPLICABLE,
        ambiguous: Int = 0,
        flipped: Boolean = false,
        fs: Double = 30.0,
    ): ConfidenceBreakdown {
        // Formula fixtures must have real support at their declared valleys and peaks.
        val raw = DoubleArray((cycles.maxOfOrNull { it.rightValley ?: it.peak } ?: 0) + 60)
        for (c in cycles) {
            for (k in c.leftValley..c.peak) raw[k] = (k - c.leftValley).toDouble() / (c.peak - c.leftValley)
            val right = c.rightValley ?: c.peak
            for (k in c.peak + 1..right) raw[k] = (right - k).toDouble() / (right - c.peak)
        }
        val oriented = if (flipped) DoubleArray(raw.size) { -raw[it] } else raw
        val series = SignalBuilder.seriesOf(oriented, fs = fs, validFraction = validFraction)
        return RepConfidence.score(series, seg(cycles, aFull, sigma, mode, ambiguous, flipped), view(kind), profile, flags)
    }

    @Test
    fun theTrackFactorGoesFrom70To95Percent() {
        assertEquals(0.0, score(validFraction = 0.70).cTrack, 1e-9)
        assertEquals(0.5, score(validFraction = 0.825).cTrack, 1e-9)
        assertEquals(1.0, score(validFraction = 0.95).cTrack, 1e-9)
        assertEquals(1.0, score(validFraction = 1.0).cTrack, 1e-9)
        assertEquals(0.0, score(validFraction = 0.5).cTrack, 1e-9)
    }

    @Test
    fun theSignalToNoiseFactorIsLogarithmicBetween5And30() {
        assertEquals(0.0, score(aFull = 0.05, sigma = 0.01).cSnr, 1e-9)
        assertEquals(0.5, score(aFull = 0.01 * 5.0 * sqrt(6.0), sigma = 0.01).cSnr, 1e-9)
        assertEquals(1.0, score(aFull = 0.30, sigma = 0.01).cSnr, 1e-9)
        assertEquals(1.0, score(aFull = 5.0, sigma = 0.01).cSnr, 1e-9)
        assertEquals(0.0, score(aFull = 0.02, sigma = 0.01).cSnr, 1e-9)
        // Sin ruido medible, la señal es perfecta.
        assertEquals(1.0, score(aFull = 1.0, sigma = 0.0).cSnr, 1e-9)
    }

    @Test
    fun theAmplitudeFactorFollowsTheVariationOfTheFullReps() {
        fun withAmplitudes(vararg a: Double) = a.mapIndexed { i, x -> full(x, 100 + 120 * i) }
        assertEquals(1.0, score(withAmplitudes(0.9, 1.0, 1.1)).cAmp, 1e-9) // CV 0,10
        assertEquals(0.5, score(withAmplitudes(0.75, 1.0, 1.25)).cAmp, 1e-9) // CV 0,25
        assertEquals(0.0, score(withAmplitudes(0.6, 1.0, 1.4)).cAmp, 1e-9) // CV 0,40
        assertEquals(1.0, score(withAmplitudes(1.0, 1.0, 1.0, 1.0)).cAmp, 1e-9)
    }

    @Test
    fun theRhythmFactorFollowsTheVariationOfTheIntervals() {
        fun withIntervals(vararg gaps: Int): List<RepCycle> {
            var p = 100
            return listOf(full(1.0, p)) + gaps.map { g -> p += g; full(1.0, p) }
        }
        assertEquals(1.0, score(withIntervals(70, 100, 130)).cRhy, 1e-9) // CV 0,30
        assertEquals(0.5, score(withIntervals(45, 100, 155)).cRhy, 1e-9) // CV 0,55
        assertEquals(0.0, score(withIntervals(20, 100, 180)).cRhy, 1e-9) // CV 0,80
        assertEquals(1.0, score(withIntervals(100, 100, 100, 100)).cRhy, 1e-9)
        // Los descansos entre mini-series no son ritmo: solo cuentan los intervalos de una misma mini-serie.
        val sets = listOf(full(1.0, 100, 0), full(1.0, 200, 0), full(1.0, 300, 0), full(1.0, 1500, 1), full(1.0, 1600, 1), full(1.0, 1700, 1))
        assertEquals(1.0, score(sets).cRhy, 1e-9)
    }

    @Test
    fun withFewerThanThreeFullRepsTheVariationFactorsAreNeutral() {
        val b = score(listOf(full(1.0, 100), full(1.0, 220)))
        assertEquals(0.5, b.cAmp, 1e-9)
        assertEquals(0.5, b.cRhy, 1e-9)
    }

    @Test
    fun theViewFactorIsOneInTheCountViewsPointSevenOblique() {
        assertEquals(1.0, score(kind = ViewKind.SIDE).cView, 1e-9)
        assertEquals(1.0, score(kind = ViewKind.FRONT).cView, 1e-9) // la sentadilla se cuenta también de frente
        assertEquals(1.0, score(kind = ViewKind.OBLIQUE).cView, 1e-9) // y oblicua
        val machine = RepTestSupport.profile(AnalysisProfileId.MACHINE_SQUAT) // solo de lado
        val series = handSeries.series()
        assertEquals(0.7, RepConfidence.score(series, seg(perfectCycles()), view(ViewKind.OBLIQUE), machine, emptySet()).cView, 1e-9)
        assertEquals(0.4, RepConfidence.score(series, seg(perfectCycles()), view(ViewKind.FRONT), machine, emptySet()).cView, 1e-9)
        assertEquals(0.4, RepConfidence.score(series, seg(perfectCycles()), view(ViewKind.UNKNOWN), machine, emptySet()).cView, 1e-9)
        assertEquals(0.4, RepConfidence.score(series, seg(perfectCycles()), view(ViewKind.BACK), machine, emptySet()).cView, 1e-9)
    }

    @Test
    fun theScoreIsTheFormulaOfTheSpec() {
        val cycles = listOf(full(0.9, 100), full(1.0, 220), full(1.1, 360), full(1.0, 480), full(1.0, 600))
        val b = score(cycles, validFraction = 0.9, aFull = 0.2, sigma = 0.01, ambiguous = 2)
        val expected = sqrt(b.cTrack) * b.cView * (0.45 * b.cSnr + 0.30 * b.cAmp + 0.25 * b.cRhy) * 0.8.pow(2)
        println("[confianza] fórmula: ${describe(b)}")
        assertEquals(expected, b.score, 1e-12)
        assertEquals(2, b.nAmbiguous)
        assertEquals(0.8, b.cTrack, 1e-9)
    }

    @Test
    fun theLevelsFollowTheScoreAndTheBlockers() {
        assertEquals(CountConfidence.ALTA, score().level)
        // Un ciclo dudoso o varias personas no ofrecen un número; los demás topes siguen en MEDIA.
        assertEquals(CountConfidence.BAJA, score(ambiguous = 1).level)
        assertEquals(CountConfidence.MEDIA, score(flags = setOf(AnalysisFlag.LEGACY_TRACK)).level)
        assertEquals(CountConfidence.BAJA, score(flags = setOf(AnalysisFlag.MULTI_PERSON)).level)
        assertEquals(CountConfidence.MEDIA, score(flags = setOf(AnalysisFlag.POLARITY_FLIPPED)).level)
        assertEquals(CountConfidence.MEDIA, score(flipped = true).level)
        assertEquals(CountConfidence.MEDIA, score(mode = SideMode.NEAR_ONLY).level)
        assertEquals(CountConfidence.MEDIA, score(flags = setOf(AnalysisFlag.SIDE_UNKNOWN)).level)
        assertEquals(CountConfidence.MEDIA, score(flags = setOf(AnalysisFlag.FAR_SIDE_WORKING)).level)
        // Otros avisos no la bajan.
        assertEquals(CountConfidence.ALTA, score(flags = setOf(AnalysisFlag.VFR, AnalysisFlag.ROLL_CORRECTED)).level)
        // Con una puntuación entre 0,45 y 0,75: MEDIA; por debajo de 0,45: BAJA.
        assertEquals(CountConfidence.MEDIA, score(aFull = 0.08, sigma = 0.01).level) // c_snr ≈ 0,26
        assertEquals(CountConfidence.MEDIA, score(aFull = 0.052, sigma = 0.01).level) // c_snr ≈ 0: aun así MEDIA (0,55)
        assertEquals(CountConfidence.BAJA, score(kind = ViewKind.UNKNOWN).level) // c_view = 0,4 → 0,40 < 0,45
    }

    @Test
    fun lowConfidenceIsForcedWithoutFullRepsWithFewValidSamplesOrAtLowFrameRate() {
        val none = score(cycles = emptyList())
        assertEquals(CountConfidence.BAJA, none.level)
        assertEquals("no_full_reps", none.forcedLowReason)
        val sparse = score(validFraction = 0.55)
        assertEquals(CountConfidence.BAJA, sparse.level)
        assertEquals("low_valid_fraction", sparse.forcedLowReason)
        val slow = score(fs = 15.0)
        assertEquals(CountConfidence.BAJA, slow.level)
        assertEquals("low_fps", slow.forcedLowReason)
        // Justo en el límite (0,6 de fracción válida, 20 Hz) ya no se fuerza.
        assertNull(score(validFraction = 0.6, fs = 20.0).forcedLowReason)
    }
}
