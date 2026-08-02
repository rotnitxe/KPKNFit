import Foundation

// MARK: - Supporting types

// Redundant AugeMetrics structure removed (defined in AugeModels.swift)


// Redundant SharedMuscleInterference and SessionInterference removed (defined in AugeModels.swift)


// MARK: - InterferenceEngine

public enum InterferenceEngine {

    private static let MUSCLE_HALF_LIFE: [String: Double] = [
        "Bíceps": 24.0, "Tríceps": 24.0, "Deltoides": 24.0,
        "Deltoides Anterior": 24.0, "Deltoides Lateral": 24.0, "Deltoides Posterior": 24.0,
        "Pantorrillas": 24.0, "Abdomen": 24.0, "Antebrazo": 24.0,
        "Pectorales": 48.0, "Dorsales": 48.0, "Hombros": 48.0, "Trapecio": 48.0,
        "Core": 48.0, "Aductores": 48.0, "Cuello": 48.0,
        "Cuádriceps": 72.0, "Glúteos": 72.0,
        "Isquiosurales": 96.0, "Erectores Espinales": 96.0,
    ]

    private static let ROLE_DRAIN_WEIGHT: [MuscleRole: Double] = [
        .PRIMARY: 1.0,
        .SECONDARY: 0.6,
        .STABILIZER: 0.3,
        .NEUTRALIZER: 0.15,
    ]

    private static let MIN_RESIDUAL_THRESHOLD = 0.08
    private static let MIN_USAGE_THRESHOLD = 0.25

    // MARK: - Public API

    public static func calculateHistoricalInterferences(
        history: [WorkoutLog],
        exerciseDb: [String: ExerciseMuscleInfo],
        settings: AppSettings,
        days: Int = 30
    ) -> [SessionInterference] {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let cutoff = nowMs - Int64(days) * 24 * 3600_000
        let recent = history
            .filter { AugeUtils.parseIsoMs($0.date) >= cutoff }
            .sorted { AugeUtils.parseIsoMs($0.date) < AugeUtils.parseIsoMs($1.date) }

        var results: [SessionInterference] = []

        for i in 0..<(recent.count - 1) {
            let logA = recent[i]
            let logB = recent[i + 1]
            let msA = AugeUtils.parseIsoMs(logA.date)
            let msB = AugeUtils.parseIsoMs(logB.date)
            let hoursApart = Double(msB - msA) / 3_600_000.0

            if hoursApart <= 0 || hoursApart > 72.0 { continue }

            let drainsA = buildMuscleDrainsFromLog(logA, exerciseDb: exerciseDb, settings: settings)
            let usagesB = buildMuscleUsagesFromLog(logB, exerciseDb: exerciseDb)

            let interference = computeInterference(
                sessionAId: logA.sessionId,
                sessionAName: logA.sessionName,
                sessionBId: logB.sessionId,
                sessionBName: logB.sessionName,
                sessionADate: String(logA.date.prefix(10)),
                sessionBDate: String(logB.date.prefix(10)),
                drainsA: drainsA,
                usagesB: usagesB,
                hoursApart: hoursApart,
                isFromHistory: true
            )

            if let interference = interference {
                results.append(interference)
            }
        }

        return results.sorted { $0.interferencePercent > $1.interferencePercent }
    }

    public static func calculatePlannedInterferences(
        program: Program?,
        exerciseDb: [String: ExerciseMuscleInfo]
    ) -> [SessionInterference] {
        guard let program = program else { return [] }

        var sessions: [(Session, Int)] = []

        outer: for macro in program.macrocycles {
            for block in macro.blocks {
                for meso in block.mesocycles {
                    guard let week = meso.weeks.first else { continue }
                    for (idx, session) in week.sessions.enumerated() {
                        let dow = session.dayOfWeek ?? session.assignedDays.first ?? (idx + 1)
                        sessions.append((session, dow))
                    }
                    if !sessions.isEmpty { break outer }
                }
            }
        }

        if sessions.isEmpty { return [] }

        let sorted = sessions.sorted { $0.1 < $1.1 }

        var results: [SessionInterference] = []

        for i in 0..<(sorted.count - 1) {
            let (sessionA, dayA) = sorted[i]
            let (sessionB, dayB) = sorted[i + 1]
            let hoursApart = Double(max(dayB - dayA, 1)) * 24.0

            if hoursApart > 72.0 { continue }

            let drainsA = buildMuscleDrainsFromSession(sessionA, exerciseDb: exerciseDb)
            let usagesB = buildMuscleUsagesFromSession(sessionB, exerciseDb: exerciseDb)

            let interference = computeInterference(
                sessionAId: sessionA.id,
                sessionAName: sessionA.name,
                sessionBId: sessionB.id,
                sessionBName: sessionB.name,
                sessionADate: nil,
                sessionBDate: nil,
                drainsA: drainsA,
                usagesB: usagesB,
                hoursApart: hoursApart,
                isFromHistory: false
            )

            if let interference = interference {
                results.append(interference)
            }
        }

        if sorted.count >= 2 {
            let (sessionZ, dayZ) = sorted.last!
            let (sessionFirst, dayFirst) = sorted.first!
            let hoursApart = Double(max(7 - dayZ + dayFirst, 1)) * 24.0
            if hoursApart <= 72.0 {
                let drainsZ = buildMuscleDrainsFromSession(sessionZ, exerciseDb: exerciseDb)
                let usagesF = buildMuscleUsagesFromSession(sessionFirst, exerciseDb: exerciseDb)
                let interference = computeInterference(
                    sessionAId: sessionZ.id,
                    sessionAName: sessionZ.name,
                    sessionBId: sessionFirst.id,
                    sessionBName: sessionFirst.name,
                    sessionADate: nil,
                    sessionBDate: nil,
                    drainsA: drainsZ,
                    usagesB: usagesF,
                    hoursApart: hoursApart,
                    isFromHistory: false
                )
                if let interference = interference {
                    results.append(interference)
                }
            }
        }

        return results.sorted { $0.interferencePercent > $1.interferencePercent }
    }

    // MARK: - Drain / Usage Builders

    private static func buildMuscleDrainsFromLog(
        _ log: WorkoutLog,
        exerciseDb: [String: ExerciseMuscleInfo],
        settings: AppSettings
    ) -> [String: Double] {
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        var drains: [String: Double] = [:]

        for ce in log.completedExercises {
            guard let info = resolveExercise(ce.exerciseDbId, name: ce.exerciseName, exerciseDb: exerciseDb) else { continue }
            var accumulated: [String: Int] = [:]

            for set in ce.sets {
                guard AugeFatigueEngine.isSetEffective(set) else { continue }
                let metrics = AugeFatigueEngine.getDynamicAugeMetrics(exerciseName: info.name, equipment: info.equipment) ?? AugeMetrics()
                let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set: set,
                    metrics: metrics,
                    tanks: tanks,
                    accumulatedSets: accumulated.values.reduce(0, +),
                    restTime: ce.restTime
                )

                for im in info.involvedMuscles {
                    let roleW = ROLE_DRAIN_WEIGHT[im.role] ?? 0.0
                    if roleW > 0.0 {
                        let muscleDrain = drain.muscularDrainPct * roleW * 0.01
                        drains[im.muscle, default: 0.0] += muscleDrain
                    }
                }

                accumulated[ce.exerciseId, default: 0] += 1
            }
        }

        return drains.mapValues { min(1.0, max(0.0, $0)) }
    }

    private static func buildMuscleUsagesFromLog(
        _ log: WorkoutLog,
        exerciseDb: [String: ExerciseMuscleInfo]
    ) -> [String: Double] {
        var usages: [String: Double] = [:]

        for ce in log.completedExercises {
            guard let info = resolveExercise(ce.exerciseDbId, name: ce.exerciseName, exerciseDb: exerciseDb) else { continue }
            for im in info.involvedMuscles {
                let roleW = ROLE_DRAIN_WEIGHT[im.role] ?? 0.0
                if roleW > 0.0 {
                    usages[im.muscle] = max(usages[im.muscle, default: 0.0], roleW)
                }
            }
        }

        let maxUsage = usages.values.max().flatMap { $0 > 0.0 ? $0 : nil } ?? 1.0
        return usages.mapValues { $0 / maxUsage }
    }

    private static func buildMuscleDrainsFromSession(
        _ session: Session,
        exerciseDb: [String: ExerciseMuscleInfo]
    ) -> [String: Double] {
        var drains: [String: Double] = [:]
        let allExercises = session.exercises + session.parts.flatMap { $0.exercises }

        for ex in allExercises {
            guard let info = resolveExercise(ex.exerciseDbId, name: ex.name, exerciseDb: exerciseDb) else { continue }
            let metrics = AugeFatigueEngine.getDynamicAugeMetrics(exerciseName: info.name, equipment: info.equipment) ?? AugeMetrics()
            let estimatedDrain = (metrics.efc / 5.0) * 0.4

            for im in info.involvedMuscles {
                let roleW = ROLE_DRAIN_WEIGHT[im.role] ?? 0.0
                if roleW > 0.0 {
                    let muscleDrain = estimatedDrain * roleW
                    drains[im.muscle] = max(drains[im.muscle, default: 0.0], muscleDrain)
                }
            }
        }

        return drains.mapValues { min(1.0, max(0.0, $0)) }
    }

    private static func buildMuscleUsagesFromSession(
        _ session: Session,
        exerciseDb: [String: ExerciseMuscleInfo]
    ) -> [String: Double] {
        var usages: [String: Double] = [:]
        let allExercises = session.exercises + session.parts.flatMap { $0.exercises }

        for ex in allExercises {
            guard let info = resolveExercise(ex.exerciseDbId, name: ex.name, exerciseDb: exerciseDb) else { continue }
            for im in info.involvedMuscles {
                let roleW = ROLE_DRAIN_WEIGHT[im.role] ?? 0.0
                if roleW > 0.0 {
                    usages[im.muscle] = max(usages[im.muscle, default: 0.0], roleW)
                }
            }
        }

        let maxUsage = usages.values.max().flatMap { $0 > 0.0 ? $0 : nil } ?? 1.0
        return usages.mapValues { $0 / maxUsage }
    }

    // MARK: - Interference Calculation

    private static func computeInterference(
        sessionAId: String,
        sessionAName: String,
        sessionBId: String,
        sessionBName: String,
        sessionADate: String?,
        sessionBDate: String?,
        drainsA: [String: Double],
        usagesB: [String: Double],
        hoursApart: Double,
        isFromHistory: Bool
    ) -> SessionInterference? {
        var sharedMuscles: [SharedMuscleInterference] = []
        var weightedInterferenceSum = 0.0
        var totalWeight = 0.0

        let commonMuscles = Set(drainsA.keys).intersection(usagesB.keys)

        for muscle in commonMuscles {
            guard let drainA = drainsA[muscle] else { continue }
            guard let usageB = usagesB[muscle] else { continue }

            if drainA < MIN_RESIDUAL_THRESHOLD || usageB < MIN_USAGE_THRESHOLD { continue }

            let halfLife = MUSCLE_HALF_LIFE[muscle] ?? 48.0
            let residualFatigue = drainA * exp(-log(2.0) / halfLife * hoursApart)

            if residualFatigue < 0.03 { continue }

            let recoveryDeficit = residualFatigue * usageB
            let muscleWeight = usageB

            sharedMuscles.append(
                SharedMuscleInterference(
                    muscleName: muscle,
                    drainFromSessionA: residualFatigue,
                    usageInSessionB: usageB,
                    recoveryDeficit: recoveryDeficit
                )
            )

            weightedInterferenceSum += recoveryDeficit * muscleWeight
            totalWeight += muscleWeight
        }

        if sharedMuscles.isEmpty { return nil }
        if totalWeight <= 0.0 { return nil }

        let interferenceRatio = min(1.0, max(0.0, weightedInterferenceSum / totalWeight))
        let interferencePercent = min(100, max(1, Int(interferenceRatio * 100)))

        let recommendation = buildRecommendation(pct: interferencePercent, muscles: sharedMuscles)

        return SessionInterference(
            sessionAId: sessionAId,
            sessionAName: sessionAName,
            sessionBId: sessionBId,
            sessionBName: sessionBName,
            sessionADate: sessionADate,
            sessionBDate: sessionBDate,
            interferencePercent: interferencePercent,
            sharedMuscles: sharedMuscles.sorted { $0.recoveryDeficit > $1.recoveryDeficit },
            recommendation: recommendation,
            isFromHistory: isFromHistory,
            hoursApart: hoursApart
        )
    }

    private static func buildRecommendation(pct: Int, muscles: [SharedMuscleInterference]) -> String {
        let topMuscle = muscles.max { $0.recoveryDeficit < $1.recoveryDeficit }?.muscleName ?? "músculos"
        switch pct {
        case 70...:
            return "Alta interferencia en \(topMuscle). Considera mover un día de separación o cambiar ejercicios compuestos por aislamiento."
        case 45..<70:
            return "Interferencia moderada en \(topMuscle). Reduce el volumen o la intensidad en los ejercicios que involucren este grupo."
        case 25..<45:
            return "Interferencia leve en \(topMuscle). El rendimiento puede verse ligeramente afectado. Considera calentar bien ese músculo."
        default:
            return "Interferencia mínima. El split está bien organizado para estos dos días."
        }
    }

    // MARK: - Helpers

    private static func resolveExercise(
        _ dbId: String?,
        name: String,
        exerciseDb: [String: ExerciseMuscleInfo]
    ) -> ExerciseMuscleInfo? {
        guard let dbId = dbId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !dbId.isEmpty else {
            return nil
        }
        return exerciseDb[dbId]
    }
}
