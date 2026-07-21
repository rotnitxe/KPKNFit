import Foundation

// MARK: - Exercise copy convenience

extension Exercise {
    fileprivate func copy(
        supersetGroupRef: String?? = nil,
        supersetId: String?? = nil,
        supersetRestBetween: Int?? = nil,
        supersetRestAfter: Int?? = nil,
        restTime: Int?? = nil
    ) -> Exercise {
        Exercise(
            id: id,
            name: name,
            exerciseDbId: exerciseDbId,
            exerciseId: exerciseId,
            canonicalExerciseId: canonicalExerciseId,
            exerciseFamilyId: exerciseFamilyId,
            relativeToCanonicalExerciseId: relativeToCanonicalExerciseId,
            relationshipType: relationshipType,
            relationshipNotes: relationshipNotes,
            sets: sets,
            warmupSets: warmupSets,
            restTime: restTime ?? self.restTime,
            isFavorite: isFavorite,
            trainingMode: trainingMode,
            customUnit: customUnit,
            reference1RM: reference1RM,
            targetSessionGoal: targetSessionGoal,
            isStarTarget: isStarTarget,
            trackHeartRate: trackHeartRate,
            trackRom: trackRom,
            setupDetails: setupDetails,
            supersetId: supersetId ?? self.supersetId,
            supersetRestBetween: supersetRestBetween ?? self.supersetRestBetween,
            supersetRestAfter: supersetRestAfter ?? self.supersetRestAfter,
            supersetGroupRef: supersetGroupRef ?? self.supersetGroupRef,
            variantName: variantName,
            selectedExecutionOption: selectedExecutionOption,
            selectedMovementPattern: selectedMovementPattern,
            prFor1RM: prFor1RM,
            consolidatedWeight: consolidatedWeight,
            brandEquivalencies: brandEquivalencies,
            isUnilateral: isUnilateral,
            unilateralMode: unilateralMode,
            unilateralSideOrder: unilateralSideOrder,
            unilateralIntensityMode: unilateralIntensityMode,
            restBetweenSidesSeconds: restBetweenSidesSeconds,
            isCalibratorAmrap: isCalibratorAmrap,
            goal1RM: goal1RM,
            goalPr: goalPr,
            calculated1RM: calculated1RM,
            damageProfile: damageProfile,
            isCompetitionLift: isCompetitionLift,
            setupCues: setupCues,
            executionCues: executionCues,
            contextProfilesV3: contextProfilesV3,
            defaultContextProfileIdV3: defaultContextProfileIdV3,
            mobilitySeries: mobilitySeries,
            timeStrategy: timeStrategy,
            targetDurationMinutes: targetDurationMinutes
        )
    }
}



// MARK: - SupersetRules

public enum SupersetRules {
    private static let maxSupersetMembers = 4

    public static func normalizeSession(session: Session) -> Session {
        let allExercises = session.allExercises()
        if allExercises.isEmpty { return session }

        let existingGroups = Dictionary(uniqueKeysWithValues: session.supersetGroups.map { ($0.id, $0) })
        var seen = Set<String>()
        let groupIds = allExercises
            .compactMap { $0.supersetGroupRefOrLegacyId() }
            .filter { seen.insert($0).inserted }

        if groupIds.isEmpty {
            return session.supersetGroups.isEmpty ? session : session.copy(supersetGroups: [])
        }

        let normalizedGroups: [SupersetGroup] = groupIds.compactMap { groupId in
            let members = allExercises.filter { $0.supersetGroupRefOrLegacyId() == groupId }
            guard members.count >= 2 else { return nil }
            let existing = existingGroups[groupId]
            var seenOrder = Set<String>()
            let orderedFromExisting = (existing?.exerciseOrder ?? []).filter { id in
                members.contains(where: { $0.id == id }) && seenOrder.insert(id).inserted
            }
            var seenCombined = Set<String>()
            let allOrdered = Array((orderedFromExisting + members.map { $0.id })
                .filter { seenCombined.insert($0).inserted }
                .prefix(maxSupersetMembers))
            guard allOrdered.count >= 2 else { return nil }
            return SupersetGroup(
                id: groupId,
                exerciseOrder: allOrdered,
                restBetweenExercises: existing?.restBetweenExercises
                    ?? members.first?.supersetRestBetween
                    ?? 60,
                restAfterSuperset: existing?.restAfterSuperset
                    ?? members.first?.supersetRestAfter
                    ?? 120,
                rounds: existing?.rounds,
                visualPlacement: existing?.visualPlacement,
                roundRestBetweenExercises: existing?.roundRestBetweenExercises ?? [:],
                roundRestAfterSuperset: existing?.roundRestAfterSuperset ?? [:]
            )
        }
        let validGroupIds = Set(normalizedGroups.map { $0.id })
        let groupById = Dictionary(uniqueKeysWithValues: normalizedGroups.map { ($0.id, $0) })

        func updateExercise(exercise: Exercise) -> Exercise {
            let groupId = exercise.supersetGroupRefOrLegacyId()
            let group = groupId.flatMap { id -> SupersetGroup? in
                guard let g = groupById[id], g.exerciseOrder.contains(exercise.id) else { return nil }
                return g
            }
            if let g = group {
                return exercise.copy(
                    supersetGroupRef: g.id,
                    supersetId: g.id,
                    supersetRestBetween: g.restBetweenExercises,
                    supersetRestAfter: g.restAfterSuperset
                )
            } else {
                return exercise.copy(
                    supersetGroupRef: nil,
                    supersetId: nil,
                    supersetRestBetween: nil,
                    supersetRestAfter: nil
                )
            }
        }

        return session.copy(
            exercises: session.exercises.map(updateExercise),
            parts: session.parts.map { SessionPart(id: $0.id, name: $0.name, exercises: $0.exercises.map(updateExercise), color: $0.color, targetDurationMinutes: $0.targetDurationMinutes) },
            supersetGroups: normalizedGroups.filter { validGroupIds.contains($0.id) }
        )
    }

    public static func createSuperset(
        session: Session,
        groupId: String,
        exerciseIds: [String],
        restBetweenExercises: Int,
        restAfterSuperset: Int,
        rounds: Int? = nil,
        anchorPartId: String? = nil,
        anchorExerciseId: String? = nil
    ) -> Session {
        let resolvedAnchorExerciseId = anchorExerciseId ?? exerciseIds.first
        var seenTarget = Set<String>()
        let targetIds = Array(exerciseIds.filter { seenTarget.insert($0).inserted }.prefix(maxSupersetMembers))
        guard targetIds.count >= 2 else { return session }
        let allIds = Set(session.allExercises().map { $0.id })
        guard targetIds.allSatisfy({ allIds.contains($0) }) else { return session }

        let group = SupersetGroup(
            id: groupId,
            exerciseOrder: targetIds,
            restBetweenExercises: max(0, restBetweenExercises),
            restAfterSuperset: max(0, restAfterSuperset),
            rounds: rounds.flatMap { max(1, $0) },
            visualPlacement: SupersetVisualPlacement(
                partId: anchorPartId,
                anchorExerciseId: resolvedAnchorExerciseId
            ),
            roundRestBetweenExercises: rounds.map { count in
                Dictionary(uniqueKeysWithValues: (0..<max(1, count)).map { ($0, max(0, restBetweenExercises)) })
            } ?? [:],
            roundRestAfterSuperset: rounds.map { count in
                Dictionary(uniqueKeysWithValues: (0..<max(1, count)).map { ($0, max(0, restAfterSuperset)) })
            } ?? [:]
        )

        let previousGroupIds: Set<String> = Set(
            session.allExercises()
                .filter { targetIds.contains($0.id) }
                .compactMap { $0.supersetGroupRefOrLegacyId() }
        ).subtracting([groupId])

        let allCurrentExercises = session.allExercises()
        let updatedMembers = targetIds.compactMap { id -> Exercise? in
            allCurrentExercises.first(where: { $0.id == id })?.copy(
                supersetGroupRef: groupId,
                supersetId: groupId,
                supersetRestBetween: group.restBetweenExercises,
                supersetRestAfter: group.restAfterSuperset
            )
        }

        var resultSession = session.copy(
            exercises: session.exercises.filter { !targetIds.contains($0.id) },
            parts: session.parts.map { SessionPart(id: $0.id, name: $0.name, exercises: $0.exercises.filter { !targetIds.contains($0.id) }, color: $0.color, targetDurationMinutes: $0.targetDurationMinutes) }
        )

        let requestedAnchorPartId = anchorPartId.flatMap { requestedId in
            session.parts.contains(where: { $0.id == requestedId }) ? requestedId : nil
        }
        let resolvedAnchorPartId = requestedAnchorPartId
            ?? session.parts.first(where: { $0.exercises.contains(where: { $0.id == resolvedAnchorExerciseId }) })?.id

        if resolvedAnchorPartId == nil {
            var mutableLoose = resultSession.exercises
            let originalAnchorIdx = session.exercises.firstIndex(where: { $0.id == resolvedAnchorExerciseId }) ?? -1
            let safeIdx: Int
            if originalAnchorIdx >= 0 {
                let removedBefore = session.exercises.prefix(originalAnchorIdx).filter { targetIds.contains($0.id) }.count
                safeIdx = min(max(0, originalAnchorIdx - removedBefore), mutableLoose.count)
            } else {
                safeIdx = mutableLoose.count
            }
            mutableLoose.insert(contentsOf: updatedMembers, at: safeIdx)
            resultSession = resultSession.copy(exercises: mutableLoose)
        } else {
            resultSession = resultSession.copy(
                parts: resultSession.parts.map { part in
                    guard part.id == resolvedAnchorPartId else { return part }
                    var mutableP = part.exercises
                    let originalAnchorIdx = session.parts.first(where: { $0.id == resolvedAnchorPartId })?.exercises.firstIndex(where: { $0.id == resolvedAnchorExerciseId }) ?? -1
                    let safeIdx: Int
                    if originalAnchorIdx >= 0 {
                        let removedBefore = session.parts.first(where: { $0.id == resolvedAnchorPartId })!.exercises.prefix(originalAnchorIdx).filter { targetIds.contains($0.id) }.count
                        safeIdx = min(max(0, originalAnchorIdx - removedBefore), mutableP.count)
                    } else {
                        safeIdx = mutableP.count
                    }
                    mutableP.insert(contentsOf: updatedMembers, at: safeIdx)
                    return SessionPart(id: part.id, name: part.name, exercises: mutableP, color: part.color, targetDurationMinutes: part.targetDurationMinutes)
                }
            )
        }

        return normalizeSession(session: resultSession.copy(
            supersetGroups: resultSession.supersetGroups
                .filter { $0.id != groupId }
                .map { existing in
                    guard previousGroupIds.contains(existing.id) else { return existing }
                    let nextOrder = existing.exerciseOrder.filter { !targetIds.contains($0) }
                    return SupersetGroup(id: existing.id, exerciseOrder: nextOrder, restBetweenExercises: existing.restBetweenExercises, restAfterSuperset: existing.restAfterSuperset, rounds: existing.rounds, visualPlacement: existing.visualPlacement, roundRestBetweenExercises: existing.roundRestBetweenExercises, roundRestAfterSuperset: existing.roundRestAfterSuperset, isOptional: existing.isOptional)
                }
                .filter { !previousGroupIds.contains($0.id) || $0.exerciseOrder.count >= 2 }
                + [group]
        ))
    }

    public static func orderedMembers(session: Session, groupId: String) -> [Exercise] {
        let group = session.allSupersetGroups().first { $0.id == groupId }
        let members = session.allExercises().filter { $0.supersetGroupRefOrLegacyId() == groupId }
        if members.isEmpty { return [] }
        let byId = Dictionary(uniqueKeysWithValues: members.map { ($0.id, $0) })
        let ordered = (group?.exerciseOrder ?? []).compactMap { byId[$0] }
        let orderedIds = Set(ordered.map { $0.id })
        return ordered + members.filter { !orderedIds.contains($0.id) }
    }

    public static func roundCount(session: Session, groupId: String) -> Int {
        let group = session.allSupersetGroups().first { $0.id == groupId }
        if let plannedRounds = group?.rounds, plannedRounds > 0 { return plannedRounds }
        return orderedMembers(session: session, groupId: groupId).compactMap { $0.sets.count }.max() ?? 0
    }

    public static func nextTarget(
        session: Session,
        visibleExercises: [Exercise],
        currentExerciseIdx: Int,
        currentSetIdx: Int
    ) -> (Int, Int)? {
        guard currentExerciseIdx >= 0, currentExerciseIdx < visibleExercises.count else { return nil }
        let current = visibleExercises[currentExerciseIdx]
        guard let groupId = current.supersetGroupRefOrLegacyId() else { return nil }
        let orderedIds = orderedMembers(session: session, groupId: groupId).map { $0.id }
        guard orderedIds.count >= 2 else { return nil }
        guard let currentOrderIdx = orderedIds.firstIndex(of: current.id) else { return nil }

        func visibleIndexFor(exerciseId: String) -> Int {
            visibleExercises.firstIndex(where: { $0.id == exerciseId }) ?? -1
        }

        for orderIdx in (currentOrderIdx + 1)..<orderedIds.count {
            let exerciseId = orderedIds[orderIdx]
            let visibleIdx = visibleIndexFor(exerciseId: exerciseId)
            if visibleIdx < 0 { continue }
            let exercise = visibleExercises[visibleIdx]
            if currentSetIdx >= 0 && currentSetIdx < exercise.sets.count {
                return (visibleIdx, currentSetIdx)
            }
        }

        let nextRound = currentSetIdx + 1
        for exerciseId in orderedIds {
            let visibleIdx = visibleIndexFor(exerciseId: exerciseId)
            if visibleIdx < 0 { continue }
            let exercise = visibleExercises[visibleIdx]
            if nextRound >= 0 && nextRound < exercise.sets.count {
                return (visibleIdx, nextRound)
            }
        }

        return nil
    }

    public static func updateRest(
        session: Session,
        groupId: String,
        restBetweenExercises: Int? = nil,
        restAfterSuperset: Int? = nil,
        rounds: Int? = nil
    ) -> Session {
        guard let existing = session.allSupersetGroups().first(where: { $0.id == groupId }) else { return session }
        let nextRestBetween = restBetweenExercises.map { max(0, $0) } ?? existing.restBetweenExercises
        let nextRestAfter = restAfterSuperset.map { max(0, $0) } ?? existing.restAfterSuperset
        let nextRounds = rounds.map { max(1, $0) } ?? existing.rounds
        let roundCount = nextRounds ?? self.roundCount(session: session, groupId: groupId)
        let nextRoundBetween = Dictionary(uniqueKeysWithValues: (0..<roundCount).map { round in
            (round, existing.roundRestBetweenExercises[round] ?? nextRestBetween)
        })
        let nextRoundAfter = Dictionary(uniqueKeysWithValues: (0..<roundCount).map { round in
            (round, existing.roundRestAfterSuperset[round] ?? nextRestAfter)
        })

        func updateExercise(exercise: Exercise) -> Exercise {
            guard exercise.supersetGroupRefOrLegacyId() == groupId else { return exercise }
            return exercise.copy(
                supersetRestBetween: nextRestBetween,
                supersetRestAfter: nextRestAfter
            )
        }

        return normalizeSession(session: session.copy(
            exercises: session.exercises.map(updateExercise),
            parts: session.parts.map { SessionPart(id: $0.id, name: $0.name, exercises: $0.exercises.map(updateExercise), color: $0.color, targetDurationMinutes: $0.targetDurationMinutes) },
            supersetGroups: session.supersetGroups.map { group in
                guard group.id == groupId else { return group }
                return SupersetGroup(
                    id: group.id,
                    exerciseOrder: group.exerciseOrder,
                    restBetweenExercises: nextRestBetween,
                    restAfterSuperset: nextRestAfter,
                    rounds: nextRounds,
                    visualPlacement: group.visualPlacement,
                    roundRestBetweenExercises: nextRoundBetween,
                    roundRestAfterSuperset: nextRoundAfter,
                    isOptional: group.isOptional
                )
            }
        ))
    }

    public static func updateRoundRest(
        session: Session,
        groupId: String,
        roundIndex: Int,
        restBetweenExercises: Int? = nil,
        restAfterSuperset: Int? = nil
    ) -> Session {
        let safeRound = max(0, roundIndex)
        return normalizeSession(session: session.copy(
            supersetGroups: session.supersetGroups.map { group in
                guard group.id == groupId else { return group }
                return SupersetGroup(
                    id: group.id,
                    exerciseOrder: group.exerciseOrder,
                    restBetweenExercises: group.restBetweenExercises,
                    restAfterSuperset: group.restAfterSuperset,
                    rounds: group.rounds,
                    visualPlacement: group.visualPlacement,
                    roundRestBetweenExercises: restBetweenExercises.map {
                        group.roundRestBetweenExercises.merging([safeRound: max(0, $0)]) { $1 }
                    } ?? group.roundRestBetweenExercises,
                    roundRestAfterSuperset: restAfterSuperset.map {
                        group.roundRestAfterSuperset.merging([safeRound: max(0, $0)]) { $1 }
                    } ?? group.roundRestAfterSuperset,
                    isOptional: group.isOptional
                )
            }
        ))
    }

    public static func removeExercise(session: Session, groupId: String, exerciseId: String) -> Session {
        guard let group = session.allSupersetGroups().first(where: { $0.id == groupId }) else { return session }
        let memberIds: [String] = {
            let ordered = orderedMembers(session: session, groupId: groupId).map { $0.id }
            return ordered.isEmpty ? group.exerciseOrder : ordered
        }()
        let remainingIds = memberIds.filter { $0 != exerciseId }
        let idsToClear: Set<String> = remainingIds.count <= 1 ? Set(memberIds) : [exerciseId]

        func updateExercise(exercise: Exercise) -> Exercise {
            guard idsToClear.contains(exercise.id) else { return exercise }
            return exercise.copy(
                supersetGroupRef: nil,
                supersetId: nil,
                supersetRestBetween: nil,
                supersetRestAfter: nil
            )
        }

        let updatedGroups: [SupersetGroup]
        if remainingIds.count <= 1 {
            updatedGroups = session.supersetGroups.filter { $0.id != groupId }
        } else {
            updatedGroups = session.supersetGroups.map {
                guard $0.id == groupId else { return $0 }
                return SupersetGroup(
                    id: $0.id,
                    exerciseOrder: remainingIds,
                    restBetweenExercises: $0.restBetweenExercises,
                    restAfterSuperset: $0.restAfterSuperset,
                    rounds: $0.rounds,
                    visualPlacement: $0.visualPlacement,
                    roundRestBetweenExercises: $0.roundRestBetweenExercises,
                    roundRestAfterSuperset: $0.roundRestAfterSuperset,
                    isOptional: $0.isOptional
                )
            }
        }

        return normalizeSession(session: session.copy(
            exercises: session.exercises.map(updateExercise),
            parts: session.parts.map { SessionPart(id: $0.id, name: $0.name, exercises: $0.exercises.map(updateExercise), color: $0.color, targetDurationMinutes: $0.targetDurationMinutes) },
            supersetGroups: updatedGroups
        ))
    }

    public static func dissolve(session: Session, groupId: String) -> Session {
        let group = session.allSupersetGroups().first { $0.id == groupId }
        let copiedRoundRest = group?.restAfterSuperset.flatMap { $0 > 0 ? $0 : nil }

        func updateExercise(exercise: Exercise) -> Exercise {
            guard exercise.supersetGroupRefOrLegacyId() == groupId else { return exercise }
            return exercise.copy(
                supersetGroupRef: nil,
                supersetId: nil,
                supersetRestBetween: nil,
                supersetRestAfter: nil,
                restTime: copiedRoundRest ?? exercise.restTime
            )
        }

        return session.copy(
            exercises: session.exercises.map(updateExercise),
            parts: session.parts.map { SessionPart(id: $0.id, name: $0.name, exercises: $0.exercises.map(updateExercise), color: $0.color, targetDurationMinutes: $0.targetDurationMinutes) },
            supersetGroups: session.supersetGroups.filter { $0.id != groupId }
        )
    }

    public static func moveGroup(
        session: Session,
        groupId: String,
        targetPartId: String?,
        targetIndex: Int?
    ) -> Session {
        let orderedMembers = orderedMembers(session: session, groupId: groupId)
        guard orderedMembers.count >= 2 else { return session }
        let movingIds = Set(orderedMembers.map { $0.id })

        func withoutGroup(exercises: [Exercise]) -> [Exercise] {
            exercises.filter { !movingIds.contains($0.id) }
        }

        let sessionWithout = session.copy(
            exercises: withoutGroup(exercises: session.exercises),
            parts: session.parts.map { SessionPart(id: $0.id, name: $0.name, exercises: withoutGroup(exercises: $0.exercises), color: $0.color, targetDurationMinutes: $0.targetDurationMinutes) }
        )

        func insertInto(exercises: [Exercise]) -> [Exercise] {
            let index = min(max(0, targetIndex ?? exercises.count), exercises.count)
            var mutable = exercises
            mutable.insert(contentsOf: orderedMembers, at: index)
            return mutable
        }

        if targetPartId == nil {
            return sessionWithout.copy(exercises: insertInto(exercises: sessionWithout.exercises))
        } else {
            return sessionWithout.copy(
                parts: sessionWithout.parts.map { part in
                    guard part.id == targetPartId else { return part }
                    return SessionPart(id: part.id, name: part.name, exercises: insertInto(exercises: part.exercises), color: part.color, targetDurationMinutes: part.targetDurationMinutes)
                }
            )
        }
    }

    public static func exercisesInContainer(part: SessionPart?, session: Session) -> [Exercise] {
        part?.exercises ?? session.exercises
    }
}
