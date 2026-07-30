package com.example.kpkn.services.workout

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Collects previous local process exits without audio or remote telemetry. */
object WorkoutVoiceExitInfoCollector {
    private const val PREFS = "workout_voice_exit_info"
    private const val LAST_SEEN_TIMESTAMP = "last_seen_timestamp"
    private const val MAX_EXIT_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
    private val lock = Any()
    private val started = AtomicBoolean(false)
    private var ready = false
    private var files: List<File> = emptyList()

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread({ collect(appContext) }, "kpkn-voice-exit-info").apply {
            isDaemon = true
            start()
        }
    }

    fun hasPendingBundle(): Boolean = synchronized(lock) { ready && files.any(File::exists) }

    fun pendingFiles(): List<File> = synchronized(lock) { files.filter(File::exists) }

    fun consumePending() = synchronized(lock) {
        files.forEach { file -> runCatching { file.delete() } }
        files = emptyList()
    }

    private fun collect(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            synchronized(lock) { ready = true }
            return
        }
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(LAST_SEEN_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val exits = runCatching {
            activityManager?.getHistoricalProcessExitReasons(null, 0, 20).orEmpty()
        }.getOrDefault(emptyList())
        val latestTimestamp = exits.maxOfOrNull { info -> info.timestamp } ?: lastSeen
        val relevant = exits.filter { info ->
            info.timestamp > lastSeen &&
                now - info.timestamp <= MAX_EXIT_AGE_MS &&
                info.processName.startsWith(context.packageName) &&
                info.reason in relevantReasons
        }
        val produced = if (relevant.isEmpty()) emptyList() else writeBundleParts(context, relevant)
        prefs.edit().putLong(LAST_SEEN_TIMESTAMP, maxOf(lastSeen, latestTimestamp)).apply()
        synchronized(lock) {
            files = produced
            ready = true
        }
        WorkoutVoiceDiagnosticStorage.mirrorRecoveryFiles(context, produced)
    }

    private fun writeBundleParts(context: Context, exits: List<ApplicationExitInfo>): List<File> {
        val directory = File(context.filesDir, "voice_diagnostics").apply { mkdirs() }
        val stamp = exits.maxOf { info -> info.timestamp }
        val jsonl = File(directory, "kpkn-voice-recovery-$stamp.jsonl")
        val output = mutableListOf<File>(jsonl)
        jsonl.bufferedWriter(Charsets.UTF_8).use { writer ->
            exits.sortedBy { info -> info.timestamp }.forEachIndexed { index, info ->
                val stateSummary = info.processStateSummary
                    ?.toString(Charsets.UTF_8)
                    ?.take(128)
                val event = JSONObject()
                    .put("schemaVersion", 2)
                    .put("timestamp", Instant.ofEpochMilli(info.timestamp).toString())
                    .put("event", "application_exit")
                    .put("process", info.processName)
                    .put("reason", reasonName(info.reason))
                    .put("reasonCode", info.reason)
                    .put("status", info.status)
                    .put("importance", info.importance)
                    .put("pssKiB", info.pss)
                    .put("rssKiB", info.rss)
                    .put("description", info.description)
                    .put("voiceState", stateSummary)
                    .put("audioStored", false)
                writer.append(event.toString())
                writer.newLine()
                runCatching {
                    info.traceInputStream?.use { input ->
                        val trace = File(directory, "kpkn-voice-recovery-$stamp-$index.trace")
                        FileOutputStream(trace).use(input::copyTo)
                        if (trace.length() > 0L) output += trace else trace.delete()
                    }
                }
            }
        }
        val device = File(directory, "kpkn-voice-recovery-$stamp-device.json")
        device.writeText(
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE)
                .put("supportedAbis", Build.SUPPORTED_ABIS.joinToString())
                .put("package", context.packageName)
                .put("audioStored", false)
                .toString(2),
        )
        output += device
        return output
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        else -> "OTHER_$reason"
    }

    private val relevantReasons = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_SIGNALED,
    )
}
