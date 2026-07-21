import Foundation

// MARK: - Enums

enum ExerciseCatalogRegion: String {
    case ALL, UPPER, LOWER, CORE, FULL

    var label: String {
        switch self {
        case .ALL: return "Todo"
        case .UPPER: return "Tren superior"
        case .LOWER: return "Tren inferior"
        case .CORE: return "Core"
        case .FULL: return "Full body"
        }
    }
}

enum ExerciseCatalogTrait: String {
    case BASIC, FREE, MACHINE, UNILATERAL

    var label: String {
        switch self {
        case .BASIC: return "Básicos"
        case .FREE: return "Libres"
        case .MACHINE: return "En máquina"
        case .UNILATERAL: return "Unilaterales"
        }
    }
}

enum ExerciseCatalogSort: String {
    case GROUP_BY_PATTERN, GROUP_BY_MUSCLE, GROUP_BY_REGION
    case RELEVANCE, NAME, FATIGUE_HIGH, FATIGUE_LOW

    var label: String {
        switch self {
        case .GROUP_BY_PATTERN: return "Patrón de movimiento"
        case .GROUP_BY_MUSCLE: return "Agrupar por músculo"
        case .GROUP_BY_REGION: return "Agrupar por región"
        case .RELEVANCE: return "Relevancia"
        case .NAME: return "Nombre A-Z"
        case .FATIGUE_HIGH: return "Fatiga alta"
        case .FATIGUE_LOW: return "Fatiga baja"
        }
    }

    var isGrouped: Bool {
        switch self {
        case .GROUP_BY_PATTERN, .GROUP_BY_MUSCLE, .GROUP_BY_REGION: return true
        default: return false
        }
    }
}

enum ExerciseKinshipBand: String {
    case VERY_CLOSE, CLOSE_VARIANT, TRANSFER_USEFUL

    var label: String {
        switch self {
        case .VERY_CLOSE: return "Parentesco alto"
        case .CLOSE_VARIANT: return "Variante cercana"
        case .TRANSFER_USEFUL: return "Transferencia util"
        }
    }
}

// MARK: - Structs

struct FriendlyFatigueBreakdown {
    let muscle: Int
    let snc: Int
    let spinal: Int
    let overall: Int
}

struct ExerciseKinship {
    let exercise: ExerciseMuscleInfo
    let band: ExerciseKinshipBand
    let similarityScore: Int
    let transferScore: Int
    let rationale: String
}

struct ExerciseKinshipResult {
    let similar: [ExerciseKinship]
    let transfer: [ExerciseKinship]
}

struct ExerciseVolumeSummary {
    let primarySeries: [String: Double]
    let secondarySeries: [String: Double]
    let stabilizerSeries: [String: Double]

    var hasData: Bool {
        !primarySeries.isEmpty || !secondarySeries.isEmpty || !stabilizerSeries.isEmpty
    }

    func totalForRole(_ role: String) -> Double {
        switch role {
        case "primary": return primarySeries.values.reduce(0, +)
        case "secondary": return secondarySeries.values.reduce(0, +)
        case "stabilizer": return stabilizerSeries.values.reduce(0, +)
        default: return 0.0
        }
    }
}

struct ThreeBandKinship {
    let lessSetup: [ExerciseKinship]
    let moreTransfer: [ExerciseKinship]
    let lessFatigue: [ExerciseKinship]
}

// MARK: - Private Constants

private let coreKeywords = ["abdomen", "core", "oblicuo", "transverso", "lumbar", "espalda baja", "recto abdominal"]
private let upperKeywords = ["pectoral", "pecho", "dorsal", "espalda", "trapecio", "romboide", "deltoide", "hombro", "bíceps", "biceps", "tríceps", "triceps", "antebrazo", "braquial"]
private let lowerKeywords = ["cuádriceps", "cuadriceps", "glúteo", "gluteo", "glúteos", "gluteos", "femoral", "isquio", "pantorrilla", "gemelo", "sóleo", "soleo", "aductor", "abductor", "pierna", "cadera"]

private let COMPETITION_BASIC_PATTERNS = [
    "peso muerto", "deadlift", "sentadilla", "squat", "press banca", "bench press",
]

private let searchStopWords: Set<String> = [
    "de", "del", "en", "con", "la", "el", "los", "las", "un", "una",
    "a", "al", "por", "para", "y", "o",
]

// MARK: - Private Helpers

private func broadMuscleLabel(_ raw: String) -> String {
    let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(raw)
    switch canonical {
    case "Pectorales": return "Pecho"
    case "Deltoides": return "Hombros"
    case "Dorsales": return "Espalda"
    case "Trapecio": return "Trapecio"
    case "Bíceps": return "Bíceps"
    case "Tríceps": return "Tríceps"
    case "Antebrazo": return "Antebrazos"
    case "Cuádriceps": return "Cuádriceps"
    case "Isquiosurales": return "Isquios"
    case "Glúteos": return "Glúteos"
    case "Aductores": return "Aductores"
    case "Pantorrillas": return "Pantorrillas"
    case "Abdomen", "Core": return "Core"
    case "Erectores Espinales": return "Espalda baja"
    default: return canonical
    }
}

private func roleWeight(_ role: MuscleRole) -> Double {
    switch role {
    case .PRIMARY: return 1.0
    case .SECONDARY: return 0.55
    case .STABILIZER: return 0.25
    case .NEUTRALIZER: return 0.15
    }
}

private func muscleProfile(_ info: ExerciseMuscleInfo) -> [String: Double] {
    guard !info.involvedMuscles.isEmpty else { return [:] }
    var aggregated: [String: Double] = [:]
    for muscle in info.involvedMuscles {
        let canonical = broadMuscleLabel(muscle.muscle)
        let contribution = max(muscle.volumeContribution ?? 0.0, 0.0)
        let weighted = roleWeight(muscle.role) * (contribution > 0.0 ? contribution : 1.0)
        aggregated[canonical, default: 0.0] += weighted
    }
    let total = aggregated.values.reduce(0, +)
    guard total > 0.0 else { return [:] }
    return aggregated.mapValues { $0 / total }
}

private func weightedJaccard(_ a: [String: Double], _ b: [String: Double]) -> Double {
    guard !a.isEmpty, !b.isEmpty else { return 0.0 }
    let keys = Set(a.keys).union(b.keys)
    let intersection = keys.reduce(0.0) { $0 + min(a[$1] ?? 0.0, b[$1] ?? 0.0) }
    let union = keys.reduce(0.0) { $0 + max(a[$1] ?? 0.0, b[$1] ?? 0.0) }
    guard union > 0.0 else { return 0.0 }
    return min(max(intersection / union, 0.0), 1.0)
}

private func equipmentFamily(_ raw: String?) -> String {
    switch raw?.lowercased() {
    case nil, "": return "unknown"
    case "barra", "mancuerna", "kettlebell", "disco", "eje", "saco de arena", "piedra", "balón medicinal": return "free_weights"
    case "máquina", "polea": return "machine"
    case "peso corporal", "trx", "banda": return "bodyweight"
    default: return (raw ?? "").lowercased()
    }
}

private func movementFamily(_ info: ExerciseMuscleInfo) -> String {
    let text = normalizeCatalogSearchValue(
        [info.name, info.force, info.type, info.category, info.alias].compactMap { $0 }.joined(separator: " ")
    )
    if ["sentadilla", "squat", "zancada", "lunge", "bulgara", "split squat", "step up", "prensa", "hack"].contains(where: { text.contains($0) }) {
        return "squat_knee"
    }
    if ["peso muerto", "deadlift", "rumano", "hip thrust", "bisagra", "good morning", "pull through", "rack pull"].contains(where: { text.contains($0) }) {
        return "hinge_hip"
    }
    if ["press banca", "bench", "flexiones", "fondos", "aperturas", "cruce"].contains(where: { text.contains($0) }) {
        return "horizontal_push"
    }
    if ["press militar", "overhead", "hombros", "arnold", "push press", "landmine"].contains(where: { text.contains($0) }) {
        return "vertical_push"
    }
    if ["dominada", "chin", "pull up", "jalon"].contains(where: { text.contains($0) }) {
        return "vertical_pull"
    }
    if ["remo", "row", "face pull"].contains(where: { text.contains($0) }) {
        return "horizontal_pull"
    }
    if ["curl", "biceps"].contains(where: { text.contains($0) }) {
        return "elbow_flexion"
    }
    if ["triceps", "extension"].contains(where: { text.contains($0) }) {
        return "elbow_extension"
    }
    if ["plancha", "pallof", "core", "ab wheel", "rodillo", "anti"].contains(where: { text.contains($0) }) {
        return "core_stability"
    }
    return [info.force?.lowercased(), resolveExerciseRegion(info).rawValue.lowercased()].compactMap { $0 }.joined(separator: "_")
}

private func fatigueSimilarity(_ target: ExerciseMuscleInfo, _ candidate: ExerciseMuscleInfo) -> Double {
    let delta = abs(Double(calculateFriendlyFatigue(target).overall - calculateFriendlyFatigue(candidate).overall))
    return min(max(1.0 - (delta / 10.0), 0.0), 1.0)
}

private func isCompetitionBasic(_ info: ExerciseMuscleInfo) -> Bool {
    let lower = info.name.lowercased()
    let matchesPattern = COMPETITION_BASIC_PATTERNS.contains { lower.contains($0) }
    let isBasicType = info.type?.lowercased() == "básico" || info.type?.lowercased() == "basico"
    let isT1 = info.tier?.lowercased() == "t1"
    return matchesPattern && (isBasicType || isT1)
}

private func buildKinshipRationale(
    _ target: ExerciseMuscleInfo,
    _ candidate: ExerciseMuscleInfo,
    _ muscleOverlap: Double
) -> String {
    var reasons: [String] = []
    if resolvePrimaryMuscleLabel(target) == resolvePrimaryMuscleLabel(candidate) {
        reasons.append("mismo musculo primario")
    }
    if movementFamily(target) == movementFamily(candidate) {
        reasons.append("patron muy parecido")
    }
    if (target.force ?? "").lowercased() == (candidate.force ?? "").lowercased() {
        reasons.append("misma direccion de fuerza")
    }
    if equipmentFamily(target.equipment) == equipmentFamily(candidate.equipment) {
        reasons.append("equipamiento similar")
    }
    if reasons.isEmpty {
        reasons.append(muscleOverlap >= 0.35 ? "transferencia por musculatura compartida" : "transferencia general de patron")
    }
    return reasons.prefix(2).joined(separator: " + ")
}

private struct KinshipScoredCandidate {
    let exercise: ExerciseMuscleInfo
    let similarity: Double
    let transfer: Double
    let rationale: String
}

// MARK: - Public Functions

func resolvePrimaryMuscleLabel(_ info: ExerciseMuscleInfo) -> String {
    let primary = info.involvedMuscles.first(where: { $0.role == .PRIMARY })?.muscle
        ?? info.involvedMuscles.first?.muscle
        ?? "General"
    return broadMuscleLabel(primary)
}

func resolveExerciseRegion(_ info: ExerciseMuscleInfo) -> ExerciseCatalogRegion {
    let bodyPart = (info.bodyPart ?? "").lowercased()
    let muscles = info.involvedMuscles.map { $0.muscle.lowercased() }.joined(separator: " ")
    let primaryMuscle = info.involvedMuscles.first(where: { $0.role == .PRIMARY })?.muscle.lowercased() ?? ""

    if bodyPart == "full" {
        if coreKeywords.contains(where: { primaryMuscle.contains($0) }) {
            return .CORE
        }
        return .FULL
    }
    if bodyPart == "lower" { return .LOWER }
    if bodyPart == "upper" {
        if coreKeywords.contains(where: { muscles.contains($0) }) && !upperKeywords.contains(where: { muscles.contains($0) }) {
            return .CORE
        }
        return .UPPER
    }
    if coreKeywords.contains(where: { muscles.contains($0) }) { return .CORE }
    if lowerKeywords.contains(where: { muscles.contains($0) }) && !upperKeywords.contains(where: { muscles.contains($0) }) { return .LOWER }
    if upperKeywords.contains(where: { muscles.contains($0) }) { return .UPPER }
    return .FULL
}

func matchesCatalogTrait(_ info: ExerciseMuscleInfo, _ trait: ExerciseCatalogTrait) -> Bool {
    let lowerName = info.name.lowercased()
    switch trait {
    case .BASIC:
        return info.type?.lowercased() == "básico" || info.type?.lowercased() == "basico"
    case .FREE:
        let freeEquipment: Set<String> = ["Barra", "Mancuerna", "Kettlebell", "Peso Corporal", "Disco", "Eje", "Saco de arena", "Balón Medicinal", "Piedra", "Neumático", "TRX", "Banda"]
        return freeEquipment.contains(info.equipment ?? "")
    case .MACHINE:
        let machineEquipment: Set<String> = ["Máquina", "Polea"]
        return machineEquipment.contains(info.equipment ?? "")
    case .UNILATERAL:
        let patterns = [
            "unilateral", "una mano", "un brazo", "a un brazo", "a una mano",
            "una pierna", "1 pierna", "1 mano", "single", "split squat", "búlgara", "bulgara"
        ]
        return patterns.contains(where: { lowerName.contains($0) })
    }
}

func calculateFriendlyFatigue(_ info: ExerciseMuscleInfo) -> FriendlyFatigueBreakdown {
    let index = ExerciseFatigueIndex.fromIntrinsic(
        efc: info.efc,
        cnc: info.cnc,
        ssc: info.ssc
    )
    return FriendlyFatigueBreakdown(
        muscle: index.muscle,
        snc: index.snc,
        spinal: index.spinal,
        overall: index.overall
    )
}

func inferSetupTimeLabel(_ info: ExerciseMuscleInfo) -> String {
    if let seconds = info.setupTime, seconds > 0 {
        if seconds < 45 { return "Rápido" }
        if seconds < 90 { return "1 min aprox." }
        if seconds < 150 { return "2 min aprox." }
        return "3+ min"
    }
    switch info.equipment {
    case "Barra":
        return (info.type?.lowercased() == "básico" || info.type?.lowercased() == "basico") ? "2-3 min" : "1-2 min"
    case "Máquina": return "30-60 seg"
    case "Polea": return "45-75 seg"
    case "Mancuerna", "Kettlebell": return "45-90 seg"
    case "Peso Corporal", "TRX", "Banda": return "Muy rápido"
    default: return "1-2 min"
    }
}

func inferLearningCurveLabel(_ info: ExerciseMuscleInfo) -> String {
    if let technical = info.technicalDifficulty {
        if technical < 2.2 { return "Baja" }
        if technical < 3.6 { return "Media"
        }
        return "Alta"
    }
    let fatigue = calculateFriendlyFatigue(info).overall
    let isIsolation = info.type?.lowercased() == "aislamiento"
    let isBasic = info.type?.lowercased() == "básico" || info.type?.lowercased() == "basico"
    let isBarra = info.equipment == "Barra"
    let isFull = resolveExerciseRegion(info) == .FULL

    if isIsolation && fatigue <= 4 { return "Baja" }
    if isBasic || isBarra || isFull { return "Alta" }
    return "Media"
}

func inferTransferLabel(_ info: ExerciseMuscleInfo) -> String {
    if let transfer = info.functionalTransfer, !transfer.isEmpty { return transfer }
    if let sports = info.sportsRelevance, !sports.isEmpty {
        let topSports = sports.prefix(3).joined(separator: ", ")
        return "Útil para \(topSports)."
    }
    switch resolveExerciseRegion(info) {
    case .FULL: return "Muy útil para fuerza general, coordinación y producción total de fuerza."
    case .LOWER: return "Buena transferencia a salto, sprint, cambios de dirección y potencia del tren inferior."
    case .UPPER: return "Buena transferencia a contacto, empuje, tracción y rendimiento del tren superior."
    case .CORE: return "Ayuda a estabilizar y transmitir fuerza entre tren superior e inferior."
    case .ALL: return "Aporta transferencia general según el objetivo del programa."
    }
}

func buildExerciseKinships(
    _ target: ExerciseMuscleInfo,
    _ catalog: [ExerciseMuscleInfo],
    similarLimit: Int = 3,
    transferLimit: Int = 3
) -> ExerciseKinshipResult {
    let targetProfile = muscleProfile(target)
    let targetPrimary = resolvePrimaryMuscleLabel(target)
    let targetRegion = resolveExerciseRegion(target)
    let targetFamily = movementFamily(target)

    let scored = catalog
        .filter { $0.id != target.id }
        .map { candidate -> KinshipScoredCandidate in
            let candidateProfile = muscleProfile(candidate)
            let muscleOverlap = weightedJaccard(targetProfile, candidateProfile)
            let samePrimary = resolvePrimaryMuscleLabel(candidate) == targetPrimary
            let sameRegion = resolveExerciseRegion(candidate) == targetRegion
            let sameFamily = movementFamily(candidate) == targetFamily
            let sameForce = (target.force ?? "").lowercased() == (candidate.force ?? "").lowercased()
            let sameChain = (target.chain ?? "").lowercased() == (candidate.chain ?? "").lowercased()
            let sameEquipmentFamily = equipmentFamily(target.equipment) == equipmentFamily(candidate.equipment)
            let fatigueMatch = fatigueSimilarity(target, candidate)

            let similarity = min(max(
                muscleOverlap * 0.56
                    + (samePrimary ? 0.14 : 0.0)
                    + (sameForce ? 0.10 : 0.0)
                    + (sameFamily ? 0.10 : 0.0)
                    + (sameEquipmentFamily ? 0.05 : 0.0)
                    + (sameRegion ? 0.03 : 0.0)
                    + fatigueMatch * 0.02,
                0.0), 1.0)

            let transfer = min(max(
                muscleOverlap * 0.30
                    + (sameForce ? 0.24 : 0.0)
                    + (sameChain ? 0.16 : 0.0)
                    + (sameRegion ? 0.14 : 0.0)
                    + (sameFamily ? 0.10 : 0.0)
                    + fatigueMatch * 0.06,
                0.0), 1.0)

            return KinshipScoredCandidate(
                exercise: candidate,
                similarity: similarity,
                transfer: transfer,
                rationale: buildKinshipRationale(target, candidate, muscleOverlap)
            )
        }

    let targetType = (target.type ?? "").lowercased()
    let targetFatigue = calculateFriendlyFatigue(target).overall

    let similar: [ExerciseKinship] = scored
        .filter { candidate in
            let info = candidate.exercise
            let sameFamily = movementFamily(info) == targetFamily
            let infoRegion = resolveExerciseRegion(info)
            let regionCompatible = infoRegion == targetRegion
                || (infoRegion != .CORE && targetRegion != .CORE)
            let candidateType = (info.type ?? "").lowercased()
            let typeCompatible: Bool
            if targetType.isEmpty || candidateType.isEmpty {
                typeCompatible = true
            } else if targetType.contains("aislamiento") {
                typeCompatible = candidateType.contains("aislamiento") || candidateType.contains("accesorio")
            } else if targetType.contains("accesorio") {
                typeCompatible = candidateType.contains("accesorio") || candidateType.contains("aislamiento")
            } else if targetType.contains("básico") || targetType.contains("basico") {
                typeCompatible = candidateType.contains("básico") || candidateType.contains("basico") || candidateType.contains("accesorio")
            } else {
                typeCompatible = true
            }
            return candidate.similarity >= 0.50 && sameFamily && regionCompatible && typeCompatible
        }
        .sorted {
            if $0.similarity != $1.similarity { return $0.similarity > $1.similarity }
            let f0 = calculateFriendlyFatigue($0.exercise).overall
            let f1 = calculateFriendlyFatigue($1.exercise).overall
            if f0 != f1 { return f0 < f1 }
            return $0.exercise.name < $1.exercise.name
        }
        .prefix(similarLimit)
        .map { candidate -> ExerciseKinship in
            let candidateFatigue = calculateFriendlyFatigue(candidate.exercise).overall
            let fatigueNote: String
            if candidateFatigue < targetFatigue - 1 {
                fatigueNote = " · menos fatigante"
            } else if candidateFatigue > targetFatigue + 1 {
                fatigueNote = " · más fatigante"
            } else {
                fatigueNote = ""
            }
            let band: ExerciseKinshipBand = candidate.similarity >= 0.72 ? .VERY_CLOSE : .CLOSE_VARIANT
            return ExerciseKinship(
                exercise: candidate.exercise,
                band: band,
                similarityScore: min(max(Int(candidate.similarity * 100), 0), 100),
                transferScore: min(max(Int(candidate.transfer * 100), 0), 100),
                rationale: candidate.rationale + fatigueNote
            )
        }

    let similarIds = Set(similar.map { $0.exercise.id })

    let transfer: [ExerciseKinship] = scored
        .filter { !similarIds.contains($0.exercise.id) && $0.transfer >= 0.45 }
        .sorted {
            let aIsComp = isCompetitionBasic($0.exercise)
            let bIsComp = isCompetitionBasic($1.exercise)
            if aIsComp != bIsComp { return aIsComp }
            if $0.transfer != $1.transfer { return $0.transfer > $1.transfer }
            if $0.similarity != $1.similarity { return $0.similarity > $1.similarity }
            return $0.exercise.name < $1.exercise.name
        }
        .prefix(transferLimit)
        .map { candidate -> ExerciseKinship in
            let rationale = isCompetitionBasic(candidate.exercise)
                ? "básico de competencia · \(candidate.rationale)"
                : candidate.rationale
            return ExerciseKinship(
                exercise: candidate.exercise,
                band: .TRANSFER_USEFUL,
                similarityScore: min(max(Int(candidate.similarity * 100), 0), 100),
                transferScore: min(max(Int(candidate.transfer * 100), 0), 100),
                rationale: rationale
            )
        }

    return ExerciseKinshipResult(similar: Array(similar), transfer: Array(transfer))
}

func computeExerciseVolumeSummary(_ info: ExerciseMuscleInfo) -> ExerciseVolumeSummary {
    var primary: [String: Double] = [:]
    var secondary: [String: Double] = [:]
    var stabilizer: [String: Double] = [:]

    for muscle in info.involvedMuscles {
        let name = broadMuscleLabel(muscle.muscle)
        let contribution = VolumeCalculator.resolveMuscleVolumeContribution(muscle, capAtOne: false)
        switch muscle.role {
        case .PRIMARY:
            primary[name, default: 0.0] += contribution
        case .SECONDARY:
            secondary[name, default: 0.0] += contribution
        case .STABILIZER, .NEUTRALIZER:
            stabilizer[name, default: 0.0] += contribution
        }
    }

    return ExerciseVolumeSummary(
        primarySeries: primary,
        secondarySeries: secondary,
        stabilizerSeries: stabilizer
    )
}

func buildThreeBandKinships(
    _ target: ExerciseMuscleInfo,
    _ catalog: [ExerciseMuscleInfo],
    limitPerBand: Int = 3
) -> ThreeBandKinship {
    let targetProfile = muscleProfile(target)
    let targetPrimary = resolvePrimaryMuscleLabel(target)
    let targetFatigue = calculateFriendlyFatigue(target).overall
    let targetSetupTime: Int
    if let setup = target.setupTime {
        targetSetupTime = setup
    } else {
        let label = inferSetupTimeLabel(target)
        if label.contains("Rápido") { targetSetupTime = 20 }
        else if label.contains("1 min") { targetSetupTime = 60 }
        else if label.contains("2") { targetSetupTime = 120 }
        else { targetSetupTime = 180 }
    }
    let targetTransfer = target.functionalTransfer ?? inferTransferLabel(target)
    _ = targetTransfer

    struct CandidateBundle {
        let similarity: Double
        let transfer: Double
        let fatigue: Int
        let setup: Int
        let exercise: ExerciseMuscleInfo
    }

    let candidates: [CandidateBundle] = catalog
        .filter { $0.id != target.id }
        .map { candidate -> CandidateBundle in
            let candidateProfile = muscleProfile(candidate)
            let muscleOverlap = weightedJaccard(targetProfile, candidateProfile)
            let candidateFatigue = calculateFriendlyFatigue(candidate).overall
            let candidateSetup: Int
            if let setup = candidate.setupTime {
                candidateSetup = setup
            } else {
                let label = inferSetupTimeLabel(candidate)
                if label.contains("Rápido") { candidateSetup = 20 }
                else if label.contains("1 min") { candidateSetup = 60 }
                else { candidateSetup = 150 }
            }
            let similarity = min(max(
                muscleOverlap * 0.56
                    + (resolvePrimaryMuscleLabel(candidate) == targetPrimary ? 0.14 : 0.0)
                    + ((target.force ?? "").lowercased() == (candidate.force ?? "").lowercased() ? 0.10 : 0.0)
                    + (movementFamily(candidate) == movementFamily(target) ? 0.10 : 0.0)
                    + (equipmentFamily(target.equipment) == equipmentFamily(candidate.equipment) ? 0.05 : 0.0)
                    + fatigueSimilarity(target, candidate) * 0.05,
                0.0), 1.0)
            let transfer = min(max(
                muscleOverlap * 0.30
                    + ((target.force ?? "").lowercased() == (candidate.force ?? "").lowercased() ? 0.24 : 0.0)
                    + ((target.chain ?? "").lowercased() == (candidate.chain ?? "").lowercased() ? 0.16 : 0.0)
                    + (resolveExerciseRegion(candidate) == resolveExerciseRegion(target) ? 0.14 : 0.0)
                    + (movementFamily(candidate) == movementFamily(target) ? 0.10 : 0.0)
                    + fatigueSimilarity(target, candidate) * 0.06,
                0.0), 1.0)

            return CandidateBundle(
                similarity: similarity,
                transfer: transfer,
                fatigue: candidateFatigue,
                setup: candidateSetup,
                exercise: candidate
            )
        }
        .filter { $0.similarity >= 0.25 }

    let lessSetup: [ExerciseKinship] = candidates
        .filter { $0.setup <= targetSetupTime }
        .sorted { $0.similarity > $1.similarity }
        .prefix(limitPerBand)
        .map { c in
            ExerciseKinship(
                exercise: c.exercise,
                band: .CLOSE_VARIANT,
                similarityScore: Int(c.similarity * 100),
                transferScore: Int(c.transfer * 100),
                rationale: inferSetupTimeLabel(c.exercise)
            )
        }

    let moreTransfer: [ExerciseKinship] = candidates
        .filter { $0.transfer > 0.45 }
        .sorted { $0.transfer > $1.transfer }
        .prefix(limitPerBand)
        .map { c in
            ExerciseKinship(
                exercise: c.exercise,
                band: .TRANSFER_USEFUL,
                similarityScore: Int(c.similarity * 100),
                transferScore: Int(c.transfer * 100),
                rationale: inferTransferLabel(c.exercise)
            )
        }

    let lessFatigue: [ExerciseKinship] = candidates
        .filter { $0.fatigue < targetFatigue - 1 }
        .sorted { $0.fatigue < $1.fatigue }
        .prefix(limitPerBand)
        .map { c in
            ExerciseKinship(
                exercise: c.exercise,
                band: .CLOSE_VARIANT,
                similarityScore: Int(c.similarity * 100),
                transferScore: Int(c.transfer * 100),
                rationale: "Fatiga: \(c.fatigue)/10"
            )
        }

    return ThreeBandKinship(
        lessSetup: lessSetup,
        moreTransfer: moreTransfer,
        lessFatigue: lessFatigue
    )
}

func buildExerciseComparisons(
    _ target: ExerciseMuscleInfo,
    _ catalog: [ExerciseMuscleInfo],
    limit: Int = 3
) -> [ExerciseMuscleInfo] {
    return buildExerciseKinships(target, catalog, similarLimit: limit, transferLimit: 0)
        .similar
        .map { $0.exercise }
}

func normalizeCatalogSearchValue(_ value: String) -> String {
    var result = value.lowercased()
    let diacritics: [Character: Character] = [
        "á": "a", "é": "e", "í": "i", "ó": "o", "ú": "u", "ü": "u", "ñ": "n"
    ]
    result = String(result.map { diacritics[$0] ?? $0 })
    let regex = try! NSRegularExpression(pattern: "[^a-z0-9]+")
    let range = NSRange(result.startIndex..., in: result)
    result = regex.stringByReplacingMatches(in: result, range: range, withTemplate: " ")
    result = result.trimmingCharacters(in: .whitespaces)
    let spaceRegex = try! NSRegularExpression(pattern: "\\s+")
    let spaceRange = NSRange(result.startIndex..., in: result)
    result = spaceRegex.stringByReplacingMatches(in: result, range: spaceRange, withTemplate: " ")
    return result
}

func calculateSearchScore(_ info: ExerciseMuscleInfo, query: String) -> Int {
    let normalizedQuery = normalizeCatalogSearchValue(query.trimmingCharacters(in: .whitespaces))
    guard !normalizedQuery.isEmpty else { return 0 }
    let meaningfulQuery = normalizeMeaningfulSearchValue(query.trimmingCharacters(in: .whitespaces))
    let terms: [String]
    let mt = meaningfulSearchTerms(query.trimmingCharacters(in: .whitespaces))
    if !mt.isEmpty {
        terms = mt
    } else {
        terms = normalizedQuery.split(separator: " ").map(String.init).filter { !$0.isEmpty }
    }
    let normalizedName = normalizeCatalogSearchValue(info.name)
    let meaningfulName = normalizeMeaningfulSearchValue(info.name)
    let nameTokens = normalizedName.split(separator: " ").map(String.init).filter { !$0.isEmpty }
    let meaningfulNameTokens = meaningfulName.split(separator: " ").map(String.init).filter { !$0.isEmpty }
    let aliasNormalized = normalizeCatalogSearchValue(info.alias ?? "")
    let meaningfulAlias = normalizeMeaningfulSearchValue(info.alias ?? "")
    let equipmentNormalized = normalizeCatalogSearchValue(info.equipment ?? "")
    let primaryMuscleNormalized = normalizeCatalogSearchValue(resolvePrimaryMuscleLabel(info))
    let regionNormalized = normalizeCatalogSearchValue(resolveExerciseRegion(info).label)
    let descriptionNormalized = normalizeCatalogSearchValue(info.description ?? "")

    let searchBlob = normalizeCatalogSearchValue(
        [info.name, info.alias, info.description, info.equipment, info.category, info.type,
         resolvePrimaryMuscleLabel(info), info.involvedMuscles.map { $0.muscle }.joined(separator: " ")
        ].compactMap { $0 }.joined(separator: " ")
    )

    if normalizedName == normalizedQuery { return 10_000 }
    if !meaningfulQuery.isEmpty && meaningfulName == meaningfulQuery { return 9_800 }
    if aliasNormalized == normalizedQuery { return 9_000 }
    if !meaningfulQuery.isEmpty && meaningfulAlias == meaningfulQuery { return 8_800 }
    if normalizedName.hasPrefix(normalizedQuery) { return 8_000 + normalizedQuery.count }
    if !meaningfulQuery.isEmpty && meaningfulName.hasPrefix(meaningfulQuery) { return 7_900 + meaningfulQuery.count }
    if !aliasNormalized.isEmpty && aliasNormalized.hasPrefix(normalizedQuery) { return 7_000 + normalizedQuery.count }
    if !meaningfulQuery.isEmpty && !meaningfulAlias.isEmpty && meaningfulAlias.hasPrefix(meaningfulQuery) { return 6_900 + meaningfulQuery.count }

    var score = 0
    if nameTokens.contains(normalizedQuery) { score += 160 }
    if normalizedName.contains(normalizedQuery) { score += 90 }
    if !meaningfulQuery.isEmpty && meaningfulName.contains(meaningfulQuery) { score += 180 }
    if !meaningfulQuery.isEmpty && terms.allSatisfy({ meaningfulNameTokens.contains($0) }) { score += 140 }
    if !aliasNormalized.isEmpty && aliasNormalized.contains(normalizedQuery) { score += 70 }
    if !meaningfulQuery.isEmpty && !meaningfulAlias.isEmpty && meaningfulAlias.contains(meaningfulQuery) { score += 60 }
    if primaryMuscleNormalized.contains(normalizedQuery) { score += 55 }
    if !equipmentNormalized.isEmpty && equipmentNormalized.contains(normalizedQuery) { score += 40 }
    if !regionNormalized.isEmpty && regionNormalized.contains(normalizedQuery) { score += 25 }

    if searchBlob.contains(normalizedQuery) {
        score += 80
    }

    let allTermsMatch = !terms.isEmpty && terms.allSatisfy { term in
        normalizedName.contains(term)
            || aliasNormalized.contains(term)
            || primaryMuscleNormalized.contains(term)
            || equipmentNormalized.contains(term)
            || descriptionNormalized.contains(term)
    }
    if allTermsMatch { score += 120 }

    for term in terms {
        if normalizedName.split(separator: " ").map(String.init).contains(term) { score += 30 }
        if normalizedName.split(separator: " ").map(String.init).contains(where: { $0.hasPrefix(term) }) { score += 20 }
        if aliasNormalized.split(separator: " ").map(String.init).contains(term) { score += 18 }
        if primaryMuscleNormalized.contains(term) { score += 16 }
        if equipmentNormalized.contains(term) { score += 10 }
        if descriptionNormalized.contains(term) { score += 6 }
        if searchBlob.contains(term) { score += 10 }
    }

    return score
}

func deduplicateCatalogVisualResults(_ items: [ExerciseMuscleInfo]) -> [ExerciseMuscleInfo] {
    var seen = Set<String>()
    return items.filter { info in
        let key = visualCatalogDedupKey(info)
        if seen.contains(key) { return false }
        seen.insert(key)
        return true
    }
}

func visualCatalogDuplicateNameKey(_ info: ExerciseMuscleInfo) -> String {
    return normalizeCatalogSearchValue(info.name)
}

// MARK: - Private Search Helpers

private func meaningfulSearchTerms(_ value: String) -> [String] {
    return normalizeCatalogSearchValue(value)
        .split(separator: " ")
        .map(String.init)
        .filter { !$0.isEmpty && !searchStopWords.contains($0) }
}

private func normalizeMeaningfulSearchValue(_ value: String) -> String {
    return meaningfulSearchTerms(value).joined(separator: " ")
}

private func visualCatalogDedupKey(_ info: ExerciseMuscleInfo) -> String {
    var normalizedName = normalizeMeaningfulSearchValue(info.name)
    let smithRegex = try! NSRegularExpression(pattern: "\\bmaquina smith\\b")
    let maquinaRegex = try! NSRegularExpression(pattern: "\\bmaquina\\b")
    let mancuernaRegex = try! NSRegularExpression(pattern: "\\bmancuernas\\b")
    let spaceRegex = try! NSRegularExpression(pattern: "\\s+")

    var range = NSRange(normalizedName.startIndex..., in: normalizedName)
    normalizedName = smithRegex.stringByReplacingMatches(in: normalizedName, range: range, withTemplate: "smith")
    range = NSRange(normalizedName.startIndex..., in: normalizedName)
    normalizedName = maquinaRegex.stringByReplacingMatches(in: normalizedName, range: range, withTemplate: "")
    range = NSRange(normalizedName.startIndex..., in: normalizedName)
    normalizedName = mancuernaRegex.stringByReplacingMatches(in: normalizedName, range: range, withTemplate: "mancuerna")
    range = NSRange(normalizedName.startIndex..., in: normalizedName)
    normalizedName = spaceRegex.stringByReplacingMatches(in: normalizedName, range: range, withTemplate: " ")
    normalizedName = normalizedName.trimmingCharacters(in: .whitespaces)

    let aliasTokens: [String] = (info.alias ?? "")
        .components(separatedBy: CharacterSet(charactersIn: ",;|"))
        .map { normalizeMeaningfulSearchValue($0) }
        .filter { !$0.isEmpty }

    let allKeys = [normalizedName] + aliasTokens
    return allKeys.min() ?? normalizedName
}
