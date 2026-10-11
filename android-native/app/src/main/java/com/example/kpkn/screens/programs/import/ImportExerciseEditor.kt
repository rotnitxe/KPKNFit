package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ProgramImportParser
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/** Paso del descanso en el selector: 15 s. */
private const val REST_STEP_SECONDS = 15

/** Descanso que se pone al indicarlo por primera vez: 2 min. */
private const val REST_DEFAULT_SECONDS = 120

/** RPE y RIR que se ponen al elegir cada modo por primera vez. */
private const val RPE_DEFAULT = 8.0
private const val RIR_DEFAULT = 2

/**
 * El panel de ajuste de un ejercicio, justo debajo de su fila: identidad (catálogo, propio o con dudas), series, repeticiones,
 * carga, esfuerzo, descanso, notas, «aplicar en las otras semanas», guardar y orden y estructura. «Guardar cambios» aplica el
 * nombre y los valores que cambiaron como un solo paso para deshacer; el orden, duplicar y quitar son inmediatos y dejan
 * deshacer. Las decisiones del catálogo también son inmediatas.
 */
@Composable
internal fun ImportExerciseEditor(
    exercise: ImportedExercise,
    dayId: String,
    position: Int,
    dayExerciseCount: Int,
    state: ImportExerciseState,
    shownName: String,
    moveTargets: List<ImportMoveTarget>,
    sameNameCount: Int,
    otherWeeks: Int,
    suggest: suspend (String) -> List<ExerciseNameCandidate>,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onEdit: (ImportEditOp) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var form by rememberSaveable(exercise, stateSaver = ImportFormSaver) { mutableStateOf(ImportExerciseForm.of(exercise)) }
    var rangeMode by rememberSaveable(exercise) { mutableStateOf(exercise.repsMin != exercise.repsMax) }
    var kgText by rememberSaveable(exercise) { mutableStateOf(exercise.loadKg?.let(::decimalText).orEmpty()) }
    var percentText by rememberSaveable(exercise) { mutableStateOf(exercise.percent1Rm?.let(::decimalText).orEmpty()) }
    var choosing by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var moving by rememberSaveable(exercise.id) { mutableStateOf(false) }

    val allWeeks = form.allWeeks && otherWeeks > 0
    val patch = patchFrom(exercise, form)
    val renamed = form.rawName.isNotBlank() && form.rawName.trim() != exercise.rawName
    val canSave = (renamed || !patch.isNoOp()) && form.isValid()

    fun save() {
        // Valores y nombre van en una sola operación: un solo paso para deshacer.
        val ops = buildList {
            if (!patch.isNoOp()) add(ImportEditOp.Update(exercise.id, patch, allWeeks))
            if (renamed) add(ImportEditOp.Rename(exercise.id, form.rawName.trim(), allWeeks))
        }
        when (ops.size) {
            0 -> Unit
            1 -> onEdit(ops.single())
            else -> onEdit(ImportEditOp.Batch(ops))
        }
        onClose()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WizardColors.glassFill)
            .border(1.dp, WizardColors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .semantics { paneTitle = "Ajusta el ejercicio" }
            .testTag("import-edit-panel"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Ajusta el ejercicio",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Tu archivo dice «${exercise.sourceText ?: exercise.rawName}».",
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        // Identidad: el catálogo, propio o con dudas. «Cambiar» y «Elegir del catálogo» abren el mismo bloque de elección.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                ImportExerciseState.CHOSEN -> if (choosing) {
                    ImportChoicePanel(
                        rawName = exercise.rawName,
                        suggest = suggest,
                        search = search,
                        onPick = { configId, name ->
                            choosing = false
                            onEdit(ImportEditOp.Decide(exercise.rawName, configId, name))
                        },
                        onCancel = { choosing = false },
                    )
                } else {
                    Text("Ejercicio del catálogo: $shownName", style = WizardTypography.controlLabel, color = WizardColors.text)
                    ImportTextAction(
                        label = "Cambiar",
                        onClick = { choosing = true },
                        modifier = Modifier.testTag("import-edit-change"),
                    )
                }
                ImportExerciseState.OWN -> if (choosing) {
                    ImportChoicePanel(
                        rawName = exercise.rawName,
                        suggest = suggest,
                        search = search,
                        onPick = { configId, name ->
                            choosing = false
                            onEdit(ImportEditOp.Decide(exercise.rawName, configId, name))
                        },
                        onCancel = { choosing = false },
                    )
                } else {
                    Text("Ejercicio propio", style = WizardTypography.controlLabel, color = WizardColors.text)
                    SetupFormTextField(
                        value = form.rawName,
                        onValueChange = { text -> form = form.copy(rawName = text) },
                        label = "Nombre",
                        testTag = "import-edit-name",
                    )
                    ImportTextAction(
                        label = "Elegir del catálogo",
                        onClick = { choosing = true },
                        modifier = Modifier.testTag("import-edit-pick"),
                    )
                }
                ImportExerciseState.UNDECIDED -> ImportChoicePanel(
                    rawName = exercise.rawName,
                    suggest = suggest,
                    search = search,
                    onPick = { configId, name -> onEdit(ImportEditOp.Decide(exercise.rawName, configId, name)) },
                    onCancel = null,
                )
            }
            if (sameNameCount > 1) {
                Text(
                    "Se aplica a las $sameNameCount veces que aparece «${exercise.rawName}».",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                )
            }
        }

        ImportSection(label = "Series") {
            ImportStepper(
                field = "series",
                valueText = "${form.sets}",
                canDecrease = form.sets > 1,
                canIncrease = form.sets < ProgramImportParser.MAX_SETS,
                onDecrease = { form = form.copy(sets = form.sets - 1) },
                onIncrease = { form = form.copy(sets = form.sets + 1) },
                testTag = "import-edit-sets",
            )
        }

        ImportSection(label = "Repeticiones") {
            ImportSegmented(
                options = listOf(false, true),
                selected = rangeMode,
                label = { range -> if (range) "Rango" else "Fijas" },
                onSelected = { range ->
                    rangeMode = range
                    if (!range) form = form.copy(repsMax = form.repsMin)
                },
                testTag = "import-edit-reps-mode",
            )
            if (rangeMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("de", style = WizardTypography.note, color = WizardColors.textMuted)
                        ImportStepper(
                            field = "repeticiones mínimas",
                            valueText = "${form.repsMin}",
                            canDecrease = form.repsMin > 1,
                            canIncrease = form.repsMin < ProgramImportParser.MAX_REPS,
                            onDecrease = { form = form.copy(repsMin = form.repsMin - 1) },
                            onIncrease = {
                                // «de» por encima de «a» empuja «a».
                                val low = form.repsMin + 1
                                form = form.copy(repsMin = low, repsMax = maxOf(form.repsMax, low))
                            },
                            testTag = "import-edit-reps-min",
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("a", style = WizardTypography.note, color = WizardColors.textMuted)
                        ImportStepper(
                            field = "repeticiones máximas",
                            valueText = "${form.repsMax}",
                            canDecrease = form.repsMax > 1,
                            canIncrease = form.repsMax < ProgramImportParser.MAX_REPS,
                            onDecrease = {
                                // «a» por debajo de «de» arrastra «de».
                                val high = form.repsMax - 1
                                form = form.copy(repsMax = high, repsMin = minOf(form.repsMin, high))
                            },
                            onIncrease = { form = form.copy(repsMax = form.repsMax + 1) },
                            testTag = "import-edit-reps-max",
                        )
                    }
                }
            } else {
                ImportStepper(
                    field = "repeticiones",
                    valueText = "${form.repsMin}",
                    canDecrease = form.repsMin > 1,
                    canIncrease = form.repsMin < ProgramImportParser.MAX_REPS,
                    onDecrease = { form = form.copy(repsMin = form.repsMin - 1, repsMax = form.repsMin - 1) },
                    onIncrease = { form = form.copy(repsMin = form.repsMin + 1, repsMax = form.repsMin + 1) },
                    testTag = "import-edit-reps",
                )
            }
        }

        ImportSection(label = "Carga") {
            ImportSegmented(
                options = ImportLoadMode.entries,
                selected = form.loadMode,
                label = { mode ->
                    when (mode) {
                        ImportLoadMode.NONE -> "Sin carga"
                        ImportLoadMode.KG -> "kg"
                        ImportLoadMode.PERCENT -> "% 1RM"
                    }
                },
                onSelected = { mode -> form = form.copy(loadMode = mode) },
                testTag = "import-edit-load-mode",
            )
            when (form.loadMode) {
                ImportLoadMode.NONE -> Unit
                ImportLoadMode.KG -> {
                    SetupFormTextField(
                        value = kgText,
                        onValueChange = { text ->
                            kgText = text
                            form = form.copy(loadKg = parseDecimal(text))
                        },
                        label = "Kilos",
                        keyboardType = KeyboardType.Decimal,
                        testTag = "import-edit-kg",
                    )
                    if (!form.loadIsValid()) {
                        Text(
                            "Entre 0,5 y 1000 kg.",
                            style = WizardTypography.note,
                            color = WizardColors.danger,
                            modifier = Modifier.testTag("import-edit-load-hint"),
                        )
                    }
                }
                ImportLoadMode.PERCENT -> {
                    SetupFormTextField(
                        value = percentText,
                        onValueChange = { text ->
                            percentText = text
                            form = form.copy(percent1Rm = parseDecimal(text))
                        },
                        label = "% de 1RM",
                        keyboardType = KeyboardType.Decimal,
                        testTag = "import-edit-percent",
                    )
                    Text(
                        "Entre 1 y 100 %.",
                        style = WizardTypography.note,
                        color = if (form.loadIsValid()) WizardColors.textMuted else WizardColors.danger,
                        modifier = Modifier.testTag("import-edit-load-hint"),
                    )
                }
            }
        }

        ImportSection(label = "Esfuerzo") {
            ImportSegmented(
                options = ImportEffortMode.entries,
                selected = form.effortMode,
                label = { mode ->
                    when (mode) {
                        ImportEffortMode.NONE -> "Sin esfuerzo"
                        ImportEffortMode.RPE -> "RPE"
                        ImportEffortMode.RIR -> "RIR"
                    }
                },
                onSelected = { mode ->
                    form = form.copy(
                        effortMode = mode,
                        rpe = if (mode == ImportEffortMode.RPE) form.rpe ?: RPE_DEFAULT else form.rpe,
                        rir = if (mode == ImportEffortMode.RIR) form.rir ?: RIR_DEFAULT else form.rir,
                    )
                },
                testTag = "import-edit-effort-mode",
            )
            when (form.effortMode) {
                ImportEffortMode.NONE -> Unit
                ImportEffortMode.RPE -> {
                    val rpe = form.rpe ?: RPE_DEFAULT
                    ImportStepper(
                        field = "RPE",
                        valueText = decimalText(rpe),
                        canDecrease = rpe > 1.0,
                        canIncrease = rpe < 10.0,
                        onDecrease = { form = form.copy(rpe = rpe - 0.5) },
                        onIncrease = { form = form.copy(rpe = rpe + 0.5) },
                        testTag = "import-edit-rpe",
                    )
                }
                ImportEffortMode.RIR -> {
                    val rir = form.rir ?: RIR_DEFAULT
                    ImportStepper(
                        field = "RIR",
                        valueText = "$rir",
                        canDecrease = rir > 0,
                        canIncrease = rir < 10,
                        onDecrease = { form = form.copy(rir = rir - 1) },
                        onIncrease = { form = form.copy(rir = rir + 1) },
                        testTag = "import-edit-rir",
                    )
                }
            }
        }

        ImportSection(label = "Descanso") {
            ImportSegmented(
                options = listOf(false, true),
                selected = form.restSeconds != null,
                label = { on -> if (on) "Indicar" else "Sin indicar" },
                onSelected = { on ->
                    form = form.copy(restSeconds = if (on) form.restSeconds ?: REST_DEFAULT_SECONDS else null)
                },
                testTag = "import-edit-rest-mode",
            )
            form.restSeconds?.let { seconds ->
                ImportStepper(
                    field = "descanso",
                    valueText = importRestText(seconds),
                    canDecrease = seconds > 0,
                    canIncrease = seconds < ProgramImportParser.MAX_REST_SECONDS,
                    onDecrease = { form = form.copy(restSeconds = (seconds - REST_STEP_SECONDS).coerceAtLeast(0)) },
                    onIncrease = {
                        form = form.copy(restSeconds = (seconds + REST_STEP_SECONDS).coerceAtMost(ProgramImportParser.MAX_REST_SECONDS))
                    },
                    testTag = "import-edit-rest",
                )
            }
        }

        SetupFormTextField(
            value = form.notes,
            onValueChange = { text -> form = form.copy(notes = text.take(ProgramImportParser.MAX_NOTE_CHARS)) },
            label = "Notas",
            testTag = "import-edit-notes",
        )

        if (otherWeeks > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ImportSwitchRow(
                    label = if (otherWeeks == 1) {
                        "Aplicar también en la otra semana"
                    } else {
                        "Aplicar también en las otras $otherWeeks semanas"
                    },
                    checked = form.allWeeks,
                    onCheckedChange = { on -> form = form.copy(allWeeks = on) },
                    testTag = "import-edit-allweeks",
                )
                Text("Solo cambia lo que tocaste aquí.", style = WizardTypography.note, color = WizardColors.textMuted)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImportCta(
                label = "Guardar cambios",
                enabled = canSave,
                onClick = { save() },
                testTag = "import-edit-save",
                modifier = Modifier.weight(1f),
            )
            ImportTextAction(label = "Cancelar", onClick = onClose, modifier = Modifier.testTag("import-edit-cancel"))
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ImportTextAction(
                label = "Subir",
                enabled = position > 0,
                onClick = { onEdit(ImportEditOp.Move(exercise.id, dayId, position - 1)) },
                modifier = Modifier.testTag("import-edit-up"),
            )
            ImportTextAction(
                label = "Bajar",
                enabled = position < dayExerciseCount - 1,
                onClick = { onEdit(ImportEditOp.Move(exercise.id, dayId, position + 1)) },
                modifier = Modifier.testTag("import-edit-down"),
            )
        }
        ImportTextAction(
            label = "Mover a otro día",
            enabled = moveTargets.isNotEmpty(),
            onClick = { moving = !moving },
            modifier = Modifier.testTag("import-edit-move"),
        )
        if (moving) {
            moveTargets.forEach { target ->
                ImportTextAction(
                    label = target.label,
                    onClick = {
                        // Al final de ese día; el panel se cierra.
                        onEdit(ImportEditOp.Move(exercise.id, target.dayId, target.exerciseCount))
                        onClose()
                    },
                    modifier = Modifier.testTag("import-edit-move-${target.dayId}"),
                )
            }
        }
        ImportTextAction(
            label = "Duplicar",
            onClick = { onEdit(ImportEditOp.Duplicate(exercise.id)) },
            modifier = Modifier.testTag("import-edit-duplicate"),
        )
        ImportTextAction(
            label = "Quitar ejercicio",
            color = WizardColors.danger,
            onClick = {
                onEdit(ImportEditOp.Remove(exercise.id))
                onClose()
            },
            modifier = Modifier.testTag("import-edit-remove"),
        )
    }
}

/** Un bloque del panel: su etiqueta y su control. */
@Composable
private fun ImportSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = WizardTypography.controlLabel, color = WizardColors.text)
        content()
    }
}

/**
 * Conmutador de opciones de una sola escala para el panel: ancho completo, cada opción de al menos 48 dp de alto, texto
 * «cardSubtitle» y la pastilla activa en tinta de marca. Es `Role.RadioButton` con `selected`, como `WizardUnitToggle`, pero sin
 * su escala de medida, que cortaba «Sin carga» en tres opciones.
 */
@Composable
private fun <T> ImportSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(WizardShapes.pill)
            .background(Color.Black)
            .padding(4.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = WizardSpacing.touchTarget)
                    .clip(WizardShapes.pill)
                    .background(if (active) WizardColors.cta else Color.Transparent)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelected(option) })
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = WizardTypography.cardSubtitle,
                    color = if (active) WizardColors.ctaContent else WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Selector «−  4  +»: dos botones de 48 dp con su descripción («Menos series», «Más series») y el valor en medio, en una sola
 * línea y en un estilo contenido para que un descanso de «10 min 30 s» quepa a 320 dp con la letra al 130 %. Se desactiva en
 * los extremos y el valor se anuncia como estado.
 */
@Composable
private fun ImportStepper(
    field: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepperButton(symbol = "−", description = "Menos $field", enabled = canDecrease, onClick = onDecrease)
        Text(
            valueText,
            style = WizardTypography.cardTitle,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = field
                    stateDescription = valueText
                },
        )
        StepperButton(symbol = "+", description = "Más $field", enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(WizardSpacing.touchTarget)
            .clip(CircleShape)
            .background(WizardColors.glassFill)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = WizardTypography.controlLabel, color = if (enabled) WizardColors.text else WizardColors.textFaint)
    }
}

/** Interruptor de texto con pista: `Role.Switch`, sin caja; la pista va en tinta de marca cuando está activo. */
@Composable
private fun ImportSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(testTag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = WizardTypography.controlLabel, color = WizardColors.text, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(if (checked) WizardColors.cta else WizardColors.progressTrack)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) WizardColors.ctaContent else WizardColors.textMuted),
            )
        }
    }
}

/** El botón principal del panel: el CTA del wizard (tinta sobre negro), apagado si no se puede guardar. */
@Composable
private fun ImportCta(label: String, enabled: Boolean, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.ctaHeight)
            .clip(WizardShapes.cta)
            .background(if (enabled) WizardColors.cta else WizardColors.ctaDisabled)
            .testTag(testTag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = WizardTypography.cta,
            color = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
        )
    }
}

/** Un número escrito con coma o con punto; null si no es un número. */
private fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Guarda el formulario al girar la pantalla: sus campos como valores que el estado puede guardar. */
private val ImportFormSaver: Saver<ImportExerciseForm, Any> = listSaver(
    save = { form ->
        listOf(
            form.rawName, form.sets, form.repsMin, form.repsMax, form.loadMode.name, form.loadKg, form.percent1Rm,
            form.effortMode.name, form.rpe, form.rir, form.restSeconds, form.notes, form.allWeeks,
        )
    },
    restore = { values ->
        ImportExerciseForm(
            rawName = values[0] as String,
            sets = values[1] as Int,
            repsMin = values[2] as Int,
            repsMax = values[3] as Int,
            loadMode = ImportLoadMode.valueOf(values[4] as String),
            loadKg = values[5] as Double?,
            percent1Rm = values[6] as Double?,
            effortMode = ImportEffortMode.valueOf(values[7] as String),
            rpe = values[8] as Double?,
            rir = values[9] as Int?,
            restSeconds = values[10] as Int?,
            notes = values[11] as String,
            allWeeks = values[12] as Boolean,
        )
    },
)
