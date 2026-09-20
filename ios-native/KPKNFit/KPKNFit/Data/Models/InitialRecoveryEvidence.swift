import Foundation

public enum InitialRecoveryActivityType: String, Codable {
    case STRENGTH, CARDIO, MIXED
}

public enum InitialRecoveryIntensity: String, Codable {
    case EASY, MODERATE, HARD, VERY_HARD
}

public struct InitialRecoverySensations: Codable {
    public let muscular: Int?
    public let energy: Int?
    public let structure: Int?

    public init(muscular: Int? = nil, energy: Int? = nil, structure: Int? = nil) {
        self.muscular = muscular
        self.energy = energy
        self.structure = structure
    }

    public func normalized() -> InitialRecoverySensations {
        InitialRecoverySensations(
            muscular: muscular.map { min(5, max(1, $0)) },
            energy: energy.map { min(5, max(1, $0)) },
            structure: structure.map { min(5, max(1, $0)) }
        )
    }
}

public struct InitialRecoveryEvidence: Codable {
    public let capturedAtMs: Int64
    public let coveredFromMs: Int64
    public let coveredToMs: Int64
    public let expiresAtMs: Int64
    public let sessions: Int
    public let activityType: InitialRecoveryActivityType
    public let intensity: InitialRecoveryIntensity
    public let zones: [String]
    public let sensations: InitialRecoverySensations
    public let muscularScore: Int
    public let systemScore: Int
    public let structureScore: Int
    public let confidence: Int
    public let estimatorVersion: Int
    public let sourceId: String

    public init(
        capturedAtMs: Int64,
        coveredFromMs: Int64,
        coveredToMs: Int64,
        expiresAtMs: Int64,
        sessions: Int,
        activityType: InitialRecoveryActivityType,
        intensity: InitialRecoveryIntensity,
        zones: [String] = [],
        sensations: InitialRecoverySensations = InitialRecoverySensations(),
        muscularScore: Int,
        systemScore: Int,
        structureScore: Int,
        confidence: Int,
        estimatorVersion: Int = 1,
        sourceId: String = "initial-recovery"
    ) {
        self.capturedAtMs = capturedAtMs
        self.coveredFromMs = min(coveredFromMs, coveredToMs)
        self.coveredToMs = max(coveredFromMs, coveredToMs)
        self.expiresAtMs = max(expiresAtMs, capturedAtMs)
        self.sessions = min(14, max(0, sessions))
        self.activityType = activityType
        self.intensity = intensity
        self.zones = Array(Set(zones.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })).sorted()
        self.sensations = sensations.normalized()
        self.muscularScore = min(100, max(0, muscularScore))
        self.systemScore = min(100, max(0, systemScore))
        self.structureScore = min(100, max(0, structureScore))
        self.confidence = min(100, max(0, confidence))
        self.estimatorVersion = estimatorVersion
        self.sourceId = sourceId
    }

    public func normalized() -> InitialRecoveryEvidence {
        InitialRecoveryEvidence(
            capturedAtMs: capturedAtMs,
            coveredFromMs: coveredFromMs,
            coveredToMs: coveredToMs,
            expiresAtMs: expiresAtMs,
            sessions: sessions,
            activityType: activityType,
            intensity: intensity,
            zones: zones,
            sensations: sensations,
            muscularScore: muscularScore,
            systemScore: systemScore,
            structureScore: structureScore,
            confidence: confidence,
            estimatorVersion: estimatorVersion,
            sourceId: sourceId
        )
    }
}
