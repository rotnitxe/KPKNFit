package com.example.kpkn.screens.stories

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.kpkn.screens.workout.WorkoutShareService
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Las salidas de una historia del programa: Instagram Stories (por el mismo camino que los entrenos), guardar en Fotos y el selector
 * de apps del sistema. La imagen viaja como PNG en la caché de la app, bajo el FileProvider.
 */
internal object StoryShare {

    /** Abre Instagram Stories con [bitmap] y, si Instagram no está, el selector del sistema (lo decide [WorkoutShareService]). */
    suspend fun shareToStories(context: Context, bitmap: Bitmap) {
        WorkoutShareService.shareBitmap(context, bitmap)
    }

    /** Guarda [bitmap] en Fotos, en la carpeta KPKN, y devuelve su Uri. */
    suspend fun save(context: Context, bitmap: Bitmap): Uri = WorkoutShareService.saveImage(context, bitmap)

    /** Abre el selector del sistema con [bitmap] como PNG, con el título «Compartir historia». */
    suspend fun shareWithChooser(context: Context, bitmap: Bitmap) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shares").apply { check(isDirectory || mkdirs()) }
            val file = File.createTempFile("program-story-", ".png", dir)
            FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        withContext(Dispatchers.Main) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("program_story", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Compartir historia").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    /**
     * Comparte el vídeo [file] (MP4 de la caché, bajo el FileProvider): a Instagram Stories si está instalado y, si no, al selector
     * del sistema con el título «Compartir historia».
     */
    suspend fun shareVideo(context: Context, file: File) {
        val uri = withContext(Dispatchers.IO) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
        withContext(Dispatchers.Main) {
            val instagram = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(uri, "video/mp4")
                setPackage(INSTAGRAM_PACKAGE)
                clipData = ClipData.newRawUri("competition_story", uri)
                putExtra("source_application", context.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val opened = hasInstagram(context) && runCatching { context.startActivity(instagram) }.isSuccess
            if (!opened) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("competition_story", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(send, "Compartir historia").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }

    /**
     * true si Instagram está instalado. En Android 11 o posterior, la app solo lo ve si el manifiesto lo declara en `<queries>`.
     */
    fun hasInstagram(context: Context): Boolean = runCatching {
        context.packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE) != null
    }.getOrDefault(false)

    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
}
