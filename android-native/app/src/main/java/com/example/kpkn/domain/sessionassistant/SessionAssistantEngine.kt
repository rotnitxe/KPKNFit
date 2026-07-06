package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.CalorieGoalObjective
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.models.AugeMetrics
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.calculations.estimateSessionDurationMinutes
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.ceil
import kotlin.math.roundToInt

object SessionAssistantEngine {

    fun evaluate(
        input: SessionAssistantInput,
        allTemplates: List<SessionTemplate> = emptyList(),
    ): SessionAssistantReport {
        val volumeResult = calcularVolumenPorMusculo(input)
        val drain = calcularDrenajeEstimado(input)
        val thresholds = buildVolumeThresholds(input, volumeResult.volumeMap)
        val ajustes = generarAjustesPorRings(input, volumeResult, drain)
                // 1. Total de descansos sumados (incluyendo transiciones de 60s entre ejercicios)
        var totalRestSeconds = 0
        for (ex in input.allExercisesInSession) {
            val exRest = ex.restTime ?: 90
            val setsCount = ex.sets.size
            if (setsCount > 0) {
                totalRestSeconds += exRest * (setsCount - 1)
            }
        }
        val exerciseCount = input.allExercisesInSession.size
        if (exerciseCount > 1) {
            totalRestSeconds += (exerciseCount - 1) * 60
        }

        // 2. Set-up de cada ejercicio (tiempo dinámico desde la base de datos o fallback de 120s) + Ejecución de series (45s por serie)
        val defaultSetupTime = 120
        val workSecondsPerSet = 45
        val totalWorkSeconds = volumeResult.totalSets * workSecondsPerSet
        
        var totalSetupSeconds = 0
        for (ex in input.allExercisesInSession) {
            val info = resolveExerciseInfo(ex, input.exerciseIndex)
            val setupTime = info?.setupTime ?: defaultSetupTime
            totalSetupSeconds += setupTime
        }
        val estimatedWorkSeconds = totalWorkSeconds + totalSetupSeconds

        val duracion = ((totalRestSeconds + estimatedWorkSeconds) + 59) / 60

        val timeAjustes = if (input.targetDurationMinutes != null && duracion > input.targetDurationMinutes) {
            val overage = duracion - input.targetDurationMinutes
            buildTimeSuggestions(input, overage)
        } else {
            emptyList()
        }

        return SessionAssistantReport(
            veredicto = Verdict.OPTIMAL,
            scoreEstimado = 0,
            riesgos = emptyList(),
            ajustes = ajustes + timeAjustes,
            oportunidades = emptyList(),
            tarjetasFantasma = emptyList(),
            plantillasCompatibles = emptyList(),
            volumenPorMusculo = volumeResult.volumeMap.mapValues { it.value.flat },
            umbralesPorMusculo = thresholds,
            drenajeEstimado = drain,
            duracionEstimada = duracion,
            totalRestSeconds = totalRestSeconds,
            estimatedWorkSeconds = estimatedWorkSeconds,
            resumenTexto = "",
        )
    }

    // ─── Time Overage Suggestions ─────────────────────────────────────────────

    private const val MAX_REDUCE_REST_SUGGESTIONS = 2
    private const val MAX_SUPERSET_SUGGESTIONS = 2
    private const val MAX_DROPSET_SUGGESTIONS = 2

    private fun buildTimeSuggestions(
        input: SessionAssistantInput,
        overageMinutes: Int
    ): List<AssistantSuggestion> {
        val suggestions = mutableListOf<AssistantSuggestion>()
        val exercises = input.allExercisesInSession

        // Only suggest reduce-rest if average rest > 45s
        val avgRest = exercises.map { it.restTime ?: 90 }.average()
        if (avgRest > 45 && suggestions.size < MAX_REDUCE_REST_SUGGESTIONS) {
            suggestions.add(
                AssistantSuggestion(
                    id = "time_reduce_rest",
                    type = com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_REST_TIME,
                    title = "Reducir descansos en 15s",
                    message = "Ahorro estimado de ~${(exercises.size * 15) / 60} min. Sesión excede por $overageMinutes min el límite.",
                    priority = 30,
                )
            )
        }

        // Supersets: pair consecutive exercises
        if (exercises.size >= 2 && suggestions.size < MAX_SUPERSET_SUGGESTIONS + 1) {
            for (i in 0 until exercises.size - 1) {
                val ex = exercises[i]
                val next = exercises[i + 1]
                if (ex.sets.any { it.isDropSet || it.isRestPause }) continue
                if (next.sets.any { it.isDropSet || it.isRestPause }) continue
                suggestions.add(
                    AssistantSuggestion(
                        id = "time_superset_${ex.id}",
                        type = com.example.kpkn.domain.sessionassistant.AssistantActionType.CONVERT_TO_SUPERSET,
                        title = "Superserie con siguiente ejercicio",
                        message = "Ahorra ~90s por ronda de descanso eliminada. Sesión excede por $overageMinutes min.",
                        exerciseId = ex.id,
                        priority = 40,
                    )
                )
                if (suggestions.size >= MAX_SUPERSET_SUGGESTIONS + 1) break
            }
        }

        // Drop sets: convert suitable exercises
        if (suggestions.size < MAX_DROPSET_SUGGESTIONS + MAX_SUPERSET_SUGGESTIONS + 1) {
            for (ex in exercises) {
                val multiSet = ex.sets.size > 1
                val notAlreadyDrop = ex.sets.none { it.isDropSet }
                val notRestPause = ex.sets.none { it.isRestPause }
                if (!multiSet || !notAlreadyDrop || !notRestPause) continue
                suggestions.add(
                    AssistantSuggestion(
                        id = "time_dropset_${ex.id}",
                        type = com.example.kpkn.domain.sessionassistant.AssistantActionType.CONVERT_TO_DROPSET,
                        title = "Convertir a dropset",
                        message = "Reduce descansos intra-ejercicio a ~10s. Sesión excede por $overageMinutes min.",
                        exerciseId = ex.id,
                        priority = 25,
                    )
                )
                if (suggestions.size >= MAX_DROPSET_SUGGESTIONS + MAX_SUPERSET_SUGGESTIONS + 1) break
            }
        }

        // Reduce sets as final option
        if (suggestions.size >= 2) {
            val targetMuscles = calcularVolumenPorMusculo(input).volumeMap.entries
                .filter { (_, acc) -> acc.flat > 0 }
                .map { it.key }
            val muscle = targetMuscles.firstOrNull()
            if (muscle != null) {
                suggestions.add(
                    AssistantSuggestion(
                        id = "time_reduce_sets",
                        type = com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_SET,
                        title = "Reducir una serie en $muscle",
                        message = "Impacto directo en duración. Sesión excede por $overageMinutes min.",
                        muscle = muscle,
                        priority = 20,
                    )
                )
            }
        }

        return suggestions
    }

    // ─── Volume Calculation ───────────────────────────────────────────────────

    internal data class VolumeCalculationResult(
        val volumeMap: Map<String, MuscularVolumeAccumulator>,
        val roleMap: Map<String, MuscleRoleBreakdown>,
        val recommendationContext: Map<String, MuscleRecommendationContext>,
        val totalSets: Int,
        val totalSpinalLoad: Double,
        val elbowStress: Int,
        val kneeStress: Int,
        val averageRpe: Double,
        val exerciseInsights: List<ExerciseInsightData>,
    )

    internal data class ExerciseInsightData(
        val exerciseId: String,
        val name: String,
        val muscular: Double,
        val cns: Double,
        val spinal: Double,
    )

    internal fun calcularVolumenPorMusculo(input: SessionAssistantInput): VolumeCalculationResult {
        val volumeMap = mutableMapOf<String, MuscularVolumeAccumulator>()
        val muscleSetCounters = mutableMapOf<String, Int>()
        val roleMap = mutableMapOf<String, MuscleRoleBreakdown>()
        val recommendationContext = mutableMapOf<String, MuscleRecommendationContext>()
        var totalSets = 0
        var totalSpinalLoad = 0.0
        var elbowStress = 0
        var kneeStress = 0
        var rpeSum = 0.0
        var rpeCount = 0

        val exerciseInsights = input.allExercisesInSession.mapNotNull { exercise ->
            val info = resolveExerciseInfo(exercise, input.exerciseIndex) ?: return@mapNotNull null
            val validSets = exercise.validAugeSets()
            if (validSets.isEmpty()) return@mapNotNull null

            val metrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.name, info.equipment, info)
            if (metrics == null) {
                // Exercise without AUGE metrics: count volume but skip drain calculation
                val perExerciseContrib = VolumeCalculator.buildPerExerciseMuscleContributions(
                    SessionMuscleFilter.relevantMusclesFor(info),
                )
                validSets.forEach { set ->
                    val effectiveRpe = set.effectiveTargetRpe()
                    val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(effectiveRpe)
                    perExerciseContrib.forEach { (normalized, hyperFactor) ->
                        val bucket = volumeMap.getOrPut(normalized) { MuscularVolumeAccumulator() }
                        bucket.flat += hyperFactor
                        bucket.effective += hyperFactor * volumeMultiplier
                        if (effectiveRpe >= 9.5) bucket.fail += hyperFactor
                    }
                    totalSets++
                }
                return@mapNotNull null
            }
            var muscular = 0.0
            var cns = 0.0
            var spinal = 0.0
            totalSets += validSets.size

            val perExerciseContrib = VolumeCalculator.buildPerExerciseMuscleContributions(
                SessionMuscleFilter.relevantMusclesFor(info),
            )
            val primaryMuscle = info.involvedMuscles
                .find { it.role == MuscleRole.PRIMARY }
                ?.let { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
                ?: "Core"
            var accumulated = muscleSetCounters[primaryMuscle] ?: 0

            validSets.forEach { set ->
                val effectiveRpe = set.effectiveTargetRpe()
                rpeSum += effectiveRpe
                rpeCount++

                val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(effectiveRpe)
                perExerciseContrib.forEach { (normalized, hyperFactor) ->
                    val bucket = volumeMap.getOrPut(normalized) { MuscularVolumeAccumulator() }
                    bucket.flat += hyperFactor
                    bucket.effective += hyperFactor * volumeMultiplier
                    if (effectiveRpe >= 9.5) bucket.fail += hyperFactor
                }
                SessionMuscleFilter.relevantMusclesFor(info).forEach { muscle ->
                    val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                    val roleBucket = roleMap.getOrPut(normalized) { MuscleRoleBreakdown() }
                    when (muscle.role) {
                        MuscleRole.PRIMARY -> roleBucket.primary += 1.0
                        MuscleRole.SECONDARY -> roleBucket.secondary += 1.0
                        MuscleRole.STABILIZER -> roleBucket.stabilizer += 1.0
                        MuscleRole.NEUTRALIZER -> roleBucket.neutralizer += 1.0
                    }
                }

                totalSpinalLoad += info.axialLoadFactor ?: 0.0
                accumulated++

                val calculatedWeight = if (exercise.trainingMode == TrainingMode.RM && set.targetPercentageRM != null && exercise.reference1RM != null && exercise.reference1RM!! > 0.0) {
                    (set.targetPercentageRM / 100.0) * exercise.reference1RM!!
                } else {
                    set.weight ?: 60.0
                }
                val completedSet = CompletedSet(
                    id = set.id,
                    weight = calculatedWeight,
                    reps = set.targetReps ?: 8,
                    rpe = set.targetRPE,
                    rir = set.targetRIR,
                    actualIntensityMode = set.intensityMode,
                    actualIntensityValue = when (set.intensityMode) {
                        IntensityMode.RPE -> set.targetRPE
                        IntensityMode.RIR -> set.targetRIR?.toDouble()
                        else -> null
                    },
                    isFailure = set.isFailure || set.intensityMode == IntensityMode.FAILURE,
                )
                val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(input.settings)
                val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                    set = completedSet,
                    metrics = metrics,
                    tanks = tanks,
                    accumulatedSets = accumulated,
                    restTime = exercise.restTime ?: 90,
                    densityMultiplier = AugeFatigueEngine.getDensityMultiplierForExercise(
                        supersetId = exercise.supersetGroupRefOrLegacyId(),
                        restTime = exercise.restTime ?: 90,
                    ),
                )
                muscular += drain.muscularDrainPct
                cns += drain.cnsDrainPct
                spinal += drain.spinalDrainPct
            }
            muscleSetCounters[primaryMuscle] = accumulated

            SessionMuscleFilter.relevantMusclesFor(info).forEach { muscle ->
                val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val ctx = recommendationContext.getOrPut(normalized) { MuscleRecommendationContext() }
                if (exercise.trainingMode == TrainingMode.RM) ctx.usesPercent = true
                if (validSets.any { it.targetRIR != null }) ctx.usesRir = true
                if (validSets.any { it.isFailure || it.intensityMode == IntensityMode.FAILURE }) ctx.usesFailure = true
            }

            val name = info.name.lowercase()
            if (name.contains("press franc") || name.contains("rompecr") ||
                name.contains("extensi") && name.contains("polea") && name.contains("tr")
            ) {
                elbowStress += validSets.size
            }
            if (name.contains("extensi") && (name.contains("cuadr") || name.contains("sissy"))) {
                kneeStress += validSets.size
            }

            ExerciseInsightData(
                exerciseId = exercise.id,
                name = exercise.name,
                muscular = muscular,
                cns = cns,
                spinal = spinal,
            )
        }

        return VolumeCalculationResult(
            volumeMap = volumeMap,
            roleMap = roleMap,
            recommendationContext = recommendationContext,
            totalSets = totalSets,
            totalSpinalLoad = totalSpinalLoad,
            elbowStress = elbowStress,
            kneeStress = kneeStress,
            averageRpe = if (rpeCount > 0) rpeSum / rpeCount else 0.0,
            exerciseInsights = exerciseInsights,
        )
    }

    // ─── Drain Calculation ────────────────────────────────────────────────────

    internal fun calcularDrenajeEstimado(input: SessionAssistantInput): PredictedDrain {
        input.customDrain?.let { return it }
        return try {
            val session = Session(
                id = input.currentSessionId,
                name = "temp",
                exercises = input.allExercisesInSession,
            )
            val base = AugeFatigueEngine.calculateAdjustedPredictedDrain(
                session, input.exerciseIndex, input.settings,
            )
            val ema = AugeFatigueEngine.calculateMesocycleStressEMA(
                logs = input.workoutLogs,
                programId = input.programId,
                mesoIndex = input.mesoIndex,
            )
            AugeFatigueEngine.adjustPredictedDrainWithEMA(base, ema)
        } catch (_: Throwable) {
            PredictedDrain(cns = 15, muscular = 20, spinal = 10)
        }
    }

    // ─── Risk Detection ───────────────────────────────────────────────────────

    internal fun detectarRiesgos(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        thresholds: Map<String, VolumeThreshold>,
        weeklyVolume: Map<String, Double> = emptyMap(),
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()

        // 1. Rigid manual limits (absolute winner)
        if (input.ruleLimits.rigidLimits) {
            riesgos += checkRigidLimits(input, volume)
        }

        // 2. MRV/MAV/MEV personalized from program (session)
        riesgos += checkVolumeThresholds(volume, thresholds)

        // 3. Weekly volume limits
        riesgos += checkWeeklyVolume(input, weeklyVolume, thresholds)

        // 4. Generic AUGE limits
        riesgos += checkGenericVolumeLimits(volume, input.settings)

        // 5. Spinal load
        riesgos += checkSpinalLoad(volume, drain)

        // 6. CNS fatigue
        riesgos += checkCnsFatigue(drain, volume)

        // 7. Excess failure
        riesgos += checkExcessFailure(input, volume)

        // 8. Joint stress
        riesgos += checkJointStress(volume)

        return riesgos.sortedByDescending { it.severity.ordinal }
    }

    private fun checkRigidLimits(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        val limits = input.ruleLimits

        if (limits.maxRPE < 10.0) {
            input.allExercisesInSession.forEach { exercise ->
                exercise.validAugeSets().forEach { set ->
                    val effectiveRpe = set.effectiveTargetRpe()
                    if (effectiveRpe > limits.maxRPE) {
                        riesgos += SessionRisk(
                            id = "rigid-rpe-${exercise.id}",
                            type = RiskType.VOLUME,
                            severity = RiskSeverity.BLOCKING,
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            title = "RPE excesivo (límite rígido)",
                            message = "${exercise.name} tiene sets a RPE ${"%.1f".format(effectiveRpe)}, por encima del límite de ${"%.1f".format(limits.maxRPE)}.",
                            action = "Reducir RPE o eliminar serie.",
                        )
                    }
                }
            }
        }

        volume.volumeMap.forEach { (muscle, data) ->
            if (data.effective >= limits.maxVolumePerMuscleSession) {
                riesgos += SessionRisk(
                    id = "rigid-volume-session-$muscle",
                    type = RiskType.VOLUME,
                    severity = RiskSeverity.BLOCKING,
                    muscle = muscle,
                    title = "Volumen rígido excedido: $muscle",
                    message = "$muscle tiene ${"%.1f".format(data.effective)} series efectivas, límite: ${"%.0f".format(limits.maxVolumePerMuscleSession)}.",
                    action = "Reducir series o bajar intensidad para $muscle.",
                )
            }
        }

        return riesgos
    }

    private fun checkVolumeThresholds(
        volume: VolumeCalculationResult,
        thresholds: Map<String, VolumeThreshold>,
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        volume.volumeMap.forEach { (muscle, data) ->
            val threshold = thresholds[muscle] ?: return@forEach
            if (data.flat >= threshold.mrv) {
                riesgos += SessionRisk(
                    id = "threshold-mrv-$muscle",
                    type = RiskType.VOLUME,
                    severity = RiskSeverity.BLOCKING,
                    muscle = muscle,
                    title = "$muscle en o sobre MRV",
                    message = "Con ${"%.1f".format(data.flat)} series equivalentes, $muscle está en el límite recuperable (${"%.1f".format(threshold.mrv)} MRV). No añadir más volumen.",
                    action = "Mantener volumen actual o reducir.",
                )
            } else if (data.flat > threshold.mav) {
                riesgos += SessionRisk(
                    id = "threshold-mav-$muscle",
                    type = RiskType.VOLUME,
                    severity = RiskSeverity.WARNING,
                    muscle = muscle,
                    title = "$muscle sobre MAV",
                    message = "Con ${"%.1f".format(data.flat)} series, estás por encima del rango óptimo (MAV: ${"%.1f".format(threshold.mav)}). Añadir volumen tiene rendimientos decrecientes.",
                    action = "Considerar reducir 1 serie o bajar intensidad.",
                )
            }
        }
        return riesgos
    }

    private fun checkGenericVolumeLimits(
        volume: VolumeCalculationResult,
        settings: Settings,
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        val sessionLimit = defaultSessionVolumeLimit(settings)
        volume.volumeMap.forEach { (muscle, data) ->
            if (data.effective > sessionLimit) {
                val hasBlocking = riesgos.any { it.muscle == muscle && it.severity == RiskSeverity.BLOCKING }
                if (!hasBlocking) {
                    riesgos += SessionRisk(
                        id = "generic-volume-$muscle",
                        type = RiskType.VOLUME,
                        severity = RiskSeverity.WARNING,
                        muscle = muscle,
                        title = "Volumen elevado: $muscle",
                        message = "$muscle tiene ${"%.1f".format(data.effective)} series efectivas sobre el límite genérico de $sessionLimit.",
                        action = "Reducir 1-2 series o bajar intensidad.",
                    )
                }
            }
        }
        return riesgos
    }

    private fun checkSpinalLoad(
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        if (volume.totalSpinalLoad > 25.0) {
            val critical = volume.totalSpinalLoad > 40.0 || drain.spinal > 30
            riesgos += SessionRisk(
                id = "spinal-load",
                type = RiskType.SPINE,
                severity = if (critical) RiskSeverity.BLOCKING else RiskSeverity.WARNING,
                title = if (critical) "Carga axial elevada" else "Carga axial moderada",
                message = if (critical) {
                    "La sesión acumula carga axial alta (${"%.1f".format(volume.totalSpinalLoad)}). Bajar series o intensidad del ejercicio más demandante protege la columna."
                } else {
                    "La sesión suma carga axial relevante (${"%.1f".format(volume.totalSpinalLoad)}). Ajustar densidad o intensidad mejora la tolerancia."
                },
                action = "Usar variantes más estables o reducir carga axial.",
            )
        }
        return riesgos
    }

    private fun checkCnsFatigue(
        drain: PredictedDrain,
        volume: VolumeCalculationResult,
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        if (drain.cns >= 85 || volume.averageRpe >= 9.3) {
            riesgos += SessionRisk(
                id = "cns-fatigue",
                type = RiskType.CNS,
                severity = if (drain.cns >= 90) RiskSeverity.BLOCKING else RiskSeverity.WARNING,
                title = "Fatiga SNC elevada",
                message = "Tu energía SNC va alta para esta sesión (${drain.cns}%). Bajar RPE, subir RIR o reducir %1RM deja margen sin romper el plan.",
                action = "Reducir intensidad global.",
            )
        }
        return riesgos
    }

    private fun checkExcessFailure(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        val totalSets = volume.totalSets
        if (totalSets == 0) return riesgos

        var failureSets = 0
        input.allExercisesInSession.forEach { exercise ->
            exercise.validAugeSets().forEach { set ->
                if (set.isFailure || set.intensityMode == IntensityMode.FAILURE) {
                    failureSets++
                }
            }
        }
        val failureRatio = failureSets.toDouble() / totalSets
        if (failureRatio >= 0.5 && failureSets >= 3) {
            riesgos += SessionRisk(
                id = "excess-failure",
                type = RiskType.FAILURE,
                severity = if (failureRatio >= 0.7) RiskSeverity.BLOCKING else RiskSeverity.WARNING,
                title = "Exceso de series al fallo",
                message = "$failureSets de $totalSets series al fallo (${(failureRatio * 100).toInt()}%). Esto acumula fatiga SNC y articular más de lo necesario.",
                action = "Pasar a RIR 1-3 o bajar RPE antes de recortar volumen.",
            )
        }
        return riesgos
    }

    private fun checkJointStress(volume: VolumeCalculationResult): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        if (volume.elbowStress > 8) {
            riesgos += SessionRisk(
                id = "joint-elbow",
                type = RiskType.JOINT,
                severity = if (volume.elbowStress > 12) RiskSeverity.WARNING else RiskSeverity.INFO,
                title = "Estrés de codos",
                message = "Hay ${volume.elbowStress} series de trabajo aislado de tríceps en ángulos agresivos. Ajustar intensidad o distribuir accesorios ayuda.",
                action = "Reducir series de extensión de tríceps o usar variantes más seguras.",
            )
        }
        if (volume.kneeStress > 8) {
            riesgos += SessionRisk(
                id = "joint-knee",
                type = RiskType.JOINT,
                severity = if (volume.kneeStress > 12) RiskSeverity.WARNING else RiskSeverity.INFO,
                title = "Estrés de rodillas",
                message = "Extensiones puras o patrones similares se están acumulando (${volume.kneeStress} series). Bajar densidad o reforzar calentamiento mejora tolerancia.",
                action = "Reducir series de extensión de cuádriceps o añadir calentamiento dinámico.",
            )
        }
        return riesgos
    }

    // ─── Weekly Volume ──────────────────────────────────────────────────────

    internal fun calcularVolumenSemanal(input: SessionAssistantInput): Map<String, Double> {
        val weeklyMap = mutableMapOf<String, Double>()
        input.weekSessions.forEach { session ->
            val exercises = session.exercises + session.parts.flatMap { it.exercises }
            exercises.forEach { exercise ->
                val info = resolveExerciseInfo(exercise, input.exerciseIndex) ?: return@forEach
                exercise.validAugeSets().forEach { set ->
                    val effectiveRpe = set.effectiveTargetRpe()
                    val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(effectiveRpe)
                    SessionMuscleFilter.relevantMusclesFor(info).forEach { muscle ->
                        val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                        val hyperFactor = resolveMuscleVolumeContribution(muscle)
                        weeklyMap[normalized] = (weeklyMap[normalized] ?: 0.0) + hyperFactor * volumeMultiplier
                    }
                }
            }
        }
        return weeklyMap
    }

    private fun checkWeeklyVolume(
        input: SessionAssistantInput,
        weeklyVolume: Map<String, Double>,
        thresholds: Map<String, VolumeThreshold>,
    ): List<SessionRisk> {
        val riesgos = mutableListOf<SessionRisk>()
        val weeklyLimit = input.ruleLimits.maxVolumePerMuscleWeekly

        weeklyVolume.forEach { (muscle, weeklySets) ->
            if (weeklySets > weeklyLimit) {
                riesgos += SessionRisk(
                    id = "weekly-volume-$muscle",
                    type = RiskType.VOLUME,
                    severity = RiskSeverity.WARNING,
                    muscle = muscle,
                    title = "Volumen semanal alto: $muscle",
                    message = "$muscle tiene ${"%.1f".format(weeklySets)} series en la semana, por encima del límite de ${"%.0f".format(weeklyLimit)}. Repartir el estímulo ayuda a la recuperación.",
                    action = "Reducir volumen en sesiones futuras o redistribute.",
                )
            }

            val weeklyMrv = thresholds[muscle]?.let {
                it.mrv * 3.0
            } ?: weeklyLimit.toDouble()
            if (weeklySets >= weeklyMrv) {
                riesgos += SessionRisk(
                    id = "weekly-mrv-$muscle",
                    type = RiskType.VOLUME,
                    severity = RiskSeverity.BLOCKING,
                    muscle = muscle,
                    title = "$muscle en o sobre MRV semanal",
                    message = "$muscle tiene ${"%.1f".format(weeklySets)} series semanales, en o sobre MRV (${"%.1f".format(weeklyMrv)}). No añadir más volumen este día.",
                    action = "Mantener volumen actual o reducir.",
                )
            }
        }
        return riesgos
    }

    // ─── Suggestions Generation ───────────────────────────────────────────────

    internal fun generarAjustes(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        riesgos: List<SessionRisk>,
    ): List<AssistantSuggestion> {
        val ajustes = mutableListOf<AssistantSuggestion>()

        // Priority 1: If excess failure, recommend lowering intensity before volume
        val failureRisk = riesgos.find { it.type == RiskType.FAILURE }
        if (failureRisk != null) {
            ajustes += AssistantSuggestion(
                id = "adj-remove-failure",
                type = AssistantActionType.REMOVE_FAILURE,
                title = "Reducir series al fallo",
                message = "Pasar a RIR 1-3 o bajar RPE. Esto reduce fatiga SNC y articular sin perder volumen efectivo.",
                priority = 1,
            )
        }

        // Priority 2: High spinal load → lower RPE
        val spinalRisk = riesgos.find { it.type == RiskType.SPINE }
        if (spinalRisk != null && spinalRisk.severity != RiskSeverity.INFO) {
            ajustes += AssistantSuggestion(
                id = "adj-lower-rpe-spine",
                type = AssistantActionType.LOWER_RPE,
                title = "Bajar intensidad para proteger columna",
                message = "Reducir RPE o %RM en ejercicios axiales principales. Esto baja la carga sin eliminar volumen.",
                priority = 2,
            )
        }

        // Priority 3: CNS fatigue → lower RPE
        val cnsRisk = riesgos.find { it.type == RiskType.CNS }
        if (cnsRisk != null) {
            ajustes += AssistantSuggestion(
                id = "adj-lower-rpe-cns",
                type = AssistantActionType.LOWER_RPE,
                title = "Bajar intensidad para reducir fatiga SNC",
                message = "Subir RIR o bajar RPE en 0.5-1.0 puntos. Deja margen para el resto de la sesión y la recuperación.",
                priority = 3,
            )
        }

        // Priority 4: Volume over MRV → reduce sets
        val volumeRisks = riesgos.filter { it.type == RiskType.VOLUME && it.severity == RiskSeverity.BLOCKING }
        volumeRisks.forEach { risk ->
            ajustes += AssistantSuggestion(
                id = "adj-reduce-${risk.muscle}",
                type = AssistantActionType.REDUCE_SET,
                title = "Reducir series de ${risk.muscle ?: "ejercicio"}",
                message = risk.message,
                muscle = risk.muscle,
                priority = 4,
            )
        }

        return ajustes.sortedBy { it.priority }
    }

    internal fun generarAjustesPorRings(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
    ): List<AssistantSuggestion> {
        val ajustes = mutableListOf<AssistantSuggestion>()
        val heavyExercises = volume.exerciseInsights
            .filter { it.muscular >= 40.0 || it.cns >= 40.0 || it.spinal >= 40.0 }
            .sortedWith(compareByDescending<ExerciseInsightData> { maxOf(it.muscular, it.cns, it.spinal) }.thenBy { it.name })
            .take(2)
        val targetNames = heavyExercises.joinToString(", ") { it.name }.ifBlank { "los ejercicios más demandantes" }

        if (drain.spinal >= 40) {
            ajustes += AssistantSuggestion(
                id = "rings-spinal-moderate",
                type = AssistantActionType.LOWER_RPE,
                title = "Moderar carga axial",
                message = "La batería espinal bajaría ${drain.spinal}%. Mantén la estructura, pero baja 0.5-1 RPE y recorta 1 serie total entre $targetNames.",
                priority = 1,
            )
        }

        if (drain.cns >= 40) {
            ajustes += AssistantSuggestion(
                id = "rings-cns-moderate",
                type = AssistantActionType.LOWER_RPE,
                title = "Bajar drenaje SNC",
                message = "La batería SNC bajaría ${drain.cns}%. Reduce levemente la intensidad en los sets duros y evita llevar series al límite en $targetNames.",
                priority = 2,
            )
        }

        if (drain.muscular >= 40) {
            val muscle = volume.volumeMap.maxByOrNull { it.value.effective }?.key
            ajustes += AssistantSuggestion(
                id = "rings-muscular-moderate-${muscle ?: "general"}",
                type = AssistantActionType.REDUCE_SET,
                title = "Ajustar volumen efectivo",
                message = "La batería muscular bajaría ${drain.muscular}%. Recorta 1 serie del bloque principal y baja 0.5 RPE en los sets finales para conservar estímulo.",
                muscle = muscle,
                priority = 3,
            )
        }

        heavyExercises.forEachIndexed { index, exercise ->
            val peak = maxOf(exercise.muscular, exercise.cns, exercise.spinal)
            if (peak < 40.0) return@forEachIndexed
            ajustes += AssistantSuggestion(
                id = "rings-exercise-${exercise.exerciseId}",
                type = AssistantActionType.LOWER_RPE,
                title = "Suavizar ${exercise.name}",
                message = "${exercise.name} concentra un drenaje cercano a ${peak.roundToInt()}%. Usa un ajuste moderado: 1 serie menos si hay muchas series, o 0.5-1 RPE menos si el volumen debe mantenerse.",
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.name,
                priority = 4 + index,
            )
        }

        return ajustes.distinctBy { it.id }.sortedBy { it.priority }
    }

    internal fun generarOportunidades(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        thresholds: Map<String, VolumeThreshold>,
        riesgos: List<SessionRisk>,
        weeklyVolume: Map<String, Double> = emptyMap(),
    ): List<AssistantSuggestion> {
        val oportunidades = mutableListOf<AssistantSuggestion>()
        val bloqueantes = riesgos.filter { it.severity == RiskSeverity.BLOCKING }.mapNotNull { it.muscle }.toSet()

        if (drain.cns >= 70 || drain.spinal >= 20) return oportunidades

        thresholds.forEach { (muscle, threshold) ->
            if (muscle in bloqueantes) return@forEach
            val current = volume.volumeMap[muscle]?.flat ?: 0.0
            val weeklyCurrent = weeklyVolume[muscle] ?: 0.0
            val weeklyMrv = threshold.mrv * 3.0
            if (current < threshold.mev && weeklyCurrent < weeklyMrv * 0.8) {
                val gap = threshold.mev - current
                oportunidades += AssistantSuggestion(
                    id = "opp-$muscle",
                    type = AssistantActionType.ADD_GHOST_EXERCISE,
                    title = "$muscle bajo MEV",
                    message = "$muscle tiene ${"%.1f".format(current)} series, por debajo de MEV (${"%.1f".format(threshold.mev)}). Quedan ${"%.1f".format(gap)} series de margen real.",
                    muscle = muscle,
                    priority = 10,
                )
            }
        }

        return oportunidades
    }

    // ─── Ghost Exercise Cards ─────────────────────────────────────────────────

    internal fun generarTarjetasFantasma(
        input: SessionAssistantInput,
        volume: VolumeCalculationResult,
        drain: PredictedDrain,
        thresholds: Map<String, VolumeThreshold>,
        riesgos: List<SessionRisk>,
        weeklyVolume: Map<String, Double> = emptyMap(),
    ): List<GhostExerciseCard> {
        val candidate = mutableListOf<GhostExerciseCard>()
        val bloqueantes = riesgos.filter { it.severity == RiskSeverity.BLOCKING }.mapNotNull { it.muscle }.toSet()

        if (drain.cns >= 75 || drain.spinal >= 25) return candidate

        val sessionPatterns = input.allExercisesInSession.mapNotNull { exercise ->
            val info = resolveExerciseInfo(exercise, input.exerciseIndex)
            info?.force
        }.toSet()

        thresholds.forEach { (muscle, threshold) ->
            if (muscle in bloqueantes) return@forEach
            val current = volume.volumeMap[muscle]?.flat ?: 0.0
            if (current >= threshold.mev) return@forEach

            val weeklyCurrent = weeklyVolume[muscle] ?: 0.0
            val weeklyMrv = threshold.mrv * 3.0
            val weeklyLimit = input.ruleLimits.maxVolumePerMuscleWeekly
            if (weeklyCurrent >= weeklyMrv || weeklyCurrent >= weeklyLimit) return@forEach

            val gap = threshold.mev - current
            val ejerciciosCompatibles = input.exerciseIndex.values.filter { info ->
                info.involvedMuscles.any { m ->
                    VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis) == muscle
                } && (sessionPatterns.isEmpty() || info.force == null || info.force in sessionPatterns)
            }.sortedByDescending { info ->
                info.involvedMuscles.count { m ->
                    VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis) == muscle
                }
            }

            ejerciciosCompatibles.take(2).forEach { info ->
                val setsNuevos = minOf(3, ceil(gap).toInt().coerceAtLeast(1))
                val impactoDrenaje = estimarImpactoDrenajeCns(info, setsNuevos)
                val impactoColumna = (info.axialLoadFactor ?: 0.0) * setsNuevos
                val weeklyAfter = (weeklyVolume[muscle] ?: 0.0) + setsNuevos * resolveMuscleVolumeContribution(
                    info.involvedMuscles.firstOrNull { m ->
                        VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis) == muscle
                    } ?: return@forEach
                )

                if (drain.cns + impactoDrenaje < 90 && drain.spinal + impactoColumna < 40 && weeklyAfter < weeklyMrv) {
                    candidate += GhostExerciseCard(
                        cardId = "ghost-${muscle}-${info.id}",
                        exerciseDbId = info.id,
                        name = info.name,
                        motivo = "$muscle está bajo MEV (${"%.1f".format(current)}/${"%.1f".format(threshold.mev)})",
                        sets = setsNuevos,
                        reps = 10,
                        rpe = 7.5,
                        restSeconds = info.averageRestSeconds ?: 90,
                        impactoVolumen = "+${setsNuevos} sets $muscle",
                        impactoDrenaje = "+${impactoDrenaje} SNC",
                        impactoColumna = if (impactoColumna > 0) "+${"%.1f".format(impactoColumna)} columna" else "Sin impacto",
                        compatibleConSplit = true,
                    )
                }
            }
        }

        return candidate
    }

    private fun estimarImpactoDrenajeCns(info: ExerciseMuscleInfo, sets: Int): Int {
        val baseCnc = info.cnc ?: 2.0
        val perSet = (baseCnc / 5.0 * 8.0).roundToInt()
        return (perSet * sets).coerceIn(0, 30)
    }

    // ─── Template Search ──────────────────────────────────────────────────────

    internal fun buscarPlantillasCompatibles(
        input: SessionAssistantInput,
        allTemplates: List<SessionTemplate>,
    ): List<TemplatePreview> {
        if (allTemplates.isEmpty()) return emptyList()

        val sessionMuscles = calcularMusculosEnSesion(input.allExercisesInSession, input.exerciseIndex)

        return allTemplates
            .filter { !it.isArchived }
            .mapNotNull { template ->
                val templateExercises = template.session.exercises + template.session.parts.flatMap { it.exercises }
                val templateMuscles = calcularMusculosEnSesion(templateExercises, input.exerciseIndex)
                val overlap = sessionMuscles.intersect(templateMuscles)
                if (overlap.isEmpty()) return@mapNotNull null

                val drenaje = input.customTemplateDrains[template.id]
                    ?: try {
                    AugeFatigueEngine.calculateAdjustedPredictedDrain(
                        template.session, input.exerciseIndex, input.settings,
                    )
                } catch (_: Throwable) {
                    PredictedDrain(cns = 15, muscular = 20, spinal = 10)
                }

                val advertencias = mutableListOf<String>()
                if (drenaje.cns > 80) advertencias += "Alto drenaje SNC (${drenaje.cns}%)"
                if (drenaje.spinal > 25) advertencias += "Carga axial elevada (${drenaje.spinal}%)"
                if (drenaje.muscular > 70) advertencias += "Drenaje muscular alto (${drenaje.muscular}%)"

                val hasContent = templateExercises.isNotEmpty()
                val modoRecomendado = if (hasContent && input.allExercisesInSession.isNotEmpty()) {
                    SessionTemplateApplyMode.APPEND
                } else {
                    SessionTemplateApplyMode.REPLACE
                }

                TemplatePreview(
                    template = template,
                    modoRecomendado = modoRecomendado,
                    volumenPorMusculo = calcularVolumenPlantilla(templateExercises, input.exerciseIndex),
                    drenajeEstimado = drenaje,
                    advertencias = advertencias,
                    duracionEstimada = template.estimatedDurationMinutes ?: 60,
                )
            }
            .sortedBy { it.advertencias.size }
            .take(4)
    }

    private fun calcularMusculosEnSesion(
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Set<String> {
        return exercises.flatMap { exercise ->
            val info = resolveExerciseInfo(exercise, exerciseIndex) ?: return@flatMap emptyList()
            SessionMuscleFilter.relevantMusclesFor(info).map { muscle ->
                VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
            }
        }.toSet()
    }

    private fun calcularVolumenPlantilla(
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val volumeMap = mutableMapOf<String, Double>()
        exercises.forEach { exercise ->
            val info = resolveExerciseInfo(exercise, exerciseIndex) ?: return@forEach
            val validSets = exercise.sets.filterNot { it.isIneffective }
            validSets.forEach { set ->
                val effectiveRpe = set.effectiveTargetRpe()
                val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(effectiveRpe)
                SessionMuscleFilter.relevantMusclesFor(info).forEach { muscle ->
                    val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                    val hyperFactor = resolveMuscleVolumeContribution(muscle)
                    volumeMap[normalized] = (volumeMap[normalized] ?: 0.0) + hyperFactor * volumeMultiplier
                }
            }
        }
        return volumeMap
    }

    // ─── Verdict ──────────────────────────────────────────────────────────────

    internal fun clasificarVeredicto(
        riesgos: List<SessionRisk>,
        drain: PredictedDrain,
        settings: Settings,
    ): Verdict {
        if (riesgos.any { it.severity == RiskSeverity.BLOCKING }) return Verdict.CRITICAL
        if (riesgos.count { it.severity == RiskSeverity.WARNING } >= 2) return Verdict.FATIGUING
        if (riesgos.isNotEmpty() || drain.cns > 60 || drain.spinal > 20) return Verdict.WARNING
        return Verdict.OPTIMAL
    }

    private fun calcularScore(veredicto: Verdict, riesgos: List<SessionRisk>, drain: PredictedDrain): Int {
        val base = when (veredicto) {
            Verdict.OPTIMAL -> 90
            Verdict.WARNING -> 70
            Verdict.FATIGUING -> 50
            Verdict.CRITICAL -> 25
        }
        val penalty = riesgos.count { it.severity == RiskSeverity.WARNING } * 3
        val drainPenalty = if (drain.cns > 70) 5 else 0
        return (base - penalty - drainPenalty).coerceIn(0, 100)
    }

    private fun construirResumen(
        veredicto: Verdict,
        riesgos: List<SessionRisk>,
        ajustes: List<AssistantSuggestion>,
        tarjetas: List<GhostExerciseCard>,
    ): String {
        val parts = mutableListOf<String>()
        when (veredicto) {
            Verdict.OPTIMAL -> parts.add("La sesión está bien balanceada.")
            Verdict.WARNING -> parts.add("Hay algunos ajustes a considerar.")
            Verdict.FATIGUING -> parts.add("La sesión acumula fatiga considerable.")
            Verdict.CRITICAL -> parts.add("Hay riesgos importantes que atender antes de continuar.")
        }
        if (riesgos.isNotEmpty()) {
            parts.add("${riesgos.size} riesgo${if (riesgos.size > 1) "s" else ""} detectado${if (riesgos.size > 1) "s" else ""}.")
        }
        if (ajustes.isNotEmpty()) {
            parts.add("${ajustes.size} ajuste${if (ajustes.size > 1) "s" else ""} sugerido${if (ajustes.size > 1) "s" else ""}.")
        }
        if (tarjetas.isNotEmpty()) {
            parts.add("${tarjetas.size} propuesta${if (tarjetas.size > 1) "s" else ""} disponible${if (tarjetas.size > 1) "s" else ""}.")
        }
        return parts.joinToString(" ")
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    internal fun Exercise.validAugeSets(): List<ExerciseSet> =
        sets.filterNot { it.isIneffective }

    internal fun ExerciseSet.effectiveTargetRpe(): Double {
        if (isFailure || intensityMode == IntensityMode.FAILURE) return 10.0
        targetRPE?.let { return it.coerceIn(1.0, 10.0) }
        targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
        return 8.0
    }

    internal fun resolveExerciseInfo(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val byId = exercise.exerciseDbId ?: exercise.exerciseId
        return byId?.lowercase()?.let(exerciseIndex::get)
            ?: exerciseIndex.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
    }

    internal fun defaultSessionVolumeLimit(settings: Settings): Int {
        val base = when (settings.calorieGoalObjective) {
            CalorieGoalObjective.DEFICIT -> 8.0
            CalorieGoalObjective.MAINTENANCE -> 9.0
            CalorieGoalObjective.SURPLUS -> 10.0
        }
        val athleteAdjustment = when (settings.athleteType) {
            com.example.kpkn.data.models.AthleteType.BODYBUILDER,
            com.example.kpkn.data.models.AthleteType.POWERBUILDER -> 0.8
            com.example.kpkn.data.models.AthleteType.POWERLIFTER,
            com.example.kpkn.data.models.AthleteType.WEIGHTLIFTER -> -0.4
            else -> 0.0
        }
        return (base + athleteAdjustment).roundToInt().coerceAtLeast(6)
    }

    internal fun defaultWeeklyVolumeLimit(settings: Settings): Int = when (settings.calorieGoalObjective) {
        CalorieGoalObjective.DEFICIT -> 20
        CalorieGoalObjective.MAINTENANCE -> 24
        CalorieGoalObjective.SURPLUS -> 28
    }

    internal fun buildVolumeThresholds(
        input: SessionAssistantInput,
        sessionVolumeByMuscle: Map<String, MuscularVolumeAccumulator>,
    ): Map<String, VolumeThreshold> {
        val personalized = input.program
            ?.volumeRecommendations
            .orEmpty()
            .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
            .mapValues { (_, grouped) ->
                val mev = grouped.sumOf { it.minEffectiveVolume }.toDouble().coerceAtLeast(1.0)
                val mav = grouped.sumOf { it.maxAdaptiveVolume }.toDouble().coerceAtLeast(mev)
                val mrv = grouped.sumOf { it.maxRecoverableVolume }.toDouble().coerceAtLeast(mav)
                Triple(mev, mav, mrv)
            }

        val involvedMuscles = sessionVolumeByMuscle.keys.filter { it.isNotBlank() }.toSet()
        if (involvedMuscles.isEmpty()) return emptyMap()

        val defaultWeeklyMrv = defaultWeeklyVolumeLimit(input.settings).toDouble().coerceAtLeast(8.0)
        val defaultWeeklyMav = (defaultWeeklyMrv * 0.8).coerceAtLeast(6.0)
        val defaultWeeklyMev = (defaultWeeklyMav * 0.65).coerceAtLeast(4.0)

        return involvedMuscles.associateWith { muscle ->
            val fromProgram = personalized[muscle]
            val weeklyMev = fromProgram?.first ?: defaultWeeklyMev
            val weeklyMav = fromProgram?.second ?: defaultWeeklyMav
            val weeklyMrv = fromProgram?.third ?: defaultWeeklyMrv
            VolumeThreshold(
                mev = (weeklyMev / 3.0).coerceAtLeast(1.0),
                mav = (weeklyMav / 3.0).coerceAtLeast(1.0),
                mrv = (weeklyMrv / 3.0).coerceAtLeast(1.0),
            )
        }
    }

    fun findNextSessionWithMuscles(
        currentSessionId: String,
        weekSessions: List<com.example.kpkn.data.models.Session>,
        muscleIds: List<String>,
        exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    ): com.example.kpkn.data.models.Session? {
        val currentIdx = weekSessions.indexOfFirst { it.id == currentSessionId }
        if (currentIdx == -1) return null
        
        for (i in (currentIdx + 1) until weekSessions.size) {
            val session = weekSessions[i]
            if (sessionContainsAnyMuscle(session, muscleIds, exerciseIndex)) {
                return session
            }
        }
        
        for (i in 0 until currentIdx) {
            val session = weekSessions[i]
            if (sessionContainsAnyMuscle(session, muscleIds, exerciseIndex)) {
                return session
            }
        }
        return null
    }

    private fun sessionContainsAnyMuscle(
        session: com.example.kpkn.data.models.Session,
        muscleIds: List<String>,
        exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    ): Boolean {
        for (ex in session.allExercises()) {
            val dbId = ex.exerciseDbId ?: ex.exerciseId ?: continue
            val info = exerciseIndex[dbId] ?: continue
            for (muscle in info.involvedMuscles) {
                if (muscle.role == com.example.kpkn.data.models.MuscleRole.PRIMARY) {
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                    if (muscleIds.contains(mId)) return true
                }
            }
        }
        return false
    }

    fun computeProposedDiscounts(
        currentSession: com.example.kpkn.data.models.Session,
        nextSession: com.example.kpkn.data.models.Session,
        targetMuscles: List<String>,
        completedSets: Map<String, com.example.kpkn.data.models.CompletedSet>,
        exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    ): List<com.example.kpkn.data.models.MuscleAdvance> {
        val muscleAdvances = mutableListOf<com.example.kpkn.data.models.MuscleAdvance>()
        val completedByExercise = mutableMapOf<String, Int>()
        for ((key, _) in completedSets) {
            var exerciseId = key.substringBeforeLast("_")
            if (exerciseId.endsWith("_L") || exerciseId.endsWith("_R")) {
                exerciseId = exerciseId.substringBeforeLast("_")
            }
            completedByExercise[exerciseId] = (completedByExercise[exerciseId] ?: 0) + 1
        }

        for (muscleId in targetMuscles) {
            var plannedSets = 0.0
            for (ex in currentSession.allExercises()) {
                val dbId = ex.exerciseDbId ?: ex.exerciseId ?: continue
                val info = exerciseIndex[dbId] ?: continue
                for (m in info.involvedMuscles) {
                    if (m.role != com.example.kpkn.data.models.MuscleRole.PRIMARY) continue
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis)
                    if (mId == muscleId) {
                        plannedSets += ex.sets.size
                    }
                }
            }

            var actualSets = 0.0
            for (ex in currentSession.allExercises()) {
                val sets = completedByExercise[ex.id] ?: 0
                if (sets == 0) continue
                val dbId = ex.exerciseDbId ?: ex.exerciseId ?: continue
                val info = exerciseIndex[dbId] ?: continue
                for (m in info.involvedMuscles) {
                    if (m.role != com.example.kpkn.data.models.MuscleRole.PRIMARY) continue
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis)
                    if (mId == muscleId) {
                        actualSets += sets
                    }
                }
            }

            val delta = actualSets - plannedSets
            if (delta <= 0.0) continue

            val proposals = mutableListOf<com.example.kpkn.data.models.VolumeDiscountProposal>()
            var remainingDiscount = delta
            
            for (ex in nextSession.allExercises()) {
                if (remainingDiscount <= 0.0) break
                val dbId = ex.exerciseDbId ?: ex.exerciseId ?: continue
                val info = exerciseIndex[dbId] ?: continue
                var trainsMuscle = false
                for (m in info.involvedMuscles) {
                    if (m.role != com.example.kpkn.data.models.MuscleRole.PRIMARY) continue
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis)
                    if (mId == muscleId) {
                        trainsMuscle = true
                        break
                    }
                }
                
                if (trainsMuscle) {
                    val maxDiscount = (ex.sets.size - 1).coerceAtLeast(0).toDouble()
                    if (maxDiscount > 0) {
                        val discount = remainingDiscount.coerceAtMost(maxDiscount)
                        proposals.add(
                            com.example.kpkn.data.models.VolumeDiscountProposal(
                                exerciseId = ex.id,
                                exerciseName = ex.name,
                                currentRole = "PRIMARY",
                                discountSets = discount,
                                reason = "Excedente de volumen de " + muscleId + " (+" + delta.toInt() + " series)",
                            )
                        )
                        remainingDiscount -= discount
                    }
                }
            }

            if (proposals.isNotEmpty()) {
                muscleAdvances.add(
                    com.example.kpkn.data.models.MuscleAdvance(
                        muscleId = muscleId,
                        muscleName = muscleId,
                        currentSets = actualSets,
                        targetSets = plannedSets,
                        deficitSets = delta,
                        targetSessionId = nextSession.id,
                        targetSessionName = nextSession.name,
                        discountProposals = proposals,
                    )
                )
            }
        }
        return muscleAdvances
    }
}
