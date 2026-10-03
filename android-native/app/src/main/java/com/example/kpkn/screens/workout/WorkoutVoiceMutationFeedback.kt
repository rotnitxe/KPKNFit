package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.services.workout.VoiceSetEditPatch

/**
 * What the voice assistant says for each [WorkoutVoiceMutationResult]. Pure so the table is testable
 * without TTS. Success is announced only for [WorkoutVoiceMutationResult.Committed], i.e. after Room
 * acknowledged the change; failures keep the user's data and say so with distinct wording.
 */
internal object WorkoutVoiceMutationFeedback {
    const val UNDO_DONE = "Serie deshecha."
    const val UNDO_NO_RECENT_SET = "No hay una serie reciente para deshacer."
    const val UNDO_BUSY = "Espera un momento y repite deshacer."
    const val UNDO_STALE = "No pude confirmar el cambio. Tus datos se conservan; repite deshacer."
    const val UNDO_FAILED = "No pude deshacer la serie. Tus datos se conservan; repite deshacer."

    const val EDIT_DONE = "Serie actualizada."
    const val EDIT_NO_RECENT_SET = "No hay una serie reciente para editar."
    const val EDIT_NO_CHANGE = "Esa serie ya tiene esos valores."
    const val EDIT_BUSY = "Espera un momento y repite el cambio."
    const val EDIT_STALE = "No pude confirmar el cambio. Tus datos se conservan; repítelo."
    const val EDIT_FAILED = "No pude guardar el cambio. Tus datos se conservan; repítelo."

    const val CLOSING = "El entreno se está cerrando."

    /** Phrase for an undo, or null to stay silent (the execution was replaced or discarded). */
    fun forUndo(result: WorkoutVoiceMutationResult): String? = when (result) {
        is WorkoutVoiceMutationResult.Committed -> UNDO_DONE
        WorkoutVoiceMutationResult.NoRecentSet, WorkoutVoiceMutationResult.NoChange -> UNDO_NO_RECENT_SET
        WorkoutVoiceMutationResult.Busy -> UNDO_BUSY
        WorkoutVoiceMutationResult.Closing -> CLOSING
        WorkoutVoiceMutationResult.Stale -> UNDO_STALE
        WorkoutVoiceMutationResult.SessionChanged -> null
        is WorkoutVoiceMutationResult.PersistenceFailed -> UNDO_FAILED
    }

    /**
     * Phrase for an edit that did not commit, or null to stay silent. A committed edit is announced
     * with [committedEditSpeech]; [EDIT_DONE] is only the fallback when no set came back.
     */
    fun forEdit(result: WorkoutVoiceMutationResult): String? = when (result) {
        is WorkoutVoiceMutationResult.Committed -> EDIT_DONE
        WorkoutVoiceMutationResult.NoRecentSet -> EDIT_NO_RECENT_SET
        WorkoutVoiceMutationResult.NoChange -> EDIT_NO_CHANGE
        WorkoutVoiceMutationResult.Busy -> EDIT_BUSY
        WorkoutVoiceMutationResult.Closing -> CLOSING
        WorkoutVoiceMutationResult.Stale -> EDIT_STALE
        WorkoutVoiceMutationResult.SessionChanged -> null
        is WorkoutVoiceMutationResult.PersistenceFailed -> EDIT_FAILED
    }

    /** Outcomes where nothing was changed but the very same command can simply be repeated. */
    fun isRetryable(result: WorkoutVoiceMutationResult): Boolean =
        result is WorkoutVoiceMutationResult.PersistenceFailed ||
            result == WorkoutVoiceMutationResult.Busy ||
            result == WorkoutVoiceMutationResult.Stale

    /** Failures worth a visible notice too (the athlete may not be listening). */
    fun needsVisibleNotice(result: WorkoutVoiceMutationResult): Boolean =
        result is WorkoutVoiceMutationResult.PersistenceFailed || result == WorkoutVoiceMutationResult.Stale

    /** Values for `speakSetUpdated`: the CONFIRMED values of the fields the athlete changed. */
    data class SetUpdatedSpeech(
        val weightKg: Double?,
        val reps: Int?,
        val intensityValue: Double?,
        val intensityKind: WorkoutVoiceIntensityKind?,
    )

    fun committedEditSpeech(patch: VoiceSetEditPatch, committed: CompletedSet?): SetUpdatedSpeech? {
        if (committed == null) return null
        val loadMode = LoadSuggestionEngine.resolvedLoadMode(committed)
        // A bodyweight set has no typed load to confirm (the weight part of the patch is ignored).
        val weightEdited = (patch.weightKg != null || patch.weightDeltaKg != null) &&
            loadMode != LoadModeV2.BODYWEIGHT
        val intensityKind = patch.intensityKind?.takeIf {
            it != WorkoutVoiceIntensityKind.PERCENT_RM && patch.intensityValue != null
        }
        return SetUpdatedSpeech(
            weightKg = if (weightEdited) LoadSuggestionEngine.inputLoad(committed, loadMode) else null,
            reps = patch.metricValue?.takeIf { it > 0 },
            intensityValue = patch.intensityValue.takeIf { intensityKind != null },
            intensityKind = intensityKind,
        )
    }
}
