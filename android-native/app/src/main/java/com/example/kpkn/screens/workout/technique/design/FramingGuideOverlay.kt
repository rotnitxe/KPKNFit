package com.example.kpkn.screens.workout.technique.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind

/**
 * Capa de guía visual con silueta anatómica y texto de orientación (VT8a).
 * Se dibuja en tinta blanca al 25 % sobre la vista previa:
 * - Silueta de líneas (vista lateral o frontal según el perfil);
 * - Textos guía de posición recomendada y altura del móvil.
 */
@Composable
fun FramingGuideOverlay(
    preferredView: ViewKind = ViewKind.SIDE,
    hasBarbell: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isFront = preferredView == ViewKind.FRONT

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val tintColor = Color.White.copy(alpha = 0.25f)
            val stroke = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            if (isFront) {
                // Silueta de frente
                val headRadius = h * 0.045f
                val headCenter = Offset(cx, h * 0.22f)
                drawCircle(color = tintColor, radius = headRadius, center = headCenter, style = stroke)

                // Hombros y tronco
                val shoulderY = h * 0.30f
                val shoulderW = w * 0.16f
                drawLine(tintColor, Offset(cx - shoulderW, shoulderY), Offset(cx + shoulderW, shoulderY), stroke.width)

                val hipY = h * 0.52f
                val hipW = w * 0.11f
                // Columna
                drawLine(tintColor, Offset(cx, shoulderY), Offset(cx, hipY), stroke.width)
                // Caderas
                drawLine(tintColor, Offset(cx - hipW, hipY), Offset(cx + hipW, hipY), stroke.width)

                // Pierna izquierda
                val kneeY = h * 0.69f
                val footY = h * 0.85f
                drawLine(tintColor, Offset(cx - hipW, hipY), Offset(cx - hipW, kneeY), stroke.width)
                drawLine(tintColor, Offset(cx - hipW, kneeY), Offset(cx - hipW * 1.1f, footY), stroke.width)
                drawLine(tintColor, Offset(cx - hipW * 1.3f, footY), Offset(cx - hipW * 0.7f, footY), stroke.width)

                // Pierna derecha
                drawLine(tintColor, Offset(cx + hipW, hipY), Offset(cx + hipW, kneeY), stroke.width)
                drawLine(tintColor, Offset(cx + hipW, kneeY), Offset(cx + hipW * 1.1f, footY), stroke.width)
                drawLine(tintColor, Offset(cx + hipW * 0.7f, footY), Offset(cx + hipW * 1.3f, footY), stroke.width)

                // Brazos
                val handY = h * 0.51f
                drawLine(tintColor, Offset(cx - shoulderW, shoulderY), Offset(cx - shoulderW * 1.15f, handY), stroke.width)
                drawLine(tintColor, Offset(cx + shoulderW, shoulderY), Offset(cx + shoulderW * 1.15f, handY), stroke.width)
            } else {
                // Silueta de lado
                val headRadius = h * 0.045f
                val headCenter = Offset(cx, h * 0.22f)
                drawCircle(color = tintColor, radius = headRadius, center = headCenter, style = stroke)

                // Nariz / dirección de la mirada hacia la derecha
                drawLine(
                    tintColor,
                    Offset(cx + headRadius * 0.8f, headCenter.y),
                    Offset(cx + headRadius * 1.35f, headCenter.y + headRadius * 0.2f),
                    stroke.width,
                )

                // Columna vertebral ligeramente curvada
                val path = Path().apply {
                    moveTo(cx, h * 0.27f)
                    quadraticTo(cx - w * 0.02f, h * 0.40f, cx - w * 0.01f, h * 0.52f)
                }
                drawPath(path, tintColor, style = stroke)

                // Pierna de lado (muslo y pierna baja)
                val kneeX = cx + w * 0.04f
                val kneeY = h * 0.69f
                val ankleX = cx
                val ankleY = h * 0.85f
                drawLine(tintColor, Offset(cx - w * 0.01f, h * 0.52f), Offset(kneeX, kneeY), stroke.width)
                drawLine(tintColor, Offset(kneeX, kneeY), Offset(ankleX, ankleY), stroke.width)
                // Pie apuntando adelante
                drawLine(tintColor, Offset(ankleX - w * 0.02f, ankleY), Offset(ankleX + w * 0.08f, ankleY), stroke.width)

                // Brazo sosteniendo barra/peso
                val handX = cx + w * 0.03f
                val handY = h * 0.52f
                drawLine(tintColor, Offset(cx, h * 0.30f), Offset(handX, handY), stroke.width)

                if (hasBarbell) {
                    // Disco en cuadro
                    drawCircle(
                        color = tintColor,
                        radius = h * 0.075f,
                        center = Offset(handX, handY + h * 0.05f),
                        style = stroke,
                    )
                }
            }
        }

        // Textos guía del perfil
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val mainGuide = if (isFront) {
                "De frente · cuerpo entero con margen · cara visible · a 2–4 m"
            } else {
                "De lado · cuerpo entero con margen · cara visible · a 2–4 m"
            }
            Text(
                text = mainGuide,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.50f),
                textAlign = TextAlign.Center,
            )

            if (hasBarbell) {
                Text(
                    text = "Móvil a media altura: en el suelo para peso muerto; disco siempre en cuadro",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.40f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
