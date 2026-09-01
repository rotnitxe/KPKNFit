package com.example.kpkn.screens.workout.components

import androidx.compose.runtime.mutableIntStateOf
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutUiTokensPrepHeightTest {
    @Test
    fun publishWorkingSetHeightKeepsFirstMeasurement() {
        val holder = mutableIntStateOf(0)
        publishLivePagerWorkingSetVisualHeight(holder, 400)
        publishLivePagerWorkingSetVisualHeight(holder, 520)
        assertEquals(400, holder.intValue)
    }

    @Test
    fun liveRoadmapCarouselCardHeight_scalesWithViewport() {
        assertEquals(48f, WorkoutUiTokens.liveRoadmapCarouselCardHeight(1f).value, 0.01f)
        assertEquals(57.6f, WorkoutUiTokens.liveRoadmapCarouselCardHeight(1.2f).value, 0.01f)
        assertEquals(34f, WorkoutUiTokens.liveRoadmapCarouselMiniCardHeight(1f).value, 0.01f)
    }

    @Test
    fun liveCockpitCompactHeight_isStableAcrossCarouselCardKinds() {
        val compact = WorkoutUiTokens.liveCockpitCompactHeight(1f).value
        assertEquals(188f, compact, 0.01f)
        assertEquals(
            WorkoutUiTokens.liveRoadmapCarouselCardHeight(1f).value,
            WorkoutUiTokens.liveRoadmapCarouselCardHeight(1f).value,
            0.01f,
        )
        assertEquals(212f, WorkoutUiTokens.liveCockpitCompactHeight(1.2f).value, 0.01f)
    }

    @Test
    fun restAndPrepShareWorkingSetHeight() {
        assertEquals(610, resolveLivePagerPrepCardHeightPx(610, 520))
        assertEquals(520, resolveLivePagerPrepCardHeightPx(0, 520))
        assertEquals(400, resolveLivePagerPrepCardHeightPx(400, 520))
    }
}
