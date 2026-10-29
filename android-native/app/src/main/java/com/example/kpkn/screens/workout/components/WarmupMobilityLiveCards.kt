package com.example.kpkn.screens.workout.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.shape.CircleShape
import com.example.kpkn.screens.onboarding.design.WizardFonts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.foundation.layout.widthIn
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
    /** En el carrusel «Terminar descanso» es el botón del roadmap: la tarjeta del descanso no lo repite (prototipo 02). */
    restEndInRoadmap: Boolean = false,
    /** Lo que sigue a la movilidad: con aproximación se dice «aproximación»; sin ella, «series». */
    nextIsWarmup: Boolean = true,
    /** El descanso de movilidad está en pausa (mismo reloj que el de las series). */
    inlineRestPaused: Boolean = false,
    /** El descanso se ve a pantalla completa; minimizado vive en la tarjeta. */
    inlineRestExpanded: Boolean = false,
    /** Expandir / minimizar el descanso; sin él, el descanso solo se ve en la tarjeta. */
    onToggleInlineRestExpanded: (() -> Unit)? = null,
    /** −15 s / +15 s del descanso; sin él no se pintan. */
    onAdjustInlineRest: ((Int) -> Unit)? = null,
    /** Pausar / continuar el descanso; sin él no se pinta. */
    onToggleInlineRestPause: (() -> Unit)? = null,
    /** Nombre del ejercicio, para el título del descanso a pantalla completa. */
    exerciseName: String? = null,
) {
    val showExerciseBadge = items.map { it.exerciseId }.distinct().size > 1
    // El prototipo abre la movilidad en «Checklist».
    var view by rememberSaveable { mutableStateOf(MOB_VIEW_LIST) }
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
            // El botón del roadmap dice qué registra: el movimiento siguiente o, con todo hecho, continuar.
            recordFabHolder?.bindLabel(recordActionInstanceOwner,
                if (doneTarget != null) "Registrar movimiento ${doneCount + 1}"
                else if (nextIsWarmup) "Ir a la aproximación" else "Ir a las series")
        } else {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
            recordFabHolder?.clearLabel(recordActionInstanceOwner)
        }
    }
    DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
        onDispose {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
            recordFabHolder?.clearLabel(recordActionInstanceOwner)
        }
    }

    val inlineRest = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
    val continueLabel = if (nextIsWarmup) "Ir a la aproximación" else "Ir a las series"
    // Lo que viene tras la movilidad, para el «SIGUIENTE» del descanso cuando ya no quedan movimientos.
    val continueTarget = if (nextIsWarmup) "Aproximaciones" else "Series efectivas"
    if (inlineRest != null && inlineRestExpanded && isActivePage && onToggleInlineRestExpanded != null) {
        // El descanso expandido (como el de las series): pantalla completa encima de todo, con minimizar a la tarjeta.
        KpknPortal {
            PrepRestFullscreen(
                eyebrow = "PAUSA DE MOVILIDAD",
                title = exerciseName?.takeIf { it.isNotBlank() } ?: "Movilidad",
                remainingSeconds = inlineRest.coerceAtLeast(0),
                totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                isPaused = inlineRestPaused,
                onAdjust = onAdjustInlineRest,
                onTogglePause = onToggleInlineRestPause,
                onMinimize = onToggleInlineRestExpanded,
                onEnd = onSkipInlineRest ?: {},
                lastText = items.lastOrNull { it.stepKey in completedStepKeys }?.mobility?.name,
                nextTitle = doneTarget?.mobility?.name ?: continueTarget,
                nextDose = doneTarget?.let { mobilityDoseLabel(it.mobility) },
            )
        }
        BackHandler { onToggleInlineRestExpanded() }
    }
    val compact = com.example.kpkn.screens.workout.liveSessionCardHeight() < 440.dp
    var guideKey by rememberSaveable { mutableStateOf<String?>(null) }
    var zoneSheet by rememberSaveable { mutableStateOf<String?>(null) }
    val mobilitySkipFooter: @Composable () -> Unit = {
        RestRef02Button("Omitir", {
            moveRunning = false
            skipRequested = true
            message = "Fase de movilidad omitida"
            onSkip()
        }, primary = false, modifier = Modifier.fillMaxWidth(), tag = "prep-skip")
    }
    // `mobilityCard` del prototipo 02: «MOVILIDAD 2 / 3», «Prepara el movimiento.», Checklist · Movimiento · Articulaciones y, en el pie,
    // «Omitir» / «Continuar». La guía de cada movimiento (con su reloj) vive en su hoja.
    val reducedMotion = wizardReducedMotion()
    // Tarjeta de movilidad ↔ descanso corto: se funden entre sí en lugar de cambiar de golpe.
    Crossfade(targetState = inlineRest != null, modifier = modifier, animationSpec = liveSpec(reducedMotion, 240), label = "mob-rest-swap") { showRest ->
        if (showRest) {
            PrepRef02InlineRest(
                modifier = Modifier,
                compact = compact,
                kicker = "DESCANSO · MOVILIDAD",
                remainingSeconds = (inlineRest ?: 0).coerceAtLeast(0),
                totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                nextTitle = doneTarget?.mobility?.name ?: continueTarget,
                nextDose = doneTarget?.let { mobilityDoseLabel(it.mobility) },
                onEnd = onSkipInlineRest ?: {},
                showEnd = !restEndInRoadmap,
                isPaused = inlineRestPaused,
                onExpand = onToggleInlineRestExpanded,
                onAdjust = onAdjustInlineRest,
                onTogglePause = onToggleInlineRestPause,
            )
        } else {
            RestRef02Card(
                compact = compact,
                tag = PrepLiveCardTag,
                // Registrar (marcar el movimiento o continuar) es el botón del roadmap; la tarjeta solo ofrece «Omitir» (pedido del usuario).
                footer = if (finished) null else mobilitySkipFooter,
            ) {
                PrepRef02Head("MOVILIDAD", "$doneCount / ${items.size}", compact, countTag = "mob-count")
                PrepRef02Title("Prepara el movimiento.", compact)
                PrepRef02Tabs(
                    options = listOf(MOB_VIEW_LIST to "Checklist", MOB_VIEW_MOVEMENT to "Movimiento", MOB_VIEW_GLOBAL to "Articulaciones"),
                    selected = view,
                    onSelect = { view = it },
                    compact = compact,
                    tagPrefix = "mob-view",
                )
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(top = if (compact) 6.dp else 8.dp)
                        .semantics { message?.let { liveRegion = LiveRegionMode.Polite; stateDescription = it } }
                        .testTag("mob-status"),
                ) {
                    // Checklist · Movimiento · Articulaciones · fin: cada panel se funde sobre el anterior (nada aparece de golpe).
                    val pane = when {
                        finished && view != MOB_VIEW_GLOBAL -> 0
                        view == MOB_VIEW_LIST -> 1
                        view == MOB_VIEW_MOVEMENT -> 2
                        else -> 3
                    }
                    AnimatedContent(
                        targetState = pane,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = { liveContentSwap(reducedMotion) },
                        label = "mob-pane",
                    ) { shown ->
                        when (shown) {
                            0 -> PrepCenteredNote(
                                modifier = Modifier.fillMaxSize(),
                                tag = "mob-end-pane",
                                title = if (skipped) "Fase omitida" else "Movilidad completada",
                                text = if (skipped) {
                                    if (nextIsWarmup) "Puedes continuar con las aproximaciones." else "Puedes continuar con tus series."
                                } else {
                                    "$doneCount de ${items.size} movimientos hechos."
                                },
                            )
                            1 -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("mob-list-pane")) {
                                items.forEach { item ->
                                    key(item.stepKey) {
                                        val done = item.stepKey in completedStepKeys
                                        PrepRef02MovementRow(
                                            name = item.mobility.name + if (showExerciseBadge && item.exerciseName.isNotBlank()) " · ${item.exerciseName}" else "",
                                            done = done,
                                            onToggle = { checked ->
                                                moveRunning = false
                                                selectedKey = null
                                                message = if (checked) "Movimiento ${items.indexOf(item) + 1} hecho." else "Movimiento pendiente"
                                                onToggleComplete(item, checked)
                                            },
                                            onInfo = {
                                                moveRunning = false
                                                selectedKey = item.stepKey
                                                guideKey = item.stepKey
                                            },
                                            tag = "mob-row-${item.stepKey}",
                                        )
                                    }
                                }
                            }
                            2 -> Column(Modifier.fillMaxSize()) { current?.let { item ->
                                val mob = item.mobility
                                val movementId = ReviewedExerciseTechniqueImages.mobilityMovementId(mob.catalogConfigurationId, mob.exerciseDbId, mob.id)
                                Box(
                                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color(0xFF0D1015))
                                        .testTag("mob-movement-pane"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (ReviewedExerciseTechniqueImages.mobilityImage(movementId) != null) {
                                        MobilityTechniqueImage(movementId = movementId, contentDescription = "Demostración de ${mob.name}",
                                            modifier = Modifier.fillMaxSize())
                                    } else {
                                        Text(mobilityDoseLabel(mob), style = PrepRef02Syne(20.sp), color = WizardColors.text, textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(16.dp))
                                    }
                                }
                                // `.v2-movement-caption`: el nombre y «Ver guía ›».
                                Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(mob.name, Modifier.weight(1f), style = PrepRef02Body(11.sp), color = WizardColors.text)
                                    Row(
                                        Modifier.heightIn(min = 38.dp).clickable(role = Role.Button) {
                                            moveRunning = false
                                            selectedKey = item.stepKey
                                            guideKey = item.stepKey
                                        }.testTag("mob-open-guide"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text("Ver guía", style = PrepRef02Body(11.sp), color = PrepRef02Muted)
                                        Icon(KpknLineIcons.ChevronRight, null, Modifier.size(12.dp), tint = PrepRef02Muted)
                                    }
                                }
                            } }
                            else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("mob-joints-pane")) {
                                val zones = items.flatMap { it.mobility.bodyZones }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                                Text("Articulaciones del movimiento.", Modifier.padding(vertical = 12.dp), style = PrepRef02Body(11.sp),
                                    color = PrepRef02Muted)
                                if (zones.isEmpty()) {
                                    Text("Este bloque no indica articulaciones.", style = PrepRef02Body(11.sp), color = PrepRef02Faint)
                                }
                                zones.forEach { zone ->
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 44.dp).prepRef02Line().clickable(role = Role.Button) { zoneSheet = zone }
                                            .testTag("mob-joint-$zone"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Las zonas llegan como claves internas en inglés («shoulder»): se enseñan con su nombre en español.
                                        Text(com.example.kpkn.data.models.mobilityBodyRegionLabel(zone), Modifier.weight(1f), style = PrepRef02Body(12.sp),
                                            color = WizardColors.text)
                                        Icon(KpknLineIcons.ChevronRight, null, Modifier.size(13.dp), tint = PrepRef02Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    items.firstOrNull { it.stepKey == guideKey }?.let { guide ->
        // `movement-detail`: la guía del movimiento con su dosis, su reloj (el del movimiento en pantalla) y «Marcar completado».
        val mob = guide.mobility
        val done = guide.stepKey in completedStepKeys
        val timed = mobilityDoseSeconds(mob) > 0
        val isCurrent = guide.stepKey == currentKey
        val movementId = ReviewedExerciseTechniqueImages.mobilityMovementId(mob.catalogConfigurationId, mob.exerciseDbId, mob.id)
        LiveSheet(
            title = mob.name,
            onDismiss = { guideKey = null },
            tag = "mob-guide-sheet",
            footer = { close ->
                LiveSheetPrimary(if (done) "Marcar pendiente" else "Marcar completado", {
                    if (done) onToggleComplete(guide, false) else markDone(guide)
                    close()
                }, tag = "mob-guide-done")
            },
        ) {
            if (ReviewedExerciseTechniqueImages.mobilityImage(movementId) != null) {
                Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D1015))) {
                    MobilityTechniqueImage(movementId = movementId, contentDescription = "Demostración de ${mob.name}", modifier = Modifier.fillMaxSize())
                }
            }
            LiveSheetSubtitle("Explora el movimiento a tu ritmo antes de añadir carga.")
            Text(mobilityDoseLabel(mob), style = PrepRef02Body(12.sp), color = WizardColors.text, modifier = Modifier.testTag("mob-dose-summary"))
            if (timed && isCurrent) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatPrepClock(moveRemaining), Modifier.weight(1f).testTag("mob-dose"), style = PrepRef02Syne(34.sp),
                        color = WizardColors.text)
                    LiveSheetSecondary(if (moveRunning) "Pausar" else "Iniciar", {
                        if (moveRunning) {
                            moveRunning = false
                            message = "Temporizador en pausa"
                        } else {
                            if (moveRemaining <= 0) moveRemaining = doseSeconds
                            moveRunning = true
                            message = "Temporizador en marcha"
                        }
                    }, Modifier.width(120.dp), tag = "mob-play")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    LiveSheetTextAction("+30 s", { moveRemaining += 30 }, Modifier.testTag("mob-plus"), wide = false)
                    LiveSheetTextAction("Reiniciar", {
                        moveRunning = false
                        moveRemaining = doseSeconds
                        message = "Temporizador reiniciado"
                    }, Modifier.testTag("mob-reset"), wide = false)
                }
            }
            mob.notes?.takeIf { it.isNotBlank() }?.let { LiveSheetShortCopy(it) }
            if (mob.bodyZones.isNotEmpty()) {
                LiveSheetSectionLabel("Articulaciones")
                Text(mob.bodyZones.joinToString(" · ") { com.example.kpkn.data.models.mobilityBodyRegionLabel(it.trim()) },
                    style = PrepRef02Body(12.sp), color = PrepRef02Muted)
            }
        }
    }
    zoneSheet?.let { zone ->
        LiveSheet(title = com.example.kpkn.data.models.mobilityBodyRegionLabel(zone), onDismiss = { zoneSheet = null }, tag = "mob-joint-sheet", footer = null) { close ->
            LiveSheetSubtitle("Movimientos de este bloque que trabajan esta zona.")
            items.filter { item -> item.mobility.bodyZones.any { it.trim() == zone } }.forEach { item ->
                LiveSheetMenuRow(item.mobility.name, {
                    moveRunning = false
                    selectedKey = item.stepKey
                    guideKey = item.stepKey
                    close()
                })
            }
        }
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
    /** En el carrusel «Terminar descanso» es el botón del roadmap: la tarjeta del descanso no lo repite (prototipo 02). */
    restEndInRoadmap: Boolean = false,
    onWeightDraft: ((row: WarmupPhaseRow, text: String) -> Unit)? = null,
    /** Carga de la primera serie efectiva, si se conoce; no se inventa. */
    workingWeightKg: Double? = null,
    /** Series efectivas de la fase, para el máximo recomendado de aproximaciones; 0 = no se conoce. */
    effectiveSetCount: Int = 0,
    /**
     * Registra una aproximación con los kg y las reps que muestra la tarjeta (la marca y el botón del roadmap). Sin él se usa
     * [onToggleComplete], que solo lleva los kg.
     */
    onCompleteWithReps: ((row: WarmupPhaseRow, weightKg: Double, reps: Int) -> Unit)? = null,
    /** Nombre del ejercicio, para el título del descanso a pantalla completa. */
    exerciseName: String? = null,
    /** El descanso de aproximación está en pausa (mismo reloj que el de las series). */
    inlineRestPaused: Boolean = false,
    /** El descanso se ve a pantalla completa; minimizado vive en la tarjeta. */
    inlineRestExpanded: Boolean = false,
    /** Expandir / minimizar el descanso; sin él, el descanso solo se ve en la tarjeta. */
    onToggleInlineRestExpanded: (() -> Unit)? = null,
    /** −15 s / +15 s del descanso; sin él no se pintan. */
    onAdjustInlineRest: ((Int) -> Unit)? = null,
    /** Pausar / continuar el descanso; sin él no se pinta. */
    onToggleInlineRestPause: (() -> Unit)? = null,
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
                    (if (kg > 0.0) "${kg.toTrimmedNumberString()} kg " else "") + "× ${row.actualReps ?: row.warmup.targetReps}"
            } else "Aproximación pendiente"
            pendingToggle = null
        }
    }

    // Los kg y las reps de cada aproximación: lo registrado o, mientras está pendiente, lo escrito en la tarjeta (o en «Ajustar»), y si
    // no, la sugerencia (el % de la carga de trabajo con el redondeo de la app) y las reps del plan.
    val weightTexts = remember { mutableStateMapOf<String, String>() }
    val repsTexts = remember { mutableStateMapOf<String, String>() }
    fun rowKey(row: WarmupPhaseRow) = "${row.exerciseId}|${row.warmup.id}"
    fun rowWeightText(row: WarmupPhaseRow): String {
        if (row.isCompleted && row.actualWeightKg != null) return row.actualWeightKg.toTrimmedNumberString()
        return weightTexts[rowKey(row)] ?: (row.actualWeightKg ?: row.suggestedWeightKg)?.toTrimmedNumberString().orEmpty()
    }
    fun rowWeightKg(row: WarmupPhaseRow): Double =
        rowWeightText(row).replace(',', '.').toDoubleOrNull() ?: row.suggestedWeightKg ?: 0.0
    fun rowRepsText(row: WarmupPhaseRow): String {
        if (row.isCompleted && row.actualReps != null) return row.actualReps.toString()
        return repsTexts[rowKey(row)] ?: (row.actualReps ?: row.warmup.targetReps).toString()
    }
    fun rowReps(row: WarmupPhaseRow): Int = rowRepsText(row).toIntOrNull()?.coerceAtLeast(0) ?: row.warmup.targetReps
    // La aproximación que se ve en el editor: la que se tocó o, si no, la primera pendiente.
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRow = rows.firstOrNull { rowKey(it) == selectedKey }
    val editorRow = selectedRow ?: firstPending
    // Lo que registra el botón del roadmap: la elegida si está pendiente; si no, la primera pendiente.
    val recordTarget = selectedRow?.takeIf { !it.isCompleted } ?: firstPending
    val recordRow: (WarmupPhaseRow) -> Unit = { row ->
        val kg = rowWeightKg(row)
        val reps = rowReps(row)
        pendingToggle = Triple(row.exerciseId, row.warmup.id, true)
        message = null
        if (selectedKey == rowKey(row)) selectedKey = null
        if (onCompleteWithReps != null) onCompleteWithReps(row, kg, reps) else onToggleComplete(row, true, kg)
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
        val stepKey = (recordTarget ?: rows.firstOrNull())?.let {
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
                    // Registra con los kg y las reps que hay en la tarjeta (no con la sugerencia sin más).
                    val row = recordTarget
                    runPrepLiveCardFabAction(
                        hasIncomplete = row != null,
                        completeNext = { row?.let(recordRow) },
                        advance = onContinue,
                    )
                },
            )
            recordFabHolder?.isUpdateMode = false
            recordFabHolder?.bindLabel(recordActionInstanceOwner,
                if (recordTarget != null) "Registrar aproximación A${recordTarget.index + 1}" else "Ir a las series")
        } else {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
            recordFabHolder?.clearLabel(recordActionInstanceOwner)
        }
    }
    DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
        onDispose {
            recordActionHolder?.clearIfOwner(recordActionInstanceOwner)
            recordFabHolder?.clearLabel(recordActionInstanceOwner)
        }
    }

    val inlineRest = inlineRestRemainingOrNull(inlineRestRemainingSeconds, inlineRestTotalSeconds)
    val compact = com.example.kpkn.screens.workout.liveSessionCardHeight() < 440.dp
    val restNextTitle = firstPending?.let { "Aproximación A${it.index + 1}" } ?: "Series efectivas"
    val restNextDose = firstPending?.let { "${rowWeightText(it).ifBlank { "—" }} kg × ${rowReps(it)}" }
    if (inlineRest != null && inlineRestExpanded && isActivePage && onToggleInlineRestExpanded != null) {
        // El descanso expandido (como el de las series): pantalla completa encima de todo, con minimizar a la tarjeta.
        KpknPortal {
            PrepRestFullscreen(
                eyebrow = "PAUSA ENTRE APROXIMACIONES",
                title = exerciseName?.takeIf { it.isNotBlank() } ?: "Aproximaciones",
                remainingSeconds = inlineRest.coerceAtLeast(0),
                totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                isPaused = inlineRestPaused,
                onAdjust = onAdjustInlineRest,
                onTogglePause = onToggleInlineRestPause,
                onMinimize = onToggleInlineRestExpanded,
                onEnd = onSkipInlineRest ?: {},
                lastText = rows.lastOrNull { it.isCompleted }?.let { "${rowWeightText(it).ifBlank { "—" }} kg × ${rowReps(it)}" },
                nextTitle = restNextTitle,
                nextDose = restNextDose,
            )
        }
        BackHandler { onToggleInlineRestExpanded() }
    }
    var page by rememberSaveable { mutableStateOf(0) }
    var loadSheet by rememberSaveable { mutableStateOf(false) }
    val pages = ((rows.size + 2) / 3).coerceAtLeast(1)
    val safePage = page.coerceIn(0, pages - 1)
    val warmupSkipFooter: @Composable () -> Unit = {
        RestRef02Button("Omitir", {
            skipRequested = true
            message = "Fase de aproximación omitida"
            onSkip()
        }, primary = false, modifier = Modifier.fillMaxWidth(), tag = "prep-skip")
    }
    // `warmupCard` del prototipo 02: «APROXIMACIONES 1 / 2», «Acércate a tu carga.», la referencia de trabajo con «Ajustar», tres filas por
    // página («A1 · 20 % · 20 × 10» con su marca), «+ Aproximación» y, en el pie, «Omitir» / «Ir a efectivas».
    val reducedMotion = wizardReducedMotion()
    // Tarjeta de aproximación ↔ descanso corto: se funden entre sí en lugar de cambiar de golpe.
    Crossfade(targetState = inlineRest != null, modifier = modifier, animationSpec = liveSpec(reducedMotion, 240), label = "warmup-rest-swap") { showRest ->
        if (showRest) {
            PrepRef02InlineRest(
                modifier = Modifier,
                compact = compact,
                kicker = "DESCANSO · APROX.",
                remainingSeconds = (inlineRest ?: 0).coerceAtLeast(0),
                totalSeconds = (inlineRestTotalSeconds ?: 1).coerceAtLeast(1),
                nextTitle = restNextTitle,
                nextDose = restNextDose,
                onEnd = onSkipInlineRest ?: {},
                showEnd = !restEndInRoadmap,
                isPaused = inlineRestPaused,
                onExpand = onToggleInlineRestExpanded,
                onAdjust = onAdjustInlineRest,
                onTogglePause = onToggleInlineRestPause,
            )
        } else {
            RestRef02Card(
                compact = compact,
                tag = PrepLiveCardTag,
                // Registrar la aproximación siguiente (o ir a las efectivas) es el botón del roadmap; la tarjeta solo ofrece «Omitir».
                footer = if (allDone || skipped) null else warmupSkipFooter,
            ) {
                PrepRef02Head("APROXIMACIONES", "$doneCount / ${rows.size}", compact, countTag = "warmup-count")
                PrepRef02Title("Acércate a tu carga.", compact)
                // Fase omitida ↔ lista de aproximaciones: se funden, no saltan.
                AnimatedContent(
                    targetState = skipped,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { liveContentSwap(reducedMotion) },
                    label = "warmup-skipped-swap",
                ) { isSkipped ->
                if (isSkipped) {
                    PrepCenteredNote(
                        modifier = Modifier.fillMaxSize()
                            .semantics { message?.let { liveRegion = LiveRegionMode.Polite; stateDescription = it } },
                        tag = "warmup-skipped-pane",
                        title = "Fase omitida",
                        text = "Las series efectivas siguen pendientes.",
                    )
                } else {
                Column(Modifier.fillMaxSize()) {
                    // `.v2-reference`: «Trabajo · 100 kg» y «Ajustar» (los kg de cada aproximación).
                    Row(Modifier.fillMaxWidth().heightIn(min = 25.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Trabajo · ${workingWeightKg?.takeIf { it > 0.0 }?.let { "${it.toTrimmedNumberString()} kg" } ?: "—"}",
                            Modifier.weight(1f).testTag("warmup-anchor"),
                            style = PrepRef02Body(10.sp), color = PrepRef02Faint,
                        )
                        if (onWeightDraft != null) {
                            Box(Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) { loadSheet = true }.testTag("warmup-adjust"),
                                contentAlignment = Alignment.Center) {
                                Text("Ajustar", style = PrepRef02Body(10.sp), color = PrepRef02Muted)
                            }
                        }
                    }
                    Column(
                        // Con editor no llena: el editor queda pegado a las filas y el aire sobrante va al final.
                        Modifier.weight(1f, fill = editorRow == null).fillMaxWidth().testTag("warmup-list")
                            .semantics { message?.let { liveRegion = LiveRegionMode.Polite; stateDescription = it } },
                    ) {
                        AnimatedContent(
                            targetState = safePage,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = { liveContentSwap(reducedMotion) },
                            label = "warmup-page",
                        ) { shownPage ->
                        Column(Modifier.fillMaxWidth()) {
                        rows.drop(shownPage * 3).take(3).forEach { row ->
                            key(row.exerciseId, row.warmup.id) {
                                PrepRef02WarmRow(
                                    label = "A${row.index + 1}",
                                    percent = formatLiveWarmupPercent(row.warmup.percentageOfWorkingWeight),
                                    // La unidad siempre a la vista: «50 kg × 8».
                                    dose = "${rowWeightText(row).ifBlank { "—" }} kg × ${rowRepsText(row)}",
                                    badge = row.exerciseBadge,
                                    done = row.isCompleted,
                                    selected = editorRow?.let { rowKey(it) } == rowKey(row),
                                    compact = compact,
                                    onSelect = { selectedKey = rowKey(row) },
                                    onToggle = {
                                        if (!row.isCompleted) {
                                            recordRow(row)
                                        } else {
                                            pendingToggle = Triple(row.exerciseId, row.warmup.id, false)
                                            message = null
                                            onToggleComplete(row, false, rowWeightKg(row))
                                        }
                                    },
                                )
                            }
                        }
                        }
                        }
                    }
                    // Los kg y las reps de la aproximación elegida (o de la primera pendiente), editables antes de registrar con el
                    // mismo campo que la carga de la serie efectiva: cifra en Syne, «kg» y − / + con el paso del ejercicio. Una ya
                    // registrada enseña lo que se registró.
                    editorRow?.let { row ->
                        key(rowKey(row)) {
                            PrepRef02WarmEditor(
                                label = "A${row.index + 1}",
                                kgText = rowWeightText(row),
                                repsText = rowRepsText(row),
                                readOnly = row.isCompleted,
                                compact = compact,
                                onKgChange = { text ->
                                    weightTexts[rowKey(row)] = text
                                    onWeightDraft?.invoke(row, text)
                                },
                                onStepKg = { direction ->
                                    val current = rowWeightText(row).replace(',', '.').toDoubleOrNull() ?: row.suggestedWeightKg ?: 0.0
                                    val text = prepKgText((current + row.loadIncrementKg * direction).coerceAtLeast(0.0))
                                    weightTexts[rowKey(row)] = text
                                    onWeightDraft?.invoke(row, text)
                                },
                                onRepsChange = { text -> repsTexts[rowKey(row)] = text.filter(Char::isDigit).take(3) },
                            )
                        }
                    }
                    // `.v2-warm-footer`: «+ Aproximación» y, con más de tres, la paginación.
                    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onAddSet != null) {
                            Row(
                                Modifier.heightIn(min = 36.dp).clickable(role = Role.Button, onClick = onAddSet)
                                    .semantics { contentDescription = "Añadir aproximación" }.testTag("warmup-add"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(KpknLineIcons.Plus, null, Modifier.size(14.dp), tint = PrepRef02Muted)
                                Text("Aproximación", style = PrepRef02Body(11.sp), color = PrepRef02Muted)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (pages > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(36.dp).clickable(enabled = safePage > 0, role = Role.Button) { page = safePage - 1 }
                                    .semantics { contentDescription = "Página anterior" }, contentAlignment = Alignment.Center) {
                                    Icon(KpknLineIcons.ChevronLeft, null, Modifier.size(13.dp), tint = PrepRef02Muted.copy(alpha = if (safePage > 0) 1f else .35f))
                                }
                                Text("${safePage + 1}/$pages", style = PrepRef02Body(10.sp), color = PrepRef02Muted)
                                Box(Modifier.size(36.dp).clickable(enabled = safePage < pages - 1, role = Role.Button) { page = safePage + 1 }
                                    .semantics { contentDescription = "Página siguiente" }, contentAlignment = Alignment.Center) {
                                    Icon(KpknLineIcons.ChevronRight, null, Modifier.size(13.dp),
                                        tint = PrepRef02Muted.copy(alpha = if (safePage < pages - 1) 1f else .35f))
                                }
                            }
                        }
                    }
                }
                }
                }
            }
        }
    }
    if (loadSheet && onWeightDraft != null) {
        // `warmup-load`: los kg de cada aproximación, con coma decimal.
        LiveSheet(title = "Carga de las aproximaciones", onDismiss = { loadSheet = false }, tag = "warmup-load-sheet") {
            LiveSheetSubtitle(
                workingWeightKg?.takeIf { it > 0.0 }?.let { "Trabajo · ${it.toTrimmedNumberString()} kg. Ajusta la carga de cada aproximación." }
                    ?: "Ajusta la carga de cada aproximación.",
            )
            rows.forEach { row ->
                key(row.exerciseId, row.warmup.id) {
                    LiveSheetLabeledField(
                        label = "A${row.index + 1} · ${formatLiveWarmupPercent(row.warmup.percentageOfWorkingWeight)} · × ${row.warmup.targetReps} · kg",
                        value = rowWeightText(row),
                        onValueChange = { text ->
                            weightTexts[rowKey(row)] = text
                            onWeightDraft(row, text)
                        },
                        modifier = Modifier.padding(top = 10.dp),
                        keyboardType = KeyboardType.Decimal,
                        tag = "warmup-load-${row.index}",
                    )
                }
            }
        }
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
    /** Reps registradas de una aproximación hecha; nulas mientras está pendiente. */
    val actualReps: Int? = null,
    /** Paso de − / + de la carga: el mismo que la tarjeta de la serie efectiva para este ejercicio. */
    val loadIncrementKg: Double = 2.5,
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

// ---------------------------------------------------------------------------------------------------------------------
// Piezas del Refinamiento 02 para las tarjetas de preparación (`mobilityCard` y `warmupCard` del prototipo).
// ---------------------------------------------------------------------------------------------------------------------

private val PrepRef02Muted = Color(0xFFB9B5AE)
private val PrepRef02Faint = Color(0xFF92908A)

private fun PrepRef02Body(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = WizardFonts.body, fontSize = size, fontWeight = weight)

private fun PrepRef02Syne(size: androidx.compose.ui.unit.TextUnit) =
    TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = size, fontFeatureSettings = "'tnum' 1")

/** Filete inferior de las filas del prototipo (#ffffff09). */
private fun Modifier.prepRef02Line(): Modifier = drawBehind {
    drawLine(Color(0x09FFFFFF), androidx.compose.ui.geometry.Offset(0f, size.height),
        androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
}

/** `.v2-kicker` de la preparación: «MOVILIDAD» / «APROXIMACIONES» y el contador a la derecha. */
@Composable
private fun PrepRef02Head(title: String, count: String, compact: Boolean, countTag: String) {
    Row(Modifier.fillMaxWidth().height(if (compact) 27.dp else 29.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = PrepRef02Body(9.sp).copy(letterSpacing = 0.65.sp), color = PrepRef02Muted)
        Text(count, Modifier.testTag(countTag), style = PrepRef02Body(9.sp).copy(fontFeatureSettings = "'tnum' 1"), color = PrepRef02Muted)
    }
}

/** `.v2-card-title`: Syne 21 (20 en compacto) con su margen. */
@Composable
private fun PrepRef02Title(text: String, compact: Boolean) {
    Text(
        text,
        Modifier.padding(top = if (compact) 7.dp else 9.dp, bottom = if (compact) 11.dp else 15.dp),
        style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = if (compact) 20.sp else 21.sp,
            letterSpacing = (-0.6).sp),
        color = WizardColors.text,
    )
}

/** `.v2-tech-tabs`: pestañas de 36 (32 en compacto), la elegida en tinta. */
@Composable
private fun PrepRef02Tabs(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit, compact: Boolean, tagPrefix: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 32.dp else 36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = if (compact) 32.dp else 36.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) WizardColors.text else Color(0x05FFFFFF))
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 5.dp, vertical = 6.dp)
                    .testTag("$tagPrefix-$value"),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = PrepRef02Body(if (compact) 10.sp else 11.sp), color = if (on) Color(0xFF111111) else WizardColors.text,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.v2-movement-row`: el círculo de hecho con el nombre (marca el movimiento) y la información a la derecha. */
@Composable
private fun PrepRef02MovementRow(name: String, done: Boolean, onToggle: (Boolean) -> Unit, onInfo: () -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).prepRef02Line(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp)
                .toggleable(value = done, role = Role.Checkbox, onValueChange = onToggle)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .then(if (done) Modifier.background(WizardColors.text) else Modifier.border(1.dp, Color(0x40FFFFFF), CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (done) Icon(KpknLineIcons.Check, null, Modifier.size(12.dp), tint = Color(0xFF111111)) }
            Text(name, style = PrepRef02Body(12.sp), color = if (done) PrepRef02Muted else WizardColors.text)
        }
        Box(
            Modifier.size(width = 36.dp, height = 40.dp).clickable(role = Role.Button, onClick = onInfo)
                .semantics { contentDescription = "Detalle de $name" },
            contentAlignment = Alignment.Center,
        ) { Icon(KpknLineIcons.Info, null, Modifier.size(15.dp), tint = PrepRef02Muted) }
    }
}

/**
 * `.v2-warm-row`: «A1», «20 %», «20 kg × 10» en Syne a la derecha y la marca (✓ hecha, + pendiente). Tocar la fila (fuera de la marca)
 * la elige para ver o corregir sus kg y reps en el editor; la elegida se resalta.
 */
@Composable
private fun PrepRef02WarmRow(
    label: String,
    percent: String,
    dose: String,
    badge: String?,
    done: Boolean,
    onToggle: () -> Unit,
    selected: Boolean = false,
    compact: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    val rowShape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 47.dp)
            .drawBehind {
                drawLine(Color(0x10FFFFFF), androidx.compose.ui.geometry.Offset(0f, size.height),
                    androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
            }
            .then(if (selected) Modifier.clip(rowShape).background(Color(0x0BFFFFFF), rowShape) else Modifier)
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = if (compact) 44.dp else 47.dp)
                .then(if (onSelect != null) Modifier.clickable(role = Role.Button, onClickLabel = "Elegir aproximación $label", onClick = onSelect) else Modifier)
                .padding(start = 4.dp)
                .testTag("warmup-row-$label"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.width(24.dp), style = PrepRef02Body(10.sp), color = if (selected) WizardColors.text else PrepRef02Faint)
            Text(percent, Modifier.width(46.dp), style = PrepRef02Body(11.sp), color = PrepRef02Muted)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(dose, style = PrepRef02Syne(16.sp), color = if (done) PrepRef02Muted else WizardColors.text)
                badge?.takeIf { it.isNotBlank() }?.let { Text(it, style = PrepRef02Body(9.sp), color = PrepRef02Faint) }
            }
        }
        Box(
            Modifier.size(width = 36.dp, height = 41.dp)
                .toggleable(value = done, role = Role.Checkbox) { onToggle() }
                .semantics { contentDescription = (if (done) "Desmarcar" else "Completar") + " aproximación $label" }
                .testTag("warmup-check-$label"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (done) KpknLineIcons.Check else KpknLineIcons.Plus, null, Modifier.size(if (done) 16.dp else 15.dp),
                tint = if (done) WizardColors.text else PrepRef02Muted)
        }
    }
}

/** Los kg con coma decimal es-CL y sin ceros sobrantes («62,5», «1,25», «60»). */
private fun prepKgText(value: Double): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("es-CL")).apply {
        isGroupingUsed = false
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }.format(value)

/** Paso − / + del editor de la aproximación: el mismo cuadro que `Ref02StepButton` de la tarjeta de la serie efectiva. */
@Composable
private fun PrepRef02StepButton(icon: ImageVector, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x07FFFFFF))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(15.dp), tint = PrepRef02Muted.copy(alpha = if (enabled) 1f else .4f)) }
}

/**
 * Editor de la aproximación elegida: los kg como la carga de la tarjeta de la serie efectiva (cifra en Syne con «kg» pegado y − / + en
 * cuadros de 36 con el paso del ejercicio) y las reps a la derecha, también editables. Con coma decimal. Si la aproximación ya está
 * registrada, enseña lo registrado y no se edita.
 */
@Composable
private fun PrepRef02WarmEditor(
    label: String,
    kgText: String,
    repsText: String,
    readOnly: Boolean,
    compact: Boolean,
    onKgChange: (String) -> Unit,
    onStepKg: (Int) -> Unit,
    onRepsChange: (String) -> Unit,
) {
    val figure = TextStyle(
        fontFamily = WizardFonts.display,
        fontSize = if (compact) 25.sp else 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "'lnum' 1, 'tnum' 1",
        color = if (readOnly) PrepRef02Muted else WizardColors.text,
    )
    val unit = PrepRef02Body(11.sp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .drawBehind {
                drawLine(Color(0x10FFFFFF), androidx.compose.ui.geometry.Offset(0f, 0f),
                    androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx())
            }
            .padding(top = 3.dp)
            .testTag("warmup-editor"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = kgText,
                onValueChange = onKgChange,
                readOnly = readOnly,
                singleLine = true,
                textStyle = figure,
                cursorBrush = SolidColor(WizardColors.text),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    .semantics { contentDescription = "Carga de la aproximación $label en kg" }
                    .testTag("warmup-editor-kg"),
                decorationBox = { inner ->
                    Box {
                        if (kgText.isBlank()) Text("—", style = figure.copy(color = PrepRef02Faint))
                        inner()
                    }
                },
            )
            Text("kg", Modifier.padding(start = 3.dp, bottom = if (compact) 5.dp else 6.dp), style = unit, color = PrepRef02Faint)
        }
        PrepRef02StepButton(KpknLineIcons.Minus, "Disminuir carga de la aproximación $label", !readOnly, "warmup-editor-minus") { onStepKg(-1) }
        PrepRef02StepButton(KpknLineIcons.Plus, "Aumentar carga de la aproximación $label", !readOnly, "warmup-editor-plus") { onStepKg(1) }
        Spacer(Modifier.width(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = repsText,
                onValueChange = onRepsChange,
                readOnly = readOnly,
                singleLine = true,
                // Sin alineación a la derecha: con ancho intrínseco desplazaba la cifra fuera del campo y la recortaba.
                textStyle = figure,
                cursorBrush = SolidColor(WizardColors.text),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    .widthIn(min = 18.dp)
                    .semantics { contentDescription = "Repeticiones de la aproximación $label" }
                    .testTag("warmup-editor-reps"),
            )
            Text("reps", Modifier.padding(start = 3.dp, bottom = if (compact) 5.dp else 6.dp), style = unit, color = PrepRef02Faint)
        }
    }
}

/**
 * El descanso corto de la preparación (`warmup-rest` del prototipo): la tarjeta del descanso con «DESCANSO · APROX.», el eclipse y la
 * siguiente aproximación; «Terminar descanso» en el pie. Corre en el mismo reloj que el de las series: con [onExpand] se abre a pantalla
 * completa, y con [onAdjust] / [onTogglePause] ofrece −15 s, pausa y +15 s. Sin ellos esos controles no se pintan.
 */
@Composable
private fun PrepRef02InlineRest(
    modifier: Modifier,
    compact: Boolean,
    kicker: String,
    remainingSeconds: Int,
    totalSeconds: Int,
    nextTitle: String?,
    nextDose: String?,
    onEnd: () -> Unit,
    showEnd: Boolean = true,
    isPaused: Boolean = false,
    onExpand: (() -> Unit)? = null,
    onAdjust: ((Int) -> Unit)? = null,
    onTogglePause: (() -> Unit)? = null,
) {
    RestRef02Card(
        compact = compact,
        modifier = modifier,
        tag = PrepLiveCardTag,
        footer = if (showEnd) {
            { RestRef02Button("Terminar descanso", onEnd, primary = false, modifier = Modifier.fillMaxWidth().testTag("prep-rest-skip")) }
        } else null,
    ) {
        RestRef02CardClockPanel(
            compact = compact,
            kicker = kicker,
            remainingSeconds = remainingSeconds,
            progress = (1f - remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f),
            isPaused = isPaused,
            animationsOn = !isPaused,
            onExpand = onExpand,
            onDecrease = { onAdjust?.invoke(-15) },
            onTogglePause = { onTogglePause?.invoke() },
            onIncrease = { onAdjust?.invoke(15) },
            nextTitle = nextTitle,
            nextDose = nextDose,
            suggestion = null,
            onOpenSuggestion = null,
            pauseEnabled = onTogglePause != null,
            decreaseEnabled = onAdjust != null,
            increaseEnabled = onAdjust != null,
        )
    }
}

/**
 * El descanso de la preparación a pantalla completa (vista expandida, como `.rest-fullscreen` de las series): estado arriba con el
 * ejercicio y «Minimizar a tarjeta», el eclipse grande con −15 s / pausa / +15 s, ÚLTIMA | SIGUIENTE y «Terminar descanso» al pie.
 */
@Composable
internal fun PrepRestFullscreen(
    eyebrow: String,
    title: String,
    remainingSeconds: Int,
    totalSeconds: Int,
    isPaused: Boolean,
    onAdjust: ((Int) -> Unit)?,
    onTogglePause: (() -> Unit)?,
    onMinimize: () -> Unit,
    onEnd: () -> Unit,
    lastText: String?,
    nextTitle: String?,
    nextDose: String?,
    modifier: Modifier = Modifier,
) {
    val total = totalSeconds.coerceAtLeast(remainingSeconds).coerceAtLeast(1)
    RestRef02FullscreenScaffold(
        eyebrow = eyebrow,
        title = title,
        onMinimize = onMinimize,
        modifier = modifier.testTag("prep-rest-fullscreen"),
        bottom = { RestRef02FullPrimary("Terminar descanso", onEnd, "prep-rest-full-end") },
    ) { compact ->
        RestRef02FullClockPanel(
            compact = compact,
            remainingSeconds = remainingSeconds,
            progress = (1f - remainingSeconds.toFloat() / total).coerceIn(0f, 1f),
            isPaused = isPaused,
            animationsOn = !isPaused,
            onDecrease = { onAdjust?.invoke(-15) },
            onTogglePause = { onTogglePause?.invoke() },
            onIncrease = { onAdjust?.invoke(15) },
            suggestion = null,
            onOpenSuggestion = null,
            lastSetText = lastText,
            nextTitle = nextTitle,
            nextDose = nextDose,
            onPlay = null,
            pauseEnabled = onTogglePause != null,
            decreaseEnabled = onAdjust != null,
        )
    }
}
