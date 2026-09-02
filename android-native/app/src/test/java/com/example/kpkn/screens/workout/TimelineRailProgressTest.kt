package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRailProgressTest {
    @Test
    fun empty_isZero() {
        assertEquals(0f, timelineRailProgress(emptyList()), 0.001f)
        assertEquals(0f, timelineRailCursorProgress(emptyList(), 0), 0.001f)
    }

    @Test
    fun mobilityWarmupAndSets_averageEqualWeights() {
        val elements = listOf(
            TimelineElement.MobilityPill(isCurrent = true, isCompleted = false, progress = 0.5f, onSelect = {}),
            TimelineElement.WarmupPill(isCurrent = false, isCompleted = false, progress = 0f, onSelect = {}),
            TimelineElement.BilateralSet(
                pageIndex = 0,
                label = "S1",
                state = WorkoutSetCardVisualState.FUTURE,
            ),
            TimelineElement.BilateralSet(
                pageIndex = 1,
                label = "S2",
                state = WorkoutSetCardVisualState.COMPLETED,
            ),
        )
        // 0.5 + 0 + 0 + 1 = 1.5 / 4 = 0.375
        assertEquals(0.375f, timelineRailProgress(elements), 0.001f)
    }

    @Test
    fun cursorProgress_followsActiveSet_notAverageCompletion() {
        val elements = listOf(
            TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.BilateralSet(pageIndex = 1, label = "S2", state = WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.BilateralSet(pageIndex = 2, label = "S3", state = WorkoutSetCardVisualState.ACTIVE),
        )
        // Cursor on S3 (index 2): 2/2 = 1.0
        assertEquals(1f, timelineRailCursorProgress(elements, 2), 0.001f)
        // Navigate back to S2 (index 1): 1/2 = 0.5 — not ante-anterior (0)
        assertEquals(0.5f, timelineRailCursorProgress(elements, 1), 0.001f)
        // S1 (index 0): 0/2 = 0
        assertEquals(0f, timelineRailCursorProgress(elements, 0), 0.001f)
    }

    @Test
    fun cursorProgress_restPill_extendsFill() {
        val elements = listOf(
            TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.RestPill(pageIndex = 1, progress = 0.5f, remainingLabel = "0:45"),
        )
        // Rest at index 1, half done: (1 + 0.5) / 1 = 1.0 capped
        assertEquals(1f, timelineRailCursorProgress(elements, 1), 0.001f)
    }

    @Test
    fun roundBadges_areIgnored() {
        val elements = listOf(
            TimelineElement.RoundBadge(
                roundIndex = 0,
                isCurrentRound = true,
                isAllDone = false,
                firstPageIndex = 0,
            ),
            TimelineElement.BilateralSet(
                pageIndex = 0,
                label = "S1",
                state = WorkoutSetCardVisualState.COMPLETED,
            ),
        )
        assertEquals(1f, timelineRailProgress(elements), 0.001f)
        assertEquals(1f, timelineRailCursorProgress(elements, 1), 0.001f)
    }

    @Test
    fun restPageInsertIndex_afterLoggedSet() {
        val pages = listOf(
            WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 0, exerciseId = "ex1"),
            WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 1, exerciseId = "ex1"),
            WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 2, exerciseId = "ex1"),
        )
        assertEquals(1, restPageInsertIndex(pages, "ex1_0", "ex1"))
    }

    @Test
    fun restLiveCardStaysVisibleWhenOverlayIsMinimized() {
        assertTrue(shouldRenderRestLiveCard(isOverlayMinimized = false))
        assertTrue(shouldRenderRestLiveCard(isOverlayMinimized = true))
    }

    @Test
    fun restLiveCardUsesSameWrapSlotAsSeriesCards() {
        assertTrue(livePagerCardAllowsContentExpansion(LivePageType.REST))
        assertTrue(livePagerCardAllowsContentExpansion(LivePageType.NORMAL))
        assertTrue(livePagerCardAllowsContentExpansion(LivePageType.WARMUP))
        assertTrue(livePagerCardAllowsContentExpansion(LivePageType.MOBILITY))
        assertFalse(livePagerCardAllowsContentExpansion(LivePageType.CARDIO))
        assertTrue(livePagerCardLocksToWorkingSetHeight(LivePageType.REST))
        assertTrue(livePagerCardLocksToWorkingSetHeight(LivePageType.WARMUP))
        assertTrue(livePagerCardLocksToWorkingSetHeight(LivePageType.MOBILITY))
        assertFalse(livePagerCardLocksToWorkingSetHeight(LivePageType.NORMAL))
        assertFalse(livePagerCardLocksToWorkingSetHeight(LivePageType.CARDIO))
    }

    @Test
    fun timelineRestInsertIndex_afterCompletedSetNode() {
        val elements = listOf(
            TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.BilateralSet(pageIndex = 2, label = "S2", state = WorkoutSetCardVisualState.FUTURE),
        )
        assertEquals(1, timelineRestInsertIndex(elements, restPageIndex = 1))
    }

    @Test
    fun activityCloudSegments_restPill_getsDescansoArea() {
        val elements = listOf(
            TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.RestPill(pageIndex = 1, progress = 0.2f, remainingLabel = "1:00"),
            TimelineElement.BilateralSet(pageIndex = 2, label = "S2", state = WorkoutSetCardVisualState.FUTURE),
        )
        val segments = activityCloudSegments(elements)
        assertEquals(3, segments.size)
        assertEquals(ActivityCloudArea.EFFECTIVE_SERIES, segments[0].area)
        assertEquals(ActivityCloudArea.DESCANSO, segments[1].area)
        assertEquals(ActivityCloudArea.EFFECTIVE_SERIES, segments[2].area)
        val gap = 10f
        val slots = stepperRailSlots(elements)
        val inflated = inflateStepperSlotWidthsForClouds(
            slots,
            slots.map { stepperRailSlotWidthDp(it) },
            gap,
        )
        val pieces = mergeStepperCloudPieces(slots, inflated, gap)
        pieces.forEach { piece ->
            val area = piece.area ?: return@forEach
            val label = activityCloudLabelWidthDp(piece.width.value, area)
            assertTrue(label + 0.01f >= activityCloudMinLabelWidthDp(area))
            assertTrue(label <= piece.width.value + 0.01f)
        }
        val widths = pieces.map { it.width.value }
        val labels = pieces.map { piece ->
            piece.area?.let { activityCloudLabelWidthDp(piece.width.value, it) } ?: 0f
        }
        assertFalse(
            activityCloudLabelsOverlap(
                leftSegmentWidthDp = widths[1],
                leftLabelWidthDp = labels[1],
                gapDp = gap,
                rightSegmentWidthDp = widths[2],
                rightLabelWidthDp = labels[2],
            ),
        )
        assertFalse(
            activityCloudLabelsOverlap(
                leftSegmentWidthDp = widths[0],
                leftLabelWidthDp = labels[0],
                gapDp = gap,
                rightSegmentWidthDp = widths[1],
                rightLabelWidthDp = labels[1],
            ),
        )
    }
}
