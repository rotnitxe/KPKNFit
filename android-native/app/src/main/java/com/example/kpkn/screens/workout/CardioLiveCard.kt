package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import com.example.kpkn.ui.components.KpknAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.domain.cardio.CardioGuideEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun CardioLiveCard(
    details: CardioDetails,
    completedSet: CompletedSet?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    executionState: CardioTimerState? = null,
    liveHeartRateBpm: Int? = null,
    onStartTimer: () -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onSkipBlock: () -> Unit = {},
    onRequestRecord: (durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) -> Unit = { _, _, _ -> },
    onCancelRecord: () -> Unit = {},
    onRecord: suspend (durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) -> Boolean,
    gpsState: CardioGpsState? = null,
    onRequestGps: () -> Unit = {},
    onPauseGps: () -> Unit = {},
    onResumeGps: () -> Unit = {},
) {
    var distanceText by remember(completedSet?.distanceKm) {
        mutableStateOf(completedSet?.distanceKm?.toString().orEmpty())
    }
    var heartRateText by remember(completedSet?.avgHeartRate) {
        mutableStateOf(completedSet?.avgHeartRate?.toString().orEmpty())
    }
    var showRecordConfirmation by remember { mutableStateOf(false) }
    var showDistancePicker by remember { mutableStateOf(false) }
    var showHeartRatePicker by remember { mutableStateOf(false) }
    var isSavingRecord by remember { mutableStateOf(false) }
    var recordSaveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val status = executionState?.status ?: CardioExecutionStatus.READY
    val isLibre = !details.hasIntervals() && details.targetDurationSeconds == null
    val plannedDurationSeconds = if (isLibre) 20 * 60 else details.effectiveDurationSeconds().coerceAtLeast(1)
    val timerElapsedSeconds = executionState?.elapsedSeconds ?: 0
    val timerRemainingSeconds = executionState?.remainingSeconds ?: plannedDurationSeconds
    val gpsMode = details.requiresGps
    val gpsHasData = gpsMode && gpsState?.let { it.pointCount > 0 || it.distanceMeters >= 10.0 } == true
    val durationSeconds = cardioRecordedDurationSeconds(gpsMode, gpsState?.elapsedActiveSeconds, timerElapsedSeconds)
    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val recordedDistanceKm = if (gpsHasData) gpsState?.distanceMeters?.div(1_000.0) else distanceKm
    val heartRate = (liveHeartRateBpm ?: heartRateText.toIntOrNull())?.coerceIn(30, 240)
    val guide = remember(details.type, details.intensity, details.intensityLevel, details.hiit) {
        CardioGuideEngine.guide(details)
    }

    LaunchedEffect(status) {
        if (status == CardioExecutionStatus.AWAITING_CONFIRMATION) showRecordConfirmation = true
        if (status == CardioExecutionStatus.RECORDED) showRecordConfirmation = false
    }

    // Cardio occupies its own stage rather than imitating a strength-set card:
    // the timer, interval context and telemetry stay readable edge-to-edge.
    // LR1c1: la tarjeta es un `module` (la misma piel que la serie); dentro, el relleno y el scroll de siempre.
    KpknModule(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        com.example.kpkn.domain.cardio.CardioPrescriptionFormatter.typeLabel(details.type).uppercase(),
                        style = KpknType.eyebrow,
                        color = WizardColors.textMuted,
                    )
                    Text(
                        com.example.kpkn.domain.cardio.CardioPrescriptionFormatter.sentence(details),
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                }
            }
            val liveNow = remember(details, timerElapsedSeconds) {
                com.example.kpkn.domain.cardio.CardioPrescriptionFormatter.liveNowNext(details, timerElapsedSeconds)
            }
            // Los estados, en la piel nueva: el trabajo en el acento; la recuperación, en la columna; el calentamiento y el enfriamiento, en `ok`.
            val phaseColor = when (liveNow.phase) {
                com.example.kpkn.data.models.CardioBlockType.WORK -> accentColor
                com.example.kpkn.data.models.CardioBlockType.RECOVER -> SymbolPalette.columna
                com.example.kpkn.data.models.CardioBlockType.WARMUP,
                com.example.kpkn.data.models.CardioBlockType.COOLDOWN,
                -> SymbolPalette.ok
                null -> accentColor
            }
            // Aviso teñido con el color de la fase: relleno del acento al 8 % y filete al 24 %.
            KpknModule(accent = phaseColor, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ahora · ${liveNow.nowLabel}", style = KpknType.bodyStrong, color = phaseColor)
                    liveNow.nextLabel?.let { Text("Siguiente · $it", style = KpknType.caption, color = WizardColors.textMuted) }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 184.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(KpknSurface.inset)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                CardioMetricCircle(
                    label = "Tiempo",
                    value = formatCardioTime(if (status == CardioExecutionStatus.READY) plannedDurationSeconds else timerElapsedSeconds),
                    progress = if (plannedDurationSeconds > 0) {
                        (timerElapsedSeconds.toFloat() / plannedDurationSeconds).coerceIn(0f, 1f)
                    } else 0f,
                    accentColor = accentColor,
                )
            }

            if (details.hasIntervals()) {
                CardioIntervalChart(
                    details = details,
                    accentColor = accentColor,
                    elapsedSeconds = timerElapsedSeconds,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                when (status) {
                    CardioExecutionStatus.RUNNING -> "En curso · ${formatCardioTime(timerElapsedSeconds)} realizados · ${formatCardioTime(timerRemainingSeconds)} restantes"
                    CardioExecutionStatus.PAUSED -> "Pausado · ${formatCardioTime(timerElapsedSeconds)} realizados"
                    CardioExecutionStatus.AWAITING_CONFIRMATION -> "Listo para confirmar el registro"
                    CardioExecutionStatus.RECORDED -> "Cardio registrado · ${formatCardioTime(timerElapsedSeconds)}"
                    CardioExecutionStatus.READY -> "Listo · objetivo ${formatCardioTime(plannedDurationSeconds)}"
                },
                style = KpknType.bodyStrong,
                color = if (status == CardioExecutionStatus.RUNNING) accentColor else WizardColors.textMuted,
            )

            if (gpsMode) {
                Text(
                    when (gpsState?.status) {
                        CardioGpsStatus.RECORDING -> "Señal GPS activa · ${formatCardioDistance(recordedDistanceKm)} · ${formatCardioTime(durationSeconds)}"
                        CardioGpsStatus.SIGNAL_LOST -> "Señal GPS perdida · puedes continuar o registrar manualmente"
                        CardioGpsStatus.PAUSED -> "GPS pausado · ${formatCardioDistance(recordedDistanceKm)}"
                        CardioGpsStatus.REQUESTING_PERMISSION -> "Solicitando permiso y señal de ubicación…"
                        CardioGpsStatus.PERMISSION_DENIED -> "Permiso denegado · registro manual disponible"
                        CardioGpsStatus.LOCATION_DISABLED -> "Ubicación desactivada · registro manual disponible"
                        else -> "GPS listo · el registro manual sigue disponible"
                    },
                    style = KpknType.body,
                    color = if (gpsState?.status == CardioGpsStatus.RECORDING) accentColor else WizardColors.textMuted,
                )
            }

            if (!details.hasIntervals()) {
                Text(
                    "Objetivo: RPE ${formatCardioRpe(guide.rpeTargetExact)}/10 · ${CardioGuideEngine.rpeAnchor(guide.rpeTargetExact)}" +
                        (guide.hrPercentRef?.let { " · $it" } ?: ""),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }

            val isGpsDeniedOrDisabled = gpsMode && gpsState?.status in setOf(
                CardioGpsStatus.PERMISSION_DENIED,
                CardioGpsStatus.LOCATION_DISABLED,
            )

            KpknPrimaryPill(
                text = when {
                    gpsMode && !isGpsDeniedOrDisabled && gpsState?.status == CardioGpsStatus.RECORDING -> "Pausar GPS"
                    gpsMode && !isGpsDeniedOrDisabled && gpsState?.status == CardioGpsStatus.SIGNAL_LOST -> "Pausar y conservar datos"
                    gpsMode && !isGpsDeniedOrDisabled && gpsState?.status == CardioGpsStatus.PAUSED -> "Reanudar GPS"
                    gpsMode && !isGpsDeniedOrDisabled -> "Iniciar GPS"
                    status == CardioExecutionStatus.RUNNING -> "Pausar"
                    status == CardioExecutionStatus.PAUSED -> "Reanudar"
                    else -> "Iniciar"
                },
                onClick = {
                    if (gpsMode && !isGpsDeniedOrDisabled) {
                        when (gpsState?.status) {
                            CardioGpsStatus.RECORDING, CardioGpsStatus.SIGNAL_LOST -> onPauseGps()
                            CardioGpsStatus.PAUSED -> onResumeGps()
                            CardioGpsStatus.REQUESTING_PERMISSION -> Unit
                            else -> onRequestGps()
                        }
                    } else if (status == CardioExecutionStatus.RUNNING) {
                        onPauseTimer()
                    } else if (status != CardioExecutionStatus.AWAITING_CONFIRMATION && status != CardioExecutionStatus.RECORDED) {
                        onStartTimer()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = gpsState?.status != CardioGpsStatus.REQUESTING_PERMISSION &&
                    status !in setOf(CardioExecutionStatus.AWAITING_CONFIRMATION, CardioExecutionStatus.RECORDED),
            )

            if (details.hasIntervals() && status in setOf(CardioExecutionStatus.RUNNING, CardioExecutionStatus.PAUSED)) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KpknTextAction(text = "Completar bloque", onClick = onSkipBlock)
                }
            }

            if (isGpsDeniedOrDisabled) {
                KpknTextAction(
                    text = "Reintentar permiso de ubicación / GPS",
                    onClick = onRequestGps,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (details.supportsDistance) {
                    com.example.kpkn.screens.sessioneditor.components.CardioValuePill(
                        label = "Km",
                        value = cardioDistancePillText(
                            gpsHasData = gpsHasData,
                            gpsDistanceKm = recordedDistanceKm,
                            enteredDistanceKm = distanceKm,
                        ),
                        accentColor = accentColor,
                        onClick = { if (!gpsHasData) showDistancePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                com.example.kpkn.screens.sessioneditor.components.CardioValuePill(
                    label = "FC media",
                    value = (liveHeartRateBpm?.toString() ?: heartRateText.ifBlank { "—" }),
                    accentColor = accentColor,
                    onClick = { if (liveHeartRateBpm == null) showHeartRatePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (gpsMode) {
                Text(
                    "Distancia ${formatCardioDistance(recordedDistanceKm)} · ritmo ${formatCardioPace(gpsState?.paceSecondsPerKm)}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                val splits = gpsState?.kmSplitPaces.orEmpty()
                if (splits.isNotEmpty()) {
                    Text(
                        splits.mapIndexed { index, pace -> "km ${index + 1} ${formatCardioPace(pace)}" }.joinToString(" · "),
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                }
            }
            val hasManualInput = (distanceKm != null && distanceKm > 0.0) || (heartRate != null) || (durationSeconds > 0)
            val canRecord = (status !in setOf(CardioExecutionStatus.READY, CardioExecutionStatus.RECORDED)) ||
                (hasManualInput && status != CardioExecutionStatus.RECORDED)
            KpknPrimaryPill(
                text = if (completedSet == null) "Finalizar y registrar" else "Actualizar cardio",
                onClick = {
                    onRequestRecord(durationSeconds, recordedDistanceKm, heartRate)
                    showRecordConfirmation = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canRecord,
            )
            if (gpsMode) {
                Text(
                    "Si se deniega el permiso o se pierde la señal, puedes registrar duración, distancia y FC manualmente.",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
        }
    }

    if (showDistancePicker) {
        com.example.kpkn.screens.sessioneditor.components.CardioDistanceWheelDialog(
            title = "Distancia",
            initialKm = distanceText.replace(',', '.').toDoubleOrNull(),
            accentColor = accentColor,
            onDismiss = { showDistancePicker = false },
            onConfirm = { km ->
                distanceText = km?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }.orEmpty()
                showDistancePicker = false
            },
        )
    }
    if (showHeartRatePicker) {
        com.example.kpkn.screens.sessioneditor.components.CardioIntWheelDialog(
            title = "FC media",
            initial = heartRateText.toIntOrNull() ?: 140,
            range = 80..220,
            unit = "bpm",
            accentColor = accentColor,
            onDismiss = { showHeartRatePicker = false },
            onConfirm = { bpm ->
                heartRateText = bpm?.toString().orEmpty()
                showHeartRatePicker = false
            },
            allowZeroAsNone = true,
        )
    }
    if (showRecordConfirmation) {
        KpknAlertDialog(
            onDismissRequest = {
                if (!isSavingRecord) {
                    showRecordConfirmation = false
                    recordSaveFailed = false
                    onCancelRecord()
                }
            },
            title = "Confirmar cardio",
            text = "Registrar ${formatCardioTime(durationSeconds)}" +
                (recordedDistanceKm?.let { " · ${formatCardioDistance(it)}" } ?: "") +
                " como resultado de esta sesión?" +
                if (recordSaveFailed) "\nNo se pudo guardar. Puedes reintentar sin perder los datos." else "",
            confirmLabel = when {
                isSavingRecord -> "Guardando…"
                recordSaveFailed -> "Reintentar"
                else -> "Registrar"
            },
            onConfirm = {
                if (!isSavingRecord) {
                    scope.launch {
                        isSavingRecord = true
                        recordSaveFailed = false
                        try {
                            if (onRecord(durationSeconds, recordedDistanceKm, heartRate)) {
                                showRecordConfirmation = false
                            } else {
                                recordSaveFailed = true
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            recordSaveFailed = true
                        } finally {
                            isSavingRecord = false
                        }
                    }
                }
            },
            dismissLabel = if (isSavingRecord) null else "Cancelar",
            onDismiss = {
                if (!isSavingRecord) {
                    showRecordConfirmation = false
                    recordSaveFailed = false
                    onCancelRecord()
                }
            },
        )
    }
}

@Composable
private fun CardioMetricCircle(
    label: String,
    value: String,
    progress: Float,
    accentColor: Color,
) {
    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.matchParentSize(),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.08f),
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(Locale("es")), style = KpknType.eyebrow, color = WizardColors.textMuted)
            // El reloj en Syne con cifras tabulares, como el descanso; encoge si no cabe en el aro.
            CardioClockText(
                text = value,
                maxFontSize = 28.sp,
                // Margen dentro del aro: el reloj encoge para caber en el hueco interior, sin pisar el trazo.
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * Reloj del aro del cardio (LR1c1): Syne ExtraBold con cifras tabulares, como `RestClockText`, pero con un mínimo de 12 sp: en el 130 %
 * el texto encoge lo necesario para caber en el hueco del aro, en vez de salirse por los lados.
 */
@Composable
private fun CardioClockText(
    text: String,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val autoSize = remember(maxFontSize) {
        TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = maxFontSize, stepSize = 1.sp)
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = WizardFonts.display,
            fontWeight = FontWeight.ExtraBold,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "'lnum' 1, 'tnum' 1",
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = autoSize,
    )
}

private fun formatCardioTime(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0) / 60
    val seconds = totalSeconds.coerceAtLeast(0) % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatCardioRpe(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatCardioDistance(distanceKm: Double?): String =
    distanceKm?.takeIf { it >= 0.0 }?.let { "%.2f km".format(it) } ?: "0.00 km"

/**
 * Display text of the "Km" tile.  `distanceText` is the raw numeric string
 * (a restored `CompletedSet.distanceKm.toString()` can be
 * "0.10811738104249106"), so the tile shows the parsed value through the same
 * formatter as the "Distancia" caption; it never formats the raw text itself.
 */
internal fun cardioDistancePillText(
    gpsHasData: Boolean,
    gpsDistanceKm: Double?,
    enteredDistanceKm: Double?,
): String {
    val shownKm = if (gpsHasData) gpsDistanceKm else enteredDistanceKm
    return if (shownKm == null) "—" else formatCardioDistance(shownKm)
}

private fun formatCardioPace(paceSecondsPerKm: Int?): String = paceSecondsPerKm?.let { seconds ->
    "%02d:%02d/km".format(seconds / 60, seconds % 60)
} ?: "--/km"
