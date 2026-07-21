import Foundation

// ─── WorkoutLog ───────────────────────────────────────────────────────────────

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
    public let energySummary: SessionEnergySummary?
    public let weekId: String?
    public let macroIndex: Int?
    public let mesoIndex: Int?
    public let clarityRating: Int?
    public let environmentTags: [String]
    public let stillPresentDiscomfortIds: [String]
    public let planDeviations: [PlanDeviation]
    public let exerciseTags: [String: String]
    public let contextualPerformanceStateV2: [String: ContextPerformanceStateV2]
    public let globalPerformanceStateV3: [String: GlobalPerformanceStateV3]
    public let contextProfilesV3: [String: WorkoutContextProfile]
    public let replacementDecisionsV2: [ExerciseReplacementDecisionV2]
    public let postExerciseReports: [ExerciseDiscomfortReport]
    public let omittedExercises: [OmittedExercise]
    public let scheduledDate: String?
    public let actualDate: String?
    public let scheduleDeltaDays: Int?

    public init(
        id: String,
        programId: String,
        sessionId: String,
        sessionName: String,
        date: String,
        durationMinutes: Int,
        completedExercises: [CompletedExercise] = [],
        fatigueLevel: Int? = nil,
        discomforts: [String] = [],
        notes: String? = nil,
        totalVolume: Double = 0.0,
        sessionStressScore: Double? = nil,
        energySummary: SessionEnergySummary? = nil,
        weekId: String? = nil,
        macroIndex: Int? = nil,
        mesoIndex: Int? = nil,
        clarityRating: Int? = nil,
        environmentTags: [String] = [],
        stillPresentDiscomfortIds: [String] = [],
        planDeviations: [PlanDeviation] = [],
        exerciseTags: [String: String] = [:],
        contextualPerformanceStateV2: [String: ContextPerformanceStateV2] = [:],
        globalPerformanceStateV3: [String: GlobalPerformanceStateV3] = [:],
        contextProfilesV3: [String: WorkoutContextProfile] = [:],
        replacementDecisionsV2: [ExerciseReplacementDecisionV2] = [],
        postExerciseReports: [ExerciseDiscomfortReport] = [],
        omittedExercises: [OmittedExercise] = [],
        scheduledDate: String? = nil,
        actualDate: String? = nil,
        scheduleDeltaDays: Int? = nil
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
        self.energySummary = energySummary
        self.weekId = weekId
        self.macroIndex = macroIndex
        self.mesoIndex = mesoIndex
        self.clarityRating = clarityRating
        self.environmentTags = environmentTags
        self.stillPresentDiscomfortIds = stillPresentDiscomfortIds
        self.planDeviations = planDeviations
        self.exerciseTags = exerciseTags
        self.contextualPerformanceStateV2 = contextualPerformanceStateV2
        self.globalPerformanceStateV3 = globalPerformanceStateV3
        self.contextProfilesV3 = contextProfilesV3
        self.replacementDecisionsV2 = replacementDecisionsV2
        self.postExerciseReports = postExerciseReports
        self.omittedExercises = omittedExercises
        self.scheduledDate = scheduledDate
        self.actualDate = actualDate
        self.scheduleDeltaDays = scheduleDeltaDays
    }
}

// ─── OmittedExercise ─────────────────────────────────────────────────────────

public struct OmittedExercise: Codable {
    public let exerciseId: String
    public let exerciseName: String
    public let exerciseDbId: String?

    public init(exerciseId: String, exerciseName: String, exerciseDbId: String? = nil) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.exerciseDbId = exerciseDbId
    }
}

// ─── ExerciseDiscomfortReport ────────────────────────────────────────────────

public struct ExerciseDiscomfortReport: Codable {
    public let exerciseId: String
    public let exerciseDbId: String?
    public let canonicalExerciseId: String?
    public let exerciseName: String
    public let technicalQuality: Int
    public let discomfortIds: [String]
    public let notes: String?
    public let perceivedIntensityRpe: Double?
    public let perceivedFailure: Bool

    public init(
        exerciseId: String,
        exerciseDbId: String? = nil,
        canonicalExerciseId: String? = nil,
        exerciseName: String,
        technicalQuality: Int,
        discomfortIds: [String] = [],
        notes: String? = nil,
        perceivedIntensityRpe: Double? = nil,
        perceivedFailure: Bool = false
    ) {
        self.exerciseId = exerciseId
        self.exerciseDbId = exerciseDbId
        self.canonicalExerciseId = canonicalExerciseId
        self.exerciseName = exerciseName
        self.technicalQuality = technicalQuality
        self.discomfortIds = discomfortIds
        self.notes = notes
        self.perceivedIntensityRpe = perceivedIntensityRpe
        self.perceivedFailure = perceivedFailure
    }
}

// ─── PlanDeviation ───────────────────────────────────────────────────────────

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

public enum PlanDeviationType: String, Codable {
    case WEIGHT_HIGH, WEIGHT_LOW
    case UNPLANNED_FAILURE, UNPLANNED_DROPSET, UNPLANNED_REST_PAUSE
    case REPS_HIGH, REPS_LOW
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

// ─── CompletedSet ─────────────────────────────────────────────────────────────

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
    public let recordedPayloadV3: RecordedSetPayload?
    public let homologatedResultV3: HomologatedPerformanceResult?
    public let setOutcomeV2: SetOutcomeV2?
    public let rom: Int?
    public let assistedReps: Int?

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
        recordedPayloadV3: RecordedSetPayload? = nil,
        homologatedResultV3: HomologatedPerformanceResult? = nil,
        setOutcomeV2: SetOutcomeV2? = nil,
        rom: Int? = nil,
        assistedReps: Int? = nil
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
        self.recordedPayloadV3 = recordedPayloadV3
        self.homologatedResultV3 = homologatedResultV3
        self.setOutcomeV2 = setOutcomeV2
        self.rom = rom
        self.assistedReps = assistedReps
    }
}

extension CompletedSet {
    public func effectiveRepEquivalent() -> Double {
        return Double(reps) + Double(max(partialReps ?? 0, 0)) * 0.5
    }
}

// ─── OngoingWorkoutState ─────────────────────────────────────────────────────

public struct OngoingWorkoutState: Codable {
    public let programId: String
    public let session: Session
    public var isPaused: Bool
    public let startTime: Int64
    public var activeExerciseId: String?
    public var activeSetId: String?
    public var activeSetIndex: Int
    public var activeExerciseIndex: Int
    public var activeStepKey: String?
    public var activeMode: WeekVariant
    public var completedSets: [String: CompletedSet]
    public var dynamicWeights: [String: Double]
    public var loadSuggestionReasons: [String: String]
    public var setDrafts: [String: WorkoutSetDraft]
    public var manualLoadOverrides: [String: Double]
    public var editingSetKey: String?
    public var isCarpeDiem: Bool
    public var macroIndex: Int?
    public var mesoIndex: Int?
    public var weekId: String?
    public var exerciseTags: [String: String]
    public var activeTags: [String: [String]]
    public var activeSubTags: [String: [String]]
    public var userCreatedTags: [String: [WorkoutTag]]
    public var contextProfilesV3: [String: WorkoutContextProfile]
    public var activeContextProfileByExerciseId: [String: String]
    public var skippedExerciseIds: Set<String>
    public var warmupCompletedExerciseIds: Set<String>
    public var mobilityCompletedExerciseIds: Set<String>
    public var readinessNeuralOverride: Int?
    public var readinessMuscularOverride: Int?
    public var readinessSpinalOverride: Int?
    public var readinessMuscleOverrides: [String: Int]
    public var restModalState: WorkoutRestModalState?
    public var persistedLoadModeBySet: [String: LoadModeV2]
    public var persistedLoadModeByExercise: [String: LoadModeV2]
    public var customTargetDurationMinutes: Int?

    public init(
        programId: String,
        session: Session,
        isPaused: Bool = false,
        startTime: Int64,
        activeExerciseId: String? = nil,
        activeSetId: String? = nil,
        activeSetIndex: Int = 0,
        activeExerciseIndex: Int = 0,
        activeStepKey: String? = nil,
        activeMode: WeekVariant = .A,
        completedSets: [String: CompletedSet] = [:],
        dynamicWeights: [String: Double] = [:],
        loadSuggestionReasons: [String: String] = [:],
        setDrafts: [String: WorkoutSetDraft] = [:],
        manualLoadOverrides: [String: Double] = [:],
        editingSetKey: String? = nil,
        isCarpeDiem: Bool = false,
        macroIndex: Int? = nil,
        mesoIndex: Int? = nil,
        weekId: String? = nil,
        exerciseTags: [String: String] = [:],
        activeTags: [String: [String]] = [:],
        activeSubTags: [String: [String]] = [:],
        userCreatedTags: [String: [WorkoutTag]] = [:],
        contextProfilesV3: [String: WorkoutContextProfile] = [:],
        activeContextProfileByExerciseId: [String: String] = [:],
        skippedExerciseIds: Set<String> = [],
        warmupCompletedExerciseIds: Set<String> = [],
        mobilityCompletedExerciseIds: Set<String> = [],
        readinessNeuralOverride: Int? = nil,
        readinessMuscularOverride: Int? = nil,
        readinessSpinalOverride: Int? = nil,
        readinessMuscleOverrides: [String: Int] = [:],
        restModalState: WorkoutRestModalState? = nil,
        persistedLoadModeBySet: [String: LoadModeV2] = [:],
        persistedLoadModeByExercise: [String: LoadModeV2] = [:],
        customTargetDurationMinutes: Int? = nil
    ) {
        self.programId = programId
        self.session = session
        self.isPaused = isPaused
        self.startTime = startTime
        self.activeExerciseId = activeExerciseId
        self.activeSetId = activeSetId
        self.activeSetIndex = activeSetIndex
        self.activeExerciseIndex = activeExerciseIndex
        self.activeStepKey = activeStepKey
        self.activeMode = activeMode
        self.completedSets = completedSets
        self.dynamicWeights = dynamicWeights
        self.loadSuggestionReasons = loadSuggestionReasons
        self.setDrafts = setDrafts
        self.manualLoadOverrides = manualLoadOverrides
        self.editingSetKey = editingSetKey
        self.isCarpeDiem = isCarpeDiem
        self.macroIndex = macroIndex
        self.mesoIndex = mesoIndex
        self.weekId = weekId
        self.exerciseTags = exerciseTags
        self.activeTags = activeTags
        self.activeSubTags = activeSubTags
        self.userCreatedTags = userCreatedTags
        self.contextProfilesV3 = contextProfilesV3
        self.activeContextProfileByExerciseId = activeContextProfileByExerciseId
        self.skippedExerciseIds = skippedExerciseIds
        self.warmupCompletedExerciseIds = warmupCompletedExerciseIds
        self.mobilityCompletedExerciseIds = mobilityCompletedExerciseIds
        self.readinessNeuralOverride = readinessNeuralOverride
        self.readinessMuscularOverride = readinessMuscularOverride
        self.readinessSpinalOverride = readinessSpinalOverride
        self.readinessMuscleOverrides = readinessMuscleOverrides
        self.restModalState = restModalState
        self.persistedLoadModeBySet = persistedLoadModeBySet
        self.persistedLoadModeByExercise = persistedLoadModeByExercise
        self.customTargetDurationMinutes = customTargetDurationMinutes
    }
}

// ─── TodaySessionItem ────────────────────────────────────────────────────────

public struct TodaySessionItem: Codable {
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

// ─── ActiveProgramState ──────────────────────────────────────────────────────

public struct ActiveProgramState: Codable {
    public let programId: String
    public let status: ProgramStatus
    public let currentMacrocycleIndex: Int
    public let currentBlockIndex: Int
    public let currentMesocycleIndex: Int
    public let currentWeekId: String

    public init(
        programId: String,
        status: ProgramStatus = .ACTIVE,
        currentMacrocycleIndex: Int = 0,
        currentBlockIndex: Int = 0,
        currentMesocycleIndex: Int = 0,
        currentWeekId: String = ""
    ) {
        self.programId = programId
        self.status = status
        self.currentMacrocycleIndex = currentMacrocycleIndex
        self.currentBlockIndex = currentBlockIndex
        self.currentMesocycleIndex = currentMesocycleIndex
        self.currentWeekId = currentWeekId
    }
}

public enum ProgramStatus: String, Codable {
    case ACTIVE, PAUSED, COMPLETED
}
