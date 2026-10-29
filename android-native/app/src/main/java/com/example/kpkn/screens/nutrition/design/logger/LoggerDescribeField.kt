package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknShape
import kotlinx.coroutines.delay

/** Cómo se ve el recuadro de la comida: escribiendo (f-empty, f-typed), leyéndose (f-ana) o ya analizado (f-result, f-error). */
enum class LoggerWriteMode { EDIT, READING, RESULT }

/** `.write .txt`: Inter Medium 19 con interlineado 1,4 y −0,2. */
private val WriteStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 26.6.sp, letterSpacing = (-0.2).sp)

/** Pausa entre un trozo y el siguiente mientras se lee la comida (f-ana). */
private const val READING_STEP_MS = 520L

/**
 * El trozo que se está leyendo mientras [active] (f-ana): avanza uno cada 0,52 s y se queda en el último; −1 con movimiento reducido
 * (todo subrayado a la vez). Lo comparten el recuadro y el plato para que digan lo mismo.
 */
@Composable
fun rememberLoggerReadingIndex(text: String, active: Boolean): Int {
    val reduced = wizardReducedMotion()
    var index by remember(text, active) { mutableStateOf(if (active && !reduced) 0 else -1) }
    if (active && !reduced) {
        val pieces = remember(text) { loggerMentionRanges(text).size }
        LaunchedEffect(text) {
            while (index < pieces) {
                delay(READING_STEP_MS)
                index += 1
            }
        }
    }
    return index
}

/**
 * El campo de la pestaña Describir (lienzo `.np-write`): plano (superficie 1, borde de 1 dp al 8 %, con foco en tinta al 25 %, sin sombra
 * ni halo). Escribiendo: el marcador «¿Qué comiste? Escríbelo como lo contarías» y, debajo, el micrófono «Dictar» ([onDictate]) a la
 * izquierda y la píldora «Analizar» ([analyzeLabel]) a la derecha; Enter también analiza (acción «Ir» del teclado). Leyendo
 * ([LoggerWriteMode.READING]): el texto a 17 con los trozos que se van subrayando. Analizado ([LoggerWriteMode.RESULT]): compacto
 * (12 × 16), a 15 en gris con los trozos reconocidos ([foodMentions]) en tinta y subrayados; tocarlo vuelve a escribir ([onEditRequest]).
 */
@Composable
fun LoggerDescribeField(
    text: String,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
    mode: LoggerWriteMode = LoggerWriteMode.EDIT,
    analyzeLabel: String = "Analizar",
    showAnalyze: Boolean = text.isNotBlank(),
    onDictate: (() -> Unit)? = null,
    listening: Boolean = false,
    foodMentions: List<String> = emptyList(),
    onEditRequest: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    fontSize: Float? = null,
    readingIndex: Int = -1,
) {
    val shape = RoundedCornerShape(26.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        if (mode == LoggerWriteMode.EDIT && focused) LoggerColors.ink.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
        tween(200),
        label = "loggerWriteBorder",
    )
    val compact = mode == LoggerWriteMode.RESULT && fontSize == null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(LoggerColors.surface1, shape)
            .border(1.dp, border, shape)
            .then(
                if (compact && onEditRequest != null) {
                    Modifier
                        .clip(shape)
                        .clickable(role = Role.Button, onClick = onEditRequest)
                        .semantics { contentDescription = "Editar lo que comiste: $text" }
                } else {
                    Modifier
                },
            )
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (compact) 12.dp else 16.dp,
                bottom = 12.dp,
            ),
    ) {
        when (mode) {
            LoggerWriteMode.EDIT -> Column(Modifier.fillMaxWidth()) {
                // Alto mínimo del campo: 150 dp en total (el texto 60 + 14 de aire + la fila de acciones de 48 + el relleno de 16 y 12).
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp)) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = WriteStyle.copy(color = LoggerColors.ink),
                        cursorBrush = SolidColor(LoggerColors.ink),
                        interactionSource = interaction,
                        // Compose no deja poner la palabra «Analizar» en la tecla: «Ir» es la acción que más se le parece.
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onAnalyze() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .semantics { contentDescription = "¿Qué comiste?" },
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    Text("¿Qué comiste? Escríbelo como lo contarías", style = WriteStyle, color = LoggerColors.dim)
                                }
                                inner()
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onDictate != null) DictateButton(listening = listening, onClick = onDictate)
                    Spacer(Modifier.weight(1f))
                    if (showAnalyze) AnalyzePill(label = analyzeLabel, onClick = onAnalyze)
                }
            }
            LoggerWriteMode.READING -> LoggerTokenText(
                text = text,
                reading = true,
                readingIndex = readingIndex,
                foodMentions = foodMentions,
                style = WriteStyle.copy(fontSize = (fontSize ?: 17f).sp, lineHeight = ((fontSize ?: 17f) * 1.4f).sp),
                color = LoggerColors.ink,
            )
            LoggerWriteMode.RESULT -> LoggerTokenText(
                text = text,
                reading = false,
                readingIndex = -1,
                foodMentions = foodMentions,
                style = WriteStyle.copy(fontSize = (fontSize ?: 15f).sp, lineHeight = ((fontSize ?: 15f) * 1.4f).sp),
                color = if (fontSize == null) LoggerColors.muted else LoggerColors.ink,
            )
        }
    }
}

/** `.np .ib.fill`: el micrófono, botón redondo de 44 dp con fondo de superficie 2 (zona táctil de 48). Dictando, pasa a superficie 3. */
@Composable
private fun DictateButton(listening: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (listening) "Dejar de dictar" else "Dictar" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (listening) LoggerColors.surface3 else LoggerColors.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                KpknLineIcons.Mic,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (listening) LoggerColors.ink else LoggerColors.muted,
            )
        }
    }
}

/** `.pill.pri.sm` con el destello: la píldora compacta «Analizar» (40 dp de alto, tinta, texto negro), a su ancho de contenido. */
@Composable
private fun AnalyzePill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(KpknShape.pill)
                .background(LoggerColors.ink, KpknShape.pill)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(LoggerIcons.Spark, contentDescription = null, modifier = Modifier.size(16.dp), tint = LoggerColors.onInk)
            Text(text = label, style = LoggerText.pillSmall, color = LoggerColors.onInk, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * El texto con sus trozos marcados (lienzo `.np-tok`): el trozo reconocido en tinta y subrayado con una raya de 1,5 dp separada 4 dp del
 * texto, en tinta al 35 % (leyéndose, al 70 %). Sin fondo de color y sin verde: el acento queda para las dudas. Transición de 0,4 s.
 */
@Composable
private fun LoggerTokenText(text: String, reading: Boolean, readingIndex: Int, foodMentions: List<String>, style: TextStyle, color: Color) {
    val tokens = remember(text, reading, readingIndex, foodMentions) {
        if (reading) loggerReadingTokens(text, readingIndex) else loggerResultTokens(text, foodMentions)
    }
    val lines = tokens.mapIndexed { index, token ->
        key(index) {
            animateColorAsState(
                when (token.state) {
                    LoggerTokenState.READING -> LoggerColors.ink.copy(alpha = 0.7f)
                    LoggerTokenState.DONE -> LoggerColors.ink.copy(alpha = 0.35f)
                    LoggerTokenState.PLAIN -> Color.Transparent
                },
                tween(400),
                label = "loggerTokenLine",
            ).value
        }
    }
    val annotated = remember(text, tokens) {
        buildAnnotatedString {
            append(text)
            tokens.forEach { token ->
                val from = token.start.coerceIn(0, text.length)
                val to = token.end.coerceIn(0, text.length)
                if (token.state != LoggerTokenState.PLAIN && from < to) addStyle(SpanStyle(color = LoggerColors.ink), from, to)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val result = layout ?: return@drawBehind
                val rule = 1.5.dp.toPx()
                val offset = 4.dp.toPx()
                tokens.forEachIndexed { index, token ->
                    val end = token.end.coerceAtMost(result.layoutInput.text.length)
                    if (token.start >= end) return@forEachIndexed
                    val firstLine = result.getLineForOffset(token.start)
                    val lastLine = result.getLineForOffset(end - 1)
                    for (line in firstLine..lastLine) {
                        val from = maxOf(token.start, result.getLineStart(line))
                        val to = minOf(end, result.getLineEnd(line, visibleEnd = true))
                        if (from >= to) continue
                        val left = result.getHorizontalPosition(from, usePrimaryDirection = true)
                        val right = result.getHorizontalPosition(to, usePrimaryDirection = true)
                        val y = minOf(result.getLineBaseline(line) + offset, result.getLineBottom(line) - rule)
                        drawRect(lines[index], topLeft = Offset(left, y), size = Size(right - left, rule))
                    }
                }
            },
    )
}
