package com.example.kpkn.screens.programs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton

private enum class LibraryProfileFilter(val label: String) {
    ALL("Todos"), STRENGTH("Fuerza"), MUSCLE("Músculo"), STRENGTH_MUSCLE("Fuerza y músculo"),
    COMPLETE_ATHLETE("Atleta completo"), OTHER("Otros"),
}

private enum class LibraryOriginFilter(val label: String) {
    ALL("Todas"), ORIGINAL("Original fiel"), ADAPTED("Adaptación KPKN"), KPKN("Plan KPKN"), LEGACY("Versión KPKN"),
}

/** One Plans library for complete programs; standalone session templates stay in the session editor. */
@Composable
fun CreateProgramTemplateSheet(
    onDismiss: () -> Unit,
    onCreateBlank: (() -> Unit)?,
    onCreateFromTemplate: (ProgramTemplateOption) -> Unit,
    onSelectProtocol: (Protocol) -> Unit = {},
    /** Non-legacy entries continue through setup/evaluation rather than a second recipe compiler. */
    onSelectPlan: ((CatalogEntry) -> Unit)? = null,
) {
    var profileFilter by remember { mutableStateOf(LibraryProfileFilter.ALL) }
    var daysFilter by remember { mutableStateOf<Int?>(null) }
    var durationFilter by remember { mutableStateOf<CatalogDuration?>(null) }
    var originFilter by remember { mutableStateOf(LibraryOriginFilter.ALL) }
    var infoEntry by remember { mutableStateOf<CatalogEntry?>(null) }

    // Solo lo listado: los históricos ocultos (C.P2b) siguen resolviéndose por id, pero no se ofrecen.
    val entries = remember {
        PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }
    }
    val visible = entries.filter { entry ->
        (daysFilter == null || daysFilter in entry.supportedFrequencies) &&
            (durationFilter == null || entry.duration == durationFilter) &&
            profileMatches(entry, profileFilter) && originMatches(entry, originFilter)
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Planes", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(
                "Elige un plan para ver cómo funciona. Antes de activarlo confirmamos tu material, tus días y tu tiempo.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )

            FilterRow(
                label = "Perfil",
                options = LibraryProfileFilter.entries.map { it.label },
                selected = profileFilter.label,
                onSelect = { label -> profileFilter = LibraryProfileFilter.entries.first { it.label == label } },
            )
            FilterRow(
                label = "Días",
                options = listOf("Todos") + (1..6).map(Int::toString),
                selected = daysFilter?.toString() ?: "Todos",
                onSelect = { label -> daysFilter = label.toIntOrNull() },
            )
            FilterRow(
                label = "Duración",
                options = listOf("Todas", "Semana repetible", "Ciclo finito"),
                selected = when (durationFilter) {
                    null -> "Todas"
                    CatalogDuration.REPEATING_WEEK -> "Semana repetible"
                    CatalogDuration.REPEATING_CYCLE -> "Ciclo repetible"
                    CatalogDuration.FINITE_CYCLE -> "Ciclo finito"
                },
                onSelect = { label ->
                    durationFilter = when (label) {
                        "Semana repetible" -> CatalogDuration.REPEATING_WEEK
                        "Ciclo repetible" -> CatalogDuration.REPEATING_CYCLE
                        "Ciclo finito" -> CatalogDuration.FINITE_CYCLE
                        else -> null
                    }
                },
            )
            FilterRow(
                label = "Procedencia",
                options = LibraryOriginFilter.entries.map { it.label },
                selected = originFilter.label,
                onSelect = { label -> originFilter = LibraryOriginFilter.entries.first { it.label == label } },
            )

            Column(
                Modifier.fillMaxWidth().heightIn(max = 390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visible.isEmpty()) {
                    Text("No hay planes con esos filtros.", color = Color.White.copy(alpha = 0.72f))
                }
                visible.forEach { entry ->
                    Surface(
                        onClick = {
                            when {
                                entry.template != null -> onCreateFromTemplate(entry.template)
                                entry.source == CatalogSource.PROTOCOL -> {
                                    val protocol = PROTOCOL_LIBRARY.firstOrNull { it.id == entry.sourceId && it.isVisibleForApplication }
                                    if (protocol != null) onSelectProtocol(protocol)
                                    else if (onSelectPlan != null) onSelectPlan(entry)
                                    else infoEntry = entry
                                }
                                onSelectPlan != null -> onSelectPlan(entry)
                                else -> infoEntry = entry
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = KpknSheetTokens.Panel,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.title, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(provenanceLabel(entry), color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelMedium)
                            Text(entry.description, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${frequencyLabel(entry)} · ${durationLabel(entry)} · ${entry.level.name.lowercase().replaceFirstChar(Char::uppercase)}",
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            entry.authoredSource?.let { source ->
                                Text(
                                    "${source.author} · ${source.editionWithConsult}",
                                    color = Color.White.copy(alpha = 0.62f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            onCreateBlank?.let { create -> KpknSheetWhiteButton(text = "Crear desde cero", onClick = create) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar", color = Color.White) }
        }
    }

    infoEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { infoEntry = null },
            title = { Text(entry.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(provenanceLabel(entry), fontWeight = FontWeight.Bold)
                    Text(entry.description)
                    entry.disclaimer?.let { Text(it) }
                    Text("Este plan completo requiere la configuración compartida para comprobar material y tiempo; no se aplica como plantilla de sesión.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                if (onSelectPlan != null) {
                    TextButton(onClick = { infoEntry = null; onSelectPlan(entry) }) { Text("Configurar plan") }
                } else {
                    TextButton(onClick = { infoEntry = null }) { Text("Entendido") }
                }
            },
            dismissButton = { TextButton(onClick = { infoEntry = null }) { Text("Volver") } },
        )
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.58f))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                KpknSheetLightChip(label = option, selected = selected == option, onClick = { onSelect(option) })
            }
        }
    }
}

/** Los cuatro objetivos del wizard: lo que no sirve a ninguno cae en «Otros». */
private val LIBRARY_GOAL_PROFILES: List<PlanGoalProfile> = listOf(
    PlanGoalProfile.STRENGTH,
    PlanGoalProfile.MUSCLE,
    PlanGoalProfile.STRENGTH_MUSCLE,
    PlanGoalProfile.COMPLETE_ATHLETE,
)

/**
 * Misma regla que el wizard (DEC-w2-06): decide [PlanGoalMatcher], no las capacidades
 * sueltas. Así «Fuerza» no lista `powerbuilding-foundation` solo por declarar fuerza.
 */
private fun profileMatches(entry: CatalogEntry, filter: LibraryProfileFilter): Boolean = when (filter) {
    LibraryProfileFilter.ALL -> true
    LibraryProfileFilter.STRENGTH -> PlanGoalMatcher.matches(entry, PlanGoalProfile.STRENGTH)
    LibraryProfileFilter.MUSCLE -> PlanGoalMatcher.matches(entry, PlanGoalProfile.MUSCLE)
    LibraryProfileFilter.STRENGTH_MUSCLE -> PlanGoalMatcher.matches(entry, PlanGoalProfile.STRENGTH_MUSCLE)
    LibraryProfileFilter.COMPLETE_ATHLETE -> PlanGoalMatcher.matches(entry, PlanGoalProfile.COMPLETE_ATHLETE)
    LibraryProfileFilter.OTHER -> LIBRARY_GOAL_PROFILES.none { PlanGoalMatcher.matches(entry, it) }
}

private fun originMatches(entry: CatalogEntry, filter: LibraryOriginFilter): Boolean {
    val category = entry.provenance?.category ?: when {
        entry.sourceAuthor.equals("KPKN", ignoreCase = true) -> PlanProvenanceClass.KPKN
        entry.source == CatalogSource.NATIVE -> PlanProvenanceClass.KPKN
        else -> PlanProvenanceClass.LEGACY
    }
    return when (filter) {
        LibraryOriginFilter.ALL -> true
        LibraryOriginFilter.ORIGINAL -> category == PlanProvenanceClass.ORIGINAL
        LibraryOriginFilter.ADAPTED -> category == PlanProvenanceClass.ADAPTED
        LibraryOriginFilter.KPKN -> category == PlanProvenanceClass.KPKN
        LibraryOriginFilter.LEGACY -> category == PlanProvenanceClass.LEGACY
    }
}

private fun provenanceLabel(entry: CatalogEntry): String = when (entry.provenance?.category) {
    PlanProvenanceClass.ORIGINAL -> "Original fiel${entry.provenance.sourceAuthor?.let { " · $it" }.orEmpty()}"
    PlanProvenanceClass.ADAPTED -> "Adaptación KPKN${entry.provenance.sourceAuthor?.let { " · desde $it" }.orEmpty()}"
    PlanProvenanceClass.KPKN -> "Plan KPKN"
    PlanProvenanceClass.LEGACY, null -> when {
        entry.source == CatalogSource.NATIVE || entry.sourceAuthor.equals("KPKN", ignoreCase = true) -> "Plan KPKN"
        entry.source == CatalogSource.TEMPLATE -> "Plantilla de programa"
        else -> "Método · ${entry.sourceAuthor ?: "procedencia heredada"}"
    }
}

private fun frequencyLabel(entry: CatalogEntry): String = when {
    entry.supportedFrequencies.first == entry.supportedFrequencies.last -> "${entry.supportedFrequencies.first} días/semana"
    else -> "${entry.supportedFrequencies.first}–${entry.supportedFrequencies.last} días/semana"
}

private fun durationLabel(entry: CatalogEntry): String = when (entry.duration) {
    CatalogDuration.REPEATING_WEEK -> "Semana repetible"
    CatalogDuration.REPEATING_CYCLE -> "Ciclo repetible"
    CatalogDuration.FINITE_CYCLE -> "Ciclo finito"
}
