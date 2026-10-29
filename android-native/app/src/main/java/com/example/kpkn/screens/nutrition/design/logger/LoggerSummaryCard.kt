package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** El reparto de las calorías entre proteína, carbos y grasa (4, 4 y 9 kcal por gramo), en tantos por uno; ceros si no hay nada. */
fun loggerMacroShares(protein: Double, carbs: Double, fats: Double): Triple<Double, Double, Double> {
    val p = protein.coerceAtLeast(0.0) * 4
    val c = carbs.coerceAtLeast(0.0) * 4
    val f = fats.coerceAtLeast(0.0) * 9
    val total = p + c + f
    return if (total <= 0.0) Triple(0.0, 0.0, 0.0) else Triple(p / total, c / total, f / total)
}

/**
 * Los anchos de los tramos de una barra apilada (`.np-bar i{flex:N;min-width:4px}`): [weights] reparten [available] (ya sin los huecos);
 * un tramo con peso que saldría más estrecho que [minWidth] se queda en [minWidth] y los demás se reparten el resto. Los pesos de 0 o
 * menos no dibujan nada (ancho 0).
 */
internal fun loggerBarWidths(weights: List<Float>, available: Float, minWidth: Float): List<Float> {
    val live = weights.map { weight -> if (weight.isFinite() && weight > 0f) weight else 0f }
    if (available <= 0f || live.sum() <= 0f) return live.map { 0f }
    val pinned = BooleanArray(live.size)
    var changed = true
    while (changed) {
        changed = false
        val freeWeight = live.indices.filter { live[it] > 0f && !pinned[it] }.sumOf { live[it].toDouble() }.toFloat()
        if (freeWeight <= 0f) break
        val free = max(0f, available - pinned.count { it } * minWidth)
        for (index in live.indices) {
            if (live[index] > 0f && !pinned[index] && free * live[index] / freeWeight < minWidth) {
                pinned[index] = true
                changed = true
            }
        }
    }
    val freeWeight = live.indices.filter { live[it] > 0f && !pinned[it] }.sumOf { live[it].toDouble() }.toFloat()
    val free = max(0f, available - pinned.count { it } * minWidth)
    return live.indices.map { index ->
        when {
            live[index] <= 0f -> 0f
            pinned[index] -> minWidth
            else -> free * live[index] / freeWeight
        }
    }
}

/**
 * Texto de una línea que nunca se corta: si no cabe a [style].fontSize, se encoge hasta [minSize] (autoajuste); jamás «…». Lo comparten
 * el resumen, las columnas de macros, la tarjeta del alimento y la barra de guardar.
 */
@Composable
internal fun LoggerFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minSize: TextUnit = 12.sp,
    textAlign: TextAlign? = null,
) {
    val top = style.fontSize
    val autoSize = remember(top, minSize) {
        TextAutoSize.StepBased(minFontSize = if (minSize.value < top.value) minSize else top, maxFontSize = top, stepSize = 0.5.sp)
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: style.textAlign),
        maxLines = 1,
        softWrap = false,
        autoSize = autoSize,
    )
}

/**
 * Resumen de la comida (lienzo `npSum`): tarjeta plana (superficie 1, radio 24, 16 × 18) con el total en Syne 30 y «kcal» a su lado,
 * [caption] a la derecha, la barra apilada de 8 dp con el reparto de las calorías y las tres columnas de macros. Sin anillo.
 * [targetKcal] ya no se usa (el anillo era lo único que lo leía); se conserva para no romper a quien llama.
 */
@Composable
fun LoggerSummaryCard(
    kcal: Int,
    protein: Double,
    carbs: Double,
    fats: Double,
    modifier: Modifier = Modifier,
    targetKcal: Int? = null,
    caption: String = "esta comida",
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LoggerColors.surface1, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = loggerThousands(kcal),
                style = LoggerText.total,
                color = LoggerColors.ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = "kcal",
                style = LoggerText.cap.copy(fontWeight = FontWeight.Medium),
                color = LoggerColors.faint,
                modifier = Modifier.padding(start = 6.dp).alignBy(androidx.compose.ui.layout.LastBaseline),
            )
            Box(Modifier.weight(1f))
            Text(
                text = caption,
                style = LoggerText.cap,
                color = LoggerColors.faint,
                modifier = Modifier.padding(start = 10.dp).alignBy(androidx.compose.ui.layout.LastBaseline),
            )
        }
        LoggerMacroSplitBar(
            protein = protein,
            carbs = carbs,
            fats = fats,
            height = 8.dp,
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
        )
        LoggerMacroColumns(protein = protein, carbs = carbs, fats = fats)
    }
}

/**
 * `.np-bar`: la barra apilada de [height] con proteína, carbos y grasa proporcionales a sus kcal (P·4, C·4, G·9) en los tres tonos de
 * tinta, 3 dp entre tramos y radio 3 dp; cada tramo mide 4 dp como mínimo. Sin nada que repartir, solo la pista.
 */
@Composable
fun LoggerMacroSplitBar(
    protein: Double,
    carbs: Double,
    fats: Double,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val weights = listOf(
        (protein * 4).coerceAtLeast(0.0).toFloat(),
        (carbs * 4).coerceAtLeast(0.0).toFloat(),
        (fats * 9).coerceAtLeast(0.0).toFloat(),
    )
    val colors = listOf(LoggerColors.macroP, LoggerColors.macroC, LoggerColors.macroF)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val gap = 3.dp.toPx()
        val minWidth = 4.dp.toPx()
        val radius = min(3.dp.toPx(), size.height / 2f)
        val live = weights.count { it > 0f }
        if (live == 0) {
            drawRoundRect(LoggerColors.track, size = Size(size.width, size.height), cornerRadius = CornerRadius(radius))
            return@Canvas
        }
        val widths = loggerBarWidths(weights, size.width - gap * (live - 1), minWidth)
        var x = 0f
        widths.forEachIndexed { index, width ->
            if (width <= 0f) return@forEachIndexed
            drawRoundRect(colors[index], topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(radius))
            x += width + gap
        }
    }
}

/**
 * `.np-mac`: tres columnas iguales con los gramos («3 g», Inter SemiBold 16, se encoge si no cabe) y debajo el cuadradito de 8 dp del tono
 * y el nombre: Proteína, Carbos, Grasa.
 */
@Composable
fun LoggerMacroColumns(protein: Double, carbs: Double, fats: Double, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LoggerMacroColumn("Proteína", protein, LoggerColors.macroP, Modifier.weight(1f))
        LoggerMacroColumn("Carbos", carbs, LoggerColors.macroC, Modifier.weight(1f))
        LoggerMacroColumn("Grasa", fats, LoggerColors.macroF, Modifier.weight(1f))
    }
}

@Composable
private fun LoggerMacroColumn(label: String, grams: Double, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        LoggerFitText(
            text = "${grams.roundToInt()} g",
            style = LoggerText.number(16f),
            color = LoggerColors.ink,
            minSize = 13.sp,
        )
        Row(modifier = Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(tone))
            Text(
                text = label,
                style = LoggerText.cap,
                color = LoggerColors.faint,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
