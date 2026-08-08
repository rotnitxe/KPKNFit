package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogSearchRedirects
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeClassifiers
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import com.example.kpkn.domain.calculations.estimateSessionDurationMinutes
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.roundToInt

internal data class AugeVolumeAccumulator(
    var flat: Double = 0.0,
    var effective: Double = 0.0,
    var fail: Double = 0.0,
)

internal data class MuscleRoleBreakdown(
    var primary: Double = 0.0,
    var secondary: Double = 0.0,
    var stabilizer: Double = 0.0,
    var neutralizer: Double = 0.0,
) {
    val total: Double
        get() = primary + secondary + stabilizer + neutralizer
    val primaryShare: Double
        get() = if (total > 0.0) primary / total else 0.0
    val secondaryShare: Double
        get() = if (total > 0.0) secondary / total else 0.0
    val stabilizerShare: Double
        get() = if (total > 0.0) (stabilizer + neutralizer) / total else 0.0
}

internal data class MuscleRecommendationContext(
    val breakdown: MuscleRoleBreakdown = MuscleRoleBreakdown(),
    var usesPercent: Boolean = false,
    var usesRir: Boolean = false,
    var usesFailure: Boolean = false,
)

internal data class SessionAugeComputation(
    val drain: PredictedDrain,
    val setCount: Int,
    val durationMinutes: Int,
    val difficulty: Int,
    val averageRpe: Double,
    val volumeMap: Map<String, AugeVolumeAccumulator>,
    val muscleDrainProjection: Map<String, Int>,
    val muscleEnergyDrain: Map<String, Int>,
    val muscleSpinalDrain: Map<String, Int>,
    val totalSpinalLoad: Double,
    val elbowStress: Int,
    val kneeStress: Int,
    val exerciseInsights: List<SessionEditorAugeExerciseInsight>,
    val muscleRoleMap: Map<String, MuscleRoleBreakdown>,
    val muscleRecommendationContext: Map<String, MuscleRecommendationContext>,
)

internal fun buildAugeSummary(
    currentSession: Session,
    weekSessions: List<Session>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    settings: Settings,
    programLogs: List<WorkoutLog>,
    program: Program?,
    programId: String,
    mesoIndex: Int,
): SessionEditorAugeSummary {
    val currentMetrics = computeSessionAugeComputation(
        session = currentSession,
        exerciseIndex = exerciseIndex,
        settings = settings,
        programLogs = programLogs,
        programId = programId,
        mesoIndex = mesoIndex,
    )
    val weeklySessions = if (weekSessions.any { it.id == currentSession.id }) weekSessions else weekSessions + currentSession
    val weeklyMetrics = weeklySessions.map {
        computeSessionAugeComputation(
            session = it,
            exerciseIndex = exerciseIndex,
            settings = settings,
            programLogs = programLogs,
            programId = programId,
            mesoIndex = mesoIndex,
        )
    }
    val weeklyDrain = aggregateWeeklyDrain(weeklyMetrics.map { it.drain })
    val weeklySetCount = weeklyMetrics.sumOf { it.setCount }
    val weeklyDurationMinutes = weeklyMetrics.sumOf { it.durationMinutes }
    val weeklyDifficulty = computeDifficulty(
        averageRpe = weeklyMetrics.map { it.averageRpe }.filter { it > 0.0 }.averageOrNull() ?: 0.0,
        averageRm = 0.0,
    )

    val sessionLimit = defaultSessionVolumeLimit(settings)
    val weeklyLimit = defaultWeeklyVolumeLimit(settings)
    val alerts = mutableListOf<SessionEditorAugeAlert>()
    val suggestions = mutableListOf<SessionEditorAugeAlert>()

    currentMetrics.volumeMap.entries
        .sortedByDescending { it.value.effective }
        .forEach { (muscle, data) ->
            if (data.effective <= sessionLimit) return@forEach
            val roleBreakdown = currentMetrics.muscleRoleMap[muscle] ?: MuscleRoleBreakdown()
            val context = currentMetrics.muscleRecommendationContext[muscle] ?: MuscleRecommendationContext()
            val intensityHint = when {
                context.usesFailure -> "conviene quitar fallo o pasar a RIR"
                context.usesRir -> "subir un poco el RIR"
                context.usesPercent -> "bajar %RM"
                else -> "bajar RPE"
            }
            val roleHint = when {
                roleBreakdown.stabilizerShare >= 0.45 -> "Gran parte viene de estabilizadores; ajustar técnica o bajar intensidad global ayuda."
                roleBreakdown.secondaryShare >= 0.45 -> "Mucho volumen llega por secundarios en multiarticulares."
                else -> "El volumen es principalmente directo."
            }
            val message = when {
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) >= 0.7 ->
                    "Muchas series cerca del fallo. $roleHint Mejor recortar series y $intensityHint."
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) <= 0.3 ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. $roleHint Puedes bajar series o $intensityHint."
                else ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. $roleHint Recorta un poco o $intensityHint."
            }
            val correction = when {
                context.usesFailure || context.usesRir || context.usesPercent -> SessionEditorAugeCorrectionType.REDUCE_RPE
                else -> SessionEditorAugeCorrectionType.REDUCE_SERIES
            }
            suggestions += SessionEditorAugeAlert(
                id = "volume-session-$muscle",
                title = "Recomendación para $muscle",
                message = message,
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.SESSION,
                muscle = muscle,
                correctionType = correction,
            )
        }

    val weeklyVolumeMap = mutableMapOf<String, AugeVolumeAccumulator>()
    weeklySessions.forEach { session ->
        accumulateSessionVolume(session, exerciseIndex, weeklyVolumeMap)
    }
    val volumeThresholdsByMuscle = buildVolumeThresholdsByMuscle(
        sessionVolumeByMuscle = currentMetrics.volumeMap.mapValues { (_, value) -> value.flat },
        weeklyVolumeByMuscle = weeklyVolumeMap.mapValues { (_, value) -> value.flat },
        program = program,
        settings = settings,
    )
    weeklyVolumeMap.entries
        .sortedByDescending { it.value.flat }
        .forEach { (muscle, data) ->
            if (data.flat <= weeklyLimit) return@forEach
            suggestions += SessionEditorAugeAlert(
                id = "volume-week-$muscle",
                title = "Recomendación semanal para $muscle",
                message = "${formatOneDecimal(data.flat)} series equivalentes sobre $weeklyLimit. Repartir el estímulo de la semana te deja más fresco.",
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.WEEK,
                muscle = muscle,
                correctionType = SessionEditorAugeCorrectionType.REDUCE_SERIES,
            )
        }

    if (currentMetrics.totalSpinalLoad > 25.0) {
        val topAxialExercise = currentMetrics.exerciseInsights.maxByOrNull { it.spinal }
        val critical = currentMetrics.totalSpinalLoad > 40.0
        suggestions += SessionEditorAugeAlert(
            id = "system-spinal",
            title = "Recomendación columna",
            message = if (critical) {
                "La sesión acumula bastante carga axial. Bajar series o intensidad del ejercicio más demandante protege la columna."
            } else {
                "La sesión ya suma carga axial relevante. Ajustar densidad o intensidad puede mejorar la tolerancia."
            },
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topAxialExercise?.exerciseId,
            exerciseName = topAxialExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE,
        )
    }

    if (currentMetrics.drain.cns >= 85 || currentMetrics.averageRpe >= 9.3) {
        val topNeuralExercise = currentMetrics.exerciseInsights.maxByOrNull { it.cns }
        suggestions += SessionEditorAugeAlert(
            id = "system-neural",
            title = "Recomendación energía",
            message = "Tu Energía ya va alta para esta sesión. Bajar RPE, subir RIR o reducir %1RM deja margen sin romper el plan.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topNeuralExercise?.exerciseId,
            exerciseName = topNeuralExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_RPE,
        )
    }

    if (currentMetrics.elbowStress > 8) {
        suggestions += SessionEditorAugeAlert(
            id = "system-elbow",
            title = "Recomendación codos",
            message = "Hay bastante trabajo aislado de tríceps en ángulos agresivos. Ajustar intensidad o distribuir mejor los accesorios ayuda.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    if (currentMetrics.kneeStress > 8) {
        suggestions += SessionEditorAugeAlert(
            id = "system-knee",
            title = "Recomendación rodillas",
            message = "Extensiones puras o patrones similares se están acumulando. Bajar densidad o reforzar calentamiento mejora tolerancia.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    if (settings.calorieGoalObjective == CalorieGoalObjective.DEFICIT) {
        suggestions += SessionEditorAugeAlert(
            id = "info-deficit",
            title = "Déficit activo",
            message = "En déficit calórico conviene apretar menos el volumen efectivo por sesión para que la recuperación no se caiga.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    if (currentMetrics.averageRpe in 0.1..<6.5) {
        suggestions += SessionEditorAugeAlert(
            id = "suggest-light-intensity",
            title = "Sesión liviana",
            message = "La intensidad media está baja. Si el objetivo era más estímulo, aún hay margen para subir la intensidad (RPE, RIR o %RM).",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }

    val status = computeAugeStatus(currentMetrics.drain, weeklyMetrics.map { it.drain }, currentSession.id, weeklySessions, settings)

    val orderedAlerts = alerts
        .distinctBy { it.id }
        .sortedWith(
            compareBy<SessionEditorAugeAlert> {
                when (it.source) {
                    SessionEditorAugeAlertSource.SYSTEM -> 0
                    SessionEditorAugeAlertSource.SESSION -> 1
                    SessionEditorAugeAlertSource.WEEK -> 2
                    SessionEditorAugeAlertSource.EXERCISE -> 3
                }
            }.thenBy {
                it.title
            }
        )

    val orderedSuggestions = suggestions
        .distinctBy { it.id }
        .sortedBy { suggestion ->
            when (suggestion.source) {
                SessionEditorAugeAlertSource.SESSION -> 0
                SessionEditorAugeAlertSource.SYSTEM -> 1
                SessionEditorAugeAlertSource.WEEK -> 2
                SessionEditorAugeAlertSource.EXERCISE -> 3
            }
        }

    return SessionEditorAugeSummary(
        sessionDrain = currentMetrics.drain,
        weeklyDrain = weeklyDrain,
        sessionSetCount = currentMetrics.setCount,
        weeklySetCount = weeklySetCount,
        sessionDurationMinutes = currentMetrics.durationMinutes,
        weeklyDurationMinutes = weeklyDurationMinutes,
        sessionDifficulty = currentMetrics.difficulty,
        weeklyDifficulty = weeklyDifficulty,
        status = status,
        alerts = orderedAlerts,
        suggestions = orderedSuggestions,
        topExercises = currentMetrics.exerciseInsights.sortedByDescending { it.total }.take(4),
        muscleDrainProjection = currentMetrics.muscleDrainProjection,
        muscleEnergyDrain = currentMetrics.muscleEnergyDrain,
        muscleSpinalDrain = currentMetrics.muscleSpinalDrain,
        sessionVolumeByMuscle = currentMetrics.volumeMap.mapValues { (_, acc) -> acc.flat },
        weeklyVolumeByMuscle = weeklyVolumeMap.mapValues { (_, acc) -> acc.flat },
        volumeThresholdsByMuscle = volumeThresholdsByMuscle,
        usesCalibratedVolumeThresholds = program?.volumeRecommendations?.isNotEmpty() == true && program.athleteProfileScore != null,
    )
}

internal fun buildAugeSummaryFromMetrics(
    currentSession: Session,
    weekSessions: List<Session>,
    currentMetrics: SessionAugeComputation,
    weeklyMetrics: List<SessionAugeComputation>,
    program: Program?,
    settings: Settings,
): SessionEditorAugeSummary {
    val weeklyDrain = aggregateWeeklyDrain(weeklyMetrics.map { it.drain })
    val weeklySetCount = weeklyMetrics.sumOf { it.setCount }
    val weeklyDurationMinutes = weeklyMetrics.sumOf { it.durationMinutes }
    val weeklyDifficulty = computeDifficulty(
        averageRpe = weeklyMetrics.map { it.averageRpe }.filter { it > 0.0 }.averageOrNull() ?: 0.0,
        averageRm = 0.0,
    )
    val sessionLimit = defaultSessionVolumeLimit(settings)
    val weeklyLimit = defaultWeeklyVolumeLimit(settings)
    val alerts = mutableListOf<SessionEditorAugeAlert>()
    val suggestions = mutableListOf<SessionEditorAugeAlert>()
    currentMetrics.volumeMap.entries
        .sortedByDescending { it.value.effective }
        .forEach { (muscle, data) ->
            if (data.effective <= sessionLimit) return@forEach
            val roleBreakdown = currentMetrics.muscleRoleMap[muscle] ?: MuscleRoleBreakdown()
            val context = currentMetrics.muscleRecommendationContext[muscle] ?: MuscleRecommendationContext()
            val intensityHint = when {
                context.usesFailure -> "conviene quitar fallo o pasar a RIR"
                context.usesRir -> "subir un poco el RIR"
                context.usesPercent -> "bajar %RM"
                else -> "bajar RPE"
            }
            val roleHint = when {
                roleBreakdown.stabilizerShare >= 0.45 -> "Gran parte viene de estabilizadores; ajustar técnica o bajar intensidad global ayuda."
                roleBreakdown.secondaryShare >= 0.45 -> "Mucho volumen llega por secundarios en multiarticulares."
                else -> "El volumen es principalmente directo."
            }
            val message = when {
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) >= 0.7 ->
                    "Muchas series cerca del fallo. $roleHint Mejor recortar series y $intensityHint."
                data.fail > 0.0 && data.flat > 0.0 && (data.fail / data.flat) <= 0.3 ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. $roleHint Puedes bajar series o $intensityHint."
                else ->
                    "${formatOneDecimal(data.effective)} pts sobre $sessionLimit. $roleHint Recorta un poco o $intensityHint."
            }
            val correction = when {
                context.usesFailure || context.usesRir || context.usesPercent -> SessionEditorAugeCorrectionType.REDUCE_RPE
                else -> SessionEditorAugeCorrectionType.REDUCE_SERIES
            }
            suggestions += SessionEditorAugeAlert(
                id = "volume-session-$muscle",
                title = "Recomendación para $muscle",
                message = message,
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.SESSION,
                muscle = muscle,
                correctionType = correction,
            )
        }
    val combinedWeeklyVolumeFlat = weeklyMetrics.flatMap { it.volumeMap.entries }.groupBy({ it.key }, { it.value.flat }).mapValues { it.value.sum() }
    val volumeThresholdsByMuscle = buildVolumeThresholdsByMuscle(
        sessionVolumeByMuscle = currentMetrics.volumeMap.mapValues { (_, value) -> value.flat },
        weeklyVolumeByMuscle = combinedWeeklyVolumeFlat,
        program = program,
        settings = settings,
    )
    combinedWeeklyVolumeFlat.entries
        .sortedByDescending { it.value }
        .forEach { (muscle, flat) ->
            if (flat <= weeklyLimit) return@forEach
            suggestions += SessionEditorAugeAlert(
                id = "volume-week-$muscle",
                title = "Recomendación semanal para $muscle",
                message = "${formatOneDecimal(flat)} series equivalentes sobre $weeklyLimit. Repartir el estímulo de la semana te deja más fresco.",
                severity = SessionEditorAugeAlertSeverity.INFO,
                source = SessionEditorAugeAlertSource.WEEK,
                muscle = muscle,
                correctionType = SessionEditorAugeCorrectionType.REDUCE_SERIES,
            )
        }
    if (currentMetrics.totalSpinalLoad > 25.0) {
        val topAxialExercise = currentMetrics.exerciseInsights.maxByOrNull { it.spinal }
        val critical = currentMetrics.totalSpinalLoad > 40.0
        suggestions += SessionEditorAugeAlert(
            id = "system-spinal",
            title = "Recomendación columna",
            message = if (critical) {
                "La sesión acumula bastante carga axial. Bajar series o intensidad del ejercicio más demandante protege la columna."
            } else {
                "La sesión ya suma carga axial relevante. Ajustar densidad o intensidad puede mejorar la tolerancia."
            },
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topAxialExercise?.exerciseId,
            exerciseName = topAxialExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE,
        )
    }
    if (currentMetrics.drain.cns >= 85 || currentMetrics.averageRpe >= 9.3) {
        val topNeuralExercise = currentMetrics.exerciseInsights.maxByOrNull { it.cns }
        suggestions += SessionEditorAugeAlert(
            id = "system-neural",
            title = "Recomendación energía",
            message = "Tu Energía ya va alta para esta sesión. Bajar RPE, subir RIR o reducir %1RM deja margen sin romper el plan.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
            exerciseId = topNeuralExercise?.exerciseId,
            exerciseName = topNeuralExercise?.name,
            correctionType = SessionEditorAugeCorrectionType.REDUCE_RPE,
        )
    }
    if (currentMetrics.elbowStress > 8) {
        suggestions += SessionEditorAugeAlert(
            id = "system-elbow",
            title = "Recomendación codos",
            message = "Hay bastante trabajo aislado de tríceps en ángulos agresivos. Ajustar intensidad o distribuir mejor los accesorios ayuda.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }
    if (currentMetrics.kneeStress > 8) {
        suggestions += SessionEditorAugeAlert(
            id = "system-knee",
            title = "Recomendación rodillas",
            message = "Extensiones puras o patrones similares se están acumulando. Bajar densidad o reforzar calentamiento mejora tolerancia.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }
    if (settings.calorieGoalObjective == CalorieGoalObjective.DEFICIT) {
        suggestions += SessionEditorAugeAlert(
            id = "info-deficit",
            title = "Déficit activo",
            message = "En déficit calórico conviene apretar menos el volumen efectivo por sesión para que la recuperación no se caiga.",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }
    if (currentMetrics.averageRpe in 0.1..<6.5) {
        suggestions += SessionEditorAugeAlert(
            id = "suggest-light-intensity",
            title = "Sesión liviana",
            message = "La intensidad media está baja. Si el objetivo era más estímulo, aún hay margen para subir la intensidad (RPE, RIR o %RM).",
            severity = SessionEditorAugeAlertSeverity.INFO,
            source = SessionEditorAugeAlertSource.SYSTEM,
        )
    }
    val status = computeAugeStatus(currentMetrics.drain, weeklyMetrics.map { it.drain }, currentSession.id, weekSessions, settings)
    val orderedAlerts = alerts.distinctBy { it.id }.sortedWith(
        compareBy<SessionEditorAugeAlert> {
            when (it.source) {
                SessionEditorAugeAlertSource.SYSTEM -> 0
                SessionEditorAugeAlertSource.SESSION -> 1
                SessionEditorAugeAlertSource.WEEK -> 2
                SessionEditorAugeAlertSource.EXERCISE -> 3
            }
        }.thenBy { it.title }
    )
    val orderedSuggestions = suggestions.distinctBy { it.id }.sortedBy { suggestion ->
        when (suggestion.source) {
            SessionEditorAugeAlertSource.SESSION -> 0
            SessionEditorAugeAlertSource.SYSTEM -> 1
            SessionEditorAugeAlertSource.WEEK -> 2
            SessionEditorAugeAlertSource.EXERCISE -> 3
        }
    }
    return SessionEditorAugeSummary(
        sessionDrain = currentMetrics.drain,
        weeklyDrain = weeklyDrain,
        sessionSetCount = currentMetrics.setCount,
        weeklySetCount = weeklySetCount,
        sessionDurationMinutes = currentMetrics.durationMinutes,
        weeklyDurationMinutes = weeklyDurationMinutes,
        sessionDifficulty = currentMetrics.difficulty,
        weeklyDifficulty = weeklyDifficulty,
        status = status,
        alerts = orderedAlerts,
        suggestions = orderedSuggestions,
        topExercises = currentMetrics.exerciseInsights.sortedByDescending { it.total }.take(4),
        muscleDrainProjection = currentMetrics.muscleDrainProjection,
        muscleEnergyDrain = currentMetrics.muscleEnergyDrain,
        muscleSpinalDrain = currentMetrics.muscleSpinalDrain,
        sessionVolumeByMuscle = currentMetrics.volumeMap.mapValues { (_, acc) -> acc.flat },
        weeklyVolumeByMuscle = combinedWeeklyVolumeFlat,
        volumeThresholdsByMuscle = volumeThresholdsByMuscle,
        usesCalibratedVolumeThresholds = program?.volumeRecommendations?.isNotEmpty() == true && program.athleteProfileScore != null,
    )
}

internal fun computeSessionAugeComputation(
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    settings: Settings,
    programLogs: List<WorkoutLog>,
    programId: String,
    mesoIndex: Int,
): SessionAugeComputation {
    val exercises = session.allExercises()
    val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
    val volumeMap = mutableMapOf<String, AugeVolumeAccumulator>()
    val muscleDrainMap = mutableMapOf<String, Double>()
    val muscleEnergyDrainMap = mutableMapOf<String, Double>()
    val muscleSpinalDrainMap = mutableMapOf<String, Double>()
    val roleMap = mutableMapOf<String, MuscleRoleBreakdown>()
    val recommendationContext = mutableMapOf<String, MuscleRecommendationContext>()
    var totalSets = 0
    var totalSpinalLoad = 0.0
    var elbowStress = 0
    var kneeStress = 0
    var rpeSum = 0.0
    var rpeCount = 0
    var rmSum = 0.0
    var rmCount = 0
    val muscleSetCounters = mutableMapOf<String, Int>()

    val exerciseInsights = exercises.mapNotNull { exercise ->
        val info = resolveExerciseInfo(exercise, exerciseIndex) ?: return@mapNotNull null
        val validSets = exercise.validAugeSets()
        if (validSets.isEmpty()) return@mapNotNull null
        val musclesForVolume = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)

        val metrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.name, info.equipment, info) ?: AugeMetrics()
        var muscular = 0.0
        var cns = 0.0
        var spinal = 0.0
        totalSets += validSets.size

        val primaryMuscle = resolvePrimaryMuscle(info) ?: "Core"
        var accumulated = muscleSetCounters[primaryMuscle] ?: 0

        validSets.forEach { set ->
            val effectiveRpe = set.effectiveTargetRpe()
            rpeSum += effectiveRpe
            rpeCount++
            if (exercise.trainingMode == TrainingMode.RM && set.targetPercentageRM != null) {
                rmSum += set.targetPercentageRM / 100.0
                rmCount++
            }

            val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(effectiveRpe)
            val perExerciseContrib = VolumeCalculator.buildPerExerciseMuscleContributions(
                musclesForVolume,
            )
            perExerciseContrib.forEach { (normalized, hyperFactor) ->
                val bucket = volumeMap.getOrPut(normalized) { AugeVolumeAccumulator() }
                bucket.flat += hyperFactor
                bucket.effective += hyperFactor * volumeMultiplier
                if (effectiveRpe >= 9.5) bucket.fail += hyperFactor
            }
            musclesForVolume.filter(VolumeCalculator::isStandardVolumeMuscle).forEach { muscle ->
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
            val drain = AugeFatigueEngine.calculateSetBatteryDrain(
                set = completedSet,
                metrics = metrics,
                tanks = tanks,
                accumulatedSets = accumulated,
                restTime = exercise.restTime ?: settings.restTimerDefaultSeconds,
                densityMultiplier = AugeFatigueEngine.getDensityMultiplierForExercise(
                    supersetId = exercise.supersetGroupRefOrLegacyId(),
                    restTime = exercise.restTime ?: settings.restTimerDefaultSeconds,
                ),
            )
            muscular += drain.muscularDrainPct
            cns += drain.cnsDrainPct
            spinal += drain.spinalDrainPct

            val roleWeightByMuscle = linkedMapOf<String, Double>()
            VolumeCalculator.buildPerExerciseMuscleContributions(musclesForVolume)
                .forEach { (muscle, contribution) ->
                    roleWeightByMuscle[muscle] = contribution
                }
            val totalRoleWeight = roleWeightByMuscle.values.sum()
            if (totalRoleWeight > 0.0) {
                roleWeightByMuscle.forEach { (muscle, roleWeight) ->
                    val share = roleWeight / totalRoleWeight
                    if (drain.muscularDrainPct > 0.0) {
                        muscleDrainMap[muscle] = (muscleDrainMap[muscle] ?: 0.0) + (drain.muscularDrainPct * share)
                    }
                    if (drain.cnsDrainPct > 0.0) {
                        muscleEnergyDrainMap[muscle] =
                            (muscleEnergyDrainMap[muscle] ?: 0.0) + (drain.cnsDrainPct * share)
                    }
                    // Spinal cost only attributed when the exercise has axial load.
                    if (drain.spinalDrainPct > 0.0 && (info.axialLoadFactor ?: 0.0) > 0.0) {
                        muscleSpinalDrainMap[muscle] =
                            (muscleSpinalDrainMap[muscle] ?: 0.0) + (drain.spinalDrainPct * share)
                    }
                }
            }
        }
        muscleSetCounters[primaryMuscle] = accumulated

        musclesForVolume.filter(VolumeCalculator::isStandardVolumeMuscle).forEach { muscle ->
            val normalized = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
            val ctx = recommendationContext.getOrPut(normalized) { MuscleRecommendationContext() }
            if (exercise.trainingMode == TrainingMode.RM) ctx.usesPercent = true
            if (validSets.any { it.targetRIR != null }) ctx.usesRir = true
            if (validSets.any { it.isFailure || it.intensityMode == IntensityMode.FAILURE }) ctx.usesFailure = true
        }

        val name = info.name.lowercase()
        if (name.contains("press francés") || name.contains("press frances") || name.contains("rompecráneos") || name.contains("rompecraneos") || name.contains("extensión en polea") || name.contains("extension en polea")) {
            elbowStress += validSets.size
        }
        if (name.contains("extensión de cuádriceps") || name.contains("extension de cuadriceps") || name.contains("sissy")) {
            kneeStress += validSets.size
        }

        val insight = SessionEditorAugeExerciseInsight(
            exerciseId = exercise.id,
            name = exercise.name,
            muscular = muscular.coerceAtMost(100.0).roundToInt(),
            cns = cns.coerceAtMost(100.0).roundToInt(),
            spinal = spinal.coerceAtMost(100.0).roundToInt(),
            total = ((muscular + cns + spinal) / 3.0).coerceAtMost(100.0).roundToInt(),
            suggestion = exerciseSuggestionForInsight(muscular, cns, spinal),
        )
        insight
    }

    val averageRest = exercises.mapNotNull { it.restTime }.ifEmpty { listOf(settings.restTimerDefaultSeconds) }.average().toInt()
    val predictedDrain = try {
        val base = AugeFatigueEngine.calculateAdjustedPredictedDrain(session, exerciseIndex, settings)
        val ema = AugeFatigueEngine.calculateMesocycleStressEMA(
            logs = programLogs,
            programId = programId,
            mesoIndex = mesoIndex,
        )
        AugeFatigueEngine.adjustPredictedDrainWithEMA(base, ema)
    } catch (_: Throwable) {
        val fallback = AugeFatigueEngine.calculateAdjustedPredictedDrain(session, exerciseIndex, settings)
        val adjustedFallback = runCatching {
            val ema = AugeFatigueEngine.calculateMesocycleStressEMA(
                logs = programLogs,
                programId = programId,
                mesoIndex = mesoIndex,
            )
            AugeFatigueEngine.adjustPredictedDrainWithEMA(fallback, ema)
        }.getOrDefault(fallback)
        if (adjustedFallback.cns == 0 && adjustedFallback.muscular == 0 && adjustedFallback.spinal == 0 && totalSets > 0) {
            AugeFatigueEngine.DEFAULT_FALLBACK_PREDICTED_DRAIN
        } else {
            adjustedFallback
        }
    }

    return SessionAugeComputation(
        drain = predictedDrain,
        setCount = totalSets,
        durationMinutes = estimateSessionDurationMinutes(totalSets, averageRest),
        difficulty = computeDifficulty(
            averageRpe = if (rpeCount > 0) rpeSum / rpeCount else 0.0,
            averageRm = if (rmCount > 0) rmSum / rmCount else 0.0,
        ),
        averageRpe = if (rpeCount > 0) rpeSum / rpeCount else 0.0,
        volumeMap = volumeMap,
        muscleDrainProjection = muscleDrainMap
            .mapValues { (_, drainPct) -> drainPct.roundToInt().coerceIn(0, 100) },
        muscleEnergyDrain = scaleMuscleDrainMap(muscleEnergyDrainMap, predictedDrain.cns),
        muscleSpinalDrain = scaleMuscleDrainMap(muscleSpinalDrainMap, predictedDrain.spinal),
        totalSpinalLoad = totalSpinalLoad,
        elbowStress = elbowStress,
        kneeStress = kneeStress,
        exerciseInsights = exerciseInsights,
        muscleRoleMap = roleMap,
        muscleRecommendationContext = recommendationContext,
    )
}

/** Scale raw per-muscle drain shares so their sum ≈ targetRingDrain (session ring coherence). */
private fun scaleMuscleDrainMap(raw: Map<String, Double>, targetRingDrain: Int): Map<String, Int> {
    if (raw.isEmpty() || targetRingDrain <= 0) return emptyMap()
    val rawSum = raw.values.sum()
    if (rawSum <= 0.0) return emptyMap()
    val scale = targetRingDrain.toDouble() / rawSum
    return raw.mapValues { (_, v) -> (v * scale).roundToInt().coerceIn(0, 100) }
        .filterValues { it > 0 }
}

internal fun accumulateSessionVolume(
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    targetMap: MutableMap<String, AugeVolumeAccumulator>,
) {
    session.allExercises().forEach { exercise ->
        val muscles = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
        if (muscles.isEmpty()) return@forEach
        exercise.validAugeSets().forEach { set ->
            val volumeMultiplier = AugeClassifiers.getEffectiveVolumeMultiplier(set.effectiveTargetRpe())
            VolumeCalculator.buildPerExerciseMuscleContributions(muscles)
                .forEach { (normalized, hyperFactor) ->
                    val bucket = targetMap.getOrPut(normalized) { AugeVolumeAccumulator() }
                    bucket.flat += hyperFactor
                    bucket.effective += hyperFactor * volumeMultiplier
                }
        }
    }
}

internal fun computeAugeStatus(
    currentDrain: PredictedDrain,
    weeklyDrains: List<PredictedDrain>,
    currentSessionId: String,
    weeklySessions: List<Session>,
    settings: Settings,
): SessionEditorAugeStatus {
    val baseSessionsPerWeek = 5
    val weeklyBudget = when (settings.calorieGoalObjective) {
        CalorieGoalObjective.DEFICIT -> 220.0
        CalorieGoalObjective.MAINTENANCE -> 260.0
        CalorieGoalObjective.SURPLUS -> 300.0
    }
    val currentCombined = currentDrain.combinedDrain()
    val otherCombined = weeklyDrains.zip(weeklySessions)
        .filter { (_, session) -> session.id != currentSessionId }
        .sumOf { (drain, _) -> drain.combinedDrain() }
    val otherSessionsCount = weeklySessions.count { it.id != currentSessionId }
    val remainingQuota = (weeklyBudget - otherCombined).coerceAtLeast(0.0)
    val slotsLeft = (baseSessionsPerWeek - otherSessionsCount).coerceAtLeast(1)
    val recommendedPerSession = if (weeklySessions.size <= 1) weeklyBudget / baseSessionsPerWeek else remainingQuota / slotsLeft

    return when {
        currentCombined <= recommendedPerSession * 0.90 -> SessionEditorAugeStatus.OPTIMAL
        currentCombined <= recommendedPerSession * 1.25 -> SessionEditorAugeStatus.WARNING
        else -> SessionEditorAugeStatus.FATIGUING
    }
}

internal fun computeDifficulty(averageRpe: Double, averageRm: Double): Int {
    if (averageRpe <= 0.0 && averageRm <= 0.0) return 0
    return ((averageRpe / 10.0) * 3.0 + (averageRm * 5.0) + 2.0)
        .roundToInt()
        .coerceIn(1, 10)
}

internal fun exerciseSuggestionForInsight(
    muscular: Double,
    cns: Double,
    spinal: Double,
): String? = when {
    spinal > 75.0 -> "Fatiga de columna alta. Bajar intensidad o usar una variante más estable puede ayudarte."
    cns > 85.0 -> "Carga neural elevada. Reducir RPE o %1RM deja más margen para el resto de la sesión."
    muscular > 88.0 -> "Volumen muscular alto. Una serie menos probablemente mantiene el estímulo."
    else -> null
}

internal fun resolveExerciseInfo(
    exercise: Exercise,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
    index = exerciseIndex,
    catalogConfigurationId = exercise.catalogConfigurationId,
    exerciseDbId = exercise.exerciseDbId,
    exerciseId = exercise.exerciseId,
    exerciseName = exercise.name,
)

internal fun resolvePrimaryMuscle(info: ExerciseMuscleInfo): String? {
    val primary = info.involvedMuscles.firstOrNull { it.role == MuscleRole.PRIMARY }
        ?: info.involvedMuscles.firstOrNull()
        ?: return null
    return VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
}

internal fun defaultSessionVolumeLimit(settings: Settings): Int {
    val base = when (settings.calorieGoalObjective) {
        CalorieGoalObjective.DEFICIT -> 8.0
        CalorieGoalObjective.MAINTENANCE -> 9.0
        CalorieGoalObjective.SURPLUS -> 10.0
    }
    val athleteAdjustment = when (settings.athleteType) {
        AthleteType.BODYBUILDER, AthleteType.POWERBUILDER -> 0.8
        AthleteType.POWERLIFTER, AthleteType.WEIGHTLIFTER -> -0.4
        else -> 0.0
    }
    return (base + athleteAdjustment).roundToInt().coerceAtLeast(6)
}

internal fun defaultWeeklyVolumeLimit(settings: Settings): Int = when (settings.calorieGoalObjective) {
    CalorieGoalObjective.DEFICIT -> 20
    CalorieGoalObjective.MAINTENANCE -> 24
    CalorieGoalObjective.SURPLUS -> 28
}

internal fun buildVolumeThresholdsByMuscle(
    sessionVolumeByMuscle: Map<String, Double>,
    weeklyVolumeByMuscle: Map<String, Double>,
    program: Program?,
    settings: Settings,
): Map<String, SessionEditorVolumeThreshold> {
    val personalized = program
        ?.volumeRecommendations
        .orEmpty()
        .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
        .mapValues { (_, grouped) ->
            val mev = grouped.sumOf { it.minEffectiveVolume }.toDouble().coerceAtLeast(1.0)
            val mav = grouped.sumOf { it.maxAdaptiveVolume }.toDouble().coerceAtLeast(mev)
            val mrv = grouped.sumOf { it.maxRecoverableVolume }.toDouble().coerceAtLeast(mav)
            Triple(mev, mav, mrv)
        }

    val involvedMuscles = (sessionVolumeByMuscle.keys + weeklyVolumeByMuscle.keys)
        .filter { it.isNotBlank() }
        .toSet()

    if (involvedMuscles.isEmpty()) return emptyMap()

    val defaultWeeklyMrv = defaultWeeklyVolumeLimit(settings).toDouble().coerceAtLeast(8.0)
    val defaultWeeklyMav = (defaultWeeklyMrv * 0.8).coerceAtLeast(6.0)
    val defaultWeeklyMev = (defaultWeeklyMav * 0.65).coerceAtLeast(4.0)

    return involvedMuscles.associateWith { muscle ->
        val fromProgram = personalized[muscle]
        val weeklyMev = fromProgram?.first ?: defaultWeeklyMev
        val weeklyMav = fromProgram?.second ?: defaultWeeklyMav
        val weeklyMrv = fromProgram?.third ?: defaultWeeklyMrv
        SessionEditorVolumeThreshold(
            sessionMev = (weeklyMev / 3.0).coerceAtLeast(1.0),
            sessionMav = (weeklyMav / 3.0).coerceAtLeast(1.0),
            sessionMrv = (weeklyMrv / 3.0).coerceAtLeast(1.0),
            weeklyMev = weeklyMev,
            weeklyMav = weeklyMav,
            weeklyMrv = weeklyMrv,
        )
    }
}

internal fun aggregateWeeklyDrain(drains: List<PredictedDrain>): PredictedDrain {
    if (drains.isEmpty()) return PredictedDrain(0, 0, 0)

    // Evita saturar a 100 muy pronto: combinación lineal + amortiguación por acumulación.
    val cnsRaw = drains.sumOf { it.cns.toDouble() }
    val muscularRaw = drains.sumOf { it.muscular.toDouble() }
    val spinalRaw = drains.sumOf { it.spinal.toDouble() }

    val dampen = { value: Double ->
        val scaled = value * 0.72
        val nonlinear = value * value * 0.0022
        (scaled - nonlinear).roundToInt().coerceIn(0, 100)
    }

    return PredictedDrain(
        cns = dampen(cnsRaw),
        muscular = dampen(muscularRaw),
        spinal = dampen(spinalRaw),
    )
}

internal fun PredictedDrain.combinedDrain(): Double = (cns + muscular + spinal) / 3.0

internal fun Exercise.validAugeSets(): List<ExerciseSet> = sets.filterNot { it.isIneffective }

internal fun ExerciseSet.effectiveTargetRpe(): Double {
    if (isFailure || intensityMode == IntensityMode.FAILURE) return 10.8
    targetRPE?.let { return it.coerceIn(1.0, 10.0) }
    targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
    return 8.0
}

internal fun ExerciseSet.lowerAugeIntensity(capRpe: Double? = null): ExerciseSet {
    val effectiveRpe = effectiveTargetRpe()
    if (effectiveRpe < 7.0 && capRpe == null) return this

    val targetRpe = maxOf(6.0, (capRpe ?: (effectiveRpe - 0.5)))
    return when (intensityMode ?: IntensityMode.RPE) {
        IntensityMode.SOLO_RM -> copy(
            targetPercentageRM = ((targetPercentageRM ?: 100.0) - 5.0).coerceAtLeast(55.0),
        )
        IntensityMode.RIR -> copy(
            targetRIR = ((targetRIR ?: 2) + 1).coerceAtMost(5),
            isFailure = false,
        )
        IntensityMode.FAILURE -> copy(
            intensityMode = IntensityMode.RIR,
            targetRIR = 1,
            targetRPE = null,
            isFailure = false,
        )
        else -> copy(
            targetRPE = targetRpe.coerceAtMost(capRpe ?: 10.0),
            targetRIR = null,
            isFailure = false,
        )
    }
}

internal data class ClonePayload(
    val parts: List<SessionPart>,
    val looseExercises: List<Exercise>,
    val supersetGroups: List<SupersetGroup> = emptyList(),
)

