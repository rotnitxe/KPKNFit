package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.auge.RingChannelId
import com.example.kpkn.domain.auge.RingsIntroSimulator
import com.example.kpkn.domain.auge.RingsIntroState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.rings.RingsIntroActions
import com.example.kpkn.screens.onboarding.rings.RingsIntroContent
import com.example.kpkn.screens.onboarding.rings.RingsIntroScene
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
 * HOJA DE DIBUJO de la intro de RINGS (overlay): sus tres actos a 360 dp, con la invitación de la línea de tiempo a mitad
 * (36 h), y con la letra al 130 % en los actos principales. No comprueba nada: escribe PNG. Solo corre con `KPKN_RENDER_DIR`
 * apuntando a una carpeta; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h840dp-xxhdpi", application = Application::class)
class RingsIntroRenderSheetTest {

    companion object {
        /**
         * Con el ejecutor de Robolectric esta suposición solo salta la primera prueba que corre y las demás siguen: por eso
         * cada prueba empieza con [onlyWhenAsked] (igual que `WizardRenderSheetTest`).
         */
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

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Ninguna acción: la hoja solo dibuja. */
    private val noActions = RingsIntroActions(
        onSkip = {},
        onNext = {},
        onStart = {},
        onSimulate = {},
        onHours = {},
        onDragChange = {},
        onEnergy = {},
        onHighlight = {},
    )

    private fun scene(
        act: Int,
        invite: Int = 0,
        demo: RingsIntroState = RingsIntroState(),
        highlighted: RingChannelId? = null,
    ): RingsIntroScene = RingsIntroScene(
        act = act,
        invite = invite,
        skipped = false,
        demo = demo,
        highlighted = highlighted,
        nextReady = true,
        reduced = false,
        dragging = false,
    )

    private fun render(name: String, scene: RingsIntroScene, fontScale: Float = 1f) {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(
                    Modifier
                        .testTag(SHEET_TAG)
                        .size(360.dp, 840.dp)
                        .background(WizardColors.background),
                ) {
                    RingsIntroContent(scene = scene, actions = noActions, modifier = Modifier.fillMaxSize())
                }
            }
        }
        // Tras los relojes de entrada: la caída de los anillos y el fundido de los botones ya han terminado.
        rule.waitForIdle()
        save(name)
    }

    private fun sessionAt(hours: Float): RingsIntroState =
        RingsIntroSimulator.withHours(RingsIntroSimulator.toggleSession(RingsIntroState()), hours)

    @Test
    fun acto1Umbral() = render("rings_intro_acto1_umbral", scene(act = 0))

    @Test
    fun acto1UmbralCon130() = render("rings_intro_acto1_umbral_130", scene(act = 0), fontScale = 1.3f)

    @Test
    fun acto2Anillos() = render("rings_intro_acto2_anillos", scene(act = 1))

    @Test
    fun acto2ColumnaDestacadaCon130() =
        render("rings_intro_acto2_columna_130", scene(act = 1, highlighted = RingChannelId.COLUMNA), fontScale = 1.3f)

    @Test
    fun acto3Invitacion1() = render("rings_intro_acto3_invitacion1", scene(act = 2, invite = 0))

    @Test
    fun acto3Invitacion2A36Horas() =
        render("rings_intro_acto3_invitacion2_36h", scene(act = 2, invite = 1, demo = sessionAt(36f)))

    @Test
    fun acto3Invitacion2A36HorasCon130() =
        render("rings_intro_acto3_invitacion2_36h_130", scene(act = 2, invite = 1, demo = sessionAt(36f)), fontScale = 1.3f)

    @Test
    fun acto3Invitacion3EnergiaDestacada() =
        render("rings_intro_acto3_invitacion3", scene(act = 2, invite = 2, demo = sessionAt(36f)))

    @Test
    fun cierreConEmpezar() = render(
        "rings_intro_cierre",
        scene(act = 2, invite = 3, demo = RingsIntroSimulator.withAdjustment(sessionAt(36f), RingChannelId.ENERGIA, 62)),
    )
}
