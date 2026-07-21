import Foundation

struct SetAdvancedFeedback {
    var rir: Int? = nil
    var reachedFailure: Bool = false
    var isFailedSet: Bool = false
    var failureReason: String? = nil
    var executionError: Bool = false
    var isPartial: Bool = false
    var partialReps: Int? = nil
    var dropSets: [DropSetData] = []
    var restPauses: [RestPauseData] = []
    var skipped: Bool = false
    var superSetWithExerciseId: String? = nil
    var isWarmup: Bool = false
    var actualIntensityMode: IntensityMode? = nil
    var actualIntensityValue: Double? = nil
    var timerElapsedSeconds: Int? = nil
    var timerTargetSeconds: Int? = nil
    var rom: Int? = nil
    var assistedReps: Int? = nil
}

struct PostExerciseFeedback {
    let exerciseId: String
    let exerciseDbId: String?
    let canonicalExerciseId: String?
    let exerciseName: String
    let technicalQuality: Int
    let discomfortIds: [String]
    let notes: String?
    let perceivedIntensityRpe: Double?
    let perceivedFailure: Bool

    init(
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

enum PostExerciseFeedbackTarget {
    case single(exerciseId: String)
    case supersetGroup(groupId: String, exerciseIds: [String])
}

func backfillCompletedSetIntensityFromPostExerciseFeedback(
    completedSets: [String: CompletedSet],
    feedback: PostExerciseFeedback
) -> [String: CompletedSet] {
    let perceived = feedback.perceivedIntensityRpe.flatMap { $0.clamped(to: 1.0...10.0) }
    if perceived == nil && !feedback.perceivedFailure { return completedSets }
    let mode: IntensityMode = feedback.perceivedFailure ? .failure : .rpe
    let value: Double = feedback.perceivedFailure ? 10.0 : (perceived ?? 10.0)
    return completedSets.mapValues { set in
        let belongsToExercise = set.key.hasPrefix("\(feedback.exerciseId)_")
        let hasRecordedIntensity = set.actualIntensityValue != nil ||
            set.actualIntensityMode != nil ||
            set.rpe != nil ||
            set.rir != nil ||
            set.isFailure
        guard belongsToExercise, !hasRecordedIntensity else { return set }
        return set.copyWith(
            rpe: feedback.perceivedFailure ? nil : value,
            isFailure: feedback.perceivedFailure,
            actualIntensityMode: mode,
            actualIntensityValue: value
        )
    }
}

func backfillCompletedSetIntensityFromPostExerciseFeedbacks(
    completedSets: [String: CompletedSet],
    feedbacks: [PostExerciseFeedback]
) -> [String: CompletedSet] {
    return feedbacks.reduce(completedSets) { current, feedback in
        backfillCompletedSetIntensityFromPostExerciseFeedback(completedSets: current, feedback: feedback)
    }
}

struct SessionClosingFeedback {
    let overallFatigue: Int
    let systemAdjustment: Int
    let muscularAdjustment: Int
    let structureAdjustment: Int
    let discomforts: [String]
    var clarityRating: Int = 5
    var environmentTags: [String] = []
    var finalNeuralBattery: Int? = nil
    var finalSpinalBattery: Int? = nil
    var finalMuscularBattery: Int? = nil
    var finalMuscleBatteries: [String: Int] = [:]
    var additionalDiscomfortNote: String? = nil
    var stillPresentDiscomfortIds: [String] = []
}

struct ExerciseHistoryEntry {
    let date: String
    let sets: [CompletedSet]
    let e1rm: Double?
    let tag: String?
    let latestHistoryColor: HistoryColorV2?
    let latestMetricType: String?
    let latestMetricValue: Double?
}

struct WeightSuggestion {
    let suggestedWeight: Double
    let reason: String
    let suggestedLoadMode: LoadModeV2?
}

struct WorkoutLoadSuggestionUi {
    let suggestedWeight: Double
    let originalWeight: Double
    var isRecalculated: Bool = false
    let reason: String
    let source: WorkoutLoadSuggestionSource
    let suggestedLoadMode: LoadModeV2?
}

enum WorkoutLoadSuggestionSource {
    case program
    case history
    case sessionErm
    case manualBase
}

struct WorkoutPlanDeviationSupport {
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
                deviations.append(PlanDeviation(
                    exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx,
                    type: .weightHigh, detail: String(format: "+%.0f%% del sugerido", (ratio - 1) * 100)
                ))
            } else if ratio < 0.85 {
                deviations.append(PlanDeviation(
                    exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx,
                    type: .weightLow, detail: String(format: "-%.0f%% del sugerido", (1 - ratio) * 100)
                ))
            }
        }
        let targetReps = plannedSet.targetReps
        if let targetReps = targetReps, targetReps > 0, actualReps > 0 {
            if actualReps > targetReps + 3 {
                deviations.append(PlanDeviation(
                    exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx,
                    type: .repsHigh, detail: "\(actualReps) vs \(targetReps) objetivo"
                ))
            } else if actualReps < targetReps - 3 {
                deviations.append(PlanDeviation(
                    exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx,
                    type: .repsLow, detail: "\(actualReps) vs \(targetReps) objetivo"
                ))
            }
        }
        if advanced.reachedFailure && !plannedSet.isFailure {
            deviations.append(PlanDeviation(
                exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx,
                type: .unplannedFailure, detail: "Fallo no programado"
            ))
        }
        if !advanced.dropSets.isEmpty && plannedSet.dropSets.isEmpty {
            deviations.append(PlanDeviation(
                exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx,
                type: .unplannedDropset, detail: "Dropset no programado"
            ))
        }
        if !advanced.restPauses.isEmpty && plannedSet.restPauses.isEmpty {
            deviations.append(PlanDeviation(
                exerciseId: exerciseId, exerciseName: exerciseName, setIdx: setIdx,
                type: .unplannedRestPause, detail: "Rest-pause no programado"
            ))
        }
        return deviations
    }
}

func applyAdvancedFeedback(base: CompletedSet, advanced: SetAdvancedFeedback) -> CompletedSet {
    return base.copyWith(
        rir: advanced.rir,
        isFailure: advanced.reachedFailure,
        isFailedSet: advanced.isFailedSet || advanced.executionError,
        failureReason: advanced.failureReason ?? (advanced.executionError ? "execution_error" : nil),
        isPartial: advanced.isPartial,
        partialReps: advanced.partialReps,
        dropSets: advanced.dropSets,
        restPauses: advanced.restPauses,
        skipped: advanced.skipped,
        superSetWithExerciseId: advanced.superSetWithExerciseId,
        isWarmup: advanced.isWarmup,
        actualIntensityMode: advanced.actualIntensityMode,
        actualIntensityValue: advanced.actualIntensityValue,
        rom: advanced.rom,
        assistedReps: advanced.assistedReps
    )
}

func calculateUnifiedSessionEffortSignal(sets: [CompletedSet]) -> Double {
    let effectiveSets = sets.filter { set in
        !set.isWarmup && AugeFatigueEngine.isSetEffective(set)
    }
    guard !effectiveSets.isEmpty else { return 7.0 }
    let signals = effectiveSets.map { set -> Double in
        var signal = AugeFatigueEngine.getEffectiveRPE(set)
        if set.isFailure { signal += 0.6 }
        if !set.dropSets.isEmpty { signal += 0.4 }
        if !set.restPauses.isEmpty { signal += 0.5 }
        return signal.clamped(to: 1.0...12.0)
    }
    let avg = signals.reduce(0.0, +) / Double(signals.count)
    return avg.clamped(to: 1.0...12.0)
}

func mapWorkoutToPostSessionFeedback(
    log: WorkoutLog,
    postExerciseFeedback: [PostExerciseFeedback],
    exerciseDbById: [String: ExerciseMuscleInfo]
) -> PostSessionFeedback {
    let grouped = Dictionary(grouping: postExerciseFeedback) { feedback -> String in
        let exercise = log.completedExercises.first { $0.exerciseId == feedback.exerciseId }
        let dbInfo = exercise.flatMap { exerciseDbById[$0.exerciseDbId ?? $0.exerciseId] }
        if let primary = dbInfo?.involvedMuscles.first(where: { $0.role == .primary }) {
            let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
            return getAugeMuscleDisplayId(canonical: canonical, emphasis: primary.emphasis)
        }
        return feedback.exerciseName
    }
    let feedbackByMuscle = grouped.mapValues { list in
        let avgTech = list.isEmpty ? 8 : list.map { $0.technicalQuality }.reduce(0, +) / list.count
        let hasJointPain = list.contains { $0.discomfortIds.contains { $0 != "none" } }
        return MuscleFeedbackEntry(
            doms: (11 - avgTech).clamped(to: 1...5),
            jointPain: hasJointPain,
            strengthCapacity: 7,
            notes: list.flatMap { $0.discomfortIds }.filter { $0 != "none" }.joined(separator: ", ")
        )
    }
    return PostSessionFeedback(
        logId: log.id,
        date: Date().formatted(.iso8601),
        cnsRecovery: 7,
        muscleFeedback: feedbackByMuscle
    )
}

struct SetAutoRegulation {
    let exerciseId: String
    let nextSetIdx: Int
    let adjustmentFactor: Double
    let adjustedWeight: Double
    let reason: String
}

struct WorkoutAutoRegulation {
    private static let minFactor = 0.60
    private static let maxFactor = 1.10

    static func computeAdjustmentFactor(
        weightedDrainPct: Double,
        effectiveRpe: Double,
        reachedFailure: Bool,
        isFailedSet: Bool,
        isPartial: Bool,
        sessionProgress: Double
    ) -> Double {
        var factor = 1.0
        let drainFactor: Double = {
            switch weightedDrainPct {
            case 10.0...: return -0.10
            case 7.0..<10.0: return -0.07
            case 4.5..<7.0: return -0.04
            case 2.5..<4.5: return -0.02
            case 1.0..<2.5: return -0.01
            default: return 0.0
            }
        }()
        factor += drainFactor
        let rpeFactor: Double = {
            switch effectiveRpe {
            case 11.0...: return -0.08
            case 10.5..<11.0: return -0.06
            case 10.0..<10.5: return -0.04
            case 9.5..<10.0: return -0.02
            case 8.5..<9.5: return -0.01
            case ..<7.5: return +0.02
            default: return 0.0
            }
        }()
        factor += rpeFactor
        if reachedFailure { factor -= 0.05 }
        if isFailedSet { factor -= 0.03 }
        if isPartial { factor -= 0.02 }
        let sessionFactor: Double = {
            switch sessionProgress {
            case 0.80...: return -0.03
            case 0.60..<0.80: return -0.02
            case 0.40..<0.60: return -0.01
            default: return 0.0
            }
        }()
        factor += sessionFactor
        return factor.clamped(to: minFactor...maxFactor)
    }

    static func buildReason(factor: Double, weightedDrainPct: Double, effectiveRpe: Double, reachedFailure: Bool) -> String {
        var parts: [String] = []
        if reachedFailure { parts.append("Fallo") }
        if weightedDrainPct >= 5.0 { parts.append("Fatiga acumulada") }
        let detail = parts.joined(separator: " · ")
        if factor < 0.95 {
            var result = "Ajuste de carga"
            if !detail.isEmpty { result += " · \(detail)" }
            result += " · −\((Int((1 - factor) * 100)))%"
            return result
        } else if factor > 1.02 {
            return "Ajuste de carga · Recuperación buena · +\((Int((factor - 1) * 100)))%"
        }
        return "Sin ajuste de carga"
    }

    private static let readinessReductionThreshold = 70
    private static let readinessSevereThreshold = 50
    private static let readinessMinFactor = 0.70
    private static let readinessSevereFactor = 0.85
    private static let readinessRecoveryFactor = 1.05

    static func computeReadinessAdjustmentFactor(
        readinessNeural: Int?,
        readinessSpinal: Int?,
        readinessMuscular: Int?,
        readinessPerMuscle: [String: Int]?,
        involvedMuscleIds: [String]
    ) -> Double {
        let perMuscleValues = readinessPerMuscle.flatMap { overrides in
            let relevant = involvedMuscleIds.compactMap { overrides[$0] }
            return relevant.isEmpty ? nil : relevant.min()
        }
        let relevant = [readinessNeural.map(Double.init),
                        readinessSpinal.map(Double.init),
                        readinessMuscular.map(Double.init),
                        perMuscleValues.map(Double.init)]
            .compactMap { $0 }
        let readiness = (relevant.min() ?? 100.0).clamped(to: 0.0...100.0)
        switch readiness {
        case ..<Double(readinessSevereThreshold):
            let t = (readiness / Double(readinessSevereThreshold)).clamped(to: 0.0...1.0)
            return (readinessMinFactor + (readinessSevereFactor - readinessMinFactor) * t)
                .clamped(to: readinessMinFactor...readinessSevereFactor)
        case ..<Double(readinessReductionThreshold):
            let t = ((readiness - Double(readinessSevereThreshold)) /
                (Double(readinessReductionThreshold) - Double(readinessSevereThreshold))).clamped(to: 0.0...1.0)
            return (readinessSevereFactor + (1.0 - readinessSevereFactor) * t)
                .clamped(to: readinessSevereFactor...1.0)
        case 90...:
            return readinessRecoveryFactor
        default:
            return 1.0
        }
    }

    static func buildReadinessReason(factor: Double, readinessValue: Int?) -> String {
        guard factor != 1.0 else { return "" }
        if factor < 0.85, let v = readinessValue {
            return "Readiness \(v) · −\((Int((1 - factor) * 100)))%"
        } else if factor < 1.0 {
            return "Readiness \(readinessValue ?? 0)"
        } else {
            return "Readiness \(readinessValue ?? 0) · +\((Int((factor - 1) * 100)))%"
        }
    }
}
