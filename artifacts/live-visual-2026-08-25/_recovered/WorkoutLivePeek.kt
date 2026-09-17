package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.example.kpkn.ui.theme.DarkBackground

/**
 * Live-session vertical peek: the next exercise is the real next page,
 * clipped by the viewport. Not a cloned "coming next" card.
 */
internal object WorkoutLivePeekTokens {
    val PageGap = 12.dp
    const val VeilAlpha = 0.40f
    val FirstPillHeight = 36.dp
    val CardHeaderHeight = 40.dp
    /** How much of the real next card shows after its header (~mid-card crop). */
    val CardMidPeek = 88.dp

    fun peekReserve(): Dp = PageGap + max(FirstPillHeight, CardHeaderHeight + CardMidPeek)
}

internal fun shouldSelectExerciseOnVerticalSettle(
    origin: WorkoutPagerSettlementOrigin,
    settledPage: Int,
    currentExerciseIdx: Int,
    showingPostExerciseCard: Boolean,
    pageCount: Int,
): Boolean {
    if (showingPostExerciseCard) return false
    if (origin != WorkoutPagerSettlementOrigin.USER) return false
    if (pageCount <= 0) return false
    if (settledPage !in 0 until pageCount) return false
    if (settledPage == currentExerciseIdx) return false
    return true
}

internal fun isLastVerticalExercisePage(page: Int, pageCount: Int): Boolean =
    pageCount > 0 && page == pageCount - 1

internal fun shouldShowSessionEndPeek(
    settledPage: Int,
    currentPage: Int,
    pageCount: Int,
    showingPostExerciseCard: Boolean,
): Boolean {
    if (showingPostExerciseCard) return false
    if (pageCount <= 0) return false
    return isLastVerticalExercisePage(settledPage, pageCount) ||
        isLastVerticalExercisePage(currentPage, pageCount)
}

@Composable
internal fun WorkoutLivePeekVeil(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = WorkoutLivePeekTokens.VeilAlpha)),
    )
}

@Composable
internal fun WorkoutLivePeekFade(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.50f to Color.Transparent,
                    1.0f to DarkBackground,
                ),
            ),
        ),
    )
}

@Composable
internal fun WorkoutLivePeekInputBlocker(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    )
}
