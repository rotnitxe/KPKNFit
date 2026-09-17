package com.example.kpkn.screens.programdetail.components.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MacrocycleToolbarUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun complexInsight() = TemporalInsight(
        isSimple = false,
        cycleWeeks = null,
        loopCadenceCycles = null,
        loopLengthWeeks = null,
        blocks = 4,
        mesocycles = 4,
        weeks = 11,
        hadLoopsOrEvents = false,
    )

    private fun stats() = ProgramStats(weeks = 11, sessions = 44, mesos = 4, blocks = 4)

    @Test
    fun narrowWidth_historyButton_staysVisibleEnabledAndInsideBounds() {
        var opened = false
        var toolbarBounds: Rect? = null
        var historyBounds: Rect? = null
        composeRule.setContent {
            MaterialTheme {
                BoxWithConstraints(modifier = Modifier.width(320.dp)) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.onGloballyPositioned { toolbarBounds = it.boundsInRoot() },
                    ) {
                        MacrocycleToolbar(
                            insight = complexInsight(),
                            stats = stats(),
                            keyDatesCount = 0,
                            hasTimelineStartDate = false,
                            showRoadmap = false,
                            isSimpleCalendarized = false,
                            snapshotCount = 1,
                            onToggleRoadmap = {},
                            onOpenKeyDates = {},
                            onOpenLibrary = {},
                            onOpenLoops = {},
                            onOpenSimpleCalendarization = {},
                            onOpenSnapshots = { opened = true },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("macrocycle_history_button").assertIsDisplayed()
        composeRule.onNodeWithTag("macrocycle_history_button").assertIsEnabled()
        composeRule.onNodeWithText("Historial (1)").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "macrocycle_history_button",
            useUnmergedTree = true,
        ).fetchSemanticsNode().let { node ->
            historyBounds = node.boundsInRoot
        }
        val toolbar = toolbarBounds
        val history = historyBounds
        assertTrue("toolbar sin bounds", toolbar != null && !toolbar.isEmpty)
        assertTrue("historial sin bounds", history != null && !history.isEmpty)
        if (toolbar != null && history != null) {
            assertTrue(
                "Historial fuera del toolbar: $history vs $toolbar",
                toolbar.contains(history.topLeft) && toolbar.contains(history.bottomRight),
            )
            // Un botón compuesto carácter por carácter mediría alto >> ancho;
            // un chip legible es netamente más ancho que alto.
            assertTrue(
                "Historial con geometría ilegible (posible wrap por carácter): $history",
                history.width > history.height,
            )
        }
        composeRule.onNodeWithTag("macrocycle_history_button").performClick()
        composeRule.waitForIdle()
        assertTrue(opened)
    }
}
