package com.example.kpkn.screens.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.workout.components.HeaderSheet
import com.example.kpkn.screens.workout.components.HeaderSheetAction
import com.example.kpkn.screens.workout.components.HeaderSheetFrame
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

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

/** Etiqueta de sección de las hojas: eyebrow KPKN (Inter SemiBold en mayúsculas, en `textMuted`). */
@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text.uppercase(Locale("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
    )
}

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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetLabel("Etiquetas sugeridas")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            commonTags.forEach { tag ->
                val isSuggested = tag == suggestedTag
                KpknChip(
                    text = if (isSuggested) "✨ $tag" else tag,
                    selected = tagText == tag,
                    onClick = {
                        tagText = tag
                        onTagSet(tag)
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutTagFilledTextField(tagText, { tagText = it }, "Etiqueta personalizada", Modifier.weight(1f))
            HeaderSheetAction("Aplicar", { onTagSet(tagText) }, Modifier.width(82.dp))
        }
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
        HeaderSheetAction(
            text = "Listo",
            primary = true,
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

@Composable
internal fun WorkoutTagFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) {
            kotlinx.coroutines.delay(60)
            requester.bringIntoView()
        }
    }
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(requester)
            .onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        singleLine = singleLine,
        textStyle = KpknType.body.copy(fontSize = 14.sp, lineHeight = 18.sp, color = WizardColors.text),
        cursorBrush = SolidColor(WizardColors.text),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(16.dp)).background(KpknSurface.inset)
                    .border(1.dp, WizardColors.textFaint.copy(alpha = .25f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(if (label == "Nombre de la etiqueta nueva") "Nueva etiqueta" else label,
                        style = KpknType.caption, color = WizardColors.textFaint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    inner()
                }
            }
        },
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
    "Las etiquetas aparecen en el historial del ejercicio y filtran tus marcas."

internal const val WORKOUT_TAG_EMPTY_COPY = "Este ejercicio aún no tiene etiquetas."

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutTagListOverlay(
    rows: List<WorkoutTagListRow>, onSelectTag: (String) -> Unit, onCreateTag: () -> Unit,
    onDismiss: () -> Unit, hasSeenEducation: Boolean = true, onOpened: () -> Unit = {},
    onCreateInline: ((String, TagSetupInput?) -> CreateTagResult)? = null,
    onManageTag: (String) -> Unit = {}, activeSubTagIds: List<String> = emptyList(),
    onToggleSubTag: (String) -> Unit = {},
) {
    LaunchedEffect(Unit) { onOpened() }
    val showEducation = remember { showTagEducation(hasSeenEducation, rows.isEmpty()) }
    HeaderSheet(onDismiss) { close ->
        HeaderSheetFrame("Etiquetas", footer = { HeaderSheetAction("Listo", close, Modifier.fillMaxWidth(), primary = true) }) {
            if (showEducation || rows.isEmpty()) Text(WORKOUT_TAG_EDUCATION_COPY, style = KpknType.caption, color = WizardColors.textMuted)
            if (rows.isEmpty()) Text(WORKOUT_TAG_EMPTY_COPY, style = KpknType.caption, color = WizardColors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { row -> HeaderTagChip(row, { onSelectTag(row.tag.id) }, { onManageTag(row.tag.id) }) }
            }
            val activeSubTags = rows.filter { it.isActive }.flatMap { it.tag.subTags }
            if (activeSubTags.isNotEmpty()) {
                SheetLabel("Subetiquetas")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeSubTags.forEach { sub -> KpknChip(sub.name, sub.id in activeSubTagIds, { onToggleSubTag(sub.id) }) }
                }
            }
            Text("Mantén pulsada una etiqueta para gestionarla.", style = KpknType.caption, color = WizardColors.textMuted)
            if (onCreateInline != null) TagCreateForm(onCreateInline)
            else HeaderSheetAction("Crear etiqueta nueva", onCreateTag, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderTagChip(row: WorkoutTagListRow, onClick: () -> Unit, onManage: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.defaultMinSize(minHeight = 48.dp).semantics { selected = row.isActive }
        .combinedClickable(role = Role.Button, onClick = onClick, onLongClickLabel = "Gestionar etiqueta", onLongClick = onManage), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(shape).background(if (row.isActive) SymbolPalette.musculo.copy(alpha = .16f) else KpknSurface.inset)
            .border(1.dp, if (row.isActive) SymbolPalette.musculo else WizardColors.textFaint.copy(alpha = .3f), shape)
            .padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(row.title, style = KpknType.chip.copy(fontSize = 13.sp), color = WizardColors.text)
            Text(row.lastLoadLabel, style = KpknType.caption.copy(fontSize = 13.sp), color = WizardColors.textMuted)
        }
    }
}

@Composable
private fun TagCreateForm(onCreate: (String, TagSetupInput?) -> CreateTagResult) {
    var name by remember { mutableStateOf("") }
    var machine by remember { mutableStateOf("") }
    var baseLoad by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutTagFilledTextField(name, { name = it; error = null }, "Nombre de la etiqueta nueva", Modifier.weight(1f))
            HeaderSheetAction("Crear", {
                val setup = TagSetupInput(machineBrand = machine, baseLoadKg = baseLoad.replace(',', '.').toDoubleOrNull(), setupNotes = notes)
                when (val result = onCreate(name, setup.takeIf { it.hasContent })) {
                    is CreateTagResult.Created -> { name = ""; machine = ""; baseLoad = ""; notes = ""; error = null }
                    is CreateTagResult.Duplicate -> error = "Ya existe una etiqueta con el nombre «${result.existingName}»."
                    CreateTagResult.InvalidName -> error = "El nombre no es válido."
                }
            }, Modifier.width(82.dp), primary = true, enabled = name.isNotBlank() || machine.isNotBlank())
        }
        error?.let { Text(it, style = KpknType.caption, color = WizardColors.danger) }
        HeaderSheetAction(if (expanded) "Set-up opcional · Ocultar" else "Set-up opcional · Mostrar", { expanded = !expanded }, Modifier.fillMaxWidth())
        if (expanded) {
            WorkoutTagFilledTextField(machine, { machine = it }, "Máquina o marca")
            WorkoutTagFilledTextField(baseLoad, { baseLoad = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Carga base (kg)")
            WorkoutTagFilledTextField(notes, { notes = it }, "Notas de set-up")
        }
    }
}

@Composable
internal fun WorkoutCreateTagOverlay(onCreate: (String, TagSetupInput?) -> CreateTagResult, onDismiss: () -> Unit) {
    HeaderSheet(onDismiss) { close ->
        HeaderSheetFrame("Nueva etiqueta", footer = { HeaderSheetAction("Cancelar", close, Modifier.fillMaxWidth()) }) { TagCreateForm(onCreate) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    var setupExpanded by remember { mutableStateOf(false) }
    var brand by remember(tag.id, machineBrand) { mutableStateOf(machineBrand) }
    var baseLoad by remember(tag.id, baseLoadKg) { mutableStateOf(baseLoadKg) }
    var notes by remember(tag.id, setupNotes) { mutableStateOf(setupNotes) }

    HeaderSheet(onDismiss) { close ->
        HeaderSheetFrame(tag.name.ifBlank { "Etiqueta" }, footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderSheetAction("Ver más", onViewAll, Modifier.weight(1f))
                HeaderSheetAction("Listo", close, Modifier.weight(1f), primary = true)
            }
        }) {
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
                Text(message, style = KpknType.caption, color = WizardColors.danger)
            }

            HorizontalDivider(color = KpknSurface.divider)

            HeaderSheetAction(if (setupExpanded) "Set-up · Ocultar" else "Set-up · Mostrar", { setupExpanded = !setupExpanded }, Modifier.fillMaxWidth())
            if (setupExpanded) {
                WorkoutTagFilledTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = "Máquina o marca",
                )
                WorkoutTagFilledTextField(
                    value = baseLoad,
                    onValueChange = { baseLoad = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = "Carga base (kg)",
                )
                WorkoutTagFilledTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notas de set-up",
                )
                KpknTextAction(
                    text = "Guardar set-up",
                    onClick = {
                        onSaveSetup(
                            TagSetupInput(
                                machineBrand = brand,
                                baseLoadKg = baseLoad.replace(',', '.').toDoubleOrNull(),
                                setupNotes = notes,
                            ),
                        )
                    },
                )

                HorizontalDivider(color = KpknSurface.divider)

            }

            SheetLabel("Historial")
            WorkoutExerciseHistoryContent(
                history = history,
                activeTag = tag.name,
                maxEntries = 4,
                maxSetsPerEntry = 3,
            )

            HorizontalDivider(color = KpknSurface.divider)

            if (tag.subTags.isNotEmpty()) {
                SheetLabel("Sub-etiquetas")
                tag.subTags.forEach { subTag ->
                    val isActive = subTag.id in activeSubTagIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KpknChip(
                            text = "${subTag.name} · ${subTagCategoryLabel(subTag.category)}",
                            selected = isActive,
                            onClick = { onToggleSubTagActive(subTag.id) },
                            modifier = Modifier.weight(1f),
                        )
                        KpknIconButton(
                            icon = KpknLineIcons.Close,
                            contentDescription = "Eliminar",
                            onClick = { onRemoveSubTag(subTag.id) },
                        )
                    }
                }
            }

            if (showAddSubTag) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutTagFilledTextField(
                        value = newSubTagName,
                        onValueChange = { newSubTagName = it },
                        label = "Nombre de sub-etiqueta",
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubTagCategory.entries.forEach { cat ->
                            KpknChip(
                                text = subTagCategoryLabel(cat),
                                selected = newSubTagCategory == cat,
                                onClick = { newSubTagCategory = cat },
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderSheetAction(
                            text = "Agregar",
                            modifier = Modifier.weight(1f),
                            primary = true,
                            onClick = {
                                if (newSubTagName.isNotBlank()) {
                                    onAddSubTag(newSubTagName, newSubTagCategory)
                                    newSubTagName = ""
                                    showAddSubTag = false
                                }
                            },
                            enabled = newSubTagName.isNotBlank(),
                        )
                        HeaderSheetAction("Cancelar", { showAddSubTag = false }, Modifier.weight(1f))
                    }
                }
            } else {
                HeaderSheetAction(
                    text = "Añadir sub-etiqueta",
                    onClick = { showAddSubTag = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(color = KpknSurface.divider)

            KpknTextAction(text = "Reiniciar historial", onClick = { showResetConfirm = true })

            KpknTextAction(
                text = "Eliminar etiqueta",
                onClick = onDelete,
                color = WizardColors.danger,
                leadingIcon = KpknLineIcons.Trash,
            )
        }
    }

    if (showResetConfirm) {
        HeaderSheet({ showResetConfirm = false }) { close ->
            HeaderSheetFrame("Reiniciar historial", footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderSheetAction("Cancelar", close, Modifier.weight(1f))
                    HeaderSheetAction("Reiniciar", { onResetHistory(); showResetConfirm = false }, Modifier.weight(1f), primary = true)
                }
            }) {
                Text("Se ignorarán las cargas anteriores de «${tag.name}» para sugerencias y PRs de esta etiqueta. Los registros de sesión no se borran.", style = KpknType.body, color = WizardColors.text)
            }
        }
    }
}

internal fun resolveSetupCurrentSet(
    currentSet: ExerciseSet?,
    sets: List<ExerciseSet>,
): ExerciseSet? = currentSet ?: sets.firstOrNull()

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

        HorizontalDivider(color = KpknSurface.divider)

        val setupSet = resolveSetupCurrentSet(currentSet, exercise.sets)
        if (setupSet == null) {
            Text(
                "Este ejercicio no tiene series.",
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

@Composable
internal fun WorkoutUntaggedAdoptionDialog(
    pending: PendingUntaggedAdoption,
    onAdopt: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    HeaderSheet(onDismiss) { close ->
        HeaderSheetFrame("Historial sin etiqueta", footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderSheetAction("Dejar limpia", onReject, Modifier.weight(1f))
                HeaderSheetAction("Usar historial", onAdopt, Modifier.weight(1f), primary = true)
            }
            KpknTextAction("Cerrar", close)
        }) {
            Text("Hay ${pending.sessionCount} sesión${if (pending.sessionCount == 1) "" else "es"} sin etiqueta. Puedes asignar ese historial a «${pending.tagName}». Si la dejas limpia, el historial anterior queda en Default y la nueva etiqueta empieza en 0 kg.", style = KpknType.body, color = WizardColors.text)
        }
    }
}
