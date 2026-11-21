package com.example.kpkn.screens.workout

import android.content.Context
import java.io.File

/** Keep recent shares available to recipient apps; remove only our own old temporary files. */
internal fun storyShareDirectory(context: Context): File {
    val dir = File(context.cacheDir, "shares")
    check(dir.isDirectory || dir.mkdirs())
    val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    val prefixes = listOf("workout-story-", "workout-photo-", "program-story-", "competition-story-")
    dir.listFiles()?.forEach { file ->
        if (file.isFile && prefixes.any(file.name::startsWith) && file.lastModified() in 1 until cutoff) file.delete()
    }
    return dir
}
