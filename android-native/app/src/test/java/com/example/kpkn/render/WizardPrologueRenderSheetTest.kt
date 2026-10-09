package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.GENDER_WHY_TAG
import com.example.kpkn.screens.onboarding.GenderWhyButton
import com.example.kpkn.screens.onboarding.design.LocalWizardGlassNote
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardGlassNoteController
import com.example.kpkn.screens.onboarding.design.WizardGlassNoteLayer
import com.example.kpkn.screens.onboarding.design.WizardPrologueContent
import com.example.kpkn.screens.onboarding.design.WizardTypography
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * HOJA DE DIBUJO del prólogo del alta («ANTES DE EMPEZAR»), en su cuadro final: 360 dp de página, letra al 100 % y al 130 %.
 * No comprueba nada: escribe PNG. Solo corre con `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class WizardPrologueRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar los gráficos nativos de Robolectric. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que la hoja del wizard). */
    private var hostView: View? = null

    private var fontScale by mutableStateOf(1f)

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderThePrologueSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(Modifier.testTag("prologue").size(360.dp, 780.dp).background(Color.Black)) {
                    // El cuadro final: el reloj ya ha pasado de todas las etapas y el botón está listo.
                    WizardPrologueContent(time = { 2.2f }, onStart = {})
                }
            }
        }
        for ((name, scale) in listOf("prologue_s100" to 1f, "prologue_s130" to 1.3f)) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            save("prologue", name)
        }
    }

    // ── La nota de vidrio «¿Por qué lo preguntamos?» abierta sobre la página (el bloque tiene que leerse) ──

    private val glassController = WizardGlassNoteController()
    private val glassHaze = HazeState()

    @Test
    fun renderTheGlassNoteSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardGlassNote provides glassController,
            ) {
                Box(Modifier.testTag("glass").size(360.dp, 780.dp)) {
                    // La página de atrás: el paso que la nota explica, con su enlace y sus opciones.
                    Box(Modifier.fillMaxSize().hazeSource(state = glassHaze).background(WizardColors.background)) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Text("Sexo de cálculo", style = WizardTypography.stepTitle, color = WizardColors.text)
                            Spacer(Modifier.height(12.dp))
                            GenderWhyButton()
                            Spacer(Modifier.height(16.dp))
                            repeat(6) { i ->
                                Text("Opción ${i + 1} de la página de atrás", style = WizardTypography.stepSubtitle, color = WizardColors.textMuted)
                                Spacer(Modifier.height(18.dp))
                            }
                        }
                    }
                    WizardGlassNoteLayer(controller = glassController, hazeState = glassHaze)
                }
            }
        }
        // El enlace se toca en cada escala para que la nota se ancle a su posición real (la letra mueve el enlace).
        for ((name, scale) in listOf("glass_s100" to 1f, "glass_s130" to 1.3f)) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            rule.onNodeWithTag(GENDER_WHY_TAG).performClick()
            rule.waitForIdle()
            save("glass", name)
            rule.runOnIdle { glassController.dismiss() }
            rule.waitForIdle()
        }
    }
}
