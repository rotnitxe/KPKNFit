package com.example.kpkn.screens.nutrition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyMeasurementEntry
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import com.example.kpkn.data.models.BodyObservationQuality
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.body.BodyMetricPoint
import com.example.kpkn.domain.body.CompatibleComposition
import com.example.kpkn.domain.body.bmi
import com.example.kpkn.domain.body.dailyMedianSeries
import com.example.kpkn.domain.body.ewmaTrend
import com.example.kpkn.domain.body.latestCompatibleComposition
import com.example.kpkn.domain.body.latestValidByMetric
import com.example.kpkn.domain.body.weeklyRate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class BodyProgressUiState(
    val observations: List<BodyObservation> = emptyList(),
    val goals: List<BodyGoal> = emptyList(),
    val selectedMetric: BodyMetric = BodyMetric.WEIGHT,
    val range: BodyChartRange = BodyChartRange.THREE_MONTHS,
    val points: List<BodyMetricPoint> = emptyList(),
    val trend: List<BodyMetricPoint> = emptyList(),
    val latestByMetric: Map<BodyMetric, BodyObservation> = emptyMap(),
    val latestComposition: CompatibleComposition? = null,
    val bmi: Double? = null,
    val weeklyRate: Double? = null,
    val coverageDays: Int = 0,
    val canRecordWithoutPlan: Boolean = true,
    val error: String? = null,
)

/** State holder for normalized body observations; composables only render this state. */
class BodyProgressViewModel(
    application: Application,
    private val repository: BodyProgressRepository,
    private val programRepository: ProgramRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        BodyProgressRepository.getInstance(application),
        ProgramRepository.getInstance(),
    )

    private val _selectedMetric = MutableStateFlow(BodyMetric.WEIGHT)
    private val _range = MutableStateFlow(BodyChartRange.THREE_MONTHS)
    val selectedMetric: StateFlow<BodyMetric> = _selectedMetric.asStateFlow()
    val range: StateFlow<BodyChartRange> = _range.asStateFlow()
    val measurementSchedule = repository.measurementSchedule

    /** Compatibility projection for the existing chart components; Room remains the source. */
    val legacyEntries: StateFlow<List<BodyMeasurementEntry>> = repository.observations
        .map { observations ->
            observations.groupBy { it.sessionId ?: "instant:${it.timestampEpochMs}" }
                .map { (groupId, rows) ->
                    val first = rows.minByOrNull { it.timestampEpochMs } ?: return@map null
                    val date = java.time.Instant.ofEpochMilli(first.timestampEpochMs)
                        .atZone(ZoneId.of(first.zoneId))
                        .toLocalDate()
                        .toString()
                    BodyMeasurementEntry(
                        id = groupId.removePrefix("legacy:"),
                        date = date,
                        weight = rows.firstOrNull { it.metric == BodyMetric.WEIGHT }?.valueSi,
                        bodyFat = rows.firstOrNull { it.metric == BodyMetric.BODY_FAT_PERCENT }?.valueSi,
                        muscleMass = rows.firstOrNull { it.metric == BodyMetric.MUSCLE_MASS_PERCENT }?.valueSi,
                        waistCm = rows.firstOrNull { it.metric == BodyMetric.WAIST }?.valueSi,
                        hipCm = rows.firstOrNull { it.metric == BodyMetric.HIP }?.valueSi,
                        neckCm = rows.firstOrNull { it.metric == BodyMetric.NECK }?.valueSi,
                        chestCm = rows.firstOrNull { it.metric == BodyMetric.CHEST }?.valueSi,
                        armCm = rows.firstOrNull { it.metric == BodyMetric.ARM }?.valueSi,
                        thighCm = rows.firstOrNull { it.metric == BodyMetric.THIGH }?.valueSi,
                    )
                }
                .filterNotNull()
                .sortedBy { it.date }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val calculatedState = combine(
        repository.observations,
        repository.goals,
        programRepository.settings,
        _selectedMetric,
        _range,
    ) { observations, goals, settings, metric, range ->
        val now = System.currentTimeMillis()
        val filtered = observations.filter { observation ->
            val age = now - observation.timestampEpochMs
            observation.metric == metric &&
                (range.days == null || (age >= 0L && age <= range.days * 86_400_000L))
        }
        val points = dailyMedianSeries(filtered, ZoneId.systemDefault())
        val latest = latestValidByMetric(observations)
        val weight = latest[BodyMetric.WEIGHT]?.valueSi
        val height = settings.userVitals.height
        val composition = latestCompatibleComposition(observations)
        val compositionWeight = composition?.weightKg ?: weight
        val bodyFat = composition?.bodyFatPercent
        BodyProgressUiState(
            observations = observations,
            goals = goals,
            selectedMetric = metric,
            range = range,
            points = points,
            trend = ewmaTrend(points),
            latestByMetric = latest,
            latestComposition = composition,
            bmi = bmi(compositionWeight, height),
            weeklyRate = weeklyRate(points),
            coverageDays = points.map { it.date }.distinct().size,
            canRecordWithoutPlan = true,
        )
    }

    val uiState: StateFlow<BodyProgressUiState> = calculatedState
        .combine(repository.lastError) { state, error -> state.copy(error = error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyProgressUiState())

    fun selectMetric(metric: BodyMetric) { _selectedMetric.value = metric }
    fun selectRange(range: BodyChartRange) { _range.value = range }

    fun addObservation(
        metric: BodyMetric,
        valueSi: Double,
        unitSi: String,
        sessionId: String? = UUID.randomUUID().toString(),
        source: BodyMetricSource = BodyMetricSource.MANUAL,
        method: BodyObservationMethod = BodyObservationMethod.MANUAL,
        quality: BodyObservationQuality = BodyObservationQuality.MEASURED,
        timestampEpochMs: Long = System.currentTimeMillis(),
        externalId: String? = null,
    ): Boolean = repository.addObservation(
        BodyObservation(
            id = UUID.randomUUID().toString(),
            metric = metric,
            valueSi = valueSi,
            unitSi = unitSi,
            sessionId = sessionId,
            timestampEpochMs = timestampEpochMs,
            zoneId = ZoneId.systemDefault().id,
            source = source,
            method = method,
            quality = quality,
            externalId = externalId,
        )
    )

    fun deleteObservation(id: String) = repository.deleteObservation(id)

    fun deleteLegacyMeasurement(id: String) {
        repository.observations.value
            .filter { it.sessionId == "legacy:$id" || it.sessionId == id || it.id == id }
            .forEach { repository.deleteObservation(it.id) }
    }

    /** Replace a manual historical session while keeping its stable entry id. */
    fun replaceLegacyMeasurement(existingId: String, entry: BodyMeasurementEntry): Int {
        deleteLegacyMeasurement(existingId)
        return addLegacyMeasurement(entry.copy(id = existingId))
    }

    fun addLegacyMeasurement(entry: BodyMeasurementEntry): Int {
        val timestamp = runCatching {
            java.time.LocalDate.parse(entry.date).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
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
            addObservation(metric, value, unit, sessionId = sessionId, timestampEpochMs = timestamp)
        }
    }
    fun upsertGoal(goal: BodyGoal): Boolean = repository.upsertGoal(goal)
    fun deleteGoal(id: String) = repository.deleteGoal(id)
    fun clearAllData() = repository.clearAllData()
    fun updateSchedule(schedule: com.example.kpkn.data.models.MeasurementSchedule) = repository.updateMeasurementSchedule(schedule)

    override fun onCleared() {
        super.onCleared()
        // Repository is process-scoped and intentionally outlives a screen VM.
    }
}
