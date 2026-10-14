package com.example.kpkn.data.media.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File

/**
 * Lee las propiedades de un vídeo sin decodificarlo: MediaExtractor para la pista (mime, tamaño codificado, giro, fps) y
 * MediaMetadataRetriever para lo que la pista no trae (duración, cámara lenta, HDR en API 30+). Bloquea unos milisegundos:
 * llámalo fuera del hilo principal.
 */
object VideoInfoProbe {
    /** Muestras de las que sale la mediana de PTS cuando la pista no declara su fps. */
    private const val FPS_SAMPLES = 60

    /**
     * Propiedades del primer vídeo de [file]. Lanza [VideoDecodeException] si el archivo no existe, no se puede abrir o no tiene
     * pista de vídeo.
     */
    fun probe(file: File): VideoInfo {
        if (!file.isFile || file.length() <= 0L) throw VideoDecodeException("El vídeo no existe o está vacío: ${file.name}")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = findVideoTrack(extractor)
            if (track < 0) throw VideoDecodeException("El archivo no tiene pista de vídeo: ${file.name}")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val codedWidth = format.intOrNull(MediaFormat.KEY_WIDTH)
            val codedHeight = format.intOrNull(MediaFormat.KEY_HEIGHT)
            if (codedWidth == null || codedHeight == null || codedWidth <= 0 || codedHeight <= 0) {
                throw VideoDecodeException("La pista de vídeo no declara su tamaño: ${file.name}")
            }
            extractor.selectTrack(track)
            val sampledPts = firstPts(extractor)
            val declaredFps = format.floatOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it > 0f }
            val retriever = RetrieverFacts.read(file)
            val rotation = VideoFrameMath.normalizeRotation(format.intOrNull(MediaFormat.KEY_ROTATION) ?: retriever.rotation ?: 0)
            val display = VideoFrameMath.displaySize(codedWidth, codedHeight, rotation)
            val durationUs = format.longOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0L }
                ?: retriever.durationUs
                ?: (sampledPts.lastOrNull() ?: 0L)
            val transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER) ?: retriever.colorTransfer
            return VideoInfo(
                codedWidth = codedWidth,
                codedHeight = codedHeight,
                rotationDegrees = rotation,
                displayWidth = display.width,
                displayHeight = display.height,
                durationUs = durationUs,
                mime = mime,
                nominalFps = declaredFps ?: VideoFrameMath.nominalFpsFromPts(sampledPts.toLongArray(), sampledPts.size),
                captureFps = retriever.captureFps,
                isHdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG,
            )
        } catch (probeFailure: VideoDecodeException) {
            throw probeFailure
        } catch (error: Exception) {
            throw VideoDecodeException("No se pudo leer el vídeo ${file.name}: ${error.javaClass.simpleName}: ${error.message}", error)
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Índice de la primera pista de vídeo del extractor; −1 si no hay. */
    internal fun findVideoTrack(extractor: MediaExtractor): Int {
        for (track in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) return track
        }
        return -1
    }

    /** PTS de las primeras muestras de la pista ya seleccionada (en el orden del contenedor, que con B-frames no es el de salida). */
    private fun firstPts(extractor: MediaExtractor): List<Long> {
        val pts = ArrayList<Long>(FPS_SAMPLES)
        while (pts.size < FPS_SAMPLES) {
            val time = extractor.sampleTime
            if (time < 0L) break
            pts.add(time)
            if (!extractor.advance()) break
        }
        return pts
    }

    /** Lo que solo da el retriever. Cualquier fallo aquí deja los campos en `null`: la pista manda. */
    private class RetrieverFacts(
        val durationUs: Long?,
        val rotation: Int?,
        val captureFps: Float?,
        val colorTransfer: Int?,
    ) {
        companion object {
            fun read(file: File): RetrieverFacts {
                val retriever = MediaMetadataRetriever()
                return try {
                    retriever.setDataSource(file.absolutePath)
                    RetrieverFacts(
                        durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()?.takeIf { it > 0L }?.times(1_000L),
                        rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull(),
                        captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                            ?.toFloatOrNull()?.takeIf { it > 0f },
                        colorTransfer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
                        } else {
                            null
                        },
                    )
                } catch (_: Exception) {
                    RetrieverFacts(null, null, null, null)
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (!containsKey(key)) null else try { getInteger(key) } catch (_: ClassCastException) { null }

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (!containsKey(key)) null else try { getLong(key) } catch (_: ClassCastException) { null }

    /** El fps de la pista puede venir como entero o como decimal según el contenedor. */
    private fun MediaFormat.floatOrNull(key: String): Float? {
        if (!containsKey(key)) return null
        return try {
            getInteger(key).toFloat()
        } catch (_: ClassCastException) {
            try { getFloat(key) } catch (_: ClassCastException) { null }
        }
    }
}
