package com.example.kpkn.screens.workout

import com.example.kpkn.ui.components.kpknFloatingSurface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.HeaderSheet
import com.example.kpkn.screens.workout.components.HeaderSheetAction
import com.example.kpkn.screens.workout.components.RoadmapPendingAction
import com.example.kpkn.screens.workout.components.LiveUndoPillHost
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith

internal val LiveSessionLineHeight = 112.dp

/** A gap keeps the original indices: compression never renumbers a pager destination. */
internal data class LiveSessionLineRange(val indices: IntRange, val compressed: Boolean)

internal fun liveSessionLineRanges(count: Int, active: Int, preparation: Set<Int>): List<LiveSessionLineRange> {
    if (count <= 9) return (0 until count).map { LiveSessionLineRange(it..it, false) }
    val keep = preparation + setOf(0, count - 1) + ((active - 2)..(active + 2)).filter { it in 0 until count }
    return buildList {
        var index = 0
        while (index < count) {
            val start = index
            if (index in keep) add(LiveSessionLineRange(index..index++, false))
            else {
                while (index + 1 < count && index + 1 !in keep) index++
                add(LiveSessionLineRange(start..index++, true))
            }
        }
    }
}


/** V1: one island owns the route, preparation, series, folded exercises and the recording action. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveSessionLine(
    exercises: List<Exercise>,
    currentExerciseId: String?,
    snapshot: LiveSetStepperSnapshot?,
    onOpenRoute: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onSelectExercise: (String) -> Unit,
    onOpenExerciseOptions: (String) -> Unit,
    onRecord: () -> Unit,
    recordVisible: Boolean,
    recordEnabled: Boolean,
    isUpdateMode: Boolean = false,
    /** Lo que registra el botón en la página activa cuando no es una serie (movilidad, aproximaciones); null = «Registrar serie». */
    recordLabel: String? = null,
    onAddSet: (() -> Unit)? = null,
    onLongPressPage: ((Int) -> Unit)? = null,
    onNavigateAdjacentExercise: ((Boolean) -> Unit)? = null,
    supersetGroups: List<SupersetGroup> = emptyList(),
    onSelectSuperset: (String) -> Unit = {},
    pendingActions: List<RoadmapPendingAction> = emptyList(),
    undoStack: List<GodModeUndoSnapshot> = emptyList(),
    completedSets: Map<String, CompletedSet> = emptyMap(),
    onUndo: (Int) -> Unit = {},
    sessionCompletedCount: Int? = null,
    sessionTotalCount: Int? = null,
    restActive: Boolean = false,
    onEndRest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ink = WizardColors.text
    val clouds = snapshot?.let { state -> liveSessionLineSteps(state, state.activePageIndex).map { step ->
        state.calibratedPageLoads[step.pageIndex]?.let { load ->
            step.copy(calibratedLoad = load, description = "${step.description}, calibrada ${finishNumber(load)} kg")
        } ?: step
    } }.orEmpty()
    val selected = clouds.indexOfFirst { it.active }.coerceAtLeast(0)
    val adjacent by rememberUpdatedState(onNavigateAdjacentExercise)
    fun selectCloud(cloud: LiveSessionLineStep) {
        if (cloud.preparationSelect != null) cloud.preparationSelect.invoke()
        else cloud.pageIndex?.let(onSelectPage)
    }
    // Roadmap del Refinamiento 02 (captura 01): vidrio gris flotante con el resumen de la sesión, la pista de fases del ejercicio
    // (MOV · APR · SER · REST) y la fila ‹ registrar ›. Las series sueltas se eligen con ‹ › o desde la ruta (toque en el resumen).
    val activeCloud = clouds.getOrNull(selected)
    val currentExercise = exercises.firstOrNull { it.id == currentExerciseId }
    val glass = RoundedCornerShape(25.dp)
    val nextExercise = currentExercise?.let { current -> exercises.getOrNull(exercises.indexOfFirst { it.id == current.id } + 1) }
    val railNodes = liveRailNodes(clouds, restActive,
        nextExerciseLabel = nextExercise?.let { displayWorkoutExerciseName(it).substringBefore(' ').take(7) },
        onSelect = ::selectCloud, onNext = adjacent?.let { go -> { go(true) } })
    var scrubbed by remember { mutableStateOf<LiveRailNode?>(null) }
    val reduced = wizardReducedMotion()
    // Barrido verde del botón al registrar: confirma el gesto aunque la tarjeta cambie a la vez.
    val wipe = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun step(forward: Boolean) {
        val target = clouds.getOrNull(selected + if (forward) 1 else -1)
        if (target != null) selectCloud(target) else adjacent?.invoke(forward)
    }
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().height(LiveSessionLineHeight).testTag("live-session-line")
            // Píldora flotante: gris oscuro plano con sombra real de elevación (pedido del usuario, 29-oct), sin vidrio.
            .kpknFloatingSurface(glass, com.example.kpkn.ui.components.KpknFloating.PillElevation)
            .padding(start = 11.dp, end = 11.dp, top = 8.dp, bottom = 9.dp)) {
            Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(10.dp))
                    .combinedClickable(role = Role.Button, onClick = onOpenRoute,
                        onLongClick = currentExerciseId?.let { id -> { onOpenExerciseOptions(id) } })
                    .semantics { contentDescription = "Abrir ruta de la sesión" }.testTag("live-session-route")
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // El número de series registradas rueda hacia arriba al cambiar.
                    androidx.compose.animation.AnimatedContent(
                        targetState = liveSessionDockSummary(sessionCompletedCount ?: snapshot?.completedCount,
                            sessionTotalCount ?: snapshot?.totalCount, short = pendingActions.isNotEmpty()),
                        transitionSpec = {
                            if (reduced) {
                                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            } else {
                                (androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn()) togetherWith
                                    (androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut())
                            }
                        },
                        label = "dock-count",
                    ) { summary ->
                        Text(summary, style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.5.sp),
                            color = WizardColors.textMuted, maxLines = 1)
                    }
                    Icon(KpknLineIcons.ChevronDown, null, Modifier.size(11.dp), tint = WizardColors.textFaint)
                }
                Spacer(Modifier.weight(1f))
                // Las acciones pendientes se leen enteras: con la letra grande se ajusta su tamaño en vez de cortarlas.
                pendingActions.forEach { pending ->
                    BasicText(pending.label, Modifier.weight(1f, fill = false).clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = pending.onClick).padding(horizontal = 6.dp, vertical = 4.dp),
                        style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = WizardColors.text),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 10.sp, stepSize = .5.sp))
                }
                val scrubNode = scrubbed
                if (scrubNode != null) {
                    // Mientras se arrastra por el raíl, la cabecera dice dónde se va a soltar.
                    Text(scrubNode.description, Modifier.weight(1f, fill = false).padding(start = 8.dp),
                        style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF49A6E), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (pendingActions.isEmpty() && currentExercise != null && activeCloud != null) {
                    // El nombre cede espacio (va completo en la cabecera); la posición «3 / 4» se ve siempre.
                    val positionStyle = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp)
                    Text(displayWorkoutExerciseName(currentExercise),
                        modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
                        style = positionStyle, color = WizardColors.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(" " + liveSessionDockPosition(activeCloud, currentExercise), style = positionStyle,
                        color = WizardColors.textFaint, maxLines = 1, softWrap = false)
                }
                val doneCount = (sessionCompletedCount ?: snapshot?.completedCount ?: 0).toFloat()
                val totalCount = (sessionTotalCount ?: snapshot?.totalCount ?: 0).toFloat()
                SessionProgressRing(if (totalCount > 0f) (doneCount / totalCount).coerceIn(0f, 1f) else 0f, reduced,
                    Modifier.padding(start = 8.dp).size(18.dp))
            }
            LiveSessionRail(railNodes, onLongPress = onLongPressPage, onScrub = { scrubbed = it }, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DockNavButton(KpknLineIcons.ChevronLeft, "Paso anterior") { step(false) }
                val endsRest = restActive && onEndRest != null
                val label = when {
                    endsRest -> "Terminar descanso"
                    recordLabel != null -> recordLabel
                    isUpdateMode -> "Actualizar serie"
                    else -> "Registrar serie"
                }
                val primaryVisible = endsRest || recordVisible
                val primaryEnabled = endsRest || recordEnabled
                Row(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (primaryVisible) ink.copy(alpha = if (primaryEnabled) 1f else .38f) else Color.Transparent)
                        .drawBehind {
                            val sweep = wipe.value
                            if (sweep > 0f) {
                                val fade = 1f - ((sweep - .7f) / .3f).coerceIn(0f, 1f)
                                drawRect(Color(0xFF87C9A5).copy(alpha = .9f * fade),
                                    size = size.copy(width = size.width * (sweep / .7f).coerceIn(0f, 1f)))
                            }
                        }
                        .combinedClickable(enabled = primaryVisible && primaryEnabled, role = Role.Button,
                            onClick = {
                                if (endsRest) {
                                    onEndRest?.invoke()
                                } else {
                                    if (!reduced) scope.launch {
                                        wipe.snapTo(0f)
                                        wipe.animateTo(1f, androidx.compose.animation.core.tween(550))
                                        wipe.snapTo(0f)
                                    }
                                    onRecord()
                                }
                            })
                        .semantics(mergeDescendants = true) { contentDescription = label }
                        .testTag("live-session-record")
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (primaryVisible) {
                        Icon(when {
                            endsRest -> KpknLineIcons.Play
                            isUpdateMode -> Icons.Default.Update
                            else -> KpknLineIcons.Check
                        }, null, Modifier.size(16.dp), tint = WizardColors.ctaContent)
                        Spacer(Modifier.width(7.dp))
                        // La etiqueta contextual («Registrar aproximación A1») cabe entera: se reduce un poco antes que cortarse.
                        BasicText(label, style = KpknType.action.copy(fontSize = 13.sp, lineHeight = 18.sp, color = WizardColors.ctaContent),
                            maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp, stepSize = .5.sp))
                    }
                }
                DockNavButton(KpknLineIcons.ChevronRight, "Paso siguiente") { step(true) }
            }
        }
        LiveUndoPillHost(undoStack, completedSets, onUndo, compact = true,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-52).dp))
    }
}

/** Anillo del avance de la sesión (series registradas sobre el total), que crece animado al registrar. */
@Composable
private fun SessionProgressRing(progress: Float, reduced: Boolean, modifier: Modifier) {
    val animated by androidx.compose.animation.core.animateFloatAsState(progress,
        if (reduced) androidx.compose.animation.core.tween(0) else androidx.compose.animation.core.tween(700), label = "dock-ring")
    val ink = WizardColors.text
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = "Avance de la sesión ${(progress * 100).toInt()} %" }) {
        val stroke = 2.2.dp.toPx()
        drawCircle(ink.copy(alpha = .1f), radius = size.minDimension / 2 - stroke / 2,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawArc(ink, -90f, 360f * animated, false, topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Botón ‹ › del roadmap: círculo de 38 dp con relleno blanco al 5 %, en una zona táctil de 44 dp. */
@Composable
private fun DockNavButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(WizardColors.text.copy(alpha = .05f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = WizardColors.textMuted)
        }
    }
}

/** Una fase de la pista del roadmap (MOV, APR, SER, REST) del ejercicio en curso. */
internal data class LiveSessionDockPhase(
    val key: String,
    val label: String,
    val description: String,
    val active: Boolean,
    val done: Boolean,
    val select: (() -> Unit)? = null,
    val page: Int? = null,
)

/**
 * Fases del ejercicio en curso a partir de sus pasos: MOV y APR solo si existen; SER y REST siempre, como en el prototipo. REST está
 * activa mientras dura un descanso.
 */
internal fun liveSessionDockPhases(steps: List<LiveSessionLineStep>, restActive: Boolean): List<LiveSessionDockPhase> {
    val active = steps.firstOrNull { it.active }
    val mobility = steps.filter { it.description == "Movilidad" }
    val warmup = steps.filter { it.description == "Aproximación" }
    val rest = steps.filter { it.description == "Descanso" }
    val sets = steps.filter { it.preparationSelect == null && it.description != "Descanso" }
    val restOn = restActive || active?.description == "Descanso"
    return buildList {
        if (mobility.isNotEmpty()) add(LiveSessionDockPhase("mov", "MOV", "Movilidad", !restOn && mobility.any { it.active },
            mobility.all { it.completed }, select = mobility.first().preparationSelect))
        if (warmup.isNotEmpty()) add(LiveSessionDockPhase("apr", "APR", "Aproximaciones", !restOn && warmup.any { it.active },
            warmup.all { it.completed }, select = warmup.first().preparationSelect))
        val nextSet = sets.firstOrNull { !it.completed && !it.skipped } ?: sets.firstOrNull()
        add(LiveSessionDockPhase("ser", "SER", "Series", !restOn && (active == null || sets.any { it.active }),
            sets.isNotEmpty() && sets.all { it.completed || it.skipped }, page = nextSet?.pageIndex))
        add(LiveSessionDockPhase("rest", "REST", "Descanso", restOn, false,
            page = rest.firstOrNull { it.active }?.pageIndex ?: rest.firstOrNull()?.pageIndex))
    }
}

/** «SERIES · 2 DE 9»: series registradas de la sesión. [short] («2 DE 9») deja sitio a las acciones pendientes sin cortarlas. */
internal fun liveSessionDockSummary(completed: Int?, total: Int?, short: Boolean = false): String = when {
    completed == null || total == null -> if (short) "RUTA" else "RUTA DE LA SESIÓN"
    short -> "$completed DE $total"
    else -> "SERIES · $completed DE $total"
}

/** La posición tras el nombre del ejercicio («Sentadilla 3 / 4»): la serie de turno sobre sus series, o la fase de preparación. */
internal fun liveSessionDockPosition(step: LiveSessionLineStep, exercise: Exercise): String {
    val total = exercise.sets.count { !it.isEmptySlot }
    val label = step.label.removePrefix("S").trim()
    return when (step.description) {
        "Movilidad" -> "· movilidad"
        "Aproximación" -> "· aproximaciones"
        "Descanso" -> "· descanso"
        else -> "$label / $total"
    }
}
