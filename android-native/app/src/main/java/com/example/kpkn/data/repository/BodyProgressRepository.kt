package com.example.kpkn.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.kpkn.data.db.BodyProgressDao
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toBodyGoal
import com.example.kpkn.data.db.toBodyObservation
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMeasurementEntry
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import com.example.kpkn.data.models.BodyObservationQuality
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Single source of truth for body progress. Observations are normalized in Room;
 * the former `body_measurements` JSON preference is imported once and then removed
 * only after the row count has been verified.
 */
class BodyProgressRepository private constructor(
    context: Context,
    private val db: KpknDatabase,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Serialises Room writes so a delete cannot be overtaken by a later reload. */
    private val writeMutex = Mutex()
    private val dao: BodyProgressDao = db.bodyProgressDao()
    private val ready = CompletableDeferred<Unit>()

    private val _observations = MutableStateFlow<List<BodyObservation>>(emptyList())
    val observations: StateFlow<List<BodyObservation>> = _observations.asStateFlow()

    private val _goals = MutableStateFlow<List<BodyGoal>>(emptyList())
    val goals: StateFlow<List<BodyGoal>> = _goals.asStateFlow()

    private val _measurementSchedule = MutableStateFlow(MeasurementSchedule())
    val measurementSchedule: StateFlow<MeasurementSchedule> = _measurementSchedule.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        scope.launch {
            load()
            ready.complete(Unit)
        }
    }

    fun addObservation(observation: BodyObservation): Boolean {
        val validation = validateBodyValue(observation.metric, observation.valueSi)
        if (!validation.valid) {
            _lastError.value = validation.reason
            return false
        }
        if (observation.externalId != null && _observations.value.any { it.externalId == observation.externalId }) return true
        _lastError.value = null
        _observations.update { current ->
            (current.filterNot { it.id == observation.id } + observation).sortedBy { it.timestampEpochMs }
        }
        scope.launch {
            runCatching { writeMutex.withLock { dao.upsertObservation(observation.toEntity()) } }
                .onFailure { _lastError.value = it.message ?: "No se pudo guardar la medición" }
        }
        return true
    }

    fun addObservations(observations: List<BodyObservation>): Int = observations.count { addObservation(it) }

    /** Compatibility entry point for legacy callers; Room remains the write source. */
    fun addLegacyEntry(entry: BodyMeasurementEntry): Int {
        val timestamp = runCatching {
            LocalDate.parse(entry.date).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
        val sessionId = "legacy:${entry.id}"
        val values = listOfNotNull(
            entry.weight?.let { Triple(BodyMetric.WEIGHT, it, "kg") },
            entry.bodyFat?.let { Triple(BodyMetric.BODY_FAT_PERCENT, it, "%") },
            entry.muscleMass?.let { Triple(BodyMetric.MUSCLE_MASS_PERCENT, it, "%") },
            entry.waistCm?.let { Triple(BodyMetric.WAIST, it, "cm") },
            entry.hipCm?.let { Triple(BodyMetric.HIP, it, "cm") },
            entry.neckCm?.let { Triple(BodyMetric.NECK, it, "cm") },
            entry.chestCm?.let { Triple(BodyMetric.CHEST, it, "cm") },
            entry.armCm?.let { Triple(BodyMetric.ARM, it, "cm") },
            entry.thighCm?.let { Triple(BodyMetric.THIGH, it, "cm") },
        )
        return values.count { (metric, value, unit) ->
            addObservation(
                BodyObservation(
                    id = "${sessionId}:${metric.name}",
                    metric = metric,
                    valueSi = value,
                    unitSi = unit,
                    sessionId = sessionId,
                    timestampEpochMs = timestamp,
                    zoneId = "UTC",
                    source = BodyMetricSource.MANUAL,
                    method = BodyObservationMethod.MANUAL,
                    quality = BodyObservationQuality.MEASURED,
                ),
            )
        }
    }

    fun deleteObservation(id: String) {
        _observations.update { it.filterNot { observation -> observation.id == id } }
        scope.launch { writeMutex.withLock { dao.deleteObservation(id) } }
    }

    fun upsertGoal(goal: BodyGoal): Boolean {
        if (!goal.targetValueSi.isFinite()) return false
        _goals.update { current -> current.filterNot { it.id == goal.id } + goal }
        scope.launch { writeMutex.withLock { dao.upsertGoal(goal.toEntity()) } }
        return true
    }

    fun deleteGoal(id: String) {
        _goals.update { it.filterNot { goal -> goal.id == id } }
        scope.launch { writeMutex.withLock { dao.deleteGoal(id) } }
    }

    fun deletePlanGoals(planId: String) {
        _goals.update { it.filterNot { goal -> goal.linkedPlanId == planId && goal.origin.name == "PLAN" } }
        scope.launch { writeMutex.withLock { dao.deletePlanGoals(planId) } }
    }

    fun updateMeasurementSchedule(schedule: MeasurementSchedule) {
        val normalized = schedule.copy(
            intervalDays = schedule.intervalDays.coerceIn(1, 365),
            reminderHour = schedule.reminderHour.coerceIn(0, 23),
            reminderMinute = schedule.reminderMinute.coerceIn(0, 59),
            nextDate = if (schedule.enabled) {
                schedule.nextDate?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
                    ?: LocalDate.now().plusDays(schedule.intervalDays.coerceIn(1, 365).toLong()).toString()
            } else null,
        )
        _measurementSchedule.value = normalized
        prefs.edit().putString(KEY_SCHEDULE, Json.encodeToString(normalized)).apply()
        val notifier = NutritionNotificationManager(appContext)
        if (normalized.enabled && normalized.nextDate != null) {
            notifier.scheduleMeasurementReminder(normalized.nextDate, normalized.reminderHour, normalized.reminderMinute)
        } else {
            notifier.cancelMeasurementReminder()
        }
    }

    /** Deletes observations, goals and the recurring reminder; used by "borrar todo". */
    fun clearAllData() {
        _observations.value = emptyList()
        _goals.value = emptyList()
        _measurementSchedule.value = MeasurementSchedule()
        prefs.edit().clear().apply()
        NutritionNotificationManager(appContext).cancelMeasurementReminder()
        scope.launch { clearAllDataAndAwait() }
    }

    /** Synchronous-at-the-call-site variant used by export/reset flows. */
    suspend fun clearAllDataAndAwait() {
        awaitReady()
        prefs.edit().clear().commit()
        NutritionNotificationManager(appContext).cancelMeasurementReminder()
        _measurementSchedule.value = MeasurementSchedule()
        writeMutex.withLock {
            dao.clearObservations()
            dao.clearGoals()
        }
    }

    suspend fun awaitPersistence() {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            ready.await()
            writeMutex.withLock { dao.getAllObservations() }
        }
    }

    /** Wait until the first Room/legacy migration has completed before export or UI snapshots. */
    suspend fun awaitReady() {
        ready.await()
    }

    suspend fun refreshFromStorage() {
        awaitReady()
        load()
    }

    /** Merge an imported backup without deleting rows that are absent from that backup. */
    suspend fun importBackup(
        observations: List<BodyObservation>,
        goals: List<BodyGoal>,
        schedule: MeasurementSchedule?,
    ) {
        awaitReady()
        val validObservations = observations.filter {
            validateBodyValue(it.metric, it.valueSi).valid
        }
        writeMutex.withLock {
            db.withTransaction {
                if (validObservations.isNotEmpty()) {
                    dao.upsertObservations(validObservations.map { it.toEntity() })
                }
                goals.filter { it.targetValueSi.isFinite() }
                    .forEach { dao.upsertGoal(it.toEntity()) }
            }
        }
        if (schedule != null) {
            updateMeasurementSchedule(schedule)
        }
        load()
    }

    private suspend fun load() {
        runCatching {
            val rows = dao.getAllObservations().mapNotNull { it.toBodyObservation() }
            val goals = dao.getAllGoals().mapNotNull { it.toBodyGoal() }
            val imported = importLegacyIfNeeded(rows)
            _observations.value = (rows + imported).distinctBy { it.id }.sortedBy { it.timestampEpochMs }
            _goals.value = goals
            _measurementSchedule.value = prefs.getString(KEY_SCHEDULE, null)?.let {
                Json.decodeFromString<MeasurementSchedule>(it)
            } ?: MeasurementSchedule()
            val schedule = _measurementSchedule.value
            if (schedule.enabled && schedule.nextDate != null) {
                NutritionNotificationManager(appContext).scheduleMeasurementReminder(
                    schedule.nextDate,
                    schedule.reminderHour,
                    schedule.reminderMinute,
                )
            }
        }.onFailure {
            _lastError.value = it.message ?: "No se pudieron cargar las mediciones"
        }
    }

    private suspend fun importLegacyIfNeeded(existing: List<BodyObservation>): List<BodyObservation> {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return emptyList()
        val json = prefs.getString(KEY_LEGACY_MEASUREMENTS, null) ?: return emptyList()
        val legacy = runCatching { Json.decodeFromString<List<BodyMeasurementEntry>>(json) }.getOrDefault(emptyList())
        if (legacy.isEmpty()) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).remove(KEY_LEGACY_MEASUREMENTS).apply()
            return emptyList()
        }
        val converted = legacy.flatMap(::legacyToObservations)
        if (converted.isEmpty()) return emptyList()
        val newRows = converted.filterNot { candidate -> existing.any { it.id == candidate.id } }
        if (newRows.isNotEmpty()) dao.upsertObservations(newRows.map { it.toEntity() })
        val persistedIds = dao.getAllObservations().map { it.id }.toSet()
        val verified = converted.count { it.id in persistedIds } == converted.size
        if (verified) prefs.edit().putBoolean(KEY_MIGRATED, true).remove(KEY_LEGACY_MEASUREMENTS).apply()
        return newRows
    }

    private fun legacyToObservations(entry: BodyMeasurementEntry): List<BodyObservation> {
        val timestamp = runCatching {
            LocalDate.parse(entry.date).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
        val values = listOfNotNull(
            entry.weight?.let { BodyMetric.WEIGHT to (it to "kg") },
            entry.bodyFat?.let { BodyMetric.BODY_FAT_PERCENT to (it to "%") },
            entry.muscleMass?.let { BodyMetric.MUSCLE_MASS_PERCENT to (it to "%") },
            entry.waistCm?.let { BodyMetric.WAIST to (it to "cm") },
            entry.hipCm?.let { BodyMetric.HIP to (it to "cm") },
            entry.neckCm?.let { BodyMetric.NECK to (it to "cm") },
            entry.chestCm?.let { BodyMetric.CHEST to (it to "cm") },
            entry.armCm?.let { BodyMetric.ARM to (it to "cm") },
            entry.thighCm?.let { BodyMetric.THIGH to (it to "cm") },
        )
        return values.mapNotNull { (metric, valueAndUnit) ->
            val (value, unit) = valueAndUnit
            if (!validateBodyValue(metric, value).valid) return@mapNotNull null
            BodyObservation(
                id = "legacy:${entry.id}:${metric.name}",
                metric = metric,
                valueSi = value,
                unitSi = unit,
                sessionId = "legacy:${entry.id}",
                timestampEpochMs = timestamp,
                zoneId = "UTC",
                source = BodyMetricSource.SETTINGS_MIGRATION,
                method = BodyObservationMethod.MANUAL,
                quality = BodyObservationQuality.IMPORTED,
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "body_measurements"
        private const val KEY_LEGACY_MEASUREMENTS = "measurements"
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_MIGRATED = "room_v1_migrated"

        @Volatile private var INSTANCE: BodyProgressRepository? = null

        fun getInstance(context: Context): BodyProgressRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: BodyProgressRepository(
                context.applicationContext,
                KpknDatabase.getInstance(context.applicationContext),
            ).also { INSTANCE = it }
        }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
