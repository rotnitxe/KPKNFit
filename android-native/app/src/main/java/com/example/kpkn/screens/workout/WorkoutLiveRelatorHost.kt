package com.example.kpkn.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.models.unresolvedDiscomfortIds
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.screens.workout.components.WarmupPhaseRow

@Composable
internal fun rememberLiveRelatorSnapshot(
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
    currentExercise: Exercise?,
    currentSet: ExerciseSet?,
    visibleExercises: List<Exercise>,
    headerExerciseName: String,
    catalogV2: ExerciseCatalogV2?,
    gender: Gender?,
    activeSide: String?,
    showingPostExerciseCard: Boolean,
    isMobilityActive: Boolean,
    isWarmupActive: Boolean,
    workingRestActive: Boolean,
    settledRelatorPhase: RelatorPhase?,
    warmupWeightDrafts: Map<String, String>,
    sessionTimeRemainingSeconds: Int? = null,
): LiveRelatorSnapshot {
    val assistAck by viewModel.relatorAssistAck.collectAsStateWithLifecycle()
    val setIdx = uiState.currentSetIdx
    val setIdentity = currentExercise?.let { workoutSetKey(it.id, setIdx, activeSide) }.orEmpty()
    var primed by remember(setIdentity) { mutableStateOf(false) }
    var changedField by remember(setIdentity) { mutableStateOf(RelatorChangedField.NONE) }
    var prevWeight by remember(setIdentity) { mutableStateOf<String?>(null) }
    var prevReps by remember(setIdentity) { mutableStateOf<String?>(null) }
    var prevIntensity by remember(setIdentity) { mutableStateOf<String?>(null) }
    var prevWarmupDraft by remember(setIdentity) { mutableStateOf("") }
    var prevMobilityDone by remember(setIdentity) { mutableIntStateOf(0) }
    var prevTimer by remember(setIdentity) { mutableStateOf(false) }
    var prevDropCount by remember(setIdentity) { mutableIntStateOf(0) }
    var prevFailure by remember(setIdentity) { mutableStateOf(false) }

    val hidden = showingPostExerciseCard || currentExercise == null || currentExercise.isCardio
    val inferredPhase = when {
        hidden -> RelatorPhase.HIDDEN
        settledRelatorPhase != null -> settledRelatorPhase
        isMobilityActive -> RelatorPhase.MOBILITY
        isWarmupActive -> RelatorPhase.WARMUP
        workingRestActive -> RelatorPhase.REST
        else -> RelatorPhase.WORKING
    }
    val groupId = currentExercise?.supersetGroupRefOrLegacyId()
    val groupMembers = groupId
        ?.let { id -> visibleExercises.filter { it.supersetGroupRefOrLegacyId() == id } }
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
    val warmupRows = remember(warmupMembers, uiState.warmupCompletedExerciseIds, uiState.completedSets) {
        buildRelatorWarmupRows(warmupMembers, uiState)
    }
    val firstIncompleteWarmup = warmupRows.firstOrNull { !it.isCompleted }
    val warmupIsLast = firstIncompleteWarmup != null && firstIncompleteWarmup.index == warmupRows.lastIndex
    val warmupKey = firstIncompleteWarmup?.let { "${it.exerciseId}_${it.warmup.id}" }
    val warmupRaw = warmupKey?.let { warmupWeightDrafts[it] }.orEmpty()
    val suggestedWarmupKg = firstIncompleteWarmup?.suggestedWeightKg

    val mobilityTotal = mobilityMembers.sumOf { it.mobilitySeries.size }
    val mobilityDone = mobilityMembers.sumOf { member ->
        member.mobilitySeries.count { mobility ->
            val key = WorkoutStepRules.mobilityStepKey(member.id, mobility.id, 0)
            key in uiState.mobilityCompletedExerciseIds || member.id in uiState.mobilityCompletedExerciseIds
        }
    }
    val timer = uiState.mobilityTotalTimerState
    val timerRunning = timer?.isRunning == true

    val draft = currentExercise?.let { viewModel.getSetDraft(it.id, setIdx, activeSide) }
    val weightText = draft?.weightText
    val repsText = draft?.valueText
    val intensityText = draft?.intensityText
    val liveDropCount = draft?.dropSetCount ?: 0
    val liveFailure = draft?.reachedFailure == true
    val detected = if (!primed) {
        RelatorChangedField.NONE
    } else {
        detectRelatorChangedField(
            phase = inferredPhase,
            draftDirty = draft?.isDirty == true,
            weightText = weightText,
            repsText = repsText,
            intensityText = intensityText,
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
            dropCount = liveDropCount,
            prevDropCount = prevDropCount,
            reachedFailure = liveFailure,
            prevReachedFailure = prevFailure,
        )
    }

    val exerciseContext = resolveRelatorExerciseContext(currentExercise, catalogV2)
    val suggestion = currentExercise?.let {
        viewModel.getWeightSuggestionWithAutoRegulation(
            it,
            setIdx,
            activeTag = uiState.exerciseTags[it.id],
            side = activeSide,
        )
    }
    val previousWeight = currentExercise?.let {
        viewModel.getPreviousSessionFirstSetWeight(it, uiState.exerciseTags[it.id])
    }
    val restPhase = inferredPhase == RelatorPhase.REST
    val sessionLastSet = currentExercise?.let {
        previousWorkingSetToday(
            completedSets = uiState.completedSets,
            exerciseId = it.id,
            currentSetIdx = setIdx,
            side = activeSide,
            restPhase = restPhase,
        )
    }
    val historyLastSet = remember(
        currentExercise?.id,
        currentExercise?.canonicalExerciseId,
        currentExercise?.name,
        uiState.exerciseTags[currentExercise?.id],
    ) {
        currentExercise
            ?.let { exercise ->
                viewModel.getExerciseHistory(
                    exercise,
                    limit = 1,
                    preferredTag = uiState.exerciseTags[exercise.id],
                ).firstOrNull()?.sets
            }
            ?.let(::firstWorkingSetMemory)
    }
    val loadAnchor = resolveRelatorLoadAnchor(
        currentSetIdx = setIdx,
        restPhase = restPhase,
        sessionPrevious = sessionLastSet,
        historyFirst = historyLastSet,
    )
    val enteredWeightRaw = when (detected) {
        RelatorChangedField.WARMUP_WEIGHT -> warmupRaw
        else -> weightText.orEmpty()
    }
    val enteredWeight = enteredWeightRaw.replace(',', '.').toDoubleOrNull()
    val suggestedWeight = when (detected) {
        RelatorChangedField.WARMUP_WEIGHT -> suggestedWarmupKg
        else -> suggestion?.suggestedWeight
    }
    val lastLiftedWeight = when (detected) {
        RelatorChangedField.WARMUP_WEIGHT -> suggestedWarmupKg
        else -> loadAnchor.compareWeightKg
    }
    val referenceWeight = suggestedWeight ?: lastLiftedWeight
    val loadKind = relatorLoadKind(draft?.loadMode ?: currentSet?.loadModeV2)
    val unit = relatorUnit(currentSet?.unitModeV2)
    val effectiveField = when {
        loadKind != RelatorLoadKind.LOAD && detected == RelatorChangedField.WEIGHT -> RelatorChangedField.NONE
        unit != RelatorUnit.REPS && detected == RelatorChangedField.REPS && unit != RelatorUnit.TIME ->
            RelatorChangedField.NONE
        else -> detected
    }
    val catalogIndex = catalogExerciseIndex()
    val tissueHint = remember(
        currentExercise?.id,
        uiState.currentExerciseIdx,
        uiState.completedSets.size,
        inferredPhase,
    ) {
        if (currentExercise == null || inferredPhase == RelatorPhase.HIDDEN) {
            null
        } else {
            buildRelatorTissueHint(
                uiState = uiState,
                viewModel = viewModel,
                currentExercise = currentExercise,
                visibleExercises = visibleExercises,
                catalogIndex = catalogIndex,
            )
        }
    }
    val sessionBestPrevious = currentExercise?.let {
        sessionBestPreviousE1rm(
            uiState = uiState,
            exerciseId = it.id,
            currentSetIdx = setIdx,
            side = activeSide,
            restPhase = restPhase,
        )
    } ?: 0.0
    val historyBestE1rm = remember(currentExercise?.id, currentExercise?.canonicalExerciseId, currentExercise?.name) {
        currentExercise?.let { viewModel.bestEstimated1RmForExercise(it) } ?: 0.0
    }
    val enteredRepsValue = repsText?.replace(',', '.')?.toDoubleOrNull()
    val prProbeWeight = if (inferredPhase == RelatorPhase.REST) sessionLastSet?.weightKg else enteredWeight
    val prProbeReps = if (inferredPhase == RelatorPhase.REST) {
        sessionLastSet?.reps
    } else {
        enteredRepsValue?.toInt()
    }
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
    val discomfortHint = remember(
        currentExercise?.id,
        uiState.postExerciseFeedbackByExerciseId,
        uiState.completedSets.size,
    ) {
        val exercise = currentExercise ?: return@remember null
        val same = thisSessionDiscomfortIds(uiState.postExerciseFeedbackByExerciseId[exercise.id])
        val others = uiState.postExerciseFeedbackByExerciseId.mapNotNull { (id, feedback) ->
            if (id == exercise.id) return@mapNotNull null
            val labels = discomfortLabelsFromIds(feedback.unresolvedDiscomfortIds())
            val label = labels.firstOrNull() ?: return@mapNotNull null
            val otherName = visibleExercises.find { it.id == id }?.name ?: feedback.exerciseName
            otherName to label
        }
        pickRelatorDiscomfortHint(
            sameExerciseThisSessionLabels = discomfortLabelsFromIds(same),
            otherThisSession = others,
            previousSessionLabels = discomfortLabelsFromIds(
                viewModel.latestDiscomfortIdsForExercise(exercise),
            ),
        )
    }

    SideEffect {
        primed = true
        changedField = detected
        prevWeight = weightText
        prevReps = repsText
        prevIntensity = intensityText
        prevWarmupDraft = warmupRaw
        prevMobilityDone = mobilityDone
        prevTimer = timerRunning
        prevDropCount = liveDropCount
        prevFailure = liveFailure
    }

    return LiveRelatorSnapshot(
        visible = !hidden,
        phase = inferredPhase,
        family = exerciseContext.family,
        feminine = gender == Gender.FEMALE,
        exerciseDisplayName = headerExerciseName,
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
        mobilityRemainingSeconds = timer?.remainingSeconds,
        lastChangedField = effectiveField,
        enteredWeight = enteredWeight,
        enteredWeightRaw = enteredWeightRaw,
        referenceWeight = referenceWeight,
        suggestedWeight = suggestedWeight,
        lastLiftedWeight = lastLiftedWeight,
        enteredReps = enteredRepsValue,
        plannedReps = currentSet?.targetReps?.toDouble()
            ?: currentSet?.plannedTargetV2
            ?: currentSet?.targetRepsRange?.max?.toDouble(),
        enteredIntensity = intensityText?.replace(',', '.')?.toDoubleOrNull(),
        plannedIntensity = currentSet?.targetRPE ?: currentSet?.targetRIR?.toDouble(),
        intensityMode = currentSet?.intensityMode,
        reachedFailure = liveFailure,
        plannedFailure = currentSet?.isFailure == true || currentSet?.intensityMode == IntensityMode.FAILURE,
        dropSetCount = liveDropCount,
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
        isDropsetFollowUp = currentSet?.isDropSet == true &&
            currentExercise?.sets?.getOrNull(setIdx - 1)?.restAfterSeconds == 0,
        failedSetCaution = currentExercise?.let {
            resolveFailedSetCaution(
                completedSets = uiState.completedSets,
                exerciseIds = visibleExercises.map { ex -> ex.id },
                currentExerciseId = it.id,
                currentSetIdx = setIdx,
                restPhase = restPhase,
            )
        },
        assistAck = assistAck,
        ultraFastApplied = uiState.ultraFastApplied,
        loadFromPreviousSession = loadAnchor.fromPreviousSession,
        axialLoadFactor = exerciseContext.axialLoadFactor,
        equipmentId = exerciseContext.equipmentId,
        movementPatternId = exerciseContext.movementPatternId,
        plannedIsoHold = currentSet?.plannedIntensityTechniques.orEmpty()
            .any { it.type == TechniqueType.ISO_HOLD },
        plannedNegatives = currentSet?.plannedIntensityTechniques.orEmpty()
            .any { it.type == TechniqueType.NEGATIVES },
        sessionSpeechKey = uiState.session?.id.orEmpty(),
        assistOffer = rememberAssistOffer(
            uiState = uiState,
            currentExercise = currentExercise,
            visibleExercises = visibleExercises,
            inferredPhase = inferredPhase,
            family = exerciseContext.family,
            activeSide = activeSide,
            sessionTimeRemainingSeconds = sessionTimeRemainingSeconds,
        ),
    )
}

@Composable
private fun rememberAssistOffer(
    uiState: WorkoutUiState,
    currentExercise: Exercise?,
    visibleExercises: List<Exercise>,
    inferredPhase: RelatorPhase,
    family: RelatorFamily,
    activeSide: String?,
    sessionTimeRemainingSeconds: Int?,
): RelatorAssistOffer? {
    val skippedIds = uiState.skippedExerciseIds
    val sessionExercises = remember(visibleExercises, skippedIds, uiState.session) {
        val all = uiState.session?.allExercises().orEmpty()
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
        ordered.map { exercise ->
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
    }
    val completedKeys = uiState.completedSets.keys
    val omittedKeys = uiState.omittedSetKeys
    val currentId = currentExercise?.id.orEmpty()
    val currentIndex = sessionExercises.indexOfFirst { it.id == currentId }
    return remember(
        inferredPhase,
        family,
        currentId,
        uiState.currentSetIdx,
        currentIndex,
        activeSide,
        completedKeys,
        omittedKeys,
        skippedIds,
        sessionTimeRemainingSeconds,
        sessionExercises,
        uiState.ultraFastApplied,
    ) {
        pickRelatorAssistOffer(
            RelatorAssistContext(
                phase = inferredPhase,
                family = family,
                currentExerciseId = currentId,
                currentExerciseName = currentExercise?.name.orEmpty(),
                currentSetIndex = uiState.currentSetIdx,
                currentExerciseIndex = currentIndex,
                activeSide = activeSide,
                sessionExercises = sessionExercises,
                completedSetKeys = completedKeys,
                omittedSetKeys = omittedKeys,
                skippedExerciseIds = skippedIds,
                remainingSeconds = sessionTimeRemainingSeconds,
                ultraFastApplied = uiState.ultraFastApplied,
            ),
        )
    }
}

internal fun plannedDropSetCount(set: ExerciseSet?): Int {
    val planned = set?.plannedIntensityTechniques?.firstOrNull { it.type == TechniqueType.DROP_SET }
    if (planned != null) {
        return (planned.params["count"]?.toIntOrNull() ?: 1).coerceIn(1, 3)
    }
    return set?.dropSets?.size ?: 0
}

internal fun lastLiftedWeightToday(
    uiState: WorkoutUiState,
    exerciseId: String,
    currentSetIdx: Int,
    side: String?,
): Double? = previousWorkingSetToday(
    completedSets = uiState.completedSets,
    exerciseId = exerciseId,
    currentSetIdx = currentSetIdx,
    side = side,
    restPhase = false,
)?.weightKg

internal fun lastCompletedWorkingSetToday(
    uiState: WorkoutUiState,
    exerciseId: String,
    currentSetIdx: Int,
    side: String?,
): RelatorSessionSetMemory? = previousWorkingSetToday(
    completedSets = uiState.completedSets,
    exerciseId = exerciseId,
    currentSetIdx = currentSetIdx,
    side = side,
    restPhase = false,
)

internal fun sessionBestPreviousE1rm(
    uiState: WorkoutUiState,
    exerciseId: String,
    currentSetIdx: Int,
    side: String?,
    restPhase: Boolean,
): Double {
    val values = uiState.completedSets.mapNotNull { (key, set) ->
        val parsed = parseCompletedSetKey(key) ?: return@mapNotNull null
        if (parsed.exerciseId != exerciseId) return@mapNotNull null
        if (set.isWarmup || set.skipped || set.weight <= 0.0 || set.reps <= 0) return@mapNotNull null
        if (side != null && parsed.side != null && parsed.side != side) return@mapNotNull null
        if (!restPhase && parsed.setIdx >= currentSetIdx) return@mapNotNull null
        parsed.setIdx to calculateHybrid1RM(set.weight, set.reps)
    }.sortedByDescending { it.first }
    return if (restPhase) {
        values.drop(1).maxOfOrNull { it.second } ?: 0.0
    } else {
        values.maxOfOrNull { it.second } ?: 0.0
    }
}

internal fun buildRelatorTissueHint(
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
    currentExercise: Exercise,
    visibleExercises: List<Exercise>,
    catalogIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
): RelatorTissueHint? {
    val todayMuscles = ExerciseMuscleResolver.effectiveMusclesForVolume(currentExercise, catalogIndex)
    val todayPrimary = todayMuscles.filter { it.role == MuscleRole.PRIMARY }.map { it.muscle }
    val todayStab = todayMuscles.filter { it.role == MuscleRole.STABILIZER }.map { it.muscle }
    val currentIdx = uiState.currentExerciseIdx
    val intra = visibleExercises.take(currentIdx.coerceAtLeast(0)).mapNotNull { prior ->
        val working = uiState.completedSets.filter { (key, set) ->
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
    val yesterday = viewModel.recentWorkoutLogs(36).flatMap { log ->
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

private fun buildRelatorWarmupRows(
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
