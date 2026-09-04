package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryLearningObservation
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.performance.PerformanceRangeCalculator
import kotlin.math.abs

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
)

data class PerformanceTauResult(
    val observations: List<RecoveryLearningObservation>,
    val diagnostics: List<PerformanceTauDiagnostic>,
)

/**
 * Builds τ observations from today's performance vs the ring predicted at
 * session start. Does not update drain multipliers.
 */
object PerformanceTauLearner {

    const val MIN_SNAPSHOTS = 3
    const val NOISE_THRESHOLD = 8
    const val MIN_HOURS = AugeAdaptiveEngine.MIN_HOURS_FOR_TAU_LEARNING
    const val MAX_HOURS = 14.0 * 24.0
    const val RPE_COMPARABLE_SLACK = 1.0
    const val WEIGHT_MATCH_FRACTION = 0.025
    const val REPS_MATCH_DELTA = 2
    const val CNC_MATCHED = 3.5
    const val CNC_FALLBACK = 4.0
    const val AXIAL_MIN = 0.6
    const val CHANNEL_ENERGY = "cns"
    const val CHANNEL_STRUCTURE = "spinal"

    fun observations(input: PerformanceTauInput): PerformanceTauResult {
        val diagnostics = mutableListOf<PerformanceTauDiagnostic>()
        val observations = mutableListOf<RecoveryLearningObservation>()

        collectMuscleObservations(input, observations, diagnostics)
        collectEnergyObservation(input, observations, diagnostics)
        collectStructureObservation(input, observations, diagnostics)

        return PerformanceTauResult(observations = observations, diagnostics = diagnostics)
    }

    fun applyToCache(
        cache: AugeAdaptiveCache,
        result: PerformanceTauResult,
        finishedLogId: String,
        nowMs: Long,
    ): AugeAdaptiveCache {
        if (result.observations.isEmpty()) {
            return cache.copy(lastPerformanceLearnLogId = finishedLogId)
        }
        var next = cache
        val total = cache.totalObservations
        for (obs in result.observations) {
            when (obs.muscle.lowercase().trim()) {
                CHANNEL_ENERGY -> {
                    val (cns, spinal) = AugeAdaptiveEngine.updateSystemRecoveryHours(
                        currentCnsTau = next.cnsRecoveryHours,
                        currentSpinalTau = next.spinalRecoveryHours,
                        cnsObservation = obs,
                        spinalObservation = null,
                        totalObservations = total,
                    )
                    next = next.copy(cnsRecoveryHours = cns, spinalRecoveryHours = spinal)
                }
                CHANNEL_STRUCTURE -> {
                    val (cns, spinal) = AugeAdaptiveEngine.updateSystemRecoveryHours(
                        currentCnsTau = next.cnsRecoveryHours,
                        currentSpinalTau = next.spinalRecoveryHours,
                        cnsObservation = null,
                        spinalObservation = obs,
                        totalObservations = total,
                    )
                    next = next.copy(cnsRecoveryHours = cns, spinalRecoveryHours = spinal)
                }
                else -> {
                    next = next.copy(
                        personalizedRecoveryHours = AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
                            current = next.personalizedRecoveryHours,
                            observation = obs,
                            totalObservations = total,
                        ),
                    )
                }
            }
        }
        return next.copy(
            totalObservations = total + 1,
            lastUpdatedMs = nowMs,
            lastPerformanceLearnLogId = finishedLogId,
        )
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

    private fun collectMuscleObservations(
        input: PerformanceTauInput,
        out: MutableList<RecoveryLearningObservation>,
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
            val built = buildErmObservation(
                input = input,
                exercise = candidate.exercise,
                channel = pillar,
                predicted = lookupPredictedMuscle(input.predictedMuscles, pillar),
                previous = previousMuscleStimulus(input.historyWithoutToday, pillar, input.exerciseDb, input.settings),
            )
            appendBuilt(built, out, diagnostics)
        }
    }

    private fun collectEnergyObservation(
        input: PerformanceTauInput,
        out: MutableList<RecoveryLearningObservation>,
        diagnostics: MutableList<PerformanceTauDiagnostic>,
    ) {
        if (input.manual.energy) {
            diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_ENERGY, skipReason = "manual_touch")
            return
        }
        val previous = previousSystemStimulus(input.historyWithoutToday, input.exerciseDb, input.settings, spinal = false)
        val matched = matchedRpeDelta(input)
        if (matched != null) {
            val implied = PerformanceImpliedBattery.impliedEnergyFromRpeDelta(input.predictedEnergy, matched)
            appendBuilt(
                finalizeObservation(
                    channel = CHANNEL_ENERGY,
                    predicted = input.predictedEnergy,
                    implied = implied,
                    previous = previous,
                    nowMs = input.nowMs,
                    ratio = null,
                    deltaRpe = matched,
                ),
                out,
                diagnostics,
            )
            return
        }
        val fallback = input.today.completedExercises
            .filter { (dbInfo(it, input.exerciseDb)?.cnc ?: 0.0) >= CNC_FALLBACK }
            .maxByOrNull { primaryContribution(it, input.exerciseDb) }
        if (fallback == null) {
            diagnostics += PerformanceTauDiagnostic(channel = CHANNEL_ENERGY, skipReason = "no_matched_rpe_or_cnc")
            return
        }
        appendBuilt(
            buildErmObservation(
                input = input,
                exercise = fallback,
                channel = CHANNEL_ENERGY,
                predicted = input.predictedEnergy,
                previous = previous,
            ),
            out,
            diagnostics,
        )
    }

    private fun collectStructureObservation(
        input: PerformanceTauInput,
        out: MutableList<RecoveryLearningObservation>,
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
        val previous = previousSystemStimulus(input.historyWithoutToday, input.exerciseDb, input.settings, spinal = true)
        appendBuilt(
            buildErmObservation(
                input = input,
                exercise = axial,
                channel = CHANNEL_STRUCTURE,
                predicted = input.predictedStructure,
                previous = previous,
            ),
            out,
            diagnostics,
        )
    }

    private fun buildErmObservation(
        input: PerformanceTauInput,
        exercise: CompletedExercise,
        channel: String,
        predicted: Int?,
        previous: PreviousStimulus?,
    ): PerformanceTauDiagnostic {
        if (predicted == null) {
            return PerformanceTauDiagnostic(channel = channel, skipReason = "no_predicted_battery")
        }
        if (isTechnicallyInvalid(input.today, exercise)) {
            return PerformanceTauDiagnostic(channel = channel, skipReason = "technical_invalid")
        }
        if (!effortComparable(exercise, input.historyWithoutToday, input.exerciseDb)) {
            return PerformanceTauDiagnostic(channel = channel, skipReason = "rpe_not_comparable")
        }
        val todayErm = sessionErm(exercise) ?: return PerformanceTauDiagnostic(
            channel = channel,
            skipReason = "no_today_erm",
        )
        val baseline = historicalErmBaseline(exercise, input.historyWithoutToday)
            ?: return PerformanceTauDiagnostic(channel = channel, skipReason = "insufficient_snapshots")
        val ratio = todayErm / baseline
        val implied = PerformanceImpliedBattery.impliedFromErmRatio(ratio)
        return finalizeObservation(
            channel = channel,
            predicted = predicted,
            implied = implied,
            previous = previous,
            nowMs = input.nowMs,
            ratio = ratio,
            deltaRpe = null,
        )
    }

    private fun finalizeObservation(
        channel: String,
        predicted: Int,
        implied: Int,
        previous: PreviousStimulus?,
        nowMs: Long,
        ratio: Double?,
        deltaRpe: Double?,
    ): PerformanceTauDiagnostic {
        if (abs(implied - predicted) < NOISE_THRESHOLD) {
            return PerformanceTauDiagnostic(
                channel = channel,
                skipReason = "below_noise_threshold",
                ratio = ratio,
                deltaRpe = deltaRpe,
                predicted = predicted,
                implied = implied,
            )
        }
        if (previous == null || previous.stress <= 0.0) {
            return PerformanceTauDiagnostic(
                channel = channel,
                skipReason = "no_previous_stimulus",
                ratio = ratio,
                deltaRpe = deltaRpe,
                predicted = predicted,
                implied = implied,
            )
        }
        val hours = (nowMs - previous.logMs) / 3_600_000.0
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
            )
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
            )
        }
        return PerformanceTauDiagnostic(
            channel = channel,
            skipReason = null,
            ratio = ratio,
            deltaRpe = deltaRpe,
            predicted = predicted,
            implied = implied,
            hoursSince = hours,
            sessionStress = previous.stress,
        )
    }

    private fun appendBuilt(
        diagnostic: PerformanceTauDiagnostic,
        out: MutableList<RecoveryLearningObservation>,
        diagnostics: MutableList<PerformanceTauDiagnostic>,
    ) {
        diagnostics += diagnostic
        val implied = diagnostic.implied
        val predicted = diagnostic.predicted
        val hours = diagnostic.hoursSince
        val stress = diagnostic.sessionStress
        if (diagnostic.skipReason != null || implied == null || predicted == null || hours == null || stress == null) {
            return
        }
        out += RecoveryLearningObservation(
            muscle = diagnostic.channel,
            predictedBattery = predicted,
            actualBattery = implied,
            sessionStress = stress,
            hoursSinceSession = hours,
        )
    }

    private fun matchedRpeDelta(input: PerformanceTauInput): Double? {
        val deltas = mutableListOf<Double>()
        for (exercise in input.today.completedExercises) {
            val info = dbInfo(exercise, input.exerciseDb)
            val cnc = info?.cnc ?: 0.0
            val multiPillar = involvedMuscles(exercise, input.exerciseDb).size > 1
            if (cnc < CNC_MATCHED && !multiPillar) continue
            val todaySets = workingSets(exercise).filter { it.rpe != null || it.weight > 0 }
            val histSets = historicalWorkingSets(exercise, input.historyWithoutToday)
            for (todaySet in todaySets) {
                val todayRpe = effectiveRpe(todaySet) ?: continue
                val matches = histSets.filter { historic ->
                    weightsMatch(todaySet.weight, historic.weight) &&
                        abs(todaySet.reps - historic.reps) <= REPS_MATCH_DELTA
                }
                val histRpes = matches.mapNotNull { effectiveRpe(it) }
                val histMedian = median(histRpes) ?: continue
                deltas += todayRpe - histMedian
            }
        }
        return median(deltas)
    }

    private fun effortComparable(
        exercise: CompletedExercise,
        history: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): Boolean {
        val todayRpe = median(workingSets(exercise).mapNotNull { effectiveRpe(it) }) ?: return false
        val histRpe = median(historicalWorkingSets(exercise, history).mapNotNull { effectiveRpe(it) }) ?: return false
        return todayRpe >= histRpe - RPE_COMPARABLE_SLACK
    }

    private fun historicalErmBaseline(
        exercise: CompletedExercise,
        history: List<WorkoutLog>,
    ): Double? {
        val erMs = history.mapNotNull { log ->
            matchingExercises(exercise, log).mapNotNull { sessionErm(it) }.maxOrNull()
        }
        if (erMs.size < MIN_SNAPSHOTS) return null
        return PerformanceRangeCalculator.computeRange(
            snapshots = erMs,
            currentErm = erMs.last(),
        ).ermRms.takeIf { it > 0.0 }
    }

    private fun sessionErm(exercise: CompletedExercise): Double? {
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
                PreviousStimulus(ms, stress)
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
                    )
                }.getOrNull() ?: return@mapNotNull null
                val stress = if (spinal) drain.spinal.toDouble() else drain.cns.toDouble()
                if (stress <= 0.0) return@mapNotNull null
                PreviousStimulus(ms, stress)
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

    private fun historicalWorkingSets(
        exercise: CompletedExercise,
        history: List<WorkoutLog>,
    ): List<CompletedSet> = history.flatMap { log ->
        matchingExercises(exercise, log).flatMap { workingSets(it) }
    }

    private fun matchingExercises(
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

    private fun effectiveRpe(set: CompletedSet): Double? {
        val explicit = set.rpe
        if (explicit != null && explicit > 0.0) return explicit
        val inferred = AugeFatigueEngine.getEffectiveRPE(set)
        return inferred.takeIf { it > 0.0 }
    }

    private fun lookupPredictedMuscle(predicted: Map<String, Int>, pillar: String): Int? =
        lookupMuscleScore(predicted, pillar)

    private fun manualMuscleTouched(touched: Set<String>, pillar: String): Boolean =
        touched.any { getAugeMusclePillarId(it) == pillar }

    private fun median(values: List<Double>): Double? {
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
        val logMs: Long,
        val stress: Double,
    )
}
