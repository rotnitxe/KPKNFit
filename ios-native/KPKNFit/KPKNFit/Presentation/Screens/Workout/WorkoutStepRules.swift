import Foundation

enum WorkoutStepType {
    case mobility
    case warmup
    case workingSet
}

struct WorkoutStep {
    let type: WorkoutStepType
    let exerciseId: String
    let exerciseName: String
    var stepKey: String = ""
    var setIndex: Int? = nil
    var warmupSetId: String? = nil
    var mobilitySeriesId: String? = nil
    var side: String? = nil
    var supersetGroupId: String? = nil
    var supersetRoundIndex: Int? = nil
    var mobilitySeries: [MobilitySeries] = []
    var restAfterKind: RestTimerKind = .standard
}

struct WorkoutStepRules {
    static func buildSteps(session: Session, visibleExercises: [Exercise] = []) -> [WorkoutStep] {
        let exercises = visibleExercises.isEmpty ? session.allExercises() : visibleExercises
        var steps: [WorkoutStep] = []
        var emittedSupersets = Set<String>()
        for exercise in exercises {
            let groupId = exercise.supersetGroupRefOrLegacyId()
            if let groupId = groupId {
                if emittedSupersets.insert(groupId).inserted {
                    appendSupersetSteps(session: session, visibleExercises: exercises, groupId: groupId, steps: &steps)
                }
            } else {
                appendExerciseSteps(exercise: exercise, groupId: nil, steps: &steps)
            }
        }
        return steps
    }

    static func buildWorkingPositions(session: Session, visibleExercises: [Exercise] = []) -> [WorkoutStep] {
        return buildSteps(session: session, visibleExercises: visibleExercises)
            .filter { $0.type == .workingSet && $0.setIndex != nil }
    }

    static func warmupStepKey(exerciseId: String, warmupSetId: String) -> String {
        return "\(exerciseId)_warmup_\(warmupSetId)"
    }

    static func mobilityStepKey(exerciseId: String, mobilitySeriesId: String) -> String {
        return "\(exerciseId)_\(mobilitySeriesId)"
    }

    static func workingStepKey(exerciseId: String, setIndex: Int, side: String? = nil) -> String {
        var key = "\(exerciseId)_\(setIndex)"
        if let side = side {
            key += "_\(side.prefix(1).uppercased())"
        }
        return key
    }

    static func buildSetPositions(session: Session, visibleExercises: [Exercise] = []) -> [WorkoutStep] {
        var emitted = Set<Pair<String, Int>>()
        return buildSteps(session: session, visibleExercises: visibleExercises)
            .filter { $0.type == .workingSet && $0.setIndex != nil }
            .filter { emitted.insert(Pair($0.exerciseId, $0.setIndex!)).inserted }
    }

    private static func appendSupersetSteps(session: Session, visibleExercises: [Exercise], groupId: String, steps: inout [WorkoutStep]) {
        let visibleIds = Set(visibleExercises.map { $0.id })
        let members = SupersetRules.orderedMembers(session: session, groupId: groupId)
            .filter { visibleIds.contains($0.id) }
        guard !members.isEmpty else { return }
        for exercise in members {
            for mobility in exercise.mobilitySeries {
                steps.append(WorkoutStep(
                    type: .mobility,
                    exerciseId: exercise.id,
                    exerciseName: "Movilidad de superserie",
                    stepKey: mobilityStepKey(exerciseId: exercise.id, mobilitySeriesId: mobility.id),
                    mobilitySeriesId: mobility.id,
                    supersetGroupId: groupId,
                    mobilitySeries: members.flatMap { $0.mobilitySeries },
                    restAfterKind: .standard
                ))
            }
        }
        for exercise in members {
            appendWarmupSteps(exercise: exercise, groupId: groupId, steps: &steps)
        }
        let rounds = SupersetRules.roundCount(session: session, groupId: groupId)
        for roundIdx in 0..<rounds {
            for (memberIdx, exercise) in members.enumerated() {
                guard roundIdx < exercise.sets.count else { continue }
                let isLastMemberWithSet = members[(memberIdx + 1)...].allSatisfy { roundIdx >= $0.sets.count }
                appendWorkingSetSteps(
                    exercise: exercise,
                    setIndex: roundIdx,
                    groupId: groupId,
                    roundIndex: roundIdx,
                    restAfterKind: isLastMemberWithSet ? .supersetRound : .supersetIntra,
                    steps: &steps
                )
            }
        }
    }

    private static func appendExerciseSteps(exercise: Exercise, groupId: String?, steps: inout [WorkoutStep]) {
        appendPreparationSteps(exercise: exercise, groupId: groupId, steps: &steps)
        for setIndex in exercise.sets.indices {
            appendWorkingSetSteps(
                exercise: exercise,
                setIndex: setIndex,
                groupId: groupId,
                roundIndex: nil,
                restAfterKind: .standard,
                steps: &steps
            )
        }
    }

    private static func appendPreparationSteps(exercise: Exercise, groupId: String?, steps: inout [WorkoutStep]) {
        for mobility in exercise.mobilitySeries {
            steps.append(WorkoutStep(
                type: .mobility,
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                stepKey: mobilityStepKey(exerciseId: exercise.id, mobilitySeriesId: mobility.id),
                mobilitySeriesId: mobility.id,
                supersetGroupId: groupId,
                mobilitySeries: [mobility],
                restAfterKind: .standard
            ))
        }
        for index in exercise.warmupSets.indices {
            steps.append(warmupStep(exercise: exercise, groupId: groupId, index: index))
        }
    }

    private static func appendWarmupSteps(exercise: Exercise, groupId: String?, steps: inout [WorkoutStep]) {
        for index in exercise.warmupSets.indices {
            steps.append(warmupStep(exercise: exercise, groupId: groupId, index: index))
        }
    }

    private static func warmupStep(exercise: Exercise, groupId: String?, index: Int) -> WorkoutStep {
        let warmupSet = exercise.warmupSets[safe: index]
        return WorkoutStep(
            type: .warmup,
            exerciseId: exercise.id,
            exerciseName: exercise.name,
            stepKey: warmupSet.map { warmupStepKey(exerciseId: exercise.id, warmupSetId: $0.id) } ?? "",
            setIndex: index,
            warmupSetId: warmupSet?.id,
            supersetGroupId: groupId,
            restAfterKind: .warmup
        )
    }

    private static func appendWorkingSetSteps(
        exercise: Exercise,
        setIndex: Int,
        groupId: String?,
        roundIndex: Int?,
        restAfterKind: RestTimerKind,
        steps: inout [WorkoutStep]
    ) {
        if !exercise.isEffectivelyUnilateral() {
            steps.append(WorkoutStep(
                type: .workingSet,
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                stepKey: workingStepKey(exerciseId: exercise.id, setIndex: setIndex),
                setIndex: setIndex,
                supersetGroupId: groupId,
                supersetRoundIndex: roundIndex,
                restAfterKind: restAfterKind
            ))
            return
        }
        let set = exercise.sets[safe: setIndex]
        let hasLeftOnly = set?.leftTarget != nil && set?.rightTarget == nil
        let hasRightOnly = set?.rightTarget != nil && set?.leftTarget == nil
        let sides: [String] = {
            if hasLeftOnly { return ["left"] }
            if hasRightOnly { return ["right"] }
            return exercise.unilateralSideOrder == .leftRight ? ["left", "right"] : ["right", "left"]
        }()
        for (sideIdx, side) in sides.enumerated() {
            steps.append(WorkoutStep(
                type: .workingSet,
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                stepKey: workingStepKey(exerciseId: exercise.id, setIndex: setIndex, side: side),
                setIndex: setIndex,
                side: side,
                supersetGroupId: groupId,
                supersetRoundIndex: roundIndex,
                restAfterKind: (sideIdx == 0 && (exercise.restBetweenSidesSeconds ?? 0) > 0) ? .betweenSides : restAfterKind
            ))
        }
    }
}
