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
    fun live_adapt_scale_is_uniform_and_shrinks_on_narrow_or_short_viewport() {
        val full = com.example.kpkn.screens.workout.components.WorkoutUiTokens.liveAdaptScale(
            availableWidth = androidx.compose.ui.unit.Dp(411f),
            availableHeight = androidx.compose.ui.unit.Dp(520f),
        )
        val narrow = com.example.kpkn.screens.workout.components.WorkoutUiTokens.liveAdaptScale(
            availableWidth = androidx.compose.ui.unit.Dp(360f),
            availableHeight = androidx.compose.ui.unit.Dp(520f),
        )
        val short = com.example.kpkn.screens.workout.components.WorkoutUiTokens.liveAdaptScale(
            availableWidth = androidx.compose.ui.unit.Dp(411f),
            availableHeight = androidx.compose.ui.unit.Dp(400f),
        )
        assertEquals(1f, full, 0.01f)
        assert(narrow < 1f)
        assert(short < 1f)
        assertEquals(360f / 411f, narrow, 0.01f)
        assertEquals(400f / 520f, short, 0.01f)
    }

    @Test
    fun live_pager_compact_scale_matches_pre_god_mode_slot() {
        assertEquals(
            1.20f,
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale,
            0.001f,
        )
        val compactSlot = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerSlotHeight
        val expected = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerBaseHeight *
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale
        assertEquals(expected, compactSlot)
    }

    @Test
    fun live_pager_stable_height_covers_expanded_normal_slot() {
        val stable = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerNormalExpandedBaseHeight *
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale
        val compactSlot = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerSlotHeight
        assert(stable > compactSlot)
    }

    @Test
    fun stepper_layout_expands_rounds_to_the_right_independently() {
        val r0 = TimelineElement.RoundBadge(0, true, false, 0)
        val a = TimelineElement.BilateralSet(
            roundIndex = 0,
            pageIndex = 0,
            label = "A",
            state = WorkoutSetCardVisualState.ACTIVE,
        )
        val r1 = TimelineElement.RoundBadge(1, false, false, 1)
        val b = TimelineElement.BilateralSet(
            roundIndex = 1,
            pageIndex = 1,
            label = "B",
            state = WorkoutSetCardVisualState.FUTURE,
        )
        val r2 = TimelineElement.RoundBadge(2, false, false, 2)
        val c = TimelineElement.BilateralSet(
            roundIndex = 2,
            pageIndex = 2,
            label = "C",
            state = WorkoutSetCardVisualState.FUTURE,
        )
        val elements = listOf(r0, a, r1, b, r2, c)
        val onlyCurrent = stepperLayoutElements(elements, expandedRounds = setOf(1))
        assertEquals(listOf(r0, r1, b, r2), onlyCurrent)

        val visited = stepperExpandedRounds(naturalActiveRound = 1)
        assertEquals(setOf(0, 1), visited)
        val currentPlusPast = stepperLayoutElements(elements, expandedRounds = visited)
        assertEquals(listOf(r0, a, r1, b, r2), currentPlusPast)

        val peekedFuture = stepperExpandedRounds(naturalActiveRound = 0, extraExpandedRounds = setOf(2))
        assertEquals(setOf(0, 2), peekedFuture)
        val independent = stepperLayoutElements(elements, expandedRounds = peekedFuture)
        assertEquals(listOf(r0, a, r1, r2, c), independent)
    }

    @Test
    fun nickname_overlay_replaces_parent_name() {
        val exercise = com.example.kpkn.data.models.Exercise(
            id = "ex1",
            name = "Press banca",
            catalogDefinitionId = "def-1",
            sets = listOf(com.example.kpkn.data.models.ExerciseSet(id = "s1")),
        )
        val nicknames = mapOf("def-1" to "Pecho pesado")
        val parts = com.example.kpkn.domain.exercises.exerciseDisplayParts(
            exercise = exercise,
            catalogInfo = null,
            nicknames = nicknames,
        )
        assertEquals("Pecho pesado", parts.parentName)
    }

    @Test
    fun god_mode_technique_range_this_remaining_all() {
        val thisRange = com.example.kpkn.domain.exercises.godModeTechniqueRange(
            setIndex = 1,
            setCount = 4,
            scope = com.example.kpkn.domain.exercises.GodModeTechniqueScope.THIS,
        )
        val remaining = com.example.kpkn.domain.exercises.godModeTechniqueRange(
            setIndex = 1,
            setCount = 4,
            scope = com.example.kpkn.domain.exercises.GodModeTechniqueScope.REMAINING,
        )
        val all = com.example.kpkn.domain.exercises.godModeTechniqueRange(
            setIndex = 1,
            setCount = 4,
            scope = com.example.kpkn.domain.exercises.GodModeTechniqueScope.ALL,
        )
        assertEquals(1 to 1, thisRange)
        assertEquals(1 to 3, remaining)
        assertEquals(0 to 3, all)
    }

    @Test
    fun god_mode_technique_apply_skips_completed_indices() {
        val applied = com.example.kpkn.domain.exercises.godModeTechniqueApplyIndices(
            setIndex = 0,
            setCount = 4,
            scope = com.example.kpkn.domain.exercises.GodModeTechniqueScope.ALL,
            completedIndices = setOf(0, 1),
        )
        assertEquals(listOf(2, 3), applied)
    }

    @Test
    fun swapped_grouped_exercise_ids_moves_block_not_members() {
        val groups = listOf(
            listOf("a"),
            listOf("b", "c"),
            listOf("d"),
        )
        assertEquals(
            listOf("b", "c", "a", "d"),
            com.example.kpkn.screens.workout.components.swappedGroupedExerciseIds(groups, 0, 1),
        )
        assertEquals(
            listOf("a", "d", "b", "c"),
            com.example.kpkn.screens.workout.components.swappedGroupedExerciseIds(groups, 2, 1),
        )
    }

    @Test
    fun activity_clouds_group_adjacent_preparation_before_effective_series() {
        val elements = listOf(
            TimelineElement.MobilityPill(
                isCurrent = false,
                isCompleted = false,
                progress = 0f,
                onSelect = {},
            ),
            TimelineElement.WarmupPill(
                isCurrent = false,
                isCompleted = false,
                progress = 0f,
                onSelect = {},
            ),
            TimelineElement.BilateralSet(
                pageIndex = 0,
                label = "S1",
                state = WorkoutSetCardVisualState.FUTURE,
            ),
        )

        assertEquals(
            listOf(
                ActivityCloudSegment(ActivityCloudArea.PREPARATION, 0, 2),
                ActivityCloudSegment(ActivityCloudArea.EFFECTIVE_SERIES, 2, 3),
            ),
            activityCloudSegments(elements),
        )
    }

    @Test
    fun activity_clouds_keep_effective_round_and_unilateral_nodes_together() {
        val elements = listOf(
            TimelineElement.RoundBadge(
                roundIndex = 0,
                isCurrentRound = true,
                isAllDone = false,
                firstPageIndex = 0,
            ),
            TimelineElement.UnilateralSet(
                roundIndex = 0,
                setLabel = "S1",
                leftPageIndex = 1,
                leftState = WorkoutSetCardVisualState.ACTIVE,
                rightPageIndex = 2,
                rightState = WorkoutSetCardVisualState.FUTURE,
            ),
        )

        assertEquals(
            listOf(ActivityCloudSegment(ActivityCloudArea.SUPERSERIE, 0, 2)),
            activityCloudSegments(elements),
        )
    }

    @Test
    fun activity_clouds_omit_missing_areas_and_preserve_contiguous_boundaries() {
        val preparationOnly = listOf(
            TimelineElement.WarmupPill(
                isCurrent = true,
                isCompleted = false,
                progress = 0.5f,
                onSelect = {},
            ),
        )
        val split = listOf(
            TimelineElement.MobilityPill(false, false, 0f, {}),
            TimelineElement.BilateralSet(
                pageIndex = 0,
                label = "S1",
                state = WorkoutSetCardVisualState.FUTURE,
            ),
            TimelineElement.WarmupPill(false, false, 0f, {}),
        )

        assertEquals(
            listOf(ActivityCloudSegment(ActivityCloudArea.PREPARATION, 0, 1)),
            activityCloudSegments(preparationOnly),
        )
        assertEquals(
            listOf(
                ActivityCloudSegment(ActivityCloudArea.PREPARATION, 0, 1),
                ActivityCloudSegment(ActivityCloudArea.EFFECTIVE_SERIES, 1, 2),
                ActivityCloudSegment(ActivityCloudArea.PREPARATION, 2, 3),
            ),
            activityCloudSegments(split),
        )
    }

    @Test
    fun activity_cloud_segment_width_includes_internal_node_gaps() {
        val elements = listOf(
            TimelineElement.MobilityPill(false, false, 0f, {}),
            TimelineElement.WarmupPill(false, false, 0f, {}),
        )
        val segment = ActivityCloudSegment(ActivityCloudArea.PREPARATION, 0, 2)

        assertEquals(
            44f + 44f + 14f,
            activityCloudSegmentWidthDp(elements, segment, interElementGapDp = 14f),
            0.01f,
        )
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
