package com.example.kpkn.screens.onboarding.design.entreno

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Humo del dibujo: cada símbolo (3 escenas y 16 implementos) se pinta sobre un lienzo real en muchos instantes de su
 * bucle y con la selección a medias, sin lanzar excepciones. No mira píxeles (eso son las capturas del emulador):
 * garantiza que ningún cuadro del bucle revienta con una geometría degenerada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EntrenoSymbolDrawTest {

    /** Un lienzo de Compose sobre un mapa de bits de Android (el `ImageBitmap` de Compose no funciona bajo Robolectric). */
    private fun newCanvas(side: Int): Canvas =
        Canvas(android.graphics.Canvas(Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)))

    private val arts: List<SymbolArt> =
        TrainingPlace.entries.map(::placeArt) + EquipmentSymbolId.entries.map(::equipmentArt)

    @Test
    fun everySymbolDrawsAtEveryInstantAndSelection() {
        val side = 200
        val canvas = newCanvas(side)
        val scope = CanvasDrawScope()
        val pen = SymbolPen()
        var frames = 0
        for (art in arts) {
            for (sel in listOf(0f, 0.4f, 1f)) {
                for (i in 0..30) {
                    val t = art.period * i / 31f
                    scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(side.toFloat(), side.toFloat())) {
                        pen.begin(this, sel, art.accent)
                        val k = side / maxOf(art.width, art.height)
                        withTransform({ scale(k, k, Offset.Zero) }) {
                            art.drawStatic(pen)
                            art.drawDynamic(pen, t)
                            // El fundido de soltar dibuja la pose congelada y la estática con opacidad de grupo.
                            pen.ga = 0.5f
                            art.drawDynamic(pen, art.restT)
                            pen.ga = 1f
                        }
                        pen.doneBadge(this, 170f, 30f, 16f, sel)
                    }
                    frames++
                }
            }
        }
        assertTrue(frames == arts.size * 3 * 31)
    }

    @Test
    fun theDoneBadgeDrawsItsCheckmarkProgressively() {
        val canvas = newCanvas(64)
        val scope = CanvasDrawScope()
        val pen = SymbolPen()
        for (p in listOf(0f, 0.001f, 0.2f, 0.5f, 0.75f, 1f, 1.2f)) {
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(64f, 64f)) {
                pen.begin(this, 1f, SymbolPalette.ok)
                pen.doneBadge(this, 32f, 32f, 12f, p)
            }
        }
    }
}
