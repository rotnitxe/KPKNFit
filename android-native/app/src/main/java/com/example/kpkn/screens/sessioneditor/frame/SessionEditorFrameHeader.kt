package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Recorrido del dedo que cambia de día (80 px, como el gesto del original). */
private const val FRAME_SWIPE_THRESHOLD_PX = 80f

/** Cabecera de la sesión, dentro de la lista y sin portada (ER1a §2.2): ceja, nombre y descripción editables, y el resumen. */
@Composable
internal fun SessionEditorFrameHeader(
    sessionId: String,
    name: String,
    description: String,
    protocolLabel: String?,
    summaryChips: List<String>,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDaySwipe: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    // Cada sesión empieza con la cabecera en su sitio; si el gesto cambió de día, la nueva entra desde el borde.
    LaunchedEffect(sessionId) { dragX.snapTo(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer { translationX = dragX.value }
            .pointerInput(sessionId) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                        scope.launch { dragX.snapTo(totalDrag) }
                    },
                    onDragEnd = {
                        val step = when {
                            totalDrag < -FRAME_SWIPE_THRESHOLD_PX -> 1
                            totalDrag > FRAME_SWIPE_THRESHOLD_PX -> -1
                            else -> 0
                        }
                        val switched = step != 0 && onDaySwipe(step)
                        scope.launch {
                            if (switched) {
                                // Sale por el lado del gesto; la sesión nueva entra desde el otro (ver la pantalla).
                                dragX.animateTo(-step * widthPx.coerceAtLeast(1).toFloat(), tween(durationMillis = 120))
                            } else {
                                dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!protocolLabel.isNullOrBlank()) {
            Text(
                text = protocolLabel.uppercase(),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NameField(sessionId = sessionId, name = name, onCommit = onNameChange)
        DescriptionField(sessionId = sessionId, description = description, onCommit = onDescriptionChange)
        SummaryChips(chips = summaryChips)
    }
}

/** El resumen de la sesión en fichas de 28 dp: «5 ejercicios · 14 series» y «≈ 58 min» (ED-A · A1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryChips(chips: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("session_header_summary"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { text ->
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 28.dp)
                    .background(KpknSurface.module, KpknShape.pill)
                    .border(1.dp, WizardColors.text.copy(alpha = 0.08f), KpknShape.pill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = WizardColors.text,
                )
            }
        }
    }
}

/**
 * Nombre editable en sitio: Syne, 34, 28 o 22 sp según la longitud (como el original), con «Nueva sesión» si está vacío. Se envía a los
 * 150 ms sin teclear o al perder el foco.
 */
@Composable
private fun NameField(sessionId: String, name: String, onCommit: (String) -> Unit) {
    var localName by remember(sessionId) { mutableStateOf(name) }
    var hadFocus by remember(sessionId) { mutableStateOf(false) }
    val latestCommit by rememberUpdatedState(onCommit)
    val sizeSp = when {
        localName.length < 15 -> 34
        localName.length < 25 -> 28
        else -> 22
    }
    LaunchedEffect(localName, sessionId) {
        if (localName == name) return@LaunchedEffect
        delay(150)
        latestCommit(localName)
    }
    DisposableEffect(sessionId) {
        onDispose { if (localName != name) latestCommit(localName) }
    }
    val style: TextStyle = KpknType.display.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp + 6).sp, color = WizardColors.text)
    BasicTextField(
        value = localName,
        // Un nombre largo se parte en las líneas que haga falta en vez de cortarse; el salto de línea no forma parte del nombre.
        onValueChange = { localName = it.replace("\n", "") },
        textStyle = style,
        cursorBrush = SolidColor(WizardColors.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { latestCommit(localName) }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .onFocusChanged { state ->
                if (state.isFocused) {
                    hadFocus = true
                } else if (hadFocus && localName != name) {
                    latestCommit(localName)
                }
            },
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (localName.isBlank()) {
                    Text(
                        text = "Nueva sesión",
                        style = style,
                        color = WizardColors.textFaint,
                    )
                }
                inner()
            }
        },
    )
}

/** Descripción editable en sitio: Inter en `textMuted`, con «Añadir descripción». Se envía a los 150 ms o al perder el foco. */
@Composable
private fun DescriptionField(sessionId: String, description: String, onCommit: (String) -> Unit) {
    var localDescription by remember(sessionId) { mutableStateOf(description) }
    var hadFocus by remember(sessionId) { mutableStateOf(false) }
    val latestCommit by rememberUpdatedState(onCommit)
    LaunchedEffect(localDescription, sessionId) {
        if (localDescription == description) return@LaunchedEffect
        delay(150)
        latestCommit(localDescription)
    }
    DisposableEffect(sessionId) {
        onDispose { if (localDescription != description) latestCommit(localDescription) }
    }
    BasicTextField(
        value = localDescription,
        // La descripción también se parte en las líneas que haga falta y no se corta en el borde.
        onValueChange = { localDescription = it.replace("\n", "") },
        textStyle = KpknType.body.copy(color = WizardColors.textMuted),
        cursorBrush = SolidColor(WizardColors.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { latestCommit(localDescription) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) {
                    hadFocus = true
                } else if (hadFocus && localDescription != description) {
                    latestCommit(localDescription)
                }
            },
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (localDescription.isBlank()) {
                    Text(
                        text = "Añadir descripción",
                        style = KpknType.body,
                        color = WizardColors.textFaint,
                    )
                }
                inner()
            }
        },
    )
}
