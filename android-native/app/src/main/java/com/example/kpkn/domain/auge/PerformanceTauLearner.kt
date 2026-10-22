package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.performance.PerformanceRangeCalculator
import kotlin.math.abs
import kotlin.math.sqrt

data class PerformanceTauManualTouches(
    val energy: Boolean = false,
    val structure: Boolean = false,
    val muscles: Set<String> = emptySet(),
)

data class PerformanceTauInput(
    val historyWithoutToday: List<WorkoutLog>,
    val today: WorkoutLog,
    val nowMs: Long,
    val exerciseDb: Map<String, ExerciseMuscleInfo>,
    val settings: Settings = Settings(),
    val predictedEnergy: Int,
    val predictedStructure: Int,
    val predictedMuscles: Map<String, Int>,
    val manual: PerformanceTauManualTouches = PerformanceTauManualTouches(),
)

data class PerformanceTauDiagnostic(
    val channel: String,
    val skipReason: String? = null,
    val ratio: Double? = null,
    val deltaRpe: Double? = null,
    val predicted: Int? = null,
    val implied: Int? = null,
    val hoursSince: Double? = null,
    val sessionStress: Double? = null,
    val baseline: Double? = null,
    val weight: Double? = null,
    val effectiveSigma: Double? = null,
)

data class PerformanceTauResult(
    val observations: List<AugeLearningObservation>,
    val diagnostics: List<PerformanceTauDiagnostic>,
)

/**
 * Adaptador de aprendizaje de rendimiento hacia el filtro EKF único ([AugeLearner]).
 *
 * Construye observaciones v4 (con fuente, σ efectiva según peso bilateral, id canónico, instante causal,
 * escala y contexto causal). La referencia para e1RM y ΔRPE es la mediana de las últimas 3 sesiones comparables
 * anteriores (excluida la sesión medida). ν < 0 (peor) aporta peso 1; ν > 0 (mejor) peso 0,5 por progresión:
 * R = σ_fuente² / w, σ_efectiva = σ_fuente / √w.
 */
object PerformanceTauLearner {

    const val MIN_SNAPSHOTS = 3
    const val MIN_HOURS = AugeParameters.LEARNER_WINDOW_MIN_HOURS
    const val MAX_HOURS = AugeParameters.LEARNER_WINDOW_MAX_HOURS
    const val SIGMA_BASE_E1RM = AugeParameters.LEARNER_SIGMA_E1RM
    const val SIGMA_BASE_DELTA_RPE = AugeParameters.LEARNER_SIGMA_DELTA_RPE
    const val POSITIVE_WEIGHT = 0.5
    const val NEGATIVE_WEIGHT = 1.0
    const val RPE_COMPARABLE_SLACK = 1.0
    const val WEIGHT_MATCH_FRACTION = 0.025
    const val REPS_MATCH_DELTA = 2
    const val CNC_MATCHED = 3.5
    const val CNC_FALLBACK = 4.0
    const val AXIAL_MIN = 0.6
    const val CHANNEL_ENERGY = "cns"
    const val CHANNEL_STRUCTURE = "spinal"

    fun sessionHasAxialStimulus(
        exercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Boolean = exercises.any { ex ->
        if (ex.cardioDetails != null) return@any false
        (dbInfo(exercise = ex, exerciseDb = exerciseDb)?.axialLoadFactor ?: 0.0) >= AXIAL_MIN
    }

    fun hasReportedDiscomfort(log: WorkoutLog): Boolean {
        val reports = log.postExerciseReports.flatMap { it.discomfortIds }
        val discomforts = log.discomforts
        val stillPresent = log.stillPresentDiscomfortIds
        return (reports + discomforts + stillPresent).any { id ->
            val trimmed = id.trim()
            trimmed.isNotEmpty() && !trimmed.equals("none", ignoreCase = true)
        }
    }

    fun observations(input: PerformanceTauInput): PerformanceTauResult {
        val diagnostics = mutableListOf<PerformanceTauDiagnostic>()
        val observations = mutableListOf<AugeLearningObservation>()

        // 1. Exclusiones previas de la sesión medida (contexto congelado, descarga, molestia)
        val context = input.today.performanceContext
        if (context == null) {
            collectPreExcludedDiagnostics(input, "no_context", diagnostics)
            return PerformanceTauResult(observations = emptyList(), diagnostics = diagnostics)
        }
        if (context.isDeload == true) {
            collectPreExcludedDiagnostics(input, "deload", diagnostics)
            return PerformanceTauResult(observations = emptyList(), diagnostics = diagnostics)
        }
        if (hasReportedDiscomfort(input.today)) {
            collectPreExcludedDiagnostics(input, "discomfort", diagnostics)
            return PerformanceTauResult(observations = emptyList(), diagnostics = diagnostics)
        }

        // 2. Instante causal de la observación
        val observationAtMs = context.sessionStartTimeMs ?: AugeUtils.logDateMs(input.today)

        // 3. Historial causal: anterior al inicio de la sesión medida, desempate estable por id ASC
        val causalHistory = input.historyWithoutToday.filter { log ->
            log.id != input.today.id &&
                AugeUtils.logDateMs(log) > 0L &&
                AugeUtils.logDateMs(log) < observationAtMs
        }

        // 4. Historial elegible para referencias de 3 sesiones: sin molestias y sin descarga explícita conocida
        val eligibleReferenceHistory = causalHistory
            .filter { log ->
                !hasReportedDiscomfort(log) &&
                    log.performanceContext?.isDeload != true
            }
            .sortedWith(compareBy<WorkoutLog>({ AugeUtils.logDateMs(it) }, { it.id }))

        // 5. Colección de observaciones por canal
        collectMuscleObservations(input, causalHistory, eligibleReferenceHistory, observationAtMs, observations, diagnostics)
        collectEnergyObservation(input, causalHistory, eligibleReferenceHistory, observationAtMs, observations, diagnostics)
        collectStructureObservation(input, causalHistory, eligibleReferenceHistory, observationAtMs, observations, diagnostics)

        return PerformanceTauResult(observations = observations, diagnostics = diagnostics)
    }

    /**
     * Aplica las observaciones resultantes al caché mediante [AugeLearner.update] y sella [finishedLogId].
     */
    fun applyObservations(
        cache: AugeAdaptiveCache,
        observations: List<AugeLearningObservation>,
        model: AugeObservationModel,
        finishedLogId: String,
    ): AugeAdaptiveCache {
        var current = cache
        for (obs in observations) {
            val result = AugeLearner.update(current, obs, model)
            current = result.cache
        }
        return current.copy(lastPerformanceLearnLogId = finishedLogId)
    }

    /**
     * Puente compatible hacia el EKF v4. Si no se provee un [model], utiliza un evaluador seguro para pruebas aisladas.
     */
    fun applyToCache(
        cache: AugeAdaptiveCache,
        result: PerformanceTauResult,
        finishedLogId: String,
        nowMs: Long,
        model: AugeObservationModel? = null,
    ): AugeAdaptiveCache {
        val actualModel = model ?: AugeObservationModel { _, _ -> AugePrediction(battery = 70.0) }
        return applyObservations(cache, result.observations, actualModel, finishedLogId)
    }

    fun primaryAugePillarFor(
        exercise: CompletedExercise,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): String? {
        val involved = involvedMuscles(exercise, exerciseDb)
        val primary = involved.find { it.role == MuscleRole.PRIMARY } ?: involved.firstOrNull() ?: return null
        return getAugeMusclePillarId(primary.muscle, primary.emphasis)
    }

    fun canonicalExerciseId(exercise: CompletedExercise): String? =
        exercise.canonicalExerciseId?.takeIf { it.isNotBlank() }
            ?: exercise.exerciseDbId?.takeIf { it.isNotBlank() }

    fun sessionErm(exercise: CompletedExercise): Double? {
        val sets = workingSets(exercise).filter { it.weight > 0.0 && it.reps > 0 }
        if (sets.isEmpty()) return null
        if (sets.all { it.side != null }) {
            val sides = sets.mapNotNull { it.side?.lowercase() }.toSet()
            if (!sides.containsAll(listOf("left", "right"))) return null
            return sets.groupBy { it.side!!.lowercase() }
                .values
                .maxOf { sideSets -> sideSets.maxOf { calculateHybrid1RM(it.weight, it.reps) } }
        }
        return sets.maxOf { calculateHybrid1RM(it.weight, it.reps) }
    }

    private fun collectPreExcludedDiagnostics(
        input: PerformanceTauInput,
        reason: String,
        diagnostics: MutableList<PerformanceTauDiagnostic>,
    ) {
        val pillars = input.today.completedExercises
            .filter { it.cardioDetails == null }
            .mapNotNull { primaryAugePillarFor(it, input.exerciseDb) }
            .distinct()
        for (pillar in pillars) {
            diagnostics += PerformanceTauDiagnostic(channel = pillar, skipReason = reason)
        }
        diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_ENERGY, skipReason = reason)
        if (sessionHasAxialStimulus(input.today.completedExercises, input.exerciseDb)) {
            diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_STRUCTURE, skipReason = reason)
        }
    }

    private fun collectMuscleObservations(
        input: PerformanceTauInput,
        causalHistory: List<WorkoutLog>,
        eligibleReferenceHistory: List<WorkoutLog>,
        observationAtMs: Long,
        out: MutableList<AugeLearningObservation>,
        diagnostics: MutableList<PerformanceTauDiagnostic>,
    ) {
        val bestByPillar = linkedMapOf<String, MuscleCandidate>()
        for (exercise in input.today.completedExercises) {
            if (exercise.cardioDetails != null) continue
            val pillar = primaryAugePillarFor(exercise, input.exerciseDb) ?: continue
            val contribution = primaryContribution(exercise, input.exerciseDb)
            val existing = bestByPillar[pillar]
            if (existing == null || contribution > existing.contribution) {
                bestByPillar[pillar] = MuscleCandidate(pillar, exercise, contribution)
            }
        }
        for ((pillar, candidate) in bestByPillar) {
            if (manualMuscleTouched(input.manual.muscles, pillar)) {
                diagnostics += PerformanceTauDiagnostic(channel = pillar, skipReason = "manual_touch")
                continue
            }
            val previous = previousMuscleStimulus(causalHistory, pillar, input.exerciseDb, input.settings)
            val (diag, obs) = buildErmObservation(
                today = input.today,
                exercise = candidate.exercise,
                channel = pillar,
                predicted = lookupPredictedMuscle(input.predictedMuscles, pillar),
                previous = previous,
                eligibleReferenceHistory = eligibleReferenceHistory,
                observationAtMs = observationAtMs,
            )
            diagnostics += diag
            if (obs != null) out += obs
        }
    }

    private fun collectEnergyObservation(
        input: PerformanceTauInput,
        causalHistory: List<WorkoutLog>,
        eligibleReferenceHistory: List<WorkoutLog>,
        observationAtMs: Long,
        out: MutableList<AugeLearningObservation>,
        diagnostics: MutableList<PerformanceTauDiagnostic>,
    ) {
        if (input.manual.energy) {
            diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_ENERGY, skipReason = "manual_touch")
            return
        }
        val previous = previousSystemStimulus(causalHistory, input.exerciseDb, input.settings, spinal = false)

        // Prioridad determinista a ΔRPE cuando sea válido
        val matched = matchedRpeDelta(input, eligibleReferenceHistory)
        if (matched != null) {
            val implied = PerformanceImpliedBattery.impliedEnergyFromRpeDelta(input.predictedEnergy, matched)
            val (diag, obs) = finalizeObservation(
                channel = CHANNEL_ENERGY,
                predicted = input.predictedEnergy,
                implied = implied,
                previous = previous,
                observationAtMs = observationAtMs,
                ratio = null,
                deltaRpe = matched,
                source = "delta_rpe",
                sigmaBase = SIGMA_BASE_DELTA_RPE,
            )
            diagnostics += diag
            if (obs != null) out += obs
            return
        }

        // Fallback e1RM para Energía con ejercicio de alto CNC
        val fallback = input.today.completedExercises
            .filter { (dbInfo(it, input.exerciseDb)?.cnc ?: 0.0) >= CNC_FALLBACK }
            .maxByOrNull { primaryContribution(it, input.exerciseDb) }
        if (fallback == null) {
            diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_ENERGY, skipReason = "no_matched_rpe_or_cnc")
            return
        }

        val (diag, obs) = buildErmObservation(
            today = input.today,
            exercise = fallback,
            channel = CHANNEL_ENERGY,
            predicted = input.predictedEnergy,
            previous = previous,
            eligibleReferenceHistory = eligibleReferenceHistory,
            observationAtMs = observationAtMs,
        )
        diagnostics += diag
        if (obs != null) out += obs
    }

    private fun collectStructureObservation(
        input: PerformanceTauInput,
        causalHistory: List<WorkoutLog>,
        eligibleReferenceHistory: List<WorkoutLog>,
        observationAtMs: Long,
        out: MutableList<AugeLearningObservation>,
        diagnostics: MutableList<PerformanceTauDiagnostic>,
    ) {
        if (input.manual.structure) {
            diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_STRUCTURE, skipReason = "manual_touch")
            return
        }
        val axial = input.today.completedExercises
            .filter { (dbInfo(it, input.exerciseDb)?.axialLoadFactor ?: 0.0) >= AXIAL_MIN }
            .maxByOrNull { primaryContribution(it, input.exerciseDb) }
        if (axial == null) {
            diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_STRUCTURE, skipReason = "no_axial_exercise")
            return
        }
        val previous = previousSystemStimulus(causalHistory, input.exerciseDb, input.settings, spinal = true)
        val (diag, obs) = buildErmObservation(
            today = input.today,
            exercise = axial,
            channel = CHANNEL_STRUCTURE,
            predicted = input.predictedStructure,
            previous = previous,
            eligibleReferenceHistory = eligibleReferenceHistory,
            observationAtMs = observationAtMs,
        )
        diagnostics += diag
        if (obs != null) out += obs
    }

    private fun buildErmObservation(
        today: WorkoutLog,
        exercise: CompletedExercise,
        channel: String,
        predicted: Int?,
        previous: PreviousStimulus?,
        eligibleReferenceHistory: List<WorkoutLog>,
        observationAtMs: Long,
    ): Pair<PerformanceTauDiagnostic, AugeLearningObservation?> {
        if (predicted == null) {
            return PerformanceTauDiagnostic(channel = channel, skipReason = "no_predicted_battery") to null
        }
        if (isTechnicallyInvalid(today, exercise)) {
            return PerformanceTauDiagnostic(channel = channel, skipReason = "technical_invalid") to null
        }
        val todayErm = sessionErm(exercise) ?: return PerformanceTauDiagnostic(
            channel = channel,
            skipReason = "no_today_erm",
        ) to null

        // Mediana de las últimas 3 sesiones comparables ANTERIORES ordenadas ASC
        val sessionErms = mutableListOf<Double>()
        for (log in eligibleReferenceHistory) {
            val match = matchingExercises(exercise, log).firstOrNull() ?: continue
            if (isTechnicallyInvalid(log, match)) continue
            if (!effortComparable(exercise, match)) continue
            val erm = sessionErm(match) ?: continue
            sessionErms += erm
        }

        if (sessionErms.size < MIN_SNAPSHOTS) {
            return PerformanceTauDiagnostic(channel = channel, skipReason = "insufficient_snapshots") to null
        }

        val lastThree = sessionErms.takeLast(MIN_SNAPSHOTS)
        val baseline = median(lastThree)
            ?: return PerformanceTauDiagnostic(channel = channel, skipReason = "insufficient_snapshots") to null

        val ratio = todayErm / baseline
        val implied = PerformanceImpliedBattery.impliedFromErmRatio(ratio)

        return finalizeObservation(
            channel = channel,
            predicted = predicted,
            implied = implied,
            previous = previous,
            observationAtMs = observationAtMs,
            ratio = ratio,
            deltaRpe = null,
            source = "e1rm",
            sigmaBase = SIGMA_BASE_E1RM,
            baseline = baseline,
        )
    }

    private fun finalizeObservation(
        channel: String,
        predicted: Int,
        implied: Int,
        previous: PreviousStimulus?,
        observationAtMs: Long,
        ratio: Double?,
        deltaRpe: Double?,
        source: String,
        sigmaBase: Double,
        baseline: Double? = null,
    ): Pair<PerformanceTauDiagnostic, AugeLearningObservation?> {
        val diff = implied - predicted
        // Bilateralidad: ν < 0 (peor) peso 1; ν > 0 (mejor) peso 0,5 por progresión
        val weight = if (diff > 0) POSITIVE_WEIGHT else NEGATIVE_WEIGHT
        val effectiveSigma = sigmaBase / sqrt(weight)

        if (previous == null || previous.stress <= 0.0) {
            return PerformanceTauDiagnostic(
                channel = channel,
                skipReason = "no_previous_stimulus",
                ratio = ratio,
                deltaRpe = deltaRpe,
                predicted = predicted,
                implied = implied,
                baseline = baseline,
                weight = weight,
                effectiveSigma = effectiveSigma,
            ) to null
        }

        val hours = (observationAtMs - previous.logMs) / 3_600_000.0
        if (hours < MIN_HOURS) {
            return PerformanceTauDiagnostic(
                channel = channel,
                skipReason = "hours_below_min",
                ratio = ratio,
                deltaRpe = deltaRpe,
                predicted = predicted,
                implied = implied,
                hoursSince = hours,
                sessionStress = previous.stress,
                baseline = baseline,
                weight = weight,
                effectiveSigma = effectiveSigma,
            ) to null
        }
        if (hours > MAX_HOURS) {
            return PerformanceTauDiagnostic(
                channel = channel,
                skipReason = "hours_above_max",
                ratio = ratio,
                deltaRpe = deltaRpe,
                predicted = predicted,
                implied = implied,
                hoursSince = hours,
                sessionStress = previous.stress,
                baseline = baseline,
                weight = weight,
                effectiveSigma = effectiveSigma,
            ) to null
        }

        val channelKey = if (channel == CHANNEL_ENERGY || channel == CHANNEL_STRUCTURE) channel
            else getAugeMusclePillarId(channel).lowercase()

        val diagnostic = PerformanceTauDiagnostic(
            channel = channel,
            skipReason = null,
            ratio = ratio,
            deltaRpe = deltaRpe,
            predicted = predicted,
            implied = implied,
            hoursSince = hours,
            sessionStress = previous.stress,
            baseline = baseline,
            weight = weight,
            effectiveSigma = effectiveSigma,
        )

        val observation = AugeLearningObservation(
            parameterKey = channelKey,
            y = implied.toDouble(),
            sigma = effectiveSigma,
            source = source,
            anchorMs = observationAtMs,
            observedAtMs = observationAtMs,
            relevantLogId = previous.logId,
            hoursSinceRelevantLoad = hours,
        )

        return diagnostic to observation
    }

    private fun matchedRpeDelta(
        input: PerformanceTauInput,
        eligibleReferenceHistory: List<WorkoutLog>,
    ): Double? {
        val deltas = mutableListOf<Double>()
        for (exercise in input.today.completedExercises) {
            val info = dbInfo(exercise, input.exerciseDb)
            val cnc = info?.cnc ?: 0.0
            val multiPillar = involvedMuscles(exercise, input.exerciseDb).size > 1
            if (cnc < CNC_MATCHED && !multiPillar) continue

            val todaySets = workingSets(exercise).filter { it.rpe != null && it.rpe > 0.0 }
            if (todaySets.isEmpty()) continue

            val matchingSessions = eligibleReferenceHistory.mapNotNull { log ->
                val matches = matchingExercises(exercise, log)
                if (matches.isEmpty()) null else matches.flatMap { workingSets(it) }
            }

            for (todaySet in todaySets) {
                val todayRpe = todaySet.rpe ?: continue
                val sessionRpes = mutableListOf<Double>()
                for (histSets in matchingSessions) {
                    val matchingHistSets = histSets.filter { histSet ->
                        histSet.rpe != null && histSet.rpe > 0.0 &&
                            weightsMatch(todaySet.weight, histSet.weight) &&
                            abs(todaySet.reps - histSet.reps) <= REPS_MATCH_DELTA
                    }
                    val histSetRpes = matchingHistSets.mapNotNull { it.rpe }
                    val sessionRpeMedian = median(histSetRpes)
                    if (sessionRpeMedian != null) {
                        sessionRpes += sessionRpeMedian
                    }
                }

                if (sessionRpes.size >= MIN_SNAPSHOTS) {
                    val lastThree = sessionRpes.takeLast(MIN_SNAPSHOTS)
                    val histBaselineRpe = median(lastThree)
                    if (histBaselineRpe != null) {
                        deltas += (todayRpe - histBaselineRpe)
                    }
                }
            }
        }
        return median(deltas)
    }

    private fun effortComparable(
        todayExercise: CompletedExercise,
        refExercise: CompletedExercise,
    ): Boolean {
        val todayRpes = workingSets(todayExercise).mapNotNull { it.rpe?.takeIf { rpe -> rpe > 0.0 } }
        val refRpes = workingSets(refExercise).mapNotNull { it.rpe?.takeIf { rpe -> rpe > 0.0 } }
        if (todayRpes.isEmpty() || refRpes.isEmpty()) return true
        val todayMedian = median(todayRpes) ?: return true
        val refMedian = median(refRpes) ?: return true
        return todayMedian >= refMedian - RPE_COMPARABLE_SLACK
    }

    private fun previousMuscleStimulus(
        history: List<WorkoutLog>,
        pillar: String,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
    ): PreviousStimulus? {
        return history
            .mapNotNull { log ->
                val ms = AugeUtils.logDateMs(log)
                if (ms <= 0L) return@mapNotNull null
                val stress = muscleStress(log, pillar, exerciseDb, settings)
                if (stress <= 0.0) return@mapNotNull null
                PreviousStimulus(log.id, ms, stress)
            }
            .maxByOrNull { it.logMs }
    }

    private fun previousSystemStimulus(
        history: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
        spinal: Boolean,
    ): PreviousStimulus? {
        return history
            .mapNotNull { log ->
                val ms = AugeUtils.logDateMs(log)
                if (ms <= 0L) return@mapNotNull null
                val drain = runCatching {
                    AugeFatigueEngine.calculateCompletedSessionDrain(
                        completedExercises = log.completedExercises,
                        exerciseDb = exerciseDb,
                        settings = settings,
                        durationMinutes = log.durationMinutes,
                    )
                }.getOrNull() ?: return@mapNotNull null
                val stress = if (spinal) drain.spinal.toDouble() else drain.cns.toDouble()
                if (stress <= 0.0) return@mapNotNull null
                PreviousStimulus(log.id, ms, stress)
            }
            .maxByOrNull { it.logMs }
    }

    private fun muscleStress(
        log: WorkoutLog,
        pillar: String,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
    ): Double {
        val impact = log.muscularImpactV2?.perMuscle?.entries?.firstOrNull {
            getAugeMusclePillarId(it.key) == pillar
        }?.value
        val stored = impact?.immediateDrainPct?.takeIf { it > 0.0 }
            ?: impact?.stressUnits?.takeIf { it > 0.0 }
        if (stored != null) return stored
        return AugeRecoveryEngine.calculateMuscleSessionStress(
            muscleName = pillar,
            log = log,
            settings = settings,
            exerciseDb = exerciseDb,
            adaptiveCache = AugeAdaptiveCache(),
        )
    }

    private fun workingSets(exercise: CompletedExercise): List<CompletedSet> =
        exercise.sets.filter { AugeFatigueEngine.isSetEffective(it) }

    fun matchingExercises(
        todayExercise: CompletedExercise,
        log: WorkoutLog,
    ): List<CompletedExercise> {
        val id = canonicalExerciseId(todayExercise) ?: return emptyList()
        val aspects = todayExercise.selectedAspects.orEmpty()
        return log.completedExercises.filter { candidate ->
            canonicalExerciseId(candidate) == id && candidate.selectedAspects.orEmpty() == aspects
        }
    }

    private fun involvedMuscles(
        exercise: CompletedExercise,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ) = AugeFatigueEngine.resolveInvolvedMuscles(
        exerciseName = exercise.exerciseName,
        snapshot = exercise.effectiveMuscles,
        dbInfo = dbInfo(exercise, exerciseDb),
    )

    private fun primaryContribution(
        exercise: CompletedExercise,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Double {
        val involved = involvedMuscles(exercise, exerciseDb)
        val primary = involved.find { it.role == MuscleRole.PRIMARY } ?: involved.firstOrNull() ?: return 0.0
        return resolveMuscleVolumeContribution(primary)
    }

    private fun dbInfo(
        exercise: CompletedExercise,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val keys = listOfNotNull(
            exercise.catalogConfigurationId,
            exercise.canonicalExerciseId,
            exercise.exerciseDbId,
            exercise.exerciseId,
        ).map { it.trim() }.filter { it.isNotBlank() }
        for (key in keys) {
            exerciseDb[key]?.let { return it }
            exerciseDb[key.lowercase()]?.let { return it }
        }
        return exerciseDb.values.firstOrNull { info ->
            info.name.equals(exercise.exerciseName, ignoreCase = true)
        }
    }

    private fun isTechnicallyInvalid(log: WorkoutLog, exercise: CompletedExercise): Boolean {
        val id = canonicalExerciseId(exercise)
        val report = log.postExerciseReports.firstOrNull { report ->
            report.exerciseId == exercise.exerciseId ||
                (id != null && (report.canonicalExerciseId == id || report.exerciseDbId == id))
        }
        return (report?.technicalQuality ?: 10) <= 2
    }

    private fun weightsMatch(a: Double, b: Double): Boolean {
        val max = maxOf(a, b, 0.01)
        return abs(a - b) / max <= WEIGHT_MATCH_FRACTION
    }

    private fun lookupPredictedMuscle(predicted: Map<String, Int>, pillar: String): Int? =
        lookupMuscleScore(predicted, pillar)

    private fun manualMuscleTouched(touched: Set<String>, pillar: String): Boolean =
        touched.any { getAugeMusclePillarId(it) == pillar }

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private data class MuscleCandidate(
        val pillar: String,
        val exercise: CompletedExercise,
        val contribution: Double,
    )

    private data class PreviousStimulus(
        val logId: String,
        val logMs: Long,
        val stress: Double,
    )
}
