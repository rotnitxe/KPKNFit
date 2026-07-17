import Foundation

// ─── WorkoutVisualModels ─────────────────────────────────────────────────────

internal enum ExerciseDrainOverlayChannelV2: String, Codable {
    case ENERGY, BACK, MUSCLE
}

internal struct ExerciseDrainOverlayItemV2: Codable {
    let label: String
    let delta: Int
    let channel: ExerciseDrainOverlayChannelV2
}

internal struct ExerciseDrainOverlayStateV2: Codable {
    let key: String
    let exerciseName: String
    let items: [ExerciseDrainOverlayItemV2]
}

internal struct WorkoutStageTransitionTargetV2: Codable {
    let exerciseId: String
    let order: Int
    let label: String
}

internal enum WorkoutSetCardVisualState: String, Codable {
    case FUTURE, COMPLETED, SKIPPED, ACTIVE
}

internal struct WorkoutSetPagerItem: Identifiable {
    let index: Int
    let label: String
    let state: WorkoutSetCardVisualState
    let isEditing: Bool
    let side: String?
    let pulseToken: UInt64?
    let isWarmupOrFeedback: Bool

    var id: String { "\(index)_\(side ?? "all")" }

    init(index: Int, label: String, state: WorkoutSetCardVisualState, isEditing: Bool, side: String? = nil, pulseToken: UInt64? = nil, isWarmupOrFeedback: Bool = false) {
        self.index = index
        self.label = label
        self.state = state
        self.isEditing = isEditing
        self.side = side
        self.pulseToken = pulseToken
        self.isWarmupOrFeedback = isWarmupOrFeedback
    }
}

internal let WORKOUT_WARMUP_BLUE = 0xFF448AFF

internal func workoutSetPagerAccent(
    state: WorkoutSetCardVisualState,
    isWarmupOrFeedback: Bool = false,
    sessionAccentColor: UInt32? = nil,
    primaryColor: UInt32,
    tertiaryColor: UInt32,
    outlineColor: UInt32,
    surfaceContainerHighestColor: UInt32
) -> UInt32 {
    if isWarmupOrFeedback { return UInt32(WORKOUT_WARMUP_BLUE) }
    if let accent = sessionAccentColor { return accent }
    switch state {
    case .ACTIVE: return primaryColor
    case .COMPLETED: return tertiaryColor
    case .SKIPPED: return outlineColor
    case .FUTURE: return surfaceContainerHighestColor
    }
}

internal func resolveWorkoutHeaderGroupLabel(
    partName: String?,
    type: String?,
    category: String?
) -> String? {
    let trimmedPart = partName?.trimmingCharacters(in: .whitespacesAndNewlines)
    let explicitPart: String? = {
        guard let v = trimmedPart, !v.isEmpty else { return nil }
        let lowered = v.lowercased()
        if lowered == "sesión" || lowered == "sesion" || lowered == "sesión principal" || lowered == "sesion principal" { return nil }
        return v
    }()
    let raw = explicitPart ?? {
        let t = type?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let t = t, !t.isEmpty { return t }
        return category?.trimmingCharacters(in: .whitespacesAndNewlines)
    }()
    return normalizeWorkoutHeaderLabel(raw)
}

private func normalizeWorkoutHeaderLabel(_ raw: String?) -> String? {
    guard var value = raw?.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression), !value.isEmpty else { return nil }
    if value.lowercased() == "principales" { return "Principales" }
    if value.lowercased() == "principal" { return "Principal" }
    let letterChars = value.filter { $0.isLetter }
    if !letterChars.isEmpty {
        let upperRatio = Double(letterChars.filter { $0.isUppercase }.count) / Double(letterChars.count)
        if upperRatio >= 0.75 { return value.uppercased() }
    }
    let pattern = try! NSRegularExpression(pattern: "(?i)principales")
    value = pattern.stringByReplacingMatches(in: value, range: NSRange(value.startIndex..., in: value), withTemplate: "Principales")
    let pattern2 = try! NSRegularExpression(pattern: "(?i)principal")
    value = pattern2.stringByReplacingMatches(in: value, range: NSRange(value.startIndex..., in: value), withTemplate: "Principal")
    return value
}

// ─── WorkoutSessionContracts ─────────────────────────────────────────────────

internal enum RestTimerKind: String, Codable {
    case STANDARD, SUPERSET_INTRA, SUPERSET_ROUND, WARMUP, BETWEEN_SIDES
}

internal struct WorkoutSetDraft: Codable {
    var weightText: String?
    var valueText: String?
    var intensityText: String?
    var loadMode: LoadModeV2?
    var selectedSide: String?
    var partialReps: Int?
    var reachedFailure: Bool?
    var voiceFields: Set<WorkoutVoiceField>
    var isDirty: Bool
    var updatedAtMs: UInt64
    var rom: Int?
    var assistedReps: Int?

    init(
        weightText: String? = nil,
        valueText: String? = nil,
        intensityText: String? = nil,
        loadMode: LoadModeV2? = nil,
        selectedSide: String? = nil,
        partialReps: Int? = nil,
        reachedFailure: Bool? = nil,
        voiceFields: Set<WorkoutVoiceField> = [],
        isDirty: Bool = false,
        updatedAtMs: UInt64 = UInt64(Date().timeIntervalSince1970 * 1000),
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

internal struct WorkoutRestModalState: Codable {
    var exerciseId: String?
    var exerciseName: String
    var kind: RestTimerKind
    var plannedSeconds: Int
    var suggestedSeconds: Int
    var activeSeconds: Int
    var endsAtMs: UInt64
    var isManualOverride: Bool
    var notificationsEnabled: Bool
    var exactAlarmGranted: Bool
    var soundReady: Bool
    var skipCurrentExerciseOnFinish: Bool

    init(
        exerciseId: String? = nil,
        exerciseName: String = "",
        kind: RestTimerKind = .STANDARD,
        plannedSeconds: Int = 0,
        suggestedSeconds: Int = 0,
        activeSeconds: Int = 0,
        endsAtMs: UInt64 = 0,
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

internal func workoutSetKey(exerciseId: String, setIdx: Int, side: String? = nil) -> String {
    switch side {
    case "left": return "\(exerciseId)_\(setIdx)_L"
    case "right": return "\(exerciseId)_\(setIdx)_R"
    default: return "\(exerciseId)_\(setIdx)"
    }
}

internal func workoutSetContextKey(exerciseId: String, setIdx: Int, tagId: String?) -> String {
    let raw = tagId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let cleanTag = (raw != nil && !raw!.isEmpty) ? raw! : "na"
    return "\(exerciseId)|\(setIdx)|\(cleanTag)"
}

internal func workoutExerciseContextKey(exerciseId: String, tagId: String?) -> String {
    let raw = tagId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let cleanTag = (raw != nil && !raw!.isEmpty) ? raw! : "na"
    return "\(exerciseId)|\(cleanTag)"
}

internal func resolvePersistedLoadModeForSet(
    exerciseId: String,
    setIdx: Int,
    tagId: String?,
    persistedLoadModeBySet: [String: LoadModeV2],
    persistedLoadModeByExercise: [String: LoadModeV2]
) -> LoadModeV2? {
    for candidateIdx in stride(from: setIdx, through: 0, by: -1) {
        let baseKey = "\(exerciseId)_\(candidateIdx)"
        if let v = persistedLoadModeBySet[baseKey] { return v }
        if let v = persistedLoadModeBySet["\(baseKey)_L"] { return v }
        if let v = persistedLoadModeBySet["\(baseKey)_R"] { return v }
        let key = workoutSetContextKey(exerciseId: exerciseId, setIdx: candidateIdx, tagId: tagId)
        if let v = persistedLoadModeBySet[key] { return v }
    }
    let exKey = workoutExerciseContextKey(exerciseId: exerciseId, tagId: tagId)
    if let v = persistedLoadModeByExercise[exKey] { return v }
    if let v = persistedLoadModeByExercise[exerciseId] { return v }
    return nil
}

internal func resolveEffectiveLoadMode(
    draftLoadMode: LoadModeV2?,
    persistedLoadMode: LoadModeV2?,
    plannedLoadMode: LoadModeV2?,
    defaultCatalogMode: LoadModeV2?
) -> LoadModeV2 {
    return draftLoadMode ?? persistedLoadMode ?? plannedLoadMode ?? defaultCatalogMode ?? .LOAD
}

internal func isWorkoutPulseActive(pulseToken: UInt64?, nowMs: UInt64, ttlMs: UInt64 = 2200) -> Bool {
    guard let token = pulseToken else { return false }
    return nowMs >= token && nowMs - token <= ttlMs
}

// ─── WorkoutFeedbackModels ───────────────────────────────────────────────────

internal struct SetAdvancedFeedback {
    var rir: Int?
    var reachedFailure: Bool
    var isFailedSet: Bool
    var failureReason: String?
    var executionError: Bool
    var isPartial: Bool
    var partialReps: Int?
    var dropSets: [DropSetData]
    var restPauses: [RestPauseData]
    var skipped: Bool
    var superSetWithExerciseId: String?
    var isWarmup: Bool
    var actualIntensityMode: IntensityMode?
    var actualIntensityValue: Double?
    var timerElapsedSeconds: Int?
    var timerTargetSeconds: Int?
    var rom: Int?
    var assistedReps: Int?

    init(rir: Int? = nil, reachedFailure: Bool = false, isFailedSet: Bool = false, failureReason: String? = nil, executionError: Bool = false, isPartial: Bool = false, partialReps: Int? = nil, dropSets: [DropSetData] = [], restPauses: [RestPauseData] = [], skipped: Bool = false, superSetWithExerciseId: String? = nil, isWarmup: Bool = false, actualIntensityMode: IntensityMode? = nil, actualIntensityValue: Double? = nil, timerElapsedSeconds: Int? = nil, timerTargetSeconds: Int? = nil, rom: Int? = nil, assistedReps: Int? = nil) {
        self.rir = rir
        self.reachedFailure = reachedFailure
        self.isFailedSet = isFailedSet
        self.failureReason = failureReason
        self.executionError = executionError
        self.isPartial = isPartial
        self.partialReps = partialReps
        self.dropSets = dropSets
        self.restPauses = restPauses
        self.skipped = skipped
        self.superSetWithExerciseId = superSetWithExerciseId
        self.isWarmup = isWarmup
        self.actualIntensityMode = actualIntensityMode
        self.actualIntensityValue = actualIntensityValue
        self.timerElapsedSeconds = timerElapsedSeconds
        self.timerTargetSeconds = timerTargetSeconds
        self.rom = rom
        self.assistedReps = assistedReps
    }
}

internal struct PostExerciseFeedback {
    var exerciseId: String
    var exerciseDbId: String?
    var canonicalExerciseId: String?
    var exerciseName: String
    var technicalQuality: Int
    var discomfortIds: [String]
    var notes: String?
    var perceivedIntensityRpe: Double?
    var perceivedFailure: Bool

    init(exerciseId: String, exerciseDbId: String? = nil, canonicalExerciseId: String? = nil, exerciseName: String, technicalQuality: Int, discomfortIds: [String] = [], notes: String? = nil, perceivedIntensityRpe: Double? = nil, perceivedFailure: Bool = false) {
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

internal enum PostExerciseFeedbackTarget {
    case single(exerciseId: String)
    case supersetGroup(groupId: String, exerciseIds: [String])
}

internal func backfillCompletedSetIntensityFromPostExerciseFeedback(
    completedSets: [String: CompletedSet],
    feedback: PostExerciseFeedback
) -> [String: CompletedSet] {
    let perceived = feedback.perceivedIntensityRpe?.clamped(to: 1.0...10.0)
    if perceived == nil && !feedback.perceivedFailure { return completedSets }
    let mode: IntensityMode = feedback.perceivedFailure ? .FAILURE : .RPE
    let value: Double? = feedback.perceivedFailure ? 10.0 : perceived
    var result = completedSets
    for (key, set) in completedSets {
        let belongsToExercise = key.hasPrefix("\(feedback.exerciseId)_")
        let hasRecordedIntensity = set.actualIntensityValue != nil || set.actualIntensityMode != nil || set.rpe != nil || set.rir != nil || set.isFailure
        if !belongsToExercise || hasRecordedIntensity { continue }
        result[key] = CompletedSet(
            id: set.id, weight: set.weight, reps: set.reps, timeSeconds: set.timeSeconds,
            rpe: feedback.perceivedFailure ? nil : value, rir: set.rir,
            isFailure: feedback.perceivedFailure, isFailedSet: set.isFailedSet, failureReason: set.failureReason,
            isPartial: set.isPartial, partialReps: set.partialReps, dropSets: set.dropSets, restPauses: set.restPauses,
            skipped: set.skipped, superSetWithExerciseId: set.superSetWithExerciseId,
            supersetId: set.supersetId, supersetRoundIndex: set.supersetRoundIndex,
            restAfterKind: set.restAfterKind, isWarmup: set.isWarmup, side: set.side,
            spinalScore: set.spinalScore, performanceMode: set.performanceMode,
            actualIntensityMode: mode, actualIntensityValue: value, debt: set.debt,
            contextProfileId: set.contextProfileId, tagId: set.tagId, subTagIds: set.subTagIds,
            setupProfileId: set.setupProfileId, machineBrand: set.machineBrand,
            homologatedResultV3: set.homologatedResultV3, rom: set.rom, assistedReps: set.assistedReps
        )
    }
    return result
}

internal func backfillCompletedSetIntensityFromPostExerciseFeedbacks(
    completedSets: [String: CompletedSet],
    feedbacks: [PostExerciseFeedback]
) -> [String: CompletedSet] {
    return feedbacks.reduce(completedSets) { current, feedback in
        backfillCompletedSetIntensityFromPostExerciseFeedback(completedSets: current, feedback: feedback)
    }
}

internal struct SessionClosingFeedback {
    var overallFatigue: Int
    var systemAdjustment: Int
    var muscularAdjustment: Int
    var structureAdjustment: Int
    var discomforts: [String]
    var clarityRating: Int
    var environmentTags: [String]
    var finalNeuralBattery: Int?
    var finalSpinalBattery: Int?
    var finalMuscularBattery: Int?
    var finalMuscleBatteries: [String: Int]
    var additionalDiscomfortNote: String?
    var stillPresentDiscomfortIds: [String]

    init(overallFatigue: Int = 5, systemAdjustment: Int = 0, muscularAdjustment: Int = 0, structureAdjustment: Int = 0, discomforts: [String] = [], clarityRating: Int = 5, environmentTags: [String] = [], finalNeuralBattery: Int? = nil, finalSpinalBattery: Int? = nil, finalMuscularBattery: Int? = nil, finalMuscleBatteries: [String: Int] = [:], additionalDiscomfortNote: String? = nil, stillPresentDiscomfortIds: [String] = []) {
        self.overallFatigue = overallFatigue
        self.systemAdjustment = systemAdjustment
        self.muscularAdjustment = muscularAdjustment
        self.structureAdjustment = structureAdjustment
        self.discomforts = discomforts
        self.clarityRating = clarityRating
        self.environmentTags = environmentTags
        self.finalNeuralBattery = finalNeuralBattery
        self.finalSpinalBattery = finalSpinalBattery
        self.finalMuscularBattery = finalMuscularBattery
        self.finalMuscleBatteries = finalMuscleBatteries
        self.additionalDiscomfortNote = additionalDiscomfortNote
        self.stillPresentDiscomfortIds = stillPresentDiscomfortIds
    }
}

internal struct ExerciseHistoryEntry {
    let date: String
    let sets: [CompletedSet]
    let e1rm: Double?
    let tag: String?
    let latestHistoryColor: HistoryColorV2?
    let latestMetricType: String?
    let latestMetricValue: Double?

    init(date: String, sets: [CompletedSet], e1rm: Double? = nil, tag: String? = nil, latestHistoryColor: HistoryColorV2? = nil, latestMetricType: String? = nil, latestMetricValue: Double? = nil) {
        self.date = date
        self.sets = sets
        self.e1rm = e1rm
        self.tag = tag
        self.latestHistoryColor = latestHistoryColor
        self.latestMetricType = latestMetricType
        self.latestMetricValue = latestMetricValue
    }
}

internal struct WeightSuggestion {
    let suggestedWeight: Double
    let reason: String
    let suggestedLoadMode: LoadModeV2?
    init(suggestedWeight: Double, reason: String, suggestedLoadMode: LoadModeV2? = nil) {
        self.suggestedWeight = suggestedWeight
        self.reason = reason
        self.suggestedLoadMode = suggestedLoadMode
    }
}

internal struct WorkoutLoadSuggestionUi {
    let suggestedWeight: Double
    let originalWeight: Double
    let isRecalculated: Bool
    let reason: String
    let source: WorkoutLoadSuggestionSource
    let suggestedLoadMode: LoadModeV2?
    init(suggestedWeight: Double, originalWeight: Double, isRecalculated: Bool = false, reason: String, source: WorkoutLoadSuggestionSource = .PROGRAM, suggestedLoadMode: LoadModeV2? = nil) {
        self.suggestedWeight = suggestedWeight
        self.originalWeight = originalWeight
        self.isRecalculated = isRecalculated
        self.reason = reason
        self.source = source
        self.suggestedLoadMode = suggestedLoadMode
    }
}

internal enum WorkoutLoadSuggestionSource: String, Codable {
    case PROGRAM, HISTORY, SESSION_ERM, MANUAL_BASE
}

internal struct SetAutoRegulation {
    let exerciseId: String
    let nextSetIdx: Int
    let adjustmentFactor: Double
    let adjustedWeight: Double
    let reason: String
}

// ─── WorkoutRestRecoveryModel ────────────────────────────────────────────────

internal struct RestRecoveryStatus {
    let recoveryFraction: Double
    let recoveryPercent: Int
    let difficultyTier: Int
    let isReady: Bool
}

internal enum WorkoutRestRecoveryModel {
    private static let READY_THRESHOLD = 0.75

    static func fromLastSet(elapsedSeconds: Int, completedSet: CompletedSet?, advanced: SetAdvancedFeedback? = nil) -> RestRecoveryStatus {
        let difficulty = estimateDifficultyTier(completedSet: completedSet, advanced: advanced)
        return calculate(elapsedSeconds: elapsedSeconds, difficultyTier: difficulty)
    }

    static func calculate(elapsedSeconds: Int, difficultyTier: Int) -> RestRecoveryStatus {
        let safeDifficulty = max(1, min(5, difficultyTier))
        let demand = 18.0 - (Double(5 - safeDifficulty) * 3.0)
        let minutes = max(0.0, Double(max(0, elapsedSeconds)) / 60.0)
        let recovery = min(1.05, max(0.0, (21.0 - (demand * pow(minutes, 0.8) * -0.7)) / 21.0))
        let percent = min(100, max(0, Int((recovery * 100.0).rounded())))
        return RestRecoveryStatus(recoveryFraction: recovery, recoveryPercent: percent, difficultyTier: safeDifficulty, isReady: recovery >= READY_THRESHOLD)
    }

    private static func estimateDifficultyTier(completedSet: CompletedSet?, advanced: SetAdvancedFeedback?) -> Int {
        var score: Int
        if advanced?.isFailedSet == true || advanced?.reachedFailure == true || completedSet?.isFailure == true || completedSet?.isFailedSet == true {
            score = 5
        } else {
            let effectiveRpe = resolveEffectiveRpe(completedSet: completedSet, advanced: advanced)
            switch effectiveRpe {
            case 9.5...: score = 5
            case 8.8..<9.5: score = 4
            case 7.8..<8.8: score = 3
            case 6.8..<7.8: score = 2
            default: score = 1
            }
        }
        var techniqueLoad = 0
        if advanced?.dropSets.isEmpty == false || completedSet?.dropSets.isEmpty == false { techniqueLoad += 1 }
        if advanced?.restPauses.isEmpty == false || completedSet?.restPauses.isEmpty == false { techniqueLoad += 1 }
        if advanced?.isPartial == true || completedSet?.isPartial == true { techniqueLoad += 1 }
        score += techniqueLoad
        return max(1, min(5, score))
    }

    private static func resolveEffectiveRpe(completedSet: CompletedSet?, advanced: SetAdvancedFeedback?) -> Double {
        let advancedFromMode: Double? = {
            switch advanced?.actualIntensityMode {
            case .RIR:
                if let rir = advanced?.rir { return 10.0 - Double(rir) }
                return advanced?.actualIntensityValue
            default:
                return advanced?.actualIntensityValue
            }
        }()
        if let v = advancedFromMode { return max(1.0, min(10.0, v)) }

        let setFromMode: Double? = {
            switch completedSet?.actualIntensityMode {
            case .RIR:
                if let rir = completedSet?.rir { return 10.0 - Double(rir) }
                return completedSet?.actualIntensityValue
            default:
                return completedSet?.actualIntensityValue
            }
        }()
        if let v = setFromMode { return max(1.0, min(10.0, v)) }

        if let rpe = completedSet?.rpe { return max(1.0, min(10.0, rpe)) }
        if let rir = advanced?.rir { return max(1.0, min(10.0, 10.0 - Double(rir))) }
        return 7.5
    }
}

// ─── WorkoutCoachMessages ────────────────────────────────────────────────────

internal struct CoachMessage {
    let key: String
    let title: String
    let body: String
    let severity: CoachSeverity
    let action: CoachAction?
}

internal enum CoachSeverity { case INFO, WARNING, DANGER, SUCCESS }
internal enum CoachAction { case REDUCE_INTENSITY, SKIP_EXERCISE, EXTEND_REST, STAY_THE_COURSE }

internal enum WorkoutCoachMessages {
    private enum DrainLevel { case XS, LOW, AVERAGE, HIGH }
    private enum ReadinessLevel { case XS, LOW, AVERAGE, HIGH }
    private enum SessionPhase { case EARLY, MID, LATE }

    private struct CoachKey: Hashable {
        let drain: DrainLevel
        let readiness: ReadinessLevel
        let phase: SessionPhase
    }

    private struct CoachMessageComponents {
        let severity: CoachSeverity
        let title: String
        let body: String
        let action: CoachAction?
    }

    private static let COACH_MESSAGES: [CoachKey: CoachMessage] = {
        var map: [CoachKey: CoachMessage] = [:]
        let drainLevels: [DrainLevel] = [.XS, .LOW, .AVERAGE, .HIGH]
        let readinessLevels: [ReadinessLevel] = [.XS, .LOW, .AVERAGE, .HIGH]
        let phases: [SessionPhase] = [.EARLY, .MID, .LATE]
        for drain in drainLevels {
            for readiness in readinessLevels {
                for phase in phases {
                    let c = resolve(drain: drain, readiness: readiness, phase: phase)
                    let key = CoachKey(drain: drain, readiness: readiness, phase: phase)
                    let name = "\(drain)_\(readiness)_\(phase)"
                    map[key] = CoachMessage(key: name, title: c.title, body: c.body, severity: c.severity, action: c.action)
                }
            }
        }
        return map
    }()

    private static func resolve(drain: DrainLevel, readiness: ReadinessLevel, phase: SessionPhase) -> CoachMessageComponents {
        let isHighDrain = drain == .HIGH || drain == .AVERAGE
        let isLowReadiness = readiness == .XS || readiness == .LOW
        let isLateSession = phase == .LATE

        if isHighDrain && isLowReadiness && isLateSession {
            return CoachMessageComponents(severity: .DANGER, title: "Cuidado: fatiga acumulada + baja disponibilidad", body: "Has acumulado fatiga significativa y tu readiness está baja. Considera terminar ejercicios principales aquí y guardar energía.", action: .REDUCE_INTENSITY)
        }
        if isHighDrain && isLowReadiness && !isLateSession {
            return CoachMessageComponents(severity: .WARNING, title: "Fatiga en aumento, readiness limitada", body: "El sistema nervioso está trabajando fuerte y la recuperación de hoy no es óptima. Prioriza las series más importantes.", action: .REDUCE_INTENSITY)
        }
        if drain == .HIGH && readiness == .AVERAGE && isLateSession {
            return CoachMessageComponents(severity: .WARNING, title: "Cerca del límite — serie final de esta fase", body: "Estás drenado pero tu cuerpo responde. Si la próxima serie se siente pesada, no insistas. Termina fuerte pero seguro.", action: .REDUCE_INTENSITY)
        }
        if drain == .HIGH && readiness == .HIGH && isLateSession {
            return CoachMessageComponents(severity: .INFO, title: "Drenado pero listo", body: "Tu batería está baja pero tu readiness es alta. Recuperas bien. La última serie cuenta.", action: .STAY_THE_COURSE)
        }
        if drain == .HIGH && readiness == .HIGH && !isLateSession {
            return CoachMessageComponents(severity: .INFO, title: "Buen ritmo de trabajo", body: "Estás drenando pero tu recuperación es excelente. Sigue con el plan, tu cuerpo responde bien.", action: .STAY_THE_COURSE)
        }
        if drain == .AVERAGE && readiness == .HIGH && isLateSession {
            return CoachMessageComponents(severity: .SUCCESS, title: "Excelente sesión", body: "Drenado moderado con readiness alta. Estás navegando la sesión con eficiencia. ¡A la carga!", action: .STAY_THE_COURSE)
        }
        if drain == .AVERAGE && readiness == .LOW {
            return CoachMessageComponents(severity: .WARNING, title: "Recuperación limitada detectada", body: "Tu cuerpo no está al 100% hoy. Ajusta la intensidad si algo no se siente bien.", action: .REDUCE_INTENSITY)
        }
        if drain == .LOW && readiness == .LOW {
            return CoachMessageComponents(severity: .INFO, title: "Sesión ligera — oportunidad", body: "Baja fatiga y readiness moderada. Ideal para trabajar técnica o accesibilidad. ¡Aprovecha!", action: .STAY_THE_COURSE)
        }
        if drain == .LOW && readiness == .HIGH {
            return CoachMessageComponents(severity: .SUCCESS, title: "Día óptimo para max effort", body: "Baja fatiga y máxima disponibilidad. Ideal para series pesadas o probar RMs.", action: .STAY_THE_COURSE)
        }
        if drain == .XS && readiness == .HIGH {
            return CoachMessageComponents(severity: .SUCCESS, title: "¡Energía al máximo!", body: "Sesión nueva, cuerpo fresco. Perfecto para series pesadas o explorar nuevos límites.", action: .STAY_THE_COURSE)
        }
        if drain == .XS && readiness == .XS {
            return CoachMessageComponents(severity: .WARNING, title: "Disponibilidad reducida", body: "Readiness muy baja. Si es posible, considera reprogramar sesiones pesadas para otro día.", action: .SKIP_EXERCISE)
        }
        if drain == .HIGH && readiness == .AVERAGE && !isLateSession {
            return CoachMessageComponents(severity: .INFO, title: "Mitad de sesión — evalúa cómo vas", body: "Has drenado bastante. Si aún te quedan series pesadas, considera reducir un 5-10% en las últimas.", action: .EXTEND_REST)
        }
        return CoachMessageComponents(severity: .INFO, title: "Seguimiento en tiempo real", body: "La sesión ajusta el descanso según tu fatiga registrada.", action: nil)
    }

    static func getMessage(weightedDrainPct: Double, readinessScore: Int?, sessionProgress: Double) -> CoachMessage {
        let drainLevel: DrainLevel = {
            if weightedDrainPct >= 8.0 { return .HIGH }
            if weightedDrainPct >= 4.0 { return .AVERAGE }
            if weightedDrainPct >= 1.5 { return .LOW }
            return .XS
        }()
        let readinessLevel: ReadinessLevel = {
            guard let score = readinessScore else { return .HIGH }
            switch score {
            case 80...100: return .HIGH
            case 60..<80: return .AVERAGE
            case 40..<60: return .LOW
            default: return .XS
            }
        }()
        let phase: SessionPhase = {
            if sessionProgress < 0.33 { return .EARLY }
            if sessionProgress < 0.66 { return .MID }
            return .LATE
        }()
        let key = CoachKey(drain: drainLevel, readiness: readinessLevel, phase: phase)
        return COACH_MESSAGES[key] ?? CoachMessage(key: "default", title: "Seguimiento en tiempo real", body: "La sesión ajusta el descanso según tu fatiga registrada.", severity: .INFO, action: nil)
    }

    static func getReadinessScore(neural: Int?, spinal: Int?, muscular: Int?) -> Int? {
        let values = [neural, spinal, muscular].compactMap { $0 }
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / values.count
    }
}

// ─── WorkoutLoadSuggestionRules ──────────────────────────────────────────────

internal enum WorkoutLoadSuggestionRules {
    static func fatigueFactorForPriorCompletedSets(priorCompletedCount: Int) -> Double {
        switch max(0, priorCompletedCount) {
        case 0: return 1.0
        case 1: return 0.8
        case 2: return 0.6
        default: return 0.5
        }
    }
}

// ─── WorkoutUiCommon ─────────────────────────────────────────────────────────

internal extension Double {
    func toTrimmedNumberString() -> String {
        let rounded = (self * 10).rounded() / 10.0
        if rounded == rounded.rounded() {
            return "\(Int(rounded))"
        }
        return String(format: "%.1f", rounded)
    }
}

internal func formatSignedDelta(_ value: Double, suffix: String = "") -> String {
    let absValue = abs(value)
    let base = absValue.toTrimmedNumberString()
    let unit = suffix.isEmpty ? "" : suffix
    if value > 0 { return "+\(base)\(unit)" }
    if value < 0 { return "-\(base)\(unit)" }
    return "0\(unit)"
}

internal func formatTime(_ seconds: Int) -> String {
    let m = seconds / 60
    let s = seconds % 60
    if m > 0 { return "\(m):\(String(format: "%02d", s))" }
    return "\(s)s"
}

internal func formatElapsed(_ seconds: Int) -> String {
    let hours = seconds / 3600
    let minutes = (seconds % 3600) / 60
    let secs = seconds % 60
    if hours > 0 { return String(format: "%d:%02d:%02d", hours, minutes, secs) }
    return String(format: "%02d:%02d", minutes, secs)
}

internal func buildWorkoutAchievementMessage(
    homologated: HomologatedPerformanceResult?,
    showPRsInWorkout: Bool
) -> String? {
    guard let h = homologated else { return nil }
    if !showPRsInWorkout {
        if let r = h.suggestionReason, !r.isEmpty { return r }
        return nil
    }
    let metric = h.metricValue.toTrimmedNumberString()
    let label = h.metricType.isEmpty ? "Rendimiento" : h.metricType
    if h.isGlobalPr { return "PR global · \(label) \(metric)" }
    if h.isContextPr { return "PR contextual · \(label) \(metric)" }
    if let reason = h.suggestionReason, !reason.isEmpty { return reason }
    return nil
}

// ─── WorkoutAdaptiveRest ─────────────────────────────────────────────────────

internal enum WorkoutAdaptiveRest {
    private static let MIN_REST = 45
    private static let MAX_REST = 360
    private static let MIN_FACTOR = 0.75
    private static let MAX_FACTOR = 2.10

    enum ExerciseType: String, Codable {
        case COMPOUND, ISOLATION, UNKNOWN
    }

    struct AdaptiveRestContext {
        let advanced: SetAdvancedFeedback
        let setDrain: SetDrain?
        let effectiveRpe: Double?
        let sessionProgress: Double?
        let exerciseType: ExerciseType
        let isSuperset: Bool
        let rom: Int?
        let sessionPaceFactor: Double?

        init(advanced: SetAdvancedFeedback, setDrain: SetDrain? = nil, effectiveRpe: Double? = nil, sessionProgress: Double? = nil, exerciseType: ExerciseType = .UNKNOWN, isSuperset: Bool = false, rom: Int? = nil, sessionPaceFactor: Double? = nil) {
            self.advanced = advanced
            self.setDrain = setDrain
            self.effectiveRpe = effectiveRpe
            self.sessionProgress = sessionProgress
            self.exerciseType = exerciseType
            self.isSuperset = isSuperset
            self.rom = rom
            self.sessionPaceFactor = sessionPaceFactor
        }
    }

    static func compute(baseRestSeconds: Int, advanced: SetAdvancedFeedback) -> Int {
        return compute(baseRestSeconds: baseRestSeconds, context: AdaptiveRestContext(advanced: advanced))
    }

    static func compute(baseRestSeconds: Int, context: AdaptiveRestContext) -> Int {
        var factor = techniqueFactor(context.advanced)
        factor *= augeDrainFactor(context.setDrain)
        factor *= rpeFactor(context.effectiveRpe)
        factor *= sessionProgressFactor(context.sessionProgress)
        factor *= exerciseTypeFactor(context.exerciseType, context.isSuperset)
        factor *= romFactor(context.rom)
        if let pace = context.sessionPaceFactor { factor *= pace }
        let boundedFactor = max(MIN_FACTOR, min(MAX_FACTOR, factor))
        return max(MIN_REST, min(MAX_REST, Int(Double(baseRestSeconds) * boundedFactor)))
    }

    private static func techniqueFactor(_ advanced: SetAdvancedFeedback) -> Double {
        var factor = 1.0
        if advanced.reachedFailure { factor *= 1.30 }
        if advanced.isFailedSet { factor *= 1.20 }
        if advanced.isPartial { factor *= 1.15 }
        if !advanced.restPauses.isEmpty { factor *= 1.20 }
        if !advanced.dropSets.isEmpty { factor *= 1.10 }
        return factor
    }

    private static func augeDrainFactor(_ setDrain: SetDrain?) -> Double {
        guard let drain = setDrain else { return 1.0 }
        let weighted = (drain.cnsDrainPct * 0.45) + (drain.muscularDrainPct * 0.25) + (drain.spinalDrainPct * 0.30)
        switch weighted {
        case 8.0...: return 1.35
        case 6.0..<8.0: return 1.27
        case 4.0..<6.0: return 1.20
        case 2.5..<4.0: return 1.12
        case 1.5..<2.5: return 1.06
        default: return 1.0
        }
    }

    private static func rpeFactor(_ effectiveRpe: Double?) -> Double {
        guard let rpe = effectiveRpe else { return 1.0 }
        switch rpe {
        case 10.0...: return 1.16
        case 9.5..<10.0: return 1.12
        case 8.8..<9.5: return 1.08
        case 7.5..<8.8: return 1.03
        case ..<6.0: return 0.95
        default: return 1.0
        }
    }

    private static func sessionProgressFactor(_ sessionProgress: Double?) -> Double {
        guard let progress = sessionProgress else { return 1.0 }
        switch progress {
        case 0.85...: return 1.12
        case 0.66..<0.85: return 1.08
        case 0.40..<0.66: return 1.04
        case ..<0.20: return 0.96
        default: return 1.0
        }
    }

    private static func exerciseTypeFactor(_ type: ExerciseType, _ isSuperset: Bool) -> Double {
        let base: Double
        switch type {
        case .COMPOUND: base = 1.08
        case .ISOLATION: base = 0.96
        case .UNKNOWN: base = 1.0
        }
        return isSuperset ? base * 1.06 : base
    }

    private static func romFactor(_ rom: Int?) -> Double {
        guard let r = rom else { return 1.0 }
        if r < 50 { return 0.92 }
        if r < 60 { return 0.95 }
        if r < 80 { return 1.0 }
        return 1.02
    }

    static func computeSessionPaceFactor(elapsedMs: UInt64, targetMinutes: Int?, completedSets: Int, totalSets: Int) -> Double? {
        guard let target = targetMinutes, totalSets > 0, completedSets > 0 else { return nil }
        let elapsedMin = Double(elapsedMs) / 60000.0
        let expectedMin = Double(target) * (Double(completedSets) / Double(totalSets))
        guard expectedMin > 0 else { return nil }
        let ratio = elapsedMin / expectedMin
        switch ratio {
        case ..<0.70: return 0.88
        case 0.70..<0.85: return 0.93
        case 0.85..<1.15: return 1.0
        case 1.15..<1.30: return 1.07
        default: return 1.14
        }
    }
}

// ─── WorkoutEditingRules ─────────────────────────────────────────────────────

internal enum WorkoutLiveEditPersistenceScope: String {
    case SESSION_ONLY, PERMANENT_ALLOWED
}

internal struct WorkoutEditingState {
    let setKey: String
    let exerciseId: String
    let setIdx: Int
    let side: String?
}

// ─── WorkoutStepRules ────────────────────────────────────────────────────────

internal enum WorkoutStepType: String, Codable {
    case MOBILITY, WARMUP, WORKING_SET
}

internal struct WorkoutStep: Identifiable {
    let type: WorkoutStepType
    let exerciseId: String
    let exerciseName: String
    let stepKey: String
    let setIndex: Int?
    let warmupSetId: String?
    let mobilitySeriesId: String?
    let side: String?
    let supersetGroupId: String?
    let supersetRoundIndex: Int?
    let mobilitySeries: [MobilitySeries]
    let restAfterKind: RestTimerKind

    var id: String { stepKey.isEmpty ? "\(exerciseId)_\(type.rawValue)_\(setIndex ?? 0)" : stepKey }

    init(type: WorkoutStepType, exerciseId: String, exerciseName: String, stepKey: String = "", setIndex: Int? = nil, warmupSetId: String? = nil, mobilitySeriesId: String? = nil, side: String? = nil, supersetGroupId: String? = nil, supersetRoundIndex: Int? = nil, mobilitySeries: [MobilitySeries] = [], restAfterKind: RestTimerKind = .STANDARD) {
        self.type = type
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.stepKey = stepKey
        self.setIndex = setIndex
        self.warmupSetId = warmupSetId
        self.mobilitySeriesId = mobilitySeriesId
        self.side = side
        self.supersetGroupId = supersetGroupId
        self.supersetRoundIndex = supersetRoundIndex
        self.mobilitySeries = mobilitySeries
        self.restAfterKind = restAfterKind
    }
}

// ─── WorkoutUnsavedChangesRules ──────────────────────────────────────────────

internal enum WorkoutPendingSetAction {
    case navigate(setIdx: Int)
    case edit(setIdx: Int, side: String?)
}

internal func pendingSetNavigationAction(hasPendingDraftChanges: Bool, activeSetIdx: Int, targetSetIdx: Int) -> WorkoutPendingSetAction? {
    if targetSetIdx == activeSetIdx { return nil }
    if hasPendingDraftChanges { return .navigate(setIdx: targetSetIdx) }
    return nil
}

internal func pendingSetEditAction(hasPendingDraftChanges: Bool, isAlreadyEditingCurrentSet: Bool, targetSetIdx: Int, side: String? = nil) -> WorkoutPendingSetAction? {
    if isAlreadyEditingCurrentSet { return nil }
    if hasPendingDraftChanges { return .edit(setIdx: targetSetIdx, side: side) }
    return nil
}

// ─── WorkoutVoiceModels ──────────────────────────────────────────────────────

internal enum WorkoutVoiceField: String, Codable {
    case WEIGHT, VALUE, INTENSITY, SIDE, FAILURE
}

internal enum WorkoutVoiceIntensityKind: String, Codable {
    case RPE, RIR, PERCENT_RM
}

internal enum WorkoutVoiceUiState: Equatable {
    case idle
    case listening(exerciseId: String, setIdx: Int, side: String?, partialText: String, isReady: Bool)
    case confirmation(exerciseId: String, setIdx: Int, side: String?, interpretation: WorkoutVoiceInterpretation)
    case applied(exerciseId: String, setIdx: Int, side: String?, interpretation: WorkoutVoiceInterpretation, message: String)
    case error(exerciseId: String, setIdx: Int, side: String?, message: String)
}

internal struct WorkoutVoiceInterpretation: Equatable {
    let transcript: String
    let weightKg: Double?
    let metricValue: Int?
    let intensityValue: Double?
    let intensityKind: WorkoutVoiceIntensityKind?
    let side: String?
    let reachedFailure: Bool
    let fields: Set<WorkoutVoiceField>

    init(transcript: String, weightKg: Double? = nil, metricValue: Int? = nil, intensityValue: Double? = nil, intensityKind: WorkoutVoiceIntensityKind? = nil, side: String? = nil, reachedFailure: Bool = false, fields: Set<WorkoutVoiceField> = []) {
        self.transcript = transcript
        self.weightKg = weightKg
        self.metricValue = metricValue
        self.intensityValue = intensityValue
        self.intensityKind = intensityKind
        self.side = side
        self.reachedFailure = reachedFailure
        self.fields = fields
    }
}

// ─── WorkoutPlanDeviationSupport ─────────────────────────────────────────────

internal enum WorkoutPlanDeviationSupport {
    static func detect(
        exerciseId: String,
        exerciseName: String,
        setIdx: Int,
        plannedSet: ExerciseSet,
        actualWeight: Double,
        actualReps: Int,
        advanced: SetAdvancedFeedback,
        suggestedWeight: Double?
    ) -> [PlanDeviation] {
        var deviations: [PlanDeviation] = []
        let targetWeight = suggestedWeight ?? 0.0
        if targetWeight > 0 && actualWeight > 0 {
            let ratio = actualWeight / targetWeight
            if ratio > 1.15 {
                deviations.append(PlanDeviation(exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx, type: .WEIGHT_HIGH, detail: "+\(String(format: "%.0f", (ratio - 1) * 100))% del sugerido"))
            } else if ratio < 0.85 {
                deviations.append(PlanDeviation(exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx, type: .WEIGHT_LOW, detail: "-\(String(format: "%.0f", (1 - ratio) * 100))% del sugerido"))
            }
        }
        if let targetReps = plannedSet.targetReps, targetReps > 0, actualReps > 0 {
            if actualReps > targetReps + 3 {
                deviations.append(PlanDeviation(exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx, type: .REPS_HIGH, detail: "\(actualReps) vs \(targetReps) objetivo"))
            } else if actualReps < targetReps - 3 {
                deviations.append(PlanDeviation(exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx, type: .REPS_LOW, detail: "\(actualReps) vs \(targetReps) objetivo"))
            }
        }
        if advanced.reachedFailure && !plannedSet.isFailure {
            deviations.append(PlanDeviation(exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx, type: .UNPLANNED_FAILURE, detail: "Fallo no programado"))
        }
        if !advanced.dropSets.isEmpty && plannedSet.dropSets.isEmpty {
            deviations.append(PlanDeviation(exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx, type: .UNPLANNED_DROPSET, detail: "Dropset no programado"))
        }
        if !advanced.restPauses.isEmpty && plannedSet.restPauses.isEmpty {
            deviations.append(PlanDeviation(exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx, type: .UNPLANNED_REST_PAUSE, detail: "Rest-pause no programado"))
        }
        return deviations
    }
}

// ─── WorkoutAutoRegulation ───────────────────────────────────────────────────

internal enum WorkoutAutoRegulation {
    private static let MIN_FACTOR = 0.60
    private static let MAX_FACTOR = 1.10

    static func computeAdjustmentFactor(
        weightedDrainPct: Double,
        effectiveRpe: Double,
        reachedFailure: Bool,
        isFailedSet: Bool,
        isPartial: Bool,
        sessionProgress: Double
    ) -> Double {
        var factor = 1.0

        let drainFactor: Double
        switch weightedDrainPct {
        case 10.0...: drainFactor = -0.10
        case 7.0..<10.0: drainFactor = -0.07
        case 4.5..<7.0: drainFactor = -0.04
        case 2.5..<4.5: drainFactor = -0.02
        case 1.0..<2.5: drainFactor = -0.01
        default: drainFactor = 0.0
        }
        factor += drainFactor

        let rpeFactor: Double
        switch effectiveRpe {
        case 11.0...: rpeFactor = -0.08
        case 10.5..<11.0: rpeFactor = -0.06
        case 10.0..<10.5: rpeFactor = -0.04
        case 9.5..<10.0: rpeFactor = -0.02
        case 8.5..<9.5: rpeFactor = -0.01
        case ..<7.0: rpeFactor = 0.02
        default: rpeFactor = 0.0
        }
        factor += rpeFactor

        if reachedFailure { factor -= 0.05 }
        if isFailedSet { factor -= 0.03 }
        if isPartial { factor -= 0.02 }

        let sessionFactor: Double
        switch sessionProgress {
        case 0.80...: sessionFactor = -0.03
        case 0.60..<0.80: sessionFactor = -0.02
        case 0.40..<0.60: sessionFactor = -0.01
        default: sessionFactor = 0.0
        }
        factor += sessionFactor

        return max(MIN_FACTOR, min(MAX_FACTOR, factor))
    }

    static func buildReason(factor: Double, weightedDrainPct: Double, effectiveRpe: Double, reachedFailure: Bool) -> String {
        var parts: [String] = []
        if reachedFailure { parts.append("Fallo") }
        if weightedDrainPct >= 5.0 { parts.append("Fatiga acumulada") }
        let detail = parts.joined(separator: " · ")
        if factor < 0.95 {
            var msg = "Ajuste de carga"
            if !detail.isEmpty { msg += " · \(detail)" }
            msg += " · −\(((1 - factor) * 100).rounded())%"
            return msg
        }
        if factor > 1.02 { return "Ajuste de carga · Recuperación buena · +\(((factor - 1) * 100).rounded())%" }
        return "Sin ajuste de carga"
    }

    private static let READINESS_REDUCTION_THRESHOLD = 70
    private static let READINESS_SEVERE_THRESHOLD = 50
    private static let READINESS_MIN_FACTOR = 0.70
    private static let READINESS_SEVERE_FACTOR = 0.85
    private static let READINESS_RECOVERY_FACTOR = 1.05

    static func computeReadinessAdjustmentFactor(
        readinessNeural: Int?,
        readinessSpinal: Int?,
        readinessMuscular: Int?,
        readinessPerMuscle: [String: Int]?,
        involvedMuscleIds: [String]
    ) -> Double {
        let perMuscleValues: Int? = readinessPerMuscle.flatMap { overrides in
            let relevant = involvedMuscleIds.compactMap { overrides[$0] }
            return relevant.isEmpty ? nil : relevant.min()
        }
        let allValues = [readinessNeural, readinessSpinal, readinessMuscular, perMuscleValues].compactMap { $0 }
        guard let readiness = allValues.map(Double.init).min() else { return 1.0 }
        let clamped = max(0.0, min(100.0, readiness))

        if clamped < Double(READINESS_SEVERE_THRESHOLD) {
            let t = max(0.0, min(1.0, clamped / Double(READINESS_SEVERE_THRESHOLD)))
            return max(READINESS_MIN_FACTOR, min(READINESS_SEVERE_FACTOR, READINESS_MIN_FACTOR + (READINESS_SEVERE_FACTOR - READINESS_MIN_FACTOR) * t))
        }
        if clamped < Double(READINESS_REDUCTION_THRESHOLD) {
            let t = max(0.0, min(1.0, (clamped - Double(READINESS_SEVERE_THRESHOLD)) / Double(READINESS_REDUCTION_THRESHOLD - READINESS_SEVERE_THRESHOLD)))
            return max(READINESS_SEVERE_FACTOR, min(1.0, READINESS_SEVERE_FACTOR + (1.0 - READINESS_SEVERE_FACTOR) * t))
        }
        if clamped >= 90 { return Double(READINESS_RECOVERY_FACTOR) }
        return 1.0
    }

    static func buildReadinessReason(factor: Double, readinessValue: Int?) -> String {
        if factor == 1.0 { return "" }
        if factor < 0.85, let v = readinessValue { return "Readiness \(v) · −\(((1 - factor) * 100).rounded())%" }
        if factor < 1.0 { return "Readiness \(readinessValue.map { "\($0)" } ?? "baja")" }
        if factor > 1.0 { return "Readiness \(readinessValue.map { "\($0)" } ?? "alta") · +\(((factor - 1) * 100).rounded())%" }
        return ""
    }
}

// ─── Helper ──────────────────────────────────────────────────────────────────

internal extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        return min(max(self, range.lowerBound), range.upperBound)
    }
}

internal func applyAdvancedFeedback(base: CompletedSet, advanced: SetAdvancedFeedback) -> CompletedSet {
    return CompletedSet(
        id: base.id, weight: base.weight, reps: base.reps, timeSeconds: base.timeSeconds,
        rpe: base.rpe, rir: advanced.rir ?? base.rir,
        isFailure: advanced.reachedFailure, isFailedSet: advanced.isFailedSet || advanced.executionError,
        failureReason: advanced.failureReason ?? (advanced.executionError ? "execution_error" : nil),
        isPartial: advanced.isPartial, partialReps: advanced.partialReps,
        dropSets: advanced.dropSets.isEmpty ? base.dropSets : advanced.dropSets,
        restPauses: advanced.restPauses.isEmpty ? base.restPauses : advanced.restPauses,
        skipped: advanced.skipped, superSetWithExerciseId: advanced.superSetWithExerciseId ?? base.superSetWithExerciseId,
        supersetId: base.supersetId, supersetRoundIndex: base.supersetRoundIndex,
        restAfterKind: base.restAfterKind, isWarmup: advanced.isWarmup, side: base.side,
        spinalScore: base.spinalScore, performanceMode: base.performanceMode,
        actualIntensityMode: advanced.actualIntensityMode ?? base.actualIntensityMode,
        actualIntensityValue: advanced.actualIntensityValue ?? base.actualIntensityValue,
        debt: base.debt, contextProfileId: base.contextProfileId, tagId: base.tagId,
        subTagIds: base.subTagIds, setupProfileId: base.setupProfileId, machineBrand: base.machineBrand,
        homologatedResultV3: base.homologatedResultV3, rom: advanced.rom ?? base.rom,
        assistedReps: advanced.assistedReps ?? base.assistedReps
    )
}

internal func calculateUnifiedSessionEffortSignal(sets: [CompletedSet]) -> Double {
    let effectiveSets = sets.filter { !$0.isWarmup && isSetEffective($0) }
    guard !effectiveSets.isEmpty else { return 7.0 }
    let signals = effectiveSets.map { set -> Double in
        var signal = getEffectiveRPE(set)
        if set.isFailure { signal += 0.6 }
        if !set.dropSets.isEmpty { signal += 0.4 }
        if !set.restPauses.isEmpty { signal += 0.5 }
        return max(1.0, min(12.0, signal))
    }
    let avg = signals.reduce(0, +) / Double(signals.count)
    return max(1.0, min(12.0, avg))
}

internal func isSetEffective(_ set: CompletedSet) -> Bool {
    return set.reps > 0 || (set.timeSeconds ?? 0) > 0
}

internal func getEffectiveRPE(_ set: CompletedSet) -> Double {
    if let rpe = set.rpe { return rpe }
    if let ir = set.actualIntensityValue { return ir }
    return 7.0
}

// ─── ExerciseMuscleInfo ──────────────────────────────────────────────────────

struct InvolvedMuscle: Codable {
    var muscle: String = ""
    var emphasis: String? = nil
    var role: MuscleRole = .PRIMARY
    var contribution: Double? = nil
}

struct ResistanceProfile: Codable {
    var description: String? = nil
}

struct AnatomicalConsideration: Codable {
    var description: String? = nil
}

struct CommonMistake: Codable {
    var description: String? = nil
}

struct Progression: Codable {
    var name: String? = nil
    var description: String? = nil
}

struct PeriodizationNote: Codable {
    var note: String? = nil
}

struct InjuryRisk: Codable {
    var level: String? = nil
    var description: String? = nil
}

struct ScoreJustification: Codable {
    var score: Double? = nil
    var justification: String? = nil
}

struct AiCoachAnalysis: Codable {
    var analysis: String? = nil
}

struct SetupDetails: Codable {
    var seatPosition: String? = nil
    var pinPosition: String? = nil
    var barWeightKg: Double? = nil
    var equipmentNotes: String? = nil
}

struct ExerciseMuscleInfo: Codable {
    var id: String = ""
    var name: String = ""
    var alias: String? = nil
    var description: String? = nil
    var involvedMuscles: [InvolvedMuscle] = []
    var equipment: String? = nil
    var category: String? = nil
    var type: String? = nil
    var force: String? = nil
    var chain: String? = nil
    var bodyPart: String? = nil
    var tier: String? = nil
    var isCustom: Bool = false
    var efc: Double? = nil
    var cnc: Double? = nil
    var ssc: Double? = nil
    var ttc: Double? = nil
    var axialLoadFactor: Double? = nil
    var technicalDifficulty: Double? = nil
    var coreInvolvement: String? = nil
    var bracingRecommended: Bool? = nil
    var strapsRecommended: Bool? = nil
    var resistanceProfile: ResistanceProfile? = nil
    var anatomicalConsiderations: [AnatomicalConsideration]? = nil
    var commonMistakes: [CommonMistake]? = nil
    var setupCues: [String]? = nil
    var executionCues: [String]? = nil
    var progressions: [Progression]? = nil
    var regressions: [Progression]? = nil
    var recommendedMobility: [String]? = nil
    var periodizationNotes: [PeriodizationNote]? = nil
    var functionalTransfer: String? = nil
    var sportsRelevance: [String]? = nil
    var injuryRisk: InjuryRisk? = nil
    var sfr: ScoreJustification? = nil
    var primeStars: ScoreJustification? = nil
    var bodybuildingScore: Double? = nil
    var communityOpinion: [String]? = nil
    var aiCoachAnalysis: AiCoachAnalysis? = nil
    var images: [String]? = nil
    var videos: [String]? = nil
    var setupDetails: SetupDetails? = nil
    var setupTime: Int? = nil
    var averageRestSeconds: Int? = nil
    var executionOptions: [String]? = nil
    var movementPattern: String? = nil
}

// ─── WorkoutTag / SubTag ─────────────────────────────────────────────────────

enum SubTagCategory: String, Codable {
    case MARCA, SETUP, TECNICA, LIBRE
}

struct WorkoutTag: Codable, Identifiable {
    var id: String = ""
    var name: String = ""
    var exerciseKey: String = ""
    var subTags: [WorkoutSubTag] = []
    var createdAtIso: String = ""
    var lastUsedAtIso: String = ""
    var usageCount: Int = 0
}

struct WorkoutSubTag: Codable, Identifiable {
    var id: String = ""
    var name: String = ""
    var category: SubTagCategory = .LIBRE
}

// ─── WorkoutFeatureFlags ─────────────────────────────────────────────────────

struct WorkoutFeatureFlags: Codable {
    var workoutV2UiShell: Bool = true
    var workoutV2SetCarousel: Bool = true
    var workoutV2LoadModes: Bool = true
    var workoutV2Homologation: Bool = true
    var workoutV2ContextMenu: Bool = true
    var workoutV2HeaderWidgets: Bool = true
    var workoutV3UnifiedFlow: Bool = true
}

// ─── Performance States ──────────────────────────────────────────────────────

struct ContextPerformanceStateV2: Codable {
    var contextKey: String = ""
    var ewma: Double = 0.0
    var mean: Double = 0.0
    var variance: Double = 0.0
    var bestScore: Double = 0.0
    var sampleCount: Int = 0
    var recentScores: [Double] = []
    var consecutiveGreenSessions: Int = 0
    var lastSuggestedLoad: Double? = nil
    var lastUpdatedAtIso: String? = nil
}

struct GlobalPerformanceStateV3: Codable {
    var globalKey: String = ""
    var ewma: Double = 0.0
    var mean: Double = 0.0
    var variance: Double = 0.0
    var bestScore: Double = 0.0
    var sampleCount: Int = 0
    var recentScores: [Double] = []
    var lastUpdatedAtIso: String? = nil
}

// ─── WorkoutHeaderWidgets ────────────────────────────────────────────────────

struct WorkoutHeaderWidgets: Codable {
    var showRmCalculator: Bool = false
    var showRealtimeRings: Bool = false
}

// ─── SetOutcomeV2 ────────────────────────────────────────────────────────────

public struct SetOutcomeV2: Codable {
    var contextKey: String = ""
    var loadMode: LoadModeV2 = .LOAD
    var unitMode: UnitModeV2 = .REPS
    var plannedTarget: Double? = nil
    var actualValue: Double = 0.0
    var actualIntensity: Double? = nil
    var debt: Double = 0.0
    var failedSet: Bool = false
    var reachedFailure: Bool = false
    var amrapOverride: Bool = false
    var techniques: [SetTechniqueV2] = []
    var metricType: String = ""
    var metricValue: Double = 0.0
    var estimatedRm: Double? = nil
    var trm: Double? = nil
    var globalPerformanceIndex: Double = 50.0
    var contextPercentile: Double = 50.0
    var globalPercentile: Double = 0.0
    var contextEwma: Double = 0.0
    var contextStdDev: Double = 0.0
    var globalEwma: Double = 0.0
    var globalStdDev: Double = 0.0
    var isContextPr: Bool = false
    var isGlobalPr: Bool = false
    var historyColor: HistoryColorV2 = .NEUTRAL
    var difficultySignal: DifficultySignalV2 = .MATCHED
    var suggestedNextLoad: Double? = nil
    var suggestedTargetSeconds: Int? = nil
    var suggestionReason: String? = nil
    var augeEquivalentLoad: Double = 0.0
    var augeEquivalentReps: Int = 0
    var suggestedLoadMode: LoadModeV2? = nil
}

// ─── VoiceSessionState ───────────────────────────────────────────────────────

enum VoicePipelineStage: String, Codable {
    case DISABLED, LISTENING, PROCESSING, CONFIRM_WAIT, TTS_SPEAKING, ERROR_RECOVERY
}

struct VoiceSessionState {
    var stage: VoicePipelineStage = .DISABLED
    var partialText: String = ""
    var lastInterpretation: WorkoutVoiceInterpretation? = nil
    var lastCommand: VoiceSessionCommand? = nil
    var errorMessage: String? = nil
    var duckHandle: String? = nil
    var consecutiveErrors: Int = 0
    var isListening: Bool { stage == .LISTENING }
    var isDucking: Bool { duckHandle != nil }
    var hasPendingConfirmation: Bool { stage == .CONFIRM_WAIT }
}

// ─── VoiceSessionCommand ─────────────────────────────────────────────────────

enum VoiceSessionCommand {
    case registerSet(WorkoutVoiceInterpretation)
    case confirm
    case cancel
    case skipExercise
    case skipSet
    case previousExercise
    case suggestWeight
    case restStatus
    case whatExercise
    case nextExercise
    case turnOffVoice
    case finishSession
    case cancelSession
    case logFeedback(technicalQuality: Int?, discomfortId: String?, perceivedIntensity: Double?, isSaveAction: Bool, exerciseSearchName: String?)
    case logFinalFeedback(notes: String?, discomfortId: String?, additionalDiscomfortNote: String?, neuralBattery: Int?, spinalBattery: Int?, isSaveAction: Bool)
    case unknown(String)
}

// ─── WorkoutContinuityTransitionTarget ───────────────────────────────────────

struct WorkoutContinuityTransitionTarget: Codable {
    var key: String = ""
    var eyebrow: String = ""
    var title: String = ""
    var body: String = ""
    var accentHex: String? = nil
}

// ─── MesocycleStressEMA ──────────────────────────────────────────────────────

enum StressTrend: String, Codable {
    case RISING, STABLE, FALLING
}

struct MesocycleStressEMA: Codable {
    var programId: String = ""
    var mesoIndex: Int = 0
    var emaValue: Double = 0.0
    var sessionCount: Int = 0
    var latestStressScore: Double? = nil
    var stressTrend: StressTrend = .STABLE
    var computedAtMs: Int64 = 0
}

// ─── ExerciseReadiness ───────────────────────────────────────────────────────
// ArticularBattery and ArticularBatteryState are defined in AugeModels.swift

struct ExerciseReadiness: Codable {
    var exerciseId: String = ""
    var exerciseName: String = ""
    var overallScore: Int = 0
    var muscularComponent: Int = 0
    var cnsComponent: Int = 0
    var spinalComponent: Int = 0
    var articularComponent: Int = 0
    var structuralComponent: Int = 0
    var relatedArticular: [ArticularBattery] = []
    var muscularWeight: Double = 0.0
    var cnsWeight: Double = 0.0
    var spinalWeight: Double = 0.0
    var articularWeight: Double = 0.0
    var setsPenaltyFactor: Double = 0.0
    var intensityPenaltyFactor: Double = 0.0
    var ermProximityFactor: Double = 0.0
    var patternId: String? = nil
    var involvedMuscleIds: [String] = []
    var limitingFactor: String? = nil
    var limitingDetail: String? = nil
}

// ─── MovementPatternReadiness ────────────────────────────────────────────────

struct MovementPatternReadiness: Codable {
    var patternId: String = ""
    var patternLabel: String = ""
    var overallScore: Int = 0
    var exerciseCount: Int = 0
    var totalSets: Int = 0
    var contributingMuscles: [String] = []
    var averageMuscleRecovery: Int = 0
}

// ─── SetAdjustmentSuggestion ─────────────────────────────────────────────────

struct SetAdjustmentSuggestion: Codable {
    var exerciseId: String = ""
    var setIndex: Int = 0
    var currentPlannedWeight: Double = 0.0
    var readinessScore: Int = 0
    var severityFactor: Double = 0.0
    var reductionPercent: Double = 0.0
    var suggestedWeight: Double = 0.0
    var averageErm: Double? = nil
    var reason: String = ""
    var suggestedLoadMode: LoadModeV2 = .LOAD
}

// ─── SessionEnergySummary ────────────────────────────────────────────────────

enum EnergyConfidence: String, Codable {
    case HIGH, MEDIUM, LOW
}

enum EnergyEstimateSource: String, Codable {
    case PLANNED, LIVE, FINAL
}

struct CalorieRange: Codable {
    var low: Int = 0
    var mid: Int = 0
    var high: Int = 0
}

struct ExerciseEnergyContribution: Codable {
    var exerciseId: String = ""
    var exerciseDbId: String? = nil
    var exerciseName: String = ""
    var activeKcal: Int = 0
    var epocKcal: Int = 0
    var totalKcal: Int = 0
    var percentageOfSession: Double = 0.0
    var completedSets: Int = 0
    var totalSets: Int = 0
}

struct SessionEnergySummary: Codable {
    var activeKcal: CalorieRange = CalorieRange()
    var epocKcal: CalorieRange = CalorieRange()
    var totalKcal: CalorieRange = CalorieRange()
    var projectedTotalKcal: Int? = nil
    var confidence: EnergyConfidence = .LOW
    var source: EnergyEstimateSource = .PLANNED
    var methodVersion: String = "auge-energy-v1"
    var exerciseContributions: [ExerciseEnergyContribution] = []
    var notes: [String] = []
}

// ─── PlanDeviation ───────────────────────────────────────────────────────────
// PlanDeviation and PlanDeviationType are defined in SessionModels.swift

// ─── ExerciseDiscomfortReport ────────────────────────────────────────────────

public struct ExerciseDiscomfortReport: Codable {
    var exerciseId: String = ""
    var exerciseDbId: String? = nil
    var canonicalExerciseId: String? = nil
    var exerciseName: String = ""
    var technicalQuality: Int = 0
    var discomfortIds: [String] = []
    var notes: String? = nil
    var perceivedIntensityRpe: Double? = nil
    var perceivedFailure: Bool = false
}

// ─── RecordedSetPayload ──────────────────────────────────────────────────────

public struct RecordedSetPayload: Codable {
    var contextProfileId: String? = nil
    var exerciseId: String = ""
    var exerciseDbId: String? = nil
    var side: String? = nil
    var loadInputMode: LoadModeV2 = .LOAD
    var unitMode: UnitModeV2 = .REPS
    var externalLoad: Double? = nil
    var assistedLoad: Double? = nil
    var bodyWeightSnapshot: Double? = nil
    var completedReps: Int? = nil
    var partialReps: Int? = nil
    var durationSeconds: Int? = nil
    var actualIntensityMode: IntensityMode? = nil
    var actualIntensityValue: Double? = nil
    var techniques: [SetTechniqueV2] = []
    var failedSet: Bool = false
    var reachedFailure: Bool = false
    var amrapPerformed: Bool = false
    var timerTargetSeconds: Int? = nil
    var timerElapsedSeconds: Int? = nil
    var failureReason: String? = nil
    var executionError: Bool = false
    var skipped: Bool = false
    var superSetWithExerciseId: String? = nil
}

// ─── WorkoutVoiceUiState (extended with .Idle) ───────────────────────────────
// WorkoutVoiceUiState is already defined above in WorkoutModels.swift

// ─── MuscleAdvance ───────────────────────────────────────────────────────────
// MuscleAdvance and DiscountProposal are defined in SessionModels.swift

// ─── OmittedExercise ─────────────────────────────────────────────────────────
// OmittedExercise is already defined in SessionModels.swift

// ─── TimerAction ─────────────────────────────────────────────────────────────

internal enum TimerAction {
    case completeSet
    case skipTimer
    case addTime
    case subtractTime
}

// ─── PerformanceRangeData ────────────────────────────────────────────────────

internal struct PerformanceRangeData: Codable, Equatable {
    let contextKey: String
    let ermMin: Double
    let ermMax: Double
    let ermRms: Double
    let sampleCount: Int
    let lastUpdatedMs: Int64
    let consecutiveAbove: Int
    let consecutiveBelow: Int
    init(contextKey: String, ermMin: Double = 0, ermMax: Double = 0, ermRms: Double = 0, sampleCount: Int = 0, lastUpdatedMs: Int64 = 0, consecutiveAbove: Int = 0, consecutiveBelow: Int = 0) {
        self.contextKey = contextKey
        self.ermMin = ermMin
        self.ermMax = ermMax
        self.ermRms = ermRms
        self.sampleCount = sampleCount
        self.lastUpdatedMs = lastUpdatedMs
        self.consecutiveAbove = consecutiveAbove
        self.consecutiveBelow = consecutiveBelow
    }
}

// ─── SetEntryV2 ──────────────────────────────────────────────────────────────

internal struct SetEntryV2: Codable, Equatable {
    let exerciseId: String
    let exerciseDbId: String?
    let canonicalExerciseId: String?
    let setIndex: Int
    let loadMode: LoadModeV2
    let unitMode: UnitModeV2
    let plannedTarget: Double?
    let actualValue: Double
    let loggedLoad: Double?
    let bodyWeight: Double?
    let plannedIntensity: Double?
    let actualIntensity: Double?
    let debt: Double
    let failedSet: Bool
    let reachedFailure: Bool
    let amrapOverride: Bool
    let techniques: [SetTechniqueV2]
    let tagId: String?
    let setupId: String?
    let machineBrand: String?
    let contextKey: String
    let timeProgressionStrategy: TimeProgressionStrategyV3
    let barWeightKg: Double?
    let rom: Int?
    let assistedReps: Int?
    let isFirstEvaluationInSession: Bool
    func resolvedCanonicalExerciseId() -> String { canonicalExerciseId ?? exerciseId }
    func resolvedExerciseDbId() -> String { exerciseDbId ?? exerciseId }
    func setIdx() -> Int { setIndex }
}

// ─── SessionLocationCursor ───────────────────────────────────────────────────

internal struct SessionLocationCursor {
    let macroIndex: Int
    let mesoIndex: Int
    let weekId: String
    let weekIndex: Int
    let dayOfWeek: Int?
    let sessionSlot: Int
}

// ─── OngoingWorkoutState ─────────────────────────────────────────────────────

internal struct OngoingWorkoutState: Codable {
    let programId: String
    let session: Session
    var isPaused: Bool
    let startTime: Int64
    var activeExerciseId: String?
    var activeSetId: String?
    var activeSetIndex: Int
    var activeExerciseIndex: Int
    var activeStepKey: String?
    var activeMode: WeekVariant
    var completedSets: [String: CompletedSet]
    var dynamicWeights: [String: Double]
    var loadSuggestionReasons: [String: String]
    var setDrafts: [String: WorkoutSetDraft]
    var manualLoadOverrides: [String: Double]
    var editingSetKey: String?
    var isCarpeDiem: Bool
    var macroIndex: Int?
    var mesoIndex: Int?
    var weekId: String?
    var exerciseTags: [String: String]
    var activeTags: [String: [String]]
    var activeSubTags: [String: [String]]
    var userCreatedTags: [String: [WorkoutTag]]
    var contextProfilesV3: [String: WorkoutContextProfile]
    var activeContextProfileByExerciseId: [String: String]
    var skippedExerciseIds: Set<String>
    var warmupCompletedExerciseIds: Set<String>
    var mobilityCompletedExerciseIds: Set<String>
    var readinessNeuralOverride: Int?
    var readinessMuscularOverride: Int?
    var readinessSpinalOverride: Int?
    var readinessMuscleOverrides: [String: Int]
    var restModalState: WorkoutRestModalState?
    var persistedLoadModeBySet: [String: LoadModeV2]
    var persistedLoadModeByExercise: [String: LoadModeV2]
    var customTargetDurationMinutes: Int?
}

// ─── WorkoutPerformanceHomologationEngine ────────────────────────────────────

internal struct WorkoutPerformanceHomologationEngine {
    private static let ewmaAlpha: Double = 0.35
    private static let minScore: Double = 0.0
    private static let maxScore: Double = 100.0

    internal struct EvaluationResult {
        let outcome: SetOutcomeV2
        let homologated: HomologatedPerformanceResult
        let nextState: ContextPerformanceStateV2
        let nextGlobalState: GlobalPerformanceStateV3
    }

    static func evaluate(
        entry: SetEntryV2,
        previous: ContextPerformanceStateV2?,
        previousGlobal: GlobalPerformanceStateV3? = nil
    ) -> EvaluationResult {
        let globalKey = entry.resolvedCanonicalExerciseId()
        let erm = entry.actualValue
        let target = entry.plannedTarget ?? erm
        let debt = entry.debt
        let failed = entry.failedSet
        let reachedFailure = entry.reachedFailure

        let prevEwma = previous?.ewma ?? erm
        let prevVariance = previous?.variance ?? 36.0
        let prevGlobalEwma = previousGlobal?.ewma ?? erm
        let prevGlobalVariance = previousGlobal?.variance ?? 36.0

        let newEwma = Self.ewmaAlpha * erm + (1.0 - Self.ewmaAlpha) * prevEwma
        let squaredDiff = (erm - newEwma) * (erm - newEwma)
        let newVariance = Self.ewmaAlpha * squaredDiff + (1.0 - Self.ewmaAlpha) * prevVariance
        let newStd = max(sqrt(newVariance), 1.0)

        let globalNewEwma = Self.ewmaAlpha * erm + (1.0 - Self.ewmaAlpha) * prevGlobalEwma
        let globalSquaredDiff = (erm - globalNewEwma) * (erm - globalNewEwma)
        let globalNewVariance = Self.ewmaAlpha * globalSquaredDiff + (1.0 - Self.ewmaAlpha) * prevGlobalVariance
        let globalNewStd = max(sqrt(globalNewVariance), 1.0)

        let contextScore = max(Self.minScore, min(Self.maxScore, 50.0 + ((erm - newEwma) / max(newStd, 0.01)) * 15.0))
        let globalScore = max(Self.minScore, min(Self.maxScore, 50.0 + ((erm - globalNewEwma) / max(globalNewStd, 0.01)) * 15.0))

        let isContextPr = erm > (previous?.bestScore ?? 0)
        let isGlobalPr = erm > (previousGlobal?.bestScore ?? 0)

        let sampleCount = (previous?.sampleCount ?? 0) + 1
        let globalSampleCount = (previousGlobal?.sampleCount ?? 0) + 1

        let color: HistoryColorV2
        if failed {
            color = .RED
        } else if reachedFailure || isContextPr {
            color = .YELLOW
        } else {
            color = .NEUTRAL
        }

        let difficulty: DifficultySignalV2
        if debt < -3.0 {
            difficulty = .EASIER
        } else if debt > 3.0 {
            difficulty = .HARDER
        } else {
            difficulty = .MATCHED
        }

        let now = ISO8601DateFormatter().string(from: Date())

        var nextState = previous ?? ContextPerformanceStateV2()
        nextState.contextKey = entry.contextKey
        nextState.ewma = newEwma
        nextState.mean = newEwma
        nextState.variance = newVariance
        nextState.bestScore = max(previous?.bestScore ?? 0, erm)
        nextState.sampleCount = sampleCount
        nextState.recentScores = Array((previous?.recentScores ?? []).suffix(19) + [erm])
        nextState.consecutiveGreenSessions = isContextPr ? (previous?.consecutiveGreenSessions ?? 0) + 1 : 0
        nextState.lastSuggestedLoad = erm + (target - erm) * 0.3
        nextState.lastUpdatedAtIso = now

        var nextGlobal = previousGlobal ?? GlobalPerformanceStateV3()
        nextGlobal.globalKey = globalKey
        nextGlobal.ewma = globalNewEwma
        nextGlobal.mean = globalNewEwma
        nextGlobal.variance = globalNewVariance
        nextGlobal.bestScore = max(previousGlobal?.bestScore ?? 0, erm)
        nextGlobal.sampleCount = globalSampleCount
        nextGlobal.recentScores = Array((previousGlobal?.recentScores ?? []).suffix(19) + [erm])
        nextGlobal.lastUpdatedAtIso = now

        let metricValue: Int = Int(round(erm))
        let suggestedLoad = erm + (target - erm) * 0.3

        let homologated = HomologatedPerformanceResult(
            contextKey: entry.contextKey,
            globalKey: globalKey,
            loadMode: entry.loadMode,
            unitMode: entry.unitMode,
            plannedTarget: target,
            actualValue: erm,
            actualIntensity: entry.actualIntensity,
            debt: debt,
            failedSet: failed,
            reachedFailure: reachedFailure,
            amrapOverride: entry.amrapOverride,
            techniques: entry.techniques,
            metricType: entry.loadMode == .BODYWEIGHT ? "BW" : "KG",
            metricValue: Double(metricValue),
            estimatedRm: erm,
            trm: nil,
            localPerformanceIndex: contextScore,
            globalPerformanceIndex: globalScore,
            contextPercentile: 50.0,
            globalPercentile: 50.0,
            contextEwma: newEwma,
            contextStdDev: newStd,
            globalEwma: globalNewEwma,
            globalStdDev: globalNewStd,
            isContextPr: isContextPr,
            isGlobalPr: isGlobalPr,
            historyColor: color,
            difficultySignal: difficulty,
            suggestedNextLoad: suggestedLoad,
            suggestedTargetSeconds: nil,
            suggestionReason: nil,
            augeEquivalentLoad: erm,
            augeEquivalentReps: metricValue,
            ermRangeMin: erm - newStd,
            ermRangeMax: erm + newStd,
            suggestedLoadMode: entry.loadMode == .BODYWEIGHT ? .BODYWEIGHT : nil
        )

        let outcome = SetOutcomeV2(
            contextKey: entry.contextKey,
            loadMode: entry.loadMode,
            unitMode: entry.unitMode,
            plannedTarget: target,
            actualValue: erm,
            actualIntensity: entry.actualIntensity,
            debt: debt,
            failedSet: failed,
            reachedFailure: reachedFailure,
            amrapOverride: entry.amrapOverride,
            techniques: entry.techniques,
            historyColor: color,
            difficultySignal: difficulty,
            suggestedNextLoad: suggestedLoad,
            suggestedTargetSeconds: nil,
            suggestionReason: nil,
            augeEquivalentLoad: erm,
            augeEquivalentReps: metricValue,
            suggestedLoadMode: entry.loadMode == .BODYWEIGHT ? .BODYWEIGHT : nil
        )

        return EvaluationResult(
            outcome: outcome,
            homologated: homologated,
            nextState: nextState,
            nextGlobalState: nextGlobal
        )
    }
}
