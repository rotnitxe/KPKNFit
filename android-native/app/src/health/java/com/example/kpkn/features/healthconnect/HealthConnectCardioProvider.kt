package com.example.kpkn.features.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.kpkn.services.cardio.CardioHealthProvider
import com.example.kpkn.services.cardio.CardioHealthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/** Optional live heart-rate source. An empty/denied Health Connect stream is a manual fallback. */
class HealthConnectCardioProvider(context: Context) : CardioHealthProvider {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(CardioHealthState())
    override val state: StateFlow<CardioHealthState> = _state.asStateFlow()
    private var job: Job? = null

    override fun start(exerciseId: String) {
        job?.cancel()
        job = scope.launch {
            val client = runCatching {
                if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) return@runCatching null
                HealthConnectClient.getOrCreate(appContext)
            }.getOrNull()
            val permission = HealthPermission.getReadPermission(HeartRateRecord::class)
            val granted = client?.let { hc -> runCatching { permission in hc.permissionController.getGrantedPermissions() }.getOrDefault(false) } == true
            if (!granted) {
                _state.value = CardioHealthState(exerciseId = exerciseId)
                return@launch
            }
            while (isActive) {
                val heartRate = runCatching {
                    val now = Instant.now()
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(120), now),
                        ),
                    ).records.flatMap { it.samples }.lastOrNull()?.beatsPerMinute?.toInt()
                }.getOrNull()
                _state.value = CardioHealthState(
                    exerciseId = exerciseId,
                    sourceAvailable = true,
                    heartRateBpm = heartRate,
                )
                delay(POLL_MS)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _state.value = CardioHealthState()
    }

    companion object { private const val POLL_MS = 5_000L }
}
