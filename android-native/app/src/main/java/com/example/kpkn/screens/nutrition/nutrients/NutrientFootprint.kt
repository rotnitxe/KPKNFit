package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
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
import com.example.kpkn.domain.nutrition.FootprintBand
import com.example.kpkn.domain.nutrition.NutrientDataKind
import com.example.kpkn.domain.nutrition.NutrientFoodCoverage
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Acento músculo del lienzo (`--mus`): las tres barras que más aportan y las cifras de «Destaca en». */
internal val NutrientAccent = Color(0xFFF49A6E)
/** Tinta apagada del lienzo (`--dim`): «sin dato». */
internal val NutrientDim = Color(0xFF5F5C57)

/** Alturas de la huella del lienzo: ficha 84, comida 78, hoja de ajuste 74; la del día usa la de la ficha. */
object NutrientFootprintHeights {
    val Food: Dp = 84.dp
    val Meal: Dp = 78.dp
    val Edit: Dp = 74.dp
    val Day: Dp = 84.dp
}

private const val GROW_MS = 900
private const val STAGGER_MS = 24
private val GrowEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * La huella de nutrientes (`huella()` de scenes-micro.js): una barra fina por nutriente hasta el 100 % de tu referencia (línea punteada
 * con «100 %»); las tres que más aportan en acento y con su sigla; los topes (sodio, cafeína) en contorno, en rojo si se pasan;
 * «sin dato» como trazo punteado a ras de suelo (nunca una barra de 0); la parte estimada en contorno discontinuo; un punto sobre la línea
 * si pasa del 100 %. Crece de izquierda a derecha al aparecer (24 ms entre barras), salvo con movimiento reducido.
 */
@Composable
fun NutrientFootprint(
    model: NutrientFootprintModel,
    modifier: Modifier = Modifier,
    height: Dp = NutrientFootprintHeights.Food,
    legend: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val totalMs = GROW_MS + STAGGER_MS * (model.bars.size - 1).coerceAtLeast(0)
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(durationMillis = totalMs, easing = LinearEasing))
    }
    val measurer = rememberTextMeasurer()
    val legendStyle = KpknType.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = WizardColors.textFaint)
    val siglaStyle = KpknType.caption.copy(fontWeight = FontWeight.Bold, color = NutrientAccent)
    val hundred = remember(measurer) { measurer.measure("100 %", legendStyle) }
    val legends = remember(measurer) { FootprintBand.entries.associateWith { measurer.measure(it.label, legendStyle) } }
    val siglas = remember(measurer, model) { model.bars.filter { it.highlighted }.associate { it.key to measurer.measure(it.slot.sigla, siglaStyle) } }
    val description = if (model.relative) "Vitaminas y minerales en cantidades relativas" else "Vitaminas y minerales respecto a tu referencia"
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag("nutrient-footprint")
            .semantics { contentDescription = description },
    ) {
        drawFootprint(model, progress.value * totalMs, legend, hundred, legends, siglas)
    }
}

private fun DrawScope.drawFootprint(
    model: NutrientFootprintModel,
    elapsedMs: Float,
    legend: Boolean,
    hundred: TextLayoutResult,
    legends: Map<FootprintBand, TextLayoutResult>,
    siglas: Map<com.example.kpkn.domain.nutrition.NutrientKey, TextLayoutResult>,
) {
    val count = model.bars.size.coerceAtLeast(1)
    val w = size.width
    val gap = 14.dp.toPx()
    val slot = (w - gap * 2) / count
    val bw = maxOf(4.dp.toPx(), slot * 0.5f)
    val top = 20.dp.toPx()
    val legendH = if (legend) (legends.values.maxOfOrNull { it.size.height } ?: 0) + 3.dp.toPx() else 4.dp.toPx()
    val base = size.height - legendH
    val barArea = (base - top).coerceAtLeast(1f)
    val offsetOf = { band: FootprintBand -> when (band) { FootprintBand.VITAMINS -> 0f; FootprintBand.MINERALS -> gap; FootprintBand.OTHER -> gap * 2 } }

    if (!model.relative) {
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, top), Offset(w, top), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))
        drawText(hundred, topLeft = Offset(w - hundred.size.width, top - 3.dp.toPx() - hundred.size.height))
    }
    drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, base + 0.5f), Offset(w, base + 0.5f), strokeWidth = 1.dp.toPx())

    model.bars.forEachIndexed { i, bar ->
        val x = i * slot + offsetOf(bar.slot.band) + (slot - bw) / 2f
        val cx = x + bw / 2f
        val h = bar.height
        if (h == null) {
            drawLine(Color.White.copy(alpha = 0.22f), Offset(cx, base - 2.dp.toPx()), Offset(cx, base - 14.dp.toPx()), strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx())))
            return@forEachIndexed
        }
        val grow = GrowEasing.transform(((elapsedMs - i * STAGGER_MS) / GROW_MS).coerceIn(0f, 1f))
        val pct = bar.percent ?: 0.0
        val full = maxOf(2.5.dp.toPx(), h * barArea)
        val bh = full * grow
        val y = base - bh
        val lim = bar.ceiling && !model.relative
        val hot = bar.highlighted
        val color = when {
            lim -> if (bar.over) WizardColors.danger else WizardColors.text
            hot -> NutrientAccent
            else -> WizardColors.text
        }
        val alpha = when {
            lim || hot -> 1f
            model.day -> if (pct >= 100.0) 0.92f else 0.42f
            else -> 0.5f
        }
        val inset = 0.6.dp.toPx()
        if (bh > 0f) {
            if (lim) {
                drawRoundRect(color, Offset(x + inset, y + inset), Size(bw - inset * 2, (bh - inset * 2).coerceAtLeast(0f)),
                    CornerRadius((bw / 2 - inset).coerceAtLeast(0f)), style = Stroke(width = 1.2.dp.toPx()))
            } else {
                val eh = bh * bar.estimatedShare
                if (bh - eh > 0f) {
                    val r = minOf(bw / 2, (bh - eh) / 2)
                    drawRoundRect(color.copy(alpha = alpha), Offset(x, y + eh), Size(bw, bh - eh), CornerRadius(r))
                }
                if (eh > 0f) {
                    drawRoundRect(color.copy(alpha = maxOf(alpha, 0.7f)), Offset(x + inset, y + inset), Size(bw - inset * 2, (eh - inset).coerceAtLeast(0f)),
                        CornerRadius((bw / 2 - inset).coerceAtLeast(0f)),
                        style = Stroke(width = 1.1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
                }
            }
            if (bar.over && !lim && !model.relative) {
                val dotY = base - (base - (top - 4.dp.toPx())) * grow
                drawCircle(color.copy(alpha = alpha), radius = 1.8.dp.toPx(), center = Offset(cx, dotY))
            }
        }
        siglas[bar.key]?.let { text ->
            val baseline = if (bar.over) top - 9.dp.toPx() else base - full - 6.dp.toPx()
            drawText(text, topLeft = Offset(cx - text.size.width / 2f, baseline - text.firstBaseline))
        }
    }

    if (legend) {
        FootprintBand.entries.forEach { band ->
            val idx = model.bars.indices.filter { model.bars[it].slot.band == band }
            if (idx.isEmpty()) return@forEach
            val mid = (idx.first() + idx.last() + 1) / 2f * slot + offsetOf(band)
            val text = legends.getValue(band)
            drawText(text, topLeft = Offset(mid - text.size.width / 2f, size.height - text.size.height.toFloat()))
        }
    }
}

/** `microHead`: «VITAMINAS Y MINERALES» a la izquierda y «% de tu día» (o lo que diga [right]) a la derecha. */
@Composable
fun NutrientMicroHead(modifier: Modifier = Modifier, right: String = "% de tu día") {
    Row(modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text("VITAMINAS Y MINERALES", style = KpknType.eyebrow, color = WizardColors.textFaint, modifier = Modifier.weight(1f))
        Text(right, style = KpknType.caption.copy(fontWeight = FontWeight.Medium), color = WizardColors.textFaint)
    }
}

/** «Destaca en» (`.nhi`): píldoras con el nombre y el % en acento; los topes, en rojo suave con «del máx.». Nada si no hay que destacar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutrientHighlightsRow(highlights: List<NutrientHighlight>, modifier: Modifier = Modifier, label: String = "Destaca en") {
    if (highlights.isEmpty()) return
    FlowRow(
        modifier.fillMaxWidth().padding(top = 10.dp).testTag("nutrient-highlights"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.heightIn(min = 30.dp).padding(end = 2.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, style = KpknType.caption, color = WizardColors.textFaint)
        }
        highlights.forEach { item ->
            val tone = if (item.warn) WizardColors.danger else NutrientAccent
            Row(
                Modifier.heightIn(min = 30.dp)
                    .background(tone.copy(alpha = 0.07f), RoundedCornerShape(15.dp))
                    .border(1.dp, tone.copy(alpha = if (item.warn) 0.25f else 0.19f), RoundedCornerShape(15.dp))
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(item.name, style = KpknType.caption, color = WizardColors.text)
                Text(item.percentText, style = NutrientFigureStyle.copy(color = tone))
            }
        }
    }
}

/** Cifra corta en Syne Bold 13 (`.nhi b`, `.nrow .p`), con números tabulares. */
internal val NutrientFigureStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp,
    fontFeatureSettings = "'lnum' 1, 'tnum' 1")

/** Línea de cobertura (`.ncov`): texto pequeño en tinta tenue, con lo que se le ponga delante (puntos, icono). */
@Composable
internal fun NutrientCoverageLine(text: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        leading?.invoke()
        Text(text, style = KpknType.caption, color = WizardColors.textFaint, modifier = Modifier.weight(1f, fill = trailing != null))
        trailing?.invoke()
    }
}

/** Un punto por alimento: lleno = ficha completa, discontinuo = estimado; sin ficha, discontinuo y tenue. */
@Composable
fun NutrientCoverageDots(kinds: List<NutrientDataKind>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        kinds.forEach { kind ->
            Canvas(Modifier.size(7.dp)) {
                when (kind) {
                    NutrientDataKind.DECLARED -> drawCircle(WizardColors.text)
                    else -> drawCircle(if (kind == NutrientDataKind.ESTIMATED) WizardColors.text else WizardColors.textFaint,
                        radius = size.minDimension / 2 - 0.6.dp.toPx(),
                        style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6.dp.toPx(), 1.4.dp.toPx()))))
                }
            }
        }
    }
}

/**
 * Bloque de la ficha de un alimento y de la hoja de ajuste (n-ficha, n-ficha-label, n-edit): cabecera «Vitaminas y minerales · % de tu
 * día», la huella y «Destaca en». Con [onSeeAll] añade «N de 38 nutrientes con dato · Ver todos ›». Si es una etiqueta corta, el aviso
 * «La etiqueta declara…» con [onCompleteWithOwnFood] («Completar con una ficha propia»). Sin referencia DRI, cantidades relativas (M-D5).
 * Es la pieza que se enchufa en los huecos `footprint` del registro.
 */
@Composable
fun NutrientFootprintSection(
    model: NutrientFootprintModel,
    modifier: Modifier = Modifier,
    height: Dp = NutrientFootprintHeights.Food,
    /** Texto del aviso de etiqueta (`NutrientFootprints.labelOnlyText(food)`); se pinta si la ficha es «solo etiqueta». */
    labelOnlyText: String? = null,
    onSeeAll: (() -> Unit)? = null,
    onCompleteWithOwnFood: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().testTag("nutrient-footprint-section")) {
        NutrientMicroHead(right = if (model.relative) "cantidades relativas" else "% de tu día")
        val shown = if (model.labelOnly) model.copy(bars = model.bars.map { it.copy(highlighted = false) }) else model
        NutrientFootprint(shown, height = height)
        when {
            model.relative -> NutrientCoverageLine(NutrientFootprints.RELATIVE_LINE)
            !model.labelOnly -> NutrientHighlightsRow(model.highlights)
        }
        if (model.labelOnly && labelOnlyText != null) NutrientLabelOnlyNotice(labelOnlyText, onCompleteWithOwnFood)
        if (onSeeAll != null && model.nutrientsWithData != null) {
            NutrientCoverageLine(
                "${model.nutrientsWithData} de ${NutrientFootprints.TOTAL_NUTRIENTS} nutrientes con dato",
                trailing = { KpknTextAction("Ver todos", onSeeAll, icon = KpknLineIcons.ChevronRight) },
            )
        }
    }
}

/** El aviso de n-ficha-label: icono de información, qué declara la etiqueta y «Completar con una ficha propia». */
@Composable
fun NutrientLabelOnlyNotice(text: String, onCompleteWithOwnFood: (() -> Unit)?, modifier: Modifier = Modifier) {
    // `.mod` del lienzo sobre la hoja: relleno de blanco al 2 % y el filete de módulo, radio 24.
    Column(
        modifier.fillMaxWidth().padding(top = 12.dp).testTag("nutrient-label-only")
            .background(Color.White.copy(alpha = 0.024f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(KpknLineIcons.Info, contentDescription = null, tint = WizardColors.textMuted, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Column {
                Text(text, style = KpknType.caption, color = WizardColors.text)
                if (onCompleteWithOwnFood != null) {
                    Spacer(Modifier.height(8.dp))
                    KpknTextAction("Completar con una ficha propia", onCompleteWithOwnFood, leadingIcon = KpknLineIcons.Edit)
                }
            }
        }
    }
}

/**
 * Módulo de la comida en el registro (n-reg): «Vitaminas y minerales · % de tu día», la huella de la comida entera, «Esta comida: …» y la
 * cobertura con un punto por alimento. Tocarlo abre la lista de la comida ([onOpen]).
 */
@Composable
fun NutrientMealModule(
    model: NutrientFootprintModel,
    coverage: NutrientFoodCoverage,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(
        modifier.fillMaxWidth().padding(top = 10.dp).testTag("nutrient-meal-module"),
        onClick = onOpen,
        onClickLabel = "Ver los nutrientes de la comida",
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("VITAMINAS Y MINERALES", style = KpknType.eyebrow, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
            Text(if (model.relative) "cantidades relativas" else "% de tu día", style = KpknType.caption, color = WizardColors.textFaint)
        }
        Spacer(Modifier.height(8.dp))
        NutrientFootprint(model, height = NutrientFootprintHeights.Meal)
        if (model.relative) NutrientCoverageLine(NutrientFootprints.RELATIVE_LINE) else NutrientHighlightsRow(model.highlights, label = "Esta comida")
        NutrientCoverageLine(
            NutrientFootprints.mealCoverageLine(coverage),
            leading = { NutrientCoverageDots(coverage.kinds) },
            trailing = { Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(14.dp)) },
        )
    }
}

/** El mismo módulo a partir del modelo de la comida (`NutrientFootprints.meal`). */
@Composable
fun NutrientMealModule(meal: NutrientMealModel, onOpen: () -> Unit, modifier: Modifier = Modifier) =
    NutrientMealModule(meal.footprint, meal.coverage, onOpen, modifier)

/** n-home: en la media hoja de Inicio no cabe la huella; solo la línea «Esta comida» con lo que más aporta. */
@Composable
fun NutrientHomeLine(model: NutrientFootprintModel, modifier: Modifier = Modifier) {
    NutrientHighlightsRow(model.highlights.filterNot { it.warn }, modifier.padding(top = 2.dp), label = "Esta comida")
}

/** n-saved: una línea bajo «548 kcal · te quedan…»: «Lo que más sumó: selenio, riboflavina y B12», sin porcentajes. */
@Composable
fun NutrientSavedLine(model: NutrientFootprintModel, modifier: Modifier = Modifier) {
    val line = NutrientFootprints.savedLine(model) ?: return
    Text(line, style = KpknType.caption, color = WizardColors.textFaint, modifier = modifier.padding(top = 6.dp))
}

/** «Ver todos ›» de las cabeceras de módulo (`act faint`). */
@Composable
internal fun NutrientSeeAll(onClick: () -> Unit) {
    KpknTextAction("Ver todos", onClick, icon = KpknLineIcons.ChevronRight, color = WizardColors.textMuted)
}

/** Módulo con cabecera «VITAMINAS Y MINERALES» y «Ver todos ›», para el día (n-day). */
@Composable
internal fun NutrientModuleHeader(onSeeAll: () -> Unit) {
    KpknModuleHeader(label = "Vitaminas y minerales", action = { NutrientSeeAll(onSeeAll) })
}
