package com.example.kpkn.screens.nutrition.components

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.TagOrigin
import com.example.kpkn.domain.nutrition.hasMaterialQuestion
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WP-U11 (C9) · Editing a registered meal through the draft holder. `seedFromLog` loads the meal once and with the same
 * flag as `seedIfEmpty`; an edit is never persisted for process death (its draft id is the meal's own id: a restored
 * draft would save a new meal over the original); `clearDraft` ends the edit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class FoodLoggerViewModelEditTest {

    private val created = mutableListOf<FoodLoggerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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

    private val rice = LoggedFood(
        id = "food-rice", foodName = "Arroz blanco cocido", amount = 150.0,
        calories = 195.0, protein = 4.1, carbs = 42.0, fats = 0.4,
        analysisSource = AnalysisSource.DATABASE,
    )

    private val stew = LoggedFood(
        id = "food-stew", foodName = "Cazuela casera (estimado)", amount = 300.0,
        calories = 420.0, protein = 22.0, carbs = 38.0, fats = 18.0,
        caloriesMin = 300.0, caloriesMax = 560.0,
        analysisSource = AnalysisSource.LOCAL_HEURISTIC, isUncertain = true,
        nutritionReferenceNote = "Asumí una cazuela casera.",
    )

    private val meal = NutritionLog(
        id = "log-1", date = "2026-10-02T12:00:00.000Z", mealType = MealType.DINNER,
        foods = listOf(rice, stew), notes = "En casa",
    )

    private fun savedSeed() = mapOf<String, Any?>(
        FoodLoggerViewModel.KEY_DRAFT_LOG_ID to "draft-1",
        FoodLoggerViewModel.KEY_DESCRIPTION to "arroz con pollo",
        FoodLoggerViewModel.KEY_LAST_ANALYZED to "arroz con pollo",
        FoodLoggerViewModel.KEY_MEAL_TYPE to "LUNCH",
        FoodLoggerViewModel.KEY_LOG_DATE to "2026-10-02",
        FoodLoggerViewModel.KEY_ACTIVE_TAB to 0,
        FoodLoggerViewModel.KEY_SEARCH_QUERY to "",
    )

    // ─── seedFromLog ────────────────────────────────────────────────────────

    @Test
    fun `seedFromLog sets the editing id, the draft id, the day, the meal and one decided card per food`() {
        val vm = newViewModel()

        assertTrue(vm.seedFromLog(meal))

        assertEquals("log-1", vm.draft.editingLogId)
        assertEquals("saving writes over the same row", "log-1", vm.draft.draftLogId)
        assertEquals("2026-10-02", vm.draft.logDate)
        assertEquals(MealType.DINNER, vm.draft.mealType)
        assertEquals(0, vm.draft.activeTab)
        assertEquals("", vm.draft.description)
        assertEquals(listOf("Arroz blanco cocido", "Cazuela casera (estimado)"), vm.draft.tags.map { it.tag })
        assertTrue(vm.draft.tags.all { it.origin == TagOrigin.EDIT })
        assertTrue("a reopened meal asks nothing", vm.draft.tags.none { it.hasMaterialQuestion() })
        assertEquals(meal, vm.draft.editBaseline?.original)
    }

    @Test
    fun `seedFromLog keeps the seeded flag so a rotation or the host cannot overwrite the edit`() {
        val vm = newViewModel()
        vm.seedFromLog(meal)
        // The user changes the meal and removes every card (nothing left that counts as content); the recreated
        // composition asks for the same seeds again and must not bring the removed foods back.
        vm.draft.mealType = MealType.SNACK
        vm.draft.tags = emptyList()
        assertFalse(vm.draft.hasContent)

        assertFalse(vm.seedFromLog(meal))
        assertFalse(vm.seedFromLog(meal.copy(id = "log-2", mealType = MealType.BREAKFAST)))
        assertFalse(vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "otra cosa", 1))

        assertEquals("log-1", vm.draft.editingLogId)
        assertEquals("log-1", vm.draft.draftLogId)
        assertEquals(MealType.SNACK, vm.draft.mealType)
        assertTrue(vm.draft.tags.isEmpty())
        assertEquals("", vm.draft.description)
    }

    @Test
    fun `a draft that was already seeded for a new meal is not turned into an edit`() {
        val vm = newViewModel()
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "", 0)

        assertFalse(vm.seedFromLog(meal))

        assertNull(vm.draft.editingLogId)
        assertNotEquals("log-1", vm.draft.draftLogId)
        assertTrue(vm.draft.tags.isEmpty())
    }

    @Test
    fun `a restored draft is never seeded from a log`() {
        val vm = newViewModel(SavedStateHandle(savedSeed()))

        assertFalse(vm.seedFromLog(meal))

        assertNull(vm.draft.editingLogId)
        assertEquals("draft-1", vm.draft.draftLogId)
        assertEquals("arroz con pollo", vm.draft.description)
        assertTrue(vm.draft.tags.isEmpty())
    }

    @Test
    fun `text typed before the first seed is not replaced by the log`() {
        val vm = newViewModel()
        vm.draft.description = "pan con palta"

        assertFalse(vm.seedFromLog(meal))

        assertNull(vm.draft.editingLogId)
        assertEquals("pan con palta", vm.draft.description)
        assertTrue(vm.draft.tags.isEmpty())
    }

    @Test
    fun `a log without a usable id is left to the host seed`() {
        val vm = newViewModel()

        assertFalse(vm.seedFromLog(meal.copy(id = "")))
        assertTrue("the host still seeds a new meal", vm.seedIfEmpty("2026-10-03", MealType.SNACK, "un yogur", 0))

        assertNull(vm.draft.editingLogId)
        assertEquals("un yogur", vm.draft.description)
    }

    @Test
    fun `a log without a date edits on today`() {
        val vm = newViewModel()

        assertTrue(vm.seedFromLog(meal.copy(date = "")))

        assertEquals(LocalDate.now().toString(), vm.draft.logDate)
    }

    // ─── clearDraft ─────────────────────────────────────────────────────────

    @Test
    fun `clearDraft ends the edit and gives the next draft a new id`() {
        val vm = newViewModel()
        vm.seedFromLog(meal)

        vm.clearDraft()

        assertNull(vm.draft.editingLogId)
        assertNull(vm.draft.editBaseline)
        assertNotEquals("log-1", vm.draft.draftLogId)
        assertTrue(vm.draft.tags.isEmpty())
        assertEquals("", vm.draft.logDate)
        assertFalse(vm.draft.isUnchangedEdit)
    }

    @Test
    fun `after clearDraft the next open seeds again, as a new meal or as another edit`() {
        val vm = newViewModel()
        vm.seedFromLog(meal)
        vm.clearDraft()

        assertTrue(vm.seedIfEmpty("2026-10-04", MealType.SNACK, "un yogur", 0))
        assertNull(vm.draft.editingLogId)
        vm.clearDraft()

        assertTrue(vm.seedFromLog(meal.copy(id = "log-2", mealType = MealType.BREAKFAST)))
        assertEquals("log-2", vm.draft.editingLogId)
        assertEquals(MealType.BREAKFAST, vm.draft.mealType)
    }

    // ─── Persistencia: una edición no sobrevive a la muerte del proceso ──────

    @Test
    fun `an edit is never persisted so a restored holder cannot save over the original meal`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.seedFromLog(meal)

        vm.persistSeed()
        vm.draft.description = "y una manzana"
        vm.draft.mealType = MealType.SNACK
        Snapshot.sendApplyNotifications()
        vm.flushSeed()

        assertTrue(FoodLoggerViewModel.SEED_KEYS.none { handle.contains(it) })

        // Process death: only the handle's contents travel.
        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = newViewModel(SavedStateHandle(saved))
        assertFalse(restored.restoredFromProcessDeath)
        assertNotEquals("log-1", restored.draft.draftLogId)
        assertNull(restored.draft.editingLogId)
    }

    @Test
    fun `a new meal after an edit is persisted again with its own draft id`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.seedFromLog(meal)
        vm.persistSeed()
        vm.clearDraft()

        assertTrue(vm.seedIfEmpty("2026-10-04", MealType.DINNER, "arroz con pollo", 0))
        vm.persistSeed()

        assertTrue(FoodLoggerViewModel.SEED_KEYS.all { handle.contains(it) })
        assertEquals(vm.draft.draftLogId, handle.get<String>(FoodLoggerViewModel.KEY_DRAFT_LOG_ID))
        assertNotEquals("log-1", handle.get<String>(FoodLoggerViewModel.KEY_DRAFT_LOG_ID))
    }

    // ─── Cerrar sin cambios ─────────────────────────────────────────────────

    @Test
    fun `an edit that changed nothing is unchanged and expanding a card does not change it`() {
        val vm = newViewModel()
        vm.seedFromLog(meal)
        assertTrue(vm.draft.isUnchangedEdit)

        vm.draft.tags = vm.draft.tags.map { it.copy(isExpanded = true) }

        assertTrue(vm.draft.isUnchangedEdit)
    }

    @Test
    fun `every real change makes the edit dirty and undoing it makes it unchanged again`() {
        val vm = newViewModel()
        vm.seedFromLog(meal)
        val seededTags = vm.draft.tags

        vm.draft.mealType = MealType.SNACK
        assertFalse("another meal", vm.draft.isUnchangedEdit)
        vm.draft.mealType = MealType.DINNER
        assertTrue(vm.draft.isUnchangedEdit)

        vm.draft.logDate = "2026-10-01"
        assertFalse("another day", vm.draft.isUnchangedEdit)
        vm.draft.logDate = "2026-10-02"
        assertTrue(vm.draft.isUnchangedEdit)

        vm.draft.tags = seededTags.take(1)
        assertFalse("a removed food", vm.draft.isUnchangedEdit)
        vm.draft.tags = seededTags.map { it.copy(amountGrams = 99.0) }
        assertFalse("another amount", vm.draft.isUnchangedEdit)
        vm.draft.tags = seededTags
        assertTrue(vm.draft.isUnchangedEdit)

        vm.draft.description = "y una manzana"
        assertFalse("text to interpret", vm.draft.isUnchangedEdit)
        vm.draft.description = ""
        vm.draft.searchQuery = "palta"
        assertFalse("a search in progress", vm.draft.isUnchangedEdit)
        vm.draft.searchQuery = ""
        assertTrue(vm.draft.isUnchangedEdit)
    }

    @Test
    fun `a draft for a new meal is never an unchanged edit`() {
        val vm = newViewModel()
        vm.seedIfEmpty("2026-10-03", MealType.LUNCH, "", 0)

        assertFalse(vm.draft.isUnchangedEdit)
        assertNull(vm.draft.editBaseline)
    }

    @Test
    fun `the saved meal travels with the draft so the save keeps its notes and untouched foods`() {
        val vm = newViewModel()
        vm.seedFromLog(meal)

        val original = vm.draft.editBaseline?.original

        assertSame(meal, original)
        assertEquals("En casa", original?.notes)
        assertEquals(listOf(rice, stew), original?.foods)
    }
}
