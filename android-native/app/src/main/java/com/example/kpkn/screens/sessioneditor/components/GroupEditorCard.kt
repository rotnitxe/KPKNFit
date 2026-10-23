package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.design.SessionSwipeDeleteRow
import com.example.kpkn.screens.sessioneditor.isEditorUncategorized
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknTextAction

/**
 * Cabecera de un grupo de fuerza (SESSION_EDITOR_R1 §3.2 y E7): el asa, el nombre editable en sitio, el resumen «3 ejercicios · 9 series» y
 * la flecha de plegar. Sin color: el grupo es tinta sobre su módulo. Va dentro del trozo de módulo que le da [SessionModuleSlot].
 *
 * La pulsación larga en el asa arrastra el grupo y deslizar la cabecera pide borrarlo, con los diálogos del original. Los ejercicios van
 * debajo, en sus propios ítems de la lista.
 */
@Composable
internal fun GroupEditorCard(
    part: SessionPart,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: (Boolean) -> Unit,
    isDragging: Boolean,
    isDropTarget: Boolean,
    onBoundsChange: (Rect) -> Unit,
    onDragStart: (Rect?, Offset) -> Unit = { _, _ -> },
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    // Default must NOT commit (N2): cancel is a no-op unless the caller wires cancelPartDrag.
    onDragCancel: () -> Unit = {},
    // D9 · movilidad de la sesión: el grupo de movilidad se edita dentro de su módulo.
    onOpenPartMobility: () -> Unit = {},
    onUpdatePartMobility: (String, (MobilitySeries) -> MobilitySeries) -> Unit = { _, _ -> },
    onRemovePartMobility: (String) -> Unit = {},
    onMovePartMobility: (String, Int) -> Unit = { _, _ -> },
    onUpdatePartMobilityConfig: (MobilityConfig) -> Unit = {},
) {
    var showDeleteConfirm by remember(part.id) { mutableStateOf(false) }
    var showDeleteModeConfirm by remember(part.id) { mutableStateOf(false) }
    var cardBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    val haptics = LocalHapticFeedback.current
    val dropScale by animateFloatAsState(if (isDropTarget) 1.01f else 1f, label = "partDropScale")
    val normalizedName = part.name.trim()
    val exerciseCount = part.exercises.size
    val setCount = part.exercises.sumOf { it.sets.size }
    val summary = when {
        part.isMobilityGroup -> {
            val movements = part.mobilitySeries.size
            val minutes = part.mobilityConfig?.totalMinutes?.takeIf { it > 0 }
            buildString {
                append(if (movements == 1) "1 movimiento" else "$movements movimientos")
                if (minutes != null) append(" · $minutes min")
            }
        }
        exerciseCount == 0 -> "Sin ejercicios"
        else -> "$exerciseCount ejercicio${if (exerciseCount == 1) "" else "s"} · $setCount serie${if (setCount == 1) "" else "s"}"
    }
    val shouldShowDeleteChoice = part.exercises.isNotEmpty()
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDrag = rememberUpdatedState(onDrag)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val latestOnDragCancel = rememberUpdatedState(onDragCancel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                cardBoundsInWindow = it.boundsInWindow()
                onBoundsChange(cardBoundsInWindow!!)
            }
            .graphicsLayer {
                scaleX = if (isDragging) 1f else dropScale
                scaleY = if (isDragging) 1f else dropScale
                alpha = if (isDragging) 0.28f else 1f
            }
            .zIndex(if (isDragging) 100f else 0f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // El asa va fuera del deslizamiento, para que borrar no cancele la pulsación larga.
            var handleWindowOrigin by remember(part.id) { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        handleWindowOrigin = Offset(b.left, b.top)
                    }
                    .pointerInput(part.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestOnDragStart.value(cardBoundsInWindow, handleWindowOrigin + offset)
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
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Grip,
                    contentDescription = "Arrastra para reordenar grupo",
                    tint = if (isDragging) WizardColors.text else WizardColors.textFaint,
                    modifier = Modifier.size(24.dp),
                )
            }
            // Deslizar abre el diálogo de borrar directamente, y al cancelar la cabecera vuelve con su nombre (ER2b).
            SessionSwipeDeleteRow(
                deleteOpen = showDeleteConfirm || showDeleteModeConfirm,
                onRequestDelete = {
                    if (shouldShowDeleteChoice) {
                        showDeleteModeConfirm = true
                    } else {
                        showDeleteConfirm = true
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KpknSurface.module, RoundedCornerShape(16.dp))
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (part.isMobilityGroup) {
                            Text(
                                text = "MOVILIDAD",
                                style = KpknType.eyebrow,
                                color = WizardColors.textMuted,
                            )
                        }
                        if (part.isEditorUncategorized()) {
                            Text(
                                text = "Sin grupo",
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                            )
                        } else {
                            // Tipo frase, como lo escribe la persona: sin mayúsculas forzadas ni recortes mientras escribe.
                            BasicTextField(
                                value = part.name,
                                onValueChange = { onRename(it) },
                                singleLine = true,
                                cursorBrush = SolidColor(WizardColors.text),
                                textStyle = KpknType.bodyStrong.copy(color = WizardColors.text),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (normalizedName.isBlank()) {
                                            Text(
                                                text = "Grupo",
                                                style = KpknType.bodyStrong,
                                                color = WizardColors.textFaint,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state ->
                                        // Al salir del campo se quitan los espacios sobrantes del nombre.
                                        if (!state.isFocused && part.name != normalizedName) onRename(normalizedName)
                                    }
                                    .testTag("session_group_name_field"),
                            )
                        }
                        Text(
                            text = summary,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            KpknIconButton(
                icon = if (collapsed) KpknLineIcons.ChevronDown else KpknLineIcons.ChevronUp,
                contentDescription = if (collapsed) "Desplegar grupo" else "Plegar grupo",
                onClick = onToggleCollapse,
            )
        }

        if (part.isMobilityGroup && !collapsed) {
            MobilityGroupInlineEditor(
                part = part,
                onAdd = onOpenPartMobility,
                onRemove = onRemovePartMobility,
                onUpdate = onUpdatePartMobility,
                onMove = onMovePartMobility,
                onUpdateConfig = onUpdatePartMobilityConfig,
            )
        }
    }

    if (showDeleteConfirm) {
        KpknAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Eliminar grupo",
            text = "¿Eliminar este grupo?",
            confirmLabel = "Eliminar",
            onConfirm = {
                showDeleteConfirm = false
                onRemove(false)
            },
            dismissLabel = "Cancelar",
            onDismiss = { showDeleteConfirm = false },
            destructive = true,
        )
    }

    if (showDeleteModeConfirm) {
        KpknAlertDialog(
            onDismissRequest = { showDeleteModeConfirm = false },
            title = { Text("¿Qué hacemos con los ejercicios?", style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Text(
                    "Puedes conservarlos sin grupo o borrar también todo su contenido.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpknSecondaryPill(
                        text = "Conservar ejercicios",
                        onClick = {
                            onRemove(true)
                            showDeleteModeConfirm = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknDangerPill(
                        text = "Borrar grupo y ejercicios",
                        onClick = {
                            onRemove(false)
                            showDeleteModeConfirm = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            dismissButton = {
                KpknAlertDismissButton(
                    text = "Cancelar",
                    onClick = { showDeleteModeConfirm = false },
                )
            },
        )
    }
}

/**
 * D9 · movilidad de la sesión: el grupo de movilidad se edita dentro de su módulo con las mismas fichas 85 × 76 que
 * el ejercicio (H2) y con el tiempo global, el catálogo, la reordenación, la dosis y el quitar dentro del grupo.
 */
@Composable
private fun MobilityGroupInlineEditor(
    part: SessionPart,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (MobilitySeries) -> MobilitySeries) -> Unit,
    onMove: (String, Int) -> Unit,
    onUpdateConfig: (MobilityConfig) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .testTag("session-mobility-group"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Movilidad global · ${part.mobilitySeries.size} movimiento${if (part.mobilitySeries.size == 1) "" else "s"}",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            MobilityGlobalTimeButton(
                mobilityConfig = part.mobilityConfig,
                onUpdateConfig = onUpdateConfig,
            )
        }
        MobilityPreparationChips(
            exerciseId = part.id,
            series = part.mobilitySeries,
            onUpdate = onUpdate,
            onMove = onMove,
            onRemove = onRemove,
            onAdd = onAdd,
        )
        if (part.mobilitySeries.isEmpty()) {
            Text(
                "Sin movimientos. Elige uno del catálogo.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}

internal fun buildSessionExerciseEditorBlocks(
    session: com.example.kpkn.data.models.Session,
    containerExercises: List<com.example.kpkn.data.models.Exercise>,
): List<com.example.kpkn.screens.sessioneditor.SessionExerciseEditorBlock> {
    return containerExercises.map { exercise -> com.example.kpkn.screens.sessioneditor.SessionExerciseEditorBlock.Single(exercise) }
}
