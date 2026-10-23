package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

/** Alto de la isla inferior del editor (ED-A · A1: 64 dp, como la de Main). */
internal val SessionEditorFrameBottomBarHeight = 64.dp

/**
 * Barra inferior fija del editor (ED-A · A1): una isla de vidrio con el tiempo estimado, «Añadir», «Asistente» y «Reglas». El tiempo es un
 * anillo con los minutos dentro, de 48 dp: tocarlo abre la hoja de reglas en la pestaña de tiempo. «Añadir» abre el catálogo a página
 * completa y es la acción principal (píldora de tinta); «Asistente» abre la hoja del asistente y lleva su número si hay sugerencias;
 * «Reglas» abre la hoja en la pestaña de reglas. Todo mide 48 dp de alto como mínimo.
 *
 * @param estimatedMinutes minutos de la sesión; nulo si no hay ejercicios (el anillo queda neutro y marca 0).
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
    onTime: () -> Unit,
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
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FrameTimeRing(minutes = estimatedMinutes ?: 0, limitMinutes = limitMinutes, hasEstimate = estimatedMinutes != null, tone = tone, fraction = fraction, onClick = onTime)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("session_editor_bar_add")
                .clip(KpknShape.pill)
                .background(WizardColors.text, KpknShape.pill)
                .clickable(role = Role.Button, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = KpknLineIcons.Plus,
                    contentDescription = null,
                    tint = WizardColors.ctaContent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Añadir",
                    style = KpknType.action,
                    color = WizardColors.ctaContent,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        FrameBarTextAction(label = "Asistente", onClick = onAssistant, badge = suggestionCount, tag = "session_editor_bar_assistant")
        FrameBarTextAction(label = "Reglas", onClick = onRules, tag = "session_editor_bar_rules")
    }
}

/** El anillo del tiempo: lo estimado frente al límite, con los minutos dentro. Zona táctil de 48 dp. */
@Composable
private fun FrameTimeRing(
    minutes: Int,
    limitMinutes: Int?,
    hasEstimate: Boolean,
    tone: FrameRingTone,
    fraction: Float,
    onClick: () -> Unit,
) {
    val toneColor = when (tone) {
        FrameRingTone.OK -> WizardColors.done
        FrameRingTone.ENERGIA -> KpknRingColors.energia
        FrameRingTone.DANGER -> WizardColors.danger
        FrameRingTone.NEUTRAL -> WizardColors.textMuted
    }
    val description = when {
        !hasEstimate -> "Tiempo estimado: sin ejercicios"
        limitMinutes != null && limitMinutes > 0 -> "Tiempo estimado $minutes de $limitMinutes minutos"
        else -> "Tiempo estimado $minutes minutos"
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag("session_editor_bar_time")
            .semantics { contentDescription = description }
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val stroke = 3.dp.toPx()
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
            if (tone != FrameRingTone.NEUTRAL && fraction > 0f) {
                drawArc(
                    color = toneColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "$minutes",
            style = KpknType.caption,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Un botón de texto de la isla («Asistente», «Reglas»), con su número de sugerencias si lo hay. Zona táctil de 48 dp de alto. */
@Composable
private fun FrameBarTextAction(
    label: String,
    onClick: () -> Unit,
    tag: String,
    badge: Int = 0,
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .testTag(tag)
            .clip(KpknShape.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = KpknType.action,
            color = WizardColors.text,
            maxLines = 1,
            softWrap = false,
        )
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(SymbolPalette.musculo)
                    .semantics { contentDescription = "$badge sugerencias" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WizardColors.ctaContent,
                )
            }
        }
    }
}
