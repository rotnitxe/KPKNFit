package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val hasSeenWelcome: Boolean = false,
    val hasSeenHomeTour: Boolean = false,

    val username: String = "Usuario",
    val profilePicture: String? = null,
    val age: Int? = null,
    val athleteType: AthleteType = AthleteType.ENTHUSIAST,

    val weightUnit: WeightUnit = WeightUnit.KG,
    val intensityMetric: IntensityMetric = IntensityMetric.RIR,
    val barbellWeight: Double = 20.0,
    val restTimerDefaultSeconds: Int = 90,
    val restTimerAutoStart: Boolean = false,
    val showPRsInWorkout: Boolean = true,
    val oneRMFormula: OneRMFormula = OneRMFormula.BRZYCKI,
    val workoutLoggerMode: WorkoutLoggerMode = WorkoutLoggerMode.PRO,
    val sessionCompactView: Boolean = false,

    val apiProvider: ApiProvider = ApiProvider.GEMINI,
    val apiKeys: ApiKeys = ApiKeys(),
    val aiTemperature: Double = 0.7,

    val appTheme: AppTheme = AppTheme.DEFAULT,
    val themePrimaryColor: String = "#6750A4",
    val enableAnimations: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,

    val userVitals: UserVitals = UserVitals(),
    val dailyCalorieGoal: Int? = null,
    val dailyProteinGoal: Int? = null,
    val dailyCarbGoal: Int? = null,
    val dailyFatGoal: Int? = null,
    val calorieGoalObjective: CalorieGoalObjective = CalorieGoalObjective.MAINTENANCE,

    val sleepTargetHours: Double = 8.0,
    val smartSleepEnabled: Boolean = false,

    val algorithmSettings: AlgorithmSettings = AlgorithmSettings(),

    val localAiFoodEnabled: Boolean = true,
    val reducedMotionMode: Boolean = false,
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,

    val sessionAutoAdvanceFields: Boolean = true,
    val showTimeSaverPrompt: Boolean = true,
    val defaultVolumeSystem: VolumeSystem = VolumeSystem.KPNK,
    val gymName: String? = null,

    val workoutFeatureFlags: WorkoutFeatureFlags = WorkoutFeatureFlags(),
    val workoutV2HeaderWidgetsBySession: Map<String, WorkoutHeaderWidgets> = emptyMap(),

    val aiFallbackEnabled: Boolean = true,
    val aiMaxTokens: Int = 512,

    val workoutReminderEnabled: Boolean = false,
    val workoutReminderTime: String = "18:00",
    val mealReminderEnabled: Boolean = false,
    val mealReminderBreakfast: String = "08:00",
    val mealReminderLunch: String = "13:00",
    val mealReminderDinner: String = "20:00",
    val sleepReminderEnabled: Boolean = false,
    val sleepReminderTime: String = "22:00",
)

enum class CalorieGoalObjective { DEFICIT, MAINTENANCE, SURPLUS }
enum class WeightUnit { KG, LBS }
enum class IntensityMetric { RPE, RIR }
enum class OneRMFormula { BRZYCKI, EPLEY, LANDER }
enum class WorkoutLoggerMode { PRO, SIMPLE }
enum class ApiProvider { GEMINI, GPT, DEEPSEEK }
enum class AppTheme { DEFAULT, DARK, DEEP_BLACK, VOLT, LIGHT }
enum class HapticIntensity { LIGHT, MEDIUM, STRONG }
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
    val augeRecoverySensitivity: Double = 1.0,
    val augeFatigueSensitivity: Double = 1.0,
    val augeReadinessThreshold: Int = 60,
    val augeAutoDeload: Boolean = false,
    val augeShowAlertsInSession: Boolean = true,
)
