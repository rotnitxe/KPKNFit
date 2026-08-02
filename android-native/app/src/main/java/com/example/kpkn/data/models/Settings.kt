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
    val availablePlates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    val restTimerDefaultSeconds: Int = 90,
    val restTimerAutoStart: Boolean = false,
    /** How chatty continuous-voice TTS is during a live session. */
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.COMPLETE,
    /** Mic VAD profile: GYM = more tolerant to noise; QUIET = snappier cutoffs. */
    val voiceNoiseProfile: VoiceNoiseProfile = VoiceNoiseProfile.GYM,
    /** TTS speech rate multiplier (0.9–1.1 typical). */
    val ttsSpeechRate: Float = 1.0f,
    /** Continuous always-on mic vs hold-to-talk. */
    val voiceInputMode: VoiceInputMode = VoiceInputMode.CONTINUOUS,
    /** Tutorial version for the current hybrid voice explanation. */
    val voiceTutorialVersionSeen: Int = 0,

    /** User nicknames → exerciseId for voice matching. */
    val voiceExerciseAliases: Map<String, String> = emptyMap(),
    val showPRsInWorkout: Boolean = true,
    val oneRMFormula: OneRMFormula = OneRMFormula.BRZYCKI,
    val workoutLoggerMode: WorkoutLoggerMode = WorkoutLoggerMode.PRO,
    val sessionCompactView: Boolean = false,

    /** Legacy enum retained only so old Settings JSON can be decoded during migration. */
    val apiProvider: ApiProvider = ApiProvider.DEEPSEEK,
    val apiKeys: ApiKeys = ApiKeys(),
    val aiTemperature: Double = 0.7,
    val useApiForDescriptions: Boolean = false,

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
    val dailyFiberGoal: Int? = 25,
    val dailySugarLimit: Int? = 50,
    val dailySodiumLimitMg: Int? = 2300,
    val dailyPotassiumGoalMg: Int? = 3500,
    val dailyHydrationGoalMl: Int? = 2000,
    val nutritionShowOverages: Boolean = true,
    val calorieGoalObjective: CalorieGoalObjective = CalorieGoalObjective.MAINTENANCE,
    /** PAL categórico 1–5 (Harris–Benedict style) usado en el plan nutricional. */
    val nutritionActivityLevel: Int = 3,
    /** omnivore | vegetarian | vegan — ajusta proteína recomendada. */
    val nutritionDietaryPreference: String = "omnivore",

    val sleepTargetHours: Double = 8.0,
    val smartSleepEnabled: Boolean = false,

    val algorithmSettings: AlgorithmSettings = AlgorithmSettings(),
    val augePredictionBias: PredictionBiasProfile = PredictionBiasProfile(),

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

    /** Código BCP-47 del idioma seleccionado, o "system" para respetar el locale del SO. */
    val appLanguage: String = "system",
    val programQueueIds: List<String> = emptyList(),
    val archivedProgramIds: List<String> = emptyList(),
    /** IDs de programas con JSON corrupto aislados; el JSON vive en backups, no en Room. */
    val quarantinedProgramIds: List<String> = emptyList(),
    /** Respaldo del JSON crudo de filas corruptas (id → data). */
    val quarantinedProgramBackups: Map<String, String> = emptyMap(),
)

enum class CalorieGoalObjective { DEFICIT, MAINTENANCE, SURPLUS }
enum class WeightUnit { KG, LBS }
enum class IntensityMetric { RPE, RIR }
enum class OneRMFormula { BRZYCKI, EPLEY, LANDER }
enum class WorkoutLoggerMode { PRO, SIMPLE }
enum class VoiceVerbosity { COMPLETE, ESSENTIAL, SILENT }
enum class VoiceNoiseProfile { GYM, QUIET }
enum class VoiceInputMode { CONTINUOUS, PUSH_TO_TALK }
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
    /** Perfil hormonal para Mifflin/Harris; independiente de Gender. */
    val metabolicProfile: MetabolicProfile? = null,
)

enum class Gender { MALE, FEMALE, OTHER }

/**
 * MetabolicProfile — perfil hormonal interno usado exclusivamente para el cálculo de TMB.
 * Desacoplado de la identidad de género (Gender). El usuario lo elige respondiendo
 * "¿Qué hormonas predominan más en tu cuerpo hoy?" sin jerga médica.
 *
 * TESTOSTERONE → constante masculina en Mifflin/Harris (+5)
 * ESTROGEN     → constante femenina en Mifflin/Harris (-161)
 * MIXED        → promedio de ambas (-78), para perfiles en transición o no binarios
 */
enum class MetabolicProfile { TESTOSTERONE, ESTROGEN, MIXED }

@Serializable
data class PredictionBiasProfile(
    val cnsBias: Double = 0.0,
    val muscularBias: Double = 0.0,
    val spinalBias: Double = 0.0,
    val sampleCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
)

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
