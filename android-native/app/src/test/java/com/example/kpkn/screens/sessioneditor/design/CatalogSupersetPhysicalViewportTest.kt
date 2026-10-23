package com.example.kpkn.screens.sessioneditor.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Ventana física real: la captura antigua recorta a880 una raíz configurada a1000. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class CatalogSupersetPhysicalViewportTest {
    @get:Rule val rule = createComposeRule()
    private var view: View? = null

    private fun fullText(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(label, layout.didOverflowHeight)
        assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        for (line in 0 until layout.lineCount) {
            assertFalse(label, layout.isLineEllipsized(line))
            assertTrue(label, layout.getLineLeft(line) >= -1f)
            assertTrue(label, layout.getLineRight(line) <= layout.size.width + 1f)
        }
    }

    private fun capture(name: String) {
        val dir = System.getenv("KPKN_RENDER_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val host = requireNotNull(view)
        val image = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(Canvas(image)) }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun checkViewport(height: Int, scale: Float) {
        var open by mutableStateOf(true)
        var confirmed: CatalogSupersetConfig? = null
        var cancelled = 0
        val selected = listOf("Press de Banca Plano", "Remo con Pecho Apoyado", "Peso Muerto Rumano", "Flexiones de Brazos")
            .mapIndexed { i, name -> ExerciseMuscleInfo("e$i", name) }
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, scale), LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(height.dp).background(Color.Black).testTag("physical-viewport")) {
                        EditorOverlayTestHost {
                            Text("Abrir", Modifier.testTag("reopen"))
                            if (open) CatalogSupersetConfiguratorDialog(selectedExercises = selected,
                                onDismiss = { cancelled++; open = false },
                                onConfirm = { confirmed = it; open = false })
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.onNodeWithText("Configurar superserie").assertIsDisplayed()
        capture("e4_physical_${height}_${(scale * 100).toInt()}_inicio")
        rule.onNodeWithContentDescription("Añadir una ronda").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Rondas: 4").assertExists()
        val create = rule.onNodeWithText("Crear superserie")
        create.performScrollTo().assertIsDisplayed()
        rule.waitForIdle()
        val viewport = rule.onNodeWithTag("physical-viewport").getUnclippedBoundsInRoot()
        val bounds = create.getUnclippedBoundsInRoot()
        assertTrue("Acción completa dentro de la ventana: $bounds / $viewport", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        create.assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Cancelar").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        fullText("Crear superserie"); fullText("Cancelar")
        capture("e4_physical_${height}_${(scale * 100).toInt()}_confirmar")
        create.performClick()
        assertEquals(CatalogSupersetConfig(4, 60, 120), confirmed)
        assertEquals(0, cancelled)
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { open = true }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Cancelar").performScrollTo().performClick()
        assertEquals(1, cancelled)
        assertEquals(CatalogSupersetConfig(4, 60, 120), confirmed)
    }

    @Test fun altura880Letra100() = checkViewport(880, 1f)
    @Test fun altura880Letra130() = checkViewport(880, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun altura740Letra100() = checkViewport(740, 1f)
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun altura740Letra130() = checkViewport(740, 1.3f)
}
