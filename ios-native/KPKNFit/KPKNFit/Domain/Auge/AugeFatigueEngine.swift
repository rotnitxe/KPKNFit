import Foundation

// Redundant AugeMetrics structure removed (defined in AugeModels.swift)


enum AugeFatigueEngine {

    private static let maxTechniqueFatigueMultiplier = 4.0

    // Recalibración 2026-08-17: floors reducidos ~48% — paridad con Android.
    private static let athleteCapacity: [AthleteType: Double] = [
        .ENTHUSIAST: 260.0,
        .HYBRID: 340.0,
        .CALISTHENICS: 310.0,
        .BODYBUILDER: 520.0,
        .POWERBUILDER: 570.0,
        .POWERLIFTER: 625.0,
        .WEIGHTLIFTER: 520.0,
        .ZERCHER_LIFTER: 340.0,
    ]

    static func getAthleteCapacity(settings: AppSettings) -> Double {
        athleteCapacity[settings.athleteType] ?? 500.0
    }

    private static func applySoftCap(drain: Double, accumulated: Double, cap: Double) -> Double {
        if drain <= 0.0 || cap <= 0.0 { return 0.0 }
        let p = min(max(accumulated / cap, 0.0), 1.0)
        let damping: Double
        if p <= 0.40 {
            damping = 1.0 - p * 0.5
        } else if p <= 0.70 {
            damping = 0.80 * exp(-3.2 * (p - 0.40))
        } else {
            damping = 0.30 * exp(-5.5 * (p - 0.70))
        }
        return max(drain * damping, 0.0)
    }

    private static func normalizeBias(profile: PredictionBiasProfile) -> (Double, Double, Double) {
        let confidence = Double(min(max(profile.sampleCount, 0), 30)) / 30.0
        return (
            min(max(profile.cnsBias * confidence, -15.0), 15.0),
            min(max(profile.muscularBias * confidence, -15.0), 15.0),
            min(max(profile.spinalBias * confidence, -15.0), 15.0)
        )
    }

    static func calculatePersonalizedBatteryTanks(settings: AppSettings) -> BatteryTanks {
        let baseMuscular = 300.0
        let baseCns = 250.0
        let baseSpinal = 4000.0
        let levelMult: Double
        switch settings.athleteType {
        case .POWERLIFTER, .WEIGHTLIFTER:
            levelMult = 1.2
        case .BODYBUILDER, .POWERBUILDER:
            levelMult = 1.1
        case .HYBRID, .CALISTHENICS, .ZERCHER_LIFTER:
            levelMult = 1.0
        case .ENTHUSIAST:
            levelMult = 0.9
        }
        let cnsMult: Double
        let muscMult: Double
        let spineMult: Double
        switch settings.athleteType {
        case .POWERLIFTER, .WEIGHTLIFTER:
            cnsMult = 1.3; spineMult = 1.4; muscMult = 0.9
        case .BODYBUILDER, .POWERBUILDER:
            cnsMult = 0.9; spineMult = 0.9; muscMult = 1.3
        default:
            cnsMult = 1.15; spineMult = 1.15; muscMult = 1.15
        }
        return BatteryTanks(
            cns: baseCns * levelMult * cnsMult,
            muscular: baseMuscular * levelMult * muscMult,
            spinal: baseSpinal * levelMult * spineMult
        )
    }

    private static func deriveAugeMetricsFromDb(dbInfo: ExerciseMuscleInfo?) -> AugeMetrics? {
        guard let dbInfo = dbInfo else { return nil }
        guard let efc = dbInfo.efc else { return nil }
        guard let cnc = dbInfo.cnc else { return nil }
        guard let ssc = dbInfo.ssc else { return nil }
        return AugeMetrics(
            efc: min(max(efc, 1.0), 5.0),
            ssc: min(max(ssc, 0.0), 2.0),
            cnc: min(max(cnc, 1.0), 5.0)
        )
    }

    private static func deriveAugeMetricsHeuristic(exerciseName: String, equipment: String?) -> AugeMetrics {
        let lower = exerciseName.lowercased().trimmingCharacters(in: .whitespaces)
        let hasEquipment = equipment?.lowercased() ?? ""

        let (baseEfc, baseCnc, baseSsc): (Double, Double, Double)
        if lower.contains("deadlift") || lower.contains("peso muerto") || lower.contains("rumano") {
            baseEfc = 4.0; baseCnc = 4.0; baseSsc = 1.6
        } else if lower.contains("squat") || lower.contains("sentadilla") || lower.contains("hack squat") {
            baseEfc = 3.8; baseCnc = 3.8; baseSsc = 1.2
        } else if lower.contains("bench press") || lower.contains("press banca") || lower.contains("press de banca") {
            baseEfc = 3.2; baseCnc = 3.5; baseSsc = 0.8
        } else if lower.contains("overhead press") || lower.contains("military press") || lower.contains("press militar") {
            baseEfc = 3.0; baseCnc = 3.5; baseSsc = 1.0
        } else if lower.contains("pull up") || lower.contains("pull-up") || lower.contains("dominada") || lower.contains("chin up") || lower.contains("chin-up") {
            baseEfc = 3.0; baseCnc = 3.0; baseSsc = 0.3
        } else if (lower.contains("row") || lower.contains("remo")) && !lower.contains("rumano") {
            baseEfc = 3.0; baseCnc = 3.2; baseSsc = 0.7
        } else if lower.contains("hip thrust") || lower.contains("empuje de cadera") {
            baseEfc = 3.5; baseCnc = 3.0; baseSsc = 0.8
        } else if lower.contains("clean") || lower.contains("snatch") || lower.contains("arranque") || lower.contains("cargada") || lower.contains("envion") || lower.contains("envión") {
            baseEfc = 4.5; baseCnc = 4.5; baseSsc = 1.4
        } else if lower.contains("lunge") || lower.contains("zancada") || lower.contains("bulgarian") || lower.contains("búlgaro") || lower.contains("bulgara") || lower.contains("búlgara") {
            baseEfc = 2.8; baseCnc = 2.5; baseSsc = 0.5
        } else if lower.contains("curl") || lower.contains("bicep") || lower.contains("bíceps") {
            baseEfc = 1.5; baseCnc = 1.5; baseSsc = 0.1
        } else if (lower.contains("extension") && (lower.contains("tricep") || lower.contains("tríceps"))) {
            baseEfc = 1.5; baseCnc = 1.5; baseSsc = 0.1
        } else if lower.contains("lateral") || (lower.contains("deltoides") && lower.contains("lateral")) {
            baseEfc = 1.5; baseCnc = 1.5; baseSsc = 0.1
        } else if lower.contains("pushdown") || lower.contains("pressdown") || lower.contains("frances") || lower.contains("francés") {
            baseEfc = 1.5; baseCnc = 1.5; baseSsc = 0.1
        } else if lower.contains("leg press") || lower.contains("prensa") {
            baseEfc = 3.0; baseCnc = 2.5; baseSsc = 0.8
        } else if lower.contains("leg curl") || lower.contains("femoral") || lower.contains("curl de pierna") {
            baseEfc = 2.0; baseCnc = 1.8; baseSsc = 0.2
        } else if lower.contains("leg extension") || lower.contains("extension de cuadriceps") || lower.contains("extensión de cuádriceps") {
            baseEfc = 2.2; baseCnc = 2.0; baseSsc = 0.1
        } else if lower.contains("calf") || lower.contains("pantorrilla") || lower.contains("gemelo") {
            baseEfc = 1.5; baseCnc = 1.2; baseSsc = 0.1
        } else if lower.contains("fly") || lower.contains("apertura") || lower.contains("pec deck") || lower.contains("crossover") {
            baseEfc = 2.0; baseCnc = 1.8; baseSsc = 0.2
        } else if lower.contains("dip") || lower.contains("fondo") {
            baseEfc = 2.8; baseCnc = 3.0; baseSsc = 0.6
        } else if lower.contains("good morning") || lower.contains("buenos dias") || lower.contains("buenos días") {
            baseEfc = 2.8; baseCnc = 2.5; baseSsc = 1.2
        } else if lower.contains("hyperextension") || lower.contains("hiperextension") || lower.contains("hiperextensión") {
            baseEfc = 2.0; baseCnc = 1.5; baseSsc = 0.8
        } else if lower.contains("carry") || (lower.contains("cargada") && lower.contains("granjero")) {
            baseEfc = 2.0; baseCnc = 2.5; baseSsc = 1.0
        } else {
            baseEfc = 2.5; baseCnc = 2.5; baseSsc = 0.5
        }

        var efc = baseEfc
        var cnc = baseCnc
        var ssc = baseSsc

        if hasEquipment.contains("mancuerna") || hasEquipment.contains("dumbbell") || hasEquipment.contains("dumbbells") {
            cnc += 0.2; ssc -= 0.2
        }
        if hasEquipment.contains("smith") {
            cnc -= 0.5; efc -= 0.2
        }
        if hasEquipment.contains("cable") || hasEquipment.contains("polea") {
            cnc -= 0.3; efc += 0.2
        }
        if hasEquipment.contains("barra") {
            ssc += 0.2
        }

        if exerciseName.contains("pausa") || exerciseName.contains("pause") {
            cnc += 0.3; efc += 0.5
        }
        if exerciseName.contains("deficit") || exerciseName.contains("déficit") {
            ssc += 0.2; efc += 0.3
        }
        if exerciseName.contains("parcial") || exerciseName.contains("partial") {
            efc -= 0.2; ssc += 0.2
        }

        return AugeMetrics(
            efc: min(max(efc, 1.0), 5.0),
            ssc: min(max(ssc, 0.0), 2.0),
            cnc: min(max(cnc, 1.0), 5.0)
        )
    }

    static func getDynamicAugeMetrics(
        exerciseName: String,
        equipment: String? = nil,
        dbInfo: ExerciseMuscleInfo? = nil
    ) -> AugeMetrics? {
        deriveAugeMetricsFromDb(dbInfo: dbInfo) ?? deriveAugeMetricsHeuristic(exerciseName: exerciseName, equipment: equipment)
    }

    static func getDynamicAugeMetrics(
        _ exerciseName: String,
        _ equipment: String? = nil,
        _ dbInfo: ExerciseMuscleInfo? = nil
    ) -> AugeMetrics? {
        getDynamicAugeMetrics(exerciseName: exerciseName, equipment: equipment, dbInfo: dbInfo)
    }

    static func getEffectiveRPE(set: CompletedSet) -> Double {
        var baseRpe = 7.0

        if set.actualIntensityMode == .FAILURE {
            baseRpe = 10.8
        } else if set.actualIntensityMode == .RPE, let value = set.actualIntensityValue {
            baseRpe = value
        } else if let rir = set.rir {
            baseRpe = Double(10 - rir)
        } else if set.actualIntensityMode == .RIR, let value = set.actualIntensityValue {
            baseRpe = 10.0 - value
        } else if let rpe = set.rpe {
            baseRpe = rpe
        }

        if set.isFailure { baseRpe = max(baseRpe, 11.2) }
        if set.isFailedSet { baseRpe = max(baseRpe, 10.2) }

        var techniqueBonus = 0.0
        if !set.dropSets.isEmpty { techniqueBonus += Double(set.dropSets.count) * 1.5 }
        if !set.restPauses.isEmpty { techniqueBonus += Double(set.restPauses.count) * 1.0 }
        if set.isPartial, let pReps = set.partialReps, pReps > 0 { techniqueBonus += 0.5 }
        if techniqueBonus > 0 && baseRpe < 10.0 { baseRpe = 10.0 }

        return min(max(baseRpe, 1.0), 12.0)
    }

    static func isSetEffective(set: CompletedSet) -> Bool {
        if set.skipped { return false }
        if set.isWarmup { return false }
        let hasTime = (set.timeSeconds ?? 0) > 0
        if set.reps <= 0 && !hasTime && set.weight <= 0.0 { return false }
        let rpe = getEffectiveRPE(set: set)
        return rpe >= 6.0
    }

    static func isSetEffective(_ set: CompletedSet) -> Bool {
        isSetEffective(set: set)
    }

    static func calculateRpeMultiplier(rpe: Double) -> Double {
        let bounded = min(max(rpe, 1.0), 12.0)
        let cappedLinear = min(bounded, 10.0)
        let base = 1.0 + pow(cappedLinear / 10.0, 4.2)
        if bounded <= 10.0 { return base }
        let overshoot = bounded - 10.0
        let failureBonus = (exp(overshoot * 0.5) - 1.0) * 0.3
        return min(base + failureBonus, 2.3)
    }

    static func getDensityMultiplierForExercise(
        supersetId: String?,
        restTime: Int,
        supersetExerciseCount: Int = 1,
        supersetRounds: Int? = nil,
        supersetRestAfter: Int? = nil
    ) -> Double {
        guard let sid = supersetId, !sid.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return 1.0 }
        let intraRestFactor: Double
        if restTime <= 45 {
            intraRestFactor = 1.30
        } else if restTime <= 75 {
            intraRestFactor = 1.24
        } else {
            intraRestFactor = 1.18
        }
        let exerciseCountFactor = 1.0 + (Double(max(supersetExerciseCount, 2) - 2) * 0.045)
        let roundFactor: Double
        if let rounds = supersetRounds, rounds > 0 {
            roundFactor = 1.0 + (Double(min(rounds - 1, 5)) * 0.018)
        } else {
            roundFactor = 1.0
        }
        let postRestFactor: Double
        if let restAfter = supersetRestAfter {
            if restAfter <= 60 {
                postRestFactor = 1.08
            } else if restAfter <= 120 {
                postRestFactor = 1.03
            } else if restAfter >= 240 {
                postRestFactor = 0.95
            } else {
                postRestFactor = 1.0
            }
        } else {
            postRestFactor = 1.0
        }
        return min(max(intraRestFactor * exerciseCountFactor * roundFactor * postRestFactor, 1.10), 1.45)
    }

    static func calculateMuscularProgressiveMultiplier(accumulatedSets: Int) -> Double {
        let sets = max(accumulatedSets, 1)
        if sets <= 3 {
            return 1.0 + Double(sets - 1) * 0.05
        } else if sets <= 6 {
            return 1.10 - Double(sets - 3) * 0.10
        } else {
            return max(0.3, 0.80 * exp(-Double(sets - 6) * 0.25))
        }
    }

    static func calculateSystemicProgressiveMultiplier(accumulatedSets: Int) -> Double {
        let progression = Double(max(accumulatedSets, 1) - 1)
        return min(max(1.0 + 0.055 * pow(progression, 1.55), 1.0), 2.6)
    }

    static func calculateMuscularTechniqueMultiplier(set: CompletedSet) -> Double {
        let debtPenalty = set.debt > 0.0 ? 1.0 + min(0.15, set.debt * 0.03) : 1.0
        let failurePenalty: Double
        if set.isFailure && set.isFailedSet {
            failurePenalty = 1.15
        } else if set.isFailure {
            failurePenalty = 1.10
        } else if set.isFailedSet {
            failurePenalty = 1.05
        } else {
            failurePenalty = 1.0
        }
        let dropPenalty = set.dropSets.isEmpty ? 1.0 : min(1.0 + 0.10 * Double(set.dropSets.count), 1.20)
        let restPausePenalty = set.restPauses.isEmpty ? 1.0 : min(1.0 + 0.08 * Double(set.restPauses.count), 1.15)
        let partialPenalty = (set.partialReps ?? 0) > 0 ? 1.03 : 1.0
        return debtPenalty * failurePenalty * dropPenalty * restPausePenalty * partialPenalty
    }

    static func calculateSystemicTechniqueMultiplier(set: CompletedSet) -> Double {
        let debtPenalty = set.debt > 0.0 ? 1.0 + min(0.35, set.debt * 0.06) : 1.0
        let failurePenalty: Double
        if set.isFailure && set.isFailedSet {
            failurePenalty = 1.28
        } else if set.isFailure {
            failurePenalty = 1.22
        } else if set.isFailedSet {
            failurePenalty = 1.12
        } else {
            failurePenalty = 1.0
        }
        let dropPenalty: Double
        if set.dropSets.isEmpty {
            dropPenalty = 1.0
        } else {
            dropPenalty = 1.0 + 0.20 * (pow(2.0, Double(set.dropSets.count)) - 1.0)
        }
        let restPausePenalty: Double
        if set.restPauses.isEmpty {
            restPausePenalty = 1.0
        } else {
            restPausePenalty = 1.0 + 0.22 * (pow(2.0, Double(set.restPauses.count)) - 1.0)
        }
        let partialPenalty = (set.partialReps ?? 0) > 0 ? 1.05 : 1.0
        return debtPenalty * failurePenalty * dropPenalty * restPausePenalty * partialPenalty
    }

    private static func estimateRelativeLoadRatio(
        set: CompletedSet,
        reps: Double,
        rpe: Double
    ) -> Double? {
        let homologatedLoad = set.homologatedResultV3?.augeEquivalentLoad ?? set.weight
        let estimatedRm = set.homologatedResultV3?.estimatedRm
        if homologatedLoad > 0.0, let rm = estimatedRm, rm > 0.0 {
            return min(max(homologatedLoad / rm, 0.30), 1.20)
        }
        if set.actualIntensityMode == .SOLO_RM { return 0.98 }
        if reps <= 1.5 && rpe >= 9.5 { return 0.95 }
        if reps <= 3.0 && rpe >= 9.0 { return 0.90 }
        if reps <= 5.0 && rpe >= 8.5 { return 0.85 }
        return nil
    }

    private static func calculateNearRmFatigueMultiplier(relativeLoadRatio: Double?) -> Double {
        guard let ratio = relativeLoadRatio else { return 1.0 }
        if ratio >= 0.98 { return 1.40 }
        if ratio >= 0.94 { return 1.30 }
        if ratio >= 0.90 { return 1.20 }
        if ratio >= 0.85 { return 1.10 }
        return 1.0
    }

    static func calculateTechniquePenalty(technicalQuality: Int, effortSignal: Double) -> Double {
        let quality = Double(min(max(technicalQuality, 1), 5))
        let boundedEffort = min(effortSignal, 12.0)
        return 1.0 + pow((5.0 - quality) / 4.0, 2.0) * pow(boundedEffort / 10.0, 3.0) * 0.5
    }

    static func calculateSetBatteryDrain(
        set: CompletedSet,
        metrics: AugeMetrics,
        tanks: BatteryTanks,
        accumulatedSets: Int = 0,
        restTime: Int = 90,
        densityMultiplier: Double = 1.0,
        cnsMultiplier: Double = 1.0,
        spinalMultiplier: Double = 1.0,
        muscleMultiplier: Double = 1.0
    ) -> SetDrain {
        if set.skipped { return SetDrain(cnsDrainPct: 0.0, muscularDrainPct: 0.0, spinalDrainPct: 0.0) }
        // Paridad Android: unilateral halves
        let sideScale: Double = set.side != nil ? 0.5 : 1.0
        let rpe = getEffectiveRPE(set: set)
        let baseReps: Double
        if (set.timeSeconds ?? 0) > 0 {
            baseReps = Double(max(set.timeSeconds ?? 0, 5)) / 5.0
        } else {
            baseReps = Double(max(set.reps, 1))
        }

        let dropReps = Double(set.dropSets.reduce(0) { $0 + $1.reps })
        let rpReps = Double(set.restPauses.reduce(0) { $0 + $1.reps })

        let repsMuscular = baseReps + 0.40 * dropReps + 0.60 * rpReps
        let repsCns = baseReps + 0.85 * dropReps + 0.90 * rpReps
        let repsSpinal = baseReps + 0.30 * dropReps + 0.80 * rpReps

        let repsFactorMuscular = pow(repsMuscular, 0.65)
        let repsFactorCns = pow(repsCns, 0.65)
        let repsFactorSpinal = pow(repsSpinal, 0.65)

        let rpeMult = calculateRpeMultiplier(rpe: rpe)

        let muscularProgressiveMult = calculateMuscularProgressiveMultiplier(accumulatedSets: accumulatedSets)
        let systemicProgressiveMult = calculateSystemicProgressiveMultiplier(accumulatedSets: accumulatedSets)

        let localRestMult: Double
        if restTime <= 30 {
            localRestMult = 0.85
        } else if restTime <= 45 {
            localRestMult = 0.90
        } else if restTime <= 75 {
            localRestMult = 0.95
        } else if restTime <= 120 {
            localRestMult = 1.00
        } else if restTime >= 240 {
            localRestMult = 1.10
        } else if restTime >= 180 {
            localRestMult = 1.05
        } else {
            localRestMult = 1.0
        }
        let systemRestMult: Double
        if restTime <= 30 {
            systemRestMult = 1.35
        } else if restTime <= 45 {
            systemRestMult = 1.25
        } else if restTime <= 75 {
            systemRestMult = 1.15
        } else if restTime <= 120 {
            systemRestMult = 1.05
        } else if restTime >= 240 {
            systemRestMult = 0.85
        } else if restTime >= 180 {
            systemRestMult = 0.90
        } else {
            systemRestMult = 1.0
        }
        let structureRestMult: Double
        if restTime <= 30 {
            structureRestMult = 1.25
        } else if restTime <= 60 {
            structureRestMult = 1.15
        } else if restTime <= 90 {
            structureRestMult = 1.08
        } else if restTime >= 240 {
            structureRestMult = 0.88
        } else if restTime >= 180 {
            structureRestMult = 0.92
        } else {
            structureRestMult = 1.0
        }

        let reps = baseReps
        let muscularBias: Double
        if reps >= 15.0 {
            muscularBias = 1.15
        } else if reps <= 4.0 {
            muscularBias = 0.78
        } else if reps >= 12.0 {
            muscularBias = 1.08
        } else {
            muscularBias = 1.0
        }
        let systemBias: Double
        if reps <= 4.0 {
            systemBias = 1.25
        } else if reps >= 12.0 {
            systemBias = 0.88
        } else {
            systemBias = 1.0
        }
        let structureBias: Double
        if reps <= 4.0 {
            structureBias = 1.22
        } else if reps >= 12.0 {
            structureBias = 0.85
        } else {
            structureBias = 1.0
        }
        let effectiveLoad = set.homologatedResultV3?.augeEquivalentLoad ?? set.weight
        let loadFactor: Double
        if effectiveLoad > 0.0 {
            loadFactor = 1.0 + log(1.0 + (effectiveLoad / 20.0)) * 0.25
        } else {
            loadFactor = 1.0
        }

        let muscularTechniqueFactor = calculateMuscularTechniqueMultiplier(set: set)
        let systemicTechniqueFactor = calculateSystemicTechniqueMultiplier(set: set)

        let nearRmRatio = estimateRelativeLoadRatio(set: set, reps: reps, rpe: rpe)
        let nearRmMult = calculateNearRmFatigueMultiplier(relativeLoadRatio: nearRmRatio)
        let density = min(max(densityMultiplier, 0.85), 1.45)

        let muscularDensityMult = density > 1.0 ? 1.0 + (density - 1.0) * 0.35 : 1.0
        let systemDensityMult = 1.0 + (density - 1.0) * 1.50
        let structureDensityMult = 1.0 + (density - 1.0) * 1.15

        let assistedCount = set.assistedReps ?? 0
        let assistedMultiplier = 1.0 + (Double(assistedCount) * 0.20)

        let rawMuscular =
            metrics.efc * repsFactorMuscular * rpeMult * muscularProgressiveMult * localRestMult * muscularBias *
            muscularTechniqueFactor * muscularDensityMult * (1.0 + (nearRmMult - 1.0) * 0.35) * 1.85 * assistedMultiplier * muscleMultiplier
        let rawCns =
            metrics.cnc * repsFactorCns * rpeMult * systemicProgressiveMult * systemRestMult * systemBias *
            systemicTechniqueFactor * systemDensityMult * nearRmMult * 1.15 * assistedMultiplier * cnsMultiplier
        let rawSpinal =
            metrics.ssc * repsFactorSpinal * rpeMult * systemicProgressiveMult * structureRestMult * structureBias * loadFactor *
            systemicTechniqueFactor * structureDensityMult * (1.0 + (nearRmMult - 1.0) * 1.20) * 5.2 * assistedMultiplier * spinalMultiplier

        // Paridad Android: caps 32/32/35 y halves unilaterales
        return SetDrain(
            cnsDrainPct: min(max(rawCns / tanks.cns * 100, 0.0), 32.0) * sideScale,
            muscularDrainPct: min(max(rawMuscular / tanks.muscular * 100, 0.0), 32.0) * sideScale,
            spinalDrainPct: min(max(rawSpinal / tanks.spinal * 100, 0.0), 35.0) * sideScale
        )
    }

    private static func scaleSpinalDrainToUi(rawSpinalSessionDrain: Double, tanks: BatteryTanks) -> Double {
        let capacity = max(70.0, tanks.spinal * 0.02)
        let rawPct = (rawSpinalSessionDrain / capacity) * 100.0
        let batteryDrop = 100.0 * (1.0 - exp(-rawPct / 70.0))
        return min(max(batteryDrop, 0.0), 100.0)
    }

    static func calculateCompletedSessionDrain(
        completedExercises: [CompletedExercise],
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        settings: AppSettings = AppSettings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache()
    ) -> PredictedDrain {
        let tanks = calculatePersonalizedBatteryTanks(settings: settings)
        let floor = AugeUtils.physiologicalFloor(settings: settings)
        let muscularCap = Double(max(100 - floor.muscular, 5))
        let cnsCap = Double(max(100 - floor.cns, 5))
        let spinalCap = Double(max(100 - floor.spinal, 5))

        var totalCns = 0.0
        var totalMuscular = 0.0
        var totalSpinal = 0.0
        var muscleVolumeMap: [String: Int] = [:]
        let conservationFactor = 0.85
        let decayK = 0.65
        var accumulatedDrain = 0.0

        for ex in completedExercises {
            let lookupId = (ex.exerciseDbId ?? ex.exerciseId)?.lowercased()
            let dbInfo = lookupId.flatMap { exerciseDb[$0] }
            guard let metrics = getDynamicAugeMetrics(exerciseName: ex.exerciseName, equipment: dbInfo?.equipment, dbInfo: dbInfo) else {
                print("[AugeFatigueEngine] Sin métricas de fatiga para '\(ex.exerciseName)' (id=\(lookupId ?? "nil")) — ejercicio omitido del drenaje")
                continue
            }
            let densityMult = getDensityMultiplierForExercise(
                supersetId: ex.supersetId,
                restTime: ex.supersetRestBetween ?? ex.restTime,
                supersetExerciseCount: ex.supersetExerciseCount,
                supersetRounds: ex.supersetRounds,
                supersetRestAfter: ex.supersetRestAfter
            )
            let primaryMuscle: String
            if let involved = dbInfo?.involvedMuscles.first(where: { $0.role == .PRIMARY }) {
                primaryMuscle = getAugeMuscleDisplayId(rawMuscle: involved.muscle, rawEmphasis: involved.emphasis)
            } else {
                primaryMuscle = "Core"
            }
            var accumulated = muscleVolumeMap[primaryMuscle] ?? 0
            let muscleKey = primaryMuscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
            let muscleMult = adaptiveCache.muscleDrainMultipliers[muscleKey] ?? 1.0

            for s in ex.sets {
                guard isSetEffective(set: s) else { continue }
                accumulated += 1
                let drain = calculateSetBatteryDrain(
                    set: s,
                    metrics: metrics,
                    tanks: tanks,
                    accumulatedSets: accumulated,
                    restTime: ex.restTime,
                    densityMultiplier: densityMult,
                    cnsMultiplier: adaptiveCache.cnsDrainMultiplier,
                    spinalMultiplier: adaptiveCache.spinalDrainMultiplier,
                    muscleMultiplier: muscleMult
                )
                let diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                let adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                let adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                let adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                totalMuscular += applySoftCap(drain: adjustedMuscular, accumulated: totalMuscular, cap: muscularCap)
                totalCns += applySoftCap(drain: adjustedCns, accumulated: totalCns, cap: cnsCap)
                totalSpinal += applySoftCap(drain: adjustedSpinal, accumulated: totalSpinal, cap: spinalCap)
                accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }

        let scaledSpinal = scaleSpinalDrainToUi(rawSpinalSessionDrain: totalSpinal, tanks: tanks)
        let raw = PredictedDrain(
            cns: Int(min(totalCns, cnsCap)),
            muscular: Int(min(totalMuscular, muscularCap)),
            spinal: Int(min(scaledSpinal, spinalCap))
        )
        let (cnsBias, muscularBias, spinalBias) = normalizeBias(profile: settings.augePredictionBias)
        return PredictedDrain(
            cns: Int(min(max(Double(raw.cns) + cnsBias, 0), cnsCap)),
            muscular: Int(min(max(Double(raw.muscular) + muscularBias, 0), muscularCap)),
            spinal: Int(min(max(Double(raw.spinal) + spinalBias, 0), spinalCap))
        )
    }

    static func calculateCompletedSessionStress(
        completedExercises: [CompletedExercise],
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        settings: AppSettings = AppSettings(),
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache()
    ) -> Double {
        let summary = calculateCompletedSessionDrain(
            completedExercises: completedExercises,
            exerciseDb: exerciseDb,
            settings: settings,
            adaptiveCache: adaptiveCache
        )
        return Double(summary.cns) * 0.45 + Double(summary.muscular) * 0.25 + Double(summary.spinal) * 0.30
    }

    @available(*, deprecated, message: "Use calculateAdjustedPredictedDrain instead")
    static func calculatePredictedSessionDrain(
        session: Session,
        exerciseDb: [String: ExerciseMuscleInfo],
        settings: AppSettings
    ) -> PredictedDrain {
        calculateAdjustedPredictedDrain(session: session, exerciseDb: exerciseDb, settings: settings)
    }

    static func calculateAdjustedPredictedDrain(
        session: Session,
        exerciseDb: [String: ExerciseMuscleInfo],
        settings: AppSettings,
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache()
    ) -> PredictedDrain {
        let tanks = calculatePersonalizedBatteryTanks(settings: settings)
        let conservationFactor = 0.85
        let decayK = 0.65
        let floor = AugeUtils.physiologicalFloor(settings: settings)
        let muscularCap = Double(max(100 - floor.muscular, 5))
        let cnsCap = Double(max(100 - floor.cns, 5))
        let spinalCap = Double(max(100 - floor.spinal, 5))
        var totalCns = 0.0
        var totalMuscular = 0.0
        var totalSpinal = 0.0
        var accumulatedDrain = 0.0
        var muscleVolumeMap: [String: Int] = [:]

        let exercises = session.exercises + session.parts.flatMap { $0.exercises }

        for ex in exercises {
            let resolvedId = (ex.exerciseDbId ?? ex.exerciseId)?.lowercased()
            let dbInfo = resolvedId.flatMap { exerciseDb[$0] }
            guard let metrics = getDynamicAugeMetrics(exerciseName: ex.name, equipment: dbInfo?.equipment, dbInfo: dbInfo) else {
                print("[AugeFatigueEngine] Sin métricas de fatiga para '\(ex.name)' — ejercicio omitido del drenaje ajustado")
                continue
            }
            let densityMult = getDensityMultiplierForExercise(supersetId: ex.supersetId, restTime: ex.restTime ?? 90)
            let primaryMuscle: String
            if let involved = dbInfo?.involvedMuscles.first(where: { $0.role == .PRIMARY }) {
                primaryMuscle = getAugeMuscleDisplayId(rawMuscle: involved.muscle, rawEmphasis: involved.emphasis)
            } else {
                primaryMuscle = "Core"
            }
            var accumulated = muscleVolumeMap[primaryMuscle] ?? 0
            let muscleKey = primaryMuscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
            let muscleMult = adaptiveCache.muscleDrainMultipliers[muscleKey] ?? 1.0

            for s in ex.sets {
                guard !s.isIneffective else { continue }
                accumulated += 1
                let calculatedWeight: Double
                if ex.trainingMode == .RM, let pct = s.targetPercentageRM, let ref1rm = ex.reference1RM, ref1rm > 0.0 {
                    calculatedWeight = (pct / 100.0) * ref1rm
                } else {
                    calculatedWeight = s.weight ?? 60.0
                }
                let syntheticSet = CompletedSet(
                    id: "",
                    weight: calculatedWeight,
                    reps: s.targetReps ?? 8,
                    rpe: s.targetRPE,
                    rir: s.targetRIR,
                    isFailure: s.isFailure || s.intensityMode == .FAILURE,
                    actualIntensityMode: s.intensityMode,
                    actualIntensityValue: {
                        switch s.intensityMode {
                        case .RPE: return s.targetRPE
                        case .RIR: return s.targetRIR.map { Double($0) }
                        default: return nil
                        }
                    }()
                )
                let rawDrain = calculateSetBatteryDrain(
                    set: syntheticSet,
                    metrics: metrics,
                    tanks: tanks,
                    accumulatedSets: accumulated,
                    restTime: ex.restTime ?? 90,
                    densityMultiplier: densityMult,
                    cnsMultiplier: adaptiveCache.cnsDrainMultiplier,
                    spinalMultiplier: adaptiveCache.spinalDrainMultiplier,
                    muscleMultiplier: muscleMult
                )
                let diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                let adjustedMuscular = rawDrain.muscularDrainPct * conservationFactor * diminishingFactor
                let adjustedCns = rawDrain.cnsDrainPct * conservationFactor * diminishingFactor
                let adjustedSpinal = rawDrain.spinalDrainPct * conservationFactor * diminishingFactor

                totalMuscular += applySoftCap(drain: adjustedMuscular, accumulated: totalMuscular, cap: muscularCap)
                totalCns += applySoftCap(drain: adjustedCns, accumulated: totalCns, cap: cnsCap)
                totalSpinal += applySoftCap(drain: adjustedSpinal, accumulated: totalSpinal, cap: spinalCap)
                accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
            }
            muscleVolumeMap[primaryMuscle] = accumulated
        }

        let scaledSpinal = scaleSpinalDrainToUi(rawSpinalSessionDrain: totalSpinal, tanks: tanks)
        let raw = PredictedDrain(
            cns: Int(min(totalCns, cnsCap)),
            muscular: Int(min(totalMuscular, muscularCap)),
            spinal: Int(min(scaledSpinal, spinalCap))
        )
        let (cnsBias, muscularBias, spinalBias) = normalizeBias(profile: settings.augePredictionBias)
        return PredictedDrain(
            cns: Int(min(max(Double(raw.cns) + cnsBias, 0), cnsCap)),
            muscular: Int(min(max(Double(raw.muscular) + muscularBias, 0), muscularCap)),
            spinal: Int(min(max(Double(raw.spinal) + spinalBias, 0), spinalCap))
        )
    }

    private static let emaAlpha = 0.17
    private static let emaSmoothing = 0.83
    private static let trendWindow = 3

    static func calculateMesocycleStressEMA(
        logs: [WorkoutLog],
        programId: String,
        mesoIndex: Int
    ) -> MesocycleStressEMA {
        let relevant = logs
            .filter { $0.programId == programId && $0.mesoIndex == mesoIndex }
            .sorted { $0.date < $1.date }

        let stressScores = relevant.compactMap { $0.sessionStressScore }

        if stressScores.isEmpty {
            return MesocycleStressEMA(
                programId: programId,
                mesoIndex: mesoIndex,
                emaValue: 0.0,
                sessionCount: 0,
                latestStressScore: nil,
                stressTrend: .STABLE,
                computedAtMs: Int64(Date().timeIntervalSince1970 * 1000)
            )
        }

        let emaValue = stressScores.reduce(0.0) { acc, score in
            acc * emaSmoothing + score * emaAlpha
        }

        let trend: StressTrend
        if stressScores.count >= trendWindow {
            let recent = Array(stressScores.suffix(trendWindow))
            let firstHalf = Array(recent.prefix(trendWindow / 2))
            let secondHalf = Array(recent.suffix(trendWindow / 2))
            let avgFirst = firstHalf.reduce(0.0, +) / Double(firstHalf.count)
            let avgSecond = secondHalf.reduce(0.0, +) / Double(secondHalf.count)
            if avgSecond > avgFirst * 1.10 {
                trend = .RISING
            } else if avgSecond < avgFirst * 0.90 {
                trend = .FALLING
            } else {
                trend = .STABLE
            }
        } else {
            trend = .STABLE
        }

        return MesocycleStressEMA(
            programId: programId,
            mesoIndex: mesoIndex,
            emaValue: emaValue,
            sessionCount: stressScores.count,
            latestStressScore: stressScores.last,
            stressTrend: trend,
            computedAtMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }

    static func adjustPredictedDrainWithEMA(
        rawDrain: PredictedDrain,
        ema: MesocycleStressEMA
    ) -> PredictedDrain {
        guard ema.sessionCount >= 2 else { return rawDrain }

        let avgHistoricalStress = ema.emaValue
        let highStressThreshold = 50.0
        let lowStressThreshold = 25.0

        let adjustmentFactor: Double
        if avgHistoricalStress > highStressThreshold {
            let severity = min((avgHistoricalStress - highStressThreshold) / 50.0, 0.5)
            adjustmentFactor = 1.0 - (severity * 0.15)
        } else if avgHistoricalStress < lowStressThreshold {
            let headroom = min((lowStressThreshold - avgHistoricalStress) / 25.0, 0.3)
            adjustmentFactor = 1.0 + (headroom * 0.10)
        } else {
            adjustmentFactor = 1.0
        }

        return PredictedDrain(
            cns: Int(min(max(Double(rawDrain.cns) * adjustmentFactor, 0), 100)),
            muscular: Int(min(max(Double(rawDrain.muscular) * adjustmentFactor, 0), 100)),
            spinal: Int(min(max(Double(rawDrain.spinal) * adjustmentFactor, 0), 100))
        )
    }

    static func shouldSuggestAutoDeload(
        cumulativeFatigue: Double,
        readinessScore: Int,
        settings: AppSettings
    ) -> Bool {
        guard settings.algorithmSettings.augeAutoDeload else { return false }
        let highFatigue = cumulativeFatigue > 75.0
        let lowReadiness = readinessScore < 40
        return highFatigue && lowReadiness
    }
}
