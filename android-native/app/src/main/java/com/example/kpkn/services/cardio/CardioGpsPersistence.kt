package com.example.kpkn.services.cardio

import android.system.Os
import com.example.kpkn.domain.cardio.GpsTrackSnapshot
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlinx.serialization.json.Json

internal enum class GpsSnapshotSource {
    CURRENT,
    LEGACY,
}

internal data class GpsSnapshotReadResult(
    val snapshot: GpsTrackSnapshot,
    val source: GpsSnapshotSource,
    val legacyFile: File? = null,
)

/** All accepted snapshot writes, reads and clears share one FIFO lane. */
internal class CardioGpsPersistenceQueue(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kpkn-cardio-gps-persistence").apply { isDaemon = true }
    },
) : AutoCloseable {
    fun execute(operation: () -> Unit) = executor.execute(operation)

    fun <T> submit(operation: () -> T): Future<T> = executor.submit<T> { operation() }

    override fun close() {
        executor.shutdownNow()
    }
}

/** Crash-safe local snapshot storage. The pending file is recoverable after process death. */
internal class CardioGpsSnapshotFileStore(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val atomicReplace: (File, File) -> Unit = { source, destination ->
        // Both files live in the same app-private directory. POSIX rename replaces
        // atomically on Android and preserves the last committed snapshot on failure.
        Os.rename(source.absolutePath, destination.absolutePath)
    },
) {
    fun read(
        currentFile: File,
        currentSessionKey: String,
        legacyFile: File? = null,
        legacySessionKey: String? = null,
    ): GpsSnapshotReadResult? {
        readRecoveringPending(currentFile, currentSessionKey)?.let { snapshot ->
            return GpsSnapshotReadResult(snapshot, GpsSnapshotSource.CURRENT)
        }
        if (legacyFile == null || legacySessionKey.isNullOrBlank()) return null
        readRecoveringPending(legacyFile, legacySessionKey)?.let { snapshot ->
            return GpsSnapshotReadResult(snapshot, GpsSnapshotSource.LEGACY, legacyFile)
        }
        return null
    }

    fun write(file: File, snapshot: GpsTrackSnapshot) {
        val parent = file.parentFile ?: error("La ruta GPS no tiene directorio padre.")
        check(parent.isDirectory || parent.mkdirs()) { "No se pudo crear el directorio GPS." }
        val pending = pendingFile(file)
        FileOutputStream(pending, false).use { stream ->
            stream.write(json.encodeToString(GpsTrackSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        atomicReplace(pending, file)
    }

    fun delete(file: File, legacyFile: File? = null) {
        pendingFile(file).delete()
        file.delete()
        if (legacyFile != null && legacyFile != file) {
            pendingFile(legacyFile).delete()
            legacyFile.delete()
        }
    }

    fun deleteLegacy(legacyFile: File) {
        pendingFile(legacyFile).delete()
        legacyFile.delete()
    }

    private fun readRecoveringPending(file: File, expectedSessionKey: String): GpsTrackSnapshot? {
        val pending = pendingFile(file)
        if (pending.exists()) {
            val pendingSnapshot = decode(pending, expectedSessionKey)
            if (pendingSnapshot != null) {
                // A valid pending payload is newer than the last atomically committed file.
                // If promotion fails, still return its contents and leave it for another retry.
                runCatching { atomicReplace(pending, file) }
                return pendingSnapshot
            }
            pending.delete()
        }
        return decode(file, expectedSessionKey)
    }

    private fun decode(file: File, expectedSessionKey: String): GpsTrackSnapshot? = runCatching {
        if (!file.isFile) return@runCatching null
        json.decodeFromString(GpsTrackSnapshot.serializer(), file.readText(Charsets.UTF_8))
            .takeIf { it.sessionKey == expectedSessionKey }
    }.getOrNull()

    private fun pendingFile(file: File): File = File(file.parentFile, "${file.name}.pending")
}

internal fun currentCardioGpsSnapshotFile(filesDir: File, sessionKey: String): File = File(
    File(filesDir, "cardio-gps"),
    MessageDigest.getInstance("SHA-256")
        .digest(sessionKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) } + ".json",
)

internal fun legacyCardioGpsSnapshotFile(filesDir: File, sessionKey: String): File = File(
    File(filesDir, "cardio-gps"),
    sessionKey.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "_").take(180) + ".json",
)

internal fun isLegacyGpsSnapshotForExecution(
    snapshot: GpsTrackSnapshot,
    expectedLegacySessionKey: String,
    minimumPointAtMs: Long,
): Boolean = minimumPointAtMs > 0L &&
    snapshot.sessionKey == expectedLegacySessionKey &&
    snapshot.points.isNotEmpty() &&
    snapshot.points.all { it.timestampEpochMs >= minimumPointAtMs }

internal fun shouldAcceptCardioGpsStartIntent(
    activeSessionKey: String?,
    activeExecutionStartedAtMs: Long,
    requestedSessionKey: String,
    requestedExecutionStartedAtMs: Long,
): Boolean {
    if (requestedSessionKey.isBlank()) return false
    val activeKey = activeSessionKey ?: return true
    if (activeKey == requestedSessionKey) {
        if (activeExecutionStartedAtMs > 0L) {
            return requestedExecutionStartedAtMs > 0L && requestedExecutionStartedAtMs >= activeExecutionStartedAtMs
        }
        return true
    }
    return requestedExecutionStartedAtMs > 0L && activeExecutionStartedAtMs > 0L &&
        requestedExecutionStartedAtMs > activeExecutionStartedAtMs
}

internal enum class CardioGpsStartIntentRestartPolicy {
    REDELIVER,
    DO_NOT_REDELIVER,
}

internal fun cardioGpsStartIntentRestartPolicy(
    acceptedByCurrentOwner: Boolean,
): CardioGpsStartIntentRestartPolicy = if (acceptedByCurrentOwner) {
    CardioGpsStartIntentRestartPolicy.REDELIVER
} else {
    CardioGpsStartIntentRestartPolicy.DO_NOT_REDELIVER
}

internal fun isCardioGpsCommandForCurrentOwner(
    activeServiceSessionKey: String?,
    trackerSessionKey: String?,
    requestedSessionKey: String,
): Boolean = activeServiceSessionKey?.let { it == requestedSessionKey }
    ?: (trackerSessionKey == requestedSessionKey)

internal fun acceptsCardioGpsStop(
    requestedSessionKey: String?,
    currentStateSessionKey: String?,
    snapshotSessionKey: String?,
    activeExecutionSessionKey: String?,
): Boolean {
    if (requestedSessionKey == null) return true
    return isCurrentCardioGpsExecution(
        sessionKey = requestedSessionKey,
        currentStateSessionKey = currentStateSessionKey,
        snapshotSessionKey = snapshotSessionKey,
        activeExecutionSessionKey = activeExecutionSessionKey,
    )
}

internal fun isCurrentCardioGpsExecution(
    sessionKey: String,
    currentStateSessionKey: String?,
    snapshotSessionKey: String?,
    activeExecutionSessionKey: String?,
): Boolean {
    if (currentStateSessionKey != sessionKey) return false
    if (snapshotSessionKey != null && snapshotSessionKey != sessionKey) return false
    return activeExecutionSessionKey == null || activeExecutionSessionKey == sessionKey
}
