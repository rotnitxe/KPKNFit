package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.templates.SessionTemplateDurationBucket
import com.example.kpkn.domain.templates.TemplateCatalogFilterLogic
import com.example.kpkn.domain.templates.TemplateCatalogFilters
import com.example.kpkn.domain.templates.TemplateCatalogNestedGroup
import com.example.kpkn.domain.templates.TemplateCatalogSection
import com.example.kpkn.domain.templates.TemplateGroupMode
import com.example.kpkn.domain.templates.TemplateSessionGoal
import com.example.kpkn.domain.templates.TemplateSessionType
import com.example.kpkn.domain.templates.TemplateSessionZone
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip
import com.example.kpkn.ui.components.KpknDropdownMenu
import kotlinx.coroutines.launch

/**
 * Result-driven state for the USER-template save dialog.  The dialog owns this
 * small state machine so it cannot disappear before the Room-backed command
 * has completed.  Keeping it here also makes the button -> command contract
 * directly testable without a Compose hierarchy.
 */
internal sealed interface UserTemplateSaveState {
    data object Idle : UserTemplateSaveState
    data object Saving : UserTemplateSaveState
    data class Error(val message: String) : UserTemplateSaveState
    data class Success(val template: SessionTemplate) : UserTemplateSaveState
}

internal suspend fun executeUserTemplateSave(
    name: String,
    description: String,
    onSave: suspend (String, String) -> Result<SessionTemplate>,
): UserTemplateSaveState {
    val normalizedName = name.trim()
    if (normalizedName.isBlank()) {
        return UserTemplateSaveState.Error("Escribe un nombre para la plantilla.")
    }
    return runCatching { onSave(normalizedName, description.trim()) }
        .fold(
            onSuccess = { result ->
                result.fold(
                    onSuccess = { template -> UserTemplateSaveState.Success(template) },
                    onFailure = { error ->
                        UserTemplateSaveState.Error(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "No se pudo guardar la plantilla. Intenta nuevamente.",
                        )
                    },
                )
            },
            onFailure = { error ->
                UserTemplateSaveState.Error(
                    error.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo guardar la plantilla. Intenta nuevamente.",
                )
            },
        )
}

/** Chips amigables de grupo / día (sin Powerlifting / Minimalista / Recuperación como primarios). */
private val FriendlyGroupChips = listOf(
    TemplateSessionType.PIERNAS,
    TemplateSessionType.TORSO,
    TemplateSessionType.BRAZOS,
    TemplateSessionType.FULL_BODY,
    TemplateSessionType.PECHO,
    TemplateSessionType.ESPALDA,
    TemplateSessionType.HOMBROS,
    TemplateSessionType.GLUTEOS,
)

private val VisibleGroupModes = listOf(
    TemplateGroupMode.MUSCLE_GROUP,
    TemplateGroupMode.SPLIT,
    TemplateGroupMode.LEVEL,
    TemplateGroupMode.DURATION,
)

@Composable
internal fun TemplateCatalogBrowser(
    templates: List<SessionTemplate>,
    searchQuery: String,
    onSelectTemplate: (SessionTemplate) -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    /** Archived USER templates stay out of application results but remain manageable. */
    archivedUserTemplates: List<SessionTemplate> = emptyList(),
    onArchiveUserTemplate: (String) -> Unit = {},
    onRestoreUserTemplate: (String) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    onEditUserTemplate: suspend (
        SessionTemplate,
        String,
        String,
        Difficulty,
        SessionTemplateFocusCategory?,
        SessionTemplateDurationClass,
        List<String>,
        List<String>,
        Boolean,
    ) -> Result<Unit> = { _, _, _, _, _, _, _, _, _ -> Result.success(Unit) },
    onSaveCurrentTemplate: suspend (String, String) -> Result<SessionTemplate> = { _, _ ->
        Result.failure(IllegalStateException("Guardado de plantillas no disponible en esta superficie"))
    },
    glassDark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (glassDark) Color.White else MaterialTheme.colorScheme.primary
    val bodyColor = if (glassDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (glassDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    val rowBg = if (glassDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    var templateBeingEdited by remember { mutableStateOf<SessionTemplate?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Keep first paint independent from the expensive AUGE/ring calculation.
    // Duration and difficulty filters already have metadata fallbacks; detailed
    // facets are calculated lazily only when a card is expanded.
    val facetsById = emptyMap<String, com.example.kpkn.domain.templates.SessionTemplateFacets>()
    val splits = remember { SPLIT_TEMPLATES.filter { it.id != "custom" && it.isVisibleForApplication } }

    var groupMode by rememberSaveable { mutableStateOf(TemplateGroupMode.MUSCLE_GROUP.name) }
    var sessionTypeName by rememberSaveable { mutableStateOf(TemplateSessionType.ALL.name) }
    var difficultyName by rememberSaveable { mutableStateOf<String?>(null) }
    var durationName by rememberSaveable { mutableStateOf(SessionTemplateDurationBucket.ALL.name) }

    val activeGroupMode = remember(groupMode) {
        TemplateGroupMode.entries.find { it.name == groupMode } ?: TemplateGroupMode.MUSCLE_GROUP
    }
    val activeSessionType = remember(sessionTypeName) {
        TemplateSessionType.entries.find { it.name == sessionTypeName } ?: TemplateSessionType.ALL
    }
    val activeDifficulty = remember(difficultyName) {
        difficultyName?.let { name -> Difficulty.entries.find { it.name == name } }
    }
    val activeDuration = remember(durationName) {
        SessionTemplateDurationBucket.entries.find { it.name == durationName }
            ?: SessionTemplateDurationBucket.ALL
    }

    val filters = remember(searchQuery, activeSessionType, activeDifficulty, activeDuration) {
        TemplateCatalogFilters(
            searchQuery = searchQuery,
            sessionType = activeSessionType,
            goal = TemplateSessionGoal.ALL,
            zone = TemplateSessionZone.ALL,
            difficulty = activeDifficulty,
            duration = activeDuration,
        )
    }

    val filteredTemplates = remember(templates, facetsById, filters) {
        TemplateCatalogFilterLogic.filterTemplates(templates, facetsById, filters)
    }

    val sections = remember(filteredTemplates, facetsById, activeGroupMode, splits) {
        TemplateCatalogFilterLogic.groupTemplates(
            templates = filteredTemplates,
            facetsById = facetsById,
            mode = activeGroupMode,
            splits = splits,
        )
    }

    val hasConstraintFilters = activeSessionType != TemplateSessionType.ALL ||
        activeDifficulty != null ||
        activeDuration != SessionTemplateDurationBucket.ALL

    fun clearAll() {
        sessionTypeName = TemplateSessionType.ALL.name
        difficultyName = null
        durationName = SessionTemplateDurationBucket.ALL.name
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        val visibleUserTemplates = templates.filter { it.sourceType == SessionTemplateSourceType.USER }
        item(key = "user-template-management") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Mis plantillas",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = titleColor,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = { showSaveDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text("Guardar sesión") }
                    }
                    if (visibleUserTemplates.isEmpty() && archivedUserTemplates.isEmpty()) {
                        Text(
                            "Todavía no tienes plantillas guardadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedColor,
                        )
                    }
                    visibleUserTemplates.forEach { template ->
                        UserTemplateManagementRow(
                            template = template,
                            archived = false,
                            mutedColor = mutedColor,
                            onArchive = onArchiveUserTemplate,
                            onRestore = onRestoreUserTemplate,
                            onDelete = onDeleteUserTemplate,
                            onEdit = { templateBeingEdited = it },
                        )
                    }
                    archivedUserTemplates.forEach { template ->
                        UserTemplateManagementRow(
                            template = template,
                            archived = true,
                            mutedColor = mutedColor,
                            onArchive = onArchiveUserTemplate,
                            onRestore = onRestoreUserTemplate,
                            onDelete = onDeleteUserTemplate,
                            onEdit = { templateBeingEdited = it },
                        )
                    }
                }
        }
        item(key = "filters-header") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Filtros de sesión",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = titleColor,
                )
                Text(
                    text = "Grupo, nivel y duración",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
            }
        }

        item(key = "filters-dropdowns") {
            FilterDropdownRow(
                groupMode = activeGroupMode,
                onGroupMode = { groupMode = it.name },
                difficulty = activeDifficulty,
                onDifficulty = { difficultyName = it?.name },
                duration = activeDuration,
                onDuration = { durationName = it.name },
                visibleGroupModes = VisibleGroupModes,
                mutedColor = mutedColor,
                glassDark = glassDark,
            )
        }

        item(key = "filters-chips") {
            SessionTypeChipsRow(
                chips = FriendlyGroupChips,
                sessionType = activeSessionType,
                onToggle = { target ->
                    sessionTypeName = if (activeSessionType == target) {
                        TemplateSessionType.ALL.name
                    } else {
                        target.name
                    }
                },
                glassDark = glassDark,
            )
        }

        if (filters.hasActiveFilters) {
            item(key = "filters-summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (filteredTemplates.isEmpty()) {
                            "Sin resultados"
                        } else {
                            "${filteredTemplates.size} resultado${if (filteredTemplates.size == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                    )
                    if (hasConstraintFilters) {
                        TextButton(
                            onClick = { clearAll() },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .semantics { contentDescription = "Limpiar filtros de sesión" },
                        ) {
                            Text("Limpiar", color = if (glassDark) Color.White else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        when {
            filteredTemplates.isEmpty() -> {
                item(key = "empty") {
                    CatalogEmptyState(
                        hasQueryOrFilters = filters.hasActiveFilters,
                        searchQuery = searchQuery,
                        mutedColor = mutedColor,
                    )
                }
            }
            else -> {
                if (!filters.hasActiveFilters) {
                    item(key = "group-label") {
                        Text(
                            text = "Agrupado ${activeGroupMode.label.removePrefix("Por ").lowercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = titleColor,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
                items(
                    items = sections,
                    key = { it.key },
                ) { section ->
                    CatalogSection(
                        section = section,
                        bodyColor = bodyColor,
                        mutedColor = mutedColor,
                        rowBg = rowBg,
                        initiallyExpanded = section.key == "user" ||
                            filters.hasActiveFilters ||
                            activeGroupMode == TemplateGroupMode.MUSCLE_GROUP,
                    ) {
                        if (section.nestedGroups.isNotEmpty()) {
                            section.nestedGroups.forEach { nested ->
                                NestedGroupBlock(
                                    nested = nested,
                                    mutedColor = mutedColor,
                                    glassDark = glassDark,
                                    facetsById = facetsById,
                                    exerciseIndex = exerciseIndex,
                                    onSelectTemplate = onSelectTemplate,
                                )
                            }
                        } else {
                            section.templates.forEach { template ->
                                CompactTemplateCard(
                                    template = template,
                                    onApply = { onSelectTemplate(template) },
                                    exerciseIndex = exerciseIndex,
                                    glassDark = glassDark,
                                    facets = facetsById[template.id],
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    templateBeingEdited?.let { template ->
        UserTemplateMetadataDialog(
            template = template,
            onDismiss = { templateBeingEdited = null },
            onSave = { name, description, difficulty, focus, duration, splitIds, dayLabels, autoGeneration ->
                onEditUserTemplate(
                    template,
                    name,
                    description,
                    difficulty,
                    focus,
                    duration,
                    splitIds,
                    dayLabels,
                    autoGeneration,
                )
            },
        )
    }
    if (showSaveDialog) {
        SaveUserTemplateDialog(
            onDismiss = { showSaveDialog = false },
            onSave = onSaveCurrentTemplate,
        )
    }
}

@Composable
private fun UserTemplateManagementRow(
    template: SessionTemplate,
    archived: Boolean,
    mutedColor: Color,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (SessionTemplate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(template.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (archived) "Archivada" else "Disponible · ${template.exerciseCount} ejercicios", style = MaterialTheme.typography.labelSmall, color = mutedColor)
        }
        if (archived) {
            TextButton(onClick = { onRestore(template.id) }) { Text("Restaurar") }
        } else {
            TextButton(onClick = { onArchive(template.id) }) { Text("Archivar") }
        }
        TextButton(onClick = { onEdit(template) }) { Text("Editar") }
        TextButton(onClick = { onDelete(template.id) }) { Text("Borrar") }
    }
}

@Composable
private fun UserTemplateMetadataDialog(
    template: SessionTemplate,
    onDismiss: () -> Unit,
    onSave: suspend (
        String,
        String,
        Difficulty,
        SessionTemplateFocusCategory?,
        SessionTemplateDurationClass,
        List<String>,
        List<String>,
        Boolean,
    ) -> Result<Unit>,
) {
    var name by remember(template.id) { mutableStateOf(template.name) }
    var description by remember(template.id) { mutableStateOf(template.description) }
    var difficulty by remember(template.id) { mutableStateOf(template.difficulty) }
    var focus by remember(template.id) { mutableStateOf(template.focusCategory) }
    var duration by remember(template.id) { mutableStateOf(template.durationClass) }
    var splitIds by remember(template.id) { mutableStateOf(template.splitIds.joinToString(", ")) }
    var dayLabels by remember(template.id) { mutableStateOf(template.splitDayLabels.joinToString(", ")) }
    var autoGeneration by remember(template.id) { mutableStateOf(template.autoGenerationEligible) }
    var expandedField by remember { mutableStateOf<String?>(null) }
    var isSaving by remember(template.id) { mutableStateOf(false) }
    var saveError by remember(template.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        // A system BACK first hides the IME.  Do not let that same event
        // dismiss the dialog and bypass the durable Result contract.
        properties = DialogProperties(dismissOnBackPress = false),
        title = { Text("Editar plantilla", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, enabled = !isSaving)
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Descripción") },
                    minLines = 2,
                    enabled = !isSaving,
                )
                TemplateMetadataDropdown(
                    label = "Dificultad",
                    value = difficulty.name,
                    expanded = expandedField == "difficulty",
                    onExpand = { expandedField = "difficulty" },
                    onDismiss = { expandedField = null },
                    values = Difficulty.entries.map { it.name },
                    onValue = { value -> difficulty = Difficulty.entries.first { it.name == value } },
                )
                TemplateMetadataDropdown(
                    label = "Foco",
                    value = focus?.name ?: "Sin foco",
                    expanded = expandedField == "focus",
                    onExpand = { expandedField = "focus" },
                    onDismiss = { expandedField = null },
                    values = listOf("Sin foco") + SessionTemplateFocusCategory.entries.map { it.name },
                    onValue = { value -> focus = SessionTemplateFocusCategory.entries.firstOrNull { it.name == value } },
                )
                TemplateMetadataDropdown(
                    label = "Duración",
                    value = duration.name,
                    expanded = expandedField == "duration",
                    onExpand = { expandedField = "duration" },
                    onDismiss = { expandedField = null },
                    values = SessionTemplateDurationClass.entries.map { it.name },
                    onValue = { value -> duration = SessionTemplateDurationClass.entries.first { it.name == value } },
                )
                OutlinedTextField(
                    splitIds,
                    { splitIds = it },
                    label = { Text("Split IDs (separados por coma)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    dayLabels,
                    { dayLabels = it },
                    label = { Text("Etiquetas de día (separadas por coma)") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoGeneration, onCheckedChange = { if (!isSaving) autoGeneration = it })
                    Text("Permitir autogeneración semanal")
                }
                saveError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    saveError = null
                    scope.launch {
                        val result = runCatching {
                            onSave(
                                name.trim(),
                                description.trim(),
                                difficulty,
                                focus,
                                duration,
                                splitIds.split(',').map(String::trim).filter(String::isNotBlank),
                                dayLabels.split(',').map(String::trim).filter(String::isNotBlank),
                                autoGeneration,
                            )
                        }.getOrElse { Result.failure(it) }
                        if (result.isSuccess) {
                            isSaving = false
                            onDismiss()
                        } else {
                            isSaving = false
                            saveError = result.exceptionOrNull()?.message
                                ?: "No se pudieron actualizar los metadatos."
                        }
                    }
                },
                enabled = name.isNotBlank() && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Guardando…")
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") } },
    )
}

@Composable
private fun SaveUserTemplateDialog(
    onDismiss: () -> Unit,
    onSave: suspend (String, String) -> Result<SessionTemplate>,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saveState by remember { mutableStateOf<UserTemplateSaveState>(UserTemplateSaveState.Idle) }
    val scope = rememberCoroutineScope()
    val isSaving = saveState is UserTemplateSaveState.Saving
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        // Keep the form mounted while the IME is being dismissed; only the
        // Room-backed success path below may close it after a save.
        properties = DialogProperties(dismissOnBackPress = false),
        title = { Text("Guardar sesión como plantilla", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    minLines = 2,
                    enabled = !isSaving,
                )
                val error = (saveState as? UserTemplateSaveState.Error)?.message
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    saveState = UserTemplateSaveState.Saving
                    scope.launch {
                        val result = executeUserTemplateSave(name, description, onSave)
                        saveState = result
                        if (result is UserTemplateSaveState.Success) {
                            // The suspend command returned only after Room accepted
                            // the row; closing now lets the Flow render the read-back.
                            onDismiss()
                        }
                    }
                },
                enabled = name.isNotBlank() && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Guardando…")
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        },
    )
}

@Composable
private fun TemplateMetadataDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    values: List<String>,
    onValue: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = onExpand,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("$label: $value", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        KpknDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValue(option)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun NestedGroupBlock(
    nested: TemplateCatalogNestedGroup,
    mutedColor: Color,
    glassDark: Boolean,
    facetsById: Map<String, com.example.kpkn.domain.templates.SessionTemplateFacets>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    onSelectTemplate: (SessionTemplate) -> Unit,
) {
    Text(
        text = "${nested.title} · ${nested.templates.size}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = mutedColor,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    HorizontalDivider(
        color = if (glassDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        },
    )
    nested.templates.forEach { template ->
        CompactTemplateCard(
            template = template,
            onApply = { onSelectTemplate(template) },
            exerciseIndex = exerciseIndex,
            glassDark = glassDark,
            facets = facetsById[template.id],
        )
    }
}

@Composable
private fun FilterDropdownRow(
    groupMode: TemplateGroupMode,
    onGroupMode: (TemplateGroupMode) -> Unit,
    difficulty: Difficulty?,
    onDifficulty: (Difficulty?) -> Unit,
    duration: SessionTemplateDurationBucket,
    onDuration: (SessionTemplateDurationBucket) -> Unit,
    visibleGroupModes: List<TemplateGroupMode>,
    mutedColor: Color,
    glassDark: Boolean,
) {
    var showGroupMenu by remember { mutableStateOf(false) }
    var showDifficultyMenu by remember { mutableStateOf(false) }
    var showDurationMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogDropdownButton(
                label = "Agrupar: ${groupMode.label}",
                expanded = showGroupMenu,
                onExpandedChange = { showGroupMenu = it },
                contentDescription = "Agrupar plantillas por ${groupMode.label}",
            ) {
                visibleGroupModes.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            onGroupMode(mode)
                            showGroupMenu = false
                        },
                    )
                }
            }

            CatalogDropdownButton(
                label = difficulty?.let { TemplateCatalogFilterLogic.difficultyLabel(it) } ?: "Nivel",
                expanded = showDifficultyMenu,
                onExpandedChange = { showDifficultyMenu = it },
                contentDescription = "Filtrar por nivel",
            ) {
                DropdownMenuItem(
                    text = { Text("Todos") },
                    onClick = {
                        onDifficulty(null)
                        showDifficultyMenu = false
                    },
                )
                Difficulty.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(TemplateCatalogFilterLogic.difficultyLabel(option)) },
                        onClick = {
                            onDifficulty(option)
                            showDifficultyMenu = false
                        },
                    )
                }
            }

            CatalogDropdownButton(
                label = if (duration == SessionTemplateDurationBucket.ALL) {
                    "Duración"
                } else {
                    TemplateCatalogFilterLogic.durationLabel(duration)
                },
                expanded = showDurationMenu,
                onExpandedChange = { showDurationMenu = it },
                contentDescription = "Filtrar por duración",
            ) {
                SessionTemplateDurationBucket.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(TemplateCatalogFilterLogic.durationLabel(option)) },
                        onClick = {
                            onDuration(option)
                            showDurationMenu = false
                        },
                    )
                }
            }
        }

        Text(
            text = if (glassDark) {
                "Por defecto: Pierna, Torso, Brazo o Full body"
            } else {
                "Elige un grupo muscular y el nivel de la sesión"
            },
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
        )
    }
}

@Composable
private fun CatalogDropdownButton(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    contentDescription: String,
    menuContent: @Composable () -> Unit,
) {
    Box {
        FilledTonalButton(
            onClick = { onExpandedChange(true) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .widthIn(max = 180.dp)
                .semantics { this.contentDescription = contentDescription },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White.copy(alpha = 0.88f),
            ),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        KpknDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            menuContent()
        }
    }
}

@Composable
private fun SessionTypeChipsRow(
    chips: List<TemplateSessionType>,
    sessionType: TemplateSessionType,
    onToggle: (TemplateSessionType) -> Unit,
    glassDark: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Grupo",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (glassDark) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { chip ->
                val selected = sessionType == chip
                CompactCatalogFilterChip(
                    selected = selected,
                    onClick = { onToggle(chip) },
                    label = chip.label,
                    glassDark = glassDark,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .semantics {
                            contentDescription = buildString {
                                append(chip.label)
                                append(if (selected) ", activo, tocar para desactivar" else ", inactivo, tocar para activar")
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun CatalogEmptyState(
    hasQueryOrFilters: Boolean,
    searchQuery: String,
    mutedColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = if (hasQueryOrFilters) {
                if (searchQuery.isNotBlank()) {
                    "Ninguna sesión coincide con \"$searchQuery\" y los filtros activos."
                } else {
                    "Ninguna sesión coincide con los filtros activos."
                }
            } else {
                "No hay plantillas de sesión en este catálogo."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = mutedColor,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = if (hasQueryOrFilters) {
                "Prueba a limpiar filtros o cambiar el grupo."
            } else {
                "Guarda una sesión como plantilla o vuelve más tarde."
            },
            style = MaterialTheme.typography.bodySmall,
            color = mutedColor.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun CatalogSection(
    section: TemplateCatalogSection,
    bodyColor: Color,
    mutedColor: Color,
    rowBg: Color,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(section.key) { mutableStateOf(initiallyExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(rowBg)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = buildString {
                        append(section.title)
                        section.subtitle?.let { append(", $it") }
                        append(if (expanded) ", expandido" else ", colapsado")
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = bodyColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!section.subtitle.isNullOrBlank()) {
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Colapsar sección" else "Expandir sección",
                tint = mutedColor,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = { content() },
            )
        }
    }
}
