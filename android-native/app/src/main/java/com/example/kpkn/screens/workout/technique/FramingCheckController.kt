package com.example.kpkn.screens.workout.technique

import android.content.Context
import android.graphics.Bitmap
import com.example.kpkn.data.media.pose.DetectedPose
import com.example.kpkn.data.media.pose.MediaPipePoseEstimators
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.PoseModel
import com.example.kpkn.data.media.pose.RgbaFrame
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

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vt-framing").apply { isDaemon = true }
    }
    private val framingDispatcher = executor.asCoroutineDispatcher()

    private var samplingJob: Job? = null
    private val isProcessing = AtomicBoolean(false)

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
        isSampling = true
        evaluator.reset()
        samplingJob?.cancel()
        samplingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (!isRecording && isSampling) {
                    val bmp = runCatching { bitmapProvider() }.getOrNull()
                    if (bmp != null) {
                        if (isProcessing.compareAndSet(false, true)) {
                            launch(framingDispatcher) {
                                try {
                                    processBitmapInternal(bmp)
                                } catch (_: Throwable) {
                                } finally {
                                    isProcessing.set(false)
                                }
                            }
                        }
                    }
                }
                delay(500L) // ~2 Hz
            }
        }
    }

    /**
     * Detiene el bucle de muestreo.
     */
    fun stop() {
        isSampling = false
        samplingJob?.cancel()
        samplingJob = null
    }

    /**
     * Procesa un [Bitmap] de forma síncrona/suspendida para pruebas y evaluación directa.
     */
    suspend fun processBitmap(bitmap: Bitmap): FramingVerdict = withContext(framingDispatcher) {
        processBitmapInternal(bitmap)
    }

    private fun processBitmapInternal(bitmap: Bitmap): FramingVerdict {
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
            val detectedPoses = estimator?.estimate(RgbaFrame(w, h, buffer), 0L).orEmpty()

            val mappedPoses = detectedPoses.map { toLandmarkMap(it) }

            val plateSeen = if (spec.needsPlate) {
                detectPlatePresence(scaled, w, h)
            } else {
                null
            }

            val roll = gravityRollProvider()
            val v = evaluator.evaluate(
                poses = mappedPoses,
                frameWidth = w,
                frameHeight = h,
                spec = spec,
                rollDeg = roll,
                plateSeen = plateSeen,
            )
            _verdict.value = v
            return v
        } finally {
            if (needRecycle) {
                scaled.recycle()
            }
        }
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
        if (externalEstimator == null) {
            internalEstimator?.close()
            internalEstimator = null
        }
        executor.shutdown()
    }
}
