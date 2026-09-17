package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLiveMetricsTest {

    @Test
    fun peek_clamped_to_88_128_for_short_normal_long_viewports() {
        assertEquals(88, clampPeekForTests(400f))
        assertTrue(computeLivePeekHeightDp(600f) in 88f..128f)
        assertEquals(108, clampPeekForTests(600f))
        assertEquals(128, clampPeekForTests(900f))
        assertEquals(128, clampPeekForTests(1200f))
    }

    @Test
    fun stage_metrics_keep_active_page_between_fraction_and_peek_budget() {
        val m = computeLiveStageMetrics(800f)
        assertTrue(m.peekHeightDp in 88f..128f)
        assertTrue(m.activePageHeightDp <= 800f - m.peekHeightDp - m.pageGapDp + 0.01f)
        assertTrue(m.activePageHeightDp >= 800f * 0.68f - 0.01f || m.activePageHeightDp <= 800f - m.peekHeightDp)
        assertTrue(m.interExerciseGapDp in 20f..32f)
        assertEquals(23f, m.railVisualWidthDp)
        assertEquals(48f, m.railHitWidthDp)
        assertEquals(18f, m.addTailDp)
    }

    @Test
    fun adaptive_window_fits_all_when_budget_allows() {
        val nodes = (1..5).map {
            LiveRoadmapNode(
                id = "n$it",
                kind = LiveRoadmapNodeKind.WORKING,
                label = "$it",
                contentDescription = "Serie $it",
                isActive = it == 3,
            )
        }
        val window = computeLiveRoadmapWindow(nodes, activeIndex = 2, availableHeightDp = 400f)
        assertEquals(5, window.visibleNodes.size)
        assertEquals(0, window.hiddenBefore)
        assertEquals(0, window.hiddenAfter)
        assertFalse(window.showsContinuityBefore)
    }

    @Test
    fun adaptive_window_centers_active_for_twelve_plus_nodes() {
        val nodes = (1..14).map {
            LiveRoadmapNode(
                id = "n$it",
                kind = LiveRoadmapNodeKind.WORKING,
                label = "$it",
                contentDescription = "Serie $it",
                isActive = it == 8,
            )
        }
        // ~3 nodes fit: 23 + 11 gap ≈ 34 per node → 100/34 ≈ 2-3
        val window = computeLiveRoadmapWindow(
            nodes = nodes,
            activeIndex = 7,
            availableHeightDp = 100f,
            nodeHeightDp = 23f,
            nodeGapDp = 11f,
        )
        assertTrue(window.visibleNodes.size < nodes.size)
        assertTrue(window.hiddenBefore > 0 || window.hiddenAfter > 0)
        assertTrue(window.visibleNodes.any { it.id == "n8" })
        assertEquals("n8", window.visibleNodes[window.activeVisibleIndex].id)
    }

    @Test
    fun labels_map_mobility_approach_and_working() {
        assertEquals("M", liveNodeShortLabel(LiveRoadmapNodeKind.MOBILITY, "MOV"))
        assertEquals("A", liveNodeShortLabel(LiveRoadmapNodeKind.APPROACH, "APR"))
        assertEquals("C", liveNodeShortLabel(LiveRoadmapNodeKind.CARDIO, "Cardio"))
        assertEquals("3", liveNodeShortLabel(LiveRoadmapNodeKind.WORKING, "Serie 3"))
        assertEquals("Movilidad", liveNodeContentDescription(LiveRoadmapNodeKind.MOBILITY, "M"))
        assertEquals("Aproximación", liveNodeContentDescription(LiveRoadmapNodeKind.APPROACH, "A"))
    }

    @Test
    fun exact_anchors_differ_for_unilateral_sides() {
        val left = liveNodeExactAnchorY(
            kind = LiveRoadmapNodeKind.UNILATERAL_SIDE,
            nodeTopPx = 100f,
            nodeHeightPx = 40f,
            side = "left",
            sideRowCount = 2,
        )
        val right = liveNodeExactAnchorY(
            kind = LiveRoadmapNodeKind.UNILATERAL_SIDE,
            nodeTopPx = 100f,
            nodeHeightPx = 40f,
            side = "right",
            sideRowCount = 2,
        )
        assertEquals(110f, left, 0.01f)
        assertEquals(130f, right, 0.01f)
        val working = liveNodeExactAnchorY(
            kind = LiveRoadmapNodeKind.WORKING,
            nodeTopPx = 100f,
            nodeHeightPx = 40f,
        )
        assertEquals(120f, working, 0.01f)
    }

    @Test
    fun card_center_stays_within_two_dp_across_first_last() {
        val first = liveCardFixedCenterYDp(stageHeightDp = 700f, headerReserveDp = 96f, dockReserveDp = 132f)
        val last = liveCardFixedCenterYDp(stageHeightDp = 700f, headerReserveDp = 96f, dockReserveDp = 132f)
        assertTrue(liveCardCenterDriftDp(first, last) <= 2f)
    }

    @Test
    fun isthmus_path_starts_at_node_and_opens_to_card() {
        val pts = liveIsthmusPathPoints(
            LiveIsthmusGeometry(
                nodeAnchor = androidx.compose.ui.geometry.Offset(24f, 200f),
                cardLeftCenter = androidx.compose.ui.geometry.Offset(120f, 260f),
                neckWidthPx = 6f,
                overlapPx = 8f,
            ),
        )
        assertEquals(4, pts.size)
        assertEquals(24f, pts.first().x, 0.01f)
        assertTrue(pts.last().x > pts.first().x)
    }
}
