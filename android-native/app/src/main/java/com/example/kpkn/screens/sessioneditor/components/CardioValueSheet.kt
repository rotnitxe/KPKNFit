package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.cardio.CardioValueKind
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.kpknPulse
import com.example.kpkn.ui.design.motion.kpknTween
import com.example.kpkn.ui.design.motion.rememberKpknPulse
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pulido v1 · CARDIO DEL EDITOR · punto 8: el selector único de duración, tiempos de fase, distancia, velocidad, inclinación, cadencia,
 * vatios y ritmo (`ceSelSheet` de `scenes-ce.js`). Sustituye a las ruedas «Min», «Min : s», «Seg», «Km» y «Décimas»: una cifra grande tocable
 * (escribe con el teclado del sistema), una regla que se arrastra, «−» y «+», atajos y Cancelar / Aplicar.
 */

/** Los tres modos del calentamiento y el enfriamiento (el resto de magnitudes solo tiene [VALUE]). */
internal enum class CardioValueMode { VALUE, FREE, OFF }

@Composable
internal fun CardioValueSheet(
    kind: CardioValueKind,
    subtitle: String,
    initialN: Int,
    onApply: (n: Int, mode: CardioValueMode) -> Unit,
    onDismiss: () -> Unit,
    initialMode: CardioValueMode = CardioValueMode.VALUE,
    /** Abre ya en el estado «escribiendo» (las hojas de render lo piden). */
    startTyping: Boolean = false,
    startBuffer: String = "",
) {
    var n by remember { mutableStateOf(kind.clampN(initialN)) }
    var mode by remember { mutableStateOf(initialMode) }
    var typing by remember { mutableStateOf(startTyping) }
    var buffer by remember { mutableStateOf(startBuffer) }
    val parsed = if (typing) kind.parseTyped(buffer) else null
    val error = typing && parsed != null && !kind.isInRange(parsed)
    val off = kind.hasModes && mode != CardioValueMode.VALUE
    val focus = remember { FocusRequester() }
    LaunchedEffect(typing) { if (typing) runCatching { focus.requestFocus() } }

    fun finishTyping() {
        val value = kind.parseTyped(buffer)
        if (value != null && kind.isInRange(value)) {
            n = value
            if (kind.hasModes) mode = CardioValueMode.VALUE
        }
        typing = false
    }

    CeSheet(onDismiss = onDismiss, gap = 14.dp) {
        CeSheetHeader(kind.title, subtitle)
        if (kind.hasModes) {
            CeSegmented(
                options = listOf("Con duración", "Libre", "Omitir"),
                selected = mode.ordinal,
                onSelect = { i ->
                    mode = CardioValueMode.entries[i]
                    if (mode == CardioValueMode.VALUE && n == 0) n = kind.shortcuts.first()
                },
            )
        }
        if (typing) {
            TypingReadout(kind = kind, buffer = buffer, n = n, error = error, onBuffer = {
                buffer = it.filter { c -> c.isDigit() || (kind.typedDecimal && (c == ',' || c == '.')) }
                    .let { f -> if (f.length > kind.maxTypedDigits + (if (kind.typedDecimal) 1 else 0)) buffer else f }
                val v = kind.parseTyped(buffer)
                if (v != null && kind.isInRange(v)) n = v
            }, onDone = ::finishTyping, focus = focus)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(104.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundStep(CeIcons.Minus, "Menos", enabled = !off && n > kind.min) { n = kind.clampN(n - 1) }
                BigReadout(
                    kind = kind,
                    n = n,
                    off = off,
                    mode = mode,
                    onTap = { if (!off) { typing = true; buffer = "" } },
                    modifier = Modifier.weight(1f),
                )
                RoundStep(CeIcons.Plus, "Más", enabled = !off && n < kind.max) { n = kind.clampN(n + 1) }
            }
        }
        val hint = when {
            error -> kind.maxMessage
            off -> if (mode == CardioValueMode.FREE) "Sin tiempo fijo: lo cierras tú en el entreno." else "No habrá ${kind.title.lowercase()}."
            typing -> "Escribe el valor y toca Listo."
            else -> kind.hint(n).ifEmpty { "Arrastra la regla, usa − y + o toca la cifra para escribir." }
        }
        // El texto del lienzo (12,5 px) no cabe en una línea con los 320 de la hoja; 4 dp a cada lado lo hacen romper igual.
        CeHint(hint, Modifier.fillMaxWidth().padding(horizontal = 4.dp), center = true, error = error)
        if (!typing) {
            Column(Modifier.alpha(if (off) 0.32f else 1f)) {
                CardioRuler(
                    kind = kind,
                    n = n,
                    enabled = !off,
                    onN = { n = it; if (kind.hasModes) mode = CardioValueMode.VALUE },
                )
                val shortcuts = listOfNotNull(kind.noneN) + kind.shortcuts
                CeChips(
                    labels = shortcuts.map { kind.shortcutLabel(it) },
                    selected = shortcuts.indexOf(n).takeIf { it >= 0 && !off },
                    onPick = { i ->
                        if (!off) {
                            n = shortcuts[i]
                            if (kind.hasModes) mode = CardioValueMode.VALUE
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 300.dp).align(Alignment.CenterHorizontally),
                )
            }
            CeSheetActions(
                primary = "Aplicar",
                onPrimary = { onApply(n, mode) },
                secondary = "Cancelar",
                onSecondary = onDismiss,
            )
        } else {
            CeSheetActions(primary = "Listo", onPrimary = ::finishTyping, primaryEnabled = !error && parsed != null)
        }
    }
}

/** `.ce-rb`: el «−» o el «+» de 54 dp. */
@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.059f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.086f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { CeIcon(icon, 20.dp, EdColors.ink) }
}

private fun bigStyle(text: Boolean) =
    if (text) edText(34f, FontWeight.Bold, EdColors.ink, 34f, true).copy(letterSpacing = (-0.6).sp)
    else edText(64f, FontWeight.Bold, EdColors.ink, 64f, true).copy(letterSpacing = (-2).sp)

/** `.ce-readv`: la cifra grande con su unidad; tocarla pasa a escribir. */
@Composable
private fun BigReadout(
    kind: CardioValueKind,
    n: Int,
    off: Boolean,
    mode: CardioValueMode,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (figure, unit) = if (off) (if (mode == CardioValueMode.FREE) "Libre" else "Omitido") to "" else kind.big(n)
    val asText = figure.length > 1 && figure.first().isLetter()
    Row(
        modifier = modifier
            .height(100.dp)
            .alpha(if (off) 0.7f else 1f)
            .clickable(enabled = !off, role = Role.Button, onClick = onTap)
            .semantics { contentDescription = "Escribir el valor. Ahora: $figure $unit".trim() },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(figure, modifier = Modifier.alignByBaseline(), style = bigStyle(asText), maxLines = 1, textAlign = TextAlign.Center)
        if (unit.isNotEmpty()) {
            Text(unit, modifier = Modifier.alignByBaseline(), style = edText(17f, FontWeight.Medium, EdColors.muted, 20f), maxLines = 2)
        }
    }
}

/** `.ce-read.typing`: la cifra que se teclea, con el cursor de 3 × 52 dp y la unidad; el error la pone en rojo suave. */
@Composable
private fun TypingReadout(
    kind: CardioValueKind,
    buffer: String,
    n: Int,
    error: Boolean,
    onBuffer: (String) -> Unit,
    onDone: () -> Unit,
    focus: FocusRequester,
) {
    val shown = if (buffer.isEmpty()) "0" else kind.typedDisplay(buffer)
    val unit = if (kind.typedAsMinSec) "min" else kind.big(kind.rulerN(n)).second
    // ce-A02 `.ce-caret` parpadeo `blink` 1 s steps(1) infinito (familia `pulso`)
    val blink = rememberKpknPulse(KpknFamilies.Pulso.demo)
    Box(modifier = Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shown, style = bigStyle(false).copy(color = if (error) EdColors.danger else EdColors.ink), maxLines = 1)
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .width(3.dp)
                        .height(52.dp)
                        .kpknPulse(blink)
                        .background(EdColors.ink),
                )
            }
            Text(unit, style = edText(17f, FontWeight.Medium, EdColors.muted, 20f))
        }
        // El teclado del sistema escribe aquí; la cifra de arriba es lo que se ve.
        BasicTextField(
            value = buffer,
            onValueChange = onBuffer,
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (kind.typedDecimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true,
        )
    }
}

/**
 * `.ce-ruler`: la regla de 80 dp que ocupa todo el ancho de la hoja, con marcas cada paso, una larga con número cada `major` y la aguja de
 * tinta en el centro. Se arrastra con el dedo (cada marca es un paso) y, al soltar, se ajusta a la marca más cercana. Las puntas se funden.
 */
@Composable
internal fun CardioRuler(
    kind: CardioValueKind,
    n: Int,
    enabled: Boolean,
    onN: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val reduced = wizardReducedMotion()
    val position = remember { Animatable(kind.rulerN(n).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { edText(11.5f, FontWeight.Medium, EdColors.faint, 14f, true) }
    val labels = remember(kind) {
        (kind.min..kind.max).filter { it % kind.major == 0 }.associateWith { measurer.measure(kind.tickLabel(it), labelStyle) }
    }
    // ce-T03 `.ce-rt` transición del transform 0,24 s estandar (ida a la marca al pulsar −, +, un atajo o soltar)
    LaunchedEffect(n) {
        if (!dragging) {
            val target = kind.rulerN(n).toFloat()
            if (reduced) position.snapTo(target) else position.animateTo(target, kpknTween(com.example.kpkn.ui.design.motion.KpknDuration(240), KpknMotion.Estandar))
        }
    }
    val needleShape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .edBleed(20.dp)
            .height(80.dp)
            .semantics {
                contentDescription = kind.title
                stateDescription = kind.big(n).let { "${it.first} ${it.second}".trim() }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // ce-X01 `.ce-ruler` máscara: transparente → negro 16 % → negro 84 % → transparente
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent, 0.16f to Color.Black, 0.84f to Color.Black, 1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .pointerInput(kind, enabled) {
                if (!enabled) return@pointerInput
                // ce-J04 `.ce-ruler` arrastre con captura del puntero · ce-J05 mueve la regla y la cifra en cada paso · ce-J06 al soltar vuelve a la marca
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val target = position.value.roundToInt().coerceIn(kind.min, kind.max)
                        scope.launch {
                            if (reduced) position.snapTo(target.toFloat())
                            else position.animateTo(target.toFloat(), kpknTween(com.example.kpkn.ui.design.motion.KpknDuration(240), KpknMotion.Estandar))
                        }
                        onN(target)
                    },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    val next = (position.value - dragAmount / (kind.tickDp * density)).coerceIn(kind.min.toFloat(), kind.max.toFloat())
                    scope.launch { position.snapTo(next) }
                    val rounded = next.roundToInt()
                    if (rounded != n) onN(rounded)
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val cx = size.width / 2f
            val stepPx = kind.tickDp.dp.toPx()
            val pos = position.value
            val first = kotlin.math.floor(pos - cx / stepPx).toInt().coerceAtLeast(kind.min)
            val last = kotlin.math.ceil(pos + cx / stepPx).toInt().coerceAtMost(kind.max)
            for (v in first..last) {
                val x = cx + (v - pos) * stepPx
                val major = v % kind.major == 0
                val h = (if (major) 32 else 16).dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = if (major) 0.4f else 0.188f),
                    topLeft = Offset(x - 1.dp.toPx(), 18.dp.toPx()),
                    size = Size(2.dp.toPx(), h),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
                val layout = labels[v]
                if (layout != null) {
                    // ce-X02 `.ce-tl` translateX(-50%): el número se centra bajo su marca
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 56.dp.toPx()))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .width(3.dp)
                .height(48.dp)
                // ce-S04 `.ce-needle` resplandor 0 0 12px #f2eee655
                .kpknShadow(KpknShadow.ResplandorCrema12, needleShape)
                .background(EdColors.ink, needleShape),
        )
    }
}
