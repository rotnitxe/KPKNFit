package com.example.kpkn.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Controles compartidos KPKN v2 (KPKN_UI_V2): cada uno expone su rol y su estado, y escribe el dato que le piden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KpknControlsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun segmentadoExponeTabYSeleccionYLlamaConElIndice() {
        val elegidos = mutableListOf<Int>()
        rule.setContent {
            KpknSegmentedControl(
                options = listOf("Semana", "Mes", "Año"),
                selectedIndex = 0,
                onSelect = { elegidos += it },
            )
        }
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(3)
        rule.onNodeWithText("Semana").assertIsSelected()
        rule.onNodeWithText("Mes").assertIsNotSelected()
        rule.onNodeWithText("Mes").performClick()
        assertEquals(listOf(1), elegidos)
    }

    @Test
    fun interruptorCambiaYExponeSuEstado() {
        rule.setContent {
            var on by remember { mutableStateOf(false) }
            KpknSwitch(checked = on, onCheckedChange = { on = it }, modifier = Modifier.testTag("interruptor"))
        }
        rule.onNodeWithTag("interruptor").assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        rule.onNodeWithTag("interruptor").performClick()
        rule.onNodeWithTag("interruptor").assertIsOn()
    }

    @Test
    fun radioYCheckExponenRolYEstado() {
        var taps = 0
        rule.setContent {
            Column {
                KpknRadioRow(selected = true, title = "Mañana", onClick = { taps++ }, modifier = Modifier.testTag("radio"))
                KpknCheckRow(checked = false, title = "Recordatorio", onClick = { taps++ }, modifier = Modifier.testTag("check"))
            }
        }
        rule.onNodeWithTag("radio").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        rule.onNodeWithTag("check").assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithTag("radio").performClick()
        rule.onNodeWithTag("check").performClick()
        assertEquals(2, taps)
    }

    @Test
    fun campoDeTextoEscribeYMuestraElError() {
        rule.setContent {
            var texto by remember { mutableStateOf("") }
            KpknTextField(
                value = texto,
                onValueChange = { texto = it },
                placeholder = "Nombre",
                error = "Mínimo 3 letras",
            )
        }
        rule.onNode(hasSetTextAction()).performTextReplacement("Ana")
        rule.onNode(hasSetTextAction()).assertTextEquals("Ana")
        rule.onNodeWithText("Mínimo 3 letras").assertIsDisplayed()
    }

    @Test
    fun chipEsCasillaYCambiaAlPulsar() {
        rule.setContent {
            var on by remember { mutableStateOf(false) }
            KpknChip(
                text = "Fuerza",
                selected = on,
                onClick = { on = !on },
                modifier = Modifier.testTag("chip"),
                leadingIcon = KpknLineIcons.Check,
            )
        }
        rule.onNodeWithTag("chip").assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithTag("chip").performClick()
        rule.onNodeWithTag("chip").assertIsOn()
    }

    @Test
    fun islaOcultaNoTieneNodosTocables() {
        rule.setContent {
            KpknTopIsland(
                visible = false,
                title = "Hoy",
                leading = { KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = {}) },
                actions = { KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Añadir", onClick = {}) },
            )
        }
        rule.onNodeWithText("Hoy").assertDoesNotExist()
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun islaVisibleMuestraTituloYSusDosAcciones() {
        rule.setContent {
            KpknTopIsland(
                visible = true,
                title = "Hoy",
                subtitle = "Martes, 13 de octubre",
                leading = { KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = {}) },
                actions = { KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Añadir", onClick = {}) },
            )
        }
        rule.onNodeWithText("Hoy").assertIsDisplayed()
        rule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun islaVisibleDejaPasarLosToquesALaPagina() {
        var taps = 0
        rule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .testTag("pagina")
                        .fillMaxSize()
                        .clickable { taps++ },
                )
                KpknTopIsland(visible = true, title = "Hoy")
            }
        }
        // Un toque en mitad de la página, lejos de la isla de arriba: tiene que llegar a la página.
        rule.onNodeWithTag("pagina").performTouchInput { click() }
        assertEquals(1, taps)
    }

    @Test
    fun histeresisMantieneElEstadoDentroDeLaBanda() {
        val banda = 24f
        assertTrue(islandShownAfter(previous = false, past = 0f, bandPx = banda))
        assertFalse(islandShownAfter(previous = false, past = -10f, bandPx = banda))
        assertTrue(islandShownAfter(previous = true, past = -10f, bandPx = banda))
        assertFalse(islandShownAfter(previous = true, past = -30f, bandPx = banda))
    }

    @Test
    fun visibilidadDeLaListaSeActivaAlSalirLaCabecera() {
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        rule.setContent {
            list = rememberLazyListState()
            scope = rememberCoroutineScope()
            val visible = rememberIslandVisible(list, afterItemIndex = 0)
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    item(key = "cabecera") { Box(modifier = Modifier.fillMaxWidth().height(300.dp)) }
                    items(40) { index -> Text(text = "Fila $index", modifier = Modifier.height(60.dp)) }
                }
                Text(text = if (visible) "visible" else "oculta", modifier = Modifier.testTag("bandera"))
            }
        }
        rule.onNodeWithTag("bandera").assertTextEquals("oculta")
        rule.runOnIdle { scope.launch { list.scrollToItem(1) } }
        rule.waitForIdle()
        rule.onNodeWithTag("bandera").assertTextEquals("visible")
    }

    @Test
    fun campoMultilineaNoCreceMasDeSuLimite() {
        val seis = "1\n2\n3\n4\n5\n6"
        rule.setContent {
            Column {
                Box(modifier = Modifier.width(300.dp)) {
                    KpknTextField(value = "1\n2", onValueChange = {}, singleLine = false)
                }
                Box(modifier = Modifier.width(300.dp)) {
                    KpknTextField(value = seis, onValueChange = {}, singleLine = false, maxLines = 2)
                }
                Box(modifier = Modifier.width(300.dp)) {
                    KpknTextField(value = seis, onValueChange = {}, singleLine = false)
                }
            }
        }
        // Mismo estilo en los tres: con el límite de dos, el de seis líneas mide como el de dos; sin límite, mide más.
        val (dos, conLimite, sinLimite) = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.size.height }
        assertTrue(
            "dos $dos, con límite $conLimite, sin límite $sinLimite",
            kotlin.math.abs(conLimite - dos) <= 2 && sinLimite > conLimite + 20,
        )
    }

    @Test
    fun marcadorDeUnCampoMultilineaOcupaHastaDosLineas() {
        val marcador = "Describe la técnica del ejercicio\nSus variantes y el ritmo\nLas pausas que sueles usar"
        lateinit var density: Density
        rule.setContent {
            density = LocalDensity.current
            Box(modifier = Modifier.width(300.dp)) {
                KpknTextField(value = "", onValueChange = {}, placeholder = marcador, singleLine = false)
            }
        }
        // Tres líneas de marcador: se ven dos, no una.
        val linea = with(density) { 21.sp.toPx() }
        val alto = rule.onNodeWithText(marcador).fetchSemanticsNode().size.height
        assertTrue("alto $alto, línea $linea", alto > linea * 1.5f && alto <= linea * 2.5f)
    }
}
