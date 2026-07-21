import Foundation

enum SessionMuscleFilter {
    static func relevantMusclesFor(_ exercise: ExerciseMuscleInfo?) -> [InvolvedMuscle] {
        guard let exercise = exercise else { return [] }
        let hasHighAxialLoad = (exercise.ssc ?? 0.0) >= 1.5
        return exercise.involvedMuscles.filter { muscle in
            switch muscle.role {
            case .PRIMARY: return true
            case .SECONDARY: return true
            case .STABILIZER: return hasHighAxialLoad
            case .NEUTRALIZER: return false
            }
        }
    }
}
