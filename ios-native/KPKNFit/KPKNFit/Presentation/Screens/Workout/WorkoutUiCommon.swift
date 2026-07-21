import Foundation
#if canImport(UIKit)
import UIKit
#endif

// MARK: - Extension to format Doubles nicely
extension Double {
    func toTrimmedNumberString() -> String {
        let rounded = Double(Int(self * 10)) / 10.0
        if rounded == Double(Int(rounded)) {
            return String(Int(rounded))
        } else {
            return String(rounded)
        }
    }
}

// MARK: - Signed Delta Formatting
func formatSignedDelta(value: Double, suffix: String = "") -> String {
    let absValue = abs(value)
    let base = absValue.toTrimmedNumberString()
    let unit = suffix.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "" : suffix
    if value > 0.0 {
        return "+\(base)\(unit)"
    } else if value < 0.0 {
        return "-\(base)\(unit)"
    } else {
        return "0\(unit)"
    }
}

// MARK: - PR Haptic triggers
func triggerPRCelebrationHaptic(context: Any? = nil) {
    #if os(iOS)
    let generator = UINotificationFeedbackGenerator()
    generator.prepare()
    generator.notificationOccurred(.success)
    #endif
}

// MARK: - Time format helper (m:ss or ss)
func formatTime(seconds: Int) -> String {
    let m = seconds / 60
    let s = seconds % 60
    if m > 0 {
        return "\(m):\(String(format: "%02d", s))"
    } else {
        return "\(s)s"
    }
}

// MARK: - Elapsed Timer helper
func formatElapsed(seconds: Int) -> String {
    let hours = seconds / 3600
    let minutes = (seconds % 3600) / 60
    let secs = seconds % 60
    if hours > 0 {
        return String(format: "%d:%02d:%02d", hours, minutes, secs)
    } else {
        return String(format: "%02d:%02d", minutes, secs)
    }
}

// MARK: - Achievement Message formatter
func buildWorkoutAchievementMessage(
    homologated: HomologatedPerformanceResult?,
    showPRsInWorkout: Bool
) -> String? {
    guard let homologated = homologated else { return nil }
    if !showPRsInWorkout {
        if let reason = homologated.suggestionReason, !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return reason
        }
        return nil
    }

    let metric = homologated.metricValue.toTrimmedNumberString()
    let label = homologated.metricType.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Rendimiento" : homologated.metricType

    if homologated.isGlobalPr {
        return "PR global · \(label) \(metric)"
    } else if homologated.isContextPr {
        return "PR contextual · \(label) \(metric)"
    } else if let reason = homologated.suggestionReason, !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return reason
    } else {
        return nil
    }
}
