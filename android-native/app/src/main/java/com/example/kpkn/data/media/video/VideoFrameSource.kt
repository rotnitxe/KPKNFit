package com.example.kpkn.data.media.video

import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Entrega TODOS los fotogramas de un vídeo, en orden, ya girados y a ~[decode]`.targetLongEdge` px, con los PTS reales (§3.7).
 *
 * Una fuente sirve una decodificación a la vez. [close] libera lo que quede; cada [decode] libera lo suyo al terminar,
 * también al cancelarse o fallar.
 */
interface VideoFrameSource : Closeable {
    val info: VideoInfo

    /**
     * Decodifica desde [fromUs] (0 = el principio) hasta el final. Por cada fotograma pregunta a [needs] qué hace falta
     * (`index` cuenta desde 0 los fotogramas con PTS ≥ [fromUs]; [FrameNeeds.SKIP] ni lo dibuja) y entrega a [onFrame] el
     * [DecodedFrame], válido solo hasta que `onFrame` vuelve. Ambos se llaman desde el hilo de decodificación.
     * Es cancelable: mira la cancelación en cada fotograma. [targetLongEdge] nunca amplía el vídeo.
     */
    suspend fun decode(
        fromUs: Long = 0L,
        targetLongEdge: Int = 720,
        needs: (index: Int, ptsUs: Long) -> FrameNeeds,
        onFrame: suspend (DecodedFrame) -> Unit,
    )
}

/** Camino por el que se decodifica. [RETRIEVER] es el respaldo (más lento y con confianza máxima MEDIA en el conteo). */
enum class VideoDecodePath { GL, RETRIEVER }

/**
 * Extensión opcional de [VideoFrameSource] (no es parte del contrato de §3.7): por qué camino se decodificó. Las fuentes de
 * [VideoFrameSources.open] la implementan; úsala con `source as? VideoDecodeReport`.
 */
interface VideoDecodeReport {
    /** El camino de la última decodificación (o el que usará la siguiente). */
    val path: VideoDecodePath

    /** Por qué se pasó al respaldo; `null` si no hizo falta. */
    val fallbackReason: String?

    /** Texto corto para depurar: decodificador elegido, GL, giro que el códec incorporó, reinicios… */
    val details: String
}

/**
 * Un vídeo que no se puede abrir o decodificar. Una excepción de `needs` u `onFrame` NO se envuelve en esta.
 * Si la decodificación ya había empezado, lleva hasta dónde llegó para que el respaldo siga desde ahí.
 */
class VideoDecodeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** PTS (µs) del último fotograma que ya se había entregado o saltado al fallar; `Long.MIN_VALUE` si todavía ninguno. */
    var consumedPtsUs: Long = Long.MIN_VALUE
        internal set

    /** Índice que habría tenido el siguiente fotograma (0 si todavía ninguno): el respaldo sigue la cuenta desde ahí. */
    var nextIndex: Int = 0
        internal set
}

/** Una excepción de `needs` u `onFrame` mientras se decodifica: no es del decodificador, así que sube tal cual y no activa el respaldo. */
internal class ConsumerFailure(cause: Throwable) : RuntimeException(cause)

/** Una fuente que puede arrancar a mitad del vídeo y seguir la numeración de otra: el respaldo de GL tras un fallo. */
interface ResumableVideoFrameSource : VideoFrameSource {
    /** Como `decode`, pero el primer fotograma entregado lleva el índice [startIndex] en vez de 0. */
    suspend fun decodeFrom(
        fromUs: Long,
        targetLongEdge: Int,
        needs: (index: Int, ptsUs: Long) -> FrameNeeds,
        onFrame: suspend (DecodedFrame) -> Unit,
        startIndex: Int,
    )
}

object VideoFrameSources {
    /**
     * Abre [file]: GL primero y, si GL falla al abrir o el códec falla de forma no transitoria y repetida, el respaldo con
     * `MediaMetadataRetriever`. Bloquea unos milisegundos (sonda del archivo y de GL): fuera del hilo principal.
     * Lanza [VideoDecodeException] si el archivo no tiene pista de vídeo.
     */
    fun open(file: File): VideoFrameSource {
        val info = VideoInfoProbe.probe(file)
        val gl = try {
            GlVideoFrameSource.open(file, info)
        } catch (failure: VideoDecodeException) {
            return RetrieverVideoFrameSource(file, info, openFailure = failure.message)
        }
        return FallbackVideoFrameSource(gl, info) { reason -> RetrieverVideoFrameSource(file, info, openFailure = reason) }
    }
}

/**
 * Un camino principal (GL) con respaldo: si el principal falla a mitad con un [VideoDecodeException] (las excepciones de
 * `needs` y `onFrame` y la cancelación pasan tal cual), el respaldo sigue desde el fotograma siguiente al último que ya se
 * había entregado o saltado, con la misma numeración. Tras un fallo, las siguientes llamadas van directas al respaldo.
 */
internal class FallbackVideoFrameSource(
    private val primary: VideoFrameSource,
    override val info: VideoInfo,
    private val backupFactory: (reason: String?) -> ResumableVideoFrameSource,
) : VideoFrameSource, VideoDecodeReport {
    private var backup: ResumableVideoFrameSource? = null

    @Volatile
    private var usingBackup = false

    @Volatile
    private var reason: String? = null

    override val path: VideoDecodePath get() = if (usingBackup) VideoDecodePath.RETRIEVER else VideoDecodePath.GL
    override val fallbackReason: String? get() = reason
    override val details: String get() = ((if (usingBackup) backup else primary) as? VideoDecodeReport)?.details.orEmpty()

    override suspend fun decode(
        fromUs: Long,
        targetLongEdge: Int,
        needs: (index: Int, ptsUs: Long) -> FrameNeeds,
        onFrame: suspend (DecodedFrame) -> Unit,
    ) {
        var resumeUs = fromUs
        var nextIndex = 0
        if (!usingBackup) {
            try {
                primary.decode(fromUs, targetLongEdge, needs, onFrame)
                return
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: VideoDecodeException) {
                reason = failure.message
                usingBackup = true
                if (failure.consumedPtsUs != Long.MIN_VALUE) resumeUs = maxOf(fromUs, failure.consumedPtsUs + 1)
                nextIndex = failure.nextIndex
            }
        }
        val rescue = backup ?: backupFactory(reason).also { backup = it }
        rescue.decodeFrom(resumeUs, targetLongEdge, needs, onFrame, nextIndex)
    }

    override fun close() {
        runCatching { primary.close() }
        runCatching { backup?.close() }
    }
}
