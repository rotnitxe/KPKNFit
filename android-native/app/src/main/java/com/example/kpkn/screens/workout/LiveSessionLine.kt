package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.components.LiveUndoPillHost
import com.example.kpkn.screens.workout.components.RoadmapPendingAction
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknChannel
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.KpknPressStyle
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknPress
import com.example.kpkn.ui.design.motion.rememberKpknPose

/*
 * ROADMAP DE LA SESIÓN EN VIVO · `LiveSessionLine` rehecha (Pulido v1, escenas `lv-road-a-*` y `lv-road-b-*`).
 *
 *   A «Calma»  · 112 dp: margen 12 + banda 1 de 32 + hueco 8 + banda 2 de 48 + margen 12.
 *   B «Con ruta visible» · 132 dp: margen 12 + banda 1 de 30 + hueco 8 + TIRA de 14 + hueco 8 + banda 2 de 48 + margen 12.
 *
 * Qué variante se usa: B si la ventana mide 820 dp de alto o más (`Configuration.screenHeightDp`, la misma medida de la tarjeta alta;
 * el Flip5 mide 880 dp y sale B), A en las demás. Banda 1 = «dónde estoy» (toda ella abre la «Ruta»; pulsación larga = opciones del
 * ejercicio) y el avance de la sesión con su anillo; banda 2 = ‹ [Registrar serie] ›. La tira (solo B) lleva un segmento por ejercicio y
 * se recorre pulsando y deslizando. Todos los valores salen de `lv.css` (`.lv2*`); la profundidad, de `ui/design/depth`; el movimiento,
 * de `ui/design/motion` (ver [LiveRoadmapMotion]).
 */

/** Alto de la variante A (la base): lo que reservan las pantallas que no miden la ventana. Usa [liveSessionLineHeight] para el real. */
internal val LiveSessionLineHeight = LiveRoadmapVariant.A.height

/** Alto del roadmap en esta ventana: 132 dp (B) si la ventana mide 820 dp o más de alto, si no 112 dp (A). */
@Composable
internal fun liveSessionLineHeight(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return remember(screenHeightDp) { liveRoadmapVariantFor(screenHeightDp).height }
}

private object Road {
    val Panel = Color(0xFF1B1B1D)
    val Border = Color(0x10FFFFFF)
    val Ink = Color(0xFFF2EEE6)
    val InkDark = Color(0xFF111111)
    val Mut = Color(0xFFB9B5AE)
    val Fnt = Color(0xFF92908A)
    val Dim = Color(0xFF5F5C57)
    val Peach = Color(0xFFF49A6E)
    val Done = Color(0xFF43D18C)
    val DoneSoft = Color(0x1F43D18C)
    val Wipe = Color(0xFF87C9A5)
    val Blue = Color(0xFF8FB2FF)
    val Track = Color(0x14FFFFFF)
    val Pill = Color(0x14FFFFFF)
    val NavFill = Color(0x0DFFFFFF)
    val NavPressed = Color(0x1AFFFFFF)
    val RecPressed = Color(0xFFE4DFD6)
    val BandPressed = Color(0x12F2EEE6)
    val Gap = 8.dp
    val Margin = 12.dp
    val Band2 = 48.dp
    val StripH = 14.dp
}

private fun inter(size: Float, weight: FontWeight, tabular: Boolean = false) = TextStyle(
    fontFamily = WizardFonts.body, fontSize = size.sp, lineHeight = size.sp, fontWeight = weight,
    fontFeatureSettings = if (tabular) "tnum, lnum" else null,
)

/** Qué dice la banda 1 tras «Serie 2 de 3 ·». Orden de prioridad del lienzo: acciones pendientes, «registrada», «última serie», el nombre. */
private sealed interface RoadTail {
    data class Pending(val actions: List<RoadmapPendingAction>) : RoadTail
    data object Registered : RoadTail
    data class Last(val finalOfSession: Boolean) : RoadTail
    data class Name(val text: String) : RoadTail
}

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
    /** Sin uso: el roadmap nuevo no tiene el ‹+› del raíl (las series se añaden desde la tarjeta). Se conserva para no romper las llamadas de las pruebas. */
    @Suppress("UNUSED_PARAMETER") onAddSet: (() -> Unit)? = null,
    /** Sin uso: las opciones de una serie viven en los chips de la «Ruta» y en el ⚙ de la tarjeta, no en una pulsación larga del raíl. */
    @Suppress("UNUSED_PARAMETER") onLongPressPage: ((Int) -> Unit)? = null,
    onNavigateAdjacentExercise: ((Boolean) -> Unit)? = null,
    supersetGroups: List<SupersetGroup> = emptyList(),
    onSelectSuperset: (String) -> Unit = {},
    pendingActions: List<RoadmapPendingAction> = emptyList(),
    undoStack: List<GodModeUndoSnapshot> = emptyList(),
    completedSets: Map<String, CompletedSet> = emptyMap(),
    omittedSetKeys: Set<String> = emptySet(),
    onUndo: (Int) -> Unit = {},
    sessionCompletedCount: Int? = null,
    sessionTotalCount: Int? = null,
    restActive: Boolean = false,
    onEndRest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val variant = liveRoadmapVariantFor(LocalConfiguration.current.screenHeightDp)
    val big = LocalDensity.current.fontScale > 1.2f
    val haptics = LocalHapticFeedback.current
    val steps = snapshot?.let { state -> liveSessionLineSteps(state, state.activePageIndex).map { step ->
        state.calibratedPageLoads[step.pageIndex]?.let { load -> step.copy(calibratedLoad = load) } ?: step
    } }.orEmpty()
    val selected = steps.indexOfFirst { it.active }.coerceAtLeast(0)
    val activeStep = steps.getOrNull(selected)
    val adjacent by rememberUpdatedState(onNavigateAdjacentExercise)
    fun selectStep(step: LiveSessionLineStep) {
        if (step.preparationSelect != null) step.preparationSelect.invoke() else step.pageIndex?.let(onSelectPage)
    }
    val currentExercise = exercises.firstOrNull { it.id == currentExerciseId }
    val groups = remember(exercises, supersetGroups) { liveRouteGroups(exercises, supersetGroups) }
    val segments = remember(groups, currentExerciseId, completedSets, omittedSetKeys) {
        liveRoadmapSegments(groups, currentExerciseId, completedSets, omittedSetKeys)
    }
    val restStep = steps.firstOrNull { it.description == "Descanso" }
    val phase = when {
        restActive || activeStep?.description == "Descanso" -> LiveRoadmapPhase.Rest
        activeStep?.description == "Movilidad" || activeStep?.description == "Aproximación" -> LiveRoadmapPhase.Prep
        else -> LiveRoadmapPhase.Set
    }
    val done = sessionCompletedCount ?: snapshot?.completedCount ?: 0
    val total = sessionTotalCount ?: snapshot?.totalCount ?: 0
    val strength = exercises.filter { !it.isCardio }
    val atFirst = selected == 0 && currentExerciseId == strength.firstOrNull()?.id
    val atLast = selected == steps.lastIndex && currentExerciseId == strength.lastOrNull()?.id
    val setNumber = activeStep?.takeIf { phase == LiveRoadmapPhase.Set }?.let { liveRoadmapSetNumber(it.label) } ?: 1
    val setCount = currentExercise?.sets?.count { !it.isEmptySlot } ?: 0
    val onLastSet = phase == LiveRoadmapPhase.Set && setCount > 0 && setNumber >= setCount
    val registered = phase == LiveRoadmapPhase.Set && (activeStep?.completed == true || isUpdateMode)
    val restLabel = restStep?.label
    val tail: RoadTail = when {
        pendingActions.isNotEmpty() -> RoadTail.Pending(pendingActions)
        registered -> RoadTail.Registered
        phase == LiveRoadmapPhase.Set && onLastSet -> RoadTail.Last(finalOfSession = currentExerciseId == strength.lastOrNull()?.id)
        else -> RoadTail.Name(currentExercise?.let(::displayWorkoutExerciseName).orEmpty())
    }
    fun step(forward: Boolean) {
        val target = steps.getOrNull(selected + if (forward) 1 else -1)
        if (target != null) selectStep(target) else adjacent?.invoke(forward)
    }

    // Segmento provisional del recorrido de la tira (B): mientras dura el dedo, la banda 1 dice su nombre.
    var provisional by remember { mutableStateOf<Int?>(null) }
    var tickNonce by remember { mutableIntStateOf(0) }
    fun jumpTo(segment: LiveRoadmapSegment) {
        val group = segment.group
        if (group.superset) onSelectSuperset(group.id!!) else onSelectExercise(group.members.first().id)
    }

    // Registrar: barrido verde, ✓ que se dibuja, cifra que rueda y anillo que avanza; el botón conserva «Registrar serie» 550 ms.
    var recordNonce by remember { mutableIntStateOf(0) }
    var holdRecord by remember { mutableStateOf(false) }
    LaunchedEffect(recordNonce) {
        if (recordNonce > 0) {
            holdRecord = true
            kotlinx.coroutines.delay(LiveRoadmapMotion.RecordHoldMs.toLong())
            holdRecord = false
        }
    }
    val slide = rememberRoadmapSlide(LiveRoadmapPosition(currentExerciseId, setNumber, phase))

    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(variant.height).testTag("live-session-line")
            .kpknShadow(KpknShadow.Caida1028Capas, shape)
            .background(Road.Panel, shape).border(1.dp, Road.Border, shape)) {
            val stripTop = Road.Margin + variant.band1 + Road.Gap
            val band2Top = if (variant == LiveRoadmapVariant.B) stripTop + Road.StripH + Road.Gap else stripTop

            RoadmapBand1(
                variant = variant, big = big, phase = phase, tail = tail,
                provisionalName = provisional?.let { segments.getOrNull(it)?.name(::displayWorkoutExerciseName) },
                lead = liveRoadmapLead(phase, activeStep, currentExercise, restLabel),
                leadShort = liveRoadmapLeadShort(phase, activeStep, currentExercise, restLabel),
                drain = if (phase == LiveRoadmapPhase.Rest) (1f - (restStep?.preparationProgress ?: 0f)).coerceIn(0f, 1f) else 0f,
                done = done, total = total, slide = slide,
                onClick = onOpenRoute,
                onLongClick = currentExerciseId?.let { id -> { onOpenExerciseOptions(id) } },
                modifier = Modifier.padding(horizontal = Road.Margin).offset(y = Road.Margin - 6.dp),
            )

            RoadmapBand2(
                modifier = Modifier.padding(horizontal = Road.Margin).offset(y = band2Top),
                holdRecord = holdRecord, recordNonce = recordNonce,
                recordLabel = recordLabel, isUpdateMode = isUpdateMode,
                recordVisible = recordVisible, recordEnabled = recordEnabled,
                canEndRest = restActive && onEndRest != null,
                prevEnabled = !atFirst, nextEnabled = !atLast,
                nextDescription = if (onLastSet && !atLast) "Siguiente ejercicio" else "Paso siguiente",
                onPrev = { step(false) }, onNext = { step(true) },
                onRecord = { recordNonce++; onRecord() },
                onEndRest = { onEndRest?.invoke() },
            )

            // La tira va después de las bandas: su zona táctil de 44 dp (15 dp por encima y por debajo) tiene prioridad sobre ellas.
            if (variant == LiveRoadmapVariant.B) {
                RoadmapStrip(
                    segments = segments, provisional = provisional, tickNonce = tickNonce,
                    onMark = { index ->
                        if (index != provisional) {
                            provisional = index
                            tickNonce++
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onRelease = { index, inside ->
                        provisional = null
                        if (inside && index != null) segments.getOrNull(index)?.let(::jumpTo)
                    },
                    modifier = Modifier.padding(horizontal = Road.Margin).offset(y = stripTop + Road.StripH / 2 - 22.dp),
                )
            }
        }
        LiveUndoPillHost(undoStack, completedSets, onUndo, compact = true,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-52).dp))
    }
}

/** Con cada cambio de posición, hacia dónde y con qué contador entra el texto nuevo de la banda 1 (`null` mientras nada cambió). */
@Composable
private fun rememberRoadmapSlide(position: LiveRoadmapPosition): Pair<LiveRoadmapSlide, Int>? {
    val last = remember { arrayOfNulls<LiveRoadmapPosition>(1) }
    val counter = remember { intArrayOf(0) }
    return remember(position) {
        val slide = liveRoadmapSlide(last[0], position)
        last[0] = position
        slide?.let { it to ++counter[0] }
    }
}

/** Ha subido la cifra de series registradas: `lv2-roll` (la cifra rueda desde abajo) con el contador de la última subida. */
@Composable
private fun rememberRoadmapRoll(done: Int): Int? {
    val last = remember { intArrayOf(-1) }
    val counter = remember { intArrayOf(0) }
    return remember(done) {
        val rose = last[0] >= 0 && done > last[0]
        last[0] = done
        if (rose) ++counter[0] else null
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Banda 1 · «dónde estoy» + avance de la sesión
// ------------------------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoadmapBand1(
    variant: LiveRoadmapVariant,
    big: Boolean,
    phase: LiveRoadmapPhase,
    tail: RoadTail,
    provisionalName: String?,
    lead: String,
    leadShort: String,
    drain: Float,
    done: Int,
    total: Int,
    slide: Pair<LiveRoadmapSlide, Int>?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressFill by animateKpknColorAsState(if (pressed) Road.BandPressed else Road.BandPressed.copy(alpha = 0f),
        LiveRoadmapMotion.PressMs, KpknMotion.Estandar)
    val drainShown by animateKpknFloatAsState(drain, LiveRoadmapMotion.DrainMs, KpknMotion.Lineal, label = "roadmap-drain")
    val ringTarget = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val ring by animateKpknFloatAsState(ringTarget, LiveRoadmapMotion.RingMs, KpknMotion.Estandar, label = "roadmap-ring")
    val roll = rememberRoadmapRoll(done)
    val slideAnimation = when (slide?.first) {
        LiveRoadmapSlide.Left -> LiveRoadmapMotion.SlideLeft
        LiveRoadmapSlide.Right -> LiveRoadmapMotion.SlideRight
        LiveRoadmapSlide.Up -> LiveRoadmapMotion.SlideUp
        null -> null
    }
    // 44 dp de zona táctil (la banda más 6 dp por arriba y por abajo); la banda se dibuja en el centro.
    Box(
        modifier.fillMaxWidth().height(variant.band1 + 12.dp).testTag("live-session-route")
            .combinedClickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick,
                onLongClick = onLongClick, onLongClickLabel = "Opciones del ejercicio")
            .semantics(mergeDescendants = true) { contentDescription = "Abrir la ruta de la sesión. $done de $total series registradas" }
            .padding(vertical = 6.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(pressFill)
            .drawBehind {
                if (drainShown > 0f) drawRoundRect(Road.Peach.copy(alpha = 0.16f), size = Size(size.width * drainShown, size.height),
                    cornerRadius = CornerRadius(16.dp.toPx()))
            }) {
            Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Road.Gap)) {
                Row(
                    Modifier.weight(1f).then(if (slideAnimation != null) Modifier.kpknAnimate(slideAnimation, restartKey = slide) else Modifier),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BandLead(phase, if (big) leadShort else lead, shrink = tail is RoadTail.Pending)
                    if (!big) BasicText("·", style = inter(13f, FontWeight.Normal).copy(color = Road.Dim), maxLines = 1)
                    when (tail) {
                        is RoadTail.Pending -> tail.actions.forEach { action ->
                            Box(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(Road.Pill)
                                .clickable(role = Role.Button, onClick = action.onClick).padding(horizontal = 9.dp),
                                contentAlignment = Alignment.Center) {
                                BasicText(action.label, style = inter(12f, FontWeight.SemiBold).copy(color = Road.Ink), maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = .5.sp))
                            }
                        }
                        RoadTail.Registered -> if (!big) Box(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp))
                            .background(Road.DoneSoft).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                LiveRoadmapCheck(11.dp, 3f, Road.Done)
                                BasicText("registrada", style = inter(12f, FontWeight.SemiBold).copy(color = Road.Done), maxLines = 1)
                            }
                        }
                        is RoadTail.Last -> if (!big) Box(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(Road.Pill)
                            .padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                            BasicText(if (tail.finalOfSession) "serie final" else "última serie",
                                style = inter(12f, FontWeight.SemiBold).copy(color = Road.Ink), maxLines = 1)
                        }
                        is RoadTail.Name -> if (!big) BasicText(provisionalName ?: tail.text, Modifier.weight(1f, fill = false),
                            style = inter(13f, FontWeight.Normal).copy(color = Road.Mut), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Progress(done, total, ring, roll)
                Icon(KpknLineIcons.ChevronUp, null, Modifier.padding(start = 2.dp).size(14.dp), tint = Road.Fnt)
            }
        }
    }
}

@Composable
private fun RowScope.BandLead(phase: LiveRoadmapPhase, text: String, shrink: Boolean) {
    val modifier = if (shrink) Modifier.weight(1f, fill = false) else Modifier
    if (phase == LiveRoadmapPhase.Rest) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(KpknLineIcons.Timer, null, Modifier.size(15.dp), tint = Road.Peach)
            BasicText(text, style = inter(14f, FontWeight.SemiBold, tabular = true).copy(color = Road.Peach), maxLines = 1, softWrap = false)
        }
    } else {
        BasicText(text, modifier, style = inter(14f, FontWeight.SemiBold, tabular = true).copy(color = Road.Ink), maxLines = 1, softWrap = false,
            overflow = if (shrink) TextOverflow.Ellipsis else TextOverflow.Clip)
    }
}

/** Anillo de 22 dp con el avance de la sesión, «4» y «/18» (la cifra rueda al subir). */
@Composable
private fun Progress(done: Int, total: Int, ring: Float, roll: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.5.dp.toPx()
            val r = 9.dp.toPx()
            drawCircle(Color(0x1AFFFFFF), radius = r, style = Stroke(stroke))
            if (ring > 0f) drawArc(Road.Ink, -90f, 360f * ring, false, topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // `/18` pega a la cifra (margin-left −4 con el hueco de 6): 2 dp.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // `.lv2-num`: la cifra vive en una caja recortada de 1,1 em y rueda desde abajo.
            Box(Modifier.height(with(LocalDensity.current) { (13.sp * 1.1f).toDp() }).clipToBounds(), contentAlignment = Alignment.CenterStart) {
                val roller = if (roll != null) Modifier.kpknAnimate(LiveRoadmapMotion.Roll, restartKey = roll) else Modifier
                BasicText("$done", roller, style = inter(13f, FontWeight.SemiBold, tabular = true).copy(color = Road.Ink), maxLines = 1)
            }
            BasicText("/$total", style = inter(13f, FontWeight.Normal, tabular = true).copy(color = Road.Fnt), maxLines = 1)
        }
    }
}

/** ✓ de la banda 1 y del botón: el trazo `M5 12.5l4.5 4.5L19 7.5` de un lienzo de 24, con la barra de trazo de [strokeWidth] (unidades del lienzo de 24). */
@Composable
internal fun LiveRoadmapCheck(size: Dp, strokeWidth: Float, color: Color) {
    Canvas(Modifier.size(size)) { drawRoadmapCheck(this.size.minDimension, strokeWidth, color, 0f) }
}

/** Dibuja el ✓; [dashOffsetFraction] = `stroke-dashoffset` del lienzo sobre su trazo de 24 (1 = oculto, 0 = completo). */
private fun DrawScope.drawRoadmapCheck(px: Float, strokeWidth: Float, color: Color, dashOffsetFraction: Float) {
    val k = px / 24f
    val path = Path().apply {
        moveTo(5f * k, 12.5f * k); lineTo(9.5f * k, 17f * k); lineTo(19f * k, 7.5f * k)
    }
    val dash = 24f * k
    val effect = if (dashOffsetFraction <= 0f) null else PathEffect.dashPathEffect(floatArrayOf(dash, dash), dashOffsetFraction * dash)
    drawPath(path, color, style = Stroke(strokeWidth * k, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
}

// ------------------------------------------------------------------------------------------------------------------------------
// La tira (solo B) · un segmento por ejercicio
// ------------------------------------------------------------------------------------------------------------------------------

/**
 * Pulsar y deslizar: el segmento bajo el dedo es el provisional (alto 18 dp, aro crema y resplandor) y cada cambio de segmento da un pulso
 * de 80 ms y una vibración corta; al soltar dentro de la zona (la tira ± 16 dp de lado y 44 dp de alto) salta a ese ejercicio y fuera
 * de ella se queda donde estaba. Un toque sin deslizar es recorrer un solo segmento y soltar.
 */
@Composable
private fun RoadmapStrip(
    segments: List<LiveRoadmapSegment>,
    provisional: Int?,
    tickNonce: Int,
    onMark: (Int) -> Unit,
    onRelease: (index: Int?, inside: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val bounds = remember(segments.size) { Array(segments.size) { 0f to 0f } }
    val latestMark by rememberUpdatedState(onMark)
    val latestRelease by rememberUpdatedState(onRelease)
    fun at(xPx: Float): Int = liveRoadmapSegmentAt(xPx, bounds.toList())
    Box(
        modifier.fillMaxWidth().height(44.dp).testTag("live-session-strip")
            .semantics { contentDescription = "Ruta de la sesión" }
            .pointerInput(segments.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    latestMark(at(down.position.x).coerceAtLeast(0))
                    var last = down
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        last = change
                        if (!change.pressed) break
                        latestMark(at(change.position.x).coerceAtLeast(0))
                        change.consume()
                    }
                    val inside = liveRoadmapReleaseInside(last.position.x / density, last.position.y / density, 0f, size.width / density, 22f)
                    latestRelease(at(last.position.x).takeIf { it >= 0 }, inside)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth().height(Road.StripH), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val scrubbing = provisional != null
            segments.forEachIndexed { index, segment ->
                RoadmapSegment(
                    segment, isProvisional = provisional == index, scrubbing = scrubbing, tickNonce = tickNonce,
                    modifier = Modifier.weight(segment.weight.toFloat()).onGloballyPositioned { c ->
                        if (index < bounds.size) bounds[index] = c.positionInParent().x to (c.positionInParent().x + c.size.width)
                    },
                )
            }
        }
    }
}

@Composable
private fun RoadmapSegment(segment: LiveRoadmapSegment, isProvisional: Boolean, scrubbing: Boolean, tickNonce: Int, modifier: Modifier) {
    val duration = if (scrubbing) LiveRoadmapMotion.SegmentScrubMs else LiveRoadmapMotion.SegmentMs
    val heightDp by animateKpknFloatAsState(
        when { isProvisional -> 18f; segment.current -> 14f; else -> 8f }, duration, KpknMotion.Estandar, label = "roadmap-seg-height")
    val trackTarget = when {
        isProvisional -> Color(0x40FFFFFF)
        segment.current -> if (scrubbing) Color(0x1AFFFFFF) else Color(0x26FFFFFF)
        segment.cardio -> Road.Blue.copy(alpha = 0.22f)
        else -> Road.Track
    }
    val track by animateKpknColorAsState(trackTarget, duration, KpknMotion.Estandar)
    val glow by animateKpknFloatAsState(if (segment.current && !scrubbing) 1f else 0f, duration, KpknMotion.Estandar, label = "roadmap-seg-glow")
    val fill by animateKpknFloatAsState(segment.fill, LiveRoadmapMotion.FillMs, KpknMotion.Estandar, label = "roadmap-seg-fill")
    val radius = when { isProvisional -> 9.dp; segment.current -> 7.dp; else -> 4.dp }
    val shape = RoundedCornerShape(radius)
    val fillColor = when { segment.current || isProvisional -> Road.Ink; segment.cardio -> Road.Blue; else -> Road.Done }
    // lv-S26: resplandor `0 0 10px` de la crema al 22 %, que aparece y se va con la altura (280 ms).
    val glowSpec = KpknShadow.ResplandorCrema10.spec.copyLayer(0) { it.copy(color = it.color.copy(alpha = it.color.alpha * glow)) }
    Box(
        modifier.height(heightDp.dp)
            .then(if (isProvisional) Modifier.kpknAnimate(LiveRoadmapMotion.Tick, restartKey = tickNonce) else Modifier)
            .then(
                if (isProvisional) Modifier.kpknShadow(KpknShadow.ResplandorCrema12Capas, shape)
                else if (glow > 0f) Modifier.kpknShadow(glowSpec, shape) else Modifier,
            )
            .semantics {
                contentDescription = segment.description(::displayWorkoutExerciseName)
                role = Role.Button
                selected = segment.current
            }
            .testTag("live-session-seg-${segment.index}")
            .clip(shape).background(track)
            .drawBehind {
                if (fill > 0f) drawRoundRect(fillColor, size = Size(size.width * fill, size.height), cornerRadius = CornerRadius(radius.toPx()))
                val members = segment.group.members.size
                if (members > 1) for (k in 1 until members) {
                    drawRect(Road.Panel, Offset(size.width * k / members, 0f), Size(1.dp.toPx(), size.height))
                }
            },
    )
}

// ------------------------------------------------------------------------------------------------------------------------------
// Banda 2 · ‹ [Registrar serie] ›
// ------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun RoadmapBand2(
    modifier: Modifier,
    holdRecord: Boolean,
    recordNonce: Int,
    recordLabel: String?,
    isUpdateMode: Boolean,
    recordVisible: Boolean,
    recordEnabled: Boolean,
    canEndRest: Boolean,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    nextDescription: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRecord: () -> Unit,
    onEndRest: () -> Unit,
) {
    Row(modifier.fillMaxWidth().height(Road.Band2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Road.Gap)) {
        RoadmapNav(KpknLineIcons.ChevronLeft, "Paso anterior", prevEnabled, onPrev)
        val endsRest = canEndRest && !holdRecord
        val updating = isUpdateMode && !holdRecord
        val label = when {
            endsRest -> "Terminar descanso"
            recordLabel != null -> recordLabel
            updating -> "Actualizar serie"
            else -> "Registrar serie"
        }
        val visible = canEndRest || recordVisible
        val enabled = canEndRest || recordEnabled
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val fill by animateKpknColorAsState(if (pressed) Road.RecPressed else Road.Ink,
            if (pressed) KpknMotion.D90 else KpknMotion.D160, KpknMotion.Estandar)
        val buttonShape = RoundedCornerShape(24.dp)
        val wipe = if (recordNonce > 0) rememberKpknPose(LiveRoadmapMotion.Wipe, recordNonce) else null
        val check = if (recordNonce > 0) rememberKpknPose(LiveRoadmapMotion.Check, recordNonce) else null
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .then(if (visible) Modifier.kpknShadow(KpknShadow.Caida414, buttonShape) else Modifier)
                .kpknPress(source, KpknPressStyle.Primary)
                .clip(buttonShape)
                .background(if (visible) fill.copy(alpha = if (enabled) 1f else 0.38f) else Color.Transparent)
                .drawBehind {
                    val pose = wipe?.pose()
                    if (pose != null && pose.alpha > 0f) {
                        val w = pose[KpknChannel.WidthFraction] * size.width
                        if (w > 0f) drawRect(Road.Wipe.copy(alpha = pose.alpha), size = Size(w, size.height))
                    }
                }
                .clickable(interactionSource = source, indication = null, enabled = visible && enabled, role = Role.Button,
                    onClick = { if (endsRest) onEndRest() else onRecord() })
                .semantics(mergeDescendants = true) { contentDescription = label }
                .testTag("live-session-record"),
            contentAlignment = Alignment.Center,
        ) {
            if (visible) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    endsRest -> Canvas(Modifier.size(16.dp)) {
                        // `play` del lienzo: triángulo macizo `M8 5.5v13l10.5-6.5z` en un lienzo de 24.
                        val k = size.minDimension / 24f
                        drawPath(Path().apply { moveTo(8f * k, 5.5f * k); lineTo(8f * k, 18.5f * k); lineTo(18.5f * k, 12f * k); close() }, Road.InkDark)
                    }
                    updating -> Icon(Icons.Default.Update, null, Modifier.size(17.dp), tint = Road.InkDark)
                    else -> Canvas(Modifier.size(18.dp)) {
                        val pose = check?.pose()
                        val offset = if (pose != null && pose.active) pose[KpknChannel.DashOffset] else 0f
                        drawRoadmapCheck(size.minDimension, 2.4f, Road.InkDark, offset)
                    }
                }
                // La etiqueta cabe entera: 15 sp que se reducen hasta 10 sp antes de cortarse.
                BasicText(label, style = inter(15f, FontWeight.SemiBold).copy(color = Road.InkDark), maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = .5.sp))
            }
        }
        RoadmapNav(KpknLineIcons.ChevronRight, nextDescription, nextEnabled, onNext)
    }
}

/** ‹ ›: zona de 44 × 44 dp con un círculo de 40 dp (#FFFFFF0D, pulsado #FFFFFF1A en 90 ms); desactivado, alfa .38. */
@Composable
private fun RoadmapNav(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill by animateKpknColorAsState(if (pressed) Road.NavPressed else Road.NavFill, LiveRoadmapMotion.PressMs, KpknMotion.Estandar)
    Box(
        Modifier.size(44.dp).alpha(if (enabled) 1f else 0.38f).clip(CircleShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = Road.Mut)
        }
    }
}
