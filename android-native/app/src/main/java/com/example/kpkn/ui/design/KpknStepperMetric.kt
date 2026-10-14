package com.example.kpkn.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Al mantener pulsado ±, el primer paso llega a los 400 ms y luego uno cada 90 ms. */
private const val REPEAT_FIRST_MS = 400L
private const val REPEAT_EVERY_MS = 90L

/**
 * Cifra grande con ± (KPKN LS1): la etiqueta ([label], «KG», «REPS» o «TIEMPO») encima, la cifra en `metricXL` y, debajo, dos botones
 * redondos de 56 dp con 16 dp entre ellos. No guarda datos: la cifra y los pasos los decide quien llama.
 *
 * - Tocar ± da un paso. Mantenerlo pulsado repite (el primero a los 400 ms y luego cada 90 ms), con un háptico en cada paso.
 * - Con [onCommit], tocar la cifra la edita en el sitio, con el mismo tamaño y el teclado [keyboardType]. «Hecho» o perder el foco
 *   llama a [onCommit] con el texto, sin espacios en los extremos.
 * - [contentDescription] es lo que se lee de la cifra («80 kilos»). Los botones leen «Restar» y «Sumar» con el nombre de la cifra.
 */
@Composable
fun KpknStepperMetric(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onCommit: ((String) -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    /** Estilo de la etiqueta: eyebrow por defecto; en tipo frase (p. ej. «Con ayuda») se pasa `KpknType.bodyStrong`. */
    labelStyle: androidx.compose.ui.text.TextStyle = KpknType.eyebrow,
) {
    var editing by remember { mutableStateOf(false) }
    val spoken = spokenName(label)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // La etiqueta visual se calla: la cifra ya lleva su unidad en la descripción.
        Text(
            text = label,
            style = labelStyle,
            color = WizardColors.textMuted,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
        if (editing && onCommit != null) {
            FigureField(
                value = value,
                keyboardType = keyboardType,
                onFinish = { text ->
                    editing = false
                    onCommit(text)
                },
            )
        } else {
            FigureText(
                value = value,
                description = contentDescription,
                editable = onCommit != null,
                onTap = { editing = true },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(icon = KpknLineIcons.Minus, description = "Restar $spoken", enabled = minusEnabled, onStep = onMinus)
            Spacer(Modifier.width(16.dp))
            StepButton(icon = KpknLineIcons.Plus, description = "Sumar $spoken", enabled = plusEnabled, onStep = onPlus)
        }
    }
}

/** La cifra en reposo. Con [editable], tocarla abre la edición. */
@Composable
private fun FigureText(
    value: String,
    description: String,
    editable: Boolean,
    onTap: () -> Unit,
) {
    Text(
        text = value,
        style = KpknType.metricXL,
        color = WizardColors.text,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .defaultMinSize(minWidth = 112.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = description }
            .then(if (editable) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier),
    )
}

/**
 * La cifra en edición: mismo tamaño que en reposo, con el cursor en tinta y un filete debajo. Se cierra una sola vez: con «Hecho» o al
 * perder el foco después de haberlo tenido.
 */
@Composable
private fun FigureField(
    value: String,
    keyboardType: KeyboardType,
    onFinish: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(TextFieldValue(text = value, selection = TextRange(0, value.length))) }
    var finished by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun finish() {
        if (finished) return
        finished = true
        onFinish(draft.text.trim())
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // La selección va en tinta, no en el azul de Material: la cifra en edición sigue siendo de KPKN.
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = WizardColors.text,
            backgroundColor = WizardColors.text.copy(alpha = 0.24f),
        ),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = KpknType.metricXL.copy(color = WizardColors.text, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(WizardColors.text),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { finish() }),
            // Mide lo que ocupa la cifra (mínimo 112 dp): el subrayado no cruza toda la celda.
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .widthIn(min = 112.dp)
                .heightIn(min = 48.dp)
                .focusRequester(focus)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        finish()
                    }
                }
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color = WizardColors.text,
                        start = Offset(0f, size.height - stroke / 2f),
                        end = Offset(size.width, size.height - stroke / 2f),
                        strokeWidth = stroke,
                    )
                },
        )
    }
}

/**
 * Botón de ± de 56 dp (KpknIconButton mide 48 y no admite este tamaño). Un toque da un paso; mantenido, repite. Deshabilitado no
 * responde, se ve apagado y lo anuncia TalkBack.
 */
@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val latestStep by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "kpknStepScale",
    )
    fun step() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        latestStep()
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(CircleShape)
            .background(KpknSurface.inset, CircleShape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = description
                role = Role.Button
                if (enabled) {
                    onClick(label = description) {
                        step()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                coroutineScope {
                                    var repeating = false
                                    val repeat = launch {
                                        delay(REPEAT_FIRST_MS)
                                        repeating = true
                                        step()
                                        while (true) {
                                            delay(REPEAT_EVERY_MS)
                                            step()
                                        }
                                    }
                                    val released = tryAwaitRelease()
                                    repeat.cancel()
                                    // Un toque corto da un paso aquí; si ya repitió, el último paso fue el de la repetición.
                                    if (released && !repeating) step()
                                }
                                pressed = false
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = WizardColors.text)
    }
}

/** Nombre hablado de la cifra para los botones: «KG» se dice «kilos», así «Sumar kilos». */
private fun spokenName(label: String): String = when (label.trim().uppercase()) {
    "KG" -> "kilos"
    "REPS" -> "repeticiones"
    "TIEMPO" -> "tiempo"
    else -> label.trim().lowercase()
}
