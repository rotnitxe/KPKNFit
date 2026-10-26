package com.example.kpkn.screens.workout

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.kpkn.data.models.Exercise

/** El despacho espera la confirmación durable; callbacks sin resultado nunca afirman éxito. */
internal data class RelatorActionUiContext(
    val scopeKey: String,
    val dispatch: suspend (RelatorAssistAction) -> RelatorAssistAck,
)

internal val LocalRelatorActionUiContext = staticCompositionLocalOf<RelatorActionUiContext?> { null }

/** Cambiar de frase/ASSIST_CONFIRM no cambia la serie. Registrar o navegar sí invalida la narración y el undo. */
internal fun relatorActionScopeKey(state: WorkoutUiState, currentExercise: Exercise?): String = listOf(
    relatorExerciseIdentity(currentExercise),
    state.startTimeMs, state.activeMode, state.currentExerciseIdx, state.currentSetIdx, state.activeStepKey,
    state.editingState?.side,
    state.completedSets.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.hashCode()}" },
).joinToString("|")

/** Identidad de contenido, no solo el ID del hueco que un reemplazo conserva. No incluye cargas del plan/borrador. */
internal fun relatorExerciseIdentity(exercise: Exercise?): String = exercise?.let {
    listOf(it.id, it.occurrenceId, it.exerciseId, it.exerciseDbId, it.canonicalExerciseId,
        it.relativeToCanonicalExerciseId, it.catalogDefinitionId, it.catalogConfigurationId,
        it.performanceProfileId, it.variantName, it.selectedExecutionOption, it.selectedAspects?.toSortedMap(), it.name)
        .joinToString("|")
}.orEmpty()
