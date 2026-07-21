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
    public init(id: String, name: String, exercises: [Exercise] = [], color: String? = nil, targetDurationMinutes: Int? = nil) {
        self.id = id
        self.name = name
        self.exercises = exercises
        self.color = color
        self.targetDurationMinutes = targetDurationMinutes
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
        volumeAdvances: [VolumeAdvance] = []
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
