package com.example.kpkn.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.abs

/**
 * Cómo se usa una [KpknNumberCell]. Sin estado: quien la usa decide qué pasa al tocarla.
 * - [Text]: se escribe con teclado; [onCommit] recibe el texto al pulsar «Hecho» o al perder el foco.
 * - [Picker]: abre un selector propio (descanso, tiempo); la celda solo avisa.
 * - [ReadOnly]: solo muestra el valor.
 */
sealed interface KpknCellInput {
    data class Text(val keyboardType: KeyboardType = KeyboardType.Decimal, val onCommit: (String) -> Unit) : KpknCellInput

    data class Picker(val onOpen: () -> Unit) : KpknCellInput

    data object ReadOnly : KpknCellInput
}

/**
 * Celda de una cifra (KPKN_UI_V2 §3): relleno `inset`, radio 12 dp y 40 dp de alto dentro de un objetivo táctil de 48 dp. La cifra va en
 * `bodyStrong` tabular y centrada; sin valor, [placeholder] en `textFaint`. Si no cabe (letra grande), encoge hasta 11 sp antes de cortarse.
 * Con [KpknCellInput.Text] se edita en el sitio, con el texto seleccionado entero y «Hecho». Con [onStep], un arrastre horizontal cada 24 dp
 * suma o resta 1 con un háptico corto, y TalkBack ofrece «Aumentar» y «Disminuir». Editando, filete de 1,5 dp en tinta; con [error], en
 * `danger`. La pieza no valida: lo hace quien la usa.
 */
@Composable
fun KpknNumberCell(
    value: String,
    input: KpknCellInput,
    contentDescription: String,
    modifier: Modifier = Modifier,
    placeholder: String = "–",
    onStep: ((Int) -> Unit)? = null,
    error: Boolean = false,
) {
    val reduced = wizardReducedMotion()
    val haptic = LocalHapticFeedback.current
    val currentOnStep by rememberUpdatedState(onStep)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "kpknCellScale",
    )
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue()) }
    var sawFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val textInput = input as? KpknCellInput.Text
    val picker = input as? KpknCellInput.Picker
    val edge by animateColorAsState(
        targetValue = when {
            error -> WizardColors.danger
            editing -> WizardColors.text
            else -> Color.Transparent
        },
        animationSpec = if (reduced) snap() else tween(durationMillis = 160),
        label = "kpknCellEdge",
    )
    val shape = RoundedCornerShape(12.dp)
    val cellStyle = KpknType.bodyStrong.copy(
        color = WizardColors.text,
        textAlign = TextAlign.Center,
        fontFeatureSettings = "'tnum' 1",
    )
    // Encoge la cifra (con la letra ya escalada) hasta 11 sp antes de cortarla; con la letra normal no cambia nada.
    val autoSize = remember { TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp, stepSize = 0.5.sp) }
    val selectionColors = remember {
        TextSelectionColors(handleColor = WizardColors.text, backgroundColor = WizardColors.text.copy(alpha = 0.24f))
    }

    fun startEditing() {
        draft = TextFieldValue(text = value, selection = TextRange(0, value.length))
        sawFocus = false
        editing = true
    }

    fun commit() {
        if (!editing) return
        editing = false
        textInput?.onCommit?.invoke(draft.text)
    }

    LaunchedEffect(editing) {
        if (editing) focus.requestFocus()
    }

    val interactive = (textInput != null || picker != null) && !editing
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (error) stateDescription = "No válido"
                if (onStep != null) {
                    customActions = listOf(
                        CustomAccessibilityAction("Aumentar") {
                            onStep(1)
                            true
                        },
                        CustomAccessibilityAction("Disminuir") {
                            onStep(-1)
                            true
                        },
                    )
                }
            }
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = if (picker != null) Role.Button else null,
                        onClickLabel = if (picker != null) "Cambiar" else "Editar",
                        onClick = {
                            if (picker != null) picker.onOpen() else startEditing()
                        },
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (onStep != null && !editing) {
                    Modifier.pointerInput(Unit) {
                        val stepPx = 24.dp.toPx()
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
                                currentOnStep?.invoke(direction)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(KpknSurface.inset, shape)
                .border(width = 1.5.dp, color = edge, shape = shape)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (editing && textInput != null) {
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { state ->
                                // Solo cuenta la pérdida de foco después de haberlo tenido: al abrir aún no lo tiene.
                                if (state.isFocused) {
                                    sawFocus = true
                                } else if (sawFocus) {
                                    commit()
                                }
                            },
                        textStyle = cellStyle,
                        cursorBrush = SolidColor(WizardColors.text),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = textInput.keyboardType, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                    )
                }
            } else {
                BasicText(
                    text = value.ifEmpty { placeholder },
                    modifier = Modifier.fillMaxWidth(),
                    style = cellStyle.copy(color = if (value.isEmpty()) WizardColors.textFaint else WizardColors.text),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = autoSize,
                )
            }
        }
    }
}
