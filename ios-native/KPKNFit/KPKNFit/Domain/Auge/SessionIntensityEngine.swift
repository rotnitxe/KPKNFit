import Foundation

struct SessionIntensityResult {
    let numericValue: Double
    let adjustedNumericValue: Double
    let displayLabel: String
    let hasFailure: Bool
    let hasTechniques: Bool
    let completitudRatio: Double
    let normalizationFactor: Double
}

enum SessionIntensityEngine {

    private static func getRpe(_ set: CompletedSet) -> Double {
        let base: Double
        switch set.actualIntensityMode {
        case .FAILURE:
            base = 10.0
        case .RPE:
            base = set.actualIntensityValue ?? Double(10 - (set.rir ?? 3))
        case .RIR:
            base = 10.0 - (set.actualIntensityValue ?? 3.0)
        default:
            base = set.rpe ?? 7.0
        }
        return max(1.0, min(base, 10.0))
    }

    private static func hasAnyTechniques(_ set: CompletedSet) -> Bool {
        !set.dropSets.isEmpty || !set.restPauses.isEmpty || (set.isPartial && (set.partialReps ?? 0) > 0)
    }

    static func calculateAverageSessionIntensity(
        completedExercises: [CompletedExercise],
        totalExercisesPlanned: Int
    ) -> SessionIntensityResult {
        let effectiveExercises = completedExercises.filter { ex in
            ex.sets.contains { !$0.isWarmup && !$0.skipped }
        }

        let plannedOrDone = max(totalExercisesPlanned, 1)
        let doneCount = effectiveExercises.count
        let completitudRatio = Double(doneCount) / Double(plannedOrDone)
        let normalizationFactor = max(0.5, min(0.5 + completitudRatio * 0.5, 1.0))

        var failureFlag = false
        var techniquesFlag = false
        let exerciseAverages: [Double] = effectiveExercises.compactMap { (exercise: CompletedExercise) -> Double? in
            let workingSets = exercise.sets.filter { !$0.isWarmup && !$0.skipped && $0.weight > 0 && $0.reps > 0 }
            if workingSets.isEmpty { return nil }

            var exFailure = false
            var exTechniques = false
            let rpes = workingSets.map { s -> Double in
                if s.isFailure || s.actualIntensityMode == .FAILURE { exFailure = true }
                if hasAnyTechniques(s) { exTechniques = true }
                return getRpe(s)
            }
            if exFailure { failureFlag = true }
            if exTechniques { techniquesFlag = true }
            return rpes.reduce(0, +) / Double(rpes.count)
        }

        let baseAverage = exerciseAverages.isEmpty ? 7.0 : exerciseAverages.reduce(0, +) / Double(exerciseAverages.count)
        let adjustedAverage = baseAverage * normalizationFactor

        let numericValue = max(1.0, min(baseAverage, 10.0))
        let adjustedNumeric = max(0.5, min(adjustedAverage, 10.0))

        var displayLabel = String(format: "%.1f", adjustedNumeric)
        if failureFlag { displayLabel += "+" }
        if techniquesFlag { displayLabel += "+" }

        return SessionIntensityResult(
            numericValue: numericValue,
            adjustedNumericValue: adjustedNumeric,
            displayLabel: displayLabel,
            hasFailure: failureFlag,
            hasTechniques: techniquesFlag,
            completitudRatio: completitudRatio,
            normalizationFactor: normalizationFactor
        )
    }
}
