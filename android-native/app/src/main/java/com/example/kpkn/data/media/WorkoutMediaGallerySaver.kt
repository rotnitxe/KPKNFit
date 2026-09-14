package com.example.kpkn.data.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import java.io.File

object WorkoutMediaGallerySaver {
    fun save(context: Context, media: WorkoutMedia): Boolean {
        val source = File(media.filePath)
        if (!source.isFile || source.length() <= 0L) return false
        val resolver = context.contentResolver
        val isVideo = media.kind == WorkoutMediaKind.VIDEO
        val displayName = source.name.ifBlank {
            if (isVideo) "kpkn_${media.id}.mp4" else "kpkn_${media.id}.jpg"
        }
        val mime = if (isVideo) {
            when (source.extension.lowercase()) {
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                else -> "video/mp4"
            }
        } else {
            when (source.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
        }
        val collection = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relative = if (isVideo) {
                    Environment.DIRECTORY_MOVIES + "/KPKN"
                } else {
                    Environment.DIRECTORY_PICTURES + "/KPKN"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pending = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, pending, null, null)
            }
            true
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }
}
