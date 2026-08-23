import CryptoKit
import Foundation

/// iOS adapter for the versioned finish snapshot.  It deliberately mirrors
/// Android's local attribution contract: catalog roles are resolved once,
/// direct/indirect stress is persisted, and no global battery average is used
/// to manufacture a per-muscle value.
public enum MuscularSessionImpactEngine {
    public static let modelVersion = "muscle-impact-v2"

    public static func evaluate(
        completedExercises: [CompletedExercise],
        completionInstantIso: String,
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        settings: Settings = Settings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        capacitiesAtCompletion: [String: Double] = [:]
    ) -> MuscularSessionImpactV2 {
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        let defaultCapacity = max(120.0, min(3500.0, AugeFatigueEngine.getAthleteCapacity(settings: settings)))
        var stress: [String: Double] = [:]
        var direct: [String: Double] = [:]
        var indirect: [String: Double] = [:]
        var involved: Set<String> = []
        var accumulatedSets = 0

        for exercise in completedExercises {
            let lookup = [exercise.exerciseDbId, exercise.exerciseId, exercise.exerciseName]
                .compactMap { $0?.lowercased() }
            let info = lookup.compactMap { exerciseDb[$0] }.first
            let muscles = info?.involvedMuscles ?? []
            guard !muscles.isEmpty else { continue }
            involved.formUnion(muscles.map { $0.muscle })
            let metrics = AugeFatigueEngine.getDynamicAugeMetrics(
                exerciseName: exercise.exerciseName,
                equipment: info?.equipment,
                dbInfo: info
            ) ?? AugeMetrics()
            let density = AugeFatigueEngine.getDensityMultiplierForExercise(
                supersetId: exercise.supersetId,
                restTime: exercise.supersetRestBetween ?? exercise.restTime,
                supersetExerciseCount: exercise.supersetExerciseCount,
                supersetRounds: exercise.supersetRounds,
                supersetRestAfter: exercise.supersetRestAfter
            )

            for set in exercise.sets {
                guard AugeFatigueEngine.isSetEffective(set: set) else { continue }
                accumulatedSets += 1
                let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set: set,
                    metrics: metrics,
                    tanks: tanks,
                    accumulatedSets: accumulatedSets,
                    restTime: exercise.restTime,
                    densityMultiplier: density,
                    cnsMultiplier: adaptiveCache.cnsDrainMultiplier,
                    spinalMultiplier: adaptiveCache.spinalDrainMultiplier,
                    muscleMultiplier: 1.0
                )
                let diminishing = 1.0 / (1.0 + 0.65 * Double(max(0, accumulatedSets - 1)) / 10.0)
                let baseStress = drain.muscularDrainPct * 10.0 * diminishing
                for muscle in muscles {
                    let roleWeight: Double
                    switch muscle.role {
                    case .PRIMARY: roleWeight = 1.0
                    case .SECONDARY: roleWeight = 0.55
                    case .STABILIZER: roleWeight = 0.30
                    case .NEUTRALIZER: roleWeight = 0.20
                    }
                    let activation = min(max(muscle.volumeContribution ?? roleWeight, 0.0), 1.0)
                    let localStress = baseStress * roleWeight * activation
                    stress[muscle.muscle, default: 0.0] += localStress
                    if muscle.role == .PRIMARY {
                        direct[muscle.muscle, default: 0.0] += localStress
                    } else {
                        indirect[muscle.muscle, default: 0.0] += localStress
                    }
                }
            }
        }

        let perMuscle = Dictionary(uniqueKeysWithValues: stress.keys.sorted().map { muscle in
            let units = stress[muscle] ?? 0.0
            let capacity = max(120.0, capacitiesAtCompletion[muscle] ?? defaultCapacity)
            let drain = (100.0 * (1.0 - exp(-units / capacity))).clamped(to: 0...100)
            return (
                muscle,
                MuscleSessionImpactV2(
                    stressUnits: units,
                    capacityAtCompletion: capacity,
                    immediateDrainPct: drain,
                    directStressUnits: direct[muscle] ?? 0.0,
                    indirectStressUnits: indirect[muscle] ?? 0.0
                )
            )
        })
        let globalDrain = AugeFatigueEngine.calculateCompletedSessionDrain(
            completedExercises: completedExercises,
            exerciseDb: exerciseDb,
            settings: settings,
            adaptiveCache: adaptiveCache
        ).muscular
        let context = [modelVersion, settings.athleteType.rawValue, "2"].joined(separator: "|")
        return MuscularSessionImpactV2(
            completionInstantIso: completionInstantIso,
            globalMuscularDrain: Double(globalDrain),
            perMuscle: perMuscle,
            involvedVolumeMuscles: involved.sorted(),
            setInputHash: completedSetInputHash(completedExercises),
            contextHash: sha256(context)
        )
    }

    public static func completedSetInputHash(_ exercises: [CompletedExercise]) -> String {
        let value = exercises.map { exercise in
            let sets = exercise.sets.map { set in
                [set.id, String(set.weight), String(set.reps), String(set.rpe ?? -1), String(set.rir ?? -1), String(set.isFailure)].joined(separator: ";")
            }.joined(separator: ",")
            return [exercise.exerciseId, exercise.exerciseDbId ?? "", sets].joined(separator: "#")
        }.joined(separator: "|")
        return sha256(value)
    }

    private static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
