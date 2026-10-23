package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.RestBundleDialog
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.withTrainingMode
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknType

/**
 * Barra de opciones del ejercicio desplegado (ER1c1, R1 §4.2): una fila de píldoras con rótulo que pasa a una segunda línea si no cabe.
 * «Modo» abre la hoja de los 7 modos; «Descanso» abre el mismo selector encadenado del original; y «⋯» reúne lo que quedaba
 * escondido (lateralidad, ROM, relación, superserie, unidad, carga inteligente y meta). Bilateral es el valor por defecto (B1).
 */
@Composable
internal fun ExerciseOptionsBar(
    exercise: Exercise,
    local: ExerciseEditorLocalState,
    accentColor: Color,
    isSupersetExercise: Boolean,
    suppressIndividualRest: Boolean,
    relationshipAnchorName: String?,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onOpenSuperset: (() -> Unit)?,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onOpenCustomUnit: () -> Unit,
    onOpenSmartLoad: () -> Unit,
    onOpenGoal: (() -> Unit)?,
    onOpenRest: () -> Unit,
) {
    var restSelectionSeconds by local.restSelectionSeconds
    var showExerciseOptionsMenu by local.showExerciseOptionsMenu
    var showModeSheet by remember { mutableStateOf(false) }
    var showLateralitySheet by remember { mutableStateOf(false) }
    var showApreSheet by remember { mutableStateOf(false) }
    val unilateral = exercise.isEffectivelyUnilateral()
    val chainWithSides = unilateral && !isSupersetExercise

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KpknChip(
            text = "Modo: ${trainingModeLabel(exercise.trainingMode)} ▾",
            selected = false,
            onClick = { showModeSheet = true },
        )
        if (!suppressIndividualRest) {
            KpknChip(
                text = if (chainWithSides) {
                    "Series ${KpknSetFormat.rest(restSelectionSeconds)} · Lados ${KpknSetFormat.rest(exercise.restBetweenSidesSeconds ?: 0)}"
                } else {
                    "Descanso ${KpknSetFormat.rest(restSelectionSeconds)}"
                },
                selected = false,
                onClick = onOpenRest,
            )
        }
        Box {
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Más opciones del ejercicio",
                onClick = { showExerciseOptionsMenu = true },
            )
            if (showExerciseOptionsMenu) KpknSheet(onDismissRequest = { showExerciseOptionsMenu = false }) {
                Text("Opciones del ejercicio", style = KpknType.dialogTitle, color = WizardColors.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                // B1/B8: bilateral es el valor por defecto; la lateralidad vive aquí, no como un segmentado fijo en la barra.
                DropdownMenuItem(
                    text = { Text("Lateralidad: ${if (unilateral) "Unilateral" else "Bilateral"}") },
                    onClick = {
                        showExerciseOptionsMenu = false
                        showLateralitySheet = true
                    },
                )
                if (exercise.trainingMode in listOf(TrainingMode.REPS, TrainingMode.RM, TrainingMode.AMRAP)) {
                    DropdownMenuItem(text = { Text("Bloque APRE") }, onClick = {
                        showExerciseOptionsMenu = false
                        showApreSheet = true
                    })
                }
                DropdownMenuItem(
                    text = { Text(if (exercise.trackRom) "Desactivar medir ROM" else "Medir ROM") },
                    onClick = {
                        showExerciseOptionsMenu = false
                        onUpdateExercise { current -> current.copy(trackRom = !current.trackRom) }
                    },
                )
                DropdownMenuItem(
                    text = { Text(relationshipAnchorName?.let { "Ancla: $it" } ?: "Relacionar ejercicio") },
                    onClick = {
                        showExerciseOptionsMenu = false
                        if (exercise.relativeToCanonicalExerciseId == null) {
                            onOpenRelationshipPicker()
                        } else {
                            onClearRelationship()
                        }
                    },
                )
                // ER1f: sin callback (un miembro de la superserie configurándose) no hay ítem que ofrecer.
                if (onOpenSuperset != null) {
                    DropdownMenuItem(
                        text = { Text(if (exercise.isInSuperset()) "Gestionar superserie" else "Superserie") },
                        onClick = {
                            showExerciseOptionsMenu = false
                            onOpenSuperset()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Unidad personalizada") },
                    onClick = {
                        showExerciseOptionsMenu = false
                        onOpenCustomUnit()
                    },
                )
                if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                    DropdownMenuItem(
                        text = { Text("Carga inteligente") },
                        onClick = {
                            showExerciseOptionsMenu = false
                            onOpenSmartLoad()
                        },
                    )
                }
                if (onOpenGoal != null) {
                    DropdownMenuItem(
                        text = { Text("Meta") },
                        onClick = {
                            showExerciseOptionsMenu = false
                            onOpenGoal()
                        },
                    )
                }
            }
        }
    }

    if (showModeSheet) {
        ExerciseModeSheet(
            current = exercise.trainingMode,
            onSelect = { mode ->
                showModeSheet = false
                // B4: el modo se aplica con `withTrainingMode` para que AMRAP conserve propósito y mínimo.
                onUpdateExercise { current -> current.withTrainingMode(mode) }
            },
            onDismiss = { showModeSheet = false },
        )
    }

    if (showLateralitySheet) {
        LateralitySheet(
            exercise = exercise,
            onUpdateExercise = onUpdateExercise,
            onDismiss = { showLateralitySheet = false },
        )
    }
    if (showApreSheet) ApreBlockSheet(exercise, onUpdateExercise) { showApreSheet = false }
}

/**
 * Selector de descansos encadenado del original (R1 §4.2): el descanso de las series y, en unilateral, el de entre lados. Lo abren la
 * barra de opciones («Descanso» o «Series · Lados») y el módulo «Lados» («Entre lados»). Guarda igual que el botón de descansos original.
 */
@Composable
internal fun ExerciseRestChain(
    exercise: Exercise,
    local: ExerciseEditorLocalState,
    accentColor: Color,
    isSupersetExercise: Boolean,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var restSelectionSeconds by local.restSelectionSeconds
    val chainWithSides = exercise.isEffectivelyUnilateral() && !isSupersetExercise
    RestBundleDialog(
        primaryLabel = if (chainWithSides) "Series" else "Descanso",
        initialPrimarySeconds = restSelectionSeconds,
        initialSideSeconds = if (chainWithSides) exercise.restBetweenSidesSeconds ?: 0 else null,
        accentColor = accentColor,
        onDismiss = onDismiss,
        onConfirm = { primary, side ->
            restSelectionSeconds = primary
            onUpdateExercise { draft ->
                draft.copy(
                    restTime = primary,
                    restBetweenSidesSeconds = if (draft.isEffectivelyUnilateral()) {
                        side ?: draft.restBetweenSidesSeconds
                    } else {
                        side?.takeIf { it > 0 }
                    },
                )
            }
            onDismiss()
        },
    )
}

/** Los 7 modos del original, cada uno con una línea que dice qué es (R1 §4.2). */
private val exerciseModeOptions: List<Triple<TrainingMode, String, String>> = listOf(
    Triple(TrainingMode.REPS, "Reps", "Repeticiones fijas o un rango, como 8–10."),
    Triple(TrainingMode.AMRAP, "AMRAP", "Todas las repeticiones posibles, con un mínimo."),
    Triple(TrainingMode.RM, "RM", "La carga como % de tu máximo."),
    Triple(TrainingMode.SOLO_RPE, "Solo RPE", "Sin carga fija: la guía el esfuerzo."),
    Triple(TrainingMode.TIME, "Tiempo", "Series por duración, como 45 s."),
    Triple(TrainingMode.DISTANCE, "Distancia", "Series por distancia recorrida."),
    Triple(TrainingMode.CUSTOM, "Personalizado", "Con tu propia unidad de medida."),
)

/**
 * Hoja «Modo» (R1 §4.2 / B4): elegir un modo solo lo marca; «Aplicar modo» confirma antes de tocarlo y lo hace con
 * [withTrainingMode], que conserva el propósito AMRAP y su mínimo al entrar en AMRAP y los limpia al salir.
 */
@Composable
internal fun ExerciseModeSheet(
    current: TrainingMode,
    onSelect: (TrainingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
    KpknSheet(onDismissRequest = onDismiss) {
        ExerciseModeSheetBody(
            current = current,
            selected = selected,
            onSelect = { selected = it },
            onConfirm = {
                if (selected != current) onSelect(selected) else onDismiss()
            },
        )
    }
}

/** Contenido de la hoja de modos, sin el marco de la hoja, para poder pintarlo aparte. */
@Composable
internal fun ExerciseModeSheetBody(
    current: TrainingMode,
    selected: TrainingMode = current,
    onSelect: (TrainingMode) -> Unit,
    onConfirm: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Modo del ejercicio", style = KpknType.dialogTitle, color = WizardColors.text)
        Text("Cómo se prescriben sus series", style = KpknType.caption, color = WizardColors.textMuted)
        exerciseModeOptions.forEach { (mode, label, description) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(description, style = KpknType.caption, color = WizardColors.textMuted)
                }
                if (mode == selected) {
                    Icon(
                        imageVector = KpknLineIcons.Check,
                        contentDescription = if (mode == current) "Modo actual" else "Modo elegido",
                        tint = WizardColors.text,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        KpknPrimaryPill(
            text = if (selected == current) "Listo" else "Aplicar ${trainingModeLabel(selected)}",
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

/**
 * Hoja de lateralidad (B1/B8). Bilateral es el valor por defecto y esta hoja es la única puerta para cambiarlo;
 * en unilateral ofrece el orden L/R (nunca I/D) sin salir de aquí.
 */
@Composable
internal fun LateralitySheet(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        LateralitySheetBody(
            exercise = exercise,
            onUpdateExercise = onUpdateExercise,
            onDismiss = onDismiss,
        )
    }
}

/** Contenido de la hoja de lateralidad, sin el marco, para pruebas y renders. */
@Composable
internal fun LateralitySheetBody(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    val unilateral = exercise.isEffectivelyUnilateral()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Lateralidad", style = KpknType.dialogTitle, color = WizardColors.text)
        Text("Si el ejercicio se hace con los dos lados a la vez o de a uno", style = KpknType.caption, color = WizardColors.textMuted)
        listOf(
            false to ("Bilateral" to "Los dos lados a la vez, una sola serie."),
            true to ("Unilateral" to "Un lado por serie, con registro L y R."),
        ).forEach { (isUnilateral, option) ->
            val (label, description) = option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable {
                        if (isUnilateral != unilateral) {
                            onUpdateExercise { current -> current.toggledBilateralUnilateral() }
                        }
                        onDismiss()
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(description, style = KpknType.caption, color = WizardColors.textMuted)
                }
                if (isUnilateral == unilateral) {
                    Icon(
                        imageVector = KpknLineIcons.Check,
                        contentDescription = "Lateralidad actual",
                        tint = WizardColors.text,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (unilateral) {
            Text(
                "ORDEN DE LADOS",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip(
                    text = "L → R",
                    selected = exercise.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT,
                    onClick = {
                        onUpdateExercise { it.copy(unilateralSideOrder = UnilateralSideOrder.LEFT_RIGHT) }
                        onDismiss()
                    },
                )
                KpknChip(
                    text = "R → L",
                    selected = exercise.unilateralSideOrder == UnilateralSideOrder.RIGHT_LEFT,
                    onClick = {
                        onUpdateExercise { it.copy(unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT) }
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Módulo «Lados» de un ejercicio unilateral (R1 §4.2): orden L/R, lados iguales o aparte y el descanso entre lados, en un solo módulo.
 */
@Composable
internal fun SideOptionsModule(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onOpenSideRest: () -> Unit,
) {
    val shared = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("LADOS", style = KpknType.eyebrow, color = WizardColors.textMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknChip(
                text = if (exercise.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) "Orden L/R" else "Orden R/L",
                selected = false,
                onClick = {
                    onUpdateExercise { current ->
                        current.copy(
                            unilateralSideOrder = if (current.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                UnilateralSideOrder.RIGHT_LEFT
                            } else {
                                UnilateralSideOrder.LEFT_RIGHT
                            },
                        )
                    }
                },
            )
            KpknChip(
                text = if (shared) "Lados iguales" else "Lados aparte",
                selected = shared,
                onClick = {
                    onUpdateExercise { current ->
                        current.copy(
                            unilateralIntensityMode = if (current.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                                UnilateralIntensityMode.INDEPENDENT
                            } else {
                                UnilateralIntensityMode.SHARED
                            },
                        )
                    }
                },
            )
            KpknChip(
                text = "Entre lados ${KpknSetFormat.rest(exercise.restBetweenSidesSeconds ?: 0)}",
                selected = false,
                onClick = onOpenSideRest,
            )
        }
    }
}

/** Módulo «Carga inteligente» de una línea (R1 §4.2). No sale en Solo RPE. */
@Composable
internal fun SmartLoadModule(
    accentColor: Color,
    onOpen: () -> Unit,
) {
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = KpknLineIcons.Training,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Carga inteligente",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = KpknLineIcons.ChevronRight,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Módulo «Unidad personalizada» del modo Personalizado (R1 §4.2): abre el diálogo de la unidad. */
@Composable
internal fun CustomUnitModule(
    customUnit: String,
    onOpen: () -> Unit,
) {
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("UNIDAD PERSONALIZADA", style = KpknType.eyebrow, color = WizardColors.textMuted)
        Text(
            customUnit.ifBlank { "Presiona para configurar" },
            style = KpknType.bodyStrong,
            color = if (customUnit.isBlank()) WizardColors.textMuted else WizardColors.text,
        )
    }
}
