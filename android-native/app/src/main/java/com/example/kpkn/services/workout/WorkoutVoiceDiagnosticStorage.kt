package com.example.kpkn.services.workout

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Persists the user-selected SAF tree and mirrors voice diagnostics without
 * blocking the main or recognition threads. The internal JSONL remains the
 * crash-safe source of truth when a document provider becomes unavailable.
 */
object WorkoutVoiceDiagnosticStorage {
    private const val TAG = "VoiceDiagnosticStore"
    private const val PREFS = "workout_voice_diagnostic_storage"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_TREE_LABEL = "tree_label"
    private const val MAX_PENDING_WRITES = 256

    private val writer = ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(MAX_PENDING_WRITES),
        { runnable -> Thread(runnable, "kpkn-voice-jsonl-mirror").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy(),
    )

    fun configure(context: Context, treeUri: Uri): Result<String> = runCatching {
        require(DocumentsContract.isTreeUri(treeUri)) { "La ubicación seleccionada no es una carpeta válida" }
        val appContext = context.applicationContext
        appContext.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val label = resolveTreeLabel(appContext, treeUri)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putString(KEY_TREE_LABEL, label)
            .commit()
        label
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        configuredTreeUri(appContext)?.let { uri ->
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun isConfigured(context: Context): Boolean = configuredTreeUri(context) != null

    fun configuredLabel(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_LABEL, null)

    fun createJsonl(context: Context, displayName: String): Uri? =
        createDocument(context.applicationContext, displayName, "application/x-ndjson")

    fun appendLine(context: Context, documentUri: Uri, line: String) {
        val appContext = context.applicationContext
        writer.execute {
            runCatching {
                appContext.contentResolver.openOutputStream(documentUri, "wa")?.use { output ->
                    output.write(line.toByteArray(Charsets.UTF_8))
                    output.write('\n'.code)
                    output.flush()
                } ?: error("El proveedor no permitió abrir el JSONL")
            }.onFailure { error ->
                Log.e(TAG, "Unable to mirror voice diagnostic line", error)
            }
        }
    }

    fun mirrorRecoveryFiles(context: Context, files: List<File>) {
        if (files.isEmpty() || !isConfigured(context)) return
        val appContext = context.applicationContext
        writer.execute {
            files.filter(File::exists).forEach { source ->
                runCatching {
                    val mime = when (source.extension.lowercase()) {
                        "jsonl" -> "application/x-ndjson"
                        "json" -> "application/json"
                        else -> "application/octet-stream"
                    }
                    val target = createDocument(appContext, source.name, mime)
                        ?: error("No se pudo crear ${source.name}")
                    appContext.contentResolver.openOutputStream(target, "w")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                        output.flush()
                    } ?: error("No se pudo escribir ${source.name}")
                }.onFailure { error ->
                    Log.e(TAG, "Unable to mirror recovery file ${source.name}", error)
                }
            }
        }
    }

    internal fun sanitizeDisplayName(name: String): String {
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return safe.ifBlank { "kpkn-voice-diagnostic.jsonl" }.take(120)
    }

    private fun createDocument(context: Context, displayName: String, mimeType: String): Uri? {
        val treeUri = configuredTreeUri(context) ?: return null
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            mimeType,
            sanitizeDisplayName(displayName),
        )
    }

    private fun configuredTreeUri(context: Context): Uri? {
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null)
            ?: return null
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return null
        val hasPermission = context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isWritePermission
        }
        return uri.takeIf { hasPermission }
    }

    private fun resolveTreeLabel(context: Context, treeUri: Uri): String {
        val queried = runCatching {
            context.contentResolver.query(
                treeUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return queried?.takeIf(String::isNotBlank)
            ?: Uri.decode(DocumentsContract.getTreeDocumentId(treeUri)).substringAfterLast(':')
                .ifBlank { "Carpeta seleccionada" }
    }
}
