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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
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
import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.home.drawAugeRingBlooms
import com.example.kpkn.screens.home.drawAugeRingCore
import com.example.kpkn.screens.home.drawAugeRingTrack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.PostSessionPreview
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

internal fun computeSessionMuscleRoleWeightedSets(
    completedExercises: List<CompletedExercise>,
): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    completedExercises.forEach { ex ->
        val dbInfo = resolveCatalogExerciseInfo(
            catalogConfigurationId = ex.catalogConfigurationId,
            exerciseDbId = ex.exerciseDbId,
            exerciseId = ex.exerciseId,
            exerciseName = ex.exerciseName,
        ) ?: return@forEach

        val effectiveSetCount = ex.sets.count { set ->
            !set.isWarmup && AugeFatigueEngine.isSetEffective(set)
        }
        if (effectiveSetCount <= 0) return@forEach

        val involvedMuscles = ex.effectiveMuscles?.takeIf { it.isNotEmpty() } ?: dbInfo.involvedMuscles
        involvedMuscles.forEach { involvement ->
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val muscleId = getAugeMusclePillarId(canonical, involvement.emphasis)
            val roleMultiplier = when (involvement.role) {
                MuscleRole.PRIMARY -> 1.0
                MuscleRole.SECONDARY -> 0.5
                MuscleRole.STABILIZER -> FINISH_ROLE_STABILIZER_MULT
                MuscleRole.NEUTRALIZER -> 0.0
            }
            if (roleMultiplier <= 0.0) return@forEach
            val weighted = effectiveSetCount * roleMultiplier
            result[muscleId] = (result[muscleId] ?: 0.0) + weighted
        }
    }
    return result
}

/**
 * Applies only the edited canonical muscles to the automatic global battery.
 * This is a weighted canonical aggregator, never an average of the internal
 * recovery battery universe.
 */
internal fun recalibratedCanonicalMuscularBattery(
    preview: PostSessionPreview,
    automaticSeed: Map<String, Int>,
    currentValues: Map<String, Int>,
    editedMuscleKeys: Set<String>,
    roleWeightedSets: Map<String, Double> = emptyMap(),
): Int {
    if (editedMuscleKeys.isEmpty()) return preview.muscular.coerceIn(0, 100)
    val impactWeights = preview.automaticImpact?.perMuscle.orEmpty()
    val weightedDelta = editedMuscleKeys.sumOf { muscle ->
        val seed = automaticSeed[muscle] ?: return@sumOf 0.0
        val current = currentValues[muscle] ?: seed
        val weight = impactWeights[muscle]?.stressUnits?.takeIf { it > 0.0 }
            ?: roleWeightedSets[muscle]?.takeIf { it > 0.0 }
            ?: 1.0
        (current - seed) * weight
    }
    val totalWeight = editedMuscleKeys.sumOf { muscle ->
        impactWeights[muscle]?.stressUnits?.takeIf { it > 0.0 }
            ?: roleWeightedSets[muscle]?.takeIf { it > 0.0 }
            ?: 1.0
    }.coerceAtLeast(1.0)
    return (preview.muscular + weightedDelta / totalWeight).toInt().coerceIn(0, 100)
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
    postSessionPreview: PostSessionPreview,
    hazeState: HazeState,
    sessionMuscleVolumeByRoleSets: Map<String, Double> = emptyMap(),
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback> = emptyMap(),
    sessionDiscomfortSummary: List<SessionDiscomfortSummary> = emptyList(),
    initialSessionNotes: String = "",
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
    onSummaryReady: (WorkoutSessionSummary) -> Unit,
) {
    val neuralSeed = postSessionPreview.neural.coerceIn(0, 100)
    val spinalSeed = postSessionPreview.spinal.coerceIn(0, 100)
    val muscleSeed = remember(postSessionPreview.perMuscle, postSessionPreview.involvedVolumeMuscles) {
        val involved = postSessionPreview.involvedVolumeMuscles
        postSessionPreview.perMuscle
            .filterKeys { involved.isEmpty() || it in involved }
            .mapValues { it.value.recoveryScore }
    }
    var neuralFinal by remember(neuralSeed) { mutableIntStateOf(neuralSeed) }
    var spinalFinal by remember(spinalSeed) { mutableIntStateOf(spinalSeed) }
    var neuralEdited by remember(neuralSeed) { mutableStateOf(false) }
    var spinalEdited by remember(spinalSeed) { mutableStateOf(false) }
    var musclesEdited by remember(muscleSeed) { mutableStateOf(false) }
    val muscleFinal = remember(muscleSeed) {
        mutableStateMapOf<String, Int>().also { map -> map.putAll(muscleSeed) }
    }

    val editedMuscleKeys by remember(muscleFinal, muscleSeed) {
        derivedStateOf { muscleFinal.keys.filterTo(mutableSetOf()) { muscleFinal[it] != muscleSeed[it] } }
    }
    val derivedMuscularFinal by remember(muscleFinal, editedMuscleKeys, postSessionPreview) {
        derivedStateOf {
            recalibratedCanonicalMuscularBattery(
                preview = postSessionPreview,
                automaticSeed = muscleSeed,
                currentValues = muscleFinal,
                editedMuscleKeys = editedMuscleKeys,
                roleWeightedSets = sessionMuscleVolumeByRoleSets,
            )
        }
    }
    val lowestMuscle = muscleFinal.minByOrNull { it.value }?.key
    LaunchedEffect(
        sessionIntensityResult.displayLabel,
        sessionDiscomfortSummary,
        derivedMuscularFinal,
        lowestMuscle,
        neuralFinal,
        spinalFinal,
    ) {
        onSummaryReady(
            WorkoutSessionSummary(
                intensityDescriptor = sessionIntensityResult.displayLabel,
                discomforts = sessionDiscomfortSummary.map { summary ->
                    "${summary.label} en ${summary.reportedInExercises.joinToString(", ")}"
                },
                muscularRing = derivedMuscularFinal,
                lowestMuscle = lowestMuscle,
                energyRing = neuralFinal,
                spinalRing = spinalFinal,
                nextSessionText = "No existe una próxima fecha fiable calculada.",
                isPlaceholder = postSessionPreview.perMuscle.isEmpty(),
            ),
        )
    }

    var showMuscleSetsBreakdown by remember { mutableStateOf(false) }
    var additionalDiscomfortNote by remember { mutableStateOf("") }
    var notes by remember(initialSessionNotes) { mutableStateOf(initialSessionNotes) }
    var selectedDiscomforts by remember {
        mutableStateOf(
            postExerciseFeedbackByExerciseId
                .values
                .flatMap { it.discomfortIds }
                .filter { it != "none" }
                .toSet(),
        )
    }
    var showDiscomfortAccordion by remember { mutableStateOf(false) }
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

    val totalSets = completedSets.values.count { !it.isWarmup }
    val totalVolume = completedSets.values.filter { !it.isWarmup }.sumOf { it.weight * it.reps }
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

    val executeConfirm = {
        val muscularAdjustment = if (editedMuscleKeys.isEmpty()) {
            0
        } else {
            (postSessionPreview.muscular - derivedMuscularFinal).coerceIn(-35, 35)
        }
        val stillPresentIds = selectedDiscomforts
            .filter { id -> discomfortStillPresent[id] ?: true }
            .toList()

        // Trazabilidad: molestias reportadas durante la sesión se
        // conservan en el log aunque el usuario ya no las sienta
        // (stillPresent = false) o las haya deseleccionado.
        val sessionReportedDiscomfortIds = postExerciseFeedbackByExerciseId
            .values
            .flatMap { it.discomfortIds }
            .filter { it != "none" }
            .toSet()
        val allSessionDiscomfortLabels = (sessionReportedDiscomfortIds + selectedDiscomforts)
            .mapNotNull { id -> DISCOMFORT_CATALOG_BY_ID[id]?.label }
            .distinct()

        onConfirm(
            notes,
            inferredFatigue,
            SessionClosingFeedback(
                overallFatigue = inferredFatigue,
                systemAdjustment = (
                    postSessionPreview.neural - neuralFinal
                    ).coerceIn(-35, 35),
                muscularAdjustment = muscularAdjustment,
                structureAdjustment = (
                    postSessionPreview.spinal - spinalFinal
                    ).coerceIn(-35, 35),
                discomforts = allSessionDiscomfortLabels + listOfNotNull(
                    additionalDiscomfortNote.trim().takeIf { it.isNotBlank() },
                ),
                clarityRating = averageTechnique.toInt().coerceIn(1, 10),
                environmentTags = emptyList(),
                finalNeuralBattery = neuralFinal,
                finalSpinalBattery = spinalFinal,
                finalMuscleBatteries = editedMuscleKeys.associateWith { muscle ->
                    muscleFinal[muscle] ?: muscleSeed[muscle] ?: 100
                },
                neuralEdited = neuralEdited,
                spinalEdited = spinalEdited,
                musclesEdited = editedMuscleKeys.isNotEmpty(),
                editedMuscleKeys = editedMuscleKeys,
                finishOperationId = postSessionPreview.finishOperationId,
                completionInstantIso = postSessionPreview.completionInstantIso,
                completedSetInputHash = postSessionPreview.inputHash,
                additionalDiscomfortNote = additionalDiscomfortNote.trim().takeIf { it.isNotBlank() },
                stillPresentDiscomfortIds = stillPresentIds,
            ),
            shareToStory,
        )
    }

    KpknSheet(
        onDismissRequest = onDismiss,
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
                                    0.85f to Color.Black,
                                    1.0f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .padding(bottom = 90.dp),
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
                                    val start = postSessionPreview.perMuscle[muscleId]?.recoveryScore?.coerceIn(0, 100) ?: 100
                                    val current = muscleFinal[muscleId]?.coerceIn(0, 100) ?: start
                                    MinimalMuscleSlider(
                                        modifier = Modifier.weight(1f),
                                        muscleLabel = muscleId,
                                        value = current,
                                        onValueChange = { updated ->
                                            muscleFinal[muscleId] = updated
                                            musclesEdited = muscleFinal[muscleId] != muscleSeed[muscleId]
                                                || muscleFinal.any { (key, value) -> value != muscleSeed[key] }
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
                                val setLines = remember(completedSets, session) {
                                    val names = session.allExercises().associateBy { it.id }
                                    completedSets.entries
                                        .filter { !it.value.isWarmup }
                                        .sortedBy { it.key }
                                        .map { (key, set) ->
                                            val parsed = parseCompletedSetKey(key)
                                            val name = parsed?.exerciseId?.let { id -> names[id]?.name } ?: "Serie"
                                            val load = if (set.weight % 1.0 == 0.0) {
                                                set.weight.toInt().toString()
                                            } else {
                                                "%.1f".format(set.weight)
                                            }
                                            "$name · $load kg × ${set.reps}"
                                        }
                                }
                                if (setLines.isEmpty() && weightedSetByMuscleSorted.isEmpty()) {
                                    Text(
                                        text = "Sin series registradas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                setLines.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                    )
                                }
                                if (setLines.isNotEmpty() && weightedSetByMuscleSorted.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
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
                        subtitle = "Intensidad global de la sesión",
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
                    ),
                )
                LaunchedEffect(voiceFinalConfirmTriggered) {
                    if (voiceFinalConfirmTriggered) {
                        executeConfirm()
                    }
                }
                Spacer(Modifier.height(100.dp))
            }

            // 7. STICKY ACTION DOCK (DarkMica chrome; confirm FAB stays solid primary CTA)
            val dockHaze = LocalHazeState.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .kpknGlassOrFallback(dockHaze, RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // Independent alignments keep the 64dp check geometrically
                // centered even when the compact right button is wider than
                // the 48dp share control.
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Left: 48dp circular share button
                    Surface(
                        onClick = onShare,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp),
                        shape = CircleShape,
                        color = Color(0xFFE1306C),
                        contentColor = Color.White,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir en Instagram Stories",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // Center: 64dp circular confirm button
                    FloatingActionButton(
                        onClick = { if (!isFinishingWorkout) executeConfirm() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp),
                        shape = CircleShape,
                        containerColor = if (isFinishingWorkout) Color(0xFF444444) else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        if (isFinishingWorkout) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Guardar y terminar entrenamiento",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    // Right: Compact ← Volver button
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver al entrenamiento",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Volver",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
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

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val bloomFarPx = 8.dp.toPx()
        val radius = (size.minDimension - bloomFarPx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val progress = animatedValue.coerceIn(0f, 1f)
        drawAugeRingTrack(center, radius, color)
        drawAugeRingBlooms(center, radius, color, progress, BlendMode.Plus)
        drawAugeRingCore(center, radius, color, progress)
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
