package com.example.kpkn.screens.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.components.LiveSetDetailSheet
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetNumberGrid
import com.example.kpkn.screens.workout.components.LiveSheetSecondary
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.Locale

/** Una fila de la Lista: una serie real, con su identidad y su lado cuando es unilateral. */
internal data class ListSetRow(
    val exercise: Exercise,
    val exerciseOrder: Int,
    val setIndex: Int,
    val side: String?,
    val key: String,
    val label: String,
    val planned: ExerciseSet,
    val completed: CompletedSet?,
)

internal sealed interface ListSection {
    data class Single(val exercise: Exercise, val order: Int) : ListSection
    data class Superset(val groupId: String, val members: List<Pair<Exercise, Int>>) : ListSection
}

private fun sideLabel(side: String?): String? = when (side) {
    "left" -> "I"
    "right" -> "D"
    else -> null
}

// Tokens de la Lista del prototipo 02 (`.set-row`, `.exercise-row`, `.inline-editor`).
private val ListLine = Color(0x1AFFFFFF)
private val ListRowLine = Color(0x09FFFFFF)
private val ListActiveFill = Color(0x06FFFFFF)
private val ListEditorFill = Color(0x04FFFFFF)
private val ListEditorLine = Color(0x15FFFFFF)
private val ListChipFill = Color(0x05FFFFFF)
private val ListDone = Color(0xFF9EACA4)
private val ListSuperLine = Color(0xFFD0C9BC)
private val SyneValue = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)
private val Body = TextStyle(fontFamily = WizardFonts.body)

/**
 * Vista Lista del prototipo 02 (B · Lista, capturas 01, 06, 07 y 08): el contexto del ejercicio en curso encabeza la lista y se desplaza
 * con ella; «REGISTRO DE LA SESIÓN x / n»; la preparación compacta cuando la hay; una fila `.set-row` por serie (número, tipo, cifra en
 * Syne, esfuerzo y registro) y un solo acordeón abierto con el editor en línea y «Registrar serie N». Las superseries van por rondas
 * con A1 / A2. Comparte borrador, historial y registro con la tarjeta; nunca convierte el esfuerzo planificado en realizado.
 */
@Composable
internal fun LiveSessionListView(
    uiState: WorkoutUiState,
    exercises: List<Exercise>,
    currentExerciseId: String?,
    accentColor: Color,
    warmupWorkingWeight: Double?,
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier,
    contextHeader: @Composable () -> Unit = {},
    onOpenExerciseOptions: (String) -> Unit = {},
    onOpenSupersetSettings: (String) -> Unit = {},
) {
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var recordRequest by remember { mutableIntStateOf(0) }
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val sections = remember(exercises) { buildSections(exercises) }
    val workingDone = exercises.sumOf { exercise ->
        rowsFor(exercise, 0, uiState.completedSets).count { it.completed != null && !it.completed.skipped }
    }
    val workingTotal = exercises.sumOf { exercise -> rowsFor(exercise, 0, uiState.completedSets).size }

    fun open(row: ListSetRow) {
        val opening = expandedKey != row.key
        expandedKey = if (opening) row.key else null
        if (opening) viewModel.selectWorkoutStep(WorkoutStepRules.workingStepKey(row.exercise.id, row.setIndex, row.side))
    }
    fun register(row: ListSetRow) {
        if (row.completed != null && !row.completed.skipped) {
            viewModel.showSeriesTypeSheet(row.exercise.id, row.setIndex)
            return
        }
        if (expandedKey != row.key) open(row)
        recordRequest++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("live-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 28.dp),
    ) {
        // `.list-area > .context`: el ejercicio en curso encabeza la lista y se va con ella al desplazarse.
        item(key = "context") {
            Box(Modifier.fillMaxWidth().padding(bottom = 13.dp).drawBehind {
                drawLine(Color(0x10FFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }.padding(bottom = 13.dp)) { contextHeader() }
        }
        val current = exercises.firstOrNull { it.id == currentExerciseId }
        val prepPending = current != null && (
            current.mobilitySeries.any { !mobilityDone(uiState, current.id, it.id, it.sets) } ||
                current.warmupSets.any { !warmupDone(uiState, current.id, it.id) }
            )
        if (current != null && prepPending) {
            item(key = "prep-${current.id}") {
                CompactPreparation(
                    exercise = current,
                    workingWeight = warmupWorkingWeight,
                    uiState = uiState,
                    onToggleWarmup = { warmupId, done -> viewModel.markWarmupComplete(current.id, warmupId, done) },
                    onToggleMobility = { mobilityId, sets, done ->
                        (0 until sets.coerceAtLeast(1)).forEach { index ->
                            viewModel.markMobilityComplete(current.id, mobilityId, index, done)
                        }
                    },
                    onGoToWarmups = {
                        current.warmupSets.firstOrNull()?.let { viewModel.selectWorkoutStep(WorkoutStepRules.warmupStepKey(current.id, it.id)) }
                    },
                    onGoToWorking = {
                        val first = rowsFor(current, 0, uiState.completedSets).firstOrNull { it.completed == null }
                        if (first != null) viewModel.selectWorkoutStep(WorkoutStepRules.workingStepKey(current.id, first.setIndex, first.side))
                    },
                )
            }
        }
        item(key = "intro") {
            ListIntro(if (current != null && prepPending) "SERIES EFECTIVAS" else "REGISTRO DE LA SESIÓN", "$workingDone / $workingTotal")
        }
        sections.forEach { section ->
            when (section) {
                is ListSection.Single -> {
                    val exercise = section.exercise
                    val isCollapsed = exercise.id in collapsed
                    item(key = "ex-${exercise.id}") {
                        ExerciseListHeader(
                            exercise = exercise,
                            onToggleCollapse = { collapsed = if (isCollapsed) collapsed - exercise.id else collapsed + exercise.id },
                            onOptions = { onOpenExerciseOptions(exercise.id) },
                        )
                    }
                    val rows = rowsFor(exercise, section.order, uiState.completedSets)
                    if (isCollapsed) {
                        item(key = "collapsed-${exercise.id}") {
                            Text("${rows.count { it.completed != null }} registros · tocar para desplegar",
                                Modifier.fillMaxWidth().padding(vertical = 8.dp), style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint)
                        }
                    } else {
                        rows.forEach { row ->
                            item(key = row.key) {
                                ListRowWithEditor(row, expandedKey == row.key, isActive(row, uiState, currentExerciseId), uiState, viewModel,
                                    recordRequest, onOpen = { open(row) }, onRegister = { register(row) }, onClose = { expandedKey = null })
                            }
                        }
                    }
                }
                is ListSection.Superset -> {
                    item(key = "sg-${section.groupId}") {
                        SupersetHeader(section.members.map { it.first }, onSettings = { onOpenSupersetSettings(section.groupId) })
                    }
                    val roundCount = section.members.maxOfOrNull { it.first.sets.count { set -> !set.isEmptySlot } } ?: 0
                    for (round in 0 until roundCount) {
                        item(key = "round-${section.groupId}-$round") { RoundLabel(round, section.groupId) }
                        section.members.forEachIndexed { memberIdx, (member, order) ->
                            val set = member.sets.getOrNull(round) ?: return@forEachIndexed
                            if (set.isEmptySlot) return@forEachIndexed
                            val rows = rowsForSet(member, order, round, set, uiState.completedSets, "${round + 1}")
                            item(key = "sm-${member.id}-$round") { SupersetMemberName("A${memberIdx + 1}", displayWorkoutExerciseName(member)) }
                            rows.forEach { row ->
                                item(key = row.key) {
                                    Box(Modifier.padding(start = 15.dp).supersetRail(inner = true)) {
                                        ListRowWithEditor(row, expandedKey == row.key, isActive(row, uiState, currentExerciseId), uiState, viewModel,
                                            recordRequest, onOpen = { open(row) }, onRegister = { register(row) }, onClose = { expandedKey = null })
                                    }
                                }
                            }
                        }
                    }
                    item(key = "sg-end-${section.groupId}") { Spacer(Modifier.height(25.dp)) }
                }
            }
        }
        item(key = "list-tail") { Spacer(Modifier.height(8.dp)) }
    }
}

private fun isActive(row: ListSetRow, uiState: WorkoutUiState, currentExerciseId: String?): Boolean =
    row.exercise.id == currentExerciseId && row.setIndex == uiState.currentSetIdx

/** El filete de la superserie: 2 dp crema a la izquierda del bloque (`.v2-superset`) y 1 dp tenue por ejercicio. */
private fun Modifier.supersetRail(inner: Boolean): Modifier = drawBehind {
    drawLine(if (inner) Color(0x22FFFFFF) else ListSuperLine, Offset(0f, 0f), Offset(0f, size.height), (if (inner) 1 else 2).dp.toPx())
}

@Composable
private fun ListIntro(label: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Body.copy(fontSize = 9.sp, letterSpacing = .7.sp), color = WizardColors.textFaint)
        Text(count, style = Body.copy(fontSize = 9.sp, letterSpacing = .7.sp), color = WizardColors.textFaint)
    }
}

@Composable
private fun ListRowWithEditor(
    row: ListSetRow,
    expanded: Boolean,
    isCurrent: Boolean,
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
    recordRequest: Int,
    onOpen: () -> Unit,
    onRegister: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        SetListRow(row, active = expanded || isCurrent, onOpen = onOpen, onRegister = onRegister)
        if (expanded) InlineSetEditor(row, uiState, viewModel, recordRequest, onClose)
    }
}

internal fun buildSections(exercises: List<Exercise>): List<ListSection> {
    val out = mutableListOf<ListSection>()
    val visited = mutableSetOf<String>()
    exercises.forEachIndexed { index, exercise ->
        if (!visited.add(exercise.id)) return@forEachIndexed
        val groupId = exercise.supersetGroupRefOrLegacyId()
        if (groupId.isNullOrBlank()) {
            out += ListSection.Single(exercise, index)
        } else {
            val members = exercises.withIndex()
                .filter { it.value.supersetGroupRefOrLegacyId() == groupId }
                .map { it.value to it.index }
            members.forEach { visited += it.first.id }
            out += ListSection.Superset(groupId, members)
        }
    }
    return out
}

internal fun rowsFor(exercise: Exercise, order: Int, completedSets: Map<String, CompletedSet>): List<ListSetRow> =
    exercise.sets.flatMapIndexed { setIndex, set ->
        if (set.isEmptySlot) emptyList()
        else rowsForSet(exercise, order, setIndex, set, completedSets, "${setIndex + 1}")
    }

internal fun rowsForSet(
    exercise: Exercise,
    order: Int,
    setIndex: Int,
    set: ExerciseSet,
    completedSets: Map<String, CompletedSet>,
    label: String,
): List<ListSetRow> {
    val sides = if (exercise.isUnilateral) listOf("left", "right") else listOf<String?>(null)
    return sides.map { side ->
        val key = workoutSetKey(exercise.id, setIndex, side)
        val suffix = sideLabel(side)
        ListSetRow(
            exercise = exercise,
            exerciseOrder = order,
            setIndex = setIndex,
            side = side,
            key = key,
            label = if (suffix == null) label else "$label $suffix",
            planned = set,
            completed = completedSets[key],
        )
    }
}

/** `.exercise-row`: nombre 12/600 (pliega y despliega), «N series» y ⋯ con las opciones del ejercicio. */
@Composable
private fun ExerciseListHeader(exercise: Exercise, onToggleCollapse: () -> Unit, onOptions: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).drawBehind {
            drawLine(ListLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(top = 9.dp, bottom = 6.dp).testTag("live-list-exercise-${exercise.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = displayWorkoutExerciseName(exercise),
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onToggleCollapse),
            style = Body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            color = WizardColors.text,
        )
        Text("${exercise.sets.count { !it.isEmptySlot }} series", style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint, maxLines = 1)
        ListIconButton(KpknLineIcons.More, "Opciones de ${displayWorkoutExerciseName(exercise)}", onOptions, width = 30.dp)
    }
}

@Composable
private fun ListIconButton(icon: ImageVector, description: String, onClick: () -> Unit, width: Dp = 44.dp, size: Dp = 16.dp) {
    Box(Modifier.width(width).height(36.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size), tint = WizardColors.textMuted)
    }
}

/** `.v2-super-head` + `.v2-super-plan`: «SUPERSERIE A», los nombres en Syne 18, el ajuste y el plan de rondas y descansos. */
@Composable
private fun SupersetHeader(members: List<Exercise>, onSettings: () -> Unit) {
    val rounds = members.maxOfOrNull { it.sets.count { set -> !set.isEmptySlot } } ?: 0
    val between = members.firstNotNullOfOrNull { it.supersetRestBetween ?: it.restTime }
    val after = members.firstNotNullOfOrNull { it.supersetRestAfter }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).supersetRail(inner = false).padding(start = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SUPERSERIE", style = Body.copy(fontSize = 9.sp, letterSpacing = 1.2.sp), color = WizardColors.textFaint)
                Text(members.joinToString(" + ", transform = ::displayWorkoutExerciseName), Modifier.padding(top = 8.dp),
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp),
                    color = WizardColors.text)
            }
            ListIconButton(KpknLineIcons.Sliders, "Configurar superserie", onSettings)
        }
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            listOfNotNull("$rounds rondas", between?.let { "Entre ejercicios · $it s" }, after?.let { "Tras ronda · ${KpknClock.format(it)}" })
                .forEach { Text(it, style = Body.copy(fontSize = 9.sp), color = WizardColors.textFaint) }
        }
    }
}

private object KpknClock {
    fun format(seconds: Int): String = "%d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

@Composable
private fun RoundLabel(round: Int, groupId: String) {
    Row(
        modifier = Modifier.fillMaxWidth().supersetRail(inner = false).padding(start = 13.dp, top = 6.dp, bottom = 10.dp)
            .testTag("live-list-round-$groupId-$round"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("RONDA ${round + 1}", style = Body.copy(fontSize = 9.sp, letterSpacing = .8.sp), color = WizardColors.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(Color(0x16FFFFFF)))
    }
}

@Composable
private fun SupersetMemberName(badge: String, name: String) {
    Row(Modifier.fillMaxWidth().supersetRail(inner = false).padding(start = 15.dp).supersetRail(inner = true).padding(start = 10.dp, top = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(badge, Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0x10FFFFFF)).padding(4.dp),
            style = Body.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = WizardColors.text)
        Text(name, style = Body.copy(fontSize = 11.sp), color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.v2-prep-list`: «PREPARACIÓN», el título en Syne 18 con su avance, una fila por movimiento o aproximación y el paso siguiente. */
@Composable
private fun CompactPreparation(
    exercise: Exercise,
    workingWeight: Double?,
    uiState: WorkoutUiState,
    onToggleWarmup: (warmupId: String, done: Boolean) -> Unit,
    onToggleMobility: (mobilityId: String, sets: Int, done: Boolean) -> Unit,
    onGoToWarmups: () -> Unit,
    onGoToWorking: () -> Unit,
) {
    val showMobility = exercise.mobilitySeries.isNotEmpty() &&
        exercise.mobilitySeries.any { !mobilityDone(uiState, exercise.id, it.id, it.sets) }
    val done = if (showMobility) {
        exercise.mobilitySeries.count { mobility -> mobilityDone(uiState, exercise.id, mobility.id, mobility.sets) }
    } else {
        exercise.warmupSets.count { warmup -> warmupDone(uiState, exercise.id, warmup.id) }
    }
    val total = if (showMobility) exercise.mobilitySeries.size else exercise.warmupSets.size
    val syne18 = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 21.dp).drawBehind {
            drawLine(Color(0x1BFFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(bottom = 8.dp).testTag("live-list-preparation"),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PREPARACIÓN", Modifier.padding(bottom = 5.dp), style = Body.copy(fontSize = 8.sp, letterSpacing = 1.sp), color = WizardColors.textFaint)
                Text(if (showMobility) "Movilidad" else "Aproximaciones", style = syne18, color = WizardColors.text)
            }
            Text("$done / $total", style = syne18, color = WizardColors.textMuted)
        }
        if (showMobility) {
            exercise.mobilitySeries.forEachIndexed { index, mobility ->
                val completed = mobilityDone(uiState, exercise.id, mobility.id, mobility.sets)
                PrepRow("%02d".format(Locale.US, index + 1), mobility.name, mobilityVolume(mobility), completed,
                    "live-list-mobility-${mobility.id}") { onToggleMobility(mobility.id, mobility.sets, !completed) }
            }
        } else {
            exercise.warmupSets.forEachIndexed { index, warmup ->
                val completed = warmupDone(uiState, exercise.id, warmup.id)
                val load = (workingWeight ?: 0.0) * warmup.percentageOfWorkingWeight / 100.0
                PrepRow("A${index + 1}", "${formatDecimal(warmup.percentageOfWorkingWeight)} %",
                    if (load > 0.0) "${formatDecimal(load)} × ${warmup.targetReps}" else "× ${warmup.targetReps}", completed,
                    "live-list-warmup-${warmup.id}", valueInSyne = true) { onToggleWarmup(warmup.id, !completed) }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (!showMobility && workingWeight != null && workingWeight > 0.0) "Referencia · ${formatDecimal(workingWeight)} kg" else "",
                style = Body.copy(fontSize = 10.sp), color = WizardColors.textMuted,
            )
            val toWarmups = showMobility && exercise.warmupSets.isNotEmpty()
            Text(
                if (toWarmups) "Aproximaciones →" else "Ir a efectivas →",
                Modifier.heightIn(min = 42.dp).clickable(role = Role.Button, onClick = if (toWarmups) onGoToWarmups else onGoToWorking)
                    .padding(vertical = 13.dp).testTag("live-list-prep-next"),
                style = Body.copy(fontSize = 10.sp), color = WizardColors.textMuted,
            )
        }
    }
}

@Composable
private fun PrepRow(index: String, title: String, volume: String, completed: Boolean, tag: String, valueInSyne: Boolean = false, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 43.dp).drawBehind {
            drawLine(ListRowLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(index, Modifier.width(17.dp), style = Body.copy(fontSize = 9.sp), color = WizardColors.textFaint)
        Text(title, Modifier.weight(1f), style = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = WizardColors.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(volume, style = if (valueInSyne) SyneValue else Body.copy(fontSize = 10.sp),
            color = if (valueInSyne) WizardColors.text else WizardColors.textFaint, maxLines = 1)
        Box(Modifier.size(width = 35.dp, height = 40.dp).clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = if (completed) "Completada: $title" else "Completar $title" }, contentAlignment = Alignment.Center) {
            Icon(if (completed) KpknLineIcons.Check else KpknLineIcons.Plus, null, Modifier.size(16.dp),
                tint = if (completed) WizardColors.text else WizardColors.textMuted)
        }
    }
}

private fun mobilityVolume(mobility: com.example.kpkn.data.models.MobilitySeries): String =
    mobility.reps?.let { "${mobility.sets} × $it" }
        ?: mobility.durationSeconds?.let { "${mobility.sets} × ${it}s" }
        ?: "${mobility.sets} serie(s)"

private fun mobilityDone(uiState: WorkoutUiState, exerciseId: String, mobilityId: String, sets: Int): Boolean =
    exerciseId in uiState.mobilityCompletedExerciseIds ||
        (0 until sets.coerceAtLeast(1)).all { index ->
            WorkoutStepRules.mobilityStepKey(exerciseId, mobilityId, index) in uiState.mobilityCompletedExerciseIds
        }

private fun warmupDone(uiState: WorkoutUiState, exerciseId: String, warmupId: String): Boolean {
    val key = WorkoutStepRules.warmupStepKey(exerciseId, warmupId)
    return key in uiState.warmupCompletedExerciseIds || exerciseId in uiState.warmupCompletedExerciseIds
}

/**
 * `.set-row` (rejilla 25 · tipo · cifra · esfuerzo · registro): la activa con fondo tenue, filete en tinta a la izquierda y el texto en
 * tinta; las omitidas, atenuadas. El cuadro ✓ registra la serie (o, ya registrada, abre sus opciones con el ajuste).
 */
@Composable
private fun SetListRow(row: ListSetRow, active: Boolean, onOpen: () -> Unit, onRegister: () -> Unit) {
    val done = row.completed != null && !row.completed.skipped
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .alpha(if (row.completed?.skipped == true) .42f else 1f)
            .then(if (active) Modifier.background(ListActiveFill) else Modifier)
            .drawBehind {
                drawLine(if (active) Color(0x14FFFFFF) else ListRowLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                if (active) drawLine(WizardColors.text, Offset(1.dp.toPx(), 10.dp.toPx()), Offset(1.dp.toPx(), size.height - 10.dp.toPx()), 2.dp.toPx())
            }
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Serie ${row.label} de ${row.exercise.name}, ${rowType(row.planned)}" }
            .padding(start = if (active) 5.dp else 0.dp, top = 3.dp, bottom = 3.dp)
            .testTag("live-list-row-${row.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(row.label, Modifier.width(25.dp), style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint, textAlign = TextAlign.Center,
            maxLines = 1)
        Text(rowType(row.planned), Modifier.weight(1f), style = Body.copy(fontSize = 10.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
            color = if (active) WizardColors.text else WizardColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rowValue(row), style = SyneValue, color = WizardColors.text, maxLines = 1, softWrap = false)
            row.completed?.partialReps?.takeIf { it > 0 }?.let {
                Text("+ $it parciales", Modifier.padding(top = 3.dp), style = Body.copy(fontSize = 9.sp), color = WizardColors.textFaint)
            }
        }
        Text(performedEffort(row.completed), Modifier.width(46.dp), style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint,
            textAlign = TextAlign.End, maxLines = 1)
        Box(
            Modifier.width(32.dp).heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onRegister)
                .semantics { contentDescription = if (done) "Editar serie ${row.label} de ${row.exercise.name}" else "Registrar serie ${row.label} de ${row.exercise.name}" }
                .testTag("live-list-row-register-${row.key}"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                row.completed?.skipped == true -> Text("—", style = Body.copy(fontSize = 11.sp), color = WizardColors.textFaint)
                row.completed?.isFailedSet == true -> Text("!", style = Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Color(0xFFFFB5AD))
                done -> Icon(KpknLineIcons.Sliders, null, Modifier.size(13.dp), tint = ListDone)
                else -> Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF1A1A1A)).border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(9.dp))
                    .padding(5.dp)) { Icon(KpknLineIcons.Check, null, Modifier.size(14.dp), tint = WizardColors.text) }
            }
        }
    }
}

/**
 * `.inline-editor` del prototipo: la carga en Syne con −/+, la cantidad a la derecha, la referencia y «Parciales / ayuda», el esfuerzo
 * en una fila (RIR 4…0 o RPE 6…10, Fallo y «…» con la escala completa), el estado y «Registrar serie N» a todo el ancho.
 */
@Composable
private fun InlineSetEditor(
    row: ListSetRow,
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
    recordRequest: Int,
    onClose: () -> Unit,
) {
    val savedDraft = viewModel.getSetDraft(row.exercise.id, row.setIndex, row.side)
    val mode = effortModeOf(row.planned)
    var weightText by rememberSaveable(row.key) { mutableStateOf(savedDraft?.weightText ?: initialWeight(row)) }
    var valueText by rememberSaveable(row.key) { mutableStateOf(savedDraft?.valueText ?: initialValue(row)) }
    var effort by rememberSaveable(row.key) {
        mutableStateOf(initialEffortValue(row, mode) ?: savedDraft?.intensityText?.let(::parseDecimal))
    }
    var failure by rememberSaveable(row.key) { mutableStateOf(row.completed?.isFailure == true || savedDraft?.reachedFailure == true) }
    var partials by rememberSaveable(row.key) { mutableIntStateOf(savedDraft?.partialReps ?: row.completed?.partialReps ?: 0) }
    var assisted by rememberSaveable(row.key) { mutableIntStateOf(savedDraft?.assistedReps ?: 0) }
    var rom by rememberSaveable(row.key) { mutableStateOf(savedDraft?.rom) }
    var note by rememberSaveable(row.key) { mutableStateOf(savedDraft?.notes.orEmpty()) }
    var showDetail by remember(row.key) { mutableStateOf(false) }
    var showScale by remember(row.key) { mutableStateOf(false) }
    var busy by remember(row.key) { mutableStateOf(false) }
    var error by remember(row.key) { mutableStateOf<String?>(null) }
    val unit = row.planned.unitModeV2 ?: UnitModeV2.REPS
    val initialRequest = remember(row.key) { recordRequest }

    fun persist() {
        val base = viewModel.getSetDraft(row.exercise.id, row.setIndex, row.side) ?: WorkoutSetDraft()
        viewModel.updateSetDraftFromExercise(
            row.exercise, row.setIndex, row.side,
            base.copy(
                weightText = weightText,
                valueText = valueText,
                intensityText = effort?.let(::formatDecimal),
                reachedFailure = failure,
                partialReps = partials.takeIf { it > 0 },
                assistedReps = assisted.takeIf { it > 0 },
                rom = rom,
                notes = note.ifBlank { null },
                isDirty = true,
            ),
        )
    }

    fun stepWeight(delta: Double) {
        val current = parseDecimal(weightText) ?: (row.planned.weight ?: 0.0)
        weightText = formatDecimal((current + delta).coerceAtLeast(0.0))
        persist()
    }

    fun record() {
        val value = parseDecimal(valueText)
        if (value == null) {
            error = "Completa la cantidad realizada."
            return
        }
        if (!failure && effort == null) {
            error = "Marca el esfuerzo para registrar esta serie."
            return
        }
        val performed = weightOrPlanned(row, weightText)
        val performedEffort = if (failure) (if (mode == IntensityMode.RPE) 10.0 else 0.0) else effort
        busy = true
        error = null
        viewModel.launchWorkoutCommand {
            try {
                val result = viewModel.recordSetV2(
                    weight = performed,
                    value = value,
                    intensity = performedEffort,
                    advanced = SetAdvancedFeedback(
                        rir = performedEffort?.toInt()?.takeIf { mode == IntensityMode.RIR },
                        reachedFailure = failure,
                        actualIntensityMode = if (failure) IntensityMode.FAILURE else mode,
                        actualIntensityValue = performedEffort,
                        intensityAdjusted = true,
                        partialReps = partials.takeIf { it > 0 },
                        assistedReps = assisted.takeIf { it > 0 },
                        rom = rom,
                        notes = note.ifBlank { null },
                    ),
                    loadMode = resolvePersistedLoadModeForSet(
                        exerciseId = row.exercise.id,
                        setIdx = row.setIndex,
                        tagId = uiState.exerciseTags[row.exercise.id],
                        persistedLoadModeBySet = uiState.persistedLoadModeBySet,
                        persistedLoadModeByExercise = uiState.persistedLoadModeByExercise,
                    ) ?: row.planned.loadModeV2,
                    unitMode = unit,
                    bodyWeight = viewModel.currentBodyWeight(),
                    side = row.side,
                    tagId = uiState.exerciseTags[row.exercise.id],
                    setupId = row.planned.setupId,
                    machineBrand = row.planned.machineBrand,
                    setIdxOverride = row.setIndex,
                    expectedExerciseId = row.exercise.id,
                    expectedSetIdx = row.setIndex,
                    expectedSide = row.side,
                    expectedSourceRef = executionRef(uiState, row.exercise, row.setIndex, row.side),
                )
                if (result.succeeded) {
                    onClose()
                } else {
                    error = (result as? RecordSetResult.Rejected)?.reason
                        ?: "No se pudo guardar la serie. Tus valores se conservan."
                }
            } finally {
                busy = false
            }
        }
    }

    // El ✓ de la fila pide registrar: si el acordeón ya estaba abierto, se registra con lo escrito; si no, se abre y avisa qué falta.
    LaunchedEffect(recordRequest) { if (recordRequest > initialRequest && !busy) record() }

    val status = error ?: when {
        row.completed != null -> "Registro existente · actualizar conserva su identidad"
        row.planned.purpose == SetPurpose.CALIBRATOR -> "El ajuste de carga se revisa después del registro"
        effort == null && !failure -> "Elige el esfuerzo que realmente hiciste"
        else -> "Todo listo para registrar"
    }
    val metric = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
        fontFeatureSettings = "'tnum' 1", color = WizardColors.text)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ListEditorFill)
            .drawBehind { drawLine(ListEditorLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(start = 11.dp, end = 11.dp, top = 7.dp, bottom = 5.dp)
            .testTag("live-list-editor-${row.key}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                ListMetricField(weightText, { weightText = it; persist() }, metric.copy(fontSize = 30.sp, lineHeight = 36.sp),
                    Modifier.width(IntrinsicSize.Max), "live-list-weight-${row.key}", "Carga en kg", !busy, placeholder = "—")
                Text("kg", Modifier.padding(start = 4.dp, bottom = 6.dp), style = Body.copy(fontSize = 11.sp), color = WizardColors.textFaint)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MiniStep(KpknLineIcons.Minus, "Reducir carga", !busy, "live-list-weight-minus-${row.key}") { stepWeight(-2.5) }
                MiniStep(KpknLineIcons.Plus, "Aumentar carga", !busy, "live-list-weight-plus-${row.key}") { stepWeight(2.5) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                ListMetricField(valueText, { valueText = it; persist() }, metric.copy(fontSize = 24.sp, lineHeight = 30.sp, textAlign = TextAlign.End),
                    Modifier.width(IntrinsicSize.Max), "live-list-value-${row.key}", "Cantidad realizada", !busy)
                Text(when (unit) { UnitModeV2.TIME -> "s"; UnitModeV2.DISTANCE -> "km"; else -> "reps" }, Modifier.padding(start = 2.dp, bottom = 5.dp),
                    style = Body.copy(fontSize = 11.sp), color = WizardColors.textFaint)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (row.completed != null) recordedSummary(row) else "Plan · ${planSummary(row.planned)}", Modifier.weight(1f),
                style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    partials > 0 && assisted > 0 -> "+$partials parciales · $assisted con ayuda"
                    partials > 0 -> "+$partials parciales"
                    assisted > 0 -> "$assisted con ayuda"
                    else -> "Parciales / ayuda"
                },
                Modifier.heightIn(min = 26.dp).clickable(role = Role.Button) { showDetail = true }.padding(vertical = 5.dp)
                    .testTag("live-list-detail-${row.key}"),
                style = Body.copy(fontSize = 10.sp), color = WizardColors.textMuted,
            )
        }
        // `.quick-effort`: rótulo del modo, cinco valores, Fallo y «…» con la escala completa.
        Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(modeLabel(mode), Modifier.width(25.dp), style = Body.copy(fontSize = 9.sp), color = WizardColors.textFaint)
            effortOptions(mode).forEach { option ->
                val picked = if (option == null) failure else !failure && effort == option
                EffortChip(option?.let(::formatDecimal) ?: "Fallo", picked, !busy,
                    "live-list-effort-${row.key}-${option?.let(::formatDecimal) ?: "failure"}", Modifier.weight(if (option == null) 1.4f else 1f)) {
                    failure = option == null
                    effort = option ?: if (mode == IntensityMode.RPE) 10.0 else 0.0
                    error = null
                    persist()
                }
            }
            EffortChip("…", false, !busy, "live-list-effort-${row.key}-scale", Modifier.weight(1f)) { showScale = true }
        }
        Text(status, Modifier.fillMaxWidth().padding(vertical = 2.dp), style = Body.copy(fontSize = 10.sp),
            color = if (error != null) Color(0xFFFFB5AD) else WizardColors.textFaint, textAlign = TextAlign.Center)
        // `.card-actions` del editor: técnica y opciones de la serie, e historial.
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { viewModel.showSeriesTypeSheet(row.exercise.id, row.setIndex) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(KpknLineIcons.Sliders, null, Modifier.size(15.dp), tint = WizardColors.textMuted)
                Text(" Avanzado", style = Body.copy(fontSize = 10.sp), color = WizardColors.textMuted)
            }
            ListIconButton(KpknLineIcons.History, "Historial del ejercicio", { viewModel.showHistoryForExercise(row.exercise) }, size = 17.dp)
        }
        // `.v2-inline-register`: el registro va en la propia serie.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 5.dp).height(43.dp).clip(RoundedCornerShape(13.dp))
                .background(if (busy) WizardColors.ctaDisabled else WizardColors.cta)
                .clickable(enabled = !busy, role = Role.Button) { record() }
                .testTag("live-list-register-${row.key}"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KpknLineIcons.Check, null, Modifier.size(15.dp), tint = WizardColors.ctaContent)
            Text(
                (if (row.completed != null) "  Actualizar serie " else "  Registrar serie ") + row.label,
                style = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = WizardColors.ctaContent,
            )
        }
    }

    if (showDetail) {
        LiveSetDetailSheet(
            exerciseName = displayWorkoutExerciseName(row.exercise),
            setLabel = row.label,
            completeReps = valueText,
            onCompleteReps = { valueText = it; persist() },
            partials = partials,
            onPartials = { partials = it.coerceAtLeast(0); persist() },
            assisted = assisted,
            onAssisted = { assisted = it.coerceIn(0, valueText.toIntOrNull() ?: it); persist() },
            romOn = rom != null,
            rom = rom,
            onRomToggle = { on -> rom = if (on) (rom ?: 100) else null; persist() },
            onRom = { rom = it; persist() },
            note = note,
            onNote = { note = it; persist() },
            onDismiss = { showDetail = false },
            showCompleteReps = unit == UnitModeV2.REPS,
        )
    }
    if (showScale) {
        val values = if (mode == IntensityMode.RPE) (12..20).map { it / 2.0 } else (10 downTo 0).map { it.toDouble() }
        LiveSheet(title = "Esfuerzo realizado", onDismiss = { showScale = false }, tag = "live-list-effort-sheet") { close ->
            LiveSheetSubtitle("${displayWorkoutExerciseName(row.exercise)} · serie ${row.label}")
            LiveSheetNumberGrid(values.map { it to formatDecimal(it) }, { !failure && effort == it }, { picked ->
                failure = false; effort = picked; error = null; persist(); close()
            }, tagPrefix = "live-list-effort-scale", marked = { plannedEffort(row.planned, mode) == it })
            LiveSheetSecondary("Llegué al fallo", {
                failure = true; effort = if (mode == IntensityMode.RPE) 10.0 else 0.0; error = null; persist(); close()
            })
        }
    }
}

private fun plannedEffort(set: ExerciseSet, mode: IntensityMode): Double? =
    if (mode == IntensityMode.RPE) set.targetRPE else set.targetRIR?.toDouble()

@Composable
private fun ListMetricField(
    value: String,
    onChange: (String) -> Unit,
    style: TextStyle,
    modifier: Modifier,
    tag: String,
    description: String,
    enabled: Boolean,
    placeholder: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = { next -> onChange(next.filter { it.isDigit() || it == ',' || it == '.' }) },
        enabled = enabled,
        modifier = modifier.testTag(tag).semantics { contentDescription = description },
        textStyle = style,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(WizardColors.text),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = WizardColors.textFaint))
                inner()
            }
        },
    )
}

/** `.mini-stepper button`: 32 × 36, radio 10, blanco al 2 %. */
@Composable
private fun MiniStep(icon: ImageVector, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 32.dp, height = 36.dp).clip(RoundedCornerShape(10.dp)).background(ListChipFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(13.dp), tint = WizardColors.text) }
}

/** `.quick-effort button`: 35 dp, radio 9; el elegido, en tinta. */
@Composable
private fun EffortChip(label: String, selected: Boolean, enabled: Boolean, testTag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(35.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) WizardColors.text else ListChipFill)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Body.copy(fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) Color(0xFF111111) else WizardColors.textMuted,
            maxLines = 1,
        )
    }
}

private fun rowType(set: ExerciseSet): String = when {
    set.purpose == SetPurpose.AMRAP -> "AMRAP"
    set.purpose == SetPurpose.CALIBRATOR -> "Calibrador"
    set.advancedTechnique == null -> "Efectiva"
    else -> when (set.advancedTechnique.lowercase(Locale.US)) {
        "drop", "dropset", "drop_set", "drop-set" -> "Drop-set"
        "pause", "restpause", "rest_pause", "rest-pause" -> "Rest-pause"
        "myo", "myoreps", "myo_reps", "myo-reps" -> "Myo-reps"
        else -> set.advancedTechnique
    }
}

/** La cifra de la fila como en el prototipo: «100 × 8» (carga × cantidad), «— × 8» sin carga y la unidad si no son repeticiones. */
private fun rowValue(row: ListSetRow): String {
    val completed = row.completed
    if (completed != null && !completed.skipped) {
        val weight = completed.weight.takeIf { it > 0.0 }?.let(::formatDecimal) ?: "—"
        val value = completed.reps.takeIf { it > 0 }?.toString()
            ?: completed.timeSeconds?.let { "$it s" }
            ?: completed.distanceKm?.let { "${formatDecimal(it)} km" }
            ?: "—"
        return "$weight × $value"
    }
    val set = row.planned
    val weight = set.weight?.takeIf { it > 0.0 }?.let(::formatDecimal) ?: "—"
    val value = when (set.unitModeV2) {
        UnitModeV2.TIME -> set.targetDuration?.let { "$it s" } ?: set.plannedTargetV2?.let { "${formatDecimal(it)} s" }
        UnitModeV2.DISTANCE -> set.plannedTargetV2?.let { "${formatDecimal(it)} km" }
        else -> set.targetReps?.toString() ?: set.plannedTargetV2?.let(::formatDecimal)
    } ?: "—"
    return "$weight × $value"
}

internal fun performedEffort(completed: CompletedSet?): String {
    if (completed == null) return "—"
    if (completed.isFailure) return "Fallo"
    completed.rir?.let { return "RIR $it" }
    completed.rpe?.let { return "RPE ${formatDecimal(it)}" }
    return "—"
}

private fun effortModeOf(set: ExerciseSet): IntensityMode = when (set.intensityMode) {
    IntensityMode.RPE, IntensityMode.RIR -> set.intensityMode
    null -> if (set.targetRPE != null) IntensityMode.RPE else IntensityMode.RIR
    else -> IntensityMode.RIR
}

private fun modeLabel(mode: IntensityMode): String = if (mode == IntensityMode.RPE) "RPE" else "RIR"

/** RIR 4→0 + Fallo; RPE 6→10 + Fallo. `null` representa el fallo muscular. */
internal fun effortOptions(mode: IntensityMode): List<Double?> =
    if (mode == IntensityMode.RPE) listOf(6.0, 7.0, 8.0, 9.0, 10.0, null)
    else listOf(4.0, 3.0, 2.0, 1.0, 0.0, null)

internal fun initialEffortValue(row: ListSetRow, mode: IntensityMode): Double? {
    val completed = row.completed ?: return null
    if (completed.isFailure) return if (mode == IntensityMode.RPE) 10.0 else 0.0
    return completed.rir?.toDouble() ?: completed.rpe
}

private fun initialWeight(row: ListSetRow): String =
    row.completed?.weight?.takeIf { it > 0.0 }?.let(::formatDecimal)
        ?: row.planned.weight?.takeIf { it > 0.0 }?.let(::formatDecimal)
        ?: ""

private fun initialValue(row: ListSetRow): String =
    row.completed?.reps?.takeIf { it > 0 }?.toString()
        ?: row.completed?.timeSeconds?.toString()
        ?: row.completed?.distanceKm?.let(::formatDecimal)
        ?: row.planned.targetReps?.toString()
        ?: row.planned.targetDuration?.toString()
        ?: row.planned.plannedTargetV2?.let(::formatDecimal)
        ?: ""

private fun weightOrPlanned(row: ListSetRow, weightText: String): Double =
    parseDecimal(weightText) ?: row.planned.weight ?: 0.0

private fun planSummary(set: ExerciseSet): String {
    val value = when (set.unitModeV2) {
        UnitModeV2.TIME -> set.targetDuration?.let { "$it s" } ?: set.plannedTargetV2?.let { "${formatDecimal(it)} s" }
        UnitModeV2.DISTANCE -> set.plannedTargetV2?.let { "${formatDecimal(it)} km" } ?: set.targetDuration?.let { "$it s" }
        else -> set.targetReps?.let { "$it reps" } ?: set.plannedTargetV2?.let(::formatDecimal)
    }
    val effort = set.targetRIR?.let { " · RIR $it" } ?: set.targetRPE?.let { " · RPE ${formatDecimal(it)}" } ?: ""
    return when {
        set.weight != null && set.weight > 0.0 && value != null -> "${formatDecimal(set.weight)} × $value$effort"
        set.weight != null && set.weight > 0.0 -> "${formatDecimal(set.weight)} kg$effort"
        value != null -> value + effort
        else -> "Serie"
    }
}

private fun recordedSummary(row: ListSetRow): String {
    val completed = row.completed ?: return "Sin historial"
    val weight = completed.weight.takeIf { it > 0.0 }?.let { "${formatDecimal(it)} × " }.orEmpty()
    val value = completed.reps.takeIf { it > 0 }?.toString()
        ?: completed.timeSeconds?.let { "$it s" }
        ?: completed.distanceKm?.let { "${formatDecimal(it)} km" }
        ?: "—"
    val effort = completed.rir?.let { " · RIR $it" } ?: completed.rpe?.let { " · RPE ${formatDecimal(it)}" } ?: ""
    return weight + value + effort
}

private fun parseDecimal(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()

internal fun formatDecimal(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)
