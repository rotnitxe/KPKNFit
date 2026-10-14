package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

internal fun exerciseHasPlannedIntensity(exercise: Exercise): Boolean = exercise.sets.any { set ->
    fun UnilateralTarget.hasPlannedIntensity(): Boolean =
        targetRPE != null ||
            targetRIR != null ||
            intensityMode == IntensityMode.RPE ||
            intensityMode == IntensityMode.RIR ||
            intensityMode == IntensityMode.FAILURE ||
            intensityMode == IntensityMode.SOLO_RM

    set.targetRPE != null ||
        set.targetRIR != null ||
        set.targetPercentageRM != null ||
        set.isFailure ||
        set.intensityMode == IntensityMode.RPE ||
        set.intensityMode == IntensityMode.RIR ||
        set.intensityMode == IntensityMode.FAILURE ||
        set.intensityMode == IntensityMode.SOLO_RM ||
        set.leftTarget?.hasPlannedIntensity() == true ||
        set.rightTarget?.hasPlannedIntensity() == true
}

/**
 * Etiquetas del ejercicio (LS7b): las sugeridas y las del usuario como chips KPKN, una etiqueta personalizada con su «Aplicar» y
 * «Eliminar etiqueta» cuando hay una activa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseTagSheetContent(
    currentTag: String?,
    onTagSet: (String) -> Unit,
    onDismiss: () -> Unit,
    userTags: List<String> = emptyList(),
    suggestedTag: String? = null,
    profiles: List<WorkoutContextProfile> = emptyList(),
) {
    fun profileForTagText(tag: String): WorkoutContextProfile? =
        profiles.firstOrNull { profile ->
            profile.tagId == tag ||
                profile.setupLabel == tag ||
                profile.persistentTagName() == tag ||
                profile.tagDisplayTitle() == tag
        }

    fun displayTag(tag: String): String =
        profileForTagText(tag)?.tagDisplayTitle() ?: tag

    var tagText by remember { mutableStateOf(currentTag ?: "") }
    val commonTags = remember(userTags, suggestedTag, profiles) {
        val base = userTags.map(::displayTag).filter { it.isNotBlank() }.distinct()
        val displayedSuggested = suggestedTag?.let(::displayTag)
        if (displayedSuggested != null && displayedSuggested !in base) {
            listOf(displayedSuggested) + base
        } else {
            base
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Etiquetas sugeridas".uppercase(java.util.Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            commonTags.forEach { tag ->
                val isSuggested = tag == suggestedTag
                KpknChip(
                    text = if (isSuggested) "$tag · sugerida" else tag,
                    selected = tagText == tag,
                    onClick = {
                        tagText = tag
                        onTagSet(tag)
                    },
                )
            }
        }
        KpknTextField(
            value = tagText,
            onValueChange = { tagText = it },
            label = "Etiqueta personalizada",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                KpknIconButton(
                    icon = KpknLineIcons.Check,
                    contentDescription = "Aplicar",
                    onClick = { onTagSet(tagText) },
                )
            },
        )
        if (!currentTag.isNullOrBlank()) {
            KpknTextAction(
                text = "Eliminar etiqueta",
                onClick = {
                    onTagSet("")
                    tagText = ""
                },
                color = WizardColors.danger,
                modifier = Modifier.align(Alignment.End),
            )
        }
        KpknPrimaryPill(
            text = "Guardar",
            onClick = {
                if (tagText.isNotBlank() && tagText.trim() != (currentTag?.trim() ?: "")) {
                    onTagSet(tagText.trim())
                }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Campo de texto de los diálogos de etiqueta: el de KPKN con su etiqueta, de una línea por defecto. */
@Composable
internal fun WorkoutTagFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    KpknTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
    )
}

internal data class WorkoutTagListRow(
    val tag: WorkoutTag,
    val title: String,
    val lastLoadLabel: String,
    val isActive: Boolean,
)

internal fun showTagEducation(seen: Boolean, @Suppress("UNUSED_PARAMETER") emptyRows: Boolean): Boolean = !seen

internal const val WORKOUT_TAG_EDUCATION_COPY =
    "Un ejercicio lo puedes llevar a cabo con distintas técnicas o máquinas diferentes. " +
        "Eso puede cambiar bastante las cargas que puedes mover. Para esos casos y más, " +
        "puedes asignar etiquetas; guardan su propio historial y sobrecarga progresiva, " +
        "y para cambiar entre etiquetas, adaptamos las cargas a una u otra para que tu " +
        "progreso sea fluído."

internal const val WORKOUT_TAG_EMPTY_COPY = "Este ejercicio aún no tiene etiquetas."

/** Lista de etiquetas del ejercicio: cada una es un módulo que se elige al tocarlo; «Crear etiqueta nueva» al final. */
@Composable
internal fun WorkoutTagListOverlay(
    rows: List<WorkoutTagListRow>,
    onSelectTag: (String) -> Unit,
    onCreateTag: () -> Unit,
    onDismiss: () -> Unit,
    hasSeenEducation: Boolean = true,
    onOpened: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onOpened() }
    val showEducation = remember {
        showTagEducation(hasSeenEducation, rows.isEmpty())
    }
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiquetas", style = KpknType.dialogTitle, color = WizardColors.text) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showEducation) {
                    Text(
                        text = WORKOUT_TAG_EDUCATION_COPY,
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                } else if (rows.isEmpty()) {
                    Text(
                        text = WORKOUT_TAG_EMPTY_COPY,
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                }
                rows.forEach { row ->
                    KpknModule(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectTag(row.tag.id) },
                        onClickLabel = row.title,
                        accent = if (row.isActive) SymbolPalette.columna else null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = row.title,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = row.lastLoadLabel,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                }
                KpknSecondaryPill(
                    text = "Crear etiqueta nueva",
                    onClick = onCreateTag,
                    modifier = Modifier.fillMaxWidth(),
                    icon = KpknLineIcons.Plus,
                )
            }
        },
        confirmButton = {
            KpknPrimaryPill(text = "Cerrar", onClick = onDismiss)
        },
    )
}

/** Diálogo para crear una etiqueta con su set-up opcional (marca, carga base y notas). */
@Composable
internal fun WorkoutCreateTagOverlay(
    onCreate: (name: String, setup: TagSetupInput?) -> CreateTagResult,
    onDismiss: () -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }
    var newMachineBrand by remember { mutableStateOf("") }
    var newBaseLoad by remember { mutableStateOf("") }
    var newSetupNotes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva etiqueta", style = KpknType.dialogTitle, color = WizardColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkoutTagFilledTextField(
                    value = newTagName,
                    onValueChange = {
                        newTagName = it
                        errorMessage = null
                    },
                    label = "Nombre de la etiqueta",
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = KpknType.caption,
                        color = WizardColors.danger,
                    )
                }
                KpknModuleHeader(label = "Set-up de máquina (opcional)")
                WorkoutTagFilledTextField(
                    value = newMachineBrand,
                    onValueChange = { newMachineBrand = it },
                    label = "Marca o máquina",
                )
                WorkoutTagFilledTextField(
                    value = newBaseLoad,
                    onValueChange = { newBaseLoad = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = "Carga base (kg)",
                )
                WorkoutTagFilledTextField(
                    value = newSetupNotes,
                    onValueChange = { newSetupNotes = it },
                    label = "Notas del setup",
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            KpknPrimaryPill(
                text = "Crear",
                onClick = {
                    if (newTagName.isNotBlank() || newMachineBrand.isNotBlank()) {
                        val setup = TagSetupInput(
                            machineBrand = newMachineBrand,
                            baseLoadKg = newBaseLoad.replace(',', '.').toDoubleOrNull(),
                            setupNotes = newSetupNotes,
                        )
                        when (val result = onCreate(newTagName, setup.takeIf { it.hasContent })) {
                            is CreateTagResult.Created -> Unit
                            is CreateTagResult.Duplicate -> {
                                errorMessage = "Ya existe una etiqueta con el nombre «${result.existingName}»."
                            }
                            CreateTagResult.InvalidName -> {
                                errorMessage = "El nombre no es válido."
                            }
                        }
                    }
                },
                enabled = newTagName.isNotBlank() || newMachineBrand.isNotBlank(),
            )
        },
        dismissButton = {
            KpknTextAction(text = "Cancelar", onClick = onDismiss)
        },
    )
}

/**
 * Gestor de una etiqueta (LS7b): el nombre, el set-up, el historial (la lista de [WorkoutExerciseHistoryContent]), las
 * sub-etiquetas con su casilla y el reinicio o borrado. Cada bloque va en su módulo o con su divisor.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutTagManagerModal(
    tag: WorkoutTag,
    exerciseId: String,
    onRename: (String) -> RenameTagResult,
    onDelete: () -> Unit,
    onResetHistory: () -> Unit,
    onAddSubTag: (String, SubTagCategory) -> Unit,
    onRemoveSubTag: (String) -> Unit,
    onToggleSubTagActive: (String) -> Unit,
    activeSubTagIds: List<String>,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit = {},
    history: List<ExerciseHistoryEntry> = emptyList(),
    machineBrand: String = "",
    baseLoadKg: String = "",
    setupNotes: String = "",
    onSaveSetup: (TagSetupInput) -> Unit = {},
) {
    var editName by remember { mutableStateOf(tag.name) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var showAddSubTag by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var newSubTagName by remember { mutableStateOf("") }
    var newSubTagCategory by remember { mutableStateOf(SubTagCategory.LIBRE) }
    var brand by remember(tag.id, machineBrand) { mutableStateOf(machineBrand) }
    var baseLoad by remember(tag.id, baseLoadKg) { mutableStateOf(baseLoadKg) }
    var notes by remember(tag.id, setupNotes) { mutableStateOf(setupNotes) }

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = tag.name.ifBlank { "Etiqueta" },
                style = KpknType.dialogTitle,
                color = WizardColors.text,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkoutTagFilledTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = "Nombre",
                )

                if (editName != tag.name) {
                    KpknTextAction(
                        text = "Guardar nombre",
                        onClick = {
                            when (val result = onRename(editName)) {
                                RenameTagResult.Success -> renameError = null
                                is RenameTagResult.Duplicate -> {
                                    renameError = "Ya existe una etiqueta con el nombre «${result.existingName}»."
                                }
                                RenameTagResult.InvalidName -> renameError = "El nombre no es válido."
                                RenameTagResult.NotFound -> renameError = "No se encontró la etiqueta."
                            }
                        },
                    )
                }
                renameError?.let { message ->
                    Text(
                        text = message,
                        style = KpknType.caption,
                        color = WizardColors.danger,
                    )
                }

                SheetDivider()

                KpknModuleHeader(label = "Set-up")
                WorkoutTagFilledTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = "Marca o máquina",
                )
                WorkoutTagFilledTextField(
                    value = baseLoad,
                    onValueChange = { baseLoad = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = "Carga base (kg)",
                )
                WorkoutTagFilledTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notas del setup",
                    singleLine = false,
                )
                KpknSecondaryPill(
                    text = "Guardar setup",
                    onClick = {
                        onSaveSetup(
                            TagSetupInput(
                                machineBrand = brand,
                                baseLoadKg = baseLoad.replace(',', '.').toDoubleOrNull(),
                                setupNotes = notes,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                SheetDivider()

                KpknModuleHeader(label = "Historial")
                WorkoutExerciseHistoryContent(
                    history = history,
                    activeTag = tag.name,
                    maxEntries = 4,
                    maxSetsPerEntry = 3,
                )

                SheetDivider()

                if (tag.subTags.isNotEmpty()) {
                    KpknModuleHeader(label = "Sub-etiquetas")
                    tag.subTags.forEach { subTag ->
                        val isActive = subTag.id in activeSubTagIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KpknCheckRow(
                                checked = isActive,
                                title = subTag.name,
                                subtitle = subTagCategoryLabel(subTag.category),
                                onClick = { onToggleSubTagActive(subTag.id) },
                                modifier = Modifier.weight(1f),
                            )
                            KpknIconButton(
                                icon = KpknLineIcons.Close,
                                contentDescription = "Eliminar sub-etiqueta ${subTag.name}",
                                onClick = { onRemoveSubTag(subTag.id) },
                            )
                        }
                    }
                }

                if (showAddSubTag) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        WorkoutTagFilledTextField(
                            value = newSubTagName,
                            onValueChange = { newSubTagName = it },
                            label = "Nombre de la sub-etiqueta",
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SubTagCategory.entries.forEach { cat ->
                                KpknChip(
                                    text = subTagCategoryLabel(cat),
                                    selected = newSubTagCategory == cat,
                                    onClick = { newSubTagCategory = cat },
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KpknPrimaryPill(
                                text = "Agregar",
                                onClick = {
                                    if (newSubTagName.isNotBlank()) {
                                        onAddSubTag(newSubTagName, newSubTagCategory)
                                        newSubTagName = ""
                                        showAddSubTag = false
                                    }
                                },
                                enabled = newSubTagName.isNotBlank(),
                            )
                            KpknTextAction(text = "Cancelar", onClick = { showAddSubTag = false })
                        }
                    }
                } else {
                    KpknSecondaryPill(
                        text = "Añadir sub-etiqueta",
                        onClick = { showAddSubTag = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = KpknLineIcons.Plus,
                    )
                }

                SheetDivider()

                KpknTextAction(
                    text = "Reiniciar historial",
                    onClick = { showResetConfirm = true },
                )
                KpknTextAction(
                    text = "Eliminar etiqueta",
                    onClick = onDelete,
                    color = WizardColors.danger,
                    leadingIcon = KpknLineIcons.Trash,
                )
            }
        },
        dismissButton = {
            KpknSecondaryPill(text = "Ver más", onClick = onViewAll)
        },
        confirmButton = {
            KpknPrimaryPill(text = "Cerrar", onClick = onDismiss)
        },
    )

    if (showResetConfirm) {
        KpknAlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = "Reiniciar historial",
            text = "Se ignorarán las cargas anteriores de «${tag.name}» para sugerencias y PRs de esta etiqueta. Los registros de sesión no se borran.",
            confirmLabel = "Reiniciar",
            onConfirm = {
                onResetHistory()
                showResetConfirm = false
            },
            dismissLabel = "Cancelar",
            onDismiss = { showResetConfirm = false },
            destructive = true,
        )
    }
}

internal fun resolveSetupCurrentSet(
    currentSet: ExerciseSet?,
    sets: List<ExerciseSet>,
): ExerciseSet? = currentSet ?: sets.firstOrNull()

/** Hoja de configuración del ejercicio (LS7b): las etiquetas, el set-up del ejercicio y «Listo». */
@Composable
internal fun ExerciseSetupSheetContent(
    exercise: Exercise,
    currentSet: ExerciseSet?,
    currentTag: String?,
    profiles: List<WorkoutContextProfile>,
    activeProfileId: String?,
    onTagSet: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (WorkoutContextProfile) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onDismiss: () -> Unit,
    sessionAccentColor: Color,
    userTags: List<String> = emptyList(),
    suggestedTag: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExerciseTagSheetContent(
            currentTag = currentTag,
            onTagSet = onTagSet,
            onDismiss = {},
            userTags = userTags,
            suggestedTag = suggestedTag,
            profiles = profiles,
        )

        SheetDivider()

        val setupSet = resolveSetupCurrentSet(currentSet, exercise.sets)
        if (setupSet == null) {
            Text(
                text = "Este ejercicio no tiene series.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        } else {
            WorkoutExerciseSetupContent(
                exercise = exercise,
                currentSet = setupSet,
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelectProfile = onSelectProfile,
                onSaveProfile = onSaveProfile,
                onUpdateExercise = onUpdateExercise,
                onUpdateSet = onUpdateSet,
                sessionAccentColor = sessionAccentColor,
            )
        }

        KpknPrimaryPill(
            text = "Listo",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal data class PendingUntaggedAdoption(
    val exerciseId: String,
    val tagId: String,
    val tagName: String,
    val sessionCount: Int,
)

/** Pregunta si el historial sin etiqueta del ejercicio pasa a la etiqueta nueva (LS7b: diálogo KPKN, mismos textos). */
@Composable
internal fun WorkoutUntaggedAdoptionDialog(
    pending: PendingUntaggedAdoption,
    onAdopt: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = "Historial sin etiqueta",
        text = "Hay ${pending.sessionCount} sesión${if (pending.sessionCount == 1) "" else "es"} sin etiqueta en este ejercicio. ¿Quieres asignar esos kilos a «${pending.tagName}»? Si no, se crea la etiqueta Default para ese historial y «${pending.tagName}» empieza en 0 kg.",
        confirmLabel = "Usar historial",
        onConfirm = onAdopt,
        dismissLabel = "Dejar limpia",
        onDismiss = onReject,
    )
}

/** Separador de 1 dp del tono `divider` entre bloques de una hoja. */
@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}
