package com.example.kpkn.screens.workout.components

import com.example.kpkn.domain.performance.StrengthModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.ExerciseHistoryEntry
import com.example.kpkn.screens.workout.displayWorkoutExerciseName
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import java.util.Locale

internal data class ExerciseRmReference(
    val estimated1RM: Double,
    val sourceSet: CompletedSet,
    val sourceDate: String,
    val fromCurrentSession: Boolean,
)

internal data class ExerciseRmRow(val reps: Int, val weightKg: Double, val inSessionRange: Boolean)

/** Reference comes from actual sets, never a stored precomputed estimate or a goal. */
internal fun buildExerciseRmReference(
    history: List<ExerciseHistoryEntry>,
    currentSets: List<CompletedSet>,
    today: LocalDate = LocalDate.now(),
): ExerciseRmReference? {
    fun candidate(set: CompletedSet, date: String, current: Boolean): ExerciseRmReference? {
        if (!set.weight.isFinite() || set.weight <= 0.0 || set.reps <= 0 || set.skipped || set.isWarmup || set.isFailedSet) return null
        if (set.recordedPayloadV3?.unitMode?.let { it != UnitModeV2.REPS } == true ||
            set.timeSeconds != null || set.distanceKm != null) return null
        val estimate = StrengthModel.estimate1RM(set)
        return estimate.takeIf { it.isFinite() && it > 0.0 }?.let { ExerciseRmReference(it, set, date, current) }
    }
    fun day(date: String): Long = runCatching { LocalDate.parse(date.take(10)).toEpochDay() }.getOrDefault(Long.MIN_VALUE)
    val candidates = history.flatMap { entry -> entry.sets.mapNotNull { candidate(it, entry.date, false) } } +
        currentSets.mapNotNull { candidate(it, today.toString(), true) }
    return candidates.maxWithOrNull(
        compareBy<ExerciseRmReference> { it.estimated1RM }
            .thenBy { day(it.sourceDate) }
            .thenBy { it.fromCurrentSession },
    )
}

/** Pending ordinals are trusted only when recorded explicitly; otherwise show the general plan. */
internal fun exerciseRmSessionRanges(exercise: Exercise, currentSets: List<CompletedSet>): List<RepRange> {
    val indexed = currentSets.filterNot { it.isWarmup }.mapNotNull { completed ->
        val index = completed.setIndex?.takeIf { it in exercise.sets.indices }
            ?: exercise.sets.indexOfFirst { it.id == completed.id }.takeIf { it >= 0 }
        index?.let { it to completed }
    }.groupBy({ it.first }, { it.second })
    val completedIndices = indexed.filterValues { sets ->
        !exercise.isEffectivelyUnilateral() || sets.any { it.side == null } ||
            sets.any { it.side == "left" } && sets.any { it.side == "right" }
    }.keys
    val general = exercise.sets.withIndex().filterNot { it.value.isEmptySlot }
    val pending = general.filter { it.index !in completedIndices && it.value.completedReps == null }
    return (pending.ifEmpty { general }).mapNotNull { it.value.effectiveRepRange() }.distinct()
}

internal fun buildExerciseRmRows(reference: ExerciseRmReference?, ranges: List<RepRange>): List<ExerciseRmRow> =
    if (reference == null) emptyList() else (1..20).map { reps ->
        ExerciseRmRow(reps, StrengthModel.weightFor(reference.estimated1RM, reps), ranges.any { it.contains(reps) })
    }

@Composable
internal fun ExerciseRmSheet(
    exercise: Exercise,
    history: List<ExerciseHistoryEntry>,
    currentSets: List<CompletedSet>,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
) {
    val reference = remember(history, currentSets) { buildExerciseRmReference(history, currentSets) }
    val ranges = remember(exercise, currentSets) { exerciseRmSessionRanges(exercise, currentSets) }
    val rows = remember(reference, ranges) { buildExerciseRmRows(reference, ranges) }
    val ink = Color(0xFFF2EEE6)
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState, stableHeightFraction = .9f, modifier = Modifier.testTag("exercise-rm-sheet")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Consulta de RM", style = KpknType.title, color = ink, modifier = Modifier.weight(1f))
                KpknIconButton(KpknLineIcons.Close, "Cerrar consulta de RM", onDismiss)
            }
            Text(displayWorkoutExerciseName(exercise), style = KpknType.bodyStrong, color = ink)
            if (reference == null) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    Text("Todavía no hay una referencia de RM", style = KpknType.bodyStrong, color = ink)
                    Spacer(Modifier.height(8.dp))
                    Text("Registra una serie válida con carga y repeticiones para consultar tus RM.", style = KpknType.caption, color = ink.copy(alpha = .6f))
                }
            } else {
                Column(Modifier.fillMaxWidth().background(Color(0xFF111111), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PR ESTIMADO", style = KpknType.eyebrow, color = ink.copy(alpha = .55f))
                    Text("${rmWeight(reference.estimated1RM)} kg", style = KpknType.metricL, color = ink, modifier = Modifier.testTag("exercise-rm-reference"))
                    Text("${rmWeight(reference.sourceSet.weight)} kg × ${reference.sourceSet.reps} · ${if (reference.fromCurrentSession) "Hoy" else reference.sourceDate.ifBlank { "Fecha no disponible" }}", style = KpknType.caption, color = ink.copy(alpha = .6f))
                }
                if (ranges.isNotEmpty()) Text("Rango de la sesión · ${ranges.joinToString(" / ") { it.format() }} reps", style = KpknType.action, color = ink, modifier = Modifier.testTag("exercise-rm-session-range"))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("exercise-rm-list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(rows, key = { it.reps }) { row ->
                        Row(Modifier.fillMaxWidth().background(if (row.inSessionRange) Color(0xFF1B1B1B) else Color(0xFF111111), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp).testTag("exercise-rm-row-${row.reps}"), verticalAlignment = Alignment.CenterVertically) {
                            Text("${row.reps} RM", style = KpknType.bodyStrong, color = ink, modifier = Modifier.weight(1f))
                            if (row.inSessionRange) Text("Sesión", style = KpknType.caption, color = ink.copy(alpha = .6f), modifier = Modifier.padding(end = 12.dp))
                            Text("${rmWeight(row.weightKg)} kg", style = KpknType.metricM, color = ink)
                        }
                    }
                }
            }
            KpknCompactPill("Listo", onClick = onDismiss, fullWidth = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun rmWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.forLanguageTag("es"), "%.1f", value)
