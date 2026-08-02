package com.example.kpkn.services.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** SAF mirror for all diagnostic namespaces. Local files remain authoritative. */
object KpknDiagnosticStorage {
    private const val TAG = "KpknDiagnosticStorage"
    private const val PREFS = "workout_voice_diagnostic_storage"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_TREE_LABEL = "tree_label"
    private const val ROOT_NAME = "KPKN"
    private val NAMESPACES = listOf(
        "voice", "nutrition", "auge", "app", "workout", "performance", "assistant",
        "programs", "learn", "health", "tts", "backend", "reports",
    )

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kpkn-jsonl-mirror").apply { isDaemon = true }
    }

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
        NAMESPACES.forEach { namespace ->
            runCatching { ensureNamespaceDirectory(appContext, namespace) }
        }
        mirrorRecoveryFiles(appContext)
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

    fun createJsonl(context: Context, namespace: String, displayName: String): Uri? {
        val appContext = context.applicationContext
        val directory = ensureNamespaceDirectory(appContext, namespace) ?: return null
        return ensureFile(appContext, directory, displayName)
    }

    fun enqueueMirror(context: Context, namespace: String, source: File, line: String) {
        if (!isConfigured(context)) return
        val appContext = context.applicationContext
        writer.execute {
            runCatching {
                val directory = ensureNamespaceDirectory(appContext, namespace)
                    ?: error("No se pudo crear KPKN/$namespace")
                val target = ensureFile(appContext, directory, source.name)
                    ?: error("No se pudo crear ${source.name}")
                appContext.contentResolver.openOutputStream(target, "wa")?.use { output ->
                    output.write(line.toByteArray(Charsets.UTF_8))
                    output.write('\n'.code)
                    output.flush()
                } ?: error("El proveedor SAF no permitió escribir ${source.name}")
            }.onFailure { error ->
                Log.e(TAG, "SAF mirror deferred; local source retained", error)
            }
        }
    }

    fun mirrorRecoveryFiles(context: Context) {
        val appContext = context.applicationContext
        val root = File(appContext.filesDir, "kpkn_diagnostics")
        if (!root.exists() || !isConfigured(appContext)) return
        writer.execute {
            root.walkTopDown()
                .filter { it.isFile && it.extension == "jsonl" }
                .forEach { source ->
                    val namespace = source.parentFile?.name ?: "app"
                    runCatching {
                        val directory = ensureNamespaceDirectory(appContext, namespace)
                            ?: error("No se pudo crear KPKN/$namespace")
                        val target = ensureFile(appContext, directory, source.name)
                            ?: error("No se pudo crear ${source.name}")
                        appContext.contentResolver.openOutputStream(target, "w")?.use { output ->
                            source.inputStream().use { input -> input.copyTo(output) }
                            output.flush()
                        } ?: error("No se pudo reescribir ${source.name}")
                    }.onFailure { error ->
                        Log.e(TAG, "Unable to recover ${source.name} to SAF", error)
                    }
                }
        }
    }

    private fun ensureNamespaceDirectory(context: Context, namespace: String): Uri? {
        val treeUri = configuredTreeUri(context) ?: return null
        val root = ensureDirectory(context, treeUri, treeDocumentUri(treeUri), ROOT_NAME) ?: return null
        return ensureDirectory(context, treeUri, root, namespace.take(48))
    }

    private fun treeDocumentUri(treeUri: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )

    private fun ensureDirectory(context: Context, treeUri: Uri, parent: Uri, name: String): Uri? {
        val existing = findChild(context, treeUri, parent, name, DocumentsContract.Document.MIME_TYPE_DIR)
        if (existing != null) return existing
        return DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            sanitize(name),
        )
    }

    private fun ensureFile(context: Context, parent: Uri, name: String): Uri? {
        val treeUri = configuredTreeUri(context) ?: return null
        return findChild(context, treeUri, parent, name, "application/x-ndjson")
            ?: DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                "application/x-ndjson",
                sanitize(name),
            )
    }

    private fun findChild(context: Context, treeUri: Uri?, parent: Uri, name: String, mimeType: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri ?: parent,
            DocumentsContract.getDocumentId(parent),
        )
        return runCatching {
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == name && cursor.getString(mimeIndex) == mimeType) {
                        val id = cursor.getString(idIndex)
                        return@use DocumentsContract.buildDocumentUriUsingTree(
                            treeUri ?: parent,
                            id,
                        )
                    }
                }
                null
            }
        }.getOrNull()
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
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return queried?.takeIf(String::isNotBlank)
            ?: Uri.decode(DocumentsContract.getTreeDocumentId(treeUri)).substringAfterLast(':')
                .ifBlank { "Carpeta seleccionada" }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\/:*?\"<>|]"), "_").trim().ifBlank { "kpkn-diagnostic" }.take(120)
}
