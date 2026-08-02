import Foundation

// ─── Supporting types (parity with AugeModels.kt) ────────────────────────────

// Redundant structural readiness, alert, and suggestion types removed (defined in AugeModels.swift)


// ─── Engine ──────────────────────────────────────────────────────────────────

enum AugeTtcEngine {

    private static let TTC_MAX = 5.0
    private static let TENDON_CAPACITY_BASE = 80.0
    private static let TENDON_RECOVERY_HIGH_TTC = 60.0
    private static let TENDON_RECOVERY_STD = 48.0
    private static let CUMULATIVE_PENALTY = 0.1
    private static let IMBALANCE_THRESHOLD = 30
    private static let DISCOMFORT_ARTICULAR_BASE_PENALTY = 10.0
    private static let DISCOMFORT_UNRESOLVED_BASE_PENALTY = 15.0
    private static let DISCOMFORT_UNRESOLVED_DECAY_HOURS = 168.0

    private static let allBatteries: [ArticularBattery] = [
        .SHOULDER, .ELBOW, .KNEE, .HIP, .ANKLE, .CERVICAL, .LUMBAR,
    ]

    // ─── Músculo → baterías articulares relacionadas ──────────────────────────

    static let MUSCLE_TO_ARTICULAR: [String: [ArticularBattery]] = [
        "Deltoides Anterior":  [.SHOULDER],
        "Deltoides Lateral":   [.SHOULDER],
        "Deltoides Posterior": [.SHOULDER],
        "Deltoides":           [.SHOULDER],
        "Pectorales":          [.SHOULDER],
        "Dorsales":            [.SHOULDER],
        "Bíceps":              [.SHOULDER, .ELBOW],
        "Tríceps":             [.ELBOW],
        "Antebrazo":           [.ELBOW],
        "Cuádriceps":          [.KNEE, .HIP],
        "Isquiosurales":       [.KNEE, .HIP],
        "Glúteos":             [.HIP],
        "Aductores":           [.HIP],
        "Pantorrillas":        [.ANKLE],
        "Trapecio":            [.SHOULDER, .CERVICAL],
        "Cuello":              [.CERVICAL],
        "Erectores Espinales": [.LUMBAR],
        "Core":                [.HIP],

        "Hombros":             [.SHOULDER],
        "Cuadriceps":          [.KNEE, .HIP],
        "Abdomen":             [.HIP],

        "Romboide":            [.SHOULDER],
        "Redondo Mayor":       [.SHOULDER],
        "Redondo Menor":       [.SHOULDER],
        "Infraespinoso":       [.SHOULDER],
        "Supraespinoso":       [.SHOULDER],
        "Subescapular":        [.SHOULDER],
        "Serrato Anterior":    [.SHOULDER],
        "Costales":            [.LUMBAR],
        "Psoas":               [.HIP],
        "Lumbar":              [.LUMBAR],
    ]

    static func articularLabel(_ ab: ArticularBattery) -> String {
        switch ab {
        case .SHOULDER: return "Hombro"
        case .ELBOW:    return "Codo"
        case .KNEE:     return "Rodilla"
        case .HIP:      return "Cadera"
        case .ANKLE:    return "Tobillo"
        case .CERVICAL: return "Cervical"
        case .LUMBAR:   return "Lumbar"
        }
    }

    // ─── 1. TTC por ejercicio ─────────────────────────────────────────────────

    static func calculateTTC(_ exerciseName: String, _ equipment: String? = nil) -> Double {
        let name = exerciseName.lowercased()
        let eq = equipment?.lowercased() ?? ""

        let baseTtc: Double = {
            if name.contains("snatch") || name.contains("arrancada") || name.contains("arranque") { return 3.0 }
            if name.contains("clean") && (name.contains("power") || name.contains("cargada")) { return 3.0 }
            if name.contains("jerk") || name.contains("envión") { return 3.0 }
            if name.contains("salto") || name.contains("jump") || name.contains("box jump") { return 3.0 }
            if name.contains("bound") || name.contains("hop") || name.contains("pliométric") { return 3.0 }
            if name.contains("peso muerto") || name.contains("deadlift") { return 2.0 }
            if name.contains("sentadilla") || name.contains("squat") { return 2.0 }
            if name.contains("press banca") || name.contains("bench press") { return 2.0 }
            if name.contains("press militar") || name.contains("ohp") { return 2.0 }
            if name.contains("dominada") || name.contains("pull-up") || name.contains("pullup") { return 2.0 }
            if name.contains("remo") || name.contains("row") { return 2.0 }
            if name.contains("hip thrust") || name.contains("puente") { return 1.5 }
            if name.contains("zancada") || name.contains("lunge") { return 2.0 }
            if name.contains("curl") || name.contains("extensión") { return 1.0 }
            return 1.0
        }()

        let eqMod: Double = {
            if eq == "máquina" || eq == "machine" || eq == "polea" || eq == "cable" { return 0.8 }
            if eq == "barra" || eq == "barbell" || eq == "mancuerna" || eq == "dumbbell" || eq == "kettlebell" { return 1.2 }
            return 1.0
        }()

        let contraMod: Double = {
            if name.contains("nórdico") || name.contains("nordic") { return 1.8 }
            if name.contains("excéntrico") || name.contains("eccentric") { return 1.8 }
            if name.contains("plancha") || name.contains("plank") { return 0.5 }
            if name.contains("isométric") || name.contains("wall sit") { return 0.5 }
            if name.contains("pausa") || name.contains("paused") { return 1.1 }
            return 1.0
        }()

        return min(TTC_MAX, baseTtc * eqMod * contraMod)
    }

    // ─── 2. Drenaje tendinoso por set ─────────────────────────────────────────

    static func calculateSetTendonDrain(
        set: CompletedSet,
        ttc: Double,
        articularWeights: [ArticularBattery: Double]
    ) -> [ArticularBattery: Double] {
        var result = Dictionary(uniqueKeysWithValues: allBatteries.map { ($0, 0.0) })
        if ttc <= 0.0 { return result }

        let rpe = getEffectiveRPE(set)
        let intensityMult: Double = {
            if rpe >= 10.0 { return 1.4 }
            if rpe >= 8.0  { return 1.0 }
            if rpe >= 6.0  { return 0.7 }
            return 0.4
        }()

        let repsFactor = min(1.5, 0.1 + Double(set.reps) / 15.0)
        let techniqueMult = min(2.5, 1.0 + 0.5 * Double(set.dropSets.count) + 0.3 * Double(set.restPauses.count))
        let baseDrain = ttc * repsFactor * intensityMult * techniqueMult * 2.5

        let totalWeight = articularWeights.values.reduce(0, +)
        let divisor = totalWeight > 0.0 ? totalWeight : 1.0
        for (ab, w) in articularWeights {
            result[ab] = baseDrain * (w / divisor)
        }
        return result
    }

    // ─── 3. Baterías articulares desde historial ──────────────────────────────

    static func calculateArticularBatteries(
        history: [WorkoutLog],
        exerciseDb: [String: ExerciseMuscleInfo] = [:],
        feedbacks: [PostSessionFeedback] = [],
        wellbeing: DailyWellbeingLog? = nil
    ) -> [ArticularBattery: ArticularBatteryState] {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let tenDaysMs: Int64 = 10 * 24 * 3600 * 1000

        let relevantLogs = history
            .filter { AugeUtils.logDateMs(log: $0) > now - tenDaysMs }
            .sorted { AugeUtils.logDateMs(log: $0) < AugeUtils.logDateMs(log: $1) }

        var accumulatedStress = Dictionary(uniqueKeysWithValues: allBatteries.map { ($0, 0.0) })
        var lastDrainTime = Dictionary(uniqueKeysWithValues: allBatteries.map { ($0, Int64(0)) })
        let recoveryWindowMs: Int64 = 72 * 3600 * 1000

        var discomfortStress = Dictionary(uniqueKeysWithValues: allBatteries.map { ($0, 0.0) })
        for log in relevantLogs {
            let logTime = AugeUtils.logDateMs(log: log)
            let hoursSince = max(0.0, Double(now - logTime) / 3_600_000.0)
            let decay = AugeUtils.safeExp(-(hoursSince / 96.0))
            for report in log.postExerciseReports {
                let quality = min(10, max(1, report.technicalQuality))
                let qualityMult: Double = {
                    if quality <= 4 { return 1.30 }
                    if quality <= 6 { return 1.10 }
                    if quality >= 9 { return 0.85 }
                    return 1.0
                }()
                for discomfortId in Set(report.discomfortIds.filter { $0 != "none" }) {
                    guard let entry = DISCOMFORT_CATALOG_BY_ID[discomfortId] else { continue }
                    let targets = entry.relatedArticular
                    if targets.isEmpty { continue }
                    let split = 1.0 / Double(targets.count)
                    for ab in targets {
                        discomfortStress[ab] = (discomfortStress[ab] ?? 0.0) +
                            (DISCOMFORT_ARTICULAR_BASE_PENALTY * qualityMult * split * decay)
                    }
                }
            }
        }

        if let preWorkoutDiscomforts = wellbeing?.preWorkoutDiscomforts {
            for discomfortId in Set(preWorkoutDiscomforts.filter { $0 != "none" }) {
                guard let entry = DISCOMFORT_CATALOG_BY_ID[discomfortId] else { continue }
                let targets = entry.relatedArticular
                if targets.isEmpty { continue }
                let split = 1.0 / Double(targets.count)
                for ab in targets {
                    discomfortStress[ab] = (discomfortStress[ab] ?? 0.0) +
                        (DISCOMFORT_ARTICULAR_BASE_PENALTY * 1.0 * split)
                }
            }
        }

        for log in relevantLogs {
            let logTime = AugeUtils.logDateMs(log: log)
            let hoursSince = max(0.0, Double(now - logTime) / 3_600_000.0)

            var sessionDrain = Dictionary(uniqueKeysWithValues: allBatteries.map { ($0, 0.0) })

            for ex in log.completedExercises {
                let info = (ex.exerciseDbId ?? ex.exerciseId)
                    .flatMap { exerciseDb[$0.lowercased()] }

                let weights = getArticularWeightsForExercise(info)
                if weights.values.allSatisfy({ $0 == 0.0 }) { continue }

                let ttc = calculateTTC(ex.exerciseName, info?.equipment)

                for s in ex.sets {
                    guard isSetEffective(s) else { continue }
                    let drain = calculateSetTendonDrain(set: s, ttc: ttc, articularWeights: weights)
                    for (ab, d) in drain {
                        sessionDrain[ab] = (sessionDrain[ab] ?? 0.0) + d
                    }
                }
            }

            for ab in allBatteries {
                var d = sessionDrain[ab] ?? 0.0
                let last = lastDrainTime[ab] ?? 0
                if d > 0 && last > 0 && logTime - last < recoveryWindowMs {
                    d *= 1 + CUMULATIVE_PENALTY
                }
                if d > 0 { lastDrainTime[ab] = logTime }
                sessionDrain[ab] = d
            }

            let totalTtc = sessionDrain.values.reduce(0, +)
            let drainedCnt = sessionDrain.values.filter { $0 > 0 }.count
            let avgTtc = drainedCnt > 0 ? totalTtc / Double(drainedCnt) : 0.0
            let recoveryHours = avgTtc > 3.0 ? TENDON_RECOVERY_HIGH_TTC : TENDON_RECOVERY_STD
            let k = 2.0 / recoveryHours

            for ab in allBatteries {
                let stress = sessionDrain[ab] ?? 0.0
                if stress <= 0.0 { continue }
                let effectiveHours: Double
                if hoursSince < 24.0 {
                    effectiveHours = hoursSince * 0.05
                } else {
                    effectiveHours = 1.2 + (hoursSince - 24.0)
                }
                let remaining = stress * AugeUtils.safeExp(-k * effectiveHours)
                accumulatedStress[ab] = (accumulatedStress[ab] ?? 0.0) + remaining
            }
        }

        var unresolvedStress = Dictionary(uniqueKeysWithValues: allBatteries.map { ($0, 0.0) })
        for fb in feedbacks {
            let fbDateMs: Int64 = {
                let fmt = DateFormatter()
                fmt.dateFormat = "yyyy-MM-dd"
                fmt.locale = Locale(identifier: "en_US")
                if let date = fmt.date(from: fb.date) {
                    return Int64(date.timeIntervalSince1970 * 1000)
                }
                return 0
            }()
            if fbDateMs <= 0 { continue }
            let hoursSince = max(0.0, Double(now - fbDateMs) / 3_600_000.0)
            let decayFactor = max(0.0, 1.0 - hoursSince / DISCOMFORT_UNRESOLVED_DECAY_HOURS)
            if decayFactor <= 0.0 { continue }
            for id in fb.unresolvedDiscomfortIds {
                guard let entry = DISCOMFORT_CATALOG_BY_ID[id] else { continue }
                let targets = entry.relatedArticular
                if targets.isEmpty { continue }
                let split = 1.0 / Double(targets.count)
                for ab in targets {
                    unresolvedStress[ab] = (unresolvedStress[ab] ?? 0.0) +
                        (DISCOMFORT_UNRESOLVED_BASE_PENALTY * decayFactor * split)
                }
            }
        }

        return Dictionary(uniqueKeysWithValues: allBatteries.map { ab in
            let acc = accumulatedStress[ab] ?? 0.0
            let discomfortAcc = discomfortStress[ab] ?? 0.0
            let unresolvedAcc = unresolvedStress[ab] ?? 0.0
            let totalAcc = acc + discomfortAcc + unresolvedAcc
            let battery = AugeUtils.clamp(100.0 - (totalAcc / TENDON_CAPACITY_BASE) * 100.0, 0.0, 100.0)
            let score = Int(battery)
            let status: ArticularStatus = {
                if battery < 40.0 { return .EXHAUSTED }
                if battery < 85.0 { return .RECOVERING }
                return .OPTIMAL
            }()
            let hoursToRecovery: Int = {
                if battery < 90.0 && acc > 0.0 {
                    let targetStress = ((100.0 - 90.0) / 100.0) * TENDON_CAPACITY_BASE
                    let kFinal = 2.0 / TENDON_RECOVERY_STD
                    return max(0, Int(-log(targetStress / totalAcc) / kFinal))
                }
                return 0
            }()
            return (ab, ArticularBatteryState(
                recoveryScore: score,
                estimatedHoursToRecovery: hoursToRecovery,
                status: status,
                accumulatedStress: totalAcc
            ))
        })
    }

    // ─── 4. Readiness estructural combinada ──────────────────────────────────

    static func getStructuralReadinessForMuscle(
        muscleName: String,
        muscleBattery: Int,
        articularBatteries: [ArticularBattery: ArticularBatteryState]
    ) -> StructuralReadinessBreakdown {
        let related = MUSCLE_TO_ARTICULAR[muscleName] ?? []
        let articularScore: Int = {
            if related.isEmpty { return muscleBattery }
            let scores = related.compactMap { articularBatteries[$0]?.recoveryScore }
            guard !scores.isEmpty else { return muscleBattery }
            return Int(Double(scores.reduce(0, +)) / Double(scores.count))
        }()

        let combined = Int((Double(muscleBattery) + Double(articularScore)) / 2.0)
        let limiting = min(muscleBattery, articularScore)

        return StructuralReadinessBreakdown(
            muscleName: muscleName,
            muscleBattery: muscleBattery,
            articularBattery: articularScore,
            combinedBattery: combined,
            limitingBattery: limiting,
            relatedArticular: related
        )
    }

    static func getStructuralReadinessForMuscles(
        perMuscle: [String: MuscleRecoveryStatus],
        articularBatteries: [ArticularBattery: ArticularBatteryState],
        muscleFilter: [String] = []
    ) -> [StructuralReadinessBreakdown] {
        let muscles = muscleFilter.isEmpty ? Array(perMuscle.keys) : muscleFilter
        return muscles.map { name in
            getStructuralReadinessForMuscle(
                muscleName: name,
                muscleBattery: perMuscle[name]?.recoveryScore ?? 100,
                articularBatteries: articularBatteries
            )
        }
    }

    // ─── 5. Alertas de desequilibrio tendinoso ────────────────────────────────

    static func getTendonImbalanceAlerts(
        perMuscle: [String: MuscleRecoveryStatus],
        articularBatteries: [ArticularBattery: ArticularBatteryState],
        sessionMuscles: [String] = []
    ) -> [TendonImbalanceAlert] {
        let readiness = getStructuralReadinessForMuscles(
            perMuscle: perMuscle,
            articularBatteries: articularBatteries,
            muscleFilter: sessionMuscles
        )
        var alerts: [TendonImbalanceAlert] = []

        for item in readiness {
            for ab in item.relatedArticular {
                guard let abScore = articularBatteries[ab]?.recoveryScore else { continue }
                let gap = item.muscleBattery - abScore
                if gap <= IMBALANCE_THRESHOLD { continue }

                let label = articularLabel(ab)
                alerts.append(TendonImbalanceAlert(
                    type: abScore < 40 ? .DANGER : .WARNING,
                    muscleLabel: item.muscleName,
                    articularLabel: label,
                    muscleBattery: item.muscleBattery,
                    articularBattery: abScore,
                    gap: gap,
                    message: "Tu lectura muscular de \(item.muscleName) va mejor (\(item.muscleBattery)%), pero el tejido de \(label) sigue atrasado (\(abScore)%). Usa la media combinada antes de subir cargas o meter explosividad hoy."
                ))
            }
        }
        return alerts
    }

    static func getTendonCompensationSuggestions(
        articularBatteries: [ArticularBattery: ArticularBatteryState],
        sessionArticular: [ArticularBattery] = []
    ) -> [TendonCompensationSuggestion] {
        let filter = sessionArticular.isEmpty ? Set(allBatteries) : Set(sessionArticular)
        let low = articularBatteries.filter { (ab, s) in filter.contains(ab) && s.recoveryScore < 50 }
        let veryLow = low.filter { (_, s) in s.recoveryScore < 30 }

        var suggestions: [TendonCompensationSuggestion] = []
        if !low.isEmpty {
            suggestions.append(TendonCompensationSuggestion(
                type: .NUTRITION,
                title: "Soporte nutricional",
                message: "Colageno hidrolizado + Vitamina C 30-60 min antes del entrenamiento puede mejorar la sintesis de colageno en tendones y acelerar la recuperacion (~10%)."
            ))
        }
        if !veryLow.isEmpty {
            suggestions.append(TendonCompensationSuggestion(
                type: .BIOMECHANICAL,
                title: "Ajuste biomecanico",
                message: "Considera sustituir ejercicios pesados o pliometricos por alternativas isometricas o de bajo TTC para no agravar el dano tendinoso."
            ))
        }
        return suggestions
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private static func getArticularWeightsForExercise(
        _ info: ExerciseMuscleInfo?
    ) -> [ArticularBattery: Double] {
        var result: [ArticularBattery: Double] = [:]
        guard let info = info else { return result }

        for involved in info.involvedMuscles {
            if involved.role != .PRIMARY && involved.role != .SECONDARY { continue }
            guard let articulars = MUSCLE_TO_ARTICULAR[involved.muscle] else { continue }
            for ab in articulars {
                let roleMult = involved.role == .PRIMARY ? 1.0 : 0.6
                result[ab] = (result[ab] ?? 0.0) + roleMult
            }
        }
        return result
    }
}
