package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.domain.habits.HabitsSnapshot
import com.example.kpkn.screens.habits.HabitsSamples
import com.example.kpkn.screens.habits.HabitsScreen
import com.example.kpkn.screens.habits.HabitsUiState
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO de la pantalla de Hábitos (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR` apuntando a una
 * carpeta; sin ella se salta. A 360 dp, con la letra al 100 % y al 130 %, con los datos fijos de [HabitsSamples].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rES-w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class HabitsRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno de gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Una hoja: estado de datos (empty, streaks o long_gap), escala de letra y hábito elegido. */
    private data class Case(
        val name: String,
        val state: String,
        val scale: Float = 1f,
        val selected: HabitKind = HabitKind.MEALS,
    )

    private var current by mutableStateOf(Case("none", "streaks"))

    /** La vista de la actividad, para dibujarla a un bitmap (`captureToImage` no funciona en Robolectric). */
    private var hostView: View? = null

    private fun snapshotFor(state: String): HabitsSnapshot = when (state) {
        "empty" -> HabitsSamples.empty()
        "long_gap" -> HabitsSamples.longGap()
        else -> HabitsSamples.streaks()
    }

    private fun setSheet() {
        rule.setContent {
            val case = current
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = case.scale),
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    Box(Modifier.testTag("sheet").width(360.dp).height(1000.dp).background(KpknSurface.canvas)) {
                        HabitsScreen(
                            state = HabitsUiState(selected = case.selected, snapshot = snapshotFor(case.state)),
                            onSelectHabit = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
    }

    private fun render(case: Case) {
        current = case
        rule.waitForIdle()
        save(case.name)
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
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
    fun renderHabitos() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        setSheet()
        listOf(
            Case("habitos_vacio_s100", "empty"),
            Case("habitos_rachas_comidas_s100", "streaks"),
            Case("habitos_rachas_comidas_s130", "streaks", scale = 1.3f),
            Case("habitos_rachas_entreno_s100", "streaks", selected = HabitKind.TRAINING),
            Case("habitos_hueco_pesaje_s100", "long_gap", selected = HabitKind.WEIGHING),
        ).forEach { render(it) }
    }
}
