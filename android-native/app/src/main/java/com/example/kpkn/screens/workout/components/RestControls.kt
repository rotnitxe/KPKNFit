package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/*
 * Controles del descanso que comparten la pantalla completa, la tarjeta y el minijuego (LV6): −15 s / +15 s, la acción principal y
 * «Jugar». Cada uno es una píldora de 44 dp o menos dentro de una zona táctil de 48 dp; nada de botones gigantes.
 */

/** «−15 s» y «+15 s»: píldora `inset` de 44 dp con su texto, dentro de una zona táctil de 48 dp. Sin ripple. */
@Composable
internal fun RestStepPill(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.action,
                color = WizardColors.text,
                maxLines = 1,
            )
        }
    }
}

/**
 * La acción principal del descanso («Saltar descanso», «Saltar», «Empezar la serie 2»): píldora de tinta de [height] (44 dp) con texto
 * negro y, si se pide, un icono delante. El texto encoge hasta que cabe en una línea (de 14 a 11 sp), también con la letra al 130 %.
 */
@Composable
internal fun RestSkipPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val autoSize = remember { TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp) }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(KpknShape.pill)
                .background(WizardColors.text, KpknShape.pill)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = WizardColors.ctaContent,
                )
            }
            BasicText(
                text = text,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(
                    fontFamily = WizardFonts.body,
                    fontWeight = FontWeight.SemiBold,
                    color = WizardColors.ctaContent,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                autoSize = autoSize,
            )
        }
    }
}

/**
 * «Jugar mientras descansas» (pantalla completa) y «Jugar» (tarjeta): píldora con borde discontinuo y un mando dibujado a trazo. Mide
 * 40 dp (32 dp en [compact]) dentro de una zona táctil de 48 dp.
 */
@Composable
internal fun RestPlayPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pillHeight = if (compact) 32.dp else 40.dp
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(pillHeight)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = WizardColors.text.copy(alpha = 0.22f),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
                        ),
                    )
                }
                .padding(horizontal = if (compact) 14.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (!compact) RestGamepadIcon(Modifier.size(16.dp))
            Text(
                text = text,
                style = KpknType.chip,
                color = WizardColors.text,
                maxLines = 1,
            )
        }
    }
}

/** El mando del prototipo (F1), a trazo fino: caja redondeada, cruceta y dos botones. */
@Composable
private fun RestGamepadIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val ink = Color(0xFFF2EEE6)
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round)
        drawRoundRect(
            color = ink,
            topLeft = Offset(3f * unit, 7f * unit),
            size = Size(18f * unit, 11f * unit),
            cornerRadius = CornerRadius(4f * unit),
            style = stroke,
        )
        drawLine(ink, Offset(8f * unit, 11f * unit), Offset(8f * unit, 14f * unit), strokeWidth = 1.8f * unit, cap = StrokeCap.Round)
        drawLine(ink, Offset(6.5f * unit, 12.5f * unit), Offset(9.5f * unit, 12.5f * unit), strokeWidth = 1.8f * unit, cap = StrokeCap.Round)
        drawCircle(ink, radius = 1f * unit, center = Offset(16f * unit, 11.5f * unit))
        drawCircle(ink, radius = 1f * unit, center = Offset(17.5f * unit, 14f * unit))
    }
}
