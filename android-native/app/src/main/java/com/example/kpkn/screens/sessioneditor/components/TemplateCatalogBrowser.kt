package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.SessionTemplateDurationBucket
import com.example.kpkn.domain.templates.SessionTemplateFacetsBuilder
import com.example.kpkn.domain.templates.TemplateCatalogFilterLogic
import com.example.kpkn.domain.templates.TemplateCatalogFilters
import com.example.kpkn.domain.templates.TemplateCatalogSection
import com.example.kpkn.domain.templates.TemplateGroupMode
import com.example.kpkn.domain.templates.TemplateSessionGoal
import com.example.kpkn.domain.templates.TemplateSessionType
import com.example.kpkn.domain.templates.TemplateSessionZone
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip

// Chips de acceso rápido por tipo de sesión (día de entreno).
private val SimpleSessionTypeChips = listOf(
    TemplateSessionType.EMPUJE,
    TemplateSessionType.TIRON,
    TemplateSessionType.PIERNAS,
    TemplateSessionType.TORSO,
    TemplateSessionType.FULL_BODY,
    TemplateSessionType.GLUTEOS,
    TemplateSessionType.BRAZOS,
)

private val AdvancedSessionTypeChips = SimpleSessionTypeChips + listOf(
    TemplateSessionType.PECHO,
    TemplateSessionType.ESPALDA,
    TemplateSessionType.HOMBROS,
    TemplateSessionType.CORE,
    TemplateSessionType.POWERLIFTING,
    TemplateSessionType.MINIMALISTA,
    TemplateSessionType.RECUPERACION,
)

@Composable
internal fun TemplateCatalogBrowser(
    templates: List<SessionTemplate>,
    searchQuery: String,
    onSelectTemplate: (SessionTemplate) -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    glassDark: Boolean = false,
) {
    val titleColor = if (glassDark) Color.White else MaterialTheme.colorScheme.primary
    val bodyColor = if (glassDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (glassDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    val rowBg = if (glassDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    val facetsById = remember(templates, exerciseIndex) {
        SessionTemplateFacetsBuilder.buildAll(templates, exerciseIndex)
    }
    val splits = remember { SPLIT_TEMPLATES.filterNot { it.id == "custom" } }

    var advanced by rememberSaveable { mutableStateOf(false) }
    var groupMode by rememberSaveable { mutableStateOf(TemplateGroupMode.SPLIT.name) }
    var sessionTypeName by rememberSaveable { mutableStateOf(TemplateSessionType.ALL.name) }
    var goalName by rememberSaveable { mutableStateOf(TemplateSessionGoal.ALL.name) }
    var zoneName by rememberSaveable { mutableStateOf(TemplateSessionZone.ALL.name) }
    var difficultyName by rememberSaveable { mutableStateOf<String?>(null) }
    var durationName by rememberSaveable { mutableStateOf(SessionTemplateDurationBucket.ALL.name) }

    val activeGroupMode = remember(groupMode) {
        TemplateGroupMode.entries.find { it.name == groupMode } ?: TemplateGroupMode.SPLIT
    }
    val activeSessionType = remember(sessionTypeName) {
        TemplateSessionType.entries.find { it.name == sessionTypeName } ?: TemplateSessionType.ALL
    }
    val activeGoal = remember(goalName) {
        TemplateSessionGoal.entries.find { it.name == goalName } ?: TemplateSessionGoal.ALL
    }
    val activeZone = remember(zoneName) {
        TemplateSessionZone.entries.find { it.name == zoneName } ?: TemplateSessionZone.ALL
    }
    val activeDifficulty = remember(difficultyName) {
        difficultyName?.let { name -> Difficulty.entries.find { it.name == name } }
    }
    val activeDuration = remember(durationName) {
        SessionTemplateDurationBucket.entries.find { it.name == durationName }
            ?: SessionTemplateDurationBucket.ALL
    }

    // Al pasar a Simple, limpiar filtros y agrupaciones solo-avanzados.
    LaunchedEffect(advanced) {
        if (!advanced) {
            goalName = TemplateSessionGoal.ALL.name
            zoneName = TemplateSessionZone.ALL.name
            durationName = SessionTemplateDurationBucket.ALL.name
            if (groupMode == TemplateGroupMode.GOAL.name || groupMode == TemplateGroupMode.DURATION.name) {
                groupMode = TemplateGroupMode.SPLIT.name
            }
        }
    }

    val filters = remember(
        searchQuery,
        activeSessionType,
        activeGoal,
        activeZone,
        activeDifficulty,
        activeDuration,
    ) {
        TemplateCatalogFilters(
            searchQuery = searchQuery,
            sessionType = activeSessionType,
            goal = activeGoal,
            zone = activeZone,
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

    val visibleGroupModes = remember(advanced) {
        if (advanced) TemplateGroupMode.entries
        else TemplateGroupMode.entries.filter {
            it == TemplateGroupMode.SPLIT || it == TemplateGroupMode.SESSION_TYPE || it == TemplateGroupMode.LEVEL
        }
    }

    val visibleSessionChips = remember(advanced) {
        if (advanced) AdvancedSessionTypeChips else SimpleSessionTypeChips
    }

    val hasConstraintFilters = activeSessionType != TemplateSessionType.ALL ||
        activeGoal != TemplateSessionGoal.ALL ||
        activeZone != TemplateSessionZone.ALL ||
        activeDifficulty != null ||
        activeDuration != SessionTemplateDurationBucket.ALL

    fun clearAll() {
        sessionTypeName = TemplateSessionType.ALL.name
        goalName = TemplateSessionGoal.ALL.name
        zoneName = TemplateSessionZone.ALL.name
        difficultyName = null
        durationName = SessionTemplateDurationBucket.ALL.name
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Filtros de sesión",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = titleColor,
            )
            Text(
                text = "Elige el tipo de día que quieres entrenar",
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor,
            )
        }

        SimpleAdvancedToggle(
            advanced = advanced,
            onChange = { advanced = it },
            glassDark = glassDark,
            titleColor = titleColor,
            mutedColor = mutedColor,
        )

        FilterDropdownRow(
            advanced = advanced,
            groupMode = activeGroupMode,
            onGroupMode = { groupMode = it.name },
            sessionType = activeSessionType,
            onSessionType = { sessionTypeName = it.name },
            goal = activeGoal,
            onGoal = { goalName = it.name },
            zone = activeZone,
            onZone = { zoneName = it.name },
            difficulty = activeDifficulty,
            onDifficulty = { difficultyName = it?.name },
            duration = activeDuration,
            onDuration = { durationName = it.name },
            visibleGroupModes = visibleGroupModes,
            mutedColor = mutedColor,
        )

        SessionTypeChipsRow(
            chips = visibleSessionChips,
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

        if (filters.hasActiveFilters) {
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

        when {
            filteredTemplates.isEmpty() -> {
                CatalogEmptyState(
                    hasQueryOrFilters = filters.hasActiveFilters,
                    searchQuery = searchQuery,
                    mutedColor = mutedColor,
                )
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!filters.hasActiveFilters) {
                        Text(
                            text = "Agrupado por ${activeGroupMode.label.lowercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = titleColor,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                    sections.forEach { section ->
                        CatalogSection(
                            section = section,
                            bodyColor = bodyColor,
                            mutedColor = mutedColor,
                            rowBg = rowBg,
                            initiallyExpanded = section.key == "user" || filters.hasActiveFilters,
                        ) {
                            if (section.nestedGroups.isNotEmpty()) {
                                section.nestedGroups.forEach { nested ->
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
                                            advanced = advanced,
                                        )
                                    }
                                }
                            } else {
                                section.templates.forEach { template ->
                                    CompactTemplateCard(
                                        template = template,
                                        onApply = { onSelectTemplate(template) },
                                        exerciseIndex = exerciseIndex,
                                        glassDark = glassDark,
                                        facets = facetsById[template.id],
                                        advanced = advanced,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleAdvancedToggle(
    advanced: Boolean,
    onChange: (Boolean) -> Unit,
    glassDark: Boolean,
    titleColor: Color,
    mutedColor: Color,
) {
    val selectedBg = if (glassDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val idleBg = if (glassDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(idleBg)
            .padding(3.dp)
            .semantics { contentDescription = "Modo de filtros Simple o Avanzado" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to "Simple", true to "Avanzado").forEach { (isAdv, label) ->
            val selected = advanced == isAdv
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) selectedBg else Color.Transparent)
                    .clickable { onChange(isAdv) }
                    .padding(vertical = 10.dp)
                    .semantics {
                        contentDescription = if (isAdv) "Modo avanzado" else "Modo simple"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                    color = if (selected) titleColor else mutedColor,
                )
            }
        }
    }
}

@Composable
private fun FilterDropdownRow(
    advanced: Boolean,
    groupMode: TemplateGroupMode,
    onGroupMode: (TemplateGroupMode) -> Unit,
    sessionType: TemplateSessionType,
    onSessionType: (TemplateSessionType) -> Unit,
    goal: TemplateSessionGoal,
    onGoal: (TemplateSessionGoal) -> Unit,
    zone: TemplateSessionZone,
    onZone: (TemplateSessionZone) -> Unit,
    difficulty: Difficulty?,
    onDifficulty: (Difficulty?) -> Unit,
    duration: SessionTemplateDurationBucket,
    onDuration: (SessionTemplateDurationBucket) -> Unit,
    visibleGroupModes: List<TemplateGroupMode>,
    mutedColor: Color,
) {
    var showGroupMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showGoalMenu by remember { mutableStateOf(false) }
    var showZoneMenu by remember { mutableStateOf(false) }
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
                label = if (sessionType == TemplateSessionType.ALL) "Tipo de sesión" else sessionType.label,
                expanded = showTypeMenu,
                onExpandedChange = { showTypeMenu = it },
                contentDescription = "Filtrar por tipo de sesión",
            ) {
                TemplateSessionType.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSessionType(option)
                            showTypeMenu = false
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

            if (advanced) {
                CatalogDropdownButton(
                    label = if (goal == TemplateSessionGoal.ALL) "Objetivo" else goal.label,
                    expanded = showGoalMenu,
                    onExpandedChange = { showGoalMenu = it },
                    contentDescription = "Filtrar por objetivo",
                ) {
                    TemplateSessionGoal.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onGoal(option)
                                showGoalMenu = false
                            },
                        )
                    }
                }

                CatalogDropdownButton(
                    label = if (zone == TemplateSessionZone.ALL) "Zona" else zone.label,
                    expanded = showZoneMenu,
                    onExpandedChange = { showZoneMenu = it },
                    contentDescription = "Filtrar por zona de la sesión",
                ) {
                    TemplateSessionZone.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onZone(option)
                                showZoneMenu = false
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
        }

        Text(
            text = if (advanced) {
                "Objetivo, zona y duración de la sesión · con fatiga AUGE"
            } else {
                "Elige un tipo de día y agrúpalos por rutina o tipo de sesión"
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
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
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
            text = "Tipo de día",
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
                "Prueba a limpiar filtros o cambiar el tipo de día."
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
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
