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

public enum KeyDateType: String, Codable {
    case COMPETITION
    case EXAMS
    case VACATION
    case TRAVEL
    case CUSTOM
}

public struct ProgramKeyDate: Codable {
    public let id: String
    public let title: String
    public let type: KeyDateType
    public let startDate: String
    public let endDate: String?
    public let eventDate: String?
    public let notes: String?

    public init(
        id: String,
        title: String,
        type: KeyDateType = .CUSTOM,
        startDate: String,
        endDate: String? = nil,
        eventDate: String? = nil,
        notes: String? = nil
    ) {
        self.id = id
        self.title = title
        self.type = type
        self.startDate = startDate
        self.endDate = endDate
        self.eventDate = eventDate
        self.notes = notes
    }
}

public struct VolumeRecommendation: Codable {
    public let muscleGroup: String
    public let minEffectiveVolume: Int
    public let maxAdaptiveVolume: Int
    public let maxRecoverableVolume: Int
    public let frequencyCap: Int

    public init(
        muscleGroup: String,
        minEffectiveVolume: Int,
        maxAdaptiveVolume: Int,
        maxRecoverableVolume: Int,
        frequencyCap: Int = 4
    ) {
        self.muscleGroup = muscleGroup
        self.minEffectiveVolume = minEffectiveVolume
        self.maxAdaptiveVolume = maxAdaptiveVolume
        self.maxRecoverableVolume = maxRecoverableVolume
        self.frequencyCap = frequencyCap
    }

    public func copy(
        minEffectiveVolume: Int? = nil,
        maxAdaptiveVolume: Int? = nil,
        maxRecoverableVolume: Int? = nil,
        frequencyCap: Int? = nil
    ) -> VolumeRecommendation {
        VolumeRecommendation(
            muscleGroup: muscleGroup,
            minEffectiveVolume: minEffectiveVolume ?? self.minEffectiveVolume,
            maxAdaptiveVolume: maxAdaptiveVolume ?? self.maxAdaptiveVolume,
            maxRecoverableVolume: maxRecoverableVolume ?? self.maxRecoverableVolume,
            frequencyCap: frequencyCap ?? self.frequencyCap
        )
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
    public let volumeRecommendations: [VolumeRecommendation]
    public let keyDates: [ProgramKeyDate]

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
        tags: [String] = [],
        volumeRecommendations: [VolumeRecommendation] = [],
        keyDates: [ProgramKeyDate] = []
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
        self.volumeRecommendations = volumeRecommendations
        self.keyDates = keyDates
    }

    public func copy(
        volumeRecommendations: [VolumeRecommendation]? = nil
    ) -> Program {
        Program(
            id: id,
            name: name,
            description: description,
            coverImage: coverImage,
            mode: mode,
            structure: structure,
            totalProgramWeeks: totalProgramWeeks,
            macrocycles: macrocycles,
            author: author,
            isPublic: isPublic,
            tags: tags,
            volumeRecommendations: volumeRecommendations ?? self.volumeRecommendations,
            keyDates: keyDates
        )
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

// ─── Program Extensions ───────────────────────────────────────────────────────

public extension Program {
    var totalBlockCount: Int {
        macrocycles.reduce(0) { $0 + $1.blocks.count }
    }

    var totalMesocycleCount: Int {
        macrocycles.reduce(0) { acc, macro in
            acc + macro.blocks.reduce(0) { $0 + $1.mesocycles.count }
        }
    }

    var isSimpleTemporalProgram: Bool {
        macrocycles.count == 1 && totalBlockCount == 1
    }
}

// ─── Program Status & Active State ───────────────────────────────────────────

public enum ProgramStatus: String, Codable {
    case ACTIVE, PAUSED, COMPLETED
}

public struct ActiveProgramState: Codable {
    public let programId: String
    public let status: ProgramStatus
    public let currentMacrocycleIndex: Int
    public let currentBlockIndex: Int
    public let currentMesocycleIndex: Int
    public let currentWeekId: String

    public init(
        programId: String,
        status: ProgramStatus = .ACTIVE,
        currentMacrocycleIndex: Int = 0,
        currentBlockIndex: Int = 0,
        currentMesocycleIndex: Int = 0,
        currentWeekId: String = ""
    ) {
        self.programId = programId
        self.status = status
        self.currentMacrocycleIndex = currentMacrocycleIndex
        self.currentBlockIndex = currentBlockIndex
        self.currentMesocycleIndex = currentMesocycleIndex
        self.currentWeekId = currentWeekId
    }

    public func copy(
        status: ProgramStatus? = nil,
        currentMacrocycleIndex: Int? = nil,
        currentBlockIndex: Int? = nil,
        currentMesocycleIndex: Int? = nil,
        currentWeekId: String? = nil
    ) -> ActiveProgramState {
        ActiveProgramState(
            programId: programId,
            status: status ?? self.status,
            currentMacrocycleIndex: currentMacrocycleIndex ?? self.currentMacrocycleIndex,
            currentBlockIndex: currentBlockIndex ?? self.currentBlockIndex,
            currentMesocycleIndex: currentMesocycleIndex ?? self.currentMesocycleIndex,
            currentWeekId: currentWeekId ?? self.currentWeekId
        )
    }
}
