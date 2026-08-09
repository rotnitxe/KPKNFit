package com.example.kpkn.services.workout

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import java.io.File
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
        // Historical exit information is an event in the central voice stream.
        // Do not recreate voice_diagnostics/ with ad-hoc JSON, trace or device
        // files: that would bypass v2 fields and the single SAF mirror.
        exits.sortedBy { info -> info.timestamp }.forEach { info ->
            val stateSummary = info.processStateSummary
                ?.toString(Charsets.UTF_8)
                ?.take(128)
            KpknDiagnosticLogger.event(
                namespace = "voice",
                name = "application_exit",
                fields = mapOf(
                    "exitTimestamp" to info.timestamp,
                    "exitProcessName" to info.processName,
                    "reason" to reasonName(info.reason),
                    "reasonCode" to info.reason,
                    "status" to info.status,
                    "importance" to info.importance,
                    "pssKiB" to info.pss,
                    "rssKiB" to info.rss,
                    "description" to info.description,
                    "voiceState" to stateSummary,
                    "traceAvailable" to runCatching {
                        info.traceInputStream?.use { true } ?: false
                    }.getOrDefault(false),
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "device" to Build.DEVICE,
                    "sdk" to Build.VERSION.SDK_INT,
                    "release" to Build.VERSION.RELEASE,
                    "supportedAbis" to Build.SUPPORTED_ABIS.joinToString(),
                    "audioStored" to false,
                ),
            )
        }
        return emptyList()
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
