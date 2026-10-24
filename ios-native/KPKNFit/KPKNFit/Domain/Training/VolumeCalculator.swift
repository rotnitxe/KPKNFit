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
                                isIneffective: set.isWarmup || set.skipped || (set.reps <= 0 && (set.timeSeconds ?? 0) <= 0)
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

// MARK: Recorded training evidence (parity contract; no physiological predictor)
internal struct TrainingEvidenceFact: Codable, Equatable, Identifiable {
    var id: String { sourceId }
    let sourceId: String
    let activityId: String
    let occurredAtMs: Int64
    let sport: String
    let metric: String
    let value: Double
    let unit: String
    let origin: String
    let exerciseVariant: String?
    let method: String?
    let protocolId: String?
    let context: [String: String]
}

internal enum TrainingInsightsEngine {
    private static func known(_ value: String?) -> Bool {
        guard let text = value?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !text.isEmpty else { return false }
        return text.components(separatedBy:CharacterSet(charactersIn:":@")).allSatisfy { !["", "unknown", "desconocido", "desconocida", "sin datos", "null", "unspecified", "not_recorded"].contains($0.trimmingCharacters(in:.whitespacesAndNewlines)) }
    }
    static func comparable(_ a: TrainingEvidenceFact, _ b: TrainingEvidenceFact) -> Bool {
        guard known(a.exerciseVariant), known(a.method), known(a.protocolId), known(a.metric), known(a.unit),
              !a.context.isEmpty, a.context.allSatisfy({ known($0.key) && known($0.value) }) else { return false }
        return a.sport == b.sport && a.metric == b.metric && a.unit == b.unit &&
            a.origin == b.origin && a.origin != "HISTORICAL_AMBIGUOUS" &&
            a.exerciseVariant == b.exerciseVariant && a.method == b.method && a.protocolId == b.protocolId && a.context == b.context
    }
    private static func unique(_ history: [TrainingEvidenceFact]) -> [TrainingEvidenceFact] {
        Dictionary(grouping: history, by: \.sourceId).values.compactMap { values in
            guard let first=values.first, !first.sourceId.isEmpty, !first.activityId.isEmpty, values.allSatisfy({ $0 == first }) else { return nil }
            return first
        }
    }
    static func visible(_ facts: [TrainingEvidenceFact], nowMs: Int64, weeks: Int = 4) -> [TrainingEvidenceFact] {
        guard [1,4,12].contains(weeks) else { return [] }
        let from=nowMs-Int64(weeks)*7*86_400_000
        return unique(facts).filter { $0.value.isFinite && $0.occurredAtMs >= from && $0.occurredAtMs <= nowMs }
            .sorted { $0.occurredAtMs == $1.occurredAtMs ? $0.sourceId < $1.sourceId : $0.occurredAtMs < $1.occurredAtMs }
    }
    static func previous(_ current: TrainingEvidenceFact, history: [TrainingEvidenceFact], nowMs: Int64) -> [TrainingEvidenceFact] {
        guard current.value.isFinite, current.occurredAtMs <= nowMs else { return [] }
        let from = current.occurredAtMs - 90 * 86_400_000
        return Array(unique(history).filter {
            $0.value.isFinite && $0.occurredAtMs >= from && $0.occurredAtMs < current.occurredAtMs &&
                $0.sourceId != current.sourceId && $0.activityId != current.activityId && comparable(current, $0)
        }.sorted {
            $0.occurredAtMs == $1.occurredAtMs ? $0.sourceId < $1.sourceId : $0.occurredAtMs > $1.occurredAtMs
        }.prefix(6))
    }
    static func median(_ facts: [TrainingEvidenceFact]) -> Double? {
        guard facts.count >= 3 else { return nil }
        let values = facts.map(\.value).sorted()
        let middle = values.count / 2
        return values.count % 2 == 1 ? values[middle] : values[middle - 1] / 2 + values[middle] / 2
    }
}

/// Read projection of existing session records. Unknown document semantics stay descriptive.
internal enum TrainingHistoryProjection {
    private static let units: [String:String] = [
        "actualWorkSeconds":"s", "actualWorkingSeconds":"s", "actualHangSeconds":"s",
        "actualRestSecondsAfter":"s", "actualRestSecondsBefore":"s", "actualExternalDurationSeconds":"s",
        "elapsedMillis":"ms", "movingMillis":"ms", "elapsedDurationMillis":"ms", "finishTimeMillis":"ms",
        "distanceMeters":"m", "elevationGainMeters":"m", "elevationLossMeters":"m",
        "attemptedCount":"intentos", "successfulCount":"aciertos", "repetitions":"rep", "holdSeconds":"s",
        "completedRounds":"rondas", "completedRepetitions":"rep", "splitMillis":"ms"
    ]
    private static let quantityUnits: Set<String> = ["KILOGRAMS","POUNDS","KILOGRAM","POUND","METERS","FEET","SECONDS","MINUTES","METER","CENTIMETER","SECOND","MILLISECOND","METER_PER_SECOND","REPETITIONS","CALORIES","BEATS_PER_MINUTE","WATTS","RUNNING_STEPS_PER_MINUTE","REVOLUTIONS_PER_MINUTE","STROKES_PER_MINUTE","METERS_PER_SECOND","SECONDS_PER_KILOMETER"]
    private static let excluded: Set<String> = ["plan","references","complexes","definition","format","prescription","manualFtpReference","events","skills","drills","progressionProposals","ruleset","scaling","division","target","targets","penalties"]
    /// Mirrors registered Android v1 envelopes. Unknown engines remain recoverable raw documents.
    private static func supports(_ document: SportTrackingDocument) -> Bool {
        guard document.version == 1 else { return false }
        let sport = document.sportId
        if document.engineId == "performance_lab.manual" {
            return ["STRENGTH","HYPERTROPHY","POWERLIFTING","POWERBUILDING","WEIGHTLIFTING","CALISTHENICS","STRONGMAN","ARMWRESTLING","SPRINT","JUMP"].contains(sport)
        }
        let exact: [String:String] = [
            "strength.weightlifting":"WEIGHTLIFTING", "strength.calisthenics":"CALISTHENICS",
            "strength.strongman":"STRONGMAN", "strength.armwrestling":"ARMWRESTLING"
        ]
        if let expected = exact[document.engineId] { return expected == sport }
        let groups: [(String,[String])] = [
            ("combat.",["BOXING","KICKBOXING","MUAY_THAI","BJJ","WRESTLING","JUDO","MMA"]),
            ("field.",["CLIMBING","SPRINT","JUMP"]),
            ("functional.manual.",["CROSSFIT","HYROX"]),
            ("endurance.manual.",["RUNNING","CYCLING","TRAIL","ROWING","SWIMMING","TRIATHLON"]),
            ("game.manual.",["TENNIS","PADEL","TEAM_SPORT"])
        ]
        return groups.contains { prefix, sports in
            sports.contains(sport) && document.engineId == prefix + sport.lowercased()
        }
    }
    static func instant(_ text: String) -> Int64? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from:text) ?? ISO8601DateFormatter().date(from:text)
        return date.map { Int64($0.timeIntervalSince1970 * 1000) }
    }
    static func facts(_ logs: [WorkoutLog], nowMs: Int64) -> [TrainingEvidenceFact] {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]
        var result: [TrainingEvidenceFact] = []
        for versions in Dictionary(grouping:logs,by: \.id).values {
            guard let log=versions.first, !log.id.isEmpty,
                  let first=try? encoder.encode(log), versions.allSatisfy({ (try? encoder.encode($0)) == first }),
                  let time=instant(log.date), time > 0, time <= nowMs else { continue }
            let documents=log.sportDocuments ?? []
            let sports=Set(documents.map(\.sportId).filter { RecordedSportNames.labels[$0] != nil })
            let sport: String?
            if !sports.isEmpty { sport = sports.count == 1 && (log.recordedSportId == nil || sports.first == log.recordedSportId) ? sports.first : nil }
            else if let declared = log.recordedSportId { sport = RecordedSportNames.labels[declared] == nil ? nil : declared }
            else { sport = !log.completedExercises.isEmpty ? "STRENGTH" : nil }
            func add(_ id:String,_ metric:String,_ value:Double,_ unit:String,_ origin:String="HISTORICAL_AMBIGUOUS",_ at:Int64?=nil,_ documentSport:String?=nil) {
                guard let family=documentSport ?? sport, value.isFinite, value >= 0 else { return }
                result.append(TrainingEvidenceFact(sourceId:id,activityId:log.id,occurredAtMs:at ?? time,sport:family,metric:metric,value:value,unit:unit,origin:origin,exerciseVariant:nil,method:nil,protocolId:nil,context:[:]))
            }
            if let seconds=log.actualDurationSeconds { add("log:\(log.id):duration","Duración de sesión",Double(seconds),"s","OBSERVED") }
            else { add("log:\(log.id):duration","Duración de sesión (precisión original)",Double(log.durationMinutes),"min") }
            if let rpe=log.sessionEffortRpe {
                add("log:\(log.id):effort","Esfuerzo global declarado",rpe,"puntos 0–10","SELF_REPORTED")
                if let seconds=log.actualDurationSeconds, log.actualDurationSource != nil, log.sessionEffortProtocol != nil {
                    add("log:\(log.id):effort-duration","Esfuerzo × duración",rpe * Double(seconds) / 60,"u.a. (puntos·min)","DESCRIPTIVE_CALCULATION")
                }
            }
            for exercise in log.completedExercises {
                for set in exercise.sets where !set.isWarmup && !set.skipped && set.recordedPayloadV3?.skipped != true {
                    let prefix = "log:\(log.id):\(exercise.exerciseId):\(set.id)"
                    if let payload = set.recordedPayloadV3 {
                        let load = payload.loadInputMode == .ASSISTED ? payload.assistedLoad : payload.externalLoad
                        if let load {
                            add(prefix + ":load", "\(exercise.exerciseName) · " + (payload.loadInputMode == .ASSISTED ? "Asistencia registrada" : "Carga externa registrada"),load,"kg")
                        }
                        if payload.unitMode == .REPS, let repetitions = payload.completedReps {
                            add(prefix + ":reps","\(exercise.exerciseName) · Repeticiones registradas",Double(repetitions),"rep")
                        }
                        if let mode = payload.actualIntensityMode, let value = payload.actualIntensityValue {
                            if mode == .RPE { add(prefix + ":effort","RPE de serie declarado",value,"puntos 0–10","SELF_REPORTED") }
                            if mode == .RIR { add(prefix + ":reserve","RIR de serie declarado",value,"rep en reserva","SELF_REPORTED") }
                        }
                    } else {
                        if set.weight > 0 { add(prefix + ":load","\(exercise.exerciseName) · Carga histórica registrada",set.weight,"kg") }
                        if set.reps > 0 && set.timeSeconds == nil { add(prefix + ":reps","\(exercise.exerciseName) · Repeticiones históricas",Double(set.reps),"rep") }
                    }
                    if let seconds=set.timeSeconds { add(prefix + ":time","Tiempo registrado",Double(seconds),"s") }
                }
            }

            for versions in Dictionary(grouping:documents,by: \.id).values {
                guard let document=versions.first, let first=try? encoder.encode(document),
                      versions.allSatisfy({ (try? encoder.encode($0)) == first }),
                      ["RESULT","OBSERVATION"].contains(document.purpose), supports(document),
                      let bytes=try? encoder.encode(document.payload), let raw=(try? JSONSerialization.jsonObject(with:bytes)) as? [String:Any] else { continue }
                if document.engineId == "performance_lab.manual", let marks = raw["marks"] as? [[String:Any]] {
                    // Read explicit mark provenance and protocol; do not reproduce a capacity predictor.
                    for versions in Dictionary(grouping:marks,by: { $0["id"] as? String ?? "" }).values {
                        guard let mark=versions.first, let id=mark["id"] as? String, !id.isEmpty,
                              let bytes=try? JSONSerialization.data(withJSONObject:mark,options:[.sortedKeys]),
                              versions.allSatisfy({ (try? JSONSerialization.data(withJSONObject:$0,options:[.sortedKeys])) == bytes }),
                              mark["status"] as? String == "VALID", let value=mark["value"] as? NSNumber,
                              String(cString:value.objCType) != "c", value.doubleValue.isFinite, value.doubleValue >= 0,
                              let metric=mark["metric"] as? String, let unit=mark["unit"] as? String,
                              quantityUnits.contains(unit) else { continue }
                        let at=(mark["capturedAtEpochMillis"] as? NSNumber)?.int64Value ?? time
                        guard at > 0, at <= nowMs else { continue }
                        let context=mark["context"] as? [String:Any] ?? [:]
                        let protocolInfo=mark["protocol"] as? [String:Any] ?? [:]
                        let protocolId=protocolInfo["id"] as? String
                        let revision=(protocolInfo["version"] as? NSNumber)?.intValue
                        let provenance=mark["provenance"] as? String ?? "unknown"
                        let method=context["method"] as? String
                        let origin: String
                        switch provenance {
                        case "INSTRUMENT_MEASURED","MANUAL_MEASURED","IMPORTED_MEASURED": origin="OBSERVED"
                        case "DERIVED": origin="DESCRIPTIVE_CALCULATION"
                        case "MANUAL_ESTIMATE","VIDEO_ESTIMATE": origin="ESTIMATED"
                        default: origin="HISTORICAL_AMBIGUOUS"
                        }
                        var conditions=context["conditions"] as? [String:String] ?? [:]
                        for key in ["method","instrument","surface"] { if let text=context[key] as? String { conditions[key]=text } }
                        if let formula=mark["formulaId"] as? String { conditions["formula"]=formula }
                        result.append(TrainingEvidenceFact(sourceId:"document:\(document.id):mark:\(id)",activityId:log.id,
                            occurredAtMs:at,sport:document.sportId,metric:metric,value:value.doubleValue,unit:unit,origin:origin,
                            exerciseVariant:mark["dimension"] as? String,method:method.map { "\(provenance):\($0)" },
                            protocolId:protocolId.flatMap { name in revision.flatMap { $0 > 0 ? "\(name)@\($0)" : nil } },context:conditions))
                    }
                    continue
                }
                // Original values only: iOS has no native sport validator yet; no derived summaries or comparisons are invented.
                func visit(_ item:Any,_ path:String,_ inheritedUnit:String?=nil,_ inheritedTime:Int64?=nil) {
                    if let array=item as? [Any] { for (i,child) in array.enumerated() { visit(child,"\(path)[\(i)]",inheritedUnit,inheritedTime) }; return }
                    guard let object=item as? [String:Any] else { return }
                    for key in ["status","quality"] { if let status=object[key] as? String, ["FAILED","INVALIDATED","UNRECORDED","MISSED"].contains(status) { return } }
                    let at=(object["capturedAtEpochMillis"] as? NSNumber)?.int64Value ?? inheritedTime ?? time
                    guard at > 0, at <= nowMs else { return }
                    let unit=object["unit"] as? String ?? inheritedUnit
                    if let unit, quantityUnits.contains(unit), let number=object["value"] as? NSNumber, String(cString:number.objCType) != "c" {
                        add("document:\(document.id):\(path).value",path,number.doubleValue,unit,"HISTORICAL_AMBIGUOUS",at,document.sportId)
                    }
                    for (key,child) in object {
                        if excluded.contains(key) || key.hasPrefix("target") || key.hasPrefix("planned") || key.hasPrefix("declared") { continue }
                        if let fieldUnit=units[key], let number=child as? NSNumber, String(cString:number.objCType) != "c" {
                            add("document:\(document.id):\(path).\(key)","\(path).\(key)",number.doubleValue,fieldUnit,"HISTORICAL_AMBIGUOUS",at,document.sportId)
                        } else if child is [Any] || child is [String:Any] { visit(child,"\(path).\(key)",unit,at) }
                    }
                }
                visit(raw,document.engineId)
            }
        }
        return result
    }
}

internal enum RecordedSportNames {
    static let labels: [String:String] = [
        "STRENGTH":"Fuerza","HYPERTROPHY":"Hipertrofia","POWERLIFTING":"Powerlifting","POWERBUILDING":"Powerbuilding",
        "WEIGHTLIFTING":"Halterofilia","CALISTHENICS":"Calistenia","STRONGMAN":"Strongman","ARMWRESTLING":"Lucha de brazos",
        "RUNNING":"Carrera","CYCLING":"Ciclismo","TRAIL":"Trail","ROWING":"Remo","SWIMMING":"Natación","TRIATHLON":"Triatlón",
        "CROSSFIT":"CrossFit","HYROX":"HYROX","BOXING":"Boxeo","KICKBOXING":"Kickboxing","MUAY_THAI":"Muay thai",
        "BJJ":"Jiu-jitsu brasileño","WRESTLING":"Lucha","JUDO":"Judo","MMA":"Artes marciales mixtas","CLIMBING":"Escalada",
        "SPRINT":"Sprint","JUMP":"Salto","TENNIS":"Tenis","PADEL":"Pádel","TEAM_SPORT":"Deporte de equipo"
    ]
}
