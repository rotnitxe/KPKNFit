package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.PostSessionPreview
import com.example.kpkn.data.models.RingStartSnapshot
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.SessionDiscomfortSummary
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.cardio.CardioSessionSummary
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.design.*
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import dev.chrisbanes.haze.HazeState
import java.time.Instant
import kotlinx.coroutines.delay


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
    pendingSeriesNotice: PendingSeriesNotice? = null,
    onContinuePendingSeries: () -> Unit = {},
    onConfirm: (String, Int, SessionClosingFeedback, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSummaryReady: (WorkoutSessionSummary) -> Unit,
    /** Récords de la sesión (hitos de e1RM nuevo). Sin récords, la cuarta cifra es «Ejercicios». */
    sessionRecordCount: Int = 0,
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
    var additionalDiscomfortNote by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable(initialSessionNotes) { mutableStateOf(initialSessionNotes) }
    var selectedDiscomforts by rememberSaveable {
        mutableStateOf(
            postExerciseFeedbackByExerciseId
                .values
                .flatMap { it.discomfortIds }
                .filter { it != "none" }
                .distinct(),
        )
    }
    var showDiscomfortAccordion by remember { mutableStateOf(false) }
    var discomfortSearchQuery by remember { mutableStateOf("") }
    var discomfortStillPresent by remember {
        mutableStateOf(selectedDiscomforts.associateWith { true })
    }
    var shareToStory by rememberSaveable { mutableStateOf(false) }

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

    val totalSets = logicalSetCountFromCompleted(completedExercises)
    val totalVolume = sessionTonnage(completedExercises)
    // Same predicate as WorkoutFinishController's P0 guard: with no recorded
    // sets the save is blocked, and the sheet must say so instead of failing
    // silently behind a transient toast.
    val emptySessionGuidance = finishEmptySessionGuidance(completedExercises)
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
                ringStartSnapshot = postSessionPreview.preSessionMuscular?.let { muscular ->
                    RingStartSnapshot(
                        capturedAtIso = postSessionPreview.completionInstantIso ?: Instant.now().toString(),
                        muscular = muscular,
                        energy = postSessionPreview.preSessionNeural ?: postSessionPreview.neural,
                        structure = postSessionPreview.preSessionSpinal ?: postSessionPreview.spinal,
                    )
                },
            ),
            shareToStory,
        )
    }

    var isAdjustExpanded by rememberSaveable { mutableStateOf(false) }
    val exerciseCount = remember(completedExercises) {
        completedExercises.count { ex -> ex.sets.any { !it.skipped && !it.isWarmup } }
    }
    // Sin datos del motor (preview sin músculos) no hay torre ni ajustes de recuperación.
    val hasEngineData = postSessionPreview.perMuscle.isNotEmpty()
    val noSeries = totalSets == 0
    val drainLine = finishDrainLine(
        muscularDrain = postSessionPreview.globalMuscularDrain,
        neuralDrain = postSessionPreview.globalCnsDrain,
        spinalDrain = postSessionPreview.globalSpinalDrain,
    )
    val tomorrowLine = finishTomorrowLine(postSessionPreview.tomorrowNeural, postSessionPreview.tomorrowSpinal)
    // Antes → después: cada anillo parte de lo que tenía antes del entreno (valor actual más desgaste) y baja al actual.
    val towerValues = TowerRingValues(
        columna = spinalFinal.coerceIn(0, 100),
        musculos = derivedMuscularFinal.coerceIn(0, 100),
        energia = neuralFinal.coerceIn(0, 100),
    )
    val towerFrom = TowerRingValues(
        columna = (spinalFinal + postSessionPreview.globalSpinalDrain).coerceIn(0, 100),
        musculos = (derivedMuscularFinal + postSessionPreview.globalMuscularDrain).coerceIn(0, 100),
        energia = (neuralFinal + postSessionPreview.globalCnsDrain).coerceIn(0, 100),
    )
    val cardioLines = remember(session, completedSets) {
        CardioSessionSummary.lines(session, completedSets)
    }
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
                    finishOneDecimal(set.weight)
                }
                "$name · $load kg × ${set.reps}"
            }
    }

    KpknSheet(
        onDismissRequest = onDismiss,
        stableHeightFraction = 0.86f,
        hazeState = hazeState,
    ) {
        LaunchedEffect(voiceFinalConfirmTriggered) {
            if (voiceFinalConfirmTriggered) {
                executeConfirm()
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinishSummaryHeader(
                    sessionName = session.name,
                    modifier = Modifier.padding(top = 8.dp),
                )
                pendingSeriesNotice?.let { notice ->
                    FinishPendingSeriesModule(
                        message = notice.message,
                        actionLabel = notice.actionLabel,
                        onContinue = onContinuePendingSeries,
                        modifier = Modifier.kpknEntrance("finish-summary", 0),
                    )
                }
                if (noSeries) {
                    FinishEmptySeriesState(guidance = emptySessionGuidance)
                } else {
                    FinishFiguresModule(
                        figures = finishFigures(
                            durationMinutes = durationMinutes,
                            totalSets = totalSets,
                            totalVolumeKg = totalVolume,
                            recordCount = sessionRecordCount,
                            exerciseCount = exerciseCount,
                        ),
                        modifier = Modifier.kpknEntrance("finish-summary", 1),
                    )
                    if (hasEngineData) {
                        FinishRecoveryModule(
                            values = towerValues,
                            from = towerFrom,
                            drainLine = drainLine,
                            tomorrowLine = tomorrowLine,
                            modifier = Modifier.kpknEntrance("finish-summary", 2),
                        )
                    }
                    if (hasGenericIntensityFallback) {
                        FinishWarningModule(
                            message = "Se usó intensidad genérica en ejercicios sin RPE percibido. " +
                                "Registra la intensidad para mejorar las estimaciones de recuperación.",
                        )
                    }
                    if (hasEngineData) {
                        KpknModule(modifier = Modifier.kpknEntrance("finish-summary", 3)) {
                            KpknModuleHeader(
                                label = "Ajustar la recuperación",
                                action = {
                                    KpknTextAction(
                                        text = if (isAdjustExpanded) "Ocultar" else "Ajustar",
                                        onClick = { isAdjustExpanded = !isAdjustExpanded },
                                    )
                                },
                            )
                            AnimatedVisibility(
                                visible = isAdjustExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                ) {
                                    Text(
                                        text = "Arrastra sobre un anillo para cambiar su valor final. " +
                                            "Desliza sobre una barra para ajustar la frescura de ese músculo.",
                                        style = KpknType.caption,
                                        color = WizardColors.textMuted,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        FinishRingAdjuster(
                                            title = "Energía",
                                            value = neuralFinal,
                                            color = KpknRingColors.energia,
                                            onValueChange = {
                                                neuralFinal = it
                                                neuralEdited = true
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                        FinishRingAdjuster(
                                            title = "Columna",
                                            value = spinalFinal,
                                            color = KpknRingColors.columna,
                                            onValueChange = {
                                                spinalFinal = it
                                                spinalEdited = true
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (muscleFinal.isNotEmpty()) {
                                        Text(
                                            text = "Frescura por músculo",
                                            style = KpknType.bodyStrong,
                                            color = WizardColors.text,
                                        )
                                        muscleFinal.keys
                                            .sortedByDescending { sessionMuscleVolumeByRoleSets[it] ?: 0.0 }
                                            .forEach { muscleId ->
                                                val start = postSessionPreview.perMuscle[muscleId]?.recoveryScore
                                                    ?.coerceIn(0, 100) ?: 100
                                                val current = muscleFinal[muscleId]?.coerceIn(0, 100) ?: start
                                                FinishMuscleBar(
                                                    label = muscleId,
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
                    }
                    KpknModule(modifier = Modifier.kpknEntrance("finish-summary", 4)) {
                        KpknModuleHeader(
                            label = "Detalle de la carga",
                            action = {
                                KpknTextAction(
                                    text = if (showMuscleSetsBreakdown) "Ocultar" else "Ver",
                                    onClick = { showMuscleSetsBreakdown = !showMuscleSetsBreakdown },
                                )
                            },
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            FinishLabeledMetric(
                                label = "Intensidad",
                                value = sessionIntensityResult.displayLabel,
                                modifier = Modifier.weight(1f),
                            )
                            FinishLabeledMetric(
                                label = "Técnica",
                                value = finishOneDecimal(averageTechnique),
                                unit = "/10",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        cardioLines.forEach { line ->
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Text(
                                    text = line.exerciseName,
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                Text(
                                    text = "Prescrito · ${line.prescribedSentence}",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                                Text(
                                    text = "Hecho · ${line.actualSummary}",
                                    style = KpknType.caption,
                                    color = WizardColors.text,
                                )
                                line.splitLabels.forEach { split ->
                                    Text(text = split, style = KpknType.caption, color = WizardColors.textMuted)
                                }
                                line.augeLine?.let { augeLine ->
                                    Text(text = augeLine, style = KpknType.caption, color = SymbolPalette.energia)
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = showMuscleSetsBreakdown,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            ) {
                                setLines.forEach { line ->
                                    Text(text = line, style = KpknType.caption, color = WizardColors.text)
                                }
                                weightedSetByMuscleSorted.forEach { (muscle, weightedSets) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(text = muscle, style = KpknType.caption, color = WizardColors.textMuted)
                                        Text(
                                            text = "${finishOneDecimal(weightedSets)} series",
                                            style = KpknType.caption,
                                            color = WizardColors.text,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                KpknModule(modifier = Modifier.kpknEntrance("finish-summary", 5)) {
                    KpknModuleHeader(
                        label = "Molestias reportadas (${selectedDiscomforts.size})",
                        action = {
                            KpknTextAction(
                                text = if (showDiscomfortAccordion) "Ocultar" else "Ver",
                                onClick = { showDiscomfortAccordion = !showDiscomfortAccordion },
                            )
                        },
                    )
                    AnimatedVisibility(
                        visible = showDiscomfortAccordion,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            Text(
                                text = "Durante la sesión reportaste estas molestias. ¿Sigues sintiéndolas?",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                            // Top de la sesión: filled = sigue presente; sin relleno = ya no la siente.
                            if (sessionDiscomfortSummary.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    sessionDiscomfortSummary.forEach { summary ->
                                        val selected = selectedDiscomforts.contains(summary.discomfortId)
                                        val stillPresent = discomfortStillPresent[summary.discomfortId] ?: true
                                        KpknChip(
                                            text = summary.label,
                                            selected = stillPresent,
                                            leadingIcon = if (stillPresent) KpknLineIcons.Check else KpknLineIcons.Close,
                                            onClick = {
                                                if (!selected) {
                                                    selectedDiscomforts = selectedDiscomforts + summary.discomfortId
                                                }
                                                discomfortStillPresent = discomfortStillPresent.toMutableMap().apply {
                                                    put(summary.discomfortId, !stillPresent)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            KpknTextField(
                                value = discomfortSearchQuery,
                                onValueChange = { discomfortSearchQuery = it },
                                placeholder = "Buscar molestia",
                                singleLine = true,
                                leadingIcon = KpknLineIcons.Search,
                            )
                            // Con búsqueda, el catálogo filtrado; sin búsqueda y sin top, el catálogo entero.
                            val catalogEntries = when {
                                discomfortSearchQuery.isNotBlank() -> DISCOMFORT_CATALOG_BY_ID.values
                                    .filter { it.id != "none" }
                                    .filter { it.label.contains(discomfortSearchQuery, ignoreCase = true) }
                                sessionDiscomfortSummary.isEmpty() -> DISCOMFORT_CATALOG_BY_ID.values
                                    .filter { it.id != "none" }
                                    .sortedBy { it.label }
                                else -> emptyList()
                            }
                            if (catalogEntries.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    catalogEntries.forEach { entry ->
                                        val selected = selectedDiscomforts.contains(entry.id)
                                        KpknChip(
                                            text = entry.label,
                                            selected = selected,
                                            onClick = {
                                                selectedDiscomforts = if (selected) {
                                                    selectedDiscomforts - entry.id
                                                } else {
                                                    selectedDiscomforts + entry.id
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            KpknTextField(
                                value = additionalDiscomfortNote,
                                onValueChange = { additionalDiscomfortNote = it },
                                label = "Otra molestia (por ejemplo, rodilla izquierda)",
                                singleLine = true,
                            )
                        }
                    }
                }
                KpknModule(modifier = Modifier.kpknEntrance("finish-summary", 6)) {
                    KpknModuleHeader(label = "Notas de la sesión")
                    KpknTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = "Opcional: lo que quieras recordar de hoy",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            FinishFooter(
                primaryLabel = if (isFinishingWorkout) "Guardando…" else "Guardar entrenamiento",
                primaryEnabled = !isFinishingWorkout,
                onPrimary = { if (!isFinishingWorkout) executeConfirm() },
                primaryStateDescription = emptySessionGuidance,
                onShare = onShare,
                onBack = onDismiss,
            )
        }
    }
}

/** Tipo de hito que el VM escribe al batir una marca de e1RM: es lo que cuenta como récord en el resumen. */
internal const val FINISH_RECORD_MILESTONE_KIND = "pr_e1rm"

/** Récords de la sesión para el resumen. Las metas estrella no son récords. */
internal fun finishSessionRecordCount(milestones: List<SessionMilestone>): Int =
    milestones.count { it.kind == FINISH_RECORD_MILESTONE_KIND }

/** Espera antes de decir «Calculando la recuperación…» en la hoja de carga (LIVE_SESSION_V1 §5). */
private const val FINISH_CALCULATING_CAPTION_DELAY_MS = 600L

/**
 * Hoja mientras se calcula la recuperación: las cifras y la torre en esqueleto y, si tarda más de 600 ms, «Calculando la
 * recuperación…». El guardado espera al cálculo, así que su botón va apagado.
 */
@Composable
internal fun FinishCalculatingSheet(sessionName: String, hazeState: HazeState, onBack: () -> Unit) {
    var captionVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(FINISH_CALCULATING_CAPTION_DELAY_MS)
        captionVisible = true
    }
    KpknSheet(
        onDismissRequest = onBack,
        stableHeightFraction = 0.86f,
        hazeState = hazeState,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinishSummaryHeader(sessionName = sessionName, modifier = Modifier.padding(top = 8.dp))
                FinishFiguresModule(figures = null)
                FinishRecoveryModule(
                    values = null,
                    from = null,
                    drainLine = null,
                    tomorrowLine = null,
                    loading = true,
                    loadingCaptionVisible = captionVisible,
                )
            }
            FinishFooter(
                primaryLabel = "Guardar entrenamiento",
                primaryEnabled = false,
                onPrimary = {},
                onBack = onBack,
            )
        }
    }
}

/**
 * Hoja del error del cálculo: el motivo en lenguaje llano, sin el mensaje técnico, y «Cerrar». Sin reintento: volver a abrir el
 * resumen lo repite.
 */
@Composable
internal fun FinishCalculationErrorSheet(sessionName: String, reason: String, hazeState: HazeState, onClose: () -> Unit) {
    KpknSheet(
        onDismissRequest = onClose,
        hazeState = hazeState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinishSummaryHeader(sessionName = sessionName, modifier = Modifier.padding(top = 8.dp))
                FinishCalculationErrorModule(reason = reason)
            }
            FinishFooter(
                primaryLabel = "Cerrar",
                primaryEnabled = true,
                onPrimary = onClose,
            )
        }
    }
}

internal fun finishRingDrainCaption(drain: Int): String? {
    val n = drain.coerceAtLeast(0)
    return if (n == 0) null else "−$n"
}
