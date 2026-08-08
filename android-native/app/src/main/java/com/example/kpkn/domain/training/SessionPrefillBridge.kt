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
        val rawSplitId = protocolDefaultSplitId
            ?: program.selectedSplitId
            ?: resolveDefaultSplitId(fallbackTrackLabel)
        val splitId = ProgramProtocolEngine.resolveSplitId(rawSplitId)
        return SPLIT_TEMPLATES.firstOrNull { it.id == splitId }
    }

    /**
     * Rellena únicamente las semanas que todavía no tienen sesiones con
     * sugerencias reales del split resuelto (PREBUILT). Las semanas que ya
     * tienen contenido quedan byte-a-byte fuera del alcance de la aplicación.
     */
    fun prefillEmptyWeeks(program: Program, split: SplitTemplate?): Program {
        if (split == null) return program
        val emptyWeekIds = program.macrocycles
            .flatMap { macro -> macro.blocks }
            .flatMap { block -> block.mesocycles }
            .flatMap { meso -> meso.weeks }
            .filter { week -> week.sessions.isEmpty() }
            .map { week -> week.id }
            .toSet()
        if (emptyWeekIds.isEmpty()) return program

        val request = SplitApplicationRequest(
            program = program,
            selectedSplit = split,
            selectedBlockId = null,
            selectedWeekId = null,
            startDay = program.startDay ?: 1,
            temporalScope = SplitTemporalScope.SELECTED_WEEKS,
            selectedWeekIds = emptyWeekIds,
            migrationMode = SessionMigrationMode.PREBUILT,
        )
        return SplitApplicationEngine.apply(request).copy(selectedSplitId = split.id)
    }

    /** Compatibilidad semántica para los callers existentes; ahora cubre semanas parciales. */
    fun prefillIfEmpty(program: Program, split: SplitTemplate?): Program =
        prefillEmptyWeeks(program, split)
}
