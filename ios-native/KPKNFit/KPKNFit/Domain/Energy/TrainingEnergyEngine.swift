import Foundation

enum TrainingEnergyEngine {
    private static let activeKcalFactor = 0.008
    private static let epocRawFactor = 0.012
    private static let epocHardSetRatioCoef = 0.18
    private static let epocAvgTtcCoef = 0.07
    private static let epocBaseRatio = 0.10

    private static let bodyweightExercisePatterns: [String: Double] = [
        "pullup": 0.65, "chinup": 0.65, "dominada": 0.65, "dominadas": 0.65,
        "pull-up": 0.65, "chin-up": 0.65,
        "dip": 0.65, "dips": 0.65, "fondo": 0.65, "fondos": 0.65,
        "pushup": 0.65, "push-up": 0.65, "push up": 0.65,
        "flexion": 0.65, "flexiones": 0.65, "lagartija": 0.65,
        "sentadilla": 0.35, "squat": 0.35,
        "zancada": 0.35, "lunge": 0.35, "lunges": 0.35,
        "step-up": 0.35, "step up": 0.35,
        "peso muerto": 0.20, "deadlift": 0.20,
        "press banca": 0.08, "bench press": 0.08,
        "remo": 0.08, "row": 0.08,
        "press militar": 0.08, "overhead press": 0.08, "ohp": 0.08,
    ]

    private static func bodyweightParticipation(_ exerciseName: String, equipment: String?) -> Double {
        let lower = exerciseName.lowercased().trimmingCharacters(in: .whitespaces)
        return bodyweightExercisePatterns.first { lower.contains($0.key) }?.value ?? 0.0
    }

    private static func resolveDbInfo(exerciseDbId: String?, exerciseId: String?, exerciseName: String) -> ExerciseMuscleInfo? {
        guard let id = (exerciseDbId ?? exerciseId)?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return nil
        }
        return catalogExerciseIndex()[id]
    }

    private static func estimateActiveSetKcal(effectiveLoadKg: Double, effectiveReps: Double, efc: Double, rpeMultiplier: Double, densityMultiplier: Double) -> Double {
        let efcFactor = min(max(efc / 2.5, 0.35), 2.4)
        return effectiveLoadKg * effectiveReps * efcFactor * rpeMultiplier * densityMultiplier * activeKcalFactor
    }

    private static func classifyHardSet(rpe: Double, isFailure: Bool, isFailedSet: Bool) -> Bool {
        rpe >= 9.5 || isFailure || isFailedSet
    }

    private static func rpeForPlannedSet(_ set: ExerciseSet) -> Double {
        if set.isFailure || set.intensityMode == .FAILURE { return 10.8 }
        if let rpe = set.targetRPE { return rpe }
        if let rir = set.targetRIR { return 10.0 - Double(rir) }
        return 7.0
    }

    private static func buildEnergyConfidence(hasBodyWeight: Bool, metricsExerciseRatio: Double, realRpeSetRatio: Double) -> EnergyConfidence {
        var score = 0
        if hasBodyWeight { score += 1 }
        if metricsExerciseRatio >= 0.60 { score += 1 }
        if realRpeSetRatio >= 0.60 { score += 1 }
        if score >= 3 && metricsExerciseRatio >= 0.80 && realRpeSetRatio >= 0.80 { return .HIGH }
        if score >= 2 { return .MEDIUM }
        return .LOW
    }

    private static func calorieRange(_ mid: Int, _ confidence: EnergyConfidence) -> CalorieRange {
        let rangePct: Double
        switch confidence {
        case .HIGH: rangePct = 0.15
        case .MEDIUM: rangePct = 0.25
        case .LOW: rangePct = 0.35
        }
        return CalorieRange(low: Int(Double(mid) * (1.0 - rangePct)), mid: mid, high: Int(Double(mid) * (1.0 + rangePct)))
    }

    private static func computeSessionEnergyInternally(plannedSets: [(String, Exercise, ExerciseSet)]?, completedExercises: [CompletedExercise]?, userBodyWeightKg: Double?, postExerciseFeedback: [String: PostExerciseFeedback]?, isPlanned: Bool) -> SessionEnergySummary {
        var setScores: [SetEnergyScore] = []
        var contributions: [ExerciseEnergyContribution] = []
        var notes: [String] = []
        let userWeight = userBodyWeightKg
        if userWeight == nil { notes.append("Peso corporal no disponible — confianza baja") }

        var exerciseCount = 0
        var exercisesWithMetrics = 0
        var totalSetCount = 0
        var setsWithRealRpe = 0
        var rawTotalActive = 0.0
        var rawTotalEpoc = 0.0

        if let completedExercises = completedExercises {
            for compEx in completedExercises {
                let dbInfo = resolveDbInfo(exerciseDbId: compEx.exerciseDbId, exerciseId: compEx.exerciseId, exerciseName: compEx.exerciseName)
                let efc = dbInfo?.efc ?? 2.5
                let ttc = dbInfo?.ttc ?? 1.5
                if dbInfo?.efc != nil && dbInfo?.cnc != nil && dbInfo?.ssc != nil { exercisesWithMetrics += 1 }
                exerciseCount += 1
                let bodyweightPart = bodyweightParticipation(compEx.exerciseName, equipment: dbInfo?.equipment)
                let densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId: compEx.supersetId, restTime: compEx.supersetRestBetween ?? compEx.restTime,
                    supersetExerciseCount: compEx.supersetExerciseCount, supersetRounds: compEx.supersetRounds,
                    supersetRestAfter: compEx.supersetRestAfter
                )
                let effSets = compEx.sets.filter { !$0.skipped }
                let postFeedback = postExerciseFeedback?.values.first { $0.exerciseId == compEx.exerciseId || $0.canonicalExerciseId == compEx.canonicalExerciseId }
                var exerciseActiveKcal = 0.0
                var exerciseHardCount = 0
                var exerciseSetScores: [SetEnergyScore] = []

                for set in effSets {
                    let effectiveRpe = AugeFatigueEngine.getEffectiveRPE(set: set)
                    totalSetCount += 1
                    let hasRealRpe = set.rpe != nil || set.rir != nil || set.actualIntensityValue != nil || set.isFailure || set.isFailedSet
                    if hasRealRpe { setsWithRealRpe += 1 }
                    if effectiveRpe < 5.0 { continue }
                    let rpeMult = AugeFatigueEngine.calculateRpeMultiplier(rpe: effectiveRpe)
                    let effectiveLoad = set.homologatedResultV3?.augeEquivalentLoad ?? set.weight
                    let effectiveReps = set.effectiveRepEquivalent()
                    let loadForKcal = effectiveLoad + (userWeight ?? 0.0) * bodyweightPart
                    let activeKcal = loadForKcal > 0.0 ? estimateActiveSetKcal(effectiveLoadKg: loadForKcal, effectiveReps: effectiveReps, efc: efc, rpeMultiplier: rpeMult, densityMultiplier: densityMult) : 0.0
                    let isHard = classifyHardSet(rpe: effectiveRpe, isFailure: set.isFailure, isFailedSet: set.isFailedSet)
                    if isHard { exerciseHardCount += 1 }
                    let score = SetEnergyScore(activeKcal: activeKcal, rpe: effectiveRpe, ttc: ttc, isHard: isHard)
                    exerciseSetScores.append(score)
                    setScores.append(score)
                    exerciseActiveKcal += activeKcal
                }

                let plannedTotalSets = compEx.sets.count
                let exerciseEpocRaw = exerciseSetScores.reduce(0.0) { $0 + $1.activeKcal * min(max($1.ttc / 3.0, 0.2), 2.0) * AugeFatigueEngine.calculateRpeMultiplier(rpe: $1.rpe) * epocRawFactor }
                let hardSetRatio = exerciseSetScores.isEmpty ? 0.0 : Double(exerciseHardCount) / Double(exerciseSetScores.count)
                let avgTtcNorm = min(max(ttc / 5.0, 0.0), 1.0)
                let epocMaxRatio = min(max(epocBaseRatio + epocHardSetRatioCoef * hardSetRatio + epocAvgTtcCoef * avgTtcNorm, 0.12), 0.35)
                let techniqueRecoveryMultiplier: Double
                if let postFeedback = postFeedback {
                    techniqueRecoveryMultiplier = AugeFatigueEngine.calculateTechniquePenalty(
                        technicalQuality: min(max(postFeedback.technicalQuality, 1), 5),
                        effortSignal: exerciseSetScores.map { $0.rpe }.reduce(0, +) / Double(max(exerciseSetScores.count, 1))
                    )
                } else { techniqueRecoveryMultiplier = 1.0 }
                let exerciseEpoc = min(exerciseEpocRaw * techniqueRecoveryMultiplier, exerciseActiveKcal * epocMaxRatio)
                let exerciseTotal = Int(exerciseActiveKcal + exerciseEpoc)
                rawTotalActive += exerciseActiveKcal
                rawTotalEpoc += exerciseEpoc
                contributions.append(ExerciseEnergyContribution(
                    exerciseId: compEx.exerciseId, exerciseDbId: compEx.exerciseDbId ?? compEx.exerciseId,
                    exerciseName: compEx.exerciseName, activeKcal: Int(exerciseActiveKcal),
                    epocKcal: Int(exerciseEpoc), totalKcal: exerciseTotal,
                    percentageOfSession: 0.0, completedSets: effSets.count, totalSets: plannedTotalSets
                ))
            }
        } else if let plannedSets = plannedSets {
            for (exerciseName, exercise, plannedSet) in plannedSets {
                let dbInfo = resolveDbInfo(exerciseDbId: exercise.exerciseDbId, exerciseId: exercise.exerciseId, exerciseName: exerciseName)
                let efc = dbInfo?.efc ?? 2.5
                let ttc = dbInfo?.ttc ?? 1.5
                if dbInfo?.efc != nil && dbInfo?.cnc != nil && dbInfo?.ssc != nil { exercisesWithMetrics += 1 }
                exerciseCount += 1
                let bodyweightPart = bodyweightParticipation(exerciseName, equipment: dbInfo?.equipment)
                let densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId: exercise.supersetGroupRefOrLegacyId(), restTime: exercise.restTime ?? 90
                )
                let plannedRpe = rpeForPlannedSet(plannedSet)
                let hasRealRpe = plannedSet.targetRPE != nil || plannedSet.targetRIR != nil || plannedSet.isFailure || plannedSet.intensityMode == .FAILURE
                if hasRealRpe { setsWithRealRpe += 1 }
                totalSetCount += 1
                if plannedRpe >= 5.0 {
                    let rpeMult = AugeFatigueEngine.calculateRpeMultiplier(rpe: plannedRpe)
                    let plannedWeight = plannedSet.weight ?? 0.0
                    let plannedReps = Double(plannedSet.targetReps ?? 0)
                    if plannedSet.weight == nil { notes.append("Falta peso planificado para \"\(exerciseName)\" — se estimó con carga base") }
                    let loadForKcal = plannedWeight + (userWeight ?? 0.0) * bodyweightPart
                    let activeKcal = (loadForKcal > 0.0 && plannedRpe >= 5.0) ? estimateActiveSetKcal(effectiveLoadKg: loadForKcal, effectiveReps: plannedReps, efc: efc, rpeMultiplier: rpeMult, densityMultiplier: densityMult) : 0.0
                    let isHard = classifyHardSet(rpe: plannedRpe, isFailure: plannedSet.isFailure || plannedSet.intensityMode == .FAILURE, isFailedSet: false)
                    let score = SetEnergyScore(activeKcal: activeKcal, rpe: plannedRpe, ttc: ttc, isHard: isHard)
                    setScores.append(score)
                    let hardRatio = setScores.isEmpty ? 0.0 : Double(setScores.filter { $0.isHard }.count) / Double(setScores.count)
                    let avgTtc = setScores.isEmpty ? ttc : setScores.map { $0.ttc }.reduce(0, +) / Double(setScores.count)
                    let avgTtcNorm = min(max(avgTtc / 5.0, 0.0), 1.0)
                    let epocMaxRatio = min(max(epocBaseRatio + epocHardSetRatioCoef * hardRatio + epocAvgTtcCoef * avgTtcNorm, 0.12), 0.35)
                    let epocRaw = score.activeKcal * min(max(score.ttc / 3.0, 0.2), 2.0) * rpeMult * epocRawFactor
                    let exerciseEpoc = min(epocRaw, activeKcal * epocMaxRatio)
                    let exerciseTotal = Int(activeKcal + exerciseEpoc)
                    rawTotalActive += activeKcal
                    rawTotalEpoc += exerciseEpoc
                    contributions.append(ExerciseEnergyContribution(
                        exerciseId: exercise.id, exerciseDbId: exercise.exerciseDbId,
                        exerciseName: exerciseName, activeKcal: Int(activeKcal),
                        epocKcal: Int(exerciseEpoc), totalKcal: exerciseTotal,
                        percentageOfSession: 0.0, completedSets: 1, totalSets: 1
                    ))
                }
            }
        }

        let totalActive = Int(rawTotalActive)
        let totalEpoc = Int(rawTotalEpoc)
        let totalMid = totalActive + totalEpoc
        let metricsExerciseRatio = exerciseCount > 0 ? Double(exercisesWithMetrics) / Double(exerciseCount) : 0.0
        let realRpeSetRatio = totalSetCount > 0 ? Double(setsWithRealRpe) / Double(totalSetCount) : 0.0
        let confidence = buildEnergyConfidence(hasBodyWeight: userWeight != nil, metricsExerciseRatio: metricsExerciseRatio, realRpeSetRatio: realRpeSetRatio)
        let totalContributions = contributions.reduce(0) { $0 + $1.totalKcal }
        let updatedContributions = totalContributions > 0 ? contributions.sorted { $0.totalKcal > $1.totalKcal }.map { c in
            ExerciseEnergyContribution(
                exerciseId: c.exerciseId,
                exerciseDbId: c.exerciseDbId,
                exerciseName: c.exerciseName,
                activeKcal: c.activeKcal,
                epocKcal: c.epocKcal,
                totalKcal: c.totalKcal,
                percentageOfSession: Double(c.totalKcal) / Double(totalContributions) * 100.0,
                completedSets: c.completedSets,
                totalSets: c.totalSets
            )
        } : contributions
        let maxTotal = setScores.count
        let completedTotal: Int
        if let completedExercises = completedExercises {
            completedTotal = completedExercises.reduce(0) { $0 + $1.sets.filter { !$0.skipped }.count }
        } else { completedTotal = setScores.count }

        return SessionEnergySummary(
            activeKcal: calorieRange(totalActive, confidence),
            epocKcal: calorieRange(totalEpoc, confidence),
            totalKcal: calorieRange(totalMid, confidence),
            projectedTotalKcal: (completedTotal < maxTotal && totalMid > 0) ? Int(Double(totalMid) / Double(max(completedTotal, 1)) * Double(maxTotal)) : nil,
            confidence: confidence,
            source: isPlanned ? .PLANNED : .LIVE,
            exerciseContributions: updatedContributions,
            notes: notes
        )
    }

    static func estimatePlannedSession(_ session: Session, settings: Settings = Settings()) -> SessionEnergySummary {
        let exercises = session.exercises + session.parts.flatMap { $0.exercises }
        let plannedSets = exercises.flatMap { ex in ex.sets.map { set in (ex.name, ex, set) } }
        let bodyWeight = settings.userVitals.weight
        var result = computeSessionEnergyInternally(plannedSets: plannedSets, completedExercises: nil, userBodyWeightKg: bodyWeight, postExerciseFeedback: nil, isPlanned: true)
        result = SessionEnergySummary(activeKcal: result.activeKcal, epocKcal: result.epocKcal, totalKcal: result.totalKcal, projectedTotalKcal: result.projectedTotalKcal, confidence: result.confidence, source: .PLANNED, exerciseContributions: result.exerciseContributions, notes: result.notes)
        return result
    }

    static func estimateLiveSession(_ completedExercises: [CompletedExercise], settings: Settings = Settings()) -> SessionEnergySummary {
        let bodyWeight = settings.userVitals.weight
        var result = computeSessionEnergyInternally(plannedSets: nil, completedExercises: completedExercises, userBodyWeightKg: bodyWeight, postExerciseFeedback: nil, isPlanned: false)
        result = SessionEnergySummary(activeKcal: result.activeKcal, epocKcal: result.epocKcal, totalKcal: result.totalKcal, projectedTotalKcal: result.projectedTotalKcal, confidence: result.confidence, source: .LIVE, exerciseContributions: result.exerciseContributions, notes: result.notes)
        return result
    }

    static func estimateCompletedSession(_ completedExercises: [CompletedExercise], settings: Settings = Settings(), postExerciseFeedback: [String: PostExerciseFeedback] = [:]) -> SessionEnergySummary {
        let bodyWeight = settings.userVitals.weight
        var result = computeSessionEnergyInternally(plannedSets: nil, completedExercises: completedExercises, userBodyWeightKg: bodyWeight, postExerciseFeedback: postExerciseFeedback, isPlanned: false)
        result = SessionEnergySummary(activeKcal: result.activeKcal, epocKcal: result.epocKcal, totalKcal: result.totalKcal, projectedTotalKcal: result.projectedTotalKcal, confidence: result.confidence, source: .FINAL, exerciseContributions: result.exerciseContributions, notes: result.notes)
        return result
    }

    static func calculateDailyEnergyBalance(consumedKcal: Int, trainingBurnKcal: Int, targetKcal: Int) -> DailyEnergyBalance {
        let netKcal = consumedKcal - trainingBurnKcal
        let deltaFromTarget = netKcal - targetKcal
        let status: DailyEnergyStatus
        if deltaFromTarget < -150 { status = .DEFICIT }
        else if deltaFromTarget > 150 { status = .SURPLUS }
        else { status = .MAINTENANCE }
        return DailyEnergyBalance(consumedKcal: consumedKcal, trainingBurnKcal: trainingBurnKcal, netKcal: netKcal, targetKcal: targetKcal, deltaFromTarget: deltaFromTarget, status: status)
    }
}

private struct SetEnergyScore {
    let activeKcal: Double
    let rpe: Double
    let ttc: Double
    let isHard: Bool
}
