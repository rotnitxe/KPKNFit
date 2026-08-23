import Foundation

// ─── Enums ────────────────────────────────────────────────────────────────────

public enum Gender: String, Codable {
    case MALE
    case FEMALE
    case OTHER
}

public enum MoodState: String, Codable {
    case HAPPY, NEUTRAL, SAD, ANXIOUS, ENERGETIC
}

public enum IntensityLevel: String, Codable {
    case LOW, MEDIUM, HIGH
}

public enum ReadinessColor: String, Codable {
    case GREEN, YELLOW, RED
}

public enum RecoveryChannelId: String, Codable {
    case MUSCULAR, SYSTEM, STRUCTURE
}

public enum RecoveryBand: String, Codable {
    case HIGH, NORMAL, MODERATE, LOW, CRITICAL
}

public enum ArticularBattery: String, Codable {
    case SHOULDER, ELBOW, KNEE, HIP, ANKLE, CERVICAL, LUMBAR
}

public enum ArticularStatus: String, Codable {
    case OPTIMAL, RECOVERING, EXHAUSTED
}

// ─── Canonical muscular finish contract (Android/backend parity) ─────────────

/// Persisted once, at the exact finish instant.  Optional fields keep legacy
/// JSONL records decodable while the v2 engine rolls out across platforms.
public struct MuscleSessionImpactV2: Codable {
    public let stressUnits: Double
    public let capacityAtCompletion: Double
    public let immediateDrainPct: Double
    public let directStressUnits: Double
    public let indirectStressUnits: Double

    public init(
        stressUnits: Double,
        capacityAtCompletion: Double,
        immediateDrainPct: Double,
        directStressUnits: Double,
        indirectStressUnits: Double
    ) {
        self.stressUnits = stressUnits
        self.capacityAtCompletion = capacityAtCompletion
        self.immediateDrainPct = immediateDrainPct
        self.directStressUnits = directStressUnits
        self.indirectStressUnits = indirectStressUnits
    }
}

public struct MuscularSessionImpactV2: Codable {
    public let modelVersion: Int
    public let completionInstantIso: String
    public let globalMuscularDrain: Double
    public let perMuscle: [String: MuscleSessionImpactV2]
    public let involvedVolumeMuscles: [String]
    public let setInputHash: String
    public let contextHash: String

    public init(
        modelVersion: Int = 2,
        completionInstantIso: String,
        globalMuscularDrain: Double,
        perMuscle: [String: MuscleSessionImpactV2],
        involvedVolumeMuscles: [String],
        setInputHash: String,
        contextHash: String
    ) {
        self.modelVersion = modelVersion
        self.completionInstantIso = completionInstantIso
        self.globalMuscularDrain = globalMuscularDrain
        self.perMuscle = perMuscle
        self.involvedVolumeMuscles = involvedVolumeMuscles
        self.setInputHash = setInputHash
        self.contextHash = contextHash
    }
}

public struct ManualMuscleBatteryOverride: Codable {
    public let battery: Int
    public let anchorEpochMs: Int64
    public let sourceSessionId: String?
    public let automaticBatteryAtAnchor: Int

    public init(
        battery: Int,
        anchorEpochMs: Int64,
        sourceSessionId: String? = nil,
        automaticBatteryAtAnchor: Int
    ) {
        self.battery = battery
        self.anchorEpochMs = anchorEpochMs
        self.sourceSessionId = sourceSessionId
        self.automaticBatteryAtAnchor = automaticBatteryAtAnchor
    }
}

// ─── User Vitals ──────────────────────────────────────────────────────────────

public struct UserVitals: Codable {
    public let age: Int?
    public let weight: Double?
    public let height: Double?
    public let gender: Gender?
    public let bodyFatPercentage: Double?
    public let muscleMassPercentage: Double?
    public let targetWeight: Double?

    public init(
        age: Int? = nil,
        weight: Double? = nil,
        height: Double? = nil,
        gender: Gender? = nil,
        bodyFatPercentage: Double? = nil,
        muscleMassPercentage: Double? = nil,
        targetWeight: Double? = nil
    ) {
        self.age = age
        self.weight = weight
        self.height = height
        self.gender = gender
        self.bodyFatPercentage = bodyFatPercentage
        self.muscleMassPercentage = muscleMassPercentage
        self.targetWeight = targetWeight
    }

    public func copy(weight: Double) -> UserVitals {
        UserVitals(age: age, weight: weight, height: height, gender: gender, bodyFatPercentage: bodyFatPercentage, muscleMassPercentage: muscleMassPercentage, targetWeight: targetWeight)
    }
}

// ─── Wellbeing & Logging ──────────────────────────────────────────────────────

public struct DailyWellbeingLog: Codable {
    public let id: String
    public let date: String
    public let sleepQuality: Int
    public let stressLevel: Int
    public let doms: Int
    public let motivation: Int
    public let sleepHours: Double
    public let moodState: MoodState?
    public let workIntensity: IntensityLevel?
    public let studyIntensity: IntensityLevel?
    public let manualMuscularBattery: Int?
    public let manualNeuralBattery: Int?
    public let manualSpinalBattery: Int?
    public let manualMuscleBatteries: [String: Int]
    public let manualMuscleOverridesV2: [String: ManualMuscleBatteryOverride]?
    public let manualBatteryAnchorMs: Int64?
    public let notes: String?
    public let preWorkoutDiscomforts: [String]

    public init(
        id: String,
        date: String,
        sleepQuality: Int = 3,
        stressLevel: Int = 3,
        doms: Int = 1,
        motivation: Int = 3,
        sleepHours: Double = 7.5,
        moodState: MoodState? = nil,
        workIntensity: IntensityLevel? = nil,
        studyIntensity: IntensityLevel? = nil,
        manualMuscularBattery: Int? = nil,
        manualNeuralBattery: Int? = nil,
        manualSpinalBattery: Int? = nil,
        manualMuscleBatteries: [String: Int] = [:],
        manualMuscleOverridesV2: [String: ManualMuscleBatteryOverride]? = nil,
        manualBatteryAnchorMs: Int64? = nil,
        notes: String? = nil,
        preWorkoutDiscomforts: [String] = []
    ) {
        self.id = id
        self.date = date
        self.sleepQuality = sleepQuality
        self.stressLevel = stressLevel
        self.doms = doms
        self.motivation = motivation
        self.sleepHours = sleepHours
        self.moodState = moodState
        self.workIntensity = workIntensity
        self.studyIntensity = studyIntensity
        self.manualMuscularBattery = manualMuscularBattery
        self.manualNeuralBattery = manualNeuralBattery
        self.manualSpinalBattery = manualSpinalBattery
        self.manualMuscleBatteries = manualMuscleBatteries
        self.manualMuscleOverridesV2 = manualMuscleOverridesV2
        self.manualBatteryAnchorMs = manualBatteryAnchorMs
        self.notes = notes
        self.preWorkoutDiscomforts = preWorkoutDiscomforts
    }
}

// ─── Post-Session Feedback ────────────────────────────────────────────────────

public struct PostSessionFeedback: Codable {
    public let logId: String
    public let date: String
    public let cnsRecovery: Int
    public let muscleFeedback: [String: MuscleFeedbackEntry]
    public let unresolvedDiscomfortIds: [String]

    public init(
        logId: String,
        date: String,
        cnsRecovery: Int = 7,
        muscleFeedback: [String: MuscleFeedbackEntry] = [:],
        unresolvedDiscomfortIds: [String] = []
    ) {
        self.logId = logId
        self.date = date
        self.cnsRecovery = cnsRecovery
        self.muscleFeedback = muscleFeedback
        self.unresolvedDiscomfortIds = unresolvedDiscomfortIds
    }
}

public struct MuscleFeedbackEntry: Codable {
    public let doms: Int
    public let jointPain: Bool
    public let strengthCapacity: Int
    public let notes: String

    public init(
        doms: Int = 1,
        jointPain: Bool = false,
        strengthCapacity: Int = 7,
        notes: String = ""
    ) {
        self.doms = doms
        self.jointPain = jointPain
        self.strengthCapacity = strengthCapacity
        self.notes = notes
    }
}

public struct PendingQuestionnaire: Codable {
    public let logId: String
    public let sessionName: String
    public let muscleGroups: [String]
    public let stillPresentDiscomfortIds: [String]
    public let scheduledTimeMs: Int64

    public init(
        logId: String,
        sessionName: String,
        muscleGroups: [String] = [],
        stillPresentDiscomfortIds: [String] = [],
        scheduledTimeMs: Int64
    ) {
        self.logId = logId
        self.sessionName = sessionName
        self.muscleGroups = muscleGroups
        self.stillPresentDiscomfortIds = stillPresentDiscomfortIds
        self.scheduledTimeMs = scheduledTimeMs
    }
}

// ─── Post-Exercise Result ─────────────────────────────────────────────────────

public struct PostExerciseResult {
    public let technicalQuality: Int
    public let mood: Int
    public let discomforts: [String]

    public init(technicalQuality: Int, mood: Int, discomforts: [String]) {
        self.technicalQuality = technicalQuality
        self.mood = mood
        self.discomforts = discomforts
    }
}

// ─── AUGE Battery Results ─────────────────────────────────────────────────────

public struct GlobalBatteries: Codable {
    public let muscular: Int
    public let cnc: Int
    public let spinal: Int

    public init(muscular: Int = 100, cnc: Int = 100, spinal: Int = 100) {
        self.muscular = muscular
        self.cnc = cnc
        self.spinal = spinal
    }

    public var system: Int { cnc }
    public var structure: Int { spinal }
}

// MuscleRecoveryStatus and RecoveryStatus are defined in SessionModels.swift

public struct AugeReadinessVerdict: Codable {
    public let score: Int
    public let label: String
    public let color: ReadinessColor
    public let details: [String]
    public let action: String
    public let confidenceLabel: String

    public init(
        score: Int,
        label: String,
        color: ReadinessColor,
        details: [String] = [],
        action: String = "",
        confidenceLabel: String = "Media"
    ) {
        self.score = score
        self.label = label
        self.color = color
        self.details = details
        self.action = action
        self.confidenceLabel = confidenceLabel
    }
}

public struct RecoveryChannelSnapshot: Codable {
    public let id: RecoveryChannelId
    public let title: String
    public let shortTitle: String
    public let score: Int
    public let band: RecoveryBand
    public let description: String
    public let action: String
    public let causes: [String]
    public let confidence: Int
    public let editable: Bool

    public init(
        id: RecoveryChannelId,
        title: String,
        shortTitle: String,
        score: Int,
        band: RecoveryBand,
        description: String,
        action: String,
        causes: [String] = [],
        confidence: Int = 50,
        editable: Bool = true
    ) {
        self.id = id
        self.title = title
        self.shortTitle = shortTitle
        self.score = score
        self.band = band
        self.description = description
        self.action = action
        self.causes = causes
        self.confidence = confidence
        self.editable = editable
    }
}

public struct RecoveryDashboard: Codable {
    public let overallScore: Int
    public let headline: String
    public let summary: String
    public let recommendation: String
    public let confidenceLabel: String
    public let channels: [RecoveryChannelSnapshot]

    public init(
        overallScore: Int = 50,
        headline: String = "",
        summary: String = "",
        recommendation: String = "",
        confidenceLabel: String = "",
        channels: [RecoveryChannelSnapshot] = []
    ) {
        self.overallScore = overallScore
        self.headline = headline
        self.summary = summary
        self.recommendation = recommendation
        self.confidenceLabel = confidenceLabel
        self.channels = channels
    }

    public func channelScore(id: RecoveryChannelId, fallback: Int = 100) -> Int {
        (channels.first { $0.id == id }?.score ?? fallback).clamped(to: 0...100)
    }
}

public struct ArticularBatteryState: Codable {
    public let recoveryScore: Int
    public let estimatedHoursToRecovery: Int
    public let status: ArticularStatus
    public let accumulatedStress: Double

    public init(
        recoveryScore: Int = 100,
        estimatedHoursToRecovery: Int = 0,
        status: ArticularStatus = .OPTIMAL,
        accumulatedStress: Double = 0.0
    ) {
        self.recoveryScore = recoveryScore
        self.estimatedHoursToRecovery = estimatedHoursToRecovery
        self.status = status
        self.accumulatedStress = accumulatedStress
    }
}

public struct AugeSnapshot {
    public let batteries: GlobalBatteries
    public let perMuscle: [String: MuscleRecoveryStatus]
    public let readiness: AugeReadinessVerdict?
    public let dashboard: RecoveryDashboard
    public let articular: [ArticularBattery: ArticularBatteryState]
    public let shouldSuggestAutoDeload: Bool
    public let cumulativeFatigue: Double
    public let autoDeloadMessage: String?
    public let isLoading: Bool

    public init(
        batteries: GlobalBatteries = GlobalBatteries(muscular: 100, cnc: 100, spinal: 100),
        perMuscle: [String: MuscleRecoveryStatus] = [:],
        readiness: AugeReadinessVerdict? = nil,
        dashboard: RecoveryDashboard = RecoveryDashboard(),
        articular: [ArticularBattery: ArticularBatteryState] = [:],
        shouldSuggestAutoDeload: Bool = false,
        cumulativeFatigue: Double = 0.0,
        autoDeloadMessage: String? = nil,
        isLoading: Bool = true
    ) {
        self.batteries = batteries
        self.perMuscle = perMuscle
        self.readiness = readiness
        self.dashboard = dashboard
        self.articular = articular
        self.shouldSuggestAutoDeload = shouldSuggestAutoDeload
        self.cumulativeFatigue = cumulativeFatigue
        self.autoDeloadMessage = autoDeloadMessage
        self.isLoading = isLoading
    }

    public func ringScore(id: RecoveryChannelId) -> Int {
        switch id {
        case .MUSCULAR: return dashboard.channelScore(id: id, fallback: batteries.muscular)
        case .SYSTEM:   return dashboard.channelScore(id: id, fallback: batteries.cnc)
        case .STRUCTURE: return dashboard.channelScore(id: id, fallback: batteries.spinal)
        }
    }
}

// ─── WorkoutReadinessBridge ───────────────────────────────────────────────────

public final class WorkoutReadinessBridge {
    public struct ReadinessAdjustments {
        public let neural: Int?
        public let muscular: Int?
        public let spinal: Int?
        public let perMuscle: [String: Int]
        public let sleepQuality: Int?

        public init(
            neural: Int? = nil,
            muscular: Int? = nil,
            spinal: Int? = nil,
            perMuscle: [String: Int] = [:],
            sleepQuality: Int? = nil
        ) {
            self.neural = neural
            self.muscular = muscular
            self.spinal = spinal
            self.perMuscle = perMuscle
            self.sleepQuality = sleepQuality
        }
    }

    public static let shared = WorkoutReadinessBridge()
    private var pending: ReadinessAdjustments?

    private init() {}

    public func store(_ adjustments: ReadinessAdjustments) {
        pending = adjustments
    }

    public func consume() -> ReadinessAdjustments? {
        let result = pending
        pending = nil
        return result
    }
}

// ─── Discomfort Catalog ───────────────────────────────────────────────────────

// Redundant DiscomfortSection removed (defined in DiscomfortCatalog.swift)


// ─── Helpers ──────────────────────────────────────────────────────────────────

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}

// ─── Auge Adaptive Models ─────────────────────────────────────────────────────

public struct AugeAdaptiveCache: Codable {
    public let personalizedRecoveryHours: [String: Double]
    public let muscleDeltas: [String: Double]
    public let cnsLearningDelta: Double
    public let spinalLearningDelta: Double
    public let cnsRecoveryHours: Double?
    public let spinalRecoveryHours: Double?
    public let cnsDrainMultiplier: Double
    public let spinalDrainMultiplier: Double
    public let muscleDrainMultipliers: [String: Double]
    /// Optional for legacy JSONL; nil/old values are invalidated on load.
    public let muscularBiasVersion: Int?
    public let totalObservations: Int
    public let lastUpdatedMs: Int64

    public init(
        personalizedRecoveryHours: [String: Double] = [:],
        muscleDeltas: [String: Double] = [:],
        cnsLearningDelta: Double = 0.0,
        spinalLearningDelta: Double = 0.0,
        cnsRecoveryHours: Double? = nil,
        spinalRecoveryHours: Double? = nil,
        cnsDrainMultiplier: Double = 1.0,
        spinalDrainMultiplier: Double = 1.0,
        muscleDrainMultipliers: [String: Double] = [:],
        muscularBiasVersion: Int? = 2,
        totalObservations: Int = 0,
        lastUpdatedMs: Int64 = 0
    ) {
        self.personalizedRecoveryHours = personalizedRecoveryHours
        self.muscleDeltas = muscleDeltas
        self.cnsLearningDelta = cnsLearningDelta
        self.spinalLearningDelta = spinalLearningDelta
        self.cnsRecoveryHours = cnsRecoveryHours
        self.spinalRecoveryHours = spinalRecoveryHours
        self.cnsDrainMultiplier = cnsDrainMultiplier
        self.spinalDrainMultiplier = spinalDrainMultiplier
        self.muscleDrainMultipliers = muscleDrainMultipliers
        self.muscularBiasVersion = muscularBiasVersion
        self.totalObservations = totalObservations
        self.lastUpdatedMs = lastUpdatedMs
    }
}

public struct RecoveryLearningObservation: Codable {
    public let muscle: String
    public let predictedBattery: Int
    public let actualBattery: Int
    public let sessionStress: Double
    public let hoursSinceSession: Double
    public let sleepQuality: Int
    public let nutritionMultiplier: Double
    public let stressLevel: Int

    public init(
        muscle: String,
        predictedBattery: Int,
        actualBattery: Int,
        sessionStress: Double,
        hoursSinceSession: Double,
        sleepQuality: Int = 3,
        nutritionMultiplier: Double = 1.0,
        stressLevel: Int = 3
    ) {
        self.muscle = muscle
        self.predictedBattery = predictedBattery
        self.actualBattery = actualBattery
        self.sessionStress = sessionStress
        self.hoursSinceSession = hoursSinceSession
        self.sleepQuality = sleepQuality
        self.nutritionMultiplier = nutritionMultiplier
        self.stressLevel = stressLevel
    }
}

// ─── Additional Auge Models ───────────────────────────────────────────────────

public struct PhysiologicalFloor: Codable {
    public let muscular: Int
    public let cns: Int
    public let spinal: Int

    public init(muscular: Int, cns: Int, spinal: Int) {
        self.muscular = muscular
        self.cns = cns
        self.spinal = spinal
    }
}

public struct BatteryTanks: Codable {
    public let cns: Double
    public let muscular: Double
    public let spinal: Double

    public init(cns: Double, muscular: Double, spinal: Double) {
        self.cns = cns
        self.muscular = muscular
        self.spinal = spinal
    }
}

public struct PredictedDrain: Codable {
    public let cns: Int
    public let muscular: Int
    public let spinal: Int

    public init(cns: Int, muscular: Int, spinal: Int) {
        self.cns = cns
        self.muscular = muscular
        self.spinal = spinal
    }
}

// ─── Wellbeing & Logging (continued) ──────────────────────────────────────────

public struct SleepLog: Codable {
    public let id: String
    public let date: String
    public let endTime: String
    public let duration: Double

    public init(id: String, date: String, endTime: String, duration: Double) {
        self.id = id
        self.date = date
        self.endTime = endTime
        self.duration = duration
    }
}

// ─── AUGE Metrics (per-exercise) ─────────────────────────────────────────────

public struct AugeMetrics: Codable {
    public let efc: Double
    public let ssc: Double
    public let cnc: Double

    public init(efc: Double = 2.5, ssc: Double = 0.5, cnc: Double = 2.5) {
        self.efc = efc
        self.ssc = ssc
        self.cnc = cnc
    }

    public var snc: Double { cnc }
}

public struct SleepRecommendation: Codable {
    public let targetHours: Double
    public let reasons: [String]

    public init(targetHours: Double, reasons: [String]) {
        self.targetHours = targetHours
        self.reasons = reasons
    }
}

// ─── TTC / Articular Battery ──────────────────────────────────────────────────

public enum AlertSeverity: String, Codable {
    case WARNING
    case DANGER
}

public struct StructuralReadinessBreakdown: Codable {
    public let muscleName: String
    public let muscleBattery: Int
    public let articularBattery: Int
    public let combinedBattery: Int
    public let limitingBattery: Int
    public let relatedArticular: [ArticularBattery]

    public init(muscleName: String, muscleBattery: Int, articularBattery: Int, combinedBattery: Int, limitingBattery: Int, relatedArticular: [ArticularBattery]) {
        self.muscleName = muscleName
        self.muscleBattery = muscleBattery
        self.articularBattery = articularBattery
        self.combinedBattery = combinedBattery
        self.limitingBattery = limitingBattery
        self.relatedArticular = relatedArticular
    }
}

public struct TendonImbalanceAlert: Codable {
    public let type: AlertSeverity
    public let muscleLabel: String
    public let articularLabel: String
    public let muscleBattery: Int
    public let articularBattery: Int
    public let gap: Int
    public let message: String

    public init(type: AlertSeverity, muscleLabel: String, articularLabel: String, muscleBattery: Int, articularBattery: Int, gap: Int, message: String) {
        self.type = type
        self.muscleLabel = muscleLabel
        self.articularLabel = articularLabel
        self.muscleBattery = muscleBattery
        self.articularBattery = articularBattery
        self.gap = gap
        self.message = message
    }
}

public enum SuggestionType: String, Codable {
    case BIOMECHANICAL
    case NUTRITION
}

public struct TendonCompensationSuggestion: Codable {
    public let type: SuggestionType
    public let title: String
    public let message: String

    public init(type: SuggestionType, title: String, message: String) {
        self.type = type
        self.title = title
        self.message = message
    }
}

// ─── Mesocycle Stress EMA ──────────────────────────────────────────────────────

public enum StressTrend: String, Codable {
    case RISING
    case STABLE
    case FALLING
}

public struct MesocycleStressEMA: Codable {
    public let programId: String
    public let mesoIndex: Int
    public let emaValue: Double
    public let sessionCount: Int
    public let latestStressScore: Double?
    public let stressTrend: StressTrend
    public let computedAtMs: Int64

    public init(programId: String, mesoIndex: Int, emaValue: Double, sessionCount: Int, latestStressScore: Double?, stressTrend: StressTrend, computedAtMs: Int64) {
        self.programId = programId
        self.mesoIndex = mesoIndex
        self.emaValue = emaValue
        self.sessionCount = sessionCount
        self.latestStressScore = latestStressScore
        self.stressTrend = stressTrend
        self.computedAtMs = computedAtMs
    }
}

// ─── Mis RINGS: Rankings ──────────────────────────────────────────────────────

public struct SessionDrainRanking: Codable {
    public let logId: String
    public let sessionName: String
    public let date: String
    public let totalDrain: Double
    public let cnsDrain: Double
    public let muscularDrain: Double
    public let spinalDrain: Double

    public init(logId: String, sessionName: String, date: String, totalDrain: Double, cnsDrain: Double, muscularDrain: Double, spinalDrain: Double) {
        self.logId = logId
        self.sessionName = sessionName
        self.date = date
        self.totalDrain = totalDrain
        self.cnsDrain = cnsDrain
        self.muscularDrain = muscularDrain
        self.spinalDrain = spinalDrain
    }
}

public struct ExerciseDrainRanking: Codable {
    public let exerciseName: String
    public let exerciseDbId: String?
    public let overallDrain: Double
    public let muscularDrain: Double
    public let cnsDrain: Double
    public let spinalDrain: Double
    public let sessionCount: Int

    public init(exerciseName: String, exerciseDbId: String?, overallDrain: Double, muscularDrain: Double, cnsDrain: Double, spinalDrain: Double, sessionCount: Int) {
        self.exerciseName = exerciseName
        self.exerciseDbId = exerciseDbId
        self.overallDrain = overallDrain
        self.muscularDrain = muscularDrain
        self.cnsDrain = cnsDrain
        self.spinalDrain = spinalDrain
        self.sessionCount = sessionCount
    }
}

// ─── Mis RINGS: Recuperación personal ─────────────────────────────────────────

public struct PersonalRecoveryStats: Codable {
    public let avgRecoveryHoursOverall: Double
    public let avgRecoveryHoursMuscular: Double
    public let avgRecoveryHoursCns: Double
    public let avgRecoveryHoursSpinal: Double
    public let fastestRecoverySession: String?
    public let slowestRecoverySession: String?
    public let sampleCount: Int

    public init(avgRecoveryHoursOverall: Double, avgRecoveryHoursMuscular: Double, avgRecoveryHoursCns: Double, avgRecoveryHoursSpinal: Double, fastestRecoverySession: String?, slowestRecoverySession: String?, sampleCount: Int) {
        self.avgRecoveryHoursOverall = avgRecoveryHoursOverall
        self.avgRecoveryHoursMuscular = avgRecoveryHoursMuscular
        self.avgRecoveryHoursCns = avgRecoveryHoursCns
        self.avgRecoveryHoursSpinal = avgRecoveryHoursSpinal
        self.fastestRecoverySession = fastestRecoverySession
        self.slowestRecoverySession = slowestRecoverySession
        self.sampleCount = sampleCount
    }
}

// ─── Mis RINGS: Interferencia ──────────────────────────────────────────────────

public struct SharedMuscleInterference: Codable {
    public let muscleName: String
    public let drainFromSessionA: Double
    public let usageInSessionB: Double
    public let recoveryDeficit: Double

    public init(muscleName: String, drainFromSessionA: Double, usageInSessionB: Double, recoveryDeficit: Double) {
        self.muscleName = muscleName
        self.drainFromSessionA = drainFromSessionA
        self.usageInSessionB = usageInSessionB
        self.recoveryDeficit = recoveryDeficit
    }
}

public struct SessionInterference: Codable {
    public let sessionAId: String
    public let sessionAName: String
    public let sessionBId: String
    public let sessionBName: String
    public let sessionADate: String?
    public let sessionBDate: String?
    public let interferencePercent: Int
    public let sharedMuscles: [SharedMuscleInterference]
    public let recommendation: String
    public let isFromHistory: Bool
    public let hoursApart: Double

    public init(sessionAId: String, sessionAName: String, sessionBId: String, sessionBName: String, sessionADate: String?, sessionBDate: String?, interferencePercent: Int, sharedMuscles: [SharedMuscleInterference], recommendation: String, isFromHistory: Bool, hoursApart: Double) {
        self.sessionAId = sessionAId
        self.sessionAName = sessionAName
        self.sessionBId = sessionBId
        self.sessionBName = sessionBName
        self.sessionADate = sessionADate
        self.sessionBDate = sessionBDate
        self.interferencePercent = interferencePercent
        self.sharedMuscles = sharedMuscles
        self.recommendation = recommendation
        self.isFromHistory = isFromHistory
        self.hoursApart = hoursApart
    }
}

// ─── Mis RINGS: Sueño extendido ───────────────────────────────────────────────

public struct SleepLogExtended: Codable {
    public let id: String
    public let date: String
    public let bedTime: String
    public let wakeTime: String
    public let duration: Double
    public let quality: Int
    public let awakenings: Int
    public let notes: String?

    public init(id: String, date: String, bedTime: String, wakeTime: String, duration: Double, quality: Int = 3, awakenings: Int = 0, notes: String? = nil) {
        self.id = id
        self.date = date
        self.bedTime = bedTime
        self.wakeTime = wakeTime
        self.duration = duration
        self.quality = quality
        self.awakenings = awakenings
        self.notes = notes
    }

    public func toSleepLog() -> SleepLog {
        SleepLog(id: id, date: date, endTime: wakeTime, duration: duration)
    }
}

// ─── Readiness por Patrón de Movimiento y Ejercicio ───────────────────────────

public struct MovementPatternReadiness: Codable {
    public let patternId: String
    public let patternLabel: String
    public let overallScore: Int
    public let exerciseCount: Int
    public let totalSets: Int
    public let contributingMuscles: [String]
    public let averageMuscleRecovery: Int

    public init(patternId: String, patternLabel: String, overallScore: Int, exerciseCount: Int, totalSets: Int, contributingMuscles: [String], averageMuscleRecovery: Int) {
        self.patternId = patternId
        self.patternLabel = patternLabel
        self.overallScore = overallScore
        self.exerciseCount = exerciseCount
        self.totalSets = totalSets
        self.contributingMuscles = contributingMuscles
        self.averageMuscleRecovery = averageMuscleRecovery
    }
}

public struct ExerciseReadiness: Codable {
    public let exerciseId: String
    public let exerciseName: String
    public let overallScore: Int
    public let muscularComponent: Int
    public let cnsComponent: Int
    public let spinalComponent: Int
    public let articularComponent: Int
    public let structuralComponent: Int
    public let relatedArticular: [ArticularBattery]
    public let muscularWeight: Double
    public let cnsWeight: Double
    public let spinalWeight: Double
    public let articularWeight: Double
    public let setsPenaltyFactor: Double
    public let intensityPenaltyFactor: Double
    public let ermProximityFactor: Double
    public let patternId: String?
    public let involvedMuscleIds: [String]
    public let limitingFactor: String?
    public let limitingDetail: String?

    public init(exerciseId: String, exerciseName: String, overallScore: Int, muscularComponent: Int, cnsComponent: Int, spinalComponent: Int, articularComponent: Int, structuralComponent: Int, relatedArticular: [ArticularBattery], muscularWeight: Double, cnsWeight: Double, spinalWeight: Double, articularWeight: Double, setsPenaltyFactor: Double, intensityPenaltyFactor: Double, ermProximityFactor: Double, patternId: String?, involvedMuscleIds: [String], limitingFactor: String? = nil, limitingDetail: String? = nil) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.overallScore = overallScore
        self.muscularComponent = muscularComponent
        self.cnsComponent = cnsComponent
        self.spinalComponent = spinalComponent
        self.articularComponent = articularComponent
        self.structuralComponent = structuralComponent
        self.relatedArticular = relatedArticular
        self.muscularWeight = muscularWeight
        self.cnsWeight = cnsWeight
        self.spinalWeight = spinalWeight
        self.articularWeight = articularWeight
        self.setsPenaltyFactor = setsPenaltyFactor
        self.intensityPenaltyFactor = intensityPenaltyFactor
        self.ermProximityFactor = ermProximityFactor
        self.patternId = patternId
        self.involvedMuscleIds = involvedMuscleIds
        self.limitingFactor = limitingFactor
        self.limitingDetail = limitingDetail
    }
}

public struct SetAdjustmentSuggestion: Codable {
    public let exerciseId: String
    public let setIndex: Int
    public let currentPlannedWeight: Double
    public let readinessScore: Int
    public let severityFactor: Double
    public let reductionPercent: Double
    public let suggestedWeight: Double
    public let averageErm: Double?
    public let reason: String
    public let suggestedLoadMode: LoadModeV2

    public init(exerciseId: String, setIndex: Int, currentPlannedWeight: Double, readinessScore: Int, severityFactor: Double, reductionPercent: Double, suggestedWeight: Double, averageErm: Double?, reason: String, suggestedLoadMode: LoadModeV2 = .LOAD) {
        self.exerciseId = exerciseId
        self.setIndex = setIndex
        self.currentPlannedWeight = currentPlannedWeight
        self.readinessScore = readinessScore
        self.severityFactor = severityFactor
        self.reductionPercent = reductionPercent
        self.suggestedWeight = suggestedWeight
        self.averageErm = averageErm
        self.reason = reason
        self.suggestedLoadMode = suggestedLoadMode
    }
}
