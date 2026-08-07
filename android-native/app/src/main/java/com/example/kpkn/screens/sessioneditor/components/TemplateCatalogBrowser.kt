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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.SessionTemplateDurationBucket
import com.example.kpkn.domain.templates.SessionTemplateFacetsCache
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
    glassDark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (glassDark) Color.White else MaterialTheme.colorScheme.primary
    val bodyColor = if (glassDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (glassDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    val rowBg = if (glassDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    val templateIdsKey = remember(templates) { templates.map { it.id } }
    val facetsById by produceState<Map<String, com.example.kpkn.domain.templates.SessionTemplateFacets>>(initialValue = emptyMap(), templateIdsKey, exerciseIndex.size) {
        value = withContext(Dispatchers.Default) {
            SessionTemplateFacetsCache.getOrBuild(templates, exerciseIndex)
        }
    }
    val splits = remember { SPLIT_TEMPLATES.filterNot { it.id == "custom" } }
    if (facetsById.isEmpty() && templates.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

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
                            text = "Agrupado por ${activeGroupMode.label.lowercase()}",
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
