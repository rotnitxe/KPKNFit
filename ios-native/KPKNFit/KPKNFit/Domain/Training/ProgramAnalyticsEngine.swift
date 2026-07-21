import Foundation

// MARK: - Public Report Types

struct ProgramAnalyticsReport {
    let coverage: CoverageAnalytics
    let balance: BalanceAnalytics
    let fatigue: FatigueAnalytics
    let efficiency: EfficiencyAnalytics
    let adherence: AdherenceAnalytics
    let progression: [ExerciseProgressionAnalytics]
    let diagnostics: [ProgramDiagnostic]
}

struct CoverageAnalytics {
    let musclesByWeeklySets: [MuscleMetric]
    let forgottenMuscles: [String]
    let repsByMuscle: [MuscleMetric]
    let timeMinutesByMuscle: [MuscleMetric]
    let directIndirectByMuscle: [DirectIndirectMetric]
    let unilateralExerciseRatio: Double
    let stabilityDemand: Double
    let stabilityDistribution: [NamedMetric]
    let strengthSpecificityRatio: Double
    let emptyReason: String?
}

struct BalanceAnalytics {
    let movementPatterns: [NamedMetric]
    let pushPullRatio: RatioMetric
    let horizontalPushPullRatio: RatioMetric
    let verticalPushPullRatio: RatioMetric
    let quadPosteriorRatio: RatioMetric
    let upperLowerRatio: RatioMetric
    let notes: [String]
}

struct FatigueAnalytics {
    let structures: [NamedMetric]
    let residualHeatmap: [MuscleMetric]
    let residualCalendar: [NamedMetric]
    let recoveryDebtByMuscle: [MuscleMetric]
    let readinessByMuscle: [MuscleMetric]
    let axialLoad: Double
    let gripDemand: Double
    let lumbarFatigue: Double
    let anteriorShoulderStress: Double
    let sourceSummary: String
}

struct EfficiencyAnalytics {
    let densitySetsPerHour: Double
    let sessionDensity: [NamedMetric]
    let paretoTopExercises: [NamedMetric]
    let topStimulusShare: Double
    let topFatiguingExercises: [NamedMetric]
    let restCompliance: RatioMetric
    let blockIdentity: String
}

struct AdherenceAnalytics {
    let completedSessionRatio: Double
    let completedExerciseRatio: Double
    let omittedExercises: [NamedMetric]
    let skippedExerciseCount: Int
    let diagnosis: String
}

struct ExerciseProgressionAnalytics {
    let exerciseId: String
    let exerciseName: String
    let firstE1rm: Double?
    let lastE1rm: Double?
    let deltaE1rm: Double?
    let sparkline: [Double]
    let stagnationRisk: Double
}

struct ProgramDiagnostic {
    let id: String
    let title: String
    let detail: String
    let severity: DiagnosticSeverity
}

enum DiagnosticSeverity { case info, warning, critical }

struct MuscleMetric {
    let id: String
    let name: String
    let value: Double
    let explanation: String
}

struct NamedMetric {
    let id: String
    let label: String
    let value: Double
    let explanation: String
}

struct DirectIndirectMetric {
    let muscle: String
    let directSets: Double
    let indirectSets: Double
    let explanation: String
}

struct RatioMetric {
    let leftLabel: String
    let leftValue: Double
    let rightLabel: String
    let rightValue: Double
    let ratio: Double
    let explanation: String
}

// MARK: - ProgramAnalyticsEngine

enum ProgramAnalyticsEngine {

    private static let trackedMuscles = [
        "Pectorales",
        "Dorsales",
        "Deltoides",
        "Bíceps",
        "Tríceps",
        "Cuádriceps",
        "Isquiosurales",
        "Glúteos",
        "Pantorrillas",
        "Erectores Espinales",
        "Core",
        "Abdomen",
    ]

    private static let tendonStructureKeywords: [String: [String]] = [
        "Rodilla": ["cuadriceps", "sentadilla", "prensa", "zancada", "sissy"],
        "Cadera": ["glute", "peso muerto", "hip thrust", "bisagra"],
        "Lumbar": ["erector", "lumbar", "peso muerto", "rdl", "pendlay", "remo"],
        "Hombro anterior": ["press", "pectoral", "deltoides anterior"],
        "Codo": ["biceps", "triceps", "curl", "extension"],
        "Muñeca": ["agarre", "antebrazo", "curl", "remo"],
        "Tendón rotuliano": ["cuadriceps", "sentadilla", "sissy", "prensa"],
        "Aquiles": ["pantorrilla", "gemelo", "soleo", "salto"],
    ]

    // MARK: - Public Entry Point

    static func analyze(
        program: Program,
        logs: [WorkoutLog],
        exerciseCatalog: [ExerciseMuscleInfo]
    ) -> ProgramAnalyticsReport {
        let catalog = Dictionary(uniqueKeysWithValues: exerciseCatalog.map { ($0.id.lowercased(), $0) })
        let plannedRows = buildPlannedRows(program, catalog: catalog)
        let completedRows = buildCompletedRows(logs: logs, catalog: catalog)

        let coverage = buildCoverage(plannedRows)
        let balance = buildBalance(plannedRows)
        let fatigue = buildFatigue(plannedRows: plannedRows, completedRows: completedRows)
        let efficiency = buildEfficiency(plannedRows: plannedRows, completedRows: completedRows, logs: logs)
        let adherence = buildAdherence(rows: plannedRows, logs: logs)
        let progression = buildProgression(completedRows)
        let diagnostics = buildDiagnostics(coverage: coverage, fatigue: fatigue, adherence: adherence, progression: progression)

        return ProgramAnalyticsReport(
            coverage: coverage,
            balance: balance,
            fatigue: fatigue,
            efficiency: efficiency,
            adherence: adherence,
            progression: progression,
            diagnostics: diagnostics
        )
    }

    // MARK: - Coverage

    private static func buildCoverage(_ rows: [PlannedRow]) -> CoverageAnalytics {
        var setsByMuscle: [String: Double] = [:]
        var repsByMuscle: [String: Double] = [:]
        var timeByMuscle: [String: Double] = [:]
        var directByMuscle: [String: Double] = [:]
        var indirectByMuscle: [String: Double] = [:]
        var stabilityBuckets: [String: Double] = [:]
        var stabilityDemand = 0.0

        for row in rows {
            let sets = Double(row.exercise.effectiveSetCount())
            guard sets > 0.0 else { continue }
            let targetReps = row.exercise.sets.compactMap { $0.targetReps }.averageOrNull() ?? 8.0
            let restSeconds = Double(row.exercise.restTime ?? row.info?.averageRestSeconds ?? 90)
            let estimatedMinutes = sets * max(restSeconds + 45, 30) / 60.0
            let stability = stabilityDemandFor(row)
            let stabilityLabel = stabilityBucketLabel(stability)
            stabilityBuckets[stabilityLabel, default: 0.0] += sets
            stabilityDemand += stability * sets
            let relevantMuscles = SessionMuscleFilter.relevantMusclesFor(row.info)
            let contributions = VolumeCalculator.buildPerExerciseMuscleContributions(relevantMuscles)
            for (canonical, contribution) in contributions {
                setsByMuscle[canonical, default: 0.0] += sets * contribution
                repsByMuscle[canonical, default: 0.0] += sets * targetReps * contribution
                timeByMuscle[canonical, default: 0.0] += estimatedMinutes * contribution
                let hasPrimaryInGroup = relevantMuscles.contains { m in
                    VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, emphasis: m.emphasis) == canonical && m.role == .primary
                }
                if hasPrimaryInGroup {
                    directByMuscle[canonical, default: 0.0] += sets * contribution
                } else {
                    indirectByMuscle[canonical, default: 0.0] += sets * contribution
                }
            }
        }

        let forgotten = trackedMuscles.filter { (setsByMuscle[$0] ?? 0.0) < 1.0 }
        let plannedCount = max(rows.count, 1)

        let allMuscleKeys = Array(Set(setsByMuscle.keys).union(indirectByMuscle.keys)).sorted()
        let directIndirectMetrics = allMuscleKeys.map { muscle in
            DirectIndirectMetric(
                muscle: muscle,
                directSets: (directByMuscle[muscle] ?? 0.0).round1(),
                indirectSets: (indirectByMuscle[muscle] ?? 0.0).round1(),
                explanation: "Primarios vs secundarios/estabilizadores desde ExerciseMuscleInfo."
            )
        }

        let totalEffectiveSets = rows.reduce(0.0) { $0 + Double($1.exercise.effectiveSetCount()) }

        return CoverageAnalytics(
            musclesByWeeklySets: setsByMuscle.toMuscleMetrics("series semanales planificadas"),
            forgottenMuscles: forgotten,
            repsByMuscle: repsByMuscle.toMuscleMetrics("repeticiones planificadas ponderadas"),
            timeMinutesByMuscle: timeByMuscle.toMuscleMetrics("minutos estimados por descansos y series"),
            directIndirectByMuscle: directIndirectMetrics,
            unilateralExerciseRatio: Double(rows.filter { $0.exercise.isEffectivelyUnilateral() }.count) / Double(plannedCount),
            stabilityDemand: (stabilityDemand / max(totalEffectiveSets, 1.0)).round2(),
            stabilityDistribution: stabilityBuckets.toNamedMetrics("series por demanda de estabilidad"),
            strengthSpecificityRatio: Double(rows.filter { $0.exercise.isCompetitionLift || $0.info?.tier?.uppercased() == "T1" }.count) / Double(plannedCount),
            emptyReason: rows.isEmpty ? "No hay ejercicios planificados para analizar." : nil
        )
    }

    // MARK: - Balance

    private static func buildBalance(_ rows: [PlannedRow]) -> BalanceAnalytics {
        var patternMap: [String: Double] = [:]
        var push = 0.0
        var pull = 0.0
        var horizontalPush = 0.0
        var horizontalPull = 0.0
        var verticalPush = 0.0
        var verticalPull = 0.0
        var quads = 0.0
        var posterior = 0.0
        var upper = 0.0
        var lower = 0.0

        for row in rows {
            let sets = Double(row.exercise.effectiveSetCount())
            guard let info = row.info else { continue }
            let pattern = movementPatternFor(info)
            patternMap[pattern, default: 0.0] += sets
            let forceLower = info.force?.normalizeForAnalytics() ?? ""
            let chainLower = info.chain?.normalizeForAnalytics() ?? ""
            let bodyPart = info.bodyPart?.normalizeForAnalytics() ?? ""
            switch pattern {
            case "Empuje horizontal":
                push += sets; horizontalPush += sets
            case "Empuje vertical":
                push += sets; verticalPush += sets
            case "Tirón horizontal":
                pull += sets; horizontalPull += sets
            case "Tirón vertical":
                pull += sets; verticalPull += sets
            default:
                if forceLower.contains("empuje") { push += sets }
                if forceLower.contains("tiron") || forceLower.contains("traccion") || forceLower.contains("jalon") { pull += sets }
            }
            if bodyPart == "upper" { upper += sets }
            if bodyPart == "lower" { lower += sets }
            for muscle in info.involvedMuscles {
                let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                let contribution = VolumeCalculator.resolveMuscleVolumeContribution(muscle)
                if canonical == "Cuádriceps" || chainLower == "anterior" {
                    quads += sets * contribution
                }
                if ["Isquiosurales", "Glúteos", "Erectores Espinales"].contains(canonical) || chainLower == "posterior" {
                    posterior += sets * contribution
                }
            }
        }

        var notes: [String] = []
        if pull > 0.0 && push / pull > 1.5 { notes.append("Empuje domina sobre tirón.") }
        if push > 0.0 && pull / push > 1.5 { notes.append("Tirón domina sobre empuje.") }
        if posterior > 0.0 && quads / posterior > 1.6 { notes.append("Cuádriceps domina sobre cadena posterior.") }
        if quads > 0.0 && posterior / quads > 1.6 { notes.append("Cadena posterior domina sobre cuádriceps.") }

        return BalanceAnalytics(
            movementPatterns: patternMap.toNamedMetrics("series planificadas por patrón"),
            pushPullRatio: ratio("Empuje", push, "Tirón", pull, "Series de fuerza por patrón force."),
            horizontalPushPullRatio: ratio("Empuje H", horizontalPush, "Tirón H", horizontalPull, "Empuje horizontal frente a tirón horizontal."),
            verticalPushPullRatio: ratio("Empuje V", verticalPush, "Tirón V", verticalPull, "Empuje vertical frente a tirón vertical."),
            quadPosteriorRatio: ratio("Quad", quads, "Posterior", posterior, "Aportes musculares ponderados por rol."),
            upperLowerRatio: ratio("Superior", upper, "Inferior", lower, "Series agrupadas por bodyPart."),
            notes: notes
        )
    }

    // MARK: - Fatigue

    private static func buildFatigue(
        plannedRows: [PlannedRow],
        completedRows: [CompletedRow]
    ) -> FatigueAnalytics {
        let sourceRows: [FatigueSource] = completedRows.isEmpty
            ? plannedRows.map { fatigueSource($0) }
            : completedRows.map { fatigueSource($0) }

        var structures: [String: Double] = Dictionary(
            uniqueKeysWithValues: tendonStructureKeywords.keys.map { ($0, 0.0) }
        )
        var muscleStress: [String: Double] = [:]
        var dayStress: [String: Double] = [:]
        var axial = 0.0
        var grip = 0.0
        var lumbar = 0.0
        var shoulder = 0.0

        for row in sourceRows {
            guard let info = row.info else { continue }
            let sets = row.sets
            let intensity = row.rpe.map { min(max($0 / 8.0, 0.7), 1.35) } ?? 1.0
            let localStress = sets * (info.efc ?? 2.5) * intensity
            let systemic = sets * (info.cnc ?? 2.5) * intensity
            let structural = sets * (info.ssc ?? 0.5) * intensity
            if let day = row.dayLabel {
                dayStress[day, default: 0.0] += localStress + systemic + structural
            }
            axial += sets * (info.axialLoadFactor ?? 0.0)
            if info.strapsRecommended == true || info.involvedMuscles.contains(where: { $0.muscle.normalizeForAnalytics().contains("antebrazo") }) {
                grip += sets * 1.0
            }
            for muscle in info.involvedMuscles {
                let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, emphasis: muscle.emphasis)
                let contribution = VolumeCalculator.resolveMuscleVolumeContribution(muscle)
                muscleStress[canonical, default: 0.0] += localStress * contribution
                if canonical == "Erectores Espinales" {
                    lumbar += structural + localStress * contribution
                }
                if canonical == "Pectorales" || muscle.emphasis?.normalizeForAnalytics().contains("anterior") == true {
                    shoulder += structural * 0.6 + systemic * 0.12
                }
            }
            let haystack = "\(info.name) \(info.force ?? "") \(info.chain ?? "") \(info.involvedMuscles.map(\.muscle).joined(separator: " "))"
                .normalizeForAnalytics()
            for (structure, keywords) in tendonStructureKeywords {
                if keywords.contains(where: { haystack.contains($0.normalizeForAnalytics()) }) {
                    structures[structure, default: 0.0] += structural + (info.ttc ?? 1.0) * sets
                }
            }
        }

        return FatigueAnalytics(
            structures: structures.toNamedMetrics("estrés estimado por estructura articular/tendinosa"),
            residualHeatmap: muscleStress.toMuscleMetrics("fatiga local residual estimada"),
            residualCalendar: dayStress.toNamedMetrics("nube de fatiga por día o sesión"),
            recoveryDebtByMuscle: muscleStress.mapValues { min($0.value / 10.0, 100.0) }
                .toMuscleMetrics("deuda de recuperación estimada"),
            readinessByMuscle: muscleStress.mapValues { min(max(100.0 - $0.value / 10.0, 0.0), 100.0) }
                .toMuscleMetrics("readiness estimado desde estrés residual"),
            axialLoad: axial.round1(),
            gripDemand: grip.round1(),
            lumbarFatigue: lumbar.round1(),
            anteriorShoulderStress: shoulder.round1(),
            sourceSummary: completedRows.isEmpty
                ? "Usa plan porque no hay logs suficientes."
                : "Usa logs reales completados."
        )
    }

    // MARK: - Efficiency

    private static func buildEfficiency(
        plannedRows: [PlannedRow],
        completedRows: [CompletedRow],
        logs: [WorkoutLog]
    ) -> EfficiencyAnalytics {
        let completedSets = completedRows.reduce(0) { $0 + $1.exercise.sets.filter { !$0.skipped }.count }
        let durationHours = max(Double(logs.reduce(0) { $0 + $1.durationMinutes }) / 60.0, 1.0 / 60.0)

        let densityBySession: [NamedMetric] = logs.map { log -> NamedMetric in
            let sets = log.completedExercises.reduce(0) { $0 + $1.sets.filter { !$0.skipped }.count }
            let density = Double(sets) / max(Double(log.durationMinutes) / 60.0, 1.0 / 60.0)
            return NamedMetric(id: log.id, label: log.sessionName, value: density.round1(), explanation: "series completadas por hora")
        }.sorted { $0.value > $1.value }

        let exerciseVolumes: [NamedMetric] = {
            var volumesByExercise: [String: Double] = [:]
            for row in completedRows {
                let key = row.exercise.exerciseName
                let vol = row.exercise.sets.reduce(0.0) { $0 + $1.weight * Double(max(1, $1.reps)) }
                volumesByExercise[key, default: 0.0] += vol
            }
            return volumesByExercise.map { (name, value) in
                NamedMetric(id: name.normalizeId(), label: name, value: value.round1(), explanation: "volumen kg x reps acumulado")
            }.sorted { $0.value > $1.value }.prefix(5).map { $0 }
        }()

        let totalStimulus = exerciseVolumes.reduce(0.0) { $0 + $1.value }
        let safeTotalStimulus = max(totalStimulus, 0.001)
        let topCount = max(1, Int((Double(exerciseVolumes.count) * 0.2).rounded()))
        let topStimulusShare = exerciseVolumes.prefix(topCount).reduce(0.0) { $0 + $1.value } / safeTotalStimulus

        let fatiguing: [NamedMetric] = {
            var results: [(id: String, name: String, stress: Double)] = []
            for row in plannedRows {
                guard let info = row.info else { continue }
                let stress = Double(row.exercise.effectiveSetCount()) * ((info.efc ?? 2.5) + (info.cnc ?? 2.5) + (info.ssc ?? 0.5))
                results.append((id: info.id, name: info.name, stress: stress))
            }
            return results.map { NamedMetric(id: $0.id, label: $0.name, value: $0.stress.round1(), explanation: "EFC + CNC + SSC por series planificadas.") }
                .sorted { $0.value > $1.value }
                .prefix(5)
                .map { $0 }
        }()

        let actualRestValues = completedRows.flatMap { row -> [Double] in
            let rest = Double(row.exercise.restTime)
            return row.exercise.sets.map { _ in rest }
        }
        let actualRest = actualRestValues.averageOrNull()
        let recommendedRestValues = completedRows.compactMap { $0.info?.averageRestSeconds }.map { Double($0) }
        let recommendedRest = recommendedRestValues.averageOrNull()

        return EfficiencyAnalytics(
            densitySetsPerHour: (Double(completedSets) / durationHours).round1(),
            sessionDensity: densityBySession,
            paretoTopExercises: exerciseVolumes,
            topStimulusShare: topStimulusShare.round2(),
            topFatiguingExercises: fatiguing,
            restCompliance: ratio(
                "Real", actualRest ?? 0.0,
                "Recomendado", recommendedRest ?? 0.0,
                "Descanso real del log frente a averageRestSeconds del catálogo."
            ),
            blockIdentity: inferBlockIdentity(plannedRows)
        )
    }

    // MARK: - Adherence

    private static func buildAdherence(rows: [PlannedRow], logs: [WorkoutLog]) -> AdherenceAnalytics {
        let plannedSessions = max(Set(rows.map(\.sessionId)).count, 1)
        let completedSessions = Set(logs.map(\.sessionId)).count
        let plannedExercises = max(rows.count, 1)
        let completedExercises = Set(logs.flatMap(\.completedExercises).map(\.exerciseId)).count

        let omissionGroup = Dictionary(grouping: logs.flatMap(\.omittedExercises), by: { $0.exerciseName.isEmpty ? $0.exerciseId : $0.exerciseName })
        let omissions = omissionGroup.map { (name, items) in
            NamedMetric(id: name.normalizeId(), label: name, value: Double(items.count), explanation: "Omitido en logs reales.")
        }.sorted { $0.value > $1.value }

        let ratioSessions = Double(completedSessions) / Double(plannedSessions)
        let ratioExercises = Double(completedExercises) / Double(plannedExercises)

        let diagnosis: String
        if ratioSessions < 0.6 && omissions.isEmpty {
            diagnosis = "Ejecución baja: faltan sesiones completas antes de culpar el programa."
        } else if ratioExercises < 0.7 || !omissions.isEmpty {
            diagnosis = "Ejecución irregular: hay ejercicios omitidos o baja cobertura real."
        } else {
            diagnosis = "Ejecución consistente; si hay estancamiento, revisar diseño del programa."
        }

        let skippedCount = logs.flatMap(\.completedExercises).reduce(0) { $0 + $1.sets.filter(\.skipped).count }

        return AdherenceAnalytics(
            completedSessionRatio: min(max(ratioSessions, 0.0), 1.0).round2(),
            completedExerciseRatio: min(max(ratioExercises, 0.0), 1.0).round2(),
            omittedExercises: omissions,
            skippedExerciseCount: skippedCount,
            diagnosis: diagnosis
        )
    }

    // MARK: - Progression

    private static func buildProgression(_ rows: [CompletedRow]) -> [ExerciseProgressionAnalytics] {
        let grouped = Dictionary(grouping: rows, by: { $0.exercise.canonicalExerciseId ?? $0.exercise.exerciseDbId ?? $0.exercise.exerciseId ?? "" })
        var results: [ExerciseProgressionAnalytics] = []

        for (id, groupedRows) in grouped {
            guard !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            let ordered = groupedRows.sorted { $0.log.date < $1.log.date }
            let values = ordered.compactMap { row -> Double? in
                row.exercise.sets.compactMap { $0.estimatedE1rm() }.max()
            }
            guard !values.isEmpty else { continue }
            let first = values.first!
            let last = values.last!
            let recent = values.suffix(4).map { $0 }
            let stagnation: Double
            if recent.count >= 3, let maxVal = recent.max(), let minVal = recent.min(), maxVal - minVal < 1.0 {
                stagnation = 0.85
            } else {
                stagnation = 0.2
            }
            results.append(ExerciseProgressionAnalytics(
                exerciseId: id,
                exerciseName: ordered.last!.exercise.exerciseName,
                firstE1rm: first.round1(),
                lastE1rm: last.round1(),
                deltaE1rm: (last - first).round1(),
                sparkline: values.map { $0.round1() },
                stagnationRisk: stagnation
            ))
        }

        return results.sorted { abs($0.deltaE1rm ?? 0.0) > abs($1.deltaE1rm ?? 0.0) }
    }

    // MARK: - Diagnostics

    private static func buildDiagnostics(
        coverage: CoverageAnalytics,
        fatigue: FatigueAnalytics,
        adherence: AdherenceAnalytics,
        progression: [ExerciseProgressionAnalytics]
    ) -> [ProgramDiagnostic] {
        var diagnostics: [ProgramDiagnostic] = []

        if !coverage.forgottenMuscles.isEmpty {
            diagnostics.append(ProgramDiagnostic(
                id: "forgotten-muscles",
                title: "Músculos sin cobertura",
                detail: coverage.forgottenMuscles.joined(separator: ", "),
                severity: .warning
            ))
        }
        if fatigue.lumbarFatigue >= 18.0 {
            diagnostics.append(ProgramDiagnostic(
                id: "high-lumbar-fatigue",
                title: "Fatiga lumbar alta",
                detail: "La suma de bisagras, remos pesados y carga axial concentra estrés lumbar.",
                severity: .critical
            ))
        }
        if fatigue.anteriorShoulderStress >= 14.0 {
            diagnostics.append(ProgramDiagnostic(
                id: "anterior-shoulder-stress",
                title: "Estrés de hombro anterior",
                detail: "El volumen de presses y pectoral eleva el estrés anterior.",
                severity: .warning
            ))
        }
        if adherence.completedExerciseRatio < 0.7 {
            diagnostics.append(ProgramDiagnostic(
                id: "execution-first",
                title: "Primero adherencia",
                detail: adherence.diagnosis,
                severity: .warning
            ))
        }
        for item in progression.filter({ $0.stagnationRisk >= 0.8 }).prefix(3) {
            diagnostics.append(ProgramDiagnostic(
                id: "stagnation-\(item.exerciseId)",
                title: "Riesgo de estancamiento",
                detail: "\(item.exerciseName): e1RM reciente casi plano.",
                severity: .warning
            ))
        }

        return diagnostics
    }

    // MARK: - Internal Row Types

    fileprivate struct PlannedRow {
        let sessionId: String
        let sessionName: String
        let dayOfWeek: Int?
        let exercise: Exercise
        let info: ExerciseMuscleInfo?
    }

    fileprivate struct CompletedRow {
        let log: WorkoutLog
        let exercise: CompletedExercise
        let info: ExerciseMuscleInfo?
    }

    private struct FatigueSource {
        let sets: Double
        let rpe: Double?
        let dayLabel: String?
        let info: ExerciseMuscleInfo?
    }

    // MARK: - Row Builders

    private static func buildPlannedRows(_ program: Program, catalog: [String: ExerciseMuscleInfo]) -> [PlannedRow] {
        program.plannedExercises().map { enrichRow($0, catalog: catalog) }
    }

    private static func buildCompletedRows(logs: [WorkoutLog], catalog: [String: ExerciseMuscleInfo]) -> [CompletedRow] {
        logs.flatMap { log in
            log.completedExercises.map { exercise in
                CompletedRow(
                    log: log,
                    exercise: exercise,
                    info: exercise.infoFrom(catalog)
                )
            }
        }
    }

    private static func enrichRow(_ row: PlannedRow, catalog: [String: ExerciseMuscleInfo]) -> PlannedRow {
        let resolved = row.exercise.exerciseDbId?.lowercased().flatMap { catalog[$0] }
            ?? row.exercise.canonicalExerciseId?.lowercased().flatMap { catalog[$0] }
            ?? row.exercise.exerciseId?.lowercased().flatMap { catalog[$0] }
        return PlannedRow(
            sessionId: row.sessionId,
            sessionName: row.sessionName,
            dayOfWeek: row.dayOfWeek,
            exercise: row.exercise,
            info: resolved
        )
    }

    // MARK: - Fatigue Source Conversions

    private static func fatigueSource(_ row: PlannedRow) -> FatigueSource {
        FatigueSource(
            sets: Double(row.exercise.effectiveSetCount()),
            rpe: row.exercise.sets.compactMap(\.targetRPE).averageOrNull(),
            dayLabel: row.dayOfWeek.map(dayLabelFor) ?? row.sessionName,
            info: row.info
        )
    }

    private static func fatigueSource(_ row: CompletedRow) -> FatigueSource {
        FatigueSource(
            sets: Double(row.exercise.sets.filter { !$0.skipped }.count),
            rpe: row.exercise.sets.compactMap(\.rpe).averageOrNull(),
            dayLabel: row.log.sessionName.isEmpty ? String(row.log.date.prefix(10)) : row.log.sessionName,
            info: row.info
        )
    }

    // MARK: - Program Extension

    private static func plannedExercises(_ program: Program) -> [PlannedRow] {
        program.macrocycles
            .flatMap(\.blocks)
            .flatMap(\.mesocycles)
            .flatMap(\.weeks)
            .flatMap { week in
                week.sessions.flatMap { session in
                    session.allExercises().map { exercise in
                        PlannedRow(
                            sessionId: session.id,
                            sessionName: session.name,
                            dayOfWeek: nil,
                            exercise: exercise,
                            info: nil
                        )
                    }
                }
            }
    }

    // MARK: - CompletedExercise Info Lookup

    private static func infoFrom(_ exercise: CompletedExercise, catalog: [String: ExerciseMuscleInfo]) -> ExerciseMuscleInfo? {
        exercise.exerciseDbId?.lowercased().flatMap { catalog[$0] }
            ?? exercise.canonicalExerciseId?.lowercased().flatMap { catalog[$0] }
            ?? exercise.exerciseId.lowercased().flatMap { catalog[$0] }
    }

    // MARK: - Exercise Helpers

    private static func effectiveSetCount(_ exercise: Exercise) -> Int {
        let counted = exercise.sets.filter { set in
            !set.isIneffective && ((set.targetReps ?? 0) > 0 || (set.weight ?? 0.0) > 0.0)
        }.count
        return counted > 0 ? counted : exercise.sets.filter { !$0.isIneffective }.count
    }

    private static func estimatedE1rm(_ set: CompletedSet) -> Double? {
        guard !set.skipped, set.reps > 0, set.weight > 0.0 else { return nil }
        let safeReps = min(set.reps, 36)
        return set.weight * 36.0 / (37.0 - Double(safeReps))
    }

    // MARK: - Metric Builders

    private static func toMuscleMetrics(_ map: [String: Double], explanation: String) -> [MuscleMetric] {
        map.filter { $0.value > 0.0 }
            .sorted { $0.value > $1.value }
            .map { MuscleMetric(id: $0.key.normalizeId(), name: $0.key, value: $0.value.round1(), explanation: explanation) }
    }

    private static func toNamedMetrics(_ map: [String: Double], explanation: String) -> [NamedMetric] {
        map.filter { $0.value > 0.0 }
            .sorted { $0.value > $1.value }
            .map { NamedMetric(id: $0.key.normalizeId(), label: $0.key, value: $0.value.round1(), explanation: explanation) }
    }

    private static func ratio(_ left: String, _ leftValue: Double, _ right: String, _ rightValue: Double, _ explanation: String) -> RatioMetric {
        let r: Double
        if rightValue > 0.0 {
            r = (leftValue / rightValue).round2()
        } else if leftValue > 0.0 {
            r = .infinity
        } else {
            r = 0.0
        }
        return RatioMetric(
            leftLabel: left,
            leftValue: leftValue.round1(),
            rightLabel: right,
            rightValue: rightValue.round1(),
            ratio: r,
            explanation: explanation
        )
    }

    // MARK: - Pattern / Stability Analysis

    private static func movementPatternFor(_ info: ExerciseMuscleInfo) -> String {
        let text = "\(info.name) \(info.force ?? "") \(info.chain ?? "")".normalizeForAnalytics()
        if ["press banca", "press de banca", "fondos", "flexion", "apertura", "cruce", "crossover"].contains(where: { text.contains($0) }) { return "Empuje horizontal" }
        if ["press militar", "press hombro", "press de hombro", "overhead", "arnold"].contains(where: { text.contains($0) }) { return "Empuje vertical" }
        if ["remo", "row", "face pull"].contains(where: { text.contains($0) }) { return "Tirón horizontal" }
        if ["dominada", "jalon", "pull up", "chin up", "pullover"].contains(where: { text.contains($0) }) { return "Tirón vertical" }
        if ["sentadilla", "squat", "prensa", "hack", "sissy"].contains(where: { text.contains($0) }) { return "Squat pattern" }
        if ["peso muerto", "rdl", "rumano", "bisagra", "hip thrust", "buenos dias"].contains(where: { text.contains($0) }) { return "Hinge pattern" }
        if ["zancada", "lunge", "bulgara", "step up", "unilateral"].contains(where: { text.contains($0) }) { return "Unilateral/lunge" }
        if ["carry", "farmer", "suitcase", "yoke"].contains(where: { text.contains($0) }) { return "Carry" }
        if ["pallof", "anti rotacion"].contains(where: { text.contains($0) }) { return "Core anti-rotación" }
        if ["plancha", "ab wheel", "hollow", "anti extension"].contains(where: { text.contains($0) }) { return "Core anti-extensión" }
        if let force = info.force, !force.isEmpty { return force }
        return "Sin patrón"
    }

    private static func stabilityDemandFor(_ row: PlannedRow) -> Double {
        let info = row.info
        let equipment = info?.equipment?.normalizeForAnalytics() ?? ""
        let name = "\(info?.name ?? "") \(row.exercise.name)".normalizeForAnalytics()

        let base: Double
        if equipment.contains("maquina") || equipment.contains("smith") { base = 1.0 }
        else if equipment.contains("polea") { base = 2.0 }
        else if equipment.contains("barra") { base = 3.0 }
        else if equipment.contains("mancuerna") || equipment.contains("kettlebell") { base = 3.3 }
        else if equipment.contains("peso corporal") || equipment.contains("trx") || equipment.contains("banda") { base = 3.5 }
        else { base = 2.5 }

        let unilateralBonus = (row.exercise.isEffectivelyUnilateral() || name.contains("unilateral") || name.contains("bulgara") || name.contains("zancada")) ? 1.0 : 0.0
        let carryBonus = (name.contains("carry") || name.contains("farmer") || name.contains("suitcase")) ? 0.8 : 0.0

        return min(max(base + unilateralBonus + carryBonus, 1.0), 5.0)
    }

    private static func stabilityBucketLabel(_ value: Double) -> String {
        if value < 1.6 { return "Muy estable" }
        if value < 2.6 { return "Estable/moderada" }
        if value < 3.6 { return "Libre moderada" }
        if value < 4.4 { return "Inestable" }
        return "Alta demanda"
    }

    // MARK: - Block Identity

    private static func inferBlockIdentity(_ rows: [PlannedRow]) -> String {
        guard !rows.isEmpty else { return "Sin datos" }
        var patterns: [String: Int] = [:]
        for row in rows {
            let p = row.info.map(movementPatternFor) ?? "Sin patrón"
            patterns[p, default: 0] += 1
        }
        let upper = rows.filter { $0.info?.bodyPart?.lowercased() == "upper" }.count
        let lower = rows.filter { $0.info?.bodyPart?.lowercased() == "lower" }.count
        let t1 = rows.filter { $0.exercise.isCompetitionLift || $0.info?.tier?.uppercased() == "T1" }.count
        let machine = rows.filter { $0.info?.equipment?.normalizeForAnalytics().contains("maquina") == true }.count
        let isolation = rows.filter { $0.info?.type?.normalizeForAnalytics().contains("aislamiento") == true }.count
        let n = Double(rows.count)

        if Double(t1) / n >= 0.35 && Double(isolation) / n >= 0.25 { return "Powerbuilder" }
        if Double(t1) / n >= 0.45 { return "Fuerza base" }
        if Double(isolation) / n >= 0.45 || Double(machine) / n >= 0.45 { return "Bodybuilding puro" }
        if Double(upper) > Double(lower) * 1.5 { return "Torso dominante" }
        if Double(lower) > Double(upper) * 1.5 { return "Pierna dominante" }
        if patterns.count >= 7 { return "Full-body inteligente" }
        if rows.count <= 5 { return "Minimalista" }
        return "Programa Frankenstein"
    }

    // MARK: - Day Label

    private static func dayLabelFor(_ dayOfWeek: Int) -> String {
        switch dayOfWeek {
        case 1: return "Lunes"
        case 2: return "Martes"
        case 3: return "Miércoles"
        case 4: return "Jueves"
        case 5: return "Viernes"
        case 6: return "Sábado"
        case 7: return "Domingo"
        default: return "Día"
        }
    }
}

// MARK: - Program Extension (plannedExercises)

extension Program {
    fileprivate func plannedExercises() -> [ProgramAnalyticsEngine.PlannedRow] {
        macrocycles
            .flatMap(\.blocks)
            .flatMap(\.mesocycles)
            .flatMap(\.weeks)
            .flatMap { week in
                week.sessions.flatMap { session in
                    session.allExercises().map { exercise in
                        ProgramAnalyticsEngine.PlannedRow(
                            sessionId: session.id,
                            sessionName: session.name,
                            dayOfWeek: nil,
                            exercise: exercise,
                            info: nil
                        )
                    }
                }
            }
    }
}

// MARK: - Exercise Helpers

extension Exercise {
    fileprivate func effectiveSetCount() -> Int {
        let counted = sets.filter { set in
            !set.isIneffective && ((set.targetReps ?? 0) > 0 || (set.weight ?? 0.0) > 0.0)
        }.count
        return counted > 0 ? counted : sets.filter { !$0.isIneffective }.count
    }
}

extension CompletedSet {
    fileprivate func estimatedE1rm() -> Double? {
        guard !skipped, reps > 0, weight > 0.0 else { return nil }
        let safeReps = min(reps, 36)
        return weight * 36.0 / (37.0 - Double(safeReps))
    }
}

// MARK: - Dictionary Metric Builders

extension Dictionary where Key == String, Value == Double {
    fileprivate func toMuscleMetrics(_ explanation: String) -> [MuscleMetric] {
        filter { $0.value > 0.0 }
            .sorted { $0.value > $1.value }
            .map { MuscleMetric(id: $0.key.normalizeId(), name: $0.key, value: $0.value.round1(), explanation: explanation) }
    }

    fileprivate func toNamedMetrics(_ explanation: String) -> [NamedMetric] {
        filter { $0.value > 0.0 }
            .sorted { $0.value > $1.value }
            .map { NamedMetric(id: $0.key.normalizeId(), label: $0.key, value: $0.value.round1(), explanation: explanation) }
    }
}

// MARK: - Average Helpers

extension [Double] {
    fileprivate func averageOrNull() -> Double? {
        isEmpty ? nil : (reduce(0, +) / Double(count))
    }
}

extension [Int] {
    fileprivate func averageOrNull() -> Double? {
        isEmpty ? nil : Double(reduce(0, +)) / Double(count)
    }
}

// MARK: - String Normalization

extension String {
    fileprivate func normalizeForAnalytics() -> String {
        self.lowercased()
            .replacingOccurrences(of: "á", with: "a")
            .replacingOccurrences(of: "é", with: "e")
            .replacingOccurrences(of: "í", with: "i")
            .replacingOccurrences(of: "ó", with: "o")
            .replacingOccurrences(of: "ú", with: "u")
            .replacingOccurrences(of: "ü", with: "u")
            .replacingOccurrences(of: "ñ", with: "n")
    }

    fileprivate func normalizeId() -> String {
        normalizeForAnalytics()
            .replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    }
}

// MARK: - Double Rounding

extension Double {
    fileprivate func round1() -> Double {
        (self * 10.0).rounded() / 10.0
    }

    fileprivate func round2() -> Double {
        (self * 100.0).rounded() / 100.0
    }
}
