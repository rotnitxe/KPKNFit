import Foundation

enum SessionMuscleFilter {
    static func relevantMusclesFor(_ exercise: ExerciseMuscleInfo?) -> [InvolvedMuscle] {
        guard let exercise = exercise else { return [] }
        return exercise.involvedMuscles.filter { muscle in
            switch muscle.role {
            case .PRIMARY: return true
            case .SECONDARY: return true
            case .STABILIZER: return false
            case .NEUTRALIZER: return false
            }
        }
    }
}
