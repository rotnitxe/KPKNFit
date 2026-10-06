package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Los cuatro símbolos de género del wizard. */
enum class WizardGenderMark { FEMALE, MALE, TRANS_MALE, TRANS_FEMALE }

/**
 * Glifo de género dibujado en un lienzo de 100×100 y escalado al lado que reciba. Los cuatro comparten caja,
 * grosor de trazo (7 unidades, puntas redondas) y la misma altura de tinta (de 11 a 89), centrada en el lienzo:
 * por eso miden lo mismo y ninguno queda descentrado, cosa que no ocurría con los símbolos ♀/♂ de la fuente.
 * Es decorativo: la etiqueta de la opción ya dice qué es.
 */
@Composable
fun WizardGenderGlyph(mark: WizardGenderMark, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val u = size.minDimension / 100f
        val strokeWidth = 7f * u
        fun point(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        fun ring(cx: Float, cy: Float, radius: Float) = drawCircle(
            color = color,
            radius = radius * u,
            center = point(cx, cy),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        when (mark) {
            WizardGenderMark.FEMALE -> {
                ring(50f, 37f, 26f)
                line(50f, 63f, 50f, 89f)
                line(34f, 76f, 66f, 76f)
            }
            WizardGenderMark.MALE -> {
                ring(37f, 63f, 26f)
                line(55.4f, 44.6f, 89f, 11f)
                line(65f, 11f, 89f, 11f)
                line(89f, 11f, 89f, 35f)
            }
            // Símbolo trans (⚧): círculo, dos flechas hacia arriba y cruz abajo. Es el mismo para las dos opciones
            // trans; la etiqueta y el color del resplandor dicen cuál es cuál.
            WizardGenderMark.TRANS_MALE, WizardGenderMark.TRANS_FEMALE -> {
                ring(50f, 45f, 20f)
                line(64.1f, 30.9f, 84f, 11f)
                line(64f, 11f, 84f, 11f)
                line(84f, 11f, 84f, 31f)
                line(35.9f, 30.9f, 16f, 11f)
                line(36f, 11f, 16f, 11f)
                line(16f, 11f, 16f, 31f)
                line(50f, 65f, 50f, 89f)
                line(37f, 77f, 63f, 77f)
            }
        }
    }
}
