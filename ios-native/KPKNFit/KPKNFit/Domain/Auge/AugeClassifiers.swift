import Foundation

enum AugeClassifiers {

    private static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double {
        min(hi, max(lo, v))
    }

    // MARK: - Effective Volume Multiplier

    static func getEffectiveVolumeMultiplier(rpe: Double) -> Double {
        switch rpe {
        case 10.0...: return 1.2
        case 8.0...: return 1.0
        default: return 0.6
        }
    }

    // MARK: - ACWR Zones

    enum AcwrZone: String {
        case under = "Sub-entrenando"
        case safe = "Zona Segura"
        case caution = "Zona de Riesgo"
        case danger = "Alto Riesgo"
    }

    static func classifyAcwrZone(_ acwr: Double) -> AcwrZone {
        if acwr < 0.8 { return .under }
        if acwr > 1.5 { return .danger }
        if acwr > 1.3 { return .caution }
        return .safe
    }

    static func acwrZoneLabel(_ zone: AcwrZone) -> String { zone.rawValue }

    static func acwrZoneColor(_ zone: AcwrZone) -> String {
        switch zone {
        case .under: return "#38BDF8"
        case .safe: return "#00FF9D"
        case .caution: return "#FFD600"
        case .danger: return "#FF2E43"
        }
    }

    // MARK: - Session Stress Zones

    enum StressZone: String {
        case low = "Bajo"
        case optimal = "Óptimo"
        case high = "Alto"
        case excessive = "Excesivo"
    }

    static func classifyStressZone(_ score: Double) -> StressZone {
        if score < 40.0 { return .low }
        if score < 80.0 { return .optimal }
        if score < 120.0 { return .high }
        return .excessive
    }

    static func stressZoneLabel(_ zone: StressZone) -> String { zone.rawValue }

    static func stressZoneColor(_ zone: StressZone) -> String {
        switch zone {
        case .low: return "#38BDF8"
        case .optimal: return "#00FF9D"
        case .high: return "#FFD600"
        case .excessive: return "#FF2E43"
        }
    }

    // MARK: - Adaptive Recovery Rate Learning

    static func learnRecoveryRate(
        currentMultiplier: Double,
        calculatedScore: Double,
        manualFeel: Double
    ) -> Double {
        let diff = manualFeel - calculatedScore
        let adjustment = diff * 0.005
        return clamp(currentMultiplier + adjustment, 0.5, 2.0)
    }

    // MARK: - Normalization

    static func normalizeToTenScale(batteryScore: Double) -> Double {
        max(1.0, min(batteryScore / 10.0, 10.0))
    }
}
