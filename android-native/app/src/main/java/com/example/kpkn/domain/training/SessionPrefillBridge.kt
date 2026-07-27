package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate

/**
 * Puente entre la creación/aplicación de plantillas y protocolos de programa y el
 * relleno de sesiones reales vía split. Evita que un programa recién creado quede
 * con semanas vacías cuando la plantilla no trae contenido ejecutable propio,
 * reutilizando [SplitApplicationEngine] en modo PREBUILT (que a su vez usa
 * [com.example.kpkn.domain.templates.SessionTemplateSuggestionEngine]).
 */
object SessionPrefillBridge {

    /** Split por defecto según el track de la plantilla/protocolo, cuando el programa no tiene uno propio. */
    fun resolveDefaultSplitId(trackLabel: String?): String = when (trackLabel) {
        "Powerlifting" -> "pl_classic_4"
        "Powerbuilding" -> "ppl_ul"
        "Culturismo" -> "ppl_x6"
        else -> "ul_x4"
    }

    /**
     * Resuelve el split a usar para el prefill: prioriza el split explícito del
     * protocolo (defaultSplit), luego el ya seleccionado en el programa, y por
     * último un default razonable según el track de la plantilla/protocolo.
     */
    fun resolveSplit(
        program: Program,
        protocolDefaultSplitId: String? = null,
        fallbackTrackLabel: String? = null,
    ): SplitTemplate? {
        val splitId = protocolDefaultSplitId
            ?: program.selectedSplitId
            ?: resolveDefaultSplitId(fallbackTrackLabel)
        return SPLIT_TEMPLATES.firstOrNull { it.id == splitId }
    }

    /**
     * Si el programa no tiene sesiones ejecutables, las rellena con sugerencias
     * reales del split resuelto (PREBUILT). Si ya tiene contenido (p.ej. lo
     * generó el propio protocolo), no toca nada.
     */
    fun prefillIfEmpty(program: Program, split: SplitTemplate?): Program {
        if (split == null) return program
        if (ProgramTemplateEngine.hasSessionContent(program)) return program

        val request = SplitApplicationRequest(
            program = program,
            selectedSplit = split,
            selectedBlockId = null,
            selectedWeekId = null,
            startDay = program.startDay ?: 1,
            temporalScope = SplitTemporalScope.WHOLE_PROGRAM,
            migrationMode = SessionMigrationMode.PREBUILT,
        )
        return SplitApplicationEngine.apply(request).copy(selectedSplitId = split.id)
    }
}
