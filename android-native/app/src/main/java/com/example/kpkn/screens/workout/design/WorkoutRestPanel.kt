package com.example.kpkn.screens.workout.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.RelatorAssistAction
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutLiveRelatorLine
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Diámetro del anillo del descanso (KPKN LS3 §3). */
private val RestRingSize = 200.dp

/** Grosor del arco del anillo. */
private val RestRingStroke = 10.dp

/** Tiempo del descanso en «m:ss» («1:45», «0:45»): sin cero a la izquierda en los minutos. */
internal fun formatWorkoutRestClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale("es"), "%d:%02d", safe / 60, safe % 60)
}

/** Kilos de la serie siguiente con coma decimal: «80», «82,5». */
internal fun formatWorkoutRestKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale("es"), "%.1f", kg)

/**
 * Línea de debajo del anillo (KPKN LS3): «Siguiente ronda» y «Entre lados» según el tipo de descanso; en el resto, la serie que sigue
 * con su dato planificado («Siguiente: Serie 3 · 80 kg × 8»). Sin serie siguiente, no hay línea (null): nunca se inventa un dato.
 */
internal fun workoutRestNextLine(
    kind: RestTimerKind?,
    nextSetNumber: Int?,
    plannedWeightKg: Double?,
    plannedReps: Int?,
): String? = when (kind) {
    RestTimerKind.SUPERSET_ROUND -> "Siguiente ronda"
    RestTimerKind.BETWEEN_SIDES -> "Entre lados"
    else -> nextSetNumber?.let { number ->
        val weight = plannedWeightKg?.takeIf { it > 0.0 }?.let { "${formatWorkoutRestKg(it)} kg" }
        val reps = plannedReps?.takeIf { it > 0 }?.let { if (weight != null) "× $it" else "$it repeticiones" }
        val detail = listOfNotNull(weight, reps).joinToString(" ")
        if (detail.isEmpty()) "Siguiente: Serie $number" else "Siguiente: Serie $number · $detail"
    }
}

/**
 * El descanso único de la sesión en vivo (KPKN LS3 §3). Ocupa el sitio del panel de la serie en la página de descanso, sobre el negro
 * y sin caja. Sin estado: [remainingSeconds] y [totalSeconds] los lleva quien la monta.
 *
 * - Un anillo de 200 dp que se vacía en `columna`, con el tiempo en `metricXL` tabular y «Descanso» en `caption`. En cero, «Listo».
 * - [nextLine] debajo del anillo, en `body`, si hay. [videoSlot] (hueco de la ola VT) se pinta debajo de ella cuando no es nulo.
 * - Con [suggestedSeconds] no nulo, «Descanso sugerido 2:30» con «Usar» ([onUseSuggestion]).
 * - «−15 s» y «+15 s» llaman a [onAdjustSeconds] con −15 y 15; «Saltar descanso» llama a [onSkip].
 * - El relator (si [relatorText] no está en blanco) va encima del anillo; [notices] son avisos honestos del aviso del descanso.
 * - [onSkipExercise], con [skipExerciseLabel], salta el resto del ejercicio.
 *
 * Con `wizardReducedMotion()`: entrada de 120 ms sin escala y el anillo salta de segundo en segundo, sin animación continua.
 */
@Composable
fun WorkoutRestPanel(
    remainingSeconds: Int,
    totalSeconds: Int,
    nextLine: String?,
    suggestedSeconds: Int?,
    onAdjustSeconds: (Int) -> Unit,
    onSkip: () -> Unit,
    onUseSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
    relatorText: String? = null,
    relatorPhaseKey: String = "",
    relatorActions: List<RelatorAssistAction> = emptyList(),
    onRelatorAction: (RelatorAssistAction) -> Unit = {},
    accentColor: Color = WizardColors.text,
    notices: List<String> = emptyList(),
    skipExerciseLabel: String? = null,
    onSkipExercise: (() -> Unit)? = null,
    videoSlot: (@Composable () -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    val remaining = remainingSeconds.coerceAtLeast(0)
    val total = totalSeconds.coerceAtLeast(1)
    val progress = (remaining.toFloat() / total).coerceIn(0f, 1f)
    val sweep by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (reduced) snap() else tween(durationMillis = 900, easing = LinearEasing),
        label = "restRingSweep",
    )
    // La entrada es un fundido (y una escala leve) al aparecer: el panel llega en el sitio de la serie.
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entrance,
        modifier = modifier.fillMaxWidth(),
        enter = if (reduced) {
            fadeIn(tween(durationMillis = 120))
        } else {
            fadeIn(tween(durationMillis = 220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(durationMillis = 220))
        },
        exit = fadeOut(tween(durationMillis = 120)),
    ) {
        // Desplazable: en la página de la serie la altura la fija el panel de la serie, y con relator o avisos el descanso puede ser más alto.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!relatorText.isNullOrBlank()) {
                WorkoutLiveRelatorLine(
                    text = relatorText,
                    phaseKey = relatorPhaseKey,
                    actions = relatorActions,
                    onAction = onRelatorAction,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RestRing(remaining = remaining, sweep = sweep)
            if (nextLine != null) {
                Text(
                    text = nextLine,
                    style = KpknType.body,
                    color = WizardColors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            videoSlot?.invoke()
            if (suggestedSeconds != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Descanso sugerido ${formatWorkoutRestClock(suggestedSeconds)}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                    )
                    KpknTextAction(text = "Usar", onClick = onUseSuggestion)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KpknSecondaryPill(
                    text = "−15 s",
                    onClick = { onAdjustSeconds(-15) },
                    modifier = Modifier.weight(1f),
                )
                KpknSecondaryPill(
                    text = "+15 s",
                    onClick = { onAdjustSeconds(15) },
                    modifier = Modifier.weight(1f),
                )
            }
            KpknTextAction(text = "Saltar descanso", onClick = onSkip)
            if (onSkipExercise != null) {
                KpknTextAction(text = skipExerciseLabel ?: "Saltar ejercicio", onClick = onSkipExercise)
            }
            notices.forEach { notice ->
                Text(
                    text = notice,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** El anillo: una pista blanca al 8 % y el arco de [sweep] en `columna`, con el tiempo y «Descanso» en el centro. */
@Composable
private fun RestRing(remaining: Int, sweep: Float) {
    val done = remaining == 0
    val clock = if (done) "Listo" else formatWorkoutRestClock(remaining)
    Box(
        modifier = Modifier
            .size(RestRingSize)
            .semantics {
                contentDescription = if (done) "Descanso terminado" else "Descanso, quedan ${formatWorkoutRestClock(remaining)}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RestRingStroke.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (sweep > 0f) {
                drawArc(
                    color = SymbolPalette.columna,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = clock,
                style = KpknType.metricXL,
                color = WizardColors.text,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Descanso",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
            )
        }
    }
}
