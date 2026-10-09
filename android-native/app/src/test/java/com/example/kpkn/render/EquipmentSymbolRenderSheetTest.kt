package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.screens.onboarding.design.entreno.BarbellArt
import com.example.kpkn.screens.onboarding.design.entreno.BoxArt
import com.example.kpkn.screens.onboarding.design.entreno.KettlebellArt
import com.example.kpkn.screens.onboarding.design.entreno.MachinesArt
import com.example.kpkn.screens.onboarding.design.entreno.RackArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.equipmentArt
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO de los símbolos de material (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin
 * ella se salta. Escribe `symbols_rest.png` (los 16 en reposo: arriba a escala 4 y, debajo, a tamaño real) y
 * `frames_<nombre>.png` (seis cuadros repartidos en el período, seleccionado) de los cinco rediseñados.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w2100dp-h2100dp-mdpi", application = android.app.Application::class)
class EquipmentSymbolRenderSheetTest {

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

    /** Los cinco rediseñados, con el nombre de su hoja. */
    private val redesigned: Map<String, SymbolArt> = linkedMapOf(
        "barra" to BarbellArt,
        "rack" to RackArt,
        "kettlebell" to KettlebellArt,
        "maquinas" to MachinesArt,
        "cajon" to BoxArt,
    )

    /** Lo que se dibuja ahora: "rest" (los 16 en reposo) o el nombre de uno de los cinco. */
    private var current by mutableStateOf("rest")

    private var hostView: View? = null

    @Composable
    private fun RestSheet() {
        Column {
            for (row in EquipmentSymbolId.entries.chunked(4)) {
                Row {
                    for (id in row) {
                        val art = equipmentArt(id)
                        SymbolRenderSheet.Frame(art, art.restT, selected = false)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            // Tamaño real (64 px, ≈ 60 dp), para ver si se entiende a esa escala.
            Row {
                for (id in EquipmentSymbolId.entries) {
                    val art = equipmentArt(id)
                    SymbolRenderSheet.Frame(art, art.restT, selected = false, px = 64f)
                }
            }
        }
    }

    @Test
    fun renderTheEquipmentSheets() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.testTag("sheet").background(Color.Black).padding(16.dp)) {
                    val art = redesigned[current]
                    if (art == null) {
                        RestSheet()
                    } else {
                        SymbolRenderSheet.Strip(art, List(6) { art.period * it / 6f })
                    }
                }
            }
        }
        for (name in listOf("rest") + redesigned.keys) {
            rule.runOnUiThread { current = name }
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
            val file = if (name == "rest") "symbols_rest" else "frames_$name"
            rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, file) }
        }
    }
}
