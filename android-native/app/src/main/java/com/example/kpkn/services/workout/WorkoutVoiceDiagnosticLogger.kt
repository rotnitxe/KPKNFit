package com.example.kpkn.services.workout

import android.content.Context
import android.net.Uri
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
            activeSessionKey = requestedKey
            traceId = id
            startedElapsedMs = SystemClock.elapsedRealtime()
            appendLocked(
                "diagnostic_started",
                mapOf(
                    "programId" to programId,
                    "sessionId" to sessionId,
                    "audioStored" to false,
                    "privacy" to "Contains recognized text and voice workflow state. No audio.",
                ),
            )
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start voice diagnostics", error)
            activeFile = null
            activeSessionKey = null
            traceId = null
            false
        }
    }

    fun isActive(): Boolean = synchronized(lock) { activeFile?.exists() == true }

    fun suggestedFileName(): String? = synchronized(lock) { activeFile?.name }

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
        val source = activeFile?.takeIf(File::exists) ?: return false
        try {
            appendLocked("export_started")
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
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

    fun close(reason: String) = synchronized(lock) { closeLocked(reason) }

    private fun closeLocked(reason: String) {
        if (activeFile == null) return
        try {
            appendLocked("diagnostic_closed", mapOf("reason" to reason))
        } catch (error: Exception) {
            Log.e(TAG, "Unable to close voice diagnostics", error)
        } finally {
            activeFile = null
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
        val line = JsonObject(payload.mapValues { (_, value) -> value.toJsonElement() }).toString()
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use {
            it.append(line)
            it.newLine()
            it.flush()
        }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString().take(MAX_TEXT_LENGTH))
    }

    private fun prune(directory: File) {
        val files = directory.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
            .toMutableList()
        var total = files.sumOf(File::length)
        while (files.size >= MAX_FILES || total > MAX_TOTAL_BYTES) {
            val oldest = files.removeLastOrNull() ?: break
            total -= oldest.length()
            oldest.delete()
        }
    }

    private val FILE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
}
