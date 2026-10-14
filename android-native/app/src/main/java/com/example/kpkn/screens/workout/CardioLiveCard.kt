package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.domain.cardio.CardioGuideEngine
import com.example.kpkn.domain.cardio.CardioIntervalEngine
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionCardioDose
import com.example.kpkn.screens.workout.design.CardioGpsReadout
import com.example.kpkn.screens.workout.design.CardioIntervalTimeline
import com.example.kpkn.screens.workout.design.CardioTimeReadout
import com.example.kpkn.screens.workout.design.CardioTimelineBlock
import com.example.kpkn.screens.workout.design.workoutBottomFade
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Página de cardio del pager (KPKN LS5b): el tiempo en `metricXL`, la línea de tiempo de los intervalos, la distancia y el ritmo del GPS
 * en `metricL` y los controles como píldoras. Sin caja: sobre el negro. La lectura se desplaza por dentro y los controles quedan abajo,
 * fijos. La lógica no cambia: mismos temporizadores, mismo GPS y mismas llamadas.
 */
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
    val gpsDurationSeconds = gpsState?.elapsedActiveSeconds?.takeIf { it > 0L }?.toInt()
    val durationSeconds = if (timerElapsedSeconds > 0) {
        timerElapsedSeconds
    } else if (gpsMode && gpsDurationSeconds != null) {
        gpsDurationSeconds
    } else {
        0
    }
    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val recordedDistanceKm = if (gpsHasData) gpsState?.distanceMeters?.div(1_000.0) else distanceKm
    val heartRate = (liveHeartRateBpm ?: heartRateText.toIntOrNull())?.coerceIn(30, 240)
    val guide = remember(details.type, details.intensity, details.intensityLevel, details.hiit) {
        CardioGuideEngine.guide(details)
    }
    val isGpsDeniedOrDisabled = gpsMode && gpsState?.status in setOf(
        CardioGpsStatus.PERMISSION_DENIED,
        CardioGpsStatus.LOCATION_DISABLED,
    )
    val gpsActive = gpsMode && !isGpsDeniedOrDisabled
    val gpsRequesting = gpsState?.status == CardioGpsStatus.REQUESTING_PERMISSION
    val awaitingOrRecorded = status == CardioExecutionStatus.AWAITING_CONFIRMATION || status == CardioExecutionStatus.RECORDED

    val intervalBlocks = remember(details) {
        if (details.hasIntervals()) CardioIntervalEngine.expandedBlocks(details) else emptyList()
    }
    val timelineBlocks = remember(intervalBlocks) {
        intervalBlocks.map { CardioTimelineBlock(work = it.type == CardioBlockType.WORK, seconds = it.durationSeconds) }
    }
    val intervalProgress = remember(details, timerElapsedSeconds) {
        if (details.hasIntervals()) CardioIntervalEngine.progressAt(details, timerElapsedSeconds) else null
    }
    val liveNow = remember(details, timerElapsedSeconds) {
        CardioPrescriptionFormatter.liveNowNext(details, timerElapsedSeconds)
    }

    LaunchedEffect(status) {
        if (status == CardioExecutionStatus.AWAITING_CONFIRMATION) showRecordConfirmation = true
        if (status == CardioExecutionStatus.RECORDED) showRecordConfirmation = false
    }

    // El tiempo grande: lo transcurrido en el cardio libre o ya cerrado; lo que queda, en el resto de bloques con objetivo.
    val timeIsElapsed = isLibre ||
        status == CardioExecutionStatus.AWAITING_CONFIRMATION ||
        status == CardioExecutionStatus.RECORDED
    val timeClock = formatCardioTime(if (timeIsElapsed) durationSeconds else timerRemainingSeconds)
    val timeCaption = if (timeIsElapsed) "Transcurrido" else "Restante"
    val statusLine = when (status) {
        CardioExecutionStatus.RUNNING -> "En curso"
        CardioExecutionStatus.PAUSED -> "Pausado"
        CardioExecutionStatus.AWAITING_CONFIRMATION -> "Listo para confirmar el registro"
        CardioExecutionStatus.RECORDED -> "Cardio registrado"
        CardioExecutionStatus.READY -> if (isLibre) "Listo para empezar" else "Listo para empezar · objetivo ${formatCardioTime(plannedDurationSeconds)}"
    }
    val intervalCaption = intervalProgress?.let { progress ->
        if (progress.isComplete) {
            "Circuito completo"
        } else {
            buildString {
                append("Intervalo ${progress.currentIndex + 1} de ${progress.totalBlocks}")
                progress.currentBlock?.let { append(" · ${CardioIntervalEngine.blockTypeLabel(it.type)}") }
            }
        }
    }
    val timelineSummary = run {
        val efforts = intervalBlocks.count { it.type == CardioBlockType.WORK }
        "Línea de tiempo: $efforts esfuerzos y ${intervalBlocks.size - efforts} pausas. ${intervalCaption.orEmpty()}"
    }
    val gpsStatusLine = when (gpsState?.status) {
        CardioGpsStatus.RECORDING -> "Señal GPS activa · ${formatCardioTime(durationSeconds)}"
        CardioGpsStatus.SIGNAL_LOST -> "Señal GPS perdida · puedes continuar o registrar manualmente"
        CardioGpsStatus.PAUSED -> "GPS pausado"
        CardioGpsStatus.REQUESTING_PERMISSION -> "Solicitando permiso y señal de ubicación…"
        CardioGpsStatus.PERMISSION_DENIED -> "Permiso denegado · registro manual disponible"
        CardioGpsStatus.LOCATION_DISABLED -> "Ubicación desactivada · registro manual disponible"
        else -> "GPS listo · el registro manual sigue disponible"
    }
    val splitsLine = gpsState?.kmSplitPaces.orEmpty().takeIf { it.isNotEmpty() }?.let { splits ->
        splits.mapIndexed { index, pace -> "km ${index + 1} ${formatCardioPaceValue(pace) ?: "—"}" }.joinToString(" · ")
    }

    // Píldora principal de movimiento: iniciar, pausar o reanudar, con la misma lógica de siempre (GPS o temporizador).
    val mainLabel = when {
        gpsActive -> when (gpsState?.status) {
            CardioGpsStatus.RECORDING -> "Pausar GPS"
            CardioGpsStatus.SIGNAL_LOST -> "Pausar y conservar datos"
            CardioGpsStatus.PAUSED -> "Reanudar GPS"
            else -> "Iniciar GPS"
        }
        status == CardioExecutionStatus.RUNNING -> "Pausar"
        status == CardioExecutionStatus.PAUSED -> "Reanudar"
        else -> "Iniciar"
    }
    val mainIsPause = mainLabel == "Pausar GPS" || mainLabel == "Pausar y conservar datos" || mainLabel == "Pausar"
    val mainIsResume = mainLabel == "Reanudar GPS" || mainLabel == "Reanudar"
    val startIsPrimary = !awaitingOrRecorded && !mainIsPause && !mainIsResume
    val onMain: () -> Unit = {
        if (gpsActive) {
            when (gpsState?.status) {
                CardioGpsStatus.RECORDING, CardioGpsStatus.SIGNAL_LOST -> onPauseGps()
                CardioGpsStatus.PAUSED -> onResumeGps()
                CardioGpsStatus.REQUESTING_PERMISSION -> Unit
                else -> onRequestGps()
            }
        } else if (status == CardioExecutionStatus.RUNNING) {
            onPauseTimer()
        } else if (!awaitingOrRecorded) {
            onStartTimer()
        }
    }

    val hasManualInput = (distanceKm != null && distanceKm > 0.0) || (heartRate != null) || (durationSeconds > 0)
    val canRecord = (status !in setOf(CardioExecutionStatus.READY, CardioExecutionStatus.RECORDED)) ||
        (hasManualInput && status != CardioExecutionStatus.RECORDED)
    val recordLabel = if (completedSet == null) "Finalizar y registrar" else "Actualizar cardio"
    val onRecordClick: () -> Unit = {
        onRequestRecord(durationSeconds, recordedDistanceKm, heartRate)
        showRecordConfirmation = true
    }

    // Dos zonas: la lectura se desplaza por dentro y los controles quedan abajo, visibles también al 130 % de letra.
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .workoutBottomFade(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CardioPrescriptionFormatter.typeLabel(details.type).uppercase(Locale("es")),
                    style = KpknType.eyebrow,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // La dosis corta («30 min», «2 × 0:40 / 0:20») no se corta a media palabra; la frase completa queda de respaldo.
                    text = SessionCardioDose.of(details) ?: CardioPrescriptionFormatter.sentence(details),
                    style = KpknType.title,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            CardioTimeReadout(clock = timeClock, caption = timeCaption)
            Text(
                text = statusLine,
                style = KpknType.caption,
                color = if (status == CardioExecutionStatus.RUNNING) accentColor else WizardColors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            if (intervalProgress != null && timelineBlocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardioIntervalTimeline(
                        blocks = timelineBlocks,
                        currentIndex = intervalProgress.currentIndex,
                        summary = timelineSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = intervalCaption.orEmpty(),
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    liveNow.nextLabel?.let { next ->
                        Text(
                            text = "Siguiente · $next",
                            style = KpknType.caption,
                            color = WizardColors.textFaint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (status == CardioExecutionStatus.RUNNING || status == CardioExecutionStatus.PAUSED) {
                        KpknTextAction(text = "Completar bloque", onClick = onSkipBlock)
                    }
                }
            }

            if (gpsMode) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardioGpsReadout(
                        distanceValue = recordedDistanceKm?.let { "%.2f".format(it) },
                        paceValue = formatCardioPaceValue(gpsState?.paceSecondsPerKm),
                    )
                    // Con el GPS grabando o en pausa, la página ya lo dice arriba («En curso», «Pausado»): la nota solo aparece si algo falla.
                    if (gpsState?.status != CardioGpsStatus.RECORDING && gpsState?.status != CardioGpsStatus.PAUSED) {
                        Text(
                            text = gpsStatusLine,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    splitsLine?.let { splits ->
                        Text(
                            text = splits,
                            style = KpknType.caption,
                            color = WizardColors.textFaint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (!details.hasIntervals()) {
                Text(
                    text = "Objetivo: RPE ${formatCardioRpe(guide.rpeTargetExact)}/10 · ${CardioGuideEngine.rpeAnchor(guide.rpeTargetExact)}" +
                        (guide.hrPercentRef?.let { " · $it" } ?: ""),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Km solo si el GPS no tiene datos (si los tiene, la distancia ya está arriba); la FC, siempre.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (details.supportsDistance && !gpsHasData) {
                    com.example.kpkn.screens.sessioneditor.components.CardioValuePill(
                        label = "Km",
                        value = cardioDistancePillText(
                            gpsHasData = false,
                            gpsDistanceKm = null,
                            enteredDistanceKm = distanceKm,
                        ),
                        accentColor = accentColor,
                        onClick = { showDistancePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                com.example.kpkn.screens.sessioneditor.components.CardioValuePill(
                    label = "FC media",
                    value = (liveHeartRateBpm ?: heartRateText.toIntOrNull())?.let { "$it bpm" } ?: "—",
                    accentColor = accentColor,
                    onClick = { if (liveHeartRateBpm == null) showHeartRatePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            if (isGpsDeniedOrDisabled) {
                KpknTextAction(text = "Reintentar permiso de ubicación / GPS", onClick = onRequestGps)
            }
            if (gpsMode && gpsState?.status in setOf(CardioGpsStatus.PERMISSION_DENIED, CardioGpsStatus.LOCATION_DISABLED, CardioGpsStatus.SIGNAL_LOST)) {
                Text(
                    text = "Puedes registrar duración, distancia y FC manualmente.",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Controles fijos abajo: la píldora de movimiento y la de registrar.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!awaitingOrRecorded) {
                if (mainIsPause || mainIsResume) {
                    KpknSecondaryPill(
                        text = mainLabel,
                        onClick = onMain,
                        modifier = Modifier.fillMaxWidth(),
                        icon = if (mainIsPause) KpknLineIcons.Pause else KpknLineIcons.Play,
                        enabled = !gpsRequesting,
                    )
                } else {
                    KpknPrimaryPill(
                        text = mainLabel,
                        onClick = onMain,
                        modifier = Modifier.fillMaxWidth(),
                        icon = KpknLineIcons.Play,
                        enabled = !gpsRequesting,
                    )
                }
            }
            if (canRecord) {
                if (startIsPrimary) {
                    KpknSecondaryPill(
                        text = recordLabel,
                        onClick = onRecordClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    KpknPrimaryPill(
                        text = recordLabel,
                        onClick = onRecordClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
            text = "¿Registrar ${formatCardioTime(durationSeconds)}" +
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

private fun formatCardioTime(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0) / 60
    val seconds = totalSeconds.coerceAtLeast(0) % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatCardioRpe(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatCardioDistance(distanceKm: Double?): String =
    distanceKm?.takeIf { it >= 0.0 }?.let { "%.2f km".format(it) } ?: "0.00 km"

/** Ritmo «5:30» por kilómetro, sin unidad (la unidad «/km» va en su columna), o null si no hay dato. */
private fun formatCardioPaceValue(paceSecondsPerKm: Int?): String? =
    paceSecondsPerKm?.takeIf { it > 0 }?.let { seconds -> "%d:%02d".format(seconds / 60, seconds % 60) }

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
