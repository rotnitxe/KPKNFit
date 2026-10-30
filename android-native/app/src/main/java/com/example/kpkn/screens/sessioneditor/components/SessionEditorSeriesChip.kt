package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts

private val KpknInk = Color(0xFFF2EEE6)
/** `.ed-sc`: el fondo de la ficha es #0D0D0D (más hondo que el módulo #111). */
private val KpknInset = Color(0xFF0D0D0D)

/**
 * Medidas de una ficha de la preparación (H1/H2, R5): exactamente la misma ficha que las series efectivas del tablero
 * —85 × 76 dp, radio 14, separación 6—. ED-B y ED-C usan la misma pieza, sin dimensiones paralelas.
 */
internal object SessionEditorChipMetrics {
    val width = 85.dp
    val height = 76.dp
    val radius = 14.dp
    val gap = 6.dp

    /** Objetivo táctil mínimo de la ley de H1/H2. */
    val minTouchTarget = 48.dp
}

/**
 * La ficha común: 85 × 76 dp. Es la misma pieza para aproximaciones, movilidad y series efectivas, para que el tamaño
 * no pueda divergir entre apartados (decisión R5).
 */
@Composable
internal fun EditorPreparationChip(
    label: String,
    value: String,
    detail: String,
    valueColor: Color,
    selected: Boolean,
    tag: String,
    description: String? = null,
    enabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SessionEditorChipMetrics.radius)
    // pulido:ed-T23 .ed-sc borde y fondo 0,18 s + escala con rebote (0,18 s) · ed-A10 .ed-sc.flash 0,6 s tras «Aplicar a todas»
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val available = with(density) { (SessionEditorChipMetrics.width - 16.dp).toPx() }
    val valueSize = remember(value, density.density, density.fontScale) {
        (34 downTo 22).map { it / 2f }.firstOrNull { size ->
            measurer.measure(value, androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display,
                fontWeight = FontWeight.Bold, fontSize = size.sp)).size.width <= available
        } ?: 11f
    }
    Column(
        modifier = Modifier
            .testTag(tag)
            .semantics { description?.let { contentDescription = it } }
            .size(SessionEditorChipMetrics.width, SessionEditorChipMetrics.height)
            .clip(shape)
            .background(if (selected) Color(0xFF232323) else KpknInset)
            .border(
                width = 1.dp,
                color = if (selected) KpknInk else Color.White.copy(alpha = 0.102f),
                shape = shape,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        if (content != null) {
            content.invoke(this)
        } else {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = valueColor,
                fontFamily = WizardFonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = valueSize.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = KpknInk,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Ficha de serie efectiva del tablero (85 × 76 dp): la referencia de tamaño de H1/H2, lista para ED-B. */
@Composable
internal fun EffectiveSeriesChip(
    label: String,
    load: String,
    dose: String,
    selected: Boolean = false,
    tag: String = "effective-series-chip-$label",
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    EditorPreparationChip(
        label = label,
        value = load,
        detail = dose,
        valueColor = KpknInk,
        selected = selected,
        tag = tag,
        description = description,
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * `.ed-sc.add`: ficha punteada de la misma altura (76 dp) que las demás. Sin [label] es «+ Añadir serie» (56 dp); con él, la ficha
 * de un lado que falta en un ejercicio unilateral («R3», 64 dp) con el nombre encima del «+».
 */
@Composable
internal fun EditorAddChip(
    label: String?,
    width: androidx.compose.ui.unit.Dp,
    plusSize: androidx.compose.ui.unit.Dp,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SessionEditorChipMetrics.radius)
    Column(
        modifier = Modifier
            .testTag(tag)
            .semantics { contentDescription = description }
            .size(width, SessionEditorChipMetrics.height)
            .clip(shape)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .edDashedBorder(Color.White.copy(alpha = 0.2f), SessionEditorChipMetrics.radius, 1.dp)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (label != null) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                maxLines = 1,
            )
        }
        androidx.compose.material3.Icon(
            imageVector = com.example.kpkn.ui.components.icons.KpknLineIcons.Plus,
            contentDescription = null,
            tint = Color(0xFFC4BFB6),
            modifier = Modifier.size(plusSize),
        )
    }
}
