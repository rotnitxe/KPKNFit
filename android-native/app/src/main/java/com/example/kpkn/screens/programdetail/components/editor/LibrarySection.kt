package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.screens.programdetail.design.structure.ProgramLibraryCard
import com.example.kpkn.screens.programdetail.design.structure.ProgramLibraryProfile
import com.example.kpkn.screens.programdetail.design.structure.ProgramLibraryTraits
import com.example.kpkn.screens.programdetail.design.structure.ProgramTemplatesSheet
import com.example.kpkn.screens.programdetail.design.structure.programBlocksAndWeeksLine
import com.example.kpkn.screens.programs.LibraryOriginFilter
import com.example.kpkn.screens.programs.isLibraryFamilyEntry
import com.example.kpkn.screens.programs.libraryOrder
import com.example.kpkn.screens.programs.originMatches
import com.example.kpkn.screens.programs.visibleProtocolOf

/**
 * La hoja «Plantillas» del editor (P3c): reúne las plantillas y los protocolos de la biblioteca publicada, con el mismo orden y la misma
 * selección que antes. Una plantilla vuelve a [onApplyTemplate] y un protocolo, a [onApplyProtocol]; la hoja solo cambia cómo se ve.
 */
@Composable
internal fun LibrarySection(
    onApplyTemplate: (ProgramTemplateOption) -> Unit,
    onApplyProtocol: (Protocol) -> Unit,
    onDismiss: () -> Unit,
) {
    val library = remember { programLibrary() }
    ProgramTemplatesSheet(
        templates = library.templateCards,
        protocols = library.protocolCards,
        onPickTemplate = { key -> library.templates[key]?.let(onApplyTemplate) },
        onPickProtocol = { key -> library.protocols[key]?.let(onApplyProtocol) },
        onDismiss = onDismiss,
    )
}

/** Lo que la hoja necesita: las tarjetas y, por cada una, lo que se aplica al tocarla. */
private class ProgramLibrary(
    val templateCards: List<ProgramLibraryCard>,
    val protocolCards: List<ProgramLibraryCard>,
    val templates: Map<String, ProgramTemplateOption>,
    val protocols: Map<String, Protocol>,
)

/**
 * La biblioteca del editor. Una entrada con plantilla es una plantilla (como antes, tiene prioridad); una entrada del catálogo de tipo
 * protocolo, sin plantilla, es un protocolo con ficha. El resto no se puede aplicar desde el editor y no aparece.
 */
private fun programLibrary(): ProgramLibrary {
    val entries = libraryOrder(
        PersonalizedPlanCatalog.listedEntries()
            .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) },
    )
    val templateEntries = entries.mapNotNull { entry -> entry.template?.let { template -> entry to template } }
    val protocolEntries = entries.mapNotNull { entry ->
        if (entry.template != null || entry.source != CatalogSource.PROTOCOL) return@mapNotNull null
        visibleProtocolOf(entry)?.let { protocol -> entry to protocol }
    }
    return ProgramLibrary(
        templateCards = templateEntries.map { (entry, template) ->
            ProgramLibraryCard(
                key = entry.id,
                name = template.name,
                description = template.description,
                caption = programBlocksAndWeeksLine(template.blockNames.size, template.weeks),
                traits = traitsOf(entry),
            )
        },
        protocolCards = protocolEntries.map { (entry, protocol) ->
            ProgramLibraryCard(
                key = entry.id,
                name = protocol.name,
                description = protocol.description,
                caption = programBlocksAndWeeksLine(protocol.blocks.size, protocol.blocks.sumOf { it.weeks }),
                traits = traitsOf(entry),
            )
        },
        templates = templateEntries.associate { (entry, template) -> entry.id to template },
        protocols = protocolEntries.associate { (entry, protocol) -> entry.id to protocol },
    )
}

/**
 * Los filtros que cumple un plan: sus perfiles (con «Otros» si no encaja en ninguno de los cuatro objetivos), sus días, su duración y sus
 * procedencias. Es la misma regla que tenía la biblioteca.
 */
private fun traitsOf(entry: CatalogEntry): ProgramLibraryTraits {
    val goals = mapOf(
        ProgramLibraryProfile.STRENGTH to PlanGoalProfile.STRENGTH,
        ProgramLibraryProfile.MUSCLE to PlanGoalProfile.MUSCLE,
        ProgramLibraryProfile.STRENGTH_MUSCLE to PlanGoalProfile.STRENGTH_MUSCLE,
        ProgramLibraryProfile.COMPLETE_ATHLETE to PlanGoalProfile.COMPLETE_ATHLETE,
    )
    val matched = goals.filterValues { goal -> PlanGoalMatcher.matches(entry, goal) }.keys
    return ProgramLibraryTraits(
        profiles = if (matched.isEmpty()) setOf(ProgramLibraryProfile.OTHER) else matched,
        frequencies = entry.supportedFrequencies,
        duration = entry.duration,
        origins = LibraryOriginFilter.entries
            .filter { origin -> origin != LibraryOriginFilter.ALL && originMatches(entry, origin) }
            .toSet(),
    )
}
