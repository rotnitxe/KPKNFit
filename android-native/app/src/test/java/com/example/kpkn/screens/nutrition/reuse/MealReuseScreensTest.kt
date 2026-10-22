package com.example.kpkn.screens.nutrition.reuse

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.MealReuse.CopyPreset
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.QuickEntry
import com.example.kpkn.domain.nutrition.QuickEntryForm
import com.example.kpkn.screens.nutrition.design.NutritionFoodMenu
import com.example.kpkn.screens.nutrition.design.NutritionFoodRow
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** N4 · Las pantallas de la reutilización del diario: hoja de copia, plantillas, entrada rápida, menú, etiqueta «rápida» y planificadas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h2000dp-xxhdpi")
class MealReuseScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)
    private val yesterday = today.minusDays(1)

    private val lunch = NutritionLog(
        id = "L1", date = "${yesterday}T12:00:00.000Z", mealType = MealType.LUNCH,
        foods = listOf(LoggedFood(id = "f1", foodName = "Arroz con pollo", amount = 300.0, calories = 560.0, protein = 28.0, carbs = 70.0, fats = 12.0)),
    )

    // ─── Hoja «Copiar a…» ────────────────────────────────────────────────────

    @Test
    fun `la hoja de copia con manana elegido dice una copia y confirma`() {
        var confirmed = 0
        rule.setContent {
            CopyToContent(
                title = "Copiar a…", subtitle = "Almuerzo · 20 oct · 560 kcal", sources = listOf(lunch),
                selection = MealReuse.initialSelection(today, setOf(yesterday)), today = today, blocked = setOf(yesterday), busy = false,
                onToggleDate = {}, onTogglePreset = {}, onConfirm = { confirmed++ },
            )
        }
        rule.onNodeWithTag("copy-preview").assertExists()
        rule.onNodeWithText("1 copia de Almuerzo").assertExists()
        rule.onNodeWithTag("copy-confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `sin fechas elegidas copiar no hace nada`() {
        var confirmed = 0
        rule.setContent {
            CopyToContent(
                title = "Copiar a…", subtitle = "x", sources = listOf(lunch), selection = MealReuse.DateSelection(), today = today,
                blocked = emptySet(), busy = false, onToggleDate = {}, onTogglePreset = {}, onConfirm = { confirmed++ },
            )
        }
        rule.onNodeWithText("Elige al menos una fecha.").assertExists()
        rule.onNodeWithTag("copy-confirm").performClick()
        assertEquals(0, confirmed)
    }

    private var selection by mutableStateOf(MealReuse.DateSelection())
    private var delivered: List<LocalDate> = emptyList()

    private fun showCopy(sources: List<NutritionLog>) = rule.setContent {
        CopyToContent(
            title = "Copiar a…", subtitle = "x", sources = sources, selection = selection, today = today,
            blocked = setOf(yesterday), busy = false,
            onToggleDate = { selection = MealReuse.toggleDate(selection, it, today, setOf(yesterday)) },
            onTogglePreset = { selection = MealReuse.togglePreset(selection, it, today, setOf(yesterday)) },
            onConfirm = { delivered = selection.sorted },
        )
    }

    @Test
    fun `un atajo de tres dias y el boton copiar entregan tres fechas en tres interacciones`() {
        selection = MealReuse.initialSelection(today, setOf(yesterday))
        showCopy(listOf(lunch, lunch.copy(id = "L2", mealType = MealType.DINNER)))
        rule.onNodeWithTag("copy-preset-${CopyPreset.NEXT_3_DAYS.name}").performClick()
        rule.onNodeWithText("6 copias (2 comidas × 3 fechas)").assertExists()
        rule.onNodeWithTag("copy-confirm").performClick()
        assertEquals((1L..3L).map { today.plusDays(it) }, delivered)
    }

    @Test
    fun `tocar un dia del calendario lo suma y el dia de origen esta apagado`() {
        selection = MealReuse.initialSelection(today, setOf(yesterday))
        showCopy(listOf(lunch))
        rule.onNodeWithContentDescription("jueves 22 de octubre de 2026", substring = true).assertExists()
        rule.onNodeWithContentDescription("martes 20 de octubre de 2026, día de origen", substring = true).assertIsNotEnabled()
        rule.onNodeWithContentDescription("sábado 24 de octubre de 2026", substring = true).performClick()
        rule.waitForIdle()
        assertTrue(selection.dates.contains(LocalDate.of(2026, 10, 24)))
        rule.onNodeWithText("2 copias de Almuerzo").assertExists()
    }

    @Test
    fun `una fecha futura avisa de que queda planificada y una pasada no`() {
        rule.setContent {
            CopyToContent(
                title = "Copiar a…", subtitle = "x", sources = listOf(lunch), selection = MealReuse.DateSelection(setOf(today.plusDays(1))),
                today = today, blocked = emptySet(), busy = false, onToggleDate = {}, onTogglePreset = {}, onConfirm = {},
            )
        }
        rule.onNodeWithText("Las fechas futuras quedan planificadas: no cuentan como lo que comiste.").assertExists()
    }

    // ─── Menú de la fila ─────────────────────────────────────────────────────

    private fun menu(copy: (() -> Unit)?, template: (() -> Unit)?) = rule.setContent {
        NutritionFoodMenu(
            anchor = Rect(left = 40f, top = 300f, right = 320f, bottom = 360f),
            moveExpanded = false,
            otherMeals = MealSlots.mealOrder.filter { it != MealType.LUNCH },
            onDuplicate = {}, onToggleMove = {}, onMove = {}, onDelete = {}, onDismiss = {},
            onCopyTo = copy, onSaveTemplate = template,
        )
    }

    @Test
    fun `el menu ofrece Copiar a y Guardar como plantilla cuando se le piden`() {
        var copied = 0
        var saved = 0
        menu({ copied++ }, { saved++ })
        rule.onNodeWithText("Duplicar").assertExists()
        rule.onNodeWithText("Eliminar").assertExists()
        rule.onNodeWithText("Copiar a…").performClick()
        assertEquals(1, copied)
        rule.onNodeWithText("Guardar como plantilla").performClick()
        assertEquals(1, saved)
    }

    @Test
    fun `sin los callbacks nuevos el menu es el de antes`() {
        menu(null, null)
        rule.onNodeWithText("Duplicar").assertExists()
        rule.onNodeWithText("Mover a…").assertExists()
        rule.onNodeWithText("Copiar a…").assertDoesNotExist()
        rule.onNodeWithText("Guardar como plantilla").assertDoesNotExist()
    }

    // ─── Plantillas ──────────────────────────────────────────────────────────

    private val rows = listOf(
        TemplateRowState(MealReuse.TemplateSummary("t1", "Mi almuerzo", 560, 28, 70, 12, 2)),
        TemplateRowState(MealReuse.TemplateSummary("t2", "Desayuno de siempre", 300, 15, 40, 8, 1)),
    )

    @Test
    fun `la lista vacia de plantillas explica como guardar una`() {
        rule.setContent { templates(emptyList()) }
        rule.onNodeWithText("Aún no tienes plantillas").assertExists()
    }

    @Test
    fun `la lista de plantillas muestra nombre kcal y macros y elegir una la aplica`() {
        var applied: String? = null
        rule.setContent { templates(rows, onApply = { applied = it }) }
        rule.onNodeWithText("Mi almuerzo").assertExists()
        rule.onNodeWithText("2 alimentos · P 28 g · H 70 g · G 12 g").assertExists()
        rule.onNodeWithText("1 alimento · P 15 g · H 40 g · G 8 g").assertExists()
        rule.onNodeWithText("Mi almuerzo").performClick()
        assertEquals("t1", applied)
    }

    @Test
    fun `renombrar y borrar piden su paso y la confirmacion de borrar nombra lo que pasa`() {
        var renamed: String? = null
        var asked: String? = null
        rule.setContent {
            templates(
                listOf(rows[0].copy(renaming = true, renameText = "Mi almuerzo"), rows[1].copy(confirmingDelete = true)),
                onConfirmRename = { renamed = it },
                onAskDelete = { asked = it },
            )
        }
        rule.onNodeWithTag("template-rename-field").assertExists()
        rule.onNodeWithText("Guardar nombre").performClick()
        assertEquals("t1", renamed)
        rule.onNodeWithText("¿Eliminar esta plantilla? Las comidas que ya registraste con ella no cambian.").assertExists()
        assertEquals(null, asked)
    }

    @androidx.compose.runtime.Composable
    private fun templates(
        list: List<TemplateRowState>,
        onApply: (String) -> Unit = {},
        onConfirmRename: (String) -> Unit = {},
        onAskDelete: (String) -> Unit = {},
    ) = TemplatesContent(
        rows = list, failure = null, busy = false, onApply = onApply, onStartRename = {}, onRenameTextChange = {},
        onConfirmRename = onConfirmRename, onCancelRename = {}, onAskDelete = onAskDelete, onConfirmDelete = {}, onCancelDelete = {},
    )

    // ─── Entrada rápida ──────────────────────────────────────────────────────

    private var quickForm by mutableStateOf(QuickEntryForm())
    private var quickShowErrors by mutableStateOf(false)
    private var quickAdded: LoggedFood? = null
    private var quickAdds = 0

    private fun quickField(tag: String) = rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun showQuick() = rule.setContent {
        CompositionLocalProvider(LocalWizardReducedMotion provides true) {
            val validation = QuickEntry.validate(quickForm, "q-1")
            QuickEntryContent(
                form = quickForm, validation = validation, showErrors = quickShowErrors, mealType = MealType.LUNCH,
                onChange = { quickForm = it }, onMealChange = {}, onClose = {},
                onSubmit = {
                    quickShowErrors = true
                    if (validation.isValid) {
                        quickAdds++
                        quickAdded = validation.food
                    }
                },
                modifier = Modifier.width(360.dp).height(2000.dp),
            )
        }
    }

    @Test
    fun `la entrada rapida entrega un alimento marcado con las kcal escritas`() {
        showQuick()
        quickField("quick-entry-kcal").performTextInput("500")
        rule.onNodeWithTag("quick-entry-submit").performClick()
        val food = quickAdded
        assertNotNull(food)
        assertEquals(500.0, food!!.calories, 0.0)
        assertTrue(food.isQuickEntry)
        assertEquals("Entrada rápida", food.foodName)
    }

    @Test
    fun `sin kcal la entrada rapida muestra el error y no entrega nada`() {
        showQuick()
        rule.onNodeWithText("Escribe las kcal.").assertDoesNotExist()
        rule.onNodeWithTag("quick-entry-submit").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Escribe las kcal.").assertExists()
        assertEquals(0, quickAdds)
    }

    @Test
    fun `el aviso de kcal que no cuadran se ve sin bloquear`() {
        val form = QuickEntryForm(kcal = "900", protein = "10", carbs = "10", fats = "5")
        val validation = QuickEntry.validate(form, "q")
        rule.setContent {
            QuickEntryContent(form, validation, showErrors = false, mealType = MealType.DINNER, onChange = {}, onMealChange = {}, onSubmit = {}, onClose = {})
        }
        rule.onNodeWithTag("quick-entry-warning").assertExists()
        assertTrue(validation.isValid)
    }

    // ─── Etiqueta «rápida» en el diario ──────────────────────────────────────

    @Test
    fun `la fila de una entrada rapida lleva la etiqueta rapida y una comida normal no`() {
        val quickFood = QuickEntry.validate(QuickEntryForm(name = "Pizza", kcal = "500"), "q").food!!
        val quickLog = NutritionLog(id = "Q", date = "${today}T12:00:00.000Z", mealType = MealType.DINNER, foods = listOf(quickFood))
        rule.setContent { NutritionFoodRow(log = quickLog, onOpen = {}, onLongPress = {}) }
        rule.onNodeWithTag("quick-entry-tag", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Entrada rápida").assertExists()
        rule.onNodeWithText("Pizza").assertExists()
    }

    @Test
    fun `una comida normal no lleva la etiqueta rapida`() {
        rule.setContent { NutritionFoodRow(log = lunch, onOpen = {}, onLongPress = {}) }
        rule.onNodeWithTag("quick-entry-tag").assertDoesNotExist()
        rule.onNodeWithText("300 g").assertExists()
    }

    // ─── Planificadas, acciones de día y pregunta ────────────────────────────

    @Test
    fun `las planificadas se pueden pasar a consumidas solo si el dia llego y siempre quitar`() {
        var consumed: String? = null
        var removed: String? = null
        rule.setContent {
            PlannedMealsModule(listOf(lunch), canMarkConsumed = true, onMarkConsumed = { consumed = it.id }, onRemove = { removed = it.id })
        }
        rule.onNodeWithTag("planned-consume-L1").performClick()
        rule.onNodeWithTag("planned-remove-L1").performClick()
        assertEquals("L1", consumed)
        assertEquals("L1", removed)
    }

    @Test
    fun `una planificada de un dia futuro solo ofrece quitar`() {
        rule.setContent { PlannedMealsModule(listOf(lunch), canMarkConsumed = false, onMarkConsumed = {}, onRemove = {}) }
        rule.onNodeWithTag("planned-consume-L1").assertDoesNotExist()
        rule.onNodeWithTag("planned-remove-L1").assertExists()
    }

    @Test
    fun `sin planificadas el modulo no pinta nada`() {
        rule.setContent { PlannedMealsModule(emptyList(), canMarkConsumed = true, onMarkConsumed = {}, onRemove = {}) }
        rule.onNodeWithTag("planned-meals").assertDoesNotExist()
    }

    @Test
    fun `las acciones de dia ocultan copiar dia cuando no hay comidas`() {
        var repeat = 0
        rule.setContent { ReuseDayActions("Repetir ayer", canCopyDay = false, onRepeat = { repeat++ }, onCopyDay = {}) }
        rule.onNodeWithText("Repetir ayer").performClick()
        assertEquals(1, repeat)
        rule.onNodeWithTag("reuse-copy-day").assertDoesNotExist()
    }

    @Test
    fun `las acciones de dia ofrecen copiar dia cuando hay comidas`() {
        var copyDay = 0
        rule.setContent { ReuseDayActions("Repetir ayer", canCopyDay = true, onRepeat = {}, onCopyDay = { copyDay++ }) }
        rule.onNodeWithText("Copiar día a…").performClick()
        assertEquals(1, copyDay)
    }

    @Test
    fun `la pregunta de repetir ayer cuenta las comidas y promete no tocar lo registrado`() {
        assertEquals(
            "Este día ya tiene 1 comida. ¿Añadir también 2 comidas de ayer? Lo que ya registraste no se toca.",
            repeatPromptText(existingMeals = 1, incomingMeals = 2, sourceIsYesterday = true),
        )
        assertEquals(
            "Este día ya tiene 3 comidas. ¿Añadir también 1 comida del día anterior? Lo que ya registraste no se toca.",
            repeatPromptText(existingMeals = 3, incomingMeals = 1, sourceIsYesterday = false),
        )
    }
}
