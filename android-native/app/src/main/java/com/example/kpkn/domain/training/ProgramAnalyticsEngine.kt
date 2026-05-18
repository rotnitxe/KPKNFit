package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class ProgramAnalyticsReport(
    val coverage: CoverageAnalytics,
    val balance: BalanceAnalytics,
    val fatigue: FatigueAnalytics,
    val efficiency: EfficiencyAnalytics,
    val adherence: AdherenceAnalytics,
    val progression: List<ExerciseProgressionAnalytics>,
    val diagnostics: List<ProgramDiagnostic>,
)

data class CoverageAnalytics(
    val musclesByWeeklySets: List<MuscleMetric>,
    val forgottenMuscles: List<String>,
    val repsByMuscle: List<MuscleMetric>,
    val timeMinutesByMuscle: List<MuscleMetric>,
    val directIndirectByMuscle: List<DirectIndirectMetric>,
    val unilateralExerciseRatio: Double,
    val stabilityDemand: Double,
    val stabilityDistribution: List<NamedMetric>,
    val strengthSpecificityRatio: Double,
    val emptyReason: String?,
)

data class BalanceAnalytics(
    val movementPatterns: List<NamedMetric>,
    val pushPullRatio: RatioMetric,
    val horizontalPushPullRatio: RatioMetric,
    val verticalPushPullRatio: RatioMetric,
    val quadPosteriorRatio: RatioMetric,
    val upperLowerRatio: RatioMetric,
    val notes: List<String>,
)

data class FatigueAnalytics(
    val structures: List<NamedMetric>,
    val residualHeatmap: List<MuscleMetric>,
    val residualCalendar: List<NamedMetric>,
    val recoveryDebtByMuscle: List<MuscleMetric>,
    val readinessByMuscle: List<MuscleMetric>,
    val axialLoad: Double,
    val gripDemand: Double,
    val lumbarFatigue: Double,
    val anteriorShoulderStress: Double,
    val sourceSummary: String,
)

data class EfficiencyAnalytics(
    val densitySetsPerHour: Double,
    val sessionDensity: List<NamedMetric>,
    val paretoTopExercises: List<NamedMetric>,
    val topStimulusShare: Double,
    val topFatiguingExercises: List<NamedMetric>,
    val restCompliance: RatioMetric,
    val blockIdentity: String,
)

data class AdherenceAnalytics(
    val completedSessionRatio: Double,
    val completedExerciseRatio: Double,
    val omittedExercises: List<NamedMetric>,
    val skippedExerciseCount: Int,
    val diagnosis: String,
)

data class ExerciseProgressionAnalytics(
    val exerciseId: String,
    val exerciseName: String,
    val firstE1rm: Double?,
    val lastE1rm: Double?,
    val deltaE1rm: Double?,
    val sparkline: List<Double>,
    val stagnationRisk: Double,
)

data class ProgramDiagnostic(
    val id: String,
    val title: String,
    val detail: String,
    val severity: DiagnosticSeverity,
)

enum class DiagnosticSeverity { INFO, WARNING, CRITICAL }

data class MuscleMetric(
    val id: String,
    val name: String,
    val value: Double,
    val explanation: String,
)

data class NamedMetric(
    val id: String,
    val label: String,
    val value: Double,
    val explanation: String,
)

data class DirectIndirectMetric(
    val muscle: String,
    val directSets: Double,
    val indirectSets: Double,
    val explanation: String,
)

data class RatioMetric(
    val leftLabel: String,
    val leftValue: Double,
    val rightLabel: String,
    val rightValue: Double,
    val ratio: Double,
    val explanation: String,
)

object ProgramAnalyticsEngine {

    private val trackedMuscles = listOf(
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
    )

    private val tendonStructureKeywords = mapOf(
        "Rodilla" to listOf("cuadriceps", "sentadilla", "prensa", "zancada", "sissy"),
        "Cadera" to listOf("glute", "peso muerto", "hip thrust", "bisagra"),
        "Lumbar" to listOf("erector", "lumbar", "peso muerto", "rdl", "pendlay", "remo"),
        "Hombro anterior" to listOf("press", "pectoral", "deltoides anterior"),
        "Codo" to listOf("biceps", "triceps", "curl", "extension"),
        "Muñeca" to listOf("agarre", "antebrazo", "curl", "remo"),
        "Tendón rotuliano" to listOf("cuadriceps", "sentadilla", "sissy", "prensa"),
        "Aquiles" to listOf("pantorrilla", "gemelo", "soleo", "salto"),
    )

    fun analyze(
        program: Program,
        logs: List<WorkoutLog>,
        exerciseCatalog: List<ExerciseMuscleInfo>,
    ): ProgramAnalyticsReport {
        val catalog = exerciseCatalog.associateBy { it.id.lowercase() }
        val plannedRows = program.plannedExercises().map { it.infoFrom(catalog) }
        val completedRows = logs.flatMap { log -> log.completedExercises.map { CompletedExerciseRow(log, it, it.infoFrom(catalog)) } }

        val coverage = buildCoverage(plannedRows)
        val balance = buildBalance(plannedRows)
        val fatigue = buildFatigue(plannedRows, completedRows)
        val efficiency = buildEfficiency(plannedRows, completedRows, logs)
        val adherence = buildAdherence(plannedRows, logs)
        val progression = buildProgression(completedRows)
        val diagnostics = buildDiagnostics(coverage, fatigue, adherence, progression)

        return ProgramAnalyticsReport(
            coverage = coverage,
            balance = balance,
            fatigue = fatigue,
            efficiency = efficiency,
            adherence = adherence,
            progression = progression,
            diagnostics = diagnostics,
        )
    }

    private fun buildCoverage(rows: List<PlannedExerciseRow>): CoverageAnalytics {
        val setsByMuscle = mutableMapOf<String, Double>()
        val repsByMuscle = mutableMapOf<String, Double>()
        val timeByMuscle = mutableMapOf<String, Double>()
        val directByMuscle = mutableMapOf<String, Double>()
        val indirectByMuscle = mutableMapOf<String, Double>()
        val stabilityBuckets = mutableMapOf<String, Double>()
        var stabilityDemand = 0.0

        rows.forEach { row ->
            val sets = row.exercise.effectiveSetCount().toDouble()
            if (sets <= 0.0) return@forEach
            val targetReps = row.exercise.sets.mapNotNull { it.targetReps }.averageOrNull() ?: 8.0
            val restSeconds = row.exercise.restTime ?: row.info?.averageRestSeconds ?: 90
            val estimatedMinutes = sets * ((restSeconds + 45).coerceAtLeast(30)) / 60.0
            val stability = stabilityDemandFor(row)
            val stabilityLabel = stabilityBucketLabel(stability)
            stabilityBuckets[stabilityLabel] = stabilityBuckets.orZero(stabilityLabel) + sets
            stabilityDemand += stability * sets
            row.info?.involvedMuscles.orEmpty().forEach { muscle ->
                val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val contribution = resolveMuscleVolumeContribution(muscle)
                setsByMuscle[canonical] = setsByMuscle.orZero(canonical) + sets * contribution
                repsByMuscle[canonical] = repsByMuscle.orZero(canonical) + sets * targetReps * contribution
                timeByMuscle[canonical] = timeByMuscle.orZero(canonical) + estimatedMinutes * contribution
                if (muscle.role == MuscleRole.PRIMARY) {
                    directByMuscle[canonical] = directByMuscle.orZero(canonical) + sets * contribution
                } else {
                    indirectByMuscle[canonical] = indirectByMuscle.orZero(canonical) + sets * contribution
                }
            }
        }

        val forgotten = trackedMuscles.filter { setsByMuscle.orZero(it) < 1.0 }
        val plannedCount = rows.size.coerceAtLeast(1)
        return CoverageAnalytics(
            musclesByWeeklySets = setsByMuscle.toMuscleMetrics("series semanales planificadas"),
            forgottenMuscles = forgotten,
            repsByMuscle = repsByMuscle.toMuscleMetrics("repeticiones planificadas ponderadas"),
            timeMinutesByMuscle = timeByMuscle.toMuscleMetrics("minutos estimados por descansos y series"),
            directIndirectByMuscle = (directByMuscle.keys + indirectByMuscle.keys)
                .distinct()
                .sorted()
                .map { muscle ->
                    DirectIndirectMetric(
                        muscle = muscle,
                        directSets = directByMuscle.orZero(muscle).round1(),
                        indirectSets = indirectByMuscle.orZero(muscle).round1(),
                        explanation = "Primarios vs secundarios/estabilizadores desde ExerciseMuscleInfo.",
                    )
                },
            unilateralExerciseRatio = rows.count { it.exercise.isEffectivelyUnilateral() }.toDouble() / plannedCount,
            stabilityDemand = (stabilityDemand / rows.sumOf { it.exercise.effectiveSetCount() }.coerceAtLeast(1)).round2(),
            stabilityDistribution = stabilityBuckets.toNamedMetrics("series por demanda de estabilidad"),
            strengthSpecificityRatio = rows.count { it.exercise.isCompetitionLift || it.info?.tier.equals("T1", true) }.toDouble() / plannedCount,
            emptyReason = if (rows.isEmpty()) "No hay ejercicios planificados para analizar." else null,
        )
    }

    private fun buildBalance(rows: List<PlannedExerciseRow>): BalanceAnalytics {
        val patternMap = mutableMapOf<String, Double>()
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

        rows.forEach { row ->
            val sets = row.exercise.effectiveSetCount().toDouble()
            val info = row.info ?: return@forEach
            val pattern = movementPatternFor(info)
            patternMap[pattern] = patternMap.orZero(pattern) + sets
            val forceLower = info.force.normalizeForAnalytics()
            val chainLower = info.chain.normalizeForAnalytics()
            val bodyPart = info.bodyPart.normalizeForAnalytics()
            when (pattern) {
                "Empuje horizontal" -> {
                    push += sets
                    horizontalPush += sets
                }
                "Empuje vertical" -> {
                    push += sets
                    verticalPush += sets
                }
                "Tirón horizontal" -> {
                    pull += sets
                    horizontalPull += sets
                }
                "Tirón vertical" -> {
                    pull += sets
                    verticalPull += sets
                }
                else -> {
                    if (forceLower.contains("empuje")) push += sets
                    if (forceLower.contains("tiron") || forceLower.contains("traccion") || forceLower.contains("jalon")) pull += sets
                }
            }
            if (bodyPart == "upper") upper += sets
            if (bodyPart == "lower") lower += sets
            info.involvedMuscles.forEach { muscle ->
                val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val contribution = resolveMuscleVolumeContribution(muscle)
                if (canonical == "Cuádriceps" || chainLower == "anterior") quads += sets * contribution
                if (canonical in setOf("Isquiosurales", "Glúteos", "Erectores Espinales") || chainLower == "posterior") {
                    posterior += sets * contribution
                }
            }
        }

        val notes = buildList {
            if (pull > 0.0 && push / pull > 1.5) add("Empuje domina sobre tirón.")
            if (push > 0.0 && pull / push > 1.5) add("Tirón domina sobre empuje.")
            if (posterior > 0.0 && quads / posterior > 1.6) add("Cuádriceps domina sobre cadena posterior.")
            if (quads > 0.0 && posterior / quads > 1.6) add("Cadena posterior domina sobre cuádriceps.")
        }

        return BalanceAnalytics(
            movementPatterns = patternMap.toNamedMetrics("series planificadas por patrón"),
            pushPullRatio = ratio("Empuje", push, "Tirón", pull, "Series de fuerza por patrón force."),
            horizontalPushPullRatio = ratio("Empuje H", horizontalPush, "Tirón H", horizontalPull, "Empuje horizontal frente a tirón horizontal."),
            verticalPushPullRatio = ratio("Empuje V", verticalPush, "Tirón V", verticalPull, "Empuje vertical frente a tirón vertical."),
            quadPosteriorRatio = ratio("Quad", quads, "Posterior", posterior, "Aportes musculares ponderados por rol."),
            upperLowerRatio = ratio("Superior", upper, "Inferior", lower, "Series agrupadas por bodyPart."),
            notes = notes,
        )
    }

    private fun buildFatigue(
        plannedRows: List<PlannedExerciseRow>,
        completedRows: List<CompletedExerciseRow>,
    ): FatigueAnalytics {
        val sourceRows = if (completedRows.isNotEmpty()) completedRows.map { it.asFatigueSource() } else plannedRows.map { it.asFatigueSource() }
        val structures = tendonStructureKeywords.keys.associateWith { 0.0 }.toMutableMap()
        val muscleStress = mutableMapOf<String, Double>()
        val dayStress = mutableMapOf<String, Double>()
        var axial = 0.0
        var grip = 0.0
        var lumbar = 0.0
        var shoulder = 0.0

        sourceRows.forEach { row ->
            val info = row.info ?: return@forEach
            val sets = row.sets
            val intensity = row.rpe?.let { (it / 8.0).coerceIn(0.7, 1.35) } ?: 1.0
            val localStress = sets * (info.efc ?: 2.5) * intensity
            val systemic = sets * (info.cnc ?: 2.5) * intensity
            val structural = sets * (info.ssc ?: 0.5) * intensity
            row.dayLabel?.let { day -> dayStress[day] = dayStress.orZero(day) + localStress + systemic + structural }
            axial += sets * (info.axialLoadFactor ?: 0.0)
            if (info.strapsRecommended == true || info.involvedMuscles.any { it.muscle.normalizeForAnalytics().contains("antebrazo") }) {
                grip += sets * 1.0
            }
            info.involvedMuscles.forEach { muscle ->
                val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val contribution = resolveMuscleVolumeContribution(muscle)
                muscleStress[canonical] = muscleStress.orZero(canonical) + localStress * contribution
                if (canonical == "Erectores Espinales") lumbar += structural + localStress * contribution
                if (canonical == "Pectorales" || muscle.emphasis.normalizeForAnalytics().contains("anterior")) {
                    shoulder += structural * 0.6 + systemic * 0.12
                }
            }
            val haystack = "${info.name} ${info.force} ${info.chain} ${info.involvedMuscles.joinToString(" ") { it.muscle }}"
                .normalizeForAnalytics()
            tendonStructureKeywords.forEach { (structure, keywords) ->
                if (keywords.any { haystack.contains(it.normalizeForAnalytics()) }) {
                    structures[structure] = structures.orZero(structure) + structural + (info.ttc ?: 1.0) * sets
                }
            }
        }

        return FatigueAnalytics(
            structures = structures.toNamedMetrics("estrés estimado por estructura articular/tendinosa"),
            residualHeatmap = muscleStress.toMuscleMetrics("fatiga local residual estimada"),
            residualCalendar = dayStress.toNamedMetrics("nube de fatiga por día o sesión"),
            recoveryDebtByMuscle = muscleStress.mapValues { (_, value) -> (value / 10.0).coerceAtMost(100.0) }
                .toMuscleMetrics("deuda de recuperación estimada"),
            readinessByMuscle = muscleStress.mapValues { (_, value) -> (100.0 - value / 10.0).coerceIn(0.0, 100.0) }
                .toMuscleMetrics("readiness estimado desde estrés residual"),
            axialLoad = axial.round1(),
            gripDemand = grip.round1(),
            lumbarFatigue = lumbar.round1(),
            anteriorShoulderStress = shoulder.round1(),
            sourceSummary = if (completedRows.isNotEmpty()) "Usa logs reales completados." else "Usa plan porque no hay logs suficientes.",
        )
    }

    private fun buildEfficiency(
        plannedRows: List<PlannedExerciseRow>,
        completedRows: List<CompletedExerciseRow>,
        logs: List<WorkoutLog>,
    ): EfficiencyAnalytics {
        val completedSets = completedRows.sumOf { it.exercise.sets.count { set -> !set.skipped } }
        val durationHours = logs.sumOf { it.durationMinutes }.coerceAtLeast(1) / 60.0
        val densityBySession = logs
            .map { log ->
                val sets = log.completedExercises.sumOf { ex -> ex.sets.count { !it.skipped } }
                val density = sets / (log.durationMinutes.coerceAtLeast(1) / 60.0)
                NamedMetric(log.id, log.sessionName, density.round1(), "series completadas por hora")
            }
            .sortedByDescending { it.value }
        val exerciseVolumes = completedRows
            .map { row -> row.exercise.exerciseName to row.exercise.sets.sumOf { it.weight * max(1, it.reps).toDouble() } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .toNamedMetrics("volumen kg x reps acumulado")
            .take(5)
        val totalStimulus = exerciseVolumes.sumOf { it.value }.takeIf { it > 0.0 } ?: 1.0
        val topStimulusShare = exerciseVolumes.take(max(1, (exerciseVolumes.size * 0.2).roundToInt())).sumOf { it.value } / totalStimulus
        val fatiguing = plannedRows
            .mapNotNull { row ->
                val info = row.info ?: return@mapNotNull null
                val stress = row.exercise.effectiveSetCount() * ((info.efc ?: 2.5) + (info.cnc ?: 2.5) + (info.ssc ?: 0.5))
                info.id to info.name to stress
            }
            .map { (pair, stress) -> NamedMetric(pair.first, pair.second, stress.round1(), "EFC + CNC + SSC por series planificadas.") }
            .sortedByDescending { it.value }
            .take(5)
        val actualRest = completedRows.flatMap { row -> row.exercise.sets.map { row.exercise.restTime.toDouble() } }.averageOrNull()
        val recommendedRest = completedRows.mapNotNull { it.info?.averageRestSeconds?.toDouble() }.averageOrNull()

        return EfficiencyAnalytics(
            densitySetsPerHour = (completedSets / durationHours).round1(),
            sessionDensity = densityBySession,
            paretoTopExercises = exerciseVolumes,
            topStimulusShare = topStimulusShare.round2(),
            topFatiguingExercises = fatiguing,
            restCompliance = ratio(
                "Real",
                actualRest ?: 0.0,
                "Recomendado",
                recommendedRest ?: 0.0,
                "Descanso real del log frente a averageRestSeconds del catálogo.",
            ),
            blockIdentity = inferBlockIdentity(plannedRows),
        )
    }

    private fun buildAdherence(rows: List<PlannedExerciseRow>, logs: List<WorkoutLog>): AdherenceAnalytics {
        val plannedSessions = rows.map { it.sessionId }.distinct().size.coerceAtLeast(1)
        val completedSessions = logs.map { it.sessionId }.distinct().size
        val plannedExercises = rows.size.coerceAtLeast(1)
        val completedExercises = logs.flatMap { it.completedExercises }.map { it.exerciseId }.distinct().size
        val omissions = logs.flatMap { it.omittedExercises }
            .groupingBy { it.exerciseName.ifBlank { it.exerciseId } }
            .eachCount()
            .map { (name, count) -> NamedMetric(name.normalizeId(), name, count.toDouble(), "Omitido en logs reales.") }
            .sortedByDescending { it.value }
        val ratioSessions = completedSessions.toDouble() / plannedSessions
        val ratioExercises = completedExercises.toDouble() / plannedExercises
        val diagnosis = when {
            ratioSessions < 0.6 && omissions.isEmpty() -> "Ejecución baja: faltan sesiones completas antes de culpar el programa."
            ratioExercises < 0.7 || omissions.isNotEmpty() -> "Ejecución irregular: hay ejercicios omitidos o baja cobertura real."
            else -> "Ejecución consistente; si hay estancamiento, revisar diseño del programa."
        }

        return AdherenceAnalytics(
            completedSessionRatio = ratioSessions.coerceIn(0.0, 1.0).round2(),
            completedExerciseRatio = ratioExercises.coerceIn(0.0, 1.0).round2(),
            omittedExercises = omissions,
            skippedExerciseCount = logs.flatMap { it.completedExercises }.sumOf { ex -> ex.sets.count { it.skipped } },
            diagnosis = diagnosis,
        )
    }

    private fun buildProgression(rows: List<CompletedExerciseRow>): List<ExerciseProgressionAnalytics> =
        rows.groupBy { it.exercise.canonicalExerciseId ?: it.exercise.exerciseDbId ?: it.exercise.exerciseId }
            .mapNotNull { (id, grouped) ->
                if (id.isBlank()) return@mapNotNull null
                val ordered = grouped.sortedBy { it.log.date }
                val values = ordered.mapNotNull { row -> row.exercise.sets.mapNotNull { it.estimatedE1rm() }.maxOrNull() }
                if (values.isEmpty()) return@mapNotNull null
                val first = values.first()
                val last = values.last()
                val recent = values.takeLast(4)
                val stagnation = if (recent.size >= 3 && recent.maxOrNull()!! - recent.minOrNull()!! < 1.0) 0.85 else 0.2
                ExerciseProgressionAnalytics(
                    exerciseId = id,
                    exerciseName = ordered.last().exercise.exerciseName,
                    firstE1rm = first.round1(),
                    lastE1rm = last.round1(),
                    deltaE1rm = (last - first).round1(),
                    sparkline = values.map { it.round1() },
                    stagnationRisk = stagnation,
                )
            }
            .sortedByDescending { abs(it.deltaE1rm ?: 0.0) }

    private fun buildDiagnostics(
        coverage: CoverageAnalytics,
        fatigue: FatigueAnalytics,
        adherence: AdherenceAnalytics,
        progression: List<ExerciseProgressionAnalytics>,
    ): List<ProgramDiagnostic> = buildList {
        if (coverage.forgottenMuscles.isNotEmpty()) {
            add(
                ProgramDiagnostic(
                    id = "forgotten-muscles",
                    title = "Músculos sin cobertura",
                    detail = coverage.forgottenMuscles.joinToString(", "),
                    severity = DiagnosticSeverity.WARNING,
                )
            )
        }
        if (fatigue.lumbarFatigue >= 18.0) {
            add(
                ProgramDiagnostic(
                    id = "high-lumbar-fatigue",
                    title = "Fatiga lumbar alta",
                    detail = "La suma de bisagras, remos pesados y carga axial concentra estrés lumbar.",
                    severity = DiagnosticSeverity.CRITICAL,
                )
            )
        }
        if (fatigue.anteriorShoulderStress >= 14.0) {
            add(
                ProgramDiagnostic(
                    id = "anterior-shoulder-stress",
                    title = "Estrés de hombro anterior",
                    detail = "El volumen de presses y pectoral eleva el estrés anterior.",
                    severity = DiagnosticSeverity.WARNING,
                )
            )
        }
        if (adherence.completedExerciseRatio < 0.7) {
            add(
                ProgramDiagnostic(
                    id = "execution-first",
                    title = "Primero adherencia",
                    detail = adherence.diagnosis,
                    severity = DiagnosticSeverity.WARNING,
                )
            )
        }
        progression.filter { it.stagnationRisk >= 0.8 }.take(3).forEach { item ->
            add(
                ProgramDiagnostic(
                    id = "stagnation-${item.exerciseId}",
                    title = "Riesgo de estancamiento",
                    detail = "${item.exerciseName}: e1RM reciente casi plano.",
                    severity = DiagnosticSeverity.WARNING,
                )
            )
        }
    }

    private data class PlannedExerciseRow(
        val sessionId: String,
        val sessionName: String,
        val dayOfWeek: Int?,
        val exercise: Exercise,
        val info: ExerciseMuscleInfo?,
    )

    private data class CompletedExerciseRow(
        val log: WorkoutLog,
        val exercise: CompletedExercise,
        val info: ExerciseMuscleInfo?,
    )

    private data class FatigueSourceRow(
        val sets: Double,
        val rpe: Double?,
        val dayLabel: String?,
        val info: ExerciseMuscleInfo?,
    )

    private fun PlannedExerciseRow.asFatigueSource(): FatigueSourceRow =
        FatigueSourceRow(
            sets = exercise.effectiveSetCount().toDouble(),
            rpe = exercise.sets.mapNotNull { it.targetRPE }.averageOrNull(),
            dayLabel = dayOfWeek?.let(::dayLabel) ?: sessionName,
            info = info,
        )

    private fun CompletedExerciseRow.asFatigueSource(): FatigueSourceRow =
        FatigueSourceRow(
            sets = exercise.sets.count { !it.skipped }.toDouble(),
            rpe = exercise.sets.mapNotNull { it.rpe }.averageOrNull(),
            dayLabel = log.sessionName.ifBlank { log.date.take(10) },
            info = info,
        )

    private fun Program.plannedExercises(): List<PlannedExerciseRow> =
        macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { week ->
                week.sessions.flatMap { session ->
                    session.allExercises().map { exercise ->
                        PlannedExerciseRow(session.id, session.name, session.dayOfWeek, exercise, null)
                    }
                }
            }

    private fun PlannedExerciseRow.infoFrom(catalog: Map<String, ExerciseMuscleInfo>): PlannedExerciseRow =
        copy(info = exercise.exerciseDbId?.lowercase()?.let { catalog[it] }
            ?: exercise.canonicalExerciseId?.lowercase()?.let { catalog[it] }
            ?: exercise.exerciseId?.lowercase()?.let { catalog[it] })

    private fun CompletedExercise.infoFrom(catalog: Map<String, ExerciseMuscleInfo>): ExerciseMuscleInfo? =
        exerciseDbId?.lowercase()?.let { catalog[it] }
            ?: canonicalExerciseId?.lowercase()?.let { catalog[it] }
            ?: exerciseId.lowercase().let { catalog[it] }

    private fun Exercise.effectiveSetCount(): Int {
        val counted = sets.count { !it.isIneffective && ((it.targetReps ?: 0) > 0 || (it.weight ?: 0.0) > 0.0) }
        return if (counted > 0) counted else sets.count { !it.isIneffective }
    }

    private fun CompletedSet.estimatedE1rm(): Double? {
        if (skipped || reps <= 0 || weight <= 0.0) return null
        val safeReps = reps.coerceAtMost(36)
        return weight * 36.0 / (37.0 - safeReps)
    }

    private fun Map<String, Double>.toMuscleMetrics(explanation: String): List<MuscleMetric> =
        entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .map { (name, value) -> MuscleMetric(name.normalizeId(), name, value.round1(), explanation) }

    private fun Map<String, Double>.toNamedMetrics(explanation: String): List<NamedMetric> =
        entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .map { (name, value) -> NamedMetric(name.normalizeId(), name, value.round1(), explanation) }

    private fun ratio(left: String, leftValue: Double, right: String, rightValue: Double, explanation: String): RatioMetric =
        RatioMetric(
            leftLabel = left,
            leftValue = leftValue.round1(),
            rightLabel = right,
            rightValue = rightValue.round1(),
            ratio = if (rightValue > 0.0) (leftValue / rightValue).round2() else if (leftValue > 0.0) Double.POSITIVE_INFINITY else 0.0,
            explanation = explanation,
        )

    private fun movementPatternFor(info: ExerciseMuscleInfo): String {
        val text = "${info.name} ${info.force} ${info.chain}".normalizeForAnalytics()
        return when {
            listOf("press banca", "press de banca", "fondos", "flexion", "apertura", "cruce", "crossover").any { text.contains(it) } -> "Empuje horizontal"
            listOf("press militar", "press hombro", "press de hombro", "overhead", "arnold").any { text.contains(it) } -> "Empuje vertical"
            listOf("remo", "row", "face pull").any { text.contains(it) } -> "Tirón horizontal"
            listOf("dominada", "jalon", "pull up", "chin up", "pullover").any { text.contains(it) } -> "Tirón vertical"
            listOf("sentadilla", "squat", "prensa", "hack", "sissy").any { text.contains(it) } -> "Squat pattern"
            listOf("peso muerto", "rdl", "rumano", "bisagra", "hip thrust", "buenos dias").any { text.contains(it) } -> "Hinge pattern"
            listOf("zancada", "lunge", "bulgara", "step up", "unilateral").any { text.contains(it) } -> "Unilateral/lunge"
            listOf("carry", "farmer", "suitcase", "yoke").any { text.contains(it) } -> "Carry"
            listOf("pallof", "anti rotacion").any { text.contains(it) } -> "Core anti-rotación"
            listOf("plancha", "ab wheel", "hollow", "anti extension").any { text.contains(it) } -> "Core anti-extensión"
            else -> info.force?.ifBlank { null } ?: "Sin patrón"
        }
    }

    private fun stabilityDemandFor(row: PlannedExerciseRow): Double {
        val info = row.info
        val equipment = info?.equipment.normalizeForAnalytics()
        val name = "${info?.name} ${row.exercise.name}".normalizeForAnalytics()
        val base = when {
            equipment.contains("maquina") || equipment.contains("smith") -> 1.0
            equipment.contains("polea") -> 2.0
            equipment.contains("barra") -> 3.0
            equipment.contains("mancuerna") || equipment.contains("kettlebell") -> 3.3
            equipment.contains("peso corporal") || equipment.contains("trx") || equipment.contains("banda") -> 3.5
            else -> 2.5
        }
        val unilateralBonus = if (row.exercise.isEffectivelyUnilateral() || name.contains("unilateral") || name.contains("bulgara") || name.contains("zancada")) 1.0 else 0.0
        val carryBonus = if (name.contains("carry") || name.contains("farmer") || name.contains("suitcase")) 0.8 else 0.0
        return (base + unilateralBonus + carryBonus).coerceIn(1.0, 5.0)
    }

    private fun stabilityBucketLabel(value: Double): String = when {
        value < 1.6 -> "Muy estable"
        value < 2.6 -> "Estable/moderada"
        value < 3.6 -> "Libre moderada"
        value < 4.4 -> "Inestable"
        else -> "Alta demanda"
    }

    private fun inferBlockIdentity(rows: List<PlannedExerciseRow>): String {
        if (rows.isEmpty()) return "Sin datos"
        val patterns = rows.groupingBy { it.info?.let(::movementPatternFor) ?: "Sin patrón" }.eachCount()
        val upper = rows.count { it.info?.bodyPart.equals("upper", true) }
        val lower = rows.count { it.info?.bodyPart.equals("lower", true) }
        val t1 = rows.count { it.exercise.isCompetitionLift || it.info?.tier.equals("T1", true) }
        val machine = rows.count { it.info?.equipment.normalizeForAnalytics().contains("maquina") }
        val isolation = rows.count { it.info?.type.normalizeForAnalytics().contains("aislamiento") }
        return when {
            t1.toDouble() / rows.size >= 0.35 && isolation.toDouble() / rows.size >= 0.25 -> "Powerbuilder"
            t1.toDouble() / rows.size >= 0.45 -> "Fuerza base"
            isolation.toDouble() / rows.size >= 0.45 || machine.toDouble() / rows.size >= 0.45 -> "Bodybuilding puro"
            upper > lower * 1.5 -> "Torso dominante"
            lower > upper * 1.5 -> "Pierna dominante"
            patterns.size >= 7 -> "Full-body inteligente"
            rows.size <= 5 -> "Minimalista"
            else -> "Programa Frankenstein"
        }
    }

    private fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Lunes"
        2 -> "Martes"
        3 -> "Miércoles"
        4 -> "Jueves"
        5 -> "Viernes"
        6 -> "Sábado"
        7 -> "Domingo"
        else -> "Día"
    }

    private fun Map<String, Double>.orZero(key: String): Double = this[key] ?: 0.0

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun Iterable<Int>.averageOrNull(): Double? {
        val values = toList()
        return if (values.isEmpty()) null else values.average()
    }

    private fun String?.normalizeForAnalytics(): String =
        orEmpty()
            .lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("ñ", "n")

    private fun String.normalizeId(): String = normalizeForAnalytics()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun Double.round1(): Double = (this * 10.0).roundToInt() / 10.0

    private fun Double.round2(): Double = (this * 100.0).roundToInt() / 100.0
}
