package com.example.kpkn.screens.nutrition.components

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.MealType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WP-U7 (C5) · The draft holder and its primitive seed. The cards (`ResolvedTag`) never reach the Bundle: what survives
 * process death is the seed (text, last analyzed text, meal, date, tab, search box, draft id) and `clearDraft` drops it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class FoodLoggerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val created = mutableListOf<FoodLoggerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        // clearDraft stops the snapshotFlow collector (it holds an apply observer on the global snapshot).
        created.forEach { it.clearDraft() }
        created.clear()
        Dispatchers.resetMain()
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): FoodLoggerViewModel =
        FoodLoggerViewModel(handle).also { created += it }

    private fun savedSeed() = mapOf<String, Any?>(
        FoodLoggerViewModel.KEY_DRAFT_LOG_ID to "draft-1",
        FoodLoggerViewModel.KEY_DESCRIPTION to "arroz con pollo y ensalada",
        FoodLoggerViewModel.KEY_LAST_ANALYZED to "arroz con pollo",
        FoodLoggerViewModel.KEY_MEAL_TYPE to "DINNER",
        FoodLoggerViewModel.KEY_LOG_DATE to "2026-10-02",
        FoodLoggerViewModel.KEY_ACTIVE_TAB to 1,
        FoodLoggerViewModel.KEY_SEARCH_QUERY to "palta",
    )

    @Test
    fun `seed restores description, meal, date and tab from SavedStateHandle and flags restoredFromProcessDeath`() {
        val vm = newViewModel(SavedStateHandle(savedSeed()))

        assertTrue(vm.restoredFromProcessDeath)
        assertEquals("arroz con pollo y ensalada", vm.draft.description)
        assertEquals("arroz con pollo", vm.draft.lastAnalyzedDescription)
        assertEquals(MealType.DINNER, vm.draft.mealType)
        assertEquals("2026-10-02", vm.draft.logDate)
        assertEquals(1, vm.draft.activeTab)
        assertEquals("palta", vm.draft.searchQuery)
        assertEquals("draft-1", vm.draft.draftLogId)
        // The cards are not in the seed: they are interpreted again from the last analyzed text.
        assertTrue(vm.draft.tags.isEmpty())
    }

    @Test
    fun `a ViewModel built from an empty handle is not a restore`() {
        val vm = newViewModel()

        assertFalse(vm.restoredFromProcessDeath)
        assertFalse(vm.takeRestoreRequest())
        assertEquals("", vm.draft.description)
        assertTrue(vm.draft.draftLogId.isNotBlank())
    }

    @Test
    fun `the restore request is one-shot so a rotation after the restore never repeats the analysis`() {
        val vm = newViewModel(SavedStateHandle(savedSeed()))

        assertTrue(vm.takeRestoreRequest())
        assertFalse(vm.takeRestoreRequest())
        assertTrue("restoredFromProcessDeath stays true: it describes how the draft was born", vm.restoredFromProcessDeath)
    }

    @Test
    fun `a restored draft is never seeded again by the host`() {
        val vm = newViewModel(SavedStateHandle(savedSeed()))

        assertFalse(vm.seedIfEmpty("2026-10-03", MealType.BREAKFAST, "otra cosa", 0))

        assertEquals("arroz con pollo y ensalada", vm.draft.description)
        assertEquals(MealType.DINNER, vm.draft.mealType)
        assertEquals("2026-10-02", vm.draft.logDate)
        assertEquals(1, vm.draft.activeTab)
    }

    @Test
    fun `seedIfEmpty seeds a fresh draft with what the host asks for`() {
        val vm = newViewModel()

        assertTrue(vm.seedIfEmpty("2026-10-03", MealType.SNACK, "un yogur", 1))

        assertEquals("2026-10-03", vm.draft.logDate)
        assertEquals(MealType.SNACK, vm.draft.mealType)
        assertEquals("un yogur", vm.draft.description)
        assertEquals(1, vm.draft.activeTab)
    }

    @Test
    fun `seedIfEmpty does not overwrite a non-empty draft`() {
        val vm = newViewModel()
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "", 0)
        // The user picks another meal and types; the host (rotation) asks for its original values again.
        vm.draft.mealType = MealType.DINNER
        vm.draft.description = "arroz con pollo"

        assertFalse(vm.seedIfEmpty("2026-10-04", MealType.BREAKFAST, "tostadas", 1))

        assertEquals("2026-10-03", vm.draft.logDate)
        assertEquals(MealType.DINNER, vm.draft.mealType)
        assertEquals("arroz con pollo", vm.draft.description)
        assertEquals(0, vm.draft.activeTab)
    }

    @Test
    fun `seedIfEmpty is a one-time seed even while the box is still empty`() {
        val vm = newViewModel()
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "", 0)
        // Nothing typed yet, but the meal chip was changed: a recreated composition must keep it.
        vm.draft.mealType = MealType.DINNER

        assertFalse(vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "", 0))

        assertEquals(MealType.DINNER, vm.draft.mealType)
    }

    @Test
    fun `seedIfEmpty respects text typed before the first seed`() {
        val vm = newViewModel()
        vm.draft.description = "pan con palta"

        assertFalse(vm.seedIfEmpty("2026-10-03", MealType.BREAKFAST, "otra cosa", 1))

        assertEquals("pan con palta", vm.draft.description)
        assertEquals("2026-10-03", vm.draft.logDate)
        assertEquals(0, vm.draft.activeTab)
    }

    @Test
    fun `persistSeed writes the primitive fields`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "arroz con pollo", 0)

        vm.persistSeed()

        assertEquals("arroz con pollo", handle.get<String>(FoodLoggerViewModel.KEY_DESCRIPTION))
        assertEquals("", handle.get<String>(FoodLoggerViewModel.KEY_LAST_ANALYZED))
        assertEquals("LUNCH", handle.get<String>(FoodLoggerViewModel.KEY_MEAL_TYPE))
        assertEquals("2026-10-03", handle.get<String>(FoodLoggerViewModel.KEY_LOG_DATE))
        assertEquals(0, handle.get<Int>(FoodLoggerViewModel.KEY_ACTIVE_TAB))
        assertEquals("", handle.get<String>(FoodLoggerViewModel.KEY_SEARCH_QUERY))
        assertEquals(vm.draft.draftLogId, handle.get<String>(FoodLoggerViewModel.KEY_DRAFT_LOG_ID))

        // Later edits follow (one write per snapshot apply).
        vm.draft.description = "arroz con pollo y ensalada"
        vm.draft.lastAnalyzedDescription = "arroz con pollo"
        vm.draft.mealType = MealType.DINNER
        vm.draft.activeTab = 1
        vm.draft.searchQuery = "palta"
        Snapshot.sendApplyNotifications()

        assertEquals("arroz con pollo y ensalada", handle.get<String>(FoodLoggerViewModel.KEY_DESCRIPTION))
        assertEquals("arroz con pollo", handle.get<String>(FoodLoggerViewModel.KEY_LAST_ANALYZED))
        assertEquals("DINNER", handle.get<String>(FoodLoggerViewModel.KEY_MEAL_TYPE))
        assertEquals(1, handle.get<Int>(FoodLoggerViewModel.KEY_ACTIVE_TAB))
        assertEquals("palta", handle.get<String>(FoodLoggerViewModel.KEY_SEARCH_QUERY))
    }

    @Test
    fun `what the first ViewModel persisted is what a restored one reads`() {
        val first = SavedStateHandle()
        val vmBefore = newViewModel(first)
        vmBefore.seedIfEmpty("2026-10-03", MealType.SNACK, "un yogur", 1)
        vmBefore.draft.lastAnalyzedDescription = "un yogur"
        vmBefore.persistSeed()

        // Process death: only the handle's contents travel.
        val saved = first.keys().associateWith { first.get<Any?>(it) }
        val vmAfter = newViewModel(SavedStateHandle(saved))

        assertTrue(vmAfter.restoredFromProcessDeath)
        assertEquals("un yogur", vmAfter.draft.description)
        assertEquals("un yogur", vmAfter.draft.lastAnalyzedDescription)
        assertEquals(MealType.SNACK, vmAfter.draft.mealType)
        assertEquals("2026-10-03", vmAfter.draft.logDate)
        assertEquals(1, vmAfter.draft.activeTab)
        assertEquals(vmBefore.draft.draftLogId, vmAfter.draft.draftLogId)
    }

    @Test
    fun `clearDraft removes persisted keys`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "arroz con pollo", 0)
        vm.persistSeed()
        assertTrue(FoodLoggerViewModel.SEED_KEYS.all { handle.contains(it) })

        vm.clearDraft()

        assertTrue(FoodLoggerViewModel.SEED_KEYS.none { handle.contains(it) })
        assertEquals("", vm.draft.description)
        assertTrue(vm.draft.tags.isEmpty())
    }

    @Test
    fun `clearDraft stops the persistence so the blank draft is never written back`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "arroz con pollo", 0)
        vm.persistSeed()

        vm.clearDraft()
        // A write after the clear must not resurrect the seed: the next open would restore a ghost of this draft.
        vm.draft.description = "otra cosa"
        Snapshot.sendApplyNotifications()

        assertTrue(FoodLoggerViewModel.SEED_KEYS.none { handle.contains(it) })
    }

    @Test
    fun `after clearDraft the next open seeds again and persists a new draft id`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "arroz con pollo", 0)
        vm.persistSeed()
        val firstId = vm.draft.draftLogId

        vm.clearDraft()
        assertTrue(vm.seedIfEmpty("2026-10-04", MealType.DINNER, "", 0))
        vm.persistSeed()

        assertNotEquals(firstId, vm.draft.draftLogId)
        assertEquals("2026-10-04", handle.get<String>(FoodLoggerViewModel.KEY_LOG_DATE))
        assertEquals("DINNER", handle.get<String>(FoodLoggerViewModel.KEY_MEAL_TYPE))
        assertEquals(vm.draft.draftLogId, handle.get<String>(FoodLoggerViewModel.KEY_DRAFT_LOG_ID))
    }

    @Test
    fun `clearDraft cancels the analysis, search and composition in flight`() {
        val vm = newViewModel()
        val analysis = vm.viewModelScope.launch { awaitCancellation() }
        val search = vm.viewModelScope.launch { awaitCancellation() }
        val composition = vm.viewModelScope.launch { awaitCancellation() }
        vm.analysisJob = analysis
        vm.searchJob = search
        vm.compositionJob = composition
        vm.draft.isAnalyzing = true

        vm.clearDraft()

        assertTrue(analysis.isCancelled)
        assertTrue(search.isCancelled)
        assertTrue(composition.isCancelled)
        assertNull(vm.analysisJob)
        assertNull(vm.searchJob)
        assertNull(vm.compositionJob)
        assertFalse(vm.draft.isAnalyzing)
    }

    @Test
    fun `persisted text is capped so the saved state stays small`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "x".repeat(50_000), 0)

        vm.flushSeed()

        val saved = handle.get<String>(FoodLoggerViewModel.KEY_DESCRIPTION)
        assertNotNull(saved)
        assertEquals(4_000, saved?.length)
    }

    @Test
    fun `MealTypeSaver keeps the meal by name and drops an unknown name`() {
        val scope = SaverScope { true }
        MealType.entries.forEach { meal ->
            val saved = with(MealTypeSaver) { scope.save(meal) }
            assertEquals(meal.name, saved)
            assertEquals(meal, saved?.let { MealTypeSaver.restore(it) })
        }
        assertNull(MealTypeSaver.restore("BRUNCH"))
    }

    @Test
    fun `a corrupt seed falls back to safe values`() {
        val vm = newViewModel(
            SavedStateHandle(
                mapOf(
                    FoodLoggerViewModel.KEY_DRAFT_LOG_ID to "draft-2",
                    FoodLoggerViewModel.KEY_MEAL_TYPE to "BRUNCH",
                    FoodLoggerViewModel.KEY_ACTIVE_TAB to 7,
                    FoodLoggerViewModel.KEY_LOG_DATE to "",
                ),
            ),
        )

        assertTrue(vm.restoredFromProcessDeath)
        assertEquals(MealType.LUNCH, vm.draft.mealType)
        assertEquals(1, vm.draft.activeTab)
        // The date falls back to today instead of a blank that would be saved as "T12:00:00.000Z".
        assertEquals(LocalDate.now(), LocalDate.parse(vm.draft.logDate))
    }
}
