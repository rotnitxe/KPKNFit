package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.computeDailyTotals
import com.example.kpkn.screens.nutrition.NutritionSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionMealModuleTest {

    @get:Rule
    val rule = createComposeRule()

    private val lunch: List<NutritionLog> = NutritionSamples.mealLogs.filter { it.mealType == MealType.LUNCH }
    private val breakfast: List<NutritionLog> = NutritionSamples.mealLogs.filter { it.mealType == MealType.BREAKFAST }

    @Composable
    private fun Meal(
        meal: MealType,
        logs: List<NutritionLog>,
        onAdd: () -> Unit = {},
        onOpen: (NutritionLog) -> Unit = {},
        onLongPress: (NutritionLog, Rect) -> Unit = { _, _ -> },
    ) {
        NutritionMealModule(
            mealType = meal,
            logs = logs,
            totals = computeDailyTotals(logs),
            onAdd = onAdd,
            onOpenLog = onOpen,
            onLongPressLog = onLongPress,
        )
    }

    @Test
    fun elMasDeLaCabeceraRegistraEnSuComida() {
        var added = 0
        rule.setContent { Meal(MealType.LUNCH, lunch, onAdd = { added++ }) }
        rule.onNodeWithContentDescription("Añadir a Almuerzo").performClick()
        assertEquals(1, added)
    }

    @Test
    fun tocarUnRegistroLoAbreParaEditar() {
        var opened: String? = null
        rule.setContent { Meal(MealType.LUNCH, lunch, onOpen = { opened = it.id }) }
        rule.onNodeWithText("Pollo con arroz").performClick()
        assertEquals("log-pollo", opened)
    }

    @Test
    fun laPulsacionLargaPideElMenuDeLaFila() {
        var pressed: String? = null
        var anchorWidth = 0f
        rule.setContent {
            Meal(MealType.LUNCH, lunch, onLongPress = { log, anchor ->
                pressed = log.id
                anchorWidth = anchor.width
            })
        }
        rule.onNodeWithText("Pollo con arroz").performTouchInput { longClick() }
        assertEquals("log-pollo", pressed)
        assertTrue(anchorWidth > 0f)
    }

    @Test
    fun unaComidaVaciaMuestraAnadirYNadaDeSinRegistros() {
        var added = 0
        rule.setContent { Meal(MealType.SNACK, emptyList(), onAdd = { added++ }) }
        rule.onNodeWithText("Añadir").assertExists()
        rule.onNodeWithText("Sin registros").assertDoesNotExist()
        rule.onNodeWithText("Añadir").performClick()
        assertEquals(1, added)
    }

    @Test
    fun elRangoDeUnaEstimacionSeVeAlTocarLaCifra() {
        rule.setContent { Meal(MealType.BREAKFAST, breakfast) }
        rule.onNodeWithText("220–270 kcal").assertDoesNotExist()
        rule.onNodeWithTag("nutrition-kcal-log-pan").performClick()
        rule.onNodeWithText("220–270 kcal").assertExists()
    }
}
