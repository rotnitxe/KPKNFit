package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.domain.cardio.CardioGuideEngine
import com.example.kpkn.domain.cardio.CardioIntervalEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.components.CardioFigureSpec
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetLabeledField
import com.example.kpkn.screens.workout.components.LiveSheetPrimary
import com.example.kpkn.screens.workout.components.LiveSheetShortCopy
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.screens.workout.components.cardioFigureCadence
import com.example.kpkn.screens.workout.components.cardioFigureIcon
import com.example.kpkn.screens.workout.components.cardioFigureSpec
import com.example.kpkn.screens.workout.components.RestRef02Button
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Lo que dice la transición «Ahora vamos con cardio» en una sesión mixta (fuerza + cardio). */
internal data class CardioTransitionInfo(val lead: String?)

/**
 * Lo que presenta «Ahora vamos con fuerza» al terminar el cardio en una sesión mixta: la siguiente serie de fuerza tal como se verá en
 * Tarjetas. `onContinue` pasa a ese ejercicio.
 */
internal data class CardioTransitionOutInfo(
    val exerciseName: String,
    val meta: String,
    val firstSet: String?,
    val planEffort: String?,
    val lastTime: String?,
    val chips: List<String>,
    val footnote: String?,
    val onContinue: () -> Unit,
)

private val CBody = TextStyle(fontFamily = WizardFonts.body)
private val CSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)

/** Lo que se mide mientras el cardio está en marcha: tiempo en zona por paso, pulso medio y máximo, tiempo por zona y pausa. */
private class CardioMeasures(stepCount: Int) {
    val stepInZone = IntArray(stepCount)
    val stepSamples = IntArray(stepCount)
    val zoneSeconds = IntArray(5)
    var hrSum = 0L
    var hrCount = 0
    var hrMax = 0
    /** Pulsos tomados a mano (sin reloj): cada uno es una muestra suelta, no tiempo en zona. */
    var manualSum = 0L
    var manualCount = 0
    var pausedSeconds by mutableIntStateOf(0)
    var tick by mutableIntStateOf(0)
    var stepStartHr: Int? = null
    var stepStartIndex = -1
    var stepElapsed = 0
}

/**
 * El espacio de cardio (iteración 03, aprobada): «Antes de empezar» con las comprobaciones y «Empezar» (cuenta atrás 3-2-1); en sesión
 * mixta, primero «Ahora vamos con cardio» con deslizar. En marcha, tres páginas (Ahora · Plan · Resumen) y el dock con Bloquear ·
 * Pausa · Saltar paso; la pausa con su velo; el bloqueo se quita manteniendo. Al terminar, el resumen medido, el cumplimiento por paso y
 * el RPE real obligatorio para guardar.
 */
@Composable
internal fun CardioLiveCard(
    details: CardioDetails,
    completedSet: CompletedSet?,
    @Suppress("UNUSED_PARAMETER") accentColor: Color,
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
    exerciseName: String = "",
    /** FC máxima (220 − edad) para las zonas en lpm; sin ella, las zonas se dicen en % de la FC máxima. */
    maxHeartRateBpm: Int? = null,
    bodyWeightKg: Double? = null,
    onRecordWithRpe: (suspend (durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?, rpe: Double) -> Boolean)? = null,
    transitionIn: CardioTransitionInfo? = null,
    transitionOut: CardioTransitionOutInfo? = null,
) {
    var distanceText by remember(completedSet?.distanceKm) { mutableStateOf(completedSet?.distanceKm?.toString().orEmpty()) }
    var heartRateText by remember(completedSet?.avgHeartRate) { mutableStateOf(completedSet?.avgHeartRate?.toString().orEmpty()) }
    var showDistancePicker by remember { mutableStateOf(false) }
    var showHeartRatePicker by remember { mutableStateOf(false) }
    var isSavingRecord by remember { mutableStateOf(false) }
    var recordSaveFailed by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var rpe by remember(completedSet?.rpe) { mutableStateOf(completedSet?.rpe?.roundToInt()) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var transitionSeen by rememberSaveable(details.type, details.intervalBlocks.size) { mutableStateOf(false) }
    var transitionOutSeen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val status = executionState?.status ?: CardioExecutionStatus.READY
    val isLibre = !details.hasIntervals() && details.targetDurationSeconds == null
    val plannedDurationSeconds = if (isLibre) 20 * 60 else details.effectiveDurationSeconds().coerceAtLeast(1)
    val elapsed = executionState?.elapsedSeconds ?: 0
    val gpsMode = details.requiresGps
    val gpsHasData = gpsMode && gpsState?.let { it.pointCount > 0 || it.distanceMeters >= 10.0 } == true
    val durationSeconds = cardioRecordedDurationSeconds(gpsMode, gpsState?.elapsedActiveSeconds, elapsed)
    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val recordedDistanceKm = if (gpsHasData) gpsState?.distanceMeters?.div(1_000.0) else distanceKm
    val guide = remember(details.type, details.intensity, details.intensityLevel, details.hiit) { CardioGuideEngine.guide(details) }
    val plannedRpe = guide.rpeTargetExact.roundToInt().coerceIn(1, 10)

    val steps = remember(details) { cardioSteps(details) }
    val progress = remember(details, elapsed) { if (details.hasIntervals()) CardioIntervalEngine.progressAt(details, elapsed) else null }
    val stepIndex = (progress?.currentIndex ?: 0).coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    val step = steps.getOrNull(stepIndex)
    val stepLeft = progress?.remainingInBlock ?: (plannedDurationSeconds - elapsed).coerceAtLeast(0)
    val stepFraction = step?.let { 1f - stepLeft.toFloat() / it.seconds.coerceAtLeast(1) } ?: 0f
    val totalLeft = (plannedDurationSeconds - elapsed).coerceAtLeast(0)
    val rounds = steps.count { it.block?.type == CardioBlockType.WORK }
    val watchHr = liveHeartRateBpm?.takeIf { it in 30..240 }
    // Pulso manual: sin reloj, el usuario lo anota; se enseña con su zona como el del reloj y con su antigüedad en tiempo activo.
    var manualHr by rememberSaveable { mutableStateOf<Int?>(null) }
    var manualHrAtSeconds by rememberSaveable { mutableIntStateOf(0) }
    var showManualHrSheet by remember { mutableStateOf(false) }
    val hr = watchHr ?: manualHr
    val hrIsManual = watchHr == null && manualHr != null
    val maxHr = maxHeartRateBpm?.takeIf { it in 120..230 }
    val liveZone = if (hr != null && maxHr != null) cardioZoneForPercent(hr * 100 / maxHr) else null
    val targetZone = step?.zone ?: 2

    // Aviso con histéresis: un pico de un segundo no cambia la zona que se enseña.
    var shownZone by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(liveZone, targetZone) {
        if (liveZone == null) { shownZone = null; return@LaunchedEffect }
        if (shownZone == null) { shownZone = liveZone; return@LaunchedEffect }
        delay(if (liveZone == targetZone) 3_000 else 5_000)
        shownZone = liveZone
    }

    val measures = remember(details) { CardioMeasures(steps.size.coerceAtLeast(1)) }
    val running = status == CardioExecutionStatus.RUNNING || (gpsMode && gpsState?.status == CardioGpsStatus.RECORDING)
    val latestStep by rememberUpdatedState(stepIndex)
    // Solo el reloj cuenta segundos en zona: un pulso manual es una lectura suelta y no se reparte por el tiempo.
    val latestHr by rememberUpdatedState(watchHr)
    val latestZone by rememberUpdatedState(liveZone.takeIf { watchHr != null })
    val latestTarget by rememberUpdatedState(targetZone)
    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            val index = latestStep
            if (index != measures.stepStartIndex) { measures.stepStartIndex = index; measures.stepElapsed = 0; measures.stepStartHr = latestHr }
            measures.stepElapsed++
            latestHr?.let { value ->
                measures.hrSum += value; measures.hrCount++; measures.hrMax = maxOf(measures.hrMax, value)
                latestZone?.let { zone ->
                    measures.zoneSeconds[zone - 1]++
                    if (index in measures.stepSamples.indices) {
                        measures.stepSamples[index]++
                        if (zone == latestTarget) measures.stepInZone[index]++
                    }
                }
            }
            measures.tick++
        }
    }
    LaunchedEffect(status == CardioExecutionStatus.PAUSED) {
        while (status == CardioExecutionStatus.PAUSED) { delay(1_000); measures.pausedSeconds++ }
    }
    LaunchedEffect(status) {
        if (status == CardioExecutionStatus.AWAITING_CONFIRMATION) finishing = true
        if (status == CardioExecutionStatus.RECORDED) { finishing = false; locked = false }
    }

    val isGpsDeniedOrDisabled = gpsMode && gpsState?.status in setOf(CardioGpsStatus.PERMISSION_DENIED, CardioGpsStatus.LOCATION_DISABLED)
    // El mando principal: con GPS controla la captura de posición; sin él, el temporizador.
    fun startOrResume() {
        if (gpsMode && !isGpsDeniedOrDisabled) {
            when (gpsState?.status) {
                CardioGpsStatus.PAUSED -> onResumeGps()
                CardioGpsStatus.RECORDING, CardioGpsStatus.SIGNAL_LOST, CardioGpsStatus.REQUESTING_PERMISSION -> Unit
                else -> onRequestGps()
            }
        } else if (status != CardioExecutionStatus.RUNNING && status != CardioExecutionStatus.AWAITING_CONFIRMATION &&
            status != CardioExecutionStatus.RECORDED) onStartTimer()
    }
    fun pause() {
        if (gpsMode && !isGpsDeniedOrDisabled && gpsState?.status in setOf(CardioGpsStatus.RECORDING, CardioGpsStatus.SIGNAL_LOST)) onPauseGps()
        else if (status == CardioExecutionStatus.RUNNING) onPauseTimer()
    }
    fun beginFinish() {
        locked = false
        onRequestRecord(durationSeconds, recordedDistanceKm, hr ?: heartRateText.toIntOrNull())
        finishing = true
    }
    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(900); countdown--; if (countdown == 0) startOrResume() }
    }

    val watchAvgHr = if (measures.hrCount > 0) (measures.hrSum / measures.hrCount).toInt() else null
    val manualAvgHr = if (measures.manualCount > 0) (measures.manualSum / measures.manualCount).toInt() else null
    val avgHr = watchAvgHr ?: manualAvgHr
    // Al terminar: lo medido por el reloj manda; si no, lo que se corrija en «FC media» y, si no, la media de los pulsos manuales.
    val finishHr = watchAvgHr ?: heartRateText.toIntOrNull() ?: manualAvgHr
    val manualHrAgeSeconds = (durationSeconds - manualHrAtSeconds).coerceAtLeast(0)
    val figureSpeedKmh = step?.block?.speedKmh ?: details.targetPaceSecondsPerKm?.takeIf { it > 0 }?.let { 3600.0 / it }
    val figure = cardioFigureSpec(details.type, figureSpeedKmh)
    val estimatedKm = remember(details, elapsed) {
        if (details.hasIntervals()) {
            var acc = 0; var km = 0.0
            CardioIntervalEngine.expandedBlocks(details).forEach { block ->
                val inBlock = (elapsed - acc).coerceIn(0, block.durationSeconds)
                block.speedKmh?.let { km += it * inBlock / 3600.0 }
                acc += block.durationSeconds
            }
            km.takeIf { it > 0.0 }
        } else null
    }
    val kcal = remember(details, elapsed, bodyWeightKg) {
        val kg = bodyWeightKg?.takeIf { it > 0 } ?: return@remember null
        if (details.hasIntervals()) {
            var acc = 0; var total = 0.0
            CardioIntervalEngine.expandedBlocks(details).forEach { block ->
                val inBlock = (elapsed - acc).coerceIn(0, block.durationSeconds)
                total += CardioIntervalEngine.metForBlock(block, details.type, details.resolvedIntensityLevel()) * kg * inBlock / 3600.0
                acc += block.durationSeconds
            }
            total
        } else details.metBase.takeIf { it > 0 }?.let { it * kg * elapsed / 3600.0 }
    }

    Box(modifier.fillMaxSize().clip(RoundedCornerShape(25.dp)).background(Color.Black).testTag("cardio-space")) {
        when {
            // Al terminar el cardio no hay descanso: si sigue fuerza, la transición aparece enseguida con lo medido.
            status == CardioExecutionStatus.RECORDED && !finishing && transitionOut != null && !transitionOutSeen -> CardioTransitionOut(
                lead = buildList {
                    add(cardioClock(durationSeconds))
                    recordedDistanceKm?.takeIf { it > 0.0 }?.let { add("${cardioDecimal(it)} km") }
                    val samples = measures.stepSamples.sum()
                    if (samples > 0) add("${(measures.stepInZone.sum() * 100.0 / samples).roundToInt()} % en tu objetivo")
                }.joinToString(" · ", prefix = "Terminaste el cardio: ", postfix = "."),
                exerciseName = transitionOut.exerciseName,
                meta = transitionOut.meta,
                firstSet = transitionOut.firstSet,
                planEffort = transitionOut.planEffort,
                lastTime = transitionOut.lastTime,
                chips = transitionOut.chips,
                footnote = transitionOut.footnote,
                onContinue = { transitionOutSeen = true; transitionOut.onContinue() },
            )
            status == CardioExecutionStatus.RECORDED && !finishing -> CardioRecordedView(
                exerciseName = exerciseName, durationSeconds = durationSeconds, distanceKm = recordedDistanceKm, avgHr = completedSet?.avgHeartRate ?: avgHr,
                rpe = completedSet?.rpe, onUpdate = { finishing = true },
            )
            finishing -> CardioFinishView(
                exerciseName = exerciseName,
                durationSeconds = durationSeconds,
                plannedSeconds = plannedDurationSeconds,
                pausedSeconds = measures.pausedSeconds,
                distanceKm = recordedDistanceKm,
                estimatedKm = estimatedKm.takeIf { recordedDistanceKm == null },
                distanceSource = if (gpsHasData) "km · GPS" else if (distanceKm != null) "km · registrada" else "km · cinta (estimada)",
                avgHr = finishHr,
                maxHr = measures.hrMax.takeIf { it > 0 },
                kcal = kcal,
                steps = steps,
                stepInZone = measures.stepInZone,
                stepSamples = measures.stepSamples,
                rpe = rpe,
                plannedRpe = plannedRpe,
                onRpe = { rpe = it; recordSaveFailed = false },
                onEditDistance = if (gpsHasData) null else ({ showDistancePicker = true }),
                onEditHeartRate = if (watchAvgHr != null) null else ({ showHeartRatePicker = true }),
                saving = isSavingRecord,
                failed = recordSaveFailed,
                onSave = {
                    val chosen = rpe ?: return@CardioFinishView
                    if (isSavingRecord) return@CardioFinishView
                    scope.launch {
                        isSavingRecord = true; recordSaveFailed = false
                        try {
                            val heart = finishHr
                            val saved = onRecordWithRpe?.invoke(durationSeconds, recordedDistanceKm ?: estimatedKm, heart, chosen.toDouble())
                                ?: onRecord(durationSeconds, recordedDistanceKm ?: estimatedKm, heart)
                            if (saved) finishing = false else recordSaveFailed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            recordSaveFailed = true
                        } finally { isSavingRecord = false }
                    }
                },
                onBack = if (status == CardioExecutionStatus.RECORDED) ({ finishing = false }) else ({ finishing = false; onCancelRecord() }),
            )
            status == CardioExecutionStatus.READY && transitionIn != null && !transitionSeen -> CardioTransitionIn(
                title = "Ahora vamos\ncon cardio",
                lead = transitionIn.lead,
                exerciseName = exerciseName,
                meta = "Cardio · ${cardioClock(plannedDurationSeconds)} · ${steps.size} ${if (steps.size == 1) "paso" else "pasos"}",
                firstStep = steps.firstOrNull(),
                steps = steps,
                totalLabel = "Total ${cardioClock(plannedDurationSeconds)}" + (estimatedTotalKm(details)?.let { " · ≈ ${cardioDecimal(it)} km" } ?: ""),
                onSwipeStart = { transitionSeen = true; countdown = 3 },
                onSeePlan = { transitionSeen = true; page = 1 },
            )
            status == CardioExecutionStatus.READY && page != 1 -> CardioReadyView(
                exerciseName = exerciseName,
                steps = steps,
                plannedSeconds = plannedDurationSeconds,
                hasLiveHr = hr != null,
                gpsMode = gpsMode,
                gpsState = gpsState,
                keepScreenOn = details.keepScreenOn,
                figure = cardioFigureSpec(details.type, steps.firstOrNull()?.block?.speedKmh ?: figureSpeedKmh),
                onStart = { countdown = 3 },
                onSeePlan = { page = 1 },
            )
            else -> Column(Modifier.fillMaxSize()) {
                // El contexto: máquina o plan, paso y vuelta, con el punto de la zona activa.
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Text(exerciseName, style = CSyne.copy(fontSize = 17.sp, lineHeight = 22.sp), color = WizardColors.text)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(cardioZoneColor(shownZone ?: targetZone)))
                        Text(listOfNotNull(
                            step?.let { it.name + (it.round?.takeIf { rounds > 1 }?.let { r -> " · Vuelta $r de $rounds" } ?: "") },
                            "Paso ${stepIndex + 1} de ${steps.size}",
                        ).joinToString(" · "), style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .pointerInput(locked) {
                            if (locked) return@pointerInput
                            var dx = 0f
                            detectHorizontalDragGestures(onDragEnd = {
                                if (abs(dx) > 40f) page = (page + if (dx < 0) 1 else -1).coerceIn(0, 2)
                                dx = 0f
                            }) { _, amount -> dx += amount }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val tick = measures.tick
                    when (page) {
                        1 -> CardioPlanPage(steps, if (status == CardioExecutionStatus.READY) -1 else stepIndex, plannedDurationSeconds)
                        2 -> CardioSummaryPage(
                            elapsed = durationSeconds, planned = plannedDurationSeconds,
                            distance = recordedDistanceKm?.let { cardioDecimal(it) } ?: estimatedKm?.let { "≈ ${cardioDecimal(it)}" } ?: "—",
                            distanceNote = if (gpsHasData) "km · GPS" else if (estimatedKm != null) "km · cinta (estimada)" else "km",
                            avgHr = avgHr, avgHrNote = if (watchAvgHr == null && manualAvgHr != null) "lpm · manual" else null,
                            kcal = kcal, zoneSeconds = measures.zoneSeconds,
                            inZonePercent = measures.stepSamples.sum().takeIf { it > 0 }?.let { measures.stepInZone.sum() * 100 / it },
                        )
                        else -> CardioNowPage(
                            step = step,
                            nextStep = steps.getOrNull(stepIndex + 1),
                            steps = steps,
                            stepIndex = stepIndex,
                            stepFraction = stepFraction,
                            stepLeft = stepLeft,
                            totalLeft = totalLeft,
                            ringZone = shownZone ?: targetZone,
                            hr = hr,
                            maxHr = maxHr,
                            shownZone = shownZone,
                            plannedRpe = plannedRpe,
                            rpeAnchor = CardioGuideEngine.rpeAnchor(guide.rpeTargetExact),
                            inZoneSeconds = measures.stepInZone.getOrNull(stepIndex) ?: 0,
                            hrDrop = measures.stepStartHr?.let { start -> hr?.let { start - it } }?.takeIf { measures.stepElapsed >= 60 && it > 0 },
                            gpsMode = gpsMode,
                            gpsState = gpsState,
                            targetDistanceKm = details.targetDistanceKm,
                            distanceKm = recordedDistanceKm,
                            rounds = rounds,
                            figure = figure,
                            figureCadence = cardioFigureCadence(figure.kind, targetZone,
                                moving = running, rpm = step?.block?.rpm),
                            figureDimmed = status == CardioExecutionStatus.PAUSED || (gpsMode && gpsState?.status == CardioGpsStatus.PAUSED),
                            hrIsManual = hrIsManual,
                            hrFromWatch = watchHr != null,
                            manualHrAgeSeconds = manualHrAgeSeconds,
                            onAddManualHr = { showManualHrSheet = true },
                        )
                    }
                    // Bloqueo: la pantalla no responde a toques accidentales; el dock enseña «Mantén para desbloquear».
                    if (locked) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .semantics { contentDescription = "Pantalla bloqueada" }.testTag("cardio-locked"))
                }
                val paused = status == CardioExecutionStatus.PAUSED || (gpsMode && gpsState?.status == CardioGpsStatus.PAUSED)
                CardioDock(
                    page = page,
                    onPage = { page = it },
                    locked = locked,
                    mainLabel = if (running) "Pausa" else if (status == CardioExecutionStatus.READY) "Empezar" else "Reanudar",
                    mainIcon = if (running) KpknLineIcons.Pause else KpknLineIcons.Play,
                    mainEnabled = gpsState?.status != CardioGpsStatus.REQUESTING_PERMISSION,
                    onMain = { if (running) pause() else if (status == CardioExecutionStatus.READY) countdown = 3 else startOrResume() },
                    onLock = { locked = true },
                    onUnlock = { locked = false },
                    onSkip = if (details.hasIntervals() && (running || paused)) onSkipBlock else null,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                )
                if (isGpsDeniedOrDisabled) {
                    Text("Ubicación no disponible · reintentar permiso", Modifier.align(Alignment.CenterHorizontally).heightIn(min = 40.dp)
                        .clickable(role = Role.Button, onClick = onRequestGps).padding(10.dp), style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                }
            }
        }
        val pausedNow = status == CardioExecutionStatus.PAUSED || (gpsMode && gpsState?.status == CardioGpsStatus.PAUSED)
        if (pausedNow && !finishing && countdown == 0) {
            CardioPauseVeil(measures.pausedSeconds, cardioClock(stepLeft), onResume = { startOrResume() }, onFinish = { beginFinish() })
        }
        if (countdown > 0) {
            val first = steps.getOrNull(stepIndex)
            CardioCountdown(countdown, listOfNotNull(first?.name, first?.seconds?.let(::cardioClock), first?.cue?.takeIf { it.isNotBlank() }).joinToString(" · "))
        }
    }

    if (showDistancePicker) {
        com.example.kpkn.screens.sessioneditor.components.CardioDistanceWheelDialog(
            title = "Distancia",
            initialKm = distanceText.replace(',', '.').toDoubleOrNull(),
            accentColor = WizardColors.text,
            onDismiss = { showDistancePicker = false },
            onConfirm = { km ->
                distanceText = km?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }.orEmpty()
                showDistancePicker = false
            },
        )
    }
    if (showManualHrSheet) {
        CardioManualHeartRateSheet(
            initial = manualHr,
            maxHr = maxHr,
            onSave = { bpm ->
                manualHr = bpm
                manualHrAtSeconds = durationSeconds
                measures.manualSum += bpm
                measures.manualCount++
                measures.hrMax = maxOf(measures.hrMax, bpm)
            },
            onDismiss = { showManualHrSheet = false },
        )
    }
    if (showHeartRatePicker) {
        com.example.kpkn.screens.sessioneditor.components.CardioIntWheelDialog(
            title = "FC media",
            initial = heartRateText.toIntOrNull() ?: manualAvgHr ?: 140,
            range = 80..220,
            unit = "bpm",
            accentColor = WizardColors.text,
            onDismiss = { showHeartRatePicker = false },
            onConfirm = { bpm ->
                heartRateText = bpm?.toString().orEmpty()
                showHeartRatePicker = false
            },
            allowZeroAsNone = true,
        )
    }
}

private fun estimatedTotalKm(details: CardioDetails): Double? =
    if (!details.hasIntervals()) details.targetDistanceKm
    else CardioIntervalEngine.expandedBlocks(details).sumOf { (it.speedKmh ?: 0.0) * it.durationSeconds / 3600.0 }.takeIf { it > 0.0 }

/**
 * Página «Ahora»: el recuadro del reloj con la figura, el pulso y su objetivo, la cinta, el estado, los mandos de la máquina (con
 * «Añadir pulso» sin reloj), el siguiente paso y el perfil.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioNowPage(
    step: CardioStep?,
    nextStep: CardioStep?,
    steps: List<CardioStep>,
    stepIndex: Int,
    stepFraction: Float,
    stepLeft: Int,
    totalLeft: Int,
    ringZone: Int,
    hr: Int?,
    maxHr: Int?,
    shownZone: Int?,
    plannedRpe: Int,
    rpeAnchor: String,
    inZoneSeconds: Int,
    hrDrop: Int?,
    gpsMode: Boolean,
    gpsState: CardioGpsState?,
    targetDistanceKm: Double?,
    distanceKm: Double?,
    rounds: Int,
    figure: CardioFigureSpec,
    figureCadence: Float,
    figureDimmed: Boolean,
    hrIsManual: Boolean,
    hrFromWatch: Boolean,
    manualHrAgeSeconds: Int,
    onAddManualHr: () -> Unit,
) {
    val zone = step?.zone ?: 2
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelHeight = cardioClockPanelHeight(maxHeight, .46f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val byDistance = gpsMode && targetDistanceKm != null && targetDistanceKm > 0
            val kmLeft = if (byDistance) (targetDistanceKm!! - (distanceKm ?: 0.0)).coerceAtLeast(0.0) else null
            CardioClockPanel(
                label = kmLeft?.let { cardioDecimal(it, 2) } ?: cardioClock(stepLeft),
                eyebrow = step?.let { s -> (s.name + (s.round?.takeIf { rounds > 1 }?.let { " · $it de $rounds" } ?: "")).uppercase() } ?: "CARDIO",
                trailing = if (kmLeft != null) "DE ${cardioDecimal(targetDistanceKm!!, 2)} KM" else "QUEDAN ${cardioClock(totalLeft)}",
                sub = if (kmLeft != null) "km restantes" else if (steps.size > 1) "restante del paso" else "restante",
                fraction = if (kmLeft != null) (1.0 - kmLeft / targetDistanceKm!!).toFloat() else stepFraction,
                zoneColor = cardioZoneColor(ringZone),
                figure = figure,
                cadence = figureCadence,
                dimmed = figureDimmed,
                modifier = Modifier.height(panelHeight),
            )
            // `.vitals`: el pulso a la izquierda y el objetivo a la derecha (sin pulso, la guía por esfuerzo).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (gpsMode && gpsState?.paceSecondsPerKm != null && hr == null) KpknLineIcons.Run else KpknLineIcons.Pulse, null,
                        Modifier.size(20.dp).padding(bottom = 4.dp), tint = if (hr != null) WizardColors.text else LiveSheetTokens.Faint)
                    val pace = gpsState?.paceSecondsPerKm?.takeIf { gpsMode && hr == null }
                    Text(pace?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: hr?.toString() ?: "—",
                        style = CSyne.copy(fontSize = 30.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.text, modifier = Modifier.testTag("cardio-hr"))
                    Text(if (pace != null) "/km" else if (hr != null) (if (hrIsManual) "lpm · manual" else "lpm") else "sin pulso", Modifier.padding(bottom = 6.dp),
                        style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (hr != null) {
                        Text("OBJETIVO", style = CBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
                        Text("Zona $zone · ${CardioZoneNames[zone - 1]}", style = CBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
                        Text(maxHr?.let { "${it * (40 + zone * 10) / 100}–${it * (50 + zone * 10) / 100} lpm" } ?: "${40 + zone * 10}–${50 + zone * 10} % FC máx",
                            style = CBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
                    } else {
                        Text("GUÍA POR ESFUERZO", style = CBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
                        Text("RPE ${(plannedRpe - 1).coerceAtLeast(1)}–$plannedRpe · $rpeAnchor", style = CBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = WizardColors.text)
                        Text("Lo planificado es una guía", style = CBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
                    }
                }
            }
            if (hr != null && maxHr != null) {
                CardioTape(listOf("Z1", "Z2", "Z3", "Z4", "Z5"), CardioZoneColors, setOf(zone - 1), cardioNeedlePercent(hr, maxHr))
            } else {
                val target = setOf((plannedRpe - 2).coerceAtLeast(0), plannedRpe - 1)
                CardioTape((1..10).map { "$it" }, List(10) { WizardColors.text }, target, null)
            }
            when {
                hr == null -> CardioStatusLine(KpknLineIcons.Info, "Sin datos de pulso", "Añádelo a mano", null)
                maxHr == null -> CardioStatusLine(KpknLineIcons.Info, "$hr lpm", "Añade tu edad para ver la zona", null)
                shownZone == null || shownZone == zone -> CardioStatusLine(KpknLineIcons.Check,
                    if (step?.block?.type == CardioBlockType.RECOVER) "Recuperando bien" else "En tu zona",
                    if (step?.block?.type == CardioBlockType.RECOVER && hrDrop != null) "−$hrDrop lpm en 1 min" else "${cardioClock(inZoneSeconds)} en Z$zone", null)
                shownZone > zone -> CardioStatusLine(KpknLineIcons.ChevronDown, "Baja el ritmo", "$hr lpm · sobre Z$zone", cardioZoneColor(shownZone))
                else -> CardioStatusLine(KpknLineIcons.ChevronUp, "Sube el ritmo", "$hr lpm · bajo Z$zone", cardioZoneColor(shownZone))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                step?.cue?.takeIf { it.isNotBlank() }?.let { CardioChip(it, icon = cardioFigureIcon(figure.kind)) }
                step?.block?.inclinePercent?.let { CardioChip("${cardioDecimal(it, 0)} %", icon = KpknLineIcons.TrendUp) }
                if (gpsMode) CardioChip(when (gpsState?.status) {
                    CardioGpsStatus.RECORDING -> "GPS activo"
                    CardioGpsStatus.SIGNAL_LOST -> "Sin señal GPS"
                    CardioGpsStatus.PAUSED -> "GPS en pausa"
                    else -> "GPS"
                }, icon = KpknLineIcons.Route)
                // Con reloj, el pulso llega solo (dato); sin él, la ficha abre la hoja para anotarlo y luego actualizarlo.
                when {
                    hrFromWatch -> CardioChip("Reloj", icon = KpknLineIcons.Pulse, tag = "cardio-hr-source")
                    hrIsManual -> CardioChip(cardioManualHeartRateLabel(manualHrAgeSeconds), icon = KpknLineIcons.Pulse, onClick = onAddManualHr,
                        actionIcon = KpknLineIcons.Edit, tag = "cardio-hr-manual")
                    else -> CardioChip("Añadir pulso", icon = KpknLineIcons.Pulse, onClick = onAddManualHr, actionIcon = KpknLineIcons.Plus,
                        tag = "cardio-hr-add")
                }
            }
            nextStep?.let { next ->
                CardioChip(listOfNotNull(next.name, cardioClock(next.seconds), next.cue.takeIf { it.isNotBlank() }).joinToString(" · "),
                    Modifier.fillMaxWidth(), label = "Siguiente")
            }
            CardioProfile(steps, stepIndex, stepFraction)
        }
    }
}

/** Página «Plan»: todos los pasos con su zona, consigna y duración; el actual destaca y los hechos se atenúan. */
@Composable
private fun CardioPlanPage(steps: List<CardioStep>, current: Int, plannedSeconds: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp).testTag("cardio-plan")) {
        CardioPageTitle("PLAN · ${steps.size} ${if (steps.size == 1) "PASO" else "PASOS"} · ${plannedSeconds / 60} MIN")
        steps.forEachIndexed { i, step ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (i == current) Color(0x0FFFFFFF) else Color.Transparent)
                    .alpha(if (current >= 0 && i < current) .42f else 1f).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(cardioZoneColor(step.zone)))
                Text(step.name + (step.round?.let { " $it" } ?: ""), Modifier.weight(1f), style = CBody.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = WizardColors.text)
                Text("Z${step.zone}" + (step.cue.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = CBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
                Text(cardioClock(step.seconds), Modifier.width(42.dp), style = CSyne.copy(fontSize = 12.sp), color = WizardColors.text, textAlign = TextAlign.End)
            }
        }
    }
}

/** Página «Resumen»: solo datos medidos; la energía lleva «≈». Tiempo por zona y el porcentaje dentro del objetivo. */
@Composable
private fun CardioSummaryPage(elapsed: Int, planned: Int, distance: String, distanceNote: String, avgHr: Int?, kcal: Double?, zoneSeconds: IntArray,
    inZonePercent: Int?, avgHrNote: String? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp).testTag("cardio-summary")) {
        CardioPageTitle("EN CURSO · DATOS MEDIDOS")
        CardioSumGrid(listOf(
            Triple("TIEMPO ACTIVO", cardioClock(elapsed), "de ${cardioClock(planned)}"),
            Triple("DISTANCIA", distance, distanceNote),
            Triple("FC MEDIA", avgHr?.toString() ?: "—", if (avgHr != null) avgHrNote ?: "lpm" else "sin pulso"),
            Triple("ENERGÍA", kcal?.let { "≈ ${it.roundToInt()}" } ?: "—", if (kcal != null) "kcal estimadas" else "sin peso corporal"),
        ))
        CardioPageTitle("TIEMPO POR ZONA", Modifier.padding(top = 14.dp))
        val total = zoneSeconds.sum().coerceAtLeast(1)
        (5 downTo 1).forEach { z ->
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Z$z", Modifier.width(22.dp), style = CSyne.copy(fontSize = 11.sp), color = WizardColors.text)
                BoxWithConstraints(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x0FFFFFFF))) {
                    Box(Modifier.fillMaxHeight().width(maxWidth * (zoneSeconds[z - 1].toFloat() / total)).background(cardioZoneColor(z)))
                }
                Text(cardioClock(zoneSeconds[z - 1]), Modifier.width(40.dp), style = CBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.End)
            }
        }
        CardioStatusLine(if (inZonePercent != null) KpknLineIcons.Check else KpknLineIcons.Info,
            inZonePercent?.let { "$it % del trabajo en tu zona objetivo" } ?: "Sin pulso: no hay tiempo en zona medido", null, null, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun CardioSumGrid(cells: List<Triple<String, String, String>>, onClick: List<(() -> Unit)?> = emptyList()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, (label, value, note) ->
                    val action = onClick.getOrNull(row * 2 + col)
                    CardioSumCell(label, value, note, Modifier.weight(1f).then(if (action != null) Modifier.clickable(role = Role.Button, onClick = action) else Modifier))
                }
            }
        }
    }
}

/** «Antes de empezar»: el recuadro con la duración y la figura, las comprobaciones (pulso, lugar, pantalla), el primer bloque y «Empezar». */
@Composable
private fun CardioReadyView(
    exerciseName: String,
    steps: List<CardioStep>,
    plannedSeconds: Int,
    hasLiveHr: Boolean,
    gpsMode: Boolean,
    gpsState: CardioGpsState?,
    keepScreenOn: Boolean,
    figure: CardioFigureSpec,
    onStart: () -> Unit,
    onSeePlan: () -> Unit,
) {
    val first = steps.firstOrNull()
    Column(Modifier.fillMaxSize().testTag("cardio-ready")) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val panelHeight = cardioClockPanelHeight(maxHeight, .40f)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exerciseName, style = CSyne.copy(fontSize = 17.sp, lineHeight = 22.sp), color = WizardColors.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(cardioZoneColor(first?.zone ?: 2)))
                    Text("Antes de empezar · ${steps.size} ${if (steps.size == 1) "paso" else "pasos"}", style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                }
                // Antes de empezar, la figura espera quieta en su máquina: se mueve al arrancar.
                CardioClockPanel(label = cardioClock(plannedSeconds), eyebrow = "DURACIÓN", trailing = "EMPIEZA EN Z${first?.zone ?: 2}", sub = null,
                    fraction = null, zoneColor = cardioZoneColor(first?.zone ?: 2), figure = figure, cadence = 0f,
                    modifier = Modifier.padding(vertical = 4.dp).height(panelHeight))
                CardioStatusLine(KpknLineIcons.Pulse, "Pulso", if (hasLiveHr) "Reloj · listo" else "Sin reloj · guía por esfuerzo", null)
                CardioStatusLine(KpknLineIcons.Route, "Lugar", when {
                    !gpsMode -> "Interior · sin GPS"
                    gpsState?.status == CardioGpsStatus.PERMISSION_DENIED -> "Exterior · falta permiso de ubicación"
                    gpsState?.status == CardioGpsStatus.LOCATION_DISABLED -> "Exterior · ubicación desactivada"
                    else -> "Exterior · GPS"
                }, null)
                CardioStatusLine(KpknLineIcons.Sun, "Pantalla", if (keepScreenOn) "Siempre encendida" else "Se apaga sola", null)
                first?.cue?.takeIf { it.isNotBlank() }?.let {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(KpknLineIcons.Sliders, null, Modifier.size(16.dp), tint = LiveSheetTokens.Muted)
                        Text("Primer bloque: ajusta a $it", style = CBody.copy(fontSize = 12.sp), color = WizardColors.text)
                    }
                }
                CardioProfile(steps, -1, 0f)
                Text("Ver el plan", Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onSeePlan).padding(vertical = 10.dp)
                    .testTag("cardio-ready-plan"), style = CBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
            }
        }
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.fillMaxWidth().padding(10.dp).clip(shape).background(Brush.linearGradient(listOf(Color(0xC2333333), Color(0xB3212121))))
                .border(1.dp, Color(0x24FFFFFF), shape).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RestRef02Button("Empezar", onStart, primary = true, modifier = Modifier.fillMaxWidth(), height = 52.dp, icon = KpknLineIcons.Play, tag = "cardio-start")
            Text("Empieza con una cuenta atrás de 3 segundos", Modifier.padding(top = 7.dp), style = CBody.copy(fontSize = 10.5.sp), color = LiveSheetTokens.Faint)
        }
    }
}

/** «Cardio terminado»: lo medido, el cumplimiento por paso y el RPE real (sin marcar hasta que se elige) para guardar. */
@Composable
private fun CardioFinishView(
    exerciseName: String,
    durationSeconds: Int,
    plannedSeconds: Int,
    pausedSeconds: Int,
    distanceKm: Double?,
    estimatedKm: Double?,
    distanceSource: String,
    avgHr: Int?,
    maxHr: Int?,
    kcal: Double?,
    steps: List<CardioStep>,
    stepInZone: IntArray,
    stepSamples: IntArray,
    rpe: Int?,
    plannedRpe: Int,
    onRpe: (Int) -> Unit,
    onEditDistance: (() -> Unit)?,
    onEditHeartRate: (() -> Unit)?,
    saving: Boolean,
    failed: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag("cardio-finish-view")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(exerciseName.uppercase(), style = CBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
            Text("Cardio terminado", Modifier.padding(top = 6.dp, bottom = 12.dp), style = CSyne.copy(fontSize = 26.sp, lineHeight = 34.sp), color = WizardColors.text)
            CardioSumGrid(
                listOf(
                    Triple("TIEMPO ACTIVO", cardioClock(durationSeconds), if (pausedSeconds > 0) "pausa ${cardioClock(pausedSeconds)} aparte" else "de ${cardioClock(plannedSeconds)}"),
                    Triple("DISTANCIA", distanceKm?.let { cardioDecimal(it) } ?: estimatedKm?.let { "≈ ${cardioDecimal(it)}" } ?: "—",
                        if (distanceKm == null && estimatedKm == null) "toca para añadir" else distanceSource),
                    Triple("FC MEDIA", avgHr?.toString() ?: "—", avgHr?.let { "lpm" + (maxHr?.let { m -> " · máx $m" } ?: "") } ?: "toca para añadir"),
                    Triple("ENERGÍA", kcal?.let { "≈ ${it.roundToInt()}" } ?: "—", if (kcal != null) "kcal estimadas" else "sin peso corporal"),
                ),
                onClick = listOf(null, onEditDistance, onEditHeartRate, null),
            )
            CardioPageTitle("CUMPLIMIENTO POR PASO", Modifier.padding(top = 14.dp))
            val measured = stepSamples.any { it > 0 }
            Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                steps.forEachIndexed { i, step ->
                    val samples = stepSamples.getOrElse(i) { 0 }
                    val pct = if (samples > 0) stepInZone[i] * 100 / samples else null
                    Box(Modifier.weight(step.seconds.toFloat().coerceAtLeast(1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp))
                        .background(cardioZoneColor(step.zone).copy(alpha = when {
                            pct == null -> .25f
                            pct >= 85 -> 1f
                            pct >= 60 -> .7f
                            else -> .45f
                        })), contentAlignment = Alignment.Center) {
                        if (pct != null && pct < 85) Text("$pct %", style = CSyne.copy(fontSize = 9.sp), color = Color(0xFF111111))
                    }
                }
            }
            Text(
                if (!measured) "Sin pulso: no hay cumplimiento medido por paso."
                else {
                    val inside = steps.indices.count { i -> stepSamples[i] > 0 && stepInZone[i] * 100 / stepSamples[i] >= 85 }
                    val worst = steps.indices.filter { stepSamples[it] > 0 }.minByOrNull { stepInZone[it] * 100 / stepSamples[it] }
                    "$inside de ${steps.count { true }} pasos dentro del objetivo" + (worst?.takeIf { stepInZone[it] * 100 / stepSamples[it] < 85 }?.let {
                        " · ${steps[it].name}${steps[it].round?.let { r -> " $r" } ?: ""} se quedó en ${stepInZone[it] * 100 / stepSamples[it]} %"
                    } ?: "")
                },
                Modifier.padding(top = 6.dp), style = CBody.copy(fontSize = 10.5.sp, lineHeight = 15.sp), color = LiveSheetTokens.Faint,
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CardioPageTitle("¿CUÁNTO TE COSTÓ? · ESFUERZO REAL", Modifier.weight(1f).padding(bottom = 0.dp))
                Text(rpe?.let { "RPE $it" } ?: "Sin marcar", style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted,
                    modifier = Modifier.testTag("cardio-rpe-label"))
            }
            CardioRpePicker(rpe, plannedRpe, onRpe)
            Text("El plan ($plannedRpe) es una referencia; marca lo que sentiste.", Modifier.padding(top = 6.dp), style = CBody.copy(fontSize = 10.sp),
                color = LiveSheetTokens.Faint)
            if (failed) Text("No se pudo guardar. Tus datos se conservan; vuelve a intentarlo.", Modifier.padding(top = 8.dp),
                style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Danger)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RestRef02Button(if (saving) "Guardando…" else "Guardar cardio", onSave, primary = true, modifier = Modifier.fillMaxWidth(), height = 50.dp,
                enabled = rpe != null && !saving, tag = "cardio-save")
            if (!saving) Text("Volver al cardio", Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onBack).padding(10.dp),
                style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
        }
    }
}

/** El cardio ya registrado: lo guardado y «Actualizar cardio». */
@Composable
private fun CardioRecordedView(exerciseName: String, durationSeconds: Int, distanceKm: Double?, avgHr: Int?, rpe: Double?, onUpdate: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("cardio-recorded"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(exerciseName.uppercase(), style = CBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(WizardColors.text), contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.Check, null, Modifier.size(18.dp), tint = Color(0xFF111111))
            }
            Text("Cardio registrado", style = CSyne.copy(fontSize = 22.sp, lineHeight = 29.sp), color = WizardColors.text)
        }
        CardioSumGrid(listOf(
            Triple("TIEMPO ACTIVO", cardioClock(durationSeconds), "registrado"),
            Triple("DISTANCIA", distanceKm?.let { cardioDecimal(it) } ?: "—", "km"),
            Triple("FC MEDIA", avgHr?.toString() ?: "—", "lpm"),
            Triple("ESFUERZO", rpe?.let { "RPE ${it.roundToInt()}" } ?: "—", "real"),
        ))
        Spacer(Modifier.weight(1f))
        RestRef02Button("Actualizar cardio", onUpdate, primary = false, modifier = Modifier.fillMaxWidth(), tag = "cardio-update")
    }
}

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

private fun formatCardioDistance(distanceKm: Double?): String =
    distanceKm?.takeIf { it >= 0.0 }?.let { "%.2f km".format(it) } ?: "0.00 km"

/** Límites del pulso manual (lpm): fuera de ellos es casi seguro un error al teclear. */
internal val CardioManualHeartRateRange = 40..230

/** El pulso tecleado, o null si no es un número de lpm válido. */
internal fun cardioManualHeartRateOrNull(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in CardioManualHeartRateRange }

/** El mensaje de la hoja para lo tecleado; null si se puede guardar. */
internal fun cardioManualHeartRateError(text: String): String? = when {
    text.isBlank() -> "Escribe tu pulso en lpm."
    text.trim().toIntOrNull() == null -> "Escribe solo números (lpm)."
    cardioManualHeartRateOrNull(text) == null ->
        "El pulso tiene que estar entre ${CardioManualHeartRateRange.first} y ${CardioManualHeartRateRange.last} lpm."
    else -> null
}

/** La ficha del pulso manual: «Manual · hace 0:30» (en tiempo activo del cardio). */
internal fun cardioManualHeartRateLabel(ageSeconds: Int): String = "Manual · hace ${cardioClock(ageSeconds)}"

/**
 * «Añadir pulso» sin reloj: el campo de lpm (40–230, con aviso si no vale) y «Guardar pulso». Lo guardado se enseña con su zona como
 * el del reloj y cuenta en la FC media del cardio.
 */
@Composable
private fun CardioManualHeartRateSheet(initial: Int?, maxHr: Int?, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    var tried by remember { mutableStateOf(false) }
    val error = if (tried) cardioManualHeartRateError(text) else null
    LiveSheet(
        title = if (initial == null) "Añadir pulso" else "Actualizar pulso",
        onDismiss = onDismiss,
        eyebrow = "PULSO MANUAL",
        tag = "cardio-hr-sheet",
        footer = { close ->
            LiveSheetPrimary("Guardar pulso", {
                tried = true
                val bpm = cardioManualHeartRateOrNull(text)
                if (bpm != null) { onSave(bpm); close() }
            }, enabled = text.isNotBlank(), tag = "cardio-hr-save")
        },
    ) {
        LiveSheetSubtitle("Míralo en la pantalla de la máquina o cuenta tus latidos 15 s en la muñeca y multiplícalos por 4.")
        LiveSheetLabeledField("Pulso (lpm)", text, { value -> text = value.filter(Char::isDigit).take(3); tried = false },
            keyboardType = KeyboardType.Number, placeholder = "140", tag = "cardio-hr-input")
        val preview = cardioManualHeartRateOrNull(text)
        when {
            error != null -> LiveSheetShortCopy(error, Modifier.testTag("cardio-hr-error"), error = true)
            preview != null && maxHr != null -> {
                val zone = cardioZoneForPercent(preview * 100 / maxHr)
                LiveSheetShortCopy("Con $preview lpm estás en zona $zone · ${CardioZoneNames[zone - 1]}.")
            }
        }
    }
}
