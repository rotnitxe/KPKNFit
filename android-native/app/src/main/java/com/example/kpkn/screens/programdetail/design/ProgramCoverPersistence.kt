package com.example.kpkn.screens.programdetail.design

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

internal sealed interface ProgramCoverPickOutcome {
    data class Success(val uriString: String) : ProgramCoverPickOutcome
    data class Failure(val message: String) : ProgramCoverPickOutcome
}

internal suspend fun resolvePickedProgramCover(
    context: Context,
    programId: String,
    uri: Uri,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): ProgramCoverPickOutcome = resolvePickedProgramCover(
    programId = programId,
    uri = uri,
    filesDir = context.filesDir,
    takePersistableReadPermission = { target ->
        context.contentResolver.takePersistableUriPermission(target, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },
    openInputStream = { target -> context.contentResolver.openInputStream(target) },
    isReadableImageFile = { file -> isDecodableImageFile(file) },
    isReadableImageStream = { stream -> isDecodableImageStream(stream) },
    dispatcher = dispatcher,
)

internal suspend fun resolvePickedProgramCover(
    programId: String,
    uri: Uri,
    filesDir: File,
    takePersistableReadPermission: (Uri) -> Unit,
    openInputStream: (Uri) -> InputStream?,
    isReadableImageFile: (File) -> Boolean = ::isDecodableImageFile,
    isReadableImageStream: (InputStream) -> Boolean = ::isDecodableImageStream,
    nowMs: () -> Long = { System.currentTimeMillis() },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): ProgramCoverPickOutcome = withContext(dispatcher) {
    val uriString = uri.toString()
    val hasPersistableGrant = runCatching { takePersistableReadPermission(uri) }.isSuccess
    if (hasPersistableGrant) {
        val readableUri = try {
            openInputStream(uri)?.use { stream -> isReadableImageStream(stream) } ?: false
        } catch (t: CancellationException) {
            throw t
        } catch (_: Throwable) {
            false
        }
        if (readableUri) return@withContext ProgramCoverPickOutcome.Success(uriString)
    }

    val coversDir = File(filesDir, "program_covers")
    if (!coversDir.exists() && !coversDir.mkdirs()) {
        return@withContext ProgramCoverPickOutcome.Failure("No se pudo preparar la carpeta privada para la portada.")
    }
    val sanitizedId = programId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "program" }
    val target = File(coversDir, "cover_${sanitizedId}_${nowMs()}.jpg")
    try {
        val stream = openInputStream(uri)
            ?: return@withContext ProgramCoverPickOutcome.Failure("No se pudo abrir la imagen seleccionada.")
        stream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (!target.exists() || target.length() <= 0L || !isReadableImageFile(target)) {
            target.delete()
            return@withContext ProgramCoverPickOutcome.Failure("La imagen seleccionada no se puede leer.")
        }
        ProgramCoverPickOutcome.Success(Uri.fromFile(target).toString())
    } catch (t: CancellationException) {
        target.delete()
        throw t
    } catch (_: Throwable) {
        target.delete()
        ProgramCoverPickOutcome.Failure("No se pudo guardar una copia de la imagen seleccionada.")
    }
}

private fun isDecodableImageFile(file: File): Boolean = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    opts.outWidth > 0 && opts.outHeight > 0
}.getOrDefault(false)

private fun isDecodableImageStream(stream: InputStream): Boolean = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(stream, null, opts)
    opts.outWidth > 0 && opts.outHeight > 0
}.getOrDefault(false)
