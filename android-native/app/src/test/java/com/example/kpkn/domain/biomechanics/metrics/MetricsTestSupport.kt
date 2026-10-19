package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.reps.PhaseSummary
import com.example.kpkn.domain.biomechanics.reps.RepPhases
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.testing.Anthro
import com.example.kpkn.domain.biomechanics.testing.Joints
import com.example.kpkn.domain.biomechanics.testing.MotionState
import com.example.kpkn.domain.biomechanics.testing.Posture
import com.example.kpkn.domain.biomechanics.testing.SynthBody
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthMovements
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthRepTruth
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin

/** Ayudas de las pruebas de las métricas: de la traza sintética al contexto de métricas, y la verdad geométrica del generador. */
internal object MetricsTestSupport {
    /** Altura del atleta de las pruebas: la del generador por defecto. */
    const val HEIGHT_M = 1.75

    private val anthro = Anthro(HEIGHT_M)

    fun gen(
        movement: SynthMovement,
        script: List<SynthSegment>,
        camera: SynthCamera = SynthCamera(),
        noise: SynthNoise = SynthNoise.NONE,
        fps: Double = 30.0,
        seed: Long = 42L,
    ): SynthResult = SyntheticLifter.generate(SynthConfig(movement, camera = camera, fps = fps, script = script, noise = noise, seed = seed))

    /** Reposo, una repetición por cada amplitud (con las fases dadas) y reposo. */
    fun script(
        amplitudes: List<Double>,
        eccS: Double = 2.0,
        p1S: Double = 0.5,
        conS: Double = 1.5,
        p2S: Double = 0.5,
        lead: Double = 2.0,
        tail: Double = 2.0,
    ): List<SynthSegment> =
        listOf<SynthSegment>(SynthSegment.Idle(lead)) +
            amplitudes.map { SynthSegment.Rep(eccS, p1S, conS, p2S, it) } +
            listOf<SynthSegment>(SynthSegment.Idle(tail))

    /** La misma verdad con otra traza (por ejemplo, con los puntos de la mano movidos). */
    fun withTrack(result: SynthResult, track: PoseTrackV2): SynthResult = SynthResult(
        track = track,
        reps = result.reps,
        primaryAngleDeg = result.primaryAngleDeg,
        cleanTrack = result.cleanTrack,
        swapGlitchFrames = result.swapGlitchFrames,
        droppedFrames = result.droppedFrames,
        bodyHeightFh = result.bodyHeightFh,
        spans = result.spans,
    )

    /** Lo que sale de procesar una traza: el conteo (con su traza de depuración), las fases y el contexto de las métricas. */
    class Scene(val result: SynthResult, val run: RepTestSupport.Run, val phases: PhaseSummary, val ctx: MetricContext)

    /** Traza → preparación → serie → segmentación → fases → contexto. `null` si no hay señal. */
    fun scene(
        result: SynthResult,
        profile: AnalysisProfile,
        bar: BarTrack? = null,
        hints: Set<String> = emptySet(),
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): Scene? {
        val run = RepTestSupport.run(result, profile, bar, hints, tuning) ?: return null
        val phases = RepPhases.compute(run.series, run.seg, run.profile, tuning)
        return Scene(result, run, phases, MetricContext(run.prepared, run.series, run.seg, phases, run.profile, tuning))
    }

    /** Resumen del conteo para los mensajes de fallo. */
    fun describe(scene: Scene?): String = if (scene == null) "sin señal" else RepTestSupport.summary(scene.run.seg)

    // ------------------------------------------------------------------------------------------------------------
    // Verdad geométrica del generador (3D, sin cámara): para escoger amplitudes y para saber qué debe salir
    // ------------------------------------------------------------------------------------------------------------

    fun posture(movement: SynthMovement, b: Double): Posture =
        SynthMovements.posture(movement, MotionState(b, b), anthro, Side.LEFT)

    fun joints(movement: SynthMovement, b: Double): Joints = SynthBody.joints(posture(movement, b), anthro)

    /** Ángulo principal del movimiento (flexión, °) con el progreso [b]. */
    fun primaryAngleDeg(movement: SynthMovement, b: Double): Double =
        SynthMovements.primaryAngleDeg(movement, joints(movement, b), Side.LEFT, anthro)

    /** Inclinación del tronco τ (°, + hacia delante) con el progreso [b]. */
    fun trunkLeanDeg(movement: SynthMovement, b: Double): Double {
        val post = posture(movement, b)
        return Math.toDegrees(SynthBody.trunkAngle(post.hip, post.shoulder))
    }

    /** Sentadilla: α (°) con el progreso [b]; > 0 si la cadera queda por debajo de la rodilla. */
    fun squatDepthDeg(b: Double): Double {
        val j = joints(SynthMovement.SQUAT, b)
        val hip = j[PoseLandmark.LEFT_HIP]
        val knee = j[PoseLandmark.LEFT_KNEE]
        return Math.toDegrees(asin(((knee.y - hip.y) / (hip - knee).norm()).coerceIn(-1.0, 1.0)))
    }

    /** Ángulo incluido en la cadera θ(S,H,K) (°) del lado izquierdo con el progreso [b]. */
    fun hipAngleDeg(movement: SynthMovement, b: Double): Double {
        val j = joints(movement, b)
        return SynthMovements.includedDeg(j[PoseLandmark.LEFT_SHOULDER], j[PoseLandmark.LEFT_HIP], j[PoseLandmark.LEFT_KNEE])
    }

    /** Ángulo incluido en el codo θ(S,E,W) (°) del lado izquierdo con el progreso [b]. */
    fun elbowAngleDeg(movement: SynthMovement, b: Double): Double {
        val j = joints(movement, b)
        return SynthMovements.includedDeg(j[PoseLandmark.LEFT_SHOULDER], j[PoseLandmark.LEFT_ELBOW], j[PoseLandmark.LEFT_WRIST])
    }

    /** El [b] en [lo, hi] con f(b) = [target], por bisección; f debe crecer con b. */
    fun solve(lo: Double, hi: Double, target: Double, f: (Double) -> Double): Double {
        var a = lo
        var c = hi
        repeat(60) {
            val mid = 0.5 * (a + c)
            if (f(mid) < target) a = mid else c = mid
        }
        return 0.5 * (a + c)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Verdad sobre la traza limpia
    // ------------------------------------------------------------------------------------------------------------

    /** Índice del fotograma más cercano a [tUs] (sin fluctuación del PTS: el fotograma k está en k/fps). */
    fun frameAt(track: PoseTrackV2, tUs: Long): Int {
        var best = 0
        var bestGap = Long.MAX_VALUE
        for ((i, f) in track.frames.withIndex()) {
            val gap = kotlin.math.abs(f.tUs - tUs)
            if (gap < bestGap) {
                bestGap = gap
                best = i
            }
        }
        return best
    }

    /** Y (FH) del centro de la mano izquierda HC = ½·muñeca + ¼·(índice + meñique) en el instante [tUs]. */
    fun handCenterY(track: PoseTrackV2, tUs: Long): Double {
        val f = track.frames[frameAt(track, tUs)]
        return 0.5 * f.y(PoseLandmark.LEFT_WRIST) + 0.25 * (f.y(PoseLandmark.LEFT_INDEX) + f.y(PoseLandmark.LEFT_PINKY))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Disco sintético con defectos inyectados
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Desplaza horizontalmente el disco durante la repetición [rep] en proporción a su progreso vertical: 0 en el punto más bajo
     * (la imagen crece hacia abajo, así que el de mayor cy) y [shiftPx] en el más alto. La deriva queda atada a la altura de la
     * barra y no al reloj, así que no depende de dónde sitúen las fases el inicio y el fin de la concéntrica.
     */
    fun withLateralDrift(bar: BarTrack, rep: SynthRepTruth, shiftPx: Float): BarTrack {
        val inRep = bar.samples.filter { it.tUs in rep.departUs..rep.arriveUs }
        val lowest = inRep.maxOf { it.cy }
        val highest = inRep.minOf { it.cy }
        val range = lowest - highest
        val samples = bar.samples.map { s ->
            if (s.tUs in rep.departUs..rep.arriveUs && range > 0f) s.copy(cx = s.cx + shiftPx * (lowest - s.cy) / range) else s
        }
        return bar.copy(samples = samples)
    }

    /**
     * Combadura lateral del disco durante la repetición [rep]: [amplitudePx]·sin(π·p) con p el progreso vertical (0 arriba, 1 en
     * el fondo). Es una trayectoria distinta de la de las demás repeticiones, tanto en la bajada como en la subida.
     */
    fun withPathBump(bar: BarTrack, rep: SynthRepTruth, amplitudePx: Float): BarTrack {
        val inRep = bar.samples.filter { it.tUs in rep.departUs..rep.arriveUs }
        val highest = inRep.minOf { it.cy }
        val lowest = inRep.maxOf { it.cy }
        val range = lowest - highest
        val samples = bar.samples.map { s: BarSample ->
            if (s.tUs in rep.departUs..rep.arriveUs && range > 0f) {
                val p = (s.cy - highest) / range
                s.copy(cx = s.cx + amplitudePx * sin(PI * p).toFloat())
            } else {
                s
            }
        }
        return bar.copy(samples = samples)
    }
}
