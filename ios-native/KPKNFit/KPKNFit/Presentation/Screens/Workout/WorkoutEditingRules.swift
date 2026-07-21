import Foundation

enum WorkoutLiveEditPersistenceScope {
    case sessionOnly
    case permanentAllowed
}

struct WorkoutEditingRules {
    static func buildEditingState(
        completedSets: [String: CompletedSet],
        exercise: Exercise?,
        setIdx: Int,
        preferredSide: String? = nil
    ) -> WorkoutEditingState? {
        guard let exercise = exercise else { return nil }
        let safeSetIdx = min(max(setIdx, 0), max(0, exercise.sets.count - 1))
        let isUnilateral = exercise.isEffectivelyUnilateral()
        guard isSetDone(completedSets: completedSets, exerciseId: exercise.id, setIdx: safeSetIdx, isUnilateral: isUnilateral) else { return nil }
        let resolvedSide: String? = {
            if !isUnilateral { return nil }
            if let side = preferredSide, completedSets[buildCompletedSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: side)] != nil { return side }
            if completedSets[buildCompletedSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: "left")] != nil { return "left" }
            if completedSets[buildCompletedSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: "right")] != nil { return "right" }
            return nil
        }()
        return WorkoutEditingState(
            setKey: workoutSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: resolvedSide),
            exerciseId: exercise.id,
            setIdx: safeSetIdx,
            side: resolvedSide
        )
    }

    private static func isSetDone(completedSets: [String: CompletedSet], exerciseId: String, setIdx: Int, isUnilateral: Bool) -> Bool {
        if completedSets[buildCompletedSetKey(exerciseId: exerciseId, setIdx: setIdx, side: nil)] != nil { return true }
        if isUnilateral,
           completedSets[buildCompletedSetKey(exerciseId: exerciseId, setIdx: setIdx, side: "left")] != nil,
           completedSets[buildCompletedSetKey(exerciseId: exerciseId, setIdx: setIdx, side: "right")] != nil { return true }
        return false
    }

    private static func buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?) -> String {
        switch side {
        case "left": return "\(exerciseId)_\(setIdx)_L"
        case "right": return "\(exerciseId)_\(setIdx)_R"
        default: return "\(exerciseId)_\(setIdx)"
        }
    }

    static func liveEditPersistenceScope(program: Program) -> WorkoutLiveEditPersistenceScope {
        let isSimpleCyclic = program.isSimpleTemporalProgram &&
            program.simpleProgramKind == .cyclic &&
            program.calendarization?.mode != .simpleDated
        return isSimpleCyclic ? .permanentAllowed : .sessionOnly
    }

    static func canPersistLiveStructuralChanges(program: Program) -> Bool {
        return liveEditPersistenceScope(program: program) == .permanentAllowed
    }

    static func replacementPersistenceOptions(program: Program) -> [ReplacementPersistenceScopeV2] {
        if canPersistLiveStructuralChanges(program: program) {
            return [.sessionOnly, .permanent]
        }
        return [.sessionOnly, .mesocycleMatching]
    }

    static func unitModeForTrainingMode(mode: TrainingMode) -> UnitModeV2 {
        switch mode {
        case .time: return .time
        case .distance: return .distance
        case .custom: return .custom
        case .reps, .rm, .soloRpe, .amrap: return .reps
        }
    }

    static func normalizeLiveEditedExercise(exercise: Exercise) -> Exercise {
        var ex = exercise
        ex.sets = ex.sets.map { normalizeLiveEditedSet(mode: exercise.trainingMode, set: $0) }
        return ex
    }

    static func normalizeLiveEditedSet(mode: TrainingMode, set: ExerciseSet) -> ExerciseSet {
        let unitMode = unitModeForTrainingMode(mode: mode)
        let metricNormalized: ExerciseSet = {
            switch mode {
            case .time:
                var s = set
                s.unitModeV2 = .time
                s.targetDuration = set.targetDuration ?? set.plannedTargetV2.flatMap { Int($0) }
                s.targetReps = nil
                s.plannedTargetV2 = nil
                s.targetPercentageRM = nil
                s.isAmrap = false
                return s
            case .distance, .custom:
                var s = set
                s.unitModeV2 = unitMode
                s.plannedTargetV2 = set.plannedTargetV2 ?? set.targetReps.map(Double.init) ?? set.targetDuration.map(Double.init)
                s.targetReps = nil
                s.targetDuration = nil
                s.targetPercentageRM = nil
                s.isAmrap = false
                return s
            case .rm:
                var s = set
                s.unitModeV2 = .reps
                s.targetReps = set.targetReps ?? set.plannedTargetV2.flatMap { Int($0) }
                s.targetDuration = nil
                s.plannedTargetV2 = nil
                s.targetPercentageRM = max(40.0, min(100.0, set.targetPercentageRM ?? 75.0))
                s.isAmrap = false
                return s
            case .soloRpe:
                var s = set
                s.unitModeV2 = .reps
                s.targetReps = nil
                s.targetDuration = nil
                s.plannedTargetV2 = nil
                s.targetPercentageRM = nil
                s.isAmrap = false
                return s
            case .amrap:
                var s = set
                s.unitModeV2 = .reps
                s.targetReps = set.targetReps ?? set.plannedTargetV2.flatMap { Int($0) }
                s.targetDuration = nil
                s.plannedTargetV2 = nil
                s.targetPercentageRM = nil
                s.isAmrap = true
                return s
            case .reps:
                var s = set
                s.unitModeV2 = .reps
                s.targetReps = set.targetReps ?? set.plannedTargetV2.flatMap { Int($0) }
                s.targetDuration = nil
                s.plannedTargetV2 = nil
                s.targetPercentageRM = nil
                s.isAmrap = false
                return s
            }
        }()
        let intensityNormalized: ExerciseSet = {
            if metricNormalized.isFailure || metricNormalized.intensityMode == .failure {
                var s = metricNormalized
                s.intensityMode = .failure
                s.targetRPE = nil
                s.targetRIR = nil
                s.isFailure = true
                return s
            }
            switch mode {
            case .rm:
                var s = metricNormalized
                s.intensityMode = .load
                s.targetRPE = nil
                s.targetRIR = nil
                s.isFailure = false
                return s
            case .soloRpe:
                var s = metricNormalized
                s.intensityMode = .rpe
                s.targetRPE = max(1.0, min(10.0, s.targetRPE ?? 8.0))
                s.targetRIR = nil
                s.isFailure = false
                return s
            default:
                var s = metricNormalized
                if s.intensityMode == nil || s.intensityMode == .soloRm {
                    s.intensityMode = .rpe
                }
                return s
            }
        }()
        var result = intensityNormalized
        if result.loadModeV2 == nil { result.loadModeV2 = .load }
        return result
    }
}
