package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.screens.body.BODY_SAMPLE_TODAY
import com.example.kpkn.screens.body.bodySampleFatPoints
import com.example.kpkn.screens.body.bodySampleFatStart
import com.example.kpkn.screens.body.bodySampleSummary30
import com.example.kpkn.screens.body.bodySampleWeightPoints
import com.example.kpkn.screens.body.bodySampleWeightSummary
import com.example.kpkn.screens.body.design.BodyChartPoint
import com.example.kpkn.screens.body.design.BodyFatModule
import com.example.kpkn.screens.body.design.BodyIsland
import com.example.kpkn.screens.body.design.BodyPageHeader
import com.example.kpkn.screens.body.design.BodyWeightModule
import com.example.kpkn.screens.body.design.bodyIslandChangeText
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.design.KpknSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.YearMonth
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO de la página de Cuerpo (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 * 360 dp de ancho a densidad 3, con los datos de ejemplo de `screens/body/BodySamples.kt`. Escenas: con objetivo de peso (100 % y
 * 130 % de letra), sin objetivo, sin pesajes, con grasa y su objetivo, con la isla visible y con la burbuja de la gráfica pulsada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class BodyPageRenderSheetTest {

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

    /** Vista de la prueba: se dibuja a un bitmap, como en las hojas de iconos. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private fun guardar(nombre: String) {
        val bounds = rule.onNodeWithTag("pagina").fetchSemanticsNode().boundsInRoot
        val vista = hostView!!
        rule.runOnUiThread { SymbolRenderSheet.savePng(vista, bounds, nombre) }
    }

    /** La escala de letra del teléfono: misma densidad, con [escala] en el texto. */
    @Composable
    private fun AlTamano(escala: Float, contenido: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) { contenido() }
    }

    /** La página de Cuerpo tal como la monta la pantalla: cabecera, módulos e isla. */
    @Composable
    private fun PaginaCuerpo(
        resumen: BodyWeightSummary,
        meta: Double?,
        puntos: List<BodyChartPoint>,
        grasa: Double?,
        grasaMeta: Double?,
        grasaAvance: Int?,
        grasaFecha: YearMonth?,
        isla: Boolean,
    ) {
        // La isla pinta su vidrio con el desenfoque de la página, como en la app: la página es la fuente y la isla, su hermana.
        val haze = remember { HazeState() }
        CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(KpknSurface.canvas).hazeSource(state = haze)) {
            Column(Modifier.fillMaxWidth().testTag("pagina")) {
                BodyPageHeader(topInset = 0.dp, onAdd = {})
                BodyWeightModule(
                    summary = resumen,
                    goalKg = meta,
                    points = puntos,
                    range = BodyChartRange.THREE_MONTHS,
                    today = BODY_SAMPLE_TODAY,
                    onRangeChange = {},
                    onAdd = {},
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                if (grasa != null) {
                    BodyFatModule(
                        current = grasa,
                        start = bodySampleFatStart(),
                        points = bodySampleFatPoints(),
                        goalPercent = grasaMeta,
                        progressPercent = grasaAvance,
                        eta = grasaFecha,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        BodyIsland(
            visible = isla,
            title = resumen.current?.let { "${formatDecimalEs(it, 1)} kg" } ?: "Cuerpo",
            subtitle = if (resumen.current != null) bodyIslandChangeText(bodySampleSummary30()) else null,
            onAdd = {},
        )
        }
        }
    }

    private fun montar(
        escala: Float = 1f,
        resumen: BodyWeightSummary = bodySampleWeightSummary(),
        meta: Double? = 60.0,
        puntos: List<BodyChartPoint> = bodySampleWeightPoints(),
        grasa: Double? = 17.2,
        grasaMeta: Double? = null,
        grasaAvance: Int? = null,
        grasaFecha: YearMonth? = null,
        isla: Boolean = false,
    ) {
        rule.setContent {
            hostView = LocalView.current
            AlTamano(escala) {
                PaginaCuerpo(
                    resumen = resumen,
                    meta = meta,
                    puntos = puntos,
                    grasa = grasa,
                    grasaMeta = grasaMeta,
                    grasaAvance = grasaAvance,
                    grasaFecha = grasaFecha,
                    isla = isla,
                )
            }
        }
    }

    /** Con el reloj parado, cada cambio de estado se compone en un frame: se deja llegar el toque y pasar frames. */
    private fun asentar() {
        repeat(3) {
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
        }
        rule.waitForIdle()
    }

    @Test
    fun cuerpoConObjetivoDePeso() {
        onlyWhenAsked()
        montar()
        rule.waitForIdle()
        guardar("cuerpo_objetivo_100")
    }

    @Test
    fun cuerpoConObjetivoDePesoA130() {
        onlyWhenAsked()
        montar(escala = 1.3f)
        rule.waitForIdle()
        guardar("cuerpo_objetivo_130")
    }

    @Test
    fun cuerpoSinObjetivo() {
        onlyWhenAsked()
        montar(meta = null, grasa = null)
        rule.waitForIdle()
        guardar("cuerpo_sin_objetivo_100")
    }

    @Test
    fun cuerpoSinPesajes() {
        onlyWhenAsked()
        montar(resumen = BodyWeightSummary(), meta = null, puntos = emptyList(), grasa = null)
        rule.waitForIdle()
        guardar("cuerpo_sin_pesajes_100")
    }

    @Test
    fun cuerpoConGrasaYSuObjetivo() {
        onlyWhenAsked()
        montar(meta = null, grasaMeta = 15.0, grasaAvance = 40, grasaFecha = YearMonth.of(2027, 6))
        rule.waitForIdle()
        guardar("cuerpo_grasa_objetivo_100")
    }

    @Test
    fun cuerpoConIslaVisible() {
        onlyWhenAsked()
        montar(isla = true)
        rule.waitForIdle()
        guardar("cuerpo_isla_100")
    }

    @Test
    fun cuerpoConBurbujaPulsada() {
        onlyWhenAsked()
        montar()
        rule.waitForIdle()
        // Sin avance del reloj, la burbuja no se cierra sola mientras se dibuja.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Tendencia de peso", substring = true)
            .performTouchInput { click() }
        asentar()
        guardar("cuerpo_burbuja_100")
    }
}
