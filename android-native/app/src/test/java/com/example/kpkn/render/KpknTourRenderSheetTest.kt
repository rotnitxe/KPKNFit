package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import com.example.kpkn.ui.tour.TourStep
import com.example.kpkn.ui.tour.TourStepKind
import com.example.kpkn.ui.tour.tourAnchor
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
 * HOJA DE DIBUJO del recorrido guiado (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 * Teléfono de 360 × 780 dp. Sin desenfoque de pantalla: la burbuja sale con su respaldo #141414 al 94 %.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", application = Application::class)
class KpknTourRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Vista de Compose del host, para dibujarla a un bitmap como en `WizardRenderSheetTest`. */
    private var hostView: View? = null

    /** Escala de letra del escenario: 1,0 normal; 1,3 para la prueba de letra grande. */
    private var fontScale by mutableStateOf(1f)

    private class NoSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()

        override fun markSeen(tourId: String) = Unit
    }

    /** Recorrido de prueba: arriba (burbuja debajo), abajo (burbuja encima) y un paso «Pruébalo» sobre el bloque de arriba. */
    private val steps = listOf(
        TourStep("arriba", "Sesión de hoy", "Toca para ver los ejercicios de hoy.", TourStepKind.NEXT),
        TourStep("abajo", "Empieza cuando quieras", "Pulsa aquí para empezar la sesión.", TourStepKind.NEXT),
        TourStep("arriba", "Abre tu sesión", "Toca el bloque de arriba para verla.", TourStepKind.TRY),
    )

    /** Pantalla de prueba: un bloque arriba y un botón abajo, ambos anclas del recorrido. */
    @Composable
    private fun RenderScreen(host: KpknOverlayHostController) {
        // Fondo gris medio (no negro): así el velo oscurece la pantalla y el margen de 8 dp del recorte se distingue.
        Box(Modifier.fillMaxSize().background(Color(0xFF3A3A3A))) {
            Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 96.dp, end = 24.dp, bottom = 120.dp)) {
                // El ancla va antes del fondo y del texto: sus límites son exactamente el bloque que se ve.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .tourAnchor("arriba")
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF262626)),
                ) {
                    Text("Sesión de hoy", color = Color(0xFFF2EEE6), modifier = Modifier.padding(16.dp))
                }
                Box(Modifier.weight(1f)) {}
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF2EEE6))
                        .tourAnchor("abajo"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Empezar", color = Color(0xFF0B0B0B))
                }
            }
            KpknOverlayHostContent(host)
        }
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        FileOutputStream(File(dir, "$name.png")).use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderTourSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        val host = KpknOverlayHostController(tourSeen = NoSeen())
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale),
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                RenderScreen(host)
            }
        }
        rule.waitForIdle()

        // Paso 1: sobre el bloque de arriba, la burbuja va debajo.
        rule.runOnIdle { host.startTour("hoja-recorrido", steps) }
        rule.waitForIdle()
        save("tour_01_paso1_ancla_arriba_burbuja_abajo")

        // Paso 2: sobre el botón de abajo, la burbuja va encima.
        rule.runOnIdle { host.tour.next() }
        rule.waitForIdle()
        save("tour_02_paso2_ancla_abajo_burbuja_arriba")

        // Paso «Pruébalo»: el recorte deja pasar el toque al bloque de arriba.
        rule.runOnIdle { host.tour.next() }
        rule.waitForIdle()
        save("tour_03_pruebalo_ancla_arriba")

        // Paso 1 con la letra al 130 %.
        rule.runOnIdle {
            host.tour.skip()
            fontScale = 1.3f
            host.startTour("hoja-recorrido-130", steps)
        }
        rule.waitForIdle()
        save("tour_04_paso1_letra_130")
    }
}
