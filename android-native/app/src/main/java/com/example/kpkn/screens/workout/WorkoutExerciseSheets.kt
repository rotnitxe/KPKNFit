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
import androidx.compose.ui.platform.testTag
import com.example.kpkn.screens.workout.components.HeaderSheet
import com.example.kpkn.screens.workout.components.HeaderSheetAction
import com.example.kpkn.screens.workout.components.HeaderSheetFrame
import com.example.kpkn.screens.workout.components.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Icon
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
    onDismiss: () -> Unit, @Suppress("UNUSED_PARAMETER") hasSeenEducation: Boolean = true, onOpened: () -> Unit = {},
    onCreateInline: ((String, TagSetupInput?) -> CreateTagResult)? = null,
    onManageTag: (String) -> Unit = {}, @Suppress("UNUSED_PARAMETER") activeSubTagIds: List<String> = emptyList(),
    @Suppress("UNUSED_PARAMETER") onToggleSubTag: (String) -> Unit = {},
    exerciseName: String = "",
) {
    LaunchedEffect(Unit) { onOpened() }
    var creating by remember { mutableStateOf(false) }
    val form = remember { TagFormState() }
    // Prototipo 02 (`tags`): «ETIQUETAS DE ESTE EJERCICIO», el ejercicio en Syne 22, una fila por etiqueta con su referencia,
    // «Nueva etiqueta» y la nota de alcance. `tag-new`: el formulario con «Guardar etiqueta» en el pie.
    LiveSheet(title = if (creating) "Nueva etiqueta" else "Etiquetas del ejercicio", onDismiss = onDismiss, tag = "live-tags-sheet",
        footer = if (creating && onCreateInline != null) { _ ->
            LiveSheetPrimary("Guardar etiqueta", { form.save(onCreateInline) { creating = false } }, enabled = form.canSave,
                tag = "live-tag-new-save")
        } else { close -> LiveSheetPrimary("Listo", close) }) {
        if (creating && onCreateInline != null) {
            TagFormFields(form, exerciseName)
            return@LiveSheet
        }
        Column(Modifier.fillMaxWidth()) {
            Text("ETIQUETAS DE ESTE EJERCICIO", style = KpknType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
            if (exerciseName.isNotBlank()) {
                Text(exerciseName, Modifier.padding(top = 7.dp, bottom = 10.dp), style = TagContextTitle, color = WizardColors.text)
            }
            Text("Cada contexto conserva su referencia de carga.", style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 19.sp),
                color = LiveSheetTokens.Muted)
        }
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            rows.forEach { row -> LiveTagOptionRow(row, { onSelectTag(row.tag.id) }, { onManageTag(row.tag.id) }) }
        }
        LiveSheetSecondary("Nueva etiqueta", { if (onCreateInline != null) { form.reset(); creating = true } else onCreateTag() },
            Modifier.padding(top = 12.dp), icon = KpknLineIcons.Plus, tag = "live-tags-new")
        LiveSheetShortCopy("Cambiar el contexto afecta a los próximos registros. Las series ya guardadas mantienen su etiqueta.")
    }
}

/** `.v2-context-header strong`: Syne 700 22 (interlineado 1,3: Syne recorta los acentos por debajo). */
private val TagContextTitle = androidx.compose.ui.text.TextStyle(
    fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.display,
    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 22.sp, lineHeight = 29.sp,
)

/** `.v2-tag-option`: marca de selección, nombre y referencia; el lápiz abre su gestión. */
@Composable
private fun LiveTagOptionRow(row: WorkoutTagListRow, onClick: () -> Unit, onManage: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).drawBehind {
        drawLine(Color(0x12FFFFFF), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).heightIn(min = 70.dp).clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { selected = row.isActive }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(17.dp).clip(CircleShape).border(1.dp, if (row.isActive) WizardColors.text else Color(0x38FFFFFF), CircleShape)
                .padding(4.dp)) {
                if (row.isActive) Box(Modifier.fillMaxSize().clip(CircleShape).background(WizardColors.text))
            }
            Column {
                Text(row.title, style = KpknType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 17.sp), color = WizardColors.text)
                Text(row.lastLoadLabel + if (row.isActive) " · Activa" else "", Modifier.padding(top = 3.dp),
                    style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = LiveSheetTokens.Faint)
            }
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onManage)
            .semantics { contentDescription = "Editar etiqueta ${row.title}" }, contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.Sliders, null, Modifier.size(16.dp), tint = LiveSheetTokens.Muted)
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

/** Lo que se escribe en `tag-new` / `tag-setup`: nombre, equipo o marca, carga base y notas de setup. */
private class TagFormState(name: String = "", brand: String = "", base: String = "", notes: String = "") {
    var name by mutableStateOf(name)
    var brand by mutableStateOf(brand)
    var base by mutableStateOf(base)
    var notes by mutableStateOf(notes)
    var error by mutableStateOf<String?>(null)
    val canSave: Boolean get() = name.isNotBlank() || brand.isNotBlank()
    fun setup() = TagSetupInput(machineBrand = brand, baseLoadKg = base.replace(',', '.').toDoubleOrNull(), setupNotes = notes)
    fun reset() { name = ""; brand = ""; base = ""; notes = ""; error = null }
    fun save(onCreate: (String, TagSetupInput?) -> CreateTagResult, onDone: () -> Unit) {
        if (base.isNotBlank() && base.replace(',', '.').toDoubleOrNull() == null) { error = "Revisa el nombre y la carga base."; return }
        when (onCreate(name, setup().takeIf { it.hasContent })) {
            is CreateTagResult.Created -> { reset(); onDone() }
            is CreateTagResult.Duplicate -> error = "Ya existe una etiqueta con ese nombre."
            CreateTagResult.InvalidName -> error = "Revisa el nombre y la carga base."
        }
    }
}

/** `tag-new` / `tag-setup`: el ejercicio, Nombre, «Equipo / marca» y «Carga base · kg» en dos columnas, y «Notas de setup». */
@Composable
private fun TagFormFields(form: TagFormState, exerciseName: String) {
    if (exerciseName.isNotBlank()) LiveSheetSubtitle(exerciseName)
    LiveSheetLabeledField("Nombre", form.name, { form.name = it; form.error = null }, tag = "live-tag-new-name")
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LiveSheetLabeledField("Equipo / marca", form.brand, { form.brand = it }, Modifier.weight(1f), tag = "live-tag-brand")
        LiveSheetLabeledField("Carga base · kg", form.base, { form.base = it.filter { c -> c.isDigit() || c == '.' || c == ',' }; form.error = null },
            Modifier.weight(1f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, tag = "live-tag-base")
    }
    LiveSheetLabeledField("Notas de setup", form.notes, { form.notes = it }, Modifier.padding(top = 12.dp), multiline = true,
        tag = "live-tag-notes")
    form.error?.let { LiveSheetShortCopy(it, Modifier.padding(top = 8.dp), error = true) }
}

@Composable
internal fun WorkoutCreateTagOverlay(onCreate: (String, TagSetupInput?) -> CreateTagResult, onDismiss: () -> Unit) {
    val form = remember { TagFormState() }
    LiveSheet(title = "Nueva etiqueta", onDismiss = onDismiss, tag = "live-tag-new-sheet", footer = { close ->
        LiveSheetPrimary("Guardar etiqueta", { form.save(onCreate, close) }, enabled = form.canSave, tag = "live-tag-new-save")
    }) {
        TagFormFields(form, "")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutTagManagerModal(
    tag: WorkoutTag,
    @Suppress("UNUSED_PARAMETER") exerciseId: String,
    onRename: (String) -> RenameTagResult,
    onDelete: () -> Unit,
    onResetHistory: () -> Unit,
    onAddSubTag: (String, SubTagCategory) -> Unit,
    onRemoveSubTag: (String) -> Unit,
    onToggleSubTagActive: (String) -> Unit,
    activeSubTagIds: List<String>,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onViewAll: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") history: List<ExerciseHistoryEntry> = emptyList(),
    machineBrand: String = "",
    baseLoadKg: String = "",
    setupNotes: String = "",
    onSaveSetup: (TagSetupInput) -> Unit = {},
    exerciseName: String = "",
    /** «REFERENCIA DE ESTA ETIQUETA»: la última carga con esta etiqueta, o «Sin historial». */
    referenceLabel: String? = null,
) {
    val form = remember(tag.id) { TagFormState(tag.name, machineBrand, baseLoadKg, setupNotes) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showSubTags by remember { mutableStateOf(false) }

    fun save(close: () -> Unit) {
        if (form.base.isNotBlank() && form.base.replace(',', '.').toDoubleOrNull() == null) {
            form.error = "Revisa el nombre y la carga base."
            return
        }
        if (form.name.trim() != tag.name) {
            when (onRename(form.name)) {
                RenameTagResult.Success -> Unit
                is RenameTagResult.Duplicate -> { form.error = "Ya existe una etiqueta con ese nombre."; return }
                RenameTagResult.InvalidName -> { form.error = "Revisa el nombre y la carga base."; return }
                RenameTagResult.NotFound -> { form.error = "No se encontró la etiqueta."; return }
            }
        }
        onSaveSetup(form.setup())
        close()
    }

    // Prototipo 02 (`tag-setup`): «Contexto del ejercicio», el formulario, «REFERENCIA DE ESTA ETIQUETA», «Reiniciar referencia de
    // historial» en rojo y «Guardar etiqueta» en el pie. Quitar la etiqueta y sus subetiquetas siguen al final, como acciones de texto.
    LiveSheet(title = "Contexto del ejercicio", onDismiss = onDismiss, tag = "live-tag-setup-sheet", footer = { close ->
        LiveSheetPrimary("Guardar etiqueta", { save(close) }, enabled = form.name.isNotBlank(), tag = "live-tag-setup-save")
    }) {
        TagFormFields(form, exerciseName)
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp).drawBehind {
                drawLine(Color(0x2DFFFFFF), androidx.compose.ui.geometry.Offset(1.dp.toPx(), 0f),
                    androidx.compose.ui.geometry.Offset(1.dp.toPx(), size.height), 2.dp.toPx())
            }.padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Text("REFERENCIA DE ESTA ETIQUETA", style = KpknType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
            Text(referenceLabel?.takeIf { it.isNotBlank() } ?: "Sin historial", Modifier.padding(top = 8.dp),
                style = TagContextTitle.copy(fontSize = 17.sp, lineHeight = 23.sp), color = WizardColors.text)
        }
        LiveSheetTextAction("Reiniciar referencia de historial", { showResetConfirm = true }, Modifier.testTag("live-tag-reset"),
            danger = true, wide = false)
        LiveSheetTextAction(if (tag.subTags.isEmpty()) "Subetiquetas" else "Subetiquetas · ${tag.subTags.size}", { showSubTags = true },
            wide = false)
        LiveSheetTextAction("Eliminar etiqueta", onDelete, danger = true, wide = false)
    }

    if (showResetConfirm) {
        LiveAlert(
            title = "¿Reiniciar esta referencia?",
            text = "La referencia de la etiqueta se calcularía desde este momento. Los registros originales se conservan.",
            primaryLabel = "Conservar referencia",
            onPrimary = { showResetConfirm = false },
            secondaryLabel = "Reiniciar referencia",
            onSecondary = { onResetHistory(); showResetConfirm = false },
            dangerSecondary = true,
            onDismiss = { showResetConfirm = false },
            tag = "live-tag-reset-alert",
        )
    }
    if (showSubTags) {
        var newSubTagName by remember { mutableStateOf("") }
        var newSubTagCategory by remember { mutableStateOf(SubTagCategory.LIBRE) }
        LiveSheet(title = "Subetiquetas", onDismiss = { showSubTags = false }, tag = "live-subtags-sheet") {
            LiveSheetSubtitle(tag.name)
            tag.subTags.forEach { subTag ->
                LiveSheetMenuRow("${subTag.name} · ${subTagCategoryLabel(subTag.category)}", { onToggleSubTagActive(subTag.id) },
                    icon = if (subTag.id in activeSubTagIds) KpknLineIcons.Check else null, chevron = false)
                LiveSheetTextAction("Quitar ${subTag.name}", { onRemoveSubTag(subTag.id) }, danger = true, wide = false)
            }
            LiveSheetLabeledField("Nueva subetiqueta", newSubTagName, { newSubTagName = it }, Modifier.padding(top = 12.dp))
            LiveSheetChoiceRow(SubTagCategory.entries.map { it to subTagCategoryLabel(it) }, selected = { it == newSubTagCategory },
                onSelect = { newSubTagCategory = it }, tagPrefix = "live-subtag-category")
            LiveSheetSecondary("Añadir subetiqueta", {
                onAddSubTag(newSubTagName, newSubTagCategory)
                newSubTagName = ""
            }, Modifier.padding(top = 10.dp), enabled = newSubTagName.isNotBlank())
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
