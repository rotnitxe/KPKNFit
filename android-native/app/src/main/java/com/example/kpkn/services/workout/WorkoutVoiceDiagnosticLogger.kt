package com.example.kpkn.services.workout

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Voice diagnostics adapter.
 *
 * The historical public API is retained for the voice pipeline, but all events
 * now go through [KpknDiagnosticLogger]. There is no voice-specific local root
 * or second SAF configuration anymore.
 */
object WorkoutVoiceDiagnosticLogger {
    private const val TAG = "VoiceDiagnostics"
    private const val MAX_TEXT_LENGTH = 12_000
    private val lock = Any()
    private var appContext: Context? = null
    private var activeSessionKey: String? = null
    private var traceId: String? = null
    private var startedElapsedMs: Long = 0L
    private var active = false
    private var commandsOk = 0
    private var commandsFailed = 0
    private var nativeFallbacks = 0

    fun initialize(context: Context) = synchronized(lock) {
        appContext = context.applicationContext
    }

    fun start(programId: String, sessionId: String): Boolean = synchronized(lock) {
        val context = appContext ?: return false
        val requestedKey = "$programId::$sessionId"
        if (active && activeSessionKey == requestedKey) return true
        closeLocked("superseded_by_new_workout")
        return runCatching {
            activeSessionKey = requestedKey
            traceId = UUID.randomUUID().toString()
            startedElapsedMs = SystemClock.elapsedRealtime()
            commandsOk = 0
            commandsFailed = 0
            nativeFallbacks = 0
            active = true
            appendLocked(
                "diagnostic_started",
                mapOf(
                    "programId" to programId,
                    "sessionId" to sessionId,
                    "audioStored" to false,
                    "automaticCopyEnabled" to KpknDiagnosticStorageBridge.isConfigured(context),
                    "automaticCopyFolder" to KpknDiagnosticStorageBridge.configuredLabel(context),
                    "privacy" to "Contains recognized text and voice workflow state. No audio.",
                ),
            )
            true
        }.getOrElse { error ->
            Log.e(TAG, "Unable to start voice diagnostics", error)
            activeSessionKey = null
            traceId = null
            startedElapsedMs = 0L
            active = false
            false
        }
    }

    fun isActive(): Boolean = synchronized(lock) { active }

    fun activeSessionId(): String? = synchronized(lock) {
        activeSessionKey?.substringAfter("::")?.takeIf(String::isNotBlank)
    }

    fun elapsedMs(): Long = synchronized(lock) {
        if (startedElapsedMs == 0L) 0L else (SystemClock.elapsedRealtime() - startedElapsedMs).coerceAtLeast(0L)
    }

    fun isAutomaticStorageConfigured(): Boolean = synchronized(lock) {
        appContext?.let(KpknDiagnosticStorageBridge::isConfigured) == true
    }

    fun hasExportableData(): Boolean = synchronized(lock) {
        val context = appContext ?: return@synchronized false
        KpknDiagnosticLogger.filesForArea(context, "voice").isNotEmpty() ||
            WorkoutVoiceExitInfoCollector.hasPendingBundle() ||
            File(context.filesDir, "voice_diagnostics").listFiles()?.any { it.isFile } == true
    }

    fun suggestedFileName(): String? = synchronized(lock) {
        val suffix = activeSessionId()?.take(8) ?: Instant.now().toString().replace(":", "").take(15)
        "kpkn-voice-diagnostics-$suffix.zip"
    }

    fun event(name: String, fields: Map<String, Any?> = emptyMap()): Unit = synchronized(lock) {
        if (!active) return@synchronized
        when (name) {
            "command_parsed" -> {
                if (fields["commandType"]?.toString()?.contains("Unknown", ignoreCase = true) == true) {
                    commandsFailed += 1
                } else {
                    commandsOk += 1
                }
            }
            "native_fallback_attempt" -> nativeFallbacks += 1
        }
        runCatching { appendLocked(name, fields) }
            .onFailure { error -> Log.e(TAG, "Unable to append voice diagnostic", error) }
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

    /** Explicit export called from Settings; no workout flow calls this method. */
    fun exportTo(uri: Uri): Boolean = synchronized(lock) {
        val context = appContext ?: return false
        val sources = buildList {
            KpknDiagnosticLogger.filesForArea(context, "voice").forEach(::add)
            File(context.filesDir, "voice_diagnostics").listFiles()
                ?.filter { file -> file.isFile && file.extension in setOf("jsonl", "trace", "json") }
                ?.sortedByDescending(File::lastModified)
                ?.take(16)
                ?.let(::addAll)
            addAll(WorkoutVoiceExitInfoCollector.pendingFiles())
        }.distinctBy(File::getAbsolutePath)
        if (sources.isEmpty()) return false
        runCatching {
            if (active) appendLocked("export_started")
            context.contentResolver.openOutputStream(uri, "w")?.use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    sources.forEachIndexed { index, source ->
                        zip.putNextEntry(ZipEntry("${index}-${source.name}"))
                        source.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                    val metadata = """
                        {"manufacturer":"${Build.MANUFACTURER}","model":"${Build.MODEL}","device":"${Build.DEVICE}","sdk":${Build.VERSION.SDK_INT},"audioStored":false}
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                    zip.putNextEntry(ZipEntry("device-build.json"))
                    zip.write(metadata)
                    zip.closeEntry()
                }
            } ?: error("No se pudo abrir el destino de exportación")
            WorkoutVoiceExitInfoCollector.consumePending()
            true
        }.getOrElse { error ->
            Log.e(TAG, "Unable to export voice diagnostics", error)
            event("export_failed", mapOf("exceptionType" to error.javaClass.name, "exceptionMessage" to error.message))
            false
        }
    }

    /** Snapshot used to correlate lock-screen and process-state failures. */
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

    /** Static environment fields; never throws. */
    fun environmentFields(context: Context): Map<String, Any?> = mapOf(
        "sdkInt" to Build.VERSION.SDK_INT,
        "batteryOptimizationIgnored" to runCatching {
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrNull(),
        "bluetoothConnectGranted" to runCatching {
            WorkoutVoicePermissionHelper.hasBluetoothConnectPermission(context.applicationContext)
        }.getOrNull(),
    )

    fun updateProcessState(stage: VoicePipelineStage, sessionGeneration: Long = 0L) {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val summary = "voice=${stage.name};gen=$sessionGeneration".toByteArray(Charsets.UTF_8).take(128).toByteArray()
        runCatching { context.getSystemService(ActivityManager::class.java)?.setProcessStateSummary(summary) }
    }

    fun close(reason: String) = synchronized(lock) { closeLocked(reason) }

    private fun closeLocked(reason: String) {
        if (!active) return
        runCatching {
            appendLocked(
                "session_summary",
                mapOf(
                    "durationMs" to elapsedMs(),
                    "commandsOk" to commandsOk,
                    "commandsFailed" to commandsFailed,
                    "nativeFallbacks" to nativeFallbacks,
                    "endedBy" to reason,
                ),
            )
            appendLocked("diagnostic_closed", mapOf("reason" to reason))
        }
            .onFailure { error -> Log.e(TAG, "Unable to close voice diagnostics", error) }
        active = false
        activeSessionKey = null
        traceId = null
        startedElapsedMs = 0L
        commandsOk = 0
        commandsFailed = 0
        nativeFallbacks = 0
    }

    private fun appendLocked(name: String, fields: Map<String, Any?> = emptyMap()) {
        val sessionId = activeSessionId() ?: return
        KpknDiagnosticLogger.event(
            namespace = "voice",
            name = name,
            fields = fields,
            traceId = traceId,
            sessionId = sessionId,
        )
    }

    private object KpknDiagnosticStorageBridge {
        fun isConfigured(context: Context): Boolean =
            com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.isConfigured(context)

        fun configuredLabel(context: Context): String? =
            com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.configuredLabel(context)
    }
}
