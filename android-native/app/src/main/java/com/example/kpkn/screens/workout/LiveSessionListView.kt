package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.DropSetData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import com.example.kpkn.screens.workout.components.liveContentSwap
import com.example.kpkn.screens.workout.components.liveEnter
import com.example.kpkn.screens.workout.components.liveExit
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import kotlinx.coroutines.delay
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.kpkn.screens.workout.components.VoiceTargetStyle
import com.example.kpkn.screens.workout.components.voiceFlightTarget
import com.example.kpkn.screens.workout.components.voiceRowTargetKey
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
private val SyneValue = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp)
private val Body = TextStyle(fontFamily = WizardFonts.body)

/** El descanso que se ve dentro de la Lista: de aproximación, de movilidad, de serie efectiva o ya cumplido. */
private enum class ListRestKind { WARMUP, MOBILITY, WORKING, OVERRUN }

private fun mobilityEntryKey(exerciseId: String, mobilityId: String) = "mob-$exerciseId-$mobilityId"
private fun warmupEntryKey(exerciseId: String, warmupId: String) = "warm-$exerciseId-$warmupId"

/** Un ítem de la Lista; [segment] es la parte de la superficie del ejercicio que pinta (null: fuera de las tarjetas). */
private sealed class ListEntry(val key: String, val segment: ListCardSegment?) {
    class Intro(val count: String) : ListEntry("intro", null)
    class Header(val exercise: Exercise, val summary: String, val collapsed: Boolean, val done: Boolean) :
        ListEntry("ex-${exercise.id}", ListCardSegment.TOP)
    class Collapsed(val exercise: Exercise) : ListEntry("collapsed-${exercise.id}", ListCardSegment.MIDDLE)
    class SubLabel(key: String, val label: String, val count: String, val detail: String? = null) : ListEntry(key, ListCardSegment.MIDDLE)
    class Mobility(val exercise: Exercise, val mobility: MobilitySeries, val index: Int, val done: Boolean, val active: Boolean) :
        ListEntry(mobilityEntryKey(exercise.id, mobility.id), ListCardSegment.MIDDLE)
    class Warmup(
        val exercise: Exercise,
        val warmup: WarmupSetDefinition,
        val index: Int,
        val done: Boolean,
        val active: Boolean,
        val suggestedKg: Double?,
        val recorded: CompletedSet?,
        val workingKg: Double?,
    ) : ListEntry(warmupEntryKey(exercise.id, warmup.id), ListCardSegment.MIDDLE)
    class Rest(val kind: ListRestKind, inCard: Boolean) : ListEntry("rest-${kind.name}", if (inCard) ListCardSegment.MIDDLE else null)
    class SetRow(val row: ListSetRow, val superset: Boolean, val active: Boolean) : ListEntry(row.key, ListCardSegment.MIDDLE)
    class SupersetHead(val groupId: String, val members: List<Exercise>, val letter: String) : ListEntry("sg-$groupId", ListCardSegment.TOP)
    class Round(val groupId: String, val round: Int) : ListEntry("round-$groupId-$round", ListCardSegment.MIDDLE)
    class MemberName(key: String, val badge: String, val name: String, val first: Boolean) : ListEntry(key, ListCardSegment.MIDDLE)
    class End(key: String) : ListEntry(key, ListCardSegment.BOTTOM)
    class Cardio(val exercise: Exercise, val order: Int, val done: Boolean) : ListEntry("cardio-${exercise.id}", null)
    object Tail : ListEntry("list-tail", null)
}

/** Lo que toca hacer en la Lista, en su orden, para saltar al siguiente pendiente tras registrar. */
private fun ListEntry.isPendingStep(): Boolean = when (this) {
    is ListEntry.Mobility -> !done
    is ListEntry.Warmup -> !done
    is ListEntry.SetRow -> row.completed == null
    else -> false
}

/** La carga de trabajo de la que salen las aproximaciones: la primera serie efectiva prevista o ya registrada (nunca se inventa). */
private fun warmupAnchorKg(exercise: Exercise, completedSets: Map<String, CompletedSet>, fallback: Double?): Double? =
    exercise.sets.firstOrNull { !it.isEmptySlot }?.weight?.takeIf { it > 0.0 }
        ?: completedSets["${exercise.id}_0"]?.weight?.takeIf { it > 0.0 }
        ?: completedSets["${exercise.id}_0_L"]?.weight?.takeIf { it > 0.0 }
        ?: completedSets["${exercise.id}_0_R"]?.weight?.takeIf { it > 0.0 }
        ?: fallback?.takeIf { it > 0.0 }

/**
 * Vista Lista de la sesión en vivo (prototipo 02, B · Lista, con los cambios que pidió el usuario el 29-oct): sin cabecera de contexto
 * arriba; «REGISTRO DE LA SESIÓN x / n»; cada ejercicio es una superficie plana propia con su título como jerarquía principal y, dentro y
 * en este orden, su movilidad, sus aproximaciones (con kg y reps reales editables) y sus series efectivas. Lo registrado se queda en la
 * lista marcado y editable; el descanso se ve dentro de la lista, desplegado o minimizado; al terminar un ejercicio se pliega y se
 * despliega el siguiente, y la lista se desplaza sola para que lo que toca ahora quede siempre a la vista. Comparte borrador, historial y
 * registro con la tarjeta; nunca convierte el esfuerzo planificado en realizado.
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
    /** Descanso de una serie efectiva en marcha: segundos que quedan y total. Desplegado, lo cubre la pantalla completa; minimizado, se ve aquí. */
    workingRestRemaining: Int? = null,
    workingRestTotal: Int? = null,
) {
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var recordRequest by remember { mutableIntStateOf(0) }
    // Un toque en ✓ de una fila cerrada la abre y registra a la vez; tocar el esfuerzo abre su escala.
    var autoRecordKey by remember { mutableStateOf<String?>(null) }
    // Panel que debe abrir el editor de una fila: «scale» (esfuerzo), «advanced» o «details».
    var panelOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
    val sections = remember(exercises) { buildSections(exercises) }
    val doneExerciseIds = sections.filterIsInstance<ListSection.Single>().filter { section ->
        !isCardioExercise(section.exercise) && rowsFor(section.exercise, section.order, uiState.completedSets).let { rows ->
            rows.isNotEmpty() && rows.all { it.completed != null }
        }
    }.map { it.exercise.id }
    // Al abrir la Lista, los ejercicios ya terminados aparecen plegados desde el primer fotograma (sin saltos de desplazamiento).
    var collapsed by rememberSaveable { mutableStateOf(doneExerciseIds) }
    // Ejercicios terminados que la Lista ya plegó: solo se pliega uno en el momento de terminarlo.
    var foldedDone by rememberSaveable { mutableStateOf(doneExerciseIds) }
    var movementSheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    val reduced = wizardReducedMotion()
    val workingDone = exercises.sumOf { exercise ->
        rowsFor(exercise, 0, uiState.completedSets).count { it.completed != null && !it.completed.skipped }
    }
    val workingTotal = exercises.sumOf { exercise -> rowsFor(exercise, 0, uiState.completedSets).size }

    // Carga sugerida de cada aproximación: la misma fuente que la tarjeta y la voz (kg alcanzables con el material real, ya redondeados;
    // null = falta la carga de trabajo, nunca 0) y la carga de trabajo de la que sale.
    val warmupPlan = remember(exercises, uiState.completedSets, uiState.exerciseTags, warmupWorkingWeight, currentExerciseId) {
        exercises.filter { it.warmupSets.isNotEmpty() }.associate { exercise ->
            val tag = uiState.exerciseTags[exercise.id]
            val anchor = warmupAnchorKg(exercise, uiState.completedSets, warmupWorkingWeight.takeIf { exercise.id == currentExerciseId })
            val suggested = exercise.warmupSets.indices.map { index ->
                viewModel.getWarmupDisplayWeight(exercise = exercise, warmupIndex = index, activeTag = tag, workingWeightAnchor = anchor)
            }
            exercise.id to (suggested to (anchor ?: viewModel.getWarmupWorkingWeightAnchor(exercise, tag)?.takeIf { it > 0.0 }))
        }
    }

    // Dónde va el descanso que corre: tras la aproximación o la movilidad que lo abrió, o tras la serie recién registrada.
    val restModal = uiState.restModalState
    val restKind: ListRestKind? = when {
        warmupRestRemaining != null -> if (warmupRestIsMobility) ListRestKind.MOBILITY else ListRestKind.WARMUP
        workingRestRemaining != null -> ListRestKind.WORKING
        uiState.restOverrun != null && uiState.isRestMinimized && !uiState.isRestTimerRunning -> ListRestKind.OVERRUN
        else -> null
    }
    val restAnchor: String? = when (restKind) {
        ListRestKind.WARMUP -> restModal?.warmupSetId?.let { warmupId ->
            exercises.firstOrNull { exercise -> exercise.warmupSets.any { it.id == warmupId } }?.let { warmupEntryKey(it.id, warmupId) }
        }
        ListRestKind.MOBILITY -> (exercises.firstOrNull { it.id == currentExerciseId } ?: exercises.firstOrNull { it.id == restModal?.exerciseId })
            ?.let { exercise -> exercise.mobilitySeries.lastOrNull()?.let { mobilityEntryKey(exercise.id, it.id) } }
        ListRestKind.WORKING, ListRestKind.OVERRUN -> uiState.setJustLoggedKey
        null -> null
    }

    fun buildEntries(collapsedIds: Set<String>): List<ListEntry> {
        val out = mutableListOf<ListEntry>()
        var restPlaced = false
        fun addRestAfter(key: String) {
            if (restKind != null && !restPlaced && key == restAnchor) {
                out += ListEntry.Rest(restKind, inCard = true)
                restPlaced = true
            }
        }
        fun addPreparation(exercise: Exercise, badge: String?) {
            if (exercise.mobilitySeries.isNotEmpty()) {
                val done = exercise.mobilitySeries.count { mobilityDone(uiState, exercise.id, it.id, it.sets) }
                out += ListEntry.SubLabel("sub-${exercise.id}-mob", "MOVILIDAD", "$done / ${exercise.mobilitySeries.size}", badge)
                exercise.mobilitySeries.forEachIndexed { index, mobility ->
                    val stepKey = WorkoutStepRules.mobilityStepKey(exercise.id, mobility.id)
                    val active = uiState.activeStepKey.let { it == stepKey || it?.startsWith("${stepKey}_set_") == true }
                    out += ListEntry.Mobility(exercise, mobility, index, mobilityDone(uiState, exercise.id, mobility.id, mobility.sets), active)
                    addRestAfter(mobilityEntryKey(exercise.id, mobility.id))
                }
            }
            if (exercise.warmupSets.isNotEmpty()) {
                val plan = warmupPlan[exercise.id]
                val workingKg = plan?.second
                val done = exercise.warmupSets.count { warmupDone(uiState, exercise.id, it.id) }
                val detail = listOfNotNull(badge, workingKg?.let { "Trabajo · ${listNumber(it)} kg" }).joinToString(" · ").ifBlank { null }
                out += ListEntry.SubLabel("sub-${exercise.id}-warm", "APROXIMACIONES", "$done / ${exercise.warmupSets.size}", detail)
                exercise.warmupSets.forEachIndexed { index, warmup ->
                    val stepKey = WorkoutStepRules.warmupStepKey(exercise.id, warmup.id)
                    out += ListEntry.Warmup(
                        exercise = exercise, warmup = warmup, index = index, done = warmupDone(uiState, exercise.id, warmup.id),
                        active = uiState.activeStepKey == stepKey, suggestedKg = plan?.first?.getOrNull(index),
                        recorded = uiState.completedSets[stepKey], workingKg = workingKg,
                    )
                    addRestAfter(warmupEntryKey(exercise.id, warmup.id))
                }
            }
        }
        out += ListEntry.Intro("$workingDone / $workingTotal")
        val supersets = sections.filterIsInstance<ListSection.Superset>()
        sections.forEach { section ->
            when (section) {
                is ListSection.Single -> {
                    val exercise = section.exercise
                    val rows = rowsFor(exercise, section.order, uiState.completedSets)
                    if (isCardioExercise(exercise)) {
                        // `c-list`: el cardio es un bloque propio con su perfil y «Empezar cardio»; no se descompone en filas de series.
                        out += ListEntry.Cardio(exercise, section.order, rows.any { it.completed != null })
                        return@forEach
                    }
                    val isCollapsed = exercise.id in collapsedIds
                    val exerciseDone = rows.isNotEmpty() && rows.all { it.completed != null }
                    val registered = rows.count { it.completed != null && !it.completed.skipped }
                    val summary = listOfNotNull(
                        exercise.mobilitySeries.takeIf { it.isNotEmpty() }?.let { list ->
                            "Movilidad ${list.count { mobilityDone(uiState, exercise.id, it.id, it.sets) }}/${list.size}"
                        },
                        exercise.warmupSets.takeIf { it.isNotEmpty() }?.let { list ->
                            "Aprox. ${list.count { warmupDone(uiState, exercise.id, it.id) }}/${list.size}"
                        },
                        "Series $registered/${rows.size}",
                    ).joinToString(" · ")
                    out += ListEntry.Header(exercise, summary, isCollapsed, exerciseDone)
                    if (isCollapsed) {
                        out += ListEntry.Collapsed(exercise)
                    } else {
                        addPreparation(exercise, badge = null)
                        if (exercise.mobilitySeries.isNotEmpty() || exercise.warmupSets.isNotEmpty()) {
                            out += ListEntry.SubLabel("sub-${exercise.id}-work", "SERIES EFECTIVAS", "$registered / ${rows.size}")
                        }
                        rows.forEach { row ->
                            out += ListEntry.SetRow(row, superset = false, active = isActive(row, uiState, currentExerciseId))
                            addRestAfter(row.key)
                        }
                    }
                    out += ListEntry.End("end-${exercise.id}")
                }
                is ListSection.Superset -> {
                    val letter = ('A' + supersets.indexOf(section).coerceAtLeast(0)).toString()
                    out += ListEntry.SupersetHead(section.groupId, section.members.map { it.first }, letter)
                    section.members.forEachIndexed { memberIdx, (member, _) -> addPreparation(member, badge = "$letter${memberIdx + 1}") }
                    val roundCount = section.members.maxOfOrNull { it.first.sets.count { set -> !set.isEmptySlot } } ?: 0
                    for (round in 0 until roundCount) {
                        out += ListEntry.Round(section.groupId, round)
                        section.members.forEachIndexed { memberIdx, (member, order) ->
                            val set = member.sets.getOrNull(round) ?: return@forEachIndexed
                            if (set.isEmptySlot) return@forEachIndexed
                            out += ListEntry.MemberName("sm-${member.id}-$round", "$letter${memberIdx + 1}", displayWorkoutExerciseName(member),
                                first = memberIdx == 0)
                            rowsForSet(member, order, round, set, uiState.completedSets, "${round + 1}").forEach { row ->
                                out += ListEntry.SetRow(row, superset = true, active = isActive(row, uiState, currentExerciseId))
                                addRestAfter(row.key)
                            }
                        }
                    }
                    out += ListEntry.End("sg-end-${section.groupId}")
                }
            }
        }
        // Si lo que abrió el descanso está plegado (o no está en la lista), el descanso va arriba, a la vista.
        if (restKind != null && !restPlaced) out.add(1, ListEntry.Rest(restKind, inCard = false))
        out += ListEntry.Tail
        return out
    }

    val entries = buildEntries(collapsed.toSet())
    // El orden completo de lo que toca (sin pliegues) para saltar al siguiente pendiente aunque esté en un ejercicio plegado.
    val stepOrder = buildEntries(emptySet()).filter { it is ListEntry.Mobility || it is ListEntry.Warmup || it is ListEntry.SetRow }

    // Al terminar un ejercicio se pliega y se despliega el siguiente (pedido del usuario, 29-oct).
    LaunchedEffect(doneExerciseIds) {
        val known = foldedDone
        val newlyDone = doneExerciseIds.filter { it !in known }
        if (newlyDone.isNotEmpty()) {
            val singles = sections.filterIsInstance<ListSection.Single>().map { it.exercise.id }
            val nextIds = newlyDone.mapNotNull { id ->
                singles.drop(singles.indexOf(id) + 1).firstOrNull { it !in doneExerciseIds }
            }
            collapsed = (collapsed + newlyDone).distinct() - nextIds.toSet()
        }
        foldedDone = known.filter { it in doneExerciseIds } + newlyDone
    }

    fun unfold(exerciseId: String) {
        if (exerciseId in collapsed) collapsed = collapsed - exerciseId
    }

    /** Tras registrar algo, el acordeón salta a lo siguiente pendiente en el orden de la lista (`adjacent(1)` del prototipo). */
    fun advanceFrom(key: String, selectStep: Boolean) {
        val index = stepOrder.indexOfFirst { it.key == key }
        val next = stepOrder.drop(index + 1).firstOrNull { it.isPendingStep() }
        when (next) {
            is ListEntry.SetRow -> {
                expandedKey = next.key
                unfold(next.row.exercise.id)
                if (selectStep) viewModel.selectWorkoutStep(WorkoutStepRules.workingStepKey(next.row.exercise.id, next.row.setIndex, next.row.side))
            }
            is ListEntry.Warmup -> {
                expandedKey = next.key
                unfold(next.exercise.id)
                if (selectStep) viewModel.selectWorkoutStep(WorkoutStepRules.warmupStepKey(next.exercise.id, next.warmup.id))
            }
            is ListEntry.Mobility -> {
                expandedKey = null
                unfold(next.exercise.id)
                if (selectStep) viewModel.selectWorkoutStep(WorkoutStepRules.mobilityStepKey(next.exercise.id, next.mobility.id))
            }
            else -> expandedKey = null
        }
    }

    fun open(row: ListSetRow) {
        val opening = expandedKey != row.key
        expandedKey = if (opening) row.key else null
        if (opening) viewModel.selectWorkoutStep(WorkoutStepRules.workingStepKey(row.exercise.id, row.setIndex, row.side))
    }
    fun openWarmup(entry: ListEntry.Warmup) {
        val opening = expandedKey != entry.key
        expandedKey = if (opening) entry.key else null
        if (opening && !entry.done) viewModel.selectWorkoutStep(WorkoutStepRules.warmupStepKey(entry.exercise.id, entry.warmup.id))
    }
    fun registerWarmup(entry: ListEntry.Warmup, kg: Double?, reps: Int) {
        // Nueva: la API de la tarjeta (guarda un CompletedSet de aproximación con ese peso y abre su descanso). Ya hecha: corrige lo guardado.
        if (entry.done) viewModel.reportWarmupStep(entry.exercise.id, entry.warmup.id, kg, reps)
        else viewModel.completeWarmupStep(entry.exercise.id, entry.warmup.id, usedWeightKg = kg, reportedReps = reps)
        advanceFrom(entry.key, selectStep = false)
    }
    fun toggleMobility(entry: ListEntry.Mobility) {
        val done = !entry.done
        (0 until entry.mobility.sets.coerceAtLeast(1)).forEach { index ->
            viewModel.markMobilityComplete(entry.exercise.id, entry.mobility.id, index, done)
        }
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
    val orderedRows = stepOrder.filterIsInstance<ListEntry.SetRow>().map { it.row }
    // Como en el prototipo, la Lista abre con el acordeón de lo que toca desplegado (una sola vez: luego manda el usuario): la serie del
    // paso activo o, si toca una aproximación, su editor con los kg. Si toca la movilidad, no se abre nada (no se salta la preparación).
    var initialOpenDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (initialOpenDone) return@LaunchedEffect
        initialOpenDone = true
        if (expandedKey == null) {
            expandedKey = stepOrder.firstOrNull { entry ->
                entry.isPendingStep() && ((entry as? ListEntry.SetRow)?.active == true || (entry as? ListEntry.Warmup)?.active == true)
            }?.key
        }
    }
    LaunchedEffect(panelRequest) {
        val request = panelRequest ?: return@LaunchedEffect
        onPanelRequestConsumed()
        val target = orderedRows.firstOrNull { it.exercise.id == request.first && it.setIndex == request.second } ?: return@LaunchedEffect
        unfold(target.exercise.id)
        expandedKey = target.key
        panelOpen = target.key to request.third
    }

    // Autodesplazamiento (pedido del usuario, 29-oct): lo que toca ahora (el descanso que aparece, el editor abierto o la fila activa)
    // queda siempre a la vista. Solo se mueve si eso no se ve entero y nunca mientras la persona arrastra la lista.
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val restKey = entries.firstOrNull { it is ListEntry.Rest }?.key
    val activeKey = entries.firstOrNull { entry ->
        when (entry) {
            is ListEntry.SetRow -> entry.active
            is ListEntry.Warmup -> entry.active && !entry.done
            is ListEntry.Mobility -> entry.active && !entry.done
            else -> false
        }
    }?.key
    val focusKey = restKey ?: expandedKey?.takeIf { key -> entries.any { it.key == key } } ?: activeKey
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(focusKey) {
        val key = focusKey ?: return@LaunchedEffect
        // Deja que el editor o el descanso terminen de desplegarse antes de medir.
        if (!reduced) delay(LIST_FOCUS_SETTLE_MS)
        // Al menos un fotograma de por medio: sin medir la lista, todo parecería fuera de la vista.
        androidx.compose.runtime.withFrameNanos { }
        if (dragged || listState.isScrollInProgress) return@LaunchedEffect
        val index = entries.indexOfFirst { it.key == key }
        if (index < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val visibleTop = 0
        val visibleBottom = info.viewportEndOffset - info.afterContentPadding
        val item = info.visibleItemsInfo.firstOrNull { it.key == key }
        if (item != null && item.offset >= visibleTop && item.offset + item.size <= visibleBottom) return@LaunchedEffect
        // Con la fila anterior a la vista, si cabe: da contexto (la serie recién registrada encima de su descanso).
        val roomForContext = item == null || item.size + with(density) { 64.dp.roundToPx() } < visibleBottom
        val target = if (roomForContext) (index - 1).coerceAtLeast(0) else index
        if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }

    val movement = movementSheet?.let { (exerciseId, mobilityId) ->
        entries.filterIsInstance<ListEntry.Mobility>().firstOrNull { it.exercise.id == exerciseId && it.mobility.id == mobilityId }
    }
    movement?.let { entry ->
        LiveListMovementSheet(entry.mobility, mobilityVolume(entry.mobility), entry.done, onToggle = { toggleMobility(entry) },
            onDismiss = { movementSheet = null })
    }

    LiveListStrictTouch {
    LazyColumn(
        state = listState,
        // Fundido a negro arriba (bajo la isla) y abajo: las filas se desvanecen en los bordes en vez de cortarse en seco.
        modifier = modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val h = size.height
                if (h > 0f) {
                    // La Lista pasa por debajo de la isla: hasta casi su borde inferior no se ve nada (ni sobre la barra de estado ni
                    // alrededor de la isla) y desde ahí las filas aparecen fundidas en 40 dp.
                    val fadeEnd = maxOf(topInset.toPx(), 56.dp.toPx()).coerceAtMost(h * .4f)
                    val fadeStart = (fadeEnd - 40.dp.toPx()).coerceAtLeast(0f)
                    val bottom = (72.dp.toPx() / h).coerceIn(0f, .3f)
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, fadeStart / h to Color.Transparent, fadeEnd / h to Color.Black,
                            (1f - bottom) to Color.Black, 1f to Color.Transparent),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                }
            }
            .testTag("live-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = topInset, bottom = 28.dp),
    ) {
        items(entries, key = { it.key }, contentType = { it::class }) { entry ->
            // Toda aparición, desaparición y recolocación se funde y se desliza (corte inmediato con el movimiento reducido).
            val motion = Modifier.animateItem(
                fadeInSpec = if (reduced) null else tween(220, easing = FastOutSlowInEasing),
                placementSpec = if (reduced) null else tween(280, easing = FastOutSlowInEasing),
                fadeOutSpec = if (reduced) null else tween(160, easing = FastOutSlowInEasing),
            )
            if (entry is ListEntry.End) {
                // El cierre de la superficie del ejercicio y, fuera de ella, más aire que entre filas.
                Column(motion.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(10.dp).listCardSegment(ListCardSegment.BOTTOM).listTapGuard())
                    Spacer(Modifier.height(ListCardGap))
                }
                return@items
            }
            Box(motion.fillMaxWidth().then(entry.segment?.let { Modifier.listCardSegment(it) } ?: Modifier).listTapGuard()) {
                when (entry) {
                    is ListEntry.Intro -> ListIntro("REGISTRO DE LA SESIÓN", entry.count)
                    is ListEntry.Header -> ExerciseListHeader(
                        exercise = entry.exercise,
                        summary = entry.summary,
                        collapsed = entry.collapsed,
                        done = entry.done,
                        onToggleCollapse = {
                            collapsed = if (entry.collapsed) collapsed - entry.exercise.id else collapsed + entry.exercise.id
                        },
                        onOptions = { onOpenExerciseOptions(entry.exercise.id) },
                    )
                    is ListEntry.Collapsed -> Text(
                        "Tocar para desplegar",
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button) { unfold(entry.exercise.id) }
                            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp).testTag("live-list-collapsed-${entry.exercise.id}"),
                        style = Body.copy(fontSize = 12.sp), color = WizardColors.textFaint,
                    )
                    is ListEntry.SubLabel -> LiveListSubLabel(entry.label, entry.count, entry.detail, tag = "live-list-${entry.key}")
                    is ListEntry.Mobility -> LiveListMobilityRow(
                        index = entry.index,
                        mobility = entry.mobility,
                        volume = mobilityVolume(entry.mobility),
                        done = entry.done,
                        active = entry.active,
                        onOpen = { movementSheet = entry.exercise.id to entry.mobility.id },
                        onToggle = { toggleMobility(entry) },
                    )
                    is ListEntry.Warmup -> ListWarmupItem(
                        entry = entry,
                        expanded = expandedKey == entry.key,
                        onOpen = { openWarmup(entry) },
                        onQuickRegister = {
                            // ✓ en una pendiente: se registra con la carga sugerida y las reps previstas; ya hecha, abre su editor.
                            if (entry.done) openWarmup(entry) else registerWarmup(entry, entry.suggestedKg, entry.warmup.targetReps)
                        },
                        onRegister = { kg, reps -> registerWarmup(entry, kg, reps) },
                        onMarkPending = { viewModel.markWarmupComplete(entry.exercise.id, entry.warmup.id, completed = false) },
                    )
                    is ListEntry.Rest -> ListRestItem(
                        kind = entry.kind,
                        uiState = uiState,
                        exercises = exercises,
                        warmupPlan = warmupPlan,
                        warmupRestRemaining = warmupRestRemaining,
                        warmupRestTotal = warmupRestTotal,
                        workingRestRemaining = workingRestRemaining,
                        workingRestTotal = workingRestTotal,
                        onEndWarmupRest = onEndWarmupRest,
                        viewModel = viewModel,
                    )
                    is ListEntry.SetRow -> {
                        val row = entry.row
                        // `.v2-superset` (filete crema de 2 y 13 de margen) + `.v2-super-exercise` (filete de 1 y 10 de sangría).
                        val inset = if (entry.superset) {
                            Modifier.padding(horizontal = 14.dp).supersetRail(inner = false).padding(start = 13.dp).supersetRail(inner = true).padding(start = 10.dp)
                        } else Modifier.padding(horizontal = 12.dp)
                        Box(inset) {
                            ListRowWithEditor(row, expandedKey == row.key, entry.active, uiState, viewModel,
                                recordRequest, onOpen = { open(row) }, onRegister = { register(row) }, onClose = { expandedKey = null },
                                onEffort = { openEffort(row) }, onRecorded = { advanceFrom(row.key, selectStep = true) },
                                autoRecord = autoRecordKey == row.key, onAutoRecordConsumed = { autoRecordKey = null },
                                openPanel = panelOpen?.takeIf { it.first == row.key }?.second, onPanelConsumed = { panelOpen = null },
                                supersetColumns = entry.superset, media = media)
                        }
                    }
                    is ListEntry.SupersetHead -> Box(Modifier.padding(horizontal = 14.dp)) {
                        SupersetHeader(entry.members, entry.letter, onSettings = { onOpenSupersetSettings(entry.groupId) })
                    }
                    is ListEntry.Round -> Box(Modifier.padding(horizontal = 14.dp)) { RoundLabel(entry.round, entry.groupId) }
                    is ListEntry.MemberName -> Box(Modifier.padding(horizontal = 14.dp)) { SupersetMemberName(entry.badge, entry.name, entry.first) }
                    is ListEntry.Cardio -> Box(Modifier.padding(bottom = ListCardGap - 6.dp)) {
                        ListCardioBlock(entry.exercise, entry.done) {
                            viewModel.selectExercise(entry.order)
                            viewModel.setLiveSessionViewPreference(LiveSessionView.CARDS.name)
                        }
                    }
                    ListEntry.Tail -> Spacer(Modifier.height(8.dp))
                    is ListEntry.End -> Unit
                }
            }
        }
    }
    }
}

/** Una aproximación de la Lista: su fila y, desplegado debajo, su editor con los kg y las reps reales. */
@Composable
private fun ListWarmupItem(
    entry: ListEntry.Warmup,
    expanded: Boolean,
    onOpen: () -> Unit,
    onQuickRegister: () -> Unit,
    onRegister: (Double?, Int) -> Unit,
    onMarkPending: () -> Unit,
) {
    val reduced = wizardReducedMotion()
    val label = "A${entry.index + 1}"
    val recordedKg = entry.recorded?.weight?.takeIf { entry.done && it > 0.0 }
    val recordedReps = entry.recorded?.reps?.takeIf { entry.done && it > 0 }
    val percent = listWarmupPercent(entry.warmup.percentageOfWorkingWeight)
    val hint = when {
        entry.done -> "Registrada · corrige los kg o las reps si hace falta"
        entry.suggestedKg != null && entry.workingKg != null ->
            "Sugerido · ${listNumber(entry.suggestedKg)} kg × ${entry.warmup.targetReps} · $percent de ${listNumber(entry.workingKg)} kg"
        entry.suggestedKg != null -> "Sugerido · ${listNumber(entry.suggestedKg)} kg × ${entry.warmup.targetReps}"
        else -> "Sin carga de trabajo: escribe los kg que usaste"
    }
    Column(Modifier.fillMaxWidth().listAnimatedSize()) {
        LiveListWarmupRow(
            label = label,
            percent = percent,
            dose = listDose(recordedKg ?: entry.suggestedKg, "${recordedReps ?: entry.warmup.targetReps}"),
            done = entry.done,
            active = entry.active && !entry.done,
            expanded = expanded,
            tag = "live-list-warm-row-${entry.exercise.id}-${entry.warmup.id}",
            onOpen = onOpen,
            onCheck = onQuickRegister,
        )
        AnimatedVisibility(visible = expanded, enter = liveEnter(reduced), exit = liveExit(reduced)) {
            LiveListWarmupEditor(
                label = label,
                tag = "live-list-warm-${entry.exercise.id}-${entry.warmup.id}",
                suggestedKg = entry.suggestedKg,
                targetReps = entry.warmup.targetReps,
                recorded = entry.recorded,
                done = entry.done,
                hint = hint,
                onRegister = onRegister,
                onMarkPending = onMarkPending,
            )
        }
    }
}

/**
 * El descanso dentro de la Lista. El de aproximación o movilidad se ve desplegado (la tarjeta del descanso) o minimizado (una fila
 * compacta) y se alterna entre ambos; el de las series efectivas, desplegado, ocupa la pantalla completa y, minimizado, queda aquí.
 */
@Composable
private fun ListRestItem(
    kind: ListRestKind,
    uiState: WorkoutUiState,
    exercises: List<Exercise>,
    warmupPlan: Map<String, Pair<List<Double?>, Double?>>,
    warmupRestRemaining: Int?,
    warmupRestTotal: Int?,
    workingRestRemaining: Int?,
    workingRestTotal: Int?,
    onEndWarmupRest: () -> Unit,
    viewModel: WorkoutViewModel,
) {
    val reduced = wizardReducedMotion()
    val minimized = uiState.isRestMinimized
    when (kind) {
        ListRestKind.WARMUP, ListRestKind.MOBILITY -> {
            val remaining = (warmupRestRemaining ?: 0).coerceAtLeast(0)
            val total = (warmupRestTotal ?: 60).coerceAtLeast(1)
            val kicker = if (kind == ListRestKind.MOBILITY) "DESCANSO · MOVILIDAD" else "DESCANSO · APROX."
            val warmupExercise = uiState.restModalState?.warmupSetId?.let { id -> exercises.firstOrNull { e -> e.warmupSets.any { it.id == id } } }
            val next = if (kind == ListRestKind.MOBILITY || warmupExercise == null) null else {
                warmupExercise.warmupSets.withIndex().firstOrNull { !warmupDone(uiState, warmupExercise.id, it.value.id) }?.let { (index, warmup) ->
                    val kg = warmupPlan[warmupExercise.id]?.first?.getOrNull(index)
                    "A${index + 1}" to "${kg?.let(::listNumber) ?: "—"} kg × ${warmup.targetReps}"
                }
            }
            AnimatedContent(minimized, transitionSpec = { liveContentSwap(reduced, animateSize = true) }, label = "live-list-rest") { mini ->
                if (mini) {
                    LiveListRestBar("DESCANSO", remaining, 1f - remaining.toFloat() / total, tag = "live-list-rest-mini",
                        onExpand = { viewModel.toggleRestMinimized() }, onAddTime = { viewModel.addPreparationRestTime(15) }, onEnd = onEndWarmupRest,
                        detail = if (kind == ListRestKind.MOBILITY) "movilidad" else "aprox.")
                } else {
                    Box(Modifier.padding(horizontal = 10.dp)) {
                        ListWarmupRest(remaining, total, kicker, next, onEnd = onEndWarmupRest, onMinimize = { viewModel.toggleRestMinimized() },
                            isPaused = uiState.restModalState?.pausedRemainingSeconds != null,
                            onAdjust = { viewModel.addPreparationRestTime(it) }, onTogglePause = { viewModel.toggleRestPause() })
                    }
                }
            }
        }
        ListRestKind.WORKING -> {
            val remaining = (workingRestRemaining ?: 0).coerceAtLeast(0)
            val total = (workingRestTotal ?: 90).coerceAtLeast(1)
            LiveListRestBar(
                kicker = "DESCANSO",
                detail = "serie",
                remainingSeconds = remaining,
                progress = 1f - remaining.toFloat() / total,
                tag = "live-list-rest-mini",
                onExpand = { if (uiState.isRestMinimized) viewModel.toggleRestMinimized() },
                onAddTime = { viewModel.addRestTime(15) },
                onEnd = { viewModel.stopRestTimer() },
            )
        }
        ListRestKind.OVERRUN -> LiveListRestBar(
            kicker = "DESCANSO CUMPLIDO",
            remainingSeconds = null,
            progress = 1f,
            tag = "live-list-rest-mini",
            onExpand = { viewModel.toggleRestMinimized() },
            onAddTime = null,
            onEnd = { viewModel.dismissRestOverrun() },
            endLabel = "Seguir",
        )
    }
}

private const val LIST_FOCUS_SETTLE_MS = 320L

// La fila activa es la del paso activo de la sesión: mientras toca la movilidad o una aproximación, ninguna serie efectiva lo es (antes
// se miraba solo `currentSetIdx`, que sigue en la serie 1 durante la preparación). Una serie ya registrada no se marca como activa.
private fun isActive(row: ListSetRow, uiState: WorkoutUiState, currentExerciseId: String?): Boolean {
    if (row.completed != null && !row.completed.skipped) return false
    val step = uiState.activeStepKey
    return if (step != null) step == WorkoutStepRules.workingStepKey(row.exercise.id, row.setIndex, row.side)
    else row.exercise.id == currentExerciseId && row.setIndex == uiState.currentSetIdx
}

/** El filete de la superserie: 2 dp crema a la izquierda del bloque (`.v2-superset`) y 1 dp tenue por ejercicio. */
private fun Modifier.supersetRail(inner: Boolean): Modifier = drawBehind {
    drawLine(if (inner) Color(0x22FFFFFF) else ListSuperLine, Offset(0f, 0f), Offset(0f, size.height), (if (inner) 1 else 2).dp.toPx())
}

@Composable
private fun ListIntro(label: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Body.copy(fontSize = 11.sp, letterSpacing = .7.sp), color = WizardColors.textFaint)
        Text(count, style = Body.copy(fontSize = 11.sp, letterSpacing = .7.sp), color = WizardColors.textFaint)
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
        // El editor de la serie se despliega y se pliega animado (fundido + alto), no a saltos.
        val reducedMotion = wizardReducedMotion()
        AnimatedVisibility(visible = expanded, enter = liveEnter(reducedMotion), exit = liveExit(reducedMotion)) {
            InlineSetEditor(row, uiState, viewModel, recordRequest, onRecorded, autoRecord, onAutoRecordConsumed, openPanel, onPanelConsumed,
                media)
        }
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

/**
 * Cabecera del ejercicio, la jerarquía principal de la Lista (pedido del usuario, 29-oct): el nombre en Syne 17 (más grande que el
 * resto, sin exagerar), el avance de movilidad, aproximaciones y series debajo, la marca de terminado, el pliegue y ⋯ con sus opciones.
 */
@Composable
private fun ExerciseListHeader(
    exercise: Exercise,
    summary: String,
    collapsed: Boolean,
    done: Boolean,
    onToggleCollapse: () -> Unit,
    onOptions: () -> Unit,
) {
    val reduced = wizardReducedMotion()
    val chevron by androidx.compose.animation.core.animateFloatAsState(if (collapsed) 0f else 180f,
        com.example.kpkn.screens.workout.components.liveSpec(reduced, 260), label = "live-list-chevron")
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
            .clickable(role = Role.Button, onClick = onToggleCollapse)
            .semantics { contentDescription = "${displayWorkoutExerciseName(exercise)}, ${if (collapsed) "plegado" else "desplegado"}" }
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)
            .testTag("live-list-exercise-${exercise.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = displayWorkoutExerciseName(exercise),
                style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
                color = if (done) WizardColors.textMuted else WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(summary, Modifier.padding(top = 3.dp), style = Body.copy(fontSize = 12.sp), color = WizardColors.textFaint, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        if (done) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.Check, "Ejercicio terminado", Modifier.size(12.dp), tint = WizardColors.text)
            }
        }
        Icon(KpknLineIcons.ChevronDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = chevron }, tint = WizardColors.textMuted)
        ListIconButton(KpknLineIcons.More, "Opciones de ${displayWorkoutExerciseName(exercise)}", onOptions, width = 40.dp, size = 18.dp)
    }
}

@Composable
private fun ListIconButton(icon: ImageVector, description: String, onClick: () -> Unit, width: Dp = 44.dp, size: Dp = 16.dp) {
    Box(Modifier.width(width).height(44.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
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
                Text("SUPERSERIE $letter", style = Body.copy(fontSize = 11.sp, letterSpacing = 1.2.sp), color = WizardColors.textFaint)
                Text(members.joinToString(" + ", transform = ::displayWorkoutExerciseName), Modifier.padding(top = 8.dp),
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
                    color = WizardColors.text)
            }
            ListIconButton(KpknLineIcons.Sliders, "Configurar superserie", onSettings)
        }
        FlowRow(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOfNotNull("$rounds rondas", between?.let { "Entre ejercicios · $it s" }, after?.let { "Tras ronda · ${KpknClock.format(it)}" })
                .forEach { Text(it, style = Body.copy(fontSize = 11.sp), color = WizardColors.textFaint) }
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
        Text("RONDA ${round + 1}", style = Body.copy(fontSize = 11.sp, letterSpacing = .8.sp), color = WizardColors.textMuted)
        Box(Modifier.weight(1f).height(1.dp).background(Color(0x16FFFFFF)))
    }
}

@Composable
private fun SupersetMemberName(badge: String, name: String, first: Boolean) {
    Row(Modifier.fillMaxWidth().supersetRail(inner = false).padding(start = 13.dp).supersetRail(inner = true)
        .padding(start = 10.dp, top = if (first) 0.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(badge, Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0x10FFFFFF)).padding(4.dp),
            style = Body.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = WizardColors.text)
        Text(name, style = Body.copy(fontSize = 13.sp), color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * El descanso de aproximación o de movilidad desplegado dentro de la lista: «AHORA · Descanso» con «Minimizar», la tarjeta del descanso y
 * «Terminar descanso». Minimizado, queda como una fila compacta ([LiveListRestBar]) que se vuelve a desplegar.
 */
@Composable
private fun ListWarmupRest(
    remainingSeconds: Int,
    totalSeconds: Int,
    kicker: String,
    next: Pair<String, String>?,
    onEnd: () -> Unit,
    onMinimize: () -> Unit,
    isPaused: Boolean = false,
    onAdjust: (Int) -> Unit = {},
    onTogglePause: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("live-list-warmup-rest")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AHORA", Modifier.width(52.dp), style = Body.copy(fontSize = 11.sp, letterSpacing = .8.sp), color = WizardColors.textFaint)
            Text("Descanso", Modifier.weight(1f), style = Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
            Row(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp)).clickable(role = Role.Button, onClick = onMinimize)
                    .padding(horizontal = 10.dp).testTag("live-list-rest-minimize"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("Minimizar", style = Body.copy(fontSize = 12.sp), color = WizardColors.textMuted)
                Icon(KpknLineIcons.ChevronUp, null, Modifier.size(14.dp), tint = WizardColors.textMuted)
            }
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
                    isPaused = isPaused,
                    animationsOn = true,
                    onExpand = null,
                    // El descanso de aproximación y movilidad se ajusta igual que el de las series (pedido del usuario del 29-oct).
                    onDecrease = { onAdjust(-15) },
                    onTogglePause = onTogglePause,
                    onIncrease = { onAdjust(15) },
                    nextTitle = next?.first,
                    nextDose = next?.second,
                    suggestion = null,
                    onOpenSuggestion = null,
                )
            }
        }
    }
}

private fun mobilityVolume(mobility: MobilitySeries): String =
    mobility.reps?.let { "${mobility.sets} × $it reps" }
        ?: mobility.durationSeconds?.let { "${mobility.sets} × $it s" }
        ?: if (mobility.sets == 1) "1 serie" else "${mobility.sets} series"

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
            // Destino del vuelo de la capa de voz: destella la fila entera (los dos lados de una unilateral comparten la clave de la serie).
            .voiceFlightTarget(voiceRowTargetKey(row.exercise.id, row.setIndex), VoiceTargetStyle.ROW)
            .heightIn(min = 44.dp)
            .alpha(if (row.completed?.skipped == true) .42f else 1f)
            .drawBehind {
                // `.set-row.active`: el fondo sobresale 5 a cada lado (margin −5 / padding 5) y la barra en tinta va en su borde.
                val bleed = 5.dp.toPx()
                if (active) drawRect(ListActiveFill, Offset(-bleed, 0f), androidx.compose.ui.geometry.Size(size.width + bleed * 2, size.height))
                drawLine(if (active) Color(0x14FFFFFF) else ListRowLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                // La barra va dentro del borde: fuera de él la recorta el contenedor de la fila y no se ve en el teléfono.
                if (active) drawLine(WizardColors.text, Offset(1.dp.toPx(), 10.dp.toPx()),
                    Offset(1.dp.toPx(), size.height - 10.dp.toPx()), 2.dp.toPx())
            }
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Serie ${row.label} de ${row.exercise.name}, ${rowType(row.planned)}" }
            .padding(top = 3.dp, bottom = 3.dp)
            .testTag("live-list-row-${row.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(row.label, Modifier.width(indexWidth), style = Body.copy(fontSize = 12.sp), color = WizardColors.textFaint, textAlign = TextAlign.Center)
        Text(rowType(row.planned), Modifier.weight(1f), style = Body.copy(fontSize = 12.sp),
            color = if (active) WizardColors.text else WizardColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // La cifra siempre con su unidad («100 kg × 8») y coma decimal; la columna crece si la carga lleva decimales.
        Column(Modifier.widthIn(min = valueWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rowValue(row), style = SyneValue.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = if (done) WizardColors.textMuted else WizardColors.text, maxLines = 1)
            row.completed?.partialReps?.takeIf { it > 0 }?.let {
                Text("+ $it parciales", Modifier.padding(top = 3.dp), style = Body.copy(fontSize = 11.sp), color = WizardColors.textFaint)
            }
        }
        // `row-effort` es un botón: abre «Esfuerzo realizado» de esa serie.
        Text(performedEffort(row.completed), Modifier.width(effortWidth).heightIn(min = 36.dp).clickable(role = Role.Button, onClick = onEffort)
            .wrapContentHeight(Alignment.CenterVertically).testTag("live-list-row-effort-${row.key}"),
            style = Body.copy(fontSize = 12.sp), color = WizardColors.textFaint, textAlign = TextAlign.End)
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
    // «Modo visual» se abre en su vista expandida, como en la tarjeta; cada «Ampliar» la vuelve a abrir (pedido del usuario, 29-oct).
    var visualExpandRequest by rememberSaveable(row.key) { mutableIntStateOf(0) }
    var visualAutoExpand by rememberSaveable(row.key) { mutableStateOf(false) }
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
        weightText = listNumber((current + delta).coerceAtLeast(0.0))
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

    // Frontal ↔ Avanzado ↔ Modo visual: la cara nueva se funde sobre la anterior; el alto lo anima el contenedor de la fila.
    val faceMotionReduced = wizardReducedMotion()
    AnimatedContent(targetState = face, transitionSpec = { liveContentSwap(faceMotionReduced) }, label = "live-list-editor-face") { shownFace ->
        if (shownFace == EDITOR_FACE_ADVANCED) {
            // `inline-editor` con `advancedContent()`: la misma cara «Avanzado» de la tarjeta, dentro de la fila.
            Box(Modifier.fillMaxWidth().height(440.dp).listTapGuard().padding(vertical = 6.dp).testTag("live-list-advanced-${row.key}")) {
                com.example.kpkn.screens.workout.components.SetCardTechniqueBack(
                    currentSet = row.planned,
                    coverWeightLabel = if (weightText.isBlank()) "—" else "$weightText kg",
                    coverValueLabel = if (valueText.isBlank()) "—" else "$valueText ${unitLabel(unit)}",
                    coverIntensityLabel = effort?.let { "${modeLabel(mode)} ${listNumber(it)}" },
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
        } else if (shownFace == EDITOR_FACE_VISUAL) {
            // `inline-editor` con `mediaContent()` a 350 de alto: la cámara, el álbum y el análisis de esa serie. Al entrar se abre la vista
            // expandida de la tarjeta (la misma superposición, por encima de todo); al plegarla queda esta cara, con «Ampliar» para volver.
            Column(Modifier.fillMaxWidth().listTapGuard().testTag("live-list-visual-${row.key}")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Modo visual · serie ${row.label}", Modifier.weight(1f).padding(start = 4.dp), style = Body.copy(fontSize = 12.sp),
                    color = WizardColors.textFaint)
                Row(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp)).clickable(role = Role.Button) { visualAutoExpand = true; visualExpandRequest++ }
                        .padding(horizontal = 10.dp).testTag("live-list-visual-expand-${row.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(KpknLineIcons.Camera, null, Modifier.size(14.dp), tint = WizardColors.text)
                    Text("Ampliar", style = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = WizardColors.text)
                }
            }
            Box(Modifier.fillMaxWidth().height(350.dp).padding(bottom = 6.dp)) {
                androidx.compose.runtime.key(visualExpandRequest) {
                com.example.kpkn.screens.workout.components.SetCardExerciseMediaBack(
                    exercise = row.exercise,
                    onFlipBack = { face = EDITOR_FACE_FRONT },
                    startExpanded = visualAutoExpand,
                    setIndex = row.setIndex,
                    side = row.side,
                    weightKg = parseDecimal(weightText)?.takeIf { it > 0.0 },
                    reps = parseDecimal(valueText)?.toInt()?.takeIf { it > 0 },
                    mediaCapture = viewModel.mediaCapture,
                    effortLabel = effort?.let { "${modeLabel(mode)} ${listNumber(it)}" },
                    onShowHistory = { viewModel.showHistoryForExercise(row.exercise) },
                    onOpenAnalysis = media.onOpenAnalysis,
                    videoAnalysis = media.videoAnalysisBySetKey[workoutSetKey(row.exercise.id, row.setIndex,
                        com.example.kpkn.data.media.WorkoutMediaSides.normalize(row.side, row.exercise))],
                    onRetryAnalysis = media.onRetryAnalysis,
                    techniqueVideoSettings = media.techniqueVideoSettings,
                )
                }
            }
            }
            // Pedido consumido: si la fila sale de pantalla y vuelve, la cara no se re-expande sola.
            LaunchedEffect(visualExpandRequest) { if (visualAutoExpand) visualAutoExpand = false }
        } else Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ListEditorFill)
                .drawBehind { drawLine(ListEditorLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                // El editor se queda con sus toques: un hueco entre campos no abre nada de otra fila.
                .listTapGuard()
                .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 5.dp)
                .testTag("live-list-editor-${row.key}"),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // La carga lleva más sitio que la cantidad: «102,5 kg» cabe sin que «kg» se parta en dos líneas.
                Row(Modifier.weight(1.15f), verticalAlignment = Alignment.Bottom) {
                    ListMetricField(weightText, { weightText = it; persist() }, metric.copy(fontSize = 28.sp, lineHeight = 34.sp),
                        Modifier.width(IntrinsicSize.Max), "live-list-weight-${row.key}", "Carga en kg", !busy, placeholder = "—")
                    Text("kg", Modifier.padding(start = 4.dp, bottom = 6.dp), style = Body.copy(fontSize = 13.sp), color = WizardColors.textFaint,
                        maxLines = 1, softWrap = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MiniStep(KpknLineIcons.Minus, "Reducir carga", !busy, "live-list-weight-minus-${row.key}") { stepWeight(-2.5) }
                    MiniStep(KpknLineIcons.Plus, "Aumentar carga", !busy, "live-list-weight-plus-${row.key}") { stepWeight(2.5) }
                }
                Row(Modifier.weight(.85f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                    ListMetricField(valueText, { valueText = it; persist() }, metric.copy(fontSize = 24.sp, lineHeight = 30.sp, textAlign = TextAlign.End),
                        Modifier.width(40.dp), "live-list-value-${row.key}", "Cantidad realizada", !busy)
                    Text(when (unit) { UnitModeV2.TIME -> "s"; UnitModeV2.DISTANCE -> "km"; else -> "reps" }, Modifier.padding(start = 2.dp, bottom = 5.dp),
                        style = Body.copy(fontSize = 13.sp), color = WizardColors.textFaint, maxLines = 1, softWrap = false)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                // La referencia del prototipo: el historial anterior de esa serie, o «Sin historial».
                Text(
                    history?.let { previous ->
                        buildString {
                            append("Anterior · ")
                            if (previous.weight > 0.0) append("${listNumber(previous.weight)} kg × ")
                            append(previous.reps)
                            previous.rir?.let { append(" · RIR $it") } ?: previous.rpe?.let { append(" · RPE ${listNumber(it)}") }
                        }
                    } ?: "Sin historial",
                    Modifier.weight(1f).testTag("live-list-reference-${row.key}"),
                    style = Body.copy(fontSize = 12.sp), color = WizardColors.textFaint,
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
                    style = Body.copy(fontSize = 12.sp), color = WizardColors.textMuted,
                )
            }
            // `.quick-effort`: rótulo del modo, cinco valores, Fallo y «…» con la escala completa.
            Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(modeLabel(mode), Modifier.width(25.dp), style = Body.copy(fontSize = 11.sp), color = WizardColors.textFaint)
                effortOptions(mode).forEach { option ->
                    val picked = if (option == null) failure else !failure && effort == option
                    EffortChip(option?.let(::listNumber) ?: "Fallo", picked, !busy,
                        "live-list-effort-${row.key}-${option?.let(::formatDecimal) ?: "failure"}", Modifier.weight(1f)) {
                        failure = option == null
                        effort = option ?: if (mode == IntensityMode.RPE) 10.0 else 0.0
                        error = null
                        persist()
                    }
                }
                EffortChip("…", false, !busy, "live-list-effort-${row.key}-scale", Modifier.weight(1f)) { showScale = true }
            }
            Text(status, Modifier.fillMaxWidth().padding(vertical = 2.dp), style = Body.copy(fontSize = 12.sp),
                color = if (error != null) Color(0xFFFF9B92) else WizardColors.textFaint, textAlign = TextAlign.Center)
            // `.card-actions` del editor: técnica y opciones de la serie, e historial.
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { face = EDITOR_FACE_ADVANCED }
                    .testTag("live-list-open-advanced-${row.key}"),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(KpknLineIcons.Sliders, null, Modifier.size(15.dp), tint = WizardColors.textMuted)
                    Text(" Avanzado", style = Body.copy(fontSize = 12.sp), color = WizardColors.textMuted)
                }
                Row(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { visualAutoExpand = true; face = EDITOR_FACE_VISUAL }
                    .testTag("live-list-open-visual-${row.key}"),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(KpknLineIcons.Camera, null, Modifier.size(16.dp), tint = WizardColors.textMuted)
                    Text(" Modo visual", style = Body.copy(fontSize = 12.sp), color = WizardColors.textMuted)
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
                    style = Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = WizardColors.ctaContent,
                )
            }
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
            LiveSheetNumberGrid(values.map { it to listNumber(it) }, { !failure && effort == it }, { picked ->
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
            style = Body.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
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

/**
 * La cifra de la fila: «100 kg × 8» (carga × cantidad, pedido del usuario del 29-oct: la carga siempre dice que son kilos), «— kg × 8» sin
 * carga y la unidad si no son repeticiones. Coma decimal es-CL.
 */
private fun rowValue(row: ListSetRow): androidx.compose.ui.text.AnnotatedString {
    val completed = row.completed
    if (completed != null && !completed.skipped) {
        val value = completed.reps.takeIf { it > 0 }?.toString()
            ?: completed.timeSeconds?.let { "$it s" }
            ?: completed.distanceKm?.let { "${listNumber(it)} km" }
            ?: "—"
        return listDose(completed.weight, value)
    }
    val set = row.planned
    val value = when (set.unitModeV2) {
        UnitModeV2.TIME -> set.targetDuration?.let { "$it s" } ?: set.plannedTargetV2?.let { "${listNumber(it)} s" }
        UnitModeV2.DISTANCE -> set.plannedTargetV2?.let { "${listNumber(it)} km" }
        else -> set.targetReps?.toString() ?: set.plannedTargetV2?.let(::listNumber)
    } ?: "—"
    return listDose(set.weight, value)
}

internal fun performedEffort(completed: CompletedSet?): String {
    if (completed == null) return "—"
    if (completed.isFailure) return "Fallo"
    completed.rir?.let { return "RIR $it" }
    completed.rpe?.let { return "RPE ${listNumber(it)}" }
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
    row.completed?.weight?.takeIf { it > 0.0 }?.let(::listNumber)
        ?: row.planned.weight?.takeIf { it > 0.0 }?.let(::listNumber)
        ?: ""

private fun initialValue(row: ListSetRow): String =
    row.completed?.reps?.takeIf { it > 0 }?.toString()
        ?: row.completed?.timeSeconds?.toString()
        ?: row.completed?.distanceKm?.let(::listNumber)
        ?: row.planned.targetReps?.toString()
        ?: row.planned.targetDuration?.toString()
        ?: row.planned.plannedTargetV2?.let(::listNumber)
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
                Text(displayWorkoutExerciseName(exercise), style = SyneValue.copy(fontSize = 16.sp, lineHeight = 21.sp), color = WizardColors.text)
                Text(
                    listOfNotNull(
                        "Cardio",
                        "${details.effectiveDurationSeconds() / 60} min".takeIf { details.effectiveDurationSeconds() > 0 },
                        "${steps.size} ${if (steps.size == 1) "paso" else "pasos"}",
                        if (zones.isNotEmpty()) "Z${zones.minOrNull()} → Z${zones.maxOrNull()}" else null,
                    ).joinToString(" · "),
                    Modifier.padding(top = 3.dp), style = Body.copy(fontSize = 12.5.sp), color = WizardColors.textFaint,
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
            Text(if (done) "  Cardio registrado · abrir" else "  Empezar cardio", style = Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = if (done) WizardColors.text else WizardColors.ctaContent)
        }
    }
}
