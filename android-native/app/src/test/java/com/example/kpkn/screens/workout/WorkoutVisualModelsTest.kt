package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVisualModelsTest {

    @Test
    fun header_title_font_scale_shrinks_with_length_never_grows_slot() {
        assertEquals(1.00f, workoutHeaderTitleFontScale(12), 0.001f)
        assertEquals(1.00f, workoutHeaderTitleFontScale(22), 0.001f)
        assertEquals(0.88f, workoutHeaderTitleFontScale(28), 0.001f)
        assertEquals(0.76f, workoutHeaderTitleFontScale(40), 0.001f)
        assertEquals(0.66f, workoutHeaderTitleFontScale(50), 0.001f)
        assertEquals(0.58f, workoutHeaderTitleFontScale(80), 0.001f)
        assertTrue(workoutHeaderTitleFontScale(80) < workoutHeaderTitleFontScale(12))
        assertEquals(
            "Press banca".length,
            workoutHeaderTitleGlyphCount("Press banca", emptyList()),
        )
        assertEquals(
            "Press".length + 3 + "Barra".length,
            workoutHeaderTitleGlyphCount("Press", listOf("Barra")),
        )
    }

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
    fun live_pager_viewport_adapt_scale_always_uses_uniform_xy() {
        val tokens = com.example.kpkn.screens.workout.components.WorkoutUiTokens
        val phone = tokens.livePagerViewportAdaptScale(
            availableWidth = androidx.compose.ui.unit.Dp(411f),
            availableHeight = androidx.compose.ui.unit.Dp(400f),
            godModeActive = false,
        )
        val compactPhone = tokens.livePagerViewportAdaptScale(
            availableWidth = androidx.compose.ui.unit.Dp(360f),
            availableHeight = androidx.compose.ui.unit.Dp(520f),
            godModeActive = false,
        )
        val flipCover = tokens.livePagerViewportAdaptScale(
            availableWidth = androidx.compose.ui.unit.Dp(280f),
            availableHeight = androidx.compose.ui.unit.Dp(520f),
            godModeActive = false,
        )
        val godShort = tokens.livePagerViewportAdaptScale(
            availableWidth = androidx.compose.ui.unit.Dp(411f),
            availableHeight = androidx.compose.ui.unit.Dp(400f),
            godModeActive = true,
        )
        assertEquals(400f / 520f, phone, 0.01f)
        assertEquals(360f / 411f, compactPhone, 0.01f)
        assertEquals(
            tokens.liveAdaptScale(
                availableWidth = androidx.compose.ui.unit.Dp(280f),
                availableHeight = androidx.compose.ui.unit.Dp(520f),
            ),
            flipCover,
            0.01f,
        )
        assertEquals(0.72f, flipCover, 0.01f)
        assertEquals(1f, godShort, 0.01f)
    }

    @Test
    fun live_pager_card_scale_grows_twenty_five_percent_from_compact() {
        assertEquals(
            1.20f * 0.85f * 1.25f * 1.25f,
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale,
            0.001f,
        )
        val compactSlot = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerSlotHeight
        val expected = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerBaseHeight *
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale *
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardHeightGrowFactor
        assertEquals(expected, compactSlot)
    }

    @Test
    fun live_pager_card_is_eleven_percent_thinner_on_x() {
        val xy = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale
        val slim = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardWidthSlimFactor
        assertEquals(0.89f * 0.90f * 0.93f, slim, 0.001f)
        assertEquals(xy * slim, xy * 0.89f * 0.90f * 0.93f, 0.001f)
        assertTrue(xy * slim < xy * 0.89f * 0.90f)
    }

    @Test
    fun live_pager_card_is_shorter_on_y_and_slimmer_on_x() {
        val xy = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale
        val slim = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardWidthSlimFactor
        val growY = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardHeightGrowFactor
        assertEquals(0.75f, growY, 0.001f)
        val scaleX = xy * slim
        val scaleY = xy * growY
        assertEquals(xy * 0.89f * 0.90f * 0.93f, scaleX, 0.001f)
        assertEquals(xy * 0.75f, scaleY, 0.001f)
        assertTrue(scaleY < xy)
        assertTrue(scaleX < xy * 0.89f * 0.90f)
        assertEquals(slim / growY, scaleX / scaleY, 0.001f)
    }

    @Test
    fun live_pager_uniform_xy_growth_keeps_aspect_ratio() {
        val xy = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale
        val slim = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardWidthSlimFactor
        val growY = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardHeightGrowFactor
        assertEquals(0.89f * 0.90f * 0.93f, slim, 0.001f)
        assertEquals(0.75f, growY, 0.001f)
        assertEquals(slim / growY, (xy * slim) / (xy * growY), 0.0001f)
    }

    @Test
    fun live_pager_22_9_leftover_must_not_stretch_card_height() {
        val tallNarrow = com.example.kpkn.ui.adapt.LiveViewportPolicyMath.compute(
            widthDp = 360f,
            heightDp = 880f,
        )
        assertTrue(tallNarrow.leftoverY > 0f)
        assertTrue(tallNarrow.cardHeightMul > 1f)
        val liveMul = 1f
        val xy = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale
        val scaleY = xy *
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardHeightGrowFactor *
            liveMul
        assertEquals(xy * 0.75f, scaleY, 0.001f)
        assertTrue(scaleY < xy)
    }

    @Test
    fun roadmap_carousel_delta_centers_item_in_viewport() {
        assertEquals(
            0,
            com.example.kpkn.screens.workout.components.lazyItemDeltaToCenterPx(
                itemOffsetPx = 100,
                itemSizePx = 80,
                viewportSizePx = 280,
            ),
        )
        assertTrue(
            com.example.kpkn.screens.workout.components.lazyItemDeltaToCenterPx(
                itemOffsetPx = 0,
                itemSizePx = 80,
                viewportSizePx = 280,
            ) < 0,
        )
        assertTrue(
            com.example.kpkn.screens.workout.components.lazyItemDeltaToCenterPx(
                itemOffsetPx = 200,
                itemSizePx = 80,
                viewportSizePx = 280,
            ) > 0,
        )
    }

    @Test
    fun live_pager_card_top_nudge_is_five_percent_of_slot() {
        assertEquals(
            0.05f,
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardTopNudgeFraction,
            0.001f,
        )
        val identity = com.example.kpkn.ui.adapt.LiveViewportPolicy.Identity
        assertEquals(0.05f, identity.topNudgeFraction, 0.001f)
        assertEquals(1f, identity.headerMul, 0.001f)
        assertEquals(1f, identity.cardWidthMul, 0.001f)
        assertEquals(1f, identity.cardHeightMul, 0.001f)
        assertEquals(0f, identity.cardPageFillX, 0.001f)
    }

    @Test
    fun live_pager_peek_on_canvas_is_unchanged() {
        val cardScale = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale
        val peek = com.example.kpkn.ui.adapt.LiveViewportPolicyMath.livePagerPeekFraction(
            availableWidthDp = 411f,
            cardScale = cardScale,
            shouldReflow = false,
            cardWidthMul = 1f,
            minPeekFraction = 0.12f,
        )
        val legacy = (0.22f * cardScale - (cardScale - 1f) / 2f).coerceIn(0.14f, 0.26f)
        assertEquals(legacy, peek, 0.001f)
    }

    @Test
    fun live_pager_peek_cover_stays_inside_viewport() {
        val policy = com.example.kpkn.ui.adapt.LiveViewportPolicyMath.compute(
            widthDp = 280f,
            heightDp = 520f,
        )
        assertEquals(1f, policy.cardHeightMul, 0.01f)
        val peek = com.example.kpkn.ui.adapt.LiveViewportPolicyMath.livePagerPeekFraction(
            availableWidthDp = 280f,
            cardScale = 1.20f * 0.85f * 0.72f,
            shouldReflow = true,
            cardWidthMul = policy.cardWidthMul,
            minPeekFraction = policy.minPeekFraction,
        )
        assertTrue(1f - 2f * peek in 0f..1f)
        assertTrue(peek >= policy.minPeekFraction - 0.001f)
    }

    @Test
    fun live_pager_stable_height_covers_expanded_normal_slot() {
        val stable = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerNormalExpandedBaseHeight *
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardScale *
            com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerCardHeightGrowFactor
        val compactSlot = com.example.kpkn.screens.workout.components.WorkoutUiTokens.LivePagerSlotHeight
        assert(stable > compactSlot)
    }

    @Test
    fun stepper_layout_expands_only_the_active_round() {
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

        val exclusive = stepperExpandedRounds(naturalActiveRound = 1)
        assertEquals(setOf(1), exclusive)
        val onlyNatural = stepperLayoutElements(elements, expandedRounds = exclusive)
        assertEquals(listOf(r0, r1, b, r2), onlyNatural)

        val peekedFuture = stepperExpandedRounds(naturalActiveRound = 0, extraExpandedRounds = setOf(2))
        assertEquals(setOf(2), peekedFuture)
        val peekedLayout = stepperLayoutElements(elements, expandedRounds = peekedFuture)
        assertEquals(listOf(r0, r1, r2, c), peekedLayout)

        val restWins = stepperExpandedRounds(
            naturalActiveRound = 0,
            restExpandedRound = 1,
        )
        assertEquals(setOf(1), restWins)
    }

    @Test
    fun stepper_rail_slots_group_round_sets_and_keep_rest_outside() {
        val r0 = TimelineElement.RoundBadge(0, true, false, 0)
        val s0 = TimelineElement.BilateralSet(
            roundIndex = 0,
            pageIndex = 0,
            label = "S1",
            state = WorkoutSetCardVisualState.ACTIVE,
        )
        val rest = TimelineElement.RestPill(pageIndex = 1, progress = 0.5f, remainingLabel = "0:30")
        val s1 = TimelineElement.BilateralSet(
            roundIndex = 0,
            pageIndex = 2,
            label = "S2",
            state = WorkoutSetCardVisualState.FUTURE,
        )
        val r1 = TimelineElement.RoundBadge(1, false, false, 3)
        val slots = stepperRailSlots(listOf(r0, s0, rest, s1, r1))
        assertEquals(3, slots.size)
        val cluster = slots[0] as StepperRailSlot.RoundCluster
        assertEquals(r0, cluster.badge)
        assertEquals(listOf(s0, s1), cluster.sets)
        assertEquals(StepperRailSlot.Loose(rest), slots[1])
        val collapsed = slots[2] as StepperRailSlot.RoundCluster
        assertEquals(r1, collapsed.badge)
        assertTrue(collapsed.sets.isEmpty())

        val expandedWidth = roundClusterWidthDp(listOf(s0, s1))
        val collapsedWidth = roundClusterWidthDp(emptyList())
        assertTrue(expandedWidth > collapsedWidth)
        assertEquals(collapsedWidth, roundClusterWidthDp(emptyList()), 0.01f)
        assertEquals(ActivityCloudArea.SUPERSERIE, stepperCloudAreaForSlot(cluster))
        assertEquals(ActivityCloudArea.SUPERSERIE, stepperCloudAreaForSlot(collapsed))
        val merged = mergeStepperCloudPieces(
            slots = listOf(cluster, StepperRailSlot.Loose(rest), collapsed),
            slotWidthsDp = listOf(expandedWidth, 108f, collapsedWidth),
            gapDp = 10f,
        )
        assertEquals(
            listOf(ActivityCloudArea.SUPERSERIE, ActivityCloudArea.DESCANSO, ActivityCloudArea.SUPERSERIE),
            merged.map { it.area },
        )
        assertEquals(89f, expandedWidth, 0.01f)
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
    fun merge_cloud_pieces_one_nube_for_three_bilateral_series() {
        val elements = listOf(
            TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.ACTIVE),
            TimelineElement.BilateralSet(pageIndex = 1, label = "S2", state = WorkoutSetCardVisualState.FUTURE),
            TimelineElement.BilateralSet(pageIndex = 2, label = "S3", state = WorkoutSetCardVisualState.FUTURE),
        )
        val slots = stepperRailSlots(elements)
        val pieces = mergeStepperCloudPieces(slots, slots.map { stepperRailSlotWidthDp(it) }, gapDp = 10f)
        assertEquals(1, pieces.size)
        assertEquals(ActivityCloudArea.EFFECTIVE_SERIES, pieces.single().area)
        assertEquals(
            stepperRailSlotWidthDp(slots[0]) + 10f + stepperRailSlotWidthDp(slots[1]) + 10f + stepperRailSlotWidthDp(slots[2]),
            pieces.single().width.value,
            0.01f,
        )
    }

    @Test
    fun merge_cloud_pieces_one_nube_for_three_unilateral_series() {
        fun uni(set: Int, left: Int, right: Int) = TimelineElement.UnilateralSet(
            setLabel = "S$set",
            leftPageIndex = left,
            leftState = WorkoutSetCardVisualState.FUTURE,
            rightPageIndex = right,
            rightState = WorkoutSetCardVisualState.FUTURE,
        )
        val elements = listOf(uni(1, 0, 1), uni(2, 2, 3), uni(3, 4, 5))
        val slots = stepperRailSlots(elements)
        val pieces = mergeStepperCloudPieces(slots, slots.map { stepperRailSlotWidthDp(it) }, gapDp = 12f)
        assertEquals(1, pieces.size)
        assertEquals(ActivityCloudArea.EFFECTIVE_SERIES, pieces.single().area)
    }

    @Test
    fun merge_cloud_pieces_one_nube_for_three_rounds() {
        fun round(index: Int) = TimelineElement.RoundBadge(
            roundIndex = index,
            isCurrentRound = index == 0,
            isAllDone = false,
            firstPageIndex = index,
        )
        fun set(index: Int, roundIndex: Int) = TimelineElement.BilateralSet(
            roundIndex = roundIndex,
            pageIndex = index,
            label = "S1",
            state = WorkoutSetCardVisualState.FUTURE,
        )
        val elements = listOf(
            round(0), set(0, 0),
            round(1), set(1, 1),
            round(2), set(2, 2),
        )
        val slots = stepperRailSlots(elements)
        assertEquals(3, slots.size)
        val collapsedWidths = slots.map { slot ->
            when (slot) {
                is StepperRailSlot.RoundCluster -> roundClusterWidthDp(emptyList())
                is StepperRailSlot.Loose -> stepperRailSlotWidthDp(slot)
            }
        }
        val pieces = mergeStepperCloudPieces(slots, collapsedWidths, gapDp = 10f)
        assertEquals(1, pieces.size)
        assertEquals(ActivityCloudArea.SUPERSERIE, pieces.single().area)
    }

    @Test
    fun merge_cloud_pieces_prep_then_effective_and_rest_splits() {
        val elements = listOf(
            TimelineElement.MobilityPill(false, false, 0f, {}),
            TimelineElement.WarmupPill(false, false, 0f, {}),
            TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.RestPill(pageIndex = 1, progress = 0.2f, remainingLabel = "1:00"),
            TimelineElement.BilateralSet(pageIndex = 2, label = "S2", state = WorkoutSetCardVisualState.FUTURE),
        )
        val slots = stepperRailSlots(elements)
        val pieces = mergeStepperCloudPieces(slots, slots.map { stepperRailSlotWidthDp(it) }, gapDp = 10f)
        assertEquals(
            listOf(
                ActivityCloudArea.PREPARATION,
                ActivityCloudArea.EFFECTIVE_SERIES,
                ActivityCloudArea.DESCANSO,
                ActivityCloudArea.EFFECTIVE_SERIES,
            ),
            pieces.map { it.area },
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
    fun session_chronometer_shows_remaining_over_limit_when_capped() {
        assertEquals(
            "12:30 / 45:00",
            formatSessionChronometerText(
                hasLimit = true,
                remainingSeconds = 12 * 60 + 30,
                elapsedSeconds = 100,
                targetMinutes = 45,
            ),
        )
    }

    @Test
    fun session_chronometer_shows_elapsed_without_limit() {
        assertEquals(
            "05:04",
            formatSessionChronometerText(
                hasLimit = false,
                remainingSeconds = 0,
                elapsedSeconds = 5 * 60 + 4,
                targetMinutes = 45,
            ),
        )
    }

    @Test
    fun session_chronometer_overtime_keeps_limit_visible() {
        assertEquals(
            "-00:15 / 30:00",
            formatSessionChronometerText(
                hasLimit = true,
                remainingSeconds = -15,
                elapsedSeconds = 30 * 60 + 15,
                targetMinutes = 30,
            ),
        )
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

    @Test
    fun pager_accent_uses_cover_emphasis_instead_of_celeste_or_green() {
        val accent = androidx.compose.ui.graphics.Color(0xFFE08E45)
        val scheme = androidx.compose.material3.darkColorScheme()
        assertEquals(
            accent,
            workoutSetPagerAccent(
                WorkoutSetCardVisualState.ACTIVE,
                scheme,
                sessionAccentColor = accent,
            ),
        )
        val completed = workoutSetPagerAccent(
            WorkoutSetCardVisualState.COMPLETED,
            scheme,
            sessionAccentColor = accent,
        )
        val warmup = workoutSetPagerAccent(
            WorkoutSetCardVisualState.FUTURE,
            scheme,
            isWarmupOrFeedback = true,
            sessionAccentColor = accent,
        )
        assertTrue(completed != androidx.compose.ui.graphics.Color(0xFF66BB6A))
        assertTrue(completed != androidx.compose.ui.graphics.Color(0xFF38BDF8))
        assertTrue(warmup != androidx.compose.ui.graphics.Color(0xFF448AFF))
        assertTrue(warmup != androidx.compose.ui.graphics.Color(0xFF38BDF8))
    }

    @Test
    fun roadmap_uncategorized_uses_cover_accent_not_celeste() {
        val cover = androidx.compose.ui.graphics.Color(0xFFE08E45)
        val uncategorized = com.example.kpkn.data.models.SessionPart(
            id = "loose",
            name = "Sin categoría",
        )
        val grouped = com.example.kpkn.data.models.SessionPart(
            id = "g1",
            name = "Empuje",
            color = "gradient://violet",
        )
        val looseAccent = com.example.kpkn.screens.workout.components.roadmapAccentForPart(
            uncategorized,
            cover,
        )
        val groupAccent = com.example.kpkn.screens.workout.components.roadmapAccentForPart(
            grouped,
            cover,
        )
        assertEquals(cover, looseAccent)
        assertTrue(looseAccent != androidx.compose.ui.graphics.Color(0xFF38BDF8))
        assertTrue(groupAccent != cover)
        assertTrue(groupAccent != androidx.compose.ui.graphics.Color(0xFF38BDF8))
    }
}
