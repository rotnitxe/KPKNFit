package com.example.kpkn.data.media.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.WorldSample
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/** El modelo de pose que carga [MediaPipePoseEstimators.create]. */
sealed interface PoseModel {
    /** El modelo «full», empaquetado en los assets de la app (`mediapipe/pose_landmarker_full.task`). */
    data object Full : PoseModel

    /** El modelo «heavy», de un archivo: NO va en el repo ni en el APK, se empuja al dispositivo (banco de depuración). */
    data class HeavyFromFile(val path: String) : PoseModel
}

object MediaPipePoseEstimators {
    /**
     * Crea un estimador de pose de MediaPipe (`PoseLandmarker`) en [mode] (`VIDEO` o `IMAGE`) con [model].
     *
     * Con [preferGpu] pide el delegado de GPU, protegido por [GpuProbe]: GLES < 3.1, GPU vetada o una excepción al crear
     * o al detectar la mandan a CPU (el delegado real está en `engine.delegate` y el porqué en [PoseEstimatorReport]).
     * [numPoses] es el máximo de poses por fotograma (2: el atleta y alguien más; el [PoseTrackAssembler] elige).
     *
     * Bloquea mientras carga el modelo: fuera del hilo principal. Todo el trabajo (crear, detectar, cerrar) ocurre en un hilo
     * propio, `vt-pose`. Hay que cerrar el estimador.
     */
    fun create(
        context: Context,
        mode: RunningMode,
        model: PoseModel,
        preferGpu: Boolean = true,
        numPoses: Int = 2,
    ): PoseEstimator {
        require(mode == RunningMode.VIDEO || mode == RunningMode.IMAGE) { "Solo VIDEO o IMAGE, no $mode" }
        require(numPoses >= 1) { "numPoses < 1: $numPoses" }
        val app = context.applicationContext
        val estimator = MediaPipePoseEstimator(app, mode, model, numPoses, if (preferGpu) GpuProbe.get(app) else null)
        try {
            estimator.open()
        } catch (error: Throwable) {
            estimator.close()
            throw error
        }
        return estimator
    }
}

/**
 * `PoseLandmarker` de MediaPipe tras [PoseEstimator]. Todo ocurre en el hilo `vt-pose` (un ejecutor de un solo hilo): el
 * delegado de GPU ata su contexto GL al hilo que crea el landmarker, y detectar o cerrar desde otro lo rompe.
 *
 * - En VIDEO el tiempo es `max(timestampMs, último + 1)` (y no menor que 0): MediaPipe exige que crezca estrictamente.
 * - La imagen entra como `ByteBufferImageBuilder` RGBA sobre el buffer directo del fotograma, sin copiar. Si la tarea lo
 *   rechaza (excepción en la 1.ª detección), pasa a un `Bitmap` ARGB_8888 reutilizado y se queda con él.
 * - NUNCA se llama a `MPImage.close()`: con un Bitmap recicla el bitmap compartido.
 * - El modelo «heavy» entra como `MappedByteBuffer`: MediaPipe guarda su DIRECCIÓN, así que el buffer vive tanto como el landmarker.
 */
internal class MediaPipePoseEstimator(
    private val context: Context,
    private val mode: RunningMode,
    private val model: PoseModel,
    private val numPoses: Int,
    private val probe: GpuProbe?,
) : PoseEstimator, PoseEstimatorReport {
    @Volatile
    private var poseThread: Thread? = null

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, THREAD_NAME).apply { isDaemon = true }.also { poseThread = it }
    }

    private val modelName = if (model is PoseModel.HeavyFromFile) "heavy" else "full"

    @Volatile
    private var delegateName = "CPU"

    @Volatile
    private var note: String? = null

    @Volatile
    private var closed = false

    // Lo que sigue solo se toca desde vt-pose.
    private var landmarker: PoseLandmarker? = null
    private var modelBuffer: MappedByteBuffer? = null
    private var onGpu = false
    private var gpuAttemptOpen = false
    private var lastTimestampMs = Long.MIN_VALUE
    private var bufferPathWorks = false
    private var useBitmap = false
    private var bitmap: Bitmap? = null

    override val engine: PoseEngineInfo get() = PoseEngineInfo(ENGINE_ID, modelName, delegateName)

    override val delegateNote: String? get() = note

    /** Crea el landmarker en `vt-pose`: GPU si se pidió y la sonda lo deja; si no, CPU. Lanza si ni la CPU puede. */
    fun open() {
        onPoseThread {
            if (probe != null) {
                val decision = probe.beginGpuAttempt()
                if (decision.useGpu) {
                    try {
                        landmarker = createLandmarker(Delegate.GPU)
                        onGpu = true
                        gpuAttemptOpen = true
                        delegateName = "GPU"
                    } catch (error: Throwable) {
                        // También un LinkageError (librería nativa de GPU que falta): se cierra el intento y se sigue en CPU.
                        probe.abandonGpuAttempt()
                        if (error is VirtualMachineError) throw error
                        note = "GPU: ${describe(error)}"
                        Log.w(TAG, "No se pudo crear con GPU, se usa CPU: ${describe(error)}")
                    }
                } else {
                    note = decision.reason
                }
            }
            if (landmarker == null) {
                landmarker = createLandmarker(Delegate.CPU)
                delegateName = "CPU"
            }
            Log.i(TAG, "PoseLandmarker listo: modelo=$modelName delegado=$delegateName modo=$mode" + (note?.let { " ($it)" } ?: ""))
        }
    }

    override fun estimate(frame: RgbaFrame, timestampMs: Long): List<DetectedPose> {
        check(!closed) { "El estimador está cerrado" }
        require(frame.width > 0 && frame.height > 0) { "Fotograma de ${frame.width}x${frame.height}" }
        require(frame.buffer.isDirect && frame.buffer.capacity() >= frame.width * frame.height * 4) {
            "El buffer RGBA ha de ser directo y de ${frame.width}x${frame.height}x4 bytes"
        }
        return onPoseThread { detect(frame, timestampMs) }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (executor.isShutdown) return
        try {
            executor.submit(
                Runnable {
                    try {
                        landmarker?.close()
                    } catch (error: Exception) {
                        Log.w(TAG, "Error al cerrar el landmarker: ${describe(error)}")
                    }
                    landmarker = null
                    modelBuffer = null
                    bitmap?.recycle()
                    bitmap = null
                    if (gpuAttemptOpen) {
                        gpuAttemptOpen = false
                        probe?.abandonGpuAttempt()
                    }
                },
            ).get(CLOSE_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo cerrar limpiamente: ${describe(error)}")
        } finally {
            executor.shutdownNow()
        }
    }

    // --- En vt-pose ---

    private fun detect(frame: RgbaFrame, timestampMs: Long): List<DetectedPose> {
        check(!closed) { "El estimador está cerrado" }
        val requested = nextTimestamp(timestampMs)
        val result = try {
            detectFrame(frame, requested)
        } catch (error: Throwable) {
            if (!onGpu || error is VirtualMachineError) throw error
            fallBackToCpu(error)
            detectFrame(frame, nextTimestamp(requested))
        }
        if (gpuAttemptOpen) {
            gpuAttemptOpen = false
            probe?.confirmGpuWorks()
        }
        return convert(result)
    }

    /** `max(timestampMs, último + 1)` en VIDEO (y nunca negativo); en IMAGE no hay tiempo que cuidar. */
    private fun nextTimestamp(timestampMs: Long): Long {
        if (mode != RunningMode.VIDEO) return timestampMs
        val wanted = if (timestampMs > 0L) timestampMs else 0L
        val next = if (wanted > lastTimestampMs) wanted else lastTimestampMs + 1
        lastTimestampMs = next
        return next
    }

    /** Detecta con la imagen como buffer y, si la tarea lo rechaza la 1.ª vez, con un Bitmap reutilizado. */
    private fun detectFrame(frame: RgbaFrame, timestampMs: Long): PoseLandmarkerResult {
        val task = checkNotNull(landmarker) { "Sin landmarker" }
        if (useBitmap) return invokeTask(task, bitmapImage(frame), timestampMs)
        try {
            return invokeTask(task, bufferImage(frame), timestampMs).also { bufferPathWorks = true }
        } catch (error: Exception) {
            // Si el buffer ya funcionó alguna vez, el fallo es de otra cosa (p. ej. la GPU): sube tal cual.
            if (bufferPathWorks) throw error
            Log.w(TAG, "La tarea rechaza el buffer RGBA, se prueba con un Bitmap: ${describe(error)}")
            try {
                val result = invokeTask(task, bitmapImage(frame), nextTimestamp(timestampMs))
                useBitmap = true
                return result
            } catch (second: Exception) {
                second.addSuppressed(error)
                throw second
            }
        }
    }

    private fun invokeTask(task: PoseLandmarker, image: MPImage, timestampMs: Long): PoseLandmarkerResult =
        if (mode == RunningMode.VIDEO) task.detectForVideo(image, timestampMs) else task.detect(image)

    /** El buffer del fotograma como imagen, sin copiar (con la capacidad exacta que MediaPipe exige). */
    private fun bufferImage(frame: RgbaFrame): MPImage {
        val needed = frame.width * frame.height * 4
        val buffer = if (frame.buffer.capacity() == needed) {
            frame.buffer
        } else {
            frame.buffer.duplicate().apply {
                clear()
                limit(needed)
            }.slice()
        }
        return ByteBufferImageBuilder(buffer, frame.width, frame.height, MPImage.IMAGE_FORMAT_RGBA).build()
    }

    /** Respaldo: el mismo fotograma en un Bitmap ARGB_8888 que se reutiliza mientras no cambie el tamaño. */
    private fun bitmapImage(frame: RgbaFrame): MPImage {
        val current = bitmap
        val target = if (current != null && current.width == frame.width && current.height == frame.height) {
            current
        } else {
            current?.recycle()
            Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888).also { bitmap = it }
        }
        frame.buffer.rewind()
        target.copyPixelsFromBuffer(frame.buffer)
        frame.buffer.rewind()
        return BitmapImageBuilder(target).build()
    }

    /** La GPU lanzó una excepción: se cierra el landmarker, se anota y se recrea en CPU (sin vetar: el proceso sigue vivo). */
    private fun fallBackToCpu(error: Throwable) {
        Log.w(TAG, "La GPU falló al detectar, se pasa a CPU: ${describe(error)}")
        try {
            landmarker?.close()
        } catch (closing: Exception) {
            Log.w(TAG, "Error al cerrar el landmarker de GPU: ${describe(closing)}")
        }
        landmarker = null
        if (gpuAttemptOpen) {
            gpuAttemptOpen = false
            probe?.abandonGpuAttempt()
        }
        onGpu = false
        note = "GPU: ${describe(error)}"
        landmarker = createLandmarker(Delegate.CPU)
        delegateName = "CPU"
        // Un landmarker nuevo no ha visto el buffer: si el Bitmap era el camino bueno, sigue siéndolo.
    }

    private fun createLandmarker(delegate: Delegate): PoseLandmarker {
        val base = BaseOptions.builder().apply {
            when (model) {
                PoseModel.Full -> setModelAssetPath(FULL_ASSET)
                is PoseModel.HeavyFromFile -> setModelAssetBuffer(mappedModel(model.path))
            }
            setDelegate(delegate)
        }.build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(mode)
            .setNumPoses(numPoses)
            .setMinPoseDetectionConfidence(CONFIDENCE)
            .setMinPosePresenceConfidence(CONFIDENCE)
            .setMinTrackingConfidence(CONFIDENCE)
            .setOutputSegmentationMasks(false)
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    /** El archivo del modelo mapeado en memoria (solo lectura), que se conserva en [modelBuffer]. */
    private fun mappedModel(path: String): MappedByteBuffer {
        modelBuffer?.let { return it }
        val file = File(path)
        require(file.isFile && file.length() > 0L) { "No existe el modelo: $path" }
        val mapped = RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0L, it.length()) }
        modelBuffer = mapped
        return mapped
    }

    private fun convert(result: PoseLandmarkerResult): List<DetectedPose> {
        val landmarks = result.landmarks()
        if (landmarks.isEmpty()) return emptyList()
        val worlds = result.worldLandmarks()
        val poses = ArrayList<DetectedPose>(landmarks.size)
        for (i in landmarks.indices) {
            val points = landmarks[i]
            val image = ArrayList<LandmarkSample>(points.size)
            for (p in points) {
                image.add(LandmarkSample(p.x(), p.y(), p.z(), p.visibility().orElse(0f), p.presence().orElse(0f)))
            }
            val worldPoints = worlds.getOrNull(i)
            val world = ArrayList<WorldSample>(worldPoints?.size ?: 0)
            if (worldPoints != null) for (p in worldPoints) world.add(WorldSample(p.x(), p.y(), p.z()))
            poses.add(DetectedPose(image, world))
        }
        return poses
    }

    /** Ejecuta [block] en `vt-pose` y espera su resultado. Desde `vt-pose` mismo, directo. */
    private fun <T> onPoseThread(block: () -> T): T {
        if (Thread.currentThread() === poseThread) return block()
        val future = try {
            executor.submit(Callable { block() })
        } catch (rejected: RejectedExecutionException) {
            throw IllegalStateException("El estimador está cerrado", rejected)
        }
        try {
            return future.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    private fun describe(error: Throwable): String = "${error.javaClass.simpleName}: ${error.message}"

    private companion object {
        const val TAG = "VtPose"
        const val THREAD_NAME = "vt-pose"
        const val ENGINE_ID = "mediapipe-pose"
        const val FULL_ASSET = "mediapipe/pose_landmarker_full.task"
        const val CONFIDENCE = 0.5f
        const val CLOSE_TIMEOUT_S = 10L
    }
}
