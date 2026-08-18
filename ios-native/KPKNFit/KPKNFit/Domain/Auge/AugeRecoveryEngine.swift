import Foundation

// ─────────────────────────────────────────────────────────────────────────────
// AugeRecoveryEngine — Motor de Recuperación AUGE v3.0 para Swift.
// Traducción fiel de AugeRecoveryEngine.kt
// ─────────────────────────────────────────────────────────────────────────────

enum AugeRecoveryEngine {

    // ─── Perfiles de recuperación (horas base) ────────────────────────────────

    private static let RECOVERY_PROFILES: [String: Double] = [
        "fast":   24.0,
        "medium": 48.0,
        "slow":   72.0,
        "heavy":  96.0,
    ]

    private static let MUSCLE_PROFILE_MAP: [String: String] = [
        "Bíceps": "fast", "Tríceps": "fast", "Deltoides": "fast",
        "Deltoides Anterior": "fast", "Deltoides Lateral": "fast", "Deltoides Posterior": "fast",
        "Pantorrillas": "fast", "Abdomen": "fast", "Antebrazo": "fast",
        "Gemelos": "fast", "Sóleo": "fast", "Oblicuos": "fast", "Costales": "fast",
        "Pectorales": "medium", "Dorsales": "medium", "Hombros": "medium", "Trapecio": "medium",
        "Romboide": "medium", "Redondo Menor": "medium", "Redondo Mayor": "medium",
        "Infraespinoso": "medium", "Supraespinoso": "medium", "Subescapular": "medium",
        "Serrato Anterior": "medium", "Cuadriceps": "medium",
        "Cuádriceps": "slow", "Glúteos": "slow",
        "Glúteo Mayor": "slow", "Glúteo Medio": "slow", "Glúteo Menor": "slow",
        "Aductores": "medium", "Tensor Fascia Lata": "medium",
        "Isquiosurales": "heavy", "Bíceps Femoral": "heavy", "Semitendinoso": "heavy",
        "Semimembranoso": "heavy", "Erectores Espinales": "heavy", "Core": "medium",
        "Cuello": "medium", "Psoas": "medium", "Lumbar": "heavy",
    ]

    // Los músculos "pilar" para el cálculo de batería muscular global
    private static let PILLAR_MUSCLES: [String] = [
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
        "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    ]

    // Set completo de baterías por músculo: pilares + todo grupo con perfil de
    // recuperación (Aductores, Antebrazo, Cuello, Psoas, rotadores, etc.).
    // El promedio global sigue usando solo PILLAR_MUSCLES; este set alimenta
    // perMuscle para la UI y el readiness de sesiones que involucran esos músculos.
    private static let BATTERY_MUSCLES: [String] = {
        var seen = Set<String>()
        var result: [String] = []
        for muscle in PILLAR_MUSCLES + MUSCLE_PROFILE_MAP.keys {
            let pillar = resolveAugeMuscle(rawMuscle: muscle).broad
            if seen.insert(pillar).inserted { result.append(pillar) }
        }
        return result
    }()

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static func densityMultiplierForCompletedExercise(_ ex: CompletedExercise) -> Double {
        AugeFatigueEngine.getDensityMultiplierForExercise(
            supersetId: ex.supersetId,
            restTime: ex.supersetRestBetween ?? ex.restTime,
            supersetExerciseCount: ex.supersetExerciseCount,
            supersetRounds: ex.supersetRounds,
            supersetRestAfter: ex.supersetRestAfter
        )
    }

    private static func normKey(_ s: String) -> String {
        s.lowercased().trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: "á", with: "a")
            .replacingOccurrences(of: "é", with: "e")
            .replacingOccurrences(of: "í", with: "i")
            .replacingOccurrences(of: "ó", with: "o")
            .replacingOccurrences(of: "ú", with: "u")
            .replacingOccurrences(of: "ü", with: "u")
    }

    private static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    private static func recoveryBand(_ score: Int) -> RecoveryBand {
        switch score {
        case 85...: return .HIGH
        case 70...: return .NORMAL
        case 50...: return .MODERATE
        case 35...: return .LOW
        default:    return .CRITICAL
        }
    }

    private static func confidenceLabel(_ score: Int) -> String {
        switch score {
        case 80...: return "Alta"
        case 60...: return "Media"
        default:    return "Baja"
        }
    }

    private static func actionForChannel(_ id: RecoveryChannelId, _ score: Int) -> String {
        switch id {
        case .MUSCULAR:
            switch score {
            case 85...: return "Puedes meter volumen alto si la sesión lo pide."
            case 70...: return "Volumen normal y buena ejecución."
            case 50...: return "Modera series duras en el músculo más cargado."
            case 35...: return "Prioriza técnica y recorta volumen local."
            default:    return "No fuerces volumen local hoy."
            }
        case .SYSTEM:
            switch score {
            case 85...: return "Buen día para intensidad y coordinación."
            case 70...: return "Empuja normal, sin necesidad de ir al límite."
            case 50...: return "Mejor dejar alguna repetición en reserva."
            case 35...: return "Evita sets al fallo y compuestos muy demandantes."
            default:    return "Haz una sesión ligera o técnica."
            }
        case .STRUCTURE:
            switch score {
            case 85...: return "Toleras bien carga axial y tensión conectiva."
            case 70...: return "Carga estructural normal con buena técnica."
            case 50...: return "Conviene moderar impacto axial y variantes agresivas."
            case 35...: return "Usa variantes estables o menos compresivas."
            default:    return "Evita carga axial o trabajo explosivo hoy."
            }
        }
    }

    private static func parseWellbeingDate(_ dateStr: String) -> Int64 {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        let prefix = String(dateStr.prefix(10))
        if let date = formatter.date(from: prefix) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        return 0
    }

    private static func manualBatteryAnchorMs(_ wellbeing: DailyWellbeingLog?) -> Int64 {
        guard let w = wellbeing else { return 0 }
        if let anchor = w.manualBatteryAnchorMs { return anchor }
        if w.manualNeuralBattery != nil || w.manualMuscularBattery != nil
            || w.manualSpinalBattery != nil || !w.manualMuscleBatteries.isEmpty {
            return nowMs()
        }
        return parseWellbeingDate(w.date)
    }

    private static func muscleMatchesCategory(_ specificMuscle: String, _ category: String) -> Bool {
        matchesAugeMuscleTarget(rawMuscle: specificMuscle, target: category)
    }

    /// Promedio ponderado de sueño de los últimos 3 días.
    /// Pesos: 50% última noche, 30% anteayer, 20% hace 3 días.
    private static func calculateWeightedSleepHours(
        _ sleepLogs: [SleepLog],
        _ wellbeing: DailyWellbeingLog?
    ) -> Double {
        let sorted = sleepLogs.sorted { $0.endTime > $1.endTime }.prefix(3)
        switch sorted.count {
        case 0:    return wellbeing?.sleepHours ?? 7.5
        case 1:    return sorted[0].duration
        case 2:    return sorted[0].duration * 0.6 + sorted[1].duration * 0.4
        default:   return sorted[0].duration * 0.5 + sorted[1].duration * 0.3 + sorted[2].duration * 0.2
        }
    }

    private static func resolveDbInfo(
        _ ex: CompletedExercise,
        _ exerciseDb: [String: ExerciseMuscleInfo]
    ) -> ExerciseMuscleInfo? {
        let lookupId = (ex.exerciseDbId ?? ex.exerciseId)?.lowercased()
        return lookupId.flatMap { exerciseDb[$0] }
    }

    private static func calculateMuscleDiscomfortPenaltyPct(
        muscleName: String,
        history: [WorkoutLog],
        now: Int64,
        wellbeing: DailyWellbeingLog? = nil
    ) -> Double {
        var penalty = 0.0
        for log in history {
            let logTime = AugeUtils.logDateMs(log)
            guard logTime > 0 else { continue }
            let hoursSince = max(0.0, Double(now - logTime) / 3_600_000.0)
            let timeDecay = AugeUtils.safeExp(-(hoursSince / 84.0))
            for report in log.postExerciseReports {
                let uniqueDiscomfortIds = Array(Set(report.discomfortIds.filter { $0 != "none" }))
                for discomfortId in uniqueDiscomfortIds {
                    guard let entry = DISCOMFORT_CATALOG_BY_ID[discomfortId] else { continue }
                    let matchesMuscle = entry.relatedMuscles.contains {
                        muscleMatchesCategory($0, muscleName) || muscleMatchesCategory(muscleName, $0)
                    }
                    guard matchesMuscle else { continue }
                    let quality = max(1, min(10, report.technicalQuality))
                    let qualityMult: Double
                    switch quality {
                    case ...4: qualityMult = 1.25
                    case ...6: qualityMult = 1.10
                    case 9...: qualityMult = 0.85
                    default:   qualityMult = 1.0
                    }
                    penalty += 8.0 * qualityMult * timeDecay
                }
            }
        }
        if let w = wellbeing {
            let uniquePreWork = Array(Set(w.preWorkoutDiscomforts.filter { $0 != "none" }))
            for discomfortId in uniquePreWork {
                guard let entry = DISCOMFORT_CATALOG_BY_ID[discomfortId] else { continue }
                let matchesMuscle = entry.relatedMuscles.contains {
                    muscleMatchesCategory($0, muscleName) || muscleMatchesCategory(muscleName, $0)
                }
                if matchesMuscle { penalty += 8.0 }
            }
        }
        return AugeUtils.clamp(penalty, 0.0, 30.0)
    }

    private static func calculateMuscleFeedbackPenaltyPct(
        _ muscleName: String,
        _ feedbacks: [PostSessionFeedback]
    ) -> Double {
        guard !feedbacks.isEmpty else { return 0.0 }
        var total = 0.0
        var samples = 0
        for fb in feedbacks {
            let direct = fb.muscleFeedback.filter { (key, _) in
                muscleMatchesCategory(key, muscleName) || muscleMatchesCategory(muscleName, key)
            }
            guard !direct.isEmpty else { continue }
            for (_, entry) in direct {
                let domsPenalty: Double
                switch max(1, min(5, entry.doms)) {
                case 5:    domsPenalty = 12.0
                case 4:    domsPenalty = 8.0
                case 3:    domsPenalty = 4.0
                default:   domsPenalty = 0.0
                }
                let jointPenalty: Double = entry.jointPain ? 4.0 : 0.0
                let strengthAdj: Double
                switch entry.strengthCapacity {
                case ...4: strengthAdj = 5.0
                case ...6: strengthAdj = 2.0
                case 9...: strengthAdj = -2.0
                default:   strengthAdj = 0.0
                }
                total += domsPenalty + jointPenalty + strengthAdj
                samples += 1
            }
        }
        guard samples > 0 else { return 0.0 }
        return AugeUtils.clamp(total / Double(samples), -4.0, 16.0)
    }

    private static func calculateSystemFeedbackPenaltyPct(_ feedbacks: [PostSessionFeedback]) -> Double {
        guard !feedbacks.isEmpty else { return 0.0 }
        let avgRecovery = feedbacks.map { Double(max(1, min(10, $0.cnsRecovery))) }.reduce(0, +) / Double(feedbacks.count)
        switch avgRecovery {
        case ...4.0: return 12.0
        case ...5.5: return 8.0
        case ...7.0: return 3.0
        case 9.0...: return -4.0
        default:     return 0.0
        }
    }

    /// Capacidad de trabajo dinámica para un músculo basada en el historial de 4 semanas.
    private static func calculateUserWorkCapacity(
        _ muscleName: String,
        _ history: [WorkoutLog],
        _ settings: Settings,
        _ exerciseDb: [String: ExerciseMuscleInfo]
    ) -> Double {
        let now = nowMs()
        let fourWeeksAgo = now - 28 * 24 * 3_600_000
        let recentLogs = history.filter { AugeUtils.logDateMs($0) > fourWeeksAgo - 7 * 24 * 3_600_000 }
        let baseFloor = AugeFatigueEngine.getAthleteCapacity(settings: settings)
        guard !recentLogs.isEmpty else { return baseFloor }

        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        var totalStress = 0.0

        for log in recentLogs {
            let logMs = AugeUtils.logDateMs(log)
            let daysSince = Double(now - logMs) / (24.0 * 3_600_000)
            let decay: Double
            if daysSince <= 28.0 { decay = 1.0 }
            else if daysSince >= 35.0 { decay = 0.0 }
            else { decay = (35.0 - daysSince) / 7.0 }

            for ex in log.completedExercises {
                guard let dbInfo = resolveDbInfo(ex, exerciseDb) else { continue }
                guard let involvement = dbInfo.involvedMuscles.first(where: { muscleMatchesCategory($0.muscle, muscleName) }) else { continue }

                let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo.equipment, dbInfo) ?? AugeMetrics()
                let densityMult = densityMultiplierForCompletedExercise(ex)
                var accumulated = 0
                var setStress = 0.0

                for s in ex.sets {
                    guard AugeFatigueEngine.isSetEffective(s) else { continue }
                    accumulated += 1
                    let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                        set: s, metrics: metrics, tanks: tanks,
                        accumulatedSets: accumulated, restTime: ex.restTime,
                        densityMultiplier: densityMult
                    )
                    setStress += drain.muscularDrainPct
                }

                let roleMult = FATIGUE_ROLE_MULTIPLIERS[involvement.role] ?? 1.0
                totalStress += setStress * roleMult * decay
            }
        }

        let weeklyAvg = totalStress / 4.0
        let calculatedCapacity = weeklyAvg * 1.8
        // Recalibración 2026-08-17: paridad Android, clamp 120
        return AugeUtils.clamp(max(calculatedCapacity, baseFloor), 120.0, 3500.0)
    }

    // ─── 1. BATERÍA MUSCULAR INDIVIDUAL ───────────────────────────────────────

    static func calculateMuscleBattery(
        muscleName: String,
        history: [WorkoutLog],
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        nutritionMultiplier: Double = 1.0,
        sleepLogs: [SleepLog] = [],
        feedbacks: [PostSessionFeedback] = [],
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        precomputedCapacity: Double? = nil,
        nowOverride: Int64? = nil
    ) -> MuscleRecoveryStatus {
        let now = nowOverride ?? nowMs()
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        let capacity = precomputedCapacity ?? calculateUserWorkCapacity(muscleName, history, settings, exerciseDb)

        let profileKey = MUSCLE_PROFILE_MAP.first { normKey($0.key) == normKey(muscleName) }?.value ?? "medium"
        let normMuscleName = muscleName.lowercased().trimmingCharacters(in: .whitespaces)
        let adaptiveHours: Double? = adaptiveCache.personalizedRecoveryHours[normMuscleName]
            ?? adaptiveCache.personalizedRecoveryHours[normKey(muscleName)]
            ?? adaptiveCache.personalizedRecoveryHours.first { normKey($0.key) == normKey(muscleName) }?.value
        let baseRecoveryTime = AugeUtils.clamp(adaptiveHours ?? RECOVERY_PROFILES[profileKey] ?? 48.0, 18.0, 144.0)

        var multiplier = nutritionMultiplier
        let age = settings.userVitals.age ?? 25
        if age > 35 { multiplier *= (1.0 + Double(age - 35) * 0.01) }
        if settings.userVitals.gender == .FEMALE { multiplier *= 0.85 }

        let feedbackPenaltyPct = calculateMuscleFeedbackPenaltyPct(muscleName, feedbacks)
        let discomfortPenaltyPct = calculateMuscleDiscomfortPenaltyPct(
            muscleName: muscleName, history: history, now: now, wellbeing: wellbeing
        )
        let recoveryTimeMultiplier = 1.0
            + (max(0.0, feedbackPenaltyPct) / 48.0)
            + (max(0.0, discomfortPenaltyPct) / 120.0)
        let realRecoveryTime = baseRecoveryTime * max(0.5, multiplier) * recoveryTimeMultiplier

        let k = 2.9957 / max(1.0, realRecoveryTime)
        let tenDaysAgo = now - 10 * 24 * 3_600_000

        let manualScore = wellbeing?.manualMuscleBatteries[muscleName]
        let anchorMs = manualBatteryAnchorMs(wellbeing)
        let hoursSinceAnchor = max(0.0, Double(now - anchorMs) / 3_600_000.0)
        var accumulatedFatigue = 0.0

        let relevantHistory: [WorkoutLog]
        if let manualScore = manualScore, anchorMs > 0 {
            let manualBattery = Double(max(0, min(100, manualScore)))
            let penalty = max(0.0, min(99.9, 100.0 - manualBattery))
            let impliedRawFatiguePct = -90.0 * log(1.0 - penalty / 100.0)
            let manualLoad = (impliedRawFatiguePct / 100.0) * capacity
            accumulatedFatigue = manualLoad * exp(-k * AugeUtils.getSigmoidalHours(hoursSinceAnchor))
            relevantHistory = history.filter { AugeUtils.logDateMs($0) > anchorMs && AugeUtils.logDateMs($0) > tenDaysAgo }
        } else {
            relevantHistory = history.filter { AugeUtils.logDateMs($0) > tenDaysAgo }
        }

        var effectiveSetsCount = 0
        var lastSessionDate: Int64 = 0

        for log in relevantHistory {
            let logTime = AugeUtils.logDateMs(log)
            let hoursSince = max(0.0, Double(now - logTime) / 3_600_000.0)
            let conservationFactor = 0.85
            let decayK = 0.65
            var accumulatedDrain = 0.0
            var overallMuscleVolumeMap: [String: Int] = [:]
            var sessionMuscleStress = 0.0

            for ex in log.completedExercises {
                let dbInfo = resolveDbInfo(ex, exerciseDb)
                let involvement = dbInfo?.involvedMuscles.first { muscleMatchesCategory($0.muscle, muscleName) }
                let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?? AugeMetrics()
                let densityMult = densityMultiplierForCompletedExercise(ex)
                let primaryMuscle = dbInfo?.involvedMuscles.first { $0.role == .PRIMARY }
                    .flatMap { getAugeMuscleDisplayId(rawMuscle: $0.muscle, rawEmphasis: $0.emphasis) } ?? "Core"
                var accumulated = overallMuscleVolumeMap[primaryMuscle] ?? 0
                let muscleKey = primaryMuscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
                let muscleMult = adaptiveCache.muscleDrainMultipliers[muscleKey] ?? 1.0

                for s in ex.sets {
                    guard AugeFatigueEngine.isSetEffective(s) else { continue }
                    accumulated += 1
                    let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                        set: s, metrics: metrics, tanks: tanks,
                        accumulatedSets: accumulated, restTime: ex.restTime,
                        densityMultiplier: densityMult,
                        cnsMultiplier: adaptiveCache.cnsDrainMultiplier,
                        spinalMultiplier: adaptiveCache.spinalDrainMultiplier,
                        muscleMultiplier: muscleMult
                    )
                    let diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                    let adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                    let adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                    let adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor

                    accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0

                    if let inv = involvement {
                        let roleMult = FATIGUE_ROLE_MULTIPLIERS[inv.role] ?? 1.0
                        let activation = inv.volumeContribution ?? VOLUME_CONTRIBUTION_FALLBACKS[inv.role] ?? 1.0
                        sessionMuscleStress += adjustedMuscular * roleMult * activation
                        if hoursSince <= 168 && (inv.role == .PRIMARY || inv.role == .SECONDARY) {
                            effectiveSetsCount += 1
                        }
                    }
                }
                overallMuscleVolumeMap[primaryMuscle] = accumulated
            }

            if sessionMuscleStress > 0 {
                accumulatedFatigue += sessionMuscleStress * AugeUtils.safeExp(-k * AugeUtils.getSigmoidalHours(hoursSince))
                if logTime > lastSessionDate { lastSessionDate = logTime }
            }
        }

        let rawFatiguePct = (accumulatedFatigue / capacity) * 100.0
        let fatiguePenalty = AugeUtils.clamp(100.0 * (1.0 - AugeUtils.safeExp(-rawFatiguePct / 90.0)), 0.0, 100.0)
        var battery = AugeUtils.clamp(100.0 - fatiguePenalty, 0.0, 100.0)

        if accumulatedFatigue <= 0.1 && (wellbeing?.doms ?? 1) <= 2 { battery = 100.0 }

        let domsCap: Double
        switch wellbeing?.doms ?? 1 {
        case 5:    domsCap = 20.0
        case 4:    domsCap = 50.0
        case 3:    domsCap = 85.0
        default:   domsCap = 100.0
        }
        battery = min(battery, domsCap)

        let status: RecoveryStatus
        switch battery {
        case 95...: status = .FRESH
        case 85...: status = .OPTIMAL
        case 40...: status = .RECOVERING
        default:    status = .EXHAUSTED
        }

        var hoursToRecovery = 0
        if battery < 90 && accumulatedFatigue > 0 {
            let targetFatigue = -90.0 * log(0.9) * capacity / 100.0
            if accumulatedFatigue > targetFatigue {
                hoursToRecovery = Int(max(0.0, -log(targetFatigue / accumulatedFatigue) / k))
            }
        }

        let floor = AugeUtils.physiologicalFloor(settings: settings).muscular
        battery = max(battery, Double(floor))

        let myDelta: Double = adaptiveCache.muscleDeltas[normMuscleName]
            ?? adaptiveCache.muscleDeltas[normKey(muscleName)]
            ?? adaptiveCache.muscleDeltas.first { normKey($0.key) == normKey(muscleName) }?.value
            ?? 0.0
        battery = AugeUtils.clamp(battery + myDelta, 0.0, 100.0)

        return MuscleRecoveryStatus(
            muscleName: muscleName,
            recoveryScore: Int(battery),
            hoursToRecovery: hoursToRecovery,
            hoursSinceLastSession: lastSessionDate > 0 ? Int((now - lastSessionDate) / 3_600_000) : -1,
            effectiveSets: effectiveSetsCount,
            status: status
        )
    }

    static func calculateMuscleSessionStress(
        muscleName: String,
        log: WorkoutLog,
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo],
        adaptiveCache: AugeAdaptiveCache
    ) -> Double {
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        let conservationFactor = 0.85
        let decayK = 0.65
        var accumulatedDrain = 0.0
        var overallMuscleVolumeMap: [String: Int] = [:]
        var sessionMuscleStress = 0.0

        for ex in log.completedExercises {
            let dbInfo = resolveDbInfo(ex, exerciseDb)
            let involvement = dbInfo?.involvedMuscles.first { muscleMatchesCategory($0.muscle, muscleName) }
            let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?? AugeMetrics()
            let densityMult = densityMultiplierForCompletedExercise(ex)
            let primaryMuscle = dbInfo?.involvedMuscles.first { $0.role == .PRIMARY }
                .flatMap { getAugeMuscleDisplayId(rawMuscle: $0.muscle, rawEmphasis: $0.emphasis) } ?? "Core"
            var accumulated = overallMuscleVolumeMap[primaryMuscle] ?? 0
            let muscleKey = primaryMuscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
            let muscleMult = adaptiveCache.muscleDrainMultipliers[muscleKey] ?? 1.0

            for s in ex.sets {
                guard AugeFatigueEngine.isSetEffective(s) else { continue }
                accumulated += 1
                let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set: s, metrics: metrics, tanks: tanks,
                    accumulatedSets: accumulated, restTime: ex.restTime,
                    densityMultiplier: densityMult,
                    cnsMultiplier: adaptiveCache.cnsDrainMultiplier,
                    spinalMultiplier: adaptiveCache.spinalDrainMultiplier,
                    muscleMultiplier: muscleMult
                )
                let diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                let adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                let adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                let adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
                if let inv = involvement {
                    let roleMult = FATIGUE_ROLE_MULTIPLIERS[inv.role] ?? 1.0
                    let activation = inv.volumeContribution ?? VOLUME_CONTRIBUTION_FALLBACKS[inv.role] ?? 1.0
                    sessionMuscleStress += adjustedMuscular * roleMult * activation
                }
            }
            overallMuscleVolumeMap[primaryMuscle] = accumulated
        }
        return sessionMuscleStress
    }

    // ─── 2. BATERÍA SNC / SISTÉMICA ───────────────────────────────────────────

    static func calculateSystemicFatigue(
        history: [WorkoutLog],
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        sleepLogs: [SleepLog] = [],
        feedbacks: [PostSessionFeedback] = [],
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache()
    ) -> (Int, Int, Int) { // (cnsBattery, gymLoad, lifeLoad)
        let now = nowMs()
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        let baseTau = adaptiveCache.cnsRecoveryHours ?? 36.0
        let feedbackPenalty = calculateSystemFeedbackPenaltyPct(feedbacks)
        let tauHours = baseTau * (1.0 + max(0.0, feedbackPenalty) / 48.0)
        let last10Days = now - 10 * 24 * 3_600_000

        let manualNeural = wellbeing?.manualNeuralBattery
        let anchorMs = manualBatteryAnchorMs(wellbeing)
        let hoursSinceAnchor = max(0.0, Double(now - anchorMs) / 3_600_000.0)
        var accumulatedGymLoad = 0.0

        let recentLogs: [WorkoutLog]
        if let manualNeural = manualNeural, anchorMs > 0 {
            let manualBattery = Double(max(0, min(100, manualNeural)))
            let penalty = max(0.0, min(99.9, 100.0 - manualBattery))
            let impliedRawGymPct = -75.0 * log(1.0 - penalty / 100.0)
            let capacity = max(80.0, tanks.cns * 1.15)
            let manualLoad = (impliedRawGymPct / 100.0) * capacity
            accumulatedGymLoad = manualLoad * exp(-hoursSinceAnchor / tauHours)
            recentLogs = history.filter { AugeUtils.logDateMs($0) > anchorMs && AugeUtils.logDateMs($0) > last10Days }
        } else {
            recentLogs = history.filter { AugeUtils.logDateMs($0) > last10Days }
        }

        for log in recentLogs {
            let hoursSince = max(0.0, Double(now - AugeUtils.logDateMs(log)) / 3_600_000.0)
            let conservationFactor = 0.85
            let decayK = 0.65
            var accumulatedDrain = 0.0
            var overallMuscleVolumeMap: [String: Int] = [:]
            var sessionCns = 0.0

            for ex in log.completedExercises {
                let dbInfo = resolveDbInfo(ex, exerciseDb)
                let primaryMuscle = dbInfo?.involvedMuscles.first { $0.role == .PRIMARY }?.muscle ?? "Core"
                var accumulated = overallMuscleVolumeMap[primaryMuscle] ?? 0
                let densityMult = densityMultiplierForCompletedExercise(ex)
                let muscleKey = primaryMuscle.lowercased().trimmingCharacters(in: .whitespaces)
                let muscleMult = adaptiveCache.muscleDrainMultipliers[muscleKey] ?? 1.0

                for s in ex.sets {
                    guard AugeFatigueEngine.isSetEffective(s) else { continue }
                    accumulated += 1
                    let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?? AugeMetrics()
                    let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                        set: s, metrics: metrics, tanks: tanks,
                        accumulatedSets: accumulated, restTime: ex.restTime,
                        densityMultiplier: densityMult,
                        cnsMultiplier: adaptiveCache.cnsDrainMultiplier,
                        spinalMultiplier: adaptiveCache.spinalDrainMultiplier,
                        muscleMultiplier: muscleMult
                    )
                    let diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                    let adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                    let adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                    let adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                    accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0

                    var currentCns = adjustedCns
                    if s.weight > 0.0 && s.reps <= 3 && AugeFatigueEngine.getEffectiveRPE(set: s) >= 9.5 {
                        currentCns += adjustedCns * 0.15
                    }
                    sessionCns += currentCns
                }
                overallMuscleVolumeMap[primaryMuscle] = accumulated
            }

            let durationMin = log.durationMinutes
            if durationMin > 90 { sessionCns *= 1.15 }
            else if durationMin > 75 { sessionCns *= 1.08 }

            accumulatedGymLoad += sessionCns * AugeUtils.safeExp(-(hoursSince / tauHours))
        }

        let capacity = max(80.0, tanks.cns * 1.15)
        let rawGymPct = (accumulatedGymLoad / capacity) * 100.0
        let normalizedGymFatigue = AugeUtils.clamp(100.0 * (1.0 - AugeUtils.safeExp(-rawGymPct / 75.0)), 0.0, 100.0)
        let cnsBattery = AugeUtils.clamp(100.0 - normalizedGymFatigue, 0.0, 100.0)

        return (Int(cnsBattery), Int(normalizedGymFatigue), 0)
    }

    // ─── 3. BATERÍA ESPINAL ───────────────────────────────────────────────────

    private static func calculateSpineFatigueMultiplier(
        history: [WorkoutLog],
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo],
        sleepLogs: [SleepLog],
        nutritionMultiplier: Double,
        feedbacks: [PostSessionFeedback],
        adaptiveCache: AugeAdaptiveCache,
        erectorsCapacity: Double? = nil,
        coreCapacity: Double? = nil,
        glutesCapacity: Double? = nil,
        latsCapacity: Double? = nil,
        nowOverride: Int64? = nil
    ) -> Double {
        let erectors = Double(calculateMuscleBattery(
            muscleName: "Erectores Espinales", history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, nutritionMultiplier: nutritionMultiplier, sleepLogs: sleepLogs,
            feedbacks: feedbacks, adaptiveCache: adaptiveCache, precomputedCapacity: erectorsCapacity, nowOverride: nowOverride
        ).recoveryScore)
        let core = Double(calculateMuscleBattery(
            muscleName: "Core", history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, nutritionMultiplier: nutritionMultiplier, sleepLogs: sleepLogs,
            feedbacks: feedbacks, adaptiveCache: adaptiveCache, precomputedCapacity: coreCapacity, nowOverride: nowOverride
        ).recoveryScore)
        let glutes = Double(calculateMuscleBattery(
            muscleName: "Glúteos", history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, nutritionMultiplier: nutritionMultiplier, sleepLogs: sleepLogs,
            feedbacks: feedbacks, adaptiveCache: adaptiveCache, precomputedCapacity: glutesCapacity, nowOverride: nowOverride
        ).recoveryScore)
        let lats = Double(calculateMuscleBattery(
            muscleName: "Dorsales", history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, nutritionMultiplier: nutritionMultiplier, sleepLogs: sleepLogs,
            feedbacks: feedbacks, adaptiveCache: adaptiveCache, precomputedCapacity: latsCapacity, nowOverride: nowOverride
        ).recoveryScore)

        // Spine Protection Factor (SPF)
        let spf = (erectors * 0.50) + (core * 0.25) + (glutes * 0.15) + (lats * 0.10)
        if spf >= 80.0 { return 1.0 }

        let fatigueDeficit = (100.0 - spf) / 100.0
        return 1.0 + (fatigueDeficit * fatigueDeficit * 0.75)
    }

    static func calculateSpinalBattery(
        history: [WorkoutLog],
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        sleepLogs: [SleepLog] = [],
        nutritionLogs: [NutritionLog] = [],
        feedbacks: [PostSessionFeedback] = [],
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache()
    ) -> Int {
        let now = nowMs()
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        let tauHours = adaptiveCache.spinalRecoveryHours ?? 52.0
        let last10Days = now - 10 * 24 * 3_600_000

        let manualSpinal = wellbeing?.manualSpinalBattery
        let anchorMs = manualBatteryAnchorMs(wellbeing)
        let hoursSinceAnchor = max(0.0, Double(now - anchorMs) / 3_600_000.0)
        var accumulatedSpinalLoad = 0.0

        let recentLogs: [WorkoutLog]
        if let manualSpinal = manualSpinal, anchorMs > 0 {
            let manualBattery = Double(max(0, min(100, manualSpinal)))
            let penalty = max(0.0, min(99.9, 100.0 - manualBattery))
            let impliedRawPct = -70.0 * log(1.0 - penalty / 100.0)
            let capacity = max(70.0, tanks.spinal * 0.02)
            let manualLoad = (impliedRawPct / 100.0) * capacity
            accumulatedSpinalLoad = manualLoad * exp(-AugeUtils.getSpinalRecoveryHours(hoursSinceAnchor) / tauHours)
            recentLogs = history.filter { AugeUtils.logDateMs($0) > anchorMs && AugeUtils.logDateMs($0) > last10Days }
        } else {
            recentLogs = history.filter { AugeUtils.logDateMs($0) > last10Days }
        }

        let stressLevel = wellbeing?.stressLevel ?? 3
        let nutritionMultiplier = getNutritionMultiplier(settings: settings, nutritionLogs: nutritionLogs, stressLevel: stressLevel)

        // Optimización O(N^2): Precalcular capacidades musculares una sola vez
        let erectorsCapacity = calculateUserWorkCapacity("Erectores Espinales", history, settings, exerciseDb)
        let coreCapacity = calculateUserWorkCapacity("Core", history, settings, exerciseDb)
        let glutesCapacity = calculateUserWorkCapacity("Glúteos", history, settings, exerciseDb)
        let latsCapacity = calculateUserWorkCapacity("Dorsales", history, settings, exerciseDb)

        for log in recentLogs {
            let logTime = AugeUtils.logDateMs(log)
            let hoursSince = max(0.0, Double(now - logTime) / 3_600_000.0)

            let historyUpToLog = history.filter { AugeUtils.logDateMs($0) <= logTime }
            let dateFormatter = DateFormatter()
            dateFormatter.dateFormat = "yyyy-MM-dd"
            let feedbacksUpToLog = feedbacks.filter { fb in
                guard let fbDate = dateFormatter.date(from: fb.date) else { return false }
                return Int64(fbDate.timeIntervalSince1970 * 1000) <= logTime
            }

            let spineProtectionMultiplier = calculateSpineFatigueMultiplier(
                history: historyUpToLog, wellbeing: wellbeing, settings: settings,
                exerciseDb: exerciseDb, sleepLogs: sleepLogs, nutritionMultiplier: nutritionMultiplier,
                feedbacks: feedbacksUpToLog, adaptiveCache: adaptiveCache,
                erectorsCapacity: erectorsCapacity, coreCapacity: coreCapacity,
                glutesCapacity: glutesCapacity, latsCapacity: latsCapacity, nowOverride: logTime
            )

            let conservationFactor = 0.85
            let decayK = 0.65
            var accumulatedDrain = 0.0
            var overallMuscleVolumeMap: [String: Int] = [:]
            var sessionSpinalLoad = 0.0

            for ex in log.completedExercises {
                let dbInfo = resolveDbInfo(ex, exerciseDb)
                let primaryMuscle = dbInfo?.involvedMuscles.first { $0.role == .PRIMARY }?.muscle ?? "Core"
                var accumulated = overallMuscleVolumeMap[primaryMuscle] ?? 0
                let densityMult = densityMultiplierForCompletedExercise(ex)
                let muscleKey = primaryMuscle.lowercased().trimmingCharacters(in: .whitespaces)
                let muscleMult = adaptiveCache.muscleDrainMultipliers[muscleKey] ?? 1.0

                for s in ex.sets {
                    guard AugeFatigueEngine.isSetEffective(s) else { continue }
                    accumulated += 1
                    let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?? AugeMetrics()
                    let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                        set: s, metrics: metrics, tanks: tanks,
                        accumulatedSets: accumulated, restTime: ex.restTime,
                        densityMultiplier: densityMult,
                        cnsMultiplier: adaptiveCache.cnsDrainMultiplier,
                        spinalMultiplier: adaptiveCache.spinalDrainMultiplier,
                        muscleMultiplier: muscleMult
                    )
                    let diminishingFactor = 1.0 / (1.0 + decayK * (accumulatedDrain / 100.0))
                    let adjustedMuscular = drain.muscularDrainPct * conservationFactor * diminishingFactor
                    let adjustedCns = drain.cnsDrainPct * conservationFactor * diminishingFactor
                    let adjustedSpinal = drain.spinalDrainPct * conservationFactor * diminishingFactor
                    accumulatedDrain += (adjustedMuscular + adjustedCns + adjustedSpinal) / 3.0
                    sessionSpinalLoad += adjustedSpinal * spineProtectionMultiplier
                }
                overallMuscleVolumeMap[primaryMuscle] = accumulated
            }

            if log.durationMinutes > 90 { sessionSpinalLoad *= 1.08 }
            accumulatedSpinalLoad += sessionSpinalLoad * AugeUtils.safeExp(-(AugeUtils.getSpinalRecoveryHours(hoursSince) / tauHours))
        }

        let capacity = max(70.0, tanks.spinal * 0.02)
        let rawPct = (accumulatedSpinalLoad / capacity) * 100.0
        let normalizedLoad = AugeUtils.clamp(100.0 * (1.0 - AugeUtils.safeExp(-rawPct / 70.0)), 0.0, 100.0)
        return Int(AugeUtils.clamp(100.0 - normalizedLoad, 0.0, 100.0))
    }

    // ─── 4. BATERÍAS GLOBALES ─────────────────────────────────────────────────

    static func calculateGlobalBatteries(
        history: [WorkoutLog],
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        sleepLogs: [SleepLog] = [],
        nutritionLogs: [NutritionLog] = [],
        feedbacks: [PostSessionFeedback] = [],
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        precomputedMuscles: [String: MuscleRecoveryStatus]? = nil,
        articularBatteries: [ArticularBattery: ArticularBatteryState] = [:]
    ) -> GlobalBatteries {
        let stressLevel = wellbeing?.stressLevel ?? 3
        let nutritionMultiplier = getNutritionMultiplier(settings: settings, nutritionLogs: nutritionLogs, stressLevel: stressLevel)

        let gatedPillarBatteries: [Int] = PILLAR_MUSCLES.compactMap { muscleName in
            let muscleScore: Int?
            if let precomputed = precomputedMuscles {
                muscleScore = precomputed[muscleName]?.recoveryScore
            } else {
                muscleScore = calculateMuscleBattery(
                    muscleName: muscleName, history: history, wellbeing: wellbeing, settings: settings,
                    exerciseDb: exerciseDb, nutritionMultiplier: nutritionMultiplier, sleepLogs: sleepLogs,
                    feedbacks: feedbacks, adaptiveCache: adaptiveCache
                ).recoveryScore
            }
            guard let score = muscleScore else { return nil }

            let relatedArtic = AugeTtcEngine.MUSCLE_TO_ARTICULAR[muscleName] ?? []
            guard !relatedArtic.isEmpty else { return score }

            let articularScores = relatedArtic.compactMap { articularBatteries[$0]?.recoveryScore }
            guard !articularScores.isEmpty else { return score }

            let articularAvg = Double(articularScores.reduce(0, +)) / Double(articularScores.count)
            let coef = 0.6 + 0.4 * (articularAvg / 100.0)
            return max(0, min(100, Int((Double(score) * coef).rounded())))
        }

        let muscularAvg: Int
        if let manual = wellbeing?.manualMuscularBattery {
            muscularAvg = manual
        } else if gatedPillarBatteries.isEmpty {
            muscularAvg = 100
        } else {
            let overallAvg = Double(gatedPillarBatteries.reduce(0, +)) / Double(gatedPillarBatteries.count)
            let sortedAsc = gatedPillarBatteries.sorted()
            let bottomCount = max(1, Int(Double(sortedAsc.count) * 0.25))
            let bottomQuartileAvg = Double(sortedAsc.prefix(bottomCount).reduce(0, +)) / Double(bottomCount)
            muscularAvg = Int(overallAvg * 0.85 + bottomQuartileAvg * 0.15)
        }

        let (cncBattery, _, _) = calculateSystemicFatigue(
            history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, sleepLogs: sleepLogs, feedbacks: feedbacks, adaptiveCache: adaptiveCache
        )
        let spinalBattery = calculateSpinalBattery(
            history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, sleepLogs: sleepLogs, nutritionLogs: nutritionLogs,
            feedbacks: feedbacks, adaptiveCache: adaptiveCache
        )

        let avgMuscleDelta: Double = adaptiveCache.muscleDeltas.isEmpty ? 0.0
            : AugeUtils.clamp(adaptiveCache.muscleDeltas.values.reduce(0, +) / Double(adaptiveCache.muscleDeltas.count), -25.0, 25.0)
        let floor = AugeUtils.physiologicalFloor(settings: settings)

        let decelMuscular = Int(AugeUtils.decelerateBattery(Double(muscularAvg)))
        let finalMuscular = Int(AugeUtils.clamp(Double(max(decelMuscular, floor.muscular)) + avgMuscleDelta, 0.0, 100.0))
        let finalCnc = max(0, min(100, Int(AugeUtils.decelerateBattery(max(Double(cncBattery), Double(floor.cns))))))
        let finalSpinal = max(0, min(100, Int(AugeUtils.decelerateBattery(max(Double(spinalBattery), Double(floor.spinal))))))

        return GlobalBatteries(muscular: finalMuscular, cnc: finalCnc, spinal: finalSpinal)
    }

    // ─── 5. PER-MUSCLE BATTERIES (todos los pilares) ──────────────────────────

    static func getPerMuscleBatteries(
        history: [WorkoutLog],
        wellbeing: DailyWellbeingLog?,
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        sleepLogs: [SleepLog] = [],
        nutritionLogs: [NutritionLog] = [],
        feedbacks: [PostSessionFeedback] = [],
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache()
    ) -> [String: MuscleRecoveryStatus] {
        let stressLevel = wellbeing?.stressLevel ?? 3
        let nutritionMultiplier = getNutritionMultiplier(settings: settings, nutritionLogs: nutritionLogs, stressLevel: stressLevel)

        // Optimización O(N^2): Precalcular capacidades de los músculos antes de iterar
        var precomputedCapacities: [String: Double] = [:]
        for muscle in BATTERY_MUSCLES {
            precomputedCapacities[muscle] = calculateUserWorkCapacity(muscle, history, settings, exerciseDb)
        }

        var result: [String: MuscleRecoveryStatus] = [:]
        for muscle in BATTERY_MUSCLES {
            result[muscle] = calculateMuscleBattery(
                muscleName: muscle, history: history, wellbeing: wellbeing, settings: settings,
                exerciseDb: exerciseDb, nutritionMultiplier: nutritionMultiplier, sleepLogs: sleepLogs,
                feedbacks: feedbacks, adaptiveCache: adaptiveCache,
                precomputedCapacity: precomputedCapacities[muscle]
            )
        }
        return result
    }

    // ─── 6. DASHBOARD Y READINESS DIARIA ─────────────────────────────────────

    static func calculateRecoveryDashboard(
        batteries: GlobalBatteries,
        perMuscle: [String: MuscleRecoveryStatus],
        articularBatteries: [ArticularBattery: ArticularBatteryState],
        wellbeing: DailyWellbeingLog?,
        sleepLogs: [SleepLog] = [],
        recentSessionCount: Int = 0
    ) -> RecoveryDashboard {
        let weightedSleep = calculateWeightedSleepHours(sleepLogs, wellbeing)
        let lowestMuscles = perMuscle.values.sorted { $0.recoveryScore < $1.recoveryScore }.prefix(2)
        let weakestArticular = articularBatteries.sorted { $0.value.recoveryScore < $1.value.recoveryScore }.prefix(2)
        let articularFloor = weakestArticular.isEmpty ? 100
            : Int(weakestArticular.map { Double($0.value.recoveryScore) }.reduce(0, +) / Double(weakestArticular.count))
        let structureScore = max(0, min(100, min(
            batteries.spinal,
            Int(Double(batteries.spinal) * 0.6 + Double(articularFloor) * 0.4)
        )))
        let muscularScore = batteries.muscular
        let systemScore = batteries.cnc
        let displayStructureScore = structureScore

        let baseConfidence: Int
        switch recentSessionCount {
        case 16...: baseConfidence = 82
        case 8...:  baseConfidence = 70
        case 4...:  baseConfidence = 58
        default:    baseConfidence = 44
        }
        let muscularConfidence = max(35, min(95, baseConfidence + (perMuscle.isEmpty ? 0 : 8)))
        let systemConfidence = max(35, min(95, baseConfidence + (sleepLogs.count >= 3 ? 12 : 0) + (wellbeing != nil ? 8 : 0)))
        let structureConfidence = max(35, min(95, baseConfidence + (articularBatteries.isEmpty ? 0 : 12)))

        var muscularCauses: [String] = []
        if let lowest = lowestMuscles.first, lowest.recoveryScore < 80 {
            muscularCauses.append(lowestMuscles.map { "\($0.muscleName) \($0.recoveryScore)%" }.joined(separator: " y "))
        } else {
            muscularCauses.append("Promedio de grupos pilar estable")
        }
        if wellbeing?.manualMuscularBattery != nil { muscularCauses.append("Ajuste manual de readiness") }
        if (wellbeing?.doms ?? 1) >= 4 { muscularCauses.append("Agujetas altas hoy") }
        if weightedSleep < 6.5 { muscularCauses.append("Sueño reciente por debajo de lo ideal") }

        var systemCauses: [String] = []
        if weightedSleep < 5.5 { systemCauses.append("Poco sueño en las últimas noches") }
        else if weightedSleep < 6.5 { systemCauses.append("Sueño subóptimo reciente") }
        else if weightedSleep >= 8.5 { systemCauses.append("Buen colchón de sueño") }
        if wellbeing?.manualNeuralBattery != nil { systemCauses.append("Ajuste manual de readiness") }
        if (wellbeing?.stressLevel ?? 3) >= 4 { systemCauses.append("Estrés alto fuera del entrenamiento") }
        if systemScore < 70 { systemCauses.append("Carga neural reciente acumulada") }

        var structureCauses: [String] = []
        if displayStructureScore < 75 { structureCauses.append("Carga axial reciente elevada") }
        if !weakestArticular.isEmpty {
            structureCauses.append(weakestArticular.map { "\(AugeTtcEngine.articularLabel($0.key)) \($0.value.recoveryScore)%" }.joined(separator: " y "))
        } else {
            structureCauses.append("Sin cuello de botella estructural claro")
        }
        if wellbeing?.manualSpinalBattery != nil { structureCauses.append("Ajuste manual de readiness") }
        if (wellbeing?.doms ?? 1) >= 4 { structureCauses.append("Tejidos aún sensibles hoy") }

        let channels: [RecoveryChannelSnapshot] = [
            RecoveryChannelSnapshot(
                id: .MUSCULAR, title: "Músculos", shortTitle: "Mús.",
                score: muscularScore, band: recoveryBand(muscularScore),
                description: "Promedio del estado de todos tus músculos hoy.",
                action: actionForChannel(.MUSCULAR, muscularScore),
                causes: Array(muscularCauses.prefix(3)), confidence: muscularConfidence, editable: false
            ),
            RecoveryChannelSnapshot(
                id: .SYSTEM, title: "Energía", shortTitle: "En.",
                score: systemScore, band: recoveryBand(systemScore),
                description: "Qué tanta intensidad, coordinación y producción de fuerza toleras hoy.",
                action: actionForChannel(.SYSTEM, systemScore),
                causes: Array(systemCauses.prefix(3)), confidence: systemConfidence, editable: true
            ),
            RecoveryChannelSnapshot(
                id: .STRUCTURE, title: "Columna", shortTitle: "Col.",
                score: displayStructureScore, band: recoveryBand(displayStructureScore),
                description: "Cómo llega hoy tu columna, tus tendones y tus articulaciones a la carga.",
                action: actionForChannel(.STRUCTURE, displayStructureScore),
                causes: Array(structureCauses.prefix(3)), confidence: structureConfidence, editable: true
            ),
        ]

        let systemCh  = channels.first { $0.id == .SYSTEM }!.score
        let muscularCh = channels.first { $0.id == .MUSCULAR }!.score
        let structureCh = channels.first { $0.id == .STRUCTURE }!.score
        let overallScore = max(0, min(100, Int(Double(systemCh) * 0.40 + Double(muscularCh) * 0.35 + Double(structureCh) * 0.25)))

        let headline: String
        switch recoveryBand(overallScore) {
        case .HIGH:     headline = "Listo para empujar"
        case .NORMAL:   headline = "Buen estado para entrenar"
        case .MODERATE: headline = "Día para moderar"
        case .LOW:      headline = "Llega cargado"
        case .CRITICAL: headline = "Prioriza recuperación"
        }
        let recommendation: String
        switch recoveryBand(overallScore) {
        case .HIGH:     recommendation = "Hoy puedes entrenar normal o fuerte si la sesión lo pide."
        case .NORMAL:   recommendation = "Hoy conviene entrenar normal, dejando algo en reserva."
        case .MODERATE: recommendation = "Hoy conviene moderar volumen o intensidad según el ring más bajo."
        case .LOW:      recommendation = "Hoy conviene priorizar técnica, variantes estables y menos carga."
        case .CRITICAL: recommendation = "Hoy conviene descargar o hacer solo trabajo liviano."
        }
        let confidenceAverage = Int(channels.map { Double($0.confidence) }.reduce(0, +) / Double(channels.count))
        let limitingChannel = channels.min { $0.score < $1.score }
        let summary = limitingChannel.map { "\($0.title) es hoy el factor limitante (\($0.score)%)." } ?? "Tu estado está equilibrado."

        return RecoveryDashboard(
            overallScore: overallScore, headline: headline, summary: summary,
            recommendation: recommendation, confidenceLabel: confidenceLabel(confidenceAverage),
            channels: channels
        )
    }

    static func calculateDailyReadiness(
        dashboard: RecoveryDashboard,
        wellbeing: DailyWellbeingLog?
    ) -> AugeReadinessVerdict {
        let score = max(0, min(100, dashboard.overallScore))
        let label: String
        let color: ReadinessColor
        switch recoveryBand(score) {
        case .HIGH:     label = "Óptimo para entrenar"; color = .GREEN
        case .NORMAL:   label = "Buen estado";          color = .GREEN
        case .MODERATE: label = "Moderado";             color = .YELLOW
        case .LOW:      label = "Cargado";              color = .YELLOW
        case .CRITICAL: label = "Descanso recomendado"; color = .RED
        }
        var details = Array(Set(dashboard.channels.flatMap { $0.causes }.prefix(4)))
        if (wellbeing?.doms ?? 1) >= 4 { details.append("Agujetas altas reportadas") }
        details = Array(Set(details))

        return AugeReadinessVerdict(
            score: score, label: label, color: color, details: details,
            action: dashboard.recommendation, confidenceLabel: dashboard.confidenceLabel
        )
    }

    // ─── 7. ENCUESTAS PENDIENTES ──────────────────────────────────────────────

    static func checkPendingSurveys(
        history: [WorkoutLog],
        feedbacks: [PostSessionFeedback]
    ) -> PendingQuestionnaire? {
        let now = nowMs()
        let twoHours: Int64 = 2 * 3_600_000
        let fortyEightHours: Int64 = 48 * 3_600_000

        return history.first { log in
            let timeSince = now - AugeUtils.logDateMs(log)
            return timeSince > twoHours
                && timeSince < fortyEightHours
                && !feedbacks.contains { $0.logId == log.id }
        }.map { log in
            PendingQuestionnaire(
                logId: log.id,
                sessionName: log.sessionName,
                muscleGroups: [],
                stillPresentDiscomfortIds: log.stillPresentDiscomfortIds,
                scheduledTimeMs: AugeUtils.logDateMs(log) + 24 * 3_600_000
            )
        }
    }

    // ─── 8. RECOMENDACIÓN DE SUEÑO ───────────────────────────────────────────

    static func calculateSleepRecommendations(
        settings: Settings,
        wellbeing: DailyWellbeingLog?,
        todayLog: WorkoutLog?
    ) -> SleepRecommendation {
        let base = settings.sleepTargetHours
        var extra = 0.0
        var reasons: [String] = []

        if let log = todayLog {
            let volume = log.completedExercises.count * 3
            if volume > 15 || (log.sessionStressScore ?? 0.0) > 50 {
                extra += 0.75; reasons.append("Alta carga neural")
            } else if volume > 10 {
                extra += 0.5; reasons.append("Volumen moderado")
            }
        }

        if let w = wellbeing {
            if w.workIntensity == .HIGH || w.studyIntensity == .HIGH {
                extra += 0.5; reasons.append("Carga cognitiva alta")
            }
            if w.stressLevel >= 4 {
                extra += 0.25; reasons.append("Estrés elevado")
            }
        }

        return SleepRecommendation(
            targetHours: max(6.0, min(10.0, base + extra)),
            reasons: reasons
        )
    }

    // ─── 9. MULTIPLICADOR NUTRICIONAL ─────────────────────────────────────────

    static func getNutritionMultiplier(
        settings: Settings,
        nutritionLogs: [NutritionLog] = [],
        stressLevel: Int = 3
    ) -> Double {
        guard settings.algorithmSettings.augeEnableNutritionTracking else { return 1.0 }
        let activePlan = NutritionRepository.shared.activeNutritionPlan
        return NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs: nutritionLogs,
            settings: settings,
            activePlan: activePlan,
            stressLevel: stressLevel
        ).recoveryTimeMultiplier
    }

    // Expose PILLAR_MUSCLES para la UI
    static var pillarMuscles: [String] { PILLAR_MUSCLES }

    // ─── 10. RANKINGS DE SESIONES ─────────────────────────────────────────────

    /// Calcula el ranking de sesiones del historial ordenadas por drenaje total.
    static func calculateSessionDrainRankings(
        history: [WorkoutLog],
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:]
    ) -> [SessionDrainRanking] {
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)
        return history.map { log in
            var totalMuscular = 0.0
            var totalCns = 0.0
            var totalSpinal = 0.0

            for ex in log.completedExercises {
                let dbInfo = resolveDbInfo(ex, exerciseDb)
                let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?? AugeMetrics()
                let densityMult = densityMultiplierForCompletedExercise(ex)
                var accumulated = 0

                for s in ex.sets {
                    guard AugeFatigueEngine.isSetEffective(s) else { continue }
                    accumulated += 1
                    let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                        set: s, metrics: metrics, tanks: tanks,
                        accumulatedSets: accumulated, restTime: ex.restTime,
                        densityMultiplier: densityMult
                    )
                    totalMuscular += drain.muscularDrainPct
                    totalCns += drain.cnsDrainPct
                    totalSpinal += drain.spinalDrainPct
                }
            }

            let normFactor = 0.01
            return SessionDrainRanking(
                logId: log.id,
                sessionName: log.sessionName,
                date: String(log.date.prefix(10)),
                totalDrain: (totalMuscular * 0.35 + totalCns * 0.40 + totalSpinal * 0.25) * normFactor,
                cnsDrain: totalCns * normFactor,
                muscularDrain: totalMuscular * normFactor,
                spinalDrain: totalSpinal * normFactor
            )
        }.sorted { $0.totalDrain > $1.totalDrain }
    }

    /// Calcula el ranking de ejercicios agregado por drenaje promedio.
    static func calculateExerciseDrainRankings(
        history: [WorkoutLog],
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:]
    ) -> [ExerciseDrainRanking] {
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)

        struct DrainAccum {
            var muscular: Double = 0.0
            var cns: Double = 0.0
            var spinal: Double = 0.0
            var count: Int = 0
            var name: String = ""
            var dbId: String? = nil
        }
        var accumMap: [String: DrainAccum] = [:]

        for log in history {
            for ex in log.completedExercises {
                let key = (ex.exerciseDbId ?? ex.exerciseName).lowercased().trimmingCharacters(in: .whitespaces)
                let dbInfo = resolveDbInfo(ex, exerciseDb)
                let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?? AugeMetrics()
                let densityMult = densityMultiplierForCompletedExercise(ex)
                var accumulated = 0
                var sessMuscular = 0.0
                var sessCns = 0.0
                var sessSpinal = 0.0

                for s in ex.sets {
                    guard AugeFatigueEngine.isSetEffective(s) else { continue }
                    accumulated += 1
                    let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                        set: s, metrics: metrics, tanks: tanks,
                        accumulatedSets: accumulated, restTime: ex.restTime,
                        densityMultiplier: densityMult
                    )
                    sessMuscular += drain.muscularDrainPct
                    sessCns += drain.cnsDrainPct
                    sessSpinal += drain.spinalDrainPct
                }

                var entry = accumMap[key] ?? DrainAccum(name: ex.exerciseName, dbId: ex.exerciseDbId)
                entry.muscular += sessMuscular
                entry.cns += sessCns
                entry.spinal += sessSpinal
                entry.count += 1
                accumMap[key] = entry
            }
        }

        return accumMap.values.map { a in
            let n = max(1.0, Double(a.count))
            let normFactor = 0.01
            return ExerciseDrainRanking(
                exerciseName: a.name,
                exerciseDbId: a.dbId,
                overallDrain: ((a.muscular / n * 0.35) + (a.cns / n * 0.40) + (a.spinal / n * 0.25)) * normFactor,
                muscularDrain: (a.muscular / n) * normFactor,
                cnsDrain: (a.cns / n) * normFactor,
                spinalDrain: (a.spinal / n) * normFactor,
                sessionCount: a.count
            )
        }.sorted { $0.overallDrain > $1.overallDrain }
    }

    /// Calcula estadísticas personales de recuperación del último mes.
    static func calculatePersonalRecoveryStats(
        history: [WorkoutLog],
        settings: Settings,
        exerciseDb: [String: ExerciseMuscleInfo] = [:]
    ) -> PersonalRecoveryStats {
        let cutoffMs = nowMs() - 30 * 24 * 3_600_000
        let recent = history.filter { AugeUtils.logDateMs($0) >= cutoffMs }

        guard !recent.isEmpty else {
            return PersonalRecoveryStats(
                avgRecoveryHoursOverall: 48.0, avgRecoveryHoursMuscular: 48.0,
                avgRecoveryHoursCns: 36.0, avgRecoveryHoursSpinal: 52.0,
                fastestRecoverySession: nil, slowestRecoverySession: nil, sampleCount: 0
            )
        }

        struct SessionRecovery { let name: String; let muscular: Double; let cns: Double; let spinal: Double }
        let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: settings)

        let sessionStats: [SessionRecovery] = recent.map { log in
            var totalMuscular = 0.0
            var totalCns = 0.0
            var totalSpinal = 0.0

            for ex in log.completedExercises {
                let dbInfo = resolveDbInfo(ex, exerciseDb)
                let metrics = AugeFatigueEngine.getDynamicAugeMetrics(ex.exerciseName, dbInfo?.equipment, dbInfo) ?? AugeMetrics()
                let densityMult = densityMultiplierForCompletedExercise(ex)
                var accumulated = 0
                for s in ex.sets {
                    guard AugeFatigueEngine.isSetEffective(s) else { continue }
                    accumulated += 1
                    let drain = AugeFatigueEngine.calculateSetBatteryDrain(
                        set: s, metrics: metrics, tanks: tanks,
                        accumulatedSets: accumulated, restTime: ex.restTime,
                        densityMultiplier: densityMult
                    )
                    totalMuscular += drain.muscularDrainPct
                    totalCns += drain.cnsDrainPct
                    totalSpinal += drain.spinalDrainPct
                }
            }

            let muscularHours = AugeUtils.clamp(24.0 + totalMuscular * 0.5, 24.0, 96.0)
            let cnsHours = AugeUtils.clamp(12.0 + totalCns * 0.3, 12.0, 48.0)
            let spinalHours = AugeUtils.clamp(24.0 + totalSpinal * 0.4, 24.0, 72.0)
            return SessionRecovery(name: log.sessionName, muscular: muscularHours, cns: cnsHours, spinal: spinalHours)
        }

        let count = Double(sessionStats.count)
        let avgMuscular = sessionStats.map { $0.muscular }.reduce(0, +) / count
        let avgCns = sessionStats.map { $0.cns }.reduce(0, +) / count
        let avgSpinal = sessionStats.map { $0.spinal }.reduce(0, +) / count
        let avgOverall = avgMuscular * 0.35 + avgCns * 0.40 + avgSpinal * 0.25

        let fastest = sessionStats.min { ($0.muscular + $0.cns + $0.spinal) < ($1.muscular + $1.cns + $1.spinal) }?.name
        let slowest = sessionStats.max { ($0.muscular + $0.cns + $0.spinal) < ($1.muscular + $1.cns + $1.spinal) }?.name

        return PersonalRecoveryStats(
            avgRecoveryHoursOverall: avgOverall, avgRecoveryHoursMuscular: avgMuscular,
            avgRecoveryHoursCns: avgCns, avgRecoveryHoursSpinal: avgSpinal,
            fastestRecoverySession: fastest, slowestRecoverySession: slowest,
            sampleCount: recent.count
        )
    }
}
