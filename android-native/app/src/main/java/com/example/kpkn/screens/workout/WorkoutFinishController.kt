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
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.AugeMuscleCapacityEngine
import com.example.kpkn.domain.auge.MuscularSessionImpactEngine
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.concurrent.TimeoutException

/**
 * Guidance for the P0 empty-session guard: a session without recorded sets is
 * never saved.  The finish sheet shows it while the guard would block, so the
 * block is never silent.
 */
internal const val FINISH_EMPTY_SESSION_GUIDANCE =
    "Registra al menos una serie para terminar o abandona sin guardar."

/** Aviso cuando guardar falla por un error inesperado: nunca se muestra el texto técnico de la excepción. */
internal const val FINISH_SAVE_FAILED_MESSAGE =
    "No se pudo guardar la sesión. Tu entreno sigue en curso: inténtalo de nuevo."

/** Aviso cuando no se puede calcular el resumen de recuperación de la hoja final. */
internal const val FINISH_PREVIEW_FAILED_MESSAGE =
    "No se pudo calcular el estado muscular. Cierra e inténtalo de nuevo."

/** Errores del guardado que ya traen un texto en español pensado para la persona. */
private val FINISH_USER_FACING_MESSAGES = setOf(
    "La ejecución activa cambió; vuelve a abrir la sesión.",
    "No hay una sesión activa que conservar.",
)

/** Texto para la persona cuando guardar falla (mensaje conocido en español o el genérico). */
internal fun finishFailureMessage(error: Throwable): String =
    error.message?.takeIf { it in FINISH_USER_FACING_MESSAGES } ?: FINISH_SAVE_FAILED_MESSAGE

/** Single source of the predicate that makes [WorkoutFinishController.finish] abort. */
internal fun isFinishBlockedForEmptySession(completedExercises: List<CompletedExercise>): Boolean =
    completedExercises.isEmpty()

/** Message for the finish sheet, or null when the session has sets and can be saved. */
internal fun finishEmptySessionGuidance(completedExercises: List<CompletedExercise>): String? =
    FINISH_EMPTY_SESSION_GUIDANCE.takeIf { isFinishBlockedForEmptySession(completedExercises) }

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
    /** Aviso cuando finish aborta por sesión sin series (guard P0 de log hueco). */
    private val onEmptySession: () -> Unit = {},
    /** Test seam; production supplies WorkoutRecordingGate.awaitIdle. */
    private val awaitRecordingIdle: suspend (Long) -> Boolean = { true },
    private val persistOngoing: suspend () -> Unit = {},
    private val workoutMediaRepository: com.example.kpkn.data.repository.WorkoutMediaRepository? = null,
    private val mediaSessionKey: () -> String = { "" },
    private val clearActiveWorkout: () -> Unit = { ActiveWorkoutHolder.clear() },
) {
    fun finish(
        notes: String,
        fatigueLevel: Int,
        closingFeedback: SessionClosingFeedback,
        onComplete: () -> Unit = {},
        onFailure: (Exception) -> Unit = {},
    ) {
        val initialState = getState()
        if (!canStartWorkoutRecording(initialState) || initialState.session == null) return
        if (!initialState.logAlreadyWrittenId.isNullOrBlank()) {
            if (initialState.showVolumeAdvanceModal && initialState.pendingVolumeAdvances.isNotEmpty()) {
                updateState { it.copy(isFinishingWorkout = false, showFinishSheet = false) }
            } else {
                updateState { it.copy(isFinishingWorkout = false) }
            }
            return
        }
        updateState { it.copy(isFinishingWorkout = true, finishWarning = null) }

        scope.launch {
            var durableLogId: String? = null
            var retainedVolumeModal = false
            var committedStressScore = 0.0
            var committedVolumeAdvances: List<MuscleAdvance> = emptyList()
            var onCompleteAttempted = false
            try {
                if (!awaitRecordingIdle(10_000L)) {
                    val warning = "No pude cerrar la sesión porque una serie sigue grabándose. Reintentá cuando termine."
                    KpknDiagnosticLogger.event(
                        namespace = "workout",
                        name = "session_finish_blocked_recording_timeout",
                        fields = mapOf(
                            "programId" to programId,
                            "workoutSessionId" to sessionId,
                            "timeoutMs" to 10_000L,
                        ),
                        sessionId = sessionId,
                    )
                    updateState {
                        it.copy(
                            isFinishingWorkout = false,
                            showFinishSheet = true,
                            finishWarning = warning,
                        )
                    }
                    runCatching { onFailure(TimeoutException(warning)) }
                    return@launch
                }

                // Read the state only after the recorder is idle so the final
                // in-flight set is included and no new set can enter the log.
                val state = getState()
                val session = state.session ?: run {
                    updateState { it.copy(isFinishingWorkout = false) }
                    return@launch
                }
                val durationMs = System.currentTimeMillis() - state.startTimeMs
                val durationMinutes = (durationMs / 60000).toInt().coerceAtLeast(1)
                val activeSession = sessionForActiveMode(session, state.activeMode)
                val allExercises = activeSession.allExercises()
                val currentExerciseIndex = exerciseIndex()

                val completedExercises = state.archivedCompletedExercises + toCompletedExercises(
                    session = activeSession,
                    completedSets = state.completedSets,
                    skippedExerciseIds = state.skippedExerciseIds,
                    catalogIndex = currentExerciseIndex,
                    includeCardioDetails = true,
                )

        // Guard P0 de sesión vacía: sin series completadas no se persiste un log hueco
        // (drenaría 0 y taparía el problema); se aborta con feedback al usuario.
                if (isFinishBlockedForEmptySession(completedExercises)) {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "finish_blocked_empty_session",
                fields = mapOf(
                    "programId" to programId,
                    "workoutSessionId" to sessionId,
                    "reason" to "empty_session",
                ),
                sessionId = sessionId,
            )
            updateState { it.copy(isFinishingWorkout = false) }
            onEmptySession()
                    return@launch
                }

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

                val totalVolume = sessionTonnage(completedExercises)

                val logId = listOf(
            programId,
            sessionId,
            state.weekId.ifBlank { "noweek" },
            state.startTimeMs.toString(),
                ).joinToString("|")

                val finishSnapshot = state.finishResumeSnapshot
                val completionInstantIso = closingFeedback.completionInstantIso
                    ?: finishSnapshot?.completionInstantIso
                    ?: Instant.now().toString()
                val finishOperationId = closingFeedback.finishOperationId
                    ?: finishSnapshot?.finishOperationId
                    ?: "finish-$logId"
                val adaptiveCache = com.example.kpkn.data.repository.AugeRepository
                    .getInstance(appContext)
                    .getAdaptiveCache()
                val automaticImpact = withContext(Dispatchers.Default) {
                    val canonicalInput = MuscularSessionImpactEngine.fromCompletedExercises(
                        completedExercises = completedExercises,
                        completionInstantIso = completionInstantIso,
                        exerciseDb = currentExerciseIndex,
                        settings = repository.settings.value,
                        adaptiveCache = adaptiveCache,
                    )
                    val initial = MuscularSessionImpactEngine.evaluate(
                        input = canonicalInput,
                        exerciseDb = currentExerciseIndex,
                        settings = repository.settings.value,
                        adaptiveCache = adaptiveCache,
                    )
                    val capacities = AugeMuscleCapacityEngine.capacitiesFor(
                        muscles = initial.involvedVolumeMuscles,
                        history = repository.history.value,
                        settings = repository.settings.value,
                        exerciseDb = currentExerciseIndex,
                        completionInstantIso = completionInstantIso,
                        adaptiveCache = adaptiveCache,
                    )
                    MuscularSessionImpactEngine.evaluate(
                        input = canonicalInput,
                        exerciseDb = currentExerciseIndex,
                        settings = repository.settings.value,
                        adaptiveCache = adaptiveCache,
                        capacitiesAtCompletion = capacities,
                    )
                }.let { impact ->
                    val frozenHash = closingFeedback.completedSetInputHash
                        ?: finishSnapshot?.completedSetInputHash
                    if (frozenHash.isNullOrBlank()) impact else impact.copy(setInputHash = frozenHash)
                }
                KpknDiagnosticLogger.event(
                    namespace = "auge",
                    name = "session_input",
                    fields = mapOf(
                        "finishOperationId" to finishOperationId,
                        "completionInstantIso" to completionInstantIso,
                        "inputHash" to automaticImpact.setInputHash,
                        "exerciseCount" to completedExercises.size,
                        "setCount" to completedExercises.sumOf { it.sets.size },
                        "canonicalExerciseIds" to completedExercises.mapNotNull { it.canonicalExerciseId },
                        "involvedVolumeMuscles" to automaticImpact.involvedVolumeMuscles,
                    ),
                    sessionId = sessionId,
                )
                KpknDiagnosticLogger.event(
                    namespace = "auge",
                    name = "session_impact",
                    fields = mapOf(
                        "finishOperationId" to finishOperationId,
                        "inputHash" to automaticImpact.setInputHash,
                        "globalMuscularDrain" to automaticImpact.globalMuscularDrain,
                        "perMuscle" to automaticImpact.perMuscle.mapValues { (_, value) ->
                            mapOf(
                                "immediateDrainPct" to value.immediateDrainPct,
                                "stressUnits" to value.stressUnits,
                                "capacityAtCompletion" to value.capacityAtCompletion,
                                "directStressUnits" to value.directStressUnits,
                                "indirectStressUnits" to value.indirectStressUnits,
                            )
                        },
                    ),
                    sessionId = sessionId,
                )

                val stressScore = withContext(Dispatchers.Default) {
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

                val finalEnergySummary = TrainingEnergyEngine.estimateCompletedSession(
                    completedExercises = completedExercises,
                    settings = repository.settings.value,
                    postExerciseFeedback = state.postExerciseFeedbackByExerciseId,
                )
                val actualDate = runCatching {
                    com.example.kpkn.domain.time.ActivityLocalDate.formatIsoDate(
                        com.example.kpkn.domain.time.ActivityLocalDate.fromInstantIso(completionInstantIso),
                    )
                }.getOrNull()?.takeIf { it.length == 10 }
                    ?: completionInstantIso.take(10).takeIf { it.length == 10 }
                    ?: LocalDate.now().toString()
                val scheduledDate = scheduledDateForSession(state.weekId, session)
                val scheduleDeltaDays = scheduledDate
                    ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.parse(actualDate)).toInt() }.getOrNull() }

                val log = WorkoutLog(
                    id = logId,
                    programId = programId,
                    sessionId = sessionId,
                    sessionName = session.name,
                    date = completionInstantIso,
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
                    notes = notes.ifBlank { state.sessionNotes }.ifBlank { null },
                    totalVolume = totalVolume,
                    sessionStressScore = stressScore,
                    muscularImpactV2 = automaticImpact,
                    weekId = state.weekId,
                    macroIndex = state.macroIndex,
                    mesoIndex = state.mesoIndex,
                    clarityRating = closingFeedback.clarityRating,
                    environmentTags = closingFeedback.environmentTags,
                    planDeviations = state.planDeviations,
                    exerciseTags = state.exerciseTags,
                    exerciseTagIds = state.activeTagsByExercise.mapValues { (_, ids) -> ids.firstOrNull().orEmpty() }
                        .filterValues { it.isNotBlank() },
                    exerciseNotes = state.exerciseNotes,
                    exercisePhotos = state.exercisePhotos,
                    sessionMilestones = state.sessionMilestones,
                    sessionNotes = state.sessionNotes,
                    sessionSavedNotes = state.sessionSavedNotes,
                    sessionPhotos = state.sessionPhotos,
                    sessionChecklist = state.sessionChecklist,
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
                    ringStartSnapshot = closingFeedback.ringStartSnapshot?.let { snapshot ->
                        val capturedAt = runCatching { java.time.Instant.parse(snapshot.capturedAtIso).toEpochMilli() }.getOrNull()
                        val estimated = capturedAt?.let { instant ->
                            com.example.kpkn.domain.auge.InitialRecoveryEvidencePolicy.resolve(
                                com.example.kpkn.domain.auge.InitialRecoveryPolicyInput(
                                    evidence = repository.settings.value.initialRecoveryEvidence,
                                    nowMs = instant,
                                    workoutLogs = repository.history.value,
                                ),
                            ).isEstimated
                        } ?: false
                        snapshot.copy(isInitialEstimate = snapshot.isInitialEstimate || estimated)
                    },
                    stillPresentDiscomfortIds = (
                        closingFeedback.stillPresentDiscomfortIds +
                            state.postExerciseFeedbackByExerciseId.values.flatMap { it.stillPresentDiscomfortIds }
                        ).distinct(),
                ).normalizedIdentityFields()

                val volumeDeltas = if (state.volumeAdvanceHandled) {
                    emptyList()
                } else {
                    computeVolumeDelta(
                        plannedSession = session,
                        completedSets = state.completedSets,
                    )
                }
                val keepOngoingForVolume = shouldKeepOngoingForVolumeAdvance(
                    volumeDeltas = volumeDeltas,
                    volumeAdvanceHandled = state.volumeAdvanceHandled,
                )
                val retainOngoingForVolume = keepOngoingForVolume && state.programId.isNotBlank()
                retainedVolumeModal = retainOngoingForVolume
                committedStressScore = stressScore
                committedVolumeAdvances = volumeDeltas
                val mediaKey = mediaSessionKey().takeIf { it.isNotBlank() }
                withContext(NonCancellable) {
                    repository.finalizeWorkout(
                        log,
                        clearOngoing = !retainOngoingForVolume,
                        mediaSessionKey = mediaKey,
                        retainedOngoingTransform = if (retainOngoingForVolume) {
                            { ongoing ->
                                ongoing.copy(
                                    pendingVolumeAdvances = volumeDeltas,
                                    showVolumeAdvanceModal = true,
                                    showFinishSheet = false,
                                    finishResumeSnapshot = null,
                                    logAlreadyWrittenId = log.id,
                                )
                            }
                        } else {
                            null
                        },
                        expectedExecutionStartTimeMs = state.startTimeMs,
                    )
                    // Mark the commit before returning to the caller's cancellable context.
                    durableLogId = log.id
                }

                if (retainOngoingForVolume) {
                    runPostCommitSafely(log.id, "defer_volume_completion") { deferOnComplete(onComplete) }
                    updateState {
                        it.copy(
                            pendingVolumeAdvances = volumeDeltas,
                            showVolumeAdvanceModal = true,
                            showFinishSheet = false,
                            isFinishingWorkout = false,
                            finishResumeSnapshot = null,
                            logAlreadyWrittenId = log.id,
                        )
                    }
                } else {
                    // The log and ongoing clear are durable. Publish success before
                    // diagnostics, history, timer cleanup, or user callbacks run.
                    updateState {
                        it.copy(
                            isComplete = true,
                            showFinishSheet = false,
                            sessionStressScore = stressScore,
                            isFinishingWorkout = false,
                            finishResumeSnapshot = null,
                            logAlreadyWrittenId = log.id,
                        )
                    }
                }

                runPostCommitSafely(log.id, "post_persisted_diagnostics") {
                    KpknDiagnosticLogger.event(
                        namespace = "auge",
                        name = "post_persisted_auto",
                        fields = mapOf(
                            "finishOperationId" to finishOperationId,
                            "logId" to log.id,
                            "inputHash" to automaticImpact.setInputHash,
                            "completionInstantIso" to completionInstantIso,
                            "automaticMuscularDrain" to automaticImpact.globalMuscularDrain,
                        ),
                        sessionId = sessionId,
                    )
                    KpknDiagnosticLogger.event(
                        namespace = "workout",
                        name = "session_finished",
                        fields = mapOf(
                            "programId" to programId,
                            "workoutSessionId" to sessionId,
                            "logId" to log.id,
                            "completedExerciseCount" to log.completedExercises.size,
                            "completedSetCount" to log.completedExercises.sumOf { it.sets.size },
                            "setsDone" to log.completedExercises.sumOf { it.sets.size },
                            "setsPlanned" to activeSession.allExercises().sumOf { it.sets.size },
                            "durationMs" to durationMs,
                            "durationMinutes" to durationMinutes,
                            "totalVolume" to totalVolume,
                            "volumeKg" to totalVolume,
                            "savedLogId" to log.id,
                            "stressScore" to stressScore,
                            "finishOperationId" to finishOperationId,
                            "completionInstantIso" to completionInstantIso,
                            "inputHash" to automaticImpact.setInputHash,
                            "automaticMuscularDrain" to automaticImpact.globalMuscularDrain,
                            "involvedVolumeMuscles" to automaticImpact.involvedVolumeMuscles,
                        ),
                        sessionId = sessionId,
                    )
                }
                runPostCommitSafely(log.id, "prediction_bias") { updatePredictionBias(closingFeedback) }
                runPostCommitSafely(log.id, "cancel_rest_alerts") { restAlertManager.cancelRestAlerts() }
                runPostCommitSafely(log.id, "clear_rest_timer") { restTimer.clearActiveTimerId() }
                schedulePostCommitPersistence(log, activeSession, state)

                if (retainOngoingForVolume) return@launch

                runPostCommitSafely(log.id, "prepare_voice_diagnostics") { prepareVoiceDiagnosticExport() }
                runPostCommitSafely(log.id, "clear_active_workout") { clearActiveWorkout() }
                onCompleteAttempted = true
                runPostCommitSafely(log.id, "completion_callback") { onComplete() }
            } catch (error: Exception) {
                val savedLogId = durableLogId
                if (error is CancellationException && savedLogId == null) {
                    // The scope was cancelled (screen left / ViewModel cleared) before the Room
                    // commit. Nothing was saved and nothing failed: release the "finishing" latch so
                    // the next «Terminar» works, never show the cancellation text as a warning, and
                    // rethrow so structured concurrency keeps working.
                    KpknDiagnosticLogger.event(
                        namespace = "workout",
                        name = "session_finish_cancelled",
                        fields = mapOf(
                            "programId" to programId,
                            "workoutSessionId" to sessionId,
                            "exceptionType" to error.javaClass.name,
                        ),
                        sessionId = sessionId,
                    )
                    runCatching { updateState { it.copy(isFinishingWorkout = false) } }
                    throw error
                }
                if (savedLogId != null) {
                    // The Room commit already succeeded. Keep the terminal/modal
                    // state and never report an ancillary failure as a save failure.
                    reportPostCommitFailure(savedLogId, "finish_post_commit", error)
                    runCatching {
                        updateState {
                            if (retainedVolumeModal) {
                                it.copy(
                                    pendingVolumeAdvances = committedVolumeAdvances,
                                    showVolumeAdvanceModal = true,
                                    showFinishSheet = false,
                                    isFinishingWorkout = false,
                                    finishResumeSnapshot = null,
                                    logAlreadyWrittenId = savedLogId,
                                )
                            } else {
                                it.copy(
                                    isComplete = true,
                                    showFinishSheet = false,
                                    sessionStressScore = committedStressScore,
                                    isFinishingWorkout = false,
                                    finishResumeSnapshot = null,
                                    logAlreadyWrittenId = savedLogId,
                                )
                            }
                        }
                    }
                    if (!retainedVolumeModal) {
                        runPostCommitSafely(savedLogId, "clear_active_workout_recovery") { clearActiveWorkout() }
                        if (!onCompleteAttempted) {
                            onCompleteAttempted = true
                            runPostCommitSafely(savedLogId, "completion_callback_recovery") { onComplete() }
                        }
                    }
                    if (error is CancellationException) throw error
                    return@launch
                }
                error.printStackTrace()
                KpknDiagnosticLogger.event(
                    namespace = "workout",
                    name = "session_finish_failed",
                    fields = mapOf(
                        "programId" to programId,
                        "workoutSessionId" to sessionId,
                        "exceptionType" to error.javaClass.name,
                        "exceptionMessage" to error.message,
                    ),
                    sessionId = sessionId,
                )
                updateState {
                    it.copy(
                        isFinishingWorkout = false,
                        finishWarning = finishFailureMessage(error),
                    )
                }
                // (P0) El caller (p.ej. cierre por voz) puede avisar que el save falló.
                runCatching { onFailure(error) }
            }
        }
    }

    private fun schedulePostCommitPersistence(
        log: WorkoutLog,
        activeSession: Session,
        state: WorkoutUiState,
    ) {
        val contextualPerformance = state.contextualPerformanceCache.values.toList()
        val globalPerformance = state.globalPerformanceCache.values.toList()
        val feedbackSnapshot = state.postExerciseFeedbackByExerciseId.toMap()
        runCatching {
            repository.ongoingPersistenceScope.launch {
                runCatching {
                    com.example.kpkn.screens.sessioneditor.TrainedSessionVersionStore
                        .getInstance(appContext)
                        .maybeAppendAfterTraining(
                            sessionId = sessionId,
                            session = activeSession,
                            reason = "Sesión entrenada",
                        )
                }.onFailure { error -> reportPostCommitFailure(log.id, "trained_session_history", error) }

                runCatching {
                    contextualPerformance.forEach { repository.upsertContextPerformanceState(it) }
                    globalPerformance.forEach { repository.upsertGlobalPerformanceState(it) }
                    performanceRangeStore.persistFinishedSessionPerformance(
                        completedExercises = log.completedExercises,
                        sessionId = sessionId,
                        postExerciseFeedbackByExerciseId = feedbackSnapshot,
                    )
                }.onFailure { error -> reportPostCommitFailure(log.id, "performance_snapshots", error) }
            }
        }.onFailure { error -> reportPostCommitFailure(log.id, "enqueue_finish_persistence", error) }
    }

    private fun runPostCommitSafely(logId: String, operation: String, action: () -> Unit) {
        runCatching(action).onFailure { error -> reportPostCommitFailure(logId, operation, error) }
    }

    private fun reportPostCommitFailure(logId: String, operation: String, error: Throwable) {
        runCatching {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "session_finish_auxiliary_failed",
                fields = mapOf(
                    "programId" to programId,
                    "workoutSessionId" to sessionId,
                    "logId" to logId,
                    "operation" to operation,
                    "exceptionType" to error.javaClass.name,
                    "exceptionMessage" to error.message,
                ),
                sessionId = sessionId,
            )
        }
        runCatching {
            updateState { state ->
                if (state.finishWarning != null) state else state.copy(
                    finishWarning = "Entreno guardado, pero no se pudo completar una actualización secundaria.",
                )
            }
        }
    }

    fun computeVolumeDelta(
        plannedSession: Session,
        completedSets: Map<String, CompletedSet>,
    ): List<MuscleAdvance> {
        val state = getState()
        val baseline = state.plannedSessionBaseline ?: plannedSession
        val live = state.session ?: plannedSession
        return computeWorkoutVolumeDelta(
            programId = state.programId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            weekId = state.weekId,
            plannedSession = baseline,
            completedSets = completedSets,
            exerciseIndex = exerciseIndex(),
            repository = repository,
            liveSession = live,
        )
    }

    fun offerLiveVolumeAdvance() {
        val state = getState()
        if (state.showVolumeAdvanceModal || state.volumeAdvanceHandled) return
        val live = state.session ?: return
        val deltas = computeVolumeDelta(live, state.completedSets)
        if (deltas.isEmpty()) return
        updateState {
            it.copy(
                pendingVolumeAdvances = deltas,
                showVolumeAdvanceModal = true,
            )
        }
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

internal fun computeMuscleSetSurplus(
    plannedSession: Session,
    liveSession: Session,
    completedSets: Map<String, CompletedSet>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): Map<String, Double> {
    fun primaryMuscleIds(exercise: Exercise): List<String> {
        resolveCatalogExerciseInfoInIndex(
            index = exerciseIndex,
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
        ) ?: return emptyList()
        return ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
            .filter { it.role == MuscleRole.PRIMARY }
            .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
            .filter { it.isNotBlank() }
    }

    val plannedPerMuscle = mutableMapOf<String, Double>()
    for (exercise in plannedSession.allExercises()) {
        for (muscleId in primaryMuscleIds(exercise)) {
            plannedPerMuscle[muscleId] = (plannedPerMuscle[muscleId] ?: 0.0) + exercise.sets.size
        }
    }

    val completedByExercise = liveSession.allExercises().associate { exercise ->
        exercise.id to logicalWorkingSetCount(exercise, completedSets)
    }

    val actualPerMuscle = mutableMapOf<String, Double>()
    for (exercise in liveSession.allExercises()) {
        val sets = completedByExercise[exercise.id] ?: 0
        if (sets == 0) continue
        for (muscleId in primaryMuscleIds(exercise)) {
            actualPerMuscle[muscleId] = (actualPerMuscle[muscleId] ?: 0.0) + sets
        }
    }

    val surplus = mutableMapOf<String, Double>()
    (plannedPerMuscle.keys + actualPerMuscle.keys).forEach { muscleId ->
        val delta = (actualPerMuscle[muscleId] ?: 0.0) - (plannedPerMuscle[muscleId] ?: 0.0)
        if (delta > 0) surplus[muscleId] = delta
    }
    return surplus
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
    liveSession: Session = plannedSession,
): List<MuscleAdvance> {
    if (programId.isEmpty()) return emptyList()

    val surplusMuscles = computeMuscleSetSurplus(
        plannedSession = plannedSession,
        liveSession = liveSession,
        completedSets = completedSets,
        exerciseIndex = exerciseIndex,
    )
    if (surplusMuscles.isEmpty()) return emptyList()

    val program = repository.getProgramById(programId) ?: return emptyList()
    val week = program.macrocycles
        .getOrNull(macroIndex)?.blocks
        ?.flatMap { it.mesocycles }
        ?.getOrNull(mesoIndex)?.weeks
        ?.firstOrNull { it.id == weekId } ?: return emptyList()
    val weekSessions = week.sessions

    val nextSession = com.example.kpkn.domain.sessionassistant.SessionAssistantEngine.findNextSessionWithMuscles(
        currentSessionId = liveSession.id.ifBlank { plannedSession.id },
        weekSessions = weekSessions,
        muscleIds = surplusMuscles.keys.toList(),
        exerciseIndex = exerciseIndex,
    ) ?: return emptyList()

    return com.example.kpkn.domain.sessionassistant.SessionAssistantEngine.computeProposedDiscounts(
        currentSession = plannedSession,
        nextSession = nextSession,
        targetMuscles = surplusMuscles.keys.toList(),
        completedSets = completedSets,
        exerciseIndex = exerciseIndex,
        liveSession = liveSession,
    ).map { advance ->
        advance.copy(
            muscleName = VolumeCalculator.normalizeCanonicalMuscleGroup(advance.muscleId)
                .ifBlank { advance.muscleName.ifBlank { advance.muscleId } },
        )
    }
}

internal fun shouldKeepOngoingForVolumeAdvance(
    volumeDeltas: List<*>,
    volumeAdvanceHandled: Boolean,
): Boolean = !volumeAdvanceHandled && volumeDeltas.isNotEmpty()
