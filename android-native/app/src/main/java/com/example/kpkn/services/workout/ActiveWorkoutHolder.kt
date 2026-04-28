package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutViewModel

object ActiveWorkoutHolder {
    @Volatile
    private var _viewModel: WorkoutViewModel? = null

    fun set(viewModel: WorkoutViewModel) {
        _viewModel = viewModel
    }

    fun clear() {
        _viewModel = null
    }

    fun get(): WorkoutViewModel? = _viewModel

    fun isActive(): Boolean = _viewModel != null

    fun handleAction(action: TimerAction) {
        _viewModel?.handleTimerAction(action)
    }
}

sealed class TimerAction {
    data object CompleteSet : TimerAction()
    data object SkipTimer : TimerAction()
    data object AddTime : TimerAction()
    data object SubtractTime : TimerAction()
}
