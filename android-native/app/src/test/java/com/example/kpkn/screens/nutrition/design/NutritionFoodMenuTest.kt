package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionFoodMenuTest {

    @get:Rule
    val rule = createComposeRule()

    /** El menú de un registro de Almuerzo: las otras comidas son Desayuno, Merienda y Cena. */
    @Composable
    private fun Menu(
        expanded: Boolean,
        onDuplicate: () -> Unit = {},
        onToggleMove: () -> Unit = {},
        onMove: (MealType) -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        NutritionFoodMenu(
            anchor = Rect(left = 40f, top = 300f, right = 320f, bottom = 360f),
            moveExpanded = expanded,
            otherMeals = MealSlots.mealOrder.filter { it != MealType.LUNCH },
            onDuplicate = onDuplicate,
            onToggleMove = onToggleMove,
            onMove = onMove,
            onDelete = onDelete,
            onDismiss = {},
        )
    }

    @Test
    fun duplicarLlamaADuplicar() {
        var duplicated = 0
        rule.setContent { Menu(expanded = false, onDuplicate = { duplicated++ }) }
        rule.onNodeWithText("Duplicar").performClick()
        assertEquals(1, duplicated)
    }

    @Test
    fun eliminarLlamaABorrar() {
        var deleted = 0
        rule.setContent { Menu(expanded = false, onDelete = { deleted++ }) }
        rule.onNodeWithText("Eliminar").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun moverATanSoloDespliegaLasOtrasComidas() {
        var toggled = 0
        rule.setContent { Menu(expanded = false, onToggleMove = { toggled++ }) }
        rule.onNodeWithText("Mover a…").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun conMoverDesplegadoSeVenLasTresComidasYMoverlaCambiaLaComida() {
        var moved: MealType? = null
        rule.setContent { Menu(expanded = true, onMove = { moved = it }) }
        rule.onNodeWithText("Desayuno").assertExists()
        rule.onNodeWithText("Merienda").assertExists()
        rule.onNodeWithText("Cena").performClick()
        assertEquals(MealType.DINNER, moved)
    }
}
