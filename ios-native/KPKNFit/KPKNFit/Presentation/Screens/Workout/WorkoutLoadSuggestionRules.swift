import Foundation

struct WorkoutLoadSuggestionRules {
    static func fatigueFactorForPriorCompletedSets(priorCompletedCount: Int) -> Double {
        switch max(0, priorCompletedCount) {
        case 0: return 1.0
        case 1: return 0.8
        case 2: return 0.6
        default: return 0.5
        }
    }
}
