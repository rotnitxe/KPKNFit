package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import android.util.Log
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.media.pose.DetectedPose
import com.example.kpkn.data.media.pose.GpuProbe
import com.example.kpkn.data.media.pose.MediaPipePoseEstimators
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.PoseEstimatorReport
import com.example.kpkn.data.media.pose.PoseModel
import com.example.kpkn.data.media.pose.PoseTrackAssembler
import com.example.kpkn.data.media.pose.RgbaFrame
import com.example.kpkn.data.media.video.DecodedFrame
import com.example.kpkn.data.media.video.FrameNeeds
import com.example.kpkn.data.media.video.VideoDecodeException
import com.example.kpkn.data.media.video.VideoDecodePath
import com.example.kpkn.data.media.video.VideoDecodeReport
import com.example.kpkn.data.media.video.VideoFrameMath
import com.example.kpkn.data.media.video.VideoFrameSource
import com.example.kpkn.data.media.video.VideoFrameSources
import com.example.kpkn.data.media.video.VideoInfo
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.work.AnalysisGate
import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy
import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.kinematics.BodyScaleEstimator
import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.domain.biomechanics.vision.LumaFrame
import com.example.kpkn.domain.biomechanics.vision.PlateDetector
import com.example.kpkn.domain.biomechanics.vision.PlateTracker
import com.example.kpkn.domain.biomechanics.vision.PlateTrackerConfig
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Los umbrales y las cadencias del pipeline (§9 puntos 2–4 y §8 punto 3). Valores de la especificación; VT10 los ajusta aquí.
 *
 * - **Decodificación:** [targetLongEdge] px del lado largo; pose en todos los fotogramas si la cadencia no pasa de [maxPoseFps] y,
 *   si no, en uno de cada dos. [channelCapacity] es el `Channel` entre decodificación y pose: exige `FramePool.SLOTS ≥ capacidad + 2`.
 * - **Cadencias:** punto de control cada [checkpointEveryFrames] fotogramas, pausa de cámara cada [gateEveryFrames].
 * - **Pose:** más de [maxPoseErrors] fotogramas con error de pose tiran el análisis.
 * - **Disco:** adquisición con las muñecas casi quietas ([stillMaxFh] de alto de fotograma de recorrido durante [stillWindowUs]),
 *   un intento cada [attemptCooldownUs] como mucho, hasta [maxAttempts] intentos o [acquireDeadlineUs] desde el primer fotograma;
 *   la propuesta debe cubrir [minSeedCoverage] de su contorno. Hacia atrás se rastrean hasta [ringUs] de luma (con un tope de
 *   [ringMaxBytes]). [defaultUserHeightM] da la escala del cuerpo si no se conoce la estatura.
 */
internal data class PipelineTuning(
    val targetLongEdge: Int = 720,
    val maxPoseFps: Float = 35f,
    val channelCapacity: Int = 2,
    val checkpointEveryFrames: Int = 150,
    val gateEveryFrames: Int = 30,
    val maxPoseErrors: Int = 20,
    val stillWindowUs: Long = 500_000L,
    val stillMaxFh: Double = 0.015,
    val attemptCooldownUs: Long = 1_000_000L,
    val acquireDeadlineUs: Long = 30_000_000L,
    val maxAttempts: Int = 24,
    val minSeedCoverage: Float = 0.4f,
    val ringUs: Long = 3_000_000L,
    val ringMaxBytes: Long = 48L * 1024L * 1024L,
    val defaultUserHeightM: Double = 1.75,
    val wristMinVisibility: Float = 0.5f,
)

/**
 * Lo que midió una pasada del pipeline, para el banco de depuración y para ajustar (VT10). Nada de esto sale del móvil.
 *
 * Los tiempos son de reloj, en ms: [decodeWaitMs] es lo que el consumidor esperó fotogramas (si es grande, decodificar es el cuello
 * de botella), [poseMs] lo que tardó el estimador (bloqueante), [plateMs] el disco y [engineMs] el motor.
 */
internal data class PipelineRunStats(
    val mode: AnalysisMode,
    val framesDecoded: Int,
    val framesPosed: Int,
    val poseStride: Int,
    val decodePath: VideoDecodePath?,
    val decodeDetails: String,
    val fallbackReason: String?,
    val poseDelegate: String?,
    val delegateNote: String?,
    val usesPlate: Boolean,
    val plateStage: String,
    val autoSeed: PlateSeed?,
    val barSamples: Int,
    val plateAttempts: Int,
    val resumedFromPtsUs: Long?,
    val wallMs: Long,
    val decodeWaitMs: Double,
    val poseMs: Double,
    val plateMs: Double,
    val engineMs: Long,
    /** First failing acquisition guard, counted only when a new pose reaches pending(). Not elapsed time. */
    val plateWaitingCounts: Map<String, Int> = emptyMap(),
    val plateLastWaitingReason: String? = null,
    val plateSeedState: String = "none",
)

/**
 * Todo lo externo que toca el pipeline, para poder probarlo con fuentes de vídeo y estimadores falsos. [forApp] arma el de la app:
 * la decodificación de VT4a, MediaPipe en modo VIDEO con el modelo full (GPU si la sonda lo deja), el medio de Room y el catálogo v2.
 *
 * @property gateIsClosed `true` si hay que parar (la cámara graba o el móvil está demasiado caliente); entonces [awaitGate] espera.
 * @property onRunStats recibe las cifras de cada pasada al terminar (también si falla). No debe lanzar.
 */
internal data class PipelineDependencies(
    val openVideo: (File) -> VideoFrameSource,
    val createPose: () -> PoseEstimator,
    val loadMedia: suspend (mediaId: String) -> WorkoutMedia?,
    val loadCatalog: suspend () -> ExerciseCatalogV2?,
    val recommendedPoseStride: () -> Int,
    val gateIsClosed: () -> Boolean,
    val awaitGate: suspend () -> Unit,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val onRunStats: (PipelineRunStats) -> Unit = {},
) {
    companion object {
        fun forApp(context: Context): PipelineDependencies {
            val app = context.applicationContext
            return PipelineDependencies(
                openVideo = { file -> VideoFrameSources.open(file) },
                createPose = {
                    MediaPipePoseEstimators.create(app, RunningMode.VIDEO, PoseModel.Full, preferGpu = true, numPoses = 2)
                },
                loadMedia = { mediaId -> VideoAnalysisRepository.init(app).videoOf(mediaId) },
                loadCatalog = {
                    try {
                        CatalogV2ProcessCache.getOrLoad(app).catalog
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("KpknTechnique", "No se pudo cargar el catálogo de ejercicios.", error)
                        null
                    }
                },
                recommendedPoseStride = {
                    try {
                        GpuProbe.get(app).recommendedPoseStride
                    } catch (error: RuntimeException) {
                        1
                    }
                },
                gateIsClosed = { AnalysisGate.cameraBusy.value || AnalysisGate.isTooHot(app) },
                awaitGate = { AnalysisGate.awaitClear(app) },
            )
        }
    }
}

/**
 * El analizador real (§9 punto 4): del vídeo de una serie a su informe, en UNA pasada.
 *
 * **FULL.** Decodifica el vídeo (`VideoFrameSources.open`: GL y, si falla, el respaldo del retriever), estima la pose de cada
 * fotograma (o de uno de cada dos con más de 35 fps, o si el móvil va justo) y, si el perfil usa el disco, lo busca y lo sigue sobre
 * la luma. Decodificación y pose van solapadas por un `Channel(2)`; el pool de VT4a tiene 4 huecos porque llegan a estar vivos
 * 2 + 2 fotogramas. Escribe `<id>.pose2.json.gz` y `<id>.bar.json.gz`, mide con `TechniqueAnalysisEngine` y devuelve
 * [Done][TechniqueAnalysisOutcome.Done]. Sobre la marcha:
 * - cada 150 fotogramas deja un punto de control (`<id>.pose.part`); un intento posterior sobre el mismo vídeo sigue desde ahí;
 * - cada 30 fotogramas espera si la cámara graba o el móvil está caliente ([AnalysisGate]);
 * - el progreso va de 0 a 95 con la decodificación (por PTS sobre la duración) y de 95 a 100 con el motor.
 *
 * **Disco (solo con un perfil que lo usa).** Con la semilla que fijó el usuario, el seguimiento arranca en su instante. Si no la hay,
 * en el primer tramo con las muñecas quietas (≥ 0,5 s) `PlateDetector.propose` busca cerca de ellas, con el radio previo de la
 * escala corporal (§8 punto 3), y la mejor propuesta es la semilla automática (`userConfirmed = false`). Desde ahí un
 * `PlateTracker` sigue hacia delante; los fotogramas anteriores, guardados en un búfer circular de hasta 3 s de luma, se siguen
 * hacia atrás con otro. Lo que quede más atrás no tiene disco. `BarTrack.plateDiameterMm` es `null` hasta que el usuario confirma
 * el disco. El disco es un complemento: si falla, el análisis sigue con la pose.
 *
 * **RECOMPUTE** vuelve a medir con los sidecars, sin abrir el vídeo. **PLATE_ONLY** decodifica solo luma desde la semilla confirmada
 * (3 s antes, para seguir hacia atrás; y hasta el final, hacia delante), reescribe el sidecar del disco con el diámetro confirmado y
 * mide de nuevo. Los dos vuelven a un FULL si falta el sidecar de pose (o el del disco, con un perfil que lo usa): es la única forma
 * de dar un resultado y solo pasa tras perder archivos. Los avisos que solo sabe quien decodificó (DECODE_FALLBACK, HDR_SOURCE) los
 * heredan del informe anterior.
 *
 * **Tiempo.** Todo va en tiempo REAL: en la cámara lenta de galería (captura ≫ cadencia de reproducción) el PTS del contenedor se
 * reescala, como pide `TrackPreparation`.
 *
 * **Errores.** Nunca lanza, salvo la cancelación. Una excepción transitoria (`CodecException` transitoria, E/S) da
 * `Failed(código, transient = true)` y deja el punto de control para el reintento; cualquier otra da `Failed(código, false)` y lo
 * borra. Códigos: [ERROR_VIDEO_UNREADABLE], [ERROR_DECODE], [ERROR_NO_FRAMES], [ERROR_POSE_UNAVAILABLE], [ERROR_POSE_FAILED],
 * [ERROR_FRAME_SIZE], [ERROR_PLATE_SEED_MISSING], [ERROR_SIDECAR_WRITE], [ERROR_IO], [ERROR_OUT_OF_MEMORY], [ERROR_ENGINE] y
 * [ERROR_UNEXPECTED].
 *
 * **Cierre.** La fuente de vídeo se cierra con `use` alrededor de la decodificación entera, y NUNCA con `decode` en marcha: antes se
 * cancela el productor y se espera a que termine. Los fotogramas del pool solo valen dentro de su `onFrame`/`consume`; lo que debe
 * vivir más (el búfer circular del disco) se COPIA. La fuente y el estimador se crean sin posibilidad de cancelación a medias
 * (`NonCancellable`) y se cierran siempre: una cancelación justo al crearlos no deja un códec ni un hilo vivo.
 */
class TechniqueAnalysisPipeline internal constructor(
    private val deps: PipelineDependencies,
    private val tuning: PipelineTuning,
) : TechniqueAnalyzer {

    /** El analizador de la app: MediaPipe en VIDEO con el modelo full. */
    constructor(context: Context) : this(PipelineDependencies.forApp(context.applicationContext), PipelineTuning())

    @Volatile
    private var resolver: ExerciseRefResolver? = null

    override suspend fun analyze(
        request: TechniqueAnalysisRequest,
        onProgress: suspend (percent: Int) -> Unit,
    ): TechniqueAnalysisOutcome {
        val run = Run(request, onProgress)
        return try {
            run.execute()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            run.failure(error)
        } finally {
            run.reportStats()
        }
    }

    /** Un análisis. Un objeto por llamada: guarda su estado y sus cifras. */
    private inner class Run(
        val request: TechniqueAnalysisRequest,
        val onProgress: suspend (percent: Int) -> Unit,
    ) {
        private val startedNs = System.nanoTime()
        private val video: File = request.videoFile
        private var lastPercent = -1
        private lateinit var profile: AnalysisProfile
        private var pass: FullPass? = null
        private var plateRunOutcome: PlateOnlyOutcome? = null
        private var engineMs = 0L
        private lateinit var completedExercise: ExerciseRef

        suspend fun execute(): TechniqueAnalysisOutcome {
            val ref = withContext(Dispatchers.IO) { completeExercise() }
            completedExercise = ref
            when (val resolution = AnalysisProfileResolver.resolve(ref)) {
                is ProfileResolution.Unsupported -> return TechniqueAnalysisOutcome.Unsupported(resolution.reason)
                is ProfileResolution.NeedsUserChoice -> return TechniqueAnalysisOutcome.NeedsProfile(resolution.guess)
                is ProfileResolution.Supported -> profile = resolution.profile
            }
            return when (request.mode) {
                AnalysisMode.FULL -> full()
                AnalysisMode.RECOMPUTE -> recompute()
                AnalysisMode.PLATE_ONLY -> plateOnly()
            }
        }

        /** El ejercicio completo: el de la petición más lo que dan el medio y el catálogo. */
        private suspend fun completeExercise(): ExerciseRef {
            val media = try {
                deps.loadMedia(request.mediaId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Sin el medio (la base ocupada) se sigue con el ejercicio parcial de la petición: es lo que el Worker ya sabía.
                Log.w(TAG, "No se pudo leer el medio ${request.mediaId}.", error)
                null
            }
            var exerciseResolver = resolver
            if (exerciseResolver == null) {
                exerciseResolver = ExerciseRefResolver(deps.loadCatalog())
                // Sin catálogo (no cargó) no se guarda: el siguiente análisis lo intenta otra vez.
                if (exerciseResolver.hasCatalog) resolver = exerciseResolver
            }
            return exerciseResolver.resolve(request.exercise, media, TechniqueSidecars.captureFile(video))
        }

        // ---------------------------------------------------------------------------------------------------------------
        // FULL
        // ---------------------------------------------------------------------------------------------------------------

        private suspend fun full(): TechniqueAnalysisOutcome {
            val meta = withContext(Dispatchers.IO) { CaptureMetadataFile.read(TechniqueSidecars.captureFile(video)) }
            val source = openVideo()
            return source.use { src ->
                currentCoroutineContext().ensureActive()
                runFull(src, meta)
            }
        }

        /** Abre el vídeo sin cancelación a medias: si se cancela justo ahora, la fuente se entrega igual y quien la reciba la cierra. */
        private suspend fun openVideo(): VideoFrameSource = try {
            withContext(Dispatchers.IO + NonCancellable) { deps.openVideo(video) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw PipelineFailure(ERROR_VIDEO_UNREADABLE, isTransient(error), error)
        }

        private suspend fun runFull(src: VideoFrameSource, meta: CaptureMetadata): TechniqueAnalysisOutcome {
            val info = src.info
            val clock = timeOf(info)
            val out = VideoFrameMath.outputSize(info.displayWidth, info.displayHeight, tuning.targetLongEdge)
            val partFile = TechniqueSidecars.partFile(video)

            // Un punto de control de un intento anterior, si sirve para ESTE análisis; si no, fuera.
            val checkpoint = withContext(Dispatchers.IO) {
                val found = AnalysisCheckpoints.read(partFile)
                val usable = found != null && AnalysisCheckpoints.isUsable(
                    found, request.mediaId, video.length(), video.lastModified(), POSE_VERSION, profile.usesPlate,
                    out.width, out.height, clock.scale, AnalysisCheckpoint.seedKey(request.plateSeed),
                )
                if (!usable) partFile.delete()
                found.takeIf { usable }
            }
            val stride = checkpoint?.header?.poseStride
                ?: if (clock.realFps > tuning.maxPoseFps || deps.recommendedPoseStride() >= 2) 2 else 1
            val focal = meta.focalLengthPx
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { it * out.width / info.displayWidth.toFloat() }

            // El estimador se crea sin cancelación a medias y se cierra siempre.
            val estimator = try {
                withContext(Dispatchers.Default + NonCancellable) { deps.createPose() }
            } catch (error: Throwable) {
                if (error is OutOfMemoryError) throw error
                throw PipelineFailure(ERROR_POSE_UNAVAILABLE, false, error)
            }
            val fullPass = try {
                FullPass(src, estimator, info, meta, out, stride, clock, focal, checkpoint, partFile)
            } catch (error: Throwable) {
                estimator.close()
                throw error
            }
            pass = fullPass
            val track = try {
                currentCoroutineContext().ensureActive()
                fullPass.execute()
                fullPass.finish()
            } finally {
                estimator.close()
            }

            // Sidecars: la pose y el disco quedan en disco ANTES de medir, así un fallo del motor no pierde el trabajo.
            val bar = fullPass.barTrack()
            val (storedPose, storedBar) = withContext(Dispatchers.IO) {
                val pose = storePose(track)
                val stored = storeBar(bar)
                partFile.delete()
                pose to stored
            }
            val extra = buildSet {
                if (fullPass.decodePath == VideoDecodePath.RETRIEVER) add(AnalysisFlag.DECODE_FALLBACK)
                if (info.isHdr && deps.sdkInt < HDR_TONE_MAP_SDK) add(AnalysisFlag.HDR_SOURCE)
            }
            val engine = storedPose.engine
            return measure(
                pose = storedPose,
                bar = storedBar,
                extraFlags = extra,
                exercise = completedExercise,
                poseEngine = "${engine.id}/${engine.model}/${engine.delegate}",
                poseVersion = POSE_VERSION,
            )
        }

        // ---------------------------------------------------------------------------------------------------------------
        // RECOMPUTE
        // ---------------------------------------------------------------------------------------------------------------

        private suspend fun recompute(): TechniqueAnalysisOutcome {
            val sidecars = withContext(Dispatchers.IO) { readSidecars() } ?: return full()
            val selected = request.plateSeed
            if (profile.usesPlate && selected != null && !PlatePublicationPolicy.matchesSelection(sidecars.bar?.seed, selected)) {
                return plateOnly() // A changed selection cannot confer identity on the old samples.
            }
            val adopted = sidecars.bar?.let { adoptConfirmedDisc(it) }
            val bar = if (adopted != sidecars.bar) withContext(Dispatchers.IO) { storeBar(adopted) } else adopted
            val carried = withContext(Dispatchers.IO) { carriedFlags() }
            return measure(sidecars.pose, bar, carried, exercise = completedExercise)
        }

        // ---------------------------------------------------------------------------------------------------------------
        // PLATE_ONLY
        // ---------------------------------------------------------------------------------------------------------------

        private suspend fun plateOnly(): TechniqueAnalysisOutcome {
            // Un perfil que no sigue el disco no tiene nada que seguir: se mide con lo que hay.
            if (!profile.usesPlate) return recompute()
            val seed = request.plateSeed ?: throw PipelineFailure(ERROR_PLATE_SEED_MISSING, false, null)
            val stored = withContext(Dispatchers.IO) {
                val file = TechniqueSidecars.poseFile(video)
                if (file.isFile) PoseTrackCodec.decode(file.readBytes())?.takeIf { it.frames.size >= 2 } else null
            } ?: return full()
            val source = openVideo()
            // Si la pose se hizo con otro tamaño de fotograma sus píxeles no valen para el disco: se sale (cerrando la fuente) a un FULL.
            val outcome = source.use { src ->
                currentCoroutineContext().ensureActive()
                val out = VideoFrameMath.outputSize(src.info.displayWidth, src.info.displayHeight, tuning.targetLongEdge)
                if (out.width != stored.frameWidth || out.height != stored.frameHeight) null else runPlateOnly(src, stored, seed, out)
            }
            return outcome ?: full()
        }

        private suspend fun runPlateOnly(
            src: VideoFrameSource,
            pose: PoseTrackV2,
            userSeed: PlateSeed,
            out: VideoFrameMath.Size,
        ): TechniqueAnalysisOutcome {
            val info = src.info
            val clock = timeOf(info)
            val confirmed = userSeed.copy(userConfirmed = true)
            val diameterMm = request.plateDiameterMm ?: DEFAULT_PLATE_MM
            val session = PlateSession(out.width, out.height, clock.realFps, tuning, diameterMm, userHeightM(), confirmed, null)
            val wristTrack = StoredWrists(pose, out.width, out.height, tuning.wristMinVisibility)
            // Se empieza 3 s antes de la semilla (en tiempo real) para poder seguir hacia atrás.
            val fromUs = max(0L, clock.pts(confirmed.tUs) - clock.pts(tuning.ringUs))
            val duration = info.durationUs
            var decoded = 0
            var sinceGate = 0
            val dispatcher = analysisDispatcher()
            try {
                emit(0)
                pumpFrames(src, fromUs, tuning.targetLongEdge, tuning.channelCapacity, dispatcher, { _, _ -> FrameNeeds.LUMA }, {}) { frame ->
                    if (frame.width != out.width || frame.height != out.height) throw PipelineFailure(ERROR_FRAME_SIZE, false, null)
                    val tUs = clock.real(frame.ptsUs)
                    val luma = frame.luma
                    if (luma != null) session.onFrame(luma, tUs, wristTrack.at(tUs), false)
                    decoded++
                    if (duration > fromUs) {
                        emit(((frame.ptsUs - fromUs) * PROGRESS_DECODE / (duration - fromUs)).toInt().coerceIn(0, PROGRESS_DECODE))
                    }
                    if (++sinceGate >= tuning.gateEveryFrames) {
                        sinceGate = 0
                        if (deps.gateIsClosed()) deps.awaitGate()
                    }
                }
            } finally {
                dispatcher.close()
            }
            val report = src as? VideoDecodeReport
            plateRunOutcome = PlateOnlyOutcome(decoded, report?.path, report?.details.orEmpty(), report?.fallbackReason, session)
            val bar = session.barTrack(request.mediaId, diameterMm, true)
            val storedBar = withContext(Dispatchers.IO) { storeBar(bar) }
            val carried = withContext(Dispatchers.IO) { carriedFlags() }
            val extra = carried + buildSet {
                if (report?.path == VideoDecodePath.RETRIEVER) add(AnalysisFlag.DECODE_FALLBACK)
                if (info.isHdr && deps.sdkInt < HDR_TONE_MAP_SDK) add(AnalysisFlag.HDR_SOURCE)
            }
            return measure(pose, storedBar, extra, exercise = completedExercise)
        }

        // ---------------------------------------------------------------------------------------------------------------
        // Sidecars
        // ---------------------------------------------------------------------------------------------------------------

        /** La pose y, con un perfil que usa el disco, su traza; `null` si falta alguna (o no se entiende): entonces toca un FULL. */
        private fun readSidecars(): LoadedSidecars? {
            val poseFile = TechniqueSidecars.poseFile(video)
            if (!poseFile.isFile) return null
            val pose = PoseTrackCodec.decode(poseFile.readBytes())?.takeIf { it.frames.size >= 2 } ?: return null
            if (!profile.usesPlate) return LoadedSidecars(pose, null)
            val barFile = TechniqueSidecars.barFile(video)
            if (!barFile.isFile) return null
            val bar = BarTrackCodec.decode(barFile.readBytes()) ?: return null
            return LoadedSidecars(pose, bar)
        }

        /** Escribe la pose y devuelve la traza TAL COMO queda en el archivo (cuantizada): FULL y RECOMPUTE miden lo mismo. */
        private fun storePose(track: PoseTrackV2): PoseTrackV2 {
            val bytes = PoseTrackCodec.encode(track)
            writeSidecar(TechniqueSidecars.poseFile(video), bytes)
            return PoseTrackCodec.decode(bytes) ?: track
        }

        /**
         * Escribe la traza del disco (también vacía: «se buscó y no se vio» evita repetir la búsqueda en cada RECOMPUTE) y la devuelve
         * como queda en el archivo. Con un perfil que no usa el disco borra la que hubiera, de otro ejercicio.
         */
        private fun storeBar(bar: BarTrack?): BarTrack? {
            val file = TechniqueSidecars.barFile(video)
            if (bar == null || !profile.usesPlate) {
                if (file.exists() && !file.delete()) throw PipelineFailure(ERROR_SIDECAR_WRITE, true, null)
                return null
            }
            val bytes = BarTrackCodec.encode(bar)
            writeSidecar(file, bytes)
            return BarTrackCodec.decode(bytes) ?: bar
        }

        private fun writeSidecar(file: File, bytes: ByteArray) {
            try {
                TechniqueSidecars.writeAtomically(file, bytes)
            } catch (error: IOException) {
                throw PipelineFailure(ERROR_SIDECAR_WRITE, true, error)
            }
        }

        /** Con una semilla confirmada por el usuario, la traza del disco lleva su diámetro (m/s y cm solo existen con él). */
        private fun adoptConfirmedDisc(bar: BarTrack): BarTrack {
            if (request.plateSeed == null) return bar
            return bar.copy(plateDiameterMm = request.plateDiameterMm ?: bar.plateDiameterMm, seed = bar.seed?.copy(userConfirmed = true))
        }

        /** Los avisos que solo sabe quien decodificó (el camino de decodificación y el HDR): el informe anterior los guardó. */
        private fun carriedFlags(): Set<AnalysisFlag> {
            val previous = TechniqueSidecars.readAnalysis(TechniqueSidecars.analysisFile(video))?.report?.flags ?: return emptySet()
            return previous.filterTo(HashSet()) { it in CARRIED_FLAGS }
        }

        // ---------------------------------------------------------------------------------------------------------------
        // Motor, progreso y errores
        // ---------------------------------------------------------------------------------------------------------------

        /** Mide con el motor y cierra el progreso. [extraFlags] son los avisos del data layer; DECODE_FALLBACK limita la confianza a MEDIA. */
        private suspend fun measure(
            pose: PoseTrackV2,
            bar: BarTrack?,
            extraFlags: Set<AnalysisFlag>,
            exercise: ExerciseRef? = null,
            poseEngine: String? = null,
            poseVersion: Int? = null,
        ): TechniqueAnalysisOutcome {
            emit(PROGRESS_DECODE)
            val started = System.nanoTime()
            val usable = bar?.takeIf { profile.usesPlate && it.samples.isNotEmpty() }
            val report = try {
                withContext(Dispatchers.Default) {
                    TechniqueAnalysisEngine.analyze(
                        TechniqueAnalysisInput(pose, usable, profile, request.loadKg, request.userHeightCm, request.setHints),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw PipelineFailure(ERROR_ENGINE, false, error)
            }
            engineMs = (System.nanoTime() - started) / NANOS_PER_MS
            val outcome = TechniqueAnalysisOutcome.Done(
                report = withDataLayerFlags(report, extraFlags),
                exercise = exercise,
                poseEngine = poseEngine,
                poseVersion = poseVersion,
            )
            emit(100)
            return outcome
        }

        suspend fun emit(percent: Int) {
            val value = percent.coerceIn(0, 100)
            if (value <= lastPercent) return
            lastPercent = value
            onProgress(value)
        }

        /** Convierte lo que salió mal en un resultado. Con un fallo definitivo borra el punto de control: no hay nada que reanudar. */
        fun failure(error: Throwable): TechniqueAnalysisOutcome.Failed {
            val failed = classify(error)
            Log.w(TAG, "El análisis de ${request.mediaId} (${request.mode}) falló: ${failed.errorCode}, transitorio=${failed.transient}", error)
            if (!failed.transient) {
                try {
                    TechniqueSidecars.partFile(video).delete()
                } catch (_: SecurityException) {
                    // Sin permiso para borrarlo: el siguiente intento lo descartará por no servir.
                }
            }
            return failed
        }

        fun reportStats() {
            val full = pass
            val plateRun = plateRunOutcome
            val session = full?.plate ?: plateRun?.session
            val stats = PipelineRunStats(
                mode = request.mode,
                framesDecoded = full?.framesDecoded ?: plateRun?.decoded ?: 0,
                framesPosed = full?.framesPosed ?: 0,
                poseStride = full?.stride ?: 0,
                decodePath = full?.decodePath ?: plateRun?.path,
                decodeDetails = full?.decodeDetails ?: plateRun?.details.orEmpty(),
                fallbackReason = full?.fallbackReason ?: plateRun?.fallbackReason,
                poseDelegate = full?.poseDelegate,
                delegateNote = full?.delegateNote,
                usesPlate = ::profile.isInitialized && profile.usesPlate,
                plateStage = session?.stage?.name ?: "NONE",
                autoSeed = session?.seed?.takeIf { !it.userConfirmed },
                barSamples = session?.sampleCount ?: 0,
                plateAttempts = session?.attempts ?: 0,
                resumedFromPtsUs = full?.resumedFromPtsUs,
                wallMs = (System.nanoTime() - startedNs) / NANOS_PER_MS,
                decodeWaitMs = (full?.waitNanos ?: 0L) / NANOS_PER_MS_D,
                poseMs = (full?.poseNanos ?: 0L) / NANOS_PER_MS_D,
                plateMs = (session?.plateNanos ?: 0L) / NANOS_PER_MS_D,
                engineMs = engineMs,
                plateWaitingCounts = session?.waitingCounts?.toMap().orEmpty(),
                plateLastWaitingReason = session?.lastWaitingReason,
                plateSeedState = session?.seed?.let { if (it.userConfirmed) "selected" else "provisional" } ?: "none",
            )
            Log.i(TAG, "Pasada de ${request.mediaId}: $stats")
            try {
                deps.onRunStats(stats)
            } catch (_: Exception) {
                // Las cifras son una cortesía: nunca tiran un análisis.
            }
        }

        private fun userHeightM(): Double =
            request.userHeightCm?.takeIf { it.isFinite() && it > 0.0 }?.div(CM_PER_M) ?: tuning.defaultUserHeightM

        private fun analysisDispatcher(): ExecutorCoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "vt-analyze").apply { isDaemon = true } }.asCoroutineDispatcher()

        // ---------------------------------------------------------------------------------------------------------------
        // La pasada de FULL
        // ---------------------------------------------------------------------------------------------------------------

        /**
         * Lo que dura una pasada: ensamblador de pose, sesión del disco y contadores. Solo se toca desde el hilo `vt-analyze` (el
         * consumidor), salvo [needsOf], que corre en el de decodificación y solo lee valores inmutables o volátiles.
         */
        private inner class FullPass(
            private val src: VideoFrameSource,
            private val estimator: PoseEstimator,
            private val info: VideoInfo,
            meta: CaptureMetadata,
            private val out: VideoFrameMath.Size,
            val stride: Int,
            private val clock: PipelineClock,
            focalLengthPx: Float?,
            resume: AnalysisCheckpoint?,
            private val partFile: File,
        ) {
            private val assembler = PoseTrackAssembler(
                mediaId = request.mediaId,
                frameWidth = out.width,
                frameHeight = out.height,
                rotationApplied = info.rotationDegrees,
                poseStride = stride,
                captureFps = info.captureFps,
                focalLengthPx = focalLengthPx,
                gravityRollDeg = meta.gravityRollDeg,
                gravityPitchDeg = meta.gravityPitchDeg,
                fallbackFps = clock.realFps,
            )
            private val wrists = WristSet()
            private val indexBase: Int = resume?.header?.nextIndex ?: 0
            private val fromUs: Long = resume?.let { it.header.lastPtsUs + 1L } ?: 0L
            private var sinceGate = 0
            private var sinceCheckpoint = 0
            private var poseErrors = resume?.header?.poseErrors ?: 0
            private var lastPtsUs = resume?.header?.lastPtsUs ?: -1L
            private var nextIndex = indexBase

            var framesDecoded = 0
                private set
            var framesPosed = 0
                private set
            var waitNanos = 0L
                private set
            var poseNanos = 0L
                private set
            val resumedFromPtsUs: Long? = resume?.header?.lastPtsUs
            var decodePath: VideoDecodePath? = null
                private set
            var decodeDetails: String = ""
                private set
            var fallbackReason: String? = null
                private set
            var poseDelegate: String? = null
                private set
            var delegateNote: String? = null
                private set

            val plate: PlateSession? = if (profile.usesPlate) {
                PlateSession(
                    out.width, out.height, clock.realFps, tuning, request.plateDiameterMm ?: DEFAULT_PLATE_MM, userHeightM(),
                    request.plateSeed?.copy(userConfirmed = true), AthleteViewImpl(),
                )
            } else {
                null
            }

            init {
                if (resume != null) {
                    assembler.resumeFrom(resume.pose.frames)
                    plate?.restore(checkNotNull(resume.bar), resume.header.plateStage)
                }
            }

            /** Decodifica y mide fotograma a fotograma hasta el final del vídeo (o hasta una cancelación o un fallo). */
            suspend fun execute() {
                val dispatcher = analysisDispatcher()
                try {
                    emit(0)
                    pumpFrames(
                        src, fromUs, tuning.targetLongEdge, tuning.channelCapacity, dispatcher,
                        { index, ptsUs -> needsOf(index, ptsUs) },
                        { nanos -> waitNanos += nanos },
                    ) { frame -> consume(frame) }
                } finally {
                    dispatcher.close()
                    val report = src as? VideoDecodeReport
                    decodePath = report?.path
                    decodeDetails = report?.details.orEmpty()
                    fallbackReason = report?.fallbackReason
                    poseDelegate = estimator.engine.delegate
                    delegateNote = (estimator as? PoseEstimatorReport)?.delegateNote
                }
            }

            /** La traza de pose entera. `NO_FRAMES` si ni el vídeo ni un punto de control dieron un solo fotograma. */
            fun finish(): PoseTrackV2 {
                val track = assembler.build(estimator.engine)
                if (track.frames.isEmpty()) throw PipelineFailure(ERROR_NO_FRAMES, false, null)
                return track
            }

            /**
             * La traza del disco para guardar: `null` si el perfil no lo usa; vacía (sin semilla ni muestras) si se buscó y no se vio.
             * Con la semilla del usuario lleva su diámetro; con una automática, `null`.
             */
            fun barTrack(): BarTrack? {
                val session = plate ?: return null
                val confirmed = request.plateSeed != null
                return session.barTrack(request.mediaId, if (confirmed) request.plateDiameterMm ?: DEFAULT_PLATE_MM else null, confirmed)
            }

            /** Qué pide el decodificador de cada fotograma (corre en `vt-decode`): RGBA si toca pose; luma mientras el disco la quiera. */
            private fun needsOf(index: Int, @Suppress("UNUSED_PARAMETER") ptsUs: Long): FrameNeeds {
                val poseDue = (indexBase + index) % stride == 0
                val lumaDue = plate?.wantsLuma == true
                return when {
                    poseDue && lumaDue -> FrameNeeds.RGBA_AND_LUMA
                    poseDue -> FrameNeeds.RGBA
                    lumaDue -> FrameNeeds.LUMA
                    else -> FrameNeeds.SKIP
                }
            }

            private suspend fun consume(frame: DecodedFrame) {
                if (frame.width != out.width || frame.height != out.height) throw PipelineFailure(ERROR_FRAME_SIZE, false, null)
                val tUs = clock.real(frame.ptsUs)
                var newPose = false
                val rgba = frame.rgba
                if (rgba != null) {
                    val poses = estimate(RgbaFrame(frame.width, frame.height, rgba), tUs)
                    assembler.add(tUs, poses)
                    framesPosed++
                    newPose = true
                    if (plate != null) wrists.fill(poses, out.width, out.height, tuning.wristMinVisibility, tUs)
                }
                val luma = frame.luma
                if (plate != null && luma != null) plate.onFrame(luma, tUs, wrists, newPose)
                framesDecoded++
                lastPtsUs = frame.ptsUs
                nextIndex = indexBase + frame.index + 1
                if (info.durationUs > 0L) {
                    emit(min(PROGRESS_DECODE.toLong(), frame.ptsUs * PROGRESS_DECODE / info.durationUs).toInt())
                }
                if (++sinceGate >= tuning.gateEveryFrames) {
                    sinceGate = 0
                    if (deps.gateIsClosed()) deps.awaitGate()
                }
                if (++sinceCheckpoint >= tuning.checkpointEveryFrames && (plate == null || plate.settled)) {
                    sinceCheckpoint = 0
                    writeCheckpoint()
                }
            }

            /** La pose de un fotograma. Un fallo suelto vale «sin atleta en este fotograma»; demasiados tiran el análisis. */
            private fun estimate(frame: RgbaFrame, tUs: Long): List<DetectedPose> {
                val started = System.nanoTime()
                try {
                    return estimator.estimate(frame, tUs / MICROS_PER_MS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    poseErrors++
                    if (poseErrors > tuning.maxPoseErrors) throw PipelineFailure(ERROR_POSE_FAILED, false, error)
                    Log.w(TAG, "La pose del fotograma en $tUs µs falló ($poseErrors).", error)
                    return emptyList()
                } finally {
                    poseNanos += System.nanoTime() - started
                }
            }

            private suspend fun writeCheckpoint() {
                val pose = assembler.build(estimator.engine)
                if (pose.frames.isEmpty()) return
                val header = AnalysisCheckpoint.Header(
                    mediaId = request.mediaId,
                    poseVersion = POSE_VERSION,
                    videoBytes = video.length(),
                    videoModifiedMs = video.lastModified(),
                    usesPlate = plate != null,
                    poseStride = stride,
                    timeScale = clock.scale,
                    frameWidth = out.width,
                    frameHeight = out.height,
                    lastPtsUs = lastPtsUs,
                    nextIndex = nextIndex,
                    plateStage = when (plate?.stage) {
                        null -> AnalysisCheckpoint.STAGE_NONE
                        PlateSession.Stage.TRACKING -> AnalysisCheckpoint.STAGE_TRACKING
                        else -> AnalysisCheckpoint.STAGE_GAVE_UP
                    },
                    seedKey = AnalysisCheckpoint.seedKey(request.plateSeed),
                    poseErrors = poseErrors,
                )
                val checkpoint = AnalysisCheckpoint(header, pose, plate?.barTrack(request.mediaId, null, request.plateSeed != null))
                try {
                    withContext(Dispatchers.IO) { AnalysisCheckpoints.write(partFile, checkpoint) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IOException) {
                    // El punto de control es una ayuda: si no se puede escribir, el análisis sigue sin él.
                    Log.w(TAG, "No se pudo escribir el punto de control de ${request.mediaId}.", error)
                }
            }

            /** Lo que el disco necesita saber del atleta mientras busca: sus muñecas y su altura en píxeles. */
            private inner class AthleteViewImpl : AthleteView {
                override fun wristsPx(out: FloatArray): Boolean {
                    val frame = assembler.build(estimator.engine).frames.lastOrNull() ?: return false
                    if (!frame.hasPose) return false
                    var any = false
                    val sides = arrayOf(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
                    for (k in sides.indices) {
                        val landmark = sides[k]
                        val visible = frame.vis(landmark) >= tuning.wristMinVisibility &&
                            !frame.x(landmark).isNaN() && !frame.y(landmark).isNaN()
                        out[k * 2] = if (visible) frame.x(landmark) * this@FullPass.out.width else Float.NaN
                        out[k * 2 + 1] = if (visible) frame.y(landmark) * this@FullPass.out.height else Float.NaN
                        if (visible) any = true
                    }
                    return any
                }

                override fun bodyHeightPx(): Double {
                    val frames = assembler.build(estimator.engine).frames
                    if (frames.size < MIN_SCALE_FRAMES) return Double.NaN
                    val recent = frames.takeLast(RECENT_POSE_FRAMES)
                    val first = recent.indexOfFirst { it.hasPose }
                    if (first < 0) return Double.NaN
                    val track = PoseTrackV2(
                        mediaId = request.mediaId,
                        engine = estimator.engine,
                        frameWidth = this@FullPass.out.width,
                        frameHeight = this@FullPass.out.height,
                        rotationApplied = info.rotationDegrees,
                        nominalFps = clock.realFps,
                        poseStride = stride,
                        vfr = false,
                        frames = recent,
                    )
                    val scale = BodyScaleEstimator.estimate(FrameGeometry(track), first until recent.size) ?: return Double.NaN
                    return scale.bodyHeightFh * this@FullPass.out.height
                }
            }
        }
    }

    private fun withDataLayerFlags(report: SetTechniqueReport, extra: Set<AnalysisFlag>): SetTechniqueReport {
        val flags = if (extra.isEmpty()) report.flags else report.flags + extra
        val capped = if (AnalysisFlag.DECODE_FALLBACK in flags && report.confidence == CountConfidence.ALTA) {
            CountConfidence.MEDIA
        } else {
            report.confidence
        }
        return if (flags == report.flags && capped == report.confidence) report else report.copy(flags = flags, confidence = capped)
    }

    companion object {
        /**
         * Versión de la traza de pose que produce este pipeline (modelo, resolución, cadencia). Subirla pide reanalizar el vídeo
         * al abrirlo (`ENGINE_VERSION` solo pide un RECOMPUTE) y descarta los puntos de control de la versión anterior.
         */
        const val POSE_VERSION = 1

        const val ERROR_VIDEO_UNREADABLE = "video_unreadable"
        const val ERROR_DECODE = "decode_failed"
        const val ERROR_NO_FRAMES = "no_frames"
        const val ERROR_POSE_UNAVAILABLE = "pose_unavailable"
        const val ERROR_POSE_FAILED = "pose_failed"
        const val ERROR_FRAME_SIZE = "frame_size_mismatch"
        const val ERROR_PLATE_SEED_MISSING = "plate_seed_missing"
        const val ERROR_SIDECAR_WRITE = "sidecar_write_failed"
        const val ERROR_IO = "io_error"
        const val ERROR_OUT_OF_MEMORY = "out_of_memory"
        const val ERROR_ENGINE = "engine_failed"
        const val ERROR_UNEXPECTED = "analysis_failed"

        private const val TAG = "KpknTechnique"
        private const val DEFAULT_FPS = 30f
        private const val DEFAULT_PLATE_MM = 450
        private const val CM_PER_M = 100.0

        /** Un vídeo cuya captura es más rápida que esto (respecto de su reproducción) se trata como cámara lenta. */
        private const val SLOW_MOTION_RATIO = 1.25f

        /** Del 0 al 95 % va la decodificación; del 95 al 100, el motor. */
        private const val PROGRESS_DECODE = 95

        /** Desde la API 31 se pide al códec la salida SDR; antes, un vídeo HDR lleva el aviso HDR_SOURCE. */
        private const val HDR_TONE_MAP_SDK = 31

        /** Fotogramas de pose recientes con los que se mide la altura del cuerpo para el radio previo del disco, y los mínimos. */
        private const val RECENT_POSE_FRAMES = 90
        private const val MIN_SCALE_FRAMES = 3

        private const val MICROS_PER_MS = 1000L
        private const val NANOS_PER_MS = 1_000_000L
        private const val NANOS_PER_MS_D = 1_000_000.0
        private const val MAX_CAUSE_DEPTH = 12

        /** Avisos que solo conoce quien decodificó: un RECOMPUTE o un PLATE_ONLY los hereda del informe anterior. */
        private val CARRIED_FLAGS = setOf(AnalysisFlag.DECODE_FALLBACK, AnalysisFlag.HDR_SOURCE)

        /** El reloj del vídeo: cadencia real y escala del PTS del contenedor al tiempo real. */
        private fun timeOf(info: VideoInfo): PipelineClock {
            val nominal = info.nominalFps?.takeIf { it > 0f } ?: info.captureFps?.takeIf { it > 0f } ?: DEFAULT_FPS
            val capture = info.captureFps?.takeIf { it > 0f }
            // Cámara lenta de galería: se capturó mucho más rápido de lo que se reproduce. El PTS está en tiempo de reproducción.
            val slowdown = if (capture != null && capture > nominal * SLOW_MOTION_RATIO) capture / nominal else 1f
            return PipelineClock(1.0 / slowdown, nominal * slowdown)
        }

        /** `true` si [error] o alguna de sus causas se arregla reintentando: E/S o un fallo transitorio del códec. */
        internal fun isTransient(error: Throwable): Boolean {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth++ < MAX_CAUSE_DEPTH) {
                if (current is IOException) return true
                if (current is MediaCodec.CodecException && current.isTransient) return true
                current = current.cause
            }
            return false
        }

        /** El resultado de un fallo, con su código estable y si merece un reintento. */
        internal fun classify(error: Throwable): TechniqueAnalysisOutcome.Failed = when {
            error is PipelineFailure -> TechniqueAnalysisOutcome.Failed(error.code, error.transient)
            error is OutOfMemoryError -> TechniqueAnalysisOutcome.Failed(ERROR_OUT_OF_MEMORY, false)
            error is VideoDecodeException -> TechniqueAnalysisOutcome.Failed(ERROR_DECODE, isTransient(error))
            error is IOException -> TechniqueAnalysisOutcome.Failed(ERROR_IO, true)
            else -> TechniqueAnalysisOutcome.Failed(ERROR_UNEXPECTED, isTransient(error))
        }
    }
}

/** Un fallo del pipeline con su código estable; `classify` lo traduce tal cual. */
private class PipelineFailure(val code: String, val transient: Boolean, cause: Throwable?) : RuntimeException(code, cause)

/** Los sidecars que lee un RECOMPUTE. */
private class LoadedSidecars(val pose: PoseTrackV2, val bar: BarTrack?)

/** Lo que dejó PLATE_ONLY (para las cifras). */
private class PlateOnlyOutcome(
    val decoded: Int,
    val path: VideoDecodePath?,
    val details: String,
    val fallbackReason: String?,
    val session: PlateSession,
)

/** Del PTS del contenedor al tiempo real (y vuelta). [scale] = µs reales por µs del contenedor: 1 salvo en cámara lenta. */
internal class PipelineClock(val scale: Double, val realFps: Float) {
    fun real(ptsUs: Long): Long = if (scale == 1.0) ptsUs else Math.round(ptsUs * scale)

    fun pts(realUs: Long): Long = if (scale == 1.0) realUs else Math.round(realUs / scale)
}

/**
 * Decodifica [source] desde [fromUs] y entrega cada fotograma a [consume] en el hilo de [consumerDispatcher], con un `Channel` de
 * [capacity] entre los dos: la decodificación va por delante y los dos trabajos se solapan. [onWait] recibe los ns que el consumidor
 * esperó cada fotograma. Termina cuando se acaba el vídeo, o con el primer fallo (de la decodificación o de [consume]) o la cancelación.
 *
 * Los [DecodedFrame] vienen de un pool de huecos reutilizados por turnos: con el canal de [capacity] viven a la vez capacidad + 2
 * (el que se procesa, los de la cola y el que espera en `send`), así que `FramePool.SLOTS` ha de ser al menos eso.
 * NUNCA vuelve con la decodificación en marcha: antes cancela al productor y ESPERA a que acabe (también si se canceló todo
 * desde fuera), para que quien cierre la fuente después no la cierre bajo un `decode` vivo.
 */
private suspend fun pumpFrames(
    source: VideoFrameSource,
    fromUs: Long,
    targetLongEdge: Int,
    capacity: Int,
    consumerDispatcher: CoroutineDispatcher,
    needs: (index: Int, ptsUs: Long) -> FrameNeeds,
    onWait: (nanos: Long) -> Unit,
    consume: suspend (DecodedFrame) -> Unit,
) {
    coroutineScope {
        val channel = Channel<DecodedFrame>(capacity)
        val producer = launch(Dispatchers.Default) {
            source.decode(fromUs, targetLongEdge, needs) { frame -> channel.send(frame) }
            channel.close()
        }
        try {
            withContext(consumerDispatcher) {
                val frames = channel.iterator()
                while (true) {
                    val waitStart = System.nanoTime()
                    if (!frames.hasNext()) break
                    onWait(System.nanoTime() - waitStart)
                    consume(frames.next())
                }
            }
        } finally {
            withContext(NonCancellable) { producer.cancelAndJoin() }
        }
    }
}

// -------------------------------------------------------------------------------------------------------------------
// El disco
// -------------------------------------------------------------------------------------------------------------------

/** Lo que la sesión del disco necesita saber del atleta mientras busca el disco (solo la usa la adquisición automática). */
private interface AthleteView {
    /** Muñecas del último fotograma con pose en píxeles: [izquierda x, y, derecha x, y], NaN la que no se ve. `false` si no se ve ninguna. */
    fun wristsPx(out: FloatArray): Boolean

    /** Altura del cuerpo en píxeles según los últimos fotogramas, o NaN si aún no se puede medir. */
    fun bodyHeightPx(): Double
}

/** Hasta cuatro muñecas (dos poses × dos) del último fotograma de pose, en píxeles, y cuándo se vieron. */
private class WristSet {
    val xy = FloatArray(MAX * 2)
    var count = 0
    var tUs = Long.MIN_VALUE

    fun fill(poses: List<DetectedPose>, width: Int, height: Int, minVisibility: Float, nowUs: Long) {
        count = 0
        for (pose in poses) {
            for (mp in WRIST_INDEXES) {
                if (count >= MAX) break
                val p = pose.image.getOrNull(mp) ?: continue
                if (!(p.visibility >= minVisibility) || p.x.isNaN() || p.y.isNaN()) continue
                xy[count * 2] = p.x * width
                xy[count * 2 + 1] = p.y * height
                count++
            }
        }
        tUs = nowUs
    }

    fun copyFrom(other: WristSet) {
        count = other.count
        tUs = other.tUs
        System.arraycopy(other.xy, 0, xy, 0, count * 2)
    }

    /** La muñeca más cercana a ([x], [y]) vista hace menos de [maxAgeUs] de [nowUs]; `null` si no hay. */
    fun nearest(nowUs: Long, x: Float, y: Float, maxAgeUs: Long): Pair<Float, Float>? {
        if (count == 0 || x.isNaN() || y.isNaN()) return null
        if (abs(nowUs - tUs) > maxAgeUs) return null
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until count) {
            val dx = xy[i * 2] - x
            val dy = xy[i * 2 + 1] - y
            val d = dx * dx + dy * dy
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return Pair(xy[best * 2], xy[best * 2 + 1])
    }

    companion object {
        const val MAX = 4
        const val PRIOR_MAX_AGE_US = 250_000L
        private val WRIST_INDEXES = intArrayOf(PoseLandmark.LEFT_WRIST.mp, PoseLandmark.RIGHT_WRIST.mp)
    }
}

/** Las muñecas de una traza de pose YA guardada, por instante (PLATE_ONLY no estima pose: lee la del sidecar). */
private class StoredWrists(pose: PoseTrackV2, width: Int, height: Int, minVisibility: Float) {
    private val times = LongArray(pose.frames.size) { pose.frames[it].tUs }
    private val points = FloatArray(pose.frames.size * 4) { Float.NaN }
    private val set = WristSet()
    private var cursor = 0

    init {
        val sides = arrayOf(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
        for (i in pose.frames.indices) {
            val frame = pose.frames[i]
            if (!frame.hasPose) continue
            for (k in sides.indices) {
                val landmark = sides[k]
                if (frame.vis(landmark) >= minVisibility && !frame.x(landmark).isNaN() && !frame.y(landmark).isNaN()) {
                    points[i * 4 + k * 2] = frame.x(landmark) * width
                    points[i * 4 + k * 2 + 1] = frame.y(landmark) * height
                }
            }
        }
    }

    /** Las muñecas del fotograma de pose más cercano a [tUs] (los instantes llegan en orden creciente). */
    fun at(tUs: Long): WristSet {
        while (cursor + 1 < times.size && abs(times[cursor + 1] - tUs) <= abs(times[cursor] - tUs)) cursor++
        set.count = 0
        for (k in 0 until 2) {
            val x = points[cursor * 4 + k * 2]
            val y = points[cursor * 4 + k * 2 + 1]
            if (x.isNaN() || y.isNaN()) continue
            set.xy[set.count * 2] = x
            set.xy[set.count * 2 + 1] = y
            set.count++
        }
        set.tUs = tUs
        return set
    }
}

/**
 * Las muñecas de los últimos fotogramas de pose, para saber si la barra está quieta: [isStill] mira si una muñeca se mantiene dentro
 * de una caja pequeña durante la ventana. Un fotograma sin muñeca visible rompe la ventana (NaN).
 */
private class WristHistory {
    private val times = LongArray(CAPACITY)
    private val xy = FloatArray(CAPACITY * 4)
    private var size = 0
    private var head = 0

    fun push(tUs: Long, wrists: FloatArray) {
        times[head] = tUs
        for (i in 0 until 4) xy[head * 4 + i] = wrists[i]
        advance()
    }

    fun pushMissing(tUs: Long) {
        times[head] = tUs
        for (i in 0 until 4) xy[head * 4 + i] = Float.NaN
        advance()
    }

    private fun advance() {
        head = (head + 1) % CAPACITY
        if (size < CAPACITY) size++
    }

    /** `true` si alguna muñeca estuvo visible y dentro de una caja de [maxExtentPx] (en los dos ejes) durante los últimos [windowUs]. */
    fun isStill(nowUs: Long, windowUs: Long, maxExtentPx: Float): Boolean {
        for (wrist in 0 until 2) {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var reached = false
            var valid = true
            var k = 0
            while (k < size) {
                val slot = (head - 1 - k + CAPACITY) % CAPACITY
                val x = xy[slot * 4 + wrist * 2]
                val y = xy[slot * 4 + wrist * 2 + 1]
                if (x.isNaN() || y.isNaN()) {
                    valid = false
                    break
                }
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (nowUs - times[slot] >= windowUs) {
                    reached = true
                    break
                }
                k++
            }
            if (valid && reached && maxX - minX <= maxExtentPx && maxY - minY <= maxExtentPx) return true
        }
        return false
    }

    private companion object {
        const val CAPACITY = 64
    }
}

/**
 * El búfer circular de luma de los últimos fotogramas (como mucho [capacity]) con su instante y sus muñecas: de aquí sale el
 * seguimiento hacia atrás. COPIA la luma (el pool de decodificación la recicla); los huecos se reservan al usarse por primera vez
 * y [release] los suelta todos de golpe.
 */
private class LumaRing(private val capacity: Int, private val frameBytes: Int) {
    private var luma = arrayOfNulls<ByteArray>(capacity)
    private val times = LongArray(capacity)
    private val wrists = Array(capacity) { WristSet() }
    private var head = 0

    var size = 0
        private set

    fun push(source: ByteArray, tUs: Long, held: WristSet) {
        val slot = head
        val buffer = luma[slot] ?: ByteArray(frameBytes).also { luma[slot] = it }
        System.arraycopy(source, 0, buffer, 0, frameBytes)
        times[slot] = tUs
        wrists[slot].copyFrom(held)
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    /** El hueco del fotograma número [k] contando desde el más antiguo (0) hasta el más reciente (`size − 1`). */
    private fun slotOf(k: Int): Int = (head - size + k + capacity) % capacity

    fun lumaAt(k: Int): ByteArray = checkNotNull(luma[slotOf(k)])

    fun timeAt(k: Int): Long = times[slotOf(k)]

    fun wristsAt(k: Int): WristSet = wrists[slotOf(k)]

    fun release() {
        luma = arrayOfNulls(capacity)
        size = 0
        head = 0
    }
}

/**
 * La búsqueda y el seguimiento del disco de una pasada (§8). Estados: [Stage.PENDING] (se llena el búfer circular de luma mientras
 * se espera la semilla: la del usuario, cuando llegue su instante, o la automática, cuando una adquisición acierte),
 * [Stage.TRACKING] (rastreador hacia delante en cada fotograma) y [Stage.GAVE_UP] (sin disco: ya no se pide luma).
 *
 * Sin semilla del usuario ([confirmedSeed] `null`) busca con las muñecas casi quietas: pasada la ventana de quietud, y con un
 * intervalo mínimo entre intentos, `PlateDetector.propose` mira cerca de las muñecas con el radio previo de la escala corporal
 * (r_b = 0,5·D·S/H, §8 punto 3) y la mejor propuesta con cobertura suficiente es la semilla. Al fijar la semilla se sigue hacia atrás
 * con otro rastreador sobre el búfer. Cualquier fallo del disco lo da por perdido ([Stage.GAVE_UP]) sin tirar el análisis.
 */
private class PlateSession(
    private val width: Int,
    private val height: Int,
    private val fps: Float,
    private val tuning: PipelineTuning,
    private val diameterMm: Int,
    private val userHeightM: Double,
    private val confirmedSeed: PlateSeed?,
    private val athlete: AthleteView?,
) {
    enum class Stage { PENDING, TRACKING, GAVE_UP }

    @Volatile
    var stage: Stage = Stage.PENDING
        private set

    /** `true` mientras la decodificación deba traer luma. Se lee desde el hilo de decodificación. */
    @Volatile
    var wantsLuma: Boolean = true
        private set

    var seed: PlateSeed? = null
        private set
    var attempts = 0
        private set
    var plateNanos = 0L
        private set

    val settled: Boolean get() = stage != Stage.PENDING
    val sampleCount: Int get() = backward.size + forward.size

    val waitingCounts: Map<String, Int> get() = waiting
    var lastWaitingReason: String? = null
        private set
    private val waiting = LinkedHashMap<String, Int>()

    private fun waitFor(reason: String): Boolean {
        waiting[reason] = (waiting[reason] ?: 0) + 1
        lastWaitingReason = reason
        return false
    }

    private val config = PlateTrackerConfig(plateDiameterMm = diameterMm)
    private val ring = LumaRing(ringCapacity(), width * height)
    private val history = WristHistory()
    private val athleteWrists = FloatArray(4)
    private val scratch = WristSet()
    private var tracker: PlateTracker? = null
    private var trackingContinuity = PlateTrackingContinuity.UNKNOWN
    private var continuityBreakUs: Long? = null

    private fun observeContinuity(current: PlateTracker) {
        val at = current.continuityBreakUs ?: return
        trackingContinuity = PlateTrackingContinuity.INTERRUPTED
        continuityBreakUs = continuityBreakUs?.let { minOf(it, at) } ?: at
    }
    private val forward = ArrayList<BarSample>()
    private val backward = ArrayList<BarSample>() // del más reciente al más antiguo
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var firstTUs: Long? = null
    private var lastAttemptTUs: Long? = null

    private fun ringCapacity(): Int {
        val byTime = ceil(tuning.ringUs / MICROS * fps).toInt() + 1
        val byBytes = (tuning.ringMaxBytes / max(1, width * height)).toInt()
        return max(2, min(byTime, byBytes))
    }

    /** Un fotograma de luma (válido solo durante la llamada). [newPose] dice que [wrists] se acaba de actualizar con la pose de este fotograma. */
    fun onFrame(luma: ByteArray, tUs: Long, wrists: WristSet, newPose: Boolean) {
        val started = System.nanoTime()
        try {
            when (stage) {
                Stage.TRACKING -> trackForward(luma, tUs, wrists)
                Stage.GAVE_UP -> Unit
                Stage.PENDING -> pending(luma, tUs, wrists, newPose)
            }
        } catch (error: Exception) {
            // El disco es un complemento del análisis: si algo falla, se sigue con la pose.
            Log.w(TAG, "El seguimiento del disco falló; el análisis sigue sin él.", error)
            giveUp()
        } finally {
            plateNanos += System.nanoTime() - started
        }
    }

    private fun pending(luma: ByteArray, tUs: Long, wrists: WristSet, newPose: Boolean) {
        val first = firstTUs ?: tUs.also { firstTUs = it }
        val pinned = confirmedSeed
        if (pinned != null) {
            if (tUs >= pinned.tUs) {
                activate(pinned, luma, tUs, wrists)
                return
            }
            if (newPose) waitFor("awaiting_seed")
        } else {
            if (newPose && tryAcquire(luma, tUs, wrists)) return
            if (tUs - first >= tuning.acquireDeadlineUs || attempts >= tuning.maxAttempts) {
                giveUp()
                return
            }
        }
        ring.push(luma, tUs, wrists)
    }

    /** Un intento de adquisición si las muñecas llevan quietas la ventana; `true` si encontró el disco y ya está siguiéndolo. */
    private fun tryAcquire(luma: ByteArray, tUs: Long, wrists: WristSet): Boolean {
        val view = athlete ?: return waitFor("no_athlete")
        if (!view.wristsPx(athleteWrists)) {
            history.pushMissing(tUs)
            return waitFor("no_wrist")
        }
        history.push(tUs, athleteWrists)
        val last = lastAttemptTUs
        if (last != null && tUs - last < tuning.attemptCooldownUs) return waitFor("cooldown")
        if (!history.isStill(tUs, tuning.stillWindowUs, (tuning.stillMaxFh * height).toFloat())) return waitFor("no_still")
        val body = view.bodyHeightPx()
        if (!(body > 0.0) || !body.isFinite()) return waitFor("no_scale")
        lastAttemptTUs = tUs
        attempts++
        val expected = (0.5 * (diameterMm / MM_PER_M) * body / userHeightM).toFloat()
        val points = ArrayList<Pair<Float, Float>>(2)
        if (!athleteWrists[0].isNaN()) points.add(Pair(athleteWrists[0], athleteWrists[1]))
        if (!athleteWrists[2].isNaN()) points.add(Pair(athleteWrists[2], athleteWrists[3]))
        val candidates = PlateDetector.propose(LumaFrame(width, height, luma), points, expected, CANDIDATES)
        val best = candidates.firstOrNull { it.coverage >= tuning.minSeedCoverage } ?: return waitFor("no_candidate")
        Log.i(TAG, "Disco automático en $tUs µs: c=(${best.cx}, ${best.cy}) r=${best.r} cobertura=${best.coverage} (previo $expected, intento $attempts).")
        activate(PlateSeed(tUs, best.cx, best.cy, best.r, false), luma, tUs, wrists)
        return true
    }

    /** Fija la semilla: el rastreador hacia delante arranca en este fotograma y otro repasa hacia atrás lo que hay en el búfer. */
    private fun activate(chosen: PlateSeed, luma: ByteArray, tUs: Long, wrists: WristSet) {
        seed = chosen
        trackingContinuity = PlateTrackingContinuity.UNINTERRUPTED
        continuityBreakUs = null
        lastWaitingReason = null
        tracker = PlateTracker(chosen, fps, config)
        lastX = chosen.cx
        lastY = chosen.cy
        stage = Stage.TRACKING
        trackForward(luma, tUs, wrists)
        if (ring.size > 0) trackBackward(chosen)
        ring.release()
    }

    private fun trackForward(luma: ByteArray, tUs: Long, wrists: WristSet) {
        val current = tracker ?: return
        val prior = wrists.nearest(tUs, lastX, lastY, WristSet.PRIOR_MAX_AGE_US)
        val sample = current.track(LumaFrame(width, height, luma), tUs, prior)
        forward.add(sample)
        observeContinuity(current)
        lastX = sample.cx
        lastY = sample.cy
    }

    private fun trackBackward(chosen: PlateSeed) {
        val back = PlateTracker(chosen, fps, config)
        var x = chosen.cx
        var y = chosen.cy
        for (k in ring.size - 1 downTo 0) {
            val tUs = ring.timeAt(k)
            scratch.copyFrom(ring.wristsAt(k))
            val prior = scratch.nearest(tUs, x, y, WristSet.PRIOR_MAX_AGE_US)
            val sample = back.track(LumaFrame(width, height, ring.lumaAt(k)), tUs, prior)
            backward.add(sample)
            observeContinuity(back)
            x = sample.cx
            y = sample.cy
        }
    }

    private fun giveUp() {
        stage = Stage.GAVE_UP
        wantsLuma = false
        ring.release()
    }

    /** Reanuda desde un punto de control: las muestras de entonces y, si seguía, un rastreador nuevo que arranca de la última fiable. */
    fun restore(bar: BarTrack, checkpointStage: String) {
        forward.addAll(bar.samples)
        seed = bar.seed
        continuityBreakUs = bar.continuityBreakUs
        // Kalman/template state is not serialized: restarting from a last sample cannot establish the old chain.
        trackingContinuity = if (bar.trackingContinuity == PlateTrackingContinuity.INTERRUPTED)
            PlateTrackingContinuity.INTERRUPTED else PlateTrackingContinuity.UNKNOWN
        if (checkpointStage == AnalysisCheckpoint.STAGE_GAVE_UP || bar.samples.isEmpty()) {
            giveUp()
            return
        }
        val last = bar.samples.lastOrNull { it.source == BarSampleSource.HOUGH || it.source == BarSampleSource.NCC } ?: bar.samples.last()
        tracker = PlateTracker(PlateSeed(last.tUs, last.cx, last.cy, last.r, false), fps, config)
        lastX = last.cx
        lastY = last.cy
        stage = Stage.TRACKING
    }

    /**
     * La traza del disco hasta ahora, en orden de tiempo (primero lo seguido hacia atrás). Sin semilla, vacía. [diameterMm] y
     * [confirmed] son los del usuario (o `null` y `false` con una semilla automática).
     */
    fun barTrack(mediaId: String, diameterMm: Int?, confirmed: Boolean): BarTrack {
        val samples = ArrayList<BarSample>(backward.size + forward.size)
        for (i in backward.indices.reversed()) samples.add(backward[i])
        samples.addAll(forward)
        return BarTrack(mediaId, width, height, diameterMm, seed?.copy(userConfirmed = confirmed), samples,
            trackingContinuity = trackingContinuity, continuityBreakUs = continuityBreakUs)
    }

    private companion object {
        const val MICROS = 1_000_000.0
        const val MM_PER_M = 1000.0
        const val CANDIDATES = 3
        const val TAG = "KpknTechnique"
    }
}
