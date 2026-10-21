package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.screens.workout.EffortScale
import com.example.kpkn.screens.workout.buildEffortScale
import com.example.kpkn.screens.workout.effortCenterPx
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El slider de esfuerzo (LV8) tal como lo ve y lo toca la persona: empieza en el mínimo y sin marcar, el plan solo se marca, un toque
 * elige la marca de ese punto, arrastrar recorre las marcas en orden y la accesibilidad tiene su acción de progreso.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class EffortFireSliderTest {

    @get:Rule
    val rule = createComposeRule()

    private val elegida = mutableStateOf<Int?>(null)
    private val pulsaciones = mutableListOf<Int>()

    private fun rirConPlanDos() = buildEffortScale(IntensityMode.RIR, IntensityMode.RIR, targetRir = 2, targetRpe = null)

    private fun mostrar(escala: EffortScale, inicial: Int? = null, ancho: Int = 252, fontScale: Float = 1f) {
        elegida.value = inicial
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(ancho.dp).background(Color(0xFF111111))) {
                            EffortFireSlider(
                                scale = escala,
                                selectedIndex = elegida.value,
                                onSelect = {
                                    pulsaciones += it
                                    elegida.value = it
                                },
                                forcedFireSeconds = 0f,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun nodo() = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode()

    private fun descripcion(): String = nodo().config[SemanticsProperties.ContentDescription].first()

    @Test
    fun empiezaEnElMinimoSinMarcarYElPlanSoloSeMarca() {
        mostrar(rirConPlanDos())

        assertEquals(0f, nodo().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        assertEquals("Esfuerzo sin marcar", descripcion())
        assertTrue("mostrar la escala no elige nada", pulsaciones.isEmpty())
        assertNull("sin marcar no hay ninguna marca elegida", elegida.value)
    }

    @Test
    fun laEscalaTieneUnaMarcaPorCadaValorDeLaApp() {
        val escala = rirConPlanDos()
        mostrar(escala)

        val info = nodo().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f, info.range.start, 0f)
        assertEquals((escala.items.size - 1).toFloat(), info.range.endInclusive, 0f)
        assertEquals(escala.items.size - 2, info.steps)
    }

    @Test
    fun tocarElBordeIzquierdoEligeElMinimo() {
        mostrar(rirConPlanDos())

        rule.onNodeWithTag(EffortSliderTag).performTouchInput { click(Offset(1f, centerY)) }
        rule.waitForIdle()

        assertEquals(listOf(0), pulsaciones)
        assertEquals("Esfuerzo RIR 10, cómodo", descripcion())
    }

    @Test
    fun tocarUnPuntoEligeLaMarcaDeEsePunto() {
        val escala = rirConPlanDos()
        mostrar(escala)
        val x = effortCenterPx(escala.planIndex!!, escala.items.size, nodo().size.width.toFloat(), 23f * 3f)

        rule.onNodeWithTag(EffortSliderTag).performTouchInput { click(Offset(x, centerY)) }
        rule.waitForIdle()

        assertEquals(listOf(escala.planIndex), pulsaciones)
        assertEquals("Esfuerzo RIR 2, exigente", descripcion())
    }

    @Test
    fun tocarElBordeDerechoEligeElFallo() {
        val escala = rirConPlanDos()
        mostrar(escala)
        val ancho = nodo().size.width.toFloat()

        rule.onNodeWithTag(EffortSliderTag).performTouchInput { click(Offset(ancho - 1f, centerY)) }
        rule.waitForIdle()

        assertEquals(listOf(escala.items.lastIndex), pulsaciones)
        assertEquals("Esfuerzo Fallo, todo", descripcion())
    }

    @Test
    fun arrastrarRecorreLasMarcasEnOrdenHastaLaUltima() {
        val escala = rirConPlanDos()
        mostrar(escala)

        rule.onNodeWithTag(EffortSliderTag).performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertTrue("el arrastre eligió marcas", pulsaciones.isNotEmpty())
        assertEquals("arranca en el mínimo", 0, pulsaciones.first())
        assertEquals("termina en la última", escala.items.lastIndex, pulsaciones.last())
        assertTrue("el arrastre nunca retrocede: $pulsaciones", pulsaciones.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun laAccionDeProgresoDeLaAccesibilidadEligeLaMarca() {
        mostrar(rirConPlanDos())

        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        rule.waitForIdle()

        assertEquals(listOf(9), pulsaciones)
        assertEquals("Esfuerzo RIR 1, muy duro", descripcion())
    }

    @Test
    fun alReabrirUnaSerieMarcadaElPulgarEstaEnSuMarca() {
        val escala = rirConPlanDos()
        mostrar(escala, inicial = escala.planIndex)

        assertEquals(escala.planIndex!!.toFloat(), nodo().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        assertEquals("Esfuerzo RIR 2, exigente", descripcion())
    }

    @Test
    fun laPistaMide18DpDentroDeLaZonaTactilAprobadaDe44Dp() {
        mostrar(rirConPlanDos(), ancho = 252)

        val alto = nodo().size.height / 3f
        assertTrue("la zona táctil mide $alto dp y debe ser de 44 dp", alto >= 43.5f && alto <= 44.5f)
        assertEquals(18f, rule.onNodeWithTag("effort-fire-track", useUnmergedTree = true).fetchSemanticsNode().size.height / 3f, .5f)
        assertTrue("el slider cabe en 252 dp", nodo().size.width / 3f <= 252.5f)
    }
}
