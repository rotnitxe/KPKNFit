package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.workout.SetTechniqueScope
import com.example.kpkn.domain.workout.techniqueScope
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.LiveSetPrPolicy
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Una fila de drop-set (peso y repeticiones) o de mini-serie de rest-pause (repeticiones y descanso), como texto. */
internal data class TechniqueCheckRow(
    val weightText: String = "",
    val repsText: String = RestPausePlanDefaults.Reps.toString(),
    val restText: String = RestPausePlanDefaults.PauseSeconds.toString(),
)

private const val LiveDropSetMax = 5

/** Paso de los ± del descanso de una mini-serie, en segundos. */
private const val RestPauseStepSeconds = 5

/**
 * Reverso de técnica de la serie (KPKN LS7b, dentro de la hoja «Técnica de la serie»): drop-sets o rest-pause, notas, error de
 * ejecución y «Guardar técnica». Las filas viven aquí; al guardar, [onCompleteTechniques] recibe lo válido. Si no hay nada válido,
 * se cierra sin técnica.
 */
@Composable
internal fun SetCardTechniqueBack(
    currentSet: ExerciseSet,
    mainWeight: Double,
    mainReps: Int,
    isFailedSet: Boolean,
    onFailedSetChange: (Boolean) -> Unit,
    onFlipBack: () -> Unit,
    onCompleteTechniques: (dropSets: List<DropSetData>, restPauses: List<RestPauseData>) -> Unit,
    noteText: String = "",
    onNoteChange: (String) -> Unit = {},
    initialDropEnabled: Boolean = false,
    initialRestPauseEnabled: Boolean = false,
    onDropEnabledChange: (Boolean) -> Unit = {},
    onRestPauseEnabledChange: (Boolean) -> Unit = {},
    showPrMediaShortcut: Boolean = false,
    onOpenPrMedia: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val plannedGuide = remember(currentSet) {
        // Volume-replaced editor plans run inside the front card controls.
        // Never arm the manual back-face editors for them: the back card is
        // a flip replacement, not an overlay, and must not resurrect a
        // second technique surface on top of the scheduled flow.
        if (currentSet.techniqueScope() == SetTechniqueScope.VOLUME_REPLACED) null
        else currentSet.resolvePlannedTechniqueGuide()
    }
    var dropEnabled by remember(currentSet.id) {
        mutableStateOf(initialDropEnabled || plannedGuide?.kind == TechniqueType.DROP_SET)
    }
    var restPauseEnabled by remember(currentSet.id) {
        mutableStateOf(initialRestPauseEnabled || plannedGuide?.kind == TechniqueType.REST_PAUSE)
    }
    var dropRows by remember(currentSet.id, mainWeight, mainReps) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.DROP_SET }
            ?: DropSetPlanDefaults.DefaultDrops
        val suggestions = suggestedDropLoadsForMainSet(mainWeight, mainReps, count)
        mutableStateOf(
            suggestions.map { load ->
                TechniqueCheckRow(
                    weightText = if (load > 0) load.toTrimmedNumberString() else "",
                    repsText = RestPausePlanDefaults.Reps.toString(),
                )
            }.ifEmpty {
                listOf(TechniqueCheckRow())
            },
        )
    }
    var restPauseRows by remember(currentSet.id) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.REST_PAUSE }
            ?: RestPausePlanDefaults.DefaultCount
        mutableStateOf(
            List(count.coerceIn(RestPausePlanDefaults.MinCount, RestPausePlanDefaults.MaxCount)) {
                TechniqueCheckRow(repsText = RestPausePlanDefaults.Reps.toString())
            },
        )
    }

    LaunchedEffect(dropEnabled) { onDropEnabledChange(dropEnabled) }
    LaunchedEffect(restPauseEnabled) { onRestPauseEnabledChange(restPauseEnabled) }

    SetTechniqueBody(
        dropEnabled = dropEnabled,
        restPauseEnabled = restPauseEnabled,
        onToggleDrop = {
            dropEnabled = !dropEnabled
            if (dropEnabled) restPauseEnabled = false
        },
        onToggleRestPause = {
            restPauseEnabled = !restPauseEnabled
            if (restPauseEnabled) dropEnabled = false
        },
        dropRows = dropRows,
        onDropRowsChange = { dropRows = it },
        onAddDrop = {
            val nextIndex = dropRows.size
            val suggested = suggestedDropLoadsForMainSet(
                mainWeight,
                mainReps,
                (nextIndex + 1).coerceAtMost(DropSetPlanDefaults.MaxDrops),
            ).getOrNull(nextIndex)
                ?: dropRows.lastOrNull()?.weightText?.toDoubleOrNull()?.minus(DropSetPlanDefaults.DropKg)
            dropRows = dropRows + TechniqueCheckRow(
                weightText = suggested?.takeIf { it > 0 }?.toTrimmedNumberString().orEmpty(),
                repsText = DropSetPlanDefaults.DropReps.toString(),
            )
        },
        restPauseRows = restPauseRows,
        onRestPauseRowsChange = { restPauseRows = it },
        onAddRestPause = {
            restPauseRows = restPauseRows + TechniqueCheckRow(
                repsText = RestPausePlanDefaults.Reps.toString(),
                restText = RestPausePlanDefaults.PauseSeconds.toString(),
            )
        },
        noteText = noteText,
        onNoteChange = onNoteChange,
        isFailedSet = isFailedSet,
        onFailedSetChange = onFailedSetChange,
        showPrMediaShortcut = showPrMediaShortcut,
        onOpenPrMedia = onOpenPrMedia,
        onFlipBack = onFlipBack,
        onSave = {
            val saved = commit(dropRows, restPauseRows, dropEnabled, restPauseEnabled, onCompleteTechniques)
            if (!saved) {
                // Sin filas válidas no hay técnica que registrar: se cierra sin drops ni pausas.
                onDropEnabledChange(false)
                onRestPauseEnabledChange(false)
                onFlipBack()
            }
        },
        modifier = modifier,
    )
}

/**
 * Cuerpo sin estado del reverso de técnica (LS7b): chips de drop-sets y rest-pause, sus filas, notas, error de ejecución y
 * «Guardar técnica». Lo pinta la hoja y lo montan las pruebas con datos y lambdas.
 */
@Composable
internal fun SetTechniqueBody(
    dropEnabled: Boolean,
    restPauseEnabled: Boolean,
    onToggleDrop: () -> Unit,
    onToggleRestPause: () -> Unit,
    dropRows: List<TechniqueCheckRow>,
    onDropRowsChange: (List<TechniqueCheckRow>) -> Unit,
    onAddDrop: () -> Unit,
    restPauseRows: List<TechniqueCheckRow>,
    onRestPauseRowsChange: (List<TechniqueCheckRow>) -> Unit,
    onAddRestPause: () -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    isFailedSet: Boolean,
    onFailedSetChange: (Boolean) -> Unit,
    showPrMediaShortcut: Boolean,
    onOpenPrMedia: (() -> Unit)?,
    onFlipBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknIconButton(
                icon = KpknLineIcons.ChevronLeft,
                contentDescription = "Volver a la serie",
                onClick = onFlipBack,
            )
            Text(
                text = "Técnica de la serie",
                style = KpknType.title,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showPrMediaShortcut && onOpenPrMedia != null) {
                KpknChip(
                    text = LiveSetPrPolicy.SHORTCUT_LABEL,
                    selected = false,
                    onClick = onOpenPrMedia,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknChip(
                text = if (dropEnabled) "Drop-sets" else "Añadir drop-sets",
                selected = dropEnabled,
                onClick = onToggleDrop,
            )
            KpknChip(
                text = if (restPauseEnabled) "Rest-pause" else "Añadir rest-pause",
                selected = restPauseEnabled,
                onClick = onToggleRestPause,
            )
        }
        when {
            dropEnabled -> DropRowsModule(
                rows = dropRows,
                onRowsChange = onDropRowsChange,
                onAdd = onAddDrop,
                canAdd = dropRows.size < LiveDropSetMax,
            )
            restPauseEnabled -> RestPauseRowsModule(
                rows = restPauseRows,
                onRowsChange = onRestPauseRowsChange,
                onAdd = onAddRestPause,
                canAdd = restPauseRows.size < RestPausePlanDefaults.MaxCount,
            )
            else -> Text(
                text = "Elige drop-sets o rest-pause",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Notas")
            Spacer(Modifier.height(10.dp))
            KpknTextField(
                value = noteText,
                onValueChange = onNoteChange,
                placeholder = "Añadir una nota de esta serie",
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KpknCheckRow(
            checked = isFailedSet,
            title = "Error de ejecución",
            onClick = { onFailedSetChange(!isFailedSet) },
            modifier = Modifier.fillMaxWidth(),
        )
        KpknPrimaryPill(
            text = "Guardar técnica",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Drop-sets: una fila por drop con su carga y sus repeticiones, y «Añadir drop-set» mientras quepa. */
@Composable
private fun DropRowsModule(
    rows: List<TechniqueCheckRow>,
    onRowsChange: (List<TechniqueCheckRow>) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean,
) {
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Drops")
        Spacer(Modifier.height(10.dp))
        TechniqueCaptionRow(first = "Kilos", second = "Repeticiones")
        rows.forEachIndexed { index, row ->
            if (index > 0) TechniqueDivider()
            // La etiqueta va en su propia línea: así cada celda ocupa la mitad del ancho y «Repeticiones» no se parte al 130 %.
            Text(
                text = "Drop ${index + 1}",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpknNumberCell(
                    value = row.weightText,
                    input = KpknCellInput.Text(
                        keyboardType = KeyboardType.Decimal,
                        onCommit = { typed -> onRowsChange(rows.withRow(index, row.copy(weightText = typed.trim()))) },
                    ),
                    contentDescription = "Kilos del drop ${index + 1}",
                    onStep = { delta ->
                        onRowsChange(rows.withRow(index, row.copy(weightText = stepLoad(row.weightText, delta))))
                    },
                    modifier = Modifier.weight(1f),
                )
                KpknNumberCell(
                    value = row.repsText,
                    input = KpknCellInput.Text(
                        keyboardType = KeyboardType.Number,
                        onCommit = { typed -> onRowsChange(rows.withRow(index, row.copy(repsText = typed.trim()))) },
                    ),
                    contentDescription = "Repeticiones del drop ${index + 1}",
                    onStep = { delta ->
                        onRowsChange(rows.withRow(index, row.copy(repsText = stepCount(row.repsText, delta, step = 1))))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (canAdd) {
            Spacer(Modifier.height(8.dp))
            KpknTextAction(
                text = "Añadir drop-set",
                onClick = onAdd,
                leadingIcon = KpknLineIcons.Plus,
            )
        }
    }
}

/** Rest-pause: una fila por mini-serie con sus repeticiones y su descanso en segundos. */
@Composable
private fun RestPauseRowsModule(
    rows: List<TechniqueCheckRow>,
    onRowsChange: (List<TechniqueCheckRow>) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean,
) {
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Pausas")
        Spacer(Modifier.height(10.dp))
        TechniqueCaptionRow(first = "Repeticiones", second = "Descanso (s)")
        rows.forEachIndexed { index, row ->
            if (index > 0) TechniqueDivider()
            Text(
                text = "Mini-serie ${index + 1}",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpknNumberCell(
                    value = row.repsText,
                    input = KpknCellInput.Text(
                        keyboardType = KeyboardType.Number,
                        onCommit = { typed -> onRowsChange(rows.withRow(index, row.copy(repsText = typed.trim()))) },
                    ),
                    contentDescription = "Repeticiones de la mini-serie ${index + 1}",
                    onStep = { delta ->
                        onRowsChange(rows.withRow(index, row.copy(repsText = stepCount(row.repsText, delta, step = 1))))
                    },
                    modifier = Modifier.weight(1f),
                )
                KpknNumberCell(
                    value = row.restText,
                    input = KpknCellInput.Text(
                        keyboardType = KeyboardType.Number,
                        onCommit = { typed -> onRowsChange(rows.withRow(index, row.copy(restText = typed.trim()))) },
                    ),
                    contentDescription = "Descanso de la mini-serie ${index + 1}, en segundos",
                    onStep = { delta ->
                        onRowsChange(
                            rows.withRow(
                                index,
                                row.copy(restText = stepCount(row.restText, delta, step = RestPauseStepSeconds)),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (canAdd) {
            Spacer(Modifier.height(8.dp))
            KpknTextAction(
                text = "Añadir mini-serie",
                onClick = onAdd,
                leadingIcon = KpknLineIcons.Plus,
            )
        }
    }
}

@Composable
private fun TechniqueCaptionRow(first: String, second: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = first, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
        Text(text = second, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TechniqueDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}

private fun List<TechniqueCheckRow>.withRow(index: Int, row: TechniqueCheckRow): List<TechniqueCheckRow> =
    mapIndexed { i, current -> if (i == index) row else current }

/** Suma un paso con signo a una carga escrita (coma o punto), sin bajar de cero. */
private fun stepLoad(text: String, delta: Int): String {
    val current = text.replace(',', '.').toDoubleOrNull() ?: 0.0
    return (current + delta).coerceAtLeast(0.0).toTrimmedNumberString()
}

/** Suma un paso con signo a una cuenta escrita (repeticiones o segundos), sin bajar de cero. */
private fun stepCount(text: String, delta: Int, step: Int): String =
    ((text.toIntOrNull() ?: 0) + delta * step).coerceAtLeast(0).toString()

private fun commit(
    dropRows: List<TechniqueCheckRow>,
    restPauseRows: List<TechniqueCheckRow>,
    dropEnabled: Boolean,
    restPauseEnabled: Boolean,
    onCompleteTechniques: (List<DropSetData>, List<RestPauseData>) -> Unit,
): Boolean {
    val drops = if (dropEnabled) {
        dropRows.mapNotNull { row ->
            val weight = row.weightText.toDoubleOrNull() ?: return@mapNotNull null
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (weight <= 0.0 || reps <= 0) null else DropSetData(weight, reps)
        }
    } else {
        emptyList()
    }
    val pauses = if (restPauseEnabled) {
        restPauseRows.mapNotNull { row ->
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (reps <= 0) null else RestPauseData(
                restTime = row.restText.toIntOrNull()?.coerceAtLeast(0) ?: RestPausePlanDefaults.PauseSeconds,
                reps = reps,
            )
        }
    } else {
        emptyList()
    }
    if (drops.isEmpty() && pauses.isEmpty()) return false
    onCompleteTechniques(drops, pauses)
    return true
}
