package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.body.bodySampleHistoryRows
import com.example.kpkn.screens.body.design.BODY_RECORD_DELETE_TAG
import com.example.kpkn.screens.body.design.BodyRecordDraft
import com.example.kpkn.screens.body.design.BodyRecordSheet
import com.example.kpkn.screens.body.design.bodyHistoryItems
import com.example.kpkn.screens.body.design.bodyRecordFrom
import com.example.kpkn.screens.body.design.bodyRecordNew
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.design.KpknMonthCalendar
import com.example.kpkn.ui.design.KpknSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO del historial y de la hoja de registro de Cuerpo (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`;
 * sin ella se salta. 360 dp de ancho a densidad 3, con las muestras de `screens/body/BodySamples.kt`, al 100 % y al 130 % de letra.
 * Cada escena es una prueba propia porque la regla de Compose solo admite un `setContent` por prueba.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class BodyHistoryRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        /** Día y hora fijos de la escena: la hoja de render no depende del reloj. */
        private val AHORA: LocalDateTime = LocalDateTime.of(2026, 10, 13, 10, 0)
    }

    @get:Rule
    val rule = createComposeRule()

    /** Vista de la prueba: se dibuja a un bitmap, como en las demás hojas de render. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Recorta el nodo con la marca [tag] de la vista completa y lo guarda en `KPKN_RENDER_DIR/<nombre>.png`. */
    private fun guardar(nombre: String, tag: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        rule.waitForIdle()
        // Sin el árbol sin fusionar, las marcas de dentro de la hoja (un clickable que fusiona descendientes) no aparecen.
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val vista = hostView!!
        val completa = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val lienzo = android.graphics.Canvas(completa)
            lienzo.drawColor(android.graphics.Color.BLACK)
            vista.draw(lienzo)
        }
        val left = bounds.left.toInt().coerceIn(0, completa.width - 1)
        val top = bounds.top.toInt().coerceIn(0, completa.height - 1)
        val width = bounds.width.toInt().coerceIn(1, completa.width - left)
        val height = bounds.height.toInt().coerceIn(1, completa.height - top)
        val recorte = Bitmap.createBitmap(completa, left, top, width, height)
        FileOutputStream(File(dir, "$nombre.png")).use { recorte.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** La pantalla de Cuerpo tal como la monta la app: fondo negro y el vidrio de la hoja sobre él. */
    private fun montar(escala: Float, contenido: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            val haze = remember { HazeState() }
            CompositionLocalProvider(LocalHazeState provides haze) {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pantalla")
                            .background(KpknSurface.canvas)
                            .hazeSource(state = haze),
                    ) {
                        contenido()
                    }
                }
            }
        }
        // Termina la entrada de la hoja antes de medir o hacer clic.
        rule.mainClock.advanceTimeBy(1_000L)
    }

    /** La hoja con su borrador en estado propio, como la monta la pantalla. */
    @Composable
    private fun HojaDeRegistro(inicial: BodyRecordDraft, origen: BodyHistoryRow?) {
        var borrador by remember { mutableStateOf(inicial) }
        BodyRecordSheet(
            draft = borrador,
            onDraftChange = { borrador = it },
            source = origen,
            unit = WizardMassUnit.KG,
            anchorKg = 64.5,
            now = AHORA,
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }

    /**
     * Una escena de la hoja: se monta, se hace la [accion] (p. ej. abrir «Medidas») y se guarda el recorte de [tag]. Por defecto, la
     * pantalla entera: así se ven también las acciones fijas al pie de la hoja.
     */
    private fun escenaDeHoja(
        nombre: String,
        escala: Float,
        origen: BodyHistoryRow?,
        inicial: BodyRecordDraft,
        tag: String = "pantalla",
        accion: () -> Unit = {},
    ) {
        onlyWhenAsked()
        montar(escala) { HojaDeRegistro(inicial, origen) }
        accion()
        guardar(nombre, tag)
    }

    private val medicionManual: BodyHistoryRow get() = bodySampleHistoryRows().first()
    private val medicionDeHealthConnect: BodyHistoryRow get() = bodySampleHistoryRows().first { it.fromHealthConnect }

    // ── Historial ────────────────────────────────────────────────────────────

    @Test
    fun historialDeTresMeses100() {
        onlyWhenAsked()
        montar(1f) {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("historial")) {
                bodyHistoryItems(rows = bodySampleHistoryRows(), onOpen = {}, onAdd = {})
            }
        }
        guardar("historial_3_meses_100", "historial")
    }

    @Test
    fun historialDeTresMeses130() {
        onlyWhenAsked()
        montar(1.3f) {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("historial")) {
                bodyHistoryItems(rows = bodySampleHistoryRows(), onOpen = {}, onAdd = {})
            }
        }
        guardar("historial_3_meses_130", "historial")
    }

    @Test
    fun historialVacio100() {
        onlyWhenAsked()
        montar(1f) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp).testTag("historial")) {
                bodyHistoryItems(rows = emptyList(), onOpen = {}, onAdd = {})
            }
        }
        guardar("historial_vacio_100", "historial")
    }

    // ── Hoja de registro ─────────────────────────────────────────────────────

    @Test
    fun hojaDeAlta100() = escenaDeHoja("hoja_alta_100", 1f, null, bodyRecordNew(AHORA).copy(weightKg = 64.5))

    @Test
    fun hojaDeAlta130() = escenaDeHoja("hoja_alta_130", 1.3f, null, bodyRecordNew(AHORA).copy(weightKg = 64.5))

    @Test
    fun hojaDeEdicionConMedidasDesplegadas100() = escenaDeHoja(
        "hoja_edicion_medidas_100", 1f, medicionManual, bodyRecordFrom(medicionManual),
        accion = { rule.onNodeWithText("Medidas").performScrollTo().performClick() },
    )

    @Test
    fun hojaDeEdicionConMedidasDesplegadas130() = escenaDeHoja(
        "hoja_edicion_medidas_130", 1.3f, medicionManual, bodyRecordFrom(medicionManual),
        accion = { rule.onNodeWithText("Medidas").performScrollTo().performClick() },
    )

    @Test
    fun hojaDeSoloLectura100() = escenaDeHoja(
        "hoja_solo_lectura_100", 1f, medicionDeHealthConnect, bodyRecordFrom(medicionDeHealthConnect),
    )

    @Test
    fun hojaDeSoloLectura130() = escenaDeHoja(
        "hoja_solo_lectura_130", 1.3f, medicionDeHealthConnect, bodyRecordFrom(medicionDeHealthConnect),
    )

    @Test
    fun dialogoDeBorrar100() = escenaDeHoja(
        "dialogo_borrar_100", 1f, medicionManual, bodyRecordFrom(medicionManual), tag = "pantalla",
        accion = {
            rule.onNodeWithTag(BODY_RECORD_DELETE_TAG).performClick()
        },
    )

    // ── Calendario ───────────────────────────────────────────────────────────

    @Test
    fun calendarioDeMes100() {
        onlyWhenAsked()
        montar(1f) {
            KpknMonthCalendar(
                selected = AHORA.toLocalDate(),
                onSelect = {},
                today = AHORA.toLocalDate(),
                maxDate = AHORA.toLocalDate(),
                modifier = Modifier.padding(16.dp).testTag("calendario"),
            )
        }
        guardar("calendario_100", "calendario")
    }

    @Test
    fun calendarioDeMes130() {
        onlyWhenAsked()
        montar(1.3f) {
            KpknMonthCalendar(
                selected = AHORA.toLocalDate(),
                onSelect = {},
                today = AHORA.toLocalDate(),
                maxDate = AHORA.toLocalDate(),
                modifier = Modifier.padding(16.dp).testTag("calendario"),
            )
        }
        guardar("calendario_130", "calendario")
    }
}
