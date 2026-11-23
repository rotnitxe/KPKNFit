package com.example.kpkn.screens.workout

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetLabeledField
import com.example.kpkn.screens.workout.components.LiveSheetPrimary
import com.example.kpkn.screens.workout.components.LiveSheetShortCopy
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknGradientArgs
import com.example.kpkn.ui.design.depth.KpknGradientLayer
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.KpknWaveEdgeFade
import com.example.kpkn.ui.design.motion.KpknWaveField
import com.example.kpkn.ui.design.motion.KpknWaveState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknTween
import com.example.kpkn.ui.design.motion.rememberKpknClockMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class CardioScreenKind { TRANS_OUT, RECORDED, FINISH, TRANS_IN, LIVE }

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
    countdownRemaining: Int = 0,
    countdownRequestId: Long? = null,
    onRequestStart: (useGps: Boolean) -> Unit = {},
    onStartTimer: () -> Unit = {},
    onCountdownCue: (remaining: Int) -> Unit = {},
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
    onNavigatePreviousExercise: (() -> Unit)? = null,
    onSkipCountdown: (() -> Unit)? = null,
    onLockedChanged: (Boolean) -> Unit = {},
    onShowExerciseContextChanged: (Boolean) -> Unit = {},
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
    var transitionSeen by rememberSaveable(details.type, details.intervalBlocks.size) { mutableStateOf(false) }
    var transitionOutSeen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cardioHaze = remember { HazeState() }

    val gpsWarningContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(gpsState?.persistenceFailed) {
        if (gpsState?.persistenceFailed == true) android.widget.Toast.makeText(gpsWarningContext,
            "No se pudo guardar la ruta GPS. La app seguirá intentando; evita cerrar el entrenamiento.",
            android.widget.Toast.LENGTH_LONG).show()
    }
    val status = executionState?.status ?: CardioExecutionStatus.READY
    val isLibre = !details.hasIntervals() && details.targetDurationSeconds == null
    val plannedDurationSeconds = if (isLibre) 20 * 60 else details.effectiveDurationSeconds().coerceAtLeast(1)
    val elapsed = executionState?.elapsedSeconds ?: 0
    val gpsMode = details.requiresGps
    val gpsHasData = gpsMode && gpsState?.let { it.pointCount > 0 || it.distanceMeters >= 10.0 } == true
    val liveDurationSeconds = cardioRecordedDurationSeconds(gpsMode, gpsState?.elapsedActiveSeconds, elapsed)
    val durationSeconds = if (liveDurationSeconds > 0) liveDurationSeconds else (completedSet?.timeSeconds ?: 0)
    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull() ?: completedSet?.distanceKm
    val recordedDistanceKm = if (gpsHasData) gpsState?.distanceMeters?.div(1_000.0) else distanceKm
    val guide = remember(details.type, details.intensity, details.intensityLevel, details.hiit) { CardioGuideEngine.guide(details) }
    val plannedRpe = guide.rpeTargetExact.roundToInt().coerceIn(1, 10)

    val steps = remember(details) { cardioSteps(details) }
    val visiblePages = if (steps.size > 1) listOf(0, 1, 2) else listOf(0, 2)
    val visiblePage = page.takeIf(visiblePages::contains) ?: 0
    LaunchedEffect(visiblePages) {
        if (page !in visiblePages) page = visiblePage
    }
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
    LaunchedEffect(countdownRequestId, countdownRemaining) {
        if (countdownRemaining in 1..3) onCountdownCue(countdownRemaining)
    }

    val watchAvgHr = if (measures.hrCount > 0) (measures.hrSum / measures.hrCount).toInt() else null
    val manualAvgHr = if (measures.manualCount > 0) (measures.manualSum / measures.manualCount).toInt() else null
    val avgHr = watchAvgHr ?: manualAvgHr
    // Al terminar: lo medido por el reloj manda; si no, lo que se corrija en «FC media» y, si no, la media de los pulsos manuales.
    val finishHr = watchAvgHr ?: heartRateText.toIntOrNull() ?: manualAvgHr ?: completedSet?.avgHeartRate
    val manualHrAgeSeconds = (durationSeconds - manualHrAtSeconds).coerceAtLeast(0)
    val estimatedKm = remember(details, durationSeconds) {
        if (details.hasIntervals()) {
            var acc = 0; var km = 0.0
            CardioIntervalEngine.expandedBlocks(details).forEach { block ->
                val inBlock = (durationSeconds - acc).coerceIn(0, block.durationSeconds)
                block.speedKmh?.let { km += it * inBlock / 3600.0 }
                acc += block.durationSeconds
            }
            km.takeIf { it > 0.0 }
        } else null
    }
    val kcal = remember(details, durationSeconds, bodyWeightKg, finishHr) {
        val kg = bodyWeightKg?.takeIf { it > 0 } ?: return@remember null
        com.example.kpkn.domain.calculations.CardioCalorieEngine.estimate(
            com.example.kpkn.domain.calculations.CardioCalorieInput(
                details = details,
                weightKg = kg,
                durationSeconds = durationSeconds,
                averageHeartRate = finishHr?.takeIf { it in 30..240 },
            ),
        ).toDouble()
    }
    val reduced = wizardReducedMotion()
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastGeneration by remember { mutableIntStateOf(0) }
    fun showToast(msg: String) {
        toastMessage = msg
        toastGeneration++
    }
    LaunchedEffect(toastGeneration) {
        if (toastMessage != null) {
            delay(1900)
            toastMessage = null
        }
    }
    var liveWaveIntroSeconds by remember { mutableFloatStateOf(0f) }
    var wasReadyOrCountdown by remember { mutableStateOf(status == CardioExecutionStatus.READY || countdownRemaining > 0) }
    LaunchedEffect(status, countdownRemaining) {
        if (status == CardioExecutionStatus.READY || countdownRemaining > 0) {
            wasReadyOrCountdown = true
        } else if (wasReadyOrCountdown && status == CardioExecutionStatus.RUNNING) {
            wasReadyOrCountdown = false
            liveWaveIntroSeconds = 1.4f
        }
    }

    val screenKind = when {
        status == CardioExecutionStatus.RECORDED && !finishing && transitionOut != null && !transitionOutSeen -> CardioScreenKind.TRANS_OUT
        status == CardioExecutionStatus.RECORDED && !finishing -> CardioScreenKind.RECORDED
        finishing -> CardioScreenKind.FINISH
        status == CardioExecutionStatus.READY && transitionIn != null && !transitionSeen -> CardioScreenKind.TRANS_IN
        else -> CardioScreenKind.LIVE
    }
    val showExerciseContext = screenKind == CardioScreenKind.LIVE && countdownRemaining <= 0
    LaunchedEffect(showExerciseContext) { onShowExerciseContextChanged(showExerciseContext) }
    LaunchedEffect(locked) { onLockedChanged(locked) }

    BoxWithConstraints(modifier.fillMaxSize().clip(RoundedCornerShape(25.dp)).background(Color.Black).testTag("cardio-space")) {
        // El degradado del lienzo se calcula sobre la pantalla completa; el host ya ha reservado la cabecera.
        val screenHeight = LocalConfiguration.current.screenHeightDp.toFloat().coerceAtLeast(1f)
        val contentHeight = maxHeight.value.coerceAtLeast(1f)
        val rootTop = (screenHeight - contentHeight).coerceAtLeast(0f)
        val gradient = KpknGradient.ResplandorAnchoZona.spec(KpknGradientArgs(zone = cardioZoneColor(shownZone ?: targetZone)))
        val radial = gradient.layers.single() as KpknGradientLayer.Radial
        val adapted = gradient.copy(layers = listOf(radial.copy(cy = (screenHeight * .24f - rootTop) / contentHeight,
            ry = radial.ry * screenHeight / contentHeight)))
        Box(Modifier.matchParentSize().kpknGradient(adapted))

        AnimatedContent(
            targetState = screenKind,
            transitionSpec = {
                if (reduced) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(kpknTween(KpknDuration(320), KpknMotion.Estandar)) togetherWith fadeOut(kpknTween(KpknDuration(230), KpknMotion.Suave))
            },
            label = "cvSwap",
        ) { currentScreen ->
          when (currentScreen) {
            // Al terminar el cardio no hay descanso: si sigue fuerza, la transición aparece enseguida con lo medido.
            CardioScreenKind.TRANS_OUT -> CardioTransitionOut(
                lead = buildList {
                    add(cardioClock(durationSeconds))
                    recordedDistanceKm?.takeIf { it > 0.0 }?.let { add("${cardioDecimal(it)} km") }
                    val samples = measures.stepSamples.sum()
                    if (samples > 0) add("${(measures.stepInZone.sum() * 100.0 / samples).roundToInt()} % en tu objetivo")
                }.joinToString(" · ", prefix = "Terminaste el cardio: ", postfix = "."),
                exerciseName = transitionOut!!.exerciseName,
                meta = transitionOut.meta,
                firstSet = transitionOut.firstSet,
                planEffort = transitionOut.planEffort,
                lastTime = transitionOut.lastTime,
                chips = transitionOut.chips,
                footnote = transitionOut.footnote,
                onContinue = { transitionOutSeen = true; transitionOut.onContinue() },
                cardioType = details.type,
                hazeState = cardioHaze,
            )
            CardioScreenKind.RECORDED -> CardioRecordedView(
                exerciseName = exerciseName, durationSeconds = durationSeconds, distanceKm = recordedDistanceKm, avgHr = completedSet?.avgHeartRate ?: avgHr,
                rpe = completedSet?.rpe, onUpdate = {
                    if (distanceText.isBlank() && completedSet?.distanceKm != null) {
                        distanceText = completedSet.distanceKm.toString()
                    }
                    if (heartRateText.isBlank() && completedSet?.avgHeartRate != null) {
                        heartRateText = completedSet.avgHeartRate.toString()
                    }
                    if (rpe == null && completedSet?.rpe != null) {
                        rpe = completedSet.rpe.roundToInt()
                    }
                    finishing = true
                },
            )
            CardioScreenKind.FINISH -> CardioFinishView(
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
                            val dur = durationSeconds.takeIf { it > 0 }
                                ?: completedSet?.timeSeconds?.takeIf { it > 0 }
                                ?: details.effectiveDurationSeconds().coerceAtLeast(1)
                            val saved = onRecordWithRpe?.invoke(dur, recordedDistanceKm ?: estimatedKm, heart, chosen.toDouble())
                                ?: onRecord(dur, recordedDistanceKm ?: estimatedKm, heart)
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
            CardioScreenKind.TRANS_IN -> Box(
                Modifier.fillMaxSize().pointerInput(onNavigatePreviousExercise) {
                    if (onNavigatePreviousExercise == null) return@pointerInput
                    var totalDx = 0f
                    val threshold = 56.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f },
                        onDragEnd = { if (totalDx > threshold) onNavigatePreviousExercise() },
                    ) { _, dx -> totalDx += dx }
                }
            ) {
                CardioTransitionIn(
                    title = "Ahora vamos\ncon cardio",
                    lead = transitionIn!!.lead,
                    exerciseName = exerciseName,
                    meta = "Cardio · ${cardioClock(plannedDurationSeconds)} · ${steps.size} ${if (steps.size == 1) "paso" else "pasos"}",
                    firstStep = steps.firstOrNull(),
                    steps = steps,
                    totalLabel = "Total ${cardioClock(plannedDurationSeconds)}" + (estimatedTotalKm(details)?.let { " · ≈ ${cardioDecimal(it)} km" } ?: ""),
                    onSwipeStart = { transitionSeen = true; onRequestStart(gpsMode) },
                    onSeePlan = { transitionSeen = true; page = 1 },
                    cardioType = details.type,
                    hazeState = cardioHaze,
                )
            }
            CardioScreenKind.LIVE -> Box(Modifier.fillMaxSize()) {
              val compact = LocalConfiguration.current.screenHeightDp < 800
              val dockBottomMargin = if (compact) 14.dp else 20.dp
              val dockApproxHeight = if (compact) 112.dp else 124.dp
              val pagesDimAlpha by animateKpknFloatAsState(if (locked) 0.50f else 1f, KpknDuration(300), KpknMotion.Suave, label = "cv-locked-dim")
              val targetIndex = visiblePages.indexOf(visiblePage).coerceAtLeast(0)
              val pagerPos = remember(visiblePages) { Animatable(targetIndex.toFloat()) }
              var isDraggingPager by remember { mutableStateOf(false) }
              var pageEnterKey by remember { mutableIntStateOf(0) }
              var previousVisiblePage by remember { mutableIntStateOf(visiblePage) }
              LaunchedEffect(visiblePage) {
                  if (visiblePage != previousVisiblePage) {
                      previousVisiblePage = visiblePage
                      pageEnterKey++
                  }
              }
              LaunchedEffect(targetIndex, isDraggingPager, reduced) {
                  if (!isDraggingPager) {
                      if (reduced) pagerPos.snapTo(targetIndex.toFloat())
                      else pagerPos.animateTo(targetIndex.toFloat(), tween(KpknDuration(420).ms, easing = KpknMotion.Estandar))
                  }
              }

              Column(Modifier.fillMaxSize().hazeSource(state = cardioHaze).graphicsLayer { alpha = pagesDimAlpha }) {
                if (status != CardioExecutionStatus.READY || visiblePage != 0) {
                    // El contexto: máquina o plan, paso y vuelta, con el punto de la zona activa.
                    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 3.dp)) {
                        Row(Modifier.heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(cardioZoneColor(shownZone ?: targetZone)))
                            Text(listOfNotNull(
                                step?.let { it.name + (it.round?.takeIf { rounds > 1 }?.let { r -> " · Vuelta $r de $rounds" } ?: "") },
                                if (steps.size > 1) "Paso ${stepIndex + 1} de ${steps.size}" else cardioClock(plannedDurationSeconds),
                            ).joinToString(" · "), style = CBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium), color = LiveSheetTokens.Muted)
                        }
                    }
                }
                val swipeThresholdPx = with(LocalDensity.current) { 50.dp.toPx() }
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth()
                        .pointerInput(locked, visiblePages, visiblePage) {
                            if (locked || visiblePages.size <= 1) return@pointerInput
                            var totalDx = 0f
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val maxIdx = visiblePages.lastIndex.toFloat()
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDraggingPager = true
                                    totalDx = 0f
                                },
                                onDragEnd = {
                                    val startIdx = visiblePages.indexOf(visiblePage).coerceAtLeast(0)
                                    if (startIdx == 0 && totalDx > swipeThresholdPx && onNavigatePreviousExercise != null) {
                                        isDraggingPager = false
                                        scope.launch { pagerPos.snapTo(0f) }
                                        onNavigatePreviousExercise()
                                        return@detectHorizontalDragGestures
                                    }
                                    val nextIdx = when {
                                        totalDx < -swipeThresholdPx -> minOf(visiblePages.lastIndex, startIdx + 1)
                                        totalDx > swipeThresholdPx -> maxOf(0, startIdx - 1)
                                        else -> startIdx
                                    }
                                    page = visiblePages[nextIdx]
                                    isDraggingPager = false
                                    scope.launch {
                                        if (reduced) pagerPos.snapTo(nextIdx.toFloat())
                                        else pagerPos.animateTo(nextIdx.toFloat(), tween(KpknDuration(420).ms, easing = KpknMotion.Estandar))
                                    }
                                },
                                onDragCancel = {
                                    isDraggingPager = false
                                    val stay = visiblePages.indexOf(visiblePage).coerceAtLeast(0).toFloat()
                                    scope.launch {
                                        pagerPos.animateTo(stay, tween(KpknDuration(420).ms, easing = KpknMotion.Estandar))
                                    }
                                },
                            ) { _, dx ->
                                totalDx += dx
                                val startIdx = visiblePages.indexOf(visiblePage).coerceAtLeast(0).toFloat()
                                val raw = startIdx - totalDx / widthPx
                                val clamped = when {
                                    raw < 0f -> raw * 0.35f
                                    raw > maxIdx -> maxIdx + (raw - maxIdx) * 0.35f
                                    else -> raw
                                }
                                scope.launch { pagerPos.snapTo(clamped) }
                            }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val tick = measures.tick
                    val currentPos = pagerPos.value
                    val settled = !isDraggingPager && abs(currentPos - targetIndex.toFloat()) < 0.001f

                    visiblePages.forEachIndexed { idx, pg ->
                        val visibleInTrack = if (settled) idx == targetIndex else abs(currentPos - idx) < 1.15f
                        if (visibleInTrack) {
                            val pageModifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    translationX = (idx - currentPos) * size.width
                                    alpha = if (settled) 1f else (1f - 0.65f * abs(idx - currentPos).coerceIn(0f, 1f))
                                }
                                .then(
                                    if (pg == visiblePage && pageEnterKey > 0) Modifier.kpknAnimate(KpknFamilies.EntradaSube.demo, restartKey = pageEnterKey)
                                    else Modifier,
                                )
                            Box(pageModifier) {
                                when (pg) {
                                    1 -> CardioPlanPage(steps, if (status == CardioExecutionStatus.READY) -1 else stepIndex, plannedDurationSeconds, stepFraction)
                                    2 -> CardioSummaryPage(
                                        elapsed = durationSeconds, planned = plannedDurationSeconds,
                                        distance = recordedDistanceKm?.let { cardioDecimal(it) } ?: estimatedKm?.let { "≈ ${cardioDecimal(it)}" } ?: "—",
                                        distanceNote = if (gpsHasData) "km · GPS" else if (estimatedKm != null) "km · cinta (estimada)" else "km",
                                        avgHr = avgHr, avgHrNote = if (watchAvgHr == null && manualAvgHr != null) "lpm · manual" else null,
                                        kcal = kcal, zoneSeconds = measures.zoneSeconds,
                                        inZonePercent = measures.stepSamples.sum().takeIf { it > 0 }?.let { measures.stepInZone.sum() * 100 / it },
                                        hasHeartRate = watchHr != null, hasManualHeartRate = hrIsManual,
                                        onAddManualHr = { showManualHrSheet = true },
                                    )
                                    else -> if (status == CardioExecutionStatus.READY) {
                                        CardioReadyPulido(
                                            steps = steps,
                                            plannedSeconds = plannedDurationSeconds,
                                            hasLiveHr = hr != null,
                                            gpsMode = gpsMode,
                                            gpsState = gpsState,
                                            keepScreenOn = details.keepScreenOn,
                                            hazeState = cardioHaze,
                                            onStart = { onRequestStart(gpsMode) },
                                            onSeePlan = { page = 1 },
                                            showBottomButton = false,
                                            onRetryGps = if (gpsMode) onRequestGps else null,
                                            onStartWithoutGps = if (isGpsDeniedOrDisabled) ({ onRequestStart(false) }) else null,
                                        )
                                    } else {
                                        CardioNowContent(
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
                                            paused = status == CardioExecutionStatus.PAUSED || (gpsMode && gpsState?.status == CardioGpsStatus.PAUSED),
                                            hrIsManual = hrIsManual,
                                            hrFromWatch = watchHr != null,
                                            manualHrAgeSeconds = manualHrAgeSeconds,
                                            onAddManualHr = { showManualHrSheet = true },
                                            elapsed = durationSeconds,
                                            kcal = kcal,
                                            estimatedKm = estimatedKm,
                                            onRetryGps = if (gpsMode) ({ onRequestGps(); showToast("Solicitando GPS…") }) else null,
                                            introSeconds = liveWaveIntroSeconds,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Bloqueo: la pantalla no responde a toques accidentales; el dock enseña «Mantén para desbloquear».
                    if (locked) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .semantics { contentDescription = "Pantalla bloqueada" }.testTag("cardio-locked"))
                }
              }
                if (locked) {
                    CardioLockCapsule(
                        cardioHaze,
                        Modifier.align(Alignment.BottomCenter).padding(bottom = dockApproxHeight + dockBottomMargin + 12.dp),
                    )
                }
                toastMessage?.let { msg ->
                    CardioToastPill(
                        msg,
                        cardioHaze,
                        Modifier.align(Alignment.BottomCenter).padding(bottom = dockApproxHeight + dockBottomMargin + 12.dp),
                    )
                }
                val paused = status == CardioExecutionStatus.PAUSED || (gpsMode && gpsState?.status == CardioGpsStatus.PAUSED)
                CardioDock(
                    page = visiblePage,
                    visiblePages = visiblePages,
                    onPage = { page = it },
                    locked = locked,
                    mainLabel = if (running) "Pausa" else if (status == CardioExecutionStatus.READY) "Empezar" else "Reanudar",
                    mainIcon = if (running) KpknLineIcons.Pause else KpknLineIcons.Play,
                    mainEnabled = gpsState?.status != CardioGpsStatus.REQUESTING_PERMISSION,
                    onMain = { if (running) pause() else if (status == CardioExecutionStatus.READY) onRequestStart(gpsMode) else startOrResume() },
                    onLock = { locked = true },
                    onUnlock = { locked = false },
                    onSkip = if (details.hasIntervals() && (running || paused)) ({
                        val upcoming = steps.getOrNull(stepIndex + 1)
                        onSkipBlock()
                        if (upcoming != null) showToast("Paso ${stepIndex + 2} de ${steps.size} · ${upcoming.name}")
                    }) else null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = dockBottomMargin),
                    hazeState = cardioHaze,
                    mainTag = if (status == CardioExecutionStatus.READY) "cardio-start" else "cardio-main",
                )
            }
          }
        }
        val pausedNow = status == CardioExecutionStatus.PAUSED || (gpsMode && gpsState?.status == CardioGpsStatus.PAUSED)
        if (pausedNow && !finishing && countdownRemaining <= 0) {
            val compact = LocalConfiguration.current.screenHeightDp < 800
            CardioPauseVeil(
                measures.pausedSeconds,
                cardioClock(stepLeft),
                onResume = { startOrResume() },
                onFinish = { beginFinish() },
                hazeState = cardioHaze,
                bottomClearance = if (compact) 116.dp else 134.dp,
            )
        }
        if (gpsMode && gpsState?.persistenceFailed == true) {
            Text(
                text = "No se pudo guardar la ruta GPS. Reintentando…",
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                    .background(Color(0xFF5B1F1F), RoundedCornerShape(8.dp)).padding(10.dp)
                    .testTag("cardio-gps-save-warning"),
                color = Color.White,
            )
        }
        if (countdownRemaining > 0) {
            val first = steps.getOrNull(stepIndex)
            CardioCountdown(
                countdownRemaining,
                listOfNotNull(first?.name, first?.seconds?.let(::cardioClock), first?.cue?.takeIf { it.isNotBlank() }).joinToString(" · "),
                zoneColor = cardioZoneColor(shownZone ?: targetZone),
                onSkip = onSkipCountdown,
            )
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
                showToast("Pulso guardado · $bpm lpm")
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
/** Página Plan: perfil y línea temporal con el paso actual y los ya completados. */
@Composable
private fun CardioPlanPage(steps: List<CardioStep>, current: Int, plannedSeconds: Int, progress: Float) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 166.dp).testTag("cardio-plan")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Plan", style = CSyne.copy(fontSize = 18.sp, lineHeight = 22.sp), color = WizardColors.text)
            Text("${steps.size} ${if (steps.size == 1) "paso" else "pasos"} \u00b7 ${(plannedSeconds / 60f).roundToInt()} min", style = CBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Faint)
        }
        CardioProfile(steps, current, progress, Modifier.padding(top = 10.dp), height = 50.dp)
        val activeProgress by animateKpknFloatAsState(progress.coerceIn(0f, 1f), KpknDuration(900), KpknMotion.Lineal, label = "cv-plan-bar")
        Column(Modifier.padding(top = 8.dp).drawBehind {
            val x = 11.dp.toPx()
            drawLine(Color(0x12FFFFFF), androidx.compose.ui.geometry.Offset(x, 14.dp.toPx()),
                androidx.compose.ui.geometry.Offset(x, size.height - 14.dp.toPx()), 2.dp.toPx())
        }) {
            steps.forEachIndexed { i, step ->
                val done = current >= 0 && i < current
                val active = i == current
                val zoneColor = cardioZoneColor(step.zone)
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (active) Color(0x0CFFFFFF) else Color.Transparent)
                    .drawBehind { if (active) drawRect(zoneColor, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width * activeProgress, 2.dp.toPx())) }
                    .padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (done) 20.dp else if (active) 14.dp else 10.dp).clip(CircleShape)
                            .background(if (done) Color(0x2443D18C) else zoneColor), contentAlignment = Alignment.Center) {
                            if (done) Icon(KpknLineIcons.Check, null, Modifier.size(12.dp), tint = Color(0xFF43D18C))
                        }
                    }
                    Column(Modifier.weight(1f).alpha(if (done) .5f else 1f)) {
                        Text(step.name + (step.round?.let { " $it" } ?: ""), style = CBody.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
                        Text("Z${step.zone} \u00b7 ${CardioZoneNames[step.zone - 1]}" + step.cue.takeIf { it.isNotBlank() }?.let { " \u00b7 $it" }.orEmpty() +
                            step.block?.inclinePercent?.let { " \u00b7 ${cardioDecimal(it, if (it % 1.0 == 0.0) 0 else 1)} %" }.orEmpty(),
                            style = CBody.copy(fontSize = 11.5.sp), color = LiveSheetTokens.Faint)
                    }
                    Text(cardioClock(step.seconds), Modifier.alpha(if (done) .5f else 1f), style = CSyne.copy(fontSize = 14.sp), color = WizardColors.text)
                }
            }
        }
    }
}

/** Página Datos: mediciones reales con `.cv-cell` y animación `cv-bar` escalonada en las zonas. */
@Composable
private fun CardioSummaryPage(elapsed: Int, planned: Int, distance: String, distanceNote: String, avgHr: Int?, kcal: Double?, zoneSeconds: IntArray,
    inZonePercent: Int?, avgHrNote: String? = null, hasHeartRate: Boolean, hasManualHeartRate: Boolean = false, onAddManualHr: () -> Unit) {
    val reduced = wizardReducedMotion()
    val barsClock = rememberKpknClockMs(running = hasHeartRate && !reduced, key = "cv-summary-bars", endMs = 1160f)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 166.dp).testTag("cardio-summary")) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Datos", style = CSyne.copy(fontSize = 18.sp, lineHeight = 22.sp), color = WizardColors.text)
            Text("medidos hasta ahora", style = CBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Faint)
        }
        val cells = listOf(
            Triple("TIEMPO ACTIVO", cardioClock(elapsed), "de ${cardioClock(planned)}"),
            Triple("DISTANCIA", distance, distanceNote),
            Triple("FC MEDIA", avgHr?.toString() ?: "—", if (avgHr != null) avgHrNote ?: "lpm" else "sin pulso"),
            Triple("ENERGÍA", kcal?.let { "≈ ${it.roundToInt()}" } ?: "—", if (kcal != null) "kcal estimadas" else "sin peso corporal"),
        )
        CardioSumGrid(cells)
        Text("TIEMPO POR ZONA", Modifier.padding(top = 18.dp), style = CBody.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
        if (hasHeartRate) {
            val total = zoneSeconds.sum().coerceAtLeast(1)
            Column(Modifier.padding(top = 8.dp)) {
                (5 downTo 1).forEachIndexed { idx, z ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Z$z", Modifier.width(24.dp), style = CSyne.copy(fontSize = 12.sp), color = WizardColors.text)
                        BoxWithConstraints(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x0FFFFFFF))) {
                            val ratio = (zoneSeconds[z - 1].toFloat() / total).coerceIn(0f, 1f)
                            Box(
                                Modifier.fillMaxHeight().width(maxWidth * ratio)
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        if (!reduced) {
                                            val p = ((barsClock.value - (100f + idx * 60f)) / 800f).coerceIn(0f, 1f)
                                            scaleX = KpknMotion.Estandar.transform(p)
                                        }
                                    }
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(cardioZoneColor(z)),
                            )
                        }
                        Text(cardioClock(zoneSeconds[z - 1]), Modifier.width(42.dp), style = CBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.End)
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val measuredTotal = zoneSeconds.sum() > 0 && inZonePercent != null
                Icon(if (measuredTotal) KpknLineIcons.Check else KpknLineIcons.Info, null, Modifier.size(17.dp),
                    tint = if (measuredTotal) WizardColors.text else LiveSheetTokens.Muted)
                Text(
                    inZonePercent?.takeIf { zoneSeconds.sum() > 0 }?.let { "$it % del trabajo en tu zona objetivo" } ?: "Aún no hay tiempo medido en zona.",
                    style = CBody.copy(fontSize = 13.sp, fontWeight = if (measuredTotal) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (measuredTotal) WizardColors.text else LiveSheetTokens.Muted,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (hasManualHeartRate) {
                        "Tu pulso manual es una lectura puntual: el tiempo en zona solo se mide con el reloj. Esta lectura no se reparte por el entrenamiento."
                    } else {
                        "Sin pulso del reloj no se mide tiempo en zona. Puedes anotar una lectura manual como dato puntual, pero no cuenta como tiempo en zona."
                    },
                    style = CBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = LiveSheetTokens.Muted,
                )
                Text(if (hasManualHeartRate) "Actualizar pulso manual" else "+ Añadir pulso a mano",
                    Modifier.heightIn(min = 48.dp).clip(CircleShape).border(1.dp, Color(0x3DF2EEE6), CircleShape)
                    .clickable(role = Role.Button, onClick = onAddManualHr).padding(horizontal = 15.dp, vertical = 13.dp).testTag("cardio-data-add-hr"),
                    style = CBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
            }
        }
    }
}

@Composable
private fun CardioSumGrid(
    cells: List<Triple<String, String, String>>,
    onClick: List<(() -> Unit)?> = emptyList(),
    editable: List<Boolean> = emptyList(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, (label, value, note) ->
                    val idx = row * 2 + col
                    val action = onClick.getOrNull(idx)
                    val isEditable = editable.getOrElse(idx) { action != null }
                    CardioSumCell(
                        label = label,
                        value = value,
                        note = note,
                        modifier = Modifier.weight(1f).then(if (action != null) Modifier.clickable(role = Role.Button, onClick = action) else Modifier),
                        editable = isEditable,
                    )
                }
            }
        }
    }
}

/** «Cardio terminado»: onda superior `Done`, `.cv-cell` (editables con borde y lápiz), barras `cv-grow`, RPE y pie `.cv-foot` en vidrio. */
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
    val compact = LocalConfiguration.current.screenHeightDp < 800
    val reduced = wizardReducedMotion()
    val finishHaze = remember { HazeState() }
    val cmpClock = rememberKpknClockMs(running = !reduced, key = "cv-fin-cmp", endMs = 600f + steps.size * 40f)
    val distanceMissing = distanceKm == null && estimatedKm == null
    val hrMissing = avgHr == null
    val finishColor = cardioZoneColor(2)

    Column(Modifier.fillMaxSize().testTag("cardio-finish-view")) {
        Column(Modifier.weight(1f).fillMaxWidth().hazeSource(state = finishHaze).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 2.dp).height(if (compact) 40.dp else 86.dp)) {
                KpknWaveField(
                    KpknWaveState.Done,
                    Modifier.fillMaxSize(),
                    zoneColor = finishColor,
                    lines = 9,
                    introSeconds = 1.6f,
                    edgeFade = KpknWaveEdgeFade.Finish,
                    restartKey = "finish-wave",
                )
            }
            Text(exerciseName.uppercase(), style = CBody.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
            Text("Cardio terminado", Modifier.padding(top = 6.dp, bottom = 14.dp), style = CSyne.copy(fontSize = if (compact) 24.sp else 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp), color = WizardColors.text)
            CardioSumGrid(
                cells = listOf(
                    Triple("TIEMPO ACTIVO", cardioClock(durationSeconds), if (pausedSeconds > 0) "pausa ${cardioClock(pausedSeconds)} aparte" else "de ${cardioClock(plannedSeconds)}"),
                    Triple("DISTANCIA", distanceKm?.let { cardioDecimal(it) } ?: estimatedKm?.let { "≈ ${cardioDecimal(it)}" } ?: "—",
                        if (distanceMissing) "toca para añadir" else distanceSource),
                    Triple("FC MEDIA", avgHr?.toString() ?: "—", avgHr?.let { "lpm" + (maxHr?.let { m -> " · máx $m" } ?: "") } ?: "toca para añadir"),
                    Triple("ENERGÍA", kcal?.let { "≈ ${it.roundToInt()}" } ?: "—", if (kcal != null) "kcal estimadas" else "sin peso corporal"),
                ),
                onClick = listOf(null, onEditDistance, onEditHeartRate, null),
                editable = listOf(false, onEditDistance != null && distanceMissing, onEditHeartRate != null && hrMissing, false),
            )
            CardioPageTitle("CUMPLIMIENTO POR PASO", Modifier.padding(top = 18.dp))
            val measured = stepSamples.any { it > 0 }
            Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                steps.forEachIndexed { i, step ->
                    val samples = stepSamples.getOrElse(i) { 0 }
                    val pct = if (samples > 0) stepInZone[i] * 100 / samples else null
                    Box(
                        Modifier.weight(step.seconds.toFloat().coerceAtLeast(1f)).fillMaxHeight()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                if (!reduced) {
                                    val p = ((cmpClock.value - i * 40f) / 600f).coerceIn(0f, 1f)
                                    val e = KpknMotion.Estandar.transform(p)
                                    scaleY = 0.05f + 0.95f * e
                                    alpha = e
                                }
                            }
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardioZoneColor(step.zone).copy(alpha = when {
                                pct == null -> .25f
                                pct >= 85 -> 1f
                                pct >= 60 -> .7f
                                else -> .45f
                            })),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pct != null && pct < 85) Text("$pct %", style = CSyne.copy(fontSize = 9.5.sp), color = Color(0xFF111111))
                    }
                }
            }
            Text(
                if (!measured) "Sin pulso: no hay cumplimiento medido por paso."
                else {
                    val inside = steps.indices.count { i -> stepSamples[i] > 0 && stepInZone[i] * 100 / stepSamples[i] >= 85 }
                    val worst = steps.indices.filter { stepSamples[it] > 0 }.minByOrNull { stepInZone[it] * 100 / stepSamples[it] }
                    "$inside de ${steps.size} pasos dentro del objetivo" + (worst?.takeIf { stepInZone[it] * 100 / stepSamples[it] < 85 }?.let {
                        " · ${steps[it].name}${steps[it].round?.let { r -> " $r" } ?: ""} se quedó en ${stepInZone[it] * 100 / stepSamples[it]} %"
                    } ?: "")
                },
                Modifier.padding(top = 6.dp), style = CBody.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = LiveSheetTokens.Faint,
            )
            Row(Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CardioPageTitle("¿CUÁNTO TE COSTÓ? · ESFUERZO REAL", Modifier.weight(1f).padding(bottom = 0.dp))
                Text(
                    rpe?.let { "RPE $it · ${CardioGuideEngine.rpeAnchor(it.toDouble())}" } ?: "Sin marcar",
                    style = CBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = if (rpe != null) WizardColors.text else LiveSheetTokens.Muted,
                    modifier = Modifier.testTag("cardio-rpe-label"),
                )
            }
            CardioRpePicker(rpe, plannedRpe, onRpe)
            Text("Esfuerzo real. El plan ($plannedRpe) es una referencia; marca lo que sentiste.", Modifier.padding(top = 8.dp), style = CBody.copy(fontSize = 11.5.sp),
                color = LiveSheetTokens.Faint)
            if (failed) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(KpknLineIcons.Warning, null, Modifier.padding(top = 2.dp).size(15.dp), tint = LiveSheetTokens.Danger)
                    Text("No se pudo guardar. Tus datos se conservan; vuelve a intentarlo.", style = CBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = LiveSheetTokens.Danger)
                }
            }
        }
        val footShape = RoundedCornerShape(30.dp)
        Column(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = if (compact) 14.dp else 20.dp)
                .kpknGlassPreset(finishHaze, KpknGlassPreset.VidrioIsla, footShape)
                .border(1.dp, Color(0x1FFFFFFF), footShape).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardioPillButton(
                label = if (saving) "Guardando…" else "Guardar cardio",
                onClick = onSave,
                primary = true,
                icon = if (!saving) KpknLineIcons.Check else null,
                modifier = Modifier.fillMaxWidth(),
                enabled = rpe != null && !saving,
                tag = "cardio-save",
            )
            if (!saving) {
                Box(
                    Modifier.height(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Volver al cardio", style = CBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
                }
            }
        }
    }
}

/** El cardio ya registrado: onda superior `Done`, tick `.cv-pop`, `.cv-cell` y pie `.cv-foot` vidrio con «Actualizar cardio». */
@Composable
private fun CardioRecordedView(exerciseName: String, durationSeconds: Int, distanceKm: Double?, avgHr: Int?, rpe: Double?, onUpdate: () -> Unit) {
    val compact = LocalConfiguration.current.screenHeightDp < 800
    val recHaze = remember { HazeState() }
    Column(Modifier.fillMaxSize().testTag("cardio-recorded")) {
        Column(Modifier.weight(1f).fillMaxWidth().hazeSource(state = recHaze).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().height(70.dp)) {
                KpknWaveField(
                    KpknWaveState.Done,
                    Modifier.fillMaxSize(),
                    zoneColor = cardioZoneColor(2),
                    lines = 8,
                    introSeconds = 1.2f,
                    edgeFade = KpknWaveEdgeFade.Finish,
                    restartKey = "recorded-wave",
                )
            }
            Text(exerciseName.uppercase(), style = CBody.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
            Row(Modifier.padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(44.dp).kpknAnimate(KpknFamilies.ReboteAparece.demo, restartKey = "recorded-tick")
                        .clip(CircleShape).background(WizardColors.text),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KpknLineIcons.Check, null, Modifier.size(22.dp), tint = Color(0xFF111111))
                }
                Text("Cardio registrado", style = CSyne.copy(fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold), color = WizardColors.text)
            }
            CardioSumGrid(listOf(
                Triple("TIEMPO ACTIVO", cardioClock(durationSeconds), "registrado"),
                Triple("DISTANCIA", distanceKm?.let { cardioDecimal(it) } ?: "—", "km"),
                Triple("FC MEDIA", avgHr?.toString() ?: "—", "lpm"),
                Triple("ESFUERZO", rpe?.let { "RPE ${it.roundToInt()}" } ?: "—", "real"),
            ))
        }
        val footShape = RoundedCornerShape(30.dp)
        Box(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = if (compact) 14.dp else 20.dp)
                .kpknGlassPreset(recHaze, KpknGlassPreset.VidrioIsla, footShape)
                .border(1.dp, Color(0x1FFFFFFF), footShape).padding(10.dp),
        ) {
            CardioPillButton("Actualizar cardio", onUpdate, primary = false, modifier = Modifier.fillMaxWidth(), tag = "cardio-update")
        }
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
