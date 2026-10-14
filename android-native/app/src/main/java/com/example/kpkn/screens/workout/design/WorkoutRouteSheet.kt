package com.example.kpkn.screens.workout.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * «Ruta de la sesión» (KPKN LS4, §4): hoja casi a pantalla completa con el cuerpo [WorkoutRouteBody]. Se abre desde la barra de
 * progreso y desde la isla inferior. Atrás y tocar fuera la cierran (la hoja lo resuelve).
 */
@Composable
fun WorkoutRouteSheet(
    summary: String,
    groups: List<WorkoutRouteGroup>,
    accent: Color,
    godModeUndoCount: Int,
    onClose: () -> Unit,
    onSelectExercise: (Int) -> Unit,
    onSelectStep: (String) -> Unit,
    onOpenContext: (String) -> Unit,
    onOpenMemberContext: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onCreateSuperset: () -> Unit,
    onSkip: (() -> Unit)?,
    onBatchCreateSuperset: (List<String>) -> Unit,
    onBatchSkip: (List<String>) -> Unit,
    onBatchDelete: (List<String>) -> Unit,
    onBatchUltraFast: (List<String>, SeriesTechnique) -> Unit,
    onDissolveSuperset: (String) -> Unit,
    onRevertGodModeAction: (Int) -> Unit,
) {
    KpknSheet(onDismissRequest = onClose, stableHeightFraction = 0.92f) {
        WorkoutRouteBody(
            summary = summary,
            groups = groups,
            accent = accent,
            godModeUndoCount = godModeUndoCount,
            onClose = onClose,
            onSelectExercise = onSelectExercise,
            onSelectStep = onSelectStep,
            onOpenContext = onOpenContext,
            onOpenMemberContext = onOpenMemberContext,
            onReorder = onReorder,
            onAdd = onAdd,
            onCreateSuperset = onCreateSuperset,
            onSkip = onSkip,
            onBatchCreateSuperset = onBatchCreateSuperset,
            onBatchSkip = onBatchSkip,
            onBatchDelete = onBatchDelete,
            onBatchUltraFast = onBatchUltraFast,
            onDissolveSuperset = onDissolveSuperset,
            onRevertGodModeAction = onRevertGodModeAction,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Cuerpo de la ruta (KPKN LS4, §4), sin ViewModel: título, resumen y «Seleccionar»; la lista de bloques (reordenar con el asa, superseries
 * con su barra); y al pie, las acciones o la isla del modo dios. Lo que pasa fuera (elegir, reordenar, superseries, omitir, eliminar,
 * deshacer) llega por lambdas; el estado de la pantalla (modo dios, selección, arrastre) vive aquí.
 *
 * [liftedGroupKey] solo lo usa la hoja de render: deja un bloque levantado, como si se arrastrara.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun WorkoutRouteBody(
    summary: String,
    groups: List<WorkoutRouteGroup>,
    accent: Color,
    godModeUndoCount: Int,
    onClose: () -> Unit,
    onSelectExercise: (Int) -> Unit,
    onSelectStep: (String) -> Unit,
    onOpenContext: (String) -> Unit,
    onOpenMemberContext: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onCreateSuperset: () -> Unit,
    onSkip: (() -> Unit)?,
    onBatchCreateSuperset: (List<String>) -> Unit,
    onBatchSkip: (List<String>) -> Unit,
    onBatchDelete: (List<String>) -> Unit,
    onBatchUltraFast: (List<String>, SeriesTechnique) -> Unit,
    onDissolveSuperset: (String) -> Unit,
    onRevertGodModeAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
    liftedGroupKey: String? = null,
) {
    val reduced = wizardReducedMotion()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgePx = with(density) { 56.dp.toPx() }
    val scrollStepPx = with(density) { 12.dp.toPx() }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingUltraFast by remember { mutableStateOf(false) }
    var pendingDissolveGroupId by remember { mutableStateOf<String?>(null) }
    val currentGroupIndex = groups.indexOfFirst { group -> group.members.any { it.isCurrent } }.coerceAtLeast(0)
    // Abre desde arriba; solo se desplaza si el bloque actual queda hacia el final de la lista.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (currentGroupIndex >= 4) currentGroupIndex - 1 else 0)
    val firstCardioIndex = groups.indexOfFirst { it.isCardio }

    // Arrastre: el bloque levantado, su desplazamiento y el asentamiento con resorte al soltarlo.
    var dragKey by remember { mutableStateOf(liftedGroupKey) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var settleKey by remember { mutableStateOf<String?>(null) }
    val settle = remember { Animatable(0f) }
    var autoScroll by remember { mutableIntStateOf(0) }
    val latestGroups by rememberUpdatedState(groups)
    val latestOnReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(autoScroll, dragKey) {
        while (autoScroll != 0 && dragKey != null) {
            listState.animateScrollBy(autoScroll * scrollStepPx)
            delay(16)
        }
    }

    fun startDrag(key: String) {
        settleKey = null
        dragKey = key
        dragPx = 0f
        autoScroll = 0
    }

    fun moveDrag(key: String, acc: Float) {
        dragPx = acc
        val fromIndex = latestGroups.indexOfFirst { it.key == key }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == fromIndex }
        autoScroll = if (item == null) {
            0
        } else {
            val center = item.offset + item.size / 2f + acc
            when {
                center < info.viewportStartOffset + edgePx && fromIndex > 0 -> -1
                center > info.viewportEndOffset - edgePx && fromIndex < latestGroups.lastIndex -> 1
                else -> 0
            }
        }
    }

    fun endDrag(key: String, acc: Float) {
        autoScroll = 0
        val groupsNow = latestGroups
        val fromIndex = groupsNow.indexOfFirst { it.key == key }
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.index == fromIndex }
        // El destino es el bloque cuyo centro queda más cerca del centro del bloque levantado.
        val target = if (fromIndex < 0 || dragged == null) {
            fromIndex
        } else {
            val center = dragged.offset + dragged.size / 2f + acc
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: fromIndex
        }
        dragKey = null
        dragPx = 0f
        settleKey = key
        scope.launch {
            if (reduced) {
                settle.snapTo(0f)
            } else {
                settle.snapTo(acc)
                settle.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            settleKey = null
        }
        if (fromIndex >= 0 && target in groupsNow.indices && target != fromIndex) {
            latestOnReorder(workoutRouteOrderedIds(groupsNow, fromIndex, target))
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ruta de la sesión",
                    style = KpknType.title,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = summary,
                    style = KpknType.caption.copy(fontFeatureSettings = "tnum"),
                    color = WizardColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (groups.isNotEmpty()) {
                if (selecting) {
                    KpknTextAction(text = "Listo", onClick = {
                        selecting = false
                        selectedIds = emptySet()
                    })
                } else {
                    KpknTextAction(text = "Seleccionar", onClick = { selecting = true })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
            userScrollEnabled = dragKey == null,
        ) {
            itemsIndexed(groups, key = { _, group -> group.key }) { index, group ->
                val lifted = dragKey == group.key
                val lift = when (group.key) {
                    dragKey -> dragPx
                    settleKey -> settle.value
                    else -> 0f
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            placementSpec = if (reduced) null else spring<IntOffset>(stiffness = Spring.StiffnessMediumLow),
                        )
                        .graphicsLayer {
                            translationY = lift
                            if (lifted) {
                                scaleX = 0.985f
                                scaleY = 0.985f
                                shadowElevation = 8f
                                shape = RoundedCornerShape(16.dp)
                                clip = false
                            }
                        }
                        .then(if (lifted) Modifier.background(KpknSurface.module, RoundedCornerShape(16.dp)) else Modifier),
                ) {
                    if (group.isCardio && index == firstCardioIndex) CardioDivider()
                    if (group.isSuperset) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.caption.orEmpty(),
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            val groupId = group.groupId
                            if (selecting && groupId != null) {
                                KpknTextAction(
                                    text = "Disolver",
                                    onClick = { pendingDissolveGroupId = groupId },
                                    color = WizardColors.textMuted,
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().then(
                            if (group.isSuperset) Modifier.drawBehind { drawSupersetRail(accent) } else Modifier,
                        ),
                    ) {
                        group.members.forEach { member ->
                            val onLongPress: (() -> Unit)? = if (selecting) {
                                null
                            } else {
                                ({
                                    onClose()
                                    if (group.isSuperset) onOpenMemberContext(member.exerciseId) else onOpenContext(member.exerciseId)
                                })
                            }
                            WorkoutRouteRow(
                                member = member,
                                selecting = selecting,
                                selected = member.exerciseId in selectedIds,
                                onTap = {
                                    if (selecting) {
                                        selectedIds = if (member.exerciseId in selectedIds) {
                                            selectedIds - member.exerciseId
                                        } else {
                                            selectedIds + member.exerciseId
                                        }
                                    } else {
                                        val stepKey = member.stepKey
                                        if (group.isSuperset && stepKey != null) {
                                            onSelectStep(stepKey)
                                        } else {
                                            onSelectExercise(member.exerciseIndex)
                                        }
                                        onClose()
                                    }
                                },
                                onLongPress = onLongPress,
                                showHandle = !selecting,
                                handleGesture = Modifier.routeGripGesture(
                                    enabled = !selecting,
                                    key = group.key,
                                    onStart = { startDrag(group.key) },
                                    onMove = { acc -> moveDrag(group.key, acc) },
                                    onEnd = { acc -> endDrag(group.key, acc) },
                                ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = selecting,
            transitionSpec = {
                fadeIn(tween(if (reduced) 120 else 180)) togetherWith fadeOut(tween(if (reduced) 120 else 140))
            },
            label = "routeBottom",
        ) { isSelecting ->
            if (isSelecting) {
                WorkoutRouteBatchIsland(
                    selectedCount = selectedIds.size,
                    undoAvailable = godModeUndoCount > 0,
                    onCreateSuperset = {
                        onBatchCreateSuperset(selectedIds.toList())
                        selectedIds = emptySet()
                    },
                    onSkip = {
                        onBatchSkip(selectedIds.toList())
                        selectedIds = emptySet()
                    },
                    onDelete = {
                        onBatchDelete(selectedIds.toList())
                        selectedIds = emptySet()
                    },
                    onUltraFast = { pendingUltraFast = true },
                    onUndo = { onRevertGodModeAction(godModeUndoCount - 1) },
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    KpknTextAction(
                        text = "Añadir ejercicio",
                        onClick = {
                            onClose()
                            onAdd()
                        },
                        leadingIcon = KpknLineIcons.Plus,
                    )
                    KpknTextAction(
                        text = "Crear superserie",
                        onClick = {
                            onClose()
                            onCreateSuperset()
                        },
                        leadingIcon = KpknLineIcons.Layers,
                    )
                    if (onSkip != null) {
                        KpknTextAction(
                            text = "Saltar ejercicio",
                            onClick = {
                                onClose()
                                onSkip()
                            },
                            leadingIcon = KpknLineIcons.ChevronRight,
                        )
                    }
                }
            }
        }
    }

    pendingDissolveGroupId?.let { groupId ->
        KpknAlertDialog(
            onDismissRequest = { pendingDissolveGroupId = null },
            title = { Text("Disolver superserie", style = KpknType.bodyStrong, color = WizardColors.text) },
            text = { Text("Los ejercicios vuelven a ser independientes.", style = KpknType.body, color = WizardColors.textMuted) },
            confirmButton = {
                KpknTextAction(
                    text = "Disolver",
                    onClick = {
                        onDissolveSuperset(groupId)
                        pendingDissolveGroupId = null
                    },
                )
            },
            dismissButton = {
                KpknTextAction(
                    text = "Cancelar",
                    onClick = { pendingDissolveGroupId = null },
                    color = WizardColors.textMuted,
                )
            },
        )
    }
    if (pendingUltraFast) {
        KpknGlassDialog(onDismissRequest = { pendingUltraFast = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Modo ultrarrápido", style = KpknType.bodyStrong, color = WizardColors.text)
                KpknSheetWhiteButton(
                    text = "Drop-set",
                    onClick = {
                        onBatchUltraFast(selectedIds.toList(), SeriesTechnique.DROPSET)
                        selectedIds = emptySet()
                        pendingUltraFast = false
                    },
                )
                KpknSheetWhiteButton(
                    text = "Rest-pause",
                    onClick = {
                        onBatchUltraFast(selectedIds.toList(), SeriesTechnique.REST_PAUSE)
                        selectedIds = emptySet()
                        pendingUltraFast = false
                    },
                )
                KpknTextAction(
                    text = "Cancelar",
                    onClick = { pendingUltraFast = false },
                    color = WizardColors.textMuted,
                )
            }
        }
    }
}

/** Divisor «Cardio» sobre el primer bloque de cardio: una línea ámbar a cada lado, en `energia`. */
@Composable
private fun CardioDivider() {
    val line = SymbolPalette.energia.copy(alpha = 0.6f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(line))
        Text(
            text = "Cardio",
            style = KpknType.caption,
            color = SymbolPalette.energia,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(line))
    }
}

/** La barra vertical de 3 dp que une las filas de una superserie (como en el editor): de la primera a la última, con 1 dp de solape. */
private fun DrawScope.drawSupersetRail(color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, -1.dp.toPx()),
        size = Size(3.dp.toPx(), size.height + 2.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx()),
    )
}

/**
 * Gesto del asa (KPKN LS4): sin pulsación larga no pasa nada (un toque no elige ni abre). Con la pulsación larga, [onStart]; después,
 * el arrastre vertical llama a [onMove] con el desplazamiento acumulado y, al soltar, [onEnd]. Es el mismo mecanismo del roadmap de
 * antes, en vertical.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.routeGripGesture(
    enabled: Boolean,
    key: String,
    onStart: () -> Unit,
    onMove: (Float) -> Unit,
    onEnd: (Float) -> Unit,
): Modifier = pointerInput(enabled, key) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        val touchSlop = viewConfiguration.touchSlop
        val wonLongPress = withTimeoutOrNull(longPressTimeout) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > touchSlop) {
                    return@withTimeoutOrNull false
                }
            }
        }
        if (wonLongPress != null) return@awaitEachGesture
        onStart()
        var acc = 0f
        var ended = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                acc += change.position.y - change.previousPosition.y
                change.consume()
                onMove(acc)
            }
            ended = true
            onEnd(acc)
        } finally {
            if (!ended) onEnd(acc)
        }
    }
}
