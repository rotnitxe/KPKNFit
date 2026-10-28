package com.example.kpkn.screens.workout.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
)

private val CapsuleShape = RoundedCornerShape(28.dp)
private val CapsuleGlass = Brush.linearGradient(listOf(Color(0xEB2A2A2A), Color(0xF2161616)))
private val CapsuleBorder = Color(0x21FFFFFF)
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
) {
    var receiptId by remember { mutableStateOf<String?>(null) }
    var commandReceiptId by remember { mutableStateOf<String?>(null) }
    // `flyTokens` del prototipo: los valores entendidos salen de la cápsula y vuelan a su campo (la tarjeta, o el reloj del descanso).
    var flying by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var dismissedReviewId by remember { mutableStateOf<String?>(null) }
    var correcting by remember { mutableStateOf<VoiceHistoryEntry?>(null) }
    var auditing by remember { mutableStateOf<VoiceHistoryEntry?>(null) }
    val newest = entries.maxByOrNull { it.updatedAtEpochMs }
    LaunchedEffect(newest?.id, newest?.updatedAtEpochMs, newest?.status) {
        val entry = newest ?: return@LaunchedEffect
        if (entry.status == VoiceHistoryStatus.REGISTERED && nowMs() - entry.updatedAtEpochMs < FRESH_MS) {
            receiptId = entry.id
            flying = entry.id to listOfNotNull(
                entry.values.weightKg?.let { "${capsuleNumber(it)} kg" },
                entry.values.reps?.let { "× $it" },
                entry.values.intensityValue?.let { "RIR ${capsuleNumber(it)}" },
            )
            delay(RECEIPT_MS)
            if (receiptId == entry.id) receiptId = null
        }
    }
    val newestCommand = commands.maxByOrNull { it.atEpochMs }
    LaunchedEffect(newestCommand?.id) {
        val command = newestCommand ?: return@LaunchedEffect
        if (nowMs() - command.atEpochMs < FRESH_MS) {
            commandReceiptId = command.id
            flying = command.id to listOf(command.value)
            delay(COMMAND_RECEIPT_MS)
            if (commandReceiptId == command.id) commandReceiptId = null
        }
    }
    val commandReceipt = commands.firstOrNull { it.id == commandReceiptId }
    val total = entries.size + commands.size
    val receipt = entries.firstOrNull { it.id == receiptId && it.status == VoiceHistoryStatus.REGISTERED }
    val review = entries.lastOrNull { it.status == VoiceHistoryStatus.REVIEW && it.id != dismissedReviewId &&
        nowMs() - it.createdAtEpochMs < FRESH_MS * 15 }

    Box(modifier.fillMaxSize().zIndex(30f)) {
        if (expanded) {
            BackHandler { onExpandedChange(false) }
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onExpandedChange(false) },
            )
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
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = bottomPadding).fillMaxWidth()
                    .shadow(18.dp, CapsuleShape, ambientColor = Color(0x99000000), spotColor = Color(0x99000000))
                    .clip(CapsuleShape).background(CapsuleGlass).border(1.dp, CapsuleBorder, CapsuleShape)
                    .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -12f) onExpandedChange(true) } }
                    .animateContentSize()
                    .semantics { paneTitle = "Modo Voz" }
                    .testTag("voice-capsule"),
            ) {
                when {
                    guideSpeech != null -> CapsuleBar(
                        state = OrbState.SPEAK, title = "KPKN habla", subtitle = "«$guideSpeech»", count = total,
                        trailing = {
                            Row(
                                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button, onClick = onStopGuide).padding(horizontal = 12.dp).testTag("voice-capsule-stop-guide"),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(WizardColors.text))
                                Text("Detener", style = CapsuleBody.copy(fontSize = 12.sp), color = WizardColors.text)
                            }
                        },
                        onExpand = { onExpandedChange(true) },
                    )
                    review != null -> {
                        CapsuleBar(state = OrbState.LISTEN, title = "Por revisar · no se registró",
                            subtitle = "${review.exerciseName} · serie ${review.setIdx + 1} · faltan datos claros", count = total,
                            trailing = { PauseButton(voiceEnabled, onTogglePause) }, onExpand = { onExpandedChange(true) })
                        ReviewBody(
                            entry = review,
                            onDiscard = { dismissedReviewId = review.id; onDiscard(review.id) },
                            onRegister = { weight, reps, rir -> dismissedReviewId = review.id; onConfirmCorrection(review.id, weight, reps, rir) },
                            onReview = { correcting = review },
                        )
                    }
                    receipt != null -> {
                        CapsuleBar(state = OrbState.LISTEN, title = "Serie ${receipt.setIdx + 1} registrada",
                            subtitle = "${capsuleValues(receipt.values)} · por voz", count = total,
                            trailing = { PauseButton(voiceEnabled, onTogglePause) }, onExpand = { onExpandedChange(true) })
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CapsuleButton("Deshacer", { receiptId = null; onUndo(receipt.id) }, Modifier.weight(1f), tag = "voice-capsule-undo")
                            CapsuleButton("Editar", { receiptId = null; correcting = receipt }, Modifier.weight(1f), icon = KpknLineIcons.Edit,
                                tag = "voice-capsule-edit")
                        }
                    }
                    commandReceipt != null -> CapsuleBar(state = OrbState.LISTEN,
                        title = if (commandReceipt.detail == "descanso" && commandReceipt.value.first() in "+−") "Descanso ${commandReceipt.value}"
                            else commandReceipt.value,
                        subtitle = "Comando aplicado", count = total,
                        trailing = { PauseButton(voiceEnabled, onTogglePause) }, onExpand = { onExpandedChange(true) })
                    !voiceEnabled -> CapsuleBar(state = OrbState.IDLE, title = "Escucha en pausa",
                        subtitle = "Los registros se conservan · toca para seguir", count = total,
                        trailing = { PauseButton(false, onTogglePause) }, onExpand = { onExpandedChange(true) })
                    else -> CapsuleBar(state = OrbState.LISTEN, title = "Te escucho",
                        subtitle = when {
                            context.capturing -> "Captura · di «Iniciar vídeo» o «Detener vídeo»"
                            context.resting -> "Descanso · di «más quince» o «terminar descanso»"
                            else -> "${context.exerciseName} · ${context.setLabel} · di peso, reps y esfuerzo"
                        },
                        count = total, trailing = { PauseButton(true, onTogglePause) }, onExpand = { onExpandedChange(true) })
                }
            }
        }
        flying?.let { (flightId, tokens) ->
            if (!expanded && tokens.isNotEmpty()) {
                key(flightId) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val startY = maxHeight - bottomPadding - 64.dp
                        val endY = maxHeight * if (context.resting) .3f else .38f
                        tokens.forEachIndexed { index, token ->
                            FlyingToken(token, index, tokens.size, maxWidth, startY, endY, onDone = {
                                if (index == tokens.lastIndex && flying?.first == flightId) flying = null
                            })
                        }
                    }
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

/**
 * `.fly`: una píldora clara con el valor que sale de la cápsula y vuela a su campo, se encoge a 0,7 y se desvanece en 0,7 s
 * (escalonadas 140 ms). Con movimiento reducido no vuela.
 */
@Composable
private fun FlyingToken(text: String, index: Int, count: Int, width: Dp, startY: Dp, endY: Dp, onDone: () -> Unit) {
    val reduced = wizardReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(60L + index * 140L)
            progress.animateTo(1f, tween(700, easing = CubicBezierEasing(.5f, 0f, .2f, 1f)))
        }
        onDone()
    }
    if (reduced) return
    val t = progress.value
    // Salen juntas de la cápsula y llegan repartidas sobre la tarjeta: carga a la izquierda, repeticiones al centro, esfuerzo a la derecha.
    val startX = width / 2 - 60.dp + (index * 44).dp
    val endX = if (count == 1) width / 2 - 30.dp else width * (.2f + .3f * index) - 30.dp
    Box(
        Modifier.offset(x = startX + (endX - startX) * t, y = startY + (endY - startY) * t)
            .graphicsLayer { val sc = 1f - .3f * t; scaleX = sc; scaleY = sc; alpha = 1f - t }
            .height(30.dp).clip(RoundedCornerShape(11.dp)).background(WizardColors.text).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CapsuleSyne.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold), color = Color(0xFF111111))
    }
}

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
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoiceOrb(state)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = CapsuleBody.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                color = WizardColors.text, modifier = Modifier.testTag("voice-capsule-title"))
            Text(subtitle, Modifier.padding(top = 2.dp), style = CapsuleBody.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = LiveSheetTokens.Faint)
        }
        if (count != null && count > 0) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0x14FFFFFF)).semantics { contentDescription = "$count registros de voz" },
                contentAlignment = Alignment.Center) {
                Text("$count", style = CapsuleBody.copy(fontSize = 10.sp), color = WizardColors.text)
            }
        }
        trailing()
        if (onExpand != null) {
            Box(Modifier.size(width = 36.dp, height = 44.dp).clickable(role = Role.Button, onClick = onExpand)
                .semantics { contentDescription = expandDescription }.testTag("voice-capsule-expand"), contentAlignment = Alignment.Center) {
                Icon(expandIcon, null, Modifier.size(16.dp), tint = WizardColors.text)
            }
        }
    }
}

@Composable
private fun PauseButton(listening: Boolean, onToggle: () -> Unit) {
    Box(Modifier.size(width = 40.dp, height = 44.dp).clickable(role = Role.Button, onClick = onToggle)
        .semantics { contentDescription = if (listening) "Pausar escucha" else "Reanudar escucha" }.testTag("voice-capsule-pause"),
        contentAlignment = Alignment.Center) {
        Icon(if (listening) KpknLineIcons.Pause else KpknLineIcons.Play, null, Modifier.size(16.dp), tint = WizardColors.text)
    }
}

/** `.orb`: cinco barras que respiran mientras escucha; quietas y tenues en pausa; más vivas cuando KPKN habla. */
@Composable
private fun VoiceOrb(state: OrbState) {
    val reduced = wizardReducedMotion()
    val visibility = rememberFeedbackAnimationVisibility()
    val clock = rememberRestClock(state != OrbState.IDLE && visibility.visible && !reduced)
    val ink = WizardColors.text
    Canvas(Modifier.size(34.dp).then(visibility.modifier)) {
        drawCircle(Color(0x12FFFFFF), size.minDimension / 2)
        val gap = 4.dp.toPx()
        repeat(5) { i ->
            val base = listOf(.45f, .75f, 1f, .75f, .45f)[i]
            val wave = when {
                state == OrbState.IDLE || reduced -> .35f
                state == OrbState.SPEAK -> .55f + .45f * kotlin.math.sin(clock.seconds * 9f + i)
                else -> .45f + .35f * kotlin.math.sin(clock.seconds * 5f + i * .9f)
            }
            val h = (14.dp.toPx() * base * wave).coerceAtLeast(3.dp.toPx())
            val x = center.x + (i - 2) * gap
            drawLine(ink.copy(alpha = if (state == OrbState.IDLE) .45f else 1f), Offset(x, center.y - h / 2), Offset(x, center.y + h / 2),
                2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun CapsuleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tag: String? = null) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier.heightIn(min = 40.dp).clip(shape)
            .background(if (primary) WizardColors.text.copy(alpha = if (enabled) 1f else .35f) else Color(0x10FFFFFF))
            .border(1.dp, if (primary) Color.Transparent else Color(0x1FFFFFFF), shape)
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
    onDiscard: () -> Unit,
    onRegister: (Double, Int, Double) -> Unit,
    onReview: () -> Unit,
) {
    val values = entry.values
    var reps by remember(entry.id) { mutableStateOf(values.reps) }
    var effort by remember(entry.id) { mutableStateOf(values.intensityValue) }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
        Text("«${entry.transcript}»", style = CapsuleBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = WizardColors.text)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CapsuleToken(values.weightKg?.let { "${capsuleNumber(it)} kg" } ?: "kg —", missing = values.weightKg == null)
            CapsuleToken(values.reps?.let { "× $it" } ?: "× —", missing = values.reps == null)
            CapsuleToken(values.intensityValue?.let { "RIR ${capsuleNumber(it)}" } ?: "RIR —", missing = values.intensityValue == null)
        }
        CapsuleEyebrow("REPETICIONES")
        val center = values.reps ?: 8
        CapsuleChips(((center - 2)..(center + 2)).filter { it > 0 }.map { it.toString() to it.toDouble() }, reps?.toDouble()) { reps = it.toInt() }
        CapsuleEyebrow("ESFUERZO REALIZADO")
        CapsuleChips(listOf("4" to 4.0, "3" to 3.0, "2" to 2.0, "1" to 1.0, "0" to 0.0, "Fallo" to -1.0),
            effort?.let { if (it < 0) -1.0 else it }) { effort = it }
        val weight = values.weightKg
        if (weight == null) {
            Text("Falta la carga: complétala en «Revisar».", Modifier.padding(top = 8.dp), style = CapsuleBody.copy(fontSize = 10.sp),
                color = LiveSheetTokens.Faint)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CapsuleButton("Descartar", onDiscard, Modifier.weight(1f), tag = "voice-capsule-discard")
            if (weight == null) {
                CapsuleButton("Revisar", onReview, Modifier.weight(1f), primary = true, tag = "voice-capsule-review")
            } else {
                val chosenReps = reps
                val chosenEffort = effort
                CapsuleButton("Registrar", {
                    if (chosenReps != null && chosenEffort != null) onRegister(weight, chosenReps, chosenEffort.coerceAtLeast(0.0))
                }, Modifier.weight(1f), primary = true, enabled = chosenReps != null && chosenEffort != null, tag = "voice-capsule-register")
            }
        }
    }
}

@Composable
private fun CapsuleToken(text: String, missing: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.clip(shape).background(Color(0x0FFFFFFF))
            .then(if (missing) Modifier.border(1.dp, Color(0x55FFFFFF), shape) else Modifier)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(text, style = CapsuleSyne.copy(fontSize = 14.sp, lineHeight = 19.sp), color = if (missing) LiveSheetTokens.Muted else WizardColors.text)
    }
}

@Composable
private fun CapsuleEyebrow(text: String) {
    Text(text, Modifier.padding(top = 10.dp, bottom = 6.dp), style = CapsuleBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapsuleChips(options: List<Pair<String, Double>>, selected: Double?, onSelect: (Double) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) ->
            val on = selected == value
            Box(
                Modifier.heightIn(min = 40.dp).width(if (label.length > 2) 64.dp else 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) WizardColors.text else Color(0x10FFFFFF))
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = CapsuleBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = if (on) Color(0xFF111111) else WizardColors.text)
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
            .shadow(22.dp, CapsuleShape, ambientColor = Color(0xAA000000), spotColor = Color(0xAA000000))
            .clip(CapsuleShape).background(CapsuleGlass).border(1.dp, CapsuleBorder, CapsuleShape)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "Todo · ${entries.size + commands.size}", true to "Por revisar · $pending").forEach { (value, label) ->
                val on = reviewOnly == value
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) Color(0x1AFFFFFF) else Color.Transparent)
                        .border(1.dp, if (on) Color(0x24FFFFFF) else Color(0x10FFFFFF), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { reviewOnly = value }
                        .testTag(if (value) "voice-filter-review" else "voice-filter-all"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = CapsuleBody.copy(fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) WizardColors.text else LiveSheetTokens.Muted)
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
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            CapsuleButton("Desactivar Modo Voz", onDisable, Modifier.fillMaxWidth(), tag = "voice-disable")
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0x0AFFFFFF))
            .border(1.dp, if (review) Color(0x40FFFFFF) else Color(0x10FFFFFF), shape)
            .padding(12.dp).testTag("voice-record-${entry.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TÚ · ${capsuleClock(entry.createdAtEpochMs)}", Modifier.weight(1f), style = CapsuleBody.copy(fontSize = 9.sp, letterSpacing = .8.sp,
                fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
            val badge = when {
                review -> "Por revisar"
                entry.revision > 1 -> "Corregida"
                else -> "Registrada"
            }
            Text(badge, Modifier.clip(RoundedCornerShape(8.dp)).background(if (review) Color(0x26FFFFFF) else Color(0x10FFFFFF))
                .padding(horizontal = 7.dp, vertical = 3.dp).testTag("voice-entry-status-${entry.id}"),
                style = CapsuleBody.copy(fontSize = 9.sp), color = WizardColors.text)
        }
        Text("«${entry.transcript}»", Modifier.padding(top = 8.dp), style = CapsuleBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = WizardColors.text)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(capsuleValues(entry.values), Modifier.weight(1f).testTag("voice-entry-values-${entry.id}"),
                style = CapsuleSyne.copy(fontSize = 15.sp, lineHeight = 20.sp), color = WizardColors.text)
            Text("${entry.exerciseName} · serie ${entry.setIdx + 1}", style = CapsuleBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
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
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color(0x0AFFFFFF)).border(1.dp, Color(0x10FFFFFF), shape).padding(12.dp)
        .testTag("voice-command-${record.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TÚ · ${capsuleClock(record.atEpochMs)}", Modifier.weight(1f), style = CapsuleBody.copy(fontSize = 9.sp, letterSpacing = .8.sp,
                fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
            Text("Comando", Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x10FFFFFF)).padding(horizontal = 7.dp, vertical = 3.dp),
                style = CapsuleBody.copy(fontSize = 9.sp), color = WizardColors.text)
        }
        if (record.transcript.isNotBlank()) {
            Text("«${record.transcript}»", Modifier.padding(top = 8.dp), style = CapsuleBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = WizardColors.text)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(record.value, style = CapsuleSyne.copy(fontSize = 15.sp, lineHeight = 20.sp), color = WizardColors.text)
            Text(" ${record.detail}", Modifier.weight(1f), style = CapsuleBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
            Text(record.link, style = CapsuleBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
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

private fun capsuleNumber(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().replace('.', ',')
}

private fun capsuleClock(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(capsuleTimeFormat)
