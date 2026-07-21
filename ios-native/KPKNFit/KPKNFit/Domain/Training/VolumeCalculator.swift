import Foundation

public struct MuscleVolumeEntry {
    public let muscleId: String
    public let muscleName: String
    public let displayVolume: Double
    public let sets: Int
}

public struct CanonicalMuscleVolumeEntry {
    public let muscleId: String
    public let muscleName: String
    public let weeklySets: Double
}

public struct VolumeCalculator {
    private static let muscleNormalization: [String: String] = [
        "cuádriceps": "Cuádriceps",
        "cuadriceps": "Cuádriceps",
        "recto femoral": "Cuádriceps",
        "vasto": "Cuádriceps",
        "glúteo": "Glúteos",
        "gluteo": "Glúteos",
        "tensor de la fascia lata": "Glúteos",
        "femoral": "Isquiosurales",
        "semitendinoso": "Isquiosurales",
        "semimembranoso": "Isquiosurales",
        "isquio": "Isquiosurales",
        "trapecio": "Trapecio",
        "romboides": "Trapecio",
        "dorsal": "Dorsales",
        "dorsales": "Dorsales",
        "lat": "Dorsales",
        "redondo": "Dorsales",
        "erector": "Erectores Espinales",
        "lumbar": "Erectores Espinales",
        "espalda": "Dorsales",
        "tríceps": "Tríceps",
        "triceps": "Tríceps",
        "antebrazo": "Antebrazo",
        "adductor": "Aductores",
        "pectíneo": "Aductores",
        "gemelo": "Pantorrillas",
        "gastrocnemio": "Pantorrillas",
        "sóleo": "Pantorrillas",
        "soleo": "Pantorrillas",
        "pantorrilla": "Pantorrillas",
        "cuello": "Cuello",
        "cervical": "Cuello",
        "pectoral": "Pectorales",
        "pecho": "Pectorales",
        "abdominal": "Abdomen",
        "oblicuo": "Abdomen",
        "core": "Abdomen",
    ]

    public static func normalizeMuscleGroup(_ specificMuscle: String, emphasis: String? = nil) -> String {
        let lower = specificMuscle.lowercased().trimmingCharacters(in: .whitespaces)
        if lower.isEmpty { return "" }

        if lower.contains("deltoides") || lower.contains("hombro") {
            if lower.contains("posterior") { return "Deltoides Posterior" }
            if lower.contains("lateral") || lower.contains("medio") { return "Deltoides Lateral" }
            if lower.contains("anterior") || lower.contains("frontal") { return "Deltoides Anterior" }
            return "Deltoides Anterior"
        }

        if (lower.contains("bíceps") || lower.contains("biceps") || lower.contains("braquial")) && !lower.contains("femoral") {
            return "Bíceps"
        }

        for (key, normalized) in muscleNormalization {
            if lower.contains(key) { return normalized }
        }

        return specificMuscle.prefix(1).uppercased() + specificMuscle.dropFirst()
    }

    public static func normalizeCanonicalMuscleGroup(_ specificMuscle: String, emphasis: String? = nil) -> String {
        let lower = specificMuscle.lowercased()
            .replacingOccurrences(of: "-", with: " ")
            .replacingOccurrences(of: "_", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let emphasisLower = emphasis?.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if lower.contains("deltoides") || lower.contains("hombro") { return "Deltoides" }
        if (lower.contains("bíceps") || lower.contains("biceps") || lower.contains("braquial")) && !lower.contains("femoral") { return "Bíceps" }
        if lower.contains("tríceps") || lower.contains("triceps") { return "Tríceps" }
        if lower.contains("antebrazo") || lower.contains("braquiorradial") { return "Antebrazo" }
        if lower.contains("trapecio") { return "Trapecio" }
        if lower.contains("romboides") { return "Romboides" }
        if lower.contains("dorsal") || lower.contains("redondo") || lower.contains("espalda") || lower.contains("lat ") || lower.hasSuffix(" lat") || lower == "lat" || lower.hasPrefix("lat ") || lower.contains("lats") { return "Dorsales" }
        if lower.contains("erector") || lower.contains("lumbar") { return "Erectores Espinales" }
        if lower.contains("pectoral") || lower.contains("pecho") { return "Pectorales" }
        if lower.contains("cuádriceps") || lower.contains("cuadriceps") || lower.contains("recto femoral") || lower.contains("vasto") { return "Cuádriceps" }
        if lower.contains("isquio") || lower.contains("femoral") || lower.contains("semitendinoso") || lower.contains("semimembranoso") { return "Isquiosurales" }
        if lower.contains("glúteo") || lower.contains("gluteo") || lower.contains("tensor de la fascia lata") || lower.contains("tensor fascia") { return "Glúteos" }
        if lower.contains("psoas") { return "Core" }
        if lower.contains("adductor") || lower.contains("aductor") || lower.contains("pectíneo") || lower.contains("pectineo") { return "Aductores" }
        if lower.contains("gemelo") || lower.contains("pantorrilla") || lower.contains("gastrocnemio") || lower.contains("sóleo") || lower.contains("soleo") { return "Pantorrillas" }
        if lower.contains("cuello") || lower.contains("cervical") { return "Cuello" }
        if lower == "core" || lower.contains("transverso") || lower.contains("serrato") || emphasisLower.contains("core") { return "Core" }
        if lower.contains("abdominal") || lower.contains("abdomen") || lower.contains("oblicuo") || lower.contains("recto del abdomen") { return "Abdomen" }
        return specificMuscle.prefix(1).uppercased() + specificMuscle.dropFirst()
    }

    public static func calculateVolumeAdjustment(_ muscle: String, _ feedbackHistory: [PostSessionFeedback]) -> Double {
        if feedbackHistory.isEmpty { return 1.0 }
        let normalizedTarget = normalizeCanonicalMuscleGroup(muscle).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let muscleLogs = feedbackHistory.filter { log in
            log.muscleFeedback.keys.contains { key in
                normalizeCanonicalMuscleGroup(key).lowercased().trimmingCharacters(in: .whitespacesAndNewlines) == normalizedTarget
            }
        }
        if muscleLogs.isEmpty { return 1.0 }
        let recent = Array(muscleLogs.sorted { $0.date > $1.date }.prefix(3))
        var totalDoms = 0.0
        var totalStr = 0.0
        var count = 0
        for log in recent {
            if let entryKey = log.muscleFeedback.keys.first(where: { normalizeCanonicalMuscleGroup($0).lowercased().trimmingCharacters(in: .whitespacesAndNewlines) == normalizedTarget }),
               let entry = log.muscleFeedback[entryKey] {
                totalDoms += Double(entry.doms)
                totalStr += Double(entry.strengthCapacity)
                count += 1
            }
        }
        if count == 0 { return 1.0 }
        let avgDoms = totalDoms / Double(count)
        let avgStr = totalStr / Double(count)
        if avgDoms >= 3.5 || avgStr <= 5.0 {
            return 0.85
        } else if avgDoms <= 1.5 && avgStr >= 8.0 {
            return 1.10
        } else {
            return 1.0
        }
    }

    static func buildPerExerciseMuscleContributions(
        _ involvedMuscles: [InvolvedMuscle]
    ) -> [String: Double] {
        guard !involvedMuscles.isEmpty else { return [:] }
        var grouped: [String: Double] = [:]
        for involvement in involvedMuscles {
            let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, emphasis: involvement.emphasis)
            let contribution = VolumeCalculator.resolveMuscleVolumeContribution(involvement)
            if contribution > (grouped[canonical] ?? 0.0) {
                grouped[canonical] = contribution
            }
        }
        return grouped.filter { $0.value > 0.0 }
    }

    static func resolveMuscleVolumeContribution(
        _ involvement: InvolvedMuscle,
        capAtOne: Bool = true
    ) -> Double {
        let fallback: Double
        switch involvement.role {
        case .PRIMARY:     fallback = 1.0
        case .SECONDARY:   fallback = 0.5
        case .STABILIZER:  fallback = 0.4
        case .NEUTRALIZER: fallback = 0.1
        }
        let raw = involvement.volumeContribution ?? fallback
        return capAtOne ? min(max(raw, 0.0), 1.0) : max(raw, 0.0)
    }

    private static func countEffectiveSets(_ exerciseSets: [ExerciseSet]) -> Int {
        let counted = exerciseSets.filter { set in
            if set.isIneffective { return false }
            if let completedReps = set.completedReps { return completedReps > 0 }
            return (set.targetReps ?? 0) > 0 || (set.weight ?? 0.0) > 0.0
        }.count
        return counted == 0 ? exerciseSets.filter { !$0.isIneffective }.count : counted
    }

    public static func calculateUnifiedMuscleVolume(
        sessions: [Session],
        exerciseList: [ExerciseMuscleInfo]
    ) -> [MuscleVolumeEntry] {
        var volumeMap: [String: (Double, Int)] = [:]
        let exIndex = Dictionary(uniqueKeysWithValues: exerciseList.map { ($0.id.lowercased(), $0) })

        for session in sessions {
            let allExercises = session.parts.isEmpty ? session.exercises : session.parts.flatMap { $0.exercises }

            for exercise in allExercises {
                let validSetsCount = countEffectiveSets(exercise.sets)
                if validSetsCount > 0 {
                    let dbInfo = exercise.exerciseDbId.flatMap { exIndex[$0.lowercased()] }
                    let musclesToCount = dbInfo.flatMap { SessionMuscleFilter.relevantMusclesFor($0) } ?? []
                    if !musclesToCount.isEmpty {
                        let uniqueMultipliers = buildPerExerciseMuscleContributions(musclesToCount)
                        for (muscleName, maxMultiplier) in uniqueMultipliers {
                            let (currentVol, currentSets) = volumeMap[muscleName] ?? (0.0, 0)
                            volumeMap[muscleName] = (currentVol + Double(validSetsCount) * maxMultiplier, currentSets + validSetsCount)
                        }
                    }
                }
            }
        }

        return volumeMap
            .filter { $0.key != "General" }
            .map { (muscleName, vol) in
                MuscleVolumeEntry(
                    muscleId: muscleName.lowercased().replacingOccurrences(of: " ", with: "-"),
                    muscleName: muscleName,
                    displayVolume: (vol.0 * 10).rounded(.towardZero) / 10,
                    sets: vol.1
                )
            }
            .sorted { $0.displayVolume > $1.displayVolume }
    }

    public static func calculateCanonicalWeeklyMuscleVolume(
        program: Program,
        exerciseList: [ExerciseMuscleInfo]
    ) -> [CanonicalMuscleVolumeEntry] {
        let weeks = program.macrocycles
            .flatMap { $0.blocks }
            .flatMap { $0.mesocycles }
            .flatMap { $0.weeks }
        return calculateCanonicalWeeklyMuscleVolumeForWeeks(
            weeks: weeks,
            exerciseList: exerciseList,
            averageByWeek: false
        )
    }

    public static func calculateCanonicalWeeklyMuscleVolumeForWeeks(
        weeks: [ProgramWeek],
        exerciseList: [ExerciseMuscleInfo],
        averageByWeek: Bool = false
    ) -> [CanonicalMuscleVolumeEntry] {
        let sessions = weeks.flatMap { $0.sessions }
        let weekDivisor = averageByWeek ? Double(max(weeks.count, 1)) : 1.0
        return calculateCanonicalWeeklyMuscleVolumeForSessions(
            sessions: sessions,
            exerciseList: exerciseList,
            divisor: weekDivisor
        )
    }

    public static func calculateCanonicalWeeklyMuscleVolumeForSessions(
        sessions: [Session],
        exerciseList: [ExerciseMuscleInfo],
        divisor: Double = 1.0
    ) -> [CanonicalMuscleVolumeEntry] {
        let safeDivisor = divisor > 0.0 ? divisor : 1.0
        let exerciseIndex = Dictionary(uniqueKeysWithValues: exerciseList.map { ($0.id.lowercased(), $0) })
        var volumeMap: [String: Double] = [:]

        for session in sessions {
            let sessionVolume = calculateSessionAssistantMuscleVolume(session, exerciseIndex)
            for (muscleName, sessionSets) in sessionVolume {
                volumeMap[muscleName] = (volumeMap[muscleName] ?? 0.0) + sessionSets
            }
        }

        return volumeMap
            .map { (muscleName, totalSets) in
                let weeklySets = totalSets / safeDivisor
                return CanonicalMuscleVolumeEntry(
                    muscleId: muscleName.lowercased().replacingOccurrences(of: " ", with: "-"),
                    muscleName: muscleName,
                    weeklySets: (weeklySets * 10.0).rounded(.towardZero) / 10.0
                )
            }
            .sorted { $0.weeklySets > $1.weeklySets }
    }

    private static func calculateSessionAssistantMuscleVolume(
        _ session: Session,
        _ exerciseIndex: [String: ExerciseMuscleInfo]
    ) -> [String: Double] {
        var volumeMap: [String: Double] = [:]

        for exercise in session.allExercises() {
            let effectiveSets = countEffectiveSets(exercise.sets)
            if effectiveSets <= 0 { continue }

            guard let dbInfo = exercise.exerciseDbId.flatMap({ exerciseIndex[$0.lowercased()] }) else { continue }
            let contributions = buildPerExerciseMuscleContributions(
                SessionMuscleFilter.relevantMusclesFor(dbInfo)
            )
            for (canonical, multiplier) in contributions {
                volumeMap[canonical] = (volumeMap[canonical] ?? 0.0) + Double(effectiveSets) * multiplier
            }
        }

        return volumeMap
    }

    public static func calculateMuscleVolume(
        sessions: [Session],
        exerciseList: [ExerciseMuscleInfo],
        useFilter: Bool = true
    ) -> [String: Double] {
        let exerciseIndex = Dictionary(uniqueKeysWithValues: exerciseList.map { ($0.id.lowercased(), $0) })
        var volumeMap: [String: Double] = [:]

        for session in sessions {
            for exercise in session.allExercises() {
                let effectiveSets = countEffectiveSets(exercise.sets)
                if effectiveSets <= 0 { continue }

                guard let dbInfo = exercise.exerciseDbId.flatMap({ exerciseIndex[$0.lowercased()] }) else { continue }
                let musclesToCount = useFilter
                    ? SessionMuscleFilter.relevantMusclesFor(dbInfo)
                    : dbInfo.involvedMuscles
                let contributions = buildPerExerciseMuscleContributions(musclesToCount)
                for (canonical, multiplier) in contributions {
                    volumeMap[canonical] = (volumeMap[canonical] ?? 0.0) + Double(effectiveSets) * multiplier
                }
            }
        }
        return volumeMap
    }

    public static func calculateCompletedWeeklyMuscleVolume(
        logs: [WorkoutLog],
        exerciseList: [ExerciseMuscleInfo],
        weeksCount: Int = 1
    ) -> [CanonicalMuscleVolumeEntry] {
        if logs.isEmpty { return [] }

        let virtualSessions = logs.map { log in
            Session(
                id: log.id,
                name: log.sessionName,
                exercises: log.completedExercises.map { ex in
                    Exercise(
                        id: ex.exerciseId,
                        name: ex.exerciseName,
                        exerciseDbId: ex.exerciseDbId,
                        sets: ex.sets.map { set in
                            ExerciseSet(
                                id: set.id,
                                targetReps: set.reps,
                                weight: set.weight,
                                completedReps: set.skipped ? 0 : set.reps,
                                isIneffective: set.isWarmup || !AugeFatigueEngine.isSetEffective(set: set)
                            )
                        }
                    )
                }
            )
        }

        let muscleVolumeEntries = calculateUnifiedMuscleVolume(
            sessions: virtualSessions,
            exerciseList: exerciseList
        )
        let divisor = Double(max(weeksCount, 1))

        return muscleVolumeEntries.map { entry in
            CanonicalMuscleVolumeEntry(
                muscleId: entry.muscleId,
                muscleName: entry.muscleName,
                weeklySets: entry.displayVolume / divisor
            )
        }
    }
}
