package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MovementPatternReadiness
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.domain.auge.DiscomfortSuggestionEngine
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.screens.home.RingColors
import com.example.kpkn.screens.home.augeRingsHostHeightDp
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.components.rings.TowerRingsStyle
import com.example.kpkn.ui.components.rings.towerRingCenter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutReadinessSheet(
    showReadinessSheet: Boolean,
    gender: Gender?,
    sessionMuscleStartingBatteries: Map<String, Int>,
    readinessNeuralStart: Int,
    readinessMuscularStart: Int,
    readinessSpinalStart: Int,
    hazeState: HazeState,
    onSave: (
        neural: Int,
        muscular: Int?,
        spinal: Int,
        perMuscle: Map<String, Int>,
        discomforts: List<String>,
        /** Only channels the user actually edited — used for AUGE manual overrides. */
        manualNeural: Int?,
        manualSpinal: Int?,
        manualMuscular: Int?,
        manualMuscleBatteries: Map<String, Int>,
    ) -> Unit,
    patternReadiness: List<MovementPatternReadiness> = emptyList(),
    exerciseReadinessMap: Map<String, ExerciseReadiness> = emptyMap(),
    sessionExercises: List<Exercise> = emptyList(),
    perMuscle: Map<String, com.example.kpkn.data.models.MuscleRecoveryStatus> = emptyMap(),
    augeSnapshot: AugeSnapshot? = null,
    initialDiscomforts: List<String> = emptyList(),
    voiceSessionEnabled: Boolean = false,
    voiceCaptureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
    onVoiceToggle: () -> Unit = {},
    onVoiceCaptureModeChange: (VoiceCaptureMode) -> Unit = {},
    ringsStyle: TowerRingsStyle = TowerRingsStyle.LUMINOUS,
    forcedGleamProgress: Float? = null,
    /**
     * Cerrar el estado previo sin responder (Atrás, tocar fuera o arrastrar la hoja hacia abajo; LV4, 15): la sesión se ve y el estado
     * previo queda pendiente. Con `null` la hoja sigue siendo obligatoria, como antes.
     */
    onDismiss: (() -> Unit)? = null,
) {
    if (!showReadinessSheet) return

    var neural by rememberSaveable { mutableIntStateOf(readinessNeuralStart) }
    var muscular by rememberSaveable { mutableIntStateOf(readinessMuscularStart) }
    var spinal by rememberSaveable { mutableIntStateOf(readinessSpinalStart) }
    val muscleAdjustments = rememberSaveable(
        saver = mapSaver(
            save = { value -> value.toMap() },
            restore = { restored ->
                mutableStateMapOf<String, Int>().apply {
                    restored.forEach { (key, value) ->
                        val n = (value as? Number)?.toInt() ?: return@forEach
                        this[key] = n
                    }
                }
            },
        ),
    ) { mutableStateMapOf<String, Int>() }
    val derivedMuscular by derivedStateOf {
        if (muscleAdjustments.isEmpty()) readinessMuscularStart.coerceIn(0, 100)
        else muscleAdjustments.values.average().toInt().coerceIn(0, 100)
    }
    var userEditedNeural by rememberSaveable { mutableStateOf(false) }
    var userEditedSpinal by rememberSaveable { mutableStateOf(false) }
    val userEditedMuscles = remember { mutableStateMapOf<String, Boolean>() }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var selectedDiscomforts by rememberSaveable { mutableStateOf(listOf<String>()) }
    var adjustTarget by remember { mutableStateOf<ReadinessAdjustTarget?>(null) }

    LaunchedEffect(
        initialized,
        readinessNeuralStart,
        readinessMuscularStart,
        readinessSpinalStart,
        sessionMuscleStartingBatteries,
    ) {
        if (!initialized) {
            neural = readinessNeuralStart
            muscular = derivedMuscular
            spinal = readinessSpinalStart
            muscleAdjustments.clear()
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                muscleAdjustments[muscleId] = value.coerceIn(0, 100)
            }
            selectedDiscomforts = initialDiscomforts
            initialized = true
        } else {
            if (!userEditedNeural) neural = readinessNeuralStart
            muscular = readinessMuscularStart
            if (!userEditedSpinal) spinal = readinessSpinalStart
            val validMuscles = sessionMuscleStartingBatteries.keys
            muscleAdjustments.keys
                .filter { it !in validMuscles }
                .forEach { muscleAdjustments.remove(it) }
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                if (userEditedMuscles[muscleId] != true) {
                    muscleAdjustments[muscleId] = value.coerceIn(0, 100)
                }
            }
        }
    }
    KpknSheet(
        onDismissRequest = { onDismiss?.invoke() },
        dismissible = onDismiss != null,
        stableHeightFraction = 0.86f,
        hazeState = hazeState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    0.82f to Color.Black,
                                    1.0f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "¿Cómo llegas a entrenar hoy?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = WizardColors.text,
                )

                Spacer(Modifier.height(10.dp))

                // 1. RINGS ENTRENLAZADOS (estilo Home) + ajuste por ring
                InterlacedRingsBlock(
                    muscular = derivedMuscular,
                    neural = neural,
                    spinal = spinal,
                    selectedTarget = adjustTarget,
                    muscleAdjustments = muscleAdjustments,
                    onRingTap = { adjustTarget = it },
                    onDismiss = { adjustTarget = null },
                    onNeuralChange = { neural = it; userEditedNeural = true },
                    onSpinalChange = { spinal = it; userEditedSpinal = true },
                    onMuscleChange = { muscleId, updated ->
                        muscleAdjustments[muscleId] = updated
                        userEditedMuscles[muscleId] = true
                    },
                    style = ringsStyle,
                    forcedGleamProgress = forcedGleamProgress,
                )

                val coaching = remember(patternReadiness, exerciseReadinessMap, sessionExercises, perMuscle) {
                    ExerciseReadinessEngine.buildPatternCoaching(
                        patternReadiness = patternReadiness,
                        exerciseReadinessMap = exerciseReadinessMap,
                        sessionExercises = sessionExercises,
                        perMuscle = perMuscle,
                    )
                }
                coaching?.let { tip ->
                    Text(
                        text = tip.headline,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    Text(
                        text = tip.detail,
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                }
                augeSnapshot?.advisories
                    ?.firstOrNull { LoadAdvisoryEngine.rank(it.level) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.ADJUST) }
                    ?.let { advisory ->
                        Text(
                            text = "${advisory.title}: ${advisory.body}",
                            style = KpknType.body,
                            // El amarillo del aviso pasa al token de la energía, el único amarillo de la marca.
                            color = RingColors[1],
                        )
                    }

                val sessionPillarMuscleIds = remember(sessionExercises) {
                    getSessionPillarMuscleIds(sessionExercises)
                }
                PreWorkoutDiscomfortSelector(
                    selectedDiscomforts = selectedDiscomforts,
                    onDiscomfortsChanged = { list ->
                        selectedDiscomforts = list
                    },
                    sessionPillarMuscleIds = sessionPillarMuscleIds,
                )

                // ── 2c. Control por voz: pre-entrenamiento ──
                SessionVoiceReadinessCard(
                    voiceSessionEnabled = voiceSessionEnabled,
                    captureMode = voiceCaptureMode,
                    onToggle = onVoiceToggle,
                    onCaptureModeChange = onVoiceCaptureModeChange,
                )
            }

            // 3. BOTÓN PRINCIPAL — ✓ de 64 dp anclado abajo, con la piel del ✓ de la serie (LR1b)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                ReadinessConfirmButton(
                    onClick = {
                        val editedMuscleMap = muscleAdjustments
                            .filter { (id, _) -> userEditedMuscles[id] == true }
                            .mapValues { (_, v) -> v.coerceIn(0, 100) }
                        val manualMuscular = null
                        onSave(
                            neural,
                            derivedMuscular,
                            spinal,
                            muscleAdjustments.toMap(),
                            selectedDiscomforts.toList(),
                            if (userEditedNeural) neural.coerceIn(0, 100) else null,
                            if (userEditedSpinal) spinal.coerceIn(0, 100) else null,
                            manualMuscular,
                            editedMuscleMap,
                        )
                    },
                )
            }
        }
    }
}

private enum class ReadinessAdjustTarget { MUSCULAR, ENERGY, SPINE }

/** Anillo de la torre que se toca para cada ajuste de la preparación. */
private fun ReadinessAdjustTarget.toRingChannel(): RingChannel = when (this) {
    ReadinessAdjustTarget.MUSCULAR -> RingChannel.MUSCULOS
    ReadinessAdjustTarget.ENERGY -> RingChannel.ENERGIA
    ReadinessAdjustTarget.SPINE -> RingChannel.COLUMNA
}

private fun RingChannel.toReadinessTarget(): ReadinessAdjustTarget = when (this) {
    RingChannel.MUSCULOS -> ReadinessAdjustTarget.MUSCULAR
    RingChannel.ENERGIA -> ReadinessAdjustTarget.ENERGY
    RingChannel.COLUMNA -> ReadinessAdjustTarget.SPINE
}

@Composable
private fun InterlacedRingsBlock(
    muscular: Int,
    neural: Int,
    spinal: Int,
    selectedTarget: ReadinessAdjustTarget?,
    muscleAdjustments: Map<String, Int>,
    onRingTap: (ReadinessAdjustTarget) -> Unit,
    onDismiss: () -> Unit,
    onNeuralChange: (Int) -> Unit,
    onSpinalChange: (Int) -> Unit,
    onMuscleChange: (String, Int) -> Unit,
    style: TowerRingsStyle = TowerRingsStyle.LUMINOUS,
    forcedGleamProgress: Float? = null,
) {
    val density = LocalDensity.current
    var ringsSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val hostHeight = augeRingsHostHeightDp(maxWidth.value).dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(hostHeight)
                    .onSizeChanged { ringsSize = it },
            ) {
                // Sin animación: al ajustar con el deslizador la torre sigue el valor al instante.
                KpknTowerRings(
                    values = TowerRingValues(columna = spinal, musculos = muscular, energia = neural),
                    modifier = Modifier.fillMaxSize(),
                    onRingTap = { channel -> onRingTap(channel.toReadinessTarget()) },
                    animate = false,
                    style = style,
                    forcedGleamProgress = forcedGleamProgress,
                )

                if (ringsSize != IntSize.Zero) {
                    selectedTarget?.let { selected ->
                        val c = towerRingCenter(
                            channel = selected.toRingChannel(),
                            width = ringsSize.width.toFloat(),
                            height = ringsSize.height.toFloat(),
                        )
                        val cardWidthPx = with(density) { 264.dp.toPx() }
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = IntOffset(
                                (c.x - cardWidthPx / 2).toInt(),
                                (c.y + with(density) { 14.dp.toPx() }).toInt(),
                            ),
                            properties = PopupProperties(focusable = true),
                            onDismissRequest = onDismiss,
                        ) {
                            ReadinessAdjustCard(
                                target = selected,
                                neural = neural,
                                spinal = spinal,
                                muscleAdjustments = muscleAdjustments,
                                onNeuralChange = onNeuralChange,
                                onSpinalChange = onSpinalChange,
                                onMuscleChange = onMuscleChange,
                                onDismiss = onDismiss,
                            )
                        }
                    }
                }
            }
        }

        // Keep the captions clear of the lowered energy ring.
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InterlacedRingLabel("Músculos", muscular, RingColors[0])
            InterlacedRingLabel("Energía", neural, RingColors[1])
            InterlacedRingLabel("Columna", spinal, RingColors[2])
        }

        Text(
            text = "Toca un anillo para ajustarlo",
            style = KpknType.caption,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadinessAdjustCard(
    target: ReadinessAdjustTarget,
    neural: Int,
    spinal: Int,
    muscleAdjustments: Map<String, Int>,
    onNeuralChange: (Int) -> Unit,
    onSpinalChange: (Int) -> Unit,
    onMuscleChange: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, color) = when (target) {
        ReadinessAdjustTarget.MUSCULAR -> "Músculos" to RingColors[0]
        ReadinessAdjustTarget.ENERGY -> "Energía" to RingColors[1]
        ReadinessAdjustTarget.SPINE -> "Columna" to RingColors[2]
    }
    // Popup del anillo: módulo de la piel nueva, con su filete y sin vidrio (LR1b).
    KpknModule(
        modifier = Modifier
            .width(264.dp)
            .heightIn(max = 340.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(
                        text = "Ajustar $title",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                }
                KpknIconButton(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Cerrar",
                    onClick = onDismiss,
                )
            }

            when (target) {
                ReadinessAdjustTarget.ENERGY -> ReadinessSliderRow(
                    title = "Energía",
                    value = neural,
                    color = RingColors[1],
                    onValueChange = onNeuralChange,
                )
                ReadinessAdjustTarget.SPINE -> ReadinessSliderRow(
                    title = "Columna",
                    value = spinal,
                    color = RingColors[2],
                    onValueChange = onSpinalChange,
                )
                ReadinessAdjustTarget.MUSCULAR -> {
                    if (muscleAdjustments.isEmpty()) {
                        Text(
                            text = "No hay músculos detectados para esta sesión.",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .nestedScroll(object : NestedScrollConnection {}),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Frescura por Músculo".uppercase(Locale.forLanguageTag("es")),
                                style = KpknType.eyebrow,
                                color = WizardColors.textMuted,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2,
                            ) {
                                muscleAdjustments.keys.sorted().forEach { muscleId ->
                                    MinimalMuscleSlider(
                                        modifier = Modifier.weight(1f),
                                        muscleLabel = muscleId,
                                        value = muscleAdjustments[muscleId] ?: 100,
                                        onValueChange = { updated ->
                                            onMuscleChange(muscleId, updated)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InterlacedRingLabel(
    label: String,
    value: Int,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Eyebrow en el color de su anillo y cifra en Syne con espacio inseparable (LR1b).
        Text(
            text = label.uppercase(Locale.forLanguageTag("es")),
            style = KpknType.eyebrow,
            color = color,
            maxLines = 1,
        )
        Text(
            text = "$value %",
            style = KpknType.metricM,
            color = WizardColors.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReadinessSliderRow(
    title: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
            Text(
                text = "$value %",
                style = KpknType.metricM,
                color = WizardColors.text,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                activeTrackColor = color,
                thumbColor = color,
                inactiveTrackColor = color.copy(alpha = 0.15f),
            ),
        )
    }
}

@Composable
internal fun AdjustableRingCompact(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    ringColor: Color,
    ringSize: Int,
    onValueChange: (Int) -> Unit,
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragStartValue by remember { mutableIntStateOf(value) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .size(ringSize.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = "$value por ciento"
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 100)
                    setProgress { target ->
                        onValueChange(target.toInt().coerceIn(0, 100))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccumulator = 0f
                            dragStartValue = value
                        },
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += -dragAmount.y
                            val step = (dragAccumulator / 1.25f).toInt()
                            onValueChange((dragStartValue + step).coerceIn(0, 100))
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ReadinessRingVisual(value = value, color = ringColor)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = ringColor,
                    fontSize = 20.sp,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ringColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }

        Text(
            "Arrastrar verticalmente",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
internal fun ReadinessRingVisual(
    value: Int,
    color: Color,
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        label = "readinessRing",
    )

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = 6.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = radius,
            center = center,
            style = Stroke(strokePx),
        )

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedValue,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(strokePx),
        )
    }
}

@Composable
private fun SessionVoiceReadinessCard(
    voiceSessionEnabled: Boolean,
    captureMode: VoiceCaptureMode,
    onToggle: () -> Unit,
    onCaptureModeChange: (VoiceCaptureMode) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var modeInfo by remember { mutableStateOf<VoiceCaptureMode?>(null) }
    var cardHeight by remember { mutableIntStateOf(0) }
    var modeRowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // Tarjeta de voz: módulo de la piel nueva; pulsarla abre la explicación, como antes (LR1b).
    KpknModule(
        onClick = { showInfo = true },
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { cardHeight = it.height },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = KpknLineIcons.Mic,
                        contentDescription = null,
                        tint = if (voiceSessionEnabled) WizardColors.text else WizardColors.textFaint,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Entrenar con Comandos de Voz",
                        style = KpknType.bodyStrong,
                        color = if (voiceSessionEnabled) WizardColors.text else WizardColors.textMuted,
                    )
                }
                KpknSwitch(
                    checked = voiceSessionEnabled,
                    onCheckedChange = { onToggle() },
                )
            }

            if (voiceSessionEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { modeRowHeight = it.height },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Modo".uppercase(Locale.forLanguageTag("es")),
                        style = KpknType.eyebrow,
                        color = WizardColors.textMuted,
                    )
                    val musicSelected = captureMode == VoiceCaptureMode.MUSIC
                    KpknSegmentedControl(
                        options = listOf("Manos libres", "Música"),
                        selectedIndex = if (musicSelected) 1 else 0,
                        onSelect = { index ->
                            val mode = if (index == 0) VoiceCaptureMode.HANDS_FREE else VoiceCaptureMode.MUSIC
                            // Pulsar el segmento ya elegido no cambia el modo, pero sí abre su explicación (como antes).
                            val alreadyChosen = (index == 1) == musicSelected
                            if (!alreadyChosen) onCaptureModeChange(mode)
                            modeInfo = mode
                        },
                        modifier = Modifier.weight(1f),
                        size = KpknSegmentedSize.Compact,
                    )
                }

                modeInfo?.let { mode ->
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(
                            0,
                            modeRowHeight + with(density) { 8.dp.toPx() }.toInt(),
                        ),
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = { modeInfo = null },
                    ) {
                        VoiceModeInfoCard(mode = mode, onDismiss = { modeInfo = null })
                    }
                }
            }
        }
    }

    if (showInfo) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(
                0,
                cardHeight + with(density) { 8.dp.toPx() }.toInt(),
            ),
            properties = PopupProperties(focusable = true),
            onDismissRequest = { showInfo = false },
        ) {
            KpknModule(
                modifier = Modifier.width(300.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = KpknLineIcons.Mic,
                            contentDescription = null,
                            tint = WizardColors.text,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "¿Cómo funciona la voz?",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                    }
                    Text(
                        text = "Controla tu sesión hablando: registra series, " +
                            "cambia pesos y gestiona descansos con comandos de voz. " +
                            "La voz estará activa durante toda la sesión.",
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                    Text(
                        text = "Ejemplos: “2 series más”, “sube a 80 kg”, “pausa de 90 segundos”.",
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                    KpknPrimaryPill(
                        text = "Entendido",
                        onClick = { showInfo = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceModeInfoCard(
    mode: VoiceCaptureMode,
    onDismiss: () -> Unit,
) {
    val (title, body) = when (mode) {
        VoiceCaptureMode.HANDS_FREE ->
            "Modo manos libres" to
                "Registra tus series con el micrófono de tus auriculares, incluso por bluetooth, " +
                "sin acercarte al celular. La contra: el audio suena con calidad de llamada " +
                "y degrada la música de fondo."
        VoiceCaptureMode.MUSIC ->
            "Modo música" to
                "Usa el micrófono del celular, no el de tus auriculares: tu música se escucha " +
                "perfecta. Solo necesitas tener el teléfono cerca (sin desbloquearlo) para " +
                "registrar tus series por voz."
    }
    KpknModule(
        modifier = Modifier.width(300.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Mic,
                    contentDescription = null,
                    tint = WizardColors.text,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
            }
            Text(
                text = body,
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            KpknPrimaryPill(
                text = "Entendido",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreWorkoutDiscomfortSelector(
    selectedDiscomforts: List<String>,
    onDiscomfortsChanged: (List<String>) -> Unit,
    sessionPillarMuscleIds: List<String> = emptyList(),
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(sessionPillarMuscleIds) {
        DiscomfortSuggestionEngine.suggestForMuscles(sessionPillarMuscleIds)
    }
    val searchResults = remember(query) {
        if (query.isBlank()) emptyList() else DiscomfortSuggestionEngine.search(query)
    }
    val isSearching = query.isNotBlank()
    val count = selectedDiscomforts.size

    fun toggle(item: DiscomfortCatalogEntry) {
        val newSelection = selectedDiscomforts.toMutableList()
        if (selectedDiscomforts.contains(item.id)) {
            newSelection.remove(item.id)
        } else {
            newSelection.add(item.id)
        }
        onDiscomfortsChanged(newSelection)
    }

    // Tarjeta de molestias: módulo de la piel nueva (LR1b), con la cabecera que pliega y despliega.
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = KpknLineIcons.Info,
                        contentDescription = null,
                        tint = WizardColors.text,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Molestias previas al entrenamiento",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                        Text(
                            text = if (count == 0) "Ninguna reportada" else "$count seleccionada(s)",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                    contentDescription = if (expanded) "Plegar" else "Desplegar",
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpknTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Buscar molestia…",
                        leadingIcon = KpknLineIcons.Search,
                        trailing = if (query.isNotBlank()) {
                            {
                                KpknIconButton(
                                    icon = KpknLineIcons.Close,
                                    contentDescription = "Limpiar búsqueda",
                                    onClick = { query = "" },
                                )
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                    )

                    if (isSearching) {
                        Text(
                            text = "Resultados".uppercase(Locale.forLanguageTag("es")),
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                        )
                        if (searchResults.isEmpty()) {
                            Text(
                                text = "Sin resultados para \"$query\"",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                searchResults.forEach { item ->
                                    DiscomfortFilterChip(
                                        item = item,
                                        isSelected = selectedDiscomforts.contains(item.id),
                                        onClick = { toggle(item) },
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Sugeridas para tu sesión".uppercase(Locale.forLanguageTag("es")),
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                        )
                        if (suggestions.isEmpty()) {
                            Text(
                                text = "No hay molestias típicas para los músculos de esta sesión.",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                suggestions.forEach { item ->
                                    DiscomfortFilterChip(
                                        item = item,
                                        isSelected = selectedDiscomforts.contains(item.id),
                                        onClick = { toggle(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscomfortFilterChip(
    item: DiscomfortCatalogEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    KpknChip(
        text = item.label,
        selected = isSelected,
        onClick = onClick,
    )
}

/**
 * Botón ✓ de confirmar y entrenar (LR1b): círculo de 64 dp en tinta con la marca en negro, como el ✓ de la serie (LR1a). No reutiliza
 * [WorkoutRecordFab] porque ese registra la serie y su etiqueta sería otra.
 */
@Composable
private fun ReadinessConfirmButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .shadow(10.dp, CircleShape, clip = false)
            .size(64.dp)
            .clip(CircleShape)
            .background(WizardColors.text)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Confirmar y entrenar" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = KpknLineIcons.Check,
            contentDescription = null,
            tint = WizardColors.ctaContent,
            modifier = Modifier.size(28.dp),
        )
    }
}

private fun getSessionPillarMuscleIds(exercises: List<Exercise>): List<String> {
    val pillars = linkedSetOf<String>()
    fun collect(exercise: Exercise) {
        val info = resolveExercise(exercise.resolvedCanonicalExerciseId())
            ?: resolveExercise(exercise.exerciseDbId ?: exercise.exerciseId)
            ?: return
        SessionMuscleFilter.relevantMusclesFor(info)
            .forEach { involvement ->
                getAugeMusclePillarId(involvement.muscle, involvement.emphasis)
                    .takeIf { it.isNotBlank() }
                    ?.let(pillars::add)
            }
    }
    exercises.forEach(::collect)
    return pillars.toList()
}
