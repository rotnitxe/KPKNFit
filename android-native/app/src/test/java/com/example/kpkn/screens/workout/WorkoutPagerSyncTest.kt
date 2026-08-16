package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPagerSyncTest {
    @Test
    fun staleSettledPage_afterNextSet_cannotReSelectPreviousSeries() {
        val coordinator = WorkoutPagerSyncCoordinator()

        assertEquals(WorkoutPagerSettlementOrigin.INITIAL, coordinator.onSettledPage(0))
        coordinator.beginProgrammaticScroll(targetPage = 1)

        // The old page can be observed while the state cursor already points
        // at page 1. It must not call selectWorkoutStep or stop the rest.
        assertEquals(WorkoutPagerSettlementOrigin.PROGRAMMATIC, coordinator.onSettledPage(0))
        assertEquals(WorkoutPagerSettlementOrigin.PROGRAMMATIC, coordinator.onSettledPage(1))
    }

    @Test
    fun roadmapSelection_isAcceptedOnlyAsAnActualUserSettledPage() {
        val key = workoutPagerStepKey(
            exerciseId = "press",
            page = WorkoutSetSwipePage(LivePageType.NORMAL, setIndex = 1),
        )

        assertTrue(
            shouldSyncSettledPagerPage(
                origin = WorkoutPagerSettlementOrigin.USER,
                activeStepKey = "press_0",
                activeStepType = WorkoutStepType.WORKING_SET,
                targetStepKey = key,
            ),
        )
        assertFalse(
            shouldSyncSettledPagerPage(
                origin = WorkoutPagerSettlementOrigin.PROGRAMMATIC,
                activeStepKey = "press_0",
                activeStepType = WorkoutStepType.WORKING_SET,
                targetStepKey = key,
            ),
        )
    }

    @Test
    fun normalAndLastPage_keepDistinctWorkingKeys() {
        val first = workoutPagerStepKey(
            "squat",
            WorkoutSetSwipePage(LivePageType.NORMAL, setIndex = 0),
        )
        val last = workoutPagerStepKey(
            "squat",
            WorkoutSetSwipePage(LivePageType.NORMAL, setIndex = 2),
        )

        assertEquals("squat_0", first)
        assertEquals("squat_2", last)
        assertTrue(first != last)
    }

    @Test
    fun unilateralAndSupersetPages_selectTheirOwnStepKeys() {
        val left = workoutPagerStepKey(
            "lunge",
            WorkoutSetSwipePage(LivePageType.NORMAL, setIndex = 0, side = "L"),
        )
        val right = workoutPagerStepKey(
            "lunge",
            WorkoutSetSwipePage(LivePageType.NORMAL, setIndex = 0, side = "R"),
        )
        val supersetMember = workoutPagerStepKey(
            "row",
            WorkoutSetSwipePage(LivePageType.NORMAL, setIndex = 1),
        )

        assertEquals("lunge_0_L", left)
        assertEquals("lunge_0_R", right)
        assertEquals("row_1", supersetMember)
        assertTrue(left != right)
    }

    @Test
    fun preparationStep_doesNotGetOverwrittenByASettledWorkingPage() {
        assertFalse(
            shouldSyncSettledPagerPage(
                origin = WorkoutPagerSettlementOrigin.USER,
                activeStepKey = "squat_warmup_w1",
                activeStepType = null,
                targetStepKey = "squat_0",
            ),
        )
    }
}
