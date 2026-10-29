package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.domain.nutrition.NutrientFoodCoverage

/** Tamaños de la huella del lienzo: 150 dp en el registro y en la hoja, 156 dp en el día. */
object NutrientRadialSizes {
    val Food: Dp = 150.dp
    val Meal: Dp = 150.dp
    val Edit: Dp = 150.dp
    val Day: Dp = 156.dp
}

private val GrowEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
/** Pista de cada radio (`#ffffff1c`). */
private val TrackColor = Color.White.copy(alpha = 0x1c / 255f)
/** Contorno del círculo «sin dato» (`#ffffff73`). */
private val NoDataColor = Color.White.copy(alpha = 0x73 / 255f)
/** Código de un radio (`.np-rad text`): Inter SemiBold 11, también con la letra del sistema grande. */
private val RadialLabelStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LoggerColors.ink)
/** Cifra del centro del día (`.c1`): Inter SemiBold 26, −0,4. */
private val RadialCenterStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.4).sp,
    fontFeatureSettings = "'lnum' 1, 'tnum' 1", color = LoggerColors.ink)
/** «de N» del centro (`.c2`): Inter Medium 11, gris tenue. */
private val RadialCenterSubStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = LoggerColors.faint)

/**
 * La huella radial (`npRad` del lienzo Arreglos v1): un radio por nutriente, 24 en tres grupos (12 vitaminas, 9 minerales, 3 otros), cuyo
 * largo es el % de tu referencia hasta el 100 %. En una comida o ración, los tres que más aportan en tinta plena y con su código, el resto
 * al 45 %; en el día, la opacidad dice el nivel y el centro cuenta los objetivos cubiertos. Los topes (sodio, cafeína) van huecos, y macizos
 * en rojo si pasan del máximo; «sin dato» es un círculo discontinuo (nunca un radio de 0). [background] es el color real del fondo: el
 * hueco de los topes y los círculos se pintan con él. Crece de dentro afuera al aparecer, salvo con movimiento reducido.
 */
@Composable
fun NutrientFootprint(
    model: NutrientFootprintModel,
    modifier: Modifier = Modifier,
    size: Dp = NutrientRadialSizes.Food,
    background: Color = LoggerColors.surface1,
) {
    val reduced = wizardReducedMotion()
    val totalMs = radialTotalMs(model.bars.size)
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(durationMillis = totalMs, easing = LinearEasing))
    }
    val measurer = rememberTextMeasurer()
    val spokes = remember(model) { radialSpokes(model) }
    val labels = remember(measurer, spokes) { spokes.filter { it.labeled }.associate { it.index to measurer.measure(it.sigla, RadialLabelStyle) } }
    val legend = remember(model) { if (model.day) radialLegend(model) else null }
    val center = remember(measurer, legend) {
        legend?.let { measurer.measure("${it.covered}", RadialCenterStyle) to measurer.measure("de ${it.goals}", RadialCenterSubStyle) }
    }
    val description = if (model.relative) "Vitaminas y minerales en cantidades relativas" else "Vitaminas y minerales: un radio por nutriente"
    Canvas(
        modifier
            .size(size)
            .testTag("nutrient-footprint")
            .semantics { contentDescription = description },
    ) {
        drawRadial(spokes, labels, center, progress.value * totalMs, background)
    }
}

private fun DrawScope.drawRadial(
    spokes: List<RadialSpoke>,
    labels: Map<Int, TextLayoutResult>,
    center: Pair<TextLayoutResult, TextLayoutResult>?,
    elapsedMs: Float,
    background: Color,
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r0 = size.minDimension * RADIAL_INNER
    val r1 = size.minDimension * RADIAL_OUTER
    val span = r1 - r0
    val thick = 5.dp.toPx()
    spokes.forEach { spoke ->
        val cs = spoke.cosine.toFloat()
        val sn = spoke.sine.toFloat()
        fun at(radius: Float) = Offset(c.x + cs * radius, c.y + sn * radius)
        drawLine(TrackColor, at(r0), at(r1), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        val fraction = spoke.fraction
        if (fraction == null) {
            val dot = at(r0 + 2.dp.toPx())
            drawCircle(background, radius = 2.4.dp.toPx(), center = dot)
            drawCircle(NoDataColor, radius = 2.4.dp.toPx(), center = dot,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.3.dp.toPx(), 1.3.dp.toPx()))))
            return@forEach
        }
        if (fraction <= 0f) return@forEach
        val grow = GrowEasing.transform(radialProgress(elapsedMs, spoke.index))
        if (grow > 0f) {
            val end = at(r0 + radialLength(span, 3.dp.toPx(), fraction) * grow)
            val ink = if (spoke.over) LoggerColors.danger else LoggerColors.ink
            drawLine(ink.copy(alpha = spoke.opacity), at(r0), end, strokeWidth = thick, cap = StrokeCap.Round)
            if (spoke.ceiling && !spoke.over) drawLine(background, at(r0), end, strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        }
        labels[spoke.index]?.let { text ->
            val label = r1 + 9.dp.toPx()
            val x = c.x + cs * label
            val baseline = c.y + sn * label + 4.dp.toPx()
            val left = when (radialLabelAlign(spoke.cosine)) {
                RadialAlign.START -> x
                RadialAlign.CENTER -> x - text.size.width / 2f
                RadialAlign.END -> x - text.size.width
            }
            drawText(text, topLeft = Offset(left, baseline - text.firstBaseline))
        }
    }
    center?.let { (count, of) ->
        drawText(count, topLeft = Offset(c.x - count.size.width / 2f, c.y + 4.dp.toPx() - count.firstBaseline))
        drawText(of, topLeft = Offset(c.x - of.size.width / 2f, c.y + 19.dp.toPx() - of.firstBaseline))
    }
}

/** `npMicro`: «VITAMINAS Y MINERALES» a la izquierda y «% de tu día» (o lo que diga [right]) a la derecha. */
@Composable
fun NutrientMicroHead(modifier: Modifier = Modifier, right: String = "% de tu día") {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Text("VITAMINAS Y MINERALES", style = LoggerText.eyebrow, color = LoggerColors.muted, modifier = Modifier.weight(1f))
        Text(right, style = LoggerText.cap, color = LoggerColors.faint)
    }
}

/**
 * El módulo plano del lienzo (`.np-mod`): fondo [LoggerColors.surface1], radio 24, relleno 16 × 18, sin borde ni degradado. Con [onClick],
 * tocarlo abre la lista completa.
 */
@Composable
internal fun NutrientModuleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(LoggerColors.surface1)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

/** La fila de la huella (`.np-mrow`): el dibujo y, a su derecha con 20 dp de por medio, «Lo que más aporta» ([top]) o la leyenda del día ([legend]). */
@Composable
private fun NutrientRadialRow(
    model: NutrientFootprintModel,
    size: Dp,
    background: Color,
    top: List<NutrientHighlight> = emptyList(),
    legend: RadialLegend? = null,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        NutrientFootprint(model, size = size, background = background)
        when {
            legend != null -> NutrientDayLegend(legend, Modifier.weight(1f))
            top.isNotEmpty() -> NutrientTopList(top, Modifier.weight(1f))
        }
    }
}

/** `npTop`: «Lo que más aporta» y los tres nombres con su porcentaje. */
@Composable
private fun NutrientTopList(rows: List<NutrientHighlight>, modifier: Modifier = Modifier, label: String = "Lo que más aporta") {
    Column(modifier.fillMaxWidth().testTag("nutrient-top"), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, style = LoggerText.cap, color = LoggerColors.faint)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.name, style = LoggerText.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = LoggerColors.muted, modifier = Modifier.weight(1f))
                Text(row.percentText, style = LoggerText.number(13f), color = LoggerColors.ink, maxLines = 1)
            }
        }
    }
}

/**
 * `npDayLegend`: una barrita de 4 × 14 dp por nivel con su recuento en negrita —«cubiertos», «cerca», «por debajo»— y, si un tope pasa de su
 * máximo, «1 · <nombre>: sobre el máximo» en rojo.
 */
@Composable
private fun NutrientDayLegend(legend: RadialLegend, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("nutrient-day-legend"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendRow(legend.covered, "cubiertos", RADIAL_OPACITY_COVERED)
        LegendRow(legend.near, "cerca", RADIAL_OPACITY_NEAR)
        LegendRow(legend.below, "por debajo", RADIAL_OPACITY_BELOW)
        legend.overMax.forEach { name -> LegendRow(1, "$name: sobre el máximo", over = true) }
    }
}

@Composable
private fun LegendRow(count: Int, text: String, opacity: Float = 1f, over: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 4.dp, height = 14.dp)) {
            val radius = CornerRadius(2.dp.toPx())
            if (over) {
                val inset = 0.75.dp.toPx()
                drawRoundRect(LoggerColors.danger, topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2), cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
            } else {
                drawRoundRect(LoggerColors.ink.copy(alpha = opacity), cornerRadius = radius)
            }
        }
        Box(Modifier.widthIn(min = 16.dp), contentAlignment = Alignment.CenterEnd) {
            Text(count.toString(), style = LoggerText.number(13f), color = if (over) LoggerColors.danger else LoggerColors.ink)
        }
        Text(text, style = LoggerText.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = LoggerColors.muted, modifier = Modifier.weight(1f, fill = false))
    }
}

/**
 * `.np-cov`: separada por un filete, la fila de cobertura con el icono de información, el texto y, si abre algo ([onClick]), el chevron.
 * Sin [onClick] es solo una línea informativa.
 */
@Composable
internal fun NutrientCoverageRow(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onClickLabel: String? = null) {
    val hair = LoggerColors.hair
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp)
            .drawBehind { drawLine(hair, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(top = 12.dp)
            .testTag("nutrient-coverage"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KpknLineIcons.Info, contentDescription = null, tint = LoggerColors.faint, modifier = Modifier.size(15.dp))
        Text(text, style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = LoggerColors.faint, modifier = Modifier.size(15.dp))
    }
}

/**
 * Bloque de la ficha de un alimento y de la hoja de ajuste: «VITAMINAS Y MINERALES · % de tu día», la huella con «Lo que más aporta» y la
 * fila de cobertura («N de 38 nutrientes con dato ›», que abre la lista con [onSeeAll]). Si es una etiqueta corta, el aviso «La etiqueta
 * declara…» con [onCompleteWithOwnFood]. Sin referencia DRI, cantidades relativas (M-D5). Va dentro de la hoja, sin fondo propio:
 * [background] es el color de lo que hay detrás (la hoja, o la tarjeta si la hoja lo envuelve en una).
 */
@Composable
fun NutrientFootprintSection(
    model: NutrientFootprintModel,
    modifier: Modifier = Modifier,
    size: Dp = NutrientRadialSizes.Food,
    /** Texto del aviso de etiqueta (`NutrientFootprints.labelOnlyText(food)`); se pinta si la ficha es «solo etiqueta». */
    labelOnlyText: String? = null,
    onSeeAll: (() -> Unit)? = null,
    onCompleteWithOwnFood: (() -> Unit)? = null,
    background: Color = LoggerColors.sheet,
) {
    Column(modifier.fillMaxWidth().testTag("nutrient-footprint-section")) {
        NutrientMicroHead(right = if (model.relative) "cantidades relativas" else "% de tu día")
        val shown = if (model.labelOnly) model.copy(bars = model.bars.map { it.copy(highlighted = false) }) else model
        val top = if (model.relative || model.labelOnly) emptyList() else radialTopRows(model)
        NutrientRadialRow(shown, size, background, top = top)
        if (model.labelOnly && labelOnlyText != null) NutrientLabelOnlyNotice(labelOnlyText, onCompleteWithOwnFood)
        val line = when {
            model.relative -> NutrientFootprints.RELATIVE_LINE
            model.nutrientsWithData != null -> "${model.nutrientsWithData} de ${NutrientFootprints.TOTAL_NUTRIENTS} nutrientes con dato"
            else -> null
        }
        if (line != null) NutrientCoverageRow(line, onClick = onSeeAll, onClickLabel = "Ver todos los nutrientes")
    }
}

/** El aviso de una ficha que solo trae la etiqueta: icono de información, qué declara y «Completar con una ficha propia». */
@Composable
fun NutrientLabelOnlyNotice(text: String, onCompleteWithOwnFood: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 12.dp).testTag("nutrient-label-only")
            .background(LoggerColors.surface2, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(KpknLineIcons.Info, contentDescription = null, tint = LoggerColors.muted, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Column {
                Text(text, style = LoggerText.cap, color = LoggerColors.ink)
                if (onCompleteWithOwnFood != null) {
                    Spacer(Modifier.height(8.dp))
                    KpknTextAction("Completar con una ficha propia", onCompleteWithOwnFood, leadingIcon = KpknLineIcons.Edit)
                }
            }
        }
    }
}

/**
 * Módulo de la comida en el registro (`npMicro`): «VITAMINAS Y MINERALES · % de tu día», la huella, «Lo que más aporta» con los tres nombres
 * y la fila de cobertura de la comida. Tocarlo abre la lista de la comida ([onOpen]).
 */
@Composable
fun NutrientMealModule(
    model: NutrientFootprintModel,
    coverage: NutrientFoodCoverage,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NutrientModuleCard(
        modifier.fillMaxWidth().padding(top = 10.dp).testTag("nutrient-meal-module"),
        onClick = onOpen,
        onClickLabel = "Ver los nutrientes de la comida",
    ) {
        NutrientMicroHead(right = if (model.relative) "cantidades relativas" else "% de tu día")
        val top = if (model.relative) emptyList() else radialTopRows(model)
        NutrientRadialRow(model, NutrientRadialSizes.Meal, LoggerColors.surface1, top = top)
        NutrientCoverageRow(if (model.relative) NutrientFootprints.RELATIVE_LINE else NutrientFootprints.mealCoverageLine(coverage), onClick = onOpen)
    }
}

/** El mismo módulo a partir del modelo de la comida (`NutrientFootprints.meal`). */
@Composable
fun NutrientMealModule(meal: NutrientMealModel, onOpen: () -> Unit, modifier: Modifier = Modifier) =
    NutrientMealModule(meal.footprint, meal.coverage, onOpen, modifier)

/** El módulo del día (`npDay`): la huella del día con la leyenda al lado y, abajo, la cobertura del motor. Tocarlo abre la lista. */
@Composable
internal fun NutrientDayRadialModule(
    model: NutrientFootprintModel,
    context: String,
    coverageText: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NutrientModuleCard(modifier.testTag("nutrients-day-module"), onClick = onOpen, onClickLabel = "Ver los nutrientes del día") {
        NutrientMicroHead(right = context)
        NutrientRadialRow(model, NutrientRadialSizes.Day, LoggerColors.surface1, legend = radialLegend(model))
        if (coverageText != null) NutrientCoverageRow(coverageText, onClick = onOpen)
    }
}
