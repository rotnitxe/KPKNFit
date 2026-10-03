import Foundation

// ─── Enums ────────────────────────────────────────────────────────────────────

public enum TrainingMode: String, Codable {
    case REPS, TIME, RM, CUSTOM, DISTANCE, SOLO_RPE, AMRAP
}

public enum TimeStrategy: String, Codable {
    case COUNTDOWN, CHRONOMETER, FREE
}

public enum DamageProfile: String, Codable {
    case STRETCH, SQUEEZE, NORMAL
}

public enum ExerciseRelationshipType: String, Codable {
    case VARIATION, ASSISTANCE, OVERLOAD, TECHNIQUE
}

public enum UnilateralMode: String, Codable {
    case BILATERAL, UNILATERAL_PAIRED, UNILATERAL_DIFFERENTIAL
}

public enum UnilateralSideOrder: String, Codable {
    case LEFT_RIGHT, RIGHT_LEFT
}

public enum UnilateralIntensityMode: String, Codable {
    case SHARED, INDEPENDENT
}

public enum TechniqueType: String, Codable {
    case DROP_SET, REST_PAUSE, PARTIALS, ISO_HOLD, NEGATIVES, CLUSTER_SET
}

public enum IntensityMode: String, Codable {
    case RPE, RIR, FAILURE, AMRAP, LOAD, SOLO_RM
}

public enum PerformanceMode: String, Codable {
    case TARGET, FAILURE, FAILED
}

public enum SessionBackgroundType: String, Codable {
    case COLOR, IMAGE
}

public enum LabelPosition: String, Codable {
    case BOTTOM_LEFT, CENTER, BOTTOM_CENTER
}

public enum AttemptResult: String, Codable {
    case GOOD, NO_LIFT, PENDING
}

// Redundant CompetitionTemplateType and CompetitionRecordMode removed (defined in CompetitionModels.swift)


// ─── Small Data Classes ────────────────────────────────────────────────────────

public struct SessionBackgroundStyle: Codable {
    public let blur: Float?
    public let brightness: Float?
    public init(blur: Float? = nil, brightness: Float? = nil) {
        self.blur = blur
        self.brightness = brightness
    }
}

public struct CoverFilters: Codable {
    public let contrast: Float
    public let saturation: Float
    public let brightness: Float
    public let grayscale: Float
    public let sepia: Float
    public let vignette: Float
    public init(contrast: Float = 1.0, saturation: Float = 1.0, brightness: Float = 1.0, grayscale: Float = 0.0, sepia: Float = 0.0, vignette: Float = 0.0) {
        self.contrast = contrast
        self.saturation = saturation
        self.brightness = brightness
        self.grayscale = grayscale
        self.sepia = sepia
        self.vignette = vignette
    }
}

public struct SupersetVisualPlacement: Codable, Equatable {
    public let partId: String?
    public let anchorExerciseId: String?
    public init(partId: String? = nil, anchorExerciseId: String? = nil) {
        self.partId = partId
        self.anchorExerciseId = anchorExerciseId
    }
}

public struct MicroProgramRule: Codable {
    public let id: String
    public let title: String
    public let description: String?
    public init(id: String, title: String, description: String? = nil) {
        self.id = id
        self.title = title
        self.description = description
    }
}

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

public struct PrReference: Codable {
    public let weight: Double
    public let reps: Int
    public init(weight: Double, reps: Int) {
        self.weight = weight
        self.reps = reps
    }
}

public struct ConsolidatedWeight: Codable {
    public let weightKg: Double
    public let reps: Int
    public init(weightKg: Double, reps: Int) {
        self.weightKg = weightKg
        self.reps = reps
    }
}

public struct BrandPr: Codable {
    public let weight: Double
    public let reps: Int
    public let e1rm: Double
    public init(weight: Double, reps: Int, e1rm: Double) {
        self.weight = weight
        self.reps = reps
        self.e1rm = e1rm
    }
}

public struct BrandEquivalency: Codable {
    public let brand: String
    public let pr: BrandPr?
    public init(brand: String, pr: BrandPr? = nil) {
        self.brand = brand
        self.pr = pr
    }
}

public struct ExerciseSetupDetails: Codable {
    public let seatPosition: String?
    public let pinPosition: String?
    public let equipmentNotes: String?
    public let barWeightKg: Double?
    public init(seatPosition: String? = nil, pinPosition: String? = nil, equipmentNotes: String? = nil, barWeightKg: Double? = nil) {
        self.seatPosition = seatPosition
        self.pinPosition = pinPosition
        self.equipmentNotes = equipmentNotes
        self.barWeightKg = barWeightKg
    }
}

public struct UnilateralTarget: Codable {
    public let weight: Double?
    public let targetReps: Int?
    public let targetDuration: Int?
    public let targetValue: Double?
    public let targetRPE: Double?
    public let targetRIR: Int?
    public let intensityMode: IntensityMode?
    public init(weight: Double? = nil, targetReps: Int? = nil, targetDuration: Int? = nil, targetValue: Double? = nil, targetRPE: Double? = nil, targetRIR: Int? = nil, intensityMode: IntensityMode? = nil) {
        self.weight = weight
        self.targetReps = targetReps
        self.targetDuration = targetDuration
        self.targetValue = targetValue
        self.targetRPE = targetRPE
        self.targetRIR = targetRIR
        self.intensityMode = intensityMode
    }
}

public struct PlannedTechnique: Codable {
    public let id: String
    public let type: TechniqueType
    public let params: [String: String]
    public init(id: String = "", type: TechniqueType, params: [String: String] = [:]) {
        self.id = id
        self.type = type
        self.params = params
    }
}

public struct VolumeDiscountProposal: Codable {
    public let exerciseId: String
    public let exerciseName: String
    public let currentRole: String
    public let discountSets: Double
    public let reason: String
    public init(exerciseId: String, exerciseName: String, currentRole: String, discountSets: Double, reason: String) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.currentRole = currentRole
        self.discountSets = discountSets
        self.reason = reason
    }
}

// ─── WarmupExercise ───────────────────────────────────────────────────────────

public struct WarmupExercise: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let category: String?
    public let duration: Int?
    public let sets: Int?
    public let reps: String?
    public init(id: String, name: String, description: String? = nil, category: String? = nil, duration: Int? = nil, sets: Int? = nil, reps: String? = nil) {
        self.id = id
        self.name = name
        self.description = description
        self.category = category
        self.duration = duration
        self.sets = sets
        self.reps = reps
    }
}

// ─── SessionMicroProgram ──────────────────────────────────────────────────────

public struct SessionMicroProgram: Codable {
    public let enabled: Bool
    public let everyXCycles: Int
    public let isMainInCycle: Bool
    public let rules: [MicroProgramRule]
    public init(enabled: Bool = false, everyXCycles: Int = 1, isMainInCycle: Bool = true, rules: [MicroProgramRule] = []) {
        self.enabled = enabled
        self.everyXCycles = everyXCycles
        self.isMainInCycle = isMainInCycle
        self.rules = rules
    }
}

// ─── MeetResults ──────────────────────────────────────────────────────────────

public struct MeetResults: Codable {
    public let placement: String?
    public let total: Double?
    public let dots: Double?
    public let awards: [String]
    public init(placement: String? = nil, total: Double? = nil, dots: Double? = nil, awards: [String] = []) {
        self.placement = placement
        self.total = total
        self.dots = dots
        self.awards = awards
    }
}

// ─── CompetitionDetails ───────────────────────────────────────────────────────

public struct CompetitionDetails: Codable {
    public let competitionDate: String?
    public let startTime: String?
    public let location: String?
    public let federation: String?
    public let category: String?
    public let division: String?
    public let equipment: String?
    public let targetBodyweightKg: Double?
    public let weighInDate: String?
    public let weighInTime: String?
    public let reminderOneWeekEnabled: Bool
    public let reminder48hEnabled: Bool
    public let reminderStartEnabled: Bool
    public let strategyNotes: String?
    public init(competitionDate: String? = nil, startTime: String? = nil, location: String? = nil, federation: String? = nil, category: String? = nil, division: String? = nil, equipment: String? = nil, targetBodyweightKg: Double? = nil, weighInDate: String? = nil, weighInTime: String? = nil, reminderOneWeekEnabled: Bool = true, reminder48hEnabled: Bool = true, reminderStartEnabled: Bool = false, strategyNotes: String? = nil) {
        self.competitionDate = competitionDate
        self.startTime = startTime
        self.location = location
        self.federation = federation
        self.category = category
        self.division = division
        self.equipment = equipment
        self.targetBodyweightKg = targetBodyweightKg
        self.weighInDate = weighInDate
        self.weighInTime = weighInTime
        self.reminderOneWeekEnabled = reminderOneWeekEnabled
        self.reminder48hEnabled = reminder48hEnabled
        self.reminderStartEnabled = reminderStartEnabled
        self.strategyNotes = strategyNotes
    }
}

// ─── TrainingBackup ───────────────────────────────────────────────────────────

public struct TrainingBackup: Codable {
    public let exercises: [Exercise]
    public let parts: [SessionPart]
    public let warmup: [WarmupExercise]
    public let savedAtMs: Int64
    public init(exercises: [Exercise] = [], parts: [SessionPart] = [], warmup: [WarmupExercise] = [], savedAtMs: Int64 = 0) {
        self.exercises = exercises
        self.parts = parts
        self.warmup = warmup
        self.savedAtMs = savedAtMs
    }
}

// ─── VolumeAdvance / MuscleAdvance ────────────────────────────────────────────

public struct VolumeAdvance: Codable {
    public let id: String
    public let muscleAdvances: [MuscleAdvance]
    public let acceptedAtMs: Int64?
    public init(id: String, muscleAdvances: [MuscleAdvance] = [], acceptedAtMs: Int64? = nil) {
        self.id = id
        self.muscleAdvances = muscleAdvances
        self.acceptedAtMs = acceptedAtMs
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

// ─── SessionPart ──────────────────────────────────────────────────────────────

public struct SessionPart: Identifiable, Codable {
    public let id: String
    public let name: String
    public let exercises: [Exercise]
    public let color: String?
    public let targetDurationMinutes: Int?
    public let opaqueFields: [String: JSONValue]
    public init(id: String, name: String, exercises: [Exercise] = [], color: String? = nil, targetDurationMinutes: Int? = nil, opaqueFields: [String: JSONValue] = [:]) {
        self.id = id
        self.name = name
        self.exercises = exercises
        self.color = color
        self.targetDurationMinutes = targetDurationMinutes
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = ["id", "name", "exercises", "color", "targetDurationMinutes"]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        id = try c.decode(String.self, "id")
        name = try c.decode(String.self, "name")
        exercises = try c.decode([Exercise].self, "exercises", default: [])
        color = try c.decodeOptional(String.self, "color")
        targetDurationMinutes = try c.decodeOptional(Int.self, "targetDurationMinutes")
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(id, forKey: DynamicCodingKey("id"))
        try c.encode(name, forKey: DynamicCodingKey("name"))
        try c.encode(exercises, forKey: DynamicCodingKey("exercises"))
        try c.encodeIfPresent(color, forKey: DynamicCodingKey("color"))
        try c.encodeIfPresent(targetDurationMinutes, forKey: DynamicCodingKey("targetDurationMinutes"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }
}

// ─── SessionBackground ────────────────────────────────────────────────────────

public struct SessionBackground: Codable {
    public let type: SessionBackgroundType
    public let value: String
    public let style: SessionBackgroundStyle?
    public init(type: SessionBackgroundType = .COLOR, value: String, style: SessionBackgroundStyle? = nil) {
        self.type = type
        self.value = value
        self.style = style
    }
}

// ─── CoverStyle ───────────────────────────────────────────────────────────────

public struct CoverStyle: Codable {
    public let filters: CoverFilters?
    public let enableMotion: Bool
    public let labelPosition: LabelPosition
    public init(filters: CoverFilters? = nil, enableMotion: Bool = false, labelPosition: LabelPosition = .BOTTOM_LEFT) {
        self.filters = filters
        self.enableMotion = enableMotion
        self.labelPosition = labelPosition
    }
}

// ─── SupersetGroup ────────────────────────────────────────────────────────────

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

// ─── WarmupSetDefinition ──────────────────────────────────────────────────────

public struct WarmupSetDefinition: Identifiable, Codable {
    public let id: String
    public let percentageOfWorkingWeight: Double
    public let targetReps: Int
    public let matchRPE: Double?
    public let restBetween: Int?
    public init(id: String, percentageOfWorkingWeight: Double, targetReps: Int, matchRPE: Double? = nil, restBetween: Int? = nil) {
        self.id = id
        self.percentageOfWorkingWeight = percentageOfWorkingWeight
        self.targetReps = targetReps
        self.matchRPE = matchRPE
        self.restBetween = restBetween
    }
}

// ─── MobilitySeries ───────────────────────────────────────────────────────────

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

// ─── ExerciseSet ──────────────────────────────────────────────────────────────

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
    public let completedRIR: Double?
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
    public let opaqueFields: [String: JSONValue]
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
        completedRIR: Double? = nil,
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
        plannedIntensityTechniques: [PlannedTechnique] = [],
        opaqueFields: [String: JSONValue] = [:]
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
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = [
        "id", "targetReps", "targetDuration", "targetRPE", "targetRIR", "intensityMode", "targetPercentageRM", "weight",
        "advancedTechnique", "completedReps", "completedDuration", "completedRPE", "completedRIR", "isFailure", "isAmrap",
        "isCalibrator", "isIneffective", "isPartial", "partialReps", "isDropSet", "isRestPause", "machineBrand",
        "isChangeOfPlans", "dropSets", "restPauses", "performanceMode", "technicalWeight", "consolidatedWeight",
        "attemptResult", "judgingLights", "technicalQuality", "discomfortIds", "refereeNotes", "loadModeV2", "unitModeV2",
        "plannedTargetV2", "tagId", "setupId", "contextKeyV2", "contextProfileIdV3", "defaultTagIdV3",
        "defaultSetupProfileIdV3", "timeProgressionStrategyV3", "leftTarget", "rightTarget", "restBetweenSides",
        "plannedIntensityTechniques",
    ]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        id = try c.decode(String.self, "id")
        targetReps = try c.decodeOptional(Int.self, "targetReps")
        targetDuration = try c.decodeOptional(Int.self, "targetDuration")
        targetRPE = try c.decodeOptional(Double.self, "targetRPE")
        targetRIR = try c.decodeOptional(Int.self, "targetRIR")
        intensityMode = try c.decodeOptional(IntensityMode.self, "intensityMode")
        targetPercentageRM = try c.decodeOptional(Double.self, "targetPercentageRM")
        weight = try c.decodeOptional(Double.self, "weight")
        advancedTechnique = try c.decodeOptional(String.self, "advancedTechnique")
        completedReps = try c.decodeOptional(Int.self, "completedReps")
        completedDuration = try c.decodeOptional(Int.self, "completedDuration")
        completedRPE = try c.decodeOptional(Double.self, "completedRPE")
        completedRIR = try c.decodeOptional(Int.self, "completedRIR")
        isFailure = try c.decode(Bool.self, "isFailure", default: false)
        isAmrap = try c.decode(Bool.self, "isAmrap", default: false)
        isCalibrator = try c.decode(Bool.self, "isCalibrator", default: false)
        isIneffective = try c.decode(Bool.self, "isIneffective", default: false)
        isPartial = try c.decode(Bool.self, "isPartial", default: false)
        partialReps = try c.decodeOptional(Int.self, "partialReps")
        isDropSet = try c.decode(Bool.self, "isDropSet", default: false)
        isRestPause = try c.decode(Bool.self, "isRestPause", default: false)
        machineBrand = try c.decodeOptional(String.self, "machineBrand")
        isChangeOfPlans = try c.decode(Bool.self, "isChangeOfPlans", default: false)
        dropSets = try c.decode([DropSetData].self, "dropSets", default: [])
        restPauses = try c.decode([RestPauseData].self, "restPauses", default: [])
        performanceMode = try c.decodeOptional(PerformanceMode.self, "performanceMode")
        technicalWeight = try c.decodeOptional(Double.self, "technicalWeight")
        consolidatedWeight = try c.decodeOptional(Double.self, "consolidatedWeight")
        attemptResult = try c.decodeOptional(AttemptResult.self, "attemptResult")
        judgingLights = try c.decode([Bool?].self, "judgingLights", default: [])
        technicalQuality = try c.decodeOptional(Int.self, "technicalQuality")
        discomfortIds = try c.decode([String].self, "discomfortIds", default: [])
        refereeNotes = try c.decodeOptional(String.self, "refereeNotes")
        loadModeV2 = try c.decodeOptional(LoadModeV2.self, "loadModeV2")
        unitModeV2 = try c.decodeOptional(UnitModeV2.self, "unitModeV2")
        plannedTargetV2 = try c.decodeOptional(Double.self, "plannedTargetV2")
        tagId = try c.decodeOptional(String.self, "tagId")
        setupId = try c.decodeOptional(String.self, "setupId")
        contextKeyV2 = try c.decodeOptional(String.self, "contextKeyV2")
        contextProfileIdV3 = try c.decodeOptional(String.self, "contextProfileIdV3")
        defaultTagIdV3 = try c.decodeOptional(String.self, "defaultTagIdV3")
        defaultSetupProfileIdV3 = try c.decodeOptional(String.self, "defaultSetupProfileIdV3")
        timeProgressionStrategyV3 = try c.decode(TimeProgressionStrategyV3.self, "timeProgressionStrategyV3", default: .LOAD_THEN_TIME)
        leftTarget = try c.decodeOptional(UnilateralTarget.self, "leftTarget")
        rightTarget = try c.decodeOptional(UnilateralTarget.self, "rightTarget")
        restBetweenSides = try c.decodeOptional(Int.self, "restBetweenSides")
        plannedIntensityTechniques = try c.decode([PlannedTechnique].self, "plannedIntensityTechniques", default: [])
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(id, forKey: DynamicCodingKey("id"))
        try c.encodeIfPresent(targetReps, forKey: DynamicCodingKey("targetReps"))
        try c.encodeIfPresent(targetDuration, forKey: DynamicCodingKey("targetDuration"))
        try c.encodeIfPresent(targetRPE, forKey: DynamicCodingKey("targetRPE"))
        try c.encodeIfPresent(targetRIR, forKey: DynamicCodingKey("targetRIR"))
        try c.encodeIfPresent(intensityMode, forKey: DynamicCodingKey("intensityMode"))
        try c.encodeIfPresent(targetPercentageRM, forKey: DynamicCodingKey("targetPercentageRM"))
        try c.encodeIfPresent(weight, forKey: DynamicCodingKey("weight"))
        try c.encodeIfPresent(advancedTechnique, forKey: DynamicCodingKey("advancedTechnique"))
        try c.encodeIfPresent(completedReps, forKey: DynamicCodingKey("completedReps"))
        try c.encodeIfPresent(completedDuration, forKey: DynamicCodingKey("completedDuration"))
        try c.encodeIfPresent(completedRPE, forKey: DynamicCodingKey("completedRPE"))
        try c.encodeIfPresent(completedRIR, forKey: DynamicCodingKey("completedRIR"))
        try c.encode(isFailure, forKey: DynamicCodingKey("isFailure"))
        try c.encode(isAmrap, forKey: DynamicCodingKey("isAmrap"))
        try c.encode(isCalibrator, forKey: DynamicCodingKey("isCalibrator"))
        try c.encode(isIneffective, forKey: DynamicCodingKey("isIneffective"))
        try c.encode(isPartial, forKey: DynamicCodingKey("isPartial"))
        try c.encodeIfPresent(partialReps, forKey: DynamicCodingKey("partialReps"))
        try c.encode(isDropSet, forKey: DynamicCodingKey("isDropSet"))
        try c.encode(isRestPause, forKey: DynamicCodingKey("isRestPause"))
        try c.encodeIfPresent(machineBrand, forKey: DynamicCodingKey("machineBrand"))
        try c.encode(isChangeOfPlans, forKey: DynamicCodingKey("isChangeOfPlans"))
        try c.encode(dropSets, forKey: DynamicCodingKey("dropSets"))
        try c.encode(restPauses, forKey: DynamicCodingKey("restPauses"))
        try c.encodeIfPresent(performanceMode, forKey: DynamicCodingKey("performanceMode"))
        try c.encodeIfPresent(technicalWeight, forKey: DynamicCodingKey("technicalWeight"))
        try c.encodeIfPresent(consolidatedWeight, forKey: DynamicCodingKey("consolidatedWeight"))
        try c.encodeIfPresent(attemptResult, forKey: DynamicCodingKey("attemptResult"))
        try c.encode(judgingLights, forKey: DynamicCodingKey("judgingLights"))
        try c.encodeIfPresent(technicalQuality, forKey: DynamicCodingKey("technicalQuality"))
        try c.encode(discomfortIds, forKey: DynamicCodingKey("discomfortIds"))
        try c.encodeIfPresent(refereeNotes, forKey: DynamicCodingKey("refereeNotes"))
        try c.encodeIfPresent(loadModeV2, forKey: DynamicCodingKey("loadModeV2"))
        try c.encodeIfPresent(unitModeV2, forKey: DynamicCodingKey("unitModeV2"))
        try c.encodeIfPresent(plannedTargetV2, forKey: DynamicCodingKey("plannedTargetV2"))
        try c.encodeIfPresent(tagId, forKey: DynamicCodingKey("tagId"))
        try c.encodeIfPresent(setupId, forKey: DynamicCodingKey("setupId"))
        try c.encodeIfPresent(contextKeyV2, forKey: DynamicCodingKey("contextKeyV2"))
        try c.encodeIfPresent(contextProfileIdV3, forKey: DynamicCodingKey("contextProfileIdV3"))
        try c.encodeIfPresent(defaultTagIdV3, forKey: DynamicCodingKey("defaultTagIdV3"))
        try c.encodeIfPresent(defaultSetupProfileIdV3, forKey: DynamicCodingKey("defaultSetupProfileIdV3"))
        try c.encode(timeProgressionStrategyV3, forKey: DynamicCodingKey("timeProgressionStrategyV3"))
        try c.encodeIfPresent(leftTarget, forKey: DynamicCodingKey("leftTarget"))
        try c.encodeIfPresent(rightTarget, forKey: DynamicCodingKey("rightTarget"))
        try c.encodeIfPresent(restBetweenSides, forKey: DynamicCodingKey("restBetweenSides"))
        try c.encode(plannedIntensityTechniques, forKey: DynamicCodingKey("plannedIntensityTechniques"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }
}

// ─── Exercise ─────────────────────────────────────────────────────────────────

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
    public let opaqueFields: [String: JSONValue]
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
        targetDurationMinutes: Int? = nil,
        opaqueFields: [String: JSONValue] = [:]
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
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = [
        "id", "name", "exerciseDbId", "exerciseId", "canonicalExerciseId", "exerciseFamilyId", "relativeToCanonicalExerciseId",
        "relationshipType", "relationshipNotes", "sets", "warmupSets", "restTime", "isFavorite", "trainingMode", "customUnit",
        "reference1RM", "targetSessionGoal", "isStarTarget", "trackHeartRate", "trackRom", "setupDetails", "supersetId",
        "supersetRestBetween", "supersetRestAfter", "supersetGroupRef", "variantName", "selectedExecutionOption",
        "selectedMovementPattern", "prFor1RM", "consolidatedWeight", "brandEquivalencies", "isUnilateral", "unilateralMode",
        "unilateralSideOrder", "unilateralIntensityMode", "restBetweenSidesSeconds", "isCalibratorAmrap", "goal1RM", "goalPr",
        "calculated1RM", "damageProfile", "isCompetitionLift", "setupCues", "executionCues", "contextProfilesV3",
        "defaultContextProfileIdV3", "mobilitySeries", "timeStrategy", "targetDurationMinutes",
    ]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        id = try c.decode(String.self, "id")
        name = try c.decode(String.self, "name")
        exerciseDbId = try c.decodeOptional(String.self, "exerciseDbId")
        exerciseId = try c.decodeOptional(String.self, "exerciseId")
        canonicalExerciseId = try c.decodeOptional(String.self, "canonicalExerciseId")
        exerciseFamilyId = try c.decodeOptional(String.self, "exerciseFamilyId")
        relativeToCanonicalExerciseId = try c.decodeOptional(String.self, "relativeToCanonicalExerciseId")
        relationshipType = try c.decodeOptional(ExerciseRelationshipType.self, "relationshipType")
        relationshipNotes = try c.decodeOptional(String.self, "relationshipNotes")
        sets = try c.decode([ExerciseSet].self, "sets", default: [])
        warmupSets = try c.decode([WarmupSetDefinition].self, "warmupSets", default: [])
        restTime = try c.decodeOptional(Int.self, "restTime")
        isFavorite = try c.decode(Bool.self, "isFavorite", default: false)
        trainingMode = try c.decode(TrainingMode.self, "trainingMode", default: .REPS)
        customUnit = try c.decodeOptional(String.self, "customUnit")
        reference1RM = try c.decodeOptional(Double.self, "reference1RM")
        targetSessionGoal = try c.decodeOptional(String.self, "targetSessionGoal")
        isStarTarget = try c.decode(Bool.self, "isStarTarget", default: false)
        trackHeartRate = try c.decode(Bool.self, "trackHeartRate", default: false)
        trackRom = try c.decode(Bool.self, "trackRom", default: false)
        setupDetails = try c.decodeOptional(ExerciseSetupDetails.self, "setupDetails")
        supersetId = try c.decodeOptional(String.self, "supersetId")
        supersetRestBetween = try c.decodeOptional(Int.self, "supersetRestBetween")
        supersetRestAfter = try c.decodeOptional(Int.self, "supersetRestAfter")
        supersetGroupRef = try c.decodeOptional(String.self, "supersetGroupRef")
        variantName = try c.decodeOptional(String.self, "variantName")
        selectedExecutionOption = try c.decodeOptional(String.self, "selectedExecutionOption")
        selectedMovementPattern = try c.decodeOptional(String.self, "selectedMovementPattern")
        prFor1RM = try c.decodeOptional(PrReference.self, "prFor1RM")
        consolidatedWeight = try c.decodeOptional(ConsolidatedWeight.self, "consolidatedWeight")
        brandEquivalencies = try c.decode([BrandEquivalency].self, "brandEquivalencies", default: [])
        isUnilateral = try c.decode(Bool.self, "isUnilateral", default: false)
        unilateralMode = try c.decode(UnilateralMode.self, "unilateralMode", default: .BILATERAL)
        unilateralSideOrder = try c.decode(UnilateralSideOrder.self, "unilateralSideOrder", default: .LEFT_RIGHT)
        unilateralIntensityMode = try c.decode(UnilateralIntensityMode.self, "unilateralIntensityMode", default: .SHARED)
        restBetweenSidesSeconds = try c.decodeOptional(Int.self, "restBetweenSidesSeconds")
        isCalibratorAmrap = try c.decode(Bool.self, "isCalibratorAmrap", default: false)
        goal1RM = try c.decodeOptional(Double.self, "goal1RM")
        goalPr = try c.decodeOptional(PrReference.self, "goalPr")
        calculated1RM = try c.decodeOptional(Double.self, "calculated1RM")
        damageProfile = try c.decodeOptional(DamageProfile.self, "damageProfile")
        isCompetitionLift = try c.decode(Bool.self, "isCompetitionLift", default: false)
        setupCues = try c.decode([String].self, "setupCues", default: [])
        executionCues = try c.decode([String].self, "executionCues", default: [])
        contextProfilesV3 = try c.decode([WorkoutContextProfile].self, "contextProfilesV3", default: [])
        defaultContextProfileIdV3 = try c.decodeOptional(String.self, "defaultContextProfileIdV3")
        mobilitySeries = try c.decode([MobilitySeries].self, "mobilitySeries", default: [])
        timeStrategy = try c.decodeOptional(TimeStrategy.self, "timeStrategy")
        targetDurationMinutes = try c.decodeOptional(Int.self, "targetDurationMinutes")
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(id, forKey: DynamicCodingKey("id"))
        try c.encode(name, forKey: DynamicCodingKey("name"))
        try c.encodeIfPresent(exerciseDbId, forKey: DynamicCodingKey("exerciseDbId"))
        try c.encodeIfPresent(exerciseId, forKey: DynamicCodingKey("exerciseId"))
        try c.encodeIfPresent(canonicalExerciseId, forKey: DynamicCodingKey("canonicalExerciseId"))
        try c.encodeIfPresent(exerciseFamilyId, forKey: DynamicCodingKey("exerciseFamilyId"))
        try c.encodeIfPresent(relativeToCanonicalExerciseId, forKey: DynamicCodingKey("relativeToCanonicalExerciseId"))
        try c.encodeIfPresent(relationshipType, forKey: DynamicCodingKey("relationshipType"))
        try c.encodeIfPresent(relationshipNotes, forKey: DynamicCodingKey("relationshipNotes"))
        try c.encode(sets, forKey: DynamicCodingKey("sets"))
        try c.encode(warmupSets, forKey: DynamicCodingKey("warmupSets"))
        try c.encodeIfPresent(restTime, forKey: DynamicCodingKey("restTime"))
        try c.encode(isFavorite, forKey: DynamicCodingKey("isFavorite"))
        try c.encode(trainingMode, forKey: DynamicCodingKey("trainingMode"))
        try c.encodeIfPresent(customUnit, forKey: DynamicCodingKey("customUnit"))
        try c.encodeIfPresent(reference1RM, forKey: DynamicCodingKey("reference1RM"))
        try c.encodeIfPresent(targetSessionGoal, forKey: DynamicCodingKey("targetSessionGoal"))
        try c.encode(isStarTarget, forKey: DynamicCodingKey("isStarTarget"))
        try c.encode(trackHeartRate, forKey: DynamicCodingKey("trackHeartRate"))
        try c.encode(trackRom, forKey: DynamicCodingKey("trackRom"))
        try c.encodeIfPresent(setupDetails, forKey: DynamicCodingKey("setupDetails"))
        try c.encodeIfPresent(supersetId, forKey: DynamicCodingKey("supersetId"))
        try c.encodeIfPresent(supersetRestBetween, forKey: DynamicCodingKey("supersetRestBetween"))
        try c.encodeIfPresent(supersetRestAfter, forKey: DynamicCodingKey("supersetRestAfter"))
        try c.encodeIfPresent(supersetGroupRef, forKey: DynamicCodingKey("supersetGroupRef"))
        try c.encodeIfPresent(variantName, forKey: DynamicCodingKey("variantName"))
        try c.encodeIfPresent(selectedExecutionOption, forKey: DynamicCodingKey("selectedExecutionOption"))
        try c.encodeIfPresent(selectedMovementPattern, forKey: DynamicCodingKey("selectedMovementPattern"))
        try c.encodeIfPresent(prFor1RM, forKey: DynamicCodingKey("prFor1RM"))
        try c.encodeIfPresent(consolidatedWeight, forKey: DynamicCodingKey("consolidatedWeight"))
        try c.encode(brandEquivalencies, forKey: DynamicCodingKey("brandEquivalencies"))
        try c.encode(isUnilateral, forKey: DynamicCodingKey("isUnilateral"))
        try c.encode(unilateralMode, forKey: DynamicCodingKey("unilateralMode"))
        try c.encode(unilateralSideOrder, forKey: DynamicCodingKey("unilateralSideOrder"))
        try c.encode(unilateralIntensityMode, forKey: DynamicCodingKey("unilateralIntensityMode"))
        try c.encodeIfPresent(restBetweenSidesSeconds, forKey: DynamicCodingKey("restBetweenSidesSeconds"))
        try c.encode(isCalibratorAmrap, forKey: DynamicCodingKey("isCalibratorAmrap"))
        try c.encodeIfPresent(goal1RM, forKey: DynamicCodingKey("goal1RM"))
        try c.encodeIfPresent(goalPr, forKey: DynamicCodingKey("goalPr"))
        try c.encodeIfPresent(calculated1RM, forKey: DynamicCodingKey("calculated1RM"))
        try c.encodeIfPresent(damageProfile, forKey: DynamicCodingKey("damageProfile"))
        try c.encode(isCompetitionLift, forKey: DynamicCodingKey("isCompetitionLift"))
        try c.encode(setupCues, forKey: DynamicCodingKey("setupCues"))
        try c.encode(executionCues, forKey: DynamicCodingKey("executionCues"))
        try c.encode(contextProfilesV3, forKey: DynamicCodingKey("contextProfilesV3"))
        try c.encodeIfPresent(defaultContextProfileIdV3, forKey: DynamicCodingKey("defaultContextProfileIdV3"))
        try c.encode(mobilitySeries, forKey: DynamicCodingKey("mobilitySeries"))
        try c.encodeIfPresent(timeStrategy, forKey: DynamicCodingKey("timeStrategy"))
        try c.encodeIfPresent(targetDurationMinutes, forKey: DynamicCodingKey("targetDurationMinutes"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }
}

// ─── Session ──────────────────────────────────────────────────────────────────

public final class Session: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let exercises: [Exercise]
    public let warmup: [WarmupExercise]
    public let parts: [SessionPart]
    public let background: SessionBackground?
    public let coverStyle: CoverStyle?
    public let dayOfWeek: Int?
    public let scheduleLabel: String?
    public let assignedDays: [Int]
    public let sessionB: Session?
    public let sessionC: Session?
    public let sessionD: Session?
    public let isMeetDay: Bool
    public let isCompetitionSession: Bool
    public let isMainSession: Bool
    public let focus: String?
    public let microProgram: SessionMicroProgram?
    public let meetBodyweight: Double?
    public let meetResults: MeetResults?
    public let competitionDetails: CompetitionDetails?
    public let competitionRecordId: String?
    public let competitionKeyDateId: String?
    public let competitionSportType: CompetitionTemplateType?
    public let competitionRecordMode: CompetitionRecordMode?
    public let trainingBackup: TrainingBackup?
    public let supersetGroups: [SupersetGroup]
    public let lastModifiedAtMs: Int64
    public let targetDurationMinutes: Int?
    public let volumeAdvances: [VolumeAdvance]
    public let opaqueFields: [String: JSONValue]
    public init(
        id: String,
        name: String,
        description: String? = nil,
        exercises: [Exercise] = [],
        warmup: [WarmupExercise] = [],
        parts: [SessionPart] = [],
        background: SessionBackground? = nil,
        coverStyle: CoverStyle? = nil,
        dayOfWeek: Int? = nil,
        scheduleLabel: String? = nil,
        assignedDays: [Int] = [],
        sessionB: Session? = nil,
        sessionC: Session? = nil,
        sessionD: Session? = nil,
        isMeetDay: Bool = false,
        isCompetitionSession: Bool = false,
        isMainSession: Bool = false,
        focus: String? = nil,
        microProgram: SessionMicroProgram? = nil,
        meetBodyweight: Double? = nil,
        meetResults: MeetResults? = nil,
        competitionDetails: CompetitionDetails? = nil,
        competitionRecordId: String? = nil,
        competitionKeyDateId: String? = nil,
        competitionSportType: CompetitionTemplateType? = nil,
        competitionRecordMode: CompetitionRecordMode? = nil,
        trainingBackup: TrainingBackup? = nil,
        supersetGroups: [SupersetGroup] = [],
        lastModifiedAtMs: Int64 = 0,
        targetDurationMinutes: Int? = nil,
        volumeAdvances: [VolumeAdvance] = [],
        opaqueFields: [String: JSONValue] = [:]
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.exercises = exercises
        self.warmup = warmup
        self.parts = parts
        self.background = background
        self.coverStyle = coverStyle
        self.dayOfWeek = dayOfWeek
        self.scheduleLabel = scheduleLabel
        self.assignedDays = assignedDays
        self.sessionB = sessionB
        self.sessionC = sessionC
        self.sessionD = sessionD
        self.isMeetDay = isMeetDay
        self.isCompetitionSession = isCompetitionSession
        self.isMainSession = isMainSession
        self.focus = focus
        self.microProgram = microProgram
        self.meetBodyweight = meetBodyweight
        self.meetResults = meetResults
        self.competitionDetails = competitionDetails
        self.competitionRecordId = competitionRecordId
        self.competitionKeyDateId = competitionKeyDateId
        self.competitionSportType = competitionSportType
        self.competitionRecordMode = competitionRecordMode
        self.trainingBackup = trainingBackup
        self.supersetGroups = supersetGroups
        self.lastModifiedAtMs = lastModifiedAtMs
        self.targetDurationMinutes = targetDurationMinutes
        self.volumeAdvances = volumeAdvances
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = [
        "id", "name", "description", "exercises", "warmup", "parts", "background", "coverStyle",
        "dayOfWeek", "scheduleLabel", "assignedDays", "sessionB", "sessionC", "sessionD", "isMeetDay",
        "isCompetitionSession", "isMainSession", "focus", "microProgram", "meetBodyweight", "meetResults",
        "competitionDetails", "competitionRecordId", "competitionKeyDateId", "competitionSportType",
        "competitionRecordMode", "trainingBackup", "supersetGroups", "lastModifiedAtMs",
        "targetDurationMinutes", "volumeAdvances",
    ]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        id = try c.decode(String.self, "id")
        name = try c.decode(String.self, "name")
        description = try c.decodeOptional(String.self, "description")
        exercises = try c.decode([Exercise].self, "exercises", default: [])
        warmup = try c.decode([WarmupExercise].self, "warmup", default: [])
        parts = try c.decode([SessionPart].self, "parts", default: [])
        background = try c.decodeOptional(SessionBackground.self, "background")
        coverStyle = try c.decodeOptional(CoverStyle.self, "coverStyle")
        dayOfWeek = try c.decodeOptional(Int.self, "dayOfWeek")
        scheduleLabel = try c.decodeOptional(String.self, "scheduleLabel")
        assignedDays = try c.decode([Int].self, "assignedDays", default: [])
        sessionB = try c.decodeOptional(Session.self, "sessionB")
        sessionC = try c.decodeOptional(Session.self, "sessionC")
        sessionD = try c.decodeOptional(Session.self, "sessionD")
        isMeetDay = try c.decode(Bool.self, "isMeetDay", default: false)
        isCompetitionSession = try c.decode(Bool.self, "isCompetitionSession", default: false)
        isMainSession = try c.decode(Bool.self, "isMainSession", default: false)
        focus = try c.decodeOptional(String.self, "focus")
        microProgram = try c.decodeOptional(SessionMicroProgram.self, "microProgram")
        meetBodyweight = try c.decodeOptional(Double.self, "meetBodyweight")
        meetResults = try c.decodeOptional(MeetResults.self, "meetResults")
        competitionDetails = try c.decodeOptional(CompetitionDetails.self, "competitionDetails")
        competitionRecordId = try c.decodeOptional(String.self, "competitionRecordId")
        competitionKeyDateId = try c.decodeOptional(String.self, "competitionKeyDateId")
        competitionSportType = try c.decodeOptional(CompetitionTemplateType.self, "competitionSportType")
        competitionRecordMode = try c.decodeOptional(CompetitionRecordMode.self, "competitionRecordMode")
        trainingBackup = try c.decodeOptional(TrainingBackup.self, "trainingBackup")
        supersetGroups = try c.decode([SupersetGroup].self, "supersetGroups", default: [])
        lastModifiedAtMs = try c.decode(Int64.self, "lastModifiedAtMs", default: 0)
        targetDurationMinutes = try c.decodeOptional(Int.self, "targetDurationMinutes")
        volumeAdvances = try c.decode([VolumeAdvance].self, "volumeAdvances", default: [])
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(id, forKey: DynamicCodingKey("id"))
        try c.encode(name, forKey: DynamicCodingKey("name"))
        try c.encodeIfPresent(description, forKey: DynamicCodingKey("description"))
        try c.encode(exercises, forKey: DynamicCodingKey("exercises"))
        try c.encode(warmup, forKey: DynamicCodingKey("warmup"))
        try c.encode(parts, forKey: DynamicCodingKey("parts"))
        try c.encodeIfPresent(background, forKey: DynamicCodingKey("background"))
        try c.encodeIfPresent(coverStyle, forKey: DynamicCodingKey("coverStyle"))
        try c.encodeIfPresent(dayOfWeek, forKey: DynamicCodingKey("dayOfWeek"))
        try c.encodeIfPresent(scheduleLabel, forKey: DynamicCodingKey("scheduleLabel"))
        try c.encode(assignedDays, forKey: DynamicCodingKey("assignedDays"))
        try c.encodeIfPresent(sessionB, forKey: DynamicCodingKey("sessionB"))
        try c.encodeIfPresent(sessionC, forKey: DynamicCodingKey("sessionC"))
        try c.encodeIfPresent(sessionD, forKey: DynamicCodingKey("sessionD"))
        try c.encode(isMeetDay, forKey: DynamicCodingKey("isMeetDay"))
        try c.encode(isCompetitionSession, forKey: DynamicCodingKey("isCompetitionSession"))
        try c.encode(isMainSession, forKey: DynamicCodingKey("isMainSession"))
        try c.encodeIfPresent(focus, forKey: DynamicCodingKey("focus"))
        try c.encodeIfPresent(microProgram, forKey: DynamicCodingKey("microProgram"))
        try c.encodeIfPresent(meetBodyweight, forKey: DynamicCodingKey("meetBodyweight"))
        try c.encodeIfPresent(meetResults, forKey: DynamicCodingKey("meetResults"))
        try c.encodeIfPresent(competitionDetails, forKey: DynamicCodingKey("competitionDetails"))
        try c.encodeIfPresent(competitionRecordId, forKey: DynamicCodingKey("competitionRecordId"))
        try c.encodeIfPresent(competitionKeyDateId, forKey: DynamicCodingKey("competitionKeyDateId"))
        try c.encodeIfPresent(competitionSportType, forKey: DynamicCodingKey("competitionSportType"))
        try c.encodeIfPresent(competitionRecordMode, forKey: DynamicCodingKey("competitionRecordMode"))
        try c.encodeIfPresent(trainingBackup, forKey: DynamicCodingKey("trainingBackup"))
        try c.encode(supersetGroups, forKey: DynamicCodingKey("supersetGroups"))
        try c.encode(lastModifiedAtMs, forKey: DynamicCodingKey("lastModifiedAtMs"))
        try c.encodeIfPresent(targetDurationMinutes, forKey: DynamicCodingKey("targetDurationMinutes"))
        try c.encode(volumeAdvances, forKey: DynamicCodingKey("volumeAdvances"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }

    public func allSupersetGroups() -> [SupersetGroup] {
        let local = supersetGroups.isEmpty ? legacySupersetGroups() : supersetGroups
        if !local.isEmpty { return local }
        return legacySupersetGroups()
    }

    private func legacySupersetGroups() -> [SupersetGroup] {
        var seen = Set<String>()
        let supersetIds = allExercises().compactMap { exercise -> String? in
            guard let id = exercise.supersetId, !id.isEmpty else { return nil }
            if seen.contains(id) { return nil }
            seen.insert(id)
            return id
        }
        if supersetIds.isEmpty { return [] }
        return supersetIds.map { id in
            let members = allExercises().filter { $0.supersetId == id }
            return SupersetGroup(
                id: id,
                exerciseOrder: members.map { $0.id },
                restBetweenExercises: members.first?.supersetRestBetween ?? 60,
                restAfterSuperset: members.first?.supersetRestAfter ?? 120,
                rounds: nil,
                isOptional: false
            )
        }
    }

    public func allExercises() -> [Exercise] {
        return exercises + parts.flatMap { $0.exercises }
    }
}

// ─── Exercise Extensions ──────────────────────────────────────────────────────

extension Exercise {
    public func isInSuperset() -> Bool {
        return (supersetGroupRef?.isEmpty == false) || (supersetId?.isEmpty == false)
    }

    public func isEffectivelyUnilateral() -> Bool {
        return unilateralMode != .BILATERAL || isUnilateral
    }

    public func supersetGroupRefOrLegacyId() -> String? {
        if let ref = supersetGroupRef, !ref.isEmpty { return ref }
        if let sid = supersetId, !sid.isEmpty { return sid }
        return nil
    }
}

// ─── Session Extensions ───────────────────────────────────────────────────────

extension Session {
    public func effectiveSupersetGroupFor(exercise: Exercise) -> SupersetGroup? {
        guard let ref = exercise.supersetGroupRef ?? exercise.supersetId else { return nil }
        return allSupersetGroups().first { $0.id == ref }
    }
}
