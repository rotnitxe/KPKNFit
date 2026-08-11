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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.Json as KotlinJson
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The single JSONL log bus for Android diagnostics.
 *
 * Compatibility callers still pass the old namespace name. The bus maps that
 * namespace to one of the official v2 areas and keeps the old name as the
 * subsystem when it is useful for filtering. Local files are authoritative;
 * SAF is only an asynchronous mirror.
 */
object KpknDiagnosticLogger {
    const val SCHEMA_VERSION = 2
    const val MAX_CONTEXT_EVENTS = 200
    const val REPORT_NAMESPACE = "reports"
    const val LOG_ROOT = "kpkn_logs"

    val officialAreas: List<String> = listOf(
        "voice", "workout", "nutrition", "performance", "auge", "reports",
    )

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

    /** Kept for report and migration compatibility; these are not directories. */
    val allNamespaces: List<String> = listOf("voice") + functionalNamespaces + REPORT_NAMESPACE

    data class AreaSummary(
        val area: String,
        val files: Int,
        val bytes: Long,
        val lastEventTimestamp: String?,
        val lastEventAgeMin: Long?,
    )

    data class SnapshotLine(
        val file: String,
        val line: Int,
        val raw: String,
    )

    private const val TAG = "KpknDiagnostics"
    private const val MAX_FILES = 64
    private const val MAX_TOTAL_BYTES = 50L * 1024L * 1024L
    private const val MAX_FILE_BYTES = 1L * 1024L * 1024L
    private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val MAX_TEXT_LENGTH = 12_000
    private val migrationJson = KotlinJson { isLenient = true; ignoreUnknownKeys = true }
    private val RESERVED_FIELDS = setOf(
        "schemaVersion", "eventId", "timestamp", "elapsedMs", "process", "area",
        "subsystem", "namespace", "traceId", "sessionId", "reportId", "screen", "event",
    )
    private val areaForNamespace = mapOf(
        "voice" to "voice",
        "workout" to "workout",
        "nutrition" to "nutrition",
        "performance" to "performance",
        "app" to "performance",
        "auge" to "auge",
        "reports" to "reports",
        "assistant" to "performance",
        "programs" to "performance",
        "learn" to "performance",
        "health" to "performance",
        "tts" to "voice",
        "backend" to "reports",
    )

    private val lock = Any()
    private val activeFiles = mutableMapOf<String, File>()
    private var appContext: Context? = null
    private var screen: String = "unknown"
    private var currentSessionId: String? = null
    private var processStartedElapsedMs: Long = SystemClock.elapsedRealtime()

    fun initialize(context: Context) = synchronized(lock) {
        appContext = context.applicationContext
        processStartedElapsedMs = SystemClock.elapsedRealtime()
        val base = File(context.filesDir, LOG_ROOT).apply { mkdirs() }
        officialAreas.forEach { File(base, it).mkdirs() }
        migrateLegacyRoots(context.applicationContext)
    }

    fun currentScreen(): String = synchronized(lock) { screen }

    fun currentSessionId(): String? = synchronized(lock) { currentSessionId }

    fun currentElapsedMs(): Long = synchronized(lock) {
        (SystemClock.elapsedRealtime() - processStartedElapsedMs).coerceAtLeast(0L)
    }

    fun setCurrentScreen(value: String?) = synchronized(lock) {
        screen = value?.trim()?.takeIf { it.isNotEmpty() }?.take(180) ?: "unknown"
    }

    /** Starts a new app session; returns the new session id. */
    fun beginSession(): String = synchronized(lock) {
        UUID.randomUUID().toString().also { currentSessionId = it }
    }

    private fun activeSessionId(): String = synchronized(lock) {
        currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it }
    }

    fun areaFor(namespace: String): String =
        areaForNamespace[namespace.safeNamespace()] ?: "performance"

    fun subsystemFor(namespace: String): String? {
        val safe = namespace.safeNamespace()
        return safe.takeUnless { it == areaFor(it) }
    }

    fun event(
        namespace: String,
        name: String,
        fields: Map<String, Any?> = emptyMap(),
        traceId: String? = null,
        sessionId: String? = null,
        reportId: String? = null,
    ): String? = synchronized(lock) {
        val context = appContext
        if (context == null) return@synchronized null
        // CRI-ANALYSIS: el cuerpo completo va en runCatching. Antes solo el FileOutputStream
        // estaba protegido: prune()/serialización/activeFileFor() podían lanzar y propagaban
        // desde NutritionTelemetry.emit() tumbando el análisis (pipeline + salvage) con un
        // falso fracaso. La telemetría debe PROMETER que nunca lanza.
        runCatching {
            val safeNamespace = namespace.safeNamespace()
            val area = areaFor(safeNamespace)
            val subsystem = subsystemFor(safeNamespace)
            val day = DAY_FORMAT.format(Instant.now())
            val directory = File(context.filesDir, "$LOG_ROOT/$area/$day").apply { mkdirs() }
            prune(directory)
            val eventId = UUID.randomUUID().toString()
            val resolvedSessionId = sessionId ?: activeSessionId()
            val payload = linkedMapOf<String, Any?>(
                "schemaVersion" to SCHEMA_VERSION,
                "eventId" to eventId,
                "timestamp" to Instant.now().toString(),
                "elapsedMs" to (SystemClock.elapsedRealtime() - processStartedElapsedMs).coerceAtLeast(0L),
                "area" to area,
                "subsystem" to subsystem,
                "event" to name.take(180),
                "screen" to screen,
                "sessionId" to resolvedSessionId,
                "traceId" to (traceId ?: eventId),
                "process" to processKind(context),
            )
            fields.forEach { (key, value) ->
                if (key !in RESERVED_FIELDS) payload[key] = value
            }
            val line = JsonObject(
                payload.mapValues { (key, value) ->
                    if (key.isSensitiveKey()) JsonPrimitive("[REDACTED]") else value.toJsonElement()
                },
            ).toString()
            val file = activeFileFor(directory, area, resolvedSessionId)
            FileOutputStream(file, true).use { output ->
                output.write(line.toByteArray(Charsets.UTF_8))
                output.write('\n'.code)
                output.flush()
            }
            KpknDiagnosticStorage.enqueueMirror(context, area, file, line)
            eventId
        }.onFailure { error ->
            Log.e(TAG, "Unable to write diagnostic event", error)
        }.getOrNull()
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
        filesForAreaInternal(context, areaFor(namespace)).sumOf { file ->
            runCatching { file.readLines(Charsets.UTF_8).count(String::isNotBlank) }.getOrDefault(0)
        }
    }

    fun latestFileName(namespace: String): String? = synchronized(lock) {
        val context = appContext ?: return@synchronized null
        filesForAreaInternal(context, areaFor(namespace)).maxByOrNull(File::lastModified)?.let(::relativePath)
    }

    fun snapshot(namespace: String, maxLines: Int = MAX_CONTEXT_EVENTS): List<String> = synchronized(lock) {
        val context = appContext ?: return@synchronized emptyList()
        readTail(filesForAreaInternal(context, areaFor(namespace)), maxLines)
    }

    /** Same bounded tail as [snapshot], retaining physical file and line provenance. */
    fun snapshotWithRefs(namespace: String, maxLines: Int = MAX_CONTEXT_EVENTS): List<SnapshotLine> = synchronized(lock) {
        val context = appContext ?: return@synchronized emptyList()
        readTailWithRefs(filesForAreaInternal(context, areaFor(namespace)), maxLines)
    }

    /** Compatibility shape; the values now come from the official area store. */
    fun snapshotAll(maxLines: Int = MAX_CONTEXT_EVENTS): Map<String, List<String>> = synchronized(lock) {
        allNamespaces.associateWith { namespace ->
            val context = appContext ?: return@associateWith emptyList()
            readTail(filesForAreaInternal(context, areaFor(namespace)), maxLines)
        }
    }

    fun localFiles(): List<File> = synchronized(lock) {
        val context = appContext ?: return@synchronized emptyList()
        filesForAreaRoot(File(context.filesDir, LOG_ROOT))
    }

    fun filesForArea(context: Context, area: String): List<File> = synchronized(lock) {
        filesForAreaInternal(context.applicationContext, area)
    }

    fun areaSummaries(context: Context): List<AreaSummary> = synchronized(lock) {
        val now = System.currentTimeMillis()
        officialAreas.map { area ->
            val files = filesForAreaInternal(context.applicationContext, area)
            val lastFile = files.maxByOrNull(File::lastModified)
            val lastLine = lastFile?.let { file ->
                runCatching { file.readLines(Charsets.UTF_8).lastOrNull(String::isNotBlank) }.getOrNull()
            }
            val timestamp = lastLine?.let { line ->
                runCatching { Json.parseTimestamp(line) }.getOrNull()
            }
            AreaSummary(
                area = area,
                files = files.size,
                bytes = files.sumOf(File::length),
                lastEventTimestamp = timestamp,
                lastEventAgeMin = lastFile?.let { ((now - it.lastModified()).coerceAtLeast(0L) / 60_000L) },
            )
        }
    }

    fun relativePath(file: File): String {
        val context = appContext ?: return file.name
        return runCatching {
            file.relativeTo(File(context.filesDir, LOG_ROOT)).path.replace(File.separatorChar, '/')
                .let { "$LOG_ROOT/$it" }
        }.getOrDefault(file.name)
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

    /** Emits one bounded health snapshot per official area at app startup. */
    fun recordHealthChecks(context: Context) = synchronized(lock) {
        if (appContext == null) initialize(context)
        val appContext = context.applicationContext
        val today = DAY_FORMAT.format(Instant.now())
        val safConfigured = KpknDiagnosticStorage.isConfigured(appContext)
        officialAreas.forEach { area ->
            val files = filesForAreaInternal(appContext, area)
            val todayFiles = files.filter { it.parentFile?.name == today }
            val lastFile = files.maxByOrNull(File::lastModified)
            event(
                namespace = area,
                name = "logs_health_check",
                fields = mapOf(
                    "filesToday" to todayFiles.size,
                    "bytesToday" to todayFiles.sumOf(File::length),
                    "filesRetained" to files.size,
                    "bytesRetained" to files.sumOf(File::length),
                    "lastEventAgeMin" to lastFile?.let {
                        (System.currentTimeMillis() - it.lastModified()).coerceAtLeast(0L) / 60_000L
                    },
                    "localStoreReady" to File(appContext.filesDir, LOG_ROOT).isDirectory,
                    "safMirror" to if (safConfigured) "ok" else "unconfigured",
                    "safMirrorConfigured" to safConfigured,
                    "rotationBytes" to MAX_FILE_BYTES,
                    "retentionDays" to 30,
                ),
                sessionId = currentSessionId,
            )
        }
    }

    private fun filesForAreaInternal(context: Context, area: String): List<File> =
        filesForAreaRoot(File(context.filesDir, "$LOG_ROOT/${area.safeNamespace()}"))

    private fun filesForAreaRoot(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "jsonl" }.sortedBy(File::lastModified).toList()

    private fun activeFileFor(directory: File, area: String, sessionId: String?): File {
        val safeSession = sessionId.orEmpty().replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
        val key = if (area == "voice" && safeSession.isNotBlank()) "$area:$safeSession" else area
        val active = activeFiles[key]
        val isCurrentDay = active?.parentFile?.name == directory.name
        if (active?.exists() == true && isCurrentDay && active.length() < MAX_FILE_BYTES) return active
        val stamp = FILE_TIME_FORMAT.format(Instant.now())
        val prefix = if (area == "voice" && safeSession.isNotBlank()) {
            "$area-$safeSession"
        } else {
            area
        }
        return File(directory, "$prefix-$stamp-${UUID.randomUUID().toString().take(8)}.jsonl")
            .also { activeFiles[key] = it }
    }

    private fun readTail(files: List<File>, maxLines: Int): List<String> {
        val result = ArrayDeque<String>()
        for (file in files.asReversed()) {
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

    private fun readTailWithRefs(files: List<File>, maxLines: Int): List<SnapshotLine> {
        val result = ArrayDeque<SnapshotLine>()
        for (file in files.asReversed()) {
            val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
            for (index in lines.indices.reversed()) {
                val line = lines[index]
                if (line.isBlank()) continue
                result.addFirst(SnapshotLine(relativePath(file), index + 1, line))
                while (result.size > maxLines) result.removeFirst()
            }
            if (result.size >= maxLines) break
        }
        return result.toList()
    }

    private fun prune(directory: File) {
        val now = System.currentTimeMillis()
        val files = filesForAreaRoot(directory).sortedByDescending(File::lastModified).toMutableList()
        files.filter { now - it.lastModified() > MAX_AGE_MS }.forEach { file ->
            activeFiles.values.remove(file)
            file.delete()
        }
        val remaining = files.filter(File::exists).toMutableList()
        var total = remaining.sumOf(File::length)
        while (remaining.size > MAX_FILES || total > MAX_TOTAL_BYTES) {
            val oldest = remaining.removeLastOrNull() ?: break
            activeFiles.values.remove(oldest)
            total -= oldest.length()
            oldest.delete()
        }
    }

    /** Copies legacy roots into the new hierarchy without deleting user evidence. */
    private fun migrateLegacyRoots(context: Context) {
        val legacyRoots = listOf(
            "kpkn_diagnostics" to { file: File -> areaFor(file.parentFile?.name.orEmpty()) },
            "voice_diagnostics" to { _: File -> "voice" },
            "nutrition_telemetry" to { _: File -> "nutrition" },
        )
        legacyRoots.forEach { (rootName, areaResolver) ->
            val root = File(context.filesDir, rootName)
            if (!root.isDirectory) return@forEach
            root.walkTopDown().filter { it.isFile && it.extension == "jsonl" }.forEach { source ->
                val area = areaResolver(source).ifBlank { "performance" }.safeNamespace()
                val date = DAY_FORMAT.format(
                    Instant.ofEpochMilli(source.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()),
                )
                val targetDir = File(context.filesDir, "$LOG_ROOT/$area/$date").apply { mkdirs() }
                val target = File(targetDir, "legacy-v2-${source.name}")
                if (!target.exists()) {
                    runCatching {
                        source.bufferedReader(Charsets.UTF_8).useLines { lines ->
                            target.bufferedWriter(Charsets.UTF_8).use { output ->
                                lines.forEachIndexed { index, raw ->
                                    if (raw.isNotBlank()) {
                                        output.append(normalizeLegacyLine(raw, area, source, index + 1))
                                        output.newLine()
                                    }
                                }
                            }
                        }
                    }.onFailure { error ->
                        Log.e(TAG, "Unable to migrate legacy diagnostic file ${source.name}", error)
                        target.delete()
                    }
                }
            }
        }
    }

    private fun normalizeLegacyLine(raw: String, area: String, source: File, lineNumber: Int): String {
        val parsed = runCatching { migrationJson.parseToJsonElement(raw).jsonObject }.getOrNull()
        val sourceNamespace = parsed?.get("namespace")?.jsonPrimitive?.contentOrNull
        val eventId = parsed?.get("eventId")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString()
        val timestamp = parsed?.get("timestamp")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: Instant.ofEpochMilli(source.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()).toString()
        val sessionId = parsed?.get("sessionId")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: "legacy-${source.nameWithoutExtension.take(32)}"
        val base = linkedMapOf<String, JsonElement>(
            "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
            "eventId" to JsonPrimitive(eventId),
            "timestamp" to JsonPrimitive(timestamp),
            "elapsedMs" to JsonPrimitive(parsed?.get("elapsedMs")?.jsonPrimitive?.longOrNull?.coerceAtLeast(0L) ?: 0L),
            "area" to JsonPrimitive(area),
            "subsystem" to (sourceNamespace?.takeIf { areaFor(it) != area }?.let(::JsonPrimitive) ?: JsonNull),
            "event" to JsonPrimitive(
                parsed?.get("event")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: "legacy_event",
            ),
            "screen" to JsonPrimitive(
                parsed?.get("screen")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: "unknown",
            ),
            "sessionId" to JsonPrimitive(sessionId),
            "traceId" to JsonPrimitive(
                parsed?.get("traceId")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: eventId,
            ),
            "process" to JsonPrimitive(
                (
                    parsed?.get("process")?.jsonPrimitive?.contentOrNull
                        ?: parsed?.get("processName")?.jsonPrimitive?.contentOrNull
                )?.takeIf { it == "main" || it.startsWith(":") } ?: "main",
            ),
        )
        if (parsed == null) {
            base["legacyParseError"] = JsonPrimitive(true)
            base["legacyLineNumber"] = JsonPrimitive(lineNumber)
            base["rawLegacyLine"] = JsonPrimitive(sanitizeText(raw))
        } else {
            parsed.forEach { (key, value) ->
                if (key !in base && key != "schemaVersion") base[key] = value
            }
        }
        return JsonObject(base).toString()
    }

    private fun processKind(context: Context): String = runCatching {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else context.packageName
        if (name == context.packageName) "main" else name.substringAfter(context.packageName).ifBlank { "main" }
    }.getOrDefault("main")

    private fun processName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else "unknown"
    }.getOrDefault("unknown")

    private fun String.safeNamespace(): String =
        lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(48).ifBlank { "app" }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Double -> if (isFinite()) JsonPrimitive(this) else JsonNull
        is Float -> if (isFinite()) JsonPrimitive(this) else JsonNull
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

    private object Json {
        fun parseTimestamp(line: String): String? =
            Regex("\\\"timestamp\\\":\\\"([^\\\"]+)\\\"").find(line)?.groupValues?.getOrNull(1)
    }

    private val DAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    private val FILE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC)
}
