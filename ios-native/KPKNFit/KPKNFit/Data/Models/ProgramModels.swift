import Foundation

public enum ProgramMode: String, Codable {
    case POWERLIFTING
    case HYPERTROPHY
    case POWERBUILDING
}

public struct Program: Identifiable, Codable {
    public let id: String
    public let name: String
    public let description: String?
    public let coverImage: String?
    public let mode: ProgramMode
    public let totalProgramWeeks: Int
    
    public init(
        id: String,
        name: String,
        description: String? = nil,
        coverImage: String? = nil,
        mode: ProgramMode = .HYPERTROPHY,
        totalProgramWeeks: Int = 1
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.coverImage = coverImage
        self.mode = mode
        self.totalProgramWeeks = totalProgramWeeks
    }
}
