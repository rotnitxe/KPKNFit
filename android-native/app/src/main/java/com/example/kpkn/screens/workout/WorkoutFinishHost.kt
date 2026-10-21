package com.example.kpkn.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.example.kpkn.data.models.RingStartSnapshot
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.SessionSavedNote
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.SessionDiscomfortSummary
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.screens.home.RingColors
import com.example.kpkn.screens.home.augeRingsHostHeightDp
import com.example.kpkn.screens.workout.components.AdjustableRingCompact
import com.example.kpkn.screens.workout.components.MinimalMuscleSlider
import com.example.kpkn.screens.workout.components.rememberFinishRingEdits
import dev.chrisbanes.haze.HazeState
import java.time.Instant

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
    /** La nota que la persona llevaba escrita al cerrar el resumen sin guardar (LV4, 15): pasa a la sesión y vuelve al reabrirlo. */
    onNotesDraft: (String) -> Unit = {},
    presentation: FinishPresentationContext = FinishPresentationContext(),
    onStoryPrepared: (WorkoutStoryData, WorkoutStoryOptions) -> Unit = { _, _ -> },
    sessionMilestones: List<SessionMilestone> = emptyList(),
    sessionSavedNotes: List<SessionSavedNote> = emptyList(),
    sessionChecklist: List<SessionChecklistItem> = emptyList(),
    onSaveSessionNote: (String) -> Unit = {},
    onAddChecklistItem: (String) -> Unit = {},
    onToggleChecklistItem: (String) -> Unit = {},
    onRemoveChecklistItem: (String) -> Unit = {},
    onOpenSessionAlbum: (() -> Unit)? = null,
) {
    val neuralSeed = postSessionPreview.neural.coerceIn(0, 100)
    val spinalSeed = postSessionPreview.spinal.coerceIn(0, 100)
    val muscleSeed = remember(postSessionPreview.perMuscle, postSessionPreview.involvedVolumeMuscles) {
        val involved = postSessionPreview.involvedVolumeMuscles
        postSessionPreview.perMuscle
            .filterKeys { involved.isEmpty() || it in involved }
            .mapValues { it.value.recoveryScore }
    }
    // Las ediciones sobreviven a la recreación de la Activity (plegar/girar); cada grupo se reinicia solo con su semilla.
    val ringEdits = rememberFinishRingEdits(neuralSeed = neuralSeed, spinalSeed = spinalSeed, muscleSeed = muscleSeed)
    var neuralFinal by ringEdits.neuralFinal
    var spinalFinal by ringEdits.spinalFinal
    var neuralEdited by ringEdits.neuralEdited
    var spinalEdited by ringEdits.spinalEdited
    var musclesEdited by ringEdits.musclesEdited
    val muscleFinal = ringEdits.muscleFinal

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
    var adjustChannel by remember { mutableStateOf<RingChannel?>(null) }
    var showShare by remember { mutableStateOf(false) }
    val finishPagesState = rememberSaveableStateHolder()
    var additionalDiscomfortNote by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable(initialSessionNotes) { mutableStateOf(initialSessionNotes) }
    // Cerrar el resumen sin guardar (Atrás, tocar fuera o «Volver») no tira lo escrito: la nota pasa a la sesión (LV4, 15).
    val dismissKeepingNotes: () -> Unit = {
        if (notes != initialSessionNotes) onNotesDraft(notes)
        onDismiss()
    }
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
    var storyOptions by rememberSaveable(stateSaver = storyOptionsSaver) { mutableStateOf(WorkoutStoryOptions()) }

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
    BackHandler(enabled = true) {
        when {
            adjustChannel != null -> adjustChannel = null
            showShare -> showShare = false
            showDiscomfortAccordion -> showDiscomfortAccordion = false
            showMuscleSetsBreakdown -> showMuscleSetsBreakdown = false
            else -> dismissKeepingNotes()
        }
    }

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

        if (shareToStory) onStoryPrepared(WorkoutStoryData(session.name, presentation, durationMinutes, totalVolume, totalSets,
            finishAverageRir(completedExercises), sessionIntensityResult.displayLabel,
            TowerRingValues(musculos = derivedMuscularFinal, energia = neuralFinal, columna = spinalFinal),
            buildFinishLiftProgress(completedExercises, presentation.history)), storyOptions)
        if (notes.isNotBlank() && sessionSavedNotes.lastOrNull()?.text != notes) onSaveSessionNote(notes)
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
                // No se pregunta la claridad: la técnica media no es claridad mental.
                clarityRating = null,
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

    // El efecto no depende de la página visible: confirmar por voz funciona también en Notas.
    LaunchedEffect(voiceFinalConfirmTriggered) {
        if (voiceFinalConfirmTriggered && !isFinishingWorkout) executeConfirm()
    }
    val lifts = remember(completedExercises, presentation.history) { buildFinishLiftProgress(completedExercises, presentation.history) }
    val tonnage = remember(presentation.history, session.id) {
        presentation.history.filter { it.sessionId == session.id && (presentation.program == null || it.programId == presentation.program.id) }
            .sortedBy { com.example.kpkn.domain.auge.AugeUtils.logDateMs(it) }.takeLast(24).map { it.totalVolume }
    }
    val values = TowerRingValues(musculos = derivedMuscularFinal, energia = neuralFinal, columna = spinalFinal)
    val before = TowerRingValues(
        musculos = postSessionPreview.preSessionMuscular ?: (postSessionPreview.muscular + postSessionPreview.globalMuscularDrain).coerceIn(0, 100),
        energia = postSessionPreview.preSessionNeural ?: (postSessionPreview.neural + postSessionPreview.globalCnsDrain).coerceIn(0, 100),
        columna = postSessionPreview.preSessionSpinal ?: (postSessionPreview.spinal + postSessionPreview.globalSpinalDrain).coerceIn(0, 100),
    )
    val sessionHistory = remember(presentation.history, session.id, presentation.program?.id) {
        presentation.history.filter { it.sessionId == session.id && (presentation.program == null || it.programId == presentation.program.id) }
    }
    val story = WorkoutStoryData(session.name, presentation, durationMinutes, totalVolume, totalSets,
        finishAverageRir(completedExercises), sessionIntensityResult.displayLabel, values, lifts)
    KpknSheet(onDismissRequest = {
        when { adjustChannel != null -> adjustChannel = null; showShare -> showShare = false; else -> dismissKeepingNotes() }
    }, stableHeightFraction = 0.95f, maxHeightFraction = 0.95f, hazeState = hazeState) {
        if (adjustChannel != null) {
            adjustChannel?.let { channel ->
        com.example.kpkn.ui.components.rings.RingAdjustPanel(
            initialChannel = channel, title = "Ajusta cómo terminas", onDismiss = { adjustChannel = null },
            energy = com.example.kpkn.ui.components.rings.RingAdjustLevel(neuralFinal, neuralSeed, neuralEdited),
            spine = com.example.kpkn.ui.components.rings.RingAdjustLevel(spinalFinal, spinalSeed, spinalEdited),
            muscles = com.example.kpkn.ui.components.rings.RingAdjustMuscles(muscleFinal.toMap(), muscleSeed, muscleSeed.keys, editedMuscleKeys),
            onEnergyChange = { neuralFinal = it; neuralEdited = it != neuralSeed },
            onSpineChange = { spinalFinal = it; spinalEdited = it != spinalSeed },
            onMuscleChange = { id, value -> muscleFinal[id] = value; musclesEdited = true },
            onEnergyReset = { neuralFinal = neuralSeed; neuralEdited = false },
            onSpineReset = { spinalFinal = spinalSeed; spinalEdited = false },
            onMusclesReset = { muscleFinal.clear(); muscleFinal.putAll(muscleSeed); musclesEdited = false },
        )
    }
        } else if (showShare) {
            WorkoutStorySheet(story, hazeState, storyOptions, onOptionsChange = { storyOptions = it },
                shareAfterSave = shareToStory, onShareAfterSaveChange = { shareToStory = it }, onDismiss = { showShare = false }, embedded = true)
        } else finishPagesState.SaveableStateProvider("finish-pages") {
        FinishPagerLayout(
            sessionName = session.name, saving = isFinishingWorkout, saveDescription = emptySessionGuidance,
            onBack = dismissKeepingNotes, onSave = { if (emptySessionGuidance == null && !isFinishingWorkout) executeConfirm() },
            onShare = { showShare = true },
            warnings = {
                if (emptySessionGuidance != null) Text(emptySessionGuidance, color = Color(0xFFF2EEE6),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (pendingSeriesNotice != null) PendingSeriesNoticeCard(pendingSeriesNotice, onContinuePendingSeries)
                if (hasGenericIntensityFallback) Text("Se usó intensidad genérica en ejercicios sin RPE percibido. Registra la intensidad para mejorar las estimaciones de recuperación.",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2EEE6))
            },
            today = {
                FinishTodayPage(values, before,
                    listOf(postSessionPreview.globalMuscularDrain, postSessionPreview.globalCnsDrain, postSessionPreview.globalSpinalDrain),
                    onRing = { adjustChannel = it },
                    metrics = listOf("Duración" to "$durationMinutes min", "Series" to "$totalSets", "Tonelaje" to "${finishNumber(totalVolume, 0)} kg",
                        "RIR medio" to (finishAverageRir(completedExercises)?.let { finishNumber(it) } ?: "—"),
                        "Marcas" to "${presentation.records}", "Gasto ≈" to (presentation.calories?.let { "$it kcal" } ?: "—")),
                    onDetails = { showMuscleSetsBreakdown = true })
            },
            lifts = { FinishLiftsPage(lifts, session.name, tonnage, totalVolume, sessionIntensityResult.displayLabel,
                if (postExerciseFeedbackByExerciseId.isEmpty()) "Sin registro" else "${finishNumber(averageTechnique)}/10") },
            notes = {
                FinishNotesPage(note = notes, onNote = { notes = it; onNotesDraft(it) }, date = presentation.today.toString(),
                    savedNotes = sessionSavedNotes, history = sessionHistory,
                    checklist = sessionChecklist, onAddTask = onAddChecklistItem, onToggleTask = onToggleChecklistItem,
                    onRemoveTask = onRemoveChecklistItem, discomfortCount = selectedDiscomforts.size,
                    onDiscomforts = { showDiscomfortAccordion = true })
            },
            milestones = { FinishMilestonesPage(sessionMilestones, sessionHistory, lifts, onEvolution = { exerciseId ->
                storyOptions = storyOptions.copy(template = WorkoutStoryTemplate.EXERCISE, exerciseId = exerciseId)
                showShare = true
            }) },
            photos = { FinishPhotosPage(presentation.photos, onOpenSessionAlbum, onCreateStory = {
                storyOptions = storyOptions.copy(template = WorkoutStoryTemplate.PHOTOS)
                showShare = true
            }, sessionDate = presentation.today.toString()) },
        )
        }
    }
    if (showMuscleSetsBreakdown) KpknSheet(onDismissRequest = { showMuscleSetsBreakdown = false }, stableHeightFraction = 0.72f, hazeState = hazeState) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
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
                                color = FinishCream,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Duración: ${durationMinutes} min  ·  Tonelaje: ${"%.0f".format(totalVolume)} kg  ·  Series: $totalSets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val cardioLines = remember(session, completedSets) {
                            com.example.kpkn.domain.cardio.CardioSessionSummary.lines(session, completedSets)
                        }
                        cardioLines.forEach { line ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
                                Text(line.exerciseName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text("Prescrito · ${line.prescribedSentence}", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                Text("Hecho · ${line.actualSummary}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                line.splitLabels.forEach { split ->
                                    Text(split, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                }
                                line.augeLine?.let {
                                    Text(it, color = FinishCream, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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

        }
    }
    if (showDiscomfortAccordion) KpknSheet(onDismissRequest = { showDiscomfortAccordion = false }, stableHeightFraction = 0.82f, hazeState = hazeState) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
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
                                        focusedBorderColor = FinishCream,
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
                                        focusedBorderColor = FinishCream,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    ),
                                )
                            }
                        }
                    }
                }

        }
    }


}

/**
 * Aviso ámbar de series pendientes (D2.1). Texto neutral: el resumen se mantiene y quien
 * terminó a propósito puede guardar igual; «Seguir con ellas» vuelve a la primera pendiente.
 */
@Composable
private fun PendingSeriesNoticeCard(
    notice: PendingSeriesNotice,
    onContinue: () -> Unit,
) {
    val amber = Color(0xFFF2EEE6)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = amber.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, amber.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finish_pending_series_notice")
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = amber,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("finish_pending_series_continue"),
                border = BorderStroke(1.dp, amber.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = amber),
            ) {
                Text(
                    text = notice.actionLabel,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

internal fun finishRingDrainCaption(drain: Int): String? {
    val n = drain.coerceAtLeast(0)
    return if (n == 0) null else "−$n"
}

@Composable
private fun FinishDrainRingsBlock(
    muscular: Int,
    neural: Int,
    spinal: Int,
    muscularDrain: Int,
    neuralDrain: Int,
    spinalDrain: Int,
) {
    val values = listOf(muscular, neural, spinal)
    val drains = listOf(muscularDrain, neuralDrain, spinalDrain)
    val labels = listOf("Músculos", "Energía", "Columna")
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
                    .semantics(mergeDescendants = true) {
                        contentDescription = labels.indices.joinToString(", ") { i ->
                            val drainText = finishRingDrainCaption(drains[i])?.let { ", drenaje $it" }.orEmpty()
                            "${labels[i]} ${values[i]}%$drainText"
                        }
                    },
            ) {
                // Antes → después: cada anillo parte de lo que tenía antes del entreno (valor actual más desgaste) y baja al actual.
                KpknTowerRings(
                    values = TowerRingValues(
                        columna = spinal.coerceIn(0, 100),
                        musculos = muscular.coerceIn(0, 100),
                        energia = neural.coerceIn(0, 100),
                    ),
                    from = TowerRingValues(
                        columna = (spinal + spinalDrain).coerceIn(0, 100),
                        musculos = (muscular + muscularDrain).coerceIn(0, 100),
                        energia = (neural + neuralDrain).coerceIn(0, 100),
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            labels.forEachIndexed { i, label ->
                FinishDrainRingLabel(
                    label = label,
                    value = values[i],
                    drain = drains[i],
                    color = RingColors[i],
                )
            }
        }
    }
}

@Composable
private fun FinishDrainRingLabel(
    label: String,
    value: Int,
    drain: Int,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Text(
            text = "${value.coerceIn(0, 100)}%",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
        )
        finishRingDrainCaption(drain)?.let { delta ->
            Text(
                text = delta,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFEF4444).copy(alpha = 0.90f),
                fontWeight = FontWeight.Bold,
            )
        }
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
