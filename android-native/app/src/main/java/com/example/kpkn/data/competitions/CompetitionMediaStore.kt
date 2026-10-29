package com.example.kpkn.data.competitions

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.kpkn.data.models.CompetitionMediaKind
import com.example.kpkn.data.models.CompetitionPhoto
import java.io.File
import java.util.UUID

object CompetitionMediaStore {
    fun copyIntoApp(context: Context, source: Uri, recordId: String): CompetitionPhoto? {
        val resolver = context.contentResolver
        val mime = resolver.getType(source).orEmpty()
        val kind = if (mime.startsWith("video")) CompetitionMediaKind.VIDEO else CompetitionMediaKind.PHOTO
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: if (kind == CompetitionMediaKind.VIDEO) "mp4" else "jpg"
        val dir = File(context.filesDir, "competition_media/$recordId").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.$ext")
        resolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (!target.exists() || target.length() <= 0L) return null
        return CompetitionPhoto(
            id = UUID.randomUUID().toString(),
            uri = Uri.fromFile(target).toString(),
            kind = kind,
        )
    }

    /** Borra el archivo de una foto o vídeo copiado al archivo de la app (las quitadas no quedan huérfanas). */
    fun delete(context: Context, photo: CompetitionPhoto): Boolean {
        val path = runCatching { Uri.parse(photo.uri).path }.getOrNull() ?: return false
        val root = File(context.filesDir, "competition_media").canonicalFile
        val file = File(path).canonicalFile
        if (!file.path.startsWith(root.path + File.separator)) return false
        return file.delete()
    }

    /** Borra todas las fotos de una competencia (al eliminarla). */
    fun deleteAll(context: Context, recordId: String): Boolean {
        if (recordId.isBlank() || recordId.contains("..") || recordId.contains('/')) return false
        return File(context.filesDir, "competition_media/$recordId").deleteRecursively()
    }
}
