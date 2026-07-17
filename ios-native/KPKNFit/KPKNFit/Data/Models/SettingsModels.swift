import Foundation

// ─── Settings ────────────────────────────────────────────────────────────────

internal struct AppSettings: Codable {
    var hasSeenWelcome: Bool = false
    var hasSeenHomeTour: Bool = false
    var username: String = "Usuario"
    var profilePicture: String? = nil
    var age: Int? = nil
    var athleteType: AthleteType = .ENTHUSIAST
    var weightUnit: WeightUnit = .KG
    var intensityMetric: IntensityMetric = .RIR
    var barbellWeight: Double = 20.0
    var availablePlates: [Double] = [25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25]
    var restTimerDefaultSeconds: Int = 90
    var restTimerAutoStart: Bool = false
    var showPRsInWorkout: Bool = true
    var oneRMFormula: OneRMFormula = .BRZYCKI
    var workoutLoggerMode: WorkoutLoggerMode = .PRO
    var sessionCompactView: Bool = false
    var apiProvider: ApiProvider = .GEMINI
    var apiKeys: ApiKeys = ApiKeys()
    var aiTemperature: Double = 0.7
    var useApiForDescriptions: Bool = false
    var appTheme: AppTheme = .DEFAULT
    var themePrimaryColor: String = "#6750A4"
    var enableAnimations: Bool = true
    var hapticFeedbackEnabled: Bool = true
    var soundsEnabled: Bool = true
    var userVitals: UserVitals = UserVitals()
    var dailyCalorieGoal: Int? = nil
    var dailyProteinGoal: Int? = nil
    var dailyCarbGoal: Int? = nil
    var dailyFatGoal: Int? = nil
    var dailyFiberGoal: Int? = 25
    var dailySugarLimit: Int? = 50
    var dailySodiumLimitMg: Int? = 2300
    var dailyPotassiumGoalMg: Int? = 3500
    var dailyHydrationGoalMl: Int? = 2000
    var nutritionShowOverages: Bool = true
    var calorieGoalObjective: CalorieGoalObjective = .MAINTENANCE
    var sleepTargetHours: Double = 8.0
    var smartSleepEnabled: Bool = false
    var algorithmSettings: AlgorithmSettings = AlgorithmSettings()
    var augePredictionBias: PredictionBiasProfile = PredictionBiasProfile()
    var reducedMotionMode: Bool = false
    var hapticIntensity: HapticIntensity = .MEDIUM
    var sessionAutoAdvanceFields: Bool = true
    var showTimeSaverPrompt: Bool = true
    var defaultVolumeSystem: VolumeSystem = .KPNK
    var gymName: String? = nil
    var workoutFeatureFlags: WorkoutFeatureFlags = WorkoutFeatureFlags()
    var workoutV2HeaderWidgetsBySession: [String: WorkoutHeaderWidgets] = [:]
    var aiFallbackEnabled: Bool = true
    var aiMaxTokens: Int = 512
    var workoutReminderEnabled: Bool = false
    var workoutReminderTime: String = "18:00"
    var mealReminderEnabled: Bool = false
    var mealReminderBreakfast: String = "08:00"
    var mealReminderLunch: String = "13:00"
    var mealReminderDinner: String = "20:00"
    var sleepReminderEnabled: Bool = false
    var sleepReminderTime: String = "22:00"
    var appLanguage: String = "system"

    func copy(userVitals: UserVitals? = nil) -> AppSettings {
        var s = self
        if let v = userVitals { s.userVitals = v }
        return s
    }
}

// ─── Supporting Settings Types ───────────────────────────────────────────────

internal enum CalorieGoalObjective: String, Codable { case DEFICIT, MAINTENANCE, SURPLUS }
internal enum WeightUnit: String, Codable { case KG, LBS }
internal enum IntensityMetric: String, Codable { case RPE, RIR }
internal enum OneRMFormula: String, Codable { case BRZYCKI, EPLEY, LANDER }
internal enum WorkoutLoggerMode: String, Codable { case PRO, SIMPLE }
internal enum ApiProvider: String, Codable { case GEMINI, GPT, DEEPSEEK }
internal enum AppTheme: String, Codable { case DEFAULT, DARK, DEEP_BLACK, VOLT, LIGHT }
internal enum HapticIntensity: String, Codable { case LIGHT, MEDIUM, STRONG }
internal enum VolumeSystem: String, Codable { case KPNK }
internal enum AthleteType: String, Codable {
    case ENTHUSIAST, POWERLIFTER, BODYBUILDER, POWERBUILDER
    case ZERCHER_LIFTER, HYBRID, WEIGHTLIFTER, CALISTHENICS
}
// Gender is defined in AugeModels.swift
internal enum MetabolicProfile: String, Codable { case TESTOSTERONE, ESTROGEN, MIXED }

internal struct ApiKeys: Codable {
    var gemini: String? = nil
    var gpt: String? = nil
    var deepseek: String? = nil
}

// UserVitals is defined in AugeModels.swift

internal struct PredictionBiasProfile: Codable {
    var cnsBias: Double = 0.0
    var muscularBias: Double = 0.0
    var spinalBias: Double = 0.0
    var sampleCount: Int = 0
    var lastUpdatedMs: Int64 = 0
}

internal struct AlgorithmSettings: Codable {
    var oneRMDecayRate: Double = 0.03
    var failureFatigueFactor: Double = 1.5
    var legVolumeMultiplier: Double = 1.0
    var torsoVolumeMultiplier: Double = 1.0
    var synergistFactor: Double = 0.25
    var augeEnableNutritionTracking: Bool = false
    var augeEnableSleepTracking: Bool = false
    var augeRecoverySensitivity: Double = 1.0
    var augeFatigueSensitivity: Double = 1.0
    var augeReadinessThreshold: Int = 60
    var augeAutoDeload: Bool = false
    var augeShowAlertsInSession: Bool = true
}
