import Foundation

/// Descriptive authored session contents. No recovery state, risk score or automatic prescription.
enum SessionAssistantEngine {
    static func evaluate(input: SessionAssistantInput, allTemplates: [SessionTemplate] = []) -> SessionAssistantReport {
        let sets = input.allExercisesInSession.reduce(0) { $0 + $1.sets.count }
        let rest = input.allExercisesInSession.reduce(0) { $0 + max(0, $1.sets.count - 1) * ($1.restTime ?? 90) }
        let work = sets * 45 // Explicit planning estimate, never an observed duration.
        return SessionAssistantReport(veredicto: nil, scoreEstimado: nil, riesgos: [], ajustes: [], oportunidades: [],
            tarjetasFantasma: [], plantillasCompatibles: [], volumenPorMusculo: [:], umbralesPorMusculo: [:],
            drenajeEstimado: nil, duracionEstimada: (rest + work + 59) / 60,
            resumenTexto: "Contenido registrado en tu programa; duración estimada", totalRestSeconds: rest, estimatedWorkSeconds: work)
    }
    static func resolveExerciseInfo(exercise: Exercise, exerciseIndex: [String: ExerciseMuscleInfo]) -> ExerciseMuscleInfo? {
        let byId = exercise.exerciseDbId ?? exercise.exerciseId
        guard let key = byId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !key.isEmpty else {
            return nil
        }
        return exerciseIndex[key]
    }

    static func findNextSessionWithMuscles(
        currentSessionId: String,
        weekSessions: [Session],
        muscleIds: [String],
        exerciseIndex: [String: ExerciseMuscleInfo]
    ) -> Session? {
        guard let idx = weekSessions.firstIndex(where: { $0.id == currentSessionId }) else { return nil }

        for i in (idx + 1)..<weekSessions.count {
            if sessionContainsAnyMuscle(session: weekSessions[i], muscleIds: muscleIds, exerciseIndex: exerciseIndex) {
                return weekSessions[i]
            }
        }

        for i in 0..<idx {
            if sessionContainsAnyMuscle(session: weekSessions[i], muscleIds: muscleIds, exerciseIndex: exerciseIndex) {
                return weekSessions[i]
            }
        }

        return nil
    }

    private static func sessionContainsAnyMuscle(
        session: Session,
        muscleIds: [String],
        exerciseIndex: [String: ExerciseMuscleInfo]
    ) -> Bool {
        for ex in session.allExercises() {
            let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
            guard let info = exerciseIndex[dbId] else { continue }
            for muscle in info.involvedMuscles where muscle.role == .PRIMARY {
                let mId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                if muscleIds.contains(mId) { return true }
            }
        }
        return false
    }

    // ─── Volume Discounts ────────────────────────────────────────────────────

    static func computeProposedDiscounts(
        currentSession: Session,
        nextSession: Session,
        targetMuscles: [String],
        completedSets: [String: CompletedSet],
        exerciseIndex: [String: ExerciseMuscleInfo]
    ) -> [MuscleAdvance] {
        var muscleAdvances = [MuscleAdvance]()
        var completedByExercise = [String: Int]()
        for (key, _) in completedSets {
            var exerciseId = key.substringBeforeLast("_")
            if exerciseId.hasSuffix("_L") || exerciseId.hasSuffix("_R") {
                exerciseId = exerciseId.substringBeforeLast("_")
            }
            completedByExercise[exerciseId, default: 0] += 1
        }

        for muscleId in targetMuscles {
            var plannedSets = 0.0
            for ex in currentSession.allExercises() {
                let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
                guard let info = exerciseIndex[dbId] else { continue }
                for m in info.involvedMuscles where m.role == .PRIMARY {
                    let mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis)
                    if mId == muscleId {
                        plannedSets += Double(ex.sets.count)
                    }
                }
            }

            var actualSets = 0.0
            for ex in currentSession.allExercises() {
                let sets = completedByExercise[ex.id] ?? 0
                if sets == 0 { continue }
                let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
                guard let info = exerciseIndex[dbId] else { continue }
                for m in info.involvedMuscles where m.role == .PRIMARY {
                    let mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis)
                    if mId == muscleId {
                        actualSets += Double(sets)
                    }
                }
            }

            let delta = actualSets - plannedSets
            if delta <= 0.0 { continue }

            var proposals = [VolumeDiscountProposal]()
            var remaining = delta

            for ex in nextSession.allExercises() {
                if remaining <= 0.0 { break }
                let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
                guard let info = exerciseIndex[dbId] else { continue }
                let trainsMuscle = info.involvedMuscles.contains { m in
                    m.role == .PRIMARY && VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis) == muscleId
                }
                if trainsMuscle {
                    let maxDiscount = Double(max(0, ex.sets.count - 1))
                    if maxDiscount > 0 {
                        let d = min(remaining, maxDiscount)
                        proposals.append(VolumeDiscountProposal(
                            exerciseId: ex.id,
                            exerciseName: ex.name,
                            currentRole: "PRIMARY",
                            discountSets: d,
                            reason: "Ajuste de volumen"
                        ))
                        remaining -= d
                    }
                }
            }

            if !proposals.isEmpty {
                muscleAdvances.append(MuscleAdvance(
                    muscleId: muscleId, muscleName: muscleId,
                    currentSets: actualSets, targetSets: plannedSets, deficitSets: delta,
                    targetSessionId: nextSession.id, targetSessionName: nextSession.name,
                    discountProposals: proposals
                ))
            }
        }

        return muscleAdvances
    }
}

// MARK: - Extensions

extension Exercise {
    func validAugeSets() -> [ExerciseSet] {
        sets.filter { !$0.isIneffective }
    }
}

extension ExerciseSet {
    func effectiveTargetRpe() -> Double {
        if isFailure || intensityMode == .FAILURE { return 10.0 }
        if let rpe = targetRPE { return max(1.0, min(10.0, rpe)) }
        if let rir = targetRIR { return max(1.0, min(10.0, 10.0 - Double(rir))) }
        return 8.0
    }
}

extension String {
    func substringBeforeLast(_ separator: String) -> String {
        if let range = range(of: separator, options: .backwards) {
            return String(self[..<range.lowerBound])
        }
        return self
    }
}

extension Dictionary {
    mutating func getOrPut(_ key: Key, default factory: () -> Value) -> Value {
        if let value = self[key] { return value }
        let value = factory()
        self[key] = value
        return value
    }
}

extension Array {
    func groupedBy<GroupKey: Hashable>(_ keySelector: (Element) -> GroupKey) -> [GroupKey: [Element]] {
        var result = [GroupKey: [Element]]()
        for element in self {
            let key = keySelector(element)
            result[key, default: []].append(element)
        }
        return result
    }
}
