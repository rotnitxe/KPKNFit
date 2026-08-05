package com.example.kpkn.screens.workout

import android.content.Context
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleAdvance
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.OmittedExercise
import com.example.kpkn.data.models.PendingQuestionnaire
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.exercises.catalogv2.toResolvedCatalogSnapshotJson
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.models.effectiveRepEquivalent
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Owns workout finish flow: build log, persist, performance snapshots, volume-advance gate.
 */
class WorkoutFinishController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val repository: ProgramRepository,
    private val programId: String,
    private val sessionId: String,
    private val exerciseIndex: () -> Map<String, ExerciseMuscleInfo>,
    private val performanceRangeStore: PerformanceRangeStore,
    private val restAlertManager: WorkoutRestAlertManager,
    private val restTimer: RestTimerController,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val sessionForActiveMode: (Session, WeekVariant) -> Session,
    private val canonicalExerciseKey: (Exercise) -> String,
    private val catalogInfoForCompletedExercise: (CompletedExercise) -> ExerciseMuscleInfo?,
    private val updatePredictionBias: (SessionClosingFeedback) -> Unit,
    private val deferOnComplete: (() -> Unit) -> Unit,
    private val prepareVoiceDiagnosticExport: () -> Unit,
) {
    fun finish(
        notes: String,
        fatigueLevel: Int,
        closingFeedback: SessionClosingFeedback,
        onPendingQuestionnaire: ((PendingQuestionnaire) -> Unit)? = null,
        onComplete: () -> Unit = {},
    ) {
        val state = getState()
        if (state.isFinishingWorkout || state.isComplete) return
        val session = state.session ?: return
        updateState { it.copy(isFinishingWorkout = true) }
        val durationMs = System.currentTimeMillis() - state.startTimeMs
        val durationMinutes = (durationMs / 60000).toInt().coerceAtLeast(1)
        val activeSession = sessionForActiveMode(session, state.activeMode)
        val allExercises = activeSession.allExercises()
        val currentExerciseIndex = exerciseIndex()

        val completedExercises = allExercises.map { exercise ->
            val catalogInfo = resolveCatalogExerciseInfoInIndex(
                index = currentExerciseIndex,
                catalogConfigurationId = exercise.catalogConfigurationId,
                exerciseDbId = exercise.exerciseDbId,
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.name,
            )
            val displayName = com.example.kpkn.domain.exercises.exerciseDisplayParts(exercise, catalogInfo).text
            val sets = exercise.sets.indices.flatMap { setIdx ->
                val bilateral = state.completedSets["${exercise.id}_$setIdx"]
                val left = state.completedSets["${exercise.id}_${setIdx}_L"]
                val right = state.completedSets["${exercise.id}_${setIdx}_R"]
                listOfNotNull(bilateral, left, right)
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = displayName,
                exerciseDbId = canonicalExerciseKey(exercise),
                catalogRevision = exercise.catalogRevision,
                catalogDefinitionId = exercise.catalogDefinitionId,
                catalogConfigurationId = exercise.catalogConfigurationId,
                performanceProfileId = exercise.performanceProfileId,
                occurrenceId = exercise.occurrenceId ?: exercise.id,
                canonicalExerciseId = exercise.canonicalExerciseId ?: canonicalExerciseKey(exercise),
                relativeToCanonicalExerciseId = exercise.relativeToCanonicalExerciseId,
                variantName = exercise.variantName,
                selectedAspects = exercise.selectedAspects,
                effectiveMuscles = exercise.effectiveMuscles,
                restTime = exercise.restTime ?: 90,
                supersetId = exercise.supersetGroupRefOrLegacyId(),
                supersetExerciseCount = exercise.supersetGroupRefOrLegacyId()
                    ?.let { SupersetRules.orderedMembers(activeSession, it).size }
                    ?: 1,
                supersetRounds = exercise.supersetGroupRefOrLegacyId()
                    ?.let { SupersetRules.roundCount(activeSession, it) },
                supersetRestBetween = exercise.supersetRestBetween,
                supersetRestAfter = exercise.supersetRestAfter,
                sets = sets,
            ).let { completed ->
                completed.copy(
                    resolvedProfileSnapshotJson = catalogInfo?.let { info ->
                        exercise.toResolvedCatalogSnapshotJson(info, System.currentTimeMillis())
                    },
                )
            }
        }.filter { it.sets.isNotEmpty() }

        val skippedWithNoSets = allExercises.filter { exercise ->
            exercise.id in state.skippedExerciseIds &&
                state.completedSets.keys.none { key -> key.startsWith("${exercise.id}_") }
        }
        val omittedExercises = skippedWithNoSets.map { exercise ->
            val catalogInfo = resolveCatalogExerciseInfoInIndex(
                index = currentExerciseIndex,
                catalogConfigurationId = exercise.catalogConfigurationId,
                exerciseDbId = exercise.exerciseDbId,
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.name,
            )
            val displayName = com.example.kpkn.domain.exercises.exerciseDisplayParts(exercise, catalogInfo).text
            OmittedExercise(
                exerciseId = exercise.id,
                exerciseName = displayName,
                exerciseDbId = canonicalExerciseKey(exercise),
                variantName = exercise.variantName,
                selectedAspects = exercise.selectedAspects,
                effectiveMuscles = exercise.effectiveMuscles,
            )
        }

        val totalVolume = completedExercises.sumOf { ex ->
            ex.sets.sumOf { it.weight * it.effectiveRepEquivalent() }
        }

        val logId = listOf(
            programId,
            sessionId,
            state.weekId.ifBlank { "noweek" },
            state.startTimeMs.toString(),
        ).joinToString("|")

        scope.launch {
            try {
                val stressScore = withContext(Dispatchers.Default) {
                    val adaptiveCache = com.example.kpkn.data.repository.AugeRepository
                        .getInstance(appContext)
                        .getAdaptiveCache()
                    val drainSummary = AugeFatigueEngine.calculateCompletedSessionDrain(
                        completedExercises = completedExercises,
                        exerciseDb = catalogExerciseIndex(),
                        settings = repository.settings.value,
                        adaptiveCache = adaptiveCache,
                    )
                    val base = (
                        drainSummary.cns * AugeFatigueEngine.STRESS_WEIGHT_CNS +
                            drainSummary.muscular * AugeFatigueEngine.STRESS_WEIGHT_MUSCULAR +
                            drainSummary.spinal * AugeFatigueEngine.STRESS_WEIGHT_SPINAL
                        ).coerceAtLeast(1.0)
                    val predictedOverall = base
                    val adjustedSystem = (drainSummary.cns + closingFeedback.systemAdjustment).coerceIn(0, 100)
                    val adjustedMuscular = (drainSummary.muscular + closingFeedback.muscularAdjustment).coerceIn(0, 100)
                    val adjustedStructure = (drainSummary.spinal + closingFeedback.structureAdjustment).coerceIn(0, 100)
                    val adjustedOverall = (
                        adjustedSystem * AugeFatigueEngine.STRESS_WEIGHT_CNS +
                            adjustedMuscular * AugeFatigueEngine.STRESS_WEIGHT_MUSCULAR +
                            adjustedStructure * AugeFatigueEngine.STRESS_WEIGHT_SPINAL
                        ).coerceAtLeast(1.0)
                    val impactFactor = adjustedOverall / predictedOverall
                    val avgSetEffortSignal = calculateUnifiedSessionEffortSignal(
                        completedExercises.flatMap { it.sets },
                    )
                    val avgTech = state.postExerciseFeedbackByExerciseId.values
                        .map { it.technicalQuality }
                        .average()
                        .takeIf { !it.isNaN() }
                        ?: 8.0
                    val techniqueQuality5 = technicalQuality10ToPenaltyScale(avgTech.toInt())
                    val techniquePenalty = AugeFatigueEngine.calculateTechniquePenalty(
                        technicalQuality = techniqueQuality5,
                        effortSignal = avgSetEffortSignal,
                    ).coerceIn(1.0, 1.5)
                    val clarityFactor = when {
                        closingFeedback.clarityRating >= 8 -> 0.96
                        closingFeedback.clarityRating <= 4 -> 1.10
                        else -> 1.0
                    }
                    (base * impactFactor * techniquePenalty * clarityFactor).coerceAtLeast(1.0)
                }

                val muscleGroups = completedExercises
                    .mapNotNull { ex ->
                        val info = catalogInfoForCompletedExercise(ex)
                        val primary = (ex.effectiveMuscles?.takeIf { it.isNotEmpty() } ?: info?.involvedMuscles.orEmpty())
                            .firstOrNull { m -> m.role == MuscleRole.PRIMARY }
                        if (primary != null) {
                            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
                            getAugeMusclePillarId(canonical, primary.emphasis)
                        } else {
                            ex.exerciseName
                        }
                    }
                    .distinct()
                    .take(6)

                val finalEnergySummary = TrainingEnergyEngine.estimateCompletedSession(
                    completedExercises = completedExercises,
                    settings = repository.settings.value,
                    postExerciseFeedback = state.postExerciseFeedbackByExerciseId,
                )
                val actualDate = LocalDate.now().toString()
                val scheduledDate = scheduledDateForSession(state.weekId, session)
                val scheduleDeltaDays = scheduledDate
                    ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.parse(actualDate)).toInt() }.getOrNull() }

                val log = WorkoutLog(
                    id = logId,
                    programId = programId,
                    sessionId = sessionId,
                    sessionName = session.name,
                    date = java.time.Instant.now().toString(),
                    scheduledDate = scheduledDate,
                    actualDate = actualDate,
                    scheduleDeltaDays = scheduleDeltaDays,
                    durationMinutes = durationMinutes,
                    completedExercises = completedExercises,
                    fatigueLevel = fatigueLevel,
                    discomforts = (
                        closingFeedback.discomforts +
                            state.postExerciseFeedbackByExerciseId.values
                                .flatMap { fb -> fb.discomfortIds }
                                .filter { it != "none" }
                                .map { discomfortLabel(it) }
                        ).distinct(),
                    notes = notes.ifBlank { null },
                    totalVolume = totalVolume,
                    sessionStressScore = stressScore,
                    weekId = state.weekId,
                    macroIndex = state.macroIndex,
                    mesoIndex = state.mesoIndex,
                    clarityRating = closingFeedback.clarityRating,
                    environmentTags = closingFeedback.environmentTags,
                    planDeviations = state.planDeviations,
                    exerciseTags = state.exerciseTags,
                    exerciseNotes = state.exerciseNotes,
                    exercisePhotos = state.exercisePhotos,
                    sessionMilestones = state.sessionMilestones,
                    contextualPerformanceStateV2 = state.contextualPerformanceCache,
                    globalPerformanceStateV3 = state.globalPerformanceCache,
                    contextProfilesV3 = state.contextProfilesV3,
                    replacementDecisionsV2 = repository.getReplacementDecisions(programId)
                        .filter { it.sessionId == sessionId }
                        .take(24),
                    postExerciseReports = state.postExerciseFeedbackByExerciseId.values.map { fb ->
                        com.example.kpkn.data.models.ExerciseDiscomfortReport(
                            exerciseId = fb.exerciseId,
                            exerciseDbId = fb.exerciseDbId,
                            canonicalExerciseId = fb.canonicalExerciseId,
                            exerciseName = fb.exerciseName,
                            technicalQuality = fb.technicalQuality,
                            discomfortIds = fb.discomfortIds.filter { it != "none" },
                            notes = fb.notes,
                            perceivedIntensityRpe = fb.perceivedIntensityRpe,
                            perceivedFailure = fb.perceivedFailure,
                        )
                    },
                    omittedExercises = omittedExercises,
                    energySummary = finalEnergySummary,
                    stillPresentDiscomfortIds = (
                        closingFeedback.stillPresentDiscomfortIds +
                            state.postExerciseFeedbackByExerciseId.values.flatMap { it.stillPresentDiscomfortIds }
                        ).distinct(),
                ).normalizedIdentityFields()

                repository.finalizeWorkout(log)
                runCatching {
                    com.example.kpkn.screens.sessioneditor.TrainedSessionVersionStore
                        .getInstance(appContext)
                        .maybeAppendAfterTraining(
                            sessionId = sessionId,
                            session = activeSession,
                            reason = "Sesión entrenada",
                        )
                }
                updatePredictionBias(closingFeedback)
                restAlertManager.cancelRestAlerts()
                restTimer.clearActiveTimerId()

                onPendingQuestionnaire?.invoke(
                    PendingQuestionnaire(
                        logId = logId,
                        sessionName = session.name,
                        muscleGroups = muscleGroups,
                        stillPresentDiscomfortIds = (
                            closingFeedback.stillPresentDiscomfortIds +
                                state.postExerciseFeedbackByExerciseId.values.flatMap { it.stillPresentDiscomfortIds }
                            ).distinct(),
                        scheduledTimeMs = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
                    )
                )

                scope.launch(Dispatchers.IO) {
                    try {
                        performanceRangeStore.persistFinishedSessionPerformance(
                            completedExercises = log.completedExercises,
                            sessionId = sessionId,
                            postExerciseFeedbackByExerciseId = getState().postExerciseFeedbackByExerciseId,
                        )
                    } catch (error: Exception) {
                        error.printStackTrace()
                    }
                }

                val currentState = getState()
                val currentSession = currentState.session
                if (currentSession != null && currentState.programId.isNotEmpty()) {
                    val deltas = computeVolumeDelta(
                        plannedSession = currentSession,
                        completedSets = currentState.completedSets,
                    )
                    if (deltas.isNotEmpty()) {
                        deferOnComplete(onComplete)
                        updateState {
                            it.copy(
                                pendingVolumeAdvances = deltas,
                                showVolumeAdvanceModal = true,
                                showFinishSheet = false,
                                isFinishingWorkout = false,
                            )
                        }
                        return@launch
                    }
                }
                prepareVoiceDiagnosticExport()
                updateState {
                    it.copy(
                        isComplete = true,
                        showFinishSheet = false,
                        sessionStressScore = stressScore,
                        isFinishingWorkout = false,
                    )
                }
                ActiveWorkoutHolder.clear()
                onComplete()
            } catch (error: Exception) {
                error.printStackTrace()
                updateState { it.copy(isFinishingWorkout = false) }
            }
        }
    }

    fun computeVolumeDelta(
        plannedSession: Session,
        completedSets: Map<String, CompletedSet>,
    ): List<MuscleAdvance> {
        val state = getState()
        return computeWorkoutVolumeDelta(
            programId = state.programId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            weekId = state.weekId,
            plannedSession = plannedSession,
            completedSets = completedSets,
            exerciseIndex = exerciseIndex(),
            repository = repository,
        )
    }

    private fun scheduledDateForSession(weekId: String?, session: Session): String? {
        if (weekId.isNullOrBlank()) return null
        val program = repository.getProgramById(programId) ?: return null
        val projected = ProgramCalendarEngine.project(program).scheduledDateFor(session, weekId)
        if (projected != null) return projected.toString()
        val week = program.macrocycles
            .asSequence()
            .flatMap { macro -> macro.blocks.asSequence() }
            .flatMap { block -> block.mesocycles.asSequence() }
            .flatMap { meso -> meso.weeks.asSequence() }
            .firstOrNull { it.id == weekId }
            ?: return null
        val day = session.dayOfWeek?.coerceIn(1, 7)
        val explicit = day?.let { week.trainingDayDates[it] }
        if (!explicit.isNullOrBlank()) return explicit
        val start = runCatching { LocalDate.parse(week.startDate) }.getOrNull() ?: return null
        return day?.let { start.plusDays((it - 1).toLong()).toString() } ?: week.startDate
    }
}

internal fun computeWorkoutVolumeDelta(
    programId: String,
    macroIndex: Int,
    mesoIndex: Int,
    weekId: String,
    plannedSession: Session,
    completedSets: Map<String, CompletedSet>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    repository: ProgramRepository,
): List<MuscleAdvance> {
    if (programId.isEmpty()) return emptyList()

    val plannedPerMuscle = mutableMapOf<String, Double>()
    for (ex in plannedSession.allExercises()) {
        resolveCatalogExerciseInfoInIndex(
            index = exerciseIndex,
            catalogConfigurationId = ex.catalogConfigurationId,
            exerciseDbId = ex.exerciseDbId,
            exerciseId = ex.exerciseId,
            exerciseName = ex.name,
        ) ?: continue
        for (muscle in ExerciseMuscleResolver.effectiveMusclesForVolume(ex, exerciseIndex)) {
            if (muscle.role != MuscleRole.PRIMARY) continue
            val muscleId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
            plannedPerMuscle[muscleId] = (plannedPerMuscle[muscleId] ?: 0.0) + ex.sets.size
        }
    }

    val actualPerMuscle = mutableMapOf<String, Double>()
    val completedByExercise = mutableMapOf<String, Int>()
    for ((key, _) in completedSets) {
        val parsed = parseCompletedSetKey(key) ?: continue
        completedByExercise[parsed.exerciseId] =
            (completedByExercise[parsed.exerciseId] ?: 0) + 1
    }
    for (ex in plannedSession.allExercises()) {
        val sets = completedByExercise[ex.id] ?: 0
        if (sets == 0) continue
        resolveCatalogExerciseInfoInIndex(
            index = exerciseIndex,
            catalogConfigurationId = ex.catalogConfigurationId,
            exerciseDbId = ex.exerciseDbId,
            exerciseId = ex.exerciseId,
            exerciseName = ex.name,
        ) ?: continue
        for (muscle in ExerciseMuscleResolver.effectiveMusclesForVolume(ex, exerciseIndex)) {
            if (muscle.role != MuscleRole.PRIMARY) continue
            val muscleId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
            actualPerMuscle[muscleId] = (actualPerMuscle[muscleId] ?: 0.0) + sets
        }
    }

    val surplusMuscles = mutableListOf<String>()
    for ((muscle, planned) in plannedPerMuscle) {
        val actual = actualPerMuscle[muscle] ?: 0.0
        val delta = actual - planned
        if (delta > 0) surplusMuscles.add(muscle)
    }
    if (surplusMuscles.isEmpty()) return emptyList()

    val program = repository.getProgramById(programId) ?: return emptyList()
    val week = program.macrocycles
        .getOrNull(macroIndex)?.blocks
        ?.flatMap { it.mesocycles }
        ?.getOrNull(mesoIndex)?.weeks
        ?.firstOrNull { it.id == weekId } ?: return emptyList()
    val weekSessions = week.sessions

    val nextSession = com.example.kpkn.domain.sessionassistant.SessionAssistantEngine.findNextSessionWithMuscles(
        currentSessionId = plannedSession.id,
        weekSessions = weekSessions,
        muscleIds = surplusMuscles,
        exerciseIndex = exerciseIndex,
    ) ?: return emptyList()

    return com.example.kpkn.domain.sessionassistant.SessionAssistantEngine.computeProposedDiscounts(
        currentSession = plannedSession,
        nextSession = nextSession,
        targetMuscles = surplusMuscles,
        completedSets = completedSets,
        exerciseIndex = exerciseIndex,
    )
}
