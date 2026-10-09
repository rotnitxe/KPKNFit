package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.screens.onboarding.design.entreno.programSourceArtOf
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO de los tres símbolos de «¿Cómo quieres tu programa?» (no comprueba nada: escribe PNG). Solo corre con
 * `KPKN_RENDER_DIR`. Escribe `program_source_rest.png` (los tres en reposo) y `program_source_frames.png` (cuatro fotogramas
 * de cada uno, seleccionado). La importación también se dibuja, aunque la opción esté oculta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h900dp-mdpi", application = android.app.Application::class)
class ProgramSourceRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    /** Lo que se dibuja ahora: «rest» (los tres en reposo) o «frames» (cuatro fotogramas de cada uno). */
    private var sheet by mutableStateOf("rest")

    /** Instantes de los fotogramas: la pasada de la torre (1,8 s), la del lápiz (1,7 s) y el vaivén de la hoja (3,2 s). */
    private val frameTimes = mapOf(
        ProgramSource.RECOMMEND to listOf(0f, 0.6f, 1.2f, 1.8f),
        ProgramSource.LATER to listOf(0f, 0.5f, 1.0f, 1.7f),
        ProgramSource.IMPORT to listOf(0f, 0.8f, 1.6f, 2.4f),
    )

    @Composable
    private fun RestRow() {
        Row {
            for (source in ProgramSource.entries) {
                val art = programSourceArtOf(source, reduced = false)
                SymbolRenderSheet.Frame(art, art.restT, selected = false, px = 192f)
            }
        }
    }

    @Composable
    private fun FrameRows() {
        for (source in ProgramSource.entries) {
            val art = programSourceArtOf(source, reduced = false)
            Row {
                for (t in frameTimes.getValue(source)) {
                    SymbolRenderSheet.Frame(art, t, selected = true, px = 128f)
                }
            }
        }
    }

    @Test
    fun renderTheProgramSourceSymbols() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.testTag("sheet").background(Color.Black).padding(16.dp)) {
                    if (sheet == "rest") RestRow() else FrameRows()
                }
            }
        }
        for (name in listOf("rest", "frames")) {
            rule.runOnUiThread { sheet = name }
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
            rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, "program_source_$name") }
        }
    }
}
