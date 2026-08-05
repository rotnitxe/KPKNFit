package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation

enum class VoicePipelineStage {
    DISABLED,
    LISTENING,
    PROCESSING,
    CONFIRM_WAIT,
    TTS_SPEAKING,
    /** Android silenció o retiró nuestra captura (llamada / otra app). */
    MIC_BUSY,
    /** Reabriendo AudioRecord tras ocupación. */
    RECONNECTING,
    /** El fénix está reintentando la reconexión del proceso de voz. */
    RECOVERING,
    ERROR_RECOVERY,
    FAILED,
}

data class VoiceSessionState(
    val stage: VoicePipelineStage = VoicePipelineStage.DISABLED,
    val partialText: String = "",
    val lastInterpretation: WorkoutVoiceInterpretation? = null,
    val lastCommand: VoiceSessionCommand? = null,
    val errorMessage: String? = null,
    val duckHandle: Any? = null,
    val consecutiveErrors: Int = 0,
    /** Waiting for the persistence scope after AddSet. */
    val pendingAddSetPersistence: Boolean = false,
    /** Exact scope choices announced for the current AddSet prompt. */
    val pendingAddSetPersistencePrompt: String = "",
    /** Live mic level from SpeechRecognizer.onRmsChanged (dB). */
    val rmsLevel: Float = 0f,
    /** Short human summary of last understood action for the dock. */
    val lastHeardSummary: String = "",
    /** True when the continuous engine is using the local Vosk runtime. */
    val usingOnDeviceRecognizer: Boolean = false,
    /** True while a native one-shot fallback is active. */
    val usingNativeFallback: Boolean = false,
    /** True when the fallback circuit is open and temporarily paused. */
    val fallbackPaused: Boolean = false,
    /** Human-readable mic route, when available. */
    val activeRouteLabel: String? = null,
    val pendingAction: VoicePendingAction? = null,
) {
    val isListening: Boolean get() = stage == VoicePipelineStage.LISTENING
    val isDucking: Boolean get() = duckHandle != null
    val hasPendingConfirmation: Boolean get() = stage == VoicePipelineStage.CONFIRM_WAIT
}

sealed interface VoicePendingAction {
    val baseInterpretation: WorkoutVoiceInterpretation

    data class IntensityKind(
        override val baseInterpretation: WorkoutVoiceInterpretation,
        val value: Double,
    ) : VoicePendingAction

    /** RIR imposible (>5, e.g. mishearing "rir doce"): re-pregunta el valor. */
    data class ReaskIntensity(
        override val baseInterpretation: WorkoutVoiceInterpretation,
    ) : VoicePendingAction

    data class LoadMode(
        override val baseInterpretation: WorkoutVoiceInterpretation,
    ) : VoicePendingAction

    data class ExerciseNavigation(
        override val baseInterpretation: WorkoutVoiceInterpretation = WorkoutVoiceInterpretation(""),
        val command: VoiceSessionCommand.NavigateToExercise,
        val exerciseName: String,
    ) : VoicePendingAction

    data class ExerciseReplacement(
        override val baseInterpretation: WorkoutVoiceInterpretation = WorkoutVoiceInterpretation(""),
        val command: VoiceSessionCommand.ConfirmReplaceExercise,
        val targetName: String,
        val replacementName: String,
    ) : VoicePendingAction

    data class DiscomfortSelection(
        override val baseInterpretation: WorkoutVoiceInterpretation = WorkoutVoiceInterpretation(""),
        val candidates: Map<String, String>,
    ) : VoicePendingAction

    data class TagCreation(
        override val baseInterpretation: WorkoutVoiceInterpretation = WorkoutVoiceInterpretation(""),
        val tagName: String,
    ) : VoicePendingAction

    data class FinishWithPending(
        override val baseInterpretation: WorkoutVoiceInterpretation = WorkoutVoiceInterpretation(""),
        val pendingExerciseNames: List<String>,
    ) : VoicePendingAction

    data class TechniqueDetails(
        override val baseInterpretation: WorkoutVoiceInterpretation,
        val technique: String,
    ) : VoicePendingAction

    /** Faltó un campo (peso o reps): se pregunta de forma dirigida. */
    data class MissingSlot(
        override val baseInterpretation: WorkoutVoiceInterpretation,
        val slot: WorkoutVoiceField,
    ) : VoicePendingAction

    /** Falta la métrica y hay valor programado: "¿Pudiste hacer las X repeticiones?" */
    data class ConfirmPlannedValue(
        override val baseInterpretation: WorkoutVoiceInterpretation,
        val slot: WorkoutVoiceField,
        val plannedValue: Double,
    ) : VoicePendingAction

    /** Falta la carga y hay sugerencia: "¿Usaste los X kilos?" (sí = usar sugerido). */
    data class ConfirmSuggestedLoad(
        override val baseInterpretation: WorkoutVoiceInterpretation,
        val suggestedWeight: Double,
        val plannedReps: Double?,
    ) : VoicePendingAction

    /** Confirmación explícita de una acción estructural (mover/disolver superserie). */
    data class ConfirmStructureAction(
        override val baseInterpretation: WorkoutVoiceInterpretation = WorkoutVoiceInterpretation(""),
        val action: VoiceSessionCommand,
    ) : VoicePendingAction

    /** Recoge miembros de la superserie por voz (uno por turno, "listo" termina). */
    data class SupersetCollectMembers(
        override val baseInterpretation: WorkoutVoiceInterpretation = WorkoutVoiceInterpretation(""),
        val members: List<String>,
        val exerciseNames: List<String>,
    ) : VoicePendingAction
}

sealed class VoiceSessionCommand {
    data class RegisterSet(val interpretation: WorkoutVoiceInterpretation) : VoiceSessionCommand()
    data class ApplyTag(val tagName: String) : VoiceSessionCommand()
    data class ApplyConfirmedTag(val tagName: String) : VoiceSessionCommand()
    data object Confirm : VoiceSessionCommand()
    data object Cancel : VoiceSessionCommand()
    data object SkipExercise : VoiceSessionCommand()
    data object SkipSet : VoiceSessionCommand()
    data object PreviousExercise : VoiceSessionCommand()
    data object SuggestWeight : VoiceSessionCommand()
    data object RestStatus : VoiceSessionCommand()
    data object WhatExercise : VoiceSessionCommand()
    data object NextExercise : VoiceSessionCommand()
    data class GoToExercise(val spokenName: String) : VoiceSessionCommand()
    data class NavigateToExercise(val exerciseId: String) : VoiceSessionCommand()
    /** Reemplaza un ejercicio de la sesión por otro del catálogo (voz).
     *  [targetName] vacío = ejercicio actual. */
    data class ReplaceExercise(
        val targetName: String,
        val replacementPhrase: String,
    ) : VoiceSessionCommand()
    /** Confirmación del reemplazo por voz; [replacement] es la configuración
     *  exacta del catálogo (con sus chips de opciones) ya resuelta. */
    data class ConfirmReplaceExercise(
        val targetExerciseId: String,
        val replacement: com.example.kpkn.data.models.ExerciseMuscleInfo,
    ) : VoiceSessionCommand()
    data object TurnOffVoice : VoiceSessionCommand()
    data object FinishSession : VoiceSessionCommand()
    data object LeaveUpToHere : VoiceSessionCommand()
    data object ConfirmFinishWithPending : VoiceSessionCommand()
    data object CancelSession : VoiceSessionCommand()
    /** Add an extra set to the current exercise (live session). */
    data object AddSet : VoiceSessionCommand()
    /** Resolve AddSet persistence prompt: keep session-only. */
    data object AddSetSessionOnly : VoiceSessionCommand()
    /** Resolve AddSet persistence prompt: save permanently to program. */
    data object AddSetPermanent : VoiceSessionCommand()
    /** Skip the active rest timer (not the exercise). */
    data object SkipRest : VoiceSessionCommand()
    /** Apply pending adaptive rest suggestion. */
    data object UseAdaptiveRest : VoiceSessionCommand()
    /** Usar la carga sugerida del set actual (auto-relleno con confirmación). */
    data object ApplySuggestedLoad : VoiceSessionCommand()
    /** Adjust rest timer by spoken delta seconds. */
    data class AdjustRestTime(val deltaSeconds: Int) : VoiceSessionCommand()
    /** Undo last auto-confirmed set within the correction window. */
    data object UndoLastSet : VoiceSessionCommand()
    /** Patch fields on the last logged set without deleting it first. */
    data class EditLastSet(val patch: VoiceSetEditPatch) : VoiceSessionCommand()
    /** Coach: explain suggested load. */
    data object SuggestWeightReasoned : VoiceSessionCommand()
    /** Coach: fatigue advice. */
    data object FatigueAdvice : VoiceSessionCommand()
    /** Coach: session pace status. */
    data object PaceStatus : VoiceSessionCommand()
    /** Coach: ¿cuánto drenaje/fatiga acumulada llevo? (read-only). */
    data object QueryDrainage : VoiceSessionCommand()
    /** ¿Qué serie voy? (read-only). */
    data object QueryCurrentSet : VoiceSessionCommand()
    /** ¿Qué lado falta en una serie unilateral? (read-only). */
    data object QueryPendingSide : VoiceSessionCommand()
    /** Mover el ejercicio actual ±1 posición (requiere confirmación). */
    data class MoveCurrentExercise(val direction: Int) : VoiceSessionCommand()
    /** Crear superserie guiada con los ejercicios dichos (requiere confirmación). */
    data object CreateSuperset : VoiceSessionCommand()
    /** Disolver la superserie del ejercicio actual (requiere confirmación). */
    data object DissolveSuperset : VoiceSessionCommand()
    /** Confirmada por voz: crear superserie con los miembros resueltos. */
    data class ConfirmCreateSuperset(
        val members: List<String>,
        val exerciseNames: List<String>,
    ) : VoiceSessionCommand()
    data class SetSessionTimeLimit(val minutes: Int, val persistToProgram: Boolean) : VoiceSessionCommand()
    data object StartTimedSet : VoiceSessionCommand()
    data object StopTimedSet : VoiceSessionCommand()
    data object CompletePreparationStep : VoiceSessionCommand()
    /** Stop current TTS utterance (barge-in). */
    data object StopSpeaking : VoiceSessionCommand()
    data class LogFeedback(
        val technicalQuality: Int?,
        val discomfortId: String?,
        val perceivedIntensity: Double?,
        val isSaveAction: Boolean = false,
        val exerciseSearchName: String? = null,
        val discomfortCandidates: Map<String, String> = emptyMap(),
    ) : VoiceSessionCommand()
    data class LogFinalFeedback(
        val notes: String? = null,
        val discomfortId: String? = null,
        val additionalDiscomfortNote: String? = null,
        val neuralBattery: Int? = null,
        val spinalBattery: Int? = null,
        val isSaveAction: Boolean = false,
    ) : VoiceSessionCommand()
    data class Unknown(val raw: String) : VoiceSessionCommand()
}
