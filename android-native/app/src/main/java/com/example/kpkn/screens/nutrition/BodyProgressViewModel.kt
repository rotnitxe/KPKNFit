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
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.body.BodyGoalTrack
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.domain.body.BodyMetricPoint
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.domain.body.CompatibleComposition
import com.example.kpkn.domain.body.ResolvedBodyGoal
import com.example.kpkn.domain.body.bmi
import com.example.kpkn.domain.body.bodyDailySeries
import com.example.kpkn.domain.body.bodyGoalTrack
import com.example.kpkn.domain.body.buildBodyHistory
import com.example.kpkn.domain.body.dailyMedianSeries
import com.example.kpkn.domain.body.ewmaTrend
import com.example.kpkn.domain.body.latestCompatibleComposition
import com.example.kpkn.domain.body.latestValidByMetric
import com.example.kpkn.domain.body.localMeasurementEpochMs
import com.example.kpkn.domain.body.measuredWeeklyRate
import com.example.kpkn.domain.body.observationsInLastDays
import com.example.kpkn.domain.body.resolveBodyGoal
import com.example.kpkn.domain.body.summarizeWeight
import com.example.kpkn.domain.body.weeklyRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
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
    // Cifras de Cuerpo calculadas una vez en el ViewModel y leídas por la pantalla.
    val goal: ResolvedBodyGoal? = null,
    val weightSummary: BodyWeightSummary = BodyWeightSummary(),
    val weightSeries: List<BodyMetricPoint> = emptyList(),
    val weightTrend: List<BodyMetricPoint> = emptyList(),
    val bodyFatSeries: List<BodyMetricPoint> = emptyList(),
    val bodyFatTrend: List<BodyMetricPoint> = emptyList(),
    val bodyFatWeeklyRate: Double? = null,
    /** Ritmo, avance y fecha de la meta cuando no es de peso (grasa o músculo). */
    val goalTrack: BodyGoalTrack? = null,
    /** false hasta que termina la primera carga del repositorio. */
    val loaded: Boolean = false,
    /** Peso de los últimos 30 días, sin depender del rango: la isla de Cuerpo muestra su cambio. */
    val weightSummary30Days: BodyWeightSummary = BodyWeightSummary(),
)

/** State holder for normalized body observations; composables only render this state. */
class BodyProgressViewModel(
    application: Application,
    private val repository: BodyProgressRepository,
    private val programRepository: ProgramRepository,
    private val nutritionRepository: NutritionRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        BodyProgressRepository.getInstance(application),
        ProgramRepository.getInstance(),
        NutritionRepository.getInstance(),
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
            // IMC con el último pesaje: el mismo peso que muestra la pantalla.
            bmi = bmi(weight, height),
            weeklyRate = weeklyRate(points),
            coverageDays = points.map { it.date }.distinct().size,
            canRecordWithoutPlan = true,
        )
    }

    /** Plan activo de nutrición: solo se lee para resolver la meta de Cuerpo. */
    private val activePlan: Flow<NutritionPlan?> = combine(
        nutritionRepository.nutritionPlans,
        nutritionRepository.activeNutritionPlanId,
    ) { plans, activeId -> plans.firstOrNull { it.id == activeId } }

    private val resolvedGoal: Flow<ResolvedBodyGoal?> = combine(activePlan, repository.goals) { plan, goals ->
        resolveBodyGoal(plan, goals)
    }

    /** Resumen del peso, series del rango y ritmo de grasa: se calculan una vez y la pantalla solo los pinta. */
    private val bodyPart: Flow<BodyDerived> = combine(repository.observations, resolvedGoal, _range) { observations, goal, range ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val latest = latestValidByMetric(observations)
        val composition = latestCompatibleComposition(observations)
        val bodyFatNow = composition?.bodyFatPercent ?: latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi
        val muscleNow = composition?.muscleMassPercent ?: latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
        val goalCurrent = when (goal?.metric) {
            GoalMetric.BODY_FAT -> bodyFatNow
            GoalMetric.MUSCLE_MASS -> muscleNow
            else -> null
        }
        val weightSeries = bodyDailySeries(
            observationsInLastDays(observations, BodyMetric.WEIGHT, range.days, zone, today),
            zone,
        )
        val bodyFatSeries = bodyDailySeries(
            observationsInLastDays(observations, BodyMetric.BODY_FAT_PERCENT, range.days, zone, today),
            zone,
        )
        BodyDerived(
            goal = goal,
            weightSummary = summarizeWeight(observations, range, goal, zone, today),
            weightSeries = weightSeries,
            weightTrend = ewmaTrend(weightSeries),
            bodyFatSeries = bodyFatSeries,
            bodyFatTrend = ewmaTrend(bodyFatSeries),
            bodyFatWeeklyRate = measuredWeeklyRate(observations, BodyMetric.BODY_FAT_PERCENT, zone, today),
            goalTrack = goal?.takeIf { it.metric != GoalMetric.WEIGHT }
                ?.let { bodyGoalTrack(observations, it, goalCurrent, zone, today) },
            weightSummary30Days = summarizeWeight(observations, BodyChartRange.ONE_MONTH, goal, zone, today),
        )
    }

    val uiState: StateFlow<BodyProgressUiState> = combine(
        calculatedState,
        repository.lastError,
        bodyPart,
        repository.loaded,
    ) { state, error, derived, loaded ->
        state.copy(
            error = error,
            goal = derived.goal,
            weightSummary = derived.weightSummary,
            weightSeries = derived.weightSeries,
            weightTrend = derived.weightTrend,
            bodyFatSeries = derived.bodyFatSeries,
            bodyFatTrend = derived.bodyFatTrend,
            bodyFatWeeklyRate = derived.bodyFatWeeklyRate,
            goalTrack = derived.goalTrack,
            loaded = loaded,
            weightSummary30Days = derived.weightSummary30Days,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyProgressUiState())

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
        // Día local: hoy, la hora actual; otro día, las 12:00 de ese día en la zona del teléfono.
        val zone = ZoneId.systemDefault()
        val timestamp = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            ?.let { localMeasurementEpochMs(it, LocalDate.now(zone), System.currentTimeMillis(), zone) }
            ?: System.currentTimeMillis()
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

    /** Historial de mediciones (una fila por medición, con su hora), del más reciente al más antiguo. */
    val history: StateFlow<List<BodyHistoryRow>> = repository.observations
        .map { buildBodyHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Guarda una medición manual con su hora exacta. Antes borra [replacing] (la medición que se edita), así que la sesión y la hora
     * quedan como las da la pantalla. [values] solo lleva los datos que tienen cifra.
     */
    fun saveBodyRecord(
        sessionId: String,
        timestampEpochMs: Long,
        values: Map<BodyMetric, Double>,
        replacing: List<BodyObservation> = emptyList(),
    ): Int {
        removeObservations(replacing)
        return values.count { (metric, value) ->
            addObservation(metric, value, bodyMetricUnit(metric), sessionId = sessionId, timestampEpochMs = timestampEpochMs)
        }
    }

    /** Borra las observaciones indicadas: una medición entera, sin tocar las demás. */
    fun removeObservations(observations: List<BodyObservation>) {
        observations.forEach { repository.deleteObservation(it.id) }
    }

    /** Deshacer: vuelve a guardar observaciones borradas con su mismo id, su hora y su origen. */
    fun restoreObservations(observations: List<BodyObservation>): Int = repository.addObservations(observations)

    private fun bodyMetricUnit(metric: BodyMetric): String = when (metric) {
        BodyMetric.WEIGHT -> "kg"
        BodyMetric.BODY_FAT_PERCENT, BodyMetric.MUSCLE_MASS_PERCENT -> "%"
        else -> "cm"
    }
}

/** Cifras derivadas de las medidas y de la meta; [BodyProgressViewModel.uiState] las une al estado. */
private data class BodyDerived(
    val goal: ResolvedBodyGoal?,
    val weightSummary: BodyWeightSummary,
    val weightSeries: List<BodyMetricPoint>,
    val weightTrend: List<BodyMetricPoint>,
    val bodyFatSeries: List<BodyMetricPoint>,
    val bodyFatTrend: List<BodyMetricPoint>,
    val bodyFatWeeklyRate: Double?,
    val goalTrack: BodyGoalTrack?,
    val weightSummary30Days: BodyWeightSummary,
)
