package com.example.kpkn.screens.home.design

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.screens.home.HomeSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** «Tu semana»: tocar un día avisa con su fecha, el elegido va seleccionado y hoy tiene su etiqueta. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeWeekStripTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = HomeSamples.today
    private val days = HomeWeekResolver.week(HomeSamples.twelveWeekItems(), startDay = 1, today = today)

    @Test
    fun `tocar un día llama a onSelect con su fecha`() {
        var picked: LocalDate? = null
        rule.setContent {
            HomeWeekStrip(days = days, selected = today, onSelect = { picked = it })
        }

        rule.onNodeWithTag("home-day-2026-10-14").performClick()

        assertEquals(LocalDate.of(2026, 10, 14), picked)
    }

    @Test
    fun `el día elegido va seleccionado y los demás no`() {
        rule.setContent {
            HomeWeekStrip(days = days, selected = today, onSelect = {})
        }

        rule.onNodeWithTag("home-day-2026-10-13").assertIsSelected()
        rule.onNodeWithTag("home-day-2026-10-14").assertIsNotSelected()
    }

    @Test
    fun `hoy existe con su etiqueta y su descripción en español`() {
        rule.setContent {
            HomeWeekStrip(days = days, selected = today, onSelect = {})
        }

        rule.onNodeWithTag("home-week").assertExists()
        rule.onNodeWithTag("home-day-2026-10-13")
            .assertExists()
            .assertContentDescriptionEquals("martes 13, hoy, Pierna A, pendiente")
    }
}
