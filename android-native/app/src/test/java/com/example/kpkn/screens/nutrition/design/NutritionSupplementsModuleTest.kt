package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.example.kpkn.render.SupplementSamples
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** La cafeína del módulo es una acción real: abre su detalle, también cuando solo viene de comidas y bebidas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionSupplementsModuleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun module(rows: List<SupplementRowState>, totalMg: Double, foodMg: Double, openCaffeine: () -> Unit) {
        rule.setContent {
            KPKNTheme {
                NutritionSupplementsModule(
                    loaded = true,
                    rows = rows,
                    caffeineTotalMg = totalMg,
                    foodCaffeineMg = foodMg,
                    creatineStreakDays = 0,
                    week = SupplementSamples.week,
                    today = LocalDate.of(2026, 10, 29),
                    nowId = null,
                    showHint = false,
                    suggestionIds = emptyList(),
                    onTake = {},
                    onHold = {},
                    onAdd = {},
                    onToggleSuggestion = {},
                    openCaffeine = openCaffeine,
                )
            }
        }
    }

    @Test
    fun laCafeinaDeLaPilaAbreSuDetalle() {
        var opened = 0
        module(SupplementSamples.rows(), totalMg = 95.0, foodMg = 95.0) { opened++ }

        rule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Cafeína")), useUnmergedTree = true).onFirst().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun laCafeinaSoloDeComidasTambienAbreSuDetalle() {
        var opened = 0
        module(emptyList(), totalMg = 85.0, foodMg = 85.0) { opened++ }

        rule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Cafeína")), useUnmergedTree = true).onFirst().performClick()

        assertEquals(1, opened)
    }
}
