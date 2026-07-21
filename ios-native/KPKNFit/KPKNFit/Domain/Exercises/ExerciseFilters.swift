import Foundation

func matchesMuscle(_ exercise: ExerciseMuscleInfo, anatomy: MuscleAnatomy) -> Bool {
    let involvements = exercise.involvedMuscles.filter {
        VolumeCalculator.normalizeCanonicalMuscleGroup($0.muscle, emphasis: $0.emphasis) == anatomy.canonicalName
    }
    if involvements.isEmpty { return false }

    if anatomy.canonicalName.lowercased() == "trapecio" {
        return involvements.contains { $0.role == .PRIMARY || $0.role == .SECONDARY }
    }
    return true
}

func matchesMuscleHead(
    _ exercise: ExerciseMuscleInfo,
    anatomy: MuscleAnatomy,
    head: MuscleHead
) -> Bool {
    if !matchesMuscle(exercise, anatomy: anatomy) { return false }
    return exercise.involvedMuscles.contains { involvement in
        let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, emphasis: involvement.emphasis)
        guard canonical == anatomy.canonicalName else { return false }
        let emphasisLower = (involvement.emphasis ?? "").lowercased().trimmingCharacters(in: .whitespaces)
        if let keyword = head.emphasisKeyword {
            return emphasisLower == keyword
        } else {
            return emphasisLower.isEmpty
        }
    }
}
