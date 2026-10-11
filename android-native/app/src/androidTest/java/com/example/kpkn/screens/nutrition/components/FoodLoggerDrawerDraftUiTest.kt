package com.example.kpkn.screens.nutrition.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP-U7 (C5) · The logger keeps its draft when the composition is recreated (rotation, folding, the state-restoration
 * round trip): typed text, interpreted foods and the chosen meal. The draft lives in the FoodLoggerViewModel owned by the
 * activity here (the NavBackStackEntry in the app), so the emulated restore recreates the content but not the holder.
 *
 * The interpretation shows an indeterminate progress panel (an infinite animation) that keeps Compose from ever being
 * idle: the clock is driven by hand while the analysis runs and handed back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class FoodLoggerDrawerDraftUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The static catalog is enough for these descriptions: no 70 MB catalog import behind the test. */
    private val noCatalogImport = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resetCalibrationSingleton()
        ProgramRepository.initForTests(context)
        NutritionRepository.initForTests(context, noCatalogImport)
    }

    @After
    fun tearDown() {
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        resetCalibrationSingleton()
    }

    /**
     * The interpretation reads the calibration profile through a process-wide singleton that caches the app database.
     * `ProgramRepository.initForTests` closes that database, so a second test would find the cached repository over a
     * closed Room (every query is cancelled). Dropping the cached instance makes each test open its own.
     */
    private fun resetCalibrationSingleton() {
        val field = NutritionCalibrationRepository::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    @Composable
    private fun LoggerUnderTest(initialDescription: String? = null, onDismiss: () -> Unit = {}) {
        MaterialTheme {
            FoodLoggerDrawer(
                nutritionRepo = NutritionRepository.getInstance(),
                onSave = { _, _ -> },
                onDismiss = onDismiss,
                isOpen = true,
                foodDatabase = emptyList(),
                initialDate = "2026-10-03",
                initialMealType = MealType.LUNCH,
                initialDescription = initialDescription,
            )
        }
    }

    /** The description box, told apart from the correction field a card may show by the text it holds. */
    private fun descriptionField(text: String): SemanticsMatcher = hasSetTextAction() and hasText(text)

    private fun loggerViewModel(): FoodLoggerViewModel =
        ViewModelProvider(composeRule.activity).get(FOOD_LOGGER_VIEW_MODEL_KEY, FoodLoggerViewModel::class.java)

    /**
     * The sheet is a lazy list: an item that is scrolled out is not composed, so it is not in the semantics tree.
     * Walks the list item by item until one matching [matcher] is composed (the list reports its item count).
     */
    private fun scrollListTo(matcher: SemanticsMatcher) {
        fun isComposed() = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        if (isComposed()) return
        val list = composeRule.onAllNodes(hasScrollToIndexAction()).onFirst()
        val rows = list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount
        for (index in 0 until rows) {
            list.performScrollToIndex(index)
            if (isComposed()) return
        }
        throw AssertionError("No item of the sheet matches " + matcher.description)
    }

    /**
     * Taps the node through its click action. The item was just brought into composition by [scrollListTo] and may sit
     * at the edge of the viewport (the sheet also clips its content with a rounded shape), so a touch at its center
     * would be a coin toss; the action does not depend on where the list left it.
     */
    private fun tap(text: String) {
        scrollListTo(hasText(text))
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    /**
     * Waits (driving the clock by hand: the progress panel never lets Compose go idle) until the interpretation has
     * produced cards, then gives the exit animation and the auto-scroll time to settle and hands the clock back.
     * Call it with `autoAdvance` already off.
     */
    private fun awaitAnalysisDone() {
        val draft = loggerViewModel().draft
        val deadline = System.nanoTime() + ANALYSIS_TIMEOUT_NS
        try {
            while (draft.isAnalyzing || draft.tags.isEmpty()) {
                check(System.nanoTime() < deadline) { "The interpretation did not finish in time" }
                composeRule.mainClock.advanceTimeByFrame()
                Thread.sleep(20)
            }
            composeRule.mainClock.advanceTimeBy(2_000)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun interpretedDraftSurvivesStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { LoggerUnderTest() }

        composeRule.onNode(hasSetTextAction()).performTextInput("arroz con pollo")
        composeRule.mainClock.autoAdvance = false
        tap("Interpretar comida")
        awaitAnalysisDone()

        scrollListTo(hasText("Resumen de la comida"))
        composeRule.onNodeWithText("Resumen de la comida").assertIsDisplayed()
        tap("Cena")
        assertEquals(MealType.DINNER, loggerViewModel().draft.mealType)

        restorationTester.emulateSavedInstanceStateRestore()

        scrollListTo(descriptionField("arroz con pollo"))
        composeRule.onNode(descriptionField("arroz con pollo")).assertTextEquals("arroz con pollo")
        scrollListTo(hasText("Resumen de la comida"))
        composeRule.onNodeWithText("Resumen de la comida").assertIsDisplayed()
        scrollListTo(hasText("Cena"))
        composeRule.onNodeWithText("Cena").assertIsDisplayed()
        val draft = loggerViewModel().draft
        assertEquals("the chosen meal survives the recreation", MealType.DINNER, draft.mealType)
        assertTrue("the interpreted foods survive the recreation", draft.tags.isNotEmpty())
        assertFalse("the interpretation is not started again", draft.isAnalyzing)
    }

    @Test
    fun sharedTextIsAppliedOnceAndNeverOverwritesLaterEditsAfterRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        composeRule.mainClock.autoAdvance = false
        restorationTester.setContent { LoggerUnderTest(initialDescription = "pan con palta") }
        awaitAnalysisDone()

        scrollListTo(descriptionField("pan con palta"))
        composeRule.onNode(descriptionField("pan con palta")).performTextReplacement("pan con palta y un cafe")
        assertEquals("pan con palta y un cafe", loggerViewModel().draft.description)

        restorationTester.emulateSavedInstanceStateRestore()

        // The host passes the same shared text again to the new composition: it must not replace what was typed.
        scrollListTo(descriptionField("pan con palta y un cafe"))
        composeRule.onNode(descriptionField("pan con palta y un cafe")).assertTextEquals("pan con palta y un cafe")
        assertEquals("pan con palta y un cafe", loggerViewModel().draft.description)
        assertEquals("pan con palta", loggerViewModel().draft.consumedInitialDescription)
    }

    @Test
    fun discardingTheDraftDropsItSoTheNextOpenStartsEmpty() {
        var dismissed = false
        composeRule.setContent { LoggerUnderTest(onDismiss = { dismissed = true }) }
        composeRule.onNode(hasSetTextAction()).performTextInput("pan con palta")
        assertEquals("pan con palta", loggerViewModel().draft.description)

        // Back asks to close the sheet; with text typed it asks for confirmation first.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Descartar")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Descartar").performSemanticsAction(SemanticsActions.OnClick) { it() }

        composeRule.waitForIdle()
        assertTrue("the host is told to close the sheet", dismissed)
        val draft = loggerViewModel().draft
        assertEquals("", draft.description)
        assertTrue(draft.tags.isEmpty())
        assertEquals("", draft.logDate)
    }

    private companion object {
        const val ANALYSIS_TIMEOUT_NS = 120_000_000_000L
    }
}
