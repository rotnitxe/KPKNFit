package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.LiveRoadmapCheck
import com.example.kpkn.screens.workout.LiveRoadmapMotion
import com.example.kpkn.screens.workout.LiveRouteGroup
import com.example.kpkn.screens.workout.WorkoutStepRules
import com.example.kpkn.screens.workout.cockpitSessionMetrics
import com.example.kpkn.screens.workout.displayWorkoutExerciseName
import com.example.kpkn.screens.workout.liveRouteGroups
import com.example.kpkn.screens.workout.sessionPaceWork
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.motion.kpknAnimate
import java.text.NumberFormat
import java.util.Locale

/*
 * «Recorrido de la sesión» (la «Ruta» del roadmap · escenas `lv-road-ruta` y `lv-road-ruta-14`). Orden del lienzo: la nota, el avance de la
 * sesión arriba (cifra en Syne, porcentaje y barra), una fila por ejercicio o superserie (número, título, estado; «Aquí» en el actual,
 * «Ir ›» en los demás y «⋯»), bajo el actual sus series en chips que entran escalonados, la superserie con sus miembros, el cardio bajo su
 * rótulo, «Reordenar pendientes», «Añadir ejercicio» y «Resumen» (registros, tonelaje, RIR medio, tiempo y ritmo). Nada de lo que ofrecía el
 * despliegue de la barra se quita. Todas las medidas y colores son las de `lv.css` (`.lv2-*`).
 */
private object Ruta {
    val Ink = Color(0xFFF2EEE6)
    val InkDark = Color(0xFF111111)
    val Mut = Color(0xFFB9B5AE)
    val Fnt = Color(0xFF92908A)
    val Done = Color(0xFF43D18C)
    val Blue = Color(0xFF8FB2FF)
    val Line = Color(0x0FFFFFFF)
    val Chip = Color(0x0DFFFFFF)
    val Track = Color(0x14FFFFFF)
}

private fun inter(size: Float, weight: FontWeight = FontWeight.Normal, lineHeight: Float = size * 1.3f) =
    TextStyle(fontFamily = WizardFonts.body, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)

private fun syne(size: Float, letterSpacing: Float = 0f) = TextStyle(fontFamily = WizardFonts.display, fontSize = size.sp,
    lineHeight = (size * 1.2f).sp, fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp, fontFeatureSettings = "tnum, lnum")

private fun Modifier.topLine(): Modifier = drawBehind { drawLine(Ruta.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }

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
    /** Número (1, 2…) de la serie en curso de la tarjeta: el chip que se resalta bajo el ejercicio actual. */
    currentSetNumber: Int? = null,
) {
    val groups = remember(exercises, supersetGroups) { liveRouteGroups(exercises, supersetGroups) }
    val paceWork = remember(exercises, completedSets, omittedSetKeys) { sessionPaceWork(exercises, completedSets, omittedSetKeys) }
    // `.lv2-sub` y `.lv2-pg`: la nota y el avance de la sesión.
    // `.bd` del lienzo lleva 2 dp de relleno arriba y la hoja de la sesión 6: la nota sube 4 dp (y el resto con ella).
    Text("Seleccionar permite consultar o editar. No registra ni omite una serie.",
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val up = 4.dp.roundToPx()
            layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
        }.padding(bottom = 12.dp),
        style = inter(12f, lineHeight = 16.8f), color = Ruta.Fnt)
    Column(Modifier.padding(bottom = 10.dp).testTag("live-route-progress")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${paceWork.completed}", style = syne(22f, -0.5f), color = Ruta.Ink)
                Text(" de ${paceWork.total} series", style = inter(12f), color = Ruta.Fnt)
            }
            Text("${if (paceWork.total > 0) Math.round(paceWork.completed * 100f / paceWork.total) else 0} %", style = inter(12f), color = Ruta.Fnt)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ruta.Track)) {
            val fraction = if (paceWork.total > 0) (paceWork.completed.toFloat() / paceWork.total).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ruta.Ink))
        }
    }
    var cardioHeader = false
    groups.forEachIndexed { position, group ->
        if (group.cardio && !cardioHeader) {
            cardioHeader = true
            CardioHeader()
        }
        RouteGroupRows(position, group, completedSets, omittedSetKeys, currentExerciseId, supersetGroups, calibratedLoads, currentSetNumber,
            onSelectExercise, onSelectSuperset, onSelectSet, onOpenExerciseOptions, onOpenSupersetOptions)
    }
    onReorder?.let { RouteMenuRow("Reordenar pendientes", KpknLineIcons.Grip, it, Modifier.padding(top = 10.dp).testTag("live-route-reorder")) }
    onAddExercise?.let { RouteMenuRow("Añadir ejercicio", KpknLineIcons.Plus, it, Modifier.testTag("live-route-add")) }

    val metrics = remember(exercises, completedSets, completedExercises, omittedSetKeys, liveEnergySummary, bodyWeight) {
        cockpitSessionMetrics(exercises, completedSets, completedExercises, omittedSetKeys, liveEnergySummary, bodyWeight)
    }
    val number = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).apply { maximumFractionDigits = 1 } }
    // `.lv2-sec` y `.lv2-met`: «RESUMEN» y la cuadrícula de dos columnas (rótulo en mayúsculas y cifra en Syne).
    Text("RESUMEN", Modifier.padding(top = 18.dp, bottom = 10.dp),
        style = inter(11f, FontWeight.SemiBold, 14f).copy(letterSpacing = 1.4.sp), color = Ruta.Fnt)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RouteMetric("Registros", "${metrics.completed} / ${metrics.total}", null, Modifier.weight(1f))
            RouteMetric("Tonelaje", number.format(metrics.tonnage), "kg", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RouteMetric("RIR medio", metrics.averageRir?.let(number::format) ?: "—", null, Modifier.weight(1f))
            RouteMetric("Tiempo", startTimeMs.takeIf { it > 0L }?.let { ((System.currentTimeMillis() - it) / 60_000L).coerceAtLeast(0L).toString() } ?: "—",
                startTimeMs.takeIf { it > 0L }?.let { "min" }, Modifier.weight(1f))
        }
    }
    Box(Modifier.padding(top = 14.dp)) { SessionPaceEstimate(startTimeMs, paceWork.completed, paceWork.total, targetMinutes) }
}

/** `.lv2-cd`: «CARDIO» en azul con un filete que llena el resto de la línea. */
@Composable
private fun CardioHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CARDIO", style = inter(11f, FontWeight.SemiBold, 14f).copy(letterSpacing = 1.4.sp), color = Ruta.Blue)
        Box(Modifier.weight(1f).height(1.dp).background(Ruta.Blue.copy(alpha = 0.2f)))
    }
}

/** `.lv2-mn`: fila de 54 dp con filete superior, icono de 18 dp y texto de 14 sp. */
@Composable
private fun RouteMenuRow(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 54.dp).topLine().clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = Ruta.Mut)
        Text(label, style = inter(14f), color = Ruta.Ink)
    }
}

/**
 * Una fila de la ruta (ejercicio, superserie o cardio) y, debajo, lo que el lienzo despliega en ella: sus miembros (superserie), las series
 * en chips (el ejercicio actual) y las descripciones extra (calibrada, rest-pause).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteGroupRows(
    position: Int,
    group: LiveRouteGroup,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
    currentExerciseId: String?,
    supersetGroups: List<SupersetGroup>,
    calibratedLoads: Map<String, Double>,
    currentSetNumber: Int?,
    onSelectExercise: (String) -> Unit,
    onSelectSuperset: (String) -> Unit,
    onSelectSet: ((exerciseId: String, setIndex: Int) -> Unit)?,
    onOpenExerciseOptions: ((String) -> Unit)?,
    onOpenSupersetOptions: ((String) -> Unit)?,
) {
    val members = group.members
    val superset = group.superset
    val cardio = group.cardio
    val done = WorkoutStepRules.completedRoadmapSlotsForExercises(members, completedSets, omittedSetKeys)
    val total = WorkoutStepRules.totalRoadmapSlotsForExercises(members, omittedSetKeys)
    val current = members.any { it.id == currentExerciseId }
    val finished = total > 0 && done >= total
    val title = if (superset) "Superserie de ${members.size}" else displayWorkoutExerciseName(members.first())
    val status = when {
        cardio -> members.first().cardioDetails?.targetDurationSeconds?.let { "Cardio · ${it / 60} min" } ?: "Cardio"
        superset -> "${supersetGroups.firstOrNull { it.id == group.id }?.rounds ?: members.maxOf { it.sets.size }} rondas · $done/$total series"
        else -> "$done/$total series" + if (finished) " · hecho" else ""
    }
    val numberColor = when { cardio -> Ruta.Blue; finished -> Ruta.Done; current -> Ruta.Ink; else -> Ruta.Fnt }
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
    val select = { if (superset) onSelectSuperset(group.id!!) else onSelectExercise(members.first().id) }
    val options = if (superset) onOpenSupersetOptions else onOpenExerciseOptions
    // `.lv2-rr`: filete superior, alto mínimo de 56 dp; a la izquierda el botón de ir (número y texto), a la derecha «Aquí» o «Ir ›» y «⋯».
    // 56 dp de fila más el filete de 1 dp: 57 dp de paso entre filas.
    Row(Modifier.fillMaxWidth().heightIn(min = 57.dp).topLine().padding(top = 1.dp).testTag("live-route-${group.id ?: members.first().id}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClick = select).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Text((position + 1).toString().padStart(2, '0'), Modifier.width(26.dp), style = syne(16f), color = numberColor, maxLines = 1)
            Column(Modifier.weight(1f)) {
                Text(title, style = inter(13f), color = if (current) Ruta.Ink else Ruta.Mut, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(status, Modifier.padding(top = 3.dp), style = inter(12f, lineHeight = 14.4f), color = if (finished) Ruta.Done else Ruta.Fnt)
            }
        }
        if (current) {
            Box(Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Ruta.Ink).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text("Aquí", style = inter(12f, FontWeight.SemiBold, 26f), color = Ruta.InkDark)
            }
        } else {
            Row(Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clickable(role = Role.Button, onClick = select)
                .semantics { contentDescription = "Ir a " + members.joinToString(" y ", transform = ::displayWorkoutExerciseName) }
                .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Ir", style = inter(13f, FontWeight.SemiBold), color = Ruta.Mut)
                Icon(KpknLineIcons.ChevronRight, null, Modifier.size(13.dp), tint = Ruta.Mut)
            }
        }
        // «⋯» abre las opciones del ejercicio o de la superserie (como en las filas de la vista Lista).
        options?.let { open ->
            Box(Modifier.width(40.dp).height(44.dp).clickable(role = Role.Button) { open(members.first().id) }
                .semantics { contentDescription = "Opciones de " + members.joinToString(" + ", transform = ::displayWorkoutExerciseName) }, contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.More, null, Modifier.size(16.dp), tint = Ruta.Fnt)
            }
        }
    }
    // `.lv2-grp .mem`: los miembros de la superserie con un filete vertical de 2 dp.
    if (superset) Column(Modifier.padding(start = 37.dp).drawBehind {
        drawLine(Color(0x2AFFFFFF), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx())
    }.padding(start = 12.dp)) {
        members.forEachIndexed { k, member ->
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A${k + 1}", Modifier.width(22.dp), style = inter(13f), color = Ruta.Fnt)
                Text(displayWorkoutExerciseName(member), style = inter(13f), color = if (current) Ruta.Ink else Ruta.Mut)
            }
        }
    }
    // Bajo el ejercicio en curso, sus series («S2 ✓»): elegir una lleva la tarjeta a esa serie para consultarla o editarla.
    val exercise = members.singleOrNull()
    if (current && exercise != null && !cardio && onSelectSet != null && exercise.sets.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth().padding(start = 37.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            exercise.sets.indices.forEach { index ->
                val doneSet = completedSets.keys.any { it == "${exercise.id}_$index" || it.startsWith("${exercise.id}_${index}_") }
                val isCurrent = currentSetNumber == index + 1
                Row(
                    Modifier.kpknAnimate(LiveRoadmapMotion.chip(index)).height(36.dp).widthIn(min = 52.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (isCurrent) Ruta.Ink else Ruta.Chip)
                        .clickable(role = Role.Button) { onSelectSet(exercise.id, index) }
                        .semantics { contentDescription = "Serie ${index + 1}" + if (doneSet) ", registrada" else "" }
                        .testTag("live-route-set-$index").padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    Text("S${index + 1}", style = inter(13f, FontWeight.Medium, 13f), color = if (isCurrent) Ruta.InkDark else Ruta.Mut)
                    if (doneSet) LiveRoadmapCheck(12.dp, 3f, if (isCurrent) Ruta.InkDark else Ruta.Mut)
                }
            }
        }
    }
    // `.lv2-xt`: descripciones extra del ejercicio (calibrada, rest-pause).
    extras.forEach { Text(it, Modifier.padding(start = 37.dp, bottom = 8.dp), style = inter(12f, lineHeight = 14.4f), color = Ruta.Fnt) }
}

@Composable
private fun RouteMetric(label: String, value: String, unit: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = inter(11f, FontWeight.Medium, 14f).copy(letterSpacing = 0.6.sp), color = Ruta.Fnt)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = syne(22f), color = Ruta.Ink)
            unit?.let { Text(" $it", style = inter(12f), color = Ruta.Mut) }
        }
    }
}
