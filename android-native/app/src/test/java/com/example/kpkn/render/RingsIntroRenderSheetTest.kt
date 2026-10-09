package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.auge.RingChannelId
import com.example.kpkn.domain.auge.RingsIntroSimulator
import com.example.kpkn.domain.auge.RingsIntroState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.rings.RingsIntroContent
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
 * HOJA DE DIBUJO del paso «Tus RINGS» (`RINGS_INTRO`): al entrar, con la sesión simulada a 0 h y a 36 h, y con Columna
 * destacada. No comprueba nada: escribe PNG. Solo corre con `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h840dp-xxhdpi", application = Application::class)
class RingsIntroRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar los gráficos nativos de Robolectric. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private const val SHEET_TAG = "rings-intro-sheet"
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que las otras hojas). */
    private var hostView: View? = null

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(SHEET_TAG).fetchSemanticsNode().boundsInRoot
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

    private fun render(name: String, demo: RingsIntroState, highlighted: RingChannelId?) {
        rule.setContent {
            hostView = LocalView.current
            Box(
                Modifier
                    .testTag(SHEET_TAG)
                    .size(360.dp, 840.dp)
                    .background(WizardColors.background)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                RingsIntroContent(
                    demo = demo,
                    highlighted = highlighted,
                    onSimulateToggle = {},
                    onHours = {},
                    onAdjust = { _, _ -> },
                    onHighlight = {},
                )
            }
        }
        // Tras el reloj de entrada: la torre ya ha llenado sus anillos.
        rule.waitForIdle()
        save(name)
    }

    private fun sessionAt(hours: Float): RingsIntroState =
        RingsIntroSimulator.withHours(RingsIntroSimulator.toggleSession(RingsIntroState()), hours)

    @Test
    fun alEntrar() = render("rings_intro_entrar", RingsIntroState(), highlighted = null)

    @Test
    fun sesionSimuladaA0Horas() = render("rings_intro_sesion_0h", sessionAt(0f), highlighted = null)

    @Test
    fun sesionSimuladaA36Horas() = render("rings_intro_sesion_36h", sessionAt(36f), highlighted = null)

    @Test
    fun sesionA36HorasConColumnaDestacada() =
        render("rings_intro_sesion_36h_columna", sessionAt(36f), highlighted = RingChannelId.COLUMNA)
}
