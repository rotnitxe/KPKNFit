package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.PartAccentPreset
import com.example.kpkn.screens.sessioneditor.components.MobilityPreparationCarousel
import com.example.kpkn.screens.sessioneditor.partAccentGradients
import com.example.kpkn.screens.sessioneditor.partAccentSolids
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Cabecera de una parte (SESSION_EDITOR_V1 §2, punto 6): un punto del color de la parte, el nombre, «3 ejercicios · 10 series» y «⋯».
 * La pulsación larga en el nombre arrastra la parte. El menú hace lo de siempre: renombrar en sitio, cambiar el color, plegar o eliminar
 * (con confirmación). Solo guarda lo de su propia cabecera (menú, edición del nombre y diálogos); los datos y el arrastre los pone quien la
 * usa, con [onDragStart], [onDrag], [onDragEnd] y [onDragCancel].
 *
 * @param colorId el id del color de la parte (`SessionPart.color`).
 * @param onDragStart recibe el rectángulo de la cabecera en la ventana y el punto donde se pulsó.
 */
@Composable
internal fun SessionPartHeader(
    name: String,
    caption: String,
    colorId: String?,
    collapsed: Boolean,
    hasExercises: Boolean,
    onRename: (String) -> Unit,
    onChangeColor: (String) -> Unit,
    onToggleCollapse: () -> Unit,
    onRemove: (keepExercises: Boolean) -> Unit,
    onDragStart: (Rect?, Offset) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = remember(colorId) { resolvePartAccent(colorId) }
    val haptics = LocalHapticFeedback.current
    var headerBounds by remember { mutableStateOf<Rect?>(null) }
    var zoneOrigin by remember { mutableStateOf(Offset.Zero) }
    var moreAnchor by remember { mutableStateOf<Rect?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(name) }
    var showColors by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val latestBounds = rememberUpdatedState(headerBounds)
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDrag = rememberUpdatedState(onDrag)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val latestOnDragCancel = rememberUpdatedState(onDragCancel)

    LaunchedEffect(renaming) {
        if (renaming) focus.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { headerBounds = it.boundsInWindow() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Zona de arrastre: el punto y el nombre. El botón «⋯» queda fuera, para que su pulsación no arrastre.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { zoneOrigin = it.boundsInWindow().topLeft }
                    .then(
                        if (renaming) {
                            Modifier
                        } else {
                            Modifier.pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestOnDragStart.value(latestBounds.value, zoneOrigin + offset)
                                    },
                                    onDragCancel = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        latestOnDragCancel.value()
                                    },
                                    onDragEnd = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        latestOnDragEnd.value()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        latestOnDrag.value(dragAmount.y)
                                    },
                                )
                            }
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent.brush()),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (renaming) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { input ->
                                val upper = input.uppercase()
                                draft = upper
                                onRename(upper)
                            },
                            singleLine = true,
                            textStyle = KpknType.bodyStrong.copy(color = WizardColors.text),
                            cursorBrush = SolidColor(accent.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onRename(draft.trim())
                                    renaming = false
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        hadFocus = true
                                    } else if (hadFocus) {
                                        // Al salir del campo se guarda el nombre sin espacios sobrantes.
                                        onRename(draft.trim())
                                        renaming = false
                                        hadFocus = false
                                    }
                                }
                                .testTag("session_part_name_field"),
                        )
                    } else {
                        Text(
                            text = name.trim().ifBlank { "Parte sin nombre" },
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = caption,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Más opciones de la parte",
                onClick = { menuOpen = true },
                modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInWindow() },
            )
        }

        AnimatedVisibility(visible = showColors, enter = sessionEnter(), exit = sessionExit()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Sólidos", style = KpknType.caption, color = WizardColors.textMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    partAccentSolids.forEach { preset ->
                        PartAccentSwatch(
                            accent = preset,
                            selected = preset.id == colorId,
                            onClick = {
                                onChangeColor(preset.id)
                                showColors = false
                            },
                        )
                    }
                }
                Text("Gradientes", style = KpknType.caption, color = WizardColors.textMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    partAccentGradients.forEach { preset ->
                        PartAccentSwatch(
                            accent = preset,
                            selected = preset.id == colorId,
                            onClick = {
                                onChangeColor(preset.id)
                                showColors = false
                            },
                        )
                    }
                }
            }
        }
    }

    val anchor = moreAnchor
    if (menuOpen && anchor != null) {
        KpknContextMenu(anchor = anchor, rows = 4, onDismiss = { menuOpen = false }) {
            KpknContextMenuRow(
                label = "Renombrar",
                onClick = {
                    menuOpen = false
                    draft = name
                    renaming = true
                },
            )
            KpknContextMenuRow(
                label = "Color",
                onClick = {
                    menuOpen = false
                    showColors = !showColors
                },
            )
            KpknContextMenuRow(
                label = if (collapsed) "Desplegar" else "Plegar",
                onClick = {
                    menuOpen = false
                    onToggleCollapse()
                },
            )
            KpknContextMenuRow(
                label = "Eliminar parte",
                destructive = true,
                onClick = {
                    menuOpen = false
                    confirmRemove = true
                },
            )
        }
    }

    if (confirmRemove) {
        if (hasExercises) {
            KpknAlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("¿Qué hacemos con los ejercicios?", style = KpknType.dialogTitle, color = WizardColors.text) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Puedes conservarlos sin parte o borrar también todo su contenido.",
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                        )
                        KpknSecondaryPill(
                            text = "Conservar ejercicios",
                            onClick = {
                                confirmRemove = false
                                onRemove(true)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    KpknDangerPill(
                        text = "Borrar parte y ejercicios",
                        onClick = {
                            confirmRemove = false
                            onRemove(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                dismissButton = {
                    KpknAlertDismissButton(text = "Cancelar", onClick = { confirmRemove = false })
                },
            )
        } else {
            KpknAlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = "Eliminar parte",
                text = "¿Eliminar esta parte?",
                confirmLabel = "Eliminar",
                onConfirm = {
                    confirmRemove = false
                    onRemove(false)
                },
                dismissLabel = "Cancelar",
                onDismiss = { confirmRemove = false },
                destructive = true,
            )
        }
    }
}

/**
 * Muestra de un color de parte (SESSION_EDITOR_V1 §2): un círculo de 24 dp (28 si es degradado) en una zona táctil de 48 dp. El seleccionado
 * lleva un filete blanco de 2 dp.
 */
@Composable
internal fun PartAccentSwatch(
    accent: PartAccentPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (accent.isGradient) 28.dp else 24.dp)
                .clip(CircleShape)
                .background(accent.brush())
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = Color.White.copy(alpha = 0.9f),
                    shape = CircleShape,
                ),
        )
    }
}

/**
 * Pie de un módulo de parte (SESSION_EDITOR_V1 §2, punto 6): «Añadir ejercicio a <parte>», con el «+» delante. Es una acción de texto de
 * 48 dp de alto.
 */
@Composable
internal fun SessionPartAddExerciseAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknTextAction(
        text = text,
        onClick = onClick,
        modifier = modifier.padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
        leadingIcon = KpknLineIcons.Plus,
    )
}

/**
 * Editor de la movilidad global de una parte de movilidad (la cabecera la muestra debajo de la parte). Sin cambios de comportamiento: solo
 * pasa al lenguaje KPKN.
 */
@Composable
internal fun MobilityGroupInlineEditor(
    mobilitySeries: List<MobilitySeries>,
    mobilityConfig: MobilityConfig?,
    accentColor: Color,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (MobilitySeries) -> MobilitySeries) -> Unit,
    onUpdateConfig: (MobilityConfig) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Movilidad global · ${mobilitySeries.size} movimiento${if (mobilitySeries.size == 1) "" else "s"}",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (mobilitySeries.isEmpty()) {
            Text(
                "Añade movimientos para programar series, repeticiones y un tiempo global.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            KpknTextAction(text = "Añadir movilidad", onClick = onAdd, leadingIcon = KpknLineIcons.Plus)
        } else {
            // La movilidad global usa el mismo carrusel compacto que un ejercicio: no hay una segunda fila de solo lectura.
            MobilityPreparationCarousel(
                series = mobilitySeries,
                mobilityConfig = mobilityConfig,
                accentColor = accentColor,
                onUpdate = onUpdate,
                onUpdateConfig = onUpdateConfig,
                onRemove = onRemove,
                onAdd = onAdd,
            )
        }
    }
}
