import Foundation

public enum ProgramMode: String, Codable {
    case POWERLIFTING
    case HYPERTROPHY
    case POWERBUILDING
}

public enum ProgramStructure: String, Codable {
    case SIMPLE, COMPLEX
}

public enum MesocycleGoal: String, Codable {
    case ACCUMULATION
    case INTENSIFICATION
    case REALIZATION
    case DELOAD
    case CUSTOM

    public var label: String {
        switch self {
        case .ACCUMULATION: return "Acumulación"
        case .INTENSIFICATION: return "Intensificación"
        case .REALIZATION: return "Realización"
        case .DELOAD: return "Descarga"
        case .CUSTOM: return "Custom"
        }
    }
}

public enum WeekVariant: String, Codable {
    case A, B, C, D
}

public struct Macrocycle: Identifiable, Codable {
    public let id: String
    public let name: String
    public let blocks: [Block]

    public init(id: String, name: String, blocks: [Block] = []) {
        self.id = id
        self.name = name
        self.blocks = blocks
    }
}

public struct Block: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let mesocycles: [Mesocycle]

    public init(id: String, name: String, description: String? = nil, mesocycles: [Mesocycle] = []) {
        self.id = id
        self.name = name
        self.description = description
        self.mesocycles = mesocycles
    }
}

public struct Mesocycle: Identifiable, Codable {
    public let id: String
    public let name: String
    public let goal: MesocycleGoal
    public let customGoal: String?
    public let weeks: [ProgramWeek]

    public init(
        id: String,
        name: String,
        goal: MesocycleGoal = .ACCUMULATION,
        customGoal: String? = nil,
        weeks: [ProgramWeek] = []
    ) {
        self.id = id
        self.name = name
        self.goal = goal
        self.customGoal = customGoal
        self.weeks = weeks
    }
}

public struct ProgramWeek: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let sessions: [Session]
    public let variant: WeekVariant?

    public init(
        id: String,
        name: String,
        description: String? = nil,
        sessions: [Session] = [],
        variant: WeekVariant? = nil
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.sessions = sessions
        self.variant = variant
    }
}

public struct Program: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let coverImage: String?
    public let mode: ProgramMode
    public let structure: ProgramStructure
    public let totalProgramWeeks: Int
    public let macrocycles: [Macrocycle]
    public let author: String?
    public let isPublic: Bool
    public let tags: [String]

    public init(
        id: String,
        name: String,
        description: String? = nil,
        coverImage: String? = nil,
        mode: ProgramMode = .HYPERTROPHY,
        structure: ProgramStructure = .SIMPLE,
        totalProgramWeeks: Int = 1,
        macrocycles: [Macrocycle] = [],
        author: String? = nil,
        isPublic: Bool = false,
        tags: [String] = []
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.coverImage = coverImage
        self.mode = mode
        self.structure = structure
        self.totalProgramWeeks = totalProgramWeeks
        self.macrocycles = macrocycles
        self.author = author
        self.isPublic = isPublic
        self.tags = tags
    }
}

// ─── Helper: find session exercises ───────────────────────────────────────────

public func findSessionExercises(program: Program, sessionId: String) -> [Exercise] {
    for macro in program.macrocycles {
        for block in macro.blocks {
            for meso in block.mesocycles {
                for week in meso.weeks {
                    if let session = week.sessions.first(where: { $0.id == sessionId }) {
                        if !session.parts.isEmpty {
                            return session.parts.flatMap { $0.exercises }
                        } else {
                            return session.exercises
                        }
                    }
                }
            }
        }
    }
    return []
}
