package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.technique.TechniqueSamples
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import com.example.kpkn.screens.technique.player.TechniqueSpeed
import com.example.kpkn.screens.technique.player.design.TechniqueOverlay
import com.example.kpkn.screens.technique.player.design.TechniquePlayerControls
import com.example.kpkn.screens.technique.player.design.TechniqueRepChips
import com.example.kpkn.screens.technique.player.design.TechniqueScrubber
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
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
 * HOJAS DE DIBUJO del reproductor de técnica (VT7a; no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se
 * salta. 360 dp de ancho a densidad 3, con las muestras de `screens/technique/TechniqueSamples.kt`, al 100 % y al 130 % de letra:
 * los mandos en reposo y reproduciendo, la línea de tiempo con 8 marcas y la 4.ª elegida, los chips con parciales y fallidas, la capa
 * sobre un fondo gris de 9:16 (esqueleto, trayectoria y guía), los tres iconos nuevos y el conjunto como lo monta la pantalla.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class TechniquePlayerRenderSheetTest {

    companion object {
        /** Marca de la hoja que se recorta y se guarda. */
        private const val HOJA = "hoja"

        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Vista de la prueba: se dibuja a un bitmap, como en las demás hojas de render. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Recorta el nodo con la marca [tag] de la vista completa y lo guarda en `KPKN_RENDER_DIR/<nombre>.png`. */
    private fun guardar(nombre: String, tag: String = HOJA) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        rule.waitForIdle()
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

    /** Monta [contenido] sobre el negro de la pantalla, con la escala de letra [escala] y, si se pide, el movimiento reducido. */
    private fun montar(escala: Float, movimientoReducido: Boolean = false, contenido: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, escala),
                LocalWizardReducedMotion provides movimientoReducido,
            ) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                    Column(Modifier.testTag(HOJA).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        contenido()
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(600L)
    }

    private fun escena(nombre: String, escala: Float, movimientoReducido: Boolean = false, contenido: @Composable () -> Unit) {
        onlyWhenAsked()
        montar(escala, movimientoReducido, contenido)
        guardar(nombre)
    }

    // Instantes de la 4.ª sentadilla del ejemplo: abajo del todo y a mitad de la subida.
    private val repCuatro get() = TechniqueSamples.squatReport.reps[3]
    private val enElGiro: Long get() = repCuatro.phases.turnStartUs + 100_000L
    private val enLaSubida: Long get() = (repCuatro.phases.turnEndUs + repCuatro.phases.arriveUs) / 2

    // ── Iconos ────────────────────────────────────────────────────────────────

    @Test
    fun iconosNuevos100() = escena("iconos_100", 1f) {
        val nuevos = listOf("FrameBack" to KpknLineIcons.FrameBack, "FrameForward" to KpknLineIcons.FrameForward, "Target" to KpknLineIcons.Target)
        val vecinos = listOf("Play" to KpknLineIcons.Play, "Pause" to KpknLineIcons.Pause, "Repeat" to KpknLineIcons.Repeat)
        FilaDeIconos(nuevos + vecinos, 24.dp)
        FilaDeIconos(nuevos + vecinos, 20.dp)
        // Ampliados, solo los nuevos: caben a 80 dp sin que se parta ninguna etiqueta.
        FilaDeIconos(nuevos, 80.dp)
    }

    // ── Mandos ────────────────────────────────────────────────────────────────

    @Test
    fun mandosEnReposo100() = escena("mandos_reposo_100", 1f) { MandosDeEjemplo(reproduciendo = false) }

    @Test
    fun mandosEnReposo130() = escena("mandos_reposo_130", 1.3f) { MandosDeEjemplo(reproduciendo = false) }

    @Test
    fun mandosReproduciendo100() = escena("mandos_reproduciendo_100", 1f) { MandosDeEjemplo(reproduciendo = true) }

    @Test
    fun mandosReproduciendo130() = escena("mandos_reproduciendo_130", 1.3f) { MandosDeEjemplo(reproduciendo = true) }

    /** Sin repeticiones que repetir el bucle queda apagado y sin respuesta. */
    @Test
    fun mandosSinRepeticiones100() = escena("mandos_sin_repeticiones_100", 1f) { MandosDeEjemplo(reproduciendo = false, bucleDisponible = false) }

    @Composable
    private fun MandosDeEjemplo(reproduciendo: Boolean, bucleDisponible: Boolean = true) {
        TechniquePlayerControls(
            isPlaying = reproduciendo,
            speed = if (reproduciendo) TechniqueSpeed.HALF else TechniqueSpeed.NORMAL,
            loopEnabled = reproduciendo,
            onPlayPause = {},
            onFrameBack = {},
            onFrameForward = {},
            onSpeedChange = {},
            onLoopToggle = {},
            loopAvailable = bucleDisponible,
        )
    }

    // ── Línea de tiempo ───────────────────────────────────────────────────────

    @Test
    fun lineaDeTiempo100() = escena("linea_de_tiempo_100", 1f) { LineaDeEjemplo() }

    @Test
    fun lineaDeTiempo130() = escena("linea_de_tiempo_130", 1.3f) { LineaDeEjemplo() }

    @Composable
    private fun LineaDeEjemplo() {
        // 8 marcas (la 3.ª parcial, la 6.ª fallida) y la 4.ª elegida, a los 13 s de 31.
        TechniqueScrubber(
            positionUs = { 13_000_000L },
            durationUs = TechniqueSamples.MIXED_DURATION_US,
            reps = TechniqueSamples.mixedMarks,
            selectedRep = 3,
            onSeek = {},
        )
        // Y sin repetición elegida, al principio y al final, para ver la bolita en los extremos.
        TechniqueScrubber(positionUs = { 0L }, durationUs = TechniqueSamples.MIXED_DURATION_US, reps = TechniqueSamples.mixedMarks, selectedRep = -1, onSeek = {})
        TechniqueScrubber(positionUs = { TechniqueSamples.MIXED_DURATION_US }, durationUs = TechniqueSamples.MIXED_DURATION_US, reps = TechniqueSamples.mixedMarks, selectedRep = 7, onSeek = {})
    }

    // ── Chips ─────────────────────────────────────────────────────────────────

    @Test
    fun chips100() = escena("chips_100", 1f) { ChipsDeEjemplo() }

    @Test
    fun chips130() = escena("chips_130", 1.3f) { ChipsDeEjemplo() }

    @Composable
    private fun ChipsDeEjemplo() {
        TechniqueRepChips(reps = TechniqueSamples.mixedMarks, selectedRep = 3, onSelectRep = {})
        TechniqueRepChips(reps = TechniqueSamples.mixedMarks, selectedRep = 5, onSelectRep = {})
    }

    /** Una serie larga: la elegida (la 12.ª) queda fuera de la vista y la fila la centra; se difumina por los dos bordes. */
    @Test
    fun chipsDeUnaSerieLarga100() = escena("chips_serie_larga_100", 1f) {
        val largas = List(16) { TechniqueSamples.mixedMarks[it % 8].copy(index = it) }
        TechniqueRepChips(reps = largas, selectedRep = 11, onSelectRep = {})
        // Y con la primera parcial y fallida, para ver el borde izquierdo con un chip ancho.
        TechniqueRepChips(reps = largas.mapIndexed { i, m -> if (i == 0) m.copy(kind = RepKind.PARTIAL) else m }, selectedRep = 0, onSelectRep = {})
    }

    // ── Capa sobre el vídeo ───────────────────────────────────────────────────

    @Test
    fun capaEnElGiro100() = escena("capa_giro_100", 1f) { VideoGris { Capa(TechniqueSamples.squatOverlay, enElGiro) } }

    @Test
    fun capaEnLaSubida100() = escena("capa_subida_100", 1f) { VideoGris { Capa(TechniqueSamples.squatOverlay, enLaSubida) } }

    @Test
    fun capaEnLaSubida130() = escena("capa_subida_130", 1.3f) { VideoGris { Capa(TechniqueSamples.squatOverlay, enLaSubida) } }

    @Test
    fun capaConDisco100() = escena("capa_disco_100", 1f) { VideoGris { Capa(TechniqueSamples.squatPlateOverlay, enLaSubida) } }

    @Test
    fun capaConMovimientoReducido100() = escena("capa_movimiento_reducido_100", 1f, movimientoReducido = true) {
        VideoGris { Capa(TechniqueSamples.squatOverlay, enElGiro) }
    }

    @Composable
    private fun Capa(overlay: TechniqueOverlayData, posicion: Long) {
        TechniqueOverlay(overlay = overlay, positionUs = { posicion }, modifier = Modifier.fillMaxSize())
    }

    /** Un vídeo de 9:16 de mentira: un degradado gris con las esquinas de 20 dp que le pone la pantalla de análisis. */
    @Composable
    private fun VideoGris(contenido: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF6B7075), Color(0xFF30353A)))),
        ) { contenido() }
    }

    // ── Conjunto ──────────────────────────────────────────────────────────────

    @Test
    fun conjunto100() = escena("conjunto_100", 1f) { Conjunto() }

    @Test
    fun conjunto130() = escena("conjunto_130", 1.3f) { Conjunto() }

    /** El reproductor como lo monta la pantalla de análisis: vídeo, línea de tiempo, mandos y chips. */
    @Composable
    private fun Conjunto() {
        val overlay = TechniqueSamples.squatOverlay
        VideoGris { Capa(overlay, enLaSubida) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TechniqueScrubber(
                positionUs = { enLaSubida },
                durationUs = overlay.lastFrameUs + 1_000_000L,
                reps = overlay.reps,
                selectedRep = 3,
                onSeek = {},
            )
            MandosDeEjemplo(reproduciendo = true)
            TechniqueRepChips(reps = overlay.reps, selectedRep = 3, onSelectRep = {})
        }
    }

    // ── Auxiliares de la hoja de iconos ───────────────────────────────────────

    @Composable
    private fun FilaDeIconos(iconos: List<Pair<String, ImageVector>>, tamano: androidx.compose.ui.unit.Dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            for ((nombre, icono) in iconos) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icono, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(tamano))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        nombre,
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 9.sp),
                        color = WizardColors.textFaint,
                    )
                }
            }
        }
    }
}
