package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.sessionMinutesLabel
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

/** Alto de la isla inferior del editor (ER1a §2.3). */
internal val SessionEditorFrameBottomBarHeight = 76.dp

/**
 * Barra inferior fija del editor (ER1a §2.3): una isla de vidrio v2 con tres botones iguales, icono y rótulo, de al menos 48 dp.
 * «Añadir» abre el catálogo a página completa; «Asistente» abre la hoja del asistente (con un punto y su número si hay sugerencias);
 * «Reglas» abre «Reglas y tiempo» en la pestaña de tiempo, con un anillo fino de lo estimado frente al límite y, debajo, los minutos.
 *
 * @param estimatedMinutes minutos de la sesión; nulo si no hay ejercicios (el anillo queda neutro y no se nombran).
 * @param limitMinutes el límite de la sesión; nulo si no hay (el anillo queda neutro).
 */
@Composable
internal fun SessionEditorFrameBottomBar(
    hazeState: HazeState,
    suggestionCount: Int,
    estimatedMinutes: Int?,
    limitMinutes: Int?,
    onAdd: () -> Unit,
    onAssistant: () -> Unit,
    onRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = if (estimatedMinutes == null) {
        FrameRingTone.NEUTRAL
    } else {
        frameTimeRingTone(estimatedMinutes, limitMinutes)
    }
    val fraction = frameTimeRingFraction(estimatedMinutes ?: 0, limitMinutes)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SessionEditorFrameBottomBarHeight)
            .kpknGlass(hazeState = hazeState, shape = KpknShape.island, variant = KpknGlassVariant.ISLAND)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FrameBarAction(
            label = "Añadir",
            icon = KpknLineIcons.Plus,
            onClick = onAdd,
            modifier = Modifier.weight(1f),
        )
        FrameBarAction(
            label = "Asistente",
            icon = KpknLineIcons.Tower,
            onClick = onAssistant,
            modifier = Modifier.weight(1f),
            badge = suggestionCount,
        )
        FrameBarAction(
            label = "Reglas",
            icon = KpknLineIcons.Clock,
            onClick = onRules,
            modifier = Modifier.weight(1f),
            ring = tone,
            ringFraction = fraction,
            minutes = estimatedMinutes?.takeIf { it > 0 },
        )
    }
}

/** Un botón de la isla: icono (con anillo o punto si procede), rótulo y, en «Reglas», los minutos. Zona táctil de todo el hueco. */
@Composable
private fun FrameBarAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    ring: FrameRingTone? = null,
    ringFraction: Float = 0f,
    minutes: Int? = null,
) {
    val toneColor = when (ring) {
        FrameRingTone.OK -> WizardColors.done
        FrameRingTone.ENERGIA -> KpknRingColors.energia
        FrameRingTone.DANGER -> WizardColors.danger
        else -> WizardColors.textMuted
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (ring != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arc = Size(diameter, diameter)
                    drawArc(
                        color = Color.White.copy(alpha = 0.14f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (ring != FrameRingTone.NEUTRAL && ringFraction > 0f) {
                        drawArc(
                            color = toneColor,
                            startAngle = -90f,
                            sweepAngle = 360f * ringFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arc,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WizardColors.text,
                modifier = Modifier.size(18.dp),
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(WizardColors.text),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WizardColors.ctaContent,
                    )
                }
            }
        }
        Text(
            text = label,
            style = KpknType.caption,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        // Una línea reservada en los tres botones: los rótulos quedan a la misma altura. Solo «Reglas» la llena.
        val minutesStyle = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp)
        if (minutes != null) {
            FrameMinutesLabel(minutes = minutes, color = toneColor, style = minutesStyle)
        } else {
            Text(text = "\u00A0", style = minutesStyle, maxLines = 1)
        }
    }
}

/**
 * Los minutos del botón «Reglas»: «60 min» con espacio inseparable, en una sola línea. Si no caben en el ancho del botón (letra grande),
 * la cifra sola y «min» más pequeño a su lado, para que nunca se corten (ER2b).
 */
@Composable
private fun FrameMinutesLabel(minutes: Int, color: Color, style: TextStyle) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val full = sessionMinutesLabel(minutes)
        val fits = measurer.measure(text = full, style = style, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth
        if (fits) {
            Text(text = full, style = style, color = color, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$minutes", style = style, color = color, maxLines = 1, softWrap = false)
                Text(
                    text = "\u00A0min",
                    style = style.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
