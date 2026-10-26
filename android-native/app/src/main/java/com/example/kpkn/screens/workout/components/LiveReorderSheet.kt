package com.example.kpkn.screens.workout.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.LiveSessionReorder
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.GodModeUndoSnapshot
import com.example.kpkn.screens.workout.ReorderRow
import com.example.kpkn.screens.workout.ReorderStatus
import com.example.kpkn.screens.workout.buildReorderRows
import com.example.kpkn.screens.workout.lockedExerciseIds
import com.example.kpkn.screens.workout.sanitizeReorder
import com.example.kpkn.screens.workout.selectedExerciseIds
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * «Orden de la sesión» (LV11, A3; prototipo C3): la hoja que se abre con «Reordenar». Una fila por ejercicio (una superserie es UNA fila
 * y se mueve entera); se arrastra desde el asa: la fila se eleva, las demás se apartan con animación y una marca de trazos dice dónde
 * caerá. Lo hecho y lo que está en curso queda FIJO arriba (sin asa, atenuado, con ✓ o «en curso · 1/4») y nada puede ponerse delante.
 *
 * «Seleccionar» abre el modo de selección con las acciones por lotes que antes eran del modo dios: Superserie (con 2 o más), Omitir,
 * Quitar y Ultrarrápido. «Listo» aplica el orden (quien llama decide el aviso de «Cambio de grupo» y la persistencia).
 *
 * El orden que se edita es [order] (la lista plana de ids) y cada cambio sale por [onOrderChange] ya saneado con las reglas de la hoja
 * ([sanitizeReorder]): lo fijo no se mueve, nada se pone delante y el cardio no se mezcla con la fuerza. Sin arrastre (lectores de
 * pantalla), el asa ofrece las acciones «Subir» y «Bajar».
 */
@Composable
internal fun LiveReorderSheet(
    order: List<String>,
    exercises: Map<String, Exercise>,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
    onOrderChange: (List<String>) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onCreateSuperset: (List<String>) -> Unit,
    onSkip: (List<String>) -> Unit,
    onRemove: (List<String>) -> Unit,
    onUltraFast: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    supersetRounds: (groupId: String) -> Int? = { null },
    displayName: (Exercise) -> String = { it.name },
    hazeState: HazeState? = null,
    undoStack: List<GodModeUndoSnapshot> = emptyList(),
    onUndo: (stackIndex: Int) -> Unit = {},
) {
    val presentOrder = remember(order, exercises) { order.filter { it in exercises } }
    val rows = remember(presentOrder, exercises, completedSets, omittedSetKeys) {
        buildReorderRows(presentOrder, exercises, completedSets, omittedSetKeys, supersetRounds, displayName)
    }
    val locked = remember(rows) { lockedExerciseIds(rows) }
    val groupOf: (String) -> String? = remember(exercises) { { id -> exercises[id]?.supersetGroupRefOrLegacyId() } }
    val isCardio: (String) -> Boolean = remember(exercises) { { id -> exercises[id]?.isCardio == true } }

    var selecting by remember { mutableStateOf(false) }
    var markedKeys by remember { mutableStateOf(emptySet<String>()) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    // Lo marcado que ya no existe o ya es fijo (se hizo una serie con la hoja abierta) deja de contar.
    val marked = remember(markedKeys, rows) { markedKeys.filter { key -> rows.any { it.key == key && !it.locked } }.toSet() }
    val selectedIds = remember(rows, marked) { selectedExerciseIds(rows, marked) }

    val latestOrder by rememberUpdatedState(presentOrder)
    val latestRows by rememberUpdatedState(rows)
    val latestLocked by rememberUpdatedState(locked)

    /** Aplica un orden pedido pasándolo antes por las reglas de la hoja; si no cambia nada, no hace nada. */
    fun requestOrder(candidate: List<String>) {
        val next = sanitizeReorder(candidate, latestOrder, latestLocked, isCardio)
        if (next != latestOrder) onOrderChange(next)
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val reduced = wizardReducedMotion()
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableFloatStateOf(0f) }

    /** Mira sobre qué fila está el centro de la fila arrastrada y, si es otra, la cambia de sitio. */
    fun evaluateDrag() {
        val key = draggingKey ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return
        val center = dragTop + dragHeight / 2f
        val target = when {
            center < visible.first().offset -> visible.first()
            center >= visible.last().offset + visible.last().size -> visible.last()
            else -> visible.firstOrNull { center >= it.offset && center < it.offset + it.size }
        } ?: return
        val targetKey = target.key as? String ?: return
        if (targetKey == key) return
        val from = latestRows.indexOfFirst { it.key == key }
        val to = latestRows.indexOfFirst { it.key == targetKey }
        if (from < 0 || to < 0) return
        requestOrder(LiveSessionReorder.moveUnitTo(latestOrder, groupOf, from, to))
    }

    // Cerca del borde de la lista, arrastrar la desplaza sola.
    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        while (isActive && draggingKey != null) {
            val viewport = listState.layoutInfo.viewportSize.height.toFloat()
            val bottom = dragTop + dragHeight
            val delta = when {
                dragTop < edge -> -((edge - dragTop).coerceAtMost(edge)) * 0.25f
                bottom > viewport - edge -> ((bottom - (viewport - edge)).coerceAtMost(edge)) * 0.25f
                else -> 0f
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                evaluateDrag()
            }
            delay(16)
        }
    }

    fun finishBatch() {
        selecting = false
        markedKeys = emptySet()
    }

    KpknSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        maxHeightFraction = 0.88f,
        stableHeightFraction = 0.88f,
        hazeState = hazeState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Atrás sale primero de la selección y después cierra la hoja. Va dentro del contenido de la hoja para registrarse DESPUÉS
            // del Atrás de la propia hoja (el último registrado es el primero que responde).
            BackHandler(enabled = selecting) {
                selecting = false
                markedKeys = emptySet()
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Orden de la sesión",
                        style = KpknType.dialogTitle,
                        color = WizardColors.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    KpknCompactPill(text = "Listo", onClick = onDone, height = 32.dp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selecting) {
                            when (selectedIds.size) {
                                0 -> "Marca los ejercicios"
                                1 -> "1 ejercicio marcado"
                                else -> "${selectedIds.size} ejercicios marcados"
                            }
                        } else {
                            "Arrastra desde el asa"
                        },
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    KpknChip(
                        text = "Seleccionar",
                        selected = selecting,
                        onClick = {
                            selecting = !selecting
                            markedKeys = emptySet()
                            expandedKey = null
                        },
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = draggingKey == null,
                ) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        val dragging = row.key == draggingKey
                        val itemModifier = if (dragging) {
                            Modifier.zIndex(1f)
                        } else if (reduced) {
                            Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                        } else {
                            Modifier.animateItem(
                                fadeInSpec = tween(200),
                                placementSpec = tween(260, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(160),
                            )
                        }
                        ReorderRowItem(
                            row = row,
                            dragging = dragging,
                            selecting = selecting,
                            selected = row.key in marked,
                            expanded = expandedKey == row.key,
                            // La fila arrastrada va donde está el dedo; su hueco, en la lista, queda como marca de «soltar aquí».
                            dragOffset = {
                                if (!dragging) 0f else {
                                    val slot = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }?.offset ?: 0
                                    dragTop - slot
                                }
                            },
                            modifier = itemModifier,
                            onClick = {
                                when {
                                    row.locked -> Unit
                                    selecting -> markedKeys = if (row.key in marked) marked - row.key else marked + row.key
                                    row.isSuperset -> expandedKey = if (expandedKey == row.key) null else row.key
                                }
                            },
                            onDragStart = {
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }
                                if (info != null) {
                                    dragTop = info.offset.toFloat()
                                    dragHeight = info.size.toFloat()
                                    draggingKey = row.key
                                    expandedKey = null
                                }
                            },
                            onDrag = { dy ->
                                if (draggingKey == row.key) {
                                    dragTop += dy
                                    evaluateDrag()
                                }
                            },
                            onDragEnd = { if (draggingKey == row.key) draggingKey = null },
                            onMove = { delta ->
                                requestOrder(LiveSessionReorder.moveUnitTo(latestOrder, groupOf, index, index + delta))
                            },
                            onMoveMember = { memberId, delta ->
                                requestOrder(LiveSessionReorder.moveMemberWithinUnit(latestOrder, groupOf, memberId, delta))
                            },
                        )
                    }
                }

                if (selecting) {
                    BatchActionBar(
                        selectedCount = selectedIds.size,
                        onCreateSuperset = {
                            onCreateSuperset(selectedIds)
                            finishBatch()
                        },
                        onSkip = {
                            onSkip(selectedIds)
                            finishBatch()
                        },
                        onRemove = {
                            onRemove(selectedIds)
                            finishBatch()
                        },
                        onUltraFast = {
                            onUltraFast(selectedIds)
                            finishBatch()
                        },
                    )
                } else {
                    Text(
                        text = "Lo hecho y lo que está en curso queda fijo. El orden nuevo se ve en el carrusel y en «Siguiente».",
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                }
            }
            // «Deshacer» de lo que se acaba de hacer aquí dentro: la hoja tapa la píldora de la sesión.
            LiveUndoPillHost(
                undoStack = undoStack,
                completedSets = completedSets,
                onUndo = onUndo,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp),
            )
        }
    }
}

/** Una fila de la hoja. [dragOffset] es lo que la separa de su hueco mientras la arrastran (se lee al dibujar, no al componer). */
@Composable
private fun ReorderRowItem(
    row: ReorderRow,
    dragging: Boolean,
    selecting: Boolean,
    selected: Boolean,
    expanded: Boolean,
    dragOffset: () -> Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMove: (delta: Int) -> Unit,
    onMoveMember: (memberId: String, delta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val rowFill = when {
        dragging -> Color(0xFF232323)
        row.locked -> KpknSurface.module
        else -> KpknSurface.modulePressed
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // La marca de «soltar aquí»: el hueco de la fila que se arrastra.
            .then(
                if (dragging) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = WizardColors.text.copy(alpha = 0.25f),
                            cornerRadius = CornerRadius(18.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .graphicsLayer {
                    translationY = dragOffset()
                    if (dragging) {
                        rotationZ = -1.2f
                        shadowElevation = 18.dp.toPx()
                        this.shape = rowShape
                        clip = false
                    }
                    alpha = if (row.status == ReorderStatus.DONE) 0.55f else 1f
                }
                .clip(rowShape)
                .background(rowFill, rowShape)
                .then(if (dragging) Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.18f), rowShape) else Modifier)
                .then(
                    if (!row.locked && (selecting || row.isSuperset)) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = if (selecting) Role.Checkbox else Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(status = row.status, selecting = selecting && !row.locked, selected = selected)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = KpknType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 19.sp),
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when {
                    dragging && row.subtitle != null -> "${row.subtitle} · soltar aquí"
                    dragging -> "soltar aquí"
                    else -> row.subtitle
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (expanded && row.isSuperset) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        Text(
                            text = "Orden dentro de la superserie".uppercase(),
                            style = KpknType.eyebrow.copy(fontSize = 11.sp),
                            color = WizardColors.textFaint,
                        )
                        row.memberIds.forEachIndexed { memberIndex, memberId ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${memberIndex + 1}. ${row.memberNames.getOrElse(memberIndex) { memberId }}",
                                    style = KpknType.caption,
                                    color = WizardColors.text,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MemberArrow(up = true, enabled = memberIndex > 0) { onMoveMember(memberId, -1) }
                                MemberArrow(up = false, enabled = memberIndex < row.memberIds.lastIndex) { onMoveMember(memberId, 1) }
                            }
                        }
                    }
                }
            }
            when {
                row.locked -> Icon(
                    imageVector = LiveOptionIcons.Lock,
                    contentDescription = "Fijo",
                    modifier = Modifier.size(16.dp),
                    tint = WizardColors.text.copy(alpha = 0.5f),
                )
                selecting -> Unit
                else -> DragHandle(
                    dragging = dragging,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onMove = onMove,
                )
            }
        }
    }
}

/** El círculo del principio de la fila: ✓ verde si está hecho, anillo de tinta si está en curso, anillo tenue si falta y, al seleccionar, la casilla. */
@Composable
private fun StatusDot(status: ReorderStatus, selecting: Boolean, selected: Boolean) {
    val size = 22.dp
    when {
        status == ReorderStatus.DONE -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(SymbolPalette.ok, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KpknLineIcons.Check, contentDescription = "Hecho", modifier = Modifier.size(14.dp), tint = SymbolPalette.onOk)
        }
        selecting -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (selected) WizardColors.text else Color.Transparent, CircleShape)
                .border(2.dp, if (selected) Color.Transparent else WizardColors.text.copy(alpha = 0.40f), CircleShape)
                .semantics { contentDescription = if (selected) "Marcado" else "Sin marcar" },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(KpknLineIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = WizardColors.ctaContent)
        }
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(
                    2.dp,
                    if (status == ReorderStatus.IN_PROGRESS) WizardColors.text else WizardColors.text.copy(alpha = 0.25f),
                    CircleShape,
                ),
        )
    }
}

/**
 * El asa: se arrastra desde ella. Para quien no puede arrastrar, ofrece «Subir» y «Bajar» (acciones de accesibilidad) que mueven la fila
 * un puesto. La zona táctil es de 48 dp.
 */
@Composable
private fun DragHandle(
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMove: (delta: Int) -> Unit,
) {
    val latestStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "Arrastrar para reordenar"
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction("Subir") {
                        onMove(-1)
                        true
                    },
                    CustomAccessibilityAction("Bajar") {
                        onMove(1)
                        true
                    },
                )
            }
            // Tocar el asa sin arrastrar no hace nada (no abre la superserie de la fila).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { latestStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        latestDrag(amount.y)
                    },
                    onDragEnd = { latestEnd() },
                    onDragCancel = { latestEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = KpknLineIcons.Grip,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (dragging) WizardColors.text else WizardColors.text.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun MemberArrow(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = if (up) "Subir miembro" else "Bajar miembro" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (up) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = WizardColors.text.copy(alpha = if (enabled) 0.8f else 0.25f),
        )
    }
}

/**
 * Las acciones por lotes que antes tenía la barra del modo dios: ahora, con lo marcado en la hoja. Van de dos en dos y del mismo ancho:
 * con cuatro en una fila la última se cortaba (y con la letra grande, la mitad), y una barra que se desplaza esconde una acción.
 */
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onCreateSuperset: () -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit,
    onUltraFast: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatchPill("Superserie", onCreateSuperset, enabled = selectedCount >= 2, modifier = Modifier.weight(1f))
            BatchPill("Omitir", onSkip, enabled = selectedCount >= 1, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatchPill("Quitar", onRemove, enabled = selectedCount >= 1, danger = true, modifier = Modifier.weight(1f))
            BatchPill("Aplicar técnica", onUltraFast, enabled = selectedCount >= 1, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BatchPill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .clip(KpknShape.pill)
                .border(
                    1.dp,
                    if (danger) WizardColors.danger.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
                    KpknShape.pill,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) WizardColors.danger else WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
