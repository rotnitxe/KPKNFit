package com.example.kpkn.data.media.video

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

private const val TAG = "KpknVideoGl"
private const val DEQUEUE_TIMEOUT_US = 10_000L
private const val FRAME_TIMEOUT_MS = 2_500L
private const val WAIT_SLICE_MS = 50L
private const val STALL_LIMIT_MS = 10_000L
private const val MAX_RESTARTS = 2
private const val MAX_TRANSIENT_STREAK = 100
private const val TRANSIENT_BACKOFF_MS = 10L
private const val PROBE_TIMEOUT_S = 20L

/**
 * Decodifica TODOS los fotogramas con el patrón «ExtractMpegFrames» de bigflake: `MediaCodec` escribe en la `Surface` de un
 * `SurfaceTexture`, un contexto EGL (pbuffer 1×1, GLES 2.0) dibuja esa textura OES en un FBO del tamaño de salida con el giro
 * en la matriz del vértice y `glReadPixels` lee RGBA con las filas de arriba abajo. Tiempos = los `presentationTimeUs` del códec.
 *
 * Todo lo de GL y el códec vive en un hilo propio (`vt-decode`) y se libera en ese mismo hilo, también al cancelar o fallar.
 * Un fallo no transitorio del códec reinicia la sesión desde el siguiente fotograma (como mucho [MAX_RESTARTS] veces); luego
 * lanza [VideoDecodeException], que [VideoFrameSources.open] convierte en el respaldo.
 */
class GlVideoFrameSource private constructor(
    private val file: File,
    override val info: VideoInfo,
) : VideoFrameSource, VideoDecodeReport {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vt-decode").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val busy = AtomicBoolean(false)

    @Volatile
    private var closed = false

    @Volatile
    private var lastDetails = ""

    override val path: VideoDecodePath get() = VideoDecodePath.GL
    override val fallbackReason: String? get() = null
    override val details: String get() = lastDetails

    override suspend fun decode(
        fromUs: Long,
        targetLongEdge: Int,
        needs: (index: Int, ptsUs: Long) -> FrameNeeds,
        onFrame: suspend (DecodedFrame) -> Unit,
    ) {
        check(!closed) { "La fuente de vídeo está cerrada" }
        check(busy.compareAndSet(false, true)) { "Ya hay una decodificación en curso en esta fuente" }
        try {
            withContext(dispatcher) {
                val run = GlDecodeRun(file, info, fromUs.coerceAtLeast(0L), targetLongEdge, needs, onFrame, currentCoroutineContext().job)
                try {
                    run.run()
                } finally {
                    lastDetails = run.details
                }
            }
        } finally {
            busy.set(false)
        }
    }

    override fun close() {
        closed = true
        dispatcher.close()
    }

    companion object {
        /**
         * Abre [file] por el camino GL: comprueba, en el hilo de decodificación, que hay EGL, el shader OES y un decodificador
         * para el mime. Lanza [VideoDecodeException] si algo de eso falta (entonces toca el respaldo).
         */
        fun open(file: File, info: VideoInfo): GlVideoFrameSource {
            val source = GlVideoFrameSource(file, info)
            try {
                source.executor.submit(Callable { probeGl(info) }).get(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                source.close()
                val root = (failure as? ExecutionException)?.cause ?: failure
                if (root is VideoDecodeException) throw root
                throw VideoDecodeException("GL no está disponible: ${root.javaClass.simpleName}: ${root.message}", root)
            }
            return source
        }

        private fun probeGl(info: VideoInfo) {
            val egl = EglCore()
            try {
                OesDrawer(16, 16).release()
            } finally {
                egl.release()
            }
            val format = MediaFormat.createVideoFormat(info.mime, info.codedWidth, info.codedHeight)
            info.nominalFps?.let { format.setInteger(MediaFormat.KEY_FRAME_RATE, Math.round(it)) }
            if (MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) == null) {
                throw VideoDecodeException("No hay decodificador para ${info.mime} ${info.codedWidth}x${info.codedHeight}")
            }
        }
    }
}

/** Fallo del códec que justifica reiniciar la sesión (no transitorio, sin salida, o el fotograma no llegó a la textura). */
private class CodecFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Una decodificación completa: crea EGL, el dibujante y el lector, y encadena sesiones de códec hasta el final del vídeo. */
private class GlDecodeRun(
    private val file: File,
    private val info: VideoInfo,
    private val fromUs: Long,
    targetLongEdge: Int,
    private val needs: (index: Int, ptsUs: Long) -> FrameNeeds,
    private val onFrame: suspend (DecodedFrame) -> Unit,
    private val job: Job,
) {
    private val out = VideoFrameMath.outputSize(info.displayWidth, info.displayHeight, targetLongEdge)
    private val texMatrix = FloatArray(16)
    private var mvp = VideoFrameMath.vertexMatrix(info.rotationDegrees, flipY = true)
    private var rotationResolved = false
    private var bakedRotation = 0
    private var egl: EglCore? = null
    private var drawer: OesDrawer? = null
    private var reader: PixelReader? = null
    private var nextIndex = 0
    private var consumedPtsUs = Long.MIN_VALUE
    private var restarts = 0
    private var decoderName = ""
    private var rendererName = ""

    val details: String
        get() = "decoder=$decoderName gl=$rendererName out=${out.width}x${out.height} bakedRotation=$bakedRotation restarts=$restarts"

    suspend fun run() {
        try {
            val core = EglCore().also { egl = it }
            rendererName = core.renderer
            drawer = OesDrawer(out.width, out.height)
            reader = PixelReader(out.width, out.height)
            var resumeUs = fromUs
            while (true) {
                try {
                    decodeSession(resumeUs)
                    return
                } catch (failure: CodecFailure) {
                    restarts++
                    Log.w(TAG, "Fallo del decodificador ($restarts): ${failure.message}")
                    if (restarts > MAX_RESTARTS) {
                        throw VideoDecodeException("El decodificador falló de forma repetida: ${failure.message}", failure)
                    }
                    if (consumedPtsUs != Long.MIN_VALUE) resumeUs = maxOf(fromUs, consumedPtsUs + 1)
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (consumer: ConsumerFailure) {
            throw consumer.cause ?: consumer
        } catch (failure: VideoDecodeException) {
            throw withProgress(failure)
        } catch (error: Exception) {
            throw withProgress(
                VideoDecodeException("Fallo al decodificar ${file.name}: ${error.javaClass.simpleName}: ${error.message}", error),
            )
        } finally {
            release()
        }
    }

    /** Anota en el fallo hasta dónde se había llegado (fotogramas ya entregados o saltados) para que el respaldo siga de ahí. */
    private fun withProgress(failure: VideoDecodeException): VideoDecodeException {
        failure.consumedPtsUs = consumedPtsUs
        failure.nextIndex = nextIndex
        return failure
    }

    /** Todo lo de GL se libera aquí, en el hilo que lo creó. */
    private fun release() {
        runCatching { drawer?.release() }
        drawer = null
        reader = null
        runCatching { egl?.release() }
        egl = null
    }

    /** Una sesión de códec: abre extractor, `SurfaceTexture` y decodificador desde [startUs] y bombea hasta el fin del vídeo. */
    private suspend fun decodeSession(startUs: Long) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var frames: FrameSurface? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = VideoInfoProbe.findVideoTrack(extractor)
            if (track < 0) throw VideoDecodeException("Sin pista de vídeo en ${file.name}")
            extractor.selectTrack(track)
            if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val surface = FrameSurface(checkNotNull(drawer).oesTexture)
            frames = surface
            val decoder = openCodec(extractor, track, surface.surface)
            codec = decoder
            decoder.start()
            pump(extractor, decoder, surface, startUs)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (codecError: MediaCodec.CodecException) {
            throw CodecFailure("CodecException ${codecError.errorCode}: ${codecError.diagnosticInfo}", codecError)
        } catch (state: IllegalStateException) {
            throw CodecFailure("Códec en un estado no válido: ${state.message}", state)
        } finally {
            codec?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            frames?.release()
            runCatching { extractor.release() }
        }
    }

    /**
     * El formato de la pista con lo que hace falta. [level] 0: sin giro del códec, sin tiempo real y a máxima velocidad;
     * 1: sin la velocidad máxima; 2: solo sin giro del códec (último recurso).
     */
    private fun buildFormat(extractor: MediaExtractor, track: Int, level: Int): MediaFormat {
        val format = extractor.getTrackFormat(track)
        format.setInteger(MediaFormat.KEY_ROTATION, 0)
        if (level <= 1) format.setInteger(MediaFormat.KEY_PRIORITY, 1)
        if (level == 0) format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
        if (info.isHdr && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            format.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        return format
    }

    /**
     * Crea y configura el decodificador: el que `DecoderChooser` elige por tamaño y fps (hardware primero) y, si no hay o falla,
     * el de por defecto. Con `KEY_OPERATING_RATE`; si `configure` falla con él, de nuevo sin él y, al final, sin `KEY_PRIORITY`.
     */
    private fun openCodec(extractor: MediaExtractor, track: Int, surface: Surface): MediaCodec {
        val chosen = DecoderChooser.pick(info.mime, info.codedWidth, info.codedHeight, (info.nominalFps ?: 30f).toDouble())
        val attempts = ArrayList<Pair<String?, Int>>(4)
        attempts.add(chosen to 0)
        attempts.add(chosen to 1)
        if (chosen != null) attempts.add(null to 1)
        attempts.add(null to 2)
        var lastError: Exception? = null
        for ((name, level) in attempts) {
            val format = buildFormat(extractor, track, level)
            var codec: MediaCodec? = null
            try {
                val created = if (name != null) MediaCodec.createByCodecName(name) else MediaCodec.createDecoderByType(info.mime)
                codec = created
                created.configure(format, surface, null, 0)
                decoderName = created.name
                return created
            } catch (error: Exception) {
                lastError = error
                runCatching { codec?.release() }
            }
        }
        throw VideoDecodeException("No se pudo configurar un decodificador de ${info.mime}: ${lastError?.message}", lastError)
    }

    /** El bucle de entrada y salida del códec. Termina con el fotograma marcado como fin de flujo. */
    private suspend fun pump(extractor: MediaExtractor, codec: MediaCodec, frames: FrameSurface, startUs: Long) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var previousPtsUs = Long.MIN_VALUE
        var progressAtNs = System.nanoTime()
        var transientStreak = 0
        var inOutput = false
        while (!outputDone) {
            job.ensureActive()
            try {
                // Entrada: llena todos los buffers libres, sin esperar.
                while (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(0L)
                    if (inputIndex < 0) break
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: throw CodecFailure("Buffer de entrada nulo")
                    val size = extractor.readSampleData(inputBuffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                // Salida: un buffer por vuelta; aquí es donde se espera al códec.
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if ((System.nanoTime() - progressAtNs) / 1_000_000L > STALL_LIMIT_MS) {
                        throw CodecFailure("El decodificador no produce fotogramas")
                    }
                } else if (outputIndex >= 0) {
                    progressAtNs = System.nanoTime()
                    val endOfStream = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (bufferInfo.size > 0) {
                        inOutput = true
                        previousPtsUs = output(codec, outputIndex, bufferInfo.presentationTimeUs, previousPtsUs, frames, startUs)
                        inOutput = false
                        // Lo que tarde el consumidor en `onFrame` (backpressure, análisis en pausa) no cuenta como códec atascado.
                        progressAtNs = System.nanoTime()
                    } else {
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    if (endOfStream) outputDone = true
                } else {
                    progressAtNs = System.nanoTime() // cambio de formato o de buffers: el códec sigue vivo
                }
                transientStreak = 0
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (consumer: ConsumerFailure) {
                throw consumer
            } catch (failure: CodecFailure) {
                throw failure
            } catch (codecError: MediaCodec.CodecException) {
                // Un fallo transitorio (recursos ocupados) se reintenta; en medio de un fotograma no, para no perderlo.
                if (codecError.isTransient && !inOutput && ++transientStreak <= MAX_TRANSIENT_STREAK) {
                    Thread.sleep(TRANSIENT_BACKOFF_MS)
                } else {
                    throw CodecFailure("CodecException ${codecError.errorCode}: ${codecError.diagnosticInfo}", codecError)
                }
            } catch (state: IllegalStateException) {
                throw CodecFailure("Códec en un estado no válido: ${state.message}", state)
            }
        }
    }

    /**
     * Un fotograma decodificado: lo descarta si es anterior a [startUs], lo salta si `needs` dice SKIP y, si no, lo envía a la
     * textura, lo dibuja, lo lee y lo entrega. Devuelve el PTS (reparado) para encadenar el siguiente.
     */
    private suspend fun output(
        codec: MediaCodec,
        outputIndex: Int,
        rawPtsUs: Long,
        previousPtsUs: Long,
        frames: FrameSurface,
        startUs: Long,
    ): Long {
        val ptsUs = VideoFrameMath.repairedPts(rawPtsUs, previousPtsUs)
        if (ptsUs < startUs) {
            codec.releaseOutputBuffer(outputIndex, false)
            return ptsUs
        }
        val index = nextIndex
        val need = try {
            needs(index, ptsUs)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            throw ConsumerFailure(error)
        }
        if (need == FrameNeeds.SKIP) {
            codec.releaseOutputBuffer(outputIndex, false)
            nextIndex = index + 1
            consumedPtsUs = ptsUs
            return ptsUs
        }
        frames.arm()
        codec.releaseOutputBuffer(outputIndex, true)
        if (!frames.await(FRAME_TIMEOUT_MS, job)) {
            throw CodecFailure("El fotograma no llegó a la textura en $FRAME_TIMEOUT_MS ms")
        }
        frames.texture.updateTexImage()
        frames.texture.getTransformMatrix(texMatrix)
        resolveRotation()
        val wantsRgba = need == FrameNeeds.RGBA || need == FrameNeeds.RGBA_AND_LUMA
        val wantsLuma = need == FrameNeeds.LUMA || need == FrameNeeds.RGBA_AND_LUMA
        checkNotNull(drawer).draw(texMatrix, mvp)
        val pixels = checkNotNull(reader)
        val slot = pixels.read(wantsLuma)
        checkGl("dibujar y leer el fotograma $index")
        nextIndex = index + 1
        consumedPtsUs = ptsUs
        val frame = DecodedFrame(
            index = index,
            ptsUs = ptsUs,
            width = out.width,
            height = out.height,
            rgba = if (wantsRgba) pixels.rgba(slot) else null,
            luma = if (wantsLuma) pixels.luma(slot) else null,
        )
        try {
            onFrame(frame)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            throw ConsumerFailure(error)
        }
        return ptsUs
    }

    /** Con el primer fotograma se ve si el códec incorporó el giro a pesar de pedirle 0; la matriz del vértice lo descuenta. */
    private fun resolveRotation() {
        if (rotationResolved) return
        rotationResolved = true
        bakedRotation = VideoFrameMath.bakedRotationOf(texMatrix)
        mvp = VideoFrameMath.vertexMatrix(info.rotationDegrees - bakedRotation, flipY = true)
    }
}

/** Elige el decodificador por tamaño y fps con `MediaCodecList` (hardware primero), p. ej. para 4K a 60. */
private object DecoderChooser {
    fun pick(mime: String, width: Int, height: Int, fps: Double): String? {
        val candidates = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { codec ->
                !codec.isEncoder && !codec.name.endsWith(".secure") && codec.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        } catch (_: Exception) {
            return null
        }
        val capable = candidates.filter { codec ->
            try {
                codec.getCapabilitiesForType(mime).videoCapabilities?.areSizeAndRateSupported(width, height, fps) == true
            } catch (_: Exception) {
                false
            }
        }
        if (capable.isEmpty()) return null
        return (capable.firstOrNull { isHardware(it) } ?: capable.first()).name
    }

    private fun isHardware(codec: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return codec.isHardwareAccelerated && !codec.isSoftwareOnly
        val name = codec.name.lowercase()
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw."))
    }
}

/** La `Surface` del decodificador: un `SurfaceTexture` cuyos avisos de fotograma llegan en un hilo propio. */
private class FrameSurface(textureId: Int) {
    private val thread = HandlerThread("vt-frames").apply { start() }
    private val signal = Semaphore(0)
    val texture = SurfaceTexture(textureId)
    val surface: Surface

    init {
        texture.setOnFrameAvailableListener({ signal.release() }, Handler(thread.looper))
        surface = Surface(texture)
    }

    /** Se llama justo antes de soltar un buffer con `render = true`: borra avisos viejos. */
    fun arm() {
        signal.drainPermits()
    }

    /** Espera el aviso de un fotograma nuevo, en tramos cortos para poder cancelar. `false` si no llega en [timeoutMs]. */
    fun await(timeoutMs: Long, job: Job): Boolean {
        var remaining = timeoutMs
        while (remaining > 0L) {
            job.ensureActive()
            val slice = minOf(remaining, WAIT_SLICE_MS)
            if (signal.tryAcquire(slice, TimeUnit.MILLISECONDS)) return true
            remaining -= slice
        }
        return false
    }

    fun release() {
        runCatching { surface.release() }
        runCatching { texture.release() }
        thread.quitSafely()
    }
}

/** El contexto EGL de la decodificación: pantalla por defecto, GLES 2.0 y un pbuffer de 1×1 que queda activo en este hilo. */
private class EglCore {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var initialized = false

    /** El `GL_RENDERER` del contexto, para depurar. */
    var renderer = ""
        private set

    init {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) throw VideoDecodeException("EGL: no hay pantalla por defecto")
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw VideoDecodeException("EGL: eglInitialize falló")
            initialized = true
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] < 1) {
                throw VideoDecodeException("EGL: no hay configuración RGBA8 para GLES 2.0")
            }
            val config = configs[0] ?: throw VideoDecodeException("EGL: configuración nula")
            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
            if (context == EGL14.EGL_NO_CONTEXT) throw VideoDecodeException("EGL: eglCreateContext falló (0x${Integer.toHexString(EGL14.eglGetError())})")
            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                0,
            )
            if (surface == EGL14.EGL_NO_SURFACE) throw VideoDecodeException("EGL: eglCreatePbufferSurface falló (0x${Integer.toHexString(EGL14.eglGetError())})")
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                throw VideoDecodeException("EGL: eglMakeCurrent falló (0x${Integer.toHexString(EGL14.eglGetError())})")
            }
            renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        } catch (failure: Throwable) {
            release()
            throw failure
        }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            if (initialized) EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
        initialized = false
    }
}

private const val VERTEX_SHADER = """
uniform mat4 uMVPMatrix;
uniform mat4 uTexMatrix;
attribute vec4 aPosition;
attribute vec4 aTextureCoord;
varying vec2 vTextureCoord;
void main() {
    gl_Position = uMVPMatrix * aPosition;
    vTextureCoord = (uTexMatrix * aTextureCoord).xy;
}
"""

private const val FRAGMENT_SHADER = """
#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
void main() {
    gl_FragColor = texture2D(sTexture, vTextureCoord);
}
"""

/** Posición (x, y) y coordenada de textura (s, t) de las cuatro esquinas, en tira de triángulos. */
private val QUAD = floatArrayOf(
    -1f, -1f, 0f, 0f,
    1f, -1f, 1f, 0f,
    -1f, 1f, 0f, 1f,
    1f, 1f, 1f, 1f,
)

private fun checkGl(what: String) {
    val error = GLES20.glGetError()
    if (error != GLES20.GL_NO_ERROR) throw VideoDecodeException("Error de GL 0x${Integer.toHexString(error)} al $what")
}

private fun compileShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES20.glGetShaderInfoLog(shader)
        GLES20.glDeleteShader(shader)
        throw VideoDecodeException("Shader no válido: $log")
    }
    return shader
}

private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
    val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    val fragment = try {
        compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    } catch (failure: Throwable) {
        GLES20.glDeleteShader(vertex)
        throw failure
    }
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertex)
    GLES20.glAttachShader(program, fragment)
    GLES20.glLinkProgram(program)
    GLES20.glDeleteShader(vertex)
    GLES20.glDeleteShader(fragment)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES20.glGetProgramInfoLog(program)
        GLES20.glDeleteProgram(program)
        throw VideoDecodeException("Programa GL no válido: $log")
    }
    return program
}

/**
 * El dibujante OES → FBO: pinta la textura externa del decodificador, con su matriz de textura y la matriz del vértice,
 * en una textura RGBA de [width]×[height] ligada a un FBO que queda activo mientras dure el contexto.
 */
private class OesDrawer(private val width: Int, private val height: Int) {
    /** La textura externa a la que se ata el `SurfaceTexture`. */
    var oesTexture = 0
        private set

    private var colorTexture = 0
    private var framebuffer = 0
    private var program = 0
    private var uMvp = -1
    private var uTexMatrix = -1
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(QUAD.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(QUAD)
        position(0)
    }

    init {
        try {
            setUp()
        } catch (failure: Throwable) {
            release()
            throw failure
        }
    }

    private fun setUp() {
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val coord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        val sampler = GLES20.glGetUniformLocation(program, "sTexture")
        uMvp = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        if (position < 0 || coord < 0 || sampler < 0 || uMvp < 0 || uTexMatrix < 0) {
            throw VideoDecodeException("El shader no tiene las variables esperadas")
        }
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        oesTexture = textures[0]
        colorTexture = textures[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTexture)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val buffers = IntArray(1)
        GLES20.glGenFramebuffers(1, buffers, 0)
        framebuffer = buffers[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, colorTexture, 0)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw VideoDecodeException("Framebuffer incompleto (0x${Integer.toHexString(status)}) de ${width}x$height")
        }
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        quad.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(position)
        quad.position(2)
        GLES20.glVertexAttribPointer(coord, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(coord)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(sampler, 0)
        checkGl("preparar el dibujante")
    }

    /** Dibuja el fotograma actual de la textura externa en el FBO: [texMatrix] es la del `SurfaceTexture`, [mvp] la del vértice. */
    fun draw(texMatrix: FloatArray, mvp: FloatArray) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            framebuffer = 0
        }
        if (oesTexture != 0 || colorTexture != 0) {
            GLES20.glDeleteTextures(2, intArrayOf(oesTexture, colorTexture), 0)
            oesTexture = 0
            colorTexture = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }
}

/** El lector: `glReadPixels` del FBO a un hueco del pool (filas de arriba abajo) y, si se pide, su luma. */
private class PixelReader(private val width: Int, private val height: Int) {
    private val pool = FramePool(width, height)

    /** Lee el FBO a un hueco nuevo del pool y devuelve su número. */
    fun read(withLuma: Boolean): Int {
        val slot = pool.nextSlot()
        val buffer = pool.rgba(slot)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        buffer.clear()
        if (withLuma) pool.fillLuma(slot)
        return slot
    }

    fun rgba(slot: Int): ByteBuffer = pool.rgba(slot)

    fun luma(slot: Int): ByteArray = pool.luma(slot)
}
