package com.example.kpkn.screens.workout

import androidx.compose.ui.graphics.Color

internal enum class ExerciseDrainOverlayChannelV2 {
    ENERGY,
    BACK,
    MUSCLE,
}

internal data class ExerciseDrainOverlayItemV2(
    val label: String,
    val delta: Int,
    val channel: ExerciseDrainOverlayChannelV2,
)

internal data class ExerciseDrainOverlayStateV2(
    val key: String,
    val exerciseName: String,
    val items: List<ExerciseDrainOverlayItemV2>,
)

internal data class WorkoutStageTransitionTargetV2(
    val exerciseId: String,
    val order: Int,
    val label: String,
)

internal enum class WorkoutSetCardVisualState {
    FUTURE,
    COMPLETED,
    SKIPPED,
    ACTIVE,
}

internal data class WorkoutSetPagerItem(
    val index: Int,
    val label: String,
    val state: WorkoutSetCardVisualState,
    val isEditing: Boolean,
    val side: String? = null,
    val pulseToken: Long? = null,
    val isWarmupOrFeedback: Boolean = false,
)

internal val WORKOUT_WARMUP_BLUE: Color = Color(0xFF448AFF)

internal fun workoutSetPagerAccent(
    state: WorkoutSetCardVisualState,
    colorScheme: androidx.compose.material3.ColorScheme,
    isWarmupOrFeedback: Boolean = false,
    sessionAccentColor: Color? = null,
): Color = when {
    isWarmupOrFeedback -> WORKOUT_WARMUP_BLUE
    sessionAccentColor != null -> sessionAccentColor
    else -> when (state) {
        WorkoutSetCardVisualState.ACTIVE -> colorScheme.primary
        WorkoutSetCardVisualState.COMPLETED -> colorScheme.tertiary
        WorkoutSetCardVisualState.SKIPPED -> colorScheme.outline
        WorkoutSetCardVisualState.FUTURE -> colorScheme.surfaceContainerHighest
    }
}
