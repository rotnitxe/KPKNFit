package com.example.kpkn.screens.workout.components

import androidx.compose.runtime.mutableIntStateOf
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutUiTokensPrepHeightTest {
    @Test
    fun publishWorkingSetHeightKeepsMaxAcrossPages() {
        val holder = mutableIntStateOf(0)
        publishLivePagerWorkingSetVisualHeight(holder, 520)
        publishLivePagerWorkingSetVisualHeight(holder, 400)
        assertEquals(520, holder.intValue)
    }

    @Test
    fun resolvePrepHeightUsesMeasuredOrFloor() {
        assertEquals(610, resolveLivePagerPrepCardHeightPx(610, 480))
        assertEquals(480, resolveLivePagerPrepCardHeightPx(0, 480))
    }
}
