package com.example.kpkn.screens.sessioneditor.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.screens.sessioneditor.design.CatalogReplaceButtonTag
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xxhdpi", application = Application::class)
class CatalogAuditStateTest {
    @get:Rule val room = RoomTestRule()
    @get:Rule val rule = createComposeRule()
    private val custom = ExerciseMuscleInfo(id = "custom:aud2", name = "Mi ejercicio AUD2", isCustom = true)
    private val confirmed = mutableListOf<ExerciseMuscleInfo>()
    private var lastSelection = emptyList<ExerciseMuscleInfo>()

    @Before fun seedCustom() = runBlocking {
        KpknDatabase.closeInstance()
        CustomExerciseRepository.initialize(ApplicationProvider.getApplicationContext())
        CustomExerciseRepository.refreshFromStorage()
        CustomExerciseRepository.upsert(custom)
        val db = KpknDatabase.getInstance(ApplicationProvider.getApplicationContext())
        withTimeout(10_000) { while (db.customExerciseDao().getById(custom.id) == null) delay(25) }
        CustomExerciseRepository.refreshFromStorage()
    }

    @After fun clearCustom() = runBlocking {
        KpknDatabase.getInstance(ApplicationProvider.getApplicationContext()).customExerciseDao().delete(custom.id)
        CustomExerciseRepository.refreshFromStorage()
        KpknDatabase.closeInstance()
    }

    private fun mount(replacing: Boolean, fillerCount: Int = 0): StateRestorationTester {
        val bench = CatalogTestBuilders.definition("bench_press", "Press", "push", listOf("pectoralis"), listOf("barbell", "dumbbells"))
        val fillers = List(fillerCount) { index -> CatalogTestBuilders.definition(
            "filler-$index", "Auxiliar ${index.toString().padStart(2, '0')}", "push", listOf("pectoralis")) }
        val repo = InMemoryExerciseCatalogRepositoryV2(CatalogTestBuilders.catalog(*(fillers + bench).toTypedArray()))
            .also { runBlocking { it.load() } }
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
            ExercisePickerV2Catalog(
                repository = repo, query = "", onSearch = {}, editingExisting = replacing,
                selectedExercisesIds = selected.toSet(), onSelect = { confirmed += it },
                onMultiSelect = { confirmed += it; it.map { info -> info.id } },
                onSelectionChange = { selected = it.map { info -> info.id }; lastSelection = it },
                onOpenExerciseDetail = {}, onDismiss = {}, opaqueSurface = true,
                replacement = if (replacing) CatalogReplacementContext(exerciseName = "Original") else null,
            )
        }
        val firstName = if (fillerCount == 0) "Press" else "Auxiliar 00"
        rule.waitUntil(15_000) { rule.onAllNodesWithText(firstName).fetchSemanticsNodes().isNotEmpty() }
        return restoration
    }

    @Test fun selectedCustomExerciseSurvivesRecreation() {
        val restoration = mount(false)
        rule.onNodeWithContentDescription("Añadir ${custom.name} a la selección").performClick()
        rule.onNodeWithText("Añadir 1").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Press").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Añadir 1").assertExists()
        rule.onNodeWithText("Añadir 1").performClick()
        assertEquals(listOf(custom.id), confirmed.map { it.id })
    }

    @Test fun customReplacementWaitsForTheConfirmButton() {
        mount(true)
        rule.onNodeWithContentDescription("Añadir ${custom.name} a la selección").performClick()
        rule.runOnIdle { assertTrue("Selecting a replacement must not commit it", confirmed.isEmpty()) }
        rule.onNodeWithTag(CatalogReplaceButtonTag).assertIsEnabled().performClick()
        assertEquals(listOf(custom.id), confirmed.map { it.id })
    }

    @Test fun proposedReplacementSurvivesRecreation() {
        val restoration = mount(true)
        val variant = exerciseCatalogVariantTagLabel("dumbbells", "bench_press")
        rule.onNodeWithText("Press").performClick()
        rule.onNodeWithText(variant).performClick()
        rule.onNodeWithContentDescription("Añadir Press a la selección").performClick()
        rule.onNodeWithTag(CatalogReplaceButtonTag).assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Press").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(variant).assertIsOn()
        rule.onNodeWithTag(CatalogReplaceButtonTag).assertIsEnabled().performClick()
        assertEquals(listOf("bench_press__dumbbells"), confirmed.map { it.catalogConfigurationId })
    }

    private fun tapTheChosenName() {
        rule.onNodeWithContentDescription("Ver los ejercicios elegidos").performClick()
        rule.onAllNodes(hasText(lastSelection.single().name) and hasClickAction(), useUnmergedTree = true).onLast()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    @Test fun tappingAChosenConfigurationScrollsToItsDefinition() {
        mount(false, fillerCount = 25)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Press"))
        rule.onNodeWithContentDescription("Añadir Press a la selección").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        tapTheChosenName()
        rule.onNodeWithContentDescription("Quitar Press de la selección").assertIsDisplayed()
    }

    @Test fun tappingAChosenCustomExerciseScrollsToItsRow() {
        mount(false, fillerCount = 25)
        rule.onNodeWithContentDescription("Añadir ${custom.name} a la selección").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(25)
        tapTheChosenName()
        rule.onNodeWithContentDescription("Quitar ${custom.name} de la selección").assertIsDisplayed()
    }
}
