package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.pose.LegacyPoseTrackAdapter
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La matriz sintética del conteo (§10.2): un caso por fila, con el generador. Cada prueba imprime una línea por caso
 * («[matriz] caso | esperado | obtenido») y falla al final con TODOS los casos que no cuadran.
 *
 * Los parciales y el fallo de ARRANQUE E se prueban con flexiones y peso muerto, cuya señal (la Y del hombro) es lineal en
 * el progreso del generador. En la sentadilla la señal de conteo es la altura de la cadera, que baja de forma no lineal
 * respecto del ángulo de rodilla: el parcial del generador (45 % del ángulo) baja el 24 % de la cadera y una «rebotada» a
 * esa altura es, para esta señal, una repetición casi completa.
 */
class RepSegmenterMatrixTest {
    private fun gen(
        movement: SynthMovement,
        script: List<SynthSegment>,
        camera: SynthCamera = SynthCamera(),
        noise: SynthNoise = SynthNoise(),
        fps: Double = 30.0,
        seed: Long = 42L,
        alternating: Boolean = false,
    ): SynthResult = SyntheticLifter.generate(
        SynthConfig(movement, camera = camera, fps = fps, script = script, noise = noise, seed = seed, alternating = alternating),
    )

    private fun rep(
        e: Double = 1.5,
        p1: Double = 0.5,
        c: Double = 1.5,
        p2: Double = 0.5,
        amplitude: Double = 1.0,
        kind: SynthRepKind = SynthRepKind.FULL,
    ) = SynthSegment.Rep(e, p1, c, p2, amplitude, null, kind)

    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.1f", x)

    private fun row(name: String, expected: Any, obtained: Any, ok: Boolean) {
        println("[matriz] $name | esperado $expected | obtenido $obtained | ${if (ok) "OK" else "FALLA"}")
    }

    /** Comprueba el conteo de un caso, lo imprime y anota el fallo si no coincide. */
    private fun expectCounts(
        fails: RepTestSupport.Failures,
        name: String,
        expected: Triple<Int, Int, Int>,
        run: RepTestSupport.Run?,
        extra: String = "",
    ) {
        val got = run?.seg?.let { RepTestSupport.counts(it) }
        val snr = run?.seg?.let { if (it.noiseSigma > 0.0) it.aFull / it.noiseSigma else Double.POSITIVE_INFINITY } ?: Double.NaN
        row(name, expected, "$got SNR=${fmt(snr)} $extra", got == expected)
        fails.check(got == expected) {
            "$name: esperado $expected y salió $got (SNR ${fmt(snr)}) ${run?.let { RepTestSupport.summary(it.seg) + "\n   " + it.trace }}"
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun everyMovementCountsExactlyWithTypicalNoise() {
        val fails = RepTestSupport.Failures()
        for ((movement, profileId) in RepTestSupport.PAIRS) {
            val r = gen(movement, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0))
            expectCounts(fails, "limpio $movement", RepTestSupport.truthCounts(r), RepTestSupport.run(r, RepTestSupport.profile(profileId)))
        }
        fails.assertNone()
    }

    @Test
    fun cleanTracksCountExactlyToo() {
        val fails = RepTestSupport.Failures()
        for ((movement, profileId) in RepTestSupport.PAIRS) {
            val r = gen(movement, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), noise = SynthNoise.NONE)
            expectCounts(fails, "sin ruido $movement", RepTestSupport.truthCounts(r), RepTestSupport.run(r, RepTestSupport.profile(profileId)))
        }
        fails.assertNone()
    }

    @Test
    fun walkoutUnrackAndRerackAreNotReps() {
        val fails = RepTestSupport.Failures()
        val script = listOf<SynthSegment>(SynthSegment.Idle(1.0), SynthSegment.Walk(2.0, 1.0), SynthSegment.Unrack(1.5), SynthSegment.Idle(1.0)) +
            List(5) { rep() } +
            listOf(SynthSegment.Idle(1.0), SynthSegment.Rerack(1.5), SynthSegment.Walk(2.0, -1.0), SynthSegment.Idle(1.0))
        val r = gen(SynthMovement.SQUAT, script)
        val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))
        expectCounts(fails, "walkout, unrack y re-rack (sentadilla)", RepTestSupport.truthCounts(r), run)
        if (run != null) {
            // Mientras camina, la máscara de caminar está encendida en la mayor parte del tramo.
            for (span in r.spans.filter { it.segment is SynthSegment.Walk }) {
                val from = ((span.startUs - run.series.t0Us) / 1e6 * run.series.fs).roundToInt()
                val to = ((span.endUs - run.series.t0Us) / 1e6 * run.series.fs).roundToInt()
                val on = (from until to).count { run.series.walk[it] }
                val fraction = on.toDouble() / (to - from)
                row("máscara de caminar ${span.segment}", "≥ 0,6", fmt(fraction), fraction >= 0.6)
                fails.check(fraction >= 0.6) { "máscara de caminar: solo el ${fmt(100 * fraction)} % del tramo $span" }
            }
            // Y no hay ninguna repetición contada fuera de las cinco de verdad.
            val inReps = run.seg.cycles.count { c -> r.reps.any { c.peak / run.series.fs * 1e6 in it.departUs.toDouble()..it.arriveUs.toDouble() } }
            fails.check(inReps == run.seg.cycles.size) { "ciclos fuera de las repeticiones de la verdad: ${run.seg.cycles.size - inReps}" }
        }
        // Con el disco (la muñeca que sujeta la barra), sacar y recolocar la barra tampoco cuenta.
        val bench = listOf<SynthSegment>(SynthSegment.Idle(1.0), SynthSegment.Unrack(1.5), SynthSegment.Idle(1.0)) +
            List(5) { rep() } + listOf(SynthSegment.Idle(1.0), SynthSegment.Rerack(1.5), SynthSegment.Idle(1.0))
        val rb = gen(SynthMovement.BENCH, bench)
        val bar = RepTestSupport.plateFromWrist(rb)
        expectCounts(fails, "unrack y re-rack con disco (banca)", RepTestSupport.truthCounts(rb), RepTestSupport.run(rb, RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true), bar))
        fails.assertNone()
    }

    @Test
    fun pausedSquatsAreNotFlippedAndCountExactly() {
        val fails = RepTestSupport.Failures()
        for ((label, pause, reps) in listOf(Triple("2 s", 2.0, 4), Triple("1,5 s", 1.5, 5))) {
            val r = gen(SynthMovement.SQUAT, repScript(reps, 1.5, pause, 1.5, 0.5, lead = 2.0, tail = 2.0))
            val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))
            expectCounts(fails, "sentadilla con pausa de $label", RepTestSupport.truthCounts(r), run)
            fails.check(run?.seg?.polarityFlipped == false) { "sentadilla con pausa de $label: se dio la vuelta a la señal" }
        }
        val push = gen(SynthMovement.PUSH_UP, repScript(5, 1.5, 2.0, 1.5, 0.5, lead = 2.0, tail = 2.0))
        val runPush = RepTestSupport.run(push, RepTestSupport.profile(AnalysisProfileId.PUSH_UP))
        expectCounts(fails, "flexiones con pausa de 2 s", RepTestSupport.truthCounts(push), runPush)
        fails.check(runPush?.seg?.polarityFlipped == false) { "flexiones con pausa: se dio la vuelta a la señal" }
        fails.assertNone()
    }

    @Test
    fun restPauseGivesThreeMiniSets() {
        val fails = RepTestSupport.Failures()
        val script = listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(3) { rep() } + SynthSegment.Rest(12.0) +
            List(2) { rep() } + SynthSegment.Rest(12.0) + List(3) { rep() } + SynthSegment.Idle(2.0)
        val r = gen(SynthMovement.SQUAT, script)
        for (hints in listOf(emptySet(), setOf("REST_PAUSE"))) {
            val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.SQUAT), hints = hints)
            expectCounts(fails, "rest-pause $hints", Triple(8, 0, 0), run, "minis=${run?.seg?.miniSets}")
            fails.check(run?.seg?.miniSets == 3) { "rest-pause $hints: mini-series ${run?.seg?.miniSets} en vez de 3" }
            fails.check(run?.seg?.cycles?.map { it.miniSet } == listOf(0, 0, 0, 1, 1, 2, 2, 2)) {
                "rest-pause $hints: mini-series por repetición ${run?.seg?.cycles?.map { it.miniSet }}"
            }
        }
        fails.assertNone()
    }

    @Test
    fun lengthenedAndShortPartialsAreClassified() {
        val fails = RepTestSupport.Failures()
        val push = RepTestSupport.profile(AnalysisProfileId.PUSH_UP)
        // Parciales «en estiramiento»: rebotes entre el 45 % y el 100 % tras cinco completas.
        val lengthened = listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(5) { rep() } +
            List(4) { rep(1.0, 0.0, 1.0, 0.0, kind = SynthRepKind.PARTIAL_REST) } + SynthSegment.Idle(2.0)
        val rl = gen(SynthMovement.PUSH_UP, lengthened)
        for (hints in listOf(emptySet(), setOf("PARTIALS"))) {
            val run = RepTestSupport.run(rl, push, hints = hints)
            expectCounts(fails, "5 completas + 4 parciales en estiramiento $hints", RepTestSupport.truthCounts(rl), run)
            val kinds = run?.seg?.cycles?.filter { it.kind == RepKind.PARTIAL }?.map { it.partialKind }
            fails.check(kinds == List(4) { PartialKind.FAR_END }) { "parciales en estiramiento: clases $kinds" }
        }
        // Parciales cortos: 0→55 % y vuelta al reposo, tras cinco completas.
        val short = listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(5) { rep() } +
            List(3) { rep(1.0, 0.0, 1.0, 0.0, kind = SynthRepKind.PARTIAL_FAR) } + SynthSegment.Idle(2.0)
        val rs = gen(SynthMovement.PUSH_UP, short)
        val runShort = RepTestSupport.run(rs, push)
        expectCounts(fails, "5 completas + 3 parciales cortos", RepTestSupport.truthCounts(rs), runShort)
        val shortKinds = runShort?.seg?.cycles?.filter { it.kind == RepKind.PARTIAL }?.map { it.partialKind }
        fails.check(shortKinds == List(3) { PartialKind.REST_END }) { "parciales cortos: clases $shortKinds" }
        fails.assertNone()
    }

    @Test
    fun setupAndRerackPartialsAreDiscardedUnlessAnnounced() {
        val fails = RepTestSupport.Failures()
        val push = RepTestSupport.profile(AnalysisProfileId.PUSH_UP)
        val partial = rep(1.0, 0.0, 1.0, 0.0, kind = SynthRepKind.PARTIAL_FAR)
        val script = listOf<SynthSegment>(SynthSegment.Idle(1.0), partial, SynthSegment.Rest(8.0)) + List(5) { rep() } +
            listOf(SynthSegment.Rest(10.0), partial, SynthSegment.Idle(2.0))
        val r = gen(SynthMovement.PUSH_UP, script)
        val run = RepTestSupport.run(r, push)
        expectCounts(fails, "preparación y re-rack descartados", Triple(5, 0, 0), run, run?.seg?.discarded?.joinToString { it.reason } ?: "")
        val reasons = run?.seg?.discarded?.map { it.reason } ?: emptyList()
        fails.check("setup" in reasons && "rerack" in reasons) { "motivos de descarte $reasons" }

        // Un parcial pegado a la primera completa solo se conserva si se anuncian PARTIALS.
        val before = listOf<SynthSegment>(SynthSegment.Idle(2.0), partial) + List(5) { rep() } + SynthSegment.Idle(2.0)
        val rb = gen(SynthMovement.PUSH_UP, before)
        val withoutHint = RepTestSupport.run(rb, push)
        val withHint = RepTestSupport.run(rb, push, hints = setOf("PARTIALS"))
        expectCounts(fails, "parcial antes de la primera completa, sin pista", Triple(5, 0, 0), withoutHint)
        expectCounts(fails, "parcial antes de la primera completa, con PARTIALS", Triple(5, 1, 0), withHint)

        // Tras un descanso largo, el parcial es el re-rack salvo en rest-pause (el descanso es parte de la serie).
        val after = listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(5) { rep() } + listOf(SynthSegment.Rest(12.0), partial, SynthSegment.Idle(2.0))
        val ra = gen(SynthMovement.PUSH_UP, after)
        expectCounts(fails, "parcial tras 12 s, sin pista", Triple(5, 0, 0), RepTestSupport.run(ra, push))
        expectCounts(fails, "parcial tras 12 s, con REST_PAUSE", Triple(5, 1, 0), RepTestSupport.run(ra, push, hints = setOf("REST_PAUSE")))
        fails.assertNone()
    }

    @Test
    fun theLastRepFailedIsFailedInBothStartPhases() {
        val fails = RepTestSupport.Failures()
        // El intento que fracasa es más lento que una completa (2× en la concéntrica).
        val script = listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(4) { rep() } +
            rep(1.5, 0.5, 3.0, 0.5, kind = SynthRepKind.FAILED) + SynthSegment.Idle(2.0)
        for ((movement, profileId) in listOf(
            SynthMovement.DEADLIFT to AnalysisProfileId.DEADLIFT,
            SynthMovement.PUSH_UP to AnalysisProfileId.PUSH_UP,
        )) {
            val r = gen(movement, script)
            expectCounts(fails, "última fallida $movement", RepTestSupport.truthCounts(r), RepTestSupport.run(r, RepTestSupport.profile(profileId)))
        }
        fails.assertNone()
    }

    @Test
    fun continuousAndDeadStopDeadliftsCountTheSameWithThePlate() {
        val fails = RepTestSupport.Failures()
        for ((label, p1, p2) in listOf(Triple("continuo", 0.0, 0.0), Triple("parado", 1.0, 0.5))) {
            val r = gen(SynthMovement.DEADLIFT, repScript(5, 1.5, p1, 1.5, p2, lead = 2.0, tail = 2.0))
            val bar = RepTestSupport.plateFromWrist(r)
            val withPlate = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.DEADLIFT, plate = true), bar)
            expectCounts(fails, "peso muerto $label con disco", RepTestSupport.truthCounts(r), withPlate, "fuente=${withPlate?.series?.source}")
            fails.check(withPlate?.series?.source == CountSeriesBuilder.SOURCE_PLATE) { "peso muerto $label: la fuente es ${withPlate?.series?.source}" }
            expectCounts(fails, "peso muerto $label con pose", RepTestSupport.truthCounts(r), RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.DEADLIFT)))
        }
        fails.assertNone()
    }

    @Test
    fun romanianDeadliftPickupAndSetDownAreNotReps() {
        val fails = RepTestSupport.Failures()
        val script = listOf<SynthSegment>(SynthSegment.Idle(2.0), rep(2.0, 0.5, 2.0, 0.5, amplitude = 1.6)) + List(5) { rep() } +
            listOf(rep(2.0, 0.5, 2.0, 0.5, amplitude = 1.6), SynthSegment.Idle(2.0))
        val r = gen(SynthMovement.RDL, script)
        val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.HINGE))
        expectCounts(fails, "rumano con recogida y dejada", Triple(5, 0, 0), run, run?.seg?.discarded?.joinToString { it.reason } ?: "")
        val reasons = run?.seg?.discarded?.map { it.reason } ?: emptyList()
        fails.check("setup" in reasons && "setdown" in reasons) { "motivos de descarte $reasons" }
        fails.assertNone()
    }

    @Test
    fun benchWithTheWristCoveredCountsWithThePlateOnly() {
        val fails = RepTestSupport.Failures()
        val covered = SynthNoise().copy(
            occlusionWindowsS = listOf(0.0 to 60.0),
            occludedLandmarks = setOf(PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_INDEX, PoseLandmark.LEFT_PINKY),
        )
        val script = listOf<SynthSegment>(SynthSegment.Idle(1.0), SynthSegment.Unrack(1.5), SynthSegment.Idle(1.0)) + List(5) { rep() } +
            listOf(SynthSegment.Idle(1.0), SynthSegment.Rerack(1.5), SynthSegment.Idle(1.0))
        val r = gen(SynthMovement.BENCH, script, noise = covered)
        val withPlate = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true), RepTestSupport.plateFromWrist(r))
        expectCounts(fails, "banca con la muñeca tapada y disco", RepTestSupport.truthCounts(r), withPlate, "fuente=${withPlate?.series?.source}")
        // Sin disco no hay mano con la que contar: sin señal, y no una cuenta inventada.
        val without = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.BENCH))
        row("banca con la muñeca tapada sin disco", "sin señal", if (without == null) "sin señal" else RepTestSupport.summary(without.seg), without == null)
        fails.check(without == null) { "banca con la muñeca tapada y sin disco debería dar NoSignal" }
        fails.assertNone()
    }

    @Test
    fun alternatingCurlFromTheFrontIsCountedPerSide() {
        val fails = RepTestSupport.Failures()
        val front = SynthCamera(yawDeg = 0.0)
        val alt = gen(SynthMovement.CURL, repScript(8, 1.5, 0.0, 1.5, 0.0, lead = 2.0, tail = 2.0), camera = front, alternating = true)
        val run = RepTestSupport.run(alt, RepTestSupport.profile(AnalysisProfileId.CURL))
        expectCounts(fails, "curl alterno de frente", RepTestSupport.truthCounts(alt), run, "modo=${run?.seg?.sideMode} lados=${run?.seg?.perSideCounts}")
        fails.check(run?.seg?.sideMode == SideMode.ALTERNATING) { "curl alterno: modo ${run?.seg?.sideMode}" }
        fails.check(run?.seg?.perSideCounts == mapOf(Side.LEFT to 4, Side.RIGHT to 4)) { "curl alterno: reps por lado ${run?.seg?.perSideCounts}" }
        val bySide = run?.seg?.cycles?.map { it.side }
        fails.check(bySide == listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT)) {
            "curl alterno: lado de cada repetición $bySide"
        }

        // Los dos brazos a la vez: se cuenta un lado y el otro coincide.
        val both = gen(SynthMovement.CURL, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), camera = front)
        val runBoth = RepTestSupport.run(both, RepTestSupport.profile(AnalysisProfileId.CURL))
        expectCounts(fails, "curl simultáneo de frente", RepTestSupport.truthCounts(both), runBoth, "modo=${runBoth?.seg?.sideMode} lados=${runBoth?.seg?.perSideCounts}")
        fails.check(runBoth?.seg?.sideMode == SideMode.BOTH_SYNC) { "curl simultáneo: modo ${runBoth?.seg?.sideMode}" }

        // Un solo brazo: se cuenta el que trabaja.
        val single = gen(
            SynthMovement.CURL,
            listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(5) { SynthSegment.Rep(1.5, 0.5, 1.5, 0.5, 1.0, Side.LEFT, SynthRepKind.FULL) } + SynthSegment.Idle(2.0),
            camera = front,
        )
        val runSingle = RepTestSupport.run(single, RepTestSupport.profile(AnalysisProfileId.CURL))
        expectCounts(fails, "curl de un brazo de frente", Triple(5, 0, 0), runSingle, "modo=${runSingle?.seg?.sideMode} lados=${runSingle?.seg?.perSideCounts}")
        fails.check(runSingle?.seg?.sideMode == SideMode.SINGLE) { "curl de un brazo: modo ${runSingle?.seg?.sideMode}" }
        fails.assertNone()
    }

    @Test
    fun lateralRaisesFromTheSideAndFromTheFrontGiveTheSameCount() {
        val fails = RepTestSupport.Failures()
        val amplitudes = HashMap<Double, Double>()
        for (yaw in listOf(90.0, 0.0)) {
            val camera = SynthTestSupport.LANDSCAPE_FAR.copy(yawDeg = yaw, heightM = 1.4)
            val r = gen(SynthMovement.LATERAL_RAISE, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), camera = camera)
            val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.RAISE))
            expectCounts(fails, "laterales yaw=$yaw", RepTestSupport.truthCounts(r), run, "modo=${run?.seg?.sideMode} β=${fmt(run?.seg?.aFull ?: Double.NaN)}")
            amplitudes[yaw] = run?.seg?.aFull ?: Double.NaN
        }
        // β solo depende de la vertical: la amplitud sale igual de lado y de frente.
        val diff = abs((amplitudes[90.0] ?: Double.NaN) - (amplitudes[0.0] ?: Double.NaN))
        row("laterales: β de lado frente a de frente", "≤ 8°", fmt(diff), diff <= 8.0)
        fails.check(diff <= 8.0) { "laterales: β de lado ${amplitudes[90.0]} y de frente ${amplitudes[0.0]}" }
        fails.assertNone()
    }

    @Test
    fun theViewTurningFromFrontToSideKeepsTheCount() {
        val fails = RepTestSupport.Failures()
        for (yaw in listOf(0.0, 25.0, 45.0, 65.0, 90.0)) {
            val r = gen(SynthMovement.SQUAT, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), camera = SynthCamera(yawDeg = yaw))
            val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))
            expectCounts(fails, "sentadilla ψ=$yaw", RepTestSupport.truthCounts(r), run, "vista=${run?.prepared?.view?.kind}")
        }
        fails.assertNone()
    }

    @Test
    fun variableFrameRateAndLostFramesKeepTheCount() {
        val fails = RepTestSupport.Failures()
        for ((label, fps, noise) in listOf(
            Triple("60 fps, PTS ±3 ms y 4 % de pérdidas", 60.0, SynthNoise().copy(ptsJitterMs = 3.0, dropRate = 0.04)),
            Triple("24 fps y 6 % de pérdidas", 24.0, SynthNoise().copy(dropRate = 0.06)),
            Triple("30 fps y 10 % de pérdidas", 30.0, SynthNoise().copy(dropRate = 0.10)),
        )) {
            val r = gen(SynthMovement.SQUAT, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), fps = fps, noise = noise)
            val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.SQUAT))
            expectCounts(fails, "VFR/pérdidas: $label", RepTestSupport.truthCounts(r), run, "VFR=${run?.prepared?.flags?.contains(AnalysisFlag.VFR)} válida=${fmt(100 * (run?.series?.validFraction ?: 0.0))} %")
            if (fps == 60.0) {
                fails.check(run?.prepared?.flags?.contains(AnalysisFlag.VFR) == true) { "VFR: el flag no está" }
            }
        }
        fails.assertNone()
    }

    @Test
    fun leftRightSwapsInTheLegsDoNotChangeAKneeBasedCount() {
        val fails = RepTestSupport.Failures()
        val noisy = SynthNoise().copy(swapGlitches = 6)
        val r = gen(SynthMovement.LUNGE, repScript(6, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0), noise = noisy)
        fails.check(r.swapGlitchFrames.count { it } > 0) { "el generador no puso cruces" }
        // La señal de rodilla (flexión del perfil de máquina) es la que sufre los cruces de las piernas.
        val run = RepTestSupport.run(r, RepTestSupport.profile(AnalysisProfileId.MACHINE_SQUAT))
        expectCounts(fails, "zancada con señal de rodilla y ${r.swapGlitchFrames.count { it }} fotogramas cruzados", RepTestSupport.truthCounts(r), run)
        fails.assertNone()
    }

    @Test
    fun portraitAndLandscapeGiveTheSameAngleSignal() {
        val fails = RepTestSupport.Failures()
        val script = repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0)
        val portrait = gen(SynthMovement.SQUAT, script, camera = SynthCamera(yawDeg = 90.0), noise = SynthNoise.NONE)
        val landscape = gen(SynthMovement.SQUAT, script, camera = SynthCamera(yawDeg = 90.0, widthPx = 1280, heightPx = 720), noise = SynthNoise.NONE)
        val profile = RepTestSupport.profile(AnalysisProfileId.MACHINE_SQUAT)
        val a = RepTestSupport.run(portrait, profile)
        val b = RepTestSupport.run(landscape, profile)
        expectCounts(fails, "ángulo de rodilla en vertical", Triple(5, 0, 0), a)
        expectCounts(fails, "ángulo de rodilla en horizontal", Triple(5, 0, 0), b)
        if (a != null && b != null && a.series.r.size == b.series.r.size) {
            var worst = 0.0
            for (k in a.series.r.indices) worst = maxOf(worst, abs(a.series.r[k] - b.series.r[k]))
            row("ángulo de rodilla vertical frente a horizontal", "≤ 2°", fmt(worst), worst <= 2.0)
            fails.check(worst <= 2.0) { "la señal de rodilla difiere hasta $worst° entre vertical y horizontal" }
        } else {
            fails.add("no se pudieron comparar las dos señales")
        }
        fails.assertNone()
    }

    @Test
    fun aLegacyV1TrackCountsButIsFlagged() {
        val fails = RepTestSupport.Failures()
        val r = gen(SynthMovement.SQUAT, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0))
        val v2 = LegacyPoseTrackAdapter.toV2(RepTestSupport.legacyV1(r))
        val prepared = TrackPreparation.prepare(v2)
        val run = RepTestSupport.run(prepared, RepTestSupport.profile(AnalysisProfileId.SQUAT))
        expectCounts(fails, "traza v1 (10 Hz)", Triple(5, 0, 0), run, "flags=${prepared.flags}")
        fails.check(AnalysisFlag.LEGACY_TRACK in prepared.flags) { "falta LEGACY_TRACK" }
        fails.check(AnalysisFlag.LOW_FPS in prepared.flags) { "falta LOW_FPS" }
        fails.assertNone()
    }

    @Test
    fun theTruthKindsOfTheMatrixAreCoherent() {
        // Salvaguarda de la propia matriz: las clases que el generador llama «parcial» y «fallida» se cuentan como se espera.
        val script = listOf<SynthSegment>(SynthSegment.Idle(1.0), rep(), rep(kind = SynthRepKind.PARTIAL_FAR), rep(kind = SynthRepKind.FAILED))
        assertEquals(Triple(1, 1, 1), RepTestSupport.truthCounts(gen(SynthMovement.PUSH_UP, script)))
    }
}
