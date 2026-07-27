package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.SessionDiscomfortSummary
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.workout.components.AdjustableRingCompact
import com.example.kpkn.screens.workout.components.MinimalMuscleSlider
import dev.chrisbanes.haze.HazeState

internal const val FINISH_ROLE_STABILIZER_MULT = 0.4

internal fun computeInitialFinishMuscleBatteries(
    startBatteries: Map<String, Int>,
    perMuscleMuscularDrain: Map<String, Int>,
): Map<String, Int> {
    if (startBatteries.isEmpty()) return emptyMap()
    return startBatteries.mapValues { (muscle, rawStart) ->
        val start = rawStart.coerceIn(0, 100)
        val drain = perMuscleMuscularDrain[muscle]
            ?: perMuscleMuscularDrain.entries.firstOrNull {
                getAugeMusclePillarId(it.key) == getAugeMusclePillarId(muscle)
            }?.value
            ?: 0
        (start - drain).coerceIn(0, 100)
    }
}

internal fun computeSessionMuscleRoleWeightedSets(
    completedExercises: List<CompletedExercise>,
): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    completedExercises.forEach { ex ->
        val dbInfo = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
            ?: EXERCISE_DATABASE_BY_ID.values.firstOrNull { it.name.equals(ex.exerciseName, ignoreCase = true) }
            ?: return@forEach

        val effectiveSetCount = ex.sets.count { set ->
            !set.isWarmup && AugeFatigueEngine.isSetEffective(set)
        }
        if (effectiveSetCount <= 0) return@forEach

        dbInfo.involvedMuscles.forEach { involvement ->
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val muscleId = getAugeMusclePillarId(canonical, involvement.emphasis)
            val roleMultiplier = when (involvement.role) {
                MuscleRole.PRIMARY -> 1.0
                MuscleRole.SECONDARY -> 0.5
                MuscleRole.STABILIZER -> FINISH_ROLE_STABILIZER_MULT
                MuscleRole.NEUTRALIZER -> FINISH_ROLE_STABILIZER_MULT
            }
            val weighted = effectiveSetCount * roleMultiplier
            result[muscleId] = (result[muscleId] ?: 0.0) + weighted
        }
    }
    return result
}

// ─── Finish Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FinishWorkoutSheet(
    session: Session,
    completedSets: Map<String, CompletedSet>,
    completedExercises: List<CompletedExercise>,
    durationMinutes: Int,
    sessionIntensityResult: SessionIntensityResult,
    predictedDrain: PredictedDrain,
    readinessNeuralStart: Int,
    readinessSpinalStart: Int,
    hazeState: HazeState,
    sessionMuscleStartBatteries: Map<String, Int> = emptyMap(),
    sessionMuscleVolumeByRoleSets: Map<String, Double> = emptyMap(),
    perMuscleMuscularDrain: Map<String, Int> = emptyMap(),
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback> = emptyMap(),
    sessionDiscomfortSummary: List<SessionDiscomfortSummary> = emptyList(),
    voiceFinalNotes: String? = null,
    voiceFinalDiscomforts: List<String> = emptyList(),
    voiceFinalAdditionalDiscomfortNote: String? = null,
    voiceFinalNeural: Int? = null,
    voiceFinalSpinal: Int? = null,
    voiceFinalConfirmTriggered: Boolean = false,
    isFinishingWorkout: Boolean = false,
    onConfirm: (String, Int, SessionClosingFeedback, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    val neuralSeed = (readinessNeuralStart - predictedDrain.cns).coerceIn(0, 100)
    val spinalSeed = (readinessSpinalStart - predictedDrain.spinal).coerceIn(0, 100)
    val muscleSeed = remember(sessionMuscleStartBatteries, perMuscleMuscularDrain) {
        computeInitialFinishMuscleBatteries(
            startBatteries = sessionMuscleStartBatteries,
            perMuscleMuscularDrain = perMuscleMuscularDrain,
        )
    }
    var neuralFinal by remember(neuralSeed) { mutableIntStateOf(neuralSeed) }
    var spinalFinal by remember(spinalSeed) { mutableIntStateOf(spinalSeed) }
    var neuralEdited by remember(neuralSeed) { mutableStateOf(false) }
    var spinalEdited by remember(spinalSeed) { mutableStateOf(false) }
    var musclesEdited by remember(muscleSeed) { mutableStateOf(false) }
    val muscleFinal = remember(muscleSeed) {
        mutableStateMapOf<String, Int>().also { map -> map.putAll(muscleSeed) }
    }

    val derivedMuscularFinal by remember(muscleFinal) {
        derivedStateOf {
            if (muscleFinal.isEmpty()) {
                val predictedMuscularStart = sessionMuscleStartBatteries.values.average().takeIf { !it.isNaN() }?.toInt() ?: 100
                (predictedMuscularStart - predictedDrain.muscular).coerceIn(0, 100)
            } else {
                muscleFinal.values.average().toInt().coerceIn(0, 100)
            }
        }
    }

    var showMuscleSetsBreakdown by remember { mutableStateOf(false) }
    var additionalDiscomfortNote by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedDiscomforts by remember {
        mutableStateOf(
            postExerciseFeedbackByExerciseId
                .values
                .flatMap { it.discomfortIds }
                .filter { it != "none" }
                .toSet(),
        )
    }
    var showDiscomfortAccordion by remember { mutableStateOf(true) }
    var discomfortSearchQuery by remember { mutableStateOf("") }
    var discomfortStillPresent by remember {
        mutableStateOf(selectedDiscomforts.associateWith { true })
    }
    var shareToStory by remember { mutableStateOf(false) }

    LaunchedEffect(voiceFinalNotes) {
        if (voiceFinalNotes != null) {
            notes = voiceFinalNotes
        }
    }
    LaunchedEffect(voiceFinalAdditionalDiscomfortNote) {
        if (voiceFinalAdditionalDiscomfortNote != null) {
            additionalDiscomfortNote = voiceFinalAdditionalDiscomfortNote
        }
    }
    LaunchedEffect(voiceFinalNeural) {
        if (voiceFinalNeural != null) {
            neuralFinal = voiceFinalNeural
            neuralEdited = true
        }
    }
    LaunchedEffect(voiceFinalSpinal) {
        if (voiceFinalSpinal != null) {
            spinalFinal = voiceFinalSpinal
            spinalEdited = true
        }
    }
    LaunchedEffect(voiceFinalDiscomforts) {
        if (voiceFinalDiscomforts.isNotEmpty()) {
            selectedDiscomforts = selectedDiscomforts + voiceFinalDiscomforts
        }
    }

    val totalSets = completedSets.size
    val totalVolume = completedSets.values.sumOf { it.weight * it.reps }
    val allSets = remember(completedSets) {
        completedSets.values
            .filter { !it.isWarmup }
            .toList()
    }
    val unifiedEffort = remember(allSets) { calculateUnifiedSessionEffortSignal(allSets) }
    val inferredFatigue = remember(unifiedEffort) {
        when {
            unifiedEffort >= 10.5 -> 5
            unifiedEffort >= 9.2 -> 4
            unifiedEffort >= 7.8 -> 3
            unifiedEffort >= 6.4 -> 2
            else -> 1
        }
    }
    val averageTechnique = remember(postExerciseFeedbackByExerciseId) {
        postExerciseFeedbackByExerciseId.values
            .map { it.technicalQuality }
            .average()
            .takeIf { !it.isNaN() }
            ?.coerceIn(1.0, 10.0)
            ?: 8.0
    }
    val hasGenericIntensityFallback = remember(postExerciseFeedbackByExerciseId) {
        postExerciseFeedbackByExerciseId.values.any { it.perceivedIntensityRpe == null && !it.perceivedFailure }
    }

    val weightedSetByMuscleSorted = remember(sessionMuscleVolumeByRoleSets) {
        sessionMuscleVolumeByRoleSets.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
    }

    // Explicit back / scrim / drag closes finish sheet (does not abandon the workout).
    BackHandler(enabled = true) { onDismiss() }

    KpknSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                    text = "RESUMEN DE ENTRENAMIENTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = session.name,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                // 1. CARDS RESUMEN / ESTADO FINAL DE RINGS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FinishSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Músculos",
                        value = derivedMuscularFinal,
                        color = com.example.kpkn.ui.theme.RingRed
                    )
                    FinishSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Energía",
                        value = neuralFinal,
                        color = com.example.kpkn.ui.theme.RingBlue
                    )
                    FinishSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Columna",
                        value = spinalFinal,
                        color = com.example.kpkn.ui.theme.RingYellow
                    )
                }

                // 2. ACCORDEÓN COLAPSABLE DE AJUSTES (RECALIBRAR RINGS)
                var isAdjustExpanded by rememberSaveable { mutableStateOf(false) }
                Surface(
                    onClick = { isAdjustExpanded = !isAdjustExpanded },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Recalibrar Rings",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ajustar valores finales de recuperación",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isAdjustExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isAdjustExpanded) "Contraer" else "Expandir",
                            tint = Color.White
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isAdjustExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Arrastra verticalmente sobre los RINGS para modificar Energía (SNC) y Columna (Spinal). Desliza horizontalmente sobre las barras para ajustar la frescura muscular final.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Justify
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AdjustableRingCompact(
                                modifier = Modifier.weight(1f),
                                title = "Energía",
                                value = neuralFinal,
                                ringColor = com.example.kpkn.ui.theme.RingBlue,
                                ringSize = 120,
                                onValueChange = {
                                    neuralFinal = it
                                    neuralEdited = true
                                },
                            )
                            AdjustableRingCompact(
                                modifier = Modifier.weight(1f),
                                title = "Columna",
                                value = spinalFinal,
                                ringColor = com.example.kpkn.ui.theme.RingYellow,
                                ringSize = 120,
                                onValueChange = {
                                    spinalFinal = it
                                    spinalEdited = true
                                },
                            )
                        }

                        if (muscleFinal.isNotEmpty()) {
                            Text(
                                text = "Desgaste final por Músculo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2,
                            ) {
                                muscleFinal.keys.sortedByDescending { sessionMuscleVolumeByRoleSets[it] ?: 0.0 }.forEach { muscleId ->
                                    val start = sessionMuscleStartBatteries[muscleId]?.coerceIn(0, 100) ?: 100
                                    val current = muscleFinal[muscleId]?.coerceIn(0, 100) ?: start
                                    MinimalMuscleSlider(
                                        modifier = Modifier.weight(1f),
                                        muscleLabel = muscleId,
                                        value = current,
                                        onValueChange = { updated ->
                                            muscleFinal[muscleId] = updated
                                            musclesEdited = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. COLLAPSIBLE GENERAL SESSION STATS
                Surface(
                    onClick = { showMuscleSetsBreakdown = !showMuscleSetsBreakdown },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Resumen de Carga",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (showMuscleSetsBreakdown) "Ocultar detalles" else "Ver detalles",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Duración: ${durationMinutes} min  ·  Tonelaje: ${"%.0f".format(totalVolume)} kg  ·  Series: $totalSets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedVisibility(visible = showMuscleSetsBreakdown) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                weightedSetByMuscleSorted.forEach { (muscle, weightedSets) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = muscle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${"%.1f".format(weightedSets)} series",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasGenericIntensityFallback) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Se usó intensidad genérica en ejercicios sin RPE percibido. Registra la intensidad para mejorar las estimaciones de recuperación.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // 4. METRICS ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val intensityColor = when {
                        sessionIntensityResult.adjustedNumericValue >= 8.0 -> Color(0xFF66BB6A)
                        sessionIntensityResult.adjustedNumericValue >= 5.0 -> Color(0xFFFFCA28)
                        else -> Color(0xFFEF5350)
                    }
                    MetricValueCard(
                        modifier = Modifier.weight(1f),
                        title = "Intensidad",
                        value = sessionIntensityResult.displayLabel,
                        subtitle = if (sessionIntensityResult.normalizationFactor < 1.0) {
                            "RPE prom. (aj. ×${"%.1f".format(sessionIntensityResult.normalizationFactor)})"
                        } else {
                            "RPE promedio"
                        },
                        valueColor = intensityColor
                    )
                    MetricValueCard(
                        modifier = Modifier.weight(1f),
                        title = "Técnica",
                        value = "${"%.1f".format(averageTechnique)}/10",
                        subtitle = "Calidad prom.",
                        valueColor = Color.White
                    )
                }

                // 5. MOLESTIAS (ACORDEÓN)
                Surface(
                    onClick = { showDiscomfortAccordion = !showDiscomfortAccordion },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Molestias reportadas (${selectedDiscomforts.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Icon(
                                imageVector = if (showDiscomfortAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showDiscomfortAccordion) "Contraer" else "Expandir",
                                tint = Color.White.copy(alpha = 0.6f),
                            )
                        }

                        AnimatedVisibility(visible = showDiscomfortAccordion) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    text = "Durante la sesión reportaste estas molestias. ¿Sigues sintiéndolas?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Top 5 por volumen articular
                                if (sessionDiscomfortSummary.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        sessionDiscomfortSummary.forEach { summary ->
                                            val selected = selectedDiscomforts.contains(summary.discomfortId)
                                            val stillPresent = discomfortStillPresent[summary.discomfortId] ?: true
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        if (!selected) {
                                                            selectedDiscomforts = selectedDiscomforts + summary.discomfortId
                                                        }
                                                        discomfortStillPresent = discomfortStillPresent.toMutableMap().apply {
                                                            put(summary.discomfortId, !stillPresent)
                                                        }
                                                    },
                                                color = Color(0xFF2A2A2A),
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = if (stillPresent) Icons.Default.Check else Icons.Default.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = if (stillPresent) Color(0xFF66BB6A) else Color(0xFF9E9E9E),
                                                    )
                                                    Column {
                                                        Text(
                                                            text = summary.label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (stillPresent) Color(0xFFB0B0B0) else Color(0xFFB0B0B0).copy(alpha = 0.5f),
                                                            textDecoration = if (stillPresent) TextDecoration.None else TextDecoration.LineThrough,
                                                        )
                                                        if (summary.reportedInExercises.isNotEmpty()) {
                                                            Text(
                                                                text = "en ${summary.reportedInExercises.first()}",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Buscador
                                OutlinedTextField(
                                    value = discomfortSearchQuery,
                                    onValueChange = { discomfortSearchQuery = it },
                                    label = { Text("Buscar molestia") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.4f),
                                        )
                                    },
                                )

                                if (discomfortSearchQuery.isNotBlank()) {
                                    val filtered = DISCOMFORT_CATALOG_BY_ID.values
                                        .filter { it.id != "none" }
                                        .filter { it.label.contains(discomfortSearchQuery, ignoreCase = true) }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        filtered.forEach { entry ->
                                            val selected = selectedDiscomforts.contains(entry.id)
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        selectedDiscomforts = if (selected) selectedDiscomforts - entry.id else selectedDiscomforts + entry.id
                                                    },
                                                color = if (selected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFF2A2A2A),
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Text(
                                                    text = entry.label,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFB0B0B0),
                                                )
                                            }
                                        }
                                    }
                                } else if (sessionDiscomfortSummary.isEmpty()) {
                                    // Mostrar catálogo completo cuando no hay top 5 ni búsqueda
                                    val catalogOptions = DISCOMFORT_CATALOG_BY_ID.values
                                        .filter { it.id != "none" }
                                        .sortedBy { it.label }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        catalogOptions.forEach { entry ->
                                            val selected = selectedDiscomforts.contains(entry.id)
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        selectedDiscomforts = if (selected) selectedDiscomforts - entry.id else selectedDiscomforts + entry.id
                                                    },
                                                color = if (selected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFF2A2A2A),
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Text(
                                                    text = entry.label,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFB0B0B0),
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = additionalDiscomfortNote,
                                    onValueChange = { additionalDiscomfortNote = it },
                                    label = { Text("Molestia adicional (ej: rodilla izquierda)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    ),
                                )
                            }
                        }
                    }
                }

                // 6. NOTAS RÁPIDAS
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Nota rápida de la sesión (opcional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                val executeConfirm = {
                    val perceivedMuscularDrop = if (muscleFinal.isEmpty()) {
                        predictedDrain.muscular.toDouble()
                    } else {
                        muscleFinal.entries
                            .map { (muscle, finalValue) ->
                                val start = sessionMuscleStartBatteries[muscle] ?: 100
                                (start - finalValue).toDouble()
                            }
                            .average()
                    }
                    val muscularAdjustment = (
                        perceivedMuscularDrop.toInt() - predictedDrain.muscular
                        ).coerceIn(-35, 35)
                    val discomfortLabels = selectedDiscomforts
                        .mapNotNull { id -> DISCOMFORT_CATALOG_BY_ID[id]?.label }
                        .distinct()
                    val stillPresentIds = selectedDiscomforts
                        .filter { id -> discomfortStillPresent[id] ?: true }
                        .toList()

                    onConfirm(
                        notes,
                        inferredFatigue,
                        SessionClosingFeedback(
                            overallFatigue = inferredFatigue,
                            systemAdjustment = (
                                (readinessNeuralStart - neuralFinal) - predictedDrain.cns
                                ).coerceIn(-35, 35),
                            muscularAdjustment = muscularAdjustment,
                            structureAdjustment = (
                                (readinessSpinalStart - spinalFinal) - predictedDrain.spinal
                                ).coerceIn(-35, 35),
                            discomforts = discomfortLabels + listOfNotNull(
                                additionalDiscomfortNote.trim().takeIf { it.isNotBlank() },
                            ),
                            clarityRating = averageTechnique.toInt().coerceIn(1, 10),
                            environmentTags = emptyList(),
                            finalNeuralBattery = neuralFinal,
                            finalSpinalBattery = spinalFinal,
                            finalMuscleBatteries = if (musclesEdited) muscleFinal.toMap() else emptyMap(),
                            neuralEdited = neuralEdited,
                            spinalEdited = spinalEdited,
                            musclesEdited = musclesEdited,
                            additionalDiscomfortNote = additionalDiscomfortNote.trim().takeIf { it.isNotBlank() },
                            stillPresentDiscomfortIds = stillPresentIds,
                        ),
                        shareToStory,
                    )
                }

                LaunchedEffect(voiceFinalConfirmTriggered) {
                    if (voiceFinalConfirmTriggered) {
                        executeConfirm()
                    }
                }

                // 7. BOTÓN COMPARTIR
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE1306C), // Instagram Pink/Red brand color
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Compartir en Instagram Stories",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Escape first, then irreversible confirm — reduces fat-finger risk.
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White.copy(alpha = 0.85f),
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = "Volver al entrenamiento",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Button(
                    onClick = { if (!isFinishingWorkout) executeConfirm() },
                    enabled = !isFinishingWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isFinishingWorkout) "Guardando…" else "Guardar y Terminar",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
    }
}

@Composable
private fun FinishSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                FinishCircularProgressVisual(value = value, color = color)
                Text(
                    text = "${value}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = color,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun FinishCircularProgressVisual(
    value: Int,
    color: Color
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100) / 100f),
        label = "finishCircularProgressVisual",
    )

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = 4.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.1f),
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
private fun MetricValueCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = Color.White,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = valueColor,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
// ─── Exercise Tag-Only Sheet ──────────────────────────────────────────────────

