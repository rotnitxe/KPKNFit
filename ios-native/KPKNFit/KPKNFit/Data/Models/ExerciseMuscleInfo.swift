import Foundation

// ─── Exercise Muscle Info ────────────────────────────────────────────────────

public struct ExerciseMuscleInfo: Codable {
    public let id: String
    public let name: String
    public let alias: String?
    public let description: String?
    public let involvedMuscles: [InvolvedMuscle]
    public let equipment: String?
    public let category: String?
    public let type: String?
    public let force: String?
    public let chain: String?
    public let bodyPart: String?
    public let tier: String?
    public let isCustom: Bool
    public let efc: Double?
    public let cnc: Double?
    public let ssc: Double?
    public let ttc: Double?
    public let axialLoadFactor: Double?
    public let technicalDifficulty: Double?
    public let coreInvolvement: String?
    public let bracingRecommended: Bool?
    public let strapsRecommended: Bool?
    public let resistanceProfile: ResistanceProfile?
    public let anatomicalConsiderations: [AnatomicalConsideration]?
    public let commonMistakes: [CommonMistake]?
    public let setupCues: [String]?
    public let executionCues: [String]?
    public let progressions: [Progression]?
    public let regressions: [Progression]?
    public let recommendedMobility: [String]?
    public let periodizationNotes: [PeriodizationNote]?
    public let functionalTransfer: String?
    public let sportsRelevance: [String]?
    public let injuryRisk: InjuryRisk?
    public let sfr: ScoreJustification?
    public let primeStars: ScoreJustification?
    public let bodybuildingScore: Double?
    public let communityOpinion: [String]?
    public let aiCoachAnalysis: AiCoachAnalysis?
    public let images: [String]?
    public let videos: [String]?
    public let setupDetails: SetupDetails?
    public let setupTime: Int?
    public let averageRestSeconds: Int?
    public let executionOptions: [String]?
    public let movementPattern: String?

    public init(
        id: String,
        name: String,
        alias: String? = nil,
        description: String? = nil,
        involvedMuscles: [InvolvedMuscle] = [],
        equipment: String? = nil,
        category: String? = nil,
        type: String? = nil,
        force: String? = nil,
        chain: String? = nil,
        bodyPart: String? = nil,
        tier: String? = nil,
        isCustom: Bool = false,
        efc: Double? = nil,
        cnc: Double? = nil,
        ssc: Double? = nil,
        ttc: Double? = nil,
        axialLoadFactor: Double? = nil,
        technicalDifficulty: Double? = nil,
        coreInvolvement: String? = nil,
        bracingRecommended: Bool? = nil,
        strapsRecommended: Bool? = nil,
        resistanceProfile: ResistanceProfile? = nil,
        anatomicalConsiderations: [AnatomicalConsideration]? = nil,
        commonMistakes: [CommonMistake]? = nil,
        setupCues: [String]? = nil,
        executionCues: [String]? = nil,
        progressions: [Progression]? = nil,
        regressions: [Progression]? = nil,
        recommendedMobility: [String]? = nil,
        periodizationNotes: [PeriodizationNote]? = nil,
        functionalTransfer: String? = nil,
        sportsRelevance: [String]? = nil,
        injuryRisk: InjuryRisk? = nil,
        sfr: ScoreJustification? = nil,
        primeStars: ScoreJustification? = nil,
        bodybuildingScore: Double? = nil,
        communityOpinion: [String]? = nil,
        aiCoachAnalysis: AiCoachAnalysis? = nil,
        images: [String]? = nil,
        videos: [String]? = nil,
        setupDetails: SetupDetails? = nil,
        setupTime: Int? = nil,
        averageRestSeconds: Int? = nil,
        executionOptions: [String]? = nil,
        movementPattern: String? = nil
    ) {
        self.id = id
        self.name = name
        self.alias = alias
        self.description = description
        self.involvedMuscles = involvedMuscles
        self.equipment = equipment
        self.category = category
        self.type = type
        self.force = force
        self.chain = chain
        self.bodyPart = bodyPart
        self.tier = tier
        self.isCustom = isCustom
        self.efc = efc
        self.cnc = cnc
        self.ssc = ssc
        self.ttc = ttc
        self.axialLoadFactor = axialLoadFactor
        self.technicalDifficulty = technicalDifficulty
        self.coreInvolvement = coreInvolvement
        self.bracingRecommended = bracingRecommended
        self.strapsRecommended = strapsRecommended
        self.resistanceProfile = resistanceProfile
        self.anatomicalConsiderations = anatomicalConsiderations
        self.commonMistakes = commonMistakes
        self.setupCues = setupCues
        self.executionCues = executionCues
        self.progressions = progressions
        self.regressions = regressions
        self.recommendedMobility = recommendedMobility
        self.periodizationNotes = periodizationNotes
        self.functionalTransfer = functionalTransfer
        self.sportsRelevance = sportsRelevance
        self.injuryRisk = injuryRisk
        self.sfr = sfr
        self.primeStars = primeStars
        self.bodybuildingScore = bodybuildingScore
        self.communityOpinion = communityOpinion
        self.aiCoachAnalysis = aiCoachAnalysis
        self.images = images
        self.videos = videos
        self.setupDetails = setupDetails
        self.setupTime = setupTime
        self.averageRestSeconds = averageRestSeconds
        self.executionOptions = executionOptions
        self.movementPattern = movementPattern
    }
}

// ─── Involved Muscle ─────────────────────────────────────────────────────────

public struct InvolvedMuscle: Codable {
    public let muscle: String
    public let role: MuscleRole
    public let volumeContribution: Double?
    public let emphasis: String?

    private enum CodingKeys: String, CodingKey {
        case muscle, role
        case volumeContribution = "activation"
        case emphasis
    }

    public init(
        muscle: String,
        role: MuscleRole = .PRIMARY,
        volumeContribution: Double? = nil,
        emphasis: String? = nil
    ) {
        self.muscle = muscle
        self.role = role
        self.volumeContribution = volumeContribution
        self.emphasis = emphasis
    }
}

// ─── Muscle Role ─────────────────────────────────────────────────────────────

public enum MuscleRole: String, Codable {
    case PRIMARY = "primary"
    case SECONDARY = "secondary"
    case STABILIZER = "stabilizer"
    case NEUTRALIZER = "neutralizer"
}

// ─── Resistance Profile ──────────────────────────────────────────────────────

public struct ResistanceProfile: Codable {
    public let curve: String?
    public let peakTensionPoint: String?
    public let description: String?

    public init(
        curve: String? = nil,
        peakTensionPoint: String? = nil,
        description: String? = nil
    ) {
        self.curve = curve
        self.peakTensionPoint = peakTensionPoint
        self.description = description
    }
}

// ─── Anatomical Consideration ────────────────────────────────────────────────

public struct AnatomicalConsideration: Codable, Hashable {
    public let trait: String
    public let advice: String

    public init(trait: String, advice: String) {
        self.trait = trait
        self.advice = advice
    }
}

// ─── Common Mistake ──────────────────────────────────────────────────────────

public struct CommonMistake: Codable, Hashable {
    public let mistake: String
    public let correction: String

    public init(mistake: String, correction: String) {
        self.mistake = mistake
        self.correction = correction
    }
}

// ─── Progression ─────────────────────────────────────────────────────────────

public struct Progression: Codable {
    public let name: String
    public let description: String

    public init(name: String, description: String) {
        self.name = name
        self.description = description
    }
}

// ─── Periodization Note ──────────────────────────────────────────────────────

public struct PeriodizationNote: Codable {
    public let phase: String
    public let suitability: Double
    public let notes: String

    public init(phase: String, suitability: Double, notes: String) {
        self.phase = phase
        self.suitability = suitability
        self.notes = notes
    }
}

// ─── Injury Risk ─────────────────────────────────────────────────────────────

public struct InjuryRisk: Codable {
    public let level: Double
    public let details: String

    public init(level: Double, details: String) {
        self.level = level
        self.details = details
    }
}

// ─── Score Justification ─────────────────────────────────────────────────────

public struct ScoreJustification: Codable {
    public let score: Double
    public let justification: String

    public init(score: Double, justification: String) {
        self.score = score
        self.justification = justification
    }
}

// ─── AI Coach Analysis ───────────────────────────────────────────────────────

public struct AiCoachAnalysis: Codable {
    public let summary: String
    public let pros: [String]
    public let cons: [String]

    public init(summary: String, pros: [String], cons: [String]) {
        self.summary = summary
        self.pros = pros
        self.cons = cons
    }
}

// ─── Setup Details ───────────────────────────────────────────────────────────

public struct SetupDetails: Codable {
    public let seatPosition: String?
    public let pinPosition: String?
    public let equipmentNotes: String?

    public init(
        seatPosition: String? = nil,
        pinPosition: String? = nil,
        equipmentNotes: String? = nil
    ) {
        self.seatPosition = seatPosition
        self.pinPosition = pinPosition
        self.equipmentNotes = equipmentNotes
    }
}

// ─── Role Multipliers ────────────────────────────────────────────────────────

public let HYPERTROPHY_ROLE_MULTIPLIERS: [MuscleRole: Double] = [
    .PRIMARY: 1.0,
    .SECONDARY: 0.5,
    .STABILIZER: 0.4,
    .NEUTRALIZER: 0.1
]

public let VOLUME_CONTRIBUTION_FALLBACKS: [MuscleRole: Double] = [
    .PRIMARY: 1.0,
    .SECONDARY: 0.5,
    .STABILIZER: 0.4,
    .NEUTRALIZER: 0.1
]

public let FATIGUE_ROLE_MULTIPLIERS: [MuscleRole: Double] = [
    .PRIMARY: 1.0,
    .SECONDARY: 0.2,
    .STABILIZER: 0.05,
    .NEUTRALIZER: 0.0
]

// ─── Helpers ─────────────────────────────────────────────────────────────────

public func resolveMuscleVolumeContribution(
    involvement: InvolvedMuscle,
    capAtOne: Bool = true
) -> Double {
    let fallback = VOLUME_CONTRIBUTION_FALLBACKS[involvement.role] ?? 0.0
    let raw = involvement.volumeContribution ?? fallback
    if capAtOne {
        return raw.clamped(to: 0.0...1.0)
    } else {
        return max(raw, 0.0)
    }
}
