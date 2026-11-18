package com.example.kpkn.screens.workout

import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.performance.StrengthModel
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.*
import java.util.Locale
import kotlin.math.roundToInt
import com.example.kpkn.ui.components.KpknAlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

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

@Composable
internal fun WorkoutExerciseHistoryContent(
    history: List<ExerciseHistoryEntry>,
    activeTag: String? = null,
    maxEntries: Int? = null,
    maxSetsPerEntry: Int = 6,
) {
    val visibleHistory = remember(history, maxEntries) {
        maxEntries?.let { history.take(it) } ?: history
    }
    if (visibleHistory.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Sin historial registrado", style = KpknType.body, color = WizardColors.textMuted)
        }
        return
    }

    val groupedHistory = remember(visibleHistory) {
        visibleHistory.groupBy { entry ->
            val date = try {
                java.time.LocalDate.parse(entry.date.take(10))
            } catch (e: Exception) {
                java.time.LocalDate.now()
            }
            val now = java.time.LocalDate.now()
            when {
                date.isAfter(now.minusWeeks(1)) -> "Esta semana"
                date.isAfter(now.minusWeeks(2)) -> "Semana pasada"
                date.isAfter(now.withDayOfMonth(1)) -> "Este mes"
                else -> {
                    val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "CL"))
                        .replaceFirstChar { it.uppercase() }
                    "$month ${date.year}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groupedHistory.forEach { (groupLabel, entries) ->
            var expanded by rememberSaveable(groupLabel) { mutableStateOf(groupLabel == "Esta semana" || groupLabel == "Semana pasada") }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Cabecera de módulo: la etiqueta a la izquierda y el chevron a la derecha; toda la fila pliega el grupo.
                KpknModuleHeader(
                    label = groupLabel,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { expanded = !expanded },
                    action = {
                        Icon(
                            imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = WizardColors.textMuted,
                        )
                    },
                )

                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.forEach { entry ->
                            val isTagMatch = activeTag != null && entry.tag == activeTag
                            // El récord se marca con el acento de músculo, no con amarillo; un fallo, con `danger`.
                            val accent = when (entry.latestHistoryColor) {
                                HistoryColorV2.YELLOW -> SymbolPalette.musculo
                                HistoryColorV2.RED -> WizardColors.danger
                                else -> if (isTagMatch) SymbolPalette.musculo else null
                            }
                            KpknModule(
                                modifier = Modifier.fillMaxWidth(),
                                accent = accent,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(formatHistoryDate(entry.date, java.time.LocalDate.now()), style = KpknType.bodyStrong, color = WizardColors.text)
                                        val tag = entry.tag
                                        if (tag != null) HistoryPill(tag)
                                    }
                                    val e1rm = entry.e1rm
                                    if (e1rm != null) {
                                        Text(
                                            "e1RM ${String.format(java.util.Locale("es", "CL"), "%.1f", e1rm)} kg",
                                            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                                            color = WizardColors.textMuted,
                                        )
                                    }
                                }
                                val workingSets = entry.sets.filter { !it.isWarmup }
                                workingSets.take(maxSetsPerEntry.coerceAtLeast(0)).forEach { s ->
                                    Spacer(Modifier.height(8.dp))
                                    HistorySetLine(s)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Una serie de la copia del historial del gestor de etiquetas: cifras tabulares, coma decimal y «×» con espacios. */
@Composable
private fun HistorySetLine(s: CompletedSet) {
    val sideLabel = when (s.side) {
        "left" -> "Izq"
        "right" -> "Der"
        else -> null
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.isFailedSet || s.failureReason == "execution_error" || !s.failureReason.isNullOrBlank()) {
            val reason = s.failureReason?.takeIf { it.isNotBlank() && it != "execution_error" && it != "Serie marcada como fallida" }
            HistoryDangerPill(
                buildString {
                    if (sideLabel != null) append("$sideLabel · ")
                    append("❌ Fallida")
                    if (reason != null) append(": $reason")
                },
            )
        } else {
            Text(
                text = buildString {
                    if (sideLabel != null) append("$sideLabel · ")
                    if (s.weight > 0) append("${s.weight.toTrimmedNumberString()} kg")
                    if (s.weight > 0 && s.reps > 0) append(" × ")
                    if (s.reps > 0) append("${s.reps} reps")
                    if (s.rpe != null) append(" · RPE ${s.rpe}")
                    if ((s.partialReps ?: 0) > 0) append(" · +${s.partialReps} parciales")
                    if ((s.assistedReps ?: 0) > 0) append(" · ${s.assistedReps} con ayuda")
                    // Pulido v1 (15): el ROM registrado en la serie.
                    s.rom?.let { append(" · ").append(com.example.kpkn.screens.workout.components.liveSetRomLabel(it)) }
                },
                style = KpknType.body.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.text,
            )
            if (s.isFailure) {
                HistoryDangerPill("F")
            }
        }
    }
}

/** Etiqueta de la sesión: píldora `inset` con filete de 1 dp en blanco al 12 %, sobre el módulo. */
@Composable
private fun HistoryPill(text: String) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = KpknType.chip, color = WizardColors.text)
    }
}

/** Aviso de serie fallida: píldora tintada de `danger`. */
@Composable
private fun HistoryDangerPill(text: String) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(WizardColors.danger.copy(alpha = 0.16f), KpknShape.pill)
            .border(1.dp, WizardColors.danger.copy(alpha = 0.40f), KpknShape.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = KpknType.chip, color = WizardColors.danger)
    }
}

/** Etiqueta de sección de las hojas de contexto: eyebrow KPKN en mayúsculas, en `textMuted`. */
@Composable
private fun ContextLabel(text: String) {
    Text(
        text = text.uppercase(java.util.Locale("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
    )
}

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (profiles.isNotEmpty()) {
            ContextLabel("Setups guardados")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles) { profile ->
                    val isSelected = profile.id == activeProfileId
                    KpknChip(
                        text = profile.tagDisplayTitle().ifBlank { "Sin nombre" },
                        selected = isSelected,
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
            HorizontalDivider(color = KpknSurface.divider)
        } else {
            KpknSecondaryPill(
                text = "Crear primer Setup",
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

        // Texto de la carga base mientras se escribe: el modelo no guarda «102,» y la coma no debe borrarse al teclearla.
        var baseLoadText by remember(activeProfileId, exercise.id) {
            mutableStateOf(currentBaseLoadKg?.toTrimmedNumberString().orEmpty())
        }
        KpknTextField(
            value = baseLoadText,
            onValueChange = { value ->
                baseLoadText = value
                val parsed = value.replace(',', '.').toDoubleOrNull()
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
        // El campo no tiene pie de texto propio: la ayuda va debajo, en `caption`.
        Text(
            if (exerciseTag.isNullOrBlank()) {
                "Se guarda con una etiqueta (barra vacía, pin mínimo, stack…)"
            } else {
                "Piso de sugerencias LOAD · etiqueta activa"
            },
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 4.dp),
        )

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
            label = "Notas de set-up",
            singleLine = false,
            minLines = 2,
            maxLines = 4,
        )

        val cues = (exercise.setupCues + exercise.executionCues).distinct()
        if (cues.isNotEmpty()) {
            ContextLabel("Cues")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cues.take(maxVisibleCues.coerceAtLeast(0)).forEach { cue ->
                    Text("- $cue", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
        }

        if (exerciseTag != null) {
            Spacer(Modifier.height(4.dp))
            KpknModule(
                modifier = Modifier.fillMaxWidth(),
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = KpknLineIcons.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = sessionAccentColor,
                    )
                    Text(
                        "Asociar set-up a \"$exerciseTag\"",
                        modifier = Modifier.weight(1f),
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                }
            }
        }
    }

    if (showNewProfileDialog) {
        var newLabel by remember { mutableStateOf("") }
        com.example.kpkn.screens.workout.components.LiveSheet(title = "Nuevo setup", onDismiss = { showNewProfileDialog = false },
            tag = "live-new-setup", footer = { close ->
                com.example.kpkn.screens.workout.components.LiveSheetPrimary("Guardar", {
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
                }, enabled = newLabel.isNotBlank())
                com.example.kpkn.screens.workout.components.LiveSheetTextAction("Cancelar", close)
            }) {
            com.example.kpkn.screens.workout.components.LiveSheetLabeledField("Nombre del setup", newLabel, { newLabel = it },
                placeholder = "Ej.: máquina del gimnasio", tag = "live-new-setup-name")
        }
    }
}

/**
 * Cargas de la tabla RM (1 a 10 repeticiones) a partir del 1RM que muestra la calculadora (LR0): la inversa de la misma fórmula
 * (StrengthModel), así la fila «1 RM» es ese 1RM y las demás bajan de forma monótona. Pura; la prueba es WorkoutRmTableTest.
 */
internal fun rmTableLoads(estimated1RM: Double): List<Pair<Int, Double>> =
    (1..10).map { reps -> reps to StrengthModel.weightFor(estimated1RM, reps) }

@Composable
internal fun WorkoutRmCalcContent(
    bodyWeight: Double? = null,
    currentLoadMode: LoadModeV2? = null,
    onWeightSelected: ((weight: Double, loadMode: LoadModeV2?) -> Unit)? = null,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    softInputs: Boolean = false,
    exercise: Exercise? = null,
    exerciseOptions: List<Exercise> = emptyList(),
    activeExerciseId: String? = exercise?.id,
    currentSets: List<CompletedSet> = emptyList(),
) {
    var rmWeightText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var rmRepsText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var selectedExerciseId by androidx.compose.runtime.saveable.rememberSaveable(activeExerciseId) {
        mutableStateOf(activeExerciseId.orEmpty())
    }
    val selectedExercise = remember(exercise, exerciseOptions, selectedExerciseId) {
        exerciseOptions.firstOrNull { it.id == selectedExerciseId } ?: exercise
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolvedSelectedExercise = remember(selectedExercise, context) {
        val source = selectedExercise ?: return@remember null
        if (source.reference1RM != null && source.prFor1RM != null && source.referenceTrainingMax != null) {
            source
        } else {
            val configId = com.example.kpkn.data.exercises.sharedLoadConfigurationId(source)
            val repo = configId?.let {
                runCatching {
                    com.example.kpkn.data.repository.SharedExerciseLoadReferenceRepository.getInstance(context)
                }.getOrNull()
            }
            if (repo == null) {
                source
            } else {
                val cfgId = configId ?: return@remember source
                val exerciseKey = source.resolvedCanonicalExerciseId()
                val tag = source.workoutTagId
                val unit = source.trainingMode.toUnitModeV2()
                val mode = source.sets.firstNotNullOfOrNull { it.loadModeV2 } ?: LoadModeV2.LOAD
                fun refFor(kind: SharedLoadReferenceKind) = repo.getCompatible(
                    SharedExerciseLoadReference(
                        id = sharedLoadReferenceId(
                            exerciseKey, cfgId, tag, unit, mode, source.loadQuantityConvention, kind,
                        ),
                        exerciseKey = exerciseKey,
                        configurationId = cfgId,
                        tagId = tag,
                        unitMode = unit,
                        loadMode = mode,
                        quantityConvention = source.loadQuantityConvention,
                        kind = kind,
                        baseValueKg = 0.0,
                        sourceAtMs = 0L,
                        sourceId = "",
                    ),
                )
                val prRef = refFor(SharedLoadReferenceKind.ESTIMATED_PR)
                val oneRmRef = refFor(SharedLoadReferenceKind.ONE_RM)
                val tmRef = refFor(SharedLoadReferenceKind.TRAINING_MAX)
                val obsRef = refFor(SharedLoadReferenceKind.OBSERVED_SESSION)
                val shared1Rm = prRef?.suggestedValueKg() ?: oneRmRef?.suggestedValueKg() ?: obsRef?.suggestedValueKg()
                source.copy(
                    reference1RM = source.reference1RM ?: shared1Rm,
                    prFor1RM = source.prFor1RM ?: prRef?.let { r ->
                        val w = r.prWeightKg
                        val reps = r.prReps
                        if (w != null && w > 0.0 && reps != null && reps > 0) PrReference(w, reps, r.effortMode, r.effortValue) else null
                    },
                    referenceTrainingMax = source.referenceTrainingMax ?: tmRef?.suggestedValueKg(),
                )
            }
        }
    }
    val selectedLoadMode = if (selectedExercise?.id == activeExerciseId) currentLoadMode else selectedExercise?.sets
        ?.mapNotNull { it.loadModeV2 }?.distinct()?.singleOrNull()
    val isAssisted = selectedLoadMode == LoadModeV2.ASSISTED
    val isLastre = selectedLoadMode == LoadModeV2.LASTRE
    LaunchedEffect(selectedExercise?.id) {
        rmWeightText = ""
        rmRepsText = ""
    }
    val currentSessionBest = remember(currentSets, selectedExercise, selectedLoadMode) {
        if (selectedExercise?.id != activeExerciseId) null else currentSets.asSequence()
            .mapNotNull { eligibleWorkoutRmEstimate(it, selectedLoadMode ?: LoadModeV2.LOAD) }
            .maxOrNull()
    }
    val sharedReference = remember(resolvedSelectedExercise, currentSessionBest) {
        currentSessionBest ?: resolvedSelectedExercise?.let { source ->
            declaredWorkoutRmReference(source)
        }
    }
    val canApplySelectedWeight = exerciseOptions.isEmpty() || selectedExercise?.id == activeExerciseId
    val hydratedFromSharedRepo = selectedExercise != null &&
        declaredWorkoutRmReference(selectedExercise) == null &&
        resolvedSelectedExercise?.let(::declaredWorkoutRmReference) != null
    val referenceSource = resolvedSelectedExercise?.let { source -> when {
        currentSessionBest != null -> "Mejor serie compatible de hoy"
        source.reference1RM?.let { it.isFinite() && it > 0.0 } == true ->
            if (hydratedFromSharedRepo) "1RM compartido de otra sesión" else "1RM declarado para esta variante"
        source.prFor1RM?.let { it.weight > 0.0 && it.reps > 0 } == true ->
            if (hydratedFromSharedRepo) "PR compartido de otra sesión" else "PR declarado para esta variante"
        source.calculated1RM?.let { it.isFinite() && it > 0.0 } == true -> "1RM calculado guardado"
        else -> null
    } }
    val weightLabel = when (selectedLoadMode) {
        LoadModeV2.LASTRE -> "Lastre (kg)"
        LoadModeV2.ASSISTED -> "Asistencia (kg)"
        else -> "Peso (kg)"
    }
    val effectiveLoad = remember(rmWeightText, isLastre, isAssisted, bodyWeight) {
        val w = rmWeightText.replace(',', '.').toDoubleOrNull() ?: 0.0
        when {
            isLastre && bodyWeight != null && bodyWeight > 0 -> bodyWeight + w
            isAssisted && bodyWeight != null && bodyWeight > 0 -> (bodyWeight - w).coerceAtLeast(0.0)
            else -> w
        }
    }
    val rmResult = remember(effectiveLoad, rmRepsText) {
        val r = rmRepsText.toIntOrNull() ?: 0
        if (effectiveLoad > 0 && r > 0) StrengthModel.estimate1RM(effectiveLoad, r) else null
    }
    val rmTable = remember(rmResult, isAssisted, isLastre, bodyWeight) {
        if (rmResult == null) emptyList()
        else {
            val estRms = rmTableLoads(rmResult)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (exerciseOptions.distinctBy { it.id }.size > 1) {
            Text("Ejercicio de referencia", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .65f))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(exerciseOptions.distinctBy { it.id }, key = { it.id }) { option ->
                    FilterChip(
                        selected = option.id == selectedExercise?.id,
                        onClick = { selectedExerciseId = option.id },
                        label = { Text(displayWorkoutExerciseName(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        if (selectedExercise != null) {
            if (sharedReference != null) {
                Text("${referenceSource ?: "Referencia compartida"} · ${String.format(Locale.forLanguageTag("es"), "%.1f", sharedReference)} kg",
                    style = MaterialTheme.typography.labelSmall, color = sessionAccentColor, modifier = Modifier.testTag("live-rm-shared-reference"))
            } else {
                val trainingMax = (resolvedSelectedExercise ?: selectedExercise).referenceTrainingMax?.takeIf { it.isFinite() && it > 0.0 }
                if (trainingMax != null) {
                    Text("TM de trabajo · ${String.format(Locale.forLanguageTag("es"), "%.1f", trainingMax)} kg · no se convierte en 1RM",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .65f), modifier = Modifier.testTag("live-rm-training-max-only"))
                } else {
                    Text("Sin referencia compatible declarada para ${displayWorkoutExerciseName(selectedExercise)}",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .55f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (softInputs) {
                com.example.kpkn.screens.workout.components.WorkoutSoftNumberField(
                    value = rmWeightText,
                    onValueChange = { rmWeightText = it },
                    label = weightLabel,
                    decimal = true,
                    modifier = Modifier.weight(1f),
                )
                com.example.kpkn.screens.workout.components.WorkoutSoftNumberField(
                    value = rmRepsText,
                    onValueChange = { rmRepsText = it },
                    label = "Repeticiones",
                    modifier = Modifier.weight(1f),
                )
            } else {
                OutlinedTextField(
                    value = rmWeightText,
                    onValueChange = { rmWeightText = it },
                    label = { Text(weightLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rmRepsText,
                    onValueChange = { rmRepsText = it },
                    label = { Text("Repeticiones") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (rmResult != null && rmTable.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A3A1A),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isAssisted || isLastre) "e1RM equiv.: ${String.format(Locale.forLanguageTag("es"), "%.1f", rmResult)} kg" else "e1RM: ${String.format(Locale.forLanguageTag("es"), "%.1f", rmResult)} kg",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                    )
            if ((isAssisted || isLastre) && bodyWeight != null) {
                        Text("Peso corporal (solo cálculo): ${bodyWeight.toTrimmedNumberString()} kg", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Tabla RM", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rmTable.forEach { (reps, triple) ->
                    val (estLoad, assistance, lastre) = triple
                    val displayWeight = when {
                        assistance != null -> assistance
                        lastre != null -> lastre
                        else -> estLoad
                    }
                    val suffix = when {
                        assistance != null -> "kg asistencia"
                        lastre != null -> "kg lastre"
                        else -> "kg"
                    }
                        Surface(
                        onClick = { if (canApplySelectedWeight) onWeightSelected?.invoke(displayWeight, selectedLoadMode) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2A2A2A),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.widthIn(min = 72.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${reps}RM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = sessionAccentColor)
                            Text(String.format(Locale.forLanguageTag("es"), "%.1f", displayWeight), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(suffix, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                        }
                    }
                }
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

