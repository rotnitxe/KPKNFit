package com.example.kpkn.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Isolates rest-timer 1 Hz collection so the parent workout tree does not recompose every tick. */
@Composable
internal fun RestRemainingReader(
    viewModel: WorkoutViewModel,
    content: @Composable (remainingSeconds: Int) -> Unit,
) {
    val remainingSeconds by viewModel.restTimerRemaining.collectAsStateWithLifecycle()
    content(remainingSeconds)
}
