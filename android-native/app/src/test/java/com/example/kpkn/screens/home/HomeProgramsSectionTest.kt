package com.example.kpkn.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.kpkn.domain.training.HomeWeekResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Tus programas»: el activo primero con «ACTIVO» y su semana, «Nuevo» crea, sin programas sale el texto, y se quedan las etiquetas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeProgramsSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val active = HomeSamples.twelveWeekProgram().copy(weekDays = 4, structureTemplateId = "generated:powerlifting:periodized")
    private val shelf = HomeSamples.shelfPrograms(active)
    private val line = HomeWeekResolver.programLine(active, HomeSamples.twelveWeekItems(active), HomeSamples.today)

    @Test
    fun `el activo va primero, con ACTIVO y su semana`() {
        rule.setContent {
            HomeProgramsSection(
                programs = shelf,
                activeProgramId = active.id,
                onProgramClick = {},
                onCreateProgram = {},
                activeProgramLine = line,
            )
        }

        rule.onNodeWithText("ACTIVO", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Semana 3 de 12", useUnmergedTree = true).assertIsDisplayed()
        val activeLeft = rule.onNodeWithTag(homeProgramCardTag(active.id)).fetchSemanticsNode().boundsInRoot.left
        val fuerzaLeft = rule.onNodeWithTag(homeProgramCardTag("p-fuerza")).fetchSemanticsNode().boundsInRoot.left
        assertTrue("el activo debe ir antes que el resto", activeLeft < fuerzaLeft)
    }

    @Test
    fun `el resto muestra semanas y dias solo si el programa los dice`() {
        rule.setContent {
            HomeProgramsSection(
                programs = shelf,
                activeProgramId = active.id,
                onProgramClick = {},
                onCreateProgram = {},
                activeProgramLine = line,
            )
        }

        rule.onNodeWithText("8 semanas · 3 días", useUnmergedTree = true).assertIsDisplayed()
        // «Hipertrofia de verano» no tiene días por semana en el programa: solo sus semanas. Va al final de la fila, fuera de la
        // pantalla a 360 dp: primero se desplaza la fila hasta su póster.
        rule.onNodeWithTag(HOME_PROGRAMS_ROW_TAG).performScrollToNode(hasTestTag(homeProgramCardTag("p-hiper")))
        rule.onNodeWithText("6 semanas", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Nuevo llama a onCreateProgram`() {
        var created = 0
        rule.setContent {
            HomeProgramsSection(
                programs = listOf(active),
                activeProgramId = active.id,
                onProgramClick = {},
                onCreateProgram = { created++ },
            )
        }

        rule.onNodeWithTag("home-programs-new").performClick()

        assertEquals(1, created)
    }

    @Test
    fun `sin programas sale el texto para empezar y solo Nuevo`() {
        rule.setContent {
            HomeProgramsSection(
                programs = emptyList(),
                activeProgramId = null,
                onProgramClick = {},
                onCreateProgram = {},
            )
        }

        rule.onNodeWithText("Crea tu primer programa o importa el tuyo.").assertIsDisplayed()
        rule.onNodeWithTag("home-programs-new").assertExists()
        rule.onNodeWithTag(HOME_PROGRAMS_ROW_TAG).assertDoesNotExist()
    }

    @Test
    fun `se quedan las etiquetas y Ver todos abre la lista`() {
        var seeAll = 0
        rule.setContent {
            HomeProgramsSection(
                programs = shelf,
                activeProgramId = active.id,
                onProgramClick = {},
                onCreateProgram = {},
                activeProgramLine = line,
                onSeeAll = { seeAll++ },
            )
        }

        rule.onNodeWithText("TUS PROGRAMAS").assertIsDisplayed()
        rule.onNodeWithTag(HOME_PROGRAMS_ROW_TAG).assertExists()
        rule.onNodeWithTag(homeProgramCardTag(active.id)).assertExists()
        rule.onNodeWithText("Ver todos").performClick()

        assertEquals(1, seeAll)
    }
}
