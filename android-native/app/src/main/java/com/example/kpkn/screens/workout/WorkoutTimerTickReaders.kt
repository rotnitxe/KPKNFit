package com.example.kpkn.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Isolates cardio 1 Hz remaining/elapsed so parent workout trees do not recompose every tick. */
@Composable
internal fun CardioTimerTickReader(
    viewModel: WorkoutViewModel,
    content: @Composable (remainingSeconds: Int, elapsedSeconds: Int) -> Unit,
) {
    val remainingSeconds by viewModel.cardioTimerRemaining.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.cardioTimerElapsed.collectAsStateWithLifecycle()
    content(remainingSeconds, elapsedSeconds)
}

/** Isolates mobility 1 Hz remaining so parent workout trees do not recompose every tick. */
@Composable
internal fun MobilityTimerTickReader(
    viewModel: WorkoutViewModel,
    content: @Composable (remainingSeconds: Int) -> Unit,
) {
    val remainingSeconds by viewModel.mobilityTimerRemaining.collectAsStateWithLifecycle()
    content(remainingSeconds)
}
