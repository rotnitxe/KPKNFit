package com.example.kpkn.data.media.technique

import com.example.kpkn.data.media.pose.DetectedPose
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.RgbaFrame
import com.example.kpkn.data.media.video.DecodedFrame
import com.example.kpkn.data.media.video.FrameNeeds
import com.example.kpkn.data.media.video.FramePool
import com.example.kpkn.data.media.video.VideoDecodePath
import com.example.kpkn.data.media.video.VideoDecodeReport
import com.example.kpkn.data.media.video.VideoFrameSource
import com.example.kpkn.data.media.video.VideoInfo
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.WorldSample
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Lo común de las pruebas del pipeline (VT4c): un vídeo FALSO que entrega fotogramas con el mismo reparto de huecos que el pool real
 * (`FramePool.SLOTS`, por turnos) y un estimador de pose FALSO que devuelve las poses de una serie del `SyntheticLifter`.
 *
 * El fotograma lleva su PTS escrito en los 8 primeros bytes del búfer RGBA. El estimador lo lee y lo compara con el `timestampMs` que
 * le pasa el pipeline: si el pipeline procesara un hueco que el decodificador ya reutilizó (un fotograma de más de `SLOTS − 1` por
 * delante), el marcador no cuadraría y la prueba lo vería.
 */
internal object PipelineTestKit {
    /** Un archivo de vídeo cualquiera (los sidecars viven a su lado y el punto de control mira su tamaño y su fecha). */
    fun videoFile(dir: File, name: String = "clip.mp4"): File =
        File(dir, name).also { it.writeBytes(ByteArray(2048) { index -> index.toByte() }) }

    fun videoInfo(
        width: Int,
        height: Int,
        durationUs: Long,
        fps: Float = 30f,
        captureFps: Float? = null,
        hdr: Boolean = false,
    ) = VideoInfo(width, height, 0, width, height, durationUs, "video/avc", fps, captureFps, hdr)

    /** Una sentadilla sintética de lado, de [reps] repeticiones, en un fotograma de [width]×[height]. */
    fun squat(
        reps: Int = 5,
        width: Int = 406,
        height: Int = 720,
        noise: SynthNoise = SynthNoise(),
        lead: Double = 2.0,
        tail: Double = 2.0,
        eccS: Double = 1.5,
        p1S: Double = 0.5,
        conS: Double = 1.5,
        p2S: Double = 0.5,
        fps: Double = 30.0,
    ): SynthResult = SyntheticLifter.generate(
        SynthConfig(
            SynthMovement.SQUAT,
            camera = SynthCamera(widthPx = width, heightPx = height),
            fps = fps,
            script = repScript(reps, eccS, p1S, conS, p2S, lead = lead, tail = tail),
            noise = noise,
        ),
    )

    /** El ejercicio de la petición: propio, con el perfil elegido por el usuario y, si se da, el material (decide si hay disco). */
    fun exercise(profile: AnalysisProfileId? = AnalysisProfileId.SQUAT, equipmentId: String? = null, name: String = "Sentadilla") = ExerciseRef(
        definitionId = null,
        configurationId = null,
        movementPatternId = null,
        equipmentId = equipmentId,
        laterality = null,
        name = name,
        isCustom = true,
        userProfileOverride = profile,
    )

    fun request(
        video: File,
        mode: AnalysisMode = AnalysisMode.FULL,
        exercise: ExerciseRef = exercise(),
        plateSeed: PlateSeed? = null,
        plateDiameterMm: Int? = 450,
    ) = TechniqueAnalysisRequest(
        mediaId = video.nameWithoutExtension,
        videoFile = video,
        mode = mode,
        exercise = exercise,
        loadKg = null,
        plateDiameterMm = plateDiameterMm,
        plateSeed = plateSeed,
        userHeightCm = null,
        setHints = emptySet(),
    )

    fun deps(
        open: (File) -> VideoFrameSource,
        pose: () -> PoseEstimator,
        media: WorkoutMedia? = null,
        catalog: ExerciseCatalogV2? = null,
        gateIsClosed: () -> Boolean = { false },
        awaitGate: suspend () -> Unit = {},
        onStats: (PipelineRunStats) -> Unit = {},
        sdkInt: Int = 34,
    ) = PipelineDependencies(
        openVideo = open,
        createPose = pose,
        loadMedia = { media },
        loadCatalog = { catalog },
        recommendedPoseStride = { 1 },
        gateIsClosed = gateIsClosed,
        awaitGate = awaitGate,
        sdkInt = sdkInt,
        onRunStats = onStats,
    )

    /** Las poses que el estimador falso devuelve para un fotograma de la serie sintética. */
    fun posesOf(frame: PoseFrameV2): List<DetectedPose> {
        if (!frame.hasPose) return emptyList()
        val byMp = PoseLandmark.entries.associateBy { it.mp }
        val image = List(MP_POINTS) { mp ->
            byMp[mp]?.let { frame.point(it) } ?: LandmarkSample(Float.NaN, Float.NaN, 0f, 0f, 0f)
        }
        val world = List(MP_POINTS) { mp -> byMp[mp]?.let { frame.world(it) } ?: WorldSample(0f, 0f, 0f) }
        return List(frame.posesDetected.coerceAtLeast(1)) { DetectedPose(image, world) }
    }

    private const val MP_POINTS = 33
}

/**
 * Un vídeo falso: entrega los [ptsUs] a partir de `fromUs`, numerados desde 0, con los tipos de fotograma que pide `needs` y el
 * reparto por turnos de `FramePool.SLOTS` huecos. Se puede hacer fallar tras [failAfterDelivered] fotogramas entregados en una llamada.
 */
internal class FakeFrameSource(
    override val info: VideoInfo,
    private val ptsUs: List<Long>,
    override val path: VideoDecodePath = VideoDecodePath.GL,
    private val painter: ((ptsUs: Long, luma: ByteArray) -> Unit)? = null,
    private val failAfterDelivered: Int = -1,
    private val failure: Throwable? = null,
) : VideoFrameSource, VideoDecodeReport {
    override val fallbackReason: String? get() = if (path == VideoDecodePath.RETRIEVER) "prueba" else null
    override val details: String get() = "falsa"

    private val width = info.displayWidth
    private val height = info.displayHeight
    private val slots = FramePool.SLOTS
    private val rgba = Array(slots) { ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder()) }
    private val luma = Array(slots) { ByteArray(width * height) }

    /** El `fromUs` de cada llamada a `decode`. */
    val decodeStarts = CopyOnWriteArrayList<Long>()

    /** Fotogramas entregados entre todas las llamadas. */
    val delivered = AtomicInteger()

    @Volatile var decoding = false

    @Volatile var closedWhileDecoding = false

    @Volatile var closed = false

    override suspend fun decode(
        fromUs: Long,
        targetLongEdge: Int,
        needs: (index: Int, ptsUs: Long) -> FrameNeeds,
        onFrame: suspend (DecodedFrame) -> Unit,
    ) {
        decoding = true
        decodeStarts.add(fromUs)
        try {
            var index = 0
            var cursor = 0
            var deliveredHere = 0
            for (t in ptsUs) {
                if (t < fromUs) continue
                currentCoroutineContext().ensureActive()
                val need = needs(index, t)
                val frameIndex = index++
                if (need == FrameNeeds.SKIP) continue
                if (failAfterDelivered in 0..deliveredHere) throw checkNotNull(failure)
                val slot = cursor
                cursor = (cursor + 1) % slots
                val buffer = rgba[slot]
                buffer.putLong(0, t)
                val wantsRgba = need == FrameNeeds.RGBA || need == FrameNeeds.RGBA_AND_LUMA
                val wantsLuma = need == FrameNeeds.LUMA || need == FrameNeeds.RGBA_AND_LUMA
                if (wantsLuma) painter?.invoke(t, luma[slot])
                onFrame(DecodedFrame(frameIndex, t, width, height, if (wantsRgba) buffer else null, if (wantsLuma) luma[slot] else null))
                deliveredHere++
                delivered.incrementAndGet()
            }
        } finally {
            decoding = false
        }
    }

    override fun close() {
        if (decoding) closedWhileDecoding = true
        closed = true
    }
}

/**
 * Un estimador de pose falso: devuelve las poses de [frames] por el PTS que lleva escrito el fotograma. Anota los marcadores que no
 * cuadran con el `timestampMs` ([staleReads]: un hueco reutilizado antes de tiempo) y los tiempos que no crecen.
 */
internal class FakePoseEstimator(
    frames: List<PoseFrameV2>,
    private val failEvery: Int = 0,
    private val failAlways: Boolean = false,
    /** PTS del contenedor = tiempo real × esto (2 en una prueba de cámara lenta). */
    private val ptsScale: Long = 1L,
) : PoseEstimator {
    private val byTime = frames.associateBy { it.tUs * ptsScale }
    private var lastTimestampMs = Long.MIN_VALUE

    override val engine = PoseEngineInfo("fake", "full", "CPU")

    val calls = AtomicInteger()
    val staleReads = CopyOnWriteArrayList<String>()
    val notIncreasing = CopyOnWriteArrayList<String>()

    @Volatile var closed = false

    override fun estimate(frame: RgbaFrame, timestampMs: Long): List<DetectedPose> {
        check(!closed) { "El estimador está cerrado" }
        val n = calls.incrementAndGet()
        val marker = frame.buffer.getLong(0)
        if (marker / ptsScale / 1000L != timestampMs) staleReads.add("marcador $marker µs frente a $timestampMs ms")
        if (timestampMs <= lastTimestampMs) notIncreasing.add("$timestampMs tras $lastTimestampMs")
        lastTimestampMs = timestampMs
        if (failAlways || (failEvery > 0 && n % failEvery == 0)) throw IllegalStateException("la pose falló en la llamada $n")
        val source = byTime[marker] ?: return emptyList()
        return PipelineTestKit.posesOf(source)
    }

    override fun close() {
        closed = true
    }
}
