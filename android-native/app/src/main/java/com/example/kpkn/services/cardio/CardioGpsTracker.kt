package com.example.kpkn.services.cardio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.kpkn.domain.cardio.CardioGpsEngine
import com.example.kpkn.domain.cardio.GpsTrackPoint
import com.example.kpkn.domain.cardio.GpsTrackSnapshot
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class CardioGpsStatus {
    INACTIVE,
    REQUESTING_PERMISSION,
    RECORDING,
    PAUSED,
    SIGNAL_LOST,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    STOPPED,
}

data class CardioGpsState(
    val sessionKey: String? = null,
    val status: CardioGpsStatus = CardioGpsStatus.INACTIVE,
    val distanceMeters: Double = 0.0,
    val elapsedActiveSeconds: Long = 0L,
    val paceSecondsPerKm: Int? = null,
    val kmSplitPaces: List<Int> = emptyList(),
    val pointCount: Int = 0,
    val lastFixAtEpochMs: Long? = null,
)

internal data class CardioGpsExecutionToken(
    val sessionKey: String,
    val generation: Long,
)

internal fun acceptsCardioGpsFix(
    callbackToken: CardioGpsExecutionToken,
    activeToken: CardioGpsExecutionToken?,
    state: CardioGpsState,
): Boolean = callbackToken == activeToken &&
    state.sessionKey == callbackToken.sessionKey &&
    state.status in setOf(
        CardioGpsStatus.REQUESTING_PERMISSION,
        CardioGpsStatus.RECORDING,
        CardioGpsStatus.SIGNAL_LOST,
    )

internal fun acceptsCardioGpsRequestCompletion(
    callbackToken: CardioGpsExecutionToken,
    activeToken: CardioGpsExecutionToken?,
    state: CardioGpsState,
): Boolean = callbackToken == activeToken &&
    state.sessionKey == callbackToken.sessionKey &&
    state.status == CardioGpsStatus.REQUESTING_PERMISSION

internal fun resolveRestoredCardioGpsStatus(
    snapshotPaused: Boolean,
    currentStatus: CardioGpsStatus,
): CardioGpsStatus {
    if (snapshotPaused) return CardioGpsStatus.PAUSED
    return when (currentStatus) {
        // These statuses mean the current process/service still owns the live
        // request. Preserve them when the editor re-enters the same session.
        CardioGpsStatus.RECORDING,
        CardioGpsStatus.REQUESTING_PERMISSION,
        CardioGpsStatus.SIGNAL_LOST,
        CardioGpsStatus.PERMISSION_DENIED,
        CardioGpsStatus.LOCATION_DISABLED,
        -> currentStatus
        // A freshly recreated process has only a persisted snapshot, not an
        // active FusedLocation request. The UI must offer "Iniciar GPS" so the
        // foreground service can register again instead of showing "Pausar".
        CardioGpsStatus.INACTIVE,
        CardioGpsStatus.PAUSED,
        CardioGpsStatus.STOPPED,
        -> CardioGpsStatus.INACTIVE
    }
}

/** An in-flight start owns its request even before its snapshot read completes. */
internal fun preservesActiveCardioGpsExecutionOnRestore(
    requestedSessionKey: String,
    state: CardioGpsState,
    activeExecutionSessionKey: String?,
): Boolean = state.sessionKey == requestedSessionKey &&
    activeExecutionSessionKey == requestedSessionKey &&
    state.status in setOf(
        CardioGpsStatus.REQUESTING_PERMISSION,
        CardioGpsStatus.RECORDING,
        CardioGpsStatus.SIGNAL_LOST,
    )

/**
 * Process-local GPS coordinator. The foreground service owns its lifecycle;
 * this object gives the workout ViewModel a read-only StateFlow and persists
 * every accepted fix locally so a process recreation never loses the total.
 */
object CardioGpsTracker {
    private const val UPDATE_INTERVAL_MS = 5_000L
    private const val MIN_UPDATE_INTERVAL_MS = 3_000L
    private const val SIGNAL_LOST_AFTER_MS = 15_000L
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val persistenceQueue = CardioGpsPersistenceQueue()
    private val _state = MutableStateFlow(CardioGpsState())
    val state: StateFlow<CardioGpsState> = _state.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val fileStore = CardioGpsSnapshotFileStore(json)
    private var appContext: Context? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var snapshot: GpsTrackSnapshot? = null
    private val snapshotFiles = mutableMapOf<String, File>()
    private val legacyFilesBySessionKey = mutableMapOf<String, File>()
    private var executionGeneration = 0L
    private var restoreGeneration = 0L
    private var activeExecutionToken: CardioGpsExecutionToken? = null
    private var tickerJob: Job? = null

    private data class RestoreTicket(
        val generation: Long,
        val snapshotFile: File,
        val legacyFile: File?,
        val inMemorySnapshot: GpsTrackSnapshot?,
    )

    private data class StartTicket(
        val token: CardioGpsExecutionToken,
        val snapshotFile: File,
        val inMemorySnapshot: GpsTrackSnapshot?,
    )

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    suspend fun restoreIfAvailable(
        context: Context,
        sessionKey: String,
        legacySessionKey: String? = null,
        executionStartedAtMs: Long = 0L,
        legacyMinPointAtMs: Long = executionStartedAtMs,
    ): CardioGpsState = withContext(Dispatchers.IO) {
        val applicationContext = context.applicationContext
        val ticket = synchronized(lock) {
            appContext = applicationContext
            if (preservesActiveCardioGpsExecutionOnRestore(
                    requestedSessionKey = sessionKey,
                    state = _state.value,
                    activeExecutionSessionKey = activeExecutionToken?.sessionKey,
                )
            ) {
                // start() has reserved this key/token but may still be waiting on
                // the serialized snapshot read. A same-key restore is a no-op.
                return@withContext _state.value
            }
            if (snapshot?.sessionKey == sessionKey && _state.value.sessionKey == sessionKey) {
                val status = resolveRestoredCardioGpsStatus(snapshot?.paused == true, _state.value.status)
                _state.value = publishStateLocked(status)
                return@withContext _state.value
            }
            restoreGeneration += 1L
            val inMemory = snapshot?.takeIf { it.sessionKey == sessionKey }
            if (snapshot?.sessionKey != sessionKey) {
                stopLocationUpdatesLocked()
                invalidateExecutionTokenLocked()
                tickerJob?.cancel()
                tickerJob = null
                snapshot = null
                _state.value = CardioGpsState(sessionKey = sessionKey)
            }
            val snapshotFile = snapshotFileLocked(sessionKey)
                ?: return@withContext CardioGpsState(sessionKey = sessionKey)
            RestoreTicket(
                generation = restoreGeneration,
                snapshotFile = snapshotFile,
                legacyFile = legacySessionKey?.let { legacyCardioGpsSnapshotFile(applicationContext.filesDir, it) },
                inMemorySnapshot = inMemory,
            )
        }

        // The writer lane orders this read after every accepted write/delete. The
        // blocking wait occurs on IO and never while holding the tracker lock.
        val readResult = ticket.inMemorySnapshot?.let {
            GpsSnapshotReadResult(it, GpsSnapshotSource.CURRENT)
        } ?: persistenceQueue.submit {
            fileStore.read(
                currentFile = ticket.snapshotFile,
                currentSessionKey = sessionKey,
                legacyFile = ticket.legacyFile,
                legacySessionKey = legacySessionKey,
            )
        }.get()

        val validResult = when {
            readResult?.source != GpsSnapshotSource.LEGACY -> readResult
            legacySessionKey != null && isLegacyGpsSnapshotForExecution(
                snapshot = readResult.snapshot,
                expectedLegacySessionKey = legacySessionKey,
                minimumPointAtMs = legacyMinPointAtMs,
            ) -> readResult
            else -> {
                readResult.legacyFile?.let { legacy -> persistenceQueue.execute { fileStore.deleteLegacy(legacy) } }
                null
            }
        }

        synchronized(lock) {
            if (ticket.generation != restoreGeneration) return@synchronized _state.value
            val accepted = validResult
            if (accepted == null) {
                snapshot = null
                _state.value = CardioGpsState(sessionKey = sessionKey)
                return@synchronized _state.value
            }
            val restored = accepted.snapshot.copy(sessionKey = sessionKey)
            snapshot = restored
            snapshotFiles[sessionKey] = ticket.snapshotFile
            if (accepted.source == GpsSnapshotSource.LEGACY) {
                ticket.legacyFile?.let { legacyFilesBySessionKey[sessionKey] = it }
            }
            val legacyCleanup = if (accepted.source == GpsSnapshotSource.LEGACY) accepted.legacyFile else ticket.legacyFile
            val currentStatus = _state.value.status.takeIf { _state.value.sessionKey == sessionKey }
                ?: CardioGpsStatus.INACTIVE
            val status = resolveRestoredCardioGpsStatus(restored.paused, currentStatus)
            persistLocked(
                deleteLegacyAfterSuccess = legacyCleanup,
            )
            _state.value = publishStateLocked(status)
            _state.value
        }
    }

    fun markPermissionDenied(sessionKey: String) {
        synchronized(lock) {
            if (!isCurrentCardioGpsExecution(
                    sessionKey = sessionKey,
                    currentStateSessionKey = _state.value.sessionKey,
                    snapshotSessionKey = snapshot?.sessionKey,
                    activeExecutionSessionKey = activeExecutionToken?.sessionKey,
                )
            ) return
            restoreGeneration += 1L
            stopLocationUpdatesLocked()
            invalidateExecutionTokenLocked()
            tickerJob?.cancel()
            tickerJob = null
            if (snapshot?.sessionKey != sessionKey) {
                snapshot = null
            } else if (snapshot != null) {
                snapshot = freezeElapsedLocked(snapshot!!).copy(paused = true)
            }
            _state.value = if (snapshot?.sessionKey == sessionKey) {
                publishStateLocked(CardioGpsStatus.PERMISSION_DENIED)
            } else {
                CardioGpsState(sessionKey = sessionKey, status = CardioGpsStatus.PERMISSION_DENIED)
            }
        }
    }

    suspend fun start(context: Context, sessionKey: String): CardioGpsStatus = withContext(Dispatchers.IO) {
        val applicationContext = context.applicationContext
        val ticket = synchronized(lock) {
            appContext = applicationContext
            restoreGeneration += 1L
            stopLocationUpdatesLocked()
            tickerJob?.cancel()
            tickerJob = null
            invalidateExecutionTokenLocked()
            val token = newExecutionTokenLocked(sessionKey)
            val snapshotFile = snapshotFileLocked(sessionKey)
                ?: return@withContext CardioGpsStatus.INACTIVE
            val inMemory = snapshot?.takeIf { it.sessionKey == sessionKey }
            if (snapshot?.sessionKey != sessionKey) snapshot = null
            _state.value = inMemory?.let {
                publishStateLocked(CardioGpsStatus.REQUESTING_PERMISSION)
            } ?: CardioGpsState(sessionKey = sessionKey, status = CardioGpsStatus.REQUESTING_PERMISSION)
            StartTicket(token, snapshotFile, inMemory)
        }

        val readResult = ticket.inMemorySnapshot?.let {
            GpsSnapshotReadResult(it, GpsSnapshotSource.CURRENT)
        } ?: persistenceQueue.submit {
            fileStore.read(ticket.snapshotFile, sessionKey)
        }.get()
        val permissionGranted = hasLocationPermission(applicationContext)
        val locationEnabled = isLocationEnabled(applicationContext)

        synchronized(lock) {
            if (activeExecutionToken != ticket.token) return@synchronized _state.value.status
            snapshot = readResult?.snapshot?.let { freezeElapsedLocked(it).copy(paused = true) }
                ?: GpsTrackSnapshot(sessionKey = sessionKey)
            snapshotFiles[sessionKey] = ticket.snapshotFile
            if (!permissionGranted || !locationEnabled) {
                invalidateExecutionTokenLocked()
                val status = if (!permissionGranted) CardioGpsStatus.PERMISSION_DENIED else CardioGpsStatus.LOCATION_DISABLED
                _state.value = publishStateLocked(status)
                return@synchronized status
            }
            val baseSnapshot = snapshot ?: GpsTrackSnapshot(sessionKey = sessionKey)
            val now = System.currentTimeMillis()
            snapshot = freezeElapsedLocked(baseSnapshot).copy(
                sessionKey = sessionKey,
                activeSegmentStartedAtEpochMs = now,
                paused = false,
            )
            persistLocked()
            fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            // Outdoor cardio needs GNSS-capable precision; this request exists only for an active GPS execution.
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build()
            locationCallback = newLocationCallback(ticket.token)
            _state.value = publishStateLocked(CardioGpsStatus.REQUESTING_PERMISSION)
            @SuppressLint("MissingPermission")
            val task = try {
                fusedClient!!.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            } catch (_: SecurityException) {
                invalidateExecutionTokenLocked()
                stopLocationUpdatesLocked()
                _state.value = publishStateLocked(CardioGpsStatus.PERMISSION_DENIED)
                return@synchronized CardioGpsStatus.PERMISSION_DENIED
            }
            task.addOnSuccessListener {
                synchronized(lock) {
                    if (acceptsCardioGpsRequestCompletion(ticket.token, activeExecutionToken, _state.value)) {
                        _state.value = publishStateLocked(CardioGpsStatus.RECORDING)
                        startTickerLocked(ticket.token)
                    }
                }
            }.addOnFailureListener {
                synchronized(lock) {
                    if (acceptsCardioGpsRequestCompletion(ticket.token, activeExecutionToken, _state.value)) {
                        _state.value = publishStateLocked(CardioGpsStatus.SIGNAL_LOST)
                        startTickerLocked(ticket.token)
                    }
                }
            }
            startTickerLocked(ticket.token)
            CardioGpsStatus.REQUESTING_PERMISSION
        }
    }

    fun pause(sessionKey: String): Boolean = synchronized(lock) {
        val current = snapshot?.takeIf { it.sessionKey == sessionKey } ?: return@synchronized false
        if (activeExecutionToken?.sessionKey != sessionKey) return@synchronized false
        snapshot = freezeElapsedLocked(current).copy(paused = true)
        invalidateExecutionTokenLocked()
        stopLocationUpdatesLocked()
        tickerJob?.cancel()
        tickerJob = null
        persistLocked()
        _state.value = publishStateLocked(CardioGpsStatus.PAUSED)
        true
    }

    suspend fun resume(context: Context, sessionKey: String): CardioGpsStatus {
        val canResume = synchronized(lock) {
            snapshot?.sessionKey == sessionKey && _state.value.sessionKey == sessionKey &&
                _state.value.status in setOf(
                    CardioGpsStatus.PAUSED,
                    CardioGpsStatus.STOPPED,
                    CardioGpsStatus.PERMISSION_DENIED,
                    CardioGpsStatus.LOCATION_DISABLED,
                )
        }
        if (!canResume) return CardioGpsStatus.INACTIVE
        return start(context, sessionKey)
    }

    fun stop(sessionKey: String? = null): GpsTrackSnapshot? = synchronized(lock) {
        val current = snapshot
        val activeToken = activeExecutionToken
        if (!acceptsCardioGpsStop(
                requestedSessionKey = sessionKey,
                currentStateSessionKey = _state.value.sessionKey,
                snapshotSessionKey = current?.sessionKey,
                activeExecutionSessionKey = activeToken?.sessionKey,
            )
        ) {
            return@synchronized null
        }
        if (current == null || (sessionKey != null && current.sessionKey != sessionKey)) {
            if (sessionKey != null && activeToken?.sessionKey == sessionKey) {
                invalidateExecutionTokenLocked()
                stopLocationUpdatesLocked()
                tickerJob?.cancel()
                tickerJob = null
                _state.value = CardioGpsState(sessionKey = sessionKey, status = CardioGpsStatus.STOPPED)
            }
            return@synchronized null
        }
        invalidateExecutionTokenLocked()
        stopLocationUpdatesLocked()
        tickerJob?.cancel()
        tickerJob = null
        val finalSnapshot = freezeElapsedLocked(current).copy(paused = true)
        snapshot = finalSnapshot
        persistLocked()
        _state.value = publishStateLocked(CardioGpsStatus.STOPPED)
        finalSnapshot
    }

    fun clearSession(sessionKey: String) {
        synchronized(lock) {
            restoreGeneration += 1L
            if (snapshot?.sessionKey == sessionKey || activeExecutionToken?.sessionKey == sessionKey) {
                invalidateExecutionTokenLocked()
                stopLocationUpdatesLocked()
                tickerJob?.cancel()
                tickerJob = null
            }
            if (snapshot?.sessionKey == sessionKey) {
                snapshot = null
                _state.value = CardioGpsState()
            } else if (_state.value.sessionKey == sessionKey) {
                _state.value = CardioGpsState()
            }
            val contextForDelete = appContext
            val cachedFile = snapshotFiles.remove(sessionKey)
            val legacyFile = legacyFilesBySessionKey.remove(sessionKey)
            if (cachedFile != null || contextForDelete != null) {
                // Invalidate synchronously above, but resolve an uncached path on
                // the serialized IO writer. All accepted writes precede this delete.
                persistenceQueue.execute {
                    val file = cachedFile ?: contextForDelete?.let {
                        currentCardioGpsSnapshotFile(it.filesDir, sessionKey)
                    } ?: return@execute
                    fileStore.delete(file, legacyFile)
                }
            }
        }
    }

    private fun newLocationCallback(token: CardioGpsExecutionToken): LocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            synchronized(lock) {
                if (!acceptsCardioGpsFix(token, activeExecutionToken, _state.value)) return
                var current = snapshot?.takeIf { it.sessionKey == token.sessionKey } ?: return
                result.locations.forEach { location ->
                    val point = GpsTrackPoint(
                        timestampEpochMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                        speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
                    )
                    val append = CardioGpsEngine.append(current.points.lastOrNull(), point)
                    if (!append.accepted) return@forEach
                    val updated = current.copy(
                        points = current.points + point,
                        distanceMeters = current.distanceMeters + append.distanceDeltaMeters,
                        lastFixAtEpochMs = point.timestampEpochMs,
                    )
                    snapshot = updated
                    current = updated
                    persistLocked()
                }
                val currentStatus = _state.value.status
                val status = when {
                    currentStatus == CardioGpsStatus.REQUESTING_PERMISSION -> CardioGpsStatus.REQUESTING_PERMISSION
                    current.lastFixAtEpochMs?.let { System.currentTimeMillis() - it > SIGNAL_LOST_AFTER_MS } == true -> CardioGpsStatus.SIGNAL_LOST
                    else -> CardioGpsStatus.RECORDING
                }
                _state.value = publishStateLocked(status)
            }
        }
    }

    private fun startTickerLocked(token: CardioGpsExecutionToken) {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val stillCurrent = synchronized(lock) {
                    if (activeExecutionToken != token || _state.value.sessionKey != token.sessionKey) return@synchronized false
                    val current = snapshot?.takeIf { it.sessionKey == token.sessionKey } ?: return@synchronized false
                    val status = when {
                        current.paused -> CardioGpsStatus.PAUSED
                        _state.value.status == CardioGpsStatus.REQUESTING_PERMISSION -> CardioGpsStatus.REQUESTING_PERMISSION
                        (current.lastFixAtEpochMs ?: current.activeSegmentStartedAtEpochMs)
                            ?.let { System.currentTimeMillis() - it > SIGNAL_LOST_AFTER_MS } == true -> CardioGpsStatus.SIGNAL_LOST
                        else -> CardioGpsStatus.RECORDING
                    }
                    _state.value = publishStateLocked(status)
                    true
                }
                if (!stillCurrent) return@launch
            }
        }
    }

    private fun publishStateLocked(status: CardioGpsStatus): CardioGpsState {
        val current = snapshot ?: return CardioGpsState(status = status)
        val elapsed = elapsedSecondsLocked(current)
        return CardioGpsState(
            sessionKey = current.sessionKey,
            status = status,
            distanceMeters = current.distanceMeters,
            elapsedActiveSeconds = elapsed,
            paceSecondsPerKm = CardioGpsEngine.paceSecondsPerKm(current.distanceMeters, elapsed),
            kmSplitPaces = CardioGpsEngine.kmSplitPaces(current.points),
            pointCount = current.points.size,
            lastFixAtEpochMs = current.lastFixAtEpochMs,
        )
    }

    private fun elapsedSecondsLocked(current: GpsTrackSnapshot): Long {
        val activeStart = current.activeSegmentStartedAtEpochMs ?: return current.elapsedActiveSeconds
        return current.elapsedActiveSeconds + ((System.currentTimeMillis() - activeStart).coerceAtLeast(0L) / 1_000L)
    }

    private fun freezeElapsedLocked(current: GpsTrackSnapshot): GpsTrackSnapshot = current.copy(
        elapsedActiveSeconds = elapsedSecondsLocked(current),
        activeSegmentStartedAtEpochMs = null,
    )

    private fun stopLocationUpdatesLocked() {
        val client = fusedClient
        val callback = locationCallback
        if (client != null && callback != null) {
            runCatching { client.removeLocationUpdates(callback) }
        }
        locationCallback = null
    }

    private fun persistLocked(deleteLegacyAfterSuccess: File? = null) {
        val current = snapshot ?: return
        val file = snapshotFiles[current.sessionKey] ?: return
        persistenceQueue.execute {
            runCatching {
                fileStore.write(file, current)
                deleteLegacyAfterSuccess?.let { legacy ->
                    fileStore.deleteLegacy(legacy)
                    synchronized(lock) {
                        if (legacyFilesBySessionKey[current.sessionKey] == legacy) {
                            legacyFilesBySessionKey.remove(current.sessionKey)
                        }
                    }
                }
            }.onFailure { error ->
                com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.event(
                    namespace = "workout", name = "gps_snapshot_failed",
                    fields = mapOf("sessionKey" to current.sessionKey, "exceptionType" to error.javaClass.name),
                )
            }
        }
    }

    private fun snapshotFileLocked(sessionKey: String): File? {
        snapshotFiles[sessionKey]?.let { return it }
        val context = appContext ?: return null
        return currentCardioGpsSnapshotFile(context.filesDir, sessionKey).also {
            snapshotFiles[sessionKey] = it
        }
    }

    private fun newExecutionTokenLocked(sessionKey: String): CardioGpsExecutionToken =
        CardioGpsExecutionToken(sessionKey, ++executionGeneration).also { activeExecutionToken = it }

    private fun invalidateExecutionTokenLocked() {
        executionGeneration += 1L
        activeExecutionToken = null
    }
}
