package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    // Onboarding flags
    val hasSeenWelcome: Boolean = false,
    val hasSeenHomeTour: Boolean = false,

    // Perfil
    val username: String = "Usuario",
    val profilePicture: String? = null,
    val age: Int? = null,
    val athleteType: AthleteType = AthleteType.ENTHUSIAST,

    // Entrenamiento
    val weightUnit: WeightUnit = WeightUnit.KG,
    val intensityMetric: IntensityMetric = IntensityMetric.RIR,
    val barbellWeight: Double = 20.0,
    val restTimerDefaultSeconds: Int = 90,
    val restTimerAutoStart: Boolean = false,
    val showPRsInWorkout: Boolean = true,
    val oneRMFormula: OneRMFormula = OneRMFormula.BRZYCKI,
    val workoutLoggerMode: WorkoutLoggerMode = WorkoutLoggerMode.PRO,
    val sessionCompactView: Boolean = false,

    // IA
    val apiProvider: ApiProvider = ApiProvider.GEMINI,
    val apiKeys: ApiKeys = ApiKeys(),
    val aiTemperature: Double = 0.7,

    // UI
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val themePrimaryColor: String = "#6750A4",
    val enableAnimations: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,

    // Biometría / Nutrición
    val userVitals: UserVitals = UserVitals(),
    val dailyCalorieGoal: Int? = null,
    val dailyProteinGoal: Int? = null,
    val dailyCarbGoal: Int? = null,
    val dailyFatGoal: Int? = null,

    // Sueño
    val sleepTargetHours: Double = 8.0,
    val smartSleepEnabled: Boolean = false,

    // Algoritmo
    val algorithmSettings: AlgorithmSettings = AlgorithmSettings(),
)

enum class WeightUnit { KG, LBS }
enum class IntensityMetric { RPE, RIR }
enum class OneRMFormula { BRZYCKI, EPLEY, LANDER }
enum class WorkoutLoggerMode { PRO, SIMPLE }
enum class ApiProvider { GEMINI, GPT, DEEPSEEK }
enum class AppTheme { DEFAULT, DARK, DEEP_BLACK, VOLT, LIGHT }
enum class AthleteType {
    ENTHUSIAST, POWERLIFTER, BODYBUILDER, POWERBUILDER,
    ZERCHER_LIFTER, HYBRID, WEIGHTLIFTER, CALISTHENICS
}

@Serializable
data class ApiKeys(
    val gemini: String? = null,
    val gpt: String? = null,
    val deepseek: String? = null,
)

@Serializable
data class UserVitals(
    val age: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val gender: Gender? = null,
    val bodyFatPercentage: Double? = null,
    val muscleMassPercentage: Double? = null,
    val targetWeight: Double? = null,
)

enum class Gender { MALE, FEMALE, OTHER }

@Serializable
data class AlgorithmSettings(
    val oneRMDecayRate: Double = 0.03,
    val failureFatigueFactor: Double = 1.5,
    val legVolumeMultiplier: Double = 1.0,
    val torsoVolumeMultiplier: Double = 1.0,
    val synergistFactor: Double = 0.25,
    val augeEnableNutritionTracking: Boolean = false,
    val augeEnableSleepTracking: Boolean = false,
)
