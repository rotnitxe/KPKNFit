package com.example.kpkn.screens.workout.components

import android.content.Context
import java.io.File

internal object ExerciseUserMediaStore {
    fun directory(context: Context, exerciseKey: String): File {
        val safe = exerciseKey.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown" }
        return File(context.filesDir, "exercise_user_media/$safe").apply { mkdirs() }
    }

    fun list(context: Context, exerciseKey: String): List<File> =
        directory(context, exerciseKey).listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun newPhotoFile(context: Context, exerciseKey: String): File =
        File(directory(context, exerciseKey), "photo_${System.currentTimeMillis()}.jpg")

    fun newVideoFile(context: Context, exerciseKey: String): File =
        File(directory(context, exerciseKey), "video_${System.currentTimeMillis()}.mp4")
}
