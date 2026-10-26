package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.workout.restPauseDescription
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.WorkoutStepRules
import com.example.kpkn.screens.workout.cockpitSessionMetrics
import com.example.kpkn.screens.workout.displayWorkoutExerciseName
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.util.Locale

/** La sesión del cockpit B1: datos reales de lo ya registrado y una ruta navegable, dentro del alto del anfitrión. */
@Composable
internal fun CockpitSessionSummary(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    completedExercises: List<CompletedExercise>,
    omittedSetKeys: Set<String>,
    currentExerciseId: String?,
    supersetGroups: List<SupersetGroup>,
    liveEnergySummary: SessionEnergySummary?,
    bodyWeight: Double?,
    startTimeMs: Long,
    targetMinutes: Int?,
    onSelectExercise: (String) -> Unit,
    onSelectSuperset: (String) -> Unit,
    onOpenExerciseOptions: ((String) -> Unit)? = null,
    onOpenSupersetOptions: ((String) -> Unit)? = onOpenExerciseOptions,
    calibratedLoads: Map<String, Double> = emptyMap(),
    onAddExercise: (() -> Unit)? = null,
) {
    val metrics = remember(exercises, completedSets, completedExercises, omittedSetKeys, liveEnergySummary, bodyWeight) {
        cockpitSessionMetrics(exercises, completedSets, completedExercises, omittedSetKeys, liveEnergySummary, bodyWeight)
    }
    val number = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).apply { maximumFractionDigits = 1 } }
    val bodyScroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().fadeBottomWhenScrollable(bodyScroll).verticalScroll(bodyScroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CockpitElapsedClock(startTimeMs, targetMinutes, Modifier.size(96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CockpitMetric("Registros", "${metrics.completed} / ${metrics.total}", Modifier.weight(1f))
                    CockpitMetric("Tonelaje", "${number.format(metrics.tonnage)} kg", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CockpitMetric("RIR medio", metrics.averageRir?.let(number::format) ?: "—", Modifier.weight(1f))
                    CockpitMetric("Gasto", metrics.calories.kcal?.let { "≈ $it kcal" } ?: "—", Modifier.weight(1f))
                }
            }
        }
        val paceWork = remember(exercises, completedSets, omittedSetKeys) {
            com.example.kpkn.screens.workout.sessionPaceWork(exercises, completedSets, omittedSetKeys)
        }
        SessionPaceEstimate(startTimeMs, paceWork.completed, paceWork.total, targetMinutes)
        metrics.calories.hint?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
        Text("RUTA", style = KpknType.eyebrow, color = WizardColors.textMuted)
        val groupByMember = remember(exercises, supersetGroups) {
            buildMap {
                supersetGroups.forEach { group -> group.exerciseOrder.forEach { put(it, group.id) } }
                exercises.forEach { ex -> ex.supersetGroupRefOrLegacyId()?.let { put(ex.id, it) } }
            }
        }
        val groups = remember(exercises, groupByMember) {
            val emitted = mutableSetOf<String>()
            exercises.mapNotNull { exercise ->
                val id = groupByMember[exercise.id]
                when {
                    id == null -> null to listOf(exercise)
                    emitted.add(id) -> id to exercises.filter { groupByMember[it.id] == id }
                    else -> null
                }
            }
        }
        groups.forEach { (groupId, members) ->
            val done = WorkoutStepRules.completedRoadmapSlotsForExercises(members, completedSets, omittedSetKeys)
            val total = WorkoutStepRules.totalRoadmapSlotsForExercises(members, omittedSetKeys)
            val current = members.any { it.id == currentExerciseId }
            val complete = total > 0 && done >= total
            val title = members.joinToString(" + ", transform = ::displayWorkoutExerciseName)
            val status = when {
                complete -> "✓ · $done/$total"
                current -> "en curso · $done/$total"
                groupId != null -> "${supersetGroups.firstOrNull { it.id == groupId }?.rounds ?: members.maxOf { it.sets.size }} rondas"
                else -> "$total series"
            }
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    .testTag("live-route-${groupId ?: members.first().id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).clickable(role = Role.Button) {
                        if (groupId != null && members.size > 1) onSelectSuperset(groupId) else onSelectExercise(members.first().id)
                    }.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = KpknType.bodyStrong, color = if (current) WizardColors.text else WizardColors.textMuted)
                    Text(status, style = KpknType.caption, color = if (complete) SymbolPalette.ok else WizardColors.textMuted)
                    members.forEach { member ->
                        completedSets.filterKeys { it.startsWith("${member.id}_") }.values.mapNotNull { it.restPauseDescription() }.forEach {
                            Text(it, style = KpknType.caption, color = WizardColors.textMuted)
                        }
                    }
                    members.forEach { member -> member.sets.indices.forEach { index ->
                        listOf(null, "left", "right").forEach { side ->
                            calibratedLoads[com.example.kpkn.screens.workout.workoutSetKey(member.id, index, side)]?.let { load ->
                                Text("S${index + 1}${side?.let { if(it == "left") " L" else " R" }.orEmpty()} · ${com.example.kpkn.screens.workout.finishNumber(load)} kg · calibrada",
                                    style = KpknType.caption, color = WizardColors.textMuted)
                            }
                        }
                    } }
                }
                val options = if (groupId != null && members.size > 1) onOpenSupersetOptions else onOpenExerciseOptions
                options?.let { open ->
                    KpknIconButton(KpknLineIcons.More, "Opciones de $title", { open(members.first().id) })
                }
            }
        }
        onAddExercise?.let { add -> HeaderSheetAction("Añadir ejercicio", add, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun CockpitMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(KpknSurface.inset).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = KpknType.caption, color = WizardColors.textMuted)
        Text(value, style = KpknType.metricM.copy(fontSize = 18.sp, lineHeight = 23.sp), color = WizardColors.text)
    }
}

/** El tick queda dentro del reloj, y solo mientras la app está en primer plano. No recalcula métricas, ruta ni AUGE. */
@Composable
private fun CockpitElapsedClock(startTimeMs: Long, targetMinutes: Int?, modifier: Modifier) {
    var now by remember(startTimeMs) { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, startTimeMs) {
        if (startTimeMs <= 0) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { now = System.currentTimeMillis(); delay(1_000) }
        }
    }
    val elapsed = if (startTimeMs > 0) ((now - startTimeMs) / 1_000L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else 0
    val progress = targetMinutes?.takeIf { it > 0 }?.let { (elapsed / (it * 60f)).coerceIn(0f, 1f) } ?: 0f
    Box(modifier.semantics { contentDescription = "Tiempo de sesión: ${KpknSetFormat.rest(elapsed)}" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 5.dp.toPx()
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            val stroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0xFF1C1C1C), -90f, 360f, false, Offset(pad, pad), arcSize, style = stroke)
            if (progress > 0f) drawArc(SymbolPalette.musculo, -90f, 360f * progress, false, Offset(pad, pad), arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(KpknSetFormat.rest(elapsed), style = KpknType.metricM.copy(fontSize = 18.sp), color = WizardColors.text)
            targetMinutes?.takeIf { it > 0 }?.let { Text("de $it min", style = KpknType.caption, color = WizardColors.textMuted) }
        }
    }
}

