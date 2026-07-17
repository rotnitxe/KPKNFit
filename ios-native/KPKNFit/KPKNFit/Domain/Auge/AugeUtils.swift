import Foundation

internal struct AugeUtils {

    static func physiologicalFloor(settings: Settings) -> PhysiologicalFloor {
        switch settings.athleteType {
        case .POWERLIFTER, .WEIGHTLIFTER:
            return PhysiologicalFloor(muscular: 15, cns: 20, spinal: 12)
        case .BODYBUILDER, .POWERBUILDER:
            return PhysiologicalFloor(muscular: 18, cns: 22, spinal: 14)
        case .CALISTHENICS:
            return PhysiologicalFloor(muscular: 20, cns: 24, spinal: 16)
        case .HYBRID, .ZERCHER_LIFTER:
            return PhysiologicalFloor(muscular: 20, cns: 25, spinal: 18)
        case .ENTHUSIAST:
            return PhysiologicalFloor(muscular: 22, cns: 26, spinal: 18)
        }
    }

    static func logDateMs(log: WorkoutLog) -> Int64 {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        let fallback = DateFormatter()
        fallback.dateFormat = "yyyy-MM-dd"
        
        if let date = formatter.date(from: log.date) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        if let date = fallback.date(from: String(log.date.prefix(10))) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        return 0
    }

    static func parseIsoMs(_ dateString: String) -> Int64 {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        let fallback = DateFormatter()
        fallback.dateFormat = "yyyy-MM-dd"
        
        if let date = formatter.date(from: dateString) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        if let date = fallback.date(from: String(dateString.prefix(10))) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        return 0
    }

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double {
        return min(hi, max(lo, v))
    }

    static func safeExp(_ v: Double) -> Double {
        let r = exp(v)
        return (r.isNaN || r.isInfinite) ? 0.0 : r
    }

    static func decelerateBattery(_ battery: Double) -> Double {
        let b = min(100.0, max(0.0, battery))
        return b < 30.0 ? 30.0 * sqrt(b / 30.0) : b
    }

    static func getSigmoidalHours(_ hoursSince: Double) -> Double {
        if hoursSince < 24.0 {
            return hoursSince * 0.15
        } else {
            return 3.6 + (hoursSince - 24.0) * 1.35
        }
    }

    static func getSpinalRecoveryHours(_ hoursSince: Double) -> Double {
        if hoursSince < 12.0 {
            return hoursSince
        }
        return hoursSince + 18.0
    }
}
