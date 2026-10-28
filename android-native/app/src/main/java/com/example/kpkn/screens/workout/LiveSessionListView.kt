package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.DropSetData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.kpkn.screens.workout.components.LiveSheetLabeledField
import com.example.kpkn.screens.workout.components.LiveSheetMenuRow
import com.example.kpkn.screens.workout.components.LiveSheetPrimary
import com.example.kpkn.screens.workout.components.LiveSheetSectionLabel
import com.example.kpkn.screens.workout.components.LiveSheetSegmented
import com.example.kpkn.screens.workout.components.LiveSheetShortCopy
import com.example.kpkn.screens.workout.components.LiveSheetTextAction
import com.example.kpkn.screens.workout.components.RestRef02CardClockPanel
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
    /** Descanso de aproximación en marcha (`warmup-rest` del prototipo): segundos que quedan, total y cómo terminarlo. */
    warmupRestRemaining: Int? = null,
    warmupRestTotal: Int? = null,
    warmupRestIsMobility: Boolean = false,
    onEndWarmupRest: () -> Unit = {},
    /** Lo que necesita «Modo visual» dentro del editor: el análisis por serie, cómo abrirlo y los ajustes de vídeo. */
    media: ListEditorMedia = ListEditorMedia(),
    /** Pedido desde «Opciones de la serie» (ejercicio, serie, «advanced» o «details»): abre esa serie con ese panel. */
    panelRequest: Triple<String, Int, String>? = null,
    onPanelRequestConsumed: () -> Unit = {},
    /** Alto de la isla flotante que queda encima (`.list-area { padding-top }`): la lista pasa por debajo al desplazarse. */
    topInset: Dp = 0.dp,
) {
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var recordRequest by remember { mutableIntStateOf(0) }
    // Un toque en ✓ de una fila cerrada la abre y registra a la vez; tocar el esfuerzo abre su escala.
    var autoRecordKey by remember { mutableStateOf<String?>(null) }
    // Panel que debe abrir el editor de una fila: «scale» (esfuerzo), «advanced» o «details».
    var panelOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
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
        if (expandedKey != row.key) {
            open(row)
            autoRecordKey = row.key
        } else {
            recordRequest++
        }
    }
    fun openEffort(row: ListSetRow) {
        if (expandedKey != row.key) open(row)
        panelOpen = row.key to "scale"
    }
    // Tras registrar, el editor salta a la serie siguiente sin registrar (en el orden de la lista), como `adjacent(1)` del prototipo.
    val orderedRows = sections.flatMap { section ->
        when (section) {
            is ListSection.Single -> rowsFor(section.exercise, section.order, uiState.completedSets)
            is ListSection.Superset -> {
                val rounds = section.members.maxOfOrNull { it.first.sets.count { set -> !set.isEmptySlot } } ?: 0
                (0 until rounds).flatMap { round ->
                    section.members.flatMap { (member, order) ->
                        val set = member.sets.getOrNull(round)
                        if (set == null || set.isEmptySlot) emptyList()
                        else rowsForSet(member, order, round, set, uiState.completedSets, "${round + 1}")
                    }
                }
            }
        }
    }
    fun afterRecord(recorded: ListSetRow) {
        val index = orderedRows.indexOfFirst { it.key == recorded.key }
        val next = orderedRows.drop(index + 1).firstOrNull { it.completed == null }
        expandedKey = next?.key
        if (next != null) viewModel.selectWorkoutStep(WorkoutStepRules.workingStepKey(next.exercise.id, next.setIndex, next.side))
    }
    LaunchedEffect(panelRequest) {
        val request = panelRequest ?: return@LaunchedEffect
        onPanelRequestConsumed()
        val target = orderedRows.firstOrNull { it.exercise.id == request.first && it.setIndex == request.second } ?: return@LaunchedEffect
        expandedKey = target.key
        panelOpen = target.key to request.third
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("live-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = topInset, bottom = 28.dp),
    ) {
        // `.list-area > .context`: el ejercicio en curso encabeza la lista y se va con ella al desplazarse.
        item(key = "context") {
            Box(Modifier.fillMaxWidth().padding(bottom = 13.dp).drawBehind {
                drawLine(Color(0x10FFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }.padding(bottom = 13.dp)) { contextHeader() }
        }
        val current = exercises.firstOrNull { it.id == currentExerciseId }
        val prepLeft = current != null && (
            current.mobilitySeries.any { !mobilityDone(uiState, current.id, it.id, it.sets) } ||
                current.warmupSets.any { !warmupDone(uiState, current.id, it.id) }
            )
        // El prototipo enseña la preparación solo mientras dura su fase (movilidad o aproximaciones), no durante las series.
        val stepKey = uiState.activeStepKey
        val inPrepPhase = current != null && (stepKey == null || current.warmupSets.any { stepKey == WorkoutStepRules.warmupStepKey(current.id, it.id) } ||
            current.mobilitySeries.any { stepKey.startsWith(WorkoutStepRules.mobilityStepKey(current.id, it.id)) })
        val prepPending = prepLeft && inPrepPhase
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
                        // «Ir a efectivas» conserva la serie elegida si sigue pendiente; si no, va a la primera pendiente.
                        val pending = rowsFor(current, 0, uiState.completedSets).filter { it.completed == null }
                        val first = pending.firstOrNull { it.setIndex == uiState.currentSetIdx } ?: pending.firstOrNull()
                        if (first != null) viewModel.selectWorkoutStep(WorkoutStepRules.workingStepKey(current.id, first.setIndex, first.side))
                    },
                )
            }
        }
        // `warmup-rest` del prototipo: «AHORA · Descanso» con la tarjeta del descanso de aproximación dentro de la lista.
        if (warmupRestRemaining != null && current != null) {
            item(key = "warmup-rest") {
                ListWarmupRest(
                    remainingSeconds = warmupRestRemaining.coerceAtLeast(0),
                    totalSeconds = (warmupRestTotal ?: 60).coerceAtLeast(1),
                    kicker = if (warmupRestIsMobility) "DESCANSO · MOVILIDAD" else "DESCANSO · APROX.",
                    next = if (warmupRestIsMobility) null else current.warmupSets.firstOrNull { !warmupDone(uiState, current.id, it.id) }?.let { warmup ->
                        val load = (warmupWorkingWeight ?: 0.0) * warmup.percentageOfWorkingWeight / 100.0
                        "A${current.warmupSets.indexOf(warmup) + 1}" to (if (load > 0.0) "${formatDecimal(load)} kg × ${warmup.targetReps}" else "× ${warmup.targetReps}")
                    },
                    onEnd = onEndWarmupRest,
                )
            }
        }
        item(key = "intro") {
            ListIntro(if (current != null && prepPending) "SERIES EFECTIVAS" else "REGISTRO DE LA SESIÓN", "$workingDone / $workingTotal")
        }
        sections.forEach { section ->
            when (section) {
                is ListSection.Single -> if (isCardioExercise(section.exercise)) {
                    // `c-list`: el cardio es un bloque propio con su perfil y «Empezar cardio»; no se descompone en filas de series.
                    val exercise = section.exercise
                    item(key = "cardio-${exercise.id}") {
                        val done = rowsFor(exercise, section.order, uiState.completedSets).any { it.completed != null }
                        ListCardioBlock(exercise, done) {
                            viewModel.selectExercise(section.order)
                            viewModel.setLiveSessionViewPreference(LiveSessionView.CARDS.name)
                        }
                    }
                } else {
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
                    val warmupsDone = exercise.warmupSets.count { warmupDone(uiState, exercise.id, it.id) }
                    if (isCollapsed) {
                        item(key = "collapsed-${exercise.id}") {
                            Text("${rows.count { it.completed != null } + warmupsDone} registros · tocar para desplegar",
                                Modifier.fillMaxWidth().padding(vertical = 8.dp), style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint)
                        }
                    } else {
                        // Las aproximaciones son filas del ejercicio (A1, A2) junto a las efectivas, también tras la preparación.
                        exercise.warmupSets.forEachIndexed { index, warmup ->
                            item(key = "warm-${exercise.id}-${warmup.id}") {
                                val done = warmupDone(uiState, exercise.id, warmup.id)
                                val load = (warmupWorkingWeight?.takeIf { exercise.id == currentExerciseId } ?: exercise.sets.firstOrNull { !it.isEmptySlot }?.weight ?: 0.0) *
                                    warmup.percentageOfWorkingWeight / 100.0
                                WarmListRow(
                                    label = "A${index + 1}",
                                    percent = "${formatDecimal(warmup.percentageOfWorkingWeight)} %",
                                    dose = if (load > 0.0) "${formatDecimal(load)} × ${warmup.targetReps}" else "— × ${warmup.targetReps}",
                                    done = done,
                                    tag = "live-list-warm-row-${exercise.id}-${warmup.id}",
                                    onToggle = { viewModel.markWarmupComplete(exercise.id, warmup.id, !done) },
                                )
                            }
                        }
                        rows.forEach { row ->
                            item(key = row.key) {
                                ListRowWithEditor(row, expandedKey == row.key, isActive(row, uiState, currentExerciseId), uiState, viewModel,
                                    recordRequest, onOpen = { open(row) }, onRegister = { register(row) }, onClose = { expandedKey = null },
                                    onEffort = { openEffort(row) }, onRecorded = { afterRecord(row) },
                                    autoRecord = autoRecordKey == row.key, onAutoRecordConsumed = { autoRecordKey = null },
                                    openPanel = panelOpen?.takeIf { it.first == row.key }?.second, onPanelConsumed = { panelOpen = null },
                                    media = media)
                            }
                        }
                    }
                }
                is ListSection.Superset -> {
                    val letter = ('A' + sections.filterIsInstance<ListSection.Superset>().indexOf(section).coerceAtLeast(0)).toString()
                    item(key = "sg-${section.groupId}") {
                        SupersetHeader(section.members.map { it.first }, letter, onSettings = { onOpenSupersetSettings(section.groupId) })
                    }
                    val roundCount = section.members.maxOfOrNull { it.first.sets.count { set -> !set.isEmptySlot } } ?: 0
                    for (round in 0 until roundCount) {
                        item(key = "round-${section.groupId}-$round") { RoundLabel(round, section.groupId) }
                        section.members.forEachIndexed { memberIdx, (member, order) ->
                            val set = member.sets.getOrNull(round) ?: return@forEachIndexed
                            if (set.isEmptySlot) return@forEachIndexed
                            val rows = rowsForSet(member, order, round, set, uiState.completedSets, "${round + 1}")
                            item(key = "sm-${member.id}-$round") {
                                SupersetMemberName("$letter${memberIdx + 1}", displayWorkoutExerciseName(member), first = memberIdx == 0)
                            }
                            rows.forEach { row ->
                                item(key = row.key) {
                                    // `.v2-superset` (filete crema de 2 y 13 de margen) + `.v2-super-exercise` (filete de 1 y 10 de sangría).
                                    Box(Modifier.supersetRail(inner = false).padding(start = 13.dp).supersetRail(inner = true).padding(start = 10.dp)) {
                                        ListRowWithEditor(row, expandedKey == row.key, isActive(row, uiState, currentExerciseId), uiState, viewModel,
                                            recordRequest, onOpen = { open(row) }, onRegister = { register(row) }, onClose = { expandedKey = null },
                                            onEffort = { openEffort(row) }, onRecorded = { afterRecord(row) },
                                            autoRecord = autoRecordKey == row.key, onAutoRecordConsumed = { autoRecordKey = null },
                                            openPanel = panelOpen?.takeIf { it.first == row.key }?.second, onPanelConsumed = { panelOpen = null },
                                            supersetColumns = true, media = media)
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
    onEffort: () -> Unit = onOpen,
    onRecorded: () -> Unit = onClose,
    autoRecord: Boolean = false,
    onAutoRecordConsumed: () -> Unit = {},
    openPanel: String? = null,
    onPanelConsumed: () -> Unit = {},
    supersetColumns: Boolean = false,
    media: ListEditorMedia = ListEditorMedia(),
) {
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        SetListRow(row, active = expanded || isCurrent, onOpen = onOpen, onRegister = onRegister, onEffort = onEffort, supersetColumns = supersetColumns)
        if (expanded) InlineSetEditor(row, uiState, viewModel, recordRequest, onRecorded, autoRecord, onAutoRecordConsumed, openPanel, onPanelConsumed,
            media)
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
        ListIconButton(KpknLineIcons.More, "Opciones de ${displayWorkoutExerciseName(exercise)}", onOptions, width = 30.dp, size = 18.dp)
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
@OptIn(ExperimentalLayoutApi::class)
private fun SupersetHeader(members: List<Exercise>, letter: String, onSettings: () -> Unit) {
    val rounds = members.maxOfOrNull { it.sets.count { set -> !set.isEmptySlot } } ?: 0
    val between = members.firstNotNullOfOrNull { it.supersetRestBetween ?: it.restTime }
    val after = members.firstNotNullOfOrNull { it.supersetRestAfter }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).supersetRail(inner = false).padding(start = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SUPERSERIE $letter", style = Body.copy(fontSize = 9.sp, letterSpacing = 1.2.sp), color = WizardColors.textFaint)
                Text(members.joinToString(" + ", transform = ::displayWorkoutExerciseName), Modifier.padding(top = 8.dp),
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp),
                    color = WizardColors.text)
            }
            ListIconButton(KpknLineIcons.Sliders, "Configurar superserie", onSettings)
        }
        FlowRow(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        modifier = Modifier.fillMaxWidth().supersetRail(inner = false).padding(start = 15.dp, top = 8.dp, bottom = 10.dp)
            .testTag("live-list-round-$groupId-$round"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("RONDA ${round + 1}", style = Body.copy(fontSize = 9.sp, letterSpacing = .8.sp), color = WizardColors.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(Color(0x16FFFFFF)))
    }
}

@Composable
private fun SupersetMemberName(badge: String, name: String, first: Boolean) {
    Row(Modifier.fillMaxWidth().supersetRail(inner = false).padding(start = 13.dp).supersetRail(inner = true)
        .padding(start = 10.dp, top = if (first) 0.dp else 9.dp),
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
    var movementSheet by remember { mutableStateOf<com.example.kpkn.data.models.MobilitySeries?>(null) }
    var jointsSheet by remember { mutableStateOf(false) }
    var loadSheet by remember { mutableStateOf(false) }
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
                    "live-list-mobility-${mobility.id}", onTitle = { movementSheet = mobility }) { onToggleMobility(mobility.id, mobility.sets, !completed) }
            }
        } else {
            exercise.warmupSets.forEachIndexed { index, warmup ->
                val completed = warmupDone(uiState, exercise.id, warmup.id)
                val load = (workingWeight ?: 0.0) * warmup.percentageOfWorkingWeight / 100.0
                WarmListRow("A${index + 1}", "${formatDecimal(warmup.percentageOfWorkingWeight)} %",
                    if (load > 0.0) "${formatDecimal(load)} × ${warmup.targetReps}" else "× ${warmup.targetReps}", completed,
                    "live-list-warmup-${warmup.id}") { onToggleWarmup(warmup.id, !completed) }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            // A la izquierda: «Articulaciones» (movilidad) o «Referencia · 100 kg» (aproximaciones), cada uno con su hoja.
            Text(
                when {
                    showMobility -> "Articulaciones"
                    workingWeight != null && workingWeight > 0.0 -> "Referencia · ${formatDecimal(workingWeight)} kg"
                    else -> ""
                },
                Modifier.heightIn(min = 42.dp).clickable(enabled = showMobility || (workingWeight ?: 0.0) > 0.0, role = Role.Button) {
                    if (showMobility) jointsSheet = true else loadSheet = true
                }.padding(vertical = 13.dp).testTag("live-list-prep-left"),
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
    movementSheet?.let { mobility ->
        val completed = mobilityDone(uiState, exercise.id, mobility.id, mobility.sets)
        LiveSheet(title = mobility.name, onDismiss = { movementSheet = null }, tag = "live-list-movement-sheet", footer = { close ->
            LiveSheetPrimary(if (completed) "Marcar pendiente" else "Marcar completado", {
                onToggleMobility(mobility.id, mobility.sets, !completed); close()
            })
        }) {
            LiveSheetSubtitle("Explora el movimiento a tu ritmo antes de añadir carga.")
            Text(mobilityVolume(mobility), style = Body.copy(fontSize = 12.sp), color = WizardColors.text)
            mobility.notes?.takeIf { it.isNotBlank() }?.let { LiveSheetShortCopy(it) }
            if (mobility.bodyZones.isNotEmpty()) {
                LiveSheetSectionLabel("Articulaciones")
                Text(mobility.bodyZones.joinToString(" · "), style = Body.copy(fontSize = 12.sp), color = WizardColors.textMuted)
            }
        }
    }
    if (jointsSheet) {
        val zones = exercise.mobilitySeries.flatMap { it.bodyZones }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        LiveSheet(title = "Articulaciones", onDismiss = { jointsSheet = false }, tag = "live-list-joints-sheet") {
            LiveSheetSubtitle("Articulaciones del movimiento.")
            if (zones.isEmpty()) LiveSheetShortCopy("Este bloque no indica articulaciones.")
            zones.forEach { zone ->
                LiveSheetMenuRow(zone.replaceFirstChar { it.uppercase() } + " · " +
                    exercise.mobilitySeries.filter { m -> m.bodyZones.any { it.trim() == zone } }.joinToString(", ") { it.name }, {}, icon = null, chevron = false)
            }
        }
    }
    if (loadSheet && workingWeight != null && workingWeight > 0.0) {
        // `warmup-load`: la carga de trabajo y lo que pide cada aproximación (el porcentaje se calcula sobre la primera serie efectiva).
        LiveSheet(title = "Carga de referencia de trabajo", onDismiss = { loadSheet = false }, tag = "live-list-load-sheet") {
            LiveSheetSubtitle("Trabajo · ${formatDecimal(workingWeight)} kg. Cambia la carga de la primera serie efectiva para recalcular las aproximaciones.")
            exercise.warmupSets.forEachIndexed { index, warmup ->
                LiveSheetMenuRow("A${index + 1} · ${formatDecimal(warmup.percentageOfWorkingWeight)} %", {}, icon = null, chevron = false,
                    small = "${formatDecimal(workingWeight * warmup.percentageOfWorkingWeight / 100.0)} kg × ${warmup.targetReps}")
            }
        }
    }
}

/** `.v2-warm-row`: alto 47, columnas 24 · 46 · cifra · 36; «A1» 10, «20 %» 11 atenuado, «20 × 10» en Syne 16 y la marca. */
@Composable
private fun WarmListRow(label: String, percent: String, dose: String, done: Boolean, tag: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 47.dp).drawBehind {
            drawLine(Color(0x10FFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(24.dp), style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint)
        Text(percent, Modifier.width(46.dp), style = Body.copy(fontSize = 11.sp), color = WizardColors.textMuted)
        Text(dose, Modifier.weight(1f), style = SyneValue.copy(fontSize = 16.sp, lineHeight = 20.sp),
            color = if (done) WizardColors.textMuted else WizardColors.text, textAlign = TextAlign.End)
        Box(Modifier.size(width = 36.dp, height = 41.dp).clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = if (done) "Desmarcar aproximación $label" else "Completar aproximación $label" },
            contentAlignment = Alignment.Center) {
            Icon(if (done) KpknLineIcons.Check else KpknLineIcons.Plus, null, Modifier.size(16.dp),
                tint = if (done) WizardColors.text else WizardColors.textMuted)
        }
    }
}

/** El descanso de aproximación dentro de la lista: «AHORA · Descanso», la tarjeta del descanso y «Terminar descanso». */
@Composable
private fun ListWarmupRest(remainingSeconds: Int, totalSeconds: Int, kicker: String, next: Pair<String, String>?, onEnd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("live-list-warmup-rest")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 45.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AHORA", Modifier.width(52.dp), style = Body.copy(fontSize = 9.sp, letterSpacing = .8.sp), color = WizardColors.textFaint)
            Text("Descanso", style = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
        }
        Box(Modifier.fillMaxWidth().height(394.dp)) {
            com.example.kpkn.screens.workout.components.RestRef02Card(
                compact = true,
                footer = {
                    com.example.kpkn.screens.workout.components.RestRef02Button("Terminar descanso", onEnd, primary = true,
                        modifier = Modifier.fillMaxWidth(), tag = "live-list-rest-end")
                },
            ) {
                RestRef02CardClockPanel(
                    compact = true,
                    kicker = kicker,
                    remainingSeconds = remainingSeconds,
                    progress = (1f - remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f),
                    isPaused = false,
                    animationsOn = true,
                    onExpand = null,
                    onDecrease = {},
                    onTogglePause = {},
                    onIncrease = {},
                    nextTitle = next?.first,
                    nextDose = next?.second,
                    suggestion = null,
                    onOpenSuggestion = null,
                    pauseEnabled = false,
                    decreaseEnabled = false,
                    increaseEnabled = false,
                )
            }
        }
    }
}

@Composable
private fun PrepRow(index: String, title: String, volume: String, completed: Boolean, tag: String, valueInSyne: Boolean = false,
    onTitle: (() -> Unit)? = null, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 43.dp).drawBehind {
            drawLine(ListRowLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(index, Modifier.width(15.dp), style = Body.copy(fontSize = 9.sp), color = WizardColors.textFaint)
        Text(title, Modifier.weight(1f).then(if (onTitle != null) Modifier.clickable(role = Role.Button, onClick = onTitle) else Modifier),
            style = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = WizardColors.text)
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
private fun SetListRow(
    row: ListSetRow,
    active: Boolean,
    onOpen: () -> Unit,
    onRegister: () -> Unit,
    onEffort: () -> Unit = onOpen,
    supersetColumns: Boolean = false,
) {
    val done = row.completed != null && !row.completed.skipped
    // Columnas del prototipo: 25 · tipo · 91 · 47 · 24 (en superserie, 18 · tipo · 87 · 43 · 30).
    val indexWidth = if (supersetColumns) 18.dp else 25.dp
    val valueWidth = if (supersetColumns) 87.dp else 91.dp
    val effortWidth = if (supersetColumns) 43.dp else 47.dp
    val registerWidth = if (supersetColumns) 30.dp else 24.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .alpha(if (row.completed?.skipped == true) .42f else 1f)
            .drawBehind {
                // `.set-row.active`: el fondo sobresale 5 a cada lado (margin −5 / padding 5) y la barra en tinta va en su borde.
                val bleed = 5.dp.toPx()
                if (active) drawRect(ListActiveFill, Offset(-bleed, 0f), androidx.compose.ui.geometry.Size(size.width + bleed * 2, size.height))
                drawLine(if (active) Color(0x14FFFFFF) else ListRowLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                if (active) drawLine(WizardColors.text, Offset(-bleed + 1.dp.toPx(), 10.dp.toPx()),
                    Offset(-bleed + 1.dp.toPx(), size.height - 10.dp.toPx()), 2.dp.toPx())
            }
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Serie ${row.label} de ${row.exercise.name}, ${rowType(row.planned)}" }
            .padding(top = 3.dp, bottom = 3.dp)
            .testTag("live-list-row-${row.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(row.label, Modifier.width(indexWidth), style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint, textAlign = TextAlign.Center)
        Text(rowType(row.planned), Modifier.weight(1f), style = Body.copy(fontSize = 10.sp),
            color = if (active) WizardColors.text else WizardColors.textMuted)
        Column(Modifier.width(valueWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rowValue(row), style = SyneValue, color = WizardColors.text)
            row.completed?.partialReps?.takeIf { it > 0 }?.let {
                Text("+ $it parciales", Modifier.padding(top = 3.dp), style = Body.copy(fontSize = 9.sp), color = WizardColors.textFaint)
            }
        }
        // `row-effort` es un botón: abre «Esfuerzo realizado» de esa serie.
        Text(performedEffort(row.completed), Modifier.width(effortWidth).heightIn(min = 36.dp).clickable(role = Role.Button, onClick = onEffort)
            .wrapContentHeight(Alignment.CenterVertically).testTag("live-list-row-effort-${row.key}"),
            style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint, textAlign = TextAlign.End)
        Box(
            Modifier.width(registerWidth).heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onRegister)
                .semantics { contentDescription = if (done) "Editar serie ${row.label} de ${row.exercise.name}" else "Registrar serie ${row.label} de ${row.exercise.name}" }
                .testTag("live-list-row-register-${row.key}"),
            contentAlignment = Alignment.Center,
        ) {
            // Con el registro en cada serie, todas las efectivas llevan su botón: ✓ (también omitidas o con incidencia) o el ajuste si ya está.
            when {
                done && row.completed?.isFailedSet != true -> Icon(KpknLineIcons.Sliders, null, Modifier.size(13.dp), tint = ListDone)
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
    autoRecord: Boolean = false,
    onAutoRecordConsumed: () -> Unit = {},
    openPanel: String? = null,
    onPanelConsumed: () -> Unit = {},
    media: ListEditorMedia = ListEditorMedia(),
) {
    val savedDraft = viewModel.getSetDraft(row.exercise.id, row.setIndex, row.side)
    // La escala puede cambiarse entre RIR y RPE en «Esfuerzo realizado».
    var mode by rememberSaveable(row.key) { mutableStateOf(effortModeOf(row.planned)) }
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
    // `state.face` del prototipo en la Lista: el editor muestra el registro, «Avanzado» o «Modo visual» en el mismo hueco.
    var face by rememberSaveable(row.key) { mutableStateOf(EDITOR_FACE_FRONT) }
    var drops by remember(row.key) { mutableStateOf<List<DropSetData>>(emptyList()) }
    var pauses by remember(row.key) { mutableStateOf<List<RestPauseData>>(emptyList()) }
    var failedSet by remember(row.key) { mutableStateOf(row.completed?.isFailedSet == true) }
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
        if (value == null || value < 0.0) {
            error = "Completa la cantidad realizada."
            return
        }
        if (weightText.isNotBlank() && parseDecimal(weightText)?.takeIf { it >= 0.0 } == null) {
            error = "Revisa la carga: escribe un número válido."
            return
        }
        if (!failure && !failedSet && effort == null) {
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
                        isFailedSet = failedSet,
                        dropSets = drops,
                        restPauses = pauses,
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
    // Abierta por el ✓ de la fila: registra en el acto (si falta el esfuerzo, lo pide); abierta por el esfuerzo: muestra la escala.
    LaunchedEffect(autoRecord) { if (autoRecord) { onAutoRecordConsumed(); if (!busy) record() } }
    LaunchedEffect(openPanel) {
        when (openPanel) {
            "scale" -> showScale = true
            "advanced" -> face = EDITOR_FACE_ADVANCED
            "details" -> showDetail = true
        }
        if (openPanel != null) onPanelConsumed()
    }
    val history = remember(row.key, uiState.exerciseTags[row.exercise.id]) {
        viewModel.getGhostForSet(row.exercise.id, row.setIndex, row.exercise.exerciseDbId ?: row.exercise.exerciseId,
            uiState.exerciseTags[row.exercise.id])
    }

    val status = error ?: when {
        row.completed != null -> "Registro existente · actualizar conserva su identidad"
        row.planned.purpose == SetPurpose.CALIBRATOR -> "El ajuste de carga se revisa después del registro"
        effort == null && !failure -> "Elige el esfuerzo que realmente hiciste"
        else -> "Todo listo para registrar"
    }
    val metric = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp,
        fontFeatureSettings = "'tnum' 1", color = WizardColors.text)

    if (face == EDITOR_FACE_ADVANCED) {
        // `inline-editor` con `advancedContent()`: la misma cara «Avanzado» de la tarjeta, dentro de la fila.
        Box(Modifier.fillMaxWidth().height(440.dp).padding(vertical = 6.dp).testTag("live-list-advanced-${row.key}")) {
            com.example.kpkn.screens.workout.components.SetCardTechniqueBack(
                currentSet = row.planned,
                coverWeightLabel = if (weightText.isBlank()) "—" else "$weightText kg",
                coverValueLabel = if (valueText.isBlank()) "—" else "$valueText ${unitLabel(unit)}",
                coverIntensityLabel = effort?.let { "${modeLabel(mode)} ${formatDecimal(it)}" },
                mainWeight = parseDecimal(weightText) ?: 0.0,
                mainReps = parseDecimal(valueText)?.toInt() ?: 0,
                sessionAccentColor = WizardColors.text,
                isFailedSet = failedSet,
                onFailedSetChange = { failed -> failedSet = failed; if (failed) failure = false },
                onFlipBack = { face = EDITOR_FACE_FRONT },
                onCompleteTechniques = { d, p -> drops = d; pauses = p; face = EDITOR_FACE_FRONT },
                noteText = note,
                onNoteChange = { note = it; persist() },
                initialDropEnabled = drops.isNotEmpty(),
                initialRestPauseEnabled = pauses.isNotEmpty(),
                setNumber = row.setIndex + 1,
                loadMode = row.planned.loadModeV2 ?: com.example.kpkn.data.models.LoadModeV2.LOAD,
            )
        }
    } else if (face == EDITOR_FACE_VISUAL) {
        // `inline-editor` con `mediaContent()` a 350 de alto: la cámara, el álbum y el análisis de esa serie.
        Box(Modifier.fillMaxWidth().height(350.dp).padding(vertical = 6.dp).testTag("live-list-visual-${row.key}")) {
            com.example.kpkn.screens.workout.components.SetCardExerciseMediaBack(
                exercise = row.exercise,
                onFlipBack = { face = EDITOR_FACE_FRONT },
                setIndex = row.setIndex,
                side = row.side,
                weightKg = parseDecimal(weightText)?.takeIf { it > 0.0 },
                reps = parseDecimal(valueText)?.toInt()?.takeIf { it > 0 },
                mediaCapture = viewModel.mediaCapture,
                effortLabel = effort?.let { "${modeLabel(mode)} ${formatDecimal(it)}" },
                onShowHistory = { viewModel.showHistoryForExercise(row.exercise) },
                onOpenAnalysis = media.onOpenAnalysis,
                videoAnalysis = media.videoAnalysisBySetKey[workoutSetKey(row.exercise.id, row.setIndex,
                    com.example.kpkn.data.media.WorkoutMediaSides.normalize(row.side, row.exercise))],
                onRetryAnalysis = media.onRetryAnalysis,
                techniqueVideoSettings = media.techniqueVideoSettings,
            )
        }
    } else Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ListEditorFill)
            .drawBehind { drawLine(ListEditorLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 5.dp)
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
                    Modifier.width(48.dp), "live-list-value-${row.key}", "Cantidad realizada", !busy)
                Text(when (unit) { UnitModeV2.TIME -> "s"; UnitModeV2.DISTANCE -> "km"; else -> "reps" }, Modifier.padding(start = 2.dp, bottom = 5.dp),
                    style = Body.copy(fontSize = 11.sp), color = WizardColors.textFaint)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            // La referencia del prototipo: el historial anterior de esa serie, o «Sin historial».
            Text(
                history?.let { previous ->
                    buildString {
                        if (previous.weight > 0.0) append("${formatDecimal(previous.weight)} × ")
                        append(previous.reps)
                        previous.rir?.let { append(" · RIR $it") } ?: previous.rpe?.let { append(" · RPE ${formatDecimal(it)}") }
                    }
                } ?: "Sin historial",
                Modifier.weight(1f).testTag("live-list-reference-${row.key}"),
                style = Body.copy(fontSize = 10.sp), color = WizardColors.textFaint,
            )
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
                    "live-list-effort-${row.key}-${option?.let(::formatDecimal) ?: "failure"}", Modifier.weight(1f)) {
                    failure = option == null
                    effort = option ?: if (mode == IntensityMode.RPE) 10.0 else 0.0
                    error = null
                    persist()
                }
            }
            EffortChip("…", false, !busy, "live-list-effort-${row.key}-scale", Modifier.weight(1f)) { showScale = true }
        }
        Text(status, Modifier.fillMaxWidth().padding(vertical = 2.dp), style = Body.copy(fontSize = 10.sp),
            color = if (error != null) Color(0xFFFF9B92) else WizardColors.textFaint, textAlign = TextAlign.Center)
        // `.card-actions` del editor: técnica y opciones de la serie, e historial.
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { face = EDITOR_FACE_ADVANCED }
                .testTag("live-list-open-advanced-${row.key}"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(KpknLineIcons.Sliders, null, Modifier.size(15.dp), tint = WizardColors.textMuted)
                Text(" Avanzado", style = Body.copy(fontSize = 10.sp), color = WizardColors.textMuted)
            }
            Row(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { face = EDITOR_FACE_VISUAL }
                .testTag("live-list-open-visual-${row.key}"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(KpknLineIcons.Camera, null, Modifier.size(16.dp), tint = WizardColors.textMuted)
                Text(" Modo visual", style = Body.copy(fontSize = 10.sp), color = WizardColors.textMuted)
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
        // «Esfuerzo realizado» del prototipo: RIR o RPE, la rejilla completa, «Otro valor…», «Llegué al fallo» y «Dejar sin informar».
        // Elegir un valor no cierra la hoja.
        val values = if (mode == IntensityMode.RPE) {
            (0..5).map { it.toDouble() } + (12..20).map { it / 2.0 }
        } else (10 downTo 0).map { it.toDouble() }
        var custom by remember(row.key, mode) { mutableStateOf("") }
        LiveSheet(title = "Esfuerzo realizado", onDismiss = { showScale = false }, tag = "live-list-effort-sheet") {
            LiveSheetSubtitle("El objetivo del plan es una referencia. Selecciona lo que hiciste.")
            LiveSheetSegmented(listOf(IntensityMode.RIR to "RIR", IntensityMode.RPE to "RPE"), mode, { picked ->
                if (picked != mode) { mode = picked; effort = null; failure = false; persist() }
            })
            LiveSheetNumberGrid(values.map { it to formatDecimal(it) }, { !failure && effort == it }, { picked ->
                failure = false; effort = picked; error = null; persist()
            }, tagPrefix = "live-list-effort-scale", marked = { plannedEffort(row.planned, mode) == it })
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom) {
                LiveSheetLabeledField("Otro valor…", custom, { custom = it }, Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                    tag = "live-list-effort-custom")
                LiveSheetSecondary("Aplicar valor", {
                    val parsed = parseDecimal(custom)
                    val max = 10.0
                    if (parsed != null && parsed >= 0.0 && parsed <= max) {
                        failure = false; effort = parsed; error = null; persist()
                    } else {
                        error = "Escribe un valor de 0 a 10."
                    }
                }, Modifier.weight(1f), tag = "live-list-effort-apply")
            }
            LiveSheetSecondary("Llegué al fallo", {
                failure = true; effort = if (mode == IntensityMode.RPE) 10.0 else 0.0; error = null; persist()
            }, Modifier.padding(top = 10.dp))
            LiveSheetTextAction("Dejar sin informar", {
                failure = false; effort = null; persist()
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

private const val EDITOR_FACE_FRONT = "front"
private const val EDITOR_FACE_ADVANCED = "advanced"
private const val EDITOR_FACE_VISUAL = "visual"

private fun unitLabel(unit: UnitModeV2): String = when (unit) {
    UnitModeV2.TIME -> "s"
    UnitModeV2.DISTANCE -> "km"
    else -> "reps"
}

/** Lo que el anfitrión pasa a «Modo visual» del editor en línea (lo mismo que recibe la tarjeta). */
internal data class ListEditorMedia(
    val videoAnalysisBySetKey: Map<String, SetVideoAnalysisUi> = emptyMap(),
    val onOpenAnalysis: ((String) -> Unit)? = null,
    val onRetryAnalysis: ((String) -> Unit)? = null,
    val techniqueVideoSettings: com.example.kpkn.data.models.TechniqueVideoSettings = com.example.kpkn.data.models.TechniqueVideoSettings(),
)

private fun isCardioExercise(exercise: Exercise): Boolean = cardioDetailsOrNull(exercise) != null

/** El bloque de cardio en Lista: icono, nombre en Syne, «Cardio · 40 min · 9 pasos · Z2 → Z4», el perfil y el botón a su espacio. */
@Composable
private fun ListCardioBlock(exercise: Exercise, done: Boolean, onOpen: () -> Unit) {
    val details = cardioDetailsOrNull(exercise) ?: return
    val steps = remember(details) { cardioSteps(details) }
    val zones = steps.map { it.zone }
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF111111))
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(20.dp)).padding(14.dp).testTag("live-list-cardio-${exercise.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x0BFFFFFF)), contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.Run, null, Modifier.size(20.dp), tint = WizardColors.text)
            }
            Column(Modifier.weight(1f)) {
                Text(displayWorkoutExerciseName(exercise), style = SyneValue.copy(fontSize = 14.sp, lineHeight = 19.sp), color = WizardColors.text)
                Text(
                    listOfNotNull(
                        "Cardio",
                        "${details.effectiveDurationSeconds() / 60} min".takeIf { details.effectiveDurationSeconds() > 0 },
                        "${steps.size} ${if (steps.size == 1) "paso" else "pasos"}",
                        if (zones.isNotEmpty()) "Z${zones.minOrNull()} → Z${zones.maxOrNull()}" else null,
                    ).joinToString(" · "),
                    Modifier.padding(top = 3.dp), style = Body.copy(fontSize = 10.5.sp), color = WizardColors.textFaint,
                )
            }
        }
        CardioProfile(steps, -1, 0f, Modifier.padding(top = 12.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 11.dp).height(43.dp).clip(RoundedCornerShape(13.dp))
                .background(if (done) Color(0x14FFFFFF) else WizardColors.cta)
                .clickable(role = Role.Button, onClick = onOpen).testTag("live-list-cardio-open-${exercise.id}"),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (done) KpknLineIcons.Check else KpknLineIcons.Play, null, Modifier.size(14.dp), tint = if (done) WizardColors.text else WizardColors.ctaContent)
            Text(if (done) "  Cardio registrado · abrir" else "  Empezar cardio", style = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = if (done) WizardColors.text else WizardColors.ctaContent)
        }
    }
}
