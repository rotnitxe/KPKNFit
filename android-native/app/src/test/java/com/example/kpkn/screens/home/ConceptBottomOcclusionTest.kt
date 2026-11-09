package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.KpknBottomBar
import com.example.kpkn.ui.components.KpknBottomBarItem
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.MeasuredKpknBottomDock
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class ConceptBottomOcclusionTest {
    @get:Rule val rule = createComposeRule()

    private val injectedNavigationInset = 24.dp
    private val expectedDockHeight = 64.dp + 8.dp + injectedNavigationInset

    @Test
    fun sharedMeasuredDockClearsLibraryTopicAndReadingActionsWithOneSafeInset() {
        rule.setContent {
            val density = LocalDensity.current
            val haze = remember { HazeState() }
            var dockHeightPx by remember { mutableIntStateOf(0) }
            val dockHeight = with(density) { dockHeightPx.toDp() }.takeIf { it > 0.dp } ?: 72.dp
            CompositionLocalProvider(
                LocalConceptsBottomOcclusion provides dockHeight,
                LocalHazeState provides haze,
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    Box(
                        Modifier.size(360.dp, 800.dp)
                            .hazeSource(state = haze)
                            .testTag("concept_test_host"),
                    ) {
                        ConceptosClaveScreen(expandedConceptId = "creatina", onBack = {})
                        MeasuredKpknBottomDock(
                            hazeState = haze,
                            onMeasuredHeightChanged = { dockHeightPx = it },
                            modifier = Modifier.align(Alignment.BottomCenter).testTag("measured_navigation_dock"),
                            safeAreaInsets = WindowInsets(bottom = injectedNavigationInset),
                        ) {
                            KpknBottomBar(
                                items = listOf(
                                    KpknBottomBarItem("home", "Inicio", KpknLineIcons.Home),
                                    KpknBottomBarItem("training", "Entreno", KpknLineIcons.Training),
                                    KpknBottomBarItem("body", "Cuerpo", KpknLineIcons.Body),
                                ),
                                selectedRoute = "home",
                                onSelect = {},
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("measured_navigation_dock").assertHeightIsEqualTo(expectedDockHeight)
        rule.onNodeWithTag("concept_bottom_fade").assertHeightIsEqualTo(expectedDockHeight + ConceptBottomFadeLead)

        // The deep-link reading's final CTA can scroll completely above the measured dock and remains tappable.
        val readingTag = "concept_reading_creatina"
        rule.onNodeWithTag(readingTag).performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertFadeAndClearance()
        rule.onNodeWithTag("concept_open_library").assertIsDisplayed()
        assertActionAboveDock("concept_open_library")
        rule.onNodeWithTag("concept_open_library").performClick()

        // Library keeps its single title/search glass island while the final indexed concept stays above the dock.
        rule.onNodeWithTag("concept_search_toggle").assertIsDisplayed()
        rule.onNodeWithTag("concept_search_field").assertDoesNotExist()
        rule.onNodeWithTag("concept_top_button").assertDoesNotExist()
        val libraryTag = "concept_library"
        rule.onNodeWithTag(libraryTag).performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertFadeAndClearance()
        rule.onNodeWithTag("concept_library_last_action").assertIsDisplayed()
        assertActionAboveDock("concept_library_last_action")
        rule.onNodeWithTag("concept_library_last_action").performClick()
        rule.onNodeWithTag("concept_top_button").assertIsDisplayed().performClick()
        rule.onNodeWithTag("concept_library").assertIsDisplayed()

    }

    @Test
    fun topicPageFinalActionClearsTheMeasuredBottomDock() {
        rule.setContent {
            val density = LocalDensity.current
            val haze = remember { HazeState() }
            var dockHeightPx by remember { mutableIntStateOf(0) }
            val dockHeight = with(density) { dockHeightPx.toDp() }.takeIf { it > 0.dp } ?: expectedDockHeight
            CompositionLocalProvider(
                LocalConceptsBottomOcclusion provides dockHeight,
                LocalHazeState provides haze,
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    Box(
                        Modifier.size(360.dp, 800.dp).hazeSource(state = haze).testTag("concept_test_host"),
                    ) {
                        ConceptosClaveScreen(expandedConceptId = null, onBack = {})
                        MeasuredKpknBottomDock(
                            hazeState = haze,
                            onMeasuredHeightChanged = { dockHeightPx = it },
                            modifier = Modifier.align(Alignment.BottomCenter).testTag("topic_measured_dock"),
                            safeAreaInsets = WindowInsets(bottom = injectedNavigationInset),
                        ) {
                            KpknBottomBar(
                                items = listOf(
                                    KpknBottomBarItem("home", "Inicio", KpknLineIcons.Home),
                                    KpknBottomBarItem("training", "Entreno", KpknLineIcons.Training),
                                    KpknBottomBarItem("body", "Cuerpo", KpknLineIcons.Body),
                                ),
                                selectedRoute = "home",
                                onSelect = {},
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("topic_measured_dock").assertHeightIsEqualTo(expectedDockHeight)
        val categoryTag = "concept_topic_${ConceptCategory.SUPPLEMENTS.name}"
        rule.onNodeWithTag("concept_library").performScrollToNode(hasTestTag(categoryTag))
        liftTargetAboveDock("concept_library", expectedDockHeight)
        assertActionAboveDock(categoryTag)
        rule.onNodeWithTag(categoryTag).assertIsDisplayed().performClick()
        val topicTag = "concept_topic_page_${ConceptCategory.SUPPLEMENTS.name}"
        rule.waitForIdle()
        rule.onNodeWithTag(topicTag).assertExists()
        rule.onNodeWithTag(topicTag).performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertFadeAndClearance()
        rule.onNodeWithTag("concept_next_topic").assertIsDisplayed()
        assertActionAboveDock("concept_next_topic")
        rule.onNodeWithTag("concept_next_topic").performClick()
        rule.onNodeWithTag(topicTag).assertDoesNotExist()
    }

    private fun assertFadeAndClearance() {
        rule.onNodeWithTag("concept_bottom_fade").assertHeightIsEqualTo(expectedDockHeight + ConceptBottomFadeLead)
        rule.onNodeWithTag("concept_bottom_clearance").assertHeightIsEqualTo(expectedDockHeight)
    }

    private fun assertActionAboveDock(tag: String) {
        val hostBottom = rule.onNodeWithTag("concept_test_host").fetchSemanticsNode().boundsInRoot.bottom
        val dockTop = hostBottom - with(rule.density) { expectedDockHeight.toPx() }
        val actionBounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag empieza dentro del dock: $actionBounds / top=$dockTop", actionBounds.top < dockTop)
        assertTrue("$tag queda cubierto por el dock: $actionBounds / top=$dockTop", actionBounds.bottom <= dockTop + 1f)
    }

    private fun liftTargetAboveDock(scrollTag: String, dockHeight: androidx.compose.ui.unit.Dp) {
        val scrollable = rule.onNodeWithTag(scrollTag)
        val size = scrollable.fetchSemanticsNode().size
        val safeBottom = with(rule.density) { (dockHeight + 8.dp).toPx() }
        val drag = with(rule.density) { 130.dp.toPx() }
        scrollable.performTouchInput {
            swipe(
                start = Offset(size.width / 2f, size.height - safeBottom),
                end = Offset(size.width / 2f, size.height - safeBottom - drag),
                durationMillis = 300,
            )
        }
        rule.waitForIdle()
    }
}
