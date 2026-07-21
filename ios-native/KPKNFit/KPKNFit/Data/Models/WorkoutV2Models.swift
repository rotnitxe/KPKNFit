import Foundation

// ─── WorkoutFeatureFlags ──────────────────────────────────────────────────────

public struct WorkoutFeatureFlags: Codable {
    public var workoutV2UiShell: Bool = true
    public var workoutV2SetCarousel: Bool = true
    public var workoutV2LoadModes: Bool = true
    public var workoutV2Homologation: Bool = true
    public var workoutV2ContextMenu: Bool = true
    public var workoutV2HeaderWidgets: Bool = true
    public var workoutV3UnifiedFlow: Bool = true

    public init(
        workoutV2UiShell: Bool = true,
        workoutV2SetCarousel: Bool = true,
        workoutV2LoadModes: Bool = true,
        workoutV2Homologation: Bool = true,
        workoutV2ContextMenu: Bool = true,
        workoutV2HeaderWidgets: Bool = true,
        workoutV3UnifiedFlow: Bool = true
    ) {
        self.workoutV2UiShell = workoutV2UiShell
        self.workoutV2SetCarousel = workoutV2SetCarousel
        self.workoutV2LoadModes = workoutV2LoadModes
        self.workoutV2Homologation = workoutV2Homologation
        self.workoutV2ContextMenu = workoutV2ContextMenu
        self.workoutV2HeaderWidgets = workoutV2HeaderWidgets
        self.workoutV3UnifiedFlow = workoutV3UnifiedFlow
    }
}

// ─── WorkoutHeaderWidgets ─────────────────────────────────────────────────────

public struct WorkoutHeaderWidgets: Codable {
    public var showRmCalculator: Bool = false
    public var showRealtimeRings: Bool = false

    public init(
        showRmCalculator: Bool = false,
        showRealtimeRings: Bool = false
    ) {
        self.showRmCalculator = showRmCalculator
        self.showRealtimeRings = showRealtimeRings
    }
}

// ─── Enums ────────────────────────────────────────────────────────────────────

public enum LoadModeV2: String, Codable {
    case LOAD, BODYWEIGHT, LASTRE, ASSISTED
}

public enum UnitModeV2: String, Codable {
    case REPS, TIME, DISTANCE, CUSTOM
}

public enum SetTechniqueV2: String, Codable {
    case DROP_SET, REST_PAUSE, PARTIALS, FAILURE, AMRAP
}

public enum HistoryColorV2: String, Codable {
    case NEUTRAL, YELLOW, RED
}

public enum DifficultySignalV2: String, Codable {
    case EASIER, HARDER, MATCHED
}

public enum ReplacementPersistenceScopeV2: String, Codable {
    case SESSION_ONLY, PERMANENT, MESOCYCLE_MATCHING
}

public enum WorkoutContextLinkStrategyV3: String, Codable {
    case LINKED_EDITABLE, INDEPENDENT, STRICT
}

public enum TimeProgressionStrategyV3: String, Codable {
    case LOAD_THEN_TIME, TIME_ONLY
}

// ─── WorkoutTag / SubTag ──────────────────────────────────────────────────────

public struct WorkoutTag: Codable {
    public var id: String = ""
    public var name: String = ""
    public var exerciseKey: String = ""
    public var subTags: [WorkoutSubTag] = []
    public var createdAtIso: String = ""
    public var lastUsedAtIso: String = ""
    public var usageCount: Int = 0

    public init(
        id: String = "",
        name: String = "",
        exerciseKey: String = "",
        subTags: [WorkoutSubTag] = [],
        createdAtIso: String = "",
        lastUsedAtIso: String = "",
        usageCount: Int = 0
    ) {
        self.id = id
        self.name = name
        self.exerciseKey = exerciseKey
        self.subTags = subTags
        self.createdAtIso = createdAtIso
        self.lastUsedAtIso = lastUsedAtIso
        self.usageCount = usageCount
    }
}

public struct WorkoutSubTag: Codable {
    public var id: String = ""
    public var name: String = ""
    public var category: SubTagCategory = .LIBRE

    public init(
        id: String = "",
        name: String = "",
        category: SubTagCategory = .LIBRE
    ) {
        self.id = id
        self.name = name
        self.category = category
    }
}

public enum SubTagCategory: String, Codable {
    case MARCA, SETUP, TECNICA, LIBRE
}

// ─── WorkoutContextProfile ────────────────────────────────────────────────────

public struct WorkoutContextProfile: Codable {
    public var id: String
    public var exerciseKey: String
    public var tagId: String? = nil
    public var setupProfileId: String? = nil
    public var setupLabel: String? = nil
    public var machineBrand: String? = nil
    public var loadMode: LoadModeV2? = nil
    public var linkStrategy: WorkoutContextLinkStrategyV3 = .LINKED_EDITABLE
    public var setupDetails: ExerciseSetupDetails? = nil
    public var barWeightKg: Double? = nil
    public var notes: String? = nil
    public var createdAtIso: String? = nil
    public var lastUsedAtIso: String? = nil
    public var usageCount: Int = 0

    public init(
        id: String,
        exerciseKey: String,
        tagId: String? = nil,
        setupProfileId: String? = nil,
        setupLabel: String? = nil,
        machineBrand: String? = nil,
        loadMode: LoadModeV2? = nil,
        linkStrategy: WorkoutContextLinkStrategyV3 = .LINKED_EDITABLE,
        setupDetails: ExerciseSetupDetails? = nil,
        barWeightKg: Double? = nil,
        notes: String? = nil,
        createdAtIso: String? = nil,
        lastUsedAtIso: String? = nil,
        usageCount: Int = 0
    ) {
        self.id = id
        self.exerciseKey = exerciseKey
        self.tagId = tagId
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
}

// ─── RecordedSetPayload ──────────────────────────────────────────────────────

public struct RecordedSetPayload: Codable {
    public var contextProfileId: String? = nil
    public var exerciseId: String
    public var exerciseDbId: String? = nil
    public var side: String? = nil
    public var loadInputMode: LoadModeV2 = .LOAD
    public var unitMode: UnitModeV2 = .REPS
    public var externalLoad: Double? = nil
    public var assistedLoad: Double? = nil
    public var bodyWeightSnapshot: Double? = nil
    public var completedReps: Int? = nil
    public var partialReps: Int? = nil
    public var durationSeconds: Int? = nil
    public var actualIntensityMode: IntensityMode? = nil
    public var actualIntensityValue: Double? = nil
    public var techniques: [SetTechniqueV2] = []
    public var failedSet: Bool = false
    public var reachedFailure: Bool = false
    public var amrapPerformed: Bool = false
    public var timerTargetSeconds: Int? = nil
    public var timerElapsedSeconds: Int? = nil
    public var failureReason: String? = nil
    public var executionError: Bool = false
    public var skipped: Bool = false
    public var superSetWithExerciseId: String? = nil

    public init(
        contextProfileId: String? = nil,
        exerciseId: String,
        exerciseDbId: String? = nil,
        side: String? = nil,
        loadInputMode: LoadModeV2 = .LOAD,
        unitMode: UnitModeV2 = .REPS,
        externalLoad: Double? = nil,
        assistedLoad: Double? = nil,
        bodyWeightSnapshot: Double? = nil,
        completedReps: Int? = nil,
        partialReps: Int? = nil,
        durationSeconds: Int? = nil,
        actualIntensityMode: IntensityMode? = nil,
        actualIntensityValue: Double? = nil,
        techniques: [SetTechniqueV2] = [],
        failedSet: Bool = false,
        reachedFailure: Bool = false,
        amrapPerformed: Bool = false,
        timerTargetSeconds: Int? = nil,
        timerElapsedSeconds: Int? = nil,
        failureReason: String? = nil,
        executionError: Bool = false,
        skipped: Bool = false,
        superSetWithExerciseId: String? = nil
    ) {
        self.contextProfileId = contextProfileId
        self.exerciseId = exerciseId
        self.exerciseDbId = exerciseDbId
        self.side = side
        self.loadInputMode = loadInputMode
        self.unitMode = unitMode
        self.externalLoad = externalLoad
        self.assistedLoad = assistedLoad
        self.bodyWeightSnapshot = bodyWeightSnapshot
        self.completedReps = completedReps
        self.partialReps = partialReps
        self.durationSeconds = durationSeconds
        self.actualIntensityMode = actualIntensityMode
        self.actualIntensityValue = actualIntensityValue
        self.techniques = techniques
        self.failedSet = failedSet
        self.reachedFailure = reachedFailure
        self.amrapPerformed = amrapPerformed
        self.timerTargetSeconds = timerTargetSeconds
        self.timerElapsedSeconds = timerElapsedSeconds
        self.failureReason = failureReason
        self.executionError = executionError
        self.skipped = skipped
        self.superSetWithExerciseId = superSetWithExerciseId
    }
}

// ─── GlobalPerformanceStateV3 ────────────────────────────────────────────────

public struct GlobalPerformanceStateV3: Codable {
    public var globalKey: String
    public var ewma: Double = 0.0
    public var mean: Double = 0.0
    public var variance: Double = 0.0
    public var bestScore: Double = 0.0
    public var sampleCount: Int = 0
    public var recentScores: [Double] = []
    public var lastUpdatedAtIso: String? = nil

    public init(
        globalKey: String,
        ewma: Double = 0.0,
        mean: Double = 0.0,
        variance: Double = 0.0,
        bestScore: Double = 0.0,
        sampleCount: Int = 0,
        recentScores: [Double] = [],
        lastUpdatedAtIso: String? = nil
    ) {
        self.globalKey = globalKey
        self.ewma = ewma
        self.mean = mean
        self.variance = variance
        self.bestScore = bestScore
        self.sampleCount = sampleCount
        self.recentScores = recentScores
        self.lastUpdatedAtIso = lastUpdatedAtIso
    }
}

// ─── HomologatedPerformanceResult ────────────────────────────────────────────

public struct HomologatedPerformanceResult: Codable {
    public var contextKey: String
    public var globalKey: String
    public var loadMode: LoadModeV2
    public var unitMode: UnitModeV2
    public var plannedTarget: Double? = nil
    public var actualValue: Double
    public var actualIntensity: Double? = nil
    public var debt: Double = 0.0
    public var failedSet: Bool = false
    public var reachedFailure: Bool = false
    public var amrapOverride: Bool = false
    public var techniques: [SetTechniqueV2] = []
    public var metricType: String
    public var metricValue: Double
    public var estimatedRm: Double? = nil
    public var trm: Double? = nil
    public var localPerformanceIndex: Double
    public var globalPerformanceIndex: Double
    public var contextPercentile: Double
    public var globalPercentile: Double
    public var contextEwma: Double
    public var contextStdDev: Double
    public var globalEwma: Double
    public var globalStdDev: Double
    public var isContextPr: Bool
    public var isGlobalPr: Bool
    public var historyColor: HistoryColorV2
    public var difficultySignal: DifficultySignalV2
    public var suggestedNextLoad: Double? = nil
    public var suggestedTargetSeconds: Int? = nil
    public var suggestionReason: String? = nil
    public var augeEquivalentLoad: Double
    public var augeEquivalentReps: Int
    public var ermRangeMin: Double = 0.0
    public var ermRangeMax: Double = 0.0
    public var suggestedLoadMode: LoadModeV2? = nil

    public init(
        contextKey: String,
        globalKey: String,
        loadMode: LoadModeV2,
        unitMode: UnitModeV2,
        plannedTarget: Double? = nil,
        actualValue: Double,
        actualIntensity: Double? = nil,
        debt: Double = 0.0,
        failedSet: Bool = false,
        reachedFailure: Bool = false,
        amrapOverride: Bool = false,
        techniques: [SetTechniqueV2] = [],
        metricType: String,
        metricValue: Double,
        estimatedRm: Double? = nil,
        trm: Double? = nil,
        localPerformanceIndex: Double,
        globalPerformanceIndex: Double,
        contextPercentile: Double,
        globalPercentile: Double,
        contextEwma: Double,
        contextStdDev: Double,
        globalEwma: Double,
        globalStdDev: Double,
        isContextPr: Bool,
        isGlobalPr: Bool,
        historyColor: HistoryColorV2,
        difficultySignal: DifficultySignalV2,
        suggestedNextLoad: Double? = nil,
        suggestedTargetSeconds: Int? = nil,
        suggestionReason: String? = nil,
        augeEquivalentLoad: Double,
        augeEquivalentReps: Int,
        ermRangeMin: Double = 0.0,
        ermRangeMax: Double = 0.0,
        suggestedLoadMode: LoadModeV2? = nil
    ) {
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

// ─── SetEntryV2 ──────────────────────────────────────────────────────────────

public struct SetEntryV2: Codable {
    public let exerciseId: String
    public let exerciseDbId: String?
    public let canonicalExerciseId: String?
    public let setIndex: Int
    public let loadMode: LoadModeV2
    public let unitMode: UnitModeV2
    public let plannedTarget: Double?
    public let actualValue: Double
    public let loggedLoad: Double?
    public let bodyWeight: Double?
    public let plannedIntensity: Double?
    public let actualIntensity: Double?
    public let debt: Double
    public let failedSet: Bool
    public let reachedFailure: Bool
    public let amrapOverride: Bool
    public let techniques: [SetTechniqueV2]
    public let tagId: String?
    public let setupId: String?
    public let machineBrand: String?
    public let contextKey: String
    public let timeProgressionStrategy: TimeProgressionStrategyV3
    public let barWeightKg: Double?
    public let rom: Int?
    public let assistedReps: Int?
    public let isFirstEvaluationInSession: Bool

    public init(
        exerciseId: String,
        exerciseDbId: String?,
        canonicalExerciseId: String?,
        setIndex: Int,
        loadMode: LoadModeV2,
        unitMode: UnitModeV2,
        plannedTarget: Double?,
        actualValue: Double,
        loggedLoad: Double?,
        bodyWeight: Double?,
        plannedIntensity: Double?,
        actualIntensity: Double?,
        debt: Double,
        failedSet: Bool,
        reachedFailure: Bool,
        amrapOverride: Bool,
        techniques: [SetTechniqueV2],
        tagId: String?,
        setupId: String?,
        machineBrand: String?,
        contextKey: String,
        timeProgressionStrategy: TimeProgressionStrategyV3,
        barWeightKg: Double?,
        rom: Int?,
        assistedReps: Int?,
        isFirstEvaluationInSession: Bool
    ) {
        self.exerciseId = exerciseId
        self.exerciseDbId = exerciseDbId
        self.canonicalExerciseId = canonicalExerciseId
        self.setIndex = setIndex
        self.loadMode = loadMode
        self.unitMode = unitMode
        self.plannedTarget = plannedTarget
        self.actualValue = actualValue
        self.loggedLoad = loggedLoad
        self.bodyWeight = bodyWeight
        self.plannedIntensity = plannedIntensity
        self.actualIntensity = actualIntensity
        self.debt = debt
        self.failedSet = failedSet
        self.reachedFailure = reachedFailure
        self.amrapOverride = amrapOverride
        self.techniques = techniques
        self.tagId = tagId
        self.setupId = setupId
        self.machineBrand = machineBrand
        self.contextKey = contextKey
        self.timeProgressionStrategy = timeProgressionStrategy
        self.barWeightKg = barWeightKg
        self.rom = rom
        self.assistedReps = assistedReps
        self.isFirstEvaluationInSession = isFirstEvaluationInSession
    }

    public func resolvedCanonicalExerciseId() -> String {
        let trimmed = canonicalExerciseId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let t = trimmed, !t.isEmpty { return t }
        let fallback = (exerciseDbId ?? exerciseId).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !fallback.isEmpty { return fallback }
        return "unknown"
    }
}

// ─── SetOutcomeV2 ────────────────────────────────────────────────────────────

public struct SetOutcomeV2: Codable {
    public var contextKey: String = ""
    public var loadMode: LoadModeV2 = .LOAD
    public var unitMode: UnitModeV2 = .REPS
    public var plannedTarget: Double? = nil
    public var actualValue: Double = 0.0
    public var actualIntensity: Double? = nil
    public var debt: Double = 0.0
    public var failedSet: Bool = false
    public var reachedFailure: Bool = false
    public var amrapOverride: Bool = false
    public var techniques: [SetTechniqueV2] = []
    public var metricType: String = ""
    public var metricValue: Double = 0.0
    public var estimatedRm: Double? = nil
    public var trm: Double? = nil
    public var globalPerformanceIndex: Double = 50.0
    public var contextPercentile: Double = 50.0
    public var globalPercentile: Double = 0.0
    public var contextEwma: Double = 0.0
    public var contextStdDev: Double = 0.0
    public var globalEwma: Double = 0.0
    public var globalStdDev: Double = 0.0
    public var isContextPr: Bool = false
    public var isGlobalPr: Bool = false
    public var historyColor: HistoryColorV2 = .NEUTRAL
    public var difficultySignal: DifficultySignalV2 = .MATCHED
    public var suggestedNextLoad: Double? = nil
    public var suggestedTargetSeconds: Int? = nil
    public var suggestionReason: String? = nil
    public var augeEquivalentLoad: Double = 0.0
    public var augeEquivalentReps: Int = 0
    public var suggestedLoadMode: LoadModeV2? = nil

    public init(
        contextKey: String = "",
        loadMode: LoadModeV2 = .LOAD,
        unitMode: UnitModeV2 = .REPS,
        plannedTarget: Double? = nil,
        actualValue: Double = 0.0,
        actualIntensity: Double? = nil,
        debt: Double = 0.0,
        failedSet: Bool = false,
        reachedFailure: Bool = false,
        amrapOverride: Bool = false,
        techniques: [SetTechniqueV2] = [],
        metricType: String = "",
        metricValue: Double = 0.0,
        estimatedRm: Double? = nil,
        trm: Double? = nil,
        globalPerformanceIndex: Double = 50.0,
        contextPercentile: Double = 50.0,
        globalPercentile: Double = 0.0,
        contextEwma: Double = 0.0,
        contextStdDev: Double = 0.0,
        globalEwma: Double = 0.0,
        globalStdDev: Double = 0.0,
        isContextPr: Bool = false,
        isGlobalPr: Bool = false,
        historyColor: HistoryColorV2 = .NEUTRAL,
        difficultySignal: DifficultySignalV2 = .MATCHED,
        suggestedNextLoad: Double? = nil,
        suggestedTargetSeconds: Int? = nil,
        suggestionReason: String? = nil,
        augeEquivalentLoad: Double = 0.0,
        augeEquivalentReps: Int = 0,
        suggestedLoadMode: LoadModeV2? = nil
    ) {
        self.contextKey = contextKey
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
        self.suggestedLoadMode = suggestedLoadMode
    }
}

// ─── ContextPerformanceStateV2 ───────────────────────────────────────────────

public struct ContextPerformanceStateV2: Codable {
    public var contextKey: String
    public var ewma: Double = 0.0
    public var mean: Double = 0.0
    public var variance: Double = 0.0
    public var bestScore: Double = 0.0
    public var sampleCount: Int = 0
    public var recentScores: [Double] = []
    public var consecutiveGreenSessions: Int = 0
    public var lastSuggestedLoad: Double? = nil
    public var lastUpdatedAtIso: String? = nil

    public init(
        contextKey: String,
        ewma: Double = 0.0,
        mean: Double = 0.0,
        variance: Double = 0.0,
        bestScore: Double = 0.0,
        sampleCount: Int = 0,
        recentScores: [Double] = [],
        consecutiveGreenSessions: Int = 0,
        lastSuggestedLoad: Double? = nil,
        lastUpdatedAtIso: String? = nil
    ) {
        self.contextKey = contextKey
        self.ewma = ewma
        self.mean = mean
        self.variance = variance
        self.bestScore = bestScore
        self.sampleCount = sampleCount
        self.recentScores = recentScores
        self.consecutiveGreenSessions = consecutiveGreenSessions
        self.lastSuggestedLoad = lastSuggestedLoad
        self.lastUpdatedAtIso = lastUpdatedAtIso
    }
}

// ─── ExerciseReplacementDecisionV2 ───────────────────────────────────────────

public struct ExerciseReplacementDecisionV2: Codable {
    public var id: String
    public var programId: String
    public var sessionId: String
    public var macroIndex: Int
    public var mesoIndex: Int
    public var weekId: String
    public var sessionSlot: Int
    public var exerciseSlot: Int
    public var fromExerciseDbId: String
    public var toExerciseDbId: String
    public var scope: ReplacementPersistenceScopeV2
    public var createdAtIso: String

    public init(
        id: String,
        programId: String,
        sessionId: String,
        macroIndex: Int,
        mesoIndex: Int,
        weekId: String,
        sessionSlot: Int,
        exerciseSlot: Int,
        fromExerciseDbId: String,
        toExerciseDbId: String,
        scope: ReplacementPersistenceScopeV2,
        createdAtIso: String
    ) {
        self.id = id
        self.programId = programId
        self.sessionId = sessionId
        self.macroIndex = macroIndex
        self.mesoIndex = mesoIndex
        self.weekId = weekId
        self.sessionSlot = sessionSlot
        self.exerciseSlot = exerciseSlot
        self.fromExerciseDbId = fromExerciseDbId
        self.toExerciseDbId = toExerciseDbId
        self.scope = scope
        self.createdAtIso = createdAtIso
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

public func buildWorkoutContextKey(
    exerciseId: String,
    machineBrand: String?,
    tagId: String?,
    loadMode: LoadModeV2,
    unitMode: UnitModeV2,
    techSubTags: String? = nil
) -> String {
    let brandRaw = machineBrand?.trimmingCharacters(in: .whitespacesAndNewlines)
    let brandPart = (brandRaw != nil && !brandRaw!.isEmpty) ? brandRaw! : "na"

    let tagRaw = tagId?.trimmingCharacters(in: .whitespacesAndNewlines)
    let tagPart = (tagRaw != nil && !tagRaw!.isEmpty) ? tagRaw! : "na"

    let techPart: String = {
        if let tech = techSubTags, !tech.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return tech
        }
        return "na"
    }()

    let exerciseRaw = exerciseId.trimmingCharacters(in: .whitespacesAndNewlines)
    let exercisePart = exerciseRaw.isEmpty ? "unknown" : exerciseRaw

    return [exercisePart, brandPart, tagPart, techPart, loadMode.rawValue, unitMode.rawValue].joined(separator: "|")
}
