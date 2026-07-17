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

public enum DiscomfortSection: String, Codable {
    case SHOULDERS_ARMS
    case SPINE_NECK
    case HIP_PELVIS
    case KNEE
    case ANKLE_FOOT
    case GENERAL

    public var label: String {
        switch self {
        case .SHOULDERS_ARMS: return "Hombro y brazos"
        case .SPINE_NECK: return "Columna y cuello"
        case .HIP_PELVIS: return "Cadera y pelvis"
        case .KNEE: return "Rodilla"
        case .ANKLE_FOOT: return "Tobillo y pie"
        case .GENERAL: return "General"
        }
    }
}

public struct DiscomfortCatalogEntry: Codable {
    public let id: String
    public let label: String
    public let description: String
    public let section: DiscomfortSection
    public let relatedMuscles: [String]

    public init(
        id: String,
        label: String,
        description: String,
        section: DiscomfortSection,
        relatedMuscles: [String] = []
    ) {
        self.id = id
        self.label = label
        self.description = description
        self.section = section
        self.relatedMuscles = relatedMuscles
    }
}

public let DISCOMFORT_CATALOG: [DiscomfortCatalogEntry] = [
    DiscomfortCatalogEntry(id: "none", label: "Sin molestias", description: "No hay molestias reportadas", section: .GENERAL),
    DiscomfortCatalogEntry(id: "shoulder_anterior", label: "Hombro anterior", description: "Dolor en la parte frontal del hombro", section: .SHOULDERS_ARMS, relatedMuscles: ["Deltoides anterior"]),
    DiscomfortCatalogEntry(id: "shoulder_posterior", label: "Hombro posterior", description: "Dolor en la parte posterior del hombro", section: .SHOULDERS_ARMS, relatedMuscles: ["Deltoides posterior"]),
    DiscomfortCatalogEntry(id: "elbow_medial", label: "Codo interno", description: "Dolor en el epicóndilo medial", section: .SHOULDERS_ARMS, relatedMuscles: ["Antebrazo"]),
    DiscomfortCatalogEntry(id: "elbow_lateral", label: "Codo externo", description: "Dolor en el epicóndilo lateral", section: .SHOULDERS_ARMS, relatedMuscles: ["Antebrazo"]),
    DiscomfortCatalogEntry(id: "wrist_hand", label: "Muñeca/mano", description: "Dolor en muñeca o mano", section: .SHOULDERS_ARMS, relatedMuscles: ["Antebrazo"]),
    DiscomfortCatalogEntry(id: "neck_cervical", label: "Cervical", description: "Dolor en la zona cervical", section: .SPINE_NECK, relatedMuscles: ["Trapecio", "Cervicales"]),
    DiscomfortCatalogEntry(id: "upper_back", label: "Espalda alta", description: "Dolor en zona torácica alta", section: .SPINE_NECK, relatedMuscles: ["Dorsales", "Trapecio"]),
    DiscomfortCatalogEntry(id: "lumbar", label: "Lumbar", description: "Dolor en la zona lumbar", section: .SPINE_NECK, relatedMuscles: ["Lumbar"]),
    DiscomfortCatalogEntry(id: "hip_front", label: "Cadera frontal", description: "Dolor en la parte frontal de la cadera", section: .HIP_PELVIS, relatedMuscles: ["Iliopsoas"]),
    DiscomfortCatalogEntry(id: "hip_lateral", label: "Cadera lateral", description: "Dolor lateral en la cadera (TFL/IT band)", section: .HIP_PELVIS, relatedMuscles: ["Glúteos", "TFL"]),
    DiscomfortCatalogEntry(id: "adductor_groin", label: "Aductores/ingle", description: "Dolor en aductores o zona inguinal", section: .HIP_PELVIS, relatedMuscles: ["Aductores"]),
    DiscomfortCatalogEntry(id: "hamstring_proximal", label: "Isquios proximal", description: "Dolor en la inserción proximal de isquiosurales", section: .HIP_PELVIS, relatedMuscles: ["Isquiosurales"]),
    DiscomfortCatalogEntry(id: "knee_patellar", label: "Rodilla rotuliana", description: "Dolor en la rótula o tendón rotuliano", section: .KNEE, relatedMuscles: ["Cuádriceps"]),
    DiscomfortCatalogEntry(id: "knee_medial", label: "Rodilla interna", description: "Dolor en el compartmento interno de la rodilla", section: .KNEE, relatedMuscles: ["Vasto interno"]),
    DiscomfortCatalogEntry(id: "achilles", label: "Aquiles", description: "Dolor en el tendón de Aquiles", section: .ANKLE_FOOT, relatedMuscles: ["Gemelos", "Sóleo"]),
    DiscomfortCatalogEntry(id: "ankle", label: "Tobillo", description: "Dolor en el tobillo", section: .ANKLE_FOOT, relatedMuscles: ["Gemelos"]),
    DiscomfortCatalogEntry(id: "plantar_foot", label: "Planta del pie", description: "Dolor en la fascia plantar", section: .ANKLE_FOOT, relatedMuscles: ["Pantorrilla"]),
]

public let DISCOMFORT_CATALOG_BY_ID: [String: DiscomfortCatalogEntry] = {
    var dict: [String: DiscomfortCatalogEntry] = [:]
    for entry in DISCOMFORT_CATALOG { dict[entry.id] = entry }
    return dict
}()

public func discomfortLabel(id: String) -> String {
    DISCOMFORT_CATALOG_BY_ID[id]?.label ?? id
}

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
