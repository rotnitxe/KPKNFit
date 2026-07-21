import Foundation

public enum EnergyConfidence: String, Codable {
    case HIGH
    case MEDIUM
    case LOW
}

public enum EnergyEstimateSource: String, Codable {
    case PLANNED
    case LIVE
    case FINAL
}

public enum DailyEnergyStatus: String, Codable {
    case DEFICIT
    case MAINTENANCE
    case SURPLUS
}

public struct CalorieRange: Codable {
    public let low: Int
    public let mid: Int
    public let high: Int

    public init(low: Int = 0, mid: Int = 0, high: Int = 0) {
        self.low = low
        self.mid = mid
        self.high = high
    }
}

public struct ExerciseEnergyContribution: Codable {
    public let exerciseId: String
    public let exerciseDbId: String?
    public let exerciseName: String
    public let activeKcal: Int
    public let epocKcal: Int
    public let totalKcal: Int
    public let percentageOfSession: Double
    public let completedSets: Int
    public let totalSets: Int

    public init(
        exerciseId: String = "",
        exerciseDbId: String? = nil,
        exerciseName: String = "",
        activeKcal: Int = 0,
        epocKcal: Int = 0,
        totalKcal: Int = 0,
        percentageOfSession: Double = 0.0,
        completedSets: Int = 0,
        totalSets: Int = 0
    ) {
        self.exerciseId = exerciseId
        self.exerciseDbId = exerciseDbId
        self.exerciseName = exerciseName
        self.activeKcal = activeKcal
        self.epocKcal = epocKcal
        self.totalKcal = totalKcal
        self.percentageOfSession = percentageOfSession
        self.completedSets = completedSets
        self.totalSets = totalSets
    }
}

public struct SessionEnergySummary: Codable {
    public let activeKcal: CalorieRange
    public let epocKcal: CalorieRange
    public let totalKcal: CalorieRange
    public let projectedTotalKcal: Int?
    public let confidence: EnergyConfidence
    public let source: EnergyEstimateSource
    public let methodVersion: String
    public let exerciseContributions: [ExerciseEnergyContribution]
    public let notes: [String]

    public init(
        activeKcal: CalorieRange = CalorieRange(),
        epocKcal: CalorieRange = CalorieRange(),
        totalKcal: CalorieRange = CalorieRange(),
        projectedTotalKcal: Int? = nil,
        confidence: EnergyConfidence = .LOW,
        source: EnergyEstimateSource = .PLANNED,
        methodVersion: String = "auge-energy-v1",
        exerciseContributions: [ExerciseEnergyContribution] = [],
        notes: [String] = []
    ) {
        self.activeKcal = activeKcal
        self.epocKcal = epocKcal
        self.totalKcal = totalKcal
        self.projectedTotalKcal = projectedTotalKcal
        self.confidence = confidence
        self.source = source
        self.methodVersion = methodVersion
        self.exerciseContributions = exerciseContributions
        self.notes = notes
    }
}

public struct DailyEnergyBalance: Codable {
    public let consumedKcal: Int
    public let trainingBurnKcal: Int
    public let netKcal: Int
    public let targetKcal: Int
    public let deltaFromTarget: Int
    public let status: DailyEnergyStatus

    public init(
        consumedKcal: Int = 0,
        trainingBurnKcal: Int = 0,
        netKcal: Int = 0,
        targetKcal: Int = 0,
        deltaFromTarget: Int = 0,
        status: DailyEnergyStatus = .MAINTENANCE
    ) {
        self.consumedKcal = consumedKcal
        self.trainingBurnKcal = trainingBurnKcal
        self.netKcal = netKcal
        self.targetKcal = targetKcal
        self.deltaFromTarget = deltaFromTarget
        self.status = status
    }
}
