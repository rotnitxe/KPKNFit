import Foundation

public enum RecoveryStatus: String, Codable {
    case FRESH
    case OPTIMAL
    case RECOVERING
    case EXHAUSTED
}

public struct MuscleRecoveryStatus: Codable {
    public let muscleName: String
    public let recoveryScore: Int
    public let hoursToRecovery: Int
    public let hoursSinceLastSession: Int
    public let effectiveSets: Int
    public let status: RecoveryStatus
    
    public init(
        muscleName: String,
        recoveryScore: Int,
        hoursToRecovery: Int,
        hoursSinceLastSession: Int,
        effectiveSets: Int,
        status: RecoveryStatus
    ) {
        self.muscleName = muscleName
        self.recoveryScore = recoveryScore
        self.hoursToRecovery = hoursToRecovery
        self.hoursSinceLastSession = hoursSinceLastSession
        self.effectiveSets = effectiveSets
        self.status = status
    }
}

public struct ExerciseSet: Identifiable, Codable {
    public let id: String
    public let targetReps: Int?
    public let targetDuration: Int?
    public let targetRPE: Double?
    public let targetRIR: Int?
    public let weight: Double?
    public let completedReps: Int?
    public let completedDuration: Int?
    
    public init(
        id: String,
        targetReps: Int? = nil,
        targetDuration: Int? = nil,
        targetRPE: Double? = nil,
        targetRIR: Int? = nil,
        weight: Double? = nil,
        completedReps: Int? = nil,
        completedDuration: Int? = nil
    ) {
        self.id = id
        self.targetReps = targetReps
        self.targetDuration = targetDuration
        self.targetRPE = targetRPE
        self.targetRIR = targetRIR
        self.weight = weight
        self.completedReps = completedReps
        self.completedDuration = completedDuration
    }
}

public struct Exercise: Identifiable, Codable {
    public let id: String
    public let name: String
    public let exerciseDbId: String?
    public let exerciseId: String?
    public let canonicalExerciseId: String?
    public let sets: [ExerciseSet]
    
    public init(
        id: String,
        name: String,
        exerciseDbId: String? = nil,
        exerciseId: String? = nil,
        canonicalExerciseId: String? = nil,
        sets: [ExerciseSet] = []
    ) {
        self.id = id
        self.name = name
        self.exerciseDbId = exerciseDbId
        self.exerciseId = exerciseId
        self.canonicalExerciseId = canonicalExerciseId
        self.sets = sets
    }
}

public struct SessionPart: Identifiable, Codable {
    public let id: String
    public let exercises: [Exercise]
    
    public init(id: String, exercises: [Exercise] = []) {
        self.id = id
        self.exercises = exercises
    }
}

public struct Session: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let exercises: [Exercise]
    public let parts: [SessionPart]
    public let targetDurationMinutes: Int?
    
    public init(
        id: String,
        name: String,
        description: String? = nil,
        exercises: [Exercise] = [],
        parts: [SessionPart] = [],
        targetDurationMinutes: Int? = nil
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.exercises = exercises
        self.parts = parts
        self.targetDurationMinutes = targetDurationMinutes
    }
}

public struct SessionLocation: Codable {
    public let macroIndex: Int
    public let mesoIndex: Int
    public let weekId: String
    
    public init(macroIndex: Int, mesoIndex: Int, weekId: String) {
        self.macroIndex = macroIndex
        self.mesoIndex = mesoIndex
        self.weekId = weekId
    }
}

public struct WorkoutLog: Codable {
    public let durationMinutes: Int
    
    public init(durationMinutes: Int) {
        self.durationMinutes = durationMinutes
    }
}

public struct TodaySessionItem: Identifiable, Codable {
    public var id: String { session.id }
    public let session: Session
    public let program: Program
    public let location: SessionLocation
    public let isCompleted: Bool
    public let dayOfWeek: Int
    public let log: WorkoutLog?
    public let isOngoing: Bool
    
    public init(
        session: Session,
        program: Program,
        location: SessionLocation,
        isCompleted: Bool,
        dayOfWeek: Int,
        log: WorkoutLog? = nil,
        isOngoing: Bool = false
    ) {
        self.session = session
        self.program = program
        self.location = location
        self.isCompleted = isCompleted
        self.dayOfWeek = dayOfWeek
        self.log = log
        self.isOngoing = isOngoing
    }
}
