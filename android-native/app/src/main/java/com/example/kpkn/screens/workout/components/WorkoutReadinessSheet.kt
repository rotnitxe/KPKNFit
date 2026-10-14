package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MovementPatternReadiness
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.domain.auge.DiscomfortSuggestionEngine
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPercentRule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import dev.chrisbanes.haze.HazeState
import java.util.Locale

/** Alto de la hoja de preparación: casi a pantalla completa, como pide la pantalla de la sesión en vivo. */
private const val ReadinessSheetFraction = 0.96f

/**
 * Preparación antes de empezar la sesión (LIVE_SESSION_V1 §5): «¿Cómo llegas a entrenar hoy?». Es la hoja de [KpknSheet] y pinta su
 * cuerpo, [WorkoutReadinessBody]. Los ajustes, las anclas, las molestias y la voz son los de siempre; solo cambia cómo se ven.
 */
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
) {
    if (!showReadinessSheet) return

    var neural by rememberSaveable { mutableIntStateOf(readinessNeuralStart) }
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

    LaunchedEffect(
        initialized,
        readinessNeuralStart,
        readinessMuscularStart,
        readinessSpinalStart,
        sessionMuscleStartingBatteries,
    ) {
        if (!initialized) {
            neural = readinessNeuralStart
            spinal = readinessSpinalStart
            muscleAdjustments.clear()
            sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                muscleAdjustments[muscleId] = value.coerceIn(0, 100)
            }
            selectedDiscomforts = initialDiscomforts
            initialized = true
        } else {
            if (!userEditedNeural) neural = readinessNeuralStart
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

    val sessionPillarMuscleIds = remember(sessionExercises) {
        getSessionPillarMuscleIds(sessionExercises)
    }
    val coaching = remember(patternReadiness, exerciseReadinessMap, sessionExercises, perMuscle) {
        ExerciseReadinessEngine.buildPatternCoaching(
            patternReadiness = patternReadiness,
            exerciseReadinessMap = exerciseReadinessMap,
            sessionExercises = sessionExercises,
            perMuscle = perMuscle,
        )
    }
    val advice = remember(augeSnapshot, coaching) {
        ReadinessAdvice(
            channelLines = augeSnapshot?.dashboard?.channels?.map { channel ->
                "${channel.title}: ${RecoveryBands.label(channel.band)} · ${channel.action}"
            }.orEmpty(),
            coachingHeadline = coaching?.headline,
            coachingDetail = coaching?.detail,
            advisoryLine = augeSnapshot?.advisories
                ?.firstOrNull { LoadAdvisoryEngine.rank(it.level) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.ADJUST) }
                ?.let { advisory -> "${advisory.title}: ${advisory.body}" },
        )
    }

    KpknSheet(
        onDismissRequest = {},
        dismissible = false,
        heightFraction = ReadinessSheetFraction,
        stableHeightFraction = ReadinessSheetFraction,
        hazeState = hazeState,
    ) {
        WorkoutReadinessBody(
            neural = neural,
            muscular = derivedMuscular,
            spinal = spinal,
            muscleAdjustments = muscleAdjustments,
            selectedDiscomforts = selectedDiscomforts,
            discomfortPillarIds = sessionPillarMuscleIds,
            advice = advice,
            voiceSessionEnabled = voiceSessionEnabled,
            voiceCaptureMode = voiceCaptureMode,
            onNeuralChange = {
                neural = it
                userEditedNeural = true
            },
            onSpinalChange = {
                spinal = it
                userEditedSpinal = true
            },
            onMuscleChange = { muscleId, updated ->
                muscleAdjustments[muscleId] = updated
                userEditedMuscles[muscleId] = true
            },
            onDiscomfortsChanged = { selectedDiscomforts = it },
            onVoiceToggle = onVoiceToggle,
            onVoiceCaptureModeChange = onVoiceCaptureModeChange,
            onStart = {
                val editedMuscleMap = muscleAdjustments
                    .filter { (id, _) -> userEditedMuscles[id] == true }
                    .mapValues { (_, v) -> v.coerceIn(0, 100) }
                onSave(
                    neural,
                    derivedMuscular,
                    spinal,
                    muscleAdjustments.toMap(),
                    selectedDiscomforts.toList(),
                    if (userEditedNeural) neural.coerceIn(0, 100) else null,
                    if (userEditedSpinal) spinal.coerceIn(0, 100) else null,
                    null,
                    editedMuscleMap,
                )
            },
        )
    }
}

/** Lo que la hoja cuenta del estado: el resumen de los canales, el consejo del patrón y el aviso de carga. Lo arma la pantalla. */
internal data class ReadinessAdvice(
    val channelLines: List<String>,
    val coachingHeadline: String?,
    val coachingDetail: String?,
    val advisoryLine: String?,
) {
    /** Sin nada que contar: el módulo no se pinta. */
    val isEmpty: Boolean
        get() = channelLines.isEmpty() && coachingHeadline == null && coachingDetail == null && advisoryLine == null

    companion object {
        val None = ReadinessAdvice(emptyList(), null, null, null)
    }
}

/**
 * Cuerpo de la preparación, sin la hoja: lo pinta [WorkoutReadinessSheet] y lo usan las pruebas y la hoja de render. Los ajustes, las
 * molestias y la voz llegan por parámetros y salen por las lambdas; aquí solo vive lo que se ve (qué módulo está abierto y qué canal
 * está resaltado). El botón de abajo queda fuera del desplazamiento, siempre a la vista.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutReadinessBody(
    neural: Int,
    muscular: Int,
    spinal: Int,
    muscleAdjustments: Map<String, Int>,
    selectedDiscomforts: List<String>,
    discomfortPillarIds: List<String>,
    advice: ReadinessAdvice,
    voiceSessionEnabled: Boolean,
    voiceCaptureMode: VoiceCaptureMode,
    onNeuralChange: (Int) -> Unit,
    onSpinalChange: (Int) -> Unit,
    onMuscleChange: (String, Int) -> Unit,
    onDiscomfortsChanged: (List<String>) -> Unit,
    onVoiceToggle: () -> Unit,
    onVoiceCaptureModeChange: (VoiceCaptureMode) -> Unit,
    onStart: () -> Unit,
) {
    var focus by remember { mutableStateOf<RingChannel?>(null) }
    var musclesOpen by rememberSaveable { mutableStateOf(false) }
    var discomfortsOpen by rememberSaveable { mutableStateOf(false) }
    var discomfortQuery by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    val suggestions = remember(discomfortPillarIds) {
        DiscomfortSuggestionEngine.suggestForMuscles(discomfortPillarIds)
    }
    val searchResults = remember(discomfortQuery) {
        if (discomfortQuery.isBlank()) emptyList() else DiscomfortSuggestionEngine.search(discomfortQuery)
    }
    val countLabel = when (selectedDiscomforts.size) {
        0 -> "Ninguna reportada"
        1 -> "1 seleccionada"
        else -> "${selectedDiscomforts.size} seleccionadas"
    }

    fun toggleDiscomfort(item: DiscomfortCatalogEntry) {
        onDiscomfortsChanged(
            if (selectedDiscomforts.contains(item.id)) selectedDiscomforts - item.id
            else selectedDiscomforts + item.id,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Solo se desvanece el borde si queda contenido por desplazar: si todo cabe, la última línea se ve entera.
                    if (scroll.canScrollForward) {
                        val fade = 32.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    ((size.height - fade) / size.height).coerceIn(0f, 1f) to Color.Black,
                                    1.0f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .verticalScroll(scroll)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Reporta tu estado antes de entrenar".uppercase(Locale("es")),
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                Text(
                    text = "¿Cómo llegas a entrenar hoy?",
                    style = KpknType.title,
                    color = WizardColors.text,
                    modifier = Modifier.semantics { heading() },
                )
            }

            // Torre de los RINGS con los tres canales. Tocar un anillo lo resalta; el de Músculos abre su ajuste.
            KpknModule(
                modifier = Modifier.kpknEntrance("preparacion", 0),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            ) {
                KpknTowerRings(
                    values = TowerRingValues(columna = spinal, musculos = muscular, energia = neural),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    highlighted = focus,
                    onRingTap = { channel ->
                        focus = if (focus == channel) null else channel
                        if (channel == RingChannel.MUSCULOS) musclesOpen = true
                    },
                    // Sin animación: al arrastrar una regla, la torre sigue el valor al instante.
                    animate = false,
                )
                Spacer(Modifier.height(16.dp))
                // Músculos es la media de sus músculos (abajo), así que no tiene regla propia: abre el ajuste.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelLabel(
                        label = "Músculos",
                        value = muscular,
                        color = KpknRingColors.musculos,
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) { contentDescription = "Músculos $muscular %" },
                    )
                    Spacer(Modifier.width(12.dp))
                    KpknTextAction(
                        text = "Ajustar",
                        onClick = { musclesOpen = !musclesOpen },
                        icon = if (musclesOpen) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ChannelRuleRow(
                    label = "Energía",
                    value = neural,
                    color = KpknRingColors.energia,
                    onChange = onNeuralChange,
                )
                Spacer(Modifier.height(10.dp))
                ChannelRuleRow(
                    label = "Columna",
                    value = spinal,
                    color = KpknRingColors.columna,
                    onChange = onSpinalChange,
                )
            }

            if (!advice.isEmpty) {
                KpknModule(
                    modifier = Modifier.kpknEntrance("preparacion", 1),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        advice.channelLines.forEach { line ->
                            Text(text = line, style = KpknType.caption, color = WizardColors.textMuted)
                        }
                        advice.coachingHeadline?.let { headline ->
                            Text(text = headline, style = KpknType.bodyStrong, color = WizardColors.text)
                        }
                        advice.coachingDetail?.let { detail ->
                            Text(text = detail, style = KpknType.caption, color = WizardColors.textMuted)
                        }
                        advice.advisoryLine?.let { line ->
                            Text(text = line, style = KpknType.caption, color = SymbolPalette.energia)
                        }
                    }
                }
            }

            // Ajuste por músculo, plegado: cada músculo con su regla, en dos columnas.
            KpknModule(
                modifier = Modifier.kpknEntrance("preparacion", 2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                KpknModuleHeader(
                    label = "Ajustar músculos",
                    modifier = Modifier
                        .semantics { stateDescription = if (musclesOpen) "Desplegado" else "Plegado" }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { musclesOpen = !musclesOpen },
                        ),
                    action = {
                        Icon(
                            imageVector = KpknLineIcons.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = if (musclesOpen) 180f else 0f },
                            tint = WizardColors.textMuted,
                        )
                    },
                )
                AnimatedVisibility(
                    visible = musclesOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (muscleAdjustments.isEmpty()) {
                            Text(
                                text = "No hay músculos detectados para esta sesión.",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        } else {
                            muscleAdjustments.keys.sorted().chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    pair.forEach { muscleId ->
                                        MuscleRuleCell(
                                            muscleId = muscleId,
                                            value = muscleAdjustments[muscleId] ?: 100,
                                            onChange = { onMuscleChange(muscleId, it) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Molestias previas: multi-selección con chips, buscador y sugerencias de los músculos de la sesión.
            KpknModule(
                modifier = Modifier.kpknEntrance("preparacion", 3),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                KpknModuleHeader(
                    label = "Molestias previas",
                    modifier = Modifier
                        .semantics { stateDescription = if (discomfortsOpen) "Desplegado" else "Plegado" }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { discomfortsOpen = !discomfortsOpen },
                        ),
                    action = {
                        Icon(
                            imageVector = KpknLineIcons.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = if (discomfortsOpen) 180f else 0f },
                            tint = WizardColors.textMuted,
                        )
                    },
                )
                Text(
                    text = countLabel,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                AnimatedVisibility(
                    visible = discomfortsOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KpknTextField(
                            value = discomfortQuery,
                            onValueChange = { discomfortQuery = it },
                            placeholder = "Buscar molestia…",
                            leadingIcon = KpknLineIcons.Search,
                            singleLine = true,
                        )
                        val searching = discomfortQuery.isNotBlank()
                        Text(
                            text = (if (searching) "Resultados" else "Sugeridas para tu sesión").uppercase(Locale("es")),
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                        )
                        val shown = if (searching) searchResults else suggestions
                        if (shown.isEmpty()) {
                            Text(
                                text = if (searching) {
                                    "Sin resultados para “$discomfortQuery”"
                                } else {
                                    "No hay molestias típicas para los músculos de esta sesión."
                                },
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                shown.forEach { item ->
                                    KpknChip(
                                        text = item.label,
                                        selected = selectedDiscomforts.contains(item.id),
                                        onClick = { toggleDiscomfort(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Voz: un interruptor en 56 dp y, al encenderla, el modo elegido y su explicación.
            KpknModule(
                modifier = Modifier.kpknEntrance("preparacion", 4),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voz durante la sesión",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                        Text(
                            text = if (voiceSessionEnabled) voiceModeTitle(voiceCaptureMode) else "Desactivada",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                    KpknSwitch(
                        checked = voiceSessionEnabled,
                        onCheckedChange = { onVoiceToggle() },
                        modifier = Modifier.semantics { contentDescription = "Voz durante la sesión" },
                    )
                }
                if (voiceSessionEnabled) {
                    Spacer(Modifier.height(8.dp))
                    KpknSegmentedControl(
                        options = listOf("Manos libres", "Música"),
                        selectedIndex = if (voiceCaptureMode == VoiceCaptureMode.MUSIC) 1 else 0,
                        onSelect = { index ->
                            onVoiceCaptureModeChange(if (index == 1) VoiceCaptureMode.MUSIC else VoiceCaptureMode.HANDS_FREE)
                        },
                        size = KpknSegmentedSize.Compact,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = VoiceExplanation,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }

            AnimatedVisibility(
                visible = voiceSessionEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                KpknModule(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        text = voiceModeBody(voiceCaptureMode),
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }

        // Empezar: a todo el ancho, fuera del desplazamiento. Su descripción es la que buscan las pruebas.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            KpknPrimaryPill(
                text = "Empezar",
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Confirmar y entrenar" },
            )
        }
    }
}

/** Etiqueta de un canal: punto de color, nombre y cifra con «%». Sin estado. */
@Composable
private fun ChannelLabel(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            modifier = Modifier.weight(1f),
        )
        KpknMetric(value = value.toString(), unit = "%", style = KpknType.metricM)
    }
}

/** Canal con regla propia: nombre y cifra, y debajo la regla de 0 a 100. Sin estado. */
@Composable
private fun ChannelRuleRow(
    label: String,
    value: Int,
    color: Color,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // La regla ya lleva el nombre y el valor para TalkBack: la fila visible no se lee dos veces.
        ChannelLabel(
            label = label,
            value = value,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        )
        KpknPercentRule(
            value = value,
            onValueChange = onChange,
            color = color,
            contentDescription = label,
        )
    }
}

/** Un músculo de la sesión: el nombre entero en su línea y, debajo, su cifra y su regla. Sin estado. */
@Composable
private fun MuscleRuleCell(
    muscleId: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // El nombre ocupa la celda entera y no se parte: a 130 % de letra sigue en una línea. La regla lleva el nombre para TalkBack.
        Text(
            text = muscleId,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clearAndSetSemantics {}) {
                KpknMetric(value = value.toString(), unit = "%", style = KpknType.metricM)
            }
            Spacer(Modifier.width(8.dp))
            KpknPercentRule(
                value = value,
                onValueChange = onChange,
                color = KpknRingColors.musculos,
                contentDescription = muscleId,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Explicación de la voz, antes de activarla. */
private const val VoiceExplanation = "Controla tu sesión hablando: registra series, cambia pesos y gestiona descansos con comandos de voz. " +
    "La voz estará activa durante toda la sesión. Ejemplos: “2 series más”, “sube a 80 kg”, “pausa de 90 segundos”."

private fun voiceModeTitle(mode: VoiceCaptureMode): String = when (mode) {
    VoiceCaptureMode.HANDS_FREE -> "Modo manos libres"
    VoiceCaptureMode.MUSIC -> "Modo música"
}

private fun voiceModeBody(mode: VoiceCaptureMode): String = when (mode) {
    VoiceCaptureMode.HANDS_FREE ->
        "Registra tus series con el micrófono de tus auriculares, incluso por bluetooth, sin acercarte al celular. " +
            "La contra: el audio suena con calidad de llamada y degrada la música de fondo."
    VoiceCaptureMode.MUSIC ->
        "Usa el micrófono del celular, no el de tus auriculares: tu música se escucha perfecta. " +
            "Solo necesitas tener el teléfono cerca (sin desbloquearlo) para registrar tus series por voz."
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
