package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.restoreExerciseSetAtRound
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknTextAction
import java.util.UUID

/**
 * Superserie en la lista (SESSION_EDITOR_R1 §3.4 y E13). La cabecera va siempre: «Superserie · 3 rondas · 1:00 / 2:00», el asa que mueve el
 * bloque entero y un «⋯» con las acciones del original. Los miembros se ven siempre, unidos por un raíl de 3 dp al 30 %. «Rondas y
 * descansos» (o tocar la cabecera) despliega lo que antes abría el original: los chips de miembros, la configuración y las rondas.
 */
@Composable
internal fun SupersetGroupEditorCard(
    group: SupersetGroup,
    exercises: List<Exercise>,
    accentHex: String?,
    partId: String?,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onBoundsChange: (Rect) -> Unit = {},
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    // Default must NOT commit (N2): cancel is a no-op unless the caller wires cancelExerciseDrag.
    onDragCancel: () -> Unit = {},
    onOpenSupersetCreator: (String?, List<String>) -> Unit,
    onUpdateSupersetRest: (String, Int?, Int?, Int?) -> Unit,
    onUpdateRoundRest: (String, Int, Int?, Int?) -> Unit = { _, _, _, _ -> },
    onToggleOptional: (String) -> Unit = {},
    onUpdateExercise: (String, (Exercise) -> Exercise) -> Unit = { _, _ -> },
    onAddSet: (String) -> Unit = {},
    onUpdateSet: (String, String, (ExerciseSet) -> ExerciseSet) -> Unit = { _, _, _ -> },
    onRemoveSet: (String, String) -> Unit = { _, _ -> },
    onMoveSet: (String, String, Int) -> Unit = { _, _, _ -> },
    onRemoveRound: (Int) -> Unit = {},
    relationshipAnchorName: (Exercise) -> String? = { null },
    onOpenRelationshipPicker: (String) -> Unit = {},
    onClearRelationship: (String) -> Unit = {},
    onRemoveFromSuperset: (String, String) -> Unit,
    onDeleteExerciseFromSuperset: (String, String) -> Unit = { _, _ -> },
    onDissolve: (String) -> Unit,
    onDeleteGroup: (String) -> Unit = {},
    onAddRound: () -> Unit,
    onReorderMembers: (List<String>) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var roundsOpen by rememberSaveable(group.id) { mutableStateOf(false) }
    var configExerciseId by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    var showOptionalInfo by rememberSaveable(group.id) { mutableStateOf(false) }
    var pendingRemovalExerciseId by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    var showDeleteGroupDialog by rememberSaveable(group.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var moreAnchor by remember { mutableStateOf<Rect?>(null) }
    val haptics = LocalHapticFeedback.current
    // Sin color de grupo (ER1b): la superserie es tinta sobre su módulo.
    val accentColor = WizardColors.text
    val rounds = (group.rounds ?: exercises.maxOfOrNull { it.sets.size } ?: 1).coerceAtLeast(1)
    val pendingRemovalExercise = pendingRemovalExerciseId?.let { id ->
        exercises.firstOrNull { it.id == id }
    }
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDrag = rememberUpdatedState(onDrag)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val latestOnDragCancel = rememberUpdatedState(onDragCancel)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .graphicsLayer {
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                alpha = if (isDragging) 0.22f else 1f
            }
            .zIndex(if (isDragging) 12f else 0f),
    ) {
        // Cabecera del bloque: el asa mueve la superserie entera, la línea abre el panel y el «⋯» tiene el resto.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var handleWindowOrigin by remember(group.id) { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        handleWindowOrigin = Offset(b.left, b.top)
                    }
                    .pointerInput(group.id) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestOnDragStart.value(handleWindowOrigin + offset)
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
                                latestOnDrag.value(Offset(dragAmount.x, dragAmount.y))
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Grip,
                    contentDescription = "Reordenar superserie",
                    tint = if (isDragging) WizardColors.text else WizardColors.textFaint,
                    modifier = Modifier.size(24.dp),
                )
            }
            SwipeToDeleteCard(
                onDelete = { showDeleteGroupDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                animateDeletion = false,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KpknSurface.module, RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { roundsOpen = !roundsOpen },
                            onLongClick = { roundsOpen = !roundsOpen },
                        )
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = WizardColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) {
                                append("Superserie")
                            }
                            withStyle(SpanStyle(color = WizardColors.textMuted)) {
                                append(" · $rounds ronda${if (rounds == 1) "" else "s"}")
                                append(" · ${KpknSetFormat.rest(group.restBetweenExercises)} / ${KpknSetFormat.rest(group.restAfterSuperset)}")
                            }
                        },
                        style = KpknType.caption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (group.isOptional) {
                        Text(
                            text = "OPCIONAL",
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                        )
                    }
                }
            }
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Más opciones de la superserie",
                onClick = { menuOpen = true },
                modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInWindow() },
            )
        }

        // Miembros: siempre a la vista, unidos por el raíl (el único cambio frente al original).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp)
                .drawBehind {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.30f),
                        size = Size(3.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                },
            content = content,
        )

        // «Rondas y descansos»: lo que abría el original al desplegar la superserie.
        AnimatedVisibility(
            visible = roundsOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(exercises, key = { it.id }) { exercise ->
                        SupersetMemberChip(
                            name = exercise.displayNameWithSelectedChips(),
                            selected = configExerciseId == exercise.id,
                            onToggleConfig = {
                                configExerciseId = if (configExerciseId == exercise.id) null else exercise.id
                            },
                            onRemove = { pendingRemovalExerciseId = exercise.id },
                            onMoveUp = if (exercises.indexOfFirst { it.id == exercise.id } > 0) {
                                {
                                    val ids = exercises.map { it.id }.toMutableList()
                                    val index = ids.indexOf(exercise.id)
                                    val moved = ids.removeAt(index)
                                    ids.add(index - 1, moved)
                                    onReorderMembers(ids)
                                }
                            } else {
                                null
                            },
                            onMoveDown = if (exercises.indexOfFirst { it.id == exercise.id } in 0 until exercises.lastIndex) {
                                {
                                    val ids = exercises.map { it.id }.toMutableList()
                                    val index = ids.indexOf(exercise.id)
                                    val moved = ids.removeAt(index)
                                    ids.add(index + 1, moved)
                                    onReorderMembers(ids)
                                }
                            } else {
                                null
                            },
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(KpknSurface.inset)
                                .border(1.dp, WizardColors.text.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                .clickable(enabled = exercises.size < 4) {
                                    onOpenSupersetCreator(partId, exercises.map { it.id })
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+ Añadir", style = KpknType.caption, color = WizardColors.text)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = configExerciseId != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    exercises.firstOrNull { it.id == configExerciseId }?.let { selected ->
                        SupersetExerciseConfigOverlay(
                            exercise = selected,
                            accentColor = accentColor,
                            relationshipAnchorName = relationshipAnchorName(selected),
                            onUpdateExercise = { updater -> onUpdateExercise(selected.id, updater) },
                            onUpdateSet = { setId, updater -> onUpdateSet(selected.id, setId, updater) },
                            onOpenRelationshipPicker = { onOpenRelationshipPicker(selected.id) },
                            onClearRelationship = { onClearRelationship(selected.id) },
                            onDismiss = { configExerciseId = null },
                        )
                    }
                }

                SupersetRoundsCarousel(
                    group = group,
                    exercises = exercises,
                    rounds = rounds,
                    accentColor = accentColor,
                    onUpdateRoundRest = { roundIndex, restBetween, restAfter -> onUpdateRoundRest(group.id, roundIndex, restBetween, restAfter) },
                    onUpdateSet = onUpdateSet,
                    onRemoveSet = onRemoveSet,
                    onMoveSet = onMoveSet,
                    onRestoreSet = { exerciseId, roundIndex ->
                        onUpdateExercise(exerciseId) { current ->
                            val template = current.sets.getOrNull((roundIndex - 1).coerceAtLeast(0))
                                ?: current.sets.lastOrNull()
                            val restored = template?.copy(id = UUID.randomUUID().toString(), isEmptySlot = false)
                                ?: ExerciseSet(id = UUID.randomUUID().toString(), targetReps = 8)
                            current.copy(sets = restoreExerciseSetAtRound(current.sets, roundIndex, restored))
                        }
                    },
                    onAddRound = onAddRound,
                    onRemoveRound = onRemoveRound,
                )
            }
        }
    }

    val anchor = moreAnchor
    if (menuOpen && anchor != null) {
        KpknContextMenu(anchor = anchor, rows = 4, onDismiss = { menuOpen = false }) {
            KpknContextMenuRow(
                label = "Rondas y descansos",
                onClick = {
                    menuOpen = false
                    roundsOpen = true
                },
            )
            KpknContextMenuRow(
                label = if (group.isOptional) "Dejar de ser opcional" else "Convertir en opcional",
                onClick = {
                    menuOpen = false
                    showOptionalInfo = true
                },
            )
            KpknContextMenuRow(
                label = "Disolver",
                onClick = {
                    menuOpen = false
                    onDissolve(group.id)
                },
            )
            KpknContextMenuRow(
                label = "Eliminar superserie",
                destructive = true,
                onClick = {
                    menuOpen = false
                    showDeleteGroupDialog = true
                },
            )
        }
    }

    if (showOptionalInfo) {
        KpknAlertDialog(
            onDismissRequest = { showOptionalInfo = false },
            title = {
                Text(
                    if (group.isOptional) "Superserie opcional" else "Convertir en superserie opcional",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Text(
                    "Una superserie opcional es un tipo de superserie que se muestra únicamente cuando la activas " +
                        "desde tu sesión de entreno; si no la invocas, te mostrará los ejercicios separados. " +
                        "Esto puede ser útil si quieres usar la superserie únicamente para cuando tienes poco tiempo " +
                        "y no como parte de tu programación recurrente.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                KpknAlertConfirmButton(
                    text = if (group.isOptional) "Dejar de ser opcional" else "Convertir",
                    onClick = {
                        onToggleOptional(group.id)
                        showOptionalInfo = false
                    },
                )
            },
            dismissButton = {
                KpknAlertDismissButton(text = "Cancelar", onClick = { showOptionalInfo = false })
            },
        )
    }

    pendingRemovalExercise?.let { exercise ->
        KpknAlertDialog(
            onDismissRequest = { pendingRemovalExerciseId = null },
            title = {
                Text("Eliminar ejercicio de la superserie", style = KpknType.dialogTitle, color = WizardColors.text)
            },
            text = {
                Text(
                    if (exercises.size == 2) {
                        "Esta superserie tiene 2 ejercicios. Si conservas «${exercise.displayNameWithSelectedChips()}», " +
                            "la superserie se disolverá y ambos quedarán como ejercicios independientes. " +
                            "También puedes borrarlo de la sesión."
                    } else {
                        "¿Qué quieres hacer con «${exercise.displayNameWithSelectedChips()}»? Puedes conservarlo " +
                            "como ejercicio individual o borrarlo de la sesión."
                    },
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KpknAlertConfirmButton(
                        text = "Borrar ejercicio",
                        onClick = {
                            pendingRemovalExerciseId = null
                            onDeleteExerciseFromSuperset(group.id, exercise.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertDismissButton(
                        text = "Conservar como individual",
                        onClick = {
                            pendingRemovalExerciseId = null
                            onRemoveFromSuperset(group.id, exercise.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertDismissButton(
                        text = "Cancelar",
                        onClick = { pendingRemovalExerciseId = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    if (showDeleteGroupDialog) {
        KpknAlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text("Eliminar superserie", style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Text(
                    "Puedes conservar sus ${exercises.size} ejercicios como individuales o borrarlos también de la sesión.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KpknAlertDismissButton(
                        text = "Conservar ejercicios",
                        onClick = {
                            showDeleteGroupDialog = false
                            onDissolve(group.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertConfirmButton(
                        text = "Borrar superserie y ejercicios",
                        onClick = {
                            showDeleteGroupDialog = false
                            onDeleteGroup(group.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknAlertDismissButton(
                        text = "Cancelar",
                        onClick = { showDeleteGroupDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

/**
 * Chip de un miembro dentro del panel «Rondas y descansos»: su nombre (toca para configurarlo), la X para quitarlo y, si puede, las flechas
 * de orden.
 */
@Composable
private fun SupersetMemberChip(
    name: String,
    selected: Boolean,
    onToggleConfig: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) WizardColors.text.copy(alpha = 0.14f) else KpknSurface.inset)
            .border(1.dp, WizardColors.text.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = name,
            style = KpknType.caption,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 170.dp)
                .clickable(onClick = onToggleConfig),
        )
        onMoveUp?.let {
            KpknIconButton(icon = KpknLineIcons.ChevronUp, contentDescription = "Subir en el orden", onClick = it)
        }
        onMoveDown?.let {
            KpknIconButton(icon = KpknLineIcons.ChevronDown, contentDescription = "Bajar en el orden", onClick = it)
        }
        KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Quitar de superserie", onClick = onRemove)
    }
}
