package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import com.example.kpkn.screens.sessioneditor.design.SessionAssistantSheetContent
import com.example.kpkn.screens.sessioneditor.design.SessionAssistantTab
import com.example.kpkn.screens.sessioneditor.design.SessionAssistantTemplatesTab
import com.example.kpkn.screens.sessioneditor.design.SessionImpactModule
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
 * HOJA DE DIBUJO del impacto de la sesión y del asistente (SE4): el módulo con datos, la pestaña Impacto con y sin datos, las sugerencias
 * con y sin ellas, la carga y las plantillas. No comprueba nada: escribe PNG. Pinta a 360 dp, con la letra al 100 % y al 130 %, sin
 * movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class SessionImpactRenderSheetTest {

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

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Los casos de la hoja, en orden. */
    private val cases = listOf(
        "modulo", "impacto", "impactoVacio", "sugerencias", "sinSugerencias", "plantillas", "cargando",
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Alto de cada caso: la pestaña Impacto y las sugerencias son largas y se ven enteras. */
    private fun heightOf(name: String): Int = when (name) {
        "modulo" -> 300
        "impacto" -> 1150
        "impactoVacio" -> 320
        "sugerencias" -> 820
        "sinSugerencias" -> 360
        "plantillas" -> 600
        else -> 300
    }

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

    /** El caso [name], con sus datos de ejemplo. Sin estado: todo llega por parámetros. */
    @Composable
    private fun Case(name: String) {
        when (name) {
            "modulo" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SessionImpactModule(impact = SessionEditorSamples.impact, onOpenAssistant = {})
            }
            "impacto" -> SessionAssistantSheetContent(
                tab = SessionAssistantTab.IMPACT,
                onTabSelect = {},
                impact = SessionEditorSamples.impact,
                analysisReady = true,
                suggestions = emptyList(),
                onApplySuggestion = { _, _ -> },
                templatesTab = {},
                modifier = Modifier.fillMaxSize(),
            )
            "impactoVacio" -> SessionAssistantSheetContent(
                tab = SessionAssistantTab.IMPACT,
                onTabSelect = {},
                impact = null,
                analysisReady = true,
                suggestions = emptyList(),
                onApplySuggestion = { _, _ -> },
                templatesTab = {},
                modifier = Modifier.fillMaxSize(),
            )
            "sugerencias" -> SessionAssistantSheetContent(
                tab = SessionAssistantTab.SUGGESTIONS,
                onTabSelect = {},
                impact = SessionEditorSamples.impact,
                analysisReady = true,
                suggestions = SessionEditorSamples.suggestions,
                onApplySuggestion = { _, _ -> },
                templatesTab = {},
                modifier = Modifier.fillMaxSize(),
            )
            "sinSugerencias" -> SessionAssistantSheetContent(
                tab = SessionAssistantTab.SUGGESTIONS,
                onTabSelect = {},
                impact = SessionEditorSamples.impact,
                analysisReady = true,
                suggestions = emptyList(),
                onApplySuggestion = { _, _ -> },
                templatesTab = {},
                modifier = Modifier.fillMaxSize(),
            )
            "plantillas" -> SessionAssistantSheetContent(
                tab = SessionAssistantTab.TEMPLATES,
                onTabSelect = {},
                impact = SessionEditorSamples.impact,
                analysisReady = true,
                suggestions = emptyList(),
                onApplySuggestion = { _, _ -> },
                templatesTab = {
                    SessionAssistantTemplatesTab(
                        templates = emptyList(),
                        searchQuery = "",
                        applyDecision = null,
                        onSearchChange = {},
                        onSelectTemplate = {},
                        onConfirmApplyTemplate = {},
                        onCancelApply = {},
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            else -> SessionAssistantSheetContent(
                tab = SessionAssistantTab.SUGGESTIONS,
                onTabSelect = {},
                impact = SessionEditorSamples.impact,
                analysisReady = false,
                suggestions = emptyList(),
                onApplySuggestion = { _, _ -> },
                templatesTab = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun renderSessionImpact() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(heightOf(cases[current]).dp)
                        .background(Color.Black),
                ) {
                    Case(cases[current])
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "${name}_$suffix")
            }
        }
    }
}
