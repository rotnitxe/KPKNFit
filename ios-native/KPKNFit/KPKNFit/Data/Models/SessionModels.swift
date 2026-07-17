import Foundation

// ─── Enums ────────────────────────────────────────────────────────────────────

public enum RecoveryStatus: String, Codable {
    case FRESH
    case OPTIMAL
    case RECOVERING
    case EXHAUSTED
}

public struct MuscleRecoveryStatus: Codable {
    public let muscleName: String
    public let recoveryScore: Int
    public let hoursToRecovery: Int
    public let hoursSinceLastSession: Int
    public let effectiveSets: Int
    public let status: RecoveryStatus

    public init(muscleName: String, recoveryScore: Int, hoursToRecovery: Int, hoursSinceLastSession: Int, effectiveSets: Int, status: RecoveryStatus) {
        self.muscleName = muscleName
        self.recoveryScore = recoveryScore
        self.hoursToRecovery = hoursToRecovery
        self.hoursSinceLastSession = hoursSinceLastSession
        self.effectiveSets = effectiveSets
        self.status = status
    }
}

public enum IntensityMode: String, Codable {
    case RPE, RIR, FAILURE, AMRAP, LOAD, SOLO_RM
}

public enum TrainingMode: String, Codable {
    case REPS, TIME, RM, CUSTOM, DISTANCE, SOLO_RPE, AMRAP
}

public enum LoadModeV2: String, Codable {
    case LOAD, BODYWEIGHT, LASTRE, ASSISTED
}

public enum UnitModeV2: String, Codable {
    case REPS, TIME, DISTANCE, CUSTOM
}

public enum UnilateralSideOrder: String, Codable {
    case LEFT_RIGHT, RIGHT_LEFT
}

public enum UnilateralMode: String, Codable {
    case BILATERAL, UNILATERAL
}

public enum UnilateralIntensityMode: String, Codable {
    case SHARED, INDEPENDENT
}

public enum HistoryColorV2: String, Codable {
    case NEUTRAL, YELLOW, RED
}

public enum PlanDeviationType: String, Codable {
    case WEIGHT_HIGH, WEIGHT_LOW
    case UNPLANNED_FAILURE, UNPLANNED_DROPSET, UNPLANNED_REST_PAUSE
    case REPS_HIGH, REPS_LOW
}

public enum ReplacementPersistenceScopeV2: String, Codable {
    case SESSION_ONLY, PERMANENT, MESOCYCLE_MATCHING
}

public enum MuscleRole: String, Codable {
    case PRIMARY, SECONDARY, STABILIZER, NEUTRALIZER
}

public enum PerformanceMode: String, Codable {
    case STANDARD, COMPETITION, CALIBRATOR
}

public enum AttemptResult: String, Codable {
    case SUCCESS, FAIL, UNKNOWN
}

public enum TimeStrategy: String, Codable {
    case FIXED, PROGRESSIVE
}

public enum TimeProgressionStrategyV3: String, Codable {
    case LOAD_THEN_TIME, TIME_THEN_LOAD
}

public enum ExerciseRelationshipType: String, Codable {
    case VARIATION, ALTERNATIVE, PROGRESSION, REGRESSION
}

public enum DifficultySignalV2: String, Codable {
    case EASIER, HARDER, MATCHED
}

// ─── Small Data Classes ──────────────────────────────────────────────────────

public struct DropSetData: Codable {
    public let weight: Double
    public let reps: Int
    public init(weight: Double, reps: Int) {
        self.weight = weight
        self.reps = reps
    }
}

public struct RestPauseData: Codable {
    public let restTime: Int
    public let reps: Int
    public init(restTime: Int, reps: Int) {
        self.restTime = restTime
        self.reps = reps
    }
}

public struct PlanDeviation: Codable {
    public let exerciseId: String
    public let exerciseName: String
    public let setIdx: Int
    public let type: PlanDeviationType
    public let detail: String
    public init(exerciseId: String, exerciseName: String, setIdx: Int, type: PlanDeviationType, detail: String) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.setIdx = setIdx
        self.type = type
        self.detail = detail
    }
}

public struct MobilitySeries: Identifiable, Codable {
    public let id: String
    public let exerciseDbId: String?
    public let name: String
    public let sets: Int
    public let reps: String?
    public let durationSeconds: Int?
    public let notes: String?
    public let associatedDiscomforts: [String]
    public let bodyZones: [String]
    public let movementPatterns: [String]
    public init(id: String, exerciseDbId: String? = nil, name: String, sets: Int = 1, reps: String? = nil, durationSeconds: Int? = nil, notes: String? = nil, associatedDiscomforts: [String] = [], bodyZones: [String] = [], movementPatterns: [String] = []) {
        self.id = id
        self.exerciseDbId = exerciseDbId
        self.name = name
        self.sets = sets
        self.reps = reps
        self.durationSeconds = durationSeconds
        self.notes = notes
        self.associatedDiscomforts = associatedDiscomforts
        self.bodyZones = bodyZones
        self.movementPatterns = movementPatterns
    }
}

public struct WarmupSetDefinition: Identifiable, Codable {
    public let id: String
    public let weight: Double?
    public let reps: Int?
    public let durationSeconds: Int?
    public let notes: String?
    public init(id: String, weight: Double? = nil, reps: Int? = nil, durationSeconds: Int? = nil, notes: String? = nil) {
        self.id = id
        self.weight = weight
        self.reps = reps
        self.durationSeconds = durationSeconds
        self.notes = notes
    }
}

public struct UnilateralTarget: Codable {
    public let targetReps: Int?
    public let targetWeight: Double?
    public init(targetReps: Int? = nil, targetWeight: Double? = nil) {
        self.targetReps = targetReps
        self.targetWeight = targetWeight
    }
}

public struct PlannedTechnique: Codable {
    public let technique: String
    public let value: Double?
    public init(technique: String, value: Double? = nil) {
        self.technique = technique
        self.value = value
    }
}

public struct PrReference: Codable {
    public let date: String?
    public let value: Double?
    public let source: String?
    public let weight: Double
    public let reps: Int

    public init(
        date: String? = nil,
        value: Double? = nil,
        source: String? = nil,
        weight: Double = 0.0,
        reps: Int = 0
    ) {
        self.date = date
        self.value = value
        self.source = source
        self.weight = weight > 0 ? weight : (value ?? 0.0)
        self.reps = reps
    }
}

public struct ConsolidatedWeight: Codable {
    public let weight: Double?
    public let source: String?
    public init(weight: Double? = nil, source: String? = nil) {
        self.weight = weight
        self.source = source
    }
}

public struct BrandEquivalency: Codable {
    public let brand: String
    public let equivalentWeight: Double?
    public init(brand: String, equivalentWeight: Double? = nil) {
        self.brand = brand
        self.equivalentWeight = equivalentWeight
    }
}

public struct DamageProfile: Codable {
    public let muscular: Double?
    public let cnc: Double?
    public let spinal: Double?
    public init(muscular: Double? = nil, cnc: Double? = nil, spinal: Double? = nil) {
        self.muscular = muscular
        self.cnc = cnc
        self.spinal = spinal
    }
}

public struct ExerciseSetupDetails: Codable {
    public let setupId: String?
    public let notes: String?
    public let seatPosition: String?
    public let pinPosition: String?
    public let equipmentNotes: String?
    public let barWeightKg: Double?

    public init(
        setupId: String? = nil,
        notes: String? = nil,
        seatPosition: String? = nil,
        pinPosition: String? = nil,
        equipmentNotes: String? = nil,
        barWeightKg: Double? = nil
    ) {
        self.setupId = setupId
        self.notes = notes
        self.seatPosition = seatPosition
        self.pinPosition = pinPosition
        self.equipmentNotes = equipmentNotes
        self.barWeightKg = barWeightKg
    }
}

public struct WorkoutContextProfile: Codable {
    public let id: String?
    public let name: String?
    public let contextKey: String?
    public let exerciseKey: String?
    public let tagId: String?
    public let setupProfileId: String?
    public let setupLabel: String?
    public let machineBrand: String?
    public let loadMode: LoadModeV2?
    public let linkStrategy: String?
    public let setupDetails: ExerciseSetupDetails?
    public let barWeightKg: Double?
    public let notes: String?
    public let createdAtIso: String?
    public let lastUsedAtIso: String?
    public let usageCount: Int

    public init(
        id: String? = nil,
        name: String? = nil,
        contextKey: String? = nil,
        exerciseKey: String? = nil,
        tagId: String? = nil,
        setupProfileId: String? = nil,
        setupLabel: String? = nil,
        machineBrand: String? = nil,
        loadMode: LoadModeV2? = nil,
        linkStrategy: String? = nil,
        setupDetails: ExerciseSetupDetails? = nil,
        barWeightKg: Double? = nil,
        notes: String? = nil,
        createdAtIso: String? = nil,
        lastUsedAtIso: String? = nil,
        usageCount: Int = 0
    ) {
        self.id = id
        self.name = name
        self.contextKey = contextKey
        self.exerciseKey = exerciseKey
        self.tagId = tagId ?? contextKey
        self.setupProfileId = setupProfileId
        self.setupLabel = setupLabel
        self.machineBrand = machineBrand
        self.loadMode = loadMode
        self.linkStrategy = linkStrategy
        self.setupDetails = setupDetails
        self.barWeightKg = barWeightKg
        self.notes = notes
        self.createdAtIso = createdAtIso
        self.lastUsedAtIso = lastUsedAtIso
        self.usageCount = usageCount
    }

    public func copy(
        id: String? = nil,
        name: String? = nil,
        contextKey: String? = nil,
        exerciseKey: String? = nil,
        tagId: String? = nil,
        setupProfileId: String? = nil,
        setupLabel: String? = nil,
        machineBrand: String? = nil,
        loadMode: LoadModeV2? = nil,
        linkStrategy: String? = nil,
        setupDetails: ExerciseSetupDetails? = nil,
        barWeightKg: Double? = nil,
        notes: String? = nil,
        createdAtIso: String? = nil,
        lastUsedAtIso: String? = nil,
        usageCount: Int? = nil
    ) -> WorkoutContextProfile {
        WorkoutContextProfile(
            id: id ?? self.id,
            name: name ?? self.name,
            contextKey: contextKey ?? self.contextKey,
            exerciseKey: exerciseKey ?? self.exerciseKey,
            tagId: tagId ?? self.tagId,
            setupProfileId: setupProfileId ?? self.setupProfileId,
            setupLabel: setupLabel ?? self.setupLabel,
            machineBrand: machineBrand ?? self.machineBrand,
            loadMode: loadMode ?? self.loadMode,
            linkStrategy: linkStrategy ?? self.linkStrategy,
            setupDetails: setupDetails ?? self.setupDetails,
            barWeightKg: barWeightKg ?? self.barWeightKg,
            notes: notes ?? self.notes,
            createdAtIso: createdAtIso ?? self.createdAtIso,
            lastUsedAtIso: lastUsedAtIso ?? self.lastUsedAtIso,
            usageCount: usageCount ?? self.usageCount
        )
    }
}

public struct SetTechniqueV2: Codable, Equatable {
    public let technique: String
    public let value: Double?
    public init(technique: String, value: Double? = nil) {
        self.technique = technique
        self.value = value
    }
}

public struct MuscleAdvance: Codable {
    public let muscleId: String
    public let muscleName: String
    public let currentSets: Double
    public let targetSets: Double
    public let deficitSets: Double
    public let targetSessionId: String
    public let targetSessionName: String
    public let discountProposals: [VolumeDiscountProposal]
    public init(muscleId: String, muscleName: String, currentSets: Double, targetSets: Double, deficitSets: Double, targetSessionId: String, targetSessionName: String, discountProposals: [VolumeDiscountProposal] = []) {
        self.muscleId = muscleId
        self.muscleName = muscleName
        self.currentSets = currentSets
        self.targetSets = targetSets
        self.deficitSets = deficitSets
        self.targetSessionId = targetSessionId
        self.targetSessionName = targetSessionName
        self.discountProposals = discountProposals
    }
}

public struct VolumeDiscountProposal: Codable {
    public let exerciseName: String
    public let exerciseId: String
    public let discountSets: Double
    public init(exerciseName: String, exerciseId: String, discountSets: Double) {
        self.exerciseName = exerciseName
        self.exerciseId = exerciseId
        self.discountSets = discountSets
    }
}

public struct SetDrain: Codable {
    public let cnsDrainPct: Double
    public let muscularDrainPct: Double
    public let spinalDrainPct: Double
    public init(cnsDrainPct: Double, muscularDrainPct: Double, spinalDrainPct: Double) {
        self.cnsDrainPct = cnsDrainPct
        self.muscularDrainPct = muscularDrainPct
        self.spinalDrainPct = spinalDrainPct
    }
}

public struct HomologatedPerformanceResult: Codable {
    public let contextKey: String
    public let globalKey: String
    public let loadMode: LoadModeV2
    public let unitMode: UnitModeV2
    public let plannedTarget: Double?
    public let actualValue: Double
    public let actualIntensity: Double?
    public let debt: Double
    public let failedSet: Bool
    public let reachedFailure: Bool
    public let amrapOverride: Bool
    public let techniques: [SetTechniqueV2]
    public let metricType: String
    public let metricValue: Double
    public let estimatedRm: Double?
    public let trm: Double?
    public let localPerformanceIndex: Double
    public let globalPerformanceIndex: Double
    public let contextPercentile: Double
    public let globalPercentile: Double
    public let contextEwma: Double
    public let contextStdDev: Double
    public let globalEwma: Double
    public let globalStdDev: Double
    public let isContextPr: Bool
    public let isGlobalPr: Bool
    public let historyColor: HistoryColorV2
    public let difficultySignal: DifficultySignalV2
    public let suggestedNextLoad: Double?
    public let suggestedTargetSeconds: Int?
    public let suggestionReason: String?
    public let augeEquivalentLoad: Double
    public let augeEquivalentReps: Int
    public let ermRangeMin: Double
    public let ermRangeMax: Double
    public let suggestedLoadMode: LoadModeV2?
    public init(contextKey: String, globalKey: String, loadMode: LoadModeV2, unitMode: UnitModeV2, plannedTarget: Double? = nil, actualValue: Double, actualIntensity: Double? = nil, debt: Double = 0.0, failedSet: Bool = false, reachedFailure: Bool = false, amrapOverride: Bool = false, techniques: [SetTechniqueV2] = [], metricType: String, metricValue: Double, estimatedRm: Double? = nil, trm: Double? = nil, localPerformanceIndex: Double, globalPerformanceIndex: Double, contextPercentile: Double, globalPercentile: Double, contextEwma: Double, contextStdDev: Double, globalEwma: Double, globalStdDev: Double, isContextPr: Bool, isGlobalPr: Bool, historyColor: HistoryColorV2, difficultySignal: DifficultySignalV2, suggestedNextLoad: Double? = nil, suggestedTargetSeconds: Int? = nil, suggestionReason: String? = nil, augeEquivalentLoad: Double, augeEquivalentReps: Int, ermRangeMin: Double = 0.0, ermRangeMax: Double = 0.0, suggestedLoadMode: LoadModeV2? = nil) {
        self.contextKey = contextKey
        self.globalKey = globalKey
        self.loadMode = loadMode
        self.unitMode = unitMode
        self.plannedTarget = plannedTarget
        self.actualValue = actualValue
        self.actualIntensity = actualIntensity
        self.debt = debt
        self.failedSet = failedSet
        self.reachedFailure = reachedFailure
        self.amrapOverride = amrapOverride
        self.techniques = techniques
        self.metricType = metricType
        self.metricValue = metricValue
        self.estimatedRm = estimatedRm
        self.trm = trm
        self.localPerformanceIndex = localPerformanceIndex
        self.globalPerformanceIndex = globalPerformanceIndex
        self.contextPercentile = contextPercentile
        self.globalPercentile = globalPercentile
        self.contextEwma = contextEwma
        self.contextStdDev = contextStdDev
        self.globalEwma = globalEwma
        self.globalStdDev = globalStdDev
        self.isContextPr = isContextPr
        self.isGlobalPr = isGlobalPr
        self.historyColor = historyColor
        self.difficultySignal = difficultySignal
        self.suggestedNextLoad = suggestedNextLoad
        self.suggestedTargetSeconds = suggestedTargetSeconds
        self.suggestionReason = suggestionReason
        self.augeEquivalentLoad = augeEquivalentLoad
        self.augeEquivalentReps = augeEquivalentReps
        self.ermRangeMin = ermRangeMin
        self.ermRangeMax = ermRangeMax
        self.suggestedLoadMode = suggestedLoadMode
    }
}

// ─── ExerciseSet (extended) ──────────────────────────────────────────────────

public struct ExerciseSet: Identifiable, Codable {
    public let id: String
    public let targetReps: Int?
    public let targetDuration: Int?
    public let targetRPE: Double?
    public let targetRIR: Int?
    public let intensityMode: IntensityMode?
    public let targetPercentageRM: Double?
    public let weight: Double?
    public let advancedTechnique: String?
    public let completedReps: Int?
    public let completedDuration: Int?
    public let completedRPE: Double?
    public let completedRIR: Int?
    public let isFailure: Bool
    public let isAmrap: Bool
    public let isCalibrator: Bool
    public let isIneffective: Bool
    public let isPartial: Bool
    public let partialReps: Int?
    public let isDropSet: Bool
    public let isRestPause: Bool
    public let machineBrand: String?
    public let isChangeOfPlans: Bool
    public let dropSets: [DropSetData]
    public let restPauses: [RestPauseData]
    public let performanceMode: PerformanceMode?
    public let technicalWeight: Double?
    public let consolidatedWeight: Double?
    public let attemptResult: AttemptResult?
    public let judgingLights: [Bool?]
    public let technicalQuality: Int?
    public let discomfortIds: [String]
    public let refereeNotes: String?
    public let loadModeV2: LoadModeV2?
    public let unitModeV2: UnitModeV2?
    public let plannedTargetV2: Double?
    public let tagId: String?
    public let setupId: String?
    public let contextKeyV2: String?
    public let contextProfileIdV3: String?
    public let defaultTagIdV3: String?
    public let defaultSetupProfileIdV3: String?
    public let timeProgressionStrategyV3: TimeProgressionStrategyV3
    public let leftTarget: UnilateralTarget?
    public let rightTarget: UnilateralTarget?
    public let restBetweenSides: Int?
    public let plannedIntensityTechniques: [PlannedTechnique]

    public init(
        id: String,
        targetReps: Int? = nil,
        targetDuration: Int? = nil,
        targetRPE: Double? = nil,
        targetRIR: Int? = nil,
        intensityMode: IntensityMode? = nil,
        targetPercentageRM: Double? = nil,
        weight: Double? = nil,
        advancedTechnique: String? = nil,
        completedReps: Int? = nil,
        completedDuration: Int? = nil,
        completedRPE: Double? = nil,
        completedRIR: Int? = nil,
        isFailure: Bool = false,
        isAmrap: Bool = false,
        isCalibrator: Bool = false,
        isIneffective: Bool = false,
        isPartial: Bool = false,
        partialReps: Int? = nil,
        isDropSet: Bool = false,
        isRestPause: Bool = false,
        machineBrand: String? = nil,
        isChangeOfPlans: Bool = false,
        dropSets: [DropSetData] = [],
        restPauses: [RestPauseData] = [],
        performanceMode: PerformanceMode? = nil,
        technicalWeight: Double? = nil,
        consolidatedWeight: Double? = nil,
        attemptResult: AttemptResult? = nil,
        judgingLights: [Bool?] = [],
        technicalQuality: Int? = nil,
        discomfortIds: [String] = [],
        refereeNotes: String? = nil,
        loadModeV2: LoadModeV2? = nil,
        unitModeV2: UnitModeV2? = nil,
        plannedTargetV2: Double? = nil,
        tagId: String? = nil,
        setupId: String? = nil,
        contextKeyV2: String? = nil,
        contextProfileIdV3: String? = nil,
        defaultTagIdV3: String? = nil,
        defaultSetupProfileIdV3: String? = nil,
        timeProgressionStrategyV3: TimeProgressionStrategyV3 = .LOAD_THEN_TIME,
        leftTarget: UnilateralTarget? = nil,
        rightTarget: UnilateralTarget? = nil,
        restBetweenSides: Int? = nil,
        plannedIntensityTechniques: [PlannedTechnique] = []
    ) {
        self.id = id
        self.targetReps = targetReps
        self.targetDuration = targetDuration
        self.targetRPE = targetRPE
        self.targetRIR = targetRIR
        self.intensityMode = intensityMode
        self.targetPercentageRM = targetPercentageRM
        self.weight = weight
        self.advancedTechnique = advancedTechnique
        self.completedReps = completedReps
        self.completedDuration = completedDuration
        self.completedRPE = completedRPE
        self.completedRIR = completedRIR
        self.isFailure = isFailure
        self.isAmrap = isAmrap
        self.isCalibrator = isCalibrator
        self.isIneffective = isIneffective
        self.isPartial = isPartial
        self.partialReps = partialReps
        self.isDropSet = isDropSet
        self.isRestPause = isRestPause
        self.machineBrand = machineBrand
        self.isChangeOfPlans = isChangeOfPlans
        self.dropSets = dropSets
        self.restPauses = restPauses
        self.performanceMode = performanceMode
        self.technicalWeight = technicalWeight
        self.consolidatedWeight = consolidatedWeight
        self.attemptResult = attemptResult
        self.judgingLights = judgingLights
        self.technicalQuality = technicalQuality
        self.discomfortIds = discomfortIds
        self.refereeNotes = refereeNotes
        self.loadModeV2 = loadModeV2
        self.unitModeV2 = unitModeV2
        self.plannedTargetV2 = plannedTargetV2
        self.tagId = tagId
        self.setupId = setupId
        self.contextKeyV2 = contextKeyV2
        self.contextProfileIdV3 = contextProfileIdV3
        self.defaultTagIdV3 = defaultTagIdV3
        self.defaultSetupProfileIdV3 = defaultSetupProfileIdV3
        self.timeProgressionStrategyV3 = timeProgressionStrategyV3
        self.leftTarget = leftTarget
        self.rightTarget = rightTarget
        self.restBetweenSides = restBetweenSides
        self.plannedIntensityTechniques = plannedIntensityTechniques
    }
}

// ─── Exercise (extended) ─────────────────────────────────────────────────────

public struct Exercise: Identifiable, Codable {
    public let id: String
    public let name: String
    public let exerciseDbId: String?
    public let exerciseId: String?
    public let canonicalExerciseId: String?
    public let exerciseFamilyId: String?
    public let relativeToCanonicalExerciseId: String?
    public let relationshipType: ExerciseRelationshipType?
    public let relationshipNotes: String?
    public let sets: [ExerciseSet]
    public let warmupSets: [WarmupSetDefinition]
    public let restTime: Int?
    public let isFavorite: Bool
    public let trainingMode: TrainingMode
    public let customUnit: String?
    public let reference1RM: Double?
    public let targetSessionGoal: String?
    public let isStarTarget: Bool
    public let trackHeartRate: Bool
    public let trackRom: Bool
    public let setupDetails: ExerciseSetupDetails?
    public let supersetId: String?
    public let supersetRestBetween: Int?
    public let supersetRestAfter: Int?
    public let supersetGroupRef: String?
    public let variantName: String?
    public let selectedExecutionOption: String?
    public let selectedMovementPattern: String?
    public let prFor1RM: PrReference?
    public let consolidatedWeight: ConsolidatedWeight?
    public let brandEquivalencies: [BrandEquivalency]
    public let isUnilateral: Bool
    public let unilateralMode: UnilateralMode
    public let unilateralSideOrder: UnilateralSideOrder
    public let unilateralIntensityMode: UnilateralIntensityMode
    public let restBetweenSidesSeconds: Int?
    public let isCalibratorAmrap: Bool
    public let goal1RM: Double?
    public let goalPr: PrReference?
    public let calculated1RM: Double?
    public let damageProfile: DamageProfile?
    public let isCompetitionLift: Bool
    public let setupCues: [String]
    public let executionCues: [String]
    public let contextProfilesV3: [WorkoutContextProfile]
    public let defaultContextProfileIdV3: String?
    public let mobilitySeries: [MobilitySeries]
    public let timeStrategy: TimeStrategy?
    public let targetDurationMinutes: Int?

    public init(
        id: String,
        name: String,
        exerciseDbId: String? = nil,
        exerciseId: String? = nil,
        canonicalExerciseId: String? = nil,
        exerciseFamilyId: String? = nil,
        relativeToCanonicalExerciseId: String? = nil,
        relationshipType: ExerciseRelationshipType? = nil,
        relationshipNotes: String? = nil,
        sets: [ExerciseSet] = [],
        warmupSets: [WarmupSetDefinition] = [],
        restTime: Int? = nil,
        isFavorite: Bool = false,
        trainingMode: TrainingMode = .REPS,
        customUnit: String? = nil,
        reference1RM: Double? = nil,
        targetSessionGoal: String? = nil,
        isStarTarget: Bool = false,
        trackHeartRate: Bool = false,
        trackRom: Bool = false,
        setupDetails: ExerciseSetupDetails? = nil,
        supersetId: String? = nil,
        supersetRestBetween: Int? = nil,
        supersetRestAfter: Int? = nil,
        supersetGroupRef: String? = nil,
        variantName: String? = nil,
        selectedExecutionOption: String? = nil,
        selectedMovementPattern: String? = nil,
        prFor1RM: PrReference? = nil,
        consolidatedWeight: ConsolidatedWeight? = nil,
        brandEquivalencies: [BrandEquivalency] = [],
        isUnilateral: Bool = false,
        unilateralMode: UnilateralMode = .BILATERAL,
        unilateralSideOrder: UnilateralSideOrder = .LEFT_RIGHT,
        unilateralIntensityMode: UnilateralIntensityMode = .SHARED,
        restBetweenSidesSeconds: Int? = nil,
        isCalibratorAmrap: Bool = false,
        goal1RM: Double? = nil,
        goalPr: PrReference? = nil,
        calculated1RM: Double? = nil,
        damageProfile: DamageProfile? = nil,
        isCompetitionLift: Bool = false,
        setupCues: [String] = [],
        executionCues: [String] = [],
        contextProfilesV3: [WorkoutContextProfile] = [],
        defaultContextProfileIdV3: String? = nil,
        mobilitySeries: [MobilitySeries] = [],
        timeStrategy: TimeStrategy? = nil,
        targetDurationMinutes: Int? = nil
    ) {
        self.id = id
        self.name = name
        self.exerciseDbId = exerciseDbId
        self.exerciseId = exerciseId
        self.canonicalExerciseId = canonicalExerciseId
        self.exerciseFamilyId = exerciseFamilyId
        self.relativeToCanonicalExerciseId = relativeToCanonicalExerciseId
        self.relationshipType = relationshipType
        self.relationshipNotes = relationshipNotes
        self.sets = sets
        self.warmupSets = warmupSets
        self.restTime = restTime
        self.isFavorite = isFavorite
        self.trainingMode = trainingMode
        self.customUnit = customUnit
        self.reference1RM = reference1RM
        self.targetSessionGoal = targetSessionGoal
        self.isStarTarget = isStarTarget
        self.trackHeartRate = trackHeartRate
        self.trackRom = trackRom
        self.setupDetails = setupDetails
        self.supersetId = supersetId
        self.supersetRestBetween = supersetRestBetween
        self.supersetRestAfter = supersetRestAfter
        self.supersetGroupRef = supersetGroupRef
        self.variantName = variantName
        self.selectedExecutionOption = selectedExecutionOption
        self.selectedMovementPattern = selectedMovementPattern
        self.prFor1RM = prFor1RM
        self.consolidatedWeight = consolidatedWeight
        self.brandEquivalencies = brandEquivalencies
        self.isUnilateral = isUnilateral
        self.unilateralMode = unilateralMode
        self.unilateralSideOrder = unilateralSideOrder
        self.unilateralIntensityMode = unilateralIntensityMode
        self.restBetweenSidesSeconds = restBetweenSidesSeconds
        self.isCalibratorAmrap = isCalibratorAmrap
        self.goal1RM = goal1RM
        self.goalPr = goalPr
        self.calculated1RM = calculated1RM
        self.damageProfile = damageProfile
        self.isCompetitionLift = isCompetitionLift
        self.setupCues = setupCues
        self.executionCues = executionCues
        self.contextProfilesV3 = contextProfilesV3
        self.defaultContextProfileIdV3 = defaultContextProfileIdV3
        self.mobilitySeries = mobilitySeries
        self.timeStrategy = timeStrategy
        self.targetDurationMinutes = targetDurationMinutes
    }
}

// ─── Exercise Extensions ─────────────────────────────────────────────────────

extension Exercise {
    public func isEffectivelyUnilateral() -> Bool {
        return unilateralMode != .BILATERAL || isUnilateral
    }

    public func supersetGroupRefOrLegacyId() -> String? {
        if let ref = supersetGroupRef, !ref.isEmpty { return ref }
        if let sid = supersetId, !sid.isEmpty { return sid }
        return nil
    }
}

// ─── CompletedSet ────────────────────────────────────────────────────────────

public struct CompletedSet: Identifiable, Codable {
    public let id: String
    public let weight: Double
    public let reps: Int
    public let timeSeconds: Int?
    public let rpe: Double?
    public let rir: Int?
    public let isFailure: Bool
    public let isFailedSet: Bool
    public let failureReason: String?
    public let isPartial: Bool
    public let partialReps: Int?
    public let dropSets: [DropSetData]
    public let restPauses: [RestPauseData]
    public let skipped: Bool
    public let superSetWithExerciseId: String?
    public let supersetId: String?
    public let supersetRoundIndex: Int?
    public let restAfterKind: String?
    public let isWarmup: Bool
    public let side: String?
    public let spinalScore: Double?
    public let performanceMode: PerformanceMode?
    public let actualIntensityMode: IntensityMode?
    public let actualIntensityValue: Double?
    public let debt: Double
    public let contextProfileId: String?
    public let tagId: String?
    public let subTagIds: [String]
    public let setupProfileId: String?
    public let machineBrand: String?
    public let homologatedResultV3: HomologatedPerformanceResult?
    public let rom: Int?
    public let assistedReps: Int?
    public let recordedPayloadV3: RecordedSetPayload?
    public let setOutcomeV2: SetOutcomeV2?

    public init(
        id: String,
        weight: Double = 0.0,
        reps: Int = 0,
        timeSeconds: Int? = nil,
        rpe: Double? = nil,
        rir: Int? = nil,
        isFailure: Bool = false,
        isFailedSet: Bool = false,
        failureReason: String? = nil,
        isPartial: Bool = false,
        partialReps: Int? = nil,
        dropSets: [DropSetData] = [],
        restPauses: [RestPauseData] = [],
        skipped: Bool = false,
        superSetWithExerciseId: String? = nil,
        supersetId: String? = nil,
        supersetRoundIndex: Int? = nil,
        restAfterKind: String? = nil,
        isWarmup: Bool = false,
        side: String? = nil,
        spinalScore: Double? = nil,
        performanceMode: PerformanceMode? = nil,
        actualIntensityMode: IntensityMode? = nil,
        actualIntensityValue: Double? = nil,
        debt: Double = 0.0,
        contextProfileId: String? = nil,
        tagId: String? = nil,
        subTagIds: [String] = [],
        setupProfileId: String? = nil,
        machineBrand: String? = nil,
        homologatedResultV3: HomologatedPerformanceResult? = nil,
        rom: Int? = nil,
        assistedReps: Int? = nil,
        recordedPayloadV3: RecordedSetPayload? = nil,
        setOutcomeV2: SetOutcomeV2? = nil
    ) {
        self.id = id
        self.weight = weight
        self.reps = reps
        self.timeSeconds = timeSeconds
        self.rpe = rpe
        self.rir = rir
        self.isFailure = isFailure
        self.isFailedSet = isFailedSet
        self.failureReason = failureReason
        self.isPartial = isPartial
        self.partialReps = partialReps
        self.dropSets = dropSets
        self.restPauses = restPauses
        self.skipped = skipped
        self.superSetWithExerciseId = superSetWithExerciseId
        self.supersetId = supersetId
        self.supersetRoundIndex = supersetRoundIndex
        self.restAfterKind = restAfterKind
        self.isWarmup = isWarmup
        self.side = side
        self.spinalScore = spinalScore
        self.performanceMode = performanceMode
        self.actualIntensityMode = actualIntensityMode
        self.actualIntensityValue = actualIntensityValue
        self.debt = debt
        self.contextProfileId = contextProfileId
        self.tagId = tagId
        self.subTagIds = subTagIds
        self.setupProfileId = setupProfileId
        self.machineBrand = machineBrand
        self.homologatedResultV3 = homologatedResultV3
        self.rom = rom
        self.assistedReps = assistedReps
        self.recordedPayloadV3 = recordedPayloadV3
        self.setOutcomeV2 = setOutcomeV2
    }
}

// ─── CompletedExercise ───────────────────────────────────────────────────────

public struct CompletedExercise: Identifiable, Codable {
    public let exerciseId: String
    public let exerciseName: String
    public let exerciseDbId: String?
    public let canonicalExerciseId: String?
    public let relativeToCanonicalExerciseId: String?
    public let sets: [CompletedSet]
    public let restTime: Int
    public let supersetId: String?
    public let supersetExerciseCount: Int
    public let supersetRounds: Int?
    public let supersetRestBetween: Int?
    public let supersetRestAfter: Int?

    public var id: String { exerciseId }

    public init(
        exerciseId: String,
        exerciseName: String,
        exerciseDbId: String? = nil,
        canonicalExerciseId: String? = nil,
        relativeToCanonicalExerciseId: String? = nil,
        sets: [CompletedSet] = [],
        restTime: Int = 90,
        supersetId: String? = nil,
        supersetExerciseCount: Int = 1,
        supersetRounds: Int? = nil,
        supersetRestBetween: Int? = nil,
        supersetRestAfter: Int? = nil
    ) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.exerciseDbId = exerciseDbId
        self.canonicalExerciseId = canonicalExerciseId
        self.relativeToCanonicalExerciseId = relativeToCanonicalExerciseId
        self.sets = sets
        self.restTime = restTime
        self.supersetId = supersetId
        self.supersetExerciseCount = supersetExerciseCount
        self.supersetRounds = supersetRounds
        self.supersetRestBetween = supersetRestBetween
        self.supersetRestAfter = supersetRestAfter
    }
}

// ─── SessionPart ─────────────────────────────────────────────────────────────

public struct SessionPart: Identifiable, Codable {
    public let id: String
    public let name: String
    public let exercises: [Exercise]
    public let color: String?
    public let targetDurationMinutes: Int?
    public init(id: String, name: String = "", exercises: [Exercise] = [], color: String? = nil, targetDurationMinutes: Int? = nil) {
        self.id = id
        self.name = name
        self.exercises = exercises
        self.color = color
        self.targetDurationMinutes = targetDurationMinutes
    }
}

// ─── Session (extended) ──────────────────────────────────────────────────────

public final class Session: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let exercises: [Exercise]
    public let parts: [SessionPart]
    public let targetDurationMinutes: Int?
    public let sessionB: Session?
    public let sessionC: Session?
    public let sessionD: Session?
    public let trainingBackup: Session?
    public let supersetGroups: [SupersetGroup]

    public init(
        id: String,
        name: String,
        description: String? = nil,
        exercises: [Exercise] = [],
        parts: [SessionPart] = [],
        targetDurationMinutes: Int? = nil,
        sessionB: Session? = nil,
        sessionC: Session? = nil,
        sessionD: Session? = nil,
        trainingBackup: Session? = nil,
        supersetGroups: [SupersetGroup] = []
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.exercises = exercises
        self.parts = parts
        self.targetDurationMinutes = targetDurationMinutes
        self.sessionB = sessionB
        self.sessionC = sessionC
        self.sessionD = sessionD
        self.trainingBackup = trainingBackup
        self.supersetGroups = supersetGroups
    }

    public func copy(
        exercises: [Exercise]? = nil,
        parts: [SessionPart]? = nil,
        sessionB: Session?? = nil,
        sessionC: Session?? = nil,
        sessionD: Session?? = nil,
        trainingBackup: Session?? = nil,
        supersetGroups: [SupersetGroup]? = nil
    ) -> Session {
        Session(
            id: id,
            name: name,
            description: description,
            exercises: exercises ?? self.exercises,
            parts: parts ?? self.parts,
            targetDurationMinutes: targetDurationMinutes,
            sessionB: sessionB ?? self.sessionB,
            sessionC: sessionC ?? self.sessionC,
            sessionD: sessionD ?? self.sessionD,
            trainingBackup: trainingBackup ?? self.trainingBackup,
            supersetGroups: supersetGroups ?? self.supersetGroups
        )
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, description, exercises, parts, targetDurationMinutes
        case sessionB, sessionC, sessionD, trainingBackup, supersetGroups
    }
}

extension Session {
    public func allExercises() -> [Exercise] {
        return exercises + parts.flatMap { $0.exercises }
    }
}

// ─── SessionLocation ─────────────────────────────────────────────────────────

public struct SessionLocation: Codable {
    public let macroIndex: Int
    public let mesoIndex: Int
    public let weekId: String
    public init(macroIndex: Int, mesoIndex: Int, weekId: String) {
        self.macroIndex = macroIndex
        self.mesoIndex = mesoIndex
        self.weekId = weekId
    }
}

// ─── WorkoutLog (extended) ───────────────────────────────────────────────────

public struct WorkoutLog: Codable {
    public let id: String
    public let programId: String
    public let sessionId: String
    public let sessionName: String
    public let date: String
    public let durationMinutes: Int
    public let completedExercises: [CompletedExercise]
    public let fatigueLevel: Int?
    public let discomforts: [String]
    public let notes: String?
    public let totalVolume: Double
    public let sessionStressScore: Double?
    public let weekId: String?
    public let macroIndex: Int?
    public let mesoIndex: Int?
    public let clarityRating: Int?
    public let environmentTags: [String]
    public let stillPresentDiscomfortIds: [String]
    public let planDeviations: [PlanDeviation]
    public let exerciseTags: [String: String]
    public let omittedExercises: [OmittedExercise]
    public let scheduledDate: String?
    public let actualDate: String?
    public let scheduleDeltaDays: Int?
    public let postExerciseReports: [ExerciseDiscomfortReport]

    public init(
        id: String,
        programId: String = "",
        sessionId: String = "",
        sessionName: String = "",
        date: String = "",
        durationMinutes: Int = 0,
        completedExercises: [CompletedExercise] = [],
        fatigueLevel: Int? = nil,
        discomforts: [String] = [],
        notes: String? = nil,
        totalVolume: Double = 0.0,
        sessionStressScore: Double? = nil,
        weekId: String? = nil,
        macroIndex: Int? = nil,
        mesoIndex: Int? = nil,
        clarityRating: Int? = nil,
        environmentTags: [String] = [],
        stillPresentDiscomfortIds: [String] = [],
        planDeviations: [PlanDeviation] = [],
        exerciseTags: [String: String] = [:],
        omittedExercises: [OmittedExercise] = [],
        scheduledDate: String? = nil,
        actualDate: String? = nil,
        scheduleDeltaDays: Int? = nil,
        postExerciseReports: [ExerciseDiscomfortReport] = []
    ) {
        self.id = id
        self.programId = programId
        self.sessionId = sessionId
        self.sessionName = sessionName
        self.date = date
        self.durationMinutes = durationMinutes
        self.completedExercises = completedExercises
        self.fatigueLevel = fatigueLevel
        self.discomforts = discomforts
        self.notes = notes
        self.totalVolume = totalVolume
        self.sessionStressScore = sessionStressScore
        self.weekId = weekId
        self.macroIndex = macroIndex
        self.mesoIndex = mesoIndex
        self.clarityRating = clarityRating
        self.environmentTags = environmentTags
        self.stillPresentDiscomfortIds = stillPresentDiscomfortIds
        self.planDeviations = planDeviations
        self.exerciseTags = exerciseTags
        self.omittedExercises = omittedExercises
        self.scheduledDate = scheduledDate
        self.actualDate = actualDate
        self.scheduleDeltaDays = scheduleDeltaDays
        self.postExerciseReports = postExerciseReports
    }
}

public struct OmittedExercise: Codable {
    public let exerciseId: String
    public let exerciseName: String
    public let reason: String?
    public init(exerciseId: String, exerciseName: String, reason: String? = nil) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.reason = reason
    }
}

// ─── TodaySessionItem ────────────────────────────────────────────────────────

public struct TodaySessionItem: Identifiable, Codable {
    public var id: String { session.id }
    public let session: Session
    public let program: Program
    public let location: SessionLocation
    public let isCompleted: Bool
    public let dayOfWeek: Int
    public let log: WorkoutLog?
    public let isOngoing: Bool

    public init(
        session: Session,
        program: Program,
        location: SessionLocation,
        isCompleted: Bool,
        dayOfWeek: Int,
        log: WorkoutLog? = nil,
        isOngoing: Bool = false
    ) {
        self.session = session
        self.program = program
        self.location = location
        self.isCompleted = isCompleted
        self.dayOfWeek = dayOfWeek
        self.log = log
        self.isOngoing = isOngoing
    }
}

// ─── SupersetGroup ───────────────────────────────────────────────────────────

public struct SupersetVisualPlacement: Codable, Equatable {
    public let partId: String?
    public let anchorExerciseId: String?
    public init(partId: String? = nil, anchorExerciseId: String? = nil) {
        self.partId = partId
        self.anchorExerciseId = anchorExerciseId
    }
}

public struct SupersetGroup: Codable, Equatable {
    public let id: String
    public let exerciseOrder: [String]
    public let restBetweenExercises: Int
    public let restAfterSuperset: Int
    public let rounds: Int?
    public let visualPlacement: SupersetVisualPlacement?
    public let roundRestBetweenExercises: [Int: Int]
    public let roundRestAfterSuperset: [Int: Int]
    public let isOptional: Bool
    public init(id: String, exerciseOrder: [String], restBetweenExercises: Int = 60, restAfterSuperset: Int = 120, rounds: Int? = nil, visualPlacement: SupersetVisualPlacement? = nil, roundRestBetweenExercises: [Int: Int] = [:], roundRestAfterSuperset: [Int: Int] = [:], isOptional: Bool = false) {
        self.id = id
        self.exerciseOrder = exerciseOrder
        self.restBetweenExercises = restBetweenExercises
        self.restAfterSuperset = restAfterSuperset
        self.rounds = rounds
        self.visualPlacement = visualPlacement
        self.roundRestBetweenExercises = roundRestBetweenExercises
        self.roundRestAfterSuperset = roundRestAfterSuperset
        self.isOptional = isOptional
    }
}
