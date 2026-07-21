import Foundation

// ─── Recovery ─────────────────────────────────────────────────────────────────

public enum RecoveryStatus: String, Codable {
    case FRESH, OPTIMAL, RECOVERING, EXHAUSTED
}

public struct MuscleRecoveryStatus: Codable {
    public let muscleName: String
    public let recoveryScore: Int
    public let hoursToRecovery: Int
    public let hoursSinceLastSession: Int
    public let effectiveSets: Int
    public let status: RecoveryStatus

    public init(muscleName: String, recoveryScore: Int, hoursToRecovery: Int, hoursSinceLastSession: Int, effectiveSets: Int, status: RecoveryStatus) {
        self.muscleName = muscleName
        self.recoveryScore = recoveryScore
        self.hoursToRecovery = hoursToRecovery
        self.hoursSinceLastSession = hoursSinceLastSession
        self.effectiveSets = effectiveSets
        self.status = status
    }
}

// ─── SetDrain ─────────────────────────────────────────────────────────────────

public struct SetDrain: Codable {
    public let cnsDrainPct: Double
    public let muscularDrainPct: Double
    public let spinalDrainPct: Double
    public init(cnsDrainPct: Double, muscularDrainPct: Double, spinalDrainPct: Double) {
        self.cnsDrainPct = cnsDrainPct
        self.muscularDrainPct = muscularDrainPct
        self.spinalDrainPct = spinalDrainPct
    }
}
