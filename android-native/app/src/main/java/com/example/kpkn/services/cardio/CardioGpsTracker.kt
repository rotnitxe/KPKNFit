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
import java.util.Locale
import java.util.concurrent.Executors
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
    val pointCount: Int = 0,
    val lastFixAtEpochMs: Long? = null,
)

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

/**
 * Process-local GPS coordinator. The foreground service owns its lifecycle;
 * this object gives the workout ViewModel a read-only StateFlow and persists
 * every accepted fix locally so a process recreation never loses the total.
 */
object CardioGpsTracker {
    private const val UPDATE_INTERVAL_MS = 5_000L
    private const val MIN_UPDATE_INTERVAL_MS = 3_000L
    private const val SIGNAL_LOST_AFTER_MS = 15_000L
    private const val STORAGE_DIRECTORY = "cardio-gps"

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val persistenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kpkn-cardio-gps-persistence").apply { isDaemon = true }
    }
    private val _state = MutableStateFlow(CardioGpsState())
    val state: StateFlow<CardioGpsState> = _state.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var appContext: Context? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var snapshot: GpsTrackSnapshot? = null
    private var tickerJob: Job? = null

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

    fun restoreIfAvailable(context: Context, sessionKey: String): CardioGpsState {
        synchronized(lock) {
            appContext = context.applicationContext
            if (snapshot?.sessionKey == sessionKey) {
                val currentStatus = _state.value.status
                val status = resolveRestoredCardioGpsStatus(snapshot?.paused == true, currentStatus)
                _state.value = publishStateLocked(status)
                return _state.value
            }
            val restored = readSnapshotLocked(sessionKey) ?: run {
                val empty = CardioGpsState(sessionKey = sessionKey)
                _state.value = empty
                return empty
            }
            snapshot = restored
            val status = resolveRestoredCardioGpsStatus(restored.paused, _state.value.status)
            _state.value = publishStateLocked(status)
            return _state.value
        }
    }

    fun markPermissionDenied(sessionKey: String) {
        synchronized(lock) {
            _state.value = CardioGpsState(sessionKey = sessionKey, status = CardioGpsStatus.PERMISSION_DENIED)
        }
    }

    fun start(context: Context, sessionKey: String): CardioGpsStatus {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            appContext = applicationContext
            if (!hasLocationPermission(applicationContext)) {
                _state.value = CardioGpsState(sessionKey = sessionKey, status = CardioGpsStatus.PERMISSION_DENIED)
                return CardioGpsStatus.PERMISSION_DENIED
            }
            if (!isLocationEnabled(applicationContext)) {
                _state.value = CardioGpsState(sessionKey = sessionKey, status = CardioGpsStatus.LOCATION_DISABLED)
                return CardioGpsStatus.LOCATION_DISABLED
            }
            if (snapshot?.sessionKey != sessionKey) {
                snapshot = readSnapshotLocked(sessionKey)
            }
            val baseSnapshot = snapshot ?: GpsTrackSnapshot(sessionKey = sessionKey)
            val now = System.currentTimeMillis()
            snapshot = freezeElapsedLocked(baseSnapshot).copy(
                sessionKey = sessionKey,
                activeSegmentStartedAtEpochMs = now,
                paused = false,
            )
            persistLocked()
            stopLocationUpdatesLocked()
            fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build()
            locationCallback = newLocationCallback()
            _state.value = publishStateLocked(CardioGpsStatus.REQUESTING_PERMISSION)
            @SuppressLint("MissingPermission")
            val task = fusedClient!!.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            task.addOnSuccessListener {
                synchronized(lock) {
                    if (snapshot?.sessionKey == sessionKey && !_state.value.status.let { it == CardioGpsStatus.PERMISSION_DENIED || it == CardioGpsStatus.LOCATION_DISABLED }) {
                        _state.value = publishStateLocked(CardioGpsStatus.RECORDING)
                        startTickerLocked()
                    }
                }
            }.addOnFailureListener {
                synchronized(lock) {
                    if (snapshot?.sessionKey == sessionKey) {
                        _state.value = publishStateLocked(CardioGpsStatus.SIGNAL_LOST)
                        startTickerLocked()
                    }
                }
            }
            startTickerLocked()
            return CardioGpsStatus.REQUESTING_PERMISSION
        }
    }

    fun pause() {
        synchronized(lock) {
            val current = snapshot ?: return
            snapshot = freezeElapsedLocked(current).copy(paused = true)
            stopLocationUpdatesLocked()
            persistLocked()
            _state.value = publishStateLocked(CardioGpsStatus.PAUSED)
        }
    }

    fun resume(context: Context): CardioGpsStatus {
        val key = synchronized(lock) { snapshot?.sessionKey } ?: return CardioGpsStatus.INACTIVE
        return start(context, key)
    }

    fun stop(): GpsTrackSnapshot? {
        synchronized(lock) {
            val finalSnapshot = snapshot?.let { freezeElapsedLocked(it).copy(paused = true) }
            stopLocationUpdatesLocked()
            tickerJob?.cancel()
            tickerJob = null
            snapshot = finalSnapshot
            if (finalSnapshot != null) {
                persistLocked()
                _state.value = publishStateLocked(CardioGpsStatus.STOPPED)
            }
            return finalSnapshot
        }
    }

    fun clearSession(sessionKey: String) {
        synchronized(lock) {
            if (snapshot?.sessionKey != sessionKey) return
            stopLocationUpdatesLocked()
            tickerJob?.cancel()
            tickerJob = null
            snapshot = null
            _state.value = CardioGpsState()
            snapshotFileLocked(sessionKey)?.delete()
        }
    }

    private fun newLocationCallback(): LocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            synchronized(lock) {
                val current = snapshot ?: return
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
                    val updated = snapshot!!.copy(
                        points = snapshot!!.points + point,
                        distanceMeters = snapshot!!.distanceMeters + append.distanceDeltaMeters,
                        lastFixAtEpochMs = point.timestampEpochMs,
                    )
                    snapshot = updated
                    persistLocked()
                }
                val status = if (snapshot?.lastFixAtEpochMs?.let { System.currentTimeMillis() - it > SIGNAL_LOST_AFTER_MS } == true) {
                    CardioGpsStatus.SIGNAL_LOST
                } else {
                    CardioGpsStatus.RECORDING
                }
                _state.value = publishStateLocked(status)
            }
        }
    }

    private fun startTickerLocked() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                synchronized(lock) {
                    val current = snapshot ?: return@synchronized
                    val status = when {
                        current.paused -> CardioGpsStatus.PAUSED
                        current.lastFixAtEpochMs?.let { System.currentTimeMillis() - it > SIGNAL_LOST_AFTER_MS } == true -> CardioGpsStatus.SIGNAL_LOST
                        else -> CardioGpsStatus.RECORDING
                    }
                    _state.value = publishStateLocked(status)
                }
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

    private fun persistLocked() {
        appContext ?: return
        val current = snapshot ?: return
        val file = snapshotFileLocked(current.sessionKey) ?: return
        val payload = runCatching {
            json.encodeToString(GpsTrackSnapshot.serializer(), current)
        }.getOrNull() ?: return
        persistenceExecutor.execute {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(payload)
            }
        }
    }

    private fun readSnapshotLocked(sessionKey: String): GpsTrackSnapshot? {
        val file = snapshotFileLocked(sessionKey) ?: return null
        return runCatching {
            if (!file.exists()) null else json.decodeFromString(GpsTrackSnapshot.serializer(), file.readText())
        }.getOrNull()
    }

    private fun snapshotFileLocked(sessionKey: String): File? {
        val context = appContext ?: return null
        val safe = sessionKey
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]"), "_")
            .take(180)
        return File(context.filesDir, "$STORAGE_DIRECTORY/$safe.json")
    }
}
