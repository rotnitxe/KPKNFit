package com.example.kpkn.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.catalogConfigurationDisplayName
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.ui.components.CONCEPTS_LINK_COLOR
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton

/**
 * Hoja «Cómo funciona» de un plan (C.P8): qué haces, tu semana tipo, material, fuente, notas del método y
 * glosario, en el orden de [PlanInfoModel]. Es la única hoja que explica un plan: la biblioteca, el asistente
 * y el detalle del programa la abren con su [mode], y [ProtocolDetailSheet] delega en ella.
 *
 * Todo el texto sale de [PlanInfoModelBuilder]; aquí solo se pinta.
 *
 * @param readyWeek primera semana real del candidato listo del asistente; solo la pintan los planes sin receta.
 * @param onPrimaryAction acción del botón primario; sin ella no hay botón (consulta de solo lectura).
 * @param onOpenConcept abre un concepto de Conceptos clave por su id; sin él, el glosario no ofrece el enlace
 *   (en el asistente no se navega).
 * @param primaryActionLabel sustituye la etiqueta que el modo le da al botón primario (la usa
 *   [ProtocolDetailSheet] para conservar «Usar este plan» o «Reemplazar plan» de su llamador).
 * @param secondaryActionLabel texto de una segunda acción opcional («Crear una copia»), con [onSecondaryAction].
 */
@Composable
fun PlanInfoSheet(
    entry: CatalogEntry,
    mode: PlanInfoMode,
    readyWeek: ReadyWeekSnapshot? = null,
    onDismiss: () -> Unit,
    onPrimaryAction: (() -> Unit)? = null,
    onOpenConcept: ((String) -> Unit)? = null,
    primaryActionLabel: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val model = remember(entry.id, mode, readyWeek) {
        PlanInfoModelBuilder.build(
            entry = entry,
            mode = mode,
            names = { configurationId -> catalogConfigurationDisplayName(configurationId) },
            equipmentOf = { configurationId ->
                CompositionMetadataHolder.current?.metadata(configurationId)?.equipmentId
            },
            readyWeek = readyWeek,
        )
    }
    val uriHandler = LocalUriHandler.current
    val primaryLabel = primaryActionLabel ?: model.primaryActionLabel
    val hasPrimary = primaryLabel != null && onPrimaryAction != null

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KpknSheetTokens.ContentPaddingHorizontal, vertical = 8.dp)
                .testTag("plan-info-sheet"),
            verticalArrangement = Arrangement.spacedBy(KpknSheetTokens.SectionGap),
        ) {
            PlanInfoHeader(model)

            PlanInfoSection("Qué haces") {
                Text(model.summary, style = MaterialTheme.typography.bodyMedium, color = KpknSheetTokens.Body)
            }

            when (val week = model.typicalWeek) {
                is TypicalWeek.Generated -> PlanInfoSection("Tu semana tipo") {
                    Text(week.text, style = MaterialTheme.typography.bodyMedium, color = KpknSheetTokens.MutedStrong)
                }
                is TypicalWeek.Days -> PlanInfoSection("Tu semana tipo") {
                    week.caption?.let { caption ->
                        Text(caption, style = MaterialTheme.typography.labelMedium, color = KpknSheetTokens.Muted)
                    }
                    week.days.forEach { day -> PlanInfoDay(day) }
                }
                TypicalWeek.Unavailable -> Unit
            }

            if (model.material.isNotEmpty()) {
                PlanInfoSection("Material") { PlanInfoChips(model.material) }
            }

            model.source?.let { source ->
                PlanInfoSection("Fuente") {
                    source.attributionLine?.let { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = KpknSheetTokens.MutedStrong)
                    }
                    val url = source.url
                    val urlLabel = source.urlLabel
                    if (url != null && urlLabel != null) {
                        Text(
                            text = urlLabel,
                            modifier = Modifier
                                .clickable(role = Role.Button) { runCatching { uriHandler.openUri(url) } }
                                .padding(vertical = 6.dp)
                                .testTag("plan-info-source-link"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = CONCEPTS_LINK_COLOR,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                    source.editionLine?.let { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall, color = KpknSheetTokens.Muted)
                    }
                    source.authoredRules?.let { rules -> PlanInfoFold("Lo que publica el autor", rules) }
                    source.kpknDefaults?.let { defaults -> PlanInfoFold("Configuración inicial KPKN", defaults) }
                }
            }

            if (model.notes.isNotEmpty()) {
                PlanInfoSection("Notas del método") {
                    model.notes.forEach { note ->
                        Text("• $note", style = MaterialTheme.typography.bodySmall, color = KpknSheetTokens.MutedStrong)
                    }
                }
            }

            if (model.glossary.isNotEmpty()) {
                PlanInfoSection("Glosario") {
                    model.glossary.forEach { glossaryEntry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                glossaryEntry.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = KpknSheetTokens.Body,
                            )
                            Text(
                                glossaryEntry.definition,
                                style = MaterialTheme.typography.bodySmall,
                                color = KpknSheetTokens.MutedStrong,
                            )
                            val conceptId = glossaryEntry.conceptId
                            if (conceptId != null && onOpenConcept != null) {
                                TextButton(onClick = { onOpenConcept(conceptId) }) {
                                    Text("Ver en Conceptos clave", color = CONCEPTS_LINK_COLOR, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KpknSheetTokens.ContentPaddingHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (primaryLabel != null && onPrimaryAction != null) {
                KpknSheetWhiteButton(
                    text = primaryLabel,
                    onClick = onPrimaryAction,
                    modifier = Modifier.testTag("plan-info-primary"),
                )
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier.fillMaxWidth().testTag("plan-info-secondary"),
                ) {
                    Text(secondaryActionLabel, color = Color.White)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("plan-info-dismiss")) {
                Text(if (hasPrimary) "Cancelar" else "Cerrar", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanInfoHeader(model: PlanInfoModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = model.displayName,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = KpknSheetTokens.TitleStrong,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanInfoChip(model.provenanceLabel)
            PlanInfoChip(model.levelLabel)
            model.kindLabel?.let { PlanInfoChip(it) }
        }
    }
}

@Composable
private fun PlanInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = KpknSheetTokens.TitleStrong,
        )
        content()
    }
}

@Composable
private fun PlanInfoDay(day: DayLines) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = KpknSheetTokens.Body,
        )
        day.lines.forEach { line ->
            Text(text = line, style = MaterialTheme.typography.bodySmall, color = KpknSheetTokens.MutedStrong)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanInfoChips(labels: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label -> PlanInfoChip(label) }
    }
}

/** Etiqueta de solo lectura: no se toca, por eso no usa los chips de filtro de las hojas. */
@Composable
private fun PlanInfoChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(KpknSheetTokens.ChipIdle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = KpknSheetTokens.ChipLabel,
        )
    }
}

/** Bloque plegable de la sección «Fuente»: empieza cerrado y enseña una línea por regla. */
@Composable
private fun PlanInfoFold(title: String, lines: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = KpknSheetTokens.Body,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = CONCEPTS_LINK_COLOR,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lines.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodySmall, color = KpknSheetTokens.MutedStrong)
                }
            }
        }
    }
}
