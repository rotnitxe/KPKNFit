package com.example.kpkn.services.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import java.io.File
import java.util.concurrent.Executors

/**
 * Single SAF mirror for the diagnostic bus.
 *
 * The selected tree is configured only from Settings. All other callers use
 * the persisted grant and never launch a picker.
 */
object KpknDiagnosticStorage {
    private const val TAG = "KpknDiagnosticStorage"
    /** Kept stable so existing user grants survive the consolidation. */
    private const val PREFS = "workout_voice_diagnostic_storage"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_TREE_LABEL = "tree_label"
    private const val ROOT_NAME = "KPKN"
    private const val LOGS_NAME = "logs"
    private const val REPORTS_NAME = "reports"
    private const val MAX_MIRROR_FILES = 64
    private const val MAX_MIRROR_TOTAL_BYTES = 50L * 1024L * 1024L
    private const val PRUNE_EVERY_EVENTS = 32
    private const val SYNC_EVERY_EVENTS = 25
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kpkn-jsonl-mirror").apply { isDaemon = true }
    }

    private val fileUris = mutableMapOf<String, Uri>()
    private var eventsSincePrune = 0
    private var eventsSinceSync = 0
    private val mirroredRecoveryFiles = mutableSetOf<String>()

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
        ensureDirectoryPath(appContext, listOf(LOGS_NAME))
        ensureDirectoryPath(appContext, listOf(REPORTS_NAME))
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
        synchronized(fileUris) { fileUris.clear() }
    }

    fun isConfigured(context: Context): Boolean = configuredTreeUri(context) != null

    fun configuredLabel(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_LABEL, null)

    fun createJsonl(context: Context, namespace: String, displayName: String): Uri? {
        val appContext = context.applicationContext
        val area = officialArea(namespace)
        val date = UTC_DAY_FORMAT.format(java.time.Instant.now())
        val directory = ensureDirectoryPath(appContext, listOf(LOGS_NAME, area, date)) ?: return null
        return ensureFile(appContext, directory, displayName, "application/x-ndjson")
    }

    fun enqueueMirror(context: Context, namespace: String, source: File, line: String) {
        if (!isConfigured(context)) return
        val appContext = context.applicationContext
        writer.execute {
            val area = officialArea(namespace)
            val date = source.parentFile?.name?.takeIf { it.matches(Regex("\\d{8}")) }
                ?: UTC_DAY_FORMAT.format(java.time.Instant.now())
            val first = runCatching { writeMirrorLine(appContext, area, date, source, line) }
            if (first.isFailure) {
                val error = first.exceptionOrNull()
                synchronized(fileUris) { fileUris.remove(source.absolutePath) }
                runCatching { writeMirrorLine(appContext, area, date, source, line) }
                    .onFailure { retryError ->
                        Log.e(TAG, "SAF mirror deferred; local source retained", retryError ?: error)
                    }
            }
            if (++eventsSinceSync >= SYNC_EVERY_EVENTS) {
                eventsSinceSync = 0
                runCatching { java.io.FileInputStream(source).use { it.fd.sync() } }
            }
            if (++eventsSincePrune >= PRUNE_EVERY_EVENTS) {
                eventsSincePrune = 0
                runCatching {
                    ensureDirectoryPath(appContext, listOf(LOGS_NAME, area, date))?.let { directory ->
                        pruneMirror(appContext, directory)
                    }
                }.onFailure { error -> Log.e(TAG, "SAF mirror prune failed", error) }
            }
        }
    }

    /** Mirrors a local report/artifact to KPKN/reports without opening a picker. */
    fun mirrorFile(context: Context, source: File, relativePath: String) {
        if (!source.isFile || !isConfigured(context)) return
        val appContext = context.applicationContext
        writer.execute {
            runCatching {
                val segments = relativePath.replace('\\', '/').split('/').filter(String::isNotBlank)
                val name = segments.lastOrNull() ?: source.name
                val directory = ensureDirectoryPath(appContext, segments.dropLast(1))
                    ?: error("No se pudo crear KPKN/$relativePath")
                val target = ensureFile(appContext, directory, name, mimeFor(source))
                    ?: error("No se pudo crear ${source.name}")
                appContext.contentResolver.openOutputStream(target, "wt")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                    output.flush()
                } ?: error("El proveedor SAF no permitió escribir ${source.name}")
            }.onFailure { error -> Log.e(TAG, "Unable to mirror ${source.name}", error) }
        }
    }

    /** Recovers both the new layout and legacy roots after a grant is configured. */
    fun mirrorRecoveryFiles(context: Context) {
        val appContext = context.applicationContext
        if (!isConfigured(appContext)) return
        val candidates = buildList {
            File(appContext.filesDir, KpknDiagnosticLogger.LOG_ROOT).takeIf(File::isDirectory)?.let { root ->
                root.walkTopDown().filter { it.isFile && it.extension in setOf("jsonl", "md") }.forEach(::add)
            }
            listOf("kpkn_diagnostics", "voice_diagnostics", "nutrition_telemetry").forEach { name ->
                File(appContext.filesDir, name).takeIf(File::isDirectory)?.let { root ->
                    root.walkTopDown().filter { it.isFile && it.extension in setOf("jsonl", "md", "json") }.forEach(::add)
                }
            }
        }.distinctBy(File::getAbsolutePath)
        if (candidates.isEmpty()) return
        writer.execute {
            candidates.filter { it.exists() && mirroredRecoveryFiles.add(it.absolutePath) }.forEach { source ->
                runCatching { mirrorRecoveryFile(appContext, source) }
                    .onFailure { error -> Log.e(TAG, "Unable to recover ${source.name}", error) }
            }
        }
    }

    private fun mirrorRecoveryFile(context: Context, source: File) {
        val root = context.filesDir
        val newRoot = File(root, KpknDiagnosticLogger.LOG_ROOT)
        val relative = when {
            source.path.startsWith(newRoot.path) -> source.relativeTo(newRoot).path.replace('\\', '/')
            source.path.startsWith(File(root, "kpkn_diagnostics").path) -> {
                val legacyRelative = source.relativeTo(File(root, "kpkn_diagnostics")).path.replace('\\', '/')
                if (legacyRelative.startsWith("reports/")) legacyRelative else legacyLogRelative(source, legacyRelative)
            }
            source.path.startsWith(File(root, "voice_diagnostics").path) ->
                "${LOGS_NAME}/voice/${UTC_DAY_FORMAT.format(java.time.Instant.ofEpochMilli(source.lastModified()))}/${source.name}"
            source.path.startsWith(File(root, "nutrition_telemetry").path) ->
                "${LOGS_NAME}/nutrition/${UTC_DAY_FORMAT.format(java.time.Instant.ofEpochMilli(source.lastModified()))}/${source.name}"
            else -> "${LOGS_NAME}/performance/${UTC_DAY_FORMAT.format(java.time.Instant.now())}/${source.name}"
        }
        val normalized = when {
            relative.startsWith("$LOGS_NAME/") -> relative
            relative.startsWith("$REPORTS_NAME/") -> relative
            else -> "$REPORTS_NAME/$relative"
        }
        val segments = normalized.split('/').filter(String::isNotBlank)
        val directory = ensureDirectoryPath(context, segments.dropLast(1)) ?: return
        val target = ensureFile(context, directory, segments.last(), mimeFor(source)) ?: return
        context.contentResolver.openOutputStream(target, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        }
    }

    private fun legacyLogRelative(source: File, relative: String): String {
        val namespace = relative.substringBefore('/').ifBlank { "performance" }
        val area = officialArea(namespace)
        val date = UTC_DAY_FORMAT.format(java.time.Instant.ofEpochMilli(source.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()))
        return "$LOGS_NAME/$area/$date/${source.name}"
    }

    private fun writeMirrorLine(context: Context, area: String, date: String, source: File, line: String) {
        val directory = ensureDirectoryPath(context, listOf(LOGS_NAME, area, date))
            ?: error("No se pudo crear KPKN/$LOGS_NAME/$area/$date")
        val key = source.absolutePath
        val target = synchronized(fileUris) { fileUris[key] }
            ?: ensureFile(context, directory, source.name, "application/x-ndjson")
                ?.also { synchronized(fileUris) { fileUris[key] = it } }
            ?: error("No se pudo crear ${source.name}")
        context.contentResolver.openOutputStream(target, "wa")?.use { output ->
            output.write(line.toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            output.flush()
        } ?: error("El proveedor SAF no permitió escribir ${source.name}")
    }

    private fun ensureDirectoryPath(context: Context, segments: List<String>): Uri? {
        val treeUri = configuredTreeUri(context) ?: return null
        var parent = treeDocumentUri(treeUri)
        segments.forEach { segment ->
            parent = ensureDirectory(context, treeUri, parent, segment) ?: return null
        }
        return parent
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

    private fun ensureFile(context: Context, parent: Uri, name: String, mimeType: String): Uri? {
        val key = "$parent/$name"
        synchronized(fileUris) { fileUris[key]?.let { return it } }
        val treeUri = configuredTreeUri(context) ?: return null
        val found = findChild(context, treeUri, parent, name, null)
            ?: DocumentsContract.createDocument(context.contentResolver, parent, mimeType, sanitize(name))
        if (found != null) synchronized(fileUris) { fileUris[key] = found }
        return found
    }

    private fun findChild(context: Context, treeUri: Uri, parent: Uri, name: String, mimeType: String?): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parent),
        )
        return runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val sameName = cursor.getString(nameIndex) == sanitize(name)
                    val matchesType = mimeType == null || cursor.getString(mimeIndex) == mimeType
                    if (sameName && matchesType) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun pruneMirror(context: Context, directory: Uri) {
        val treeUri = configuredTreeUri(context) ?: return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(directory))
        val files = context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    add(
                        Triple(
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex)),
                            if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L,
                            if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L,
                        ),
                    )
                }
            }
        }.orEmpty()
        val sorted = files.sortedBy { it.second }
        var total = sorted.sumOf { it.third }
        var index = 0
        while (sorted.size - index > MAX_MIRROR_FILES || total > MAX_MIRROR_TOTAL_BYTES) {
            val oldest = sorted.getOrNull(index++) ?: break
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, oldest.first) }
            total -= oldest.third
        }
    }

    private fun configuredTreeUri(context: Context): Uri? {
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) ?: return null
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

    private fun officialArea(namespace: String): String = when (namespace.lowercase()) {
        "voice" -> "voice"
        "workout" -> "workout"
        "nutrition" -> "nutrition"
        "performance", "app", "assistant", "programs", "learn", "health" -> "performance"
        "auge" -> "auge"
        "reports", "backend" -> "reports"
        "tts" -> "voice"
        else -> "performance"
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "jsonl" -> "application/x-ndjson"
        "json" -> "application/json"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "kpkn-diagnostic" }.take(120)

    private val UTC_DAY_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").withZone(java.time.ZoneOffset.UTC)
}
