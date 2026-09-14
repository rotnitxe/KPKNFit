package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.models.unresolvedDiscomfortIds
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.relator.RelatorCoachSlice
import com.example.kpkn.domain.relator.RelatorContext
import com.example.kpkn.domain.relator.RelatorHistorySet
import com.example.kpkn.domain.relator.RelatorHistorySlice
import com.example.kpkn.domain.relator.RelatorMediaSlice
import com.example.kpkn.domain.relator.RelatorMilestoneSlice
import com.example.kpkn.domain.relator.RelatorPlanSlice
import com.example.kpkn.domain.relator.RelatorProgressSlice
import com.example.kpkn.domain.relator.RelatorReadinessSlice
import com.example.kpkn.domain.relator.RelatorRestSlice
import com.example.kpkn.domain.relator.RelatorSessionPhase
import com.example.kpkn.domain.relator.RelatorWarmupSlice
import com.example.kpkn.screens.workout.components.WarmupPhaseRow

internal data class RelatorBuilderQueries(
    val getSetDraft: (exerciseId: String, setIdx: Int, side: String?) -> WorkoutSetDraft?,
    val getWeightSuggestion: (Exercise, Int, String?, String?) -> WeightSuggestion?,
    val getPreviousSessionFirstSetWeight: (Exercise, String?) -> Double?,
    val getExerciseHistory: (Exercise, Int, String?) -> List<ExerciseHistoryEntry>,
    val bestEstimated1Rm: (Exercise) -> Double,
    val tagProgressionHint: (Exercise) -> RelatorTagProgressHint?,
    val latestDiscomfortIds: (Exercise) -> List<String>,
    val recentWorkoutLogs: (Int) -> List<WorkoutLog>,
    val visibleExercises: (WorkoutUiState) -> List<Exercise>,
    val workoutStepPositions: (WorkoutUiState) -> List<WorkoutStep>,
)

internal data class RelatorUiSlice(
    val sessionId: String,
    val currentExerciseIdx: Int,
    val currentSetIdx: Int,
    val activeStepKey: String?,
    val completedSetCount: Int,
    val completedSetFingerprint: Int,
    val omittedSize: Int,
    val skippedSize: Int,
    val draftFingerprint: Int,
    val warmupDoneSize: Int,
    val mobilityDoneSize: Int,
    val restRunning: Boolean,
    val restKind: RestTimerKind?,
    val restPlanned: Int,
    val restSuggested: Int,
    val showPostExercise: Boolean,
    val livePlan: LivePlanContext?,
    val dailyScore: Int?,
    val wellbeingSleep: Int?,
    val wellbeingStress: Int?,
    val wellbeingDoms: Int?,
    val exerciseReadinessScore: Int?,
    val coachKey: String?,
    val milestonesSize: Int,
    val targetMinutes: Int?,
    val startTimeMs: Long,
    val ultraFast: Boolean,
    val editingKey: String?,
    val lastLoggedKey: String?,
    val prStar: Boolean,
    val sleepQuality: Int?,
    val autoRegulationKey: String?,
    val loadSuggestionFingerprint: Int,
    val tagsFingerprint: Int,
) {
    companion object {
        fun from(state: WorkoutUiState): RelatorUiSlice {
            val exercises = state.session?.allExercises().orEmpty()
            val current = exercises.getOrNull(state.currentExerciseIdx)
            val readiness = current?.id?.let { state.exerciseReadinessMap[it] }
            return RelatorUiSlice(
                sessionId = state.session?.id.orEmpty(),
                currentExerciseIdx = state.currentExerciseIdx,
                currentSetIdx = state.currentSetIdx,
                activeStepKey = state.activeStepKey,
                completedSetCount = state.completedSets.size,
                completedSetFingerprint = state.completedSets.keys.hashCode(),
                omittedSize = state.omittedSetKeys.size,
                skippedSize = state.skippedExerciseIds.size,
                draftFingerprint = state.setDrafts.hashCode(),
                warmupDoneSize = state.warmupCompletedExerciseIds.size,
                mobilityDoneSize = state.mobilityCompletedExerciseIds.size,
                restRunning = state.isRestTimerRunning,
                restKind = state.restModalState?.kind,
                restPlanned = state.restModalState?.plannedSeconds ?: 0,
                restSuggested = state.restModalState?.suggestedSeconds ?: 0,
                showPostExercise = state.showPostExerciseSheet,
                livePlan = state.livePlanContext,
                dailyScore = state.dailyReadiness?.score,
                wellbeingSleep = state.todayWellbeing?.sleepQuality,
                wellbeingStress = state.todayWellbeing?.stressLevel,
                wellbeingDoms = state.todayWellbeing?.doms,
                exerciseReadinessScore = readiness?.overallScore,
                coachKey = state.currentCoachMessage?.key,
                milestonesSize = state.sessionMilestones.size,
                targetMinutes = state.customTargetDurationMinutes ?: state.targetDurationMinutes,
                startTimeMs = state.startTimeMs,
                ultraFast = state.ultraFastApplied,
                editingKey = state.editingState?.setKey,
                lastLoggedKey = state.setJustLoggedKey,
                prStar = current?.isStarTarget == true,
                sleepQuality = state.sleepQuality,
                autoRegulationKey = state.currentAutoRegulation?.let { "${it.exerciseId}:${it.nextSetIdx}" },
                loadSuggestionFingerprint = state.loadSuggestions.keys.hashCode(),
                tagsFingerprint = state.exerciseTags.hashCode(),
            )
        }
    }
}

internal class RelatorChangeTracker {
    private var primed = false
    private var identity = ""
    private var changedField = RelatorChangedField.NONE
    private var prevWeight: String? = null
    private var prevReps: String? = null
    private var prevIntensity: String? = null
    private var prevWarmupDraft = ""
    private var prevMobilityDone = 0
    private var prevTimer = false
    private var prevDropCount = 0
    private var prevFailure = false

    fun detect(
        setIdentity: String,
        phase: RelatorPhase,
        draft: WorkoutSetDraft?,
        warmupRaw: String,
        mobilityDone: Int,
        timerRunning: Boolean,
    ): RelatorChangedField {
        if (setIdentity != identity) {
            identity = setIdentity
            primed = false
            changedField = RelatorChangedField.NONE
        }
        val detected = if (!primed) {
            RelatorChangedField.NONE
        } else {
            detectRelatorChangedField(
                phase = phase,
                draftDirty = draft?.isDirty == true,
                weightText = draft?.weightText,
                repsText = draft?.valueText,
                intensityText = draft?.intensityText,
                prevWeight = prevWeight,
                prevReps = prevReps,
                prevIntensity = prevIntensity,
                warmupDraft = warmupRaw,
                prevWarmupDraft = prevWarmupDraft,
                mobilityDone = mobilityDone,
                prevMobilityDone = prevMobilityDone,
                timerRunning = timerRunning,
                prevTimerRunning = prevTimer,
                previousField = changedField,
                dropCount = draft?.dropSetCount ?: 0,
                prevDropCount = prevDropCount,
                reachedFailure = draft?.reachedFailure == true,
                prevReachedFailure = prevFailure,
            )
        }
        primed = true
        changedField = detected
        prevWeight = draft?.weightText
        prevReps = draft?.valueText
        prevIntensity = draft?.intensityText
        prevWarmupDraft = warmupRaw
        prevMobilityDone = mobilityDone
        prevTimer = timerRunning
        prevDropCount = draft?.dropSetCount ?: 0
        prevFailure = draft?.reachedFailure == true
        return detected
    }
}

internal object RelatorContextBuilder {
    fun inferPhase(
        state: WorkoutUiState,
        currentExercise: Exercise?,
        steps: List<WorkoutStep>,
        showingPostExerciseCard: Boolean,
    ): RelatorPhase {
        val hidden = showingPostExerciseCard || currentExercise == null || currentExercise.isCardio
        if (hidden) return RelatorPhase.HIDDEN
        val workingRest = state.isRestTimerRunning &&
            state.restModalState != null &&
            state.restModalState?.kind != RestTimerKind.WARMUP
        if (workingRest) return RelatorPhase.REST
        val step = steps.firstOrNull { it.stepKey == state.activeStepKey }
        return when (step?.type) {
            WorkoutStepType.MOBILITY, WorkoutStepType.MOBILITY_GROUP, WorkoutStepType.MOBILITY_TOTAL -> RelatorPhase.MOBILITY
            WorkoutStepType.WARMUP -> RelatorPhase.WARMUP
            WorkoutStepType.CARDIO -> RelatorPhase.HIDDEN
            else -> RelatorPhase.WORKING
        }
    }

    fun buildSnapshot(
        state: WorkoutUiState,
        queries: RelatorBuilderQueries,
        tracker: RelatorChangeTracker,
        restRemainingSeconds: Int,
        sessionTimeRemainingSeconds: Int?,
        idleCycle: Int,
        warmupWeightDrafts: Map<String, String>,
        assistAck: RelatorAssistAck?,
        gender: Gender?,
        catalog: com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2? =
            CatalogV2ProcessCache.peek()?.catalog,
        speechMemory: RelatorSpeechMemory = RelatorSpeechMemory(),
        shownConceptIds: Set<String> = emptySet(),
    ): LiveRelatorSnapshot {
        val visible = queries.visibleExercises(state)
        val currentExercise = visible.getOrNull(state.currentExerciseIdx)
            ?: state.session?.allExercises()?.getOrNull(state.currentExerciseIdx)
        val setIdx = state.currentSetIdx
        val currentSet = currentExercise?.sets?.getOrNull(setIdx)
        val steps = queries.workoutStepPositions(state)
        val activeSide = state.editingState?.side
            ?: currentExercise?.takeIf { it.isEffectivelyUnilateral() }?.let { "left" }
        val inferredPhase = inferPhase(state, currentExercise, steps, state.showPostExerciseSheet)
        val groupId = currentExercise?.supersetGroupRefOrLegacyId()
        val groupMembers = groupId
            ?.let { id -> visible.filter { it.supersetGroupRefOrLegacyId() == id } }
            .orEmpty()
        val warmupMembers = if (groupMembers.size > 1) {
            groupMembers.filter { it.warmupSets.isNotEmpty() }
        } else {
            listOfNotNull(currentExercise?.takeIf { it.warmupSets.isNotEmpty() })
        }
        val mobilityMembers = if (groupMembers.size > 1) {
            groupMembers.filter { it.mobilitySeries.isNotEmpty() }
        } else {
            listOfNotNull(currentExercise?.takeIf { it.mobilitySeries.isNotEmpty() })
        }
        val warmupRows = buildRelatorWarmupRows(warmupMembers, state)
        val firstIncompleteWarmup = warmupRows.firstOrNull { !it.isCompleted }
        val warmupIsLast = firstIncompleteWarmup != null && firstIncompleteWarmup.index == warmupRows.lastIndex
        val warmupKey = firstIncompleteWarmup?.let { "${it.exerciseId}_${it.warmup.id}" }
        val warmupRaw = warmupKey?.let { warmupWeightDrafts[it] }.orEmpty()
        val mobilityTotal = mobilityMembers.sumOf { it.mobilitySeries.size }
        val mobilityDone = mobilityMembers.sumOf { member ->
            member.mobilitySeries.count { mobility ->
                val key = WorkoutStepRules.mobilityStepKey(member.id, mobility.id, 0)
                key in state.mobilityCompletedExerciseIds || member.id in state.mobilityCompletedExerciseIds
            }
        }
        val timerRunning = restRemainingSeconds > 0 && inferredPhase == RelatorPhase.MOBILITY
        val draft = currentExercise?.let { queries.getSetDraft(it.id, setIdx, activeSide) }
        val detected = tracker.detect(
            setIdentity = currentExercise?.let { workoutSetKey(it.id, setIdx, activeSide) }.orEmpty(),
            phase = inferredPhase,
            draft = draft,
            warmupRaw = warmupRaw,
            mobilityDone = mobilityDone,
            timerRunning = timerRunning,
        )
        val exerciseContext = resolveRelatorExerciseContext(currentExercise, catalog)
        val suggestion = currentExercise?.let {
            queries.getWeightSuggestion(it, setIdx, state.exerciseTags[it.id], activeSide)
        }
        val previousWeight = currentExercise?.let {
            queries.getPreviousSessionFirstSetWeight(it, state.exerciseTags[it.id])
        }
        val restPhase = inferredPhase == RelatorPhase.REST
        val sessionLastSet = currentExercise?.let {
            previousWorkingSetToday(
                completedSets = state.completedSets,
                exerciseId = it.id,
                currentSetIdx = setIdx,
                side = activeSide,
                restPhase = restPhase,
            )
        }
        val historyLastSet = currentExercise
            ?.let { exercise ->
                queries.getExerciseHistory(exercise, 1, state.exerciseTags[exercise.id]).firstOrNull()?.sets
            }
            ?.let(::firstWorkingSetMemory)
        val loadAnchor = resolveRelatorLoadAnchor(
            currentSetIdx = setIdx,
            restPhase = restPhase,
            sessionPrevious = sessionLastSet,
            historyFirst = historyLastSet,
        )
        val enteredWeightRaw = when (detected) {
            RelatorChangedField.WARMUP_WEIGHT -> warmupRaw
            else -> draft?.weightText.orEmpty()
        }
        val enteredWeight = enteredWeightRaw.replace(',', '.').toDoubleOrNull()
        val suggestedWeight = when (detected) {
            RelatorChangedField.WARMUP_WEIGHT -> firstIncompleteWarmup?.suggestedWeightKg
            else -> suggestion?.suggestedWeight
        }
        val lastLiftedWeight = when (detected) {
            RelatorChangedField.WARMUP_WEIGHT -> firstIncompleteWarmup?.suggestedWeightKg
            else -> loadAnchor.compareWeightKg
        }
        val loadKind = relatorLoadKind(draft?.loadMode ?: currentSet?.loadModeV2)
        val unit = relatorUnit(currentSet?.unitModeV2)
        val effectiveField = when {
            loadKind != RelatorLoadKind.LOAD && detected == RelatorChangedField.WEIGHT -> RelatorChangedField.NONE
            unit != RelatorUnit.REPS && detected == RelatorChangedField.REPS && unit != RelatorUnit.TIME ->
                RelatorChangedField.NONE
            else -> detected
        }
        val catalogIndex = catalogExerciseIndex()
        val tissueHint = if (currentExercise == null || inferredPhase == RelatorPhase.HIDDEN) {
            null
        } else {
            buildRelatorTissueHintFromLogs(
                state = state,
                currentExercise = currentExercise,
                visibleExercises = visible,
                catalogIndex = catalogIndex,
                recentLogs = queries.recentWorkoutLogs(36),
            )
        }
        val sessionBestPrevious = currentExercise?.let {
            sessionBestPreviousE1rm(
                uiState = state,
                exerciseId = it.id,
                currentSetIdx = setIdx,
                side = activeSide,
                restPhase = restPhase,
            )
        } ?: 0.0
        val historyBestE1rm = currentExercise?.let { queries.bestEstimated1Rm(it) } ?: 0.0
        val enteredRepsValue = draft?.valueText?.replace(',', '.')?.toDoubleOrNull()
        val prProbeWeight = if (inferredPhase == RelatorPhase.REST) sessionLastSet?.weightKg else enteredWeight
        val prProbeReps = if (inferredPhase == RelatorPhase.REST) {
            sessionLastSet?.reps
        } else {
            enteredRepsValue?.toInt()
        }
        val tagProgressHint = currentExercise?.let { queries.tagProgressionHint(it) }
        val prHint = currentExercise?.let { ex ->
            resolveRelatorPrHint(
                liveWeightKg = prProbeWeight,
                liveReps = prProbeReps,
                historyBestE1rm = historyBestE1rm,
                sessionBestPreviousE1rm = sessionBestPrevious,
                isStar = ex.isStarTarget || (ex.goal1RM != null && (ex.goal1RM ?: 0.0) > 0.0),
                goal1RmKg = ex.goal1RM,
            )
        }
        val discomfortHint = currentExercise?.let { exercise ->
            val same = thisSessionDiscomfortIds(state.postExerciseFeedbackByExerciseId[exercise.id])
            val others = state.postExerciseFeedbackByExerciseId.mapNotNull { (id, feedback) ->
                if (id == exercise.id) return@mapNotNull null
                val labels = discomfortLabelsFromIds(feedback.unresolvedDiscomfortIds())
                val label = labels.firstOrNull() ?: return@mapNotNull null
                val otherName = visible.find { it.id == id }?.name ?: feedback.exerciseName
                otherName to label
            }
            pickRelatorDiscomfortHint(
                sameExerciseThisSessionLabels = discomfortLabelsFromIds(same),
                otherThisSession = others,
                previousSessionLabels = discomfortLabelsFromIds(queries.latestDiscomfortIds(exercise)),
            )
        }
        val headerName = currentExercise?.let(::displayWorkoutExerciseName).orEmpty()
        val assistOffer = pickAssistOffer(
            state = state,
            currentExercise = currentExercise,
            visibleExercises = visible,
            inferredPhase = inferredPhase,
            family = exerciseContext.family,
            activeSide = activeSide,
            sessionTimeRemainingSeconds = sessionTimeRemainingSeconds,
        )
        return LiveRelatorSnapshot(
            visible = inferredPhase != RelatorPhase.HIDDEN,
            phase = inferredPhase,
            family = exerciseContext.family,
            feminine = gender == Gender.FEMALE,
            exerciseDisplayName = headerName,
            setIndex = setIdx,
            setCount = currentExercise?.sets?.size?.coerceAtLeast(1) ?: 1,
            hasHistory = (loadAnchor.historyFirst?.weightKg ?: 0.0) > 0.0 ||
                (previousWeight != null && previousWeight > 0.0),
            warmupIncompleteIndex = firstIncompleteWarmup?.index,
            warmupCount = warmupRows.size,
            warmupIsLastIncomplete = warmupIsLast,
            mobilityCompleted = mobilityDone,
            mobilityTotal = mobilityTotal,
            mobilityTimerRunning = timerRunning,
            lastChangedField = effectiveField,
            enteredWeight = enteredWeight,
            enteredWeightRaw = enteredWeightRaw,
            referenceWeight = suggestedWeight ?: lastLiftedWeight,
            suggestedWeight = suggestedWeight,
            lastLiftedWeight = lastLiftedWeight,
            enteredReps = enteredRepsValue,
            plannedReps = currentSet?.targetReps?.toDouble()
                ?: currentSet?.plannedTargetV2
                ?: currentSet?.targetRepsRange?.max?.toDouble(),
            enteredIntensity = draft?.intensityText?.replace(',', '.')?.toDoubleOrNull(),
            plannedIntensity = currentSet?.targetRPE ?: currentSet?.targetRIR?.toDouble(),
            intensityMode = currentSet?.intensityMode,
            reachedFailure = draft?.reachedFailure == true,
            plannedFailure = currentSet?.isFailure == true || currentSet?.intensityMode == IntensityMode.FAILURE,
            dropSetCount = draft?.dropSetCount ?: 0,
            plannedDropCount = plannedDropSetCount(currentSet),
            compound = exerciseContext.compound,
            tissueHint = tissueHint,
            loadKind = loadKind,
            unit = unit,
            setKey = currentExercise?.let { workoutSetKey(it.id, setIdx, activeSide) }.orEmpty(),
            parentContextKey = relatorParentContextKey(
                exerciseId = currentExercise?.id.orEmpty(),
                groupId = groupId,
                groupMemberCount = groupMembers.size,
                unilateral = currentExercise?.isEffectivelyUnilateral() == true,
            ),
            idleCycle = idleCycle,
            isSuperset = groupMembers.size > 1,
            activeSideLabel = if (currentExercise?.isEffectivelyUnilateral() == true) {
                when (activeSide?.lowercase()) {
                    "left", "l" -> "izquierdo"
                    "right", "r" -> "derecho"
                    else -> null
                }
            } else {
                null
            },
            sessionLastSet = sessionLastSet,
            historyLastSet = loadAnchor.historyFirst,
            discomfortHint = discomfortHint,
            prHint = prHint,
            tagProgressHint = tagProgressHint,
            isDropsetFollowUp = currentSet?.isDropSet == true &&
                currentExercise?.sets?.getOrNull(setIdx - 1)?.restAfterSeconds == 0,
            failedSetCaution = currentExercise?.let {
                resolveFailedSetCaution(
                    completedSets = state.completedSets,
                    exerciseIds = visible.map { ex -> ex.id },
                    currentExerciseId = it.id,
                    currentSetIdx = setIdx,
                    restPhase = restPhase,
                )
            },
            assistAck = assistAck,
            ultraFastApplied = state.ultraFastApplied,
            loadFromPreviousSession = loadAnchor.fromPreviousSession,
            axialLoadFactor = exerciseContext.axialLoadFactor,
            equipmentId = exerciseContext.equipmentId,
            movementPatternId = exerciseContext.movementPatternId,
            plannedIsoHold = currentSet?.plannedIntensityTechniques.orEmpty()
                .any { it.type == TechniqueType.ISO_HOLD },
            plannedNegatives = currentSet?.plannedIntensityTechniques.orEmpty()
                .any { it.type == TechniqueType.NEGATIVES },
            shownConceptIds = shownConceptIds,
            speechMemory = speechMemory,
            sessionSpeechKey = state.session?.id.orEmpty(),
            assistOffer = assistOffer,
        )
    }

    fun buildContext(
        state: WorkoutUiState,
        snapshot: LiveRelatorSnapshot,
        restRemainingSeconds: Int,
        sessionTimeRemainingSeconds: Int?,
        queries: RelatorBuilderQueries,
    ): RelatorContext {
        val visible = queries.visibleExercises(state)
        val current = visible.getOrNull(state.currentExerciseIdx)
        val currentSet = current?.sets?.getOrNull(state.currentSetIdx)
        val plan = state.livePlanContext
        val historySets = current
            ?.let { queries.getExerciseHistory(it, 3, state.exerciseTags[it.id]) }
            .orEmpty()
        val lastSets = historySets.firstOrNull()?.sets.orEmpty()
            .filter { !it.isWarmup && !it.skipped && it.weight > 0 }
            .map { RelatorHistorySet(it.weight, it.reps) }
        val trend = historySets.mapNotNull { entry ->
            entry.sets.filter { !it.isWarmup && it.weight > 0 }.maxOfOrNull { it.weight }
        }
        val bestRm = current?.let { queries.bestEstimated1Rm(it) } ?: 0.0
        val liveRm = snapshot.enteredWeight?.let { w ->
            snapshot.enteredReps?.toInt()?.let { r -> calculateHybrid1RM(w, r) }
        }
        val distance = if (bestRm > 0 && liveRm != null) (bestRm - liveRm).coerceAtLeast(0.0) else
            bestRm.takeIf { it > 0 }?.let { snapshot.suggestedWeightKgGap(it) }
        val totalWorking = visible.filter { !it.isCardio }.sumOf { it.sets.size }
        val completedWorking = state.completedSets.count { (_, set) -> !set.isWarmup && !set.skipped }
        val elapsedMin = ((System.currentTimeMillis() - state.startTimeMs) / 60_000L).toInt().coerceAtLeast(0)
        val targetMin = state.customTargetDurationMinutes ?: state.targetDurationMinutes
        val minutesAhead = targetMin?.let { target ->
            val expected = if (totalWorking <= 0) 0 else (elapsedMin - (target * completedWorking / totalWorking.coerceAtLeast(1)))
            -expected
        }
        val nextExercise = visible.getOrNull(state.currentExerciseIdx + 1)
        val nextKg = nextExercise?.let {
            queries.getWeightSuggestion(it, 0, state.exerciseTags[it.id], null)?.suggestedWeight
        }
        val readiness = current?.id?.let { state.exerciseReadinessMap[it] }
        val warmupRemaining = (snapshot.warmupCount - (snapshot.warmupIncompleteIndex ?: snapshot.warmupCount))
            .coerceAtLeast(if (snapshot.warmupIsLastIncomplete) 1 else 0)
        val concept = snapshot.conceptCueOrNull()
        return RelatorContext(
            sessionId = state.session?.id.orEmpty(),
            setKey = snapshot.setKey,
            idleCycle = snapshot.idleCycle,
            feminine = snapshot.feminine,
            visible = snapshot.visible,
            phase = snapshot.phase.toDomain(),
            exerciseId = current?.id.orEmpty(),
            exerciseName = snapshot.exerciseDisplayName,
            setIndex = snapshot.setIndex,
            setCount = snapshot.setCount,
            slotRole = current?.slotRole,
            isTopSet = currentSet?.isTopSet == true,
            loadBasis = currentSet?.loadBasis,
            techniqueModifier = current?.techniqueModifier,
            isAmrap = currentSet?.isAmrap == true,
            isCompetitionLift = current?.isCompetitionLift == true,
            targetPercentageRm = currentSet?.targetPercentageRM,
            prescribedWeightKg = currentSet?.weight,
            suggestedWeightKg = snapshot.suggestedWeight,
            enteredWeightKg = snapshot.enteredWeight,
            targetReps = currentSet?.targetReps,
            targetRpe = currentSet?.targetRPE,
            targetRir = currentSet?.targetRIR,
            executionCues = current?.executionCues.orEmpty(),
            restAfterSeconds = currentSet?.restAfterSeconds ?: current?.restTime,
            isTyping = snapshot.lastChangedField.isReaction,
            userReacted = snapshot.lastChangedField.isReaction,
            shownConceptIds = snapshot.shownConceptIds,
            conceptId = concept?.id,
            conceptLines = concept?.lines.orEmpty(),
            plan = RelatorPlanSlice(
                sourceProtocolId = plan?.sourceProtocolId,
                sourceProtocolName = plan?.sourceProtocolName,
                mode = plan?.mode,
                trainingPhase = plan?.trainingPhase,
                goals = plan?.goals,
                autoregulationMode = plan?.autoregulationMode ?: com.example.kpkn.data.models.AutoregulationMode.OFF,
                blockGoal = plan?.blockGoal,
                blockProgressionScheme = plan?.blockProgressionScheme,
                blockName = plan?.blockName,
                weekIndexInBlock = plan?.weekIndexInBlock,
                weeksInBlock = plan?.weeksInBlock,
                progression = plan?.progression,
                autoregulationHooks = plan?.autoregulationHooks.orEmpty(),
            ),
            history = RelatorHistorySlice(
                lastSessionSets = lastSets,
                trendThreeSessionsKg = trend,
                bestEstimatedRmKg = bestRm,
                distanceToBestKg = distance,
            ),
            progress = RelatorProgressSlice(
                completedWorkingSets = completedWorking,
                totalWorkingSets = totalWorking,
                elapsedMinutes = elapsedMin,
                targetDurationMinutes = targetMin,
                remainingSeconds = sessionTimeRemainingSeconds,
                minutesAhead = minutesAhead,
                nextExerciseName = nextExercise?.let(::displayWorkoutExerciseName)
                    ?: current?.let(::displayWorkoutExerciseName),
                nextSuggestedKg = nextKg ?: snapshot.suggestedWeight,
            ),
            rest = RelatorRestSlice(
                active = snapshot.phase == RelatorPhase.REST,
                remainingSeconds = restRemainingSeconds,
                plannedSeconds = state.restModalState?.plannedSeconds ?: current?.restTime ?: 0,
                adaptiveSeconds = state.restModalState?.suggestedSeconds ?: 0,
                justHitPr = snapshot.prHint != null && snapshot.phase == RelatorPhase.REST,
                overlayMinimized = false, // F4: relator does not depend on isRestMinimized
            ),
            readiness = RelatorReadinessSlice(
                dailyScore = state.dailyReadiness?.score,
                dailyLabel = state.dailyReadiness?.label,
                dailyDetails = state.dailyReadiness?.details.orEmpty(),
                exerciseScore = readiness?.overallScore,
                limitingFactor = readiness?.limitingFactor,
                sleepQuality = state.todayWellbeing?.sleepQuality ?: state.sleepQuality,
                stressLevel = state.todayWellbeing?.stressLevel,
                doms = state.todayWellbeing?.doms,
                motivation = state.todayWellbeing?.motivation,
                preWorkoutDiscomforts = state.todayWellbeing?.preWorkoutDiscomforts.orEmpty(),
                muscleDrainLabel = readiness?.limitingDetail,
            ),
            warmup = RelatorWarmupSlice(
                incompleteIndex = snapshot.warmupIncompleteIndex,
                count = snapshot.warmupCount,
                isLastIncomplete = snapshot.warmupIsLastIncomplete,
                suggestedKg = snapshot.suggestedWeight.takeIf { snapshot.phase == RelatorPhase.WARMUP },
                remainingCount = if (snapshot.phase == RelatorPhase.WARMUP) {
                    (snapshot.warmupCount - (snapshot.warmupIncompleteIndex ?: 0)).coerceAtLeast(1)
                } else {
                    warmupRemaining
                },
            ),
            coach = RelatorCoachSlice(
                key = state.currentCoachMessage?.key,
                title = state.currentCoachMessage?.title,
                body = state.currentCoachMessage?.body,
                action = state.currentCoachMessage?.action?.name,
            ),
            media = RelatorMediaSlice(previousCount = 0),
            milestone = RelatorMilestoneSlice(
                prJustNow = snapshot.prHint != null,
                isStar = snapshot.prHint?.isStar == true,
                estimatedRmKg = snapshot.prHint?.estimatedRmKg,
                goal1RmKg = snapshot.prHint?.goal1RmKg,
                sessionVolumeRecord = false,
                bestTagName = snapshot.tagProgressHint?.tagName,
            ),
        )
    }

    private fun LiveRelatorSnapshot.suggestedWeightKgGap(bestRm: Double): Double? {
        val live = suggestedWeight ?: enteredWeight ?: return null
        return (bestRm - live).coerceAtLeast(0.0)
    }

    private fun pickAssistOffer(
        state: WorkoutUiState,
        currentExercise: Exercise?,
        visibleExercises: List<Exercise>,
        inferredPhase: RelatorPhase,
        family: RelatorFamily,
        activeSide: String?,
        sessionTimeRemainingSeconds: Int?,
    ): RelatorAssistOffer? {
        val skippedIds = state.skippedExerciseIds
        val all = state.session?.allExercises().orEmpty()
        val visibleById = visibleExercises.associateBy { it.id }
        val ordered = if (all.isEmpty()) {
            visibleExercises
        } else {
            all.mapNotNull { exercise ->
                when {
                    exercise.id in skippedIds -> exercise
                    else -> visibleById[exercise.id]
                }
            }
        }
        val sessionExercises = ordered.map { exercise ->
            RelatorAssistExercise(
                id = exercise.id,
                name = exercise.name,
                setCount = exercise.sets.size,
                groupId = exercise.supersetGroupRefOrLegacyId(),
                unilateral = exercise.isEffectivelyUnilateral(),
                isCardio = exercise.isCardio,
                mobilityLabels = exercise.mobilitySeries.flatMap { series ->
                    listOfNotNull(
                        series.id,
                        series.exerciseDbId,
                        series.catalogConfigurationId,
                        series.name,
                        series.notes,
                    )
                },
            )
        }
        val currentId = currentExercise?.id.orEmpty()
        val currentIndex = sessionExercises.indexOfFirst { it.id == currentId }
        return pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = inferredPhase,
                family = family,
                currentExerciseId = currentId,
                currentExerciseName = currentExercise?.name.orEmpty(),
                currentSetIndex = state.currentSetIdx,
                currentExerciseIndex = currentIndex,
                activeSide = activeSide,
                sessionExercises = sessionExercises,
                completedSetKeys = state.completedSets.keys,
                omittedSetKeys = state.omittedSetKeys,
                skippedExerciseIds = skippedIds,
                remainingSeconds = sessionTimeRemainingSeconds,
                ultraFastApplied = state.ultraFastApplied,
            ),
        )
    }
}

internal fun RelatorPhase.toDomain(): RelatorSessionPhase = when (this) {
    RelatorPhase.HIDDEN -> RelatorSessionPhase.HIDDEN
    RelatorPhase.MOBILITY -> RelatorSessionPhase.MOBILITY
    RelatorPhase.WARMUP -> RelatorSessionPhase.WARMUP
    RelatorPhase.WORKING -> RelatorSessionPhase.WORKING
    RelatorPhase.REST -> RelatorSessionPhase.REST
}

internal fun buildRelatorTissueHintFromLogs(
    state: WorkoutUiState,
    currentExercise: Exercise,
    visibleExercises: List<Exercise>,
    catalogIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    recentLogs: List<WorkoutLog>,
): RelatorTissueHint? {
    val todayMuscles = ExerciseMuscleResolver.effectiveMusclesForVolume(currentExercise, catalogIndex)
    val todayPrimary = todayMuscles.filter { it.role == MuscleRole.PRIMARY }.map { it.muscle }
    val todayStab = todayMuscles.filter { it.role == MuscleRole.STABILIZER }.map { it.muscle }
    val currentIdx = state.currentExerciseIdx
    val intra = visibleExercises.take(currentIdx.coerceAtLeast(0)).mapNotNull { prior ->
        val working = state.completedSets.filter { (key, set) ->
            val parsed = parseCompletedSetKey(key) ?: return@filter false
            parsed.exerciseId == prior.id && !set.isWarmup && !set.skipped
        }.values
        if (working.isEmpty()) return@mapNotNull null
        val muscles = ExerciseMuscleResolver.effectiveMusclesForVolume(prior, catalogIndex)
        RelatorPriorExercise(
            name = prior.name,
            primaryMuscles = muscles.filter { it.role == MuscleRole.PRIMARY }.map { it.muscle },
            secondaryMuscles = muscles.filter { it.role == MuscleRole.SECONDARY }.map { it.muscle },
            stabilizerMuscles = muscles.filter { it.role == MuscleRole.STABILIZER }.map { it.muscle },
            highIntensity = working.any { set ->
                set.isFailure || (set.rpe ?: 0.0) >= 8.0 || (set.rir ?: 99) <= 2
            },
        )
    }
    val yesterday = recentLogs.flatMap { log ->
        val drain = log.muscularImpactV2?.perMuscle?.mapValues { it.value.immediateDrainPct }.orEmpty()
        log.completedExercises.map { completed ->
            val muscles = completed.effectiveMuscles.orEmpty()
            RelatorPriorExercise(
                name = completed.exerciseName,
                primaryMuscles = muscles.filter { it.role == MuscleRole.PRIMARY }.map { it.muscle },
                secondaryMuscles = muscles.filter { it.role == MuscleRole.SECONDARY }.map { it.muscle },
                stabilizerMuscles = muscles.filter { it.role == MuscleRole.STABILIZER }.map { it.muscle },
                highIntensity = completed.sets.any { set ->
                    set.isFailure || (set.rpe ?: 0.0) >= 8.0 || (set.rir ?: 99) <= 2
                },
                drainByMuscle = drain,
            )
        }
    }
    return pickRelatorTissueHint(
        todayPrimaryMuscles = todayPrimary,
        todayStabilizers = todayStab,
        intraSession = intra,
        yesterday = yesterday,
    )
}

internal fun buildRelatorWarmupRows(
    members: List<Exercise>,
    uiState: WorkoutUiState,
): List<WarmupPhaseRow> {
    val rows = mutableListOf<WarmupPhaseRow>()
    var index = 0
    members.forEach { member ->
        val working = member.sets.firstOrNull()?.weight
            ?: uiState.completedSets["${member.id}_0"]?.weight
        member.warmupSets.forEach { warmup ->
            val key = WorkoutStepRules.warmupStepKey(member.id, warmup.id)
            val pct = if (warmup.percentageOfWorkingWeight > 1.0) {
                warmup.percentageOfWorkingWeight / 100.0
            } else {
                warmup.percentageOfWorkingWeight
            }
            val suggested = working?.let { base -> kotlin.math.round(base * pct / 2.5) * 2.5 }
            rows += WarmupPhaseRow(
                exerciseId = member.id,
                exerciseBadge = null,
                index = index++,
                warmup = warmup,
                suggestedWeightKg = suggested,
                actualWeightKg = uiState.completedSets[key]?.weight?.takeIf { it > 0.0 },
                isCompleted = member.id in uiState.warmupCompletedExerciseIds ||
                    key in uiState.warmupCompletedExerciseIds,
            )
        }
    }
    return rows
}
