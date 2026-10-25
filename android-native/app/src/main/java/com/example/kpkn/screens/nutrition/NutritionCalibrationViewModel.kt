package com.example.kpkn.screens.nutrition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardEngine
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardState
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
    private val profileRepository = NutritionCalibrationRepository.getInstance(application)
    private val _uiState = MutableStateFlow(NutritionCalibrationUiState(isLoading = true))
    val uiState: StateFlow<NutritionCalibrationUiState> = _uiState.asStateFlow()

    val checkin = com.example.kpkn.screens.nutrition.checkin.NutritionCheckinController(
        viewModelScope, com.example.kpkn.screens.nutrition.checkin.NutritionCheckinRuntime.dependencies(application),
    )
    val checkinState = checkin.state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { profileRepository.get() } }
                .onSuccess { profile ->
                    _uiState.value = NutritionCalibrationUiState(
                        profile = profile,
                        wizard = NutritionCalibrationWizardEngine.start(profile),
                    )
                }
                .onFailure { error -> _uiState.value = NutritionCalibrationUiState(error = error.message) }
        }
    }

    fun evaluate() { refresh(); checkin.refresh() }

    fun reset() {
        viewModelScope.launch {
            profileRepository.clear()
            _uiState.value = NutritionCalibrationUiState(profile = null)
        }
    }

    fun answerWizard(value: String) {
        viewModelScope.launch {
            val next = NutritionCalibrationWizardEngine.answer(_uiState.value.wizard, value)
            profileRepository.save(next.profile)
            _uiState.value = _uiState.value.copy(profile = next.profile, wizard = next, error = null)
        }
    }

    fun skipWizard() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val updated = NutritionCalibrationWizardEngine.recordConfirmedPortion(
                _uiState.value.profile ?: NutritionCalibrationProfile(), key, grams,
            )
            profileRepository.save(updated)
            _uiState.value = _uiState.value.copy(profile = updated, wizard = NutritionCalibrationWizardEngine.start(updated))
        }
    }
}
