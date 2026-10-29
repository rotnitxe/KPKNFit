package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.NutrientGroup
import com.example.kpkn.domain.nutrition.NutrientKey
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Nombre de la fila (`.nrow strong`): Inter Medium 14. */
private val RowNameStyle = KpknType.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
/** Cantidad (`.nrow .v`): 13, gris, números tabulares. */
private val RowValueStyle = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1")

/** Cabecera de grupo (`.mgroup`): eyebrow pequeño en mayúsculas y tinta tenue. */
@Composable
internal fun NutrientGroupLabel(text: String, modifier: Modifier = Modifier, color: Color = WizardColors.textFaint) {
    Text(text.uppercase(), style = KpknType.eyebrow.copy(letterSpacing = 1.3.sp), color = color,
        modifier = modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 16.dp, bottom = 4.dp))
}

/**
 * Una fila de lista (`nrow`): nombre, cantidad, % y barra fina hasta el 100 % (la marca del final). «Sin dato» sin barra, con un filete
 * discontinuo; sin referencia, solo el total y «—»; con tope, «N % del máx.» y la barra en contorno (rojo si se pasa); la parte estimada
 * rayada; lo que pasa del 100 % llena la barra y lleva una flecha. [onClick] abre la hoja del nutriente.
 */
@Composable
fun NutrientRow(row: NutrientRowModel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("nutrient-${row.key.name}")
            .drawBehind { drawLine(KpknSurface.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Ver ${row.name}", onClick = onClick) else Modifier)
            .padding(top = 10.dp, bottom = 11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.name, style = RowNameStyle, color = WizardColors.text, modifier = Modifier.weight(1f).alignByBaseline())
            Text(row.valueText, style = if (row.missing) RowValueStyle.copy(fontStyle = FontStyle.Italic) else RowValueStyle,
                color = if (row.missing) NutrientDim else WizardColors.textMuted, maxLines = 1, modifier = Modifier.alignByBaseline())
            if (!row.missing) {
                val tone = when {
                    row.noReference -> NutrientDim
                    row.ceiling && row.over -> WizardColors.danger
                    else -> WizardColors.text
                }
                Row(Modifier.widthIn(min = 44.dp).alignByBaseline(), horizontalArrangement = Arrangement.End) {
                    Text(row.percentText.orEmpty(), style = NutrientFigureStyle, color = tone, textAlign = TextAlign.End, modifier = Modifier.alignByBaseline())
                    if (row.ceiling && !row.noReference) {
                        Text(" del máx.", style = KpknType.caption.copy(fontWeight = FontWeight.Medium), color = WizardColors.textFaint, modifier = Modifier.alignByBaseline())
                    }
                }
            }
        }
        when {
            row.missing -> Canvas(Modifier.fillMaxWidth().padding(top = 9.dp).height(1.5.dp)) {
                drawLine(Color.White.copy(alpha = 0.13f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            !row.noReference -> NutrientBar(row.fill, row.estimatedShare, ceiling = row.ceiling, overCeiling = row.ceiling && row.over,
                arrow = row.over && !row.ceiling, modifier = Modifier.padding(top = 7.dp))
        }
        row.note?.let { Text(it, style = KpknType.caption, color = WizardColors.textFaint, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/**
 * La barra fina (`.nbar`): pista de blanco al 5 %, relleno de tinta hasta [fill], la parte estimada rayada, la marca del 100 % al final
 * y, si se pasa, una flecha. Con [ceiling] el relleno es un contorno (rojo si [overCeiling]).
 */
@Composable
internal fun NutrientBar(
    fill: Float,
    estimatedShare: Float,
    modifier: Modifier = Modifier,
    ceiling: Boolean = false,
    overCeiling: Boolean = false,
    arrow: Boolean = false,
    height: Dp = 5.dp,
    track: Boolean = true,
    fillAlpha: Float = 1f,
    hatchedFill: Boolean = false,
    mark: Boolean = true,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(3.dp.toPx())
        if (track) drawRoundRect(Color.White.copy(alpha = 0.055f), cornerRadius = radius)
        val p = fill.coerceIn(0f, 1f)
        val solid = p * (1f - estimatedShare.coerceIn(0f, 1f))
        val est = p - solid
        when {
            ceiling -> if (p > 0f) {
                val stroke = 1.dp.toPx()
                drawRoundRect(if (overCeiling) WizardColors.danger else WizardColors.text.copy(alpha = 0.69f), topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size((size.width * p - stroke).coerceAtLeast(0f), size.height - stroke), cornerRadius = radius, style = Stroke(stroke))
            }
            hatchedFill -> hatch(0f, size.width * p)
            else -> {
                if (solid > 0f) drawRoundRect(WizardColors.text.copy(alpha = fillAlpha), size = Size(size.width * solid, size.height), cornerRadius = radius)
                if (est > 0f) hatch(size.width * solid, size.width * est)
            }
        }
        if (mark) drawRect(Color.White.copy(alpha = 0.27f), topLeft = Offset(size.width - 1.dp.toPx(), -3.dp.toPx()), size = Size(2.dp.toPx(), size.height + 6.dp.toPx()))
        if (arrow) {
            val cy = size.height / 2
            val left = size.width + 4.dp.toPx()
            drawPath(Path().apply {
                moveTo(left, cy - 4.dp.toPx()); lineTo(left + 5.dp.toPx(), cy); lineTo(left, cy + 4.dp.toPx()); close()
            }, WizardColors.text)
        }
    }
}

/** Rayado de la parte estimada (`repeating-linear-gradient(135deg…)`): trazos de 2 dp cada 5 dp, en tinta al 69 %. */
private fun DrawScope.hatch(start: Float, width: Float) {
    if (width <= 0f) return
    clipRect(left = start, top = 0f, right = start + width, bottom = size.height) {
        val step = 5.dp.toPx() * 1.4142f
        var x = start - size.height
        while (x < start + width) {
            drawLine(WizardColors.text.copy(alpha = 0.69f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
            x += step
        }
    }
}

/** Chips de grupo en una sola fila que se desliza en horizontal: Vitaminas · Minerales · Grasas · Carbos · Otros. */
@Composable
fun NutrientGroupChips(selected: NutrientGroup, onSelect: (NutrientGroup) -> Unit, modifier: Modifier = Modifier, sidePadding: Dp = 18.dp) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = sidePadding).testTag("nutrient-group-chips"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NutrientGroup.entries.forEach { group ->
            KpknChip(text = group.label, selected = group == selected, onClick = { onSelect(group) },
                modifier = Modifier.testTag("nutrient-group-${group.name}"), height = 32.dp)
        }
    }
}

/** Una cifra de `.kpi`: número en Syne ExtraBold 18 y su etiqueta debajo. */
@Composable
internal fun NutrientKpi(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = WizardColors.text) {
    Column(modifier.background(KpknSurface.inset, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value, style = NutrientKpiStyle, color = valueColor, maxLines = 1)
        Text(label, style = KpknType.caption, color = WizardColors.textFaint, maxLines = 2)
    }
}

private val NutrientKpiStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.sp,
    fontFeatureSettings = "'lnum' 1, 'tnum' 1")

/** Filas de un grupo, con la hoja del nutriente al tocarlas si hay [onOpen]. */
@Composable
internal fun NutrientRowsColumn(rows: List<NutrientRowModel>, onOpen: ((NutrientKey) -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        rows.forEach { row -> NutrientRow(row, onClick = onOpen?.let { open -> { open(row.key) } }) }
        Spacer(Modifier.height(1.dp))
    }
}
