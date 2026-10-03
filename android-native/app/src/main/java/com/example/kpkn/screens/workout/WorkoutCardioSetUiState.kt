package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet

internal data class CardioSetUiState(
    val completedSet: CompletedSet?,
    val timerState: CardioTimerState?,
)

internal fun cardioSetUiStateForPage(
    exerciseId: String,
    setIndex: Int,
    setId: String?,
    completedSets: Map<String, CompletedSet>,
    timerState: CardioTimerState?,
): CardioSetUiState {
    val timerForSet = timerState?.takeIf { timer ->
        val timerSetId = timer.setId?.takeIf { it.isNotBlank() }
        val targetSetId = setId?.takeIf { it.isNotBlank() }
        timer.exerciseId == exerciseId &&
            ((timerSetId != null && timerSetId == targetSetId) || (setIndex == 0 && timerSetId == null))
    }
    return CardioSetUiState(
        completedSet = completedSets[WorkoutStepRules.cardioCompletionKey(exerciseId, setIndex)],
        timerState = timerForSet,
    )
}
