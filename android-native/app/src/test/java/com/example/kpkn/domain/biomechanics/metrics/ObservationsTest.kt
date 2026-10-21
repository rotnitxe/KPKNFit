package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ProfileTier
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import com.example.kpkn.domain.biomechanics.vbt.VbtTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * Las observaciones de técnica (§7): una prueba por regla de la tabla, que dispara cuando debe y calla sin sus datos, más el tope de 3,
 * el orden, las palabras prohibidas y que nunca salgan m/s ni 1RM sin calibrar.
 *
 * Los defectos se inyectan en la traza del generador (sin ruido, a 30 fps, de lado con el atleta de lado izquierdo hacia la cámara:
 * el atleta mira a la IZQUIERDA de la imagen, f = −1) moviendo puntos con [shifted]: la cadera para torcer el tronco sin tocar las
 * señales de conteo, el talón, el codo… Los desplazamientos van en unidades FH (alto del fotograma = 1) y el atleta ocupa ~23 % del alto
 * (el tronco mide ~0,12 FH).
 */
class ObservationsTest {
    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.1f", x)

    // ------------------------------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------------------------------

    private class Run(
        val report: SetTechniqueReport,
        val scene: MetricsTestSupport.Scene,
        val candidates: Map<String, Candidate>,
    ) {
        fun fired(code: String): Boolean = code in candidates
        fun reps(code: String): List<Int> = candidates[code]?.repIndices ?: emptyList()
        fun text(code: String): String = candidates[code]?.text ?: ""
    }

    private fun run(
        result: SynthResult,
        profile: AnalysisProfile,
        bar: BarTrack? = null,
        hints: Set<String> = emptySet(),
        withContext: Boolean = true,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): Run {
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(result.track, bar, profile, null, null, hints, tuning))
        val scene = MetricsTestSupport.scene(result, profile, bar, hints, tuning) ?: error("sin señal: ${report.noRepsReason}")
        val path = PathMetrics.compute(scene.ctx, bar = bar)
        val found = Observations.candidates(
            report, scene.run.prepared, profile, report.scale ?: ScaleInfo.NONE, tuning, if (withContext) scene.ctx else null, path, hints,
        )
        return Run(report, scene, found.associateBy { it.code })
    }

    private fun describe(label: String, run: Run) {
        println(
            "[observaciones] $label: ${run.report.repCount} reps ${run.report.confidence} vista ${run.report.view} | " +
                (if (run.candidates.isEmpty()) "ninguna" else run.candidates.values.joinToString(" | ") { "${it.code} ${it.repIndices.map { i -> i + 1 }} x${String.format(Locale.ROOT, "%.1f", it.strength)}: ${it.text}" }),
        )
    }

    private fun smooth(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        return u * u * (3.0 - 2.0 * u)
    }

    /** De 0 a 1 entre [a] y [b] (µs), suave. */
    private fun ramp(t: Long, a: Long, b: Long): Double = if (b <= a) (if (t >= b) 1.0 else 0.0) else smooth((t - a).toDouble() / (b - a))

    /** Desplaza los puntos [landmarks] en cada fotograma: [dxFh] y [dyFh] (FH) según el instante (µs). */
    private fun SynthResult.shifted(
        landmarks: Collection<PoseLandmark>,
        dxFh: (Long) -> Double = { 0.0 },
        dyFh: (Long) -> Double = { 0.0 },
    ): SynthResult {
        val aspect = track.frameWidth.toDouble() / track.frameHeight
        val times = track.frames.map { it.tUs }
        val moved = track.mapFrames { i, image, _ ->
            val dx = dxFh(times[i])
            val dy = dyFh(times[i])
            if (dx != 0.0 || dy != 0.0) {
                for (lm in landmarks) {
                    val p = image[lm] ?: continue
                    image[lm] = p.copy(x = p.x + (dx / aspect).toFloat(), y = p.y + dy.toFloat())
                }
            }
        }
        return MetricsTestSupport.withTrack(this, moved)
    }

    /** Un pico suave de [amount] entre [from] y [to] (µs) que sube y baja con un seno al cuadrado. */
    private fun bump(t: Long, from: Long, to: Long, amount: Double): Double {
        if (t <= from || t >= to) return 0.0
        val s = Math.sin(Math.PI * (t - from).toDouble() / (to - from))
        return amount * s * s
    }

    /** Sube de 0 a [amount] entre [a] y [b] (µs), se mantiene y vuelve a 0 entre [b] y [c]: un cambio que se queda durante una fase. */
    private fun plateau(t: Long, a: Long, b: Long, c: Long, amount: Double): Double = amount * (ramp(t, a, b) - ramp(t, b, c))

    private val hips = listOf(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
    private val shoulders = listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
    private val elbows = listOf(PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW)
    private val wrists = listOf(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)

    /** Hacia delante del atleta de lado (ψ = 90°) es hacia la izquierda de la imagen. */
    private val forward = -1.0

    private fun profile(id: AnalysisProfileId, plate: Boolean = false) = RepTestSupport.profile(id, plate)

    private fun squatAmplitudeForAlpha(alphaDeg: Double): Double = MetricsTestSupport.solve(0.5, 1.5, alphaDeg) { MetricsTestSupport.squatDepthDeg(it) }

    private fun squatAmplitudeForRom(romDeg: Double): Double {
        val rest = MetricsTestSupport.primaryAngleDeg(SynthMovement.SQUAT, 0.0)
        return MetricsTestSupport.solve(0.3, 1.5, rest + romDeg) { MetricsTestSupport.primaryAngleDeg(SynthMovement.SQUAT, it) }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Series limpias: ninguna regla debe disparar
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun cleanSetsOfEveryProfileProduceNoObservations() {
        val fails = RepTestSupport.Failures()
        val cases = listOf(
            Triple("sentadilla", SynthMovement.SQUAT, AnalysisProfileId.SQUAT),
            Triple("peso muerto", SynthMovement.DEADLIFT, AnalysisProfileId.DEADLIFT),
            Triple("flexiones", SynthMovement.PUSH_UP, AnalysisProfileId.PUSH_UP),
            Triple("press militar", SynthMovement.OHP, AnalysisProfileId.OHP),
            Triple("remo", SynthMovement.ROW, AnalysisProfileId.ROW),
            Triple("curl", SynthMovement.CURL, AnalysisProfileId.CURL),
            Triple("laterales", SynthMovement.LATERAL_RAISE, AnalysisProfileId.RAISE),
        )
        for ((label, movement, id) in cases) {
            val r = MetricsTestSupport.gen(movement, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5))
            val run = run(r, profile(id))
            describe("limpia: $label", run)
            fails.check(run.report.repCount == 6) { "$label: conteo ${run.report.repCount}" }
            fails.check(run.candidates.isEmpty()) { "$label: no debería disparar nada y dispara ${run.candidates.keys}" }
            fails.check(run.report.observations.isEmpty()) { "$label: observaciones del informe ${run.report.observations.map { it.code }}" }
        }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Comunes
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun theRomTrendFiresWhenTheLastRepsAreFourteenDegreesShorter() {
        val fails = RepTestSupport.Failures()
        val full = squatAmplitudeForRom(100.0)
        val short = squatAmplitudeForRom(86.0)
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(listOf(full, full, full, full, short, short)))
        val run = run(r, profile(AnalysisProfileId.SQUAT))
        describe("tendencia del ROM", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.fired(ObservationCodes.ROM_TREND)) { "debería disparar la tendencia: ${run.candidates.keys}" }
        fails.check(run.text(ObservationCodes.ROM_TREND).contains("se acortó") && run.text(ObservationCodes.ROM_TREND).contains("rodilla")) { run.text(ObservationCodes.ROM_TREND) }
        fails.check(run.reps(ObservationCodes.ROM_TREND).sorted() == listOf(0, 1, 4, 5)) { "repeticiones ${run.reps(ObservationCodes.ROM_TREND)} (las dos primeras y las dos últimas)" }

        // Con una caída de 4°, bajo el umbral de 6°, calla.
        val mild = squatAmplitudeForRom(96.0)
        val quiet = run(MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(listOf(full, full, full, full, mild, mild))), profile(AnalysisProfileId.SQUAT))
        fails.check(!quiet.fired(ObservationCodes.ROM_TREND)) { "con 4° de caída no debería disparar: ${quiet.report.romTrendDeg}" }
        // Con tres repeticiones no hay tendencia.
        val three = run(MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(listOf(full, short, short))), profile(AnalysisProfileId.SQUAT))
        fails.check(!three.fired(ObservationCodes.ROM_TREND)) { "con 3 FULL no hay tendencia" }
        fails.assertNone()
    }

    @Test
    fun theTrunkChangeBetweenTheFirstAndTheLastRep() {
        val fails = RepTestSupport.Failures()
        val r0 = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5))
        // Los hombros se adelantan 0,03 FH a partir de la tercera repetición (~11° más de inclinación del tronco).
        val from = r0.reps[2].departUs
        val r = r0.shifted(shoulders, dxFh = { t -> forward * 0.03 * ramp(t, from, from + 500_000) })
        val run = run(r, profile(AnalysisProfileId.SQUAT))
        describe("cambio del tronco", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.fired(ObservationCodes.TRUNK_CHANGE)) { "debería disparar: τ en el giro ${run.report.reps.map { fmt(it.trunkAtTurnDeg) }}" }
        fails.check(run.text(ObservationCodes.TRUNK_CHANGE).contains("más en la última repetición que en la primera")) { run.text(ObservationCodes.TRUNK_CHANGE) }
        fails.check(run.reps(ObservationCodes.TRUNK_CHANGE) == listOf(0, 5)) { "repeticiones ${run.reps(ObservationCodes.TRUNK_CHANGE)}" }
        // Sin el cambio, calla (cubierto por la serie limpia), y de frente no hay tronco.
        val front = run(MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { 1.0 }), camera = SynthCamera(yawDeg = 0.0)), profile(AnalysisProfileId.SQUAT))
        fails.check(front.candidates.isEmpty()) { "de frente no debería disparar: ${front.candidates.keys}" }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sentadilla
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun squatDepthAboveParallelAndIrregularDepth() {
        val fails = RepTestSupport.Failures()
        val below = squatAmplitudeForAlpha(10.0)
        val above = squatAmplitudeForAlpha(-10.0)
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(listOf(below, below, above, above, below, below)))
        val run = run(r, profile(AnalysisProfileId.SQUAT))
        describe("profundidad", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.SQUAT_DEPTH_ABOVE) == listOf(2, 3)) { "por encima del paralelo: ${run.reps(ObservationCodes.SQUAT_DEPTH_ABOVE)}" }
        fails.check(run.text(ObservationCodes.SQUAT_DEPTH_ABOVE).startsWith("En las repeticiones 3 y 4 la cadera no bajó del paralelo (≈ ")) { run.text(ObservationCodes.SQUAT_DEPTH_ABOVE) }
        fails.check(run.fired(ObservationCodes.SQUAT_DEPTH_IRREGULAR)) { "DE de α: ${run.report.reps.map { fmt(it.depthDeg) }}" }
        fails.check(run.fired(ObservationCodes.SQUAT_DEPTH_SHALLOW_REP) && run.reps(ObservationCodes.SQUAT_DEPTH_SHALLOW_REP) == listOf(2, 3)) {
            "repeticiones más altas que la mediana: ${run.reps(ObservationCodes.SQUAT_DEPTH_SHALLOW_REP)}"
        }

        // Todas a la par: nada. Todas por encima: sí «no bajó del paralelo», pero no «irregular».
        val level = squatAmplitudeForAlpha(0.0)
        val parallel = run(MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { level })), profile(AnalysisProfileId.SQUAT))
        fails.check(parallel.candidates.isEmpty()) { "todas a la par: ${parallel.candidates.keys}" }
        val allAbove = run(MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { above })), profile(AnalysisProfileId.SQUAT))
        fails.check(allAbove.fired(ObservationCodes.SQUAT_DEPTH_ABOVE) && !allAbove.fired(ObservationCodes.SQUAT_DEPTH_IRREGULAR)) { "todas por encima: ${allAbove.candidates.keys}" }

        // Calla sin sus datos: de frente no hay profundidad absoluta; con la cadera o la rodilla a menos de 0,7 de visibilidad en el fondo, tampoco.
        val front = run(MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { above }), camera = SynthCamera(yawDeg = 0.0)), profile(AnalysisProfileId.SQUAT))
        fails.check(!front.fired(ObservationCodes.SQUAT_DEPTH_ABOVE)) { "de frente no debería disparar" }
        val base = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { above }))
        val dim = MetricsTestSupport.withTrack(
            base,
            base.track.mapFrames { _, image, _ ->
                for (lm in listOf(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)) {
                    val p = image[lm] ?: continue
                    image[lm] = p.copy(visibility = 0.6f)
                }
            },
        )
        val dimRun = run(dim, profile(AnalysisProfileId.SQUAT))
        fails.check(dimRun.report.repCount == 6 && MetricKey.DEPTH in dimRun.report.available) { "con visibilidad 0,6 la profundidad se mide: ${dimRun.report.available}" }
        fails.check(!dimRun.fired(ObservationCodes.SQUAT_DEPTH_ABOVE)) { "con visibilidad 0,6 en el fondo debería callar" }
        fails.assertNone()
    }

    @Test
    fun theHipsRisingBeforeTheShoulderFiresWhenTheTrunkTipsForwardAtTheStartOfTheAscent() {
        val fails = RepTestSupport.Failures()
        val r0 = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        // En las repeticiones 2, 3 y 4 el hombro se queda atrás y abajo (0,02 FH) y el tronco se inclina hacia delante (0,03 FH) durante la subida.
        val bad = listOf(1, 2, 3)
        fun inCon(t: Long, f: (Long, Long) -> Double): Double {
            for (k in bad) {
                val rep = r0.reps[k]
                if (t > rep.turnEndUs && t < rep.arriveUs) return f(rep.turnEndUs, rep.arriveUs)
            }
            return 0.0
        }
        val r = r0.shifted(
            shoulders,
            dxFh = { t -> forward * inCon(t) { a, b -> bump(t, a, b, 0.03) } },
            dyFh = { t -> inCon(t) { a, b -> bump(t, a, b, 0.02) } },
        )
        val run = run(r, profile(AnalysisProfileId.SQUAT))
        describe("cadera adelantada", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.fired(ObservationCodes.SQUAT_HIPS_FIRST)) { "debería disparar: Δτ temprano ${run.report.reps.map { fmt(it.trunkEarlyDeltaDeg) }}" }
        fails.check(run.reps(ObservationCodes.SQUAT_HIPS_FIRST).all { it in bad }) { "repeticiones ${run.reps(ObservationCodes.SQUAT_HIPS_FIRST)} (esperadas dentro de $bad)" }
        fails.check(run.text(ObservationCodes.SQUAT_HIPS_FIRST).contains("veces más que el hombro")) { run.text(ObservationCodes.SQUAT_HIPS_FIRST) }
        // Sin el defecto, calla (serie limpia); de frente tampoco se mide el tronco.
        val front = run(MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { 1.0 }), camera = SynthCamera(yawDeg = 0.0)), profile(AnalysisProfileId.SQUAT))
        fails.check(!front.fired(ObservationCodes.SQUAT_HIPS_FIRST)) { "de frente no debería disparar" }
        fails.assertNone()
    }

    @Test
    fun theHeelLiftingFiresOnlyWithTheToeStillAndTheFootVisible() {
        val fails = RepTestSupport.Failures()
        val r0 = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        // El talón sube 0,01 FH (≈ 16 % del largo del pie, que mide ~0,063 FH) alrededor del fondo de las repeticiones 3 y 4 (índices 2 y 3).
        val bad = listOf(2, 3)
        val heel = listOf(PoseLandmark.LEFT_HEEL)
        fun lift(t: Long): Double {
            for (k in bad) {
                val rep = r0.reps[k]
                val v = bump(t, rep.turnStartUs - 700_000, rep.turnEndUs + 700_000, 0.01)
                if (v != 0.0) return -v // la Y crece hacia abajo: subir es restar
            }
            return 0.0
        }
        val r = r0.shifted(heel, dyFh = { t -> lift(t) })
        val run = run(r, profile(AnalysisProfileId.SQUAT))
        describe("talón", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.SQUAT_HEEL_LIFT) == bad) { "talón levantado en ${run.reps(ObservationCodes.SQUAT_HEEL_LIFT)} (esperado $bad)" }
        fails.check(run.text(ObservationCodes.SQUAT_HEEL_LIFT).contains("largo del pie")) { run.text(ObservationCodes.SQUAT_HEEL_LIFT) }
        // Si la punta también se mueve, no es el talón que se levanta: el pie entero se mueve.
        val both = r0.shifted(listOf(PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX), dyFh = { t -> lift(t) })
        fails.check(!run(both, profile(AnalysisProfileId.SQUAT)).fired(ObservationCodes.SQUAT_HEEL_LIFT)) { "con la punta también levantada no debería disparar" }
        // Con el talón a visibilidad 0,6 calla.
        val dim = MetricsTestSupport.withTrack(
            r,
            r.track.mapFrames { _, image, _ ->
                val p = image[PoseLandmark.LEFT_HEEL] ?: return@mapFrames
                image[PoseLandmark.LEFT_HEEL] = p.copy(visibility = 0.6f)
            },
        )
        fails.check(!run(dim, profile(AnalysisProfileId.SQUAT)).fired(ObservationCodes.SQUAT_HEEL_LIFT)) { "con el talón a 0,6 de visibilidad debería callar" }
        fails.assertNone()
    }

    @Test
    fun theBarDriftOfASquatIsReportedInPercentOfTheRomWithoutAPlate() {
        val fails = RepTestSupport.Failures()
        val r0 = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        // El hombro (el punto de barra sin disco) se va 0,03 FH hacia delante durante la subida de la repetición 4 (índice 3): ≈ 24 % del recorrido.
        val rep = r0.reps[3]
        val r = r0.shifted(
            shoulders,
            dxFh = { t -> forward * 0.03 * (ramp(t, rep.turnEndUs, rep.arriveUs) - ramp(t, rep.arriveUs + 100_000, rep.arriveUs + 700_000)) },
        )
        val run = run(r, profile(AnalysisProfileId.SQUAT))
        describe("deriva de la barra", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.SQUAT_BAR_DRIFT) == listOf(3)) { "deriva en ${run.reps(ObservationCodes.SQUAT_BAR_DRIFT)}: ${run.report.reps.map { fmt(it.pathDrift) }}" }
        val text = run.text(ObservationCodes.SQUAT_BAR_DRIFT)
        fails.check(text.contains("≈") && text.contains("% del recorrido") && text.contains("hacia delante") && text.contains("la repetición 4")) { text }
        fails.check(run.report.barDeviationUnit == "%ROM") { "unidad ${run.report.barDeviationUnit}" }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Peso muerto, rumano y hip thrust
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun theDeadliftLockoutNamesTheAngleThatWasReached() {
        val fails = RepTestSupport.Failures()
        val short = MetricsTestSupport.solve(0.3, 1.0, 150.0) { MetricsTestSupport.hipAngleDeg(SynthMovement.DEADLIFT, it) }
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(listOf(1.0, 1.0, short, short, 1.0, 1.0)))
        val run = run(r, profile(AnalysisProfileId.DEADLIFT))
        describe("bloqueo del peso muerto", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.DEADLIFT_LOCKOUT) == listOf(2, 3)) { "bloqueo en ${run.reps(ObservationCodes.DEADLIFT_LOCKOUT)}" }
        fails.check(Regex("la cadera llegó a ≈ 1[45]\\d°").containsMatchIn(run.text(ObservationCodes.DEADLIFT_LOCKOUT)) && run.text(ObservationCodes.DEADLIFT_LOCKOUT).contains("165°")) { run.text(ObservationCodes.DEADLIFT_LOCKOUT) }
        // De frente (sin SIDE) calla.
        val front = run(MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(listOf(1.0, 1.0, short, short, 1.0, 1.0)), camera = SynthCamera(yawDeg = 0.0)), profile(AnalysisProfileId.DEADLIFT))
        fails.check(!front.fired(ObservationCodes.DEADLIFT_LOCKOUT)) { "de frente no debería disparar" }
        fails.assertNone()
    }

    @Test
    fun theDeadliftLeaningBackAtTheTopFiresWhenTheHipsPushForwardAtLockout() {
        val fails = RepTestSupport.Failures()
        val r0 = MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        // Las caderas se adelantan 0,03 FH justo al terminar la subida de las repeticiones 3 y 4 (el tronco queda ≈ 14° hacia atrás).
        val bad = listOf(2, 3)
        val r = r0.shifted(
            hips,
            dxFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = r0.reps[k]
                    v += forward * bump(t, rep.turnStartUs - 500_000, rep.turnStartUs + 600_000, 0.03)
                }
                v
            },
        )
        val run = run(r, profile(AnalysisProfileId.DEADLIFT))
        describe("inclinación atrás arriba", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.DEADLIFT_LEAN_BACK) == bad) { "inclinación atrás en ${run.reps(ObservationCodes.DEADLIFT_LEAN_BACK)}" }
        fails.check(run.text(ObservationCodes.DEADLIFT_LEAN_BACK).contains("hacia atrás arriba")) { run.text(ObservationCodes.DEADLIFT_LEAN_BACK) }
        fails.assertNone()
    }

    @Test
    fun theHipsRisingBeforeTheBarFiresInTheDeadliftAndNeedsTheBarContext() {
        val fails = RepTestSupport.Failures()
        val r0 = MetricsTestSupport.gen(SynthMovement.DEADLIFT, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        // En las repeticiones 2 y 3 las caderas suben 0,06 FH extra en la primera parte de la subida (de la salida al 70 % de la concéntrica):
        // la barra sube poco y el tronco se inclina más de lo que se levanta.
        val bad = listOf(1, 2)
        val r = r0.shifted(
            hips,
            dyFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = r0.reps[k]
                    val span = rep.turnStartUs - rep.departUs
                    v -= bump(t, rep.departUs, rep.departUs + (0.7 * span).toLong(), 0.06)
                }
                v
            },
        )
        val run = run(r, profile(AnalysisProfileId.DEADLIFT))
        describe("cadera antes que la barra", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.DEADLIFT_HIPS_FIRST).all { it in bad } && run.fired(ObservationCodes.DEADLIFT_HIPS_FIRST)) {
            "cadera antes que la barra en ${run.reps(ObservationCodes.DEADLIFT_HIPS_FIRST)} (esperado dentro de $bad); Δτ temprano ${run.report.reps.map { fmt(it.trunkEarlyDeltaDeg) }}"
        }
        // Sin el contexto de las métricas (la barra), la regla calla.
        val noContext = run(r, profile(AnalysisProfileId.DEADLIFT), withContext = false)
        fails.check(!noContext.fired(ObservationCodes.DEADLIFT_HIPS_FIRST)) { "sin el contexto de la barra no debería disparar" }
        fails.assertNone()
    }

    @Test
    fun theDeadliftStyleOnlyDescribesAMixOfPausedAndContinuousReps() {
        val fails = RepTestSupport.Failures()
        // El suelo antes de cada repetición: 0,5 s (parada) y 0 (continuo) alternados.
        fun script(floor: List<Double>): List<SynthSegment> =
            listOf<SynthSegment>(SynthSegment.Idle(2.0)) + floor.map { SynthSegment.Rep(1.5, it, 1.5, 0.5, 1.0) } + SynthSegment.Idle(2.0)
        val mixed = run(MetricsTestSupport.gen(SynthMovement.DEADLIFT, script(listOf(0.5, 0.0, 0.5, 0.0, 0.5, 0.0))), profile(AnalysisProfileId.DEADLIFT))
        describe("estilo del peso muerto", mixed)
        fails.check(mixed.report.repCount == 6) { "conteo ${mixed.report.repCount}" }
        fails.check(mixed.fired(ObservationCodes.DEADLIFT_STYLE)) { "mezcla de paradas y continuas: pausas ${mixed.report.reps.map { it.restPauseMs }}" }
        val uniform = run(MetricsTestSupport.gen(SynthMovement.DEADLIFT, script(List(6) { 0.5 })), profile(AnalysisProfileId.DEADLIFT))
        fails.check(!uniform.fired(ObservationCodes.DEADLIFT_STYLE)) { "todas paradas: no hay nada que describir" }
        fails.assertNone()
    }

    @Test
    fun theDeadliftBounceNeedsThePlateAndFiftyFramesPerSecond() {
        val fails = RepTestSupport.Failures()
        // Sin pausa entre repeticiones y con una barra que no se para: el disco sube y baja en triángulo (velocidad constante),
        // sin la desaceleración del jerk mínimo, y se invierte de golpe en el suelo.
        val script = listOf<SynthSegment>(SynthSegment.Idle(2.0)) + List(6) { SynthSegment.Rep(1.5, 0.0, 1.5, 0.0, 1.0) } + SynthSegment.Idle(2.0)
        val r = MetricsTestSupport.gen(SynthMovement.DEADLIFT, script, fps = 60.0)
        val smooth = RepTestSupport.plateFromWrist(r)
        val triangle = smooth.copy(
            samples = smooth.samples.map { s ->
                // Dentro de cada repetición, la Y del disco va en línea recta de abajo (salida) a arriba (giro) y vuelve.
                val rep = r.reps.firstOrNull { s.tUs in it.departUs..it.arriveUs }
                if (rep == null) {
                    s
                } else {
                    val bottom = smooth.samples.first { it.tUs >= rep.departUs }.cy
                    val top = smooth.samples.filter { it.tUs in rep.departUs..rep.arriveUs }.minOf { it.cy }
                    val u = if (s.tUs <= rep.turnStartUs) (s.tUs - rep.departUs).toDouble() / (rep.turnStartUs - rep.departUs) else 1.0 - (s.tUs - rep.turnEndUs).toDouble() / (rep.arriveUs - rep.turnEndUs)
                    s.copy(cy = (bottom + (top - bottom) * u.coerceIn(0.0, 1.0)).toFloat())
                }
            },
        )
        val run60 = run(r, profile(AnalysisProfileId.DEADLIFT, plate = true), triangle)
        describe("rebote a 60 fps", run60)
        fails.check(run60.report.repCount == 6) { "conteo ${run60.report.repCount}" }
        fails.check(run60.fired(ObservationCodes.DEADLIFT_BOUNCE)) { "con el disco y a 60 fps debería detectar el rebote" }
        // Con la barra suave (jerk mínimo) hay toque y vuelta, pero la barra se para: no es rebote.
        val soft = run(r, profile(AnalysisProfileId.DEADLIFT, plate = true), smooth)
        fails.check(!soft.fired(ObservationCodes.DEADLIFT_BOUNCE)) { "con la barra que se para en el suelo no debería haber rebote" }
        // A 30 fps no se resuelve un contacto de menos de 0,1 s: calla.
        val r30 = MetricsTestSupport.gen(SynthMovement.DEADLIFT, script, fps = 30.0)
        val smooth30 = RepTestSupport.plateFromWrist(r30)
        val tri30 = smooth30.copy(
            samples = smooth30.samples.map { s ->
                val rep = r30.reps.firstOrNull { s.tUs in it.departUs..it.arriveUs }
                if (rep == null) s else {
                    val bottom = smooth30.samples.first { it.tUs >= rep.departUs }.cy
                    val top = smooth30.samples.filter { it.tUs in rep.departUs..rep.arriveUs }.minOf { it.cy }
                    val u = if (s.tUs <= rep.turnStartUs) (s.tUs - rep.departUs).toDouble() / (rep.turnStartUs - rep.departUs) else 1.0 - (s.tUs - rep.turnEndUs).toDouble() / (rep.arriveUs - rep.turnEndUs)
                    s.copy(cy = (bottom + (top - bottom) * u.coerceIn(0.0, 1.0)).toFloat())
                }
            },
        )
        val run30 = run(r30, profile(AnalysisProfileId.DEADLIFT, plate = true), tri30)
        fails.check(!run30.fired(ObservationCodes.DEADLIFT_BOUNCE)) { "a 30 fps no debería detectar el rebote" }
        fails.assertNone()
    }

    @Test
    fun theRomanianDeadliftFlagsTheKneeBendAndTheVariationOfTheRom() {
        val fails = RepTestSupport.Failures()
        // La sentadilla-bisagra del generador dobla la rodilla 36° en la bajada: pasa de 25°.
        val r = MetricsTestSupport.gen(SynthMovement.RDL, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 2.0, p1S = 0.5, conS = 1.5, p2S = 0.5))
        val run = run(r, profile(AnalysisProfileId.HINGE))
        describe("rumano", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.fired(ObservationCodes.HINGE_KNEE_FLEXION) && run.text(ObservationCodes.HINGE_KNEE_FLEXION).contains("la rodilla se flexionó")) { "rodilla: ${run.candidates.keys}" }
        fails.check(!run.fired(ObservationCodes.ROM_VARIES)) { "el ROM de seis repeticiones iguales no varía" }
        // De frente (sin SIDE), calla.
        val front = run(MetricsTestSupport.gen(SynthMovement.RDL, MetricsTestSupport.script(List(6) { 1.0 }), camera = SynthCamera(yawDeg = 0.0)), profile(AnalysisProfileId.HINGE))
        fails.check(!front.fired(ObservationCodes.HINGE_KNEE_FLEXION)) { "de frente no debería disparar" }
        // Amplitudes que cambian (todas FULL, a ≥ 0,70 del recorrido completo): el ROM de cadera varía ~12 %, más del 10 %.
        val varied = run(
            MetricsTestSupport.gen(SynthMovement.RDL, MetricsTestSupport.script(listOf(1.0, 0.8, 1.0, 0.8, 1.0, 0.8), eccS = 2.0, p1S = 0.5, conS = 1.5, p2S = 0.5)),
            profile(AnalysisProfileId.HINGE),
        )
        describe("rumano con amplitudes distintas", varied)
        fails.check(varied.report.repCount == 6 && varied.fired(ObservationCodes.ROM_VARIES)) { "CV del ROM ${varied.report.romCvPct}" }
        fails.assertNone()
    }

    @Test
    fun theHipThrustLockoutAndThePauseAtTheTop() {
        val fails = RepTestSupport.Failures()
        // La cadera llega a 158° (por debajo de 165°) y el recorrido sigue siendo ≥ 0,70 del completo: son FULL, no parciales.
        val short = MetricsTestSupport.solve(0.2, 1.0, 158.0) { MetricsTestSupport.hipAngleDeg(SynthMovement.HIP_THRUST, it) }
        val r = MetricsTestSupport.gen(SynthMovement.HIP_THRUST, MetricsTestSupport.script(listOf(1.0, short, short, 1.0, 1.0, 1.0)))
        val run = run(r, profile(AnalysisProfileId.HIP_THRUST))
        describe("hip thrust", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.THRUST_LOCKOUT) == listOf(1, 2)) { "bloqueo en ${run.reps(ObservationCodes.THRUST_LOCKOUT)}" }
        fails.check(run.fired(ObservationCodes.THRUST_PAUSE)) { "la pausa de 0,5 s arriba debería describirse" }
        fails.check(run.text(ObservationCodes.THRUST_PAUSE).contains("Pausa arriba")) { run.text(ObservationCodes.THRUST_PAUSE) }
        // Sin pausa arriba, calla la pausa.
        val noPause = run(MetricsTestSupport.gen(SynthMovement.HIP_THRUST, MetricsTestSupport.script(List(6) { 1.0 }, p2S = 0.0)), profile(AnalysisProfileId.HIP_THRUST))
        fails.check(!noPause.fired(ObservationCodes.THRUST_PAUSE)) { "sin pausa no hay nada que describir" }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Banca y flexiones
    // ------------------------------------------------------------------------------------------------------------

    private fun benchScript(amplitudes: List<Double>, pauseS: Double = 1.0) =
        MetricsTestSupport.script(amplitudes, eccS = 1.5, p1S = pauseS, conS = 1.5, p2S = 0.5)

    @Test
    fun theBenchFlagsAnAtypicalPathAShorterRepAndTheContactPoint() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.BENCH, benchScript(listOf(1.0, 1.0, 1.0, 1.0, 0.85)))
        val bar = RepTestSupport.plateFromWrist(r)
        // Una repetición con la barra combada 12 px a mitad del recorrido.
        val bumped = MetricsTestSupport.withPathBump(bar, r.reps[2], 12f)
        val run = run(r, profile(AnalysisProfileId.BENCH, plate = true), bumped)
        describe("banca", run)
        fails.check(run.report.repCount == 5 && run.report.view == ViewKind.LYING) { "conteo ${run.report.repCount} vista ${run.report.view}" }
        fails.check(run.reps(ObservationCodes.BENCH_ATYPICAL) == listOf(2)) { "atípica en ${run.reps(ObservationCodes.BENCH_ATYPICAL)}: PD ${run.report.reps.map { fmt(it.pathDeviationRms) }}" }
        fails.check(run.reps(ObservationCodes.BENCH_SHORTER) == listOf(4)) { "más corta en ${run.reps(ObservationCodes.BENCH_SHORTER)}" }
        fails.check(run.text(ObservationCodes.BENCH_SHORTER).startsWith("La repetición 5 quedó más corta")) { run.text(ObservationCodes.BENCH_SHORTER) }
        // La serie limpia calla las dos.
        val clean = run(r, profile(AnalysisProfileId.BENCH, plate = true), bar)
        fails.check(!clean.fired(ObservationCodes.BENCH_ATYPICAL)) { "sin combadura no hay repetición atípica" }

        // El punto de contacto: cada repetición toca el pecho en un sitio distinto (±10 px de ~87 px de recorrido).
        val offsets = listOf(0f, 10f, -10f, 8f, -8f)
        val moved = bar.copy(
            samples = bar.samples.map { s ->
                val k = r.reps.indexOfFirst { s.tUs in it.departUs..it.arriveUs }
                if (k < 0) s else s.copy(cx = s.cx + offsets[k])
            },
        )
        val spread = run(r, profile(AnalysisProfileId.BENCH, plate = true), moved)
        describe("banca con el contacto variable", spread)
        fails.check(spread.fired(ObservationCodes.BENCH_CONTACT)) { "DE del contacto: ${spread.scene.ctx.let { PathMetrics.compute(it).contactSdBh }}" }
        fails.check(!clean.fired(ObservationCodes.BENCH_CONTACT)) { "el contacto de la serie limpia es siempre el mismo" }
        fails.assertNone()
    }

    @Test
    fun theBenchPauseShorterThanHalfASecondOnlyCountsInAPausedSet() {
        val fails = RepTestSupport.Failures()
        // Cuatro repeticiones con 1 s de pausa en el pecho y una con 0,3 s.
        val amplitudes = List(5) { 1.0 }
        val pauses = listOf(1.0, 1.0, 0.3, 1.0, 1.0)
        val script = listOf<SynthSegment>(SynthSegment.Idle(2.0)) +
            amplitudes.mapIndexed { i, a -> SynthSegment.Rep(1.5, pauses[i], 1.5, 0.5, a) } + SynthSegment.Idle(2.0)
        val r = MetricsTestSupport.gen(SynthMovement.BENCH, script)
        val run = run(r, profile(AnalysisProfileId.BENCH, plate = true), RepTestSupport.plateFromWrist(r))
        describe("pausa en el pecho", run)
        fails.check(run.report.repCount == 5) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.BENCH_PAUSE) == listOf(2)) { "pausa corta en ${run.reps(ObservationCodes.BENCH_PAUSE)}: pausas ${run.report.reps.map { it.turnPauseMs }}" }
        // Una serie sin pausa (toque y vuelta) no es una serie «con pausa»: calla.
        val touchAndGo = MetricsTestSupport.gen(SynthMovement.BENCH, benchScript(List(5) { 1.0 }, pauseS = 0.0))
        val tg = run(touchAndGo, profile(AnalysisProfileId.BENCH, plate = true), RepTestSupport.plateFromWrist(touchAndGo))
        fails.check(!tg.fired(ObservationCodes.BENCH_PAUSE)) { "sin pausa no hay nada que comprobar" }
        fails.assertNone()
    }

    @Test
    fun aShortPushUpAndABrokenBodyLine() {
        val fails = RepTestSupport.Failures()
        // El reposo arriba dura 1,4 s: la ventana de cada repetición (±0,5 s) no alcanza las rampas de la cadera de sus vecinas.
        val r0 = MetricsTestSupport.gen(SynthMovement.PUSH_UP, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 1.4))
        // La cadera cae 0,025 FH (≈ 22° de quiebre) en las repeticiones 3 y 4.
        val bad = listOf(2, 3)
        val r = r0.shifted(
            hips,
            dyFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = r0.reps[k]
                    v += ramp(t, rep.departUs - 300_000, rep.departUs) - ramp(t, rep.arriveUs, rep.arriveUs + 300_000)
                }
                0.025 * v
            },
        )
        val run = run(r, profile(AnalysisProfileId.PUSH_UP))
        describe("flexiones", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.PUSHUP_BODY_LINE) == bad) { "línea del cuerpo en ${run.reps(ObservationCodes.PUSHUP_BODY_LINE)}" }
        fails.check(run.text(ObservationCodes.PUSHUP_BODY_LINE).contains("por debajo de la línea del cuerpo")) { run.text(ObservationCodes.PUSHUP_BODY_LINE) }
        // Y si la cadera sube en vez de caer, el texto dice «por encima».
        val pike = r0.shifted(
            hips,
            dyFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = r0.reps[k]
                    v += ramp(t, rep.departUs - 300_000, rep.departUs) - ramp(t, rep.arriveUs, rep.arriveUs + 300_000)
                }
                -0.025 * v
            },
        )
        val pikeRun = run(pike, profile(AnalysisProfileId.PUSH_UP))
        fails.check(pikeRun.text(ObservationCodes.PUSHUP_BODY_LINE).contains("por encima de la línea del cuerpo")) { pikeRun.text(ObservationCodes.PUSHUP_BODY_LINE) }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Press militar
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun thePressLockoutLeanBackAndKneeDrive() {
        val fails = RepTestSupport.Failures()
        val short = MetricsTestSupport.solve(0.2, 1.0, 140.0) { MetricsTestSupport.elbowAngleDeg(SynthMovement.OHP, it) }
        val r0 = MetricsTestSupport.gen(SynthMovement.OHP, MetricsTestSupport.script(listOf(1.0, short, short, 1.0, 1.0, 1.0), eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        val lock = run(r0, profile(AnalysisProfileId.OHP))
        describe("press militar (bloqueo)", lock)
        fails.check(lock.report.repCount == 6) { "conteo ${lock.report.repCount}" }
        fails.check(lock.reps(ObservationCodes.OHP_LOCKOUT) == listOf(1, 2)) { "bloqueo en ${lock.reps(ObservationCodes.OHP_LOCKOUT)}" }
        fails.check(Regex("el codo llegó a ≈ 1[34]\\d°").containsMatchIn(lock.text(ObservationCodes.OHP_LOCKOUT))) { lock.text(ObservationCodes.OHP_LOCKOUT) }

        // Inclinación hacia atrás en la mitad de la subida de las repeticiones 4 y 5: la cadera se adelanta 0,035 FH.
        val clean = MetricsTestSupport.gen(SynthMovement.OHP, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        val bad = listOf(3, 4)
        val leaned = clean.shifted(
            hips,
            dxFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = clean.reps[k]
                    v += forward * bump(t, rep.departUs, rep.turnStartUs, 0.035)
                }
                v
            },
        )
        val lean = run(leaned, profile(AnalysisProfileId.OHP))
        describe("press militar (inclinación)", lean)
        fails.check(lean.reps(ObservationCodes.OHP_LEAN_BACK) == bad) { "inclinación atrás en ${lean.reps(ObservationCodes.OHP_LEAN_BACK)}" }

        // La flexión de rodilla antes de subir solo se mira si se avisa de que el press es estricto.
        val dip = clean.shifted(
            listOf(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE),
            dxFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = clean.reps[k]
                    v += forward * bump(t, rep.departUs - 600_000, rep.departUs, 0.04)
                }
                v
            },
        )
        val strict = run(dip, profile(AnalysisProfileId.OHP), hints = setOf("STRICT_PRESS"))
        describe("press estricto con las rodillas", strict)
        fails.check(strict.fired(ObservationCodes.OHP_KNEE_DRIVE)) { "con STRICT_PRESS debería disparar: ${strict.candidates.keys}" }
        val notStrict = run(dip, profile(AnalysisProfileId.OHP))
        fails.check(!notStrict.fired(ObservationCodes.OHP_KNEE_DRIVE)) { "sin el aviso de press estricto debería callar" }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Dominada, jalón y remo
    // ------------------------------------------------------------------------------------------------------------

    private fun pullUp(amplitudes: List<Double>, hangS: Double = 0.5): SynthResult {
        val base = MetricsTestSupport.gen(SynthMovement.PULL_UP, MetricsTestSupport.script(amplitudes, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = hangS), camera = SynthCamera(distanceM = 1.7))
        // Los nudillos rodean la barra: índice y meñique a la altura de la muñeca.
        val knuckles = listOf(
            PoseLandmark.LEFT_INDEX to PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_PINKY to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_INDEX to PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_PINKY to PoseLandmark.RIGHT_WRIST,
        )
        val track = base.track.mapFrames { _, image, _ ->
            for ((knuckle, wrist) in knuckles) {
                val w = image[wrist] ?: continue
                image[knuckle] = w
            }
        }
        return MetricsTestSupport.withTrack(base, track)
    }

    @Test
    fun thePullUpChinElbowAndSwingRules() {
        val fails = RepTestSupport.Failures()
        // La repetición 4 se queda corta (amplitud 0,9): la barbilla no llega a la barra.
        val r = pullUp(listOf(1.25, 1.25, 1.25, 0.9, 1.25, 1.25))
        val run = run(r, profile(AnalysisProfileId.PULL_VERTICAL))
        describe("dominadas", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.PULL_CHIN) == listOf(3)) { "barbilla: ${run.reps(ObservationCodes.PULL_CHIN)}" }
        fails.check(run.text(ObservationCodes.PULL_CHIN) == "Barbilla sobre la barra en ≈ 5 de 6 repeticiones.") { run.text(ObservationCodes.PULL_CHIN) }
        // Sin el contexto de la barra no se distingue la dominada del jalón: la regla calla.
        val noContext = run(r, profile(AnalysisProfileId.PULL_VERTICAL), withContext = false)
        fails.check(!noContext.fired(ObservationCodes.PULL_CHIN)) { "sin el contexto no debería disparar" }

        // Colgado incompleto: los codos se quedan doblados abajo en las repeticiones 2 y 3 (el codo se va 0,03 FH de lado: ~132°). La
        // regla busca el brazo más estirado en la repetición y ±0,5 s: el codo está desplazado en todo ese tramo, y el reposo de 1,4 s
        // deja las rampas fuera de las ventanas de las repeticiones vecinas.
        val base = pullUp(List(6) { 1.25 }, hangS = 1.4)
        val bad = listOf(1, 2)
        val first = base.reps[bad.first()]
        val last = base.reps[bad.last()]
        val bent = base.shifted(
            elbows,
            dxFh = { t -> 0.03 * (ramp(t, first.departUs - 850_000, first.departUs - 500_000) - ramp(t, last.arriveUs + 500_000, last.arriveUs + 850_000)) },
        )
        val hang = run(bent, profile(AnalysisProfileId.PULL_VERTICAL))
        describe("dominadas con el codo doblado abajo", hang)
        fails.check(hang.report.repCount == 6) { "conteo ${hang.report.repCount}" }
        fails.check(hang.reps(ObservationCodes.PULL_HANG) == bad) { "colgado en ${hang.reps(ObservationCodes.PULL_HANG)}" }

        // Balanceo: las caderas y las rodillas se mueven ±0,05 FH (≈ 24 % de la altura corporal de 0,413 FH) en las repeticiones 4 y 5.
        // Los tobillos quedan quietos: si no, el segmentador lo tomaría por caminar.
        val swing = listOf(3, 4)
        val kipping = base.shifted(
            hips + listOf(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE),
            dxFh = { t ->
                var v = 0.0
                for (k in swing) {
                    val rep = base.reps[k]
                    if (t in rep.departUs..rep.arriveUs) v += 0.05 * Math.sin(2.0 * Math.PI * (t - rep.departUs) / 1.5e6)
                }
                v
            },
        )
        val swung = run(kipping, profile(AnalysisProfileId.PULL_VERTICAL))
        describe("dominadas con balanceo", swung)
        fails.check(swung.reps(ObservationCodes.PULL_SWING) == swing) { "balanceo en ${swung.reps(ObservationCodes.PULL_SWING)}" }
        fails.assertNone()
    }

    @Test
    fun theLatPulldownLeanBackIsOnlyForPulldowns() {
        val fails = RepTestSupport.Failures()
        val base = MetricsTestSupport.gen(SynthMovement.LAT_PULLDOWN, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        val bad = listOf(2, 3)
        // Las caderas se adelantan 0,04 FH durante el tirón y se quedan ahí hasta el final de la repetición: el tronco se echa ~17° hacia atrás.
        val leaned = base.shifted(
            hips,
            dxFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = base.reps[k]
                    v += forward * plateau(t, rep.departUs, rep.turnStartUs, rep.arriveUs + 300_000, 0.04)
                }
                v
            },
        )
        val run = run(leaned, profile(AnalysisProfileId.PULL_VERTICAL))
        describe("jalón", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.PULLDOWN_LEAN_BACK) == bad) { "inclinación atrás en ${run.reps(ObservationCodes.PULLDOWN_LEAN_BACK)}" }
        fails.check(!run.fired(ObservationCodes.PULL_CHIN) && !run.fired(ObservationCodes.PULL_SWING)) { "en un jalón no se juzga la barbilla ni el balanceo: ${run.candidates.keys}" }
        fails.assertNone()
    }

    @Test
    fun theRowTorsoRisingAndTheTrunkSwing() {
        val fails = RepTestSupport.Failures()
        val base = MetricsTestSupport.gen(SynthMovement.ROW, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        val bad = listOf(3, 4)
        // La cadera baja 0,06 FH durante la tracción y se queda abajo hasta el final de la repetición: el torso se levanta ~19°.
        val r = base.shifted(
            hips,
            dyFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = base.reps[k]
                    v += plateau(t, rep.departUs, rep.turnStartUs, rep.arriveUs + 300_000, 0.06)
                }
                v
            },
        )
        val run = run(r, profile(AnalysisProfileId.ROW))
        describe("remo", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.ROW_TORSO_RISE) == bad) { "el torso sube en ${run.reps(ObservationCodes.ROW_TORSO_RISE)}" }
        fails.check(run.reps(ObservationCodes.ROW_SWING) == bad) { "balanceo del tronco en ${run.reps(ObservationCodes.ROW_SWING)}: rangos ${run.report.reps.map { fmt(it.trunkRangeDeg) }}" }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Curl, tríceps y elevaciones
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun theCurlTrunkSwingElbowDriftAndIncompleteExtension() {
        val fails = RepTestSupport.Failures()
        val base = MetricsTestSupport.gen(SynthMovement.CURL, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        val bad = listOf(3, 4)
        fun during(t: Long, amount: Double): Double {
            var v = 0.0
            for (k in bad) {
                val rep = base.reps[k]
                v += bump(t, rep.departUs, rep.arriveUs, amount)
            }
            return v
        }
        // Balanceo del tronco: las caderas se adelantan 0,03 FH (≈ 14°) en la repetición.
        val swing = run(base.shifted(hips, dxFh = { t -> forward * during(t, 0.03) }), profile(AnalysisProfileId.CURL))
        describe("curl con balanceo", swing)
        fails.check(swing.report.repCount == 6) { "conteo ${swing.report.repCount}" }
        fails.check(swing.reps(ObservationCodes.ARM_TRUNK_SWING) == bad) { "balanceo en ${swing.reps(ObservationCodes.ARM_TRUNK_SWING)}" }
        fails.check(!swing.fired(ObservationCodes.ARM_ELBOW_DRIFT)) { "14° de cambio de θ(H,S,E) están bajo el umbral de 25°" }

        // Deriva del codo: con las caderas 0,08 FH adelantadas, θ(cadera, hombro, codo) cambia ~34°.
        val drift = run(base.shifted(hips, dxFh = { t -> forward * during(t, 0.08) }), profile(AnalysisProfileId.CURL))
        describe("curl con el codo a la deriva", drift)
        fails.check(drift.reps(ObservationCodes.ARM_ELBOW_DRIFT) == bad) { "deriva del codo en ${drift.reps(ObservationCodes.ARM_ELBOW_DRIFT)}" }

        // Extensión incompleta abajo: la muñeca se queda adelantada mientras el brazo está cerca de abajo (peso 1 con menos de 30° de
        // flexión verdadera, 0 con más de 60°, según el ángulo del generador), así que el codo no pasa de ~143° en ninguna repetición
        // (adelanto de 0,04 FH sobre un antebrazo de ~0,06 FH: +34° de flexión). Con 0,01 FH llegaría a ~167°.
        val frameOf = base.track.frames.withIndex().associate { (i, frame) -> frame.tUs to i }
        fun nearBottom(t: Long): Double {
            val i = frameOf[t] ?: return 0.0
            return ((60.0 - base.primaryAngleDeg[i]) / 30.0).coerceIn(0.0, 1.0)
        }
        val ext = run(base.shifted(wrists, dxFh = { t -> forward * 0.04 * nearBottom(t) }), profile(AnalysisProfileId.CURL))
        describe("curl sin extender", ext)
        fails.check(ext.report.repCount == 6) { "conteo ${ext.report.repCount}" }
        fails.check(ext.reps(ObservationCodes.CURL_EXTENSION).size == 6) { "extensión en ${ext.reps(ObservationCodes.CURL_EXTENSION)}" }
        val almost = run(base.shifted(wrists, dxFh = { t -> forward * 0.01 * nearBottom(t) }), profile(AnalysisProfileId.CURL))
        fails.check(almost.report.repCount == 6 && !almost.fired(ObservationCodes.CURL_EXTENSION)) {
            "con el brazo a ~167° no debería disparar: conteo ${almost.report.repCount}, ${almost.candidates.keys}"
        }
        // En un tríceps el umbral de deriva baja a 20° y no hay regla de extensión (solo la del curl).
        fails.assertNone()
    }

    @Test
    fun theLateralRaiseMomentum() {
        val fails = RepTestSupport.Failures()
        val base = MetricsTestSupport.gen(SynthMovement.LATERAL_RAISE, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(distanceM = 1.7))
        val bad = listOf(3, 4)
        val r = base.shifted(
            hips,
            dxFh = { t ->
                var v = 0.0
                for (k in bad) {
                    val rep = base.reps[k]
                    v += forward * bump(t, rep.departUs, rep.arriveUs, 0.03)
                }
                v
            },
        )
        val run = run(r, profile(AnalysisProfileId.RAISE))
        describe("laterales", run)
        fails.check(run.report.repCount == 6) { "conteo ${run.report.repCount}" }
        fails.check(run.reps(ObservationCodes.RAISE_MOMENTUM) == bad) { "impulso en ${run.reps(ObservationCodes.RAISE_MOMENTUM)}" }
        // De frente solo se vigila la simetría, que no entra en v1: calla.
        val front = run(
            MetricsTestSupport.gen(SynthMovement.LATERAL_RAISE, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 1.5, p1S = 0.5, conS = 1.5, p2S = 0.5), camera = SynthCamera(yawDeg = 0.0)),
            profile(AnalysisProfileId.RAISE),
        )
        fails.check(front.candidates.isEmpty()) { "de frente no debería disparar nada: ${front.candidates.keys}" }
        fails.assertNone()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Disco calibrado: barra por delante del medio pie
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun theBarAheadOfTheMidfootInARomanianDeadliftNeedsTheCalibratedPlate() {
        val fails = RepTestSupport.Failures()
        val r = MetricsTestSupport.gen(SynthMovement.RDL, MetricsTestSupport.script(List(6) { 1.0 }, eccS = 2.0, p1S = 0.5, conS = 1.5, p2S = 0.5))
        val f = VbtTestSupport.focalPx()
        val zp = 2.30 // el disco, 0,70 m más cerca de la cámara que el plano sagital (3,0 m)
        val zWrist = VbtTestSupport.leftWristDepthM()
        val scaleUp = zWrist / zp
        // Los pies se fijan en el centro del fotograma (medio pie a 0 m del eje óptico).
        val feet = listOf(PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX)
        val fixedFeet = MetricsTestSupport.withTrack(
            r,
            r.track.mapFrames { _, image, _ ->
                for (lm in feet) {
                    val p = image[lm] ?: continue
                    image[lm] = p.copy(x = 0.5f)
                }
            },
        )

        // El disco sube y baja con la muñeca (a la escala del plano del disco) y está SIEMPRE [aheadM] metros por delante del medio pie
        // (hacia la izquierda de la imagen): en el plano del disco, a z_p = 2,30 m, eso son aheadM·f/z_p píxeles.
        fun plateAhead(aheadM: Double): BarTrack {
            val samples = r.cleanTrack.frames.map { fr ->
                BarSample(
                    tUs = fr.tUs,
                    cx = (360.0 - aheadM * f / zp).toFloat(),
                    cy = (640.0 + (fr.y(PoseLandmark.LEFT_WRIST) * 1280.0 - 640.0) * scaleUp).toFloat(),
                    r = VbtTestSupport.radiusPx(450, zp),
                    axisRatio = 1f,
                    quality = 0.9f,
                    source = com.example.kpkn.domain.biomechanics.bar.BarSampleSource.HOUGH,
                )
            }
            val seed = samples.first().let { PlateSeed(it.tUs, it.cx, it.cy, it.r, true) }
            return BarTrack(r.track.mediaId, 720, 1280, 450, seed, samples,
                trackingContinuity = PlateTrackingContinuity.UNINTERRUPTED)
        }

        val ahead = analyze(fixedFeet, profile(AnalysisProfileId.HINGE, plate = true), plateAhead(0.11))
        println("[observaciones] rumano con el disco 11 cm por delante: ${ahead.repCount} reps escala ${ahead.scale?.source} desvío ${fmt(ahead.barDeviation)} ${ahead.barDeviationUnit} | ${ahead.observations.map { it.text }}")
        fails.check(ahead.repCount == 6) { "conteo ${ahead.repCount}" }
        fails.check(ahead.barDeviationUnit == "cm" && ahead.barDeviation != null && abs((ahead.barDeviation ?: 0.0) - 11.0) <= 1.5) {
            "desvío ${ahead.barDeviation} ${ahead.barDeviationUnit} (esperado ≈ 11 cm)"
        }
        fails.check(ahead.observations.any { it.code == ObservationCodes.HINGE_BAR_AHEAD }) {
            "debería decir que la barra va por delante del medio pie: ${ahead.observations.map { it.code }}"
        }

        // 3 cm por delante: bajo el umbral de 8 cm.
        val near = analyze(fixedFeet, profile(AnalysisProfileId.HINGE, plate = true), plateAhead(0.03))
        fails.check(near.observations.none { it.code == ObservationCodes.HINGE_BAR_AHEAD }) { "con 3 cm no debería disparar: ${near.observations.map { it.code }}" }

        // Sin diámetro (sin calibrar) calla: no hay centímetros.
        val uncal = analyze(fixedFeet, profile(AnalysisProfileId.HINGE, plate = true), plateAhead(0.11).copy(plateDiameterMm = null))
        fails.check(uncal.observations.none { it.code == ObservationCodes.HINGE_BAR_AHEAD }) { "sin calibrar no hay cm: ${uncal.observations.map { it.code }}" }
        fails.assertNone()
    }

    private fun analyze(r: SynthResult, profile: AnalysisProfile, bar: BarTrack? = null): SetTechniqueReport =
        TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, bar, profile, null, null, emptySet()))

    // ------------------------------------------------------------------------------------------------------------
    // Reglas generales del conjunto
    // ------------------------------------------------------------------------------------------------------------

    @Test
    fun atMostThreeObservationsOrderedByStrengthTimesRepsTimesConfidence() {
        val fails = RepTestSupport.Failures()
        // Una serie con muchos defectos a la vez: la sentadilla más corta y desigual, con la deriva de la barra.
        val below = squatAmplitudeForAlpha(10.0)
        val above = squatAmplitudeForAlpha(-12.0)
        val r0 = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(listOf(below, above, above, above, below, above, above, below)), camera = SynthCamera(distanceM = 1.7))
        val drifted = r0.shifted(
            shoulders,
            dxFh = { t ->
                var v = 0.0
                for (k in listOf(1, 2, 3)) {
                    val rep = r0.reps[k]
                    v += forward * 0.03 * (ramp(t, rep.turnEndUs, rep.arriveUs) - ramp(t, rep.arriveUs + 100_000, rep.arriveUs + 700_000))
                }
                v
            },
        )
        val run = run(drifted, profile(AnalysisProfileId.SQUAT))
        describe("muchas reglas", run)
        val list = run.report.observations
        fails.check(run.candidates.size >= 3) { "se esperaban al menos 3 reglas disparando: ${run.candidates.keys}" }
        fails.check(list.size == 3) { "como mucho 3 observaciones y aquí exactamente 3: ${list.map { it.code }}" }
        fails.check(list.map { it.rank } == list.map { it.rank }.sortedDescending()) { "orden por rank: ${list.map { it.rank }}" }
        fails.check(list.map { it.code }.toSet().size == list.size) { "códigos repetidos ${list.map { it.code }}" }
        // La confianza BAJA no da observaciones.
        val low = run.report.copy(confidence = CountConfidence.BAJA)
        fails.check(Observations.evaluate(low, run.scene.run.prepared, profile(AnalysisProfileId.SQUAT), ScaleInfo.NONE, AnalysisTuning.DEFAULT, run.scene.ctx).isEmpty()) { "con confianza BAJA no hay observaciones" }
        fails.assertNone()
    }

    @Test
    fun noTextEverMentionsAnythingForbiddenAndNoMetersPerSecondNorOneRepMaxWithoutCalibration() {
        val fails = RepTestSupport.Failures()
        val texts = ArrayList<String>()
        fun add(s: String) {
            texts.add(s)
        }
        // Todas las funciones de texto con datos extremos (repeticiones de la 1 a la 40, ángulos y porcentajes enormes y diminutos).
        val manyReps = (0 until 40).toList()
        val oneRep = listOf(0)
        for (reps in listOf(oneRep, listOf(6, 7), listOf(2, 4, 5), manyReps)) {
            for (x in listOf(0.0, 0.4, 5.0, 99.9, 1234.5)) {
                add(ObservationText.squatDepthAbove(reps, x))
                add(ObservationText.squatDepthShallowRep(reps, x))
                add(ObservationText.squatHipsFirst(reps, x, x))
                add(ObservationText.barDrift(reps, x, true, true))
                add(ObservationText.barDrift(reps, x, false, false))
                add(ObservationText.squatHeelLift(reps, x))
                add(ObservationText.deadliftHipsFirst(reps, x, x))
                add(ObservationText.deadliftLockout(reps, x, x, 165.0, 160.0))
                add(ObservationText.deadliftLockout(reps, x, null, 165.0, 160.0))
                add(ObservationText.deadliftLockout(reps, null, x, 165.0, 160.0))
                add(ObservationText.deadliftLockout(reps, null, null, 165.0, 160.0))
                add(ObservationText.leanBackAtTop(reps, x))
                add(ObservationText.deadliftBounce(reps))
                add(ObservationText.hingeKneeFlexion(reps, x))
                add(ObservationText.hingeBarAhead(reps, x))
                add(ObservationText.thrustLockout(reps, x, 165.0))
                add(ObservationText.benchAtypical(reps, x, true))
                add(ObservationText.benchAtypical(reps, x, false))
                add(ObservationText.benchShorter(reps, x, true))
                add(ObservationText.benchShorter(reps, x, false))
                add(ObservationText.benchPause(reps, x * 10, x * 20))
                add(ObservationText.pushUpShort(reps, x))
                add(ObservationText.pushUpBodyLine(reps, x, true))
                add(ObservationText.pushUpBodyLine(reps, x, false))
                add(ObservationText.ohpLeanBack(reps, x))
                add(ObservationText.ohpLockoutElbow(reps, x, 160.0))
                add(ObservationText.ohpLockoutWrist(reps))
                add(ObservationText.ohpKneeDrive(reps, x))
                add(ObservationText.pullHang(reps, x, 155.0))
                add(ObservationText.pullSwing(reps, x, x))
                add(ObservationText.pullSwing(reps, x, null))
                add(ObservationText.pullSwing(reps, null, x))
                add(ObservationText.pulldownLeanBack(reps, x))
                add(ObservationText.rowTorsoRise(reps, x))
                add(ObservationText.trunkSwing(reps, x))
                add(ObservationText.elbowDrift(reps, x))
                add(ObservationText.curlExtension(reps, x, 150.0))
                add(ObservationText.raiseMomentum(reps, x, x))
                add(ObservationText.raiseMomentum(reps, x, null))
                add(ObservationText.raiseMomentum(reps, null, x))
            }
        }
        for (x in listOf(0.0, 0.4, 5.0, 99.9, 1234.5)) {
            for (angle in com.example.kpkn.domain.biomechanics.profiles.RomAngle.entries) {
                add(ObservationText.romTrend(angle, x, x))
                add(ObservationText.romTrend(angle, -x, x))
                add(ObservationText.romVaries(angle, x))
            }
            add(ObservationText.trunkChange(x))
            add(ObservationText.trunkChange(-x))
            add(ObservationText.squatDepthIrregular(x))
            add(ObservationText.deadliftStyle(1, 3))
            add(ObservationText.deadliftStyle(40, 1))
            add(ObservationText.thrustPause(x * 100))
            add(ObservationText.benchContact(x, true))
            add(ObservationText.benchContact(x, false))
            add(ObservationText.pullChin(0, 1))
            add(ObservationText.pullChin(5, 40))
        }
        fails.check(texts.isNotEmpty()) { "sin textos" }
        for (text in texts) {
            fails.check(forbiddenIn(text) == null) { "«$text» lleva «${forbiddenIn(text)}»" }
            fails.check(text.isNotEmpty() && text.first().isUpperCase() && text.endsWith(".")) { "«$text» no es una frase" }
            // m/s y 1RM sin calibrar: nunca.
            fails.check(!text.contains("m/s") && !text.contains("1RM") && !text.contains("RM ")) { "«$text» habla de m/s o de 1RM" }
        }
        println("[observaciones] revisados ${texts.size} textos sin palabras prohibidas")

        // Y los textos de las observaciones que salen de un motor sin calibrar tampoco mencionan m/s ni 1RM: cualquier serie del resto de pruebas.
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(6) { squatAmplitudeForAlpha(-12.0) }))
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, null, profile(AnalysisProfileId.SQUAT), null, null, emptySet()))
        fails.check(report.scale == ScaleInfo.NONE && report.observations.isNotEmpty()) { "debería haber observaciones sin calibrar: ${report.observations.map { it.code }}" }
        for (o in report.observations) fails.check(forbiddenIn(o.text) == null && !o.text.contains("m/s") && !o.text.contains("1RM")) { "«${o.text}»" }
        fails.assertNone()
    }

    /** La primera palabra o frase prohibida (§7, «NUNCA se emite»), o `null`. Sin distinguir mayúsculas. */
    private fun forbiddenIn(text: String): String? {
        val lower = text.lowercase()
        val words = listOf(
            "error", "errores", "incorrecto", "incorrecta", "incorrectos", "incorrectas", "peligroso", "peligrosa", "peligro", "riesgo", "riesgos",
            "lesión", "lesion", "válido", "válida", "valido", "valida", "nulo", "nula", "mal", "mala", "malo", "debes", "debería", "deberías",
            "corrige", "corregir", "rir", "rpe", "lumbar", "columna", "espalda", "cabeza", "cuello", "mirada", "valgo", "agarre", "arco",
            "movilidad", "butt", "wink",
        )
        for (w in words) {
            if (Regex("(^|[^\\p{L}])" + Regex.escape(w) + "([^\\p{L}]|$)").containsMatchIn(lower)) return w
        }
        val phrases = listOf("rodillas por delante", "codos abiertos", "espalda redonda", "barra alta", "barra baja", "ancho de pies", "ángulo de pies")
        for (p in phrases) if (lower.contains(p)) return p
        // Emojis y símbolos gráficos.
        for (ch in text.codePoints().toArray()) {
            if (ch >= 0x1F000 || ch in 0x2600..0x27BF || ch == 0xFE0F || ch == 0x200D) return "emoji U+" + Integer.toHexString(ch)
        }
        return null
    }

    @Test
    fun theTextsUseTheDecimalCommaAndNumberTheRepsFromOne() {
        assertEquals("En las repeticiones 7 y 8 la cadera no bajó del paralelo (≈ 6° por encima).", ObservationText.squatDepthAbove(listOf(6, 7), 6.2))
        assertEquals("El tronco se inclinó 9° más en la última repetición que en la primera.", ObservationText.trunkChange(9.0))
        assertEquals("La barra se desplazó 5 cm hacia delante durante la subida en la repetición 4.", ObservationText.barDrift(listOf(3), 5.0, true, true))
        assertEquals("Barbilla sobre la barra en ≈ 6 de 8 repeticiones.", ObservationText.pullChin(6, 8))
        assertEquals("la repetición 4", ObservationText.repetitions(listOf(3)))
        assertEquals("las repeticiones 7 y 8", ObservationText.repetitions(listOf(6, 7)))
        assertEquals("las repeticiones 3, 5 y 6", ObservationText.repetitions(listOf(2, 4, 5)))
        assertEquals("las repeticiones 3 a 6", ObservationText.repetitions(listOf(2, 3, 4, 5)))
        assertEquals("las repeticiones 1, 2 y 3", ObservationText.repetitions(listOf(0, 1, 2)))
        assertEquals("las repeticiones 1 a 4 y 7", ObservationText.repetitions(listOf(0, 1, 2, 3, 6)))
        assertEquals("0,3 s", ObservationText.seconds(300.0))
        assertEquals("12 cm", ObservationText.cm(12.3))
        assertEquals("4,2 cm", ObservationText.cm(4.24))
        assertTrue(ObservationText.barDrift(listOf(2, 3), 7.0, false, false).contains("≈ 7 % del recorrido hacia atrás"))
        assertTrue(ObservationText.barDrift(listOf(2, 3), 7.0, true, true).contains("hasta 7 cm"))
        assertEquals("La repetición 5 quedó más corta: el fondo 3,1 cm más arriba que en la mediana.", ObservationText.benchShorter(listOf(4), 3.1, true))
        assertTrue(ObservationText.benchShorter(listOf(3, 4), 3.1, true).startsWith("Las repeticiones 4 y 5 quedaron más cortas"))
    }

    @Test
    fun aHandMadeReportWithNothingToSayGivesNothing() {
        val profile = profile(AnalysisProfileId.SQUAT)
        val r = MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(5) { 1.0 }))
        val scene = MetricsTestSupport.scene(r, profile)!!
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, null, profile, null, null, emptySet()))
        assertTrue(Observations.evaluate(report.copy(reps = emptyList()), scene.run.prepared, profile, ScaleInfo.NONE).isEmpty())
        assertTrue(Observations.evaluate(report, scene.run.prepared, profile, ScaleInfo.NONE, AnalysisTuning.DEFAULT, scene.ctx).isEmpty())
        assertEquals(ProfileTier.A, report.tier)
    }
}
