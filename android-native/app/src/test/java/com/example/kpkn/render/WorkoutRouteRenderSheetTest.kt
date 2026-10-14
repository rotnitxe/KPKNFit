package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.design.WorkoutRouteBody
import com.example.kpkn.screens.workout.design.sampleRouteGroups
import com.example.kpkn.screens.workout.design.workoutRouteSummary
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.design.KpknSurface
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
 * HOJA DE DIBUJO de la ruta de la sesión (KPKN LS4): no comprueba nada, escribe PNG a 360 dp, al 100 % y al 130 % de letra. Solo corre
 * con `KPKN_RENDER_DIR`; sin ella se salta. Escenas: la lista (6 ejercicios, 2 hechos, el actual y una superserie de 2), el modo dios con
 * dos elegidos y «Deshacer», un bloque levantado mientras se arrastra, y una sesión con cardio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutRouteRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Escena que se dibuja ahora, su escala de letra y una revisión que fuerza una composición nueva en cada captura. */
    private var scene by mutableStateOf("lista")
    private var fontScale by mutableStateOf(1f)
    private var revision by mutableStateOf(0)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val noop: () -> Unit = {}

    /** La hoja con datos de ejemplo. Lleva el asa arriba, como la hoja real, para que la cabecera no quede pegada al borde. */
    @Composable
    private fun Scene() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).height(700.dp).background(KpknSurface.canvas),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(KpknSheetTokens.Handle),
                )
            }
            WorkoutRouteBody(
                summary = workoutRouteSummary(currentIdx = 2, exerciseCount = 6, setsDone = 14, setsTotal = 22),
                groups = sampleRouteGroups(withCardio = scene == "cardio"),
                accent = SymbolPalette.musculo,
                godModeUndoCount = 2,
                onClose = noop,
                onSelectExercise = {},
                onSelectStep = {},
                onOpenContext = {},
                onOpenMemberContext = {},
                onReorder = {},
                onAdd = noop,
                onCreateSuperset = noop,
                onSkip = noop,
                onBatchCreateSuperset = {},
                onBatchSkip = {},
                onBatchDelete = {},
                onBatchUltraFast = { _, _ -> },
                onDissolveSuperset = {},
                onRevertGodModeAction = {},
                liftedGroupKey = if (scene == "arrastre") "exercise:e6" else null,
                modifier = Modifier.height(652.dp),
            )
        }
    }

    /** Dibuja la vista entera sobre negro, tal como está ahora. */
    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun crop(full: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Guarda el recuadro «sheet» tal como se ve ahora. */
    private fun save(name: String) {
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /** Pinta una escena a 100 % y 130 % de letra; [drive] hace los toques previos (modo dios) sobre la escena recién compuesta. */
    private fun renderScene(sceneName: String, file: String, drive: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
                revision++
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            drive()
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            save("$file$suffix")
        }
    }

    @Test
    fun renderWorkoutRouteSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                // Movimiento reducido: las capturas son estados finales, sin desplazamientos a mitad de camino.
                LocalWizardReducedMotion provides true,
            ) {
                key(revision) { Scene() }
            }
        }
        renderScene(sceneName = "lista", file = "ls4_ruta")
        renderScene(
            sceneName = "seleccion",
            file = "ls4_modo_dios",
            drive = {
                rule.onNodeWithText("Seleccionar").performClick()
                rule.onNodeWithText("Press inclinado con mancuernas").performClick()
                rule.onNodeWithText("Sentadilla frontal").performClick()
            },
        )
        renderScene(sceneName = "arrastre", file = "ls4_arrastre")
        renderScene(sceneName = "cardio", file = "ls4_cardio")
    }
}
