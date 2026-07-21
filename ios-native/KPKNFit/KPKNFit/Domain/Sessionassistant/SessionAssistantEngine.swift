import Foundation

// MARK: - AugeMetrics (from data.models.AugeModels.kt – temporary definition until AugeFatigueEngine is translated)

// Redundant AugeMetrics structure removed (defined in AugeModels.swift)


// MARK: - SessionAssistantEngine

enum SessionAssistantEngine {

    // ─── Helper Types ────────────────────────────────────────────────────────

    struct VolumeCalculationResult {
        var volumeMap: [String: MuscularVolumeAccumulator]
        var roleMap: [String: MuscleRoleBreakdown]
        var recommendationContext: [String: MuscleRecommendationContext]
        var totalSets: Int
        var totalSpinalLoad: Double
        var elbowStress: Int
        var kneeStress: Int
        var averageRpe: Double
        var exerciseInsights: [ExerciseInsightData]
    }

    struct ExerciseInsightData {
        var exerciseId: String
        var name: String
        var muscular: Double
        var cns: Double
        var spinal: Double
    }

    // ─── Evaluate ────────────────────────────────────────────────────────────

    static func evaluate(
        input: SessionAssistantInput,
        allTemplates: [SessionTemplate] = []
    ) -> SessionAssistantReport {
        let volumeResult = calcularVolumenPorMusculo(input: input)
        let drain = calcularDrenajeEstimado(input: input)
        let thresholds = buildVolumeThresholds(input: input, sessionVolumeByMuscle: volumeResult.volumeMap)
        let ajustes = generarAjustesPorRings(input: input, volume: volumeResult, drain: drain)

        var totalRestSeconds = 0
        for ex in input.allExercisesInSession {
            let exRest = ex.restTime ?? 90
            let setsCount = ex.sets.count
            if setsCount > 0 {
                totalRestSeconds += exRest * (setsCount - 1)
            }
        }
        let exerciseCount = input.allExercisesInSession.count
        if exerciseCount > 1 {
            totalRestSeconds += (exerciseCount - 1) * 60
        }

        let defaultSetupTime = 120
        let workSecondsPerSet = 45
        let totalWorkSeconds = volumeResult.totalSets * workSecondsPerSet

        var totalSetupSeconds = 0
        for ex in input.allExercisesInSession {
            let info = resolveExerciseInfo(exercise: ex, exerciseIndex: input.exerciseIndex)
            totalSetupSeconds += info?.setupTime ?? defaultSetupTime
        }
        let estimatedWorkSeconds = totalWorkSeconds + totalSetupSeconds
        let duracion = (totalRestSeconds + estimatedWorkSeconds + 59) / 60

        let timeAjustes: [AssistantSuggestion]
        if let target = input.targetDurationMinutes, duracion > target {
            timeAjustes = buildTimeSuggestions(input: input, overageMinutes: duracion - target)
        } else {
            timeAjustes = []
        }

        return SessionAssistantReport(
            veredicto: .optimal,
            scoreEstimado: 0,
            riesgos: [],
            ajustes: ajustes + timeAjustes,
            oportunidades: [],
            tarjetasFantasma: [],
            plantillasCompatibles: [],
            volumenPorMusculo: volumeResult.volumeMap.mapValues { $0.flat },
            umbralesPorMusculo: thresholds,
            drenajeEstimado: drain,
            duracionEstimada: duracion,
            resumenTexto: "",
            totalRestSeconds: totalRestSeconds,
            estimatedWorkSeconds: estimatedWorkSeconds
        )
    }

    // ─── Time Overage Suggestions ────────────────────────────────────────────

    private static let MAX_REDUCE_REST_SUGGESTIONS = 2
    private static let MAX_SUPERSET_SUGGESTIONS = 2
    private static let MAX_DROPSET_SUGGESTIONS = 2

    private static func buildTimeSuggestions(
        input: SessionAssistantInput,
        overageMinutes: Int
    ) -> [AssistantSuggestion] {
        var suggestions = [AssistantSuggestion]()
        let exercises = input.allExercisesInSession

        let avgRest = exercises.map { Double($0.restTime ?? 90) }.reduce(0, +) / Double(exercises.count)
        if avgRest > 45 && suggestions.count < MAX_REDUCE_REST_SUGGESTIONS {
            suggestions.append(
                AssistantSuggestion(
                    id: "time_reduce_rest",
                    type: .reduceRestTime,
                    title: "Reducir descansos en 15s",
                    message: "Ahorro estimado de ~\((exercises.count * 15) / 60) min. Sesión excede por \(overageMinutes) min el límite.",
                    priority: 30
                )
            )
        }

        if exercises.count >= 2 && suggestions.count < MAX_SUPERSET_SUGGESTIONS + 1 {
            for i in 0..<(exercises.count - 1) {
                let ex = exercises[i]
                let next = exercises[i + 1]
                if ex.sets.contains(where: { $0.isDropSet || $0.isRestPause }) { continue }
                if next.sets.contains(where: { $0.isDropSet || $0.isRestPause }) { continue }
                suggestions.append(
                    AssistantSuggestion(
                        id: "time_superset_\(ex.id)",
                        type: .convertToSuperset,
                        title: "Superserie con siguiente ejercicio",
                        message: "Ahorra ~90s por ronda de descanso eliminada. Sesión excede por \(overageMinutes) min.",
                        exerciseId: ex.id,
                        priority: 40
                    )
                )
                if suggestions.count >= MAX_SUPERSET_SUGGESTIONS + 1 { break }
            }
        }

        if suggestions.count < MAX_DROPSET_SUGGESTIONS + MAX_SUPERSET_SUGGESTIONS + 1 {
            for ex in exercises {
                let multiSet = ex.sets.count > 1
                let notAlreadyDrop = !ex.sets.contains(where: { $0.isDropSet })
                let notRestPause = !ex.sets.contains(where: { $0.isRestPause })
                if !multiSet || !notAlreadyDrop || !notRestPause { continue }
                suggestions.append(
                    AssistantSuggestion(
                        id: "time_dropset_\(ex.id)",
                        type: .convertToDropset,
                        title: "Convertir a dropset",
                        message: "Reduce descansos intra-ejercicio a ~10s. Sesión excede por \(overageMinutes) min.",
                        exerciseId: ex.id,
                        priority: 25
                    )
                )
                if suggestions.count >= MAX_DROPSET_SUGGESTIONS + MAX_SUPERSET_SUGGESTIONS + 1 { break }
            }
        }

        if suggestions.count >= 2 {
            let targetMuscles = calcularVolumenPorMusculo(input: input).volumeMap
                .filter { $0.value.flat > 0 }
                .map { $0.key }
            if let muscle = targetMuscles.first {
                suggestions.append(
                    AssistantSuggestion(
                        id: "time_reduce_sets",
                        type: .reduceSet,
                        title: "Reducir una serie en \(muscle)",
                        message: "Impacto directo en duración. Sesión excede por \(overageMinutes) min.",
                        muscle: muscle,
                        priority: 20
                    )
                )
            }
        }

        return suggestions
    }

    // ─── Volume Calculation ──────────────────────────────────────────────────

    static func calcularVolumenPorMusculo(input: SessionAssistantInput) -> VolumeCalculationResult {
        var volumeMap = [String: MuscularVolumeAccumulator]()
        var muscleSetCounters = [String: Int]()
        var roleMap = [String: MuscleRoleBreakdown]()
        var recommendationContext = [String: MuscleRecommendationContext]()
        var totalSets = 0
        var totalSpinalLoad = 0.0
        var elbowStress = 0
        var kneeStress = 0
        var rpeSum = 0.0
        var rpeCount = 0

        let exerciseInsights: [ExerciseInsightData] = input.allExercisesInSession.compactMap { exercise in
            guard let info = resolveExerciseInfo(exercise: exercise, exerciseIndex: input.exerciseIndex) else { return nil }
            let validSets = exercise.validAugeSets()
            if validSets.isEmpty { return nil }

            let metrics = AugeFatigueEngine.getDynamicAugeMetrics(exerciseName: exercise.name, equipment: info.equipment, dbInfo: info)

            guard let metrics = metrics else {
                let contribs = VolumeCalculator.buildPerExerciseMuscleContributions(SessionMuscleFilter.relevantMusclesFor(info))
                for set in validSets {
                    let rpe = set.effectiveTargetRpe()
                    let vm = AugeClassifiers.getEffectiveVolumeMultiplier(rpe: rpe)
                    for (norm, hf) in contribs {
                        let b = volumeMap.getOrPut(norm) { MuscularVolumeAccumulator() }
                        b.flat += hf
                        b.effective += hf * vm
                        if rpe >= 9.5 { b.fail += hf }
                    }
                    totalSets += 1
                }
                return nil
            }

            var muscular = 0.0
            var cns = 0.0
            var spinal = 0.0
            totalSets += validSets.count

            let contribs = VolumeCalculator.buildPerExerciseMuscleContributions(SessionMuscleFilter.relevantMusclesFor(info))
            let primaryMuscle = info.involvedMuscles
                .first { $0.role == .PRIMARY }
                .flatMap { VolumeCalculator.normalizeCanonicalMuscleGroup($0.muscle, emphasis: $0.emphasis) }
                ?? "Core"
            var accumulated = muscleSetCounters[primaryMuscle] ?? 0

            for set in validSets {
                let rpe = set.effectiveTargetRpe()
                rpeSum += rpe
                rpeCount += 1

                let vm = AugeClassifiers.getEffectiveVolumeMultiplier(rpe: rpe)
                for (norm, hf) in contribs {
                    let b = volumeMap.getOrPut(norm) { MuscularVolumeAccumulator() }
                    b.flat += hf
                    b.effective += hf * vm
                    if rpe >= 9.5 { b.fail += hf }
                }

                for muscle in SessionMuscleFilter.relevantMusclesFor(info) {
                    let norm = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                    let rb = roleMap.getOrPut(norm) { MuscleRoleBreakdown() }
                    switch muscle.role {
                    case .PRIMARY:   rb.primary += 1.0
                    case .SECONDARY: rb.secondary += 1.0
                    case .STABILIZER: rb.stabilizer += 1.0
                    case .NEUTRALIZER: rb.neutralizer += 1.0
                    }
                }

                totalSpinalLoad += info.axialLoadFactor ?? 0.0
                accumulated += 1

                let calcWeight: Double
                if exercise.trainingMode == .RM, let pct = set.targetPercentageRM, let rm = exercise.reference1RM, rm > 0 {
                    calcWeight = (pct / 100.0) * rm
                } else {
                    calcWeight = set.weight ?? 60.0
                }

                let cs = CompletedSet(
                    id: set.id,
                    weight: calcWeight,
                    reps: set.targetReps ?? 8,
                    rpe: set.targetRPE,
                    rir: set.targetRIR,
                    isFailure: set.isFailure || set.intensityMode == .FAILURE,
                    actualIntensityMode: set.intensityMode,
                    actualIntensityValue: {
                        switch set.intensityMode {
                        case .RPE: return set.targetRPE
                        case .RIR: return set.targetRIR.map(Double.init)
                        default: return nil
                        }
                    }()
                )

                let tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings: input.settings)
                let sd = AugeFatigueEngine.calculateSetBatteryDrain(
                    set: cs,
                    metrics: metrics,
                    tanks: tanks,
                    accumulatedSets: accumulated,
                    restTime: exercise.restTime ?? 90,
                    densityMultiplier: AugeFatigueEngine.getDensityMultiplierForExercise(
                        supersetId: exercise.supersetGroupRefOrLegacyId(),
                        restTime: exercise.restTime ?? 90
                    )
                )
                muscular += sd.muscularDrainPct
                cns += sd.cnsDrainPct
                spinal += sd.spinalDrainPct
            }
            muscleSetCounters[primaryMuscle] = accumulated

            for muscle in SessionMuscleFilter.relevantMusclesFor(info) {
                let norm = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                let ctx = recommendationContext.getOrPut(norm) { MuscleRecommendationContext() }
                if exercise.trainingMode == .RM { ctx.usesPercent = true }
                if validSets.contains(where: { $0.targetRIR != nil }) { ctx.usesRir = true }
                if validSets.contains(where: { $0.isFailure || $0.intensityMode == .FAILURE }) { ctx.usesFailure = true }
            }

            let lc = info.name.lowercased()
            if lc.contains("press franc") || lc.contains("rompecr") ||
                (lc.contains("extensi") && lc.contains("polea") && lc.contains("tr")) {
                elbowStress += validSets.count
            }
            if lc.contains("extensi") && (lc.contains("cuadr") || lc.contains("sissy")) {
                kneeStress += validSets.count
            }

            return ExerciseInsightData(
                exerciseId: exercise.id,
                name: exercise.name,
                muscular: muscular,
                cns: cns,
                spinal: spinal
            )
        }

        return VolumeCalculationResult(
            volumeMap: volumeMap,
            roleMap: roleMap,
            recommendationContext: recommendationContext,
            totalSets: totalSets,
            totalSpinalLoad: totalSpinalLoad,
            elbowStress: elbowStress,
            kneeStress: kneeStress,
            averageRpe: rpeCount > 0 ? rpeSum / Double(rpeCount) : 0.0,
            exerciseInsights: exerciseInsights
        )
    }

    // ─── Drain Calculation ───────────────────────────────────────────────────

    static func calcularDrenajeEstimado(input: SessionAssistantInput) -> PredictedDrain {
        if let custom = input.customDrain { return custom }
        do {
            let session = Session(
                id: input.currentSessionId,
                name: "temp",
                exercises: input.allExercisesInSession
            )
            let base = AugeFatigueEngine.calculateAdjustedPredictedDrain(
                session: session,
                exerciseDb: input.exerciseIndex,
                settings: input.settings
            )
            let ema = AugeFatigueEngine.calculateMesocycleStressEMA(
                logs: input.workoutLogs,
                programId: input.programId,
                mesoIndex: input.mesoIndex
            )
            return AugeFatigueEngine.adjustPredictedDrainWithEMA(rawDrain: base, ema: ema)
        } catch {
            return PredictedDrain(cns: 15, muscular: 20, spinal: 10)
        }
    }

    // ─── Risk Detection ──────────────────────────────────────────────────────

    static func detectarRiesgos(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        thresholds: [String: VolumeThreshold],
        weeklyVolume: [String: Double] = [:]
    ) -> [SessionRisk] {
        var riesgos = [SessionRisk]()

        if input.ruleLimits.rigidLimits {
            riesgos += checkRigidLimits(input: input, volume: volume)
        }
        riesgos += checkVolumeThresholds(volume: volume, thresholds: thresholds)
        riesgos += checkWeeklyVolume(input: input, weeklyVolume: weeklyVolume, thresholds: thresholds)
        riesgos += checkGenericVolumeLimits(volume: volume, settings: input.settings)
        riesgos += checkSpinalLoad(volume: volume, drain: drain)
        riesgos += checkCnsFatigue(drain: drain, volume: volume)
        riesgos += checkExcessFailure(input: input, volume: volume)
        riesgos += checkJointStress(volume: volume)

        return riesgos.sorted { severityOrdinal($0.severity) > severityOrdinal($1.severity) }
    }

    private static func severityOrdinal(_ s: RiskSeverity) -> Int {
        switch s {
        case .info:    return 0
        case .warning: return 1
        case .blocking: return 2
        }
    }

    private static func checkRigidLimits(input: SessionAssistantInput, volume: VolumeCalculationResult) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        let limits = input.ruleLimits

        if limits.maxRPE < 10.0 {
            for exercise in input.allExercisesInSession {
                for set in exercise.validAugeSets() {
                    let rpe = set.effectiveTargetRpe()
                    if rpe > limits.maxRPE {
                        riesgos.append(SessionRisk(
                            id: "rigid-rpe-\(exercise.id)", type: .volume, severity: .blocking,
                            exerciseId: exercise.id, exerciseName: exercise.name,
                            title: "RPE excesivo (límite rígido)",
                            message: "\(exercise.name) tiene sets a RPE \(String(format: "%.1f", rpe)), por encima del límite de \(String(format: "%.1f", limits.maxRPE)).",
                            action: "Reducir RPE o eliminar serie."
                        ))
                    }
                }
            }
        }

        for (muscle, data) in volume.volumeMap {
            if data.effective >= limits.maxVolumePerMuscleSession {
                riesgos.append(SessionRisk(
                    id: "rigid-volume-session-\(muscle)", type: .volume, severity: .blocking,
                    muscle: muscle,
                    title: "Volumen rígido excedido: \(muscle)",
                    message: "\(muscle) tiene \(String(format: "%.1f", data.effective)) series efectivas, límite: \(String(format: "%.0f", limits.maxVolumePerMuscleSession)).",
                    action: "Reducir series o bajar intensidad para \(muscle)."
                ))
            }
        }

        return riesgos
    }

    private static func checkVolumeThresholds(volume: VolumeCalculationResult, thresholds: [String: VolumeThreshold]) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        for (muscle, data) in volume.volumeMap {
            guard let t = thresholds[muscle] else { continue }
            if data.flat >= t.mrv {
                riesgos.append(SessionRisk(
                    id: "threshold-mrv-\(muscle)", type: .volume, severity: .blocking, muscle: muscle,
                    title: "\(muscle) en o sobre MRV",
                    message: "Con \(String(format: "%.1f", data.flat)) series equivalentes, \(muscle) está en el límite recuperable (\(String(format: "%.1f", t.mrv)) MRV). No añadir más volumen.",
                    action: "Mantener volumen actual o reducir."
                ))
            } else if data.flat > t.mav {
                riesgos.append(SessionRisk(
                    id: "threshold-mav-\(muscle)", type: .volume, severity: .warning, muscle: muscle,
                    title: "\(muscle) sobre MAV",
                    message: "Con \(String(format: "%.1f", data.flat)) series, estás por encima del rango óptimo (MAV: \(String(format: "%.1f", t.mav))). Añadir volumen tiene rendimientos decrecientes.",
                    action: "Considerar reducir 1 serie o bajar intensidad."
                ))
            }
        }
        return riesgos
    }

    private static func checkGenericVolumeLimits(volume: VolumeCalculationResult, settings: Settings) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        let sessionLimit = defaultSessionVolumeLimit(settings: settings)
        for (muscle, data) in volume.volumeMap {
            if data.effective > Double(sessionLimit) {
                let hasBlocking = riesgos.contains { $0.muscle == muscle && $0.severity == .blocking }
                if !hasBlocking {
                    riesgos.append(SessionRisk(
                        id: "generic-volume-\(muscle)", type: .volume, severity: .warning, muscle: muscle,
                        title: "Volumen elevado: \(muscle)",
                        message: "\(muscle) tiene \(String(format: "%.1f", data.effective)) series efectivas sobre el límite genérico de \(sessionLimit).",
                        action: "Reducir 1-2 series o bajar intensidad."
                    ))
                }
            }
        }
        return riesgos
    }

    private static func checkSpinalLoad(volume: VolumeCalculationResult, drain: PredictedDrain) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        if volume.totalSpinalLoad > 25.0 {
            let critical = volume.totalSpinalLoad > 40.0 || drain.spinal > 30
            riesgos.append(SessionRisk(
                id: "spinal-load", type: .spine,
                severity: critical ? .blocking : .warning,
                title: critical ? "Carga axial elevada" : "Carga axial moderada",
                message: critical
                    ? "La sesión acumula carga axial alta (\(String(format: "%.1f", volume.totalSpinalLoad))). Bajar series o intensidad del ejercicio más demandante protege la columna."
                    : "La sesión suma carga axial relevante (\(String(format: "%.1f", volume.totalSpinalLoad))). Ajustar densidad o intensidad mejora la tolerancia.",
                action: "Usar variantes más estables o reducir carga axial."
            ))
        }
        return riesgos
    }

    private static func checkCnsFatigue(drain: PredictedDrain, volume: VolumeCalculationResult) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        if drain.cns >= 85 || volume.averageRpe >= 9.3 {
            riesgos.append(SessionRisk(
                id: "cns-fatigue", type: .cns,
                severity: drain.cns >= 90 ? .blocking : .warning,
                title: "Fatiga SNC elevada",
                message: "Tu energía SNC va alta para esta sesión (\(drain.cns)%). Bajar RPE, subir RIR o reducir %1RM deja margen sin romper el plan.",
                action: "Reducir intensidad global."
            ))
        }
        return riesgos
    }

    private static func checkExcessFailure(input: SessionAssistantInput, volume: VolumeCalculationResult) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        let totalSets = volume.totalSets
        if totalSets == 0 { return riesgos }

        var failureSets = 0
        for exercise in input.allExercisesInSession {
            for set in exercise.validAugeSets() {
                if set.isFailure || set.intensityMode == .FAILURE { failureSets += 1 }
            }
        }
        let ratio = Double(failureSets) / Double(totalSets)
        if ratio >= 0.5 && failureSets >= 3 {
            riesgos.append(SessionRisk(
                id: "excess-failure", type: .failure,
                severity: ratio >= 0.7 ? .blocking : .warning,
                title: "Exceso de series al fallo",
                message: "\(failureSets) de \(totalSets) series al fallo (\(Int(ratio * 100))%). Esto acumula fatiga SNC y articular más de lo necesario.",
                action: "Pasar a RIR 1-3 o bajar RPE antes de recortar volumen."
            ))
        }
        return riesgos
    }

    private static func checkJointStress(volume: VolumeCalculationResult) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        if volume.elbowStress > 8 {
            riesgos.append(SessionRisk(
                id: "joint-elbow", type: .joint,
                severity: volume.elbowStress > 12 ? .warning : .info,
                title: "Estrés de codos",
                message: "Hay \(volume.elbowStress) series de trabajo aislado de tríceps en ángulos agresivos. Ajustar intensidad o distribuir accesorios ayuda.",
                action: "Reducir series de extensión de tríceps o usar variantes más seguras."
            ))
        }
        if volume.kneeStress > 8 {
            riesgos.append(SessionRisk(
                id: "joint-knee", type: .joint,
                severity: volume.kneeStress > 12 ? .warning : .info,
                title: "Estrés de rodillas",
                message: "Extensiones puras o patrones similares se están acumulando (\(volume.kneeStress) series). Bajar densidad o reforzar calentamiento mejora tolerancia.",
                action: "Reducir series de extensión de cuádriceps o añadir calentamiento dinámico."
            ))
        }
        return riesgos
    }

    // ─── Weekly Volume ──────────────────────────────────────────────────────

    static func calcularVolumenSemanal(input: SessionAssistantInput) -> [String: Double] {
        var weeklyMap = [String: Double]()
        for session in input.weekSessions {
            let exercises = session.exercises + session.parts.flatMap { $0.exercises }
            for exercise in exercises {
                guard let info = resolveExerciseInfo(exercise: exercise, exerciseIndex: input.exerciseIndex) else { continue }
                for set in exercise.validAugeSets() {
                    let rpe = set.effectiveTargetRpe()
                    let vm = AugeClassifiers.getEffectiveVolumeMultiplier(rpe: rpe)
                    for muscle in SessionMuscleFilter.relevantMusclesFor(info) {
                        let norm = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                        weeklyMap[norm] = (weeklyMap[norm] ?? 0.0) + VolumeCalculator.resolveMuscleVolumeContribution(muscle) * vm
                    }
                }
            }
        }
        return weeklyMap
    }

    private static func checkWeeklyVolume(
        input: SessionAssistantInput,
        weeklyVolume: [String: Double],
        thresholds: [String: VolumeThreshold]
    ) -> [SessionRisk] {
        var riesgos = [SessionRisk]()
        let weeklyLimit = input.ruleLimits.maxVolumePerMuscleWeekly

        for (muscle, weeklySets) in weeklyVolume {
            if weeklySets > weeklyLimit {
                riesgos.append(SessionRisk(
                    id: "weekly-volume-\(muscle)", type: .volume, severity: .warning, muscle: muscle,
                    title: "Volumen semanal alto: \(muscle)",
                    message: "\(muscle) tiene \(String(format: "%.1f", weeklySets)) series en la semana, por encima del límite de \(String(format: "%.0f", weeklyLimit)). Repartir el estímulo ayuda a la recuperación.",
                    action: "Reducir volumen en sesiones futuras o redistribute."
                ))
            }

            let weeklyMrv = thresholds[muscle].map { $0.mrv * 3.0 } ?? weeklyLimit
            if weeklySets >= weeklyMrv {
                riesgos.append(SessionRisk(
                    id: "weekly-mrv-\(muscle)", type: .volume, severity: .blocking, muscle: muscle,
                    title: "\(muscle) en o sobre MRV semanal",
                    message: "\(muscle) tiene \(String(format: "%.1f", weeklySets)) series semanales, en o sobre MRV (\(String(format: "%.1f", weeklyMrv))). No añadir más volumen este día.",
                    action: "Mantener volumen actual o reducir."
                ))
            }
        }
        return riesgos
    }

    // ─── Suggestions Generation ──────────────────────────────────────────────

    static func generarAjustes(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        riesgos: [SessionRisk]
    ) -> [AssistantSuggestion] {
        var ajustes = [AssistantSuggestion]()

        if riesgos.contains(where: { $0.type == .failure }) {
            ajustes.append(AssistantSuggestion(
                id: "adj-remove-failure", type: .removeFailure,
                title: "Reducir series al fallo",
                message: "Pasar a RIR 1-3 o bajar RPE. Esto reduce fatiga SNC y articular sin perder volumen efectivo.",
                priority: 1
            ))
        }

        if let spinalRisk = riesgos.first(where: { $0.type == .spine }), spinalRisk.severity != .info {
            ajustes.append(AssistantSuggestion(
                id: "adj-lower-rpe-spine", type: .lowerRpe,
                title: "Bajar intensidad para proteger columna",
                message: "Reducir RPE o %RM en ejercicios axiales principales. Esto baja la carga sin eliminar volumen.",
                priority: 2
            ))
        }

        if riesgos.contains(where: { $0.type == .cns }) {
            ajustes.append(AssistantSuggestion(
                id: "adj-lower-rpe-cns", type: .lowerRpe,
                title: "Bajar intensidad para reducir fatiga SNC",
                message: "Subir RIR o bajar RPE en 0.5-1.0 puntos. Deja margen para el resto de la sesión y la recuperación.",
                priority: 3
            ))
        }

        for risk in riesgos where risk.type == .volume && risk.severity == .blocking {
            ajustes.append(AssistantSuggestion(
                id: "adj-reduce-\(risk.muscle ?? "ejercicio")", type: .reduceSet,
                title: "Reducir series de \(risk.muscle ?? "ejercicio")",
                message: risk.message, muscle: risk.muscle, priority: 4
            ))
        }

        return ajustes.sorted { $0.priority < $1.priority }
    }

    static func generarAjustesPorRings(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain
    ) -> [AssistantSuggestion] {
        var ajustes = [AssistantSuggestion]()
        let heavyExercises = volume.exerciseInsights
            .filter { i in
                (i.muscular >= 40.0 && Double(drain.muscular) >= 40) ||
                (i.cns >= 40.0 && Double(drain.cns) >= 40) ||
                (i.spinal >= 40.0 && Double(drain.spinal) >= 40)
            }
            .sorted { a, b in
                let ma = max(a.muscular, a.cns, a.spinal)
                let mb = max(b.muscular, b.cns, b.spinal)
                if ma != mb { return ma > mb }
                return a.name < b.name
            }
            .prefix(2)

        let heavyList = Array(heavyExercises)
        let names = heavyList.map { $0.name }.joined(separator: ", ")
        let targetNames = names.isEmpty ? "los ejercicios más demandantes" : names

        if drain.spinal >= 40 {
            ajustes.append(AssistantSuggestion(
                id: "rings-spinal-moderate", type: .lowerRpe,
                title: "Moderar carga axial",
                message: "La batería espinal bajaría \(drain.spinal)%. Mantén la estructura, pero baja 0.5-1 RPE y recorta 1 serie total entre \(targetNames).",
                priority: 1
            ))
        }

        if drain.cns >= 40 {
            ajustes.append(AssistantSuggestion(
                id: "rings-cns-moderate", type: .lowerRpe,
                title: "Bajar drenaje SNC",
                message: "La batería SNC bajaría \(drain.cns)%. Reduce levemente la intensidad en los sets duros y evita llevar series al límite en \(targetNames).",
                priority: 2
            ))
        }

        if drain.muscular >= 40 {
            let muscle = volume.volumeMap.max { $0.value.effective < $1.value.effective }?.key
            ajustes.append(AssistantSuggestion(
                id: "rings-muscular-moderate-\(muscle ?? "general")", type: .reduceSet,
                title: "Ajustar volumen efectivo",
                message: "La batería muscular bajaría \(drain.muscular)%. Recorta 1 serie del bloque principal y baja 0.5 RPE en los sets finales para conservar estímulo.",
                muscle: muscle, priority: 3
            ))
        }

        for (index, ex) in heavyList.enumerated() {
            let peak = max(ex.muscular, ex.cns, ex.spinal)
            if peak < 40.0 { continue }
            ajustes.append(AssistantSuggestion(
                id: "rings-exercise-\(ex.exerciseId)", type: .lowerRpe,
                title: "Suavizar \(ex.name)",
                message: "\(ex.name) concentra un drenaje cercano a \(Int(peak.rounded()))%. Usa un ajuste moderado: 1 serie menos si hay muchas series, o 0.5-1 RPE menos si el volumen debe mantenerse.",
                exerciseId: ex.exerciseId, exerciseName: ex.name, priority: 4 + index
            ))
        }

        var seen = Set<String>()
        return ajustes.filter { seen.insert($0.id).inserted }.sorted { $0.priority < $1.priority }
    }

    static func generarOportunidades(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        thresholds: [String: VolumeThreshold],
        riesgos: [SessionRisk],
        weeklyVolume: [String: Double] = [:]
    ) -> [AssistantSuggestion] {
        var oportunidades = [AssistantSuggestion]()
        let bloqueantes = Set(riesgos.filter { $0.severity == .blocking }.compactMap { $0.muscle })

        if drain.cns >= 70 || drain.spinal >= 20 { return oportunidades }

        for (muscle, threshold) in thresholds {
            if bloqueantes.contains(muscle) { continue }
            let current = volume.volumeMap[muscle]?.flat ?? 0.0
            if current >= threshold.mev { continue }
            let weeklyCurrent = weeklyVolume[muscle] ?? 0.0
            let weeklyMrv = threshold.mrv * 3.0
            if weeklyCurrent < weeklyMrv * 0.8 {
                oportunidades.append(AssistantSuggestion(
                    id: "opp-\(muscle)", type: .addGhostExercise,
                    title: "\(muscle) bajo MEV",
                    message: "\(muscle) tiene \(String(format: "%.1f", current)) series, por debajo de MEV (\(String(format: "%.1f", threshold.mev))). Quedan \(String(format: "%.1f", threshold.mev - current)) series de margen real.",
                    muscle: muscle, priority: 10
                ))
            }
        }

        return oportunidades
    }

    // ─── Ghost Exercise Cards ────────────────────────────────────────────────

    static func generarTarjetasFantasma(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        thresholds: [String: VolumeThreshold],
        riesgos: [SessionRisk],
        weeklyVolume: [String: Double] = [:]
    ) -> [GhostExerciseCard] {
        var candidate = [GhostExerciseCard]()
        let bloqueantes = Set(riesgos.filter { $0.severity == .blocking }.compactMap { $0.muscle })

        if drain.cns >= 75 || drain.spinal >= 25 { return candidate }

        let sessionPatterns = Set(input.allExercisesInSession.compactMap { ex in
            resolveExerciseInfo(exercise: ex, exerciseIndex: input.exerciseIndex)?.force
        })

        for (muscle, threshold) in thresholds {
            if bloqueantes.contains(muscle) { continue }
            let current = volume.volumeMap[muscle]?.flat ?? 0.0
            if current >= threshold.mev { continue }

            let weeklyCurrent = weeklyVolume[muscle] ?? 0.0
            let weeklyMrv = threshold.mrv * 3.0
            let weeklyLimit = input.ruleLimits.maxVolumePerMuscleWeekly
            if weeklyCurrent >= weeklyMrv || weeklyCurrent >= weeklyLimit { continue }

            let gap = threshold.mev - current

            let compatibles = input.exerciseIndex.values
                .filter { info in
                    info.involvedMuscles.contains { m in
                        VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis) == muscle
                    } && (sessionPatterns.isEmpty || info.force == nil || sessionPatterns.contains(info.force!))
                }
                .sorted { a, b in
                    a.involvedMuscles.filter { m in
                        VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis) == muscle
                    }.count > b.involvedMuscles.filter { m in
                        VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis) == muscle
                    }.count
                }
                .prefix(2)

            for info in compatibles {
                let setsNuevos = min(3, max(1, Int(ceil(gap))))
                let impactoDrenaje = estimarImpactoDrenajeCns(info: info, sets: setsNuevos)
                let impactoColumna = (info.axialLoadFactor ?? 0.0) * Double(setsNuevos)

                guard let pm = info.involvedMuscles.first(where: { m in
                    VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis) == muscle
                }) else { continue }

                let weeklyAfter = (weeklyVolume[muscle] ?? 0.0) + Double(setsNuevos) * VolumeCalculator.resolveMuscleVolumeContribution(pm)

                if Double(drain.cns) + Double(impactoDrenaje) < 90.0 && Double(drain.spinal) + impactoColumna < 40.0 && weeklyAfter < weeklyMrv {
                    candidate.append(GhostExerciseCard(
                        cardId: "ghost-\(muscle)-\(info.id)",
                        exerciseDbId: info.id,
                        name: info.name,
                        motivo: "\(muscle) está bajo MEV (\(String(format: "%.1f", current))/\(String(format: "%.1f", threshold.mev)))",
                        sets: setsNuevos, reps: 10, rpe: 7.5,
                        restSeconds: info.averageRestSeconds ?? 90,
                        impactoVolumen: "+\(setsNuevos) sets \(muscle)",
                        impactoDrenaje: "+\(impactoDrenaje) SNC",
                        impactoColumna: impactoColumna > 0 ? "+\(String(format: "%.1f", impactoColumna)) columna" : "Sin impacto",
                        compatibleConSplit: true
                    ))
                }
            }
        }

        return candidate
    }

    private static func estimarImpactoDrenajeCns(info: ExerciseMuscleInfo, sets: Int) -> Int {
        let perSet = Int(((info.cnc ?? 2.0) / 5.0 * 8.0).rounded())
        return min(30, max(0, perSet * sets))
    }

    // ─── Template Search ─────────────────────────────────────────────────────

    static func buscarPlantillasCompatibles(
        input: SessionAssistantInput,
        allTemplates: [SessionTemplate]
    ) -> [TemplatePreview] {
        if allTemplates.isEmpty { return [] }

        let sessionMuscles = calcularMusculosEnSesion(exercises: input.allExercisesInSession, exerciseIndex: input.exerciseIndex)

        return allTemplates
            .filter { !$0.isArchived }
            .compactMap { template -> TemplatePreview? in
                let templateExercises = template.session.exercises + template.session.parts.flatMap { $0.exercises }
                let templateMuscles = calcularMusculosEnSesion(exercises: templateExercises, exerciseIndex: input.exerciseIndex)
                if sessionMuscles.intersection(templateMuscles).isEmpty { return nil }

                let drenaje: PredictedDrain
                if let cached = input.customTemplateDrains[template.id] {
                    drenaje = cached
                } else {
                    do {
                        drenaje = AugeFatigueEngine.calculateAdjustedPredictedDrain(
                            session: template.session, exerciseDb: input.exerciseIndex, settings: input.settings
                        )
                    } catch {
                        drenaje = PredictedDrain(cns: 15, muscular: 20, spinal: 10)
                    }
                }

                var advertencias = [String]()
                if drenaje.cns > 80 { advertencias.append("Alto drenaje SNC (\(drenaje.cns)%)") }
                if drenaje.spinal > 25 { advertencias.append("Carga axial elevada (\(drenaje.spinal)%)") }
                if drenaje.muscular > 70 { advertencias.append("Drenaje muscular alto (\(drenaje.muscular)%)") }

                let hasContent = !templateExercises.isEmpty
                let modo: SessionTemplateApplyMode = (hasContent && !input.allExercisesInSession.isEmpty) ? .append : .replace

                return TemplatePreview(
                    template: template, modoRecomendado: modo,
                    volumenPorMusculo: calcularVolumenPlantilla(exercises: templateExercises, exerciseIndex: input.exerciseIndex),
                    drenajeEstimado: drenaje, advertencias: advertencias,
                    duracionEstimada: template.estimatedDurationMinutes ?? 60
                )
            }
            .sorted { $0.advertencias.count < $1.advertencias.count }
            .prefix(4).map { $0 }
    }

    private static func calcularMusculosEnSesion(exercises: [Exercise], exerciseIndex: [String: ExerciseMuscleInfo]) -> Set<String> {
        var result = Set<String>()
        for exercise in exercises {
            guard let info = resolveExerciseInfo(exercise: exercise, exerciseIndex: exerciseIndex) else { continue }
            for muscle in SessionMuscleFilter.relevantMusclesFor(info) {
                result.insert(VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis))
            }
        }
        return result
    }

    private static func calcularVolumenPlantilla(exercises: [Exercise], exerciseIndex: [String: ExerciseMuscleInfo]) -> [String: Double] {
        var volumeMap = [String: Double]()
        for exercise in exercises {
            guard let info = resolveExerciseInfo(exercise: exercise, exerciseIndex: exerciseIndex) else { continue }
            let validSets = exercise.sets.filter { !$0.isIneffective }
            for set in validSets {
                let rpe = set.effectiveTargetRpe()
                let vm = AugeClassifiers.getEffectiveVolumeMultiplier(rpe: rpe)
                for muscle in SessionMuscleFilter.relevantMusclesFor(info) {
                    let norm = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                    volumeMap[norm] = (volumeMap[norm] ?? 0.0) + VolumeCalculator.resolveMuscleVolumeContribution(muscle) * vm
                }
            }
        }
        return volumeMap
    }

    // ─── Verdict ─────────────────────────────────────────────────────────────

    static func clasificarVeredicto(riesgos: [SessionRisk], drain: PredictedDrain, settings: Settings) -> Verdict {
        if riesgos.contains(where: { $0.severity == .blocking }) { return .critical }
        if riesgos.filter({ $0.severity == .warning }).count >= 2 { return .fatiguing }
        if !riesgos.isEmpty || drain.cns > 60 || drain.spinal > 20 { return .warning }
        return .optimal
    }

    private static func calcularScore(veredicto: Verdict, riesgos: [SessionRisk], drain: PredictedDrain) -> Int {
        let base: Int
        switch veredicto {
        case .optimal:   base = 90
        case .warning:   base = 70
        case .fatiguing: base = 50
        case .critical:  base = 25
        }
        let penalty = riesgos.filter { $0.severity == .warning }.count * 3
        let drainPenalty = drain.cns > 70 ? 5 : 0
        return max(0, min(100, base - penalty - drainPenalty))
    }

    private static func construirResumen(veredicto: Verdict, riesgos: [SessionRisk], ajustes: [AssistantSuggestion], tarjetas: [GhostExerciseCard]) -> String {
        var parts = [String]()
        switch veredicto {
        case .optimal:   parts.append("La sesión está bien balanceada.")
        case .warning:   parts.append("Hay algunos ajustes a considerar.")
        case .fatiguing: parts.append("La sesión acumula fatiga considerable.")
        case .critical:  parts.append("Hay riesgos importantes que atender antes de continuar.")
        }
        if !riesgos.isEmpty {
            parts.append("\(riesgos.count) riesgo\(riesgos.count > 1 ? "s" : "") detectado\(riesgos.count > 1 ? "s" : "").")
        }
        if !ajustes.isEmpty {
            parts.append("\(ajustes.count) ajuste\(ajustes.count > 1 ? "s" : "") sugerido\(ajustes.count > 1 ? "s" : "").")
        }
        if !tarjetas.isEmpty {
            parts.append("\(tarjetas.count) propuesta\(tarjetas.count > 1 ? "s" : "") disponible\(tarjetas.count > 1 ? "s" : "").")
        }
        return parts.joined(separator: " ")
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    static func resolveExerciseInfo(exercise: Exercise, exerciseIndex: [String: ExerciseMuscleInfo]) -> ExerciseMuscleInfo? {
        let byId = exercise.exerciseDbId ?? exercise.exerciseId
        if let key = byId?.lowercased(), let info = exerciseIndex[key] { return info }
        return exerciseIndex.values.first { $0.name == exercise.name }
    }

    static func defaultSessionVolumeLimit(settings: Settings) -> Int {
        let base: Double
        switch settings.calorieGoalObjective {
        case .DEFICIT:     base = 8.0
        case .MAINTENANCE: base = 9.0
        case .SURPLUS:     base = 10.0
        }
        let adj: Double
        switch settings.athleteType {
        case .BODYBUILDER, .POWERBUILDER: adj = 0.8
        case .POWERLIFTER, .WEIGHTLIFTER: adj = -0.4
        default:                          adj = 0.0
        }
        return max(6, Int((base + adj).rounded()))
    }

    static func defaultWeeklyVolumeLimit(settings: Settings) -> Int {
        switch settings.calorieGoalObjective {
        case .DEFICIT:     return 20
        case .MAINTENANCE: return 24
        case .SURPLUS:     return 28
        }
    }

    static func buildVolumeThresholds(
        input: SessionAssistantInput,
        sessionVolumeByMuscle: [String: MuscularVolumeAccumulator]
    ) -> [String: VolumeThreshold] {
        let personalized = (input.program?.volumeRecommendations ?? [])
            .groupedBy { VolumeCalculator.normalizeCanonicalMuscleGroup($0.muscleGroup) }
            .mapValues { grouped -> (Double, Double, Double) in
                let mev = max(1.0, Double(grouped.reduce(0) { $0 + $1.minEffectiveVolume }))
                let mav = max(mev, Double(grouped.reduce(0) { $0 + $1.maxAdaptiveVolume }))
                let mrv = max(mav, Double(grouped.reduce(0) { $0 + $1.maxRecoverableVolume }))
                return (mev, mav, mrv)
            }

        let involved = Set(sessionVolumeByMuscle.keys.filter { !$0.isEmpty })
        if involved.isEmpty { return [:] }

        let defaultWeeklyMrv = max(8.0, Double(defaultWeeklyVolumeLimit(settings: input.settings)))
        let defaultWeeklyMav = max(6.0, defaultWeeklyMrv * 0.8)
        let defaultWeeklyMev = max(4.0, defaultWeeklyMav * 0.65)

        var result = [String: VolumeThreshold]()
        for muscle in involved {
            let p = personalized[muscle]
            let wMev = p?.0 ?? defaultWeeklyMev
            let wMav = p?.1 ?? defaultWeeklyMav
            let wMrv = p?.2 ?? defaultWeeklyMrv
            result[muscle] = VolumeThreshold(
                mev: max(1.0, wMev / 3.0),
                mav: max(1.0, wMav / 3.0),
                mrv: max(1.0, wMrv / 3.0)
            )
        }
        return result
    }

    // ─── Session Finder ──────────────────────────────────────────────────────

    static func findNextSessionWithMuscles(
        currentSessionId: String,
        weekSessions: [Session],
        muscleIds: [String],
        exerciseIndex: [String: ExerciseMuscleInfo]
    ) -> Session? {
        guard let idx = weekSessions.firstIndex(where: { $0.id == currentSessionId }) else { return nil }

        for i in (idx + 1)..<weekSessions.count {
            if sessionContainsAnyMuscle(session: weekSessions[i], muscleIds: muscleIds, exerciseIndex: exerciseIndex) {
                return weekSessions[i]
            }
        }

        for i in 0..<idx {
            if sessionContainsAnyMuscle(session: weekSessions[i], muscleIds: muscleIds, exerciseIndex: exerciseIndex) {
                return weekSessions[i]
            }
        }

        return nil
    }

    private static func sessionContainsAnyMuscle(
        session: Session,
        muscleIds: [String],
        exerciseIndex: [String: ExerciseMuscleInfo]
    ) -> Bool {
        for ex in session.allExercises() {
            let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
            guard let info = exerciseIndex[dbId] else { continue }
            for muscle in info.involvedMuscles where muscle.role == .PRIMARY {
                let mId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                if muscleIds.contains(mId) { return true }
            }
        }
        return false
    }

    // ─── Volume Discounts ────────────────────────────────────────────────────

    static func computeProposedDiscounts(
        currentSession: Session,
        nextSession: Session,
        targetMuscles: [String],
        completedSets: [String: CompletedSet],
        exerciseIndex: [String: ExerciseMuscleInfo]
    ) -> [MuscleAdvance] {
        var muscleAdvances = [MuscleAdvance]()
        var completedByExercise = [String: Int]()
        for (key, _) in completedSets {
            var exerciseId = key.substringBeforeLast("_")
            if exerciseId.hasSuffix("_L") || exerciseId.hasSuffix("_R") {
                exerciseId = exerciseId.substringBeforeLast("_")
            }
            completedByExercise[exerciseId, default: 0] += 1
        }

        for muscleId in targetMuscles {
            var plannedSets = 0.0
            for ex in currentSession.allExercises() {
                let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
                guard let info = exerciseIndex[dbId] else { continue }
                for m in info.involvedMuscles where m.role == .PRIMARY {
                    let mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis)
                    if mId == muscleId {
                        plannedSets += Double(ex.sets.count)
                    }
                }
            }

            var actualSets = 0.0
            for ex in currentSession.allExercises() {
                let sets = completedByExercise[ex.id] ?? 0
                if sets == 0 { continue }
                let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
                guard let info = exerciseIndex[dbId] else { continue }
                for m in info.involvedMuscles where m.role == .PRIMARY {
                    let mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis)
                    if mId == muscleId {
                        actualSets += Double(sets)
                    }
                }
            }

            let delta = actualSets - plannedSets
            if delta <= 0.0 { continue }

            var proposals = [VolumeDiscountProposal]()
            var remaining = delta

            for ex in nextSession.allExercises() {
                if remaining <= 0.0 { break }
                let dbId = ex.exerciseDbId ?? ex.exerciseId ?? ""
                guard let info = exerciseIndex[dbId] else { continue }
                let trainsMuscle = info.involvedMuscles.contains { m in
                    m.role == .PRIMARY && VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis) == muscleId
                }
                if trainsMuscle {
                    let maxDiscount = Double(max(0, ex.sets.count - 1))
                    if maxDiscount > 0 {
                        let d = min(remaining, maxDiscount)
                        proposals.append(VolumeDiscountProposal(
                            exerciseId: ex.id,
                            exerciseName: ex.name,
                            currentRole: "PRIMARY",
                            discountSets: d,
                            reason: "Ajuste de volumen"
                        ))
                        remaining -= d
                    }
                }
            }

            if !proposals.isEmpty {
                muscleAdvances.append(MuscleAdvance(
                    muscleId: muscleId, muscleName: muscleId,
                    currentSets: actualSets, targetSets: plannedSets, deficitSets: delta,
                    targetSessionId: nextSession.id, targetSessionName: nextSession.name,
                    discountProposals: proposals
                ))
            }
        }

        return muscleAdvances
    }
}

// MARK: - Extensions

extension Exercise {
    func validAugeSets() -> [ExerciseSet] {
        sets.filter { !$0.isIneffective }
    }
}

extension ExerciseSet {
    func effectiveTargetRpe() -> Double {
        if isFailure || intensityMode == .FAILURE { return 10.0 }
        if let rpe = targetRPE { return max(1.0, min(10.0, rpe)) }
        if let rir = targetRIR { return max(1.0, min(10.0, 10.0 - Double(rir))) }
        return 8.0
    }
}

extension String {
    func substringBeforeLast(_ separator: String) -> String {
        if let range = range(of: separator, options: .backwards) {
            return String(self[..<range.lowerBound])
        }
        return self
    }
}

extension Dictionary {
    mutating func getOrPut(_ key: Key, default factory: () -> Value) -> Value {
        if let value = self[key] { return value }
        let value = factory()
        self[key] = value
        return value
    }
}

extension Array {
    func groupedBy<GroupKey: Hashable>(_ keySelector: (Element) -> GroupKey) -> [GroupKey: [Element]] {
        var result = [GroupKey: [Element]]()
        for element in self {
            let key = keySelector(element)
            result[key, default: []].append(element)
        }
        return result
    }
}
