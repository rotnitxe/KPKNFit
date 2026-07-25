package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import androidx.compose.animation.animateContentSize
import com.example.kpkn.domain.exercises.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun TemplateCatalogBrowser(
    templates: List<SessionTemplate>,
    searchQuery: String,
    onSelectTemplate: (SessionTemplate) -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
) {
    val splits = remember { SPLIT_TEMPLATES.filterNot { it.id == "custom" } }

    val splitsWithGroups = remember(templates, splits, exerciseIndex) {
        splits.map { split ->
            split to SessionTemplateCatalogPolicy.templatesForSplit(split, templates, exerciseIndex)
        }.filter { it.second.any { g -> g.templates.isNotEmpty() } }
    }

    val independentGroups = remember(templates) {
        SessionTemplateCatalogPolicy.independentTemplateGroups(templates)
    }

    val userGroup = remember(templates) {
        SessionTemplateCatalogPolicy.userTemplateGroup(templates)
    }

    val isSearching = searchQuery.isNotBlank()
    val filteredTemplates = remember(templates, searchQuery) {
        if (searchQuery.isBlank()) templates
        else templates.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.muscleGroupsSummary.contains(searchQuery, ignoreCase = true) ||
            it.shortDescription.contains(searchQuery, ignoreCase = true)
        }
    }

    if (isSearching) {
        if (filteredTemplates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin resultados para \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Resultados de búsqueda:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                filteredTemplates.forEach { template ->
                    CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (userGroup.templates.isNotEmpty()) {
                var userExpanded by rememberSaveable("user-templates") { mutableStateOf(true) }

                Text(
                    text = "Mis plantillas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { userExpanded = !userExpanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Guardadas por ti",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${userGroup.templates.size} ${if (userGroup.templates.size == 1) "plantilla" else "plantillas"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (userExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = userExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                userGroup.templates.forEach { template ->
                                    CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                                }
                            }
                        }
                    }
                }
            }

            if (splitsWithGroups.isNotEmpty()) {
                Text(
                    text = "Organizado por Split",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                splitsWithGroups.forEach { (split, dayGroups) ->
                    var splitExpanded by rememberSaveable("split-${split.id}") { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { splitExpanded = !splitExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = split.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (split.description.isNotBlank()) {
                                        Text(
                                            text = split.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (splitExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = splitExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    dayGroups.forEach { group ->
                                        if (group.templates.isNotEmpty()) {
                                            var dayExpanded by rememberSaveable("split-${split.id}-day-${group.dayIndex}") { mutableStateOf(true) }

                                            Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { dayExpanded = !dayExpanded }
                                                        .padding(vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                                        modifier = Modifier.height(24.dp)
                                                    ) {
                                                        Text(
                                                            text = group.dayLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "${group.templates.size} ${if (group.templates.size == 1) "opción" else "opciones"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(Modifier.weight(1f))
                                                    Icon(
                                                        imageVector = if (dayExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                AnimatedVisibility(visible = dayExpanded) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        group.templates.forEach { template ->
                                                            CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (independentGroups.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Plantillas por Enfoque",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                independentGroups.forEach { group ->
                    var focusExpanded by rememberSaveable("focus-${group.category.name}") { mutableStateOf(false) }

                    val categoryLabel = when (group.category) {
                        SessionTemplateFocusCategory.PIERNAS -> "Piernas"
                        SessionTemplateFocusCategory.BRAZOS -> "Brazos"
                        SessionTemplateFocusCategory.GLUTEOS -> "Glúteos"
                        SessionTemplateFocusCategory.PECHO -> "Pecho"
                        SessionTemplateFocusCategory.ESPALDA -> "Espalda"
                        SessionTemplateFocusCategory.HOMBROS -> "Hombros"
                        SessionTemplateFocusCategory.FULL_BODY -> "Full Body"
                        SessionTemplateFocusCategory.POWERLIFTING -> "Powerlifting"
                        SessionTemplateFocusCategory.MINIMALISTA -> "Minimalista"
                        SessionTemplateFocusCategory.RECUPERACION -> "Recuperación"
                    }

                    val categoryEmoji = when (group.category) {
                        SessionTemplateFocusCategory.PIERNAS -> "🦵"
                        SessionTemplateFocusCategory.BRAZOS -> "💪"
                        SessionTemplateFocusCategory.GLUTEOS -> "🍑"
                        SessionTemplateFocusCategory.PECHO -> "🛡️"
                        SessionTemplateFocusCategory.ESPALDA -> "🦅"
                        SessionTemplateFocusCategory.HOMBROS -> "✈️"
                        SessionTemplateFocusCategory.FULL_BODY -> "🌟"
                        SessionTemplateFocusCategory.POWERLIFTING -> "🏋️"
                        SessionTemplateFocusCategory.MINIMALISTA -> "⚡"
                        SessionTemplateFocusCategory.RECUPERACION -> "🩹"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { focusExpanded = !focusExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = categoryEmoji, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = categoryLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${group.templates.size} ${if (group.templates.size == 1) "plantilla" else "plantillas"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (focusExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = focusExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    group.templates.forEach { template ->
                                        CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
