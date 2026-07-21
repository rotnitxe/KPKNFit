import SwiftUI

internal enum ExerciseDrainOverlayChannelV2: String, Codable {
    case ENERGY
    case BACK
    case MUSCLE
}

internal struct ExerciseDrainOverlayItemV2: Codable {
    let label: String
    let delta: Int
    let channel: ExerciseDrainOverlayChannelV2
}

internal struct ExerciseDrainOverlayStateV2: Codable {
    let key: String
    let exerciseName: String
    let items: [ExerciseDrainOverlayItemV2]
}

internal struct WorkoutStageTransitionTargetV2: Codable {
    let exerciseId: String
    let order: Int
    let label: String
}

internal enum WorkoutSetCardVisualState: String, Codable {
    case FUTURE
    case COMPLETED
    case SKIPPED
    case ACTIVE
}

internal struct WorkoutSetPagerItem: Codable {
    let index: Int
    let label: String
    let state: WorkoutSetCardVisualState
    let isEditing: Bool
    let side: String?
    let pulseToken: Int64?
    let isWarmupOrFeedback: Bool

    init(
        index: Int,
        label: String,
        state: WorkoutSetCardVisualState,
        isEditing: Bool,
        side: String? = nil,
        pulseToken: Int64? = nil,
        isWarmupOrFeedback: Bool = false
    ) {
        self.index = index
        self.label = label
        self.state = state
        self.isEditing = isEditing
        self.side = side
        self.pulseToken = pulseToken
        self.isWarmupOrFeedback = isWarmupOrFeedback
    }
}

internal let WORKOUT_WARMUP_BLUE = Color(red: 68 / 255.0, green: 138 / 255.0, blue: 255 / 255.0)

internal func workoutSetPagerAccent(
    state: WorkoutSetCardVisualState,
    primaryColor: Color = .accentColor,
    tertiaryColor: Color = .orange,
    outlineColor: Color = .gray,
    futureColor: Color = .secondary,
    isWarmupOrFeedback: Bool = false,
    sessionAccentColor: Color? = nil
) -> Color {
    if isWarmupOrFeedback {
        return WORKOUT_WARMUP_BLUE
    }
    if let sessionAccentColor = sessionAccentColor {
        return sessionAccentColor
    }
    switch state {
    case .ACTIVE: return primaryColor
    case .COMPLETED: return tertiaryColor
    case .SKIPPED: return outlineColor
    case .FUTURE: return futureColor
    }
}

internal func resolveWorkoutHeaderGroupLabel(
    partName: String?,
    type: String?,
    category: String?
) -> String? {
    let explicitPart: String?
    if let part = partName?.trimmingCharacters(in: .whitespacesAndNewlines),
       !part.isEmpty,
       part.caseInsensitiveCompare("sesión") == .orderedSame ||
       part.caseInsensitiveCompare("sesion") == .orderedSame ||
       part.caseInsensitiveCompare("sesión principal") == .orderedSame ||
       part.caseInsensitiveCompare("sesion principal") == .orderedSame {
        explicitPart = nil
    } else {
        explicitPart = partName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? partName?.trimmingCharacters(in: .whitespacesAndNewlines) : nil
    }

    let cleanType = type?.trimmingCharacters(in: .whitespacesAndNewlines)
    let explicitType = (cleanType?.isEmpty == false) ? cleanType : nil

    let cleanCategory = category?.trimmingCharacters(in: .whitespacesAndNewlines)
    let explicitCategory = (cleanCategory?.isEmpty == false) ? cleanCategory : nil

    return normalizeWorkoutHeaderLabel(explicitPart ?? explicitType ?? explicitCategory)
}

private func normalizeWorkoutHeaderLabel(_ raw: String?) -> String? {
    guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
    
    // Replace multiple spaces with a single space
    let components = trimmed.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
    let value = components.joined(separator: " ")
    
    if value.caseInsensitiveCompare("principales") == .orderedSame {
        return "Principales"
    }
    if value.caseInsensitiveCompare("principal") == .orderedSame {
        return "Principal"
    }
    
    let letterChars = value.filter { $0.isLetter }
    let upperCount = letterChars.filter { $0.isUppercase }.count
    let upperRatio = letterChars.isEmpty ? 0.0 : Double(upperCount) / Double(letterChars.count)
    
    if upperRatio >= 0.75 {
        return value.uppercased()
    }
    
    // Case-insensitive replacements for "principales" and "principal"
    var replaced = value
    if let regexPrincipales = try? NSRegularExpression(pattern: "principales", options: .caseInsensitive) {
        replaced = regexPrincipales.stringByReplacingMatches(in: replaced, options: [], range: NSRange(replaced.startIndex..., in: replaced), withTemplate: "Principales")
    }
    if let regexPrincipal = try? NSRegularExpression(pattern: "principal", options: .caseInsensitive) {
        replaced = regexPrincipal.stringByReplacingMatches(in: replaced, options: [], range: NSRange(replaced.startIndex..., in: replaced), withTemplate: "Principal")
    }
    
    return replaced
}
