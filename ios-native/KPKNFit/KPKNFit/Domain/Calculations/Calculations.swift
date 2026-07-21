import Foundation

func calculateBrzycki1RM(weight: Double, reps: Int, isAmrap: Bool = false) -> Double {
    guard weight > 0, reps > 0 else { return 0.0 }
    if reps == 1 { return weight }
    let effectiveReps = min(reps, 30)
    var e1rm = weight * (36.0 / (37 - Double(effectiveReps)))
    if isAmrap && reps > 3 { e1rm *= 1.025 }
    return (e1rm * 10.0).rounded() / 10.0
}

func calculateEpley1RM(weight: Double, reps: Int) -> Double {
    guard weight > 0, reps > 0 else { return 0.0 }
    if reps == 1 { return weight }
    let e1rm = weight * (1 + Double(reps) / 30.0)
    return (e1rm * 10.0).rounded() / 10.0
}

func calculateLander1RM(weight: Double, reps: Int) -> Double {
    guard weight > 0, reps > 0 else { return 0.0 }
    if reps == 1 { return weight }
    let denominator = 101.3 - 2.67123 * Double(reps)
    guard denominator > 0 else { return 0.0 }
    return (weight * 10000.0 / denominator).rounded() / 100.0
}

func calculateHybrid1RM(weight: Double, reps: Int, isAmrap: Bool = false) -> Double {
    guard weight > 0, reps > 0 else { return 0.0 }
    if reps == 1 { return weight }
    let r = min(reps, 50)
    let e1rm: Double
    if r <= 10 {
        e1rm = weight * (36.0 / (37 - Double(r)))
    } else if r <= 20 {
        e1rm = weight * (1 + Double(r) / 30.0)
    } else {
        e1rm = weight * (1 + 20.0 / 30) * pow(1 + (Double(r) - 20) / 80.0, 0.9)
    }
    let adjusted = isAmrap && reps > 3 ? e1rm * 1.025 : e1rm
    return (adjusted * 10.0).rounded() / 10.0
}

func calculateWeightFrom1RM(e1rm: Double, reps: Int) -> Double {
    guard reps > 0, e1rm > 0 else { return 0.0 }
    if reps == 1 { return e1rm }
    let r = min(reps, 50)
    let weight: Double
    if r <= 10 {
        weight = e1rm * ((37 - Double(r)) / 36.0)
    } else if r <= 20 {
        weight = e1rm / (1 + Double(r) / 30.0)
    } else {
        weight = e1rm / ((1 + 20.0 / 30) * pow(1 + (Double(r) - 20) / 80.0, 0.9))
    }
    return max(0.0, (weight * 10.0).rounded() / 10.0)
}

func calculateWeightFrom1RMAndIntensity(reference1RM: Double, set: ExerciseSet) -> Double? {
    guard reference1RM > 0 else { return nil }
    guard let reps = set.targetReps, reps > 0 else { return nil }

    let effectiveReps: Int
    switch set.intensityMode {
    case .FAILURE, .AMRAP, .SOLO_RM:
        effectiveReps = reps
    case .RIR:
        effectiveReps = reps + (set.targetRIR ?? 0)
    case .RPE:
        effectiveReps = reps + Int(10 - (set.targetRPE ?? 8.0))
    default:
        effectiveReps = reps + 2
    }
    guard effectiveReps > 0 else { return nil }
    let weight = calculateWeightFrom1RM(e1rm: reference1RM, reps: effectiveReps)
    return weight > 0 ? weight : nil
}

func calculateGeneralizedCapacity(load: Double, metric: Double) -> Double {
    guard load > 0, metric > 0 else { return 0.0 }
    if metric <= 1.0 { return load }
    let normalizedMetric = min(metric, 300.0)
    let capacity: Double
    if normalizedMetric <= 10.0 {
        capacity = load * (36.0 / (37.0 - normalizedMetric))
    } else if normalizedMetric <= 20.0 {
        capacity = load * (1.0 + normalizedMetric / 30.0)
    } else {
        capacity = load * (1 + 20.0 / 30.0) * pow(1 + (normalizedMetric - 20.0) / 80.0, 0.9)
    }
    return (capacity * 10.0).rounded() / 10.0
}

func calculateLoadFromGeneralizedCapacity(capacity: Double, metric: Double) -> Double {
    guard capacity > 0, metric > 0 else { return 0.0 }
    if metric <= 1.0 { return capacity }
    let normalizedMetric = min(metric, 300.0)
    let load: Double
    if normalizedMetric <= 10.0 {
        load = capacity * ((37.0 - normalizedMetric) / 36.0)
    } else if normalizedMetric <= 20.0 {
        load = capacity / (1.0 + normalizedMetric / 30.0)
    } else {
        load = capacity / ((1 + 20.0 / 30.0) * pow(1 + (normalizedMetric - 20.0) / 80.0, 0.9))
    }
    return (load * 10.0).rounded() / 10.0
}

func estimateRepsFromPercent1RM(_ percent: Double) -> Int {
    let boundedPercent = min(max(percent, 45.0), 100.0)
    return (1...30).min(by: { abs(estimatePercent1RM($0) - boundedPercent) < abs(estimatePercent1RM($1) - boundedPercent) }) ?? 1
}

private func roundSuggestedLoad(_ weight: Double) -> Double {
    (weight * 4.0).rounded() / 4.0
}

private func assistanceFactor(_ metric: Double) -> Double {
    guard metric > 0.0 else { return 0.0 }
    let normalizedMetric = min(metric, 300.0)
    if normalizedMetric <= 1.0 { return 1.0 }
    if normalizedMetric <= 10.0 { return 36.0 / (37.0 - normalizedMetric) }
    if normalizedMetric <= 20.0 { return 1.0 + normalizedMetric / 30.0 }
    return (1 + 20.0 / 30.0) * pow(1 + (normalizedMetric - 20.0) / 80.0, 0.9)
}

private func calculateAssistedLoadFromPr(prAssistance: Double, prMetric: Double, targetMetric: Double) -> Double {
    guard prAssistance > 0.0, prMetric > 0.0, targetMetric > 0.0 else { return 0.0 }
    let prFactor = assistanceFactor(prMetric)
    guard prFactor > 0.0 else { return 0.0 }
    let targetFactor = assistanceFactor(targetMetric)
    return prAssistance * (targetFactor / prFactor)
}

private func plannedMetricForMode(_ set: ExerciseSet, _ trainingMode: TrainingMode) -> Double? {
    switch trainingMode {
    case .TIME: return set.targetDuration.map(Double.init)
    case .SOLO_RPE: return nil
    default: return set.targetReps.map(Double.init)
    }
}

private func effectiveMetricForSuggestion(_ set: ExerciseSet, _ trainingMode: TrainingMode) -> Double? {
    if trainingMode == .RM {
        guard let percent = set.targetPercentageRM else { return nil }
        return Double(estimateRepsFromPercent1RM(percent))
    }
    guard let baseMetric = plannedMetricForMode(set, trainingMode) else { return nil }
    switch trainingMode {
    case .REPS:
        switch set.intensityMode {
        case .FAILURE, .AMRAP, .SOLO_RM: return baseMetric
        case .RIR: return baseMetric + Double(set.targetRIR ?? 0)
        case .RPE: return baseMetric + Double(Int(10 - (set.targetRPE ?? 8.0)))
        default: return baseMetric + 2.0
        }
    case .TIME, .DISTANCE, .CUSTOM:
        let multiplier: Double
        switch set.intensityMode {
        case .FAILURE, .AMRAP: multiplier = 1.0
        case .RIR: multiplier = 1.0 + Double(set.targetRIR ?? 0) * 0.05
        case .RPE: multiplier = 1.0 + max(10.0 - (set.targetRPE ?? 8.0), 0.0) * 0.05
        default: multiplier = 1.10
        }
        return max(baseMetric * multiplier, 1.0)
    case .SOLO_RPE, .RM: return nil
    case .AMRAP: return baseMetric
    @unknown default: return nil
    }
}

func resolveReferenceCapacity(_ exercise: Exercise) -> Double? {
    if let ref = exercise.reference1RM, ref > 0.0 { return ref }
    guard let pr = exercise.prFor1RM, pr.weight > 0, pr.reps > 0 else { return nil }
    switch exercise.trainingMode {
    case .REPS, .RM:
        return calculateHybrid1RM(weight: pr.weight, reps: pr.reps)
    case .TIME, .DISTANCE, .CUSTOM:
        return calculateGeneralizedCapacity(load: pr.weight, metric: Double(pr.reps))
    case .SOLO_RPE: return nil
    case .AMRAP: return calculateHybrid1RM(weight: pr.weight, reps: pr.reps)
    @unknown default: return nil
    }
}

func resolveReferenceCapacity(_ exercise: Exercise, _ history: [WorkoutLog]) -> Double? {
    if let cap = resolveReferenceCapacity(exercise) { return cap }
    let canonicalId = exercise.resolvedCanonicalExerciseId()
    return history.flatMap { $0.completedExercises }
        .filter { $0.resolvedCanonicalExerciseId() == canonicalId }
        .flatMap { $0.sets }
        .filter { !$0.isWarmup && $0.weight > 0.0 && $0.reps > 0 }
        .map { calculateHybrid1RM(weight: $0.weight, reps: $0.reps) }
        .max()
}

func calculateSuggestedLoad(_ exercise: Exercise, _ set: ExerciseSet) -> Double? {
    let loadMode = set.loadModeV2 ?? .LOAD
    if loadMode == .BODYWEIGHT { return 0.0 }
    if loadMode == .ASSISTED {
        guard let pr = exercise.prFor1RM, let targetMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) else { return nil }
        let suggested = calculateAssistedLoadFromPr(prAssistance: pr.weight, prMetric: Double(pr.reps), targetMetric: targetMetric)
        return suggested > 0.0 ? roundSuggestedLoad(suggested) : nil
    }
    guard let referenceCapacity = resolveReferenceCapacity(exercise) else { return nil }
    let suggested: Double?
    switch exercise.trainingMode {
    case .RM:
        let percent = min(max(set.targetPercentageRM ?? estimatePercent1RM(set.targetReps ?? 1), 40.0), 100.0)
        suggested = referenceCapacity * percent / 100.0
    case .SOLO_RPE: suggested = nil
    case .REPS:
        guard let effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode).map(Int.init) else { return nil }
        suggested = calculateWeightFrom1RM(e1rm: referenceCapacity, reps: effectiveMetric)
    case .TIME, .DISTANCE, .CUSTOM:
        guard let effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) else { return nil }
        suggested = calculateLoadFromGeneralizedCapacity(capacity: referenceCapacity, metric: effectiveMetric)
    case .AMRAP:
        guard let effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode).map(Int.init) else { return nil }
        suggested = calculateWeightFrom1RM(e1rm: referenceCapacity, reps: effectiveMetric)
    @unknown default: suggested = nil
    }
    guard let result = suggested, result > 0.0 else { return nil }
    return roundSuggestedLoad(result)
}

func calculateSuggestedLoad(_ exercise: Exercise, _ set: ExerciseSet, _ history: [WorkoutLog]) -> Double? {
    let loadMode = set.loadModeV2 ?? .LOAD
    if loadMode == .BODYWEIGHT { return 0.0 }
    if loadMode == .ASSISTED {
        guard let pr = exercise.prFor1RM, let targetMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) else { return nil }
        let suggested = calculateAssistedLoadFromPr(prAssistance: pr.weight, prMetric: Double(pr.reps), targetMetric: targetMetric)
        return suggested > 0.0 ? roundSuggestedLoad(suggested) : nil
    }
    guard let referenceCapacity = resolveReferenceCapacity(exercise, history) else { return nil }
    let suggested: Double?
    switch exercise.trainingMode {
    case .RM:
        let percent = min(max(set.targetPercentageRM ?? estimatePercent1RM(set.targetReps ?? 1), 40.0), 100.0)
        suggested = referenceCapacity * percent / 100.0
    case .SOLO_RPE: suggested = nil
    case .REPS:
        guard let effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode).map(Int.init) else { return nil }
        suggested = calculateWeightFrom1RM(e1rm: referenceCapacity, reps: effectiveMetric)
    case .TIME, .DISTANCE, .CUSTOM:
        guard let effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) else { return nil }
        suggested = calculateLoadFromGeneralizedCapacity(capacity: referenceCapacity, metric: effectiveMetric)
    case .AMRAP:
        guard let effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode).map(Int.init) else { return nil }
        suggested = calculateWeightFrom1RM(e1rm: referenceCapacity, reps: effectiveMetric)
    @unknown default: suggested = nil
    }
    guard let result = suggested, result > 0.0 else { return nil }
    return roundSuggestedLoad(result)
}

func calculateEstimatedMetric(_ exercise: Exercise, _ set: ExerciseSet) -> Double? {
    switch exercise.trainingMode {
    case .RM:
        guard let percent = set.targetPercentageRM else { return nil }
        return Double(estimateRepsFromPercent1RM(percent))
    case .TIME: return set.targetDuration.map(Double.init)
    case .DISTANCE, .CUSTOM, .REPS: return set.targetReps.map(Double.init)
    case .SOLO_RPE: return nil
    case .AMRAP: return set.targetReps.map(Double.init)
    @unknown default: return nil
    }
}

func rpeToRir(_ rpe: Double) -> Int { max(0, Int(10 - max(0.0, rpe))) }
func rirToRpe(_ rir: Int) -> Double { max(0.0, 10 - Double(max(0, rir))) }

struct FfmiResult {
    let ffmi: Double
    let normalizedFfmi: Double
    let interpretation: String
    let leanBodyMass: Double
}

func calculateFFMI(heightCm: Double, weightKg: Double, bodyFatPercent: Double) -> FfmiResult? {
    guard heightCm > 0, weightKg > 0, bodyFatPercent >= 0 else { return nil }
    let heightM = heightCm / 100.0
    let leanBodyMass = weightKg * (1 - bodyFatPercent / 100.0)
    let ffmi = leanBodyMass / (heightM * heightM)
    let normalizedFfmi = ffmi + 6.1 * (1.8 - heightM)
    let interpretation: String
    if normalizedFfmi >= 26 { interpretation = "Superior/Elite" }
    else if normalizedFfmi >= 22 { interpretation = "Excelente" }
    else if normalizedFfmi >= 20 { interpretation = "Promedio" }
    else { interpretation = "Novato" }
    return FfmiResult(
        ffmi: (ffmi * 10.0).rounded() / 10.0,
        normalizedFfmi: (normalizedFfmi * 10.0).rounded() / 10.0,
        interpretation: interpretation,
        leanBodyMass: (leanBodyMass * 10.0).rounded() / 10.0
    )
}

enum IpfEquipment { case classic, equipped }
enum IpfLift { case total, bench, squat, deadlift }

func calculateIPFGLPoints(totalLifted: Double, bodyWeight: Double, gender: String, equipment: IpfEquipment, weightUnit: String = "kg") -> Double {
    guard totalLifted > 0, bodyWeight > 0 else { return 0.0 }
    let isFemale = gender == "female" || gender == "transfemale"
    var bwKg = weightUnit == "lbs" ? bodyWeight * 0.45359237 : bodyWeight
    let totalKg = weightUnit == "lbs" ? totalLifted * 0.45359237 : totalLifted
    bwKg = isFemale ? max(bwKg, 35.0) : max(bwKg, 40.0)

    struct Coeffs { let a: Double; let b: Double; let c: Double }
    let coeffs: Coeffs
    switch (isFemale, equipment) {
    case (false, .equipped): coeffs = Coeffs(a: 1236.25115, b: 1449.21864, c: 0.01644)
    case (false, .classic):  coeffs = Coeffs(a: 1199.72839, b: 1025.18162, c: 0.00921)
    case (true, .equipped):  coeffs = Coeffs(a: 758.63878, b: 949.31382, c: 0.02435)
    case (true, .classic):   coeffs = Coeffs(a: 610.32796, b: 1045.59282, c: 0.03048)
    }
    let denominator = coeffs.a - coeffs.b * exp(-coeffs.c * bwKg)
    guard denominator != 0.0 else { return 0.0 }
    let coefficient = 100.0 / denominator
    return (coefficient * totalKg * 100.0).rounded() / 100.0
}

func roundWeight(_ weight: Double, unit: String = "kg") -> Double {
    let increment = unit == "lbs" ? 2.5 : 1.25
    let result = (weight / increment).rounded() * increment
    return result < 0.0 ? 0.0 : result
}

func estimatePercent1RM(_ repsToFailure: Int) -> Double {
    guard repsToFailure > 0 else { return 100.0 }
    let percent: Double
    if repsToFailure <= 1 { percent = 100.0 }
    else if repsToFailure <= 3 { percent = 100.0 - (Double(repsToFailure) - 1) * 4.0 }
    else if repsToFailure <= 6 { percent = 92.0 - (Double(repsToFailure) - 3) * 3.0 }
    else if repsToFailure <= 10 { percent = 83.0 - (Double(repsToFailure) - 6) * 2.0 }
    else { percent = max(45.0, 75.0 - (Double(repsToFailure) - 10) * 1.3) }
    return (percent * 10).rounded() / 10.0
}

func getEffectiveRepsForRM(_ set: ExerciseSet) -> Int? {
    guard let reps = set.targetReps else { return nil }
    let result: Int
    switch set.intensityMode {
    case .FAILURE, .AMRAP, .SOLO_RM: result = reps
    case .RIR: result = reps + (set.targetRIR ?? 0)
    case .RPE: result = reps + Int(10 - (set.targetRPE ?? 8.0))
    default: result = reps + 2
    }
    return max(result, 1)
}

func suggestRestSeconds(setCount: Int, averageRpe: Double = 8.0, averagePercent1RM: Double? = nil) -> Int {
    let base: Int
    if let avgPct = averagePercent1RM, avgPct >= 88 { base = 240 }
    else if let avgPct = averagePercent1RM, avgPct >= 80 { base = 180 }
    else if averageRpe >= 9.5 { base = 210 }
    else if averageRpe >= 8.5 { base = 150 }
    else if averageRpe >= 7.5 { base = 120 }
    else { base = 90 }
    let densityPenalty: Int
    if setCount >= 8 { densityPenalty = 30 }
    else if setCount >= 5 { densityPenalty = 15 }
    else { densityPenalty = 0 }
    return min(max(base + densityPenalty, 45), 300)
}

func estimateSessionDurationMinutes(totalSets: Int, averageRestSeconds: Int) -> Int {
    let workSeconds = totalSets * 55
    let restSeconds = totalSets * averageRestSeconds
    let transitions = (totalSets / 3) * 20
    return max(Int(Double(workSeconds + restSeconds + transitions) / 60.0), 5)
}

struct SessionTimeBreakdown {
    let setupSeconds: Int
    let executionSeconds: Int
    let restSeconds: Int
    let warmupSeconds: Int
    let totalSeconds: Int
    let exerciseCount: Int
    let totalSetCount: Int

    var totalMinutes: Int { max(Int(Double(totalSeconds) / 60.0), 1) }
    var setupMinutes: Int { Int(Double(setupSeconds) / 60.0) }
    var executionMinutes: Int { Int(Double(executionSeconds) / 60.0) }
    var restMinutes: Int { Int(Double(restSeconds) / 60.0) }
    var warmupMinutes: Int { Int(Double(warmupSeconds) / 60.0) }
}

func calculateSessionTimeBreakdown(
    exercises: [Exercise],
    supersetGroups: [SupersetGroup],
    sessionWarmup: [WarmupExercise] = [],
    averageSetupSeconds: Int = 60,
    averageWorkSeconds: Int = 45
) -> SessionTimeBreakdown {
    var setupSec = 0
    var executionSec = 0
    var restSec = 0
    var warmupSec = 0

    for warmupExercise in sessionWarmup {
        let exerciseDuration: Int
        if let dur = warmupExercise.duration, dur > 0 {
            exerciseDuration = dur
        } else {
            let sets = max(warmupExercise.sets ?? 1, 1)
            let repsEstimate = Int(warmupExercise.reps?.filter(\.isNumber) ?? "") ?? 10
            exerciseDuration = sets * repsEstimate * 4
        }
        warmupSec += exerciseDuration + 15
    }

    var supersetGroupsProcessed = Set<String>()

    for exercise in exercises {
        setupSec += averageSetupSeconds

        for approxSet in exercise.warmupSets {
            warmupSec += 30
            warmupSec += (approxSet.restBetween ?? 45)
        }

        for mobility in exercise.mobilitySeries {
            let mobilityDuration: Int
            if let dur = mobility.durationSeconds, dur > 0 {
                mobilityDuration = dur * max(mobility.sets, 1)
            } else {
                mobilityDuration = 30 * max(mobility.sets, 1)
            }
            warmupSec += mobilityDuration
        }

        let sets = exercise.sets.isEmpty ? (0..<3).map { ExerciseSet(id: "placeholder_\($0)") } : exercise.sets

        for set in sets {
            let setExecSec: Int
            switch exercise.trainingMode {
            case .TIME: setExecSec = set.targetDuration ?? averageWorkSeconds
            default: setExecSec = averageWorkSeconds
            }
            executionSec += setExecSec

            let drops = set.plannedIntensityTechniques.filter { $0.type == .DROP_SET }
            var dropCount = drops.reduce(0) { $0 + ($1.params["weightPcts"]?.split(separator: ",").count ?? 3) }
            if set.isDropSet && set.dropSets.isEmpty { dropCount = max(dropCount, 3) }
            executionSec += dropCount * 6

            let rpTechniques = set.plannedIntensityTechniques.filter { $0.type == .REST_PAUSE }
            for technique in rpTechniques {
                let miniSetCount = Int(technique.params["count"] ?? "") ?? 3
                let pauseSec = Int(technique.params["pauseSeconds"] ?? "") ?? 10
                let miniReps = Int(technique.params["reps"] ?? "") ?? 3
                executionSec += miniSetCount * miniReps * 2
                restSec += miniSetCount * pauseSec
            }
            if set.isRestPause && rpTechniques.isEmpty {
                let rpCount = max(set.restPauses.count, 3)
                executionSec += rpCount * 3 * 2
                restSec += rpCount * 10
            }
        }

        let exerciseRestSec = exercise.restTime ?? 90
        let supersetRef = exercise.supersetGroupRefOrLegacyId()

        if let supersetRef = supersetRef {
            if let group = supersetGroups.first(where: { $0.id == supersetRef }), !supersetGroupsProcessed.contains(supersetRef) {
                let rounds = max(sets.count, 1)
                restSec += group.restBetweenExercises * (group.exerciseOrder.count - 1) * rounds
                restSec += group.restAfterSuperset * rounds
                supersetGroupsProcessed.insert(supersetRef)
            }
        } else {
            restSec += exerciseRestSec * sets.count
        }
    }

    let total = setupSec + executionSec + restSec + warmupSec
    return SessionTimeBreakdown(
        setupSeconds: setupSec, executionSeconds: executionSec,
        restSeconds: restSec, warmupSeconds: warmupSec,
        totalSeconds: total, exerciseCount: exercises.count,
        totalSetCount: exercises.reduce(0) { $0 + max($1.sets.count, 1) }
    )
}
