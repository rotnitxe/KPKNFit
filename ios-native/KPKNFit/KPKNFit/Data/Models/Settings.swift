import Foundation

// ─── Settings ────────────────────────────────────────────────────────────────

public typealias Settings = AppSettings

public struct AppSettings: Codable {
    public var hasSeenWelcome: Bool = false
    public var hasSeenHomeTour: Bool = false
    public var username: String = "Usuario"
    public var profilePicture: String? = nil
    public var age: Int? = nil
    public var athleteType: AthleteType = .ENTHUSIAST
    public var weightUnit: WeightUnit = .KG
    public var intensityMetric: IntensityMetric = .RIR
    public var barbellWeight: Double = 20.0
    public var availablePlates: [Double] = [25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25]
    public var restTimerDefaultSeconds: Int = 90
    public var restTimerAutoStart: Bool = false
    public var showPRsInWorkout: Bool = true
    public var oneRMFormula: OneRMFormula = .BRZYCKI
    public var workoutLoggerMode: WorkoutLoggerMode = .PRO
    public var sessionCompactView: Bool = false
    public var apiProvider: ApiProvider = .GEMINI
    public var apiKeys: ApiKeys = ApiKeys()
    public var aiTemperature: Double = 0.7
    public var useApiForDescriptions: Bool = false
    public var appTheme: AppTheme = .DEFAULT
    public var themePrimaryColor: String = "#6750A4"
    public var enableAnimations: Bool = true
    public var hapticFeedbackEnabled: Bool = true
    public var soundsEnabled: Bool = true
    public var userVitals: UserVitals = UserVitals()
    public var dailyCalorieGoal: Int? = nil
    public var dailyProteinGoal: Int? = nil
    public var dailyCarbGoal: Int? = nil
    public var dailyFatGoal: Int? = nil
    public var dailyFiberGoal: Int? = 25
    public var dailySugarLimit: Int? = 50
    public var dailySodiumLimitMg: Int? = 2300
    public var dailyPotassiumGoalMg: Int? = 3500
    public var dailyHydrationGoalMl: Int? = 2000
    public var nutritionShowOverages: Bool = true
    public var calorieGoalObjective: CalorieGoalObjective = .MAINTENANCE
    public var sleepTargetHours: Double = 8.0
    public var smartSleepEnabled: Bool = false
    public var algorithmSettings: AlgorithmSettings = AlgorithmSettings()
    public var augePredictionBias: PredictionBiasProfile = PredictionBiasProfile()
    public var reducedMotionMode: Bool = false
    public var hapticIntensity: HapticIntensity = .MEDIUM
    public var sessionAutoAdvanceFields: Bool = true
    public var showTimeSaverPrompt: Bool = true
    public var defaultVolumeSystem: VolumeSystem = .KPNK
    public var gymName: String? = nil
    public var workoutFeatureFlags: WorkoutFeatureFlags = WorkoutFeatureFlags()
    public var workoutV2HeaderWidgetsBySession: [String: WorkoutHeaderWidgets] = [:]
    public var aiFallbackEnabled: Bool = true
    public var aiMaxTokens: Int = 512
    public var workoutReminderEnabled: Bool = false
    public var workoutReminderTime: String = "18:00"
    public var mealReminderEnabled: Bool = false
    public var mealReminderBreakfast: String = "08:00"
    public var mealReminderLunch: String = "13:00"
    public var mealReminderDinner: String = "20:00"
    public var sleepReminderEnabled: Bool = false
    public var sleepReminderTime: String = "22:00"
    public var appLanguage: String = "system"
}

// ─── Enums ────────────────────────────────────────────────────────────────────

public enum CalorieGoalObjective: String, Codable {
    case DEFICIT, MAINTENANCE, SURPLUS
}

public enum WeightUnit: String, Codable {
    case KG, LBS
}

public enum IntensityMetric: String, Codable {
    case RPE, RIR
}

public enum OneRMFormula: String, Codable {
    case BRZYCKI, EPLEY, LANDER
}

public enum WorkoutLoggerMode: String, Codable {
    case PRO, SIMPLE
}

public enum ApiProvider: String, Codable {
    case GEMINI, GPT, DEEPSEEK
}

public enum AppTheme: String, Codable {
    case DEFAULT, DARK, DEEP_BLACK, VOLT, LIGHT
}

public enum HapticIntensity: String, Codable {
    case LIGHT, MEDIUM, STRONG
}

public enum AthleteType: String, Codable {
    case ENTHUSIAST, POWERLIFTER, BODYBUILDER, POWERBUILDER
    case ZERCHER_LIFTER, HYBRID, WEIGHTLIFTER, CALISTHENICS
}

// Redundant VolumeSystem removed (defined in Program.swift)


// Redundant Gender removed (defined in AugeModels.swift)


public enum MetabolicProfile: String, Codable {
    case TESTOSTERONE, ESTROGEN, MIXED
}

// ─── Supporting Structs ───────────────────────────────────────────────────────

public struct ApiKeys: Codable {
    public var gemini: String? = nil
    public var gpt: String? = nil
    public var deepseek: String? = nil
}

public struct PredictionBiasProfile: Codable {
    public var cnsBias: Double = 0.0
    public var muscularBias: Double = 0.0
    public var spinalBias: Double = 0.0
    public var sampleCount: Int = 0
    public var lastUpdatedMs: Int64 = 0
}

public struct AlgorithmSettings: Codable {
    public var oneRMDecayRate: Double = 0.03
    public var failureFatigueFactor: Double = 1.5
    public var legVolumeMultiplier: Double = 1.0
    public var torsoVolumeMultiplier: Double = 1.0
    public var synergistFactor: Double = 0.25
    public var augeEnableNutritionTracking: Bool = false
    public var augeEnableSleepTracking: Bool = false
    public var augeRecoverySensitivity: Double = 1.0
    public var augeFatigueSensitivity: Double = 1.0
    public var augeReadinessThreshold: Int = 60
    public var augeAutoDeload: Bool = false
    public var augeShowAlertsInSession: Bool = true
}
