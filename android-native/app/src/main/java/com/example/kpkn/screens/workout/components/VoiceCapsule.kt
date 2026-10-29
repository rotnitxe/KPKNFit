package com.example.kpkn.screens.workout.components

import com.example.kpkn.ui.components.kpknFloatingSurface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.domain.workout.VoiceCommandRecord
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryStatus
import com.example.kpkn.domain.workout.VoiceHistoryValues
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Lo que la cápsula dice de la sesión: el ejercicio y la serie que espera, y el modo de captura. */
internal data class VoiceCapsuleContext(
    val exerciseName: String,
    val setLabel: String,
    val captureModeLabel: String,
    val resting: Boolean = false,
    /** El grabador de técnica está abierto: los comandos que valen son los de vídeo. */
    val capturing: Boolean = false,
    /** La sesión está en la vista Lista: la cápsula se apoya abajo y el registro vuela a su fila (v-list). */
    val listView: Boolean = false,
    /** Lo que queda del descanso en curso, para el recibo de un comando («Comando aplicado · queda 1:47»). */
    val restRemainingSeconds: Int? = null,
    /** Repeticiones del plan de la serie actual: los botones de «Por revisar» se centran en ellas si no se oyeron. */
    val plannedReps: Int? = null,
)

// `.vpill` (26 de radio) y `.vbar` (56 de alto). La superficie no es vidrio: gris plano con sombra real (decisión del 29-oct).
private val CapsuleShape = RoundedCornerShape(26.dp)
private val PanelShape = RoundedCornerShape(28.dp)
/** `.vpill{transition:height .5s cubic-bezier(.2,.9,.2,1)}`: la cápsula crece y se reduce con esta curva. */
private val CapsuleEase = CubicBezierEasing(.2f, .9f, .2f, 1f)
private val CapsuleBody = TextStyle(fontFamily = WizardFonts.body)
private val CapsuleSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)
private val capsuleTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Cuánto tiempo se ve el recibo de una serie registrada por voz. */
private const val RECEIPT_MS = 8_000L

/** Una captura cuenta como «recién llegada» (recibo o revisión en la cápsula) durante este tiempo. */
private const val FRESH_MS = 20_000L

/**
 * Modo Voz como capa (iteración 03, aprobada): una cápsula de vidrio que flota sobre la sesión, justo encima del roadmap (en Lista, abajo).
 * «Te escucho» con el orbe, qué serie espera y cómo decirlo, el contador de registros, pausa y ⌃. Al registrar por voz, la cápsula crece
 * con la frase y se queda como recibo «Serie N registrada» con «Deshacer» y «Editar»; una frase ambigua queda «Por revisar» con lo que
 * falta en botones grandes, «Descartar» y «Registrar». Arrastrar hacia arriba (o ⌃) abre los registros: «Todo» / «Por revisar», cada
 * captura con su estado, editar con chips y «Desactivar Modo Voz». Mientras la guía de encuadre habla: «KPKN habla» y «Detener».
 */
@Composable
internal fun VoiceCapsuleLayer(
    entries: List<VoiceHistoryEntry>,
    voiceEnabled: Boolean,
    context: VoiceCapsuleContext,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bottomPadding: Dp,
    notice: String?,
    guideSpeech: String?,
    onStopGuide: () -> Unit,
    onTogglePause: () -> Unit,
    onDisable: () -> Unit,
    onListenExample: () -> Unit,
    onConfirmCorrection: (String, Double, Int, Double) -> Unit,
    onUndo: (String) -> Unit,
    onDiscard: (String) -> Unit,
    modifier: Modifier = Modifier,
    nowMs: () -> Long = System::currentTimeMillis,
    /** Comandos aplicados en la sesión («+15 s», «Iniciar vídeo»…): cuentan en los registros como «Comando». */
    commands: List<VoiceCommandRecord> = emptyList(),
    /** La frase parcial del reconocedor y lo que KPKN está diciendo (datos reales del motor). Nulo: sin motor (pruebas). */
    live: State<VoiceLiveFeed>? = null,
    /** Nivel real del micrófono (dB) para la onda del orbe. */
    levelDb: State<Float>? = null,
    /** La frase que el parser acaba de llevar al borrador de una serie: «Entendido» y el vuelo de sus valores a su campo. */
    heard: VoiceHeardCapture? = null,
    /** «Detener» mientras KPKN habla. */
    onStopSpeech: () -> Unit = {},
    /** La cápsula está a la vista y puede llevar los valores a su campo antes de que la serie se registre. */
    onFlightArmedChange: (Boolean) -> Unit = {},
    /** Los valores de la frase ya llegaron a su campo (o la frase no tenía valores que llevar). */
    onValuesDelivered: () -> Unit = {},
) {
    val reduced = wizardReducedMotion()
    val targets = LocalVoiceFlightTargets.current
    var receiptId by remember { mutableStateOf<String?>(null) }
    var commandReceiptId by remember { mutableStateOf<String?>(null) }
    var dismissedReviewId by remember { mutableStateOf<String?>(null) }
    var correcting by remember { mutableStateOf<VoiceHistoryEntry?>(null) }
    var auditing by remember { mutableStateOf<VoiceHistoryEntry?>(null) }
    // v-record: «Entendido» con la frase y sus valores; luego vuelan a su campo (`flyTokens`). v-rest: el comando oído y su vuelo al reloj.
    var heardSeen by remember { mutableStateOf<VoiceHeardCapture?>(null) }
    var heardShown by remember { mutableStateOf<VoiceHeardCapture?>(null) }
    var heardFlying by remember { mutableStateOf(false) }
    var commandShown by remember { mutableStateOf<VoiceCommandRecord?>(null) }
    var commandFlying by remember { mutableStateOf(false) }
    val tokenRects = remember { mutableStateMapOf<Int, Rect>() }
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val latestEntries = rememberUpdatedState(entries)
    val latestDelivered by rememberUpdatedState(onValuesDelivered)
    val latestArmed by rememberUpdatedState(onFlightArmedChange)

    // Con la cápsula a la vista (y sin movimiento reducido), la serie dictada espera a que sus valores lleguen a su campo.
    val armed = voiceEnabled && !expanded && !reduced && guideSpeech == null
    DisposableEffect(armed) {
        latestArmed(armed)
        onDispose { latestArmed(false) }
    }
    LaunchedEffect(heard?.id) { if (heard != null) heardSeen = heard }
    LaunchedEffect(heardSeen?.id) {
        val capture = heardSeen ?: return@LaunchedEffect
        val show = !expanded && !reduced && capture.tokens.isNotEmpty() && nowMs() - capture.atMs < HEARD_FRESH_MS
        if (!show) {
            latestDelivered()
            return@LaunchedEffect
        }
        tokenRects.clear()
        heardFlying = false
        heardShown = capture
        try {
            // `runRecord`: las fichas aparecen y 0,5 s después vuelan (60 ms + 140 ms por ficha, 0,7 s de vuelo, brillo al llegar).
            delay(500)
            heardFlying = true
            val flights = if (context.listView) 1 else capture.tokens.size
            delay(FLY_DELAY_MS + FLY_MS + FLY_LAND_MS + (flights - 1) * FLY_STAGGER_MS)
        } finally {
            latestDelivered()
        }
        // Se registra (o queda «Por revisar»): la cápsula se reduce a su recibo en cuanto la captura llega.
        delay(500)
        withTimeoutOrNull(HEARD_WAIT_MS) {
            snapshotFlow {
                latestEntries.value.any {
                    it.exerciseId == capture.exerciseId && it.setIdx == capture.setIdx && it.updatedAtEpochMs >= capture.atMs - 1_000
                }
            }.first { it }
        }
        if (heardShown?.id == capture.id) {
            heardShown = null
            heardFlying = false
        }
    }
    val newest = entries.maxByOrNull { it.updatedAtEpochMs }
    LaunchedEffect(newest?.id, newest?.updatedAtEpochMs, newest?.status) {
        val entry = newest ?: return@LaunchedEffect
        if (entry.status == VoiceHistoryStatus.REGISTERED && nowMs() - entry.updatedAtEpochMs < FRESH_MS) {
            receiptId = entry.id
            delay(RECEIPT_MS)
            if (receiptId == entry.id) receiptId = null
        }
    }
    val newestCommand = commands.maxByOrNull { it.atEpochMs }
    LaunchedEffect(newestCommand?.id) {
        val command = newestCommand ?: return@LaunchedEffect
        if (nowMs() - command.atEpochMs >= FRESH_MS) return@LaunchedEffect
        if (!reduced && !expanded) {
            // `runRest`: la frase y su valor; 0,4 s después «+15 s» vuela al reloj y, al llegar, la cápsula se reduce al recibo.
            commandFlying = false
            commandShown = command
            delay(400)
            if (command.isRestDelta()) {
                commandFlying = true
                delay(FLY_DELAY_MS + FLY_MS + FLY_LAND_MS)
            }
            commandShown = null
            commandFlying = false
        }
        commandReceiptId = command.id
        delay(COMMAND_RECEIPT_MS)
        if (commandReceiptId == command.id) commandReceiptId = null
    }
    val commandReceipt = commands.firstOrNull { it.id == commandReceiptId }
    val total = entries.size + commands.size
    val receipt = entries.firstOrNull { it.id == receiptId && it.status == VoiceHistoryStatus.REGISTERED }
    val review = entries.lastOrNull { it.status == VoiceHistoryStatus.REVIEW && it.id != dismissedReviewId &&
        nowMs() - it.createdAtEpochMs < FRESH_MS * 15 }
    // v-review: la tarjeta enseña el borrador de voz marcado (borde discontinuo) mientras la captura está por revisar.
    DisposableEffect(review?.id, review?.updatedAtEpochMs, context.listView) {
        val marked = review?.takeIf { !context.listView }?.let { entry ->
            listOfNotNull(
                voiceFieldTargetKey(entry.exerciseId, entry.setIdx, VoiceFlightField.LOAD).takeIf { entry.values.weightKg != null },
                voiceFieldTargetKey(entry.exerciseId, entry.setIdx, VoiceFlightField.QUANTITY)
                    .takeIf { entry.values.reps != null || entry.values.seconds != null },
                voiceFieldTargetKey(entry.exerciseId, entry.setIdx, VoiceFlightField.EFFORT).takeIf { entry.values.intensityValue != null },
            )
        }.orEmpty()
        marked.forEach(targets::mark)
        onDispose { marked.forEach(targets::unmark) }
    }

    // La frase parcial del reconocedor: se enseña mientras se dice y se da por usada en cuanto llega su captura o su comando.
    val feed = live?.value ?: VoiceLiveFeed()
    val partial = feed.partialText.trim()
    var phraseSince by remember { mutableStateOf(0L) }
    var phraseStale by remember { mutableStateOf(false) }
    val consumedAt = maxOf(heardSeen?.atMs ?: 0L, newestCommand?.atEpochMs ?: 0L, newest?.updatedAtEpochMs ?: 0L)
    val latestConsumedAt by rememberUpdatedState(consumedAt)
    LaunchedEffect(partial) {
        phraseStale = false
        if (partial.isBlank()) return@LaunchedEffect
        if (phraseSince <= latestConsumedAt) phraseSince = nowMs()
        delay(PHRASE_STALE_MS)
        phraseStale = true
    }
    val livePhrase = partial.takeIf { it.isNotBlank() && feed.listening && voiceEnabled && !phraseStale && phraseSince > consumedAt }
    val spoken = feed.spokenText?.takeIf { it.isNotBlank() }

    val show: CapsuleShow = when {
        guideSpeech != null -> CapsuleShow.Speak(guideSpeech, guide = true)
        heardShown != null -> CapsuleShow.Heard(heardShown!!, heardFlying)
        commandShown != null -> CapsuleShow.CommandHeard(commandShown!!, commandFlying)
        livePhrase != null -> CapsuleShow.Phrase(livePhrase)
        review != null -> CapsuleShow.Review(review)
        receipt != null -> CapsuleShow.Receipt(receipt)
        commandReceipt != null -> CapsuleShow.CommandReceipt(commandReceipt)
        spoken != null -> CapsuleShow.Speak(spoken, guide = false)
        !voiceEnabled -> CapsuleShow.Paused
        else -> CapsuleShow.Listen
    }

    Box(
        modifier.fillMaxSize().zIndex(30f).onGloballyPositioned {
            layerOrigin = it.positionInRoot()
            layerSize = it.size
        },
    ) {
        if (expanded) BackHandler { onExpandedChange(false) }
        // Registros: el velo se funde y el panel sube con un fundido; al cerrar, al revés (nada aparece ni desaparece de golpe).
        AnimatedVisibility(visible = expanded, enter = liveFadeIn(reduced), exit = liveFadeOut(reduced)) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onExpandedChange(false) },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) androidx.compose.animation.EnterTransition.None
            else fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 8 },
            exit = if (reduced) androidx.compose.animation.ExitTransition.None
            else fadeOut(tween(160, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                VoiceRecordsPanel(
                    entries = entries,
                    commands = commands,
                    voiceEnabled = voiceEnabled,
                    context = context,
                    notice = notice,
                    height = maxHeight * .66f,
                    onCollapse = { onExpandedChange(false) },
                    onTogglePause = onTogglePause,
                    onDisable = onDisable,
                    onListenExample = onListenExample,
                    onConfirmCorrection = onConfirmCorrection,
                    onDiscard = onDiscard,
                    onReview = { correcting = it },
                    onAudit = { auditing = it },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 18.dp).navigationBarsPadding(),
                )
            }
        }
        AnimatedVisibility(
            visible = !expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = liveFadeIn(reduced),
            exit = liveFadeOut(reduced),
        ) {
            val listenSubtitle = when {
                context.capturing -> "Captura · di «Iniciar vídeo» o «Detener vídeo»"
                context.resting -> "Descanso · di «más quince» o «terminar descanso»"
                else -> "${context.exerciseName} · ${context.setLabel} · di peso, reps y esfuerzo"
            }
            val expand = { onExpandedChange(true) }
            Column(
                Modifier.padding(start = 10.dp, end = 10.dp, bottom = bottomPadding).fillMaxWidth()
                    // Píldora flotante: gris oscuro plano con sombra real de elevación (pedido del usuario, 29-oct).
                    .kpknFloatingSurface(CapsuleShape, com.example.kpkn.ui.components.KpknFloating.PillElevation)
                    .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -12f) onExpandedChange(true) } }
                    // `.vpill`: crece y se reduce en 0,5 s con cubic-bezier(.2,.9,.2,1).
                    .animateContentSize(if (reduced) androidx.compose.animation.core.snap<IntSize>() else tween<IntSize>(500, easing = CapsuleEase))
                    .semantics { paneTitle = "Modo Voz" }
                    .testTag("voice-capsule"),
            ) {
                // La frase que se dice y la que se entiende comparten la cápsula (no se funden entre sí); el resto de estados se funde.
                AnimatedContent(
                    targetState = show,
                    contentKey = { it.key },
                    transitionSpec = { liveContentSwap(reduced) },
                    label = "voice-capsule-state",
                ) { state ->
                    Column(Modifier.fillMaxWidth()) {
                        when (state) {
                            is CapsuleShow.Speak -> CapsuleBar(
                                state = OrbState.SPEAK, title = "KPKN habla", subtitle = "«${state.text}»", count = total, levelDb = levelDb,
                                trailing = {
                                    VoicePillButton("Detener", if (state.guide) onStopGuide else onStopSpeech, height = 36.dp, fontSize = 12,
                                        stopGlyph = true, horizontalPadding = 12.dp,
                                        tag = if (state.guide) "voice-capsule-stop-guide" else "voice-capsule-stop-speech")
                                },
                                onExpand = expand,
                            )
                            is CapsuleShow.Phrase, is CapsuleShow.Heard, is CapsuleShow.CommandHeard -> {
                                val title = when (state) {
                                    is CapsuleShow.Heard -> "Entendido"
                                    is CapsuleShow.Phrase -> if (context.resting) "Te escucho" else "Te escucho…"
                                    else -> "Te escucho"
                                }
                                CapsuleBar(state = OrbState.LISTEN, title = title, subtitle = listenSubtitle, count = total, levelDb = levelDb,
                                    trailing = { PauseButton(true, onTogglePause) }, onExpand = expand)
                                VoiceLiveBody(
                                    state = state,
                                    context = context,
                                    onTokenPlaced = { index, rect -> tokenRects[index] = rect },
                                )
                            }
                            is CapsuleShow.Review -> {
                                val entry = state.entry
                                CapsuleBar(state = OrbState.LISTEN, title = "Por revisar · no se registró",
                                    subtitle = "${entry.exerciseName} · serie ${entry.setIdx + 1} · faltan datos claros", count = total, levelDb = levelDb,
                                    trailing = { PauseButton(voiceEnabled, onTogglePause) }, onExpand = expand)
                                ReviewBody(
                                    entry = entry,
                                    plannedReps = context.plannedReps,
                                    onDiscard = { dismissedReviewId = entry.id; onDiscard(entry.id) },
                                    onRegister = { weight, reps, rir -> dismissedReviewId = entry.id; onConfirmCorrection(entry.id, weight, reps, rir) },
                                    onReview = { correcting = entry },
                                )
                            }
                            is CapsuleShow.Receipt -> {
                                val entry = state.entry
                                if (context.listView) {
                                    // v-list: el recibo se queda en la barra; «Deshacer» va en el subtítulo.
                                    CapsuleBar(state = OrbState.LISTEN, title = "Serie ${entry.setIdx + 1} registrada",
                                        subtitle = "${capsuleValues(entry.values)} · Deshacer", count = total, levelDb = levelDb,
                                        trailing = { PauseButton(voiceEnabled, onTogglePause) }, onExpand = expand,
                                        onTextClick = { receiptId = null; onUndo(entry.id) }, textClickLabel = "Deshacer")
                                } else {
                                    CapsuleBar(state = OrbState.LISTEN, title = "Serie ${entry.setIdx + 1} registrada",
                                        subtitle = "${capsuleValues(entry.values)} · por voz", count = total, levelDb = levelDb,
                                        trailing = { PauseButton(voiceEnabled, onTogglePause) }, onExpand = expand)
                                    // `.vact` del recibo: dos botones de 34 sin margen superior.
                                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        VoicePillButton("Deshacer", { receiptId = null; onUndo(entry.id) }, Modifier.weight(1f), height = 34.dp, tag = "voice-capsule-undo")
                                        VoicePillButton("Editar", { receiptId = null; correcting = entry }, Modifier.weight(1f), height = 34.dp,
                                            icon = KpknLineIcons.Edit, tag = "voice-capsule-edit")
                                    }
                                }
                            }
                            is CapsuleShow.CommandReceipt -> {
                                val record = state.record
                                val left = context.restRemainingSeconds?.takeIf { context.resting && record.detail == "descanso" && it > 0 }
                                CapsuleBar(state = OrbState.LISTEN,
                                    title = if (record.isRestDelta()) "Descanso ${record.value}" else record.value,
                                    subtitle = left?.let { "Comando aplicado · queda ${capsuleClockSeconds(it)}" } ?: "Comando aplicado",
                                    count = total, levelDb = levelDb,
                                    trailing = { PauseButton(voiceEnabled, onTogglePause) }, onExpand = expand)
                            }
                            CapsuleShow.Paused -> CapsuleBar(state = OrbState.IDLE, title = "Escucha en pausa",
                                subtitle = "Los registros se conservan · toca para seguir", count = total, levelDb = levelDb,
                                trailing = { PauseButton(false, onTogglePause) }, onExpand = expand,
                                onTextClick = onTogglePause, textClickLabel = "Reanudar escucha")
                            CapsuleShow.Listen -> CapsuleBar(state = OrbState.LISTEN, title = "Te escucho", subtitle = listenSubtitle,
                                count = total, levelDb = levelDb, trailing = { PauseButton(true, onTogglePause) }, onExpand = expand)
                        }
                    }
                }
            }
        }
        // `flyTokens`: los valores salen de sus fichas y vuelan a su campo; al llegar, el campo brilla.
        val flyingCapture = heardShown?.takeIf { heardFlying && !expanded }
        val flyingCommand = commandShown?.takeIf { commandFlying && !expanded }
        if (flyingCapture != null || flyingCommand != null) {
            val density = LocalDensity.current
            val flights = remember(flyingCapture?.id, flyingCommand?.id) {
                voiceFlights(flyingCapture, flyingCommand, context, tokenRects.toMap(), targets, layerOrigin, layerSize, density)
            }
            key(flyingCapture?.id ?: flyingCommand?.id) {
                flights.forEachIndexed { index, flight ->
                    VoiceFlyingToken(flight, index, onLanded = { flight.targetKey?.let(targets::glow) })
                }
            }
        }
    }

    correcting?.let { entry ->
        VoiceCorrectionSheet(
            entry = entry,
            onDismiss = { correcting = null },
            onConfirm = { weight, reps, rir ->
                correcting = null
                onConfirmCorrection(entry.id, weight, reps, rir)
            },
        )
    }
    auditing?.let { entry -> VoiceAuditSheet(entry = entry, onDismiss = { auditing = null }) }
}

private enum class OrbState { LISTEN, IDLE, SPEAK }

/** Cuánto se ve el recibo de un comando aplicado. */
private const val COMMAND_RECEIPT_MS = 5_000L

/** Una frase del parser cuenta como recién oída (y vuela) durante este tiempo. */
private const val HEARD_FRESH_MS = 4_000L

/** Tras el vuelo, la cápsula espera como mucho esto a que llegue la captura registrada (o por revisar). */
private const val HEARD_WAIT_MS = 2_500L

/** Una frase parcial que no cambia en este tiempo ya no se está diciendo. */
private const val PHRASE_STALE_MS = 5_000L

// `.fly{transition:all .7s cubic-bezier(.5,0,.2,1)}`, lanzadas a los 60 ms + 140 ms por ficha; el campo brilla a los 880 ms.
private const val FLY_DELAY_MS = 60L
private const val FLY_MS = 700L
private const val FLY_LAND_MS = 120L
private const val FLY_STAGGER_MS = 140L
private val FlyEase = CubicBezierEasing(.5f, 0f, .2f, 1f)

/** Lo que enseña la cápsula plegada. [key] agrupa los estados que comparten cápsula (frase oída → entendida) para no fundirlos. */
private sealed interface CapsuleShow {
    val key: String
    data class Speak(val text: String, val guide: Boolean) : CapsuleShow { override val key = "speak" }
    data class Phrase(val text: String) : CapsuleShow { override val key = "live" }
    data class Heard(val capture: VoiceHeardCapture, val flying: Boolean) : CapsuleShow { override val key = "live" }
    data class CommandHeard(val record: VoiceCommandRecord, val flying: Boolean) : CapsuleShow { override val key = "live" }
    data class Review(val entry: VoiceHistoryEntry) : CapsuleShow { override val key = "review" }
    data class Receipt(val entry: VoiceHistoryEntry) : CapsuleShow { override val key = "receipt" }
    data class CommandReceipt(val record: VoiceCommandRecord) : CapsuleShow { override val key = "command" }
    data object Paused : CapsuleShow { override val key = "paused" }
    data object Listen : CapsuleShow { override val key = "listen" }
}

private fun VoiceCommandRecord.isRestDelta(): Boolean = detail == "descanso" && value.firstOrNull()?.let { it in "+−" } == true

/** Un vuelo: el texto que viaja, de dónde sale y a dónde llega (en coordenadas de la capa) y el destino que brilla al llegar. */
private data class VoiceFlight(val text: String, val start: Offset, val end: Offset, val targetKey: String?)

/**
 * Los vuelos de la frase entendida (cada valor a su campo; en Lista, uno solo a su fila) o del comando de descanso (al reloj). Si un
 * destino no está a la vista, el valor vuela al sitio donde ese campo queda en la tarjeta y no brilla nada.
 */
private fun voiceFlights(
    capture: VoiceHeardCapture?,
    command: VoiceCommandRecord?,
    context: VoiceCapsuleContext,
    tokenRects: Map<Int, Rect>,
    targets: VoiceFlightTargets,
    origin: Offset,
    layer: IntSize,
    density: androidx.compose.ui.unit.Density,
): List<VoiceFlight> {
    val w = layer.width.toFloat()
    val h = layer.height.toFloat()
    val half = with(density) { Offset(30.dp.toPx(), 15.dp.toPx()) }
    fun visible(rect: Rect?): Rect? = rect?.takeIf {
        it.width > 0f && it.height > 0f && it.center.x - origin.x in 0f..w && it.center.y - origin.y in 0f..h
    }
    fun startOf(index: Int): Offset = tokenRects[index]?.topLeft?.minus(origin)
        ?: Offset(w / 2f - half.x, h - with(density) { 160.dp.toPx() })
    fun endOf(key: String, fallback: Offset): Pair<Offset, String?> {
        val rect = visible(targets.boundsInRoot(key)) ?: return fallback to null
        return (rect.center - origin - half) to key
    }
    if (command != null) {
        val (end, key) = endOf(VoiceRestClockTargetKey, Offset(w / 2f - half.x, h * .3f))
        return listOf(VoiceFlight(command.value, startOf(0), end, key))
    }
    val heard = capture ?: return emptyList()
    if (context.listView) {
        val text = listTokenText(heard)
        val (end, key) = endOf(voiceRowTargetKey(heard.exerciseId, heard.setIdx), Offset(w / 2f - half.x, h * .4f))
        return listOf(VoiceFlight(text, startOf(0), end, key))
    }
    return heard.tokens.mapIndexed { index, token ->
        val fallback = when (token.field) {
            VoiceFlightField.LOAD -> Offset(w * .25f, h * .3f)
            VoiceFlightField.QUANTITY -> Offset(w * .5f, h * .42f)
            VoiceFlightField.EFFORT -> Offset(w * .7f, h * .52f)
        } - half
        val (end, key) = endOf(voiceFieldTargetKey(heard.exerciseId, heard.setIdx, token.field), fallback)
        VoiceFlight(token.fly, startOf(index), end, key)
    }
}

/** v-list: los valores van juntos en una ficha («100 × 8 · RIR 2»). */
private fun listTokenText(capture: VoiceHeardCapture): String {
    val load = capture.tokens.firstOrNull { it.field == VoiceFlightField.LOAD }?.fly
    val quantity = capture.tokens.firstOrNull { it.field == VoiceFlightField.QUANTITY }?.fly
    val effort = capture.tokens.firstOrNull { it.field == VoiceFlightField.EFFORT }?.fly
    val main = listOfNotNull(load, quantity).joinToString(" × ")
    return listOfNotNull(main.takeIf { it.isNotBlank() }, effort).joinToString(" · ")
}

/**
 * `.fly`: una píldora clara (30 de alto, radio 11, Syne 800 de 13) con el valor; sale de su ficha, viaja en 0,7 s, se encoge a 0,7 y se
 * desvanece; 120 ms después de llegar su campo brilla.
 */
@Composable
private fun VoiceFlyingToken(flight: VoiceFlight, index: Int, onLanded: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val latestLanded by rememberUpdatedState(onLanded)
    LaunchedEffect(Unit) {
        delay(FLY_DELAY_MS + index * FLY_STAGGER_MS)
        progress.animateTo(1f, tween(FLY_MS.toInt(), easing = FlyEase))
        delay(FLY_LAND_MS)
        latestLanded()
    }
    val t = progress.value
    if (t >= 1f) return
    Box(
        Modifier
            .offset {
                IntOffset(
                    (flight.start.x + (flight.end.x - flight.start.x) * t).roundToInt(),
                    (flight.start.y + (flight.end.y - flight.start.y) * t).roundToInt(),
                )
            }
            .graphicsLayer { val sc = 1f - .3f * t; scaleX = sc; scaleY = sc; alpha = 1f - t }
            .zIndex(45f)
            .height(30.dp).clip(RoundedCornerShape(11.dp)).background(WizardColors.text).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(flight.text, style = CapsuleSyne.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold), color = Color(0xFF111111),
            maxLines = 1)
    }
}

/**
 * `.vbody` de la frase: «lo que se dice» con el cursor mientras se habla; al entenderla, las fichas de valor (`.tokens`) y la serie a la
 * que van (`.vlink`). Mide como la cápsula del prototipo: 176 en la tarjeta, 150 en Lista y 132 en el descanso.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceLiveBody(state: CapsuleShow, context: VoiceCapsuleContext, onTokenPlaced: (Int, Rect) -> Unit) {
    val minBody = when {
        state is CapsuleShow.CommandHeard || context.resting -> 76.dp
        context.listView -> 94.dp
        else -> 120.dp
    }
    Column(Modifier.fillMaxWidth().heightIn(min = minBody).padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        when (state) {
            is CapsuleShow.Phrase -> VoicePhrase(state.text, caret = true)
            is CapsuleShow.Heard -> VoicePhrase(state.capture.transcript, caret = false)
            is CapsuleShow.CommandHeard -> VoicePhrase(state.record.transcript.ifBlank { state.record.value }, caret = false)
            else -> Unit
        }
        val dim by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if ((state as? CapsuleShow.Heard)?.flying == true || (state as? CapsuleShow.CommandHeard)?.flying == true) .25f else 1f,
            animationSpec = tween(160), label = "voice-token-dim",
        )
        when (state) {
            is CapsuleShow.Heard -> {
                FlowRow(Modifier.padding(top = 10.dp).graphicsLayer { alpha = dim }, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (context.listView) {
                        VoiceTokenChip(listTokenText(state.capture), null, missing = false, index = 0, onPlaced = { onTokenPlaced(0, it) })
                    } else {
                        state.capture.tokens.forEachIndexed { index, token ->
                            VoiceTokenChip(token.value, token.unit, missing = false, index = index, onPlaced = { onTokenPlaced(index, it) })
                        }
                    }
                }
                if (!context.listView) {
                    Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(KpknLineIcons.Check, null, Modifier.size(12.dp), tint = LiveSheetTokens.Faint)
                        Text("${state.capture.exerciseName} · serie ${state.capture.setIdx + 1}", style = CapsuleBody.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                            color = LiveSheetTokens.Faint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            is CapsuleShow.CommandHeard -> Row(Modifier.padding(top = 10.dp).graphicsLayer { alpha = dim }) {
                VoiceTokenChip(state.record.value, null, missing = false, index = 0, onPlaced = { onTokenPlaced(0, it) })
            }
            else -> Unit
        }
    }
}

/** `.phrase`: Inter 14 en tinta entre comillas; mientras se habla, el cursor `.caret` (2 × 15) parpadea al final. */
@Composable
private fun VoicePhrase(text: String, caret: Boolean) {
    val reduced = wizardReducedMotion()
    val blink by androidx.compose.animation.core.rememberInfiniteTransition(label = "voice-caret").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes { durationMillis = 800; 1f at 0; 1f at 399; 0f at 400; 0f at 799 },
        ),
        label = "voice-caret-blink",
    )
    val content = androidx.compose.ui.text.buildAnnotatedString {
        append("«$text»")
        if (caret) appendInlineContent("caret", "|")
    }
    val inline = if (caret) mapOf(
        "caret" to androidx.compose.foundation.text.InlineTextContent(
            androidx.compose.ui.text.Placeholder(4.sp, 15.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter),
        ) {
            Box(Modifier.fillMaxSize().padding(start = 2.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.width(2.dp).height(15.dp).graphicsLayer { alpha = if (reduced) 1f else blink }.background(WizardColors.text))
            }
        },
    ) else emptyMap()
    Text(content, Modifier.fillMaxWidth().heightIn(min = 20.dp).testTag("voice-capsule-phrase"),
        style = CapsuleBody.copy(fontSize = 14.sp, lineHeight = 19.sp), color = WizardColors.text, inlineContent = inline)
}

/**
 * `.tok`: 34 de alto, radio 12, #ffffff12, Syne 800 de 14 con la unidad en Inter 10 tenue; entra desde abajo (`tokin`, 0,45 s,
 * escalonadas 0,12 s). `.tok.miss`: sin relleno, borde discontinuo #ffffff40 y texto tenue.
 */
@Composable
private fun VoiceTokenChip(value: String, unit: String?, missing: Boolean, index: Int, onPlaced: ((Rect) -> Unit)? = null) {
    val reduced = wizardReducedMotion()
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(index * 120L)
            enter.animateTo(1f, tween(450, easing = CapsuleEase))
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val dash = Color(0x40FFFFFF)
    Row(
        Modifier
            .graphicsLayer {
                val t = enter.value
                alpha = t
                translationY = (1f - t) * 10.dp.toPx()
                val sc = .8f + .2f * t
                scaleX = sc; scaleY = sc
            }
            .then(if (onPlaced != null) Modifier.onGloballyPositioned { onPlaced(it.boundsInRoot()) } else Modifier)
            .height(34.dp)
            .clip(shape)
            .then(
                if (missing) Modifier.drawBehind {
                    drawRoundRect(dash, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                } else Modifier.background(Color(0x12FFFFFF)),
            )
            .padding(horizontal = 11.dp)
            .testTag("voice-capsule-token"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(value, style = CapsuleSyne.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
            color = if (missing) LiveSheetTokens.Faint else WizardColors.text, maxLines = 1)
        unit?.let { Text(it, style = CapsuleBody.copy(fontSize = 10.sp, lineHeight = 12.sp), color = LiveSheetTokens.Faint, maxLines = 1) }
    }
}

/**
 * `.btn` dentro de la cápsula: píldora (radio completo) de 38 (34 en el recibo, 36 en «Detener»), Inter 600 de 13. `.secondary`: sin
 * relleno con filete #ffffff22; `.primary`: tinta con texto #111.
 */
@Composable
private fun VoicePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 38.dp,
    fontSize: Int = 13,
    horizontalPadding: Dp = 18.dp,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** El cuadrado de «Detener» (`ic('stop',12)`). */
    stopGlyph: Boolean = false,
    tag: String? = null,
) {
    val shape = RoundedCornerShape(50)
    val fill by androidx.compose.animation.animateColorAsState(
        when {
            !primary -> Color.Transparent
            enabled -> WizardColors.text
            else -> WizardColors.text.copy(alpha = .35f)
        },
        tween(220), label = "voice-pill-fill",
    )
    val ink = if (primary) Color(0xFF111111) else WizardColors.text.copy(alpha = if (enabled) 1f else .4f)
    Row(
        modifier.height(height).clip(shape).background(fill)
            .then(if (primary) Modifier else Modifier.border(1.dp, Color(0x22FFFFFF), shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, null, Modifier.size((fontSize).dp), tint = ink) }
        if (stopGlyph) Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(ink))
        Text(text, style = CapsuleBody.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 4).sp, fontWeight = FontWeight.SemiBold), color = ink,
            maxLines = 1)
    }
}

private fun capsuleClockSeconds(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** `.vbar`: orbe, título y subtítulo, contador, la acción de la derecha y ⌃ para abrir los registros. */
@Composable
private fun CapsuleBar(
    state: OrbState,
    title: String,
    subtitle: String,
    count: Int?,
    trailing: @Composable () -> Unit,
    onExpand: (() -> Unit)?,
    expandIcon: androidx.compose.ui.graphics.vector.ImageVector = KpknLineIcons.ChevronUp,
    expandDescription: String = "Ver registros de voz",
    levelDb: State<Float>? = null,
    /** Tocar el texto («toca para seguir», «Deshacer» en Lista). */
    onTextClick: (() -> Unit)? = null,
    textClickLabel: String? = null,
) {
    // `.vbar`: 56 de alto, 10 entre piezas, relleno 0 6 0 12.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VoiceOrb(state, levelDb)
        Column(
            Modifier.weight(1f).padding(vertical = 8.dp)
                .then(if (onTextClick != null) Modifier.clickable(role = Role.Button, onClickLabel = textClickLabel, onClick = onTextClick) else Modifier),
        ) {
            // `.vtext strong`: Inter 600 de 13; `span`: 10,5 tenue, una línea con puntos suspensivos.
            Text(title, style = CapsuleBody.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = WizardColors.text, modifier = Modifier.testTag("voice-capsule-title"))
            Text(subtitle, Modifier.padding(top = 2.dp).testTag("voice-capsule-subtitle"), style = CapsuleBody.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
                color = LiveSheetTokens.Faint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (count != null && count > 0) {
            // `.vcount`: 28 de alto, radio 14, #ffffff10, Inter 600 de 11.
            Box(Modifier.heightIn(min = 28.dp).widthIn(min = 28.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x10FFFFFF))
                .padding(horizontal = 9.dp).semantics { contentDescription = "$count registros de voz" }.testTag("voice-capsule-count"),
                contentAlignment = Alignment.Center) {
                Text("$count", style = CapsuleBody.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
            }
        }
        trailing()
        if (onExpand != null) {
            Box(Modifier.size(width = 36.dp, height = 40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onExpand)
                .semantics { contentDescription = expandDescription }.testTag("voice-capsule-expand"), contentAlignment = Alignment.Center) {
                Icon(expandIcon, null, Modifier.size(16.dp), tint = LiveSheetTokens.Muted)
            }
        }
    }
}

/** `.icon-btn` de 40 × 40: pausar o reanudar la escucha. */
@Composable
private fun PauseButton(listening: Boolean, onToggle: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onToggle)
        .semantics { contentDescription = if (listening) "Pausar escucha" else "Reanudar escucha" }.testTag("voice-capsule-pause"),
        contentAlignment = Alignment.Center) {
        Icon(if (listening) KpknLineIcons.Pause else KpknLineIcons.Play, null, Modifier.size(16.dp), tint = LiveSheetTokens.Muted)
    }
}

/**
 * `.orb`: círculo de 36 con filete #ffffff2a y cinco barras de 3 de ancho (2 entre ellas) que suben de 5 a 20 en 1 s, escalonadas
 * 0,15 s. Al escuchar, la altura sigue el nivel REAL del micrófono; en pausa, quietas a 5 y a media opacidad; cuando KPKN habla, en
 * gris y más lentas (1,6 s).
 */
@Composable
private fun VoiceOrb(state: OrbState, levelDb: State<Float>? = null) {
    val reduced = wizardReducedMotion()
    val visibility = rememberFeedbackAnimationVisibility()
    val clock = rememberRestClock(state != OrbState.IDLE && visibility.visible && !reduced)
    val ink = if (state == OrbState.SPEAK) LiveSheetTokens.Muted else WizardColors.text
    Canvas(Modifier.size(36.dp).then(visibility.modifier).testTag("voice-capsule-orb")) {
        drawCircle(Color(0x2AFFFFFF), size.minDimension / 2 - .5.dp.toPx(), style = Stroke(1.dp.toPx()))
        val barW = 3.dp.toPx()
        val step = barW + 2.dp.toPx()
        val rms = levelDb?.value ?: 0f
        // dB del micrófono (≤ 0; 0 = sin dato): de −70 a −20 dB va de quieto a la onda completa.
        val level = if (state == OrbState.LISTEN && rms.isFinite() && rms != 0f) ((rms + 70f) / 50f).coerceIn(0f, 1f) else 1f
        val period = if (state == OrbState.SPEAK) 1.6f else 1f
        repeat(5) { i ->
            val heightDp = when {
                state == OrbState.IDLE -> 5f
                reduced -> 9f
                else -> {
                    val phase = ((clock.seconds - i * .15f) / period).let { it - kotlin.math.floor(it) }
                    val wave = (1f - kotlin.math.cos(phase * 2f * kotlin.math.PI.toFloat())) / 2f
                    5f + 15f * wave * (.3f + .7f * level)
                }
            }
            val h = heightDp.dp.toPx()
            val x = center.x + (i - 2) * step - barW / 2
            drawRoundRect(ink.copy(alpha = if (state == OrbState.IDLE) .5f else 1f), topLeft = Offset(x, center.y - h / 2),
                size = androidx.compose.ui.geometry.Size(barW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
private fun CapsuleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tag: String? = null) {
    // `.vact .btn` de los registros: píldora de 38; `.secondary` sin relleno con filete #ffffff22, `.primary` en tinta.
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 38.dp).clip(shape)
            .background(if (primary) WizardColors.text.copy(alpha = if (enabled) 1f else .35f) else Color.Transparent)
            .border(1.dp, if (primary) Color.Transparent else Color(0x22FFFFFF), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val ink = if (primary) Color(0xFF111111) else WizardColors.text.copy(alpha = if (enabled) 1f else .4f)
        icon?.let { Icon(it, null, Modifier.size(13.dp).padding(end = 0.dp), tint = ink) }
        if (icon != null) Box(Modifier.width(5.dp))
        Text(text, style = CapsuleBody.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = ink)
    }
}

/** La captura ambigua: la frase, lo que se entendió (lo dudoso con borde discontinuo) y lo que falta en botones grandes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ReviewBody(
    entry: VoiceHistoryEntry,
    plannedReps: Int?,
    onDiscard: () -> Unit,
    onRegister: (Double, Int, Double) -> Unit,
    onReview: () -> Unit,
) {
    // `runReview`: la frase, lo entendido (lo que falta con borde discontinuo) y SOLO lo que falta en botones grandes.
    val values = entry.values
    var reps by remember(entry.id) { mutableStateOf(values.reps) }
    var effort by remember(entry.id) { mutableStateOf(values.intensityValue) }
    val needsReps = values.reps == null && values.seconds == null
    val needsEffort = values.intensityValue == null
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        VoicePhrase(entry.transcript, caret = false)
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VoiceTokenChip(values.weightKg?.let(::capsuleNumber) ?: "kg —", if (values.weightKg != null) "kg" else null, missing = values.weightKg == null, index = 0)
            when {
                values.reps != null -> VoiceTokenChip("× ${values.reps}", "reps", missing = false, index = 1)
                values.seconds != null -> VoiceTokenChip("${values.seconds}", "s", missing = false, index = 1)
                else -> VoiceTokenChip("× —", null, missing = true, index = 1)
            }
            VoiceTokenChip(values.intensityValue?.let { "RIR ${capsuleNumber(it)}" } ?: "RIR —", null, missing = needsEffort, index = 2)
        }
        if (needsReps) {
            CapsuleEyebrow("REPETICIONES", top = 11.dp)
            val center = plannedReps ?: 8
            CapsuleChips(((center - 2)..(center + 2)).filter { it > 0 }.map { it.toString() to it.toDouble() }, reps?.toDouble(),
                tagPrefix = "voice-capsule-reps") { reps = it.toInt() }
        }
        if (needsEffort) {
            CapsuleEyebrow("ESFUERZO REALIZADO", top = if (needsReps) 9.dp else 11.dp)
            CapsuleChips(listOf("4" to 4.0, "3" to 3.0, "2" to 2.0, "1" to 1.0, "0" to 0.0, "Fallo" to -1.0),
                effort?.let { if (it < 0) -1.0 else it }, tagPrefix = "voice-capsule-effort") { effort = it }
        }
        val weight = values.weightKg
        if (weight == null) {
            Text("Falta la carga: complétala en «Revisar».", Modifier.padding(top = 9.dp), style = CapsuleBody.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                color = LiveSheetTokens.Faint)
        }
        // `.vact`: «Descartar» y «Registrar» (o «Revisar» si falta la carga), 38 de alto y 7 entre ellos.
        Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            VoicePillButton("Descartar", onDiscard, Modifier.weight(1f), tag = "voice-capsule-discard")
            if (weight == null) {
                VoicePillButton("Revisar", onReview, Modifier.weight(1f), primary = true, tag = "voice-capsule-review")
            } else {
                val chosenReps = reps ?: values.seconds
                val chosenEffort = effort
                VoicePillButton("Registrar", {
                    if (chosenReps != null && chosenEffort != null) onRegister(weight, chosenReps, chosenEffort.coerceAtLeast(0.0))
                }, Modifier.weight(1f), primary = true, enabled = chosenReps != null && chosenEffort != null, tag = "voice-capsule-register")
            }
        }
    }
}

/** `.eyebrow` de la cápsula: Inter 600 de 9 con 1,6 de espaciado, tenue y en mayúsculas. */
@Composable
private fun CapsuleEyebrow(text: String, top: Dp = 10.dp) {
    Text(text, Modifier.padding(top = top), style = CapsuleBody.copy(fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
}

/** `.qchips`: botones que se reparten la fila (4 entre ellos), 34 de alto, radio 10, #ffffff0d y Syne 800 de 12; el elegido en tinta. */
@Composable
private fun CapsuleChips(options: List<Pair<String, Double>>, selected: Double?, tagPrefix: String? = null, onSelect: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (label, value) ->
            val on = selected == value
            val fill by androidx.compose.animation.animateColorAsState(if (on) WizardColors.text else Color(0x0DFFFFFF), tween(180),
                label = "voice-chip-fill")
            Box(
                // «Fallo» en Syne de la app es más ancho que en el prototipo: un poco más de sitio para que no se corte.
                Modifier.weight(if (label.length > 2) 1.35f else 1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(fill)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .semantics { contentDescription = label; this.selected = on }
                    .then(if (tagPrefix != null) Modifier.testTag("$tagPrefix-$label") else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = CapsuleSyne.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (on) Color(0xFF111111) else WizardColors.text)
            }
        }
    }
}

/** Los registros expandidos: la cápsula crece hasta 2/3 de la pantalla, con el filtro, cada captura y «Desactivar Modo Voz». */
@Composable
private fun VoiceRecordsPanel(
    entries: List<VoiceHistoryEntry>,
    commands: List<VoiceCommandRecord>,
    voiceEnabled: Boolean,
    context: VoiceCapsuleContext,
    notice: String?,
    height: Dp,
    onCollapse: () -> Unit,
    onTogglePause: () -> Unit,
    onDisable: () -> Unit,
    onListenExample: () -> Unit,
    onConfirmCorrection: (String, Double, Int, Double) -> Unit,
    onDiscard: (String) -> Unit,
    onReview: (VoiceHistoryEntry) -> Unit,
    onAudit: (VoiceHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reviewOnly by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val pending = entries.count { it.status == VoiceHistoryStatus.REVIEW }
    val shown = entries.filter { !reviewOnly || it.status == VoiceHistoryStatus.REVIEW }.sortedByDescending { it.createdAtEpochMs }
    Column(
        modifier.fillMaxWidth().height(height)
            .kpknFloatingSurface(PanelShape, com.example.kpkn.ui.components.KpknFloating.SheetElevation)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag > 14f) onCollapse() } }
            .semantics { paneTitle = "Registros de voz" }
            .testTag("voice-records"),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 34.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF)))
        }
        CapsuleBar(
            state = if (voiceEnabled) OrbState.LISTEN else OrbState.IDLE,
            title = if (voiceEnabled) "Te escucho" else "Escucha en pausa",
            subtitle = "${context.exerciseName} · ${context.setLabel} · ${context.captureModeLabel}",
            count = null,
            trailing = { PauseButton(voiceEnabled, onTogglePause) },
            onExpand = onCollapse,
            expandIcon = KpknLineIcons.ChevronDown,
            expandDescription = "Minimizar",
        )
        // `.vfilter`: dos píldoras de 30 de alto (radio 15, #ffffff0a; la elegida #ffffff1c en tinta); el toque ocupa 44.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(false to "Todo · ${entries.size + commands.size}", true to "Por revisar · $pending").forEach { (value, label) ->
                val on = reviewOnly == value
                Box(
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Tab) { reviewOnly = value }
                        .testTag(if (value) "voice-filter-review" else "voice-filter-all"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) Color(0x1CFFFFFF) else Color(0x0AFFFFFF))
                        .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = CapsuleBody.copy(fontSize = 11.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (on) WizardColors.text else LiveSheetTokens.Muted)
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shownCommands = if (reviewOnly) emptyList() else commands
            if (shown.isEmpty() && shownCommands.isEmpty()) item("empty") {
                Text(if (reviewOnly) "Todas las capturas están revisadas." else "Aún no hay registros de voz en esta sesión.",
                    style = CapsuleBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted, modifier = Modifier.testTag("voice-history-empty"))
            }
            val timeline = (shown.map { it.createdAtEpochMs to (it as Any) } + shownCommands.map { it.atEpochMs to (it as Any) })
                .sortedByDescending { it.first }.map { it.second }
            items(timeline, key = { (it as? VoiceHistoryEntry)?.id ?: (it as VoiceCommandRecord).id }) { item ->
                if (item is VoiceCommandRecord) VoiceCommandCard(item) else {
                val entry = item as VoiceHistoryEntry
                VoiceRecordCard(
                    entry = entry,
                    editing = editingId == entry.id,
                    onEdit = { editingId = if (editingId == entry.id) null else entry.id },
                    onCancelEdit = { editingId = null },
                    onConfirmEdit = { weight, reps, rir -> editingId = null; onConfirmCorrection(entry.id, weight, reps, rir) },
                    onDiscard = { onDiscard(entry.id) },
                    onReview = { onReview(entry) },
                    onAudit = { onAudit(entry) },
                )
                }
            }
            item("tail") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    notice?.let { Text(it, Modifier.padding(vertical = 6.dp), style = CapsuleBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Muted) }
                    LiveSheetTextAction("Escuchar frase de ejemplo", onListenExample, Modifier.testTag("voice-example"))
                }
            }
        }
        // `.btn.ghost` de 44: «Desactivar Modo Voz» en gris, sin relleno ni filete.
        Box(
            Modifier.fillMaxWidth().padding(12.dp).height(44.dp).clip(RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = onDisable).testTag("voice-disable"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Desactivar Modo Voz", style = CapsuleBody.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                color = LiveSheetTokens.Muted)
        }
    }
}

/** `.rec`: «TÚ · 38:05» con su estado, la frase, los valores con la serie vinculada y, si se corrigió, el valor anterior. */
@Composable
private fun VoiceRecordCard(
    entry: VoiceHistoryEntry,
    editing: Boolean,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onConfirmEdit: (Double, Int, Double) -> Unit,
    onDiscard: () -> Unit,
    onReview: () -> Unit,
    onAudit: () -> Unit,
) {
    val review = entry.status == VoiceHistoryStatus.REVIEW
    // `.rec`: #151515, filete #ffffff0d (melocotón #f49a6e55 si está por revisar), radio 17, relleno 11 × 12.
    val shape = RoundedCornerShape(17.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF151515))
            .border(1.dp, if (review) Color(0x55F49A6E) else Color(0x0DFFFFFF), shape)
            .padding(horizontal = 12.dp, vertical = 11.dp).testTag("voice-record-${entry.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TÚ · ${capsuleClock(entry.createdAtEpochMs)}", Modifier.weight(1f), style = CapsuleBody.copy(fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Muted)
            val badge = when {
                review -> "Por revisar"
                entry.revision > 1 -> "Corregida"
                else -> "Registrada"
            }
            // `.badge`: verde «Registrada», melocotón «Por revisar» (`.rev`) y neutra «Corregida» (`.edit`).
            val (badgeBg, badgeInk) = when {
                review -> Color(0x1FF49A6E) to Color(0xFFF49A6E)
                entry.revision > 1 -> Color(0x12FFFFFF) to LiveSheetTokens.Muted
                else -> Color(0x1F87C9A5) to Color(0xFF87C9A5)
            }
            Text(badge, Modifier.clip(RoundedCornerShape(7.dp)).background(badgeBg)
                .padding(horizontal = 7.dp, vertical = 3.dp).testTag("voice-entry-status-${entry.id}"),
                style = CapsuleBody.copy(fontSize = 9.5.sp, letterSpacing = .8.sp, fontWeight = FontWeight.SemiBold), color = badgeInk)
        }
        Text("«${entry.transcript}»", Modifier.padding(top = 6.dp), style = CapsuleBody.copy(fontSize = 12.sp, lineHeight = 16.sp), color = LiveSheetTokens.Muted)
        // `.rec-v`: las cifras en Syne 800 de 18 y las unidades en Inter 10 tenue; la serie vinculada a la derecha en 11 gris.
        Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.Bottom) {
            Text(capsuleValuesStyled(entry.values), Modifier.weight(1f).testTag("voice-entry-values-${entry.id}"),
                style = CapsuleSyne.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold), color = WizardColors.text)
            Text("${entry.exerciseName} · serie ${entry.setIdx + 1}", style = CapsuleBody.copy(fontSize = 11.sp, lineHeight = 14.sp), color = LiveSheetTokens.Muted,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        entry.audit?.let { audit ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Antes: ", style = CapsuleBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
                Text(capsuleValues(audit.previous), style = CapsuleBody.copy(fontSize = 10.sp, textDecoration = TextDecoration.LineThrough),
                    color = LiveSheetTokens.Faint)
                Text(" · misma serie, sin duplicar", Modifier.weight(1f), style = CapsuleBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
                Text("Ver cambios", Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onAudit).padding(horizontal = 4.dp, vertical = 8.dp)
                    .testTag("voice-entry-audit-${entry.id}"), style = CapsuleBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Muted)
            }
        }
        when {
            review -> Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsuleButton("Descartar", onDiscard, Modifier.weight(1f), tag = "voice-entry-discard-${entry.id}")
                CapsuleButton("Revisar", onReview, Modifier.weight(1f), primary = true, tag = "voice-entry-review-${entry.id}")
            }
            editing -> VoiceInlineEdit(entry, onCancelEdit, onConfirmEdit)
            else -> Text("Editar", Modifier.align(Alignment.End).heightIn(min = 36.dp).clickable(role = Role.Button, onClick = onEdit)
                .padding(horizontal = 6.dp, vertical = 10.dp).testTag("voice-entry-edit-${entry.id}"),
                style = CapsuleBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
        }
    }
}

/** `.rec` de un comando: «TÚ · 38:24» con «Comando», la frase y lo aplicado («+15 s descanso · Aplicado»). */
@Composable
private fun VoiceCommandCard(record: VoiceCommandRecord) {
    val shape = RoundedCornerShape(17.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF151515)).border(1.dp, Color(0x0DFFFFFF), shape)
        .padding(horizontal = 12.dp, vertical = 11.dp).testTag("voice-command-${record.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TÚ · ${capsuleClock(record.atEpochMs)}", Modifier.weight(1f), style = CapsuleBody.copy(fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Muted)
            Text("Comando", Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0x12FFFFFF)).padding(horizontal = 7.dp, vertical = 3.dp),
                style = CapsuleBody.copy(fontSize = 9.5.sp, letterSpacing = .8.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Muted)
        }
        if (record.transcript.isNotBlank()) {
            Text("«${record.transcript}»", Modifier.padding(top = 6.dp), style = CapsuleBody.copy(fontSize = 12.sp, lineHeight = 16.sp), color = LiveSheetTokens.Muted)
        }
        Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.Bottom) {
            Text(record.value, style = CapsuleSyne.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold), color = WizardColors.text)
            Text(" ${record.detail}", Modifier.weight(1f), style = CapsuleBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
            Text(record.link, style = CapsuleBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
        }
    }
}

/** `v-edit`: el borrador dentro del mismo registro; repeticiones y esfuerzo con chips; «Cancelar» no toca la captura. */
@Composable
private fun ColumnScope.VoiceInlineEdit(entry: VoiceHistoryEntry, onCancel: () -> Unit, onConfirm: (Double, Int, Double) -> Unit) {
    var reps by remember(entry.id) { mutableStateOf(entry.values.reps) }
    var effort by remember(entry.id) { mutableStateOf(entry.values.intensityValue) }
    val weight = entry.values.weightKg
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0DFFFFFF)))
        CapsuleEyebrow("REPETICIONES")
        val center = entry.values.reps ?: 8
        CapsuleChips(((center - 3)..(center + 2)).filter { it > 0 }.map { it.toString() to it.toDouble() }, reps?.toDouble()) { reps = it.toInt() }
        CapsuleEyebrow("ESFUERZO REALIZADO")
        CapsuleChips(listOf("4" to 4.0, "3" to 3.0, "2" to 2.0, "1" to 1.0, "0" to 0.0, "Fallo" to -1.0), effort) { effort = it }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CapsuleButton("Cancelar", onCancel, Modifier.weight(1f))
            val chosenReps = reps
            val chosenEffort = effort
            CapsuleButton("Confirmar", {
                if (weight != null && chosenReps != null && chosenEffort != null) onConfirm(weight, chosenReps, chosenEffort.coerceAtLeast(0.0))
            }, Modifier.weight(1f), primary = true, icon = KpknLineIcons.Check,
                enabled = weight != null && chosenReps != null && chosenEffort != null, tag = "voice-entry-confirm-${entry.id}")
        }
        Text("Cancelar no cambia la captura original. Confirmar actualiza la serie ${entry.setIdx + 1}; no crea otra.",
            Modifier.padding(top = 8.dp), style = CapsuleBody.copy(fontSize = 10.sp, lineHeight = 14.sp), color = LiveSheetTokens.Faint)
    }
}

private fun capsuleValues(values: VoiceHistoryValues): String {
    val weight = values.weightKg?.let { "${capsuleNumber(it)} kg" } ?: "— kg"
    val metric = when {
        values.reps != null -> "${values.reps}"
        values.seconds != null -> "${values.seconds} s"
        values.distanceKm != null -> "${capsuleNumber(values.distanceKm)} km"
        else -> "—"
    }
    val effort = values.intensityValue?.let { " · RIR ${capsuleNumber(it)}" } ?: " · falta RIR"
    return "$weight × $metric$effort"
}

/** `.rec-v` del prototipo: «<b>100</b> kg × <b>8</b> · RIR 2»; lo que falta, «falta RIR» en tenue. */
private fun capsuleValuesStyled(values: VoiceHistoryValues): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val small = androidx.compose.ui.text.SpanStyle(fontFamily = WizardFonts.body, fontSize = 10.sp, fontWeight = FontWeight.Normal,
            color = LiveSheetTokens.Faint)
        append(values.weightKg?.let(::capsuleNumber) ?: "—")
        withStyle(small) { append(" kg × ") }
        when {
            values.reps != null -> append("${values.reps}")
            values.seconds != null -> { append("${values.seconds}"); withStyle(small) { append(" s") } }
            values.distanceKm != null -> { append(capsuleNumber(values.distanceKm)); withStyle(small) { append(" km") } }
            else -> append("—")
        }
        withStyle(small) {
            append(values.intensityValue?.let { " · RIR ${capsuleNumber(it)}" } ?: " · falta RIR")
        }
    }

private fun capsuleNumber(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().replace('.', ',')
}

private fun capsuleClock(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(capsuleTimeFormat)
