import Foundation

public enum RestTimerKind: String, Codable {
    case STANDARD
    case SUPERSET_INTRA
    case SUPERSET_ROUND
    case WARMUP
    case BETWEEN_SIDES
}

public struct WorkoutSetDraft: Codable {
    public var weightText: String?
    public var valueText: String?
    public var intensityText: String?
    public var loadMode: LoadModeV2?
    public var selectedSide: String?
    public var partialReps: Int?
    public var reachedFailure: Bool?
    public var voiceFields: Set<WorkoutVoiceField>
    public var isDirty: Bool
    public var updatedAtMs: Int64
    public var rom: Int?
    public var assistedReps: Int?

    public init(
        weightText: String? = nil,
        valueText: String? = nil,
        intensityText: String? = nil,
        loadMode: LoadModeV2? = nil,
        selectedSide: String? = nil,
        partialReps: Int? = nil,
        reachedFailure: Bool? = nil,
        voiceFields: Set<WorkoutVoiceField> = [],
        isDirty: Bool = false,
        updatedAtMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        rom: Int? = nil,
        assistedReps: Int? = nil
    ) {
        self.weightText = weightText
        self.valueText = valueText
        self.intensityText = intensityText
        self.loadMode = loadMode
        self.selectedSide = selectedSide
        self.partialReps = partialReps
        self.reachedFailure = reachedFailure
        self.voiceFields = voiceFields
        self.isDirty = isDirty
        self.updatedAtMs = updatedAtMs
        self.rom = rom
        self.assistedReps = assistedReps
    }
}

public struct WorkoutRestModalState: Codable {
    public var exerciseId: String?
    public var exerciseName: String
    public var kind: RestTimerKind
    public var plannedSeconds: Int
    public var suggestedSeconds: Int
    public var activeSeconds: Int
    public var endsAtMs: Int64
    public var isManualOverride: Bool
    public var notificationsEnabled: Bool
    public var exactAlarmGranted: Bool
    public var soundReady: Bool
    public var skipCurrentExerciseOnFinish: Bool

    public init(
        exerciseId: String? = nil,
        exerciseName: String = "",
        kind: RestTimerKind = .STANDARD,
        plannedSeconds: Int = 0,
        suggestedSeconds: Int = 0,
        activeSeconds: Int = 0,
        endsAtMs: Int64 = 0,
        isManualOverride: Bool = false,
        notificationsEnabled: Bool = true,
        exactAlarmGranted: Bool = true,
        soundReady: Bool = true,
        skipCurrentExerciseOnFinish: Bool = false
    ) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.kind = kind
        self.plannedSeconds = plannedSeconds
        self.suggestedSeconds = suggestedSeconds
        self.activeSeconds = activeSeconds
        self.endsAtMs = endsAtMs
        self.isManualOverride = isManualOverride
        self.notificationsEnabled = notificationsEnabled
        self.exactAlarmGranted = exactAlarmGranted
        self.soundReady = soundReady
        self.skipCurrentExerciseOnFinish = skipCurrentExerciseOnFinish
    }
}

func workoutSetKey(exerciseId: String, setIdx: Int, side: String? = nil) -> String {
    switch side {
    case "left":
        return "\(exerciseId)_\(setIdx)_L"
    case "right":
        return "\(exerciseId)_\(setIdx)_R"
    default:
        return "\(exerciseId)_\(setIdx)"
    }
}

func workoutSetContextKey(exerciseId: String, setIdx: Int, tagId: String?) -> String {
    let cleanTag = tagId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let resolvedTag = (cleanTag?.isEmpty == false) ? cleanTag! : "na"
    return "\(exerciseId)|\(setIdx)|\(resolvedTag)"
}

func workoutExerciseContextKey(exerciseId: String, tagId: String?) -> String {
    let cleanTag = tagId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let resolvedTag = (cleanTag?.isEmpty == false) ? cleanTag! : "na"
    return "\(exerciseId)|\(resolvedTag)"
}

func resolvePersistedLoadModeForSet(
    exerciseId: String,
    setIdx: Int,
    tagId: String?,
    persistedLoadModeBySet: [String: LoadModeV2],
    persistedLoadModeByExercise: [String: LoadModeV2]
) -> LoadModeV2? {
    for candidateIdx in stride(from: setIdx, through: 0, by: -1) {
        let baseKey = "\(exerciseId)_\(candidateIdx)"
        if let mode = persistedLoadModeBySet[baseKey] { return mode }
        if let mode = persistedLoadModeBySet["\(baseKey)_L"] { return mode }
        if let mode = persistedLoadModeBySet["\(baseKey)_R"] { return mode }
        
        let key = workoutSetContextKey(exerciseId: exerciseId, setIdx: candidateIdx, tagId: tagId)
        if let mode = persistedLoadModeBySet[key] { return mode }
    }
    let exKey = workoutExerciseContextKey(exerciseId: exerciseId, tagId: tagId)
    if let mode = persistedLoadModeByExercise[exKey] { return mode }
    if let mode = persistedLoadModeByExercise[exerciseId] { return mode }
    return nil
}

func resolveEffectiveLoadMode(
    draftLoadMode: LoadModeV2?,
    persistedLoadMode: LoadModeV2?,
    plannedLoadMode: LoadModeV2?,
    defaultCatalogMode: LoadModeV2?
) -> LoadModeV2 {
    return draftLoadMode
        ?? persistedLoadMode
        ?? plannedLoadMode
        ?? defaultCatalogMode
        ?? .LOAD
}

func isWorkoutPulseActive(
    pulseToken: Int64?,
    nowMs: Int64,
    ttlMs: Int64 = 2200
) -> Bool {
    guard let pulseToken = pulseToken else { return false }
    let diff = nowMs - pulseToken
    return diff >= 0 && diff <= ttlMs
}

func inferDefaultLoadModeFromCatalog(exercise: Exercise) -> LoadModeV2 {
    let canonicalId = exercise.resolvedCanonicalExerciseId()
    guard let info = EXERCISE_DATABASE_BY_ID[canonicalId] else { return .LOAD }
    let equipment = info.equipment?.lowercased() ?? ""
    let name = exercise.name.lowercased()
    
    if equipment.contains("peso corporal") || equipment.contains("bodyweight") || equipment.contains("calistenia") {
        return .BODYWEIGHT
    } else if equipment.contains("asist") || name.contains("asist") || equipment.contains("assisted") || name.contains("assisted") {
        return .ASSISTED
    } else {
        return .LOAD
    }
}
