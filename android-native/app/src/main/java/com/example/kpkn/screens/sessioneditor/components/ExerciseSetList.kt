package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatMax2Decimals
import com.example.kpkn.screens.sessioneditor.withPlannedConfigOf
import com.example.kpkn.screens.sessioneditor.withSetDuplicated
import com.example.kpkn.screens.sessioneditor.withSideConfigOf
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/** Cuánto dura el aviso «Serie eliminada · Deshacer» (la misma ventana que el del ejercicio eliminado). */
private const val SET_UNDO_WINDOW_MS = 5_000L

/**
 * `ed-serie` · Series del ejercicio abierto (B1/B2): las fichas de 85 × 76 dp (la última, punteada, es «+ Añadir serie») y un único foco
 * de edición. La serie elegida siempre tiene su foco debajo: «Serie N de M» con ‹ ›, carga, dosis, intensidad (según el modo), descanso
 * y las acciones («Aplicar a todas» y «Duplicar» como botones; Subir, Bajar y «Eliminar serie», con «Deshacer», en el menú ⋯).
 *
 * En unilateral cada serie son dos fichas, L y R (sin el azul y el rojo del original, B5); la que falta es una ficha punteada «R3».
 */
@Composable
internal fun ExerciseSetList(
    exercise: Exercise,
    reference1RM: Double?,
    trainingMode: TrainingMode,
    customUnit: String?,
    predictedMetrics: Map<String, Double?>,
    accentColor: Color,
    backgroundAccentColor: Color,
    modifier: Modifier = Modifier,
    onAddSet: (String?) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onTransformExercise: (((Exercise) -> Exercise) -> Unit)? = null,
    /** Abre la hoja de la carga inteligente: la cifra de carga de cada serie es su botón. */
    onOpenSmartLoad: (() -> Unit)? = null,
) {
    if (exercise.sets.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("No hay series añadidas", style = KpknType.caption, color = WizardColors.textMuted)
            KpknSecondaryPill(text = "+ Añadir serie", onClick = { onAddSet(null) })
        }
        return
    }

    var openKey by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    var pendingApply by remember(exercise.id) { mutableStateOf<PendingApply?>(null) }
    var deletedSet by remember(exercise.id) { mutableStateOf<DeletedSet?>(null) }
    // pulido:ed-A10 `applyok`: tras cerrar la hoja, todas las fichas (menos «+») destellan 0,6 s; cada «Aplicar» sube la cuenta
    var flashCount by remember(exercise.id) { mutableIntStateOf(0) }
    val unilateral = exercise.isEffectivelyUnilateral()
    val orderedSides = if (exercise.unilateralSideOrder == UnilateralSideOrder.RIGHT_LEFT) listOf("R", "L") else listOf("L", "R")
    val sharedSides = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED
    val predictedWeights = remember(exercise.trainingMode, exercise.reference1RM, exercise.referenceTrainingMax, exercise.prFor1RM, exercise.sets) {
        exercise.sets.associate { set -> set.id to calculateSuggestedLoad(exercise, set) }
    }

    /** Pide copiar la configuración de [set] (o de su lado [side]) al resto; si hay series distintas, antes pregunta. */
    fun requestApply(set: ExerciseSet, side: String?) {
        fun applyTo(current: ExerciseSet): ExerciseSet =
            if (side == null) current.withPlannedConfigOf(set) else current.withSideConfigOf(set, side, sharedSides)
        val changed = exercise.sets
            .filter { it.id != set.id && !it.isEmptySlot }
            .filter { applyTo(it) != it }
        if (changed.isEmpty()) return
        pendingApply = PendingApply(count = changed.size) {
            changed.forEach { target ->
                onUpdateSet(target.id) { current -> applyTo(current) }
            }
        }
    }

    /** Añade el lado que falta (L o R) con la carga del otro lado, como «Añadir L/R» del original. */
    fun addSide(set: ExerciseSet, side: String) {
        onUpdateSet(set.id) { current ->
            val source = if (side == "L") current.rightTarget else current.leftTarget
            val default = UnilateralTarget(
                weight = current.weight,
                targetReps = current.targetReps,
                targetRepsRange = current.targetRepsRange,
                targetDuration = current.targetDuration,
                targetValue = current.plannedTargetV2,
                targetRPE = current.targetRPE,
                targetRIR = current.targetRIR,
                intensityMode = current.intensityMode,
            )
            val seed = source ?: default
            if (side == "L") current.copy(leftTarget = current.leftTarget ?: seed) else current.copy(rightTarget = current.rightTarget ?: seed)
        }
    }

    val slots = buildList<Triple<ExerciseSet, Int, String?>> {
        exercise.sets.forEachIndexed { index, set ->
            if (!unilateral || set.isEmptySlot) add(Triple(set, index, null))
            else orderedSides.filter { sideTarget(set, it) != null }.forEach { add(Triple(set, index, it)) }
        }
    }
    fun keyOf(set: ExerciseSet, side: String?) = if (side == null) set.id else "${set.id}:$side"
    val selectable = slots.filter { (set, _, _) -> !set.isEmptySlot }
    // La serie elegida siempre tiene su foco (ex.sel del lienzo): si no hay ninguna elegida, la primera.
    val selectedSlot = selectable.firstOrNull { (set, _, side) -> keyOf(set, side) == openKey } ?: selectable.firstOrNull()
    val selectedPosition = selectedSlot?.let { selectable.indexOf(it) } ?: -1

    @Composable
    fun SetFocus(set: ExerciseSet, index: Int, side: String?) {
        val rowKey = keyOf(set, side)
        val predictedWeight = predictedWeights[set.id]
        val estimatedMetric = predictedMetrics[set.id]
        val lineColor = Color.White.copy(alpha = 0.063f)
        Column(
            modifier = Modifier
                // pulido:ed-A06 .ed-focus.anim ed-in 0,26 s --ed-ease (6 dp hacia arriba con fundido) al elegir otra serie o cambiar Fijas/Rango
                .edEnterOnChange(rowKey to (set.targetRepsRange != null))
                .fillMaxWidth()
                .testTag("set-focus-$rowKey")
                // .ed-focus: filete superior de 1 dp (#ffffff10) y 12 dp de aire antes del primer dato
                .drawBehind { drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                .padding(top = 13.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    "Serie ${index + 1} de ${exercise.sets.size}${side?.let { " · $it" } ?: ""}",
                    style = edText(14f, FontWeight.SemiBold, EdColors.ink, 17.5f),
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.CenterVertically, unbounded = true),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                ) {
                    val previous = selectable.getOrNull(selectedPosition - 1)
                    val next = selectable.getOrNull(selectedPosition + 1)
                    SeriesNavButton(KpknLineIcons.ChevronLeft, "Serie anterior", previous != null) {
                        previous?.let { (s, _, sd) -> openKey = keyOf(s, sd) }
                    }
                    SeriesNavButton(KpknLineIcons.ChevronRight, "Serie siguiente", next != null) {
                        next?.let { (s, _, sd) -> openKey = keyOf(s, sd) }
                    }
                }
            }
            if (set.purpose.isAmrap) {
                AmrapSetFocus(exercise, set, index, fixedUnilateralSide = side, onOpenSmartLoad = onOpenSmartLoad) { updater -> onUpdateSet(set.id, updater) }
            } else if (trainingMode == TrainingMode.RM) {
                RmSetFocus(exercise, set, { updater -> onUpdateSet(set.id, updater) }, onTransformExercise, fixedUnilateralSide = side, onOpenSmartLoad = onOpenSmartLoad)
            } else InlineSetRow(
                set = set,
                index = index,
                reference1RM = reference1RM,
                trainingMax = exercise.referenceTrainingMax,
                predictedWeight = predictedWeight,
                estimatedMetric = estimatedMetric,
                trainingMode = trainingMode,
                customUnit = customUnit,
                accentColor = accentColor,
                backgroundAccentColor = backgroundAccentColor,
                canMoveUp = index > 0,
                canMoveDown = index < exercise.sets.lastIndex,
                isUnilateral = unilateral,
                fixedUnilateralSide = side,
                showSetActions = false,
                unilateralIntensityMode = exercise.unilateralIntensityMode,
                onUpdate = { updater -> onUpdateSet(set.id, updater) },
                onRemove = { onRemoveSet(set.id) },
                onMoveUp = { onMoveSet(set.id, -1) },
                onMoveDown = { onMoveSet(set.id, 1) },
                fillHeight = false,
                presentation = SetRowPresentation.List,
                onOpenSmartLoad = onOpenSmartLoad,
            )
            SetRestControl(
                seconds = set.restAfterSeconds ?: exercise.restTime ?: 90,
                inherited = set.restAfterSeconds == null,
                onChange = { seconds -> onUpdateSet(set.id) { it.copy(restAfterSeconds = seconds) } },
                onReset = { onUpdateSet(set.id) { it.copy(restAfterSeconds = null) } },
                exerciseSeconds = exercise.restTime ?: 90,
                stateKey = "set-rest-${exercise.id}-$rowKey",
            )
            SetActions(
                canApply = exercise.sets.size > 1,
                canDuplicate = onTransformExercise != null,
                canMoveUp = index > 0,
                canMoveDown = index < exercise.sets.lastIndex,
                canDelete = exercise.sets.size > 1,
                onApply = { requestApply(set, side) },
                onDuplicate = { onTransformExercise?.invoke { current -> current.withSetDuplicated(set.id) } },
                onMoveUp = { onMoveSet(set.id, -1) },
                onMoveDown = { onMoveSet(set.id, 1) },
                onDelete = {
                    val position = exercise.sets.indexOfFirst { it.id == set.id }
                    if (onTransformExercise != null && position >= 0) deletedSet = DeletedSet(set, position)
                    openKey = null
                    onRemoveSet(set.id)
                },
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // .ed-chips: 2 dp de aire por arriba y por abajo
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SessionEditorChipMetrics.gap),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp),
        ) {
            items(slots, key = { (set, _, side) -> keyOf(set, side) }) { (set, index, side) ->
                val rowKey = keyOf(set, side)
                val summary = setRowSummary(set, side, trainingMode, customUnit, predictedWeights[set.id], predictedMetrics[set.id])
                EffectiveSeriesChip(
                    label = if (side == null) "S${index + 1}" else "${sideLetter(side)}${index + 1}",
                    load = if (set.isEmptySlot) "Hueco" else seriesChipValue(set, side, trainingMode, customUnit, predictedWeights[set.id]),
                    dose = if (set.isEmptySlot) "vacío" else seriesChipDetail(set, side, trainingMode, customUnit, summary),
                    description = listOfNotNull("Serie ${index + 1}", summary.load, summary.dose, summary.intensity, summary.technique).joinToString(" · "),
                    enabled = !set.isEmptySlot,
                    selected = selectedSlot != null && keyOf(selectedSlot.first, selectedSlot.third) == rowKey,
                    tag = "set-row-$rowKey",
                    flashKey = flashCount,
                    onClick = { if (!set.isEmptySlot) openKey = rowKey },
                )
            }
            if (unilateral) {
                // Lo que falta de una serie unilateral (L o R) es una ficha punteada con su nombre: «R3».
                items(
                    exercise.sets.withIndex().filterNot { it.value.isEmptySlot }.flatMap { (index, set) ->
                        orderedSides.filter { sideTarget(set, it) == null }.map { Triple(set, index, it) }
                    },
                    key = { (set, _, side) -> "missing:${set.id}:$side" },
                ) { (set, index, side) ->
                    EditorAddChip(
                        label = "${sideLetter(side)}${index + 1}",
                        width = 64.dp,
                        plusSize = 18.dp,
                        description = "Añadir ${sideLetter(side)} a la serie ${index + 1}",
                        tag = "set-add-side-${set.id}-$side",
                        onClick = { addSide(set, side) },
                    )
                }
            }
            item(key = "add-set") {
                EditorAddChip(
                    label = null,
                    width = 56.dp,
                    plusSize = 22.dp,
                    description = "Añadir serie",
                    tag = "set-add",
                    onClick = { onAddSet(null) },
                )
            }
        }
        deletedSet?.let { removed ->
            LaunchedEffect(removed) {
                delay(SET_UNDO_WINDOW_MS)
                if (deletedSet === removed) deletedSet = null
            }
            SetDeletedNotice(onUndo = {
                onTransformExercise?.invoke { current -> current.withSetRestored(removed.set, removed.index) }
                deletedSet = null
            })
        }
        selectedSlot?.let { (set, index, side) -> SetFocus(set, index, side) }
    }

    pendingApply?.let { request ->
        EditorConfirmationSheet(
            onDismissRequest = { pendingApply = null },
            title = "Aplicar a todas",
            text = if (request.count == 1) {
                "Otra serie cambiará para quedar igual que esta. ¿Aplicar?"
            } else {
                "${request.count} series cambiarán para quedar iguales que esta. ¿Aplicar?"
            },
            confirmLabel = "Aplicar",
            onConfirm = {
                request.onApply()
                pendingApply = null
                flashCount += 1
            },
            dismissLabel = "Cancelar",
            onDismiss = { pendingApply = null },
        )
    }
}

/** Flecha de 44 dp de «Serie N de M» (disabled = `--dim`). */
@Composable
private fun SeriesNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        EdIcon(icon, 20.dp, if (enabled) EdColors.ink else EdColors.dim)
    }
}

/**
 * `.ed-acts`: «Aplicar a todas» y «Duplicar» como botones con icono (crecen para repartirse el ancho) y, al final, «⋯» con Subir, Bajar y,
 * separada, «Eliminar serie» en rosa. Subir se desactiva en la primera serie, Bajar en la última y Eliminar cuando solo queda una.
 */
@Composable
private fun SetActions(
    canApply: Boolean,
    canDuplicate: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onApply: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    EdFlexRow(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), gap = 8.dp, lineGap = 8.dp) {
        if (canApply) EdActionButton("Aplicar a todas", EdIcons.CopyAll, onApply, Modifier.edFlex(grow = true))
        if (canDuplicate) EdActionButton("Duplicar", EdIcons.Dupe, onDuplicate, Modifier.edFlex(grow = true))
        EdActionButton(
            text = null,
            icon = KpknLineIcons.More,
            onClick = { menuOpen = !menuOpen },
            modifier = Modifier.edFlex(autoStart = true).edAnchor { anchor = it },
            description = "Más acciones de la serie",
            iconOnly = true,
            expanded = menuOpen,
            iconTint = EdColors.ink,
        )
    }
    val bounds = anchor
    if (menuOpen && bounds != null) {
        EdAnchoredMenu(anchor = bounds, width = 220.dp, estimatedHeight = 200.dp, onDismiss = { menuOpen = false }, alignEnd = true) {
            EdMenuRow("Subir", onClick = { menuOpen = false; onMoveUp() }, icon = EdIcons.ArrowUp, enabled = canMoveUp)
            EdMenuRow("Bajar", onClick = { menuOpen = false; onMoveDown() }, icon = EdIcons.ArrowDown, enabled = canMoveDown)
            EdMenuDivider()
            EdMenuRow(
                title = "Eliminar serie",
                subtitle = if (canDelete) null else "Siempre queda una serie",
                icon = KpknLineIcons.Trash,
                danger = true,
                enabled = canDelete,
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/** «Serie eliminada · Deshacer» (función nueva de la decisión «Eliminar serie gana Deshacer»): 5 s, bajo las fichas. */
@Composable
private fun SetDeletedNotice(onUndo: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Serie eliminada", style = edText(14f, FontWeight.SemiBold, EdColors.muted, 17.5f), modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.063f), shape)
                .clickable(role = Role.Button, onClick = onUndo)
                .semantics { contentDescription = "Deshacer" }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Deshacer", style = edText(14f, FontWeight.SemiBold, EdColors.ink, 17.5f), maxLines = 1)
        }
    }
}

/** Confirmación pendiente de «Aplicar a todas»: cuántas series cambian y la copia que se hace al confirmar. */
private class PendingApply(val count: Int, val onApply: () -> Unit)

/** La serie que acaba de eliminarse y el sitio que ocupaba, para devolverla con «Deshacer». */
private class DeletedSet(val set: ExerciseSet, val index: Int)

/**
 * Devuelve [set] a su sitio tras «Deshacer»: si en su lugar quedó una ranura vacía (rondas de superserie) la sustituye; si no, la
 * inserta en [index]. Si la serie ya está, no hace nada.
 */
internal fun Exercise.withSetRestored(set: ExerciseSet, index: Int): Exercise {
    val slot = sets.indexOfFirst { it.id == set.id }
    if (slot >= 0 && !sets[slot].isEmptySlot) return this
    val restored = sets.toMutableList()
    if (slot >= 0) restored[slot] = set else restored.add(index.coerceIn(0, restored.size), set)
    return copy(sets = restored)
}

private fun sideTarget(set: ExerciseSet, side: String): UnilateralTarget? =
    if (side == "L") set.leftTarget else set.rightTarget

private fun sideLetter(side: String): String = if (side == "L") "L" else "R"

/** En la ficha cabe la cifra; al abrir el foco se editan todos los detalles. La semántica conserva la dosis completa. */
private fun seriesChipValue(set: ExerciseSet, side: String?, mode: TrainingMode, customUnit: String?, predicted: Double?): String {
    val target = side?.let { sideTarget(set, it) }
    return when (mode) {
        TrainingMode.SOLO_RPE -> (target?.targetRPE ?: set.targetRPE)?.let { "RPE\u00a0${formatSummaryNumber(it)}" } ?: "—"
        // Lienzo (ed-serie-tiempo): la duración de la ficha es «0:40», como el resto de tiempos del editor.
        TrainingMode.TIME -> (target?.targetDuration ?: set.targetDuration)?.let { KpknSetFormat.rest(it) } ?: "—"
        TrainingMode.DISTANCE, TrainingMode.CUSTOM -> (target?.targetValue ?: set.plannedTargetV2)?.let { formatSummaryNumber(it) } ?: "—"
        else -> if (set.loadModeV2 == LoadModeV2.BODYWEIGHT) "Corporal"
            else (target?.weight ?: set.weight ?: predicted)?.let {
                "${if (set.loadModeV2 == LoadModeV2.LASTRE) "+" else ""}${formatSummaryNumber(it)}\u00a0kg"
            } ?: "—"
    }
}

private fun seriesChipDetail(set: ExerciseSet, side: String?, mode: TrainingMode, customUnit: String?, summary: SetRowSummary): String {
    val target = side?.let { sideTarget(set, it) }
    val dose = when {
        set.purpose.isAmrap -> set.amrapMinimumReps?.let { "$it+" } ?: "AMRAP"
        mode == TrainingMode.RM -> set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %${if (set.loadBasis == com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM) "TM" else "RM"}" } ?: "—"
        mode == TrainingMode.SOLO_RPE -> "objetivo"
        mode == TrainingMode.TIME -> summary.intensity.orEmpty()
        mode == TrainingMode.DISTANCE -> "m"
        mode == TrainingMode.CUSTOM -> customUnit?.ifBlank { "Unidad" } ?: "Unidad"
        else -> {
            target?.targetRepsRange?.format()
                ?: target?.targetReps?.toString()
                ?: set.targetRepsRange?.format()
                ?: set.targetReps?.toString()
                ?: "—"
        }
    }
    return if (mode == TrainingMode.REPS && !set.purpose.isAmrap) listOfNotNull(dose, summary.intensity).joinToString(" · ") else dose
}

internal data class SetRowSummary(
    val dose: String,
    val load: String?,
    val intensity: String?,
    val technique: String?,
)

/**
 * Resume una serie (o su lado) con la misma lógica que la fila: la carga sugerida si la hay, las reps o el rango, el tiempo o la
 * distancia según el modo, y la intensidad y la técnica. Los decimales van con coma y las unidades con espacio inseparable.
 */
internal fun setRowSummary(
    set: ExerciseSet,
    side: String?,
    mode: TrainingMode,
    customUnit: String?,
    predictedWeight: Double?,
    estimatedMetric: Double?,
): SetRowSummary {
    val target: UnilateralTarget? = if (side == null) null else sideTarget(set, side)
    fun <T> pick(fromSet: T?, fromTarget: T?): T? = if (target != null) fromTarget else fromSet
    val isAmrap = set.purpose.isAmrap
    val rpe = pick(set.targetRPE, target?.targetRPE)
    val rir = pick(set.targetRIR, target?.targetRIR)
    // ER2a: la fila enseña la carga guardada; la calculada solo aparece si la serie no tiene ninguna.
    val weight = pick(set.weight, target?.weight)?.takeIf { it > 0.0 } ?: predictedWeight
    val reps = pick(set.targetReps, target?.targetReps)
    val range = pick(set.targetRepsRange, target?.targetRepsRange)
    val duration = pick(set.targetDuration, target?.targetDuration)
    val value = pick(set.plannedTargetV2, target?.targetValue)
    val intensityMode = target?.intensityMode ?: set.intensityMode

    val kgText = weight?.let { "${formatSummaryNumber(it)} kg" }
    val dose = when (mode) {
        // ER2a: una serie RM con rango o RIR enseña su prescripción («95 kg × 4–6 · RIR 2»); la pura, como antes («95 kg × 7»).
        TrainingMode.RM -> if (range != null || rir != null) {
            rmPrescriptionDose(kgText, range, reps, rir, estimatedMetric?.toInt())
        } else {
            val estimated = estimatedMetric?.toInt()
            listOfNotNull(kgText, estimated?.let { "× $it" }).joinToString(" ").ifBlank {
                set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %" } ?: "—"
            }
        }
        TrainingMode.SOLO_RPE -> "Solo RPE"
        TrainingMode.TIME -> duration?.let { KpknSetFormat.doseTime(it) } ?: "—"
        TrainingMode.DISTANCE -> value?.let { "${formatSummaryNumber(it)} m" } ?: "—"
        TrainingMode.CUSTOM -> value?.let {
            "${formatSummaryNumber(it)} ${customUnit?.takeIf { unit -> unit.isNotBlank() } ?: "u"}"
        } ?: "—"
        else -> {
            val repsText = if (isAmrap) {
                set.amrapMinimumReps?.let { "$it+" } ?: "AMRAP"
            } else {
                range?.format() ?: reps?.toString()
            }
            when {
                kgText != null && repsText != null -> "$kgText × $repsText"
                kgText != null -> kgText
                repsText != null -> repsText
                else -> "—"
            }
        }
    }

    val intensity: String? = when {
        mode == TrainingMode.RM -> set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %" }
        mode == TrainingMode.SOLO_RPE -> rpe?.let { "RPE\u00A0${formatSummaryNumber(it)}" }
        isAmrap -> null
        else -> when (intensityMode ?: IntensityMode.RPE) {
            IntensityMode.RPE -> rpe?.let { "RPE\u00A0${formatSummaryNumber(it)}" }
            IntensityMode.RIR -> rir?.let { "RIR\u00A0$it" }
            IntensityMode.FAILURE -> "Fallo"
            IntensityMode.SOLO_RM -> set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %" }
            IntensityMode.AMRAP, IntensityMode.LOAD -> null
        }
    }

    val load = when (set.loadModeV2 ?: LoadModeV2.LOAD) {
        LoadModeV2.LOAD -> null
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre"
        LoadModeV2.ASSISTED -> "Asistido"
    }

    return SetRowSummary(
        dose = dose,
        load = load,
        intensity = intensity,
        technique = techniqueSummary(set),
    )
}

/** «Drop × 2» o «RP × 3», con el número de drops o de mini-series que tiene la serie. */
private fun techniqueSummary(set: ExerciseSet): String? {
    val drop = set.plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    if (drop != null) {
        val count = drop.params["count"]?.toIntOrNull()
            ?: drop.params["weightPcts"]?.split(",")?.size
            ?: DropSetPlanDefaults.DefaultDrops
        return "Drop × $count"
    }
    val restPause = set.plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE } ?: return null
    val count = restPause.params["count"]?.toIntOrNull() ?: RestPausePlanDefaults.DefaultCount
    return "RP × $count"
}

private fun formatSummaryNumber(value: Double): String = formatMax2Decimals(value).replace('.', ',')

/**
 * Dosis de una serie RM con rango o RIR (ER2a): la carga guardada, las reps del rango («× 4–6») y el RIR («· RIR 2»). Sin rango usa
 * las reps que salen al %RM, como la dosis pura.
 */
private fun rmPrescriptionDose(kgText: String?, range: RepRange?, reps: Int?, rir: Int?, estimated: Int?): String {
    val repsText = range?.format() ?: reps?.toString() ?: estimated?.toString()
    val head = if (kgText != null && repsText != null) "$kgText × $repsText" else kgText ?: repsText
    // «RIR 2» con espacio inseparable: al partir la dosis a dos líneas, la cifra no se queda sola en la segunda.
    return listOfNotNull(head, rir?.let { "RIR $it" }).joinToString(" · ").ifBlank { "—" }
}
