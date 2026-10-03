package com.example.kpkn.data.media

import android.content.Context
import com.example.kpkn.data.models.WorkoutMediaKind
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Private on-device store for unified workout media.
 *
 * Layout: `filesDir/workout_media/yyyy-MM/<id>.jpg|mp4` plus `workout_media/thumbs/`.
 */
class WorkoutMediaStore(private val filesDir: File) {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    fun root(): File = File(filesDir, ROOT_DIR)

    fun thumbsDir(): File = File(root(), THUMBS_DIR)

    fun monthDir(createdAtMs: Long): File {
        val ym = YEAR_MONTH.format(Instant.ofEpochMilli(createdAtMs).atZone(ZoneOffset.UTC))
        return File(root(), ym)
    }

    fun destinationFile(
        id: String,
        kind: WorkoutMediaKind,
        createdAtMs: Long,
        extension: String? = null,
    ): File {
        val ext = extension?.lowercase()?.trim('.')?.takeIf { it.isNotBlank() }
            ?: if (kind == WorkoutMediaKind.VIDEO) "mp4" else "jpg"
        val directory = monthDir(createdAtMs)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("No se pudo preparar el almacenamiento de medios.")
        }
        return File(directory, "$id.$ext")
    }

    fun thumbFile(id: String): File =
        thumbsDir().let { directory ->
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                throw IOException("No se pudo preparar las miniaturas de medios.")
            }
            File(directory, "$id.jpg")
        }

    fun poseSidecarFile(id: String, createdAtMs: Long): File {
        val directory = monthDir(createdAtMs)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("No se pudo preparar el almacenamiento de medios.")
        }
        return File(directory, "$id${PoseTrajectoryAnalysis.SIDECAR_SUFFIX}")
    }

    fun isManagedPath(file: File): Boolean {
        val rootPath = canonicalPath(root())
        val filePath = canonicalPath(file)
        return filePath == rootPath || filePath.startsWith("$rootPath/")
    }

    /**
     * Copies [source] into the private tree. Does not delete or mutate [source].
     * If the destination already exists, it is left untouched.
     */
    fun copyIntoStore(
        source: File,
        id: String,
        kind: WorkoutMediaKind,
        createdAtMs: Long,
        destinationExtension: String? = null,
    ): File {
        val dest = destinationFile(
            id,
            kind,
            createdAtMs,
            destinationExtension ?: source.extension,
        )
        if (!dest.exists()) {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dest
    }

    fun deleteManaged(filePath: String) {
        val file = File(filePath)
        if (file.exists() && isManagedPath(file)) {
            file.delete()
        }
    }

    companion object {
        const val ROOT_DIR = "workout_media"
        const val THUMBS_DIR = "thumbs"
        private val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        fun kindOf(file: File): WorkoutMediaKind =
            when (file.extension.lowercase()) {
                "mp4", "webm", "mov", "m4v", "3gp" -> WorkoutMediaKind.VIDEO
                else -> WorkoutMediaKind.PHOTO
            }

        fun canonicalPath(file: File): String =
            runCatching { file.canonicalFile.absolutePath }
                .getOrDefault(file.absolutePath)
                .replace('\\', '/')
    }
}
