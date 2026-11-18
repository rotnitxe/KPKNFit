package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.media.technique.LiveAnalysisPhase
import com.example.kpkn.data.media.technique.LiveAnalysisTelemetry
import com.example.kpkn.data.media.technique.LiveSignalSample
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.analysis.AnalysisStatusUi
import com.example.kpkn.screens.technique.analysis.clampPercent
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.rememberKpknClockMs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val ANALYSIS_ANALYZING_STAGE_TAG = "analysis-analyzing-stage"
const val ANALYSIS_CANCEL_TAG = "analysis-cancel"

private val AnalyzingBackdrop = Color(0xFF050507)
private val AnalyzingCream = Color(0xFFF2EEE6)
private val AnalyzingGreen = Color(0xFF7FE08A)
private val AnalyzingAmber = Color(0xFFF7CF73)
private val AnalyzingDangerFill = Color(0x1FE05252)
private val AnalyzingDangerBorder = Color(0x4DE05252)
private val AnalyzingDangerInk = Color(0xFFF79090)

/**
 * Pantalla fullscreen `.tc-an` para `QUEUED`, `RUNNING` y `Actualizando…`:
 * barra superior `.tc-ant`, visor encuadrado 9:16 con esquinas `.tc-br` + láser `.tc-sweep` + pose/barra viva,
 * curva de señal `.tc-sig`, porcentaje hero `52sp`, barra 3dp, checklist 2×2 `.tc-phs` y acción real `Cancelar análisis`.
 */
@Composable
fun TechniqueAnalyzingStage(
    exerciseName: String,
    setInfo: String?,
    videoAspect: Float,
    durationMs: Long,
    status: AnalysisStatusUi.Analyzing,
    telemetry: LiveAnalysisTelemetry?,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    previewFrame: ImageBitmap? = null,
) {
    val reduced = wizardReducedMotion()
    val rawPct = maxOf(status.progressPct, telemetry?.progressPct ?: 0).let(::clampPercent)
    val isQueued = status.queued && rawPct == 0 && (telemetry == null || telemetry.phase == LiveAnalysisPhase.QUEUED)
    val animatedPct by animateKpknFloatAsState(
        target = rawPct / 100f,
        duration = if (reduced) KpknDuration(0) else KpknMotion.D280,
        easing = KpknMotion.Salida,
        label = "analyzingProgress",
    )
    val activeClock by rememberKpknClockMs(running = !isQueued && !reduced)
    val queuedClock by rememberKpknClockMs(running = isQueued && !reduced)

    val eyebrowText = when {
        status.updating -> "ACTUALIZANDO"
        isQueued -> "EN COLA"
        else -> "ANALIZANDO"
    }
    val headlineText = when {
        status.updating -> "Actualizando el análisis…"
        isQueued -> "En cola: empieza en cuanto el móvil esté libre."
        else -> "Analizando…"
    }

    val activeStageIndex = when {
        isQueued -> -1
        telemetry?.phase == LiveAnalysisPhase.DONE || rawPct >= 99 -> 4
        telemetry?.phase == LiveAnalysisPhase.METRICS || rawPct >= 90 -> 3
        telemetry?.phase == LiveAnalysisPhase.COUNTING || rawPct >= 75 -> 2
        rawPct >= 38 -> 1
        else -> 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnalyzingBackdrop)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag(ANALYSIS_ANALYZING_STAGE_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Barra superior `.tc-ant`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = eyebrowText,
                    style = KpknType.eyebrow.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp),
                    color = Color(0x73FFFFFF),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = exerciseName.ifBlank { "Técnica" },
                    style = TextStyle(
                        fontFamily = WizardFonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                    ),
                    color = AnalyzingCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (!setInfo.isNullOrBlank()) {
                    Text(
                        text = setInfo,
                        style = KpknType.caption.copy(fontSize = 11.5.sp),
                        color = Color(0x88FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Cerrar vista" }
                    .testTag("analysis-close"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x26FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KpknLineIcons.Close, contentDescription = null, tint = AnalyzingCream, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Cuerpo adaptativo `.tc-an`
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val tightHeight = maxHeight < 560.dp || LocalDensity.current.fontScale >= 1.2f
            val vfWidth = when {
                maxHeight < 500.dp -> 122.dp
                maxHeight < 600.dp -> 144.dp
                else -> 174.dp
            }
            val aspect = videoAspect.takeIf { it > 0.35f && it < 2.5f } ?: (9f / 16f)
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (tightHeight) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(horizontal = 20.dp, vertical = if (tightHeight) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (tightHeight) Arrangement.spacedBy(10.dp) else Arrangement.SpaceBetween,
            ) {
                // Visor encuadrado `.tc-vf`
                AnalyzingViewfinder(
                    aspect = aspect,
                    isQueued = isQueued,
                    telemetry = telemetry,
                    durationMs = maxOf(durationMs, telemetry?.durationMs ?: 0L),
                    previewFrame = previewFrame,
                    sweepClockMs = activeClock,
                    reduced = reduced,
                    modifier = Modifier.width(vfWidth),
                )

                // Curva de señal `.tc-sig`
                AnalyzingSignalGraph(
                    isQueued = isQueued,
                    progressFraction = animatedPct,
                    samples = telemetry?.signalSamples.orEmpty(),
                    activeClockMs = activeClock,
                    queuedClockMs = queuedClock,
                    reduced = reduced,
                    modifier = Modifier
                        .widthIn(max = 264.dp)
                        .fillMaxWidth()
                        .height(58.dp),
                )

                // Porcentaje hero + barra `.tc-pb`
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "$rawPct %",
                        style = TextStyle(
                            fontFamily = WizardFonts.display,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (tightHeight) 44.sp else 52.sp,
                            lineHeight = if (tightHeight) 48.sp else 56.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = AnalyzingCream,
                        modifier = Modifier.semantics { contentDescription = "$rawPct %" },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = headlineText,
                        style = KpknType.bodyStrong.copy(fontSize = 14.5.sp),
                        color = if (isQueued) Color(0xCCFFFFFF) else AnalyzingCream,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x1FFFFFFF))
                            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(rawPct / 100f, 0f..1f) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedPct.coerceIn(0f, 1f))
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brush.horizontalGradient(listOf(AnalyzingCream, AnalyzingGreen))),
                        )
                    }
                }

                // Grid 2x2 de fases `.tc-phs`
                AnalyzingPhasesGrid(
                    activeStage = activeStageIndex,
                    activeClockMs = activeClock,
                    reduced = reduced,
                    modifier = Modifier
                        .widthIn(max = 316.dp)
                        .fillMaxWidth(),
                )

                // Nota de segundo plano + botón rojo Cancelar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(
                        text = "Puedes salir: sigue en segundo plano.",
                        style = KpknType.caption.copy(fontSize = 12.sp),
                        color = Color(0x73FFFFFF),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 172.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(AnalyzingDangerFill)
                            .border(1.dp, AnalyzingDangerBorder, RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button, onClick = onCancel)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .testTag(ANALYSIS_CANCEL_TAG),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancelar análisis",
                            style = KpknType.bodyStrong.copy(fontSize = 13.5.sp),
                            color = AnalyzingDangerInk,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingViewfinder(
    aspect: Float,
    isQueued: Boolean,
    telemetry: LiveAnalysisTelemetry?,
    durationMs: Long,
    previewFrame: ImageBitmap?,
    sweepClockMs: Float,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0C0C10))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)),
    ) {
        if (previewFrame != null) {
            Image(
                bitmap = previewFrame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.42f),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF17181E), Color(0xFF0C0D12), Color(0xFF13151B))),
                ),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Pose y trayectoria parcial viva si no está en cola
            if (!isQueued && telemetry != null) {
                val poseStroke = 1.6.dp.toPx()
                for ((a, b) in telemetry.poseSegmentsNormalized) {
                    drawLine(
                        color = AnalyzingCream.copy(alpha = 0.72f),
                        start = Offset(a.x * w, a.y * h),
                        end = Offset(b.x * w, b.y * h),
                        strokeWidth = poseStroke,
                        cap = StrokeCap.Round,
                    )
                }
                val trail = telemetry.barTrailNormalized
                if (trail.size >= 2) {
                    val trailPath = Path().apply {
                        moveTo(trail[0].x * w, trail[0].y * h)
                        for (i in 1 until trail.size) {
                            lineTo(trail[i].x * w, trail[i].y * h)
                        }
                    }
                    drawPath(
                        path = trailPath,
                        color = AnalyzingAmber,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }

            // Línea láser vertical animada `.tc-sweep` (pausada en cola)
            if (!isQueued && !reduced) {
                val phase = (sweepClockMs % 2200f) / 2200f
                val y = (0.08f + phase * 0.84f) * h
                val band = 14.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, AnalyzingGreen.copy(alpha = 0.24f), Color.Transparent),
                        startY = y - band,
                        endY = y + band,
                    ),
                    topLeft = Offset(0f, y - band),
                    size = androidx.compose.ui.geometry.Size(w, band * 2f),
                )
                drawLine(
                    color = AnalyzingCream.copy(alpha = 0.82f),
                    start = Offset(10.dp.toPx(), y),
                    end = Offset(w - 10.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // 4 esquinas `.tc-br`
            val inset = 9.dp.toPx()
            val arm = 14.dp.toPx()
            val stroke = 2.dp.toPx()
            val cornerColor = AnalyzingCream.copy(alpha = 0.82f)
            // Top-left
            drawLine(cornerColor, Offset(inset, inset), Offset(inset + arm, inset), stroke, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(inset, inset), Offset(inset, inset + arm), stroke, cap = StrokeCap.Round)
            // Top-right
            drawLine(cornerColor, Offset(w - inset, inset), Offset(w - inset - arm, inset), stroke, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(w - inset, inset), Offset(w - inset, inset + arm), stroke, cap = StrokeCap.Round)
            // Bottom-left
            drawLine(cornerColor, Offset(inset, h - inset), Offset(inset + arm, h - inset), stroke, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(inset, h - inset), Offset(inset, h - inset - arm), stroke, cap = StrokeCap.Round)
            // Bottom-right
            drawLine(cornerColor, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), stroke, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(w - inset, h - inset), Offset(w - inset, h - inset - arm), stroke, cap = StrokeCap.Round)
        }

        // Pill de tiempo en vivo inferior
        val timeText = when {
            isQueued -> "En espera"
            telemetry != null && durationMs > 0L -> "${formatClockMs(telemetry.currentPtsMs)} / ${formatClockMs(durationMs)}"
            durationMs > 0L -> "0:00 / ${formatClockMs(durationMs)}"
            else -> "En curso"
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xA6000000))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = timeText,
                style = KpknType.caption.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                color = AnalyzingCream,
            )
        }
    }
}

@Composable
private fun AnalyzingSignalGraph(
    isQueued: Boolean,
    progressFraction: Float,
    samples: List<LiveSignalSample>,
    activeClockMs: Float,
    queuedClockMs: Float,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp)),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            val w = size.width
            val h = size.height
            // Rejilla sutil
            val grid = Color(0x12FFFFFF)
            drawLine(grid, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), 1.dp.toPx())
            drawLine(grid, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1.dp.toPx())
            drawLine(grid, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), 1.dp.toPx())

            if (isQueued) {
                val breathe = if (reduced) 0.45f else (0.30f + 0.30f * (0.5f + 0.5f * cos(queuedClockMs * 2f * PI.toFloat() / 1800f)))
                drawLine(
                    color = Color(0xFFFFFFFF).copy(alpha = breathe),
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                return@Canvas
            }

            val limit = progressFraction.coerceIn(0.02f, 1f)
            val points: List<Offset> = if (samples.size >= 2) {
                samples.map { Offset(it.tFraction.coerceIn(0f, 1f) * w, (1f - it.heightNormalized.coerceIn(0.05f, 0.95f)) * h) }
            } else {
                // Cuando se abre un proceso en curso sin muestras acumuladas aún, se dibuja línea de progreso activa estable
                val steps = 24
                val phaseShift = if (reduced) 0f else activeClockMs * 0.004f
                List(steps) { idx ->
                    val fx = (idx.toFloat() / (steps - 1)) * limit
                    val ripple = 0.5f + 0.14f * sin(fx * 18f - phaseShift)
                    Offset(fx * w, ripple * h)
                }
            }

            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(
                path = path,
                color = AnalyzingCream.copy(alpha = 0.86f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val tip = points.last()
            drawCircle(AnalyzingCream.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = tip)
            drawCircle(AnalyzingCream, radius = 3.dp.toPx(), center = tip)
        }
    }
}

@Composable
private fun AnalyzingPhasesGrid(
    activeStage: Int,
    activeClockMs: Float,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val stages = listOf(
        "Detectando tu pose",
        "Siguiendo barra y disco",
        "Contando repeticiones",
        "Calculando métricas",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0..1) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) {
                    val idx = row * 2 + col
                    val completed = activeStage > idx
                    val active = activeStage == idx
                    AnalyzingPhaseCell(
                        label = stages[idx],
                        completed = completed,
                        active = active,
                        activeClockMs = activeClockMs,
                        reduced = reduced,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzingPhaseCell(
    label: String,
    completed: Boolean,
    active: Boolean,
    activeClockMs: Float,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val inkColor = when {
        completed -> Color(0xE6F2EEE6)
        active -> AnalyzingCream
        else -> Color(0x57FFFFFF)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0x14FFFFFF) else Color(0x08FFFFFF))
            .border(
                1.dp,
                when {
                    completed -> AnalyzingGreen.copy(alpha = 0.34f)
                    active -> Color(0x33F2EEE6)
                    else -> Color(0x12FFFFFF)
                },
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            when {
                completed -> Box(
                    Modifier.size(15.dp).clip(CircleShape).background(AnalyzingGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KpknLineIcons.Check, contentDescription = null, tint = AnalyzingGreen, modifier = Modifier.size(10.dp))
                }
                active -> {
                    val pulse = if (reduced) 1f else (0.55f + 0.45f * (0.5f + 0.5f * cos(activeClockMs * 2f * PI.toFloat() / 1100f)))
                    Canvas(Modifier.size(14.dp)) {
                        drawCircle(
                            color = AnalyzingCream.copy(alpha = 0.24f),
                            radius = size.minDimension / 2f,
                            style = Stroke(1.6.dp.toPx()),
                        )
                        drawCircle(
                            color = AnalyzingCream.copy(alpha = pulse),
                            radius = 3.2.dp.toPx(),
                        )
                    }
                }
                else -> Canvas(Modifier.size(12.dp)) {
                    drawCircle(color = Color(0x33FFFFFF), radius = 4.dp.toPx(), style = Stroke(1.4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = KpknType.caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp),
            color = inkColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatClockMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "$min:${sec.toString().padStart(2, '0')}"
}
