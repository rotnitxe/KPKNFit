package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO de las portadas de líneas (`LineCoverArt`): no comprueba nada, escribe PNG. Solo corre con `KPKN_RENDER_DIR`; sin
 * ella se salta. `lineas_contacto.png`: los ocho estilos en CARBON (fila 1), los mismos en NOCHE (fila 2), RELIEVE en los cinco fondos
 * (fila 3) y SESION en CARBON (fila 4), a 150 × 188 dp con 12 dp de separación y su nombre debajo. `lineas_banda.png`: PERFIL en CARBON
 * a 360 × 150 dp, como la tarjeta del programa en curso. Todas usan la semilla 5, las cargas semanales de PERFIL y la semana en curso
 * con índice 3. La ventana de la prueba (1400 × 1200 dp) tiene que caber la hoja entera, o el recorte sale mal.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1400dp-h1200dp-mdpi", application = android.app.Application::class)
class LineCoverRenderSheetTest {

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

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private var hostView: View? = null

    /** Los datos de ejemplo de todas las portadas de la hoja: semilla 5, cargas de PERFIL y semana en curso (índice 3). */
    private fun spec(style: LineCoverStyle, background: LineCoverBackground): LineCoverSpec = LineCoverSpec(
        style = style,
        background = background,
        seed = 5,
        weekLoads = listOf(0.62f, 0.7f, 0.8f, 0.66f, 0.74f, 0.38f),
        currentWeek = 3,
        weeks = 6,
    )

    /** Los ocho estilos de la fila de referencia (SESION va aparte: solo se usa en la historia de un entreno). */
    private val styles = listOf(
        LineCoverStyle.RELIEVE,
        LineCoverStyle.PERFIL,
        LineCoverStyle.ONDAS,
        LineCoverStyle.ORBITAS,
        LineCoverStyle.CAMPO,
        LineCoverStyle.ESPIRAL,
        LineCoverStyle.ECO,
        LineCoverStyle.BRUMA,
    )

    /** Una portada de 150 × 188 dp con su nombre (estilo · fondo) debajo, en blanco a 11 sp. */
    @Composable
    private fun Cell(style: LineCoverStyle, background: LineCoverBackground) {
        Column(Modifier.width(150.dp)) {
            LineCoverArt(spec(style, background), Modifier.width(150.dp).height(188.dp), reduced = true)
            BasicText(
                text = "${style.label} · ${background.label}",
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = WizardFonts.body),
            )
        }
    }

    /** La hoja de contacto: cuatro filas con 12 dp de separación entre portadas y entre filas. */
    @Composable
    private fun Sheet() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (style in styles) Cell(style, LineCoverBackground.CARBON)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (style in styles) Cell(style, LineCoverBackground.NOCHE)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (background in LineCoverBackground.entries) Cell(LineCoverStyle.RELIEVE, background)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cell(LineCoverStyle.SESION, LineCoverBackground.CARBON)
            }
        }
    }

    /** La banda ancha: PERFIL en CARBON a 360 × 150 dp, como la tarjeta del programa en curso. */
    @Composable
    private fun Banda() {
        LineCoverArt(spec(LineCoverStyle.PERFIL, LineCoverBackground.CARBON), Modifier.width(360.dp).height(150.dp), reduced = true)
    }

    /**
     * Pinta [content] dentro de la caja etiquetada [tag], a densidad 1 y con la escala de letra [fontScale], y la guarda como
     * [name].png en `KPKN_RENDER_DIR`.
     */
    private fun shoot(name: String, fontScale: Float, tag: String, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                Column(Modifier.background(Color.Black)) {
                    Box(Modifier.testTag(tag)) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    @Test
    fun contactSheet() {
        onlyWhenAsked()
        shoot("lineas_contacto", 1f, "contacto") { Sheet() }
    }

    @Test
    fun bandaAncha() {
        onlyWhenAsked()
        shoot("lineas_banda", 1f, "banda") { Banda() }
    }
}
