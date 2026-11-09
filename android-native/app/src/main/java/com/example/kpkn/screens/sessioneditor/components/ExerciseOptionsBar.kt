package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import com.example.kpkn.screens.workout.components.RomSwitch
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
import com.example.kpkn.domain.training.resolveLiveApreBlock
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.screens.sessioneditor.RestBundleDialog
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.sessioneditor.apreExitPreview
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.withTrainingMode
import com.example.kpkn.screens.sessioneditor.withoutApreBlock
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.onboarding.design.WizardColors
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
    val apre = remember(exercise) { resolveLiveApreBlock(exercise) }
    val apreNeedsReview = exercise.apreBlock != null && apre == null
    val apreExitPreview = remember(exercise) { exercise.apreExitPreview().summary }
    val modeLabel = when {
        exercise.apreBlock == null -> trainingModeLabel(exercise.trainingMode)
        apre == null -> "APRE · revisar"
        else -> "APRE ${apre.variant.xrmReps}"
    }

    EdFlexRow(modifier = Modifier.fillMaxWidth(), gap = 8.dp, lineGap = 8.dp) {
        EdChip(
            text = "Modo: $modeLabel",
            trailingIcon = KpknLineIcons.ChevronDown,
            onClick = { showModeSheet = true },
            description = "Modo del ejercicio: $modeLabel",
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
            val closeExerciseMenu = LocalEdSheetClose.current
            EdSheetHeader("Opciones del ejercicio")
            Column(modifier = Modifier.edBleed(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // B1/B8: bilateral es el valor por defecto; la lateralidad vive aquí, no como un segmentado fijo en la barra.
                EdMenuRow(
                    title = "Lateralidad",
                    subtitle = if (unilateral) "Unilateral" else "Bilateral",
                    icon = EdIcons.SideLR,
                    onClick = {
                        // pulido:ed-J05 closeLayer(cb): la hoja baja (230 ms) y después abre lo elegido
                        closeExerciseMenu {
                            showExerciseOptionsMenu = false
                            showLateralitySheet = true
                        }
                    },
                )
                // Pulido v1 (15) · `lv-rom-editor`: «Medir ROM» pasa a ser el interruptor «Registrar ROM» (el mismo nombre que en «Detalle del
                // registro» de la sesión en vivo) y NO cierra la hoja. Es `Exercise.trackRom`: activo, la tarjeta de cada serie del ejercicio
                // lleva el deslizador de ROM al 100 %; apagado, sin ROM.
                RomSwitchMenuRow(
                    checked = exercise.trackRom,
                    onToggle = { onUpdateExercise { current -> current.copy(trackRom = !current.trackRom) } },
                )
                EdMenuRow(
                    title = relationshipAnchorName?.let { "Ancla: $it" } ?: "Relacionar ejercicio",
                    icon = KpknLineIcons.Link,
                    onClick = {
                        // pulido:ed-J05 closeLayer(cb): la hoja baja (230 ms) y después abre lo elegido
                        closeExerciseMenu {
                            showExerciseOptionsMenu = false
                            if (exercise.relativeToCanonicalExerciseId == null) {
                                onOpenRelationshipPicker()
                            } else {
                                onClearRelationship()
                            }
                        }
                    },
                )
                // ER1f: sin callback (un miembro de la superserie configurándose) no hay ítem que ofrecer.
                if (onOpenSuperset != null) {
                    EdMenuRow(
                        title = if (exercise.isInSuperset()) "Gestionar superserie" else "Superserie",
                        icon = KpknLineIcons.Layers,
                        onClick = {
                            // pulido:ed-J05 closeLayer(cb): la hoja baja (230 ms) y después abre lo elegido
                            closeExerciseMenu {
                                showExerciseOptionsMenu = false
                                onOpenSuperset()
                            }
                        },
                    )
                }
                EdMenuRow(
                    title = "Unidad personalizada",
                    icon = EdIcons.Ruler2,
                    onClick = {
                        // pulido:ed-J05 closeLayer(cb): la hoja baja (230 ms) y después abre lo elegido
                        closeExerciseMenu {
                            showExerciseOptionsMenu = false
                            onOpenCustomUnit()
                        }
                    },
                )
                if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                    EdMenuRow(
                        title = "Carga inteligente",
                        icon = EdIcons.Bolt,
                        onClick = {
                            // pulido:ed-J05 closeLayer(cb): la hoja baja (230 ms) y después abre lo elegido
                            closeExerciseMenu {
                                showExerciseOptionsMenu = false
                                onOpenSmartLoad()
                            }
                        },
                    )
                }
                if (onOpenGoal != null) {
                    EdMenuRow(
                        title = "Meta",
                        icon = EdIcons.Star,
                        onClick = {
                            // pulido:ed-J05 closeLayer(cb): la hoja baja (230 ms) y después abre lo elegido
                            closeExerciseMenu {
                                showExerciseOptionsMenu = false
                                onOpenGoal()
                            }
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
            onSelectApre = if (exercise.apreBlock != null || exercise.trainingMode in listOf(TrainingMode.REPS, TrainingMode.RM, TrainingMode.AMRAP)) {
                { showModeSheet = false; showApreSheet = true }
            } else null,
            hasApreBlock = exercise.apreBlock != null,
            hasInvalidApreBlock = apreNeedsReview,
            apreExitPreview = apreExitPreview,
            onExitApre = { onUpdateExercise { current -> current.withoutApreBlock() } },
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
    if (showApreSheet) ApreBlockSheet(
        exercise = exercise,
        onApply = onUpdateExercise,
        onDismiss = { showApreSheet = false },
        onOpenSmartLoad = { showApreSheet = false; onOpenSmartLoad() },
    )
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
    onSelectApre: (() -> Unit)? = null,
    hasApreBlock: Boolean = false,
    hasInvalidApreBlock: Boolean = false,
    apreExitPreview: String? = null,
    onExitApre: (() -> Unit)? = null,
) {
    var selected by remember(current) { mutableStateOf(current) }
    EdSheet(onDismiss = onDismiss) {
        // pulido:ed-J05 closeLayer(cb): la hoja baja (230 ms) y después se aplica el modo elegido
        val close = LocalEdSheetClose.current
        ExerciseModeSheetBody(
            current = current,
            selected = selected,
            onSelect = { selected = it },
            onSelectApre = onSelectApre,
            hasApreBlock = hasApreBlock,
            hasInvalidApreBlock = hasInvalidApreBlock,
            apreExitPreview = apreExitPreview,
            onConfirm = {
                close {
                    when {
                        hasApreBlock && selected == TrainingMode.REPS -> onExitApre?.invoke() ?: onDismiss()
                        selected != current -> onSelect(selected)
                        else -> onDismiss()
                    }
                }
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
    onSelectApre: (() -> Unit)? = null,
    hasApreBlock: Boolean = false,
    hasInvalidApreBlock: Boolean = false,
    apreExitPreview: String? = null,
) {
    val apreModePreview = when {
        hasApreBlock && selected == TrainingMode.REPS ->
            apreExitPreview ?: "Vista previa: se conservan las cuatro series, cargas y objetivos; S3 deja de ajustar S4 y se retira la calibración APRE."
        hasApreBlock && selected != current ->
            "Vista previa: se retiran el marcador y las reglas APRE S1–S4; se conservan las series, cargas y objetivos antes de aplicar ${trainingModeLabel(selected)}."
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EdSheetHeader("Modo del ejercicio", "Cómo se prescriben sus series")
        Column(
            modifier = Modifier.edBleed(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            exerciseModeOptions.forEach { (mode, label, description) ->
                EdMenuRow(
                    title = label,
                    subtitle = if (mode == TrainingMode.REPS && hasApreBlock) {
                        "Salir de APRE y conservar sus cuatro series como repeticiones fijas."
                    } else description,
                    checked = mode == selected,
                    description = if (mode == selected) "$label, ${if (mode == current) "modo actual" else "modo elegido"}" else label,
                    onClick = { onSelect(mode) },
                )
            }
            onSelectApre?.let { selectApre ->
                EdMenuRow(
                    title = if (hasInvalidApreBlock) "Revisar APRE" else "APRE",
                    subtitle = if (hasInvalidApreBlock) "El marcador está dañado. Reparar crea cuatro series APRE con IDs distintos."
                    else if (hasApreBlock) "Editar la variante y los objetivos S1–S4 del bloque APRE activo."
                    else "Bloque de cuatro series APRE 3, 6 o 10; calcula S1–S4 desde la referencia del ejercicio.",
                    checked = hasApreBlock,
                    description = if (hasInvalidApreBlock) "Revisar o reparar bloque APRE" else if (hasApreBlock) "APRE activo" else "Elegir el modo de series APRE",
                    onClick = selectApre,
                )
            }
        }
        apreModePreview?.let { preview ->
            Text(preview, style = KpknType.caption, color = WizardColors.textMuted)
        }
        EdPrimaryButton(
            text = when {
                hasApreBlock && hasInvalidApreBlock && selected == TrainingMode.REPS -> "Salir de APRE con esta previsualización"
                hasApreBlock && selected == TrainingMode.REPS -> "Salir de APRE y conservar las cuatro series"
                selected == current -> "Listo"
                else -> "Aplicar ${trainingModeLabel(selected)}"
            },
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

/** `Registrar ROM` de la hoja «Opciones del ejercicio»: título, línea de ayuda y el interruptor del lienzo (`.lv-sw`). */
@Composable
private fun RomSwitchMenuRow(checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Switch, onClick = onToggle)
            .semantics {
                toggleableState = ToggleableState(checked)
                stateDescription = if (checked) "Activado" else "Desactivado"
            }
            .testTag("exercise-options-rom-switch")
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Registrar ROM", style = edText(14.5f, FontWeight.SemiBold, EdColors.ink, 18.1f))
            Text("Añade un deslizador de ROM a la tarjeta de cada serie de este ejercicio.",
                style = edText(12f, FontWeight.Medium, EdColors.faint, 16.2f))
        }
        RomSwitch(checked)
    }
}
