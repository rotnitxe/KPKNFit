package com.example.kpkn.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.example.kpkn.data.models.WorkoutMediaKind
import java.io.File

data class WorkoutMediaProbe(
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbWritten: Boolean = false,
)

object WorkoutMediaThumbnailer {
    fun probeAndThumb(
        source: File,
        thumbDest: File,
        kind: WorkoutMediaKind,
    ): WorkoutMediaProbe {
        if (!source.isFile || source.length() <= 0L) return WorkoutMediaProbe()
        if (kind != WorkoutMediaKind.VIDEO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            return WorkoutMediaProbe(
                width = bounds.outWidth.takeIf { it > 0 },
                height = bounds.outHeight.takeIf { it > 0 },
            )
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            var written = false
            if (frame != null) {
                thumbDest.parentFile?.mkdirs()
                thumbDest.outputStream().use { out ->
                    written = frame.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                frame.recycle()
            }
            WorkoutMediaProbe(
                durationMs = duration,
                width = width,
                height = height,
                thumbWritten = written && thumbDest.isFile && thumbDest.length() > 0L,
            )
        } catch (_: Exception) {
            WorkoutMediaProbe()
        } finally {
            runCatching { retriever.release() }
        }
    }
}
