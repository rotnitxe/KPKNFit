package com.example.kpkn.screens.workout

import android.content.Context
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.voice.VoiceState
import com.example.kpkn.services.workout.VoiceSessionCommand
import com.example.kpkn.services.workout.VoicePipelineStage
import com.example.kpkn.services.workout.VoiceSessionState
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.WorkoutVoiceForegroundService
import com.example.kpkn.services.workout.WorkoutVoicePermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Voice input (transcript) and continuous voice-session command handling.
 */
class WorkoutVoiceCommandHandler(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val voiceRecognizer: WorkoutVoiceRecognizer,
    private val voiceController: WorkoutVoiceController,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun workoutStepPositions(state: WorkoutUiState): List<WorkoutStep>
        fun getSetDraft(exerciseId: String, setIdx: Int, side: String?): WorkoutSetDraft?
        fun updateSetDraft(exerciseId: String, setIdx: Int, side: String?, draft: WorkoutSetDraft)
        fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?)
        fun getWeightSuggestionWithAutoRegulation(
            exercise: Exercise,
            setIdx: Int,
            activeTag: String? = null,
            side: String? = null,
        ): WeightSuggestion?
        fun restSecondsRemaining(): Int?
        fun canonicalExerciseKey(exercise: Exercise): String
        suspend fun recordSetV2(
            weight: Double,
            value: Double,
            intensity: Double?,
            advanced: SetAdvancedFeedback = SetAdvancedFeedback(),
            side: String? = null,
        )
        fun skipSet()
        fun skipRemainingCurrentExercise()
        fun prevSet()
        fun finishUpToCurrentPoint()
        fun cancelWorkout()
        fun savePostExerciseFeedback(feedback: PostExerciseFeedback)
        fun savePostExerciseFeedbacks(feedbacks: List<PostExerciseFeedback>)
        fun addSetToCurrentExercise()
        fun commitStructuralPersistenceSessionOnly()
        fun commitStructuralPersistencePermanent()
        fun clearPendingStructuralPersistence()
    }

    private var voiceJob: Job? = null

    fun startVoiceInput(
        exerciseId: String,
        setIdx: Int,
        side: String?,
        isTimeMode: Boolean,
        isUnilateral: Boolean,
    ) {
        voiceJob?.cancel()
        updateState {
            it.copy(
                voiceUiState = WorkoutVoiceUiState.Listening(
                    exerciseId = exerciseId,
                    setIdx = setIdx,
                    side = side,
                )
            )
        }
        voiceJob = scope.launch {
            voiceRecognizer.recognize().collect { state ->
                when (state) {
                    is VoiceState.Ready -> {
                        updateState { current ->
                            val listening = current.voiceUiState as? WorkoutVoiceUiState.Listening
                            if (listening == null || listening.exerciseId != exerciseId || listening.setIdx != setIdx || listening.side != side) {
                                current
                            } else {
                                current.copy(voiceUiState = listening.copy(isReady = true))
                            }
                        }
                    }

                    is VoiceState.Partial -> {
                        updateState { current ->
                            val listening = current.voiceUiState as? WorkoutVoiceUiState.Listening
                            if (listening == null || listening.exerciseId != exerciseId || listening.setIdx != setIdx || listening.side != side) {
                                current
                            } else {
                                current.copy(voiceUiState = listening.copy(partialText = state.text))
                            }
                        }
                    }

                    is VoiceState.Final -> {
                        val interpretation = parseWorkoutVoiceTranscript(
                            transcript = state.text,
                            isTimeMode = isTimeMode,
                            isUnilateral = isUnilateral,
                        )
                        updateState { current ->
                            if (interpretation == null) {
                                current.copy(
                                    voiceUiState = WorkoutVoiceUiState.Error(
                                        exerciseId = exerciseId,
                                        setIdx = setIdx,
                                        side = side,
                                        message = "No pude extraer datos utiles. Intenta con carga, reps, intensidad o lado.",
                                    )
                                )
                            } else {
                                current.copy(
                                    voiceUiState = WorkoutVoiceUiState.Confirmation(
                                        exerciseId = exerciseId,
                                        setIdx = setIdx,
                                        side = side,
                                        interpretation = interpretation,
                                    )
                                )
                            }
                        }
                    }

                    is VoiceState.Error -> {
                        updateState {
                            it.copy(
                                voiceUiState = WorkoutVoiceUiState.Error(
                                    exerciseId = exerciseId,
                                    setIdx = setIdx,
                                    side = side,
                                    message = state.message,
                                )
                            )
                        }
                    }

                    VoiceState.Unavailable -> {
                        updateState {
                            it.copy(
                                voiceUiState = WorkoutVoiceUiState.Error(
                                    exerciseId = exerciseId,
                                    setIdx = setIdx,
                                    side = side,
                                    message = "Este dispositivo no tiene reconocimiento de voz disponible.",
                                )
                            )
                        }
                    }

                    VoiceState.Done -> Unit
                }
            }
        }
    }

    fun cancelVoiceInput() {
        voiceJob?.cancel()
        voiceJob = null
        updateState { it.copy(voiceUiState = WorkoutVoiceUiState.Idle) }
    }

    fun showVoiceError(
        exerciseId: String,
        setIdx: Int,
        side: String?,
        message: String,
    ) {
        voiceJob?.cancel()
        voiceJob = null
        updateState {
            it.copy(
                voiceUiState = WorkoutVoiceUiState.Error(
                    exerciseId = exerciseId,
                    setIdx = setIdx,
                    side = side,
                    message = message,
                )
            )
        }
    }

    fun consumeVoiceAppliedMessage(
        exerciseId: String,
        setIdx: Int,
        side: String?,
    ) {
        val state = getState().voiceUiState as? WorkoutVoiceUiState.Applied ?: return
        if (state.exerciseId == exerciseId && state.setIdx == setIdx && state.side == side) {
            updateState { it.copy(voiceUiState = WorkoutVoiceUiState.Idle) }
        }
    }

    fun confirmVoiceInput(
        exerciseId: String,
        setIdx: Int,
        side: String?,
        isTimeMode: Boolean,
        baseIntensityMode: IntensityMode?,
    ) {
        val confirmation = getState().voiceUiState as? WorkoutVoiceUiState.Confirmation ?: return
        if (confirmation.exerciseId != exerciseId || confirmation.setIdx != setIdx || confirmation.side != side) return

        val draft = ports.getSetDraft(exerciseId, setIdx, side) ?: WorkoutSetDraft(selectedSide = side)
        val interpretation = confirmation.interpretation
        val resolvedSide = interpretation.side ?: side ?: draft.selectedSide
        val nextDraft = draft.copy(
            weightText = interpretation.weightKg?.toTrimmedNumberString() ?: draft.weightText,
            valueText = interpretation.metricValue?.toString() ?: draft.valueText,
            intensityText = workoutVoiceIntensityText(interpretation, baseIntensityMode).ifBlank { draft.intensityText.orEmpty() },
            selectedSide = resolvedSide,
            reachedFailure = if (WorkoutVoiceField.FAILURE in interpretation.fields) interpretation.reachedFailure else draft.reachedFailure,
            voiceFields = interpretation.fields,
            isDirty = true,
        )
        if (resolvedSide != side) {
            ports.clearDraftForSet(exerciseId, setIdx, side)
        }
        ports.updateSetDraft(exerciseId, setIdx, resolvedSide, nextDraft)
        updateState {
            it.copy(
                voiceUiState = WorkoutVoiceUiState.Applied(
                    exerciseId = exerciseId,
                    setIdx = setIdx,
                    side = resolvedSide,
                    interpretation = interpretation,
                    message = workoutVoiceAppliedMessage(interpretation, isTimeMode),
                )
            )
        }
    }

    fun toggleVoiceSession() {
        if (getState().voiceSessionEnabled) {
            disableVoice()
        } else {
            enableVoice()
        }
    }

    fun enableVoice() {
        val capability = WorkoutVoicePermissionHelper.checkVoiceCapability(appContext)
        if (!capability.canUseVoice) {
            updateState {
                it.copy(
                    voiceSessionEnabled = false,
                    voiceSessionState = VoiceSessionState(
                        stage = VoicePipelineStage.ERROR_RECOVERY,
                        errorMessage = capability.blockingReason
                            ?: "Reconocimiento de voz no disponible",
                    ),
                )
            }
            return
        }
        voiceController.enable()
        val controllerState = voiceController.state.value
        if (!voiceController.isEnabled() ||
            controllerState.stage == VoicePipelineStage.DISABLED
        ) {
            updateState {
                it.copy(
                    voiceSessionEnabled = false,
                    voiceSessionState = controllerState.copy(
                        stage = VoicePipelineStage.ERROR_RECOVERY,
                        errorMessage = controllerState.errorMessage
                            ?: "No se pudo activar el control por voz",
                    ),
                )
            }
            return
        }
        WorkoutVoiceForegroundService.start(appContext)
        updateState {
            it.copy(
                voiceSessionEnabled = true,
                voiceSessionState = controllerState,
            )
        }
    }

    fun disableVoice() {
        voiceController.disable()
        updateState {
            it.copy(
                voiceSessionEnabled = false,
                voiceSessionState = voiceController.state.value,
            )
        }
        WorkoutVoiceForegroundService.stop(appContext)
    }

    /**
     * Called when the activity goes to background. Does NOT disable voice —
     * the microphone FGS keeps the session alive for hands-free logging.
     */
    fun onVoiceHostPaused() {
        // Intentionally no-op for disable. Engine continues under FGS.
    }

    fun onVoiceHostResumed() {
        if (!getState().voiceSessionEnabled) return
        if (!voiceController.isEnabled()) {
            enableVoice()
        } else if (voiceController.getStage() == VoicePipelineStage.ERROR_RECOVERY) {
            voiceController.enable()
            updateState { it.copy(voiceSessionState = voiceController.state.value) }
        }
    }

    fun handleVoiceCommand(command: VoiceSessionCommand) {
        updateState { it.copy(voiceSessionState = voiceController.state.value) }

        when (command) {
            is VoiceSessionCommand.RegisterSet -> handleVoiceRegisterSet(command.interpretation)
            is VoiceSessionCommand.Confirm -> handleVoiceConfirmSet()
            is VoiceSessionCommand.Cancel -> handleVoiceCancelSet()
            is VoiceSessionCommand.SkipExercise -> handleVoiceSkipExercise()
            is VoiceSessionCommand.SkipSet -> ports.skipSet()
            is VoiceSessionCommand.PreviousExercise -> handleVoicePreviousExercise()
            is VoiceSessionCommand.SuggestWeight -> handleVoiceSuggestWeight()
            is VoiceSessionCommand.RestStatus -> handleVoiceRestStatus()
            is VoiceSessionCommand.WhatExercise -> handleVoiceWhatExercise()
            is VoiceSessionCommand.NextExercise -> handleVoiceNextExercise()
            is VoiceSessionCommand.TurnOffVoice -> disableVoice()
            is VoiceSessionCommand.FinishSession -> ports.finishUpToCurrentPoint()
            is VoiceSessionCommand.CancelSession -> ports.cancelWorkout()
            is VoiceSessionCommand.LogFeedback -> handleVoiceLogFeedback(command)
            is VoiceSessionCommand.LogFinalFeedback -> handleVoiceLogFinalFeedback(command)
            is VoiceSessionCommand.AddSet -> ports.addSetToCurrentExercise()
            is VoiceSessionCommand.AddSetSessionOnly -> {
                ports.commitStructuralPersistenceSessionOnly()
            }
            is VoiceSessionCommand.AddSetPermanent -> {
                ports.commitStructuralPersistencePermanent()
            }
            is VoiceSessionCommand.Unknown -> { /* no-op */ }
        }
    }

    fun speakCurrentStepAnnouncementIfEnabled() {
        if (voiceController.isEnabled() && !getState().isRestTimerRunning) {
            val updatedState = getState()
            val exercises = ports.visibleExercises(updatedState)
            val nextEx = exercises.getOrNull(updatedState.currentExerciseIdx)
            if (nextEx != null) {
                val step = updatedState.activeStepKey?.let { key ->
                    ports.workoutStepPositions(updatedState).firstOrNull { it.stepKey == key }
                }
                val round = step?.supersetRoundIndex?.let { it + 1 }
                voiceController.speakCurrentExercise(nextEx.name, updatedState.currentSetIdx + 1, nextEx.sets.size, round = round)
            }
        }
    }

    private fun handleVoiceLogFeedback(command: VoiceSessionCommand.LogFeedback) {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val exercise = allExercises.getOrNull(state.postExerciseTargetIdx) ?: return

        val target = state.postExerciseFeedbackTarget
        val targetExercise = if (target is PostExerciseFeedbackTarget.SupersetGroup) {
            val transcriptNormalized = command.exerciseSearchName.orEmpty()
            val matchedId = target.exerciseIds.firstOrNull { exerciseId ->
                val memberEx = allExercises.firstOrNull { it.id == exerciseId }
                if (memberEx != null) {
                    val normalizedName = java.text.Normalizer.normalize(memberEx.name.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                        .replace("\\p{Mn}+".toRegex(), "")
                        .trim()
                    normalizedName.isNotBlank() && transcriptNormalized.contains(normalizedName)
                } else false
            }
            val finalId = matchedId ?: target.missingFeedbackExerciseIds(state).firstOrNull() ?: target.exerciseIds.first()
            allExercises.firstOrNull { it.id == finalId } ?: exercise
        } else {
            exercise
        }

        var currentFeedback = state.postExerciseFeedbackByExerciseId[targetExercise.id] ?: PostExerciseFeedback(
            exerciseId = targetExercise.id,
            exerciseDbId = ports.canonicalExerciseKey(targetExercise),
            canonicalExerciseId = targetExercise.canonicalExerciseId ?: ports.canonicalExerciseKey(targetExercise),
            exerciseName = targetExercise.name,
            technicalQuality = 8,
            discomfortIds = emptyList(),
            perceivedIntensityRpe = null
        )

        if (command.isSaveAction) {
            if (target is PostExerciseFeedbackTarget.SupersetGroup) {
                val feedbacksToSave = target.exerciseIds.map { exerciseId ->
                    val memberEx = allExercises.firstOrNull { it.id == exerciseId }
                    state.postExerciseFeedbackByExerciseId[exerciseId] ?: PostExerciseFeedback(
                        exerciseId = exerciseId,
                        exerciseDbId = memberEx?.let { ports.canonicalExerciseKey(it) } ?: "",
                        canonicalExerciseId = memberEx?.canonicalExerciseId ?: memberEx?.let { ports.canonicalExerciseKey(it) } ?: "",
                        exerciseName = memberEx?.name ?: "",
                        technicalQuality = 8,
                        discomfortIds = emptyList(),
                        perceivedIntensityRpe = null
                    )
                }
                ports.savePostExerciseFeedbacks(feedbacksToSave)
            } else {
                ports.savePostExerciseFeedback(currentFeedback)
            }
            voiceController.speakFeedbackSaved()
            return
        }

        val updates = mutableListOf<String>()

        if (command.technicalQuality != null) {
            currentFeedback = currentFeedback.copy(technicalQuality = command.technicalQuality)
            updates.add("Calidad técnica fijada en ${command.technicalQuality}")
        }

        if (command.perceivedIntensity != null) {
            currentFeedback = currentFeedback.copy(perceivedIntensityRpe = command.perceivedIntensity)
            updates.add("Intensidad en RPE ${command.perceivedIntensity}")
        }

        if (command.discomfortId != null) {
            val nextDiscomforts = if (command.discomfortId == "none") {
                emptyList()
            } else {
                (currentFeedback.discomfortIds + command.discomfortId).distinct()
            }
            currentFeedback = currentFeedback.copy(discomfortIds = nextDiscomforts)
            val jointLabel = discomfortLabel(command.discomfortId)
            updates.add(if (command.discomfortId == "none") "Sin molestias" else "Molestia en $jointLabel agregada")
        }

        if (updates.isNotEmpty()) {
            updateState {
                it.copy(
                    postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId + (targetExercise.id to currentFeedback)
                )
            }
            val targetLabel = if (target is PostExerciseFeedbackTarget.SupersetGroup) "${targetExercise.name}: " else ""
            voiceController.speakFeedbackUpdated("$targetLabel${updates.joinToString(", ")}")
        }
    }

    private fun handleVoiceLogFinalFeedback(command: VoiceSessionCommand.LogFinalFeedback) {
        val state = getState()
        if (command.isSaveAction) {
            updateState {
                it.copy(
                    voiceFinalConfirmTriggered = true
                )
            }
            voiceController.speakSessionSaved()
            return
        }

        val updates = mutableListOf<String>()
        var nextNotes = state.voiceFinalNotes
        var nextAdditionalNote = state.voiceFinalAdditionalDiscomfortNote
        var nextNeural = state.voiceFinalNeural
        var nextSpinal = state.voiceFinalSpinal
        var nextDiscomforts = state.voiceFinalDiscomforts

        if (command.notes != null) {
            nextNotes = command.notes
            updates.add("Nota de sesión actualizada")
        }

        if (command.additionalDiscomfortNote != null) {
            nextAdditionalNote = command.additionalDiscomfortNote
            updates.add("Detalles de molestia actualizados")
        }

        if (command.neuralBattery != null) {
            nextNeural = command.neuralBattery
            updates.add("Batería nerviosa en ${command.neuralBattery}")
        }

        if (command.spinalBattery != null) {
            nextSpinal = command.spinalBattery
            updates.add("Batería espinal en ${command.spinalBattery}")
        }

        if (command.discomfortId != null) {
            nextDiscomforts = if (command.discomfortId == "none") {
                emptyList()
            } else {
                (nextDiscomforts + command.discomfortId).distinct()
            }
            val jointLabel = discomfortLabel(command.discomfortId)
            updates.add(if (command.discomfortId == "none") "Sin molestias finales" else "Molestia en $jointLabel agregada")
        }

        if (updates.isNotEmpty()) {
            updateState {
                it.copy(
                    voiceFinalNotes = nextNotes,
                    voiceFinalAdditionalDiscomfortNote = nextAdditionalNote,
                    voiceFinalNeural = nextNeural,
                    voiceFinalSpinal = nextSpinal,
                    voiceFinalDiscomforts = nextDiscomforts
                )
            }
            voiceController.speakFeedbackUpdated(updates.joinToString(", "))
        }
    }

    private fun discomfortLabel(discomfortId: String): String = when (discomfortId) {
        "shoulder_anterior" -> "hombro"
        "knee_patellar" -> "rodilla"
        "elbow_lateral" -> "codo"
        "lower_back" -> "espalda baja"
        "wrist" -> "muñeca"
        "hip" -> "cadera"
        "ankle" -> "tobillo"
        else -> "articulación"
    }

    private fun handleVoiceRegisterSet(interpretation: WorkoutVoiceInterpretation) {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val setIdx = state.currentSetIdx
        val side = if (exercise.isEffectivelyUnilateral()) interpretation.side else null

        val isTimeMode = exercise.trainingMode == TrainingMode.TIME
        val baseIntensityMode = exercise.sets.getOrNull(setIdx)?.intensityMode

        val draft = ports.getSetDraft(exercise.id, setIdx, side) ?: WorkoutSetDraft(selectedSide = side)
        val resolvedSide = interpretation.side ?: side ?: draft.selectedSide
        val nextDraft = draft.copy(
            weightText = interpretation.weightKg?.toTrimmedNumberString() ?: draft.weightText,
            valueText = interpretation.metricValue?.toString() ?: draft.valueText,
            intensityText = workoutVoiceIntensityText(interpretation, baseIntensityMode).ifBlank { draft.intensityText.orEmpty() },
            selectedSide = resolvedSide,
            reachedFailure = if (WorkoutVoiceField.FAILURE in interpretation.fields) interpretation.reachedFailure else draft.reachedFailure,
            voiceFields = interpretation.fields,
            isDirty = true,
        )
        if (resolvedSide != side) {
            ports.clearDraftForSet(exercise.id, setIdx, side)
        }
        ports.updateSetDraft(exercise.id, setIdx, resolvedSide, nextDraft)
        updateState {
            it.copy(
                voiceSessionState = voiceController.state.value,
                voiceUiState = WorkoutVoiceUiState.Applied(
                    exerciseId = exercise.id,
                    setIdx = setIdx,
                    side = resolvedSide,
                    interpretation = interpretation,
                    message = workoutVoiceAppliedMessage(interpretation, isTimeMode),
                ),
            )
        }

        scope.launch {
            val weight = interpretation.weightKg
            val reps = interpretation.metricValue
            if (weight == null || weight <= 0.0 || reps == null || reps <= 0) {
                return@launch
            }
            val intensity = interpretation.intensityValue

            ports.recordSetV2(
                weight = weight,
                value = reps.toDouble(),
                intensity = intensity,
                advanced = buildVoiceAdvancedFeedback(interpretation),
                side = resolvedSide,
            )
        }
    }

    private fun handleVoiceConfirmSet() {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val interpretation = voiceController.state.value.lastInterpretation ?: return

        scope.launch {
            val weight = interpretation.weightKg
            val reps = interpretation.metricValue
            if (weight == null || weight <= 0.0 || reps == null || reps <= 0) {
                return@launch
            }
            val intensity = interpretation.intensityValue

            ports.recordSetV2(
                weight = weight,
                value = reps.toDouble(),
                intensity = intensity,
                advanced = buildVoiceAdvancedFeedback(interpretation),
                side = interpretation.side,
            )

            updateState {
                it.copy(
                    voiceSessionState = voiceController.state.value,
                )
            }
        }
    }

    private fun buildVoiceAdvancedFeedback(interpretation: WorkoutVoiceInterpretation): SetAdvancedFeedback {
        val reachedFailure = interpretation.reachedFailure
        val intensity = interpretation.intensityValue
        val intensityMode = when {
            reachedFailure -> IntensityMode.FAILURE
            interpretation.intensityKind == WorkoutVoiceIntensityKind.RPE -> IntensityMode.RPE
            interpretation.intensityKind == WorkoutVoiceIntensityKind.RIR -> IntensityMode.RIR
            else -> null
        }
        return SetAdvancedFeedback(
            reachedFailure = reachedFailure,
            actualIntensityMode = intensityMode,
            actualIntensityValue = if (!reachedFailure) intensity else null,
            rir = if (intensityMode == IntensityMode.RIR) intensity?.toInt() else null,
        )
    }

    private fun handleVoiceCancelSet() {
        updateState {
            it.copy(voiceSessionState = voiceController.state.value)
        }
    }

    private fun handleVoiceSkipExercise() {
        ports.skipRemainingCurrentExercise()
        updateState { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoicePreviousExercise() {
        ports.prevSet()
        updateState { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceSuggestWeight() {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val suggestion = ports.getWeightSuggestionWithAutoRegulation(
            exercise = exercise,
            setIdx = state.currentSetIdx,
            activeTag = state.exerciseTags[exercise.id],
            side = null,
        )
        val weight = suggestion?.suggestedWeight
        if (weight != null && weight > 0.0) {
            voiceController.speakSuggestedWeight(exercise.name, weight)
        }
        updateState { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceRestStatus() {
        val remaining = ports.restSecondsRemaining() ?: 0
        if (remaining > 0) {
            voiceController.speakRestRemaining(remaining)
        }
        updateState { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceWhatExercise() {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val setNum = state.currentSetIdx + 1
        val totalSets = exercise.sets.size

        scope.launch {
            voiceController.speakCurrentExercise(exercise.name, setNum, totalSets)
        }
        updateState { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceNextExercise() {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val nextEx = allExercises.getOrNull(state.currentExerciseIdx + 1)
        if (nextEx != null) {
            scope.launch {
                voiceController.speakNextExercise(nextEx.name)
            }
        }
        updateState { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun PostExerciseFeedbackTarget.missingFeedbackExerciseIds(
        state: WorkoutUiState,
    ): List<String> {
        val targetIds = when (this) {
            is PostExerciseFeedbackTarget.Single -> listOf(exerciseId)
            is PostExerciseFeedbackTarget.SupersetGroup -> exerciseIds
        }
        return targetIds.filter { it !in state.postExerciseFeedbackByExerciseId }
    }
}
