import Foundation

public enum CompetitionRecordMode: String, Codable {
    case TECHNICAL
    case JOURNAL
    case HYBRID
}

public enum CompetitionTemplateType: String, Codable {
    case POWERLIFTING
    case BODYBUILDING
    case WEIGHTLIFTING
    case RUNNING
    case STRONGMAN
    case CROSSFIT
    case MARTIAL_ARTS
    case CUSTOM
}

public enum CompetitionRecordStatus: String, Codable {
    case PLANNED
    case COMPLETED
    case ARCHIVED
}

public enum CompetitionMovementType: String, Codable {
    case SQUAT
    case BENCH
    case DEADLIFT
    case SNATCH
    case CLEAN_AND_JERK
    case PRESS
    case RUN
    case CUSTOM
}

public enum CompetitionAttemptResult: String, Codable {
    case GOOD_LIFT
    case NO_LIFT
    case SKIPPED
    case PENDING
}

public enum CompetitionEquipment: String, Codable {
    case RAW
    case SLEEVES
    case WRAPS
    case EQUIPPED
    case CLASSIC
    case CUSTOM
}

public struct CompetitionRecord: Codable {
    public let id: String
    public let title: String
    public let eventDate: String?
    public let startTime: String?
    public let sportType: CompetitionTemplateType
    public let recordMode: CompetitionRecordMode
    public let status: CompetitionRecordStatus
    public let location: String?
    public let federation: String?
    public let category: String?
    public let bodyweightKg: Double?
    public let resultSummary: String?
    public let placement: String?
    public let medal: String?
    public let notes: String?
    public let plannedProgramId: String?
    public let plannedSessionId: String?
    public let plannedWeekId: String?
    public let keyDateId: String?
    public let reminderOneWeekEnabled: Bool
    public let reminder48hEnabled: Bool
    public let reminderStartEnabled: Bool
    public let technicalBlocks: [CompetitionTechnicalBlock]
    public let journal: CompetitionJournal?
    public let photos: [CompetitionPhoto]
    public let customMetrics: [CustomCompetitionMetric]
    public let powerliftingDetails: PowerliftingCompetitionDetails?
    public let bodybuildingDetails: BodybuildingCompetitionDetails?
    public let createdAtMs: Int64
    public let updatedAtMs: Int64

    public init(
        id: String,
        title: String,
        eventDate: String? = nil,
        startTime: String? = nil,
        sportType: CompetitionTemplateType = .CUSTOM,
        recordMode: CompetitionRecordMode = .HYBRID,
        status: CompetitionRecordStatus = .PLANNED,
        location: String? = nil,
        federation: String? = nil,
        category: String? = nil,
        bodyweightKg: Double? = nil,
        resultSummary: String? = nil,
        placement: String? = nil,
        medal: String? = nil,
        notes: String? = nil,
        plannedProgramId: String? = nil,
        plannedSessionId: String? = nil,
        plannedWeekId: String? = nil,
        keyDateId: String? = nil,
        reminderOneWeekEnabled: Bool = true,
        reminder48hEnabled: Bool = true,
        reminderStartEnabled: Bool = false,
        technicalBlocks: [CompetitionTechnicalBlock] = [],
        journal: CompetitionJournal? = nil,
        photos: [CompetitionPhoto] = [],
        customMetrics: [CustomCompetitionMetric] = [],
        powerliftingDetails: PowerliftingCompetitionDetails? = nil,
        bodybuildingDetails: BodybuildingCompetitionDetails? = nil,
        createdAtMs: Int64 = 0,
        updatedAtMs: Int64 = 0
    ) {
        self.id = id
        self.title = title
        self.eventDate = eventDate
        self.startTime = startTime
        self.sportType = sportType
        self.recordMode = recordMode
        self.status = status
        self.location = location
        self.federation = federation
        self.category = category
        self.bodyweightKg = bodyweightKg
        self.resultSummary = resultSummary
        self.placement = placement
        self.medal = medal
        self.notes = notes
        self.plannedProgramId = plannedProgramId
        self.plannedSessionId = plannedSessionId
        self.plannedWeekId = plannedWeekId
        self.keyDateId = keyDateId
        self.reminderOneWeekEnabled = reminderOneWeekEnabled
        self.reminder48hEnabled = reminder48hEnabled
        self.reminderStartEnabled = reminderStartEnabled
        self.technicalBlocks = technicalBlocks
        self.journal = journal
        self.photos = photos
        self.customMetrics = customMetrics
        self.powerliftingDetails = powerliftingDetails
        self.bodybuildingDetails = bodybuildingDetails
        self.createdAtMs = createdAtMs
        self.updatedAtMs = updatedAtMs
    }
}

public struct CompetitionTechnicalBlock: Codable {
    public let id: String
    public let title: String
    public let movementType: CompetitionMovementType
    public let exerciseDbId: String?
    public let canonicalExerciseId: String?
    public let exerciseName: String?
    public let resultUnit: String?
    public let attempts: [CompetitionAttempt]
    public let bestValidWeightKg: Double?
    public let bestValidMark: String?
    public let notes: String?

    public init(
        id: String,
        title: String,
        movementType: CompetitionMovementType = .CUSTOM,
        exerciseDbId: String? = nil,
        canonicalExerciseId: String? = nil,
        exerciseName: String? = nil,
        resultUnit: String? = nil,
        attempts: [CompetitionAttempt] = [],
        bestValidWeightKg: Double? = nil,
        bestValidMark: String? = nil,
        notes: String? = nil
    ) {
        self.id = id
        self.title = title
        self.movementType = movementType
        self.exerciseDbId = exerciseDbId
        self.canonicalExerciseId = canonicalExerciseId
        self.exerciseName = exerciseName
        self.resultUnit = resultUnit
        self.attempts = attempts
        self.bestValidWeightKg = bestValidWeightKg
        self.bestValidMark = bestValidMark
        self.notes = notes
    }
}

public struct CompetitionAttempt: Codable {
    public let id: String
    public let attemptNumber: Int
    public let weightKg: Double?
    public let reps: Int?
    public let distance: Double?
    public let timeSeconds: Double?
    public let mark: String?
    public let resultType: CompetitionAttemptResult
    public let invalidReason: String?
    public let rpe: Double?
    public let technicalNotes: String?

    public init(
        id: String,
        attemptNumber: Int,
        weightKg: Double? = nil,
        reps: Int? = nil,
        distance: Double? = nil,
        timeSeconds: Double? = nil,
        mark: String? = nil,
        resultType: CompetitionAttemptResult = .PENDING,
        invalidReason: String? = nil,
        rpe: Double? = nil,
        technicalNotes: String? = nil
    ) {
        self.id = id
        self.attemptNumber = attemptNumber
        self.weightKg = weightKg
        self.reps = reps
        self.distance = distance
        self.timeSeconds = timeSeconds
        self.mark = mark
        self.resultType = resultType
        self.invalidReason = invalidReason
        self.rpe = rpe
        self.technicalNotes = technicalNotes
    }
}

public struct CompetitionJournal: Codable {
    public let overallFeeling: String?
    public let physicalState: String?
    public let mentalState: String?
    public let whatWentWell: String?
    public let whatWentWrong: String?
    public let learnings: String?
    public let preparationNotes: String?
    public let judgesFeedback: String?
    public let personalReflection: String?

    public init(
        overallFeeling: String? = nil,
        physicalState: String? = nil,
        mentalState: String? = nil,
        whatWentWell: String? = nil,
        whatWentWrong: String? = nil,
        learnings: String? = nil,
        preparationNotes: String? = nil,
        judgesFeedback: String? = nil,
        personalReflection: String? = nil
    ) {
        self.overallFeeling = overallFeeling
        self.physicalState = physicalState
        self.mentalState = mentalState
        self.whatWentWell = whatWentWell
        self.whatWentWrong = whatWentWrong
        self.learnings = learnings
        self.preparationNotes = preparationNotes
        self.judgesFeedback = judgesFeedback
        self.personalReflection = personalReflection
    }
}

public struct CompetitionPhoto: Codable {
    public let id: String
    public let uri: String
    public let caption: String?

    public init(id: String, uri: String, caption: String? = nil) {
        self.id = id
        self.uri = uri
        self.caption = caption
    }
}

public struct CustomCompetitionMetric: Codable {
    public let id: String
    public let label: String
    public let value: String
    public let unit: String?

    public init(id: String, label: String, value: String, unit: String? = nil) {
        self.id = id
        self.label = label
        self.value = value
        self.unit = unit
    }
}

public struct PowerliftingCompetitionDetails: Codable {
    public let weightClass: String?
    public let division: String?
    public let equipment: CompetitionEquipment
    public let sexCategory: String?
    public let totalKg: Double?
    public let ipfGlPoints: Double?
    public let dotsPoints: Double?
    public let wilksPoints: Double?

    public init(
        weightClass: String? = nil,
        division: String? = nil,
        equipment: CompetitionEquipment = .RAW,
        sexCategory: String? = nil,
        totalKg: Double? = nil,
        ipfGlPoints: Double? = nil,
        dotsPoints: Double? = nil,
        wilksPoints: Double? = nil
    ) {
        self.weightClass = weightClass
        self.division = division
        self.equipment = equipment
        self.sexCategory = sexCategory
        self.totalKg = totalKg
        self.ipfGlPoints = ipfGlPoints
        self.dotsPoints = dotsPoints
        self.wilksPoints = wilksPoints
    }
}

public struct BodybuildingCompetitionDetails: Codable {
    public let division: String?
    public let stageWeightKg: Double?
    public let conditionNotes: String?
    public let posingNotes: String?
    public let judgesFeedback: String?
    public let personalReflection: String?

    public init(
        division: String? = nil,
        stageWeightKg: Double? = nil,
        conditionNotes: String? = nil,
        posingNotes: String? = nil,
        judgesFeedback: String? = nil,
        personalReflection: String? = nil
    ) {
        self.division = division
        self.stageWeightKg = stageWeightKg
        self.conditionNotes = conditionNotes
        self.posingNotes = posingNotes
        self.judgesFeedback = judgesFeedback
        self.personalReflection = personalReflection
    }
}
