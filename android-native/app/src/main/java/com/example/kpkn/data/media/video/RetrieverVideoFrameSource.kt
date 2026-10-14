package com.example.kpkn.data.media.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

private const val DEFAULT_FPS = 30f
private const val MAX_BLOCK = 30
private const val BLOCK_BUDGET_BYTES = 64L * 1024 * 1024
private const val TIMED_MAX_FPS = 15
private const val MAX_CONSECUTIVE_NULLS = 3

/**
 * Respaldo con `MediaMetadataRetriever`: más lento y menos preciso que [GlVideoFrameSource], pero no necesita GL.
 * - Con API 28+, `getFramesAtIndex` en bloques (de hasta 30 fotogramas, menos si el vídeo es grande para no pasar de ~64 MB):
 *   todos los fotogramas, con PTS = índice / fps nominal (no son los reales: en un vídeo VFR se desvían).
 * - Con una API anterior (o si la anterior no sirve para ese archivo), `getScaledFrameAtTime(OPTION_CLOSEST)` a ≤ 15 fps.
 *
 * Los Bitmap pasan a RGBA y luma con los mismos tamaños que GL. La confianza del conteo con este camino no pasa de MEDIA.
 * [openFailure] es el motivo por el que se llegó al respaldo, solo para [VideoDecodeReport.fallbackReason].
 */
class RetrieverVideoFrameSource(
    private val file: File,
    override val info: VideoInfo,
    private val indexed: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
    private val openFailure: String? = null,
) : ResumableVideoFrameSource, VideoDecodeReport {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vt-decode-rt").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val busy = AtomicBoolean(false)

    @Volatile
    private var closed = false

    @Volatile
    private var lastDetails = ""

    override val path: VideoDecodePath get() = VideoDecodePath.RETRIEVER
    override val fallbackReason: String? get() = openFailure
    override val details: String get() = lastDetails

    override suspend fun decode(
        fromUs: Long,
        targetLongEdge: Int,
        needs: (index: Int, ptsUs: Long) -> FrameNeeds,
        onFrame: suspend (DecodedFrame) -> Unit,
    ) = decodeFrom(fromUs, targetLongEdge, needs, onFrame, 0)

    /** Como [decode], pero numerando los fotogramas desde [startIndex]: así sigue la cuenta tras un fallo de GL a mitad. */
    override suspend fun decodeFrom(
        fromUs: Long,
        targetLongEdge: Int,
        needs: (index: Int, ptsUs: Long) -> FrameNeeds,
        onFrame: suspend (DecodedFrame) -> Unit,
        startIndex: Int,
    ) {
        check(!closed) { "La fuente de vídeo está cerrada" }
        check(busy.compareAndSet(false, true)) { "Ya hay una decodificación en curso en esta fuente" }
        try {
            withContext(dispatcher) {
                val run = RetrieverRun(
                    file, info, indexed, fromUs.coerceAtLeast(0L), targetLongEdge, needs, onFrame, currentCoroutineContext().job, startIndex,
                )
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
}

/** Una decodificación con el retriever. Todo (Bitmap, buffers, retriever) vive y muere dentro de [run]. */
private class RetrieverRun(
    private val file: File,
    private val info: VideoInfo,
    private val indexed: Boolean,
    private val fromUs: Long,
    targetLongEdge: Int,
    private val needs: (index: Int, ptsUs: Long) -> FrameNeeds,
    private val onFrame: suspend (DecodedFrame) -> Unit,
    private val job: Job,
    startIndex: Int,
) {
    private val out = VideoFrameMath.outputSize(info.displayWidth, info.displayHeight, targetLongEdge)
    private val pool = FramePool(out.width, out.height)
    private val target = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(target)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var nextIndex = startIndex
    private var mode = ""
    private var sourceSize = ""
    private var rotatedByUs = false

    val details: String
        get() = "retriever mode=$mode source=$sourceSize rotatedByUs=$rotatedByUs out=${out.width}x${out.height}"

    suspend fun run() {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val fps = info.nominalFps ?: info.captureFps ?: DEFAULT_FPS
            val done = indexed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && runIndexed(retriever, fps)
            if (!done) runTimed(retriever, fps)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (consumer: ConsumerFailure) {
            throw consumer.cause ?: consumer
        } catch (failure: VideoDecodeException) {
            throw failure
        } catch (error: Exception) {
            throw VideoDecodeException("Fallo del respaldo con ${file.name}: ${error.javaClass.simpleName}: ${error.message}", error)
        } finally {
            runCatching { retriever.release() }
            target.recycle()
        }
    }

    /** Fotogramas de [fromUs] en adelante por índice. `false` si la API no sirve para este archivo (se pasa al modo por tiempo). */
    private suspend fun runIndexed(retriever: MediaMetadataRetriever, fps: Float): Boolean {
        val reported = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()?.takeIf { it > 0 }
        val frameCount = reported ?: ceil(info.durationUs.toDouble() * fps / 1_000_000.0).toInt()
        val bytesPerFrame = info.displayWidth.toLong() * info.displayHeight * 4
        val block = (BLOCK_BUDGET_BYTES / bytesPerFrame).toInt().coerceIn(1, MAX_BLOCK)
        mode = "indexed block=$block frames=$frameCount"
        var index = VideoFrameMath.firstIndexAtOrAfter(fromUs, fps)
        var first = true
        while (index < frameCount) {
            job.ensureActive()
            val count = min(block, frameCount - index)
            val bitmaps = try {
                retriever.getFramesAtIndex(index, count)
            } catch (error: Exception) {
                if (first) return false
                break
            }
            if (bitmaps.isNullOrEmpty()) {
                if (first) return false
                break
            }
            first = false
            for (k in bitmaps.indices) {
                val bitmap = bitmaps[k]
                try {
                    deliver(bitmap, VideoFrameMath.indexedPtsUs(index + k, fps))
                } finally {
                    bitmap.recycle()
                }
            }
            index += bitmaps.size
        }
        return true
    }

    /** Fotogramas a ≤ 15 fps con `getScaledFrameAtTime`/`getFrameAtTime`; el PTS es el instante pedido. */
    private suspend fun runTimed(retriever: MediaMetadataRetriever, fps: Float) {
        val stepUs = maxOf(1_000_000L / TIMED_MAX_FPS, (1_000_000.0 / fps).roundToLong())
        mode = "timed step=${stepUs}us"
        var timeUs = fromUs
        var nulls = 0
        while (timeUs <= info.durationUs) {
            job.ensureActive()
            val bitmap = frameAt(retriever, timeUs)
            if (bitmap == null) {
                if (++nulls >= MAX_CONSECUTIVE_NULLS) break
            } else {
                nulls = 0
                try {
                    deliver(bitmap, timeUs)
                } finally {
                    bitmap.recycle()
                }
            }
            timeUs += stepUs
        }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, out.width, out.height)
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }

    /** Un fotograma del retriever: `needs`, conversión a RGBA y luma y `onFrame`. El índice se gasta siempre (aquí no se reintenta). */
    private suspend fun deliver(bitmap: Bitmap, ptsUs: Long) {
        val index = nextIndex
        val need = try {
            needs(index, ptsUs)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            throw ConsumerFailure(error)
        }
        nextIndex = index + 1
        if (need == FrameNeeds.SKIP) return
        drawInto(bitmap)
        val wantsRgba = need == FrameNeeds.RGBA || need == FrameNeeds.RGBA_AND_LUMA
        val wantsLuma = need == FrameNeeds.LUMA || need == FrameNeeds.RGBA_AND_LUMA
        val slot = pool.nextSlot()
        val buffer = pool.rgba(slot)
        target.copyPixelsToBuffer(buffer)
        buffer.clear()
        if (wantsLuma) pool.fillLuma(slot)
        val frame = DecodedFrame(
            index = index,
            ptsUs = ptsUs,
            width = out.width,
            height = out.height,
            rgba = if (wantsRgba) pool.rgba(slot) else null,
            luma = if (wantsLuma) pool.luma(slot) else null,
        )
        try {
            onFrame(frame)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            throw ConsumerFailure(error)
        }
    }

    /**
     * Dibuja [source] escalado (y girado si hace falta) en el Bitmap de salida. Según la versión, el retriever devuelve el
     * fotograma ya girado o tal como se codificó: si el giro es de 90/270 y su orientación no es la de la pantalla, se gira aquí.
     * Con 180° no se puede distinguir y se fía del retriever.
     */
    private fun drawInto(source: Bitmap) {
        sourceSize = "${source.width}x${source.height}"
        val rotation = info.rotationDegrees
        val sideways = rotation % 180 != 0
        val needsTurn = sideways && source.width != source.height && (source.width > source.height) != (info.displayWidth > info.displayHeight)
        rotatedByUs = needsTurn
        canvas.save()
        if (!needsTurn) {
            canvas.scale(out.width / source.width.toFloat(), out.height / source.height.toFloat())
        } else {
            canvas.translate(out.width / 2f, out.height / 2f)
            canvas.rotate(rotation.toFloat())
            canvas.scale(out.height / source.width.toFloat(), out.width / source.height.toFloat())
            canvas.translate(-source.width / 2f, -source.height / 2f)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.restore()
    }
}
