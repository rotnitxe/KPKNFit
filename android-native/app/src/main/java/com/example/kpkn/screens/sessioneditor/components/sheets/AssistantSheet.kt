package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import com.example.kpkn.data.models.EnergyConfidence
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SessionEstimatedRings
import com.example.kpkn.screens.sessioneditor.buildMuscleVolumeRows
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState
import com.example.kpkn.ui.components.KpknAlertDialog

private val EnergyRingColor = com.example.kpkn.ui.theme.RingBlue
private val SpineRingColor = com.example.kpkn.ui.theme.RingYellow
private val MuscleRingColor = com.example.kpkn.ui.theme.RingRed
private val DirectBarColor = Color(0xFF22C55E)
private val IndirectBarColor = Color(0xFF64748B)

@Composable
private fun AssistantLightTabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AssistantGlassOverlay(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (String) -> Unit,
    onTemplateSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
) {
    val panelInteraction = remember { MutableInteractionSource() }
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Offset inmediato durante el drag; Animatable solo para el settle.
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val settleAnim = remember { Animatable(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(300f)) {
        val sheetHeightPx = with(density) { (maxHeight * 0.92f).toPx() }
        // Umbral de descarte: el menor entre ~150dp y el 25% de la altura del sheet.
        val dismissThresholdPx = with(density) {
            minOf(150.dp.toPx(), sheetHeightPx * 0.25f)
        }
        // Progreso 0..1 del arrastre para atenuar el scrim.
        val dragProgress = (dragOffsetPx / sheetHeightPx).coerceIn(0f, 1f)

        fun settle() {
            scope.launch {
                settleAnim.snapTo(dragOffsetPx)
                if (dragOffsetPx >= dismissThresholdPx) {
                    settleAnim.animateTo(sheetHeightPx, animationSpec = tween(durationMillis = 220)) {
                        dragOffsetPx = value
                    }
                    onDismiss()
                } else {
                    settleAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) {
                        dragOffsetPx = value
                    }
                }
            }
        }

        val dragModifier = Modifier.pointerInput(sheetHeightPx, dismissThresholdPx) {
            detectVerticalDragGestures(
                onDragEnd = { settle() },
                onDragCancel = { settle() },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                },
            )
        }

        // Scrim — tap outside to dismiss; se aclara al arrastrar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f * (1f - dragProgress)))
                .clickable(onClick = onDismiss),
        )
        // True bottom sheet: full-bleed, rounded top only, draggable handle/header
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                .clip(sheetShape)
                .kpknGlass(hazeState, sheetShape)
                .clickable(
                    interactionSource = panelInteraction,
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                // Zona táctil ampliada (~36dp) que captura el drag hacia abajo.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .then(dragModifier)
                        .semantics {
                            contentDescription = "Arrastra hacia abajo para minimizar el asistente"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }
                AssistantSheet(
                    uiState = uiState,
                    templates = templates,
                    onApplyAugeCorrection = onApplyAugeCorrection,
                    onAddGhostExercise = onAddGhostExercise,
                    onApplyAssistantSuggestion = onApplyAssistantSuggestion,
                    onTemplateSearchChange = onTemplateSearchChange,
                    onSelectTemplate = onSelectTemplate,
                    onConfirmApplyTemplate = onConfirmApplyTemplate,
                    onCancelTemplateApply = onCancelTemplateApply,
                )
            }
        }
    }
}

@Composable
internal fun AssistantSheet(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (String) -> Unit,
    onTemplateSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
) {
    // Keep unused callbacks referenced so signature stays stable for callers.
    @Suppress("UNUSED_EXPRESSION")
    onApplyAugeCorrection
    @Suppress("UNUSED_EXPRESSION")
    onAddGhostExercise

    val report = uiState.assistantReport
    val summary = uiState.augeSummary
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Métricas", "Sugerencias", "Plantillas")

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ASISTENTE DE SESIÓN",
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                AssistantLightTabChip(
                    label = title.uppercase(),
                    selected = selectedTab == index,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = index },
                )
            }
        }

        when (selectedTab) {
            1 -> AssistantSuggestionsTab(
                suggestions = report?.ajustes.orEmpty(),
                onApply = onApplyAssistantSuggestion,
            )
            2 -> AssistantTemplatesTab(
                templates = templates,
                searchQuery = uiState.templateSearchQuery,
                applyDecision = uiState.templateApplyDecision,
                onSearchChange = onTemplateSearchChange,
                onSelectTemplate = onSelectTemplate,
                onConfirmApplyTemplate = onConfirmApplyTemplate,
                onCancelApply = onCancelTemplateApply,
            )
            else -> AssistantMainTab(uiState = uiState)
        }
    }
}

@Composable
private fun AssistantMainTab(uiState: SessionEditorUiState) {
    val summary = uiState.augeSummary
    val report = uiState.assistantReport
    val timeBreakdown = uiState.sessionTimeBreakdown
    val energySummary = summary.sessionEnergy

    // 1. RINGS first (protagonist) — fill = estimated drain
    Text(
        "RINGS DE SESIÓN",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        color = Color.White.copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    SessionEstimatedRings(
        energyDrain = summary.sessionDrain.cns,
        spineDrain = summary.sessionDrain.spinal,
        muscleDrain = summary.sessionDrain.muscular,
    )

    // 2. Time as elegant text (no card)
    if (timeBreakdown != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${timeBreakdown.totalMinutes} min",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.92f),
            )
            Text(
                "Preparación ${timeBreakdown.setupMinutes} · Ejecución ${timeBreakdown.executionMinutes} · Descansos ${timeBreakdown.restMinutes}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    }

    // 3. Energy (kcal / EPOC) — translucent dark panel
    if (energySummary.totalKcal.mid > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Kcal", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                Text(
                    "${energySummary.totalKcal.mid}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    "${energySummary.totalKcal.low}–${energySummary.totalKcal.high}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EPOC", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                Text(
                    "${energySummary.epocKcal.mid} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Confianza", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                Text(
                    when (energySummary.confidence) {
                        EnergyConfidence.HIGH -> "alta"
                        EnergyConfidence.MEDIUM -> "media"
                        EnergyConfidence.LOW -> "baja"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = when (energySummary.confidence) {
                        EnergyConfidence.HIGH -> Color(0xFF22C55E)
                        EnergyConfidence.MEDIUM -> Color(0xFFF59E0B)
                        EnergyConfidence.LOW -> Color(0xFFEF4444)
                    },
                )
            }
        }
    }

    if (report == null) {
        Text(
            "Calculando análisis...",
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    // 4. Volume — always visible; empty state when no exercises
    val session = uiState.session
    val volumeRows = remember(session) {
        session?.let { buildMuscleVolumeRows(it) }.orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Volumen de entreno de la sesión",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        if (volumeRows.isEmpty()) {
            Text(
                "Agrega ejercicios a la sesión para ver el volumen por músculo.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            val maxDirect = volumeRows.maxOfOrNull { it.directSets }?.coerceAtLeast(1.0) ?: 1.0
            val maxIndirect = volumeRows.maxOfOrNull { it.indirectSets }?.coerceAtLeast(1.0) ?: 1.0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                volumeRows.forEach { row ->
                    MuscleVolumeCard(
                        muscle = row.muscle,
                        directSets = row.directSets,
                        indirectSets = row.indirectSets,
                        intensityLabel = row.intensity.label,
                        maxDirect = maxDirect,
                        maxIndirect = maxIndirect,
                        energyDrain = summary.muscleEnergyDrain[row.muscle] ?: 0,
                        spinalDrain = summary.muscleSpinalDrain[row.muscle] ?: 0,
                        muscleDrain = summary.muscleDrainProjection[row.muscle] ?: 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun MuscleVolumeCard(
    muscle: String,
    directSets: Double,
    indirectSets: Double,
    intensityLabel: String,
    maxDirect: Double,
    maxIndirect: Double,
    energyDrain: Int,
    spinalDrain: Int,
    muscleDrain: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            muscle,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )

        // Direct volume bar
        val directText = formatSets(directSets)
        VolumeBarRow(
            label = "$directText series · Intensidad $intensityLabel",
            progress = (directSets / maxDirect).toFloat().coerceIn(0f, 1f),
            color = DirectBarColor,
        )

        // Indirect volume bar (always shown when > 0; zero shows thin track)
        if (indirectSets > 0.0) {
            VolumeBarRow(
                label = "${formatSets(indirectSets)} series indirectas",
                progress = (indirectSets / maxIndirect).toFloat().coerceIn(0f, 1f),
                color = IndirectBarColor,
            )
        }

        // Physiological cost of that volume → ring drains
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrainChip(label = "Músculo", value = muscleDrain, color = MuscleRingColor)
            DrainChip(label = "Energía", value = energyDrain, color = EnergyRingColor)
            if (spinalDrain > 0) {
                DrainChip(label = "Columna", value = spinalDrain, color = SpineRingColor)
            }
        }
    }
}

@Composable
private fun VolumeBarRow(
    label: String,
    progress: Float,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun DrainChip(label: String, value: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            "$label $value%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatSets(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

@Composable
private fun AssistantSuggestionsTab(
    suggestions: List<com.example.kpkn.domain.sessionassistant.AssistantSuggestion>,
    onApply: (String) -> Unit,
) {
    if (suggestions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Sin sugerencias",
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                "Cuando el asistente detecte ajustes útiles aparecerán aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        suggestions.forEach { suggestion ->
            AssistantSuggestionCard(suggestion, onApply)
        }
    }
}

@Composable
internal fun AssistantTemplatesTab(
    templates: List<SessionTemplate>,
    searchQuery: String,
    applyDecision: SessionTemplateApplyDecision?,
    onSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelApply: () -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Buscar plantilla...") },
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
        ),
    )
    Box(modifier = Modifier.height(10.dp))
    TemplateCatalogBrowser(
        templates = templates,
        searchQuery = searchQuery,
        onSelectTemplate = onSelectTemplate,
        exerciseIndex = remember { EXERCISE_DATABASE.associateBy { it.id.lowercase() } },
        glassDark = true,
    )
    if (applyDecision != null) {
        KpknAlertDialog(
            onDismissRequest = onCancelApply,
            title = { Text("Aplicar plantilla", fontWeight = FontWeight.Black) },
            text = {
                Text("La sesión ya tiene ejercicios. ¿Qué deseas hacer con la plantilla \"${applyDecision.template.name}\"?")
            },
            confirmButton = {
                Button(onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.REPLACE) }) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.APPEND) }) {
                    Text("Añadir al final")
                }
            },
        )
    }
}

@Composable
internal fun AssistantSuggestionCard(
    suggestion: com.example.kpkn.domain.sessionassistant.AssistantSuggestion,
    onApplySuggestion: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(suggestion.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = Color.White)
        Text(suggestion.message, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
        if (suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.LOWER_RPE ||
            suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_SET ||
            suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.REMOVE_FAILURE
        ) {
            FilledTonalButton(onClick = { onApplySuggestion(suggestion.id) }) {
                Text("Aplicar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal fun buildExerciseCatalogLookup(catalog: List<ExerciseMuscleInfo>): Map<String, ExerciseMuscleInfo> {
    val base = catalog.associateBy { it.id.lowercase() }
    val aliasEntries = EXERCISE_ID_ALIASES.mapNotNull { (alias, canonical) ->
        base[canonical]?.let { alias.lowercase() to it }
    }.toMap()
    return base + aliasEntries
}

internal fun resolveCatalogExerciseInfo(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? {
    val byId = exercise.exerciseDbId ?: exercise.exerciseId
    return byId?.lowercase()?.let(catalogLookup::get)
        ?: catalogLookup.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
}

internal fun buildDiscomfortByExercise(
    workoutLogs: List<WorkoutLog>,
): Map<String, List<Pair<String, Int>>> {
    val map = mutableMapOf<String, MutableMap<String, Int>>()
    workoutLogs.forEach { log ->
        log.postExerciseReports.forEach { report ->
            val key = report.canonicalExerciseId ?: report.exerciseDbId ?: report.exerciseId
            if (key.isBlank()) return@forEach
            val bucket = map.getOrPut(key) { mutableMapOf() }
            report.discomfortIds
                .filter { it != "none" }
                .forEach { discomfortId ->
                    val label = discomfortLabel(discomfortId)
                    bucket[label] = (bucket[label] ?: 0) + 1
                }
        }
    }

    return map.mapValues { (_, value) ->
        value.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
    }
}
