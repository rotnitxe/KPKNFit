package com.example.kpkn.screens.workout

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.kpkn.data.models.CompletedExercise
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WorkoutShareService {
    // El PNG tras terminar debe sobrevivir a que la navegación retire el ViewModel del entrenamiento.
    private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal fun shareAfterFinish(context: Context, data: WorkoutStoryData, options: WorkoutStoryOptions) {
        shareScope.launch {
            runCatching {
                val bitmap = renderWorkoutStory(context.applicationContext, data, options)
                try { shareBitmap(context, bitmap) } finally { bitmap.recycle() }
            }.onFailure { Toast.makeText(context, "No se pudo abrir la pantalla para compartir.", Toast.LENGTH_SHORT).show() }
        }
    }

    /** Compatibilidad con otros puntos de entrada: también usan la historia General oficial. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun shareToInstagramStory(
        context: Context,
        sessionName: String,
        completedExercises: List<CompletedExercise> = emptyList(),
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int? = null,
        previousVolume: Double? = null,
        previousDurationMinutes: Int? = null,
        previousBestEstimated1RM: Double? = null,
        currentBestEstimated1RM: Double? = null,
    ) {
        val data = WorkoutStoryData(sessionName, FinishPresentationContext(), durationMinutes, totalVolume, totalSets,
            finishAverageRir(completedExercises), "Sin registro", buildFinishLiftProgress(completedExercises, emptyList()))
        val bitmap = renderWorkoutStory(context.applicationContext, data, WorkoutStoryOptions())
        try { shareBitmap(context, bitmap) } finally { bitmap.recycle() }
    }

    internal suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shares").apply { check(isDirectory || mkdirs()) }
            val file = File.createTempFile("workout-story-", ".png", dir)
            FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        }
        withContext(Dispatchers.Main) { launchShareIntents(context,uri) }
    }

    /** Shares only the selected local photo through Android's chooser, without story/Instagram routing. */
    internal suspend fun sharePhoto(context: Context, path: String) {
        val chooser = createPhotoShareIntent(context, path)
        withContext(Dispatchers.Main) { context.startActivity(chooser) }
    }

    /** Copy to the existing shares/ FileProvider root; never expose private file:// paths. */
    internal suspend fun createPhotoShareIntent(context: Context, path: String): Intent = withContext(Dispatchers.IO) {
        val source = if (path.startsWith("content:") || path.startsWith("file:")) Uri.parse(path) else {
            require(File(path).isAbsolute) { "A local photo path is required" }
            Uri.fromFile(File(path))
        }
        require(source.scheme == "content" || source.scheme == "file")
        val mime = context.contentResolver.getType(source)
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                source.lastPathSegment?.substringAfterLast('.', "")?.lowercase(java.util.Locale.ROOT).orEmpty())
            ?: "image/jpeg"
        require(mime.startsWith("image/")) { "Only photos can use this share action" }
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
        val directory = File(context.cacheDir, "shares").apply { check(isDirectory || mkdirs()) }
        val copy = File.createTempFile("workout-photo-", ".$extension", directory)
        try {
            checkNotNull(context.contentResolver.openInputStream(source)).use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            }
            check(copy.length() > 0)
            val shared = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shared)
                clipData = ClipData.newRawUri("workout_photo", shared)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(send, "Compartir foto").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (error: Throwable) {
            copy.delete()
            throw error
        }
    }

    /** API24–28 requiere WRITE_EXTERNAL_STORAGE concedido por la hoja; API29+ usa almacenamiento acotado. */
    internal suspend fun saveImage(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val name = "KPKN-${System.currentTimeMillis()}.png"
        var legacyFile: File? = null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,name)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            if(Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH,"${Environment.DIRECTORY_PICTURES}/KPKN")
                put(MediaStore.Images.Media.IS_PENDING,1)
            } else {
                @Suppress("DEPRECATION") val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"KPKN")
                check(dir.isDirectory || dir.mkdirs()) { "No se pudo crear la carpeta KPKN" }
                legacyFile=File(dir,name)
                @Suppress("DEPRECATION") put(MediaStore.Images.Media.DATA,legacyFile!!.absolutePath)
            }
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        try {
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            if(Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            uri
        } catch(error:Throwable) {
            context.contentResolver.delete(uri,null,null)
            legacyFile?.delete()
            throw error
        }
    }

    private fun launchShareIntents(context: Context, uri: Uri) {
        val instagramIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            setPackage(INSTAGRAM_PACKAGE)
            clipData = ClipData.newRawUri("workout_story", uri)
            putExtra("source_application", context.packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        var sharedDirectly = false
        try {
            context.grantUriPermission(
                INSTAGRAM_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            context.startActivity(instagramIntent)
            sharedDirectly = true
        } catch (_: Exception) {
            // Instagram is not installed or failed to launch. Fallback.
        }

        if (!sharedDirectly) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("workout_story", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(fallback, "Compartir entrenamiento").apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    private const val INSTAGRAM_PACKAGE = "com.instagram.android"

}
