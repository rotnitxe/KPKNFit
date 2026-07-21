import Foundation

public struct LearnCategory: Codable, Equatable {
    public let rawValue: String
    public let label: String
    public let color: String
    public let icon: String

    public init(rawValue: String, label: String, color: String, icon: String) {
        self.rawValue = rawValue
        self.label = label
        self.color = color
        self.icon = icon
    }

    public static let BEGINNER = LearnCategory(rawValue: "BEGINNER", label: "Novato", color: "0xFF00BCD4", icon: "rocket")
    public static let TRAINING = LearnCategory(rawValue: "TRAINING", label: "Entrenamiento", color: "0xFF1E88E5", icon: "dumbbell")
    public static let NUTRITION = LearnCategory(rawValue: "NUTRITION", label: "Nutrición", color: "0xFF43A047", icon: "apple")
    public static let RINGS = LearnCategory(rawValue: "RINGS", label: "RINGS", color: "0xFF448AFF", icon: "ring")
    public static let MENTAL_HEALTH = LearnCategory(rawValue: "MENTAL_HEALTH", label: "Salud Mental", color: "0xFF7E57C2", icon: "mind")
    public static let TOOLS = LearnCategory(rawValue: "TOOLS", label: "Herramientas", color: "0xFFFF8F00", icon: "wrench")
}

public enum ContentType: String, Codable {
    case PARAGRAPH
    case HEADING
    case BULLET
    case TIP
    case WARNING
    case CALLOUT
}

public struct ContentBlock: Codable {
    public let type: ContentType
    public let text: String
    public let items: [String]
    public let accentColor: String?

    public init(type: ContentType, text: String = "", items: [String] = [], accentColor: String? = nil) {
        self.type = type
        self.text = text
        self.items = items
        self.accentColor = accentColor
    }
}

public struct QuizQuestion: Codable {
    public let id: String
    public let question: String
    public let options: [String]
    public let correctIndex: Int
    public let explanation: String

    public init(id: String, question: String, options: [String], correctIndex: Int, explanation: String) {
        self.id = id
        self.question = question
        self.options = options
        self.correctIndex = correctIndex
        self.explanation = explanation
    }
}

public struct LearnSubmodule: Codable {
    public let id: String
    public let title: String
    public let content: [ContentBlock]
    public let quiz: [QuizQuestion]

    public init(id: String, title: String, content: [ContentBlock], quiz: [QuizQuestion]) {
        self.id = id
        self.title = title
        self.content = content
        self.quiz = quiz
    }
}

public enum BadgeTier: String, Codable {
    case BRONCE
    case PLATA
    case ORO
}

public struct LearnBadge: Codable {
    public let courseId: String
    public let courseName: String
    public let tier: BadgeTier?
    public let earnedAt: String
    public let score: Int
    public let isSpecial: Bool

    public init(courseId: String, courseName: String, tier: BadgeTier?, earnedAt: String, score: Int, isSpecial: Bool = false) {
        self.courseId = courseId
        self.courseName = courseName
        self.tier = tier
        self.earnedAt = earnedAt
        self.score = score
        self.isSpecial = isSpecial
    }
}

public struct CourseProgress: Codable {
    public let moduleId: String
    public var submoduleIndex: Int
    public var submoduleQuizScores: [String: Int]
    public var finalQuizScore: Int
    public var badge: LearnBadge?
    public var completedAt: String?
    public var disclaimerShown: Bool

    public init(moduleId: String, submoduleIndex: Int = -1, submoduleQuizScores: [String: Int] = [:], finalQuizScore: Int = -1, badge: LearnBadge? = nil, completedAt: String? = nil, disclaimerShown: Bool = false) {
        self.moduleId = moduleId
        self.submoduleIndex = submoduleIndex
        self.submoduleQuizScores = submoduleQuizScores
        self.finalQuizScore = finalQuizScore
        self.badge = badge
        self.completedAt = completedAt
        self.disclaimerShown = disclaimerShown
    }

    public var isCompleted: Bool { finalQuizScore >= 8 }
    public var inProgress: Bool { submoduleIndex >= 0 || !submoduleQuizScores.isEmpty }
    public var totalSubmodulesCompleted: Int { submoduleQuizScores.count }

    public func copy(
        moduleId: String? = nil,
        submoduleIndex: Int? = nil,
        submoduleQuizScores: [String: Int]? = nil,
        finalQuizScore: Int? = nil,
        badge: LearnBadge? = nil,
        completedAt: String? = nil,
        disclaimerShown: Bool? = nil
    ) -> CourseProgress {
        CourseProgress(
            moduleId: moduleId ?? self.moduleId,
            submoduleIndex: submoduleIndex ?? self.submoduleIndex,
            submoduleQuizScores: submoduleQuizScores ?? self.submoduleQuizScores,
            finalQuizScore: finalQuizScore ?? self.finalQuizScore,
            badge: badge ?? self.badge,
            completedAt: completedAt ?? self.completedAt,
            disclaimerShown: disclaimerShown ?? self.disclaimerShown
        )
    }
}

public struct LearnModule: Codable {
    public let id: String
    public let title: String
    public let category: LearnCategory
    public let shortDescription: String
    public let icon: String
    public let estimatedMinutes: Int
    public let disclaimer: String?
    public let submodules: [LearnSubmodule]
    public let finalQuiz: [QuizQuestion]
    public let isSpecial: Bool

    public init(id: String, title: String, category: LearnCategory, shortDescription: String, icon: String, estimatedMinutes: Int, disclaimer: String? = nil, submodules: [LearnSubmodule], finalQuiz: [QuizQuestion] = [], isSpecial: Bool = false) {
        self.id = id
        self.title = title
        self.category = category
        self.shortDescription = shortDescription
        self.icon = icon
        self.estimatedMinutes = estimatedMinutes
        self.disclaimer = disclaimer
        self.submodules = submodules
        self.finalQuiz = finalQuiz
        self.isSpecial = isSpecial
    }
}
