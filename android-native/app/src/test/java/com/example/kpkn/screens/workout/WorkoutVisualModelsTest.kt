package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVisualModelsTest {

    @Test
    fun stepper_gap_caps_when_few_series_leave_empty_width() {
        val gap = workoutStepperGapDp(
            availableWidthDp = 280f,
            contentWidthWithoutGapsDp = 78f,
            gapCount = 2,
        )
        assertEquals(21f, gap, 0.01f)
    }

    @Test
    fun stepper_gap_uses_leftover_between_min_and_max() {
        val gap = workoutStepperGapDp(
            availableWidthDp = 200f,
            contentWidthWithoutGapsDp = 140f,
            gapCount = 4,
        )
        assertEquals(15f, gap, 0.01f)
    }

    @Test
    fun stepper_gap_compresses_toward_min_when_many_series() {
        val gap = workoutStepperGapDp(
            availableWidthDp = 220f,
            contentWidthWithoutGapsDp = 234f,
            gapCount = 8,
        )
        assertEquals(10f, gap, 0.01f)
    }

    @Test
    fun stepper_gap_never_collapses_to_zero_even_when_over_budget() {
        val gap = workoutStepperGapDp(
            availableWidthDp = 100f,
            contentWidthWithoutGapsDp = 400f,
            gapCount = 20,
            minGapDp = 0f, // hostile caller — floor still enforced
            maxGapDp = 21f,
        )
        assertEquals(10f, gap, 0.01f)
    }

    @Test
    fun stepper_gap_shrinks_as_point_count_grows() {
        val few = workoutStepperGapDp(
            availableWidthDp = 260f,
            contentWidthWithoutGapsDp = 96f, // 3 * 32
            gapCount = 2,
        )
        val many = workoutStepperGapDp(
            availableWidthDp = 260f,
            contentWidthWithoutGapsDp = 288f, // 9 * 32
            gapCount = 8,
        )
        assertEquals(21f, few, 0.01f)
        assertEquals(10f, many, 0.01f)
        assert(few > many)
    }

    @Test
    fun stepper_scroll_pulls_active_out_from_under_leading_chrome() {
        val target = workoutStepperScrollToKeepActiveVisible(
            nodeLeftPx = 80f,
            nodeWidthPx = 80f,
            viewportWidthPx = 400f,
            leadingChromePx = 52f,
            trailingChromePx = 52f,
            currentScrollPx = 50,
            maxScrollPx = 200,
        )
        // visibleLeft = 30 < clearLeft 52 → scroll to 80 - 52 = 28
        assertEquals(28, target)
    }

    @Test
    fun stepper_scroll_advances_when_active_enters_trailing_chrome() {
        val target = workoutStepperScrollToKeepActiveVisible(
            nodeLeftPx = 300f,
            nodeWidthPx = 80f,
            viewportWidthPx = 400f,
            leadingChromePx = 52f,
            trailingChromePx = 52f,
            currentScrollPx = 0,
            maxScrollPx = 200,
        )
        // visibleRight = 380 > clearRight 348 → scroll to 300+80-348 = 32
        assertEquals(32, target)
    }

    @Test
    fun stepper_scroll_noop_when_active_already_in_clear_band() {
        val target = workoutStepperScrollToKeepActiveVisible(
            nodeLeftPx = 120f,
            nodeWidthPx = 80f,
            viewportWidthPx = 400f,
            leadingChromePx = 52f,
            trailingChromePx = 52f,
            currentScrollPx = 0,
            maxScrollPx = 200,
        )
        assertEquals(null, target)
    }

    @Test
    fun unilateral_item_width_is_wider_than_round_dot() {
        val uni = timelineStepperItemWidthDp(
            TimelineElement.UnilateralSet(
                setLabel = "S1",
                leftPageIndex = 0,
                leftState = WorkoutSetCardVisualState.ACTIVE,
                rightPageIndex = 0,
                rightState = WorkoutSetCardVisualState.FUTURE,
            )
        )
        val bi = timelineStepperItemWidthDp(
            TimelineElement.BilateralSet(
                pageIndex = 0,
                label = "1",
                state = WorkoutSetCardVisualState.ACTIVE,
            )
        )
        assert(uni > bi)
    }

    @Test
    fun header_group_prefers_user_session_part_name() {
        val label = resolveWorkoutHeaderGroupLabel(
            partName = "Pecho pesado",
            type = "Básico",
            category = "Fuerza",
        )

        assertEquals("Pecho pesado", label)
    }

    @Test
    fun header_group_falls_back_to_type_then_category() {
        assertEquals(
            "Básico",
            resolveWorkoutHeaderGroupLabel(partName = "Sesión", type = "Básico", category = "Fuerza"),
        )
        assertEquals(
            "Hipertrofia",
            resolveWorkoutHeaderGroupLabel(partName = "", type = "", category = "Hipertrofia"),
        )
    }

    @Test
    fun header_group_normalizes_principales_casing() {
        assertEquals(
            "PRINCIPALES",
            resolveWorkoutHeaderGroupLabel(partName = "PRINCIPALEs", type = null, category = null),
        )
        assertEquals(
            "PRINCIPALES",
            resolveWorkoutHeaderGroupLabel(partName = "PRINCIPALES", type = null, category = null),
        )
        assertEquals(
            "Principales",
            resolveWorkoutHeaderGroupLabel(partName = "principales", type = null, category = null),
        )
    }
}
