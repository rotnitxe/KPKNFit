import Foundation

/// JSON-compatible value used only for bounded cross-platform transport fields.
public indirect enum JSONValue: Codable, Equatable {
    case object([String: JSONValue])
    case array([JSONValue])
    case string(String)
    case integer(Int64)
    case number(Double)
    case bool(Bool)
    case null

    public init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer()
        if value.decodeNil() { self = .null; return }
        if let bool = try? value.decode(Bool.self) { self = .bool(bool); return }
        if let integer = try? value.decode(Int64.self) { self = .integer(integer); return }
        if let number = try? value.decode(Double.self) { self = .number(number); return }
        if let string = try? value.decode(String.self) { self = .string(string); return }
        if let array = try? value.decode([JSONValue].self) { self = .array(array); return }
        if let object = try? value.decode([String: JSONValue].self) { self = .object(object); return }
        throw DecodingError.dataCorruptedError(in: value, debugDescription: "Unsupported JSON value")
    }

    public func encode(to encoder: Encoder) throws {
        var value = encoder.singleValueContainer()
        switch self {
        case .object(let object): try value.encode(object)
        case .array(let array): try value.encode(array)
        case .string(let string): try value.encode(string)
        case .integer(let integer): try value.encode(integer)
        case .number(let number): try value.encode(number)
        case .bool(let bool): try value.encode(bool)
        case .null: try value.encodeNil()
        }
    }
}

struct DynamicCodingKey: CodingKey, Hashable {
    let stringValue: String
    let intValue: Int?

    init(_ value: String) { stringValue = value; intValue = nil }
    init?(stringValue: String) { self.init(stringValue) }
    init?(intValue: Int) { stringValue = String(intValue); self.intValue = intValue }
}

func decodeOpaqueFields(
    from container: KeyedDecodingContainer<DynamicCodingKey>,
    excluding knownKeys: Set<String>
) throws -> [String: JSONValue] {
    var fields: [String: JSONValue] = [:]
    for key in container.allKeys where !knownKeys.contains(key.stringValue) {
        fields[key.stringValue] = try container.decode(JSONValue.self, forKey: key)
    }
    return fields
}

func encodeOpaqueFields(
    _ fields: [String: JSONValue],
    to container: inout KeyedEncodingContainer<DynamicCodingKey>,
    excluding knownKeys: Set<String>
) throws {
    for (name, value) in fields where !knownKeys.contains(name) {
        try container.encode(value, forKey: DynamicCodingKey(name))
    }
}

extension KeyedDecodingContainer where Key == DynamicCodingKey {
    func decode<T: Decodable>(_ type: T.Type, _ name: String, default defaultValue: T) throws -> T {
        try decodeIfPresent(type, forKey: DynamicCodingKey(name)) ?? defaultValue
    }

    func decodeOptional<T: Decodable>(_ type: T.Type, _ name: String) throws -> T? {
        try decodeIfPresent(type, forKey: DynamicCodingKey(name))
    }
}

// ─── Enums ────────────────────────────────────────────────────────────────────

public enum ProgramMode: String, Codable {
    case POWERLIFTING, HYPERTROPHY, POWERBUILDING
}

public enum ProgramStructure: String, Codable {
    case SIMPLE, COMPLEX
}

public enum TrainingPhase: String, Codable {
    case ACCUMULATION, TRANSFORMATION, REALIZATION
}

public enum VolumeSystem: String, Codable {
    case ISRAETEL, KPNK, MANUAL
}

public enum ProgramCalendarizationMode: String, Codable {
    case advancedCompetition = "ADVANCED_COMPETITION", simpleDated = "SIMPLE_DATED"
}

public enum SimpleProgramKind: String, Codable {
    case cyclic = "CYCLIC", calendarized = "CALENDARIZED"
    case linear = "LINEAR"
}

public enum MesocycleGoal: String, Codable {
    case ACCUMULATION, INTENSIFICATION, REALIZATION, DELOAD, CUSTOM

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

/// Objetivo a nivel de bloque (enum NUEVO; no muta MesocycleGoal legacy).
public enum BlockGoal: String, Codable {
    case ACCUMULATION, INTENSIFICATION, SPECIFICITY, REALIZATION, DELOAD, DENSITY, PEAK, TAPER, CUSTOM

    public var label: String {
        switch self {
        case .ACCUMULATION: return "Acumulación"
        case .INTENSIFICATION: return "Intensificación"
        case .SPECIFICITY: return "Especificidad"
        case .REALIZATION: return "Realización"
        case .DELOAD: return "Descarga"
        case .DENSITY: return "Densidad / Metabolitos"
        case .PEAK: return "Pico"
        case .TAPER: return "Taper"
        case .CUSTOM: return "Custom"
        }
    }
}

public enum BlockProgressionScheme: String, Codable {
    case NONE, LINEAR_LOAD, UNDULATING, PERCENT_RM, RPE_CAP
}

public enum WeekVariant: String, Codable {
    case A, B, C, D
}

public enum LoopType: String, Codable {
    case ONE_RM_TEST, DELOAD, COMPETITION, CUSTOM
}

public enum DurationType: String, Codable {
    case DAY, WEEK
}

public enum LoopStatus: String, Codable {
    case SCHEDULED, ACTIVE, COMPLETED, POSTPONED, CANCELLED
}

public enum KeyDateType: String, Codable {
    case COMPETITION, EXAMS, VACATION, TRAVEL, CUSTOM
}

public enum TrainingStyle: String, Codable {
    case BODYBUILDER, POWERBUILDER, POWERLIFTER
}

public enum AthleteProfileLevel: String, Codable {
    case BEGINNER, ADVANCED
}

// ─── Small Supporting Structs ─────────────────────────────────────────────────

public struct PostponedLoop: Codable, Identifiable {
    public let id: String
    public let fromCycle: Int
    public let toCycle: Int

    public init(id: String, fromCycle: Int, toCycle: Int) {
        self.id = id
        self.fromCycle = fromCycle
        self.toCycle = toCycle
    }
}

public struct LoopState: Codable {
    public let currentCycle: Int
    public let postponed: [PostponedLoop]
    public let cancelled: [String]
    public let opaqueFields: [String: JSONValue]

    public init(currentCycle: Int = 0, postponed: [PostponedLoop] = [], cancelled: [String] = [], opaqueFields: [String: JSONValue] = [:]) {
        self.currentCycle = currentCycle
        self.postponed = postponed
        self.cancelled = cancelled
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = ["currentCycle", "postponed", "cancelled"]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        currentCycle = try c.decode(Int.self, "currentCycle", default: 0)
        postponed = try c.decode([PostponedLoop].self, "postponed", default: [])
        cancelled = try c.decode([String].self, "cancelled", default: [])
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(currentCycle, forKey: DynamicCodingKey("currentCycle"))
        try c.encode(postponed, forKey: DynamicCodingKey("postponed"))
        try c.encode(cancelled, forKey: DynamicCodingKey("cancelled"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }
}

// ─── Hierarchy Structs ────────────────────────────────────────────────────────

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
    public let goal: BlockGoal?
    public let progressionScheme: BlockProgressionScheme?
    public let opaqueFields: [String: JSONValue]

    public init(
        id: String,
        name: String,
        description: String? = nil,
        mesocycles: [Mesocycle] = [],
        goal: BlockGoal? = nil,
        progressionScheme: BlockProgressionScheme? = nil,
        opaqueFields: [String: JSONValue] = [:]
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.mesocycles = mesocycles
        self.goal = goal
        self.progressionScheme = progressionScheme
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = ["id", "name", "description", "mesocycles", "goal", "progressionScheme"]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        id = try c.decode(String.self, "id")
        name = try c.decode(String.self, "name")
        description = try c.decodeOptional(String.self, "description")
        mesocycles = try c.decode([Mesocycle].self, "mesocycles", default: [])
        goal = try c.decodeOptional(BlockGoal.self, "goal")
        progressionScheme = try c.decodeOptional(BlockProgressionScheme.self, "progressionScheme")
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(id, forKey: DynamicCodingKey("id"))
        try c.encode(name, forKey: DynamicCodingKey("name"))
        try c.encodeIfPresent(description, forKey: DynamicCodingKey("description"))
        try c.encode(mesocycles, forKey: DynamicCodingKey("mesocycles"))
        try c.encodeIfPresent(goal, forKey: DynamicCodingKey("goal"))
        try c.encodeIfPresent(progressionScheme, forKey: DynamicCodingKey("progressionScheme"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
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
    public let isLoopWeek: Bool
    public let loopId: String?
    public let startDate: String?
    public let endDate: String?
    public let trainingDayDates: [Int: String]
    public let progressionIndex: Int?
    public let opaqueFields: [String: JSONValue]

    public init(
        id: String,
        name: String,
        description: String? = nil,
        sessions: [Session] = [],
        variant: WeekVariant? = nil,
        isLoopWeek: Bool = false,
        loopId: String? = nil,
        startDate: String? = nil,
        endDate: String? = nil,
        trainingDayDates: [Int: String] = [:],
        progressionIndex: Int? = nil,
        opaqueFields: [String: JSONValue] = [:]
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.sessions = sessions
        self.variant = variant
        self.isLoopWeek = isLoopWeek
        self.loopId = loopId
        self.startDate = startDate
        self.endDate = endDate
        self.trainingDayDates = trainingDayDates
        self.progressionIndex = progressionIndex
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = ["id", "name", "description", "sessions", "variant", "isLoopWeek", "loopId", "startDate", "endDate", "trainingDayDates", "progressionIndex"]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        id = try c.decode(String.self, "id")
        name = try c.decode(String.self, "name")
        description = try c.decodeOptional(String.self, "description")
        sessions = try c.decode([Session].self, "sessions", default: [])
        variant = try c.decodeOptional(WeekVariant.self, "variant")
        isLoopWeek = try c.decode(Bool.self, "isLoopWeek", default: false)
        loopId = try c.decodeOptional(String.self, "loopId")
        startDate = try c.decodeOptional(String.self, "startDate")
        endDate = try c.decodeOptional(String.self, "endDate")
        trainingDayDates = try c.decode([Int: String].self, "trainingDayDates", default: [:])
        progressionIndex = try c.decodeOptional(Int.self, "progressionIndex")
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(id, forKey: DynamicCodingKey("id"))
        try c.encode(name, forKey: DynamicCodingKey("name"))
        try c.encodeIfPresent(description, forKey: DynamicCodingKey("description"))
        try c.encode(sessions, forKey: DynamicCodingKey("sessions"))
        try c.encodeIfPresent(variant, forKey: DynamicCodingKey("variant"))
        try c.encode(isLoopWeek, forKey: DynamicCodingKey("isLoopWeek"))
        try c.encodeIfPresent(loopId, forKey: DynamicCodingKey("loopId"))
        try c.encodeIfPresent(startDate, forKey: DynamicCodingKey("startDate"))
        try c.encodeIfPresent(endDate, forKey: DynamicCodingKey("endDate"))
        try c.encode(trainingDayDates, forKey: DynamicCodingKey("trainingDayDates"))
        try c.encodeIfPresent(progressionIndex, forKey: DynamicCodingKey("progressionIndex"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }
}

public struct Loop: Identifiable, Codable {
    public let id: String
    public let title: String
    public let type: LoopType
    public let repeatEveryXLoops: Int
    public let durationType: DurationType
    public let dayOfWeek: Int?
    public let durationWeeks: Int?
    public let priority: Int?
    public let sessions: [Session]
    public let color: String?

    public init(
        id: String,
        title: String,
        type: LoopType = .CUSTOM,
        repeatEveryXLoops: Int = 1,
        durationType: DurationType = .WEEK,
        dayOfWeek: Int? = nil,
        durationWeeks: Int? = nil,
        priority: Int? = nil,
        sessions: [Session] = [],
        color: String? = nil
    ) {
        self.id = id
        self.title = title
        self.type = type
        self.repeatEveryXLoops = repeatEveryXLoops
        self.durationType = durationType
        self.dayOfWeek = dayOfWeek
        self.durationWeeks = durationWeeks
        self.priority = priority
        self.sessions = sessions
        self.color = color
    }
}

public struct LoopActivation: Codable, Identifiable {
    public let id: String
    public let loopId: String
    public let cycle: Int
    public let status: LoopStatus
    public let postponedTo: Int?

    public init(loopId: String, cycle: Int, status: LoopStatus = .SCHEDULED, postponedTo: Int? = nil) {
        self.id = loopId
        self.loopId = loopId
        self.cycle = cycle
        self.status = status
        self.postponedTo = postponedTo
    }
}

// ─── Metadata Structs ─────────────────────────────────────────────────────────

public struct ProgramCalendarization: Codable {
    public let mode: ProgramCalendarizationMode
    public let manualEndDate: String?
    public let strictStart: Bool
    public let activatedByCompetition: Bool

    public init(
        mode: ProgramCalendarizationMode,
        manualEndDate: String? = nil,
        strictStart: Bool = false,
        activatedByCompetition: Bool = false
    ) {
        self.mode = mode
        self.manualEndDate = manualEndDate
        self.strictStart = strictStart
        self.activatedByCompetition = activatedByCompetition
    }
}

public struct SimpleProgramSnapshot: Codable {
    public let macrocycles: [Macrocycle]
    public let loops: [Loop]
    public let loopState: LoopState?
    public let events: [ProgramEvent]
    public let selectedSplitId: String?
    public let customSplitPattern: [String]
    public let customSplitName: String?
    public let customSplitDescription: String?
    public let blockSplitSelections: [String: String]
    public let savedAtMs: Int64
    public let opaqueFields: [String: JSONValue]

    public init(
        macrocycles: [Macrocycle] = [],
        loops: [Loop] = [],
        loopState: LoopState? = nil,
        events: [ProgramEvent] = [],
        selectedSplitId: String? = nil,
        customSplitPattern: [String] = [],
        customSplitName: String? = nil,
        customSplitDescription: String? = nil,
        blockSplitSelections: [String: String] = [:],
        savedAtMs: Int64 = 0,
        opaqueFields: [String: JSONValue] = [:]
    ) {
        self.macrocycles = macrocycles
        self.loops = loops
        self.loopState = loopState
        self.events = events
        self.selectedSplitId = selectedSplitId
        self.customSplitPattern = customSplitPattern
        self.customSplitName = customSplitName
        self.customSplitDescription = customSplitDescription
        self.blockSplitSelections = blockSplitSelections
        self.savedAtMs = savedAtMs
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = ["macrocycles", "loops", "loopState", "events", "selectedSplitId", "customSplitPattern", "customSplitName", "customSplitDescription", "blockSplitSelections", "savedAtMs"]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        macrocycles = try c.decode([Macrocycle].self, "macrocycles", default: [])
        loops = try c.decode([Loop].self, "loops", default: [])
        loopState = try c.decodeOptional(LoopState.self, "loopState")
        events = try c.decode([ProgramEvent].self, "events", default: [])
        selectedSplitId = try c.decodeOptional(String.self, "selectedSplitId")
        customSplitPattern = try c.decode([String].self, "customSplitPattern", default: [])
        customSplitName = try c.decodeOptional(String.self, "customSplitName")
        customSplitDescription = try c.decodeOptional(String.self, "customSplitDescription")
        blockSplitSelections = try c.decode([String: String].self, "blockSplitSelections", default: [:])
        savedAtMs = try c.decode(Int64.self, "savedAtMs", default: 0)
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(macrocycles, forKey: DynamicCodingKey("macrocycles"))
        try c.encode(loops, forKey: DynamicCodingKey("loops"))
        try c.encodeIfPresent(loopState, forKey: DynamicCodingKey("loopState"))
        try c.encode(events, forKey: DynamicCodingKey("events"))
        try c.encodeIfPresent(selectedSplitId, forKey: DynamicCodingKey("selectedSplitId"))
        try c.encode(customSplitPattern, forKey: DynamicCodingKey("customSplitPattern"))
        try c.encodeIfPresent(customSplitName, forKey: DynamicCodingKey("customSplitName"))
        try c.encodeIfPresent(customSplitDescription, forKey: DynamicCodingKey("customSplitDescription"))
        try c.encode(blockSplitSelections, forKey: DynamicCodingKey("blockSplitSelections"))
        try c.encode(savedAtMs, forKey: DynamicCodingKey("savedAtMs"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }
}

public struct VolumeRecommendation: Codable, Identifiable {
    public let id: String
    public let muscleGroup: String
    public let minEffectiveVolume: Int
    public let maxAdaptiveVolume: Int
    public let maxRecoverableVolume: Int
    public let frequencyCap: Int

    public init(
        id: String = UUID().uuidString,
        muscleGroup: String,
        minEffectiveVolume: Int,
        maxAdaptiveVolume: Int,
        maxRecoverableVolume: Int,
        frequencyCap: Int = 4
    ) {
        self.id = id
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
            id: id,
            muscleGroup: muscleGroup,
            minEffectiveVolume: minEffectiveVolume ?? self.minEffectiveVolume,
            maxAdaptiveVolume: maxAdaptiveVolume ?? self.maxAdaptiveVolume,
            maxRecoverableVolume: maxRecoverableVolume ?? self.maxRecoverableVolume,
            frequencyCap: frequencyCap ?? self.frequencyCap
        )
    }
}

public struct AthleteProfileScore: Codable {
    public let technicalScore: Int
    public let consistencyScore: Int
    public let strengthScore: Int
    public let mobilityScore: Int
    public let trainingStyle: TrainingStyle
    public let totalScore: Int
    public let profileLevel: AthleteProfileLevel

    public init(
        technicalScore: Int,
        consistencyScore: Int,
        strengthScore: Int,
        mobilityScore: Int,
        trainingStyle: TrainingStyle,
        totalScore: Int,
        profileLevel: AthleteProfileLevel
    ) {
        self.technicalScore = technicalScore
        self.consistencyScore = consistencyScore
        self.strengthScore = strengthScore
        self.mobilityScore = mobilityScore
        self.trainingStyle = trainingStyle
        self.totalScore = totalScore
        self.profileLevel = profileLevel
    }
}

public struct ProgramGoals: Codable {
    public let squat1RM: Double?
    public let bench1RM: Double?
    public let deadlift1RM: Double?

    public init(squat1RM: Double? = nil, bench1RM: Double? = nil, deadlift1RM: Double? = nil) {
        self.squat1RM = squat1RM
        self.bench1RM = bench1RM
        self.deadlift1RM = deadlift1RM
    }
}

public struct ProgramEvent: Identifiable, Codable {
    public let id: String?
    public let title: String
    public let type: String
    public let date: String
    public let endDate: String?
    public let calculatedWeek: Int
    public let createMacrocycle: Bool
    public let repeatEveryXCycles: Int?
    public let sessions: [Session]

    public init(
        id: String? = nil,
        title: String,
        type: String,
        date: String,
        endDate: String? = nil,
        calculatedWeek: Int,
        createMacrocycle: Bool = false,
        repeatEveryXCycles: Int? = nil,
        sessions: [Session] = []
    ) {
        self.id = id
        self.title = title
        self.type = type
        self.date = date
        self.endDate = endDate
        self.calculatedWeek = calculatedWeek
        self.createMacrocycle = createMacrocycle
        self.repeatEveryXCycles = repeatEveryXCycles
        self.sessions = sessions
    }
}

public struct ProgramKeyDate: Identifiable, Codable {
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

// ─── Main Program Struct ──────────────────────────────────────────────────────

public struct Program: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let coverImage: String?
    public let mode: ProgramMode
    public let structure: ProgramStructure
    public let blockLabel: String?
    public let macrocycles: [Macrocycle]
    public let author: String?
    public let isPublic: Bool
    public let tags: [String]
    public let events: [ProgramEvent]
    public let loops: [Loop]
    public let loopState: LoopState?
    public let exerciseGoals: [String: Double]
    public let goals: ProgramGoals?
    public let trainingPhase: TrainingPhase?
    public let volumeSystem: VolumeSystem?
    public let autoVolumeEnabled: Bool
    public let startDay: Int?
    public let weekDays: Int?
    public let selectedSplitId: String?
    public let customSplitPattern: [String]
    public let customSplitName: String?
    public let customSplitDescription: String?
    public let blockSplitSelections: [String: String]
    public let structureTemplateId: String?
    public let timelineStartDate: String?
    public let calendarization: ProgramCalendarization?
    public let simpleProgramKind: SimpleProgramKind
    public let pausedCyclicSnapshot: SimpleProgramSnapshot?
    public let keyDates: [ProgramKeyDate]
    public let volumeRecommendations: [VolumeRecommendation]
    public let athleteProfileScore: AthleteProfileScore?
    public let volumeAlertsEnabled: Bool
    public let volumeSetupPromptSeen: Bool
    public let splitTrialSeen: Bool
    public let isDraft: Bool
    public let opaqueFields: [String: JSONValue]

    public init(
        id: String,
        name: String,
        description: String? = nil,
        coverImage: String? = nil,
        mode: ProgramMode = .HYPERTROPHY,
        structure: ProgramStructure = .SIMPLE,
        blockLabel: String? = nil,
        macrocycles: [Macrocycle] = [],
        author: String? = nil,
        isPublic: Bool = false,
        tags: [String] = [],
        events: [ProgramEvent] = [],
        loops: [Loop] = [],
        loopState: LoopState? = nil,
        exerciseGoals: [String: Double] = [:],
        goals: ProgramGoals? = nil,
        trainingPhase: TrainingPhase? = nil,
        volumeSystem: VolumeSystem? = nil,
        autoVolumeEnabled: Bool = false,
        startDay: Int? = nil,
        weekDays: Int? = nil,
        selectedSplitId: String? = nil,
        customSplitPattern: [String] = [],
        customSplitName: String? = nil,
        customSplitDescription: String? = nil,
        blockSplitSelections: [String: String] = [:],
        structureTemplateId: String? = nil,
        timelineStartDate: String? = nil,
        calendarization: ProgramCalendarization? = nil,
        simpleProgramKind: SimpleProgramKind = .cyclic,
        pausedCyclicSnapshot: SimpleProgramSnapshot? = nil,
        keyDates: [ProgramKeyDate] = [],
        volumeRecommendations: [VolumeRecommendation] = [],
        athleteProfileScore: AthleteProfileScore? = nil,
        volumeAlertsEnabled: Bool = true,
        volumeSetupPromptSeen: Bool = false,
        splitTrialSeen: Bool = false,
        isDraft: Bool = false,
        opaqueFields: [String: JSONValue] = [:]
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.coverImage = coverImage
        self.mode = mode
        self.structure = structure
        self.blockLabel = blockLabel
        self.macrocycles = macrocycles
        self.author = author
        self.isPublic = isPublic
        self.tags = tags
        self.events = events
        self.loops = loops
        self.loopState = loopState
        self.exerciseGoals = exerciseGoals
        self.goals = goals
        self.trainingPhase = trainingPhase
        self.volumeSystem = volumeSystem
        self.autoVolumeEnabled = autoVolumeEnabled
        self.startDay = startDay
        self.weekDays = weekDays
        self.selectedSplitId = selectedSplitId
        self.customSplitPattern = customSplitPattern
        self.customSplitName = customSplitName
        self.customSplitDescription = customSplitDescription
        self.blockSplitSelections = blockSplitSelections
        self.structureTemplateId = structureTemplateId
        self.timelineStartDate = timelineStartDate
        self.calendarization = calendarization
        self.simpleProgramKind = simpleProgramKind
        self.pausedCyclicSnapshot = pausedCyclicSnapshot
        self.keyDates = keyDates
        self.volumeRecommendations = volumeRecommendations
        self.athleteProfileScore = athleteProfileScore
        self.volumeAlertsEnabled = volumeAlertsEnabled
        self.volumeSetupPromptSeen = volumeSetupPromptSeen
        self.splitTrialSeen = splitTrialSeen
        self.isDraft = isDraft
        self.opaqueFields = opaqueFields
    }

    private static let knownCodingKeys: Set<String> = [
        "id", "name", "description", "coverImage", "mode", "structure", "blockLabel", "macrocycles", "author",
        "isPublic", "tags", "events", "loops", "loopState", "exerciseGoals", "goals", "trainingPhase", "volumeSystem",
        "autoVolumeEnabled", "startDay", "weekDays", "selectedSplitId", "customSplitPattern", "customSplitName",
        "customSplitDescription", "blockSplitSelections", "structureTemplateId", "timelineStartDate", "calendarization",
        "simpleProgramKind", "pausedCyclicSnapshot", "keyDates", "volumeRecommendations", "athleteProfileScore",
        "volumeAlertsEnabled", "volumeSetupPromptSeen", "splitTrialSeen", "isDraft",
    ]

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DynamicCodingKey.self)
        id = try c.decode(String.self, "id")
        name = try c.decode(String.self, "name")
        description = try c.decodeOptional(String.self, "description")
        coverImage = try c.decodeOptional(String.self, "coverImage")
        mode = try c.decode(ProgramMode.self, "mode", default: .HYPERTROPHY)
        structure = try c.decode(ProgramStructure.self, "structure", default: .SIMPLE)
        blockLabel = try c.decodeOptional(String.self, "blockLabel")
        macrocycles = try c.decode([Macrocycle].self, "macrocycles", default: [])
        author = try c.decodeOptional(String.self, "author")
        isPublic = try c.decode(Bool.self, "isPublic", default: false)
        tags = try c.decode([String].self, "tags", default: [])
        events = try c.decode([ProgramEvent].self, "events", default: [])
        loops = try c.decode([Loop].self, "loops", default: [])
        loopState = try c.decodeOptional(LoopState.self, "loopState")
        exerciseGoals = try c.decode([String: Double].self, "exerciseGoals", default: [:])
        goals = try c.decodeOptional(ProgramGoals.self, "goals")
        trainingPhase = try c.decodeOptional(TrainingPhase.self, "trainingPhase")
        volumeSystem = try c.decodeOptional(VolumeSystem.self, "volumeSystem")
        autoVolumeEnabled = try c.decode(Bool.self, "autoVolumeEnabled", default: false)
        startDay = try c.decodeOptional(Int.self, "startDay")
        weekDays = try c.decodeOptional(Int.self, "weekDays")
        selectedSplitId = try c.decodeOptional(String.self, "selectedSplitId")
        customSplitPattern = try c.decode([String].self, "customSplitPattern", default: [])
        customSplitName = try c.decodeOptional(String.self, "customSplitName")
        customSplitDescription = try c.decodeOptional(String.self, "customSplitDescription")
        blockSplitSelections = try c.decode([String: String].self, "blockSplitSelections", default: [:])
        structureTemplateId = try c.decodeOptional(String.self, "structureTemplateId")
        timelineStartDate = try c.decodeOptional(String.self, "timelineStartDate")
        calendarization = try c.decodeOptional(ProgramCalendarization.self, "calendarization")
        simpleProgramKind = try c.decode(SimpleProgramKind.self, "simpleProgramKind", default: .cyclic)
        pausedCyclicSnapshot = try c.decodeOptional(SimpleProgramSnapshot.self, "pausedCyclicSnapshot")
        keyDates = try c.decode([ProgramKeyDate].self, "keyDates", default: [])
        volumeRecommendations = try c.decode([VolumeRecommendation].self, "volumeRecommendations", default: [])
        athleteProfileScore = try c.decodeOptional(AthleteProfileScore.self, "athleteProfileScore")
        volumeAlertsEnabled = try c.decode(Bool.self, "volumeAlertsEnabled", default: true)
        volumeSetupPromptSeen = try c.decode(Bool.self, "volumeSetupPromptSeen", default: false)
        splitTrialSeen = try c.decode(Bool.self, "splitTrialSeen", default: false)
        isDraft = try c.decode(Bool.self, "isDraft", default: false)
        opaqueFields = try decodeOpaqueFields(from: c, excluding: Self.knownCodingKeys)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: DynamicCodingKey.self)
        try c.encode(id, forKey: DynamicCodingKey("id"))
        try c.encode(name, forKey: DynamicCodingKey("name"))
        try c.encodeIfPresent(description, forKey: DynamicCodingKey("description"))
        try c.encodeIfPresent(coverImage, forKey: DynamicCodingKey("coverImage"))
        try c.encode(mode, forKey: DynamicCodingKey("mode"))
        try c.encode(structure, forKey: DynamicCodingKey("structure"))
        try c.encodeIfPresent(blockLabel, forKey: DynamicCodingKey("blockLabel"))
        try c.encode(macrocycles, forKey: DynamicCodingKey("macrocycles"))
        try c.encodeIfPresent(author, forKey: DynamicCodingKey("author"))
        try c.encode(isPublic, forKey: DynamicCodingKey("isPublic"))
        try c.encode(tags, forKey: DynamicCodingKey("tags"))
        try c.encode(events, forKey: DynamicCodingKey("events"))
        try c.encode(loops, forKey: DynamicCodingKey("loops"))
        try c.encodeIfPresent(loopState, forKey: DynamicCodingKey("loopState"))
        try c.encode(exerciseGoals, forKey: DynamicCodingKey("exerciseGoals"))
        try c.encodeIfPresent(goals, forKey: DynamicCodingKey("goals"))
        try c.encodeIfPresent(trainingPhase, forKey: DynamicCodingKey("trainingPhase"))
        try c.encodeIfPresent(volumeSystem, forKey: DynamicCodingKey("volumeSystem"))
        try c.encode(autoVolumeEnabled, forKey: DynamicCodingKey("autoVolumeEnabled"))
        try c.encodeIfPresent(startDay, forKey: DynamicCodingKey("startDay"))
        try c.encodeIfPresent(weekDays, forKey: DynamicCodingKey("weekDays"))
        try c.encodeIfPresent(selectedSplitId, forKey: DynamicCodingKey("selectedSplitId"))
        try c.encode(customSplitPattern, forKey: DynamicCodingKey("customSplitPattern"))
        try c.encodeIfPresent(customSplitName, forKey: DynamicCodingKey("customSplitName"))
        try c.encodeIfPresent(customSplitDescription, forKey: DynamicCodingKey("customSplitDescription"))
        try c.encode(blockSplitSelections, forKey: DynamicCodingKey("blockSplitSelections"))
        try c.encodeIfPresent(structureTemplateId, forKey: DynamicCodingKey("structureTemplateId"))
        try c.encodeIfPresent(timelineStartDate, forKey: DynamicCodingKey("timelineStartDate"))
        try c.encodeIfPresent(calendarization, forKey: DynamicCodingKey("calendarization"))
        try c.encode(simpleProgramKind, forKey: DynamicCodingKey("simpleProgramKind"))
        try c.encodeIfPresent(pausedCyclicSnapshot, forKey: DynamicCodingKey("pausedCyclicSnapshot"))
        try c.encode(keyDates, forKey: DynamicCodingKey("keyDates"))
        try c.encode(volumeRecommendations, forKey: DynamicCodingKey("volumeRecommendations"))
        try c.encodeIfPresent(athleteProfileScore, forKey: DynamicCodingKey("athleteProfileScore"))
        try c.encode(volumeAlertsEnabled, forKey: DynamicCodingKey("volumeAlertsEnabled"))
        try c.encode(volumeSetupPromptSeen, forKey: DynamicCodingKey("volumeSetupPromptSeen"))
        try c.encode(splitTrialSeen, forKey: DynamicCodingKey("splitTrialSeen"))
        try c.encode(isDraft, forKey: DynamicCodingKey("isDraft"))
        try encodeOpaqueFields(opaqueFields, to: &c, excluding: Self.knownCodingKeys)
    }

    public func copy(
        id: String? = nil,
        name: String? = nil,
        description: String?? = nil,
        coverImage: String?? = nil,
        mode: ProgramMode? = nil,
        structure: ProgramStructure? = nil,
        blockLabel: String?? = nil,
        macrocycles: [Macrocycle]? = nil,
        author: String?? = nil,
        isPublic: Bool? = nil,
        tags: [String]? = nil,
        events: [ProgramEvent]? = nil,
        loops: [Loop]? = nil,
        loopState: LoopState? = nil,
        exerciseGoals: [String: Double]? = nil,
        goals: ProgramGoals? = nil,
        trainingPhase: TrainingPhase? = nil,
        volumeSystem: VolumeSystem? = nil,
        autoVolumeEnabled: Bool? = nil,
        startDay: Int? = nil,
        weekDays: Int? = nil,
        selectedSplitId: String?? = nil,
        customSplitPattern: [String]? = nil,
        customSplitName: String?? = nil,
        customSplitDescription: String?? = nil,
        blockSplitSelections: [String: String]? = nil,
        structureTemplateId: String?? = nil,
        timelineStartDate: String?? = nil,
        calendarization: ProgramCalendarization? = nil,
        simpleProgramKind: SimpleProgramKind? = nil,
        pausedCyclicSnapshot: SimpleProgramSnapshot? = nil,
        keyDates: [ProgramKeyDate]? = nil,
        volumeRecommendations: [VolumeRecommendation]? = nil,
        athleteProfileScore: AthleteProfileScore? = nil,
        volumeAlertsEnabled: Bool? = nil,
        volumeSetupPromptSeen: Bool? = nil,
        splitTrialSeen: Bool? = nil,
        isDraft: Bool? = nil,
        opaqueFields: [String: JSONValue]? = nil
    ) -> Program {
        Program(
            id: id ?? self.id,
            name: name ?? self.name,
            description: description ?? self.description,
            coverImage: coverImage ?? self.coverImage,
            mode: mode ?? self.mode,
            structure: structure ?? self.structure,
            blockLabel: blockLabel ?? self.blockLabel,
            macrocycles: macrocycles ?? self.macrocycles,
            author: author ?? self.author,
            isPublic: isPublic ?? self.isPublic,
            tags: tags ?? self.tags,
            events: events ?? self.events,
            loops: loops ?? self.loops,
            loopState: loopState ?? self.loopState,
            exerciseGoals: exerciseGoals ?? self.exerciseGoals,
            goals: goals ?? self.goals,
            trainingPhase: trainingPhase ?? self.trainingPhase,
            volumeSystem: volumeSystem ?? self.volumeSystem,
            autoVolumeEnabled: autoVolumeEnabled ?? self.autoVolumeEnabled,
            startDay: startDay ?? self.startDay,
            weekDays: weekDays ?? self.weekDays,
            selectedSplitId: selectedSplitId ?? self.selectedSplitId,
            customSplitPattern: customSplitPattern ?? self.customSplitPattern,
            customSplitName: customSplitName ?? self.customSplitName,
            customSplitDescription: customSplitDescription ?? self.customSplitDescription,
            blockSplitSelections: blockSplitSelections ?? self.blockSplitSelections,
            structureTemplateId: structureTemplateId ?? self.structureTemplateId,
            timelineStartDate: timelineStartDate ?? self.timelineStartDate,
            calendarization: calendarization ?? self.calendarization,
            simpleProgramKind: simpleProgramKind ?? self.simpleProgramKind,
            pausedCyclicSnapshot: pausedCyclicSnapshot ?? self.pausedCyclicSnapshot,
            keyDates: keyDates ?? self.keyDates,
            volumeRecommendations: volumeRecommendations ?? self.volumeRecommendations,
            athleteProfileScore: athleteProfileScore ?? self.athleteProfileScore,
            volumeAlertsEnabled: volumeAlertsEnabled ?? self.volumeAlertsEnabled,
            volumeSetupPromptSeen: volumeSetupPromptSeen ?? self.volumeSetupPromptSeen,
            splitTrialSeen: splitTrialSeen ?? self.splitTrialSeen,
            isDraft: isDraft ?? self.isDraft,
            opaqueFields: opaqueFields ?? self.opaqueFields
        )
    }

    public func forEachWeek(_ block: (_ blockId: String, _ week: ProgramWeek) -> Void) {
        for macro in macrocycles {
            for programBlock in macro.blocks {
                for meso in programBlock.mesocycles {
                    for week in meso.weeks {
                        block(programBlock.id, week)
                    }
                }
            }
        }
    }
}

// ─── Program Computed Properties ──────────────────────────────────────────────

public extension Program {
    /// Keeps the recipe snapshot authoritative while marking a locally edited
    /// recipe-backed session as an occurrence-scoped override for Android.
    func recordingManualSessionOverride(
        session: Session,
        weekId: String,
        weekOccurrence: Int? = nil,
        createdAtMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> Program {
        guard let sourceRecipe = opaqueFields["sourceRecipe"], sourceRecipe != .null else { return self }

        var fields = opaqueFields
        var overrides: [JSONValue]
        switch fields["manualSessionOverrides"] {
        case .some(.array(let existing)):
            overrides = existing
        case nil, .some(.null):
            overrides = []
        default:
            // Do not overwrite an unexpected future representation.
            return self
        }

        let recipeDayId: JSONValue? = {
            guard let value = session.opaqueFields["recipeDayId"], case .string = value else { return nil }
            return value
        }()
        let cycleNumber: Int64? = {
            guard case .some(.object(let runState)) = fields["runState"],
                  case .some(.integer(let value)) = runState["cycleNumber"] else { return nil }
            return value
        }()
        if let index = overrides.firstIndex(where: { value in
            guard case .object(let object) = value,
                  object["sessionId"] == .string(session.id) else { return false }
            return object["scope"] == nil || object["scope"] == .string("SESSION")
        }), case .object(var existing) = overrides[index] {
            if existing["weekId"] == nil || existing["weekId"] == .null {
                existing["weekId"] = .string(weekId)
            }
            if (existing["weekOccurrence"] == nil || existing["weekOccurrence"] == .null), let weekOccurrence {
                existing["weekOccurrence"] = .integer(Int64(weekOccurrence))
            }
            if (existing["cycleNumber"] == nil || existing["cycleNumber"] == .null), let cycleNumber {
                existing["cycleNumber"] = .integer(cycleNumber)
            }
            if (existing["recipeDayId"] == nil || existing["recipeDayId"] == .null),
               let recipeDayId {
                existing["recipeDayId"] = recipeDayId
            }
            overrides[index] = .object(existing)
        } else {
            var marker: [String: JSONValue] = [
                "sessionId": .string(session.id),
                "weekId": .string(weekId),
                "scope": .string("SESSION"),
                "reason": .string("Edición local; receta fuente conservada"),
                "createdAtMs": .integer(createdAtMs),
            ]
            if let weekOccurrence {
                marker["weekOccurrence"] = .integer(Int64(weekOccurrence))
            }
            if let cycleNumber {
                marker["cycleNumber"] = .integer(cycleNumber)
            }
            if let recipeDayId {
                marker["recipeDayId"] = recipeDayId
            }
            overrides.append(.object(marker))
        }

        fields["manualSessionOverrides"] = .array(overrides)
        return copy(opaqueFields: fields)
    }

    var totalBlockCount: Int {
        macrocycles.reduce(0) { $0 + $1.blocks.count }
    }

    var totalMesocycleCount: Int {
        macrocycles.reduce(0) { acc, macro in
            acc + macro.blocks.reduce(0) { $0 + $1.mesocycles.count }
        }
    }

    var totalProgramWeeks: Int {
        macrocycles.reduce(0) { acc, macro in
            acc + macro.blocks.reduce(0) { blockAcc, block in
                blockAcc + block.mesocycles.reduce(0) { $0 + $1.weeks.count }
            }
        }
    }

    var isSimpleTemporalProgram: Bool {
        macrocycles.count == 1 && totalBlockCount == 1
    }

    var isSimpleCalendarizedProgram: Bool {
        isSimpleTemporalProgram && simpleProgramKind == .calendarized
    }

    var simpleCycleWeeks: Int? {
        isSimpleTemporalProgram ? totalProgramWeeks : nil
    }

    var primaryLoopCadenceCycles: Int? {
        if !loops.isEmpty {
            return loops.map { max($0.repeatEveryXLoops, 1) }.min()
        }
        if !events.isEmpty {
            return events.compactMap { $0.repeatEveryXCycles }.map { max($0, 1) }.min()
        }
        return nil
    }

    var primaryLoopLengthWeeks: Int? {
        guard let cycleWeeks = simpleCycleWeeks, let cadence = primaryLoopCadenceCycles else { return nil }
        return cycleWeeks * cadence
    }
}

// ─── Program Mutating Functions ───────────────────────────────────────────────

public extension Program {
    func normalizedTemporalStructure() -> Program {
        let shouldBeSimple = isSimpleTemporalProgram
        let normalizedSimpleKind: SimpleProgramKind = {
            if !shouldBeSimple { return .cyclic }
            if calendarization?.mode == .simpleDated, let start = timelineStartDate, !start.isEmpty {
                return .calendarized
            }
            return simpleProgramKind
        }()

        let cleanMacrocycles = macrocycles.map { macro in
            macro.copy(
                blocks: macro.blocks.map { block in
                    block.copy(
                        mesocycles: block.mesocycles.map { meso in
                            meso.copy(
                                weeks: meso.weeks.map { week in
                                    if week.isLoopWeek && !shouldBeSimple {
                                        return ProgramWeek(
                                            id: week.id,
                                            name: week.name,
                                            description: week.description,
                                            sessions: week.sessions,
                                            variant: week.variant,
                                            isLoopWeek: false,
                                            loopId: nil,
                                            startDate: week.startDate,
                                            endDate: week.endDate,
                                            trainingDayDates: week.trainingDayDates,
                                            progressionIndex: week.progressionIndex,
                                            opaqueFields: week.opaqueFields
                                        )
                                    }
                                    return week
                                }
                            )
                        }
                    )
                }
            )
        }

        return copy(
            structure: shouldBeSimple ? ProgramStructure.SIMPLE : ProgramStructure.COMPLEX,
            macrocycles: cleanMacrocycles,
            events: (shouldBeSimple && normalizedSimpleKind == .cyclic) ? events : [],
            loops: (shouldBeSimple && normalizedSimpleKind == .cyclic) ? loops : [],
            loopState: (shouldBeSimple && normalizedSimpleKind == .cyclic) ? loopState : nil,
            simpleProgramKind: normalizedSimpleKind,
            pausedCyclicSnapshot: shouldBeSimple ? pausedCyclicSnapshot : nil
        )
    }

    func toSimpleProgramSnapshot() -> SimpleProgramSnapshot {
        SimpleProgramSnapshot(
            macrocycles: macrocycles,
            loops: loops,
            loopState: loopState,
            events: events,
            selectedSplitId: selectedSplitId,
            customSplitPattern: customSplitPattern,
            customSplitName: customSplitName,
            customSplitDescription: customSplitDescription,
            blockSplitSelections: blockSplitSelections,
            savedAtMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }

    func startSimpleCalendarizedBreak(
        startDate: Date,
        endDate: Date?,
        startDayOfWeek: Int,
        trainingDays: Set<Int>
    ) -> Program {
        let safeDays = Set(trainingDays.filter { $0 >= 1 && $0 <= 7 })
        let resolvedDays = safeDays.isEmpty ? suggestCalendarTrainingDays() : safeDays

        let calculatedEndDate: Date = endDate ?? Calendar.current.date(byAdding: .weekOfYear, value: 3, to: startDate)!.addingTimeInterval(6 * 24 * 60 * 60)
        let weekCount = inclusiveCalendarWeekCount(startDate: startDate, endDate: calculatedEndDate)

        let weeks = buildSimpleCalendarWeeks(
            startDate: startDate,
            weekCount: weekCount,
            startDayOfWeek: startDayOfWeek,
            trainingDays: resolvedDays
        )

        let snapshot = pausedCyclicSnapshot ?? toSimpleProgramSnapshot()

        return copy(
            structure: .SIMPLE,
            macrocycles: [
                Macrocycle(
                    id: "macro_calendarized_\(UUID().uuidString)",
                    name: "Break calendarizado",
                    blocks: [
                        Block(
                            id: "block_calendarized_\(UUID().uuidString)",
                            name: "Semanas calendarizadas",
                            mesocycles: [
                                Mesocycle(
                                    id: "meso_calendarized_\(UUID().uuidString)",
                                    name: "Calendarizado",
                                    goal: .ACCUMULATION,
                                    weeks: weeks
                                )
                            ]
                        )
                    ]
                )
            ],
            events: [],
            loops: [],
            loopState: nil,
            startDay: startDayOfWeek,
            timelineStartDate: isoString(from: startDate),
            calendarization: ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
            simpleProgramKind: .calendarized,
            pausedCyclicSnapshot: snapshot
        )
    }

    func restorePausedCyclicProgram() -> Program {
        guard let snapshot = pausedCyclicSnapshot else {
            return copy(
                calendarization: nil,
                simpleProgramKind: .cyclic,
                pausedCyclicSnapshot: nil
            )
        }
        return copy(
            structure: .SIMPLE,
            macrocycles: snapshot.macrocycles,
            events: snapshot.events,
            loops: snapshot.loops,
            loopState: snapshot.loopState,
            selectedSplitId: snapshot.selectedSplitId,
            customSplitPattern: snapshot.customSplitPattern,
            customSplitName: snapshot.customSplitName,
            customSplitDescription: snapshot.customSplitDescription,
            blockSplitSelections: snapshot.blockSplitSelections,
            calendarization: nil,
            simpleProgramKind: .cyclic,
            pausedCyclicSnapshot: nil
        )
    }

    func startFreshSimpleCycle() -> Program {
        return copy(
            structure: .SIMPLE,
            macrocycles: [
                Macrocycle(
                    id: "macro_simple_\(UUID().uuidString)",
                    name: "Macrociclo base",
                    blocks: [
                        Block(
                            id: "block_simple_\(UUID().uuidString)",
                            name: "Ciclo base",
                            mesocycles: [
                                Mesocycle(
                                    id: "meso_simple_\(UUID().uuidString)",
                                    name: "Mesociclo 1",
                                    goal: .ACCUMULATION,
                                    weeks: [
                                        ProgramWeek(
                                            id: "week_simple_\(UUID().uuidString)",
                                            name: "Semana 1"
                                        )
                                    ]
                                )
                            ]
                        )
                    ]
                )
            ],
            events: [],
            loops: [],
            loopState: nil,
            calendarization: nil,
            simpleProgramKind: .cyclic,
            pausedCyclicSnapshot: nil
        )
    }

    func nextSimpleCalendarStart() -> Date {
        let lastEnd: Date? = macrocycles
            .flatMap { $0.blocks }
            .flatMap { $0.mesocycles }
            .flatMap { $0.weeks }
            .compactMap { parseIsoDate($0.endDate) }
            .max()

        if let lastEndDate = lastEnd {
            return Calendar.current.date(byAdding: .day, value: 1, to: lastEndDate)!
        }
        if let timelineStart = timelineStartDate, !timelineStart.isEmpty, let parsed = parseIsoDate(timelineStart) {
            return parsed
        }
        return Date()
    }

    func suggestCalendarTrainingDays() -> Set<Int> {
        let daysFromDates: Set<Int> = Set(
            macrocycles
                .flatMap { $0.blocks }
                .flatMap { $0.mesocycles }
                .flatMap { $0.weeks }
                .flatMap { $0.trainingDayDates.keys }
                .filter { $0 >= 1 && $0 <= 7 }
        )
        if !daysFromDates.isEmpty { return daysFromDates }

        let daysFromSessions: Set<Int> = Set(
            macrocycles
                .flatMap { $0.blocks }
                .flatMap { $0.mesocycles }
                .flatMap { $0.weeks }
                .flatMap { $0.sessions }
                .compactMap { session in
                    guard let day = session.dayOfWeek, day >= 1 && day <= 7 else { return nil }
                    return day
                }
        )
        if !daysFromSessions.isEmpty { return daysFromSessions }

        return [1, 3, 5]
    }
}

// ─── Private Helpers ──────────────────────────────────────────────────────────

private func parseIsoDate(_ raw: String?) -> Date? {
    guard let raw = raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withFullDate]
    return formatter.date(from: raw)
}

private func isoString(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withFullDate]
    return formatter.string(from: date)
}

private func buildSimpleCalendarWeeks(
    startDate: Date,
    weekCount: Int,
    startDayOfWeek: Int,
    trainingDays: Set<Int>
) -> [ProgramWeek] {
    let startDayIso = max(1, min(7, startDayOfWeek))
    let calendar = Calendar.current

    return (0..<weekCount).map { index in
        guard let weekStart = calendar.date(byAdding: .weekOfYear, value: index, to: startDate) else {
            return ProgramWeek(id: UUID().uuidString, name: "Semana \(index + 1)")
        }
        guard let weekEnd = calendar.date(byAdding: .day, value: 6, to: weekStart) else {
            return ProgramWeek(id: UUID().uuidString, name: "Semana \(index + 1)")
        }

        var trainingDayDates: [Int: String] = [:]
        for dayOfWeek in trainingDays {
            let targetDayIso = dayOfWeek
            let offset = ((targetDayIso - startDayIso + 7) % 7)
            if let actualDate = calendar.date(byAdding: .day, value: offset, to: weekStart) {
                trainingDayDates[dayOfWeek] = isoString(from: actualDate)
            }
        }

        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "en_US")
        dateFormatter.dateFormat = "MM/dd"
        let weekName = "Semana: \(dateFormatter.string(from: weekStart))"

        return ProgramWeek(
            id: UUID().uuidString,
            name: weekName,
            startDate: isoString(from: weekStart),
            endDate: isoString(from: weekEnd),
            trainingDayDates: trainingDayDates
        )
    }
}

private func inclusiveCalendarWeekCount(startDate: Date, endDate: Date) -> Int {
    let calendar = Calendar.current
    guard let daysBetween = calendar.dateComponents([.day], from: startDate, to: endDate).day else { return 1 }
    let inclusiveDays = max(0, daysBetween) + 1
    let rawWeeks = Double(inclusiveDays + 6) / 7.0
    return max(1, min(52, Int(rawWeeks.rounded(.up))))
}

// ─── Extensions for copy method on Program Structs ────────────────────────────

extension Macrocycle {
    public func copy(id: String? = nil, name: String? = nil, blocks: [Block]? = nil) -> Macrocycle {
        Macrocycle(id: id ?? self.id, name: name ?? self.name, blocks: blocks ?? self.blocks)
    }
}

extension Block {
    public func copy(id: String? = nil, name: String? = nil, mesocycles: [Mesocycle]? = nil) -> Block {
        Block(
            id: id ?? self.id,
            name: name ?? self.name,
            description: description,
            mesocycles: mesocycles ?? self.mesocycles,
            goal: goal,
            progressionScheme: progressionScheme,
            opaqueFields: opaqueFields
        )
    }
}

extension Mesocycle {
    public func copy(id: String? = nil, name: String? = nil, goal: MesocycleGoal? = nil, weeks: [ProgramWeek]? = nil) -> Mesocycle {
        Mesocycle(id: id ?? self.id, name: name ?? self.name, goal: goal ?? self.goal, customGoal: customGoal, weeks: weeks ?? self.weeks)
    }
}

