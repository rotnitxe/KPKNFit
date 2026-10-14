package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import kotlin.math.roundToInt

internal enum class WorkoutExerciseContextTab {
    HISTORY,
    TAGS,
    DRAIN,
    ENERGY,
    REPLACE,
    EDIT,
    RM_CALC,
}

internal fun subTagCategoryLabel(category: SubTagCategory): String = when (category) {
    SubTagCategory.MARCA -> "Marca"
    SubTagCategory.SETUP -> "Setup"
    SubTagCategory.TECNICA -> "Técnica"
    SubTagCategory.LIBRE -> "Libre"
}

/**
 * Historial del ejercicio en la hoja de contexto (LS7b): el mismo historial que la hoja de sesión de LS7a, con su fila
 * [HistoryEntryRow]. [maxEntries] y [maxSetsPerEntry] solo recortan la vista (por ejemplo, en el gestor de etiquetas).
 */
@Composable
internal fun WorkoutExerciseHistoryContent(
    history: List<ExerciseHistoryEntry>,
    activeTag: String? = null,
    maxEntries: Int? = null,
    maxSetsPerEntry: Int = 6,
) {
    val visibleHistory = remember(history, maxEntries, maxSetsPerEntry) {
        (maxEntries?.let { history.take(it) } ?: history).map { entry ->
            entry.copy(sets = entry.sets.filter { !it.isWarmup }.take(maxSetsPerEntry.coerceAtLeast(0)))
        }
    }
    ExerciseHistoryContent(history = visibleHistory, activeTag = activeTag)
}

/**
 * Configuración del ejercicio (setup) en la hoja de contexto (LS7b): los setups guardados como chips KPKN, los campos de máquina,
 * asiento, pin, carga base y notas con `KpknTextField`, los cues y la asociación a la etiqueta activa. La lógica no cambia.
 */
@Composable
internal fun WorkoutExerciseSetupContent(
    exercise: Exercise,
    currentSet: ExerciseSet,
    profiles: List<WorkoutContextProfile>,
    activeProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (WorkoutContextProfile) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    sessionAccentColor: Color,
    maxVisibleCues: Int = Int.MAX_VALUE,
    exerciseTag: String? = null,
) {
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var hasSetupChanges by remember { mutableStateOf(false) }
    val activeProfile = remember(activeProfileId, profiles) { profiles.firstOrNull { it.id == activeProfileId } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (profiles.isNotEmpty()) {
            Text(
                text = "Setups guardados".uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles) { profile ->
                    KpknChip(
                        text = profile.tagDisplayTitle().ifBlank { "Sin nombre" },
                        selected = profile.id == activeProfileId,
                        onClick = { onSelectProfile(profile.id) },
                    )
                }
                item {
                    KpknChip(
                        text = "Nuevo",
                        selected = false,
                        onClick = { showNewProfileDialog = true },
                        leadingIcon = KpknLineIcons.Plus,
                    )
                }
            }
        } else {
            KpknSecondaryPill(
                text = "Crear primer setup",
                onClick = { showNewProfileDialog = true },
                modifier = Modifier.fillMaxWidth(),
                icon = KpknLineIcons.Plus,
            )
        }

        // Details of active or temporary setup
        val currentMachine = activeProfile?.machineBrand ?: currentSet.machineBrand.orEmpty()
        val currentSeat = activeProfile?.setupDetails?.seatPosition ?: exercise.setupDetails?.seatPosition.orEmpty()
        val currentPin = activeProfile?.setupDetails?.pinPosition ?: exercise.setupDetails?.pinPosition.orEmpty()
        val currentNotes = activeProfile?.setupDetails?.equipmentNotes ?: exercise.setupDetails?.equipmentNotes.orEmpty()
        val currentBaseLoadKg = com.example.kpkn.domain.workout.BaseLoadPolicy.resolvedForDisplay(
            profile = activeProfile,
            exercise = exercise,
        )

        KpknTextField(
            value = currentMachine,
            onValueChange = { newValue ->
                if (activeProfile != null) {
                    onSaveProfile(activeProfile.copy(machineBrand = newValue.ifBlank { null }))
                } else {
                    onUpdateSet(currentSet.id) { it.copy(machineBrand = newValue.ifBlank { null }) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Máquina o marca",
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KpknTextField(
                value = currentSeat,
                onValueChange = { newValue ->
                    if (activeProfile != null) {
                        onSaveProfile(activeProfile.copy(setupDetails = (activeProfile.setupDetails ?: ExerciseSetupDetails()).copy(seatPosition = newValue.ifBlank { null })))
                    } else {
                        onUpdateExercise { current ->
                            current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(seatPosition = newValue.ifBlank { null }))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = "Asiento",
                singleLine = true,
            )
            KpknTextField(
                value = currentPin,
                onValueChange = { newValue ->
                    if (activeProfile != null) {
                        onSaveProfile(activeProfile.copy(setupDetails = (activeProfile.setupDetails ?: ExerciseSetupDetails()).copy(pinPosition = newValue.ifBlank { null })))
                    } else {
                        onUpdateExercise { current ->
                            current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(pinPosition = newValue.ifBlank { null }))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = "Pin",
                singleLine = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KpknTextField(
                value = currentBaseLoadKg?.toTrimmedNumberString().orEmpty(),
                onValueChange = { value ->
                    val parsed = value.toDoubleOrNull()
                    val hasTag = !exerciseTag.isNullOrBlank()
                    when {
                        // Persist to tagged profile only when a tag is active.
                        hasTag && activeProfile != null -> {
                            val mirrored = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                                activeProfile.setupDetails ?: ExerciseSetupDetails(),
                                parsed,
                            )
                            onSaveProfile(
                                activeProfile.copy(
                                    tagId = activeProfile.tagId ?: exerciseTag,
                                    baseLoadKg = mirrored.baseLoadKg,
                                    barWeightKg = mirrored.barWeightKg,
                                    setupDetails = mirrored,
                                ),
                            )
                        }
                        hasTag && activeProfile == null -> {
                            // Session exercise mirror until user creates/selects a profile;
                            // explicit save-with-tag button also writes a profile.
                            onUpdateExercise { ex ->
                                ex.copy(
                                    setupDetails = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                                        ex.setupDetails ?: ExerciseSetupDetails(),
                                        parsed,
                                    ),
                                )
                            }
                        }
                        else -> {
                            // No tag: session-only (not Room / not suggestion floor).
                            onUpdateExercise { ex ->
                                ex.copy(
                                    setupDetails = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                                        ex.setupDetails ?: ExerciseSetupDetails(),
                                        parsed,
                                    ),
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = "Carga base (kg)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            Text(
                text = if (exerciseTag.isNullOrBlank()) {
                    "Se guarda con una etiqueta (barra vacía, pin mínimo, stack…)"
                } else {
                    "Piso de sugerencias LOAD · etiqueta activa"
                },
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }

        KpknTextField(
            value = currentNotes,
            onValueChange = { newValue ->
                if (activeProfile != null) {
                    onSaveProfile(activeProfile.copy(setupDetails = (activeProfile.setupDetails ?: ExerciseSetupDetails()).copy(equipmentNotes = newValue.ifBlank { null })))
                } else {
                    onUpdateExercise { current ->
                        current.copy(setupDetails = (current.setupDetails ?: ExerciseSetupDetails()).copy(equipmentNotes = newValue.ifBlank { null }))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Notas del setup",
            singleLine = false,
            minLines = 2,
            maxLines = 4,
        )

        val cues = (exercise.setupCues + exercise.executionCues).distinct()
        if (cues.isNotEmpty()) {
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Cues")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cues.take(maxVisibleCues.coerceAtLeast(0)).forEach { cue ->
                        Text(
                            text = "· $cue",
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                        )
                    }
                }
            }
        }

        if (exerciseTag != null) {
            KpknSecondaryPill(
                text = "Asociar setup a «$exerciseTag»",
                onClick = {
                    val mirroredSetup = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                        ExerciseSetupDetails(
                            seatPosition = currentSeat.ifBlank { null },
                            pinPosition = currentPin.ifBlank { null },
                            equipmentNotes = currentNotes.ifBlank { null },
                        ),
                        currentBaseLoadKg,
                    )
                    onSaveProfile(
                        WorkoutContextProfile(
                            id = java.util.UUID.randomUUID().toString(),
                            exerciseKey = "",
                            tagId = exerciseTag,
                            setupDetails = mirroredSetup,
                            machineBrand = currentMachine.ifBlank { null },
                            baseLoadKg = mirroredSetup.baseLoadKg,
                            barWeightKg = mirroredSetup.barWeightKg,
                        )
                    )
                    hasSetupChanges = false
                },
                modifier = Modifier.fillMaxWidth(),
                icon = KpknLineIcons.Plus,
            )
        }
    }

    if (showNewProfileDialog) {
        var newLabel by remember { mutableStateOf("") }
        KpknAlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            title = { Text("Nuevo setup", style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                KpknTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = "Nombre del setup (ej. máquina SmartFit)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Guardar",
                    onClick = {
                        val sessionBase = com.example.kpkn.domain.workout.BaseLoadPolicy.resolvedForDisplay(
                            profile = null,
                            exercise = exercise,
                        )
                        val mirroredSetup = com.example.kpkn.domain.workout.BaseLoadPolicy.withMirroredBaseLoad(
                            exercise.setupDetails ?: ExerciseSetupDetails(),
                            sessionBase,
                        )
                        onSaveProfile(
                            WorkoutContextProfile(
                                id = java.util.UUID.randomUUID().toString(),
                                exerciseKey = "", // Will be set by VM
                                setupLabel = newLabel.ifBlank { "Nuevo Setup" },
                                tagId = exerciseTag,
                                setupDetails = mirroredSetup,
                                baseLoadKg = mirroredSetup.baseLoadKg,
                                barWeightKg = mirroredSetup.barWeightKg,
                            )
                        )
                        showNewProfileDialog = false
                    },
                    enabled = newLabel.isNotBlank(),
                )
            },
            dismissButton = {
                KpknTextAction(text = "Cancelar", onClick = { showNewProfileDialog = false })
            },
        )
    }
}

/**
 * Calculadora de RM (LS7b): el peso y las repeticiones en campos KPKN, el 1RM estimado en `metricM` con su unidad y la tabla de 1 a 10
 * repeticiones; tocar una fila de la tabla devuelve ese peso. [softInputs] ya no cambia el aspecto: se conserva por el cockpit.
 */
@Composable
internal fun WorkoutRmCalcContent(
    bodyWeight: Double? = null,
    currentLoadMode: LoadModeV2? = null,
    onWeightSelected: ((weight: Double, loadMode: LoadModeV2?) -> Unit)? = null,
    sessionAccentColor: Color = WizardColors.text,
    @Suppress("UNUSED_PARAMETER") softInputs: Boolean = false,
    initialWeightText: String = "",
    initialRepsText: String = "",
) {
    var rmWeightText by remember { mutableStateOf(initialWeightText) }
    var rmRepsText by remember { mutableStateOf(initialRepsText) }
    val isAssisted = currentLoadMode == LoadModeV2.ASSISTED
    val isLastre = currentLoadMode == LoadModeV2.LASTRE
    val weightLabel = when (currentLoadMode) {
        LoadModeV2.LASTRE -> "Lastre (kg)"
        LoadModeV2.ASSISTED -> "Asistencia (kg)"
        else -> "Peso (kg)"
    }
    val effectiveLoad = remember(rmWeightText, isLastre, isAssisted, bodyWeight) {
        val w = rmWeightText.toDoubleOrNull() ?: 0.0
        when {
            isLastre && bodyWeight != null && bodyWeight > 0 -> bodyWeight + w
            isAssisted && bodyWeight != null && bodyWeight > 0 -> (bodyWeight - w).coerceAtLeast(0.0)
            else -> w
        }
    }
    val rmResult = remember(effectiveLoad, rmRepsText) {
        val r = rmRepsText.toIntOrNull() ?: 0
        if (effectiveLoad > 0 && r > 0) calculateHybrid1RM(effectiveLoad, r) else null
    }
    val rmTable = remember(rmResult, isAssisted, isLastre, bodyWeight) {
        if (rmResult == null) emptyList()
        else {
            val estRms = (1..10).map { reps ->
                val estLoad = rmResult / (1.0 + reps / 30.0) // Epley inverse
                reps to estLoad
            }
            when {
                isAssisted && bodyWeight != null && bodyWeight > 0 -> {
                    estRms.map { (reps, load) ->
                        val assistance = (bodyWeight - load).coerceAtLeast(0.0)
                        reps to Triple(load, assistance, null)
                    }
                }
                isLastre && bodyWeight != null && bodyWeight > 0 -> {
                    estRms.map { (reps, load) ->
                        val lastre = (load - bodyWeight).coerceAtLeast(0.0)
                        reps to Triple(load, null, lastre)
                    }
                }
                else -> estRms.map { (reps, load) -> reps to Triple(load, null, null) }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            KpknTextField(
                value = rmWeightText,
                onValueChange = { rmWeightText = it },
                label = weightLabel,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            KpknTextField(
                value = rmRepsText,
                onValueChange = { rmRepsText = it },
                label = "Repeticiones",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (rmResult != null && rmTable.isNotEmpty()) {
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = if (isAssisted || isLastre) "1RM estimado equivalente" else "1RM estimado")
                Spacer(Modifier.height(6.dp))
                KpknMetric(
                    value = rmResult.toTrimmedNumberString(),
                    unit = "kg",
                    style = KpknType.metricM,
                    color = SymbolPalette.ok,
                )
                if ((isAssisted || isLastre) && bodyWeight != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Peso corporal (solo cálculo): ${bodyWeight.toTrimmedNumberString()} kg",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Repeticiones máximas")
                if (isAssisted || isLastre) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isAssisted) "Kilos de asistencia" else "Kilos de lastre",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val rmRows = rmTable.map { (reps, triple) -> reps to rmDisplayWeight(triple) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RmTableColumn(
                        rows = rmRows.take(5),
                        onWeightSelected = { weight -> onWeightSelected?.invoke(weight, currentLoadMode) },
                        modifier = Modifier.weight(1f),
                    )
                    RmTableColumn(
                        rows = rmRows.drop(5),
                        onWeightSelected = { weight -> onWeightSelected?.invoke(weight, currentLoadMode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** El peso que muestra la tabla RM para una fila: el de asistencia o lastre si lo hay, si no el estimado. */
private fun rmDisplayWeight(triple: Triple<Double, Double?, Double?>): Double =
    triple.second ?: triple.third ?: triple.first

/** Una columna de la tabla RM: cinco filas de 48 dp, «1 RM» a la izquierda y el peso a la derecha, sin tarjeta por celda. */
@Composable
private fun RmTableColumn(
    rows: List<Pair<Int, Double>>,
    onWeightSelected: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        rows.forEachIndexed { index, (reps, weight) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KpknSurface.divider),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = { onWeightSelected(weight) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$reps RM",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                )
                KpknMetric(
                    value = weight.toTrimmedNumberString(),
                    unit = "kg",
                    style = KpknType.bodyStrong.copy(fontFeatureSettings = "tnum"),
                    unitStyle = KpknType.caption,
                )
            }
        }
    }
}

internal fun normalizeWorkoutMuscleKey(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .trim()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")

internal fun workoutCatalogInfo(exercise: Exercise): ExerciseMuscleInfo? {
    return resolveCatalogExerciseInfo(
        catalogConfigurationId = exercise.catalogConfigurationId,
        exerciseDbId = exercise.exerciseDbId,
        exerciseId = exercise.exerciseId,
        exerciseName = exercise.name,
    )
}

internal fun workoutCatalogInfo(exercise: CompletedExercise): ExerciseMuscleInfo? {
    return resolveCatalogExerciseInfo(
        catalogConfigurationId = exercise.catalogConfigurationId,
        exerciseDbId = exercise.exerciseDbId,
        exerciseId = exercise.exerciseId,
        exerciseName = exercise.exerciseName,
    )
}

internal fun buildExerciseDrainOverlayState(
    exerciseName: String,
    drain: PredictedDrain,
    involvedMuscles: List<InvolvedMuscle>,
): ExerciseDrainOverlayState {
    val items = mutableListOf<ExerciseDrainOverlayItem>()

    if (drain.cns > 0) {
        items += ExerciseDrainOverlayItem(
            label = "Energia",
            delta = drain.cns.coerceAtLeast(1),
            channel = ExerciseDrainOverlayChannel.ENERGY,
        )
    }
    if (drain.spinal > 0) {
        items += ExerciseDrainOverlayItem(
            label = "Columna",
            delta = drain.spinal.coerceAtLeast(1),
            channel = ExerciseDrainOverlayChannel.BACK,
        )
    }

    if (drain.muscular > 0) {
        val totalWeight = involvedMuscles.sumOf { resolveMuscleVolumeContribution(it) }.takeIf { it > 0.0 } ?: 0.0
        val topMuscles = involvedMuscles
            .sortedByDescending { resolveMuscleVolumeContribution(it) }
            .take(3)

        if (topMuscles.isNotEmpty() && totalWeight > 0.0) {
            topMuscles.forEach { involved ->
                val label = involved.emphasis
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "${involved.muscle} · $it" }
                    ?: involved.muscle
                val share = resolveMuscleVolumeContribution(involved) / totalWeight
                val delta = (share * drain.muscular).roundToInt().coerceAtLeast(1)
                items += ExerciseDrainOverlayItem(
                    label = label,
                    delta = delta,
                    channel = ExerciseDrainOverlayChannel.MUSCLE,
                )
            }
        } else {
            items += ExerciseDrainOverlayItem(
                label = "Muscular",
                delta = drain.muscular.coerceAtLeast(1),
                channel = ExerciseDrainOverlayChannel.MUSCLE,
            )
        }
    }

    return ExerciseDrainOverlayState(
        key = System.currentTimeMillis(),
        exerciseName = exerciseName,
        items = items,
    )
}
