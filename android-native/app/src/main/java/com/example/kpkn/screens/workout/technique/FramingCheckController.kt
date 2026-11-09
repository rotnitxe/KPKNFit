package com.example.kpkn.screens.workout.technique

import android.content.Context
import android.graphics.Bitmap
import com.example.kpkn.data.media.pose.DetectedPose
import com.example.kpkn.data.media.pose.MediaPipePoseEstimators
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.PoseModel
import com.example.kpkn.data.media.pose.RgbaFrame
import com.example.kpkn.domain.biomechanics.framing.FramingCheckStatus
import com.example.kpkn.domain.biomechanics.framing.FramingEvaluator
import com.example.kpkn.domain.biomechanics.framing.FramingSpec
import com.example.kpkn.domain.biomechanics.framing.FramingVerdict
import com.example.kpkn.domain.biomechanics.vision.LumaFrame
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.vision.PlateDetector
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Controlador de comprobación continua de encuadre (§1 punto 1 y §4 punto 14).
 *
 * Muestra el estado del encuadre en tiempo real antes de iniciar la grabación:
 * - Muestrea la vista previa a ~2 Hz en el hilo principal;
 * - Reduce el fotograma a 256 px fuera del hilo principal;
 * - Ejecuta MediaPipe en modo IMAGE con CPU en un hilo dedicado `vt-framing`;
 * - Si la detección anterior aún no ha terminado, se salta;
 * - Opcional: detección de disco si el perfil lo requiere;
 * - Evalúa con [FramingEvaluator] incluyendo la inclinación de la gravedad;
 * - Expone el veredicto en [verdict];
 * - Solo activo con la vista previa y sin grabar.
 */
class FramingCheckController(
    private val scope: CoroutineScope,
    private val spec: FramingSpec,
    private val context: Context? = null,
    private val gravityRollProvider: () -> Float? = { null },
    private val externalEstimator: PoseEstimator? = null,
    private val evaluator: FramingEvaluator = FramingEvaluator(),
) : Closeable {

    private val _verdict = MutableStateFlow(
        FramingVerdict(
            ok = false,
            issues = emptyList(),
            view = null,
        )
    )
    val verdict: StateFlow<FramingVerdict> = _verdict.asStateFlow()

    private val _status = MutableStateFlow(FramingCheckStatus.CHECKING)

    /** Diferencia «aún comprobando» de «no se pudo comprobar» sin inventar una aprobación. */
    val status: StateFlow<FramingCheckStatus> = _status.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vt-framing").apply { isDaemon = true }
    }
    private val framingDispatcher = executor.asCoroutineDispatcher()

    private var samplingJob: Job? = null
    private val isProcessing = AtomicBoolean(false)
    private val evaluationLock = Any()
    private val generation = AtomicLong(0L)

    @Volatile
    var isRecording: Boolean = false
        set(value) {
            field = value
            if (value) {
                // Durante la grabación se congela la evaluación
            }
        }

    @Volatile
    private var isSampling: Boolean = false

    private var internalEstimator: PoseEstimator? = null

    private fun getOrInitEstimator(): PoseEstimator? {
        if (externalEstimator != null) return externalEstimator
        if (internalEstimator != null) return internalEstimator
        val ctx = context?.applicationContext ?: return null
        return runCatching {
            MediaPipePoseEstimators.create(
                context = ctx,
                mode = RunningMode.IMAGE,
                model = PoseModel.Full,
                preferGpu = false,
                numPoses = 2,
            ).also { internalEstimator = it }
        }.getOrNull()
    }

    /**
     * Inicia el bucle de muestreo continuo de la vista previa a ~2 Hz.
     */
    fun start(bitmapProvider: () -> Bitmap?) {
        val activeGeneration = beginSamplingGeneration()
        samplingJob?.cancel()
        samplingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                // Claim the inference slot before asking PreviewView for a bitmap. This avoids full-resolution copies
                // every 500 ms while a previous pose inference is still running.
                if (!isRecording && isSampling && isProcessing.compareAndSet(false, true)) {
                    val bmp = runCatching { bitmapProvider() }.getOrNull()
                    if (bmp == null) {
                        isProcessing.set(false)
                    } else {
                        launch(framingDispatcher) {
                            try {
                                processBitmapInternal(bmp, activeGeneration)
                            } catch (_: Throwable) {
                            } finally {
                                // PreviewView.bitmap returns an owned snapshot; release it after inference.
                                if (!bmp.isRecycled) bmp.recycle()
                                isProcessing.set(false)
                            }
                        }
                    }
                }
                delay(500L) // ~2 Hz
            }
        }
    }

    /** Starts a fresh preview identity; exposed internally for deterministic lifecycle regression tests. */
    internal fun beginSamplingGeneration(): Long {
        isSampling = true
        return synchronized(evaluationLock) {
            val next = generation.incrementAndGet()
            evaluator.reset()
            _verdict.value = FramingVerdict(ok = false, issues = emptyList(), view = null)
            _status.value = FramingCheckStatus.CHECKING
            next
        }
    }

    /**
     * Detiene el bucle de muestreo.
     */
    fun stop() {
        isSampling = false
        samplingJob?.cancel()
        samplingJob = null
        synchronized(evaluationLock) { generation.incrementAndGet() }
    }

    /**
     * Procesa un [Bitmap] de forma síncrona/suspendida para pruebas y evaluación directa.
     */
    suspend fun processBitmap(bitmap: Bitmap): FramingVerdict = withContext(framingDispatcher) {
        processBitmapInternal(bitmap)
    }

    /** Evaluates a sample captured by one preview generation; stale generations cannot publish. */
    internal suspend fun processBitmapForGeneration(bitmap: Bitmap, expectedGeneration: Long): FramingVerdict =
        withContext(framingDispatcher) { processBitmapInternal(bitmap, expectedGeneration) }

    private fun processBitmapInternal(bitmap: Bitmap, expectedGeneration: Long? = null): FramingVerdict {
        val origW = bitmap.width
        val origH = bitmap.height
        if (origW <= 0 || origH <= 0) return _verdict.value

        val maxDim = maxOf(origW, origH)
        val scaled: Bitmap
        val needRecycle: Boolean
        if (maxDim > 256) {
            val scale = 256f / maxDim
            val targetW = (origW * scale).toInt().coerceAtLeast(1)
            val targetH = (origH * scale).toInt().coerceAtLeast(1)
            scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            needRecycle = (scaled != bitmap)
        } else {
            scaled = bitmap
            needRecycle = false
        }

        try {
            val w = scaled.width
            val h = scaled.height
            val byteCount = w * h * 4
            val buffer = ByteBuffer.allocateDirect(byteCount)
            scaled.copyPixelsToBuffer(buffer)
            buffer.rewind()

            val estimator = getOrInitEstimator()
            val detectedPoses = if (estimator == null) {
                return publishUnavailableIfCurrent(expectedGeneration)
            } else {
                runCatching {
                    estimator.estimate(RgbaFrame(w, h, buffer), 0L)
                }.getOrElse {
                    return publishUnavailableIfCurrent(expectedGeneration)
                }
            }

            val mappedPoses = detectedPoses.map { toLandmarkMap(it) }

            val plateSeen = if (spec.needsPlate) {
                detectPlatePresence(scaled, w, h)
            } else {
                null
            }

            return synchronized(evaluationLock) {
                if (expectedGeneration != null && (generation.get() != expectedGeneration || !isSampling)) {
                    return@synchronized _verdict.value
                }
                val v = evaluator.evaluate(
                    poses = mappedPoses,
                    frameWidth = w,
                    frameHeight = h,
                    spec = spec,
                    rollDeg = gravityRollProvider(),
                    plateSeen = plateSeen,
                )
                _verdict.value = v
                _status.value = FramingCheckStatus.EVALUATED
                v
            }
        } finally {
            if (needRecycle) {
                scaled.recycle()
            }
        }
    }

    private fun publishUnavailableIfCurrent(expectedGeneration: Long?): FramingVerdict = synchronized(evaluationLock) {
        if (expectedGeneration == null || (generation.get() == expectedGeneration && isSampling)) {
            _status.value = FramingCheckStatus.UNAVAILABLE
        }
        _verdict.value
    }

    private fun detectPlatePresence(scaledBitmap: Bitmap, w: Int, h: Int): Boolean {
        return runCatching {
            val pixels = IntArray(w * h)
            scaledBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val luma = ByteArray(w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                luma[i] = ((299 * r + 587 * g + 114 * b) / 1000).toByte()
            }
            val lumaFrame = LumaFrame(w, h, luma)
            val candidates = PlateDetector.propose(
                frame = lumaFrame,
                wristsPx = emptyList(),
                expectedRadiusPx = w * 0.15f,
                max = 1,
            )
            candidates.isNotEmpty()
        }.getOrDefault(false)
    }

    private fun toLandmarkMap(pose: DetectedPose): Map<PoseLandmark, LandmarkSample> {
        val map = EnumMap<PoseLandmark, LandmarkSample>(PoseLandmark::class.java)
        for (landmark in PoseLandmark.KEPT_IMAGE) {
            val sample = pose.image.getOrNull(landmark.mp) ?: continue
            if (sample.x.isNaN() || sample.y.isNaN()) continue
            map[landmark] = sample
        }
        return map
    }

    override fun close() {
        stop()
        val ownedEstimator = if (externalEstimator == null) internalEstimator else null
        internalEstimator = null
        if (ownedEstimator != null) runCatching { executor.execute { ownedEstimator.close() } }
        framingDispatcher.close()
    }
}
