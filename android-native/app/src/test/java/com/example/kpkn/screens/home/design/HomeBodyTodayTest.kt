package com.example.kpkn.screens.home.design

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.home.HomeSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Cuerpo» de Home: «Ver progreso» abre la pantalla de progreso y, sin pesajes, «Añadir pesaje» abre el mismo destino. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeBodyTodayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `Ver progreso llama a onOpen con el destino de progreso`() {
        val opened = mutableListOf<String>()
        rule.setContent {
            HomeBodyToday(state = HomeSamples.bodyWithTrendAndGoal(), onOpen = { opened += it })
        }

        rule.onNodeWithText("Ver progreso").performClick()

        assertEquals(listOf("body-progress"), opened)
    }

    @Test
    fun `sin pesajes Añadir pesaje abre el progreso`() {
        val opened = mutableListOf<String>()
        rule.setContent {
            HomeBodyToday(state = HomeSamples.bodyNoWeights(), onOpen = { opened += it })
        }

        rule.onNodeWithTag("home-body-add").performClick()

        assertEquals(listOf("body-progress"), opened)
    }
}
