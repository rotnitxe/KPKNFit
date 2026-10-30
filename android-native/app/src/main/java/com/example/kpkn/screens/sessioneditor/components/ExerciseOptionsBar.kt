package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.screens.sessioneditor.RestBundleDialog
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.withTrainingMode
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSetFormat

/**
 * `ed-serie` · Barra de opciones del ejercicio desplegado (ER1c1, R1 §4.2): «Modo: Reps ⌄», el descanso del ejercicio (un reloj y el
 * tiempo; en unilateral «Series 3:00 · Lados 0:15») y «⋯», que reúne lo que no cabe (lateralidad, APRE, ROM, relación, superserie,
 * unidad, carga inteligente y meta). El descanso y «⋯» van al final de la línea; si no caben pasan a la siguiente. Bilateral es el valor
 * por defecto (B1).
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

    EdFlexRow(modifier = Modifier.fillMaxWidth(), gap = 8.dp, lineGap = 8.dp) {
        EdChip(
            text = "Modo: ${trainingModeLabel(exercise.trainingMode)}",
            trailingIcon = KpknLineIcons.ChevronDown,
            onClick = { showModeSheet = true },
            description = "Modo del ejercicio: ${trainingModeLabel(exercise.trainingMode)}",
        )
        if (!suppressIndividualRest) {
            EdChip(
                text = if (chainWithSides) {
                    "Series ${KpknSetFormat.rest(restSelectionSeconds)} · Lados ${KpknSetFormat.rest(exercise.restBetweenSidesSeconds ?: 0)}"
                } else {
                    KpknSetFormat.rest(restSelectionSeconds)
                },
                icon = KpknLineIcons.Timer,
                onClick = onOpenRest,
                modifier = Modifier.edFlex(autoStart = true),
                description = "Descanso del ejercicio",
            )
        }
        EdChip(
            text = null,
            icon = KpknLineIcons.More,
            iconOnly = true,
            onClick = { showExerciseOptionsMenu = true },
            modifier = if (suppressIndividualRest) Modifier.edFlex(autoStart = true) else Modifier,
            description = "Más opciones del ejercicio",
        )
    }

    if (showExerciseOptionsMenu) {
        EdSheet(onDismiss = { showExerciseOptionsMenu = false }) {
            EdSheetHeader("Opciones del ejercicio")
            Column(modifier = Modifier.edBleed(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // B1/B8: bilateral es el valor por defecto; la lateralidad vive aquí, no como un segmentado fijo en la barra.
                EdMenuRow(
                    title = "Lateralidad",
                    subtitle = if (unilateral) "Unilateral" else "Bilateral",
                    icon = EdIcons.SideLR,
                    onClick = {
                        showExerciseOptionsMenu = false
                        showLateralitySheet = true
                    },
                )
                if (exercise.trainingMode in listOf(TrainingMode.REPS, TrainingMode.RM, TrainingMode.AMRAP)) {
                    EdMenuRow(
                        title = "Bloque APRE",
                        icon = EdIcons.Target2,
                        onClick = {
                            showExerciseOptionsMenu = false
                            showApreSheet = true
                        },
                    )
                }
                // Pulido v1 (15): el mismo dato que el interruptor «Registrar ROM» de «Detalle del registro» en la sesión en vivo
                // (`Exercise.trackRom`). Lienzo: la fila se activa y desactiva ahí mismo, sin cerrar la hoja.
                EdMenuRow(
                    title = if (exercise.trackRom) "Desactivar medir ROM" else "Medir ROM",
                    icon = EdIcons.Rom,
                    onClick = { onUpdateExercise { current -> current.copy(trackRom = !current.trackRom) } },
                )
                EdMenuRow(
                    title = relationshipAnchorName?.let { "Ancla: $it" } ?: "Relacionar ejercicio",
                    icon = KpknLineIcons.Link,
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
                    EdMenuRow(
                        title = if (exercise.isInSuperset()) "Gestionar superserie" else "Superserie",
                        icon = KpknLineIcons.Layers,
                        onClick = {
                            showExerciseOptionsMenu = false
                            onOpenSuperset()
                        },
                    )
                }
                EdMenuRow(
                    title = "Unidad personalizada",
                    icon = EdIcons.Ruler2,
                    onClick = {
                        showExerciseOptionsMenu = false
                        onOpenCustomUnit()
                    },
                )
                if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                    EdMenuRow(
                        title = "Carga inteligente",
                        icon = EdIcons.Bolt,
                        onClick = {
                            showExerciseOptionsMenu = false
                            onOpenSmartLoad()
                        },
                    )
                }
                if (onOpenGoal != null) {
                    EdMenuRow(
                        title = "Meta",
                        icon = EdIcons.Star,
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
 * `ed-descanso-ejercicio` · Selector de descansos encadenado del original (R1 §4.2): el descanso de las series y, en unilateral, el de entre
 * lados. Lo abren la barra de opciones («Descanso» o «Series · Lados») y el módulo «Lados» («Entre lados»). Guarda igual que el botón de
 * descansos original.
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
 * `ed-modo` · Hoja «Modo» (R1 §4.2 / B4): elegir un modo solo lo marca; «Aplicar modo» confirma antes de tocarlo y lo hace con
 * [withTrainingMode], que conserva el propósito AMRAP y su mínimo al entrar en AMRAP y los limpia al salir.
 */
@Composable
internal fun ExerciseModeSheet(
    current: TrainingMode,
    onSelect: (TrainingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
    EdSheet(onDismiss = onDismiss) {
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EdSheetHeader("Modo del ejercicio", "Cómo se prescriben sus series")
        Column(
            modifier = Modifier.edBleed(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            exerciseModeOptions.forEach { (mode, label, description) ->
                EdMenuRow(
                    title = label,
                    subtitle = description,
                    checked = mode == selected,
                    description = if (mode == selected) "$label, ${if (mode == current) "modo actual" else "modo elegido"}" else label,
                    onClick = { onSelect(mode) },
                )
            }
        }
        EdPrimaryButton(
            text = if (selected == current) "Listo" else "Aplicar ${trainingModeLabel(selected)}",
            onClick = onConfirm,
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
    EdSheet(onDismiss = onDismiss) {
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EdSheetHeader("Lateralidad", "Si el ejercicio se hace con los dos lados a la vez o de a uno")
        Column(modifier = Modifier.edBleed(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                false to ("Bilateral" to "Los dos lados a la vez, una sola serie."),
                true to ("Unilateral" to "Un lado por serie, con registro L y R."),
            ).forEach { (isUnilateral, option) ->
                val (label, description) = option
                EdMenuRow(
                    title = label,
                    subtitle = description,
                    checked = isUnilateral == unilateral,
                    description = if (isUnilateral == unilateral) "$label, lateralidad actual" else label,
                    onClick = {
                        if (isUnilateral != unilateral) {
                            onUpdateExercise { current -> current.toggledBilateralUnilateral() }
                        }
                        onDismiss()
                    },
                )
            }
        }
        if (unilateral) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdNumberPill(
                    text = "L → R",
                    on = exercise.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT,
                    onClick = {
                        onUpdateExercise { it.copy(unilateralSideOrder = UnilateralSideOrder.LEFT_RIGHT) }
                        onDismiss()
                    },
                )
                EdNumberPill(
                    text = "R → L",
                    on = exercise.unilateralSideOrder == UnilateralSideOrder.RIGHT_LEFT,
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
 * `ed-unilateral` · Fila «Lados» de un ejercicio unilateral (R1 §4.2): orden L → R / R → L, lados iguales o aparte y el descanso entre
 * lados, tres píldoras bajo las opciones. Si son iguales, lo que editas en un lado se copia al otro.
 */
@Composable
internal fun SideOptionsModule(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onOpenSideRest: () -> Unit,
) {
    val shared = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED
    EdFlexRow(modifier = Modifier.fillMaxWidth(), gap = 8.dp, lineGap = 8.dp) {
        EdChip(
            text = if (exercise.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) "Orden L → R" else "Orden R → L",
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
            description = "Orden de los lados",
        )
        EdChip(
            text = if (shared) "Lados iguales" else "Lados aparte",
            on = shared,
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
        EdChip(
            text = "Entre lados ${KpknSetFormat.rest(exercise.restBetweenSidesSeconds ?: 0)}",
            onClick = onOpenSideRest,
        )
    }
}
