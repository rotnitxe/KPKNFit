package com.example.kpkn.screens.home.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/** Glifos de Home: la (i) de ayuda y la cruz de cierre, dibujados en la tinta de la marca. */
internal enum class HomeGlyph { INFO, CLOSE }

/**
 * Botón de icono de 48 dp (`Role.Button`) con un glifo dibujado en la tinta, sin el icono de Material. [description] es lo que
 * lee TalkBack.
 */
@Composable
internal fun HomeGlyphButton(
    glyph: HomeGlyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.6.dp.toPx()
            val ink = WizardColors.text
            when (glyph) {
                HomeGlyph.INFO -> {
                    // Círculo, punto y barra de la «i».
                    drawCircle(
                        color = ink,
                        radius = size.minDimension / 2f - stroke / 2f,
                        style = Stroke(width = stroke),
                    )
                    drawCircle(color = ink, radius = 1.3.dp.toPx(), center = Offset(size.width / 2f, size.height * 0.30f))
                    drawLine(
                        color = ink,
                        start = Offset(size.width / 2f, size.height * 0.46f),
                        end = Offset(size.width / 2f, size.height * 0.76f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
                HomeGlyph.CLOSE -> {
                    drawLine(
                        color = ink,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = ink,
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
