package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
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
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogSearchRedirects
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
import com.example.kpkn.screens.sessioneditor.SessionSubMuscleBreakdownList
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetTranslucentButton
import com.example.kpkn.ui.components.kpknSheetWhiteFieldColors
import dev.chrisbanes.haze.HazeState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.width
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction

private val EnergyRingColor = com.example.kpkn.ui.theme.RingBlue
private val SpineRingColor = com.example.kpkn.ui.theme.RingYellow
private val MuscleRingColor = com.example.kpkn.ui.theme.RingRed
private val DirectBarColor = Color(0xFF22C55E)
private val SecondaryBarColor = Color(0xFF64748B)
private val StabilizerBarColor = Color(0xFF38BDF8)

@Composable
internal fun AssistantGlassOverlay(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState,
    onDismiss: () -> Unit,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
    onTemplateSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
) {
    // Uses KpknSheet (portal + LocalHazeState) so blur samples MainActivity's hazeSource.
    // Do NOT pass the session-local hazeState — the sheet is not a sibling of that source.
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.82f) {
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

@Composable
internal fun AssistantSheet(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
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
                KpknSheetLightChip(
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

    // 2. Duración — título + tarjeta
    if (timeBreakdown != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Duración de la sesión",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
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

    if (report == null) {
        Text(
            "Calculando análisis...",
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    // 3. Volumen (colapsado por defecto)
    val session = uiState.session
    val volumeRows = remember(session) {
        session?.let { buildMuscleVolumeRows(it) }.orEmpty()
    }
    var volumeExpanded by rememberSaveable { mutableStateOf(false) }
    fun toggleVolumeExpanded() {
        val next = !volumeExpanded
        // #region agent log
        com.example.kpkn.screens.sessioneditor.SessionEditorDebugLog.log(
            hypothesisId = "H-A",
            location = "AssistantSheet.kt:volumeHeaderClick",
            message = "Volume panel header clickable fired",
            data = mapOf(
                "wasExpanded" to volumeExpanded,
                "willExpand" to next,
                "rowCount" to volumeRows.size,
                "clickOnFullColumn" to false,
                "clickOnHeaderOnly" to true,
            ),
            runId = "post-fix",
        )
        // #endregion
        volumeExpanded = next
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toggleVolumeExpanded() },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Volumen de entreno de la sesión",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (volumeExpanded) "▲" else "▼",
                    color = Color.White.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!volumeExpanded) {
                Text(
                    if (volumeRows.isEmpty()) "Sin volumen aún" else "${volumeRows.size} músculos · tocar para ver",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
        if (volumeExpanded) {
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
            val maxSecondary = volumeRows.maxOfOrNull { it.secondarySets }?.coerceAtLeast(1.0) ?: 1.0
            val maxStabilizer = volumeRows.maxOfOrNull { it.stabilizerSets }?.coerceAtLeast(1.0) ?: 1.0
            // Sin scroll anidado: el sheet padre ya scrollea (evita jank al abrir).
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                volumeRows.take(12).forEach { row ->
                    MuscleVolumeCard(
                        muscle = row.muscle,
                        directSets = row.directSets,
                        secondarySets = row.secondarySets,
                        stabilizerSets = row.stabilizerSets,
                        intensityLabel = row.intensity.label,
                        maxDirect = maxDirect,
                        maxSecondary = maxSecondary,
                        maxStabilizer = maxStabilizer,
                        energyDrain = summary.muscleEnergyDrain[row.muscle] ?: 0,
                        spinalDrain = summary.muscleSpinalDrain[row.muscle] ?: 0,
                        muscleDrain = summary.muscleDrainProjection[row.muscle] ?: 0,
                        session = session,
                    )
                }
                if (volumeRows.size > 12) {
                    Text(
                        "+${volumeRows.size - 12} músculos más en el desglose interno",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
        }
        }
    }

    // 4. Calorías (active + EPOC del motor auge-energy-v2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Calorías estimadas",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        if (energySummary.totalKcal.mid > 0) {
            Text(
                "${energySummary.totalKcal.mid} kcal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                "${energySummary.totalKcal.low}–${energySummary.totalKcal.high}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
            )
            val loadHint = energySummary.notes.firstOrNull {
                it.contains("1RM", ignoreCase = true) || it.contains("carga", ignoreCase = true)
            }
            if (loadHint != null) {
                Text(
                    loadHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text(
                "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.55f),
            )
            Text(
                energySummary.notes.firstOrNull {
                    it.contains("peso", ignoreCase = true) ||
                        it.contains("1RM", ignoreCase = true) ||
                        it.contains("estimar", ignoreCase = true)
                } ?: "Completa pesos o 1RM en los ejercicios para estimar el gasto calórico",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MuscleVolumeCard(
    muscle: String,
    directSets: Double,
    secondarySets: Double,
    stabilizerSets: Double,
    intensityLabel: String,
    maxDirect: Double,
    maxSecondary: Double,
    maxStabilizer: Double,
    energyDrain: Int,
    spinalDrain: Int,
    muscleDrain: Int,
    session: com.example.kpkn.data.models.Session? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)
    val hasPortions = canonical in setOf("Deltoides", "Glúteos", "Pectorales")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasPortions) Modifier.clickable { expanded = !expanded } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                muscle,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (hasPortions) {
                Text(
                    if (expanded) " ▴" else " ▾",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Direct volume bar
        val directText = formatSets(directSets)
        VolumeBarRow(
            label = "$directText series · Intensidad $intensityLabel",
            progress = (directSets / maxDirect).toFloat().coerceIn(0f, 1f),
            color = DirectBarColor,
        )

        // Secondary volume bar
        if (secondarySets > 0.0) {
            VolumeBarRow(
                label = "${formatSets(secondarySets)} series como secundario",
                progress = (secondarySets / maxSecondary).toFloat().coerceIn(0f, 1f),
                color = SecondaryBarColor,
            )
        }

        // Stabilizer volume bar
        if (stabilizerSets > 0.0) {
            VolumeBarRow(
                label = "${formatSets(stabilizerSets)} series como estabilizador",
                progress = (stabilizerSets / maxStabilizer).toFloat().coerceIn(0f, 1f),
                color = StabilizerBarColor,
            )
        }

        if (expanded && hasPortions && session != null) {
            SessionSubMuscleBreakdownList(
                muscleName = canonical,
                session = session,
                countIndirect = true,
                adjustByIntensity = false,
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
    suggestions: List<AssistantSuggestion>,
    onApply: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
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
                "Si la sesión se pone demasiado exigente, aparecerán ajustes aquí.",
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
        placeholder = {
            Text("Buscar plantilla...", color = KpknSheetTokens.ControlPlaceholder)
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                null,
                modifier = Modifier.size(18.dp),
                tint = KpknSheetTokens.ControlLabel,
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = KpknSheetTokens.ControlLabel),
        colors = kpknSheetWhiteFieldColors(),
    )
    Box(modifier = Modifier.height(10.dp))
    TemplateCatalogBrowser(
        templates = templates,
        searchQuery = searchQuery,
        onSelectTemplate = onSelectTemplate,
        exerciseIndex = remember { exerciseCatalogSnapshot().associateBy { it.id.lowercase() } },
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
                Button(
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.REPLACE) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.APPEND) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Text("Añadir al final")
                }
            },
        )
    }
}

@Composable
internal fun AssistantSuggestionCard(
    suggestion: AssistantSuggestion,
    onApplySuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
) {
    val accepted = remember(suggestion.id, suggestion.details) {
        mutableStateMapOf<String, Boolean>().apply {
            suggestion.details.forEach { detail ->
                put(detail.id, detail.defaultAccepted)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(suggestion.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = Color.White)
        Text(suggestion.message, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
        if (suggestion.details.isNotEmpty()) {
            suggestion.details.forEach { detail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            accepted[detail.id] = !(accepted[detail.id] ?: true)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = accepted[detail.id] ?: detail.defaultAccepted,
                        onCheckedChange = { accepted[detail.id] = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White.copy(alpha = 0.85f),
                            uncheckedColor = Color.White.copy(alpha = 0.35f),
                            checkmarkColor = Color.Black,
                        ),
                    )
                    Text(
                        detail.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val selectedIds = suggestion.details.map { it.id }.filter { accepted[it] == true }
            KpknSheetTranslucentButton(
                text = if (selectedIds.isEmpty()) "Nada seleccionado" else "Aplicar selección",
                enabled = selectedIds.isNotEmpty(),
                onClick = { onApplySuggestion(suggestion.id, selectedIds) },
            )
        } else {
            KpknSheetTranslucentButton(
                text = "Aplicar",
                onClick = { onApplySuggestion(suggestion.id, emptyList()) },
            )
        }
    }
}

internal fun buildExerciseCatalogLookup(catalog: List<ExerciseMuscleInfo>): Map<String, ExerciseMuscleInfo> {
    val base = catalog.associateBy { it.id.lowercase() }
    val aliasEntries = catalogSearchRedirects().mapNotNull { (alias, canonical) ->
        base[canonical]?.let { alias.lowercase() to it }
    }.toMap()
    return base + aliasEntries
}

internal fun resolveCatalogExerciseInfo(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? {
    val byId = exercise.catalogConfigurationId ?: exercise.exerciseDbId ?: exercise.exerciseId
    return byId?.trim()?.lowercase()?.let(catalogLookup::get)
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
