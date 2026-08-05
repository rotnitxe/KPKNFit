package com.example.kpkn.screens.sessioneditor

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.example.kpkn.screens.home.SingleRingCanvas
import com.example.kpkn.data.models.Session
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogSearchRedirects
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateTag
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import com.example.kpkn.domain.templates.SplitTemplateDayGroup
import com.example.kpkn.domain.templates.FocusTemplateGroup
import androidx.compose.animation.animateContentSize
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.screens.sessioneditor.components.SetCardDensity
import com.example.kpkn.screens.sessioneditor.components.SetEditorCard
import com.example.kpkn.screens.sessioneditor.components.SessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.components.rememberSessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.components.rememberSessionEditorSpacing
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.screens.wikilab.components.ExerciseFatigueScenarios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.kpkn.screens.sessioneditor.components.InlineSetRow

internal fun suggestWarmupReps(percentage: Double): Int = when {
    percentage >= 90.0 -> 1
    percentage >= 85.0 -> 2
    percentage >= 80.0 -> 3
    percentage >= 75.0 -> 4
    percentage >= 70.0 -> 5
    percentage >= 65.0 -> 6
    percentage >= 60.0 -> 8
    percentage >= 50.0 -> 10
    else -> 12
}


// ===== COMPACT COMPONENTS FOR OPTIMIZED EXERCISE EDITOR =====



internal fun computeSessionRoleWeightedSets(
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val exercises = session.allExercises()
    exercises.forEach { exercise ->
        val dbEntry = (exercise.catalogConfigurationId ?: exercise.exerciseDbId ?: exercise.exerciseId)
            ?.trim()
            ?.lowercase()
            ?.let(exerciseIndex::get)
            ?: return@forEach
        val effectiveSetCount = exercise.sets.count { !it.isIneffective }.coerceAtLeast(1)
        dbEntry.involvedMuscles.forEach { involvement ->
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val roleMultiplier = resolveMuscleVolumeContribution(involvement)
            val weighted = effectiveSetCount * roleMultiplier
            result[canonical] = (result[canonical] ?: 0.0) + weighted
        }
    }
    return result
}

internal fun computePredictedMuscleBatteries(
    session: Session,
    roleWeightedSets: Map<String, Double>,
    predictedMuscularDrain: Int,
): Map<String, Int> {
    if (roleWeightedSets.isEmpty()) return emptyMap()
    val totalRoleWeight = roleWeightedSets.values.sum().takeIf { it > 0.0 } ?: return emptyMap()
    val muscleCount = roleWeightedSets.size.coerceAtLeast(1)
    val totalSets = session.allExercises().sumOf { exercise ->
        exercise.sets.count { !it.isIneffective }
    }.coerceAtLeast(1)
    val avgSessionRest = session.allExercises().mapNotNull { it.restTime }.ifEmpty { listOf(90) }.average()
    val densityFactor = when {
        avgSessionRest <= 45.0 -> 1.16
        avgSessionRest <= 75.0 -> 1.10
        avgSessionRest >= 210.0 -> 0.92
        avgSessionRest >= 150.0 -> 0.96
        else -> 1.0
    }
    val progressionFactor = (1.0 + ((totalSets - 4).coerceAtLeast(0) / 14.0) * 0.22)
        .coerceIn(1.0, 1.30)
    val supersetFactor = if (session.allExercises().any { !it.supersetGroupRefOrLegacyId().isNullOrBlank() }) 1.08 else 1.0
    val expectedDrop = predictedMuscularDrain.coerceIn(0, 100).toDouble()
    val adjustedExpectedDrop = (expectedDrop * densityFactor * progressionFactor * supersetFactor).coerceAtMost(100.0)

    return roleWeightedSets.mapValues { (_, weight) ->
        val share = (weight / totalRoleWeight).coerceIn(0.0, 1.0)
        val relativeShare = share * muscleCount.toDouble()
        val roleFactor = (0.60 + (0.40 * relativeShare)).coerceIn(0.45, 1.55)
        val modeledDrop = (adjustedExpectedDrop * roleFactor).coerceIn(0.0, 100.0)
        (100.0 - modeledDrop).roundToInt().coerceIn(0, 100)
    }
}

internal fun computePredictedMuscleBatteriesFromVolumeMap(
    volumeByMuscle: Map<String, Double>,
    predictedMuscularDrain: Int,
): Map<String, Int> {
    if (volumeByMuscle.isEmpty()) return emptyMap()
    val totalVolume = volumeByMuscle.values.sum().takeIf { it > 0.0 } ?: return emptyMap()
    val expectedDrop = predictedMuscularDrain.coerceIn(0, 100).toDouble()
    return volumeByMuscle.mapValues { (_, sets) ->
        val share = (sets / totalVolume).coerceIn(0.0, 1.0)
        val modeledDrop = (expectedDrop * (0.65 + share * 0.9)).coerceIn(0.0, 100.0)
        (100.0 - modeledDrop).roundToInt().coerceIn(0, 100)
    }
}

internal fun thresholdForScope(
    threshold: SessionEditorVolumeThreshold?,
    scope: SessionAnalyticsScope,
): Triple<Double, Double, Double>? {
    threshold ?: return null
    return when (scope) {
        SessionAnalyticsScope.CURRENT -> Triple(threshold.sessionMev, threshold.sessionMav, threshold.sessionMrv)
        SessionAnalyticsScope.WEEK -> Triple(threshold.weeklyMev, threshold.weeklyMav, threshold.weeklyMrv)
    }
}

@Composable
internal fun PredictedMuscleBatterySection(perMuscle: Map<String, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Batería restante por músculo",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
        )
        val entries = perMuscle.entries.sortedBy { it.value }
        entries.forEach { (muscle, score) ->
            val color = when {
                score >= 80 -> Color(0xFF22C55E)
                score >= 50 -> Color(0xFFFACC15)
                else -> Color(0xFFEF4444)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    muscle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(max = 118.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                )
                Text(
                    "$score%",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}


internal fun resolveSpecificSubMuscle(muscle: String, emphasis: String?): String {
    return MuscleHeadResolution.resolveDisplayHead(muscle, emphasis) ?: muscle
}

internal fun com.example.kpkn.data.models.ExerciseSet.editorEffectiveTargetRpe(): Double {
    if (isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE) return 10.0
    targetRPE?.let { return it.coerceIn(1.0, 10.0) }
    targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
    return 8.0
}

/** Intensity tiers for session volume display (assistant muscle card). */
enum class SessionIntensityTier(val label: String, val ordinalRank: Int) {
    MEDIA("Media", 0),
    ALTA("Alta", 1),
    MUY_ALTA("Muy Alta", 2),
    MAXIMA("Máxima", 3),
}

/**
 * Classify a planned set's intensity for the assistant volume card.
 * - Máxima: failure + dropset/rest-pause
 * - Muy Alta: failure
 * - Alta: RPE 8–9 / RIR 0–2
 * - Media: RPE 6–7 / RIR 3–4 (and default)
 */
internal fun com.example.kpkn.data.models.ExerciseSet.sessionIntensityTier(): SessionIntensityTier {
    val isFail = isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE
    val hasTechnique = isDropSet || isRestPause || dropSets.isNotEmpty() || restPauses.isNotEmpty()
    if (isFail && hasTechnique) return SessionIntensityTier.MAXIMA
    if (isFail) return SessionIntensityTier.MUY_ALTA
    val rpe = editorEffectiveTargetRpe()
    return if (rpe >= 8.0) SessionIntensityTier.ALTA else SessionIntensityTier.MEDIA
}

/** Average intensity tier across sets (ordinal mean → nearest tier). */
internal fun averageSessionIntensityTier(
    sets: List<com.example.kpkn.data.models.ExerciseSet>,
): SessionIntensityTier {
    val active = sets.filterNot { it.isIneffective }
    if (active.isEmpty()) return SessionIntensityTier.MEDIA
    val avg = active.map { it.sessionIntensityTier().ordinalRank }.average()
    return SessionIntensityTier.entries.minByOrNull { kotlin.math.abs(it.ordinalRank - avg) }
        ?: SessionIntensityTier.MEDIA
}

/**
 * Per-muscle role-separated set counts for the assistant volume card.
 * Direct = PRIMARY contribution × sets. SecondaryIndirect = SECONDARY contribution × sets.
 * StabilizerIndirect = STABILIZER contribution × sets.
 * Intensity is averaged only over exercises that contribute PRIMARY volume to that muscle.
 */
internal data class MuscleVolumeRow(
    val muscle: String,
    val directSets: Double,
    val secondarySets: Double,
    val stabilizerSets: Double,
    val intensity: SessionIntensityTier,
)

internal fun buildMuscleVolumeRows(session: Session): List<MuscleVolumeRow> {
    val volumes = VolumeCalculator.calculateRoleSeparatedMuscleVolume(
        sessions = listOf(session),
        exerciseList = catalogIndexForVolume.values.toList(),
        aliases = catalogSearchRedirects(),
    )
    val exerciseIndex = catalogIndexForVolume
    val intensitySets = mutableMapOf<String, MutableList<com.example.kpkn.data.models.ExerciseSet>>()

    session.allExercises().forEach { exercise ->
        if (VolumeCalculator.countEffectiveSets(exercise.sets) <= 0) return@forEach
        val muscles = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
        muscles.filter { it.role == MuscleRole.PRIMARY }
            .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
            .distinct()
            .forEach { muscle ->
                intensitySets.getOrPut(muscle) { mutableListOf() }
                    .addAll(exercise.sets.filterNot { it.isIneffective })
            }
    }

    return volumes.map { (muscle, volume) ->
        MuscleVolumeRow(
            muscle = muscle,
            directSets = volume.directSets,
            secondarySets = volume.secondarySets,
            stabilizerSets = volume.stabilizerSets,
            intensity = averageSessionIntensityTier(intensitySets[muscle].orEmpty()),
        )
    }.filter { it.directSets > 0.0 || it.secondarySets > 0.0 || it.stabilizerSets > 0.0 }
        .sortedByDescending { it.directSets }
}

private val catalogIndexForVolume: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo> by lazy {
    // Full index (definition + configuration + custom ids) so v2-picked exercises
    // resolve their involved muscles and actually contribute volume.
    catalogExerciseIndex()
}

internal fun buildDisplayContributions(
    involvedMuscles: List<com.example.kpkn.data.models.InvolvedMuscle>,
    countIndirect: Boolean
): Map<String, Double> {
    val grouped = linkedMapOf<String, Double>()
    involvedMuscles.forEach { involvement ->
        val isMatch = if (countIndirect) {
            involvement.role == com.example.kpkn.data.models.MuscleRole.SECONDARY || involvement.role == com.example.kpkn.data.models.MuscleRole.STABILIZER
        } else {
            involvement.role == com.example.kpkn.data.models.MuscleRole.PRIMARY
        }
        if (isMatch) {
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
            val current = grouped[canonical] ?: 0.0
            if (contribution > current) {
                grouped[canonical] = contribution
            }
        }
    }
    return grouped.filterValues { it > 0.0 }
}

internal fun countDisplaySets(exerciseSets: List<com.example.kpkn.data.models.ExerciseSet>, adjustByIntensity: Boolean): Double {
    var total = 0.0
    val activeSets = exerciseSets.filterNot { it.isIneffective }
    val counted = activeSets.filter { set ->
        ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
    }
    val targetList = if (counted.isEmpty()) activeSets else counted
    targetList.forEach { set ->
        val mult = if (adjustByIntensity) {
            com.example.kpkn.domain.auge.AugeClassifiers.getEffectiveVolumeMultiplier(set.editorEffectiveTargetRpe())
        } else {
            1.0
        }
        total += mult
    }
    return total
}

internal fun calculateSubMuscleBreakdown(
    canonicalMuscle: String,
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    countIndirect: Boolean,
    adjustByIntensity: Boolean
): List<Pair<String, List<Pair<String, Double>>>> {
    val targetSubMuscles = when (canonicalMuscle) {
        "Deltoides" -> listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
        "Glúteos" -> listOf("Glúteo Mayor", "Glúteo Medio", "Glúteo Menor")
        "Pectorales" -> listOf("Pectoral Superior", "Pectoral Medio", "Pectoral Inferior")
        else -> return emptyList()
    }
    
    val subMuscleVolumes = targetSubMuscles.associateWith { mutableMapOf<String, Double>() }.toMutableMap()
    
    session.allExercises().forEach { exercise ->
        val effectiveSets = countDisplaySets(exercise.sets, adjustByIntensity)
        if (effectiveSets <= 0.0) return@forEach
        val musclesToCount = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)

        musclesToCount.forEach { involvement ->
            // El breakdown de porciones desglosa TODO el trabajo de ese músculo
            // (directo + secundario + estabilizador), no filtra por rol.
            if (involvement.role != com.example.kpkn.data.models.MuscleRole.NEUTRALIZER) {
                val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
                if (canonical == canonicalMuscle) {
                    val subMuscle = resolveSpecificSubMuscle(involvement.muscle, involvement.emphasis)
                    val map = subMuscleVolumes[subMuscle]
                    if (map != null) {
                        val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
                        val current = map[exercise.name] ?: 0.0
                        if (effectiveSets * contribution > current) {
                            map[exercise.name] = effectiveSets * contribution
                        }
                    }
                }
            }
        }
    }
    
    return targetSubMuscles.map { subName ->
        val exerciseMap = subMuscleVolumes[subName] ?: emptyMap()
        subName to exerciseMap.entries
            .map { it.key to it.value }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
    }
}

internal fun getMuscleEmphasisEducationalText(muscle: String, headName: String?): String {
    val normalizedMuscle = muscle.trim().lowercase()
    val normalizedHead = headName?.trim()?.lowercase() ?: ""

    return when (normalizedMuscle) {
        "pectorales" -> {
            when {
                normalizedHead.contains("clavicular") || normalizedHead.contains("superior") ->
                    "La porción superior (clavicular) se enfatiza mediante la flexión del hombro (ej. press inclinado o cruces en polea baja), donde la trayectoria ascendente alinea la línea de tracción con la dirección de sus fibras musculares."
                normalizedHead.contains("esternal") || normalizedHead.contains("inferior") ->
                    "La porción inferior (esternal/costal) se enfatiza mediante la aducción horizontal declinada (ej. fondos en paralelas o cruces en polea alta), alineando el plano de empuje con las fibras inferiores."
                normalizedHead.contains("plano") || normalizedHead.contains("medio") ->
                    "La porción media se enfatiza con la aducción horizontal pura perpendicular al torso (ej. press de banca plano o aperturas planas)."
                else ->
                    "El pectoral se divide en porciones clavicular, esternal y costal. Modificar la inclinación del press o la trayectoria de los cruces de polea cambia la alineación de las fibras activadas por los brazos de momento de la carga."
            }
        }
        "deltoides" -> {
            when {
                normalizedHead.contains("anterior") ->
                    "El deltoides anterior se enfatiza mediante la flexión del hombro (ej. press militar, press de hombros con mancuernas o elevaciones frontales)."
                normalizedHead.contains("lateral") || normalizedHead.contains("medio") ->
                    "El deltoides lateral se enfatiza mediante la abducción pura del hombro (ej. elevaciones laterales), idealmente realizadas en el plano escapular (30° al frente) para mayor seguridad articular."
                normalizedHead.contains("posterior") ->
                    "El deltoides posterior se enfatiza mediante la abducción horizontal y extensión del hombro (ej. pájaros con mancuernas o cruces invertidos en polea)."
                else ->
                    "El deltoides consta de cabezas anterior, lateral y posterior. Se enfatizan modificando la dirección del plano del movimiento del hombro (flexión, abducción o abducción horizontal)."
            }
        }
        "trapecio" -> {
            when {
                normalizedHead.contains("descendente") || normalizedHead.contains("superior") ->
                    "El trapecio superior se enfatiza mediante la elevación escapular (ej. encogimientos de hombros), donde las fibras tiran hacia arriba, y también contribuye en la abducción del brazo por encima de la cabeza."
                normalizedHead.contains("transversa") || normalizedHead.contains("media") ->
                    "El trapecio medio se enfatiza mediante la retracción escapular (ej. jalones a la cara/face pulls, o remos abiertos juntando las escápulas)."
                normalizedHead.contains("ascendente") || normalizedHead.contains("inferior") ->
                    "El trapecio inferior se enfatiza mediante la depresión escapular (ej. jalones escapulares o elevaciones en Y), donde las fibras tiran de la escápula hacia abajo."
                else ->
                    "El trapecio se divide en superior, medio e inferior. Sus fibras cambian de orientación funcional, requiriendo elevación, retracción o depresión de las escápulas para enfatizar cada zona."
            }
        }
        "cuádriceps" -> {
            when {
                normalizedHead.contains("recto femoral") ->
                    "El recto femoral es biarticular (cruza cadera y rodilla). Se enfatiza cuando la cadera está extendida y la rodilla se flexiona (ej. sentadilla sissy o extensiones con el torso inclinado hacia atrás), aumentando su tensión de estiramiento."
                else ->
                    "Los vastos (lateral, medial, intermedio) son monoarticulares y se activan en conjunto en flexo-extensión de rodilla (ej. prensa o sentadillas). El recto femoral requiere cambios en la extensión de la cadera para modificar su participación relativa."
            }
        }
        "glúteos" -> {
            when {
                normalizedHead.contains("mayor") ->
                    "El glúteo mayor es el extensor primario de cadera. Se enfatiza con cargas donde la máxima tensión coincide con la cadera extendida (ej. hip thrust) o estirada (ej. peso muerto rumano o sentadilla profunda)."
                normalizedHead.contains("medio") || normalizedHead.contains("menor") ->
                    "Los glúteos medio y menor actúan como abductores y rotadores. Se enfatizan mediante la abducción pura de la cadera (ej. abducciones en polea, máquina de abductores o caminatas laterales con banda)."
                else ->
                    "El complejo glúteo incluye el mayor (extensor principal) y el medio/menor (estabilizadores y abductores). Alternar ejercicios de extensión pura con movimientos de abducción cambia el énfasis entre estas porciones."
            }
        }
        "pantorrillas" -> {
            when {
                normalizedHead.contains("gastrocnemio") ->
                    "El gastrocnemio es biarticular. Se enfatiza con la rodilla extendida (ej. elevaciones de talones de pie o en prensa), donde puede estirarse y contraerse en condiciones óptimas."
                normalizedHead.contains("sóleo") ->
                    "El sóleo es monoarticular. Se enfatiza con la rodilla flexionada a 90° (ej. elevaciones de talones sentado), posición que acorta e inactiva en gran parte al gastrocnemio."
                else ->
                    "La pantorrilla se compone de gastrocnemios y sóleo. Flexionar la rodilla altera drásticamente la contribución de los gastrocnemios debido a la insuficiencia activa, dejando la mayor parte del trabajo al sóleo."
            }
        }
        "bíceps" -> {
            when {
                normalizedHead.contains("larga") ->
                    "La cabeza larga (biarticular) se enfatiza al colocar el hombro en extensión (ej. curl en banco inclinado), lo que la sitúa en una posición de mayor preestiramiento."
                normalizedHead.contains("corta") ->
                    "La cabeza corta se enfatiza cuando el hombro está flexionado (ej. curl predicador o curl araña), lo que reduce el preestiramiento de la cabeza larga e incrementa el estímulo relativo en la porción interna."
                normalizedHead.contains("braquial") ->
                    "El braquial y braquiorradial se enfatizan usando agarres neutros o pronos (ej. curl martillo o curl invertido), donde disminuye la ventaja mecánica de las cabezas del bíceps."
                else ->
                    "El flexor del codo incluye la cabeza larga, corta y el músculo braquial. Modificar la posición del hombro respecto al torso o cambiar la orientación del agarre altera la ventaja mecánica de cada porción."
            }
        }
        "tríceps" -> {
            when {
                normalizedHead.contains("larga") ->
                    "La cabeza larga es la única biarticular del tríceps. Se enfatiza mediante la flexión del hombro (brazo elevado sobre la cabeza, ej. copas de tríceps o extensiones tras nuca), colocándola en una posición de estiramiento máximo."
                normalizedHead.contains("lateral") ->
                    "La cabeza lateral se enfatiza con el brazo al costado del cuerpo y agarre prono o neutro (ej. extensiones en polea alta con cuerda o barra V)."
                normalizedHead.contains("medial") ->
                    "La cabeza medial es el caballo de batalla del tríceps, activa en todos los movimientos de extensión, siendo especialmente demandada al final del rango en el bloqueo del codo."
                else ->
                    "El tríceps tiene cabezas lateral, medial y larga. Dado que solo la cabeza larga cruza la articulación del hombro, elevar el brazo por encima de la cabeza es indispensable para estirarla y enfatizarla."
            }
        }
        "antebrazo" -> {
            when {
                normalizedHead.contains("flexores") ->
                    "Los flexores se enfatizan mediante movimientos de flexión de muñeca (palma hacia el antebrazo) bajo resistencia."
                normalizedHead.contains("extensores") ->
                    "Los extensores se enfatizan mediante la extensión de muñeca (dorso de la mano hacia el antebrazo)."
                else ->
                    "El antebrazo se divide principalmente en extensores y flexores. Cambiar la orientación del agarre de la barra (prono o supino) redirige el estímulo y la tensión mecánica a cada grupo."
            }
        }
        else -> ""
    }
}

@Composable
internal fun SessionSubMuscleBreakdownList(
    muscleName: String,
    session: Session,
    countIndirect: Boolean,
    adjustByIntensity: Boolean,
) {
    val exerciseIndex = remember { catalogExerciseIndex() }
    val breakdown = remember(muscleName, session, countIndirect, adjustByIntensity) {
        calculateSubMuscleBreakdown(muscleName, session, exerciseIndex, countIndirect, adjustByIntensity)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        breakdown.forEach { (subName, exercises) ->
            val totalSubSets = exercises.sumOf { it.second }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = subName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${if (totalSubSets == totalSubSets.toLong().toDouble()) totalSubSets.toLong().toString() else "%.1f".format(totalSubSets)} sets",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (exercises.isEmpty()) {
                    Text(
                        text = "  Sin aportes registrados para esta cabeza.",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else {
                    exercises.forEach { (exName, valSets) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• $exName",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${if (valSets == valSets.toLong().toDouble()) valSets.toLong().toString() else "%.1f".format(valSets)} sets",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
