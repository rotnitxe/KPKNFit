package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutViewModel
import java.lang.ref.WeakReference

object ActiveWorkoutHolder {
    @Volatile
    private var _viewModelRef: WeakReference<WorkoutViewModel>? = null

    fun set(viewModel: WorkoutViewModel) {
        _viewModelRef = WeakReference(viewModel)
    }

    fun clear() {
        _viewModelRef?.clear()
        _viewModelRef = null
    }

    fun get(): WorkoutViewModel? = _viewModelRef?.get()

    fun isActive(): Boolean = _viewModelRef?.get() != null

    fun handleAction(action: TimerAction) {
        _viewModelRef?.get()?.handleTimerAction(action)
    }
}

sealed class TimerAction {
    data object CompleteSet : TimerAction()
    data object SkipTimer : TimerAction()
    data object AddTime : TimerAction()
    data object SubtractTime : TimerAction()
}
