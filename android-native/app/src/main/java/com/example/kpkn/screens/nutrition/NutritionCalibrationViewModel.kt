package com.example.kpkn.screens.nutrition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.data.models.NutritionCalibrationRevision
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.CalibrationWeightPoint
import com.example.kpkn.domain.nutrition.NutritionCalibrationEngine
import com.example.kpkn.domain.nutrition.NutritionCalibrationInput
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardEngine
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardState
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NutritionCalibrationUiState(
    val profile: com.example.kpkn.data.models.NutritionCalibrationProfile? = null,
    val wizard: NutritionCalibrationWizardState = NutritionCalibrationWizardEngine.start(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class NutritionCalibrationViewModel(application: Application) : AndroidViewModel(application) {
    private val nutritionRepository = NutritionRepository.getInstance()
    private val programRepository = ProgramRepository.getInstance()
    private val bodyRepository = BodyProgressRepository.getInstance(application)
    private val profileRepository = NutritionCalibrationRepository.getInstance(application)
    private val _uiState = MutableStateFlow(NutritionCalibrationUiState(isLoading = true))
    val uiState: StateFlow<NutritionCalibrationUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { profileRepository.get() }
                .onSuccess { profile ->
                    _uiState.value = NutritionCalibrationUiState(
                        profile = profile,
                        wizard = NutritionCalibrationWizardEngine.start(profile),
                    )
                }
                .onFailure { error -> _uiState.value = NutritionCalibrationUiState(error = error.message) }
        }
    }

    fun evaluate() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val previousProfile = runCatching { profileRepository.get() }.getOrNull()
            runCatching {
                bodyRepository.awaitReady()
                val weights = bodyRepository.observations.value
                    .filter { it.metric == BodyMetric.WEIGHT }
                    .mapNotNull { observation ->
                        runCatching {
                            CalibrationWeightPoint(
                                date = Instant.ofEpochMilli(observation.timestampEpochMs)
                                    .atZone(runCatching { ZoneId.of(observation.zoneId) }.getOrDefault(ZoneId.of("UTC")))
                                    .toLocalDate(),
                                weightKg = observation.valueSi,
                            )
                        }.getOrNull()
                    }
                val completeDays = nutritionRepository.nutritionLogs.value
                    .asSequence()
                    .filter { it.status != com.example.kpkn.data.models.NutritionStatus.PLANNED }
                    .mapNotNull { log ->
                        val date = runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()
                            ?: return@mapNotNull null
                        val intake = log.foods.sumOf { it.calories }
                        date to intake
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, values) -> values.sum() }
                    .filterValues { it > 0.0 && it.isFinite() }
                val activePlan = nutritionRepository.activeNutritionPlan
                val settings = programRepository.settings.value
                val baselineKcal = activePlan?.calorieTarget?.takeIf { it > 0 }
                    ?: settings.dailyCalorieGoal
                val currentKcal = completeDays.values
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.roundToInt()
                val activePlanChangedAt = activePlan
                    ?.createdAt
                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                NutritionCalibrationEngine.evaluate(
                    NutritionCalibrationInput(
                        baselineKcal = baselineKcal,
                        currentKcal = currentKcal,
                        weightPoints = weights,
                        completeIntakeDays = completeDays.keys,
                        nowEpochMs = System.currentTimeMillis(),
                        planChangedAtEpochMs = activePlanChangedAt,
                    ),
                )
            }.onSuccess { result ->
                val revision = NutritionCalibrationRevision(
                    recordedAtEpochMs = result.profile.updatedAtEpochMs,
                    beforeKcal = previousProfile?.currentKcal ?: result.profile.baselineKcal,
                    afterKcal = result.profile.currentKcal,
                    proposedAdjustmentKcal = result.profile.recommendedAdjustmentKcal,
                    weeklyWeightChangeKg = result.weeklyWeightChangeKg,
                    status = result.profile.status,
                )
                val profileWithRevision = result.profile.copy(
                    revisions = (previousProfile?.revisions.orEmpty() + revision).takeLast(52),
                )
                val currentWizard = NutritionCalibrationWizardEngine.start(_uiState.value.wizard.profile)
                val profile = profileWithRevision.copy(
                    wizardVersion = currentWizard.profile.wizardVersion,
                    wizardStep = currentWizard.profile.wizardStep,
                    wizardSkipped = currentWizard.profile.wizardSkipped,
                    wizardCompleted = currentWizard.profile.wizardCompleted,
                    weighingConvention = currentWizard.profile.weighingConvention,
                    utensilVolumesMl = currentWizard.profile.utensilVolumesMl,
                    habitualPortionsGrams = currentWizard.profile.habitualPortionsGrams,
                    maturePortionsGrams = currentWizard.profile.maturePortionsGrams,
                    confirmedPortions = currentWizard.profile.confirmedPortions,
                    identityMappings = currentWizard.profile.identityMappings,
                    statePreferences = currentWizard.profile.statePreferences,
                    preparationProfiles = currentWizard.profile.preparationProfiles,
                    oilProfiles = currentWizard.profile.oilProfiles,
                    lastWizardUpdatedAtEpochMs = currentWizard.profile.lastWizardUpdatedAtEpochMs,
                )
                profileRepository.save(profile)
                _uiState.value = NutritionCalibrationUiState(
                    profile = profile,
                    wizard = NutritionCalibrationWizardEngine.start(profile),
                )
            }.onFailure { error ->
                _uiState.value = NutritionCalibrationUiState(error = error.message)
            }
        }
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            profileRepository.clear()
            _uiState.value = NutritionCalibrationUiState(profile = null)
        }
    }

    fun answerWizard(value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = NutritionCalibrationWizardEngine.answer(_uiState.value.wizard, value)
            profileRepository.save(next.profile)
            _uiState.value = _uiState.value.copy(profile = next.profile, wizard = next, error = null)
        }
    }

    fun skipWizard() {
        viewModelScope.launch(Dispatchers.IO) {
            val next = NutritionCalibrationWizardEngine.skip(_uiState.value.wizard)
            profileRepository.save(next.profile)
            _uiState.value = _uiState.value.copy(profile = next.profile, wizard = next, error = null)
        }
    }

    fun resumeWizard() {
        val next = NutritionCalibrationWizardEngine.resume(_uiState.value.profile ?: NutritionCalibrationProfile())
        _uiState.value = _uiState.value.copy(wizard = next)
    }

    fun recordConfirmedPortion(key: String, grams: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = NutritionCalibrationWizardEngine.recordConfirmedPortion(
                _uiState.value.profile ?: NutritionCalibrationProfile(), key, grams,
            )
            profileRepository.save(updated)
            _uiState.value = _uiState.value.copy(profile = updated, wizard = NutritionCalibrationWizardEngine.start(updated))
        }
    }
}
