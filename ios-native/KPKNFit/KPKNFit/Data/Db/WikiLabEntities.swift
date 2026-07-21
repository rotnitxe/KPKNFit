import Foundation

public struct MuscleGroupEntity: Codable {
    public let id: String
    public let name: String
    public let description: String
    public let bodyPart: String?
    public let coverImage: String?
    public let origin: String?
    public let insertion: String?
    public let mechanicalFunctions: String?
    public let mev: String?
    public let mav: String?
    public let mrv: String?
    public let recommendedExercises: String?
    public let relatedJoints: String?
    public let relatedTendons: String?
    public let importanceMovement: String?
    public let importanceHealth: String?
    public let aestheticImportance: String?
}

public struct JointEntity: Codable {
    public let id: String
    public let name: String
    public let type: String
    public let description: String
    public let bodyPart: String?
    public let musclesCrossing: String?
    public let tendonsRelated: String?
    public let movementPatterns: String?
    public let commonInjuries: String?
    public let protectiveExercises: String?
}

public struct TendonEntity: Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let muscleId: String?
    public let jointId: String?
    public let commonInjuries: String?
    public let protectiveExercises: String?
}

public struct MovementPatternEntity: Codable {
    public let id: String
    public let name: String
    public let description: String
    public let forceTypes: String?
    public let chainTypes: String?
    public let primaryMuscles: String?
    public let primaryJoints: String?
    public let exampleExercises: String?
}

public struct KineticChainEntity: Codable {
    public let id: String
    public let name: String
    public let description: String
    public let importance: String
    public let muscles: String?
}
