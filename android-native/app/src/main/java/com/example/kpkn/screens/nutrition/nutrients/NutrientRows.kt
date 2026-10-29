package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.NutrientKey
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetGroup
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.domain.nutrition.NutrientGroup

/** Nombre de la fila (`.np-nrow strong`): Inter Medium 14. */
private val RowNameStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
/** Contorno de la pista de un tope (`#ffffff26`) y rayas de un dato incompleto. */
private val HollowColor = Color.White.copy(alpha = 0x26 / 255f)

/**
 * Una fila de la lista (`np-nrow`): nombre, cantidad —con «· máx.» en los topes— y % en columnas; debajo, una barra de 3 dp en tinta sobre su
 * pista. Un tope tiene la pista hueca; un dato incompleto o sin dato, discontinua; solo lo que pasa de un máximo va en rojo. Sin referencia,
 * el total y «—», sin barra. [onClick] abre la hoja del nutriente.
 */
@Composable
fun NutrientRow(row: NutrientRowModel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val hair = LoggerColors.hair
    Column(
        modifier
            .fillMaxWidth()
            .testTag("nutrient-${row.key.name}")
            .drawBehind { drawLine(hair, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Ver ${row.name}", onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.name, style = RowNameStyle, color = LoggerColors.ink, modifier = Modifier.weight(1f).alignByBaseline())
            Text(row.valueText + if (row.ceiling && !row.missing) " · máx." else "", style = LoggerText.cap.copy(fontFeatureSettings = "'tnum' 1"),
                color = LoggerColors.faint, maxLines = 1, modifier = Modifier.alignByBaseline())
            if (!row.missing) {
                Row(Modifier.widthIn(min = 46.dp).alignByBaseline(), horizontalArrangement = Arrangement.End) {
                    Text(row.percentText.orEmpty(), style = LoggerText.number(14f), maxLines = 1, modifier = Modifier.alignByBaseline(),
                        color = if (row.ceiling && row.over) LoggerColors.danger else if (row.noReference) LoggerColors.faint else LoggerColors.ink)
                }
            }
        }
        if (!row.noReference) {
            NutrientBar(if (row.missing) 0f else row.fill, Modifier.padding(top = 8.dp), ceiling = row.ceiling, over = row.ceiling && row.over,
                partial = row.partial || row.missing)
        }
    }
}

/**
 * La barra fina (`.np-nbar`): pista de blanco al 8 % y relleno de tinta hasta [fill]. Con [ceiling] la pista es un contorno de 1 dp; con
 * [partial] va discontinua; con [over] (solo un máximo pasado) el relleno es rojo.
 */
@Composable
internal fun NutrientBar(
    fill: Float,
    modifier: Modifier = Modifier,
    ceiling: Boolean = false,
    over: Boolean = false,
    partial: Boolean = false,
    height: Dp = 3.dp,
    track: Boolean = true,
    fillAlpha: Float = 1f,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(2.dp.toPx())
        if (track) {
            when {
                ceiling -> {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(HollowColor, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = radius, style = Stroke(stroke))
                }
                partial -> drawLine(HollowColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                else -> drawRoundRect(LoggerColors.track, cornerRadius = radius)
            }
        }
        val p = fill.coerceIn(0f, 1f)
        if (p > 0f) {
            drawRoundRect((if (over) LoggerColors.danger else LoggerColors.ink).copy(alpha = fillAlpha),
                size = Size(size.width * p, size.height), cornerRadius = radius)
        }
    }
}

/** Filas de un grupo, con la hoja del nutriente al tocarlas si hay [onOpen]. */
@Composable
internal fun NutrientRowsColumn(rows: List<NutrientRowModel>, onOpen: ((NutrientKey) -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        rows.forEach { row -> NutrientRow(row, onClick = onOpen?.let { open -> { open(row.key) } }) }
    }
}

/** La lista completa (`npList`): un grupo tras otro —Vitaminas, Minerales y el resto que guarda la app— con su etiqueta y sus filas. */
@Composable
internal fun NutrientGroupedRows(list: NutrientListModel, onOpen: ((NutrientKey) -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        NutrientGroup.entries.forEach { group ->
            val rows = list.orderedRowsOf(group)
            if (rows.isNotEmpty()) {
                LoggerSheetGroup(group.label)
                NutrientRowsColumn(rows, onOpen)
            }
        }
    }
}
