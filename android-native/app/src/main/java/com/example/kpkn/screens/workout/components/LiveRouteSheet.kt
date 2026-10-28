package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.workout.restPauseDescription
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.WorkoutStepRules
import com.example.kpkn.screens.workout.cockpitSessionMetrics
import com.example.kpkn.screens.workout.displayWorkoutExerciseName
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import java.text.NumberFormat
import java.util.Locale

/**
 * «Recorrido de la sesión» (prototipo 02, `route`): una fila por ejercicio o superserie con su número en Syne, el avance de series y,
 * bajo el que está en curso, sus series en chips para consultarlas o editarlas. Debajo, «Reordenar pendientes» y «Añadir ejercicio».
 * Las métricas del antiguo resumen (registros, tonelaje, RIR medio, gasto y ritmo) se conservan al final, en «Resumen».
 */
@Composable
internal fun LiveRouteSheetContent(
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
    calibratedLoads: Map<String, Double>,
    onSelectExercise: (String) -> Unit,
    onSelectSuperset: (String) -> Unit,
    onSelectSet: ((exerciseId: String, setIndex: Int) -> Unit)?,
    onOpenExerciseOptions: ((String) -> Unit)?,
    onOpenSupersetOptions: ((String) -> Unit)?,
    onReorder: (() -> Unit)?,
    onAddExercise: (() -> Unit)?,
) {
    LiveSheetSubtitle("Seleccionar permite consultar o editar. No registra ni omite una serie.")
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
    groups.forEachIndexed { position, (groupId, members) ->
        val superset = groupId != null && members.size > 1
        val done = WorkoutStepRules.completedRoadmapSlotsForExercises(members, completedSets, omittedSetKeys)
        val total = WorkoutStepRules.totalRoadmapSlotsForExercises(members, omittedSetKeys)
        val current = members.any { it.id == currentExerciseId }
        val title = members.joinToString(" + ", transform = ::displayWorkoutExerciseName)
        val status = buildString {
            if (superset) append("Superserie · ${supersetGroups.firstOrNull { it.id == groupId }?.rounds ?: members.maxOf { it.sets.size }} rondas · ")
            append("$done/$total series")
        }
        val extras = members.flatMap { member ->
            completedSets.filterKeys { it.startsWith("${member.id}_") }.values.mapNotNull { it.restPauseDescription() } +
                member.sets.indices.flatMap { index ->
                    listOf(null, "left", "right").mapNotNull { side ->
                        calibratedLoads[com.example.kpkn.screens.workout.workoutSetKey(member.id, index, side)]?.let { load ->
                            "S${index + 1}${side?.let { if (it == "left") " L" else " R" }.orEmpty()} · " +
                                "${com.example.kpkn.screens.workout.finishNumber(load)} kg · calibrada"
                        }
                    }
                }
        }
        // `.route-row`: filete superior, número en Syne 16 (tinta si es el actual), nombre 12 y avance 10 en gris tenue.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).liveSheetTopLine()
                .testTag("live-route-${groupId ?: members.first().id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                    .clickable(role = Role.Button) { if (superset) onSelectSuperset(groupId!!) else onSelectExercise(members.first().id) }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Text(
                    (position + 1).toString().padStart(2, '0'),
                    style = TextStyle(fontFamily = WizardFonts.display, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                    color = if (current) WizardColors.text else LiveSheetTokens.Faint,
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = KpknType.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = if (current) WizardColors.text else LiveSheetTokens.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(status, Modifier.padding(top = 4.dp), style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = if (total > 0 && done >= total) WizardColors.done else LiveSheetTokens.Faint)
                    extras.forEach {
                        Text(it, Modifier.padding(top = 2.dp), style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp),
                            color = LiveSheetTokens.Faint)
                    }
                }
            }
            // En lugar del ›, «⋯» abre las opciones del ejercicio o de la superserie (como en las filas de la vista Lista).
            val options = if (superset) onOpenSupersetOptions else onOpenExerciseOptions
            options?.let { open ->
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { open(members.first().id) }
                        .semantics { contentDescription = "Opciones de $title" },
                    contentAlignment = Alignment.Center,
                ) { Icon(KpknLineIcons.More, null, Modifier.size(16.dp), tint = LiveSheetTokens.Faint) }
            }
        }
        // Bajo el ejercicio en curso, sus series («S2 ✓»): elegir una lleva la tarjeta a esa serie para consultarla o editarla.
        val exercise = members.singleOrNull()
        if (current && exercise != null && !exercise.isCardio && onSelectSet != null && exercise.sets.isNotEmpty()) {
            LiveSheetChoiceRow(
                exercise.sets.indices.map { index ->
                    val doneSet = completedSets.keys.any { it == "${exercise.id}_$index" || it.startsWith("${exercise.id}_${index}_") }
                    index to "S${index + 1}" + if (doneSet) " ✓" else ""
                },
                selected = { false },
                onSelect = { onSelectSet(exercise.id, it) },
                modifier = Modifier.padding(start = 30.dp),
                tagPrefix = "live-route-set",
            )
        }
    }
    onReorder?.let { LiveSheetMenuRow("Reordenar pendientes", it, icon = KpknLineIcons.Reorder, modifier = Modifier.testTag("live-route-reorder")) }
    onAddExercise?.let { LiveSheetMenuRow("Añadir ejercicio", it, icon = KpknLineIcons.Plus, modifier = Modifier.testTag("live-route-add")) }

    val metrics = remember(exercises, completedSets, completedExercises, omittedSetKeys, liveEnergySummary, bodyWeight) {
        cockpitSessionMetrics(exercises, completedSets, completedExercises, omittedSetKeys, liveEnergySummary, bodyWeight)
    }
    val number = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).apply { maximumFractionDigits = 1 } }
    LiveSheetSectionLabel("Resumen")
    // `.metric-grid` del prototipo: dos columnas, rótulo en mayúsculas y cifra en Syne.
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RouteMetric("Registros", "${metrics.completed} / ${metrics.total}", null, Modifier.weight(1f))
            RouteMetric("Tonelaje", number.format(metrics.tonnage), "kg", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RouteMetric("RIR medio", metrics.averageRir?.let(number::format) ?: "—", null, Modifier.weight(1f))
            RouteMetric("Gasto", metrics.calories.kcal?.let { "≈ $it" } ?: "—", metrics.calories.kcal?.let { "kcal" }, Modifier.weight(1f))
        }
    }
    metrics.calories.hint?.let { LiveSheetShortCopy(it) }
    val paceWork = remember(exercises, completedSets, omittedSetKeys) {
        com.example.kpkn.screens.workout.sessionPaceWork(exercises, completedSets, omittedSetKeys)
    }
    Box(Modifier.padding(top = 12.dp)) { SessionPaceEstimate(startTimeMs, paceWork.completed, paceWork.total, targetMinutes) }
}

@Composable
private fun RouteMetric(label: String, value: String, unit: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = .6.sp), color = LiveSheetTokens.Faint)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = TextStyle(fontFamily = WizardFonts.display, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                color = WizardColors.text)
            unit?.let { Text(" $it", Modifier.padding(bottom = 3.dp), style = KpknType.caption.copy(fontSize = 10.sp), color = LiveSheetTokens.Muted) }
        }
    }
}
