package com.example.kpkn.debug

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.PI
import kotlin.math.sin

/**
 * SOLO DEPURACIÓN (no se integra): genera clips AVC de 320×240 con un disco blanco de radio 20 sobre negro que recorre una
 * trayectoria conocida ([diskX], [diskY]), como en «EncodeAndMuxTest» de bigflake: `MediaCodec` con entrada por `Surface`, el
 * disco dibujado con GLES y el PTS de cada fotograma fijado con `eglPresentationTimeANDROID`, y `MediaMuxer` a MP4.
 */
object SyntheticClipEncoder {
    const val WIDTH = 320
    const val HEIGHT = 240
    const val DISK_RADIUS = 20f
    private const val MIME = "video/avc"
    private const val BIT_RATE = 2_000_000
    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val DRAIN_LIMIT_MS = 10_000L
    private const val ENCODE_LIMIT_S = 45L
    private const val EGL_RECORDABLE_ANDROID = 0x3142

    /** Un clip por generar: [orientationHint] es el giro del contenedor y [variableRate] alterna saltos de 25 y 41 ms. */
    class Spec(val name: String, val fps: Int, val seconds: Int, val orientationHint: Int, val variableRate: Boolean)

    /** El clip escrito: [ptsUs] son los PTS que recibió el muxer, ordenados. */
    class Clip(val file: File, val spec: Spec, val ptsUs: LongArray)

    /** X del centro del disco en el fotograma codificado (píxeles continuos, origen arriba a la izquierda) en el instante [tUs]. */
    fun diskX(tUs: Long): Double = WIDTH / 2.0 + 0.30 * WIDTH * sin(2 * PI * 0.5 * tUs / 1e6)

    /** Y del centro del disco en el fotograma codificado (hacia abajo) en el instante [tUs]. */
    fun diskY(tUs: Long): Double = HEIGHT / 2.0 + 0.25 * HEIGHT * sin(2 * PI * 0.8 * tUs / 1e6 + 0.7)

    /** PTS (µs) que se piden al codificador: constantes a [Spec.fps] o, en VFR, con saltos alternos de 25 y 41 ms. */
    fun presentationTimes(spec: Spec): LongArray {
        if (!spec.variableRate) return LongArray(spec.seconds * spec.fps) { it * 1_000_000L / spec.fps }
        val times = ArrayList<Long>()
        var t = 0L
        var i = 0
        while (t < spec.seconds * 1_000_000L) {
            times.add(t)
            t += if (i % 2 == 0) 25_000L else 41_000L
            i++
        }
        return times.toLongArray()
    }

    /** Como [encode], pero en un hilo aparte y con tiempo máximo: un codificador atascado no cuelga el banco. */
    fun encodeWithTimeout(file: File, spec: Spec): Clip {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "vt-encode").apply { isDaemon = true } }
        try {
            val future = executor.submit(Callable { encode(file, spec) })
            try {
                return future.get(ENCODE_LIMIT_S, TimeUnit.SECONDS)
            } catch (timeout: TimeoutException) {
                future.cancel(true)
                error("El codificador no terminó ${spec.name} en $ENCODE_LIMIT_S s")
            }
        } finally {
            executor.shutdown()
        }
    }

    fun encode(file: File, spec: Spec): Clip {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val times = presentationTimes(spec)
        val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, spec.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var egl: EncoderEgl? = null
        var drawer: DiskDrawer? = null
        try {
            val codec = MediaCodec.createEncoderByType(MIME)
            encoder = codec
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            inputSurface = surface
            codec.start()
            val window = EncoderEgl(surface)
            egl = window
            val painter = DiskDrawer()
            drawer = painter
            val output = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = output
            output.setOrientationHint(spec.orientationHint)
            val sink = MuxSink(codec, output)
            for (t in times) {
                sink.drain(false)
                painter.draw(diskX(t), diskY(t))
                window.present(t * 1000L)
            }
            codec.signalEndOfInputStream()
            sink.drain(true)
            if (sink.started) output.stop()
            check(sink.written.isNotEmpty()) { "El codificador no produjo ningún fotograma" }
            val written = sink.written.toLongArray()
            written.sort()
            return Clip(file, spec, written)
        } finally {
            runCatching { drawer?.release() }
            runCatching { egl?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { inputSurface?.release() }
            runCatching { muxer?.release() }
        }
    }

    /** Vacía la salida del codificador hacia el muxer, anotando el PTS de cada muestra escrita. */
    private class MuxSink(private val encoder: MediaCodec, private val muxer: MediaMuxer) {
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        var started = false
            private set
        val written = ArrayList<Long>()

        fun drain(endOfStream: Boolean) {
            var idleSince = System.nanoTime()
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        check((System.nanoTime() - idleSince) / 1_000_000L < DRAIN_LIMIT_MS) { "El codificador no cierra el flujo" }
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!started) { "El formato de salida cambió dos veces" }
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                    }
                    index >= 0 -> {
                        idleSince = System.nanoTime()
                        val data = encoder.getOutputBuffer(index) ?: error("Buffer de salida nulo")
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (info.size > 0) {
                            check(started) { "Datos del codificador antes de su formato" }
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                            written.add(info.presentationTimeUs)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }
        }
    }

    /** EGL con ventana sobre la `Surface` de entrada del codificador (config «grabable»). */
    private class EncoderEgl(inputSurface: Surface) {
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                check(display != EGL14.EGL_NO_DISPLAY) { "EGL: sin pantalla" }
                val version = IntArray(2)
                check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL: eglInitialize falló" }
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                // Primero la config «grabable» y, si el dispositivo no la ofrece, una normal (la ventana es la del codificador igualmente).
                for (recordable in booleanArrayOf(true, false)) {
                    val attributes = intArrayOf(
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                        if (recordable) EGL_RECORDABLE_ANDROID else EGL14.EGL_NONE, 1,
                        EGL14.EGL_NONE,
                    )
                    if (EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) break
                    configs[0] = null
                }
                val config = checkNotNull(configs[0]) { "EGL: sin config para la ventana del codificador" }
                context = EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                    0,
                )
                check(context != EGL14.EGL_NO_CONTEXT) { "EGL: eglCreateContext falló" }
                surface = EGL14.eglCreateWindowSurface(display, config, inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
                check(surface != EGL14.EGL_NO_SURFACE) { "EGL: eglCreateWindowSurface falló" }
                check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL: eglMakeCurrent falló" }
            } catch (failure: Throwable) {
                release()
                throw failure
            }
        }

        /** Entrega el fotograma dibujado al codificador con ese PTS (ns). */
        fun present(presentationNs: Long) {
            EGLExt.eglPresentationTimeANDROID(display, surface, presentationNs)
            check(EGL14.eglSwapBuffers(display, surface)) { "EGL: eglSwapBuffers falló" }
        }

        fun release() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(display)
            }
            display = EGL14.EGL_NO_DISPLAY
            context = EGL14.EGL_NO_CONTEXT
            surface = EGL14.EGL_NO_SURFACE
        }
    }

    /** Dibuja un disco blanco con borde suavizado (cobertura de medio píxel) sobre negro, en coordenadas de píxel. */
    private class DiskDrawer {
        private val program: Int
        private val uCenter: Int
        private val uRadius: Int
        private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

        init {
            program = link(VERTEX, FRAGMENT)
            uCenter = GLES20.glGetUniformLocation(program, "uCenter")
            uRadius = GLES20.glGetUniformLocation(program, "uRadius")
            val position = GLES20.glGetAttribLocation(program, "aPosition")
            check(uCenter >= 0 && uRadius >= 0 && position >= 0) { "El shader del disco no tiene las variables esperadas" }
            GLES20.glUseProgram(program)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 8, quad)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glViewport(0, 0, WIDTH, HEIGHT)
        }

        /** [x] e [y] son del centro en el fotograma (Y hacia abajo); `gl_FragCoord` tiene la Y hacia arriba. */
        fun draw(x: Double, y: Double) {
            GLES20.glUniform2f(uCenter, x.toFloat(), (HEIGHT - y).toFloat())
            GLES20.glUniform1f(uRadius, DISK_RADIUS)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun release() {
            GLES20.glDeleteProgram(program)
        }

        private fun link(vertexSource: String, fragmentSource: String): Int {
            val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val id = GLES20.glCreateProgram()
            GLES20.glAttachShader(id, vertex)
            GLES20.glAttachShader(id, fragment)
            GLES20.glLinkProgram(id)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            val status = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Programa del disco no válido: ${GLES20.glGetProgramInfoLog(id)}" }
            return id
        }

        private fun compile(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val status = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { "Shader del disco no válido: ${GLES20.glGetShaderInfoLog(id)}" }
            return id
        }
    }

    private const val VERTEX = """
attribute vec4 aPosition;
void main() {
    gl_Position = aPosition;
}
"""

    private const val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 uCenter;
uniform float uRadius;
void main() {
    float d = distance(gl_FragCoord.xy, uCenter);
    float a = clamp(uRadius + 0.5 - d, 0.0, 1.0);
    gl_FragColor = vec4(a, a, a, 1.0);
}
"""
}
