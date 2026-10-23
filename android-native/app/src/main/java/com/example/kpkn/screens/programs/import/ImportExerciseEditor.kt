package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.components.KpknRestPickerChain
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.kpkn.domain.training.importer.ImportLoadUnit
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportExerciseEditor(
    exercise: ImportedExercise,
    dayId: String,
    position: Int,
    dayExerciseCount: Int,
    state: ImportExerciseState,
    shownName: String,
    loadUnit: ImportLoadUnit,
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
    val canSave = (renamed || !patch.isNoOp()) && form.isValid(loadUnit)

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
            .importGlassPanel(compact = true)
            .semantics { paneTitle = "Ajusta el ejercicio" }
            .testTag("import-edit-panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
            color = WizardColors.textFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Identidad: el catálogo, propio o con dudas. «Cambiar» y «Elegir del catálogo» abren el mismo bloque de elección.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = WizardSpacing.touchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Ejercicio del catálogo: $shownName",
                            style = WizardTypography.cardSubtitle,
                            color = WizardColors.text,
                            modifier = Modifier.weight(1f),
                        )
                        ImportOutlineButton(label = "Cambiar", onClick = { choosing = true }, testTag = "import-edit-change")
                    }
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

        ImportSection(label = "Series y repeticiones") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ImportStepper(
                    field = "series",
                    valueText = "${form.sets}",
                    suffix = if (form.sets == 1) "serie" else "series",
                    canDecrease = form.sets > 1,
                    canIncrease = form.sets < ProgramImportParser.MAX_SETS,
                    onDecrease = { form = form.copy(sets = form.sets - 1) },
                    onIncrease = { form = form.copy(sets = form.sets + 1) },
                    testTag = "import-edit-sets",
                    modifier = Modifier.weight(1f),
                )
                ImportRepsMode(
                    rangeMode = rangeMode,
                    repsMin = form.repsMin,
                    repsMax = form.repsMax,
                    onChange = { range, low, high ->
                        rangeMode = range
                        form = form.copy(repsMin = low, repsMax = high)
                    },
                    tagPrefix = "import-edit",
                    modifier = Modifier.weight(1f),
                )
            }
            ImportRepsSteppers(
                rangeMode = rangeMode,
                repsMin = form.repsMin,
                repsMax = form.repsMax,
                onChange = { range, low, high ->
                    rangeMode = range
                    form = form.copy(repsMin = low, repsMax = high)
                },
                tagPrefix = "import-edit",
            )
        }

        ImportSection(label = "Carga") {
            ImportSegmented(
                options = ImportLoadMode.entries,
                selected = form.loadMode,
                label = { mode ->
                    when (mode) {
                        ImportLoadMode.NONE -> "Sin carga"
                        ImportLoadMode.KG -> importLoadUnitLabel(loadUnit)
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
                        label = if (loadUnit == ImportLoadUnit.LB) "Libras" else "Kilos",
                        keyboardType = KeyboardType.Decimal,
                        testTag = "import-edit-kg",
                    )
                    if (!form.loadIsValid(loadUnit)) {
                        Text(
                            importLoadRangeText(loadUnit),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1.75f),
                    weights = listOf(1.7f, 1f, 1f),
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
                            modifier = Modifier.weight(1f),
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
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        ImportSection(label = "Descanso") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ImportSegmented(
                    options = listOf(false, true),
                    selected = form.restSeconds != null,
                    label = { on -> if (on) "Indicar" else "Sin indicar" },
                    onSelected = { on ->
                        form = form.copy(restSeconds = if (on) form.restSeconds ?: REST_DEFAULT_SECONDS else null)
                    },
                    testTag = "import-edit-rest-mode",
                    modifier = Modifier.weight(1.3f),
                    weights = listOf(1.2f, 1f),
                )
                form.restSeconds?.let { seconds ->
                    ImportRestStepper(
                        seconds = seconds,
                        onChange = { form = form.copy(restSeconds = it.coerceIn(0, ProgramImportParser.MAX_REST_SECONDS)) },
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
        }

        SetupFormTextField(
            value = form.notes,
            onValueChange = { text -> form = form.copy(notes = text.take(ProgramImportParser.MAX_NOTE_CHARS)) },
            label = "Notas",
            testTag = "import-edit-notes",
        )

        if (otherWeeks > 0) {
            ImportSwitchRow(
                label = if (otherWeeks == 1) {
                    "Aplicar también en la otra semana"
                } else {
                    "Aplicar también en las otras $otherWeeks semanas"
                },
                note = "Solo cambia lo que tocaste aquí.",
                checked = form.allWeeks,
                onCheckedChange = { on -> form = form.copy(allWeeks = on) },
                testTag = "import-edit-allweeks",
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportCta(
                label = "Guardar cambios",
                enabled = canSave,
                onClick = { save() },
                testTag = "import-edit-save",
                modifier = Modifier.weight(1f),
                compact = true,
            )
            ImportTextAction(label = "Cancelar", onClick = onClose, modifier = Modifier.testTag("import-edit-cancel"))
        }

        // Orden y estructura: Subir, Bajar, Mover a otro día, Duplicar y Quitar, como fichas.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ImportActionChip(
                label = "Subir",
                enabled = position > 0,
                onClick = { onEdit(ImportEditOp.Move(exercise.id, dayId, position - 1)) },
                testTag = "import-edit-up",
            )
            ImportActionChip(
                label = "Bajar",
                enabled = position < dayExerciseCount - 1,
                onClick = { onEdit(ImportEditOp.Move(exercise.id, dayId, position + 1)) },
                testTag = "import-edit-down",
            )
            ImportActionChip(
                label = "Mover a otro día",
                enabled = moveTargets.isNotEmpty(),
                onClick = { moving = !moving },
                testTag = "import-edit-move",
            )
            ImportActionChip(
                label = "Duplicar",
                onClick = { onEdit(ImportEditOp.Duplicate(exercise.id)) },
                testTag = "import-edit-duplicate",
            )
            ImportActionChip(
                label = "Quitar ejercicio",
                danger = true,
                onClick = {
                    onEdit(ImportEditOp.Remove(exercise.id))
                    onClose()
                },
                testTag = "import-edit-remove",
            )
        }
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
    }
}

/**
 * El panel de los paneles del importador (el de «Elegir», el ajuste, los menús y «Añadir ejercicio»): un módulo #111 con filete de
 * blanco al 10 % y radio 20 dp. Con [compact], el relleno del tablero G3 (10 × 14 dp); sin él, 16 dp.
 */
internal fun Modifier.importGlassPanel(compact: Boolean = false): Modifier = this
    .clip(RoundedCornerShape(20.dp))
    .background(ImportModuleFill)
    .border(1.dp, ImportModuleBorder, RoundedCornerShape(20.dp))
    .then(if (compact) Modifier.padding(horizontal = 14.dp, vertical = 10.dp) else Modifier.padding(16.dp))

/** Los tonos del tablero G3: el módulo #111, su filete y el elemento tonal #1A1A1A de los controles. */
private val ImportModuleFill = Color(0xFF111111)
private val ImportModuleBorder = Color.White.copy(alpha = 0.10f)
internal val ImportControlFill = Color(0xFF1A1A1A)
private val ImportControlShape = RoundedCornerShape(14.dp)
private val ImportPillShape = RoundedCornerShape(24.dp)

/** El peligro de la paleta KPKN (#EF6F64) de «Quitar ejercicio». */
private val ImportDanger = Color(0xFFEF6F64)

/**
 * Repeticiones: «Fijas» (un selector de 1 a 100) o «Rango» (dos selectores, «de» y «a»). En «Rango», si «de» sube por encima de
 * «a», «a» sube con él; si «a» baja por debajo de «de», «de» baja con él. [onChange] recibe el modo y las dos repeticiones nuevas;
 * [tagPrefix] da las marcas de prueba (`<prefijo>-reps-min`…). Lo usan el panel de ajuste y el de «Añadir ejercicio».
 */
@Composable
internal fun ImportRepsEditor(
    rangeMode: Boolean,
    repsMin: Int,
    repsMax: Int,
    onChange: (rangeMode: Boolean, repsMin: Int, repsMax: Int) -> Unit,
    tagPrefix: String,
) {
    ImportRepsMode(rangeMode = rangeMode, repsMin = repsMin, repsMax = repsMax, onChange = onChange, tagPrefix = tagPrefix)
    ImportRepsSteppers(rangeMode = rangeMode, repsMin = repsMin, repsMax = repsMax, onChange = onChange, tagPrefix = tagPrefix)
}

/** El conmutador «Fijas | Rango» de las repeticiones. Al pasar a «Fijas», las dos repeticiones quedan en la de «de». */
@Composable
internal fun ImportRepsMode(
    rangeMode: Boolean,
    repsMin: Int,
    repsMax: Int,
    onChange: (rangeMode: Boolean, repsMin: Int, repsMax: Int) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    ImportSegmented(
        options = listOf(false, true),
        selected = rangeMode,
        label = { range -> if (range) "Rango" else "Fijas" },
        onSelected = { range -> onChange(range, repsMin, if (range) repsMax else repsMin) },
        testTag = "$tagPrefix-reps-mode",
        modifier = modifier,
    )
}

/** Los selectores de repeticiones: uno en «Fijas»; «de» y «a» en «Rango», con el empuje entre ellos. */
@Composable
internal fun ImportRepsSteppers(
    rangeMode: Boolean,
    repsMin: Int,
    repsMax: Int,
    onChange: (rangeMode: Boolean, repsMin: Int, repsMax: Int) -> Unit,
    tagPrefix: String,
) {
    if (rangeMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ImportStepper(
                field = "repeticiones mínimas",
                valueText = "$repsMin",
                prefix = "de",
                canDecrease = repsMin > 1,
                canIncrease = repsMin < ProgramImportParser.MAX_REPS,
                onDecrease = { onChange(true, repsMin - 1, repsMax) },
                onIncrease = {
                    // «de» por encima de «a» empuja «a».
                    val low = repsMin + 1
                    onChange(true, low, maxOf(repsMax, low))
                },
                testTag = "$tagPrefix-reps-min",
                modifier = Modifier.weight(1f),
            )
            ImportStepper(
                field = "repeticiones máximas",
                valueText = "$repsMax",
                prefix = "a",
                canDecrease = repsMax > 1,
                canIncrease = repsMax < ProgramImportParser.MAX_REPS,
                onDecrease = {
                    // «a» por debajo de «de» arrastra «de».
                    val high = repsMax - 1
                    onChange(true, minOf(repsMin, high), high)
                },
                onIncrease = { onChange(true, repsMin, repsMax + 1) },
                testTag = "$tagPrefix-reps-max",
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        ImportStepper(
            field = "repeticiones",
            valueText = "$repsMin",
            canDecrease = repsMin > 1,
            canIncrease = repsMin < ProgramImportParser.MAX_REPS,
            onDecrease = { onChange(false, repsMin - 1, repsMin - 1) },
            onIncrease = { onChange(false, repsMin + 1, repsMin + 1) },
            testTag = "$tagPrefix-reps",
        )
    }
}

/** Un bloque del panel: su etiqueta pequeña en mayúsculas y su control. */
@Composable
internal fun ImportSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label.uppercase(), style = WizardTypography.eyebrow, color = WizardColors.textMuted, maxLines = 1)
        content()
    }
}

/**
 * Conmutador de opciones de una sola escala para el panel: pista tonal #1A1A1A de 48 dp de alto, cada opción con su objetivo
 * táctil de 48 dp y la pastilla activa en tinta de marca. Es `Role.RadioButton` con `selected`, como `WizardUnitToggle`, pero sin
 * su escala de medida, que cortaba «Sin carga» en tres opciones. [weights] reparte el ancho entre las opciones (iguales si no se da).
 */
@Composable
internal fun <T> ImportSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    weights: List<Float>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WizardSpacing.touchTarget)
            .clip(ImportPillShape)
            .background(ImportControlFill)
            .testTag(testTag),
    ) {
        options.forEachIndexed { position, option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(weights?.getOrNull(position) ?: 1f)
                    .fillMaxHeight()
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelected(option) })
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(ImportPillShape)
                        .background(if (active) WizardColors.cta else Color.Transparent)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = WizardTypography.cardSubtitle.copy(fontSize = 12.sp),
                        color = if (active) WizardColors.ctaContent else WizardColors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Selector «−  4  +»: un módulo tonal de 48 dp con dos botones de 48 dp (descripciones «Menos series», «Más series») y el valor en
 * medio, en una sola línea; [prefix] («de», «a») y [suffix] («series») van como textos aparte, para que el valor sea el único texto
 * de su nodo. Se desactiva en los extremos y el valor se anuncia como estado.
 */
@Composable
internal fun ImportStepper(
    field: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WizardSpacing.touchTarget)
            .clip(ImportControlShape)
            .background(ImportControlFill)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(symbol = "−", description = "Menos $field", enabled = canDecrease, onClick = onDecrease)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            prefix?.let { Text(it, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted, maxLines = 1) }
            Text(
                valueText,
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.semantics {
                    contentDescription = field
                    stateDescription = valueText
                },
            )
            suffix?.let { Text(it, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted, maxLines = 1) }
        }
        StepperButton(symbol = "+", description = "Más $field", enabled = canIncrease, onClick = onIncrease)
    }
}

/**
 * El descanso: «−15», el valor como m:ss (que abre la hoja de descanso) y «+15», en un módulo tonal de 48 dp. [onChange] recibe los
 * segundos nuevos, que quien llama acota a lo que admite el importador.
 */
@Composable
private fun ImportRestStepper(seconds: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WizardSpacing.touchTarget)
            .clip(ImportControlShape)
            .background(ImportControlFill)
            .testTag("import-edit-rest"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            symbol = "−15",
            description = "Quitar $REST_STEP_SECONDS segundos",
            enabled = seconds > 0,
            onClick = { onChange(seconds - REST_STEP_SECONDS) },
            small = true,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics { contentDescription = "Abrir la hoja de descanso" }
                .clickable(role = Role.Button) { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                KpknSetFormat.rest(seconds),
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.semantics { stateDescription = importRestText(seconds) },
            )
        }
        StepperButton(
            symbol = "+15",
            description = "Añadir $REST_STEP_SECONDS segundos",
            enabled = seconds < ProgramImportParser.MAX_REST_SECONDS,
            onClick = { onChange(seconds + REST_STEP_SECONDS) },
            small = true,
        )
    }
    if (picking) {
        KpknRestPickerChain(
            primaryTitle = "Descanso",
            primarySeconds = seconds,
            secondaryTitle = null,
            secondarySeconds = null,
            onConfirm = { primary, _ ->
                onChange(primary)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit, small: Boolean = false) {
    Box(
        modifier = Modifier
            .size(WizardSpacing.touchTarget)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = if (small) WizardTypography.cardSubtitle else WizardTypography.controlLabel,
            color = if (enabled) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/** Interruptor de texto con su nota: `Role.Switch`, sin caja; la pista va en tinta de marca cuando está activo. */
@Composable
private fun ImportSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String, note: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(testTag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = WizardTypography.cardSubtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = WizardColors.text)
            note?.let { Text(it, style = WizardTypography.note.copy(fontSize = 12.sp), color = WizardColors.textMuted) }
        }
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(CircleShape)
                .background(if (checked) WizardColors.cta else Color(0xFF262626))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) WizardColors.ctaContent else WizardColors.textMuted),
            )
        }
    }
}

/** El botón principal del panel: el CTA del wizard (tinta sobre negro), apagado si no se puede guardar. Con [compact], de 48 dp. */
@Composable
internal fun ImportCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .heightIn(min = if (compact) WizardSpacing.touchTarget else WizardSpacing.ctaHeight)
            .clip(if (compact) ImportPillShape else WizardShapes.cta)
            .background(if (enabled) WizardColors.cta else WizardColors.ctaDisabled)
            .testTag(testTag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (compact) WizardTypography.cardTitle else WizardTypography.cta,
            color = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
        )
    }
}

/** «Cambiar» del ejercicio: una píldora con filete de 36 dp de alto dentro de su objetivo de 48. */
@Composable
internal fun ImportOutlineButton(label: String, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = WizardTypography.cardSubtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = WizardColors.text, maxLines = 1)
        }
    }
}

/** Una acción de orden o estructura: ficha tonal de 32 dp dentro de su objetivo de 48; «Quitar» lleva el filete de peligro. */
@Composable
private fun ImportActionChip(label: String, onClick: () -> Unit, testTag: String, enabled: Boolean = true, danger: Boolean = false) {
    Box(
        modifier = Modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .alpha(if (enabled) 1f else 0.38f)
            .testTag(testTag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (danger) {
                        Modifier.border(1.dp, ImportDanger.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    } else {
                        Modifier.background(ImportControlFill)
                    },
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = WizardTypography.cardSubtitle.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = if (danger) ImportDanger else WizardColors.text,
                maxLines = 1,
            )
        }
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
