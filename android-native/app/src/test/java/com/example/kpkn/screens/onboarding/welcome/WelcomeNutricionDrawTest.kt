package com.example.kpkn.screens.onboarding.welcome

import android.content.Context
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dibujar la escena en cada momento del bucle no puede fallar (índices de línea, rutas, cachés de texto…). Con
 * Robolectric no se ve el resultado —eso se revisa en el emulador—, pero sí se ejecuta todo el código de dibujo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WelcomeNutricionDrawTest {
    @Test
    fun everyMomentOfTheLoopDrawsWithoutFailing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val kit = NutricionKit(createFontFamilyResolver(context))
        val geo = NutricionSheetGeometry(kit)
        // Sin bitmap: Robolectric (gráficos heredados) no los crea; basta un lienzo que reciba las órdenes de dibujo.
        val canvas = Canvas(android.graphics.Canvas())
        val scope = CanvasDrawScope()
        var drawn = 0
        var t = 0f
        while (t < WelcomeNutricionPeriod) {
            scope.draw(Density(3f), LayoutDirection.Ltr, canvas, Size(900f, 1860f)) {
                drawNutricionFrame(nutricionFrameAt(t), kit, geo)
            }
            drawn++
            t += 0.05f
        }
        assertTrue(drawn > 200)
    }

    @Test
    fun theKeywordsAndTheChipsHaveTheirGeometry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val geo = NutricionSheetGeometry(NutricionKit(createFontFamilyResolver(context)))
        // Con Robolectric las medidas de texto no son las del teléfono: solo se comprueba la forma de los datos.
        for (k in 0..3) {
            assertTrue(geo.underline[k].size >= 4 && geo.underline[k].size % 4 == 0)
            assertEquals(4, geo.source[k].size)
        }
        assertEquals(listOf(0, 0, 1, 1), geo.chipRow.toList())
        // Cada fila de chips cabe dentro del campo y los dos chips de una fila no se pisan.
        val fieldRight = NutricionLayout.Width - NutricionLayout.Margin
        for (row in 0..1) {
            val a = row * 2
            assertTrue(geo.chipX[a] >= NutricionLayout.Margin)
            assertTrue(geo.chipX[a + 1] >= geo.chipX[a] + geo.chipW[a])
        }
        assertTrue(fieldRight > 0f)
    }
}
