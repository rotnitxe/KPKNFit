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

internal val VolumeReplacedStepperTint = Color(0xFFE53935)

internal fun workoutSetPagerAccent(
    state: WorkoutSetCardVisualState,
    colorScheme: androidx.compose.material3.ColorScheme,
    isWarmupOrFeedback: Boolean = false,
    sessionAccentColor: Color? = null,
    volumeReplaced: Boolean = false,
): Color {
    val accent = if (volumeReplaced) {
        VolumeReplacedStepperTint
    } else {
        sessionAccentColor ?: colorScheme.primary
    }
    return when {
        isWarmupOrFeedback -> androidx.compose.ui.graphics.lerp(accent, Color.White, 0.22f)
        state == WorkoutSetCardVisualState.ACTIVE -> accent
        state == WorkoutSetCardVisualState.COMPLETED -> androidx.compose.ui.graphics.lerp(accent, Color.White, 0.18f)
        state == WorkoutSetCardVisualState.SKIPPED -> colorScheme.outline
        else -> colorScheme.onSurfaceVariant
    }
}

internal fun resolveWorkoutHeaderGroupLabel(
    partName: String?,
    type: String?,
    category: String?,
): String? {
    val explicitPart = partName
        ?.trim()
        ?.takeIf {
            it.isNotBlank() &&
                !it.equals("sesión", ignoreCase = true) &&
                !it.equals("sesion", ignoreCase = true) &&
                !it.equals("sesión principal", ignoreCase = true) &&
                !it.equals("sesion principal", ignoreCase = true)
        }
    return normalizeWorkoutHeaderLabel(explicitPart ?: type?.trim()?.takeIf { it.isNotBlank() } ?: category?.trim())
}

internal fun normalizeWorkoutHeaderLabel(raw: String?): String? {
    val value = raw?.trim()?.replace(Regex("\\s+"), " ") ?: return null
    if (value.isBlank()) return null
    val letterChars = value.filter { it.isLetter() }
    val upperRatio = if (letterChars.isEmpty()) {
        0.0
    } else {
        letterChars.count { it.isUpperCase() }.toDouble() / letterChars.length
    }
    // Preserve intentional ALL CAPS (and repair mixed leftovers like PRINCIPALEs from IME).
    if (upperRatio >= 0.75) return value.uppercase()
    if (value.equals("principales", ignoreCase = true)) return "Principales"
    if (value.equals("principal", ignoreCase = true)) return "Principal"
    return value
        .replace(Regex("principales", RegexOption.IGNORE_CASE), "Principales")
        .replace(Regex("principal", RegexOption.IGNORE_CASE), "Principal")
}
