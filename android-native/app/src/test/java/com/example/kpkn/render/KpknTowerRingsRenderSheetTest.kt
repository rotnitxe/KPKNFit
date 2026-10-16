package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.R
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
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
 * HOJA DE DIBUJO de la torre de RINGS frente al logo (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`;
 * sin esa variable se salta. Sigue el patrón de [WizardRenderSheetTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", application = android.app.Application::class)
class KpknTowerRingsRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose, para dibujarla a un bitmap (igual que en la hoja del wizard). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
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

    @Composable
    private fun Caption(text: String) {
        Text(text, color = Color(0xFF9A958D), fontSize = 12.sp)
    }

    @Composable
    private fun Cell(text: String, content: @Composable () -> Unit) {
        Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Caption(text)
            content()
        }
    }

    @Composable
    private fun MiniRow(text: String, values: TowerRingValues, visible: Set<RingChannel> = RingChannel.entries.toSet()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Caption(text)
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                KpknTowerRings(values = values, modifier = Modifier.fillMaxSize(), visible = visible)
            }
        }
    }

    @Test
    fun renderTheTowerSheets() {
        onlyWhenAsked()
        // Sin reloj automático: la hoja se dibuja cuando las animaciones de entrada han acabado (avance manual).
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            Column(
                Modifier.width(360.dp).background(Color.Black).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(Modifier.testTag("logo"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption("Logo de referencia (izquierda) y torre 100 / 100 / 100 a 150 dp de ancho (derecha)")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.kpkn_logo_vertical),
                            contentDescription = null,
                            modifier = Modifier.size(150.dp, 134.5.dp),
                        )
                        KpknTowerRings(TowerRingValues(100, 100, 100), Modifier.size(150.dp, 134.5.dp))
                    }
                }
                Column(Modifier.testTag("torre300"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption("Inicio 300 dp · 100 / 100 / 100")
                    KpknTowerRings(TowerRingValues(100, 100, 100), Modifier.width(300.dp).aspectRatio(300f / 230f))
                    Caption("Inicio 300 dp · 25 / 30 / 20 (bajos)")
                    KpknTowerRings(TowerRingValues(25, 30, 20), Modifier.width(300.dp).aspectRatio(300f / 230f))
                    Caption("Inicio 300 dp · 72 / 64 / sin calibrar")
                    KpknTowerRings(TowerRingValues(72, 64, null), Modifier.width(300.dp).aspectRatio(300f / 230f))
                }
                Column(Modifier.testTag("torre220"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption("Estado previo 220 dp · 100 / 100 / 100")
                    KpknTowerRings(TowerRingValues(100, 100, 100), Modifier.size(220.dp))
                    Caption("Estado previo 220 dp · 72 / 64 / 80")
                    KpknTowerRings(TowerRingValues(72, 64, 80), Modifier.size(220.dp))
                    Caption("Estado previo 220 dp · 20 / 45 / sin calibrar")
                    KpknTowerRings(TowerRingValues(20, 45, null), Modifier.size(220.dp))
                }
                Column(Modifier.testTag("torre120"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Cell("Final 120 dp · 100 / 100 / 100") { KpknTowerRings(TowerRingValues(100, 100, 100), Modifier.size(120.dp)) }
                        Cell("Final 120 dp · 72 / 64 / 80") { KpknTowerRings(TowerRingValues(72, 64, 80), Modifier.size(120.dp)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Cell("Final 120 dp · 20 / 45 / sin calibrar") {
                            KpknTowerRings(TowerRingValues(20, 45, null), Modifier.size(120.dp))
                        }
                        Cell("Modo Free · solo Energía") {
                            KpknTowerRings(
                                TowerRingValues(72, 64, 80),
                                Modifier.size(120.dp),
                                visible = setOf(RingChannel.ENERGIA),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Cell("Resaltado · Músculos") {
                            KpknTowerRings(TowerRingValues(72, 64, 80), Modifier.size(120.dp), highlighted = RingChannel.MUSCULOS)
                        }
                        Cell("Resaltado · Columna") {
                            KpknTowerRings(TowerRingValues(72, 64, 80), Modifier.size(120.dp), highlighted = RingChannel.COLUMNA)
                        }
                    }
                }
                Column(Modifier.testTag("mini40"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniRow("40 dp · 100 / 100 / 100", TowerRingValues(100, 100, 100))
                    MiniRow("40 dp · 72 / 64 / 80", TowerRingValues(72, 64, 80))
                    MiniRow("40 dp · 20 / 45 / sin calibrar", TowerRingValues(20, 45, null))
                    MiniRow("40 dp · Modo Free", TowerRingValues(72, 64, 80), setOf(RingChannel.ENERGIA))
                }
            }
        }
        rule.mainClock.advanceTimeBy(3000L)
        rule.waitForIdle()
        save("logo", "e1_logo_frente")
        save("torre300", "e1_torre_inicio_300")
        save("torre220", "e1_torre_220")
        save("torre120", "e1_torre_120")
        save("mini40", "e1_mini_40")
    }
}
