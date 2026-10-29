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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
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
 * El recuadro grande de la pestaña Describir (lienzo `.write`). Escribiendo: el marcador «¿Qué comiste? Escríbelo como lo contarías», y al
 * pie «Cantidades, marcas y cómo lo cocinaste» con el micrófono «Dictar» ([onDictate]); con texto, «Enter para analizar» y la píldora
 * «Analizar» ([analyzeLabel]). Leyendo ([LoggerWriteMode.READING]): el texto a 17 con los trozos que se subrayan en acento y pasan a
 * verde. Analizado ([LoggerWriteMode.RESULT]): compacto, a 14 en gris, con en verde lo que se convirtió en alimento ([foodMentions]);
 * tocarlo vuelve a escribir ([onEditRequest]).
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
        if (mode == LoggerWriteMode.EDIT && focused) LoggerColors.ink.copy(alpha = 0.333f) else Color.White.copy(alpha = 0.086f),
        tween(200),
        label = "loggerWriteBorder",
    )
    val compact = mode == LoggerWriteMode.RESULT && fontSize == null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // `.write.focus`: un halo de 4 dp por fuera (box-shadow), sin mover el recuadro.
                if (mode == LoggerWriteMode.EDIT && focused) {
                    val halo = 4.dp.toPx()
                    drawRoundRect(
                        color = LoggerColors.ink.copy(alpha = 0.047f),
                        topLeft = Offset(-halo, -halo),
                        size = Size(size.width + halo * 2, size.height + halo * 2),
                        cornerRadius = CornerRadius(26.dp.toPx() + halo),
                    )
                }
            }
            .background(LoggerColors.card, shape)
            .border(1.dp, border, shape)
            .then(
                if (compact && onEditRequest != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onEditRequest)
                        .semantics { contentDescription = "Editar lo que comiste: $text" }
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = if (compact) 0.dp else 150.dp)
            .padding(
                start = if (compact) 14.dp else 16.dp,
                end = if (compact) 14.dp else 16.dp,
                top = if (compact) 12.dp else 16.dp,
                bottom = 12.dp,
            ),
    ) {
        when (mode) {
            LoggerWriteMode.EDIT -> Column(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = WriteStyle.copy(color = LoggerColors.ink),
                    cursorBrush = SolidColor(LoggerColors.ink),
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAnalyze() }),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (showAnalyze) {
                        Text("Enter para analizar", style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.weight(1f))
                        LoggerPill(
                            text = analyzeLabel,
                            onClick = onAnalyze,
                            small = true,
                            icon = LoggerIcons.Spark,
                        )
                    } else {
                        Text(
                            "Cantidades, marcas y cómo lo cocinaste",
                            style = LoggerText.cap,
                            color = LoggerColors.faint,
                            modifier = Modifier.weight(1f),
                        )
                        if (onDictate != null) {
                            LoggerIconButton(
                                icon = KpknLineIcons.Mic,
                                contentDescription = if (listening) "Dejar de dictar" else "Dictar",
                                onClick = onDictate,
                                fill = true,
                                tint = if (listening) LoggerColors.mus else LoggerColors.muted,
                            )
                        }
                    }
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
                style = WriteStyle.copy(fontSize = (fontSize ?: 14f).sp, lineHeight = ((fontSize ?: 14f) * 1.4f).sp),
                color = if (fontSize == null) LoggerColors.muted else LoggerColors.ink,
            )
        }
    }
}

/**
 * El texto con sus trozos marcados (lienzo `.tok`): fondo redondeado de 6 dp y una raya inferior de 2 dp, en acento mientras se lee
 * (`.tok.on`) y en verde cuando ya es un alimento (`.tok.done`), con una transición de 0,4 s.
 */
@Composable
private fun LoggerTokenText(text: String, reading: Boolean, readingIndex: Int, foodMentions: List<String>, style: TextStyle, color: Color) {
    val tokens = remember(text, reading, readingIndex, foodMentions) {
        if (reading) loggerReadingTokens(text, readingIndex) else loggerResultTokens(text, foodMentions)
    }
    val fills = tokens.mapIndexed { index, token ->
        key(index) {
            animateColorAsState(
                when (token.state) {
                    LoggerTokenState.READING -> LoggerColors.mus.copy(alpha = 0.15f)
                    LoggerTokenState.DONE -> LoggerColors.done.copy(alpha = 0.11f)
                    LoggerTokenState.PLAIN -> Color.Transparent
                },
                tween(400),
                label = "loggerTokenFill",
            ).value
        }
    }
    val lines = tokens.mapIndexed { index, token ->
        key(index) {
            animateColorAsState(
                when (token.state) {
                    LoggerTokenState.READING -> LoggerColors.mus
                    LoggerTokenState.DONE -> LoggerColors.done.copy(alpha = 0.53f)
                    LoggerTokenState.PLAIN -> Color.Transparent
                },
                tween(400),
                label = "loggerTokenLine",
            ).value
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val result = layout ?: return@drawBehind
                val pad = 2.dp.toPx()
                val radius = CornerRadius(6.dp.toPx())
                val rule = 2.dp.toPx()
                tokens.forEachIndexed { index, token ->
                    val end = token.end.coerceAtMost(result.layoutInput.text.length)
                    if (token.start >= end) return@forEachIndexed
                    val firstLine = result.getLineForOffset(token.start)
                    val lastLine = result.getLineForOffset(end - 1)
                    for (line in firstLine..lastLine) {
                        val from = maxOf(token.start, result.getLineStart(line))
                        val to = minOf(end, result.getLineEnd(line, visibleEnd = true))
                        if (from >= to) continue
                        val left = result.getHorizontalPosition(from, usePrimaryDirection = true) - pad
                        val right = result.getHorizontalPosition(to, usePrimaryDirection = true) + pad
                        val top = result.getLineTop(line)
                        val bottom = result.getLineBottom(line)
                        drawRoundRect(fills[index], topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = radius)
                        drawRect(lines[index], topLeft = Offset(left, bottom - rule), size = Size(right - left, rule))
                    }
                }
            },
    )
}
