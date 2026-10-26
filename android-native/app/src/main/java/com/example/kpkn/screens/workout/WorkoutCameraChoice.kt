package com.example.kpkn.screens.workout

internal enum class WorkoutCameraLens { BACK, FRONT }

/** A camera switch never changes the lens of an in-flight recording. */
internal data class WorkoutCameraChoice(
    val lens: WorkoutCameraLens = WorkoutCameraLens.BACK,
    val hasBack: Boolean = false,
    val hasFront: Boolean = false,
) {
    val canSwitch: Boolean get() = hasBack && hasFront
    val switchDescription: String get() = if (lens == WorkoutCameraLens.BACK) "Usar cámara frontal" else "Usar cámara trasera"

    fun detected(back: Boolean, front: Boolean): WorkoutCameraChoice = copy(
        hasBack = back,
        hasFront = front,
        lens = when {
            lens == WorkoutCameraLens.BACK && !back && front -> WorkoutCameraLens.FRONT
            lens == WorkoutCameraLens.FRONT && !front && back -> WorkoutCameraLens.BACK
            else -> lens
        },
    )

    fun switched(recording: Boolean): WorkoutCameraChoice = if (recording || !canSwitch) this else copy(
        lens = if (lens == WorkoutCameraLens.BACK) WorkoutCameraLens.FRONT else WorkoutCameraLens.BACK,
    )
}
