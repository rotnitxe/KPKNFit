import Foundation

// MARK: - Enums

enum FormulaType { case MIFFLIN, HARRIS, KATCH }
enum CalorieGoal { case LOSE, MAINTAIN, GAIN }
enum TrendStatus { case ON_TRACK, BEHIND, AHEAD, UNKNOWN }
enum NutritionRiskSeverity { case INFO, WARNING, DANGER }

// MARK: - Data Structures

struct NutritionInput {
    let weightKg: Double
    let heightCm: Double
    let age: Int
    let gender: Gender
    let metabolicProfile: MetabolicProfile?
    let bodyFatPercentage: Double?

    init(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        gender: Gender,
        metabolicProfile: MetabolicProfile? = nil,
        bodyFatPercentage: Double? = nil
    ) {
        self.weightKg = weightKg
        self.heightCm = heightCm
        self.age = age
        self.gender = gender
        self.metabolicProfile = metabolicProfile
        self.bodyFatPercentage = bodyFatPercentage
    }
}

struct CalorieGoalConfig {
    let formula: FormulaType
    let activityLevel: Int
    let goal: CalorieGoal
    let weeklyChangeKg: Double?
    let healthMultiplier: Double
    let customActivityFactor: Double?
    let activityDaysPerWeek: Int?
    let activityHoursPerDay: Double?
    let dailyCalorieGoal: Int?

    init(
        formula: FormulaType = .MIFFLIN,
        activityLevel: Int = 3,
        goal: CalorieGoal = .MAINTAIN,
        weeklyChangeKg: Double? = nil,
        healthMultiplier: Double = 1.0,
        customActivityFactor: Double? = nil,
        activityDaysPerWeek: Int? = nil,
        activityHoursPerDay: Double? = nil,
        dailyCalorieGoal: Int? = nil
    ) {
        self.formula = formula
        self.activityLevel = activityLevel
        self.goal = goal
        self.weeklyChangeKg = weeklyChangeKg
        self.healthMultiplier = healthMultiplier
        self.customActivityFactor = customActivityFactor
        self.activityDaysPerWeek = activityDaysPerWeek
        self.activityHoursPerDay = activityHoursPerDay
        self.dailyCalorieGoal = dailyCalorieGoal
    }
}

struct NutritionProjection {
    let etaDate: String?
    let trendStatus: TrendStatus
    let weeklyDelta: Double?
    let confidence: Double
}

struct NutritionRiskFlag {
    let id: String
    let code: String
    let severity: NutritionRiskSeverity
    let message: String
    let hardStop: Bool

    init(id: String, code: String, severity: NutritionRiskSeverity, message: String, hardStop: Bool = false) {
        self.id = id
        self.code = code
        self.severity = severity
        self.message = message
        self.hardStop = hardStop
    }
}

struct CalculationSnapshot {
    let formula: FormulaType
    let activityFactor: Double
    let bmr: Double?
    let tdee: Int?
    let calorieTarget: Int
    let generatedAt: String
}

struct RiskInput {
    let settings: NutritionInput
    let calorieTarget: Int
    let goalMetric: GoalMetric
    let goalValue: Double
    let weeklyChangeKg: Double
    let calorieGoal: CalorieGoal

    init(
        settings: NutritionInput,
        calorieTarget: Int,
        goalMetric: GoalMetric,
        goalValue: Double,
        weeklyChangeKg: Double,
        calorieGoal: CalorieGoal = .MAINTAIN
    ) {
        self.settings = settings
        self.calorieTarget = calorieTarget
        self.goalMetric = goalMetric
        self.goalValue = goalValue
        self.weeklyChangeKg = weeklyChangeKg
        self.calorieGoal = calorieGoal
    }
}

// MARK: - BMR Formulas

func mifflinStJeor(
    weightKg: Double,
    heightCm: Double,
    age: Int,
    gender: Gender,
    metabolicProfile: MetabolicProfile? = nil
) -> Double {
    let s: Double
    switch metabolicProfile {
    case .TESTOSTERONE: s = 5.0
    case .ESTROGEN:     s = -161.0
    case .MIXED:        s = -78.0
    case nil:
        switch gender {
        case .FEMALE: s = -161.0
        case .MALE:   s = 5.0
        case .OTHER:  s = -78.0
        }
    }
    return 10.0 * weightKg + 6.25 * heightCm - 5.0 * Double(age) + s
}

func harrisBenedict(
    weightKg: Double,
    heightCm: Double,
    age: Int,
    gender: Gender,
    metabolicProfile: MetabolicProfile? = nil
) -> Double {
    let male   = 88.362  + 13.397 * weightKg + 4.799 * heightCm - 5.677 * Double(age)
    let female = 447.593 +  9.247 * weightKg + 3.098 * heightCm - 4.330 * Double(age)

    switch metabolicProfile {
    case .TESTOSTERONE: return male
    case .ESTROGEN:     return female
    case .MIXED:        return (male + female) / 2.0
    case nil:
        switch gender {
        case .MALE:   return male
        case .FEMALE: return female
        case .OTHER:  return (male + female) / 2.0
        }
    }
}

func katchMcArdle(weightKg: Double, bodyFatPercent: Double) -> Double {
    let lbm = weightKg * (1.0 - bodyFatPercent / 100.0)
    return 370.0 + 21.6 * lbm
}

// MARK: - Activity Factor

private let ACTIVITY_FACTORS: [Int: Double] = [
    1: 1.2,
    2: 1.375,
    3: 1.55,
    4: 1.725,
    5: 1.9,
]

func getActivityFactor(config: CalorieGoalConfig) -> Double {
    if let caf = config.customActivityFactor, caf >= 1.0, caf <= 2.0 {
        return caf
    }
    let days = config.activityDaysPerWeek ?? -1
    let hours = config.activityHoursPerDay ?? -1.0
    if days >= 0 || hours >= 0.0 {
        let d = days >= 0 ? min(days, 7) : 3
        let h = hours >= 0.0 ? min(hours, 24.0) : 1.0
        return 1.2 + (Double(d) / 7.0) * 0.4 + (h / 12.0) * 0.3
    }
    let clampedLevel = min(max(config.activityLevel, 1), 5)
    return ACTIVITY_FACTORS[clampedLevel] ?? 1.55
}

// MARK: - Core BMR Calculator

func calculateBMR(input: NutritionInput, config: CalorieGoalConfig = CalorieGoalConfig()) -> Double? {
    guard input.weightKg > 0, input.heightCm > 0, input.age > 0 else { return nil }

    switch config.formula {
    case .HARRIS:
        return harrisBenedict(
            weightKg: input.weightKg, heightCm: input.heightCm, age: input.age,
            gender: input.gender, metabolicProfile: input.metabolicProfile
        )
    case .KATCH:
        guard let bf = input.bodyFatPercentage else { return nil }
        return katchMcArdle(weightKg: input.weightKg, bodyFatPercent: bf)
    case .MIFFLIN:
        return mifflinStJeor(
            weightKg: input.weightKg, heightCm: input.heightCm, age: input.age,
            gender: input.gender, metabolicProfile: input.metabolicProfile
        )
    }
}

// MARK: - Daily Calorie Goal

func calculateDailyCalorieGoal(input: NutritionInput, config: CalorieGoalConfig) -> Int {
    if let dcg = config.dailyCalorieGoal { return dcg }

    guard let bmr = calculateBMR(input: input, config: config) else { return 0 }
    let factor = getActivityFactor(config: config)
    var tdee = bmr * factor
    let weeklyChange = config.weeklyChangeKg ?? 0.5

    switch config.goal {
    case .LOSE: tdee -= (weeklyChange * 7700) / 7
    case .GAIN: tdee += (weeklyChange * 7700) / 7
    case .MAINTAIN: break
    }

    return Int((tdee * config.healthMultiplier).rounded())
}

// MARK: - TDEE Calculator

func calculateTDEE(input: NutritionInput, config: CalorieGoalConfig = CalorieGoalConfig()) -> Int? {
    guard let bmr = calculateBMR(input: input, config: config) else { return nil }
    let factor = getActivityFactor(config: config)
    return Int((bmr * factor).rounded())
}

// MARK: - Build Snapshot

func buildCalculationSnapshot(input: NutritionInput, config: CalorieGoalConfig) -> CalculationSnapshot {
    let bmr = calculateBMR(input: input, config: config)
    let factor = getActivityFactor(config: config)
    let tdee = bmr.map { Int(($0 * factor).rounded()) }
    let target = calculateDailyCalorieGoal(input: input, config: config)

    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime]

    return CalculationSnapshot(
        formula: config.formula,
        activityFactor: factor,
        bmr: bmr,
        tdee: tdee,
        calorieTarget: target,
        generatedAt: formatter.string(from: Date())
    )
}

// MARK: - Linear Projection

private let DAY_MS: Int64 = 24 * 60 * 60 * 1000

func computeLinearProjection(
    points: [(Int64, Double)],
    goal: Double
) -> (String?, Double?) {
    guard points.count >= 2 else { return (nil, nil) }

    let n = Double(points.count)
    let sumX = points.reduce(0.0) { $0 + Double($1.0) }
    let sumY = points.reduce(0.0) { $0 + $1.1 }
    let sumXY = points.reduce(0.0) { $0 + Double($1.0) * $1.1 }
    let sumX2 = points.reduce(0.0) { $0 + Double($1.0) * Double($1.0) }

    let denom = n * sumX2 - sumX * sumX
    if abs(denom) < 1e-10 { return (nil, nil) }

    let slope = (n * sumXY - sumX * sumY) / denom
    let weeklyDelta = slope * 7
    let current = points.last!
    let diff = goal - current.1
    if abs(slope) < 1e-10 { return (nil, weeklyDelta) }

    let headingToGoal = diff > 0 ? slope > 0 : slope < 0
    guard headingToGoal else { return (nil, weeklyDelta) }

    let daysToGoal = diff / slope
    guard daysToGoal.isFinite else { return (nil, weeklyDelta) }

    let etaMillis = current.0 + Int64(daysToGoal * Double(DAY_MS))
    let epochDay = etaMillis / DAY_MS
    let etaDate: String = {
        let date = Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400)
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }()
    return (etaDate, weeklyDelta)
}

func buildNutritionProjection(
    points: [(Int64, Double)],
    goal: Double,
    estimatedEndDate: String?
) -> NutritionProjection {
    let (etaDate, weeklyDelta) = computeLinearProjection(points: points, goal: goal)
    let effectiveEta = etaDate ?? estimatedEndDate

    var trendStatus = TrendStatus.UNKNOWN
    if etaDate == nil && points.count >= 2 {
        trendStatus = .BEHIND
    } else if let eta = etaDate, let endDate = estimatedEndDate {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone(secondsFromGMT: 0)
        if let etaDateObj = f.date(from: eta), let planDateObj = f.date(from: endDate) {
            let etaDays = Int(etaDateObj.timeIntervalSince1970 / 86_400)
            let planDays = Int(planDateObj.timeIntervalSince1970 / 86_400)
            let diff = etaDays - planDays
            switch diff {
            case _ where diff > 7:  trendStatus = .BEHIND
            case _ where diff < -7: trendStatus = .AHEAD
            default:                trendStatus = .ON_TRACK
            }
        }
    } else if etaDate != nil {
        trendStatus = .ON_TRACK
    }

    let densityConfidence = min(max(Double(points.count) / 10.0, 0.2), 1.0)
    let confidence = min(max(densityConfidence, 0.2), 1.0)

    return NutritionProjection(
        etaDate: effectiveEta,
        trendStatus: trendStatus,
        weeklyDelta: weeklyDelta.map { ($0 * 100).rounded() / 100.0 },
        confidence: (confidence * 100).rounded() / 100.0
    )
}

// MARK: - Risk Flags

func buildNutritionRiskFlags(input: RiskInput) -> [NutritionRiskFlag] {
    var flags: [NutritionRiskFlag] = []
    let isFemale = input.settings.gender == .FEMALE
    let minSoft = isFemale ? 1200 : 1500
    let minHard = isFemale ? 1000 : 1200

    if input.calorieTarget < minHard {
        flags.append(NutritionRiskFlag(
            id: UUID().uuidString,
            code: "calories_extreme_low",
            severity: .DANGER,
            message: "Objetivo calórico extremadamente bajo (< \(minHard) kcal/día). Ajusta el plan.",
            hardStop: true
        ))
    } else if input.calorieTarget < minSoft {
        flags.append(NutritionRiskFlag(
            id: UUID().uuidString,
            code: "calories_low",
            severity: .WARNING,
            message: "Objetivo calórico bajo (\(input.calorieTarget) kcal/día). Revisa adherencia."
        ))
    }

    let resolvedGoal: CalorieGoal
    if input.calorieGoal == .MAINTAIN && input.goalMetric == .WEIGHT {
        let currentWeight = input.settings.weightKg
        if input.goalValue < currentWeight - 0.1 {
            resolvedGoal = .LOSE
        } else if input.goalValue > currentWeight + 0.1 {
            resolvedGoal = .GAIN
        } else {
            resolvedGoal = .MAINTAIN
        }
    } else {
        resolvedGoal = input.calorieGoal
    }

    switch resolvedGoal {
    case .LOSE:
        if input.weeklyChangeKg > 1.5 {
            flags.append(NutritionRiskFlag(
                id: UUID().uuidString,
                code: "pace_extreme",
                severity: .DANGER,
                message: "Ritmo de pérdida extremo (> 1.5 kg/sem). Alto riesgo de pérdida muscular y metabólica.",
                hardStop: true
            ))
        } else if input.weeklyChangeKg > 1.0 {
            flags.append(NutritionRiskFlag(
                id: UUID().uuidString,
                code: "pace_aggressive",
                severity: .WARNING,
                message: "Ritmo de pérdida agresivo (> 1 kg/sem). Puede comprometer masa muscular."
            ))
        }
    case .GAIN:
        if input.weeklyChangeKg > 0.75 {
            flags.append(NutritionRiskFlag(
                id: UUID().uuidString,
                code: "pace_gain_extreme",
                severity: .DANGER,
                message: "Ritmo de ganancia muy agresivo (> 0.75 kg/sem). Acumularás grasa en exceso.",
                hardStop: false
            ))
        } else if input.weeklyChangeKg > 0.5 {
            flags.append(NutritionRiskFlag(
                id: UUID().uuidString,
                code: "pace_gain_aggressive",
                severity: .WARNING,
                message: "Ritmo de ganancia elevado (> 0.5 kg/sem). Considera un superávit más moderado."
            ))
        }
    case .MAINTAIN: break
    }

    if input.goalMetric == .BODY_FAT && (input.goalValue < 5 || input.goalValue > 45) {
        flags.append(NutritionRiskFlag(
            id: UUID().uuidString,
            code: "bodyfat_unhealthy",
            severity: input.goalValue < 4 ? .DANGER : .WARNING,
            message: "% grasa fuera de rango saludable.",
            hardStop: input.goalValue < 4
        ))
    }

    if input.goalMetric == .MUSCLE_MASS && (input.goalValue < 20 || input.goalValue > 60) {
        flags.append(NutritionRiskFlag(
            id: UUID().uuidString,
            code: "muscle_unrealistic",
            severity: .WARNING,
            message: "% de músculo poco realista. Revisa tu meta."
        ))
    }

    if input.goalMetric == .WEIGHT {
        let hCm = input.settings.heightCm
        if hCm > 0 {
            let bmi = input.goalValue / ((hCm / 100) * (hCm / 100))
            if bmi < 17 || bmi > 33 {
                flags.append(NutritionRiskFlag(
                    id: UUID().uuidString,
                    code: "goal_bmi_extreme",
                    severity: (bmi < 16.5 || bmi > 35) ? .DANGER : .WARNING,
                    message: "Meta de peso implica IMC \((bmi * 10).rounded() / 10.0).",
                    hardStop: bmi < 16.5 || bmi > 35
                ))
            }
        }
    }

    return flags
}
