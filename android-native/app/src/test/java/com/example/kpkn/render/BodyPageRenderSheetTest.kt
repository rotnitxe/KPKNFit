package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.screens.body.BODY_SAMPLE_BODY_FAT
import com.example.kpkn.screens.body.BODY_SAMPLE_MUSCLE
import com.example.kpkn.screens.body.BODY_SAMPLE_TODAY
import com.example.kpkn.screens.body.bodySampleBmi
import com.example.kpkn.screens.body.bodySampleFatKg
import com.example.kpkn.screens.body.bodySampleFatPoints
import com.example.kpkn.screens.body.bodySampleFatStart
import com.example.kpkn.screens.body.bodySampleFfmi
import com.example.kpkn.screens.body.bodySampleHistoryRows
import com.example.kpkn.screens.body.bodySampleLeanKg
import com.example.kpkn.screens.body.bodySampleResolvedGoal
import com.example.kpkn.screens.body.bodySampleSchedule
import com.example.kpkn.screens.body.bodySampleSummary30
import com.example.kpkn.screens.body.bodySampleWeightPoints
import com.example.kpkn.screens.body.bodySampleWeightSummary
import com.example.kpkn.screens.body.design.BodyChartPoint
import com.example.kpkn.screens.body.design.BodyCompositionModule
import com.example.kpkn.screens.body.design.BodyFatModule
import com.example.kpkn.screens.body.design.BodyGoalModule
import com.example.kpkn.screens.body.design.BodyIsland
import com.example.kpkn.screens.body.design.BodyPageHeader
import com.example.kpkn.screens.body.design.BodyReminderModule
import com.example.kpkn.screens.body.design.BodyWeightModule
import com.example.kpkn.screens.body.design.bodyHistoryItems
import com.example.kpkn.screens.body.design.bodyIslandChangeText
import com.example.kpkn.screens.body.design.bodyWeightText
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.rememberIslandVisible
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
 * HOJAS DE DIBUJO de la página de Cuerpo entera (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 * 360 dp de ancho a densidad 3, con las muestras de `screens/body/BodySamples.kt`, al 100 % y al 130 % de letra. La página se monta como
 * la pantalla: una lista perezosa con cabecera, módulos e historial, y la isla encima. Cada escena es una prueba propia porque la regla
 * de Compose solo admite un `setContent` por prueba. Las capturas se parten por franjas para que cada PNG se pueda leer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h5000dp-xxhdpi", application = android.app.Application::class)
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

    /** Vista de la prueba: se dibuja completa a un bitmap y de ella se recortan las franjas. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Los datos que pinta la página: cada escena cambia solo estos. */
    private data class EscenaCuerpo(
        val resumen: BodyWeightSummary = bodySampleWeightSummary(),
        val meta: Double? = 60.0,
        val puntos: List<BodyChartPoint> = bodySampleWeightPoints(),
        /** % de grasa de la última medición con composición; sin él no hay módulo de grasa ni barra de composición. */
        val grasa: Double? = 17.2,
        val conAltura: Boolean = true,
        val unidad: WizardMassUnit = WizardMassUnit.KG,
        val historial: List<BodyHistoryRow> = bodySampleHistoryRows(),
        val recordatorio: MeasurementSchedule = bodySampleSchedule(),
    )

    /** La escala de letra del teléfono: misma densidad, con [escala] en el texto. */
    @Composable
    private fun AlTamano(escala: Float, contenido: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) { contenido() }
    }

    /** La página de Cuerpo tal como la monta la pantalla: cabecera, módulos e historial en una lista perezosa, con la isla encima. */
    @Composable
    private fun PaginaCuerpo(escena: EscenaCuerpo, listState: LazyListState, isla: Boolean) {
        val haze = remember { HazeState() }
        val margen = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        val actual = escena.resumen.current
        CompositionLocalProvider(LocalHazeState provides haze) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas).hazeSource(state = haze)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("pagina"),
                        contentPadding = PaddingValues(bottom = 140.dp),
                    ) {
                        item(key = "cabecera") {
                            Box(Modifier.testTag("cuerpo-cabecera")) { BodyPageHeader(topInset = 0.dp, onAdd = {}) }
                        }
                        item(key = "peso") {
                            BodyWeightModule(
                                summary = escena.resumen,
                                goalKg = escena.meta,
                                points = escena.puntos,
                                range = BodyChartRange.THREE_MONTHS,
                                today = BODY_SAMPLE_TODAY,
                                weightUnit = escena.unidad,
                                onRangeChange = {},
                                onAdd = {},
                                modifier = margen.testTag("cuerpo-peso"),
                            )
                        }
                        escena.grasa?.let { grasa ->
                            item(key = "grasa") {
                                BodyFatModule(
                                    current = grasa,
                                    start = bodySampleFatStart(),
                                    points = bodySampleFatPoints(),
                                    goalPercent = null,
                                    progressPercent = null,
                                    eta = null,
                                    modifier = margen.testTag("cuerpo-grasa"),
                                )
                            }
                        }
                        item(key = "composicion") {
                            BodyCompositionModule(
                                weightKg = actual,
                                weightUnit = escena.unidad,
                                leanKg = escena.grasa?.let { bodySampleLeanKg() },
                                fatKg = escena.grasa?.let { bodySampleFatKg() },
                                bodyFatPercent = escena.grasa?.let { BODY_SAMPLE_BODY_FAT },
                                musclePercent = escena.grasa?.let { BODY_SAMPLE_MUSCLE },
                                ffmi = if (escena.conAltura) escena.grasa?.let { bodySampleFfmi() } else null,
                                bmi = if (escena.conAltura) bodySampleBmi() else null,
                                hasHeight = escena.conAltura,
                                onAddHeight = {},
                                onInfo = {},
                                modifier = margen.testTag("cuerpo-composicion"),
                            )
                        }
                        item(key = "objetivo") {
                            BodyGoalModule(
                                goal = if (escena.meta != null) bodySampleResolvedGoal() else null,
                                weightUnit = escena.unidad,
                                activePlan = false,
                                onEditInPlan = {},
                                onEditOwn = {},
                                modifier = margen.testTag("cuerpo-objetivo"),
                            )
                        }
                        item(key = "recordatorio") {
                            BodyReminderModule(
                                schedule = escena.recordatorio,
                                customInterval = false,
                                onCustomIntervalChange = {},
                                onUpdate = {},
                                onEditTime = {},
                                modifier = margen.testTag("cuerpo-recordatorio"),
                            )
                        }
                        bodyHistoryItems(rows = escena.historial, weightUnit = escena.unidad, onOpen = {}, onAdd = {})
                        item(key = "fin") { Box(Modifier.fillMaxWidth().height(1.dp).testTag("cuerpo-fin")) }
                        item(key = "aire") { Spacer(Modifier.height(100.dp)) }
                    }
                }
                BodyIsland(
                    visible = isla,
                    title = actual?.let { bodyWeightText(it, escena.unidad) } ?: "Cuerpo",
                    subtitle = if (actual != null) bodyIslandChangeText(bodySampleSummary30(), escena.unidad) else null,
                    onAdd = {},
                )
            }
        }
    }

    /** La isla sola, con los textos de la página: el fondo es el negro de la pantalla y la isla va encima, como en la app. */
    @Composable
    private fun IslaSola(unidad: WizardMassUnit) {
        val haze = remember { HazeState() }
        val actual = bodySampleWeightSummary().current
        CompositionLocalProvider(LocalHazeState provides haze) {
            Box(Modifier.fillMaxWidth().height(160.dp)) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas).hazeSource(state = haze))
                Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 16.dp).testTag("isla")) {
                    BodyIsland(
                        visible = true,
                        title = actual?.let { bodyWeightText(it, unidad) } ?: "Cuerpo",
                        subtitle = bodyIslandChangeText(bodySampleSummary30(), unidad),
                        onAdd = {},
                    )
                }
            }
        }
    }

    private fun montarIsla(unidad: WizardMassUnit) {
        rule.setContent {
            hostView = LocalView.current
            AlTamano(1f) { IslaSola(unidad) }
        }
    }

    /** Monta la página con la escena; la isla sigue a la lista como en la pantalla (aparece al salir la cabecera). */
    private fun montar(escena: EscenaCuerpo, escala: Float = 1f) {
        rule.setContent {
            hostView = LocalView.current
            val listState = rememberLazyListState()
            val isla = rememberIslandVisible(listState, afterItemIndex = 0)
            AlTamano(escala) {
                PaginaCuerpo(escena = escena, listState = listState, isla = isla)
            }
        }
    }

    /** Recorta de la vista completa la franja entre [arriba] y [abajo] (píxeles de la raíz) y la guarda en `KPKN_RENDER_DIR/<nombre>.png`. */
    private fun guardarFranja(nombre: String, arriba: Float, abajo: Float) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        rule.waitForIdle()
        val vista = hostView!!
        val completa = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val lienzo = android.graphics.Canvas(completa)
            lienzo.drawColor(android.graphics.Color.BLACK)
            vista.draw(lienzo)
        }
        val top = arriba.toInt().coerceIn(0, completa.height - 2)
        val bottom = abajo.toInt().coerceIn(top + 1, completa.height)
        val recorte = Bitmap.createBitmap(completa, 0, top, completa.width, bottom - top)
        FileOutputStream(File(dir, "$nombre.png")).use { recorte.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Franja desde el borde superior del nodo [desde] hasta el inferior del nodo [hasta]: un módulo o varios seguidos. */
    private fun guardarEntre(nombre: String, desde: String, hasta: String) {
        rule.waitForIdle()
        val arriba = rule.onNodeWithTag(desde, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val abajo = rule.onNodeWithTag(hasta, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom
        guardarFranja(nombre, arriba, abajo)
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
    fun paginaCompleta100() {
        onlyWhenAsked()
        montar(EscenaCuerpo())
        rule.waitForIdle()
        guardarEntre("cuerpo_completo_100_a_arriba", "cuerpo-cabecera", "cuerpo-grasa")
        guardarEntre("cuerpo_completo_100_b_composicion", "cuerpo-composicion", "cuerpo-objetivo")
        guardarEntre("cuerpo_completo_100_c_recordatorio_historial", "cuerpo-recordatorio", "cuerpo-fin")
    }

    @Test
    fun paginaCompleta130() {
        onlyWhenAsked()
        montar(EscenaCuerpo(), escala = 1.3f)
        rule.waitForIdle()
        guardarEntre("cuerpo_completo_130_a_arriba", "cuerpo-cabecera", "cuerpo-grasa")
        guardarEntre("cuerpo_completo_130_b_composicion", "cuerpo-composicion", "cuerpo-objetivo")
        guardarEntre("cuerpo_completo_130_c_recordatorio_historial", "cuerpo-recordatorio", "cuerpo-fin")
    }

    @Test
    fun paginaEnLibras100() {
        onlyWhenAsked()
        montar(EscenaCuerpo(unidad = WizardMassUnit.LB))
        rule.waitForIdle()
        guardarEntre("cuerpo_libras_100_a_arriba", "cuerpo-cabecera", "cuerpo-grasa")
        guardarEntre("cuerpo_libras_100_b_composicion", "cuerpo-composicion", "cuerpo-objetivo")
        guardarEntre("cuerpo_libras_100_c_recordatorio_historial", "cuerpo-recordatorio", "cuerpo-fin")
    }

    @Test
    fun personaNuevaSinPesajesAlturaNiObjetivo() {
        onlyWhenAsked()
        montar(
            EscenaCuerpo(
                resumen = BodyWeightSummary(),
                meta = null,
                puntos = emptyList(),
                grasa = null,
                conAltura = false,
                historial = emptyList(),
            ),
        )
        rule.waitForIdle()
        guardarEntre("cuerpo_persona_nueva_100", "cuerpo-cabecera", "cuerpo-fin")
    }

    /** La isla sola, en kg y en libras: la pinta `BodyIsland` con `visible = true` y los textos de la página, sin depender de la lista. */
    @Test
    fun islaSolaEnKilogramos() {
        onlyWhenAsked()
        montarIsla(WizardMassUnit.KG)
        rule.waitForIdle()
        guardarEntre("cuerpo_isla_sola_100", "isla", "isla")
    }

    @Test
    fun islaSolaEnLibras() {
        onlyWhenAsked()
        montarIsla(WizardMassUnit.LB)
        rule.waitForIdle()
        guardarEntre("cuerpo_isla_sola_libras_100", "isla", "isla")
    }

    @Test
    fun burbujaDeLaTendencia() {
        onlyWhenAsked()
        montar(EscenaCuerpo())
        rule.waitForIdle()
        // Sin avance del reloj, la burbuja no se cierra sola mientras se dibuja.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Tendencia de peso", substring = true)
            .performTouchInput { click() }
        asentar()
        guardarEntre("cuerpo_burbuja_100", "cuerpo-peso", "cuerpo-peso")
    }
}
