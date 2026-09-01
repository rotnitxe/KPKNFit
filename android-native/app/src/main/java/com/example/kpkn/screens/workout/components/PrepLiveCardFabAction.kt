package com.example.kpkn.screens.workout.components

internal fun runPrepLiveCardFabAction(
    hasIncomplete: Boolean,
    completeNext: () -> Unit,
    advance: () -> Unit,
) {
    if (hasIncomplete) completeNext() else advance()
}
