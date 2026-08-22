package com.example.kpkn.data.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Durabilidad solicitada para un evento de telemetría. */
enum class TelemetryPriority { NORMAL, CRITICAL }

/** Estado observable del escritor, útil para Ajustes y auditorías. */
data class DiagnosticWriterStatus(
    val queueDepth: Int,
    val lastFlushAt: Long?,
    val lastDurableSyncAt: Long?,
    val lastError: String?,
)

/**
 * Bus único de JSONL para diagnósticos Android.
 *
 * El contrato de evento sigue siendo JSONL v2. La escritura se hace en un
 * único hilo dedicado: el llamante solo serializa y encola, por lo que una
 * sesión en vivo no queda bloqueada por FileOutputStream o SAF.
 */
object KpknDiagnosticLogger {
    const val SCHEMA_VERSION = 2
    const val MAX_CONTEXT_EVENTS = 200
    const val REPORT_NAMESPACE = "reports" // compatibilidad de lectura; no es un área nueva
    const val LOG_ROOT = "kpkn_logs"

    val officialAreas: List<String> = listOf("workout", "voice", "nutrition", "app")

    val functionalNamespaces: List<String> = listOf(
        "nutrition", "auge", "app", "workout", "performance", "assistant",
        "programs", "learn", "health", "tts", "backend",
    )

    /** Namespaces antiguos conservados solo para snapshots/exportaciones. */
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

    private data class PendingEvent(
        val area: String,
        val sessionId: String,
        val day: String,
        val line: String,
        val eventId: String,
        val priority: TelemetryPriority,
    )

    private const val TAG = "KpknDiagnostics"
    private const val MAX_FILES = 64
    private const val MAX_TOTAL_BYTES = 50L * 1024L * 1024L
    private const val MAX_FILE_BYTES = 1L * 1024L * 1024L
    private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val MAX_TEXT_LENGTH = 12_000
    private const val FLUSH_INTERVAL_MS = 250L
    private const val FLUSH_BATCH_SIZE = 32
    private const val DURABLE_SYNC_INTERVAL_MS = 1_000L
    private const val MAX_QUEUE_DEPTH = 16_384

    private val migrationJson = KotlinJson { isLenient = true; ignoreUnknownKeys = true }
    private val RESERVED_FIELDS = setOf(
        "schemaVersion", "eventId", "sequence", "timestamp", "elapsedMs", "process", "area",
        "subsystem", "namespace", "traceId", "sessionId", "reportId", "screen", "event",
    )
    private val areaForNamespace = mapOf(
        "voice" to "voice",
        "workout" to "workout",
        "nutrition" to "nutrition",
        "performance" to "app",
        "app" to "app",
        "auge" to "app",
        "reports" to "app",
        "assistant" to "app",
        "programs" to "app",
        "learn" to "app",
        "health" to "app",
        "tts" to "voice",
        "backend" to "app",
    )

    private val lock = Any()
    private val queue = LinkedBlockingQueue<PendingEvent>()
    private val writer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "kpkn-jsonl-writer").apply { isDaemon = true }
    }
    private val sequence = AtomicLong(0L)
    private val activeFiles = mutableMapOf<String, File>()
    private val liveSessionVoice = mutableMapOf<String, Boolean>()

    private var appContext: Context? = null
    private var screen: String = "unknown"
    private var currentSessionId: String? = null
    private var processStartedElapsedMs: Long = SystemClock.elapsedRealtime()
    private var initializedRoot: String? = null
    private var bootstrapRecorded = false
    private var lastFlushElapsed = SystemClock.elapsedRealtime()
    private var lastDurableSyncElapsed = 0L
    private var writesSincePrune = 0
    @Volatile private var lastFlushAt: Long? = null
    @Volatile private var lastDurableSyncAt: Long? = null
    @Volatile private var lastWriteError: String? = null
    @Volatile private var backpressureMarkerRecorded = false

    init {
        writer.scheduleWithFixedDelay(
            { drainIfDue() },
            50L,
            50L,
            TimeUnit.MILLISECONDS,
        )
    }

    fun initialize(context: Context) {
        val app = context.applicationContext
        val rootPath = File(app.filesDir, LOG_ROOT).absolutePath
        val shouldBootstrap = synchronized(lock) {
            val changedRoot = initializedRoot != null && initializedRoot != rootPath
            if (changedRoot) {
                queue.clear()
                activeFiles.clear()
                liveSessionVoice.clear()
                currentSessionId = null
                bootstrapRecorded = false
                writesSincePrune = 0
            }
            appContext = app
            processStartedElapsedMs = SystemClock.elapsedRealtime()
            initializedRoot = rootPath
            File(app.filesDir, LOG_ROOT).mkdirs()
            officialAreas.forEach { File(app.filesDir, "$LOG_ROOT/$it").mkdirs() }
            if (!bootstrapRecorded) {
                bootstrapRecorded = true
                true
            } else {
                false
            }
        }

        // Migration can walk large legacy trees; never make Application.onCreate wait for it.
        writer.execute { migrateLegacyRoots(app) }
        if (shouldBootstrap) {
            officialAreas.forEach { area ->
                event(
                    namespace = area,
                    name = "area_bootstrap",
                    fields = mapOf("writer" to "async", "flushIntervalMs" to FLUSH_INTERVAL_MS),
                    priority = TelemetryPriority.CRITICAL,
                )
            }
        }
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

    fun registerLiveSession(sessionId: String, voiceEnabled: Boolean) {
        val safe = sessionId.trim()
        if (safe.isNotEmpty()) synchronized(lock) { liveSessionVoice[safe] = voiceEnabled }
    }

    fun updateLiveSessionVoiceMode(sessionId: String, voiceEnabled: Boolean) {
        registerLiveSession(sessionId, voiceEnabled)
    }

    fun endLiveSession(sessionId: String) {
        val safe = sessionId.trim()
        if (safe.isNotEmpty()) synchronized(lock) { liveSessionVoice.remove(safe) }
        flushAsync()
    }

    private fun activeSessionId(): String = synchronized(lock) {
        currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it }
    }

    fun areaFor(namespace: String): String =
        areaForNamespace[namespace.safeNamespace()] ?: "app"

    fun subsystemFor(namespace: String): String? {
        val safe = namespace.safeNamespace()
        return safe.takeUnless { it == areaFor(it) }
    }

    private fun resolveArea(namespace: String, sessionId: String?): String {
        val safe = namespace.safeNamespace()
        if (safe == "voice" || safe == "tts") return "voice"
        if (safe == "workout" && sessionId != null && synchronized(lock) { liveSessionVoice[sessionId] == true }) {
            return "voice"
        }
        return areaFor(safe)
    }

    fun event(
        namespace: String,
        name: String,
        fields: Map<String, Any?> = emptyMap(),
        traceId: String? = null,
        sessionId: String? = null,
        reportId: String? = null,
        priority: TelemetryPriority = TelemetryPriority.NORMAL,
    ): String? {
        val pending = runCatching {
            synchronized(lock) {
                val context = appContext ?: return@synchronized null
                val safeNamespace = namespace.safeNamespace()
                val resolvedSessionId = sessionId ?: activeSessionId()
                val area = resolveArea(safeNamespace, sessionId)
                val subsystem = safeNamespace.takeUnless { it == area }
                val eventId = UUID.randomUUID().toString()
                val timestamp = Instant.now()
                val payload = linkedMapOf<String, Any?>(
                    "schemaVersion" to SCHEMA_VERSION,
                    "eventId" to eventId,
                    "sequence" to sequence.incrementAndGet(),
                    "timestamp" to timestamp.toString(),
                    "elapsedMs" to (SystemClock.elapsedRealtime() - processStartedElapsedMs).coerceAtLeast(0L),
                    "area" to area,
                    "subsystem" to subsystem,
                    "event" to name.take(180),
                    "screen" to screen,
                    "sessionId" to resolvedSessionId,
                    "traceId" to (traceId ?: eventId),
                    "process" to processKind(context),
                )
                if (reportId != null) payload["legacyReportId"] = reportId
                fields.forEach { (key, value) ->
                    if (key !in RESERVED_FIELDS) payload[key] = value
                }
                val line = JsonObject(
                    payload.mapValues { (key, value) ->
                        if (key.isSensitiveKey()) JsonPrimitive("[REDACTED]") else value.toJsonElement()
                    },
                ).toString()
                PendingEvent(
                    area = area,
                    sessionId = resolvedSessionId,
                    day = DAY_FORMAT.format(timestamp),
                    line = line,
                    eventId = eventId,
                    priority = priority,
                )
            }
        }.getOrElse { error ->
            Log.e(TAG, "Unable to prepare diagnostic event", error)
            null
        } ?: return null

        queue.offer(pending)
        if (queue.size > MAX_QUEUE_DEPTH && !backpressureMarkerRecorded) {
            backpressureMarkerRecorded = true
            Log.w(TAG, "Diagnostic writer queue depth is ${queue.size}")
        } else if (queue.size < MAX_QUEUE_DEPTH / 2) {
            backpressureMarkerRecorded = false
        }
        if (pending.priority == TelemetryPriority.CRITICAL) flushAsync()
        return pending.eventId
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
            priority = TelemetryPriority.CRITICAL,
        )
    }

    fun flushAsync() {
        runCatching { writer.execute { drainAll(forceSync = false) } }
            .onFailure { error -> Log.e(TAG, "Unable to schedule diagnostic flush", error) }
    }

    /** Drains and durably syncs the writer. Call from IO/background or crash paths. */
    fun flushSync(timeoutMs: Long = 5_000L): Boolean {
        if (Thread.currentThread().name == "kpkn-jsonl-writer") {
            drainAll(forceSync = true)
            return queue.isEmpty()
        }
        val latch = CountDownLatch(1)
        return runCatching {
            writer.execute {
                try {
                    drainAll(forceSync = true)
                } finally {
                    latch.countDown()
                }
            }
            latch.await(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS) && queue.isEmpty()
        }.onFailure { error ->
            lastWriteError = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "Unable to synchronously flush diagnostics", error)
        }.getOrDefault(false)
    }

    fun awaitIdle(timeoutMs: Long = 5_000L): Boolean = flushSync(timeoutMs)

    fun writerStatus(): DiagnosticWriterStatus = DiagnosticWriterStatus(
        queueDepth = queue.size,
        lastFlushAt = lastFlushAt,
        lastDurableSyncAt = lastDurableSyncAt,
        lastError = lastWriteError,
    )

    private fun drainIfDue() {
        val age = SystemClock.elapsedRealtime() - lastFlushElapsed
        if (queue.isNotEmpty() && (queue.size >= FLUSH_BATCH_SIZE || age >= FLUSH_INTERVAL_MS)) {
            drainAll(forceSync = false)
        }
    }

    private fun drainAll(forceSync: Boolean) {
        var drained = 0
        while (true) {
            val pending = queue.poll() ?: break
            runCatching { writePending(pending, forceSync || pending.priority == TelemetryPriority.CRITICAL) }
                .onFailure { error ->
                    lastWriteError = error.message ?: error.javaClass.simpleName
                    Log.e(TAG, "Unable to write diagnostic event ${pending.eventId}", error)
                }
            drained++
        }
        if (drained > 0 || forceSync) {
            lastFlushElapsed = SystemClock.elapsedRealtime()
            lastFlushAt = System.currentTimeMillis()
            if (forceSync || SystemClock.elapsedRealtime() - lastDurableSyncElapsed >= DURABLE_SYNC_INTERVAL_MS) {
                lastDurableSyncElapsed = SystemClock.elapsedRealtime()
                lastDurableSyncAt = System.currentTimeMillis()
            }
        }
    }

    private fun writePending(pending: PendingEvent, forceSync: Boolean) {
        val context = synchronized(lock) { appContext } ?: return
        val directory = File(context.filesDir, "$LOG_ROOT/${pending.area}/${pending.day}").apply { mkdirs() }
        writesSincePrune += 1
        if (writesSincePrune >= 64) {
            writesSincePrune = 0
            pruneArea(pending.area)
        }
        val file = activeFileFor(directory, pending.area, pending.sessionId)
        FileOutputStream(file, true).use { output ->
            output.write(pending.line.toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            output.flush()
            if (forceSync || SystemClock.elapsedRealtime() - lastDurableSyncElapsed >= DURABLE_SYNC_INTERVAL_MS) {
                output.fd.sync()
                lastDurableSyncElapsed = SystemClock.elapsedRealtime()
                lastDurableSyncAt = System.currentTimeMillis()
            }
        }
        runCatching { KpknDiagnosticStorage.enqueueMirror(context, pending.area, file, pending.line) }
            .onFailure { error -> Log.w(TAG, "Unable to enqueue SAF mirror", error) }
        lastWriteError = null
    }

    fun eventCount(namespace: String): Int {
        val context = synchronized(lock) { appContext } ?: return 0
        return filesForAreaInternal(context, areaFor(namespace)).sumOf { file ->
            runCatching { file.readLines(Charsets.UTF_8).count(String::isNotBlank) }.getOrDefault(0)
        }
    }

    fun latestFileName(namespace: String): String? {
        val context = synchronized(lock) { appContext } ?: return null
        return filesForAreaInternal(context, areaFor(namespace)).maxByOrNull(File::lastModified)?.let(::relativePath)
    }

    fun snapshot(namespace: String, maxLines: Int = MAX_CONTEXT_EVENTS): List<String> {
        val context = synchronized(lock) { appContext } ?: return emptyList()
        return readTail(filesForAreaInternal(context, areaFor(namespace)), maxLines)
    }

    fun snapshotWithRefs(namespace: String, maxLines: Int = MAX_CONTEXT_EVENTS): List<SnapshotLine> {
        val context = synchronized(lock) { appContext } ?: return emptyList()
        return readTailWithRefs(filesForAreaInternal(context, areaFor(namespace)), maxLines)
    }

    fun snapshotAll(maxLines: Int = MAX_CONTEXT_EVENTS): Map<String, List<String>> =
        allNamespaces.associateWith { namespace -> snapshot(namespace, maxLines) }

    fun localFiles(): List<File> {
        val context = synchronized(lock) { appContext } ?: return emptyList()
        return filesForAreaRoot(File(context.filesDir, LOG_ROOT))
    }

    fun filesForArea(context: Context, area: String): List<File> =
        filesForAreaInternal(context.applicationContext, areaFor(area))

    fun areaSummaries(context: Context): List<AreaSummary> {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        return officialAreas.map { area ->
            val files = filesForAreaInternal(app, area)
            val lastFile = files.maxByOrNull(File::lastModified)
            val lastLine = lastFile?.let { file ->
                runCatching { file.readLines(Charsets.UTF_8).lastOrNull(String::isNotBlank) }.getOrNull()
            }
            AreaSummary(
                area = area,
                files = files.size,
                bytes = files.sumOf(File::length),
                lastEventTimestamp = lastLine?.let { line -> runCatching { Json.parseTimestamp(line) }.getOrNull() },
                lastEventAgeMin = lastFile?.let { ((now - it.lastModified()).coerceAtLeast(0L) / 60_000L) },
            )
        }
    }

    fun suggestedFileName(): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now())
        return "kpkn-full-diagnostics-$timestamp.zip"
    }

    fun exportAllTo(context: Context, uri: Uri): Boolean {
        flushSync()
        val root = File(context.filesDir, LOG_ROOT)
        val filesToExport = buildList {
            if (root.isDirectory) {
                root.walkTopDown().filter { it.isFile && it.extension in setOf("jsonl", "md", "json", "trace") }.forEach(::add)
            }
            listOf("kpkn_diagnostics", "voice_diagnostics", "nutrition_telemetry").forEach { legacyName ->
                val legacyDir = File(context.filesDir, legacyName)
                if (legacyDir.isDirectory) {
                    legacyDir.walkTopDown().filter { it.isFile && it.extension in setOf("jsonl", "md", "json", "trace") }.forEach(::add)
                }
            }
        }.distinctBy(File::getAbsolutePath)
        if (filesToExport.isEmpty()) return false
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { rawOutput ->
                java.util.zip.ZipOutputStream(rawOutput).use { zip ->
                    filesToExport.forEach { file ->
                        val relativePath = file.relativeTo(context.filesDir).path.replace('\\', '/')
                        zip.putNextEntry(java.util.zip.ZipEntry(relativePath))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            true
        }.getOrElse { error ->
            Log.e(TAG, "Unable to export all diagnostics", error)
            false
        }
    }

    fun relativePath(file: File): String {
        val context = synchronized(lock) { appContext } ?: return file.name
        return runCatching {
            file.relativeTo(File(context.filesDir, LOG_ROOT)).path.replace(File.separatorChar, '/')
                .let { "$LOG_ROOT/$it" }
        }.getOrDefault(file.name)
    }

    fun runtimeStateFields(context: Context): Map<String, Any?> {
        val app = context.applicationContext
        val powerManager = runCatching { app.getSystemService(PowerManager::class.java) }.getOrNull()
        val keyguardManager = runCatching { app.getSystemService(KeyguardManager::class.java) }.getOrNull()
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
    fun recordHealthChecks(context: Context) {
        initialize(context)
        val app = context.applicationContext
        val today = DAY_FORMAT.format(Instant.now())
        val safConfigured = KpknDiagnosticStorage.isConfigured(app)
        officialAreas.forEach { area ->
            val files = filesForAreaInternal(app, area)
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
                    "localStoreReady" to File(app.filesDir, LOG_ROOT).isDirectory,
                    "safMirror" to if (safConfigured) "ok" else "unconfigured",
                    "safMirrorConfigured" to safConfigured,
                    "rotationBytes" to MAX_FILE_BYTES,
                    "retentionDays" to 30,
                    "queueDepth" to queue.size,
                ),
            )
        }
        flushAsync()
    }

    private fun filesForAreaInternal(context: Context, area: String): List<File> =
        filesForAreaRoot(File(context.filesDir, "$LOG_ROOT/${area.safeNamespace()}"))

    private fun filesForAreaRoot(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "jsonl" }.sortedBy(File::lastModified).toList()

    private fun activeFileFor(directory: File, area: String, sessionId: String?): File {
        val safeSession = sessionId.orEmpty().replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
        val perSession = area == "voice" || area == "workout"
        val key = if (perSession && safeSession.isNotBlank()) "$area:$safeSession" else area
        val active = activeFiles[key]
        val isCurrentDay = active?.parentFile?.name == directory.name
        if (active?.exists() == true && isCurrentDay && active.length() < MAX_FILE_BYTES) return active
        val prefix = if (perSession && safeSession.isNotBlank()) "$area-$safeSession" else area
        val suffix = if (active == null) "" else "-part${sequence.incrementAndGet().toString().takeLast(4)}"
        return File(directory, "$prefix$suffix-${FILE_TIME_FORMAT.format(Instant.now())}.jsonl")
            .also { activeFiles[key] = it }
    }

    private fun pruneArea(area: String) {
        val root = synchronized(lock) { appContext?.let { File(it.filesDir, "$LOG_ROOT/$area") } } ?: return
        val now = System.currentTimeMillis()
        val files = filesForAreaRoot(root).sortedByDescending(File::lastModified).toMutableList()
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

    /** Copies legacy roots into the new hierarchy without deleting user evidence. */
    private fun migrateLegacyRoots(context: Context) {
        val legacyRoots = listOf("kpkn_diagnostics", "voice_diagnostics", "nutrition_telemetry")
        legacyRoots.forEach { rootName ->
            val root = File(context.filesDir, rootName)
            if (!root.isDirectory) return@forEach
            root.walkTopDown().filter { it.isFile && it.extension == "jsonl" }.forEach sourceLoop@{ source ->
                val namespace = when (rootName) {
                    "voice_diagnostics" -> "voice"
                    "nutrition_telemetry" -> "nutrition"
                    else -> source.parentFile?.name.orEmpty()
                }
                val area = areaFor(namespace)
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
        // Older v2 builds already used kpkn_logs but created one directory per
        // subsystem. Keep those files readable while consolidating them into
        // the canonical `app` area; no new event is ever written to the old
        // directories.
        mapOf("performance" to "app", "auge" to "app", "reports" to "app").forEach { (legacyArea, area) ->
            val root = File(context.filesDir, "$LOG_ROOT/$legacyArea")
            if (!root.isDirectory) return@forEach
            root.walkTopDown().filter { it.isFile && it.extension == "jsonl" }.forEach { source ->
                val date = DAY_FORMAT.format(
                    Instant.ofEpochMilli(source.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()),
                )
                val targetDir = File(context.filesDir, "$LOG_ROOT/$area/$date").apply { mkdirs() }
                val target = File(targetDir, "legacy-${legacyArea}-${source.name}")
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
                        Log.e(TAG, "Unable to migrate legacy area file ${source.name}", error)
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
            ?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
        val timestamp = parsed?.get("timestamp")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: Instant.ofEpochMilli(source.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()).toString()
        val sessionId = parsed?.get("sessionId")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank) ?: "legacy-${source.nameWithoutExtension.take(32)}"
        val base = linkedMapOf<String, JsonElement>(
            "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
            "eventId" to JsonPrimitive(eventId),
            "sequence" to JsonPrimitive(sequence.incrementAndGet()),
            "timestamp" to JsonPrimitive(timestamp),
            "elapsedMs" to JsonPrimitive(parsed?.get("elapsedMs")?.jsonPrimitive?.longOrNull?.coerceAtLeast(0L) ?: 0L),
            "area" to JsonPrimitive(area),
            "subsystem" to (sourceNamespace?.takeIf { areaFor(it) != area }?.let(::JsonPrimitive) ?: JsonNull),
            "event" to JsonPrimitive(parsed?.get("event")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: "legacy_event"),
            "screen" to JsonPrimitive(parsed?.get("screen")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: "unknown"),
            "sessionId" to JsonPrimitive(sessionId),
            "traceId" to JsonPrimitive(parsed?.get("traceId")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: eventId),
            "process" to JsonPrimitive(
                (parsed?.get("process")?.jsonPrimitive?.contentOrNull
                    ?: parsed?.get("processName")?.jsonPrimitive?.contentOrNull)
                    ?.takeIf { it == "main" || it.startsWith(":") } ?: "main",
            ),
        )
        if (parsed == null) {
            base["legacyParseError"] = JsonPrimitive(true)
            base["legacyLineNumber"] = JsonPrimitive(lineNumber)
            base["rawLegacyLine"] = JsonPrimitive(sanitizeText(raw))
        } else {
            parsed.forEach { (key, value) -> if (key !in base && key != "schemaVersion") base[key] = value }
        }
        return JsonObject(base).toString()
    }

    private fun processKind(context: Context): String = runCatching {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else context.packageName
        if (name == context.packageName) "main" else name.substringAfter(context.packageName).ifBlank { "main" }
    }.getOrDefault("main")

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

    private fun sanitizeText(value: String): String = value
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer [REDACTED]")
        .replace(Regex("(?i)sk-[A-Za-z0-9_-]{12,}"), "[REDACTED]")
        .replace(Regex("(?i)(api[_ -]?key\\s*[:=]\\s*)[^\\s,]+"), "$1[REDACTED]")
        .take(MAX_TEXT_LENGTH)

    private object Json {
        fun parseTimestamp(line: String): String? = Regex("\\\"timestamp\\\":\\\"([^\\\"]+)\\\"")
            .find(line)?.groupValues?.getOrNull(1)
    }

    private val DAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    private val FILE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC)
}
