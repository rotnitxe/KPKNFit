package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.domain.cardio.CardioZones
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.screens.workout.CardioZoneColors
import com.example.kpkn.screens.workout.CardioZoneNames
import com.example.kpkn.ui.design.motion.KpknChannel
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.kpknCssColorLerp
import com.example.kpkn.ui.design.motion.rememberKpknPose
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState

/*
 * Pulido v1 · CARDIO DEL EDITOR · piezas compartidas de la tarjeta, las hojas y la gráfica de bloques (`ce.css` / `scenes-ce.js`).
 * Medidas en dp y textos en sp (escalan con la letra, como `calc(Npx*var(--fs))`). Los colores son los de [EdColors] más los del cardio.
 * Lo que es profundidad (sombras, vidrio, degradados) y movimiento (animaciones, transiciones) lleva su marca `// pulido:ce-…` con el ID
 * de la fila de `paridad/ce.md`.
 */

/** Tokens propios del cardio del editor, sobre la paleta del editor ([EdColors]). */
internal object CeColors {
    /** `--col`: el azul del cardio. */
    val col = Color(0xFF8FB2FF)

    /** `.ce-mod`: el módulo de la lista. */
    val module = Color(0xFF111111)

    /** `.ce-item.open`: la fila abierta. */
    val open = Color(0xFF131313)

    /** `.ce-cell`: las celdas de magnitud. */
    val cell = Color(0xFF1A1A1A)

    /** Fondo del segmentado (`#0d0d0d`). */
    val well = Color(0xFF0D0D0D)

    /** `.ce-val`, `.ce-step`: campos de 40 dp sobre la hoja (`#1b1b1d`). */
    val field = Color(0xFF1B1B1D)

    /** Filete de las filas (`#ffffff0f`). */
    val rule = Color.White.copy(alpha = 0.059f)

    /** Borde de los campos (`#ffffff12`). */
    val fieldBorder = Color.White.copy(alpha = 0.071f)
}

private const val Tabular = "'tnum' 1, 'lnum' 1"

/** `.pl-eyebrow`: 10,5 px, 600, 1,5 de espaciado, mayúsculas, `--faint`. */
internal val CeEyebrowStyle = EdEyebrowStyle.copy(fontSize = 10.5.sp, lineHeight = 12.6.sp, letterSpacing = 1.5.sp)

/** Iconos del cardio del editor (trazos de `scenes-ce.js`, retícula de 24, trazo de 1,75). */
internal object CeIcons {
    val Up: ImageVector by lazy { icon("CeUp", "M12 19V5M6 11l6-6 6 6") }
    val Down: ImageVector by lazy { icon("CeDown", "M12 5v14M6 13l6 6 6-6") }
    val Minus: ImageVector by lazy { icon("CeMinus", "M5 12h14") }
    val Plus: ImageVector by lazy { icon("CePlus", "M12 5v14M5 12h14") }
    val ChevUp: ImageVector by lazy { icon("CeChevUp", "M6 15l6-6 6 6") }
    val ChevDown: ImageVector by lazy { icon("CeChevDown", "M6 9l6 6 6-6") }
    val Fwd: ImageVector by lazy { icon("CeFwd", "M9 5l7 7-7 7") }
    val Back: ImageVector by lazy { icon("CeBack", "M15 5l-7 7 7 7") }
    val Check: ImageVector by lazy { icon("CeCheck", "M5 12.5l4.5 4.5L19 7.5") }
    val Close: ImageVector by lazy { icon("CeClose", "M6 6l12 12M18 6L6 18") }
    val Gps: ImageVector by lazy { icon("CeGps", "M12 21s6-5.6 6-10.5a6 6 0 0 0 -12 0C6 15.4 12 21 12 21z", "M9.8 10.5a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0z") }
    val Sun: ImageVector by lazy {
        icon("CeSun", "M8.5 12a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z", "M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6 6l1.4 1.4M16.6 16.6L18 18M6 18l1.4-1.4M16.6 7.4L18 6")
    }
    val Voice: ImageVector by lazy { icon("CeVoice", "M4 10v4h4l5 4V6L8 10z", "M16.5 9.5a4 4 0 0 1 0 5M19 7a7.5 7.5 0 0 1 0 10") }
    val Vib: ImageVector by lazy {
        icon("CeVib", "M10.5 3.5h3A2.5 2.5 0 0 1 16 6v12a2.5 2.5 0 0 1 -2.5 2.5h-3A2.5 2.5 0 0 1 8 18V6a2.5 2.5 0 0 1 2.5 -2.5z", "M4 8v8M20 8v8")
    }
    val Beep: ImageVector by lazy { icon("CeBeep", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5h4") }
    val Chart: ImageVector by lazy { icon("CeChart", "M4 20V10M10 20V4M16 20v-7M22 20H2") }
    val Guide: ImageVector by lazy { icon("CeGuide", "M5 4.5h10a4 4 0 0 1 4 4V20H9a4 4 0 0 1 -4 -4z", "M5 16a4 4 0 0 1 4 -4h10") }
    val Swap: ImageVector by lazy { icon("CeSwap", "M7 7h11l-3-3M17 17H6l3 3") }
    val Trash: ImageVector by lazy { icon("CeTrash", "M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13") }
    val Copy: ImageVector by lazy {
        icon("CeCopy", "M11 8h6a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3h-6a3 3 0 0 1 -3 -3v-6a3 3 0 0 1 3 -3z", "M16 8V6a2 2 0 0 0 -2 -2H6a2 2 0 0 0 -2 2v8a2 2 0 0 0 2 2h2")
    }
    val Sliders: ImageVector by lazy {
        icon("CeSliders", "M5 7h9M18 7h1M5 17h3M12 17h7", "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z")
    }
    val More: ImageVector by lazy { icon("CeMore", "M4.8 12h.01M12 12h.01M19.2 12h.01", width = 3.2f) }

    private fun icon(name: String, vararg paths: String, width: Float = 1.75f): ImageVector {
        val ink = SolidColor(Color.Black)
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = ink,
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}

/** Un icono de [CeIcons] con su tamaño y su tinta. */
@Composable
internal fun CeIcon(icon: ImageVector, size: Dp = 20.dp, tint: Color = EdColors.muted, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Un rótulo `.pl-eyebrow`. */
@Composable
internal fun CeEyebrow(text: String, modifier: Modifier = Modifier, color: Color = EdColors.faint) {
    Text(text.uppercase(java.util.Locale.forLanguageTag("es")), modifier = modifier, style = CeEyebrowStyle.copy(color = color), maxLines = 1)
}

/** `.ce-link`: acción de texto de 40 dp en el azul del cardio («Plantillas»). */
@Composable
internal fun CeLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) CeIcon(leading, 16.dp, CeColors.col)
        Text(text, style = edText(13.5f, FontWeight.SemiBold, CeColors.col), maxLines = 1)
    }
}

/** `.ce-hint`: línea de ayuda de 12,5 px. */
@Composable
internal fun CeHint(text: String, modifier: Modifier = Modifier, center: Boolean = false, error: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = edText(12.5f, FontWeight.Normal, if (error) EdColors.danger else EdColors.faint, 18.1f),
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/**
 * `.ce-seg`: segmentado de 3 (en línea) o de 4 (`.ce-seg4`, dos por fila): fondo `#0d0d0d`, borde, 4 dp de margen interior, botón de 42 dp.
 */
@Composable
internal fun CeSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    twoColumns: Boolean = false,
    descriptions: List<String>? = null,
) {
    val outer = RoundedCornerShape(if (twoColumns) 22.dp else 24.dp)
    val inner = RoundedCornerShape(20.dp)
    val rows = if (twoColumns) options.indices.chunked(2) else listOf(options.indices.toList())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(outer)
            .background(CeColors.well, outer)
            .border(1.dp, Color.White.copy(alpha = 0.063f), outer)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val on = i == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 42.dp)
                            .clip(inner)
                            .background(if (on) EdColors.segOn else Color.Transparent, inner)
                            .clickable(role = Role.RadioButton, onClick = { onSelect(i) })
                            .semantics {
                                stateDescription = if (on) "Elegido" else "No elegido"
                                descriptions?.getOrNull(i)?.let { contentDescription = it }
                            }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            options[i],
                            style = edText(if (twoColumns) 13f else 13.5f, FontWeight.SemiBold, if (on) EdColors.ink else EdColors.faint, if (twoColumns) 15f else 17f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `.ce-cell`: una magnitud con su rótulo de 12 px y su cifra de 26 px (22 px en las celdas del inspector, [compact]) con la unidad al lado.
 * [dim] la apaga (la duración cuando manda la distancia).
 */
@Composable
internal fun CeCell(
    label: String,
    value: String,
    unit: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    compact: Boolean = false,
    background: Color = CeColors.cell,
    leadingDot: Color? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 68.dp)
            .clip(shape)
            .background(if (compact) CeColors.module else background, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = listOfNotNull(label, value, unit).joinToString(" ") }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(label, style = edText(12f, FontWeight.Normal, EdColors.muted, 15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            modifier = Modifier.alpha(if (dim) 0.4f else 1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (leadingDot != null) {
                Box(Modifier.align(Alignment.CenterVertically).size(9.dp).clip(CircleShape).background(leadingDot))
            }
            Text(
                value,
                modifier = Modifier.alignByBaseline(),
                style = edText(if (compact) 22f else 26f, FontWeight.SemiBold, EdColors.ink, if (compact) 24.2f else 28.6f, true),
                maxLines = 1,
            )
            if (!unit.isNullOrEmpty()) {
                Text(unit, modifier = Modifier.alignByBaseline(), style = edText(13.5f, FontWeight.Medium, EdColors.muted), maxLines = 1)
            }
        }
    }
}

/** `.ce-val`: el valor de una fila de ajuste, en un campo de 40 dp (70 dp de ancho mínimo). */
@Composable
internal fun CeValue(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    description: String? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 70.dp, minHeight = 48.dp)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp)
            .height(40.dp)
            .clip(shape)
            .background(CeColors.field, shape)
            .border(1.dp, CeColors.fieldBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Text(text, style = edText(15.5f, FontWeight.SemiBold, EdColors.ink, 19f, true), maxLines = 1)
    }
}

/** `.ce-r`: fila de ajuste de al menos 58 dp con su filete inferior; el contenido lo pone quien la usa. */
@Composable
internal fun CeRow(
    modifier: Modifier = Modifier,
    topRule: Boolean = false,
    bottomRule: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    /** La fila lleva un campo o un paso de 48 dp con su zona de toque: 4 dp de margen (no 6) para que no pase de los 58 dp del lienzo. */
    controlRow: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val pad = if (controlRow) 4.dp else 6.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .drawRules(topRule, bottomRule)
            // El filete del lienzo (borde de 1 dp) ocupa sitio: 1 dp arriba en la primera fila del grupo y 1 dp abajo en cada una.
            .padding(top = pad + (if (topRule) 1.dp else 0.dp), bottom = pad + (if (bottomRule) 1.dp else 0.dp)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private fun Modifier.drawRules(top: Boolean, bottom: Boolean): Modifier = this.drawBehind {
    val w = 1.dp.toPx()
    if (top) drawRect(CeColors.rule, topLeft = Offset.Zero, size = Size(size.width, w))
    if (bottom) drawRect(CeColors.rule, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
}

/** `.ce-rl`: rótulo de una fila (15 px) con su línea de ayuda de 12,5 px debajo. */
@Composable
internal fun RowScope.CeRowLabel(title: String, sub: String? = null, modifier: Modifier = Modifier, bold: Boolean = false) {
    Column(modifier = modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = edText(15f, if (bold) FontWeight.SemiBold else FontWeight.Medium, EdColors.ink, 19.5f))
        if (sub != null) Text(sub, style = edText(12.5f, FontWeight.Normal, EdColors.faint, 17.5f))
    }
}

/** `.ce-ri`: el disco de 36 dp con el icono de una fila. */
@Composable
internal fun CeRowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.051f)),
        contentAlignment = Alignment.Center,
    ) { CeIcon(icon, 20.dp, EdColors.muted) }
}

/** `.ce-sw`: el interruptor de 46 × 28 dp. */
@Composable
internal fun CeSwitch(on: Boolean, modifier: Modifier = Modifier) {
    // ce-T04 `.ce-sw` fondo 0,2 s ease · ce-T05 `.ce-sw i` left y fondo 0,2 s ease
    val track by animateKpknColorAsState(if (on) EdColors.ink else Color.White.copy(alpha = 0.133f), KpknDuration(200), KpknMotion.Suave)
    val thumb by animateKpknColorAsState(if (on) EdColors.onInk else Color(0xFFB9B5AC), KpknDuration(200), KpknMotion.Suave)
    val left by animateKpknFloatAsState(if (on) 21f else 3f, KpknDuration(200), KpknMotion.Suave)
    Box(
        modifier = modifier
            .width(46.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(left.dp.roundToPx(), 3.dp.roundToPx()) }
                .size(22.dp)
                .clip(CircleShape)
                .background(thumb),
        )
    }
}

/** Una fila con interruptor (GPS, sonidos, voz…): disco con icono, rótulo con ayuda y el interruptor. */
@Composable
internal fun CeSwitchRow(
    icon: ImageVector,
    title: String,
    sub: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topRule: Boolean = false,
) {
    CeRow(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { stateDescription = if (on && enabled) "Activado" else "Desactivado" },
        topRule = topRule,
        onClick = if (enabled) onToggle else null,
        role = Role.Switch,
    ) {
        CeRowIcon(icon)
        CeRowLabel(title, sub)
        CeSwitch(on && enabled)
    }
}

/** `.ce-lk`: fila que abre una hoja (Avisos y pantalla, Protocolo): disco con icono, rótulo con resumen y flecha. */
@Composable
internal fun CeLinkRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, modifier: Modifier = Modifier, topRule: Boolean = false) {
    CeRow(modifier = modifier, topRule = topRule, onClick = onClick) {
        CeRowIcon(icon)
        CeRowLabel(title, sub)
        CeIcon(CeIcons.Fwd, 16.dp, EdColors.faint)
    }
}

/** `.ce-step`: el paso de rondas o de series, con − y + de 44 × 40 dp en un campo de 40 dp. */
@Composable
internal fun CeStepper(
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    canMinus: Boolean,
    canPlus: Boolean,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(CeColors.field, shape)
            .border(1.dp, CeColors.fieldBorder, shape)
            .semantics(mergeDescendants = false) { contentDescription = "$label $value".trim() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(CeIcons.Minus, "Menos $label".trim(), canMinus, onMinus)
        Text(
            value.toString(),
            modifier = Modifier.defaultMinSize(minWidth = 34.dp),
            style = edText(15.5f, FontWeight.SemiBold, EdColors.ink, 19f, true),
            textAlign = TextAlign.Center,
        )
        StepperButton(CeIcons.Plus, "Más $label".trim(), canPlus, onPlus)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { CeIcon(icon, 18.dp, if (enabled) EdColors.ink else EdColors.ink.copy(alpha = 0.3f)) }
}

/** `.ce-pill`: botón de 44 dp (−15 s, +15 s); [filled] es el de tinta. */
@Composable
internal fun CePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (filled) EdColors.ink else Color.White.copy(alpha = 0.063f), shape)
            .border(1.dp, if (filled) EdColors.ink else Color.White.copy(alpha = 0.086f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = edText(13.5f, FontWeight.SemiBold, if (filled) EdColors.onInk else EdColors.ink), maxLines = 1)
    }
}

// ───────────────────────────── esfuerzo (RPE) ─────────────────────────────

/** `.ce-rpe`: el esfuerzo en diez barras con las zonas reales del cardio en vivo, y su zona debajo. Se arrastra el dedo por encima. */
@Composable
internal fun CeRpeMeter(rpe: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, title: String = "Esfuerzo (RPE)") {
    val zone = CardioZones.zoneOfRpe(rpe)
    var widthPx by remember { mutableStateOf(0) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            CeEyebrow(title)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rpe.toString(), style = edText(24f, FontWeight.Bold, EdColors.ink, 28f, true), modifier = Modifier.padding(end = 4.dp))
                Text(CardioPrescriptionFormatter.rpeAnchor(rpe), style = edText(14f, FontWeight.SemiBold, EdColors.ink, 18f))
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(50.dp)
                .onSizeChanged { widthPx = it.width }
                .semantics {
                    contentDescription = "Esfuerzo RPE $rpe de 10"
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (widthPx > 0) onChange(Math.ceil(offset.x / widthPx * 10.0).toInt().coerceIn(1, 10))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (widthPx > 0) onChange(Math.ceil(change.position.x / widthPx * 10.0).toInt().coerceIn(1, 10))
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (i in 1..10) {
                val target = if (i <= rpe) CardioZoneColors[CardioZones.zoneOfRpe(i) - 1] else Color.White.copy(alpha = 0.078f)
                // ce-T02 `.ce-meter i` fondo 0,15 s ease
                val tint by animateKpknColorAsState(target, KpknDuration(150), KpknMotion.Suave)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((14 + i * 2.4f).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint),
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(CardioZoneColors[zone - 1]))
            Text("Zona $zone · ${CardioZoneNames[zone - 1]}", style = edText(12.5f, FontWeight.Normal, EdColors.muted, 16f))
        }
    }
}

// ───────────────────────────── gráfica de bloques ─────────────────────────────

/** Un bloque de la gráfica: su tipo, su duración (ancho) y su intensidad 1–10 (alto). */
internal data class CeBar(val type: CardioBlockType, val seconds: Int, val level: Int)

/**
 * `.ce-chart`: anchura = duración, altura = intensidad (`max(16, nivel × 10) %`). El esfuerzo va en tinta, la pausa en blanco al 23 %, el calentamiento
 * y el enfriamiento al 15 %; el elegido, en el azul del cardio, y los demás al 72 %. [sparkline] es la versión de fichas y filas (2 px de hueco).
 * Con [onPick], tocar elige la barra más cercana al dedo: así las barras estrechas se eligen igual de bien.
 */
@Composable
internal fun CeBlockChart(
    bars: List<CeBar>,
    height: Dp,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onPick: ((Int) -> Unit)? = null,
    sparkline: Boolean = false,
) {
    val gap = if (sparkline) 1.5.dp else 2.dp
    val radius = if (sparkline) 2.dp else 3.dp
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val total = bars.sumOf { it.seconds.coerceAtLeast(1) }.coerceAtLeast(1)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { widthPx = it.width }
            .then(
                if (onPick != null) Modifier.pointerInput(bars) {
                    detectTapGestures { offset ->
                        if (widthPx <= 0 || bars.isEmpty()) return@detectTapGestures
                        onPick(nearestBar(bars, offset.x, widthPx.toFloat(), with(density) { gap.toPx() }))
                    }
                } else Modifier,
            ),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { i, bar ->
            val frac = (maxOf(16, bar.level * 10) / 100f).coerceIn(0.16f, 1f)
            val base = when (bar.type) {
                CardioBlockType.WORK -> EdColors.ink
                CardioBlockType.RECOVER -> Color.White.copy(alpha = 0.227f)
                CardioBlockType.WARMUP, CardioBlockType.COOLDOWN -> Color.White.copy(alpha = 0.149f)
            }
            // ce-T01 `.ce-b` opacidad y fondo 0,15 s ease
            val color by animateKpknColorAsState(if (selected == i) CeColors.col else base, KpknDuration(150), KpknMotion.Suave)
            val dim by animateKpknFloatAsState(if (selected != null && selected != i) 0.72f else 1f, KpknDuration(150), KpknMotion.Suave)
            Box(
                modifier = Modifier
                    .weight(bar.seconds.coerceAtLeast(1).toFloat() / total)
                    .fillMaxHeight(frac)
                    .alpha(dim)
                    .clip(RoundedCornerShape(radius))
                    .background(color),
            )
        }
    }
}

/** La barra más cercana a la posición [x] (px) en una gráfica de [widthPx] px con huecos de [gapPx]. */
internal fun nearestBar(bars: List<CeBar>, x: Float, widthPx: Float, gapPx: Float): Int {
    val total = bars.sumOf { it.seconds.coerceAtLeast(1) }.toFloat()
    val room = (widthPx - gapPx * (bars.size - 1)).coerceAtLeast(1f)
    var start = 0f
    var best = 0
    var bestDistance = Float.MAX_VALUE
    bars.forEachIndexed { i, bar ->
        val w = room * bar.seconds.coerceAtLeast(1) / total
        val center = start + w / 2f
        val inside = x >= start - gapPx / 2f && x <= start + w + gapPx / 2f
        val d = if (inside) 0f else kotlin.math.abs(x - center)
        if (d < bestDistance) { bestDistance = d; best = i }
        start += w + gapPx
    }
    return best
}

/** La barra plana de «Z2 30 min» en las plantillas (`.ce-flat`). */
@Composable
internal fun CeFlatBar(modifier: Modifier = Modifier, height: Dp = 30.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(0.6f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.149f)),
    )
}

// ───────────────────────────── hojas ─────────────────────────────

/**
 * `.pl-sheet` del cardio: la hoja plana `#1B1B1D` con 20 dp a los lados y [gap] entre sus bloques. Sirve a todas las hojas del cardio
 * (selector, zona, protocolo, avisos, plantillas, menú).
 */
@Composable
internal fun CeSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    maxHeightFraction: Float = 0.92f,
    stableHeightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
    if (LocalCardioSheetInline.current) {
        body()
        return
    }
    // pulido:ce-S08 .pl-sheet sombra 0 -14px 44px (la dibuja la hoja común KpknSheet) · ce-A… entrada y salida de la hoja (KpknSheet)
    com.example.kpkn.ui.components.KpknSheet(
        onDismissRequest = onDismiss,
        surfaceColor = EdColors.sheet,
        maxHeightFraction = maxHeightFraction,
        stableHeightFraction = stableHeightFraction,
    ) { body() }
}

/** Solo para pruebas: los toques no llegan dentro de `KpknSheet` en Robolectric, así que con esto la hoja se pinta en línea, sin ella. */
internal val LocalCardioSheetInline = androidx.compose.runtime.staticCompositionLocalOf { false }

/** `.ce-sh-h`: título de la hoja (Syne 19) con su línea de contexto de 13 px. */
@Composable
internal fun CeSheetHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // `h2.pl-h2` conserva el margen del navegador (0,83 em de 19 px = 15,77 arriba y abajo): en un contenedor flex no se funden con el hueco.
        Text(
            title,
            modifier = Modifier.padding(vertical = 15.77.dp),
            style = edDisplay(19f, FontWeight.Bold, EdColors.ink, -0.4f).copy(lineHeight = 21.9.sp),
        )
        if (subtitle != null) Text(subtitle, style = edText(13f, FontWeight.Normal, EdColors.faint, 18f))
    }
}

/** El botón principal de las hojas (52 dp) a todo el ancho o repartido en una fila (Cancelar · Aplicar). */
@Composable
internal fun CeSheetActions(
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (secondary != null && onSecondary != null) {
            EdPrimaryButton(secondary, onSecondary, Modifier.weight(1f), ghost = true)
        }
        EdPrimaryButton(primary, if (primaryEnabled) onPrimary else ({}), Modifier.weight(1f).alpha(if (primaryEnabled) 1f else 0.35f))
    }
}

/** Una línea de `FlowRow` centrada para los atajos de la regla (`.ce-chips`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CeChips(
    labels: List<String>,
    selected: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = selected == i
            val shape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(vertical = 4.dp)
                    .height(40.dp)
                    .clip(shape)
                    .background(if (on) EdColors.ink else Color.White.copy(alpha = 0.059f), shape)
                    .border(1.dp, if (on) EdColors.ink else Color.White.copy(alpha = 0.078f), shape)
                    .clickable(role = Role.Button, onClick = { onPick(i) })
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = edText(13.5f, FontWeight.SemiBold, if (on) EdColors.onInk else EdColors.muted, 17f, true), maxLines = 1)
            }
        }
    }
}

// ───────────────────────────── destello del cardio recién añadido ─────────────────────────────

/** Qué cardio acaba de añadirse o duplicarse (el que debe destellar), para el relleno `ce-fresh` de su fila. Lo marca el ViewModel. */
internal object CardioFreshFlash {
    val id = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun mark(exerciseId: String) { id.value = exerciseId }

    fun clear(exerciseId: String) { if (id.value == exerciseId) id.value = null }
}

/**
 * `.ce-item.fresh` (ce-A03): el relleno de la fila empieza en el azul del cardio al 15 % y se funde a transparente en 1,6 s con `ease-out`
 * (familia `destello`, valores exactos de la fila). Solo la fila marcada en [CardioFreshFlash] lo hace, una vez.
 */
@Composable
internal fun Modifier.cardioFreshFlash(exerciseId: String): Modifier {
    val current by CardioFreshFlash.id.collectAsState()
    if (current != exerciseId) return this
    val animation = remember { KpknFamilies.Destello.demo.copy(duration = KpknDuration(1600), easing = KpknMotion.Salida) }
    val state = rememberKpknPose(animation, key = exerciseId)
    LaunchedEffect(exerciseId) {
        kotlinx.coroutines.delay(1700)
        CardioFreshFlash.clear(exerciseId)
    }
    return this.drawBehind {
        val mix = state.pose()[KpknChannel.ColorMix]
        drawRect(kpknCssColorLerp(CeColors.col.copy(alpha = 0.149f), Color.Transparent, mix))
    }
}

/** Avisos breves de la tarjeta («Plantilla aplicada: Tabata.», «Bloque duplicado.»…). El editor los recoge y los saca en su snackbar; sin editor, caen al vacío. */
internal object CardioMessages {
    val flow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun post(message: String) { flow.tryEmit(message) }
}
