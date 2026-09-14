package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.Exercise

internal fun restLiveCardExerciseName(restState: WorkoutRestModalState?): String? =
    restState?.exerciseName?.takeIf { it.isNotBlank() }

internal fun firstPrepMemberOrNull(members: List<Exercise>): Exercise? = members.firstOrNull()

internal fun cardioDetailsOrNull(exercise: Exercise): CardioDetails? = exercise.cardioDetails

internal fun overlayCardioTimerTick(
    base: CardioTimerState?,
    remainingSeconds: Int,
    elapsedSeconds: Int,
): CardioTimerState? {
    val state = base ?: return null
    return if (state.status == CardioExecutionStatus.RUNNING) {
        state.copy(remainingSeconds = remainingSeconds, elapsedSeconds = elapsedSeconds)
    } else {
        state
    }
}

internal fun imbalanceNoticeText(notice: String?): String? = notice?.takeIf { it.isNotBlank() }

internal fun historySheetDbId(showHistorySheet: Boolean, historySheetExerciseDbId: String?): String? =
    if (showHistorySheet) historySheetExerciseDbId else null

internal fun inlineRestRemainingOrNull(remainingSeconds: Int?, totalSeconds: Int?): Int? {
    if (remainingSeconds == null) return null
    if ((totalSeconds ?: 0) <= 0) return null
    return remainingSeconds
}

internal fun invokeAddSetAction(onAddSet: (() -> Unit)?): Boolean {
    if (onAddSet == null) return false
    onAddSet()
    return true
}
