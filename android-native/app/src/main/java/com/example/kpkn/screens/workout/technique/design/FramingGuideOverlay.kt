package com.example.kpkn.screens.workout.technique.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind

/**
 * Capa de guía visual con silueta anatómica y texto de orientación (VT8a).
 * Se dibuja en línea fina blanca al 25 % sobre la vista previa:
 * - Silueta lateral anatómica (cabeza, cuello, tronco con volumen, un brazo y dos piernas diferenciadas);
 * - Silueta frontal limpia y simétrica;
 * - Textos guía de encuadre separados del área inferior del botón REC.
 */
@Composable
fun FramingGuideOverlay(
    preferredView: ViewKind = ViewKind.SIDE,
    hasBarbell: Boolean = false,
    modifier: Modifier = Modifier,
    profileId: AnalysisProfileId? = null,
    expandedScene: Boolean = false,
    showSilhouette: Boolean = true,
    showGuideText: Boolean = true,
) {
    val isFront = preferredView == ViewKind.FRONT

    Column(
        modifier = modifier.fillMaxSize().padding(bottom = if (expandedScene) 0.dp else 128.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSilhouette) Canvas(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(
                top = if (expandedScene) 0.dp else 128.dp,
            ).testTag("technique_silhouette"),
        ) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val tintColor = WizardColors.text.copy(alpha = 0.25f)
            val stroke = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            if (!isFront && profileId == AnalysisProfileId.BENCH) {
                val shoulder = Offset(w * 0.38f, h * 0.48f)
                val hip = Offset(w * 0.63f, h * 0.48f)
                drawCircle(tintColor, h * 0.045f, Offset(w * 0.27f, h * 0.47f), style = stroke)
                drawLine(tintColor, Offset(w * 0.32f, h * 0.48f), hip, stroke.width)
                drawLine(tintColor, hip, Offset(w * 0.76f, h * 0.64f), stroke.width)
                drawLine(tintColor, Offset(w * 0.76f, h * 0.64f), Offset(w * 0.80f, h * 0.82f), stroke.width)
                drawLine(tintColor, shoulder, Offset(w * 0.41f, h * 0.23f), stroke.width)
                if (hasBarbell) drawCircle(tintColor, h * 0.068f, Offset(w * 0.41f, h * 0.23f), style = stroke)
            } else if (isFront) {
                // Silueta de frente limpia y simétrica
                val headCenter = Offset(cx, h * 0.20f)
                val headRadius = h * 0.038f
                drawCircle(color = tintColor, radius = headRadius, center = headCenter, style = stroke)

                // Cuello
                drawLine(tintColor, Offset(cx, headCenter.y + headRadius), Offset(cx, h * 0.26f), stroke.width)

                // Hombros / clavícula
                val shoulderW = w * 0.15f
                val shoulderY = h * 0.265f
                drawLine(tintColor, Offset(cx - shoulderW, shoulderY), Offset(cx + shoulderW, shoulderY), stroke.width)

                // Tronco / columna y caja torácica
                val hipY = h * 0.49f
                val hipW = w * 0.10f
                drawLine(tintColor, Offset(cx, shoulderY), Offset(cx, hipY), stroke.width)
                drawLine(tintColor, Offset(cx - shoulderW * 0.85f, shoulderY + h * 0.02f), Offset(cx - hipW, hipY), stroke.width)
                drawLine(tintColor, Offset(cx + shoulderW * 0.85f, shoulderY + h * 0.02f), Offset(cx + hipW, hipY), stroke.width)

                // Caderas / pelvis
                drawLine(tintColor, Offset(cx - hipW, hipY), Offset(cx + hipW, hipY), stroke.width)

                // Brazos simétricos
                val elbowY = h * 0.38f
                val handY = h * 0.49f
                drawLine(tintColor, Offset(cx - shoulderW, shoulderY), Offset(cx - shoulderW * 1.10f, elbowY), stroke.width)
                drawLine(tintColor, Offset(cx - shoulderW * 1.10f, elbowY), Offset(cx - shoulderW * 1.15f, handY), stroke.width)
                drawLine(tintColor, Offset(cx + shoulderW, shoulderY), Offset(cx + shoulderW * 1.10f, elbowY), stroke.width)
                drawLine(tintColor, Offset(cx + shoulderW * 1.10f, elbowY), Offset(cx + shoulderW * 1.15f, handY), stroke.width)

                // Piernas simétricas
                val kneeY = h * 0.66f
                val footY = h * 0.82f
                // Pierna izquierda
                drawLine(tintColor, Offset(cx - hipW, hipY), Offset(cx - hipW, kneeY), stroke.width)
                drawLine(tintColor, Offset(cx - hipW, kneeY), Offset(cx - hipW * 1.05f, footY), stroke.width)
                drawLine(tintColor, Offset(cx - hipW * 1.25f, footY), Offset(cx - hipW * 0.75f, footY), stroke.width)
                // Pierna derecha
                drawLine(tintColor, Offset(cx + hipW, hipY), Offset(cx + hipW, kneeY), stroke.width)
                drawLine(tintColor, Offset(cx + hipW, kneeY), Offset(cx + hipW * 1.05f, footY), stroke.width)
                drawLine(tintColor, Offset(cx + hipW * 0.75f, footY), Offset(cx + hipW * 1.25f, footY), stroke.width)

                if (hasBarbell && profileId in setOf(AnalysisProfileId.SQUAT, AnalysisProfileId.OHP, AnalysisProfileId.DEADLIFT, AnalysisProfileId.HINGE, AnalysisProfileId.ROW)) {
                    val plateY = if (profileId == AnalysisProfileId.SQUAT || profileId == AnalysisProfileId.OHP) shoulderY else h * 0.72f
                    val barW = w * 0.36f
                    drawLine(tintColor, Offset(cx - barW, plateY), Offset(cx + barW, plateY), stroke.width)
                    drawCircle(tintColor, radius = h * 0.05f, center = Offset(cx - barW, plateY), style = stroke)
                    drawCircle(tintColor, radius = h * 0.05f, center = Offset(cx + barW, plateY), style = stroke)
                }
            } else {
                // Silueta de lado con proporciones anatómicas (cabeza, cuello, tronco, brazo, 2 piernas separadas)
                val headCenter = Offset(cx, h * 0.20f)
                val headRadius = h * 0.038f
                drawCircle(color = tintColor, radius = headRadius, center = headCenter, style = stroke)

                // Perfil / dirección de la mirada hacia la derecha
                drawLine(
                    tintColor,
                    Offset(cx + headRadius * 0.85f, headCenter.y),
                    Offset(cx + headRadius * 1.35f, headCenter.y + headRadius * 0.15f),
                    stroke.width,
                )

                // Cuello
                drawLine(tintColor, Offset(cx, headCenter.y + headRadius), Offset(cx - w * 0.01f, h * 0.26f), stroke.width)

                // Tronco anatómico: columna posterior con ligera curvatura S
                val spinePath = Path().apply {
                    moveTo(cx - w * 0.01f, h * 0.26f)
                    cubicTo(cx - w * 0.03f, h * 0.33f, cx - w * 0.02f, h * 0.42f, cx - w * 0.01f, h * 0.50f)
                }
                drawPath(spinePath, tintColor, style = stroke)

                // Contorno del pecho/abdomen (volumen corporal)
                val chestPath = Path().apply {
                    moveTo(cx + w * 0.01f, h * 0.27f)
                    cubicTo(cx + w * 0.05f, h * 0.33f, cx + w * 0.04f, h * 0.42f, cx + w * 0.02f, h * 0.49f)
                }
                drawPath(chestPath, tintColor, style = stroke)

                // Pelvis
                drawLine(tintColor, Offset(cx - w * 0.01f, h * 0.50f), Offset(cx + w * 0.02f, h * 0.49f), stroke.width)

                // Brazo lateral (hombro -> codo -> mano)
                val handX = cx + w * 0.03f
                val handY = h * 0.49f
                drawLine(tintColor, Offset(cx, h * 0.28f), Offset(cx + w * 0.04f, h * 0.38f), stroke.width)
                drawLine(tintColor, Offset(cx + w * 0.04f, h * 0.38f), Offset(handX, handY), stroke.width)

                // Pierna trasera (ligeramente más atenuada y retrasada)
                val farLegColor = WizardColors.text.copy(alpha = 0.17f)
                val farKneeX = cx - w * 0.01f
                val farKneeY = h * 0.66f
                val farAnkleX = cx - w * 0.03f
                val farAnkleY = h * 0.82f
                drawLine(farLegColor, Offset(cx - w * 0.02f, h * 0.50f), Offset(farKneeX, farKneeY), stroke.width)
                drawLine(farLegColor, Offset(farKneeX, farKneeY), Offset(farAnkleX, farAnkleY), stroke.width)
                drawLine(farLegColor, Offset(farAnkleX - w * 0.02f, farAnkleY), Offset(farAnkleX + w * 0.06f, farAnkleY), stroke.width)

                // Pierna delantera (primer plano, clara)
                val nearKneeX = cx + w * 0.05f
                val nearKneeY = h * 0.66f
                val nearAnkleX = cx + w * 0.03f
                val nearAnkleY = h * 0.82f
                drawLine(tintColor, Offset(cx + w * 0.01f, h * 0.50f), Offset(nearKneeX, nearKneeY), stroke.width)
                drawLine(tintColor, Offset(nearKneeX, nearKneeY), Offset(nearAnkleX, nearAnkleY), stroke.width)
                drawLine(tintColor, Offset(nearAnkleX - w * 0.02f, nearAnkleY), Offset(nearAnkleX + w * 0.07f, nearAnkleY), stroke.width)

                if (hasBarbell && profileId in setOf(AnalysisProfileId.SQUAT, AnalysisProfileId.OHP, AnalysisProfileId.DEADLIFT, AnalysisProfileId.HINGE, AnalysisProfileId.ROW)) {
                    val plateRadius = h * 0.068f
                    val plateCenter = Offset(handX, if (profileId == AnalysisProfileId.SQUAT || profileId == AnalysisProfileId.OHP) h * 0.28f else h * 0.72f)
                    drawCircle(color = tintColor, radius = plateRadius, center = plateCenter, style = stroke)
                    drawLine(tintColor, Offset(plateCenter.x, plateCenter.y - plateRadius * 1.15f), Offset(plateCenter.x, plateCenter.y + plateRadius * 1.15f), stroke.width)
                }
            }
        } else Spacer(Modifier.weight(1f))

        if (showGuideText) {
            // El texto reserva su altura real: ni al 130 % puede rozar los pies.
            Spacer(Modifier.height(12.dp))
            FramingGuideCaption(
                preferredView = preferredView,
                hasBarbell = hasBarbell,
                modifier = Modifier.padding(horizontal = 16.dp),
                backgroundAlpha = if (expandedScene) 0.55f else 0.70f,
            )
        }
    }
}

/** Texto de colocación de cámara; fuera del área flexible para que nunca se recorte. */
@Composable
internal fun FramingGuideCaption(
    preferredView: ViewKind,
    hasBarbell: Boolean,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.55f,
) {
    val isFront = preferredView == ViewKind.FRONT
    Column(
        modifier = modifier
            .testTag("technique_guide_text")
            .background(Color.Black.copy(alpha = backgroundAlpha), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            style = KpknType.caption,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
        )

        if (hasBarbell) {
            Text(
                text = "Móvil a media altura: en el suelo para peso muerto; disco siempre en cuadro",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
