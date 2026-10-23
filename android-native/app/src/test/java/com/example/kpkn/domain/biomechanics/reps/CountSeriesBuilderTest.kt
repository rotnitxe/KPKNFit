package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.signal.Timebase
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport

/** La señal de conteo por perfil: orientación, unidades, disco, huecos, caminar, punto de barra y lado de trabajo. */
class CountSeriesBuilderTest {
    private fun gen(
        movement: SynthMovement,
        script: List<SynthSegment> = repScript(3, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0),
        camera: SynthCamera = SynthCamera(),
        noise: SynthNoise = SynthNoise.NONE,
        fps: Double = 30.0,
    ): SynthResult = SyntheticLifter.generate(SynthConfig(movement, camera = camera, fps = fps, script = script, noise = noise))

    private fun build(
        track: PoseTrackV2,
        profile: AnalysisProfile,
        bar: com.example.kpkn.domain.biomechanics.bar.BarTrack? = null,
    ): CountSeriesResult = CountSeriesBuilder.build(TrackPreparation.prepare(track), bar, profile)

    private fun ok(result: CountSeriesResult): CountSeriesResult.Ok = result as? CountSeriesResult.Ok ?: throw AssertionError("sin señal: $result")

    private fun mean(x: DoubleArray, from: Int, to: Int): Double {
        var s = 0.0
        var n = 0
        for (k in from until to) if (!x[k].isNaN()) { s += x[k]; n++ }
        return s / n
    }

    @Test
    fun theRestIsTheMinimumAndTheTurnTheMaximumInEveryProfile() {
        val fails = RepTestSupport.Failures()
        for ((movement, id) in RepTestSupport.PAIRS) {
            val r = gen(movement)
            val profile = RepTestSupport.profile(id)
            val series = ok(build(r.track, profile)).series
            val rest = mean(series.r, 3, 40)
            val first = r.reps[0]
            val turn = ((first.turnStartUs + first.turnEndUs) / 2e6 * series.fs).roundToInt()
            val atTurn = mean(series.r, turn - 3, turn + 4)
            println("[serie] $movement/$id: reposo %.3f giro %.3f (amplitud mínima %.3f, unidad %s)".format(rest, atTurn, profile.minAmplitude, series.unit))
            fails.check(atTurn - rest >= 0.5 * profile.minAmplitude) { "$movement: reposo $rest y giro $atTurn: el reposo no es el mínimo" }
            fails.check(series.source == CountSeriesBuilder.SOURCE_POSE) { "$movement: fuente ${series.source}" }
            fails.check(series.size == TrackPreparation.prepare(r.track).timebase.n) { "$movement: tamaño ${series.size}" }
        }
        fails.assertNone()
    }

    @Test
    fun eachSignalHasItsUnit() {
        fun unit(movement: SynthMovement, id: AnalysisProfileId) = ok(build(gen(movement).track, RepTestSupport.profile(id))).series.unit
        assertEquals(SignalUnit.BODY_HEIGHTS, unit(SynthMovement.SQUAT, AnalysisProfileId.SQUAT))
        assertEquals(SignalUnit.DEGREES, unit(SynthMovement.SQUAT, AnalysisProfileId.MACHINE_SQUAT))
        assertEquals(SignalUnit.DEGREES, unit(SynthMovement.LATERAL_RAISE, AnalysisProfileId.RAISE))
        assertEquals(SignalUnit.RATIO, unit(SynthMovement.PULL_UP, AnalysisProfileId.PULL_VERTICAL))
    }

    @Test
    fun theSameSignalComesOutInPortraitAndLandscape() {
        val fails = RepTestSupport.Failures()
        val portrait = gen(SynthMovement.SQUAT, camera = SynthCamera(yawDeg = 90.0))
        val landscape = gen(SynthMovement.SQUAT, camera = SynthCamera(yawDeg = 90.0, widthPx = 1280, heightPx = 720))
        // Ángulos: iguales al grado.
        val machine = RepTestSupport.profile(AnalysisProfileId.MACHINE_SQUAT)
        val a = ok(build(portrait.track, machine)).series
        val b = ok(build(landscape.track, machine)).series
        var worst = 0.0
        for (k in a.r.indices) worst = maxOf(worst, abs(a.r[k] - b.r[k]))
        println("[serie] ángulo de rodilla, vertical frente a horizontal: peor diferencia %.3f°".format(worst))
        fails.check(worst <= 1.0) { "el ángulo de rodilla difiere hasta $worst° entre vertical y horizontal" }
        // Desplazamientos: en alturas corporales, la amplitud coincide con unos pocos %.
        val squat = RepTestSupport.profile(AnalysisProfileId.SQUAT)
        val sa = ok(build(portrait.track, squat)).series
        val sb = ok(build(landscape.track, squat)).series
        fun range(x: DoubleArray) = x.filter { !it.isNaN() }.let { it.max() - it.min() }
        val ratio = range(sa.r) / range(sb.r)
        println("[serie] bajada de la cadera en BH: vertical %.3f, horizontal %.3f".format(range(sa.r), range(sb.r)))
        fails.check(abs(ratio - 1.0) <= 0.08) { "la bajada de la cadera difiere (razón $ratio) entre vertical y horizontal" }
        fails.assertNone()
    }

    // ---- el disco ----

    private val benchScript = repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0)

    @Test
    fun theDiscReplacesThePoseOnlyWithEnoughCoverage() {
        val fails = RepTestSupport.Failures()
        val r = gen(SynthMovement.BENCH, benchScript)
        val plateProfile = RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true)
        val full = ok(build(r.track, plateProfile, RepTestSupport.plateFromWrist(r))).series
        fails.check(full.source == CountSeriesBuilder.SOURCE_PLATE) { "cobertura total: fuente ${full.source}" }
        fails.check(full.unit == SignalUnit.BODY_HEIGHTS) { "disco: unidad ${full.unit}" }
        // Con el 10 % de las muestras de respaldo (POSE_PRIOR) la cobertura es del 90 % y manda el disco, con esas muestras débiles.
        val ninety = ok(build(r.track, plateProfile, RepTestSupport.plateFromWrist(r) { i -> if (i % 10 < 9) BarSampleSource.HOUGH else BarSampleSource.POSE_PRIOR })).series
        fails.check(ninety.source == CountSeriesBuilder.SOURCE_PLATE) { "cobertura del 90 %: fuente ${ninety.source}" }
        fails.check(ninety.weak.count { it } > 0) { "las muestras de respaldo deberían salir débiles" }
        // Con el 70 % no llega al 85 %: cuenta la pose.
        val seventy = ok(build(r.track, plateProfile, RepTestSupport.plateFromWrist(r) { i -> if (i % 10 < 7) BarSampleSource.NCC else BarSampleSource.PREDICTED })).series
        fails.check(seventy.source == CountSeriesBuilder.SOURCE_POSE) { "cobertura del 70 %: fuente ${seventy.source}" }
        // Sin traza de disco, o con un perfil que no lo usa, también la pose.
        fails.check(ok(build(r.track, plateProfile, null)).series.source == CountSeriesBuilder.SOURCE_POSE) { "sin disco" }
        val poseProfile = RepTestSupport.profile(AnalysisProfileId.BENCH, plate = false)
        fails.check(ok(build(r.track, poseProfile, RepTestSupport.plateFromWrist(r))).series.source == CountSeriesBuilder.SOURCE_POSE) {
            "perfil sin disco"
        }
        // El disco sigue la misma orientación: el reposo (brazos extendidos) es el mínimo y la barra en el pecho el máximo.
        val first = r.reps[0]
        val turn = ((first.turnStartUs + first.turnEndUs) / 2e6 * full.fs).roundToInt()
        fails.check(mean(full.r, turn - 3, turn + 4) - mean(full.r, 3, 40) >= 0.1) { "el disco no sube con la barra en el pecho" }
        fails.assertNone()
    }

    @Test
    fun lungePlateFallbackKeepsThePoseShoulderProxyWithoutHorizontalVeto() {
        val r = gen(SynthMovement.LUNGE, repScript(5, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0))
        val profile = RepTestSupport.profile(AnalysisProfileId.LUNGE, plate = true)
        val target = r.reps[1]
        // Todo el cuerpo se traslada lateralmente durante la segunda zancada: la flexión vertical no cambia,
        // pero hombro (proxy POSE) y muñeca (disco confirmado) recorren la misma distancia horizontal real.
        val shiftedTrack = r.track.mapFrames { i, image, _ ->
            val t = r.track.frames[i].tUs
            val fraction = ((t - target.departUs).toDouble() / (target.arriveUs - target.departUs).toDouble())
                .coerceIn(0.0, 1.0)
            val dx = (0.30 * fraction).toFloat()
            for ((lm, point) in image.toMap()) image[lm] = point.copy(x = point.x + dx)
        }
        val shifted = SynthResult(shiftedTrack, r.reps, r.primaryAngleDeg)
        val confirmed = RepTestSupport.plateFromWrist(shifted)
        val confirmedSeries = ok(build(shiftedTrack, profile, confirmed)).series
        assertEquals(CountSeriesBuilder.SOURCE_PLATE, confirmedSeries.source)
        assertTrue("LUNGE no usa la máscara de caminar", confirmedSeries.walk.none { it })
        val confirmedSeg = RepSegmenter.segment(confirmedSeries, profile, emptySet())
        assertEquals("el disco lateral veta solo la segunda zancada", 4, confirmedSeg.fullCount)
        assertTrue("el disco lateral debe activar el veto", confirmedSeg.discarded.any { it.reason == "nonrep_horizontal" })
        val seed = checkNotNull(confirmed.seed)
        val withoutPlate = ok(build(shiftedTrack, profile)).series
        assertEquals(CountSeriesBuilder.SOURCE_POSE, withoutPlate.source)
        assertNotNull(withoutPlate.barX)
        assertNotNull(withoutPlate.barY)
        assertTrue("LUNGE no usa la máscara de caminar", withoutPlate.walk.none { it })
        val proxyX = checkNotNull(withoutPlate.barX)
        val proxyY = checkNotNull(withoutPlate.barY)
        val start = ((target.departUs - withoutPlate.t0Us) / 1e6 * withoutPlate.fs).roundToInt().coerceIn(proxyX.indices)
        val end = ((target.arriveUs - withoutPlate.t0Us) / 1e6 * withoutPlate.fs).roundToInt().coerceIn(proxyX.indices)
        assertTrue("el proxy de hombro debe desplazarse durante la zancada", abs(proxyX[end] - proxyX[start]) > 0.20)
        fun span(values: DoubleArray): Double {
            val window = (start..end).map { values[it] }.filter { it.isFinite() }
            return window.maxOrNull()!! - window.minOrNull()!!
        }
        assertTrue("el proxy lateral cruzaría el antiguo veto horizontal",
            span(proxyX) > AnalysisTuning.DEFAULT.horizontalPathRatio * span(proxyY))
        assertEquals("la pose conserva las cinco zancadas", 5,
            RepSegmenter.segment(withoutPlate, profile, emptySet()).fullCount)

        val unconfirmed = confirmed.copy(seed = seed.copy(userConfirmed = false))
        val interrupted = confirmed.copy(trackingContinuity = PlateTrackingContinuity.INTERRUPTED,
            continuityBreakUs = 4_000_000L)
        val lowCoverage = confirmed.copy(samples = confirmed.samples.mapIndexed { i, sample ->
            if (i % 10 < 7) sample else sample.copy(source = BarSampleSource.PREDICTED)
        })
        for ((reason, plate) in listOf(
            "sin confirmar" to unconfirmed,
            "interrumpida" to interrupted,
            "cobertura baja" to lowCoverage,
        )) {
            val fallback = ok(build(shiftedTrack, profile, plate)).series
            assertEquals(reason, CountSeriesBuilder.SOURCE_POSE, fallback.source)
            assertArrayEquals(reason, checkNotNull(withoutPlate.barX), checkNotNull(fallback.barX), 0.0)
            assertArrayEquals(reason, checkNotNull(withoutPlate.barY), checkNotNull(fallback.barY), 0.0)
            val segmented = RepSegmenter.segment(fallback, profile, emptySet())
            assertEquals(reason, 5, segmented.fullCount)
            assertTrue(reason, segmented.discarded.none { it.reason == "nonrep_horizontal" })
        }
    }

    @Test
    fun thePoseToDiscLagIsCorrectedUpToThreeFrames() {
        val fails = RepTestSupport.Failures()
        val r = gen(SynthMovement.BENCH, benchScript)
        val profile = RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true)
        val reference = ok(build(r.track, profile, RepTestSupport.plateFromWrist(r))).series
        for (lag in listOf(2, 3, -2)) {
            val shifted = ok(build(r.track, profile, RepTestSupport.plateFromWrist(r, lagFrames = lag))).series
            var worst = 0.0
            for (k in 6 until reference.r.size - 6) worst = maxOf(worst, abs(reference.r[k] - shifted.r[k]))
            println("[serie] desfase del disco de $lag fotogramas: peor diferencia con el alineado %.4f BH".format(worst))
            fails.check(worst <= 0.004) { "desfase $lag: la señal del disco difiere hasta $worst BH de la alineada" }
        }
        fails.assertNone()
    }

    // ---- sin señal ----

    @Test
    fun withoutScaleThereIsNoBodyHeightSignalButTheAnglesStillWork() {
        val r = gen(SynthMovement.SQUAT)
        val prepared = TrackPreparation.prepare(r.track)
        assertNotNull(prepared.scale)
        val unscaled = PreparedTrack(prepared.track, prepared.geo, prepared.timebase, null, prepared.near, prepared.facing, prepared.view, prepared.flags)
        val squat = CountSeriesBuilder.build(unscaled, null, RepTestSupport.profile(AnalysisProfileId.SQUAT))
        assertEquals(CountSeriesResult.NoSignal("no_scale"), squat)
        assertTrue(CountSeriesBuilder.build(unscaled, null, RepTestSupport.profile(AnalysisProfileId.MACHINE_SQUAT)) is CountSeriesResult.Ok)
        val series = (CountSeriesBuilder.build(unscaled, null, RepTestSupport.profile(AnalysisProfileId.MACHINE_SQUAT)) as CountSeriesResult.Ok).series
        // Sin escala no hay caminar ni punto de barra en BH.
        assertTrue(series.walk.none { it })
        assertNull(series.barX)
        assertNull(series.barY)
    }

    @Test
    fun withoutAnyPoseOrTooLittleSignalThereIsNoSignal() {
        val r = gen(SynthMovement.SQUAT)
        val empty = r.track.copy(frames = r.track.frames.map { PoseFrameV2.of(it.tUs, 0, null, null) })
        assertEquals(CountSeriesResult.NoSignal("no_pose"), build(empty, RepTestSupport.profile(AnalysisProfileId.SQUAT)))
        assertEquals(CountSeriesResult.NoSignal("no_timebase"), build(r.track.copy(frames = emptyList()), RepTestSupport.profile(AnalysisProfileId.SQUAT)))
        // Solo medio segundo con pose: no hay señal que contar.
        val sparse = r.track.copy(frames = r.track.frames.mapIndexed { i, f -> if (i < 15) f else PoseFrameV2.of(f.tUs, 0, null, null) })
        assertEquals(CountSeriesResult.NoSignal("no_valid_signal"), build(sparse, RepTestSupport.profile(AnalysisProfileId.SQUAT)))
    }

    // ---- huecos, atípicos y puntos débiles ----

    @Test
    fun shortGapsAreFilledAndMarkedAndLongOnesAreCuts() {
        val fails = RepTestSupport.Failures()
        val r = gen(SynthMovement.SQUAT, repScript(3, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0))
        // 12 fotogramas (0,4 s) y 30 fotogramas (1 s) sin pose.
        val holes = r.track.copy(
            frames = r.track.frames.mapIndexed { i, f -> if (i in 100..111 || i in 300..329) PoseFrameV2.of(f.tUs, 0, null, null) else f },
        )
        val series = ok(build(holes, RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
        val short = (101..110)
        fails.check(short.all { !series.r[it].isNaN() && series.filled[it] }) { "el hueco de 0,4 s debería estar relleno y marcado" }
        val long = (305..324)
        fails.check(long.all { series.r[it].isNaN() && !series.filled[it] }) { "el hueco de 1 s debería quedar como corte" }
        fails.check(series.validFraction < 1.0 && series.validFraction > 0.85) { "fracción válida ${series.validFraction}" }
        fails.assertNone()
    }

    @Test
    fun aLoneOutlierDoesNotReachTheSignal() {
        val r = gen(SynthMovement.SQUAT)
        val spiked = r.track.mapFrames { i, image, _ ->
            if (i == 200) {
                for (lm in listOf(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)) {
                    val p = image[lm] ?: continue
                    image[lm] = p.copy(y = p.y + 0.3f)
                }
            }
        }
        val series = ok(build(spiked, RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
        assertTrue("salto de ${abs(series.r[200] - series.r[199])} BH en el atípico", abs(series.r[200] - series.r[199]) < 0.05)
    }

    @Test
    fun weakPointsMarkTheSamplesThatUsedThem() {
        val r = gen(SynthMovement.BENCH, benchScript)
        val weakWrist = r.track.mapFrames { i, image, _ ->
            if (i in 150..300) {
                val w = image[PoseLandmark.LEFT_WRIST] ?: return@mapFrames
                image[PoseLandmark.LEFT_WRIST] = w.copy(visibility = 0.4f)
            }
        }
        val series = ok(build(weakWrist, RepTestSupport.profile(AnalysisProfileId.BENCH))).series
        assertTrue("débiles dentro: ${(155..295).count { series.weak[it] }}", (155..295).all { series.weak[it] })
        assertTrue("débiles fuera", (5..140).none { series.weak[it] } && (310..400).none { series.weak[it] })
        assertTrue("la fracción válida baja: ${series.validFraction}", series.validFraction < 0.8)
    }

    // ---- caminar y punto de barra ----

    @Test
    fun walkingIsMaskedOnlyWhileWalkingAndNeverInALunge() {
        val fails = RepTestSupport.Failures()
        val script = listOf<SynthSegment>(SynthSegment.Idle(1.0), SynthSegment.Walk(2.0, 1.0), SynthSegment.Idle(1.0)) +
            List(2) { SynthSegment.Rep(1.5, 0.5, 1.5, 0.5) } + SynthSegment.Idle(1.0)
        val r = gen(SynthMovement.SQUAT, script)
        val walk = r.spans.first { it.segment is SynthSegment.Walk }
        val squat = ok(build(r.track, RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
        val from = (walk.startUs / 1e6 * squat.fs).roundToInt()
        val to = (walk.endUs / 1e6 * squat.fs).roundToInt()
        val inside = (from until to).count { squat.walk[it] }.toDouble() / (to - from)
        val reps = r.reps.first().departUs / 1e6 * squat.fs
        val outside = (reps.roundToInt() until squat.size).count { squat.walk[it] }
        fails.check(inside >= 0.6) { "caminando: máscara en el ${100 * inside} % del tramo" }
        fails.check(outside <= 5) { "fuera de caminar hay $outside muestras de máscara" }
        // La zancada mueve los pies a propósito: sin máscara de caminar.
        val lunge = ok(build(r.track, RepTestSupport.profile(AnalysisProfileId.LUNGE))).series
        fails.check(lunge.walk.none { it }) { "la zancada no debería tener máscara de caminar" }
        // El pie de la extensión de cuádriceps se mueve, pero el cuerpo no: tampoco es caminar.
        val extension = gen(SynthMovement.LEG_EXTENSION)
        val ext = ok(build(extension.track, RepTestSupport.profile(AnalysisProfileId.LEG_EXTENSION))).series
        fails.check(ext.walk.none { it }) { "extensión de cuádriceps con ${ext.walk.count { it }} muestras de caminar" }
        fails.assertNone()
    }

    /** Constant world/image foot identity, with hip motion independent of visibility switches. */
    @Test
    fun stationaryFeetNeverBecomeWalkingWhenVisibilitySwitchesBetweenBothLeftAndRight() {
        val truth = gen(SynthMovement.SQUAT, camera = SynthTestSupport.LANDSCAPE_FAR)
        val hips = setOf(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        fun fixture(alternate: Boolean) = truth.track.mapFrames { i, image, _ ->
            val t = truth.track.frames[i].tUs / 1e6
            for (lm in hips) image[lm]?.let { p ->
                image[lm] = p.copy(x = p.x + (0.08 * kotlin.math.sin(2.0 * Math.PI * 0.4 * t)).toFloat())
            }
            val mode = if (alternate) (i / 20) % 4 else 0
            for ((lm, fixedX) in listOf(PoseLandmark.LEFT_ANKLE to 0.4f, PoseLandmark.RIGHT_ANKLE to 0.6f)) {
                val p = checkNotNull(image[lm])
                val visible = !(mode == 1 && lm == PoseLandmark.RIGHT_ANKLE || mode == 3 && lm == PoseLandmark.LEFT_ANKLE)
                image[lm] = p.copy(x = fixedX, visibility = if (visible) 1f else 0f, presence = 1f)
            }
        }
        val full = fixture(false)
        val alternating = fixture(true)
        val prepared = TrackPreparation.prepare(alternating)
        val s = checkNotNull(prepared.scale).bodyHeightFh
        val times = LongArray(prepared.track.frames.size) { prepared.track.frames[it].tUs }
        val hx = DoubleArray(times.size) {
            checkNotNull(prepared.geo.mid(it, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)).x / s
        }
        val hipSpeed = CountSeriesBuilder.horizontalSpeed(hx, times, prepared.timebase, 3, AnalysisTuning.DEFAULT)
        assertTrue("positive control: hips really cross the existing speed gate", hipSpeed.count { abs(it) > AnalysisTuning.DEFAULT.walkSpeedBhPerS } > 30)
        val baseline = ok(build(full, RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
        val switched = ok(build(alternating, RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
        assertTrue(baseline.walk.none { it })
        assertTrue("identity switches are not foot motion", switched.walk.none { it })
        org.junit.Assert.assertArrayEquals("vertical counting signal is unchanged", baseline.r, switched.r, 0.0)
    }

    @Test
    fun constantWalkingRemainsMaskedWithEitherAnkleTemporarilyHiddenAndAbstainsInLongDoubleOcclusion() {
        val truth = gen(SynthMovement.SQUAT, listOf(SynthSegment.Idle(6.0)), camera = SynthTestSupport.LANDSCAPE_FAR)
        val base = TrackPreparation.prepare(truth.track)
        val s = checkNotNull(base.scale).bodyHeightFh
        val normalizedSpeed = 0.3 * s / (truth.track.frameWidth.toDouble() / truth.track.frameHeight)
        fun fixture(mirror: Boolean, bothMissing: Boolean) = truth.track.mapFrames { i, image, _ ->
            val t = truth.track.frames[i].tUs / 1e6
            val shift = (normalizedSpeed * (t - 3.0)).toFloat()
            for ((lm, p) in image.toMap()) image[lm] = p.copy(x = p.x + shift)
            val mode = (i / 20) % 4
            for (lm in listOf(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)) {
                val p = checkNotNull(image[lm])
                val hideLeft = if (mirror) mode == 1 else mode == 3
                val hideRight = if (mirror) mode == 3 else mode == 1
                val hidden = bothMissing && t in 2.0..4.0 ||
                    lm == PoseLandmark.LEFT_ANKLE && hideLeft || lm == PoseLandmark.RIGHT_ANKLE && hideRight
                image[lm] = p.copy(visibility = if (hidden) 0f else 1f, presence = 1f)
            }
        }
        for (mirror in listOf(false, true)) {
            val walking = ok(build(fixture(mirror, false), RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
            val interior = (0 until walking.size).filter { it / walking.fs in 1.0..5.0 }
            val covered = interior.count { walking.walk[it] }.toDouble() / interior.size
            assertTrue("known 0.3 BH/s translation must retain walking with either occlusion order: $covered", covered >= 0.6)
            val unsupported = ok(build(fixture(mirror, true), RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
            val core = (0 until unsupported.size).filter { it / unsupported.fs in 2.5..3.5 }
            assertTrue(core.isNotEmpty())
            assertTrue("no walking measurement inside unsupported core, beyond existing dilation", core.none { unsupported.walk[it] })
        }
    }

    @Test
    fun horizontalSpeedNeverBridgesMissingTimestampsOrVisibilityBeyondTheExistingFillLimit() {
        val tuning = AnalysisTuning.DEFAULT
        val tb = Timebase(0L, 30.0, 360, false, 1.0)
        val times = LongArray(360) { (it * 1_000_000.0 / 30.0).roundToLong() }
        // Eighteen missing grid cells are 0.6 s, but their actual observed endpoints are 0.633333 s apart.
        // Two stationary supported spans with unknown intervening motion must not become a measured ramp.
        val keep = times.indices.filter { it !in 120..137 }
        val omittedPts = LongArray(keep.size) { times[keep[it]] }
        val x = DoubleArray(keep.size) { if (keep[it] < 120) 0.2 else 0.6 }
        val omittedSpeed = CountSeriesBuilder.horizontalSpeed(x, omittedPts, tb, 3, tuning)
        val missing = DoubleArray(times.size) { if (it in 120..137) Double.NaN else if (it < 120) 0.2 else 0.6 }
        val hiddenSpeed = CountSeriesBuilder.horizontalSpeed(missing, times, tb, 3, tuning)
        for (v in listOf(omittedSpeed, hiddenSpeed)) {
            assertTrue((119..138).all { v[it].isNaN() })
            assertTrue((30..100).all { abs(v[it]) < 1e-9 })
            assertTrue((160..320).all { abs(v[it]) < 1e-9 })
        }
        // Even adjacent grid points cannot establish a slope if observations exceed fillCountGapS.
        val sparse = CountSeriesBuilder.horizontalSpeed(doubleArrayOf(0.0, 0.3, 0.6),
            longArrayOf(0L, 1_000_000L, 2_000_000L), Timebase(0L, 1.0, 3, false, 1.0), 2, tuning)
        assertTrue(sparse.all { it.isNaN() })
    }

    @Test
    fun aShortOcclusionWithinTheSameAnkleRetainsItsKnownHorizontalVelocity() {
        val tb = Timebase(0L, 30.0, 360, false, 1.0)
        val times = LongArray(360) { (it * 1_000_000.0 / 30.0).roundToLong() }
        val x = DoubleArray(360) { if (it in 120..125) Double.NaN else 0.3 * times[it] / 1e6 }
        val speed = CountSeriesBuilder.horizontalSpeed(x, times, tb, 3, AnalysisTuning.DEFAULT)
        for (i in 90..240) org.junit.Assert.assertEquals("analytic 0.3 BH/s at $i", 0.3, speed[i], 1e-6)
    }

    @Test
    fun theBarPointFollowsTheProfileRule() {
        val fails = RepTestSupport.Failures()
        fun range(x: DoubleArray?) = x!!.filter { !it.isNaN() }.let { it.max() - it.min() }
        val squat = ok(build(gen(SynthMovement.SQUAT).track, RepTestSupport.profile(AnalysisProfileId.SQUAT))).series
        fails.check(squat.barX != null && squat.barY != null) { "sentadilla sin punto de barra" }
        fails.check(range(squat.barY) in 0.15..0.5) { "el hombro de la sentadilla baja ${range(squat.barY)} BH" }
        fails.check(squat.barY!!.count { it.isNaN() } == 0) { "punto de barra con huecos" }
        val deadlift = ok(build(gen(SynthMovement.DEADLIFT).track, RepTestSupport.profile(AnalysisProfileId.DEADLIFT))).series
        fails.check(deadlift.barY != null && range(deadlift.barY) > 0.1) { "peso muerto: punto de barra ${deadlift.barY?.let { range(it) }}" }
        val calf = ok(build(gen(SynthMovement.CALF_RAISE).track, RepTestSupport.profile(AnalysisProfileId.CALF))).series
        fails.check(calf.barX == null && calf.barY == null) { "los gemelos no tienen punto de barra" }
        // Con el disco, el punto de barra es el disco.
        val bench = gen(SynthMovement.BENCH, benchScript)
        val withPlate = ok(build(bench.track, RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true), RepTestSupport.plateFromWrist(bench))).series
        fails.check(withPlate.barY != null && range(withPlate.barY) in 0.15..0.5) { "banca con disco: ${withPlate.barY?.let { range(it) }}" }
        fails.assertNone()
    }

    // ---- lado de trabajo ----

    @Test
    fun perSideProfilesKeepBothSidesOnlyWhenBothAreReliable() {
        val fails = RepTestSupport.Failures()
        val curl = RepTestSupport.profile(AnalysisProfileId.CURL)
        val front = ok(build(gen(SynthMovement.CURL, camera = SynthCamera(yawDeg = 0.0)).track, curl)).series
        fails.check(front.perSide != null && front.perSide!!.keys == setOf(Side.LEFT, Side.RIGHT)) { "de frente: lados ${front.perSide?.keys}" }
        fails.check(front.workSide == null) { "de frente: lado de trabajo ${front.workSide}" }
        val frontRun = RepTestSupport.run(gen(SynthMovement.CURL, camera = SynthCamera(yawDeg = 0.0)), curl)!!
        val frontFlags = RepFlags.of(frontRun.prepared, frontRun.series, frontRun.seg)
        fails.check(AnalysisFlag.SIDE_UNKNOWN !in frontFlags && AnalysisFlag.FAR_SIDE_WORKING !in frontFlags) { "de frente: avisos $frontFlags" }
        // De lado, el lejano es débil: manda el cercano y no se sabe qué brazo trabaja.
        val sideRun = RepTestSupport.run(gen(SynthMovement.CURL, noise = SynthNoise()), curl)!!
        val side = sideRun.series
        fails.check(side.perSide == null && side.workSide == Side.LEFT) { "de lado: lados ${side.perSide?.keys} trabaja ${side.workSide}" }
        val sideFlags = RepFlags.of(sideRun.prepared, sideRun.series, sideRun.seg)
        fails.check(AnalysisFlag.SIDE_UNKNOWN in sideFlags && AnalysisFlag.FAR_SIDE_WORKING !in sideFlags) { "de lado: avisos $sideFlags" }
        // Un solo brazo, el lejano (el derecho, de lado a la cámara por el izquierdo): se cuenta el que se mueve.
        val farScript = listOf<SynthSegment>(SynthSegment.Idle(2.0)) +
            List(5) { SynthSegment.Rep(1.5, 0.5, 1.5, 0.5, 1.0, Side.RIGHT, SynthRepKind.FULL) } + SynthSegment.Idle(2.0)
        val far = gen(SynthMovement.CURL, farScript, noise = SynthNoise())
        val run = RepTestSupport.run(far, curl)
        fails.check(run != null && run.series.workSide == Side.RIGHT) { "brazo lejano: lado de trabajo ${run?.series?.workSide}" }
        fails.check(run?.seg?.fullCount == 5) { "brazo lejano: ${run?.seg?.let { RepTestSupport.summary(it) }}" }
        fails.check(run?.seg?.sideMode == SideMode.NEAR_ONLY) { "brazo lejano: modo ${run?.seg?.sideMode}" }
        val farFlags = run?.let { RepFlags.of(it.prepared, it.series, it.seg) } ?: emptySet()
        fails.check(AnalysisFlag.FAR_SIDE_WORKING in farFlags && AnalysisFlag.SIDE_UNKNOWN !in farFlags) { "brazo lejano: avisos $farFlags" }
        // Con el lado lejano trabajando, la confianza llega como mucho a MEDIA, no a BAJA por la visibilidad débil.
        if (run != null) {
            val level = RepConfidence.score(run.series, run.seg, run.prepared.view, run.profile, farFlags)
            println("[confianza] brazo lejano: nivel ${level.level} puntuación %.2f fracción válida %.2f".format(level.score, run.series.validFraction))
            fails.check(level.level == CountConfidence.MEDIA) { "brazo lejano: confianza ${level.level} ($level)" }
        }
        fails.assertNone()
    }
}
