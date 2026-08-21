package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.buildProgramDraft
import com.example.kpkn.data.sessions.SessionTemplate

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
        applySplitPrefill: Boolean = true,
        generationTemplates: List<SessionTemplate>? = null,
    ): ApplyResult {
        val strategy = resolveApplyStrategy(current, forceCopy)
        val trackKey = template.trackLabel?.trim()?.lowercase()
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
            // A discipline template owns its training mode.  Carrying the
            // mode from a previous draft (for example applying Culturismo to
            // an old PL program) makes the generated catalog and editorial
            // rules disagree with the visible track.
            mode = when (trackKey) {
                "powerlifting" -> ProgramMode.POWERLIFTING
                "powerbuilding" -> ProgramMode.POWERBUILDING
                "culturismo", "bodybuilding", "body building", "hipertrofia" -> ProgramMode.HYPERTROPHY
                else -> current.mode
            },
        )
        // Persist the split that actually materializes the draft.  Keeping it
        // only as a transient fallback made later block/week edits resolve a
        // different schedule than the one the athlete first received.
        val selectedSplitId = when {
            trackKey == "powerlifting" -> "pl_sbd_x3"
            trackKey == "powerbuilding" -> "ppl_ul"
            trackKey == "culturismo" ||
                trackKey == "bodybuilding" ||
                trackKey == "body building" ||
                trackKey == "hipertrofia" -> "ppl_x6"
            draft.selectedSplitId != null -> draft.selectedSplitId
            else -> SessionPrefillBridge.resolveSplit(
                draft,
                fallbackTrackLabel = template.trackLabel,
            )?.id
        }
        val scheduledDraft = draft.copy(
            selectedSplitId = selectedSplitId,
        )
        val materializationSplit = SessionPrefillBridge.resolveSplit(
            scheduledDraft,
            fallbackTrackLabel = template.trackLabel,
        )
        // Bridge F4: una plantilla sin sesiones propias queda con semanas vacías;
        // la rellenamos con sugerencias reales del split (actual o por defecto según track).
        val prefilled = if (applySplitPrefill) {
            SessionPrefillBridge.prefillEmptyWeeks(
                scheduledDraft,
                materializationSplit,
                // An empty list is the pre-hydration sentinel from
                // SessionTemplateRepository, not an intentional "no recipes"
                // publication.  Preserve the engine's fail-safe SYSTEM
                // catalog until the derived Room flow has emitted candidates.
                templates = generationTemplates
                    ?.takeIf { it.isNotEmpty() }
                    ?: com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM,
            )
        } else {
            scheduledDraft
        }
        // The split only supplies the day-by-day blueprint.  Advanced programs
        // must then compile every block with its own goal/scheme; otherwise
        // Base, Intensification, Peak and Taper all execute the same baseline.
        val materialized = prefilled.copy(
            macrocycles = prefilled.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        BlockProgressionEngine.applyProgression(
                            block = block,
                            scheme = block.progressionScheme ?: BlockProgressionScheme.PERCENT_RM,
                        ).block
                    },
                )
            },
        )
        val executable = hydrateProgramGoals(materialized).alignTemporalMetadata()
        if (applySplitPrefill) ProgramExecutionContract.requireExecutable(executable)
        return ApplyResult(
            program = executable,
            strategy = strategy,
            createdCopy = strategy == ApplyStrategy.CREATE_DRAFT_COPY,
        )
    }

    /**
     * A percentage prescription is executable only when the athlete already
     * has a recorded goal/reference.  Copying the program goal into the exact
     * S/B/D configuration makes the load calculator useful immediately while
     * leaving it null (and therefore fail-safe) when no goal was recorded.
     */
    private fun hydrateProgramGoals(program: Program): Program {
        val goals = program.goals ?: return program
        fun referenceFor(exercise: Exercise): Double? {
            val id = listOfNotNull(
                exercise.catalogConfigurationId,
                exercise.canonicalExerciseId,
                exercise.exerciseDbId,
                exercise.exerciseId,
            ).firstOrNull()?.lowercase() ?: return exercise.reference1RM
            val goal = when (id) {
                "low_bar_back_squat__barbell", "high_bar_back_squat__barbell" -> goals.squat1RM
                "bench_press__barbell" -> goals.bench1RM
                "conventional_deadlift__bilateral__barbell" -> goals.deadlift1RM
                else -> null
            }
            return goal?.takeIf { it > 0.0 } ?: exercise.reference1RM
        }
        fun mapSession(session: Session): Session = session.copy(
            exercises = session.exercises.map { it.copy(reference1RM = referenceFor(it)) },
            parts = session.parts.map { part ->
                part.copy(exercises = part.exercises.map { it.copy(reference1RM = referenceFor(it)) })
            },
            sessionB = session.sessionB?.let(::mapSession),
            sessionC = session.sessionC?.let(::mapSession),
            sessionD = session.sessionD?.let(::mapSession),
        )
        return program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            week.copy(sessions = week.sessions.map(::mapSession))
                        })
                    })
                })
            },
        )
    }
}
