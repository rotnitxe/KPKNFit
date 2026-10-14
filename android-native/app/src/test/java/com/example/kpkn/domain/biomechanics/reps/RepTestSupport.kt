package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.NormalizedPoint
import com.example.kpkn.domain.biomechanics.PoseFrame
import com.example.kpkn.domain.biomechanics.PoseJoint
import com.example.kpkn.domain.biomechanics.PoseTrack
import com.example.kpkn.domain.biomechanics.TrajectoryFamily
import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Ayudas de las pruebas del conteo: de la traza sintética a la serie, el segmentador y la confianza. */
internal object RepTestSupport {
    /** Los 16 movimientos del generador con el perfil que los mide. */
    val PAIRS: List<Pair<SynthMovement, AnalysisProfileId>> = listOf(
        SynthMovement.SQUAT to AnalysisProfileId.SQUAT,
        SynthMovement.LUNGE to AnalysisProfileId.LUNGE,
        SynthMovement.DEADLIFT to AnalysisProfileId.DEADLIFT,
        SynthMovement.RDL to AnalysisProfileId.HINGE,
        SynthMovement.HIP_THRUST to AnalysisProfileId.HIP_THRUST,
        SynthMovement.BENCH to AnalysisProfileId.BENCH,
        SynthMovement.PUSH_UP to AnalysisProfileId.PUSH_UP,
        SynthMovement.OHP to AnalysisProfileId.OHP,
        SynthMovement.PULL_UP to AnalysisProfileId.PULL_VERTICAL,
        SynthMovement.LAT_PULLDOWN to AnalysisProfileId.PULL_VERTICAL,
        SynthMovement.ROW to AnalysisProfileId.ROW,
        SynthMovement.CURL to AnalysisProfileId.CURL,
        SynthMovement.TRICEPS_PUSHDOWN to AnalysisProfileId.TRICEPS_FLEXED_REST,
        SynthMovement.LATERAL_RAISE to AnalysisProfileId.RAISE,
        SynthMovement.LEG_EXTENSION to AnalysisProfileId.LEG_EXTENSION,
        SynthMovement.CALF_RAISE to AnalysisProfileId.CALF,
    )

    /** El perfil base, con el disco como señal principal si se pide (el resolver lo hace según el material). */
    fun profile(id: AnalysisProfileId, plate: Boolean = false): AnalysisProfile {
        val base = AnalysisProfiles.of(id)
        return if (plate && base.plateSignal != null) {
            base.copy(usesPlate = true, framing = base.framing.copy(needsPlate = true))
        } else {
            base
        }
    }

    /** Perfil de arranque E (flexiones) con una amplitud mínima baja, para las señales hechas a mano en BH. */
    fun eccentricProfile(minAmplitude: Double = 0.1): AnalysisProfile =
        AnalysisProfiles.of(AnalysisProfileId.PUSH_UP).copy(minAmplitude = minAmplitude)

    /** Perfil de arranque C (peso muerto) con una amplitud mínima baja. */
    fun concentricProfile(minAmplitude: Double = 0.1): AnalysisProfile =
        AnalysisProfiles.of(AnalysisProfileId.DEADLIFT).copy(minAmplitude = minAmplitude)

    class Run(
        val prepared: PreparedTrack,
        val series: CountSeries,
        val seg: SegmentationResult,
        val profile: AnalysisProfile,
        /** Los números con los que decidió el segmentador (vacío si no se pidió). */
        val trace: String = "",
    )

    /** Segmenta con la traza de depuración encendida y devuelve el resultado y la traza. */
    fun segmentTraced(
        series: CountSeries,
        profile: AnalysisProfile,
        hints: Set<String> = emptySet(),
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): Pair<SegmentationResult, String> {
        val lines = ArrayList<String>()
        RepSegmenter.trace = { lines.add(it) }
        try {
            return RepSegmenter.segment(series, profile, hints, tuning) to lines.joinToString("\n   ")
        } finally {
            RepSegmenter.trace = null
        }
    }

    /** Traza → preparación → serie → segmentación. `null` si no hay señal. */
    fun run(
        result: SynthResult,
        profile: AnalysisProfile,
        bar: BarTrack? = null,
        hints: Set<String> = emptySet(),
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): Run? = run(TrackPreparation.prepare(result.track, tuning), profile, bar, hints, tuning)

    fun run(
        prepared: PreparedTrack,
        profile: AnalysisProfile,
        bar: BarTrack? = null,
        hints: Set<String> = emptySet(),
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): Run? {
        val built = CountSeriesBuilder.build(prepared, bar, profile, tuning)
        if (built !is CountSeriesResult.Ok) return null
        val (seg, trace) = segmentTraced(built.series, profile, hints, tuning)
        return Run(prepared, built.series, seg, profile, trace)
    }

    /**
     * Un disco sintético que sigue una muñeca de la verdad (la traza limpia), en píxeles del fotograma de la pose y con
     * fuente HOUGH. Con [lagFrames] el disco llega tarde respecto de la pose.
     */
    fun plateFromWrist(
        result: SynthResult,
        wrist: PoseLandmark = PoseLandmark.LEFT_WRIST,
        lagFrames: Int = 0,
        radiusPx: Float = 60f,
        source: (index: Int) -> BarSampleSource = { BarSampleSource.HOUGH },
    ): BarTrack {
        val track = result.cleanTrack
        val w = track.frameWidth.toFloat()
        val h = track.frameHeight.toFloat()
        val frames = track.frames
        val samples = ArrayList<BarSample>(frames.size)
        for (i in frames.indices) {
            val k = (i - lagFrames).coerceIn(0, frames.size - 1)
            val f = frames[k]
            samples.add(
                BarSample(
                    tUs = frames[i].tUs,
                    cx = f.x(wrist) * w,
                    cy = f.y(wrist) * h,
                    r = radiusPx,
                    axisRatio = 1f,
                    quality = 0.9f,
                    source = source(i),
                ),
            )
        }
        return BarTrack(
            mediaId = track.mediaId,
            frameWidth = track.frameWidth,
            frameHeight = track.frameHeight,
            plateDiameterMm = null,
            seed = null,
            samples = samples,
        )
    }

    /** Cuántas repeticiones de la verdad hay de cada clase: completas, parciales y fallidas. */
    fun truthCounts(result: SynthResult): Triple<Int, Int, Int> {
        val full = result.reps.count { it.kind == SynthRepKind.FULL }
        val partial = result.reps.count { it.kind == SynthRepKind.PARTIAL_FAR || it.kind == SynthRepKind.PARTIAL_REST }
        val failed = result.reps.count { it.kind == SynthRepKind.FAILED }
        return Triple(full, partial, failed)
    }

    fun counts(seg: SegmentationResult): Triple<Int, Int, Int> = Triple(seg.fullCount, seg.partialCount, seg.failedCount)

    /** La traza v1 (ML Kit a ~10 Hz, 13 puntos) que daría la misma serie: un fotograma de cada `step`. */
    fun legacyV1(result: SynthResult, step: Int = 3, videoWidth: Int = 720, videoHeight: Int = 1280): PoseTrack {
        val joints = mapOf(
            PoseJoint.NOSE to PoseLandmark.NOSE,
            PoseJoint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER, PoseJoint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseJoint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW, PoseJoint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
            PoseJoint.LEFT_WRIST to PoseLandmark.LEFT_WRIST, PoseJoint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
            PoseJoint.LEFT_HIP to PoseLandmark.LEFT_HIP, PoseJoint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
            PoseJoint.LEFT_KNEE to PoseLandmark.LEFT_KNEE, PoseJoint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
            PoseJoint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE, PoseJoint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
        )
        val frames = ArrayList<PoseFrame>()
        for (i in result.track.frames.indices step step) {
            val f = result.track.frames[i]
            val points = HashMap<PoseJoint, NormalizedPoint>()
            if (f.hasPose) {
                for ((joint, landmark) in joints) {
                    val x = f.x(landmark)
                    val y = f.y(landmark)
                    if (x.isNaN() || y.isNaN()) continue
                    points[joint] = NormalizedPoint(x, y, f.vis(landmark).coerceIn(0f, 1f))
                }
            }
            frames.add(PoseFrame(timestampMs = (f.tUs / 1000.0).roundToLong(), points = points))
        }
        val fps = (result.track.nominalFps / step).roundToInt()
        return PoseTrack(
            mediaId = "legacy-v1",
            family = TrajectoryFamily.SQUAT,
            sampleFps = fps,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            frames = frames,
        )
    }

    fun summary(seg: SegmentationResult): String {
        val kinds = seg.cycles.joinToString(",") {
            when (it.kind) {
                RepKind.FULL -> "F"
                RepKind.PARTIAL -> "P" + (if (it.partialKind?.name == "FAR_END") "f" else "r")
                RepKind.FAILED -> "X"
            }
        }
        val dropped = seg.discarded.joinToString(",") { "${it.peak}:${it.reason}" }
        return String.format(
            Locale.ROOT,
            "full=%d partial=%d failed=%d minis=%d mode=%s T0=%s sigma=%.4f aFull=%.4f flip=%s nAmb=%d why=%s [%s] descartados=[%s]",
            seg.fullCount, seg.partialCount, seg.failedCount, seg.miniSets, seg.sideMode,
            seg.periodS?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "-",
            seg.noiseSigma, seg.aFull, seg.polarityFlipped, seg.ambiguousCycles, seg.noRepsReason, kinds, dropped,
        )
    }

    /** Recoge los fallos de una prueba para darlos todos juntos al final (una corrida revela cuantos casos fallan). */
    class Failures {
        private val lines = ArrayList<String>()

        fun add(message: String) {
            lines.add(message)
        }

        /** Anota [message] si [ok] es falso. */
        fun check(ok: Boolean, message: () -> String) {
            if (!ok) lines.add(message())
        }

        fun assertNone() {
            if (lines.isNotEmpty()) throw AssertionError("${lines.size} fallo(s):\n" + lines.joinToString("\n"))
        }
    }
}
