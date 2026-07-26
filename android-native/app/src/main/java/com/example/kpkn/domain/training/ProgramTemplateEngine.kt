package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.buildProgramDraft

object ProgramTemplateEngine {

    enum class ApplyStrategy {
        REPLACE_STRUCTURE,
        CREATE_DRAFT_COPY,
    }

    data class ApplyResult(
        val program: Program,
        val strategy: ApplyStrategy,
        val createdCopy: Boolean = false,
    )

    fun hasSessionContent(program: Program): Boolean =
        program.macrocycles.any { macro ->
            macro.blocks.any { block ->
                block.mesocycles.any { meso ->
                    meso.weeks.any { week -> week.sessions.isNotEmpty() }
                }
            }
        }

    fun resolveApplyStrategy(program: Program, forceCopy: Boolean = false): ApplyStrategy =
        if (forceCopy || hasSessionContent(program)) ApplyStrategy.CREATE_DRAFT_COPY
        else ApplyStrategy.REPLACE_STRUCTURE

    fun applyTemplate(
        current: Program,
        template: ProgramTemplateOption,
        forceCopy: Boolean = false,
        idProvider: IdProvider = UuidIdProvider,
    ): ApplyResult {
        val strategy = resolveApplyStrategy(current, forceCopy)
        val draft = template.buildProgramDraft(
            when (strategy) {
                ApplyStrategy.CREATE_DRAFT_COPY -> current.copy(
                    id = idProvider.newId(),
                    name = "${current.name} · ${template.name}",
                    isDraft = true,
                )
                ApplyStrategy.REPLACE_STRUCTURE -> current
            },
        ).copy(
            mode = when (template.trackLabel) {
                "Powerlifting" -> com.example.kpkn.data.models.ProgramMode.POWERLIFTING
                "Powerbuilding" -> com.example.kpkn.data.models.ProgramMode.POWERBUILDING
                else -> current.mode
            },
        )
        return ApplyResult(
            program = draft.alignTemporalMetadata(),
            strategy = strategy,
            createdCopy = strategy == ApplyStrategy.CREATE_DRAFT_COPY,
        )
    }
}
