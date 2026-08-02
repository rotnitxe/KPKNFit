import Foundation

enum ExerciseReadinessEngine {
    static let adjustmentThreshold = 75

    private static let minReductionMultiplier = 0.20
    private static let maxReductionMultiplier = 0.80
    private static let maxReductionCap = 0.30
    private static let baseSets = 3

    static func calculatePerExerciseReadiness(
        exercise: Exercise,
        augeBatteries: GlobalBatteries,
        perMuscle: [String: MuscleRecoveryStatus],
        averageErm: Double? = nil,
        unresolvedDiscomfortIds: [String] = [],
        articularBatteries: [ArticularBattery: ArticularBatteryState] = [:]
    ) -> ExerciseReadiness? {
        let dbInfo: ExerciseMuscleInfo?
        if let dbId = exercise.exerciseDbId?.lowercased() {
            dbInfo = catalogExerciseIndex()[dbId]
        } else if let exId = exercise.exerciseId?.lowercased() {
            dbInfo = catalogExerciseIndex()[exId]
        } else {
            dbInfo = nil
        }
        guard let dbInfo = dbInfo else { return nil }

        let involvedMuscles = dbInfo.involvedMuscles
            .filter { resolveMuscleVolumeContribution(involvement: $0) > 0.0 }
            .filter { $0.role != .NEUTRALIZER }

        let muscleIds = involvedMuscles.compactMap { getAugeMuscleDisplayId(rawMuscle: $0.muscle, rawEmphasis: $0.emphasis) }
        guard !muscleIds.isEmpty else { return nil }

        var scoreSum = 0.0
        var weightSum = 0.0
        for involved in involvedMuscles {
            let id = getAugeMuscleDisplayId(rawMuscle: involved.muscle, rawEmphasis: involved.emphasis)
            guard let recovery = perMuscle[id]?.recoveryScore else { continue }
            let roleWeight = FATIGUE_ROLE_MULTIPLIERS[involved.role] ?? 0
            scoreSum += Double(recovery) * roleWeight
            weightSum += roleWeight
        }
        let muscularComponent = weightSum > 0 ? min(max(Int(scoreSum / weightSum), 0), 100) : 100

        var artScoreSum = 0.0
        var artWeightSum = 0.0
        for involved in involvedMuscles {
            let id = getAugeMuscleDisplayId(rawMuscle: involved.muscle, rawEmphasis: involved.emphasis)
            let relatedArtic = AugeTtcEngine.MUSCLE_TO_ARTICULAR[id] ?? []
            guard !relatedArtic.isEmpty else { continue }
            let roleWeight = FATIGUE_ROLE_MULTIPLIERS[involved.role] ?? 0
            let scores = relatedArtic.compactMap { articularBatteries[$0]?.recoveryScore }
            guard !scores.isEmpty else { continue }
            let avgScore = Double(scores.reduce(0, +)) / Double(scores.count)
            artScoreSum += avgScore * roleWeight
            artWeightSum += roleWeight
        }
        let articularComponent = artWeightSum > 0 ? min(max(Int(artScoreSum / artWeightSum), 0), 100) : 100
        let structuralComponent = min(muscularComponent, articularComponent)

        let relatedArticular = involvedMuscles
            .compactMap { getAugeMuscleDisplayId(rawMuscle: $0.muscle, rawEmphasis: $0.emphasis) }
            .flatMap { AugeTtcEngine.MUSCLE_TO_ARTICULAR[$0] ?? [] }
            .distinct()

        let cnsComponent = min(max(augeBatteries.cnc, 0), 100)
        let spinalComponent = min(max(augeBatteries.spinal, 0), 100)

        let exerciseTtc = AugeTtcEngine.calculateTTC(dbInfo.name, dbInfo.equipment)
        let articularDemand = min(max(exerciseTtc / 5.0, 0.0), 1.0)

        let cnc = dbInfo.cnc ?? 2.5
        let axialLoad = dbInfo.axialLoadFactor ?? 0.0
        let neuralDemand = cnc / 5.0
        let spinalDemand = min(max(axialLoad, 0.0), 1.0)
        let muscularDemand = 1.0

        let totalDemand = muscularDemand + neuralDemand + spinalDemand + articularDemand
        let wMusc = muscularDemand / totalDemand
        let wCns = neuralDemand / totalDemand
        let wSpine = spinalDemand / totalDemand
        let wArtic = articularDemand / totalDemand

        let baseReadiness = min(max(
            Double(structuralComponent) * wMusc +
            Double(cnsComponent) * wCns +
            Double(spinalComponent) * wSpine +
            Double(articularComponent) * wArtic, 0.0), 100.0)

        let setsCount = exercise.sets.count
        let setsExtra = max(0, setsCount - baseSets)
        let setsPenaltyFactor = max(0.85, 1.0 - Double(setsExtra) * 0.01)

        let maxRpe = exercise.sets.compactMap { $0.targetRPE }.max() ?? 7.0
        let intensityPenaltyFactor: Double
        if maxRpe >= 10.0 { intensityPenaltyFactor = 0.97 }
        else if maxRpe >= 9.5 { intensityPenaltyFactor = 0.98 }
        else { intensityPenaltyFactor = 1.0 }

        let ermPenaltyFactor: Double
        if let averageErm = averageErm, averageErm > 0.0 {
            let maxPlannedWeight = exercise.sets.compactMap { set -> Double? in
                if let w = set.weight { return w }
                if let pct = set.targetPercentageRM, averageErm > 0 { return averageErm * pct }
                return nil
            }.max() ?? 0.0
            ermPenaltyFactor = (maxPlannedWeight > 0.0 && maxPlannedWeight / averageErm > 0.85) ? 0.95 : 1.0
        } else {
            ermPenaltyFactor = 1.0
        }

        let discomfortPenaltyFactor = computeDiscomfortPenaltyFactor(involvedMuscles: involvedMuscles, unresolvedDiscomfortIds: unresolvedDiscomfortIds)

        let ttcHardCap: Int
        if exerciseTtc >= 3.0 && articularComponent < 40 {
            let scale = Double(articularComponent) / 40.0
            ttcHardCap = Int(30 + scale * 30)
        } else {
            ttcHardCap = 100
        }

        let finalScore = min(max(Int(baseReadiness * setsPenaltyFactor * intensityPenaltyFactor * ermPenaltyFactor * discomfortPenaltyFactor), 0), ttcHardCap)

        let componentScores: [String: Int] = [
            "MUSCULAR": muscularComponent, "CNS": cnsComponent,
            "SPINAL": spinalComponent, "ARTICULAR": articularComponent
        ]
        let minEntry = componentScores.min(by: { $0.value < $1.value })
        let limitingFactor = minEntry?.key
        let limitingDetail: String?
        switch limitingFactor {
        case "MUSCULAR":
            limitingDetail = involvedMuscles.compactMap { involved -> (String, Int)? in
                let id = getAugeMuscleDisplayId(rawMuscle: involved.muscle, rawEmphasis: involved.emphasis)
                guard let recovery = perMuscle[id]?.recoveryScore else { return nil }
                return (id, recovery)
            }.min(by: { $0.1 < $1.1 })?.0
        case "ARTICULAR":
            limitingDetail = relatedArticular.compactMap { ab -> (String, Int)? in
                guard let score = articularBatteries[ab]?.recoveryScore else { return nil }
                return (AugeTtcEngine.articularLabel(ab), score)
            }.min(by: { $0.1 < $1.1 })?.0
        case "SPINAL": limitingDetail = "Columna (Espinal)"
        case "CNS": limitingDetail = "Sistema Nervioso Central (Energía)"
        default: limitingDetail = nil
        }

        return ExerciseReadiness(
            exerciseId: exercise.id, exerciseName: exercise.name,
            overallScore: finalScore, muscularComponent: muscularComponent,
            cnsComponent: cnsComponent, spinalComponent: spinalComponent,
            articularComponent: articularComponent, structuralComponent: structuralComponent,
            relatedArticular: relatedArticular, muscularWeight: wMusc,
            cnsWeight: wCns, spinalWeight: wSpine, articularWeight: wArtic,
            setsPenaltyFactor: setsPenaltyFactor, intensityPenaltyFactor: intensityPenaltyFactor,
            ermProximityFactor: ermPenaltyFactor, patternId: dbInfo.force,
            involvedMuscleIds: muscleIds, limitingFactor: limitingFactor, limitingDetail: limitingDetail
        )
    }

    static func calculatePerMovementPatternReadiness(
        exercises: [Exercise],
        exerciseReadinessMap: [String: ExerciseReadiness],
        perMuscle: [String: MuscleRecoveryStatus]
    ) -> [MovementPatternReadiness] {
        var byPattern: [String: [(ExerciseReadiness, Exercise)]] = [:]
        for exercise in exercises {
            guard let readiness = exerciseReadinessMap[exercise.id] else { continue }
            let patternId = readiness.patternId ?? "Otro"
            byPattern[patternId, default: []].append((readiness, exercise))
        }
        return byPattern.map { (patternId, pairs) in
            let exercisesInPattern = pairs.map { $0.1 }
            let readinesses = pairs.map { $0.0 }
            let totalSets = exercisesInPattern.reduce(0) { $0 + $1.sets.count }
            let weightedScore: Double
            if totalSets > 0 {
                weightedScore = zip(readinesses, exercisesInPattern).reduce(0.0) { $0 + Double($1.0.overallScore * $1.1.sets.count) } / Double(totalSets)
            } else {
                weightedScore = readinesses.map { Double($0.overallScore) }.reduce(0, +) / Double(max(readinesses.count, 1))
            }
            let uniqueMuscles = Array(Set(readinesses.flatMap { $0.involvedMuscleIds }))
            let avgMuscleRecov = uniqueMuscles.compactMap { perMuscle[$0]?.recoveryScore }
            let avg = avgMuscleRecov.isEmpty ? 0 : avgMuscleRecov.reduce(0, +) / avgMuscleRecov.count
            return MovementPatternReadiness(
                patternId: patternId, patternLabel: patternLabelFor(patternId),
                overallScore: min(max(Int(weightedScore), 0), 100),
                exerciseCount: exercisesInPattern.count, totalSets: totalSets,
                contributingMuscles: uniqueMuscles, averageMuscleRecovery: avg
            )
        }.sorted { $0.overallScore > $1.overallScore }
    }

    static func calculateSetAdjustment(
        plannedWeight: Double,
        exerciseReadiness: ExerciseReadiness,
        severitySlider: Double,
        averageErm: Double? = nil,
        loadMode: LoadModeV2 = .LOAD,
        bodyWeight: Double? = nil
    ) -> SetAdjustmentSuggestion {
        let score = exerciseReadiness.overallScore
        if score >= adjustmentThreshold {
            return SetAdjustmentSuggestion(
                exerciseId: exerciseReadiness.exerciseId, setIndex: 0,
                currentPlannedWeight: plannedWeight, readinessScore: score,
                severityFactor: severitySlider, reductionPercent: 0.0,
                suggestedWeight: plannedWeight, averageErm: averageErm,
                reason: "No requiere ajuste", suggestedLoadMode: loadMode
            )
        }
        let gap = Double(adjustmentThreshold - score) / 100.0
        let reductionFactor = minReductionMultiplier + severitySlider * (maxReductionMultiplier - minReductionMultiplier)
        let reductionPercent = min(max(gap * reductionFactor, 0.0), maxReductionCap)

        let suggestedWeight: Double
        let suggestedMode: LoadModeV2
        let reason: String
        switch loadMode {
        case .LASTRE:
            let rawWeight = plannedWeight * (1.0 - reductionPercent)
            if rawWeight < 2.5 {
                suggestedWeight = 0.0; suggestedMode = .BODYWEIGHT
                reason = "Readiness \(score)% · Pasar a Peso Corporal (quitar lastre)"
            } else {
                let fw = Double(Int(rawWeight / 2.5) * 2)
                suggestedWeight = fw; suggestedMode = .LASTRE
                reason = "Readiness \(score)% · Reducir lastre a \(Int(fw))kg (−\(Int(reductionPercent * 100))%)"
            }
        case .BODYWEIGHT:
            let pc = bodyWeight ?? 75.0
            let suggestedAssistance = pc * reductionPercent
            let fw = max(Double(Int(suggestedAssistance / 2.5) * 2), 2.5)
            suggestedWeight = fw; suggestedMode = .ASSISTED
            reason = "Readiness \(score)% · Añadir \(Int(fw))kg de asistencia por fatiga"
        case .ASSISTED:
            let pc = bodyWeight ?? 75.0
            let netWeight = max(pc - plannedWeight, 10.0)
            let adjustedNet = netWeight * (1.0 - reductionPercent)
            let suggestedAssistance = min(max(pc - adjustedNet, 0.0), pc)
            let fw = max(Double(Int(suggestedAssistance / 2.5) * 2), plannedWeight + 2.5)
            suggestedWeight = fw; suggestedMode = .ASSISTED
            reason = "Readiness \(score)% · Aumentar asistencia a \(Int(fw))kg (+\(Int(fw - plannedWeight))kg)"
        case .LOAD:
            let rawWeight = plannedWeight * (1.0 - reductionPercent)
            let fw = max(Double(Int(rawWeight / 2.5) * 2), 0.0)
            suggestedWeight = fw; suggestedMode = .LOAD
            reason = "Readiness \(score)% · Reducir peso a \(fw)kg (−\(Int(reductionPercent * 100))%)"
        @unknown default:
            suggestedWeight = plannedWeight; suggestedMode = loadMode
            reason = "Readiness \(score)% · Reducir carga"
        }

        return SetAdjustmentSuggestion(
            exerciseId: exerciseReadiness.exerciseId, setIndex: 0,
            currentPlannedWeight: plannedWeight, readinessScore: score,
            severityFactor: severitySlider, reductionPercent: reductionPercent,
            suggestedWeight: suggestedWeight, averageErm: averageErm,
            reason: reason, suggestedLoadMode: suggestedMode
        )
    }

    static func computeDiscomfortPenaltyFactor(involvedMuscles: [InvolvedMuscle], unresolvedDiscomfortIds: [String]) -> Double {
        guard !unresolvedDiscomfortIds.isEmpty else { return 1.0 }
        let exerciseArticulars = Set(involvedMuscles.flatMap { AugeTtcEngine.MUSCLE_TO_ARTICULAR[$0.muscle] ?? [] })
        guard !exerciseArticulars.isEmpty else { return 1.0 }
        let overlappingCount = unresolvedDiscomfortIds.filter { id in
            DISCOMFORT_CATALOG_BY_ID[id]?.relatedArticular.contains(where: { exerciseArticulars.contains($0) }) ?? false
        }.count
        if overlappingCount == 0 { return 1.0 }
        if overlappingCount == 1 { return 0.95 }
        return 0.90
    }

    static func readinessLabel(_ score: Int) -> String {
        if score >= 85 { return "Óptimo" }
        if score >= 75 { return "Bueno" }
        if score >= 50 { return "Moderado" }
        if score >= 35 { return "Bajo" }
        return "Crítico"
    }

    private static func patternLabelFor(_ force: String) -> String {
        switch force.lowercased() {
        case "empuje": return "Empuje"
        case "tirón", "tiron": return "Tirón"
        case "sentadilla": return "Sentadilla"
        case "bisagra": return "Bisagra"
        case "anti-extensión", "antiextension": return "Anti-Extensión"
        case "flexión", "flexion": return "Flexión"
        case "extensión", "extension": return "Extensión"
        default: return force.prefix(1).uppercased() + force.dropFirst()
        }
    }
}
