package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.finishRingDrainCaption
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Cifra del resumen al terminar: etiqueta, valor y unidad, si la hay (KPKN_UI_V2 §5). */
data class FinishFigure(val label: String, val value: String, val unit: String? = null)

/**
 * Las cuatro cifras del resumen (LIVE_SESSION_V1 §5): duración, series, volumen y récords. Sin récords, la cuarta es «Ejercicios» con su
 * número: nunca «0 récords».
 */
internal fun finishFigures(
    durationMinutes: Int,
    totalSets: Int,
    totalVolumeKg: Double,
    recordCount: Int,
    exerciseCount: Int,
): List<FinishFigure> {
    val fourth = if (recordCount > 0) {
        FinishFigure(label = "Récords", value = recordCount.toString())
    } else {
        FinishFigure(label = "Ejercicios", value = exerciseCount.toString())
    }
    return listOf(
        FinishFigure(label = "Duración", value = durationMinutes.toString(), unit = "min"),
        FinishFigure(label = "Series", value = totalSets.toString()),
        FinishFigure(label = "Volumen", value = finishFormatThousands(totalVolumeKg.roundToLong()), unit = "kg"),
        fourth,
    )
}

/** Miles con punto, como se escriben en español: 8450 → «8.450». */
internal fun finishFormatThousands(value: Long): String {
    val digits = abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (value < 0) "−$digits" else digits
}

/** Un decimal con coma, como en español: 7.5 → «7,5». */
internal fun finishOneDecimal(value: Double): String = String.format(Locale("es"), "%.1f", value)

/**
 * Gasto de la torre por canal, en el orden de la pantalla: «Músculos −32 · Energía −18 · Columna −12». Nulo si no hay gasto que contar.
 */
internal fun finishDrainLine(muscularDrain: Int, neuralDrain: Int, spinalDrain: Int): String? {
    val parts = listOfNotNull(
        finishRingDrainCaption(muscularDrain)?.let { "Músculos $it" },
        finishRingDrainCaption(neuralDrain)?.let { "Energía $it" },
        finishRingDrainCaption(spinalDrain)?.let { "Columna $it" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Estimación para mañana, con «≈» porque es una estimación. Nulo si no hay ninguna de las dos. */
internal fun finishTomorrowLine(neural: Int?, spinal: Int?): String? {
    val parts = listOfNotNull(
        neural?.let { "Energía ≈ $it %" },
        spinal?.let { "Columna ≈ $it %" },
    )
    return parts.takeIf { it.isNotEmpty() }?.let { "Mañana: " + it.joinToString(", ") }
}

/** Cabecera del resumen: «Sesión terminada» en `display` y el nombre de la sesión en `caption`. */
@Composable
internal fun FinishSummaryHeader(sessionName: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Sesión terminada",
            style = KpknType.display,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = sessionName,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Módulo de las cuatro cifras en rejilla de 2 × 2 (KPKN_UI_V2 §1 y §5). Con [figures] nulo, esqueletos mientras se calcula.
 */
@Composable
internal fun FinishFiguresModule(figures: List<FinishFigure>?, modifier: Modifier = Modifier) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Esta sesión")
        if (figures == null) {
            repeat(2) { row ->
                if (row > 0) FinishDivider()
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(2) { column ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (column > 0) 16.dp else 0.dp, top = 12.dp, bottom = 12.dp),
                        ) {
                            KpknSkeleton(modifier = Modifier.width(56.dp).height(12.dp), shape = RoundedCornerShape(6.dp))
                            Spacer(Modifier.height(10.dp))
                            KpknSkeleton(modifier = Modifier.width(96.dp).height(30.dp), shape = RoundedCornerShape(10.dp))
                        }
                    }
                }
            }
        } else {
            figures.chunked(2).forEachIndexed { row, pair ->
                if (row > 0) FinishDivider()
                Row(modifier = Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { column, figure ->
                        FinishLabeledMetric(
                            label = figure.label,
                            value = figure.value,
                            unit = figure.unit,
                            style = KpknType.metricL,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (column > 0) 16.dp else 0.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Etiqueta en `caption` y cifra debajo: la base de las cifras del resumen y del detalle. */
@Composable
internal fun FinishLabeledMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    style: TextStyle = KpknType.metricM,
) {
    Column(modifier = modifier) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        Spacer(Modifier.height(4.dp))
        KpknMetric(value = value, unit = unit, style = style)
    }
}

/**
 * Módulo de la torre de los RINGS (120 dp): se gasta una vez de lo que había ([from]) a lo que queda ([values]), con el gasto por canal
 * en `caption` y la estimación de mañana debajo. Con [loading], esqueleto y, si [loadingCaptionVisible], «Calculando la recuperación…».
 * Sin [values] y sin cargar, no se pinta: sin datos del motor, no hay bloque.
 */
@Composable
internal fun FinishRecoveryModule(
    values: TowerRingValues?,
    from: TowerRingValues?,
    drainLine: String?,
    tomorrowLine: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    loadingCaptionVisible: Boolean = false,
) {
    if (!loading && values == null) return
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Recuperación")
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading || values == null) {
                // Esqueleto con la silueta de la torre: un bloque ancho y bajo, no un círculo.
                KpknSkeleton(modifier = Modifier.size(width = 120.dp, height = 74.dp), shape = RoundedCornerShape(percent = 50))
                if (loadingCaptionVisible) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Calculando la recuperación…",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            } else {
                KpknTowerRings(
                    values = values,
                    from = from,
                    modifier = Modifier.size(120.dp),
                )
                drainLine?.let { line ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = line,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                tomorrowLine?.let { line ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = line,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Aviso de series pendientes (D2.1): texto neutral, con «Seguir con ellas» en el color de energía. Lleva las etiquetas de prueba
 * `finish_pending_series_notice` y `finish_pending_series_continue`.
 */
@Composable
internal fun FinishPendingSeriesModule(
    message: String,
    actionLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(
        modifier = modifier
            .testTag("finish_pending_series_notice")
            .semantics { liveRegion = LiveRegionMode.Polite },
        accent = SymbolPalette.energia,
    ) {
        Text(text = message, style = KpknType.body, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        KpknTextAction(
            text = actionLabel,
            onClick = onContinue,
            modifier = Modifier
                .align(Alignment.End)
                .testTag("finish_pending_series_continue"),
            color = SymbolPalette.energia,
        )
    }
}

/** Error del cálculo: módulo teñido de peligro con el motivo en lenguaje llano. Nunca el mensaje técnico de la excepción. */
@Composable
internal fun FinishCalculationErrorModule(reason: String, modifier: Modifier = Modifier) {
    KpknModule(modifier = modifier, accent = WizardColors.danger) {
        Text(text = reason, style = KpknType.body, color = WizardColors.text)
    }
}

/** Sin series registradas: estado vacío con el texto de hoy y la guía de la sesión, si la hay. */
@Composable
internal fun FinishEmptySeriesState(guidance: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        KpknEmptyState(
            icon = KpknLineIcons.Training,
            title = "Sin series registradas",
            body = guidance,
        )
    }
}

/**
 * Pie del resumen: «Guardar entrenamiento» a todo el ancho, «Compartir» con su descripción de Instagram Stories y «Volver al
 * entrenamiento» como acción de texto. Sin [onShare] ni [onBack], esos controles no se pintan.
 */
@Composable
internal fun FinishFooter(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryStateDescription: String? = null,
    onShare: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KpknPrimaryPill(
            text = primaryLabel,
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { primaryStateDescription?.let { stateDescription = it } },
        )
        if (onShare != null) {
            KpknSecondaryPill(
                text = "Compartir",
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Compartir en Instagram Stories" },
            )
        }
        if (onBack != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                KpknTextAction(
                    text = "Volver al entrenamiento",
                    onClick = onBack,
                    color = WizardColors.textMuted,
                )
            }
        }
    }
}

/**
 * Anillo de ajuste de la recuperación (LIVE_SESSION_V1 §5): arrastrar en vertical cambia el valor de 0 a 100, con el arco en [color]
 * sobre una pista al 12 % de la tinta. El valor y el cambio llegan de fuera; el arrastre es solo estado de gesto.
 */
@Composable
internal fun FinishRingAdjuster(
    title: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = value.coerceIn(0, 100)
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragStartValue by remember { mutableIntStateOf(clamped) }
    val currentValue by rememberUpdatedState(clamped)
    val currentOnChange by rememberUpdatedState(onValueChange)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = KpknType.caption, color = WizardColors.textMuted)
        Box(
            modifier = Modifier
                .size(120.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = "$clamped por ciento"
                    progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..100f, 100)
                    setProgress { target ->
                        currentOnChange(target.toInt().coerceIn(0, 100))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccumulator = 0f
                            dragStartValue = currentValue
                        },
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += -dragAmount.y
                            val step = (dragAccumulator / 1.25f).toInt()
                            currentOnChange((dragStartValue + step).coerceIn(0, 100))
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(120.dp)) { drawRingArc(percent = clamped, color = color) }
            Text(
                text = clamped.toString(),
                style = KpknType.metricL,
                color = WizardColors.text,
                maxLines = 1,
            )
        }
    }
}

/** Arco del anillo de ajuste: pista al 12 % de la tinta y, encima, el porcentaje en [color] desde las 12 en sentido horario. */
private fun DrawScope.drawRingArc(percent: Int, color: Color) {
    val stroke = 10.dp.toPx()
    val inset = stroke / 2f
    val arcSize = Size(size.width - stroke, size.height - stroke)
    val topLeft = Offset(inset, inset)
    drawArc(
        color = KpknRingColors.ink.copy(alpha = 0.12f),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    if (percent > 0) {
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * percent / 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * Barra de frescura de un músculo (LIVE_SESSION_V1 §5): tocar o deslizar en horizontal fija el valor de 0 a 100. El color sigue la
 * frescura: verde desde 80, tinta atenuada desde 55 y, debajo, tinta más tenue. Zona táctil de 48 dp.
 */
@Composable
internal fun FinishMuscleBar(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = value.coerceIn(0, 100)
    val accent = when {
        clamped >= 80 -> WizardColors.done
        clamped >= 55 -> WizardColors.textMuted
        else -> WizardColors.textFaint
    }
    var barWidth by remember { mutableFloatStateOf(0f) }
    val currentOnChange by rememberUpdatedState(onValueChange)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = KpknType.body, color = WizardColors.textMuted)
            Text(text = "$clamped %", style = KpknType.unit, color = WizardColors.text)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { barWidth = it.width.toFloat() }
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = "$clamped por ciento"
                    progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..100f, 100)
                    setProgress { target ->
                        currentOnChange(target.toInt().coerceIn(0, 100))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (barWidth > 0f) currentOnChange(((offset.x / barWidth) * 100f).toInt().coerceIn(0, 100))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        if (barWidth > 0f) {
                            currentOnChange(((change.position.x / barWidth) * 100f).toInt().coerceIn(0, 100))
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KpknSurface.inset),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped / 100f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
        }
    }
}

/** Línea de separación de 1 dp dentro de un módulo (KPKN_UI_V2 §1). */
@Composable
internal fun FinishDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}

/** Aviso teñido de energía: una nota sobre la calidad de las estimaciones, sin acción. */
@Composable
internal fun FinishWarningModule(message: String, modifier: Modifier = Modifier) {
    KpknModule(modifier = modifier, accent = SymbolPalette.energia, contentPadding = PaddingValues(16.dp)) {
        Text(text = message, style = KpknType.caption, color = WizardColors.text)
    }
}
