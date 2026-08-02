package com.example.kpkn.services.workout

import android.app.ActivityManager
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Local, incremental diagnostics for workout voice. Audio is never stored.
 * Every JSON object is flushed as an independent line to survive process death.
 */
object WorkoutVoiceDiagnosticLogger {
    private const val TAG = "VoiceDiagnostics"
    private const val SCHEMA_VERSION = 1
    private const val MAX_FILES = 10
    private const val MAX_TOTAL_BYTES = 10L * 1024L * 1024L
    private const val MAX_TEXT_LENGTH = 12_000
    private val lock = Any()
    private var appContext: Context? = null
    private var activeFile: File? = null
    private var activeWriter: java.io.BufferedWriter? = null
    private var automaticFileUri: Uri? = null
    private var activeSessionKey: String? = null
    private var traceId: String? = null
    private var startedElapsedMs: Long = 0L

    fun initialize(context: Context) = synchronized(lock) {
        appContext = context.applicationContext
    }

    fun start(programId: String, sessionId: String): Boolean = synchronized(lock) {
        val context = appContext ?: return false
        val requestedKey = "$programId::$sessionId"
        if (activeFile?.exists() == true && activeSessionKey == requestedKey) return true
        closeLocked("superseded_by_new_workout")
        try {
            val directory = File(context.filesDir, "voice_diagnostics").apply { mkdirs() }
            prune(directory)
            val id = UUID.randomUUID().toString()
            val stamp = FILE_TIME_FORMAT.format(Instant.now())
            activeFile = File(directory, "kpkn-voice-$stamp-${id.take(8)}.jsonl")
            automaticFileUri = runCatching {
                WorkoutVoiceDiagnosticStorage.createJsonl(context, activeFile!!.name)
            }.onFailure { error -> Log.e(TAG, "Unable to create automatic JSONL copy", error) }.getOrNull()
            activeSessionKey = requestedKey
            traceId = id
            startedElapsedMs = SystemClock.elapsedRealtime()
            appendLocked(
                "diagnostic_started",
                mapOf(
                    "programId" to programId,
                    "sessionId" to sessionId,
                    "audioStored" to false,
                    "automaticCopyEnabled" to (automaticFileUri != null),
                    "automaticCopyFolder" to WorkoutVoiceDiagnosticStorage.configuredLabel(context),
                    "privacy" to "Contains recognized text and voice workflow state. No audio.",
                ),
            )
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start voice diagnostics", error)
            activeFile = null
            automaticFileUri = null
            activeSessionKey = null
            traceId = null
            false
        }
    }

    fun isActive(): Boolean = synchronized(lock) { activeFile?.exists() == true }

    fun isAutomaticStorageConfigured(): Boolean = synchronized(lock) {
        appContext?.let(WorkoutVoiceDiagnosticStorage::isConfigured) == true
    }

    fun hasExportableData(): Boolean = synchronized(lock) {
        activeFile?.exists() == true || WorkoutVoiceExitInfoCollector.hasPendingBundle()
    }

    fun suggestedFileName(): String? = synchronized(lock) {
        val sourceName = activeFile?.name ?: WorkoutVoiceExitInfoCollector.pendingFiles().firstOrNull()?.name
        sourceName?.substringBeforeLast('.')?.plus(".zip")
    }

    fun event(name: String, fields: Map<String, Any?> = emptyMap()): Unit = synchronized(lock) {
        if (activeFile == null) return@synchronized
        try {
            appendLocked(name, fields)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to append voice diagnostic", error)
        }
    }

    fun exception(name: String, error: Throwable, fields: Map<String, Any?> = emptyMap()) {
        event(
            name,
            fields + mapOf(
                "exceptionType" to error.javaClass.name,
                "exceptionMessage" to error.message,
                "stackTrace" to error.stackTraceToString(),
            ),
        )
    }

    fun exportTo(uri: Uri): Boolean = synchronized(lock) {
        val context = appContext ?: return false
        val sources = buildList {
            activeFile?.takeIf(File::exists)?.let(::add)
            File(context.filesDir, "voice_diagnostics").listFiles()
                ?.filter { file -> file.isFile && file.extension in setOf("jsonl", "trace", "json") }
                ?.sortedByDescending(File::lastModified)
                ?.take(8)
                ?.let(::addAll)
            addAll(WorkoutVoiceExitInfoCollector.pendingFiles())
        }.distinctBy(File::getAbsolutePath)
        if (sources.isEmpty()) return false
        try {
            if (activeFile != null) appendLocked("export_started")
            context.contentResolver.openOutputStream(uri, "w")?.use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    sources.forEach { source ->
                        zip.putNextEntry(ZipEntry(source.name))
                        source.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                    val metadata = JsonObject(
                        mapOf(
                            "manufacturer" to JsonPrimitive(Build.MANUFACTURER),
                            "model" to JsonPrimitive(Build.MODEL),
                            "device" to JsonPrimitive(Build.DEVICE),
                            "sdk" to JsonPrimitive(Build.VERSION.SDK_INT),
                            "release" to JsonPrimitive(Build.VERSION.RELEASE),
                            "audioStored" to JsonPrimitive(false),
                        ),
                    ).toString().toByteArray(Charsets.UTF_8)
                    zip.putNextEntry(ZipEntry("device-build.json"))
                    zip.write(metadata)
                    zip.closeEntry()
                }
            } ?: return false
            WorkoutVoiceExitInfoCollector.consumePending()
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to export voice diagnostics", error)
            event(
                "export_failed",
                mapOf("exceptionType" to error.javaClass.name, "exceptionMessage" to error.message),
            )
            false
        }
    }

    /** Snapshot used to correlate lock-screen and process-state failures. */
    fun runtimeStateFields(context: Context): Map<String, Any?> {
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        return mapOf(
            "interactive" to powerManager?.isInteractive,
            "keyguardLocked" to keyguardManager?.isKeyguardLocked,
            "powerSaveMode" to powerManager?.isPowerSaveMode,
            "processImportance" to processInfo.importance,
        )
    }

    fun updateProcessState(stage: VoicePipelineStage, sessionGeneration: Long = 0L) {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val summary = "voice=${stage.name};gen=$sessionGeneration".toByteArray(Charsets.UTF_8).take(128).toByteArray()
        runCatching {
            context.getSystemService(ActivityManager::class.java)?.setProcessStateSummary(summary)
        }
    }
    fun close(reason: String) = synchronized(lock) { closeLocked(reason) }

    private fun closeLocked(reason: String) {
        if (activeFile == null) return
        try {
            appendLocked("diagnostic_closed", mapOf("reason" to reason))
        } catch (error: Exception) {
            Log.e(TAG, "Unable to close voice diagnostics", error)
        } finally {
            runCatching { activeWriter?.close() }
            activeWriter = null
            activeFile = null
            automaticFileUri = null
            activeSessionKey = null
            traceId = null
        }
    }

    private fun appendLocked(name: String, fields: Map<String, Any?> = emptyMap()) {
        val file = activeFile ?: return
        val payload = linkedMapOf<String, Any?>(
            "schemaVersion" to SCHEMA_VERSION,
            "timestamp" to Instant.now().toString(),
            "elapsedMs" to (SystemClock.elapsedRealtime() - startedElapsedMs).coerceAtLeast(0L),
            "traceId" to traceId,
            "event" to name,
        )
        payload.putAll(fields)
        val line = JsonObject(payload.mapValues { (key, value) -> if (key.isSensitiveKey()) JsonPrimitive("[REDACTED]") else value.toJsonElement() }).toString()
        // Writer persistente: misma durabilidad (flush por línea) sin reabrir el
        // archivo en cada evento — menos syscalls en almacenamiento de gama baja.
        val writer = activeWriter
            ?: FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).also { activeWriter = it }
        writer.append(line)
        writer.newLine()
        writer.flush()
        automaticFileUri?.let { uri ->
            appContext?.let { context ->
                WorkoutVoiceDiagnosticStorage.appendLine(context, uri, line)
            }
        }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(redactSecrets(toString()).take(MAX_TEXT_LENGTH))
    }

    private fun String.isSensitiveKey(): Boolean {
        val value = lowercase()
        return "key" in value || "token" in value || "secret" in value ||
            "password" in value || "authorization" in value || "cookie" in value
    }

    private fun prune(directory: File) {
        val files = directory.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
            .toMutableList()
        var total = files.sumOf(File::length)
        while (files.size >= MAX_FILES || total > MAX_TOTAL_BYTES) {
        KpknDiagnosticLogger.event(
            namespace = "voice",
            name = name,
            fields = fields,
            traceId = traceId,
            sessionId = activeSessionKey,
        )
            val oldest = files.removeLastOrNull() ?: break
            total -= oldest.length()
            oldest.delete()
        }
    }

        is String -> JsonPrimitive(redactSecrets(this))
    private val FILE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
}
    private fun redactSecrets(value: String): String = value
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._-]+"), "$1[REDACTED]")
        .replace(Regex("(?i)(api[_ -]?key\\s*[:=]\\s*)[^\\s,]+"), "$1[REDACTED]")
        .replace(Regex("(?i)sk-[A-Za-z0-9_-]{12,}"), "[REDACTED]")

