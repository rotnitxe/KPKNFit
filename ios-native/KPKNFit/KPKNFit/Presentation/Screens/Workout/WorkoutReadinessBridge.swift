import Foundation

struct WorkoutReadinessBridge {
    struct ReadinessAdjustments {
        let neural: Int?
        let muscular: Int?
        let spinal: Int?
        let perMuscle: [String: Int]
        let sleepQuality: Int?
    }

    private static var pending: ReadinessAdjustments? = nil

    static func store(adjustments: ReadinessAdjustments) {
        pending = adjustments
    }

    static func consume() -> ReadinessAdjustments? {
        let result = pending
        pending = nil
        return result
    }
}
