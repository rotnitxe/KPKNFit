package com.example.kpkn.ui.design.depth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/*
 * Hojas de calibración de PROFUNDIDAD del lienzo (`cm-som-*`, `cm-vid-1`, `cm-vid-filtros-1`, `cm-deg-1…5`), dibujadas con los cimientos de `ui/design/depth`.
 * Misma geometría que `src/scenes-cm.js` y `src/cm.css` del lienzo: 360 × 880 dp, fondo #0B0B0C, muestras de 140 × 84 dp con radio 24 y relleno #1B1B1D, franjas de
 * vidrio de 312 × 72 dp sobre ocho barras de color con texto de 13 px, y los rótulos y la cabecera tal como salen en el lienzo.
 * La usa `DepthSheetRenderTest` (puerta 2 de PROTOCOLO_1A1.md) y se compara con `node tools/compare.cjs`.
 *
 *   KpknDepthSheet("cm-som-caida-2")                                // la hoja entera, con rótulos y cabecera
 *   KpknDepthSheet("cm-som-caida-2", showLabels = false)            // solo las muestras (para medir el error de cada sombra)
 *   KpknDepthSheet("cm-vid-1", effects = false)                     // el fondo de barras y texto sin vidrio (referencia para calibrar el desenfoque)
 */

internal enum class KpknDepthSheetKind { Shadow, TextShadow, Glass, Filter, Gradient }

internal data class KpknDepthSample(
    val name: String,
    val x: Float,
    val y: Float,
    val lx: Float,
    val ly: Float,
    val lw: Float,
    val uses: Int,
    val members: Int,
    val text: String,
    val preset: Any,
)

internal data class KpknDepthSheetDef(
    val id: String,
    val kind: KpknDepthSheetKind,
    val title: String,
    val sub: String,
    val samples: List<KpknDepthSample>,
)

private val SheetBg = Color(0xFF0B0B0C)
private val SampleSurface = Color(0xFF1B1B1D)
private val TextCream = Color(0xFFF2EEE6)
private val TextMuted = Color(0xFF8E8980)
private val TextLabel = Color(0xFFC4BFB6)
private val SampleShape = RoundedCornerShape(24.dp)
private val ZoneSample = Color(0xFF87C9A5)
private val CardioSample = Color(0xFF8FB2FF)
private val BarColors = listOf(
    Color(0xFFE5484D), Color(0xFFF7CF73), Color(0xFF43D18C), Color(0xFF8FB2FF),
    Color(0xFFA98BFF), Color(0xFFF49A6E), Color(0xFFF2EEE6), Color(0xFF2A2A2D),
)
private val LabelBase = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 12.5.sp,
    color = TextLabel,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** La hoja [sheet] (id del lienzo, p. ej. `cm-som-caida-2`). [showLabels]: rótulos y cabecera. [effects]: false = en las hojas de vidrio y filtros, solo el fondo sin efecto. */
@Composable
internal fun KpknDepthSheet(sheet: String, showLabels: Boolean = true, effects: Boolean = true) {
    val def = remember(sheet) { KpknDepthSheetData.byId(sheet) }
    val hazeState = remember { HazeState() }
    Box(Modifier.size(360.dp, 880.dp).clipToBounds().background(SheetBg)) {
        when (def.kind) {
            KpknDepthSheetKind.Shadow -> for (s in def.samples) ShadowSample(s)
            KpknDepthSheetKind.TextShadow -> for (s in def.samples) TextShadowSample(s)
            KpknDepthSheetKind.Gradient -> for (s in def.samples) GradientSample(s)
            KpknDepthSheetKind.Filter -> for (s in def.samples) FilterSample(s, effects)
            KpknDepthSheetKind.Glass -> {
                DepthPattern(Modifier.fillMaxSize().hazeSource(state = hazeState), 360f, 880f)
                for (s in def.samples) GlassStrip(s, hazeState, effects)
            }
        }
        if (showLabels) {
            for (s in def.samples) {
                if (def.kind == KpknDepthSheetKind.Glass) GlassTag(s) else SampleLabel(s)
            }
            SheetHeader(def.title, def.sub)
        }
    }
}

/* ---------------------------------------------------------------- muestras */

@Composable
private fun ShadowSample(s: KpknDepthSample) {
    val preset = s.preset as KpknShadow
    Box(
        Modifier
            .offset(s.x.dp, s.y.dp)
            .size(140.dp, 84.dp)
            .background(SampleSurface, SampleShape)
            .kpknShadow(preset, SampleShape, color = preset.sampleColor ?: Color.Unspecified),
    )
}

@Composable
private fun TextShadowSample(s: KpknDepthSample) {
    val preset = s.preset as KpknTextShadow
    Box(
        Modifier.offset(s.x.dp, s.y.dp).size(140.dp, 84.dp).clip(SampleShape).background(Color(0xFF9A968F)),
        contentAlignment = Alignment.Center,
    ) {
        KpknShadowedText(
            text = "Aa 128,5",
            preset = preset,
            style = TextStyle(
                fontFamily = WizardFonts.body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 24.sp,
                fontFeatureSettings = "'tnum' 1, 'lnum' 1",
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun GradientSample(s: KpknDepthSample) {
    val preset = s.preset as KpknGradient
    Box(Modifier.offset(s.x.dp, s.y.dp).size(140.dp, 84.dp).kpknGradient(preset, SampleShape))
}

@Composable
private fun FilterSample(s: KpknDepthSample, effects: Boolean) {
    val preset = s.preset as KpknFilterPreset
    Box(Modifier.offset(s.x.dp, s.y.dp).size(140.dp, 84.dp).clip(SampleShape).background(SheetBg)) {
        DepthPattern(Modifier.fillMaxSize().then(if (effects) Modifier.kpknFilter(preset) else Modifier), 140f, 84f)
    }
}

@Composable
private fun GlassStrip(s: KpknDepthSample, hazeState: HazeState, effects: Boolean) {
    val preset = s.preset as KpknGlassPreset
    if (!effects) return
    // El vidrio en un nodo de fondo sin contenido (la saturación y el brillo no tocan nada más) y el aro interior de 1 dp en otro encima.
    Box(Modifier.offset(s.x.dp, s.y.dp).size(312.dp, 72.dp).kpknGlassPresetForSheet(hazeState, preset))
    Box(
        Modifier
            .offset(s.x.dp, s.y.dp)
            .size(312.dp, 72.dp)
            .kpknInset(KpknShadowSpec.of(spread = 1.dp, color = Color(0x2EFFFFFF), inset = true), SampleShape),
    )
}

private fun Modifier.kpknGlassPresetForSheet(hazeState: HazeState, preset: KpknGlassPreset): Modifier =
    kpknGlassPreset(hazeState, preset, SampleShape)

/* ---------------------------------------------------------------- fondo de barras y texto (`cmPattern`) */

private const val PatternRepeats = 44
private val PatternText: String = buildString {
    for (i in 0 until PatternRepeats) append("KPKN FIT 0123456789 ABCDEFGHIJ ").append(if (i % 2 == 1) "SESIÓN " else "RUTINA ")
}

/** Las ocho barras de color y el texto de 13 px de `.cm-pat` (ancho [widthDp] × alto [heightDp]); el texto parte las líneas en cualquier carácter (`word-break: break-all`). */
@Composable
private fun DepthPattern(modifier: Modifier, widthDp: Float, heightDp: Float) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
        color = Color.White,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { widthDp.dp.toPx() }
    val lines = remember(widthPx, heightDp) {
        val layout = measurer.measure(AnnotatedString(PatternText), style, softWrap = false, constraints = Constraints())
        val n = PatternText.length
        val maxLines = (heightDp / 18f).toInt() + 2
        val out = ArrayList<String>()
        var start = 0
        while (start < n && out.size < maxLines) {
            val x0 = layout.getHorizontalPosition(start, true)
            var end = start
            while (end < n && layout.getHorizontalPosition(end + 1, true) - x0 <= widthPx + 3f) end++
            if (end == start) end = start + 1
            else if (end < n && PatternText[end] == ' ') end++
            out += PatternText.substring(start, end).trimEnd()
            start = end
        }
        out
    }
    Box(modifier.clipToBounds()) {
        Row(Modifier.fillMaxSize()) {
            for (c in BarColors) Box(Modifier.weight(1f).fillMaxHeight().background(c))
        }
        Column(Modifier.fillMaxSize().alpha(0.92f)) {
            for (line in lines) Text(text = line, style = style, softWrap = false, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.height(18.dp))
        }
    }
}

/* ---------------------------------------------------------------- rótulos y cabecera (cm-l, cm-gt, cm-h) */

@Composable
private fun SampleLabel(s: KpknDepthSample) {
    val bold = SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextCream)
    val dim = SpanStyle(fontSize = 9.5.sp, color = TextMuted)
    val first = buildAnnotatedString {
        withStyle(bold) { append(s.name) }
        withStyle(dim) {
            append("  ${s.uses} ${if (s.uses == 1) "fila" else "filas"}")
            if (s.members > 1) append(" · ${s.members} valores")
        }
    }
    Column(
        Modifier
            .offset(s.lx.dp, s.ly.dp)
            .width(s.lw.dp)
            .then(if (s.lw > 200f) Modifier else Modifier.heightIn(max = 54.dp))
            .clipToBounds(),
    ) {
        Text(text = first, style = LabelBase)
        Text(
            text = s.text,
            style = LabelBase.copy(fontSize = 9.5.sp, color = TextMuted, fontFeatureSettings = "'tnum' 1"),
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun GlassTag(s: KpknDepthSample) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.name) }
        append(" · ${s.uses} filas · ${s.text}")
    }
    Box(
        Modifier
            .offset(s.lx.dp, s.ly.dp)
            .widthIn(max = 296.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x8C000000))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = LabelBase.copy(color = Color.White), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetHeader(title: String, sub: String) {
    Column(
        Modifier.fillMaxWidth().height(56.dp).background(SheetBg).padding(start = 16.dp, end = 16.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontFamily = WizardFonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 12.1.sp,
                letterSpacing = 1.sp,
                color = TextCream,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
        )
        Text(
            text = sub,
            style = LabelBase.copy(color = TextMuted, lineHeight = 12.sp),
        )
    }
}
