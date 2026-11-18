package com.example.kpkn.screens.workout.components

import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.screens.workout.declaredWorkoutRmReference
import com.example.kpkn.screens.workout.eligibleWorkoutRmEstimate
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
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
    val sourceSet: CompletedSet?,
    val sourceDate: String,
    val fromCurrentSession: Boolean,
    val sourceDescription: String = "Historial",
    val trainingMax: Double? = null,
    val sourceTag: String? = null,
)

internal data class ExerciseRmRow(val reps: Int, val weightKg: Double, val inSessionRange: Boolean)

/** Reference comes from actual sets, never a stored precomputed estimate or a goal. */
internal fun buildExerciseRmReference(
    history: List<ExerciseHistoryEntry>,
    currentSets: List<CompletedSet>,
    today: LocalDate = LocalDate.now(),
    exercise: Exercise? = null,
    expectedLoadMode: LoadModeV2? = null,
    selectedTagId: String? = null,
    selectedTagName: String? = null,
    currentTagId: String? = null,
): ExerciseRmReference? {
    val expectedConfiguration = exercise?.catalogConfigurationId
    val expectedSetup = exercise?.sets.orEmpty().mapNotNull { it.defaultSetupProfileIdV3 ?: it.setupId }.distinct().singleOrNull()
    val expectedBrand = exercise?.sets.orEmpty().mapNotNull { it.machineBrand?.trim()?.takeIf(String::isNotBlank) }.distinct().singleOrNull()
    fun candidate(set: CompletedSet, date: String, current: Boolean, sourceTag: String? = null): ExerciseRmReference? {
        if (!set.weight.isFinite() || set.weight <= 0.0) return null
        val actualLoadMode = set.loadMode ?: set.recordedPayloadV3?.loadInputMode ?: LoadModeV2.LOAD
        if (expectedLoadMode != null && actualLoadMode != expectedLoadMode) return null
        if (expectedSetup != null && set.setupProfileId != expectedSetup) return null
        if (expectedBrand != null && set.machineBrand?.trim() != expectedBrand) return null
        val candidateTag = if (current) set.tagId ?: currentTagId ?: set.tagName else set.tagId ?: sourceTag ?: set.tagName
        if (current && selectedTagId != null && candidateTag != selectedTagId &&
            candidateTag != selectedTagName && set.tagName != selectedTagName
        ) return null
        val estimate = eligibleWorkoutRmEstimate(set, expectedLoadMode) ?: return null
        return estimate.let {
            ExerciseRmReference(
                estimated1RM = it,
                sourceSet = set,
                sourceDate = date,
                fromCurrentSession = current,
                sourceDescription = if (current) "Mejor serie de esta sesión" else "Mejor serie del historial",
                trainingMax = exercise?.referenceTrainingMax?.takeIf { tm -> tm.isFinite() && tm > 0.0 },
                sourceTag = set.tagName ?: sourceTag,
            )
        }
    }
    fun day(date: String): Long = runCatching { LocalDate.parse(date.take(10)).toEpochDay() }.getOrDefault(Long.MIN_VALUE)
    val compatibleHistory = history.filter { entry ->
        exercise == null || entry.catalogConfigurationId == expectedConfiguration
    }
    val candidates = compatibleHistory.flatMap { entry -> entry.sets.mapNotNull { candidate(it, entry.date, false, entry.tag) } } +
        currentSets.mapNotNull { set -> candidate(set, today.toString(), true, set.tagName) }
    return candidates.maxWithOrNull(
        compareBy<ExerciseRmReference> { it.estimated1RM }
            .thenBy { day(it.sourceDate) }
            .thenBy { it.fromCurrentSession },
    )?.let { best ->
        // A measured, compatible set outranks any stored declaration. Fall
        // back only when the exercise has no usable history yet.
        best
    } ?: exercise?.let { source ->
        val declared = declaredWorkoutRmReference(source)
            ?: return@let null
        val sourceDescription = when {
            source.reference1RM?.let { it.isFinite() && it > 0.0 } == true -> "1RM de referencia del ejercicio"
            source.prFor1RM?.let { it.weight > 0.0 && it.reps > 0 } == true -> "PR declarado · ${source.prFor1RM!!.weight} kg × ${source.prFor1RM!!.reps}"
            else -> "1RM calculado guardado"
        }
        ExerciseRmReference(
            estimated1RM = declared,
            sourceSet = null,
            sourceDate = "",
            fromCurrentSession = false,
            sourceDescription = sourceDescription,
            trainingMax = source.referenceTrainingMax?.takeIf { it.isFinite() && it > 0.0 },
        )
    }
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
    tagOptions: List<WorkoutTag> = emptyList(),
    selectedTagId: String? = null,
    onTagSelected: (String?) -> Unit = {},
    currentTagId: String? = selectedTagId,
) {
    val expectedLoadMode = exercise.sets.mapNotNull { it.loadModeV2 }.distinct().singleOrNull()
    val selectedTagName = tagOptions.firstOrNull { it.id == selectedTagId || it.name == selectedTagId }?.name
    val reference = remember(history, currentSets, exercise, expectedLoadMode, selectedTagId, selectedTagName, currentTagId) {
        buildExerciseRmReference(history, currentSets, exercise = exercise, expectedLoadMode = expectedLoadMode,
            selectedTagId = selectedTagId, selectedTagName = selectedTagName, currentTagId = currentTagId)
    }
    val trainingMax = exercise.referenceTrainingMax?.takeIf { it.isFinite() && it > 0.0 }
    val ranges = remember(exercise, currentSets) { exerciseRmSessionRanges(exercise, currentSets) }
    val rows = remember(reference, ranges) { buildExerciseRmRows(reference, ranges) }
    val ink = Color(0xFFF2EEE6)
    KpknSheet(backdropScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Veil, additionalGlassScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Panel, onDismissRequest = onDismiss, hazeState = hazeState, stableHeightFraction = .9f, modifier = Modifier.testTag("exercise-rm-sheet")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Consulta de RM", style = KpknType.title, color = ink, modifier = Modifier.weight(1f))
                KpknIconButton(KpknLineIcons.Close, "Cerrar consulta de RM", onDismiss)
            }
            Text(displayWorkoutExerciseName(exercise), style = KpknType.bodyStrong, color = ink)
            if (tagOptions.isNotEmpty()) {
                Text("Etiqueta para consultar · no modifica la sesión", style = KpknType.caption, color = ink.copy(alpha = .55f))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedTagId == null, onClick = { onTagSelected(null) }, label = { Text("Todas") })
                    tagOptions.forEach { tag ->
                        FilterChip(selected = selectedTagId == tag.id || selectedTagId == tag.name,
                            onClick = { onTagSelected(tag.id) }, label = { Text(tag.name.ifBlank { "Sin nombre" }) })
                    }
                }
            }
            if (reference == null && trainingMax == null) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    Text("Todavía no hay una referencia de RM", style = KpknType.bodyStrong, color = ink)
                    Spacer(Modifier.height(8.dp))
                    Text("Registra una serie válida con carga y repeticiones para consultar tus RM.", style = KpknType.caption, color = ink.copy(alpha = .6f))
                }
            } else {
                if (reference != null) Column(Modifier.fillMaxWidth().background(Color(0xFF111111), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (reference.sourceSet == null) "REFERENCIA DISPONIBLE" else "PR ESTIMADO", style = KpknType.eyebrow, color = ink.copy(alpha = .55f))
                    Text("${rmWeight(reference.estimated1RM)} kg", style = KpknType.metricL, color = ink, modifier = Modifier.testTag("exercise-rm-reference"))
                    val sourceLine = reference.sourceSet?.let { set ->
                        "${rmWeight(set.weight)} kg × ${set.reps} · ${if (reference.fromCurrentSession) "Hoy" else reference.sourceDate.ifBlank { "Fecha no disponible" }}" +
                            reference.sourceTag?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    } ?: reference.sourceDescription
                    Text(sourceLine, style = KpknType.caption, color = ink.copy(alpha = .6f))
                }
                trainingMax?.let { tm ->
                    Text("TM de trabajo · ${rmWeight(tm)} kg", style = KpknType.action, color = ink, modifier = Modifier.testTag("exercise-rm-training-max"))
                }
                if (ranges.isNotEmpty()) Text("Rango de la sesión · ${ranges.joinToString(" / ") { it.format() }} reps", style = KpknType.action, color = ink, modifier = Modifier.testTag("exercise-rm-session-range"))
                if (reference != null) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("exercise-rm-list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(rows, key = { it.reps }) { row ->
                            Row(Modifier.fillMaxWidth().background(if (row.inSessionRange) Color(0xFF1B1B1B) else Color(0xFF111111), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp).testTag("exercise-rm-row-${row.reps}"), verticalAlignment = Alignment.CenterVertically) {
                                Text("${row.reps} RM", style = KpknType.bodyStrong, color = ink, modifier = Modifier.weight(1f))
                                if (row.inSessionRange) Text("Sesión", style = KpknType.caption, color = ink.copy(alpha = .6f), modifier = Modifier.padding(end = 12.dp))
                                Text("${rmWeight(row.weightKg)} kg", style = KpknType.metricM, color = ink)
                            }
                        }
                    }
                } else if (trainingMax != null) {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        Text("La prescripción de esta variante usa el TM directamente.", style = KpknType.bodyStrong, color = ink)
                        Text("No se convierte el TM a 1RM sin una referencia declarada o una serie válida.", Modifier.padding(top = 8.dp), style = KpknType.caption, color = ink.copy(alpha = .6f))
                    }
                }
            }
            KpknCompactPill("Listo", onClick = onDismiss, fullWidth = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun rmWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.forLanguageTag("es"), "%.1f", value)
