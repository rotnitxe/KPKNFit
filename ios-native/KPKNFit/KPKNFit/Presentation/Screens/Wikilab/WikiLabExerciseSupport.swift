import SwiftUI

internal struct WikiLabExerciseLink {
    let id: String
    let name: String
    let subtitle: String
    
    init(id: String, name: String, subtitle: String = "") {
        self.id = id
        self.name = name
        self.subtitle = subtitle
    }
}

private let CANONICAL_MUSCLE_COLORS: [String: Color] = [
    "Pectorales": Color(hex: 0xE53935),
    "Dorsales": Color(hex: 0x1E88E5),
    "Trapecio": Color(hex: 0x1976D2),
    "Deltoides": Color(hex: 0xFFFF8F00),
    "Tríceps": Color(hex: 0x7B1FA2),
    "Bíceps": Color(hex: 0x8E24AA),
    "Antebrazo": Color(hex: 0x795548),
    "Abdomen": Color(hex: 0x00897B),
    "Cuádriceps": Color(hex: 0x43A047),
    "Isquiosurales": Color(hex: 0x2E7D32),
    "Glúteos": Color(hex: 0x558B2F),
    "Aductores": Color(hex: 0x7CB342),
    "Pantorrillas": Color(hex: 0x33691E),
    "Core": Color(hex: 0x00695C),
    "Erectores Espinales": Color(hex: 0x1565C0),
    "Cuello": Color(hex: 0x6D4C41),
]

internal func wikilabMuscleColor(_ name: String) -> Color {
    CANONICAL_MUSCLE_COLORS[name] ?? Color(hex: 0x757575)
}

internal func resolveWikiLabExerciseLinks(ids: [String], subtitle: String = "") -> [WikiLabExerciseLink] {
    var seen = Set<String>()
    return ids.compactMap { requestedId in
        let exercise = resolveExercise(requestedId) ?? resolveExerciseFromNaturalLanguage(requestedId) ?? nil
        guard let ex = exercise else { return nil }
        guard !seen.contains(ex.id) else { return nil }
        seen.insert(ex.id)
        return WikiLabExerciseLink(id: ex.id, name: ex.name, subtitle: subtitle)
    }
}

private func resolveExerciseFromNaturalLanguage(_ raw: String) -> ExerciseMuscleInfo? {
    let query = normalizeForLookup(raw)
    guard !query.isEmpty,
          let hit = exerciseCatalogV2().search(query, limit: 1).first,
          let configuration = try? exerciseCatalogV2().defaultConfiguration(for: hit.definitionId)
    else { return nil }
    return resolveExercise(configuration.id)
}

private func normalizeForLookup(_ raw: String) -> String {
    raw.lowercased()
        .replacingOccurrences(of: "á", with: "a")
        .replacingOccurrences(of: "é", with: "e")
        .replacingOccurrences(of: "í", with: "i")
        .replacingOccurrences(of: "ó", with: "o")
        .replacingOccurrences(of: "ú", with: "u")
        .replacingOccurrences(of: "ü", with: "u")
        .replacingOccurrences(of: "ñ", with: "n")
        .replacingOccurrences(of: "-", with: " ")
        .replacingOccurrences(of: "_", with: " ")
        .trimmingCharacters(in: .whitespaces)
}

internal func resolveWikiLabMuscleId(_ muscleName: String) -> String? {
    switch muscleName {
    case "Pectorales": return "pectoral"
    case "Dorsales": return "espalda"
    case "Trapecio": return "trapecio"
    case "Deltoides": return "deltoides"
    case "Tríceps": return "tríceps"
    case "Bíceps": return "bíceps"
    case "Antebrazo": return "antebrazo"
    case "Abdomen": return "abdomen"
    case "Cuádriceps": return "cuádriceps"
    case "Isquiosurales": return "isquiosurales"
    case "Glúteos": return "glúteos"
    case "Aductores": return "aductores"
    case "Pantorrillas": return "pantorrillas"
    case "Core": return "core"
    case "Erectores Espinales": return "erectores-espinales"
    case "Cuello": return "cuello"
    default: return nil
    }
}

internal func canonicalMuscleDisplayName(_ raw: String, emphasis: String? = nil) -> String {
    normalizeCanonicalMuscleGroup(raw: raw.replacingOccurrences(of: "-", with: " "), emphasis: emphasis)
}

internal func canonicalWikiLabMuscleId(raw: String, emphasis: String? = nil) -> String? {
    resolveWikiLabMuscleId(canonicalMuscleDisplayName(raw, emphasis: emphasis))
}

internal func canonicalWikiLabMuscleIdFromEntityId(_ muscleId: String) -> String? {
    let entity = WikiLabRepository.shared.getMuscleById(id: muscleId)
    if let canonicalFromName = entity?.name.flatMap({ canonicalWikiLabMuscleId(raw: $0) }) {
        return canonicalFromName
    }
    if let canonicalFromId = canonicalWikiLabMuscleId(raw: muscleId) {
        return canonicalFromId
    }
    guard let ent = entity else { return nil }
    let knownIds: Set<String> = [
        "pectoral", "espalda", "hombros", "brazos", "piernas",
        "abdomen", "core", "deltoides", "bíceps", "tríceps",
        "antebrazo", "cuádriceps", "isquiosurales", "glúteos",
        "aductores", "pantorrillas", "erectores-espinales",
        "cuello", "trapecio",
    ]
    return knownIds.contains(ent.id) ? ent.id : nil
}

internal func collapseInvolvedMusclesToCanonical(_ muscles: [InvolvedMuscle]) -> [InvolvedMuscle] {
    guard !muscles.isEmpty else { return [] }
    
    let rolePriority: [MuscleRole: Int] = [
        .PRIMARY: 0,
        .SECONDARY: 1,
        .STABILIZER: 2,
        .NEUTRALIZER: 3,
    ]
    
    var grouped = [String: InvolvedMuscle]()
    for item in muscles {
        let canonical = canonicalMuscleDisplayName(item.muscle, emphasis: item.emphasis)
        if let existing = grouped[canonical] {
            let existingPrio = rolePriority[existing.role] ?? 99
            let incomingPrio = rolePriority[item.role] ?? 99
            let role = incomingPrio < existingPrio ? item.role : existing.role
            let vol: Double? = {
                let a = existing.volumeContribution ?? 0.0
                let b = item.volumeContribution ?? 0.0
                let m = max(a, b)
                return m > 0.0 ? m : nil
            }()
            grouped[canonical] = InvolvedMuscle(muscle: canonical, role: role, volumeContribution: vol, emphasis: nil)
        } else {
            grouped[canonical] = InvolvedMuscle(muscle: canonical, role: item.role, volumeContribution: item.volumeContribution, emphasis: nil)
        }
    }
    return Array(grouped.values)
}

internal func normalizeCanonicalMuscleGroup(raw: String, emphasis: String? = nil) -> String {
    let cleaned = raw.trimmingCharacters(in: .whitespaces)
    if cleaned.hasPrefix("Quad") || cleaned.hasPrefix("Cua") { return "Cuádriceps" }
    if cleaned.hasPrefix("Ham") || cleaned.hasPrefix("Isq") || cleaned.hasPrefix("Isquio") { return "Isquiosurales" }
    if cleaned.hasPrefix("Glute") || cleaned.hasPrefix("Glú") { return "Glúteos" }
    if cleaned.hasPrefix("Cal") || cleaned.hasPrefix("Pan") { return "Pantorrillas" }
    if cleaned.hasPrefix("Adductor") || cleaned.hasPrefix("Adu") { return "Aductores" }
    if cleaned.hasPrefix("Pec") { return "Pectorales" }
    if cleaned.hasPrefix("Lat") || cleaned.hasPrefix("Dor") { return "Dorsales" }
    if cleaned.hasPrefix("Trap") { return "Trapecio" }
    if cleaned.hasPrefix("Del") { return "Deltoides" }
    if cleaned.hasPrefix("Tri") { return "Tríceps" }
    if cleaned.hasPrefix("Bi") { return "Bíceps" }
    if cleaned.hasPrefix("Fore") || cleaned.hasPrefix("Ante") { return "Antebrazo" }
    if cleaned.hasPrefix("Spine") || cleaned.hasPrefix("Erector") || cleaned.hasPrefix("Erec") { return "Erectores Espinales" }
    if cleaned.hasPrefix("Neck") || cleaned.hasPrefix("Cue") { return "Cuello" }
    if cleaned.hasPrefix("Neck") || cleaned.hasPrefix("Abd") { return "Abdomen" }
    if cleaned == "core" || cleaned == "Core" { return "Core" }
    return cleaned
}

private func calculateSearchScore(_ exercise: ExerciseMuscleInfo, _ query: String) -> Int {
    let q = query.lowercased().trimmingCharacters(in: .whitespaces)
    if q.isEmpty { return 0 }
    let name = exercise.name.lowercased()
    var score = 0
    if name == q { score += 100 }
    else if name.hasPrefix(q) { score += 60 }
    else if name.contains(q) { score += 30 }
    
    let alias = exercise.alias?.lowercased() ?? ""
    if !alias.isEmpty {
        if alias == q { score += 80 }
        else if alias.hasPrefix(q) { score += 45 }
        else if alias.contains(q) { score += 20 }
    }
    
    for token in q.split(separator: " ") {
        if name.contains(token) { score += 12 }
        if alias.contains(token) { score += 8 }
    }
    
    let cat = exercise.category?.lowercased() ?? ""
    if cat.contains(q) { score += 10 }
    
    let equip = exercise.equipment?.lowercased() ?? ""
    if equip.contains(q) { score += 6 }
    
    return score
}

internal func deduplicateCatalogVisualResults(_ exercises: [ExerciseMuscleInfo]) -> [ExerciseMuscleInfo] {
    var seen = Set<String>()
    return exercises.filter { seen.insert($0.id.lowercased()).inserted }
}
