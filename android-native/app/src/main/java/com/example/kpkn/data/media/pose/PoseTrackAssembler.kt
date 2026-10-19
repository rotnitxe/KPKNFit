package com.example.kpkn.data.media.pose

import com.example.kpkn.domain.biomechanics.kinematics.AthleteSelector
import com.example.kpkn.domain.biomechanics.kinematics.Vec2
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.pose.WorldSample
import com.example.kpkn.domain.biomechanics.signal.Resampler
import java.util.EnumMap

/**
 * Del `List<DetectedPose>` de cada fotograma a la [PoseTrackV2] de un vídeo (§3.1 y §4 punto 2).
 *
 * Por fotograma elige al atleta con [AthleteSelector]: la pose cuyo centro de torso queda más cerca del anterior y, durante el
 * primer segundo (aún sin atleta fijado), la de caja más grande. De la elegida guarda solo los [PoseLandmark.KEPT_IMAGE] y los
 * [PoseLandmark.KEPT_WORLD]; el resto se descarta. Un «world» con todos los puntos a cero (ML Kit no lo da) se guarda como
 * ausente, porque (0, 0, 0) falsearía la vista.
 *
 * Un solo hilo. [frameWidth]×[frameHeight] son los píxeles del fotograma girado que vio el estimador (el `DecodedFrame`) y
 * [rotationApplied] el giro que aplicó el decodificador (`VideoInfo.rotationDegrees`). [poseStride] es 2 cuando solo se
 * estimó uno de cada dos fotogramas; el que no se estimó simplemente no se añade. [fallbackFps] solo se usa si los tiempos
 * no dan cadencia (menos de dos fotogramas).
 */
class PoseTrackAssembler(
    private val mediaId: String,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val rotationApplied: Int,
    private val poseStride: Int = 1,
    private val captureFps: Float? = null,
    private val focalLengthPx: Float? = null,
    private val gravityRollDeg: Float? = null,
    private val gravityPitchDeg: Float? = null,
    private val fallbackFps: Float? = null,
) {
    init {
        require(frameWidth > 0 && frameHeight > 0) { "Fotograma de ${frameWidth}x$frameHeight" }
        require(rotationApplied in ROTATIONS) { "Giro no válido: $rotationApplied" }
        require(poseStride >= 1) { "poseStride < 1: $poseStride" }
    }

    private val frames = ArrayList<PoseFrameV2>()
    private var firstTUs = 0L
    private var lastTUs = Long.MIN_VALUE
    private var previousCenter: Vec2? = null
    private var multiFrames = 0
    private var posedFrames = 0

    /** Fotogramas ignorados por llegar con un tiempo que no crece (repetido o anterior al último). */
    var droppedFrames = 0
        private set

    /** Fotogramas añadidos (con o sin atleta). */
    val frameCount: Int get() = frames.size

    /** Fotogramas en los que se encontró al atleta. */
    val framesWithAthlete: Int get() = posedFrames

    /** Fracción de fotogramas en los que el modelo vio 2 poses o más (para la flag MULTI_PERSON). 0 si aún no hay ninguno. */
    val multiPersonFraction: Double get() = if (frames.isEmpty()) 0.0 else multiFrames.toDouble() / frames.size

    /** MULTI_PERSON: la 2.ª pose aparece en más del 10 % de los fotogramas. */
    val isMultiPerson: Boolean get() = multiPersonFraction > MULTI_PERSON_FRACTION

    /**
     * Añade el fotograma de tiempo [tUs] (µs, el PTS) con las [poses] que vio el estimador (puede ser vacía). Los tiempos han
     * de crecer estrictamente: un fotograma que no cumple se ignora y se cuenta en [droppedFrames].
     */
    fun add(tUs: Long, poses: List<DetectedPose>) {
        if (frames.isNotEmpty() && tUs <= lastTUs) {
            droppedFrames++
            return
        }
        if (frames.isEmpty()) firstTUs = tUs
        lastTUs = tUs
        if (poses.size >= 2) multiFrames++
        if (poses.isEmpty()) {
            frames.add(PoseFrameV2.of(tUs, 0, null, null))
            return
        }
        val candidates = ArrayList<Map<PoseLandmark, LandmarkSample>>(poses.size)
        for (pose in poses) candidates.add(imageMap(pose))
        // Hasta que pasa el primer segundo no hay atleta fijado: manda la caja más grande.
        val previous = if (tUs - firstTUs >= LOCK_IN_US) previousCenter else null
        val index = AthleteSelector.select(candidates, frameWidth, frameHeight, previous)
        val chosen = if (index >= 0) candidates[index] else null
        if (chosen == null || chosen.isEmpty()) {
            frames.add(PoseFrameV2.of(tUs, poses.size, null, null))
            return
        }
        AthleteSelector.torsoCenter(chosen, frameWidth, frameHeight)?.let { previousCenter = it }
        frames.add(PoseFrameV2.of(tUs, poses.size, chosen, worldMap(poses[index])))
        posedFrames++
    }

    /**
     * Reanuda desde los fotogramas de una traza parcial (el punto de control `.pose.part`): pasan a ser los primeros del
     * ensamblador, con el atleta y la fracción de varias personas como si se hubieran añadido uno a uno. Solo con el
     * ensamblador vacío; después se sigue con [add] a partir del PTS siguiente al último.
     */
    fun resumeFrom(previous: List<PoseFrameV2>) {
        check(frames.isEmpty()) { "Solo se reanuda un ensamblador vacío" }
        for (frame in previous) {
            if (frames.isNotEmpty() && frame.tUs <= lastTUs) {
                droppedFrames++
                continue
            }
            if (frames.isEmpty()) firstTUs = frame.tUs
            lastTUs = frame.tUs
            if (frame.posesDetected >= 2) multiFrames++
            if (frame.hasPose) {
                posedFrames++
                torsoCenterOf(frame)?.let { previousCenter = it }
            }
            frames.add(frame)
        }
    }

    /** El centro del torso de la pose guardada en [frame], con los mismos puntos y reglas que usa el selector. */
    private fun torsoCenterOf(frame: PoseFrameV2): Vec2? {
        val map = EnumMap<PoseLandmark, LandmarkSample>(PoseLandmark::class.java)
        for (landmark in TORSO) frame.point(landmark)?.let { map[landmark] = it }
        return AthleteSelector.torsoCenter(map, frameWidth, frameHeight)
    }

    /**
     * Cierra la traza con [engine] (el `PoseEstimator.engine` de AL FINAL: si la GPU falló a mitad, ya dice CPU).
     * `nominalFps` = 1/mediana(ΔPTS) de los fotogramas añadidos y `vfr` = P95/P5 de ΔPTS > 1,3. Se puede seguir añadiendo después.
     */
    fun build(engine: PoseEngineInfo): PoseTrackV2 {
        val times = LongArray(frames.size) { frames[it].tUs }
        val timebase = Resampler.timebase(times)
        val fps = if (timebase.fs > 0.0 && timebase.fs.isFinite()) timebase.fs.toFloat() else (fallbackFps ?: 0f)
        return PoseTrackV2(
            mediaId = mediaId,
            engine = engine,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            rotationApplied = rotationApplied,
            nominalFps = fps,
            poseStride = poseStride,
            vfr = timebase.vfr,
            captureFps = captureFps,
            focalLengthPx = focalLengthPx,
            gravityRollDeg = gravityRollDeg,
            gravityPitchDeg = gravityPitchDeg,
            aspectKnown = true,
            frames = frames.toList(),
        )
    }

    /** Los [PoseLandmark.KEPT_IMAGE] de [pose]; un punto que falta o con x o y NaN no entra. */
    private fun imageMap(pose: DetectedPose): Map<PoseLandmark, LandmarkSample> {
        val map = EnumMap<PoseLandmark, LandmarkSample>(PoseLandmark::class.java)
        for (landmark in PoseLandmark.KEPT_IMAGE) {
            val sample = pose.image.getOrNull(landmark.mp) ?: continue
            if (sample.x.isNaN() || sample.y.isNaN()) continue
            map[landmark] = sample
        }
        return map
    }

    /** Los [PoseLandmark.KEPT_WORLD] de [pose], o `null` si no hay «world» (vacío o todo a cero). */
    private fun worldMap(pose: DetectedPose): Map<PoseLandmark, WorldSample>? {
        if (pose.world.isEmpty()) return null
        val map = EnumMap<PoseLandmark, WorldSample>(PoseLandmark::class.java)
        var anyNonZero = false
        for (landmark in PoseLandmark.KEPT_WORLD) {
            val sample = pose.world.getOrNull(landmark.mp) ?: continue
            if (sample.x.isNaN() || sample.y.isNaN() || sample.z.isNaN()) continue
            if (sample.x != 0f || sample.y != 0f || sample.z != 0f) anyNonZero = true
            map[landmark] = sample
        }
        return if (anyNonZero) map else null
    }

    companion object {
        /** MULTI_PERSON si la 2.ª pose aparece en MÁS de esta fracción de los fotogramas. */
        const val MULTI_PERSON_FRACTION = 0.10

        /** Mientras el fotograma está a menos de 1 s del primero, el atleta es la pose de caja más grande. */
        const val LOCK_IN_US = 1_000_000L

        private val ROTATIONS = setOf(0, 90, 180, 270)

        private val TORSO = listOf(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
        )
    }
}
