package com.example.kpkn.data.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
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
 * Local-first, bounded JSONL diagnostics shared by the app and :voice process.
 * The local files are authoritative; SAF is only a mirror/recovery destination.
 */
object KpknDiagnosticLogger {
    const val SCHEMA_VERSION = 1
    const val MAX_CONTEXT_EVENTS = 200
    const val REPORT_NAMESPACE = "reports"

    val functionalNamespaces: List<String> = listOf(
        "nutrition",
        "auge",
        "app",
        "workout",
        "performance",
        "assistant",
        "programs",
        "learn",
        "health",
        "tts",
        "backend",
    )

    val allNamespaces: List<String> = listOf("voice") + functionalNamespaces + REPORT_NAMESPACE

    private const val TAG = "KpknDiagnostics"
    private const val MAX_FILES = 64
    private const val MAX_TOTAL_BYTES = 50L * 1024L * 1024L
    private const val MAX_FILE_BYTES = 1L * 1024L * 1024L
    private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val MAX_TEXT_LENGTH = 12_000
    private const val DIRECTORY_NAME = "kpkn_diagnostics"
    private val RESERVED_FIELDS = setOf("schemaVersion", "eventId", "timestamp", "elapsedMs", "processName", "namespace", "traceId", "sessionId", "reportId", "screen", "event")

    private val lock = Any()
    private val activeFiles = mutableMapOf<String, File>()
    private var appContext: Context? = null
    private var screen: String = "unknown"
    private var currentSessionId: String? = null

    fun initialize(context: Context) = synchronized(lock) {
        appContext = context.applicationContext
        val base = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        allNamespaces.forEach { File(base, it).mkdirs() }
    }

    fun currentScreen(): String = synchronized(lock) { screen }

    fun setCurrentScreen(value: String?) = synchronized(lock) {
        screen = value?.trim()?.takeIf { it.isNotEmpty() }?.take(180) ?: "unknown"
    }

    /** Starts a new analytics session; returns the new session id. */
    fun beginSession(): String = synchronized(lock) {
        UUID.randomUUID().toString().also { currentSessionId = it }
    }

    private fun activeSessionId(): String = synchronized(lock) {
        currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it }
    }

    fun event(
        namespace: String,
        name: String,
        fields: Map<String, Any?> = emptyMap(),
        traceId: String? = null,
        sessionId: String? = null,
        reportId: String? = null,
    ): String? = synchronized(lock) {
        val context = appContext ?: return@synchronized null
        val safeNamespace = namespace.safeNamespace()
        val directory = File(context.filesDir, "$DIRECTORY_NAME/$safeNamespace").apply { mkdirs() }
        prune(directory)
        val eventId = UUID.randomUUID().toString()
        val payload = linkedMapOf<String, Any?>(
            "schemaVersion" to SCHEMA_VERSION,
            "eventId" to eventId,
            "timestamp" to Instant.now().toString(),
            "elapsedMs" to SystemClock.elapsedRealtime(),
            "processName" to processName(),
            "namespace" to safeNamespace,
            "traceId" to (traceId ?: eventId),
            "sessionId" to (sessionId ?: activeSessionId()),
            "reportId" to reportId,
            "screen" to screen,
            "event" to name.take(180),
        )
        fields.forEach { (key, value) ->
            if (key !in RESERVED_FIELDS) payload[key] = value
        }
        val line = JsonObject(payload.mapValues { (key, value) -> if (key.isSensitiveKey()) JsonPrimitive("[REDACTED]") else value.toJsonElement() }).toString()
        val file = activeFileFor(directory, safeNamespace)
        try {
            FileOutputStream(file, true).use { output ->
                output.write(line.toByteArray(Charsets.UTF_8))
                output.write('\n'.code)
                output.flush()
            }
            KpknDiagnosticStorage.enqueueMirror(context, safeNamespace, file, line)
            eventId
        } catch (error: Exception) {
            Log.e(TAG, "Unable to write diagnostic event", error)
            null
        }
    }

    fun exception(
        namespace: String,
        name: String,
        error: Throwable,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        event(
            namespace = namespace,
            name = name,
            fields = fields + mapOf(
                "exceptionType" to error.javaClass.name,
                "exceptionMessage" to error.message,
                "stackTrace" to error.stackTraceToString(),
            ),
        )
    }

    fun eventCount(namespace: String): Int = synchronized(lock) {
        val context = appContext ?: return@synchronized 0
        File(context.filesDir, DIRECTORY_NAME + "/" + namespace.safeNamespace())
            .listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sumOf { file -> runCatching { file.readLines(Charsets.UTF_8).count(String::isNotBlank) }.getOrDefault(0) }
            ?: 0
    }

    fun latestFileName(namespace: String): String? = synchronized(lock) {
        val context = appContext ?: return@synchronized null
        File(context.filesDir, DIRECTORY_NAME + "/" + namespace.safeNamespace())
            .listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.maxByOrNull(File::lastModified)
            ?.name
    }
    fun snapshot(namespace: String, maxLines: Int = MAX_CONTEXT_EVENTS): List<String> = synchronized(lock) {
        val context = appContext ?: return@synchronized emptyList()
        val directory = File(context.filesDir, "$DIRECTORY_NAME/${namespace.safeNamespace()}")
        readTail(directory, maxLines)
    }

    fun snapshotAll(maxLines: Int = MAX_CONTEXT_EVENTS): Map<String, List<String>> = synchronized(lock) {
        allNamespaces.associateWith { namespace ->
            val context = appContext ?: return@associateWith emptyList()
            readTail(File(context.filesDir, "$DIRECTORY_NAME/$namespace"), maxLines)
        }
    }

    fun localFiles(): List<File> = synchronized(lock) {
        val context = appContext ?: return@synchronized emptyList()
        File(context.filesDir, DIRECTORY_NAME).walkTopDown()
            .filter { it.isFile && it.extension == "jsonl" }
            .toList()
    }

    fun runtimeStateFields(context: Context): Map<String, Any?> {
        val appContext = context.applicationContext
        val powerManager = runCatching { appContext.getSystemService(PowerManager::class.java) }.getOrNull()
        val keyguardManager = runCatching { appContext.getSystemService(KeyguardManager::class.java) }.getOrNull()
        val importance = runCatching {
            val processInfo = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(processInfo)
            processInfo.importance
        }.getOrNull()
        return mapOf(
            "interactive" to runCatching { powerManager?.isInteractive }.getOrNull(),
            "keyguardLocked" to runCatching { keyguardManager?.isKeyguardLocked }.getOrNull(),
            "powerSaveMode" to runCatching { powerManager?.isPowerSaveMode }.getOrNull(),
            "processImportance" to importance,
        )
    }

    fun environmentFields(context: Context): Map<String, Any?> = mapOf(
        "sdkInt" to Build.VERSION.SDK_INT,
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "voiceBluetoothConnectGranted" to runCatching {
            context.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrNull(),
    )

    private fun activeFileFor(directory: File, namespace: String): File {
        val active = activeFiles[namespace]
        if (active?.exists() == true && active.length() < MAX_FILE_BYTES) return active
        val stamp = FILE_TIME_FORMAT.format(Instant.now())
        return File(directory, "kpkn-$namespace-$stamp-${UUID.randomUUID().toString().take(8)}.jsonl")
            .also { activeFiles[namespace] = it }
    }

    private fun readTail(directory: File, maxLines: Int): List<String> {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
        val result = ArrayDeque<String>()
        for (file in files) {
            val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
            for (line in lines.asReversed()) {
                if (line.isBlank()) continue
                result.addFirst(line)
                while (result.size > maxLines) result.removeFirst()
            }
            if (result.size >= maxLines) break
        }
        return result.toList()
    }

    private fun prune(directory: File) {
        val now = System.currentTimeMillis()
        val files = directory.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
            .toMutableList()
        files.filter { now - it.lastModified() > MAX_AGE_MS }
            .forEach { file ->
                activeFiles.values.remove(file)
                file.delete()
            }
        val remaining = files.filter(File::exists).sortedByDescending(File::lastModified).toMutableList()
        var total = remaining.sumOf(File::length)
        while (remaining.size > MAX_FILES || total > MAX_TOTAL_BYTES) {
            val oldest = remaining.removeLastOrNull() ?: break
            activeFiles.values.remove(oldest)
            total -= oldest.length()
            oldest.delete()
        }
    }

    private fun processName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else "unknown"
    }.getOrDefault("unknown")

    private fun String.safeNamespace(): String =
        lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(48).ifBlank { "app" }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
            val safeKey = key.toString().take(80)
            safeKey to if (safeKey.isSensitiveKey()) JsonPrimitive("[REDACTED]") else value.toJsonElement()
        })
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        is String -> JsonPrimitive(sanitizeText(this))
        else -> JsonPrimitive(toString().take(MAX_TEXT_LENGTH))
    }

    private fun String.isSensitiveKey(): Boolean {
        val value = lowercase()
        return "key" in value || "token" in value || "secret" in value ||
            "password" in value || "authorization" in value || "cookie" in value
    }

    private fun sanitizeText(value: String): String =
        value
            .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer [REDACTED]")
            .replace(Regex("(?i)sk-[A-Za-z0-9_-]{12,}"), "[REDACTED]")
            .replace(Regex("(?i)(api[_ -]?key\\s*[:=]\\s*)[^\\s,]+"), "$1[REDACTED]")
            .take(MAX_TEXT_LENGTH)

    private val FILE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
}

