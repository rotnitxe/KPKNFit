package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List as ListIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.ReviewedExerciseTechniqueImages
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.warmupLiveAddAdvisory
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.RecordFabHolder
import com.example.kpkn.screens.workout.WorkoutStepRules
import com.example.kpkn.screens.workout.inlineRestRemainingOrNull
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.ui.components.MobilityTechniqueImage
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------------------------------
// Tarjetas de aproximación (H5) y movilidad (H6) en vivo (LV-C).
//
// Misma tarjeta que la de la serie: la ocupa el marco `LiveSessionCardFrame` (312 × 425 dp), con el radio de 28 dp, el relleno
// #111111 y el filete de la tarjeta de serie. TODO lo que hace la fase vive DENTRO de la tarjeta: las listas largas se desplazan
// por dentro y los objetivos táctiles miden al menos 48 dp.
// ---------------------------------------------------------------------------------------------------------------------

/** Etiqueta de prueba de la tarjeta de preparación (aproximación o movilidad). */
internal const val PrepLiveCardTag = "prep-live-card"

private val PrepTouch = 48.dp
private val PrepEnergy = Color(0xFFF7CF73)
private val PrepTitleStyle = KpknType.title.copy(fontSize = 20.sp, lineHeight = 26.sp)
private val PrepMetaStyle = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 16.sp)
private val PrepTinyStyle = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 14.sp)

private const val MOB_VIEW_MOVEMENT = 0
private const val MOB_VIEW_LIST = 1
private const val MOB_VIEW_GLOBAL = 2

/**
 * Página de movilidad (H6): un movimiento cada vez, con su reloj propio, y dos páginas internas, la lista y el reloj global, que son
 * independientes. La movilidad avanza al marcar «Hecho»; llegar a cero no avanza sola.
 */
@Composable
internal fun MobilityPhaseLiveCard(
    items: List<WorkoutMobilityChecklistItem>,
    completedStepKeys: Set<String>,
    remainingSeconds: Int,
    totalMinutes: Int,
    isTimerRunning: Boolean,
    sessionAccentColor: Color,
    onToggleComplete: (item: WorkoutMobilityChecklistItem, completed: Boolean) -> Unit,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onAddTimerSeconds: (Int) -> Unit,
    onResetTimer: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    inlineRestRemainingSeconds: Int? = null,
    inlineRestTotalSeconds: Int? = null,
    onSkipInlineRest: (() -> Unit)? = null,
    recordActionHolder: RecordActionHolder? = null,
    recordActionScopeOwner: Any? = null,
    recordActionPageKey: String? = null,
    recordFabHolder: RecordFabHolder? = null,
    isActivePage: Boolean = false,
    /** Lo que sigue a la movilidad: con aproximación se dice «aproximación»; sin ella, «series». */
    nextIsWarmup: Boolean = true,
) {
    val showExerciseBadge = items.map { it.exerciseId }.distinct().size > 1
    var view by rememberSaveable { mutableStateOf(MOB_VIEW_MOVEMENT) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var skipRequested by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val doneCount = items.count { it.stepKey in completedStepKeys }
    val finished = items.isNotEmpty() && doneCount == items.size
    val skipped = skipRequested && finished
    val current = items.firstOrNull { it.stepKey == selectedKey }
        ?: items.firstOrNull { it.stepKey !in completedStepKeys }
        ?: items.lastOrNull()
    val currentIndex = current?.let { items.indexOf(it) } ?: 0
    val currentKey = current?.stepKey.orEmpty()
    val doseSeconds = current?.let { mobilityDoseSeconds(it.mobility) } ?: 0

    // Reloj propio del movimiento en pantalla: independiente del reloj global y solo de la tarjeta.
    var moveRemaining by rememberSaveable(currentKey) { mutableStateOf(doseSeconds) }
    var moveRunning by rememberSaveable(currentKey) { mutableStateOf(false) }
    LaunchedEffect(moveRunning, currentKey) {
        while (moveRunning && moveRemaining > 0) {
            delay(1_000)
            if (moveRunning) moveRemaining -= 1
        }
        if (moveRunning && moveRemaining <= 0) {
            moveRunning = false
            message = "Tiempo cumplido. Marca el movimiento como hecho."
        }
    }

    val markDone: (WorkoutMobilityChecklistItem) -> Unit = { item ->
        moveRunning = false
        selectedKey = null
        view = MOB_VIEW_MOVEMENT
        message = "Movimiento ${items.indexOf(item) + 1} hecho."
        onToggleComplete(item, true)
    }
    // «Hecho» y el ✓ de la línea hacen lo mismo: marcan el movimiento en pantalla (o el primero pendiente).
    val doneTarget = current?.takeIf { it.stepKey !in completedStepKeys }
        ?: items.firstOrNull { it.stepKey !in completedStepKeys }
    val onDone: () -> Unit = {
        val target = doneTarget
        if (target != null) markDone(target) else onContinue()
    }

    val recordActionInstanceOwner = remember { Any() }
    SideEffect {
        val actionPageKey = recordActionPageKey?.takeIf { it.isNotBlank() }
        val stepKey = doneTarget?.stepKey ?: items.firstOrNull()?.stepKey.orEmpty()
        if (
            isActivePage &&
            recordActionHolder != null &&
            recordActionScopeOwner != null &&
            actionPageKey != null &&
            stepKey.isNotBlank()
        ) {
            recordActionHolder.bind(
                scopeOwner = recordActionScopeOwner,
                instanceOwner = recordActionInstanceOwner,
                stepKey = stepKey,
                pageKey = actionPageKey,
                action = {
                    runPrepLiveCardFabAction(
                        hasIncomplete = doneTarget != null,
                        completeNext = { doneTarget?.let(markDone) },
                        advance = onContinue,
                    )
                },
            )
            recordFabHolder?.isUpdateMode = false
        } else {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
        }
    }
    DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
        onDispose { recordActionHolder?.clearIfOwner(recordActionInstanceOwner) }
    }

    val inlineRest = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
    val continueLabel = if (nextIsWarmup) "Continuar con aproximación" else "Continuar con series"
    PrepCardShell(modifier) {
        // Cabecera: título, reloj global (abre su página) y lista (abre su página).
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PrepTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Movilidad",
                style = PrepTitleStyle,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PrepButton(
                text = formatPrepClock(remainingSeconds),
                onClick = { view = if (view == MOB_VIEW_GLOBAL) MOB_VIEW_MOVEMENT else MOB_VIEW_GLOBAL },
                tone = PrepTone.TONAL,
                icon = KpknLineIcons.Clock,
                description = "Abrir reloj global de movilidad",
                tag = "mob-global-chip",
            )
            KpknIconButton(
                icon = Icons.AutoMirrored.Filled.ListIcon,
                contentDescription = if (view == MOB_VIEW_LIST) "Cerrar lista de movimientos" else "Ver lista de movimientos",
                onClick = { view = if (view == MOB_VIEW_LIST) MOB_VIEW_MOVEMENT else MOB_VIEW_LIST },
                modifier = Modifier.testTag("mob-list-toggle"),
            )
        }

        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        when {
            view == MOB_VIEW_LIST -> MobilityListPane(
                modifier = bodyModifier,
                items = items,
                completedStepKeys = completedStepKeys,
                currentKey = currentKey,
                doneCount = doneCount,
                onSelect = { item ->
                    moveRunning = false
                    selectedKey = item.stepKey
                    view = MOB_VIEW_MOVEMENT
                },
                onToggle = { item, completed ->
                    moveRunning = false
                    selectedKey = null
                    view = MOB_VIEW_MOVEMENT
                    message = if (completed) "Movimiento ${items.indexOf(item) + 1} hecho." else "Movimiento pendiente"
                    onToggleComplete(item, completed)
                },
            )
            view == MOB_VIEW_GLOBAL -> MobilityGlobalPane(
                modifier = bodyModifier,
                totalMinutes = totalMinutes,
                remainingSeconds = remainingSeconds,
                isRunning = isTimerRunning,
                onPlay = { if (isTimerRunning) onPauseTimer() else onStartTimer() },
                onAddSeconds = { onAddTimerSeconds(30) },
                onReset = onResetTimer,
                onBack = { view = MOB_VIEW_MOVEMENT },
            )
            finished || current == null -> PrepCenteredNote(
                modifier = bodyModifier,
                tag = "mob-end-pane",
                title = if (skipped) "Fase omitida" else "Movilidad completada",
                text = if (skipped) {
                    if (nextIsWarmup) "Puedes continuar con las aproximaciones." else "Puedes continuar con tus series."
                } else {
                    "$doneCount de ${items.size} movimientos hechos."
                },
            )
            else -> MobilityMovementPane(
                modifier = bodyModifier,
                item = current,
                number = currentIndex + 1,
                total = items.size,
                showExerciseBadge = showExerciseBadge,
                remaining = moveRemaining,
                running = moveRunning,
                onPlay = {
                    if (moveRunning) {
                        moveRunning = false
                        message = "Temporizador en pausa"
                    } else {
                        if (moveRemaining <= 0) moveRemaining = doseSeconds
                        moveRunning = true
                        message = "Temporizador en marcha"
                    }
                },
                onAddSeconds = { moveRemaining += 30 },
                onReset = {
                    moveRunning = false
                    moveRemaining = doseSeconds
                    message = "Temporizador reiniciado"
                },
            )
        }

        if (inlineRest != null) {
            PrepRestBlock(
                remainingSeconds = inlineRest.coerceAtLeast(0),
                totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                accent = sessionAccentColor,
                onSkip = onSkipInlineRest ?: {},
            )
        }
        if (finished) {
            PrepButton(
                text = continueLabel,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                tone = PrepTone.PRIMARY,
                tag = "prep-continue",
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrepButton(
                    text = "Saltar fase",
                    onClick = {
                        moveRunning = false
                        skipRequested = true
                        message = "Fase de movilidad omitida"
                        onSkip()
                    },
                    modifier = Modifier.weight(1f),
                    tone = PrepTone.TONAL,
                    tag = "prep-skip",
                )
                PrepButton(
                    text = "Hecho",
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    tone = PrepTone.PRIMARY,
                    icon = KpknLineIcons.Check,
                    description = "Marcar hecho y avanzar al siguiente movimiento",
                    tag = "mob-done",
                )
            }
        }
        PrepStatus(
            message ?: when {
                skipped -> "Fase de movilidad omitida"
                finished -> "Movilidad completada."
                else -> "Completa cada movimiento para avanzar."
            },
        )
    }
}

@Composable
private fun MobilityMovementPane(
    modifier: Modifier,
    item: WorkoutMobilityChecklistItem,
    number: Int,
    total: Int,
    showExerciseBadge: Boolean,
    remaining: Int,
    running: Boolean,
    onPlay: () -> Unit,
    onAddSeconds: () -> Unit,
    onReset: () -> Unit,
) {
    val mob = item.mobility
    val seconds = mobilityDoseSeconds(mob)
    val timed = seconds > 0
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 4.dp).testTag("mob-movement-pane"),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val movementId = ReviewedExerciseTechniqueImages.mobilityMovementId(mob.catalogConfigurationId, mob.exerciseDbId, mob.id)
            // Sin arte revisado para este movimiento no se reserva un hueco vacío.
            if (ReviewedExerciseTechniqueImages.mobilityImage(movementId) != null) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(KpknSurface.inset)) {
                    MobilityTechniqueImage(movementId = movementId, contentDescription = "", modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    mob.name,
                    style = KpknType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = WizardColors.text,
                )
                Text(
                    buildString {
                        append("Movimiento $number de $total")
                        if (showExerciseBadge && item.exerciseName.isNotBlank()) append(" · ${item.exerciseName}")
                    },
                    style = PrepTinyStyle.copy(lineHeight = 15.sp),
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (timed) formatPrepClock(remaining) else "${mob.reps?.takeIf { it.isNotBlank() } ?: "—"} reps",
                style = KpknType.metricXL.copy(fontSize = 34.sp, lineHeight = 40.sp),
                color = WizardColors.text,
                modifier = Modifier.weight(1f).testTag("mob-dose"),
            )
            if (timed) {
                PrepButton(
                    text = if (running) "Pausar" else "Iniciar",
                    onClick = onPlay,
                    tone = PrepTone.PRIMARY,
                    description = (if (running) "Pausar" else "Iniciar") + " temporizador del movimiento",
                    tag = "mob-play",
                )
            } else {
                Text("A tu ritmo", style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        if (timed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                PrepButton("+30 s", onAddSeconds, tone = PrepTone.QUIET, description = "Añadir treinta segundos al movimiento", tag = "mob-plus")
                PrepButton("Reiniciar", onReset, tone = PrepTone.QUIET, description = "Reiniciar el temporizador del movimiento", tag = "mob-reset")
            }
        }
        Text(
            mobilityDoseLabel(mob),
            style = KpknType.caption,
            color = PrepEnergy,
            modifier = Modifier.padding(top = 10.dp).testTag("mob-dose-summary"),
        )
        mob.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                notes,
                style = KpknType.caption.copy(lineHeight = 19.sp),
                color = WizardColors.textMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MobilityListPane(
    modifier: Modifier,
    items: List<WorkoutMobilityChecklistItem>,
    completedStepKeys: Set<String>,
    currentKey: String,
    doneCount: Int,
    onSelect: (WorkoutMobilityChecklistItem) -> Unit,
    onToggle: (WorkoutMobilityChecklistItem, Boolean) -> Unit,
) {
    Column(modifier.verticalScroll(rememberScrollState()).testTag("mob-list-pane")) {
        Text(
            "$doneCount/${items.size} movimientos hechos",
            style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        items.forEach { item ->
            val done = item.stepKey in completedStepKeys
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).prepDivider(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrepCheckBox(
                    checked = done,
                    description = (if (done) "Desmarcar " else "Marcar hecho ") + item.mobility.name,
                    onCheckedChange = { checked -> onToggle(item, checked) },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onSelect(item) }
                        .semantics { contentDescription = "Seleccionar ${item.mobility.name}" }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        item.mobility.name,
                        style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = if (done) WizardColors.textFaint else WizardColors.text,
                    )
                    Text(
                        mobilityDoseLabel(item.mobility) + if (item.stepKey == currentKey && !done) " · en pantalla" else "",
                        style = PrepMetaStyle,
                        color = PrepEnergy,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobilityGlobalPane(
    modifier: Modifier,
    totalMinutes: Int,
    remainingSeconds: Int,
    isRunning: Boolean,
    onPlay: () -> Unit,
    onAddSeconds: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier.verticalScroll(rememberScrollState()).testTag("mob-global-pane")) {
        Text(
            "Objetivo global · $totalMinutes min",
            style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(
            formatPrepClock(remainingSeconds),
            style = KpknType.metricXL.copy(fontSize = 48.sp, lineHeight = 54.sp),
            color = WizardColors.text,
            modifier = Modifier.testTag("mob-global-time"),
        )
        PrepButton(
            text = if (isRunning) "Pausar" else "Iniciar",
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            tone = PrepTone.PRIMARY,
            description = (if (isRunning) "Pausar" else "Iniciar") + " temporizador global",
            tag = "mob-global-play",
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrepButton(
                "+30 s", onAddSeconds, Modifier.weight(1f), PrepTone.TONAL,
                description = "Añadir treinta segundos al reloj global", tag = "mob-global-plus",
            )
            PrepButton(
                "Reiniciar", onReset, Modifier.weight(1f), PrepTone.TONAL,
                description = "Reiniciar reloj global", tag = "mob-global-reset",
            )
        }
        PrepButton(
            "Volver al movimiento", onBack, Modifier.fillMaxWidth(), PrepTone.QUIET,
            tag = "mob-global-back",
        )
    }
}

/**
 * Página de aproximación (H5): cada fila con su marca de hecha, los kg editables con coma decimal, las repeticiones y su descanso;
 * añadir aproximación arriba, y el descanso corto, saltar la fase y los mensajes dentro de la tarjeta.
 */
@Composable
internal fun WarmupPhaseLiveCard(
    rows: List<WarmupPhaseRow>,
    sessionAccentColor: Color,
    onToggleComplete: (row: WarmupPhaseRow, completed: Boolean, weightKg: Double) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    onAddSet: (() -> Unit)? = null,
    inlineRestRemainingSeconds: Int? = null,
    inlineRestTotalSeconds: Int? = null,
    onSkipInlineRest: (() -> Unit)? = null,
    recordActionHolder: RecordActionHolder? = null,
    recordActionScopeOwner: Any? = null,
    recordActionPageKey: String? = null,
    recordFabHolder: RecordFabHolder? = null,
    isActivePage: Boolean = false,
    onWeightDraft: ((row: WarmupPhaseRow, text: String) -> Unit)? = null,
    /** Carga de la primera serie efectiva, si se conoce; no se inventa. */
    workingWeightKg: Double? = null,
    /** Series efectivas de la fase, para el máximo recomendado de aproximaciones; 0 = no se conoce. */
    effectiveSetCount: Int = 0,
) {
    var skipRequested by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var knownCount by rememberSaveable { mutableStateOf(rows.size) }
    var pendingToggle by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val doneCount = rows.count { it.isCompleted }
    val allDone = rows.isNotEmpty() && doneCount == rows.size
    val skipped = skipRequested && allDone
    val firstPending = rows.firstOrNull { !it.isCompleted }

    // A tap requests a write; only the committed rows can confirm completion.
    LaunchedEffect(rows) {
        val pending = pendingToggle ?: return@LaunchedEffect
        val row = rows.firstOrNull { it.exerciseId == pending.first && it.warmup.id == pending.second }
            ?: return@LaunchedEffect
        if (row.isCompleted == pending.third) {
            val kg = row.actualWeightKg ?: 0.0
            message = if (row.isCompleted) {
                "Aproximación ${row.index + 1} hecha · " +
                    (if (kg > 0.0) "${kg.toTrimmedNumberString()} kg " else "") + "× ${row.warmup.targetReps}"
            } else "Aproximación pendiente"
            pendingToggle = null
        }
    }

    // Al añadir una aproximación, los avisos de WarmupRules (máximo recomendado y 85 %) salen como mensaje de la tarjeta.
    LaunchedEffect(rows.size) {
        if (rows.size > knownCount) {
            val advisory = if (effectiveSetCount > 0) {
                warmupLiveAddAdvisory(rows.map { it.warmup }, effectiveSetCount)
            } else {
                null
            }
            message = advisory ?: "Aproximación añadida"
        }
        knownCount = rows.size
    }

    val recordActionInstanceOwner = remember { Any() }
    SideEffect {
        val actionPageKey = recordActionPageKey?.takeIf { it.isNotBlank() }
        val stepKey = (firstPending ?: rows.firstOrNull())?.let {
            WorkoutStepRules.warmupStepKey(it.exerciseId, it.warmup.id)
        }.orEmpty()
        if (
            isActivePage &&
            recordActionHolder != null &&
            recordActionScopeOwner != null &&
            actionPageKey != null &&
            stepKey.isNotBlank()
        ) {
            recordActionHolder.bind(
                scopeOwner = recordActionScopeOwner,
                instanceOwner = recordActionInstanceOwner,
                stepKey = stepKey,
                pageKey = actionPageKey,
                action = {
                    val row = rows.firstOrNull { !it.isCompleted }
                    runPrepLiveCardFabAction(
                        hasIncomplete = row != null,
                        completeNext = {
                            row?.let {
                                val kg = it.actualWeightKg ?: it.suggestedWeightKg ?: 0.0
                                onToggleComplete(it, true, kg)
                            }
                        },
                        advance = onContinue,
                    )
                },
            )
            recordFabHolder?.isUpdateMode = false
        } else {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
        }
    }
    DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
        onDispose { recordActionHolder?.clearIfOwner(recordActionInstanceOwner) }
    }

    val inlineRest = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
    PrepCardShell(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PrepTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Aproximación",
                style = PrepTitleStyle,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$doneCount/${rows.size}",
                style = PrepMetaStyle.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.textMuted,
                modifier = Modifier.testTag("warmup-count"),
            )
            if (!skipped && onAddSet != null) {
                KpknIconButton(
                    icon = KpknLineIcons.Plus,
                    contentDescription = "Añadir aproximación",
                    onClick = onAddSet,
                    modifier = Modifier.testTag("warmup-add"),
                )
            }
        }
        if (skipped) {
            PrepCenteredNote(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                tag = "warmup-skipped-pane",
                title = "Fase omitida",
                text = "Las series efectivas siguen pendientes.",
            )
        } else {
            workingWeightKg?.takeIf { it > 0.0 }?.let { kg ->
                Text(
                    "Primera serie efectiva · ${kg.toTrimmedNumberString()} kg",
                    style = PrepMetaStyle,
                    color = WizardColors.textMuted,
                    modifier = Modifier.testTag("warmup-anchor"),
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("warmup-list"),
            ) {
                rows.forEach { row ->
                    key(row.exerciseId, row.warmup.id) {
                        WarmupChecklistLine(
                            title = "A${row.index + 1} · ${formatLiveWarmupPercent(row.warmup.percentageOfWorkingWeight)}",
                            subtitle = row.exerciseBadge.orEmpty(),
                            suggestedWeightKg = row.suggestedWeightKg,
                            actualWeightKg = row.actualWeightKg,
                            isCompleted = row.isCompleted,
                            accent = sessionAccentColor,
                            onToggle = { completed, kg ->
                                pendingToggle = Triple(row.exerciseId, row.warmup.id, completed)
                                message = null
                                onToggleComplete(row, completed, kg)
                            },
                            onWeightDraft = onWeightDraft?.let { callback -> { text -> callback(row, text) } },
                            repsLabel = "× ${row.warmup.targetReps}",
                            restLabel = formatPrepClock(row.warmup.restBetween ?: 60),
                            checkDescription = (if (row.isCompleted) "Desmarcar" else "Marcar hecha") + " aproximación ${row.index + 1}",
                            weightDescription = "Carga de aproximación ${row.index + 1} en kg",
                        )
                    }
                }
            }
        }
        if (inlineRest != null) {
            PrepRestBlock(
                remainingSeconds = inlineRest.coerceAtLeast(0),
                totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                accent = sessionAccentColor,
                onSkip = onSkipInlineRest ?: {},
            )
        }
        if (allDone || skipped) {
            if (inlineRest == null) {
                PrepButton(
                    text = "Continuar con series",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    tone = PrepTone.PRIMARY,
                    tag = "prep-continue",
                )
            }
        } else {
            PrepButton(
                text = "Saltar fase",
                onClick = {
                    skipRequested = true
                    message = "Fase de aproximación omitida"
                    onSkip()
                },
                modifier = Modifier.fillMaxWidth(),
                tone = PrepTone.TONAL,
                tag = "prep-skip",
            )
        }
        PrepStatus(
            message ?: when {
                skipped -> "Fase de aproximación omitida"
                allDone -> "Aproximación completada. Todo listo para empezar."
                else -> "Marca cada aproximación al terminar."
            },
        )
    }
}

internal data class WarmupPhaseRow(
    val exerciseId: String,
    val exerciseBadge: String?,
    val index: Int,
    val warmup: WarmupSetDefinition,
    val suggestedWeightKg: Double?,
    val actualWeightKg: Double?,
    val isCompleted: Boolean,
)

/**
 * Transparent sizing slot for every live pager page.
 *
 * The normal set card deliberately has no outer gray Surface: only its
 * Planificado/Reportar inner sections provide containers. Prep cards add one
 * undivided Surface inside this slot, while the slot keeps every page aligned.
 * The inner frame scales the previous base bounds uniformly so all card
 * content grows without spilling into the neighboring pager page. Normal
 * set cards can opt into measured height expansion so plan/technique panels
 * are laid out below the card instead of being hidden by the fixed slot.
 */
@Composable
internal fun LivePagerCardFrame(
    modifier: Modifier = Modifier,
    allowContentExpansion: Boolean = false,
    lockToWorkingSetHeight: Boolean = false,
    publishWorkingSetHeight: Boolean = false,
    godModeActive: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (godModeActive) {
        val scale = WorkoutUiTokens.GodModeCardScale
        val baseHeight = WorkoutUiTokens.LivePagerNormalExpandedBaseHeight
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val pageWidth = maxWidth
            Box(
                modifier = Modifier
                    .width(pageWidth * scale)
                    .height(baseHeight * scale),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredWidth(pageWidth)
                        .requiredHeight(baseHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin.Center
                        },
                    content = content,
                )
            }
        }
        return
    }

    val scale = WorkoutUiTokens.effectiveLivePagerCardScale()
    val policy = LocalLiveViewportPolicy.current
    val scaleX = LiveViewportPolicyMath.livePagerScaleX(
        scale * WorkoutUiTokens.LivePagerCardWidthSlimFactor,
        policy.cardPageFillX,
    )
    val scaleY = scale * WorkoutUiTokens.LivePagerCardHeightGrowFactor
    val slotHeight = WorkoutUiTokens.effectiveLivePagerStableHeight()
    val baseHeight = WorkoutUiTokens.LivePagerNormalExpandedBaseHeight
    val density = LocalDensity.current
    // Banda del relator (LR1g): la tarjeta empieza justo debajo y el margen sale de la misma fórmula que la banda.
    val relatorBand = WorkoutUiTokens.liveRelatorBandHeight(density)
    val topNudgeFraction = policy.topNudgeFraction
    val topNudge = slotHeight * topNudgeFraction + WorkoutUiTokens.LivePagerRelatorClearance
    if (!allowContentExpansion) {
        val widthFraction = if (scaleX >= 1f) (1f / scaleX).coerceIn(0.01f, 1f) else 1f
        // El borde inferior de la tarjeta se queda donde estaba: la tarjeta crece por arriba lo que ahorra la banda.
        val cardBottom = topNudge + baseHeight * scaleY
        val cardScaleY = ((cardBottom - relatorBand) / baseHeight).coerceAtLeast(0.01f)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(slotHeight + topNudge),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = relatorBand)
                    .fillMaxWidth(widthFraction)
                    .height(baseHeight)
                    .graphicsLayer {
                        this.scaleX = scaleX
                        this.scaleY = cardScaleY
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                content = content,
            )
        }
        return
    }

    val workingSetHeightHolder = LocalLivePagerWorkingSetVisualHeightPx.current
    val workingSetHeightPx = workingSetHeightHolder?.intValue ?: 0
    val lockHeightPx = if (lockToWorkingSetHeight) workingSetHeightPx else 0
    SubcomposeLayout(modifier = modifier.fillMaxWidth().wrapContentHeight()) { constraints ->
        val maxChildWidth = if (constraints.hasBoundedWidth) {
            if (scaleX >= 1f) {
                (constraints.maxWidth / scaleX).roundToInt().coerceAtLeast(0)
            } else {
                constraints.maxWidth
            }
        } else {
            Constraints.Infinity
        }
        val minChildWidth = if (scaleX >= 1f) {
            (constraints.minWidth / scaleX).roundToInt().coerceAtLeast(0)
        } else {
            0
        }
        val childMaxHeight = if (lockHeightPx > 0) {
            lockHeightPx
        } else {
            Constraints.Infinity
        }
        val childMinHeight = if (lockHeightPx > 0) lockHeightPx else 0
        val placeable = subcompose("expandable_live_pager_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (lockHeightPx > 0) {
                            Modifier
                                .height(with(density) { lockHeightPx.toDp() })
                                .clipToBounds()
                        } else {
                            Modifier.wrapContentHeight()
                        },
                    )
                    .then(
                        if (publishWorkingSetHeight) {
                            Modifier.onSizeChanged { size ->
                                publishLivePagerWorkingSetVisualHeight(
                                    workingSetHeightHolder,
                                    size.height,
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.TopCenter,
                content = content,
            )
        }.first().measure(
            Constraints(
                minWidth = minChildWidth.coerceAtMost(maxChildWidth),
                maxWidth = maxChildWidth,
                minHeight = childMinHeight,
                maxHeight = childMaxHeight,
            ),
        )
        val scaledWidth = (placeable.width * scaleX).roundToInt()
        val layoutWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            scaledWidth
        }
        val unscaledHeight = if (lockHeightPx > 0) lockHeightPx else placeable.height
        val scaledHeight = (unscaledHeight * scaleY).roundToInt()
        // Borde inferior de siempre (no se mueve): la tarjeta empieza bajo la banda del relator y crece por arriba
        // lo que ahorra (LR1g). Así el ✓ y el cajón quedan igual que antes, y el hueco es el mismo en serie y descanso.
        val topNudgePx = (scaledHeight * topNudgeFraction).roundToInt() +
            WorkoutUiTokens.LivePagerRelatorClearance.roundToPx()
        val footPx = scaledHeight + topNudgePx
        val bandPx = relatorBand.roundToPx()
        val cardPx = (footPx - bandPx).coerceAtLeast(1)
        val cardScaleY = if (unscaledHeight > 0) cardPx.toFloat() / unscaledHeight else scaleY
        val layoutHeight = footPx

        layout(layoutWidth, layoutHeight) {
            placeable.placeRelativeWithLayer(
                x = ((layoutWidth - scaledWidth) / 2f).roundToInt(),
                y = bandPx,
            ) {
                this.scaleX = scaleX
                this.scaleY = cardScaleY
                transformOrigin = TransformOrigin(0f, 0f)
                clip = lockToWorkingSetHeight
            }
        }
    }
}

/**
 * Tarjeta de las fases de preparación: la misma de la serie (radio 28 dp, relleno #111111 y filete). Ocupa todo el marco de 312 × 425 dp;
 * lo que no cabe se desplaza DENTRO de ella.
 */
@Composable
private fun PrepCardShell(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = WorkoutUiTokens.CardShape
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(PrepLiveCardTag)
            .clip(shape)
            .background(Color(0xFF111111), shape)
            .border(BorderStroke(1.dp, KpknSurface.moduleBorder), shape),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

private enum class PrepTone { PRIMARY, TONAL, QUIET }

/** Botón de la tarjeta de preparación: píldora de al menos 48 dp de alto. */
@Composable
private fun PrepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PrepTone = PrepTone.TONAL,
    icon: ImageVector? = null,
    description: String? = null,
    tag: String? = null,
) {
    val fill = when (tone) {
        PrepTone.PRIMARY -> WizardColors.cta
        PrepTone.TONAL -> KpknSurface.inset
        PrepTone.QUIET -> Color.Transparent
    }
    val ink = if (tone == PrepTone.PRIMARY) WizardColors.ctaContent else WizardColors.text
    Row(
        modifier = modifier
            .heightIn(min = PrepTouch)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clip(KpknShape.pill)
            .background(fill, KpknShape.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = ink)
        Text(text, style = KpknType.action, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mensajes de la fase: dentro de la tarjeta, de una o dos líneas. */
@Composable
private fun PrepStatus(text: String) {
    Text(
        text,
        style = PrepMetaStyle,
        color = WizardColors.textMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("prep-status"),
    )
}

@Composable
private fun PrepCenteredNote(modifier: Modifier, tag: String, title: String, text: String) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = KpknType.title.copy(fontSize = 22.sp, lineHeight = 28.sp), color = WizardColors.text, textAlign = TextAlign.Center)
        Text(text, style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 18.sp), color = WizardColors.textMuted, textAlign = TextAlign.Center)
    }
}

/** Filete inferior de una fila de lista. */
private fun Modifier.prepDivider(): Modifier = drawBehind {
    drawLine(KpknSurface.divider, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
}

/**
 * Marca de hecho de la aproximación y la movilidad: cuadro de 22 dp con anillo de 1,75 dp. Solo la marca responde al toque, en una
 * zona de 48 dp.
 */
@Composable
private fun PrepCheckBox(
    checked: Boolean,
    description: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val corner = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(PrepTouch)
            .semantics { contentDescription = description }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(corner)
                .background(if (checked) WizardColors.done else Color.Transparent, corner)
                .border(1.75.dp, if (checked) WizardColors.done else WizardColors.textFaint, corner),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = WizardColors.ctaContent,
                )
            }
        }
    }
}

/**
 * Fila de una aproximación (H5): marca de hecha, «A1 · 50 %», los kg editables (celda `inset`, cifra en Syne, coma decimal), las
 * repeticiones y el descanso.
 */
@Composable
internal fun WarmupChecklistLine(
    title: String,
    subtitle: String,
    suggestedWeightKg: Double?,
    actualWeightKg: Double?,
    isCompleted: Boolean,
    accent: Color,
    onToggle: (completed: Boolean, weightKg: Double) -> Unit,
    onWeightDraft: ((String) -> Unit)? = null,
    repsLabel: String? = null,
    restLabel: String? = null,
    checkDescription: String? = null,
    weightDescription: String? = null,
) {
    var textValue by remember(title, actualWeightKg, suggestedWeightKg) {
        val initial = actualWeightKg ?: suggestedWeightKg
        mutableStateOf(initial?.toTrimmedNumberString() ?: "")
    }
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .prepDivider(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PrepCheckBox(
            checked = isCompleted,
            description = checkDescription ?: ((if (isCompleted) "Desmarcar " else "Marcar hecha ") + title),
            onCheckedChange = { checked ->
                // El teclado decimal escribe la coma: se lee como punto antes de convertir (LR0).
                val kg = textValue.replace(',', '.').toDoubleOrNull()
                    ?: suggestedWeightKg
                    ?: 0.0
                onToggle(checked, kg)
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (subtitle.isBlank()) title else "$title · $subtitle",
                style = PrepTinyStyle,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Celda de peso `inset`: la cifra en Syne y la unidad junto a ella, con coma decimal.
                Row(
                    modifier = Modifier
                        .heightIn(min = PrepTouch)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KpknSurface.inset)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            onWeightDraft?.invoke(it)
                        },
                        textStyle = KpknType.metricL.copy(
                            fontSize = 23.sp,
                            lineHeight = 28.sp,
                            color = if (isCompleted) WizardColors.textMuted else WizardColors.text,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .width(60.dp * fontScale)
                            .then(if (weightDescription != null) Modifier.semantics { contentDescription = weightDescription } else Modifier),
                    )
                }
                Text("kg", style = PrepMetaStyle, color = WizardColors.textMuted)
                if (repsLabel != null) {
                    Text(
                        repsLabel,
                        style = KpknType.metricM.copy(fontSize = 19.sp, lineHeight = 24.sp),
                        color = WizardColors.text,
                        maxLines = 1,
                    )
                }
            }
        }
        if (restLabel != null) {
            Text(restLabel, style = PrepTinyStyle, color = WizardColors.textMuted, maxLines = 1)
        }
    }
}

/** Descanso corto de la aproximación o de la movilidad: reloj, «Saltar» y progreso, dentro de la tarjeta. */
@Composable
private fun PrepRestBlock(
    remainingSeconds: Int,
    totalSeconds: Int,
    accent: Color,
    onSkip: () -> Unit,
) {
    val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KpknSurface.inset)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)
            .testTag("prep-rest"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Descanso", style = PrepTinyStyle, color = WizardColors.textMuted)
                Text(
                    formatPrepClock(remainingSeconds),
                    style = KpknType.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = WizardColors.text,
                    modifier = Modifier.testTag("prep-rest-time"),
                )
            }
            PrepButton(
                text = "Saltar",
                onClick = onSkip,
                tone = PrepTone.PRIMARY,
                description = "Saltar descanso",
                tag = "prep-rest-skip",
            )
        }
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KpknSurface.divider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(accent.copy(alpha = 0.85f)),
            )
        }
    }
}

private fun formatLiveWarmupPercent(raw: Double): String {
    val pct = if (raw <= 1.0) raw * 100.0 else raw
    return "${pct.roundToInt()} %"
}
