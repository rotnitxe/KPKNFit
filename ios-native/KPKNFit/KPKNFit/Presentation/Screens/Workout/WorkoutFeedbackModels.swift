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
    var discomforts: [String] = []
    var clarityRating: Int? = nil
    var environmentTags: [String] = []
    var additionalDiscomfortNote: String? = nil
    var stillPresentDiscomfortIds: [String] = []
    var sessionEffortRpe: Double? = nil
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





struct SetAutoRegulation {
    let exerciseId: String
    let nextSetIdx: Int
    let adjustmentFactor: Double
    let adjustedWeight: Double
    let reason: String
}


