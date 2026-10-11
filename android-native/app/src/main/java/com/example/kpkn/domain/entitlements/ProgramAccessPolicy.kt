package com.example.kpkn.domain.entitlements

import com.example.kpkn.data.models.*

class CapabilityRequiredException(val code: String) : IllegalStateException(code)

object ProgramAccessPolicy {
    fun rejection(level: PlanLevel, program: Program): String? {
        if (level == PlanLevel.PRO) return null
        val blocks = program.macrocycles.flatMap { it.blocks }
        val mesocycles = blocks.flatMap { it.mesocycles }
        val weeks = mesocycles.flatMap { it.weeks }
        if (program.structure == ProgramStructure.COMPLEX || program.macrocycles.size > 1 || blocks.size > 1 ||
            mesocycles.size > 1 || program.sourceRecipe != null || program.sourceProtocolId != null ||
            program.autoregulationMode != AutoregulationMode.OFF || program.autoVolumeEnabled ||
            weeks.any { week -> week.sessions.any { it.sessionB != null || it.sessionC != null || it.sessionD != null || it.microProgram != null } })
            return "advanced_program_requires_pro"
        // A draft can have no week yet. Assigned days each consume a weekly slot.
        return CapabilityCatalog.validateProgramStructure(level, maxOf(1, weeks.size),
            if (weeks.isEmpty()) listOf(0) else weeks.map { week ->
                week.sessions.sumOf { session -> maxOf(1, session.assignedDays.distinct().size) }
            })
    }
    fun requireWrite(level: PlanLevel, program: Program) {
        rejection(level, program)?.let { throw CapabilityRequiredException(it) }
    }
}
