package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import com.example.kpkn.screens.workout.WorkoutVoiceUiState
import kotlin.math.roundToInt

/**
 * Modo Voz como capa viva (iteración 03): lo que la cápsula enseña mientras se habla. Todo sale del motor real:
 * [partialText] es la frase parcial del reconocedor, [listening] dice si la escucha está abierta y [spokenText] es el texto que KPKN
 * está diciendo por el TTS en este momento (el mismo que se oye).
 */
data class VoiceLiveFeed(
    val partialText: String = "",
    val listening: Boolean = false,
    val spokenText: String? = null,
)

/** Los tres campos de la tarjeta que reciben un valor dictado. */
internal enum class VoiceFlightField { LOAD, QUANTITY, EFFORT }

/** Un valor entendido: su campo, la cifra («100»), la unidad pequeña («kg») y lo que vuela («100»). */
internal data class VoiceHeardToken(val field: VoiceFlightField, val value: String, val unit: String?, val fly: String)

/**
 * Una frase que el parser acaba de llevar al borrador de una serie (`WorkoutVoiceUiState.Applied`): la cápsula la enseña «Entendido» y
 * sus valores vuelan a su campo antes de que la serie se registre.
 */
internal data class VoiceHeardCapture(
    val id: String,
    val atMs: Long,
    val exerciseId: String,
    val exerciseName: String,
    val setIdx: Int,
    val transcript: String,
    val tokens: List<VoiceHeardToken>,
)

/** Construye la captura con los valores REALES del parser. [quantityUnit] es la unidad de la cantidad de esa serie («reps», «s»…). */
internal fun voiceHeardCapture(applied: WorkoutVoiceUiState.Applied, exerciseName: String, quantityUnit: String, atMs: Long): VoiceHeardCapture {
    val interpretation = applied.interpretation
    val tokens = buildList {
        interpretation.weightKg?.let { add(VoiceHeardToken(VoiceFlightField.LOAD, heardNumber(it), "kg", heardNumber(it))) }
        interpretation.resolvedMetricValue?.let {
            add(VoiceHeardToken(VoiceFlightField.QUANTITY, if (quantityUnit == "reps") "× ${heardNumber(it)}" else heardNumber(it), quantityUnit, heardNumber(it)))
        }
        val effort = when {
            interpretation.intensityValue != null -> when (interpretation.intensityKind) {
                WorkoutVoiceIntensityKind.RPE -> "RPE ${heardNumber(interpretation.intensityValue)}"
                WorkoutVoiceIntensityKind.PERCENT_RM -> "${heardNumber(interpretation.intensityValue)} %"
                else -> "RIR ${heardNumber(interpretation.intensityValue)}"
            }
            interpretation.reachedFailure -> "Fallo"
            else -> null
        }
        effort?.let { add(VoiceHeardToken(VoiceFlightField.EFFORT, it, null, it)) }
    }
    return VoiceHeardCapture(
        id = "${applied.exerciseId}/${applied.setIdx}/${applied.side}/${interpretation.transcript}/${atMs}",
        atMs = atMs,
        exerciseId = applied.exerciseId,
        exerciseName = exerciseName,
        setIdx = applied.setIdx,
        transcript = interpretation.transcript,
        tokens = tokens,
    )
}

private fun heardNumber(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().replace('.', ',')
}

/** Destino del vuelo de un campo de la tarjeta de la serie. */
internal fun voiceFieldTargetKey(exerciseId: String, setIdx: Int, field: VoiceFlightField): String = "set/$exerciseId/$setIdx/${field.name}"

/** Destino del vuelo de una fila de la vista Lista (v-list). */
internal fun voiceRowTargetKey(exerciseId: String, setIdx: Int): String = "row/$exerciseId/$setIdx"

/** Destino del vuelo de un comando de descanso («+15 s» al reloj, v-rest). */
internal const val VoiceRestClockTargetKey = "rest/clock"

/** Cómo responde un destino al recibir el valor: anillo de brillo (campo) o destello del fondo (fila de Lista). */
internal enum class VoiceTargetStyle { RING, ROW }

/**
 * Registro de los destinos del vuelo. Cada destino informa de su posición en la raíz y la cápsula la lee al lanzar el vuelo; al
 * aterrizar, el destino brilla. «Por revisar» marca los valores dudosos con borde discontinuo (`.voice-mark`).
 */
@Stable
internal class VoiceFlightTargets {
    private class Placed(val owner: Any, val rect: Rect)

    private val placed = HashMap<String, Placed>()
    private val glowTicks = mutableStateMapOf<String, Int>()
    private val marks = mutableStateMapOf<String, Int>()

    fun report(key: String, owner: Any, rect: Rect) { placed[key] = Placed(owner, rect) }

    fun forget(key: String, owner: Any) { if (placed[key]?.owner === owner) placed.remove(key) }

    fun boundsInRoot(key: String): Rect? = placed[key]?.rect

    fun glow(key: String) { glowTicks[key] = (glowTicks[key] ?: 0) + 1 }

    fun glowTick(key: String): Int = glowTicks[key] ?: 0

    fun mark(key: String) { marks[key] = (marks[key] ?: 0) + 1 }

    fun unmark(key: String) {
        val count = (marks[key] ?: 0) - 1
        if (count <= 0) marks.remove(key) else marks[key] = count
    }

    fun isMarked(key: String): Boolean = (marks[key] ?: 0) > 0

    companion object {
        /** El registro de la sesión en vivo: la tarjeta, la Lista y la cápsula comparten este por defecto. */
        val Shared = VoiceFlightTargets()
    }
}

internal val LocalVoiceFlightTargets = staticCompositionLocalOf { VoiceFlightTargets.Shared }

private val GlowInk = Color(0xFFF2EEE6)
private val GlowEase = CubicBezierEasing(0f, 0f, .58f, 1f)

/**
 * Gancho de un destino del vuelo de Modo Voz: informa de su posición y dibuja el brillo al recibir el valor (`.glow`: anillo de 0 a
 * 18 dp que se apaga en 1,2 s; en Lista, `.flash`: el fondo destella) y el borde discontinuo de «Por revisar» (`.voice-mark`).
 */
internal fun Modifier.voiceFlightTarget(key: String, style: VoiceTargetStyle = VoiceTargetStyle.RING): Modifier = composed {
    val targets = LocalVoiceFlightTargets.current
    val owner = remember { Any() }
    DisposableEffect(key, targets) { onDispose { targets.forget(key, owner) } }
    val tick = targets.glowTick(key)
    val marked = targets.isMarked(key)
    val reduced = wizardReducedMotion()
    val glow = remember { Animatable(1f) }
    LaunchedEffect(tick) {
        if (tick > 0 && !reduced) {
            glow.snapTo(0f)
            glow.animateTo(1f, tween(1_200, easing = GlowEase))
        }
    }
    this
        .onGloballyPositioned { targets.report(key, owner, it.boundsInRoot()) }
        .drawWithContent {
            val t = glow.value
            if (style == VoiceTargetStyle.ROW && t < 1f) {
                drawRect(GlowInk.copy(alpha = .2f * (1f - t)))
            }
            drawContent()
            if (style == VoiceTargetStyle.RING && t < 1f) {
                val spread = 18.dp.toPx() * t
                val radius = minOf(size.height / 2f, 12.dp.toPx())
                val inner = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
                }
                clipPath(inner, ClipOp.Difference) {
                    drawRoundRect(
                        GlowInk.copy(alpha = .67f * (1f - t)),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(radius + spread),
                    )
                }
            }
            if (marked) {
                val gap = 3.dp.toPx()
                drawRoundRect(
                    GlowInk.copy(alpha = .4f),
                    topLeft = Offset(-gap, -gap),
                    size = Size(size.width + gap * 2, size.height + gap * 2),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
        }
}
