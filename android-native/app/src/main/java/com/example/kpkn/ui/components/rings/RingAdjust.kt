package com.example.kpkn.ui.components.rings

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * El ajuste manual OFICIAL de los anillos (LV10b, prototipos G5 y G6): el mismo en el estado previo, en Inicio y, con estos
 * componentes, en el resumen final. No guardan nada: reciben los valores visibles (0–100) y devuelven los cambios por callback; la
 * pantalla que los monta decide qué guarda (y la Columna se sigue invirtiendo SOLO donde ya se invierte hoy, al guardar).
 *
 *  - [EnergyColumnAdjust]: Energía y Columna, un anillo grande con «líquido» que sube hasta el valor.
 *  - [MuscleBodyAdjust] (en `RingAdjustBody.kt`): los músculos sobre un cuerpo de frente y de espalda.
 *  - [RingAdjustSheet]: una hoja con las tres pestañas, abierta en el canal pedido.
 */

/** Lado del dial de Energía y Columna (el prototipo lo pinta a 280). */
private val RingAdjustDialSize = 280.dp

/** Curva de las transiciones del dial (la del prototipo: cubic-bezier(.2, .8, .2, 1)). */
private val RingAdjustEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private val DiscColor = Color(0xFF111111)
private val TrackColor = Color(0xFF1A1A1A)

/** Fondo de la hoja de ajuste (el de los prototipos): opaco, para que la hoja de debajo no se transparente por el vidrio. */
private val SheetFill = Color(0xFF0E0E0E)

/** Sombra suave de los textos del centro: se leen igual sobre el líquido que sobre el disco vacío. */
private val CenterTextShadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1.5f), blurRadius = 8f)

/**
 * Lo que sabe la hoja de un canal con un solo valor: el [value] que se ve, el [before] (el automático o el de partida, que sale como
 * «antes») y si la persona lo ha tocado ([edited], que activa «Restablecer»; por defecto, que difiera de [before]).
 */
@Immutable
data class RingAdjustLevel(
    val value: Int,
    val before: Int,
    val edited: Boolean = value != before,
)

/**
 * Los músculos de la hoja: [values] y [before] por músculo (la clave que use quien monta los componentes: el pilar del motor u otro
 * nombre que se reduzca a un pilar), los [highlighted] (los de hoy, que van marcados) y los [edited] (los que la persona ha tocado;
 * por defecto, los que difieren de [before]).
 */
@Immutable
data class RingAdjustMuscles(
    val values: Map<String, Int> = emptyMap(),
    val before: Map<String, Int> = emptyMap(),
    val highlighted: Set<String> = emptySet(),
    val edited: Set<String> = values.filter { (id, value) -> before[id]?.let { it != value } ?: false }.keys,
)

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Energía y Columna
// ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Ajuste de Energía o Columna (G5): un anillo grande de [size] (se adapta si no cabe) con el arco luminoso del canal y, dentro, un
 * «líquido» del color del canal que sube hasta el valor con dos ondas que se desplazan. Dentro van el nombre del canal, el valor
 * grande («72 %») y «antes 88 %»; una marca tenue en el anillo señala el [before].
 *
 * Tocar o arrastrar alrededor del anillo fija el valor (paso de 1; suena la háptica cada 5); − y + lo mueven de 5 en 5; debajo va la
 * palabra del tramo («Con mucha energía», «Espalda descansada»…). Con [onReset] sale «Restablecer», activo mientras el valor
 * difiera de [before]. Para TalkBack el anillo es un deslizador (`setProgress`). Cada cambio llega a [onValueChange] ya recortado a
 * 0–100. Con «reducir movimiento» el líquido va quieto; sin la app en primer plano no corre ningún bucle.
 *
 * Valores en la escala VISIBLE: la Columna invertida es asunto de quien guarda, no de este componente.
 */
@Composable
fun EnergyColumnAdjust(
    channel: RingChannel,
    value: Int,
    before: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = RingAdjustDialSize,
    onReset: (() -> Unit)? = null,
) {
    require(channel != RingChannel.MUSCULOS) { "EnergyColumnAdjust solo ajusta Energía y Columna; los músculos van en MuscleBodyAdjust" }
    val shown = ringAdjustClamp(value)
    val start = ringAdjustClamp(before)
    val (dark, light) = ringAdjustGradient(channel)
    val reduced = wizardReducedMotion()
    val foreground = rememberRingAdjustForeground()
    var visible by remember { mutableStateOf(false) }
    val clock = rememberRingAdjustClock(running = foreground && visible && !reduced)
    var dragging by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(
        targetValue = shown.toFloat(),
        animationSpec = if (reduced) {
            snap<Float>()
        } else {
            tween<Float>(durationMillis = if (dragging) 90 else 450, easing = RingAdjustEase)
        },
        label = "ringAdjustValue",
    )
    val haptics = LocalHapticFeedback.current
    // Último valor entregado: entre dos toques seguidos la pantalla aún puede no haber recompuesto con el nuevo.
    val lastEmitted = remember { IntArray(1) }
    SideEffect { lastEmitted[0] = shown }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val emit: (Int) -> Unit = { next ->
        val clamped = ringAdjustClamp(next)
        val previous = lastEmitted[0]
        if (clamped != previous) {
            if (ringAdjustCrossedTick(previous, clamped)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastEmitted[0] = clamped
            currentOnValueChange(clamped)
        }
    }
    val name = ringAdjustChannelName(channel)

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val dial = minOf(size, maxWidth)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RingAdjustDial(
                dial = dial,
                name = name,
                value = shown,
                before = start,
                animated = animated,
                dark = dark,
                light = light,
                seconds = clock,
                onValue = emit,
                onDragging = { dragging = it },
                onVisible = { visible = it },
            )
            Text(
                text = "Toca o arrastra por el anillo",
                style = KpknType.caption,
                color = WizardColors.textFaint,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.width(dial),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknIconButton(
                    icon = KpknLineIcons.Minus,
                    contentDescription = "Bajar $RingAdjustDialStep",
                    onClick = { emit(ringAdjustStep(shown, -RingAdjustDialStep)) },
                    modifier = Modifier.testTag("ring-adjust-minus"),
                )
                Text(
                    text = ringAdjustWord(channel, shown),
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ring-adjust-word"),
                )
                KpknIconButton(
                    icon = KpknLineIcons.Plus,
                    contentDescription = "Subir $RingAdjustDialStep",
                    onClick = { emit(ringAdjustStep(shown, RingAdjustDialStep)) },
                    modifier = Modifier.testTag("ring-adjust-plus"),
                )
            }
            if (onReset != null) {
                KpknCompactPill(
                    text = "Restablecer",
                    onClick = onReset,
                    filled = false,
                    height = 40.dp,
                    enabled = shown != start,
                    modifier = Modifier.testTag("ring-adjust-reset"),
                )
            }
        }
    }
}

/** El dial: dibujo del anillo, del líquido y de la marca de «antes», los textos del centro y los gestos. */
@Composable
private fun RingAdjustDial(
    dial: Dp,
    name: String,
    value: Int,
    before: Int,
    animated: Float,
    dark: Color,
    light: Color,
    seconds: State<Float>,
    onValue: (Int) -> Unit,
    onDragging: (Boolean) -> Unit,
    onVisible: (Boolean) -> Unit,
) {
    val discPath = remember { Path() }
    val waveBack = remember { Path() }
    val waveFront = remember { Path() }
    val currentOnValue by rememberUpdatedState(onValue)
    val currentOnDragging by rememberUpdatedState(onDragging)
    Box(
        modifier = Modifier
            .size(dial)
            .testTag("ring-adjust-ring")
            .ringAdjustVisible(onVisible)
            .semantics {
                contentDescription = "Ajuste de $name"
                stateDescription = ringAdjustPercent(value)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = 0f..RingAdjustMax.toFloat(),
                    steps = RingAdjustMax - 1,
                )
                setProgress { target ->
                    currentOnValue(target.roundToInt())
                    true
                }
            }
            .ringDialGestures(
                onValue = { currentOnValue(it) },
                onDragging = { currentOnDragging(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRingAdjustDial(
                animated = animated,
                before = before,
                dark = dark,
                light = light,
                seconds = seconds.value,
                disc = discPath,
                waveBack = waveBack,
                waveFront = waveFront,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name.uppercase(Locale.forLanguageTag("es")),
                style = KpknType.eyebrow.copy(shadow = CenterTextShadow),
                color = light,
                maxLines = 1,
            )
            Text(
                text = ringAdjustPercent(value),
                style = KpknType.metricXL.copy(fontSize = 46.sp, lineHeight = 50.sp, shadow = CenterTextShadow),
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.testTag("ring-adjust-value"),
            )
            Text(
                text = "antes ${ringAdjustPercent(before)}",
                style = KpknType.caption.copy(shadow = CenterTextShadow),
                color = WizardColors.textMuted,
                maxLines = 1,
                modifier = Modifier.testTag("ring-adjust-before"),
            )
        }
    }
}

/**
 * Gestos del dial: un toque sobre el anillo fija el valor de su ángulo y arrastrar lo sigue. Solo empiezan sobre la banda del
 * anillo (el centro y las esquinas no): fuera de ella el toque sigue su camino (por ejemplo, desplazar la hoja). Al dar la vuelta por
 * arriba el valor se queda en el tope ([ringAdjustDragValue]).
 */
private fun Modifier.ringDialGestures(
    onValue: (Int) -> Unit,
    onDragging: (Boolean) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val half = minOf(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val first = down.position - center
        if (!ringAdjustIsOnBand(first.x, first.y, half)) return@awaitEachGesture
        var last = ringAdjustValueAt(first.x, first.y)
        onDragging(true)
        try {
            onValue(last)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (change.positionChanged()) {
                    val offset = change.position - center
                    // Muy cerca del centro el ángulo no significa nada: se ignora hasta que el dedo vuelva a la banda.
                    if (ringAdjustIsOnBand(offset.x, offset.y, half)) {
                        last = ringAdjustDragValue(last, ringAdjustValueAt(offset.x, offset.y))
                        onValue(last)
                    }
                    change.consume()
                }
            }
        } finally {
            onDragging(false)
        }
    }
}

private fun DrawScope.drawRingAdjustDial(
    animated: Float,
    before: Int,
    dark: Color,
    light: Color,
    seconds: Float,
    disc: Path,
    waveBack: Path,
    waveFront: Path,
) {
    val side = size.minDimension
    val unit = side / 280f
    val center = Offset(side / 2f, side / 2f)
    val discRadius = 96f * unit
    val trackRadius = 118f * unit
    val stroke = 18f * unit
    val fraction = (animated / RingAdjustMax).coerceIn(0f, 1f)
    val diagonal = Brush.linearGradient(listOf(dark, light), start = Offset.Zero, end = Offset(side, side))

    // El disco y el líquido, recortado al círculo.
    drawCircle(DiscColor, radius = discRadius, center = center)
    disc.reset()
    disc.addOval(Rect(center, discRadius))
    clipPath(disc) {
        val amplitude = 5f * unit
        val wavelength = 120f * unit
        // La superficie sube del fondo (0 %) a por encima del borde (100 %) para que el disco quede lleno de verdad.
        val level = center.y + discRadius + amplitude - (2f * discRadius + 2f * amplitude) * fraction
        val left = center.x - discRadius - wavelength
        val right = center.x + discRadius + wavelength
        val bottom = center.y + discRadius + 2f * amplitude
        waveBack.buildWave(left, right, bottom, level, amplitude, wavelength, phase = -seconds / 3f * wavelength, step = 4f * unit)
        drawPath(waveBack, color = dark.copy(alpha = 0.35f))
        waveFront.buildWave(left, right, bottom, level + 6f * unit, amplitude, wavelength, phase = seconds / 4.6f * wavelength, step = 4f * unit)
        drawPath(waveFront, brush = diagonal, alpha = 0.55f)
    }

    // La pista y el arco: halo suave detrás, degradado y extremos redondeados.
    drawCircle(TrackColor, radius = trackRadius, center = center, style = Stroke(width = stroke))
    if (fraction > 0f) {
        val topLeft = Offset(center.x - trackRadius, center.y - trackRadius)
        val arcSize = Size(trackRadius * 2f, trackRadius * 2f)
        val sweep = 360f * fraction
        for ((extra, alpha) in Halo) {
            drawArc(
                color = light.copy(alpha = alpha),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke + extra * unit, cap = StrokeCap.Round),
            )
        }
        drawArc(
            brush = diagonal,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }

    // La marca tenue del «antes»: una raya corta que cruza el anillo en su ángulo.
    val angle = Math.toRadians(ringAdjustAngleDegrees(before).toDouble())
    val sinA = sin(angle).toFloat()
    val cosA = cos(angle).toFloat()
    drawLine(
        color = WizardColors.text.copy(alpha = 0.5f),
        start = Offset(center.x + 104f * unit * sinA, center.y - 104f * unit * cosA),
        end = Offset(center.x + 132f * unit * sinA, center.y - 132f * unit * cosA),
        strokeWidth = 3f * unit,
        cap = StrokeCap.Round,
    )
}

/** Capas del halo del arco: cuánto más ancha que el arco y con qué opacidad (todas del color claro del canal). */
private val Halo = listOf(20f to 0.05f, 13f to 0.07f, 7f to 0.10f, 3f to 0.14f)

/** Una ola: el borde superior es una senoide de [amplitude] y [wavelength] desplazada [phase]; por debajo, relleno hasta [bottom]. */
private fun Path.buildWave(
    left: Float,
    right: Float,
    bottom: Float,
    level: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
    step: Float,
) {
    val twoPi = 2f * PI.toFloat()
    fun surface(x: Float): Float = level + amplitude * sin(twoPi * (x - phase) / wavelength)
    reset()
    moveTo(left, bottom)
    lineTo(left, surface(left))
    var x = left + step
    while (x < right) {
        lineTo(x, surface(x))
        x += step
    }
    lineTo(right, surface(right))
    lineTo(right, bottom)
    close()
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Reloj y primer plano
// ──────────────────────────────────────────────────────────────────────────────────────────────

/** Detiene las ondas o el latido cuando el dibujo sale del área visible de la hoja. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.ringAdjustVisible(onChange: (Boolean) -> Unit): Modifier =
    onVisibilityChanged(minFractionVisible = 0.05f) { onChange(it) }

/** Verdadero mientras la app está en primer plano (al menos RESUMED): fuera de él no corre ningún bucle. */
@Composable
internal fun rememberRingAdjustForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * Segundos que llevan corriendo las animaciones del ajuste (las ondas, el latido de la zona elegida). Corre solo mientras [running];
 * al pararse conserva su valor y al reanudarse sigue desde ahí. Usa `withInfiniteAnimationFrameNanos`: las pruebas de Compose cancelan
 * el bucle en lugar de esperarlo.
 */
@Composable
internal fun rememberRingAdjustClock(running: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val base = seconds.floatValue
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now -> seconds.floatValue = base + (now - start) / 1_000_000_000f }
        }
    }
    return seconds
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// La hoja
// ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * La hoja del ajuste oficial: «Ajusta cómo llegas» con las pestañas «Energía · Columna · Músculos» (las de [channels]; con una sola
 * no hay pestañas), abierta en [initialChannel] y con «Restablecer» y «Listo» abajo. Muestra [EnergyColumnAdjust] o [MuscleBodyAdjust]
 * según la pestaña. Cerrarla (con «Listo», Atrás o tocando fuera) llama a [onDismiss]; lo que se ajusta ya ha ido por los callbacks.
 *
 * Es solo la vista: cada cambio sale por `onEnergyChange`, `onSpineChange` y `onMuscleChange` con valores VISIBLES, y «Restablecer»
 * llama al `on…Reset` del canal abierto, que debe devolverlo a su valor automático y quitarle la marca de editado. Quién la monta
 * decide qué guarda y cuándo.
 */
@Composable
fun RingAdjustSheet(
    initialChannel: RingChannel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    energy: RingAdjustLevel = RingAdjustLevel(value = 0, before = 0),
    spine: RingAdjustLevel = RingAdjustLevel(value = 0, before = 0),
    muscles: RingAdjustMuscles = RingAdjustMuscles(),
    onEnergyChange: (Int) -> Unit = {},
    onSpineChange: (Int) -> Unit = {},
    onMuscleChange: (muscleId: String, value: Int) -> Unit = { _, _ -> },
    onEnergyReset: () -> Unit = {},
    onSpineReset: () -> Unit = {},
    onMusclesReset: () -> Unit = {},
    hazeState: HazeState? = null,
    channels: List<RingChannel> = listOf(RingChannel.ENERGIA, RingChannel.COLUMNA, RingChannel.MUSCULOS),
    title: String = "Ajusta cómo llegas",
    doneLabel: String = "Listo",
) {
    require(channels.isNotEmpty()) { "La hoja necesita al menos un canal" }
    var channel by remember(initialChannel) { mutableStateOf(initialChannel.takeIf { it in channels } ?: channels.first()) }
    KpknSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("ring-adjust-sheet"),
        stableHeightFraction = 0.9f,
        hazeState = hazeState,
        additionalGlassScrim = SheetFill,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = KpknType.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = WizardColors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            if (channels.size > 1) {
                KpknSegmentedControl(
                    options = channels.map { ringAdjustChannelName(it) },
                    selectedIndex = channels.indexOf(channel),
                    onSelect = { channel = channels[it] },
                    size = KpknSegmentedSize.Compact,
                    modifier = Modifier.testTag("ring-adjust-tabs"),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (channel) {
                    RingChannel.ENERGIA -> EnergyColumnAdjust(
                        channel = RingChannel.ENERGIA,
                        value = energy.value,
                        before = energy.before,
                        onValueChange = onEnergyChange,
                    )
                    RingChannel.COLUMNA -> EnergyColumnAdjust(
                        channel = RingChannel.COLUMNA,
                        value = spine.value,
                        before = spine.before,
                        onValueChange = onSpineChange,
                    )
                    RingChannel.MUSCULOS -> MuscleBodyAdjust(
                        values = muscles.values,
                        before = muscles.before,
                        onValueChange = onMuscleChange,
                        highlighted = muscles.highlighted,
                    )
                }
            }
            val canReset = when (channel) {
                RingChannel.ENERGIA -> energy.edited
                RingChannel.COLUMNA -> spine.edited
                RingChannel.MUSCULOS -> muscles.edited.isNotEmpty()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknCompactPill(
                    text = "Restablecer",
                    onClick = {
                        when (channel) {
                            RingChannel.ENERGIA -> onEnergyReset()
                            RingChannel.COLUMNA -> onSpineReset()
                            RingChannel.MUSCULOS -> onMusclesReset()
                        }
                    },
                    filled = false,
                    height = 40.dp,
                    enabled = canReset,
                    modifier = Modifier.testTag("ring-adjust-reset"),
                )
                KpknCompactPill(
                    text = doneLabel,
                    onClick = onDismiss,
                    filled = true,
                    height = 40.dp,
                    fullWidth = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ring-adjust-done"),
                )
            }
        }
    }
}
