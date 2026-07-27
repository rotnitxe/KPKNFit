package com.example.kpkn.domain.auge

import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.*
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor de cálculo de readiness por patrón de movimiento y ejercicio.
 *
 * TODA la lógica usa exclusivamente datos AUGE reales (batteries, perMuscle)
 * y propiedades reales del ejercicio (cnc, axialLoadFactor, involvedMuscles, roles),
 * sin pesos fijos arbitrarios. Los pesos son DINÁMICOS según el perfil del ejercicio.
 */
object ExerciseReadinessEngine {

    const val ADJUSTMENT_THRESHOLD = 75

    private const val MIN_REDUCTION_MULTIPLIER = 0.20
    private const val MAX_REDUCTION_MULTIPLIER = 0.80
    private const val MAX_REDUCTION_CAP = 0.30
    private const val BASE_SETS = 3

    // ─── Readiness por Ejercicio ──────────────────────────────────────────────

    /**
     * Calcula el readiness para un ejercicio.
     *
     * @param exercise        Ejercicio del programa (con sets, peso, RPE planificados)
     * @param augeBatteries   Baterías AUGE reales del snapshot
     * @param perMuscle       Recuperación por músculo del snapshot
     * @param averageErm      eRM promedio del historial (si existe)
     * @param unresolvedDiscomfortIds  IDs de molestias persistentes de feedbacks previos (Fase 6)
     * @return                ExerciseReadiness o null si no hay datos suficientes
     */
    fun calculatePerExerciseReadiness(
        exercise: com.example.kpkn.data.models.Exercise,
        augeBatteries: GlobalBatteries,
        perMuscle: Map<String, MuscleRecoveryStatus>,
        averageErm: Double? = null,
        unresolvedDiscomfortIds: List<String> = emptyList(),
        articularBatteries: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
    ): ExerciseReadiness? {
        val dbInfo = EXERCISE_DATABASE_BY_ID[exercise.exerciseDbId?.lowercase()]
            ?: EXERCISE_DATABASE_BY_ID[exercise.exerciseId?.lowercase()]
            ?: return null

        val involvedMuscles = if (!exercise.effectiveMuscles.isNullOrEmpty()) {
            exercise.effectiveMuscles!!
                .filter { resolveMuscleVolumeContribution(it) > 0.0 }
        } else {
            dbInfo.involvedMuscles
                .filter { resolveMuscleVolumeContribution(it) > 0.0 }
                .filter { it.role != MuscleRole.NEUTRALIZER }
        }

        val muscleIds = involvedMuscles
            .mapNotNull { getAugeMuscleDisplayId(it.muscle, it.emphasis) }

        if (muscleIds.isEmpty()) return null

        // Componente muscular: promedio ponderado por rol; lookup against pillar keys in perMuscle
        val (scoreSum, weightSum) = involvedMuscles.fold(0.0 to 0.0) { (s, w), involved ->
            val pillarId = getAugeMusclePillarId(involved.muscle, involved.emphasis)
            val displayId = getAugeMuscleDisplayId(involved.muscle, involved.emphasis)
            val recovery = (perMuscle[displayId] ?: perMuscle[pillarId])?.recoveryScore?.toDouble()
                ?: return@fold s to w
            val roleWeight = FATIGUE_ROLE_MULTIPLIERS[involved.role] ?: return@fold s to w
            (s + recovery * roleWeight) to (w + roleWeight)
        }

        val muscularComponent = if (weightSum > 0.0) {
            (scoreSum / weightSum).roundToInt().coerceIn(0, 100)
        } else {
            100
        }

        // NUEVO Step C' — Articular component (limiting por ejercicio)
        val (artScoreSum, artWeightSum) = involvedMuscles.fold(0.0 to 0.0) { (s, w), involved ->
            val relatedArtic = AugeTtcEngine.articularBatteriesFor(involved.muscle, involved.emphasis)
            if (relatedArtic.isEmpty()) return@fold s to w
            val roleWeight = FATIGUE_ROLE_MULTIPLIERS[involved.role] ?: return@fold s to w
            val avgScore = relatedArtic
                .mapNotNull { articularBatteries[it]?.recoveryScore }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?: return@fold s to w
            (s + avgScore * roleWeight) to (w + roleWeight)
        }
        val articularComponent = if (artWeightSum > 0.0) {
            (artScoreSum / artWeightSum).roundToInt().coerceIn(0, 100)
        } else 100

        // LIMITING principle: structural = min(muscular, articular)
        val structuralComponent = minOf(muscularComponent, articularComponent)
        val relatedArticular = involvedMuscles
            .flatMap { AugeTtcEngine.articularBatteriesFor(it.muscle, it.emphasis) }
            .distinct()

        val cnsComponent = augeBatteries.cnc.coerceIn(0, 100)
        val spinalComponent = augeBatteries.spinal.coerceIn(0, 100)

        // Step E — Dynamic weights: ahora incluyen articularDemand
        val exerciseTtc = AugeTtcEngine.calculateTTC(dbInfo.name, dbInfo.equipment)
        val articularDemand = (exerciseTtc / 5.0).coerceIn(0.0, 1.0)

        val cnc = dbInfo.cnc ?: 2.5
        val axialLoad = dbInfo.axialLoadFactor ?: 0.0

        val neuralDemand = cnc / 5.0
        val spinalDemand = axialLoad.coerceIn(0.0, 1.0)
        val muscularDemand = 1.0

        val totalDemand = muscularDemand + neuralDemand + spinalDemand + articularDemand

        val wMusc = muscularDemand / totalDemand
        val wCns = neuralDemand / totalDemand
        val wSpine = spinalDemand / totalDemand
        val wArtic = articularDemand / totalDemand

        val baseReadiness = (
            structuralComponent.toDouble() * wMusc +
            cnsComponent.toDouble() * wCns +
            spinalComponent.toDouble() * wSpine +
            articularComponent.toDouble() * wArtic
        ).coerceIn(0.0, 100.0)

        val setsCount = exercise.sets.size
        val setsExtra = max(0, setsCount - BASE_SETS)
        val setsPenaltyFactor = max(0.85, 1.0 - setsExtra * 0.01)

        val maxRpe = exercise.sets.mapNotNull { it.targetRPE }.maxOrNull() ?: 7.0
        val intensityPenaltyFactor = when {
            maxRpe >= 10.0 -> 0.97
            maxRpe >= 9.5 -> 0.98
            else -> 1.0
        }

        val ermPenaltyFactor = if (averageErm != null && averageErm > 0.0) {
            val maxPlannedWeight = exercise.sets
                .mapNotNull {
                    it.weight
                        ?: if (it.targetPercentageRM != null && averageErm > 0.0)
                            averageErm * it.targetPercentageRM
                        else null
                }
                .maxOrNull() ?: 0.0
            if (maxPlannedWeight > 0.0 && maxPlannedWeight / averageErm > 0.85) 0.95
            else 1.0
        } else {
            1.0
        }

        val discomfortPenaltyFactor = computeDiscomfortPenaltyFactor(involvedMuscles, unresolvedDiscomfortIds)

        // Hardcap progresivo continuo por TTC + articular baja (protección balística para mejor UX)
        val ttcFactor = (exerciseTtc / 5.0).coerceIn(0.4, 1.0)
        val ttcHardCap = if (articularComponent < 60) {
            val scale = (articularComponent / 60.0).coerceIn(0.0, 1.0)
            val capMin = 25.0 + (1.0 - ttcFactor) * 20.0
            (capMin + scale * (100.0 - capMin)).toInt()
        } else {
            100
        }

        val finalScore = (baseReadiness * setsPenaltyFactor * intensityPenaltyFactor * ermPenaltyFactor * discomfortPenaltyFactor)
            .roundToInt()
            .coerceIn(0, ttcHardCap)
            .coerceIn(0, 100)

        val componentScores = mapOf(
            "MUSCULAR" to muscularComponent,
            "CNS" to cnsComponent,
            "SPINAL" to spinalComponent,
            "ARTICULAR" to articularComponent
        )
        val minEntry = componentScores.minByOrNull { it.value }
        val limitingFactor = minEntry?.key
        val limitingDetail = when (limitingFactor) {
            "MUSCULAR" -> {
                involvedMuscles.mapNotNull { involved ->
                    val pillarId = getAugeMusclePillarId(involved.muscle, involved.emphasis)
                    val displayId = getAugeMuscleDisplayId(involved.muscle, involved.emphasis)
                    val recovery = (perMuscle[displayId] ?: perMuscle[pillarId])?.recoveryScore ?: 100
                    displayId to recovery
                }.minByOrNull { it.second }?.first
            }
            "ARTICULAR" -> {
                relatedArticular.mapNotNull { ab ->
                    val score = articularBatteries[ab]?.recoveryScore ?: 100
                    AugeTtcEngine.articularLabel(ab) to score
                }.minByOrNull { it.second }?.first
            }
            "SPINAL" -> "Columna (Espinal)"
            "CNS" -> "Sistema Nervioso Central (Energía)"
            else -> null
        }

        return ExerciseReadiness(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            overallScore = finalScore,
            muscularComponent = muscularComponent,
            cnsComponent = cnsComponent,
            spinalComponent = spinalComponent,
            articularComponent = articularComponent,
            structuralComponent = structuralComponent,
            relatedArticular = relatedArticular,
            muscularWeight = wMusc,
            cnsWeight = wCns,
            spinalWeight = wSpine,
            articularWeight = wArtic,
            setsPenaltyFactor = setsPenaltyFactor,
            intensityPenaltyFactor = intensityPenaltyFactor,
            ermProximityFactor = ermPenaltyFactor,
            patternId = dbInfo.force,
            involvedMuscleIds = muscleIds,
            limitingFactor = limitingFactor,
            limitingDetail = limitingDetail,
        )
    }

    // ─── Readiness por Patrón ─────────────────────────────────────────────────

    fun calculatePerMovementPatternReadiness(
        exercises: List<com.example.kpkn.data.models.Exercise>,
        exerciseReadinessMap: Map<String, ExerciseReadiness>,
        perMuscle: Map<String, MuscleRecoveryStatus>,
    ): List<MovementPatternReadiness> {
        val byPattern = exercises
            .mapNotNull { exercise ->
                val readiness = exerciseReadinessMap[exercise.id] ?: return@mapNotNull null
                val patternId = readiness.patternId ?: "Otro"
                patternId to (readiness to exercise)
            }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second } }

        return byPattern.map { (patternId, pairs) ->
            val exercisesInPattern = pairs.map { it.second }
            val readinesses = pairs.map { it.first }
            val totalSets = exercisesInPattern.sumOf { ex -> ex.sets.size }

            val weightedScore = if (totalSets > 0) {
                readinesses.zip(exercisesInPattern).sumOf { (rd, ex) ->
                    (rd.overallScore * ex.sets.size).toLong()
                }.toDouble() / totalSets
            } else {
                readinesses.map { it.overallScore.toDouble() }.average()
            }

            val uniqueMuscles = readinesses
                .flatMap { it.involvedMuscleIds }
                .distinct()

            val avgMuscleRecov = uniqueMuscles
                .mapNotNull { perMuscle[it]?.recoveryScore }
                .average()
                .roundToInt()

            MovementPatternReadiness(
                patternId = patternId,
                patternLabel = patternLabelFor(patternId),
                overallScore = weightedScore.roundToInt().coerceIn(0, 100),
                exerciseCount = exercisesInPattern.size,
                totalSets = totalSets,
                contributingMuscles = uniqueMuscles,
                averageMuscleRecovery = avgMuscleRecov,
            )
        }.sortedByDescending { it.overallScore }
    }

    // ─── Ajuste de Carga ─────────────────────────────────────────────────────

    /**
     * Calcula la carga sugerida para una serie según readiness, slider de severidad y tipo de carga.
     * Soporta progresión inteligente: Asistencia -> Peso Corporal -> Lastre.
     */
    fun calculateSetAdjustment(
        plannedWeight: Double,
        exerciseReadiness: ExerciseReadiness,
        severitySlider: Double,
        averageErm: Double? = null,
        loadMode: LoadModeV2 = LoadModeV2.LOAD,
        bodyWeight: Double? = null,
    ): SetAdjustmentSuggestion {
        val score = exerciseReadiness.overallScore

        if (score >= ADJUSTMENT_THRESHOLD) {
            return SetAdjustmentSuggestion(
                exerciseId = exerciseReadiness.exerciseId,
                setIndex = 0,
                currentPlannedWeight = plannedWeight,
                readinessScore = score,
                severityFactor = severitySlider,
                reductionPercent = 0.0,
                suggestedWeight = plannedWeight,
                averageErm = averageErm,
                reason = "No requiere ajuste",
                suggestedLoadMode = loadMode,
            )
        }

        val gap = (ADJUSTMENT_THRESHOLD - score) / 100.0
        val reductionFactor = MIN_REDUCTION_MULTIPLIER +
            severitySlider * (MAX_REDUCTION_MULTIPLIER - MIN_REDUCTION_MULTIPLIER)

        val reductionPercent = (gap * reductionFactor).coerceIn(0.0, MAX_REDUCTION_CAP)

        val (suggestedWeight, suggestedMode, reason) = when (loadMode) {
            LoadModeV2.LASTRE -> {
                val rawWeight = plannedWeight * (1.0 - reductionPercent)
                if (rawWeight < 2.5) {
                    Triple(
                        0.0,
                        LoadModeV2.BODYWEIGHT,
                        "Readiness $score% · Pasar a Peso Corporal (quitar lastre)"
                    )
                } else {
                    val finalWeight = ((rawWeight / 2.5).roundToInt() * 2.5).toDouble()
                    Triple(
                        finalWeight,
                        LoadModeV2.LASTRE,
                        "Readiness $score% · Reducir lastre a ${finalWeight.toInt()}kg (−${(reductionPercent * 100).roundToInt()}%)"
                    )
                }
            }
            LoadModeV2.BODYWEIGHT -> {
                // Assistance suggestion from readiness severity — not (PC − net).
                val baseAssistance = (2.5 + reductionPercent * 20.0).coerceAtLeast(2.5)
                val finalWeight = ((baseAssistance / 2.5).roundToInt() * 2.5).coerceAtLeast(2.5).toDouble()
                Triple(
                    finalWeight,
                    LoadModeV2.ASSISTED,
                    "Readiness $score% · Añadir ${finalWeight.toInt()}kg de asistencia por fatiga"
                )
            }
            LoadModeV2.ASSISTED -> {
                // Stay in ASSISTED: increase assistance kg directly (no PC−net translation).
                val bump = ((plannedWeight * reductionPercent).coerceAtLeast(2.5) / 2.5).roundToInt() * 2.5
                val finalWeight = (plannedWeight + bump).coerceAtLeast(plannedWeight + 2.5)
                Triple(
                    finalWeight,
                    LoadModeV2.ASSISTED,
                    "Readiness $score% · Aumentar asistencia a ${finalWeight.toInt()}kg (+${(finalWeight - plannedWeight).toInt()}kg)"
                )
            }
            LoadModeV2.LOAD -> {
                val rawWeight = plannedWeight * (1.0 - reductionPercent)
                val finalWeight = ((rawWeight / 2.5).roundToInt() * 2.5).coerceAtLeast(0.0).toDouble()
                val weightStr = if (finalWeight % 1.0 == 0.0) finalWeight.toInt().toString() else finalWeight.toString()
                Triple(
                    finalWeight,
                    LoadModeV2.LOAD,
                    "Readiness $score% · Reducir peso a ${weightStr}kg (−${(reductionPercent * 100).roundToInt()}%)"
                )
            }
        }

        return SetAdjustmentSuggestion(
            exerciseId = exerciseReadiness.exerciseId,
            setIndex = 0,
            currentPlannedWeight = plannedWeight,
            readinessScore = score,
            severityFactor = severitySlider,
            reductionPercent = reductionPercent,
            suggestedWeight = suggestedWeight,
            averageErm = averageErm,
            reason = reason,
            suggestedLoadMode = suggestedMode,
        )
    }


    // ─── Discomfort Penalty ───────────────────────────────────────────────────

    /**
     * Calcula un factor de penalización [0.90, 1.0] según si las molestias no
     * resueltas afectan articulaciones que este ejercicio trabaja.
     */
    // exposed for package-level testing
    fun computeDiscomfortPenaltyFactor(
        involvedMuscles: List<InvolvedMuscle>,
        unresolvedDiscomfortIds: List<String>,
    ): Double {
        if (unresolvedDiscomfortIds.isEmpty()) return 1.0

        val exerciseArticulars = involvedMuscles
            .flatMap { muscle -> AugeTtcEngine.articularBatteriesFor(muscle.muscle, muscle.emphasis) }
            .toSet()
        if (exerciseArticulars.isEmpty()) return 1.0

        val overlappingCount = unresolvedDiscomfortIds.count { id ->
            val entry = DISCOMFORT_CATALOG_BY_ID[id]
            entry?.relatedArticular?.any { it in exerciseArticulars } == true
        }

        return when {
            overlappingCount == 0 -> 1.0
            overlappingCount == 1 -> 0.95
            else -> 0.90
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    fun readinessLabel(score: Int): String = when {
        score >= 85 -> "Óptimo"
        score >= 75 -> "Bueno"
        score >= 50 -> "Moderado"
        score >= 35 -> "Bajo"
        else -> "Crítico"
    }

    private fun patternLabelFor(force: String): String = when (force.lowercase()) {
        "empuje" -> "Empuje"
        "tirón", "tiron" -> "Tirón"
        "sentadilla" -> "Sentadilla"
        "bisagra" -> "Bisagra"
        "anti-extensión", "antiextension" -> "Anti-Extensión"
        "flexión", "flexion" -> "Flexión"
        "extensión", "extension" -> "Extensión"
        else -> force.replaceFirstChar { it.uppercase() }
    }
}
