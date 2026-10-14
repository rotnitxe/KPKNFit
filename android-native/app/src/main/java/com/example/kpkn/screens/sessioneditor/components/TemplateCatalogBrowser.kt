package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.templates.SessionTemplateDurationBucket
import com.example.kpkn.domain.templates.SessionTemplateFacets
import com.example.kpkn.domain.templates.TemplateCatalogFilterLogic
import com.example.kpkn.domain.templates.TemplateCatalogFilters
import com.example.kpkn.domain.templates.TemplateCatalogNestedGroup
import com.example.kpkn.domain.templates.TemplateCatalogSection
import com.example.kpkn.domain.templates.TemplateGroupMode
import com.example.kpkn.domain.templates.TemplateSessionGoal
import com.example.kpkn.domain.templates.TemplateSessionType
import com.example.kpkn.domain.templates.TemplateSessionZone
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
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
                    onFailure = { _ ->
                        UserTemplateSaveState.Error(
                            "No se pudo guardar la plantilla. Inténtalo de nuevo.",
                        )
                    },
                )
            },
            onFailure = { _ ->
                UserTemplateSaveState.Error(
                    "No se pudo guardar la plantilla. Inténtalo de nuevo.",
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

/**
 * Pestaña de plantillas (SE4b): «Mis plantillas» en su módulo, con «Guardar esta sesión» en la cabecera; la fila de filtros; y el catálogo
 * de KPKN en módulos por grupo. Filtros, búsqueda, guardado, edición, archivado y borrado conservan su lógica.
 */
@Composable
internal fun TemplateCatalogBrowser(
    templates: List<SessionTemplate>,
    searchQuery: String,
    onSelectTemplate: (SessionTemplate) -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    /** Archived USER templates stay out of application results but remain manageable. */
    archivedUserTemplates: List<SessionTemplate> = emptyList(),
    templatesReady: Boolean = true,
    corruptTemplateIds: List<String> = emptyList(),
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
    /** Resumen de la sesión que «Guardar esta sesión» guardaría, p. ej. «6 ejercicios · 22 series». Sin él, el diálogo no lo muestra. */
    sessionSummary: String? = null,
    modifier: Modifier = Modifier,
) {
    var templateBeingEdited by remember { mutableStateOf<SessionTemplate?>(null) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // La primera pintura no espera al cálculo de AUGE: las facetas de cada fila se calculan en segundo plano, al pintarla.
    val facetsById = emptyMap<String, SessionTemplateFacets>()
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
    // Las plantillas propias van en su módulo; el catálogo son las de KPKN, agrupadas.
    val userTemplates = remember(filteredTemplates) {
        filteredTemplates.filter { it.sourceType == SessionTemplateSourceType.USER }
    }
    val catalogTemplates = remember(filteredTemplates) {
        filteredTemplates.filter { it.sourceType != SessionTemplateSourceType.USER }
    }
    val archivedMatches = remember(archivedUserTemplates, facetsById, filters) {
        TemplateCatalogFilterLogic.filterTemplates(archivedUserTemplates, facetsById, filters)
    }
    val sections = remember(catalogTemplates, facetsById, activeGroupMode, splits) {
        TemplateCatalogFilterLogic.groupTemplates(
            templates = catalogTemplates,
            facetsById = facetsById,
            mode = activeGroupMode,
            splits = splits,
        )
    }
    val ownTemplates = remember(templates) {
        templates.filter { it.sourceType == SessionTemplateSourceType.USER }
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
        item(key = "user-templates") {
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                ModuleHeaderRow(label = "Mis plantillas") {
                    KpknTextAction(
                        text = "Guardar esta sesión",
                        onClick = { showSaveSheet = true },
                        leadingIcon = KpknLineIcons.Plus,
                    )
                }
                if (!templatesReady) {
                    ModuleCaption("Cargando plantillas…")
                } else if (ownTemplates.isEmpty() && archivedUserTemplates.isEmpty()) {
                    ModuleCaption("Todavía no tienes plantillas guardadas.")
                } else if (userTemplates.isEmpty() && archivedMatches.isEmpty()) {
                    ModuleCaption("Ninguna de tus plantillas coincide con la búsqueda o los filtros.")
                }
                if (corruptTemplateIds.isNotEmpty()) {
                    Text(
                        text = if (corruptTemplateIds.size == 1) {
                            "Hay 1 plantilla ilegible en cuarentena. No se aplicará hasta reparar el archivo."
                        } else {
                            "Hay ${corruptTemplateIds.size} plantillas ilegibles en cuarentena. No se aplicarán hasta reparar el archivo."
                        },
                        style = KpknType.caption,
                        color = SymbolPalette.energia,
                    )
                }
                val rows = userTemplates.map { it to false } + archivedMatches.map { it to true }
                rows.forEachIndexed { index, (template, archived) ->
                    if (index > 0) ModuleDivider()
                    CatalogTemplateRow(
                        template = template,
                        exerciseIndex = exerciseIndex,
                        onApply = if (archived) null else ({ onSelectTemplate(template) }),
                        manage = UserTemplateActions(
                            archived = archived,
                            onEdit = { templateBeingEdited = template },
                            onArchiveOrRestore = {
                                if (archived) onRestoreUserTemplate(template.id) else onArchiveUserTemplate(template.id)
                            },
                            onDelete = { pendingDeleteId = template.id },
                        ),
                    )
                }
            }
        }

        item(key = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatalogFilterChip(
                    text = "Agrupar: ${activeGroupMode.label.removePrefix("Por ").replaceFirstChar { it.uppercase() }}",
                    selected = false,
                    menuRows = VisibleGroupModes.size,
                ) { close ->
                    VisibleGroupModes.forEach { mode ->
                        CatalogFilterOptionRow(
                            label = mode.label,
                            selected = mode == activeGroupMode,
                            onClick = {
                                groupMode = mode.name
                                close()
                            },
                        )
                    }
                }
                CatalogFilterChip(
                    text = activeDifficulty?.let { "Nivel: ${TemplateCatalogFilterLogic.difficultyLabel(it)}" } ?: "Nivel",
                    selected = activeDifficulty != null,
                    menuRows = Difficulty.entries.size + 1,
                ) { close ->
                    CatalogFilterOptionRow(
                        label = "Todos",
                        selected = activeDifficulty == null,
                        onClick = {
                            difficultyName = null
                            close()
                        },
                    )
                    Difficulty.entries.forEach { option ->
                        CatalogFilterOptionRow(
                            label = TemplateCatalogFilterLogic.difficultyLabel(option),
                            selected = activeDifficulty == option,
                            onClick = {
                                difficultyName = option.name
                                close()
                            },
                        )
                    }
                }
                CatalogFilterChip(
                    text = if (activeDuration == SessionTemplateDurationBucket.ALL) {
                        "Duración"
                    } else {
                        "Duración: ${TemplateCatalogFilterLogic.durationLabel(activeDuration)}"
                    },
                    selected = activeDuration != SessionTemplateDurationBucket.ALL,
                    menuRows = SessionTemplateDurationBucket.entries.size,
                ) { close ->
                    SessionTemplateDurationBucket.entries.forEach { option ->
                        CatalogFilterOptionRow(
                            label = TemplateCatalogFilterLogic.durationLabel(option),
                            selected = activeDuration == option,
                            onClick = {
                                durationName = option.name
                                close()
                            },
                        )
                    }
                }
                FriendlyGroupChips.forEach { chip ->
                    KpknChip(
                        text = templateCatalogText(chip.label),
                        selected = activeSessionType == chip,
                        onClick = {
                            sessionTypeName = if (activeSessionType == chip) TemplateSessionType.ALL.name else chip.name
                        },
                    )
                }
            }
        }

        if (filters.hasActiveFilters) {
            item(key = "filter-summary") {
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
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    if (hasConstraintFilters) {
                        KpknTextAction(
                            text = "Limpiar",
                            onClick = { clearAll() },
                            modifier = Modifier.semantics { contentDescription = "Limpiar filtros de sesión" },
                        )
                    }
                }
            }
        }

        if (sections.isEmpty()) {
            item(key = "empty") {
                CatalogEmptyState(
                    hasQueryOrFilters = filters.hasActiveFilters,
                    searchQuery = searchQuery,
                )
            }
        } else {
            items(items = sections, key = { it.key }) { section ->
                CatalogSectionModule(
                    section = section,
                    initiallyExpanded = filters.hasActiveFilters || activeGroupMode == TemplateGroupMode.MUSCLE_GROUP,
                    exerciseIndex = exerciseIndex,
                    onSelectTemplate = onSelectTemplate,
                )
            }
        }
    }

    pendingDeleteId?.let { id ->
        val pendingName = (templates + archivedUserTemplates).firstOrNull { it.id == id }?.name
        KpknAlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = "Eliminar plantilla",
            text = if (pendingName.isNullOrBlank()) {
                "Esta acción es permanente. ¿Eliminar la plantilla?"
            } else {
                "Esta acción es permanente. ¿Eliminar «$pendingName»?"
            },
            confirmLabel = "Eliminar",
            onConfirm = {
                onDeleteUserTemplate(id)
                pendingDeleteId = null
            },
            dismissLabel = "Cancelar",
            onDismiss = { pendingDeleteId = null },
            destructive = true,
        )
    }
    templateBeingEdited?.let { template ->
        UserTemplateMetadataSheet(
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
    if (showSaveSheet) {
        SaveUserTemplateSheet(
            sessionSummary = sessionSummary,
            onDismiss = { showSaveSheet = false },
            onSave = onSaveCurrentTemplate,
        )
    }
}

/**
 * Cabecera de módulo con una acción (SE4b): la etiqueta en eyebrow a la izquierda y la acción a la derecha. Si no caben en una línea (letra
 * al 130 %), la acción baja a la línea siguiente, alineada a la izquierda: ni la etiqueta ni la acción se cortan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleHeaderRow(
    label: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.heightIn(min = 48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label.uppercase(Locale.forLanguageTag("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
        }
        action()
    }
}

@Composable
private fun ModuleCaption(text: String) {
    Text(text = text, style = KpknType.caption, color = WizardColors.textMuted)
}

@Composable
private fun ModuleDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}

/**
 * Filtro de la fila: una `KpknChip` que abre un menú de vidrio anclado a sí misma con sus opciones. Elegir una opción llama a [menu] con
 * [close] para cerrarlo.
 */
@Composable
private fun CatalogFilterChip(
    text: String,
    selected: Boolean,
    menuRows: Int,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    KpknChip(
        text = text,
        selected = selected,
        onClick = { menuOpen = true },
        modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
    )
    val rect = anchor
    if (menuOpen && rect != null) {
        KpknContextMenu(anchor = rect, rows = menuRows, onDismiss = { menuOpen = false }) {
            menu { menuOpen = false }
        }
    }
}

/** Opción de un filtro dentro de su menú: 52 dp, con marca de check en la elegida. */
@Composable
private fun CatalogFilterOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = if (selected) WizardColors.text else WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = KpknLineIcons.Check,
                contentDescription = null,
                tint = WizardColors.text,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CatalogEmptyState(
    hasQueryOrFilters: Boolean,
    searchQuery: String,
) {
    val query = searchQuery.trim()
    KpknEmptyState(
        icon = if (hasQueryOrFilters) KpknLineIcons.Search else KpknLineIcons.Book,
        title = when {
            !hasQueryOrFilters -> "No hay plantillas en el catálogo"
            query.isNotEmpty() -> "Ninguna plantilla coincide con «$query»"
            else -> "Ninguna plantilla coincide con los filtros"
        },
        body = when {
            !hasQueryOrFilters -> "Guarda una sesión como plantilla o vuelve más tarde."
            query.isNotEmpty() -> "Prueba con otra palabra o quita algún filtro."
            else -> "Prueba a quitar algún filtro."
        },
    )
}

/** Un grupo del catálogo en su módulo: cabecera con el número de plantillas y la flecha que lo despliega. */
@Composable
private fun CatalogSectionModule(
    section: TemplateCatalogSection,
    initiallyExpanded: Boolean,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    onSelectTemplate: (SessionTemplate) -> Unit,
) {
    var expanded by rememberSaveable(section.key) { mutableStateOf(initiallyExpanded) }
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = if (expanded) 8.dp else 18.dp,
        ),
    ) {
        ModuleHeaderRow(
            label = templateCatalogText(section.title),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = if (expanded) "Colapsar sección" else "Expandir sección",
                onClick = { expanded = !expanded },
            ),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = templatesCountText(section.templateCount),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = KpknLineIcons.ChevronDown,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                if (section.nestedGroups.isNotEmpty()) {
                    section.nestedGroups.forEach { nested ->
                        NestedGroupRows(
                            nested = nested,
                            exerciseIndex = exerciseIndex,
                            onSelectTemplate = onSelectTemplate,
                        )
                    }
                } else {
                    section.templates.forEachIndexed { index, template ->
                        if (index > 0) ModuleDivider()
                        CatalogTemplateRow(
                            template = template,
                            exerciseIndex = exerciseIndex,
                            onApply = { onSelectTemplate(template) },
                            manage = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NestedGroupRows(
    nested: TemplateCatalogNestedGroup,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    onSelectTemplate: (SessionTemplate) -> Unit,
) {
    Text(
        text = "${templateCatalogText(nested.title)} · ${nested.templates.size}",
        style = KpknType.caption,
        color = WizardColors.textMuted,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
    nested.templates.forEachIndexed { index, template ->
        if (index > 0) ModuleDivider()
        CatalogTemplateRow(
            template = template,
            exerciseIndex = exerciseIndex,
            onApply = { onSelectTemplate(template) },
            manage = null,
        )
    }
}

/** Editar los metadatos de una plantilla propia, en una hoja. El pie es el resumen de su propia sesión. */
@Composable
private fun UserTemplateMetadataSheet(
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
    var isSaving by remember(template.id) { mutableStateOf(false) }
    var saveError by remember(template.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val exercises = template.session.allExercises()
    val summary = templateCountsLine(exercises.size, exercises.sumOf { it.sets.size })

    KpknSheet(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Editar plantilla", style = KpknType.title, color = WizardColors.text)
            KpknTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre",
                singleLine = true,
                enabled = !isSaving,
            )
            KpknTextField(
                value = description,
                onValueChange = { description = it },
                label = "Descripción",
                singleLine = false,
                minLines = 2,
                enabled = !isSaving,
            )
            MetadataChoices(
                label = "Grupo",
                options = listOf<SessionTemplateFocusCategory?>(null) + SessionTemplateFocusCategory.entries,
                selected = focus,
                labelOf = { category ->
                    category?.let { templateCatalogText(TemplateCatalogFilterLogic.focusCategoryLabel(it)) } ?: "Sin foco"
                },
                onSelect = { focus = it },
                enabled = !isSaving,
            )
            MetadataChoices(
                label = "Nivel",
                options = Difficulty.entries,
                selected = difficulty,
                labelOf = { TemplateCatalogFilterLogic.difficultyLabel(it) },
                onSelect = { difficulty = it },
                enabled = !isSaving,
            )
            MetadataChoices(
                label = "Duración",
                options = SessionTemplateDurationClass.entries,
                selected = duration,
                labelOf = { durationClassLabel(it) },
                onSelect = { duration = it },
                enabled = !isSaving,
            )
            KpknTextField(
                value = splitIds,
                onValueChange = { splitIds = it },
                label = "Divisiones (ids separados por coma)",
                singleLine = true,
                enabled = !isSaving,
            )
            KpknTextField(
                value = dayLabels,
                onValueChange = { dayLabels = it },
                label = "Etiquetas de día (separadas por coma)",
                singleLine = true,
                enabled = !isSaving,
            )
            KpknCheckRow(
                checked = autoGeneration,
                title = "Permitir autogeneración semanal",
                onClick = { if (!isSaving) autoGeneration = !autoGeneration },
                enabled = !isSaving,
            )
            Text(text = summary, style = KpknType.caption, color = WizardColors.textMuted)
            saveError?.let { message ->
                Text(text = message, style = KpknType.caption, color = WizardColors.danger)
            }
            KpknPrimaryPill(
                text = if (isSaving) "Guardando…" else "Guardar",
                enabled = name.isNotBlank() && !isSaving,
                onClick = {
                    if (isSaving) return@KpknPrimaryPill
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
                        isSaving = false
                        if (result.isSuccess) {
                            onDismiss()
                        } else {
                            saveError = "No se pudieron actualizar los metadatos."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            KpknTextAction(
                text = "Cancelar",
                onClick = { if (!isSaving) onDismiss() },
                color = WizardColors.textMuted,
            )
        }
    }
}

/**
 * Una elección de metadatos: una fila de `KpknChip` con la opción elegida seleccionada. Sustituye a los desplegables de antes; las
 * opciones y el valor son los mismos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MetadataChoices(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                KpknChip(
                    text = labelOf(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                )
            }
        }
    }
}

/** Etiqueta de la duración de una plantilla. La clase «estándar» del dominio se ve como «Media», como el filtro de duración. */
private fun durationClassLabel(duration: SessionTemplateDurationClass): String = when (duration) {
    SessionTemplateDurationClass.SHORT -> "Corta"
    SessionTemplateDurationClass.STANDARD -> "Media"
    SessionTemplateDurationClass.LONG -> "Larga"
}

/**
 * Guardar la sesión actual como plantilla. El pie es el resumen de la sesión que se guardaría (si quien abre el diálogo lo pasa). El
 * guardado es el mismo comando que antes: nombre y descripción.
 */
@Composable
private fun SaveUserTemplateSheet(
    sessionSummary: String?,
    onDismiss: () -> Unit,
    onSave: suspend (String, String) -> Result<SessionTemplate>,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saveState by remember { mutableStateOf<UserTemplateSaveState>(UserTemplateSaveState.Idle) }
    val scope = rememberCoroutineScope()
    val isSaving = saveState is UserTemplateSaveState.Saving
    val error = (saveState as? UserTemplateSaveState.Error)?.message

    KpknSheet(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Guardar sesión como plantilla", style = KpknType.title, color = WizardColors.text)
            KpknTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre",
                singleLine = true,
                enabled = !isSaving,
            )
            KpknTextField(
                value = description,
                onValueChange = { description = it },
                label = "Descripción",
                singleLine = false,
                minLines = 2,
                enabled = !isSaving,
            )
            sessionSummary?.let { summary ->
                Text(text = summary, style = KpknType.caption, color = WizardColors.textMuted)
            }
            error?.let { message ->
                Text(text = message, style = KpknType.caption, color = WizardColors.danger)
            }
            KpknPrimaryPill(
                text = if (isSaving) "Guardando…" else "Guardar",
                enabled = name.isNotBlank() && !isSaving,
                onClick = {
                    if (isSaving) return@KpknPrimaryPill
                    saveState = UserTemplateSaveState.Saving
                    scope.launch {
                        val result = executeUserTemplateSave(name, description, onSave)
                        saveState = result
                        // El comando de guardado vuelve solo cuando Room ya aceptó la fila; al cerrar, el Flow la pinta.
                        if (result is UserTemplateSaveState.Success) {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            KpknTextAction(
                text = "Cancelar",
                onClick = { if (!isSaving) onDismiss() },
                color = WizardColors.textMuted,
            )
        }
    }
}
