package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.programdetail.design.structure.StructureDivider
import com.example.kpkn.screens.programdetail.design.structure.StructureFaint
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructureInset
import com.example.kpkn.screens.programdetail.design.structure.StructureMuted

/*
 * Piezas del lienzo v6 propias de la pestaña Progreso (docs/design/program-detail-v1/src/styles.css): `.headline`, `.kpi`, `.num`,
 * `.lift`, `.st`, `spark()`, `.legend`, `.ratio` y el `.seg` pequeño de «Hecho / Plan». Cada medida es la del CSS, de px a dp/sp uno a uno.
 * Los módulos, cabeceras, acciones y filas de hoja son las de Estructura (`structure/ProgramStructureCanvas.kt`).
 */

// ── Colores del lienzo ──────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `--done`, `--ene`, `--danger`, `--mus`. */
internal val ProgressDone = SymbolPalette.ok
internal val ProgressEne = SymbolPalette.energia
internal val ProgressDanger = WizardColors.danger
internal val ProgressMus = SymbolPalette.musculo

/** Línea de un ejercicio sin tendencia (`#888`). */
private val ProgressNoTrend = Color(0xFF888888)

// ── Tipografía del lienzo ────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `.headline`: Syne 700 19 / 1,25, espaciado −0,3. */
internal val ProgressHeadlineStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 19.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.3).sp,
)

/** `.kpi small`: Inter 600 10, espaciado 1, en mayúsculas. */
internal val ProgressKpiLabelStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    letterSpacing = 1.sp,
)

/** `.kpi b`: Syne 800 20 / 1,1, espaciado −0,4, con cifras tabulares. */
internal val ProgressKpiValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.4).sp,
    fontFeatureSettings = "'tnum' 1",
)

/** La unidad pequeña dentro de una cifra (`font:600 12px Inter`): « %», « t». */
internal val ProgressUnitSpan = SpanStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp)

/** `.kpi span`: Inter 11. */
internal val ProgressKpiFooterStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)

/** `.lift strong`: Inter 600 14. */
internal val ProgressLiftNameStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)

/** `.lift span`: Inter 12 (también el texto de `.st`, que hereda el tamaño de `.lift span`). */
internal val ProgressLiftSpanStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

/** `.st`: el texto en 600 con 0,3 de espaciado. */
private val ProgressStatusStyle = ProgressLiftSpanStyle.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)

/** `.lift .val b`: Syne 700 16, con cifras tabulares. */
internal val ProgressLiftValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** La unidad « kg» del valor (`font:600 11px Inter; color: faint`). */
internal val ProgressKgSpan = SpanStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = StructureFaint)

/** `.lift .val em`: Inter 600 11. */
internal val ProgressLiftDeltaStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** `.num` a 20 px: Syne 800, espaciado −0,6, con cifras tabulares. */
internal val ProgressNumStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.6).sp,
    fontFeatureSettings = "'tnum' 1",
)

/** `.legend`: Inter 11. */
internal val ProgressLegendStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)

/** `.bars-l span`: Inter 10. */
internal val ProgressBarLabelStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 13.sp)

/** `.ratio`: Inter 13. */
internal val ProgressRatioStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp)

/** La cifra de una fila de esfuerzo o de las mejores series: Syne 700 14, con cifras tabulares. */
internal val ProgressRatioValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** `.vrow-h b`: Syne 700 15, con cifras tabulares. */
internal val ProgressVolumeValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 19.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** `.tl strong`: Inter 600 13,5. */
internal val ProgressTimelineTitleStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp)

/** `.tl span`: Inter 12 / 1,4. */
internal val ProgressTimelineLineStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)

/** `.seg button` a 12 px: Inter 600 12. */
private val ProgressSegStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)

// ── Iconos del lienzo que KPKN Line no trae ──────────────────────────────────────────────────────────────────────────────────────

/** Iconos de app.js con el trazo del set (1,75, extremos redondeados): `trendUp`, `trendDown`, `flat` y `spark`. */
internal object ProgressIcons {
    val TrendUp: ImageVector by lazy { lineIcon("KpknLine.ProgressTrendUp", listOf("M4 17l6-6 4 4 6-7", "M15 8h5v5")) }
    val TrendDown: ImageVector by lazy { lineIcon("KpknLine.ProgressTrendDown", listOf("M4 7l6 6 4-4 6 7", "M15 16h5v-5")) }
    val Flat: ImageVector by lazy { lineIcon("KpknLine.ProgressFlat", listOf("M4 12h16M16 8l4 4-4 4")) }
    val Spark: ImageVector by lazy { lineIcon("KpknLine.ProgressSpark", listOf("M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z")) }
}

/** Un icono de línea: trazo de 1,75 dp con extremos y uniones redondeados, retícula de 24. */
private fun lineIcon(name: String, paths: List<String>): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    for (d in paths) {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

// ── Estado de un ejercicio ───────────────────────────────────────────────────────────────────────────────────────────────────────

/** Color del estado (`.up`, `.flat`, `.down`): verde, ámbar, rojo; sin tendencia, `#888`. */
internal fun liftStatusColor(status: LiftTrendStatus): Color = when (status) {
    LiftTrendStatus.UP -> ProgressDone
    LiftTrendStatus.STAGNANT -> ProgressEne
    LiftTrendStatus.DOWN -> ProgressDanger
    LiftTrendStatus.NO_TREND -> ProgressNoTrend
}

/** Etiqueta del estado, tal como se lee en su cápsula. */
internal fun liftStatusLabel(status: LiftTrendStatus): String = when (status) {
    LiftTrendStatus.UP -> "Sube"
    LiftTrendStatus.STAGNANT -> "Estancado"
    LiftTrendStatus.DOWN -> "Baja"
    LiftTrendStatus.NO_TREND -> "Sin tendencia"
}

/**
 * `.st`: cápsula de 22 dp con el icono de 13 dp y la etiqueta a 5 dp. El relleno es el color del estado al 10 % (`#ffffff0d` sin
 * tendencia); el texto e icono van en `--faint`, como en el lienzo (`.lift span` manda sobre `.up`), y sin tendencia en `--muted`.
 */
@Composable
internal fun LiftStatusCapsule(status: LiftTrendStatus, modifier: Modifier = Modifier) {
    val fill = if (status == LiftTrendStatus.NO_TREND) Color(0x0DFFFFFF) else liftStatusColor(status).copy(alpha = 0.10f)
    val ink = if (status == LiftTrendStatus.NO_TREND) StructureMuted else StructureFaint
    val icon = when (status) {
        LiftTrendStatus.UP -> ProgressIcons.TrendUp
        LiftTrendStatus.STAGNANT -> ProgressIcons.Flat
        LiftTrendStatus.DOWN -> ProgressIcons.TrendDown
        LiftTrendStatus.NO_TREND -> null
    }
    Row(
        modifier = modifier
            .height(22.dp)
            .background(fill, RoundedCornerShape(11.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = ink)
        }
        Text(text = liftStatusLabel(status), style = ProgressStatusStyle, color = ink, maxLines = 1)
    }
}

/**
 * `spark()`: la línea de [values] en 74 × 28 dp, con su área al 12 % y un punto de 2,6 dp al final. Con menos de dos valores, una raya
 * discontinua (`#ffffff22`, 3 · 4) a media altura.
 */
@Composable
internal fun ProgressSparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(74.dp, 28.dp)) {
        val u = size.width / 74f
        val w = 74f
        val h = 28f
        if (values.size < 2) {
            drawLine(
                color = Color(0x22FFFFFF),
                start = androidx.compose.ui.geometry.Offset(2f * u, h / 2f * u),
                end = androidx.compose.ui.geometry.Offset((w - 2f) * u, h / 2f * u),
                strokeWidth = 1f * u,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 4f * u)),
            )
            return@Canvas
        }
        val low = values.min()
        val high = values.max()
        val range = (high - low).takeIf { it > 0.0 } ?: 1.0
        val points = values.mapIndexed { i, v ->
            androidx.compose.ui.geometry.Offset(
                (2f + i * (w - 6f) / (values.size - 1)) * u,
                (h - 4f - ((v - low) / range).toFloat() * (h - 8f)) * u,
            )
        }
        val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, h * u)
            lineTo(2f * u, h * u)
            close()
        }
        drawPath(area, color = color.copy(alpha = 0.12f))
        drawPath(line, color = color, style = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = color, radius = 2.6f * u, center = points.last())
    }
}

/** Un filete de `--div` a todo el ancho. */
@Composable
internal fun ProgressDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(StructureDivider))
}

/** `.div`: el filete con 12 dp arriba y abajo. */
@Composable
internal fun ProgressSectionDivider() {
    Spacer(Modifier.height(12.dp))
    ProgressDivider()
    Spacer(Modifier.height(12.dp))
}

/**
 * `.kpis`: tres cifras sobre `--inset` (radio 16, relleno 10 · 12) con 8 dp entre ellas y 14 dp de margen arriba. Cada una lleva la
 * etiqueta en mayúsculas, la cifra (con su unidad pequeña) a 6 dp y el pie.
 */
@Composable
internal fun ProgressKpiRow(kpis: List<ProgressKpiText>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kpis.forEach { kpi -> ProgressKpiTile(kpi, Modifier.weight(1f)) }
    }
}

/** El texto de una cifra: etiqueta, valor, unidad pequeña opcional (« %», « t») y pie. */
internal data class ProgressKpiText(val label: String, val value: String, val unit: String? = null, val footer: String)

@Composable
private fun ProgressKpiTile(kpi: ProgressKpiText, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(StructureInset, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = kpi.label.uppercase(java.util.Locale.forLanguageTag("es")),
            style = ProgressKpiLabelStyle,
            color = StructureFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildAnnotatedString {
                append(kpi.value)
                kpi.unit?.let { withStyle(ProgressUnitSpan) { append(it) } }
            },
            style = ProgressKpiValueStyle,
            color = StructureInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = kpi.footer, style = ProgressKpiFooterStyle, color = StructureFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** `.legend`: 10 dp de margen arriba, 14 dp entre entradas; cada una con su marca a 6 dp del texto. */
@Composable
internal fun ProgressLegend(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Una entrada de la leyenda: la [mark] y el texto en `--faint`. */
@Composable
internal fun ProgressLegendItem(text: String, mark: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark?.invoke()
        Text(text = text, style = ProgressLegendStyle, color = StructureFaint, maxLines = 1)
    }
}

/** `.dot`: punto de 7 dp. */
@Composable
internal fun ProgressDot(color: Color) {
    Box(Modifier.size(7.dp).background(color, CircleShape))
}

/** `.ratio`: fila con filete arriba, 9 dp de relleno vertical y 10 dp entre columnas. */
@Composable
internal fun ProgressRatioRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        ProgressDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * `.seg` pequeño (g-volume): 150 dp de ancho, relleno 3 dp, `#0d0d0d` con filete `#ffffff10`; cada botón de 30 dp, Inter 600 12 en
 * `--faint`, y el elegido en `#272727` con tinta.
 */
@Composable
internal fun ProgressSmallSegment(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(150.dp)
            .background(Color(0xFF0D0D0D), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(24.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .background(if (on) Color(0xFF272727) else Color.Transparent, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = option, style = ProgressSegStyle, color = if (on) StructureInk else StructureFaint, maxLines = 1)
            }
        }
    }
}
