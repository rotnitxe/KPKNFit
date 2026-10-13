package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.roundToInt

/*
 * «Cómo llegan tus músculos» (ola Home v1, I2): un anillo por músculo de la sesión, con su % de recuperación. Sin estado.
 */

/** Un músculo de la sesión con su % de recuperación. [score] null = sin dato: se pinta «—», nunca un 100 % inventado. */
internal data class HomeMuscleGauge(val name: String, val score: Int?)

/** Hasta seis músculos, de menor a mayor %, con los sin dato al final. */
internal fun homeMuscleOrder(muscles: List<HomeMuscleGauge>): List<HomeMuscleGauge> =
    muscles.sortedWith(compareBy<HomeMuscleGauge>({ it.score == null }, { it.score ?: 0 })).take(6)

/**
 * Recuerda la última lista de anillos que ya trazó. El trazo crece al verla por primera vez y al cambiar de día; al volver a
 * una lista ya vista (p. ej. tras hacer scroll) aparece directamente en su valor.
 */
internal object HomeGaugeMemory {
    private var lastKey: String? = null

    /** true si [key] es distinta de la última que trazó; la guarda para la siguiente vez. */
    @Synchronized
    fun firstPlay(key: String): Boolean {
        if (key == lastKey) return false
        lastKey = key
        return true
    }
}

/**
 * La rejilla de anillos: de tres en tres, hasta seis músculos. Toda la rejilla es un botón de [onOpen] (el detalle de Músculos),
 * con una descripción con los valores finales. Sin músculos no pinta nada.
 */
@Composable
internal fun HomeMuscleGauges(
    muscles: List<HomeMuscleGauge>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (muscles.isEmpty()) return
    val reduced = wizardReducedMotion()
    val shown = remember(muscles) { homeMuscleOrder(muscles) }
    val gaugeKey = shown.joinToString("|") { "${it.name}:${it.score}" }
    val description = "Músculos de esta sesión: " + shown.joinToString(", ") { gauge ->
        gauge.score?.let { "${gauge.name} $it %" } ?: "${gauge.name} sin dato"
    }
    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionLabel("Cómo llegan tus músculos")
        Spacer(Modifier.height(16.dp))
        // Con `key`, cada lista nueva (otro día) recompone la rejilla y sus trazos empiezan de cero.
        key(gaugeKey) {
            val play = remember { HomeGaugeMemory.firstPlay(gaugeKey) && !reduced }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WizardSpacing.gutter)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .semantics(mergeDescendants = true) { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                shown.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { gauge ->
                            GaugeCell(gauge = gauge, play = play, modifier = Modifier.weight(1f))
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * Un músculo: el anillo de 52 dp con el % dentro (sin espacio, «91%»), y debajo el nombre centrado en una sola línea con elipsis.
 * Sin dato, el anillo es discontinuo y dentro va el guion.
 */
@Composable
private fun GaugeCell(gauge: HomeMuscleGauge, play: Boolean, modifier: Modifier = Modifier) {
    val score = gauge.score
    val target = (score ?: 0).coerceIn(0, 100) / 100f
    val sweep = remember { Animatable(if (play && score != null) 0f else target) }
    LaunchedEffect(Unit) {
        if (play && score != null) sweep.animateTo(target, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 4.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                if (score == null) {
                    // Sin dato: pista discontinua, sin arco.
                    drawArc(
                        color = WizardColors.textFaint,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                } else {
                    drawArc(
                        color = Color.White.copy(alpha = 0.10f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    if (sweep.value > 0f) {
                        drawArc(
                            color = SymbolPalette.musculo,
                            startAngle = -90f,
                            sweepAngle = 360f * sweep.value,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Text(
                text = if (score == null) "—" else "${(sweep.value * 100f).roundToInt()}%",
                style = HomeTypography.gaugeFigure,
                color = if (score == null) WizardColors.textFaint else WizardColors.text,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = gauge.name,
            style = WizardTypography.caption,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
