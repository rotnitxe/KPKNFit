package com.example.kpkn.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.KpknSheet
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Isla superior de Home (KPKN_UI_V2 §3): histéresis de aparición, toques y entrada. La entrada se mide en píxeles: a 0 ms el avatar
 * no se ve (todo oscuro) y a 400 ms está entero (la inicial, en tinta, se ve).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi", application = android.app.Application::class)
class HomeTopBarIslandTest {

    @get:Rule
    val rule = createComposeRule()

    /** Opacidad de la cabecera respecto a la isla: la cambia la prueba fuera de la composición. */
    private var barAlpha by mutableStateOf(0f)

    /** Progreso del relevo de la sesión: la prueba del contexto discreto lo cambia fuera de la composición. */
    private var sessionProgress by mutableStateOf(0f)

    /** La vista de Compose, para dibujarla a un bitmap y medir el avatar. */
    private var hostView: View? = null

    @Composable
    private fun Island(onProfile: () -> Unit) {
        hostView = LocalView.current
        CompositionLocalProvider(LocalWizardReducedMotion provides false) {
            Box(Modifier.size(360.dp, 120.dp).background(Color.Black)) {
                HomeTopBar(
                    modifier = Modifier.testTag("isla"),
                    greeting = "Buenos días",
                    userName = "Alex",
                    greetingProgress = 1f,
                    
                    sessionProgress = sessionProgress,
                    nutritionProgress = 0f,
                    hasPrograms = true,
                    
                    
                    
                    primarySession = null,
                    isRestDay = true,
                    dailyCalorieGoal = 2300,
                    consumedCalories = 1240,
                    onStartWorkout = { _, _ -> },
                    onCreateProgram = {},
                    onAddMeal = {},
                    onNavigateToProfile = onProfile,
                    hazeState = remember { HazeState() },
                    onBottomPositionChanged = {},
                    barAlpha = barAlpha,
                )
            }
        }
    }

    /** Máximo de brillo (0 a 255) dentro del avatar, tal como se dibuja ahora. */
    private fun brightestInAvatar(): Int {
        val bounds = rule.onNodeWithContentDescription("Perfil").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val right = bounds.right.toInt().coerceIn(left + 1, full.width)
        val bottom = bounds.bottom.toInt().coerceIn(top + 1, full.height)
        var brightest = 0
        for (x in left until right) {
            for (y in top until bottom) {
                val c = full.getPixel(x, y)
                val lum = (android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)) / 3
                if (lum > brightest) brightest = lum
            }
        }
        return brightest
    }

    @Test
    fun histeresisAparecerEn055YSalirEn045() {
        assertFalse(homeBarShownAfter(previous = false, barAlpha = 0.5f))
        assertTrue(homeBarShownAfter(previous = false, barAlpha = 0.55f))
        assertTrue(homeBarShownAfter(previous = true, barAlpha = 0.5f))
        assertFalse(homeBarShownAfter(previous = true, barAlpha = 0.45f))
    }

    @Test
    fun islaOcultaNoTieneNodosTocables() {
        barAlpha = 0f
        rule.setContent { Island(onProfile = {}) }
        rule.onNodeWithContentDescription("Perfil").assertDoesNotExist()
    }

    @Test
    fun islaVisibleDejaPulsarPerfil() {
        barAlpha = 1f
        var profile = 0
        rule.setContent { Island(onProfile = { profile++ }) }
        rule.onNodeWithContentDescription("Perfil").performClick()
        assertEquals(1, profile)
    }

    @Test
    fun entradaVaDeOcultaAEntera() {
        rule.mainClock.autoAdvance = false
        barAlpha = 0f
        rule.setContent { Island(onProfile = {}) }
        rule.waitForIdle()
        rule.runOnIdle { barAlpha = 1f }
        rule.waitForIdle()
        // Con el reloj parado no hay cuadros: el primer cuadro coloca la isla y arranca la entrada (t = 0).
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val at0 = brightestInAvatar()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        val at400 = brightestInAvatar()
        assertTrue("a 0 ms la isla no se ve (brillo máximo $at0)", at0 < 40)
        assertTrue("a 400 ms la isla está entera (brillo máximo $at400)", at400 > 120)
    }

    /** Los textos con rol de título de la semántica (sin fusionar): el contexto de la isla tiene que dar uno solo. */
    private fun headings() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)

    @Test
    fun contextoDeLaIslaEsDiscretoConUnSoloTituloEnElScrollIntermedio() {
        barAlpha = 1f
        sessionProgress = 0.5f
        rule.setContent { Island(onProfile = {}) }
        rule.waitForIdle()
        // A medio relevo la isla sigue con el saludo: un solo título, sin mezclar con la sesión.
        headings().assertCountEquals(1)
        // El saludo entero o su versión corta («Hola, Alex») según el ancho: se comprueba por el nombre.
        rule.onNodeWithText("Alex", substring = true).assertExists()
        rule.onNodeWithText("Día de descanso").assertDoesNotExist()
        // Al pasar la sesión cambia de una vez, sin dos títulos a la vez.
        rule.runOnIdle { sessionProgress = 0.8f }
        rule.waitForIdle()
        headings().assertCountEquals(1)
        rule.onNodeWithText("Día de descanso").assertExists()
        rule.onNodeWithText("Alex", substring = true).assertDoesNotExist()
        // Un poco de vuelta (0,5) conserva la sesión: la histéresis.
        rule.runOnIdle { sessionProgress = 0.5f }
        rule.waitForIdle()
        headings().assertCountEquals(1)
        rule.onNodeWithText("Día de descanso").assertExists()
    }

    @Test
    fun laIslaDeHomeNoSeVeConUnaHojaAbierta() {
        barAlpha = 1f
        rule.setContent {
            Island(onProfile = {})
            KpknSheet(onDismissRequest = {}) { Text("Hoja de prueba") }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Perfil").assertDoesNotExist()
    }

    @Test
    fun contextoDeLaIslaConHistéresisDeCadaSeccion() {
        assertEquals(HomeBarContext.GREETING, homeBarContextAfter(HomeBarContext.GREETING, 0.5f, 0f))
        assertEquals(HomeBarContext.SESSION, homeBarContextAfter(HomeBarContext.GREETING, 0.55f, 0f))
        assertEquals(HomeBarContext.SESSION, homeBarContextAfter(HomeBarContext.SESSION, 0.5f, 0f))
        assertEquals(HomeBarContext.GREETING, homeBarContextAfter(HomeBarContext.SESSION, 0.45f, 0f))
        assertEquals(HomeBarContext.NUTRITION, homeBarContextAfter(HomeBarContext.SESSION, 1f, 0.6f))
        assertEquals(HomeBarContext.NUTRITION, homeBarContextAfter(HomeBarContext.NUTRITION, 1f, 0.5f))
        assertEquals(HomeBarContext.SESSION, homeBarContextAfter(HomeBarContext.NUTRITION, 1f, 0.4f))
    }
}
