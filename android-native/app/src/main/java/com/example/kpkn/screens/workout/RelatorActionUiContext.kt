package com.example.kpkn.screens.workout

import androidx.compose.runtime.staticCompositionLocalOf

/** El despacho devuelve el Ack del mismo toque; callbacks antiguos sin resultado nunca afirman éxito. */
internal data class RelatorActionUiContext(
    val scopeKey: String,
    val dispatch: (RelatorAssistAction) -> RelatorAssistAck,
)

internal val LocalRelatorActionUiContext = staticCompositionLocalOf<RelatorActionUiContext?> { null }

/** Cambiar de frase/ASSIST_CONFIRM no cambia la serie. Registrar o navegar sí invalida la narración y el undo. */
internal fun relatorActionScopeKey(state: WorkoutUiState): String = listOf(
    state.startTimeMs, state.activeMode, state.currentExerciseIdx, state.currentSetIdx, state.activeStepKey,
    state.editingState?.side,
    state.completedSets.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.hashCode()}" },
).joinToString("|")
