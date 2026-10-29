package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealSlots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La hoja ⋯ de una comida registrada (lienzo s-rowmenu): título, línea de estado, seis filas con subtítulo y «Mover a…» desplegable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionFoodMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private val breakfast = NutritionLog(
        id = "L1",
        date = "2026-10-28T08:40:00.000Z",
        mealType = MealType.BREAKFAST,
        foods = (1..4).map { LoggedFood(id = "f$it", foodName = "Alimento $it", amount = 100.0, calories = 137.0) },
    )

    /** La hoja de un registro de Almuerzo: las otras comidas son Desayuno, Merienda y Cena. */
    @Composable
    private fun Menu(
        expanded: Boolean,
        onDuplicate: () -> Unit = {},
        onToggleMove: () -> Unit = {},
        onMove: (MealType) -> Unit = {},
        onDelete: () -> Unit = {},
        onDismiss: () -> Unit = {},
        log: NutritionLog? = null,
        onEdit: (() -> Unit)? = null,
        onCopyTo: (() -> Unit)? = null,
        onSaveTemplate: (() -> Unit)? = null,
    ) {
        NutritionFoodMenu(
            anchor = Rect(left = 40f, top = 300f, right = 320f, bottom = 360f),
            moveExpanded = expanded,
            otherMeals = MealSlots.mealOrder.filter { it != MealType.LUNCH },
            onDuplicate = onDuplicate,
            onToggleMove = onToggleMove,
            onMove = onMove,
            onDelete = onDelete,
            onDismiss = onDismiss,
            onCopyTo = onCopyTo,
            onSaveTemplate = onSaveTemplate,
            log = log,
            onEdit = onEdit,
        )
    }

    @Test
    fun duplicarLlamaADuplicar() {
        var duplicated = 0
        rule.setContent { Menu(expanded = false, onDuplicate = { duplicated++ }) }
        rule.onNodeWithText("Duplicar").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, duplicated)
    }

    @Test
    fun eliminarLlamaABorrar() {
        var deleted = 0
        rule.setContent { Menu(expanded = false, onDelete = { deleted++ }) }
        rule.onNodeWithText("Eliminar").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, deleted)
    }

    @Test
    fun duplicarYEliminarCierranLaHojaAntesDeActuar() {
        val order = mutableListOf<String>()
        rule.setContent {
            Menu(expanded = false, onDuplicate = { order += "duplicar" }, onDelete = { order += "eliminar" }, onDismiss = { order += "cerrar" })
        }
        rule.onNodeWithText("Duplicar").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Eliminar").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("cerrar", "duplicar", "cerrar", "eliminar"), order)
    }

    @Test
    fun moverATanSoloDespliegaLasOtrasComidas() {
        var toggled = 0
        var dismissed = 0
        rule.setContent { Menu(expanded = false, onToggleMove = { toggled++ }, onDismiss = { dismissed++ }) }
        rule.onNodeWithText("Mover a…").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, toggled)
        assertEquals(0, dismissed)
    }

    @Test
    fun conMoverDesplegadoSeVenLasTresComidasYMoverlaCambiaLaComida() {
        var moved: MealType? = null
        rule.setContent { Menu(expanded = true, onMove = { moved = it }) }
        rule.onNodeWithText("Desayuno").assertExists()
        rule.onNodeWithText("Merienda").assertExists()
        rule.onNodeWithText("Cena").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(MealType.DINNER, moved)
    }

    @Test
    fun conLaComidaLaHojaLlevaSuNombreYLaLineaDeEstado() {
        rule.setContent { Menu(expanded = false, log = breakfast) }
        rule.onNodeWithText("Desayuno").assertExists()
        rule.onNodeWithText("08:40 · 548 kcal · 4 alimentos").assertExists()
    }

    @Test
    fun sinLaComidaLaHojaSeLlamaComidaYNoLlevaLinea() {
        rule.setContent { Menu(expanded = false) }
        rule.onNodeWithText("Comida").assertExists()
    }

    @Test
    fun cadaAccionLlevaSuSubtitulo() {
        rule.setContent { Menu(expanded = false, onCopyTo = {}, onSaveTemplate = {}, onEdit = {}) }
        rule.onNodeWithText("En la misma comida").assertExists()
        rule.onNodeWithText("Hoy, mañana o los próximos días").assertExists()
        rule.onNodeWithText("Otra comida u otro día").assertExists()
        rule.onNodeWithText("Para repetirla con un toque").assertExists()
        rule.onNodeWithText("Se puede deshacer").assertExists()
    }

    @Test
    fun editarSoloSaleSiHayCallback() {
        rule.setContent { Menu(expanded = false) }
        rule.onNodeWithText("Editar").assertDoesNotExist()
    }

    @Test
    fun editarLlamaAEditarTrasCerrar() {
        val order = mutableListOf<String>()
        rule.setContent { Menu(expanded = false, onEdit = { order += "editar" }, onDismiss = { order += "cerrar" }) }
        rule.onNodeWithText("Editar").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("cerrar", "editar"), order)
    }
}
