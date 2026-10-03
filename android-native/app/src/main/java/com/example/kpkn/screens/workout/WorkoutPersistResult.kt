package com.example.kpkn.screens.workout

sealed class WorkoutPersistResult {
    data object Ok : WorkoutPersistResult()
    data class Failed(val cause: Throwable) : WorkoutPersistResult()
    /** Room committed; the owner must reconcile UI before a stale snapshot can write again. */
    data class UiPublicationFailed(val cause: Throwable) : WorkoutPersistResult()
    data object Skipped : WorkoutPersistResult()
    data object Cancelled : WorkoutPersistResult()

    val succeeded: Boolean get() = this is Ok || this is UiPublicationFailed
}
