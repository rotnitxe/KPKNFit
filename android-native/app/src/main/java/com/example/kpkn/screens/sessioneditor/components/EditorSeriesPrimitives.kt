package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknGradientArgs
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknPose
import com.example.kpkn.ui.design.motion.rememberKpknClockMs
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Piezas del editor de series (Pulido v1 · paquete EDITOR · pasada A1). Todo sale del lienzo `ed.css` / `scenes-ed.js`:
 * las medidas están en dp y los textos en sp (escalan con la letra, como `calc(Npx*var(--f))` del lienzo).
 * Sin sombras, vidrio ni animaciones: son de la pasada posterior y cada sitio lleva su marca `// pulido:ed-…`.
 */

/** Paleta del editor: los tokens `--ink`, `--muted`, `--faint`… del lienzo y los grises de los campos. */
internal object EdColors {
    val ink = Color(0xFFF2EEE6)
    val muted = Color(0xFFC4BFB6)
    val faint = Color(0xFF8E8980)
    val dim = Color(0xFF5F5C57)
    val onInk = Color(0xFF111111)
    val field = Color(0xFF1A1A1A)
    val well = Color(0xFF0D0D0D)
    val segOn = Color(0xFF272727)
    val sheet = Color(0xFF1B1B1D)
    val mus = Color(0xFFF49A6E)
    val rest = Color(0xFFC9B8FF)
    val danger = Color(0xFFFF9B92)

    /** `#ffffff12`: filete de los campos. */
    val line = Color.White.copy(alpha = 0.071f)

    /** `#ffffff14`. */
    val line2 = Color.White.copy(alpha = 0.078f)

    /** `#ffffff1a`. */
    val line3 = Color.White.copy(alpha = 0.102f)

    /** `#ffffff2e`: borde punteado. */
    val dashed = Color.White.copy(alpha = 0.18f)

    /** `#f2eee699`: borde del control abierto. */
    val open = ink.copy(alpha = 0.6f)
}

private val tabular = "'tnum' 1, 'lnum' 1"

/** Texto de lectura del editor (Inter). */
internal fun edText(size: Float, weight: FontWeight = FontWeight.SemiBold, color: Color = EdColors.ink, lineHeight: Float? = null, tabularNums: Boolean = false) =
    TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (lineHeight ?: (size * 1.25f)).sp,
        color = color,
        fontFeatureSettings = if (tabularNums) tabular else null,
    )

/** Cifra grande de marca (Syne). */
internal fun edDisplay(size: Float, weight: FontWeight = FontWeight.Bold, color: Color = EdColors.ink, letterSpacing: Float = 0f) =
    TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = size.sp,
        color = color,
        letterSpacing = letterSpacing.sp,
        fontFeatureSettings = "'lnum' 1",
    )

/** `.ed-eyebrow`: 11 px, 600, 1,3 de espaciado, mayúsculas, `--faint`. */
internal val EdEyebrowStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 13.2.sp,
    letterSpacing = 1.3.sp,
    color = EdColors.faint,
)

// ───────────────────────────── iconos del editor ─────────────────────────────

/** Iconos que el lienzo dibuja en el editor y el set KPKN Line aún no tiene (trazos de `scenes-ed.js`, retícula de 24). */
internal object EdIcons {
    val ArrowUp: ImageVector by lazy { icon("EdArrowUp", "M12 19V5M6.5 10.5L12 5l5.5 5.5") }
    val ArrowDown: ImageVector by lazy { icon("EdArrowDown", "M12 5v14M6.5 13.5L12 19l5.5-5.5") }
    val CopyAll: ImageVector by lazy { icon("EdCopyAll", "M8.5 8.5h11.5v11.5H8.5z", "M5 15.5V7a3 3 0 013-3h8.5") }
    val Dupe: ImageVector by lazy { icon("EdDupe", "M4 9h11v11H4z", "M9 5.5A3 3 0 0112 4h5a3 3 0 013 3v5a3 3 0 01-1.5 2.6") }
    val SideLR: ImageVector by lazy { icon("EdSideLR", "M4 8h6M4 16h6M14 8h6M14 16h6") }
    val Rom: ImageVector by lazy { icon("EdRom", "M5 19L19 5M5 19h7M5 19v-7") }
    val Target2: ImageVector by lazy { icon("EdTarget2", "M12 4a8 8 0 100 16 8 8 0 000-16z", "M12 8.5a3.5 3.5 0 100 7 3.5 3.5 0 000-7z") }
    val Ruler2: ImageVector by lazy { icon("EdRuler2", "M5 8h14a2 2 0 012 2v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4a2 2 0 012-2z", "M7 8v3M11 8v4M15 8v3M19 8v3") }
    val Bolt: ImageVector by lazy { icon("EdBolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z") }
    val Star: ImageVector by lazy { icon("EdStar", "M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z") }
    val Play: ImageVector by lazy { icon("EdPlay", "M8 5.5v13l10.5-6.5z") }

    private fun icon(name: String, vararg paths: String): ImageVector {
        val ink = SolidColor(Color.Black)
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = ink,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}

/** Icono de línea del editor con el tamaño y la tinta indicados (sin descripción: lo describe el botón). */
@Composable
internal fun EdIcon(icon: ImageVector, size: Dp = 20.dp, tint: Color = EdColors.muted, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

// ───────────────────────────── bordes y fila flexible ─────────────────────────────

/** Borde punteado de CSS (`border: 1.5px dashed`): trazo redondeado por dentro del borde, con el radio dado. */
internal fun Modifier.edDashedBorder(
    color: Color,
    radius: Dp,
    width: Dp = 1.dp,
    dash: Dp = 4.dp,
    gap: Dp = 3.dp,
): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
    )
}

/** Datos de un hijo de [EdFlexRow]: `flex-grow: 1` y `margin-left: auto`. */
internal data class EdFlexData(val grow: Boolean, val autoStart: Boolean)

private class EdFlexModifier(val data: EdFlexData) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = data
}

internal fun Modifier.edFlex(grow: Boolean = false, autoStart: Boolean = false): Modifier = this.then(EdFlexModifier(EdFlexData(grow, autoStart)))

/**
 * `display: flex; flex-wrap: wrap` del lienzo: los hijos se colocan en línea con su ancho natural y pasan a la siguiente si no caben.
 * El espacio que sobra se reparte a partes iguales entre los que llevan [edFlex]`(grow = true)`; si ninguno crece, lo absorbe el
 * margen automático del que lleve `autoStart` (empuja a ese hijo y a los siguientes al final de la línea). Alineación vertical al centro.
 */
@Composable
internal fun EdFlexRow(
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    lineGap: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val lineGapPx = lineGap.roundToPx()
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE / 4
        val natural = measurables.map { m -> m.maxIntrinsicWidth(Int.MAX_VALUE).coerceIn(0, maxW) }
        val lines = mutableListOf<MutableList<Int>>()
        var current = mutableListOf<Int>()
        var currentW = 0
        natural.forEachIndexed { i, w ->
            val needed = if (current.isEmpty()) w else gapPx + w
            if (current.isNotEmpty() && currentW + needed > maxW) {
                lines += current
                current = mutableListOf()
                currentW = 0
            }
            currentW += if (current.isEmpty()) w else gapPx + w
            current += i
        }
        if (current.isNotEmpty()) lines += current

        val widths = natural.toIntArray()
        val leading = IntArray(measurables.size)
        lines.forEach { line ->
            val used = line.sumOf { natural[it] } + gapPx * (line.size - 1)
            val free = maxW - used
            if (free > 0) {
                val growers = line.filter { (measurables[it].parentData as? EdFlexData)?.grow == true }
                if (growers.isNotEmpty()) {
                    val share = free / growers.size
                    growers.forEachIndexed { n, idx ->
                        widths[idx] = natural[idx] + share + if (n == 0) free - share * growers.size else 0
                    }
                } else {
                    val auto = line.firstOrNull { (measurables[it].parentData as? EdFlexData)?.autoStart == true }
                    if (auto != null) leading[auto] = free
                }
            }
        }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints(minWidth = widths[i], maxWidth = max(widths[i], 0), minHeight = 0, maxHeight = Constraints.Infinity))
        }
        val lineHeights = lines.map { line -> line.maxOf { placeables[it].height } }
        val totalH = lineHeights.sum() + lineGapPx * (lines.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else lines.maxOfOrNull { l -> l.sumOf { placeables[it].width + leading[it] } + gapPx * (l.size - 1) } ?: 0
        layout(width, totalH.coerceIn(constraints.minHeight, max(constraints.minHeight, constraints.maxHeight))) {
            var y = 0
            lines.forEachIndexed { li, line ->
                var x = 0
                line.forEachIndexed { n, idx ->
                    x += leading[idx]
                    val p = placeables[idx]
                    p.placeRelative(x, y + (lineHeights[li] - p.height) / 2)
                    x += p.width + gapPx
                }
                y += lineHeights[li] + lineGapPx
            }
        }
    }
}

// ───────────────────────────── secciones y campos ─────────────────────────────

/** `.ed-sec`: un dato de la serie con su rótulo (`.ed-eyebrow`) y la frase corta a la derecha (`.hint`). */
@Composable
internal fun EdSection(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Separación entre el rótulo y el campo (6 dp; 8 en el descanso: `.ed-rest`). */
    gap: Dp = 6.dp,
    /** Alto mínimo de la línea del rótulo (22 dp en `.ed-sech`; 24 con «Igual que el ejercicio»; 0 en las hojas, que llevan solo el rótulo). */
    headerMin: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = headerMin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label.uppercase(), style = EdEyebrowStyle, maxLines = 1)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (trailing != null) trailing()
                else if (hint != null) Text(hint, style = edText(12f, FontWeight.Medium, EdColors.faint, 15.6f), textAlign = TextAlign.End)
            }
        }
        content()
    }
}

/** Botón − o + de 48 dp (40 en la variante de dos campos) de un campo numérico. */
@Composable
private fun EdStepButton(
    icon: ImageVector,
    description: String,
    width: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    text: String? = null,
) {
    val (source, pressed) = rememberEdPressed()
    // pulido:ed-T28 .ed-stb fondo y color 0,12 s ease al pulsar (`:active{background:#ffffff12;color:var(--ink)}`)
    val background = edTransitionColor(if (pressed && enabled) Color.White.copy(alpha = 0.071f) else Color.White.copy(alpha = 0f), 120)
    val tint = edTransitionColor(if (!enabled) EdColors.dim else if (pressed) EdColors.ink else EdColors.muted, 120)
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = 48.dp)
            .background(background)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(text, style = edText(12.5f, FontWeight.SemiBold, tint), maxLines = 1)
        } else {
            EdIcon(icon, 20.dp, tint)
        }
    }
}

/**
 * `.ed-st`: campo de al menos 50 dp con − y + y el valor en medio. Se apila cuando el ancho o la escala de fuente lo requieren; el
 * valor se arrastra de lado a lado (un paso cada 16 dp, con un tic) y, si [keyboard] no es nulo, tocarlo lo edita con el teclado.
 * [leading] es la unidad integrada (intensidad); [centerContent] sustituye el valor (descanso, «Hasta el fallo»).
 */
@Composable
internal fun EdStepperField(
    text: String,
    label: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    small: String? = null,
    compact: Boolean = false,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
    keyboard: KeyboardType? = KeyboardType.Number,
    onCommitText: ((String) -> Unit)? = null,
    minusDescription: String = "Menos",
    plusDescription: String = "Más",
    minusText: String? = null,
    plusText: String? = null,
    stepWidth: Dp = if (compact) 40.dp else 48.dp,
    height: Dp = 50.dp,
    highlighted: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    centerContent: (@Composable () -> Unit)? = null,
    onCenterClick: (() -> Unit)? = null,
    scrubbable: Boolean = true,
    /** Sin − ni + (el valor «Hasta el fallo» de la intensidad). */
    showSteppers: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentStep by rememberUpdatedState(onStep)
    // pulido:ed-A15 / ed-J06 `stepField`: la cifra rebota 0,24 s (`.ed-stv.bump b`) con cada paso o arrastre, no al aparecer
    val bumps = remember { mutableIntStateOf(0) }
    val stepAndBump: (Int) -> Unit = { direction ->
        bumps.intValue += 1
        currentStep(direction)
    }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue()) }
    var sawFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val editable = keyboard != null && onCommitText != null && centerContent == null
    fun commit() {
        if (!editing) return
        editing = false
        onCommitText?.invoke(draft.text)
    }
    androidx.compose.runtime.LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    // pulido:ed-T27 .ed-st borde 0,16 s ease (.edit = #f2eee6aa)
    val borderColor = edTransitionColor(if (highlighted || editing) EdColors.ink.copy(alpha = 0.667f) else EdColors.line, 160)
    val currentText = if (editing) draft.text else text
    val textSize = if (compact) 19f else 20f
    val estimatedTextWidth = currentText.length * textSize * 0.56f * density.fontScale
    val trailingText = if (compact && !editing) null else unit ?: small
    val estimatedTrailingWidth = (trailingText?.length ?: 0) * 6.5f * density.fontScale
    val estimatedCenterWidth = estimatedTextWidth + estimatedTrailingWidth +
        (if (trailingText != null) 6f else 0f) + (if (centerContent != null) 28f else 0f)
    // El selector de intensidad mide 96 dp con su divisor; reserva su ancho, los dos controles y su separación antes de apilar.
    val leadingWidth = if (leading == null) 0f else 105f
    val requiredInlineWidth = estimatedCenterWidth + leadingWidth +
        (if (showSteppers) max(stepWidth.value, 48f) * 2f else 0f) + 8f
    EdBumpProvider(bumps.intValue) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val wrap = showSteppers &&
                // A 1,5×, los controles siguen bajo el valor en filas compactas; para otros casos basta el cálculo de contenido.
                ((density.fontScale >= 1.5f && maxWidth < 360.dp) || maxWidth.value < requiredInlineWidth)
            @Composable
            fun CenterContents(centerModifier: Modifier, fillHeight: Boolean = true) {
                Box(
                    modifier = centerModifier
                        .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                        .then(
                            if (scrubbable && !editing) Modifier.pointerInput(Unit) {
                                // pulido:ed-J (arrastrar el valor) un paso cada 16 dp, `moved` bloquea el clic siguiente
                                val stepPx = 16.dp.toPx()
                                var accumulated = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulated = 0f },
                                    onDragEnd = { accumulated = 0f },
                                    onDragCancel = { accumulated = 0f },
                                ) { change, dragAmount ->
                                    change.consume()
                                    accumulated += dragAmount
                                    while (abs(accumulated) >= stepPx) {
                                        val direction = if (accumulated > 0f) 1 else -1
                                        accumulated -= direction * stepPx
                                        stepAndBump(direction)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            } else Modifier,
                        )
                        .then(
                            if (onCenterClick != null) Modifier.clickable(role = Role.Button, onClick = onCenterClick)
                            else if (editable && !editing) Modifier.clickable(role = Role.Button, onClickLabel = "Editar") {
                                draft = TextFieldValue(text = text, selection = TextRange(0, text.length))
                                sawFocus = false
                                editing = true
                            } else Modifier,
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = label
                            stateDescription = text + (unit?.let { " $it" } ?: "")
                            customActions = listOf(
                                CustomAccessibilityAction("Aumentar") { stepAndBump(1); true },
                                CustomAccessibilityAction("Disminuir") { stepAndBump(-1); true },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (centerContent != null) {
                        centerContent()
                    } else if (editing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                                    .focusRequester(focus)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) sawFocus = true else if (sawFocus) commit()
                                    },
                                textStyle = edText(textSize, FontWeight.SemiBold, EdColors.ink, tabularNums = true).copy(textAlign = TextAlign.Center),
                                cursorBrush = SolidColor(EdColors.ink),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = keyboard ?: KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commit() }),
                            )
                            val extra = unit ?: small
                            if (extra != null) {
                                Text(
                                    extra,
                                    style = edText(if (compact) 11f else 12f, FontWeight.Medium, EdColors.muted),
                                    maxLines = 1,
                                )
                            }
                        }
                    } else if (compact) {
                        // .ed-st2 .ed-stv: columna, cifra de 19 px y rótulo de 11 px sin interlineado
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text, modifier = Modifier.edBump(), style = edText(19f, FontWeight.SemiBold, EdColors.ink, 22f, true), maxLines = 1)
                            if (small != null) Text(small, style = edText(11f, FontWeight.Medium, EdColors.muted, 11f), maxLines = 1)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text,
                                modifier = Modifier.weight(1f, fill = false).edBump(),
                                style = edText(20f, FontWeight.SemiBold, EdColors.ink, 24f, true),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                            val extra = unit ?: small
                            if (extra != null) Text(extra, style = edText(12f, FontWeight.Medium, EdColors.muted, 14f), maxLines = 1)
                        }
                    }
                }
            }

            val shell = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(EdColors.field, shape)
                .border(1.dp, borderColor, shape)
            if (wrap) {
                Column(modifier = shell, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = height),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leading?.invoke(this)
                        CenterContents(Modifier.weight(1f), fillHeight = false)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(EdColors.line2))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EdStepButton(KpknLineIcons.Minus, minusDescription, stepWidth, canDecrement, { stepAndBump(-1) }, minusText)
                        EdStepButton(KpknLineIcons.Plus, plusDescription, stepWidth, canIncrement, { stepAndBump(1) }, plusText)
                    }
                }
            } else {
                Row(
                    modifier = shell.heightIn(min = height),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading?.invoke(this)
                    if (showSteppers) EdStepButton(KpknLineIcons.Minus, minusDescription, stepWidth, canDecrement, { stepAndBump(-1) }, minusText)
                    CenterContents(Modifier.weight(1f))
                    if (showSteppers) EdStepButton(KpknLineIcons.Plus, plusDescription, stepWidth, canIncrement, { stepAndBump(1) }, plusText)
                }
            }
        }
    }
}

/** `.ed-ghost`: botón de borde punteado a todo el ancho («Añadir intensidad»). */
@Composable
internal fun EdGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = KpknLineIcons.Plus) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .edDashedBorder(EdColors.dashed, 16.dp, 1.5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdIcon(icon, 18.dp, EdColors.muted)
        Text(text, style = edText(14f, FontWeight.SemiBold, EdColors.muted), maxLines = 1)
    }
}

/** `.ed-chip`: píldora de 44 dp (Modo, descanso, lados…). */
@Composable
internal fun EdChip(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    iconOnly: Boolean = false,
    on: Boolean = false,
    description: String? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    // pulido:ed-T08 .ed-chip borde y fondo 0,16 s ease (`.on{border-color:#f2eee699}`)
    val borderColor = edTransitionColor(if (on) EdColors.open else EdColors.line, 160)
    val fill = edTransitionColor(EdColors.field, 160)
    Row(
        modifier = modifier
            .height(44.dp)
            .then(if (iconOnly) Modifier.width(44.dp) else Modifier)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = if (iconOnly) 0.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, if (iconOnly) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) EdIcon(icon, if (iconOnly) 20.dp else 16.dp, EdColors.muted)
        if (text != null) Text(text, style = edText(13f, FontWeight.SemiBold, EdColors.ink, 16f, true), maxLines = 1)
        if (trailingIcon != null) EdIcon(trailingIcon, 14.dp, EdColors.muted)
    }
}

/** `.ed-sm`: conmutador pequeño de dos opciones («Fijas | Rango»). */
@Composable
internal fun EdMiniSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(EdColors.well, shape)
            .border(1.dp, EdColors.line, shape)
            // `.ed-sm`: borde de 1 px por fuera de los 2 px de aire: la caja mide 38 dp (32 + 2 + 2 + 2)
            .padding(3.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            // pulido:ed-T26 .ed-sm button fondo y color 0,18 s ease
            val fill = edTransitionColor(if (on) EdColors.segOn else EdColors.segOn.copy(alpha = 0f), 180)
            val ink = edTransitionColor(if (on) EdColors.ink else EdColors.faint, 180)
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .defaultMinSize(minWidth = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(fill)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = edText(12.5f, FontWeight.SemiBold, ink), maxLines = 1)
            }
        }
    }
}

/** `.ed-presets button`: atajo de 40 dp (0:30, 1:00…). [on] lo rellena de tinta. */
@Composable
internal fun EdPresetButton(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dashed: Boolean = false, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(20.dp)
    // pulido:ed-T20 .ed-presets button fondo y color 0,16 s ease (el filete cambia al instante: `.on{border-color:transparent}`)
    val fill = edTransitionColor(if (on) EdColors.ink else EdColors.field, 160)
    val textColor = edTransitionColor(if (on) EdColors.onInk else if (dashed) EdColors.muted else EdColors.ink, 160)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .then(
                if (dashed) Modifier.edDashedBorder(EdColors.dashed.copy(alpha = 0.2f), 20.dp, 1.dp)
                else Modifier.background(fill, shape).then(if (on) Modifier else Modifier.border(1.dp, EdColors.line, shape)),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) EdIcon(icon, 14.dp, EdColors.muted)
        Text(
            text,
            style = edText(13f, FontWeight.SemiBold, textColor, 16f, true),
            maxLines = 1,
        )
    }
}

/** Fila de números redondos (1 · 2 · 3, 3 · 4 · 5, 10 s…) de la configuración de una técnica: 44 dp, 48 de ancho mínimo. */
@Composable
internal fun EdNumberPill(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .defaultMinSize(minWidth = 48.dp)
            .clip(shape)
            .background(if (on) EdColors.ink else EdColors.field, shape)
            .then(if (on) Modifier else Modifier.border(1.dp, EdColors.line, shape))
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = edText(13f, FontWeight.SemiBold, if (on) EdColors.onInk else EdColors.ink, 16f, true), maxLines = 1)
    }
}

/** `.ed-act`: botón de acción con forma de píldora (44 dp). */
@Composable
internal fun EdActionButton(
    text: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconOnly: Boolean = false,
    expanded: Boolean = false,
    color: Color = EdColors.ink,
    iconTint: Color = EdColors.muted,
) {
    val shape = RoundedCornerShape(22.dp)
    val (source, pressed) = rememberEdPressed()
    // pulido:ed-T01 .ed-act fondo 0,16 s ease (`#ffffff0d` → `:active{background:#ffffff1c}`); el filete abierto cambia al instante
    val fill = edTransitionColor(Color.White.copy(alpha = if (pressed) 0.110f else 0.051f), 160)
    Row(
        modifier = modifier
            .height(44.dp)
            .then(if (iconOnly) Modifier.width(44.dp) else Modifier)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, if (expanded) EdColors.open else EdColors.line3, shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = if (iconOnly) 0.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) EdIcon(icon, if (iconOnly) 20.dp else 16.dp, iconTint)
        if (text != null) Text(text, style = edText(13f, FontWeight.SemiBold, color), maxLines = 1)
    }
}

/** `.ed-pb`: botón principal de las hojas (52 dp, tinta). [ghost] es el secundario. */
@Composable
internal fun EdPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, ghost: Boolean = false) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(if (ghost) Color.White.copy(alpha = 0.071f) else EdColors.ink, shape)
            .then(if (ghost) Modifier.border(1.dp, Color.White.copy(alpha = 0.141f), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = edText(15f, FontWeight.SemiBold, if (ghost) EdColors.ink else EdColors.onInk), maxLines = 1)
    }
}

/** `.ed-lnkb`: acción de texto con icono de 44 dp («Probar cronómetro»). */
@Composable
internal fun EdTextLink(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(44.dp).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdIcon(icon, 16.dp, EdColors.ink)
        Text(text, style = edText(13.5f, FontWeight.SemiBold, EdColors.ink), maxLines = 1)
    }
}

/** `.ed-tx2`: línea de ayuda de 12,5 px. */
@Composable
internal fun EdHelpText(text: String, modifier: Modifier = Modifier, color: Color = EdColors.muted) {
    Text(text, modifier = modifier, style = edText(12.5f, FontWeight.Medium, color, 17.5f))
}

/** `.ed-slider` de %RM y RPE: pista de 4 dp con la parte llena en tinta y pulgar de 24 dp. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EdSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "Deslizador de $description" },
            thumb = {
                // pulido:ed-S06 .ed-range::-webkit-slider-thumb sombra 0 2px 8px #000a (preset asa-deslizador, valor de la fila)
                Box(
                    Modifier
                        .size(24.dp)
                        .kpknShadow(KpknShadow.AsaDeslizador, CircleShape)
                        .background(if (enabled) EdColors.ink else EdColors.faint, CircleShape),
                )
            },
            track = { state: SliderState ->
                val span = (state.valueRange.endInclusive - state.valueRange.start).coerceAtLeast(0.0001f)
                val fraction = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
                // pulido:ed-G01 .ed-range::-webkit-slider-runnable-track linear-gradient(90deg, tinta p, #ffffff1f p): parada dura al valor
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .kpknGradient(
                            KpknGradient.RellenoHorizontalCrema1,
                            RoundedCornerShape(2.dp),
                            KpknGradientArgs(ink = if (enabled) EdColors.ink else EdColors.faint, progress = fraction),
                        ),
                )
            },
        )
        Row(Modifier.fillMaxWidth().offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = edText(11f, FontWeight.Medium, EdColors.faint, 13f))
            Text(endLabel, style = edText(11f, FontWeight.Medium, EdColors.faint, 13f))
        }
    }
}

// ───────────────────────────── menús y hojas ─────────────────────────────

/** `.ed-mi`: fila de menú con título, línea de ayuda, icono y marca de elegida. */
@Composable
internal fun EdMenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
) {
    val color = if (danger) EdColors.danger else EdColors.ink
    val (source, pressed) = rememberEdPressed()
    // pulido:ed-T19 .ed-mi fondo 0,14 s ease al pulsar (`:active{background:#ffffff12}`)
    val fill = edTransitionColor(Color.White.copy(alpha = if (pressed && enabled) 0.071f else 0f), 140)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (enabled) Modifier else Modifier.background(Color.Transparent)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (enabled) 1f else 0.4f
        if (icon != null) EdIcon(icon, 20.dp, (if (danger) EdColors.danger else EdColors.muted).copy(alpha = alpha))
        Column(Modifier.weight(1f)) {
            Text(title, style = edText(14.5f, FontWeight.SemiBold, color.copy(alpha = alpha), 18.1f))
            if (subtitle != null) Text(subtitle, style = edText(12f, FontWeight.Medium, EdColors.faint.copy(alpha = alpha), 16.2f))
        }
        if (checked) EdIcon(KpknLineIcons.Check, 18.dp, EdColors.ink)
    }
}

/** `.ed-mdiv`: filete entre grupos de un menú. */
@Composable
internal fun EdMenuDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).height(1.dp).background(Color.White.copy(alpha = 0.071f)))
}

/**
 * `.ed-pop`: menú anclado de 20 dp de radio sobre #1B1B1D con filete (carga, unidad, acciones de la serie). Se abre debajo del ancla
 * alineado a su borde izquierdo o, si no cabe (la barra de abajo ocupa 100 dp), encima; tocar fuera lo cierra. Vive en un [KpknPortal].
 */
@Composable
internal fun EdAnchoredMenu(
    anchor: Rect,
    width: Dp,
    onDismiss: () -> Unit,
    /** Alto que el lienzo da por hecho al decidir si abre debajo o encima (carga 300, unidad 230, acciones 200). */
    estimatedHeight: Dp = 230.dp,
    alignEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val reduced = wizardReducedMotion()
    // pulido:ed-A08 .ed-pop `ed-popin` 0,2 s --ed-ease (opacidad 0→1, escala .92→1, desplazamiento de 6 dp desde el ancla)
    val popClock = rememberKpknClockMs(running = !reduced, key = "ed-pop", endMs = EdAnimations.PopInDown.endMs)
    val popPose = remember { KpknPose() }
    KpknPortal {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .onGloballyPositioned { origin = it.positionInWindow() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Cerrar el menú" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDismiss),
            )
            val start = origin
            if (start != null) {
                val shape = RoundedCornerShape(20.dp)
                Layout(
                    modifier = Modifier.fillMaxSize(),
                    content = {
                        // pulido:ed-S05 .ed-pop sombra 0 18px 44px #000d (preset popover); va antes del recorte para que no se corte
                        Column(
                            modifier = Modifier
                                .width(width)
                                .kpknShadow(KpknShadow.Popover, shape)
                                .clip(shape)
                                .background(EdColors.sheet, shape)
                                .border(1.dp, Color.White.copy(alpha = 0.110f), shape)
                                .padding(6.dp),
                            content = content,
                        )
                    },
                ) { measurables, constraints ->
                    val menu = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
                    val edge = with(density) { 12.dp.roundToPx() }
                    val gap = with(density) { 8.dp.roundToPx() }
                    val bar = with(density) { 100.dp.roundToPx() }
                    val relLeft = anchor.left - start.x
                    val relRight = anchor.right - start.x
                    val relTop = anchor.top - start.y
                    val relBottom = anchor.bottom - start.y
                    val x = (if (alignEnd) relRight - menu.width else relLeft).roundToInt()
                        .coerceIn(edge, max(edge, constraints.maxWidth - edge - menu.width))
                    val below = relBottom + gap
                    // Lienzo: `flip = a.b + est > H − 100` (la barra de abajo ocupa 100 px), con el alto estimado de cada menú.
                    val flip = relBottom + estimatedHeight.roundToPx() > constraints.maxHeight - bar
                    val y = if (!flip) below.roundToInt()
                    else (relTop - gap - menu.height).roundToInt().coerceAtLeast(edge)
                    val originX = (anchor.center.x - start.x - x).coerceIn(20.dp.toPx(), (menu.width - 20.dp.toPx()).coerceAtLeast(20.dp.toPx()))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        menu.placeWithLayer(IntOffset(x, y)) {
                            // `.ed-pop`: transform-origin `--ox` `--oy` (arriba si abre debajo, abajo si abre encima) y `--dy` ∓6 dp
                            (if (flip) EdAnimations.PopInUp else EdAnimations.PopInDown).poseAt(popClock.value, popPose, reduced)
                            alpha = popPose.alpha
                            scaleX = popPose.scaleX
                            scaleY = popPose.scaleY
                            translationY = popPose.translateYDp * this.density
                            transformOrigin = TransformOrigin(originX / menu.width.coerceAtLeast(1), if (flip) 1f else 0f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                    }
                }
            }
        }
    }
}

/** Ancla de un menú: guarda el rectángulo del elemento en la ventana. */
internal fun Modifier.edAnchor(onBounds: (Rect) -> Unit): Modifier = this.onGloballyPositioned { onBounds(it.boundsInWindow()) }

/** Margen negativo horizontal (`margin: 0 -8px` de `.ed-modegrid`): el contenido ocupa [amount] más a cada lado del que le deja el padre. */
internal fun Modifier.edBleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * extra else constraints.minWidth,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * extra else constraints.maxWidth,
        ),
    )
    layout(constraints.maxWidth.coerceAtMost(placeable.width), placeable.height) { placeable.placeRelative(-extra, 0) }
}

/** `h3` + `p` de una hoja del editor. */
@Composable
internal fun EdSheetHeader(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = edDisplay(20f, FontWeight.Bold, EdColors.ink, -0.4f).copy(lineHeight = 23.sp))
        if (subtitle != null) Text(subtitle, style = edText(13.5f, FontWeight.Normal, EdColors.muted, 19.6f))
    }
}
