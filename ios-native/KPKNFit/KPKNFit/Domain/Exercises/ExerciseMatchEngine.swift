import Foundation

struct ExerciseMatchResult {
    let exercise: ExerciseMuscleInfo
    let score: Double
}

struct InferredSuggestions {
    let efc: Double?
    let cnc: Double?
    let ssc: Double?
    let suggestedMuscles: [InvolvedMuscle]
    let suggestedBodyPart: String
    let suggestedChain: String
    let suggestedTier: String
    let suggestedRestSeconds: Int
    let matchCount: Int
    let topMatches: [ExerciseMatchResult]
    let anatomicalConsiderations: [(String, String)]
    let commonMistakes: [(String, String)]
    let setupCues: [String]
    let executionCues: [String]
}

private let equipmentGroups: [String: Set<String>] = [
    "barra": ["barra", "barra olímpica", "barra ez", "barra corta"],
    "mancuerna": ["mancuerna"],
    "maquina": ["máquina", "maquina", "hack"],
    "polea": ["polea", "cable"],
    "peso corporal": ["peso corporal", "bandas", "ninguno"],
    "kettlebell": ["kettlebell"],
]

private let forceGroups: [String: Set<String>] = [
    "empuje": ["empuje", "press", "extensión", "extension"],
    "tirón": ["tirón", "tiron", "remo", "pull", "curl", "dominada", "face pull"],
    "sentadilla": ["sentadilla", "squat", "step-up", "zancada", "lunge"],
    "bisagra": ["bisagra", "deadlift", "peso muerto", "rumano", "hip thrust", "extensión cadera"],
    "anti-extension": ["anti-extensión", "anti-extension", "plancha", "ab wheel", "rollout", "pallof"],
    "anti-flexion": ["anti-flexión", "anti-flexion", "farmer", "granjero"],
    "anti-rotacion": ["anti-rotación", "anti-rotacion", "pallof"],
    "flexion": ["flexión", "flexion", "crunch", "elevación", "elevacion"],
]

private let categoryWeights: [String: Double] = [
    "fuerza": 1.0, "potencia": 0.9, "hipertrofia": 0.8,
    "isometría": 0.7, "isometria": 0.7,
]

private let typeHierarchy: [String: Double] = [
    "básico": 1.0, "basico": 1.0, "variante": 0.8,
    "accesorio": 0.6, "aislamiento": 0.5,
]

private let nameTokensToIgnore: Set<String> = [
    "con", "de", "del", "en", "para", "el", "la", "los", "las", "un", "una",
    "y", "o", "a", "al", "por", "sin",
]

private func getEquipmentGroup(_ equip: String?) -> String? {
    guard let e = equip?.lowercased().trimmingCharacters(in: .whitespaces) else { return nil }
    return equipmentGroups.first { $0.value.contains(e) }?.key
}

private func getForceGroup(_ force: String?) -> String? {
    guard let f = force?.lowercased().trimmingCharacters(in: .whitespaces) else { return nil }
    return forceGroups.first { $0.value.contains(f) }?.key
}

private func tokenizeName(_ name: String) -> Set<String> {
    Set(name.lowercased()
        .replacingOccurrences(of: "[^a-záéíóúñü\\s]", with: "", options: .regularExpression)
        .components(separatedBy: .whitespaces)
        .filter { !$0.isEmpty && !nameTokensToIgnore.contains($0) })
}

private func nameSimilarity(_ queryTokens: Set<String>, _ candidate: String) -> Double {
    guard !queryTokens.isEmpty else { return 0.0 }
    let candidateTokens = tokenizeName(candidate)
    guard !candidateTokens.isEmpty else { return 0.0 }
    let intersection = queryTokens.intersection(candidateTokens)
    let unionSize = queryTokens.count + candidateTokens.count - intersection.count
    let jaccard = Double(intersection.count) / Double(unionSize)
    let overlap = Double(intersection.count) / Double(queryTokens.count)
    return min(max(jaccard * 0.6 + overlap * 0.4, 0.0), 1.0)
}

func findBestMatches(
    database: [ExerciseMuscleInfo],
    name: String,
    equipment: String,
    force: String,
    category: String,
    type: String,
    bodyPart: String,
    chain: String,
    maxResults: Int = 8
) -> [ExerciseMatchResult] {
    let queryTokens = tokenizeName(name)
    let queryEquipGroup = getEquipmentGroup(equipment)
    let queryForceGroup = getForceGroup(force)
    let queryCategory = category.lowercased().trimmingCharacters(in: .whitespaces)
    let queryType = type.lowercased().trimmingCharacters(in: .whitespaces)
    let queryBodyPart = bodyPart.lowercased().trimmingCharacters(in: .whitespaces)
    let queryChain = chain.lowercased().trimmingCharacters(in: .whitespaces)

    return database
        .filter { $0.efc != nil && $0.cnc != nil }
        .map { candidate -> ExerciseMatchResult in
            var score = 0.0
            let candidateEquipGroup = getEquipmentGroup(candidate.equipment)
            score += candidate.equipment?.caseInsensitiveCompare(equipment) == .orderedSame ? 0.30 :
                (queryEquipGroup != nil && candidateEquipGroup == queryEquipGroup ? 0.25 :
                (queryEquipGroup != nil && candidateEquipGroup != nil ? 0.05 : 0.0))

            let candidateForceGroup = getForceGroup(candidate.force)
            score += candidate.force?.caseInsensitiveCompare(force) == .orderedSame ? 0.25 :
                (queryForceGroup != nil && candidateForceGroup == queryForceGroup ? 0.22 :
                (queryForceGroup != nil && candidateForceGroup != nil ? 0.05 : 0.0))

            score += candidate.category?.caseInsensitiveCompare(category) == .orderedSame ? 0.20 :
                (!queryCategory.isEmpty && candidate.category?.lowercased() == queryCategory ? 0.18 :
                (!queryCategory.isEmpty ? 0.02 : 0.0))

            let typeDiff: (Double?, Double?) = (typeHierarchy[queryType], typeHierarchy[candidate.type?.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""])
            score += candidate.type?.caseInsensitiveCompare(type) == .orderedSame ? 0.10 :
                (typeDiff.0 != nil && typeDiff.1 != nil ? 0.10 * (1.0 - abs(typeDiff.0! - typeDiff.1!)) : 0.0)

            score += candidate.bodyPart?.caseInsensitiveCompare(bodyPart) == .orderedSame ? 0.05 :
                (!queryBodyPart.isEmpty && candidate.bodyPart?.lowercased() == queryBodyPart ? 0.04 : 0.0)

            score += candidate.chain?.caseInsensitiveCompare(chain) == .orderedSame ? 0.05 :
                (!queryChain.isEmpty && candidate.chain?.lowercased() == queryChain ? 0.04 : 0.0)

            score += nameSimilarity(queryTokens, candidate.name) * 0.05

            return ExerciseMatchResult(exercise: candidate, score: score)
        }
        .filter { $0.score > 0.15 }
        .sorted { $0.score > $1.score }
        .prefix(maxResults)
        .map { $0 }
}

func inferFromMatches(
    matches: [ExerciseMatchResult],
    name: String,
    equipment: String,
    force: String,
    category: String,
    isAxialLoaded: Bool
) -> InferredSuggestions {
    guard !matches.isEmpty else {
        let fallback = inferExerciseMetrics(
            type: "Accesorio", force: force, equipment: equipment,
            category: category, isAxialLoaded: isAxialLoaded, exerciseName: name
        )
        return InferredSuggestions(
            efc: fallback.efc, cnc: fallback.cnc, ssc: fallback.ssc,
            suggestedMuscles: fallback.suggestedMuscles,
            suggestedBodyPart: fallback.suggestedBodyPart,
            suggestedChain: fallback.suggestedChain,
            suggestedTier: "T2", suggestedRestSeconds: 90,
            matchCount: 0, topMatches: [],
            anatomicalConsiderations: [], commonMistakes: [],
            setupCues: [], executionCues: []
        )
    }

    let matchesWithMetrics = matches.filter { $0.exercise.efc != nil && $0.exercise.cnc != nil && $0.exercise.ssc != nil }
    let totalWeight = matchesWithMetrics.isEmpty ? matches.reduce(0) { $0 + $1.score } : matchesWithMetrics.reduce(0) { $0 + $1.score }
    let avgEfc = matchesWithMetrics.isEmpty ? nil : matchesWithMetrics.reduce(0.0) { $0 + ($1.exercise.efc! * $1.score) } / totalWeight
    let avgCnc = matchesWithMetrics.isEmpty ? nil : matchesWithMetrics.reduce(0.0) { $0 + ($1.exercise.cnc! * $1.score) } / totalWeight
    let avgSsc = matchesWithMetrics.isEmpty ? nil : matchesWithMetrics.reduce(0.0) { $0 + ($1.exercise.ssc! * $1.score) } / totalWeight

    let sscMultiplier = isAxialLoaded ? 1.0 : 0.4

    let (eqEfcMult, eqCncMult): (Double, Double) = {
        switch equipment.lowercased() {
        case "barra": return (1.0, 1.2)
        case "mancuerna": return (0.9, 1.1)
        case "máquina", "polea": return (0.8, 0.6)
        case "peso corporal": return (0.8, 0.8)
        default: return (1.0, 1.0)
        }
    }()

    let efc = avgEfc.map { min(max($0 * eqEfcMult, 0.5), 5.0) }
    let cnc = avgCnc.map { min(max($0 * eqCncMult, 0.5), 5.0) }
    let ssc = avgSsc.map { min(max($0 * sscMultiplier, 0.0), 2.0) }

    var muscleScores: [String: (MuscleRole, Double)] = [:]
    for match in matches {
        for muscle in match.exercise.involvedMuscles {
            let newScore = (muscle.volumeContribution ?? 1.0) * match.score
            if let existing = muscleScores[muscle.muscle] {
                if newScore > existing.1 { muscleScores[muscle.muscle] = (muscle.role, newScore) }
            } else {
                muscleScores[muscle.muscle] = (muscle.role, newScore)
            }
        }
    }

    let sortedMuscles = muscleScores.sorted { $0.value.1 > $1.value.1 }
        .prefix(6)
        .map { InvolvedMuscle(muscle: $0.key, role: $0.value.0, volumeContribution: min(max($0.value.1, 0.3), 1.0)) }

    let bodyPartGroups = Dictionary(grouping: matches, by: { $0.exercise.bodyPart ?? "" })
    let bodyPart = bodyPartGroups.max(by: { a, b in
        let sumA = a.value.map(\.score).reduce(0.0, +)
        let sumB = b.value.map(\.score).reduce(0.0, +)
        return sumA < sumB
    })?.key ?? "upper"

    let chainGroups = Dictionary(grouping: matches, by: { $0.exercise.chain ?? "" })
    let chain = chainGroups.max(by: { a, b in
        let sumA = a.value.map(\.score).reduce(0.0, +)
        let sumB = b.value.map(\.score).reduce(0.0, +)
        return sumA < sumB
    })?.key ?? "full"

    let filteredForTier = matches.filter { !($0.exercise.tier ?? "").isEmpty }
    let tierGroups = Dictionary(grouping: filteredForTier, by: { $0.exercise.tier ?? "" })
    let tier = tierGroups.max(by: { a, b in
        let sumA = a.value.map(\.score).reduce(0.0, +)
        let sumB = b.value.map(\.score).reduce(0.0, +)
        return sumA < sumB
    })?.key ?? "T2"

    let restValues = matches.compactMap { $0.exercise.averageRestSeconds }
    let restSeconds: Int
    if restValues.isEmpty {
        restSeconds = 90
    } else {
        let sum = restValues.reduce(0, +)
        restSeconds = Int(Double(sum) / Double(restValues.count))
    }

    let anatomical = Array(Set(matches.flatMap { $0.exercise.anatomicalConsiderations ?? [] }))
        .prefix(4).map { ($0.trait, $0.advice) }
    let mistakes = Array(Set(matches.flatMap { $0.exercise.commonMistakes ?? [] }))
        .prefix(4).map { ($0.mistake, $0.correction) }
    let setupCues = Array(Set(matches.flatMap { $0.exercise.setupCues ?? [] })).prefix(3).map { $0 }
    let executionCues = Array(Set(matches.flatMap { $0.exercise.executionCues ?? [] })).prefix(3).map { $0 }

    return InferredSuggestions(
        efc: efc, cnc: cnc, ssc: ssc, suggestedMuscles: sortedMuscles,
        suggestedBodyPart: bodyPart, suggestedChain: chain,
        suggestedTier: tier, suggestedRestSeconds: restSeconds,
        matchCount: matches.count, topMatches: matches,
        anatomicalConsiderations: anatomical, commonMistakes: mistakes,
        setupCues: setupCues, executionCues: executionCues
    )
}
