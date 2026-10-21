package com.example.kpkn.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.ui.components.icons.KpknLineIcons
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Barra inferior (I11b): cada pestaña es un `Role.Tab` con su etiqueta, la elegida expone `selected`, tocar una llama a
 * `onSelect` con su ruta y sin Nutrición quedan tres.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = android.app.Application::class)
class KpknBottomBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val todas = listOf(
        KpknBottomBarItem(route = "home", label = "Inicio", icon = KpknLineIcons.Home),
        KpknBottomBarItem(route = "training", label = "Entreno", icon = KpknLineIcons.Training),
        KpknBottomBarItem(route = "nutrition", label = "Nutrición", icon = KpknLineIcons.Nutrition),
        KpknBottomBarItem(route = "body", label = "Cuerpo", icon = KpknLineIcons.Body),
    )

    private fun ponBarra(items: List<KpknBottomBarItem>, seleccionada: String?, alPulsar: (String) -> Unit = {}) {
        rule.setContent {
            KpknBottomBar(items = items, selectedRoute = seleccionada, onSelect = alPulsar)
        }
    }

    @Test
    fun cadaPestanaEsTabConSuEtiqueta() {
        ponBarra(todas, seleccionada = "home")
        for (item in todas) {
            val rol = rule.onNodeWithText(item.label).fetchSemanticsNode().config[SemanticsProperties.Role]
            assertEquals(item.label, Role.Tab, rol)
        }
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
    }

    @Test
    fun laPestanaElegidaExponeSelectedYLasDemasNo() {
        ponBarra(todas, seleccionada = "training")
        rule.onNodeWithText("Entreno").assertIsSelected()
        rule.onNodeWithText("Inicio").assertIsNotSelected()
        rule.onNodeWithText("Nutrición").assertIsNotSelected()
        rule.onNodeWithText("Cuerpo").assertIsNotSelected()
    }

    @Test
    fun tocarUnaPestanaLlamaAOnSelectConSuRuta() {
        val pulsadas = mutableListOf<String>()
        ponBarra(todas, seleccionada = "home", alPulsar = { pulsadas.add(it) })
        rule.onNodeWithText("Cuerpo").performClick()
        rule.onNodeWithText("Nutrición").performClick()
        assertEquals(listOf("body", "nutrition"), pulsadas)
    }

    @Test
    fun sinNutricionQuedanTresPestanas() {
        ponBarra(todas.filter { it.route != "nutrition" }, seleccionada = "home")
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(3)
        rule.onNodeWithText("Nutrición").assertDoesNotExist()
    }
}
